# Combined integration status

**Final automated result: build PASS; 101 native tests executed, 101 passed, 0 failed.**
All five subtasks are implemented and locally verified. Manual modpack acceptance remains pending; no deployment/publication occurred.

Development branch: `codex/combined-integration`, based on verified remote
`v0.1-foundation` at `d09f3544babeb2602261ddc2aeb90c34b8ff966e`.
Existing checkout was clean; original branches and prepared environment retained.
No live world, installed mods, server settings, or multiplayer are in scope.

## Verified inputs and baseline

Both ordinary handoff ZIPs were safely extracted together into
`/workspace/catancraft-inputs/CatanCraft-Dev`, without extracting the source snapshot
over the checkout. `python3 handoff/verify_inputs.py` from that directory exited 0:
`TRANSFER CHECK PASS: 23 files`. AGENTS.md, START_HERE.md, and working instructions
read. Individual asset contracts were audited independently; runtime exports were reproduced deterministically.

Outer ZIP SHA-256:
- Part 1: `0bd7c0c6c957ea11d020dee7fb668155695ed716791d2104cad26b7fb0f5336e`
- Part 2: `5871f1cd217dbbba9176719c840e1c22c868210fd634b7ab27fc0d29f1fb653d`

Reusable baseline evidence at `/workspace/setup-evidence/SETUP_REPORT.md` matches
the unchanged original baseline source: Java 17 / Gradle 8.8, baseline build exit 0; retained
namespace 50 executed / 50 passed / 0 failed, public27 namespace 11 / 11 / 0,
both command exits 0. Conventional Gradle test is NO-SOURCE. These are baseline
results, not validation of upcoming changes. Full logs and evidence checksums retained.

## Tracked subtasks

| Subtask | Status | Dependencies | Files changed / validation |
| --- | --- | --- | --- |
| 1. Entity obstruction | Implemented; focused and retained regressions passed | Verified baseline | ConstructionHazards, ConstructionHazardTests, ProducerTransaction/Service/Tests/OccupancyTests, StreetscapeTransaction/Tests, build.gradle. Eight new native geometry tests: 8 executed / 8 passed / 0 failed (offline command exit 0); fresh retained 50/50 and public27 11/11 passed, both exit 0. Initial six-test attempt: 4 pass / 2 fail due unstationary item fixtures; fixed without weakening assertions. |
| 2. Producer Exterior R2 | Implemented; focused native tests passed | 1; immutable input verification | ProducerExteriorR2, ProducerRevisions/Transaction/Service, pinned resources, 9 native tests executed/passed, 0 failed, exit0; final regression passed |
| 3. Six R5 houses | Implemented; focused native tests passed | 1; whole-house R1/R2 route verification | R5HouseRevisions/Transaction/Service/Tests, PlannedWorld, full contexts; 11 native tests executed/passed, 0 failed, exit0; combined validation passed |
| 4. Initial defenses | Implemented; focused native tests passed | 1; compatibility with 2/3/protected reservations | Defense registry/transaction/service/catalog/commands/tests; 8 executed/passed, 0 failed; final regression passed |
| 5. Combined validation/release | Verified locally; packaging/review | All four implemented and independently tested | Preserve both existing namespaces; add new native coverage; one JAR/package/checklist |

## Checkpoints and next step

| Checkpoint | Scope |
| --- | --- |
| `1dcc316` | Verified handoff and tracked subtasks |
| `d32b625` | Entity hazards and focused native tests |
| `27760c3` | Fresh retained/public regression evidence |
| `d83e0dd` | Producer R2 and 9/9 focused native tests |
| `ad06058` | Six houses and 11/11 focused native tests |
| `622156a` | Initial defenses and 8/8 focused native tests |

Combined checkpoint `c1097d3` contains the complete tested runtime source. Final evidence checkpoint updates this status document only; release/runtime hash comparison is retained in the release manifest. `7dca159` introduced the all-suite runner, read-only CI, checklist and historical-cost lookup improvement.

