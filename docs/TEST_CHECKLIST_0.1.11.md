# CatanCraft v0.1.11 — Targeted SOLO retest after R5 park rollback failure

**Use ONLY your existing separately backed-up disposable test-server copy.** Keep the normal world and installed JAR untouched. The previously passed 537-cell apply does NOT need to be repeated.

- [ ] Stop test server. Save a complete separate backup of the currently installed (modified) park world, the config/catancraft/r5_test_journal folder, and mods.
- [ ] Copy only catancraft-0.1.11.jar into test server and client's mods folders. Remove older catancraft JARs. Preserve all supporting config/catancraft R3/R4-A/R5 sidecars and journal JSON **without edits**.
- [ ] Start test server with Java 17 / Forge 47.4.10. Do NOT create new one-shot approval file and do not reapply park. Confirm client/server start and journal exists.
- [ ] Run `/catan r5 rollback a front_public_park confirm` ONCE, in this same disposable world. Expected after fix: success; exact original 537-cell baseline restored; journal now `ROLLED_BACK`.
- [ ] If refused, stop, do not manually modify any cell or journal; send the complete chat error, `latest.log`, and `A_front_public_park.json`. New diagnostic will show *actual*, *before*, and *installed* canonical blockstates.
- [ ] After successful rollback, visually inspect park, streets, Town Hall, factories and nation state; restart test server and confirm restoration persists.
- [ ] Optional later on a *different clean disposable copy*: intentionally replace a placed leaf with **stone** (not a changed leaf-distance property) and confirm rollback refuses before changing any other cell. Restore disposable backup. This is needed before general world-write acceptance, but is not a prerequisite for recovering this test copy.

**Status before new user results: COMPILED if CI passes; IN-GAME NOT ACCEPTED.** R5 tree cleanup, physical civics, R6 defense purchases, houses and original live world are blocked. Crowbar issue #6 remains open.
