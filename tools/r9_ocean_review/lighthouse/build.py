"""Generate Brinewatch Pilot Station, a standalone Sponge v2 review asset."""
from collections import Counter
import gzip
import math
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parent
W, H, D = 55, 50, 47
SEA, DECK, YARD = 8, 9, 13
AIR = 'minecraft:air'
blocks = {}
def put(x,y,z,b):
    assert 0<=x<W and 0<=y<H and 0<=z<D,(x,y,z,b)
    if b=='air':
        blocks.pop((x,y,z),None)
    else:
        blocks[x,y,z]='minecraft:'+b
def fill(x1,y1,z1,x2,y2,z2,b):
    for y in range(y1,y2+1):
        for z in range(z1,z2+1):
            for x in range(x1,x2+1): put(x,y,z,b)
def noise(x,y,z):
    v=(x*73856093)^(y*19349663)^(z*83492791)
    return (v^(v>>13))%101
def disk(dx,dz,r):
    return max(abs(dx),abs(dz))<=r and abs(dx)+abs(dz)<=r+(r+1)//2
def octagon(cx,y,cz,r,b,ring=False):
    for dx in range(-r,r+1):
        for dz in range(-r,r+1):
            if disk(dx,dz,r) and (not ring or not disk(dx,dz,r-1)):
                put(cx+dx,y,cz+dz,b)
def door(x,y,z,face):
    for n,half in enumerate(('lower','upper')):
        put(x,y+n,z,f'spruce_door[facing={face},half={half},hinge=left,open=false,powered=false]')
def lantern_post(x,y,z):
    fill(x,y,z,x,y+1,z,'spruce_fence')
    put(x,y+2,z,'lantern')
def stairs(x,y,z,face,wood='spruce'):
    put(x,y,z,f'{wood}_stairs[facing={face},half=bottom,shape=straight,waterlogged=false]')
