"""Independent, read-only comparison of the saved Pine Reach pilot with its R7 source."""
from collections import Counter, defaultdict
from functools import lru_cache
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import re
import struct
import sys
import zlib

import numpy as np

sys.dont_write_bytecode = True
HERE=Path(__file__).resolve().parent
WORK=HERE.parents[1]
SOURCE=WORK/'inputs/CatanCraft_Clean_R7_Input'
sys.path.insert(0,str(SOURCE))
from nbt_check import NBT

ORIGINAL='e7ecd5d0f0ac5c1457be5c234d2e5e08c9239007787b20e0bdc45b3e2b8d862d'
AIR='minecraft:air'
WATER='minecraft:water[level=0]'
PILOT='CatanCraft_Pine_Reach_Brinewatch'
R7=SOURCE/'source_world/CatanCraft_Season1_R7_Development'

def check(c,msg):
    if not c: raise ValueError(msg)

def sha(raw):
    return hashlib.sha256(raw).hexdigest()

def read(p):
    return json.loads(p.read_bytes())

def state(row):
    props=row.get('Properties',{})
    suffix=','.join(f'{k}={v}' for k,v in sorted(props.items()))
    return row['Name']+('['+suffix+']' if suffix else '')

class Reader:
    def __init__(self,world):
        self.world=world
        self.locations={}
        self.region_hash={}
        self.metadata={}
        for path in sorted((world/'region').glob('*.mca')):
            m=re.fullmatch(r'r\.(-?\d+)\.(-?\d+)\.mca',path.name)
            check(m,'Invalid region filename')
            rx,rz=map(int,m.groups())
            raw=path.read_bytes()
            check(len(raw)%4096==0,'Unaligned region file')
            self.region_hash[path.name]=sha(raw)
            allocation={0,1}
            for i in range(1024):
                addr=int.from_bytes(raw[i*4:i*4+3],'big'); n=raw[i*4+3]
                check(bool(addr)==bool(n),'Invalid region allocation')
                if not n: continue
                sectors=set(range(addr,addr+n))
                check(not allocation & sectors and (addr+n)*4096<=len(raw),'Overlapping/truncated chunk allocation')
                allocation.update(sectors)
                offset=addr*4096
                length=struct.unpack_from('>I',raw,offset)[0]
                check(2<=length<=4096*n-4 and raw[offset+4]==2,'Invalid compressed chunk')
                payload=raw[offset+5:offset+4+length]
                coord=(rx*32+i%32,rz*32+i//32)
                self.locations[coord]=(path,offset+5,len(payload),sha(payload))
        self.doc=lru_cache(maxsize=48)(self._doc)
        self.sec=lru_cache(maxsize=580)(self._sec)

    def _doc(self,cx,cz):
        loc=self.locations.get((cx,cz))
        check(loc,f'Missing chunk at {(cx,cz)}')
        path,offset,n,_=loc
        with path.open('rb') as stream:
            stream.seek(offset)
            d=NBT(zlib.decompress(stream.read(n))).root()
        check(d['xPos']==cx and d['zPos']==cz and d['DataVersion']==3465 and d['Status'].removeprefix('minecraft:')=='full',f'Wrong chunk {(cx,cz)}')
        return d

    def _sec(self,cx,cz,sy):
        d=self.doc(cx,cz)
        bs=next((s.get('block_states') for s in d['sections'] if s['Y']==sy),None)
        if not bs: return (AIR,),None
        names=tuple(map(state,bs['palette']))
        if len(names)==1: return names,None
        bits=max(4,(len(names)-1).bit_length())
        n=64//bits
        words=np.frombuffer(bs['data'],dtype='>u8').astype(np.uint64)
        check(len(words)==(4096+n-1)//n,'Wrong packed section size')
        shifts=np.arange(n,dtype=np.uint64)*bits
        indices=((words[:,None] >> shifts) & ((1<<bits)-1)).ravel()[:4096].astype(np.uint16)
        check(int(indices.max())<len(names),'Invalid local palette index')
        return names,indices

    def at(self,x,y,z):
        names,ids=self.sec(x//16,z//16,y//16)
        return names[0] if ids is None else names[ids[(y%16)*256+(z%16)*16+x%16]]

def check_inputs(original):
    p=SOURCE/'INPUT_MANIFEST.json'
    check(sha(p.read_bytes())==ORIGINAL,'Wrong pristine R7 manifest')
    for row in read(p)['files']:
        path=SOURCE/row['path']
        check(path.stat().st_size==row['bytes'] and sha(path.read_bytes())==row['sha256'],f"Changed pristine input: {row['path']}")
    check(set(original.locations)=={(x,z) for x in range(-64,64) for z in range(-64,64)},'R7 mainland extent differs')

def road_and_river(r, plan):
    road=plan['roadCenterXZ']
    river=plan['riverCenterXZ']
    check(road[0]==[-400,-568] and road[-1]==[-16,-561] and len(road)==385,'Road endpoints')
    check(river[0]==[-16,-570] and river[-1][1]==-1024 and len(river)==455,'River endpoints')
    for (x,z),(u,v) in zip(road,road[1:]):
        check(u-x==1 and abs(v-z)<=1,'Road center gap')
    for (x,z),(u,v) in zip(river,river[1:]):
        check(z-v==1 and abs(u-x)<=1,'River center gap')
    for x,z in road:
        for zz in range(z-2,z+3):
            check(r.at(x,80,zz) in {'minecraft:gravel','minecraft:andesite','minecraft:stone_bricks'},f'Road floor at {(x,zz)}')
            check(all(r.at(x,y,zz)==AIR for y in range(81,84)),f'Road clearance at {(x,zz)}')
    for x,z in river:
        for xx in range(x-3,x+5):
            water(r,xx,z,3,4)
    return {'roadRows':len(road),'roadWidth':5,'roadAir':3,'riverRows':len(river),'riverWidth':8,'riverDepth':3,'riverAir':4}

def water(r,x,z,depth,head):
    for y in range(76-depth,76):
        got=r.at(x,y,z)
        check(got in (WATER,'minecraft:water'),f'Source water at {(x,y,z)} instead found {got}')
    for y in range(76,76+head):
        got=r.at(x,y,z)
        check(got==AIR,f'Boat air at {(x,y,z)} instead found {got}')

def actual_diff(old,new):
    missing=set(old.locations)-set(new.locations)
    check(not missing,f'Missing {len(missing)} original chunks')
    changed=sorted(c for c in old.locations if old.locations[c][-1]!=new.locations[c][-1])
    differences={}
    meta_keys=set()
    for cx,cz in changed:
        a=old.doc(cx,cz); b=new.doc(cx,cz)
        for k in ('block_entities','block_ticks','fluid_ticks','structures','InhabitedTime'):
            check(a.get(k)==b.get(k),f'Original gameplay metadata changed: {(cx,cz)} {k}')
        meta_keys.update(k for k in set(a)|set(b) if k!='sections' and a.get(k)!=b.get(k))
        amap={s['Y']:s for s in a['sections']}
        bmap={s['Y']:s for s in b['sections']}
        for sy in set(amap)|set(bmap):
            ss=amap.get(sy,{});tt=bmap.get(sy,{})
            check(ss.get('biomes')==tt.get('biomes'),f'Original biome difference {(cx,sy,cz)}')
            if ss.get('block_states')==tt.get('block_states'):continue
            an,ai=old.sec(cx,cz,sy);bn,bi=new.sec(cx,cz,sy)
            keys={v:k for k,v in enumerate(set(an)|set(bn))}
            def values(names, ids):
                if ids is None:return np.full(4096,keys[names[0]],dtype=np.int32)
                return np.array([keys[name] for name in names])[ids]
            for i in np.flatnonzero(values(an,ai)!=values(bn,bi)):
                x,y,z=cx*16+int(i)%16,sy*16+int(i)//256,cz*16+(int(i)//16)%16
                differences[x,y,z]=(an[ai[i]] if ai is not None else an[0],bn[bi[i]] if bi is not None else bn[0])
    return changed,differences,sorted(meta_keys)

def protected(diff):
    config=read(SOURCE/'bootstrap_reference/config/catancraft/map.json')
    cols={(x,z) for x,y,z in diff}
    archetype=WORK/'resource-ui/src/main/resources/assets/catancraft/sector_p3/city_a.json.gz'
    template=json.loads(gzip.decompress(archetype.read_bytes()))
    guard_xz={(r[0]+750,r[2]+750) for asset in template['assets'] for k in ('reads','fresh') for r in asset[k]}
    for t in config['territories']:
        for key in ('siegeRegion','restorationRegion'):
            box=t[key]
            check(not any(box['minX']<=x<=box['maxX'] and box['minZ']<=z<=box['maxZ'] for x,z in cols),f'{key} touched in '+t['id'])
        c=t['cityCenter'];sd=1 if c['rotation']==0 else -1
        check(not any((sd*(x-c['x']),sd*(z-c['z'])) in guard_xz for x,z in cols),'Wall guard touched in '+t['id'])
        anchors=[c,t['managementAnchor'],*t['buildingPlots']]
        check(not any((a['x'],a['z']) in cols for a in anchors),'City anchor touched')
    for p in config['infrastructure']:
        check((p['x'],p['z']) not in cols,'Bridge anchor changed')
    check(not any(-150<=x<=150 and -150<=z<=150 for x,z in cols),'Central court changed')
    return {'territories':len(config['territories']),'siegeAndRestorationBoxes':32,'wallGuardCityTransforms':16,'plotAnchors':96,'bridgeAnchors':4,'courtColumnsChanged':0}

EXTENTS=[(-1568,-1408,224,-1024),(-1568,-1024,-1024,-224)]
def manifest_diff():
    raw=(HERE/'mainland-placements.json.gz').read_bytes()
    check(sha(raw)=='55d02bdca7de2f42d639fe87a65864ce8cbdee21f2ddbcc22acc015076d27103','Unreviewed mainland delta')
    data=json.loads(gzip.decompress(raw))
    out={}
    for x,z,spans in data['columns']:
        check(-395<=x<=-106 and -644<=z<=-500 and not -598<=z<=-546,'Outside proposed woodland envelope')
        for low,high,a,b in spans:
            for y in range(low,high+1):
                coord=(x,y,z); check(coord not in out,'Duplicate delta')
                out[coord]=(data['palette'][a],data['palette'][b])
    return out

def segment(r,a,b,width,depth,head):
    x,z=a; u,v=b; dx=(u>x)-(u<x); dz=(v>z)-(v<z)
    check(bool(dx)!=bool(dz),'Expected cardinal boat route')
    step=max(abs(u-x),abs(v-z)); low=-(width//2)
    for t in range(step+1):
        xx=x+dx*t;zz=z+dz*t
        for k in range(low,low+width):
            water(r,xx+(k if dz else 0),zz+(k if dx else 0),depth,head)
    return {'fromXZ':a,'toXZ':b,'centers':step+1,'width':width,'depth':depth,'air':head}

def square(r,x,z,width,depth,head):
    for xx in range(x-width//2,x-width//2+width):
        for zz in range(z-width//2,z-width//2+width):
            water(r,xx,zz,depth,head)

def berth(r):
    selected=[(-1385,76,-431),(-1419,76,-452)]
    for x,y,z in selected:
        check(r.at(x,y,z)=='minecraft:spruce_planks',f'Boarding lip {(x,y,z)}: {r.at(x,y,z)}')
        check(all(r.at(x,yy,z)==AIR for yy in (y+1,y+2)),f'Blocked landing {(x,y,z)}')
    check(r.at(-1409,81,-434)=='minecraft:cartography_table','Chart marker absent')
    return {'westPilotRotation':180,'eastPierLandingBlockXYZ':selected[0],'northPierLandingBlockXYZ':selected[1],
            'chartMarkerBlockXYZ':[-1409,81,-434]}

def verify_lighthouse(r):
    archive=WORK/'receipts/ocean-v2/lighthouse/Brinewatch_Pilot_Station.schem'
    raw=archive.read_bytes()
    check(sha(raw)=='ae9e82291ee2af4e660a23e9da0cf6240edea10903e7358f146cdf3f1a2a0bbb','Unexpected Brinewatch source')
    d=NBT(gzip.decompress(raw)).root()
    w,h,l=[d[k] for k in ('Width','Height','Length')]
    check((w,h,l)==(55,50,47) and len(d['BlockData'])==w*h*l,'Unexpected lighthouse volume')
    def rotate(s):
        if '[' not in s:return s
        name,props=s[:-1].split('[',1)
        opts=dict(p.split('=') for p in props.split(','))
        opposite={'north':'south','east':'west','south':'north','west':'east'}
        turn={opposite.get(k,k):opposite.get(v,v) if k=='facing' else v for k,v in opts.items()}
        return state({'Name':name,'Properties':turn})
    lookup={v:rotate(k) for k,v in d['Palette'].items()}
    checked=0
    for i,val in enumerate(d['BlockData']):
        want=lookup[val]
        if want==AIR: continue
        x,y,z=i%w,i//(w*l),(i//w)%l
        dest=(-1410-(x-27),67+y,-430-(z-23))
        got=r.at(*dest)
        check(got==want,f'Lighthouse has {got}, expected {want} at {dest}')
        checked+=1
    check(checked==15430,'Unexpected solid block count')
    return {'serializedInputSHA256':sha(raw),'matchedNonAirBlocksIncludingOrientedStates':checked}

def sea_route(r):
    # Use exact consecutive widths across straight legs. Turns also require full square clearance.
    legs=[((44,-1025),(44,-1072),8,3,4),((44,-1073),(44,-1220),8,3,4),
          ((44,-1220),(-1220,-1220),32,8,4),((-1220,-1220),(-1220,-430),32,8,4),
          ((-1220,-430),(-1362,-430),32,6,4),((-1362,-430),(-1383,-430),3,3,2),
          ((-1383,-430),(-1383,-431),3,3,2),
          ((-1362,-430),(-1362,-464),3,3,2),((-1362,-464),(-1419,-464),3,3,2),
          ((-1419,-464),(-1419,-454),3,3,2)]
    out=[segment(r,*s) for s in legs]
    for x,z,width,depth in [(-1220,-1220,32,8),(-1220,-430,32,6),(-1362,-430,3,3),
                            (-1383,-431,3,3),(-1362,-464,3,3),(-1419,-464,3,3),(-1419,-454,3,3)]:
        square(r,x,z,width,depth,2)
    doc=read(HERE/'ocean.json')
    check([list(s) for s in EXTENTS]==doc['newChunkRectanglesHalfOpenXZ'],'Exterior bounds differ')
    check(doc['primaryRouteXZ']==[[44,-1025],[44,-1220],[-1220,-1220],[-1220,-430],[-1362,-430],[-1383,-430],[-1383,-431]],'Unexpected lighthouse approach')
    check(doc['alternateRouteXZ']==[[-1362,-430],[-1362,-464],[-1419,-464],[-1419,-454]],'Unexpected alternate berth approach')
    full=[]
    unique_columns=set()
    for row in doc['channelProof']:
        x0,z0,x1,z1=row['boundsXZ']; target=row['minimumRequired']
        for x in range(x0,x1):
            for z in range(z0,z1):
                water(r,x,z,target,4)
                unique_columns.add((x,z))
        full.append({'name':row['name'],'width':row['width'],'minimumVerifiedDepth':target,'air':4,'columnsTested':(x1-x0)*(z1-z0)})
    return {'routeLegs':out,'fullLaneRectangles':full,'distinctFullBandWaterColumns':len(unique_columns)}

def seam(old,new):
    def profile(r,x,z):
        water_top=None
        for y in range(140,50,-1):
            name=r.at(x,y,z).split('[')[0].removeprefix('minecraft:')
            if name in {'air','cave_air','void_air'}: continue
            if name=='water':
                if water_top is None:water_top=y
                continue
            return y,name,water_top
        raise ValueError('Missing seam floor')
    result={}
    for key,region in [('north',range(-1024,224)),('west',range(-1024,-224))]:
        heights=[];drywet=Counter();materials=Counter();changes=[]
        for pos in region:
            a=(pos,-1024) if key=='north' else (-1024,pos)
            b=(pos,-1025) if key=='north' else (-1025,pos)
            y0,m0,w0=profile(old,*a);y1,m1,w1=profile(new,*b)
            heights.append(abs(y1-y0));drywet[w0==w1]+=1;materials[m0==m1]+=1
            if y0!=y1 or w0!=w1 or m0!=m1: changes.append([pos,y0,y1,m0,m1,w0,w1])
        result[key]={'columns':len(heights),'maxSurfaceDifference':max(heights),
                     'sameWaterline':drywet[True],'sameSurfaceMaterial':materials[True],
                     'changedExamples':changes[:5]}
        check(max(heights)<=1 and drywet[True]==len(heights),f'Bad shoreline seam {key}: '+str(result[key]))
    return result

def verify(world,plan_file):
    old=Reader(R7); new=Reader(world)
    check_inputs(old)
    add=set(new.locations)-set(old.locations)
    expected={(x,z) for x0,z0,x1,z1 in EXTENTS for x in range(x0//16,x1//16) for z in range(z0//16,z1//16)}
    check(add==expected,f'Exterior additions differ: {len(add-expected)} extra, {len(expected-add)} missing')
    new_region_names={f'r.{x//32}.{z//32}.mca' for x,z in expected}
    check(set(new.region_hash)==set(old.region_hash)|new_region_names,'Unexpected or missing region file')
    changed,delta,keys=actual_diff(old,new)
    authored=manifest_diff()
    if delta!=authored:
        left=set(delta)-set(authored);right=set(authored)-set(delta)
        wrong=[p for p in set(delta)&set(authored) if delta[p]!=authored[p]]
        raise ValueError(f'Mainland changes disagree: extra={len(left)}, missing={len(right)}, wrong states={len(wrong)}; sample={list(left)[:1]}, {list(right)[:1]}, {[(p,delta[p],authored[p]) for p in wrong[:1]]}')
    protection=protected(delta)
    plan=read(plan_file)['survey']
    check(road_and_river(old,plan)==road_and_river(new,plan),'Inland route difference')
    import bank
    bank_ref=read(HERE/'bank-reference.json')
    check(json.loads(json.dumps(bank.survey(world)))==bank_ref,'Road to river transfer differs from original')
    sea=sea_route(new)
    lip=berth(new) | verify_lighthouse(new)
    joins=seam(old,new)
    lvl=NBT(gzip.decompress((world/'level.dat').read_bytes())).root()['Data']
    checks={'BorderCenterX':0,'BorderCenterZ':0,'BorderSize':3500,'SpawnX':-400,'SpawnY':81,'SpawnZ':-568,'GameType':1}
    check(all(lvl.get(k)==v for k,v in checks.items()) and lvl['GameRules']['spawnRadius']=='0',f'Pilot spawn/border: {[(k,lvl.get(k)) for k in checks]}')
    check(not any((world/name).exists() for name in ('playerdata','players','advancements','stats','session.lock')),'Player or session records shipped')
    return {'result':'PASS_OFFLINE_SAVED_WORLD','originalInputManifestSHA256':ORIGINAL,'pristineInputsPinned':40,
            'originalChunkCount':len(old.locations),'changedOriginalChunkPayloads':len(changed),'originalChunkPayloadsByteIdentical':len(old.locations)-len(changed),
            'originalRegionByteEquality':{name:pin==new.region_hash[name] for name,pin in old.region_hash.items()},
            'changedMainlandVoxels':len(delta),'changedMainlandColumns':len({(x,z) for x,y,z in delta}),'originalModifiedChunkMetadataKeys':keys,
            'allowedExteriorExtentsXZHalfOpen':EXTENTS,'newChunks':len(add),'protectedFeatures':protection,'roadRiverReadback':road_and_river(new,plan),
            'roadToRiverTransferMatches':True,'boatLaunchXYZ':bank_ref['launchWaterXYZ'],'launchMinBoatDepth':bank_ref['minimumBoatWaterDepth'],
            'seaLegs':sea,'seam':joins,'island':lip,'worldProfile':checks,'clientOrServerExecuted':False}

if __name__=='__main__':
    cli=argparse.ArgumentParser(description=__doc__)
    cli.add_argument('world',type=Path,nargs='?',default=HERE/'review'/PILOT)
    cli.add_argument('--plan',type=Path,default=WORK/'receipts/mainland-coast-plan/mainland-coast.json')
    args=cli.parse_args()
    print(json.dumps(verify(args.world.resolve(),args.plan),indent=2))
