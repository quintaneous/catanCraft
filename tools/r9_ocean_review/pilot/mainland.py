"""Pine Reach road-verge landscape pilot. Read-only preparation, then explicit build/restore."""
from collections import Counter, defaultdict
from functools import lru_cache
from pathlib import Path
import gzip
import hashlib
import json
import math
import random
import sys

HOME = Path(__file__).resolve().parent
WORK = HOME.parents[1]
PLACEMENTS = HOME/"mainland-placements.json.gz"
PLAN_SHA256 = "fcd4ea05b94fe70e7b7f5af095dd8c9fe9e7f93d32c86df7fec452554ad2d550"
SEED = 715331
PARCELS = [(-352,-623,42,18),(-263,-625,53,18),(-158,-615,43,13),
           (-349,-520,38,13),(-255,-522,43,15),(-153,-522,40,14)]
LOW = (-395,-644)
HIGH = (-106,-500)
AIR = "minecraft:air"
GRASS = "minecraft:grass_block[snowy=false]"

def parcel_strength(x,z):
    distortion=.06*math.sin(x*.105+z*.071)+.06*math.cos(x*.067-z*.158)
    return max(1-((x-cx)/wx)**2-((z-cz)/wz)**2 for cx,cz,wx,wz in PARCELS)+distortion

def envelope(x,z):
    # A road-side strip and the entire final bridge approach remain outside all edits.
    return (LOW[0]<=x<=HIGH[0] and LOW[1]<=z<=HIGH[1] and not -598<=z<=-546
            and parcel_strength(x,z)>.10)

@lru_cache(maxsize=1)
def _plan():
    raw = gzip.decompress(PLACEMENTS.read_bytes())
    if hashlib.sha256(raw).hexdigest() != PLAN_SHA256:
        raise ValueError("Pine Reach placement data differs from the verified plan")
    return json.loads(raw)

@lru_cache(maxsize=1)
def _excluded():
    return {tuple(xz) for xz in _plan()["excludedColumns"]}

def allowed(x,z):
    return envelope(x,z) and (x,z) not in _excluded()

def protected(x,z):
    return not allowed(x,z)

def _canonical(state):
    if state in ("minecraft:grass_block","grass_block"):
        return GRASS
    return state

def _run(world, restore=False):
    doc=_plan()
    steps=[]
    # Verify the entire before-image and mask before changing the first voxel.
    for x,z,spans in doc["columns"]:
        if not allowed(x,z):
            raise ValueError(f"Pine Reach protected column {x},{z}")
        for y0,y1,a,b in spans:
            src,dst=doc["palette"][a],doc["palette"][b]
            if restore:
                src,dst=dst,src
            for y in range(y0,y1+1):
                actual=_canonical(world.get(x,y,z))
                if actual!=src:
                    raise ValueError(f"Pine Reach expected {src} at {(x,y,z)}, got {actual}")
                steps.append((x,y,z,dst))
    for x,y,z,b in steps:
        world.set(x,y,z,b)
    return {"operation":"restore" if restore else "build","source":doc["source"],
            "changedBlocks":len(steps),"changedColumns":len(doc["columns"]),
            "boundsInclusiveXYZ":doc["boundsInclusiveXYZ"],"features":doc["counts"],
            "originalAndTargetSpanSHA256":hashlib.sha256(PLACEMENTS.read_bytes()).hexdigest()}

def build(world):
    return _run(world)

def restore(world):
    return _run(world,True)

def _tree(rng,species,height,radius):
    state=lambda s:"minecraft:"+s
    crown=state(species+"_leaves[distance=1,persistent=true,waterlogged=false]")
    log=state(species+"_log[axis=y]")
    blocks={}
    if species=="spruce":
        # Drooping triangular boughs, in stacked whorls; summit has a narrow leader.
        crown_base=max(3,round(height*.36))
        for y in range(crown_base,height+1):
            taper=radius*(height-y)/(height-crown_base)
            local=max(.45,taper+(.85 if (y-crown_base)%3==0 else .1))
            extent=math.ceil(local)
            for x in range(-extent,extent+1):
                for z in range(-extent,extent+1):
                    if (x*x+z*z)**.5 <= local+(.27 if abs(x)+abs(z)<=extent else 0) and (abs(x)+abs(z)<2 or rng.random()>.07):
                        blocks[x,y,z]=crown
        for x,z in [(1,0),(-1,0),(0,1),(0,-1)]:
            blocks[x,crown_base-1,z]=crown
    else:
        for y in range(height-4,height+2):
            local=radius*(1-((y-(height-1.7))/4.0)**2)**.5
            for x in range(-radius,radius+1):
                for z in range(-radius,radius+1):
                    if x*x+z*z<=(local+.15)**2 and rng.random()>.045:
                        blocks[x,y,z]=crown
    for y in range(1,height+1):
        blocks[0,y,0]=log
    blocks[0,height+1,0]=crown
    return blocks

