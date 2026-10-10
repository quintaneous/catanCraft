# CatanCraft — combined R2 visual polish + TaCZ/Superb Warfare Phase 1 checklist

**Baseline:** Minecraft Forge 1.20.1 / 47.4.10, Java 17. Development branch `v0.1-foundation` (procurement commit `74b642cf6b2937be63ee3156515417b3f6f5f167`).

**Source validation:** Blender `R2-VISUAL-VALIDATION.md`, `PLACEMENT-CONTRACT.md`, `INDUSTRY-PLOT-PROPOSAL.md`, `existing-world-r2-patches.json`, and R2 asset ZIP, inspected October 9, 2026 (US Eastern). CatanCraft procurement source and prior server crash log also reviewed.

## CURRENT STATUS / STOP RULES

- **R2 Blender is delivered, but NOT YET integrated into the CatanCraft runtime/world.** Its static checks passed, not in-game checks.
- **Procurement Phase 1 JAR successfully compiled.** Actual TaCZ/Superb Warfare server runtime acceptance remains untested.
- **Your last server startup was blocked by missing `kotlinforforge` version 4.11.0 or newer.** Add the Forge 1.20.1-compatible Kotlin for Forge dependency (and any other dependencies the next log reports) before testing procurement. Prefer Java 17; the crash log showed Java 25.0.1, though this was not the reported blocker.
- **Never replace your developed world with `CatanCraft_Environment_V4_Monument_R2.zip`.** That world ZIP is for a separate **fresh testing world** only.
- **Never repaste the full `starter_settlement_v2.schem`, `city_environment*.schem`, `central_district_base.schem`, or combined monument district in your developed server.** R2 needs safe individual-asset updates and a guarded 96-group house-patch applier that is **not implemented yet**.
- Blender's proposed six new factories were **not built**. Their absence is not an R2 regression.

## A. PRE-FLIGHT / SAFE INSTALLATION (do first)

- [ ] A01 — Stop server; back up **full world including `data/`**, `config/catancraft/`, `mods/`, and `server.properties`.
- [ ] A02 — Launch Forge **1.20.1 / 47.4.10** on **Java 17** (or record another working runtime); verify server actually boots, not just that the Forge bootstrap runs.
- [ ] A03 — Add **Kotlin for Forge 4.11.0+ compatible with 1.20.1**, required by the installed Superb Warfare `0.8.9.2` JAR. Resolve any subsequent mod dependency errors one at a time.
- [ ] A04 — Check TaCZ, Superb Warfare and their required dependencies are installed **on server and client**, using versions for Forge 1.20.1; no Fabric/NeoForge files and only **one** CatanCraft JAR in each `mods` directory.
- [ ] A05 — Keep current developed Environment V4 world and existing R1 schematic bundle until an R2 integration build is supplied. Do not paste Blender R2 files manually to the active server.
- [ ] A06 — After boot, run `/catan map assetstatus`, `/catan map environmentstatus`, `/catan map verify`. Expected baseline: **32 ready / 0 invalid**, **16/16 prepared**, **26/26 checks** (report differences rather than guessing a reset).
- [ ] A07 — Open `/nation` and confirm nation overview and collapsible sections still work.

## B. PROCUREMENT + ADMIN TOOLS — TESTABLE SOLO AFTER A02–A04

