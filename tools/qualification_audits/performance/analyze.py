"""Summarize paired completed CatanCraft native logs without starting Minecraft."""
import argparse
import hashlib
import json
import math
import re
from pathlib import Path
from statistics import median


def describe(values):
    values = sorted(values)
    return dict(count=len(values), minimum=values[0], median=median(values),
                p95_nearest_rank=values[math.ceil(len(values) * .95) - 1], maximum=values[-1])


def clock(line):
    h, m, s = map(int, re.match(r'^\[(\d\d):(\d\d):(\d\d)\]', line).groups())
    return h * 3600 + m * 60 + s


def phase_data(root, layout, phase, source, stage):
    folder = root / f'layout{layout}-{phase}'
    record = json.loads((folder / 'process.json').read_bytes())
    raw = (folder / 'native.log').read_bytes()
    sha = hashlib.sha256(raw).hexdigest()
    if not (record['source'] == source and record['sourceUnchanged'] and record['exitCode'] == 0
            and record['reportedCompleted'] == [1] and record['nativeLogSHA256'] == sha
            and record['status'] == 'NATIVE_PROCESS_PASS_REQUIRES_REVIEW'):
        raise ValueError(f'Native record mismatch for {folder}')
    world = Path(stage['root']) / stage['worldFolder'] / 'data' / f'progression53-layout-{layout}'
    saved = json.loads((world / f'{phase}.json').read_bytes())
    expected = ('PASS',80) if phase == 'seed' else ('RESTART_PASS',16)
    if ((saved['result'],len(saved['checkpoints'])) != expected or saved['worldId'] != stage['worldId']
            or saved['layout'] != layout or saved['source'] != source or saved['phase'] != phase):
        raise ValueError(f'Wrong saved world or phase for {folder}')
    text = raw.decode()
    lines = text.splitlines()
    spawn_line, = [l for l in lines if 'LoggerChunkProgressListener]: Time elapsed: ' in l]
    spawn_ms = int(re.search(r'Time elapsed: (\d+) ms', spawn_line)[1])
    warnings = []
    for line in lines:
        match = re.search(r"Can't keep up!.*?Running (\d+)ms or (\d+) ticks behind",line)
        if match:
            warnings.append(dict(ms_behind=int(match[1]),ticks_behind=int(match[2]),
                                 seconds_after_spawn_log=(clock(line)-clock(spawn_line)) % 86400))
    prestige = {}
    for level in (4,5):
        entries = re.findall(rf'TH prestige City ([a-p]) level {level} COMMITTED (\d+) writes',text)
        if phase == 'seed' and sorted(city for city,_ in entries) != list('abcdefghijklmnop'):
            raise ValueError(f'Incomplete prestige coverage in {folder}')
        prestige[str(level)] = [int(writes) for _,writes in entries]
    return dict(layout=layout,phase=phase,log_sha256=sha,process_seconds=record['elapsedSeconds'],
                spawn_preparation_ms=spawn_ms,warnings=warnings,prestige_writes=prestige,
                full_save_messages=text.count('ThreadedAnvilChunkStorage: All dimensions are saved'))


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('receipts',type=Path)
    parser.add_argument('layouts',type=int,nargs='+')
    a=parser.parse_args()
    source=(a.receipts/'source.txt').read_text().strip()
    if len(set(a.layouts)) != len(a.layouts):
        parser.error('duplicate layout')
    rows=[]
    for layout in sorted(a.layouts):
        stage=json.loads((a.receipts/f'layout{layout}-staging.json').read_bytes())
        if stage['sourceCommit'] != source or stage['layout'] != layout:
            raise ValueError(f'Wrong stage for {layout}')
        rows.extend(phase_data(a.receipts,layout,p,source,stage) for p in ('seed','verify'))
    groups={}
    for phase in ('seed','verify'):
        group=[r for r in rows if r['phase']==phase]
        groups[phase] = {key:describe([r[key] for r in group]) for key in
                         ('process_seconds','spawn_preparation_ms','full_save_messages')}
    warnings=[w for r in rows for w in r['warnings']]
    report=dict(source=source,layouts=sorted(a.layouts),runs=rows,by_phase=groups,
                startup_backlog_ms=describe([w['ms_behind'] for w in warnings]) if warnings else None,
                warning_delay_after_spawn_seconds=describe([w['seconds_after_spawn_log'] for w in warnings]) if warnings else None,
                prestige_writes_per_purchase={str(n):describe([v for r in rows for v in r['prestige_writes'][str(n)]]) for n in (4,5)})
    print(json.dumps(report,indent=2))


if __name__=='__main__':
    main()
