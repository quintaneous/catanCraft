# ECO-03 — vehicle service source audit

This is a read-only source review, not a native test pass or installed-pack acceptance. Native validation results must be recorded separately against the exact final source.

## Pinned provider energy paths

Reviewed the SBW 0.8.9.2 source archive directly. SHA-256: `06b4bc749e540879265e3f3829c6c242da81512b19a80de0fbd9b089847893d5`.

| Source member under `com/atsuishio/superbwarfare/` | SHA-256 |
|---|---|
| `entity/vehicle/base/VehicleEntity.kt` | `11b33524a9da7dacd02e3d5d448c5869de0c357e47cc3d6699e2a9352c252392` |
| `capability/energy/VehicleEnergyStorage.kt` | `7e4b507f61f46fad9bad2b8534109a672ca078f6ddd8f68d129e0217685bfa7b` |
| `capability/energy/SyncedEntityEnergyStorage.kt` | `8b7f7e7a848bb0c43f7b497313fd15f0ecdc40875be91655d2562f856ba12172` |
| `block/entity/ChargingStationBlockEntity.kt` | `42bc4de26cb862f06094b39d59a875abe61605ef9e14a42d6ccc4ef5d8424145` |
| `block/entity/CreativeChargingStationBlockEntity.kt` | `71269eb53c994734fbaea38aa1a36ea253068666fefd15a70fff2856f70d5400` |

Vehicle inventory battery charging calls the native vehicle energy setter. However, nearby normal and creative charging stations obtain the vehicle's Forge energy capability and call its `receiveEnergy` directly. That capability is the native `VehicleEnergyStorage`; its inherited implementation modifies both its internal field and synced entity data. Vehicle `consumeEnergy` similarly calls storage extraction directly.

Consequently, canceling vehicle ticks and the vehicle `setEnergy` method alone does **not** prevent energy changes during a held refueling operation. The service implementation must also guard the vehicle storage `receiveEnergy` and `extractEnergy` paths. A denied receive must return zero, so the station does not consume source energy for a rejected transfer. The authorized service mutation needs a tightly scoped server-thread permit that is cleared even when persistence fails.

Native deserialization restores the original entity energy; blocking that path would prevent correct recovery. Direct `SyncedEntityEnergyStorage.setEnergy` calls found elsewhere in the pinned provider are for DPS-generator entities, not the reviewed vehicle paths. This source inspection does not certify arbitrary add-on code or different SBW versions.

Specifically, `VehicleEntity.readAdditionalSaveData` restores an integer Energy tag through `energyStorage.deserializeNBT`; the synced storage then assigns its internal field and synced data directly. Guarding receive/extract and the vehicle setter should therefore leave normal native loading intact. A locked-vehicle save/restart test is still required.

## Required persistence review

The nation ledger and vehicle entity save are separate durable records. A held attempt cannot infer successful delivery merely from current energy or restore an old target after fuel consumption. Exact operation receipts must bind the original quote and identity. National Fuel, fractional credit and debit receipt must be saved together; added energy and its receipt must be saved together. Repeated confirmation/recovery must verify those receipts and never repeat an ambiguous mutation.

An operation reservation must survive restart and prevent both boarding and native charging while unresolved. A client-created session, a registry identifier alone, or a boolean supplied by a UI must not grant service authority. Current nation ownership, authentic physical counter reach, completed facility source, loaded service space, stationary vehicle and absence of active construction/siege remain server checks.

Review findings raised against the initial implementation:

- Completion and lock release are separate writes. A crash after the completion marker but before durable unlock must leave the original operation visible/recoverable. Recovery must verify debit and entity receipts, then finish unlock; it cannot merely return early because the completion marker exists.
- Purchase recovery must compare the original bay's immutable identity without first treating the original, already-spawned vehicle as a foreign bay obstruction. Availability validation may ignore only the verified original vehicle identity, never an arbitrary entity supplied by a caller.
- Capacity must be checked against actual native `getMaxEnergy`, as well as the pinned source definition, before a quote or mutation. The tariff cannot silently assume a data-file value when effective instance state differs.
- The pinned vehicle overrides `baseTick`, not `tick`. Packaged `javap` output confirms the production name `m_6075_()V`; a non-remapping injection into `tick()V` would miss. Both the development and production method names need native verification.
- New purchase delivery must retain the prior adapter's all-dimension UUID collision check, read-only saved target-chunk check, purchased/nation tags and complete native pose checks. A same-UUID entity or matching receipt alone does not prove safe delivery or retained crowbar protection.
- A failed durable unlock must restore the conservative in-memory reservation. Otherwise a save/readback failure after setting the memory lock to `none` would expose the vehicle to driving/charging before release had been verified.

Subsequent source recheck observed fixes for all listed findings: capacity verification, visible recovery of completed-but-locked operations through a shared receipt-checking helper, separation of immutable bay identity from occupancy, corrected `baseTick`/`m_6075_` injection targets, retained all-dimension/saved-chunk UUID guards, purchased/nation/receipt identity, finite position/yaw/pitch/both roll endpoints, and conservative in-memory lock restoration if durable release fails. Runtime tests still determine readiness; these source corrections are not test results.

The test sources now exercise the real native refuel/purchase adapters with only physical authority substituted inside exact marked GameTest fixtures. Both pickup and LAV purchase paths are included. The shared completion helper is exercised with a completed journal and an active lock, including a conflicting paid receipt refusal and replay after consumption. Separate seed/verify JVM tests preserve actual ledger/entity-region state, lock and original operation receipts. These statements describe test coverage, not test execution. The final combined validation must record actual counts and failures against its exact source.

The initial UI source binds physical sessions to the exact connected player, uses server-generated quote IDs, clears the visible quote before confirmation, and revalidates the factory for review/confirmation/recovery. This is source review only; client UI/native interaction still requires runtime verification.

## Existing retrieval protection

The existing `VehicleCrowbarGuardMixin`, `CrowbarHelperGuardMixin` and `NationVehicleProtection` are retained during this review. New purchased vehicles must carry the same purchased/nation tags so those guards continue to recognize them. This review does not close SEC-01: the crowbar exploit remains **UNRESOLVED / HIGH PRIORITY** until the installed-pack acceptance test passes.


## Final native evidence

The source findings above are now backed by native service, capability-lock and separate-JVM restart runs, including combined TaCZ/SBW with the selected SimpleBedrockModel 2.6.2 runtime. Independent inspection verifies nonzero saved fractional credit across two fills and replay after consumption. Raw captured ledger/entity readbacks are distinguished from entities still present in the final region files. See [release evidence and exact source provenance](VEHICLE_SERVICE_050_RELEASE.md). This does not establish authenticated-client or installed-pack acceptance.
