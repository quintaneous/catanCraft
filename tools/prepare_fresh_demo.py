#!/usr/bin/env python3
"""Stage verified demo components only; never copy worlds or installed mods.

Python 3.11+, standard library only. This tool never declares a runnable release.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import sys
import tomllib
import zipfile
from io import BytesIO

ASSET_PINS = {
    "schematics_bundle.zip": "2a7a362047a6672b802c0077cd58db236183c10a69cc69dc928c64859d985edc",
    "R5_READ_ONLY_PROBE_INDEX.zip": "4f5a445347adbb62e73d1c38bfab5ff850c7307733a619847d9eeac9245834ba",
}
LIMIT = 256 * 1024 * 1024
MISSING = [
    {"id": "clean_authored_world", "reason": "No clean authored CatanCraft world has been supplied and verified for this staging operation. World saves are never copied by this tool."},
    {"id": "approved_sector_wall_art", "reason": "The current representative basic/advanced compass-sector Blender brief and approved assets are not supplied by the verified original handoff assets."},
    {"id": "matching_launcher_and_modpack", "reason": "A matching isolated launcher/modpack profile, configuration and dependency hashes have not been supplied and verified."},
    {"id": "sbw_runtime_verification", "reason": "Uploaded log evidence identifies Superb Warfare 0.8.9.2 only; the intended artifact, weapon IDs, ammunition, dimensions, damage and anti-packing behavior remain unverified."},
    {"id": "fresh_initialization_contract", "reason": "Existing starter assets and R7 maintenance authorization do not implement a verified fresh-world initialization with current visuals; no world authorization is created here."},
]

class InputError(ValueError):
    pass

def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def checked_path(path: Path, *, directory: bool = False) -> Path:
    """Reject symlinks in every existing component, including the input root."""
    path = Path(os.path.abspath(path))
    for part in [*reversed(path.parents), path]:
        if part.is_symlink():
            raise InputError(f"Symlink paths are forbidden: {part}")
    if directory:
        if not path.is_dir():
            raise InputError(f"Required directory unavailable: {path}")
    elif not path.is_file():
        raise InputError(f"Required file unavailable: {path}")
    return path

def read_regular(path: Path) -> bytes:
    path = checked_path(path)
    flags = os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)
    with os.fdopen(os.open(path, flags), "rb") as stream:
        info = os.fstat(stream.fileno())
        if not stat.S_ISREG(info.st_mode) or info.st_size > LIMIT:
            raise InputError(f"Not a bounded regular input file: {path}")
        data = stream.read(LIMIT + 1)
    if len(data) > LIMIT:
        raise InputError(f"Input exceeds transfer limit: {path}")
    return data

def inspect_zip(data: bytes, label: str) -> zipfile.ZipFile:
    try:
        archive = zipfile.ZipFile(BytesIO(data))
        if sum(m.file_size for m in archive.infolist()) > LIMIT:
            raise InputError(f"Expanded archive exceeds safe inspection limit: {label}")
        names = set()
        for member in archive.infolist():
            name = member.filename
            rel = PurePosixPath(name)
            if "\\" in name or rel.is_absolute() or ".." in rel.parts or re.match(r"^[A-Za-z]:", name):
                raise InputError(f"Unsafe archive path in {label}: {name}")
            if name in names or stat.S_ISLNK(member.external_attr >> 16):
                raise InputError(f"Duplicate/symlink archive member in {label}: {name}")
            names.add(name)
        if archive.testzip() is not None:
            raise InputError(f"Archive CRC mismatch: {label}")
        return archive
    except (zipfile.BadZipFile, RuntimeError, OSError) as ex:
        raise InputError(f"Invalid archive {label}: {ex}") from ex

def inspect_jar(data: bytes) -> dict:
    try:
        with inspect_zip(data, "CatanCraft JAR") as jar:
            metadata = tomllib.loads(jar.read("META-INF/mods.toml").decode("utf-8"))
        mods = [m for m in metadata.get("mods", []) if m.get("modId") == "catancraft"]
        if len(mods) != 1 or not isinstance(mods[0].get("version"), str) or "${" in mods[0]["version"]:
            raise InputError("JAR must contain one versioned catancraft mod entry")
        deps = metadata.get("dependencies", {}).get("catancraft", [])
        versions = {d.get("modId"): d.get("versionRange", "") for d in deps}
        if "47.4.10" not in versions.get("forge", "") or "1.20.1" not in versions.get("minecraft", ""):
            raise InputError("JAR metadata must identify Minecraft 1.20.1 / Forge 47.4.10 compatibility")
        return {"modId": "catancraft", "version": mods[0]["version"], "minecraft": "1.20.1", "forgeBaseline": "47.4.10"}
    except (KeyError, UnicodeError, tomllib.TOMLDecodeError) as ex:
        raise InputError(f"Missing/invalid CatanCraft JAR metadata: {ex}") from ex

def prepare(handoff_root: Path, jar: Path, jar_sha256: str, output: Path) -> dict:
    if not re.fullmatch(r"[0-9a-fA-F]{64}", jar_sha256):
        raise InputError("--jar-sha256 must be an explicitly supplied SHA-256 digest")
    root = checked_path(handoff_root, directory=True)
    destination = Path(os.path.abspath(output))
    checked_path(destination.parent, directory=True)
    if destination.exists() or destination.is_symlink():
        raise InputError(f"Output already exists; preserve it and select a new directory: {destination}")
    if any(part.lower() in {"mods", "saves"} for part in Path(os.path.abspath(jar)).parts):
        raise InputError("Use a build/release artifact, never an installed mods or saves path")
    jar_bytes = read_regular(jar)
    if sha256(jar_bytes) != jar_sha256.lower():
        raise InputError("Explicit JAR checksum mismatch; nothing staged")
    metadata = inspect_jar(jar_bytes)
    components = {"artifacts/catancraft.jar": jar_bytes}
    entries = [{"path": "artifacts/catancraft.jar", "sha256": sha256(jar_bytes), "bytes": len(jar_bytes), "metadata": metadata, "verification": "explicit caller-supplied digest; not independent gameplay certification"}]
    for name, pin in sorted(ASSET_PINS.items()):
        relative = Path("handoff/assets") / name
        content = read_regular(root / relative)
        if sha256(content) != pin:
            raise InputError(f"Original handoff asset checksum mismatch: {relative}")
        with inspect_zip(content, name):
            pass
        target = f"config/catancraft/{name}"
        components[target] = content
        entries.append({"path": target, "sha256": pin, "bytes": len(content), "source": relative.as_posix(), "verification": "immutable verified original handoff pin"})
    manifest = {
        "schemaVersion": 1, "status": "NOT_READY", "packageKind": "verified-components-only",
        "runnable": False, "worldIncluded": False, "installedModsCopied": False,
        "components": sorted(entries, key=lambda e: e["path"]), "missingPrerequisites": MISSING,
        "requiredReview": "Supply and verify missing inputs separately; authorize a new disposable world and complete fresh-initialization/runtime checks before creating a runnable demo release.",
    }
    manifest_bytes = (json.dumps(manifest, sort_keys=True, indent=2) + "\n").encode()
    components["MANIFEST.json"] = manifest_bytes
    components["MANIFEST.sha256"] = f"{sha256(manifest_bytes)}  MANIFEST.json\n".encode()
    components["NOT_READY.txt"] = ("NOT_READY — COMPONENT STAGING ONLY; NOT A RUNNABLE DEMO OR RELEASE\n\n" + "\n".join(f"- {m['id']}: {m['reason']}" for m in MISSING) + "\n\nNo world, installed mods, authorization marker, server settings or launcher is included.\n").encode()
    # Exclusive creation only after every source is verified. Never merge into an existing tree.
    destination.mkdir()
    try:
        for name, content in sorted(components.items()):
            path = destination / name
            path.parent.mkdir(parents=True, exist_ok=True)
            with path.open("xb") as stream:
                stream.write(content)
            if sha256(path.read_bytes()) != sha256(content):
                raise InputError(f"Staged file readback mismatch: {name}")
    except BaseException:
        shutil.rmtree(destination)
        raise
    return manifest

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--handoff-root", type=Path, required=True, help="Verified CatanCraft-Dev input root")
    parser.add_argument("--jar", type=Path, required=True, help="Explicit reviewable built JAR, never an installed mods directory")
    parser.add_argument("--jar-sha256", required=True, help="Expected digest from the matching build/release evidence")
    parser.add_argument("--output", type=Path, required=True, help="New component-staging directory; must not exist")
    args = parser.parse_args()
    try:
        manifest = prepare(args.handoff_root, args.jar, args.jar_sha256, args.output)
    except (InputError, OSError) as ex:
        print(f"REFUSED: {ex}", file=sys.stderr)
        return 2
    print(f"NOT_READY: {len(manifest['components'])} verified components staged; {len(MISSING)} prerequisites remain. Review MANIFEST.json and NOT_READY.txt.")
    return 0

if __name__ == "__main__":
    sys.exit(main())
