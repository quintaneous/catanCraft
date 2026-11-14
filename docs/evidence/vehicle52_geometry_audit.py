from pathlib import Path
import base64,zipfile,io,gzip,json,sys,struct,zlib,math,collections,hashlib
ROOT=Path('/workspace/scratch/a1b6a42f71e7'); REPO=ROOT/'catanCraft'; INPUT=ROOT/'fresh-baseline-verified/CatanCraft_Clean_R7_Input'; WORLD=INPUT/'source_world/CatanCraft_Season1_R7_Development'
sys.path.insert(0,str(INPUT));from nbt_check import NBT
z=zipfile.ZipFile(io.BytesIO(base64.b64decode((REPO/'src/main/resources/data/catancraft/basic_construction/assets.b64').read_bytes())))
def schem(name):
 raw=z.read(name+'.schem'); d=NBT(gzip.decompress(raw)).root();p={v:k for k,v in d['Palette'].items()};a=[];v=sh=0
 for b in d['BlockData']:
  v|=(b&127)<<sh
  if b&128:sh+=7
  else:a.append(p[v]);v=sh=0
 return d,a,hashlib.sha256(raw).hexdigest()
base,ba,bsha=schem('starter_settlement');factory,fa,fsha=schem('vehicle_factory')
def block(d,a,x,y,z):
 if not 0<=x<d['Width'] or not 0<=y<d['Height'] or not 0<=z<d['Length']:return 'OUTSIDE'
 return a[(y*d['Length']+z)*d['Width']+x]
def parsed(s):
 n,_,p=s.partition('[');v=dict(x.split('=') for x in p.rstrip(']').split(',') if x);return n,v
flip={'north':'south','south':'north','east':'west','west':'east'}
def rotated(s,rot):
 n,p=parsed(s)
 if rot:
  p={flip.get(k,k):flip.get(v,v) if k=='facing' else str((int(v)+8)%16) if k=='rotation' else v for k,v in p.items()}
 return n,p
