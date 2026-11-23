"""Axonometric preview reconstructed from the published Sponge schematic. Pillow required."""
import gzip
import hashlib
import io
import math
from pathlib import Path
import random
import struct
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent
def decode(path):
    f=io.BytesIO(gzip.decompress(path.read_bytes()))
    def number(fmt):return struct.unpack(fmt,f.read(struct.calcsize(fmt)))[0]
    def string():return f.read(number('>H')).decode()
    def data(t):
        if t==2:return number('>h')
        if t==3:return number('>i')
        if t==7:return f.read(number('>i'))
        if t==8:return string()
        if t==9:
            subtype=number('>B');return [data(subtype) for _ in range(number('>i'))]
        if t==10:
            d={}
            while k:=number('>B'):
                name=string();d[name]=data(k)
            return d
        if t==11:return [number('>i') for _ in range(number('>i'))]
        raise ValueError(t)
    assert number('>B')==10 and string()=='Schematic'
    d=data(10);assert not f.read()
    w,h,l=d['Width'],d['Height'],d['Length']
    byid={i:s for s,i in d['Palette'].items()};ids=[];k=shift=0
    for b in d['BlockData']:
        k|=(b&127)<<shift
        if b&128:shift+=7
        else:ids.append(k);k=shift=0
    assert not shift and len(ids)==w*h*l
    return d,{(i%w,i//(w*l),(i//w)%l):byid[k] for i,k in enumerate(ids) if byid[k]!='minecraft:air'}

COL={
'stone':'82908d','deepslate':'56656a','andesite':'889997','tuff':'738783',
'cobblestone':'7e8987','mossy_cobblestone':'839167','gravel':'acaaa0','grass_block':'819855','grass':'708e56',
'calcite':'e5e4d5','red_terracotta':'a24b3d','white_terracotta':'dabca6','stone_bricks':'939d99',
'polished_andesite_slab':'a1a9a4','weathered_cut_copper_stairs':'719580','polished_andesite':'a1a9a4','stone_brick_stairs':'929d98','bricks':'ad6450','brick_wall':'a96953',
'dark_oak_log':'594535','dark_oak_slab':'5f4433','stripped_dark_oak_log':'634936',
'spruce_planks':'a47d51','spruce_stairs':'95724e','spruce_door':'8d663e','spruce_fence':'876640',
'stripped_spruce_log':'937853','stripped_oak_log':'ad9365','dark_oak_fence':'634e3b',
'oxidized_cut_copper_stairs':'489888','oxidized_cut_copper':'54998a','weathered_cut_copper':'749f83','oxidized_copper':'508e80',
'glass':'9fc3bf','light_blue_stained_glass':'90b1bf','sea_lantern':'ffefa2','lightning_rod':'ab744e','ladder':'bb955d',
'lantern':'ffc66f','chain':'56615b','flower_pot':'ae7555','bookshelf':'99773e','loom':'aa9367','crafting_table':'917545',
'cartography_table':'a57c50','white_wool':'dedacb','cyan_wool':'388a93'}
def components(state):
    name=state[10:].split('[')[0]
    p={}
    if '[' in state:
        p=dict(t.split('=') for t in state.split('[',1)[1][:-1].split(','))
    return name,p
def boxes(name,s):
    if name.endswith('_fence'):
        a=[(.36,0,.36,.64,1,.64)]
        for y in (.34,.72):
            for side,b in [('east',(.52,y,.44,1,y+.15,.56)),('west',(0,y,.44,.5,y+.15,.56)),('north',(.44,y,0,.56,y+.15,.5)),('south',(.44,y,.5,.56,y+.15,1))]:
                if s.get(side)=='true':a.append(b)
        return a
    if name=='lantern':return [(.32,.15,.32,.68,.68,.68),(.42,.68,.42,.58,.92,.58)]
    if name in ('chain','lightning_rod'):return [(.43,0,.43,.57,1,.57)]
    if name.endswith('_slab'):return [(0,0,0,1,.5,1)]
    if name.endswith('_stairs'):
        st={'north':(0,.5,0,1,1,.5),'south':(0,.5,.5,1,1,1),'west':(0,.5,0,.5,1,1),'east':(.5,.5,0,1,1,1)}
        return [(0,0,0,1,.5,1),st[s['facing']]]
    if name.endswith('_door'):
        return [{'south':(0,0,.8,1,1,1),'west':(0,0,0,.2,1,1),'east':(.8,0,0,1,1,1)}[s['facing']]]
    if name=='ladder':
        return {'south':[(0,.21,0,1,.32,.12),(0,.66,0,1,.77,.12)],'east':[(0,.21,0,.12,.32,1),(0,.66,0,.12,.77,1)]}[s['facing']]
    if name=='grass':return [(.30,0,.33,.55,.39,.55),(.52,0,.48,.65,.25,.75)]
    if name.endswith('_wall'):return [(.29,0,.29,.71,1,.71)]
    if name=='flower_pot':return [(.30,0,.30,.70,.4,.70)]
    return [(0,0,0,1,1,1)]
def render(blocks,output):
    factor=2
    im=Image.new('RGB',(1152*factor,880*factor)); dr=ImageDraw.Draw(im)
    for j in range(im.height):
        t=j/im.height
        c=tuple(int(v+t*d) for v,d in [(42,-12),(92,-17),(103,-13)])
        dr.line([(0,j),(im.width,j)],fill=c)
    rng=random.Random(775)
    for _ in range(1550):
        x,y=rng.randrange(im.width),rng.randrange(im.height)
        if .04<y/im.height<.96:
            size=rng.choice((3,5,9,13))*factor
            dr.line([(x,y),(x+size,y-1)],fill=rng.choice(('#366370','#396e77','#30606c')),width=factor)
    def raw(x,y,z):return ((x+z)*.78,(-x+z)*.38-y*.91)
    corners=[raw(x+i,y+j,z+k) for x,y,z in blocks if y>=8 for i,j,k in [(0,0,0),(1,1,1),(0,1,1),(1,0,0)]]
    x0=min(a for a,b in corners);x1=max(a for a,b in corners);y0=min(b for a,b in corners);y1=max(b for a,b in corners)
    scale=min((im.width-124*factor)/(x1-x0),(im.height-110*factor)/(y1-y0))
    ox=(im.width-scale*(x0+x1))*.5;oy=(im.height-scale*(y0+y1))*.5
    def pt(x,y,z):
        a,b=raw(x,y,z);return (round(ox+scale*a),round(oy+scale*b))
    # A flat display sea at the registered block-top plane, not additional schematic blocks.
    for (x,y,z),b in blocks.items():
        if y in (2,6,8):
            dr.polygon([pt(x,9,z),pt(x+1,9,z),pt(x+1,9,z+1),pt(x,9,z+1)],fill={2:'#326572',6:'#457782',8:'#648288'}[y])
    parsed={s:components(s) for s in set(blocks.values())}
    expanded={s:boxes(*p) for s,p in parsed.items()}
    full={s for s,b in expanded.items() if b==[(0,0,0,1,1,1)] and not parsed[s][0].endswith('glass')}
    glassdraw=ImageDraw.Draw(im,'RGBA')
    def shade(col,k):
        return tuple(max(0,min(255,round(int(col[i:i+2],16)*k))) for i in (0,2,4))
    for (x,y,z),state in sorted(blocks.items(),key=lambda kv:(-kv[0][0]+kv[0][1]+kv[0][2],kv[0][1],kv[0][2])):
        if y<9:continue
        name,p=parsed[state];base=COL[name]
        tint=1+(((x*31+y*7+z*17)%13)-6)*.006
        for xb,yb,zb,xe,ye,ze in expanded[state]:
            left,bot,back=x+xb,y+yb,z+zb;right,top,front=x+xe,y+ye,z+ze
            faces=[
                ((x,y+1,z),[(left,top,back),(right,top,back),(right,top,front),(left,top,front)],1.10),
                ((x-1,y,z),[(left,bot,back),(left,top,back),(left,top,front),(left,bot,front)],.80),
                ((x,y,z+1),[(left,bot,front),(left,top,front),(right,top,front),(right,bot,front)],.94)]
            for neighbor,points,brightness in faces:
                if state in full and blocks.get(neighbor) in full:continue
                screen=[pt(*point) for point in points]
                if name.endswith('glass'):
                    if blocks.get(neighbor)==state:continue
                    glassdraw.polygon(screen,fill=(*shade(base,tint*brightness),64))
                    glassdraw.line([screen[0],screen[1]],fill=(158,201,204,150),width=factor)
                else:dr.polygon(screen,fill=shade(base,tint*brightness))
    im.resize((1152,880),Image.Resampling.LANCZOS).save(output,optimize=True)
if __name__=='__main__':
    _,b=decode(ROOT/'Brinewatch_Pilot_Station.schem')
    render(b,ROOT/'Brinewatch_Pilot_Station.png')
