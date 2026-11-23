"""Measure the qualified candidate's objective areas and untouched-mainland coast options."""
from collections import Counter
from hashlib import sha256
from itertools import product
from pathlib import Path
import argparse
import gzip
import json
import re
import statistics
import struct
import subprocess
import sys
import zipfile

BUILD = "6ed6eaf31f946195f8b75fa869308c915537a6ed"


def rectangle(points):
    xs, zs = zip(*[(p["x"], p["z"]) for p in points])
    assert set(zip(xs, zs)) == set(product((min(xs), max(xs)), (min(zs), max(zs))))
    # TerritoryBoundary tests block centers; the upper polygon boundary is exclusive.
    return min(xs), max(xs)-1, min(zs), max(zs)-1


def cells(box):
    l, r, t, b = box
    return product(range(l, r+1), range(t, b+1))


def count_claims(points, territories):
    counts = Counter()
    for x, z in points:
        matches = [i for i,(l,r,t,b) in territories.items() if l <= x <= r and t <= z <= b]
        assert len(matches) <= 1
        counts[matches[0] if matches else "outside"] += 1
    return dict(sorted(counts.items()))


def gap(a, b):
    # Chebyshev distance in blocks between nearest included block coordinates.
    return max(0, a[0]-b[1], b[0]-a[1], a[2]-b[3], b[2]-a[3])


def above_ground_columns(schematic, world_min, ground):
    """Union of columns containing a non-air voxel strictly above the supplied ground Y."""
    w,h,l = (schematic[k] for k in ["Width", "Height", "Length"])
    air = {v for k,v in schematic["Palette"].items() if k in {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}}
    v = shift = i = 0
    occupied = set()
    for byte in schematic["BlockData"]:
        v |= (byte & 127) << shift
        if byte & 128:
            shift += 7
            continue
        y, rem = divmod(i, w*l)
        z,x = divmod(rem,w)
        if y + world_min[1] > ground and v not in air:
            occupied.add((x+world_min[0],z+world_min[2]))
        v = shift = 0
        i += 1
    assert i == w*h*l and shift == 0
    return occupied


