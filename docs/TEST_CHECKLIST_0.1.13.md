# CatanCraft v0.1.13 — focused solo construction audit checklist

**Do this ONLY on the existing backed-up DISPOSABLE test world. Do not update the live CatanCraft server or overwrite its mod files.**

1. Stop test server, back up world/config/mods, then install `catancraft-0.1.13.jar` on test server and matching client, replacing only the older CatanCraft JAR. Keep all existing R3/R4-A/R5 asset files and historical park journal.
2. Start test server; `/catan map assetstatus` should remain 38 ready / 0 invalid. No need to repeat previously passed economy, park apply/rollback or v0.1.12 preview.
3. Keep City A loaded and run `/catan r5 constructioncheck a commercial`. It must be **READ-ONLY, ALWAYS NO CONSTRUCTION APPROVAL**. Record unique proposed union cells, cross-stage overlapping coordinates, digest, proposed barrel count and entity collisions.
4. Run `/catan r5 constructioncheck a logistics`. Record same summary; the report should count 24 proposed barrels, with no automatic installation/charges.
5. Ensure none of the 92 tree blocks is removed, no sidewalk or civic appears, and Commercial/Logistics saved levels, inventories and national stockpile remain unchanged.
6. Any `BLOCKED`/unloaded/occupied result is a **correct safety refusal**, not an invitation to edit world terrain. If there's a refusal, share the exact `R5 CIVIC AUDIT` lines from `latest.log` or screenshots.
7. No multiplayer and no crowbar retest requested. The crowbar exploit remains open; R6, house updates and civic installs remain disabled.

**Status before tester reply:** compilation only, physical civic construction **NOT IMPLEMENTED**. This build performs an audit, not the installation itself.