def station():
    blocks.clear()
    # Solid shelf, irregular tidal ledges and a narrow stone/lichen transition.
    for x in range(7,49):
        for z in range(4,38):
            d=math.hypot((x-27)/19,(z-20)/14.2)
            n=.065*math.sin(x*.69+z*.26)+.045*math.cos(z*.54-x*.32)
            e=d+n
            top=min(13,int(18.8-10*max(0,e-.47)**1.19*2.25))
            if e>1.19: continue
            top=max(1,top)
            for y in range(top+1):
                i=noise(x,y,z)
                rock=('deepslate' if y<5 and i<42 else 'tuff' if i<25 else 'andesite' if i<55 else 'stone')
                if y==top and top>=11:
                    rock='grass_block' if e<.89 else ('gravel' if i<41 else 'mossy_cobblestone')
                elif y==top and top>=8 and i<48: rock='gravel'
                put(x,y,z,rock)
            if top in (11,12,13) and e>.73 and noise(x,0,z)<14:
                put(x,top+1,z,'grass')
    # Granite, stone and cobbled working yard integrated into the shelf.
    for x in range(14,43):
        for z in range(13,28):
            if x<16 and z>24:continue
            for y in range(10,14): put(x,y,z,'cobblestone' if y<13 else ('andesite' if noise(x,13,z)<62 else 'stone_bricks'))
            fill(x,14,z,x,16,z,'air')
    # West quay + northern-facing ladder on the south edge; south quay has east approach.
    fill(2,DECK,22,13,DECK,25,'spruce_planks')
    fill(2,DECK,19,5,DECK,28,'spruce_planks')
    fill(34,DECK,32,37,DECK,45,'spruce_planks')
    fill(31,DECK,42,40,DECK,45,'spruce_planks')
    for x,z in [(2,19),(5,19),(2,28),(5,28),(9,22),(9,25),(13,22),(13,25),
                (31,45),(40,45),(31,42),(40,42),(34,37),(37,37),(34,32),(37,32)]:
        fill(x,0,z,x,DECK+1,z,'stripped_spruce_log')
    # Stairways climb towards the island. Clears two overhead blocks along both lanes.
    for step in range(4):
        for z in (23,24):
            x,y=11+step,DECK+1+step
            fill(x,DECK,z,x,y-1,z,'stone_bricks')
            fill(x,y,z,x,y+3,z,'air')
            stairs(x,y,z,'east','stone_brick')
        for x in (35,36):
            z,y=33-step,DECK+1+step
            fill(x,DECK,z,x,y-1,z,'stone_bricks')
            fill(x,y,z,x,y+3,z,'air')
            stairs(x,y,z,'north','stone_brick')
    for x in (35,36):
        for z in (27,28,29):
            fill(x,10,z,x,13,z,'stone_bricks')
            fill(x,14,z,x,16,z,'air')
    for x,z in [(2,20),(40,43),(13,25),(34,32)]: lantern_post(x,10,z)
    for x,y,z,facing in [(3,8,29,'south'),(41,8,44,'east')]:
        for level in (y,y+1):
            # Top ladder needs deck block backing; lower rung has an embedded pile.
            dx,dz=(0,-1) if facing=='south' else (-1,0)
            if level==8:put(x+dx,level,z+dz,'stripped_spruce_log')
            put(x,level,z,'ladder[facing='+facing+',waterlogged='+('true' if level<=SEA else 'false')+']')
    # Tapered, hollow, octagonal signal tower with three daymark bands and a central ladder.
    cx,cz=19,17
    for y in range(14,40):
        r=5 if y<24 else 4
        shade='red_terracotta' if 22<=y<=24 or 32<=y<=34 else 'calcite'
        if y in (14,15): shade='polished_andesite'
        octagon(cx,y,cz,r,shade,ring=True)
    # Closed entrance door on south elevation, wall only one block deep here.
    door(cx,14,cz+5,'south')
    for y in (19,28,37):
        r=5 if y<24 else 4
        for x,z in [(cx+r,cz),(cx-r,cz),(cx,cz-r),(cx,cz+r)]:
            fill(x,y,z,x,y+1,z,'light_blue_stained_glass')
    for y in (22,31,39):
        octagon(cx,y,cz,4 if y==22 else 3,'spruce_planks')
        put(cx+1,y,cz,'air')
    for y in range(14,41):
        put(cx,y,cz,'stripped_oak_log')
        put(cx+1,y,cz,'ladder[facing=east,waterlogged=false]')
    # Gallery and lantern chamber. Continuous internal exit onto the top floor.
    octagon(cx,39,cz,6,'polished_andesite')
    put(cx+1,39,cz,'ladder[facing=east,waterlogged=false]')
    for dx in range(-6,7):
        for dz in range(-6,7):
            if disk(dx,dz,6) and any(not disk(dx+ex,dz+ez,6) for ex in (-1,0,1) for ez in (-1,0,1)):
                put(cx+dx,40,cz+dz,'dark_oak_fence')
    # Covered threshold and stone jambs on the tower's south entrance.
    for x in (cx-1,cx+1):
        fill(x,14,cz+5,x,16,cz+5,'stone_bricks')
    fill(cx-2,17,cz+5,cx+2,17,cz+6,'polished_andesite_slab[type=bottom]')
    for y in range(40,45):
        octagon(cx,y,cz,3,'glass',ring=True)
        for dx,dz in [(3,0),(-3,0),(0,-3),(0,3)]:
            put(cx+dx,y,cz+dz,'oxidized_cut_copper')
    for y in (40,41):put(cx,y,cz+3,'air')
    # Real luminous source above head height, behind the glazed lantern enclosure.
    for y in (42,43):put(cx,y,cz,'sea_lantern')
    put(cx,44,cz,'chain[axis=y,waterlogged=false]')
    for y,r,b in [(45,4,'oxidized_cut_copper'),(46,3,'weathered_cut_copper'),(47,2,'oxidized_cut_copper'),(48,1,'oxidized_copper')]:
        octagon(cx,y,cz,r,b)
    put(cx,49,cz,'lightning_rod[facing=up,powered=false,waterlogged=false]')
    # Keeper's cottage, white infill/timber frame, ridge running east–west.
    for x in range(28,42):
        for z in range(13,23):
            put(x,13,z,'spruce_planks')
            for y in range(14,19):
                if x in (28,41) or z in (13,22):
                    frame=(x in (28,41) and z in (13,22)) or (z in (13,22) and x in (32,36)) or y==18
                    put(x,y,z,'stripped_dark_oak_log' if frame else 'white_terracotta')
                    if y in (15,16) and ((z in (13,22) and x in (30,31,38,39)) or (x in (28,41) and z in (15,16,20))):
                        put(x,y,z,'light_blue_stained_glass')
    door(34,14,22,'south')
    door(28,14,18,'west')
    for z in range(11,25):
        height=18+min(z-11,24-z)
        for x in range(26,44):
            if z in (17,18):
                put(x,height,z,'oxidized_cut_copper')
            else:
                stairs(x,height,z,'south' if z<18 else 'north',
                       'weathered_cut_copper' if x in (27,32,38,42) else 'oxidized_cut_copper')
        if 13<=z<=22:
            for x in (28,41):
                if height>19:fill(x,19,z,x,height-1,z,'stripped_spruce_log' if z in (17,18) else 'white_terracotta')
    # Two glazed south-facing dormers break up the long roof plane.
    for cx2 in (31,38):
        for x in range(cx2-1,cx2+2):
            fill(x,20,21,x,21,21,'stripped_dark_oak_log' if x!=cx2 else 'light_blue_stained_glass')
            for z in range(19,23):
                if x<cx2: stairs(x,22,z,'east','oxidized_cut_copper')
                elif x>cx2: stairs(x,22,z,'west','oxidized_cut_copper')
                else: put(x,23,z,'weathered_cut_copper')
    fill(39,14,14,39,25,14,'bricks')
    put(39,26,14,'brick_wall')
    for x in (30,31,38):
        put(x,14,14,'bookshelf')
    fill(37,14,20,39,14,20,'polished_andesite')
    put(39,15,20,'flower_pot')
    for x in (30,31):
        stairs(x,14,20,'south','spruce')
    put(33,14,15,'crafting_table')
    put(35,14,14,'loom[facing=south]')
    for x in (30,39):put(x,17,18,'lantern[hanging=true]')
    fill(28,18,18,41,18,18,'dark_oak_log[axis=x]')
    # Built-in sleeping berth, without a respawn or container block entity.
    put(40,14,17,'white_wool');fill(40,14,18,40,14,19,'cyan_wool')
    # Covered walkway from tower side door to the west cottage entrance.
    door(24,14,17,'east')
    for x in range(24,29):
        for z in range(16,21):put(x,18,z,'dark_oak_slab[type=bottom]')
    for x,z in [(25,16),(27,20)]:fill(x,14,z,x,17,z,'stripped_spruce_log')
    # Enclosed shore-facing marker candidate inside a small boat chart pavilion.
    fill(24,13,25,28,13,29,'spruce_planks')
    for x in (24,28):
        for z in (25,29):fill(x,14,z,x,17,z,'stripped_spruce_log')
    fill(23,18,24,29,18,30,'dark_oak_slab[type=bottom]')
    put(26,14,27,'cartography_table')
    put(24,15,27,'lantern');put(24,14,27,'polished_andesite')
    for x,z in [(9,22),(34,37),(41,23),(17,24)]: lantern_post(x,10 if x in (9,34) else 14,z)
    # Only fence state connections are updated explicitly, to retain continuity in a sparse paste.
    links_to={p for p,b in blocks.items() if b.endswith(('_fence','_log'))}
    for (x,y,z),b in list(blocks.items()):
        if b.endswith('_fence'):
            links={face: str((x+dx,y,z+dz) in links_to).lower() for face,dx,dz in [('east',1,0),('north',0,-1),('south',0,1),('west',-1,0)]}
            blocks[x,y,z]=b+'['+','.join(f'{k}={v}' for k,v in links.items())+',waterlogged=false]'
    return blocks

