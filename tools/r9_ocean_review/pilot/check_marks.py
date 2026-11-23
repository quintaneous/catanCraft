"""Check full marker geometry and berth routes against persisted Anvil blocks."""
import argparse
import json
from pathlib import Path
import sys
sys.dont_write_bytecode=True
import ocean
from sound_ocean import route_points

def run(repo,inputs,world_dir):
    sys.path.insert(0,str(repo/'tools/walls53'));sys.path.insert(0,str(inputs))
    from nbt_check import NBT
    from audit_sites import World
    world=World(world_dir,NBT)
    def block(x,y,z):
        return world.at((x,y,z)).split('[')[0]
    marks=ocean.pilot_buoys()
    for mark in marks:
        x,z=mark['x'],mark['z']; floor=ocean.offshore_column(x,z)[0]
        checks=[(0,floor,0,'polished_andesite'),(0,76,0,'waxed_oxidized_copper'),
                (0,77,0,'white_concrete'),(0,78,0,'waxed_oxidized_copper'),(0,79,0,'sea_lantern')]
        checks.extend((dx,75,dz,'waxed_oxidized_copper') for dx,dz in ((0,0),(-1,0),(1,0),(0,-1),(0,1)))
        checks.extend((0,y,0,'chain') for y in range(floor+1,75))
        if mark['turn']:checks.extend([(0,80,0,'yellow_concrete'),(0,81,0,'sea_lantern')])
        for dx,y,dz,name in checks:
            assert block(x+dx,y,z+dz)=='minecraft:'+name,(mark,y,name)
        for y in range(floor+1,75):
            assert 'waterlogged=true' in world.at((x,y,z)),mark
    results=[]
    for route in (ocean.PRIMARY_ROUTE,ocean.ALTERNATE_ROUTE):
        pts=set(route_points(route));footprint={(x+dx,z+dz) for x,z in pts for dx in (-1,0,1) for dz in (-1,0,1)}
        for x,z in footprint:
            assert all(block(x,y,z)=='minecraft:water' and 'level=0' in world.at((x,y,z)) for y in (73,74,75)),(x,z,'depth')
            assert all(block(x,y,z)=='minecraft:air' for y in (76,77)),(x,z,'headroom')
        results.append({'centerColumns':len(pts),'boatFootprintColumns':len(footprint)})
    return {'kind':'saved-Anvil-readback','markersVerified':len(marks),'turnSparsVerified':sum(m['turn'] for m in marks),
            'waterState':'source level 0','primaryRoute':results[0],'alternateRoute':results[1],
            'readOnly':True,'liveBoatRun':False}

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('repo',type=Path);p.add_argument('inputs',type=Path);p.add_argument('world',type=Path)
    a=p.parse_args();print(json.dumps(run(a.repo,a.inputs,a.world),indent=2))
