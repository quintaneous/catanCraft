#!/usr/bin/env python3
"""Reproduce verifier refusal/preservation checks; never start Minecraft.
Run from checkout: python3 tools/sec01_035/check_verifier.py
Requires the prepared build/sec01-035-runtime input inventory.
Prints JSON evidence to stdout. Test clones use hard links, but mutations replace
links before writing; canonical runtime files are never edited.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

PROJECT = Path(__file__).resolve().parents[2]
VERIFIER = PROJECT / "tools/sec01_035/prepare_runtime.py"
RUNTIME = PROJECT / "build/sec01-035-runtime"

def sha256(data):
    return hashlib.sha256(data).hexdigest()

def snapshot(root):
    result = {}
    for path in sorted(root.rglob("*")):
        relative = path.relative_to(root).as_posix()
        if path.is_symlink():
            result[relative] = {"kind": "symlink", "target": os.readlink(path)}
        elif path.is_file():
            result[relative] = {"kind": "file", "sha256": sha256(path.read_bytes()),
                                "mtime_ns": path.stat().st_mtime_ns}
    return result

def replace_file(path, data):
    # unlink only this disposable clone's hard link, never write shared inodes
    path.unlink()
    path.write_bytes(data)

def forge_manifest(path):
    data = json.loads(path.read_text())
    data["artifacts"][0]["coordinate"] = "attacker:unreviewed:1"
    replace_file(path, json.dumps(data).encode())

def verify(root):
    command = ["python3", str(VERIFIER), "--root", str(root), "--verify-only"]
    run = subprocess.run(command, text=True, capture_output=True)
    return {"command": command, "exit_code": run.returncode,
            "stdout": run.stdout, "stderr": run.stderr}

baseline = snapshot(RUNTIME)
manifest = json.loads((RUNTIME / "runtime-artifacts.json").read_text())
jar = manifest["artifacts"][0]["staged"]
original = manifest["inputs"][0]["original"]
cases = [
    ("forged manifest", lambda root: forge_manifest(root / "runtime-artifacts.json")),
    ("modified staged JAR", lambda root: replace_file(root / jar, b"unreviewed bytes")),
    ("modified staged POM", lambda root: replace_file((root / jar).with_suffix(".pom"), b"<project>unreviewed</project>")),
    ("modified published input", lambda root: replace_file(root / original, b"unreviewed input")),
    ("unknown file", lambda root: (root / "preserve-me.txt").write_text("unrelated data")),
    ("symlink", lambda root: (root / "preserve-link").symlink_to(RUNTIME / "runtime-artifacts.json")),
]
report = {"schema": "catancraft-sec01-verifier-evidence-v1",
          "commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=PROJECT, text=True).strip(),
          "verifier_sha256": sha256(VERIFIER.read_bytes()),
          "init_sha256": sha256((VERIFIER.parent / "validate.gradle").read_bytes()),
          "check_script_sha256": sha256(Path(__file__).read_bytes()),
          "manifest_sha256": sha256((RUNTIME / "runtime-artifacts.json").read_bytes()),
          "runtime_file_count": len(baseline), "negative_cases": [],
          "native_tests_executed": False}
with tempfile.TemporaryDirectory(prefix="sec01-verify-negative-", dir=PROJECT / "build") as area:
    for number, (label, mutate) in enumerate(cases):
        clone = Path(area) / str(number)
        shutil.copytree(RUNTIME, clone, copy_function=os.link)
        mutate(clone)
        before = snapshot(clone)
        result = verify(clone)
        result.update({"name": label, "fixture_bytes_links_mtimes_preserved": before == snapshot(clone)})
        assert result["exit_code"] == 1, result
        assert result["fixture_bytes_links_mtimes_preserved"], result
        report["negative_cases"].append(result)
canonical = verify(RUNTIME)
assert canonical["exit_code"] == 0, canonical
report["canonical_verify"] = canonical
report["canonical_bytes_mtimes_unchanged"] = baseline == snapshot(RUNTIME)
assert report["canonical_bytes_mtimes_unchanged"], "Canonical runtime changed"
report["canonical_inventory_before_and_after"] = baseline
report["result"] = "PASS"
print(json.dumps(report, sort_keys=True, indent=2))
