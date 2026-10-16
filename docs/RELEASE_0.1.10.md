# CatanCraft v0.1.10 — R5 pilot journal safety hardening (TEST-WORLD ONLY)

**Source:** v0.1-foundation; **runtime acceptance:** NOT TESTED. Minecraft 1.20.1, Forge 47.4.10, Java 17. This is a deliberately conservative follow-up to the untested v0.1.9 pilot; it does **not** authorize deployment to the developed world.

## Changes
- R5 journals use **schema 2** and bind the recorded transaction to the resolved server **world save root path**. Rollback on a different save directory is refused, even if city ID, footprint, and config filenames match.
- Schema-1 journals from the previous pilot are rejected by this version rather than treated as cross-world recoverable. If a v0.1.9 test has actually written blocks, **first complete rollback on that original disposable instance using v0.1.9**, then upgrade the *test copy*. Do not delete or edit a pending journal to bypass this safety check.
- Rollback refuses duplicate journal coordinates, empty or oversized journals. Apply/rollback verify the exact resulting block state of every masked cell before marking the journal COMMITTED/ROLLED_BACK. Failed verification retains an unfinished PENDING/COMMITTED journal for review.
- CI now builds on pushes to `v0.1-foundation` as well as existing main/PR runs.
- Corrected v0.1.9 documentation: City A Commercial had **92 expected tree-mask conflicts plus 2 dependency notices**, **no unexplained non-tree collisions**.

## Explicitly unchanged / prohibited
- Only `front_public_park`, `logistics_service_connection`, and `commercial_sidewalk` may be applied, and only with a one-use server-side approval token placed by the tester on an independently backed-up **disposable test-world copy**.
- No normal-world release approval, no R5 house façades, no tree cleanup, no physical Commercial District/Logistics Center, no R4/R6 defense installation, no purchasing or money/resource migration.
- Preflight: exact old states + properties, pinned ZIP, owned city, expected rotation/dimension, loaded chunks, no fluids/block entities/entity collisions. Actual-before snapshots persist PENDING before writes; explicit rollback only on unmodified before/after cells.
- **Path binding is defense-in-depth, not proof that a save is disposable**. A user can still deliberately put an approval file on a live server: never do that. Backups remain mandatory. Neither full neighbor physics isolation nor crash recovery is proven by CI.
- Superb Warfare purchased-vehicle crowbar retrieval **still fails in actual play**; issue #6 remains OPEN. Don't distribute crowbars.

## Acceptance boundary
Java 17 Forge CI compile and packaging are build verification only. No gameplay evidence yet exists for v0.1.9/v0.1.10 pilot apply/rollback. Use the focused v0.1.10 checklist on a disposable server copy; do not repeat previously accepted v0.1.7 solo economy tests beyond a lightweight unchanged-data check.
