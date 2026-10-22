#!/usr/bin/env python3
"""Export immutable approved City A NORTH P2 isolated-test input, without altering schematic bytes."""
import argparse,gzip,hashlib,json,sys
from pathlib import Path
import numpy as np
if not __debug__:raise RuntimeError("Fixture integrity assertions require normal Python, not -O")

def sha(b):return hashlib.sha256(b).hexdigest()
def run(root,output,runtime_root):
 root=root.resolve();pins={}
 for line in (root/'SHA256SUMS.txt').read_text().splitlines():
  digest,name=line.split(maxsplit=1);name=name.lstrip('*');p=(root/name).resolve();assert p.is_relative_to(root) and not p.is_symlink();assert sha(p.read_bytes())==digest,name;pins[name]=digest
 assert len(pins)==180
 sys.path.insert(0,str(root/'source/vendor'));import nbtlib
 definitions=json.loads((root/'source/minecraft_1_20_1_block_definitions.json').read_text());ids=set(json.loads((root/'source/minecraft_1_20_1_block_ids.json').read_text()))
 def canonical(s):
  name,_,tail=str(s).partition('[');assert name.startswith('minecraft:') and name[10:] in ids;definition=definitions.get(name[10:],[{},{}]);defaults=definition[1].copy()
  if tail:defaults.update(dict(v.split('=',1) for v in tail[:-1].split(',')))
  assert set(defaults)==set(definition[0])
  for k,v in defaults.items():assert v in definition[0][k]
  return name+('['+','.join(k+'='+v for k,v in sorted(defaults.items()))+']' if defaults else '')
 def decode(path):
  n=nbtlib.load(path);assert int(n['Version'])==2 and int(n['DataVersion'])==3465 and not n['Entities'] and not n['BlockEntities']
  palette={int(v):canonical(str(k)) for k,v in n['Palette'].items()};values=[];v=shift=0
  for signed in n['BlockData']:
   b=int(signed)&255;v|=(b&127)<<shift
   if b&128:shift+=7;assert shift<=28
   else:values.append(palette[v]);v=shift=0
  assert shift==0;return n,np.array(values).reshape(int(n['Height']),int(n['Length']),int(n['Width']))
 def packed(array):
  texts=[canonical(x) for x in array.ravel()];palette=sorted(set(texts));lookup={s:i for i,s in enumerate(palette)}
  return {'sizeXYZ':[array.shape[2],array.shape[0],array.shape[1]],'order':'YZX','palette':palette,'blocks':[lookup[s] for s in texts],'signature':sha('\n'.join(texts).encode())}
 manifest=json.loads((root/'contracts/sector-manifest.json').read_text());upgrade=json.loads((root/'contracts/basic-to-reinforced.json').read_text());variants=[];targets={};worldbase=np.array([-839,81,-853])
 context=np.load(root/'reference/context_r7_r5.npz',allow_pickle=False);states=np.array(context['palette'])[context['blocks']];origin=np.array(context['origin']);assert origin.tolist()==[-846,73,-856]
 roadfile=np.load(root/'reference/authoring-road-water-masks.npz',allow_pickle=False);road=roadfile['road_mask'];margin=road.copy()
 # Two-cell Manhattan dilation, matching the supplied authoring contract, no scipy dependency.
 for _ in range(2):
  old=margin.copy();margin[1:]|=old[:-1];margin[:-1]|=old[1:];margin[:,1:]|=old[:,:-1];margin[:,:-1]|=old[:,1:]
 protected=json.loads((root/'contracts/protected-building-envelopes.json').read_text())
 allwrites={}
 for asset in manifest['assets']:
  n,target=decode(root/asset['file']);assert list(n['Offset'])==asset['offsetXYZ'];assert [target.shape[2],target.shape[0],target.shape[1]]==asset['sizeXYZ']==[179,9,20];assert sha((root/asset['file']).read_bytes())==asset['sha256'];targets[asset['variant']]=target
  full=np.full(target.shape,'minecraft:air',dtype=object);components=[];seen=set()
  for index,part in enumerate(asset['components']):
   assert sha((root/part['file']).read_bytes())==part['sha256'];_,array=decode(root/part['file']);offset=part['translationSectorLocalXYZ'];points=[]
   for y,z,x in np.argwhere(array!='minecraft:air'):
    pos=(int(x)+offset[0],int(y)+offset[1],int(z)+offset[2]);assert pos not in seen;seen.add(pos);full[pos[1],pos[2],pos[0]]=array[y,z,x];points.append({'localXYZ':list(pos),'state':str(array[y,z,x])})
   components.append({'id':f'part_{index:02d}','sha256':part['sha256'],'translationInSectorLocalXYZ':offset,'writes':points})
  assert np.array_equal(full,target) and len(seen)==asset['nonAirWriteCells']
  mask=np.load(root/asset['writeMask'],allow_pickle=False);assert len(mask['localXYZ'])==len(seen);assert {tuple(map(int,p)) for p in mask['localXYZ']}==seen
  for xyz,sid in zip(mask['localXYZ'],mask['stateIds']):x,y,z=map(int,xyz);assert target[y,z,x]==canonical(mask['palette'][sid])
  source=np.load(root/f"contracts/{asset['id']}_source_and_support.npz",allow_pickle=False);assert np.all(source['sourceStates']=='minecraft:air');assert source['sourceWorldMinXYZ'].tolist()==worldbase.tolist();assert source['sourceStates'].shape==target.shape
  supports=[]
  for xyz,s in zip(source['supportWorldXYZ'],source['supportStates']):
   ix=xyz-origin;assert canonical(states[ix[1],ix[2],ix[0]])==canonical(s);supports.append({'localXYZ':(xyz-worldbase).tolist(),'state':canonical(s)})
  assert len(supports)==1333
  worldpoints={tuple((np.array(pos)+worldbase).tolist()) for pos in seen};allwrites[asset['variant']]=worldpoints
  for wx,wy,wz in worldpoints:
   assert not road[wz+1024,wx+1024] and not margin[wz+1024,wx+1024]
   assert not (-830<=wx<=-670 and -833<=wz<=-667)
   for env in protected['envelopes']:assert not all(env['worldMinInclusive'][i]<=[wx,wy,wz][i]<=env['worldMaxInclusive'][i] for i in range(3))
   ix=np.array([wx,wy,wz])-origin;assert states[ix[1],ix[2],ix[0]]=='minecraft:air'
  variants.append({'id':asset['id'],'variant':asset['variant'],'schematicSHA256':asset['sha256'],'target':packed(target),'components':components,'writeCount':len(seen),'supports':supports})
 result=targets['basic'].copy()
 for c in upgrade['changes']:
  x,y,z=c['localXYZ'];assert result[y,z,x]==canonical(c['before']);result[y,z,x]=canonical(c['after']);c['before']=canonical(c['before']);c['after']=canonical(c['after'])
 assert len(upgrade['changes'])==4539 and np.array_equal(result,targets['reinforced'])
 read=states[7:17,3:25,7:186];assert read.shape==(10,22,179)
 roadreads=[]
 for y in range(80,88):
  for z in range(-846,-789):
   for x in range(-755,-744):
    state=canonical(states[y-73,z+856,x+846]);assert y==80 or state=='minecraft:air';roadreads.append({'localXYZ':[x+839,y-81,z+853],'state':state})
 def roadpoints(mask):return [[int(x)-1024+839,int(z)-1024+853] for z,x in np.argwhere(mask[-856+1024:-653+1024,-846+1024:-653+1024]) + np.array([-856+1024,-846+1024])]
 # Reconcile all current raw/factory/TH/strategic bounds directly from registry source.
 import re
 registry_path=runtime_root/'src/main/java/com/quin/catancraft/world/SchematicAssetRegistry.java';registry=registry_path.read_text();envelopes=[]
 pattern=r'new Asset\(\s*"([^"]+)"\s*,\s*"[^"]+"\s*,\s*"[^"]+"\s*,\s*\d+\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*true\s*,\s*Set.of\(0, 180\)\s*,\s*(-?\d+)\s*,\s*(-?\d+)\s*\)'
 protected_ids={'th1','th2','th3','th4','th5','vehicle_factory','quarry','weapons_factory','steel_mill','farm','lumberyard','iron_oil_site','coal_copper_site','textile_mill','fuel_refinery','concrete_plant','machine_shop','chemical_plant','electronics_factory'}
 for match in re.finditer(pattern,registry):
  ident=match[1]
  if ident not in protected_ids:continue
  w,h,d,ax,ay,az,ox,oz=map(int,match.groups()[1:]);lo=[-750+ox-ax,81-ay,-750+oz-az];hi=[lo[0]+w-1,lo[1]+h-1,lo[2]+d-1]
  for worldpoints in allwrites.values():assert not any(all(lo[i]<=p[i]<=hi[i] for i in range(3)) for p in worldpoints),ident
  envelopes.append({'id':ident,'worldMinInclusive':lo,'worldMaxInclusive':hi})
 assert {e['id'] for e in envelopes}==protected_ids,sorted(protected_ids-{e['id'] for e in envelopes})
 defense_path=runtime_root/'src/main/resources/assets/catancraft/defense/contracts.json.gz';legacy=json.loads(gzip.decompress(defense_path.read_bytes()));overlaps=[]
 for item in legacy:
  if item['city']!='a':continue
  positions={tuple(row[:3]) for row in item['writes']}
  hits={variant:len(points&positions) for variant,points in allwrites.items()}
  if any(hits.values()):overlaps.append({'socket':item['socket'],'module':item['module'],'overlappingWriteCells':hits})
 # Cap delta is a separate neighbor-owned-support transition, never part of a fresh purchase.
 corners=json.loads((root/'contracts/corner-interfaces.json').read_text())
 for row in corners['interfaces']:
  target=targets['reinforced' if 'reinforced' in row['asset'] else 'basic']
  for c in row['capDelta']:
   x,y,z=c['localXYZ'];assert target[y,z,x]==canonical(c['before']);assert c['after']=='minecraft:air';c['before']=canonical(c['before'])
 caps=[]
 for world in [[-732,88,-849],[-732,88,-848],[-716,88,-839]]:
  x,y,z=(np.array(world)-worldbase).tolist();assert all(t[y,z,x]!='minecraft:air' for t in targets.values());caps.append({'worldXYZ':world,'localXYZ':[x,y,z],'basic':str(targets['basic'][y,z,x]),'reinforced':str(targets['reinforced'][y,z,x])})
 runtime={'registrySHA256':sha(registry_path.read_bytes()),'envelopes':envelopes,'legacyDefenseResourceSHA256':sha(defense_path.read_bytes()),'legacyWriteOverlaps':overlaps,'legacyPolicy':'Fresh source must match air; refuse owned legacy-module history/physical conflicts. No overwrite or migration.'}
 data={'schemaVersion':1,'revision':'P2','approvalScope':'City A NORTH appearance and footprint approved for isolated fresh-world testing only; P3 sides/prices/weapons/production deployment not approved.','approvedForIsolatedFreshNorthTest':True,'approvedForOtherCitiesOrSides':False,'sourceMemberSHA256':pins,'manifest':manifest,'readLocalMin':[0,-1,0],'readWorldMinXYZ':[-839,80,-853],'readContext':packed(read),'variants':variants,'upgrade':upgrade,'primaryRoadReads':roadreads,'roadLocalXZ':roadpoints(road),'turnBufferLocalXZ':roadpoints(margin),'cornerInterfaces':corners,'preservedEndPierCaps':caps,'futureGate':json.loads((root/'contracts/future-gate-reservation.json').read_text()),'emplacements':json.loads((root/'contracts/emplacements.json').read_text()),'protectedEnvelopes':protected,'runtimeAudit':runtime,'footprintProposal':json.loads((root/'contracts/footprint-proposal.json').read_text()),'scope':'Exact original north bytes retained. Source manifest contains historical pre-approval wording preserved for provenance. No P1 repair route or actual weapon spawn.'}
 raw=json.dumps(data,sort_keys=True,separators=(',',':')).encode();compressed=gzip.compress(raw,mtime=0);output.parent.mkdir(parents=True,exist_ok=True);output.write_bytes(compressed)
 print(json.dumps({'resource':str(output),'sha256':sha(compressed),'jsonSHA256':sha(raw),'sourceMembers':len(pins),'readCells':int(read.size),'primaryRoadReadCells':len(roadreads),'currentProtectedEnvelopes':len(envelopes),'legacySocketsWithOverlap':len(overlaps),'counts':{v['variant']:v['writeCount'] for v in variants},'upgrade':len(upgrade['changes'])}))
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--input',type=Path,required=True);p.add_argument('--runtime-root',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();run(a.input,a.output,a.runtime_root.resolve())
