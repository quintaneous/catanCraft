"""Linux developer utility for the sole qualification writer, between native processes."""
import argparse
import ctypes
import errno
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import platform
import stat
import sys
import tempfile
from contextlib import contextmanager
from uuid import UUID

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "recovery"))
from resume_plan import WORLD, phase_receipt, read, require, verify_saved_seed

MARKER = ".catancraft-authored53-native.json"
ORIGINAL = "e7ecd5d0f0ac5c1457be5c234d2e5e08c9239007787b20e0bdc45b3e2b8d862d"

def regular(path):
    st = path.lstat()
    require(stat.S_ISREG(st.st_mode) and st.st_nlink == 1, f"Expected private regular file: {path}")

def pin(path):
    regular(path)
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()

def canonical(path):
    path = path.absolute()
    require(path.resolve() == path, f"Expected canonical unlinked path: {path}")
    return path

def inventory(root):
    require(root.is_dir() and not root.is_symlink(), f"Expected directory: {root}")
    found = {}
    for entry in root.rglob("*"):
        name = entry.relative_to(root).as_posix()
        found[name] = None if stat.S_ISDIR(entry.lstat().st_mode) else pin(entry)
    return found

def publish(tree, target):
    # POSIX rename may replace an empty existing directory. Linux no-replace is atomic.
    libc = ctypes.CDLL(None, use_errno=True)
    if libc.renameat2(-100, os.fsencode(tree), -100, os.fsencode(target), 1):
        code = ctypes.get_errno()
        raise OSError(code, os.strerror(code), str(target))

class RangeLock(ctypes.Structure):
    _fields_ = [("type", ctypes.c_short), ("whence", ctypes.c_short),
                ("start", ctypes.c_longlong), ("length", ctypes.c_longlong), ("pid", ctypes.c_int)]

@contextmanager
def exclusive_world(run):
    # Java FileChannel locks conflict with OFD locks on Linux. Hashing or copying
    # session.lock through a separate descriptor does not release this OFD lock.
    lock = canonical(run / WORLD / "session.lock")
    regular(lock)
    with lock.open("r+b") as stream:
        try:
            fcntl.fcntl(stream.fileno(), getattr(fcntl, "F_OFD_SETLK", 37), bytes(RangeLock(fcntl.F_WRLCK, os.SEEK_SET, 0, 0, 0)))
        except OSError as exc:
            if exc.errno in (errno.EAGAIN, errno.EACCES):
                raise ValueError("World session lock is held") from exc
            raise
        yield

