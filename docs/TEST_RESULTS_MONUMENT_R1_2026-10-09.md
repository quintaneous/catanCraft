# CatanCraft Environment V4 Monument Gameplay R1 — in-game acceptance log

**Test report:** 2026-10-09 (user screenshots)
**Source build:** `23fe52f723413a070330b45cba08c7903c425162` on `v0.1-foundation`
**CI:** `38005120975` (successful Java 17 build)
**Release:** Monument Gameplay R1, Forge 1.20.1

## Verified by server-side screenshot
- [x] `/catan map reloadassets` cleared schematic cache.
- [x] `/catan map assetstatus`: **32 ready, 0 missing/invalid** (shown twice).
- [x] `/catan map environmentstatus`: **authored world=true**, **16/16** prepared markers; world LevelName `CatanCraft Environment V4`. Message confirms no claim/restart/visual-refresh repaste of prepared environment.
- [x] `/catan map updatemonuments`: **Monument Gameplay R1 synchronized, 2635 changed blocks**. Command reported city environment and nation state untouched. **Not** an independent database-diff verification.
- [x] `/catan map verify`: **PASS, 26/26 checks matched**.
- [x] `/nation` Territories dashboard: collapsible Production & Upgrades, Buildings, and Construction sections visually function; collapsed and expanded sections shown. Other accordion edge cases not yet independently exercised.
- [x] Military Depot capture process **started and displayed progress** for Britain at **28%**. **Full completion, contesting and rewards not yet tested.**

## Pending in-game acceptance
- [ ] Industrial Complex: walk both closed interior loops, 3 independent capture routes, 2 ways up.
- [ ] Military Depot: walk 3 interior loops, 3 capture routes, 2 ways up.
- [ ] Refinery: walk 2 interior loops, 3 capture routes, 2 ways up, third lower escape.
- [ ] Test actual openable doors with world protection, movement without block breaking, lighting and collision.
- [ ] Allow Military Depot capture to reach 100%; confirm reward reaches national stockpile/treasury.
- [ ] Two nations enter capture zone: progress must pause when contested. Verify empty-zone behavior and rotating monument timer.
- [ ] Restart server after R1 sync; verify unchanged monument geometry, ownership, economy and upgrades.
- [ ] Test other `/nation` accordion states with multiple territories and repeated navigation.
- [ ] With TaCZ installed: penetration vs cover, long lines of sight, elevation and rooftop dominance.
- [ ] With Superb Warfare installed: real vehicle width/turning in service lanes; no guarantee from static clearance pass.

## Status
**Server R1 asset update accepted at installation/command level.** Manual walk-through and multiplayer/combat acceptance are still pending. Keep Environment V4 as the active world; do not replace it with Blender's fresh R1 world. City pads / V4 environment remain untouched by `updatemonuments`.

Screenshots were supplied in the project conversation on October 9, 2026.
