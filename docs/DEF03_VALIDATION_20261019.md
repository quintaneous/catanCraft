# DEF-03 City A P3 validation — 2026-10-19

## Delivery state

This is a reviewed development checkpoint, **not a playable release**. All four
appearance/footprint approvals are honored. North P2 bytes remain unchanged;
P3 exact geometry, rear corridor and agreed DEF-04 prices are retained. Existing
producer, house and entity integration remains present. No live save, installed
mods, server settings, multiplayer, other cities, NPCs or siege were accessed.

Code checkpoint: `bc695bf` (full ID in the packaged SOURCE_COMMIT.txt). Its parent
`c2c232d` contains the isolated North fixture correction; `5471c3d` preserves
naturally generated entities outside the combined fixture; `e5f60d1` adds fixed
cover v2; `d56c036` contains the grouped backend; `0c6dff4` pins approved assets.
The original delivery/foundation and newer local history were preserved.

## What changed

Approved immutable P3 assets are reconciled with existing reservations and
North P2. Whole-city composition enumerates 81 tier combinations and 108
upgrade/reverse cases. Each coordinated purchase journals one quoted price,
one side level and exact prior city lineage; owned corner writes and neighboring
receipts belong to that operation. The backend preserves closed unrelated
connections, opens incident available corners, refuses missing/changed ancestry,
resumes historical prices, and refuses rollback across later purchases.

Full-city tests exercise all eight purchases/upgrades, mixed tiers, North with
both connections open, two-owner seam interruption/recovery/reverse, duplicate
requests, old journal bytes, protected books/items and SavedData reload. This
reload evidence does not claim an independent packaged-process restart.

Native testing found actual grass decay beneath fixed paving and bottom stairs.
Policy v3 uses vanilla grass light occlusion, while NONE/v1/v2 retain their
original semantics. Only precisely whitelisted grass/plain dirt under proven
exact covers can differ; no terrain repair, arbitrary dirt adoption or repaint
was added. The deterministic seventh support test runs native grass random ticks,
rejects fresh unproved dirt and v1 stair interpretation, and checks historical
payment, NBT, rollback and duplicate safety. Independent read-only review found
no safety blocker. Off-whitelist refusal is also retained in tests 1 and 6.

## Exact validation commands and isolation

```bash
cd /workspace/catanCraft
export JAVA_HOME=/workspace/toolchains/jdk-17.0.16+8
export PATH="$JAVA_HOME/bin:/workspace/toolchains/gradle-8.8/bin:$PATH"
export GRADLE_USER_HOME=/workspace/gradle-cache
bash /workspace/integration-evidence/p3-native/run-consolidated-e5.sh
bash /workspace/integration-evidence/p3-native/run-final-checks.sh
bash /workspace/integration-evidence/p3-native/run-round6.sh
```

The exact runner scripts, source IDs, patches, logs and exit codes are packaged.
Each namespace uses a separate disposable native world. Existing test directories
are moved to build/test-world-history; thall4.schem and thall5.schem are staged
under that run's config/catancraft/schematics. The underlying native command is:

```bash
gradle runGameTestServer -PgameTestNamespace=<namespace> --offline --no-daemon --max-workers=4
```

