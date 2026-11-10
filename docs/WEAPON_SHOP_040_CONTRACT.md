# ECO-03 — physical weapon purchase contract, 0.1.40

This implementation continues `fd8072c350eeb69f00c432a5a3f3bdd6a7479882` on
`chatgpt/combined-shop-acceptance`. It is an automated development checkpoint for
the combined acceptance plan, not a request to install another intermediate JAR.
The complete tier catalog, randomized monument rewards, fixed vehicle bays and
finished player-facing siege flow remain separate unfinished work in that bundle.

## Physical interaction

A connected, alive Survival/Adventure nation leader right-clicks the existing
City A management lectern for starter stock, or the existing crafting-table
counter inside the one completed local Weapons Factory. Other members retain the
management dashboard. The shop includes a City management button; it does not
replace the lectern book or modify buildings. The shared block event respects
prior cancellations and main hand only. Client/server protocol is now 5 because
older clients cannot render the new shop view.

Authorization remains restricted to the existing authorized fresh-demo/historical
R7 world contract and canonical City A. Ownership, unique membership, completed
Town Hall, unique factory/plot, source placement token, native counter/support/
standing-space states, dimension, world identity, construction/recovery holds,
player identity and local factory level are rechecked. Siege/restoration history
currently holds shopping for review; this is conservative and is not completed
post-siege integration. No other city is enabled.

Provisional interaction bounds: eye distance at most four blocks, clear native
collision ray to the counter, session expires at 1,200 server game ticks (one
minute at 20 TPS), maximum 256 active sessions. Reopening, disconnect, player
replacement, server replacement, close or expiry invalidates the session. A quote
is consumed before purchasing. Commands can review an existing physical session;
none creates one or bypasses it. Both procurement aliases share the same routes.
A stale level/owner/counter/world cannot confirm a fresh quote. Paid recovery
retains its historical facility and required level; a higher level is allowed.

## Stock and native item identity

Only the four existing offers are active; prices are unchanged and provisional:

| Offer | Local access | Money | Nation supplies |
|---|---|---:|---|
| Glock 17, unloaded | Starter or factory | 350 | 5 Wood + 5 Stone |
| 9mm, 32 rounds | Starter or factory | 100 | 5 Stone |
| AK-47, unloaded | Weapons Factory L1+ | 1,200 | 12 Steel + 10 Wood |
| 7.62x39, 32 rounds | Weapons Factory L1+ | 175 | 2 Steel + 1 Explosives |

The 113-entry reference allocation is not activated. TaCZ must recognize the gun
or ammo index, native builder, exact item ID, count, firing mode and empty magazine/
chamber. Full native item NBT is retained. Missing/incompatible APIs or pack entries
refuse before a new charge; no fallback or force-built item is issued. API signatures
were checked against official TaCZ source `42af1be5bbe4127c083cfa19b57482f8aae23df2`
(`GunItemBuilder`, `AmmoItemBuilder`, `TimelessAPI`, `IGun`). This is source checking,
not a native TaCZ runtime or actual installed-pack compatibility pass.

Existing vehicle procurement remains its legacy command/deployment flow; this
checkpoint does not represent fixed-bay vehicle delivery. Existing SEC-01 vehicle
provenance/protection code is retained, with its published-runtime tests rerun.
SEC-01 remains **UNRESOLVED / HIGH PRIORITY** pending actual-runtime acceptance.

## Payment, delivery and recovery

One sealed immutable intent binds actor, original nation/world, facility, catalog,
price, exact item and selected empty inventory slot. Nation debit and its receipt
share native CatanSavedData. A delivery receipt lives under Forge's death-preserved
PlayerPersisted data and is saved in the same native player file as inventory.
It is not an item tag, so ammunition can retain normal native stacking behavior.

The progress companion is absent/READY before staging, durably DELIVERING before
an item can be inserted, COMMITTED after verified delivery, or HELD when ambiguous.
Native save methods can suppress errors: both ledger and player saves are read
back and compared with frozen expected values and current memory. Return from a
save callback alone is not success. A global shop lock blocks callback reentrancy.

A clean failed staging can return to READY only when the unchanged exact staged
stack can be removed and both disk and live player state match the frozen original
inventory/receipt preimage. A saved delivery is never overwritten by rollback.
Altered or unprovable delivery remains held, without automatic redelivery/refund.
A pending order blocks a new order. One empty fixed inventory slot is required;
no partial insertion, ground drop, inventory clearing or automatic slot substitution.

After a verified delivery, moving, using or losing the item, or death-cloning the
player does not erase its receipt. Recovery completes from original intent and
proof without charging/granting again. Completed repeats do not save or mutate.
Historical completed orders remain readable after a legitimate nation change;
a pending order cannot transfer its charge to a new nation. History is bounded
at 4,096 operations/receipts per actor and fails closed rather than pruning.

Limits: native-file readback and sealed atomic journals establish the tested
save/restart behavior, not a universal hardware power-loss guarantee. Ambiguous
states may need administrative investigation while preserving all records. The
server runs these operations synchronously; there is no new latency guarantee.

## Validation and owner acceptance

Controlled tests use vanilla items and detached FakePlayers saved through the
server's actual native PlayerDataStorage. Forge exposes an unmodifiable roster;
a tightly guarded fixture helper accesses the unique storage field by type and
verifies its directory before calling the native save method. It never adds a
synthetic actor to the connected/ticking roster or manufactures a native file. They do not establish an authenticated counter click,
client screen rendering, TaCZ compatibility, actual-pack weapon balance or owner
acceptance. Separate-process tests preserve original player files, native ledger,
intent/progress bytes and source-bound proofs between JVMs without copying or
reconstructing them. The resulting checkpoint records actual counts and hashes.

No live world, installed mods, accepted walls or construction assets are changed.
No reset, imports, repeated accepted owner purchases, multiplayer or Blender task.
Keep the one combined solo checklist in COMBINED_SHOP_ACCEPTANCE_PLAN.md pending
until the linked features and their actual runtime integration are ready.
