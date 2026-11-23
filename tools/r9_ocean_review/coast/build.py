"""Generate two standalone, zero-offset Java 1.20.1 coastal review schematics."""
import argparse
import gzip
import math
from pathlib import Path
import random
import struct
import numpy as np

WIDTH, HEIGHT, LENGTH = 112, 64, 96
BASE, SEA = 57, 75
SEEDS = {"sheltered_cove": 1841, "headland_estuary": 3919}
ORIGINS = {"sheltered_cove": (-368, BASE, -1024), "headland_estuary": (-40, BASE, -1024)}


def noise(rng, spacing):
    """Smoothly interpolate fixed lattice noise without axis-aligned terraces."""
    nz, nx = LENGTH//spacing + 2, WIDTH//spacing + 2
    values = np.array([[rng.uniform(-1,1) for _ in range(nx)] for _ in range(nz)])
    z, x = np.indices((LENGTH, WIDTH))
    i, j = z//spacing, x//spacing
    u, v = (x % spacing)/spacing, (z % spacing)/spacing
    u, v = u*u*(3-2*u), v*v*(3-2*v)
    return (1-v)*((1-u)*values[i,j]+u*values[i,j+1]) + v*((1-u)*values[i+1,j]+u*values[i+1,j+1])


def profile(kind, rng):
    z, x = np.indices((LENGTH, WIDTH))
    broad, rolling, fine = noise(rng, 27), noise(rng, 11), noise(rng, 4)
    relief = 2.3*broad + 1.4*rolling + .65*fine
    if kind == "sheltered_cove":
        shore = 25 + 37*np.exp(-((x-59)/30)**2) + 5*np.sin(x/12) - 7*np.exp(-((x-97)/12)**2)
        d = z-shore + 1.7*rolling
        headland = np.exp(-((x-13)/18)**2) + .75*np.exp(-((x-97)/12)**2)
        y = SEA + np.where(d<0, np.maximum(-13, d*(.23+.30*headland)),
                           np.minimum(9,d*.23) + headland*14*(1-np.exp(-np.maximum(d,0)/9)))
        dunes = 1.8*np.sin((x+.52*z)/9) + .7*np.sin((z-.26*x)/3)
        y += relief + np.where(d>4,dunes*np.minimum((d-4)/12,1),0)
        rocky = headland > .39
        bed_river = np.zeros(x.shape,dtype=bool)
    else:
        shore = 35 + 10*np.sin((x-40)/19) - 15*np.exp(-((x-18)/11)**2) + 4*rolling
        d = z-shore
        promontory = 20*np.exp(-((x-21)/24)**2-((z-34)/32)**2)
        ridge = 9*np.exp(-((x-95)/16)**2-((z-57)/27)**2)
        land = np.minimum(12,np.maximum(0,d)*.6) + (promontory+ridge)*np.clip((d+1)/4,0,1)
        y = SEA + np.where(d<0,np.maximum(-13,d*.64),land) + relief
        # Samples at the southern cut approach the measured R7 river span, X8..30 at Z-929.
        center = 59 + 26*(1-z/95) + 4*np.sin(2*np.pi*z/95)
        width = 11 + 9*(1-z/95)**2
        distance = np.abs(x-center)
        riverbed = SEA - 5.3 - .8*(1-z/95) + (distance/width)**2*5.3 + .25*fine
        y = np.minimum(y, riverbed)
        bed_river = (distance < width*1.16) & (z>20)
        rocky = ~bed_river & ((x<45)|(x>86))
    for sx,sz,rx,rz,peak in ([(13,13,5.7,5,6),(96,8,3.4,4,3)] if kind=="sheltered_cove" else
                             [(24,8,5.2,6.6,10),(14,12,3.3,4.3,4),(99,18,4.5,5.3,5)]):
        mound = 1-((x-sx)/rx)**2-((z-sz)/rz)**2 + .19*fine
        y = np.maximum(y,SEA-2 + peak*mound)
        rocky |= mound>-.4
    return np.clip(np.rint(y),BASE+2,BASE+HEIGHT-16).astype(int), rocky, bed_river, rolling, fine


class Tile:
    def __init__(self):
        self.names = ["minecraft:air"]
        self.ids = {"air":0}
        self.blocks = np.zeros((HEIGHT,LENGTH,WIDTH),dtype=np.uint8)

    def material(self, name):
        if name not in self.ids:
            self.ids[name] = len(self.names)
            self.names.append("minecraft:"+name)
        return self.ids[name]

    def block(self,x,y,z,name, vacant=False):
        if 0<=x<WIDTH and BASE<=y<BASE+HEIGHT and 0<=z<LENGTH and (not vacant or self.blocks[y-BASE,z,x]==0):
            self.blocks[y-BASE,z,x]=self.material(name)


