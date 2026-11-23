"""Record the frozen authored rows that meet the new north and west ocean chunks."""
import argparse
import hashlib
import json
from pathlib import Path
import sys

def survey(repo, source):
    manifest = (source/'INPUT_MANIFEST.json').read_bytes()
    assert hashlib.sha256(manifest).hexdigest()=='e7ecd5d0f0ac5c1457be5c234d2e5e08c9239007787b20e0bdc45b3e2b8d862d'
    pins={r['path']:r['sha256'] for r in json.loads(manifest)['files']}
    sys.path.insert(0,str(repo/'tools/walls53'));sys.path.insert(0,str(source))
    from audit_sites import World
    from nbt_check import NBT
    w=World(source/'source_world/CatanCraft_Season1_R7_Development',NBT)
    def scan(x,z):
        surface=[]
        for sy,(palette,_) in sorted(w.chunk(x//16,z//16).items(), reverse=True):
            if len(palette)==1 and palette[0] in ('minecraft:air','minecraft:cave_air','minecraft:void_air'):continue
            for y in range(sy*16+15,sy*16-1,-1):
                name=w.at((x,y,z)).split('[')[0].removeprefix('minecraft:')
                if name in ('air','cave_air','void_air'): continue
                surface.append((y,name))
                if name!='water': return [y,name,surface[0][0] if len(surface)>1 else None]
        raise ValueError((x,z))
    data={'north':[scan(x,-1024) for x in range(-1024,224)],
          'west':[scan(-1024,z) for z in range(-1024,-224)]}
    for name,raw in w.regions.items():
        assert hashlib.sha256(raw).hexdigest()==pins[name.relative_to(source).as_posix()]
    # Run lengths retain every column; reader starts at coordinate −1024.
    def compress(rows):
        runs=[]
        for row in rows:
            if not runs or runs[-1][1:]!=row:runs.append([1,*row])
            else:runs[-1][0]+=1
        return runs
    return {k:compress(v) for k,v in data.items()}

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('repo',type=Path);p.add_argument('source',type=Path)
    a=p.parse_args(); print(json.dumps(survey(a.repo,a.source),separators=(',',':')))
