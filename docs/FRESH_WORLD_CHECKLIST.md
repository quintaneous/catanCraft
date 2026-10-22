# One fresh-world acceptance checklist

**Blocked / not yet executed.** The current `0.1.29-sector-dev` artifact is an
engineering checkpoint. No clean authored world, approved wall side or matching
launcher/modpack has been supplied. Do not install it over developed City A or
interpret native fixture passes as visual or modpack acceptance.

When a matching complete demo package is available, use one new disposable
singleplayer save in its own launcher profile:

1. Verify package hashes, Java/Forge/Minecraft versions and the supplied world
   identity. Confirm this is the clean authored demo, with no old nation ledger,
   purchases, player inventory or recovery journals. Keep City A untouched.
2. Create a nation, select an offered start, and inspect ordinary starter
   buildings and producers. No legacy repair command should be required. Keep
   management books and inventories populated through the following steps.
3. In this demo only, grant convenient test funds using the existing operator
   commands: `/catan debug money 1000000` and `/catan debug give <resource> 10000`
   for `wood`, `stone`, `steel`, `concrete` as needed. Record starting balances.
   Funding is a one-time test aid, not altered production economics.
4. Buy normal buildings and progress producers through normal upgrades. Check
   costs, paid levels, ownership, books and inventories. Save, close and reopen
   the demo; verify those values and structures persist.
5. For each approved compass side, check its displayed direction against F3,
   its one total price and level using `/nation defense quote <city> <north|east|south|west>` and `/nation defense status <city> <side>`. Use `/nation defense buy <city> <side> confirm` for the quoted next level:
   one charge per quoted operation, correct corners and neighboring sides,
   unobstructed roads, usable stairs/circulation/firing space. A repeated request
   must not charge or build twice. Current engineering builds refuse these
   purchases until approved production contracts, prices and reviewed art are registered. The service adapter is implemented; P1 remains a native-test-only prototype.
6. Check insufficient money and materials separately; preserve nearby harmless
   items/entities and refuse actual collision/support/attachment hazards. Use
   automated fault-injection evidence for interruption paths; do not force-kill
   the owner's save as a manual test. Reopen the disposable demo to verify its
   completed purchases and normal restart behavior.
7. Only with the verified intended SBW artifact/profile, test actual
   player-operated weapon fit, ammunition and damage in the disposable demo.
   Record crowbar/packing behavior; the exploit remains UNRESOLVED / HIGH PRIORITY
   unless genuinely reproduced and verified fixed. No NPCs, siege or multiplayer.

Record actual passes, failures and screenshots against the package/source hash.
Unexecuted items remain pending; no checklist item grants live-world deployment.
