# ECO-03 / SEC-01 — 0.1.40 validation checkpoint

Combined owner acceptance remains pending. Do not install an intermediate build
for this checkpoint. See WEAPON_SHOP_040_CONTRACT.md for implemented scope and
COMBINED_SHOP_ACCEPTANCE_PLAN.md for the linked remaining work.

## Corrected exact-source run: validated development checkpoint

Source **`56e54c7d5371f78b4c6789d69d267e5ef0baec91`**, tree
`424ab70e4852b5eb1d67db0838df2f480dce9e96`, workflow
[42897946825](https://github.com/quintaneous/catanCraft/actions/runs/42897946825).
All six validation jobs and evidence collection succeeded: **137 native phase
executions across 135 distinct scenarios, zero failures**.

| Native suite | Executed / passed |
|---|---:|
| New physical counter and purchase cases | 35 / 35 |
| Shop restart, independent seed and verify JVMs | 2 / 2 |
| Retained baseline | 50 / 50 |
| Retained paid purchase recovery | 19 / 19 |
| Retained structural restoration | 22 / 22 |
| Pinned published SBW protection | 7 / 7 |
| SBW actual procurement restart, seed and verify | 2 / 2 |

The shop suite consists of 27 transaction cases and eight facility/catalog/
refusal cases. It covers frozen save verification, silent failures, save callbacks,
changed/moved/consumed items, death cloning, stale ownership, immutable prices,
reentrancy and held ambiguous deliveries. Geometry checks use the pinned source
in six actual plot/orientation transforms, and native collision/eye rays through
the normal opened-door route in both rotations. Fake/disconnected negatives do
not certify positive authenticated-client behavior.

Shop restart evidence binds the same source and original native files to JVMs
`3134:1794343489722` and `3286:1794343507833`. Actor A paid before interruption,
then recovered one item with no further debit. Actor B's saved delivered item was
consumed before restart; recovery delivered zero replacements and charged zero.
Both original intent files were unchanged. All six completed retries across the
two actors performed zero saves, debits, insertions or history rewrites.

The published SBW run also restored both actual previously purchased vehicle
entities in a second JVM and checked ownership, both crowbar hands, absence of
packed items and ordinary unprotected retrieval. This reruns existing035 protection;
it is not a newly invented fix or the owner's installed-pack acceptance. SEC-01
remains **UNRESOLVED / HIGH PRIORITY**.

### Artifact provenance

- JAR: `catancraft-0.1.40-weapon-shop.jar`, 3,165,039 bytes.
- JAR SHA-256: `819ccc3c52cb15dfe24f3a95e0ff7138d818be45e76e2e43bbde89387128aa0d`.
- [Combined source/evidence artifact](https://github.com/quintaneous/catanCraft/actions/runs/42897946825/artifacts/13800673590): ID `13800673590`, 6,409,299 bytes.
- Archive SHA-256: `5c2f068ceba678e117c9e8eaf10acfde15dcc99df067835a89963494484f5574`.
- [Machine-readable verification](evidence/weapon_shop40_validation.json).

Independent downloaded checks verified archive digest/CRC/path safety, all 78
manifest file hashes, all 402 source files against Git, common JAR hash across
jobs, embedded source/version, exact native saved player/ledger and sealed
intent/progress proofs. Compared with validated039, 475 existing class files and
all existing construction assets are byte-identical. Seven existing classes
change only in lifecycle, client view, procurement routing, interaction and network
protocol integration; the two other changed existing entries are version/source
metadata. All purchase/producer/siege restoration classes remain carried forward.

This checkpoint does not claim native TaCZ integration, authenticated GUI clicks,
actual installed/add-on catalog matching, full tier stock, randomized monument
rewards, fixed-bay vehicles, completed player-facing siege or owner acceptance.
Keep one combined solo checklist for the eventual playable bundle. No separate
installation, accepted city repurchase, reset, import, multiplayer or Blender task.

## First run: retained failure evidence

Source `c3b54ec226cb8335783da474b372de44a44c8e58`, workflow
[42896885413](https://github.com/quintaneous/catanCraft/actions/runs/42896885413).
Java 17 / Gradle 8.8 / Forge 47.4.10 builds succeeded. Actual native executions:
136, of which 107 passed and 29 failed; the second shop JVM never ran. This is
not a validated release.

- Shop: 35 executed, 7 passed, 28 failed. All 27 purchase fixtures and the detached
  actor fixture attempted to modify Forge's unmodifiable public player roster.
- Shop restart seed: 1 executed, 1 failed. The same roster insertion exception
  prevented native player persistence; the verified-save boundary refused the
  absent player file. Verify phase did not run.
- Retained baseline 50/50, purchase recovery 19/19, structural restoration 22/22,
  published SBW 7/7 and SBW procurement seed/verify 1/1 each passed.
- CI evidence copying also tried nonexistent shop-restart files in unrelated
  jobs, causing job failure after successful test execution. The aggregate
  artifact still retained the actual logs/results with `all_passed: false`.

Retained combined artifact `13800813040`, 6,387,713 bytes, SHA-256
`91b01f8cfd3442417152065dd15d204112c6f7b7b553bb1db624b5453c105059`.
Downloaded archive CRC, digest and all 67 manifest file hashes verified.

Corrections use a marked controlled-world helper to call the actual native
PlayerDataStorage.save method, verify its real directory, and avoid roster edits.
Production saveAll, accounting, recovery, assertions and test counts stay unchanged.
The copy step now checks whether each optional evidence file exists. The corrected
run above validates these fixture/workflow corrections without relaxing assertions.