- [ ] B01 — `/speed` toggles x6 walking/flight for OP. `/speed 10` increases movement, `/speed 1` restores original walk/flight permissions. Rejoin after logout and confirm boost is cleared.
- [ ] B02 — `/nation procure list` shows `pistol`, `ammo9`, `ak47`, `ammo762`, `pickup`, and `lav150` with their costs and factory requirements.
- [ ] B03 — Capture before-purchase nation treasury and resource amounts (`/nation stockpile`). As nation leader, `/nation procure pistol` gives a **real TaCZ Glock 17**, once, and deducts exactly **$350 +5 Wood +5 Stone**.
- [ ] B04 — `/nation procure ammo9` gives **32 rounds 9mm** and deducts **$100 +5 Stone**. Check Glock loading, reloading, aiming and firing at a safe test target.
- [ ] B05 — Test full inventory and insufficient treasury/resources: purchase must fail **without** creating an item or deducting money/resources. Note if an invalid/missing gun-pack ID is reported.
- [ ] B06 — With a Weapons Factory owned by the nation, `/nation procure ak47` produces the actual AK-47; `/nation procure ammo762` produces correct **7.62×39** ammunition. Check weapon firing and once-only national deductions. Without the factory, purchases must be rejected without charge.
- [ ] B07 — With a Vehicle Factory owned, stockpile funded, and **flat clear ground in owned territory**, `/nation procure pickup` spawns a **real Superb Warfare pickup directly in the world**, not a portable item. Expected cost **$4,000 +20 Steel +10 Mechanical Parts +8 Fuel**.
- [ ] B08 — Board/start/steer/stop/dismount the pickup; record whether it needs fuel, a key, or charging to drive normally. No gameplay functionality should be assumed merely from entity spawning.
- [ ] B09 — Sneak + crowbar + right-click the bought pickup; it **must not be converted into recoverable items**. Check other interact/pickup methods and report any exploit; crowbar protection is the only specifically implemented guard in Phase 1.
- [ ] B10 — Move to unowned territory or obstruct the spawn area and try to buy a vehicle. Purchase must fail, spawn nothing, and deduct **nothing**. Check invalid slope/water and whether the vehicle is entirely within the nation's territory.
- [ ] B11 — With sufficient materials and vehicle factory, `/nation procure lav150` spawns a drivable LAV-150, costs **$8,500 +50 Steel +25 Mechanical Parts +20 Fuel +5 Electronics**, and survives a server restart without becoming a portable item.
- [ ] B12 — Test the monument capture reward receipt after a successful solo capture: only your nation members should see a **detailed chat payout**; the public broadcast remains generic. Military Depot expected **+$1,500, +25 Steel, +30 Fabric, +20 Explosives**. Verify funds/stockpile match the message.
- [ ] B13 — Rejoin after restart; verify nation ownership, procurement resources, no duplicated vehicles/items, `/nation` UI, and monument rotation/capture status.

### Useful SOLO commands (only on a backed-up test copy where needed)

```mcfunction
/catan debug money 50000
/catan debug give wood 100
/catan debug give stone 100
/catan debug give steel 150
/catan debug give mechanical_parts 80
/catan debug give fuel 60
/catan debug give electronics 20
/catan debug give explosives 20
/nation procure list
/nation procure pistol
/nation procure ammo9
```

To unlock restricted purchases, build the proper factory normally or, **on a disposable test save only**, use `/catan building add <owned_territory_id> weapons_factory` and `/catan building add <owned_territory_id> vehicle_factory`. The debug method modifies saved economy data; it is not a temporary visual demonstration. Use `/catan monument list`, then `/catan monument activate <listed_id>` for a specific capture test. Use `/nation stockpile` before and after each purchase.

## C. R2 STATIC-TO-LIVE INTEGRATION GATE — DEVELOPER, NOT PLAYER TEST

- [ ] C01 — Verify **34** main schematic exports (32 active plus two reference/installation), **13 changed / 21 unchanged**; retain all original filenames, dimensions, anchors, rotation conventions, capture coordinates, and world profiles. There are four additional **test-fixture schematics** in the archive that must not be installed as normal city assets.
- [ ] C02 — Integrate individually changed **three monument**, **six existing plot**, and **two resource-site** schematics. Preserve unpurchased plot slots, inactive neutral sites and fixed ownership/economy records. Never use `refreshassets` as a bulk destructive R2 update.
- [ ] C03 — Implement a guarded house migration from `existing-world-r2-patches.json`: **96 house groups / 37,376 exact old↔new state records**. Match expected-old or already-new state for every cell, abort conflicting houses as a unit, snapshot/rollback or journal partial failures, mark completion idempotently, update lighting, and apply only to activated settlements.
- [ ] C04 — Add a dedicated **safe R2 synchronization path** that respects existing placed-asset versions, excludes Town Halls and full environment restamps, and reports exactly what was skipped or upgraded. Do not invent a command name until implemented and compiled.
- [ ] C05 — Build and test the new combined CatanCraft JAR and R2 asset bundle on an **isolated clone** of your existing world. Verify no lost ownership, building instances, inventories, plot assignments, money, stocks, or Town Hall upgrades.

**Stop here for R2 playtesting until C01–C05 are complete.** R2 files arriving from Blender does **not** mean your server has their changes yet.

## D. R2 MONUMENT PLAYTEST — ONLY AFTER C01–C05

