"""Read back both schematic assets, compare their water level to pristine R7, and test channels."""
import argparse
from collections import deque
import gzip
import hashlib
import json
from pathlib import Path
import sys
import numpy as np

BASE, WATER_Y = 57, 75
WL = WATER_Y - BASE
TILES = {
    "sheltered_cove": {"origin":[-368,57,-1024], "inlet":(54,35)},
    "headland_estuary":{"origin":[-40,57,-1024], "inlet":(59,95)}
}


def decode(nbt, path):
    root=nbt(gzip.decompress(path.read_bytes())).root()
    assert (root["Version"],root["DataVersion"])==(2,3465)
    w,l,h=[root[k] for k in ["Width","Length","Height"]]
    assert (w,h,l)==(112,64,96) and not root["Entities"] and not root["BlockEntities"]
    assert bytes(root["Offset"]) == bytes(12)
    names = sorted(root["Palette"],key=root["Palette"].get)
    assert [root["Palette"][key] for key in names]==list(range(root["PaletteMax"]))
    raw=np.frombuffer(bytes(root["BlockData"]),dtype=np.uint8)
    assert len(raw)==w*l*h and max(raw)<len(names)<128
    return raw.reshape(h,l,w),names


def route(mask,start_z,end):
    q=deque()
    seen={}
    for x,allowed in enumerate(mask[start_z]):
        if allowed:
            p=(x,start_z)
            seen[p]=None
            q.append(p)
    while q:
        p=q.popleft()
        if p==end:
            path=[]
            while p is not None:
                path.append(p)
                p=seen[p]
            return path[::-1]
        x,z=p
        for other in ((x,z+1),(x+1,z),(x-1,z),(x,z-1)):
            a,b=other
            if other not in seen and 0<=b<mask.shape[0] and 0<=a<mask.shape[1] and mask[b,a]:
                seen[other]=p
                q.append(other)
    return []


def corridor(nav, width):
    """Require a width × width clear square at every path center; leading/trailing edges clip at tile limits."""
    def clipped_all(z,x):
        k=width//2
        if x<k or x>=nav.shape[1]-k: return False
        return nav[max(0,z-k):z+k+1,x-k:x+k+1].all()
    answer=np.empty(nav.shape,dtype=bool)
    for z in range(nav.shape[0]):
        for x in range(nav.shape[1]): answer[z,x]=clipped_all(z,x)
    return answer


def audit(path, nbt):
    a,names=decode(nbt,path)
    water=names.index("minecraft:water[level=0]")
    air=names.index("minecraft:air")
    depth=np.sum(a==water,axis=0)
    wet=depth>0
    assert (a[WL][wet]==water).all() and not (a[WL+1:]==water).any()
    for y in range(WL+1):
        assert np.array_equal(a[y]==water,depth>WL-y)
    nav=(depth>=3)&(a[WL+1]==air)&(a[WL+2]==air)
    kind=path.stem.removeprefix("r9_")
    goal=TILES[kind]["inlet"]
    path5=route(corridor(nav,5),0,goal)
    assert path5, f"{kind}: no entrance-to-destination 5-wide route"
    terrain=[i for i,s in enumerate(names) if s.split("[")[0].split(":")[-1] in
             ["stone","andesite","tuff","clay","gravel","dirt","sandstone","sand","grass_block"]]
    h=a.shape[0]-1-np.argmax(np.isin(a,terrain)[::-1],axis=0)
    result={
      "schematic":path.name,"sha256":hashlib.sha256(path.read_bytes()).hexdigest(),
      "referenceOriginXYZ":TILES[kind]["origin"],"sizeXYZ":[a.shape[2],a.shape[0],a.shape[1]],
      "waterBlockY":WATER_Y,"firstAirBlockYAboveOpenWater":WATER_Y+1,"paletteSize":len(names),"waterColumns":int(wet.sum()),
      "maximumWaterDepth":int(np.max(depth)),"terrainHeightMinMaxWorldY":[int(h.min()+BASE),int(h.max()+BASE)],
      "route":{"fromLocalXZ":path5[0],"toLocalXZ":goal,"lengthInHorizontalSteps":len(path5)-1,
               "requiredClearWidth":5,"requiredSourceWaterDepth":3,"requiredAirBlocksAboveWater":2,
               "leastDepthAtRouteCenter":int(min(depth[z,x] for x,z in path5)),
               "centerlineLocalXZ":path5},
      "blocks":{names[i]:int(n) for i,n in enumerate(np.bincount(a.ravel(),minlength=len(names)))},
    }
    return result,depth,h,path5


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("workspace",type=Path)
    p.add_argument("tiles",type=Path)
    args=p.parse_args()
    sys.dont_write_bytecode=True
    workspace=args.workspace.resolve()
    inputs=workspace/"inputs/CatanCraft_Clean_R7_Input"
    sys.path[:0]=[str(inputs),str(workspace/"resource-ui/tools/walls53")]
    from nbt_check import NBT
    from audit_sites import World
    reference=World(inputs/"source_world/CatanCraft_Season1_R7_Development",NBT)
    samples=[]
    for x,z in [(37,-1000),(37,-1024),(-36,999),(-36,1023)]:
        assert reference.at((x,75,z))=="minecraft:water[level=0]" and reference.at((x,76,z))=="minecraft:air"
        samples.append({"xz":[x,z],"topWaterBlockY":75})
    result={"pristineWaterlineSamples":samples,"tiles":{}}
    for name in TILES:
        r,_,_,_=audit(args.tiles/("r9_"+name+".schem"),NBT)
        result["tiles"][name]=r
    print(json.dumps(result,indent=2))
if __name__=="__main__": main()
