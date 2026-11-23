"""Build an equal-scale review from two encoded Brinewake schematics."""
import argparse
import gzip
import hashlib
import io
import json
import math
import struct
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

HERE = Path(__file__).resolve().parent
S = 2
SCALE = 8.85
SEA = 16
SIN, COS = math.sin(math.radians(17)), math.cos(math.radians(17))
EL_SIN, EL_COS = math.sin(math.radians(24)),math.cos(math.radians(24))
PLATES = {'gray_concrete','red_terracotta','terracotta','light_gray_concrete','cut_copper'}
COLORS={'spruce_planks':(151,110,72),'dark_oak_planks':(103,76,54),'stripped_dark_oak_log':(110,85,61),'red_terracotta':(161,80,62),'gray_concrete':(93,99,101),'light_gray_concrete':(159,161,154),'brown_terracotta':(130,85,57),'terracotta':(177,107,73),'yellow_terracotta':(210,157,69),'orange_terracotta':(195,116,52),'cyan_terracotta':(84,118,121),'waxed_weathered_cut_copper':(86,153,139),'waxed_exposed_cut_copper':(172,120,91),'waxed_exposed_copper':(151,113,85),'waxed_cut_copper':(190,126,83),'cut_copper':(195,126,83),'smooth_sandstone':(219,206,169),'smooth_quartz':(235,230,211),'light_blue_stained_glass':(105,160,168),'polished_andesite':(129,136,131),'polished_deepslate':(72,82,83),'polished_blackstone':(62,66,68),'chiseled_stone_bricks':(150,145,125),'iron_bars':(149,158,152),'dark_oak_fence':(116,86,59),'chain':(82,91,92),'lightning_rod':(170,118,76),'lantern':(244,178,86),'spruce_stairs':(151,109,71),'ladder':(149,109,75),'stone_button':(160,164,152),'polished_blackstone_wall':(75,80,83),'gravel':(132,132,123),'andesite':(130,136,129),'stone':(126,132,126),'tuff':(113,125,114)}
def rgb(color,brightness):
    return tuple(max(0,min(255,round(v*brightness))) for v in color)

def read_sponge(path):
    data=io.BytesIO(gzip.decompress(path.read_bytes()))
    def num(fmt):return struct.unpack('>'+fmt,data.read(struct.calcsize('>'+fmt)))[0]
    def string():return data.read(num('H')).decode()
    def payload(tag):
        if tag in (1,2,3,4): return num({1:'b',2:'h',3:'i',4:'q'}[tag])
        if tag==7: return data.read(num('i'))
        if tag==8:return string()
        if tag==9:
            et,n=num('B'),num('i');return [payload(et) for _ in range(n)]
        if tag==10:
            result={}
            while (k:=num('B')):
                name=string();result[name]=payload(k)
            return result
        if tag in (11,12):
            n=num('i');return [num('i' if tag==11 else 'q') for _ in range(n)]
        raise ValueError(f'Unsupported tag {tag}')
    assert num('B')==10 and string()=='Schematic'
    n=payload(10);assert n['Version']==2 and n['DataVersion']==3465 and not data.read()
    pal={v:k.split(':',1)[1] for k,v in n['Palette'].items()}
    ids=n['BlockData'];assert max(ids)<128 and len(pal)<128
    blocks=np.frombuffer(ids,np.uint8).reshape(n['Height'],n['Length'],n['Width'])
    return pal,blocks

def dims(pal,blocks):
    keys=[v for v,s in pal.items() if s.split('[')[0] in PLATES]
    zs,xs=np.where(np.isin(blocks[16],keys))
    assert len(xs)
    beam=max(int(np.ptp(zs[xs==x]))+1 for x in np.unique(xs))
    return (int(xs.min()),int(xs.max())),beam,(int(zs.min()),int(zs.max()))

