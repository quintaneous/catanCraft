#!/usr/bin/env python3
"""Read-only independent trajectory summary and local swept-mask extraction.

Does not certify source blocks from JSON alone: native tests perform those checks.
Requires exactly eight successful native trajectory records and one exact source SHA.
"""
import argparse
import json
import math
from pathlib import Path


def require(value, reason):
    if not value:
        raise ValueError(reason)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path)
    parser.add_argument("source")
    args = parser.parse_args()
    require(len(args.source) == 40 and all(c in "0123456789abcdef" for c in args.source), "source SHA")
    cases = []
    seen = set()
    clearance = set()
    support = set()
    for case in range(8):
        proof = json.loads((args.directory / f"catancraft_vehicle50_{case}.json").read_text())
        require(proof["source"] == args.source and proof["case"] == case, "source/case")
        require(proof["result"] == "PASS" and proof["routeQualified"] is True, "unsuccessful route")
        require(proof["stop"] == "QUALIFIED_DEPARTURE" and proof["postSpawnPoseWrites"] == 0, "incomplete/forced route")
        require(proof["sourceCellsUnchanged"] == proof["stagedCells"] == 18400, "changed source")
        require(proof["naturalServerTicks"] is True and proof["controlPath"] == "VehicleMovementMessage.handler", "wrong control path")
        require(proof["rotation"] in [0, 180] and proof["turn"] in [-1, 1], "orientation")
        key = (proof["vehicle"], proof["rotation"], proof["turn"])
        require(key not in seen, "duplicate case")
        seen.add(key)
        require(key[0] in ["sodayo_pick_up", "lav_150"], "vehicle")
        parts = 8 if key[0] == "sodayo_pick_up" else 10
        rows = proof["trajectory"]
        require(100 <= len(rows) <= 1901, "bounded sample count")
        previous = None
        length = 0
        for row in rows:
            require(all(math.isfinite(row[n]) for n in ["x", "y", "z", "yaw", "pitch", "roll", "speed", "energy"]), "nonfinite row")
            require(not row["horizontalCollision"], "native collision")
            require(len(row["nativeBounds"]) == parts, "native part count")
            require(row.get("keys", 0) in [0, 1, 2, 4, 5, 6, 8, 9, 10, 16, 17, 18, 20, 21, 22, 24, 25, 26], "unreviewed native key")
            error = 0
            if previous:
                require(row["tick"] == previous["tick"] + 1, "observation gap")
                require(row["entityTick"] > previous["entityTick"] and row["gameTime"] > previous["gameTime"], "no natural tick")
                require(row["energy"] <= previous["energy"], "energy replenished")
                length += math.hypot(row["x"] - previous["x"], row["z"] - previous["z"])
                angle = abs(math.remainder(row["yaw"] - previous["yaw"], 360)) + abs(row["pitch"] - previous["pitch"]) + abs(row["roll"] - previous["roll"])
                error = 15 * math.radians(angle) ** 2 / 8 + .00001
            for i, bounds in enumerate(row["nativeBounds"]):
                require(len(bounds) == 6 and all(math.isfinite(v) for v in bounds), "part bounds")
                old = previous["nativeBounds"][i] if previous else bounds
                low = [min(bounds[n], old[n]) - (error if n != 1 else 0) for n in range(3)]
                high = [max(bounds[n + 3], old[n + 3]) + (error if n != 1 else 0) for n in range(3)]
                sign = 1 if proof["rotation"] == 0 else -1
                for x in range(math.floor(low[0]), math.floor(high[0]) + 1):
                    for z in range(math.floor(low[2]), math.floor(high[2]) + 1):
                        lx, lz = sign * (x - proof["anchorX"]), sign * (z - proof["anchorZ"])
                        require(-29 <= lx <= 10 and -22 <= lz <= 23, "sweep beyond authored source")
                        support.add((lx, -1, lz))
                        for y in range(math.floor(low[1]), math.floor(high[1]) + 1):
                            ly = y - proof["anchorY"]
                            require(-2 <= ly <= 7, "vertical sweep beyond source")
                            clearance.add((lx, ly, lz))
            previous = row
        heading = math.remainder(rows[-1]["yaw"] - rows[0]["yaw"], 360)
        require(abs(heading - 90 * proof["turn"]) <= 5, "final road heading")
        require(rows[-1]["speed"] < .002 and rows[-1]["phase"] == "FINAL_BRAKE", "not stopped")
        require(rows[-1]["energy"] < proof["initialEnergy"], "no consumption")
        require(length > 20, "insufficient departure distance")
        cases.append({"case": case, "vehicle": key[0], "rotation": key[1], "turn": key[2], "samples": len(rows), "pathLength": length, "heading": heading, "energyUsed": proof["initialEnergy"] - rows[-1]["energy"]})
    print(json.dumps({"source": args.source, "cases": cases, "clearance": sorted(clearance), "support": sorted(support), "scope": "Native trajectory verification and conservative cell masks; production authority remains separate."}, indent=2))


if __name__ == "__main__":
    main()
