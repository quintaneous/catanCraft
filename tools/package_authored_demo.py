#!/usr/bin/env python3
"""Assemble explicitly transferred, pinned authored-world inputs in a NEW folder.

Reuses prepare_fresh_demo's bounded ZIP/JAR/file validation. No downloads, live-world
copying, Java/Forge installation, server execution, deployment or automatic purchases.
"""
from __future__ import annotations
import argparse
from io import BytesIO
import gzip
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import struct
import sys
import zipfile

# The existing component stager remains the common input-validation implementation.
sys.path.insert(0, str(Path(__file__).resolve().parent))
from prepare_fresh_demo import InputError, checked_path, inspect_jar, inspect_zip, read_regular, sha256

MAX_WORLD = 256 * 1024 * 1024
FORGE = "1.20.1-47.4.10"
R7_MODE = "verified-clean-r7-with-disposable-opt-in"
R7_ROOT = "CatanCraft_Season1_R7_Development"
R7_NAME = "CatanCraft Environment V4 - Visual Polish R2"
R7_MARKER = "CATANCRAFT_R7_AUTOBUILD_DISPOSABLE_TEST_ONLY.txt"
R7_OPT_IN = b"I_ACCEPT_AUTOMATIC_R5_CIVIC_CONSTRUCTION_ON_THIS_BACKED_UP_DISPOSABLE_R7_WORLD_ONLY\n"
CITY_A_APPROVAL = {
    "city": "a", "sectors": ["north", "east", "south", "west"], "structuralTiers": [1, 2],
    "ownerComment": "https://github.com/quintaneous/catanCraft/issues/10#issuecomment-6256056792",
    "p3ArchiveSHA256": "96278c6ebfd1aef8454ebff9e51c0c56a48a273349d87928bf84e73ced039095",
}
R7_NATIVE_PASS = {"namespace": "catancraft_r7_production", "executed": 1, "passed": 1, "failed": 0}



def relative(text: str) -> str:
    if not isinstance(text, str) or not text or "\\" in text or ":" in text:
        raise InputError("Invalid relative manifest path")
    p = PurePosixPath(text)
    if p.is_absolute() or any(part in {"", ".", ".."} for part in text.split("/")):
        raise InputError(f"Unsafe relative manifest path: {text}")
    if any(part.rstrip(" .") != part or part.split(".")[0].upper() in {"CON", "PRN", "AUX", "NUL", *[f"COM{i}" for i in range(1, 10)], *[f"LPT{i}" for i in range(1, 10)]} for part in p.parts):
        raise InputError("Path is unsafe on Windows")
    return p.as_posix()


def digest(text: str) -> str:
    if not isinstance(text, str) or not re.fullmatch(r"[0-9a-f]{64}", text):
        raise InputError("Explicit lowercase SHA-256 required")
    return text


def pinned(root: Path, row: dict) -> bytes:
    data = read_regular(root / relative(row["source"]))
    if sha256(data) != digest(row["sha256"]):
        raise InputError(f"Transferred input checksum mismatch: {row['source']}")
    return data


def level_data(data: bytes) -> dict:
    """Bounded standard NBT decoding, never pickle/eval or third-party world tools."""
    try:
        with gzip.GzipFile(fileobj=BytesIO(data)) as f:
            raw = f.read(16 * 1024 * 1024 + 1)
        if len(raw) > 16 * 1024 * 1024:
            raise InputError("level.dat expands beyond inspection limit")
        f = BytesIO(raw)

        def take(n):
            if n < 0 or n > len(raw):
                raise InputError("Invalid NBT length")
            value = f.read(n)
            if len(value) != n:
                raise InputError("Truncated NBT")
            return value

        def number(fmt):
            return struct.unpack(fmt, take(struct.calcsize(fmt)))[0]

        def string():
            return take(number(">H")).decode("utf-8")

        def payload(tag, depth=0):
            if depth > 64:
                raise InputError("NBT nesting limit exceeded")
            formats = {1: ">b", 2: ">h", 3: ">i", 4: ">q", 5: ">f", 6: ">d"}
            if tag in formats:
                return number(formats[tag])
            if tag == 8:
                return string()
            if tag in {7, 11, 12}:
                count = number(">i")
                if count < 0:
                    raise InputError("Negative NBT array")
                return take(count * {7: 1, 11: 4, 12: 8}[tag])
            if tag == 9:
                kind, count = number(">B"), number(">i")
                if count < 0 or count > 1000000 or (kind == 0 and count):
                    raise InputError("Invalid NBT list")
                return [payload(kind, depth + 1) for _ in range(count)]
            if tag == 10:
                out = {}
                while True:
                    kind = number(">B")
                    if kind == 0:
                        return out
                    name = string()
                    if name in out:
                        raise InputError("Duplicate NBT compound key")
                    out[name] = payload(kind, depth + 1)
            raise InputError("Unknown NBT tag")

        if number(">B") != 10:
            raise InputError("level.dat root is not a compound")
        string()
        result = payload(10)
        if f.read(1):
            raise InputError("Trailing NBT bytes")
        return result["Data"]
    except (OSError, EOFError, KeyError, UnicodeError, struct.error) as ex:
        raise InputError(f"Invalid level.dat: {ex}") from ex


