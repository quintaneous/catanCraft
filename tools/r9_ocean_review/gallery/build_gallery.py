"""Build a separate Java 1.20.1 spectator gallery from four approved-input schematics.

Requires numpy and nbtlib 2.0.4. No server, world paste or existing save is opened for writing.
"""
import argparse
from collections import defaultdict
import gzip
import io
import json
import math
from pathlib import Path
import struct
import zlib
import nbtlib as nbt
import numpy as np

VERSION = 3465
# (file, local review-world minimum x/y/z). The tiles are displayed in asset orientation.
SCENES = {
    "Sheltered cove": ("coast/r9_sheltered_cove.schem", (-128,57,-112)),
    "Estuary headland": ("coast/r9_headland_estuary.schem", (16,57,-112)),
    "Brinewatch pilot station": ("lighthouse/Brinewatch_Pilot_Station.schem", (-89,67,26)),
    "Freighter wreck": ("wreck/brinewake.schem", (13,60,18)),
}
X0, Z0 = -160, -144
COLUMNS, ROWS = 320, 256
MIN_Y, HEIGHT = -64, 208
WATER = 75


def schematic(path):
    doc = nbt.load(path)
    assert int(doc['Version']) == 2 and int(doc['DataVersion']) == VERSION
    xs, ys, zs = map(int, [doc['Width'],doc['Height'],doc['Length']])
    assert not doc.get('Entities') and not doc.get('BlockEntities')
    encoded = doc['BlockData'].view(np.uint8)
    if int(encoded.max()) < 128:
        data = encoded
    else:
        values, current, shift = [], 0, 0
        for octet in map(int,encoded):
            current |= (octet & 127) << shift
            if octet & 128:
                shift += 7
            else:
                values.append(current)
                current, shift = 0, 0
        assert shift == 0
        data = np.array(values, dtype=np.uint16)
    palette = [k for k,v in sorted(doc['Palette'].items(), key=lambda row:int(row[1]))]
    assert list(range(len(palette))) == sorted(map(int,doc['Palette'].values()))
    return data.reshape(ys,zs,xs), palette


def state_tag(state):
    name, _, props = state.partition('[')
    tag = nbt.Compound({'Name':nbt.String(name)})
    if props:
        tag['Properties'] = nbt.Compound({k:nbt.String(v) for k,v in
                                         (item.split('=') for item in props.rstrip(']').split(','))})
    return tag


def encode_chunk(cx, cz, column, palette_tags):
    sections = []
    for start in range(0,384,16):
        sy = start//16-4
        if start < column.shape[0]:
            section = column[start:start+16].reshape(-1)
            ids, inverse = np.unique(section,return_inverse=True)
        else:
            ids = [0]
        blocks = nbt.Compound({'palette':nbt.List[nbt.Compound]([palette_tags[i] for i in ids])})
        if len(ids)>1:
            bits = max(4,math.ceil(math.log2(len(ids))))
            per = 64//bits
            indices = np.pad(inverse.ravel(),(0,(-4096)%per)).reshape(-1,per).astype(np.uint64)
            packed = np.bitwise_or.reduce(indices << (np.arange(per,dtype=np.uint64)*bits),axis=1)
            blocks['data'] = nbt.LongArray(packed.view(np.int64))
        sections.append(nbt.Compound({'Y':nbt.Byte(sy),'block_states':blocks,
            'biomes':nbt.Compound({'palette':nbt.List[nbt.String]([nbt.String('minecraft:ocean')])})}))
    # Omit cached lighting and height maps. Vanilla recomputes missing derived maps.
    chunk = nbt.File({'DataVersion':nbt.Int(VERSION),'xPos':nbt.Int(cx),'yPos':nbt.Int(-4),'zPos':nbt.Int(cz),
        'Status':nbt.String('minecraft:full'),'LastUpdate':nbt.Long(0),'InhabitedTime':nbt.Long(0),
        'sections':nbt.List[nbt.Compound](sections),'isLightOn':nbt.Byte(0),
        'block_entities':nbt.List[nbt.Compound]([]),'block_ticks':nbt.List[nbt.Compound]([]),
        'fluid_ticks':nbt.List[nbt.Compound]([]),'structures':nbt.Compound({'starts':nbt.Compound(),'References':nbt.Compound()})})
    stream = io.BytesIO()
    chunk.write(stream)
    data = b'\x02'+zlib.compress(stream.getvalue(),4)
    return struct.pack('>I',len(data))+data


