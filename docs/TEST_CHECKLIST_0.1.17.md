# CatanCraft v0.1.17 — One focused Logistics Center solo test

**Test-world only. NO live server install.** The user's v0.1.16 Commercial District rollback and restart have PASSED; don't repeat. Use a backed-up **disposable developed City A world copy** with exactly one existing saved Logistics Center purchase. The old Commercial journal and R3/R4/R5 assets must remain unchanged.

## Install
- [ ] Stop the test server/close client. Make a full backup of the CURRENT disposable world (including `data` and all R5 journals), `mods` and `config`.
- [ ] Replace ONLY `catancraft-0.1.16.jar` with `catancraft-0.1.17.jar` in both test-server and matching client `mods` folders; do not keep both versions.
- [ ] Start test server, verify `/catan map assetstatus` returns **38 ready / 0 invalid**; City A is owned and the Logistics Center is already purchased.

## Denial test (must come first)
- [ ] WITHOUT creating approval, run `/catan r5 logisticspilot apply confirm`. It must refuse and NOT change blocks/resources or create an install journal. If it refuses for a different safety reason, STOP and send the refusal (do not force clear an obstruction).

## Approve first installation
- [ ] Check again that you're using a disposable backed-up **world folder containing `level.dat`**, not the live world.
- [ ] In that world root create `CATANCRAFT_R5_CITY_A_LOGISTICS_DISPOSABLE_APPROVAL.txt` with exactly:
`I_CONFIRM_THIS_IS_AN_OFFLINE_BACKED_UP_DISPOSABLE_CITY_A_TEST_COPY_ONLY`
- [ ] Keep City A Logistics site chunks loaded; keep players, mobs, vehicles/dropped items away from the construction footprint.
- [ ] Run `/catan r5 logisticspilot apply confirm`. Expected **3,511 ordered physical writes** across 2 stages, no additional Logistics purchase/treasury charge and world-local Logistics PENDING→COMMITTED journal. Refusal/PENDING: STOP and send exact output, don't manually touch blocks.

## In-world visual and safety inspection
- [ ] Logistics Center now exists; inspect warehouse/storage interiors, exterior, service access/road connection and lighting.
- [ ] 24 new barrels exist and stay **EMPTY**; no items placed or barrel name changes.
- [ ] Town Hall, factories, park, roads, military structures and existing purchases unaffected; nation resources/treasury unchanged.
- [ ] Take one exterior and one interior screenshot.

## Restart and guarded rollback
- [ ] Stop/restart test server normally. Logistics Center remains intact, saved Logistics purchase is unchanged. Journal `world/data/catancraft_r5_city_a_logistics_v1.json` exists; DO NOT edit/delete it.
- [ ] Run `/catan r5 logisticspilot diagnose`. Must report zero unsafe third states and READ-ONLY (normal spruce-door-open states are allowed). If unsafe >0: STOP.
- [ ] Move everybody/items/vehicles out; verify 24 barrels EMPTY. Run `/catan r5 logisticspilot rollback confirm`.
- [ ] Confirm Logistics Center and service link removed; exact original ground restored; Town Hall/factories/Commercial purchase/economy unaffected; Logistics purchase STILL owned; journal kept as ROLLED_BACK.
- [ ] Restart once more and verify original terrain/purchases remain restored.

## Send back
Send screenshots/log output for: denial → apply → diagnosis → rollback; exterior/interior pictures; and PASS/FAIL on restart/purchases. Do not send private file paths without redacting.

**No multiplayer tests.** No Commercial reinstallation, no R7 or roof-mod replacement, no R6 placement, no crowbar fix validation in this build.
