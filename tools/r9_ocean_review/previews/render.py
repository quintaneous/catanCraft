"""Render paired views from R7 Anvil blocks and matching isolated Java 1.20.1 schematic volumes."""
import argparse
import gzip
import hashlib
import json
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
SIZE = (112, 64, 96)
CASES = {
    'sheltered_cove': ('Sheltered cove', -368, -1024, (50,21.4,-29), (50,31,53)),
    'headland_estuary': ('Headland estuary', -40, -1024, (77,21.4,-5), (61,23,55)),
}
# Colors are schematic review colors, independent of any client biome tint or resource pack.
PAINT = {
 'air':(0,0,0,0),'cave_air':(0,0,0,0),'void_air':(0,0,0,0),
 'water':(1,52,142,153),'grass_block':(2,132,159,78),'dirt':(0,135,109,80),
 'stone':(0,143,145,141),'andesite':(0,145,153,152),'tuff':(0,113,128,120),
 'sandstone':(0,202,182,133),'sand':(0,226,208,155),'gravel':(0,156,149,138),
 'clay':(0,154,166,171),'grass':(3,115,146,66),'short_grass':(3,115,146,66),
 'fern':(3,100,130,65),'oxeye_daisy':(3,214,217,173),'dandelion':(3,221,190,69),
 'sugar_cane':(3,145,162,83),'oak_log':(4,114,88,54),'spruce_log':(4,93,73,57),
 'spruce_leaves':(5,62,108,74),'oak_leaves':(5,89,137,65),'bedrock':(0,84,82,80),
 'birch_log':(4,192,188,160),'birch_leaves':(5,114,143,78),'coarse_dirt':(0,131,105,77),'moss_block':(0,108,136,69),'diorite':(0,173,174,163),
}
def hash_file(path):
    with path.open('rb') as f:
        return hashlib.file_digest(f,'sha256').hexdigest()

def simple(state):
    return state.split('[')[0].removeprefix('minecraft:')

def original(world,x0,z0):
    w,h,d=SIZE
    names=['air']; ids={'air':0}; data=np.zeros((h,d,w),dtype='uint8')
    for y in range(h):
        for z in range(d):
            for x in range(w):
                key=simple(world.at((x+x0,57+y,z+z0)))
                if key not in ids:
                    ids[key]=len(names);names.append(key)
                data[y,z,x]=ids[key]
    return data,names

def proposed(path,nbt):
    s=nbt(gzip.decompress(path.read_bytes())).root()
    assert (s['Width'],s['Height'],s['Length'])==SIZE and s['Version']==2 and s['DataVersion']==3465
    names=[simple(k) for k,_ in sorted(s['Palette'].items(),key=lambda t:t[1])]
    assert len(names)<128 and names[0]=='air'
    data=np.frombuffer(s['BlockData'],np.uint8)
    assert data.max()<len(names) and data.size==np.prod(SIZE)
    return data.reshape(SIZE[1],SIZE[2],SIZE[0]),names

def font(size,bold=False):
    name='OpenSans-Semibold.ttf' if bold else 'OpenSans-Regular.ttf'
    return ImageFont.truetype(name,size)
def sheet(title,panels,geo,dest):
    W=1512;margin=44;gap=24;pw=700;ph=371
    im=Image.new('RGB',(W,1100),'#f8f7f1');d=ImageDraw.Draw(im)
    d.text((margin,26),'CATANCRAFT   /   R9 COASTAL STUDIES',font=font(17,True),fill='#31685f')
    d.text((margin,53),title,font=font(51,True),fill='#193633')
    d.text((margin,126),'The same terrain window and camera are used in each pair.',font=font(23),fill='#425954')
    for x,t in [(margin,'Current authored terrain  ·  R7'),(margin+pw+gap,'Coastal proposal  ·  R9')]:
        d.text((x,190),t,font=font(23,True),fill='#213c38')
    for i,(view,label) in enumerate([('aerial','Aerial view'),('waterline','Sea-level approach')]):
        top=236+i*404
        for j,phase in enumerate(('before','after')):
            img=panels[view,phase].resize((pw,ph),Image.Resampling.LANCZOS)
            x=margin+j*(pw+gap);im.paste(img,(x,top))
            b=d.textbbox((0,0),label,font=font(17,True))
            d.rounded_rectangle((x+11,top+11,x+32+b[2],top+41),radius=4,fill='#f8f7f1')
            d.text((x+21,top+12),label,font=font(17,True),fill='#254842')
    x,z=geo
    d.text((margin,1031),f'R7 reference window   X {x} to {x+111}   /   Z {z} to {z+95}   /   Y 57 to 120',font=font(19,True),fill='#304d48')
    d.text((margin,1063),'Daylight block renders. Exposed faces show the edge of the source crop. The sky is illustrative.',font=font(17),fill='#556762')
    im.save(dest,optimize=True)

def run(root,data_dir,out):
    sys.path.insert(0,str(root/'inputs/CatanCraft_Clean_R7_Input'))
    from nbt_check import NBT
    sys.path.insert(0,str(root/'resource-ui/tools/walls53'))
    from audit_sites import World
    base=root/'inputs/CatanCraft_Clean_R7_Input'
    world_path=base/'source_world/CatanCraft_Season1_R7_Development'
    world=World(world_path,NBT); records={}
    with tempfile.TemporaryDirectory() as td:
        temp=Path(td);exe=temp/'render'
        subprocess.run(['c++','-O3','-std=c++17',str(HERE/'voxel_renderer.cpp'),'-o',str(exe)],check=True)
        for key,(title,x0,z0,eye,target) in CASES.items():
            path=data_dir/f'r9_{key}.schem'
            panels={};cams={'aerial':((-29,100,-84),(55,15,48)), 'waterline':(eye,target)}
            for phase,(volume,names) in [('before',original(world,x0,z0)),('after',proposed(path,NBT))]:
                raw=temp/'voxels';raw.write_bytes(volume.tobytes())
                colors=temp/'palette'
                missing=set(names)-PAINT.keys()
                if missing:raise ValueError(f'Unmapped materials: {missing}')
                colors.write_text(''.join(' '.join(map(str, PAINT[k]))+'\n' for k in names))
                for view,(cam,focus) in cams.items():
                    ppm=temp/'view.ppm'
                    subprocess.run([str(exe),str(raw),str(colors),str(ppm),'1260','668',str(int(view=='aerial')),*map(str,cam),*map(str,focus)],check=True)
                    with Image.open(ppm) as image:panels[view,phase]=image.copy()
            sheet(title,panels,(x0,z0),out/f'{key}_review.png')
            regions={f'region/r.{x//512}.{z//512}.mca' for x in (x0,x0+111) for z in (z0,z0+95)}
            records[key]={'tile_origin_XYZ':[x0,57,z0],'size_XYZ':SIZE,'source_regions':{f:hash_file(world_path/f) for f in sorted(regions)},'schematic_sha256':hash_file(path),'cameras_local_XYZ':cams,'waterline_top_Y':76,'review_png_sha256':hash_file(out/f'{key}_review.png')}
    (out/'view_sources.json').write_text(json.dumps(records,indent=2)+'\n')
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--workspace',type=Path,default=Path.cwd())
    p.add_argument('--coast',type=Path,default=HERE.parent/'coast')
    p.add_argument('--output',type=Path,default=HERE)
    a=p.parse_args();a.output.mkdir(exist_ok=True,parents=True);run(a.workspace.resolve(),a.coast,a.output)
