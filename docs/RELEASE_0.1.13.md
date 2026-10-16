# CatanCraft v0.1.13 — guarded civic transaction planning / construction audit

## Status and version boundary
Minecraft 1.20.1 / Forge 47.4.10 / Java 17. **PRE-CONSTRUCTION DIAGNOSTIC ONLY**. No civic write commands, no new defense construction, no purchases, no new or modified saved-data state. This is a *development* release for a backed-up disposable test server; never install on the live world merely because CI passes.

Based on the user's accepted v0.1.12 City A read-only staged preview: **Commercial 16,505 comparisons / 0 issues** and **Logistics 18,417 comparisons / 0 issues**, with exactly one previously saved purchase of each. The v0.1.11 City A front park 537-cell installation and successful rollback after restart are accepted and should not be repeated.

## New operator-only diagnostics
`/catan r5 constructioncheck a commercial`
`/catan r5 constructioncheck a logistics`

First executes the full ordered read-only R5 stage preview, checking the signed-in-place pinned Blender R5 sidecar and compact index, active city mapping/rotation, exact loaded current block states, dependencies, civic support and preserved-air envelope. If it matches, it derives a deterministic **union write mask**, preserving each cell's **actual** original before state if a dependency and the civic both touch the same cell. It detects cross-stage overlaps, rechecks current live baseline and builds a SHA-256 digest for review. It requires exactly one saved legacy civic purchase, without mutating the purchase, ledger, ownership or national stockpile.

Before future construction can be enabled, it also checks live block entities and fluids on the proposal mask and tests all living entity **bounding boxes** against the planned non-air construction voxels, reporting obstructions and the count of proposed inventory-bearing barrels (**8 Commercial / 24 Logistics**). Outputs are logged as `R5 CIVIC AUDIT` and capped in chat. A successful diagnostic is NOT approval to install.

## Known remaining blockers
- The R5 civic asset contains **barrels** (block entities). Park's old simplified journal does not preserve arbitrary block-entity inventories or NBT and is not acceptable for these civics. Require a separate journal and rollback design that refuses to erase user items.
- Staged tree cleanup/sidewalk and civic installation need **one crash-recoverable dependency transaction**, per-city lock, durable NBT snapshots, write-stage ordering, rollback in reverse order and power-loss recovery.
- Runtime protected-zone/R3 factory/R4-A/R6 reservations, physics and exact entity clearance must be comprehensive. Today's audit detects occupancy but does **not** declare those protections complete.
- No duplicate-charge logic for new civic purchases is enabled; this precommit audit only checks one existing saved instance.
- Sandbox/different-world journal safety, unknown third states, post-neighbor physics and simulation-versus-real installation remain mandatory before construction.
- Do not remove City A tree manually or paste air-inclusive schematics.
- Superb Warfare crowbar retrieval of purchased vehicles remains **OPEN high priority GitHub issue #6**, not fixed here. Do not distribute crowbars.

## Acceptance
Compile + integrity verification only establish valid mod packaging. Runtime acceptance requires focused City A solo **read-only** diagnostic and verification that no blocks, inventory, treasury or city levels changed. Use the versioned focused checklist; no multiplayer tests.
