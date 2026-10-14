# TH4/TH5 access-repair readiness

Updated 2026-10-14. **ENGINEERING PREPARATION, NOT A NEW INSTALL RELEASE.**

The owner is keeping the developed R7 test City A on delivered v0.1.24 while Blender repairs TH4/TH5 rear doors, stair landings and unfinished edges. Preserve both approved overall designs. Read the shared `CatanCraft_PROJECT_WORKING_INSTRUCTIONS.md`: recommend the smallest reliable implementation, challenge unnecessary scope, avoid repeated JAR swaps/imports and do not weaken safety to move faster.

## Current authoritative baseline

Delivered v0.1.24 source is `c8991308da4c058155842279b5b4eb2c09742b6b`, JAR SHA256 `57163c3022ad5b2facb042f659a33b388706036c0cb3ff55b8cee888e6fa4b40`. Both halls are visible in owner screenshots; architectural direction is positively reviewed. Rear-door access and top stair landings FAIL. Payment totals, management book/UI and restart persistence are not established by screenshots. Earlier accepted Commercial/Logistics results remain accepted.

New preparation commits add tests and this document only. Production transaction, world writes, schematic files, prices, visual tokens and version are NOT changed. Build artifacts from these preparation commits are CI evidence, not a new release to install. Keep the previously delivered JAR and immutable original assets.

## Work that can finish before Blender returns

`TownHallAccessTests` adds four native Forge collision/support regressions: TH4 and TH5 in each 0/180-degree orientation. It loads all 229125 cells from each hash-pinned original R1 schematic, opens both door halves, and requires reproduction of six blocked rear-leaf approach volumes and ten missing landing-floor cells per tier. The main entrance is a positive control.

In the isolated GameTest fixture ONLY, a deliberately minimal 16-cell test control removes the six obstructing trim cells and fills the ten landing cells. The detector must then show zero blocked rear approaches and complete support through each five-wide top-tread-to-gallery route, sampled every one-eighth block. Reintroducing each defect independently must be detected. Immediate and delayed full-envelope comparisons forbid any unrelated changes and check the management lectern's fixture NBT.

These tests use Minecraft's actual voxel collision shapes for an upright 0.6-wide / 1.8-high body. They are NOT actual player-input tests, complete staircase/whole-interior routes, the final Blender R2 design, economy/journal migration, recovery or owner modpack acceptance. The in-memory control is NOT an asset release or a repair command. No new schematic is exported; no save or installed mod is accessed. Keep the existing 16 state/door regressions unchanged.

## Next integration: repair already-paid halls, not another purchase

Wait for the separate pinned `thall4_access_r2.schem` and `thall5_access_r2.schem` plus sparse diff. Validate them against immutable R1 hashes, coordinates, full envelope and management position. Run candidate-specific native passage/support checks with original defects retained as negative controls. Do not merely substitute R2 files over active `thall4.schem` / `thall5.schem`: existing source validation and old journals refer to R1.

Implement one narrowly scoped visual-revision transaction, not a universal city-rebuild framework. Reuse validated state/door policy and drop-safe write conventions; do not duplicate the economic purchase path. A paid TH4 or TH5 repair changes only its visual revision and explicitly reviewed sparse cells. It must never call EconomyCost.charge, change the paid city level, or add another building purchase.

Required transition matrix for the eventual combined build:
- Existing TH4/R1 -> TH4/R2: zero-cost same-tier repair.
- Existing TH5/R1 -> TH5/R2: zero-cost same-tier repair.
- Already matching R2: idempotent no-op, no extra journal/payment/world writes.
- Future ordinary TH3 -> TH4/R2 and TH4/R1 or TH4/R2 -> TH5/R2: one normal tier purchase, correct source selected from saved visual revision.
- Unknown/partial revision, incomplete purchase journal, foreign replacement, liquid/container or invalid manager: refuse before edits, keep history.

Do not enable construction until the following are implemented and tested:
1. Exact source/revision hashes, same-world/owner/tier authorization, full live-envelope preflight with existing narrow normal-use allowances, protected manager NBT, outside context and occupied edit volume. Unknown r1/r2 mixtures are not automatically accepted.
2. Actual block states and relevant NBT, source/target revision, patch hash, world identity and old purchase-journal references captured in a separate durable before-image journal. Read-back validation before writes. Keep original purchase journals byte-identical.
3. Apply only reviewed changed cells, preserve unrelated current states including open doors, validate target access/full envelope, manager NBT, item drops and protected outside cells. Save only the visual revision after verified physical completion; balances, ownership, city level and building records unchanged.
4. An explicit safe rollback/recovery path for THIS repair transaction: before/after or legitimate journaled intermediate states only; reject unknown third states. Restore actual before-states and prior visual revision without charging/refunding purchases. Retain history; repeated committed apply or repeated completed rollback is a verified no-op. Interruption must never unlock a new paid purchase or overwrite the before-image.
5. Native tests for source mismatch/no writes, exact sparse repair, zero economy mutations, repeat no-op, actual-before restoration, pending restart reconciliation and preservation of old journals/manager. No automatic live-server maintenance on startup.

The existing prestige purchase service still lacks a general interrupted-transaction recovery tool. Do not claim this planning document resolves that. A new repair path must not inherit the same missing-recovery limitation and be labeled production-ready.

## Owner workflow / avoid repeated installation work

No new checklist or JAR swap while Blender works. Keep current city and blocks intact. On return, combine approved R2 artwork, safe same-tier repair, future-upgrade source selection and new tests into ONE release with ONE focused checklist. Do not ask for another TH3->4->5 replay or civic imports merely to correct already-built geometry. The eventual user check should be repair current hall once, walk all doors/landings, check manager/unchanged level and balances, and restart normally.

Defer resource-building and defense runtime integration until this repair has a clear completion point. R8/R9 terrain/ocean remain separate. Crowbar exploit #6 is unresolved high priority; resource art and visual tests do not close it. Do not expand Blender's current repair assignment while it is in progress.
