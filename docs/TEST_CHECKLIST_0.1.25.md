# v0.1.25 — Repair the CURRENT Town Hall (no repeat purchases)
Use the existing developed R7 TEST world, not live. Back it up, including data and
journals. No new world, manual building import, paid upgrade or civic reinstall.

1. Stop server normally and close Minecraft. Back up the current world in place.
2. Replace only the CatanCraft JAR in server/client mods with v0.1.25; keep one JAR.
   Keep existing config, R7 folder name/marker, R1 schematics and journals unchanged.
3. Start the same test server. In /nation record City A's current level. Do not
   downgrade it or buy another level. Existing level should be 4 or 5.
4. Stand on the road outside the whole hall. Run /catan townhall status a.
   Expected current R1 or already R2, 229125 cells checked, no writes/charge.
   On a refusal, stop and preserve diagnostics. Do not fix blocks manually.
5. Record /nation info and /nation stockpile; note management lectern/book contents.
   Leave ordinary doors open/closed as they are. Empty the hall of entities.
6. Run /catan townhall repair a confirm ONCE.
   Expected repair to R2, 584 changes, same city level/book/resources/purchases.
   Already R2 may correctly report 0 changes and no charge.
7. Walk through the main entrance and BOTH rear exits in both directions. Walk
   BOTH staircases up and down, straight through their top landings without jumping.
   Check new railings, the intentionally open atrium and unchanged exterior.
8. Check management UI/book and unchanged paid level. Confirm no unexpected items.
   Do not rebuild Commercial/Logistics; only note any new damage.
9. Run the repair command one more time: expected ALREADY REPAIRED, zero writes and
   zero charge. This is not another import or purchase.
10. Stop/restart normally once. Confirm repaired access, R2 status, current city
    level, manager/book and nation records persist with no second charge.

If an interruption is reported, stop and retain the log/journal. Do not delete it.
Status is safe. The new resume/rollback commands are recovery tools, not routine
owner tests. Do not deliberately crash or roll back a successful repair.
Do not repeat TH3-to-TH5 purchases just to view a lower-tier hall.

Report:
Current City level:
Status preflight:
584-cell repair / already R2:
Main and both rear doors:
Both stairs and top landings:
Management book/UI:
No level/resource change:
Repeat command no-op:
Restart persistence:
Visual issues:
