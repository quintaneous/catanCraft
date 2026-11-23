"""Read-only snapshot of completed fresh-world runs. Run with Python 3 from any directory."""
import argparse
import base64
import collections
import csv
import gzip
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import subprocess
import zipfile

sys.dont_write_bytecode = True
PROPERTIES = {"plot_1": (-45,-24), "plot_3": (-45,16), "plot_4": (45,16)}
COUNTERS = {"weapons_factory": (19,3,18), "vehicle_factory": (18,3,9)}
BLOCK = "minecraft:crafting_table"

def load(name, p):
    spec = importlib.util.spec_from_file_location(name,p)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

def read(p):
    return json.loads(p.read_bytes())

def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()

def tables(blob, nbt):
    obj = nbt.NBT(gzip.decompress(blob)).root()
    palette = {v:k for k,v in obj["Palette"].items()}
    coords = []
    index = value = shift = 0
    for token in obj["BlockData"]:
        value |= (token & 127) << shift
        if token & 128:
            shift += 7
            continue
        if palette[value] == BLOCK:
            x = index % obj["Width"]
            yz = index // obj["Width"]
            coords.append((x,yz//obj["Length"],yz%obj["Length"]))
        index += 1
        value = shift = 0
    assert shift == 0 and index == obj["Length"]*obj["Width"]*obj["Height"]
    return coords

def physical(city, plot, local):
    center = city["cityCenter"]
    assert center["rotation"] in (0,180)
    sign = 1 if center["rotation"] == 0 else -1
    ox,oz = PROPERTIES[plot]
    x,y,z = local
    direction = -sign if plot=="plot_4" else sign
    return (center["x"]+sign*ox+direction*(x-29),81+y-2,center["z"]+sign*oz+direction*(z-15))

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workspace", required=True, type=Path,
                        help="Test workspace containing receipts/combined, inputs and the resource-ui checkout")
    parser.add_argument("--repo", type=Path, help="Candidate checkout including its completed build worlds")
    parser.add_argument("--output-dir", type=Path, default=Path.cwd())
    args=parser.parse_args()
    base=args.workspace.resolve()
    combined=base/"receipts/combined"
    repo=(args.repo or base/"resource-ui").resolve()
    out=args.output_dir.resolve()
    out.mkdir(parents=True,exist_ok=True)
    source=(combined/"source.txt").read_text().strip()
    assert source=="6ed6eaf31f946195f8b75fa869308c915537a6ed"
    audit = load("walls_audit", repo/"tools/walls53/audit_sites.py")
    nbt = load("nbt",base/"inputs/CatanCraft_Clean_R7_Input/nbt_check.py")
    assert subprocess.check_output(["git","-C",str(repo),"rev-parse","HEAD"],text=True).strip()==source
    assert not subprocess.check_output(["git","-C",str(repo),"status","--porcelain","--","src","tools/walls53/audit_sites.py"])
    resources = repo/"src/main/resources/data/catancraft"
    assert sha(resources/"maps/season1_river_bridges_v3.json")=="e50d9753c807b5fe6f8fdee9765ae09640d951cc0ba2d59f1612f0923485c986"
    cities = {c["id"]:c for c in read(resources/"maps/season1_river_bridges_v3.json")["territories"]}
    assets = zipfile.ZipFile(io.BytesIO(base64.b64decode((resources/"basic_construction/assets.b64").read_bytes())))
    found_tables = {kind:tables(assets.read(kind+".schem"),nbt) for kind in COUNTERS}
    positions = {}
    for name,c in sorted(cities.items()):
        for plot in PROPERTIES:
            assert plot not in c["reservedPlotIds"]
            for kind,local in COUNTERS.items():
                assert local in found_tables[kind]
                key = ":".join((name,plot,kind))
                positions[key] = physical(c,plot,local)
    assert len(positions) == len(set(positions.values())) == 96
    assert len({tuple(c["managementAnchor"][d] for d in ("x","y","z")) for c in cities.values()} | set(positions.values())) == 112

    completed, observation, pin = [],[],{}
    for i in range(9):
        process = combined/f"layout{i}-verify/process.json"
        if not process.exists() or read(process)["status"]!="NATIVE_PROCESS_PASS_REQUIRES_REVIEW": continue
        staging = read(combined/f"layout{i}-staging.json")
        root = repo/"build"/Path(staging["root"]).name/staging["worldFolder"]
        reports = root/"data"/f"progression53-layout-{i}"
        for phase, expected in [("seed","PASS"),("verify","RESTART_PASS")]:
            folder = combined/f"layout{i}-{phase}"
            process_record=read(folder/"process.json")
            assert process_record["source"]==source and process_record["exitCode"]==0
            assert process_record["reportedCompleted"]==[1] and process_record["sourceUnchanged"]
            assert process_record["status"]=="NATIVE_PROCESS_PASS_REQUIRES_REVIEW"
            assert process_record["nativeLogSHA256"]==sha(folder/"native.log")
            assert "tools/authored53/combined.gradle" in process_record["command"]
            report = read(reports/f"{phase}.json")
            assert report["source"]==source and report["worldId"]==staging["worldId"] and report["result"]==expected
            pin[f"{i}/{phase}"]=sha(reports/f"{phase}.json")
        seed=read(reports/"seed.json")
        world = audit.World(root,nbt.NBT)
        for checkpoint in report["checkpoints"]:
            assert checkpoint["townHallLevel"]==5
            name = checkpoint["city"]
            for facility in checkpoint["facilities"]:
                kind=facility["type"]
                if kind not in COUNTERS: continue
                plot=facility["plot"]
                key=":".join((name,plot,kind))
                at=positions[key]
                actual=world.at(at)
                assert actual==BLOCK, (i,key,at,actual)
                assert seed["factoryPurchasedLevels"][key]==[1,2,3,4,5] and facility["level"]==5
                extra = [physical(cities[name],plot,pos) for pos in found_tables[kind] if pos!=COUNTERS[kind]]
                assert all(world.at(pos)==BLOCK and pos not in positions.values() for pos in extra)
                support=world.at((at[0],at[1]-1,at[2]))
                assert support==("minecraft:spruce_planks" if kind=="weapons_factory" else "minecraft:smooth_stone")
                assert all(world.at((x,y-1,z))=="minecraft:smooth_stone" for x,y,z in extra)
                observation.append({"counterSupport":support,"otherCraftingTablesXYZ":extra,"layout":i,"site":key,"rotation":cities[name]["cityCenter"]["rotation"],"xyz":at,"savedBlock":actual})
        del world
        completed.append(i)
    assert completed, "At least one completed layout pair with saved chunks is required"
    assert len({o["site"] for o in observation})==len(observation)
    doc={"gameplayCommit":source,"completedLayouts":completed,"sourceSchematicCraftingTablesXYZ":found_tables,
         "possibleSites":positions,"checkedNativeSites":observation,"reportSHA256":pin,"clientInteractionsExecuted":0}
    with (out/"locations.csv").open("w", newline="") as handle:
        writer=csv.writer(handle)
        writer.writerow(["city","plot","factory","city rotation","shop counter XYZ","other crafting table XYZ","verified layout"])
        observed={r["site"]:r["layout"] for r in observation}
        for key,at in sorted(positions.items()):
            city,plot,kind=key.split(":")
            others=[physical(cities[city],plot,pos) for pos in found_tables[kind] if pos!=COUNTERS[kind]]
            writer.writerow([city.upper(),plot,kind,cities[city]["cityCenter"]["rotation"]," ".join(map(str,at)),
                             " / ".join(" ".join(map(str,pos)) for pos in others),observed.get(key,"")])
    (out/"snapshot.json").write_text(json.dumps(doc, indent=2)+"\n")
    print("Completed layouts",completed,"physical counters",len(observation),"by type",dict(collections.Counter(o["site"].split(":")[-1] for o in observation)),"schematics",found_tables)
if __name__=="__main__":main()
