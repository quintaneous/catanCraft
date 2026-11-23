#!/usr/bin/env python3
"""Export a qualified clean server ZIP for an explicitly selected gameplay commit.

Usage: python3 export_test_kit.py KIT_DIRECTORY EXPECTED_GAMEPLAY_COMMIT NEW_ZIP
The output JSON records the archive checksum for the download index. Publishing
the archive and updating the owner-facing download link happen after verification.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import zipfile

from new_run import create, verify


def export(kit, commit, output):
    kit = kit.resolve(strict=True)
    output = output.absolute()
    if output.is_relative_to(kit) or output.exists() or output.is_symlink():
        raise ValueError("Choose a new ZIP outside the kit.")
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise ValueError("Expected the full gameplay commit.")
    policy = verify(kit)
    if policy["sourceCommit"] != commit or policy["readyForFocusedSoloTest"] is not True:
        raise ValueError("The kit needs completed qualification for the requested commit.")
    # Use the verified inventories explicitly. A tester may already have runs here.
    members = ["KIT.json", "KIT.sha256", *policy["toolFiles"]]
    members.extend("baseline/" + name for name in policy["baselineFiles"])
    with tempfile.TemporaryDirectory(dir=output.parent) as temp:
        work = Path(temp)
        archive = work / "server.zip"
        with zipfile.ZipFile(archive, "x", zipfile.ZIP_DEFLATED, compresslevel=1) as writer:
            for name in sorted(members):
                writer.write(kit / name, name)
        extracted = work / "extracted"
        with zipfile.ZipFile(archive) as reader:
            reader.extractall(extracted)
        # create() rechecks the extracted inventory before constructing an independent run.
        extracted_policy = json.loads((extracted / "KIT.json").read_bytes())
        if extracted_policy != policy:
            raise ValueError("The archived kit manifest differs from the verified source.")
        run = create(extracted)
        subprocess.run([sys.executable, "tools/launch_demo.py", "--verify-only"], cwd=run,
                       check=True, stdout=sys.stderr)
        with archive.open("rb") as data:
            archive_hash = hashlib.file_digest(data, "sha256").hexdigest()
        package = json.loads((run / "PACKAGE.json").read_bytes())
        # Publish complete bytes without replacing an existing release on a concurrent run.
        os.link(archive, output)
        return {"archive": output.name, "bytes": output.stat().st_size, "sha256": archive_hash,
                "sourceCommit": commit, "mods": package["modFiles"], "freshRunVerified": True,
                "dedicatedServerStartedByExporter": False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("kit", type=Path)
    parser.add_argument("commit")
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    try:
        result = export(args.kit, args.commit, args.output)
    except (ValueError, OSError, subprocess.CalledProcessError, zipfile.BadZipFile) as error:
        parser.exit(1, f"Export failed: {error}\n")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
