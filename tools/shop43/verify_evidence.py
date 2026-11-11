#!/usr/bin/env python3
"""Verify downloaded Supply43 evidence without running builds, mods or worlds.

Run from the repository root:
  python3 tools/shop43/verify_evidence.py archive.zip source_commit archive_sha256 run artifact
Requires the retained validated042 expanded evidence. Only a completely verified
archive produces verification.json; an existing extraction must match its ZIP.
"""
from pathlib import Path, PurePosixPath
import hashlib, json, re, subprocess, sys, zipfile, gzip, struct, io, uuid

if not __debug__:
    raise SystemExit("Verification requires Python assertions (do not use -O).")
if len(sys.argv) != 6:
    raise SystemExit(__doc__)
archive = Path(sys.argv[1])
commit, artifact_sha = sys.argv[2:4]
run, artifact = map(int, sys.argv[4:6])
assert re.fullmatch(r"[0-9a-f]{40}", commit)
assert re.fullmatch(r"[0-9a-f]{64}", artifact_sha) and run > 0 and artifact > 0
sha = lambda value: hashlib.sha256(value).hexdigest()
assert sha(archive.read_bytes()) == artifact_sha
BASE = Path("build/stock42-evidence/run2/expanded")
BASE_SOURCE = "9b3eb646a494348632e4b5c6a692c57f9a9f2f16"
BASE_JAR_SHA = "af1a2def5eb3fc8db5088e21d41a8ceeb2bfa551f33b143413b022d287b50c80"


def relative(name):
    path = PurePosixPath(name)
    assert name and not path.is_absolute() and ".." not in path.parts and "\\" not in name, name
    assert path.as_posix() == name.rstrip("/"), name
    return path


root = archive.parent / "expanded"
assert not root.is_symlink()
fresh = not root.exists()
root.mkdir(exist_ok=True)
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
    names = z.namelist()
    assert len(names) == len(set(names)), "Duplicate archive paths"
    for entry in z.infolist():
        relative(entry.filename)
        assert (entry.external_attr >> 16) & 0o170000 != 0o120000
    if fresh:
        z.extractall(root)
    for path in root.rglob("*"):
        assert not path.is_symlink(), path
    actual = {p.relative_to(root).as_posix() for p in root.rglob("*") if p.is_file()}
    assert actual == {n for n in names if not n.endswith("/")}
    for name in actual:
        assert (root / name).read_bytes() == z.read(name), name

m = json.loads((root / "MANIFEST.json").read_text())
assert m["source"] == commit and m["source_errors"] == []
expected = {
    "catancraft_supply43": 8, "catancraft_claims43": 10,
    "claims_tacz": 2, "claims_tacz_seed": 1, "claims_tacz_verify": 1,
    "claims_sbw": 2, "claims_sbw_seed": 1, "claims_sbw_verify": 1,
    "catancraft_terrain43": 3, "catancraft_stock42": 8,
    "native_tacz": 8, "native_tacz_seed": 1, "native_tacz_verify": 1,
    "native_sbw": 8, "native_sbw_seed": 1, "native_sbw_verify": 1,
    "native_combined": 1, "catancraft_shop40": 35, "shop_seed": 1,
    "shop_verify": 1, "catancraft": 50, "catancraft_paid_recovery": 19,
    "catancraft_rewards41": 32, "rewards_seed": 1, "rewards_verify": 1,
    "catancraft_sbw": 7, "procurement_seed": 1, "procurement_verify": 1,
}
assert set(m["results"]) == set(expected)
assert sum(expected.values()) == m["expected_phase_executions"] == 207
assert m["expected_unique_scenarios"] == 200


def folder(label):
    for prefix in ("claims_tacz", "claims_sbw", "native_tacz", "native_sbw"):
        if label.startswith(prefix):
            return prefix
    if label.startswith("rewards_") or label == "catancraft_rewards41":
        return "rewards"
    if label.startswith("shop_"):
        return "shop_restart"
    if label.startswith("procurement_") or label == "catancraft_sbw":
        return "sbw_restart"
    return label


groups = {folder(label) for label in expected}
manifest_paths = set()
manifest_pairs = set()
for row in m["files"]:
    assert row["group"] in groups
    relative(row["saved_as"]); relative(row["file"])
    pair = (row["group"], row["file"])
    assert pair not in manifest_pairs
    manifest_pairs.add(pair)
    saved = row["saved_as"]
    expected_saved = ("shared/" + Path(row["file"]).name
                      if row["file"].endswith(".jar") or Path(row["file"]).name == "source.zip"
                      else row["group"] + "/" + row["file"])
    assert saved == expected_saved and sha((root / saved).read_bytes()) == row["sha256"], row
    manifest_paths.add(saved)
