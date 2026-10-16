# CatanCraft v0.1.12 — R5 civic staged read-only preview

**MC 1.20.1, Forge 47.4.10, Java 17.** Safe continuation after v0.1.11 user-confirmed City A R5 park apply -> rollback -> 537/537 baseline check **after server restart**. This version makes **no new civic or defense world writes**.

## New OP-only commands
- \`/catan r5 plan a commercial\`
- \`/catan r5 plan a logistics\`
- Any active owned city a-p can be previewed (including 180-degree rotated I-P), but only City A had previous R5 park testing.

These commands verify SHA-pinned original Blender R5 ZIP and compact group-index ZIP, validate active city center/rotation and civic identity, inspect real current world state without loading chunks, and project prerequisite changes in memory *in author-specified order*. Commercial projects cleanup_tree_16_10 then commercial_sidewalk before checking civic write states, supports and the entire remaining above-grade source-air envelope. Logistics projects logistics_service_connection. Duplicate group/civic coordinates, nonmatching current blocks, unloaded chunks, fluids and block entities produce conservative review messages. The preview also counts existing data-only civic purchases without changing/charging them.

All staged dependencies are **virtual**, not installed; a green/zero-issue preview is NOT an installation approval. Strict prerequisites on a previously modified world may fail because the source target expects the pre-R5 baseline. No automatic property normalization for old snapshots is silently introduced.

## Unchanged
- Only the original three one-shot, operator-only, disposable-test-world streetscape groups retain the conditional pilot apply; no tree cleanup, Commercial building or Logistics building apply command exists.
- Previous park rollback journal and v0.1.11 leaf-distance safety behavior are preserved.
- No R6 socket registry, R4/R6 defense purchases, gate changes, house migrations or saved-data economy mutations.
- R3 factories, Town Halls, roads and nation saves remain untouched by the preview.
- Superb Warfare purchased-vehicle crowbar packing issue #6 remains OPEN and a high-priority exploit; do not distribute crowbars.

## Boundaries
The SHA-pinned source contracts were verified offline. Read-only simulation is **not** a replacement for crash-safe grouped block writes, payment reservation/refund, block-entity NBT handling, active-world protected zones, neighbor physics checks, vehicle clearances or runtime acceptance. This release must not be used to authorize construction on the developed/live world.

**Acceptance**: GitHub Actions compilation is separate from single-player gameplay testing. Use the v0.1.12 focused checklist; do not repeat v0.1.7 economy checks or the completed v0.1.11 City A rollback.
