"""Use read-only source material and private temporary fixtures. Never start a server."""
from pathlib import Path
import copy
import json
import shutil
import sys
import tempfile
import unittest

sys.dont_write_bytecode=True
import resume_plan as plan

WORK = Path(__file__).resolve().parents[3]
sys.path.insert(0,str(WORK/"inputs/CatanCraft_Clean_R7_Input"))
from nbt_check import NBT


def save(path, data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data))


class RecoveryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.src=WORK/"receipts/combined"
        cls.stage=plan.read(cls.src/"layout6-staging.json")
        cls.orig=Path(cls.stage["root"])
        cls.source=cls.stage["sourceCommit"]
        cls.original_report=cls.orig/plan.WORLD/"data/progression53-layout-6"

    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root=Path(self.temp.name)
        self.build=root/"build"
        self.receipts=root/"receipts"
        self.receipts.mkdir()
        self.run=self.build/"sample-layout-6"
        self.world=self.run/plan.WORLD
        self.data=self.world/"data"
        self.report=self.data/"progression53-layout-6"
        self.staging=copy.deepcopy(self.stage)
        self.staging["root"]=str(self.run)

    def seeded(self,verify=False):
        for path in [self.receipts/"layout6-staging.json",self.run/".catancraft-authored53-native.json"]:
            save(path,self.staging)
        for name in self.staging["immutableFiles"]:
            (self.run/name).parent.mkdir(parents=True,exist_ok=True)
            shutil.copyfile(self.orig/name,self.run/name)
        self.report.mkdir(parents=True)
        for name in ["seed.json","restart.dat"]:
            shutil.copyfile(self.original_report/name,self.report/name)
        histories=plan.read(self.report/"seed.json")["completedHistorySHA256"]
        for name in [*histories,"catancraft.dat"]:
            shutil.copyfile(self.orig/plan.WORLD/"data"/name,self.data/name)
        for phase in (["seed","verify"] if verify else ["seed"]):
            d=self.receipts/f"layout6-{phase}"
            shutil.copytree(self.src/d.name,d)
            p=plan.read(d/"process.json")
            p["command"]=["-PauthoredRunRoot="+str(self.run) if a.startswith("-PauthoredRunRoot=") else a for a in p["command"]]
            save(d/"process.json",p)
        if verify:
            shutil.copyfile(self.original_report/"verify.json",self.report/"verify.json")

    def action(self,stopped=True,changing=None,source=None):
        return plan.plan_layout(self.receipts,self.build,source or self.source,6,stopped,
                                changing or set(),lambda raw: NBT(raw).root())["action"]

    def test_completed_seed(self):
        self.seeded()
        self.assertEqual(self.action(),"verify_seed")

    def test_completed_pair(self):
        self.seeded(verify=True)
        self.assertEqual(self.action(),"retain_pair")

    def test_partial_verify_not_reopened(self):
        self.seeded()
        save(self.report/"verify.json",{"result":"RUNNING","checkpoints":[{}]*7})
        self.assertEqual(self.action(stopped=False),"confirm_writer_stopped")
        self.assertEqual(self.action(),"preserve_then_restage")

    def test_aborted_verify_before_report(self):
        self.seeded()
        save(self.receipts/"layout6-verify/process.json",{"status":"RUNNING"})
        self.assertEqual(self.action(),"preserve_then_restage")

    def test_bad_seal(self):
        self.seeded()
        f=self.report/"restart.dat"
        f.write_bytes(f.read_bytes()[:-2])
        self.assertEqual(self.action(),"preserve_then_restage")

    def test_changed_ledger(self):
        self.seeded()
        shutil.copyfile(self.orig/plan.WORLD/"level.dat",self.data/"catancraft.dat")
        self.assertEqual(self.action(),"preserve_then_restage")

    def test_changed_paid_history(self):
        self.seeded()
        name=next(iter(plan.read(self.report/"seed.json")["completedHistorySHA256"]))
        (self.data/name).write_bytes(b"changed")
        self.assertEqual(self.action(),"preserve_then_restage")

    def test_partial_seed(self):
        self.seeded()
        p=self.report/"seed.json"
        d=plan.read(p)
        d["result"]="RUNNING"
        d["checkpoints"]=d["checkpoints"][:12]
        save(p,d)
        self.assertEqual(self.action(),"preserve_then_restage")

    def test_unseen_and_in_progress(self):
        self.assertEqual(self.action(),"fresh_stage")
        self.assertEqual(self.action(changing={"layout6-seed"}),"wait")

    def test_different_source_or_bad_process_log(self):
        self.seeded(verify=True)
        self.assertEqual(self.action(source="a"*40),"preserve_then_restage")
        p=self.receipts/"layout6-seed/native.log"
        with p.open("ab") as file:
            file.write(b"unrecorded bytes")
        self.assertEqual(self.action(),"preserve_then_restage")

    def test_mismatched_verify_history(self):
        self.seeded(verify=True)
        p=self.report/"verify.json"
        d=plan.read(p)
        d["completedHistorySHA256"].popitem()
        save(p,d)
        self.assertEqual(self.action(),"preserve_then_restage")


if __name__=="__main__":
    unittest.main()
