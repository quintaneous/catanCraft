"""Build an isolated Minecraft Java 1.20.1 R7 coast pilot from the pristine region files.

Python 3, numpy, nbtlib 2.0.4. x/y/z are block coordinates. fill_column is inclusive.
"""
from collections import OrderedDict
import argparse
from functools import lru_cache
import gzip
import hashlib
import io
import json
import math
import os
from pathlib import Path
import shutil
import struct
import sys
import tempfile
import zlib

import numpy as np
HERE=Path(__file__).resolve().parent
WORK=HERE.parents[1]
VENDOR=WORK/'python-tests/CatanCraft_City_A_Sector_Walls_P3/source/vendor'
if VENDOR.is_dir():sys.path.insert(0,str(VENDOR))
import nbtlib as n

VERSION=3465
ORIGINAL='e7ecd5d0f0ac5c1457be5c234d2e5e08c9239007787b20e0bdc45b3e2b8d862d'
LIGHTHOUSE_SHA='ae9e82291ee2af4e660a23e9da0cf6240edea10903e7358f146cdf3f1a2a0bbb'
SOURCE=WORK/'inputs/CatanCraft_Clean_R7_Input'
SCHEM=WORK/'receipts/ocean-v2/lighthouse/Brinewatch_Pilot_Station.schem'
AIR='minecraft:air'
WATER='minecraft:water[level=0]'
EMPTY_LIST=lambda:n.List[n.Compound]([])

@lru_cache(maxsize=None)
def canonical(state):
    name,sep,suffix=state.partition('[')
    if ':' not in name:name='minecraft:'+name
    return name if not sep else name+'['+','.join(sorted(suffix.rstrip(']').split(',')))+']'

def str_state(tag):
    props=tag.get('Properties')
    return canonical(str(tag['Name'])+('['+','.join(f'{k}={v}' for k,v in props.items())+']' if props else ''))

@lru_cache(maxsize=None)
def state_tag(name):
    name,_,props=canonical(name).partition('[')
    result=n.Compound({'Name':n.String(name)})
    if props: result['Properties']=n.Compound({k:n.String(v) for k,v in (s.split('=') for s in props[:-1].split(','))})
    return result

def pack(vals,bits):
    vals=np.asarray(vals,dtype=np.uint64).ravel()
    per=64//bits
    rows=np.pad(vals,(0,(-len(vals))%per)).reshape(-1,per)
    return n.LongArray(np.bitwise_or.reduce(rows << (np.arange(per,dtype=np.uint64)*bits),axis=1).view(np.int64))

def parse_section(s,register):
    bs=s['block_states']
    ids=np.array([register(str_state(t)) for t in bs['palette']],dtype=np.uint16)
    shape=(16,16,16)
    if len(ids)==1:return np.full(shape,ids[0],dtype=np.uint16)
    bits=max(4,(len(ids)-1).bit_length()); per=64//bits
    raw=np.asarray(bs['data'],dtype=np.uint64)
    local=((raw[:,None]>>(np.arange(per,dtype=np.uint64)*bits))&((1<<bits)-1)).ravel()[:4096]
    return ids[local].reshape(shape)

def encode_section(values,names):
    ids,local=np.unique(values,return_inverse=True)
    bs=n.Compound({'palette':n.List[n.Compound]([state_tag(names[i]) for i in ids])})
    if len(ids)>1:bs['data']=pack(local,max(4,(len(ids)-1).bit_length()))
    return bs

def new_chunk(cx,cz):
    sections=n.List[n.Compound]([n.Compound({'Y':n.Byte(y),
        'block_states':n.Compound({'palette':n.List[n.Compound]([state_tag(AIR)])}),
        'biomes':n.Compound({'palette':n.List[n.String]([n.String('minecraft:ocean')])})}) for y in range(-4,20)])
    return n.File({'DataVersion':n.Int(VERSION),'xPos':n.Int(cx),'zPos':n.Int(cz),'yPos':n.Int(-4),'Status':n.String('minecraft:full'),
                   'LastUpdate':n.Long(0),'InhabitedTime':n.Long(0),'sections':sections,'isLightOn':n.Byte(0),'block_ticks':EMPTY_LIST(),
                   'block_entities':EMPTY_LIST(),'fluid_ticks':EMPTY_LIST(),'structures':n.Compound({'starts':n.Compound(),'References':n.Compound()})})

