#!/usr/bin/env python3
"""Run the authored wall campaign in separate JVMs using one checkpointed world.

--max-steps 1 stops after the next native exit and sealed world snapshot.
Reinvoke with the same arguments to continue. After an interrupted process,
inspect its native log and pass --retry-interrupted to restore its input.
"""
import argparse
from contextlib import contextmanager
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import tempfile

from prepare import INPUT_SHA, MARKER, WORLD, prepare

REPO = Path(__file__).resolve().parents[2]
NAMESPACE = 'catancraft_sector53_authored'
PASS = 'NATIVE_PROCESS_PASS_REQUIRES_REVIEW'
CHUNK = 1024 * 1024


def require(condition, message):
    if not condition:
        raise ValueError(str(message))


def read(path):
    return json.loads(path.read_bytes())


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def pin(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def encoded(value):
    return (json.dumps(value, indent=2, sort_keys=True) + '\n').encode()


def publish(path, raw):
    """Publish complete immutable bytes. Multiple steps may share content objects."""
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        require(path.read_bytes() == raw, f'Existing evidence differs: {path}')
        return
    with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as stream:
        tmp = Path(stream.name)
        stream.write(raw)
    try:
        os.link(tmp, path)
    finally:
        tmp.unlink()


@contextmanager
def lease(path):
    with path.open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        yield lock.fileno()


def closed(world):
    lock = world / 'session.lock'
    if lock.exists():
        with lock.open('rb+') as f:
            fcntl.lockf(f, fcntl.LOCK_EX | fcntl.LOCK_NB)


def world_files(world):
    require(world.is_dir() and not world.is_symlink(), f'Missing closed world: {world}')
    result = []
    for path in sorted(world.rglob('*')):
        mode = path.lstat().st_mode
        if stat.S_ISDIR(mode):
            continue
        require(stat.S_ISREG(mode), f'Nonregular world entry: {path}')
        result.append(path)
    return result


def blob(objects, key):
    require(re.fullmatch('[0-9a-f]{64}', key), 'Invalid checkpoint object hash')
    return objects / key[:2] / key


def capture(world, objects, dest, binding):
    closed(world)
    files = {}
    chunks = {}
    for path in world_files(world):
        relative = path.relative_to(world).as_posix()
        full = hashlib.sha256()
        parts = []
        with path.open('rb') as stream:
            while data := stream.read(CHUNK):
                key = digest(data)
                publish(blob(objects, key), data)
                full.update(data)
                parts.append(key)
        files[relative] = full.hexdigest()
        chunks[relative] = parts
    publish(dest, encoded(dict(binding, files=files, chunks=chunks)))


def check_world(world, snapshot):
    closed(world)
    current = {p.relative_to(world).as_posix(): pin(p) for p in world_files(world)}
    require(current == snapshot['files'], 'Closed world differs from the verified input snapshot')


def restore(snapshot, world, objects, attempt):
    """Preserve the played world, reconstruct its last sealed input at the bound path."""
    identity = 'catancraft-fresh-demo.json'
    preserved = attempt / 'interrupted-world'
    if world.exists():
        closed(world)
        require(pin(world / identity) == snapshot['files'][identity], 'Refusing a different world identity')
    require(world.exists() or preserved.exists(), 'Missing both active and preserved world')
    if preserved.exists():
        # Recovery may itself have stopped between renames.
        require(pin(preserved / identity) == snapshot['files'][identity], 'Wrong preserved world')
        if world.exists():
            check_world(world, snapshot)
            return
    with tempfile.TemporaryDirectory(prefix='.restore-', dir=world.parent) as tmp:
        copy = Path(tmp) / 'world'
        for name, expected in snapshot['files'].items():
            relative = Path(name)
            require(not relative.is_absolute() and '..' not in relative.parts, 'Unsafe world entry')
            path = copy / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            with path.open('xb') as stream:
                for key in snapshot['chunks'][name]:
                    data = blob(objects, key).read_bytes()
                    require(digest(data) == key, 'Stored checkpoint object changed')
                    stream.write(data)
            require(pin(path) == expected, f'Restored file differs: {name}')
        check_world(copy, snapshot)
        if not preserved.exists():
            # Preserve the interrupted attempt on the world's filesystem if evidence lives elsewhere.
            require(world.stat().st_dev == attempt.stat().st_dev,
                    'Evidence and world must share a filesystem for recoverable world swaps')
            world.rename(preserved)
        copy.rename(world)


def ranges(size):
    require(1 <= size <= 126, 'Step size must be in 1..126')
    points = list(range(0, 126, size)) + [126, 128]
    return [[0, 0]] + [[lo, hi] for lo, hi in zip(points, points[1:])]


def native_proof(attempt, source, properties):
    proc = read(attempt / 'process.json')
    log = (attempt / 'native.log').read_bytes()
    require(proc['source'] == source and proc['expectedTests'] == 1 and proc['status'] == PASS
            and proc['exitCode'] == 0 and proc['sourceUnchanged'] is True
            and proc['reportedCompleted'] == [1] and proc['requiredTestsPassedMessage'] is True
            and proc['nativeLogSHA256'] == digest(log) and set(properties) <= set(proc['command']),
            f'Native exit, command or log hash mismatch: {attempt}')
    require(re.findall(rb'=+ (\d+) GAME TESTS COMPLETE =+', log) == [b'1']
            and re.findall(rb'All (\d+) required tests passed', log) == [b'1'],
            f'Ambiguous native result: {attempt}')


def result_file(world, phase, end):
    name = 'verify.json' if phase == 'verify' else 'seed.json' if end == 128 else f'checkpoint-{end}.json'
    return world / 'data/sector53-authored' / name


def check_report(report, binding, predecessor, lo, hi, phase):
    require(report['worldId'] == binding['worldId'] and report['source'] == binding['source']
            and report['phase'] == phase, 'Foreign world report')
    if phase == 'verify':
        require(report['result'] == 'PASS' and report['seedEvidenceSHA256'] == predecessor
                and report['committedPurchases'] == 128 and report['partialOperationsRecovered'] == 2,
                'Incomplete wall restart report')
        return
    require(report['rangeStart'] == lo and report['rangeEnd'] == hi
            and report['predecessorSHA256'] == predecessor, 'Wall range or predecessor mismatch')
    if hi == 128:
        require(report['result'] == 'SEED_PASS_TWO_PARTIAL_OPERATIONS_REQUIRE_PROCESS_RESTART'
                and report['purchases'] == 128 and report['partialAtSeed'] == 2,
                'Missing full wall seed result')
    else:
        require(report['result'] == 'WALL_SEED_SEGMENT_PASS', 'Incomplete wall segment')
    require(sum(e['kind'] == 'wall_purchase' for e in report['events']) == hi, 'Missing paid purchases')


def check_interruption(attempt, world, phase):
    procfile = attempt / 'process.json'
    if procfile.exists():
        try:
            proc = read(procfile)
        except (ValueError, UnicodeDecodeError):
            proc = {}  # A truncated receipt alone cannot certify an exit.
        require(proc.get('status') in (None, 'RUNNING'), f'Diagnose completed native failure first: {attempt}')
    logpath = attempt / 'native.log'
    raw = logpath.read_bytes() if logpath.exists() else b''
    require(not re.search(rb'OutOfMemoryError|Authored wall qualification failed|There (?:was|were) \d+ required test failures?|BUILD FAILED', raw),
            f'Diagnose native failure before retrying: {attempt}')
    require(not (world / 'data/sector53-authored' / f'failure-{phase}.json').exists(),
            f'Diagnose saved world failure before retrying: {attempt}')


def run(a, lock_fd):
    source = a.source
    build = a.run_root
    evidence = a.evidence
    objects = evidence / 'objects'
    world = build / WORLD
    require(re.fullmatch('[0-9a-f]{40}', source)
            and subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=REPO, text=True).strip() == source,
            'Exact source checkout required')
    require(build.is_relative_to(REPO / 'build') and build != REPO / 'build'
            and not evidence.is_relative_to(build) and not build.is_relative_to(evidence),
            'Choose separate run root inside build and evidence directory')
    evidence.mkdir(parents=True, exist_ok=True)
    staging = evidence / 'staging.json'
    if not staging.exists():
        # prepare writes the original marker last. It can prove a finished staging if
        # the controller disappeared after returning, before publishing staging.json.
        if build.exists():
            record = read(build / MARKER)
            require(all(pin(build / name) == value for name, value in record['initialFiles'].items()),
                    'Incomplete staging; preserve it and choose a new run root')
        else:
            record = prepare(a.inputs, build, source, 0, NAMESPACE)
        publish(staging, encoded(record))
    record = read(staging)
    require(read(build / MARKER) == record and record['root'] == str(build)
            and record['sourceCommit'] == source and record['inputManifestSHA256'] == INPUT_SHA
            and record['namespace'] == NAMESPACE and record['worldFolder'] == WORLD and record['layout'] == 0,
            'Foreign or changed staged world')
    binding = dict(source=source, worldId=record['worldId'], root=str(build))
    plan = ranges(a.step_size)
    publish(evidence / 'plan.json', encoded(dict(binding, ranges=plan)))
    previous = evidence / 'pristine/snapshot.json'
    if not previous.exists():
        expected = {k[len(WORLD)+1:]: v for k, v in record['initialFiles'].items() if k.startswith(WORLD + '/')}
        check_world(world, {'files': expected})
        capture(world, objects, previous, dict(binding, index=-1, reportSHA256=None, processSHA256=None,
                                              nativeLogSHA256=None, inputSnapshotSHA256=None))
    predecessor = ''
    previous_end = 0
    new_steps = 0
    for index, (lo, hi) in enumerate(plan + [[None, None]]):
        phase = 'verify' if hi is None else 'seed'
        step = evidence / 'steps' / f'{index:04d}'
        step.mkdir(parents=True, exist_ok=True)
        completed = step / 'complete.json'
        input_pin = pin(previous)
        snap = read(previous)
        require(all(snap[k] == v for k, v in binding.items()) and snap['index'] == index-1
                and snap['reportSHA256'] == (predecessor or None), 'Foreign input snapshot')
        properties = ['-PgameTestNamespace='+NAMESPACE, '-PauthoredSource='+source,
                      '-PauthoredRunRoot='+str(build), '-PauthoredPhase='+phase,
                      '-PtaczAuditRoot='+str(a.tacz), '-PsbwAuditRoot='+str(a.sbw)]
        if phase == 'seed':
            properties += [f'-Pwall53RangeStart={lo}', f'-Pwall53RangeEnd={hi}']
            if predecessor:
                properties.append('-Pwall53Predecessor='+predecessor)
        if completed.exists():
            attname = read(completed)['attempt']
            require(re.fullmatch(r'attempt-\d{4}', attname), 'Invalid saved attempt name')
            attempt = step / attname
        else:
            if a.max_steps and new_steps >= a.max_steps:
                return
            attempts = sorted(step.glob('attempt-[0-9][0-9][0-9][0-9]'))
            attempt = attempts[-1] if attempts else None
            if attempt is not None:
                procfile = attempt / 'process.json'
                try:
                    success = procfile.exists() and read(procfile).get('status') == PASS
                except (ValueError, UnicodeDecodeError):
                    success = False
                if not success:
                    require(a.retry_interrupted, f'Inspect interrupted attempt, then use --retry-interrupted: {attempt}')
                    check_interruption(attempt, world, phase)
                    restore(snap, world, objects, attempt)
                    attempt = None
            if attempt is None:
                check_world(world, snap)
                attempt = step / f'attempt-{len(attempts)+1:04d}'
                arguments = [sys.executable, str(REPO / 'tools/authored53/run_native.py'),
                             '--source', source, '--expected-tests', '1', '--output', str(attempt),
                             '--wrapper', str(a.wrapper), '--lease-fd', str(lock_fd), '--lease-file', str(a.lease),
                             '--', 'runGameTestServer', '--console=plain',
                             *properties, '-I', str(a.heap_init), '-I', 'tools/authored53/validate.gradle',
                             '-I', 'tools/authored53/combined.gradle']
                print(f'Native step {index+1}/{len(plan)+1}: {phase} {lo}..{hi}', flush=True)
                subprocess.run(arguments, cwd=REPO, check=True, pass_fds=(lock_fd,),
                               env=dict(os.environ, CC_QUAL_SOURCE=source))
            new_steps += 1
        native_proof(attempt, source, properties)
        proc = read(attempt / 'process.json')
        require(previous_end <= proc['startedUnix'] <= proc['endedUnix'], 'Wall steps overlap')
        previous_end = proc['endedUnix']
        output = attempt / 'world-report.json'
        if not completed.exists():
            closed(world)
            publish(output, result_file(world, phase, hi).read_bytes())
        check_report(read(output), binding, predecessor, lo, hi, phase)
        pins = dict(reportSHA256=pin(output), processSHA256=pin(attempt/'process.json'),
                    nativeLogSHA256=pin(attempt/'native.log'), inputSnapshotSHA256=input_pin)
        ss = step / 'snapshot.json'
        if not completed.exists():
            capture(world, objects, ss, dict(binding, index=index, **pins))
        snapshot = read(ss)
        require(all(snapshot[k] == v for k, v in dict(binding, index=index, **pins).items()),
                'Closed snapshot binding differs')
        relative_report = result_file(world, phase, hi).relative_to(world).as_posix()
        require(snapshot['files'][relative_report] == pins['reportSHA256'], 'Snapshot excludes original report')
        row = dict(binding, index=index, phase=phase, rangeStart=lo, rangeEnd=hi, attempt=attempt.name,
                   predecessorSHA256=predecessor, snapshotSHA256=pin(ss), **pins)
        publish(completed, encoded(row))
        predecessor = pins['reportSHA256']
        previous = ss
        if phase == 'verify' or hi == 128:
            for name in ('native.log', 'process.json', 'world-report.json'):
                publish(evidence / phase / name, (attempt / name).read_bytes())
        if new_steps:
            print(f'Closed checkpoint: {completed}', flush=True)
    subprocess.run([sys.executable, str(REPO / 'tools/authored53/verify_wall_segments.py'),
                    '--source', source, str(evidence)], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', required=True)
    for key in ('run-root', 'evidence', 'inputs', 'wrapper', 'lease', 'heap-init', 'tacz', 'sbw'):
        parser.add_argument('--' + key, type=Path, required=True)
    parser.add_argument('--step-size', type=int, default=2)
    parser.add_argument('--max-steps', type=int)
    parser.add_argument('--retry-interrupted', action='store_true')
    args = parser.parse_args()
    for key in ('run_root', 'evidence', 'inputs', 'wrapper', 'lease', 'heap_init', 'tacz', 'sbw'):
        path = getattr(args, key).absolute()
        require(path == path.resolve(), f'Linked or noncanonical {key} path')
        setattr(args, key, path)
    require(args.max_steps is None or args.max_steps > 0, '--max-steps must be positive')
    with lease(args.lease) as fd:
        run(args, fd)


if __name__ == '__main__':
    main()
