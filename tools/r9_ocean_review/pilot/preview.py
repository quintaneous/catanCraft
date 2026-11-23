"""Read an integrated Java 1.20.1 pilot world and render its pilot route and woodland approach."""
import argparse
from collections import Counter
import gzip
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import subprocess
import sys
import tempfile
import zlib

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE=Path(__file__).resolve().parent
WORK=HERE.parents[1]
sys.dont_write_bytecode=True
sys.path.insert(0,str(WORK/'inputs/CatanCraft_Clean_R7_Input'))
from nbt_check import NBT
from functools import lru_cache

def material(s):
    return s['Name'].split(':')[-1]

class Volume:
    """Decode individual chunk sections in YZX order. Missing authored surface stays blank."""
    def __init__(self,folder):
        self.root=Path(folder);self.names=['air'];self.codes={'air':0};self.regions=set()
    def code(self,n):
        if n in ('cave_air','void_air'):return 0
        if n not in self.codes:
            self.codes[n]=len(self.names);self.names.append(n)
        return self.codes[n]
    @lru_cache(maxsize=6)
    def region(self,rx,rz):
        p=self.root/'region'/f'r.{rx}.{rz}.mca'
        if p.is_file():
            self.regions.add(p);return p.read_bytes()
    def chunk(self,cx,cz):
        data=self.region(cx//32,cz//32)
        if not data:return None
        i=4*((cz%32)*32+cx%32);loc=int.from_bytes(data[i:i+3],'big')*4096
        if not loc:return None
        size=int.from_bytes(data[loc:loc+4],'big');comp=data[loc+4]
        raw=data[loc+5:loc+4+size]
        raw={1:gzip.decompress,2:zlib.decompress,3:lambda b:b}[comp](raw)
        res=NBT(raw).root()
        assert res['xPos']==cx and res['zPos']==cz
        return res['sections']
    def section(self,section):
        ids=np.array([self.code(material(s)) for s in section['palette']],dtype='uint16')
        if len(ids)==1:return np.full((16,16,16),ids[0],np.uint16)
        bits=max(4,(len(ids)-1).bit_length());per=64//bits
        arr=np.frombuffer(section['data'],dtype='>u8').astype(np.uint64)
        nums=np.arange(4096,dtype=np.uint64)
        return ids[((arr[nums//per]>>((nums%per)*bits))&((1<<bits)-1)).reshape(16,16,16)]
    def read(self,bounds,surface=False):
        x0,y0,z0,x1,y1,z1=bounds;w,h,d=x1-x0,y1-y0,z1-z0
        assert all(n%16==0 for n in bounds)
        grid=np.zeros((d,w) if surface else (h,d,w),dtype=np.uint16)
        heights=np.full((d,w),y0-1,dtype=np.int16)
        for cz in range(z0//16,z1//16):
            zz=16*cz-z0
            for cx in range(x0//16,x1//16):
                xx=16*cx-x0;data=self.chunk(cx,cz)
                if data is None:continue
                done=np.zeros((16,16),dtype=bool)
                for s in sorted(data,key=lambda a:-a['Y']):
                    yy=16*s['Y']-y0;b=s.get('block_states')
                    if yy<0 or yy>=h or not b or (len(b['palette'])==1 and material(b['palette'][0]).endswith('air')):continue
                    cells=self.section(b)
                    if surface:
                        hit=(cells!=0)[::-1]
                        valid=hit.any(axis=0)&~done
                        z,x=np.where(valid);y=15-np.argmax(hit[:,z,x],axis=0)
                        grid[zz+z,xx+x]=cells[y,z,x]
                        heights[zz+z,xx+x]=y+y0+yy
                        done|=valid
                        if done.all():break
                    else:grid[yy:yy+16,zz:zz+16,xx:xx+16]=cells
        return (grid,heights) if surface else grid

PAINT={
 'air':(0,'#000000'),'water':(1,'#328492'),'grass_block':(2,'#92aa67'),'dirt':(0,'#846b49'),'coarse_dirt':(0,'#85734f'),
 'podzol':(0,'#92734e'),'stone':(0,'#979994'),'andesite':(0,'#9ea09a'),'gravel':(0,'#a5a197'),'sand':(0,'#daca9b'),
 'snow_block':(0,'#e6eee6'),'sandstone':(0,'#c8b987'),'diorite':(0,'#b9b8aa'),'stone_bricks':(0,'#9a9b91'),'moss_block':(0,'#8caa65'),
 'clay':(0,'#99a6ab'),'tuff':(0,'#7f8d80'),'spruce_leaves':(5,'#426d52'),'oak_leaves':(5,'#648644'),
 'birch_leaves':(5,'#7b914e'),'spruce_log':(4,'#684f3b'),'birch_log':(4,'#b9b49a'),'oak_log':(4,'#7f6246'),
 'grass':(3,'#87a35e'),'tall_grass':(3,'#87a35e'),'short_grass':(3,'#87a35e'),
 'fern':(3,'#748d53'),'large_fern':(3,'#728955'),'sweet_berry_bush':(3,'#65894c'),
 'brown_mushroom':(3,'#9e775b'),'red_mushroom':(3,'#a6644c'),'dead_bush':(3,'#846f4c'),
 'oxeye_daisy':(3,'#dbdcb2'),'dandelion':(3,'#d6bb4b'),'sugar_cane':(3,'#91a753'),
 'mossy_cobblestone':(0,'#7d8972'),'cobblestone':(0,'#858980'),'moss_carpet':(3,'#88a66b'),
 'oak_planks':(0,'#b29469'),'spruce_planks':(0,'#977451'),'red_concrete':(0,'#a25449'),
 'polished_andesite':(0,'#9a9e96'),'seagrass':(1,'#468a83'),'kelp':(1,'#367d7a'),'kelp_plant':(1,'#37777a'),
}
def paint(name):
    if name in PAINT:m,s=PAINT[name]
    elif 'spruce' in name:m,s=0,'#887155'
    elif 'oak' in name:m,s=0,'#9b805c'
    elif 'leaves' in name:m,s=5,'#678758'
    elif 'copper' in name or 'prismarine' in name:m,s=0,'#799990'
    elif 'terracotta' in name or 'bricks'==name:m,s=0,'#ab7462'
    elif 'iron' in name or 'stone' in name or 'andesite' in name:m,s=0,'#9b9f99'
    elif 'snow' in name or 'white' in name or 'quartz' in name:m,s=0,'#e6e7d9'
    elif 'poppy' in name or 'tulip' in name:m,s=3,'#b56f60'
    else:m,s=0,'#b0a99a'
    return m,tuple(bytes.fromhex(s[1:]))
def font(size,b=False):return ImageFont.truetype('OpenSans-Semibold.ttf' if b else 'OpenSans-Regular.ttf',size)
def route_map(world,out,plan):
    bounds=(-1568,48,-1392,144,192,-320);x0,_,z0,x1,_,z1=bounds
    codes,height=world.read(bounds,True)
    sw,sh=x1-x0,z1-z0
    base=np.asarray([paint(n)[1] for n in world.names],dtype='float32')[codes]
    dy,dx=np.gradient(height.astype('float32'));shade=np.clip(1.-dx*.055-dy*.085,.72,1.17)
    z,x=np.mgrid[:sh,:sw];noise=((x*31+z*71+((z+x)*11)%23)%41)/680.
    base*=((shade+noise-.03)[:,:,None])
    water=(codes==world.codes.get('water',65535))
    base[water]=np.stack((69+.6*noise+2*np.sin(x/35+z/32),
                          133+np.sin(z/18+x/23)*2,144+np.cos(z/18+x/23)*3),axis=-1)[water]
    base[codes==0]=[212,215,200]
    chart=Image.fromarray(np.uint8(np.clip(base,0,255)))
    w=1510;margin=40;wh=1430;hh=round(wh*sh/sw)
    chart=chart.resize((wh,hh),Image.Resampling.LANCZOS)
    im=Image.new('RGB',(w,hh+214),'#f7f6ed');im.paste(chart,(margin,145));d=ImageDraw.Draw(im)
    d.text((margin,18),'CATANCRAFT   /   CONNECTED FIELD PILOT',font=font(16,True),fill='#39716a')
    d.text((margin,44),'Pine Reach to Brinewatch',font=font(42,True),fill='#243e3a')
    d.text((margin,105),'Surface read directly from the saved pilot world. North is up.',font=font(18),fill='#455d57')
    def xy(p):return (round(margin+(p[0]-x0)*wh/sw),round(145+(p[1]-z0)*hh/sh))
    def path(pts,col,width=4,dashes=False):
        pix=list(map(xy,pts));d.line(pix,fill='#203e40',width=width+2,joint='curve')
        if not dashes:d.line(pix,fill=col,width=width,joint='curve')
        else:
            dist=0
            for (x,y),(u,v) in zip(pix,pix[1:]):
                n=math.hypot(u-x,v-y)
                for i in range(round(n)):
                    if (i+dist)%14<9:
                        t=(i+.5)/max(n,1);px=x+(u-x)*t;py=y+(v-y)*t;d.ellipse((px-1.4,py-1.4,px+1.4,py+1.4),fill=col)
                dist+=round(n)
    road=plan['survey']['roadCenterXZ'];river=plan['survey']['riverCenterXZ']
    ocean=json.loads((HERE/'ocean.json').read_bytes())
    sea=ocean['primaryRouteXZ']
    bank=json.loads((HERE/'bank-reference.json').read_bytes())
    bankpoints=[(p[0],p[2]) for p in bank['roadToBankFeetXYZ']]
    road=[p for p in road if -400<=p[0]<=-45]
    path([*road,*bankpoints],'#f0c873');path([*bank['launchToSurveyedRiverXZ'],*river],'#feefb7',3);path([river[-1],*sea],'#fef0c1',3,True)
    locations=[('Pine Reach',road[0],(-107,20)),('B1 crossing',plan['survey']['bridgeAnchorXZ'],(-7,32)),('North estuary',river[-1],(-169,-26)),('Brinewatch',[-1410,-430],(-79,32))]
    for label,at,(dx,dy) in locations:
        px,py=xy(at);d.ellipse((px-6,py-6,px+6,py+6),fill='#ffe29f',outline='#293d37',width=2)
        tx,ty=px+dx,py+dy;tw=d.textlength(label,font=font(18,True))
        d.rounded_rectangle((tx-6,ty-3,tx+tw+7,ty+23),4,fill='#f8f4e4');d.text((tx,ty-2),label,font=font(18,True),fill='#294742')
    d.text((margin,hh+160),'Gold follows the walking and river route. Dashes mark the offshore sailing leg.',font=font(17),fill='#495f57')
    # Scale in map blocks at the same conversion as the raster.
    dx=round(200*wh/sw);xx=1285;yy=hh+173;d.line((xx,yy,xx+dx,yy),fill='#34534b',width=3)
    d.text((xx,yy+4),'200 blocks',font=font(14),fill='#34534b')
    im.save(out/'Pine_Reach_to_Brinewatch.png',optimize=True)
    return {'map_bounds_inclusive_xz':[x0,z0,x1-1,z1-1],'authored_columns_displayed':int((codes!=0).sum()),'ocean_waypoints_xz':sea}

def forest(before,after,out):
    # The land geometry on each pair is independently decoded. The renderer is projected
    # into the chosen crop. Geometry beyond it is intentionally out of frame.
    folders=[before,after]
    bounds=(-416,64,-672,-224,144,-512);x0,y0,z0,x1,y1,z1=bounds
    w,h,d=x1-x0,y1-y0,z1-z0
    feeds=[world.read(bounds) for world in folders]
    cams={
     'aerial':((x0-42,207,z1+93),(-327,86,-602)),
     'road':((-347.5,93.62,-602.5),(-347.5,97.8,-640.5))}
    # Match the player camera to the original solid surface. Keep it identical in both views.
    gx,_,gz=cams['road'][0];ci=np.floor([gx-x0,gz-z0]).astype(int)
    ground=[j+y0 for j,k in enumerate(feeds[0][:,ci[1],ci[0]]) if k and paint(before.names[k])[0] not in {3,4,5}]
    if ground:cams['road']=((gx,max(ground)+2.62,gz),cams['road'][1])
    src=WORK/'receipts/ocean-prototype/previews/voxel_renderer.cpp'
    code=src.read_text().replace('const int W=112,H=64,D=96;',f'const int W={w},H={h},D={d};').replace('right*(u*87)+up*(v*87/aspect)','right*(u*128)+up*(v*128/aspect)')
    scale_key='const int W='+str(w)+',H='+str(h)+',D='+str(d)+';'
    assert scale_key in code and 'right*(u*128)' in code
    with tempfile.TemporaryDirectory() as tmp:
        t=Path(tmp);(t/'render.cpp').write_text(code);exe=t/'render'
        subprocess.run(['c++','-O3','-std=c++17',str(t/'render.cpp'),'-o',str(exe)],check=True)
        panels={}
        for i,(vol,world) in enumerate(zip(feeds,folders)):
            unique=np.unique(vol);mapper=np.zeros(len(world.names),dtype=np.uint8)
            for j,n in enumerate(unique):mapper[n]=j
            assert unique[0]==0 and len(unique)<=255
            (t/'data').write_bytes(mapper[vol].tobytes())
            (t/'pal').write_text(''.join(' '.join(map(str,(paint(world.names[n])[0],*paint(world.names[n])[1])))+'\n' for n in unique))
            for view,(eye,target) in cams.items():
                coords=np.asarray([eye,target])-np.array([x0,y0,z0])
                subprocess.run([str(exe),str(t/'data'),str(t/'pal'),str(t/'v.ppm'),'1072','616',str(int(view=='aerial')),*map(str,coords.flatten())],check=True)
                with Image.open(t/'v.ppm') as img:panels[view,i]=img.copy()
    pw,ph=688,395;gap=22;left=42;top=211
    im=Image.new('RGB',(1482,1126),'#f7f6ed');draw=ImageDraw.Draw(im)
    draw.text((left,23),'CATANCRAFT   /   PINE REACH',font=font(18,True),fill='#39716a')
    draw.text((left,50),'The woodland approach',font=font(48,True),fill='#243e3a')
    draw.text((left,121),'Matched views of the original terrain and the integrated pilot save.',font=font(22),fill='#455d57')
    for i,label in enumerate(['Current R7 terrain','Connected pilot']):
        draw.text((left+i*(pw+gap),167),label,font=font(25,True),fill='#253f3a')
    for row,(v,label) in enumerate([('aerial','Aerial'),('road','Player height')]):
        for col in (0,1):
            px,py=left+col*(pw+gap),top+row*(ph+16)
            im.paste(panels[v,col].resize((pw,ph),Image.Resampling.LANCZOS),(px,py))
            tw=draw.textlength(label,font=font(17,True))
            draw.rounded_rectangle((px+12,py+12,px+tw+33,py+43),4,fill='#f7f6ed')
            draw.text((px+22,py+14),label,font=font(17,True),fill='#295049')
    pos=cams['road'][0];draw.text((left,1040),f'Player-height view from X {pos[0]:.1f}, Y {pos[1]:.1f}, Z {pos[2]:.1f}, facing north.',font=font(20,True),fill='#304a41')
    draw.text((left,1075),'Local block-volume render. Sky, lighting, foliage opacity and plant shapes are illustrative.',font=font(17),fill='#506357')
    im.save(out/'Pine_Reach_before_after.png',optimize=True)
    by_name={name:i for i,name in enumerate(sorted(set(before.names+after.names)))}
    maps=[np.array([by_name[n] for n in v.names],np.uint16) for v in folders]
    return {'forest_bounds_exclusive_xyz':bounds,'matched_camera_xyz':cams,
            'crop_changed_cells':int(np.count_nonzero(maps[0][feeds[0]]!=maps[1][feeds[1]]))}

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--pilot',type=Path,required=True)
    p.add_argument('--baseline',type=Path,default=WORK/'inputs/CatanCraft_Clean_R7_Input/source_world/CatanCraft_Season1_R7_Development')
    p.add_argument('--output',type=Path,default=HERE/'previews')
    p.add_argument('--mode',choices=['forest','route','all'],default='all')
    a=p.parse_args();a.output.mkdir(parents=True,exist_ok=True)
    b=Volume(a.baseline);v=Volume(a.pilot)
    if not (a.pilot/'level.dat').exists():raise ValueError('Expected saved pilot world')
    plan=json.loads((WORK/'receipts/mainland-coast-plan/mainland-coast.json').read_bytes())
    if a.mode in ('forest','all'):
        r=forest(b,v,a.output)
        (a.output/'pine_cameras.json').write_text(json.dumps(r,indent=2)+'\n')
    if a.mode in ('route','all'):
        r=route_map(v,a.output,plan)
        (a.output/'surface_sources.json').write_text(json.dumps(r,indent=2)+'\n')
if __name__=='__main__':main()