def font(size,bold=False):
    return ImageFont.truetype('OpenSans-Semibold.ttf' if bold else 'OpenSans-Regular.ttf',size*S)

def ship(pal,blocks,expected):
    h,l,w=blocks.shape
    xr,beam,zr=dims(pal,blocks);length=xr[1]-xr[0]+1
    assert (length,beam)==expected,(length,beam)
    xmid=(xr[0]+xr[1]+1)/2;zmid=(zr[0]+zr[1]+1)/2
    size=(782,402)
    def screen(x,y,z):
        xx,zz=x-xmid,z-zmid
        return (S*(size[0]*.50+SCALE*(COS*xx+SIN*zz)),
                S*(230+SCALE*((-SIN*xx+COS*zz)*EL_SIN-(y-SEA)*EL_COS)))
    cw,ch=(e*S for e in size)
    y,x=np.mgrid[:ch,:cw]
    wave=np.sin(x/(13*S)+y/(5*S)+np.sin(x/(25*S))) + .6*np.cos(y/(7*S)-x/(43*S))
    ys=y/ch
    ocean=np.stack([63+2.5*wave-11*ys,128+2.5*wave-10*ys,139+3*wave-9*ys],axis=-1).astype('uint8')
    surface=Image.fromarray(ocean)
    solids=[k for k,b in pal.items() if b.split('[')[0] not in ('air','water')]
    coords=np.argwhere(np.isin(blocks[SEA:],solids));coords[:,0]+=SEA
    shadow=Image.new('L',surface.size,0);sd=ImageDraw.Draw(shadow)
    for y,z,x in coords:
        t=y-SEA;ox=.31*t;oz=.24*t
        pts=[screen(x+dx+ox,SEA,z+dz+oz) for dx,dz in [(0,0),(1,0),(1,1),(0,1)]]
        sd.polygon(pts,fill=49)
    shadow=shadow.filter(ImageFilter.GaussianBlur(3*S))
    surface.paste(Image.new('RGB',surface.size,(27,62,66)),(0,0),shadow)
    d=ImageDraw.Draw(surface)
    def box(x,y,z,X,Y,Z,color,faces=(True,True,True)):
        quads=(
            [(x,y,z),(x,y,Z),(x,Y,Z),(x,Y,z)],
            [(x,y,Z),(X,y,Z),(X,Y,Z),(x,Y,Z)],
            [(x,Y,z),(X,Y,z),(X,Y,Z),(x,Y,Z)])
        for show,quad,light in zip(faces,quads,(.71,.87,1.06)):
            if show:d.polygon([screen(*p) for p in quad],fill=rgb(color,light))
    short={'iron_bars','chain','ladder','lantern','stone_button','polished_blackstone_wall','lightning_rod','dark_oak_fence','spruce_stairs'}
    def solid(x,y,z):
        if not (0<=x<w and 0<=y<h and 0<=z<l):return False
        return pal[blocks[y,z,x]].split('[')[0] not in short|{'air','water'}
    keys=-(SIN*coords[:,2])+COS*coords[:,1]+math.tan(math.radians(24))*coords[:,0]
    for idx in np.argsort(keys,kind='stable'):
        y,z,x=map(int,coords[idx]);st=pal[blocks[y,z,x]];b=st.split('[')[0]
        c=rgb(COLORS[b],1+((x*17+z*13+y*7)%13-6)*.005)
        if b in ('iron_bars','dark_oak_fence'):
            box(x+.39,y,z+.39,x+.61,y+.95,z+.61,c)
            for side,(a,g,e,j) in {'north':(.445,0,.555,.5),'south':(.445,.5,.555,1),'west':(0,.445,.5,.555),'east':(.5,.445,1,.555)}.items():
                if side+'=true' in st:
                    for hh in (.34,.72):box(x+a,y+hh,z+g,x+e,y+hh+.10,z+j,c)
        elif b=='spruce_stairs':
            box(x,y,z,x+1,y+.5,z+1,c)
            k=(0,.5) if 'facing=north' in st else (.5,1)
            box(x,y+.5,z+k[0],x+1,y+1,z+k[1],c)
        elif b=='ladder':
            for a in (.18,.79):box(x+a,y,z,x+a+.12,y+1,z+.15,c)
            for hh in (.23,.65):box(x+.18,y+hh,z,x+.91,y+hh+.11,z+.15,c)
        elif b in short:
            width={'chain':.16,'lantern':.46,'lightning_rod':.24,'stone_button':.30,'polished_blackstone_wall':.64}[b]
            k=(1-width)/2;box(x+k,y,z+k,x+1-k,y+(.24 if b=='stone_button' else 1),z+1-k,c)
        else:box(x,y,z,x+1,y+1,z+1,c,tuple(not solid(x+dx,y+dy,z+dz) for dx,dy,dz in [(-1,0,0),(0,0,1),(0,1,0)]))
    # A direction parallel to the image horizontal at sea level has no foreshortening.
    bx, by = 20*S, (size[1]-21)*S
    ex=bx+round(SCALE*10*S);white=(235,244,231)
    d.line((bx,by,ex,by),fill=white,width=2*S)
    d.line((bx,by-4*S,bx,by+3*S),fill=white,width=2*S)
    d.line((ex,by-4*S,ex,by+3*S),fill=white,width=2*S)
    d.text((bx,by-26*S),'10 blocks',font=font(15,True),fill=white)
    return surface,(w,h,l),length,beam