SBW artifact audit uses the same command plus
`--init-script tools/validate_sbw.gradle`; it tests the pinned published artifact,
not the owner's installed mods. Build uses `gradle build --offline --no-daemon
--max-workers=4`. Gradle's NO-SOURCE unit-test task is not counted as a test.
Python commands are `python3 -m unittest discover -s tests -p '<pattern>' -v`,
with patterns test_*demo.py (38), test_sector_economy.py (14), and
 test_sector_p3.py (8). All 60 passed in consolidated-e5.

## Source-specific executed native evidence

| Namespace | Executed | Passed | Failed | Evidence directory |
| --- | ---: | ---: | ---: | --- |
| catancraft | 50 | 50 | 0 | consolidated-e5 |
| catancraft_public27 | 11 | 11 | 0 | consolidated-e5 |
| catancraft_integration | 12 | 12 | 0 | final-checks |
| catancraft_producer_r2 | 9 | 9 | 0 | consolidated-e5 |
| catancraft_houses | 11 | 11 | 0 | consolidated-e5 |
| catancraft_defense | 8 | 8 | 0 | consolidated-e5 |
| catancraft_sectors | 18 | 18 | 0 | consolidated-e5 |
| catancraft_sectors_service | 14 | 14 | 0 | consolidated-e5 |
| catancraft_sectors_p1 | 4 | 4 | 0 | consolidated-e5 |
| catancraft_north_p2 | 4 | 4 | 0 | round6 |
| catancraft_reservation_verifier | 4 | 4 | 0 | consolidated-e5 |
| catancraft_sector_prices | 7 | 7 | 0 | consolidated-e5 |
| catancraft_city_a_p3 | 5 | 5 | 0 | consolidated-e5 |
| catancraft_sector_connections | 4 | 4 | 0 | consolidated-e5 |
| catancraft_sectors_natural | 7 | 7 | 0 | round6 |
| catancraft_sectors_group_proof | 2 | 2 | 0 | round6 |
| catancraft_city_a_group | 3 | 3 | 0 | round6 |
| catancraft_sbw | 7 | 7 | 0 | final-checks |

Selected totals: **173 native + 7 published-SBW audit tests passed; 60 Python tests passed.**
Final v3 Gradle build passed (round6/build.log, exit 0).


This is a selected set of actual executions across documented checkpoints,
not one uninterrupted final-source test run. Consolidated-e5 includes a marked
reuse of same-source round5 grouped results. Final-checks reruns the corrected
combined fixture and SBW. Round6 reruns every v3-dependent full-size adapter and
paid group proof. Unchanged namespaces retain their prior execution evidence;
no prior failures have been erased or reclassified as passes.

Consolidated-e5 itself exited 1: combined completed 12 with 11 passed/1 failed
(generated chicken collided with synthetic floor); North completed 4 with
3 passed/1 failed (generated pig at old fixture altitude). Isolated fixture
corrections preserve the animals and all assertions. The next North run crashed
on real delayed stair-covered grass decay; it has no completed-suite count and
must not be reported as four executed tests. The v3 correction addresses that
cause. Earlier rounds 2–4 and their failures also remain archived.

## Remaining blockers and exact next steps

1. **Runtime responsiveness:** full-size grouped quote/purchase operations take
   tens of seconds synchronously. The production resolver deliberately refuses
   purchase/charge. Implement a bounded, resumable main-thread operation runner
   with durable city reservation, exact world/ownership/NBT guards, fresh entity
   checks at tick boundaries, and original-price recovery; measure latency and
   validate interruption/restart before opening the gate. Do not bypass guards.
2. **Authored world missing:** eighteen unique input ZIP indexes contain no
   level.dat or region files. Bounded schematic/NPZ context is not a playable
   authored world. Obtain and hash the intended clean world/bootstrap input,
   then use the existing tested assembler for matching JAR/config/assets and
   launcher. Do not copy developed City A or substitute a native test fixture.
3. **Acceptance:** run the single CITY_A_FRESH_SOLO_CHECKLIST.md in that isolated
   package, including a real stop/reopen. Artwork approval alone is not gameplay,
   installed-mod, vehicle or gun acceptance.

DEF-04 remains PROVISIONAL PLAYTEST PRICING: each Basic $1,500 + 15 Wood +
30 Stone; each Reinforced increment $4,000 + 50 Concrete + 15 Steel. Full city:
$22,000 + 60 Wood + 120 Stone + 200 Concrete + 60 Steel. ECO-02 comparison remains
recorded, not a claim of final balance. No corner, gate, gun or ammunition fee
or equipment spawn is implied. SEC-01 crowbar/retrieval remains **UNRESOLVED /
HIGH PRIORITY**; SEC-02 remains open. No merge, push or deployment occurred.
