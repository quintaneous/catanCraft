"""Read-only independent audit of the R9 review assets, proposed sites and pristine R7."""
import argparse
from collections import Counter
import gzip
from hashlib import sha256
import importlib.util
import json
from pathlib import Path
import struct
import sys

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
ORIGINAL = 'e7ecd5d0f0ac5c1457be5c234d2e5e08c9239007787b20e0bdc45b3e2b8d862d'
REGISTRY_PINS = {
    'minecraft_1_20_1_block_ids.json': '0b8b3236235802db1520d1514472154885c25ad1018dafff1c7072c361b04299',
    'minecraft_1_20_1_block_definitions.json': '226a2f512571814ce56d413524a38e20b340e28495699551f673b76a9f6976ff',
}
WORLD = 'source_world/CatanCraft_Season1_R7_Development'
REQUIRED_TAGS = {'Version':3, 'DataVersion':3, 'Width':2, 'Height':2, 'Length':2,
                 'Offset':11, 'PaletteMax':3, 'Palette':10, 'BlockData':7}

def require(condition, reason):
    if not condition:
        raise ValueError(reason)

def digest(path):
    with path.open('rb') as f:
        return sha256(f.read()).hexdigest()

def module(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod

def baseline(root, repo):
    manifest = root/'INPUT_MANIFEST.json'
    require(digest(manifest) == ORIGINAL, 'Unexpected original manifest')
    records = json.loads(manifest.read_bytes())['files']
    matches = {}
    for row in records:
        path = root/row['path']
        require(path.resolve().is_relative_to(root) and path.is_file() and not path.is_symlink(), 'Missing or redirected file: '+row['path'])
        actual = digest(path)
        require(actual == row['sha256'] and path.stat().st_size == row['bytes'], 'Input changed: '+row['path'])
        matches[row['path']] = actual
    source = root/WORLD
    actual_world = {p.relative_to(root).as_posix() for p in source.rglob('*') if p.is_file()}
    region = {f'{WORLD}/region/r.{x}.{z}.mca' for x in range(-2,2) for z in range(-2,2)}
    require(actual_world == region | {WORLD+'/level.dat'}, 'World inventory differs')
    map_path = 'bootstrap_reference/config/catancraft/map.json'
    require(digest(repo/'src/main/resources/data/catancraft/maps/season1_river_bridges_v3.json') == matches[map_path], 'Gameplay source map differs')
    return {'manifestSHA256':ORIGINAL, 'matchingManifestEntries':len(matches),
            'canonicalWorldRegionFileSHA256':{path.rsplit('/',1)[1]:matches[path] for path in sorted(region)},
            'originalLevelDatSHA256':matches[WORLD+'/level.dat'], 'mapSHA256':matches[map_path],
            'matchingBootstrapConfigFiles':sum(p.startswith('bootstrap_reference/config/') for p in matches)}

def nbt_root(data, parser):
    reader = parser(data)
    require(reader.num('>B',1)==10 and reader.string()=='Schematic', 'Expected named Schematic compound')
    tags, fields = {}, {}
    while True:
        t = reader.num('>B',1)
        if not t: break
        name = reader.string()
        require(name not in fields, 'Duplicate root tag')
        tags[name] = t
        if name == 'Palette' and t == 10:
            palette = {}
            while True:
                state_tag = reader.num('>B',1)
                if not state_tag: break
                require(state_tag==3, 'Palette entry must be TAG_Int')
                state = reader.string()
                require(state not in palette, 'Duplicate state in palette')
                palette[state] = reader.payload(3)
            fields[name] = palette
        else:
            fields[name] = reader.payload(t)
    require(reader.i == len(data), 'Unexpected bytes following NBT root')
    require(all(tags.get(k) == t for k,t in REQUIRED_TAGS.items()), 'Unexpected/missing Sponge v2 tag or type')
    return fields, tags

def block_counts(data, allowed, volume):
    counts = Counter()
    value = shift = 0
    for byte in data:
        value |= (byte & 127) << shift
        if byte & 128:
            shift += 7
            require(shift < 35, 'Oversize palette varint')
            continue
        require(value in allowed and (shift == 0 or byte > 0), 'Unknown or noncanonical palette index')
        counts[value] += 1
        value = shift = 0
    require(shift == 0, 'Truncated palette varint')
    require(sum(counts.values()) == volume, 'Block volume differs from encoded count')
    return counts

def protected_content(value):
    if isinstance(value, dict):
        for key,item in value.items():
            require(key.casefold() not in {'loottable','loot_table','loottableseed','loot_table_seed','command','items','passengers','spawndata'}, 'Gameplay payload present: '+key)
            protected_content(item)
    elif isinstance(value, list):
        for item in value:
            protected_content(item)

def schem(path, parser, registry):
    fields, tags = nbt_root(gzip.decompress(path.read_bytes()),parser)
    require((fields['Version'],fields['DataVersion']) == (2,3465), 'Expected Sponge v2 / Java 1.20.1 (3465)')
    dims = [fields[k] for k in ('Width','Height','Length')]
    require(min(dims)>0 and len(fields['Offset'])==12,'Invalid dimensions/offset')
    offset = struct.unpack('>iii', fields['Offset'])
    require(offset==(0,0,0), 'Zero offset expected for these review assets')
    for key in ('Entities','BlockEntities','TileEntities'):
        if key in tags:
            require(tags[key]==9 and fields[key]==[],key+' must be an empty list')
    protected_content(fields)
    palette = fields['Palette']
    require(len(palette)==fields['PaletteMax'] and set(palette.values())==set(range(len(palette))), 'Invalid palette IDs')
    categories = {}
    for state, i in palette.items():
        registry.validate_state(state)
        base = state.split('[',1)[0].removeprefix('minecraft:')
        require(base not in {'command_block','chain_command_block','repeating_command_block','jigsaw','structure_block','spawner','end_portal','nether_portal'}, 'Unexpected active block: '+state)
        categories[i] = ('empty' if base in {'air','cave_air','void_air'} else
                         'fluid' if base in {'water','lava'} else
                         'pasteMask' if base=='structure_void' else 'otherBlock')
    counts = block_counts(fields['BlockData'],set(categories),dims[0]*dims[1]*dims[2])
    summary = Counter()
    for index,n in counts.items():
        summary[categories[index]] += n
    return {'file':path.name,'sha256':digest(path),'formatVersion':2,'dataVersion':3465,
            'sizeXYZ':dims,'offsetXYZ':list(offset),'encodedCells':sum(counts.values()),
            'usedPaletteStates':len(counts),'validatedPaletteStates':len(palette),
            'counts':{k:summary[k] for k in ('empty','fluid','otherBlock','pasteMask')},'entities':0,'blockEntities':0}

def fit(asset, site, pivot, turn):
    w,h,l = asset['sizeXYZ']
    require(0<=pivot[0]<w and 0<=pivot[2]<l and 0<=pivot[1]<h, 'Registration pivot lies outside asset')
    base_x,base_z = site['centerXZ']
    signs = {0:1,180:-1}
    sign = signs[turn]
    xs = [base_x+sign*(x-pivot[0]) for x in (0,w-1)]
    zs = [base_z+sign*(z-pivot[2]) for z in (0,l-1)]
    bounds = [min(xs),min(zs),max(xs)+1,max(zs)+1]
    sx,sz,ex,ez = site['boundsXZ']
    ax,az,bx,bz = bounds
    require(sx<=ax<bx<=ex and sz<=az<bz<=ez,'Full asset box exceeds reservation')
    require(bx<=-1024 or ax>=1024 or bz<=-1024 or az>=1024,'Placement overlaps original authored chunk square')
    require(-1600<=ax<bx<=1600 and -1600<=az<bz<=1600,'Asset exceeds proposed border')
    base_y = 75-pivot[1]
    return {'site':site['id'],'localPivotXYZ':pivot,'worldPivotXYZ':[base_x,75,base_z],
            'rotationDegreesAboutY':turn,'assetBoundsXZHalfOpen':bounds,'assetBoundsYHalfOpen':[base_y,base_y+h],
            'reservationBoundsXZHalfOpen':site['boundsXZ'],'approachFrom':site['approachFrom'],
            'clearanceToReservationEdgeBlocks':min(ax-sx,az-sz,ex-bx,ez-bz),
            'clearanceToAuthoredChunkSquareBlocks':max(-1024-bx,ax-1024,-1024-bz,az-1024),
            'clearanceToProposedWorldBorderBlocks':min(ax+1600,az+1600,1600-bx,1600-bz)}

def run(workspace, prototype, registry_dir=None):
    root = workspace/'inputs/CatanCraft_Clean_R7_Input'
    repo = workspace/'resource-ui'
    original = baseline(root.resolve(), repo.resolve())
    registry_dir = (registry_dir or workspace/'python-tests/CatanCraft_City_A_Sector_Walls_P3/source')
    for name,pin in REGISTRY_PINS.items():
        require(digest(registry_dir/name) == pin,'Block registry data differs: '+name)
    registry = module(registry_dir/'block_registry.py','r9_vanilla_registry')
    parser = module(root/'nbt_check.py','r9_pristine_nbt').NBT
    atlas = json.loads((prototype/'atlas/atlas.json').read_bytes())
    sites = {r['id']:r for r in atlas['sites']}
    specs = {'coast':2,'lighthouse':1,'wreck':1}
    assets = {}
    for group, count in specs.items():
        sources = sorted((prototype/group).rglob('*.schem'))
        require(len(sources)==count, f'{group} expected {count} schematic(s); found {len(sources)}')
        rows = [schem(p,parser,registry) for p in sources]
        assets[group] = rows
    require(atlas['waterBlockY']==75,'Unexpected top water block in atlas')
    fits = {
        'lighthouse':fit(assets['lighthouse'][0],sites['lighthouse_west'],[27,8,23],180),
        'wreck':fit(assets['wreck'][0],sites['wreck_east'],[37,15,24],0),
    }
    return {'result':'PASS_OFFLINE_ASSET_AND_PLACEMENT_AUDIT','originalInputs':original,
            'atlasSHA256':digest(prototype/'atlas/atlas.json'), 'registrySourceCommit':registry.SOURCE_COMMIT,
            'registryFileSHA256':REGISTRY_PINS,'schematics':assets,'proposedPlacementChecks':fits,
            'nativeServerStarted':False,'worldPlaced':False,'connectedPlayerTested':False}

if __name__ == '__main__':
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument('--workspace',type=Path,required=True)
    cli.add_argument('--prototype',type=Path,default=HERE.parent)
    cli.add_argument('--registry-dir',type=Path)
    args = cli.parse_args()
    print(json.dumps(run(args.workspace.resolve(),args.prototype.resolve(),args.registry_dir),indent=2))