class Chunk:
    def __init__(self,doc,world):
        self.doc=doc
        self.world=world
        self.sections={int(row['Y']):row for row in doc['sections']}
        self.arrays={}
        self.changed=set()

    def array(self,sy):
        if sy not in self.arrays:
            sec=self.sections.get(sy)
            self.arrays[sy]=parse_section(sec,self.world.id) if sec and 'block_states' in sec else np.zeros((16,16,16),np.uint16)
        return self.arrays[sy]

    def payload(self):
        for y in self.changed:
            if y not in self.sections:raise ValueError(f'Missing writable section Y={y}')
            self.sections[y]['block_states']=encode_section(self.arrays[y],self.world.names)
        # Compute WORLD_SURFACE exactly. Omit motion/fluid-policy maps: the FULL chunk
        # loader primes any omitted maps from actual block states, using vanilla rules.
        height=np.zeros((16,16),dtype=np.uint16)
        for sy in sorted(s for s in self.sections if -4<=s<=19):
            arr=self.array(sy); solid=arr!=0; wet=solid.any(axis=0)
            highest=(sy+4)*16+16-solid[::-1].argmax(axis=0)
            height[wet]=highest[wet]
        self.doc['Heightmaps']=n.Compound({'WORLD_SURFACE':pack(height,9)})
        self.doc['isLightOn']=n.Byte(0)
        for s in self.doc['sections']:
            s.pop('BlockLight',None);s.pop('SkyLight',None)
        buf=io.BytesIO();self.doc.write(buf)
        payload=b'\x02'+zlib.compress(buf.getvalue(),3)
        return struct.pack('>I',len(payload))+payload