assert manifest_paths == actual - {"MANIFEST.json"}
logs = {}
for label, count in expected.items():
    ev = root / folder(label) / "supply43-evidence"
    log = (ev / (label + ".log")).read_text()
    logs[label] = log
    assert (ev / "SOURCE_COMMIT.txt").read_text().strip() == commit
    assert "BUILD SUCCESSFUL" in (ev / "build.log").read_text()
    assert re.findall(r"\b(\d+) GAME TESTS COMPLETE\b", log) == [str(count)], label
    assert f"All {count} required tests passed" in log
    assert (ev / (label + ".exit")).read_text().strip() == "0"
    assert (ev / (label + ".expected")).read_text().strip() == str(count)
    assert m["results"][label] == {"expected": count, "passed": True}
    marker = None
    if label.startswith(("native_", "claims_")) and label.endswith(("_seed", "_verify")):
        group, provider, phase = label.split("_")
        marker = f"NATIVE{'43' if group == 'claims' else '42'}_{provider.upper()}_RESTART_{phase.upper()}_PASS"
    elif label.startswith(("shop_", "rewards_", "procurement_")):
        group, phase = label.split("_")
        marker = {"shop": "SHOP40_RESTART_", "rewards": "REWARDS41_RESTART_", "procurement": "SEC01_PROCUREMENT35_"}[group] + phase.upper() + "_PASS"
    elif label == "native_combined":
        marker = "NATIVE42_COMBINED_PASS"
    if marker:
        assert log.count(marker) == 1, marker
assert m["all_passed"] is True
jars = list((root / "shared").glob("*.jar"))
assert len(jars) == 1
jar = jars[0]
jarsha = sha(jar.read_bytes())
assert jar.name == "catancraft-0.1.43-monument-supplies.jar", jar.name
with zipfile.ZipFile(jar) as z:
    assert z.testzip() is None and len(z.namelist()) == len(set(z.namelist()))
    assert f"CatanCraft-Source-Commit: {commit}" in z.read("META-INF/MANIFEST.MF").decode()
    assert "0.1.43-monument-supplies" in z.read("META-INF/mods.toml").decode()
    for name in ("ShopPurchase40", "ShopFacility40", "PhysicalWeaponShop40", "MonumentRewards41",
                 "WeaponTierCatalog41", "RewardTable41", "NativeWeapon42", "NativeTacz42", "NativeSbw42",
                 "WeaponStock42", "RewardClaim43", "NativeReward43", "MonumentSupply43", "TerrainConfig43Tests"):
        assert "com/quin/catancraft/world/" + name + ".class" in z.namelist()
for group in groups:
    ev = root / group / "supply43-evidence"
    assert (ev / "JAR_SHA256.txt").read_text().split()[0] == jarsha
    assert (group, "libs/" + jar.name) in manifest_pairs
    assert (group, "supply43-evidence/source.zip") in manifest_pairs
with zipfile.ZipFile(root / "shared/source.zip") as z:
    assert z.testzip() is None and len(z.namelist()) == len(set(z.namelist()))
    files = subprocess.check_output(["git", "ls-tree", "-r", "--name-only", commit], text=True).splitlines()
    assert set(files) == {n for n in z.namelist() if not n.endswith("/")}
    for name in files:
        assert z.read(name) == subprocess.check_output(["git", "show", commit + ":" + name]), name

# Every old entry must remain. Only explicitly reviewed source owners may have
# changed class bytes, and each such owner must actually differ in Git source.
old = BASE / "shared/catancraft-0.1.42-native-weapon-stock.jar"
assert sha(old.read_bytes()) == BASE_JAR_SHA
reviewed_owners = {"CatanCraft", "world/CombatTerrainSafety", "world/MonumentRewards41",
                  "world/NativeTacz42", "world/NativeWeapon42", "world/NativeWeapon42Tests",
                  "world/PhysicalWeaponShop40", "world/RewardTable41", "world/ShopNativePlayerFixture40"}
new_owners = {"world/MonumentSupply43", "world/MonumentSupply43Tests", "world/NativeReward43",
              "world/RewardClaim43", "world/RewardClaim43RestartTests", "world/RewardClaim43Tests",
              "world/TerrainConfig43Tests"}
changed_sources = set(subprocess.check_output(
    ["git", "diff", "--name-only", BASE_SOURCE, commit, "--", "src/main/java"], text=True).splitlines())
