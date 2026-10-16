# Monument Gameplay R1 integration

Blender's Environment V4 Monument Gameplay R1 requires the flat R1 34-schematic bundle at `config/catancraft/schematics_bundle.zip`.
Replace any conflicting per-file overrides in `config/catancraft/schematics/` first (per-file override takes priority).
The 3 monuments have bumped visual tokens r3 -> r4. Capture anchors, dimensions, all city placements and district base are unchanged.

On an existing Environment V4 server: **BACK UP THE WORLD, CONFIG AND OLD MOD JAR FIRST**.
Install the updated CatanCraft JAR and new bundle; restart.
Run, as OP: `/catan map reloadassets`, `/catan map assetstatus`, `/catan map environmentstatus`, `/catan map updatemonuments`, `/catan map verify`.
`assetstatus` should show 32 ready and 0 missing. `updatemonuments` is R1 monuments-only (no district base or city repaste) and safe to repeat.
Do not use `/catan map refreshassets` for an R1-only update. `/catan map installseason1` rewrites map.json; only use it when intentionally resetting the map.

For a *separate new world*, extract `CatanCraft_Environment_V4_Monument_R1.zip` and set `level-name=CatanCraft_Environment_V4_Monument_R1`. It does not include prior nation state. Its `level.dat` LevelName is recognized as a prepared V4 map.

Blender static validation passed but requires real Minecraft player movement, door/protection, actual TaCZ gun penetration/cover, actual Superb Warfare clearance, monument capture/contest and `/nation` GUI accordion checks.
R1 capture anchors: Industrial (-86,81,-80); Military (88,81,-35); Refinery (22,81,107). Radius 14 for each.

Rollback: restore old JAR + bundle + backed-up entire world including world data and config together.