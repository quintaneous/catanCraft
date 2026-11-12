# ECO-03 / SEC-01 — native vehicle delivery adapter, 0.1.47

`VehicleNativeDelivery47` connects the existing `VehicleDelivery46` journal protocol to actual SBW entities, the authoritative national `CatanSavedData` file and native entity-region storage. `VehiclePayment47` implements the national payment side. Both classes remain package-private; only the explicitly named `fixture(...)` factory constructs an adapter. No management-book offer, public factory counter, legacy command replacement, production resolver or background recovery registration is introduced.

Construction and every unfinished operation require the server thread, an isolated `GameTestServer` with no connected players, the exact marked `controlled-catancraft_vehicle47-run` or `controlled-catancraft_vehicle47_restart-run` fixture, the seed-zero overworld and the pinned SBW runtime marker. The caller must supply an explicit `Authority` that validates the fixture's original facility, source, lease, support, clearance and operation authorization. This boundary is intentionally unsuitable for public activation. An authored facility, all physical exit requirements and the common purchase UI/command authority must qualify separately.

## Original offer and identity

The frozen `native_vehicle47_v1` catalog retains the existing two vehicle prices. A supplied intent with a different catalog, offer, type, native geometry SHA or original price is refused; historical records are never repriced.

| Offer | Native type | Original charge |
|---|---|---|
| Pickup | `superbwarfare:sodayo_pick_up` | $4,000; 20 Steel, 10 Mechanical Parts, 8 Fuel |
| LAV-150 | `superbwarfare:lav_150` | $8,500; 50 Steel, 25 Mechanical Parts, 20 Fuel, 5 Electronics |

`newIntent` generates the operation and entity UUIDs on the server. World identity binds the canonical world directory, seed and dimension, so moving a pending world to a different directory requires explicit review rather than silent adoption. The full 046 intent also retains the actor, nation, facility and bay, fixed fractional spawn/heading, original price and canonical fingerprint. The adapter checks the current original leader/membership, journal identity and loaded block **and entity** chunks. It independently hashes the effective SBW vehicle JSON through the resource manager and checks the registered native type before payment and again before spawn.

All loaded dimensions are searched for a conflicting preselected UUID. Native region readback covers the original bay chunk; it does not prove that an arbitrary externally supplied UUID is absent from every unloaded chunk in every dimension. Fresh UUIDs must therefore originate from the server intent generator, within the supplied authority. An unloaded bay, failed entity-manager load, moved entity or ambiguous spawn attempt never authorizes a replacement.

## National debit and receipt

The receipt `vehicle47_paid:<operation UUID>` stores the complete intent fingerprint in `CatanSavedData.placedMapAssets`. `EconomyCost.charge` and the receipt mutate the same authoritative data object before a single native SavedData save. No sidecar receipt is treated as proof of payment. Readback independently reads `world/data/catancraft.dat`, extracts its `data` compound and requires equality with the entire expected/current `CatanSavedData` serialization. An exact receipt is trusted only with this complete memory/disk agreement. The original nation balance/resources and receipt consequently belong to one serialized ledger snapshot.

Before payment, the 046 journal must durably contain `DEBIT_ATTEMPTED`; before entity creation/addition it must durably contain `SPAWN_ATTEMPTED`, and the original debit must read back exactly. The existing attempt protocol never invokes a second debit or spawn after an uncertain attempt. A callback exception after a verified native save can be reconciled from independent readback; a missing, malformed, stale or partially written ledger holds. No rollback, refund, replacement or inferred success is performed. This is verified logical save/readback ordering, not an assertion of arbitrary filesystem or power-loss immunity. National saves follow Minecraft's native storage implementation.

Payment history is bounded at 4,096 vehicle47 receipts without deleting any earlier receipt. Ledger reading refuses symbolic links, missing/nonregular/unidentifiable files, unstable size/file identity/mtime, compressed input over 16 MiB, decompressed input over 64 MiB and NBT accounting over 128 MiB. Malformed NBT is translated to checked failure. Existing unrelated unsaved national changes cause a conservative hold until a consistent native save exists.

## Native entity and independent region readback

The adapter constructs the real registered SBW entity, sets the already journaled UUID and fixed pose, then applies both existing crowbar protection tags and the new provenance compound **before** `addFreshEntity`:

- `CatanCraftPurchasedVehicle=true` and `CatanCraftVehicleNation=<original nation>` retain the existing purchase protection path.
- `CatanCraftVehicleDelivery47` binds schema, operation/actor/nation/entity UUIDs, world, offer, native type, intent fingerprint, fixed XYZ and heading.

Tags are correspondence evidence, not standalone purchase or exit authority. No caller may promote an unqualified vehicle into a managed purchase merely by seeing those tags. Public exit eligibility requires its own authorized association with the exact operation.

After addition, the server performs its native save. Readback opens the original overworld entity-region file with a read-only `FileChannel`; it never constructs a writable `RegionFile`, loads/imports an entity from NBT, creates a region, allocates sectors or repairs storage. It verifies all header extents and rejects overlaps, invalid lengths, nonaligned or oversized regions, unstable file identity/header/payload and external chunk encodings. Gzip, zlib and uncompressed internal encodings are accepted, with 16 MiB decompression and 32 MiB NBT accounting bounds; unknown encodings and trailing malformed NBT are refused.

The entity chunk must name the exact expected position. Its raw `Entities` list must contain compounds (or be legitimately empty), every entry must have a UUID and valid type identifier, and duplicate UUIDs are refused. The selected saved entity must have the exact UUID, native type, original nation/operation/fingerprint and fixed pose. The complete native saved NBT must also equal the current native serialization, including provider data. Memory presence alone is insufficient. `readEntityChunk` is the same read-only parser/schema path exposed to controlled malformed-file tests; it is not a separate permissive test implementation.

Pending pose comparison uses XYZ tolerance below 0.000001, heading tolerance below 0.0001 degrees and zero pitch. SBW does not serialize its roll state in native entity NBT, so live `getRoll(0)` and `getRoll(1)` must independently return finite zero roll within 0.000001; the pinned implementation interpolates previous/current roll at these endpoints. Missing roll access or a tilted endpoint holds even when all serialized NBT agrees. Ordinary native physics may move a vehicle before settlement; that pending record then holds rather than moving the fixed intent or replacing its vehicle. Once the journal is `COMPLETE`, the 046 core returns settled before invoking the adapter. Later driving, settling, loss, destruction or unload can never cause another delivery.

## Validation boundaries

`SavePoint` hooks allow controlled interruptions around ledger save, entity addition and entity save. They do not bypass the actual native debit, UUID assignment, protection tags, save or readback. Separately owned tests cover original prices, interruption/recreation boundaries, native save correspondence, wrong provenance, movement/loss, malformed region records, and a separate-JVM fixture that preserves native world/region/journal files. Only the root validation checkpoint records actual executed counts and outcomes; this implementation contract does not itself claim a successful build or native test.

These fixtures do not establish owner modpack/client acceptance, authored factory activation, full native seat/packet exit safety, multiplayer behavior or complete crowbar-exploit closure. SEC-01 remains **UNRESOLVED / HIGH PRIORITY** until its complete acceptance contract is met. Existing 033/034 purchase recovery, accepted City A wall designs, TaCZ-only handheld acquisition, installed mods and live worlds remain outside this code-only adapter change.
