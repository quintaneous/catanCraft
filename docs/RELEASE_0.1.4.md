# CatanCraft v0.1.4 — Logistics Economy + R4 Defense Survey

**Forge 1.20.1 / Java 17; CatanCraft mod metadata + JAR version: 0.1.4.**
Development branch: `v0.1-foundation`.

This release INCLUDES the unverified v0.1.3 Superb Warfare crowbar Mixin guard. The previously user-tested v0.1.2 terrain/weapon protection is retained.

## 1. Logistics Center finally has an economic function

The existing Logistics Center can be purchased by the nation leader for the pre-existing cost ($3,000 + 50 Wood + 50 Stone), requires City Level 2, occupies one normal city development slot and can be upgraded using ordinary building upgrade commands.

A single Logistics Center grants a production-output bonus to **processor factories located in the SAME city**, without multiplying the raw-resource production or supply costs in other territories:

| Logistics level | Extra output for city processors |
|---|---|
| L1 | +10% |
| L2 | +15% |
| L3 | +20% |
| L4 | +25% |
| L5 | +30% |

**Rounding:** additional items round UP so modest outputs are meaningfully improved: Textile Mill L1 8 Fabric/15 min becomes **9** at Logistics L1 (if supplied and beneath target stock). Machine Shop L1 6 Mechanical Parts becomes **7**. Multiple processor batches use the total batch output for one rounding step per cycle. Existing processor input recipes, national stock targets and four-hour raw production timing (four cycles/hour) remain unchanged.

Bonus applies automatically to pre-existing Logistics Centers saved in player cities; no repurchase required. Only one Logistics Center and one Commercial District may be purchased per city going forward; pre-existing duplicate buildings are NOT erased, and Logistics uses the highest-level existing center rather than stacking bonuses.

The `/nation` dashboard's Production & Upgrades section now displays the city's Logistics percentage, and actual Ready factory output shows the boosted amount. No Logistics Center **schematic** exists yet, so the purchase is still a data-only civic building. The Commercial District's existing passive cash income is unchanged and it also awaits visual art.

## 2. R4-A defense groundwork — READ-ONLY, not purchasable yet

Operator command:

- `/catan defense status` — confirms defense installation is disabled.
- `/catan defense survey a` — reports 15 provisional R4 reservation bands for City A, equivalent `b`..`p` for all other 15 cities.

The survey transforms Blender's north-facing proposal around each city's saved center/orientation, checks city siege-bounds, loaded chunks, sampling fluid, terrain-height differences and apparent obstructions. Unloaded chunks are explicitly marked, never loaded for the survey. The output distinguishes proposed alternative bunker/trench bands from standard walls/towers/checkpoint.

**CRITICAL:** Coordinates are **provisional research candidates only**, not approved R4-A world anchors. Sampling every five blocks cannot certify exact terrain or building clearance. The final R4-A Blender package, voxel masks, on-server validations, safe snapshots and deployment rollback must all be completed before purchases or placements are enabled.

NO R4 module schematics have been added to the live bundle; no buildings, roads, Town Halls or terrain are altered by the survey command. Do not manually paste the prototypes over the existing world.

## 3. v0.1.3 crowbar fix carried forward — MUST BE TESTED

The original v0.1.3 optional Superb Warfare compatibility Mixin intercepts vehicle `onCrowbarInteract` before it can award portable vehicles or discard a purchased vehicle. **Forge CI cannot prove that Mixin triggers against your actual installed Superb Warfare 0.8.9.2**. This remains the main acceptance item. If it fails or crashes the game, immediately revert to the already user-tested 0.1.2 JAR while investigating; do not distribute crowbars in the meantime.

## Install

