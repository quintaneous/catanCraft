"""Read-only artifact/source verification: ZIP COMMIT SHA256 RUN_ID."""
import hashlib,json,sys,zipfile,subprocess
from pathlib import Path,PurePosixPath
archive=Path(sys.argv[1]);commit,digest,run=sys.argv[2:];sha=lambda b:hashlib.sha256(b).hexdigest()
assert sha(archive.read_bytes())==digest
root=archive.parent/'expanded'
with zipfile.ZipFile(archive) as z:
 assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))
 for i in z.infolist():
  p=PurePosixPath(i.filename);assert not p.is_absolute() and '..' not in p.parts and '\\' not in i.filename
  assert (i.external_attr>>16)&0o170000!=0o120000
 assert not root.exists();z.extractall(root)
ev=next(root.rglob('SOURCE_COMMIT.txt')).parent
assert (ev/'SOURCE_COMMIT.txt').read_text().strip()==commit
assert 'BUILD SUCCESSFUL' in (ev/'build.log').read_text()
expected={'catancraft_economy45':6,'catancraft_vehicle45':8,'catancraft_economy_clock':9,'catancraft':50}
for ns,count in expected.items():
 log=(ev/(ns+'.log')).read_text();assert (ev/(ns+'.exit')).read_text().strip()=='0'
 assert (ev/(ns+'.expected')).read_text().strip()==str(count)
 assert f'{count} GAME TESTS COMPLETE' in log and f'All {count} required tests passed' in log
proofs=[]
for line in (ev/'catancraft_economy45.log').read_text().splitlines():
 if 'ECONOMY45_NATIVE_PASS ' in line:proofs.append(json.loads(line.split('ECONOMY45_NATIVE_PASS ',1)[1]))
assert len(proofs)==6 and len({p['case'] for p in proofs})==6
files=subprocess.check_output(['git','ls-tree','-r','--name-only',commit]).decode().splitlines()
with zipfile.ZipFile(ev/'source.zip') as z:
 assert set(n for n in z.namelist() if not n.endswith('/'))==set(files)
 for f in files:assert z.read(f)==subprocess.check_output(['git','show',commit+':'+f]),f
jar=next(root.rglob('catancraft-0.1.45-economy-bays.jar'));old=next(Path('build/tacz44-evidence/run3/expanded').rglob('catancraft-0.1.44-tacz-only.jar'))
assert sha(old.read_bytes())=='c2d0dda48f31f1a04a3d853d68470b80956920c7c4569da6bbbf73a2f5835ca1'
classes=assets=0
with zipfile.ZipFile(jar) as z,zipfile.ZipFile(old) as b:
 for name in b.namelist():
  if name.endswith('.class') or name.startswith('data/') or name=='catancraft.mixins.json':
   assert z.read(name)==b.read(name),name
   if name.endswith('.class'):classes+=1
   else:assets+=1
record=dict(source=commit,run=int(run),archive_sha256=digest,jar_file=jar.name,jar_sha256=sha(jar.read_bytes()),jar_bytes=jar.stat().st_size,phases=sum(expected.values()),failed=0,suites=expected,source_files_verified=len(files),unchanged_044_classes=classes,unchanged_044_data_entries=assets,native_economy_vectors=proofs,scope='Native economy parity and unregistered fixed-bay policy; no vehicle UI/transactions activated; no live world or installed mods accessed.')
(archive.parent/'verification.json').write_text(json.dumps(record,indent=2)+'\n');print(json.dumps(record,indent=2))
