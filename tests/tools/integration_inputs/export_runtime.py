"""Reproduce immutable integration input contexts; no checkout writes."""
from pathlib import Path
import sys,json,gzip,hashlib,shutil,argparse
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--assets',type=Path,required=True,help='Safely extracted R5 root, containing Producer R2 subfolder')
parser.add_argument('--output',type=Path,required=True,help='Separate output directory; never a live world')
args=parser.parse_args()
BASE=args.output.resolve();BASE.mkdir(parents=True,exist_ok=True)
ASSETS=args.assets.resolve()
sys.path.insert(0,str(ASSETS/'source'))
from common import read_npz,read_schem,P,HOMES
sha=lambda b:hashlib.sha256(b).hexdigest()
def output(name,data):
 raw=json.dumps(data,sort_keys=True,separators=(',',':')).encode()
 (BASE/(name+'.json.gz')).write_bytes(gzip.compress(raw,mtime=0))
 return {'file':name+'.json.gz','sha256':sha((BASE/(name+'.json.gz')).read_bytes()),'jsonSha256':sha(raw)}
def context(a):
 states=[P[int(v)] for v in a.ravel()];pal=sorted(set(states));indices={s:i for i,s in enumerate(pal)}
 return {'order':'YZX','palette':pal,'blocks':[indices[s] for s in states],'signature':sha('\n'.join(states).encode())}
reb=json.loads((ASSETS/'contracts/r2-to-r5-rebase-and-conflicts.json').read_text())['house_groups']
arrays={k:read_npz(ASSETS/'reference'/f) for k,f in [('R1','north_r1_house_baseline.npz'),('R2','north_before.npz'),('R5','north_reworked.npz')]}
houses=[]
for i,h in enumerate(reb):
 x,z=HOMES[i];row={'id':h['group_id'],'localMin':[x,0,z],'sizeXYZ':[13,47,11],'revisions':{},'routes':{'R1_to_R5':h['direct_R1_to_R5'],'R2_to_R5':h['staged_R2_to_R5']}}
 for rev,a in arrays.items():
  c=context(a[6:53,z+12:z+23,x+16:x+29]);assert c['signature']==h[rev+'_signature'];row['revisions'][rev]=dict(c,token=h['group_id']+'@'+rev)
 # Reconstruct both exact routes and enforce convergence.
 for rev,route in [('R1',h['direct_R1_to_R5']),('R2',h['staged_R2_to_R5'])]:
  c=row['revisions'][rev];states=[c['palette'][v] for v in c['blocks']]
  for v in route:
   xx,yy,zz=v['local_xyz'];idx=(yy*11+zz-z)*13+xx-x
   assert states[idx]==v['expected_before_blockstate'];states[idx]=v['after_blockstate']
  assert sha('\n'.join(states).encode())==h['R5_signature']
 houses.append(row)
provenance={str(p.relative_to(ASSETS)):sha(p.read_bytes()) for p in [ASSETS/'contracts/r2-to-r5-rebase-and-conflicts.json',*[ASSETS/'reference'/f for f in ['north_r1_house_baseline.npz','north_before.npz','north_reworked.npz']]]}
manifest={'schemaVersion':1,'files':[output('r5-house-contexts',{'schemaVersion':1,'provenanceSHA256':provenance,'houses':houses})]}
r=ASSETS/'CatanCraft_Producer_Exterior_R2';producers=[]
for f in sorted((r/'contracts').glob('*r1_to_exterior_r2.json')):
 d=json.loads(f.read_text());src=r/d['sourceFile'];dst=r/d['targetFile'];assert sha(src.read_bytes())==d['sourceSHA256'];assert sha(dst.read_bytes())==d['targetSHA256']
 source=read_schem(src);target=read_schem(dst);assert source.shape==target.shape==(47,31,31)
 cs=context(source);ct=context(target);states=[cs['palette'][v] for v in cs['blocks']]
 for v in d['changes']:
  x,y,z=v['localXYZ'];idx=(y*31+z)*31+x;assert states[idx]==v['before'];states[idx]=v['after']
 assert states==[ct['palette'][v] for v in ct['blocks']];assert len(d['changes'])==d['writeCount']
 rel='producer_exterior_r2/'+dst.name;(BASE/'producer_exterior_r2').mkdir(exist_ok=True);shutil.copyfile(dst,BASE/rel)
 producers.append({'family':d['family'],'level':d['producerLevel'],'sizeXYZ':[31,47,31],'sourceToken':d['sourceToken'],'targetToken':d['proposedTargetToken'],'sourceSchematicSHA256':d['sourceSHA256'],'targetSchematicSHA256':d['targetSHA256'],'targetSchematicFile':rel,'source':cs,'target':ct,'changes':d['changes'],'contractSHA256':sha(f.read_bytes())})
manifest['files'].append(output('producer-r2-contexts',{'schemaVersion':1,'producers':producers,'placement':json.loads((r/'contracts/producer-exterior-r2-manifest.json').read_text()),'adjacentRoutes':[json.loads(f.read_text()) for f in sorted((r/'contracts/adjacent-upgrades').glob('*.json'))]}))
manifest['schematics']=[{'file':str(p.relative_to(BASE)),'sha256':sha(p.read_bytes())} for p in sorted((BASE/'producer_exterior_r2').glob('*.schem'))]
(BASE/'MANIFEST.json').write_text(json.dumps(manifest,indent=2)+'\n')
print('PASS: 18 house signatures, 12 house routes, 9 producer routes and source/target artifact pins verified')
print(json.dumps(manifest,indent=2))
