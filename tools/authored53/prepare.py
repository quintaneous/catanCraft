#!/usr/bin/env python3
"""Stage a NEW native campaign from the immutable reviewed authored R7 input.

No installed files, EULA consent, grants or source-world mutations. Restart
verification reuses the staged run; this program never resets an existing run.
"""
from pathlib import Path
import argparse
import hashlib
import json
import re
import sys
import uuid

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / 'tools'))
from prepare_fresh_demo import checked_path, read_regular, InputError
from package_authored_demo import level_data, relative
from server53.world_profile import derive

INPUT_SHA = 'e7ecd5d0f0ac5c1457be5c234d2e5e08c9239007787b20e0bdc45b3e2b8d862d'
WORLD = 'CatanCraft_Season1_R7_Development'
MARKER = '.catancraft-authored53-native.json'
NAMESPACES = {'catancraft_progression53', 'catancraft_sector53_authored'}


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def encoded(value):
    return (json.dumps(value, indent=2, sort_keys=True) + '\n').encode()


def prepare(input_root, destination, source, layout, namespace):
    root = checked_path(input_root, directory=True)
    if not re.fullmatch('[0-9a-f]{40}', source) or layout not in range(9) or namespace not in NAMESPACES:
        raise InputError('Exact source, supported native namespace and layout 0..8 required')
    target = Path(destination).absolute()
    for p in [target, *target.parents]:
        if p.is_symlink():
            raise InputError('Native campaign refuses symbolic link paths')
    if target != target.resolve(strict=False):
        raise InputError('Native campaign requires a normalized absolute destination')
    if target.exists() or target == REPO / 'build' or not target.is_relative_to(REPO / 'build'):
        raise InputError('Choose a NEW destination beneath this checkout build directory')
    raw = read_regular(root / 'INPUT_MANIFEST.json')
    if sha(raw) != INPUT_SHA:
        raise InputError('Original clean input manifest differs')
    inventory = json.loads(raw)
    supplied = {}
    for row in inventory['files']:
        name = relative(row['path'])
        data = read_regular(root / name)
        if len(data) != row['bytes'] or sha(data) != row['sha256'] or name in supplied:
            raise InputError('Changed/repeated original input: ' + name)
        supplied[name] = data
    if len(supplied) != 40:
        raise InputError('Unexpected clean input inventory')
    prefix = 'source_world/' + WORLD + '/'
    world = {k[len(prefix):]: v for k, v in supplied.items() if k.startswith(prefix)}
    expected = {'level.dat'} | {f'region/r.{x}.{z}.mca' for x in range(-2, 2) for z in range(-2, 2)}
    if set(world) != expected:
        raise InputError('Expected the exact clean seventeen-file world, no inherited history')
    level = level_data(world['level.dat'])
    if 'Player' in level or level['DataVersion'] != 3465 or level['WorldGenSettings']['seed'] != 1042026:
        raise InputError('Clean source metadata differs')
    config_prefix = 'bootstrap_reference/config/'
    config = {'config/' + k[len(config_prefix):]: v for k, v in supplied.items() if k.startswith(config_prefix)}
    current_map = read_regular(REPO / 'src/main/resources/data/catancraft/maps/season1_river_bridges_v3.json')
    if config.get('config/catancraft/map.json') != current_map:
        raise InputError('Reviewed map and current source differ')
    before = {name: sha(data) for name, data in world.items()}
    authored_hash = sha(encoded(before))
    world['level.dat'], profile = derive(world['level.dat'])
    identity = {'schema': 1, 'purpose': 'catancraft-fresh-authored-demo',
                'worldId': str(uuid.uuid4()), 'levelName': level['LevelName'],
                'seed': 1042026, 'dimension': 'minecraft:overworld',
                'mapSha256': sha(current_map), 'authoredWorldSha256': authored_hash,
                'bootstrapRevision': 'authored53-protected-map-v1',
                'installationBoundPath': str(target / WORLD)}
    test_input = {'schema': 1, 'purpose': 'verified-clean-r7-progression53',
                  'worldFolder': WORLD, 'levelName': level['LevelName'], 'seed': 1042026,
                  'mapSha256': sha(current_map), 'authoredWorldSha256': authored_hash,
                  'sourceCommit': source, 'layout': layout}
    files = {WORLD + '/' + k: v for k, v in world.items()}
    files.update(config)
    files[WORLD + '/catancraft-fresh-demo.json'] = encoded(identity)
    files['config/catancraft/progression53-input.json'] = encoded(test_input)
    properties = {'level-name': WORLD, 'level-type': 'minecraft:normal', 'level-seed': '1042026',
                  'gamemode': 'adventure', 'force-gamemode': 'true', 'difficulty': 'normal',
                  'server-ip': '127.0.0.1', 'server-port': '25567', 'online-mode': 'true',
                  'white-list': 'true', 'enforce-whitelist': 'true', 'max-players': '1',
                  'view-distance': '3', 'simulation-distance': '3', 'max-tick-time': '-1'}
    files['server.properties'] = ''.join(k + '=' + v + '\n' for k, v in properties.items()).encode()
    files['eula.txt'] = b'eula=false\n'
    record = {'schema': 1, 'purpose': 'verified-authored53-native-run', 'namespace': namespace,
              'sourceCommit': source, 'layout': layout, 'root': str(target), 'worldFolder': WORLD,
              'worldId': identity['worldId'], 'inputManifestSHA256': INPUT_SHA,
              'sourceWorldFiles': before, 'sourceWorldManifestSHA256': authored_hash,
              'derivedProfile': profile, 'serverProperties': properties,
              'initialFiles': {k: sha(v) for k, v in files.items()},
              'immutableFiles': {k: sha(v) for k, v in files.items() if k.startswith('config/')},
              'noGrants': True, 'nativeTestsExecuted': False}
    files[MARKER] = encoded(record)
    target.mkdir(parents=True, exist_ok=False)
    for name, data in files.items():
        p = target / name
        p.parent.mkdir(parents=True, exist_ok=True)
        with p.open('xb') as stream:
            stream.write(data)
        if sha(p.read_bytes()) != sha(data):
            raise InputError('Staged readback differs: ' + name)
    for row in inventory['files']:
        if sha(read_regular(root / row['path'])) != row['sha256']:
            raise InputError('Immutable input changed during preparation')
    return record


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--input-root', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--source', required=True)
    p.add_argument('--layout', type=int, required=True)
    p.add_argument('--namespace', default='catancraft_progression53', choices=sorted(NAMESPACES))
    a = p.parse_args()
    print(json.dumps(prepare(a.input_root, a.output, a.source, a.layout, a.namespace), indent=2))