Next: review the one packaged release and perform the single focused checklist on an isolated development copy. No owner/live-world acceptance is claimed. Only root ran native worlds; independent agents reviewed/audited assigned files.

## Exclusions and unresolved issues

Crowbar exploit remains **UNRESOLVED / HIGH PRIORITY**; this integration must not
claim it fixed from compilation, mocked interaction or denial text. No gates,
gate-dependent wings, trenches, rejected bunkers, orientation pavilion, terrain
rollout, siege restoration or automatic all-city installation. No reset, live
world access, merge to main, force-push, deployment or multiplayer testing.

Owner supplied provisional defense prices; explicit centralized catalog and pre-confirmation quote now implemented. No recurring upkeep or new resources/technology. Network proxy again denies Forge/Minecraft hosts; offline cached compilation and eight native tests passed, so this currently does not block implementation. Any unresolved contract/design question will
be recorded here with the exact next step; independently unblocked work continues.

## Current focused validation

- `gradle compileJava --offline --no-daemon --max-workers=4`: exit 0.
- `gradle runGameTestServer -PgameTestNamespace=catancraft_integration --offline --no-daemon --max-workers=4`: 8 executed, 8 passed, 0 failed; exit 0. Logs `/workspace/integration-evidence/entity-retry/`.
- Retained50/public27 initial and fresh reruns are recorded below; both fresh suites passed.
- Independent input audits: Producer R2 186/186 internal hashes, R5 333/333 hashes, all 18 house signatures and 12 branch routes passed. Pinned loaders/exports are now implemented and committed.

## Entity checkpoint and retained reruns

- Checkpoint `d32b625`: precise shape/support/attachment hazards, unchanged entity/item NBT checks, focused 8/8 pass; retained regressions were pending then.
- Reusing prior native worlds produced 46/50 retained passes (falling gravel/stale occupancy) and 10/11 public27 (new obstruction fixture lacked entity visibility wait). Failed evidence/worlds preserved, assertions retained. Fresh disposable directories and explicit entity query/ticket readiness fixed fixture isolation.
- Fresh runs `/workspace/integration-evidence/entity-regression-fresh`: retained50 executed/50passed/0failed exit0; public27 11/11/0 exit0, with real collision and pending/reverse hazard checks intact.
- Exact commands: `gradle build runGameTestServer --offline --no-daemon --max-workers=4`; `gradle runGameTestServer -PgameTestNamespace=catancraft_public27 --offline --no-daemon --max-workers=4`. Same Java17/Gradle8.8 activation as setup report.

## Provisional defense costs (nation currency/stockpile only)

| Explicit price band | Money | Wood | Stone | Steel | Concrete |
| --- | ---: | ---: | ---: | ---: | ---: |
| 8-block/short wall | 500 | 5 | 15 | 0 | 0 |
| 16-block/long wall | 1000 | 10 | 30 | 0 | 0 |
| Compatible corner (registry still controls availability) | 750 | 10 | 20 | 0 | 0 |
| Checkpoint | 2000 | 20 | 40 | 0 | 0 |
| Guard tower | 3000 | 20 | 50 | 10 | 0 |
| Compact R6 casemate | 4000 | 0 | 0 | 15 | 40 |

Explicit mappings are in DefenseCostCatalog: authored wall6/7/end-cap/checkpoint-wing connector modules use the short band; wall14/straight16 the long band. Footing/support cells bundled within each module cost no extra. Unknown/unmapped IDs refuse. Cost is journaled; current price edits never reinterpret recovery/refund. Separate money and material shortage tests passed.

## Producer R2 verified checkpoint

`/workspace/integration-evidence/producer-final-focus/native.log`: 9 executed / 9 passed / 0 failed, exit0, including 40-tick exact native geometry and inventory/payment checks on both future-upgrade sources. Command `gradle runGameTestServer -PgameTestNamespace=catancraft_producer_r2 --offline --no-daemon --max-workers=4`.

