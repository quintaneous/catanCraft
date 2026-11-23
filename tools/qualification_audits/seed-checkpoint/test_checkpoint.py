"""Synthetic tiny campaign-shaped fixtures. No real game saves are used."""
import fcntl
import gzip
import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from uuid import uuid4

sys.dont_write_bytecode=True
import checkpoint as cp
from resume_plan import CITIES, PLOTS, INDUSTRIES
HERE=Path(__file__).resolve().parent
WORK=HERE.parents[2]
sys.path.insert(0,str(WORK/"resource-ui/tools/server53"))
from world_profile import tag
sys.path.insert(0,str(WORK/"inputs/CatanCraft_Clean_R7_Input"))
from nbt_check import NBT

def write(p, v):
    p.parent.mkdir(parents=True,exist_ok=True)
    p.write_bytes(v if isinstance(v,bytes) else json.dumps(v).encode())

class CheckpointTests(unittest.TestCase):
    def setUp(self):
        temp=tempfile.TemporaryDirectory(dir=HERE)
        self.addCleanup(temp.cleanup)
        self.w=Path(temp.name)
        self.run=self.w/"resource-ui/build/synthetic-layout"
        world=self.run/cp.WORLD
        self.data=world/"data"
        self.proof=self.data/"progression53-layout-6"
        self.r=self.w/"receipts/combined"
        self.snap=self.w/"snapshot"
        self.src="5"*40
        uid=str(uuid4())
        self.lock=world/"session.lock"
        write(self.lock,"world lock")
        auth=f"{cp.WORLD}/catancraft-fresh-demo.json"
        write(self.run/auth,{"worldId":uid,"mapSha256":"a"*64,"authoredWorldSha256":"b"*64,
                             "installationBoundPath":str(world)})
        config="config/catancraft/progression53-input.json"
        write(self.run/config,{"sourceCommit":self.src,"layout":6,"worldFolder":cp.WORLD,
                               "authoredWorldSha256":"b"*64,"mapSha256":"a"*64})
        stage={"schema":1,"purpose":"verified-authored53-native-run","sourceCommit":self.src,"layout":6,
               "root":str(self.run),"worldFolder":cp.WORLD,"worldId":uid,"namespace":"catancraft_progression53",
               "inputManifestSHA256":cp.ORIGINAL,"immutableFiles":{config:cp.pin(self.run/config)},
               "initialFiles":{auth:cp.pin(self.run/auth)}}
        write(self.run/cp.MARKER,stage)
        write(self.r/"layout6-staging.json",stage)
        log=self.r/"layout6-seed/native.log"
        write(log,b"=== 1 GAME TESTS COMPLETE ===\nAll 1 required tests passed")
        cmd=["runGameTestServer","tools/authored53/validate.gradle","tools/authored53/combined.gradle",
             "-PgameTestNamespace=catancraft_progression53","-PauthoredPhase=seed",
             "-PauthoredSource="+self.src,"-PauthoredRunRoot="+str(self.run)]
        write(log.with_name("process.json"),{"status":"NATIVE_PROCESS_PASS_REQUIRES_REVIEW","source":self.src,
               "sourceUnchanged":True,"exitCode":0,"command":cmd,"reportedCompleted":[1],"expectedTests":1,
               "requiredTestsPassedMessage":True,"nativeLogSHA256":cp.pin(log),"startedUnix":1,"endedUnix":2})
        write(self.data/"history.dat",b"journal")
        history=cp.pin(self.data/"history.dat")
        ledger=[tag(3,"tier",5)]
        write(self.data/"catancraft.dat",gzip.compress(tag(10,"",[tag(10,"data",ledger)])))
        raw=gzip.compress(tag(10,"",[tag(3,"schema",1),tag(3,"layout",6),tag(8,"source",self.src),
                                    tag(10,"ledger",ledger),tag(10,"books",[tag(3,c,1) for c in CITIES]),
                                    tag(10,"histories",[tag(8,"history.dat",history)])]))
        write(self.proof/"restart.dat",b"CPR1"+len(raw).to_bytes(4,"big")+hashlib.sha256(raw).digest()+raw)
        write(self.proof/"seed.json",{"schema":1,"result":"PASS","purpose":"verified-clean-r7-progression53",
               "phase":"seed","source":self.src,"layout":6,"worldId":uid,
               "mapSHA256":"a"*64,"authoredWorldSHA256":"b"*64,
               "completedHistorySHA256":{"history.dat":history},
               "checkpoints":[{"city":c,"townHallLevel":t} for c in CITIES for t in range(1,6)],
               "factoryPurchasedLevels":{f"{c}:{p}:{INDUSTRIES[(3*i+j+6)%9]}":[1,2,3,4,5] for i,c in enumerate(CITIES) for j,p in enumerate(PLOTS)}})

    def do(self,mode="capture",digest=None):
        return cp.transfer(mode,self.w,6,self.src,self.snap,lambda b:NBT(b).root(),digest)

    def test_roundtrip(self):
        before=cp.inventory(self.run)
        digest=self.do()
        original=self.w/"preserved"
        self.run.rename(original)
        self.assertEqual(self.do("restore",digest),str(self.run))
        self.assertEqual(cp.inventory(self.run),before)
        self.assertNotEqual((original/cp.MARKER).stat().st_ino,(self.run/cp.MARKER).stat().st_ino)

    def test_live_java_style_lock(self):
        with self.lock.open("r+b") as f:
            fcntl.lockf(f,fcntl.LOCK_EX|fcntl.LOCK_NB)
            with self.assertRaisesRegex(ValueError,"lock is held"): self.do()
        self.assertFalse(self.snap.exists())

    def test_partial_verify(self):
        write(self.proof/"verify.json",{"result":"RUNNING"})
        with self.assertRaisesRegex(ValueError,"already started"): self.do()

    def test_stale_runner_receipt(self):
        (self.r/"layout6-verify").mkdir()
        with self.assertRaisesRegex(ValueError,"previous verify"): self.do()

    def test_failed_seed(self):
        p=self.r/"layout6-seed/process.json"
        r=cp.read(p);r["exitCode"]=1;write(p,r)
        with self.assertRaisesRegex(ValueError,"incomplete"): self.do()

    def test_bound_identity_mismatch(self):
        p=self.run/cp.WORLD/"catancraft-fresh-demo.json"
        a=cp.read(p);a["installationBoundPath"]="other-world";write(p,a)
        with self.assertRaisesRegex(ValueError,"identity differs"): self.do()

    def test_links(self):
        p=self.run/"foreign"
        p.symlink_to(self.data/"history.dat")
        with self.assertRaises(ValueError): self.do()
        p.unlink();os.link(self.data/"history.dat",p)
        with self.assertRaises(ValueError): self.do()

    def test_existing_outputs(self):
        digest=self.do()
        with self.assertRaisesRegex(ValueError,"already exists"): self.do()
        with self.assertRaisesRegex(ValueError,"vacant"): self.do("restore",digest)

    def test_tampered_copy(self):
        digest=self.do();self.run.rename(self.w/"old")
        write(self.snap/"run/new-file",b"x")
        with self.assertRaisesRegex(ValueError,"contents differ"): self.do("restore",digest)
        self.assertFalse(self.run.exists())

    def test_wrong_pin_or_changed_receipt(self):
        digest=self.do();self.run.rename(self.w/"old")
        with self.assertRaisesRegex(ValueError,"checksum differs"): self.do("restore","a"*64)
        p=self.r/"layout6-seed/process.json";write(p,p.read_bytes()+b" ")
        with self.assertRaisesRegex(ValueError,"receipts changed"): self.do("restore",digest)

    def test_atomic_publish_never_replaces_empty_dir(self):
        a=self.w/"a";b=self.w/"b";a.mkdir();b.mkdir()
        with self.assertRaises(FileExistsError): cp.publish(a,b)
        self.assertTrue(a.is_dir() and b.is_dir())

if __name__=="__main__":
    unittest.main()
