#!/usr/bin/env python3
"""Prepare/verify only the pinned published TaCZ 042 test runtime under this repo's build/.

No installed mods or worlds are read. Different existing files, symlinks and unknown
files refuse rather than being overwritten. --verify-only performs no downloads or
writes and returns a recomputed manifest for validate.gradle, never caller-supplied
coordinates or remapping flags. These optional inputs are not release dependencies.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile
import urllib.request
import zipfile

PROJECT = Path(__file__).resolve().parents[2]
GROUP = "audit.catancraft.shop42_tacz"
MARKER = b"CatanCraft TaCZ 042 0.1.42 pinned published runtime only\n"
SCHEMA = "catancraft-tacz42-published-runtime-v1"
USER_AGENT = "quintaneous/catanCraft TaCZ 042 reproducible audit"
TACZ = "tacz-forge-1.20.1-1.2.0.jar"
# name: (official Modrinth project, release id, version, bytes, SHA-256)
INPUTS = {
    TACZ: ("SzzJttH8", "QGCbNkTy", "1.2.0", 69001578,
           "1c2f2915f76908d396a0da577665c977564c279ccb89d459a00a29225aa45a92"),
}
# Exact reviewed JarJar records. No installed mod directories are consulted.
EMBEDDED = [
    ("com.github.mcmodderanchor", "simplebedrockmodel", "2.6.2-forge-mc1.20.1", "[2.6.2,)",
     "simplebedrockmodel-2.6.2-forge-mc1.20.1.jar", True, 1289143,
     "8b2008ceadae0174994ef929c3275e16b5d547c478364783dc4c68e644f115ad", "simplebedrockmodel"),
    ("org.apache.commons", "commons-math3", "3.6.1", "[3.6.1,)",
     "commons-math3-3.6.1.jar", False, 2213560,
     "1e56d7b058d28b65abd256b8458e3885b674c1d588fa43cd7d1cbb9c7ef2b308", "commons-math3"),
    ("com.github.FiguraMC.luaj", "luaj-core", "3.0.8-figura", "[3.0.8,)",
     "luaj-core-3.0.8-figura.jar", False, 294956,
     "6050678ca1dad5e2a4c401cc9ce968d282d30105b2ea46f79c30abccdfa30fbe", "luaj-core"),
    ("com.github.FiguraMC.luaj", "luaj-jse", "3.0.8-figura", "[3.0.8,)",
     "luaj-jse-3.0.8-figura.jar", False, 279768,
     "1783d249b5152c6f110c1bcca5fb0d4ef0075fc291c912f6b74e2bb61472e95e", "luaj-jse"),
    ("org.apache.bcel", "bcel", "6.6.1", "[6.6.1,)", "bcel-6.6.1.jar", False, 695309,
     "50e909f555c772285d0f282d22ec7ca246010fa6996d40226357a28555445904", "bcel"),
    ("io.github.llamalad7", "mixinextras-forge", "0.3.6", "[0.3.6,)",
     "mixinextras-forge-0.3.6.jar", False, 159556,
     "66eeeea6d1751c9f8f3abd32938fe5ee8113ae1a2730a9a29489300ffe02dd0f", "mixinextras-forge"),
]


def encoded(value: object) -> bytes:
    return (json.dumps(value, sort_keys=True, indent=2) + "\n").encode()


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def fail(message: str) -> None:
    raise ValueError(message)


def checked_root(value: str) -> Path:
    candidate = Path(value)
    if not candidate.is_absolute():
        candidate = PROJECT / candidate
    # Resolve only after rejecting symbolic links along the requested path.
    for part in (candidate, *candidate.parents):
        if part.is_symlink():
            fail(f"Runtime root contains a symlink: {part}")
    root = candidate.resolve()
    build = PROJECT / "build"
    if not root.is_relative_to(build) or root == build:
        fail("Runtime root must be a dedicated directory beneath this checkout's build/")
    return root


def maven_path(artifact: str, version: str, suffix: str = "jar") -> str:
    return f"maven/{GROUP.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.{suffix}"


def pom(artifact: str, version: str) -> bytes:
    return (f'<?xml version="1.0" encoding="UTF-8"?>\n'
            f'<project xmlns="http://maven.apache.org/POM/4.0.0">'
            f'<modelVersion>4.0.0</modelVersion><groupId>{GROUP}</groupId>'
            f'<artifactId>{artifact}</artifactId><version>{version}</version>'
            '<packaging>jar</packaging></project>\n').encode()


def allowed_files() -> set[str]:
    names = {".catancraft-tacz42-runtime-v1", "runtime-artifacts.json", "download-provenance.json"}
    names.update("inputs/" + name for name in INPUTS)
    pairs = [("tacz", "1.2.0")]
    for _, _, version, _, name, _, _, _, artifact in EMBEDDED:
        names.add("embedded/" + name)
        pairs.append((artifact, version))
    for artifact, version in pairs:
        names.update(maven_path(artifact, version, suffix) for suffix in ("jar", "pom"))
    return names


def check_tree(root: Path) -> None:
    allowed = allowed_files()
    directories = {str(p) for name in allowed for p in Path(name).parents if str(p) != "."}
    if root.exists() and not root.is_dir():
        fail("Runtime root is not a directory")
    if root.exists():
        for path in root.rglob("*"):
            relative = path.relative_to(root).as_posix()
            if path.is_symlink():
                fail(f"Runtime contains a symlink: {relative}")
            if path.is_dir():
                if relative not in directories:
                    fail(f"Unknown runtime directory retained: {relative}")
            elif not path.is_file() or relative not in allowed:
                fail(f"Unknown runtime file retained: {relative}")


def write_once(path: Path, data: bytes) -> None:
    if path.exists() or path.is_symlink():
        if path.is_symlink() or not path.is_file() or path.read_bytes() != data:
            fail(f"Different existing runtime file retained: {path}")
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    handle, temporary = tempfile.mkstemp(prefix=".tacz42-writing-", dir=path.parent)
    try:
        with os.fdopen(handle, "wb") as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        # Link instead of replace: do not overwrite a concurrent or unknown file.
        try:
            os.link(temporary, path)
        except FileExistsError:
            if path.is_symlink() or not path.is_file() or path.read_bytes() != data:
                fail(f"Runtime destination changed concurrently: {path}")
    finally:
        Path(temporary).unlink(missing_ok=True)


def get(url: str, maximum: int) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=60) as response:
        if response.geturl() != url:
            fail("Unexpected redirect for a pinned Modrinth input")
        data = response.read(maximum + 1)
    if len(data) > maximum:
        fail("Pinned Modrinth response exceeds expected bound")
    return data


def verify_input(name: str, data: bytes) -> None:
    _, _, _, size, digest = INPUTS[name]
    if len(data) != size or sha256(data) != digest:
        fail(f"Pinned published input mismatch: {name}")


def input_record(name: str, data: bytes) -> dict:
    project, version, label, size, digest = INPUTS[name]
    return {"name": name, "original": "inputs/" + name, "bytes": size,
            "sha256": digest, "sha1": hashlib.sha1(data).hexdigest(),
            "sha512": hashlib.sha512(data).hexdigest(),
            "project": project, "version_id": version, "version": label,
            "metadata_url": f"https://api.modrinth.com/v2/version/{version}",
            "url": f"https://cdn.modrinth.com/data/{project}/versions/{version}/{name}"}


def download_inputs(root: Path) -> dict[str, bytes]:
    result = {}
    metadata = {}
    for name, (project, version, label, size, _) in INPUTS.items():
        if version not in metadata:
            metadata[version] = json.loads(get(f"https://api.modrinth.com/v2/version/{version}", 2 * 1024 * 1024))
        document = metadata[version]
        if (document.get("id") != version or document.get("project_id") != project
                or document.get("version_number") != label or "forge" not in document.get("loaders", [])
                or "1.20.1" not in document.get("game_versions", [])):
            fail(f"Unexpected official release metadata: {version}")
        matches = [row for row in document["files"] if row.get("filename") == name]
        url = f"https://cdn.modrinth.com/data/{project}/versions/{version}/{name}"
        if len(matches) != 1 or matches[0].get("url") != url or matches[0].get("size") != size:
            fail(f"Unexpected official file metadata: {name}")
        row = matches[0]
        path = root / "inputs" / name
        data = path.read_bytes() if path.exists() else get(url, size)
        verify_input(name, data)
        if set(row.get("hashes", {})) != {"sha1", "sha512"}:
            fail(f"Missing published file hashes: {name}")
        for algorithm, digest in row["hashes"].items():
            if hashlib.new(algorithm, data).hexdigest() != digest:
                fail(f"Published {algorithm} mismatch: {name}")
        write_once(path, data)
        result[name] = data
    return result


def expected_runtime(inputs: dict[str, bytes]) -> tuple[dict, dict[str, bytes]]:
    import io
    files = {"inputs/" + name: data for name, data in inputs.items()}
    artifacts = []
    def add(artifact: str, version: str, original: str, data: bytes,
            deobfuscate: bool, embedded_path: str | None = None) -> None:
        staged = maven_path(artifact, version)
        files[staged] = data
        files[maven_path(artifact, version, "pom")] = pom(artifact, version)
        artifacts.append({"coordinate": f"{GROUP}:{artifact}:{version}", "original": original,
                          "embedded_path": embedded_path, "staged": staged,
                          "sha256": sha256(data), "bytes": len(data), "deobfuscate": deobfuscate})
    add("tacz", "1.2.0", "inputs/" + TACZ, inputs[TACZ], True)
    with zipfile.ZipFile(io.BytesIO(inputs[TACZ])) as archive:
        expected_rows = []
        for group, artifact, version, version_range, name, remap, size, digest, local in EMBEDDED:
            internal = "META-INF/jarjar/" + name
            expected_rows.append({"identifier": {"group": group, "artifact": artifact},
                                  "version": {"range": version_range, "artifactVersion": version},
                                  "path": internal, "isObfuscated": remap})
            data = archive.read(internal)
            if len(data) != size or sha256(data) != digest:
                fail(f"Pinned embedded dependency mismatch: {name}")
            original = "embedded/" + name
            files[original] = data
            add(local, version, original, data, remap, internal)
        if json.loads(archive.read("META-INF/jarjar/metadata.json")) != {"jars": expected_rows}:
            fail("Unexpected TaCZ embedded runtime inventory")
    records = [input_record(name, data) for name, data in inputs.items()]
    manifest = {"schema": SCHEMA, "minecraft": "1.20.1", "forge": "47.4.10",
                "inputs": records, "artifacts": artifacts}
    files[".catancraft-tacz42-runtime-v1"] = MARKER
    files["runtime-artifacts.json"] = encoded(manifest)
    files["download-provenance.json"] = encoded({"schema": SCHEMA, "verified_public_modrinth_inputs": records})
    return manifest, files


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default="build/shop42-tacz-runtime")
    parser.add_argument("--verify-only", action="store_true")
    parser.add_argument("--emit-manifest", action="store_true")
    args = parser.parse_args()
    root = checked_root(args.root)
    check_tree(root)
    if args.verify_only:
        if not root.is_dir():
            fail("Prepared runtime is missing")
        inputs = {name: (root / "inputs" / name).read_bytes() for name in INPUTS}
        for name, data in inputs.items():
            verify_input(name, data)
    else:
        inputs = download_inputs(root)
    manifest, files = expected_runtime(inputs)
    if set(files) != allowed_files():
        fail("Internal runtime inventory mismatch")
    if not args.verify_only:
        # Preflight every existing destination before installing any staged file.
        for name, data in files.items():
            path = root / name
            if path.exists() and (not path.is_file() or path.read_bytes() != data):
                fail(f"Different existing runtime file retained: {name}")
        for name, data in files.items():
            write_once(root / name, data)
    check_tree(root)
    for name, data in files.items():
        path = root / name
        if not path.is_file() or path.is_symlink() or path.read_bytes() != data:
            fail(f"Runtime verification failed: {name}")
    result = {"result": "PASS", "root": str(root), "verified_file_count": len(files),
              "runtime_artifact_count": len(manifest["artifacts"]), "published_input_count": len(INPUTS),
              "manifest_sha256": sha256(files["runtime-artifacts.json"]),
              "no_native_tests_executed": True}
    if args.emit_manifest:
        result["manifest"] = manifest
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError, zipfile.BadZipFile) as error:
        print(f"TaCZ 042 runtime refusal: {error}", file=sys.stderr)
        sys.exit(1)