with zipfile.ZipFile(old) as a, zipfile.ZipFile(jar) as b:
    assert f"CatanCraft-Source-Commit: {BASE_SOURCE}" in a.read("META-INF/MANIFEST.MF").decode()
    old_entries = {n for n in a.namelist() if not n.endswith("/")}
    assert old_entries <= set(b.namelist()), "A preexisting JAR entry was removed"
    changed = sorted(n for n in old_entries if a.read(n) != b.read(n))
    for name in changed:
        if name in {"META-INF/MANIFEST.MF", "META-INF/mods.toml"}:
            continue
        assert name.startswith("com/quin/catancraft/") and name.endswith(".class"), name
        owner = name.removeprefix("com/quin/catancraft/").removesuffix(".class").split("$")[0]
        assert owner in reviewed_owners, name
        assert "src/main/java/com/quin/catancraft/" + owner + ".java" in changed_sources, name
    added = sorted(n for n in b.namelist() if not n.endswith("/") and n not in old_entries)
    copied_templates = {"data/" + namespace + "/structures/townhall_state_empty.nbt" for namespace in
                        ("catancraft_supply43", "catancraft_claims43", "catancraft_claims43_tacz",
                         "catancraft_claims43_sbw", "catancraft_native43_tacz_restart",
                         "catancraft_native43_sbw_restart", "catancraft_terrain43")}
    assert copied_templates <= set(added)
    for name in added:
        if name.startswith("com/quin/catancraft/") and name.endswith(".class"):
            owner = name.removeprefix("com/quin/catancraft/").removesuffix(".class").split("$")[0]
            assert owner in reviewed_owners | new_owners, name
            assert "src/main/java/com/quin/catancraft/" + owner + ".java" in changed_sources, name
        elif name in copied_templates:
            assert b.read(name) == a.read("data/catancraft/structures/townhall_state_empty.nbt"), name
        else:
            assert name in {"data/catancraft/claim43_fixtures/" + leaf for leaf in
                            ("stock42_provenance.json", "stock42_tacz.dat", "stock42_sbw.dat")}, name
    classes = sum(n.endswith(".class") and a.read(n) == b.read(n) for n in old_entries)

def native(data):
 stream=io.BytesIO(gzip.decompress(data))
 def num(fmt):return struct.unpack('>'+fmt,stream.read(struct.calcsize('>'+fmt)))[0]
 def string():return stream.read(num('H')).decode('utf-8')
 def value(t):
  if t in (1,2,3,4,5,6):return num({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t])
  if t==7:return stream.read(num('i'))
  if t==8:return string()
  if t==9:
   subtype=num('B');return [value(subtype) for _ in range(num('i'))]
  if t==10:
   result={}
   while True:
    kind=num('B')
    if kind==0:return result
    key=string();result[key]=value(kind)
  if t in (11,12):return [num('i' if t==11 else 'q') for _ in range(num('i'))]
  raise AssertionError(t)
 assert num('B')==10;string();result=value(10);assert stream.read()==b'';return result
def sealed(path):
 data=path if isinstance(path,bytes) else path.read_bytes();magic,size=struct.unpack('>II',data[:8]);assert magic==0x43505231 and len(data)==40+size
 assert hashlib.sha256(data[40:]).digest()==data[8:40]
 return native(data[40:])
def uuidstr(a):return str(uuid.UUID(bytes=struct.pack('>4i',*a)))
def receipts(n):return n.get('ForgeData',{}).get('PlayerPersisted',{}).get('CatanCraftShop40',{})
restart=root/'shop_restart/supply43-evidence/restart'
seedpath=restart/'catancraft_shop40_restart_seed.proof';verifiedpath=restart/'catancraft_shop40_restart_verified.proof'
seed=sealed(seedpath);verified=sealed(verifiedpath)
assert seed['source']==verified['source']==commit and verified['result']=='PASS'
assert seed['process']==verified['seedProcess'] and seed['process']!=verified['process']
assert verified['seedProofSHA256']==sha(seedpath.read_bytes())
assert len(seed['actors'])==len(verified['actors'])==2
for i,(a,b) in enumerate(zip(seed['actors'],verified['actors'])):
 op=uuidstr(a['operation']);actor=uuidstr(a['actor']);intent=sealed(a['originalIntentBytes'])
 assert a['originalIntentBytes']==(restart/a['intentFile']).read_bytes()
 assert sha(a['originalIntentBytes'])==a['intentSHA256']==a['debitReceipt']
 assert intent['price']==a['price'] and intent['item']==a['item']
 assert sha(a['originalPlayerBytes'])==a['originalPlayerSHA256']
 assert sha(a['originalProgressBytes'])==a['originalProgressSHA256']
 assert sha(a['nativePlayerBeforeConsumptionBytes'])==a['nativePlayerBeforeConsumptionSHA256']
 assert a['progressPhase']==('ABSENT' if i==0 else 'DELIVERING')
 if i==0:assert a['originalProgressBytes']==b''
 else:assert sealed(a['originalProgressBytes'])['phase']=='DELIVERING'
 assert a['nativeCountAtRestart']==0 and a['nativeCountBeforeConsumption']==i
 assert a['deliveryReceipt']==('' if i==0 else a['intentSHA256'])
 assert b['nativeCountAfterRecovery']==b['newItemsAfterRecovery']==1-i
 assert b['additionalDebit']==b['completedRetrySaves']==0 and b['completedRetries']==3
 assert b['nationAfterRecovery']==a['nationAfterDebit']
 completion=restart/a['progressFile'];assert sha(completion.read_bytes())==b['completionSHA256']
 assert sealed(completion)['phase']=='COMMITTED' and sealed(completion)['intentSHA256']==a['intentSHA256']
 current=restart/(actor+'.dat');assert sha(current.read_bytes())==b['nativePlayerAfterRecoverySHA256']
 assert receipts(native(current.read_bytes()))[op]==a['intentSHA256']
 assert receipts(native(a['originalPlayerBytes'])).get(op,'')==a['deliveryReceipt']
