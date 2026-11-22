# Vehicle Factory stock and rare monument vehicles

**Design proposal for ECO-03 and MON-01 · 2026-11-22.** A nation should be able to buy practical transport and earn a competitive late-game vehicle through normal production. Monument captures can add a distinctive alternative that other nations can recognize. The proposed split is six factory models, two monument exclusives and 35 definitions held for later consideration. The first playtest should answer whether each model gives players a reason to choose it.

## Current build

The Vehicle Factory crafting-table menu exposes the unarmed pickup and LAV-150 at a completed eligible factory. The purchase menu currently lists both together. The pickup costs $4,000, 20 Steel, 10 Mechanical Parts and 8 Fuel. The LAV-150 costs $8,500, 50 Steel, 25 Mechanical Parts, 20 Fuel and 5 Electronics. Both arrive with 25% fuel. A complete fill from empty costs 32 and 80 national Fuel respectively. Development code recognizes 48 factory sites across 16 cities. Each new model still needs its own clearance and service checks. [Purchase offers](../src/main/java/com/quin/catancraft/command/NationProcurement.java) · [Vehicle menu](../src/main/java/com/quin/catancraft/world/PhysicalVehicleShop50.java) · [Fuel tariffs](../src/main/java/com/quin/catancraft/world/VehicleFuel49.java) · [All-city factory evidence](VEHICLE_SITES_052_CHECKPOINT.md)

The three active monument pools award national resources, money or TaCZ equipment. Each pool has 100,000 tickets and selects one bundle per successful capture. All current equipment entitlements cover guns or ammunition. Adding a vehicle requires a vehicle delivery and recovery path as well as a new reward-table revision. [Active pool budgets and entries](TACZ_ONLY_044_CATALOG.md) · [Pool implementation](../src/main/java/com/quin/catancraft/world/MonumentSupply44.java)

## Proposed factory stock

Levels mean **local Vehicle Factory level**, with lower-tier offers still available. IDs in both proposal tables use the `superbwarfare` namespace. Seat counts come from the pinned mod's server definitions. Ease of use, performance, balance and fit require gameplay measurements.

| First factory level | Model and exact ID | Why buy it? | Decision to test |
| --- | --- | --- | --- |
| 1 | Unarmed pickup · `sodayo_pick_up` | Eight seats make this a practical first purchase for moving teammates. | Keep the current purchase as the affordability baseline. |
| 2 | Truck · `truck` | Two-seat utility truck with a native towing parameter. | Verify a useful towable and legal route on this map. National stockpiles already serve the economy, so the pitch cannot assume physical cargo hauling. Defer the truck if it has no useful job. |
| 2 | Mounted pickup · `sodayo_pick_up_hmg` | Three-seat escort option for a nation choosing between transport and a mounted role. | Measure cost and fuel use against the base pickup and LAV-150. Ensure a solo player can leave the driver seat and reach the intended position. |
| 3 | LAV-150 · `lav_150` | Existing five-seat wheeled armored option. | Moving a currently available offer to L3 is a design decision. Compare that delay against existing access on a fresh economy and preserve earlier purchased vehicles and pending receipts under their recorded terms. |
| 4 | LAV-25 · `lav_25` | Five-seat wheeled alternative for nations investing in an advanced factory. | Compare handling, upkeep and in-game effectiveness with the LAV-150. Keep it if players have a meaningful choice at the two price points. |
| 5 | BMP-2 · `bmp_2` | Seven-seat tracked carrier provides a late-game team transport earned through manufacturing. | Check whether all seven seats, turning, departure and upkeep work in the actual cities. Compare it with the proposed Military Depot reward. |

## Proposed monument exclusives

| Monument | Proposed drop | Why here? |
| --- | --- | --- |
| Industrial Complex | Specialist three-seat pickup · `sodayo_pick_up_tow` | An uncommon factory conversion gives the Industrial capture a vehicle identity while leaving both basic transport and the ordinary mounted pickup available for purchase. |
| Military Depot | Bradley seven-seat tracked carrier · `bradley` | A rare counterpart to the buyable BMP-2. Compare the two in the same matchups and on the same routes. If the drop dominates, revise its selection before adding it to the pool. |
| Refinery | Retain the existing reward mix for the first comparison. | Fuel already supports every vehicle. Measure the value of winning and using that fuel before assigning a third exclusive. |

