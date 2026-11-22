# 0.1.53 focused solo checklist — DRAFT, not yet cleared for testing

This is the planned single owner pass after the matching JAR and complete native
validation are delivered. A draft checklist or assembled kit is not a release.
Current native validation and installed-pack owner acceptance remain pending.
SEC-01 crowbar remains **UNRESOLVED / HIGH PRIORITY**.

## Setup once

Use either the newly delivered isolated test kit, or your existing isolated test
server after stopping/backing it up and replacing only its CatanCraft JAR with the
exact delivered `catancraft-0.1.53-server-wide.jar`. Preserve its other mods,
configuration, world and history. Do not touch the live server or installed mods.

For a new kit, extract into a new directory and run `NEW_TEST_RUN.bat`. It prints a
new `runs/run-<UUID>` folder. Read the Minecraft EULA and personally accept it in
that run's `eula.txt` only if you agree. Use `START_SERVER.bat` in that run. On Linux,
use the corresponding `.sh` files with Python 3.11+ and 64-bit Java 17. Never run the
baseline directly or reuse a played run as a new baseline.

Use a separate Minecraft 1.20.1 / Forge 47.4.10 client with the supplied four
`client/mods` JARs. The resource forecast uses network protocol 6. Use the
same CatanCraft candidate JAR on the client and the isolated server. Add your authenticated username with `whitelist add YOUR_NAME`
in the server console and join `127.0.0.1:25566`.

In a fresh run, use `/nation create <your chosen name>`, then `/nation`; choose
**Northeast Hearth, slot 2 (City D)**. Assignment does not teleport you. Spawn is
near City A (-750,81,-833); take the roads east to D, center (750,81,-750).

The “management book” means the built-in Town Hall lectern, not an inventory item.
In D it is at approximately **(750,84,-773)**. It opens starter stock and a City
management button. Use the authored factory crafting-table counter, not a newly
placed crafting table. Stand within four blocks with a clear view. Menus expire
after about a minute; reopen them if needed.

D weapons counters by factory plot: 1 (695,82,-771), 3 (695,82,-731),
4 (805,82,-737). Vehicle counters: 1 (694,82,-780), 3 (694,82,-740),
4 (806,82,-728). Only the counter matching the completed factory type applies.
Starter stock includes the TaCZ Glock 17 and 9mm; broader stock needs a completed
weapons factory and its earned tier. Do not grind the full ladder for this pass.

## Seven checks

1. **D startup and management.** Let normal startup finish. Check the base city is
   visible, the lectern opens, and the city/nation shown is yours in D. Open
   **/nation → Resources**. Check that each of the 14 balances has a signed
   net forecast or a zero for the next 15-minute production cycle. If a cycle
   posts while you have the page open, compare the balance change against the
   prior forecast, allowing for any intervening purchases, trades or rewards.
   Check that balances and forecasts refresh while you stay on the page.
2. **One TaCZ gun and its ammo.** If you own a completed Weapons Factory, right-click its marked crafting-table counter in D and confirm the factory menu opens. Record the plot and any refusal. Use an affordable available shop entry. Open its
   quote and cancel once: no charge/item. Reopen and confirm once: one charge and
   one gun. Buy matching ammo once and check it loads/fires. Record price and item.
3. **One needed D wall purchase.** Open City management → DEFENSES. Inspect the
   sector/tier quote; buy only one genuinely needed, previously unaccepted upgrade
   if affordable. Check its wall and connection to adjacent sectors. Preserve the
   accepted City A walls (North Reinforced; East/South/West Basic); do not rebuy them.
4. **One vehicle.** At the crafting-table counter in your completed D Vehicle Factory, open the local vehicle menu, then review and confirm one
   affordable vehicle purchase. Enter it, drive a short safe distance, and exit.
   Record any placement, ownership or interaction problem.
5. **Economy fuel.** With that vehicle below full fuel, review **Add 25% Fuel**.
   Check the quoted economy cost, confirm once, and compare charge/fuel before and
   after. Note any refused action verbatim; do not substitute item conversions.
6. **Crowbar once, if available.** Try the SBW crowbar on that purchased vehicle
   once. It must not pack/duplicate the vehicle or bypass ownership/economy. If you
   have no crowbar through normal play, mark this BLOCKED and continue. SEC-01 stays
   open until the installed-pack behavior is reviewed; do not grant yourself tools.
7. **One ordinary restart.** Exit the client, use console `stop`, and restart with
   START in the **same run folder**. Rejoin and check D assignment, purchased wall,
   gun/ammo, vehicle, fuel and money persist without duplication or repeated charge.

If an earned building/resource/vehicle is unavailable, mark that step BLOCKED,
record what is missing and continue. No creative, debug grants, imports, resets,
multiplayer tests, or repeated accepted purchase tests. If you naturally earn a
monument claim, optionally record its random reward once; do not farm it.

Return seven PASS/FAIL/BLOCKED results with exact refusal text and relevant
screenshots/logs. Visual and installed-pack observations are distinct from the
developer's all-city native regression evidence.
