"""Trace an existing two-way land/boat transfer at the northwest side of bridge B1."""
import argparse
from collections import deque
from functools import lru_cache
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
REFERENCE = ROOT / "inputs/CatanCraft_Clean_R7_Input"
sys.path[:0] = [str(REFERENCE), str(ROOT / "resource-ui/tools/walls53")]
from audit_sites import World
from nbt_check import NBT

START = (-45, -568)
CHANNEL = (-16, -570)
FLOORS = {"sand", "gravel", "grass_block", "coarse_dirt", "dirt", "stone", "andesite", "stone_bricks"}


def neighbors(point):
    x, z = point
    return [(x+1,z), (x,z-1), (x-1,z), (x,z+1)]


def path_to(parents, last):
    path = []
    while last is not None:
        path.append(last)
        last = parents[last]
    return path[::-1]


def survey(directory):
    world = World(directory, NBT)

    @lru_cache(None)
    def block(x, y, z):
        return world.at((x,y,z)).split("[")[0].removeprefix("minecraft:")

    @lru_cache(None)
    def floor(x,z):
        for y in range(88,73,-1):
            if block(x,y,z) == "air":
                continue
            if block(x,y,z) in FLOORS and all(block(x,y+k,z) == "air" for k in (1,2,3)):
                return y
            break
        return None

    @lru_cache(None)
    def wet(x,z):
        water = {"minecraft:water[level=0]", "minecraft:water"}
        return all(world.at((a,75,b)) in water
                   and block(a,76,b) == "air" and block(a,77,b) == "air"
                   for a in range(x-1,x+2) for b in range(z-1,z+2))

    water_route = {CHANNEL: None}
    search = deque([CHANNEL])
    while search:
        point = search.popleft()
        for p in neighbors(point):
            if -45 <= p[0] <= -10 and -581 <= p[1] <= -569 and p not in water_route and wet(*p):
                water_route[p] = point
                search.append(p)
    if not wet(*CHANNEL):
        raise ValueError("River end of transfer blocked")

    land_route = {START: None}
    search = deque([START])
    while search:
        point = search.popleft()
        x,z = point
        h = floor(x,z)
        if h is None:
            raise ValueError("Missing bank floor")
        if h in (75,76):
            for a,b in neighbors(point):
                # Put the boat two blocks from shore, with clear water between it and the bank.
                launch = (2*a-x,2*b-z)
                if launch in water_route:
                    walk = [(i,floor(i,j)+1,j) for i,j in path_to(land_route,point)]
                    return {"roadToBankFeetXYZ":walk, "launchWaterXYZ":[launch[0],75,launch[1]],
                            "launchToSurveyedRiverXZ":path_to(water_route,launch)[::-1],
                            "maxGroundHeightChange":max(abs(u[1]-v[1]) for u,v in zip(walk,walk[1:])),
                            "minimumWalkHeadroom":3,"boatFootprintWidth":3,
                            "minimumBoatWaterDepth":1,"minimumBoatHeadroom":2}
        for p in neighbors(point):
            if not (-52 <= p[0] <= -18 and -581 <= p[1] <= -568) or p in land_route:
                continue
            next_h = floor(*p)
            if next_h is not None and abs(next_h-h)<=1:
                land_route[p] = point
                search.append(p)
    raise ValueError("Could not connect the road and river")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("world",type=Path)
    args = parser.parse_args()
    print(json.dumps(survey(args.world),indent=2))
