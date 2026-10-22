"""Artificial ZIP/NBT fixtures only; never launch Java, Minecraft or a real world."""
from io import BytesIO
import gzip
import importlib.util
import json
import os
from pathlib import Path
import struct
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import zipfile

SPEC = importlib.util.spec_from_file_location("package_authored_demo", Path(__file__).resolve().parents[1] / "tools/package_authored_demo.py")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def archive(files):
    stream = BytesIO()
    with zipfile.ZipFile(stream, "w") as z:
        for name, value in files.items():
            z.writestr(name, value)
    return stream.getvalue()


def text(value):
    data = value.encode()
    return struct.pack(">H", len(data)) + data


def level(player=False, name="Synthetic authored fixture", version=None):
    data = b"\x0a" + text("") + b"\x0a" + text("Data")
    data += b"\x08" + text("LevelName") + text(name)
    data += b"\x0a" + text("WorldGenSettings") + b"\x04" + text("seed") + struct.pack(">q", 42) + b"\x00"
    if version is not None:
        data += b"\x03" + text("DataVersion") + struct.pack(">i", version)
    if player:
        data += b"\x0a" + text("Player") + b"\x00"
    return gzip.compress(data + b"\x00\x00", mtime=0)


class AuthoredDemoPackageTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.inputs = self.base / "transferred-inputs"
        self.inputs.mkdir()
        self.output = self.base / "new-demo"
        self.world_files = {"level.dat": level(), "region/r.0.0.mca": b"\0" * 8192,
                            "datapacks/bootstrap/pack.mcmeta": b'{"pack":{"pack_format":15,"description":"SYNTHETIC TEST ONLY"}}'}
        self.manifest = {
            "schemaVersion": 1, "packageKind": "catancraft-authored-demo-input", "fixtureOnly": True,
            "approval": {"freshAuthoredWorld": True, "bootstrapReviewed": True, "sectorArtEnabled": False},
            "sourceCommit": "a" * 40,
            "runtime": {"javaMajor": 17, "minecraft": "1.20.1", "forge": "47.4.10"},
            "world": {"source": "fresh-world.zip", "root": "Synthetic_World", "levelName": "Synthetic authored fixture", "seed": 42, "regionFiles": 1},
            "bootstrap": {"mode": "reviewed-transferred-bootstrap", "metadataFiles": ["datapacks/bootstrap/pack.mcmeta"],
                          "authorization": {"worldId": "12345678-1234-4234-8234-123456789abc", "mapSha256": "b" * 64, "bootstrapRevision": "synthetic-fixture-no-gameplay-claim"}},
            "jar": {"source": "built.jar", "version": "0.1.29"},
            "sourceEvidence": {"source": "source-evidence.json"},
            "config": [{"source": "config.json", "target": "config/catancraft/map.json"}],
            "assets": [{"source": "assets.zip", "target": "config/catancraft/fixture-assets.zip"}],
            "forgeRuntimeFiles": {f"libraries/net/minecraftforge/forge/{MODULE.FORGE}/{name}": MODULE.sha256(b"official-fixture-" + name.encode())
                                  for name in ("unix_args.txt", "win_args.txt", f"forge-{MODULE.FORGE}-server.jar")},
        }
        metadata = b'''modLoader="javafml"
loaderVersion="[47,)"
[[mods]]
modId="catancraft"
version="0.1.29"
[[dependencies.catancraft]]
modId="forge"
versionRange="[47.4.10,)"
[[dependencies.catancraft]]
modId="minecraft"
versionRange="[1.20.1,1.20.2)"
'''
        self.write_input(self.manifest["jar"], archive({"META-INF/mods.toml": metadata}))
        evidence = {"kind": "matching-runtime-evidence", "sourceCommit": "a" * 40,
                    "jarSHA256": self.manifest["jar"]["sha256"], "fixtureOnly": True}
        self.write_input(self.manifest["sourceEvidence"], json.dumps(evidence).encode())
        self.write_input(self.manifest["config"][0], b'{"fixtureOnly":true}')
        self.manifest["bootstrap"]["authorization"]["mapSha256"] = self.manifest["config"][0]["sha256"]
        self.write_input(self.manifest["assets"][0], archive({"fixture.txt": b"artificial test asset"}))
        self.rewrite_world()

    def write_input(self, row, content):
        (self.inputs / row["source"]).write_bytes(content)
        row["sha256"] = MODULE.sha256(content)

    def rewrite_world(self):
        self.manifest["world"]["files"] = {name: MODULE.sha256(value) for name, value in self.world_files.items()}
        self.write_input(self.manifest["world"], archive({self.manifest["world"]["root"] + "/" + name: value for name, value in self.world_files.items()}))

    def assemble(self):
        manifest_file = self.inputs / "input-manifest.json"
        data = json.dumps(self.manifest).encode()
        manifest_file.write_bytes(data)
        return MODULE.assemble(self.inputs, manifest_file, MODULE.sha256(data), self.output)

    def refuses(self):
        with self.assertRaises((MODULE.InputError, OSError)):
            self.assemble()
        self.assertFalse(self.output.exists())

    def launcher(self):
        spec = importlib.util.spec_from_file_location("synthetic_demo_launcher", self.output / "tools/launch_demo.py")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module

    def launch(self, module, verify=False):
        for name in self.manifest["forgeRuntimeFiles"]:
            file = self.output / name
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_bytes(b"official-fixture-" + file.name.encode())
        (self.output / "eula.txt").write_text("eula=true\n")
        result = SimpleNamespace(returncode=0, stdout="", stderr="java.version = 17.0.16\nsun.arch.data.model = 64\n")
        with patch.object(module.shutil, "which", return_value="/never-executed-java"), patch.object(module.subprocess, "run", return_value=result), patch.object(module.subprocess, "call", return_value=0) as server, patch.object(sys, "argv", ["launch_demo.py"] + (["--verify-only"] if verify else [])):
            old = Path.cwd()
            try:
                code = module.main()
            finally:
                os.chdir(old)
            if verify:
                server.assert_not_called()
            else:
                server.assert_called_once()
        return code

    def clean_r7(self):
        self.manifest["world"].update(root=MODULE.R7_ROOT, levelName=MODULE.R7_NAME, regionFiles=16)
        self.world_files={"level.dat":level(name=MODULE.R7_NAME, version=3465)}
        self.world_files.update({f"region/r.{x}.{z}.mca":b"\0"*8192 for x in range(-2,2) for z in range(-2,2)})
        self.manifest["bootstrap"].update(mode=MODULE.R7_MODE,metadataFiles=[],r7OptIn={"source":"r7-opt-in.txt"})
        self.write_input(self.manifest["bootstrap"]["r7OptIn"],MODULE.R7_OPT_IN)
        self.rewrite_world()

    def test_clean_r7_exact_opt_in_added_only_to_output_not_source(self):
        self.clean_r7();original=(self.inputs/"fresh-world.zip").read_bytes();result=self.assemble()
        self.assertEqual((self.inputs/"fresh-world.zip").read_bytes(),original)
        self.assertEqual((self.output/MODULE.R7_ROOT/MODULE.R7_MARKER).read_bytes(),MODULE.R7_OPT_IN)
        self.assertEqual(len(result["initialWorldFiles"]),19)
        self.assertFalse((self.output/MODULE.R7_ROOT/"datapacks").exists())
        self.assertFalse((self.output/".demo-launch-state.json").exists())

    def test_clean_r7_refuses_changed_opt_in_even_when_manifest_rehashed(self):
        self.clean_r7();self.write_input(self.manifest["bootstrap"]["r7OptIn"],b"arbitrary permission");self.refuses()

    def test_clean_r7_refuses_changed_world_identity(self):
        self.clean_r7();self.world_files["level.dat"]=level(name="Renamed",version=3465)
        self.manifest["world"]["levelName"]="Renamed";self.rewrite_world();self.refuses()

    def test_clean_r7_refuses_missing_region_even_when_count_rewritten(self):
        self.clean_r7();del self.world_files["region/r.0.0.mca"]
        self.manifest["world"]["regionFiles"]=15;self.rewrite_world();self.refuses()

    def test_clean_r7_refuses_transferred_opt_in(self):
        self.clean_r7();self.world_files[MODULE.R7_MARKER]=MODULE.R7_OPT_IN
        self.manifest["bootstrap"]["metadataFiles"]=[MODULE.R7_MARKER];self.rewrite_world();self.refuses()

    def test_clean_r7_refuses_funding_datapack_even_when_manifest_rehashed(self):
        self.clean_r7();self.world_files["datapacks/funding/pack.mcmeta"]=b'{"pack":{"pack_format":15}}'
        self.rewrite_world();self.refuses()

    def approved_r7(self, evidence=True):
        self.clean_r7()
        self.manifest["approval"]["sectorArtEnabled"] = True
        self.manifest["sectorApproval"] = json.loads(json.dumps(MODULE.CITY_A_APPROVAL))
        if evidence:
            row = self.manifest["sourceEvidence"]
            report = json.loads((self.inputs / row["source"]).read_text())
            report["sectorValidation"] = MODULE.R7_NATIVE_PASS
            self.write_input(row, json.dumps(report).encode())

    def test_approved_r7_scope_and_matching_native_evidence_recorded(self):
        self.approved_r7()
        result = self.assemble()
        self.assertTrue(result["sectorArtEnabled"])
        self.assertEqual(result["sectorApproval"], MODULE.CITY_A_APPROVAL)
        self.assertTrue(result["fixtureOnly"])  # This Python fixture is not native evidence.

    def test_approved_r7_without_native_evidence_refuses(self):
        self.approved_r7(evidence=False)
        self.refuses()

    def test_approved_r7_cannot_expand_to_another_city(self):
        self.approved_r7()
        self.manifest["sectorApproval"]["city"] = "b"
        self.refuses()

    def test_complete_fixture_package_has_both_start_scripts_and_matching_provenance(self):
        result = self.assemble()
        self.assertTrue(result["fixtureOnly"])
        self.assertFalse(result["runtimeBundled"])
        self.assertFalse(result["sectorArtEnabled"])
        self.assertEqual(result["sourceCommit"], "a" * 40)
        for name in ["START_DEMO.sh", "START_DEMO.bat", "tools/launch_demo.py", "eula.txt", "PACKAGE.json"]:
            self.assertTrue((self.output / name).is_file())
        self.assertIn("eula=false", (self.output / "eula.txt").read_text())
        self.assertFalse((self.output / "libraries").exists())
        auth = json.loads((self.output / "Synthetic_World/catancraft-fresh-demo.json").read_text())
        self.assertNotIn("installationBoundPath", auth)
        self.assertEqual(auth["authoredWorldSha256"], self.manifest["world"]["sha256"])

    def test_verify_only_checks_but_does_not_start_or_bind(self):
        self.assemble()
        self.launch(self.launcher(), verify=True)
        self.assertFalse((self.output / ".demo-launch-state.json").exists())
        auth = json.loads((self.output / "Synthetic_World/catancraft-fresh-demo.json").read_text())
        self.assertNotIn("installationBoundPath", auth)

    def test_ordinary_restart_preserves_gameplay_changes(self):
        self.assemble()
        launcher = self.launcher()
        self.launch(launcher)
        world = self.output / "Synthetic_World"
        (world / "level.dat").write_bytes(b"legitimate changed level after gameplay")
        (world / "region/r.0.0.mca").write_bytes(b"legitimate region changes")
        (world / "data").mkdir()
        (world / "data/catancraft.dat").write_bytes(b"legitimate nation and purchased progress")
        self.launch(launcher)
        self.assertEqual((world / "data/catancraft.dat").read_bytes(), b"legitimate nation and purchased progress")

    def test_missing_receipt_after_binding_refuses_instead_of_reset(self):
        self.assemble()
        launcher = self.launcher()
        self.launch(launcher)
        (self.output / ".demo-launch-state.json").unlink()
        with self.assertRaises(RuntimeError):
            self.launch(launcher)

    def test_missing_or_changed_authorization_on_restart_refuses(self):
        self.assemble()
        launcher = self.launcher()
        self.launch(launcher)
        auth = self.output / "Synthetic_World/catancraft-fresh-demo.json"
        value = json.loads(auth.read_text())
        value["installationBoundPath"] = "/different/world"
        auth.write_text(json.dumps(value))
        with self.assertRaises(RuntimeError):
            self.launch(launcher)
        auth.unlink()
        with self.assertRaises(OSError):
            self.launch(launcher)

    def test_existing_destination_preserved(self):
        self.output.mkdir()
        (self.output / "keep.txt").write_text("user work")
        with self.assertRaises(MODULE.InputError):
            self.assemble()
        self.assertEqual((self.output / "keep.txt").read_text(), "user work")

    def test_unapproved_world_and_sector_art_refuse(self):
        self.manifest["approval"]["sectorArtEnabled"] = True
        self.refuses()

    def test_wrong_world_member_hash_refuses(self):
        self.manifest["world"]["files"]["level.dat"] = "0" * 64
        self.refuses()

    def test_inherited_ledger_player_and_entity_files_refuse(self):
        for path in ["data/catancraft.dat", "playerdata/player.dat", "entities/r.0.0.mca", "data/catancraft_sector_a_north.dat"]:
            with self.subTest(path=path):
                self.world_files[path] = b"inherited work"
                self.rewrite_world()
                self.refuses()
                del self.world_files[path]

    def test_inherited_single_player_nbt_refuses(self):
        self.world_files["level.dat"] = level(player=True)
        self.rewrite_world()
        self.refuses()

    def test_world_seed_identity_and_unsupported_runtime_refuse(self):
        self.manifest["world"]["seed"] = 99
        self.refuses()
        self.manifest["world"]["seed"] = 42
        self.manifest["runtime"]["forge"] = "47.4.11"
        self.refuses()

    def test_source_evidence_mismatch_refuses(self):
        self.write_input(self.manifest["sourceEvidence"], json.dumps({"kind": "matching-runtime-evidence", "sourceCommit": "c" * 40, "jarSHA256": self.manifest["jar"]["sha256"]}).encode())
        self.refuses()

    def test_missing_bootstrap_or_forge_platform_hash_refuses(self):
        del self.manifest["forgeRuntimeFiles"][f"libraries/net/minecraftforge/forge/{MODULE.FORGE}/win_args.txt"]
        self.refuses()

    def test_zip_traversal_and_windows_unsafe_paths_refuse(self):
        for path in ["../escape", "CON.txt", "region/bad:stream"]:
            with self.subTest(path=path):
                self.world_files[path] = b"unapproved"
                self.rewrite_world()
                self.refuses()
                del self.world_files[path]

    def test_transferred_test_or_bound_authorization_marker_refuses(self):
        self.world_files["catancraft-fresh-demo.json"] = b'{"installationBoundPath":"old world"}'
        self.manifest["bootstrap"]["metadataFiles"].append("catancraft-fresh-demo.json")
        self.rewrite_world()
        self.refuses()

    def test_symlink_source_and_destination_parent_refuse(self):
        source = self.inputs / "built.jar"
        actual = self.inputs / "original.jar"
        source.rename(actual)
        source.symlink_to(actual)
        self.refuses()

    def invoke(self, launcher):
        old = Path.cwd()
        try:
            return launcher.main()
        finally:
            os.chdir(old)

    def test_eula_and_missing_runtime_refuse_without_java(self):
        self.assemble()
        launcher = self.launcher()
        with patch.object(launcher.subprocess, "run") as java:
            with self.assertRaisesRegex(RuntimeError, "EULA"):
                self.invoke(launcher)
            (self.output / "eula.txt").write_text("eula=true\n")
            with self.assertRaisesRegex(RuntimeError, "Missing/changed required file"):
                self.invoke(launcher)
            java.assert_not_called()

    def test_changed_immutable_jar_refuses_after_normal_first_launch(self):
        self.assemble()
        launcher = self.launcher()
        self.launch(launcher)
        (self.output / "mods/catancraft.jar").write_bytes(b"wrong binary")
        with self.assertRaisesRegex(RuntimeError, "Missing/changed required file"):
            self.launch(launcher)

    def test_normal_properties_rewrite_allows_restart_with_escaped_world_value(self):
        policy = self.assemble()
        launcher = self.launcher()
        self.launch(launcher)
        values = dict(policy["serverProperties"], motd="A Minecraft Server", **{"max-world-size": "29999984"})
        values["level-name"] = "\\u0053ynthetic_World"
        (self.output / "server.properties").write_text(
            "#Minecraft server properties\n#A new timestamp\n" +
            "".join(f"{key}={value}\n" for key, value in sorted(values.items())))
        self.assertEqual(self.launch(launcher), 0)

    def test_changed_pinned_server_settings_refuse_after_first_launch(self):
        self.clean_r7()
        policy = self.assemble()
        launcher = self.launcher()
        self.launch(launcher)
        for key in policy["serverProperties"]:
            with self.subTest(key=key):
                changed = dict(policy["serverProperties"])
                changed[key] = "changed"
                (self.output / "server.properties").write_text(
                    "".join(f"{name}={value}\n" for name, value in changed.items()))
                with self.assertRaisesRegex(RuntimeError, "Changed required server property"):
                    self.launch(launcher)

    def test_duplicate_server_property_refuses_after_first_launch(self):
        self.assemble()
        launcher = self.launcher()
        self.launch(launcher)
        with (self.output / "server.properties").open("a") as stream:
            stream.write("server-ip=0.0.0.0\n")
        with self.assertRaisesRegex(RuntimeError, "duplicate server property"):
            self.launch(launcher)

    def test_non_java_line_separators_cannot_hide_required_settings(self):
        policy = self.assemble()
        launcher = self.launcher()
        self.launch(launcher)
        for separator in ("\v", "\f", "\x85", "\u2028", "\u2029"):
            with self.subTest(separator=repr(separator)):
                values = dict(policy["serverProperties"])
                values.pop("server-ip")
                (self.output / "server.properties").write_text(
                    "".join(f"{key}={value}\n" for key, value in values.items()) +
                    f"#comment{separator}server-ip=127.0.0.1\n")
                with self.assertRaisesRegex(RuntimeError, "separator/control"):
                    self.launch(launcher)

    def test_missing_world_authorization_on_first_launch_refuses(self):
        self.assemble()
        launcher = self.launcher()
        (self.output / "Synthetic_World/catancraft-fresh-demo.json").unlink()
        with self.assertRaisesRegex(RuntimeError, "exact transferred clean world"):
            self.launch(launcher)

    def test_authorization_map_digest_must_match_packaged_map(self):
        self.manifest["bootstrap"]["authorization"]["mapSha256"] = "f" * 64
        self.refuses()

    def test_existing_output_symlink_and_input_parent_destination_refuse(self):
        self.output.symlink_to(self.inputs, target_is_directory=True)
        with self.assertRaises(MODULE.InputError):
            self.assemble()
        self.output.unlink()
        self.output = self.inputs / "new-demo"
        self.refuses()


if __name__ == "__main__":
    unittest.main()