def render(first,second,out):
    rows=[];checks={}
    for label,path,expected in [('v1',first,(61,19)),('v2',second,(76,23))]:
        pal,blocks=read_sponge(path)
        surface,stamp,length,beam=ship(pal,blocks,expected)
        rows.append((surface,stamp,length,beam))
        checks[label]={'schematic_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'stamp_xyz':stamp,'hull_length':length,'widest_hull_cross_section':beam}
    W,H=1692,820
    im=Image.new('RGB',(W*S,H*S),'#f8f7f2');draw=ImageDraw.Draw(im);ink='#233b3b';soft='#4c6462'
    def write(x,y,s,sz,b=False,fill=ink):draw.text((x*S,y*S),s,font=font(sz,b),fill=fill)
    write(44,22,'CATANCRAFT  /  OFFSHORE MONUMENTS',18,True,'#326a61')
    write(41,49,'Brinewake hull comparison',52,True)
    write(44,133,'Both wrecks share the same camera angle, water level and blocks-per-pixel scale.',23,False,soft)
    for j,(surface,stamp,length,beam) in enumerate(rows):
        x=44+j*822
        write(x,206,'Original wreck' if j==0 else 'Larger wreck',28,True)
        im.paste(surface,(x*S,257*S))
        write(x,672,f'{length} blocks long   ×   {beam} blocks across',29,True)
        w,h,l=stamp
        write(x,721,f'Full schematic  ·  {w} wide × {l} deep × {h} high',20,False,soft)
    write(44,782,'Beam is measured at the widest hull section. Schematic dimensions include the shoal, docks, water and air.',18,False,soft)
    dest=out/'Brinewake_hull_comparison.png';im.resize((W,H),Image.Resampling.LANCZOS).save(dest,optimize=True)
    checks['png_sha256']=hashlib.sha256(dest.read_bytes()).hexdigest()
    checks['projection']={'yaw_degrees':17,'elevation_degrees':24,'pixels_per_block':SCALE,'local_water_surface_y':SEA,'world_water_surface_y':76}
    (out/'comparison.json').write_text(json.dumps(checks,indent=2)+'\n')

if __name__=='__main__':
    a=argparse.ArgumentParser();a.add_argument('first',type=Path);a.add_argument('second',type=Path);a.add_argument('--output',type=Path,default=HERE)
    args=a.parse_args();render(args.first,args.second,args.output)
