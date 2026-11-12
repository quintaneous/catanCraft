# ECO-03 / SEC-01 — durable vehicle delivery protocol, 0.1.46

`VehicleDelivery46` implements an **unregistered entity-delivery backend** with a real append-only sealed journal and an explicit native adapter boundary. It does not replace the legacy procurement command, register a factory counter, debit an actual nation, spawn a native vehicle or claim native entity-storage durability. Public activation still requires the qualified bay, actual ledger/entity adapter, independent native storage readback and restart tests, then the shared UI/command gate. No existing price, purchase record, inventory or world is migrated.

## Immutable operation

Each intent binds a nonzero operation UUID, actor UUID, nation UUID and preselected native entity UUID; explicit world identity; original offer/catalog identity; the complete `VehicleBay45.Bay`; and original immutable `ShopPurchase40.Price`. The bay includes exact vehicle type, facility/plot/dimension/nation/revision/source hash, anchor/rotation/counter/level, native geometry identity, fractional spawn, heading and full support/clearance/boarding/departure masks. Only the existing pickup and LAV offer/type pairs are recognized. The core preserves the price supplied by the authorized quote; it does not introduce a new price catalog or reprice historical records. Current-offer authority and original prices remain the later adapter's responsibility.

Strict NBT schema rejects unknown fields/types, unsupported schema/phase, malformed identities, empty/contradictory/unbounded masks and nonfinite spawn coordinates. Decode reconstructs immutable records and copies masks/prices through the existing validated constructors. The operation fingerprint uses a type-preserving canonical byte encoding with sorted compound keys and sorted cell lists; it is independent of map insertion order and compressed-file byte ordering. Phase changes do not change the fingerprint.

## Durable order and conservative recovery

| Phase | Permitted next work | Recovery rule |
|---|---|---|
| PREPARED | Verify current physical authority/source and no prior debit/entity, then append DEBIT_ATTEMPTED | No money/entity mutation before the attempt marker is read back |
| DEBIT_ATTEMPTED | A fresh successful transition may invoke one debit-and-save callback | A resumed attempt never invokes debit again; exact durable debit evidence advances, anything else holds |
| PAID | Recheck original debit, facility and authoritative pre-attempt absence; append SPAWN_ATTEMPTED | No entity spawn before the spawn marker is read back |
| SPAWN_ATTEMPTED | A fresh successful transition may invoke one spawn-and-save callback | A resumed attempt never spawns again; exact durable original entity and debit may settle, anything else holds |
| COMPLETE | Return settled without invoking the adapter | Moved, destroyed, consumed or unloaded completed vehicles are never recreated |

Both attempt markers deliberately accept a conservative unresolved window: interruption after the marker but before its effect can hold a paid or unpaid reservation. This is intentional loss of automatic availability, not evidence that the effect occurred. It is safer than interpreting uncertain absence as permission to debit, spawn, replace or refund again. There is no automatic refund/substitution/alternate-bay path.

A callback that throws `IOException` after successfully saving can continue only when independent readback proves the exact durable result. Silent callback/save failure also holds because readback disagrees. Unknown/corrupt/conflicting payment, unsaved entities, absence in unloaded chunks, moved/gone entities before settlement, and mismatched UUID/type/nation/operation/fixed transform all remain held. The core never adopts an entity observed before its own spawn-attempt marker.

## Journal and native adapter boundary

`FileJournal` is real filesystem persistence in an explicitly supplied authorized directory. Every phase is a separate sealed file `operationUUID.ordinal.dat`; original intent and previous phases remain unchanged. Compare-and-append uses a file lock, `CREATE_NEW` prevents overwriting a previous phase, file data is forced and reread, and directory synchronization is attempted where supported. Missing intermediate phases, changed intents, malformed seals and unknown operation companions refuse without deletion or repair. A torn new file is preserved and blocks recovery. Symbolic-link paths are refused. These guarantees do not claim arbitrary platform/power-loss immunity.

`Environment.guard` must provide current server-thread, world, actor, nation/leader, exact facility/source, pending-work and appropriate bay authority. `Debit.EXACT` must mean the original price and operation fingerprint were saved together with the national debit and agree with current memory; a boolean tag alone is insufficient. `Delivery.EXACT_SAVED` must mean independent native storage verification of the original UUID/type/nation/operation and fixed-bay transform, with purchased-vehicle protection/provenance applied before the entity becomes usable. Native presence in memory alone is `EXACT_UNSAVED`. Unloaded/foreign/ambiguous observations cannot be promoted to authoritative absence. The adapter is intentionally not implemented here; synthetic adapter tests cannot prove actual Minecraft entity-region persistence.

## Validation scope

The separately owned `VehicleDelivery46Tests` exercises serialization, adapter failure/restart boundaries, exact historical identity, callback/readback disagreement, no repeat charge/spawn/refund, immutable masks/prices and actual `FileJournal` reopen/append/corruption behavior in controlled fixtures. Its test count and runtime outcome belong in the root checkpoint; writing this contract is not a build or native pass. Native bay geometry is a separate qualification result. Existing 033/034 recovery, accepted City A, TaCZ-only handheld policy and SEC-01's **UNRESOLVED / HIGH PRIORITY** status remain unchanged.
