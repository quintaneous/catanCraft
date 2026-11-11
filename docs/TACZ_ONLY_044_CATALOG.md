# MON-01 / ECO-03: TaCZ-only gun policy and provisional pools 044

The owner's correction supersedes the mixed TaCZ/SBW acquisition policy in 042/043: **TaCZ is the only enabled handheld gun provider. Superb Warfare remains for vehicles; SBW handheld explosives are deferred.** This document does not approve new vehicle paths, explosives, prices, or installed-pack acceptance.

`ActiveWeaponStock44` exposes exactly **69 unchanged TaCZ offers: 52 guns + 17 ammunition identities**. The four original 040 offers/prices/gates and every remaining TaCZ 042 price/native fingerprint/revision are preserved. The 24 SBW gun offers and four SBW ammunition offers are absent from current browsing/lookup and fail current-policy checks. The original 042 catalog remains immutable historical metadata, not current acquisition authority.

`NativeReward43` retains its frozen 100-entry schema for reading historical 043 outcomes, but current delivery/recheck/description require the TaCZ-only policy. `activeEntitlements()` contains **72 entries: 55 guns (52 ordinary + three rare) + 17 ammunition identities**. Existing TaCZ 043 entitlement revisions remain valid without migration. Historical SBW outcomes/receipts are retained; they do not authorize current delivery. No old outcome is rerolled, silently substituted, or repriced.

`MonumentSupply44` activates new pools under `monument_supply44_tacz_only_provisional_v1`. Frozen 043 rounds keep their original roll, winner and accounting history. Each new pool has 100,000 tickets and chooses one bundle once. Gun bundles contain one unloaded gun plus one compatible ammo entitlement; ammo is collected separately. `.338` bundles contain 30 rounds; other ammo bundles contain 32. Odds and retained shop prices remain **provisional playtest values**, not calibrated balance.

## Current cumulative factory access

| Completed local factory level | Guns available | Ammo available | Total offers |
|---|---:|---:|---:|
| Starter | 1 | 1 | 2 |
| 1 | 8 | 6 | 14 |
| 2 | 16 | 9 | 25 |
| 3 | 33 | 11 | 44 |
| 4 | 46 | 15 | 61 |
| 5 | 52 | 17 | 69 |

A reward does not require purchasing its gun's factory tier. The tiers above control shop access only; loot weighting uses them as a rarity input. Existing AK-47 access remains the grandfathered L1 factory gate, even though the unchanged 041 proposal classified it at L2.

## Exact new pool budgets

| Monument | Entries | Treasury/materials | Ammo only | Ordinary gun + ammo | Very rare gun + ammo |
|---|---:|---:|---:|---:|---:|
| Industrial Complex | 42 | 75.000% | 20.000% | 4.900% | 0.100% |
| Military Depot | 36 | 40.000% | 30.000% | 29.500% | 0.500% |
| Refinery | 37 | 75.000% | 20.000% | 4.900% | 0.100% |

There are 115 entries across all three tables: nine national bundles, 51 ammo-only entries, 52 ordinary gun bundles and three rare gun bundles. Ordinary gun categories retain variety: 12 pistols, four SMGs, six shotguns, 18 rifles, four DMRs, five snipers and three machine guns. Industrial holds 21 ordinary guns, Military 15, Refinery 16. No SBW gun, gun ammo, throwable explosive, portable vehicle item or vehicle entitlement occurs in these active pools.

Gun weights use scores 12/8/4/2/1 for starter–L1/L2/L3/L4/L5 and Hamilton integer allocation within each fixed group budget, with full native ID breaking remainder ties. Ammo-only tickets divide equally across 17 identities with the same tie rule. The group probabilities and national bundles are unchanged from 043; removed SBW shares redistribute among the remaining TaCZ entries in that group. Individual odds therefore change only for new 044 activations.

## Ordinary gun bundles

Each chance below is per capture of that monument, not conditional on another roll.

