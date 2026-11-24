#!/usr/bin/env python3
"""Rebuild capture-floor geometry and verify it against the original Full Map Review 02 save."""
import argparse
from collections import deque
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile

from audit_route import MAP_ARCHIVE_SHA, PROFILE, ROOT, checked_save
from audit_sites import INPUT_SHA, MAP_SHA, World, pinned

CATALOG = ROOT/"src/main/resources/data/catancraft/maps/full_review_02_capture_floors.json"
RECEIPT = ROOT/"tools/delivery/capture_floor_audit.json"
# Interior bounds are strictly inside the four exterior walls. Each entrance
# passes from uncovered ground through an actual opening into this room.
ROOMS = {
    "industrial": ("Switchyard Turbine Hall", (-106,-66,-106,-55), (-86,-106), (0,1), (-86,-80),
                   [(-107,86,-106), (-86,108,-80), (-86,80,-80)]),
    "depot": ("Meridian Airlift Depot", (48,82,-88,-52), (66,-52), (0,-1), (64,-54),
              [(47,85,-88), (66,100,-52), (82,90,-52)]),
    "refinery": ("Copperline Refinery", (-33,-13,89,113), (-22,89), (0,1), (-22,98),
                 [(-34,81,89), (-22,92,89), (-22,80,101)]),
}
FLOORS = {"smooth_stone","stone_bricks","quartz_block","yellow_terracotta","ochre_froglight","deepslate_tiles"}


def pack(cells, bounds):
    """Merge equal horizontal runs on consecutive rows into disjoint rectangles."""
    x0,x1,z0,z1 = bounds
    opened, boxes = {}, []
    for z in range(z0,z1+2):
        runs = set()
        x = x0
        while x <= x1:
            if (x,z) not in cells:
                x += 1
                continue
            start = x
            while (x,z) in cells:
                x += 1
            runs.add((start,x-1))
        for span in sorted(opened.keys()-runs):
            boxes.append(dict(minX=span[0],minY=81,minZ=opened.pop(span),maxX=span[1],maxY=82,maxZ=z-1))
        for span in runs:
            opened.setdefault(span,z)
    return boxes


def audit(world, old, definition):
    records, proofs = [], {}
    assert set(ROOMS) == {m["id"] for m in definition["monuments"]}
    for site in definition["monuments"]:
        identity = site["id"]
        name, bounds, door, inward, nav, pins = ROOMS[identity]
        floor = lambda x,z: world.at((x,80,z)).removeprefix("minecraft:")
        clear = lambda x,z: all(world.at((x,y,z))=="minecraft:air" for y in (81,82))
        cells = set()
        x0,x1,z0,z1 = bounds
        for x in range(x0,x1+1):
            for z in range(z0,z1+1):
                if clear(x,z):
                    assert floor(x,z) in FLOORS
                    assert any(world.at((x,y,z))!="minecraft:air" for y in range(83,125)), ("open sky",x,z)
                    cells.add((x,z))
        reached = {door}
        queue = deque([door])
        assert door in cells
        while queue:
            x,z = queue.popleft()
            for point in [(x-1,z),(x+1,z),(x,z-1),(x,z+1)]:
                if point in cells and point not in reached:
                    reached.add(point)
                    queue.append(point)
        assert nav in reached and len(reached) > 200
        assert all(world.at((nav[0],y,nav[1]))=="minecraft:air" for y in (81,82,83,84))
        entrance = [[door[0]-inward[0]*i,81,door[1]-inward[1]*i] for i in (2,1,0)]
        for x,_,z in entrance:
            assert clear(x,z) and floor(x,z) not in {"air","water"}
        assert (entrance[0][0],entrance[0][2]) not in cells and (entrance[1][0],entrance[1][2]) not in cells
        boxes = pack(reached,bounds)
        unpacked = [(x,z) for b in boxes for x in range(b["minX"],b["maxX"]+1) for z in range(b["minZ"],b["maxZ"]+1)]
        assert set(unpacked) == reached and len(unpacked) == len(reached)
        fingerprints = []
        for point in pins:
            actual = world.at(point)
            assert actual not in {"minecraft:air",old.at(point)} and "[" not in actual
            fingerprints.append(dict(x=point[0],y=point[1],z=point[2],block=actual))
        records.append(dict(id=identity, name=name, type=site["type"], dimension=site["dimension"],
                            x=site["x"], y=site["y"], z=site["z"], radius=site["radius"],
                            navigation={"x":nav[0],"y":81,"z":nav[1]}, fingerprints=fingerprints, boxes=boxes))
        proofs[identity]=dict(name=name, roomBoundsXZ=[x0,z0,x1,z1], navigableCells=len(reached),
                              unreachableClearCellsExcluded=len(cells-reached), disjointBoxes=len(boxes),
                              exteriorToInterior=entrance, navigationXYZ=[nav[0],81,nav[1]],
                              legacyAnchorInside=(site["x"],site["z"]) in reached,
                              oldMapMismatchCount=len(fingerprints))
    return {"mapId":PROFILE, "sites":records}, {"mapId":PROFILE,"mapArchiveSHA256":MAP_ARCHIVE_SHA,
            "regionSource":"All 64 Review 02 region files and level.dat verified against the original ZIP.",
            "coverage":"Every configured landmark. Offshore landmarks have no configured capturable objective.",
            "geometry":"Roofed walkable ground-floor cells inside the main building. Excludes exterior thresholds, machinery, roof and other levels. Feet from Y 81 inclusive to 83 exclusive allow ordinary jumps.",
            "sites":proofs}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("review_zip",type=Path)
    parser.add_argument("clean_r7_input_root",type=Path)
    parser.add_argument("--extracted-save",type=Path)
    parser.add_argument("--write",action="store_true",help="Regenerate the catalog and audit receipt")
    args=parser.parse_args()
    if not __debug__:
        raise RuntimeError("Enable assertions for this audit")
    olddir=args.clean_r7_input_root
    pins={f["path"]:f["sha256"] for f in json.loads(pinned(olddir/"INPUT_MANIFEST.json",INPUT_SHA))["files"]}
    spec=importlib.util.spec_from_file_location("pinned_nbt",olddir/"nbt_check.py")
    pinned(olddir/"nbt_check.py",pins["nbt_check.py"])
    nbt=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(nbt)
    oldname="source_world/CatanCraft_Season1_R7_Development"
    old=World(olddir/oldname,nbt.NBT)
    for *_, witnesses in ROOMS.values():
        for x,_,z in witnesses:
            rel=f"{oldname}/region/r.{x//512}.{z//512}.mca"
            pinned(olddir/rel,pins[rel])
    definition=json.loads(pinned(ROOT/"src/main/resources/data/catancraft/maps/season1_river_bridges_v3.json",MAP_SHA))
    with tempfile.TemporaryDirectory() as tmp:
        location,_=checked_save(args.review_zip,Path(tmp),args.extracted_save)
        catalog, report=audit(World(location,nbt.NBT),old,definition)
    contents=json.dumps(catalog,indent=2)+"\n"
    report["catalogSHA256"]=hashlib.sha256(contents.encode()).hexdigest()
    for dest,data in ((CATALOG,contents),(RECEIPT,json.dumps(report,indent=2)+"\n")):
        if args.write:
            dest.write_text(data)
        else:
            assert dest.read_text()==data, f"Readback mismatch: {dest}"
    print(json.dumps(report,indent=2))


if __name__=="__main__":
    main()