def audit(repo, inputs):
    assert subprocess.check_output(["git", "-C", repo, "rev-parse", "HEAD"], text=True).strip() == BUILD
    manifest_raw = (inputs/"INPUT_MANIFEST.json").read_bytes()
    assert sha256(manifest_raw).hexdigest() == "e7ecd5d0f0ac5c1457be5c234d2e5e08c9239007787b20e0bdc45b3e2b8d862d"
    manifest = {f["path"]: f["sha256"] for f in json.loads(manifest_raw)["files"]}
    def pinned(path):
        raw = (inputs/path).read_bytes()
        assert sha256(raw).hexdigest() == manifest[str(path)]
        return raw
    pinned("nbt_check.py")
    sys.dont_write_bytecode = True
    sys.path.insert(0,str(inputs))
    from nbt_check import NBT
    config = pinned("bootstrap_reference/config/catancraft/map.json")
    assert config == (repo/"src/main/resources/data/catancraft/maps/season1_river_bridges_v3.json").read_bytes()
    m = json.loads(config)
    ts = {t["id"]:rectangle(t["boundary"]) for t in m["territories"]}
    assert [p["id"] for p in m["publicZones"]] == ["central_objective_district"]
    public = rectangle(m["publicZones"][0]["boundary"])
    captures = {m["id"]:m for m in m["monuments"]}
    registry = (repo/"src/main/java/com/quin/catancraft/world/SchematicAssetRegistry.java").read_text()
    assignments = {"central_district_base":None, "industrial_complex":"industrial", "military_depot":"depot", "refinery_monument":"refinery"}
    import io
    assets = zipfile.ZipFile(io.BytesIO(pinned("bootstrap_reference/config/catancraft/schematics_bundle.zip")))
    rows = {}
    protected = {}
    for asset,mid in assignments.items():
        pattern = r'new Asset\(\s*"'+asset+r'",\s*"([^"]+)",\s*"[^"]+",\s*\d+,\s*(\d+),\s*(\d+),\s*(\d+),\s*(\d+),\s*(\d+),\s*(\d+),\s*true,\s*Set.of\(0\)'
        match = re.search(pattern,registry)
        assert match,asset
        filename = match[1]+".schem"
        dims = tuple(map(int,match.groups()[1:4]))
        offsets = tuple(-int(i) for i in match.groups()[4:])
        placement = re.search(r'case "'+asset+r'" ->\s*new MapAnchor\("[^"]+", (-?\d+), (\d+), (-?\d+), 0\)',registry)
        assert placement
        anchor = tuple(map(int,placement.groups()))
        nbt = NBT(gzip.decompress(assets.read(filename))).root()
        assert tuple(nbt[k] for k in ("Width","Height","Length")) == dims and struct.unpack(">iii",nbt["Offset"]) == offsets
        x,y,z = [a+b for a,b in zip(anchor,offsets)]
        box = x,x+dims[0]-1,z,z+dims[2]-1
        if mid is None:
            court = box
            continue
        cap = captures[mid]
        assert cap['dimension'] == 'minecraft:overworld'
        cx,cz,r = cap["x"],cap["z"],cap["radius"]
        centered = [(a,b) for a in range(cx-r,cx+r+1) for b in range(cz-r,cz+r+1) if (a-cx)**2+(b-cz)**2 <= r*r]
        raised = above_ground_columns(nbt, (x,y,z),80)
        rows[mid] = {"captureAnchorXYZ":[cx,cap["y"],cz],"captureRadius":r,"captureAtBlockCentersByCity":count_claims(centered,ts),
                     "placementAnchorXYZ":anchor,"placementBoxXZ":box,"placementColumnsByCity":count_claims(cells(box),ts),
                     "nonAirColumnsAboveY80ByCity":count_claims(raised,ts)}
        assert all(gap((a,a,b,b),court)==0 for a,b in list(cells(box))+centered)
    for t in m["territories"]:
        for a in t["buildingPlots"] + t["defenseAnchors"] + [t[k] for k in ("cityCenter","townHall","managementAnchor","settlementAnchor","resourceSite") if t.get(k)]:
            protected[t["id"]+"/"+a["id"]] = a["x"],a["x"],a["z"],a["z"]
        for k in ("siegeRegion","restorationRegion"):
            b=t[k]
            protected[t["id"]+"/"+k]=b["minX"],b["maxX"],b["minZ"],b["maxZ"]
    nearest = min(gap(court,b) for b in protected.values())
    bridges = {b["id"]:gap(court,(b["x"],b["x"],b["z"],b["z"])) for b in m["infrastructure"]}
    # Apply the same north/south template transformation as SectorSites53.
    wall_file = repo/"src/main/resources/assets/catancraft/sector_p3/city_a.json.gz"
    template = json.loads(gzip.decompress(wall_file.read_bytes()))
    local_guards = {(row[0]+750,row[2]+750) for a in template["assets"]
                    for k in ("reads","fresh") for row in a[k]}
    wall_clearance = {}
    for t in m["territories"]:
        center = t["cityCenter"]
        assert center["rotation"] in (0,180)
        sign = 1 if center["rotation"] == 0 else -1
        mapped = [(center["x"]+sign*x, center["z"]+sign*z) for x,z in local_guards]
        wall_clearance[t["id"]] = min(gap(court,(x,x,z,z)) for x,z in mapped)
    full_city_alternative = {i:{"allClaimColumns":len(range(b[0],b[1]+1))*len(range(b[2],b[3]+1)),
                                "plotAnchorsLost":sum(1 for t in m["territories"] for a in t["buildingPlots"]
                                                  if gap(b,(a["x"],a["x"],a["z"],a["z"]))==0)}
                            for i,b in ts.items() if i in count_claims(cells(court),ts)}
    folder = Path("source_world/CatanCraft_Season1_R7_Development")
    level = NBT(gzip.decompress(pinned(folder/"level.dat"))).root()["Data"]
    chunks = set()
    for file in sorted((inputs/folder/"region").glob("*.mca")):
        _,rx,rz,_ = file.name.split(".")
        raw = pinned(file.relative_to(inputs))
        for i in range(1024):
            if int.from_bytes(raw[4*i:4*i+3],"big"):
                chunks.add((int(rx)*32+i%32,int(rz)*32+i//32))
    assert chunks == set(product(range(-64,64),repeat=2))
    sys.path.insert(0,str(repo/"tools/walls53"))
    from audit_sites import World
    w = World(inputs/folder,NBT)
    coastline = {}
    air = {"minecraft:air","minecraft:cave_air","minecraft:void_air"}
    def surface(x,z):
        for sy,(palette,_) in sorted(w.chunk(x//16,z//16).items(),reverse=True):
            if len(palette)==1 and palette[0] in air:
                continue
            for y in range(16*sy+15,16*sy-1,-1):
                state = w.at((x,y,z)).split("[")[0]
                if state not in air:
                    return y,state
        raise ValueError(f"Missing surface {(x,z)}")
    for edge in ("north","south","west","east"):
        materials, depths = Counter(), {}
        for v in range(-1000,1000):
            x,z = {"north":(v,-1000),"south":(v,999),"west":(-1000,v),"east":(999,v)}[edge]
            y,name = surface(x,z)
            materials[name] += 1
            if name == "minecraft:water":
                bed = y-1
                while w.at((x,bed,z)).split("[")[0] == "minecraft:water":
                    bed -= 1
                depths[v] = y-bed
        coastline[edge] = {"surface":dict(sorted(materials.items())), "waterRange":None,
                           "depthMinMedianMax":None, "widthAtLeastThreeBlocksDeep":sum(n>=3 for n in depths.values())}
        if depths:
            lo,hi = min(depths),max(depths)
            assert set(depths) == set(range(lo,hi+1))
            coastline[edge].update({"waterRange":[lo,hi],"depthMinMedianMax":
                                     [min(depths.values()),statistics.median(depths.values()),max(depths.values())]})
    coast = []
    for band in (350,600):
        half = int(level["BorderSize"]//2)+band
        chunk_range = range((-half)//16,(half-1)//16+1)
        add = set(product(chunk_range,repeat=2))-chunks
        coast.append({"coastAtApprox1000WaterWidth":band,"squareBorderDiameter":2*half,
                      "additionalChunksForBorderInterior":len(add),"preservedAuthoredChunks":len(chunks)})
    return {"source":BUILD,"mapSHA256":sha256(config).hexdigest(),"boxFormat":"inclusive [minX,maxX,minZ,maxZ]",
            "currentPublicBounds":public,"publicColumnsByCity":count_claims(cells(public),ts),
            "fullCourtBounds":court,"fullCourtColumnsByCity":count_claims(cells(court),ts),
            "omittedCourtEdgesByCity":count_claims(set(cells(court))-set(cells(public)),ts),"monuments":rows,
            "plotAnchorCount":sum(len(t["buildingPlots"]) for t in m["territories"]),"testedPointsAndBoxes":len(protected),
            "wallGuardMinimumDistanceByCity":wall_clearance,"wholeClaimNeutralizationImpact":full_city_alternative,
            "minimumDistanceToProtectedPointsAndBoxes":nearest,"closestProtected":[k for k,b in protected.items() if gap(court,b)==nearest],
            "distanceToBridgeAnchors":bridges,"regionCount":len(list((inputs/folder/"region").glob("*.mca"))),
            "border":{k:level[k] for k in ("BorderSize","BorderSizeLerpTarget","BorderSizeLerpTime","BorderCenterX","BorderCenterZ")},
            "currentBorderSurface":coastline,"coastScenarios":coast}

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("repo",type=Path)
    parser.add_argument("pristine_input",type=Path)
    args = parser.parse_args()
    print(json.dumps(audit(args.repo.resolve(),args.pristine_input.resolve()),indent=2))
