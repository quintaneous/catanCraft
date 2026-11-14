#!/usr/bin/env python3
"""Describe cumulative all-city checks; validate submitted evidence, never run Minecraft."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys

from package_authored_demo import InputError, read_regular, relative, sha256

STAGES = (
    ("clean_start", "Pristine progress, complete assets and first server startup before founding"),
    ("founding", "Normal founding/claim, automatic base preparation, TH1 economy and starter access"),
    ("tier_2", "Earn TH2 with needed producers/trade; test every currently eligible building and service"),
    ("tier_3", "Earn TH3 with interleaved production/factories; test newly unlocked and earlier gameplay"),
    ("tier_4", "Earn TH4, upgrade eligible production/factories and retain earlier purchases/services"),
    ("tier_5", "Earn TH5; cover all applicable tiers/slots, TaCZ/rewards, vehicles/Fuel and mixed walls"),
    ("persistence", "Restart/interruption recovery and isolation of prior purchases"),
)


def plan_for(map_bytes: bytes, run_id: str, source: str, jar_sha: str) -> dict:
    data = json.loads(map_bytes)
    cities = data["territories"]
    if len(cities) != 16 or {c["id"] for c in cities} != set("abcdefghijklmnop"):
        raise InputError("Current ladder requires exactly the configured A-P roster; review new maps explicitly")
    rows = []
    for city in sorted(cities, key=lambda c: c["id"]):
        center = city["cityCenter"]
        if center["rotation"] not in (0, 180):
            raise InputError("Unreviewed city orientation")
        plots = [p["id"] for p in city["buildingPlots"]
                 if p["id"] in {"plot_1", "plot_3", "plot_4"}
                 and p["id"] not in city["reservedPlotIds"]]
        if len(plots) != len(set(plots)) or not plots:
            raise InputError("Missing or duplicate ordinary industry plots")
        for stage, description in STAGES:
            rows.append({"id": f"{city['id']}/{stage}", "city": city["id"],
                         "stage": stage, "description": description,
                         "center": center, "managementAnchor": city["managementAnchor"],
                         "resources": city["resources"], "industryPlots": plots,
                         "foundingRoute": "start" if city.get("startSlot", 0) else "claim"})
    return {"schemaVersion": 1, "kind": "catancraft-progression-plan",
            "runId": run_id, "sourceCommit": source, "jarSHA256": jar_sha,
            "mapSHA256": sha256(map_bytes), "stages": [s[0] for s in STAGES],
            "checks": rows,
            "scope": "Stage-level obligations, not implemented native test adapters or per-variant results. "
                     "Each observation must evidence all applicable levels/slots and cross-city refusals. "
                     "Separate scenarios are needed for mutually exclusive factory layouts."}


def results_template(plan: dict, checkpoint: str) -> dict:
    if (plan.get("schemaVersion") != 1 or plan.get("kind") != "catancraft-progression-plan"
            or plan.get("stages") != [s[0] for s in STAGES]):
        raise InputError("Unknown or incomplete progression plan")
    rows = plan.get("checks", [])
    expected = {f"{city}/{stage}" for city in "abcdefghijklmnop" for stage, _ in STAGES}
    if (len(rows) != len(expected) or {r["id"] for r in rows} != expected
            or any(r["id"] != f"{r['city']}/{r['stage']}" for r in rows)):
        raise InputError("Plan must cover all A-P cities and all ladder stages")
    if checkpoint not in plan["stages"]:
        raise InputError("Unknown progression checkpoint")
    stages = plan["stages"][:plan["stages"].index(checkpoint) + 1]
    return {"schemaVersion": 1, "kind": "catancraft-progression-results",
            "runId": plan["runId"], "planSHA256": sha256(encoded(plan)),
            "checkpoint": checkpoint, "fundingUsed": False,
            "checks": [{"id": r["id"], "status": "NOT_RUN", "evidence": []}
                       for r in plan["checks"] if r["stage"] in stages]}


def encoded(value: dict) -> bytes:
    return (json.dumps(value, sort_keys=True, indent=2) + "\n").encode()


def evaluate(plan: dict, results: dict, evidence_root: Path, checkpoint: str) -> dict:
    expected = results_template(plan, checkpoint)
    for key in ("schemaVersion", "kind", "runId", "planSHA256", "checkpoint"):
        if results.get(key) != expected[key]:
            raise InputError(f"Foreign/stale result identity: {key}")
    if results.get("fundingUsed") is not False:
        raise InputError("Funded runs cannot qualify normal-economy progression")
    rows = results.get("checks")
    if not isinstance(rows, list) or any(not isinstance(r, dict) for r in rows):
        raise InputError("Missing result rows")
    ids = [r.get("id") for r in rows]
    required = {r["id"] for r in expected["checks"]}
    if len(ids) != len(set(ids)) or set(ids) != required:
        raise InputError("Result rows must include every city and every earlier stage exactly once")
    blocked = []
    for row in rows:
        if row.get("status") not in {"PASS", "FAIL", "BLOCKED", "NOT_RUN"}:
            raise InputError("Unknown result status; no unreviewed N/A exemption")
        if row["status"] != "PASS":
            blocked.append(row["id"])
            continue
        artifacts = row.get("evidence")
        if not isinstance(artifacts, list) or not artifacts:
            raise InputError(f"PASS requires retained evidence: {row['id']}")
        for artifact in artifacts:
            data = read_regular(evidence_root / relative(artifact["path"]))
            if not data or sha256(data) != artifact["sha256"]:
                raise InputError(f"Missing/changed/empty evidence: {row['id']}")
    return {"status": "BLOCKED" if blocked else "EVIDENCE_COMPLETE_REQUIRES_REVIEW",
            "checkpoint": checkpoint, "requiredChecks": len(required), "blockedChecks": blocked,
            "gameplayExecutedByThisTool": False, "evidenceSemanticsVerified": False,
            "freshExecutionVerified": False, "ownerAcceptance": False}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--plan", type=Path, required=True)
    parser.add_argument("--checkpoint", choices=[s[0] for s in STAGES], required=True)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--new-results", type=Path)
    group.add_argument("--results", type=Path)
    parser.add_argument("--evidence-root", type=Path)
    args = parser.parse_args()
    try:
        plan = json.loads(read_regular(args.plan))
        if args.new_results:
            # Exclusive creation: never replace an earlier checkpoint or failed record.
            from prepare_fresh_demo import checked_path
            checked_path(args.new_results.parent, directory=True)
            with args.new_results.open("xb") as out:
                out.write(encoded(results_template(plan, args.checkpoint)))
            print("Created NOT_RUN result template; no gameplay executed")
            return 0
        if args.evidence_root is None:
            raise InputError("--evidence-root is required with --results")
        report = evaluate(plan, json.loads(read_regular(args.results)), args.evidence_root, args.checkpoint)
        print(json.dumps(report, indent=2))
        return 0 if report["status"] == "EVIDENCE_COMPLETE_REQUIRES_REVIEW" else 2
    except (InputError, OSError, ValueError, KeyError, TypeError) as ex:
        print(f"REFUSED: {ex}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