assert sha((restart/'catancraft.dat').read_bytes())==verified['nativeLedgerAfterRecoverySHA256']
rewardroot=root/'rewards/supply43-evidence/rewards-restart'
rseedpath=rewardroot/'catancraft_rewards41_restart_seed.proof'
rverifypath=rewardroot/'catancraft_rewards41_restart_verified.proof'
rs=sealed(rseedpath);rv=sealed(rverifypath)
assert rs['source']==rv['source']==commit and rv['result']=='PASS'
assert rv['seedProcess']==rs['process'] and rv['process']!=rs['process']
assert rv['seedProofSHA256']==sha(rseedpath.read_bytes())
assert sha(rs['preparedNativeLedgerBytes'])==rs['preparedNativeLedgerSHA256']
assert sha(rs['originalNativeLedgerBytes'])==rs['originalNativeLedgerSHA256']
assert native(rs['originalNativeLedgerBytes'])['data']==rs['capturedNativeLedger']
assert rs['preparedActivation']['phase']=='OPEN' and 'winner' not in rs['preparedActivation']
assert rs['capturedActivation']['phase']=='CAPTURED' and rv['settledActivation']['phase']=='SETTLED'
assert rs['capturedActivation']['winner']==rv['settledActivation']['winner']==rs['nation']
for key in ('id','identity','monument','tableRevision','tableSHA256','entry','roll'):
 assert rs['preparedActivation'][key]==rs['capturedActivation'][key]==rv['settledActivation'][key]
intent_expected={k:v for k,v in rs['capturedActivation'].items() if k!='phase'}
assert rv['intentBytes']==(rewardroot/rv['intentFile']).read_bytes()
assert rv['receiptBytes']==(rewardroot/rv['receiptFile']).read_bytes()
assert sha(rv['intentBytes'])==rv['intentSHA256'] and sealed(rv['intentBytes'])==intent_expected
rc=sealed(rv['receiptBytes']);assert sha(rv['receiptBytes'])==rv['receiptSHA256']
assert rc=={'schema':1,'id':rs['activation'],'phase':'COMMITTED','intentSHA256':rv['intentSHA256']}
assert sha((rewardroot/'catancraft.dat').read_bytes())==rv['settledNativeLedgerSHA256']
assert native((rewardroot/'catancraft.dat').read_bytes())['data']==rv['settledNativeLedger']
assert rv['settledNativeLedger']['placedMapAssets']['monument41_paid:'+uuidstr(rs['activation'])]==rv['intentSHA256']
assert rv['pendingEntitlements']==3 and rv['spawnedEquipment']==rv['completedRetrySaves']==0 and rv['completedRetries']==3
assert len(rs['expectedReward']['entitlements'])==3 and rs['expectedReward']==intent_expected['roll']
assert rv['nationAfterRecovery']['treasury']==rs['nationBefore']['treasury']+rs['expectedReward']['money']
for resource,amount in rs['expectedReward']['resources'].items():
 assert rv['nationAfterRecovery']['stockpile'][resource.lower()]==rs['nationBefore']['stockpile'][resource.lower()]+amount
native_proofs={}
for provider in ('tacz','sbw'):
 nr=root/('native_'+provider)/'supply43-evidence/native-restart'
 seedfile=nr/('catancraft_native42_'+provider+'_restart_seed.proof')
 donefile=nr/('catancraft_native42_'+provider+'_restart_verified.proof')
 ns=sealed(seedfile);nv=sealed(donefile)
 assert ns['source']==nv['source']==commit and nv['result']=='PASS'
 assert ns['process']==nv['seedProcess'] and ns['process']!=nv['process']
 assert nv['seedProofSHA256']==sha(seedfile.read_bytes())
 op=uuidstr(ns['operation']);actor=uuidstr(ns['actor'])
 original=sealed(ns['originalIntentBytes']);progress=sealed(ns['originalProgressBytes'])
 assert sha(ns['originalIntentBytes'])==ns['intentSHA256']==ns['debitReceipt']==ns['deliveryReceipt']==nv['intentSHA256']
 assert sha(ns['originalProgressBytes'])==ns['progressSHA256'] and progress['phase']=='DELIVERING'
 intentfile=nr/('catancraft_shop40_'+actor+'_'+op+'.dat')
 assert intentfile.read_bytes()==ns['originalIntentBytes']
 assert original['item']==ns['item'] and original['catalog'].endswith('@'+ns['fingerprint'])
 assert sha(ns['originalPlayerBytes'])==ns['playerSHA256']
 assert (nr/(actor+'.dat')).read_bytes()==ns['originalPlayerBytes']
 assert receipts(native(ns['originalPlayerBytes']))[op]==ns['intentSHA256']
 assert sha(ns['originalLedgerBytes'])==ns['ledgerSHA256'] and native(ns['originalLedgerBytes'])['data']==ns['nativeLedger']
 assert native((nr/'catancraft.dat').read_bytes())['data']==ns['nativeLedger']
 completion=sealed(nr/(intentfile.name+'.receipt'))
 assert completion['phase']=='COMMITTED' and completion['intentSHA256']==ns['intentSHA256'] and completion['id']==ns['operation']
 assert nv['additionalDebit']==nv['newItemsAfterRecovery']==0 and nv['nativeItemCount']==1 and nv['completedRetries']==3
 assert nv['firstRecoveryLedgerSaves']==1 and nv['firstRecoveryPlayerSaves']==nv['completedRetryLedgerSaves']==nv['completedRetryPlayerSaves']==0
 if provider=='sbw':assert ns['nativeGunUUID']==nv['nativeGunUUID']
 native_proofs[provider]={'source':commit,'seed_process':ns['process'],'verify_process':nv['process'],'item_id':ns['item']['id'],'fingerprint':ns['fingerprint'],'native_uuid':uuidstr(ns['nativeGunUUID']) if provider=='sbw' else None,'first_recovery_ledger_saves':1,'completed_retry_saves':0}

