#!/usr/bin/env python3
"""Bounded DEF-04 comparison from current Java/map definitions, not balance certification.

Only a one-City-A raw-production policy model is timed. Industrial growth,
combat success, travel and multiplayer behavior are not simulated.
"""
from __future__ import annotations
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path
import re

BASIC = {"money": 1500, "wood": 15, "stone": 30}
REINFORCED = {"money": 4000, "concrete": 50, "steel": 15}
JAVA = "src/main/java/com/quin/catancraft/"
SOURCES = [JAVA + p for p in ["economy/EconomyCatalog.java", "economy/EconomyBalance.java", "economy/EconomyEngine.java", "data/BuildingType.java", "data/NationData.java", "data/CatanSavedData.java", "command/NationProcurement.java"]] + ["src/main/resources/data/catancraft/maps/season1_river_bridges_v3.json"]


class ModelError(ValueError):
    pass


def method(source, name):
    match = re.search(r"\b" + re.escape(name) + r"\s*\([^)]*\)\s*\{", source)
    if not match:
        raise ModelError(f"Required source method unavailable: {name}")
    start = match.end()
    depth = 1
    for i in range(start, len(source)):
        depth += (source[i] == "{") - (source[i] == "}")
        if depth == 0:
            return source[start:i]
    raise ModelError("Unbalanced source method")


def integer(text):
    return int(text.replace("_", "").rstrip("Ll"))


def cost_expression(text):
    found = re.search(r"new EconomyCost\(\s*([0-9_]+)\s*,\s*Map\.of\((.*?)\)\s*\)", text, re.S)
    if not found:
        raise ModelError("Unsupported Java price expression")
    pairs = re.findall(r"ResourceType\.([A-Z_]+)\s*,\s*([0-9_]+)L", found[2])
    rest = re.sub(r"ResourceType\.[A-Z_]+\s*,\s*[0-9_]+L", "", found[2]).strip(" ,\n\r\t")
    if rest:
        raise ModelError("Unknown Java resource price syntax")
    return {"money": integer(found[1]), **{k.lower(): integer(v) for k, v in pairs}}


def switch_cost(source, name, level):
    body = method(source, name)
    found = re.search(r"case\s+" + str(level) + r"\s*->(.*?)(?=case\s+\d+\s*->|default\s*->)", body, re.S)
    if not found:
        raise ModelError("Price switch branch unavailable")
    return cost_expression(found[1])


def scalar(source, name):
    found = re.search(r"\b" + name + r"\s*=\s*([0-9_]+)L?\s*;", source)
    if not found:
        raise ModelError(f"Unknown source constant: {name}")
    return integer(found[1])


