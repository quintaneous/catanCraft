# Combined integration status

Development branch: `codex/combined-integration`, based on verified remote
`v0.1-foundation` at `d09f3544babeb2602261ddc2aeb90c34b8ff966e`.
Existing checkout was clean; original branches and prepared environment retained.
No live world, installed mods, server settings, or multiplayer are in scope.

## Verified inputs and baseline

Both ordinary handoff ZIPs were safely extracted together into
`/workspace/catancraft-inputs/CatanCraft-Dev`, without extracting the source snapshot
over the checkout. `python3 handoff/verify_inputs.py` from that directory exited 0:
`TRANSFER CHECK PASS: 23 files`. AGENTS.md, START_HERE.md, and working instructions
read. Individual asset contracts are being audited independently, read-only.

Outer ZIP SHA-256:
- Part 1: `0bd7c0c6c957ea11d020dee7fb668155695ed716791d2104cad26b7fb0f5336e`
- Part 2: `5871f1cd217dbbba9176719c840e1c22c868210fd634b7ab27fc0d29f1fb653d`

Reusable baseline evidence at `/workspace/setup-evidence/SETUP_REPORT.md` matches
the exact current source: Java 17 / Gradle 8.8, baseline build exit 0; retained
namespace 50 executed / 50 passed / 0 failed, public27 namespace 11 / 11 / 0,
both command exits 0. Conventional Gradle test is NO-SOURCE. These are baseline
results, not validation of upcoming changes. Full logs and evidence checksums retained.

## Tracked subtasks

| Subtask | Status | Dependencies | Files changed / validation |
| --- | --- | --- | --- |
| 1. Entity obstruction | Implemented; focused and retained regressions passed | Verified baseline | ConstructionHazards, ConstructionHazardTests, ProducerTransaction/Service/Tests/OccupancyTests, StreetscapeTransaction/Tests, build.gradle. Eight new native geometry tests: 8 executed / 8 passed / 0 failed (offline command exit 0); fresh retained 50/50 and public27 11/11 passed, both exit 0. Initial six-test attempt: 4 pass / 2 fail due unstationary item fixtures; fixed without weakening assertions. |
| 2. Producer Exterior R2 | Implemented; focused native tests passed | 1; immutable input verification | ProducerExteriorR2, ProducerRevisions/Transaction/Service, pinned resources, 9 native tests executed/passed, 0 failed, exit0; full final regression pending |
| 3. Six R5 houses | Implemented; focused native tests passed | 1; whole-house R1/R2 route verification | R5HouseRevisions/Transaction/Service/Tests, PlannedWorld, full contexts; 11 native tests executed/passed, 0 failed, exit0; combined validation pending |
| 4. Initial defenses | Implemented; focused native tests next | 1; compatibility with 2/3/protected reservations | Defense registry/transaction/service/catalog/commands/tests; 8 tests pending |
| 5. Combined validation/release | Pending | All four implemented and independently tested | Preserve both existing namespaces; add new native coverage; one JAR/package/checklist |

## Checkpoints and next step

Initial status checkpoint: `1dcc316` (verified inputs and tracked subtasks); subsequent
checkpoints will identify tested changes, exact commands, counts and commit IDs.
Next: implement reusable precise construction hazards and revise only deliberately
broad occupancy assertions with stronger preservation and real-obstruction coverage.
Only one agent edits shared transaction/command files or runs writable native worlds.
Independent agents audit assets and review code without editing the checkout.

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
- Original retained 50 and public27 11 namespaces are running sequentially with strengthened real-obstruction/recovery assertions; no result yet. Logs `/workspace/integration-evidence/entity-regression/`.
- Independent input audits: Producer R2 186/186 internal hashes, R5 333/333 hashes, all 18 house signatures and 12 branch routes passed. New pinned resource loaders/exports are staged separately in working files; no feature implementation completion claimed.

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

Explicit mappings are in DefenseCostCatalog: authored wall6/7/end-cap/checkpoint-wing connector modules use the short band; wall14/straight16 the long band. Footing/support cells bundled within each module cost no extra. Unknown/unmapped IDs refuse. Cost is journaled; current price edits never reinterpret recovery/refund. Separate money and material shortage tests are pending.

## Producer R2 verified checkpoint

`/workspace/integration-evidence/producer-final-focus/native.log`: 9 executed / 9 passed / 0 failed, exit0, including 40-tick exact native geometry and inventory/payment checks on both future-upgrade sources. Command `gradle runGameTestServer -PgameTestNamespace=catancraft_producer_r2 --offline --no-daemon --max-workers=4`.

Initial focused attempt6/9 passed: one test assumed all glass was clear (approved source also has light-blue stained glass), plus underground/boundary fixtures caused support checks. Fixtures moved to isolated sky contexts without modifying authored states. Retry7/9 passed but synthetic fence reconnection checks still refused future purchases: the unchanged original R1 L5 design has authored disconnected boundary fence states next to added andesite. The transaction's inherited UPDATE_KNOWN_SHAPE/no UPDATE_NEIGHBORS policy intentionally does not request those neighbor writes. PlannedWorld now models actual caller flags, keeps unconditional survival/gravity checks, and defaults strict otherwise. Assets/hashes/counts unchanged; delayed native targets passed. Additional flags-policy/support regressions are queued with combined validation. Ordinary later edits can recalculate vanilla connections and must still meet exact guarded source checks.

New runtime R2 flags are absent from legacy journal descriptions, preserving old pins/tokens and pending R1 interpretation. New revisions and immutable source contexts are separate. Explicit `/catan producer exterior <city> confirm` performs a free correction at the actual paid tier; normal upgrades choose corrected targets and recognize either source revision. Existing current inventories and historical journals are preserved.

## Six-house verified checkpoint

House namespace `/workspace/integration-evidence/components-retry/catancraft_houses.log`: 11 executed / 11 passed / 0 failed, exit0. Command `gradle runGameTestServer -PgameTestNamespace=catancraft_houses --offline --no-daemon --max-workers=4`. All12 routes exercised, both orientations, mixed recognized whole-house branches, exact source signatures, actual current book/inventory NBT through resumed/reversed recovery, corrupt/drift/foreign source refusal, pendingAPPLY refusal, durable physical-save window, native delayed targets. No current server/player world was used.

Files: R5HouseRevisions/Transaction/Service/Tests.java and pinned `r5-house-contexts.json.gz`. Full13x47x11 YZX canonical contexts, strict R1/R2 sources, one independent free six-house history. Existing R5 public groups/civics unchanged. Source snapshot never pasted over checkout.