# Compare every runtime manifest/provenance byte to the hash-pinned 042 records.
# The combined runtime retains both published manifests; its init selects exactly
# the newer reviewed SimpleBedrockModel, leaving both original JARs untouched.
runtime_proofs = {}
runtime_specs = {
    "tacz": ("tacz-", "1f3a4c5b31e1c0893f30f333a602b4334a82ba3c25ea23784ab16257b4080891",
             7, 1, 24, "shop42-tacz-runtime", {"claims_tacz", "native_tacz", "native_combined", "catancraft_terrain43"}),
    "sbw": ("", "6e4424ef5a564609dc893c5d9d1ff3efcc7b897d8b9ae9a9acf96d7c331c9452",
            9, 3, 31, "sec01-035-runtime", {"claims_sbw", "native_sbw", "native_combined", "catancraft_terrain43", "sbw_restart"}),
}
for provider, (prefix, digest, count, inputs, file_count, directory, suites) in runtime_specs.items():
    retained = BASE / ("native_" + provider) / "stock42-evidence"
    manifest_bytes = (retained / (prefix + "runtime-artifacts.json")).read_bytes()
    assert sha(manifest_bytes) == digest
    manifest = json.loads(manifest_bytes)
    provenance = {"schema": manifest["schema"], "verified_public_modrinth_inputs": manifest["inputs"]}
    assert len(manifest["artifacts"]) == count and len(manifest["inputs"]) == inputs
    assert len({row["coordinate"] for row in manifest["artifacts"]}) == count
    assert manifest["minecraft"] == "1.20.1" and manifest["forge"] == "47.4.10"
    for group in suites:
        ev = root / group / "supply43-evidence"
        assert (ev / (prefix + "runtime-artifacts.json")).read_bytes() == manifest_bytes
        assert json.loads((ev / (prefix + "download-provenance.json")).read_text()) == provenance
        for phase in ("prepare", "verify"):
            result = json.loads((ev / (prefix + "runtime-" + phase + ".json")).read_text())
            assert result["result"] == "PASS" and result["manifest_sha256"] == digest
            assert result["runtime_artifact_count"] == count and result["published_input_count"] == inputs
            assert result["verified_file_count"] == file_count and result["no_native_tests_executed"] is True
            assert Path(result["root"]).parts[-2:] == ("build", directory)
    runtime_proofs[provider] = {"manifest_sha256": digest, "artifacts": count, "published_inputs": inputs,
                                "verified_groups": sorted(suites)}

# The compatibility fixture is the actual 042 purchase intent, not a newly
# quoted substitute. Compare the JAR/source resource to the retained old proof.
fixture_proofs = {}
with zipfile.ZipFile(jar) as built, zipfile.ZipFile(root / "shared/source.zip") as source_zip:
    prefix = "data/catancraft/claim43_fixtures/"
    provenance_bytes = built.read(prefix + "stock42_provenance.json")
    assert provenance_bytes == source_zip.read("src/main/resources/" + prefix + "stock42_provenance.json")
    provenance = json.loads(provenance_bytes)
    assert provenance["source"] == BASE_SOURCE and provenance["run"] == 42914749967
    assert provenance["artifact"] == 13807659810
    assert provenance["archive_sha256"] == "df07f5c41980cbcac7143e039413fd064748d67e5334557d9554713dc4459a2e"
    assert set(provenance["records"]) == {"tacz", "sbw"}
    for provider, expected_hash, expected_size in (
        ("tacz", "43a980b0d707a1b95a52457c953ed70c87d8f45e09f43106c0667ea590000d9a", 556),
        ("sbw", "517663b5a9fc7c0a9e81e0d24334562292d2f3a95d4bafc429e9423121c02f6d", 550),
    ):
        name = "stock42_" + provider + ".dat"
        row = provenance["records"][provider]
        assert row == {"file": name, "sha256": expected_hash, "bytes": expected_size}
        fixture = built.read(prefix + name)
        assert fixture == source_zip.read("src/main/resources/" + prefix + name)
        assert len(fixture) == expected_size and sha(fixture) == expected_hash
        archived = BASE / ("native_" + provider) / "stock42-evidence/native-restart"
        old_seed = sealed(archived / ("catancraft_native42_" + provider + "_restart_seed.proof"))
        assert old_seed["source"] == BASE_SOURCE and fixture == old_seed["originalIntentBytes"]
        old_intent = sealed(fixture)
        assert old_intent["item"] == old_seed["item"]
        assert old_intent["catalog"].endswith("@" + old_seed["fingerprint"])
        marker = f"CLAIMS43_{provider.upper()}_STOCK42_FROZEN_SUPPLY_PASS"
        matching = [line for line in logs["claims_" + provider].splitlines() if marker in line]
        assert len(matching) == 1
        for field in ("source=" + BASE_SOURCE, "intentSHA256=" + expected_hash,
                      "originalFingerprint=PASS", "originalNativeItem=PASS", "noRequote=PASS"):
            assert field in matching[0], field
        fixture_proofs[provider] = {"sha256": expected_hash, "bytes": expected_size,
                                   "original_fingerprint": old_seed["fingerprint"]}

