# INT-02 clean R7 validation — 2026-10-19

This report continues the existing integration. The supplied complete authored
world replaces the earlier missing-input blocker; it does not establish packaged
startup, gameplay or modpack acceptance. No developed save or installed mods were
accessed. Existing releases, journals and source history are preserved.

## Inputs and reconciliation

Both ordinary ZIPs were safely extracted into a shared separate directory:
`/workspace/catancraft-clean-r7-inputs-20261019/CatanCraft_Clean_R7_Input`.

| Input | SHA-256 |
| --- | --- |
| Part 1 | `5b764eda3052c917a1000081ada7ea916ea4f6b917b2d766ddcab063584ce894` |
| Part 2 | `daff27f63af1938bdbbae2144f89162d128a94f5e509eea9bc963e1c5bf83036` |

From that directory, `python3 verify_inputs.py --deep --report
/workspace/integration-evidence/clean-r7/deep-verification.json` passed all 40
manifest hashes, 17 world files, 16 regions and 16,384 decoded chunk records.
The source remains unchanged. Folder `CatanCraft_Season1_R7_Development`,
LevelName `CatanCraft Environment V4 - Visual Polish R2`, seed 1042026 and
DataVersion 3465 are preserved.

Read-only reconciliation covered all 67,205 P3 guarded positions. The 28
substantive raw-world differences are grass versus expected stone bricks at
Y=80, Z=-832, X=-770..-757 and -743..-730. These are exactly the writes of the
existing guarded R5 public bootstrap. No other public write intersects the
perimeter guards; all six house routes have zero guard overlap. Map, R4-A,
R5 and Town Hall input pins match current loaders. This is source evidence,
now supplemented by the actual native bootstrap pass below.

The new output gets both scoped authorization markers; the immutable source
gets neither. Found normally, then run `/catan r5 public apply a confirm`.
Never paste Blender's city preview. The package has no inherited purchases,
funding datapack, nation data or player inventories. Creative/peaceful inspection
settings and frozen world updates are disclosed, not described as calibrated
survival settings. Showroom grants remain separate from no-grant observations.

## Implementation and checks

Checkpoint `6d42870` adds the exact clean-R7 package input mode and refusal tests.
Checkpoints `5493542`, `fac94f0` and `b5f2eb7` add resumable main-thread sector
placement, exact sealed-journal checks between slices and immutable entity-bound
compilation. Full preflight precedes the single recorded debit; interruption
retains the journal and incomplete level. Live entities are checked again before
upcoming writes. Historical prices, rollback and duplicate refusal remain intact.

Python checks executed from `/workspace/catanCraft` with
`PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tests -p '<pattern>' -v`:

| Pattern | Executed | Passed | Failed |
| --- | ---: | ---: | ---: |
| `test_*demo.py` | 51 | 51 | 0 |
| `test_sector_economy.py` | 14 | 14 | 0 |
| `test_sector_p3.py` | 8 | 8 | 0 |

At `4b0b1ae`, `catancraft_r7_production` executed **1 / passed 1 / failed 0**
on the actual copied world. Forge's supported native entry loads existing
level.dat; no flat-world substitution or preview geometry was used. The test
asserted original identity, activated City A through the normal service,
performed the existing 1,661-write public bootstrap and verified an unfunded
purchase refusal without debit. Only then did it log separate showroom grants.
Eight transitions (four Basic, then four Reinforced, including connected North)
passed through the public production service and real tick scheduler. Every
same-tick charge was exact, totaling $22,000 + Wood 60 + Stone 120 + Concrete 200
+ Steel 60. Actual `data/catancraft.dat` readback matched the completed nation
and four saved sector states. Eight operations are counted as **one native test**.
Normal economy ticking was not disabled. This does not claim paid building /
producer progression or process-restart acceptance on this raw world.

Exact command (prepared Java 17 / Gradle 8.8):
`gradle runGameTestServer -PgameTestNamespace=catancraft_r7_production --offline
--no-daemon --max-workers=4`. Evidence: `clean-r7/r7-production-round1/`.
Checkpoints `ba78a11` and `3bc5c6e` change only packaging/evidence scope and launcher validation, retaining the exact
`src/main` and build.gradle trees tested at `4b0b1ae`.

At `b5f2eb7`, affected runner checks passed 67/67. At `ba78a11`, the final
retained matrix passed 138/138, including repeated service/full-city checks.
Selected nonduplicated evidence totals **180/180 CatanCraft native tests** plus
**7/7 separate published-SBW audit tests**. Actual successful executions across
these batches plus the raw-R7 run total 206, including 19 repeated cases.
Python checks total **73/73**. These are source-specific runs, not one
uninterrupted final-commit execution. Final changes after `4b0b1ae` are packaging,
validation script and documentation only; gameplay/build tree hashes match. Earlier failed and stopped attempts stay
under `/workspace/integration-evidence/p3-native/`. No safety assertions or test
namespaces were removed.

## Runtime and remaining gates