Initial focused attempt6/9 passed: one test assumed all glass was clear (approved source also has light-blue stained glass), plus underground/boundary fixtures caused support checks. Fixtures moved to isolated sky contexts without modifying authored states. Retry7/9 passed but synthetic fence reconnection checks still refused future purchases: the unchanged original R1 L5 design has authored disconnected boundary fence states next to added andesite. The transaction's inherited UPDATE_KNOWN_SHAPE/no UPDATE_NEIGHBORS policy intentionally does not request those neighbor writes. PlannedWorld now models actual caller flags, keeps unconditional survival/gravity checks, and defaults strict otherwise. Assets/hashes/counts unchanged; delayed native targets passed. Flags-policy/support regressions passed within the final 12-test integration namespace. Ordinary later edits can recalculate vanilla connections and must still meet exact guarded source checks.

New runtime R2 flags are absent from legacy journal descriptions, preserving old pins/tokens and pending R1 interpretation. New revisions and immutable source contexts are separate. Explicit `/catan producer exterior <city> confirm` performs a free correction at the actual paid tier; normal upgrades choose corrected targets and recognize either source revision. Existing current inventories and historical journals are preserved.

## Six-house verified checkpoint

House namespace `/workspace/integration-evidence/components-retry/catancraft_houses.log`: 11 executed / 11 passed / 0 failed, exit0. Command `gradle runGameTestServer -PgameTestNamespace=catancraft_houses --offline --no-daemon --max-workers=4`. All12 routes exercised, both orientations, mixed recognized whole-house branches, exact source signatures, actual current book/inventory NBT through resumed/reversed recovery, corrupt/drift/foreign source refusal, pendingAPPLY refusal, durable physical-save window, native delayed targets. No current server/player world was used.

Files: R5HouseRevisions/Transaction/Service/Tests.java and pinned `r5-house-contexts.json.gz`. Full13x47x11 YZX canonical contexts, strict R1/R2 sources, one independent free six-house history. Existing R5 public groups/civics unchanged. Source snapshot never pasted over checkout.

## Initial-defense verified checkpoint

`components-retry/catancraft_defense.log`: 8 executed / 8 passed / 0 failed, exit0. Command `gradle runGameTestServer -PgameTestNamespace=catancraft_defense --offline --no-daemon --max-workers=4`. Covers the full explicit catalog/864 plans, original-cost recovery and exactly-once accounting, command owner policy, separate money/material shortages, support/read refusal, interrupted application, UUID repurchase history and missing-journal refusal. Commands and price mapping are documented in DEFENSE_INITIAL_INTEGRATION.md.

Producer checkpoint `d83e0dd`; house checkpoint `ad06058`. Remote development head rechecked unchanged at `d09f3544babeb2602261ddc2aeb90c34b8ff966e`. Combined focus executed12:10 passed,2 canonical-city composition fixtures refused unsupported gravity blocks. Logs retained in `/workspace/integration-evidence/combined-focus/`; fixing test fixture boundary support, with strict runtime checks retained. Final fresh regression subsequently passed; see final results below.

## Release validation preparation

Release target: 0.1.28, Minecraft1.20.1/Forge47.4.10, Java17/Gradle8.8 unchanged.
`tools/validate_combined.sh` builds, checks original fixture hashes, stages fixtures,
archives previous disposable test directories, runs all six namespaces sequentially,
requires exact completion/pass counts and preserves logs/exit codes/source status.
Observed retained50 + public27 11 + integration12 + producer9 + houses11 + defense8 =101, all passed.
New CI runs the same script with read-only repository permissions and no publication;
it has not run remotely. `docs/COMBINED_INGAME_CHECKLIST.md` is the single manual checklist.
Independent final defense review found no blocker; its historical-price ordering
recommendation is implemented: service recovery and commands do not require a current
catalog entry to interpret an existing journal's recorded cost.

