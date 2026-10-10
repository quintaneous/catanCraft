# CatanCraft 0.1.2 — User in-game acceptance results

**Date:** 2026-10-10
**Reported build:** CatanCraft 0.1.2 (Forge 1.20.1, Java 17)
**Source release commit:** fb4db8569bee886378e2fc93a9dd039db6f2550c
**Assessment:** **20 PASS, 1 FAIL**. Protection/peace-world stability accepted for observed test cases; vehicle packaging still not acceptable for full economy release.

## Installation and world — 3/3 PASS
- PASS — Server and client load CatanCraft 0.1.2.
- PASS — `/catan map assetstatus`: 38 ready, 0 invalid.
- PASS — Existing world, buildings and nation data load normally.

## Combat protection — 10/10 PASS
- PASS — TaCZ pistol / AK-47 cannot break Military Depot windows.
- PASS — TaCZ cannot break glass in Industrial Complex, Refinery, houses or factories.
- PASS — TaCZ explosive/incendiary ammo cannot break or ignite blocks.
- PASS — Superb Warfare pickup cannot destroy windows or walls by collisions.
- PASS — Superb Warfare LAV-150 cannot destroy stone or glass.
- PASS — Superb Warfare vehicle weapons and explosions cannot destroy scenery.
- PASS — TaCZ and Superb Warfare still damage entities normally.
- PASS — Solid walls still block gunfire.
- PASS — Block protection remains active after restart.
- PASS — No terrain-protection warnings in server latest.log (per user's test).

## Existing gameplay — 7/8 PASS, 1 FAIL
- PASS — Territory HUD displays correct claim/territory label (current behavior approved).
- PASS — Nation dashboard menus open and collapse.
- PASS — OP `/speed`, `/speed 10`, `/speed 1`.
- PASS — Military Depot reward chat receipt.
- PASS — R3 factory purchases on available plots without overwriting existing buildings.
- PASS — TaCZ weapon/ammo purchases.
- **FAIL** — A purchased Superb Warfare pickup can be recovered with a crowbar. User actually recovered it despite CatanCraft's `NationVehicleProtection` handler.
- PASS — Treasury, stockpile, vehicle/building data persist after restart.

## Crowbar regression analysis
SBW upstream `VehicleEntity.interact` invokes `onCrowbarInteract` for `forge:tools/crowbar` when sneaking; `onCrowbarInteract` emits `getRetrieveItems()` and discards the vehicle. Therefore restricting player distribution of crowbars is mitigation only, **not a code-level guarantee**, and it may permit bypassing CatanCraft nation ownership/procurement if obtainable otherwise.

Existing `NationVehicleProtection` tries to cancel Forge `EntityInteract` and `EntityInteractSpecific` for tagged vehicles. Because the recovery succeeded, at least one assumption fails for the user's runtime (event, target entity identity/part hitbox, persistence tag visibility, client/server sequencing, or handler ordering). Root cause is not yet conclusively established. Do not simply assert the tag check works; reproduce and instrument.

Suggested next patch: investigate actual server-side interact sequence and tag storage, block direct retrieval by using robust API interception / registered compatibility or a targeted hook. Test with vanilla server player and the actual SBW crowbar; verify that purchased vehicle stays spawned, **no container item** enters inventory, and nation tag persists. Test normal/unpurchased vehicles are not affected, plus restart and sneaking, both interaction hands, and any other retrieval mechanic.

## Next milestone
R4-A city defense socket surveying, Blender integration planning and isolated design can proceed. Do not call R4 combat/economy launch ready until vehicle retrieval is resolved. Multiplayer contested capture is deferred until another player is available.
