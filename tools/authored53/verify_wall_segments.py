"""Independently verify the full chain of closed native wall-test segments.

This verifier can run on just the exported evidence. Closed-world catalogs bind
continuity and restoration bytes. They do not replace the native log and the
Java report made by each process. The final restart is always a different JVM.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import re

INPUT_SHA = 'e7ecd5d0f0ac5c1457be5c234d2e5e08c9239007787b20e0bdc45b3e2b8d862d'
WORLD = 'CatanCraft_Season1_R7_Development'
WALL = 'catancraft_sector53_authored'
PASS = 'NATIVE_PROCESS_PASS_REQUIRES_REVIEW'
SHA = re.compile(r'[0-9a-f]{64}')
CITIES = 'abef dchg mijn plko'.replace(' ', '')
SIDE_ORDERS = ('neswnesw', 'eneswnws', 'wswnesen', 'swnesenw')
SIDES = dict(n='north', e='east', s='south', w='west')


def require(value, reason):
    if not value:
        raise ValueError(reason)


def read(path):
    return json.loads(path.read_bytes())


def digest(path):
    with path.open('rb') as f:
        return hashlib.file_digest(f, 'sha256').hexdigest()


def validate_plan(ranges):
    require(isinstance(ranges, list) and len(ranges) >= 2, 'Missing segment boundaries')
    prior = 0
    for index, pair in enumerate(ranges):
        require(isinstance(pair, list) and len(pair) == 2 and all(type(n) is int for n in pair),
                'Non-integer or missing boundaries')
        start, end = pair
        require(start == prior and (start < end or (index == 0 and pair == [0, 0])),
                'Gap, overlap or empty intermediate segment')
        require(0 <= start <= 126 and (end <= 126 or end == 128), 'Partial D and M operations must share the final JVM')
        prior = end
    require(prior == 128, 'Wall purchase coverage stops before 128')


def check_native(record, path, source, root, phase, bounds, predecessor, previous_end):
    r = record
    require(r['source'] == source and r['sourceUnchanged'] is True and r['status'] == PASS and
            r['exitCode'] == 0 and r['expectedTests'] == 1 and r['reportedCompleted'] == [1] and
            r['requiredTestsPassedMessage'] is True and r['nativeLogSHA256'] == digest(path),
            f'Failed, foreign or altered process: {path}')
    start, end = r['startedUnix'], r['endedUnix']
    require(all(type(t) in (float, int) and math.isfinite(t) for t in (start, end)) and
            previous_end <= start < end, f'Overlapping or incomplete native time interval: {path}')
    args = r['command']
    require('runGameTestServer' in args, f'Native entry point differs: {path}')
    init_files = [v for k, v in zip(args, args[1:]) if k == '-I']
    require(all(i in init_files for i in ('tools/authored53/validate.gradle', 'tools/authored53/combined.gradle')),
            f'Missing authored or pinned combined runtime: {path}')
    props = {}
    for arg in args:
        if arg.startswith('-P'):
            key, sep, val = arg[2:].partition('=')
            require(sep and key not in props, f'Ambiguous property: {arg}')
            props[key] = val
    require(props['gameTestNamespace'] == WALL and props['authoredSource'] == source and
            props['authoredRunRoot'] == root and props['authoredPhase'] == phase, 'Wrong native fixture')
    expected = {}
    if bounds is not None:
        expected = {'wall53RangeStart': str(bounds[0]), 'wall53RangeEnd': str(bounds[1])}
        if predecessor:
            expected['wall53Predecessor'] = predecessor
    require({k: v for k, v in props.items() if k.startswith('wall53')} == expected,
            f'Wrong native segment or checkpoint: {path}')
    raw = path.read_bytes()
    require(re.findall(rb'=+ (\d+) GAME TESTS COMPLETE =+', raw) == [b'1'] and
            re.findall(rb'All (\d+) required tests passed', raw) == [b'1'] and
            b"Running test batch 'sector53_authored:1' (1 tests)" in raw, f'Wrong native result: {path}')
    return end


def check_events(events, end):
    """Independently reconstruct purchase order, Hall milestones and partials."""
    scheduled, delayed = [], []
    for city in CITIES:
        order = SIDE_ORDERS[(ord(city) - ord('a')) % 4]
        for pos, side in enumerate(order):
            partial = city in 'dm' and pos == 7
            (delayed if partial else scheduled).append((city, SIDES[side], partial, (1 if pos < 2 else 4 if pos < 4 else 5)))
    scheduled.extend(delayed)
    bought, ids, operations, tiers, levels, founded, grants = 0, set(), set(), {}, {}, [], set()
    for row in events:
        kind = row['kind']
        if kind == 'EXPLICIT_DEVELOPER_FIXTURE_GRANT':
            require(row['nation'] not in grants and not founded and row['money'] == 1_000_000 and
                    row['eachResource'] == 100_000, 'Fixture grant differs')
            grants.add(row['nation'])
        elif kind == 'normal_founding_and_automatic_base':
            city = row['city']
            require(len(grants) == 4 and not tiers and bought == 0 and
                    row['route'] == ('nation_start' if city in 'admp' else 'nation_claim'),
                    'Normal founding evidence differs')
            founded.append(city)
        elif kind == 'paid_hall_milestone':
            city = row['city']
            tier = tiers.get(city, 1)
            require(bought < end and city == scheduled[bought][0] and row['from'] == tier and row['to'] == tier + 1
                    and tier < 5 and row['exactDebit'] is True and row['earlierWallAssemblyVerified'] is True
                    and row['unchangedWallJournals'] >= 2, 'Hall progression or earlier history differs')
            tiers[city] = tier + 1
        elif kind == 'wall_purchase':
            require(''.join(founded) == CITIES and bought < end, 'Missing founders or extra purchase')
            city, side, partial, expected_hall = scheduled[bought]
            old = levels.get((city, side), 0)
            require((row['city'], row['side'], row['partialAtSeed']) == (city, side, partial) and
                    (row['from'], row['to']) == (old, old + 1) and row['townHallLevel'] == tiers.get(city, 1) == expected_hall
                    and row['groupSchema'] == (1 if city == 'a' else 2) and row['money'] > 0,
                    'Wall order, tier, charge or partial isolation differs')
            written, total = row['observedWritten'], row['exactChanges']
            require(0 < written <= total and partial == (written < total), 'Unexpected partial scope')
            require(row['id'] not in ids and row['operation'] not in operations
                    and re.fullmatch(rf'catancraft_sector_{city}_{side}_[0-9a-f-]{{36}}_v1[.]dat', row['operation'])
                    and SHA.fullmatch(row['immutableHistorySHA256']) and SHA.fullmatch(row['journalSHA256']),
                    'Missing/duplicated transaction identity or history')
            ids.add(row['id'])
            operations.add(row['operation'])
            levels[city, side] = old + 1
            bought += 1
        else:
            raise ValueError('Unknown wall event: ' + kind)
    require(''.join(founded) == CITIES and bought == end and len(grants) == 4, 'Incomplete founding or purchase scope')
    if end == 128:
        require(len(levels) == 64 and set(levels.values()) == {2} and
                len(tiers) == 16 and set(tiers.values()) == {5}, 'Incomplete final wall/Hall coverage')


def verify(evidence, source):
    require(re.fullmatch(r'[0-9a-f]{40}', source), 'Full gameplay source required')
    members = []

    def member(name):
        p = evidence / name
        require(p.is_file() and not p.is_symlink() and p.resolve() == evidence.resolve() / name,
                'Missing or linked evidence: ' + name)
        members.append(name)
        return p

    stage = read(member('staging.json'))
    require(stage['sourceCommit'] == source and stage['namespace'] == WALL and stage['worldFolder'] == WORLD
            and stage['layout'] == 0 and stage['inputManifestSHA256'] == INPUT_SHA, 'Foreign staging')
    root, world = stage['root'], stage['worldId']
    plan = read(member('plan.json'))
    require((plan['source'], plan['worldId'], plan['root']) == (source, world, root), 'Foreign segment plan')
    ranges = plan['ranges']
    validate_plan(ranges)

    def snapshot(path, index, pins):
        data = read(path)
        require((data['source'], data['root'], data['worldId'], data['index']) == (source, root, world, index)
                and all(data[k] == v for k, v in pins.items()), f'Foreign snapshot or broken provenance: {path}')
        files = data['files']
        require('level.dat' in files and files['catancraft-fresh-demo.json'] ==
                stage['initialFiles'][WORLD + '/catancraft-fresh-demo.json'] and all(
                    (not Path(name).is_absolute() and '..' not in Path(name).parts and SHA.fullmatch(pin))
                    for name, pin in files.items()), 'Invalid closed-world file catalog')
        chunks = data['chunks']
        require(chunks.keys() == files.keys() and all(isinstance(v, list) and all(SHA.fullmatch(h) for h in v)
                                                     for v in chunks.values()), 'Invalid closed-world chunk catalog')
        return files

    pins = {k: None for k in ('processSHA256', 'nativeLogSHA256', 'reportSHA256', 'inputSnapshotSHA256')}
    first = member('pristine/snapshot.json')
    original = snapshot(first, -1, pins)
    pristine = {k[len(WORLD) + 1:]: v for k, v in stage['initialFiles'].items() if k.startswith(WORLD + '/')}
    require(original == pristine, 'Initial snapshot differs from staged pristine world')
    previous_snapshot = digest(first)
    previous_report, previous_end, prior_events, prior_halls = None, 0, [], {}
    seed_digest = None
    for index, bounds in enumerate([*ranges, None]):
        phase = 'verify' if bounds is None else 'seed'
        directory = f'steps/{index:04}'
        receipt = read(member(directory + '/complete.json'))
        attempt = receipt['attempt']
        require(re.fullmatch(r'attempt-\d{4,}', attempt), 'Unexpected attempt directory')
        base = f'{directory}/{attempt}/'
        process = member(base + 'process.json')
        log = member(base + 'native.log')
        report = member(base + 'world-report.json')
        cat = member(directory + '/snapshot.json')
        begin, end = bounds if bounds is not None else (None, None)
        require((receipt['source'], receipt['worldId'], receipt['index'], receipt['phase'],
                 receipt['rangeStart'], receipt['rangeEnd']) == (source, world, index, phase, begin, end),
                f'Step {index} differs from plan')
        require((receipt['predecessorSHA256'] or None) == previous_report and receipt['snapshotSHA256'] == digest(cat),
                f'Step {index} predecessor report or snapshot hash differs')
        pins = {'processSHA256': digest(process), 'nativeLogSHA256': digest(log),
                'reportSHA256': digest(report), 'inputSnapshotSHA256': previous_snapshot}
        require(all(receipt[k] == v for k, v in pins.items()), f'Step {index} evidence or input snapshot differs')
        files = snapshot(cat, index, pins)
        previous_end = check_native(read(process), log, source, root, phase, bounds,
                                    previous_report if bounds is not None else None, previous_end)
        row = read(report)
        require((row['source'], row['worldId'], row['phase']) == (source, world, phase) and
                row['realAuthoredCoordinates'] is True and row['fundedDeveloperFixture'] is True and
                row['manualTargetBlockStamping'] is False, f'Foreign or synthetic wall report at step {index}')
        if bounds is not None:
            require((row['rangeStart'], row['rangeEnd'], row['predecessorSHA256']) == (begin, end, previous_report or ''),
                    'Native report disagrees with ordered purchase range')
            events, halls = row['events'], row['hallHistorySHA256']
            require(events[:len(prior_events)] == prior_events and all(halls.get(k) == v for k, v in prior_halls.items()),
                    'Earlier events or Hall histories overwritten between JVMs')
            check_events(events, end)
            prior_events, prior_halls = events, halls
            require(all(files.get('data/' + k) == pin for k, pin in halls.items()), 'Hall journal catalog mismatch')
            require(all(files.get('data/' + e['operation']) == e['journalSHA256'] for e in events
                        if e['kind'] == 'wall_purchase'), 'Wall journal catalog mismatch')
            if end < 128:
                require(row['result'] == 'WALL_SEED_SEGMENT_PASS', 'Incomplete intermediate report')
                report_name, ledger = f'checkpoint-{end}.json', f'checkpoint-{end}-ledger.nbt'
                ledger_hash = row['ledgerSHA256']
            else:
                require((row['result'], row['cities'], row['sectorPlacements'], row['purchases'],
                         row['committedAtSeed'], row['partialAtSeed'], row['paidHallUpgrades']) ==
                        ('SEED_PASS_TWO_PARTIAL_OPERATIONS_REQUIRE_PROCESS_RESTART', 16, 64, 128, 126, 2, 64),
                        'Final wall seed incomplete')
                report_name, ledger, ledger_hash = 'seed.json', 'seed-ledger.nbt', row['seedLedgerSHA256']
                seed_digest = pins['reportSHA256']
            require(files.get('data/sector53-authored/' + ledger) == ledger_hash and SHA.fullmatch(ledger_hash),
                    'Checkpoint ledger missing from closed-world snapshot')
        else:
            report_name = 'verify.json'
            require((row['result'], row['cities'], row['sectorPlacements'], row['committedPurchases'],
                     row['partialOperationsRecovered'], row['unchangedCommittedJournals'], row['paidHallUpgrades'],
                     row['seedEvidenceSHA256']) == ('PASS', 16, 64, 128, 2, 126, 64, seed_digest)
                    and row['allCityHall4And5WallPurchasesVerified'] is True
                    and row['earlierWallsPreservedAcrossHallUpgrades'] is True, 'Incomplete independent restart')
            require(files.get('data/sector53-authored/seed.json') == seed_digest and
                    all(files.get('data/' + k) == pin for k, pin in prior_halls.items()) and
                    all(files.get('data/' + e['operation']) == e['journalSHA256'] for e in prior_events
                        if e['kind'] == 'wall_purchase' and not e['partialAtSeed']), 'Restart changed closed histories')
        require(files.get('data/sector53-authored/' + report_name) == pins['reportSHA256'],
                'World report and snapshot catalog disagree')
        if end == 128 or phase == 'verify':
            for name, expected in (('world-report.json', pins['reportSHA256']), ('native.log', pins['nativeLogSHA256']),
                                   ('process.json', pins['processSHA256'])):
                require(digest(member(phase + '/' + name)) == expected, 'Final view differs from chain')
        previous_report, previous_snapshot = pins['reportSHA256'], receipt['snapshotSHA256']
    return ({'source': source, 'worldId': world, 'seedSegments': len(ranges), 'nativeProcesses': len(ranges) + 1,
             'purchases': 128, 'cities': 16, 'recoveredOnRestart': 2, 'closingSnapshotSHA256': previous_snapshot,
             'result': 'COMPLETE_PENDING_NATIVE_LOG_REVIEW'}, members)


if __name__ == '__main__':
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument('evidence', type=Path)
    cli.add_argument('--source', required=True)
    cli.add_argument('--file-list', action='store_true', help='Print the evidence paths used in verification')
    parsed = cli.parse_args()
    try:
        summary, files = verify(parsed.evidence, parsed.source)
        print(json.dumps(files if parsed.file_list else summary, indent=2))
    except (ValueError, KeyError, OSError, TypeError) as error:
        cli.exit(1, f'Wall segment evidence rejected: {error}\n')
