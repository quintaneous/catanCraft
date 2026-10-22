# DEF-02: immutable P1 developer fixture

P1 is **unapproved, test-only geometry**. It is not an enabled defense catalog entry, a current-city placement approval, or a playable milestone. `SectorP1Fixture` requires an actual `GameTestServer` before returning metadata, sector plans or corner coupons. There are no production registry or purchase-command hooks. A future approved revision requires separate immutable input and review; it must not relabel P1 as approved.

## Input and integrity

Original archive: `CatanCraft_Sector_Wall_P1_Review_Assets.zip`, SHA-256 `0cc36bd927d66c1fd0db5ea7d3b379170324ccab76bd3f03eddeaeae43581100`.

The independently extracted source is `/workspace/catancraft-sector-p1-audit-20261018/CatanCraft_Sector_Wall_P1`. Read-only checks verified all 133 checksummed members, both target schematic hashes, all 20 component hashes, exact non-overlapping component/mask reconstruction and the 3,582-cell basic-to-reinforced delta. No original authoring scripts or world writers were executed.

`tools/export_sector_p1_fixture.py` exports canonical complete-state contexts and explicit sparse plans without authoring geometry. It preserves road, protection, corner, emplacement and footprint-proposal metadata. A separate reproduction produced identical bytes.

Resource: `src/main/resources/assets/catancraft/sector_fixture/p1.json.gz`

SHA-256: `701a70af260a34ac16b439e18dc15a24668bf212352b86ff199b5386a968dcaa`

```sh
python3 tools/export_sector_p1_fixture.py \
  --input /workspace/catancraft-sector-p1-audit-20261018/CatanCraft_Sector_Wall_P1 \
  --output /workspace/p1-fixture-reproduction.json.gz
```

## Exact fixture contract

| Item | Verified count or bounds |
| --- | --- |
| Basic / reinforced target XYZ | 179 × 9 × 11; 17,721 cells each |
| Local anchor / stored offset | `[89,0,6]` / `[-89,0,-6]` |
| Candidate City A north anchor | `[-750,81,-838]` |
| Inclusive candidate bounds | `[-839,81,-844]..[-661,89,-834]` |
| Basic / reinforced sparse writes | 3,878 / 4,351 |
| Basic → reinforced changes | 3,582 |
| Internal components | 10 per target; not separate purchases |
| Full local read context | `[0,-1,0]..[178,8,13]`; 25,060 cells |
| Additional primary-road source reads before deduplication | 5,016 |
| Corner coupons | 12; absent/basic/reinforced neighbors, both corner ends and target variants |
| Fixed lights / embedded inventories or weapon entities | 7 / none |

Air is preserved read context, not clearing permission. Native fixtures stage the authored source at an isolated high-sky location with boundary support; source/target properties remain exact. Both 0° and 180° geometry are exercised. Rotation does not constitute a south-site or another-city terrain survey. Protection-envelope checks are explicitly against supplied historical references, not a fresh live-world survey.

## Native validation

Namespace: `catancraft_sectors_p1`. Central execution recorded **4 executed, 4 passed, 0 failed**, exit 0. The preserved log contains `4 GAME TESTS COMPLETE`, `All 4 required tests passed` and `BUILD SUCCESSFUL`:

- `/workspace/integration-evidence/roadmap-focused/p1.log`
- `/workspace/integration-evidence/roadmap-focused/p1.exit`

The four tests cover:

1. Real basic purchase, exact reinforced upgrade, both reverse operations, one whole-side charge per operation, duplicate safety and actual adjacent book/inventory NBT preservation.
2. A conflict in the final authored component refusing the entire sector without writes, debit, progress or journal creation.
3. Exact source/target state and coordinate rotation in both orientations, authored road/turn-margin exclusion and supplied protected envelopes.
4. Native final-state support, cap closure/opening, supported deck and clear circulation across all 12 coupons in both rotations: 24 geometry cases.

Reproduction uses the prepared Java 17 / Gradle 8.8 / Minecraft 1.20.1 / Forge 47.4.10 environment:

```sh
gradle runGameTestServer -PgameTestNamespace=catancraft_sectors_p1 --offline --no-daemon --max-workers=4
```

These native results validate isolated prototype contracts and the grouped transaction engine. They do not certify owner acceptance, all production sites, live-world compatibility, client appearance or SBW operation.

## Gates that remain closed

The entrance is still under revision. P1's 47-column opening, widened typical reservation and projecting bays remain unapproved; no final gate dimension or global footprint follows from them. Corner coupons are short structural examples, not approved adjoining whole sectors. Their cap transitions must never open an unsupported edge.

The two emplacement records have no verified weapon IDs or installed models. Their explicit half-open weapon/operator bounds are **[5,3,2] XYZ**, irrespective of ambiguous older prose. No weapon, ammunition, damage, traverse, muzzle, packing, persistence or armed-readiness claim is made. No NPC crew or automatic firing is implemented. The crowbar/retrieval exploit remains **UNRESOLVED / HIGH PRIORITY** until verified against the intended runtime.
