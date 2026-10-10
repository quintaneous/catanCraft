# CatanCraft 0.1.2 — Combat terrain protection

Forge 1.20.1 / Java 17. This is a **server-side behavior hotfix** on top of CatanCraft 0.1.1. World, city plots, schematic bundle, nation data, network protocol and Blender R3 schematic revisions are unchanged.

## Report after v0.1.1 tests (2026-10-10)
- [x] Screenshot confirms Military Depot capture chat receipt: +$1,500 treasury, +25 Steel, +30 Fabric, +20 Explosives.
- [x] User reports HUD displays territory/claim labels outside map territory as well, and is happy with current behavior. **No change requested.**
- [ ] TaCZ gunfire destroys panes/glass even inside monuments (regression / protection failure).
- [ ] Superb Warfare vehicles can destroy map blocks (regression / protection failure).
- The shared v0.1.1 Markdown test checklist still contains unchecked template boxes; user reports their test session complete, but individual GUI checkbox values are not present in the document. Do not infer independent PASS results for every checklist row.
- Multiplayer contested capture remains deferred until another tester joins.

## Root cause
`WorldProtection` cancels normal player break/place and Minecraft `ExplosionEvent.Detonate` affected blocks, but third-party mods call `Level.destroyBlock()` directly:
- TaCZ's `DestroyGlassBlock.onAmmoHitBlock` uses `AmmoConfig.DESTROY_GLASS` and directly breaks glass/panes when enabled.
- Superb Warfare's `VehicleMotionUtils.collideBlocks` calls `destroyBlock` if collision block destruction config is enabled; projectiles and extra explosion effects also have their own config gates.

Canceling TaCZ AmmoHitBlockEvent is not safe: it can skip its usual bullet-hit logic, making bullets pass through blocks. Use the **combat mods' native configuration gates** instead.

## Automatic server protection
On server startup and every 200 ticks (normally ~10 seconds), CatanCraft 0.1.2 ensures the following Forge Boolean config values are false using optional-mod runtime config references; missing mods don't crash the server.

TaCZ: `DESTROY_GLASS=false`, `IGNITE_BLOCK=false`, `EXPLOSIVE_AMMO_DESTROYS_BLOCK=false`.

Superb Warfare: `COLLISION_DESTROY_SOFT_BLOCKS=false`, `COLLISION_DESTROY_NORMAL_BLOCKS=false`, `COLLISION_DESTROY_HARD_BLOCKS=false`, `COLLISION_DESTROY_BLOCKS_BEASTLY=false`, `PROJECTILE_DESTROY_BLOCKS=false`, `EXPLOSION_DESTROY=false`, `EXTRA_EXPLOSION_EFFECT=false`.

Any settings that cannot be read or modified produce server **WARN** logs, not a claimed protection pass. Block destruction from code paths unrelated to those flags remains to be tested. Directly spawned vehicles and player damage should remain enabled. Until in-game tests pass, **make a backup before using combat mods near authored structures**. This is whole-world peace-map terrain protection; siege-specific destruction requires a separately designed, explicitly reviewed system and restoration workflow.

## Install
1. Stop server/client. Back up world, `config/catancraft`, and mods.
2. Remove **every** previous `catancraft-*.jar` from server AND client `mods/`.
3. Install only `catancraft-0.1.2.jar` on both sides. Mod metadata version = 0.1.2. Network protocol 3 is unchanged; match client/server exactly.
4. Reuse or install the supplied R3 `schematics_bundle.zip` in server `config/catancraft/`. Exactly **38 ready and 0 invalid** required, with 40 zip entries.
5. Keep existing `world/`, claims, economy, `map.json`, TaCZ, Superb Warfare and Kotlin for Forge dependency. No world migration or visual repaste.

## Solo regression checklist
- [ ] Server and client launch showing CatanCraft **0.1.2** (no duplicate JAR, no crash).
- [ ] Inspect server `latest.log` for protection WARN warnings; share them if any appear.
- [ ] Shoot the Military Depot/Refinery/Industrial Complex **glass windows** using TaCZ. Windows remain intact; gun still fires and can damage a target entity behind accessible lines of sight.
- [ ] Shoot ordinary **village and factory windows** with TaCZ; no block breaks.
- [ ] Attempt TaCZ incendiary/explosive ammo near monument walls. No block/ignition changes; entity effects where appropriate remain.
- [ ] Drive a Superb Warfare purchased pickup against **glass, wood, stone, walls**. All blocks remain intact. Driveability, health/physics and collision remain normal.
- [ ] Fire mounted Superb Warfare guns/explosive projectiles near monuments; verify no block loss and player/entity damage still occurs (in a backed-up test copy).
- [ ] Repeat the above test after a server restart: settings stay enforced.
- [ ] `/catan map assetstatus` shows **38 ready, 0 missing/invalid**. Do NOT force-refresh city schematics in this test.
- [ ] HUD, `/speed`, collapsing `/nation` GUI, R3 flexible factory construction, capture reward receipt, TaCZ procurement, and protected vehicle spawning still work.
- [ ] Nation treasury, stockpile, constructed plots, ownership and vehicle tags persist after restart.

## Limits
- Not a world rollback or repair operation: previously broken windows are not automatically restored.
- This enforces documented native combat mod settings (based on TaCZ 1.20.1 branch and Superb Warfare 0.8.9.1-1.20 branch), but **does not prove full third-party compatibility** with the user's installed Superb Warfare 0.8.9.2 or every special vehicle ability until runtime tested.
- Other server admin tools, commands, datapacks and custom mods can still change world blocks.
- No siege-specific damage or visual R2 house migration is included.

Next development: R4 city defenses remain design-only until peace protections and R3 game testing pass.
