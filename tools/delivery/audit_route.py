#!/usr/bin/env python3
"""Verify real pedestrian approaches and the level courier route on Full Map Review 02. Requires NumPy."""
import argparse
from collections import Counter, deque
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sys
import tempfile
import zipfile

import numpy as np

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/walls53"))
from audit_sites import INPUT_SHA, MAP_SHA, World, pinned

# Conservative full cubes; slabs, fluids and movable gates are excluded.
PAVING = {
    "gravel", "andesite", "polished_andesite", "cobblestone", "mossy_cobblestone",
    "stone", "stone_bricks", "smooth_stone", "mossy_stone_bricks",
    "cracked_stone_bricks", "spruce_planks", "oak_planks", "dark_oak_planks",
    "polished_deepslate", "light_gray_concrete", "gray_concrete", "coarse_dirt",
    "sea_lantern", "ochre_froglight", "yellow_terracotta", "white_concrete", "smooth_quartz",
}


MAP_ARCHIVE_SHA = "46e4d890f4bf2ed513b1936c7e72f98c17b774a9839cf3f337695d7952318292"
SAVE = "CatanCraft_Full_Map_Review_02"
PROFILE = "catancraft_full_map_review_02"
STOPS = {
    "depot": {"name": "Meridian Airlift Depot", "counter": (74,82,-30), "stand": (74,81,-29),
              "floor": "andesite", "blocks": {(74,82,-30): "spruce_slab[type=bottom]",
              (74,81,-30): "spruce_planks", (72,81,-30): "stripped_spruce_log"}},
    "refinery": {"name": "Copperline Refinery", "counter": (28,82,75), "stand": (29,81,75),
                 "floor": "polished_andesite", "blocks": {(28,82,75): "waxed_exposed_cut_copper",
                 (28,81,75): "deepslate_bricks", (27,83,75): "chiseled_polished_blackstone"}},
}
TURNS = [(74,-29),(50,-29),(50,-45),(45,-45),(45,-53),(28,-53),(28,-44),
         (17,-44),(17,-33),(7,-33),(7,-25),(-3,-25),(-3,-14),(-8,-14),
         (-8,-9),(-13,-9),(-13,7),(-11,7),(-11,10),(-6,10),(-6,12),
         (4,12),(4,71),(29,71),(29,75)]


ALLOWED_SOIL = {"grass_block", "dirt", "podzol", "sand", "red_sand"}
LOW, HIGH = 64, 159
# Prefer the ground closest to the Y=80 road surface where several exist.
ORDER = np.array(sorted(range(HIGH - LOW - 2), key=lambda level: abs(LOW + level - 80)))
BLOCK_CODES = {name: 1 for name in PAVING | ALLOWED_SOIL}
BLOCK_CODES.update({"air": 0, "cave_air": 0})
OFFSETS = np.arange(4096, dtype=np.uint64)


