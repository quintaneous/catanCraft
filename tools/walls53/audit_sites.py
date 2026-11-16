#!/usr/bin/env python3
"""Read-only authored-world qualification for versioned all-city wall sites.

This examines the immutable R7 source, normal starter placement and exact R5
public deltas. It does not paste a fixture or infer a native/owner gameplay pass.
The legacy A template and resource are inputs, never modified by this exporter.
"""
import argparse
import base64
import collections
import gzip
import hashlib
import importlib.util
import io
import itertools
import json
from pathlib import Path
import struct
import sys
import zipfile
import zlib

MAP_SHA = 'e50d9753c807b5fe6f8fdee9765ae09640d951cc0ba2d59f1612f0923485c986'
INPUT_SHA = 'e7ecd5d0f0ac5c1457be5c234d2e5e08c9239007787b20e0bdc45b3e2b8d862d'
P3_SHA = 'facc415887db9cff673296c3c40ca8a30f80832570de36de8fe4d6a6827560c2'
BUNDLE_SHA = 'cbaf8b095958f7de19a58bbca2723f18a1273d908eb5f174d36cd4aa1e9608d1'
STARTER_SHA = 'fe03d2ddcc76555ad56f0767d601100380193053bfdca1f56dd2f45d7be9e443'
R5_SHA = '7cbbf0c9cc38d9971e12e1b20fdba37aba630fd41d83c8819f2fdaa2d70c50cd'
HOUSE_SHA = '2722756e24b1d647da167dcc4b78db0f6d4a559d297379c3887e2b89890734fb'
SIDES = ('north', 'east', 'south', 'west')
OPPOSITE = dict(zip(SIDES, ('south', 'west', 'north', 'east')))
AIR = 'minecraft:air'


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def pinned(path, expected):
    if path.is_symlink() or not path.is_file():
        raise ValueError(f'Input must be an ordinary file: {path}')
    raw = path.read_bytes()
    if sha(raw) != expected:
        raise ValueError(f'Input hash changed: {path}')
    return raw


def state(text, rotate=False):
    name, _, tail = text.partition('[')
    props = dict(pair.split('=') for pair in tail.rstrip(']').split(',') if pair)
    # The original Sponge palette alone omits this default. P3 and R7 contain it.
    if name == 'minecraft:grass_block':
        props.setdefault('snowy', 'false')
    if rotate:
        props = {OPPOSITE.get(k, k): OPPOSITE.get(v, v) if k == 'facing'
                 else str((int(v) + 8) % 16) if k == 'rotation' else v
                 for k, v in props.items()}
    return name + ('[' + ','.join(f'{k}={v}' for k, v in sorted(props.items())) + ']' if props else '')


def transform(p, center):
    sign = 1 if center['rotation'] == 0 else -1
    return (center['x'] + sign * (p[0] + 750), center['y'] + p[1] - 81,
            center['z'] + sign * (p[2] + 750))


def rows_hash(mapping):
    raw = ''.join(f'{x},{y},{z}\t{s}\n' for (x,y,z),s in sorted(mapping.items())).encode()
    return sha(raw)


