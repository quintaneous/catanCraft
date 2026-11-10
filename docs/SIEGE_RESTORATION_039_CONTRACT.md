# SIE-02 / SIE-03 — bounded structural snapshots, 0.1.39

Development follows validated 0.1.38 (`acd6e906a9cbcec500cf5f4e91b6e1f849d0a0e0` documentation head). This is an unregistered backend: no live siege damage, city profile, weapon hook, command, scheduler, installation or ownership settlement is enabled. Preserve the accepted City A North Reinforced / East, South and West Basic walls, all purchase histories and prior scoped acceptance. SEC-01 remains **UNRESOLVED / HIGH PRIORITY** pending installed-runtime acceptance.

## Saved source and authority

`SiegeRestoration39` captures actual pre-siege block states from an independently approved sparse plan, not the older reusable `CityRestorationService` template. An immutable sealed snapshot binds the actual world root, world ID, seed, dimension, city, siege UUID, original owner and exact plan revision/positions. A companion journal references the exact snapshot SHA-256. Original snapshots and completed records cannot be overwritten to start another siege; archival/new-session policy is future work.

The trusted server Environment must resolve current actor/ownership/phase/plan authority and exclude other city construction/recovery. RESTORE additionally requires all damage sources to have stopped. Every action rechecks this read-only guard, server thread, level identity, current file bytes and the held OS lock's identity. These callbacks are not client permissions. This lock coordinates this backend only; integration with existing construction writers remains the controller's responsibility.

The explicit plan accepts 1–512 destructible cells, at most 4,096 protected reads, a 64×32×64 envelope and the exact six-face context of every destructible cell. Reads may extend two blocks beyond that envelope. No City A boundary is inferred from old restoration boxes or wall bounds.

Restorable sources are currently only stone, stone bricks (ordinary/cracked/mossy), cobblestone, bricks, andesite/polished andesite, smooth stone, iron block and white concrete. This intentionally does not cover the complete accepted wall art, modded blocks, stairs, gates, fluids or writable block entities. Supporting those requires their own state/physics policy. Source capture saves the physical world first, then rechecks all captured data before sealing it.

Protected reads preserve exact block state and full block-entity NBT, including inventories/books. They are never recreated or rolled back. Changed inventory or protected state blocks restoration, leaving the changed content intact. A future consumable/ammunition policy must replace this conservative hold explicitly; this implementation does not mint replacement inventories or restore paid ledger entries.

## Lifecycle and failure boundaries

1. CAPTURED: retained immutable source; no damage is permitted by this backend.
2. ARM: persist one frozen nonempty subset of approved destructible cells before any future damage. Identical repeat requests are inert. This records a prerequisite, not an active weapon hook or a guarantee that arbitrary destructive effects are safe.
3. ARMED: only exact original state or ordinary AIR is recognized in that subset. Unknown blocks, cave air, fluid, block entities, damage outside the subset and changed protected NBT refuse.
4. RESTORING: explicit authorized start seals restoration intent before writes. Whole-plan checks, loaded entity residency, ordered native support simulation and existing entity collision/attachment safeguards run before each slice. Each write rechecks source and authority. At most four actual writes use the established no-drop, known-shape flags. No entity is moved, deleted or spawned.
5. Physical chunks/block entities are flushed before advancing progress or recording DONE. Interrupted original/AIR mixtures reopen against the same immutable source and intent; already restored cells are skipped. A save failure closes the handle and retains pending evidence.
6. DONE requires every original/protected cell to match. Repeated opens/start/step are byte-inert with zero writes/saves. Later drift refuses rather than using old completion as permission for repair.

Faults before/after snapshot and journal writes, after a native block write and after a physical save are exposed only to explicitly marked disposable GameTest worlds. Checksums detect corruption, not malicious rewriting. This is a cooperative single-server persistence protocol, not hostile-filesystem security, rollback protection or arbitrary power-loss atomicity. Existing shared journal writer and all prior production classes remain unchanged.

Four-write slices do not imply a frame-time guarantee: whole-plan scans, prefix simulation, serialization and world saves are synchronous and potentially expensive. No latency claim or production city-scale benchmark is made.

## Validation scope

The dedicated workflow builds exact committed source on Java17 / Gradle8.8 / Minecraft1.20.1 / Forge47.4.10. Executed matrix: new snapshot22, retained session38 22, gate35 12 and baseline50, plus one distinct-JVM restart scenario in two phases: 107 unique scenarios / 108 phase executions. All 108 phase executions passed in workflow `42888843335`; independently checked artifacts and source are recorded in [the checkpoint](SIEGE_RESTORATION_039_CHECKPOINT.md).

The restart scenario uses eight mixed structural cells and a protected barrel with actual inventory/full NBT. First JVM saves four restored/four AIR cells. The second JVM checks those physical saved blocks and original snapshot/journal bytes before reopening, restores the remaining four and verifies inert completion. It never copies, reseeds or clears the fixture in the second phase. Synthetic authority and no-drop AIR damage are not connected players or weapon integration.

No user-world changes, multiplayer, forced-crash request, accepted purchase repeats, Blender assignment, shop rollout or price change belongs to this batch. The next siege work is to define exact supported damage/secondary-effect/drop boundaries and join this backend to the saved outcome controller before actual TaCZ/SBW routing. Preserve the approved defender timeout/surrender win routes and existing objective rules.
