#!/usr/bin/env python3
"""Compare hypothetical economy cadences without changing game configuration or prices.

Reads the045 model/current source; writes only tools/economy46/results.json.
The imported045 main() is never run, so its results and native evidence stay intact.
"""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
MODEL = ROOT / "tools/economy45/simulate.py"
sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location("economy45_model", MODEL)
model = importlib.util.module_from_spec(spec)
spec.loader.exec_module(model)


def run(config, actions, cycle_minutes, max_hours=200):
    """Replay economic cycles and purchases; replace045's hard-coded event hours."""
    sim = model.Sim(copy.deepcopy(config))
    pending = copy.deepcopy(actions)
    max_cycles = max_hours * 60 // cycle_minutes
    snapshots = {}
    for cycle in range(max_cycles + 1):
        assert sim.cycle == cycle
        old_events = len(sim.events)
        while pending and sim.act(pending[0]):
            pending.pop(0)
        for event in sim.events[old_events:]:
            event["cycle"] = cycle
            event["active_minutes"] = cycle * cycle_minutes
            event["hour"] = cycle * cycle_minutes / 60
        if cycle * cycle_minutes in (120, 240, 480, 960, 1440, 2880, 6000, 12000):
            snapshots[str(cycle * cycle_minutes / 60)] = {
                "purchases": len(sim.events), "next": pending[0] if pending else None,
                "stock": sim.stock.copy(), "starved_territory_cycles": sim.starved,
            }
        assert all(value >= 0 for value in sim.stock.values())
        for territory in sim.owned.values():
            assert len(territory["buildings"]) <= 2 * territory["city"]
            assert sum(b["type"] not in ("COMMERCIAL_DISTRICT", "LOGISTICS_CENTER")
                       for b in territory["buildings"]) <= 3
        if not pending or cycle == max_cycles:
            break
        model.manage_targets(sim, pending[0])
        sim.tick()
    return {
        "events": sim.events, "unfinished": pending,
        "completion_cycles": sim.cycle if not pending else None,
        "completion_hours": sim.cycle * cycle_minutes / 60 if not pending else None,
        "final_stock": sim.stock, "snapshots": snapshots,
        "starved_territory_cycles": sim.starved,
    }


def milestone(result, action):
    return next((e["hour"] for e in result["events"] if e["action"] == action), None)


