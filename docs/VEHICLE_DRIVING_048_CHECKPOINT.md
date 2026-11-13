# ECO-03 — 0.1.48 native driving checkpoint

Continues [validated 047](https://github.com/quintaneous/catanCraft/blob/a449d25c4e71ab6fb4ea09cbe357e6e0fe4e417a/docs/VEHICLE_NATIVE_047_CHECKPOINT.md) and [046 geometry/delivery](https://github.com/quintaneous/catanCraft/blob/e92b6f8e7ad6072e9a5f670beaf228c0d69a3b1c/docs/VEHICLE_DELIVERY_046_CHECKPOINT.md). This is an investigated native control baseline, not a completed driving route or public shop activation.

## Exact validated source

Source `e7c41e40071027ee85d3f1bc2ba1c0ce8bdba528` (tree `c94a2b81bbd688fa911f85989fc49005bec65de5`) on `chatgpt/combined-shop-acceptance` built and passed **122/122 phase executions, 120 distinct scenarios, zero failures** on its first run, [43515762036](https://github.com/quintaneous/catanCraft/actions/runs/43515762036). Four new driving cases and all 118 retained 047 phases executed. The downloaded archive, all 538 source files, source/version manifests, actual trajectories and retained native recovery evidence passed independent verification. All 682 pre-existing 047 compiled classes, 244 data entries and the exact mixin configuration remain byte-identical. Directory entries are included in the data-entry count.

| Artifact | Exact value |
|---|---|
| JAR | `catancraft-0.1.48-native-driving.jar`; 3,788,965 bytes |
| JAR SHA-256 | `a001c1c6f2ad76e7f660e8cce1f0f1ef24ecf55da91d6e9856650f7133bad413` |
| Actions artifact | `14080763670`; 8,727,880 bytes |
| Archive SHA-256 | `fe4fbecce2f368514c3297c5aef440230e432b5d12e6c609798e98980b676ddf` |
| Runtime manifest SHA-256 | `6e4424ef5a564609dc893c5d9d1ff3efcc7b897d8b9ae9a9acf96d7c331c9452` |
| Retained evidence | `vehicle48_run1_evidence.zip`; 498,928 bytes; SHA-256 `2f7560af6bb88179965e7897a3ab0f00c891a78f32d310b372b9e8711082137c` |

[Machine audit](evidence/vehicle48_validation.json), [raw logs/native proofs/trajectories](evidence/vehicle48_run1_evidence.zip), [independent verifier](../tools/vehicle48/verify.py). The retained bundle excludes the duplicate source ZIP and JAR; their exact identities are above. No failed build/native attempt occurred for this source. Reaching the authored boundary is a **route limitation**, not a failed assertion in these explicitly bounded observation tests.

## What was actually observed

Real registered pickup/LAV entities, a noncreative first-passenger fake player, the provider's actual movement-packet handler and ordinary server entity ticks were used. No post-spawn vehicle position, heading, velocity, gravity or engine-power overrides were applied. A declared one-million-unit synthetic energy seed powers each disposable fixture. Inputs were neutral for 20 observations, forward until 12 blocks displacement, then forward-left; observation ends at the authored boundary or the bounded tick limit. [Implementation contract](VEHICLE_DRIVING_048_CONTRACT.md), [pinned source audit](VEHICLE_DRIVING_048_SOURCE_AUDIT.md).

| Vehicle | Authored orientation | Native ticks | Maximum displacement (blocks) | Heading change | Native energy used |
|---|---:|---:|---:|---:|---:|
| sodayo_pick_up | 0° | 64 | 18.67844 | 6.20346° | 510 |
| sodayo_pick_up | 180° | 64 | 18.67842 | 6.20345° | 510 |
| lav_150 | 0° | 72 | 19.90594 | 10.82501° | 1603 |
| lav_150 | 180° | 72 | 19.90590 | 10.82491° | 1603 |

All four runs stopped at `AUTHORED_ENVELOPE_BOUNDARY`; none completed a 90-degree departure turn. All observed horizontal-collision flags were false, but that is not a continuous swept-clearance proof. The full 18,400-cell authored composition, including posts/lanterns, matched unchanged after each run. Independent audit recomputed input transitions, native/server tick progression, displacement, heading/steering, energy, full native-part bounds and boundary endpoints from raw JSON. Neutral-input horizontal drift stayed below 0.1 block before propulsion. All four had genuine motion, steering and native energy consumption.

**Full bay qualification remains false.** The tested held-forward-then-left sequence runs out of qualified space while still turning only 6–11 degrees. That does not prove the authored bay impossible: slower/braked maneuvers, steering preparation, reverse/multipoint turns and both road directions remain untested. Do not move/remove posts, widen pavement, invent cleared terrain or turn the earlier stationary-yaw geometry result into a driving claim.

## Purchased-vehicle energy gap

[Read-only 047 energy evidence](evidence/vehicle48_energy_readback.json) confirms zero native energy in ten saved snapshots, seven remaining base vehicles and two restart-final vehicles; these collections overlap. Both current native adapter and legacy procurement create vehicles without filling native energy. The national purchase Fuel cost has no implemented conversion to native energy. This is an unresolved delivery/refueling contract, not permission to reprice old purchases or silently add free energy. The 048 synthetic energy seed must not be represented as normal bought-vehicle readiness.

Pinned SBW source supports charged energy-capable inventory items and nearby charging stations, but normal player access, costs and integration have not been qualified. See the source audit for the precise paths. No charging implementation, production energy grant or owner refueling test is introduced here.

## Next bounded work and retained constraints

Test deliberate low-speed/braked steering and both departures on the same unchanged authored source, then establish the vehicle delivery/refueling contract and production facility authority before physical counter/common-command wiring. Authenticated-client and installed-pack acceptance remain separate. Keep one combined playable owner build and focused solo checklist; no intermediate owner installation is requested.

No live world or installed mods touched. Preserve accepted City A (North Reinforced; East/South/West Basic), 033/034 recovery, TaCZ-only handhelds and deferred SBW explosives. No reset, manual import, accepted-purchase replay, multiplayer or Blender assignment. Prices and economy timing remain unchanged. SEC-01 crowbar remains **UNRESOLVED / HIGH PRIORITY** pending installed-pack acceptance.
