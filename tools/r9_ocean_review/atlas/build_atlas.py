"""Build a read-only R7 base map and an R9 reservation overlay. All bounds are half open."""
from pathlib import Path
import argparse
import gzip
import hashlib
import json
import struct
import sys
import zlib
from collections import Counter, deque

from wreck_placement import augment as add_wreck_placement

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib import patches
import numpy as np

SOURCE = "6ed6eaf31f946195f8b75fa869308c915537a6ed"
MAP_PIN = "e50d9753c807b5fe6f8fdee9765ae09640d951cc0ba2d59f1612f0923485c986"
MANIFEST_PIN = "e7ecd5d0f0ac5c1457be5c234d2e5e08c9239007787b20e0bdc45b3e2b8d862d"
AUTHORED = [-1024,-1024,1024,1024]
BORDER = [-1600,-1600,1600,1600]
AIR = {"minecraft:air","minecraft:cave_air","minecraft:void_air"}

def check(condition, message):
    if not condition:
        raise ValueError(message)

def digest(b):
    return hashlib.sha256(b).hexdigest()

def overlap(a,b):
    return max(0,min(a[2],b[2])-max(a[0],b[0])) * max(0,min(a[3],b[3])-max(a[1],b[1]))

def touch(a,b):
    ox = min(a[2],b[2])-max(a[0],b[0])
    oz = min(a[3],b[3])-max(a[1],b[1])
    return (ox>=0 and oz>0) or (oz>=0 and ox>0)

def gap(a,b):
    return max(0,b[0]-a[2],a[0]-b[2],b[1]-a[3],a[1]-b[3])

def band(a,b,r):
    return [min(a[0],b[0])-r,min(a[1],b[1])-r,max(a[0],b[0])+r,max(a[1],b[1])+r]

def raster_color(name):
    n=name.split(':')[-1]
    if n=="water": return (92,149,166)
    if n=="grass_block": return (185,200,151)
    if "leaves" in n: return (104,148,105)
    if n in ("gravel","dirt_path","coarse_dirt"): return (147,127,100)
    if n in ("sand","sandstone"): return (221,210,166)
    if n in ("dirt","rooted_dirt","mud"): return (167,142,115)
    if any(k in n for k in ("wood","planks","log")): return (143,123,95)
    return (153,158,145)