def planes(world, cx, cz):
    """Choose a walkable full block with three air blocks above it in each column."""
    sections = world.chunk(cx, cz)
    del world.chunks[cx, cz], world.entities[cx, cz]
    voxels = np.zeros((HIGH - LOW + 1, 16, 16), dtype=np.uint8)
    for sy in range(LOW // 16, HIGH // 16 + 1):
        section = sections.get(sy)
        if section is None:
            continue
        palette, longs = section
        names = [state.split("[")[0].removeprefix("minecraft:") for state in palette]
        codes = np.array([BLOCK_CODES.get(name, 2) for name in names], dtype=np.uint8)
        layer = voxels[sy * 16 - LOW:(sy + 1) * 16 - LOW]
        if len(palette) == 1:
            layer.fill(codes[0])
            continue
        bits = max(4, (len(palette) - 1).bit_length())
        per = 64 // bits
        words = np.asarray(longs, dtype=np.uint64)
        indices = (words[OFFSETS // per] >> ((OFFSETS % per) * bits)) & ((1 << bits) - 1)
        layer[:] = codes[indices].reshape(16, 16, 16)
    clearance = (voxels[1:-2] == 0) & (voxels[2:-1] == 0) & (voxels[3:] == 0)
    usable = (clearance & (voxels[:-3] == 1))[ORDER]
    return np.where(usable.any(axis=0), ORDER[usable.argmax(axis=0)] + LOW, -120).astype(np.int16)


def terrain(world, coords):
    first = min(min(pt) for pt in coords) // 16 - 2
    last = max(max(pt) for pt in coords) // 16 + 3
    side = (last - first) * 16 + 2
    # An invalid frame prevents wraparound at array boundaries.
    heights = np.full((side, side), -120, dtype=np.int16)
    offset = 1 - first * 16
    for cz in range(first, last):
        for cx in range(first, last):
            row, col = cz * 16 + offset, cx * 16 + offset
            heights[row:row + 16, col:col + 16] = planes(world, cx, cz)
    return heights, offset


def trace_paths(heights, offset, depot, targets):
    side = heights.shape[0]
    floors = heights.ravel()

    def index(point):
        return (point[1] + offset) * side + point[0] + offset

    origin = index(depot)
    assert floors[origin] == 80 and all(floors[index(p)] == 80 for p in targets.values()), "Inaccessible endpoint or city center"
    parent = np.full(side * side, -1, dtype=np.int32)
    parent[origin] = origin
    queue = deque([origin])
    pending = {index(point) for point in targets.values()}
    while queue and pending:
        current = queue.popleft()
        pending.discard(current)
        floor = int(floors[current])
        for neighbor in (current - 1, current + 1, current - side, current + side):
            if parent[neighbor] == -1 and abs(int(floors[neighbor]) - floor) <= 1:
                parent[neighbor] = current
                queue.append(neighbor)
    if pending:
        failed = [name for name, point in targets.items() if index(point) in pending]
        raise ValueError(f"No continuous surveyed foot approach to {failed}")

    paths = {}
    for name, point in targets.items():
        node = index(point)
        path = []
        while True:
            z, x = divmod(node, side)
            path.append([x - offset, int(floors[node]) + 1, z - offset])
            if node == origin:
                break
            node = int(parent[node])
        paths[name] = path
    return paths


def click_witness(world, old, stop):
    counter = stop["counter"]
    stand = stop["stand"]
    x,y,z = stand
    assert sum(abs(stand[i]-counter[i]) for i in (0,2)) == 1 and y + 1 == counter[1]
    assert all(world.at((x,y+i,z)) == "minecraft:air" for i in range(3))
    assert world.at((x,y-1,z)) == "minecraft:" + stop["floor"]
    fingerprints = []
    for point, name in stop["blocks"].items():
        state = world.at(point)
        assert state == "minecraft:" + name, (point, state, name)
        assert old.at(point) == "minecraft:air", ("R7 should fail this fingerprint", point)
        fingerprints.append({"block":point, "state": state, "r7State":"minecraft:air"})
    # Eye and hit point are in the same clear approach column up to the
    # exposed face. Aim below slab half-height at the depot.
    face_y = counter[1] + (0.25 if "slab" in stop["blocks"][counter] else 0.5)
    face = [(x+counter[0])/2+0.5, face_y, (z+counter[2])/2+0.5]
    eye = [x+0.5,y+1.62,z+0.5]
    distance = sum((a-b)**2 for a,b in zip(eye,face))**0.5
    assert distance < 4.5
    return {"name":stop["name"], "block":counter, "stand":stand, "eye":eye, "hitFace":face,
            "reachBlocks": round(distance,3), "approachFloor":stop["floor"], "fingerprints":fingerprints}


def checked_save(archive, work, extracted):
    with archive.open("rb") as f:
        assert hashlib.file_digest(f, "sha256").hexdigest() == MAP_ARCHIVE_SHA, "Wrong Full Map Review 02 ZIP"
    directory = extracted or work / SAVE
    entries = {}
    with zipfile.ZipFile(archive) as source:
        for info in source.infolist():
            if not info.filename.startswith(SAVE+"/") or info.is_dir():
                continue
            rel = info.filename[len(SAVE)+1:]
            assert rel == "level.dat" or re.fullmatch(r"region/r\.-?\d+\.-?\d+\.mca", rel)
            assert rel not in entries
            data = source.read(info)
            digest = hashlib.sha256(data).hexdigest()
            file = directory/rel
            if extracted:
                pinned(file, digest)
            else:
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_bytes(data)
            entries[rel] = digest
        checklist = source.read("Map_Checklist.html").decode()
        for name in [stop["name"] for stop in STOPS.values()] + ["Switchyard Turbine Hall"]:
            assert name in checklist
    assert len(entries) == 65 and "level.dat" in entries
    return directory, entries


def run(archive, r7_root, work, extracted=None):
    if not __debug__:
        raise RuntimeError("Run qualification with Python assertions enabled")
    manifest = json.loads(pinned(r7_root/"INPUT_MANIFEST.json", INPUT_SHA))
    pins = {f["path"]: f["sha256"] for f in manifest["files"]}
    pinned(r7_root/"nbt_check.py", pins["nbt_check.py"])
    spec = importlib.util.spec_from_file_location("pinned_nbt", r7_root/"nbt_check.py")
    nbt = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(nbt)
    directory, entries = checked_save(archive, work, extracted)
    world = World(directory, nbt.NBT)
    old = World(r7_root/"source_world/CatanCraft_Season1_R7_Development", nbt.NBT)
    for stop in STOPS.values():
        x,_,z = stop["counter"]
        region = f"source_world/CatanCraft_Season1_R7_Development/region/r.{x//512}.{z//512}.mca"
        pinned(r7_root/region,pins[region])
    source = (ROOT/"src/main/java/com/quin/catancraft/delivery/DeliveryRoute.java").read_text()
    assert f'"{PROFILE}"' in source
    for name,stop in STOPS.items():
        pos = ", ".join(map(str,stop["counter"]))
        assert f"{name.upper()}_COUNTER = new BlockPos({pos})" in source and f'"{stop["name"]}"' in source
    sites = {name:click_witness(world,old,stop) for name,stop in STOPS.items()}

    definition = json.loads(pinned(ROOT/"src/main/resources/data/catancraft/maps/season1_river_bridges_v3.json", MAP_SHA))
    cities = {c["id"]: (c["cityCenter"]["x"], c["cityCenter"]["z"]) for c in definition["territories"]}
    assert len(cities) == 16
    heights, offset = terrain(world,[*cities.values(),*TURNS])
    approaches = trace_paths(heights, offset, TURNS[0], cities)
    loaded = [[*STOPS["depot"]["stand"]]]
    for start,end in zip(TURNS,TURNS[1:]):
        x,z = start
        dx,dz = (end[0]>x)-(end[0]<x), (end[1]>z)-(end[1]<z)
        assert abs(dx) + abs(dz) == 1
        while (x,z) != end:
            x,z = x+dx,z+dz
            loaded.append([x,81,z])
    assert tuple(loaded[-1]) == STOPS["refinery"]["stand"]
    surfaces = Counter()
    for x,y,z in loaded:
        assert heights[z+offset,x+offset] == 80
        floor = world.at((x,y-1,z)).split("[")[0].removeprefix("minecraft:")
        assert floor in PAVING and all(world.at((x,i,z)) == "minecraft:air" for i in (81,82,83))
        surfaces[floor] += 1
        for site in definition["monuments"]:
            assert (x-site["x"])**2+(z-site["z"])**2 > (site["radius"]+1)**2
    reached = {}
    for name,path in {"depotToRefinery": loaded, **approaches}.items():
        reached[name] = {"steps":len(path)-1, "feetY":[min(v[1] for v in path),max(v[1] for v in path)],
                         "start":path[0], "end":path[-1],
                         "pathSHA256":hashlib.sha256(json.dumps(path,separators=(",",":")).encode()).hexdigest()}
    return {"mapId":PROFILE, "sourceArchive":archive.name, "sourceSHA256":MAP_ARCHIVE_SHA,
            "levelDatSHA256":entries["level.dat"], "verifiedRegionFiles":len(entries)-1,
            "cityAnchorDefinitionSHA256":MAP_SHA, "negativeControlManifestSHA256":INPUT_SHA,
            "scope":"Original Full Map Review 02 save blocks, footprints and hand reach, before purchases. Static audit.",
            "movement":"Cardinal walking with three air blocks above a full floor and at most one block of rise or drop. Loaded leg stays level on paving; city approaches may cross natural ground.",
            "foundingCities":sorted(c["id"] for c in definition["territories"] if c.get("startSlot",0)>0),
            "loadedRouteAvoidsAllCaptureZones":True, "loadedRouteTurnsXZ":TURNS,
            "loadedRouteSurfaceCells":dict(sorted(surfaces.items())), "counters":sites, "routes":reached}


if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("review_zip", type=Path)
    parser.add_argument("clean_r7_input_root", type=Path, help="Pinned NBT reader and legacy negative control only")
    parser.add_argument("--extracted-save", type=Path, help="Optional extracted Review 02 world, checked against ZIP")
    args=parser.parse_args()
    with tempfile.TemporaryDirectory() as tmp:
        print(json.dumps(run(args.review_zip,args.clean_r7_input_root,Path(tmp),args.extracted_save), indent=2))
