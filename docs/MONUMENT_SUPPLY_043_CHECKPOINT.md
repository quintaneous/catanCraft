# 0.1.43 monument supplies — validated development checkpoint

This is a development checkpoint within the combined shop/reward/crowbar acceptance plan, not an owner installation request. The [contract](MONUMENT_SUPPLY_043_CONTRACT.md) and [provisional pools](MONUMENT_SUPPLY_043_POOLS.md) describe the implemented behavior. SEC-01 remains **UNRESOLVED / HIGH PRIORITY** pending actual installed-pack acceptance.

## Exact validated source and artifact

Source `581259acb84aecdd1ae77090ee61ba9dbdd62b5d`, tree `ee7998380d982ce5da0ad24f36d43a86c0ca9ef0`, on `chatgpt/combined-shop-acceptance`. [Workflow42931037121](https://github.com/quintaneous/catanCraft/actions/runs/42931037121) succeeded: **207/207 native phase executions,200 distinct scenarios,zero failures**. All15 job builds produced the same source-stamped JAR. Later documentation/evidence commits do not rebuild it.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| `catancraft-0.1.43-monument-supplies.jar` | 3,544,624 | `9941d561cb201de84bd352137320420a2bdc3119cc897d5955e775ba0d78e866` |
| Combined source/evidence ZIP, GitHub artifact13816885455 | 8,284,425 | `fe520440464cb5eb436fc32a029374b64145128fd5b404cd1066a60431494da9` |

The downloaded archive's checksum, ZIP integrity/safe paths, all264 manifest records,455 Git source files, source-stamped JAR/version and native sealed receipts were independently verified with [the verifier](../tools/shop43/verify_evidence.py). [Machine evidence](evidence/monument_supply43_validation.json) records exact runtime hashes, phase results and original source/player/ledger chains. Compared to validated042,548 pre-existing classes and all existing construction assets are byte-identical; changed entries are restricted to the reviewed source owners and version metadata. New test templates are exact copies of the existing fixture.

## Executed coverage and behavior

All178 prior042 phases were retained. New coverage adds supply8, claim failure/authority10, real TaCZ/SBW claims4, real native claims across distinct JVMs4 phases, and combined-provider config lifecycle3. The eight supply tests include98,304 deterministic weighted draws,151 entries,100 exact entitlement identities,79 gun/ammo bundles,63 ammo-only entries, public activation, historical payout preservation and malformed token/record refusal. Draws and internal variants are not counted as extra GameTests.

Real native claims qualify all100 identities, keep the three rare guns out of paid stock, and directly recheck original frozen042 TaCZ/SBW supply fixtures without requoting, replacing UUIDs or repricing. Restart proofs retain the original winner, settled source reward, native item/UUID, claim intent, player receipt and national ledger. Already-delivered claims complete without additional items, debit, source credit or player/ledger saves; three further retries are inert. Claim tests exercise inventory capacity, leader/actor/source changes, missing native player UUID, incompatible history, save failures, consumed delivery and conservative holds.

The final terrain run passed the deterministic raw-map rebuild regression,32 cache-reset cycles, all ten guarded setters, unrelated-setting persistence, actual external FileWatcher edits, and byte-exact restoration. The watcher scenario observed TaCZ reload counts2→3→4 and SBW1→2→3; the setter scenario observed TaCZ4→7→8 and SBW3→4→5. Proofs use each scenario's own counters; batch order was watcher→loaded→setters. All ten effective native flags stayed false, including when the authored flags were true. No `ConfigLoadingException`, `ParsingException` or FileWatcher exception appears in the retained final logs. Both published runtime manifests/provenance remain exact042 inputs.

This addresses the observed guard-induced autosave/parser issue and the newly discovered overlay startup race for the pinned published runtime. It does not repair arbitrary malformed TOML or every third-party writer. The known optional SBW `grant_manual` / missing `patchouli:guide_book` loot-table error remains visible; this is not a claim of a completely error-free full modpack startup.

## Retained failed attempts

| Attempt | Exact source | Workflow | Actual result |
|---|---|---|---|
| 1 | `4628e40d67c1091b3c914a2d56c1ccb8061b73ec` | [42928966039](https://github.com/quintaneous/catanCraft/actions/runs/42928966039) | All15 build jobs failed compilation; zero native tests executed, no JAR. |
| 2 | `bcc85c2b5f4a5b8ce29e4542ce6263622b378aa7` | [42929354313](https://github.com/quintaneous/catanCraft/actions/runs/42929354313) | Built successfully;195 phases executed,193 passed,2 failed,12 not executed because optional-provider startup failed. |

Attempt1 called `MonumentSupply43.entitlements()` from two native test helpers; the canonical API is `NativeReward43.entitlements()`. Correcting those references changed no assertion. [All15 original build logs](evidence/monument_supply43_run1_compile_failed.zip),43,852 bytes, SHA-256 `3032e23f635b39dc55a7797deaf228ce704491505d1d92dcae0e2d05c1350f85`. Original combined artifact13814663179:2,861,610 bytes, SHA-256 `1edcf5cbc9042c30167a6383a09609f9cc3c04e70f5b49adb6a6b24f0661dad4`.

Attempt2 passed the new supply8, claims10, native TaCZ/SBW claims/restarts8, and retained stock/shop/reward/TaCZ/purchase-recovery/baseline suites. The terrain suite executed3: initial protection passed; two actual watcher waits failed. SBW protection startup, SBW42 restart seed and combined-provider stock startup failed before tests. The latter prevented its dependent verify/procurement phases. [Native/build logs, manifests and proofs](evidence/monument_supply43_run2_failed_logs.zip),269,629 bytes, SHA-256 `360f41a3d63804cfccc88a14ccfd11d4d7fb8a3626a547168946222c8ae881cc`. Original combined artifact13815398414:8,239,838 bytes, SHA-256 `91f9df53908a81adf599a365a0602e38eda9d876213330dd330b248f4e15ed5e`; asset-download logs remain there.

The configuration failures are retained separately from successful claim behavior. Forge's initial config correction and NightConfig's already-registered watcher can mutate the raw map during the new overlay's validation, causing `ConcurrentModificationException` or an incomplete-config refusal. The external edit fixture used atomic replacement, while the pinned watcher expects modification events. GameTest batches also execute in an unspecified order, and ticks are accelerated; no proof may assume numeric batch chronology or use tick count as elapsed wall time. The final source replaces watched-map traversal with immutable-spec structure and native getOrElse defaults, retains effective validation, emits the watcher’s actual modification event in the fixture, bounds waits by monotonic wall time and records each scenario separately. The complete final rerun above passes; original failures remain retained.

## Owner boundary

No live world or installed mod was accessed or modified. No reset, imports, multiplayer, repeated accepted purchases or artwork work. Accepted City A North Reinforced and East/South/West Basic, readiness and 033/034 purchase recovery remain preserved. Vehicles are excluded from active reward pools until fixed deployment bays are reviewed. Prices/odds remain provisional; actual authenticated-client interaction, installed/add-on matching, balancing and player-facing siege remain separate work. The scoped final configuration evidence addresses the watcher observation; it does not close SEC-01 or establish owner acceptance.