# The three JSON snapshots contain the actual native getter values. GameTest
# batches need not run in phase-name order. Each edit scenario records its own
# before/edit/restore reload counters and bytes; never infer cross-test chronology.
terrain_root = root / "catancraft_terrain43/supply43-evidence/terrain-config"
assert {p.name for p in terrain_root.iterdir()} == {
    "catancraft_terrain43_" + phase + ".json" for phase in ("loaded", "setters", "watcher")}
terrain_proofs = {}
expected_guards = {
    "tacz": {"ammo.DestroyGlass", "ammo.IgniteBlock", "ammo.ExplosiveAmmoDestroysBlock"},
    "superbwarfare": {"vehicle.collision.collision_destroy_soft_blocks",
                      "vehicle.collision.collision_destroy_normal_blocks",
                      "vehicle.collision.collision_destroy_hard_blocks",
                      "vehicle.collision.collision_destroy_blocks_beastly",
                      "projectile.allow_projectile_destroy_blocks",
                      "explosion.explosion_destroy", "explosion.extra_explosion_effect"},
}
for phase in ("loaded", "setters", "watcher"):
    proof = json.loads((terrain_root / ("catancraft_terrain43_" + phase + ".json")).read_text())
    assert proof["schema"] == "catancraft-terrain43-native-config-v2" and proof["phase"] == phase
    assert proof["flags_false"] == 10 and set(proof["providers"]) == set(expected_guards)
    for provider, guard_names in expected_guards.items():
        row = proof["providers"][provider]
        assert set(row["effective_guards"]) == guard_names, (provider, row["effective_guards"])
        assert all(value is False for value in row["effective_guards"].values())
        assert re.fullmatch(r"[0-9a-f]{64}", row["authored_sha256"])
        assert row["restored_sha256"] == row["authored_sha256"]
        assert row["file"] == ("tacz-common.toml" if provider == "tacz" else "superbwarfare-server.toml")
        assert type(row["observed_reloads"]) is int and row["observed_reloads"] >= 0
        edit_fields = {"reloads_before_edit", "reloads_after_edit", "reloads_after_restore", "edited_sha256",
                       "ordinary_path", "ordinary_before", "ordinary_edited", "ordinary_restored"}
        if phase != "loaded":
            assert edit_fields <= set(row)
            assert all(type(row[key]) is int for key in ("reloads_before_edit", "reloads_after_edit", "reloads_after_restore"))
            assert 0 <= row["reloads_before_edit"] < row["reloads_after_edit"] < row["reloads_after_restore"] <= row["observed_reloads"]
            assert re.fullmatch(r"[0-9a-f]{64}", row["edited_sha256"])
            assert row["edited_sha256"] != row["authored_sha256"]
            assert isinstance(row["ordinary_path"], str) and row["ordinary_path"] and row["ordinary_path"] not in guard_names
            assert all(type(row[key]) is bool for key in ("ordinary_before", "ordinary_edited", "ordinary_restored"))
            assert row["ordinary_before"] == row["ordinary_restored"]
            assert row["ordinary_edited"] is not row["ordinary_before"]
        else:
            assert not edit_fields.intersection(row)
        if phase == "watcher":
            assert set(row["authored_guards_after_edit"]) == guard_names
            assert all(value is True for value in row["authored_guards_after_edit"].values())
        else:
            assert "authored_guards_after_edit" not in row
    terrain_proofs[phase] = proof
for path in root.rglob("*.log"):
    log = path.read_text(errors="replace")
    assert not re.search(r"ConfigLoadingException|ParsingException|Exception[^\n]*FileWatcher", log), path


def claim_receipts(player):
    return player.get("ForgeData", {}).get("PlayerPersisted", {}).get("CatanCraftReward43", {})


