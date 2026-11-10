# ECO-03 / MON-01 — 0.1.41 validation checkpoint

Exact source **`880acefc0ea6c43de100afbedec47fdc54e4930f`**, tree
`4161ab643a6b41c891bbf2d9f55b11eff4272314`, passed the first native run:
[workflow 42907852441](https://github.com/quintaneous/catanCraft/actions/runs/42907852441).
All seven validation jobs and collection succeeded: **149 native phase executions,
146 distinct scenarios, zero failures**. Java 17 / Gradle 8.8 / Forge 47.4.10.

| Native suite | Executed / passed |
|---|---:|
| Tier policy and monument reward cases | 32 / 32 |
| Weighted reward restart, seed and verify JVMs | 2 / 2 |
| Retained physical shop cases | 35 / 35 |
| Retained shop restart, seed and verify JVMs | 2 / 2 |
| Retained baseline | 50 / 50 |
| Retained paid purchase recovery | 19 / 19 |
| Published SBW protection | 7 / 7 |
| Actual SBW procurement restart, seed and verify JVMs | 2 / 2 |

The new suite comprises eight tier-policy and 24 reward cases. It covers all 113
reference entries, reserved/held exclusions, immutable native metadata, weighted
selection and strict schemas, frozen hidden outcomes, original winners, silent or
throwing native saves, readback changes, stale live state versus native credit,
retry idempotence, geometry/corruption/overflow holds, map reload and pending
entitlements without item/entity delivery. Review corrections added native-history,
active-pointer and unrelated-record guards before the first committed test run.

The reward restart preserves original native saved bytes across JVMs
`3360:1794347167473` and `3518:1794347190066`. The original weighted outcome and
winning nation survive, treasury/resources are credited once, all three gun/ammo/
vehicle references remain pending, no equipment spawns, and three completed retries
perform zero saves or history rewrites. This uses explicit component recovery in a
marked controlled fixture; it does not certify automatic production startup with
the actual installed pack.

The retained shop restart again grants one paid-undelivered item without charging
again, and does not replace a delivered item consumed before restart. Published
SBW checks restore both actually purchased vehicle entities in another JVM and
verify ownership, both crowbar hands, no portable packed item and ordinary
unprotected retrieval. This carries forward035 protection; SEC-01 remains
**UNRESOLVED / HIGH PRIORITY** pending installed-pack acceptance.

## Independently verified artifacts

- JAR: `catancraft-0.1.41-economy-foundation.jar`, **3,266,697 bytes**.
- JAR SHA-256: `811211af67628449daba93a9889b0e9cc6e70d0d884f573224eb2011608ae369`.
- [Combined source/evidence artifact](https://github.com/quintaneous/catanCraft/actions/runs/42907852441/artifacts/13806750955): ID `13806750955`, **6,699,997 bytes**.
- Archive SHA-256: `8d67d0a663653727bb37ca162f3bd0c5e74b48c9a4eaf2867098258441663445`.
- [Machine-readable verification](evidence/economy41_validation.json).
- Reproducible downloaded-evidence verifier: `tools/shop41/verify_evidence.py`.

Downloaded archive digest, CRC and path safety, all 96 manifest records and all 415
source files against Git passed independent checks. Every job built the same JAR;
embedded source/version match. Native purchase and reward restart bytes, sealed
intent/completion hashes, original winner and single-credit balances were decoded
and checked independently. Compared with040, **508 pre-existing class files** and
all pre-existing construction assets are byte-identical. Only five existing
classes change: monument data/manager, shared SavedData preservation, command
failure reporting and the shop policy guard; version/source metadata also changes.

## Delivery boundary

This is a validated development checkpoint inside the [combined acceptance plan](COMBINED_SHOP_ACCEPTANCE_PLAN.md), not a new owner installation request.
The full reference tier policy and durable weighted-reward infrastructure are now
implemented. Current four offers/prices and default monument payouts remain
unchanged. New production tiers, prices/loot odds, native gun/ammo qualification,
equipment claim delivery, safe fixed vehicle bays and finished player-facing siege
remain before the combined playable release and its one focused solo checklist.
No native TaCZ, authenticated-client, actual installed/add-on-pack or owner
acceptance is claimed. No live world/mod changes, reset, imports, accepted-purchase
replay, multiplayer or Blender work occurred. Accepted City A walls/readiness and
purchase recovery remain preserved. [Detailed contract](ECONOMY_041_CONTRACT.md).