Model each exclusive at **0.5% and 2% per successful capture of its own monument** as alternative trial rates. Under independent rolls, 139 or 35 successful captures respectively give a nation about a 50% chance to see at least one. Use observed captures per session and competing nations' shares to translate this into season availability. Each trial must name the existing tickets it reallocates and keep the pool total at 100,000. No rates or replacement tickets have been selected for production.

On a winning roll, record a durable entitlement for the winning nation. Let its authorized leader redeem it at an eligible owned Vehicle Factory into that facility's checked bay. Show the pending reward and collection instructions even if the nation has yet to build a factory. Rewarded vehicles can bypass a model's retail tier while still requiring a usable bay. A full or obstructed bay leaves the entitlement available. One redemption must produce one bound vehicle with a recorded starting fuel amount and the same ownership, refueling and anti-packing rules as purchased vehicles. Retain the original roll across retry and restart.

## Held definitions

These 35 IDs complete the inventory of all 43 entries under `data/superbwarfare/sbw/vehicles/` in the pinned JAR. A corresponding registration name was checked in `com/atsuishio/superbwarfare/init/ModEntities.kt` in its sources JAR.

| Group | Exact IDs | Reason to hold |
| --- | --- | --- |
| Aircraft, airships and drone | `a_10a`, `ac_130h`, `ah_6`, `air_sheep`, `drone`, `happiest_ghast`, `j_16`, `ju_87`, `kirov`, `kv_16`, `mi_28`, `tom_6` | Air access, retrieval, landing and the impact on city defenses need separate gameplay decisions. |
| Watercraft | `speedboat`, `tiny_speedboat` | Roadside bays cannot demonstrate launch or retrieval on water. Revisit with the coast/ocean work. |
| Fixed defenses and emplacement candidates | `annihilator`, `bl_132`, `hpj_11`, `laser_tower`, `mk_42`, `mle_1934`, `mortar`, `tow`, `type_63`, `waveforce_tower` | These belong in a separate review of emplacement sites and control. The standalone `tow` is a different entity from the proposed pickup variant. |
| Overlapping or heavy ground options | `fh_77bw`, `lav_ad`, `m_1a_2`, `plz_05`, `prism_tank`, `sodayo_pick_up_rocket`, `t_90a`, `yx_100`, `ztz_99a` | Revisit individual models when an unfilled gameplay role and a qualified operating area are identified. |
| Other entities | `vehicle_assembling_table`, `wheel_chair` | The assembling table is infrastructure. The wheelchair needs a separate mobility design if requested. |

Reviewed runtime and uploader-provided sources are Superb Warfare 0.8.9.2 build `65d985a64`. SHA-256 for the published JAR is `559930003f638080de648a8621821d785158768c3f69a762094ef4c8d3c4cc12`. SHA-256 for the sources is `06b4bc749e540879265e3f3829c6c242da81512b19a80de0fbd9b089847893d5`. [Pinned artifact verifier](../tools/sec01_035/prepare_runtime.py)

## Combined playtest decisions

1. From normal starting resources, compare time to first pickup, first armed option, first full refuel and L5 carrier. Record which materials block the next purchase, how much a replacement costs and whether trade or monument ownership meaningfully changes the choice.
2. At each eligible city, validate the factory counter, all three possible plots and both city orientations. For each model test actual spawn, unblocked departure, steering, seats, dismount and paid refueling. Check an occupied bay refuses before debit. A new model needs a measured collision envelope and its own fuel tariff. [Original delivery survey](VEHICLE_PROCUREMENT_045_SURVEY.md)
3. Test a non-owner, an interrupted purchase, an interrupted reward delivery and a restart both before and after redemption. Check the original national owner, one receipt, one vehicle, preserved Fuel credit, and zero packed copies. Keep the installed-pack crowbar check in the combined City D session. [Crowbar tracking issue](https://github.com/quintaneous/catanCraft/issues/6)
4. Record purchase, decline and replacement decisions, reward frequency, time to redeem and any vehicle a player cannot use. Select the first release roster, gates, prices, fuel tariffs and reward odds from those results. Keep proven pickup/LAV service available while new candidates are evaluated.