def inspect_world(data: bytes, world: dict, bootstrap: dict) -> dict[str, bytes]:
    files = world["files"]
    if not isinstance(files, dict) or not files:
        raise InputError("Exact transferred world-member hashes required")
    root = relative(world["root"])
    if "/" in root or not re.fullmatch(r"[A-Za-z0-9_-]+", root) or root.lower() in {"mods", "config", "tools", "libraries", "evidence"}:
        raise InputError("World folder must be one safe unambiguous name")
    pins = {relative(name): digest(pin) for name, pin in files.items()}
    if len({name.casefold() for name in pins}) != len(pins):
        raise InputError("Case-colliding world paths")
    metadata = {relative(name) for name in bootstrap["metadataFiles"]}
    if (not metadata and bootstrap["mode"] != R7_MODE) or not metadata.issubset(pins):
        raise InputError("Reviewed bootstrap/authorization metadata must be transferred and pinned")
    extracted = {}
    with inspect_zip(data, "transferred fresh authored world") as archive:
        for member in archive.infolist():
            if member.is_dir():
                continue
            name = relative(member.filename)
            prefix = root + "/"
            if not name.startswith(prefix):
                raise InputError("World ZIP has an unexpected root")
            rel = relative(name[len(prefix):])
            # No inherited purchases, journals, players, saved inventories or entity stores.
            # Explicit bootstrap metadata cannot authorize any of these excluded locations.
            parts = PurePosixPath(rel).parts
            if rel == "catancraft-fresh-demo.json" or "TEST_ONLY" in rel:
                raise InputError("Transferred authorization/test markers are not accepted; fresh binding is generated from verified inputs")
            if any(part.lower() in {"data", "playerdata", "players", "advancements", "stats", "entities"} for part in parts):
                raise InputError(f"World contains inherited ledger/player/entity data: {rel}")
            allowed = rel == "level.dat" or re.fullmatch(r"(?:region|poi)/r\.-?\d+\.-?\d+\.mca", rel) or rel in metadata or rel.startswith("datapacks/")
            if not allowed or rel not in pins:
                raise InputError(f"Unapproved world file: {rel}")
            content = archive.read(member)
            if sha256(content) != pins[rel]:
                raise InputError(f"World member checksum mismatch: {rel}")
            extracted[rel] = content
    if set(extracted) != set(pins):
        raise InputError("Transferred world member list differs from approved manifest")
    if "level.dat" not in extracted:
        raise InputError("Authored world level.dat missing")
    regions = [name for name in extracted if name.startswith("region/")]
    if not regions or len(regions) != world["regionFiles"]:
        raise InputError("Authored region count does not match manifest")
    level = level_data(extracted["level.dat"])
    if "Player" in level or level.get("LevelName") != world["levelName"] or level.get("WorldGenSettings", {}).get("seed") != world["seed"]:
        raise InputError("World level identity/seed is wrong or contains an inherited player")
    if bootstrap["mode"] == R7_MODE:
        expected = {"level.dat"} | {f"region/r.{x}.{z}.mca" for x in range(-2, 2) for z in range(-2, 2)}
        if metadata or set(extracted) != expected or root != R7_ROOT or level.get("LevelName") != R7_NAME or level.get("DataVersion") != 3465:
            raise InputError("Clean R7 requires its exact identity, version and seventeen source files")
    return {root + "/" + name: content for name, content in extracted.items()}


