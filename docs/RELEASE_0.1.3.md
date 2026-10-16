# CatanCraft 0.1.3 — purchased-vehicle crowbar protection

Build: Forge 1.20.1, Java 17. Code-only release building on 0.1.2; same R3 40-schematic bundle and 38 required asset checks. **Does not integrate Blender R4 or R4-A prototypes.**

## Root cause and protection change

User reported a purchased Superb Warfare pickup could still be shift-right-clicked with a crowbar and converted into a portable item even though CatanCraft's Forge event handler attempted to cancel it.

Superb Warfare `VehicleEntity.onCrowbarInteract` itself calls `getRetrieveItems()`, gives the player portable items, and then discards the vehicle. The new optional compatibility Mixin intercepts **at the start of this method**, before inventory changes or vehicle deletion. The interceptor blocks only entities bearing the persistent `CatanCraftPurchasedVehicle` tag already applied by `/nation procure pickup` and `/nation procure lav150`; normal Superb Warfare vehicles retain their ordinary behavior.

Mixin is registered via `MixinConfigs` in JAR manifest and uses a soft/optional target. If Superb Warfare is absent, CatanCraft remains loadable. The previous Forge event fallback remains. Expect dedicated server log `Denied crowbar retrieval of purchased nation vehicle ...` if successfully blocked.

**Compatibility warning:** Forge CI compiles without Superb Warfare installed and therefore cannot runtime-test injection against the user's version. Test in the actual modded server; if the exact installed Superb Warfare build changes its method name or interception flow, this fix may not activate. No invulnerability or anti-duplication guarantee is claimed before in-game acceptance.

## Installation

1. STOP server/client. Backup world, configs and old JARs.
2. Remove **all** older `catancraft-*.jar` from both server and client `mods/`.
3. Install **only** `catancraft-0.1.3.jar` in both. Do not change Forge 1.20.1 / Java 17, TaCZ or Superb Warfare versions for this test.
4. Keep your existing `world`, `map.json`, claims and R3 `schematics_bundle.zip` unchanged. ZIP is re-included for convenience.
5. Reload only if desired: `/catan map assetstatus` should show 38 ready / 0 invalid. Do not paste R4 prototypes into live world.

## Solo acceptance

- [ ] Server and client load `CatanCraft 0.1.3`, no Mixin errors or duplicate JARs.
- [ ] In an owned territory, purchase `/nation procure pickup` and ensure treasury/materials deducted once.
- [ ] While empty and parked, shift-right-click **with Superb Warfare crowbar**. Nation-purchased pickup must remain spawned, cannot become a container/item, and show the rejection message.
- [ ] Server `latest.log` records `Denied crowbar retrieval ...`, proving interception actually ran.
- [ ] Restart server and repeat crowbar test on the **same** purchased pickup; entity remains and tag persisted.
- [ ] Try purchased LAV-150 the same way. Neither vehicle may produce an item or disappear.
- [ ] Create a **non-procured** Superb Warfare vehicle on a disposable test world; its default retrieval should remain unchanged (differential check).
- [ ] Test normal entering, driving, inventory/weapon and tank collisions still function.
- [ ] Confirm 0.1.2 terrain rules still prevent TaCZ windows and Superb Warfare vehicle/projectile block griefing.
- [ ] Confirm /nation dashboard, /speed, HUD, R3 industrial plots, capture reward chat, and save persistence remain intact.
- [ ] Test survival non-OP behavior only if another player is available; multiplayer contest remains deferred.

**Failure protocol:** send latest.log lines containing `mixin`, `catancraft`, and `crowbar`. If pickup packs again, do not claim it is fixed; we may need a different injection target or an SBW compatibility update.

## Next milestone

Blender R4-A is still being prepared in a separate conversation, no placement-ready package is in the shared library as of this release. Keep the defense prototypes isolated. Proceed with R4-A safe installation only once the package's placement/terrain gates have been checked.
