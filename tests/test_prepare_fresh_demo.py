"""Focused standard-library tests: no Minecraft process, real worlds or network."""
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from io import BytesIO
import zipfile

SPEC = importlib.util.spec_from_file_location("prepare_fresh_demo", Path(__file__).resolve().parents[1] / "tools/prepare_fresh_demo.py")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)

def archive(files):
    stream = BytesIO()
    with zipfile.ZipFile(stream, "w", compression=zipfile.ZIP_DEFLATED) as z:
        for name, data in sorted(files.items()):
            info = zipfile.ZipInfo(name, date_time=(2026, 10, 18, 0, 0, 0))
            z.writestr(info, data)
    return stream.getvalue()

def metadata(version="0.1.28", minecraft="[1.20.1,1.20.2)"):
    return f'''modLoader="javafml"
loaderVersion="[47,)"
[[mods]]
modId="catancraft"
version="{version}"
[[dependencies.catancraft]]
modId="forge"
versionRange="[47.4.10,)"
[[dependencies.catancraft]]
modId="minecraft"
versionRange="{minecraft}"
'''.encode()

class PrepareFreshDemoTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.root = self.base / "CatanCraft-Dev"
        self.assets = self.root / "handoff/assets"
        self.assets.mkdir(parents=True)
        self.pins = {}
        for name in MODULE.ASSET_PINS:
            content = archive({"fixture.schem": b"synthetic test bytes only"})
            (self.assets / name).write_bytes(content)
            self.pins[name] = MODULE.sha256(content)
        self.jar = self.base / "built.jar"
        self.jar.write_bytes(archive({"META-INF/mods.toml": metadata()}))
        self.digest = MODULE.sha256(self.jar.read_bytes())
        self.patch = patch.object(MODULE, "ASSET_PINS", self.pins)
        self.patch.start()
        self.addCleanup(self.patch.stop)

    def prepare(self, name="components", **kwargs):
        return MODULE.prepare(kwargs.get("root", self.root), kwargs.get("jar", self.jar), kwargs.get("digest", self.digest), self.base / name)

    def refused_without_output(self, **kwargs):
        with self.assertRaises((MODULE.InputError, OSError)):
            self.prepare(**kwargs)
        self.assertFalse((self.base / "components").exists())

    def test_deterministic_manifest_and_exact_component_copy(self):
        first = self.prepare("one")
        self.prepare("two")
        self.assertEqual((self.base / "one/MANIFEST.json").read_bytes(), (self.base / "two/MANIFEST.json").read_bytes())
        self.assertEqual(first["status"], "NOT_READY")
        self.assertFalse(first["runnable"])
        self.assertFalse(first["worldIncluded"])
        self.assertEqual(len(first["components"]), 3)
        self.assertEqual((self.base / "one/artifacts/catancraft.jar").read_bytes(), self.jar.read_bytes())
        manifest = (self.base / "one/MANIFEST.json").read_bytes()
        self.assertEqual((self.base / "one/MANIFEST.sha256").read_text(), f"{MODULE.sha256(manifest)}  MANIFEST.json\n")
        for row in first["components"]:
            self.assertEqual(MODULE.sha256((self.base / "one" / row["path"]).read_bytes()), row["sha256"])

    def test_bad_jar_checksum_refuses_before_output(self):
        self.refused_without_output(digest="0" * 64)

    def test_invalid_digest_refuses_before_output(self):
        self.refused_without_output(digest="not a digest")

    def test_changed_original_asset_refuses_before_output(self):
        (self.assets / "schematics_bundle.zip").write_bytes(b"changed")
        self.refused_without_output()

    def test_missing_expected_asset_refuses(self):
        (self.assets / "R5_READ_ONLY_PROBE_INDEX.zip").unlink()
        self.refused_without_output()

    def test_existing_output_is_preserved(self):
        output = self.base / "components"
        output.mkdir()
        (output / "keep").write_bytes(b"existing user work")
        with self.assertRaises(MODULE.InputError):
            self.prepare()
        self.assertEqual((output / "keep").read_bytes(), b"existing user work")
        self.assertEqual(list(output.iterdir()), [output / "keep"])

    def test_output_symlink_refuses(self):
        target = self.base / "target"
        target.mkdir()
        (self.base / "components").symlink_to(target, target_is_directory=True)
        with self.assertRaises(MODULE.InputError):
            self.prepare()
        self.assertEqual(list(target.iterdir()), [])

    def test_symlink_parent_refuses(self):
        target = self.base / "target"
        target.mkdir()
        (self.base / "linked").symlink_to(target, target_is_directory=True)
        with self.assertRaises(MODULE.InputError):
            MODULE.prepare(self.root, self.jar, self.digest, self.base / "linked/components")
        self.assertEqual(list(target.iterdir()), [])

    def test_symlink_source_asset_refuses(self):
        source = self.assets / "schematics_bundle.zip"
        saved = self.base / "asset.zip"
        source.rename(saved)
        source.symlink_to(saved)
        self.refused_without_output()

    def test_symlink_handoff_root_refuses(self):
        linked = self.base / "linked"
        linked.symlink_to(self.root, target_is_directory=True)
        self.refused_without_output(root=linked)

    def test_symlink_jar_refuses(self):
        linked = self.base / "linked.jar"
        linked.symlink_to(self.jar)
        self.refused_without_output(jar=linked)

    def test_installed_mods_path_refuses(self):
        mods = self.base / "mods"
        mods.mkdir()
        installed = mods / "catancraft.jar"
        installed.write_bytes(self.jar.read_bytes())
        self.refused_without_output(jar=installed)

    def test_missing_jar_metadata_refuses(self):
        self.jar.write_bytes(archive({"other": b"not a CatanCraft build"}))
        self.refused_without_output(digest=MODULE.sha256(self.jar.read_bytes()))

    def test_incompatible_minecraft_refuses(self):
        self.jar.write_bytes(archive({"META-INF/mods.toml": metadata(minecraft="[1.21,1.22)")}))
        self.refused_without_output(digest=MODULE.sha256(self.jar.read_bytes()))

    def test_unexpanded_build_version_refuses(self):
        self.jar.write_bytes(archive({"META-INF/mods.toml": metadata(version="${version}")}))
        self.refused_without_output(digest=MODULE.sha256(self.jar.read_bytes()))

    def test_archive_path_traversal_refuses_without_extraction(self):
        content = archive({"META-INF/mods.toml": metadata(), "../escape": b"bad"})
        self.jar.write_bytes(content)
        self.refused_without_output(digest=MODULE.sha256(content))
        self.assertFalse((self.base / "escape").exists())

    def test_unrelated_world_mod_and_art_files_are_never_staged(self):
        (self.root / "level.dat").write_bytes(b"do not inspect or copy")
        (self.root / "secret-mod.jar").write_bytes(b"unapproved")
        (self.root / "candidate.blend").write_bytes(b"unapproved")
        result = self.prepare()
        names = {p.name for p in (self.base / "components").rglob("*") if p.is_file()}
        self.assertNotIn("level.dat", names)
        self.assertNotIn("secret-mod.jar", names)
        self.assertNotIn("candidate.blend", names)
        self.assertEqual({m["id"] for m in result["missingPrerequisites"]}, {"clean_authored_world", "approved_sector_wall_art", "matching_launcher_and_modpack", "sbw_runtime_verification", "fresh_initialization_contract"})
        self.assertFalse(any("AUTHORIZ" in name for name in names))

if __name__ == "__main__":
    unittest.main()
