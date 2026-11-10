# 0.1.39 siege restoration — validated checkpoint, 2026-11-10

The combined structural snapshot/restoration backend is implemented and validated on `chatgpt/siege-restoration-0.1.39`. This continues 0.1.38, retaining delivered 0.1.33 purchase recovery and 0.1.34 safeguards. No live world, installed mod, main branch or deployment was changed. This checkpoint does not ask the owner to install an unregistered backend or repeat accepted purchases.

## Exact source and binary

- Source: `b652bb5ab468d1c45c066fd19175a11ecae93bd5`; tree `68f92fdccc2d8b72453fb6fd41de50fdd8ed6513`.
- Parent: `acd6e906a9cbcec500cf5f4e91b6e1f849d0a0e0`, validated 0.1.38 documentation head. Fresh remote heads were checked before edits.
- JAR: `catancraft-0.1.39-siege-restoration.jar`, 3,049,383 bytes, SHA-256 `222f250c09480ea9834b2dd515d066f97e92f6c35a275aec81587001d62573a3`.
- [Workflow 42888843335](https://github.com/quintaneous/catanCraft/actions/runs/42888843335): completed SUCCESS, first native attempt, Java17 / Gradle8.8 / Minecraft1.20.1 / Forge47.4.10.
- [Combined source/evidence artifact 13796658661](https://github.com/quintaneous/catanCraft/actions/runs/42888843335/artifacts/13796658661): 6,039,926 bytes, SHA-256 `558e4ca8f5b7ba691927b27c5ae39543f39b72fa7bc37fbe016756151f9f8041`.

This documentation and roadmap follow-up does not rebuild or change the exact source represented by that JAR.

## Implemented behavior and limits

The [contract](SIEGE_RESTORATION_039_CONTRACT.md) describes immutable actual pre-siege block snapshots, preserved protected block-entity NBT, a frozen explicit damage subset, guarded four-write restoration slices, durable physical-save/progress ordering and interrupted recovery. Inventories are compared and left untouched; changed contents block restoration rather than being rolled back or cloned. Unknown blocks, fluids, unauthorized holes, moved ownership, changed history, incomplete context and native entity obstructions refuse without replacing the conflicting content.

This is a small-whitelist, unregistered backend. No player command/UI, live city profile, weapon hook, full-wall restoration, outcome integration, settlement or new-session archival policy is enabled. Its explicit 512-cell limit and simple structural whitelist do not cover the whole accepted wall assembly. Existing global protections remain active. Full scans, prefix simulation and synchronous saves are not a frame-time guarantee. Checksums/locks are cooperative persistence safeguards, not hostile-filesystem or arbitrary power-loss guarantees.

## Executed validation

| Suite | Actual phase executions | Passed | Failed |
|---|---:|---:|---:|
| New structural snapshot/restoration | 22 | 22 | 0 |
| Retained objective state/recovery 0.1.38 | 22 | 22 | 0 |
| Retained gate transitions 0.1.35 | 12 | 12 | 0 |
| Retained baseline | 50 | 50 | 0 |
| One physical restart scenario, seed + verify JVMs | 2 | 2 | 0 |
| **Total** | **108** | **108** | **0** |

There are **107 unique scenarios**, because the restart case runs twice. Every native log reports its expected GameTest count and required passes with exit0. All five builds produced the same JAR/source. No failed native execution or unexplained retry was discarded. Retained baseline logs include expected operation-refusal diagnostics inside passing tests; those are not failed GameTests. Earlier release failures remain in their original evidence and were not relabeled as this run.

The new22 cover mixed actual source recovery, four-write/resume counts, frozen subset and unknown-damage refusal, inventory/block-entity preservation, nearby items, genuine collision refusal, changing authority/world/owner/siege/plan, lock contention and lock identity replacement, corrupted/currently altered history, missing intent, source-save failure, post-block and post-save failure, pre/post journal ambiguity, immutable snapshot faults, symlink refusal and inert DONE behavior. These are native disposable fixtures, not authored-world or installed-mod acceptance.

Restart seed process `3151:1794340215514` saved eight mixed structural cells in a four-original/four-AIR state, with protected barrel contents of 13 emeralds and two books. Verify process `3309:1794340237115` checked the actual saved partial blocks, exact original snapshot/partial journal bytes, and reloaded full barrel NBT before opening recovery. It wrote the remaining four originals, preserved inventory and verified repeat completion makes zero writes/saves/receipt changes. The second process did not copy, clear or reconstruct the fixture. Fault tests model interrupted boundaries; this successful normal two-process restart is not a forced-power-loss certificate.

Downloaded combined ZIP CRC/digest, all52 manifest records, all383 source archive files against the exact git commit, JAR version/source manifest, common job JAR hashes and sealed restart proof/checksum links were independently verified. **All466 pre-existing compiled classes are byte-identical to 0.1.38**, and all pre-existing assets are unchanged; only existing JAR manifest/mod version metadata differs. Machine results: [snapshot39_validation.json](evidence/snapshot39_validation.json).

## Roadmap addition and next boundary

The owner's new gun progression and weighted monument supply-drop requirements were added to [roadmap #10](https://github.com/quintaneous/catanCraft/issues/10), preserving ECO-03/ECO-02/MON-01 and phase order. A [complete reference allocation](ECO03_WEAPON_TIERS_AND_MONUMENT_LOOT_PLAN.md) covers70 TaCZ and43 SBW configured entries with28 provisional drop-only candidates. It includes a price/rarity testing plan and persisted one-time reward/claim requirements. It does not claim an installed/custom-pack scan or combat balance, and it changes no existing shop price or monument payout.

Next siege work must define supported damage/drop/secondary-effect behavior and connect restoration to saved outcomes, before any TaCZ/SBW damage routing or ownership settlement. Keep defender timeout/surrender wins. Preserve accepted City A North Reinforced / East, South and West Basic walls, mixed connections, restart, and 0.1.32 readiness at their original scope. **SEC-01 remains UNRESOLVED / HIGH PRIORITY.** No Blender assignment, multiplayer, manual imports, reset or repeat accepted purchase test is requested.
