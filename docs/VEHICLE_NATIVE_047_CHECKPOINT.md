# 0.1.47 native vehicle persistence and pickup exits

Development checkpoint continuing validated 046. Public fixed-bay purchasing and the combined owner installation remain gated by remaining physical integration and client acceptance.

## Exact source and validation

Exact source `125cc823dfba778397b6d73e24fc7d919d319ed8` (tree `e756a404c4898b67bc798297610cd854cb2caf21`) on `chatgpt/combined-shop-acceptance` built and passed **118/118 phase executions, 116 distinct scenarios, zero failures** in [run 43278587028](https://github.com/quintaneous/catanCraft/actions/runs/43278587028). The downloaded archive, all 525 source files, source/version manifests, native ledgers/Anvil entities, original payment fingerprints, separate-JVM proofs and every selected suite were independently verified. All 661 pre-existing 046 compiled classes and 235 data entries remain byte-identical. The mixin configuration retains all existing entries and adds only the two scoped pickup-exit hooks.

| Artifact | Exact result |
|---|---|
| JAR | `catancraft-0.1.47-native-vehicles.jar`; 3,775,864 bytes |
| JAR SHA-256 | `0da357e09e62697ffbe8f8d8bdc89974db50a33d89d5d5a223c1f1147d6f7b2a` |
| Actions artifact | `13972261958`; 7,820,681 bytes |
| Archive SHA-256 | `5e98f23808802f9d2144b78daabfa531f6ff7f33272c97dbfdb904d9f96d943e` |
| Runtime manifest SHA-256 | `6e4424ef5a564609dc893c5d9d1ff3efcc7b897d8b9ae9a9acf96d7c331c9452` |
| Retained raw evidence | `vehicle47_run4_evidence.zip`; 426,524 bytes; SHA-256 `98158450abaf69c20af594b6e80619867ab0be44af3c6b91127e652cdb7bd673` |

[Machine evidence](evidence/vehicle47_validation.json) and [raw suite logs, native saves/journals and runtime manifests](evidence/vehicle47_run4_evidence.zip). The raw bundle omits the duplicate source ZIP and compiled JAR; their exact source and hashes are retained above.

All 16 native packet exits (eight seats at both authored orientations) used a 0.125-block outward correction and a checked downward landing, clearing the original self-overlap. Unsafe exits retained the passenger and allowed a later retry. All six fixtures actually observed resident terrain before entity loading finished: the direct residency guard refused each pending state, then every leased chunk passed readiness before the existing packet cases ran. This proves the scoped server path in the pinned runtime; it is not authenticated-client or installed-pack acceptance.

Independent raw-save inspection confirms 14 original intents/fingerprints, original prices exactly once, the same two restored vehicle UUIDs, one first spawn from the original paid LAV intent, a moved pending vehicle held, and zero completed-loss replacements. Thirteen malformed region fixtures and three valid controls were preserved and checked.

## Implementation

`VehicleNativeDelivery47` connects the immutable 046 protocol to real `CatanSavedData`, the original national prices and actual SBW entities. A canonical operation receipt and debit share the same authoritative nation file. Original entity UUID, nation, operation fingerprint and purchased-vehicle protection are attached before world addition. Readback checks real entity-region bytes, whole native entity NBT, fixed position/heading/roll and the saved national ledger. Unknown, unsaved, mismatched, moved or missing pending deliveries remain held. Completed losses never recreate a vehicle. Unique nation membership is required. [Native adapter contract](VEHICLE_NATIVE_DELIVERY_047_CONTRACT.md).

The read-only Anvil parser checks header/sector boundaries, aliases, compression, bounded decompression and NBT accounting, chunk identity, entity list element types, every UUID/type, duplicates and file stability. It does not construct a writable region reader, create chunks or import entities. Its absence authority covers the original resident bay chunk and a server-generated UUID; it is not a global inventory of every unloaded chunk. The adapter is deliberately available only through exact controlled test authority; no live factory counter or command is rewired by this checkpoint.

`NativeVehicleExit47` corrects voluntary exits only for marked nation-purchased pickups in the pinned runtime. It checks the actual provider packet, effective definition and all eight native OBBs. It tries four small offsets (0.125–0.5 block) outward from the current seat, checks the full standing body/downward landing sweep, support and loaded block/entity chunks, and refuses unsafe voluntary exits while retaining the passenger. Normal Shift is suppressed by the provider; its dismount key sends the intercepted packet. The accepted exit override exists only around that one stopRiding call, with a scoped Forge pre-removal recheck and cleanup. Ordinary vehicles, LAV, forced removal, ejection and death stay on their provider paths. [Exit contract](VEHICLE_EXIT_047_CONTRACT.md).

No provider data, authored geometry, prices, economy timing or historical records are migrated. Existing purchased tags scope egress only; they do not authorize spending. Optional mixins are skipped without SBW; their exact target signatures are required when SBW is present. The earlier 046 geometric overlap baseline remains unchanged and must still demonstrate the unsafe uncorrected provider exits.

## Selected execution coverage

| Suite | Phase executions |
|---|---:|
| Native delivery, real journal/ledger/entity saves and fault boundaries | 4 |
| Native delivery seed/verify in separate JVMs | 2 |
| Pickup exit packet, all seats, hazards and bypasses | 6 |
| Retained 046 delivery protocol | 18 |
| Retained 046 authored road/provider baseline | 6 |
| Retained published-SBW crowbar protection | 7 |
| Retained legacy procurement seed/verify | 2 |
| Economy reference scenarios | 6 |
| Fixed-bay policy | 8 |
| Economy clock | 9 |
| Baseline | 50 |
| Total validated | 118 |

There are 116 distinct scenarios: each of the two restart scenarios runs twice. Nested boundary/seat probes are not counted as extra GameTests. The new native restart leaves a completed pickup, an original paid LAV awaiting its first spawn, and a moved pending pickup. Verification must restore the same two existing UUIDs, create the one original paid delivery once, keep the moved pending operation held, and refuse to replace the completed vehicle after loss. Natural physics remains enabled. Platforms used for transaction tests are labeled synthetic fixtures; only the exit/046 route suites use the unchanged authored composition.

## Attempt history

| Attempt | Source | Workflow | Actual result |
|---|---|---|---|
| 1 | `8beb2cb8edac42be56ca51886f189bf993f09f1b` | [43273926125](https://github.com/quintaneous/catanCraft/actions/runs/43273926125) | Build failed; zero test phases executed; all 118 unexecuted |
| 2 | `4ede9984bd6fedb97603c1392c57dd63b6536e6f` | [43274625968](https://github.com/quintaneous/catanCraft/actions/runs/43274625968) | Build passed; 4 phases executed, 0 passed, 4 failed; 114 unexecuted |
| 3 | `9d0d1b17b433f6f1b3301e749d2c3f389e7cd380` | [43276658463](https://github.com/quintaneous/catanCraft/actions/runs/43276658463) | Build passed; 118/118 phases passed; superseded by additional exit entity-residency guard |
| 4 | `125cc823dfba778397b6d73e24fc7d919d319ed8` | [43278587028](https://github.com/quintaneous/catanCraft/actions/runs/43278587028) | Final build passed; 118/118 phases passed; independent artifact audit passed |

Attempt 1 called protected `Player.wantsToStopRiding` directly from a test. The correction uses a small test-only `FakePlayer` subclass bridge and includes both an unmounted Shift control and the provider's mounted suppression assertion. No production access or safety assertion was relaxed. [Retained build log](evidence/vehicle47_run1_failed_logs.zip): 3,225 bytes; SHA-256 `e6164d506f9ec2c9b3e08e4be111cefdb5442a25793650252dda865770bafb06`. Original artifact 13968454551: 3,749,729 bytes; SHA-256 `487a8d5a24c6d4725a6980d4ed261c1aa17a22e8e0b9893512841bb17337d9e2`.

Attempt 2 rejected native zero-length entity-region files before initial delivery. All seven retained region files were empty; no delivery intent had been created. The corrected reader recognizes stable native empty files and complete unpadded final sectors. It refuses partial headers, truncated payloads, overlaps and out-of-file allocations, including when querying an absent chunk. Thirteen malformed fixtures and three valid controls retain these boundaries; the official Minecraft 1.20.1 writer was inspected independently. [Retained build/native logs](evidence/vehicle47_run2_failed_logs.zip): 14,860 bytes; SHA-256 `0d7c4fde8a7c541b5278684f3486138fef260a70795af95e696860467fc392eb`. Original artifact 13970210932: 7,516,919 bytes; SHA-256 `996c2a05e227df5b69831805abd878f2eac3ae2cd441b02e41dc6b43691ccf3e`.

Attempt 3 passed the complete selected suite and independent artifact audit: 525 exact source files, all 661 existing 046 classes and 235 data entries verified. JAR: 3,773,275 bytes; SHA-256 `fdaeb525490a64ff8112078cf3def8db4e5be53d0fb72729ebaa076f60b476fb`. Artifact 13971562634: 7,816,208 bytes; SHA-256 `90b5c2e76f02a9b493a6353224917eed29ed67e8f3204f6d055047f07b307850`. [Machine evidence](evidence/vehicle47_run3_validation.json), [retained logs/native files](evidence/vehicle47_run3_logs_native.zip) (416,363 bytes; SHA-256 `cbf1be1ea2f16cb561b6ef7e4b3b8ee9e554dfd79afad01136a691c07b7d1a21`). Runtime packet construction and final indexed exit hooks executed successfully. Review additionally required complete entity-chunk readiness before occupancy queries; the final candidate includes that guard and waits for real readiness in the controlled fixtures. The earlier pass does not by itself certify the later change.

## Remaining boundary

Native transaction durability is separate from final production facility authority, public counter/common command wiring, steering-radius/real driving qualification and authenticated-client interaction. The exit correction is bounded to stationary/upright pickups, loaded supported ground and the pinned provider geometry. Arbitrary other-mod mutation after the final event recheck and installed/add-on pack behavior are not certified. Full arbitrary power-loss guarantees are not claimed.

Preserve accepted City A (North Reinforced, East/South/West Basic), 033/034 recovery, TaCZ-only handhelds and deferred SBW handheld explosives. No live world or installed mods touched; no reset, manual import, multiplayer, accepted-purchase replay or Blender assignment. Keep one combined playable owner build and one focused solo checklist. SEC-01 crowbar remains **UNRESOLVED / HIGH PRIORITY** pending installed-pack acceptance.
