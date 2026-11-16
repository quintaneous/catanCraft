#!/usr/bin/env python3
"""Benchmark exact authored wall coordinates with Java 17; never opens a world."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import statistics
import subprocess

ROOT = Path(__file__).resolve().parents[2]
TEMPLATE_SHA = 'facc415887db9cff673296c3c40ca8a30f80832570de36de8fe4d6a6827560c2'
SITES_SHA = 'b2b4f028db5838e837887bc569d06068837d4df2e536442bd947250335acd5b5'


def pinned(path, expected):
    raw = path.read_bytes()
    if hashlib.sha256(raw).hexdigest() != expected:
        raise ValueError('Input pin mismatch: ' + str(path))
    return json.loads(gzip.decompress(raw))


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--java', required=True, type=Path)
    p.add_argument('--output', required=True, type=Path)
    p.add_argument('--city', default='m', choices=list('abcdefghijklmnop'))
    args = p.parse_args()
    args.output.mkdir(parents=True, exist_ok=False)
    template = pinned(ROOT/'src/main/resources/assets/catancraft/sector_p3/city_a.json.gz', TEMPLATE_SHA)
    sites = pinned(ROOT/'src/main/resources/assets/catancraft/sector53/sites.json.gz', SITES_SHA)
    site = sites['cities'][args.city]
    original = {tuple(row[:3]) for asset in template['assets'] for row in asset['fresh'] + asset['reads']}
    coordinates = set()
    for x, y, z in original:
        dx, dy, dz = x + 750, y - 81, z + 750
        if site['rotation'] == 180:
            dx, dz = -dx, -dz
        cx, cy, cz = site['center']
        coordinates.add((cx + dx, cy + dy, cz + dz))
    if len(coordinates) != site['guardCount']:
        raise ValueError('Qualification guard inventory differs')
    coords = args.output/'coordinates.txt'
    coords.write_text(''.join('%d %d %d\n' % row for row in sorted(coordinates)))
    source = ROOT/'tools/walls53/SpatialMapBenchmark.java'
    version = subprocess.run([str(args.java), '-version'], text=True, capture_output=True, check=True)
    run = subprocess.run([str(args.java), str(source), str(coords)], text=True, capture_output=True)
    (args.output/'stdout.log').write_text(run.stdout)
    (args.output/'stderr.log').write_text(run.stderr)
    run.check_returncode()
    result = json.loads(run.stdout)
    result.update(schema='catancraft-sector53-spatial-map-benchmark-v1', city=args.city,
                  template_sha256=TEMPLATE_SHA, sites_sha256=SITES_SHA,
                  coordinate_sha256=hashlib.sha256(coords.read_bytes()).hexdigest(),
                  benchmark_sha256=hashlib.sha256(source.read_bytes()).hexdigest(),
                  java_version=version.stderr.strip(),
                  limitation='Standalone mirrored Vec3i key benchmark; not native gameplay, a world change, or end-to-end quote latency.')
    result['medians_ms'] = {key: statistics.median(row[key] for row in result['measurements'])
                            for key in ['hash_copy_ms', 'hash_lookup_ms', 'map_copyof_ms', 'map_copyof_lookup_ms']}
    result['median_copy_speedup'] = result['medians_ms']['map_copyof_ms']/result['medians_ms']['hash_copy_ms']
    result['median_lookup_speedup'] = result['medians_ms']['map_copyof_lookup_ms']/result['medians_ms']['hash_lookup_ms']
    (args.output/'result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
