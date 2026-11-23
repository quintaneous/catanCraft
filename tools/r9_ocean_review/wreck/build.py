"""Build Brinewake, a standalone Sponge v2 freighter wreck for Minecraft Java 1.20.1."""
import gzip
import math
from pathlib import Path
import struct

HERE = Path(__file__).resolve().parent
W,H,L = 88,40,54
SEA = 15
AIR, WATER = 'air','water[level=0]'
cells = [AIR]*(W*H*L)

def index(x,y,z):
    assert 0<=x<W and 0<=y<H and 0<=z<L,(x,y,z)
    return (y*L+z)*W+x

def put(x,y,z,b):
    cells[index(x,y,z)]=b

def get(x,y,z):
    return cells[index(x,y,z)]

def box(x0,x1,y0,y1,z0,z1,b):
    for y in range(y0,y1+1):
        for z in range(z0,z1+1):
            for x in range(x0,x1+1):
                put(x,y,z,b)

def hashpos(x,y,z):
    return ((x*73856093)^(y*19349663)^(z*83492791))%1009

def shoal(x,z):
    a=((x-44)/38)**2+((z-26)/16)**2
    b=((x-55)/16)**2+((z-28)/11)**2
    n=.65*math.sin(x*.49+z*.31)+.42*math.cos(z*.89-x*.32)
    return int(max(1,2+9*math.exp(-a)+4*math.exp(-b)+n))
for x in range(W):
    for z in range(L):
        floor=shoal(x,z)
        top=('gravel' if floor<8 else 'andesite') if hashpos(x,0,z)%5 else 'tuff'
        for y in range(SEA+1):
            put(x,y,z, WATER if y>floor else top if y==floor else 'stone')

def center(x): return 25 if x<53 else 28

def width(x):
    if x<5 or x>80: return -1
    if x<21: return 1+int((x-5)*.65)
    if x<=73: return 11
    return 11-(x-73)//2

def broken(x,z):
    # Jagged transverse cut, with three blocks of sideways displacement at the stern.
    return 47+(z+1)//5 <= x <= 50+(z+1)//5

def deck(x): return 19 if x<53 else 18

def plating(x,y,z,upper):
    base= 'gray_concrete' if y>=upper-2 else 'red_terracotta'
    rust=math.sin(.42*x+.20*z)+.75*math.cos(.76*x-.34*y+.27*z)
    if rust>1.0 and y<upper: return 'terracotta' if hashpos(x,y,z)%4 else 'cut_copper'
    if y==upper-2 and rust<1.10: return 'light_gray_concrete'
    return base

