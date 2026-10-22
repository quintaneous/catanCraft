> Runtime gate: native delayed checks found grass support turning into dirt beneath

Historical checkpoint. Current evidence: [DEF-03 validation](DEF03_VALIDATION_20261019.md).
Use the single [City A solo checklist](CITY_A_FRESH_SOLO_CHECKLIST.md) for the eventual matching package.
> north P2. Production placement is explicitly disabled pending a safe natural-support
> contract and regression tests, independently of the authored-world gate below. DEF-04 prices are now approved provisionally.

# City A NORTH P2: focused isolated fresh-world checklist

**Readiness gate:** north P2 appearance and bounded footprint are approved for isolated fresh-world testing. PROVISIONAL PLAYTEST PRICING is Basic $1,500 + 15 Wood + 30 Stone, then an additional $4,000 + 50 Concrete + 15 Steel for Reinforced, from the owning nation stockpile. The clean authored-world/bootstrap package has not been transferred and verified, and the natural-support runtime issue above remains unresolved. Therefore the current ordinary purchase path must refuse a quote without placement or charge. Do not substitute native fixture prices, sum old module prices, edit the catalog locally, copy old test authorization or claim a completed playable package.

This is a prospective single-player/isolated development checklist after the world prerequisites and runtime blocker are resolved. It is not instructions for the owner's developed City A, installed mods or live server. It does not repeat accepted producer/house/Town Hall purchases or require multiplayer. P3 East/South/West artwork and seam transitions remain pending; no other side or city is approved by this north test.

## Before starting

- Record the exact runtime commit, JAR SHA-256, north input ZIP SHA-256 and packaged config/bootstrap identity. The current P2 input ZIP pin is `039ff60335cf3d80f537adb689f11a833e3a15b7a2fcf54aeb6490bb2ae0b996`; its registration covers City A NORTH only, at anchor `(-750,81,-838)`. Reconcile any future revision instead of treating these values as interchangeable.
- Use a new isolated package with its actual authored world, matching JAR/assets/config and generated bound fresh authorization. Follow the normal EULA and prerequisite checks in [INT02_DEMO_PACKAGE.md](INT02_DEMO_PACKAGE.md). Do not rename another save, delete journals or use a GameTest fixture as the playable authored world.
- Found/claim City A through normal progression; perform purchases as its owning nation leader. Record initial nation money/materials, north level, ownership and surrounding development. Keep East and West absent, including no unexplained receipts or pending transactions; overlapping old socket defenses must refuse rather than be adopted or overwritten.
- Keep normal no-grant progression distinct from an explicitly funded showroom session. If funding is chosen, record the existing operator grants separately; grants must not buy any tiers automatically. Use the provisional structural schedule above; functional gates, weapons, ammunition and resupply are excluded.

## One focused sequence

1. **Check gates and identity.** Run:

   ```text
   /nation defense list a
   /nation defense status a north
   /nation defense quote a north
   ```

   NORTH means geographic negative Z, not the direction a player faces. At current readiness, the quote must refuse the unresolved runtime/world prerequisite and perform zero writes/debits. **Stop purchase validation here until the runtime blocker is fixed and the real isolated package is available.** Record that as blocked, not passed purchase acceptance. Listing/status can report a saved L0 without resolving an unavailable next-tier price.

2. **Buy basic once, after the gates are resolved.** Review the displayed complete-side price and target revision; confirm within 1,200 server ticks:

   ```text
   /nation defense buy a north confirm
   /nation defense status a north
   ```

   Expect the complete basic side, north L1, and exactly the displayed nation treasury/material debit. The current immutable fresh→basic plan has **5,013 sparse changes**; this is contract evidence, not permission to paste an envelope or clear a plot. No independent module purchases, player-inventory resource debit, terrain reset or edits to nearby buildings should occur. Repeat `buy` **without requesting another quote**: it must verify the current completed operation with no new writes or charge. A new quote at L1 describes the upgrade, not a duplicate basic purchase.

