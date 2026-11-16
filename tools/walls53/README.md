# INT-02 / DEF-02 / DEF-03 / DEF-04: actual-site wall source audit

Run the exporter against the immutable clean R7 input, never against a played
world or installed mod directory:

```sh
PYTHONDONTWRITEBYTECODE=1 python3 tools/walls53/audit_sites.py \
  --input-root /path/to/CatanCraft_Clean_R7_Input \
  --report docs/evidence/walls53_source_audit.json \
  --catalog src/main/resources/assets/catancraft/sector53/sites.json.gz
```

The exporter verifies all 40 original manifest entries before reading chunks,
then checks their hashes again after the audit. It reads the actual authored
terrain under every transformed wall guard and composes the normal starter and
R5 public operations. City A's original resource is read-only. The generated
catalog is a source qualification input, not a world-placement instruction.

The catalog has 16 city rows, each with the exact map center/orientation,
67,205 guarded cells, 17,462 owned structural cells, canonical observed source
hash, unchanged context overrides, and protected rear-corridor reads. Coordinate
rows are absolute world coordinates. Rotated cities map geometry and ownership
to physical compass directions, including the asymmetric extension and corridor.

`sourceOverrides` are four-column `(x,y,z,state)` rows for unchanged guard context.
`ownedSourceOverrides` are six-column `(x,y,z,before,basic,reinforced)` rows.
Only 26 actual authored soil cells need the latter: F=10, G=8, I=1, J=2, L=5.
Every one is already inside the original sparse structural mask and replaces
exact grass with stone bricks, then light gray concrete. There is no terrain
clearance or footprint expansion. Apply these sources only while the owning
side is level zero; original before-states must remain available for rollback.
A has neither kind of override and retains its historical provider.

The complete report records 1,075,280 actual guard observations, 1,382 decoded
chunks in all 16 regions, 2,160 factory type/slot/tier full-envelope checks and
80 direct per-city civic/support contracts. The template's geometry proof covers
81 level vectors, 433 valid open/closed corner histories and 984 next-tier
transitions; each site's transform is separately bijective and its terrain is
separately read. These are static cases, not a count of native tests.

All current factory envelopes, civic/support writes, house-route changes and
protected rear corridors are disjoint from structural writes. Declared access
routes have no observed obstruction. The 114 observed leaf cells are unchanged
context, persistent, and none is adjacent to any structural write. No leaf or
soil comparison is weakened by this exporter. Normal tick behavior still needs
native qualification.

The first audit correctly refused the 26 non-air structural sources. Its failed
log is retained as `docs/evidence/walls53_source_attempt1.log`. The exact-source
soil construction contract resolved that blocker; it did not replace the source
terrain with City A terrain. The deterministic catalog SHA-256 is
`b2b4f028db5838e837887bc569d06068837d4df2e536442bd947250335acd5b5`.

Artifact regressions:

```sh
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/walls53 -p 'test_*.py' -v
```

Seven tests passed; the retained log is
`docs/evidence/walls53_artifact_tests.log`. This is separate from native wall
transactions, startup/restart, cumulative progression, client interactions and
owner acceptance. Do not label this static evidence as a playable release.
