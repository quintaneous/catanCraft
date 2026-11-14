"""Synthetic packaging/evidence checks only; no Java, game, owner save or network."""
import json
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import create_progression_run as runs
import progression_ladder as ladder
import test_package_authored_demo as fixtures


class ProgressionRunTests(unittest.TestCase):
    def setUp(self):
        self.fixture = fixtures.AuthoredDemoPackageTests()
        self.fixture.setUp()
        self.addCleanup(self.fixture.doCleanups)
        self.fixture.clean_r7()
        self.base = self.fixture.base
        self.input = self.fixture.inputs
        self.map_bytes = (Path(__file__).resolve().parents[1] /
                          "src/main/resources/data/catancraft/maps/season1_river_bridges_v3.json").read_bytes()
        self.fixture.write_input(self.fixture.manifest["config"][0], self.map_bytes)
        self.fixture.manifest["bootstrap"]["authorization"]["mapSha256"] = runs.sha256(self.map_bytes)
        self.manifest = self.input / "baseline.json"
        self.rewrite_manifest()
        self.output = self.base / "run-001"

    def rewrite_manifest(self):
        self.manifest.write_bytes(ladder.encoded(self.fixture.manifest))
        self.pin = runs.sha256(self.manifest.read_bytes())

    def create(self, output=None):
        return runs.create_run(self.input, self.manifest, self.pin, output or self.output)

    def plan(self):
        return ladder.plan_for(self.map_bytes, "run-1", "a" * 40, "b" * 64)

    def test_new_runs_independent_and_previous_progress_and_inputs_unchanged(self):
        before = {p.name: p.read_bytes() for p in self.input.iterdir()}
        first = self.create()
        world = self.output / self.fixture.manifest["world"]["root"]
        (world / "data").mkdir()
        (world / "data" / "catancraft.dat").write_bytes(b"DO NOT RESET PAID PROGRESS")
        (world / "level.dat").write_bytes(b"A PLAYED RUN")
        second_path = self.base / "run-002"
        second = self.create(second_path)
        self.assertNotEqual(first["runId"], second["runId"])
        self.assertNotEqual(first["derivedManifestSHA256"], second["derivedManifestSHA256"])
        self.assertEqual(first["baselineManifestSHA256"], second["baselineManifestSHA256"])
        self.assertEqual((world / "level.dat").read_bytes(), b"A PLAYED RUN")
        self.assertEqual((world / "data" / "catancraft.dat").read_bytes(), b"DO NOT RESET PAID PROGRESS")
        self.assertFalse((second_path / world.name / "data").exists())
        self.assertEqual((second_path / world.name / "level.dat").read_bytes(), self.fixture.world_files["level.dat"])
        self.assertEqual(before, {p.name: p.read_bytes() for p in self.input.iterdir()})
        self.assertNotEqual((world / "region/r.0.0.mca").stat().st_ino,
                            (second_path / world.name / "region/r.0.0.mca").stat().st_ino)

    def test_existing_output_and_symlink_parent_refuse(self):
        self.output.mkdir()
        marker = self.output / "keep.txt"
        marker.write_text("KEEP")
        with self.assertRaises(runs.InputError):
            self.create()
        link = self.base / "linked"
        link.symlink_to(self.output, target_is_directory=True)
        with self.assertRaises(runs.InputError):
            self.create(link / "new")
        self.assertEqual(marker.read_text(), "KEEP")
        self.assertEqual(list(self.output.iterdir()), [marker])

    def test_installed_saves_destination_refuses(self):
        folder = self.base / "saves"
        folder.mkdir()
        with self.assertRaises(runs.InputError):
            self.create(folder / "new")
        self.assertEqual(list(folder.iterdir()), [])

    def test_bad_baseline_pin_refuses_without_output(self):
        self.manifest.write_bytes(self.manifest.read_bytes() + b" ")
        with self.assertRaises(runs.InputError):
            self.create()
        self.assertFalse(self.output.exists())

    def test_inherited_ledger_and_datapack_refuse(self):
        for path in ("data/catancraft.dat", "datapacks/funding/pack.mcmeta"):
            with self.subTest(path=path):
                self.fixture.world_files[path] = b"inherited or funding content"
                self.fixture.rewrite_world()
                self.rewrite_manifest()
                with self.assertRaises(runs.InputError):
                    self.create()
                self.assertFalse(self.output.exists())
                del self.fixture.world_files[path]

    def test_profile_provenance_and_all_city_templates_are_honest(self):
        result = self.create()
        package = json.loads((self.output / "PACKAGE.json").read_bytes())
        self.assertTrue(result["fixtureOnly"])
        self.assertFalse(result["gameplayQualified"])
        self.assertFalse(package["runtimeBundled"])
        self.assertFalse(package["fullModpack"])
        self.assertEqual(package["authorization"]["worldId"], result["runId"])
        self.assertNotIn("installationBoundPath", package["authorization"])
        self.assertIn("eula=false", (self.output / "eula.txt").read_text())
        self.assertFalse((self.output / ".demo-launch-state.json").exists())
        for key, value in runs.PROFILE.items():
            self.assertEqual(package["serverProperties"][key], value)
        for name, pin in package["immutableFiles"].items():
            self.assertEqual(runs.sha256((self.output / name).read_bytes()), pin, name)
        plan = json.loads((self.output / "PROGRESSION_PLAN.json").read_bytes())
        self.assertEqual(len(plan["checks"]), 16 * len(ladder.STAGES))
        self.assertEqual({r["city"] for r in plan["checks"]}, set("abcdefghijklmnop"))
        self.assertEqual({r["center"]["rotation"] for r in plan["checks"]}, {0, 180})
        for i, (stage, _) in enumerate(ladder.STAGES):
            template = json.loads((self.output / "evidence" / f"results-{stage}.json").read_bytes())
            self.assertEqual(len(template["checks"]), 16 * (i + 1))
            self.assertTrue(all(r["status"] == "NOT_RUN" for r in template["checks"]))
            report = ladder.evaluate(plan, template, self.output, stage)
            self.assertEqual(report["status"], "BLOCKED")

    def test_existing_launcher_can_verify_new_package_without_launch_or_binding(self):
        self.create()
        self.fixture.output = self.output
        launcher = self.fixture.launcher()
        self.assertEqual(self.fixture.launch(launcher, verify=True), 0)
        self.assertFalse((self.output / ".demo-launch-state.json").exists())

    def test_publish_race_preserves_concurrently_created_destination(self):
        original = runs.assemble
        def raced(*args):
            result = original(*args)
            self.output.mkdir()
            (self.output / "keep.txt").write_text("OTHER RUN")
            return result
        with patch.object(runs, "assemble", side_effect=raced), self.assertRaises(FileExistsError):
            self.create()
        self.assertEqual((self.output / "keep.txt").read_text(), "OTHER RUN")
        self.assertEqual(len(list(self.output.iterdir())), 1)

    def test_missing_city_and_changed_orientation_maps_refuse(self):
        data = json.loads(self.map_bytes)
        data["territories"].pop()
        with self.assertRaises(runs.InputError):
            ladder.plan_for(ladder.encoded(data), "run", "a" * 40, "b" * 64)
        data = json.loads(self.map_bytes)
        data["territories"][0]["cityCenter"]["rotation"] = 90
        with self.assertRaises(runs.InputError):
            ladder.plan_for(ladder.encoded(data), "run", "a" * 40, "b" * 64)

    def test_earlier_stage_or_city_omission_cannot_qualify_later_checkpoint(self):
        plan = self.plan()
        result = ladder.results_template(plan, "tier_5")
        result["checks"].pop(0)
        with self.assertRaises(runs.InputError):
            ladder.evaluate(plan, result, self.base, "tier_5")
        plan["checks"].pop(0)
        with self.assertRaises(runs.InputError):
            ladder.results_template(plan, "tier_5")

    def test_stale_run_checkpoint_and_funded_results_refuse(self):
        plan = self.plan()
        for key, value in (("runId", "old-run"), ("checkpoint", "founding"),
                           ("planSHA256", "0" * 64), ("fundingUsed", True)):
            with self.subTest(key=key):
                result = ladder.results_template(plan, "clean_start")
                result[key] = value
                with self.assertRaises(runs.InputError):
                    ladder.evaluate(plan, result, self.base, "clean_start")

    def test_pass_needs_nonempty_matching_safe_evidence_and_review_still_required(self):
        plan = self.plan()
        result = ladder.results_template(plan, "clean_start")
        log = self.base / "synthetic-proof.txt"
        log.write_bytes(b"SYNTHETIC EVIDENCE VALIDATOR TEST; NO GAMEPLAY")
        for row in result["checks"]:
            row.update(status="PASS", evidence=[{"path": log.name, "sha256": runs.sha256(log.read_bytes())}])
        report = ladder.evaluate(plan, result, self.base, "clean_start")
        self.assertEqual(report["status"], "EVIDENCE_COMPLETE_REQUIRES_REVIEW")
        self.assertFalse(report["gameplayExecutedByThisTool"])
        self.assertFalse(report["freshExecutionVerified"])
        self.assertFalse(report["evidenceSemanticsVerified"])
        self.assertFalse(report["ownerAcceptance"])
        for evidence in ([], [{"path": "../outside", "sha256": "a" * 64}],
                         [{"path": log.name, "sha256": "b" * 64}]):
            result["checks"][0]["evidence"] = evidence
            with self.assertRaises(runs.InputError):
                ladder.evaluate(plan, result, self.base, "clean_start")

    def test_duplicate_rows_and_unreviewed_na_refuse(self):
        plan = self.plan()
        result = ladder.results_template(plan, "clean_start")
        result["checks"][0] = dict(result["checks"][1])
        with self.assertRaises(runs.InputError):
            ladder.evaluate(plan, result, self.base, "clean_start")
        result = ladder.results_template(plan, "clean_start")
        result["checks"][0]["status"] = "N/A"
        with self.assertRaises(runs.InputError):
            ladder.evaluate(plan, result, self.base, "clean_start")


if __name__ == "__main__":
    unittest.main()