3. **Inspect the actual north side.** Walk the approach and stairs/deck; inspect lighting, continuous support and road/headroom clearance against the approved P2 revision. Keep the frozen NW and NE caps closed and owned by NORTH. No cap opening, missing edge guard or unsupported fall gap is allowed while adjacent East/West sectors are absent. Do not transplant the old P1 entrance-gap assumption or widen the reservation beyond the exact P2 contract. Observe untouched house/civic/industrial/Town Hall surroundings and preserved inventories/books. Vehicle drive-through or weapon-model clearance remains a separate actual-runtime gate; a visual walk is not certification of it.

4. **Check a real hazard and a harmless nearby item.** Before a new operation, record a dropped item's full stack and location in unaffected space. It must remain intact. A genuine entity intersection/support hazard in a changed location must refuse before fresh payment or changes; use an intentional isolated test fixture, without moving/deleting existing entities or breaking source blocks to manufacture a pass. If no safe manual fixture is available, retain the native evidence and mark this manual observation not executed. Loaded-entity/unknown-source/legacy-overlap refusal is expected safety behavior; clear only its actual prerequisite, never broadly empty the area or force a replacement.

5. **Upgrade basic→reinforced once.** Obtain a new full-side quote, record the approved upgrade cost, then use the same purchase command:

   ```text
   /nation defense quote a north
   /nation defense buy a north confirm
   /nation defense status a north
   ```

   There is no separate `upgrade` command. Expect north L2 and exactly one displayed upgrade debit; the current exact basic→reinforced plan has **4,539 changed cells**. Recheck roads, frozen caps, supports, deck and surrounding inventories/development. The basic source must be recognized exactly; no air-inclusive overwrite or arbitrary old-wall conversion is allowed. Repeat `buy` without a new quote: no additional charge or writes. East/South/West must remain unavailable; no legacy socket `buy` path may bypass sector purchases.

6. **Roll back that upgrade and retain basic.** Record the reinforced state and both historical charges, then run:

   ```text
   /nation defense rollback a north confirm
   /nation defense status a north
   ```

   Expect exact restoration of the purchased L1 basic source, its prior receipt/revision and the **historical upgrade cost only**. The basic purchase remains paid; inventories/books and neighboring states remain current and unchanged. No refund may leave usable unowned reinforced pieces. Repeat rollback once: the retained rolled-back operation must give no additional writes/refund. Do not delete that history or intentionally roll back unrelated accepted work.

7. **One ordinary restart.** Stop normally and reopen this same isolated package at the same path. Check north L1, basic geometry, original basic payment, one upgrade refund, ownership, cap/road safety and preserved item/inventory state. No automatic new purchase or repeated refund should occur. This is the single persistence observation for this sequence, not a claim of arbitrary-crash certification or a request to replay old accepted migrations.

## If interrupted or refused

Preserve logs, world and journals. A partial physical sector is **pending**, not a completed new level. Inspect status before selecting one recovery action:

```text
/nation defense status a north
/nation defense resume a north confirm
```

Or explicitly choose reverse recovery with `/nation defense rollback a north confirm`. Do not run both as a script, repeat `buy` to recover, change historical prices, remove journal files or refund by hand. Resume must use the recorded complete plan and original accounting; rollback must restore the entire recorded source before any refund. Conflicts, changed ownership, neighbor state or obstructing entities must stop recovery for review without destructive cleanup. Deliberate fault injection belongs to the isolated native test suite, not the owner's checklist.

## Record the outcome

Record exact commands/messages, displayed approved prices and before/after balances; source/JAR/input pins; basic/upgrade/rollback levels and historical receipt evidence; one restart result; road/cap/support/item/inventory observations; screenshots; and preserved log paths. Separate **native evidence**, **manual observations**, **blocked**, and **not executed**. Do not mark P3 sides, full modpack procurement, gun apertures, functional gates or siege behavior passed by this sequence. Purchased-vehicle retrieval remains **OPEN / HIGH PRIORITY** until its remaining evidence is complete.

For physical gun/ammunition counters and fixed vehicle bays, follow the requirements audit in [ECO03_PHYSICAL_PROCUREMENT_REQUIREMENTS.md](ECO03_PHYSICAL_PROCUREMENT_REQUIREMENTS.md); this north test authorizes no storefront redesign, new economy schedule or NPC work.
