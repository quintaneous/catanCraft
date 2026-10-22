#!/usr/bin/env python3
"""Export immutable P1 developer fixture only. Never author geometry or enable purchases."""
import argparse,gzip,hashlib,json,sys
from pathlib import Path
import numpy as np
if not __debug__:raise RuntimeError("Fixture integrity assertions require normal Python, not -O")

def sha(b):return hashlib.sha256(b).hexdigest()
def run(root,output):
 root=root.resolve();pins={}
 for line in (root/'SHA256SUMS.txt').read_text().splitlines():
  digest,name=line.split(maxsplit=1);name=name.lstrip('*');p=(root/name).resolve();assert p.is_relative_to(root) and not p.is_symlink();assert sha(p.read_bytes())==digest,name;pins[name]=digest
 assert len(pins)==133
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
 manifest=json.loads((root/'contracts/sector-manifest.json').read_text());upgrade=json.loads((root/'contracts/basic-to-reinforced.json').read_text());variants=[];targets={}
 for asset in manifest['assets']:
  n,target=decode(root/asset['file']);assert list(n['Offset'])==asset['offset'];assert [target.shape[2],target.shape[0],target.shape[1]]==asset['size'];assert sha((root/asset['file']).read_bytes())==asset['sha256'];targets[asset['variant']]=target
  full=np.full(target.shape,'minecraft:air',dtype=object);components=[];seen=set()
  for part in asset['components']:
   assert sha((root/part['file']).read_bytes())==part['sha256'];_,array=decode(root/part['file']);offset=part['translationInSectorLocalXYZ'];points=[]
   for y,z,x in np.argwhere(array!='minecraft:air'):
    pos=(int(x)+offset[0],int(y)+offset[1],int(z)+offset[2]);assert pos not in seen;seen.add(pos);full[pos[1],pos[2],pos[0]]=array[y,z,x];points.append({'localXYZ':list(pos),'state':str(array[y,z,x])})
   components.append({'id':part['id'],'sha256':part['sha256'],'translationInSectorLocalXYZ':offset,'writes':points})
  assert np.array_equal(full,target) and len(seen)==asset['nonAirWriteCells']
  mask=np.load(root/asset['writeMask'],allow_pickle=False);assert len(mask['localXYZ'])==len(seen);assert {tuple(map(int,p)) for p in mask['localXYZ']}==seen
  for xyz,sid in zip(mask['localXYZ'],mask['stateIds']):x,y,z=map(int,xyz);assert target[y,z,x]==canonical(mask['palette'][sid])
  before=np.load(root/f"contracts/{asset['id']}_expected_before.npz",allow_pickle=False)['states'];assert np.all(before=='minecraft:air')
  variants.append({'id':asset['id'],'variant':asset['variant'],'schematicSHA256':asset['sha256'],'target':packed(target),'components':components,'writeCount':len(seen)})
 result=targets['basic'].copy()
 for c in upgrade['changes']:
  x,y,z=c['localXYZ'];assert result[y,z,x]==canonical(c['before']);result[y,z,x]=canonical(c['after']);c['before']=canonical(c['before']);c['after']=canonical(c['after'])
 assert len(upgrade['changes'])==3582 and np.array_equal(result,targets['reinforced'])
 context=np.load(root/'reference/context_r7_r5.npz',allow_pickle=False);states=np.array(context['palette'])[context['blocks']];read=states[7:17,12:26,7:186];assert read.shape==(10,14,179)
 # Preserve the explicit primary road surface/headroom beyond the wall's own read box.
 road=[]
 for y in range(80,88):
  for z in range(-846,-789):
   for x in range(-755,-744):road.append({'localXYZ':[x+839,y-81,z+844],'state':canonical(states[y-73,z+856,x+846])})
 masks=np.load(root/'reference/road-mask-site.npz',allow_pickle=False)
 for variant in variants:
  for part in variant['components']:
   for row in part['writes']:
    x,y,z=row['localXYZ'];assert not masks['road'][z+12,x+7] and not masks['turnBuffer'][z+12,x+7]
 for row in road:
  if row['localXYZ'][1]>=0:assert row['state']=='minecraft:air'

 def xzpoints(mask):return [[int(x)-7,int(z)-12] for z,x in np.argwhere(mask)]
 coupons=[]
 for f in sorted((root/'validation/corner_coupons').glob('*.schem')):
  _,a=decode(f);coupons.append({'id':f.stem,'sha256':sha(f.read_bytes()),'context':packed(a)})
 data={'schemaVersion':1,'revision':'SECTOR_WALL_P1','approved':False,'testOnly':True,'sourceMemberSHA256':pins,'manifest':manifest,'readLocalMin':[0,-1,0],'readContext':packed(read),'variants':variants,'upgrade':upgrade,'primaryRoadReads':road,'roadLocalXZ':xzpoints(masks['road']),'turnBufferLocalXZ':xzpoints(masks['turnBuffer']),'cornerInterfaces':json.loads((root/'contracts/corner-interfaces.json').read_text()),'coupons':coupons,'emplacements':json.loads((root/'contracts/emplacements.json').read_text()),'protectedEnvelopes':json.loads((root/'contracts/protected-building-envelopes.json').read_text()),'footprintProposal':json.loads((root/'contracts/proposed-footprint-change.json').read_text()),'scope':'City A north prototype context only; rotated geometry fixtures are not site surveys or production approval.'}
 raw=json.dumps(data,sort_keys=True,separators=(',',':')).encode();compressed=gzip.compress(raw,mtime=0);output.parent.mkdir(parents=True,exist_ok=True);output.write_bytes(compressed)
 print(json.dumps({'resource':str(output),'sha256':sha(compressed),'jsonSHA256':sha(raw),'sourceMembers':len(pins),'readCells':read.size,'primaryRoadReadCells':len(road),'coupons':len(coupons),'approved':False}))
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--input',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();run(a.input,a.output)
