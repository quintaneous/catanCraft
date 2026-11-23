# Fresh-world solo gameplay check

Use this checklist when the matching clean kit and gameplay evidence are delivered. Start a new run on the authored Season 1 map with empty player, nation and purchase history. Choose your own starting city and play at your own pace.

## Start a fresh run

1. Extract the delivered kit to a new folder outside your installed Minecraft profiles. On Windows, double-click `NEW_TEST_RUN.bat`. It creates a fresh copy of the map and prints the new `runs/run-<UUID>` folder.
2. In that run folder, read the Minecraft EULA and set `eula=true` in `eula.txt` if you agree. Double-click `START_SERVER.bat` in the same folder. On Linux the corresponding launchers are `NEW_TEST_RUN.sh` and `START_SERVER.sh` with Python 3.11+ and 64-bit Java 17.
3. Use a separate Minecraft 1.20.1 / Forge 47.4.10 client profile with the four supplied `client/mods` JARs. In the server console enter `whitelist add YOUR_USERNAME` and join `127.0.0.1:25566`.
4. Create your nation with `/nation create <your chosen name>`. Open `/nation` and choose any available starter. World spawn is beside City A at (-750, 81, -833). Spawn does not claim a city for you. In this build, follow the roads to the city you selected.

| Slot | Starting city | Direction | City center (X, Z) |
| --- | --- | --- | --- |
| 1 | A · Northwest Hearth | Northwest | -750, -750 |
| 2 | D · Northeast Hearth | Northeast | 750, -750 |
| 3 | M · Southwest Hearth | Southwest | -750, 750 |
| 4 | P · Southeast Hearth | Southeast | 750, 750 |

You can explore all 16 cities. The other 12 enter nation progression through expansion. The built-in Town Hall lectern opens starter stock and city management. Use the marked crafting-table counter in a completed Weapons or Vehicle Factory for that factory's shop. Stand within four blocks with a clear view and reopen an expired menu.

## Things to spot-check as you play

- **Founding and resources.** Does the Town Hall show your nation and the city you chose? On `/nation → Resources`, check the balances and signed forecasts for all 14 resources. If the next 15-minute production cycle posts while the page is open, compare it with the previous forecast, accounting for purchases, trades and rewards.
- **Weapons.** Once you have a Weapons Factory, check whether its physical counter opens the factory menu. Cancel one quote and check your balance, then confirm an affordable purchase. Check one charge, one item and matching ammo that loads and fires. Starter stock is also available at the Town Hall lectern.
- **Walls.** If you want a wall, use City management → Defenses in a city you own. Check its compass side, quoted price and connection to neighboring sectors.
- **Vehicles and fuel.** If you earn a Vehicle Factory, use its counter to buy a vehicle you can afford. Drive it, check ownership, then compare the quoted fuel charge and the actual fuel added.
- **Crowbar.** If you obtain the Superb Warfare crowbar through normal play, try it once on the purchased vehicle. Check whether the vehicle stays in the world and whether a packed item appears. This interaction still needs installed-pack review.
- **Monuments.** If you capture one during play, record which reward you received.
- **Restart.** When you are ready, leave the client, enter `stop` in the server console, then use `START_SERVER` in the **same run folder**. Check that the city assignment, balances, inventory and purchases you actually made persisted and that you were charged only once.

If something looks wrong, record the city, the plot or compass side if relevant, what you clicked, what you expected, and the message or result you saw. A screenshot helps. Skip any feature you haven't earned or don't feel like testing. The automated suite handles repeat checks across cities and layouts.
