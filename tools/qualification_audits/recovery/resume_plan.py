#!/usr/bin/env python3
"""Read developer receipts and print a conditional recovery plan. No server or file writes."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import re
import sys
import time

sys.dont_write_bytecode = True
CITIES = "abcdefghijklmnop"
WORLD = "CatanCraft_Season1_R7_Development"
PLOTS = ("plot_1", "plot_3", "plot_4")
INDUSTRIES = ("steel_mill", "concrete_plant", "textile_mill", "refinery", "machine_shop",
              "electronics_factory", "chemical_plant", "weapons_factory", "vehicle_factory")
PASS = "NATIVE_PROCESS_PASS_REQUIRES_REVIEW"


def require(ok, message):
    if not ok:
        raise ValueError(message)


def read(path):
    return json.loads(path.read_bytes())


def sha(data):
    return hashlib.sha256(data).hexdigest()


def sample(receipts):
    result = {}
    for log in receipts.glob("layout*-*/native.log"):
        stat = log.stat()
        result[log.parent.name] = (stat.st_size, stat.st_mtime_ns, stat.st_ino)
    return result


def phase_receipt(receipts, index, phase, stage, world):
    folder = receipts / f"layout{index}-{phase}"
    record = read(folder / "process.json")
    require(record["status"] == PASS and record["source"] == stage["sourceCommit"] and
            record["exitCode"] == 0 and record["sourceUnchanged"] is True and
            record["reportedCompleted"] == [1] and record["expectedTests"] == 1 and
            record["requiredTestsPassedMessage"] is True, f"{phase} native completion is incomplete")
    cmd = record["command"]
    for token in ("runGameTestServer", "tools/authored53/validate.gradle", "tools/authored53/combined.gradle",
                  "-PgameTestNamespace=catancraft_progression53", "-PauthoredRunRoot=" + stage["root"],
                  "-PauthoredSource=" + stage["sourceCommit"], "-PauthoredPhase=" + phase):
        require(token in cmd, f"{phase} command differs at {token}")
    log = (folder / "native.log").read_bytes()
    require(sha(log) == record["nativeLogSHA256"] and
            re.findall(rb"=+ (\d+) GAME TESTS COMPLETE =+", log) == [b"1"] and
            re.findall(rb"All (\d+) required tests passed", log) == [b"1"], f"{phase} native log differs")
    row = read(world / "data" / f"progression53-layout-{index}" / (phase + ".json"))
    is_seed = phase == "seed"
    expected = {(city, tier) for city in CITIES for tier in (range(1, 6) if is_seed else (5,))}
    checkpoints = [(r["city"], r["townHallLevel"]) for r in row["checkpoints"]]
    require(row["result"] == ("PASS" if is_seed else "RESTART_PASS") and
            row["schema"] == 1 and row["purpose"] == "verified-clean-r7-progression53" and
            row["phase"] == phase and row["source"] == stage["sourceCommit"] and
            row["worldId"] == stage["worldId"] and row["layout"] == index and
            len(checkpoints) == len(expected) and set(checkpoints) == expected,
            f"{phase} report identity, result or checkpoint coverage differs")
    require(row["completedHistorySHA256"], "Missing retained histories")
    if is_seed:
        sites = {f"{city}:{plot}:{INDUSTRIES[(3*c+s+index)%9]}" for c, city in enumerate(CITIES)
                 for s, plot in enumerate(PLOTS)}
        require(row["factoryPurchasedLevels"] == {site:[1,2,3,4,5] for site in sites},
                "Seed factory coverage differs")
    return record, row


def verify_saved_seed(world, index, source, seed, decode):
    data = world / "data"
    sealed = (data / f"progression53-layout-{index}" / "restart.dat").read_bytes()
    payload = sealed[40:]
    require(sealed[:4] == b"CPR1" and 0 < len(payload) <= 8*1024*1024 and
            len(payload) == int.from_bytes(sealed[4:8], "big") and
            hashlib.sha256(payload).digest() == sealed[8:40], "Restart proof seal differs")
    proof = decode(gzip.decompress(payload))
    require(proof["schema"] == 1 and proof["source"] == source and proof["layout"] == index and
            set(proof["books"]) == set(CITIES) and proof["histories"] == seed["completedHistorySHA256"],
            "Restart proof identity, books or history differs")
    require(proof["ledger"] == decode(gzip.decompress((data/"catancraft.dat").read_bytes()))["data"],
            "Saved ledger differs from completed seed proof")
    for name, expected in proof["histories"].items():
        require(Path(name).name == name and not (data/name).is_symlink() and
                sha((data/name).read_bytes()) == expected, f"Changed paid history: {name}")


def plan_layout(receipts, build, source, i, stopped, changing, decode):
    def answer(action, detail):
        return {"layout": i, "action": action, "detail": detail}
    phases = [receipts / f"layout{i}-{phase}" for phase in ("seed", "verify")]
    if any(p.name in changing for p in phases):
        return answer("wait", "Native log changed during observation.")
    def interrupted(why):
        action = "preserve_then_restage" if stopped else "confirm_writer_stopped"
        return answer(action, why)
    stage_path = receipts / f"layout{i}-staging.json"
    if not stage_path.exists():
        if any(p.exists() for p in phases) or list(build.glob(f"*-layout-{i}")):
            return interrupted("An attempt exists without its matching staging receipt.")
        return answer("fresh_stage", "No existing attempt recorded for this layout.")
    try:
        stage = read(stage_path)
        run = Path(stage["root"])
        require(stage["sourceCommit"] == source and stage["layout"] == i and
                stage["namespace"] == "catancraft_progression53" and stage["worldFolder"] == WORLD and
                run.is_absolute() and run.resolve().parent == build.resolve() and
                run.resolve() == run and stage == read(run/".catancraft-authored53-native.json"),
                "Staging record differs from the expected campaign or world")
        world = run/WORLD
        seed_proc, seed = phase_receipt(receipts,i,"seed",stage,world)
        proof_folder = world/"data"/f"progression53-layout-{i}"
        if phases[1].exists() or (proof_folder/"verify.json").exists():
            verify_proc, restart = phase_receipt(receipts,i,"verify",stage,world)
            require(seed_proc["endedUnix"] <= verify_proc["startedUnix"] and
                    seed["completedHistorySHA256"] == restart["completedHistorySHA256"] and
                    all(seed[k] == restart[k] for k in ("mapSHA256","authoredWorldSHA256")),
                    "Restart timing, source map or paid histories differ")
            return answer("retain_pair", "Matched native exits, original log hashes, world identity and 80+16 checkpoints.")
        saved_input = read(run/"config/catancraft/progression53-input.json")
        require(saved_input["mapSha256"] == seed["mapSHA256"] and
                saved_input["authoredWorldSha256"] == seed["authoredWorldSHA256"],
                "Seed report differs from staged input")
        for name, expected in stage["immutableFiles"].items():
            require((run/name).resolve().is_relative_to(run) and sha((run/name).read_bytes()) == expected,
                    f"Changed staged immutable file: {name}")
        verify_saved_seed(world,i,source,seed,decode)
        return answer("verify_seed", "Clean seed exit, matching ledger and paid histories. No verify attempt recorded.")
    except (OSError, ValueError, TypeError, KeyError, EOFError) as error:
        return interrupted(str(error))


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--workspace",type=Path,default=Path(__file__).resolve().parents[3])
    p.add_argument("--writers-stopped",action="store_true",
                   help="Operator confirmed the queue and server stopped; a stale receipt is insufficient.")
    p.add_argument("--sample-seconds",type=float,default=1)
    args=p.parse_args()
    require(0 <= args.sample_seconds <= 60, "Choose an observation interval of 0–60 seconds")
    work=args.workspace.resolve()
    receipts=work/"receipts/combined"
    source=(receipts/"source.txt").read_text().strip()
    require(re.fullmatch("[0-9a-f]{40}",source),"Full gameplay commit required")
    sys.path.insert(0,str(work/"inputs/CatanCraft_Clean_R7_Input"))
    from nbt_check import NBT
    before=sample(receipts)
    time.sleep(args.sample_seconds)
    after=sample(receipts)
    changing={name for name in before.keys() | after.keys() if before.get(name)!=after.get(name)}
    stopped=args.writers_stopped and not changing
    layouts=[plan_layout(receipts,work/"resource-ui/build",source,i,stopped,changing,
                         lambda raw:NBT(raw).root()) for i in range(9)]
    print(json.dumps({"source":source,"changingLogs":sorted(changing),"exclusiveWriterConfirmedStopped":stopped,
                      "actionsAreConditionalOnExclusiveWriter":True, "layouts":layouts},indent=2))


if __name__=="__main__":
    main()