| TaCZ native gun | Minimum shop level | Monument | Tickets | Chance | Ammo identity |
|---|---:|---|---:|---:|---|
| `tacz:ak47` | 1 | Industrial | 735 | 0.735% | `tacz:762x39` |
| `tacz:ak74m` | 2 | Industrial | 490 | 0.490% | `tacz:545x39` |
| `tacz:m16a1` | 2 | Industrial | 490 | 0.490% | `tacz:556x45` |
| `tacz:ak12` | 3 | Industrial | 245 | 0.245% | `tacz:545x39` |
| `tacz:aug` | 3 | Industrial | 245 | 0.245% | `tacz:556x45` |
| `tacz:g36k` | 3 | Industrial | 245 | 0.245% | `tacz:556x45` |
| `tacz:m16a4` | 3 | Industrial | 245 | 0.245% | `tacz:556x45` |
| `tacz:m4a1` | 3 | Industrial | 245 | 0.245% | `tacz:556x45` |
| `tacz:qr_24` | 3 | Industrial | 245 | 0.245% | `tacz:545x39` |
| `tacz:rpk` | 3 | Industrial | 245 | 0.245% | `tacz:762x39` |
| `tacz:scar_l` | 3 | Industrial | 245 | 0.245% | `tacz:556x45` |
| `tacz:type_81` | 3 | Industrial | 245 | 0.245% | `tacz:762x39` |
| `tacz:fn_fal` | 4 | Industrial | 123 | 0.123% | `tacz:308` |
| `tacz:hk_g3` | 4 | Industrial | 123 | 0.123% | `tacz:308` |
| `tacz:m249` | 4 | Industrial | 123 | 0.123% | `tacz:556x45` |
| `tacz:mk18` | 4 | Industrial | 123 | 0.123% | `tacz:556x45` |
| `tacz:qbz_191` | 4 | Industrial | 122 | 0.122% | `tacz:58x42` |
| `tacz:qbz_95` | 4 | Industrial | 122 | 0.122% | `tacz:58x42` |
| `tacz:urgi` | 4 | Industrial | 122 | 0.122% | `tacz:556x45` |
| `tacz:hk416d` | 5 | Industrial | 61 | 0.061% | `tacz:556x45` |
| `tacz:pkm` | 5 | Industrial | 61 | 0.061% | `tacz:762x54` |
| `tacz:db_long` | 1 | Military | 3848 | 3.848% | `tacz:12g` |
| `tacz:db_short` | 1 | Military | 3848 | 3.848% | `tacz:12g` |
| `tacz:m1866` | 1 | Military | 3848 | 3.848% | `tacz:45acp` |
| `tacz:springfield1873` | 1 | Military | 3848 | 3.848% | `tacz:45_70` |
| `tacz:kar98` | 2 | Military | 2565 | 2.565% | `tacz:792x57` |
| `tacz:m1897` | 2 | Military | 2565 | 2.565% | `tacz:12g` |
| `tacz:sks_tactical` | 2 | Military | 2565 | 2.565% | `tacz:762x39` |
| `tacz:m700` | 3 | Military | 1283 | 1.283% | `tacz:30_06` |
| `tacz:m870` | 3 | Military | 1283 | 1.283% | `tacz:12g` |
| `tacz:spr15hb` | 3 | Military | 1282 | 1.282% | `tacz:556x45` |
| `tacz:m1014` | 4 | Military | 641 | 0.641% | `tacz:12g` |
| `tacz:mk14` | 4 | Military | 641 | 0.641% | `tacz:308` |
| `tacz:svd` | 4 | Military | 641 | 0.641% | `tacz:762x54` |
| `tacz:ai_awp` | 5 | Military | 321 | 0.321% | `tacz:338` |
| `tacz:spas_12` | 5 | Military | 321 | 0.321% | `tacz:12g` |
| `tacz:glock_17` | 0 | Refinery | 668 | 0.668% | `tacz:9mm` |
| `tacz:m1911` | 1 | Refinery | 668 | 0.668% | `tacz:45acp` |
| `tacz:taurus943` | 1 | Refinery | 668 | 0.668% | `tacz:22wmr` |
| `tacz:m9a4` | 2 | Refinery | 446 | 0.446% | `tacz:9mm` |
| `tacz:tec_9` | 2 | Refinery | 445 | 0.445% | `tacz:9mm` |
| `tacz:uzi` | 2 | Refinery | 445 | 0.445% | `tacz:9mm` |
| `tacz:cz75` | 3 | Refinery | 223 | 0.223% | `tacz:9mm` |
| `tacz:hk_mk23` | 3 | Refinery | 223 | 0.223% | `tacz:45acp` |
| `tacz:hk_mp5a5` | 3 | Refinery | 223 | 0.223% | `tacz:9mm` |
| `tacz:p320` | 3 | Refinery | 223 | 0.223% | `tacz:45acp` |
| `tacz:rhino357` | 3 | Refinery | 223 | 0.223% | `tacz:357mag` |
| `tacz:b93r` | 4 | Refinery | 111 | 0.111% | `tacz:9mm` |
| `tacz:deagle` | 4 | Refinery | 111 | 0.111% | `tacz:50ae` |
| `tacz:ump45` | 4 | Refinery | 111 | 0.111% | `tacz:45acp` |
| `tacz:hk_mp7a1` | 5 | Refinery | 56 | 0.056% | `tacz:46x30` |
| `tacz:timeless50` | 5 | Refinery | 56 | 0.056% | `tacz:50ae` |

## Ammo-only outcomes

