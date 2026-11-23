"""Sound the proposed boat channels, including the real non-air lighthouse overlay."""
import argparse
import hashlib
import json
from pathlib import Path
import sys
sys.dont_write_bytecode=True
import ocean

def boxcells(b):
    return ((x,z) for x in range(b[0],b[2]) for z in range(b[1],b[3]))

def route_points(route):
    for (x1,z1),(x2,z2) in zip(route,route[1:]):
        dx=(x2>x1)-(x2<x1);dz=(z2>z1)-(z2<z1)
        assert not(dx and dz)
        for step in range(abs(x2-x1)+abs(z2-z1)+1):
            yield x1+dx*step,z1+dz*step

def audit(station):
    sys.path.insert(0,str(station))
    from render import decode
    file=station/'Brinewatch_Pilot_Station.schem'
    _,blocks=decode(file)
    markers={}
    class Recorder:
        def set(self,x,y,z,state):
            assert ocean.inside(x,z)
            markers[x,y,z]=state
    ocean.decorate(Recorder())
    def block(x,y,z):
        local=(-1383-x,y-67,-407-z)
        if local in blocks:return blocks[local]
        if (x,y,z) in markers:return markers[x,y,z]
        if z==-1024 and -1024<=x<224:
            floor,name,water=ocean.SEAM['north'][x+1024]
            mat='minecraft:'+name;dry=mat if water is None else None
        else: floor,mat,_,dry=ocean.offshore_column(x,z)
        if y<=floor:return mat if y==floor else 'minecraft:stone'
        return 'minecraft:water[level=0]' if not dry and y<=75 else 'minecraft:air'
    soundings=[]
    for name,b,minimum in (
            ('north estuary throat',(40,-1072,48,-1024),3),
            ('north estuary fairway',(32,-1236,56,-1072),6),
            ('north circuit to northwest turn',(-1236,-1236,60,-1204),8),
            ('west circuit to island turn',(-1236,-1236,-1204,-414),8),
            ('lighthouse approach',(-1362,-446,-1204,-414),6)):
        ys=[ocean.offshore_column(x,z)[0] for x,z in boxcells(b)]
        extras=[(x,y,z) for x,y,z in markers if b[0]<=x<b[2] and b[1]<=z<b[3] and y>=75-minimum+1]
        assert 75-max(ys)>=minimum and not extras,(name,75-max(ys),extras)
        soundings.append(dict(name=name,boundsXZ=b,width=8 if name.endswith('throat') else 24 if name.endswith('fairway') else 32,
                              minimumRequired=minimum,minimumUnderKeelWaterBlocks=75-max(ys)))
    def test_route(points,start):
        rows=list(route_points(points))
        for x,z in rows:
            for xx in range(x-1,x+2):
                for zz in range(z-1,z+2):
                    assert all(block(xx,y,zz) in ('minecraft:water','minecraft:water[level=0]') for y in (73,74,75)),(start,x,z,'water',xx,zz)
                    assert all(block(xx,y,zz)=='minecraft:air' for y in (76,77)),(start,x,z,'air',xx,zz)
        return len(set(rows))
    primary=test_route(ocean.PRIMARY_ROUTE,'primary')
    alt=test_route(ocean.ALTERNATE_ROUTE,'alternate')
    first=(ocean.PRIMARY_ROUTE[-1],(-1385,-431))
    second=(ocean.ALTERNATE_ROUTE[-1],(-1419,-452))
    for ((x,z),(dx,dz)) in (first,second):
        assert abs(x-dx)+abs(z-dz)==2
        assert block(dx,76,dz) not in ('minecraft:air','minecraft:water','minecraft:water[level=0]')
        assert block(dx,77,dz)==block(dx,78,dz)=='minecraft:air'
    seam={}
    for side in ('north','west'):
        rows=ocean.SEAM[side];same=0
        for i,(h,material,water) in enumerate(rows):
            x,z=(-1024+i,-1025) if side=='north' else (-1025,-1024+i)
            f,m,_,dry=ocean.offshore_column(x,z)
            assert f==h and m.removeprefix('minecraft:')==material and (dry is None)==(water is not None)
            same+=1
        seam[side]=same
    return dict(waterBlockY=75,waterSurfacePlaneY=76,newChunkRectanglesHalfOpenXZ=ocean.EXTENTS,exteriorChunks=len(ocean.pilot_chunks()),
                primaryRouteXZ=ocean.PRIMARY_ROUTE,alternateRouteXZ=ocean.ALTERNATE_ROUTE,
                landingsXYZ={'primary':[-1385,76,-431],'alternate':[-1419,76,-452]},
                primaryRouteCenterColumns=primary,alternateRouteCenterColumns=alt,channelProof=soundings,
                terminalClearance={'boatWidth':3,'waterBlocks':3,'airBlocks':2,'horizontalCenterToDeckBlocks':2},
                seamlessNeighborColumns=seam,mooredPilotSpars=ocean.pilot_buoys(),
                shorelineSide='green to starboard travelling from river toward lighthouse',
                lighthouseSHA256=hashlib.sha256(file.read_bytes()).hexdigest(),gameplayBoatTrial='pending')
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('station',type=Path)
    args=p.parse_args();print(json.dumps(audit(args.station.resolve()),indent=2))
