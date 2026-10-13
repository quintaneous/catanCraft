# CatanCraft v0.1.19 — Fresh Authored World Bootstrap

**WORLD SAFETY: test server ONLY.** This update supports starting a *completely new*, clean CatanCraft Season One test world from the actual authored V4/R2 world. It is NOT a Minecraft procedural world-generation mod. Vanilla Create New World does not create the designed 16-territory map, and it does not supply the intended rivers/bridges, cities or historical source voxel conditions.

## Base-world source and archive authenticity
- Canonical archived map: `CatanCraft_Environment_V4_Monument_R2.zip`, source SHA256 `7a4cea6061855ee67da856fc1a2656eb6a26f5e5fb51144c9c84823afe4eb1d0`.
- The source ZIP was retrieved from the shared Library root. Its 17 members (1 level.dat + 16 Anvil .mca region files) passed CRC checks.
- Source `level.dat` internal level name: **`CatanCraft Environment V4 - Visual Polish R2`**, discovered from the actual compressed NBT. Do NOT edit it.
- In v0.1.18 `MapAssetService.isEnvironmentV4World` recognized the exact names for original V4 and Monument Gameplay R1, but **not** the authored V4 Visual Polish R2 LevelName. On a new server, neutral claims could therefore mistakenly paste a large air-inclusive environment replacement mask. v0.1.19 fixes recognition of the EXACT LevelName, so `/catan map environmentstatus` and `/catan map installseason1` can adopt the preinstalled 16 environment layers.
- Do NOT use the unrelated River & Bridges V3 archive as the default fresh base for this R5 City A test. Do NOT create a vanilla world instead.

## R5 City A clean-world source audit
Independent offline Anvil blockstate check, against the real V4 R2 `region/r.-2.-2.mca` authored chunk data and unmodified pinned R5 source contracts:
- Commercial: `cleanup_tree_16_10` **92**, `commercial_sidewalk` **231**, `COMMERCIAL_DISTRICT` **2,512** ordered writes = **2,835** writes, **2,793** unique positions. **0 exact expected-before mismatches** in source.
- Logistics: `logistics_service_connection` **158**, `LOGISTICS_CENTER` **3,353** writes = **3,511** unique positions. **0 expected-before mismatches**.
- Total 6,346 ordered steps, 6,304 unique positions, 0 intersecting source write cells. The two site masks are disjoint (92-block minimum Chebyshev X/Z column distance), preserving both sites.
- This confirms OFFLINE compatibility against the fresh source only, not any runtime purchases, level updates, players, chunks, physics or installed R5 streetscape variants. Protect original R3/R4/R6 and the player's developed world.

## Changes in v0.1.19
- Small, exact LevelName recognition fix in `MapAssetService`; asset bundles and map JSON unchanged.
- Retains v0.1.18 Commercial and Logistics physical pilot installers, their separate first-use City A world approvals, SHA-checked durable journals, item-drop-safe 0.1.18 rollback, economy logic, Town Hall 1/2/3 paths and all previously accepted tests.
- **Does not** automatically construct Commercial and Logistics when purchased; those buildings remain saved economy data first, with operator-only City A physical test pilots. Do not imply automated production integration is finished yet.
- **Does not** install R7 terrain. The currently shared R7 terrain ZIP was corrupt/truncated on retrieval, with checksum mismatch; the handoff reports 57 source-static features but the usable archive must be reuploaded and validated first.
- Does not integrate Blender's TH4/TH5 or corrected Commercial roof yet.

## Safe path for user
1. Stop test server, leave existing 0.1.18 world and journals intact. Take a full backup of server/mods/config.
2. Work in an **independent copied test-server folder**. Extract the new world from `CatanCraft_0.1.19_CLEAN_WORLD_STARTER.zip` as a named folder e.g. `CatanCraft_Season1_Clean_019` beside (NOT on top of) old world folder.
3. Set `level-name=CatanCraft_Season1_Clean_019` in that TEST server's `server.properties`. This controls folder only, not the internal LevelName. DO NOT use a live server or edit original `level.dat`.
4. Swap only `catancraft-0.1.18.jar` -> `catancraft-0.1.19.jar` on client and copied test server. Keep all other mods as before.
5. Use bundled `schematics_bundle.zip` at `config/catancraft/schematics_bundle.zip`. Keep `CatanCraft_R5_City_Rework_And_Civic_Assets.zip` and `R5_READ_ONLY_PROBE_INDEX.zip` in that same `config/catancraft/` folder. They are identical to v0.1.18.
6. Start server and client, run `/catan map installseason1`, `/catan map reloadassets`, `/catan map assetstatus` (38 ready / 0 invalid), `/catan map environmentstatus` (16 layers already installed/recorded), `/catan map verify`.
7. Run `/nation start 1` to claim City A for a new test nation (verify the in-game GUI/command context). At that moment no R5 commercial/logistics economic building is yet purchased.
8. Purchase Commercial and Logistics via the existing in-game economy at required city levels (Commercial city level 1, Logistics city level 2), then run read-only `/catan r5 constructioncheck a commercial` and `/catan r5 constructioncheck a logistics`. If purchase costs or a special test-funding path are unavailable, **do not fake nation data or run direct file edits**; stop and report.
9. For strictly disposable physical integration, continue with the v0.1.18 one-shot approval/OP-only Commercial and Logistics pilot procedures on this fresh City A owned save, after a backup. Both private journals must be initially absent and remain separate. Do not use R7 or a modified R5 roof yet.
10. Report gameplay integration, both visual placements at once, walk/drive access, restart persistence, honest game level/treasury, and separate safe rollbacks; test only on disposable world.

## Pending milestones
- Automatic, transactional physical R5 building on purchase in all sixteen cities is NOT implemented and should come after this fresh-world compatibility trial.
- R7 Blender source ZIP re-upload and independent integrity verification, followed by a separate guarded terrain dry-run, remains blocking full integrated environmental deployment.
- Separate TH4/5 schemes pending from Blender. R5 Commercial roof asymmetry remains unaccepted visually.
- Issue #6 Superb Warfare crowbar packing recovery exploit OPEN.