def write_regions(volume,palette,out):
    region_dir=out/'region'
    region_dir.mkdir()
    tables = defaultdict(dict)
    for row in range(ROWS//16):
        for col in range(COLUMNS//16):
            cx,cz = X0//16+col, Z0//16+row
            table=tables[cx//32,cz//32]
            table[cx%32+32*(cz%32)] = encode_chunk(cx,cz,volume[:,row*16:(row+1)*16,col*16:(col+1)*16],palette)
    for (rx,rz),chunks in tables.items():
        result=bytearray(8192)
        for slot,data in sorted(chunks.items()):
            count=(len(data)+4095)//4096
            offset=len(result)//4096
            assert count < 256
            struct.pack_into('>I',result,slot*4,(offset<<8)|count)
            result.extend(data)
            result.extend(bytes((-len(data))%4096))
        (region_dir/f'r.{rx}.{rz}.mca').write_bytes(result)


def write_level(template,out):
    # Reuse the version and vanilla dimensions schema only. Initialize a new identity and rules.
    source = nbt.load(template)['Data']
    gen=source['WorldGenSettings']
    gen['seed']=nbt.Long(9128371)
    settings=gen['dimensions']['minecraft:overworld']['generator']['settings']
    settings['biome']=nbt.String('minecraft:ocean')
    settings['layers']=nbt.List[nbt.Compound]([nbt.Compound({'height':nbt.Int(h),'block':nbt.String('minecraft:'+b)})
                                               for h,b in [(1,'bedrock'),(120,'stone'),(1,'gravel'),(18,'water')]])
    values=nbt.Compound({'DataVersion':nbt.Int(VERSION),'Version':source['Version'],'version':nbt.Int(19133),
        'LevelName':nbt.String('CatanCraft R9 - Ocean Design Gallery v2'),'GameType':nbt.Int(3),
        'Difficulty':nbt.Byte(0),'allowCommands':nbt.Byte(1),'hardcore':nbt.Byte(0),'initialized':nbt.Byte(1),
        'Time':nbt.Long(1000),'DayTime':nbt.Long(1000),'LastPlayed':nbt.Long(0),
        'SpawnX':nbt.Int(0),'SpawnY':nbt.Int(84),'SpawnZ':nbt.Int(4),'SpawnAngle':nbt.Float(54),
        'GameRules':nbt.Compound({k:nbt.String(v) for k,v in {'doDaylightCycle':'false','doWeatherCycle':'false',
            'doMobSpawning':'false','doFireTick':'false','randomTickSpeed':'0','spawnRadius':'0'}.items()}),
        'DataPacks':nbt.Compound({'Enabled':nbt.List[nbt.String]([nbt.String('vanilla')]),'Disabled':nbt.List[nbt.String]([])}),
        'WorldGenSettings':gen})
    stream=io.BytesIO()
    nbt.File({'Data':values}).write(stream)
    (out/'level.dat').write_bytes(gzip.compress(stream.getvalue(),mtime=0))


def build(assets,template,out):
    keys=['minecraft:air','minecraft:bedrock','minecraft:stone','minecraft:gravel','minecraft:water[level=0]']
    volume=np.zeros((HEIGHT,ROWS,COLUMNS),dtype=np.uint16)
    volume[0]=1
    volume[1:121]=2
    volume[121]=3
    volume[122:140]=4
    placements={}
    occupied=[]
    for label,(filename,(x,y,z)) in SCENES.items():
        data,palette=schematic(assets/filename)
        ys,zs,xs=data.shape
        a,b,c=x-X0,y-MIN_Y,z-Z0
        assert 0<=a<=COLUMNS-xs and 0<=b<=HEIGHT-ys and 0<=c<=ROWS-zs
        assert not any(x<ex and x+xs>ax and z<ez and z+zs>az for ax,az,ex,ez in occupied)
        occupied.append((x,z,x+xs,z+zs))
        for key in palette:
            if key not in keys: keys.append(key)
        blocks = np.take([keys.index(k) for k in palette],data)
        if filename.startswith('lighthouse/'):
            # The asset treats under-pier air as transparent. Retain the gallery's seawater.
            sub = blocks[:WATER-y+1]
            sub[sub==0] = 4
        volume[b:b+ys,c:c+zs,a:a+xs]=blocks
        if y>58:
            # Give the islet and grounded shoal continuous footing down to the local seabed.
            solid = np.array([keys[i].split('[')[0] not in ('minecraft:air','minecraft:water')
                              for i in range(len(keys))])[blocks[0]]
            volume[58-MIN_Y:b,c:c+zs,a:a+xs][:,solid]=2
        placements[label]={'file':filename,'minimumXYZ':[x,y,z],'sizeXYZ':[xs,ys,zs]}
    out.mkdir(parents=True,exist_ok=False)
    write_regions(volume,[state_tag(k) for k in keys],out)
    write_level(template,out)
    return placements

if __name__ == '__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('assets',type=Path)
    p.add_argument('version_template',type=Path)
    p.add_argument('new_output',type=Path)
    a=p.parse_args()
    print(json.dumps(build(a.assets,a.version_template,a.new_output),indent=2))
