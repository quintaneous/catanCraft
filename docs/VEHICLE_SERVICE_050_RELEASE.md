# CatanCraft 0.1.50 — combined solo-test update

Source: `715a22786c4bc050814b6e5b6130084a950f525c` on `chatgpt/combined-shop-acceptance`. JAR: `catancraft-0.1.50-vehicle-service.jar`, **3943851 bytes**, SHA-256 `f83d46140cfb5d8055072db25a4ac43ff075351832a709d1b37e6ec08ecf7440`. This is the combined owner test build, not a claim of installed-pack acceptance.

## Player-facing changes

The completed City A Vehicle Factory now opens a physical purchase/refueling menu at its crafting-table counter. New purchases at the original factory location arrive along the adjacent road with 25% fuel. Refueling is supported at all three recognized City A factory slots; new deliveries remain limited to the qualified original plot. Park wholly in the factory service lane, or in the original factory's adjacent roadside area, stop and exit before using Add 25% or Fill tank.

Fuel is charged to the nation. Provisional empty-to-full costs are 32 Fuel for a pickup and 80 for a LAV-150. Only the added fraction is billed, with prepaid fractional credit saved per vehicle. Purchase prices and historical receipts are retained; older vehicles are not silently filled. The physical menu reviews the exact vehicle, percentage, charge and credit before confirmation.

The JAR carries forward the TaCZ-only handheld shop and ammunition, weighted monument supply rewards, purchased-vehicle retrieval protection, and accepted 033/034 placement/recovery. SBW still provides vehicles and mounted weapons; its handheld guns/explosives remain disabled. Prices and reward odds remain provisional. The siege foundation is not a new player-facing siege release.

## Validation and provenance

| Evidence | Source | Result |
|---|---|---|
| Combined regression, 33 successful suite/phase invocations | `54091940597e054e8eb5953e280ef46807f67014` | 301/301 native phase executions passed |
| Required generated-flat startup bootstrap | same | 7/7 passed; original fixture preparation contract retained |
| Road-aligned native departures | same | 8/8 passed; 52 initial seat exits plus 8 driver exits after driving |
| Combined TaCZ/SBW native refueling, including nonzero saved credit | `2e02c34ed903bedf29868cd86b310c0d8d86cad0` | 8/8 passed |
| Combined TaCZ/SBW separate-JVM service restart | `2e02c34ed903bedf29868cd86b310c0d8d86cad0` | 2/2 phases passed |
| Combined TaCZ/SBW road departures after setup diagnostics | `715a22786c4bc050814b6e5b6130084a950f525c` | 8/8 road cases passed |
| Standalone Fuel accounting and transaction model | unchanged policy/transaction sources | 20,031 policy assertions and 29 transaction assertions passed |
| Final Gradle build and reobfuscation | final source | PASS; no-source JUnit task is not counted as a test |

The final source adds the fractional-credit GameTest, an opt-in combined-runtime validation script and bounded road-fixture readiness diagnostics to the regression source. An explicit byte comparison found `845` unchanged compiled files; only service/road test classes changed. Production class and resource bytes are identical. The table records the exact source used for each run.

The native fractional test saves a one-unit top-off, reloads the actual ledger with nonzero prepaid credit, uses that credit for a second zero-charge top-off, and replays after consumption without charging or restoring energy. Independent readback confirms the saved receipts and accounting. The separate-JVM service restart restores the same native vehicle and original Fuel debit and releases the held lock only after receipt verification. New native locks block boarding, base ticks, setters and capability charging/extraction while leaving NBT restoration intact.

Eight road departures used real movement packets and natural server physics on the unchanged 18,400-cell factory-over-starter source. Pickup traveled 14.09 blocks and LAV 13.62 blocks before stopping, with native fuel consumption and unchanged heading. This qualifies the specified straight departure and checked seat exits, not all road routes or authenticated-client interaction.

## Retained failures and limits

The first slow-arc attempt at `243e90e8f92976dcd7afbb6b5f376298d19e4b62` failed 8/8 conservative swept-clearance checks near unchanged posts. A later multi-point maneuver at `cf27a62eaa22d797e4cef68f47372d4df852476f` passed 8/8, but required roughly 83–93 seconds of shunting. The final versioned bay uses the road-aligned position instead. No obstacle or assertion was removed to obtain a pass. The first combined-mod road run at `2e02c34` passed seven cases and failed one before vehicle creation, with zero trajectory rows, at the 400-tick entity-readiness setup limit. Its original log and failed proof are retained. The diagnostic retry records pending block/entity chunks and monotonic waiting time, permits up to 900 setup ticks within the unchanged 1,200-tick total test limit, and retains every driving/geometry assertion. That first diagnostic retry also failed before spawning: 900 ticks took only 816.5 ms, leaving one entity chunk pending. The final fixture adds a 25 ms yield per pending five-tick poll (at most 180 such polls); both failed attempts remain retained. Post-spawn movement is unchanged. The short initial wall-clock interval is consistent with asynchronous readiness delay but does not establish its cause. The first 034 startup fixture preparation also refused the new empty reward advisory-lock file before executing any startup test. Its zero-byte lock was preserved separately from the completed synthetic bootstrap, then the unchanged preparer and all three native startup tests passed; the native ledger and all assertions were unchanged. Early dependency/setup and compile failures are retained with the raw evidence; subsequent corrected runs are recorded separately.

Automated fixtures use pinned SBW 0.8.9.2 and TaCZ 1.2.0 runtime artifacts; the final coexistence checks use SimpleBedrockModel 2.6.2 and verify that selection in-game. Exact installed-pack/add-on behavior, authenticated counter/boarding controls, UI readability and price/rarity balance still require owner testing. **SEC-01 crowbar remains UNRESOLVED / HIGH PRIORITY until installed-pack acceptance.** The known optional SBW/Patchouli loot-table issue is not claimed fixed. Existing R7 placement latency limits remain.

No live world or installed mod was accessed or changed. No accepted wall purchase was repeated for owner acceptance. City A North Reinforced, East/South/West Basic, and prior readiness/034 acceptance remain accepted. All construction/map asset bytes are unchanged from 049; delivered 033 source and release-documentation commits remain ancestors. No reset, manual import, multiplayer test or Blender assignment.

## One owner checklist

Use [the focused solo checklist](VEHICLE_SERVICE_050_SOLO_CHECKLIST.md) for one installation, one TaCZ purchase, one vehicle purchase/refuel/crowbar check and one normal restart. Report pass/fail/blocked. Do not install historical checkpoint JARs.

Machine summary: [vehicle50_validation.json](evidence/vehicle50_validation.json). Raw logs, original failed/successful trajectories, native ledger/region evidence, runtime manifests and file checksums: [vehicle50_validation_evidence.zip](evidence/vehicle50_validation_evidence.zip), SHA-256 `bcfee557348f97cff40226f68cd122aec6fce9e773584277864ed63c241e61ad`.
