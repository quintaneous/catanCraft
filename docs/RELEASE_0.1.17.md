# CatanCraft v0.1.17 — City A Logistics Center physical test pilot

## Purpose and boundary
First test-world-only **physical** R5 Logistics Center install and rollback. Preserves the successful v0.1.16 Commercial District rollback fix. Minecraft 1.20.1 / Forge 47.4.10 / Java 17.

Only City A on an independently backed-up disposable developed-world copy is permitted; live world deployment is NOT APPROVED. No new R4/R6/R7 installs, no R5 roof fix, no new purchases, no economy debit and no duplicate existing-building charge.

## New operator commands
`/catan r5 logisticspilot diagnose` — read-only classification of the world-local Logistics journal and current union states.
`/catan r5 logisticspilot apply confirm` — physical Logistics install, **requires one-use approval file** in disposable world root.
`/catan r5 logisticspilot rollback confirm` — guarded restoration using the unchanged actual-before journal.

## Authorization
Only after backing up and verifying the world is NOT live, manually create in the **world folder containing level.dat**:
`CATANCRAFT_R5_CITY_A_LOGISTICS_DISPOSABLE_APPROVAL.txt`
with exact one-line content:
`I_CONFIRM_THIS_IS_AN_OFFLINE_BACKED_UP_DISPOSABLE_CITY_A_TEST_COPY_ONLY`
The file is consumed before the first write. Do not reuse the old Commercial District approval filename or modify any existing Commercial journal.

## Exact stages
1. `logistics_service_connection`: 158 ordered write cells (validated pinned compact R5 index).
2. `LOGISTICS_CENTER`: 3,353 ordered write cells (validated pinned original R5 delta).
Total 3,511 unique write cells (no overlap), exact City A bounds X=-827..-794, Y=80..95, Z=-708..-675; 24 new empty inventory barrels. All 9 registered R3 factory/Town Hall source-envelope variants remain excluded with safety margins (offline intersections = 0). Existing saved Logistics purchase count must equal **one**; no ledger, nation data or purchase values are mutated.

## Transaction rules
Uses the already tested v0.1.16 Commercial pilot's restricted journal algorithm, but with its own marker and separate immutable world-local journal:
`world/data/catancraft_r5_city_a_logistics_v1.json`
SHA-sealed PENDING journal of every original block state, world save root, source ordered stages and proposed after-states is flushed before voxel writes. Full live old-state, loaded-chunk, no fluid/tile, entity AABB and civic-stage/source-air envelope checks. COMMITTED includes installed new barrel NBT; users must not put items in the barrels or rename them. After restart, a cautious preflight checks all 3,511 current states and refuses to rollback unknown third-state drift, unknown inventory or occupants. Allows **only** legitimate spruce-door open/closed change (all other properties fixed), and vanilla oak-leaf distance normalization where relevant. Retains ROLLING_BACK / ROLLED_BACK history; never overwrites prior transaction journal.

The prior Commercial District static/source ZIPs and runtime safe journal are untouched. The source is not newly authored R5 artwork. Keep existing asset archives byte-identical.

## Explicit limitations
The first Logistics pilot remains UNTESTED in Minecraft until user's solo disposable-world result. Static contract checks and Forge compilation do not prove live-world physics, material interactions, crash recovery, arbitrary block-entity NBT, or all possible R4/R6 protection interactions. One-shot approval is a manual test-world safety control, not production authorization. If a failure occurs, STOP, preserve the Logistics journal and world and send the exact error; do not manually modify blocks or attempt a fresh install. Original Commercial District visual roof defect remains an OPEN Blender art issue. R7 ZIP previously failed transferred-file integrity and is NOT installed. Purchased Superb Warfare crowbar exploit issue #6 remains OPEN.

## Already accepted — do not repeat
v0.1.7 economy; v0.1.11 park rollback after restart; v0.1.12 staged previews; v0.1.13/14 construction+model audits; v0.1.15 Commercial construction and visual inspection; **v0.1.16 Commercial rollback, original terrain restoration and restart PASS (5/5, user reported)**.
