#!/usr/bin/env python3
"""Independently decode pinned City A P3 sparse contracts; never run package authoring scripts."""
import argparse, gzip, hashlib, itertools, json, re, sys, zipfile
from pathlib import Path
import numpy as np

ARCHIVE_SHA256='96278c6ebfd1aef8454ebff9e51c0c56a48a273349d87928bf84e73ced039095'
MEMBERS_SHA256='21fd5825c66da683a10f5dfc90eb2a9b789dd118fbeafd9530083aeca6baa7aa'
P2_SHA256='039ff60335cf3d80f537adb689f11a833e3a15b7a2fcf54aeb6490bb2ae0b996'
AIR='minecraft:air'
if not __debug__: raise RuntimeError('Integrity assertions require normal Python, not -O')
def sha(b): return hashlib.sha256(b).hexdigest()
def xyz(p): return tuple(map(int,p))
def rows(mapping): return [[*p,*v] if isinstance(v,tuple) else [*p,v] for p,v in sorted(mapping.items())]
def raw_hash(a): return sha('\n'.join(map(str,a.ravel())).encode())
def run(root, output, runtime_root, archive):
 root, runtime_root, archive=map(lambda p:Path(p).resolve(),(root,runtime_root,archive))
 assert sha(archive.read_bytes())==ARCHIVE_SHA256, 'Unrecognized P3 archive'
 assert sha((root/'SHA256SUMS.txt').read_bytes())==MEMBERS_SHA256,'Unrecognized P3 member inventory'
 pins={}
 with zipfile.ZipFile(archive) as z:
  assert z.testzip() is None
  for line in (root/'SHA256SUMS.txt').read_text().splitlines():
   digest,name=line.split(maxsplit=1);name=name.lstrip('*');relative=Path(name)
   assert not relative.is_absolute() and '..' not in relative.parts and name not in pins
   path=root/relative;assert not any(p.is_symlink() for p in [path,*path.parents])
   data=path.read_bytes();assert sha(data)==digest,name
   assert z.read(root.name+'/'+name)==data,name
   pins[name]=digest
 assert len(pins)==215
 # Import only checksum-verified vendored NBT parser, not authoring/build code.
 sys.path.insert(0,str(root/'source/vendor'));import nbtlib
 definitions=json.loads((root/'source/minecraft_1_20_1_block_definitions.json').read_text())
 ids=set(json.loads((root/'source/minecraft_1_20_1_block_ids.json').read_text()))
 def canonical(s):
  name,_,tail=str(s).partition('[');assert name.startswith('minecraft:') and name[10:] in ids
  definition=definitions.get(name[10:],[{},{}]);properties=definition[1].copy()
  if tail: assert tail.endswith(']');properties.update(dict(v.split('=',1) for v in tail[:-1].split(',')))
  assert set(properties)==set(definition[0])
  for key,value in properties.items():assert value in definition[0][key]
  return name+('['+','.join(k+'='+v for k,v in sorted(properties.items()))+']' if properties else '')
 def decode(path):
  n=nbtlib.load(path);assert int(n['Version'])==2 and int(n['DataVersion'])==3465 and not n['Entities'] and not n['BlockEntities']
  palette={int(v):str(k) for k,v in n['Palette'].items()};values=[];v=shift=0
  for signed in n['BlockData']:
   b=int(signed)&255;v|=(b&127)<<shift
   if b&128:shift+=7;assert shift<=28
   else:values.append(palette[v]);v=shift=0
  assert shift==0
  a=np.array(values,dtype=object).reshape(int(n['Height']),int(n['Length']),int(n['Width']))
  for state in palette.values():canonical(state)
  return n,a
 context=np.load(root/'reference/context_r7_r5.npz',allow_pickle=False);origin=context['origin'];baseline=np.array(context['palette'])[context['blocks']]
 def source(world):
  x,y,z=np.array(world)-origin;assert 0<=x<baseline.shape[2] and 0<=y<baseline.shape[0] and 0<=z<baseline.shape[1],world
  return canonical(baseline[y,z,x])
 def load(name):return json.loads((root/name).read_text())
 manifest=load('contracts/sector-manifest.json');north=load('contracts/north-owned-transitions.json');corners=load('contracts/corner-connection-deltas.json')
 assert len(manifest['assets'])==6 and len(corners['connections'])==16
 p2zip=root/'reference/CatanCraft_Sector_Wall_P2_Entrance_Review_Assets.zip';assert sha(p2zip.read_bytes())==P2_SHA256
 with zipfile.ZipFile(p2zip) as z:
  assert z.testzip() is None
  for f in north['immutableFiles']:
   assert sha((root/f['file']).read_bytes())==f['sha256']
   match=[n for n in z.namelist() if n.endswith('/'+f['file'])];assert len(match)==1 and z.read(match[0])==(root/f['file']).read_bytes()
 p2path=runtime_root/'src/main/resources/assets/catancraft/sector_p2/north.json.gz';p2=json.loads(gzip.decompress(p2path.read_bytes()))
 arrays={};metadata={};assets=[];ownership={};newpoints={}
 for row in manifest['assets']:
  side,tier=row['front'],row['variant'];n,a=decode(root/row['file']);lo=np.array(row['worldMinXYZ'])
  assert row['supportedRotations']==[0] and list(map(int,n['Offset']))==row['storedOffsetXYZ']
  assert [a.shape[2],a.shape[0],a.shape[1]]==row['sizeXYZ'] and (np.array(row['worldPasteAnchorXYZ'])-row['localAnchorXYZ']).tolist()==lo.tolist()
  assert sha((root/row['file']).read_bytes())==row['sha256'];arrays[side,tier]=a;metadata[side,tier]=row
  rebuilt=np.full(a.shape,AIR,dtype=object);seen=set()
  for component in row['components']:
   assert component['rotation']==0 and sha((root/component['file']).read_bytes())==component['sha256']
   _,part=decode(root/component['file']);offset=component['localTranslationXYZ']
   for y,z,x in np.argwhere(part!=AIR):
    p=(int(x)+offset[0],int(y)+offset[1],int(z)+offset[2]);assert p not in seen;seen.add(p);rebuilt[p[1],p[2],p[0]]=part[y,z,x]
  assert np.array_equal(a,rebuilt)
  mask=np.load(root/row['freshMask'],allow_pickle=False);maskpoints=[xyz(p) for p in mask['localXYZ']]
  assert len(maskpoints)==len(set(maskpoints))==len(seen) and set(maskpoints)==seen
  for pos,sid,before in zip(maskpoints,mask['stateIds'],mask['expectedBefore']):
   x,y,z=pos;assert canonical(mask['palette'][sid])==canonical(a[y,z,x]);assert canonical(before)==source(np.array(pos)+lo)==AIR
 for tier in ['basic','reinforced']:
  f=next(f for f in north['immutableFiles'] if f['file'].endswith('_'+tier+'.schem'));n,a=decode(root/f['file']);arrays['north',tier]=a
  row=next(r for r in p2['manifest']['assets'] if r['variant']==tier)
  packed=next(v for v in p2['variants'] if v['variant']==tier)['target'];sx,sy,sz=packed['sizeXYZ']
  exported=np.array(packed['palette'])[packed['blocks']].reshape(sy,sz,sx)
  assert exported.shape==a.shape and np.array_equal(exported,np.vectorize(canonical)(a)), 'Existing North runtime target differs from frozen P2'
  assert row['sha256']==f['sha256'];metadata['north',tier]={'id':'north_sector_p2_'+tier,'front':'north','variant':tier,'file':f['file'],'sha256':f['sha256'],'worldMinXYZ':[-839,81,-853],'worldPasteAnchorXYZ':[-750,81,-838],'localAnchorXYZ':[89,0,15],'sizeXYZ':[179,9,20]}
 for side in ['north','east','south','west']:
  a,b=arrays[side,'basic'],arrays[side,'reinforced'];assert a.shape==b.shape;lo=np.array(metadata[side,'basic']['worldMinXYZ']);union={}
  for y,z,x in np.argwhere((a!=AIR)|(b!=AIR)):union[xyz(lo+[x,y,z])]=(canonical(a[y,z,x]),canonical(b[y,z,x]))
  ownership[side]=rows(union)
  for tier in ['basic','reinforced']:
   row=metadata[side,tier];array=arrays[side,tier];fresh={};reads={}
   for y,z,x in np.argwhere(array!=AIR):
    world=xyz(lo+[x,y,z]);assert source(world)==AIR;fresh[world]=(AIR,canonical(array[y,z,x]))
   def add_read(world,state=None):
    world=xyz(world)
    if world in union:return
    state=source(world) if state is None else canonical(state)
    assert source(world)==state
    if world in reads:assert reads[world]==state
    reads[world]=state
   route=[];footprint=sorted({(p[0],p[2]) for p in fresh})
   if side!='north':
    sr=np.load(root/row['sourceRead'],allow_pickle=False)
    for point,state in zip(sr['worldXYZ'],sr['expectedState']):assert source(point)==canonical(state);add_read(point,state)
    route=np.load(root/row['route'],allow_pickle=False)['worldXYZ'].tolist()
    for point in route:add_read(point)
    for stair in row['stairs']:
     for x,y,z in stair['cellsWorldXYZ']:
      for yy in range(y,y+4):add_read([x,yy,z])
    for socket in row['sockets']:
     for name in ['weaponLocalHalfOpen','operatorLocalHalfOpen','approachLocalHalfOpen']:
      box=socket[name]
      for x in range(box['min'][0],box['max'][0]):
       for y in range(box['min'][1],box['max'][1]):
        for z in range(box['min'][2],box['max'][2]):add_read(lo+[x,y,z])
    fp=np.load(root/f'contracts/{row["id"]}_footprint.npz',allow_pickle=False)
    assert set(map(xyz,fp['worldXZ']))==set(footprint)
   else:
    packed=p2['readContext'];sx,sy,sz=packed['sizeXYZ'];readarray=np.array(packed['palette'])[packed['blocks']].reshape(sy,sz,sx);readbase=np.array(p2['readWorldMinXYZ'])
    for y,z,x in np.ndindex(readarray.shape):add_read(readbase+[x,y,z],readarray[y,z,x])
    for read in p2['primaryRoadReads']:add_read(lo+read['localXYZ'],read['state'])
    for read in next(v for v in p2['variants'] if v['variant']==tier)['supports']:add_read(lo+read['localXYZ'],read['state'])
   result={k:row[k] for k in ['id','variant','worldMinXYZ','worldPasteAnchorXYZ','localAnchorXYZ','sizeXYZ']};result.update(side=side,schematicSHA256=row['sha256'],fresh=rows(fresh),reads=rows(reads),routeWorldXYZ=route,footprintWorldXZ=[list(p) for p in footprint]);assets.append(result);newpoints[side,tier]=set(fresh)
 # Canonicalized side-owned corner cells. Prove every overlay against the immutable source tier.
 for connection in corners['connections']:
  assert len(connection['changes'])==12
  for c in connection['changes']:
   side=c['cellOwner'];tier=connection['ownerTier'] if side==connection['transactionOwner'] else connection['neighborTier'];lo=np.array(metadata[side,tier]['worldMinXYZ']);x,y,z=np.array(c['worldXYZ'])-lo
   assert canonical(arrays[side,tier][y,z,x])==canonical(c['before']) and c['after']==AIR
   c['before']=canonical(c['before']);c['after']=canonical(c['after'])
 def opened(side,tier,open_corners):
  a=arrays[side,tier].copy();lo=np.array(metadata[side,tier]['worldMinXYZ']);changed=set()
  for corner in open_corners:
   matches=[c for c in corners['connections'] if c['corner']==corner and ((c['transactionOwner']==side and c['ownerTier']==tier) or (c['neighbor']==side and c['neighborTier']==tier))]
   assert matches
   edits=[c for c in matches[0]['changes'] if c['cellOwner']==side];assert len(edits)==6
   for e in edits:
    x,y,z=np.array(e['worldXYZ'])-lo;assert canonical(a[y,z,x])==e['before'];a[y,z,x]=AIR;changed.add(tuple(e['worldXYZ']))
  assert len(changed)==6*len(open_corners);return a
 upgrades=[];modes=load('contracts/supported-upgrade-modes.json')['modes'];assert len(modes)==12
 for mode in modes:
  side=mode['sector'][2:].lower();lo=np.array(metadata[side,'basic']['worldMinXYZ']);before=opened(side,'basic',mode['openCorners']);after=opened(side,'reinforced',mode['openCorners'])
  assert raw_hash(before)==mode['canonicalBasicAssetStateHash'] and raw_hash(after)==mode['canonicalReinforcedAssetStateHash']
  guard={xyz(r[:3]):(canonical(before[r[1]-lo[1],r[2]-lo[2],r[0]-lo[0]]),canonical(after[r[1]-lo[1],r[2]-lo[2],r[0]-lo[0]])) for r in ownership[side]}
  changes={p:s for p,s in guard.items() if s[0]!=s[1]};npz=np.load(root/mode['delta'],allow_pickle=False)
  supplied={xyz(p+lo):(canonical(b),canonical(a)) for p,b,a in zip(npz['localXYZ'],npz['before'],npz['after'])};suppliedguard={xyz(p+lo):(canonical(b),canonical(a)) for p,b,a in zip(npz['guardLocalXYZ'],npz['guardBefore'],npz['guardAfter'])}
  assert supplied==changes and suppliedguard==guard and len(supplied)==len(npz['localXYZ'])==mode['changes'] and len(guard)==len(npz['guardLocalXYZ'])==mode['guardCells']
  upgrades.append({'side':side,'openCorners':mode['openCorners'],'changes':rows(changes),'guards':rows(guard),'derived':False})
 for flags in itertools.product([False,True],repeat=2):
  opens=[c for c,f in zip(['NW','NE'],flags) if f];side='north';lo=np.array(metadata[side,'basic']['worldMinXYZ']);before=opened(side,'basic',opens);after=opened(side,'reinforced',opens)
  guard={xyz(r[:3]):(canonical(before[r[1]-lo[1],r[2]-lo[2],r[0]-lo[0]]),canonical(after[r[1]-lo[1],r[2]-lo[2],r[0]-lo[0]])) for r in ownership[side]};changes={p:s for p,s in guard.items() if s[0]!=s[1]}
  assert len(changes)==4539-6*len(opens)
  upgrades.append({'side':side,'openCorners':opens,'changes':rows(changes),'guards':rows(guard),'derived':True})
 runtime=reconcile(runtime_root,root,newpoints)
 data={'schemaVersion':1,'revision':'CITY_A_P3','city':'a','coordinateConvention':'world XYZ; rows x,y,z,before,after; reads x,y,z,state; no further rotation','archiveSHA256':ARCHIVE_SHA256,'sourceMemberSHA256':pins,'manifest':manifest,'assets':assets,'ownership':ownership,'upgrades':upgrades,'corners':corners,'northPins':{'archiveSHA256':P2_SHA256,'files':north['immutableFiles'],'runtimeResourceSHA256':sha(p2path.read_bytes()),'transitions':north['transitions'],'preservedEndPierCaps':p2['preservedEndPierCaps']},'runtimeAudit':runtime,'emplacements':load('contracts/emplacement-reservations.json'),'scope':'Owner approved City A sparse footprints only. Historical source prose is retained. No other cities, rotations, functional weapons, NPCs or terrain rollout. Export validation is not native acceptance.'}
 raw=json.dumps(data,sort_keys=True,separators=(',',':')).encode();compressed=gzip.compress(raw,mtime=0);output=Path(output);output.parent.mkdir(parents=True,exist_ok=True);output.write_bytes(compressed)
 print(json.dumps({'output':str(output),'sha256':sha(compressed),'jsonSHA256':sha(raw),'assets':len(assets),'upgrades':len(upgrades),'counts':{a['id']:len(a['fresh']) for a in assets}}));return data

