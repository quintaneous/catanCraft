# CatanCraft v0.1.21 — One-time R7 world + purchase-driven building test

**Only on a NEW copied disposable server with separate R7 world. DO NOT install/enable against a live or previously developed world.**

1. Stop old server. Back up old worlds, mods, and config. Leave all prior rollback journals untouched.
2. Unzip CatanCraft_0.1.21_R7_Integrated_TEST_KIT.zip. Copy only its whole `CatanCraft_Season1_R7_Development` folder into the root of a separate test-server copy.
3. Confirm inside that folder: `level.dat`, `region/` (16 .mca files) and `CATANCRAFT_R7_AUTOBUILD_DISPOSABLE_TEST_ONLY.txt` containing the exact authorized one-time R7 text. Do not rename the world directory.
4. In that test server's `server.properties`: `level-name=CatanCraft_Season1_R7_Development` (not the old world). Never overwrite existing saves.
5. Replace old CatanCraft JAR with v0.1.21 on test server AND client. Do not change unrelated Forge/TaCZ/Superb Warfare mod versions.
6. Copy required config files from the kit's `REQUIRED_CONFIG_FILES` to `config/catancraft/`: `schematics_bundle.zip`, pinned `CatanCraft_R5_City_Rework_And_Civic_Assets.zip`, `R5_READ_ONLY_PROBE_INDEX.zip`, plus `schematics/thall4.schem` and `schematics/thall5.schem`.
7. Start server and client. `/catan map installseason1`, `/catan map reloadassets`, `/catan map assetstatus`: expect **40 ready, 0 invalid**. `/catan map environmentstatus`: authored V4 baseline=true, 16/16 prepared markers. `/catan map verify` must show no unexpected protected/map errors.
8. Inspect R7 wilderness/trees/roads/bridges and City A. Allow a normal restart and verify changed terrain persists, no leaf decay/drop problems. Never run force-refresh over city pads.
9. Create a new nation and start City A normally: `/nation start 1`. Verify the raw producers, TH1, treasury and resources.
10. Via normal GUI or `/nation build a commercial_district`, BUY Commercial District ONCE. It must physically construct immediately, with one cost, and a committed journal `world/data/catancraft_r5_auto_a_commercial_district_v1.json`. Do NOT run any `commercialpilot apply` or import a schematic manually.
11. Upgrade City A to Level 2 normally, and buy `/nation build a logistics_center` ONCE. Expect physical warehouse immediately, one charge, second committed auto journal `world/data/catancraft_r5_auto_a_logistics_center_v1.json`, and normal Logistics processor bonus. No manual installer.
12. Walk both buildings; verify doors, full roofs/shops, warehouse barrels and road connections. Note Commercial left roof remains an existing visual flaw, not an R7 bug. Keep barrels empty during initial acceptance.
13. Restart and confirm BOTH physical buildings, ownership, treasury, producer output, town hall, world changes and both journals persist. No extra purchase/charge or repaste; building UI and GUI commands still work. Check no unexpected ground items.
14. If any step fails, STOP AND PRESERVE the new world and its auto journal(s). Do not delete files, retry purchases, import buildings or run old rollback pilots. Send exact output + screenshots to main development.

**One end-to-end test** (not repetitive manual imports). Record the exact balance changes, purchase output, 40/0 asset status and post-restart screenshots. Later city-I rotation and all-city multiplayer stress tests can wait until this initial baseline is accepted.
