# Physical factory counter audit

**In a Weapons Factory, use the crafting table on spruce planks.** The factory also has a crafting table on smooth stone; that table has no shop binding. The Vehicle Factory has one crafting table, on smooth stone. For either shop, use an empty main hand. Holding a placeable block, bucket, flint and steel, fire charge, or bone meal cancels the server-side click before the shop handler runs. The weapon policy also blocks use of disabled handheld items. These are two reproducible ways the intended interaction can be missed. Quin’s earlier incident did not record the targeted block or held item.

## What was verified

Gameplay source `6ed6eaf31f946195f8b75fa869308c915537a6ed`. Snapshot covers completed combined-source layouts 2–6. Each had a successful native progression process and a separate successful restart process. The audit rechecked each process record against its original log hash and checked the source, world identity and saved phase results.

| Question | Evidence |
| --- | --- |
| Are the candidate coordinates unique across the map? | The reviewed map and factory schematics give 48 Weapons Factory and 48 Vehicle Factory counter locations. All 96 are distinct from one another and from the 16 Town Hall lecterns. Each site uses the city rotation and the extra industrial-plot-4 flip. |
| Were tables physically present in the finished worlds? | Readback of completed worlds’ region files found **27/27 weapon counters** in 11 cities and **26/26 vehicle counters** in all 16 cities. Both city orientations occurred for both shops. Each site had paid tiers 1–5 in its seed report and a tier-5 facility in its restart report. All 27 ordinary weapons tables were also present, on smooth stone. |
| What did the existing native progression check? | After construction, the progression code checks the recorded facility, expected blocks and earned factory level. Its city checks also recheck the weapon or vehicle construction binding, and the vehicle envelope when applicable. These tests do not open a connected player's screen. |
| Does clicking, reviewing, canceling and reopening work on the installed client? | Still needs a connected player in the matching modpack. No client was launched for this audit. |

The 96 mapped locations are alternative placements. The table count in a specific city depends on which factories the nation built. In `locations.csv`, an empty “verified layout” cell means the location was mapped but did not hold that factory in these five completed layouts.

## Player spot-check

1. Choose any available starter on the fresh world. Once you own the relevant completed factory, go to its counter as your nation’s leader in Adventure or Survival.
2. Select an empty main-hand slot, stand within four blocks with a clear view, and right-click the table. In a Weapons Factory, look for the spruce planks directly underneath. In a Vehicle Factory, use its single table.
3. Review an available item, cancel, and check that you return to the same shop and your balance stays the same. Close the screen, then right-click the table again. No purchase is necessary for this menu check.
4. If it fails, record the city, factory, plot if known, F3 targeted block XYZ, held item, and the exact result: a vanilla crafting grid, no visible response, a chat refusal, or a shop that fails on review or cancel. A screenshot of the targeted block and chat is enough.

For example, a **City D, plot 3 Weapons Factory** has its working counter at **695, 82, -731** and its ordinary table at **688, 82, -741**. Use the CSV for any other city or plot.

## Click path for developers

- `WorldProtection.onRightClickBlock` can cancel a click based on the held item. `MapInteractionHandler.onRightClickBlock` listens at LOWEST priority and routes uncanceled server-side main-hand clicks to the vehicle factory, then the weapon shop, then the management lectern fallback. A regular factory table outside the bindings proceeds through normal interaction unless another handler cancels it.
- `ShopSites51` and `VehicleSites52` recognize candidate coordinates, then independently resolve a unique current-map site. `ShopFacility40.open` and `VehicleFactory50.open` check actor, role, local ownership, construction state, reach and line of sight. A recognized factory counter can emit a specific chat refusal.
- The physical shop creates a server-side session and sends rows through `NationNetwork.openDashboard`. `ClientNationDashboardHandler.open` routes either shop marker to `WeaponShopScreen`. Its buttons send the command supplied by the server. Review and cancel recheck the session and local facility. Cancel redraws the menu. Close sends the close command and dismisses the screen.
- A connected-client test should check the displayed shop identity and the return from Cancel. A report that a crafting table exists cannot establish any of those client interactions.

## Reproduce the offline audit

From anywhere, run `python3 check.py --workspace /path/to/test-workspace --repo /path/to/candidate-checkout --output-dir /path/to/results`. Python’s standard library is sufficient. The script refreshes `snapshot.json` and `locations.csv` in the output directory. Its reads are limited to source, completed receipts and saved chunks. No server is started.

External inputs are the candidate checkout with its `build/combined-nov22-*` completed authored worlds, the matching `receipts/combined/source.txt`, `layoutN-staging.json`, `layoutN-seed` and `layoutN-verify` process records and native logs, and `inputs/CatanCraft_Clean_R7_Input/nbt_check.py` from the transferred clean world bundle. The source checkout supplies the map, schematics and read-only region parser. The check requires a clean gameplay source tree at the SHA above; the branch can have any name. Snapshot results include the SHA-256 of every saved seed and restart report used.
