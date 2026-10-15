# Resource producer R1 transaction — candidate v0.1.26

The accepted v0.1.25 Town Hall implementation and its 36 native tests are unchanged.
Farm, Lumberyard and Quarry use territory producerLevel, not Town Hall level or
BuildingType purchases. The unchanged existing resource-upgrade cost is charged
once for the territory, after all applicable plots pass preflight and sparse writes.
Strategic Iron/Oil and Coal/Copper behavior is unchanged.

## Assets and placement
All 15 bundled files retain the delivered compressed bytes and SHA-256 pins,
including the three immutable L1 references. Each has 45,167 cells, original
plot/anchor and 0/180-degree rotation. Twelve adjacent deltas were independently
compared cell-for-cell against the delivered contracts. A paid higher-level L1
world may receive a direct exact-source-to-paid-target sparse retrofit for no cost;
a normal next upgrade may combine visual catch-up with that single next-tier charge.
Full read context is checked, including unchanged deep Quarry cells. Only changed
cells must be within authorized city write bounds. This does not clear a whole plot.

## Preserved and dynamic state
Barrels (6/5/3), their full current NBT, farmland, crops and source water are never
in the mutation mask. Legal barrel-open, crop-age and farmland-moisture values
are compared narrowly and retained. Typed normal door/gate operation and native
neighbor-derived states use the existing allowlist; missing crops/doors, altered
fluids, material/facing changes and foreign block entities refuse. New designs
cannot introduce inventory-bearing or fluid cells into the write mask.

## Transaction and recovery
All applicable plots preflight together before any write or payment. A distinct
sealed actual-before journal is flushed, atomically renamed, then read back. It
binds world, owner, dimension, city, source/target hashes, anchors, paid levels and
exact cost. The current inventory snapshots are audit evidence, not permission to
restore stale inventories. Unchanged cells and an outside shell are read back;
new item drops, occupancy and ledger changes refuse. Only sparse cells are written.

States: PREPARED -> APPLYING -> PHYSICAL_COMPLETE -> COMMITTED. ROLLBACK uses
actual-before states in reverse order, ending ROLLED_BACK. The physical save is
flushed before committing economy. The transaction receipt, paid level and visual
tokens are persisted in the SAME CatanSavedData record. Resume uses that receipt,
never infers payment from a current balance; refund is also receipt-guarded and
exactly once. Later legitimate inventory edits survive rollback. Older histories
remain immutable; superseded transactions cannot be replayed. Rolled-back purchases
can be attempted through a new journal, not by deleting prior history.

Startup on the authorized R7 world reconciles existing incomplete journals only.
It never initiates a purchase or new visual retrofit. Unknown state/owner/corrupt
history stops with journals retained. If automatic recovery refuses, the explicit
resume/rollback controls remain available. Partial block writes are therefore
recorded pending work, not a successfully completed trio or a paid upgrade.

## Entry paths
Existing `/nation upgrade producer a` and the management dashboard route to the
same core transaction after unchanged leader/ownership checks. Read-only
`/catan producer status a`; zero-cost `/catan producer retrofit a confirm` for an
already-paid level. Operator recovery: `/catan producer resume a confirm` or
`/catan producer rollback a confirm`. The last two are not routine acceptance tasks.
Prebuilt L1 producers can be adopted without block writes; activation and refresh
cannot silently repaste an upgraded producer back to L1. No loose-asset import or
new configuration is needed. The same existing R7 authorization boundary applies.

## Test scope and limits
Fourteen new native tests use actual delivered bytes and the production transaction:
all pins/deltas/applicability, both rotations, grouped L1-L5 charging, free paid-L5
retrofit, duplicate no-op, actual inventory/crop state, reverse rollback, interrupted
writes, paid/refund receipt save windows, corrupted journals, foreign identity,
last-plot conflict, missing barrel, funds, occupancy, authorization and delayed L5
state checks. Their status is established by CI evidence, not this document.
Saved-data reload and injected exceptions are not separate-process power-loss
simulation or live-world approval. Owner movement/lighting/appearance and modpack
acceptance remain pending. No multiplayer tests are requested. Preserve developed
R7 City A and accepted Town Hall/Commercial/Logistics results. Crowbar #6 and general
older prestige purchase recovery remain unresolved. No R5/defense/terrain/pavilion
changes are included.