- [ ] D01 — Industrial Complex: nighttime, normal-brightness screenshots of capture room and low interior at world-feet **`-81 81 -74`** and **`-109 81 -86`**. Doors, cover, stairs and ceiling lamps usable with protection rules on. Test **2** interior loops, **3** capture approaches and **2** elevated paths.
- [ ] D02 — Military Depot: from inside former awkward garage look outward at **`80 81 -70`**, then inspect southern apron at **`80 81 -55`**. Verify **new 9-wide × 6-high garage opening** has credible clearance/turning and no facing wall; test **3** interior loops, **3** capture routes and **2** elevated paths.
- [ ] D03 — Actually drive **purchased Superb Warfare pickup and LAV-150** into/out of the Depot garage, through apron and west exit. The static validation models a **3-wide × 5-long × 3-high body** with a 6-block turning radius only; larger models and collision/steering are not yet proven.
- [ ] D04 — Military Depot nighttime interior at **`54 81 -80`** and capture at **`93 81 -35`**. Check fixtures, wall seams, doors and whether vehicles can actually exit rather than just fit through a garage door.
- [ ] D05 — Refinery: nighttime at **`26 81 110`** (capture), **`-5 81 60`** (lowest interior) and **`5 98 57`** (upper route). Verify **2** interior loops, **3** ground approaches and **2** elevated paths; no dark/unusable stairs or trapping gates.
- [ ] D06 — Fire real TaCZ guns at monument cover and from elevated sightlines; document whether supposed solid cover works and whether any overpowered line-of-sight/corner exists. Test actual combat, not just geometric routes.
- [ ] D07 — Confirm the monument capture point HUD remains correctly aligned, reward messages and treasury awards still work, and new asset geometry persists after a restart.

## E. R2 CITIES / RESOURCES / PERSISTENCE — ONLY AFTER C01–C05

- [ ] E01 — Visit all **six village houses** in an activated starter town: new windows, varied brick/timber, roofs/vents/chimneys/doorsteps render properly without wall gaps or broken doors. Verify village alleys remain navigable. No purchase/inventory overwritten.
- [ ] E02 — Check **six existing production exteriors**: Vehicle Factory, Quarry, Weapons Factory, Steel Mill, Farm, and Lumberyard. Verify differentiated facades and roof vents, usable entrance and delivery area, correct building orientation. **Do not expect unpurchased factory structures to appear.**
- [ ] E03 — Check **Iron/Oil** and **Coal/Copper** strategic sites for material identification, work lights, equipment and access without clipping or loss of normal production.
- [ ] E04 — Activate/revisit north- and south-facing cities and verify proper 0°/180° orientation, plot boundaries, no duplicated buildings and no shifted roads, bridges or Town Halls. Confirm inactive neutral territories remain untouched until claimed.
- [ ] E05 — Buy an ordinary building, claim a neutral territory, upgrade City/Producer, set a stock target, and run a 15-minute production cycle. Check outputs/consumption/cash still function after visuals are updated.
- [ ] E06 — Restart the server twice. Verify house-patch replay is no-op, assets do not repaste, purchased factory upgrades and all nation stocks persist, and `/catan map verify` passes.
- [ ] E07 — Attempt a deliberately modified/conflicting **test house** on a throwaway save: R2 guarded migration should refuse that whole house without overwriting the player's edit or touching nearby purchases; record conflict details.

## F. NOT IN THIS RELEASE / DEFERRED

- [ ] F01 — Review **`INDUSTRY-PLOT-PROPOSAL.md`** separately: Blender recommends retaining **three dedicated starter raw-producer plots** (plot_2 quarry, plot_5 farm, plot_6 lumberyard) and making **three** 31×31 industrial plots (plot_1, plot_3, plot_4) flexible for future industry choice. This is a **design proposal**, not working code or six new schematic files.
- [ ] F02 — After plot design approval, author and implement **Textile Mill, Chemical Plant, Machine Shop, city Refinery, Concrete Plant, Electronics Factory**. Do not score their absence as an R2 test failure.
- [ ] F03 — Later, with a **second independent player/nation**, verify enemy nation sees only generic capture announcement, contested monument capture pauses, nation-only procurement/security permissions, enemy vehicle access, and PvP/siege damage/protection.

## RESULTS TEMPLATE

For each failed or unclear item, send: **Test ID; result (PASS/FAIL/BLOCKED); exact command or coordinates; expected versus actual; screenshot/video; and newest `latest.log`/crash report for server failures.** Distinguish **BLOCKED (not installed or not implemented)** from **FAIL (installed and did not work)**.

### Developer reference

- Blender R2 visual validation and placement: shared project Library root, plus `CatanCraft-V4-R2-Visual-Polish-Assets.zip` and `CatanCraft_Environment_V4_Monument_R2.zip` (fresh world only).
- TaCZ/Superb Warfare Phase 1 procurement: development commit `74b642cf6b2937be63ee3156515417b3f6f5f167`; compiled build `38020144053`.
- Server Kotlin crash: Superb Warfare 0.8.9.2 requires kotlinforforge >= 4.11.0; the uploaded latest.log indicated none installed.