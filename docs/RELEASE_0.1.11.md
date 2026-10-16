# CatanCraft v0.1.11 — R5 park rollback auto-updating foliage fix

**Minecraft 1.20.1 / Forge 47.4.10 / Java 17. TEST-WORLD-ONLY.** v0.1.11 is a focused correction to the v0.1.10 R5 pilot rollback after the user's test: 537-cell front public park apply succeeded, but rollback refused at [-702,84,-825]. In the pinned R5 source this specific cell is newly placed oak leaves from air, authored with distance=1 despite being three Manhattan blocks from the nearest newly placed trunk. Engine-managed leaf-distance changes are a strong probable cause; actual current state is not yet available from the server journal or targeted diagnostics.

## Change
- `matchesInstalled()` recognizes **only vanilla oak-leaves `distance` property drift** for intended-after leaf states; it still requires the **same block ID and exact persistent/waterlogged/other properties**. No other blocks or custom states may drift.
- Apply post-write verification and full-group post-apply verification use the same narrowly defined normalization. Rollback checks EVERY cell before mutating ANY cell; each current state must equal exact original snapshot or the permitted installed state. Third-state blocks and unsupported property changes still refuse.
- Rollback diagnostic now prints actual current canonical state, recorded actual-before, and intended installed-after state to distinguish physics from grief/drift.
- **Schema 2 journals from v0.1.10 remain readable**: they remain world-path-bound, retain durable journal and history, and are neither overwritten nor automatically repaired.
- Does not modify or activate physical civic buildings, tree cleanup, R6, defense purchases, economy, or R5 houses.
- No approval token is necessary for `/catan r5 rollback`, but rollback must ONLY run on the same disposable world copy and journal directory. No one-shot token should be created on the main server.

## Current evidence
v0.1.10 tester screenshot confirmed: `R5 pilot applied 537 exact cells`, journal under `config/catancraft/r5_test_journal/A_front_public_park.json`, then `R5 pilot rollback: refused/interrupted: Rollback drift; unchanged blocks required at BlockPos{x=-702, y=84, z=-825}`. The initial 0.1.10 rollback preflight runs before the restore loop, so refusal is expected to preserve all cells. Exact actual in-world blockstate remains unverified.

## Acceptance
CI build success validates compilation only. User needs to back up the **currently modified disposable test world and journal**, replace 0.1.10 JAR with the 0.1.11 JAR on matching server and client, and rerun the rollback once. The existing journal should show `ROLLED_BACK` and correct original park afterwards. If it refuses, **stop** and share the full error and journal; do not change blocks manually. A deliberately changed *non-distance* property or unrelated block must still refuse in a fresh disposable test scenario before promotion. No multiplayer checks.

**Do NOT deploy to the active server or mark v0.1.11 runtime-accepted merely because GitHub Actions passes.** Superb Warfare crowbar exploit #6 remains unresolved (do not distribute crowbars).
