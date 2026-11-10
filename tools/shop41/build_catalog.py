#!/usr/bin/env python3
"""Rebuild the inactive tier reference from already-pinned local audit inputs.
No download, live-world read, new price, loot weight or runtime activation.
"""
from pathlib import Path
import hashlib
import json
import re
import zipfile

ROOT = Path(__file__).resolve().parents[2]
AUDIT = ROOT / 'docs/evidence/weapon_catalog_reference39.json'
RAW = ROOT / 'build/weapon-catalog-audit39'
OUT = ROOT / 'src/main/resources/data/catancraft/economy/weapon_tiers41.json'
AUDIT_SHA = '3e6e15cd57bf16da5b1976608ec56f0bec70204535348578171c1a2cf34c8b0c'
IDS_SHA = '6bc571cd770b0ba7b737192c8b5aa402eb876e370a6eacd27e2197375b36607b'
sha = lambda b: hashlib.sha256(b).hexdigest()


def jsonc(text):
    text = re.sub(r'"(?:\\.|[^"\\])*"|//[^\n]*|/\*[\s\S]*?\*/', lambda m: '' if m[0].startswith('/') else m[0], text)
    text = re.sub(r',\s*([}\]])', r'\1', text)
    return json.loads(text)


def build():
    assert sha(AUDIT.read_bytes()) == AUDIT_SHA, 'Reference audit changed'
    audit = json.loads(AUDIT.read_text())
    assert sha(('\n'.join(sorted(audit['proposal']))+'\n').encode()) == IDS_SHA
    for path, digest in audit['tacz_files_sha256'].items():
        local = RAW/'tacz'/path.split('/data/tacz/')[1]
        assert sha(local.read_bytes()) == digest, path
    sbw = RAW/'sbw-sources.jar'
    assert sbw.stat().st_size == audit['sbw_source_bytes']
    assert sha(sbw.read_bytes()) == audit['sbw_source_sha256']
    entries = []
    with zipfile.ZipFile(sbw) as archive:
        for provider, facts in [('tacz', audit['tacz_entries']), ('superbwarfare', audit['sbw_entries'])]:
            for fact in facts:
                native_id = fact['id']; name = native_id.split(':')[1]
                allocation = audit['proposal'][native_id]['proposed_unlock']
                disposition = 'STARTER' if allocation == 'Starter' else 'SHOP' if allocation.startswith('L') else allocation
                level = 0 if disposition == 'STARTER' else int(allocation[1:]) if disposition == 'SHOP' else -1
                flags = {'REFERENCE_ONLY'}
                if provider == 'tacz':
                    index = jsonc((RAW/'tacz/index/guns'/f'{name}.json').read_text())
                    data = jsonc((RAW/'tacz/data/guns'/f"{index['data'].split(':')[1]}.json").read_text())
                    ammo = data['ammo']; modes = data['fire_mode']; default_mode = None
                    category = index['type']; script = data.get('script'); data_id = index['data']
                    assert ammo == fact['ammo'] and modes == fact['fire_mode'] and script == fact['script']
                    if category == 'rpg' or data.get('bullet', {}).get('explosion', {}).get('explode', False): flags.add('EXPLOSIVE_DAMAGE')
                else:
                    data = json.loads(archive.read(f'data/superbwarfare/sbw/guns/{name}.json'))
                    ammo = data['AmmoType']; modes = data.get('AvailableFireModes'); default_mode = data.get('DefaultFireMode')
                    category = data['GunType']; script = None; data_id = native_id
                    assert ammo == fact['AmmoType'] and default_mode == fact['DefaultFireMode']
                    if category in {'DirectLauncher','CurvedLauncher'}: flags.add('EXPLOSIVE_DAMAGE')
                    if ammo == 'FE': flags.add('ENERGY_CONSUMER')
                    if isinstance(ammo, list): flags.add('ALTERNATE_AMMO')
                    if category == 'Special': flags.add('SPECIAL_BEHAVIOR')
                    if native_id == 'superbwarfare:devotion': flags.add('SPECIAL_HANDLING')
                    if isinstance(modes, list) and any(isinstance(m, dict) for m in modes): flags.add('FIRE_MODE_OVERRIDES')
                entries.append({'id': native_id, 'provider': provider, 'category': category,
                    'disposition': disposition, 'proposedLevel': level, 'dataId': data_id,
                    'ammo': ammo, 'fireModes': modes, 'declaredDefaultFireMode': default_mode,
                    'script': script, 'qualificationFlags': sorted(flags),
                    'reason': audit['proposal'][native_id]['reason']})
    entries.sort(key=lambda entry: entry['id'])
    assert len(entries) == 113 and len({entry['id'] for entry in entries}) == 113
    document = {'schema': 1, 'revision': 'weapon_tiers41_reference_candidate_v1',
        'status': 'REFERENCE_ONLY_NOT_RUNTIME_OR_BALANCE_ACCEPTANCE',
        'source': {'auditSHA256': AUDIT_SHA, 'taczCommit': audit['tacz_source_commit'],
            'sbwSHA256': audit['sbw_source_sha256'], 'idSetSHA256': IDS_SHA},
        'excludedInternalIds': audit['excluded_internal_sbw_ids'], 'entries': entries}
    encoded = (json.dumps(document, ensure_ascii=False, allow_nan=False, indent=2)+'\n').encode()
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(encoded)
    print(json.dumps({'file': str(OUT.relative_to(ROOT)), 'entries': len(entries), 'bytes': len(encoded), 'sha256': sha(encoded)}, indent=2))


if __name__ == '__main__':
    build()
