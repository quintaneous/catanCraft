# City A — one fresh-world solo checklist

**Use only the new isolated clean-R7 package after its runtime technical gates
pass.** The complete authored world is now supplied and deep-verified. Never
extract this package over developed City A or an installed-mod folder. Preserve
folder `CatanCraft_Season1_R7_Development` and its historical display name.
Artwork approval for all four sides is complete; it does not certify runtime or
SBW behavior. Check the package's readiness report before starting.

Launch with START_DEMO.sh (Linux) or START_DEMO.bat (Windows), using Java 17 and
Python 3.11+. Forge 47.4.10 libraries are supplied in this test package. Startup
requires Minecraft EULA acceptance. The server is localhost-only on port 25566; use
a separate Forge 1.20.1/47.4.10 client profile with the matching JAR. Do not alter
your normal mods. For the one solo tester, the server console commands are
`whitelist add <your-name>` and `op <your-name>`. Stop normally with `stop`.

**Normal fresh start:** `/nation create <name>`, then `/nation start 1` for City A.
Use `/catan r5 public apply a confirm` for the existing guarded, free public
bootstrap before sector purchases. Its 28 relevant paving changes match P3's
exact source; do not paste Blender's preview scene. Optional existing house
visual migration is `/catan r5 houses apply a confirm`; it is free and not a
prerequisite for perimeter placement. Mark its result separately.

**Two distinct test modes:** the supplied inspection world uses creative/peaceful,
frozen weather/daylight/mobs and randomTickSpeed=0. First record ordinary nation
balances and normal building/producer progression without any grants. Do not call
that survival-economy calibration. Use an explicitly labeled showroom session
only after recording the no-grant observations; record each grant below. No
funding datapack, free equipment or automatic purchases is loaded by this package.

Record the JAR SHA-256, source commit and package identity. Use the isolated
launcher/configuration. Found City A normally and make one normal house and
producer upgrade, recording the displayed payment and preserved inventory/book.
Keep ordinary progression separate from a funded showroom session: if operator
funding is used, record each `/catan debug money <amount>` and
`/catan debug give <resource> <amount>` grant. Grants must not buy any levels.
For a separately recorded showroom session, $100,000 plus 1,000 units each of
Wood, Stone, Concrete and Steel is sufficient for this structural checklist.
These grants go to the nation; they do not supply weapons or player items.

For each purchase, use the same command pair, replacing `north` with the actual
compass side (`north=-Z`, `east=+X`, `south=+Z`, `west=-X`):

```
/nation defense quote a north
/nation defense buy a north confirm
/nation defense status a north
```

Wait for the completion message and saved target level before the next side.
Placement proceeds across server ticks; visible partial walls are still pending.
Quotes/preparation can pause briefly (up to 7.2 seconds in the native R7 run),
and a side can take over a minute at normal tick rate. Do not repeat a purchase
to speed it up. A native pass is not a measured interactive client frame rate.

1. **North Basic, then East Basic.** Each quote must show $1,500 + 15 Wood +
   30 Stone from the owning nation's stockpile. North's NW/NE caps stay closed
   when alone. Buying East opens NE across both owned terminals in the same
   purchase. NW remains closed. Repeat North `buy` without a new quote: no
   additional payment or placement, even though East changed the connection.
2. **Upgrade North with East connected.** Quote and buy North again. The
   incremental charge is $4,000 + 50 Concrete + 15 Steel; Basic is not charged
   again. NE remains open, NW remains closed. Inspect the unchanged entrance,
   three original upper pier caps, stairs, deck and road clearance.
3. **South Basic, then West Basic.** SE opens when South joins East. West joins
   both North and South, opening NW/SW. All four corners should have continuous
   supported three-wide passage despite mixed tiers. Walk every stair and seam.
   Preserve the entire rear resource corridor X=-770 through -730, inclusive;
   this opening is intentional, not a missing functional gate.
4. **Upgrade South, East and West.** Each costs the same incremental reinforced
   price. Connections already open must stay open. No duplicate corner payment,
   extra module purchase, road clearing or weapon/ammunition spawn is allowed.
   Four Basic plus four Reinforced purchases total **$22,000 + 60 Wood +
   120 Stone + 200 Concrete + 60 Steel**, before unrelated purchases/grants.
5. **Check safety and one reverse operation.** Confirm nearby dropped items,
   building inventories and management-book NBT remain intact. A refused fresh
   operation must charge nothing. Roll back only the most recent West upgrade
   with `/nation defense rollback a west confirm`: West returns to Basic,
   both connections remain open, and only its recorded upgrade cost is refunded.
   Repeat that rollback once: no second refund. Do not delete journals or force
   an older operation over a newer neighboring purchase.
6. **One normal restart.** Stop and reopen the same isolated package at the same
   path. Check North/East/South Reinforced, West Basic, all four connections,
   original ownership/payment, the single refund, items and inventories. Repeat
   status and a no-new-quote duplicate: no automatic purchase or extra debit.

If interrupted, preserve the log and journals. A partially installed side is
pending, not a completed level. Inspect `/nation defense status a <side>`, then
choose `/nation defense resume a <side> confirm` or deliberate journaled rollback.
Do not refund manually, erase partial blocks, remove histories or switch price
catalogs to force recovery. Deliberate crash injection belongs to native tests.

Record exact messages/balances, before/after levels, screenshots, restart result
and log path. Mark native evidence, manual observations, blocked and not executed
separately. This checklist does not certify vehicle turning, gun/muzzle clearance,
blast resistance or a sealed perimeter. Guns, ammunition, gates, NPCs and siege
are outside this structural purchase test. SEC-01 crowbar/retrieval protection
remains **UNRESOLVED / HIGH PRIORITY**.
