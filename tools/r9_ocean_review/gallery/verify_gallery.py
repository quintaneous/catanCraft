"""Verify the four finished review-world scenes from disk using the independent clean-input NBT reader."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import struct
import sys
import zlib
import numpy as np

def canonical(state):
    if '[' not in state:
        return state
    name,props=state[:-1].split('[')
    return name+'['+','.join(sorted(props.split(',')))+']'

def verify(world,assets,layout,nbt):
    as_nbt=lambda data:nbt(data).root()
    level=as_nbt(gzip.decompress((world/'level.dat').read_bytes()))['Data']
    assert level['DataVersion']==3465 and level['GameType']==3 and 'Player' not in level
    assert set(p.name for p in world.iterdir())=={'region','level.dat'}
    chunks={}
    ids={}
    def key(state):
        name=canonical(state)
        return ids.setdefault(name,len(ids))
    for p in (world/'region').glob('*.mca'):
        _,rx,rz,_=p.name.split('.')
        raw=p.read_bytes()
        for idx in range(1024):
            start=int.from_bytes(raw[idx*4:idx*4+3],'big')*4096
            if not start: continue
            capacity=raw[idx*4+3]*4096
            length=struct.unpack_from('>I',raw,start)[0]
            assert length<=capacity-4 and start+capacity<=len(raw) and raw[start+4]==2
            d=as_nbt(zlib.decompress(raw[start+5:start+4+length]))
            cx,cz=int(rx)*32+idx%32,int(rz)*32+idx//32
            assert d['xPos']==cx and d['zPos']==cz and d['DataVersion']==3465 and not d['block_entities']
            assert d['Status']=='minecraft:full' and (cx,cz) not in chunks
            pieces={}
            for sec in d['sections']:
                states=sec['block_states']; names=[]
                for tag in states['palette']:
                    props=tag.get('Properties',{})
                    names.append(key(tag['Name']+('['+','.join(k+'='+v for k,v in props.items())+']' if props else '')))
                if len(names)==1:
                    pieces[sec['Y']]=names[0]
                else:
                    bits=max(4,(len(names)-1).bit_length()); per=64//bits
                    values=np.frombuffer(states['data'],dtype='>u8')
                    ordinal=np.arange(4096,dtype=np.uint64)
                    indexes=(values[ordinal//per] >> (bits*(ordinal%per))) & ((1<<bits)-1)
                    pieces[sec['Y']]=np.take(names,indexes.astype(int)).astype('uint16').reshape(16,16,16)
            chunks[cx,cz]=pieces
    assert len(chunks)==320
    def box(x,y,z,w,h,l):
        result=np.zeros((h,l,w),dtype=np.uint16)
        for cy in range(y//16,(y+h-1)//16+1):
            for cz in range(z//16,(z+l-1)//16+1):
                for cx in range(x//16,(x+w-1)//16+1):
                    slices=[]; loc=[]
                    for lower,dim,c in ((y,h,cy),(z,l,cz),(x,w,cx)):
                        start=max(lower,c*16); end=min(lower+dim,c*16+16)
                        slices.append(slice(start-lower,end-lower));loc.append(slice(start-c*16,end-c*16))
                    section=chunks[cx,cz][cy]
                    result[tuple(slices)] = section if isinstance(section,int) else section[tuple(loc)]
        return result
    rows=[]
    for label,s in layout.items():
        x,y,z=s['minimumXYZ']
        w,h,l=s['sizeXYZ']
        source=assets/s['file']
        d=as_nbt(gzip.decompress(source.read_bytes()))
        vals=[];val=0;shift=0
        for v in d['BlockData']:
            val|=(v&127)<<shift
            if v&128:shift+=7
            else:vals.append(val);val=0;shift=0
        assert not shift and len(vals)==w*h*l
        lookup=[key(n) for n,_ in sorted(d['Palette'].items(),key=lambda kv:kv[1])]
        expected=np.take(lookup,vals).reshape(h,l,w)
        flooded=0
        if s['file'].startswith('lighthouse/'):
            mask=expected[:76-y]==key('minecraft:air')
            flooded=int(mask.sum());expected[:76-y][mask]=key('minecraft:water[level=0]')
        actual=box(x,y,z,w,h,l)
        assert np.array_equal(actual,expected),label
        support=0
        if y>58:
            waterids=[k for name,k in ids.items() if name.split('[')[0] in ('minecraft:air','minecraft:water')]
            solid=~np.isin(expected[0],waterids)
            foundation=box(x,58,z,w,y-58,l)
            assert np.all(foundation[:,solid]==key('minecraft:stone'))
            support=int(solid.sum())*(y-58)
        rows.append({'scene':label,'minimumXYZ':[x,y,z],'comparedCells':w*h*l,'sourceSHA256':hashlib.sha256(source.read_bytes()).hexdigest(),
                     'waterFilledTransparentCells':flooded,'extraFoundationBlocks':support})
    assert box(0,84,4,1,2,1).min()==key('minecraft:air')==box(0,84,4,1,2,1).max()
    return {'minecraft':'Java 1.20.1','dataVersion':3465,'chunksChecked':len(chunks),'spectatorSpawn':[0,84,4],
            'sceneCellsChecked':sum(d['comparedCells'] for d in rows),'scenes':rows,'bootedInMinecraft':False}

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    for arg in ['world','assets','placements','clean_input']:p.add_argument(arg,type=Path)
    a=p.parse_args()
    sys.path.insert(0,str(a.clean_input))
    from nbt_check import NBT
    print(json.dumps(verify(a.world,a.assets,json.loads(a.placements.read_bytes()),NBT),indent=2))
