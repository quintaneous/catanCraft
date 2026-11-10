# SEC-01 / ECO-03 — combined acceptance dependencies, 2026-11-10

The owner wants the next in-game acceptance session bundled with the new weapon shop and crowbar protection. This is a delivery/checklist decision, not evidence that either feature has passed in the owner's modpack. Validate each component independently first, then one combined build with one focused solo checklist. Preserve existing purchases, City A walls and prior acceptance; no live installation, reset, manual imports, repeated wall purchases or multiplayer work is requested.

This read-only audit inspected the current 0.1.39 development source (`c570d02587fe8fb42f54b5cd47080b4bbf1f3218`). No protection implementation was changed, and no new native test or build was run for this note. **SEC-01 remains UNRESOLVED / HIGH PRIORITY.**

## Existing guard and evidence to retain

`NationVehicleProtection.purchasedRoot` resolves a tagged entity or a Forge `PartEntity` parent chain of at most16 entries. `denyRetrieval` protects that root while the player sneaks. The Forge entity-interact and specific-interact handlers inspect both hands for the crowbar tag. Direct optional mixins intercept the SBW base retrieval method, AutoAimable/Mortar/Annihilator overrides, and the Kotlin crowbar helper before item grant/removal. Non-sneak artillery behavior and ordinary unpurchased retrieval are intentionally retained.

The existing seven published-SBW tests cover direct/helper retrieval, override/artillery behavior, both-hand Forge fallback, normal unpurchased retrieval and entity serialization. The 0.1.35 real procurement test additionally bought the actual pickup and LAV through the command, checked exact charges and ownership, and loaded the same entity UUIDs in a distinct JVM before repeating guarded retrieval. [The validated checkpoint](SECURITY_GATE_035_CHECKPOINT.md) records the executed evidence. Do not describe the already tested guard as a newly written 0.1.40 fix.

The current optional test runtime exists at `build/sec01-035-runtime/`. Its read-only verifier was run for this audit with exit0 and result PASS: nine runtime artifacts; manifest SHA-256 `6e4424ef5a564609dc893c5d9d1ff3efcc7b897d8b9ae9a9acf96d7c331c9452`. This validates the retained published fixture files, not Minecraft behavior or the owner's installed mods. Unrelated negative-verifier directories are intentionally corrupted evidence and must not be reused as a runtime.

| Pinned published input | SHA-256 |
|---|---|
| SBW0.8.9.2 Forge1.20.1, `65d985a64-all.jar` | `559930003f638080de648a8621821d785158768c3f69a762094ef4c8d3c4cc12` |
| Matching SBW sources | `06b4bc749e540879265e3f3829c6c242da81512b19a80de0fbd9b089847893d5` |
| KotlinForForge4.11.0 | `ef988f86d170af499d147ea8a5c34c99dbd6d8f4a5ad236a4b43818dc4470a10` |

Embedded dependencies, exact staged files and verification rules remain in `tools/sec01_035/prepare_runtime.py`; no new release dependency is introduced. The actual installed SBW binary/hash/configuration and applicable part/custom-hit behavior remain unverified. A published version string alone does not establish byte identity. Both mixins use optional targets and `require=0`; changed SBW signatures/routes can therefore leave a loadable mod with no matching interception. Preserve optional-mod loading, but verify the actual compatibility contract before calling secured procurement accepted.

## Concrete shared integration gaps

| Current behavior | Consequence for the combined update |
|---|---|
| Both normal procurement aliases execute `NationProcurement.buy` directly | GUI presence checks alone would be bypassable. Counter/session/quote validation must gate every purchase path server-side; remote catalog viewing can remain. |
| Current factory eligibility checks only any owned building of the requested type | It does not establish a completed local shop, selected plot/counter identity or factory tier. New shop eligibility must resolve those actual records and pending construction state. |
| Items/entities are delivered before debit, followed only by `setDirty()` | Existing normal restart success does not establish an interruption-safe purchase transaction. Persist an immutable operation/price/delivery identity and reconcile debit/delivery rather than replaying either blindly. |
| Vehicle purchase sets `CatanCraftPurchasedVehicle=true` and `CatanCraftVehicleNation` before `addFreshEntity` | Every new fixed-bay, recovery or reward path must preserve this ordering and the same persistent entity UUID. A nation tag alone does not activate the current crowbar guard. |
| Crowbar protection trusts the boolean marker, independently of a purchase receipt or nation tag | Imported/copied NBT is not proof of an authentic purchase. A true marker on an ordinary entity protects it; an absent/false marker leaves it ordinary. This is a provenance boundary, not a demonstrated unprivileged player tag-edit exploit. Do not auto-tag all unmarked entities or strip unknown tags. New deliveries need durable receipt-to-UUID/type/nation binding. |
| Existing spawn search chooses space relative to the purchaser | A physical vehicle shop/reward must use its approved fixed bay and initialized native geometry, never a nearby fallback or portable item. A blocked bay must not debit or lose a pending reward. |
| Siege settlement may later change vehicle ownership | The purchased protection marker must survive ownership transfer. Update the owner/receipt consistently; block restoration must not clone or recreate vehicles. Current structural snapshots do not perform entity settlement. |

The Forge parent fallback is generic; the pinned source audit did not identify a native SBW `VehiclePart` case. It does not certify arbitrary custom wrappers, very deep nested parts, or another mod's hit routing. A malformed/deep chain returns no purchased root after the bound; no applicable installed native bypass has been demonstrated. Keep this coverage limit explicit and add an actual fixture only when the installed entity path is identified.

Future fixed weapons and reward vehicles are not automatically covered by the purchase tests merely because their names use SBW. They require the actual approved entity, creation path and retrieval routes. Non-sneak assembling-table conversion remains outside the currently purchasable vehicle contract.

## Minimum combined solo acceptance when the playable build is ready

This is the checklist shape for the future combined build, not an instruction to install this audit or test incomplete features now.

1. At the actual registered weapon counter, buy one intended unlocked gun/ammo offer. Check the displayed tier/price, exact delivered item/ammo and one charge. Inspect a locked tier and a reserved drop-only weapon; neither should be purchasable. A remote/stale purchase must refuse without charging.
2. Use an already purchased vehicle for the crowbar check. Sneak-interact with the crowbar in the main hand and then offhand; the same vehicle must remain and no packed vehicle item may appear in inventory or nearby. A denial message alone is not a pass. No replacement vehicle purchase is required solely for this check.
3. At the next ordinary save/stop/restart, check the same purchased vehicle and retained shop delivery/accounting. Repeat only the narrow crowbar interaction needed to establish persistence; do not redo accepted city purchases or induce a crash.
4. If fixed-bay vehicle sales or monument rewards are included in that actual build, append their one focused end-to-end delivery check using the same receipt/protection rules. Do not silently add a whole extra feature or test exercise to this checklist. Backend-only siege work stays automated until a playable approved siege flow exists.

Developer validation should cover duplicate confirmations, full inventory, missing mods/IDs, owner/factory changes, save interruption and existing ordinary unpurchased retrieval in disposable worlds. Avoid requiring the owner to create artificial unpurchased vehicles or run debug commands for the ordinary-behavior control. Closing SEC-01 requires matching runtime evidence and the scoped owner result, not this plan, a green Java build or a synthetic denial message.
