# CatanCraft Procurement Phase 1 — TaCZ + Superb Warfare (Forge 1.20.1)

**New command:** `/nation procure list` (also `/catan nation procure list`). To purchase: `/nation procure <id>`.

**Install dependencies on BOTH server and client:** TaCZ for firearms; Superb Warfare for vehicles, plus Superb Warfare's required Curios API and GeckoLib. Cloth Config optionally enables in-game mod settings. CatanCraft continues to load without the mods, but refuses the affected purchases until the corresponding mod is installed.

## Catalog (early testing prices)

| ID | Equipment | Factory requirement | National cost |
| --- | --- | --- | --- |
| pistol | TaCZ Glock 17 | None (starter access) | $350 + 5 Wood + 5 Stone |
| ammo9 | 32x 9mm | None | $100 + 5 Stone |
| ak47 | TaCZ AK-47 | Weapons Factory | $1,200 + 12 Steel + 10 Wood |
| ammo762 | 32x 7.62x39 | Weapons Factory | $175 + 2 Steel + 1 Explosives |
| pickup | Superb Warfare unarmed pickup | Vehicle Factory | $4,000 + 20 Steel + 10 Mechanical Parts + 8 Fuel |
| lav150 | Superb Warfare LAV-150 | Vehicle Factory | $8,500 + 50 Steel + 25 Mechanical Parts + 20 Fuel + 5 Electronics |

- Only nation **leaders** may spend national stockpile/treasury for Phase 1. Ordinary nation members cannot spend pooled resources yet.
- The necessary factory may be in **any territory owned by the nation**; no road/warehouse connection requirement.
- Real TaCZ guns/ammo are constructed through TaCZ's own runtime API with verified gun/ammo IDs; procurement does not issue generic Minecraft weapons.
- Guns/ammo are given to the buying player's inventory. An empty inventory slot is required; errors or missing gun pack **do not charge** national resources.
- Vehicles are spawned directly into the world (never as a container item); spawn requires that player stand in their owned territory on/near flat ground, with all vehicle corners within that territory and with open space about eight blocks in front of the player's gaze.
- A successful vehicle spawn is tagged persistently with its nation UUID; **sneak + crowbar retrieval is canceled** for purchased vehicles. This includes Forge `forge:tools/crowbar` tags used by Superb Warfare.
- CatanCraft charges only after successfully adding an item to inventory or spawning a vehicle. Failed attempts should not charge stockpile/treasury.
- Vehicle charging/fuel, other reclaim/exploit paths, enemy vehicle access, vehicle ownership after conquest, destroyed-vehicle refunds, anti-duplication, actual collision geometry, and precise physical Vehicle Factory deployment bays are **not yet production complete**. Do not claim they are fully secured.

## Solo test plan

1. Back up the world, nation data and `config/catancraft`; install mods on server/client; replace the CatanCraft JAR on both, not its V4/R1 world or schematic bundle.
2. With a nation leader, run `/nation procure list`. Check catalog costs and gated equipment.
3. With nation stockpile funded, `/nation procure pistol` and `/nation procure ammo9`. Confirm actual TaCZ Glock is visible, loads the 9mm ammo, fires and subtracts money/materials only once.
4. Test insufficient stockpile, a full inventory, and missing TaCZ gun-pack paths: no items and no funds charged.
5. Build or debug-add Weapons Factory, then `/nation procure ak47` and `/nation procure ammo762`. Confirm actual gun and rounds match.
6. Build or debug-add Vehicle Factory and fund national cost. Stand in owned territory with a clear flat yard, point horizontally in an open direction, then `/nation procure pickup`.
7. Validate vehicle is a real Superb Warfare drivable entity, persists after server restart, and `Shift + crowbar + right-click` cannot turn it into a container item. Test no-charge behavior when standing outside owned territory or when spawn is obstructed.
8. `/nation procure lav150` in sufficiently clear yard (approx 4×4 footprint), verify steering/boarding/collision and fuel/charging requirements separately.
9. Recheck `/nation` collapse, Environment V4/R1 monuments, vehicle plot purchases, stockpile and nation ownership after restart.

## Development next

Implement an integrated `/nation` GUI procurement panel with actual vendor filtering and factory states; test SBW entity retrieval methods beyond crowbars; block non-factory assembly or item-container exploits if needed; map stable vehicle registration/ownership after territory capture; setup armory and deployment bay schematics with Blender; update vehicle/weapon costs after 20-hour economy simulation.

## Source of API assumptions

- TaCZ 1.20.1: `com.tacz.guns.api.item.builder.GunItemBuilder` and `AmmoItemBuilder`, `tacz_default_gun` index files.
- Superb Warfare 0.8.9.1-1.20: `ModEntities.kt`, `CrowbarItem.kt`, `VehicleEntity.kt` and `ModTags.kt`. Crowbar tool tag is `forge:tools/crowbar`.