def same(expected,actual):return expected[0]==actual[0] and all(actual[1].get(k)==v for k,v in expected[1].items())
regions={};chunks={}
def chunk(cx,cz):
 key=cx,cz
 if key in chunks:return chunks[key]
 rp=WORLD/'region'/f'r.{cx//32}.{cz//32}.mca'
 if rp not in regions:regions[rp]=rp.read_bytes()
 raw=regions[rp];i=((cx%32)+(cz%32)*32)*4;off=int.from_bytes(raw[i:i+3],'big')*4096
 assert off;length=int.from_bytes(raw[off:off+4],'big');assert raw[off+4]==2
 d=NBT(zlib.decompress(raw[off+5:off+4+length])).root();sections={}
 for sec in d['sections']:
  if 'block_states' not in sec:continue
  b=sec['block_states'];pal=[(p['Name'],p.get('Properties',{})) for p in b['palette']];data=b.get('data');longs=[] if data is None else list(struct.unpack('>'+str(len(data)//8)+'Q',data));sections[sec['Y']]=(pal,longs)
 chunks[key]=sections;return sections
def native(x,y,z):
 sec=chunk(x//16,z//16).get(y//16)
 if sec is None:return ('minecraft:air',{})
 pal,longs=sec
 if len(pal)==1:return pal[0]
 bits=max(4,(len(pal)-1).bit_length());per=64//bits;i=(y%16)*256+(z%16)*16+x%16;index=(longs[i//per]>>((i%per)*bits))&((1<<bits)-1);return pal[index]
r5=json.loads(gzip.decompress(base64.b64decode((REPO/'src/main/resources/data/catancraft/r5_public/contract.b64').read_bytes())))
citymap=json.loads((REPO/'src/main/resources/data/catancraft/maps/season1_river_bridges_v3.json').read_bytes())
rows=[]
for city in citymap['territories']:
 c=city['cityCenter'];cs=1 if c['rotation']==0 else -1
 r5w={(c['x']+cs*(x-80),c['y']+y-2,c['z']+cs*(zz-84)):rotated(r5['palette'][index],c['rotation']) for x,y,zz,index in r5['writes']}
 for plot,dx,dz,extra in [('plot_1',-45,-24,False),('plot_3',-45,16,False),('plot_4',45,16,True)]:
  a=(c['x']+cs*dx,81,c['z']+cs*dz);rot=(c['rotation']+(180 if extra else 0))%360;s=1 if rot==0 else -1
  start=(c['x'],81,c['z']-83*cs)
  outside=[];mismatches=[];r5overlays=[];roadtypes=collections.Counter();roadobstructions=[];blockEntities=[]
  def expected(lx,ly,lz):
   wx,wy,wz=a[0]+s*lx,a[1]+ly,a[2]+s*lz
   if -29<=lx<=1 and -15<=lz<=15:return rotated(block(factory,fa,lx+29,ly+2,lz+15),rot)
   return rotated(block(base,ba,cs*(wx-start[0])+80,wy-start[1]+2,cs*(wz-start[2])+1),c['rotation'])
  for lx in range(-29,11):
   for ly in range(-2,8):
    for lz in range(-22,24):
     p=(a[0]+s*lx,a[1]+ly,a[2]+s*lz);e=expected(lx,ly,lz)
     if e[0]=='OUTSIDE':outside.append(p)
     if p in r5w and not same(e,r5w[p]):r5overlays.append({'local':[lx,ly,lz],'position':p,'expected':e,'after':r5w[p]})
     if not (-29<=lx<=1 and -15<=lz<=15):
      n=native(*p)
      if not same(e,n):mismatches.append({'local':[lx,ly,lz],'position':p,'expected':e,'actual':n})
  for lx in range(2,8):
   for lz in range(-10,10):
    roadtypes[expected(lx,-1,lz)[0]]+=1
    for ly in range(0,5):
     if expected(lx,ly,lz)[0]!='minecraft:air':roadobstructions.append([lx,ly,lz,expected(lx,ly,lz)])
  rows.append({'city':city['id'],'startsWithSettlement':city['startsWithSettlement'],'plot':plot,'anchor':[*a,rot],'envelopeCells':18400,'outsideBaseline':len(outside),'r5OverlayDifferences':len(r5overlays),'r5Samples':r5overlays[:12],'rawR7OutsideFactoryMismatchCount':len(mismatches),'rawR7MismatchSamples':mismatches[:8],'roadFloorTypes':dict(roadtypes),'roadAirObstructions':roadobstructions})
report={'source':'6409925b7356f8a3aecfc0bedfa621ce8624dd5b','method':'Read-only palette inspection, explicit-property subset comparisons; native collision/state-default checks are not inferred. Factory future placement overlay is expected; R7 comparison covers outside factory only.','starterSHA256':bsha,'factorySHA256':fsha,'rows':rows}
manifestPath=INPUT/'INPUT_MANIFEST.json'; manifest=json.loads(manifestPath.read_text()); checked=[]
for entry in manifest['files']:
 if entry['path'].startswith('source_world/'):
  file=INPUT/entry['path']; actual=hashlib.sha256(file.read_bytes()).hexdigest()
  assert actual==entry['sha256'] and file.stat().st_size==entry['bytes']; checked.append({'path':entry['path'],'sha256':actual,'bytes':entry['bytes']})
report.update({'inputManifestSHA256':hashlib.sha256(manifestPath.read_bytes()).hexdigest(),'worldFilesVerifiedUnchanged':checked,'chunksDecoded':len(chunks),'regionsRead':len(regions),'r5RawContractSHA256':hashlib.sha256(gzip.decompress(base64.b64decode((REPO/'src/main/resources/data/catancraft/r5_public/contract.b64').read_bytes()))).hexdigest(),'notes':['No production files, source world or installed mods modified. No native runtime, connected-client or full campaign pass is inferred.','Nonstarting cities have120/93/96 outsidefactory raw R7 differences at plots1/3/4. Normal founding pastes the complete starter_settlement before factory purchases; pre-founding raw terrain is not authority for a completedcity.','All48 actual road service rectangles contain600 AIR cells above120 gray-concrete cells. These static shapes still require native vehicle collision/drive qualification.','Each normally prepared city overlays165/160/165 R5 cells within the chosen envelope. They lie X[-29,1],Y[-1,4],Z[17,23] forplots1/4 orZ[-22,-17] forplot3; none touch service lane or roadside X[2,8),Z[-21,22].','Static baseline mismatches versus blindly translated CityAplot1 outsidefactory: plot1=0,plot3=498,plot4=141. Native exact property defaults and source identity remain separate checks.']})
path=ROOT/'vehicle52_geometry_audit.json';path.write_text(json.dumps(report,indent=2)+'\n')
print('wrote',path,'rows',len(rows),'chunksRead',len(chunks),'regionsRead',len(regions))
for r in rows:print(r['city'],r['plot'],'start',r['startsWithSettlement'],'outside',r['outsideBaseline'],'r5',r['r5OverlayDifferences'],'R7diff',r['rawR7OutsideFactoryMismatchCount'],'roadobstructions',len(r['roadAirObstructions']),'floor',r['roadFloorTypes'])
