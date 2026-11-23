"""Read the Sponge block volume and validate the east site's transformed placement and boarding lanes."""
from __future__ import annotations

import gzip
import hashlib
from itertools import product
from pathlib import Path
import struct

AIR = "minecraft:air"

def require(ok, message):
    if not ok:
        raise ValueError(message)


def read_blocks(path, decoder):
    raw = path.read_bytes()
    root = decoder(gzip.decompress(raw)).root()
    dim = tuple(root[k] for k in ("Width", "Height", "Length"))
    require((root["Version"], root["DataVersion"]) == (2, 3465), "Unsupported Sponge / Minecraft version")
    require(not any(struct.unpack(">iii",root["Offset"])), "Unexpected schematic offset")
    states = {i:s for s,i in root["Palette"].items()}
    ids = []
    val = shift = 0
    for b in root["BlockData"]:
        val |= (b & 127) << shift
        if b & 128:
            shift += 7
            require(shift < 35, "Palette index varint exceeds 5 bytes")
        else:
            require(val in states, "Unknown palette index")
            ids.append(val)
            val = shift = 0
    w,h,l = dim
    require(not shift and len(ids) == w*h*l, "Malformed block volume")
    def block(x,y,z):
        if not (0 <= x < w and 0 <= y < h and 0 <= z < l):
            return None
        return states[ids[(y*l+z)*w+x]]
    return dim,block,hashlib.sha256(raw).hexdigest()


def augment(site, schem, decoder):
    dim,block,sha = read_blocks(schem, decoder)
    require(dim==(88,40,54), "Brinewake v2 dimensions differ")
    pivot=(44,15,27)
    wx,wz=site["centerXZ"]
    origin=(wx-pivot[0],75-pivot[1],wz-pivot[2])
    def transform(local):
        # Confirmed asset orientation: prow points west, rotation zero.
        return tuple(o+d for o,d in zip(origin,local))
    corners=[transform(p) for p in product(*((0,n-1) for n in dim))]
    bounds=[*[min(p[j] for p in corners) for j in range(3)],
            *[max(p[j] for p in corners)+1 for j in range(3)]]
    xmin,zmin,xmax,zmax=site["boundsXZ"]
    margins=[bounds[0]-xmin,bounds[2]-zmin,xmax-bounds[3],zmax-bounds[5]]
    require(min(margins)>=0, "Asset falls outside east reservation")
    branch = {}
    landings = {"north":(41,17,7),"south":(22,17,43)}
    berths = {k:list(transform(local)) for k,local in landings.items()}
    for key,(lx,fy,lz) in landings.items():
        require(block(lx,fy-1,lz) not in (None,AIR,"minecraft:water[level=0]") and
                all(block(lx,h,lz)==AIR for h in (fy,fy+1)),f"Obstructed {key} boarding feet")
        lane_z=5 if key=="north" else 45
        # Exact measured landings are two blocks to the side of the navigable centerline.
        require(abs(lane_z-lz)==2,"West-entry lane must adjoin the landing")
        # Follow the site entry from the existing spur end around the bow. The straight
        # part inside the .schem is checked against every encoded water/air block.
        world_z = origin[2]+lane_z
        turn_x = xmin+2
        branch[key]=[[xmin,wz],[turn_x,wz],[turn_x,world_z],[origin[0]+lx,world_z]]
        # The first four columns of this site lie beyond the west edge of the tile.
        for x in range(lx+1):
            for xx in range(max(0,x-1),x+2):
                for zz in range(lane_z-1,lane_z+2):
                    for yy in (13,14,15):
                        require(block(xx,yy,zz) in ("minecraft:water","minecraft:water[level=0]"),
                                f"{key} water lane obstruction at {(xx,yy,zz)}")
                    for yy in (16,17):
                        require(block(xx,yy,zz)==AIR,f"{key} overhead obstruction at {(xx,yy,zz)}")
        require(all(xmin<=x<xmax and zmin<=z<zmax for x,z in branch[key]),"Boat branch outside reserve")
    site.update(prototypeBerthsXYZ=berths,proposedApproachBranchesXZ=branch,
                prototypePlacement=dict(revision=2,schematic=schem.name,sha256=sha,
                    dimensionsXYZ=dim,rotationDegrees=0,localPivotXYZ=pivot,worldPivotXYZ=(wx,75,wz),
                    worldLowerCornerXYZ=origin,worldBoundsXYZHalfOpen=bounds,
                    marginsXZLowThenHigh=margins),
                localBerthBranchGeometry=dict(widthBlocks=3,minWaterBlocks=3,minOverheadAirBlocks=2,
                    topWaterBlockY=75,northLocalLaneZ=5,southLocalLaneZ=45,
                    withinSchematic="validated directly against water and air states",
                    outsideSchematic="proposed water, pending coast authoring and boat trial"))
