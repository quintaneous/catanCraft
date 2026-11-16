"""Synthetic manifests/NBT/runtime fixtures only. No game, real Java, downloads or saves."""
from io import BytesIO
import gzip
import importlib.util
import json
import os
from pathlib import Path
import struct
import subprocess
import shutil
import sys
import unittest
from unittest.mock import patch
import zipfile
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import package_authored_demo as assembler
from server53 import contracts, new_run, package_server, stage_inputs
from progression_ladder import encoded
import test_package_authored_demo as fixture


class Server53KitTests(unittest.TestCase):
    def setUp(self):
        self.f=fixture.AuthoredDemoPackageTests();self.f.setUp();self.addCleanup(self.f.doCleanups)
        self.f.clean_r7()
        self.base=self.f.base;self.input=self.f.inputs;self.m=self.f.manifest
        self.m['bootstrap']['mode']=contracts.PROGRESSION_MODE
        self.m['bootstrap'].pop('r7OptIn')
        raw=gzip.decompress(self.f.world_files['level.dat'])
        rules=b'\x0a'+fixture.text('GameRules')+b'\x08'+fixture.text('randomTickSpeed')+fixture.text('0')+b'\x08'+fixture.text('keepInventory')+fixture.text('true')+b'\x00'
        self.f.world_files['level.dat']=gzip.compress(raw[:-2]+b'\x03'+fixture.text('GameType')+struct.pack('>i',1)+b'\x01'+fixture.text('Difficulty')+b'\x00'+rules+raw[-2:],mtime=0)
        self.f.rewrite_world()
        jar=zipfile.ZipFile(BytesIO((self.input/'built.jar').read_bytes()))
        files={n:jar.read(n) for n in jar.namelist()};jar.close()
        files['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\r\nCatanCraft-Source-Commit: '+b'a'*40+b'\r\n'
        self.f.write_input(self.m['jar'],fixture.archive(files))
        report={'kind':'matching-runtime-evidence','sourceCommit':'a'*40,'jarSHA256':self.m['jar']['sha256'],'fixtureOnly':True}
        self.f.write_input(self.m['sourceEvidence'],encoded(report))
        mapbytes=(Path(__file__).resolve().parents[1]/'src/main/resources/data/catancraft/maps/season1_river_bridges_v3.json').read_bytes()
        self.f.write_input(self.m['config'][0],mapbytes)
        self.m['bootstrap']['authorization']['mapSha256']=assembler.sha256(mapbytes)
        self.fake_mods={name:assembler.sha256(('SYNTHETIC '+name).encode()) for name in contracts.PUBLISHED_MODS}
        self.patcher=patch.object(assembler,'PUBLISHED_MODS',self.fake_mods);self.patcher.start();self.addCleanup(self.patcher.stop)
        self.m['fullRuntime']={'schema':1,'mods':[],'forgeLibraries':[],'windowsJava':[]}
        for name,pin in self.fake_mods.items():
            (self.input/name).write_bytes(('SYNTHETIC '+name).encode())
            self.m['fullRuntime']['mods'].append({'source':name,'target':'mods/'+name,'sha256':pin})
        for name,pin in self.m['forgeRuntimeFiles'].items():
            source=Path(name).name
            (self.input/source).write_bytes(b'official-fixture-'+source.encode())
            self.m['fullRuntime']['forgeLibraries'].append({'source':source,'target':name,'sha256':pin})
        self.manifest=self.input/'full.json';self.output=self.base/'kit'
        self.save()

    def save(self):
        self.manifest.write_bytes(encoded(self.m));self.pin=assembler.sha256(self.manifest.read_bytes())
    def kit(self):
        self.save();return package_server.make_kit(self.input,self.manifest,self.pin,self.output)
    def launcher(self,root):
        spec=importlib.util.spec_from_file_location('server53_synthetic_launcher',root/'tools/launch_demo.py')
        m=importlib.util.module_from_spec(spec)
        previous=sys.dont_write_bytecode;sys.dont_write_bytecode=True
        try:spec.loader.exec_module(m)
        finally:sys.dont_write_bytecode=previous
        return m
    def launch(self,root,verify=True):
        launcher=self.launcher(root);cwd=Path.cwd()
        with patch.object(sys,'argv',['launch_demo.py']+(['--verify-only'] if verify else [])),patch.object(launcher.subprocess,'run') as java,patch.object(launcher.subprocess,'call') as server:
            try: result=launcher.main()
            finally: os.chdir(cwd)
            java.assert_not_called();server.assert_not_called();return result

    def test_pristine_derived_profile_complete_runtime_and_honest_pending(self):
        before={p.name:p.read_bytes() for p in self.input.iterdir()}
        kit=self.kit();baseline=self.output/'baseline'
        policy=json.loads((baseline/'PACKAGE.json').read_bytes())
        self.assertTrue(kit['fullRuntimeBundled']);self.assertFalse(kit['readyForFocusedSoloTest'])
        self.assertFalse(policy['evidenceSemanticsVerifiedByPackager'])
        level=assembler.level_data((baseline/self.m['world']['root']/'level.dat').read_bytes())
        self.assertEqual((level['GameType'],level['Difficulty'],level['GameRules']['randomTickSpeed']),(2,2,'3'))
        self.assertEqual(level['GameRules']['keepInventory'],'true')
        self.assertEqual(before,{p.name:p.read_bytes() for p in self.input.iterdir()})
        for n,data in self.f.world_files.items():
            if n!='level.dat': self.assertEqual((baseline/self.m['world']['root']/n).read_bytes(),data)
        self.assertEqual(len(list((self.output/'client/mods').iterdir())),4)
        self.assertEqual((baseline/'SOLO_CHECKLIST.md').read_bytes(),(self.output/'SOLO_CHECKLIST.md').read_bytes())
        self.assertIn('eula=false',(baseline/'eula.txt').read_text())
        with self.assertRaisesRegex(RuntimeError,'baseline'):self.launch(baseline)

    def test_new_verify_does_not_create_runs_or_launch_and_new_is_independent(self):
        self.kit();before=new_run.inventory(self.output/'baseline');new_run.verify(self.output)
        self.assertFalse((self.output/'runs').exists())
        one=new_run.create(self.output)
        world=one/self.m['world']['root'];(world/'data').mkdir();(world/'data/paid.dat').write_bytes(b'PAID')
        (world/'level.dat').write_bytes(b'PLAYED')
        two=new_run.create(self.output)
        p1=json.loads((one/'PACKAGE.json').read_bytes());p2=json.loads((two/'PACKAGE.json').read_bytes())
        self.assertNotEqual(p1['authorization']['worldId'],p2['authorization']['worldId'])
        self.assertEqual((world/'data/paid.dat').read_bytes(),b'PAID')
        self.assertFalse((two/self.m['world']['root']/'data').exists())
        self.assertEqual(before,new_run.inventory(self.output/'baseline'))
        self.assertEqual(self.launch(two),0)
        self.assertFalse((two/'.demo-launch-state.json').exists())
        self.assertEqual((two/'SOLO_CHECKLIST.md').read_bytes(),(self.output/'SOLO_CHECKLIST.md').read_bytes())
        plan=json.loads((two/'PROGRESSION_PLAN.json').read_bytes())
        self.assertEqual(plan['runId'],p2['authorization']['worldId'])
        for f in (two/'evidence').glob('results-*'):
            r=json.loads(f.read_bytes());self.assertEqual(r['runId'],plan['runId']);self.assertEqual(r['planSHA256'],p2['progressionRun']['planSHA256'])

    def test_tampered_extra_baseline_tool_and_client_files_refuse(self):
        self.kit()
        for name in ('baseline/mods/extra.jar','tools/extra.py','client/mods/extra.jar','extra.bat'):
            with self.subTest(name=name):
                path=self.output/name;path.write_bytes(b'EXTRA')
                with self.assertRaises(ValueError):new_run.create(self.output)
                path.unlink()
        name=self.output/'baseline/eula.txt';saved=name.read_bytes();name.write_bytes(b'eula=true')
        with self.assertRaises(ValueError):new_run.create(self.output)
        name.write_bytes(saved)
        self.assertFalse((self.output/'runs').exists())

    def test_symlink_baseline_root_tools_and_run_parent_refuse(self):
        self.kit()
        for rel in ('baseline/link','tools/link','runs'):
            p=self.output/rel;p.symlink_to(self.base,target_is_directory=True)
            with self.assertRaises(ValueError):new_run.verify(self.output)
            p.unlink()
        link=self.base/'linked';link.symlink_to(self.base,target_is_directory=True)
        with self.assertRaises(ValueError):new_run.create(self.output,link/'new')
        self.assertFalse((self.base/'new').exists())

    def test_existing_destination_preserved(self):
        self.kit();run=self.base/'existing';run.mkdir();(run/'keep').write_bytes(b'KEEP')
        with self.assertRaises(ValueError):new_run.create(self.output,run)
        self.assertEqual((run/'keep').read_bytes(),b'KEEP')

    def test_extra_mod_library_tool_and_root_script_refuse_before_java(self):
        self.kit();run=new_run.create(self.output)
        for rel in ('mods/extra.jar','libraries/extra.jar','tools/extra.py','extra.bat'):
            p=run/rel;p.write_bytes(b'EXTRA')
            with self.assertRaises(RuntimeError):self.launch(run)
            p.unlink()
        self.assertEqual(self.launch(run),0)

    def test_eula_false_start_refuses_before_java_or_binding(self):
        self.kit();run=new_run.create(self.output)
        with self.assertRaisesRegex(RuntimeError,'EULA'):self.launch(run,verify=False)
        self.assertFalse((run/'.demo-launch-state.json').exists())

    def test_partial_or_redirected_binding_refuses(self):
        self.kit();run=new_run.create(self.output)
        auth=run/self.m['world']['root']/'catancraft-fresh-demo.json';value=json.loads(auth.read_bytes())
        value['installationBoundPath']=str(auth.parent);auth.write_bytes(encoded(value))
        with self.assertRaises(RuntimeError):self.launch(run)
        marker=run/'.demo-launch-state.json';marker.write_bytes(b'')
        with self.assertRaises(ValueError):self.launch(run)
        marker.unlink();marker.symlink_to(auth)
        with self.assertRaises(RuntimeError):self.launch(run)

    def test_runtime_wrong_mod_hash_missing_library_or_duplicate_destination_refuses(self):
        for mutate in ('mod','library','duplicate'):
            original=json.loads(json.dumps(self.m))
            if mutate=='mod':self.m['fullRuntime']['mods'][0]['sha256']='0'*64
            if mutate=='library':self.m['fullRuntime']['forgeLibraries'].pop()
            if mutate=='duplicate':self.m['fullRuntime']['forgeLibraries'].append(self.m['fullRuntime']['forgeLibraries'][0])
            with self.assertRaises(assembler.InputError):self.kit()
            self.assertFalse(self.output.exists());self.m=original

    def test_wrong_jar_source_commit_cannot_be_claimed_by_report(self):
        self.m['sourceCommit']='b'*40
        r=json.loads((self.input/'source-evidence.json').read_bytes());r['sourceCommit']='b'*40
        self.f.write_input(self.m['sourceEvidence'],encoded(r))
        with self.assertRaisesRegex(assembler.InputError,'source commit'):self.kit()

    def test_native_evidence_hash_and_path_required(self):
        for source,pin in (('../outside','0'*64),('missing.log','0'*64)):
            r=json.loads((self.input/'source-evidence.json').read_bytes());r['automatedValidation']={'nativeEvidence':[{'source':source,'sha256':pin}]}
            self.f.write_input(self.m['sourceEvidence'],encoded(r))
            with self.assertRaises((assembler.InputError,OSError)):self.kit()
            self.assertFalse(self.output.exists())

    def test_readiness_requires_every_aggregate_and_never_synthetic_fixture(self):
        r={'fixtureOnly':False,'serverWideValidation':{'schema':1,'status':'PASS','cities':list('abcdefghijklmnop'),'wallSectors':64,'structuralCombinationsPerCity':81,'ownerAcceptance':False,'nativeEvidence':[{'source':'synthetic'}]},
           'automatedValidation':{'schema':1,'status':'PASS','sourceCommit':'a'*40,'nativeEvidence':[{'source':'synthetic'}],
             'authoredWalls':{'cities':16,'sectors':64,'purchases':128,'seedPassed':True,'restartPassed':True},
             'progression':{'layouts':9,'cityTierCheckpoints':720,'factoryTypeSitePairs':432,'paidFactoryLevels':[1,2,3,4,5],'restartCityChecks':144},
             'retainedRegression':{'executed':10,'passed':10,'failed':0},'combinedPublishedRuntime':True}}
        self.assertTrue(contracts.readiness(r,'a'*40,False)) # validator branch only, no artifact published
        self.assertFalse(contracts.readiness(r,'a'*40,True))
        for section,key,value in [('automatedValidation','status','PENDING'),('automatedValidation','sourceCommit','b'*40),('automatedValidation','nativeEvidence',[]),('serverWideValidation','nativeEvidence',[]),('serverWideValidation','wallSectors',4)]:
            changed=json.loads(json.dumps(r));changed[section][key]=value
            self.assertFalse(contracts.readiness(changed,'a'*40,False))
        for group,key in [('authoredWalls','purchases'),('progression','layouts'),('progression','cityTierCheckpoints'),('progression','factoryTypeSitePairs'),('progression','restartCityChecks')]:
            changed=json.loads(json.dumps(r));changed['automatedValidation'][group][key]-=1
            self.assertFalse(contracts.readiness(changed,'a'*40,False))

    @unittest.skipUnless(os.environ.get('CATAN_PWSH') or shutil.which('pwsh'), 'PowerShell unavailable; no platform pass claimed')
    def test_native_powershell_verify_clone_tamper_and_eula_refusal(self):
        self.kit()
        pwsh=os.environ.get('CATAN_PWSH') or shutil.which('pwsh')
        def invoke(script,*args):
            result=subprocess.run([pwsh,'-NoProfile','-File',str(script),*args],capture_output=True,text=True)
            print('POWERSHELL',script.name,list(args),'exit',result.returncode,result.stdout.strip(),result.stderr.strip())
            return result
        script=self.output/'tools/New-TestRun.ps1'
        result=invoke(script,'-VerifyOnly');self.assertEqual(result.returncode,0,result.stderr)
        self.assertFalse((self.output/'runs').exists())
        before=new_run.inventory(self.output/'baseline')
        result=invoke(script);self.assertEqual(result.returncode,0,result.stderr)
        runs=list((self.output/'runs').iterdir());self.assertEqual(len(runs),1);run=runs[0]
        self.assertEqual(before,new_run.inventory(self.output/'baseline'))
        self.assertEqual(self.launch(run),0) # cross-language identities/hashes agree
        start=run/'tools/Start-Server.ps1'
        result=invoke(start,'-VerifyOnly');self.assertEqual(result.returncode,0,result.stderr)
        self.assertFalse((run/'.demo-launch-state.json').exists())
        result=invoke(start);self.assertNotEqual(result.returncode,0);self.assertIn('EULA',result.stderr)
        self.assertFalse((run/'.demo-launch-state.json').exists())
        extra=run/'mods/extra.jar';extra.write_bytes(b'EXTRA')
        result=invoke(start,'-VerifyOnly');self.assertNotEqual(result.returncode,0)
        extra.unlink()
        world=run/self.m['world']['root'];(world/'data').mkdir();(world/'data/paid.dat').write_bytes(b'KEEP')
        result=invoke(script);self.assertEqual(result.returncode,0,result.stderr)
        self.assertEqual((world/'data/paid.dat').read_bytes(),b'KEEP')
        runs=list((self.output/'runs').iterdir());self.assertEqual(len(runs),2)
        ids={json.loads((r/'PACKAGE.json').read_bytes())['authorization']['worldId'] for r in runs}
        self.assertEqual(len(ids),2)
        extra=self.output/'tools/extra.ps1';extra.write_bytes(b'EXTRA')
        result=invoke(script,'-VerifyOnly');self.assertNotEqual(result.returncode,0)

    @unittest.skipUnless(os.environ.get('CATAN_PWSH') or shutil.which('pwsh'), 'PowerShell unavailable; no platform pass claimed')
    def test_powershell_unicode_run_path_and_utf8_binding_readback(self):
        # WinPS 5.1 defaults BOM-less Get-Content to ANSI, unlike pwsh 7/Linux.
        # Assert the explicit cross-version encoding contract as well as running
        # both scripts against actual UTF-8 path/binding bytes on this host.
        for name in ('New-TestRun.ps1','Start-Server.ps1'):
            text=(Path(__file__).resolve().parents[1]/'tools/server53'/name).read_text()
            reads=[line for line in text.splitlines() if 'Get-Content ' in line]
            self.assertTrue(reads)
            self.assertTrue(all('Get-Content -Raw -Encoding UTF8 -LiteralPath' in line for line in reads),name)
        self.output=self.base/'kit-Élodie-城市';self.kit()
        pwsh=os.environ.get('CATAN_PWSH') or shutil.which('pwsh')
        def invoke(script,*args):
            result=subprocess.run([pwsh,'-NoProfile','-File',str(script),*args],capture_output=True,text=True)
            print('POWERSHELL_UTF8',script.name,list(args),'exit',result.returncode,result.stdout.strip(),result.stderr.strip())
            self.assertEqual(result.returncode,0,result.stderr)
        run=self.base/'run-José-城'
        invoke(self.output/'tools/New-TestRun.ps1','-Output',str(run))
        self.assertEqual(self.launch(run),0)
        invoke(run/'tools/Start-Server.ps1','-VerifyOnly')
        package=json.loads((run/'PACKAGE.json').read_bytes())
        world=run/package['worldRoot']
        auth=dict(package['authorization'],installationBoundPath=str(world))
        binding={'root':str(run),'inputManifestSHA256':package['inputManifestSHA256']}
        for path,value in [(world/'catancraft-fresh-demo.json',auth),(run/'.demo-launch-state.json',binding)]:
            raw=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
            self.assertTrue(any(b>127 for b in raw));self.assertFalse(raw.startswith(b'\xef\xbb\xbf'))
            path.write_bytes(raw)
        # Synthetic established binding only; no Java/server launch or EULA change.
        (world/'level.dat').write_bytes(b'SYNTHETIC LEGITIMATE POST-BINDING CHANGE')
        invoke(run/'tools/Start-Server.ps1','-VerifyOnly')
        self.assertEqual(self.launch(run),0)
        self.assertIn('eula=false',(run/'eula.txt').read_text())
        self.assertEqual(json.loads((run/'.demo-launch-state.json').read_bytes()),binding)

    def test_pristine_stager_rejects_wrong_original_without_output(self):
        (self.input/'INPUT_MANIFEST.json').write_bytes(b'{}')
        args=type('Args',(),{'original_root':self.input})()
        with self.assertRaisesRegex(assembler.InputError,'pristine'):stage_inputs.stage(args)

if __name__=='__main__':unittest.main()
