# 0.1.44 native handheld policy

The current gameplay policy is TaCZ handheld guns only. SBW remains installed for vehicles and their mounted weapons. Explosives are deferred from the shop and monument pools. Existing SBW gun stacks and historical reward receipts are retained; this policy does not delete or convert them.

## Pinned native audit

This implementation was audited against `superbwarfare-0.8.9.2-mc1.20.1-65d985a64-sources.jar`, SHA-256 `06b4bc749e540879265e3f3829c6c242da81512b19a80de0fbd9b089847893d5`, the source paired with the existing SEC01 native runtime manifest. It does not infer SBW behavior from the old catalog alone. The expected native registry contains 43 catalog gun items plus the internal empty and vehicle gun items. The test rejects a changed count. The native `repair_tool` also inherits GunItem and can damage entities, so its handheld repair/damage beam is included in this prohibition; it is not an exception for vehicle maintenance.

`GunData.shoot` delegates directly to `GunItem.shoot`; it does not debit ammunition first. All normal overloads converge on `shoot(ShootParameters)`. Its HEAD precedes canShoot, perks/events, projectiles, ammunition and heat updates. The overrides in IglaItem, JavelinItem and BocekItem are also intercepted. Bocek's distinct `onFireKeyRelease` route is intercepted separately. `ShootEvent.Pre` is not cancelable and therefore cannot implement this policy by itself.

The native handheld packet handlers ShootMessage, FireKeyMessage, LaserShootMessage, MeleeAttackMessage and LungeMineAttackMessage are stopped before reading sender context or invoking damage. LaserShootMessage is especially important: its native damage path does not check for a held gun. Native ServerPacketPayload enqueues the handler and sets packetHandled itself, so an early return from the handler does not omit packet acknowledgement. VehicleFireMessage and ReloadMessage are untouched; ReloadMessage includes vehicle reload handling.

The mounted-weapon exception requires a VehicleGunItem, a live VehicleEntity ammunition supplier in the same ServerLevel, and exact object identity with a GunData value in that vehicle's native gunDataMap. Being a passenger alone is insufficient; copied vehicle GunData or a handheld gun supplied with a vehicle cannot pass. No SBW registry entries or shared ammunition are removed.

## Acquisition and retained items

| Route | Policy boundary |
|---|---|
| Shop and newly assigned monument rewards | Active TaCZ catalog and supply policy, documented separately |
| Crafting/smithing/research output | RecipeManager resource input is copied and filtered before native deserialization; prohibited item outputs are removed |
| Loot, including global loot modifiers | ForgeHooks.modifyLoot return is filtered after the modifier chain |
| Creative tabs | Genuine Forge creative entry map is filtered using copied removal keys |
| Dropped prohibited item pickup | Forge pickup event is canceled; the existing ItemEntity is not deleted |
| Main/offhand interactions and melee | Forge interaction/attack events reject prohibited items |
| Retained or admin-provided SBW gun | Native firing and handheld damage packet boundaries remain inert |

The pinned native recipes use ordinary item results for guns, including minecraft:smithing_transform. The filter recognizes result/results strings, item/id objects, and arrays. Native vehicle_assembling entity results, shared rifle/heavy ammunition, vehicle keys, and crowbars remain available. Blueprints may remain as historical/crafting objects, but their gun-producing recipes cannot load.

Deferred handheld IDs are hand_grenade, rgo_grenade, m18_smoke_grenade, blu_43_mine, claymore_mine, c4_bomb, c4_bomb_rc and lunge_mine. Their new recipe/loot/creative acquisition and player interactions are rejected. Existing placed explosives, native dispenser automation and vehicle ordnance are not erased or globally disabled. This is not an assertion that every SBW explosive entity has been removed from the world.

## Production mapping and optional providers

The optional SBW mixins use `@Pseudo`, string target names and remap=false; production policy classes do not link optional SBW classes. The native runtime gate requires each expected present-provider interception to execute. Missing optional providers are exercised by the existing base suite.

The project does not generate a Mixin refmap. RecipeManager therefore uses exact development and production selectors, both with remap=false:

- `apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V`
- `m_5787_(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V`

These names were cross-checked against Mojang's official 1.20.1 server mappings (`RecipeManager -> cjd`, the Map apply overload -> a) and [MCPConfig 1.20.1 joined.tsrg](https://github.com/MinecraftForge/MCPConfig/blob/master/versions/release/1.20.1/joined.tsrg) (`cjd.a(Map,akx,ban) -> m_5787_`). The exact descriptor excludes the synthetic Object bridge. Forge's modifyLoot and creative event APIs were checked in Forge source commit `132704e5f23dbee28d776738eb1c0c42fefc0bf6`.

Register these five mixins: SbwHandheldFireMixin, SbwHandheldPacketMixin, SbwHandheldReleaseMixin, SbwHandheldRecipesMixin and SbwHandheldLootMixin. Forge and MOD event subscribers are annotation registered; no CatanCraft bootstrap hook or new datapack resource is needed.

## Controlled verification contract

Seven `catancraft_handheld44` GameTests run with both pinned native providers, exclusively in the marked synthetic `controlled-catancraft_handheld44-run/world` fixture with no connected players:

1. Every registered SBW gun is prohibited; TaCZ guns, shared vehicle ammunition and tools remain allowed.
2. Actual native shoot methods for all 45 items execute interception before NBT/ammunition/entity changes.
3. Five real native packet handlers plus both release implementations return before accessing context or changing gun state.
4. Loaded recipes contain no prohibited output; actual RecipeManager accepts an immutable caller map without mutating it; actual final Forge loot hook removes only prohibited results.
5. Both-hand interaction, pickup preservation and genuine Forge creative entry filtering retain TaCZ and vehicle supply controls.
6. Genuine mounted LAV Cannon data fires a real projectile and consumes exactly one physical native small_shell_ap shell (eight become seven); a handheld gun while riding that vehicle and detached vehicle data remain denied.
7. A serialized/reloaded legacy native stack remains byte-for-byte represented in NBT and cannot fire through GunData.shoot.

Each case emits a HANDHELD44_*_PASS marker only after its assertions. These are controlled native checks, not owner in-game acceptance, balance testing, a live-world migration or a promise about unsupported provider versions. Consult the release validation report for actual execution results; writing this contract alone is not a test pass.