claim_proofs = {}
for provider, native_provider, actor_suffix in (("tacz", "tacz", "a"), ("sbw", "superbwarfare", "b")):
    evidence = root / ("claims_" + provider) / "supply43-evidence/claim-restart"
    namespace = "catancraft_native43_" + provider + "_restart"
    seedfile = evidence / (namespace + "_seed.proof")
    donefile = evidence / (namespace + "_verified.proof")
    cs, cv = sealed(seedfile), sealed(donefile)
    for key in ("schema", "namespace", "provider", "root", "seed", "dimension", "source"):
        assert cs[key] == cv[key], key
    assert cs["schema"] == 1 and cs["namespace"] == namespace and cs["provider"] == native_provider
    assert cs["source"] == commit and cs["seed"] == 0 and cs["dimension"] == "minecraft:overworld"
    assert Path(cs["root"]).parts[-3:] == ("build", "controlled-" + namespace + "-run", "world")
    assert cs["result"] == "SEEDED_PENDING_DISTINCT_JVM_VERIFICATION" and cv["result"] == "PASS"
    assert re.fullmatch(r"\d+:\d+", cs["process"]) and re.fullmatch(r"\d+:\d+", cv["process"])
    assert cs["process"] == cv["seedProcess"] and cs["process"] != cv["process"]
    assert cv["seedProofSHA256"] == sha(seedfile.read_bytes())
    actor = uuidstr(cs["actor"]); reward = uuidstr(cs["reward"]); claim = uuidstr(cs["claim"])
    assert actor == "43000000-0000-0000-0000-0000000000" + actor_suffix + "1"
    assert reward == "43000000-0000-0000-0000-0000000000" + actor_suffix + "2"
    assert cs["name"] == ("CCClaim43TaCZ" if provider == "tacz" else "CCClaim43SBW")
    assert cs["index"] == 0
    computed_claim = uuid.UUID(bytes=hashlib.md5(("CatanCraftReward43:" + reward + ":0").encode()).digest(), version=3)
    assert str(computed_claim) == claim
    for key in ("sourceIntent", "sourceReceipt", "claimIntent", "claimProgress", "nativePlayer", "nativeLedger"):
        assert sha(cs[key + "Bytes"]) == cs[key + "SHA256"], key
    source_intent = sealed(cs["sourceIntentBytes"])
    source_receipt = sealed(cs["sourceReceiptBytes"])
    claim_intent = sealed(cs["claimIntentBytes"])
    claim_progress = sealed(cs["claimProgressBytes"])
    source_path = evidence / ("catancraft_reward41_" + reward + ".dat")
    claim_path = evidence / ("catancraft_reward43_" + reward + "_0.dat")
    assert source_path.read_bytes() == cs["sourceIntentBytes"]
    assert source_path.with_name(source_path.name + ".receipt").read_bytes() == cs["sourceReceiptBytes"]
    assert claim_path.read_bytes() == cs["claimIntentBytes"]
    assert source_intent["id"] == cs["reward"] and source_intent["winner"] == cs["nation"]
    assert source_receipt == {"schema": 1, "id": cs["reward"], "phase": "COMMITTED", "intentSHA256": cs["sourceIntentSHA256"]}
    assert claim_progress == {"schema": 1, "id": cs["claim"], "phase": "DELIVERING", "intentSHA256": cs["claimIntentSHA256"]}
    completion = sealed(claim_path.with_name(claim_path.name + ".receipt"))
    assert completion == {**claim_progress, "phase": "COMMITTED"}
    assert set(claim_intent) == {"schema", "id", "reward", "index", "identity", "sourceSHA256", "entitlement", "facility", "item", "fingerprint", "slot"}
    assert claim_intent["schema"] == 1 and claim_intent["id"] == cs["claim"]
    assert claim_intent["reward"] == cs["reward"] and claim_intent["index"] == 0
    assert claim_intent["sourceSHA256"] == cs["sourceIntentSHA256"]
    identity = claim_intent["identity"]
    assert identity["actor"] == cs["actor"] and identity["nation"] == cs["nation"]
    assert identity["root"] == cs["root"] and identity["seed"] == cs["seed"]
    assert identity["dimension"] == cs["dimension"] and identity["scope"] == "controlled_fixture"
    assert claim_intent["facility"] and claim_intent["slot"] == 0
    assert claim_intent["entitlement"] == cs["entitlement"] == source_intent["roll"]["entitlements"][0]
    assert len(source_intent["roll"]["entitlements"]) == 1
    assert cs["entitlement"] == {"kind": "GUN", "id": "tacz:glock_17" if provider == "tacz" else "superbwarfare:ak_47", "count": 1, "revision": "monument_supply43_native_v1"}
    assert claim_intent["item"] == cs["item"] and cs["item"]["Count"] == 1
    assert claim_intent["fingerprint"] == cs["fingerprint"] and re.fullmatch(r"[0-9a-f]{64}", cs["fingerprint"])
    assert cs["playerReceipt"] == cs["claimIntentSHA256"] == cv["claimIntentSHA256"]
    player_bytes = (evidence / (actor + ".dat")).read_bytes()
    assert player_bytes == cs["nativePlayerBytes"]
    player = native(player_bytes)
    assert player["UUID"] == cs["actor"]
    assert claim_receipts(player) == {claim: cs["claimIntentSHA256"]} and receipts(player) == {}
    inventory = player["Inventory"]
    assert inventory == [{**cs["item"], "Slot": 0}], inventory
    ledger = native(cs["nativeLedgerBytes"])["data"]
    assert ledger == cs["nativeLedger"] == native((evidence / "catancraft.dat").read_bytes())["data"]
    assert ledger["nations"] == [cs["nationAfterSourceCredit"]]
    nation = cs["nationAfterSourceCredit"]
    assert nation["id"] == cs["nation"] and nation["leaderId"] == cs["actor"]
    assert nation["members"] == [{"id": cs["actor"]}]
    assert nation["treasury"] == 10500 and source_intent["roll"]["money"] == 500
    assert source_intent["roll"]["resources"] == {"WOOD": 7}
    assert nation["stockpile"]["wood"] == 107
    assert all(value == (107 if key == "wood" else 100) for key, value in nation["stockpile"].items())
    assert ledger["placedMapAssets"]["monument41_paid:" + reward] == cs["sourceIntentSHA256"]
    monuments = [row for row in ledger["monuments"] if row["id"] == cs["monument"]]
    assert len(monuments) == 1
    activation = monuments[0]["rewardActivation41"]
    assert activation["phase"] == "SETTLED"
    assert {key: value for key, value in activation.items() if key != "phase"} == source_intent
    assert not any(key.startswith("shop40_paid:") for key in ledger["placedMapAssets"])
    for key in ("additionalCharge", "additionalSourceCredit", "newItemsAfterRecovery", "firstRecoveryPlayerSaves", "firstRecoveryLedgerSaves", "completedRetrySaves"):
        assert cv[key] == 0, key
    assert cv["nativeItemCount"] == 1 and cv["completedRetries"] == 3
    if provider == "sbw":
        assert cs["nativeGunUUID"] == cv["nativeGunUUID"] == cs["item"]["tag"]["GunData"]["UUID"]
        assert cs["item"]["id"] == "superbwarfare:ak_47"
        assert cs["item"]["tag"] == {"Perks": {}, "Attachments": {}, "GunData": {"UUID": cs["nativeGunUUID"]}}
    else:
        assert cs["item"]["id"] == "tacz:modern_kinetic_gun" and cs["item"]["tag"]["GunId"] == "tacz:glock_17"
        assert cs["item"]["tag"] == {"GunId": "tacz:glock_17", "GunFireMode": "SEMI", "GunCurrentAmmoCount": 0, "HasBulletInBarrel": 0}
    claim_proofs[provider] = {"source": commit, "actor": actor, "winner": uuidstr(cs["nation"]),
                              "reward": reward, "claim": claim, "source_intent_sha256": cs["sourceIntentSHA256"],
                              "claim_intent_sha256": cs["claimIntentSHA256"], "fingerprint": cs["fingerprint"],
                              "seed_process": cs["process"], "verify_process": cv["process"],
                              "native_uuid": uuidstr(cs["nativeGunUUID"]) if provider == "sbw" else None,
                              "additional_charge": 0, "additional_credit": 0, "new_items": 0, "recovery_saves": 0}

