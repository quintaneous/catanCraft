# Next combined in-game acceptance — owner direction, 2026-11-10

The owner requests bundling the next in-game test with the new weapon shop and crowbar protection. Adopt **one combined playable build and one focused solo checklist**, after separate automated component validation. Do not ask the owner to install each backend checkpoint. Latest validated runtime remains exact 0.1.39 source `b652bb5ab468d1c45c066fd19175a11ecae93bd5`, documented at `c570d02587fe8fb42f54b5cd47080b4bbf1f3218`; this preparation does not produce another JAR or enable a shop.

## Why the bundle makes sense

It reduces installations and repeats, and exercises the systems together in the actual pack. The components are not completely independent: shop payment/delivery, vehicle ownership/provenance, crowbar retrieval, reward vehicles and later siege ownership/restart behavior share persistent state. Separate component commits and evidence make faults easier to isolate; the final acceptance build must contain their reviewed combination. Do not use an unrelated passing test to close another feature's unresolved acceptance.

## Work carried forward

| Area | Required before the owner test | Evidence/next work |
|---|---|---|
| Weapon shop | Physical counter interaction and GUI; shared server-side presence/quote checks across all purchase aliases; real completed factory/plot/tier; exact item/ammo identity; charge/delivery recovery | Existing art and counter candidates surveyed in [ECO03_SHOP_FACILITY_SURVEY_040.md](ECO03_SHOP_FACILITY_SURVEY_040.md). Implement recoverable purchase boundaries before routing the GUI to purchases. |
| Tier catalog and prices | Reconcile effective test-pack guns/overrides with the113-entry reference; cumulative tier variety; DROP/HOLD excluded from shops; provisional prices tested before rollout | Preserve [the reference proposal](ECO03_WEAPON_TIERS_AND_MONUMENT_LOOT_PLAN.md), historical prices/receipts and starter access. No final balance or missing runtime identity inferred. |
| Monument supply drops | Same later shop/reward update: weighted useful rewards, immutable saved roll and exactly-once claims; rare gun bundles and safe vehicle entitlements where approved | This remains the owner's requested linked update. Do not silently omit it or treat rare-item grinding in one solo session as a probability test; validate distributions automatically and check the actual claim flow once. |
| SEC-01 crowbar | Existing protection retained; every new vehicle path tags before spawn and binds exact UUID/type/nation to its receipt; actual runtime matched and relevant retrieval routes checked | [Shared dependency audit](SEC01_COMBINED_SHOP_ACCEPTANCE_040.md). Reuse an already purchased vehicle for owner checks. Existing035 tests are retained evidence, not new installed-pack acceptance. |
| Siege/gates | Continue bounded development; include a player-facing test only once its actual interaction, geometry, damage/restoration and outcome flow is validated | Keep current039 backend tests automated. This scheduling decision does not make unregistered primitives playable, approve unsurveyed damage regions, or waive defender-win behavior. |
| Combined restart | Same delivered purchases/rewards, ownership/protection and completed state survive a normal save/stop/restart without replay | Focus the checklist on the shared persistence boundary; no forced crash or repeated accepted city purchases. |

The next concrete shop implementation sequence is facility/session binding, durable quote/payment/item delivery, the physical counter GUI and shared command gate, then exact installed-catalog tier/price integration. Fixed vehicle bays require their own initialized-model/exit-clearance contract; a nearby-player spawn is not an acceptable substitute. The minimal existing crafting-table counter and preserved management terminal offer starting points without new art, subject to native interaction/route verification.

## One focused owner checklist, issued only with the playable build

1. Open the real weapon counter without typing a purchase command. Check a locked factory tier and buy one intended unlocked gun/ammo offer: correct displayed cost, exact item and one charge. Reserved top guns must not be shop purchases.
2. Claim one monument supply reward through the delivered interface. Confirm the shown contents and that reopening cannot grant them twice. Rarity probabilities are checked through developer simulations, not by asking the owner to repeatedly capture monuments until a rare result occurs.
3. Use an existing purchased vehicle to check main-hand and offhand sneaking crowbar interaction: the vehicle stays, with no packed item in inventory or nearby. Check actual behavior rather than only a denial message.
4. At the next ordinary restart, confirm the same purchased/reward state and vehicle persist with no extra charges or duplicate deliveries. Repeat only the narrow crowbar interaction needed to establish persistent protection.
5. Add a short player-facing siege/gate check only for the finished flow included in that build, with a clear defender-win path. Do not ask the owner to test backend-only internals or invent multiplayer coverage.

The exact checklist will be rewritten around the final delivered features. Installation remains held until that concrete combined build is ready. Do not impose current development tasks on the owner, reset/copy the developed world, touch installed mods, import schematics manually, repeat accepted walls/producer/civic purchases, or request multiplayer. No Blender assignment is needed from the current survey. **SEC-01 remains UNRESOLVED / HIGH PRIORITY until scoped actual-runtime acceptance.**

## This preparation's evidence

Fresh local/remote heads matched 0.1.39 before work. Existing factory art was decoded read-only from the pinned construction bundle; counter geometry and rotation mappings were inspected. The retained published SBW test-runtime verifier passed read-only for nine artifacts and its expected manifest. No new build, native GameTest, connected-client action, live-world change, shop implementation or newly written crowbar fix is claimed by this planning/survey commit. The current runtime keeps its existing command purchases and fixed monument rewards until their replacements are implemented and validated together.
