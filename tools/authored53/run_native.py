#!/usr/bin/env python3
"""Record one native Gradle process; never reset worlds or infer a pass from build success.

The caller supplies the pinned local Gradle wrapper and all fixture arguments.
Seed and verification are separate invocations. A new evidence directory is
required even when verification deliberately reopens the same isolated world.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time

REPO = Path(__file__).resolve().parents[2]
BOUND = ['src', 'build.gradle', 'tools/authored53', 'tools/server53/world_profile.py']


def git(*args):
    return subprocess.check_output(['git', *args], cwd=REPO).decode().strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--wrapper', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--source', required=True)
    parser.add_argument('--lease-fd', type=int, help='Already-held shared lease inherited from the segment coordinator')
    parser.add_argument('--lease-file', type=Path, help='Path of the same shared lease')
    parser.add_argument('--expected-tests', type=int, required=True)
    parser.add_argument('gradle', nargs=argparse.REMAINDER)
    args = parser.parse_args()
    inherited = ()
    if args.lease_fd is not None or args.lease_file is not None:
        import fcntl
        if args.lease_fd is None or args.lease_fd < 3 or args.lease_file is None:
            parser.error('Both the campaign lease and its inherited descriptor are required')
        if not os.path.samestat(os.fstat(args.lease_fd), args.lease_file.stat()):
            parser.error('Inherited descriptor is for a different lease')
        fcntl.flock(args.lease_fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        inherited = (args.lease_fd,)
    if not re.fullmatch('[0-9a-f]{40}', args.source) or args.expected_tests < 1:
        parser.error('Exact source and positive expected native count required')
    if git('rev-parse', 'HEAD') != args.source or git('status', '--porcelain', '--', *BOUND):
        parser.error('Commit gameplay/build/harness source before recording native evidence')
    output = args.output.absolute()
    if output.exists() or any(p.is_symlink() for p in [output, *output.parents]):
        parser.error('Choose a new regular evidence directory; preserve previous attempts')
    command = [sys.executable, str(args.wrapper.resolve(strict=True))]
    command += args.gradle[1:] if args.gradle[:1] == ['--'] else args.gradle
    if 'runGameTestServer' not in command:
        parser.error('This recorder requires runGameTestServer')
    output.mkdir(parents=True, exist_ok=False)
    start = time.time()
    initial = {'schema': 1, 'source': args.source, 'command': command,
               'expectedTests': args.expected_tests, 'startedUnix': start,
               'status': 'RUNNING', 'requiresHumanLogReview': True}
    (output / 'process.json').write_text(json.dumps(initial, indent=2) + '\n')
    with (output / 'native.log').open('xb') as log:
        process = subprocess.run(command, cwd=REPO, stdout=log, stderr=subprocess.STDOUT, pass_fds=inherited)
    raw = (output / 'native.log').read_bytes()
    text = raw.decode('utf-8', errors='replace')
    # Minecraft reports completion independently of Gradle. Require both and
    # preserve the raw report for inspection; missing/ambiguous output fails shut.
    completed = [int(n) for n in re.findall(r'=+ (\d+) GAME TESTS COMPLETE =+', text)]
    successful_counts = [int(n) for n in re.findall(r'All (\d+) required tests passed', text)]
    successful = successful_counts == [args.expected_tests]
    clean = git('rev-parse', 'HEAD') == args.source and not git('status', '--porcelain', '--', *BOUND)
    passed = process.returncode == 0 and completed == [args.expected_tests] and successful and clean
    result = {**initial, 'endedUnix': time.time(), 'elapsedSeconds': round(time.time()-start, 3),
              'exitCode': process.returncode, 'reportedCompleted': completed,
              'requiredTestsPassedMessage': successful, 'sourceUnchanged': clean,
              'nativeLogSHA256': hashlib.sha256(raw).hexdigest(),
              'status': 'NATIVE_PROCESS_PASS_REQUIRES_REVIEW' if passed else 'FAILED_OR_INCOMPLETE'}
    (output / 'process.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2), flush=True)
    return 0 if passed else 1


if __name__ == '__main__':
    raise SystemExit(main())
