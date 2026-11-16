#!/usr/bin/env python3
"""Run selected, inventoried single-phase regressions serially in NEW worlds.

Restart pairs are deliberately run through run_native.py separately so the
seed report and any post-exit gate can be reviewed before reopening its world.
This tool records execution, not owner acceptance or independent log review.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

REPO = Path(__file__).resolve().parents[2]
PLAN = Path(__file__).with_name('retained_regressions.json')


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--wrapper', type=Path, required=True)
    p.add_argument('--source', required=True)
    p.add_argument('--label', required=True)
    p.add_argument('--namespaces', required=True, help='Comma-separated exact namespace names, in execution order')
    p.add_argument('--pristine-input', type=Path)
    a = p.parse_args()
    if not re.fullmatch('[a-z0-9][a-z0-9_-]{0,63}', a.label) or not re.fullmatch('[0-9a-f]{40}', a.source):
        p.error('Safe new label and exact source required')
    plan = json.loads(PLAN.read_text())
    selected = a.namespaces.split(',')
    if len(set(selected)) != len(selected):
        p.error('Repeated namespaces are not additional coverage')
    specs = {row['namespace']: row for row in plan['namespaces']}
    runs = {row['namespace']: row for row in plan['runs'] if row['phase'] == 'single'}
    for name in selected:
        if name not in runs:
            p.error('Only inventoried single-phase namespaces supported: ' + name)
        for row in specs[name]['sourceFiles']:
            if hashlib.sha256((REPO / row['path']).read_bytes()).hexdigest() != row['sha256']:
                p.error('Refresh reviewed expected-test inventory for changed source: ' + row['path'])
    output = REPO / 'build/evidence53' / a.label
    if output.exists():
        p.error('Evidence directory exists: preserve it and choose a new attempt label')
    output.mkdir(parents=True, exist_ok=False)
    records = []
    for name in selected:
        run = runs[name]
        props = {k: v.replace('${RUN_LABEL}', a.label).replace('${SOURCE_COMMIT}', a.source)
                 for k, v in run['gradleProperties'].items()}
        base = REPO / ('build/validation-' + a.label + '/build' if 'controlledRunLabel' in props else 'build')
        fixture = base / ('controlled-' + name + '-run')
        if fixture.exists():
            raise RuntimeError('Refusing existing native fixture: ' + str(fixture))
        if name == 'catancraft_placement53':
            if a.pristine_input is None:
                raise RuntimeError('Placement fixture requires verified original schematic bundle input')
            raw = (a.pristine_input / 'bootstrap_reference/config/catancraft/schematics_bundle.zip').read_bytes()
            if hashlib.sha256(raw).hexdigest() != '2a7a362047a6672b802c0077cd58db236183c10a69cc69dc928c64859d985edc':
                raise RuntimeError('Placement original bundle hash differs')
            target = fixture / 'config/catancraft/schematics_bundle.zip'
            target.parent.mkdir(parents=True, exist_ok=False)
            target.write_bytes(raw)
        command = [sys.executable, str(Path(__file__).with_name('run_native.py')),
                   '--wrapper', str(a.wrapper), '--source', a.source,
                   '--expected-tests', str(run['expectedTests']), '--output', str(output / name),
                   '--', 'runGameTestServer', '--offline', '--console=plain']
        for init in run['initScripts']:
            command += ['-I', init]
        command += ['-P' + k + '=' + v for k, v in props.items()]
        print('START ' + name + ' expected=' + str(run['expectedTests']), flush=True)
        status = subprocess.run(command, cwd=REPO).returncode
        record_path = output / name / 'process.json'
        records.append({'namespace': name, 'recorderExitCode': status,
                        'processRecord': str(record_path.relative_to(REPO)),
                        'processRecordSHA256': hashlib.sha256(record_path.read_bytes()).hexdigest() if record_path.exists() else None})
        (output / 'batch.json').write_text(json.dumps({'schema': 1, 'source': a.source,
            'planned': selected, 'records': records, 'requiresHumanLogReview': True}, indent=2) + '\n')
        if status:
            return status
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