for x in range(5,81):
    upper=deck(x); c=center(x)
    for y in range(10,upper+1):
        half=max(0,width(x)-max(0,(16-y+1)//2))
        for z in range(c-half,c+half+1):
            if broken(x,z): continue
            edge=abs(z-c)==half or x==80
            if y==10 or edge:
                put(x,y,z,plating(x,y,z,upper))
            elif y==upper:
                put(x,y,z,'spruce_planks' if x%9 else 'stripped_dark_oak_log[axis=z]')
            elif y>SEA:
                put(x,y,z,AIR)
    for side in (-1,1):
        z=c+side*width(x)
        if not broken(x,z) and x%9 not in {0,1} and not (side>0 and 21<=x<=24 or side<0 and 40<=x<=43):
            put(x,upper+1,z,'iron_bars')
    if x%6==0:
        for z in range(c-width(x)+1,c+width(x)):
            if not broken(x,z) and x<69: put(x,13,z,'waxed_weathered_cut_copper')
# Exposed ribs and flooded end of the main hold.
for x in (48,51,55,58):
    for z in range(16,37):
        if broken(x,z): put(x,12,z,'waxed_exposed_copper')
for x in range(48,61): put(x,12,25,'polished_deepslate')

def hold(x0,x1,z0,z1,covered):
    for x in range(x0,x1+1):
        box(x,x,19,19,z0,z1,'dark_oak_planks' if x<x0+covered else AIR)
        for z in (z0-1,z1+1): put(x,20,z,'waxed_exposed_cut_copper')
    for x in (x0-1,x1+1):
        box(x,x,20,20,z0-1,z1+1,'waxed_exposed_cut_copper')

# A separate forward hatch and an open cargo hold rather than stretching the old hatch.
hold(25,31,22,27,5)
hold(36,47,23,29,3)

def container(x0,x1,base,z0,z1,body,band):
    for x in range(x0,x1+1):
        for y in range(base,base+4):
            for z in range(z0,z1+1):
                if y in (base,base+3) or x in (x0,x1) or z in (z0,z1):
                    put(x,y,z,band if x in (x0,x1) or (x-x0)%3==0 and y<base+3 else body)
    for z in (z0+1,z1-1):
        for y in (base+1,base+2): put(x1,y,z,'polished_andesite')
container(21,28,20,16,19,'cyan_terracotta','waxed_weathered_cut_copper')
container(24,33,20,31,34,'red_terracotta','brown_terracotta')
container(28,36,20,16,19,'cyan_terracotta','waxed_weathered_cut_copper')
container(28,35,24,16,19,'red_terracotta','brown_terracotta')
container(57,64,19,20,23,'yellow_terracotta','orange_terracotta')
# Midship winch: two opposing bearing posts, chain drum and operator step.
for x in (38,41):
    box(x,x,20,21,32,33,'polished_blackstone')
box(39,40,21,21,32,33,'stripped_dark_oak_log[axis=x]')
for z in (32,33):put(40,22,z,'chain[axis=z,waterlogged=false]')

# Sheltered landings, open rail gates, and stairs rising toward the main deck.
def stair(facing):
    return 'spruce_stairs[facing='+facing+',half=bottom,shape=straight,waterlogged=false]'
for side,first in ((1,21),(-1,40)):
    edge=25+side*11
    for x in range(first,first+4):
        for dz in range(0,8):
            z=edge+side*dz
            y=max(16,19-dz)
            put(x,y+1,z,AIR);put(x,y+2,z,AIR)
            put(x,y,z,stair('north' if side==1 else 'south') if dz in (1,2,3) else 'spruce_planks')
            if dz in (4,7) and x in (first,first+3):
                box(x,x,shoal(x,z)+1,15,z,z,'stripped_dark_oak_log[axis=y]')
        put(x,19,edge,'spruce_planks')
# Salvagers laid a narrow timber crossing over the breach to the surviving aft cabin.
for x in range(49,62):
    for z in (32,33):
        put(x,18,z,'stripped_dark_oak_log[axis=x]' if z==33 else 'dark_oak_planks')
# Tow bollards and forward mast, hand winch, kingpost cargo derrick.
for x in (11,15):
    for z in (23,27):
        put(x,20,z,'polished_blackstone_wall')
for y in range(20,33): put(17,y,25,'stripped_dark_oak_log[axis=y]')
for x in range(13,22): put(x,29,25,'dark_oak_fence')
put(17,33,25,'lantern[hanging=false,waterlogged=false]')

# A twin-post derrick and trussed boom carry a stranded hook above the breach.
for z in (30,34):
    box(46,47,20,31,z,z+1,'yellow_terracotta')
    box(45,48,20,20,z-1,z+1,'waxed_exposed_cut_copper')
box(46,47,30,31,30,35,'yellow_terracotta')
for i in range(15):
    y=28+i//2; x=47+i
    put(x,y,32,'waxed_exposed_cut_copper')
    put(x,y+2,32,'yellow_terracotta')
    if i%3<2: put(x,y+1,32,'yellow_terracotta')
for y in range(27,35): put(61,y,32,'chain[axis=y,waterlogged=false]')
put(61,26,32,'iron_bars')
# Aft accommodation & wheelhouse, sheltered gangway along port, open doorways.
def cabin(x0,x1,y0,z0,z1,height,portholes):
    for y in range(y0,y0+height):
        for z in range(z0,z1+1):
            for x in range(x0,x1+1):
                edge=x in (x0,x1) or z in (z0,z1)
                if not edge: continue
                window=(y==y0+1 and (z==z0 or z==z1) and (x-x0)%3==1) if portholes else (y in (y0+1,y0+2) and x not in (x0,x1) and z in (z0,z1) and (x-x0)%4!=0 or y in (y0+1,y0+2) and x==x0 and z0<z<z1)
                put(x,y,z,'light_blue_stained_glass' if window else 'smooth_sandstone' if y>=y0+1 else 'light_gray_concrete')
    box(x0-1,x1+1,y0+height,y0+height,z0-1,z1+1,'smooth_quartz')
cabin(66,76,19,23,34,4,True)
box(69,70,19,21,34,34,AIR)
box(72,74,19,19,24,26,'spruce_planks')
cabin(67,75,24,24,32,4,False)
box(70,71,24,26,32,32,AIR)
box(68,68,24,24,25,29,'polished_deepslate')
put(68,25,25,'stone_button[face=floor,facing=west,powered=false]')
# At the roof edge all ladder pieces have a solid attached face, no lip above.
for y in range(19,24):
    put(75,y,34,'smooth_sandstone')
    put(75,y,35,'ladder[facing=south,waterlogged=false]')
for y in range(24,30):
    box(73,74,y,y,33,34,'red_terracotta' if y<28 else 'polished_blackstone')
box(73,74,30,30,33,34,'polished_blackstone')
box(71,71,29,34,27,27,'polished_andesite')
box(70,72,33,33,27,27,'iron_bars')
put(71,35,27,'lightning_rod[facing=up,powered=false,waterlogged=false]')
for x in range(43,47):
    for z in range(16,19):
        put(x,19,z,'waxed_cut_copper' if x in (43,46) or z in (16,18) else 'chiseled_stone_bricks')
        put(x,20,z,AIR);put(x,21,z,AIR)
# Derive visible connections, including fence and iron-bar spans.
for y in range(H):
    for z in range(1,L-1):
        for x in range(1,W-1):
            b=get(x,y,z)
            if b not in ('dark_oak_fence','iron_bars'): continue
            links=[]
            for k,dx,dz in [('north',0,-1),('east',1,0),('south',0,1),('west',-1,0)]:
                n=get(x+dx,y,z+dz).split('[')[0]
                links.append(k+'='+str(n not in ('air','water') and (n==b or not n.endswith(('wall','stairs','chain','rod','lantern')))).lower())
            put(x,y,z,b+'['+','.join(links)+',waterlogged=false]')

def string(text):
    b=text.encode();return struct.pack('>H',len(b))+b

def tag(t,name,payload):
    return bytes([t])+string(name)+payload

def compile_schem():
    palette={state:i for i,state in enumerate(dict.fromkeys(cells))}
    assert len(palette)<128
    fields=[tag(3,'Version',struct.pack('>i',2)),tag(3,'DataVersion',struct.pack('>i',3465))]
    for name,value in [('Width',W),('Height',H),('Length',L)]:
        fields.append(tag(2,name,struct.pack('>h',value)))
    fields.extend([tag(11,'Offset',struct.pack('>iiii',3,0,0,0)),
      tag(3,'PaletteMax',struct.pack('>i',len(palette))),
      tag(10,'Palette',b''.join(tag(3,'minecraft:'+b,struct.pack('>i',i)) for b,i in palette.items())+b'\0'),
      tag(7,'BlockData',struct.pack('>i',len(cells))+bytes(palette[s] for s in cells)),
      tag(9,'BlockEntities',b'\x0a\0\0\0\0'),tag(9,'Entities',b'\x0a\0\0\0\0'),
      tag(10,'Metadata',tag(8,'Name',string('Brinewake II - grounded coaster'))+b'\0')])
    return gzip.compress(tag(10,'Schematic',b''.join(fields)+b'\0'),compresslevel=9,mtime=0)

if __name__=='__main__':
    (HERE/'brinewake.schem').write_bytes(compile_schem())
