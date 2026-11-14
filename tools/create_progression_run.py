#!/usr/bin/env python3
"""Prepare a NEW isolated progression run from pinned pristine inputs; never reset a save."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import shutil
import sys
import tempfile
import uuid

from package_authored_demo import assemble, digest, pinned
from prepare_fresh_demo import InputError, checked_path, read_regular, sha256
from progression_ladder import encoded, plan_for, results_template

PROFILE = {
    "gamemode": "adventure", "force-gamemode": "true", "difficulty": "normal",
    "hardcore": "false", "pvp": "true", "allow-flight": "false",
    "spawn-protection": "0", "max-players": "1",
}


def create_run(input_root: Path, manifest: Path, expected_sha: str, output: Path) -> dict:
    root = checked_path(input_root, directory=True)
    raw = read_regular(manifest)
    if sha256(raw) != digest(expected_sha):
        raise InputError("Original baseline manifest checksum mismatch")
    original = json.loads(raw)
    derived = json.loads(raw)
    if any(name.startswith("datapacks/") for name in original["world"]["files"]):
        raise InputError("Normal progression needs a separately reviewed no-grant datapack contract; use pristine no-datapack inputs for now")
    run_id = str(uuid.uuid4())
    derived["bootstrap"]["authorization"]["worldId"] = run_id
    maps = [r for r in original["config"] if r["target"] == "config/catancraft/map.json"]
    if len(maps) != 1:
        raise InputError("One pinned map configuration is required")
    plan = plan_for(pinned(root, maps[0]), run_id, original["sourceCommit"], original["jar"]["sha256"])
    destination = Path(os.path.abspath(output))
    checked_path(destination.parent, directory=True)
    if destination.exists() or destination.is_symlink() or destination == root or root in destination.parents:
        raise InputError("Choose a NEW run folder outside the immutable inputs; existing runs are never reset")
    if any(p.lower() in {"mods", "saves"} for p in destination.parts):
        raise InputError("Do not prepare runs inside installed mods or saves")
    # Assemble into an exclusively owned staging directory, then publish to a new
    # destination. A concurrent destination creation must not be overwritten.
    with tempfile.TemporaryDirectory(prefix=".progression-stage-", dir=destination.parent) as temporary:
        stage = Path(temporary)
        derived_raw = encoded(derived)
        derived_file = stage / "derived-input.json"
        derived_file.write_bytes(derived_raw)
        package = assemble(root, derived_file, sha256(derived_raw), stage / "server")
        server = stage / "server"
        properties = dict(package["serverProperties"])
        properties.update(PROFILE)
        properties_raw = "".join(f"{key}={value}\n" for key, value in sorted(properties.items())).encode()
        (server / "server.properties").write_bytes(properties_raw)
        package["serverProperties"] = properties
        package["initialServerPropertiesSHA256"] = sha256(properties_raw)
        provenance = {
            "schemaVersion": 1, "kind": "catancraft-fresh-progression-run", "runId": run_id,
            "baselineManifestSHA256": expected_sha, "derivedManifestSHA256": sha256(derived_raw),
            "worldArchiveSHA256": original["world"]["sha256"],
            "sourceCommit": original["sourceCommit"], "jarSHA256": original["jar"]["sha256"],
            "profile": "normal-economy-intent-no-grants", "sourceWorldMetadataUnchanged": True,
            "fixtureOnly": package["fixtureOnly"], "gameplayQualified": False,
            "blockers": ["Complete matching Forge/mod/content runtime and review authored world gamerules",
                         "Validate normal startup and all-city progression with no funding or free items",
                         "Resolve current shop, vehicle, wall and shared bootstrap coverage gaps",
                         "Validate launcher platforms and authenticated solo client interaction"],
        }
        additions = {
            "evidence/BASELINE_INPUT_MANIFEST.json": raw,
            "PROGRESSION_RUN.json": encoded(provenance),
            "PROGRESSION_PLAN.json": encoded(plan),
            "README_PROGRESSION.txt": (
                "CATANCRAFT FRESH PROGRESSION RUN — DEVELOPER PREPARATION, NOT A QUALIFIED RELEASE\n"
                "Authored map retained; no inherited players, nations or purchase histories.\n"
                "This folder is one run. To start over, create a DIFFERENT run from the original inputs.\n"
                "Never delete progress, copy a played run, move a bound run, or extract over a live server.\n"
                "Normal-economy intent: Adventure, Normal difficulty, no automatic grants or purchases.\n"
                "Authored level.dat/gamerules remain unchanged and still require gameplay review.\n"
                "Do not use debug funding, creative items or optional showroom datapacks for progression evidence.\n"
                "If you need accelerated developer tests, keep them in a separately labeled run.\n"
                "Required external runtime/mod/content files are NOT bundled or certified by this tool.\n"
                "Read Minecraft's EULA and personally set eula=true only if you agree.\n"
                "START_DEMO scripts verify prerequisites and bind the first launch; they do not reset progress.\n"
                "Server is loopback-only, authenticated, whitelisted and one-player.\n"
                "Every checkpoint needs fresh evidence for all earlier applicable checks across A-P.\n"
                "PROGRESSION_PLAN is a stage-level checklist, not native test implementation.\n"
                "No gameplay or owner acceptance is implied by successful assembly. See PROGRESSION_RUN blockers.\n"
            ).encode(),
        }
        # Replace historical showroom instructions with this run's explicit scope.
        additions["README_DEMO.txt"] = additions["README_PROGRESSION.txt"]
        for name, data in additions.items():
            (server / name).write_bytes(data)
            package["immutableFiles"][name] = sha256(data)
        # Templates are mutable observations, intentionally outside immutable runtime files.
        for checkpoint in plan["stages"]:
            path = server / "evidence" / f"results-{checkpoint}.json"
            path.write_bytes(encoded(results_template(plan, checkpoint)))
        package["progressionRun"] = {"runId": run_id, "planSHA256": sha256(encoded(plan)),
                                     "baselineManifestSHA256": expected_sha, "gameplayQualified": False}
        data = encoded(package)
        (server / "PACKAGE.json").write_bytes(data)
        (server / "PACKAGE.sha256").write_text(f"{sha256(data)}  PACKAGE.json\n")
        destination.mkdir()  # exclusive claim; never merge into a pre-existing directory
        # A transfer failure leaves the exclusively created partial output for
        # inspection. Never remove a pre-existing or previously played run.
        for child in server.iterdir():
            shutil.move(str(child), destination / child.name)
    return provenance


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input-root", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--manifest-sha256", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = create_run(args.input_root, args.manifest, args.manifest_sha256, args.output)
    except (InputError, OSError, ValueError, KeyError, TypeError) as ex:
        print(f"REFUSED: {ex}", file=sys.stderr)
        return 2
    print(json.dumps(result, indent=2))
    print("PREPARED: new isolated run; no server started, no gameplay qualification")
    return 0


if __name__ == "__main__":
    sys.exit(main())