def string(s):
    raw=s.encode('utf-8');return struct.pack('>H',len(raw))+raw
def tag(t,name,raw):return bytes([t])+string(name)+raw
def integer(n):return struct.pack('>i',n)
def encode():
    pal=[AIR]+sorted(set(blocks.values())-{AIR})
    ids={b:i for i,b in enumerate(pal)}
    assert len(pal)<128
    data=bytes(ids[blocks.get((x,y,z),AIR)] for y in range(H) for z in range(D) for x in range(W))
    palette=b''.join(tag(3,b,integer(i)) for i,b in enumerate(pal))+b'\0'
    tags=[tag(3,'Version',integer(2)),tag(3,'DataVersion',integer(3465))]
    for name,n in [('Width',W),('Height',H),('Length',D)]:tags.append(tag(2,name,struct.pack('>h',n)))
    tags.extend([tag(11,'Offset',integer(3)+struct.pack('>iii',0,0,0)),tag(10,'Metadata',tag(8,'Name',string('Brinewatch Pilot Station'))+b'\0'),tag(3,'PaletteMax',integer(len(pal))),tag(10,'Palette',palette),tag(7,'BlockData',integer(len(data))+data)])
    for name in ('BlockEntities','Entities'):tags.append(tag(9,name,bytes([10])+integer(0)))
    return gzip.compress(tag(10,'Schematic',b''.join(tags)+b'\0'),mtime=0)

if __name__=='__main__':
    station()
    p=ROOT/'Brinewatch_Pilot_Station.schem'
    p.write_bytes(encode())
    print(p.name, len(blocks), 'occupied blocks')
