# Vehicle procurement spawn hotfix — test instructions

**Target:** CatanCraft Forge 1.20.1, `v0.1-foundation`, TaCZ + Superb Warfare Procurement Phase 1.

## Reported bug
User stood on/near a paved Vehicle Factory garage apron and attempted `/nation procure pickup`. Earlier code:
- Used the `MOTION_BLOCKING_NO_LEAVES` **heightmap**, which sees the garage's roof instead of nearby ground.
- Checked collision using `bounds.inflate(0.35, 0.25, 0.35)` after placing wheels at `ground + 0.15`. This extended the bounding box **below the pavement**, rejecting normal solid floors.
- Checked only one hard-coded point 8 blocks in front of the player. Roof, wall or entity could invalidate it.
- Correctly reported **no funds charged** on failed attempts.

## Changes
- Use a bounded search of candidate centers 7, 9, 11, 13 or 15 blocks from the player's location, favoring facing direction and then adjacent angles.
- Inspect floor support at nine footprint samples, no liquids, and candidate ground within 2 blocks vertically of player's feet.
- Check same owned territory at candidate footprint; avoid unloaded chunks.
- Expand collision horizontally/upward only; **never embed the collision margin in floor blocks**.
- Abort if parked vehicles, players or walls obstruct the spawn.
- Only charge treasury/materials after the entity is successfully added to the world; preserve persistent purchased-vehicle tags/crowbar guard.

## Install
Back up the world and current CatanCraft JAR. Replace only `catancraft-0.1.0-alpha.jar` in server and client `mods/`. Keep existing World V4/R1/R2, schematic bundle, TaCZ, Superb Warfare and Java 17. There must be **one** CatanCraft JAR in each mods directory.

## Solo QA
- [ ] Start Forge with all dependencies installed.
- [ ] In a nation-owned, paved, open deployment yard, stand on the ground and point toward open space. `/nation procure pickup`.
- [ ] Check that a real Superb Warfare pickup spawns, is enterable, and **deducts exact national cost only once**.
- [ ] Repeat purchase on the paved garage frontage (with a clear nearby area). It should not choose an inaccessible roof or intersect columns.
- [ ] In a roofed garage with low headroom, vehicle may legitimately be refused unless a nearby clear spot exists. Ensure the failure message says **no funds charged**.
- [ ] Block the entire surrounding area with parked vehicles/walls and verify failed purchase does not spend resources or spawn extra entities.
- [ ] Move outside owned territory and verify purchase still fails without charging.
- [ ] `/nation procure lav150` in a suitably wide paved area; test larger-vehicle collision separately.
- [ ] Sneak + Superb Warfare crowbar on purchased pickup should not package it; vehicle and ownership tags survive logout/restart.
- [ ] Regression: `/nation` UI collapse, TaCZ pistol and ammo, capture reward receipt, map assetstatus 32/32, world verification 26/26.

**Limits:** This is a spawn detection hotfix, not a dedicated Vehicle Factory bay or guaranteed spawn-anywhere teleport system. Vehicles still must fit into legitimately clear space within owned territory. Blender R2's garage geometry is a separate asset update; do not repaste R2 city assets while evaluating this code-only hotfix.