def main():
    config = model.load()
    assert config["cycle_minutes"] == 15, "Revisit pacing comparison when live cadence changes"
    source_costs = copy.deepcopy(config["costs"])
    profiles = {}
    for name, minutes in (("long_baseline", 15), ("medium_hypothesis", 5), ("fast_hypothesis", 1)):
        config_for_profile = copy.deepcopy(config)
        config_for_profile["cycle_minutes"] = minutes
        assert {k: v for k, v in config_for_profile.items() if k != "cycle_minutes"} == {k: v for k, v in config.items() if k != "cycle_minutes"}
        # Time is explicitly calculated by this wrapper.045 Sim.run() and its hour labels are not used.
        progression = run(config_for_profile, model.plan(2), minutes)
        solo_starter = run(config_for_profile, [("buy", "a", "procure_pistol"), ("buy", "a", "procure_ammo9")], minutes)
        team_starter = run(config_for_profile, [("buy", "a", "procure_pistol"), ("buy", "a", "procure_ammo9")] * 2, minutes)
        cycle_rate = 60 / minutes
        income = {"starter_per_hour": config["cash"] * cycle_rate,
                  "four_territories_one_l1_commercial_per_hour": (4 * config["cash"] + config["commercial"][0]) * cycle_rate}
        ammo = config["costs"]["ammo_tacz:762x39"]
        fixed_hour_demand = {k: value * 4 * 4 for k, value in ammo.items()}
        profiles[name] = {
            "hypothetical": minutes != 15, "cycle_minutes": minutes,
            "cycle_frequency_vs_current": 15 / minutes,
            "per_cycle_values_and_all_prices_unchanged": config_for_profile["costs"] == source_costs,
            "milestones_hours": {
                "independent_one_player_pistol_and_32_rounds": solo_starter["completion_hours"],
                "independent_two_player_pistols_and_32_rounds_each": team_starter["completion_hours"],
                "commercial_in_41_action_queue": milestone(progression, ("build", "a", "COMMERCIAL_DISTRICT")),
                "vehicle_factory_in_41_action_queue": milestone(progression, ("build", "g", "VEHICLE_FACTORY")),
                "pickup_in_41_action_queue": milestone(progression, ("buy", "g", "procure_pickup")),
                "lav_in_41_action_queue": milestone(progression, ("buy", "g", "procure_lav150")),
                "complete_41_action_queue": progression["completion_hours"],
            },
            "cash_income": income,
            "ammo_assumption_four_players_four_packs_each_per_active_hour": fixed_hour_demand,
            "ammo_cash_share_of_four_territory_commercial_income": fixed_hour_demand["money"] / income["four_territories_one_l1_commercial_per_hour"],
            "managed_progression": progression,
            "independent_starter_one_player": solo_starter,
            "independent_starter_two_players": team_starter,
        }
    baseline = profiles["long_baseline"]["managed_progression"]
    assert baseline["completion_cycles"] == 265 and baseline["completion_hours"] == 66.25
    assert len(baseline["events"]) == 41 and not baseline["unfinished"]
    # Same cycle rules must produce identical decisions, debits and stocks in every cadence.
    for profile in profiles.values():
        actual = profile["managed_progression"]
        assert actual["completion_cycles"] == 265
        assert [(e["cycle"], e["action"], e["price"]) for e in actual["events"]] == [(e["cycle"], e["action"], e["price"]) for e in baseline["events"]]
        assert actual["final_stock"] == baseline["final_stock"]
        assert profile["independent_starter_one_player"]["completion_cycles"] == 2
        assert profile["independent_starter_two_players"]["completion_cycles"] == 4
        assert profile["ammo_assumption_four_players_four_packs_each_per_active_hour"] == {"money": 2800, "steel": 32, "explosives": 16}
        assert profile["per_cycle_values_and_all_prices_unchanged"]
    assert config["costs"] == source_costs and config["cycle_minutes"] == 15
    output = {
        "schema": 1,
        "source_commit_at_execution": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
        "model_sha256": hashlib.sha256(MODEL.read_bytes()).hexdigest(),
        "source_sha256": model.SOURCES,
        "scope": "Offline hypothetical cadence comparison. No runtime changes, approved prices or chosen campaign duration.",
        "assumptions": {
            "profile_labels": "Relative labels only:15/5/1-minute cycles are illustrative, not user-selected targets.",
            "rules": "Every per-cycle output, income, upkeep, batch rule, target, price, starting balance and cap remains unchanged.",
            "time": "Full scheduled cycles at20TPS, no offline/catchup grants, construction/travel/UI delays excluded.",
            "starter_experiments": "Fresh independent nations; starter spending is not silently added free to the separate41-action queue.",
            "queue": "Same managed-target two-player41-action midgame queue as045, no search for an optimal order.",
            "vehicle": "Economic milestone only; native bay/ownership/geometry and complete purchase route readiness remain separate.",
            "combat": "Fixed consumption sensitivity only; no firing, losses, capture, player behavior, trade or monument rewards modeled.",
            "food_and_targets": "Upkeep frequency increases with production frequency; all physical slots and target caps stay enforced.",
        },
        "profiles": profiles,
        "validation": {"validated_045_offline_baseline_265_cycles_reproduced": True,
                       "identical_per_cycle_actions_debits_and_final_balances": True,
                       "time_conversion_and_nonnegative_balances_checked": True,
                       "physical_slot_caps_preserved": True,
                       "fixed_real_hour_ammo_demand_preserved": True,
                       "runtime_or_native_profile_test": False},
    }
    target = ROOT / "tools/economy46/results.json"
    target.write_text(json.dumps(output, indent=2, sort_keys=True) + "\n")
    print(json.dumps({name: p["milestones_hours"] for name, p in profiles.items()}, indent=2))


if __name__ == "__main__":
    main()