class World:
    def __init__(self, directory, nbt):
        self.directory, self.nbt = directory, nbt
        self.regions, self.chunks, self.entities = {}, {}, {}

    def chunk(self, cx, cz):
        key = (cx, cz)
        if key in self.chunks:
            return self.chunks[key]
        path = self.directory / 'region' / f'r.{cx//32}.{cz//32}.mca'
        if path not in self.regions:
            self.regions[path] = path.read_bytes()
        raw = self.regions[path]
        index = ((cx % 32) + (cz % 32) * 32) * 4
        offset = int.from_bytes(raw[index:index+3], 'big') * 4096
        if not offset:
            raise ValueError(f'Missing authored chunk {key}')
        length = int.from_bytes(raw[offset:offset+4], 'big')
        if raw[offset+4] != 2:
            raise ValueError('Unexpected authored chunk compression')
        data = self.nbt(zlib.decompress(raw[offset+5:offset+4+length])).root()
        sections = {}
        for section in data['sections']:
            if 'block_states' not in section:
                continue
            blocks = section['block_states']
            palette = [state(p['Name'] + ('[' + ','.join(f'{k}={v}' for k,v in p['Properties'].items()) + ']'
                       if p.get('Properties') else '')) for p in blocks['palette']]
            encoded = blocks.get('data', b'')
            longs = struct.unpack('>' + str(len(encoded)//8) + 'Q', encoded)
            sections[section['Y']] = (palette, longs)
        self.chunks[key] = sections
        self.entities[key] = {(e['x'],e['y'],e['z']): e for e in data.get('block_entities', [])}
        return sections

    def at(self, p):
        x,y,z = p
        section = self.chunk(x//16,z//16).get(y//16)
        if section is None:
            return AIR
        palette, longs = section
        if len(palette) == 1:
            return palette[0]
        bits = max(4, (len(palette)-1).bit_length())
        per = 64//bits
        index = (y%16)*256 + (z%16)*16 + x%16
        return palette[(longs[index//per] >> ((index%per)*bits)) & ((1<<bits)-1)]


def geometry(template):
    baseline, owned, tiers = {}, {}, {}
    for side, rows in template['ownership'].items():
        for x,y,z,basic,reinforced in rows:
            p = (x,y,z)
            if p in owned:
                raise ValueError('Two owners for a template cell')
            owned[p], tiers[p] = side, (AIR,state(basic),state(reinforced))
    for asset in template['assets']:
        for row in asset['reads'] + asset['fresh']:
            p, s = tuple(row[:3]), state(row[3])
            if p in baseline and baseline[p] != s:
                raise ValueError('Template source conflict')
            baseline[p] = s
    if any(baseline[p] != AIR for p in owned):
        raise ValueError('Owned template source must be exact air')
    connections = template['corners']['connections']
    configs = histories = transitions = 0
    # Check every level vector and every supported open/closed corner history.
    # Transform is separately required to be bijective for each actual site.
    for vector in itertools.product(range(3),repeat=4):
        levels = dict(zip(SIDES,vector)); configs += 1
        eligible = {c['corner']:c for c in connections
                    if levels[c['transactionOwner']] == (1 if c['ownerTier']=='basic' else 2)
                    and levels[c['neighbor']] == (1 if c['neighborTier']=='basic' else 2)}
        names = sorted(eligible)
        for flags in itertools.product((False,True),repeat=len(names)):
            opened = {k for k, flag in zip(names,flags) if flag}; histories += 1
            before = {p:tiers[p][levels[owner]] for p,owner in owned.items()}
            for k in opened:
                for edit in eligible[k]['changes']:
                    p = tuple(edit['worldXYZ'])
                    if owned[p] != edit['cellOwner'] or before[p] != state(edit['before']) or edit['after'] != AIR:
                        raise ValueError('Invalid exact corner ownership/source')
                    before[p] = AIR
            for side in SIDES:
                if levels[side] == 2:
                    continue
                afterlevels = levels | {side:levels[side]+1}
                active = {c['corner']:c for c in connections
                          if afterlevels[c['transactionOwner']] == (1 if c['ownerTier']=='basic' else 2)
                          and afterlevels[c['neighbor']] == (1 if c['neighborTier']=='basic' else 2)}
                afteropen = opened | {k for k,c in active.items() if side in (c['transactionOwner'],c['neighbor'])}
                after = {p:tiers[p][afterlevels[owner]] for p,owner in owned.items()}
                for k in afteropen:
                    for edit in active[k]['changes']:
                        p = tuple(edit['worldXYZ'])
                        if after[p] != state(edit['before']):
                            raise ValueError('Invalid upgraded corner source')
                        after[p] = AIR
                for p in owned:
                    if before[p] != after[p] and owned[p] != side:
                        allowed = {tuple(e['worldXYZ']) for k,c in active.items()
                                   if k in afteropen and side in (c['transactionOwner'],c['neighbor']) for e in c['changes']}
                        if p not in allowed:
                            raise ValueError('Transition changes unrelated sector')
                transitions += 1
    return baseline, owned, {'levelConfigurations':configs,'supportedCornerHistories':histories,
                              'nextTierTransitions':transitions,'uniqueOwnedCells':len(owned)}


def run(repo, input_root, report_file, catalog_file):
    manifest_raw = pinned(input_root/'INPUT_MANIFEST.json', INPUT_SHA)
    manifest = json.loads(manifest_raw)
    verified = []
    for entry in manifest['files']:
        path = input_root / entry['path']
        raw = pinned(path,entry['sha256'])
        if len(raw) != entry['bytes']:
            raise ValueError('Input size differs')
        verified.append({'path':entry['path'],'sha256':sha(raw),'bytes':len(raw)})
    # Import the verified reader without creating bytecode beside immutable inputs.
    sys.dont_write_bytecode = True
    spec = importlib.util.spec_from_file_location('walls53_nbt',input_root/'nbt_check.py')
    module = importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    maps = json.loads(pinned(repo/'src/main/resources/data/catancraft/maps/season1_river_bridges_v3.json',MAP_SHA))
    template = json.loads(gzip.decompress(pinned(repo/'src/main/resources/assets/catancraft/sector_p3/city_a.json.gz',P3_SHA)))
    baseline, owned, proof = geometry(template)
    bundle = base64.b64decode((repo/'src/main/resources/data/catancraft/basic_construction/assets.b64').read_bytes())
    if sha(bundle) != BUNDLE_SHA:raise ValueError('Construction bundle changed')
    starter_raw = zipfile.ZipFile(io.BytesIO(bundle)).read('starter_settlement.schem')
    if sha(starter_raw) != STARTER_SHA:raise ValueError('Starter source changed')
    starter = module.NBT(gzip.decompress(starter_raw)).root()
    palette = {v:state(k) for k,v in starter['Palette'].items()}; voxel=[];value=shift=0
    for b in starter['BlockData']:
        value |= (b&127)<<shift
        if b&128:shift+=7
        else:voxel.append(palette[value]);value=shift=0
    w,h,l = (starter[k] for k in ('Width','Height','Length'))
    if len(voxel) != w*h*l or shift:raise ValueError('Incomplete starter')
    r5raw = gzip.decompress(base64.b64decode((repo/'src/main/resources/data/catancraft/r5_public/contract.b64').read_bytes()))
    if sha(r5raw) != R5_SHA:raise ValueError('R5 source changed')
    r5 = json.loads(r5raw)
    house_path = repo/'src/main/resources/assets/catancraft/producer_r2/r5-house-contexts.json.gz'
    if not house_path.is_file():
        candidates=list((repo/'src/main/resources').rglob('r5-house-contexts.json.gz'))
        if len(candidates)!=1:raise ValueError('Ambiguous house contract')
        house_path=candidates[0]
    house = json.loads(gzip.decompress(pinned(house_path,HOUSE_SHA)))
    world = World(input_root/'source_world/CatanCraft_Season1_R7_Development',module.NBT)
    source_config=input_root/'bootstrap_reference/config/catancraft'
    civic_zip=zipfile.ZipFile(source_config/'CatanCraft_R5_City_Rework_And_Civic_Assets.zip')
    probe_zip=zipfile.ZipFile(source_config/'R5_READ_ONLY_PROBE_INDEX.zip')
    catalog = {'schemaVersion':1,'mapSHA256':MAP_SHA,'inputManifestSHA256':INPUT_SHA,
               'templateResourceSHA256':P3_SHA,'starterSHA256':STARTER_SHA,'publicSHA256':R5_SHA,'cities':{}}
    report = {'schemaVersion':1,'method':'Read-only actual authored R7 palette and block-entity inspection, composed with normal starter placement and exact R5 public writes. Static qualification only; native placement, startup, collision and owner acceptance are separate.',
              'pins':{k:v for k,v in catalog.items() if k.endswith('SHA256')},'templateGeometry':proof,'cities':[],'inputFilesVerified':verified,
              'geometrySourceFiles':{name:sha((repo/name).read_bytes()) for name in [
                  'src/main/java/com/quin/catancraft/world/SchematicAssetRegistry.java',
                  'src/main/java/com/quin/catancraft/world/IndustrialPlotService.java',
                  'src/main/java/com/quin/catancraft/world/ProducerRevisions.java',
                  'src/main/java/com/quin/catancraft/world/R5HouseRevisions.java']},
              'limits':['Static geometry/source qualification is not native placement, process restart, no-grant progression, connected-client or owner acceptance.',
                        'Every factory tier uses its registered full-volume envelope. Economic unlock/accounting behavior is outside this audit.',
                        'Readonly observed leaves and soil remain exact states; this audit grants no general natural-state normalization.',
                        'Twenty-six exact authored soil cells occupy already-owned structural wall positions; construction replaces them with the original Basic/Reinforced blocks and must retain original rollback source.']}
    for city in maps['territories']:
        center = city['cityCenter']; rot = center['rotation']==180; sign=-1 if rot else 1
        if center['rotation'] not in (0,180):raise ValueError('Unsupported city transform')
        expected = {transform(p,center):state(s,rot) for p,s in baseline.items()}
        ownership = {transform(p,center):OPPOSITE.get(d,d) if rot else d for p,d in owned.items()}
        if len(expected)!=len(baseline) or len(ownership)!=len(owned):raise ValueError('Nonbijective site')
        r5writes = {(center['x']+sign*(x-80),center['y']+y-2,center['z']+sign*(z-84)):state(r5['palette'][index],rot)
                    for x,y,z,index in r5['writes']}
        anchor = city['settlementAnchor']
        def founded_at(p):
            x,y,z=sign*(p[0]-anchor['x'])+80,p[1]-anchor['y']+2,sign*(p[2]-anchor['z'])+1
            if 0<=x<w and 0<=y<h and 0<=z<l:return state(voxel[(y*l+z)*w+x],rot),'starter'
            return world.at(p),'authoredTerrain'
        rawdiff,foundeddiff,prepareddiff,blocked,bes=[],[],[],[],[]
        composition=collections.Counter();overrides=[];owned_overrides=[];observed={}
        observed_types=collections.Counter();override_types=collections.Counter()
        for p,e in sorted(expected.items()):
            raw=world.at(p);founded,source=founded_at(p);actual=r5writes.get(p,founded)
            observed[p]=actual;composition['public' if p in r5writes else source]+=1
            observed_types[actual.split('[')[0]]+=1
            if raw!=e:rawdiff.append([*p,e,raw])
            if founded!=e:foundeddiff.append([*p,e,founded])
            if actual!=e:
                prepareddiff.append([*p,e,actual]);override_types[actual.split('[')[0]]+=1
                if p in ownership:
                    original=(sign*(p[0]-center['x'])-750,p[1]-center['y']+81,sign*(p[2]-center['z'])-750)
                    target=next(r for r in template['ownership'][owned[original]] if tuple(r[:3])==original)
                    basic,reinforced=state(target[3],rot),state(target[4],rot)
                    owned_overrides.append([*p,actual,basic,reinforced])
                    # Exact immutable authored soil occupied by existing load-bearing
                    # wall blocks, not a generic vegetation-removal permission.
                    if not (actual=='minecraft:grass_block[snowy=false]' and owned[original]=='north'
                            and original[1]==81 and original[2] in (-853,-852)
                            and -763<=original[0]<=-732 and basic=='minecraft:stone_bricks'
                            and reinforced=='minecraft:light_gray_concrete'):
                        blocked.append([*p,'unqualified owned source differs',e,actual,basic,reinforced])
                else:overrides.append([*p,actual])
            if p in world.entities[(p[0]//16,p[2]//16)]:bes.append([*p])
        # The normal six-house update must remain disjoint from every wall guard.
        houseoverlap=[]
        for group in house['houses']:
            for route,changes in group['routes'].items():
                for change in changes:
                    x,y,z=change['local_xyz'];p=(anchor['x']+sign*(x-80),anchor['y']+y-2,anchor['z']+sign*(z-1))
                    if p in expected:houseoverlap.append([group['id'],route,*p])
        roads=[transform(p,center) for p in baseline if -770<=p[0]<=-730 and p[2]>=-666]
        if any(p in ownership for p in roads):raise ValueError('Rear corridor ownership overlap')
        # Actual per-city reservations are transforms of this reviewed layout;
        # use the exact map center, not a translated north-facing A fixture.
        envelopeoverlap=[]
        for box in template['runtimeAudit']['envelopes']:
            corners=[transform(tuple(box[k]),center) for k in ('worldMinInclusive','worldMaxInclusive')]
            lo=tuple(min(a,b) for a,b in zip(*corners));hi=tuple(max(a,b) for a,b in zip(*corners))
            overlap=[p for p in ownership if all(a<=v<=b for a,v,b in zip(lo,p,hi))]
            if overlap:envelopeoverlap.append({'id':box['id'],'count':len(overlap),'samples':overlap[:8]})
        # Every ordinary master uses the reviewed 31x47x31 footprint. Resolve
        # the real roadside anchor and the extra plot4/steel master flip, then
        # check the entire volume for every economic tier and alternate slot.
        factoryoverlap=[];factoryplacements=0
        for plot,dx,dz in [('plot_1',-45,-24),('plot_3',-45,16),('plot_4',45,16)]:
            for master in ['vehicle_factory','weapons_factory','steel_mill','textile_mill','fuel_refinery',
                           'concrete_plant','machine_shop','chemical_plant','electronics_factory']:
                ax=1 if master=='steel_mill' else 29
                flip=(plot=='plot_4')!=(master=='steel_mill');fs=-sign if flip else sign
                anchorx,anchorz=center['x']+sign*dx,center['z']+sign*dz
                x0,x1=sorted((anchorx+fs*-ax,anchorx+fs*(30-ax)))
                z0,z1=sorted((anchorz+fs*-15,anchorz+fs*15))
                overlaps=[p for p in expected if x0<=p[0]<=x1 and center['y']-2<=p[1]<=center['y']+44 and z0<=p[2]<=z1]
                for tier in range(1,6):
                    factoryplacements+=1
                    if overlaps:factoryoverlap.append({'plot':plot,'master':master,'tier':tier,'guardCells':len(overlaps)})
        civiccoverage=[]
        for name,archive in [('commercial_district',civic_zip),('logistics_center',civic_zip),
                             ('cleanup_tree_16_10',probe_zip),('commercial_sidewalk',probe_zip),('logistics_service_connection',probe_zip)]:
            path=(f'contracts/civic-deltas/{city["id"].upper()}_{name}.json' if archive is civic_zip
                  else f'contracts/groups/{city["id"].upper()}/{name}.json')
            document=json.loads(archive.read(path));changes=document['writes' if archive is civic_zip else 'changes']
            overlaps=[r['world_xyz'] for r in changes if tuple(r['world_xyz']) in expected]
            civiccoverage.append({'module':name,'contract':path,'contractSHA256':sha(archive.read(path)),
                                  'writes':len(changes),'guardOverlap':overlaps})
        routes={transform(tuple(p),center) for asset in template['assets'] for p in asset['routeWorldXYZ']}
        routeobstructions=[[*p,observed[p]] for p in routes if observed[p]!=AIR and p not in ownership]
        leafadjacent=[]
        for p,s in observed.items():
            if not s.split('[')[0].endswith('_leaves'):continue
            if any((p[0]+dx,p[1]+dy,p[2]+dz) in ownership for dx,dy,dz in
                   [(1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1)]):leafadjacent.append([*p,s])
        qualified=not(blocked or bes or houseoverlap or envelopeoverlap or factoryoverlap
                      or any(c['guardOverlap'] for c in civiccoverage) or routeobstructions or leafadjacent)
        catalog['cities'][city['id']]={'qualified':qualified,'rotation':center['rotation'],'center':[center['x'],center['y'],center['z']],
            'guardCount':len(expected),'guardSHA256':rows_hash(observed),'ownedCount':len(ownership),
            'sourceOverrides':overrides,'ownedSourceOverrides':owned_overrides,'protectedRoads':sorted(roads)}
        row={'city':city['id'],'startsWithSettlement':city['startsWithSettlement'],'rotation':center['rotation'],'qualified':qualified,
            'guardCells':len(expected),'sourceComposition':dict(composition),'rawDifferences':len(rawdiff),'rawDifferenceSamples':rawdiff[:12],
            'foundedDifferences':len(foundeddiff),'foundedDifferenceSamples':foundeddiff[:12],'preparedDifferences':len(prepareddiff),
            'preparedDifferenceSamples':prepareddiff[:40],'blockedOwnedSources':blocked,'guardBlockEntities':bes,'houseRouteOverlap':houseoverlap,
            'exactOwnedSoilReplacements':owned_overrides,'preparedOverrideTypes':dict(override_types),'preparedGuardTypes':dict(observed_types),
            'buildingEnvelopeOverlap':envelopeoverlap,'protectedRoadCells':len(roads),'guardSHA256':rows_hash(observed),
            'factoryTierPlacementsReviewed':factoryplacements,'factoryFullEnvelopeGuardOverlap':factoryoverlap,
            'civicAndSupportContracts':civiccoverage,'declaredRouteObstructions':routeobstructions,'readLeavesAdjacentToWallWrites':leafadjacent,
            'worldCompassOwnedCells':dict(collections.Counter(ownership.values())),
            'levelConfigurations':proof['levelConfigurations'],'supportedCornerHistories':proof['supportedCornerHistories']}
        report['cities'].append(row)
        print(city['id'], 'raw',len(rawdiff),'founded',len(foundeddiff),'prepared',len(prepareddiff),'blocked',len(blocked),'BE',len(bes),'qualified',qualified,flush=True)
    for entry in verified:
        pinned(input_root/entry['path'],entry['sha256'])
    report.update({'inputFilesReverifiedUnchanged':len(verified),'chunksRead':len(world.chunks),'regionsRead':len(world.regions),
                   'qualifiedCities':sum(r['qualified'] for r in report['cities']),'nativeExecutionClaimed':False})
    report_file.parent.mkdir(parents=True,exist_ok=True)
    report_file.write_text(json.dumps(report,indent=2)+'\n')
    if not all(r['qualified'] for r in report['cities']):
        raise ValueError('Actual-site source qualification blocked; report retained, no catalog published')
    encoded=gzip.compress((json.dumps(catalog,sort_keys=True,separators=(',',':'))+'\n').encode(),mtime=0)
    catalog_file.parent.mkdir(parents=True,exist_ok=True);catalog_file.write_bytes(encoded)
    print('catalog SHA256',sha(encoded),'bytes',len(encoded),flush=True)
    return report,catalog


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo',type=Path,default=Path(__file__).resolve().parents[2])
    parser.add_argument('--input-root',type=Path,required=True)
    parser.add_argument('--report',type=Path,required=True)
    parser.add_argument('--catalog',type=Path,required=True)
    args=parser.parse_args();run(args.repo,args.input_root,args.report,args.catalog)
