# ECO-03 — vehicle counter and fixed-bay survey after 0.1.44

Read-only audit of source `f655451371e3545d43ca224831cd79b9e530d0b6`, its bundled factory schematic and the existing pinned SBW runtime sources. No world, installed mod, schematic or runtime configuration was opened or changed. This establishes a bounded implementation candidate, not native geometry acceptance.

## Existing counter is reachable

Decoded the repository's base64 basic-construction ZIP and parsed its original gzip NBT. `vehicle_factory.schem` SHA-256 is `793e2a277fcb7287ba76298d2080808e315bcf626f76e9fe8f9ce5143e731649`, matching the previous weapon-counter survey. Dimensions are XYZ31×47×31; offset is [-29,-2,-15], so local anchor is (29,2,15). Exactly one crafting table occurs, at (18,3,9).

The earlier040 fixed-height search limitation is resolved: approach the table from its south side. Its other three adjacent feet cells contain smooth stone. The correct standing cell is (18,2,10), ordinary air at feet and head, with authored support at Y1.

| Route | Local endpoints | Steps |
|---|---|---:|
| Roadside approach through open factory frontage | (29,2,15) → (18,2,15) | 11 |
| Turn toward workstation | (18,2,15) → (18,2,10) | 5 |

All16 steps have air at feet/head and authored support; this route requires no door or stair operation. No crafting table, inventory or decorative block needs replacement. Confirm the source states and native player reach/ray trace in the actual disposable runtime fixture before enabling the counter.

For resolved factory anchor A and s=+1 for rotation0 / -1 for180:

- Counter: (Ax−11s, Ay+1, Az−6s).
- Standing cell: (Ax−11s, Ay, Az−5s).
- Approach: (Ax, Ay, Az).

Use `IndustrialPlotService.occupiedPlot` and `anchorFor(VEHICLE_FACTORY, plot, territory)`, including plot4's extra rotation. Do not substitute a map plot origin or assume a fixed plot. These formulas transform the authored route for all supported factory placements; they do not authorize another city.

## Existing straight deployment lane

The exact 700 authored cells X[10,29], Z[12,18], Y[2,6] inclusive are AIR. Every corresponding Y1 floor cell is one of gravel, smooth stone, stone bricks, black concrete or yellow concrete. At Y7, lanterns and entrance lintel/roof blocks occur. Treat the lane's five-block air height as an observed constraint rather than unlimited headroom.

A concrete first qualification candidate is local vehicle center (17.5,2.1,15.5), facing outward along local +X. For rotation0 the expected Minecraft yaw is −90 degrees; rotation180 reverses it. Verify native vehicle forward orientation rather than assuming registered model orientation. The continuous spawn offset relative to the source anchor is u=(-11.5,0.1,0.5). Block-cell indices and fractional entity positions require different rotation formulas. `BasicConstructionAssets.plan` transforms each cell index by A+s×(local−sourceAnchor); the corresponding entity position rotates around the anchor **cell center**:

`world = (Ax+0.5+s×(ux−0.5), Ay+uy, Az+0.5+s×(uz−0.5))`.

Thus the candidate center is (Ax−11.5, Ay+0.1, Az+0.5) for rotation0 and (Ax+12.5, Ay+0.1, Az+0.5) for rotation180, matching `VehicleBay45.Bay.worldSpawn`. Its containing source-offset cell is (-12,0,0), which becomes world cell (Ax−12,Ay,Az) or (Ax+12,Ay,Az). Simply multiplying fractional offsets by s would put the rotated entity in the wrong neighboring cell. The counter/standing formulas above describe integer **block cells**, so their discrete formulas remain unchanged. The lane supplies a straight departure route to the existing roadside anchor; no turning inside the factory is required. Do not enable an unsurveyed turn on the external road.

The candidate is suitable for bounded native investigation, not an approved spawn envelope. The prior source audit found main collision OBB full XYZ2.5×1.458×6.75 for pickup and2.75×2.375×6.375 for LAV150. Those are only main bodies: model parts, turret, wheels, pose-dependent transforms, passenger seats and dismount points must also fit. The native source exposes `VehicleEntity.updateOBB()`, `getOBBs()`, `getSeat(...)` and `getDismountLocationForIndex(...)`. Validate initialized native transforms, every OBB, support, boarding/dismount and straight departure sweep. The initial entity AABB alone is insufficient.

Qualification should use both existing offers and both orientations in disposable authored-factory fixtures. Require loaded chunks and current collision/entity/support checks for the declared lane and bay; refuse occupancy or drift without clearing blocks/entities, charging funds or falling back to the player's surroundings. No new art or Blender task is justified by this survey.

## Transaction gap remains real

Current `NationProcurement.buyVehicle` still creates an entity using arbitrary player-relative `VehicleSpawnFinder`, tags purchased/nation before adding it, then charges and marks SavedData dirty. It has no durable procurement intent or immutable deployed-entity receipt. The existing035 restart tests prove completed purchases/protection persist; they do not prove crash-safe interruption between entity spawn and payment.

Retain the original pickup and LAV prices and native IDs. Implement a separate versioned vehicle transaction contract instead of reusing an inventory receipt as an entity receipt. Freeze actor, nation, city, actual factory/plot/revision, offer price, declared bay transform and stable entity UUID before any payment or spawn. Persist a debit marker and exact entity provenance; on restart reconcile only the known bay/chunk and UUID. If delivery is ambiguous, hold the reservation for explicit recovery; absence from an unloaded chunk must never justify a replacement/refund. A usable entity and refunded payment must not coexist.

Both `/nation procure` and `/catan nation procure` aliases must share the physical session/review/confirm path. Existing physical shop authority, exact facility token/completion and pending-construction checks offer a pattern, but vehicle bay, entity identity and settlement require separate checks. Preserve leader spending authority and recheck ownership, presence, dimension, current source and available funds at confirmation.

## Recommended bounded next implementation

1. Register the above source-derived counter/approach and a disabled-by-default fixed-bay candidate; native-qualify pickup/LAV initialized geometry and access in synthetic/disposable fixtures, including blocked bay and both rotations.
2. Implement the immutable vehicle reservation/settlement state machine with fake-adapter interruption coverage before routing public commands or adding any live UI capability. Keep unknown delivery held and preserve historical records.
3. Bind counter GUI, both command aliases and native adapter only after steps1–2 pass, then run original purchase/crowbar/restart coverage plus new native interruption proofs. No nearby-player fallback or portable vehicle item.

The owner's economy simulation can evaluate unchanged proposed procurement costs independently. Simulation affordability does not certify physical geometry, combat strength or crowbar protection. SEC-01 remains UNRESOLVED / HIGH PRIORITY pending installed-pack acceptance; accepted City A,033/034 recovery, TaCZ-only handheld policy and one combined solo acceptance checklist remain unchanged.
