#!/usr/bin/env python3
"""Read-only pinned-geometry audit, not native vehicle/gameplay acceptance."""
import argparse, base64, collections, gzip, hashlib, importlib.util, io, json, struct, sys, zipfile
from pathlib import Path
sys.dont_write_bytecode = True

def sha(raw): return hashlib.sha256(raw).hexdigest()
def module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    value = importlib.util.module_from_spec(spec); spec.loader.exec_module(value); return value

def run(repo, inputs, world, output):
    audit = module('vehicle53_world_reader', repo/'tools/walls53/audit_sites.py')
    parser = module('vehicle53_nbt_reader', inputs/'nbt_check.py')
    resources = repo/'src/main/resources/data/catancraft'
    pins = {}
    def read(path):
        raw = path.read_bytes(); pins[str(path.relative_to(repo)) if path.is_relative_to(repo) else str(path)] = sha(raw); return raw
    read(inputs/'nbt_check.py')
    for name in ('VehicleFactory50','VehicleSites52','VehiclePublicSource52','IndustrialPlotService','SchematicAssetRegistry','TownHallRevisions'):
        read(repo/f'src/main/java/com/quin/catancraft/world/{name}.java')
    read(repo/'tools/vehicle53/audit_hall_context.py')
    bundle_raw = base64.b64decode(read(resources/'basic_construction/assets.b64'))
    assert sha(bundle_raw) == audit.BUNDLE_SHA
    bundle = zipfile.ZipFile(io.BytesIO(bundle_raw))
    def decode(raw):
        n = parser.NBT(gzip.decompress(raw)).root(); palette = {v:audit.state(k) for k,v in n['Palette'].items()}
        values=[]; value=shift=0
        for b in n['BlockData']:
            value |= (b&127)<<shift
            if b&128: shift += 7
            else: values.append(palette[value]); value=shift=0
        assert len(values)==n['Width']*n['Height']*n['Length'] and shift==0
        return n,values
    def at(volume,x,y,z):
        n,values=volume
        assert 0<=x<n['Width'] and 0<=y<n['Height'] and 0<=z<n['Length'],(x,y,z)
        return values[(y*n['Length']+z)*n['Width']+x]
    def bundled(name):
        raw=bundle.read(name+'.schem');pins['bundle:'+name+'.schem']=sha(raw);return decode(raw)
    starter=bundled('starter_settlement'); factory=bundled('vehicle_factory')
    halls={f'TH{tier}':bundled(f'th{tier}') for tier in range(1,4)}
    for tier in (4,5):
        raw=read(repo/f'tests/fixtures/thall{tier}.schem')
        assert sha(raw)=={4:'39f2fda6a74d0d891d45b37d111079905a6f67e4261b591c13902b4215342c4b',5:'1ea2fd544c32ed5506a553bf6c56628ecd4b7ac8d29257009e7bd84a427bbc06'}[tier]
        original=decode(raw); halls[f'TH{tier}R1']=original
        # Reproduce the exact immutable R2 sparse expansion; compare all relevant
        # cells, without requiring a loose external or installed schematic.
        n,values=original;values=values.copy()
        def put(x,y,z,state):values[(y*65+z)*75+x]=state
        def get(x,y,z):return values[(y*65+z)*75+x]
        left,right,rear=(7,67,55) if tier==4 else (5,69,58)
        stone='minecraft:smooth_sandstone' if tier==4 else 'minecraft:smooth_quartz'
        cap='minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]';floor='minecraft:polished_diorite'
        for center in (left+10,right-10):
            for x in range(center-1,center+2):put(x,6,rear+1,'minecraft:air')
        for lo in (left+3,right-7):
            hi=lo+4
            for x in range(lo,hi+1):put(x,14,34,floor)
            for x in (lo-1,hi+1):
                for z in range(24,34):
                    step=z-24
                    for y in range(5,7+step):
                        if get(x,y,z)=='minecraft:air':put(x,y,z,stone)
                    if get(x,7+step,z)=='minecraft:air':put(x,7+step,z,cap)
                for z in range(23,34):put(x,15,z,stone);put(x,16,z,cap)
            for x in range(lo,hi+1):put(x,15,23,stone);put(x,16,23,cap)
        for x in range(28,47):
            for z in range(22,48):
                if x in (28,46) or z in (22,47):put(x,14,z,floor);put(x,15,z,stone);put(x,16,z,cap)
        for x in range(26,49):put(x,15,18,stone);put(x,16,18,cap)
        assert sum(a!=b for a,b in zip(original[1],values))==584
        halls[f'TH{tier}R2']=(n,values)
    public_raw=gzip.decompress(base64.b64decode(read(resources/'r5_public/contract.b64')));assert sha(public_raw)==audit.R5_SHA
    public_doc=json.loads(public_raw);public={(x-80,y-2,z-84):audit.state(public_doc['palette'][s]) for x,y,z,s in public_doc['writes']}
    definitions=json.loads(read(resources/'maps/season1_river_bridges_v3.json'))['territories']
    slots={}
    for plot,ax,az,sign in [('plot_1',-45,-24,1),('plot_3',-45,16,1),('plot_4',45,16,-1)]:
        overlaps=[];owned=[];rows={key:{False:[],True:[]} for key in halls}
        for x in range(-29,11):
            for y in range(-2,8):
                for z in range(-22,24):
                    p=(ax+sign*x,y,az+sign*z);px,py,pz=p
                    if not(-37<=px<=37 and -40<=pz<=24):continue
                    overlaps.append(p);factory_cell=-29<=x<=1 and -15<=z<=15
                    if factory_cell:owned.append(p)
                    raw=at(factory,x+29,y+2,z+15) if factory_cell else at(starter,px+80,py+2,pz+84)
                    for key,volume in halls.items():
                        target=at(volume,px+37,py+2,pz+40)
                        for prepared in (False,True):
                            expected=public.get(p,raw) if prepared and not factory_cell else raw
                            if expected!=target:rows[key][prepared].append((p,expected,target))
        assert not owned
        for key, modes in rows.items():
            assert modes[False]==modes[True]
            if key in ('TH1','TH2','TH3'):assert not modes[True]
            else:
                assert len(modes[True])=={'plot_1':120,'plot_3':93,'plot_4':96}[plot]
                assert all(p[1]==-2 and before=='minecraft:stone' and after=='minecraft:dirt' for p,before,after in modes[True])
        slots[plot]=(overlaps,rows)
    records=[]
    for definition in definitions:
        if definition['id'] not in 'abcdefghijklmnop':continue
        c=definition['cityCenter'];s=1 if c['rotation']==0 else -1
        transform=lambda p:(c['x']+s*p[0],c['y']+p[1],c['z']+s*p[2])
        for plot,(overlaps,rows) in slots.items():
            record={'city':definition['id'],'plot':plot,'cityRotation':c['rotation'],'factoryRotation':(c['rotation']+(180 if plot=='plot_4' else 0))%360,'hallEnvelopeReadIntersection':len(overlaps),'ownedFactoryIntersection':0,'tiers':{}}
            for key,prepared_rows in rows.items():
                entry={}
                for prepared,changes in prepared_rows.items():
                    points=[transform(p) for p,b,t in changes]
                    entry['prepared' if prepared else 'raw']={'changedReads':len(changes),'worldBounds':[[min(p[k] for p in points),max(p[k] for p in points)] for k in range(3)] if points else None,'statePairs':[{'expected':b,'target':t,'count':count} for (b,t),count in sorted(collections.Counter((b,t) for p,b,t in changes).items())]}
                record['tiers'][key]=entry
            records.append(record)
    assert len(records)==48
    live=audit.World(world,parser.NBT);point=(287,79,-726);actual=live.at(point);cx,cz=point[0]//16,point[2]//16
    region=world/'region'/f'r.{cx//32}.{cz//32}.mca';history=world/'data/catancraft_th_prestige_c_4_v1.dat'
    expected=at(starter,117,0,108);assert expected=='minecraft:stone' and actual=='minecraft:dirt'
    read(region);history_bytes=read(history)
    try:journal=parser.NBT(gzip.decompress(history_bytes)).root();phase=journal['phase']
    except (ValueError,KeyError,gzip.BadGzipFile):phase='requires sealed-journal reader'
    report={'schema':1,'scope':'READ_ONLY_STATIC_AUDIT_WITH_RETAINED_FAILED_WORLD_OBSERVATION','nativePass':False,'ownerAcceptance':False,'failure':{'world':str(world),'point':point,'expected':expected,'actual':actual,'blockEntity':live.entities[(cx,cz)].get(point),'paidHallHistory':str(history),'historyPhase':phase,'TH3':at(halls['TH3'],74,0,64),'TH4R1':at(halls['TH4R1'],74,0,64)},'sourcePins':pins,'sites':records,'findings':{'eligibleSites':48,'cityCount':16,'tiers':['TH1','TH2','TH3','TH4R1','TH4R2','TH5R1','TH5R2'],'allEarlyHallMismatches':sum(r['tiers'][t][mode]['changedReads'] for r in records for t in ('TH1','TH2','TH3') for mode in ('raw','prepared')),'ownedFactoryOverlap':0,'prestigeChangedReadsPerCity':309,'allPrestigeChanges':'minecraft:stone -> minecraft:dirt at world y79 only','vehicleSupportFloorY':80,'legacyCityAIdentity':'No identity, bay mask, price, journal or placement modification performed. City A has the same qualified foundation-context conflicts.','collisionPolicy':'Actual native collision, fluid, entity and full-support checks remain necessary; this static audit grants no occupancy or driving permission.'}}
    output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps({'report':str(output),'sites':48,'earlyHallMismatches':report['findings']['allEarlyHallMismatches'],'failure':report['failure'],'perSlot':{p:{'intersection':len(v[0]),'TH4changes':len(v[1]['TH4R1'][True])} for p,v in slots.items()}},indent=2))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--repo',type=Path,required=True);p.add_argument('--inputs',type=Path,required=True);p.add_argument('--world',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();run(a.repo.resolve(),a.inputs.resolve(),a.world.resolve(),a.output.resolve())