def reconcile(runtime_root,root,points):
 registry=runtime_root/'src/main/java/com/quin/catancraft/world/SchematicAssetRegistry.java';text=registry.read_text();envelopes=[]
 pattern=r'new Asset\(\s*"([^"]+)"\s*,\s*"[^"]+"\s*,\s*"[^"]+"\s*,\s*\d+\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*true\s*,\s*Set.of\(0, 180\)\s*,\s*(-?\d+)\s*,\s*(-?\d+)\s*\)'
 expected={'th1','th2','th3','th4','th5','vehicle_factory','quarry','weapons_factory','steel_mill','farm','lumberyard','iron_oil_site','coal_copper_site','textile_mill','fuel_refinery','concrete_plant','machine_shop','chemical_plant','electronics_factory'}
 for m in re.finditer(pattern,text):
  if m[1] not in expected:continue
  w,h,d,ax,ay,az,ox,oz=map(int,m.groups()[1:]);lo=[-750+ox-ax,81-ay,-750+oz-az];hi=[lo[0]+w-1,lo[1]+h-1,lo[2]+d-1]
  assert not any(all(lo[i]<=p[i]<=hi[i] for i in range(3)) for ps in points.values() for p in ps),m[1]
  envelopes.append({'id':m[1],'worldMinInclusive':lo,'worldMaxInclusive':hi})
 assert {e['id'] for e in envelopes}==expected
 masks=np.load(root/'reference/authoring-road-water-masks.npz',allow_pickle=False);road=masks['road_mask'];margin=road.copy()
 for _ in range(2):
  old=margin.copy();margin[1:]|=old[:-1];margin[:-1]|=old[1:];margin[:,1:]|=old[:,:-1];margin[:,:-1]|=old[:,1:]
 for (side,tier),ps in points.items():
  for x,y,z in ps:
   assert not road[z+1024,x+1024] and not margin[z+1024,x+1024],(side,'road',x,y,z)
   assert not(-830<=x<-669 and -833<=z<-666),(side,'core')
   assert not(-770<=x<-729 and -666<=z<-653),(side,'rear corridor')
 legacy_path=runtime_root/'src/main/resources/assets/catancraft/defense/contracts.json.gz';legacy=json.loads(gzip.decompress(legacy_path.read_bytes()));overlaps=[]
 for row in legacy:
  if row['city']!='a':continue
  writes={xyz(p[:3]) for p in row['writes']};hits={side+'_'+tier:len(ps&writes) for (side,tier),ps in points.items()}
  if any(hits.values()):overlaps.append({'socket':row['socket'],'module':row['module'],'overlappingWriteCells':hits})
 return {'registrySHA256':sha(registry.read_bytes()),'envelopes':envelopes,'legacyDefenseResourceSHA256':sha(legacy_path.read_bytes()),'legacyWriteOverlaps':overlaps,'policy':'Refuse legacy purchased ownership/physical conflicts, never migrate or erase old modules.'}
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--input',type=Path,required=True);p.add_argument('--runtime-root',type=Path,required=True);p.add_argument('--archive',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();run(a.input,a.output,a.runtime_root,a.archive)
