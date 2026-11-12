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
expected={'catancraft_vehicle46':18,'catancraft_vehicle46_native':6,'catancraft_sbw':7,'procurement_seed':1,'procurement_verify':1,'catancraft_economy45':6,'catancraft_vehicle45':8,'catancraft_economy_clock':9,'catancraft':50}
for ns,count in expected.items():
 log=(ev/(ns+'.log')).read_text();assert (ev/(ns+'.exit')).read_text().strip()=='0'
 assert (ev/(ns+'.expected')).read_text().strip()==str(count)
 assert f'{count} GAME TESTS COMPLETE' in log and f'All {count} required tests passed' in log
import re,struct,gzip
native=(ev/'catancraft_vehicle46_native.log').read_text()
rows=re.findall(r'VEHICLE46_NATIVE_PASS vehicle=(\w+) rotation=(\d+) obbs=(\d+) seats=(\d+) sweepSamples=(\d+) checkedCells=(\d+)',native)
assert len(rows)==4 and {(r[0],int(r[1])) for r in rows}=={(v,r) for v in ['sodayo_pick_up','lav_150'] for r in [0,180]}
for v,rot,boxes,seats,samples,cells in rows:
 assert int(boxes)==(8 if v=='sodayo_pick_up' else 10) and int(seats)==(8 if v=='sodayo_pick_up' else 5) and int(samples)==117 and int(cells)>0
assert 'VEHICLE46_NATIVE_HAZARDS_PASS' in native and 'VEHICLE46_NATIVE_BOUNDARY_PASS' in native
assert native.count('externalRoadQualified=true')==4
routes=re.findall(r'VEHICLE46_NATIVE_ROUTE_PASS vehicle=(\w+) rotation=(\d+) routePoses=(\d+) continuousAngularBound=true northAndSouth=true',native)
assert len(routes)==4 and {(r[0],int(r[1])) for r in routes}=={(v,r) for v in ['sodayo_pick_up','lav_150'] for r in [0,180]}
assert all(int(r[2])==117 for r in routes)
seats=re.findall(r'VEHICLE46_NATIVE_SEAT vehicle=(\w+) rotation=(\d+) seat=(\d+) finalExit=(.*?) blockers=(.*?)\n',native)
assert len(seats)==26
assert {(v,int(rot),int(seat)) for v,rot,seat,_,_ in seats}=={(v,rot,seat) for v,n in [('sodayo_pick_up',8),('lav_150',5)] for rot in [0,180] for seat in range(n)}
for v,rot,seat,position,blockers in seats:
 assert int(seat) in range(8 if v=='sodayo_pick_up' else 5)
 assert (blockers=='[]') if v=='lav_150' else 'Native final dismount intersects own vehicle OBB ' in blockers
dismounts=re.findall(r'VEHICLE46_NATIVE_DISMOUNT vehicle=(\w+) rotation=(\d+) checkedSeats=(\d+) blockedSeats=(\d+) qualified=(true|false) firstSeatBoarded=true fullBayQualified=false',native)
assert len(dismounts)==4 and {(r[0],int(r[1])) for r in dismounts}=={(v,r) for v in ['sodayo_pick_up','lav_150'] for r in [0,180]}
for v,rot,checked,blocked,qualified in dismounts:
 assert (int(checked),int(blocked),qualified)==((8,8,'false') if v=='sodayo_pick_up' else (5,0,'true'))
assert 'ownVehicle=true sourceDrift=true roadHazard=true authoredLandmarks=true' in native
assert 'changedIdentity=true unstagedSourceHeld=true' in native
view=re.findall(r'VEHICLE46_NATIVE_VIEW_PASS checkedHeadings=12 minimumRawDot=([0-9.]+) normalizedDirection=true invalidViewHeld=true staleObbHeadingHeld=true',native)
assert len(view)==1 and .9998<float(view[0])<.999999
marks=re.findall(r'VEHICLE46_DELIVERY_PASS (\w+)',(ev/'catancraft_vehicle46.log').read_text());assert len(marks)==len(set(marks))==18
sealed=list((ev/'journals').rglob('*.dat'));assert len(sealed)==7
for f in sealed:
 b=f.read_bytes();magic,size=struct.unpack('>II',b[:8]);assert magic==0x43505231 and size==len(b)-40 and b[8:40].hex()==sha(b[40:]);assert gzip.decompress(b[40:])[0]==10
assert len(list((ev/'journals').rglob('*.foreign')))==1
assert 'SEC01_PROCUREMENT35_SEED_PASS' in (ev/'procurement_seed.log').read_text()
assert 'SEC01_PROCUREMENT35_VERIFY_PASS' in (ev/'procurement_verify.log').read_text()
assert sha((ev/'runtime-artifacts.json').read_bytes())=='6e4424ef5a564609dc893c5d9d1ff3efcc7b897d8b9ae9a9acf96d7c331c9452'
assert json.loads((ev/'runtime-verify.json').read_text())['result']=='PASS'
proofs=[]
for line in (ev/'catancraft_economy45.log').read_text().splitlines():
 if 'ECONOMY45_NATIVE_PASS ' in line:proofs.append(json.loads(line.split('ECONOMY45_NATIVE_PASS ',1)[1]))
assert len(proofs)==6 and len({p['case'] for p in proofs})==6
files=subprocess.check_output(['git','ls-tree','-r','--name-only',commit]).decode().splitlines()
with zipfile.ZipFile(ev/'source.zip') as z:
 assert set(n for n in z.namelist() if not n.endswith('/'))==set(files)
 for f in files:assert z.read(f)==subprocess.check_output(['git','show',commit+':'+f]),f
jar=next(root.rglob('catancraft-0.1.46-vehicle-delivery.jar'));old=next(Path('build/economy45-evidence/run1/expanded').rglob('catancraft-0.1.45-economy-bays.jar'))
assert sha(old.read_bytes())=='cad8c1a504951142bfb80fe22a2662acff3fb234c486fc9864e67e5e1c862ce0'
classes=assets=0
with zipfile.ZipFile(jar) as z,zipfile.ZipFile(old) as b:
 assert f'CatanCraft-Source-Commit: {commit}' in z.read('META-INF/MANIFEST.MF').decode()
 assert 'version="0.1.46-vehicle-delivery"' in z.read('META-INF/mods.toml').decode()
 assert sha(jar.read_bytes()) in (ev/'JAR_SHA256.txt').read_text()
 for name in b.namelist():
  if name.endswith('.class') or name.startswith('data/') or name=='catancraft.mixins.json':
   assert z.read(name)==b.read(name),name
   if name.endswith('.class'):classes+=1
   else:assets+=1
record=dict(source=commit,run=int(run),archive_sha256=digest,jar_file=jar.name,jar_sha256=sha(jar.read_bytes()),jar_bytes=jar.stat().st_size,phases=sum(expected.values()),failed=0,suites=expected,source_files_verified=len(files),packaged_source_version_and_jar_manifest_verified=True,unchanged_045_classes=classes,unchanged_045_data_entries=assets,native_economy_vectors=proofs,native_geometry_rows=rows,delivery_markers=marks,sealed_journal_files=len(sealed),external_road_geometry_qualified=True,full_bay_qualified=False,native_view_minimum_raw_dot=float(view[0]),native_routes=routes,native_dismounts=dismounts,native_seats=seats,scope='Vehicle delivery state machine and bounded native geometry checks, retained economy/bay/crowbar regressions; no public vehicle purchasing activation or installed-pack acceptance.')
(archive.parent/'verification.json').write_text(json.dumps(record,indent=2)+'\n');print(json.dumps(record,indent=2))
