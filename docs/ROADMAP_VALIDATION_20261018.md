# INT-01 / INT-02 / DEF-02 / SEC-01 checkpoint evidence

This is an engineering checkpoint, not an approved playable release or deployment.
Completed producer, house, entity and historical socket implementations remain.
Canonical scope is [issue #10](https://github.com/quintaneous/catanCraft/issues/10).

## Source and checks

Full clean-source validation: `4e0569e4c28d45c9ddbf834aff1cc6361d824c9c`.
Final runtime: `18d1de745c802fcefd7b4b37f77af3cb92efd816`.
The intervening change touches only SectorService's startup recovery dispatcher,
its native regression, and the runner's expected service count. It isolates a
foreign/corrupt city journal refusal from later independent cities. Final build
and the entire affected service namespace were rerun; unaffected suite evidence
is reused from the full run, not claimed as newly executed on the later commit.
Both test starts recorded a clean worktree. Later documentation commits do not
change the tested runtime.

| Check | Source | Executed | Passed | Failed |
| --- | --- | ---: | ---: | ---: |
| catancraft | 4e0569e | 50 | 50 | 0 |
| catancraft_public27 | 4e0569e | 11 | 11 | 0 |
| catancraft_integration | 4e0569e | 12 | 12 | 0 |
| catancraft_producer_r2 | 4e0569e | 9 | 9 | 0 |
| catancraft_houses | 4e0569e | 11 | 11 | 0 |
| catancraft_defense | 4e0569e | 8 | 8 | 0 |
| catancraft_sectors | 4e0569e | 17 | 17 | 0 |
| catancraft_sectors_service (before final isolation fix) | 4e0569e | 11 | 11 | 0 |
| catancraft_sectors_p1 | 4e0569e | 4 | 4 | 0 |
| catancraft_sbw, pinned actual optional artifacts | 4e0569e | 7 | 7 | 0 |
| catancraft_sectors_service (final affected-suite rerun) | 18d1de7 | 12 | 12 | 0 |
| Python demo packaging, both tools | 4e0569e | 38 | 38 | 0 |

Full run: **133 ordinary native + 7 SBW tests passed**. Final service rerun:
**12/12 passed**, including one additional recovery-isolation regression.
Do not add repeated suite executions into a misleading unique-test total.
`gradle build` passed at both commits; Gradle `test NO-SOURCE` is not test evidence.
All native suites ran sequentially in separate disposable development worlds.
No connected clients or multiplayer tests were used.

Exact environment and commands:

```bash
export JAVA_HOME=/workspace/toolchains/jdk-17.0.16+8
export PATH="$JAVA_HOME/bin:/workspace/toolchains/gradle-8.8/bin:$PATH"
export GRADLE_USER_HOME=/workspace/gradle-cache
bash tools/validate_combined.sh /workspace/integration-evidence/roadmap-final-4e0569e --offline
# Optional SBW run, after ordinary suites finish:
gradle runGameTestServer -PgameTestNamespace=catancraft_sbw \
  --init-script tools/validate_sbw.gradle --offline --no-daemon --max-workers=4
# Final recovery change only:
gradle build --offline --no-daemon --max-workers=4
gradle runGameTestServer -PgameTestNamespace=catancraft_sectors_service \
  --offline --no-daemon --max-workers=4
```

The runner executes `gradle build`, `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest
discover -s tests -p 'test_*demo.py' -v`, and `gradle runGameTestServer
-PgameTestNamespace=<namespace> --offline --no-daemon --max-workers=4` for every
ordinary namespace. Before each native run, the prior run directory is moved to
`build/test-world-history/`; `tests/fixtures/thall4.schem` and `thall5.schem` are
copied to its new `config/catancraft/schematics/`. Their pinned hashes are checked.
The optional SBW and final service runs used the same staging/preservation steps.

Evidence roots (logs, exits, actual source, clean-worktree record, hashes/results):
- `/workspace/integration-evidence/roadmap-int01/`: original published release reconciliation.
- `/workspace/integration-evidence/roadmap-focused/`: focused passes and all failed attempts.
- `/workspace/integration-evidence/roadmap-final-4e0569e/`: full clean-source run, `RESULTS.json`.
- `/workspace/integration-evidence/roadmap-recovery-final/`: final build and 12-test rerun, `RESULTS.json`.

Development failures remain visible: service fixture-loading runs were 11/4/7
and 11/10/1; production loading guards were preserved. Initial SBW launches failed
before tests on refmap configuration and Gradle task-registration timing; the
corrected, hash-pinned optional wrapper then passed all seven real-artifact tests.
No dependency versions, TLS checks, proxy settings or safety assertions were
changed to force a pass. See [SEC01_RETRIEVAL_AUDIT.md](SEC01_RETRIEVAL_AUDIT.md).

## Checkpoints and files

- `aa5dae5`: INT-01 reconciliation and AGENTS/status roadmap instructions.
- `80802f1`: INT-02 authored-input package assembler and 21 new Python tests.
- `6117373`: P1 exporter, immutable prototype resource/loader and four native tests.
- `558ba84`: DEF-02 service/commands/recovery, fresh progression authorization,
  nine service + two authorization tests, runner and focused checklist.
- `4e0569e`: SEC-01 helper/override/Forge guard candidate, seven native tests and pinned optional runtime wrapper.
- `18d1de7`: per-city recovery isolation and one new native regression.

Exact changed files for each checkpoint are available with `git show --stat
<commit>`; complete current change inventory is `git diff --name-status
df04ea4..18d1de7`. Shared command/transaction changes were coordinated; only root
ran native tests. Independent reviews identified and resolved missing lower-tier
history proof, corner-table consistency, fixture symlink containment, malformed
fresh identity handling and recovery isolation. No reviewed gameplay work was
reset or replaced.

Final JAR: `build/libs/catancraft-0.1.29-sector-dev.jar`, SHA-256
`2387a5e47acf13d034331855e73890d1352942e8de0068c1185bac0bc4d38849`.
Matching original configuration assets and this JAR are staged separately at
`/workspace/releases/CatanCraft-sector-18d1de7-components/`, with explicit
`NOT_READY`, `runnable=false`, `worldIncluded=false` and `SOURCE_EVIDENCE.json`.
Earlier archives/components remain untouched. This is not another standalone
player release; no final demo ZIP, publication, push, merge or deployment occurred.

## Remaining gates and exact next steps

- **INT-02:** transfer the clean authored-world/original demo payload with original
  hashes, reviewed matching current-art bootstrap/config metadata and pinned Forge
  runtime closure. Run `tools/package_authored_demo.py` on those verified inputs,
  then validate real first launch, ordinary building/producer progression and
  restart. Do not replace it with a GameTest world or copy developed City A.
- **DEF-03:** receive and review the focused entrance revision before registering
  immutable player-facing geometry. P1 stays GameTestServer-only; its width,
  entrance, weapon positions and any expansion to other cities are unapproved.
- **DEF-04:** approve explicit sector prices/tier count and compatibility/corner
  transitions. No production catalog entry exists; fixture prices are not defaults.
- **SEC-01 OPEN / HIGH PRIORITY:** the actual-artifact candidate passes seven scoped
  tests, but full procurement/process-restart, applicable interaction paths and
  owner-modpack artifact/acceptance remain unverified. Do not advertise complete
  crowbar protection or infer armed readiness from schematic geometry.
- **INT-03 acceptance:** use the single [fresh-world checklist](FRESH_WORLD_CHECKLIST.md)
  with the matching playable package when those gates are met. No new legacy
  repair requirement, NPC work, siege implementation or multiplayer is included.
