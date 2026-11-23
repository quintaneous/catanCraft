"""Read actual block data and check metadata, access routes, ladder backing, and door pairs."""
from collections import Counter,deque
import argparse
import hashlib
import json
from pathlib import Path
import sys

sys.dont_write_bytecode=True
from render import decode,components,ROOT
from build import station,encode

def paths(blocks):
    def get(p):return components(blocks.get(p,'minecraft:air'))
    def passable(p):
        b,_=get(p)
        return b in ('air','ladder','grass','spruce_door')
    def allowed(p):
        x,y,z=p
        if not(0<=x<55 and 9<=y<50 and 0<=z<47) or not passable(p) or not passable((x,y+1,z)):return False
        top,_=get((x,y-1,z)); own,_=get(p)
        return own=='ladder' or (top not in ('air','grass','ladder','lantern','spruce_door','flower_pot') and not top.endswith(('_fence','_wall','_slab')))
    def distances(start):
        assert allowed(start),start
        q=deque([start]);prev={start:None}
        while q:
            a=q.popleft()
            x,y,z=a
            for dx,dz,side,rev in [(1,0,'east','west'),(-1,0,'west','east'),(0,1,'south','north'),(0,-1,'north','south'),(0,0,None,None)]:
                for sy in (-1,0,1):
                    b=(x+dx,y+sy,z+dz)
                    if b in prev or not allowed(b):continue
                    if side is None:
                        if sy==0 or not (get(a)[0]=='ladder' and get(b)[0]=='ladder'):continue
                    elif sy:
                        high=b if sy>0 else a
                        support,props=get((high[0],high[1]-1,high[2]))
                        if not support.endswith('_stairs') or props.get('facing')!=(side if sy>0 else rev):continue
                    prev[b]=a
                    q.append(b)
        return prev
    targets={'chartTableStandingSpace':(26,14,28),'towerSouthThreshold':(19,14,22),'towerLadderBase':(20,14,17),
             'keeperInterior':(34,14,19),'watchGallery':(19,40,21)}
    entries={'westLanding':(2,10,24),'southLanding':(36,10,45)}
    result={}
    for startname,start in entries.items():
        prev=distances(start)
        result[startname]={}
        for key,target in targets.items():
            assert target in prev,(startname,key,target)
            moves=0;p=target
            while prev[p] is not None:p=prev[p];moves+=1
            result[startname][key]=moves
    return result

def validate(registry=None):
    p=ROOT/'Brinewatch_Pilot_Station.schem';d,b=decode(p)
    assert d['Version']==2 and d['DataVersion']==3465 and (d['Width'],d['Height'],d['Length'])==(55,50,47)
    assert d['Offset']==[0,0,0] and not d['BlockEntities'] and not d['Entities']
    assert len(d['Palette'])==d['PaletteMax']
    assert b==station() and encode()==p.read_bytes()
    if registry:
        sys.path.insert(0,str(registry))
        from block_registry import validate_state
        for state in d['Palette']:validate_state(state)
    door_count=0;ladder_count=0
    for (x,y,z),state in b.items():
        name,s=components(state)
        if name.endswith('_door') and s['half']=='lower':
            upper=b[x,y+1,z];assert upper==state.replace('half=lower','half=upper')
            assert (x,y-1,z) in b
            door_count+=1
        if name=='ladder':
            offset={'east':(-1,0),'south':(0,-1)}[s['facing']]
            assert (x+offset[0],y,z+offset[1]) in b
            ladder_count+=1
    assert b[26,14,27]=='minecraft:cartography_table'
    assert all((x,y,z) not in b for x,y,z in [(1,9,24),(1,10,24),(36,9,46),(36,10,46)])
    block_ids={s.split('[')[0][10:] for s in d['Palette']}
    assert 'command_block' not in block_ids and 'chest' not in block_ids and 'spawner' not in block_ids
    return {'asset':p.name,'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'spongeVersion':2,'dataVersion':3465,
        'XYZ':[55,50,47],'nonAirBlocks':len(b),'paletteStatesIncludingAir':len(d['Palette']),
        'registeredStatesChecked':len(d['Palette']) if registry else None,'blockEntities':0,'entities':0,
        'matchedDoorPairs':door_count,'backedLadderBlocks':ladder_count,'localHighestWaterY':8,
        'localWaterPlaneY':9,'anchorLocal':[27,8,23],'routeLengthsInGraphSteps':paths(b),
        'reachabilityModel':'Two clear cells, cardinal walk, rise/descent along oriented stairs only, backed ladders, opened doors. Rails and slabs excluded as supports. No diagonal jumps. Static offline geometry; interactive boat and collision checks remain pending.'}

if __name__=='__main__':
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--registry',type=Path,help='directory containing the project 1.20.1 block_registry.py and its data')
    args=ap.parse_args()
    print(json.dumps(validate(args.registry),indent=2))