1. Stop both Minecraft and Forge server; back up the whole world, `config/catancraft` and existing mod JARs.
2. Delete all old `catancraft-*.jar` from **both** client and server `mods/`. Install only `catancraft-0.1.4.jar` into both.
3. Use the supplied `schematics_bundle.zip` on the server at `config/catancraft/schematics_bundle.zip` or keep your existing **unchanged** v0.1.2/v0.1.3 R3 bundle.
4. Keep existing `world/`, `map.json`, nation data, TaCZ, Superb Warfare and dependencies. **No world reimport, no whole-city paste, no R4 prototype inclusion.**
5. Start and verify `/catan map assetstatus`: **38 ready, 0 invalid**. GUI, HUD and economic cycle should still work.

## Solo testing checklist

### Version and v0.1.3 retrieval carried into 0.1.4
- [ ] Client and server report CatanCraft **0.1.4**, no `Mixin` load/start errors.
- [ ] Purchase `pickup` in owned city; shift-right-click purchased empty truck with real Superb Warfare crowbar. Truck remains, nothing enters inventory; visible denial and server log `Denied crowbar retrieval...`.
- [ ] Repeat after server restart; repeat on purchased LAV-150. Neither yields a container item.
- [ ] Create **unprocured** Superb Warfare truck on disposable test copy and verify its normal retrieval remains possible.
- [ ] With crowbar test complete, confirm pickup/LAV driving, mounted weapons and existing nation purchase rates work.

### Logistics economy
- [ ] `/nation` purchase **Logistics Center** in City Level 2 territory once; national treasury/material cost deducted one time.
- [ ] Second Logistics Center purchase in the same city is rejected with **no second charge**.
- [ ] `/nation` Production section displays **+10% processor output** and purchased Logistics L1.
- [ ] For a supplied Textile Mill with target stock above national Fabric: baseline 8 Fabric/batch increases to **9 Fabric/batch** when Logistics L1 is present, without extra input cost. Test via preview and one real 15-minute production tick.
- [ ] A processor in another city without Logistics remains at baseline output; raw Iron/Wood/Agriculture output is unchanged.
- [ ] Upgrade City Level/Logistics Center to L2; visible bonus becomes **+15%**; subsequent upgrades use L3 +20%, L4 +25%, L5 +30%.
- [ ] Commercial District still provides normal cash income and cannot be purchased twice in the same city.
- [ ] Restart and confirm Logistics purchase/level/bonus, factory stock and national resources persist.

### R4 defense reconnaissance (OP)
- [ ] `/catan defense status` says installation is disabled.
- [ ] `/catan defense survey a` prints **15 proposed regions** and reports review flags where applicable. **Do not treat any proposed area as approved for placement.**
- [ ] Run `/catan defense survey i` in a 180-degree/south-facing city; verify displayed X/Z are mirrored relative to North orientation.
- [ ] Survey does **not** change any block, build wall, place schematic or change treasury/ownership.
- [ ] If a chunk is unloaded, the survey reports unverified rather than loading/clearing it.

### Regression
- [ ] `/catan map assetstatus`: 38 ready / 0 invalid.
- [ ] TaCZ bullet glass/ignition protection and Superb Warfare collision/weapon terrain protection remain in effect, including after restart.
- [ ] `/nation` accordions, territory HUD, `/speed`, monument rewards and national stockpile persistence remain functioning.
- [ ] R3 factory plotting/purchasing still works and no already placed city objects are destroyed.
- [ ] No client/server crash, no mixin warnings, no serious errors in `latest.log`.

## Known limits and next steps

- Physical Commercial District and Logistics Center assets **not available**; visual buildings must be built later, with separate civic sockets and guarded placement contracts.
- R4 wall/gate/bunker/trench **not purchasable** and not applied to live map in this release.
- 15 defense proposal bands from Blender R4 are provisional. Blender R4-A package not yet available/terrain unverified.
- Individual input/production cycles are based on game time; scheduled ticking behavior unchanged.
- v0.1.3 crowbar retrieval defense **not runtime accepted** until user tests this 0.1.4 JAR.
- Siege phases, selective damage/repair, and sandbag cleanup remain future features.
