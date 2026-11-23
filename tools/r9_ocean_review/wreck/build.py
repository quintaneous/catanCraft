"""Build Brinewake, a standalone Sponge v2 freighter wreck for Minecraft Java 1.20.1."""
import gzip
import math
from pathlib import Path
import struct

HERE = Path(__file__).resolve().parent
W,H,L = 74,39,48
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
    a=((x-38)/30)**2+((z-23)/14)**2
    b=((x-48)/14)**2+((z-25)/10)**2
    n=.65*math.sin(x*.49+z*.31)+.42*math.cos(z*.89-x*.32)
    return int(max(1,2+9*math.exp(-a)+4*math.exp(-b)+n))
for x in range(W):
    for z in range(L):
        floor=shoal(x,z)
        top=('gravel' if floor<8 else 'andesite') if hashpos(x,0,z)%5 else 'tuff'
        for y in range(SEA+1):
            put(x,y,z, WATER if y>floor else top if y==floor else 'stone')

def center(x): return 22 if x<43 else 25

def width(x):
    if x<6 or x>66: return -1
    if x<18: return int((x-6)*.68)+1
    if x<=60: return 9
    return 9-(x-60)//2

def broken(x,z):
    # Two jagged cut faces with a flooded gap and exposed longitudinal frames.
    return 39+(z+1)//5 <= x <= 42+(z+1)//5

def deck(x): return 19 if x<43 else 18

def plating(x,y,z,upper):
    base= 'gray_concrete' if y>=upper-2 else 'red_terracotta'
    rust=math.sin(.42*x+.20*z)+.75*math.cos(.76*x-.34*y+.27*z)
    if rust>1.0 and y<upper: return 'terracotta' if hashpos(x,y,z)%4 else 'cut_copper'
    if y==upper-2 and rust<1.10: return 'light_gray_concrete'
    return base

for x in range(6,67):
    upper=deck(x); c=center(x)
    for y in range(10,upper+1):
        half=max(0,width(x)-max(0,(16-y+1)//2))
        for z in range(c-half,c+half+1):
            if broken(x,z): continue
            edge=abs(z-c)==half or x==66
            if y==10 or edge:
                put(x,y,z,plating(x,y,z,upper))
            elif y==upper:
                put(x,y,z,'spruce_planks' if x%9 else 'stripped_dark_oak_log[axis=z]')
            elif y>SEA:
                put(x,y,z,AIR)
    for side in (-1,1):
        z=c+side*width(x)
        if not broken(x,z) and x%9 not in {0,1} and not (side>0 and 19<=x<=22 or side<0 and 32<=x<=35):
            put(x,upper+1,z,'iron_bars')
    if x%6==0:
        for z in range(c-width(x)+1,c+width(x)):
            if not broken(x,z) and x<58: put(x,13,z,'waxed_weathered_cut_copper')
# Exposed ribs and flooded end of the main hold.
for x in (40,43,47,50):
    for z in range(16,31):
        if broken(x,z): put(x,12,z,'waxed_exposed_copper')
for x in range(40,51):
    put(x,12,22,'polished_deepslate')
for x in range(27,37):
    for z in range(19,25):
        put(x,19,z,AIR)
for x in range(26,38):
    for z in (18,25): put(x,20,z,'waxed_exposed_cut_copper')
for x in (26,37):
    for z in range(18,26): put(x,20,z,'waxed_exposed_cut_copper')
# Broken hatch covers half of the opening, leaving the flood visible.
for x in range(27,31):
    box(x,x,19,19,19,24,'dark_oak_planks')

def container(x0,x1,base,z0,z1,body,band):
    for x in range(x0,x1+1):
        for y in range(base,base+4):
            for z in range(z0,z1+1):
                if y in (base,base+3) or x in (x0,x1) or z in (z0,z1):
                    put(x,y,z,band if x in (x0,x1) or (x-x0)%3==0 and y<base+3 else body)
    for z in (z0+1,z1-1):
        for y in (base+1,base+2): put(x1,y,z,'polished_andesite')
container(17,24,20,16,19,'cyan_terracotta','waxed_weathered_cut_copper')
container(25,31,20,27,30,'red_terracotta','brown_terracotta')
container(48,54,19,19,22,'yellow_terracotta','orange_terracotta')

# Sheltered landings, open rail gates, and stairs rising toward the main deck.
def stair(facing):
    return 'spruce_stairs[facing='+facing+',half=bottom,shape=straight,waterlogged=false]'
for side,first in ((1,19),(-1,32)):
    edge=22+side*9
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
for x in range(40,52):
    for z in (28,29):
        put(x,18,z,'stripped_dark_oak_log[axis=x]' if z==29 else 'dark_oak_planks')
# Tow bollards and forward mast, hand winch, kingpost cargo derrick.
for x in (11,14):
    for z in (20,24):
        put(x,20,z,'polished_blackstone_wall')
for y in range(20,32): put(16,y,22,'stripped_dark_oak_log[axis=y]')
for x in range(13,20): put(x,28,22,'dark_oak_fence')
put(16,32,22,'lantern[hanging=false,waterlogged=false]')
box(36,37,20,28,26,27,'yellow_terracotta')
for i in range(11):
    y=27+i//2; x=37+i
    put(x,y,26,'waxed_exposed_cut_copper')
    put(x,y+1,26,'yellow_terracotta')
for y in range(26,32): put(47,y,26,'chain[axis=y,waterlogged=false]')
put(47,25,26,'iron_bars')
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
cabin(55,63,19,20,30,4,True)
box(57,58,19,21,30,30,AIR)
box(61,62,19,19,21,23,'spruce_planks')
cabin(56,62,24,21,28,4,False)
box(58,59,24,26,28,28,AIR)
box(57,57,24,24,22,25,'polished_deepslate')
put(57,25,22,'stone_button[face=floor,facing=west,powered=false]')
for y in range(19,24):
    put(62,y,30,'smooth_sandstone');put(62,y,31,'ladder[facing=south,waterlogged=false]')
put(62,23,31,'ladder[facing=south,waterlogged=false]')
for y in range(24,29):
    box(60,61,y,y,29,30,'red_terracotta' if y<27 else 'polished_blackstone')
box(60,61,29,29,29,30,'polished_blackstone')
box(59,59,29,33,24,24,'polished_andesite')
box(58,60,32,32,24,24,'iron_bars')
put(59,34,24,'lightning_rod[facing=up,powered=false,waterlogged=false]')
# Cap aft decks and ballast. A recessed bronze compass tile marks a dry candidate objective.
for x in range(34,38):
    for z in range(14,17):
        put(x,19,z,'waxed_cut_copper' if x in (34,37) or z in (14,16) else 'chiseled_stone_bricks')
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
      tag(10,'Metadata',tag(8,'Name',string('Brinewake - grounded coaster'))+b'\0')])
    return gzip.compress(tag(10,'Schematic',b''.join(fields)+b'\0'),compresslevel=9,mtime=0)

if __name__=='__main__':
    (HERE/'brinewake.schem').write_bytes(compile_schem())