Official Forge 47.4.10 installation passed after configuring Java to use the
existing proxy and trusted system certificate store. TLS verification stayed on.
Installer SHA-256:
`1912760b4cb6b803d8a826de603c9076b1da71ec2765e9a1f8c1ca78f65278e3`.
The new isolated output includes 104 pinned runtime files. Java 17 and Python
3.11+ are launcher prerequisites; Windows execution is not verified here.

EULA consent is pending. The actual launcher correctly refused `eula=false`;
no dedicated server startup or restart pass is claimed. The production resolver now accepts only bound, canonical City A fresh-demo
worlds. All four approved sides passed native raw-world validation; this does
not establish dedicated launcher/client acceptance. Atomic quote/preparation
peaked at 7.188 seconds and full slices including settlement at 1.286 seconds.
Four writes is the per-slice maximum; 25 ms is a cooperative target, not a hard
latency bound. Native tick execution is not an interactive frame-rate benchmark.

The supplied minimal level.dat lacks optional DragonFight data. Vanilla logged
the missing field and used its explicit EndDragonFight.Data.DEFAULT fallback;
it retained overworld identity/geometry and continued. The original source was
not edited. Offline Yggdrasil/update fetch warnings also did not fail native
execution; authenticated-client login remains unverified. Online mode and TLS
were not disabled to suppress them.
SEC-01 crowbar/retrieval remains **UNRESOLVED / HIGH PRIORITY**; SEC-02 is open.
No guns, gates, NPCs, siege, other-city expansion, deployment or multiplayer
testing was added. Use the single [fresh-world checklist](CITY_A_FRESH_SOLO_CHECKLIST.md)
only with the final readiness state, not as evidence that pending steps passed.

## Launcher review checkpoint

`3bc5c6e` permits Minecraft's normal server.properties rewrite while requiring
all owned settings to retain their exact decoded values. The initial file is
still hash-pinned; subsequent launches check semantic values. Canonical keys,
duplicates, continuations and non-Java line separators are guarded. Tests cover
normal rewrites, every protected setting, duplicate keys and comment/separator
bypasses. Independent read-only review found no remaining bypass. All 51 demo
Python tests passed; this is not a substitute for a dedicated-server restart.

## Selected native evidence by namespace

| Namespace | Executed | Passed | Failed | Source |
| --- | ---: | ---: | ---: | --- |
| `catancraft_sectors` | 22 | 22 | 0 | `b5f2eb7` |
| `catancraft_sectors_natural` | 7 | 7 | 0 | `b5f2eb7` |
| `catancraft_sectors_p1` | 4 | 4 | 0 | `b5f2eb7` |
| `catancraft_north_p2` | 4 | 4 | 0 | `b5f2eb7` |
| `catancraft_sectors_group_proof` | 2 | 2 | 0 | `b5f2eb7` |
| `catancraft_producer_r2` | 9 | 9 | 0 | `b5f2eb7` |
| `catancraft` | 50 | 50 | 0 | `ba78a11` |
| `catancraft_city_a_group` | 4 | 4 | 0 | `ba78a11` |
| `catancraft_city_a_p3` | 5 | 5 | 0 | `ba78a11` |
| `catancraft_defense` | 8 | 8 | 0 | `ba78a11` |
| `catancraft_houses` | 11 | 11 | 0 | `ba78a11` |
| `catancraft_integration` | 12 | 12 | 0 | `ba78a11` |
| `catancraft_public27` | 11 | 11 | 0 | `ba78a11` |
| `catancraft_reservation_verifier` | 4 | 4 | 0 | `ba78a11` |
| `catancraft_sbw` | 7 | 7 | 0 | `ba78a11` |
| `catancraft_sector_connections` | 4 | 4 | 0 | `ba78a11` |
| `catancraft_sector_prices` | 7 | 7 | 0 | `ba78a11` |
| `catancraft_sectors_service` | 15 | 15 | 0 | `ba78a11` |
| `catancraft_r7_production` | 1 | 1 | 0 | `4b0b1ae` |

All fixture runs stage `tests/fixtures/thall4.schem` and `thall5.schem` into the
isolated namespace's `config/catancraft/schematics/`; prior worlds are moved to
`build/test-world-history`. Exact matrix command:
`gradle runGameTestServer -PgameTestNamespace=<namespace> --offline --no-daemon
--max-workers=4`; SBW additionally uses `--init-script tools/validate_sbw.gradle`.
The raw-R7 run uses its separate verified source-copy staging script. Selected
logs and source IDs are indexed in `evidence/clean-r7/selected-native-results.json`;
all earlier failed/stopped logs remain included. No tests were disabled.

Release build command: `gradle build --offline --no-daemon --max-workers=4
-PreleaseCommit=<full source commit recorded in SOURCE_COMMIT.txt>`. The release
records the actual exit, JAR manifest commit and SHA-256. Assembly refuses a JAR
with a different source stamp or gameplay/build trees from the raw-R7 pass.
