# CatanCraft v0.1.19 — NEW authored Catan world in-game checklist

**Test-server only. Keep old developed world and test-world journals untouched. No R7 installation yet.**

## Create independent fresh Catan world
- [ ] Stop server/client, back up full existing TEST server (including mods/config/world). Do not overwrite any live world.
- [ ] Download NEW fresh-world starter ZIP and v0.1.19 JAR, extract world folder `CatanCraft_Season1_Clean_019` into a **copied test server root**, alongside the old `world` folder.
- [ ] Open only the copied TEST server's `server.properties`; set `level-name=CatanCraft_Season1_Clean_019` (folder name must match). Never replace old `world`; never edit internal `level.dat`.
- [ ] Replace ONLY v0.1.18 CatanCraft JAR with v0.1.19 in TEST server and client `mods`; keep other mods and Forge 1.20.1 intact.
- [ ] In `config/catancraft/`: verify `schematics_bundle.zip`, `CatanCraft_R5_City_Rework_And_Civic_Assets.zip`, `R5_READ_ONLY_PROBE_INDEX.zip` are present and unchanged. Other R3/R4 assets/config remain untouched.
- [ ] Start new world, verify same authored 16-territory landscape, roads, four bridges, monuments, and City A. Confirm no old nation/purchases/journals carried over.

## Command smoke checks
- [ ] OP: `/catan map installseason1` — Season One map loads with 16 territories.
- [ ] OP: `/catan map reloadassets` and `/catan map assetstatus` — expect **38 ready; 0 invalid**.
- [ ] OP: `/catan map environmentstatus` — 16 preinstalled V4 environment layers tracked, V4 authored baseline recognized.
- [ ] OP: `/catan map verify` — map compatibility check; report any mismatch. **Do not run /catan map refreshassets on this clean source just to test; it may repaste civic/monument regions.**
- [ ] Player: `/nation start 1` for City A; confirm TH1 and raw producer buildings activate, ownership saved.

## Purchase and both civic sites in the NEW world
- [ ] On owned City A, legitimately purchase Commercial District at city level 1 through the existing economy; save purchase; no physical install expected automatically at this stage.
- [ ] Upgrade to city level 2, legitimately purchase Logistics Center; check Town Hall changes to TH2 and ownership/economy persist.
- [ ] OP: `/catan r5 constructioncheck a commercial` and `/catan r5 constructioncheck a logistics` — expect 0 blockers on unmodified authored world. If any issue, STOP and send exact output.
- [ ] Back up this newly purchased fresh test world BEFORE physical pilots.
- [ ] Test denial: `/catan r5 commercialpilot apply confirm` without authorization must refuse with no changes.
- [ ] Put **Commercial** one-use approval `CATANCRAFT_R5_CITY_A_DISPOSABLE_APPROVAL.txt` beside the current fresh world `level.dat` with exact phrase `I_CONFIRM_THIS_IS_AN_OFFLINE_BACKED_UP_DISPOSABLE_CITY_A_TEST_COPY_ONLY`.
- [ ] Apply commercial OP pilot — expect 2,835 ordered writes; 8 empty barrels. No duplicate purchase/charge. Inspect full building and original roof visual issue; do NOT declare visual fix.
- [ ] Test denial: `/catan r5 logisticspilot apply confirm` without Logistics approval must refuse.
- [ ] Put Logistics approval `CATANCRAFT_R5_CITY_A_LOGISTICS_DISPOSABLE_APPROVAL.txt` beside the SAME fresh world `level.dat` with exact same phrase.
- [ ] Apply logistics OP pilot — expect 3,511 ordered writes; 24 empty barrels. Check no overlap with Commercial.
- [ ] Walk between both buildings; check sidewalks, warehouse truck access, shops, roads, bridge access and neighboring structures. Don't place items in barrels or modify construction.
- [ ] Stop/restart server with **both physically installed**, confirm purchases, levels, treasury and all buildings persist.

## Safe rollback
- [ ] Read-only `/catan r5 commercialpilot diagnose` and `/catan r5 logisticspilot diagnose` — both should show 0 unsafe third states; door interactions may be recognized.
- [ ] Move all entities/items away, confirm 8+24 barrels empty.
- [ ] Roll back each separately: `/catan r5 logisticspilot rollback confirm`, then `/catan r5 commercialpilot rollback confirm`. Both must report successful direct restoration and 0 spawned items.
- [ ] Inspect restored city sites, no doors/blocks dropped, no holes, nearby houses/factories intact; confirm saved purchases remain.
- [ ] Restart server and recheck original terrain, purchases and economy.
- [ ] Share screenshots of environmentstatus, first City A, both buildings together, first restart, diagnosis and both rollbacks.

**STOP on any command refusal, asset mismatch, R5 journal drift, or unexpected change. Keep journals and save copy; do not manually clear/overwrite. Do not use R7 ZIP until separately verified.**