Independent final Producer/house read-only review: no concrete blocker. Original15 producer resources byte-identical to baseline;11 runtime manifest pins matched (two contexts+nine corrected schematics). Reviewed actual-tier free corrections, both upgrade source revisions, legacy describe/hash/token compatibility, exact12 house routes, current inventory/book NBT and durable forward/reverse recovery. This review is separate from native results and is not owner in-game acceptance.

## Final source, exact commands and actual results

Native tests ran against clean commit `c1097d38bd384135873b9375b0d21837dafad4a6`, based on remote `v0.1-foundation` `d09f3544babeb2602261ddc2aeb90c34b8ff966e` (fetched/rechecked unchanged before final validation). Prepared Java17/Gradle8.8 and actual cached dependencies were reused without changing Minecraft/Forge versions, bypassing the proxy, or disabling TLS. Conventional Gradle `test` is NO-SOURCE; the counts below are actual native GameTests.

```bash
export JAVA_HOME=/workspace/toolchains/jdk-17.0.16+8
export PATH="$JAVA_HOME/bin:/workspace/toolchains/gradle-8.8/bin:$PATH"
export GRADLE_USER_HOME=/workspace/gradle-cache
bash tools/validate_combined.sh /workspace/integration-evidence/final --offline
```

Runner command capture `final/commands.log` contains these exact Gradle invocations (all with `--offline --no-daemon --max-workers=4`): `gradle build`, then `gradle runGameTestServer -PgameTestNamespace=<namespace>` for each row below, in order. Original thall4/thall5 fixture hashes were verified and fixtures staged in each namespace's isolated directory. Prior test directories were moved to `build/test-world-history`, never used as live worlds.

| Namespace | Executed | Passed | Failed | Exit |
| --- | ---: | ---: | ---: | ---: |
| catancraft | 50 | 50 | 0 | 0 |
| catancraft_public27 | 11 | 11 | 0 | 0 |
| catancraft_integration | 12 | 12 | 0 | 0 |
| catancraft_producer_r2 | 9 | 9 | 0 | 0 |
| catancraft_houses | 11 | 11 | 0 | 0 |
| catancraft_defense | 8 | 8 | 0 | 0 |

Baseline build exit0; overall runner exit0. Full console logs, fixture checksums, source status and runtime/build/test pins are in `/workspace/integration-evidence/final/`; all earlier failed and successful attempts remain alongside it. Original28 resources/fixtures were byte-compared with baseline, all unchanged. Exact changed-file and checkpoint lists are also packaged.

Both canonical-city A/I composition tests passed at native coordinates: public1661 writes; six mixed-branch houses2161; producer A424/I212;54 compatible defense candidates per city and3 actual purchases each with duplicate accounting, guarded reverse/refund, disk-ledger reload, current inventory/book preservation and40-tick geometry/item checks. First retry's A delayed failure was a stale-world item merge (old+new7-stack became14); fresh worlds passed without altering assertions. Earlier partial-file compilation failure in the first component attempt executed no house/defense tests and remains in logs.

## Remaining blockers, scope and exact next step

No automated build/release blocker remains. Cached offline dependency validation passed; fresh online dependency restoration was not recertified in this integration after proxy denials. Optional Forge update/authentication warnings did not prevent native completion. Remote GitHub CI has not run; the new workflow is reviewable source only.

Actual TaCZ aperture behavior, Superb Warfare vehicle clearance and visual lighting require the single isolated in-game checklist `docs/COMBINED_INGAME_CHECKLIST.md`. Native fault injection and saved-journal reload are not a claim of exhaustive arbitrary-crash certification. Crowbar exploit remains **UNRESOLVED / HIGH PRIORITY**. Preserve all exclusions above. Exact next step: owner reviews the release package/source/evidence, then performs that one checklist on an isolated copy; no merge, push, publication, deployment or live-world action is authorized here.

