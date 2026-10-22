# DEF-04 applied pricing: source and validation

Runtime checkpoint: `9f624dd` (`9f624dd47ea83ae7f4ef73ec2c927fc5d89a59b4`).
Prior broad regression: `7aafc34`. Reused rows below are explicitly marked; they
are not claimed as fresh executions on the pricing commit. The delta changes
sector pricing/quote metadata and tests plus analysis/docs; producer/house/entity,
legacy defenses, sector engine/cache and SBW guard runtime code are unchanged.

## Provisional schedule

Catalog `DEF04_SECTOR_PRICES_V1_20261018`, same actual compass N/E/S/W:

| Operation | Nation treasury | Nation stockpile |
| --- | ---: | --- |
| Basic L0→L1 | $1,500 | 15 Wood + 30 Stone |
| Reinforced L1→L2 increment | $4,000 | 50 Concrete + 15 Steel |

PROVISIONAL PLAYTEST PRICING. Two structural tiers this iteration, not the final
maximum. No equipment, ammunition, resupply, functional gates, upkeep or new tech
gates. No geometry approval follows from price availability. Each side is one
quote/confirmation/level/charge, including its authored owned corner connections.
Quotes freeze the version; stale versions refuse. New journals seal that version
and the original cost, and old/new historical recovery does not consult the
current catalog. Seven catalog and fourteen service tests passed, including
full Concrete/Steel debits, incremental-only costs, duplicate requests, separate
money/material refusal and historical reload/refund with an unavailable catalog.

Canonical issue #10 updated in place to revision 6; read-back matches exactly.
Before/after issue JSON is retained beside RESULTS.json. No branch pushed.

## Actual native results

| Namespace | Executed | Passed | Failed | Tested commit |
| --- | ---: | ---: | ---: | --- |
| `catancraft` | 50 | 50 | 0 | `7aafc34` |
| `catancraft_public27` | 11 | 11 | 0 | `7aafc34` |
| `catancraft_integration` | 12 | 12 | 0 | `9f624dd` |
| `catancraft_producer_r2` | 9 | 9 | 0 | `7aafc34` |
| `catancraft_houses` | 11 | 11 | 0 | `7aafc34` |
| `catancraft_defense` | 8 | 8 | 0 | `7aafc34` |
| `catancraft_sectors` | 18 | 18 | 0 | `7aafc34` |
| `catancraft_sectors_service` | 14 | 14 | 0 | `9f624dd` |
| `catancraft_sectors_p1` | 4 | 4 | 0 | `9f624dd` |
| `catancraft_north_p2` | 4 | 3 | 1 | `9f624dd` |
| `catancraft_reservation_verifier` | 3 | 3 | 0 | `7aafc34` |
| `catancraft_sector_prices` | 7 | 7 | 0 | `9f624dd` |
| `catancraft_sbw` | 7 | 7 | 0 | `7aafc34` |

Ordinary namespaces: **151 executed / 150 passed / 1 failed** across those named
checkpoints. Optional actual-SBW namespace: **7 / 7 / 0**, separately identified.
Python: **52 / 52 / 0** (38 demo and 14 economy). Build: PASS. Gradle NO-SOURCE
is not counted as tests. The first `7aafc34` combined integration attempt was
**12 / 11 / 1** due generated water intrusion; its log/world remain preserved.
The transparent fixture enclosure leaves all explicit source/Farm water intact;
the corrected `9f624dd` rerun is **12 / 12 / 0**. Runtime water guards unchanged.

North P2's full purchase/upgrade/immediate rollback assertions passed with the
approved catalog costs; the delayed assertion still failed on grass→dirt supports
with management-book NBT equal. The native interruption/reload/refund, preflight
refusal and source/cap/road/scope cases passed. No assertion or random tick was
disabled. See DEF03_NATURAL_SUPPORT_BLOCKER.md for the required versioned policy
and latency work; production P2 remains explicitly blocked.

## Commands and artifacts

Java 17.0.16+8, Gradle 8.8, Minecraft 1.20.1 / Forge 47.4.10.

```bash
export JAVA_HOME=/workspace/toolchains/jdk-17.0.16+8
export PATH="$JAVA_HOME/bin:/workspace/toolchains/gradle-8.8/bin:$PATH"
export GRADLE_USER_HOME=/workspace/gradle-cache
gradle build --offline --no-daemon --max-workers=4
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tests -p 'test_*.py' -v
# One isolated world per namespace, serially:
gradle runGameTestServer -PgameTestNamespace=<namespace> --offline --no-daemon --max-workers=4
# Optional actual SBW additionally uses:
# --init-script tools/validate_sbw.gradle
```

Exact executed scripts, commit/worktree snapshots, per-command exit codes and
logs: `/workspace/integration-evidence/north-p2-7aafc34/` and
`/workspace/integration-evidence/def04-9f624dd/`. Both existing townhall fixtures
were copied to each run's config/catancraft/schematics before execution. Earlier
worlds were retained under build/test-world-history. Final matrix: RESULTS.json.

Built development JAR SHA-256: `73ed7565388635c30946ef656ac123633651495a053ca51152eae0150a6cba44`.
Exact bytes are retained as `UNRELEASED-catancraft-0.1.29-sector-dev.jar` in the
latter evidence directory. This is test evidence, not a delivered playable demo.
No release ZIP/deployment is claimed from a failing north suite.

ECO-02 executed 15 stated spending policies / 2,895 snapshots with the applied
versioned catalog: see ECO02_SECTOR_PRICE_COMPARISON.md. Four Basic sides take
6 hours in its no-ammo starter model, or 5 after upgrading production first;
128 rounds/nation/hour spends the L1 Stone stream and prevents either goal under
that declared ammo-first policy. This is not full economic calibration.

Remaining gates: natural-support handling and latency, actual authored demo
world/bootstrap, P3 side/seam delivery and owner modpack acceptance. SEC-01
crowbar/retrieval remains unresolved/high priority; SEC-02 remains unresolved.
No live worlds/mods, multiplayer tests, NPC/siege implementation, merge, force-push
or deployment. Focused checklist: NORTH_P2_FRESH_CHECKLIST.md.
