# v0.1.18 Focused Door-Item Rollback Regression Checklist

TEST WORLD COPY ONLY. Do not use live server or overwrite previous construction journal.

1. Back up existing current v0.1.17 rolled-back test world INCLUDING its Logistics journal. Do not delete journal. Find a distinct NEW disposable pre-Logistics-install City A backup (one Logistics purchase; no logistics journal). If unavailable, STOP; do not reuse the old world or try to force another install.
2. Stop server/client; replace ONLY the CatanCraft JAR 0.1.17 with 0.1.18 in BOTH test-server and client mods folders; leave original R3/R4/R5 bundle and all other mods/config untouched.
3. Start separate fresh disposable world; run /catan map assetstatus (38 ready / 0 invalid), verify saved City A Logistics purchase.
4. Before consent run /catan r5 logisticspilot apply confirm; must refuse without writes or new journal.
5. Inside NEW disposable world root next to level.dat create CATANCRAFT_R5_CITY_A_LOGISTICS_DISPOSABLE_APPROVAL.txt with exact content I_CONFIRM_THIS_IS_AN_OFFLINE_BACKED_UP_DISPOSABLE_CITY_A_TEST_COPY_ONLY
6. Clear players, mobs, vehicles, and items from the area. Run /catan r5 logisticspilot apply confirm; expect 3,511 ordered writes, Logistics COMMITTED journal, 24 new empty barrels, no second purchase or economy charge.
7. Open BOTH spruce doors intentionally; do not break any blocks or fill/rename any barrels. Restart server and run /catan r5 logisticspilot diagnose; expect 0 unsafe-third-states.
8. Confirm no door item already lying on ground. Ensure all barrels empty and footprint free of entities; step away. Run /catan r5 logisticspilot rollback confirm. Expect complete rollback, 0 new spawned item entities, and ideally 0 cells auto-restored during the loop.
9. Search both doorway positions and former Logistics footprint for any dropped spruce-door items. If any appear or rollback refuses, STOP and preserve the world/journal. Send screenshots and exact command output. Do not manually clear blocks, items, or history.
10. Restart server once more; verify complete original terrain/road, no unwanted item drops, saved Logistics purchase, Town Hall/factories/park, nation treasury/resources and 0 new barrels are unchanged.
Send screenshots of diagnosis, rollback output including count, former entrance after rollback, and PASS/FAIL for door drops, restart and economy. NO MULTIPLAYER TESTS.
