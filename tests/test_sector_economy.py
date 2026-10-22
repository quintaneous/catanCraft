"""Economic comparison arithmetic/policy tests; no Java, worlds, mods or network."""
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("simulate_sector_economy", Path(__file__).resolve().parents[1] / "tools/simulate_sector_economy.py")
MODEL = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODEL)
REPO = Path(__file__).resolve().parents[1]


class SectorEconomyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.definitions = MODEL.load_definitions(REPO)

    def test_current_source_starting_stock_cycle_and_upgrade_costs(self):
        d = self.definitions
        self.assertEqual((d["startingMoney"], d["startingStockpile"], d["cycleMinutes"], d["incomePerCycle"]), (12000, 0, 15, 125))
        self.assertEqual(d["producerCosts"][1], {"money": 2000, "wood": 30, "stone": 30})
        self.assertEqual(d["producerCosts"][2], {"money": 5000, "concrete": 40, "steel": 10})
        self.assertEqual(d["offers"]["ammo9"], {"money": 100, "stone": 5})
        self.assertEqual(len(d["sourcePins"]), 8)

    def test_provisional_sector_cost_is_incremental_and_in_stockpile_units(self):
        self.assertEqual(MODEL.BASIC, {"money": 1500, "wood": 15, "stone": 30})
        self.assertEqual(MODEL.REINFORCED, {"money": 4000, "concrete": 50, "steel": 15})
        self.assertEqual(MODEL.add(MODEL.BASIC, MODEL.REINFORCED), {"money": 5500, "wood": 15, "stone": 30, "concrete": 50, "steel": 15})
        self.assertEqual(MODEL.add(*([MODEL.BASIC, MODEL.REINFORCED] * 4)), {"money": 22000, "wood": 60, "stone": 120, "concrete": 200, "steel": 60})

    def test_java_positive_half_rounding_and_agriculture_upkeep(self):
        stock = {"money": 0, "wood": 0, "stone": 0, "agriculture": 0}
        MODEL.raw_cycle(stock, 1, self.definitions)
        self.assertEqual(stock, {"money": 125, "wood": 5, "stone": 5, "agriculture": 4})
        MODEL.raw_cycle(stock, 5, self.definitions)
        self.assertEqual((stock["wood"], stock["stone"], stock["agriculture"]), (38, 38, 29))

    def test_no_food_produces_no_nonfood_not_free_material(self):
        d = copy.deepcopy(self.definitions)
        d["starterYields"]["agriculture"] = 0
        stock = {"money": 0}
        MODEL.raw_cycle(stock, 1, d)
        self.assertEqual((stock["wood"], stock["stone"], stock["money"]), (0, 0, 125))

    def test_debit_checks_every_material_without_partial_change(self):
        stock = {"money": 12000, "wood": 15, "stone": 29}
        before = stock.copy()
        with self.assertRaises(MODEL.ModelError):
            MODEL.debit(stock, MODEL.BASIC)
        self.assertEqual(stock, before)
        stock["stone"] = 30
        MODEL.debit(stock, MODEL.BASIC)
        self.assertEqual(stock, {"money": 10500, "wood": 0, "stone": 0})

    def test_no_ammo_one_side_vs_growth_policy(self):
        basic, _ = MODEL.simulate(self.definitions, "one", ["basic"], hours=8)
        productive, _ = MODEL.simulate(self.definitions, "productive", ["producer2", "basic"], hours=8)
        defensive, _ = MODEL.simulate(self.definitions, "defensive", ["basic", "producer2"], hours=8)
        self.assertEqual(basic["allGoalsCompletedMinutes"], 90)
        self.assertEqual([p["minutes"] for p in productive["completed"]], [90, 150])
        self.assertEqual([p["minutes"] for p in defensive["completed"]], [90, 180])

    def test_upgrades_apply_only_to_next_production_cycle(self):
        _, rows = MODEL.simulate(self.definitions, "producer", ["producer2"], hours=2)
        self.assertEqual(rows[6]["stone"], 0)
        self.assertEqual(rows[7]["stone"], 9)

    def test_ammo_priority_can_exhaust_the_stone_stream_without_hiding_demand(self):
        result, rows = MODEL.simulate(self.definitions, "ammo128", ["basic"], rounds_per_hour=128, hours=48)
        self.assertIsNone(result["allGoalsCompletedMinutes"])
        self.assertEqual(result["completed"], [])
        self.assertGreater(result["roundsFired"], 0)
        self.assertGreater(result["unmetRoundsAfterGuns"], 0)
        self.assertTrue(all(r["stone"] >= 0 and r["money"] >= 0 for r in rows))
        self.assertEqual(rows[2]["actions"], "two_pistols")

    def test_perimeter_comparison_accounts_for_all_four_costs_and_no_free_upgrades(self):
        result, _ = MODEL.simulate(self.definitions, "perimeter", ["basic"] * 4, hours=8)
        self.assertEqual(result["allGoalsCompletedMinutes"], 360)
        self.assertEqual(len(result["completed"]), 4)
        self.assertEqual(result["finalStock"]["money"], 12000 + 32 * 125 - 4 * 1500)

    def test_source_price_parser_refuses_unknown_expression(self):
        with self.assertRaises(MODEL.ModelError):
            MODEL.cost_expression("new EconomyCost(BASE_PRICE, Map.of(ResourceType.WOOD, 5L))")
        with self.assertRaises(MODEL.ModelError):
            MODEL.cost_expression("new EconomyCost(10, Map.of(ResourceType.WOOD, DYNAMIC))")

    def test_versioned_sector_catalog_is_parsed_pinned_and_mismatch_refuses(self):
        source = '''static final String VERSION="DEF04_TEST_V1";
private static final EconomyCost BASIC=new EconomyCost(1500,Map.of(ResourceType.WOOD,15L,ResourceType.STONE,30L));
private static final EconomyCost REINFORCED_INCREMENT=new EconomyCost(4000,Map.of(ResourceType.CONCRETE,50L,ResourceType.STEEL,15L));'''
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "SectorPriceCatalog.java"
            path.write_text(source)
            prices = MODEL.sector_prices(REPO, path)
            self.assertEqual(prices["version"], "DEF04_TEST_V1")
            self.assertEqual(prices["basic"], MODEL.BASIC)
            self.assertEqual(prices["status"], "EXPLICIT_CANDIDATE_SOURCE_NOT_APPLIED")
            self.assertEqual(len(prices["sha256"]), 64)
            path.write_text(source.replace("1500", "1501"))
            with self.assertRaises(MODEL.ModelError):
                MODEL.sector_prices(REPO, path)

    def test_nonintegral_ammo_demand_requires_explicit_policy_update(self):
        with self.assertRaises(MODEL.ModelError):
            MODEL.simulate(self.definitions, "unsupported", ["basic"], rounds_per_hour=1)

    def test_unknown_goals_and_negative_demand_cannot_become_free_actions(self):
        for goals, rounds in [(["reinforced"], 0), (["producer2", "producer2"], 0), (["basic"], -4)]:
            with self.assertRaises(MODEL.ModelError):
                MODEL.simulate(self.definitions, "unsupported", goals, rounds_per_hour=rounds)

    def test_reports_have_pins_explicit_limits_and_preserve_existing_evidence(self):
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp) / "comparison"
            report = MODEL.report(REPO, output, hours=2)
            self.assertFalse(report["balanceCertification"])
            self.assertEqual(len(report["scenarios"]), 15)
            saved = json.loads((output / "comparison.json").read_text())
            self.assertEqual(len(saved["definitions"]["sourcePins"]), 8)
            self.assertTrue((output / "cycles.csv").read_text().startswith("scenario,roundsPerHour,cycle"))
            before = (output / "comparison.json").read_bytes()
            with self.assertRaises(FileExistsError):
                MODEL.report(REPO, output, hours=2)
            self.assertEqual((output / "comparison.json").read_bytes(), before)


if __name__ == "__main__":
    unittest.main()
