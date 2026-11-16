#!/usr/bin/env python3
"""Assemble immutable full-runtime baseline plus NEW/START entry points; never starts Java."""
import argparse
import json
import os
from pathlib import Path
import shutil
import sys
import tempfile
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from create_progression_run import create_run
from package_authored_demo import digest, PROGRESSION_MODE
from prepare_fresh_demo import InputError, checked_path, read_regular, sha256
from progression_ladder import encoded
from server53.new_run import inventory

HERE=Path(__file__).resolve().parent


def make_kit(input_root,manifest,expected_sha,output):
    raw=read_regular(manifest)
    if sha256(raw)!=digest(expected_sha): raise InputError('Input manifest checksum mismatch')
    m=json.loads(raw)
    if m['bootstrap']['mode']!=PROGRESSION_MODE or not m.get('fullRuntime'): raise InputError('Kit requires protected-map progression and full pinned runtime')
    root=checked_path(input_root,directory=True)
    destination=Path(os.path.abspath(output))
    checked_path(destination.parent,directory=True)
    if destination.exists() or destination.is_symlink() or destination==root or root in destination.parents or any(x.lower() in {'mods','saves'} for x in destination.parts):
        raise InputError('Use a NEW isolated kit destination')
    with tempfile.TemporaryDirectory(prefix='.kit-stage-',dir=destination.parent) as temporary:
        stage=Path(temporary)
        baseline=stage/'baseline'
        create_run(root,manifest,expected_sha,baseline)
        package=json.loads((baseline/'PACKAGE.json').read_bytes())
        if not package['runtimeBundled'] or not package['fullModpack']: raise InputError('Complete pinned runtime required')
        checklist=read_regular(HERE.parents[1]/'docs/SERVER_WIDE_053_SOLO_CHECKLIST.md')
        additions={
          'tools/Start-Server.ps1':read_regular(HERE/'Start-Server.ps1'),
          'START_SERVER.bat':b'@echo off\r\ncd /d "%~dp0"\r\npowershell.exe -NoProfile -ExecutionPolicy Bypass -File tools\\Start-Server.ps1 %*\r\nif errorlevel 1 pause\r\n',
          'START_SERVER.sh':b'#!/bin/sh\nset -eu\ncd -- "$(dirname -- "$0")"\nexec python3 tools/launch_demo.py "$@"\n',
          'SOLO_CHECKLIST.md':checklist,
          'README_PROGRESSION.txt':read_regular(HERE/'README.md'),
          'README_DEMO.txt':read_regular(HERE/'README.md'),
        }
        additions['START_DEMO.bat']=additions['START_SERVER.bat']
        additions['START_DEMO.sh']=additions['START_SERVER.sh']
        for name,data in additions.items():
            (baseline/name).write_bytes(data)
            package['immutableFiles'][name]=sha256(data)
            if name.endswith('.sh'): (baseline/name).chmod(0o755)
        package['status']='READY_FOR_FOCUSED_SOLO_TEST' if package['readyForFocusedSoloTest'] else 'ASSEMBLED_CURRENT_RUNTIME_REQUIRES_AUTOMATED_VALIDATION'
        package['ownerAcceptance']='PENDING'
        raw_package=encoded(package)
        (baseline/'PACKAGE.json').write_bytes(raw_package)
        (baseline/'PACKAGE.sha256').write_text(sha256(raw_package)+'  PACKAGE.json\n')
        (baseline/'.catancraft-baseline-only').write_text('IMMUTABLE SOURCE. Use NEW_TEST_RUN; never launch this folder.\n')
        for name,pin in package['modFiles'].items():
            target=stage/'client'/name
            target.parent.mkdir(parents=True,exist_ok=True)
            data=read_regular(baseline/name)
            if sha256(data)!=pin: raise InputError('Client mod readback mismatch')
            target.write_bytes(data)
        root_files={
          'tools/new_run.py':read_regular(HERE/'new_run.py'),
          'tools/New-TestRun.ps1':read_regular(HERE/'New-TestRun.ps1'),
          'SOLO_CHECKLIST.md':checklist,
          'START_HERE.txt':read_regular(HERE/'README.md'),
          'NEW_TEST_RUN.bat':b'@echo off\r\ncd /d "%~dp0"\r\npowershell.exe -NoProfile -ExecutionPolicy Bypass -File tools\\New-TestRun.ps1 %*\r\nif errorlevel 1 pause\r\n',
          'NEW_TEST_RUN.sh':b'#!/bin/sh\nset -eu\ncd -- "$(dirname -- "$0")"\nexec python3 tools/new_run.py "$@"\n',
          'client/README.txt':b'Use a separate Minecraft 1.20.1 / Forge 47.4.10 client profile. Copy the four supplied mods; no installed profile is changed by this package. Join 127.0.0.1:25566 after whitelisting your authenticated username in the server console.\n',
        }
        for name,data in root_files.items():
            target=stage/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
            if name.endswith('.sh'): target.chmod(0o755)
        files=inventory(stage)
        kit={'schema':1,'kind':'catancraft-immutable-server53-kit','sourceCommit':m['sourceCommit'],
             'inputManifestSHA256':expected_sha,'baselineFiles':inventory(baseline),
             'toolFiles':{n:h for n,h in files.items() if not n.startswith('baseline/')},
             'fullRuntimeBundled':True,'javaBundled':package['javaBundled'],
             'readyForFocusedSoloTest':package['readyForFocusedSoloTest'],'ownerAcceptance':'PENDING',
             'sectorArtEnabled':package['sectorArtEnabled'],'freshRunOnly':True,'noServerExecutedByPackager':True}
        raw_kit=encoded(kit)
        (stage/'KIT.json').write_bytes(raw_kit)
        (stage/'KIT.sha256').write_text(sha256(raw_kit)+'  KIT.json\n')
        destination.mkdir()
        for child in stage.iterdir(): shutil.move(str(child),destination/child.name)
    return kit


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ('input-root','manifest','output'): p.add_argument('--'+name,type=Path,required=True)
    p.add_argument('--manifest-sha256',required=True)
    a=p.parse_args()
    try: print(json.dumps(make_kit(a.input_root,a.manifest,a.manifest_sha256,a.output),indent=2))
    except (InputError,OSError,ValueError,KeyError,TypeError) as ex:
        print('REFUSED: '+str(ex),file=sys.stderr);return 2
    return 0
if __name__=='__main__':sys.exit(main())
