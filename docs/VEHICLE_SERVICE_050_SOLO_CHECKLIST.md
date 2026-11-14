# CatanCraft 0.1.50 — one solo check

> **Paused for City D:** this checklist incorrectly implied local shop/vehicle
> access throughout the map. 050 binds those interfaces and wall purchases to
> City A. Do not reinstall, relocate, rebuild or reset to complete these steps.
> They are retained as historical test instructions; wait for the combined
> server-wide fix and its focused replacement checklist. See
> [the all-city requirement and baseline](SERVER_WIDE_RELEASE_REQUIREMENTS.md).

This checks the combined TaCZ shop, purchased-vehicle crowbar protection and national-Fuel refueling update. Automated checks do not replace this first installed-pack check.

## Install once

Stop Minecraft and the server normally and back up your existing world. Replace only the previous CatanCraft JAR with `catancraft-0.1.50-vehicle-service.jar` in the server and client mod folders you use. Keep the world, configuration and other mods. Do not leave two CatanCraft JARs installed. Start normally; do not reset or import a world.

Use your nation leader in Survival or Adventure, with one empty inventory slot and enough nation supplies for the prices shown. No purchase commands are needed. If a required building or resource is missing, report that step as blocked instead of rebuilding the accepted city just for this check.

## In game

1. **Load your existing city.** Confirm it loads normally and your nation, inventory and existing buildings are present. You do not need to buy or upgrade the accepted walls again.
2. **Buy one TaCZ gun and matching ammunition.** Right-click the Weapons Factory's crafting-table counter, or the Town Hall management lectern for starter stock. Review a gun available at your current factory level, cancel once, then review and confirm it. Cancellation should spend nothing; confirmation should charge once and deliver once. Buy its listed ammunition and check that it loads and fires. Current handheld offers should be TaCZ only; higher tiers stay locked until earned. You do not need to test every gun or factory tier.
3. **Buy one vehicle.** Right-click the crafting-table counter inside your completed original Vehicle Factory. Review and confirm a pickup, or a LAV-150 if affordable. One vehicle should arrive facing along the road immediately outside, with 25% fuel. Board, drive straight clear of the factory and stop; exit without getting trapped or pushed into the vehicle. New deliveries are supported at the original factory location; factories in the other supported City A slots offer refueling only.
4. **Refuel using nation Fuel.** Return to its delivery position on the road outside the original factory, facing along the road; keep the whole vehicle in that area. The factory service lane is also supported. Stop and get out. At the same counter, select that vehicle and review **Add 25%**. Check the displayed charge, confirm once, and reopen the counter: fuel should rise by 25 percentage points, capped at full, and national Fuel should fall by the quoted amount once. Then review **Fill tank**, confirm, and check full. Any prepaid fraction shown stays saved for this vehicle; no extra top-off test is needed. Full empty-to-full tariffs are provisionally **32 Fuel for a pickup / 80 for a LAV-150**.
5. **Try the crowbar once on that purchased vehicle.** Sneak + right-click it with the SBW crowbar once. It should refuse to pack the vehicle, leave it present, and give no duplicate vehicle item. If you do not already have the tool, report this check as blocked. SEC-01 stays unresolved/high priority until the installed-pack result is confirmed.
6. **Restart normally once.** Stop and restart, then check that the same purchased vehicle, its fuel and your nation balances remain. Reopen the shop/factory menus: no extra item, vehicle, charge or fuel should appear. An unfinished-operation message should be reported exactly; do not retry purchases to work around it.

If a monument reward becomes available during ordinary play, also open **Monument supply rewards** at the weapon shop or management counter and confirm one claim delivers once. Do not wait for events or grind rare drops for this checklist. Rewards vary by weighted pool; prices and rarity still need playtesting.

Report **pass / fail / blocked** for each numbered step. For any failure, include the exact message, a screenshot if useful, and the server/client log. Stop that action after a failure; no deliberate crashes, repeated accepted purchases, resets, imports or multiplayer checks are needed.
