# CatanCraft v0.1.10 — Focused R5 pilot SOLO test checklist

**TEST COPY ONLY.** Keep the active world/installed mods untouched. Back up original world, mods, and config; run a separate disposable server with Java 17 / Forge 47.4.10, matching CatanCraft client+server JAR, and unchanged original R3/R4-A/R5 sidecars. NO multiplayer testing.

- [ ] Oldest requirement: if v0.1.9 already created a schema-1 PENDING/COMMITTED journal, rollback using v0.1.9 *on that same disposable copy* before replacing JAR; never discard journal to force apply.
- [ ] Confirm v0.1.10 startup, 38 assets ready / 0 invalid and previously purchased factory/nation data unchanged.
- [ ] Read-only `/catan r5 group a front_public_park` matches exact old states; without token `/catan r5 apply a front_public_park confirm` REFUSES with zero edits.
- [ ] On a clean, backed-up disposable copy, create **one** token in server `config/catancraft/R5_DISPOSABLE_WORLD_ONE_SHOT.txt` with exact content `I_BACKED_UP_THIS_DISPOSABLE_WORLD_AND_APPROVE_ONE_R5_WRITE`; apply once, confirm journal schema=2, state=COMMITTED and `world_root` points to that test save.
- [ ] Inspect park, surrounding roads/plots/Town Hall/factories, levels and national stockpile; no changes outside masked coordinates. Token consumed; repeat apply DENIED.
- [ ] Rollback same test save: exact original states restored and journal says ROLLED_BACK. Restart test server and verify persistence; old journal cannot be applied again.
- [ ] **Different world-root test**: using a second disposable world/instance with its own backup, copy ONLY the journal into its config (do NOT use the original save path); rollback MUST refuse `Journal belongs to a different world save` and touch no blocks.
- [ ] Dirty-before-state and unloaded chunk scenarios: apply refuses before writing or journaling; entity inside would-be solid block refuses.
- [ ] Separate disposable test: change one already written masked cell to a third state; rollback refuses all changes. Restore from system-level backup afterward.
- [ ] Record `latest.log`, the journal header (omit private filesystem names if sharing), and exact PASS/FAIL for above. No crowbar retest yet; issue #6 remains unresolved.

**Acceptance status before tester results: COMPILED if CI passes; GAMEPLAY NOT TESTED.** R5 physical civics, R6, house migration and main world remain blocked.
