# City D gameplay loop review

On November 22, 2026, three offline playthroughs from Northeast Hearth (City D) completed the same 26 economic actions with no grants. Building the Vehicle Factory and being able to afford its first pickup were separate milestones. A player who deliberately prioritized the pickup acquired it **10 active server hours earlier** than a player who finished the other factories first. Early transport pacing is therefore a useful design decision even before selecting rare vehicles.

## Reproduce the economic playthrough

From the repository root run `python3 tools/gameplay/city_d.py`. It prints each action, elapsed minutes, debit, Agriculture balance and any unfinished actions as JSON. It reads the map, live costs and 15-minute cycle through the [existing economic model](../tools/economy45/simulate.py). Each case starts at TH1 in D with $12,000 and zero materials. The nation buys the starter pistol and ammo, develops D, claims adjacent C → G → F for resources, builds industry, buys an AK and ammo, purchases and refuels a pickup, and budgets one wall side from Basic to Reinforced.

| Economic milestone | Four processor targets raised to 1,000 | Targets managed for next purchase | Managed, pickup prioritized |
| --- | ---: | ---: | ---: |
| Starter Glock 17 and 32 rounds paid for | 0h30 | 0h30 | 0h30 |
| Commercial District built in D | 3h00 | 3h00 | 3h00 |
| Weapons Factory built in D | 15h15 | 15h15 | 15h15 |
| Vehicle Factory built in D | 17h45 | 17h45 | 17h45 |
| AK and one pack of compatible ammo paid for | 43h00 | 43h15 | 44h45 |
| Pickup paid for at D's factory | 44h30 | 44h30 | 34h30 |
| One additional quarter tank paid for | 44h30 | 44h45 | 34h45 |
| Basic and then Reinforced paid for on one side | 46h45 | 47h00 | 47h00 |

Elapsed times assume an active server at 20 ticks per second and purchases immediately after an affordable cycle. They exclude travel, construction delays, interactive menus, combat losses, trade and random income. The factory is local to D and the processing resources are shared nationally. Refueling assumes space for a full quarter tank and zero prepaid credit. In that case the current pickup tariff charges eight Fuel. The model calculates the debit only. Physical vehicle delivery, parking and service require a clear registered area and a real player. No target season duration or price change follows from these timings.

All three routes experienced zero underfed territory-cycles and ended with **668–672 Agriculture**. These routes buy no Textile Mill, so they show how food accumulates along a vehicle-focused path. Other playstyles can consume food faster. The managed pickup-first variant moves the pickup and its first refill ahead of the chemical and electronics factories. Neither factory supplies a pickup component. The player still needs a Machine Shop to turn Steel into Mechanical Parts.

## Fresh-world processor targets across the four starters

The unattended column above uses the older economy simulation's raised stock targets for the Concrete Plant, Steel Mill, Machine Shop and Electronics Factory. A newly purchased processor starts at 100. In eight matched replays from A, D, M and P, taking either adjacent first claim, the unchanged targets produced 104 Concrete and stopped. The next Town Hall upgrade needed 160. All eight still waited for that upgrade at the 200-hour cutoff. With only the Concrete Plant target raised to 200 and the purchase order held fixed, all eight finished the route, and pickup affordability fell between 44h30 and 45h00 of active production time. Players can also obtain resources through trade. These modeled routes exclude trade, rewards, travel and construction delays.

## What an actual player must be able to do

| Player action | Evidence and remaining question |
| --- | --- |
| Found in D and find the correct counter | The authored starting spawn is near A. Choosing D does not teleport the player. The existing D checklist gives directions. In a solo test, see whether the player can find D's management lectern and the specific completed factory counter using in-game information alone. The starter counter checks for the lectern block and entity. It does not require a book item in the lectern. [City D checklist](SERVER_WIDE_053_SOLO_CHECKLIST.md) · [Factory and starter checks](../src/main/java/com/quin/catancraft/world/ShopFacility40.java) |
| Build actual facilities, drive and refuel | In the archived layout-1 native run at `b3e240c`, all 80 city/tier checkpoints passed, all 48 industrial sites advanced through paid levels 1–5, and all 272 tracked histories matched after a separate restart. These automated checks use a simulated player. The physical click, delivery, driving, fuel purchase and installed crowbar behavior still belong to the existing City D test. [Campaign contract](../tools/progression53/README.md) · [Vehicle site qualification](VEHICLE_SITES_052_CHECKPOINT.md) |
| Capture a monument and collect its reward | A nation alone in the active zone can progress toward the 120-second capture. Contested presence pauses it. National supply credits and equipment claims have distinct delivery paths. New equipment rolls currently cover TaCZ guns/ammo. Add a player-facing claim and valid vehicle delivery path before enabling any rare vehicle. Check ordinary access to compatible mounted ammunition if the proposed vehicle needs it. [Capture logic](../src/main/java/com/quin/catancraft/monument/MonumentManager.java) · [Active reward rules](TACZ_ONLY_044_CONTRACT.md) |
| Trade to overcome a shortage | The dashboard supports proposals and incoming/outgoing actions. The command path escrows the offer, checks recipient funds at acceptance, and refunds a canceled offer. Exercise insufficient funds, decline, cancellation and restart with two simulated nation ledgers. The offline route earned its resources without a trading partner. [Trade actions](../src/main/java/com/quin/catancraft/ui/NationDashboard.java) · [Trade handlers](../src/main/java/com/quin/catancraft/command/CatanCommands.java) |
| Defend the city and keep paid progress | Script both outcomes when the production siege controller is connected. Defenders can contest the active objective and win at timeout or attacker surrender. Attackers must take the outer objective and then Town Hall. Check ownership, the single settlement and restoration across restart for both outcomes. Provisional rules and bounded restoration fixtures exist; the end-to-end player battle remains a gameplay gap. [Siege rules](SIEGE_RULES_036_CONTRACT.md) · [Restoration scope](SIEGE_RESTORATION_039_CONTRACT.md) |

## Decisions and next tests

- **Economy and vehicle roster.** Decide when a normally progressing nation should first be able to drive. The measured gap from a built factory to pickup affordability was **16h45–26h45** in these routes. Compare potential shop tiers, purchase materials, refueling and rare-only vehicles against that target. The trip to contested monuments must remain practical before a nation earns its first rare drop.
- **Gameplay clarity.** Fold direction-finding, factory tier/cost visibility, claim collection and a live stockpile read into the existing focused City D pass as those features become available. Capture the first action where a player needs external coordinates, a command explanation or help interpreting a missing resource.
- **Season completion.** Use the current economic playthrough plus separate escrow and monument-claim scenarios. Extend it through attacker victory, defender victory, recovery and a mid-battle restart when those player actions are connected. Keep server-wide qualification across all eligible cities; D supplies one human usability check.

This review covers the City D scenario and inspection of the linked paths. Affordability alone does not establish menu usability, combat balance, drop enjoyment, live timing or multiplayer acceptance.