def fill_land(tile,kind,ground,rock,river,rolling,fine):
    dz, dx = np.gradient(ground.astype(float))
    for z in range(LENGTH):
        for x in range(WIDTH):
            y=int(ground[z,x])
            slope=math.hypot(dx[z,x],dz[z,x])
            crag=rock[z,x] and (slope>.9 or (y<SEA+4 and fine[z,x]>.08))
            for h in range(BASE,y+1):
                b="stone"
                if h%13 in (3,4) and (rolling[z,x]>-.3): b="tuff"
                if h%11==1 and fine[z,x]>.15: b="andesite"
                if h==y:
                    if river[z,x] and h<=SEA+1: b=("clay" if rolling[z,x]>.1 else "gravel")
                    elif crag: b=("andesite" if fine[z,x]>.24 else "stone")
                    elif h<=SEA+2 or (kind=="sheltered_cove" and not rock[z,x] and h<=SEA+4 and rolling[z,x]>.02):
                        b="gravel" if (fine[z,x]>.48 and h<=SEA) else "sand"
                    else: b="grass_block[snowy=false]"
                elif h>=y-3 and not crag:
                    b="sandstone" if y<=SEA+3 and not river[z,x] else "dirt"
                tile.block(x,h,z,b)
            for h in range(y+1,SEA+1): tile.block(x,h,z,"water[level=0]")


def plant(tile,kind,rng,ground):
    occupied = []
    for _ in range(350):
        x,z=rng.randrange(5,WIDTH-5),rng.randrange(8,LENGTH-5)
        y=int(ground[z,x])
        if y<SEA+6 or tile.names[tile.blocks[y-BASE,z,x]] != "minecraft:grass_block[snowy=false]": continue
        if min((math.hypot(x-ox,z-oz) for ox,oz in occupied),default=99)<6.7: continue
        if rng.random() > (.23 + .74*(math.sin(x/23+z/17)**2)): continue
        occupied.append((x,z))
        conifer=rng.random()<(.66 if kind=="headland_estuary" else .30)
        wood="spruce" if conifer else "oak"
        height=rng.randint(6,10) if conifer else rng.randint(4,6)
        leaf=wood+"_leaves[distance=7,persistent=true,waterlogged=false]"
        for h in range(1,height+1): tile.block(x,y+h,z,wood+"_log[axis=y]")
        if conifer:
            rings=[(max(2,height-5),2.5),(height-4,2.1),(height-3,2.6),
                   (height-2,1.3),(height-1,1.7),(height,.85),(height+1,.3)]
        else:
            rings=[(height-2,2.35),(height-1,2.7),(height,2.2),(height+1,1.45)]
        for h,rad in rings:
            for xx in range(-3,4):
                for zz in range(-3,4):
                    if math.hypot(xx,zz)<rad+rng.uniform(-.40,.40): tile.block(x+xx,y+h,z+zz,leaf,True)
        if len(occupied)>=31: break
    for _ in range(1100):
        x,z=rng.randrange(WIDTH),rng.randrange(LENGTH)
        y=int(ground[z,x])
        if tile.names[tile.blocks[y-BASE,z,x]]=="minecraft:grass_block[snowy=false]" and y>SEA+1:
            tile.block(x,y+1,z,rng.choices(["grass","fern","dandelion","oxeye_daisy"],[60,22,2,1])[0],True)
    # Short reeds form intermittent bands near calm water. All cane stems have valid adjacent water.
    water=tile.material("water[level=0]")
    for z in range(1,LENGTH-1):
        for x in range(1,WIDTH-1):
            if ground[z,x]!=SEA or rng.random()>.15: continue
            if tile.names[tile.blocks[SEA-BASE,z,x]] not in ("minecraft:sand","minecraft:dirt","minecraft:grass_block[snowy=false]"): continue
            if any(tile.blocks[SEA-BASE,z+dz,x+dx]==water for dx,dz in [(1,0),(-1,0),(0,1),(0,-1)]):
                for n in range(1,1+rng.randint(1,3)): tile.block(x,SEA+n,z,"sugar_cane[age=0]",True)


def string(s):
    data=s.encode("utf8")
    return struct.pack(">H",len(data))+data


def named(tag,name,data):
    return bytes([tag])+string(name)+data


def schematic(tile,title):
    # Block IDs fit a single VarInt byte. Sponge v2 arrays use x + z*width + y*width*length.
    assert len(tile.names)<128
    tags=[named(3,"Version",struct.pack(">i",2)),named(3,"DataVersion",struct.pack(">i",3465))]
    for key,num in [("Width",WIDTH),("Height",HEIGHT),("Length",LENGTH)]: tags.append(named(2,key,struct.pack(">h",num)))
    tags += [named(11,"Offset",struct.pack(">iiii",3,0,0,0)),
             named(10,"Metadata",named(8,"Name",string(title))+b"\0"),
             named(3,"PaletteMax",struct.pack(">i",len(tile.names))),
             named(10,"Palette",b"".join(named(3,s,struct.pack(">i",i)) for i,s in enumerate(tile.names))+b"\0")]
    raw=tile.blocks.tobytes()
    tags.append(named(7,"BlockData",struct.pack(">i",len(raw))+raw))
    for key in ("Entities","BlockEntities"): tags.append(named(9,key,struct.pack(">bi",10,0)))
    return gzip.compress(named(10,"Schematic",b"".join(tags)+b"\0"),compresslevel=9,mtime=0)


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("output",type=Path)
    out=p.parse_args().output
    out.mkdir(parents=True,exist_ok=True)
    for kind,seed in SEEDS.items():
        rng=random.Random(seed)
        ground,rock,river,rolling,fine=profile(kind,rng)
        t=Tile()
        fill_land(t,kind,ground,rock,river,rolling,fine)
        plant(t,kind,rng,ground)
        (out/("r9_"+kind+".schem")).write_bytes(schematic(t,"R9 "+kind.replace("_"," ")))
if __name__=="__main__": main()