## Complete changed-file inventory

- `.github/workflows/combined-validation.yml`
- `build.gradle`
- `docs/COMBINED_INGAME_CHECKLIST.md`
- `docs/DEFENSE_INITIAL_INTEGRATION.md`
- `docs/INTEGRATION_STATUS.md`
- `src/main/java/com/quin/catancraft/world/CombinedIntegrationTests.java`
- `src/main/java/com/quin/catancraft/world/ConstructionHazardTests.java`
- `src/main/java/com/quin/catancraft/world/ConstructionHazards.java`
- `src/main/java/com/quin/catancraft/world/DefenseCommandTests.java`
- `src/main/java/com/quin/catancraft/world/DefenseCommands.java`
- `src/main/java/com/quin/catancraft/world/DefenseCostCatalog.java`
- `src/main/java/com/quin/catancraft/world/DefenseRevisions.java`
- `src/main/java/com/quin/catancraft/world/DefenseService.java`
- `src/main/java/com/quin/catancraft/world/DefenseTests.java`
- `src/main/java/com/quin/catancraft/world/DefenseTransaction.java`
- `src/main/java/com/quin/catancraft/world/PlannedWorld.java`
- `src/main/java/com/quin/catancraft/world/PlannedWorldTests.java`
- `src/main/java/com/quin/catancraft/world/ProducerExteriorR2.java`
- `src/main/java/com/quin/catancraft/world/ProducerExteriorR2Tests.java`
- `src/main/java/com/quin/catancraft/world/ProducerOccupancyTests.java`
- `src/main/java/com/quin/catancraft/world/ProducerRevisions.java`
- `src/main/java/com/quin/catancraft/world/ProducerService.java`
- `src/main/java/com/quin/catancraft/world/ProducerTests.java`
- `src/main/java/com/quin/catancraft/world/ProducerTransaction.java`
- `src/main/java/com/quin/catancraft/world/R5HouseRevisions.java`
- `src/main/java/com/quin/catancraft/world/R5HouseService.java`
- `src/main/java/com/quin/catancraft/world/R5HouseTests.java`
- `src/main/java/com/quin/catancraft/world/R5HouseTransaction.java`
- `src/main/java/com/quin/catancraft/world/StreetscapeTests.java`
- `src/main/java/com/quin/catancraft/world/StreetscapeTransaction.java`
- `src/main/resources/assets/catancraft/defense/contracts.json.gz`
- `src/main/resources/data/catancraft/integration/producer-r2-contexts.json.gz`
- `src/main/resources/data/catancraft/integration/producer_exterior_r2/farm_l3.schem`
- `src/main/resources/data/catancraft/integration/producer_exterior_r2/farm_l4.schem`
- `src/main/resources/data/catancraft/integration/producer_exterior_r2/farm_l5.schem`
- `src/main/resources/data/catancraft/integration/producer_exterior_r2/lumberyard_l3.schem`
- `src/main/resources/data/catancraft/integration/producer_exterior_r2/lumberyard_l4.schem`
- `src/main/resources/data/catancraft/integration/producer_exterior_r2/lumberyard_l5.schem`
- `src/main/resources/data/catancraft/integration/producer_exterior_r2/quarry_l3.schem`
- `src/main/resources/data/catancraft/integration/producer_exterior_r2/quarry_l4.schem`
- `src/main/resources/data/catancraft/integration/producer_exterior_r2/quarry_l5.schem`
- `src/main/resources/data/catancraft/integration/r5-house-contexts.json.gz`
- `tests/tools/integration_inputs/MANIFEST.json`
- `tests/tools/integration_inputs/README.md`
- `tests/tools/integration_inputs/export_runtime.py`
- `tools/prepare_defense_inputs.py`
- `tools/validate_combined.sh`
