#!/usr/bin/env python3
"""Verify real pedestrian approaches and the level courier route on pinned R7 blocks. Requires NumPy."""
import argparse
from collections import deque
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sys

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
    "sea_lantern",
}


def parse_contract(map_data):
    source = (ROOT / "src/main/java/com/quin/catancraft/delivery/DeliveryRoute.java").read_text()
    calls = re.findall(r'surveyed\(district, \w+, "(\w+)", MonumentType\.\w+, (-?\d+), (-?\d+), (-?\d+), (-?\d+)\)', source)
    assert len(calls) == 2, "Re-audit an edited or ambiguous route resolver"
    monuments = {site["id"]: site for site in map_data["monuments"]}
    stops = {}
    for key, ax, az, x, z in calls:
        site = monuments[key]
        assert (int(ax), 81, int(az)) == (site["x"], site["y"], site["z"])
        assert site["dimension"] == "minecraft:overworld"
        pad = (int(x), int(z))
        # All 25 block centres on each stop are outside all monument objectives.
        for m in monuments.values():
            if m["dimension"] == site["dimension"]:
                assert all((pad[0]+dx-m["x"])**2+(pad[1]+dz-m["z"])**2 > m["radius"]**2
                           for dx in range(-2, 3) for dz in range(-2, 3))
        stops[key] = pad
    return stops


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


def run(input_root):
    if not __debug__:
        raise RuntimeError("Run qualification with Python assertions enabled")
    manifest = json.loads(pinned(input_root/"INPUT_MANIFEST.json", INPUT_SHA))
    pins = {f["path"]: f["sha256"] for f in manifest["files"]}
    for name, digest in pins.items():
        if name.startswith("source_world/") or name in {"bootstrap_reference/config/catancraft/map.json", "nbt_check.py"}:
            pinned(input_root/name, digest)
    spec = importlib.util.spec_from_file_location("pinned_nbt", input_root/"nbt_check.py")
    nbt = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(nbt)
    definition = json.loads(pinned(ROOT/"src/main/resources/data/catancraft/maps/season1_river_bridges_v3.json", MAP_SHA))
    original = json.loads((input_root/"bootstrap_reference/config/catancraft/map.json").read_bytes())
    for group in ("mapId", "monuments", "publicZones"):
        assert original[group] == definition[group], group
    cities = {c["id"]: (c["cityCenter"]["x"], c["cityCenter"]["z"]) for c in definition["territories"]}
    assert cities == {c["id"]: (c["cityCenter"]["x"], c["cityCenter"]["z"]) for c in original["territories"]}
    stops = parse_contract(definition)
    world = World(input_root/"source_world/CatanCraft_Season1_R7_Development", nbt.NBT)
    for x,z in stops.values():
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                assert [world.at((x+dx,y,z+dz)) for y in (80,81,82)] == ["minecraft:gravel","minecraft:air","minecraft:air"]
    heights, offset = terrain(world,[*cities.values(),*stops.values()])
    approaches = trace_paths(heights, offset, stops["depot"], cities)
    # Actual bends of the public gravel route. The two gravel lots touch at one
    # grass block. Check that discontinuity explicitly rather than calling it road.
    turns = [stops["depot"], (72,-16), (72,4), (44,4), (44,82), (29,82),
             (29,87), (22,87), stops["refinery"]]
    loaded = [[turns[0][0],81,turns[0][1]]]
    for (x,z),(end_x,end_z) in zip(turns,turns[1:]):
        assert (x == end_x) != (z == end_z), "Expected a single cardinal segment"
        dx = (end_x>x)-(end_x<x)
        dz = (end_z>z)-(end_z<z)
        while (x,z) != (end_x,end_z):
            x,z = x+dx,z+dz
            assert heights[z+offset,x+offset] == 80
            floor = world.at((x,80,z)).split("[")[0]
            assert floor == ("minecraft:grass_block" if (x,z)==(39,82) else "minecraft:gravel")
            assert all(world.at((x,y,z)) == "minecraft:air" for y in (81,82,83))
            for site in definition["monuments"]:
                assert (x-site["x"])**2+(z-site["z"])**2 > (site["radius"]+1)**2
            loaded.append([x,81,z])
    paths = {"depotToRefinery": loaded, **approaches}
    reached = {}
    for name,path in paths.items():
        reached[name] = {"steps": len(path)-1, "feetY": [min(v[1] for v in path),max(v[1] for v in path)],
                         "start": path[0], "end": path[-1],
                         "pathSHA256": hashlib.sha256(json.dumps(path,separators=(",",":")).encode()).hexdigest()}
    return {"fixtureManifestSHA256": INPUT_SHA, "mapSHA256": MAP_SHA, "scope": "Static pedestrian clearance in the pristine authored R7 source, before purchases.",
            "movement": "Cardinal walking with three air blocks and at most one full-block rise or drop. The loaded leg stays at feet Y=81 and crosses one grass block between gravel lots. City approaches may cross natural ground.",
            "foundingCities": sorted(c['id'] for c in definition['territories'] if c.get('startSlot',0)>0),
            "loadedRouteAvoidsAllCaptureZones": True, "loadedRouteTurnsXZ": turns,
            "loadedLegSurfaceSteps": {"gravel": len(loaded)-2, "grass": 1},
            "landmarkPads": {k: [x,81,z] for k,(x,z) in stops.items()}, "padWidth":5, "routes": reached}


if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("clean_input_root", type=Path)
    print(json.dumps(run(parser.parse_args().clean_input_root), indent=2))
