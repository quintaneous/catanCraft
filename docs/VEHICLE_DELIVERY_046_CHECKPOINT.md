# 0.1.46 vehicle delivery, authored road and pacing checkpoint

Development checkpoint only. Command-free vehicle purchases remain disabled; this is not the combined owner installation build.

## Source and evidence

- Exact compiled source: `04821fe22b4f686519ce3a6b1f78bc581de41495`; tree `24c20a27bf3570580cf62ef1c97e55e2b68fe990`.
- [Final workflow 43267236979](https://github.com/quintaneous/catanCraft/actions/runs/43267236979): **SUCCESS, 106/106 phase executions, 105 distinct scenarios, zero failures**.
- JAR `catancraft-0.1.46-vehicle-delivery.jar`: 3,691,022 bytes; SHA-256 `78b3481e2c79f75ac206d503736897265cd8429f2505cb44e3fdc9c99d05518a`.
- [Artifact 13966468865](https://github.com/quintaneous/catanCraft/actions/runs/43267236979/artifacts/13966468865): 7,162,041 bytes; SHA-256 `50303a877f9499ed5f108b748f9a491bdfc99afe63210469cd011006faffd332`.
- [Machine verification](evidence/vehicle46_validation.json); [read-only verifier](../tools/vehicle46/verify.py).

The downloaded ZIP passed hash/size/CRC/path checks. All 503 source files match the exact commit byte-for-byte; packaged source/version and workflow JAR checksum match. Every selected test exit/count/pass marker, four route observations, 26 seat observations, native view regression, runtime manifest and seven sealed journal files were checked. All 638 pre-existing 045 compiled classes and 230 data/mixin entries remain byte-identical. Later evidence/documentation commits do not alter this compiled source.

## What changed

`VehicleDelivery46` adds an unregistered delivery protocol and real append-only sealed phase journal. Original price, actor/nation, fixed facility, bay geometry and entity UUID stay bound to one operation. Durable readback precedes each external effect. Restarting an uncertain debit or spawn never repeats that effect, substitutes a vehicle or refunds it automatically. Completed delivery never respawns a moved/destroyed vehicle. The native ledger/entity-storage adapter and public counter are not activated. [Delivery contract](VEHICLE_DELIVERY_046_CONTRACT.md).

`NativeVehicleBay46` investigates the pinned SBW pickup/LAV against the actual original plot-1 factory-over-starter composition, including the existing road, posts and lanterns. Six cases retain explicit loaded/source/support/fluid/block/entity boundaries. Four vehicle/rotation combinations examine 117 route poses each: 16-block exit, both 90-degree junction turns with conservative continuous angular bounds, and 12-block departures. Standing passenger bodies are also checked against all native OBBs, separately from road geometry. [Native contract](VEHICLE_NATIVE_046_CONTRACT.md).

The route investigation is bounded authored-source geometry, not steering-radius, vehicle physics, every city/plot, authenticated client or installed-pack qualification. The investigator constructs native entities without adding them to entity storage. Its first-seat boarding/dismount check and all-seat exit observations do not prove all-seat player boarding. No road artwork or provider vehicle definition is changed.

## Native findings

| Vehicle | Routes at 0/180 degrees | Final native exits per orientation | Bay activation |
|---|---|---|---|
| Pickup | Both 117-pose routes passed | All 8 overlap its own COLLISION OBB | HOLD |
| LAV | Both 117-pose routes passed | All 5 clear the checked standing body | HOLD pending remaining gates |

All four cases preserve the authored junction posts, lanterns and road. The first seat's actual native boarding/stopRiding position matches its observed final exit. The pickup finding is geometric overlap in the pinned runtime; it does not claim a tested client trap, driving injury or installed-pack behavior. A successful refusal assertion leaves pickup qualification negative. No native provider dismount patch is included.

The final view regression observed minimum raw dot `0.9999917000262836`, exercising the original failure while the normalized direction and independent OBB checks pass. Invalid/scaled/wrong-direction vectors and a deliberately stale OBB heading are still refused.

## Attempt history

[Failure source/count verification](evidence/vehicle46_failed_attempts.json) independently matches all 503 committed source files for each failed artifact. Each attempt below stands on its own; counts are not pooled into a success claim.

| Attempt | Exact source | Workflow | Actual execution |
|---|---|---|---|
| 1 | `46e1467d6dc40bb70b3fc63a62fbf1044fe8d9de` | [43263279084](https://github.com/quintaneous/catanCraft/actions/runs/43263279084) | Build passed; 18 delivery tests executed: 17 passed, 1 failed; remaining 88 phases did not execute |
| 2, interior-only geometry | `479a12da430771d5da0522434a95230eab65c0a0` | [43264354720](https://github.com/quintaneous/catanCraft/actions/runs/43264354720) | Build passed; 106/106 phases passed, 105 distinct scenarios |
| 3, expanded junction | `b033d4d46f13aa5184ae917ca22aeb10f9c874a7` | [43266012037](https://github.com/quintaneous/catanCraft/actions/runs/43266012037) | Build passed; 24 phases executed: 22 passed, 2 failed; remaining 82 phases did not execute |
| 4, corrected final junction | `04821fe22b4f686519ce3a6b1f78bc581de41495` | [43267236979](https://github.com/quintaneous/catanCraft/actions/runs/43267236979) | Build passed; 106/106 phases passed, 105 distinct scenarios |

Attempt 1 failed `unknown_journal_schema_or_corrupt_bytes_fail_closed` with `Unexpected end of ZLIB input stream`. The synthetic journal adapter used compressed NBT directly; its checked-I/O contract now translates a native `ReportedException` only when its direct cause is `IOException`. Other failures propagate. Production code and rejection assertions were not weakened. This diagnoses the native wrapper from its behavior; the first log did not include a stack/class. [All three retained raw logs](evidence/vehicle46_run1_failed_logs.zip): SHA-256 `ab92e590729a910d98ba7389724daf751b4d3ffd8b685e79c3a255a21c67d6dd`, 125,105 bytes. Original artifact 13964183447: SHA-256 `f991b6a6f4ad2b409fd2271cafe771d4d1f1638941dc694bd9f0da70f9900405`, 7,085,265 bytes.

Attempt 2 was independently downloaded and verified against all 503 committed source files. Its earlier geometry scope checked only the factory interior and 8-block straight internal departure; it did not check the external road or own-vehicle passenger overlap. JAR SHA-256 `d5800daf1f1a1ba2ecb6ad5f16c77043b58e3a1cbfeda714b3e094c4abcd4433`, 3,683,118 bytes. Artifact 13964629798: SHA-256 `b7f4b61ae64f558de62fa9c20b1f802f9a9e99e8b003e3113fff6f7e5b78c896`, 7,147,502 bytes. [Machine evidence](evidence/vehicle46_run2_validation.json). That pass does not substitute for the expanded final junction/dismount execution.

Attempt 3 passed all 18 delivery cases and four of six native cases. Both unrotated vehicles completed their 117-pose routes; all eight pickup final exits intersected its native COLLISION OBB, while all five LAV exits cleared it. Both 180-degree route cases failed the native-forward heading check. [All four retained raw logs](evidence/vehicle46_run3_failed_logs.zip): SHA-256 `f93602a5aa8167170b95e6943ffd04f126bb0ee29ee1990a6091c0f754c2a487`, 135,037 bytes. Original artifact 13967210113: SHA-256 `c9a49993d655f7e270ee9db084afbd43d155dc346f751134f606c88d93e388e1`, 7,114,491 bytes. The inherited vanilla sine-table view vector at yaw 85 degrees is slightly shorter than unit length. A separate float32 reproduction yields raw dot 0.99999170003 but normalized dot 0.99999999979; the original direction threshold is 0.999999. This explains the asymmetric failure without claiming the failing log recorded those numbers. The correction normalizes direction under the unchanged threshold and adds a finite/near-unit guard, diagnostic values, twelve actual native heading checks and wrong-view/stale-OBB refusal checks. All OBB and clearance assertions remain intact. Attempt 4 subsequently proves this correction with the actual native view regression and all four route observations.

## Coverage boundaries

| Selected suite | Phase executions |
|---|---:|
| Vehicle delivery protocol, adapter interruption and real journal | 18 |
| Native authored route/dismount/hazard/source checks | 6 |
| Retained published-SBW protection | 7 |
| Retained native vehicle procurement seed/restart verification | 2 |
| Actual-engine economy reference scenarios | 6 |
| Fixed-bay policy | 8 |
| Economy clock | 9 |
| Baseline | 50 |
| Total | 106 |

The two procurement phases are one scenario in separate JVMs. The new delivery tests use independently reloaded synthetic adapter state, not native entity-region persistence. The real journal case retains five consecutive phase files for one operation and two gapped files for another; unknown companion bytes remain preserved/refused. The seven sealed files and one foreign companion are retained in the artifact. Old procurement restart tests exercise the existing path, not the new delivery adapter. The earlier 044 220-phase result remains separate; it was not wholly rerun in 046.

## Economy pacing findings

[Reproducible offline comparison](ECONOMY_PACING_046.md) uses the unchanged 045 model and source-derived costs. Its managed two-player 41-action queue takes 265 economic cycles: 66 h 15 min at the current 15-minute interval, 22 h 5 min at a hypothetical 5 minutes, and 4 h 25 min at a hypothetical 1 minute. Starter and vehicle milestones, unchanged debits/final balances and fixed real-hour ammunition spending are retained in [machine results](../tools/economy46/results.json). Shorter cycles also make fixed combat resupply cheaper relative to income. These are illustrative alternatives, not chosen campaign lengths or approved price/cadence changes. There is no new native runtime cadence setting.

## Remaining work and owner boundary

Production activation requires a safely qualified vehicle/bay, native ledger and entity-storage adapter with independent saved UUID/provenance evidence across restarts, then the physical counter and common command gates. Any observed unsafe native exit stays held; a passing refusal assertion is not an approved pickup bay. Steering/physics and authenticated-client handling remain separate. No intermediate installation request or new owner checklist is issued for this backend checkpoint. Keep one combined playable build and one focused solo checklist after the remaining integration.

No live world or installed mods accessed. Preserve accepted City A (North Reinforced, East/South/West Basic), 033/034 recovery, TaCZ-only handhelds and deferred SBW handheld explosives. No reset, manual import, multiplayer, repeated accepted purchase tests or Blender assignment. SEC-01 crowbar remains **UNRESOLVED / HIGH PRIORITY** pending installed-pack acceptance.