def sector_prices(repo, supplied_source=None):
    path = Path(supplied_source) if supplied_source else Path(repo) / (JAVA + "world/SectorPriceCatalog.java")
    text = path.read_text()
    version = re.search(r'\bVERSION\s*=\s*"([A-Za-z0-9_:-]+)"', text)
    if not version:
        if supplied_source:
            raise ModelError("Supplied sector catalog is not explicitly versioned")
        return {"version": None, "status": "OWNER_VALUES_ONLY_CATALOG_NOT_YET_APPLIED", "path": str(path),
                "sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "basic": BASIC.copy(), "reinforcedIncrement": REINFORCED.copy()}
    prices = {}
    for field, key, expected in (("BASIC", "basic", BASIC), ("REINFORCED_INCREMENT", "reinforcedIncrement", REINFORCED)):
        expression = re.search(r'\b' + field + r'\s*=\s*(new EconomyCost\(.*?);', text, re.S)
        if not expression:
            raise ModelError("Versioned sector price expression unavailable")
        prices[key] = cost_expression(expression[1])
        if prices[key] != expected:
            raise ModelError("Sector Java price differs from this owner-authorized iteration; review model before rerunning")
    return {"version": version[1], "status": "EXPLICIT_CANDIDATE_SOURCE_NOT_APPLIED" if supplied_source else "CHECKOUT_VERSIONED_CATALOG",
            "path": str(path), "sha256": hashlib.sha256(path.read_bytes()).hexdigest(), **prices}


def load_definitions(repo, sector_catalog=None):
    repo = Path(repo)
    texts = {name: (repo / name).read_text() for name in SOURCES}
    balance = texts[JAVA + "economy/EconomyBalance.java"]
    catalog = texts[JAVA + "economy/EconomyCatalog.java"]
    procurement = texts[JAVA + "command/NationProcurement.java"]
    creation = method(texts[JAVA + "data/CatanSavedData.java"], "createNation")
    starting = re.search(r"new NationData\(UUID.randomUUID\(\), name, leaderId, ([0-9_]+)\)", creation)
    if not starting or "stockpile.put(type, 0L)" not in texts[JAVA + "data/NationData.java"]:
        raise ModelError("Unknown initial nation resources")
    raw = {int(k): int(v) for k, v in re.findall(r"case\s+(\d+)\s*->\s*(\d+)\s*;", method(balance, "rawProductionPerCycle"))}
    raw[5] = int(re.search(r"default\s*->\s*(\d+)", method(balance, "rawProductionPerCycle"))[1])
    upkeep = {int(k): int(v) for k, v in re.findall(r"case\s+(\d+)\s*->\s*(\d+)\s*;", method(balance, "agricultureUpkeepPerCycle"))}
    upkeep[5] = int(re.search(r"default\s*->\s*(\d+)", method(balance, "agricultureUpkeepPerCycle"))[1])
    offers = {}
    for key in ("pistol", "ammo9", "ammo762"):
        offer = re.search(r'offer\("' + key + r'",(.*?)(?=\n\s*offer\(|\n\s*\})', procurement, re.S)
        if not offer:
            raise ModelError("Procurement offer unavailable")
        offers[key] = cost_expression(offer[1])
    if not re.search(r'"tacz:9mm",\s*32,', procurement) or not re.search(r'"tacz:762x39",\s*32,', procurement):
        raise ModelError("Ammo pack size changed; adapt policy explicitly")
    map_data = json.loads(texts[SOURCES[-1]])
    city = next(t for t in map_data["territories"] if t["id"] == "a")
    if set(city["resources"]) != {"wood", "stone", "agriculture"}:
        raise ModelError("Starter resources changed; model requires explicit update")
    return {"sourcePins": {name: hashlib.sha256((repo / name).read_bytes()).hexdigest() for name in SOURCES}, "sectorCatalog": sector_prices(repo, sector_catalog),
            "cycleMinutes": scalar(balance, "CYCLE_MINUTES"), "incomePerCycle": scalar(balance, "BASE_TERRITORY_INCOME_PER_CYCLE"),
            "startingMoney": integer(starting[1]), "startingStockpile": 0, "raw": raw, "upkeep": upkeep,
            "starterYields": city["resourceYieldPercent"], "offers": offers,
            "producerCosts": {level: switch_cost(catalog, "producerUpgradeCost", level) for level in range(1, 5)},
            "cityL2": switch_cost(catalog, "cityUpgradeCost", 1), "ordinaryFactoryL2": switch_cost(catalog, "buildingUpgradeCost", 1)}


def add(*costs):
    result = {}
    for price in costs:
        for resource, amount in price.items():
            result[resource] = result.get(resource, 0) + amount
    return result


def affordable(stock, price):
    return all(stock.get(resource, 0) >= amount for resource, amount in price.items())


def debit(stock, price):
    if not affordable(stock, price):
        raise ModelError("Unaffordable policy purchase")
    for resource, amount in price.items():
        stock[resource] = stock.get(resource, 0) - amount


def raw_cycle(stock, level, definitions):
    # Java Math.round(x>=0) is floor(x+.5), not Python banker's round.
    output = {r: math.floor(definitions["raw"][level] * percent / 100 + .5) for r, percent in definitions["starterYields"].items()}
    stock["agriculture"] = stock.get("agriculture", 0) + output["agriculture"]
    upkeep = definitions["upkeep"][level]
    available = stock["agriculture"]
    supply = min(1, available / upkeep) if upkeep else 1
    stock["agriculture"] -= min(available, upkeep)
    for resource in ("wood", "stone"):
        stock[resource] = stock.get(resource, 0) + math.floor(output[resource] * supply)
    stock["money"] += definitions["incomePerCycle"]


def simulate(definitions, name, goals, rounds_per_hour=0, hours=48):
    if not isinstance(rounds_per_hour, int) or rounds_per_hour < 0 or not isinstance(hours, int) or not 1 <= hours <= 240:
        raise ModelError("Use nonnegative integral ammunition demand and bounded whole hours")
    if not goals or any(goal not in {"basic", "producer2"} for goal in goals) or goals.count("producer2") > 1:
        raise ModelError("Unknown or unsupported construction policy")
    cycle_minutes = definitions["cycleMinutes"]
    rounds_per_cycle = rounds_per_hour * cycle_minutes / 60
    if rounds_per_cycle != int(rounds_per_cycle):
        raise ModelError("Use an integral per-cycle declared ammunition demand")
    stock = {"money": definitions["startingMoney"], "wood": 0, "stone": 0, "agriculture": 0}
    level, completed, ammo, fired, missed = 1, [], 0, 0, 0
    guns = rounds_per_hour == 0
    snapshots = []
    for cycle in range(hours * 60 // cycle_minutes + 1):
        actions = []
        if cycle:
            raw_cycle(stock, level, definitions)
            if not guns and affordable(stock, add(definitions["offers"]["pistol"], definitions["offers"]["pistol"])):
                debit(stock, add(definitions["offers"]["pistol"], definitions["offers"]["pistol"]))
                guns = True
                actions.append("two_pistols")
            # Ammo has priority over construction once both pistols are bought.
            # No travel, loot, initial/free rounds or demand catch-up is assumed.
            if rounds_per_hour and guns:
                demand = int(rounds_per_cycle)
                while ammo < demand and affordable(stock, definitions["offers"]["ammo9"]):
                    debit(stock, definitions["offers"]["ammo9"])
                    ammo += 32
                    actions.append("ammo9_pack")
                used = min(ammo, demand)
                ammo -= used
                fired += used
                missed += demand - used
            if len(completed) < len(goals):
                goal = goals[len(completed)]
                price = definitions["producerCosts"][1] if goal == "producer2" else definitions["sectorCatalog"]["basic"]
                if affordable(stock, price):
                    debit(stock, price)
                    if goal == "producer2":
                        level = 2  # Applies to the next cycle, not retroactively.
                    completed.append({"goal": goal, "cycle": cycle, "minutes": cycle * cycle_minutes})
                    actions.append(goal)
        snapshots.append({"scenario": name, "roundsPerHour": rounds_per_hour, "cycle": cycle, "minutes": cycle * cycle_minutes,
                          **stock, "producerLevel": level, "completedGoals": len(completed), "ammoStock": ammo,
                          "roundsFired": fired, "unmetRoundsAfterGuns": missed, "actions": ";".join(actions)})
    return {"scenario": name, "roundsPerHour": rounds_per_hour, "goals": goals, "completed": completed,
            "allGoalsCompletedMinutes": completed[-1]["minutes"] if len(completed) == len(goals) else None,
            "finalStock": stock, "roundsFired": fired, "unmetRoundsAfterGuns": missed}, snapshots


def report(repo, output, hours=48, sector_catalog=None):
    definitions = load_definitions(repo, sector_catalog)
    if hours < 1 or hours > 240:
        raise ModelError("Use a bounded 1–240 hour policy horizon")
    basic, reinforced = definitions["sectorCatalog"]["basic"], definitions["sectorCatalog"]["reinforcedIncrement"]
    static = {"one_basic": basic, "one_reinforced_increment": reinforced, "one_basic_plus_reinforced": add(basic, reinforced),
              "four_basic_financial_only": add(*([basic] * 4)), "four_basic_and_reinforced_financial_only": add(*([basic, reinforced] * 4)),
              "producer_1_to_2": definitions["producerCosts"][1], "producer_2_to_3": definitions["producerCosts"][2],
              "city_1_to_2": definitions["cityL2"], "ordinary_factory_1_to_2": definitions["ordinaryFactoryL2"]}
    scenarios, snapshots = [], []
    for rounds in (0, 64, 128):
        for name, goals in (("one_basic", ["basic"]), ("producer_first_one_basic", ["producer2", "basic"]),
                            ("basic_first_producer", ["basic", "producer2"]), ("four_basic", ["basic"] * 4),
                            ("producer_first_four_basic", ["producer2"] + ["basic"] * 4)):
            summary, rows = simulate(definitions, name, goals, rounds, hours)
            scenarios.append(summary)
            snapshots.extend(rows)
    result = {"kind": "explicit-policy-static-and-bounded-cycle-comparison", "balanceCertification": False,
              "sectorSchedule": "OWNER_AUTHORIZED_PROVISIONAL_TWO_TIERS_STOCKPILE_UNITS_NO_ARMED_GEAR", "horizonHours": hours,
              "assumptions": ["One already-owned City A at city/producer L1; nation $12000 and zero stocks as source defines; claiming/founding placement time not modeled.",
                              "Starts just after a cycle boundary; active server at 20TPS; no stopped-server production.",
                              "No debug/demo funding, commerce, trade, capture rewards, processors, travel, death or reequipment; at most one construction goal per cycle, with equipment/ammo priority separate.",
                              "Ammo scenarios buy two pistols first, then prioritize demanded 9mm packs before declared construction; demand is per nation, not per player.",
                              "Four-side rows are financial hypotheticals only; unavailable geometry and neighbor/cap transitions are not enabled.",
                              "Reinforced costs are static only: one starter has no steel/concrete production; no industrial growth timings invented."],
              "definitions": definitions, "staticCosts": static, "scenarios": scenarios}
    output = Path(output)
    output.mkdir(parents=True, exist_ok=False)
    (output / "comparison.json").write_text(json.dumps(result, sort_keys=True, indent=2) + "\n")
    with (output / "cycles.csv").open("w", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(snapshots[0]))
        writer.writeheader()
        writer.writerows(snapshots)
    lines = ["scenario,rounds/nation/hour,completion_minutes"]
    lines += [f"{s['scenario']},{s['roundsPerHour']},{s['allGoalsCompletedMinutes'] if s['allGoalsCompletedMinutes'] is not None else 'NOT_REACHED'}" for s in scenarios]
    text = "\n".join(lines) + "\n"
    (output / "summary.csv").write_text(text)
    print(text, end="")
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--output", type=Path, required=True, help="New evidence directory, never an existing result")
    parser.add_argument("--hours", type=int, default=48)
    parser.add_argument("--sector-catalog", type=Path, help="Explicit candidate Java catalog; recorded as candidate, never as applied runtime source")
    args = parser.parse_args()
    report(args.repo, args.output, args.hours, args.sector_catalog)