class Region:
    def __init__(self,path):
        self.path=path; self.original=path.read_bytes() if path.is_file() else bytes(8192)
        if len(self.original)<8192 or len(self.original)%4096:raise ValueError('Malformed region '+str(path))
        self.new={}

    def entry(self,k):
        if k in self.new:return self.new[k]
        off=int.from_bytes(self.original[k*4:k*4+3],'big')*4096
        if not off:return None
        nbytes=int.from_bytes(self.original[off:off+4],'big')+4
        sectors=self.original[k*4+3]
        if off<8192 or nbytes<5 or nbytes>sectors*4096 or off+nbytes>len(self.original):raise ValueError('Malformed region slot')
        return self.original[off:off+nbytes]

    def write(self):
        if not self.new:return
        out=bytearray(self.original[:8192])
        for i in range(1024):
            data=self.entry(i)
            if data is None: continue
            sectors=(len(data)+4095)//4096
            if sectors>255:raise ValueError('Oversize sector; refusing to write external chunk')
            struct.pack_into('>I',out,4*i,((len(out)//4096)<<8)+sectors)
            out.extend(data);out.extend(bytes((-len(data))%4096))
        outpath=self.path.with_name(self.path.name+'.new')
        with outpath.open('xb') as f:f.write(out)
        os.replace(outpath,self.path)
        self.new.clear()
        self.original=bytes(out)

class World:
    def __init__(self,output_world_dir,*,writable=False):
        self.path=Path(output_world_dir)
        self.writable=writable
        if writable and self.path.resolve().is_relative_to(SOURCE.resolve()):raise ValueError("Immutable authored-world source")
        self.regions={};self.cache=OrderedDict()
        self.names=[AIR];self.ids={AIR:0}
        self.modified=set();self.fresh=set();self.original_writes=set()

    def id(self,state):
        state=canonical(state)
        if state not in self.ids:
            self.ids[state]=len(self.names);self.names.append(state)
            if len(self.names)>65535:raise ValueError('Exceeded supported global palette')
        return self.ids[state]

    def region(self,cx,cz):
        key=(cx//32,cz//32)
        if key not in self.regions:
            self.regions[key]=Region(self.path/'region'/f'r.{key[0]}.{key[1]}.mca')
        return self.regions[key]

    def save_chunk(self,key,v):
        if v.changed:
            cx,cz=key
            self.region(cx,cz).new[cx%32+32*(cz%32)]=v.payload()
            self.modified.add(key)

    def load(self,cx,cz):
        key=(cx,cz)
        if key in self.cache:
            self.cache.move_to_end(key);return self.cache[key]
        if len(self.cache)>=36:
            old,chunk=self.cache.popitem(last=False);self.save_chunk(old,chunk)
        raw=self.region(cx,cz).entry(cx%32+32*(cz%32))
        if raw:
            kind=raw[4]
            content={1:gzip.decompress,2:zlib.decompress,3:lambda x:x}[kind](raw[5:])
            doc=n.File.parse(io.BytesIO(content))
            if int(doc['xPos'])!=cx or int(doc['zPos'])!=cz or int(doc['DataVersion'])!=VERSION:raise ValueError('Wrong region identity')
        else:
            raise KeyError(f'No authored chunk at {key}')
        result=Chunk(doc,self);self.cache[key]=result;return result

    def get(self,x,y,z):
        if not -64<=y<320:raise IndexError(y)
        v=self.load(x//16,z//16)
        return self.names[int(v.array(y//16)[y%16,z%16,x%16])]

    def set(self,x,y,z,state):
        if not self.writable:raise PermissionError("Read-only world")
        if not -64<=y<320:raise IndexError(y)
        v=self.load(x//16,z//16);arr=v.array(y//16);idx=(y%16,z%16,x%16)
        i=self.id(state)
        if int(arr[idx])!=i:
            arr[idx]=i;v.changed.add(y//16)
            if -1024<=x<=1023 and -1024<=z<=1023:self.original_writes.add((x,y,z))

    def fill_column(self,x,z,y_min,y_max,state):
        if not self.writable:raise PermissionError("Read-only world")
        if y_min>y_max:return
        if not -64<=y_min<=y_max<320:raise IndexError((y_min,y_max))
        if -1024<=x<=1023 and -1024<=z<=1023:
            for y in range(y_min,y_max+1):self.set(x,y,z,state)
            return
        c=self.load(x//16,z//16);v=self.id(state)
        for sy in range(y_min//16,y_max//16+1):
            c.array(sy)[max(0,y_min-sy*16):min(16,y_max-sy*16+1),z%16,x%16]=v
            c.changed.add(sy)

    def add_ocean(self,cx,cz,column):
        if not self.writable:raise PermissionError("Read-only world")
        if -64<=cx<=63 and -64<=cz<=63:raise ValueError('Ocean overlaps R7')
        region=self.region(cx,cz);slot=cx%32+32*(cz%32)
        if region.entry(slot) is not None:raise ValueError('Exterior chunk already exists')
        if len(self.cache)>=36:
            old,v=self.cache.popitem(last=False);self.save_chunk(old,v)
        c=Chunk(new_chunk(cx,cz),self);self.cache[cx,cz]=c
        floors=np.empty((16,16),np.int32);tops=np.empty((16,16),np.uint16);waters=np.empty((16,16),np.int32)
        for iz in range(16):
            for ix in range(16):
                floor,material,surface,dry=column(cx*16+ix,cz*16+iz)
                floors[iz,ix]=floor; tops[iz,ix]=self.id(dry or material);waters[iz,ix]=surface if dry is None else -64
        rock=self.id('stone');bedrock=self.id('bedrock');water=self.id(WATER)
        if floors.min()<=-64 or floors.max()>=320 or waters.max()>=320:raise ValueError('Exterior profile outside dimension')
        for sy in range(-4, max(floors.max(),waters.max())//16+1):
            y=(sy*16+np.arange(16))[:,None,None]
            arr=np.where(y<floors,rock,np.where(y==floors,tops,np.where(y<=waters,water,0))).astype(np.uint16)
            if sy==-4:arr[0]=bedrock
            c.arrays[sy]=arr;c.changed.add(sy)
        self.fresh.add((cx,cz))

    def flush(self):
        if not self.writable:raise PermissionError("Read-only world")
        while self.cache:
            key,v=self.cache.popitem(last=False);self.save_chunk(key,v)
        for region in self.regions.values():region.write()

    def stamp_lighthouse(self,schem,anchor_world_xyz=(-1410,75,-430),anchor_local_xyz=(27,8,23),rotation=180):
        if hashlib.sha256(Path(schem).read_bytes()).hexdigest()!=LIGHTHOUSE_SHA:raise ValueError('Unexpected lighthouse asset')
        if rotation!=180:raise ValueError('Only the tested western-site rotation is enabled')
        doc=n.load(schem)
        if int(doc['Version'])!=2 or int(doc['DataVersion'])!=VERSION or doc['Entities'] or doc['BlockEntities']:raise ValueError('Unexpected schematic')
        w,h,l=(int(doc[k]) for k in ('Width','Height','Length'))
        raw=np.asarray(doc['BlockData'],dtype=np.uint8)
        if raw.max()>=128 or raw.size!=w*h*l:raise ValueError('Unexpected lighthouse palette encoding')
        reverse={'east':'west','west':'east','south':'north','north':'south'}
        def rotate(name):
            block,_,parts=name.partition('[')
            if not parts:return block
            attrs={}
            for item in parts[:-1].split(','):
                k,v=item.split('=')
                attrs[reverse.get(k,k)]=reverse.get(v,v) if k=='facing' else v
            return canonical(block+'['+','.join(f'{k}={v}' for k,v in attrs.items())+']')
        states={int(id):rotate(s) for s,id in doc['Palette'].items()}
        signs=(anchor_world_xyz[0]+anchor_local_xyz[0],anchor_world_xyz[1]-anchor_local_xyz[1],anchor_world_xyz[2]+anchor_local_xyz[2])
        foundation=0
        for y,z,x in zip(*np.nonzero(raw.reshape(h,l,w))):
            y,z,x=int(y),int(z),int(x)
            xx,yy,zz=signs[0]-x,signs[1]+y,signs[2]-z
            block=states[int(raw[(y*l+z)*w+x])]
            if y==0:
                fill=yy-1
                while self.get(xx,fill,zz).split('[')[0] in ('minecraft:air','minecraft:water') and fill>-64:fill-=1
                if fill+1<yy:
                    self.fill_column(xx,zz,fill+1,yy-1,block if 'log' in block else 'minecraft:stone')
                    foundation+=yy-fill-1
            self.set(xx,yy,zz,block)
        return {'assetSHA256':LIGHTHOUSE_SHA,'rotation':rotation,'nonAirBlocks':int(np.count_nonzero(raw)),'foundationWrites':foundation,
                'anchorWorldXYZ':list(anchor_world_xyz),'anchorLocalXYZ':list(anchor_local_xyz),'xBounds':[-1437,-1383],'zBounds':[-453,-407]}

def verify_reference(input_root):
    manifest=input_root/'INPUT_MANIFEST.json'
    if hashlib.sha256(manifest.read_bytes()).hexdigest()!=ORIGINAL:raise ValueError('Original R7 manifest changed')
    rows=json.loads(manifest.read_bytes())['files']
    expected={r['path']:r for r in rows if r['path'].startswith('source_world/')}
    actual={str(f.relative_to(input_root)) for f in (input_root/'source_world').rglob('*') if f.is_file()}
    if actual!=set(expected) or len(expected)!=17:raise ValueError('Original world inventory changed')
    for name,row in expected.items():
        p=input_root/name
        if p.is_symlink() or p.stat().st_size!=row['bytes'] or hashlib.sha256(p.read_bytes()).hexdigest()!=row['sha256']:raise ValueError('Original region hash failed '+name)
    return input_root/'source_world/CatanCraft_Season1_R7_Development'

def level(template,dest):
    doc=n.load(template)
    d=doc['Data']
    if 'Player' in d or int(d['DataVersion'])!=VERSION:raise ValueError('Unexpected owner save')
    for k,val in {'LevelName':n.String('CatanCraft – Pine Reach to Brinewatch'),'GameType':n.Int(1),'allowCommands':n.Byte(1),
                  'hardcore':n.Byte(0),'Difficulty':n.Byte(0),'SpawnX':n.Int(-400),'SpawnY':n.Int(81),'SpawnZ':n.Int(-568),
                  'SpawnAngle':n.Float(-90),'BorderCenterX':n.Double(0),'BorderCenterZ':n.Double(0),'BorderSize':n.Double(3500),
                  'BorderSizeLerpTarget':n.Double(3500),'BorderSizeLerpTime':n.Long(0),'LastPlayed':n.Long(0)}.items():d[k]=val
    for k in ('doMobSpawning','doWeatherCycle','doFireTick','doDaylightCycle'):d['GameRules'][k]=n.String('false')
    d['GameRules']['spawnRadius']=n.String('0')
    settings=d['WorldGenSettings']['dimensions']['minecraft:overworld']['generator']['settings']
    settings['biome']=n.String('minecraft:ocean')
    settings['layers']=n.List[n.Compound]([n.Compound({'height':n.Int(h),'block':n.String('minecraft:'+b)}) for h,b in [(1,'bedrock'),(122,'stone'),(1,'gravel'),(16,'water')]])
    buf=io.BytesIO();doc.write(buf);dest.write_bytes(gzip.compress(buf.getvalue(),mtime=0))

def build(source,schem,out,*,mainland,ocean):
    source=source.resolve(strict=True);out=out.absolute()
    if out.is_symlink() or out.exists() or source==out or source in out.parents:raise ValueError('Expected a new isolated output path')
    original=verify_reference(source)
    out.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(dir=out.parent,prefix='pilot-build-') as temp:
        stage=Path(temp)/'review'
        shutil.copytree(original,stage)
        world=World(stage,writable=True)
        details={'mainland':mainland.build(world)}
        print('Mainland completed', flush=True)
        for cx,cz in sorted(ocean.pilot_chunks()):
            world.add_ocean(cx,cz,ocean.offshore_column)
        print('Exterior terrain completed', len(world.fresh),flush=True)
        if hasattr(ocean,'decorate'):details['exteriorArt']=ocean.decorate(world)
        details['lighthouse']=world.stamp_lighthouse(schem)
        world.flush()
        for x,y,z in [(-400,80,-568),(-16,75,-570),(-1385,76,-431),(-1409,81,-434)]:
            details.setdefault('landmarkBlocks',{})[f'{x},{y},{z}']=world.get(x,y,z)
        details.update({'sourceManifestSHA256':ORIGINAL,'newChunks':len(world.fresh),'dirtyChunks':len(world.modified),'mainlandDistinctVoxelEdits':len(world.original_writes)})
        level(original/'level.dat',stage/'level.dat')
        verify_reference(source)
        if out.exists() or out.is_symlink():raise FileExistsError(out)
        stage.rename(out)
    return details

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--input-root',type=Path,default=SOURCE)
    p.add_argument('--schem',type=Path,default=SCHEM)
    p.add_argument('--out',type=Path,required=True)
    p.add_argument('--report',type=Path)
    a=p.parse_args()
    import mainland, ocean
    report=build(a.input_root,a.schem,a.out,mainland=mainland,ocean=ocean)
    if a.report:
        with a.report.open('x') as f:json.dump(report,f,indent=2);f.write('\n')
    print(json.dumps(report,indent=2))