LAUNCHER = r'''#!/usr/bin/env python3
"""Local-only existing package verifier/launcher; never downloads or resets a world."""
import hashlib,json,os,pathlib,re,shutil,subprocess,sys
ROOT=pathlib.Path(__file__).resolve().parents[1]
def fail(text): raise RuntimeError(text)
def safe(name):
    p=ROOT/name
    if pathlib.PurePosixPath(name).is_absolute() or '..' in pathlib.PurePosixPath(name).parts: fail('Unsafe package path')
    for q in [p,*p.parents]:
        if q==ROOT.parent: break
        if q.is_symlink(): fail('Package symlinks are forbidden')
    return p
def check(name,pin):
    p=safe(name)
    if not p.is_file() or hashlib.sha256(p.read_bytes()).hexdigest()!=pin: fail('Missing/changed required file: '+name)
def server_properties(expected):
    # Properties.store writes one key=value per line, escaping values. Accept that
    # normal rewrite, not alternate syntaxes that could hide a second owned key.
    found={}
    def unescape(value):
        def decode(match):
            token=match.group(1)
            if token.startswith('u'): return chr(int(token[1:],16))
            return {'n':'\n','r':'\r','t':'\t','f':'\f'}.get(token,token)
        return re.sub(r'\\(u[0-9a-fA-F]{4}|.)',decode,value)
    text=safe('server.properties').read_text(encoding='utf-8')
    if any((ord(c)<32 and c not in '\r\n') or c in '\x7f\x85\u2028\u2029' for c in text): fail('Unsupported server property separator/control')
    for line in re.split(r'\r\n|\r|\n',text):
        if not line.strip() or line.lstrip().startswith(('#','!')): continue
        key,separator,value=line.partition('=')
        if not separator or not re.fullmatch(r'[A-Za-z0-9_.-]+',key) or key in found: fail('Unsupported/duplicate server property')
        if (len(value)-len(value.rstrip('\\'))) & 1: fail('Continued server property refused')
        found[key]=unescape(value)
    for key,value in expected.items():
        if found.get(key)!=value: fail('Changed required server property: '+key)
def main():
    if sys.version_info<(3,11): fail('Python 3.11+ required; no tools are installed automatically')
    os.chdir(ROOT)
    raw=safe('PACKAGE.json').read_bytes()
    if hashlib.sha256(raw).hexdigest()!=safe('PACKAGE.sha256').read_text().split()[0]: fail('Package manifest checksum mismatch')
    policy=json.loads(raw)
    for name,pin in policy['immutableFiles'].items(): check(name,pin)
    server_properties(policy['serverProperties'])
    if not re.search(r'(?m)^\s*eula\s*=\s*true\s*$',safe('eula.txt').read_text()): fail('Read Minecraft EULA and personally set eula=true before launch')
    # Runtime files must come from an independently verified official Forge installation.
    for name,pin in policy['forgeRuntimeFiles'].items(): check(name,pin)
    java=shutil.which('java')
    if not java: fail('Install 64-bit Java 17 and official Forge 1.20.1-47.4.10 first; see README_DEMO.txt')
    result=subprocess.run([java,'-XshowSettings:properties','-version'],capture_output=True,text=True)
    details=result.stdout+result.stderr
    if result.returncode or not re.search(r'java\.version\s*=\s*17(?:\.|\s)',details) or not re.search(r'sun\.arch\.data\.model\s*=\s*64\b',details): fail('Exactly 64-bit Java 17 required')
    marker=safe('.demo-launch-state.json')
    authorization=safe(policy['worldRoot']+'/catancraft-fresh-demo.json')
    identity={'root':str(ROOT),'inputManifestSHA256':policy['inputManifestSHA256']}
    if marker.exists():
        if json.loads(marker.read_text())!=identity: fail('Played demo moved or authorization changed; preserve it for review')
        auth=json.loads(authorization.read_text())
        expected=dict(policy['authorization']); expected['installationBoundPath']=str(safe(policy['worldRoot']).resolve())
        if auth!=expected: fail('Fresh demo authorization binding changed; preserve the world')
    else:
        check('server.properties',policy['initialServerPropertiesSHA256'])
        world=safe(policy['worldRoot'])
        actual=set()
        for p in world.rglob('*'):
            if p.is_symlink(): fail('World symlink forbidden')
            if p.is_file(): actual.add(p.relative_to(ROOT).as_posix())
        if actual!=set(policy['initialWorldFiles']): fail('First launch requires the exact transferred clean world; never delete journals to force startup')
        for name,pin in policy['initialWorldFiles'].items(): check(name,pin)
    args='win_args.txt' if os.name=='nt' else 'unix_args.txt'
    argfile='libraries/net/minecraftforge/forge/1.20.1-47.4.10/'+args
    if argfile not in policy['forgeRuntimeFiles']: fail('Required pinned Forge platform argument file missing')
    if '--verify-only' in sys.argv: print('Package and local Java/Forge prerequisites verified; server not started'); return 0
    if len(sys.argv)!=1: fail('Only --verify-only is accepted; no arbitrary launch arguments')
    if not marker.exists():
        auth=dict(policy['authorization']);auth['installationBoundPath']=str(safe(policy['worldRoot']).resolve())
        authorization.write_text(json.dumps(auth,sort_keys=True,indent=2)+'\n')
        with marker.open('x') as out: json.dump(identity,out,sort_keys=True)
    return subprocess.call([java,'-Xms1G','-Xmx4G','@'+argfile,'nogui'],cwd=ROOT)
if __name__=='__main__':
    try: sys.exit(main())
    except (RuntimeError,OSError,ValueError,KeyError) as ex: print('REFUSED: '+str(ex),file=sys.stderr); sys.exit(2)
'''