| TaCZ ammo identity | Bundle count | Minimum shop level | Industrial tickets/chance | Military tickets/chance | Refinery tickets/chance |
|---|---:|---:|---:|---:|---:|
| `tacz:12g` | 32 | 1 | 1177 / 1.177% | 1765 / 1.765% | 1177 / 1.177% |
| `tacz:22wmr` | 32 | 1 | 1177 / 1.177% | 1765 / 1.765% | 1177 / 1.177% |
| `tacz:308` | 32 | 4 | 1177 / 1.177% | 1765 / 1.765% | 1177 / 1.177% |
| `tacz:30_06` | 32 | 3 | 1177 / 1.177% | 1765 / 1.765% | 1177 / 1.177% |
| `tacz:338` | 30 | 5 | 1177 / 1.177% | 1765 / 1.765% | 1177 / 1.177% |
| `tacz:357mag` | 32 | 3 | 1177 / 1.177% | 1765 / 1.765% | 1177 / 1.177% |
| `tacz:45_70` | 32 | 1 | 1177 / 1.177% | 1765 / 1.765% | 1177 / 1.177% |
| `tacz:45acp` | 32 | 1 | 1177 / 1.177% | 1765 / 1.765% | 1177 / 1.177% |
| `tacz:46x30` | 32 | 5 | 1176 / 1.176% | 1765 / 1.765% | 1176 / 1.176% |
| `tacz:50ae` | 32 | 4 | 1176 / 1.176% | 1765 / 1.765% | 1176 / 1.176% |
| `tacz:545x39` | 32 | 2 | 1176 / 1.176% | 1765 / 1.765% | 1176 / 1.176% |
| `tacz:556x45` | 32 | 2 | 1176 / 1.176% | 1765 / 1.765% | 1176 / 1.176% |
| `tacz:58x42` | 32 | 4 | 1176 / 1.176% | 1764 / 1.764% | 1176 / 1.176% |
| `tacz:762x39` | 32 | 1 | 1176 / 1.176% | 1764 / 1.764% | 1176 / 1.176% |
| `tacz:762x54` | 32 | 4 | 1176 / 1.176% | 1764 / 1.764% | 1176 / 1.176% |
| `tacz:792x57` | 32 | 2 | 1176 / 1.176% | 1764 / 1.764% | 1176 / 1.176% |
| `tacz:9mm` | 32 | 0 | 1176 / 1.176% | 1764 / 1.764% | 1176 / 1.176% |

## Reserved rare gun bundles

| Monument | TaCZ gun | Tickets/chance | Compatible ammunition |
|---|---|---:|---|
| Industrial Complex | `tacz:scar_h` | 100 / 0.100% | `tacz:308` ×32 |
| Military Depot | `tacz:scar_mk20` | 500 / 0.500% | `tacz:308` ×32 |
| Refinery | `tacz:deagle_golden` | 100 / 0.100% | `tacz:357mag` ×32 |

These three guns remain excluded from the shop. Other DROP/HOLD/special-qualification reservations stay excluded from active supply; this correction does not enable new weapons or native behavior.

## National outcomes retained

| Monument | Tickets/chance | Outcome |
|---|---:|---|
| Industrial Complex | 20,000 / 20.000% | $4,000 |
| Industrial Complex | 35,000 / 35.000% | 60 Steel + 80 Concrete |
| Industrial Complex | 20,000 / 20.000% | 20 Steel + 30 Mechanical Parts + 8 Electronics |
| Military Depot | 10,000 / 10.000% | $5,000 |
| Military Depot | 20,000 / 20.000% | 40 Steel + 60 Fabric + 30 Explosives |
| Military Depot | 10,000 / 10.000% | 20 Steel + 20 Mechanical Parts + 10 Electronics |
| Refinery | 15,000 / 15.000% | $5,000 |
| Refinery | 45,000 / 45.000% | 60 Oil + 100 Fuel |
| Refinery | 15,000 / 15.000% | 40 Fuel + 10 Mechanical Parts + 20 Electronics |

“Explosives” in the national stockpile is the existing abstract production resource; it does not grant an SBW grenade, launcher or explosive item. Expected treasury remains $800/$500/$750 per Industrial/Military/Refinery capture; this arithmetic does not value equipment or establish balance.

## Preservation and evidence boundary

Existing 042 prices/reference JSON and 043 pool code remain unchanged. No installed mod, owner inventory/world, accepted City A construction or historical transaction is edited by catalog generation. Existing frozen SBW entries remain visible to recovery/audit rather than being deleted or rebound to a TaCZ item. Current acquisition and gameplay policy must block their use/delivery, including alternate routes covered by the main 044 integration.

Static source-based arithmetic independently checks 52 guns, 17 ammunition identities, the 69 cumulative offers, 72 reward identities, 42/36/37 entry counts and 100,000 tickets per pool. Native build/tests and the final checkpoint are owned by the parent runner; this catalog document alone claims no native test pass, owner acceptance or final balance. SEC-01 remains **UNRESOLVED / HIGH PRIORITY**.
