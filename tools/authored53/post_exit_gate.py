#!/usr/bin/env python3
"""Prove the prestige seed remained unpaid/pending AFTER its JVM exited.

Reads only the new labeled native world; writes one evidence gate, never world
data or history. The caller must have awaited the successful seed process.
"""
import argparse
import gzip
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import re
import struct
import uuid

REPO = Path(__file__).resolve().parents[2]


def require(value, message):
    if not value:
        raise ValueError(message)


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def gate(world, source, seed_exit_code):
    require(seed_exit_code == 0 and re.fullmatch('[0-9a-f]{40}', source), 'Successful seed exit and exact source required')
    world = Path(world).absolute()
    require(world == world.resolve() and world.is_relative_to(REPO / 'build')
            and world.parent.name == 'controlled-catancraft_prestige_natural53_restart-run', 'Foreign native world')
    for p in [world, *world.parents]:
        require(not p.is_symlink(), 'Linked native world refused')

    def path(name):
        require(isinstance(name, str) and name and not any(c in name for c in '\\:')
                and not name.startswith('/') and all(p not in ('', '.', '..') for p in name.split('/')), 'Unsafe evidence path')
        p = world / name
        for node in [p, *p.parents]:
            require(not node.is_symlink(), 'Linked evidence refused')
        return p

    def read(name):
        p = path(name)
        require(p.is_file() and p.stat().st_size <= 16 * 1024 * 1024, 'Missing/bounded native evidence: ' + name)
        return p.read_bytes()

    spec = importlib.util.spec_from_file_location('native_nbt53', REPO / 'tools/vehicle47/native-proof.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)

    def decode(raw):
        with gzip.GzipFile(fileobj=io.BytesIO(raw)) as stream:
            data = stream.read(64 * 1024 * 1024 + 1)
        require(len(data) <= 64 * 1024 * 1024, 'Oversized native NBT')
        return module.plain(module.Reader(data).root())

    name = 'data/prestige-natural53-restart/seed-manifest.json'
    raw = read(name)
    manifest = json.loads(raw)
    require(manifest['schema'] == 1 and manifest['purpose'] == 'prestige-natural53-pending-seed-v1'
            and manifest['source'] == source and manifest['world'] == str(world), 'Foreign seed manifest')
    process = manifest['seedProcess']
    require(re.fullmatch('[0-9]+:[0-9]+', process), 'Invalid seed process identity')
    try:
        os.kill(int(process.split(':')[0]), 0)
    except ProcessLookupError:
        pass
    else:
        raise ValueError('Seed process is still alive or its PID is in use')
    require(manifest['seedProofFile'] == 'data/prestige-natural53-restart/seed.dat'
            and manifest['savedDataFile'] == 'data/catancraft.dat', 'Foreign seed evidence files')
    require(sha(read(manifest['seedProofFile'])) == manifest['seedProofSHA256'], 'Seed proof changed')
    saved = read(manifest['savedDataFile'])
    require(sha(saved) == manifest['savedDataSHA256'], 'Seed SavedData changed before process exit')
    ledger = decode(saved)['data']
    territories = {r['id']: r for r in ledger['territories']}
    assets = ledger['placedMapAssets']
    require(len(manifest['purchases']) == 2 and {r['city'] for r in manifest['purchases']} == {'a', 'i'}, 'Missing original pending purchases')
    for row in manifest['purchases']:
        city = row['city']
        require(row['fromLevel'] == (3 if city == 'a' else 4) and row['toLevel'] == row['fromLevel'] + 1, 'Foreign pending tier')
        require(territories[city]['cityLevel'] == row['fromLevel'], 'Purchase completed before restart')
        owner = str(uuid.UUID(bytes=struct.pack('>4i', *territories[city]['ownerNationId'])))
        require(owner == row['ownerNation'] and assets['territory:' + city + ':town_hall'] == row['sourceToken'], 'Pending owner/source changed')
        require(row['receiptKey'] == 'paid_construction:' + row['transaction'] and row['receiptMustBeAbsent'] is True
                and row['receiptKey'] not in assets, 'Seed purchase already paid')
        original_name = 'data/catancraft_th_prestige_' + city + '_' + str(row['toLevel']) + '_v1.dat'
        require(row['journalFile'] == original_name and row['companionFile'] == original_name + '.recovery-v1.dat'
                and row['companionMustBeAbsent'] is True and not path(row['companionFile']).exists(), 'Seed recovery companion already exists')
        original = read(original_name)
        require(sha(original) == row['journalSHA256'], 'Original pending journal changed')
        journal = decode(original)
        require(row['expectedPhase'] == journal['phase'] == 'PENDING' and journal['city'] == city
                and journal['worldRoot'] == str(world) and journal['ownerNation'] == owner, 'Original is not a bound pending purchase')
        require(str(uuid.UUID(bytes=struct.pack('>4i', *journal['transaction']))) == row['transaction'], 'Transaction changed')
    result = {'schema': 1, 'purpose': 'prestige-natural53-post-exit-gate-v1', 'source': source,
              'world': str(world), 'seedProcess': process, 'seedManifestSHA256': sha(raw),
              'seedProofSHA256': manifest['seedProofSHA256'], 'savedDataSHA256': sha(saved),
              'checkedAfterSeedExit': True, 'seedProcessAlive': False,
              'originalsPending': True, 'companionsAbsent': True}
    output = path('data/prestige-natural53-restart/post-exit-gate.json')
    with output.open('x') as stream:
        stream.write(json.dumps(result, indent=2, sort_keys=True) + '\n')
    return result


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--world', type=Path, required=True)
    p.add_argument('--source', required=True)
    p.add_argument('--seed-exit-code', type=int, required=True)
    args = p.parse_args()
    print(json.dumps(gate(args.world, args.source, args.seed_exit_code), indent=2))
