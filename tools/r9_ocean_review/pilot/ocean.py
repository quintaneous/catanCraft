"""Column terrain for the north estuary, north/west circuit leg and Brinewatch pilot."""
from functools import lru_cache
from math import sin, cos, hypot, exp
from pathlib import Path
import json

SEA = 75
# Deliberate survey extent, not a complete ring. Coordinates are half open.
EXTENTS = ((-1568,-1408,224,-1024),(-1568,-1024,-1024,-224))
PRIMARY_ROUTE = ((44,-1025),(44,-1220),(-1220,-1220),(-1220,-430),(-1362,-430),(-1383,-430),(-1383,-431))
ALTERNATE_ROUTE = ((-1362,-430),(-1362,-464),(-1419,-464),(-1419,-454))
SEAM = {side: [(y,block,water) for n,y,block,water in runs for _ in range(n)]
        for side,runs in json.loads(Path(__file__).with_name('ocean-seam.json').read_bytes()).items()}
BLOCKS={s:'minecraft:'+s for s in ('sand','gravel','stone','grass_block','andesite',
                                     'cobblestone','dirt','clay','sandstone','smooth_basalt','tuff')}

def pilot_chunks():
    chunks=set()
    for x0,z0,x1,z1 in EXTENTS:
        chunks.update((cx,cz) for cx in range(x0//16,x1//16) for cz in range(z0//16,z1//16))
    return chunks

def smooth(t):
    t=min(1.,max(0.,t))
    return t*t*(3.-2.*t)

def lerp(a,b,t):
    return a+(b-a)*t

def inside(x,z):
    return any(x0<=x<x1 and z0<=z<z1 for x0,z0,x1,z1 in EXTENTS)

def _seam(x,z):
    if x>=-1024: return -1024-z, x, SEAM['north'][x+1024]
    if z>=-1024: return -1024-x, -2048-z, SEAM['west'][z+1024]
    # NW corner extends radially from a single shared authored boundary column.
    return hypot(x+1024,z+1024),-1024,SEAM['west'][0]

def _substrate(x,z,h):
    n=sin(x*.18+z*.076)+cos(z*.12-x*.034)+.7*sin(x*.05-z*.087)
    if h>=72: return 'gravel' if n>.85 else 'sand'
    if h>=66: return 'clay' if n<-.74 else 'gravel' if n>.68 else 'sand'
    return 'tuff' if n>1.35 else 'gravel' if n>.0 else 'clay' if n<-.89 else 'sand'

# Small offshore rock stacks remain outside the shipping lane and monument envelope.
STACKS=((-570,-1304,15,11,18),(-594,-1312,9,7,12),(-603,-1296,5,4,7),
        (-1325,-870,21,12,23),(-1301,-858,11,8,10))

def offshore_column(x,z):
    """Return (topSolidY, topMaterial, waterTopY, dryMaterialOrNone) for an exterior column.

    The wet range is topSolidY+1 through 75, inclusive. Air begins at 76 over water.
    Never call for an existing R7 column or for an ungenerated pilot chunk.
    """
    if not inside(x,z): raise ValueError(f'Outside offshore pilot: {(x,z)}')
    distance,s,base=_seam(x,z)
    h0,b0,w0=base
    if distance<=1:
        return h0,BLOCKS[b0],SEA,None if w0 is not None else BLOCKS[b0]
    coastal_width=61+16*sin(s*.014)+14*sin(s*.041+.7)+8*cos(s*.0092-.2)
    coastal_width=max(35,min(96,coastal_width))
    # Uneven nearshore relief fades to sand and open water without altering the R7 bank.
    shore_noise=1.4*sin(x*.129+z*.044)+1.1*cos(z*.105-x*.022)
    cliffs=(1+sin(s*.023))*.5
    blend=smooth((distance-coastal_width*.26)/(coastal_width*.76))
    h=lerp(h0+shore_noise*smooth(distance/18)*cliffs,71,blend)
    if distance>coastal_width:
        offshore=59+2*sin(x*.019+z*.007)+2*cos(z*.028-x*.008)+sin(x*.071)
        h=lerp(71,offshore,smooth((distance-coastal_width)/85))
    # Keep coherent shoals and small exposed stacks well outside boat lanes.
    for sx,sz,rx,rz,relief in STACKS:
        if abs(x-sx)>rx*3 or abs(z-sz)>rz*3: continue
        radius=hypot((x-sx)/rx,(z-sz)/rz)
        top=59+relief*exp(-radius*radius*.95)+2.2*exp(-radius*radius*.55)*sin(x*.23+z*.31)
        h=max(h,top)
    # The actual river exits at frozen row -1024. Broaden each successive cross
    # section gradually with a long sandy spit west and short gravel bank east.
    plug=None
    if z<-1024 and abs(x-44)<155 and distance<185:
        t=-1024-z
        c=44+min(t,100)*.10 + 3*sin(t/32)
        width=11+(.26*t if t<100 else 26+(.53*(t-100)))
        riverbed=lerp(71,62,smooth(t/127))
        # Match actual wet/dry profile at the seam; carry its cut into adjacent banks.
        edge=abs(x-c)/width
        if edge<1.45:
            cross=lerp(riverbed,79.5,smooth((edge-.47)/.91))
            fh=lerp(h0,cross,smooth((t-1)/10))
            h=min(h,fh)
            if h<76: plug='sand' if x<c else ('gravel' if sin(x*.14+z*.075)>0 else 'sand')
    # A broad U-shaped channel follows the inlet to the external fairway. The
    # shoulders feather into the adjacent bed; six blocks of depth begin at −1072.
    if z < -1024 and abs(x-44)<40:
        u=-1024-z
        deep_core=4+8*smooth((u-8)/40)
        shoulder=1-smooth((abs(x-43.5)-deep_core)/7)
        cap=lerp(72,68,smooth((u-6)/42))
        h=lerp(h,min(h,cap),shoulder)
    # Maintain a continuous nine-block minimum depth under the island core and
    # bring its western/northern landing approaches smoothly onto that shelf.
    rx=(x+1410)/37; rz=(z+430)/34
    r2=rx*rx+rz*rz
    if r2<4: h=lerp(h,66,1-smooth((r2-1)/3))
    y=round(h)
    if distance<3 and w0 is not None: y=min(y,h0)
    if plug: surface=plug
    elif y<=SEA: surface=_substrate(x,z,y)
    elif distance<8: surface=b0
    elif distance>104: surface='andesite' if sin(x*.29+z*.17)>.15 else 'stone'
    elif y<79: surface='sand' if sin(s*.038)<.46 else 'gravel'
    elif cliffs>.81 and distance>coastal_width*.32: surface='stone' if shore_noise>0 else 'andesite'
    else: surface='grass_block'
    # A water block at 75 above the soil is a wet surface; bed reaching 75 is dry.
    mat=BLOCKS[surface]
    return y,mat,SEA,mat if y>=SEA else None

def decorate(world):
    """Solar/weathered natural accents restricted to the exteriors of the two mouth banks."""
    _place_buoys(world)
    # Keep this sparse at the navigable inlet and keep all decorative writes away from R7.
    for x0,z0,rx,rz in ((3,-1043,13,9),(-12,-1063,13,6),(87,-1046,13,6),
                         (-533,-1076,17,8),(-1095,-672,11,15)):
        for x in range(x0-rx,x0+rx+1):
            for z in range(z0-rz,z0+rz+1):
                if not inside(x,z):continue
                y,mat,_,dry=offshore_column(x,z)
                if not dry or y<77: continue
                h=(x*73428767 ^ z*912931 ^ x*z*17)&0xffffffff
                if h%19==0 and mat=='minecraft:grass_block':
                    world.set(x,y+1,z,'minecraft:grass')
                elif h%107==0 and mat in ('minecraft:gravel','minecraft:andesite','minecraft:stone'):
                    world.set(x,y+1,z,'minecraft:andesite')

def pilot_buoys():
    """Permanent review packet geometry. Keep green marks on starboard outbound."""
    marks=[]
    def mark(x,z,name,turn=False):marks.append(dict(x=x,z=z,id=name,turn=turn))
    # Exit is narrower than eight blocks until offshore: the first marker is in the widened reach.
    for i,z in enumerate([-1115,-1198]):mark(58,z,'exit-'+str(i+1))
    # Travel west: starboard is north. Travel south: starboard is west.
    for i,x in enumerate(range(-36,-1200,-84)):mark(x,-1244,'north-'+str(i+1))
    for i,z in enumerate(range(-1136,-474,84)):mark(-1244,z,'west-'+str(i+1))
    for i,x in enumerate([-1270,-1348]):mark(x,-454,'station-'+str(i+1))
    for i,(x,z) in enumerate([(64,-1239),(71,-1233),(-1244,-1241),(-1251,-1234),(-1245,-455),(-1252,-462)]):
        mark(x,z,'turn-'+str(i+1),True)
    return marks

def _place_buoys(world):
    for b in pilot_buoys():
        x,z=b['x'],b['z'];floor,_,_,dry=offshore_column(x,z)
        if dry or floor>71:raise ValueError(f"Buoy outside deep water: {b}")
        # A chain attaches the buoy to a small stone anchor; fixed spars have no entity tick cost.
        world.set(x,floor,z,'minecraft:polished_andesite')
        for y in range(floor+1,75):world.set(x,y,z,'minecraft:chain[axis=y,waterlogged=true]')
        for dx,dz in [(0,0),(-1,0),(1,0),(0,-1),(0,1)]:
            world.set(x+dx,75,z+dz,'minecraft:waxed_oxidized_copper')
        world.set(x,76,z,'minecraft:waxed_oxidized_copper')
        world.set(x,77,z,'minecraft:white_concrete')
        world.set(x,78,z,'minecraft:waxed_oxidized_copper')
        world.set(x,79,z,'minecraft:sea_lantern')
        if b['turn']:
            world.set(x,80,z,'minecraft:yellow_concrete')
            world.set(x,81,z,'minecraft:sea_lantern')
