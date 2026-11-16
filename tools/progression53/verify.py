#!/usr/bin/env python3
"""Check complete nine-layout native campaign evidence; never execute or mint passes."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re

CITIES = tuple(chr(ord('a') + i) for i in range(16))
PLOTS = ('plot_1', 'plot_3', 'plot_4')
INDUSTRIES = ('steel_mill', 'concrete_plant', 'textile_mill', 'refinery',
              'machine_shop', 'electronics_factory', 'chemical_plant',
              'weapons_factory', 'vehicle_factory')


def require(value: bool, message: str) -> None:
    if not value:
        raise ValueError(message)


def read(path: Path) -> dict:
    require(path.is_file() and not path.is_symlink() and path.stat().st_size <= 32_000_000,
            f'Missing/bounded regular evidence required: {path}')
    value = json.loads(path.read_text())
    require(type(value) is dict, 'Report must be an object')
    return value


def verify(paths: list[Path], source: str) -> dict:
    require(bool(re.fullmatch('[0-9a-f]{40}', source)), 'Exact source commit required')
    require(len(paths) == 18 and len({p.resolve() for p in paths}) == 18,
            'Exactly 18 distinct seed/verify reports required')
    reports: dict[tuple[int, str], tuple[Path, dict]] = {}
    worlds: set[str] = set()
    map_hash: str | None = None
    world_hash: str | None = None
    coverage: set[str] = set()
    cycles = 0
    ticks = 0
    for path in paths:
        row = read(path)
        require(row.get('schema') == 1 and row.get('purpose') == 'verified-clean-r7-progression53'
                and row.get('source') == source, f'Foreign schema/source: {path}')
        layout, phase = row.get('layout'), row.get('phase')
        require(type(layout) is int and 0 <= layout < 9 and phase in ('seed', 'verify'),
                f'Unknown layout/phase: {path}')
        key = (layout, phase)
        require(key not in reports, 'Repeated layout/phase is not additional coverage')
        reports[key] = (path, row)
        require(row.get('result') == ('PASS' if phase == 'seed' else 'RESTART_PASS'),
                f'Failed/incomplete execution retained: {path}')
        require(row.get('artificialMoneyOrMaterialGrants') == 0
                and row.get('directPaidLevelOrBuildingSeeding') is False
                and row.get('manualTargetBlockWrites') == 0
                and row.get('connectedClientAcceptance') is False
                and row.get('realTimePacingAcceptance') is False,
                'Campaign provenance/scope differs')
        require(isinstance(row.get('worldId'), str) and len(row['worldId']) == 36,
                'Missing bound fresh world identity')
        for field in ('mapSHA256', 'authoredWorldSHA256'):
            require(isinstance(row.get(field), str)
                    and bool(re.fullmatch('[0-9a-f]{64}', row[field])), 'Missing source pin')
        if map_hash is None:
            map_hash, world_hash = row['mapSHA256'], row['authoredWorldSHA256']
        require((row['mapSHA256'], row['authoredWorldSHA256']) == (map_hash, world_hash),
                'Layouts do not share one reviewed pristine source')
        expected = {(city, tier) for city in CITIES
                    for tier in (range(1, 6) if phase == 'seed' else [5])}
        observed: set[tuple[str, int]] = set()
        for checkpoint in row.get('checkpoints', []):
            checkpoint_key = (checkpoint.get('city'), checkpoint.get('townHallLevel'))
            require(checkpoint_key in expected and checkpoint_key not in observed,
                    'Foreign or repeated checkpoint')
            observed.add(checkpoint_key)
            city, tier = checkpoint_key
            require(checkpoint.get('producerLevel') == tier
                    and checkpoint.get('rotation') == (0 if city <= 'h' else 180),
                    'Checkpoint actual city/tier transform differs')
            for flag in ('managementBookPreserved', 'automaticPublicAndHousesReady',
                         'immutableEarlierHistories', 'savedDataRoundtrip',
                         'earlierEligibleFacilitiesRechecked'):
                require(checkpoint.get(flag) is True, f'Missing cumulative check: {flag}')
            require(type(checkpoint.get('checkedPhysicalCells')) is int
                    and checkpoint['checkedPhysicalCells'] > 0
                    and type(checkpoint.get('unpaidWallSourceCellsChecked')) is int
                    and checkpoint['unpaidWallSourceCellsChecked'] > 0
                    and checkpoint.get('compassWallQuotes') == 4,
                    'Physical and all-compass wall evidence missing')
        require(observed == expected, 'Missing earlier checkpoint/city')
        require(isinstance(row.get('completedHistorySHA256'), dict)
                and row['completedHistorySHA256'], 'Retained original histories missing')
        if phase == 'seed':
            require(row['worldId'] not in worlds, 'A prior world was reused for another fresh layout')
            worlds.add(row['worldId'])
            purchased = row.get('factoryPurchasedLevels', {})
            expected_sites = {f'{city}:{plot}:{INDUSTRIES[((ord(city)-ord("a"))*3+slot+layout)%9]}'
                              for city in CITIES for slot, plot in enumerate(PLOTS)}
            require(set(purchased) == expected_sites and
                    all(value == [1, 2, 3, 4, 5] for value in purchased.values()),
                    'Missing physical industry purchase/upgrade level')
            require(set(row.get('factorySiteCumulativeChecks', {})) == expected_sites,
                    'Final layout cumulative facility inventory differs')
            coverage.update(expected_sites)
            require(row.get('actions') and all(action.get('exactDebit') is True
                    for action in row['actions']), 'Missing real command exact-debit evidence')
            require(type(row.get('acceleratedScheduledCycles')) is int
                    and row['acceleratedScheduledCycles'] > 0
                    and row.get('simulatedActiveMinutes') == row['acceleratedScheduledCycles'] * 15,
                    'Accelerated economy clock provenance differs')
            cycles += row['acceleratedScheduledCycles']
        require(type(row.get('actualNativeTicks')) is int and row['actualNativeTicks'] > 0,
                'Actual native execution duration missing')
        ticks += row['actualNativeTicks']
    for layout in range(9):
        seed, restart = reports[(layout, 'seed')][1], reports[(layout, 'verify')][1]
        require(seed['worldId'] == restart['worldId'], 'Restart is not the original run')
        require(seed['completedHistorySHA256'] == restart['completedHistorySHA256'],
                'Original paid histories changed across process restart')
    expected_all = {f'{city}:{plot}:{industry}' for city in CITIES
                    for plot in PLOTS for industry in INDUSTRIES}
    require(coverage == expected_all, 'All-city/type/slot coverage incomplete')
    return {'schema': 1, 'result': 'NATIVE_REPORTS_COMPLETE_REQUIRES_LOG_REVIEW',
            'source': source, 'freshLayouts': 9, 'distinctRunIdentities': len(worlds),
            'seedCheckpoints': 720, 'restartCityChecks': 144,
            'industrySiteCombinations': len(coverage), 'industrySiteTierChecks': len(coverage) * 5,
            'acceleratedScheduledCycles': cycles, 'actualNativeTicks': ticks,
            'requiresOriginalNativeLogAndExitReview': True, 'connectedClientAcceptance': False,
            'realTimePacingAcceptance': False,
            'reports': [{'path': str(path), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}
                        for path in paths]}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', required=True)
    parser.add_argument('reports', type=Path, nargs='+')
    args = parser.parse_args()
    try:
        print(json.dumps(verify(args.reports, args.source), indent=2))
    except (OSError, ValueError, KeyError, TypeError) as error:
        parser.exit(1, f'Progression53 evidence refused: {error}\n')


if __name__ == '__main__':
    main()
