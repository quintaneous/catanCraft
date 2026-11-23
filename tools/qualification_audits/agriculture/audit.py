"""Audit Agriculture flows on the 40 existing no-grant routes. Prints JSON to stdout."""
import argparse
from collections import Counter
import gzip
import hashlib
import json
from pathlib import Path
import re
import sys

sys.dont_write_bytecode = True

def audit_route(model, config, row):
    sim = model.Sim(config)
    sim.owned[row["start"]] = sim.owned.pop("a")
    recorded = row["waits"]
    actions = [(w["kind"], w["city"], w["action"]) for w in recorded] + row["next_action"]
    cursor = 0
    claim_cost = config["costs"]["claim"]
    restrictions = Counter()
    flows = Counter()
    pickup_stock = None
    minutes = config["cycle_minutes"]
    last_minute = 200 * 60 if row["next_action"] else recorded[-1]["minute"]
    for minute in range(0, last_minute + 1, minutes):
        while cursor < len(actions):
            action = actions[cursor]
            kind, city, value = action
            stock = sim.stock.get("agriculture", 0)
            if not sim.act(action):
                if kind == "claim" and stock < claim_cost["agriculture"]:
                    other_missing = any(sim.stock.get(k,0) < v for k,v in claim_cost.items() if k != "agriculture")
                    restrictions["also_missing_other_inputs" if other_missing else "agriculture_only"] += 1
                break
            assert cursor < len(recorded) and recorded[cursor]["minute"] == minute
            cursor += 1
            flows["purchase_cost"] += stock - sim.stock.get("agriculture", 0)
            if kind == "build" and row["concrete_target"] is not None:
                plant = next(b for b in sim.owned[city]["buildings"] if b["type"] == value)
                if config["props"][value]["processingTier"]:
                    plant["target"] = row["concrete_target"] if value == "CONCRETE_PLANT" else 100
            if value == "procure_pickup":
                pickup_stock = sim.stock.get("agriculture", 0)
        if minute == last_minute:
            break
        if row["managed"]:
            model.manage_targets(sim, actions[cursor])
        starting_stock = sim.stock.get("agriculture", 0)
        production = sum(sim.output(city,"agriculture") for city in sim.owned
                         if "agriculture" in config["territories"][city]["resources"])
        full_upkeep = sum(config["upkeep"][territory["producer"] - 1] for city,territory in sim.owned.items()
                          if set(config["territories"][city]["resources"]) - {"agriculture"})
        upkeep_paid = min(full_upkeep, starting_stock + production)
        sim.tick()
        processed = starting_stock + production - upkeep_paid - sim.stock.get("agriculture", 0)
        assert processed >= 0
        flows.update(produced=production, upkeep=upkeep_paid, processing=processed)
    assert cursor == row["completed"] and sim.stock == row["final_stock"] and sim.starved == row["starvation"]
    assert sim.stock["agriculture"] == flows["produced"] - flows["upkeep"] - flows["processing"] - flows["purchase_cost"]
    return {"starter":row["start"], "gateway":row["claims"][0], "managed":row["managed"],
            "pickup_first":row["pickup_first"], "concrete_target":row["concrete_target"],
            "cycles_until_stop":sim.cycle, "actions_completed":cursor, "pickup_stock":pickup_stock,
            "ending_stock":sim.stock["agriculture"], "agriculture_flows":dict(flows),
            "claim_wait_cycles_agriculture_only":restrictions["agriculture_only"],
            "claim_wait_cycles_also_missing_other_inputs":restrictions["also_missing_other_inputs"],
            "territory_cycles_with_partial_upkeep":sim.starved}

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("repo",type=Path)
    p.add_argument("starts_json", type=Path, nargs="?", default=Path(__file__).with_name("routes.json.gz"))
    args = p.parse_args()
    sys.path.insert(0, str(args.repo.resolve()/"tools/gameplay"))
    import city_d
    model=city_d.economy
    cfg=model.load()
    tariff=re.search(r'PICKUP = new Tariff\(CATALOG, "pickup", (\d+)\)', model.java("world/VehicleFuel49"))
    assert tariff and int(tariff[1]) % 4 == 0
    cfg["costs"]["refill_pickup_quarter"] = {"fuel":int(tariff[1])//4}
    raw_routes=args.starts_json.read_bytes()
    if args.starts_json.suffix == ".gz":
        raw_routes=gzip.decompress(raw_routes)
    rows=[audit_route(model,cfg,r) for r in json.loads(raw_routes)]
    assert len(rows)==40
    farms=sorted(city for city,t in cfg["territories"].items() if "agriculture" in t["resources"])
    level_rates = {}
    for level in range(1,6):
        sim = model.Sim(cfg)
        sim.owned["a"]["producer"]=level
        production=sim.output("a","agriculture")
        upkeep=cfg["upkeep"][level-1]
        level_rates[level]={"starter_gross":production,"starter_upkeep":upkeep,
                            "starter_net":production-upkeep}
    example={}
    for cities in (["a","b","f","g"],["a","b","f","g","c"]):
        sim=model.Sim(cfg)
        for city in cities:
            sim.owned[city]={"city":5,"producer":5,"buildings":[]}
        sim.tick()
        example[''.join(cities)]={"gross":level_rates[5]["starter_gross"],
                              "required_upkeep":len(cities)*cfg["upkeep"][-1],
                              "short_territories_from_zero":sim.starved}
    textile=model.Sim(cfg)
    textile.owned["a"].update(city=2, producer=5)
    textile.owned["a"]["buildings"].append({"type":"TEXTILE_MILL","level":1,"target":100})
    cycles=0
    while textile.stock.get("fabric",0)<100 and cycles<100:
        textile.tick()
        cycles+=1
    at_target=textile.stock["fabric"]
    textile.tick()
    assert textile.stock["fabric"]==at_target
    mill=cfg["props"]["TEXTILE_MILL"]
    textile_case={"cycles_to_target":cycles,"fabric_at_target":at_target,
                 "agriculture_to_first_target":cycles*mill["inputs"]["agriculture"],
                 "extra_fabric_next_cycle":textile.stock["fabric"]-at_target}
    summary={"source_sha256":model.SOURCES,"existing_routes_sha256":hashlib.sha256(raw_routes).hexdigest(),
             "L1_textile_without_logistics_default_target":textile_case,
             "agriculture_sources":farms,"single_starter_per_cycle":level_rates,
             "sample_connected_L5_nations":example,
             "prices_charging_agriculture":{k:v for k,v in cfg["costs"].items() if "agriculture" in v},
             "prices_charging_fabric":{k:v for k,v in cfg["costs"].items() if "fabric" in v},
             "processors_using_agriculture":{k:v for k,v in cfg["props"].items() if "agriculture" in v["inputs"]},
             "processors_using_fabric":[k for k,v in cfg["props"].items() if "fabric" in v["inputs"]],
             "routes":rows}
    print(json.dumps(summary,indent=2))
if __name__=="__main__":
    main()