record = {
    "source": commit, "run": run, "workflow_url": f"https://github.com/quintaneous/catanCraft/actions/runs/{run}",
    "artifact_id": artifact, "archive_sha256": artifact_sha, "unique_scenarios": 200,
    "phase_executions": 207, "passed_phase_executions": sum(expected.values()), "failed": 0,
    "results": m["results"], "jar_file": jar.name, "jar_bytes": jar.stat().st_size, "jar_sha256": jarsha,
    "source_files_byte_compared": len(files), "all_manifest_files_verified": len(m["files"]),
    "baseline_042_jar_sha256": BASE_JAR_SHA, "identical_preexisting_classes_vs_042": classes,
    "changed_preexisting_jar_entries_vs_042": changed, "native_weapon_restart_proofs": native_proofs,
    "added_jar_entries_vs_042": added,
    "native_claim_restart_proofs": claim_proofs, "stock42_compatibility_fixtures": fixture_proofs,
    "published_runtime_proofs": runtime_proofs, "terrain_config_proofs": terrain_proofs,
    "restart_proof_verified": True, "weighted_reward_component_restart_verified": True,
    "reward_seed_process": rs["process"], "reward_verify_process": rv["process"],
    "seed_process": seed["process"], "verify_process": verified["process"],
    "scope": "Controlled native monument equipment claims and separate-JVM recovery; ten native terrain getters and real FileWatcher reloads; all prior 178 phases retained. No owner installed-pack, authenticated client, combat balance or owner acceptance.",
}
(archive.parent / "verification.json").write_text(json.dumps(record, indent=2) + "\n")
print(json.dumps(record, indent=2))
