# ECO-03 — native stock 0.1.42 validation checkpoint

Exact source **`9b3eb646a494348632e4b5c6a692c57f9a9f2f16`**, tree `faddaf126efce81ffef6cb5a0cadb669ffa1cc06`, built and passed [workflow 42914749967](https://github.com/quintaneous/catanCraft/actions/runs/42914749967): **178 native phase executions / 173 distinct scenarios / zero failures**. All ten validation jobs and collection succeeded using Java 17, Gradle 8.8 and Forge 47.4.10. This is a development checkpoint in the combined acceptance plan, not an owner installation request.

## Implemented and validated

The physical counter now browses 97 qualified native products: 76 ordinary guns (52 TaCZ + 24 SBW) and 21 compatible ammunition products. Cumulative factory gates, explicit provisional prices, eight-row pagination and exclusions for all 28 DROP, two HOLD and seven conditional/special guns are enforced. Existing four aliases/gates/prices and historical 040 purchase recovery remain. TaCZ .338 explicitly supplies 30 rounds to respect its native stack limit; the other ammo bundles contain 32.

| Selected native suite | Executed / passed |
|---|---:|
| Catalog, tiers, explicit quantities, exclusions and pagination | 8 / 8 |
| TaCZ native offers, mutation refusals and purchase recovery | 8 / 8 |
| SBW native offers, mutation refusals and purchase recovery | 8 / 8 |
| TaCZ and SBW separate-JVM recovery phases | 4 / 4 |
| All 97 offers with both published providers loaded together | 1 / 1 |
| Retained physical shop and separate-JVM recovery | 37 / 37 |
| Retained baseline and paid purchase recovery | 69 / 69 |
| Retained tier/reward checks and weighted-outcome restart | 34 / 34 |
| Published SBW protection and purchased-vehicle restart | 9 / 9 |

Both original native gun purchases recovered from delivered-but-uncommitted records in distinct JVMs, retaining original intent bytes, player bytes, native ledger accounting and frozen configuration fingerprints. Recovery caused zero additional debit/items/player saves; initial completion performed the existing core's one verified ledger persistence. Three completed retries performed zero saves. SBW UUID `67f043c5-4c1f-4922-bc24-752754d0250d` survived intact. TaCZ processes were `3386:1794349849183` → `3550:1794349873346`; SBW `3708:1794349854079` → `3872:1794349882299`.

The SBW regression additionally proves that incidental native perk/Set enumeration order does not invalidate a quote, while membership changes, duplicate multiplicity and ordered firing-mode changes remain detectable. Both-provider validation uses TaCZ 1.2.0, SBW 0.8.9.2 and SimpleBedrockModel 2.6.2, with 15 selected pinned runtime artifacts. This qualifies published test inputs, not the owner's installed/add-on pack or connected client.

## Independently verified artifacts

- JAR `catancraft-0.1.42-native-weapon-stock.jar`, **3,394,043 bytes**; SHA256 `af1a2def5eb3fc8db5088e21d41a8ceeb2bfa551f33b143413b022d287b50c80`.
- [Combined source/evidence artifact](https://github.com/quintaneous/catanCraft/actions/runs/42914749967/artifacts/13807659810), ID `13807659810`, **7,350,353 bytes**; SHA256 `df07f5c41980cbcac7143e039413fd064748d67e5334557d9554713dc4459a2e`.
- [Machine verification](evidence/native_weapon_stock42_validation.json); reproducible verifier `tools/shop42/verify_evidence.py`.

The downloaded archive digest, ZIP CRC/path safety, all 168 manifest records and all 435 source files against Git were independently checked. Every job built the same source-stamped JAR. Native sealed purchase/reward intents, completion receipts, original saved player/ledger bytes and separate-process proofs were decoded and verified. **533 pre-existing class files** and all pre-existing construction assets are byte-identical to 041. Existing class differences are limited to the shop/session integration, read-only purchase inspection, controlled test-root whitelist and lifecycle hooks (plus their nested classes); the payment/delivery/recovery algorithm is unchanged.

## Preserved first-run failures

Source `111532c48e8bb519a32232b5b0d0d26900bd87bd` built, but [run 42912799018](https://github.com/quintaneous/catanCraft/actions/runs/42912799018) failed: **176 executed / 172 passed / 4 failed**. The two TaCZ restart phases did not run after its provider suite failed.

- The TaCZ .338 offer requested 32 despite native limit 30, failing ammunition and combined-provider cases. The catalog now explicitly displays/delivers 30; native capacity checks remain strict.
- The legacy four-row catalog display regressed. Its original discovery rows were restored; the physical counter retains the new paged catalog.
- SBW restart fingerprints depended on native registry/perk enumeration order. Full entries and duplicate counts are now sorted only for semantically unordered membership/Set values; ordered lists, native UUID and substantive configuration remain bound.

The new restart fixture was also corrected to expect the existing core's one verified ledger save on first completion, while retaining zero extra charge/item/player save and zero completed-retry saves. No production recovery algorithm was changed to satisfy that assertion. Run1 journals remain immutable; run2 seeded fresh disposable proofs.

All first-run files remain in artifact 13808232809, SHA256 `3997b73125ead0ba45e8868fddda5686d77b6ae08f890297e6cc05e5e0e2c546`; all 154 manifest entries and 434 source files were verified. [First-run machine record](evidence/native_weapon_stock42_run1.json). Exact raw failed logs are additionally committed in [the failed-log archive](evidence/native_weapon_stock42_run1_failed_logs.zip), SHA256 `d4b8fe6f1d27a1ccc1494cb87a8a15b494d750ddd2511c5d2fea72a480e60358`, so their retention does not depend on Actions artifact expiry.

## Remaining delivery boundaries

The first combined log recorded a pretest Forge/NightConfig duplicate `[ammo]` watcher exception immediately after existing terrain-protection configuration writes. It did not recur in run2, but that does not resolve write coordination or establish all final protection settings. `CombatTerrainSafety` is unchanged. Investigate and verify this observation before combined owner delivery; synchronization only on the public setting/spec wrapper is not a demonstrated fix. Raw logs also retain the previously documented optional SBW `grant_manual`/missing Patchouli parse error and development warnings. Passing selected tests is not an error-free full-modpack certificate.

Read [contract](NATIVE_WEAPON_STOCK_042_CONTRACT.md), [provisional prices](WEAPON_STOCK_042_PRICES.md) and [combined acceptance plan](COMBINED_SHOP_ACCEPTANCE_PLAN.md). Native stock is implemented; price/combat balance remains uncalibrated. Monument default compatibility payouts and pending equipment references are unchanged. Implement actual randomized supply-drop claims/production odds next, alongside the startup configuration follow-up; exact installed-pack/client qualification, fixed safe vehicle bays and finished player-facing siege remain separate work before their release claims.

One combined playable build and one focused solo checklist remain the delivery goal. No intermediate owner install, live-world/mod change, reset/import, accepted-purchase replay, multiplayer or Blender work. Accepted City A North Reinforced / East-South-West Basic and readiness/restart remain preserved. **SEC-01 stays UNRESOLVED / HIGH PRIORITY pending installed-pack acceptance.**