def transfer(mode, workspace, index, source, checkpoint, decode, expected=None):
    work = canonical(workspace)
    saved = canonical(checkpoint)
    require(re.fullmatch("[0-9a-f]{40}", source) and 0 <= index < 9, "Invalid commit or layout")
    receipts = canonical(work / "receipts/combined")
    stage_file = canonical(receipts / f"layout{index}-staging.json")
    receipt_files = [stage_file, *(canonical(receipts / f"layout{index}-seed" / n) for n in ("process.json", "native.log"))]
    receipt_pins = [pin(p) for p in receipt_files]
    stage = read(stage_file)
    run = canonical(Path(stage["root"]))
    require(str(run) == stage["root"] and run.parent == canonical(work / "resource-ui/build"), "Wrong disposable root")
    require(saved != run and run not in saved.parents and saved not in run.parents, "Checkpoint overlaps native run")
    require(stage["schema"] == 1 and stage["purpose"] == "verified-authored53-native-run" and
            stage["sourceCommit"] == source and stage["layout"] == index and stage["worldFolder"] == WORLD and
            stage["namespace"] == "catancraft_progression53" and stage["inputManifestSHA256"] == ORIGINAL and
            str(UUID(stage["worldId"])) == stage["worldId"], "Staging contract differs")
    verify_dir = receipts / f"layout{index}-verify"
    require(not os.path.lexists(verify_dir), "Preserve any previous verify attempt separately before proceeding")

    def check_seed(tree, files):
        world = tree / WORLD
        require(read(tree / MARKER) == stage, "Staging marker differs")
        require(not (world / f"data/progression53-layout-{index}/verify.json").exists(), "Verify has already started")
        auth_name = f"{WORLD}/catancraft-fresh-demo.json"
        auth = read(tree / auth_name)
        require(auth["worldId"] == stage["worldId"] and auth["installationBoundPath"] == str(run/WORLD) and
                files[auth_name] == stage["initialFiles"][auth_name], "World identity differs")
        for name, checksum in stage["immutableFiles"].items():
            require(files.get(name) == checksum, "Immutable staged input changed: " + name)
        inputs = read(tree/"config/catancraft/progression53-input.json")
        require(inputs["sourceCommit"] == source and inputs["layout"] == index and
                inputs["worldFolder"] == WORLD and inputs["authoredWorldSha256"] == auth["authoredWorldSha256"] and
                inputs["mapSha256"] == auth["mapSha256"], "Progression configuration differs")
        _, seed = phase_receipt(receipts, index, "seed", stage, world)
        require(auth["mapSha256"] == seed["mapSHA256"] and auth["authoredWorldSha256"] == seed["authoredWorldSHA256"],
                "Source map or authored world differs")
        verify_saved_seed(world, index, source, seed, decode)

    binding = {"schema":1,"source":source,"layout":index,"root":str(run),"worldId":stage["worldId"],"receiptPins":receipt_pins}
    if mode == "capture":
        require(not os.path.lexists(saved), "Checkpoint already exists")
        with exclusive_world(run), tempfile.TemporaryDirectory(dir=saved.parent) as temp:
            files = inventory(run)
            check_seed(run, files)
            folder = Path(temp)/"checkpoint"
            folder.mkdir()
            shutil.copytree(run, folder/"run", symlinks=True)
            require(inventory(folder/"run") == files and inventory(run) == files, "Seed changed during capture")
            require(not os.path.lexists(verify_dir), "Verifier started during capture")
            manifest = folder/"manifest.json"
            manifest.write_text(json.dumps({**binding,"files":files},sort_keys=True)+"\n")
            digest = pin(manifest)
            publish(folder,saved)
        return digest
    require(mode == "restore" and not os.path.lexists(run), "Restore requires a vacant original disposable root")
    require(set(p.name for p in saved.iterdir()) == {"run","manifest.json"}, "Foreign checkpoint entries")
    manifest = saved/"manifest.json"
    require(pin(manifest) == expected, "Checkpoint manifest checksum differs")
    meta = read(manifest)
    require(all(meta[key] == value for key,value in binding.items()), "Checkpoint binding or seed receipts changed")
    tree = saved/"run"
    files = inventory(tree)
    require(files == meta["files"], "Checkpoint contents differ")
    check_seed(tree, files)
    with tempfile.TemporaryDirectory(dir=run.parent) as temp:
        restored = Path(temp)/"new"
        shutil.copytree(tree, restored, symlinks=True)
        require(inventory(restored) == files, "Restored copy differs")
        require(not os.path.lexists(verify_dir), "Verifier started during restore")
        publish(restored,run)
    return str(run)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode",choices=["capture","restore"])
    parser.add_argument("workspace",type=Path)
    parser.add_argument("layout",type=int)
    parser.add_argument("commit")
    parser.add_argument("checkpoint",type=Path)
    parser.add_argument("--manifest-sha256")
    a=parser.parse_args()
    if sys.platform != "linux" or platform.machine() not in {"x86_64","amd64"}:
        parser.error("This developer helper targets 64-bit x86 Linux")
    work=canonical(a.workspace)
    repo=canonical(work/"resource-ui")
    git=["git","--no-optional-locks","-C",str(repo)]
    head=subprocess.check_output([*git,"rev-parse","HEAD"],text=True).strip()
    dirty=subprocess.check_output([*git,"status","--porcelain","--","src","build.gradle","tools/authored53",
                                   "tools/server53/world_profile.py"],text=True)
    if head != a.commit or (work/"receipts/combined/source.txt").read_text().strip() != a.commit or dirty:
        parser.error("The campaign needs its original clean gameplay checkout and source receipt")
    sys.path.insert(0,str(work/"inputs/CatanCraft_Clean_R7_Input"))
    from nbt_check import NBT
    try:
        print(transfer(a.mode,work,a.layout,a.commit,a.checkpoint,lambda b:NBT(b).root(),a.manifest_sha256))
    except (KeyError, TypeError, ValueError, OSError) as exc:
        parser.exit(1, f"Checkpoint refused: {exc}\n")
if __name__ == "__main__":
    main()
