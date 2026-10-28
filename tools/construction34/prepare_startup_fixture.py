#!/usr/bin/env python3
"""Copy only a CI-generated flat GameTest fixture into a new recovery-test root.

No external paths, authored saves, installed mods, or overwrite option are accepted.
The historical identity is necessary to exercise the unchanged legacy authorizer;
this generated fixture is explicitly NOT the owner's world or approved artwork.
"""
from pathlib import Path
import gzip
import hashlib
import json
import shutil
import struct
import sys

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
from package_authored_demo import level_data

REPO = TOOLS.parent
source_run = REPO / 'build/controlled-catancraft_sector_prices-run'
source = source_run / 'world'
target_run = REPO / 'build/controlled-catancraft_startup34-run'
target = target_run / 'CatanCraft_Season1_R7_Development'
signature = 'CatanCraft synthetic flat fixture v1\n'
name = 'CatanCraft Environment V4 - Visual Polish R2'
assert (source_run / '.catancraft-controlled-fixture-v1').read_text() == signature
props = dict(line.split('=', 1) for line in (source_run / 'server.properties').read_text().splitlines()
             if line and not line.startswith('#') and '=' in line)
assert props['level-type'].replace('\\:', ':') in ('minecraft:flat', 'flat') and props['level-name'] == 'world'
assert source.is_dir() and not target_run.exists(), 'Never overwrite a test world'
assert not any(p.is_symlink() for p in source_run.rglob('*')), 'No linked inputs'
for forbidden in ('catancraft-fresh-demo.json', 'CATANCRAFT_R7_AUTOBUILD_DISPOSABLE_TEST_ONLY.txt'):
    assert not (source / forbidden).exists(), 'Never copy an authorized authored world'
raw_file = (source / 'level.dat').read_bytes()
metadata = level_data(raw_file)
dimensions = metadata['WorldGenSettings']['dimensions']
assert dimensions['minecraft:overworld']['generator']['type'] == 'minecraft:flat'
assert not any(p.name != 'catancraft.dat' for p in (source / 'data').glob('catancraft*')), 'Bootstrap must not contain transaction histories'
old = metadata['LevelName'].encode('utf-8')
prefix = b'\x08\x00\x09LevelName'
needle = prefix + struct.pack('>H', len(old)) + old
raw = gzip.decompress(raw_file)
assert raw.count(needle) == 1, 'Ambiguous level name'
changed = raw.replace(needle, prefix + struct.pack('>H', len(name.encode())) + name.encode())
updated = gzip.compress(changed, mtime=0)
assert level_data(updated)['LevelName'] == name
shutil.copytree(source, target)
(target / 'level.dat').write_bytes(updated)
(target / '.catancraft-startup34-synthetic-v1').write_text('Generated flat startup recovery fixture; not an authored or owner world.\n')
(target_run / '.catancraft-controlled-fixture-v1').write_text(signature)
settings = {
    'level-name': target.name,
    'level-type': 'minecraft:flat',
    'level-seed': '0',
    'generate-structures': 'false',
    'generator-settings': '{"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains","lakes":false,"features":false,"structure_overrides":[]}',
}
(target_run / 'server.properties').write_text(''.join(f'{key}={value}\n' for key, value in settings.items()))
record = {'source': str(source.relative_to(REPO)), 'target': str(target.relative_to(REPO)),
          'sourceLevelSHA256': hashlib.sha256(raw_file).hexdigest(),
          'testLevelSHA256': hashlib.sha256(updated).hexdigest(),
          'changedField': 'Data.LevelName', 'generatedFlatOnly': True,
          'ownerWorldAccessed': False}
(target_run / 'SYNTHETIC_PROVENANCE.json').write_text(json.dumps(record, indent=2) + '\n')
print(json.dumps(record))