def unpack(raw,bits,index):
    per=64//bits
    word=struct.unpack_from(">Q",raw,8*(index//per))[0]
    return (word>>((index%per)*bits))&((1<<bits)-1)

def raster(world,input_root,pins,nbt):
    """Draw the true top surface at four-block intervals, including roads and bridges."""
    image=np.zeros((512,512,3),dtype=np.uint8)
    visited=set()
    for path in sorted((world/"region").glob("*.mca")):
        raw=path.read_bytes()
        check(digest(raw)==pins[path.relative_to(input_root).as_posix()],f"Region checksum {path.name}")
        rx,rz=map(int,path.name.split('.')[1:3])
        for i in range(1024):
            pos=int.from_bytes(raw[i*4:i*4+3],"big")*4096
            check(pos and raw[pos+4]==2,"Missing or unexpected chunk encoding")
            length=int.from_bytes(raw[pos:pos+4],"big")
            c=nbt(zlib.decompress(raw[pos+5:pos+4+length])).root()
            cx,cz=rx*32+i%32,rz*32+i//32
            check((cx,cz)==(c["xPos"],c["zPos"]),"Wrong R7 chunk")
            visited.add((cx,cz))
            height=c["Heightmaps"]["WORLD_SURFACE"]
            sections={s["Y"]:s["block_states"] for s in c["sections"] if "block_states" in s}
            for z in range(2,16,4):
                for x in range(2,16,4):
                    y=unpack(height,9,z*16+x)+16*c["yPos"]-1
                    states=sections[y//16]
                    pal=states["palette"]
                    pi=0 if len(pal)==1 else unpack(states["data"],max(4,(len(pal)-1).bit_length()),(y%16)*256+z*16+x)
                    name=pal[pi]["Name"]
                    check(name not in AIR,"Air at saved surface")
                    image[(cz+64)*4+z//4,(cx+64)*4+x//4]=raster_color(name)
    check(visited=={(x,z) for x in range(-64,64) for z in range(-64,64)},"Authored bounds changed")
    return image

def mouth_survey(world):
    rows={}
    for side,zs,xs in [('north',range(-1000,-1025,-1),range(12,71)),
                       ('south',range(999,1024),range(-70,-12))]:
        path=[]
        for z in zs:
            def water(x,y):
                return world.at((x,y,z)).split('[')[0]=="minecraft:water"
            wet=[x for x in xs if water(x,75)]
            deep=[x for x in wet if all(water(x,y) for y in (73,74,75))
                  and all(world.at((x,y,z)).split('[')[0] in AIR for y in range(76,80))]
            check(deep and deep==list(range(min(deep),max(deep)+1)),f"{side} depth continuity at {z}")
            center=(deep[0]+deep[-1]+1)//2
            check(center-4>=deep[0] and center+3<=deep[-1], "Eight-block throat too wide")
            if path: check(abs(center-path[-1]['centerX'])<=1,"Discontinuous throat")
            path.append(dict(z=z,wetXInclusive=[wet[0],wet[-1]],centerX=center,clearDeepXInclusive=[deep[0],deep[-1]]))
        rows[side]=path
    return rows

def design(r7,meters,mouths,wreck,nbt):
    cities=[dict(id=t['id'],center=[t['cityCenter']['x'],t['cityCenter']['z']]) for t in r7['territories']]
    bridges=[dict(id=t['id'],xz=[t['x'],t['z']]) for t in r7['infrastructure']]
    check(len(cities)==16 and len(bridges)==4,"Unexpected map metadata")
    sites=[]
    lanes=[]
    def corridor(name,box,width,depth):
        lanes.append(dict(id=name,boundsXZ=box,nominalWidth=width,minWaterBlocks=depth))
    ring=[(-1220,-1220),(1220,-1220),(1220,1220),(-1220,1220)]
    for side,a,b in zip(('north','east','south','west'),ring,ring[1:]+ring[:1]):
        corridor('circuit_'+side,band(a,b,16),32,8)
    specs=[
        ('lighthouse_west','Harbor light','east',-1410,-430,'west',[-1362,-446,-1204,-414]),
        ('wreck_east','Grounded freighter','west',1390,280,'east',[1204,264,1342,296]),
        ('reserve_north','North reserve','south',510,-1420,'north',[494,-1372,526,-1204]),
        ('reserve_south','South reserve','north',-495,1410,'south',[-511,1204,-479,1362])]
    for name,label,approach,x,z,side,box in specs:
        bounds=[x-48,z-48,x+48,z+48]
        corridor('approach_'+side,box,32,6)
        sites.append(dict(id=name,name=label,centerXZ=[x,z],boundsXZ=bounds,approachFrom=approach,
            approachCorridor='approach_'+side,siteWidth=96,
            proposedOuterEdgeToBorder=min(bounds[0]+1600,bounds[1]+1600,1600-bounds[2],1600-bounds[3]),
            distanceToFrozenChunkEdge=gap(bounds,AUTHORED),
            stagingWaterBlocks=6))
    for side,zn,near,far in [('north',-1024,-1072,-1236),('south',1024,1072,1236)]:
        cx=mouths[side][-1]['centerX']
        corridor('mouth_'+side,[cx-4,min(zn,near),cx+4,max(zn,near)],8,3)
        corridor('estuary_'+side,[cx-12,min(near,far),cx+12,max(near,far)],24,6)
    squares={s['id']:s['boundsXZ'] for s in sites}
    rects={s['id']:s['boundsXZ'] for s in lanes}
    for key,box in {**rects,**squares}.items():
        check(overlap(box,BORDER)==(box[2]-box[0])*(box[3]-box[1]) and not overlap(box,AUTHORED),key)
    for i,a in enumerate(sites):
        for b in sites[i+1:]: check(not touch(a['boundsXZ'],b['boundsXZ']),"Sites touch")
        touching=[k for k,v in rects.items() if touch(a['boundsXZ'],v)]
        check(touching==[a['approachCorridor']] and all(not overlap(a['boundsXZ'],v) for v in rects.values()),"Site access conflict")
    for site in sites:
        x,z=site["centerXZ"]
        site["surveyMarkerXYZ"]=[x,76,z]
    sites[0]["prototypeBerthsXYZ"]={"primary":[-1385,76,-431],"secondaryLandingEnvelopeXZ":[-1423,-452,-1413,-448]}
    sites[0]["proposedApproachWaypointsXZ"]=[[-1362,-430],[-1377,-431],[-1384,-431]]
    add_wreck_placement(sites[1],wreck,nbt)
    nodes=list(rects)
    seen={nodes[0]}
    queue=deque(seen)
    while queue:
        current=queue.popleft()
        for k,v in rects.items():
            if k not in seen and touch(rects[current],v):
                seen.add(k);queue.append(k)
    check(seen==set(nodes),"Disconnected lane plan")
    return dict(schema=1,sourceCommit=SOURCE,mapSHA256=MAP_PIN,boundsConvention="minX,minZ,maxXExclusive,maxZExclusive",
        negativeZ="north",currentBorderWidth=meters,proposedBoundsXZ=BORDER,authoredBoundsXZ=AUTHORED,
        waterBlockY=75,waterSurfacePlaneY=76,
        authoredBorderAdditionalChunks=((BORDER[2]-BORDER[0])//16)**2-((AUTHORED[2]-AUTHORED[0])//16)**2,
        proposedTransitionOuterBoundsXZ=[-1120,-1120,1120,1120],
        sideDistanceFromShorelineToBorderRange=[480,576],
        shorelineAssumption="Future shoreline may occupy the 96-block strip immediately outside frozen authored chunks. Axial distance to border, excluding islands and not a sounded-water claim.",
        mainlandSampling="Surface palette at X and Z every four blocks. R7 region files verified against original manifest.",
        existingCities=cities,existingBridgeAnchors=bridges,
        measuredR7FringeBoatChannel=dict(minWidth=8,minWaterBlocks=3,clearAirBlocks=4,rows=mouths),
        sites=sites,lanes=lanes,checks=dict(knownClaims=16,authoredChunksRead=16384,
            proposedLaneRegions=len(lanes),connectedLaneRegions=len(seen),reservations=len(sites),
            proposedOverlapWithAuthoredColumns=0,siteOverlapWithThroughOrApproachLanes=0,
            placementAndBoatTests="pending; route outside current authored chunks is a design envelope"))

def draw(output,doc,image):
    fig=plt.figure(figsize=(13.5,9.5),facecolor='#faf9f2')
    ax=fig.add_axes([.06,.10,.60,.80]); key=fig.add_axes([.69,.085,.295,.80]);key.axis('off')
    ax.set_aspect('equal'); ax.set_xlim(-1630,1630); ax.set_ylim(1630,-1630)
    ax.set_facecolor("#e2eef0")
    def box(coords,**styles):
        a,b,c,d=coords
        ax.add_patch(patches.Rectangle((a,b),c-a,d-b,**styles))
    box(doc['proposedTransitionOuterBoundsXZ'],fc='#eadcc3',ec='#c9bca5',lw=.6,zorder=-1)
    ax.imshow(image,extent=[-1024,1024,1024,-1024],origin='upper',interpolation="nearest")
    box(AUTHORED,fill=False,lw=.75,ec='#62766b')
    box([-1000,-1000,1000,1000],fill=False,lw=.85,ls=(0,(2,3)),ec='#3d5a4e')
    box(BORDER,fill=False,lw=1.1,ls=(0,(6,3)),ec='#598895')
    for row in doc['lanes']:
        box(row['boundsXZ'],fc='#32899b',ec='none',alpha=.79 if row['id'].startswith('circuit') else .67)
    for site in doc['sites']:
        x,z=site['centerXZ']; is_asset=not site['id'].startswith('reserve')
        box(site['boundsXZ'],fc='#d68158' if is_asset else '#f5ecda',ec='#985231',lw=1.5,hatch=None if is_asset else '//')
        label=site['name']
        if x < -1300: xy=(x,z-110); ha='center'
        elif x > 1300:xy=(x,z-114);ha='center';label='Freighter'
        elif z<0:xy=(x-85,z-60);ha='right'
        else:xy=(x+80,z+35);ha='left'
        ax.text(*xy,label,ha=ha,va='center',size=9,weight='bold',color='#70412b')
    for row in doc['existingCities']:
        x,z=row['center']
        ax.text(x,z,row['id'].upper(),ha='center',va='center',weight='bold',fontsize=7,color='#2c443f',
              bbox=dict(boxstyle='round,pad=.22',fc='#f9f5df',ec='#63735c',lw=.55))
    for b in doc['existingBridgeAnchors']:
        ax.plot(*b['xz'],'D',ms=4,mec='white',mew=.45,color='#44423e')
    ax.plot(0,0,'+',ms=10,mew=1.2,color='#673e39')
    for side in ('north','south'):
        fringe=doc['measuredR7FringeBoatChannel']['rows'][side]
        ax.plot([r['centerX'] for r in fringe],[r['z'] for r in fringe],color='#00667c',lw=2.3)
    ticks=[-1600,-1000,0,1000,1600]
    ax.set_xticks(ticks);ax.set_yticks(ticks)
    ax.tick_params(length=0,pad=5,labelsize=8,colors='#415e64')
    for spine in ax.spines.values():spine.set_visible(False)
    ax.set_xlabel('X  /  blocks east',fontsize=9,color='#415e64');ax.set_ylabel('Z  /  blocks south',fontsize=9,color='#415e64')
    fig.text(.06,.95,"Offshore reservations & boat routes",size=23,weight='bold',color='#263f42')
    fig.text(.06,.915,"Season 1  •  Current R7 terrain with proposed R9 overlay  •  North is up",size=11,color='#476365')
    def note(y,title,message):
        key.text(0,y,title,fontsize=11,weight='bold',color='#273f40',va='top')
        key.text(0,y-.047,message,fontsize=9.5,color='#405652',va='top',linespacing=1.42)
    note(.986,"R7 mainland","Terrain colors sample the actual saved surface.\nA–P mark cities; diamonds mark bridge anchors.\nThe cross marks the central monument district.")
    note(.789,"R9 water envelope","Dashed border: proposed 3,200-block square.\nSand tint: 96 blocks for future coastal shaping.\nSolid inland edge encloses all authored chunks;\nthe dotted edge is the current playable border.")
    note(.556,"Boat access","Teal circuit: reserve 32 clear blocks across and\n8 water blocks deep. Spurs: 32 wide, 6 deep.\nNarrow river throats use measured R7 exits.")
    note(.381,"Two prototype sites","West  •  Harbor light    (−1410, −430)\nEast   •  Grounded freighter    (+1390, +280)\nSolid boxes reserve 96 × 96 blocks each.")
    note(.205,"Two future sites","North  •  (+510, −1420)     South  •  (−495, +1410)\nHatched boxes preserve future design space.\nTerrain, buildings and events remain unplaced.")
    fig.text(.06,.033,"Authored terrain and infrastructure from gameplay source 6ed6eaf.  |  Design study for fresh-world visual review.",fontsize=9,color='#566968')
    fig.savefig(output,dpi=170);plt.close(fig)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('repo',type=Path)
    parser.add_argument('input_root',type=Path)
    parser.add_argument('output',type=Path)
    parser.add_argument('--wreck',type=Path,default=Path(__file__).resolve().parents[1]/'wreck/brinewake.schem')
    args=parser.parse_args()
    repo=args.repo.resolve();base=args.input_root.resolve();out=args.output
    out.mkdir(parents=True,exist_ok=True)
    raw=(base/'INPUT_MANIFEST.json').read_bytes();check(digest(raw)==MANIFEST_PIN,"Unexpected R7 manifest")
    pins={r['path']:r['sha256'] for r in json.loads(raw)['files']}
    raw=(repo/'src/main/resources/data/catancraft/maps/season1_river_bridges_v3.json').read_bytes()
    check(digest(raw)==MAP_PIN,"Unexpected map")
    config=json.loads(raw);sys.dont_write_bytecode=True
    check(digest((base/'nbt_check.py').read_bytes())==pins['nbt_check.py'],"Unexpected input reader")
    sys.path.insert(0,str(base));sys.path.insert(0,str(repo/'tools/walls53'))
    from nbt_check import NBT
    from audit_sites import World
    world=base/'source_world/CatanCraft_Season1_R7_Development'
    level=(world/'level.dat').read_bytes();check(digest(level)==pins[(world/'level.dat').relative_to(base).as_posix()],"Wrong level")
    meta=NBT(gzip.decompress(level)).root()['Data']
    check(meta['BorderSize']==2000 and meta['BorderSizeLerpTime']==0 and meta['BorderCenterX']==meta['BorderCenterZ']==0,"Wrong border")
    image=raster(world,base,pins,NBT)
    doc=design(config,meta['BorderSize'],mouth_survey(World(world,NBT)),args.wreck,NBT)
    (out/'atlas.json').write_text(json.dumps(doc,indent=2)+'\n')
    draw(out/'R9-offshore-atlas.png',doc,image)

if __name__=='__main__':
    main()
