# CatanCraft v0.1.18 — R5 rollback drop suppression (SOLO TEST WORLD ONLY)

## Observation
After the v0.1.17 Logistics Center physical install and rollback, user suspects two spruce doors became collectible dropped items. Its 3,511-cell final mask verified but only 3,509 explicit writes were counted; two cells may have changed from neighbor physics and drop items. The exact drop was not captured in server logs and is not yet confirmed as a fact. Prior rollback implementation counted blocks but not newly spawned item entities.

## Targeted source change
BOTH City A Commercial and Logistics pilot rollback engines retain journal SHA, world/save identity, exact before/after block checks, entity occupancy, barrel NBT and empty inventory, existing purchases and the strict full final-mask audit. Only inverse block writes use the Minecraft block flags UPDATE_CLIENTS + UPDATE_KNOWN_SHAPE + UPDATE_SUPPRESS_DROPS instead of prior 2, preventing transient shape updates from breaking two-block door structures and suppressing incidental drops. Installation behavior remains unchanged.

The rollback captures pre-existing dropped item UUIDs within journal-cell outer AABB plus 2 blocks, verifies there are no NEW ItemEntity instances before successful journal COMMIT, and refuses to mark ROLLED_BACK if new drops appear. Any failure retains ROLLING_BACK journal; existing dropped items are never cleared or altered automatically. Counts explicitly separate direct writes, cells already original at preflight, and cells that became original without a direct write during the restoration loop. Unexpected indirect changes are logged.

## Scope / limitations
This is a new version for disposable test clones only, not live multiplayer. It cannot remove objects already dropped in v0.1.17, cannot reset a ROLLED_BACK world journal, and is not a world-item cleanup tool. Forge CI proves compilation, not all modded physics or delayed tick effects. Need focused user regression test for door drops and post-restart persistence on a NEW backed-up disposable pre-Logistics world copy. Do not repeat already accepted v0.1.16 Commercial rollback, R5 preview or R3 economy acceptance. Commercial roof correction and R7 ZIP repair still pending; no R4/R6/R7 install; purchased Superb Warfare crowbar exploit issue #6 remains unresolved.
