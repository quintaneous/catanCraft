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
| 1. Entity obstruction | In progress: native geometry/transaction inspection | Verified baseline | None yet; implementation and new tests next |
| 2. Producer Exterior R2 | Asset/contract audit | 1; immutable input verification | None yet; no implementation claimed |
| 3. Six R5 houses | Asset/contract audit | 1; whole-house R1/R2 route verification | None yet; no implementation claimed |
| 4. Initial defenses | Asset/economy/socket audit | 1; compatibility with 2/3/protected reservations | None yet; no implementation claimed |
| 5. Combined validation/release | Pending | All four implemented and independently tested | Preserve both existing namespaces; add new native coverage; one JAR/package/checklist |

## Checkpoints and next step

Initial status checkpoint: recorded in Git history with this document; subsequent
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

No feature blocker established yet. Any unresolved contract/design question will
be recorded here with the exact next step; independently unblocked work continues.