def generate():
    sys.dont_write_bytecode=True
    src=WORK/"inputs/CatanCraft_Clean_R7_Input"
    sys.path[:0]=[str(src),str(WORK/"resource-ui/tools/walls53")]
    from nbt_check import NBT
    from audit_sites import World, INPUT_SHA, pinned
    manifest=json.loads(pinned(src/"INPUT_MANIFEST.json",INPUT_SHA))
    pins={p["path"]:p["sha256"] for p in manifest["files"]}
    root=src/"source_world/CatanCraft_Season1_R7_Development"
    for name in ['nbt_check.py','bootstrap_reference/config/catancraft/map.json',
                 str((root/'region/r.-1.-2.mca').relative_to(src)),str((root/'region/r.-1.-1.mca').relative_to(src))]:
        pinned(src/name,pins[name])
    w=World(root,NBT)
    box=range(LOW[0]-12,HIGH[0]+13), range(LOW[1]-12,HIGH[1]+13)
    ground_y={}
    excluded=set()
    road_surface=set()
    for x in box[0]:
        for z in box[1]:
            is_road=False
            for y in (79,80,81):
                s=w.at((x,y,z)).partition('[')[0].removeprefix('minecraft:')
                if s in {"andesite","gravel","stone_bricks","dirt_path","smooth_stone"}:
                    is_road=True
                    break
            if is_road:
                road_surface.add((x,z))
    # Exclude the twelve-block square buffer of surveyed pavement for the entire crown.
    for x,z in road_surface:
        for xx in range(x-12,x+13):
            for zz in range(z-12,z+13):
                if LOW[0]<=xx<=HIGH[0] and LOW[1]<=zz<=HIGH[1]: excluded.add((xx,zz))
    cfg=json.loads((src/'bootstrap_reference/config/catancraft/map.json').read_bytes())
    sector=WORK/'resource-ui/src/main/resources/assets/catancraft/sector_p3/city_a.json.gz'
    from audit_sites import P3_SHA
    city_a=json.loads(gzip.decompress(pinned(sector,P3_SHA)))
    local_guards={(row[0]+750,row[2]+750) for a in city_a["assets"] for k in ("reads","fresh") for row in a[k]}
    _is_in=lambda x,z:LOW[0]<=x<=HIGH[0] and LOW[1]<=z<=HIGH[1]
    for city in cfg['territories']:
        center=city['cityCenter']
        for kind in ('siegeRegion','restorationRegion'):
            r=city[kind]
            for x in range(max(LOW[0],r['minX']),min(HIGH[0],r['maxX'])+1):
                for z in range(max(LOW[1],r['minZ']),min(HIGH[1],r['maxZ'])+1):
                    excluded.add((x,z))
        sign=1 if center['rotation']==0 else -1
        for dx,dz in local_guards:
            x,z=center['x']+sign*dx,center['z']+sign*dz
            if _is_in(x,z): excluded.add((x,z))
    rough={(x,z) for x in range(LOW[0],HIGH[0]+1) for z in range(LOW[1],HIGH[1]+1) if envelope(x,z)}
    eligible=rough-excluded
    for x,z in eligible:
        for y in range(119,67,-1):
            b=w.at((x,y,z))
            if b!=AIR:
                if b==GRASS:ground_y[x,z]=y
                break
    edits={}
    features=[]
    occupied=[]
    rng=random.Random(SEED)
    def add(kind,base,deltas,detail=None):
        if not all((x,z) in eligible and (x,y,z) not in edits and w.at((x,y,z))==a for (x,y,z),(a,b) in deltas.items()):
            return False
        edits.update(deltas)
        features.append({"kind":kind,"anchorXYZ":list(base),"blocks":len(deltas),**(detail or {})})
        return True
    points=sorted(ground_y)
    for _ in range(3200):
        x,z=rng.choice(points);y=ground_y[x,z]
        if any((x-i)**2+(z-j)**2<(s+1.4)**2 for i,j,s in occupied): continue
        strength=parcel_strength(x,z)
        if strength<=.25 or rng.random()>min(.77,strength*.62):continue
        if any(abs(ground_y.get((x+dx,z+dz),y-5)-y)>2 for dx,dz in ((0,1),(0,-1),(1,0),(-1,0))):continue
        young=rng.random()<(.58 if strength<.5 else .19)
        species="spruce" if rng.random()<.83 else "birch"
        h=rng.randint(4,7) if young else rng.randint(9,15 if species=='spruce' else 11)
        rad=(1 if h<6 else 2) if young else rng.choice((3,3,4))
        model=_tree(rng,species,h,rad)
        deltas={(x+dx,y+dy,z+dz):(AIR,b) for (dx,dy,dz),b in model.items()}
        if add(species, (x,y,z),deltas,{"height":h,"young":young}):occupied.append((x,z,rad+2))
    for _ in range(1100):
        x,z=rng.choice(points);y=ground_y[x,z]
        if any((x-i)**2+(z-j)**2<8 for i,j,s in occupied):continue
        if rng.random()>=.20:continue
        material=rng.choice(("andesite","mossy_cobblestone","cobblestone"))
        if rng.random()<.65:
            stones={(x,y+1,z):(AIR,"minecraft:"+material)}
            for dx,dz in rng.sample([(1,0),(0,1),(-1,0),(0,-1)],rng.randint(1,3)):
                if ground_y.get((x+dx,z+dz))==y:
                    stones[x+dx,y+1,z+dz]=(AIR,"minecraft:"+rng.choice((material,"andesite")))
            if len(stones)>=2 and rng.random()<.25:stones[x,y+2,z]=(AIR,"minecraft:"+material)
            if len(stones)>=2 and add("lichen boulder", (x,y,z),stones):occupied.append((x,z,2))
        else:
            dx,dz=rng.choice([(1,0),(0,1)])
            length=rng.choice((2,3,4))
            if all(ground_y.get((x+i*dx,z+i*dz))==y for i in range(length)):
                logs={(x+i*dx,y+1,z+i*dz):(AIR,f"minecraft:spruce_log[axis={'x' if dx else 'z'}]") for i in range(length)}
                if add("fallen spruce",(x,y,z),logs):occupied.append((x,z,3))
    for _ in range(5000):
        x,z=rng.choice(points);y=ground_y[x,z]
        if not any((x-i)**2+(z-j)**2<(s+2)**2 for i,j,s in occupied):continue
        if rng.random()<.53:
            ground=rng.choice(("minecraft:podzol[snowy=false]","minecraft:coarse_dirt"))
            add("forest floor",(x,y,z),{(x,y,z):(GRASS,ground)})
        elif (x,y+1,z) not in edits:
            add("understory",(x,y,z),{(x,y+1,z):(AIR,rng.choice(("minecraft:fern","minecraft:grass")))})
    coords=sorted(edits)
    pal=sorted({b for pair in edits.values() for b in pair})
    groups=defaultdict(list)
    for x,y,z in coords:
        a,b=edits[x,y,z]
        groups[x,z].append((y,pal.index(a),pal.index(b)))
    columns=[]
    for (x,z),vs in sorted(groups.items()):
        spans=[]
        for y,a,b in sorted(vs):
            if spans and spans[-1][1]+1==y and spans[-1][2:]==[a,b]:
                spans[-1][1]=y
            else:spans.append([y,y,a,b])
        columns.append([x,z,spans])
    doc={"schema":1,"source":"6ed6eaf31f946195f8b75fa869308c915537a6ed","palette":pal,
         "columns":columns,"excludedColumns":[list(xz) for xz in sorted(rough&excluded)],
         "boundsInclusiveXYZ":[[min(p[a] for p in coords) for a in range(3)],[max(p[a] for p in coords) for a in range(3)]],
         "counts":dict(Counter(f["kind"] for f in features))}
    PLACEMENTS.write_bytes(gzip.compress(json.dumps(doc,separators=(',',':')).encode(),mtime=0))
    _plan.cache_clear();_excluded.cache_clear()
    result={"source":doc["source"],"sourceManifestSHA256":INPUT_SHA,"regionSHA256":{k:v for k,v in pins.items() if k.endswith(('r.-1.-2.mca','r.-1.-1.mca'))},
            "spanSHA256":hashlib.sha256(PLACEMENTS.read_bytes()).hexdigest(),"sourceAndTargetStatePalette":pal,
            "boundsInclusiveXYZ":doc["boundsInclusiveXYZ"],"changedVoxels":len(edits),"changedColumns":len(columns),
            "featureCounts":doc["counts"],"trees":[f for f in features if f["kind"] in ('spruce','birch')],"protectedOverlap":0}
    assert all(allowed(x,z) for x,z in groups)
    (HOME/"mainland-details.json").write_text(json.dumps(result,indent=2)+'\n')
    return result

if __name__=="__main__":
    print(json.dumps({k:v for k,v in generate().items() if k not in ('trees','regionSHA256','sourceAndTargetStatePalette')},indent=2))
