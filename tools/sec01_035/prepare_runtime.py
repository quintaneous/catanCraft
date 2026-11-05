#!/usr/bin/env python3
"""Prepare/verify only the pinned published SEC-01 test runtime under this repo's build/.

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
GROUP = "audit.catancraft.sec01_035"
MARKER = b"CatanCraft SEC01 0.1.35 pinned published runtime only\n"
SCHEMA = "catancraft-sec01-published-runtime-v1"
USER_AGENT = "quintaneous/catanCraft SEC01 reproducible audit"
SBW = "superbwarfare-0.8.9.2-mc1.20.1-65d985a64-all.jar"
SOURCES = "superbwarfare-0.8.9.2-mc1.20.1-65d985a64-sources.jar"
KFF = "kotlinforforge-4.11.0-all.jar"
# name: (Modrinth project, version id, version label, byte count, SHA-256)
INPUTS = {
    SBW: ("Cd3DYqzn", "81Iyv9ao", "0.8.9.2", 42118965,
          "559930003f638080de648a8621821d785158768c3f69a762094ef4c8d3c4cc12"),
    SOURCES: ("Cd3DYqzn", "81Iyv9ao", "0.8.9.2", 31115305,
              "06b4bc749e540879265e3f3829c6c242da81512b19a80de0fbd9b089847893d5"),
    KFF: ("ordsPcFz", "hmeyC41q", "4.11.0", 7193768,
          "ef988f86d170af499d147ea8a5c34c99dbd6d8f4a5ad236a4b43818dc4470a10"),
}
# SBW's exact reviewed JarJar records: (group, artifact, version, range,
# filename, native-obfuscated flag, byte count, SHA-256, local artifact id).
EMBEDDED = [
    ("top.theillusivec4.curios", "curios-forge", "5.14.1+1.20.1", "[5.14,6.0)",
     "curios-forge-5.14.1+1.20.1.jar", True, 398066,
     "6d77ae8ad532fdf303390f404b0081b3b4ac4f61e7f1f4b4d4a9077e132dae4f", "curios"),
    ("software.bernie.geckolib", "geckolib-forge-1.20.1", "4.4.6", "[4.4.6,5.0.0)",
     "geckolib-forge-1.20.1-4.4.6.jar", True, 991143,
     "0de40c205130fe07e79440f1250a22459e32618e1afdfcd44eb9626044c12f87", "geckolib"),
    ("org.ywzj", "rhino", "1.8.1-SNAPSHOT", "[1.8.0,2.0.0)",
     "rhino-1.8.1-SNAPSHOT.jar", False, 1632242,
     "4aab6124356e91e16263fa065556444b96bf5d9438e1399914cd971ef8b73616", "rhino"),
    ("com.github.mcmodderanchor", "simplebedrockmodel", "2.5.1-forge-mc1.20.1", "[2.5,3.0)",
     "simplebedrockmodel-2.5.1-forge-mc1.20.1.jar", True, 1110316,
     "323612d16de1d68a1dadc02b3329bfc0614d015ef70995bce7098adf38a437dc", "simplebedrockmodel"),
    ("net.createmod.ponder", "Ponder-Forge-1.20.1", "1.0.91", "[1.0.91,2.0.0)",
     "Ponder-Forge-1.20.1-1.0.91.jar", True, 783841,
     "86e6b64372aba6d9c56f2c35725ea26d8febf2c75eed9950566e7f2849443b34", "ponder"),
    ("dev.engine-room.flywheel", "flywheel-forge-1.20.1", "1.0.5", "[1.0.5,2.0.0)",
     "flywheel-forge-1.20.1-1.0.5.jar", True, 868892,
     "316ca250f19244956b5f0cd75329309ea65a77b4b8da854389b6a9222e7f427c", "flywheel"),
    ("me.shedaniel.cloth", "cloth-config-forge", "11.1.106", "[11.1.106,12.0.0)",
     "cloth-config-forge-11.1.106.jar", True, 1168375,
     "29cc609a80f3cf27a8e53a43473b71ff24ecb3a6eb480c610017f8a98429e867", "cloth-config"),
]
# These remain nested in KFF's unmodified FML library bundle. In particular the
# LANGPROVIDER is NOT discarded merely because the outer JAR has no mods.toml.
KFF_EMBEDDED = [
    ("kfflang", "618d5d46c69c955f94e4bcb321b5741f9f4101b608d128d2de7adf7603185713", "LANGPROVIDER"),
    ("kfflib", "43f5315234f8d81c06392b4c29778a72c274bb4636b04fc4846e9aab9c00b410", "GAMELIBRARY"),
    ("kffmod", "78bc484adb6fad9fdb7c2d44e4adefb6f53c31284f198c2017bf6840dfef8a95", None),
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
    names = {".catancraft-sec01-runtime-v1", "runtime-artifacts.json", "download-provenance.json"}
    names.update("inputs/" + name for name in INPUTS)
    pairs = [("superbwarfare", "0.8.9.2"), ("kotlinforforge", "4.11.0")]
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
    handle, temporary = tempfile.mkstemp(prefix=".sec01-writing-", dir=path.parent)
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
    add("superbwarfare", "0.8.9.2", "inputs/" + SBW, inputs[SBW], True)
    add("kotlinforforge", "4.11.0", "inputs/" + KFF, inputs[KFF], False)
    with zipfile.ZipFile(io.BytesIO(inputs[SBW])) as archive:
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
            fail("Unexpected SBW embedded runtime inventory")
    with zipfile.ZipFile(io.BytesIO(inputs[KFF])) as archive:
        expected_rows = []
        if "FMLModType: LIBRARY" not in archive.read("META-INF/MANIFEST.MF").decode():
            fail("KotlinForForge outer library declaration changed")
        for artifact, digest, mod_type in KFF_EMBEDDED:
            internal = f"META-INF/jarjar/{artifact}-4.11.0.jar"
            expected_rows.append({"identifier": {"group": "thedarkcolour", "artifact": artifact},
                                  "version": {"range": "[4.11.0,)", "artifactVersion": "4.11.0"},
                                  "path": internal, "isObfuscated": False})
            data = archive.read(internal)
            if sha256(data) != digest:
                fail(f"KotlinForForge embedded component changed: {artifact}")
            with zipfile.ZipFile(io.BytesIO(data)) as component:
                manifest = component.read("META-INF/MANIFEST.MF").decode()
                if mod_type and f"FMLModType: {mod_type}" not in manifest:
                    fail(f"KotlinForForge loading role changed: {artifact}")
                if artifact == "kfflang" and "META-INF/services/net.minecraftforge.forgespi.language.IModLanguageProvider" not in component.namelist():
                    fail("KotlinForForge language provider service missing")
        if json.loads(archive.read("META-INF/jarjar/metadata.json")) != {"jars": expected_rows}:
            fail("Unexpected KotlinForForge embedded inventory")
    records = [input_record(name, data) for name, data in inputs.items()]
    manifest = {"schema": SCHEMA, "minecraft": "1.20.1", "forge": "47.4.10",
                "inputs": records, "artifacts": artifacts,
                "kotlin_language_provider": "Unmodified KotlinForForge all-JAR nested kfflang-4.11.0.jar"}
    files[".catancraft-sec01-runtime-v1"] = MARKER
    files["runtime-artifacts.json"] = encoded(manifest)
    files["download-provenance.json"] = encoded({"schema": SCHEMA, "verified_public_modrinth_inputs": records})
    return manifest, files


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default="build/sec01-035-runtime")
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
        print(f"SEC01 runtime refusal: {error}", file=sys.stderr)
        sys.exit(1)
