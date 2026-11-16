#!/usr/bin/env python3
"""Verify an immutable kit; create a new independent run, never reset or clone progress."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import sys
import uuid


def sha(data): return hashlib.sha256(data).hexdigest()
def encoded(value): return (json.dumps(value,sort_keys=True,indent=2)+'\n').encode()
def fail(message): raise ValueError(message)

def safe(root, name=''):
    if name and (not isinstance(name,str) or '\\' in name or ':' in name or PurePosixPath(name).is_absolute() or any(p in ('','..','.') for p in name.split('/'))):
        fail('Unsafe package path')
    path = root / name
    for part in [*reversed(path.parents),path]:
        if part.is_symlink() or (part.exists() and getattr(part.lstat(),'st_file_attributes',0) & 0x400):
            fail('Symlinks/junctions/reparse points are forbidden')
    return path


def inventory(root):
    safe(root)
    found = {}
    for p in root.rglob('*'):
        safe(root,p.relative_to(root).as_posix())
        if p.is_file():
            if not stat.S_ISREG(p.stat().st_mode): fail('Non-regular file')
            found[p.relative_to(root).as_posix()] = sha(p.read_bytes())
    return found


def verify(kit):
    kit = Path(os.path.abspath(kit))
    raw = safe(kit,'KIT.json').read_bytes()
    if sha(raw) != safe(kit,'KIT.sha256').read_text().split()[0]: fail('Kit manifest checksum mismatch')
    policy = json.loads(raw)
    if policy.get('schema') != 1 or policy.get('kind') != 'catancraft-immutable-server53-kit': fail('Wrong kit schema')
    if inventory(safe(kit,'baseline')) != policy['baselineFiles']: fail('Changed/extra baseline file; preserve it for review')
    actual = {}
    for p in kit.iterdir():
        safe(kit,p.name)
        if p.name in ('KIT.json','KIT.sha256','baseline','runs'): continue
        if p.is_dir(): actual.update({p.name+'/'+n:h for n,h in inventory(p).items()})
        elif p.is_file(): actual[p.name] = sha(p.read_bytes())
        else: fail('Non-regular kit entry')
    if actual != policy['toolFiles']: fail('Changed/extra kit tools/client files')
    return policy


def replace_identity(value, old, new):
    if isinstance(value,dict): return {k:replace_identity(v,old,new) for k,v in value.items()}
    if isinstance(value,list): return [replace_identity(v,old,new) for v in value]
    return new if value == old else value


def create(kit, output=None):
    kit = Path(os.path.abspath(kit))
    policy = verify(kit)
    run_id = str(uuid.uuid4())
    runs = safe(kit,'runs')
    if output is None:
        runs.mkdir(exist_ok=True)
        output = runs / ('run-'+run_id)
    output = Path(os.path.abspath(output))
    safe(output.parent)
    if not output.parent.is_dir() or output.exists() or output.is_symlink(): fail('Choose a NEW run directory; existing runs never reset')
    baseline = kit/'baseline'
    if output == kit or output == baseline or baseline in output.parents or (kit in output.parents and runs not in output.parents) or any(x.lower() in ('mods','saves') for x in output.parts): fail('Unsafe run destination')
    output.mkdir() # exclusive; leave interrupted copies for inspection
    for name,pin in sorted(policy['baselineFiles'].items()):
        source = safe(baseline,name)
        data = source.read_bytes()
        if sha(data) != pin: fail('Baseline changed during copy')
        target = safe(output,name)
        target.parent.mkdir(parents=True,exist_ok=True)
        with target.open('xb') as stream: stream.write(data)
        if sha(target.read_bytes()) != pin: fail('Run copy readback mismatch')
        if source.stat().st_mode & 0o111: target.chmod(0o755)
    package = json.loads((output/'PACKAGE.json').read_bytes())
    old = package['authorization']['worldId']
    authname = package['worldRoot']+'/catancraft-fresh-demo.json'
    names = [authname,'PROGRESSION_RUN.json','PROGRESSION_PLAN.json']
    names += [name for name in policy['baselineFiles'] if name.startswith('evidence/results-')]
    for name in names:
        value = replace_identity(json.loads((output/name).read_bytes()),old,run_id)
        (output/name).write_bytes(encoded(value))
    plan_sha = sha((output/'PROGRESSION_PLAN.json').read_bytes())
    for name in names:
        if name.startswith('evidence/results-'):
            value = json.loads((output/name).read_bytes())
            value['planSHA256'] = plan_sha
            (output/name).write_bytes(encoded(value))
    package = replace_identity(package,old,run_id)
    package['progressionRun']['planSHA256'] = plan_sha
    for name in names:
        if name in package['immutableFiles']: package['immutableFiles'][name] = sha((output/name).read_bytes())
        if name in package['initialWorldFiles']: package['initialWorldFiles'][name] = sha((output/name).read_bytes())
    package['kitSHA256'] = sha((kit/'KIT.json').read_bytes())
    raw = encoded(package)
    (output/'PACKAGE.json').write_bytes(raw)
    (output/'PACKAGE.sha256').write_text(sha(raw)+'  PACKAGE.json\n')
    (output/'.catancraft-baseline-only').unlink()
    return output


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--kit',type=Path,default=Path(__file__).resolve().parents[1])
    p.add_argument('--output',type=Path)
    p.add_argument('--verify-only',action='store_true')
    args=p.parse_args()
    try:
        if args.verify_only:
            verify(args.kit); print('Verified immutable kit; no run created, Java not executed')
        else: print('Created new isolated run: '+str(create(args.kit,args.output)))
    except (ValueError,OSError,KeyError,TypeError) as ex:
        print('REFUSED: '+str(ex),file=sys.stderr); return 2
    return 0
if __name__=='__main__': sys.exit(main())