def assemble(input_root: Path, manifest_path: Path, manifest_sha256: str, output: Path) -> dict:
    root = checked_path(input_root, directory=True)
    manifest_bytes = read_regular(manifest_path)
    if sha256(manifest_bytes) != digest(manifest_sha256):
        raise InputError("Transferred input manifest checksum mismatch")
    try:
        m = json.loads(manifest_bytes)
        if m["schemaVersion"] != 1 or m["packageKind"] != "catancraft-authored-demo-input":
            raise InputError("Unknown authored-world manifest schema")
        enabled = m["approval"].get("sectorArtEnabled")
        if type(enabled) is not bool or m["approval"] != {"freshAuthoredWorld": True, "bootstrapReviewed": True, "sectorArtEnabled": enabled}:
            raise InputError("Explicit fresh-world/bootstrap and sector approval required")
        if enabled and (m["bootstrap"]["mode"] != R7_MODE or m.get("sectorApproval") != CITY_A_APPROVAL):
            raise InputError("Enabled sectors require the exact approved City A P2/P3 clean-R7 scope")
        if not re.fullmatch(r"[0-9a-f]{40}", m["sourceCommit"]):
            raise InputError("Exact source commit required")
        if m["runtime"]["javaMajor"] != 17 or m["runtime"]["minecraft"] != "1.20.1" or m["runtime"]["forge"] != "47.4.10":
            raise InputError("Runtime prerequisites differ from prepared environment")
        if m["bootstrap"]["mode"] not in {"reviewed-transferred-bootstrap", R7_MODE}:
            raise InputError("No approved deterministic fresh-bootstrap contract supplied")
        world = pinned(root, m["world"])
        components = inspect_world(world, m["world"], m["bootstrap"])
        if m["bootstrap"]["mode"] == R7_MODE:
            opt_in = pinned(root, m["bootstrap"]["r7OptIn"])
            if opt_in != R7_OPT_IN:
                raise InputError("R7 disposable opt-in does not match the current exact contract")
            components[m["world"]["root"] + "/" + R7_MARKER] = opt_in
        auth_input = m["bootstrap"]["authorization"]
        import uuid
        world_id = str(uuid.UUID(auth_input["worldId"]))
        if world_id != auth_input["worldId"] or not isinstance(m["world"]["seed"], int) or isinstance(m["world"]["seed"], bool):
            raise InputError("Invalid fresh world UUID/seed")
        if not isinstance(auth_input["bootstrapRevision"], str) or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._:-]{0,127}", auth_input["bootstrapRevision"]):
            raise InputError("Pinned deterministic bootstrap revision required")
        authorization = {"schema": 1, "purpose": "catancraft-fresh-authored-demo", "worldId": world_id,
                         "levelName": m["world"]["levelName"], "seed": m["world"]["seed"], "dimension": "minecraft:overworld",
                         "mapSha256": digest(auth_input["mapSha256"]), "authoredWorldSha256": m["world"]["sha256"],
                         "bootstrapRevision": auth_input["bootstrapRevision"]}
        components[m["world"]["root"] + "/catancraft-fresh-demo.json"] = (json.dumps(authorization, sort_keys=True, indent=2) + "\n").encode()
        jar = pinned(root, m["jar"])
        metadata = inspect_jar(jar)
        if metadata["version"] != m["jar"]["version"]:
            raise InputError("JAR version differs from approved input manifest")
        components["mods/catancraft.jar"] = jar
        evidence = pinned(root, m["sourceEvidence"])
        report = json.loads(evidence)
        if report["sourceCommit"] != m["sourceCommit"] or report["jarSHA256"] != sha256(jar) or report["kind"] != "matching-runtime-evidence":
            raise InputError("Source/runtime evidence does not match packaged JAR")
        if enabled and report.get("sectorValidation") != R7_NATIVE_PASS:
            raise InputError("Enabled sectors require matching-source completed raw-R7 native evidence")
        components["evidence/SOURCE_RUNTIME.json"] = evidence
        for group in ("config", "assets"):
            if not m[group]:
                raise InputError(f"Matching reviewed {group} inputs required")
            for row in m[group]:
                target = relative(row["target"])
                if not target.startswith("config/") or target in components:
                    raise InputError("Config/assets must have unique config/ destinations")
                components[target] = pinned(root, row)
        if "config/catancraft/map.json" not in components or sha256(components["config/catancraft/map.json"]) != authorization["mapSha256"]:
            raise InputError("Fresh authorization must pin actual packaged config/catancraft/map.json bytes")
        runtime_files = {relative(name): digest(pin) for name, pin in m["forgeRuntimeFiles"].items()}
        required_args = {f"libraries/net/minecraftforge/forge/{FORGE}/{platform}_args.txt" for platform in ("unix", "win")}
        if not required_args.issubset(runtime_files) or not any(name.endswith(".jar") and FORGE in name for name in runtime_files):
            raise InputError("Pinned official Forge platform args and runtime JAR required")
        if any(not name.startswith("libraries/") for name in runtime_files):
            raise InputError("Forge runtime prerequisites must stay inside package libraries/")
    except (KeyError, TypeError, ValueError, AttributeError) as ex:
        if isinstance(ex, InputError):
            raise
        raise InputError(f"Incomplete/invalid transferred package manifest: {ex}") from ex
    destination = Path(os.path.abspath(output))
    checked_path(destination.parent, directory=True)
    if destination.exists() or destination.is_symlink() or destination == root or root in destination.parents:
        raise InputError("Use an isolated NEW destination outside transferred inputs; existing work is preserved")
    components["tools/launch_demo.py"] = LAUNCHER.encode()
    components["START_DEMO.sh"] = b'#!/bin/sh\nset -eu\ncd -- "$(dirname -- "$0")"\nexec python3 tools/launch_demo.py "$@"\n'
    components["START_DEMO.bat"] = b'@echo off\r\ncd /d "%~dp0"\r\npy -3 tools\\launch_demo.py %*\r\nif errorlevel 1 pause\r\n'
    components["eula.txt"] = b'# Read https://aka.ms/MinecraftEULA. Set true yourself only if you agree.\neula=false\n'
    world_root = m["world"]["root"]
    components["server.properties"] = (f"server-ip=127.0.0.1\nserver-port=25566\nonline-mode=true\nwhite-list=true\nenforce-whitelist=true\nenable-rcon=false\nenable-query=false\nlevel-name={world_root}\n").encode()
    if m["bootstrap"]["mode"] == R7_MODE:
        components["server.properties"] += b"gamemode=creative\nforce-gamemode=false\ndifficulty=peaceful\nhardcore=false\npvp=false\nallow-flight=true\nspawn-protection=0\nmax-players=1\nview-distance=6\nsimulation-distance=6\nsync-chunk-writes=true\n"
    components["evidence/TRANSFER_INPUT_MANIFEST.json"] = manifest_bytes
    components["README_DEMO.txt"] = (
        "CATANCRAFT AUTHORED-WORLD DEMO — ISOLATED PACKAGE, NOT A FULL MODPACK\n\n"
        "Start scripts are included. Never extract over another instance, copy a live save, delete journals, or reset a played demo.\n"
        "Prerequisites: Python 3.11+, 64-bit Java 17, official Minecraft 1.20.1 / Forge 47.4.10 server libraries.\n"
        "Obtain official Forge from https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html ; verify the supplied runtime-file hashes before use.\n"
        "Install Forge manually in THIS isolated folder. No launcher downloads or executes an installer. Required library hashes are in PACKAGE.json.\n"
        "Read the Minecraft EULA and personally set eula=true in this folder. Run START_DEMO.sh (Linux) or START_DEMO.bat (Windows).\n"
        "Use --verify-only to check local prerequisites without starting Minecraft. Stop cleanly with console command stop. Do not move a played demo.\n"
        "Client: separate Minecraft 1.20.1 / Forge 47.4.10 profile with the exact mods/catancraft.jar. TaCZ/SBW/dependency packs are NOT bundled or certified.\n"
        "No network hosting or multiplayer validation is included. Server listens only on localhost:25566 with authenticated whitelist enabled.\n"
        "Funding shortcut, after normal founding: console op YOUR_NAME; then in-game /catan debug money 100000 and /catan debug give stone 1000 (repeat explicitly for needed resources).\n"
        "These existing operator commands only fund the nation; they do not buy buildings, city tiers or defenses. No auto-buy function is installed.\n"
        "Artwork and structural prices are approved for isolated City A testing; runtime technical gates remain independent. Package assembly is not gameplay, launcher-platform, bootstrap or owner acceptance evidence.\n"
    ).encode()
    initial = {name: sha256(data) for name, data in components.items() if name.startswith(world_root + "/")}
    # Minecraft rewrites this file with defaults/comments at startup. Its owned
    # settings stay pinned semantically after the first launch, not byte-for-byte.
    server_properties = dict(line.split("=", 1) for line in components["server.properties"].decode().splitlines())
    mutable = {"eula.txt", "server.properties"}
    package = {"schemaVersion": 1, "kind": "isolated-authored-demo-package", "status": "ASSEMBLED_REQUIRES_LOCAL_PREREQUISITES_AND_ACCEPTANCE", "sourceCommit": m["sourceCommit"], "inputManifestSHA256": manifest_sha256,
               "fixtureOnly": m.get("fixtureOnly", False),
               "worldRoot": world_root, "initialWorldFiles": initial, "forgeRuntimeFiles": runtime_files,
               "authorization": authorization,
               "serverProperties": server_properties,
               "initialServerPropertiesSHA256": sha256(components["server.properties"]),
               "immutableFiles": {name: sha256(data) for name, data in components.items() if name not in mutable and name not in initial},
               "runtimeBundled": False, "fullModpack": False, "sectorArtEnabled": enabled}
    if enabled:
        package["sectorApproval"] = CITY_A_APPROVAL
    raw = (json.dumps(package, sort_keys=True, indent=2) + "\n").encode()
    components["PACKAGE.json"] = raw
    components["PACKAGE.sha256"] = f"{sha256(raw)}  PACKAGE.json\n".encode()
    if len({name.casefold() for name in components}) != len(components):
        raise InputError("Case-colliding package output paths")
    destination.mkdir()
    try:
        for name, content in sorted(components.items()):
            path = destination / relative(name)
            path.parent.mkdir(parents=True, exist_ok=True)
            with path.open("xb") as stream:
                stream.write(content)
            if sha256(path.read_bytes()) != sha256(content):
                raise InputError("Assembled output readback mismatch")
            if name in {"START_DEMO.sh", "tools/launch_demo.py"}:
                path.chmod(0o755)
    except BaseException:
        shutil.rmtree(destination)
        raise
    return package


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input-root", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--manifest-sha256", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = assemble(args.input_root, args.manifest, args.manifest_sha256, args.output)
    except (InputError, OSError) as ex:
        print(f"REFUSED: {ex}", file=sys.stderr)
        return 2
    print(f"ASSEMBLED: {len(result['initialWorldFiles'])} transferred world files; runtime prerequisites and gameplay acceptance still required")
    return 0


if __name__ == "__main__":
    sys.exit(main())
