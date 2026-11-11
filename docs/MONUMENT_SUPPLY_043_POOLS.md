# MON-01 / ECO-03: provisional monument supply pools 043

These explicit playtest pools replace the fixed payout for newly prepared production monument rounds. They do not add equipment on top of the old guaranteed treasury/material reward. Existing frozen rounds keep their original table, roll, prices where applicable, and receipts. Native claims impose no purchase charge or factory-tier gate; equipment identity and native configuration remain fully qualified. All numbers below are provisional, not owner combat-balance or installed-modpack acceptance.

Each table has exactly **100,000 integer tickets**. One capture chooses exactly one entry once; that resolved bundle is saved before a claim can deliver it. The entry probability is `weight / 100000`. There is no pity counter, reroll, vehicle, portable vehicle item, loaded gun, enchantment/perk roll or extra attachment grant. Guns retain only their qualified native defaults.

## Coverage and rarity

All 97 ordinary 042 identities are reused with exact native amounts: 76 one-gun identities and 21 ammo identities. Every ordinary gun appears in one role-specific table; every ammo-only entry appears in all three tables. Each gun result additionally includes one stack of its exact compatible ordinary ammunition. TaCZ `.338` is **30 rounds**, all other active ammo bundles are **32**; all guns are **one**.

Only three of the 28 DROP-reserved guns are enabled for this reward catalog: SCAR-H, SCAR Mk20 and Golden Desert Eagle. The remaining **25 DROP reservations**, both HOLD entries, internal/unknown IDs and all vehicles remain excluded. The three additions remain forbidden to every shop path. HK416D is already an ordinary L5 SHOP entry and is not reclassified.

| Monument | Entries | Treasury | Material bundles | Ammo-only | Ordinary gun + ammo | Very rare gun + ammo |
|---|---:|---:|---:|---:|---:|---:|
| Industrial Complex | 57 | 20.000% | 55.000% | 20.000% | 4.900% | 0.100% |
| Military Depot | 47 | 10.000% | 30.000% | 30.000% | 29.500% | 0.500% |
| Refinery | 47 | 15.000% | 60.000% | 20.000% | 4.900% | 0.100% |

Ordinary gun ticket shares use the actual 042 minimum factory tier only as a rarity input: starter/L1 score **12**, L2 **8**, L3 **4**, L4 **2**, L5 **1**. A reward claim does not require that factory. Each table allocates its fixed ordinary-gun budget in proportion to these scores; floor each share, then award remaining tickets by largest fractional remainder, breaking ties by full native ID. Ammo-only entries divide their fixed budget equally with the same deterministic remainder rule. The exact resulting integer weights appear below, so changing counts or catalogue membership cannot silently change odds.

## Treasury/material entries and exact expected credits

| Monument | Entry | Weight | Chance | Bundle |
|---|---|---:|---:|---|
| Industrial Complex | `treasury` | 20,000 | 20.000% | $4,000 treasury |
| Industrial Complex | `construction_materials` | 35,000 | 35.000% | 60 Steel, 80 Concrete |
| Industrial Complex | `industrial_components` | 20,000 | 20.000% | 20 Steel, 30 Mechanical Parts, 8 Electronics |
| Military Depot | `treasury` | 10,000 | 10.000% | $5,000 treasury |
| Military Depot | `field_supplies` | 20,000 | 20.000% | 40 Steel, 60 Fabric, 30 Explosives |
| Military Depot | `military_components` | 10,000 | 10.000% | 20 Steel, 20 Mechanical Parts, 10 Electronics |
| Refinery | `treasury` | 15,000 | 15.000% | $5,000 treasury |
| Refinery | `fuel_supplies` | 45,000 | 45.000% | 60 Oil, 100 Fuel |
| Refinery | `refinery_components` | 15,000 | 15.000% | 40 Fuel, 10 Mechanical Parts, 20 Electronics |

| Monument | Expected treasury/capture | Expected material credits/capture | Old fixed baseline |
|---|---:|---|---|
| Industrial Complex | $800 | Steel 25, Concrete 28, Mechanical Parts 6, Electronics 1.6 | $1,000; Steel 40, Concrete 40, Mechanical Parts 15 |
| Military Depot | $500 | Steel 10, Fabric 12, Explosives 6, Mechanical Parts 2, Electronics 1 | $1,500; Steel 25, Fabric 30, Explosives 20 |
| Refinery | $750 | Oil 27, Fuel 51, Mechanical Parts 1.5, Electronics 3 | $1,000; Oil 40, Fuel 60, Electronics 8 |

Expected credits are exact arithmetic, not simulated combat value. Equipment has not been converted to a money equivalent: it is neither a cash payout nor an assumption that shop resource costs can be freely converted. Expected gun counts per capture are 0.050 / 0.300 / 0.050 (Industrial / Military / Refinery). Ammo-stack counts are 0.250 / 0.600 / 0.250 because every gun bundle includes one compatible stack. Very rare average waiting times are 1,000 / 200 / 1,000 captures of the corresponding monument, without a guarantee or pity system.

## Ammo-only entries: exact odds

The same exact entitlement is also used as the compatible ammo component of gun bundles. This table lists only the independent ammo-only roll chances.

| Native ammo ID | Amount | Industrial weight / chance | Military weight / chance | Refinery weight / chance |
|---|---:|---:|---:|---:|
| `superbwarfare:handgun_ammo` | 32 | 953 / 0.953% | 1,429 / 1.429% | 953 / 0.953% |
| `superbwarfare:rifle_ammo` | 32 | 953 / 0.953% | 1,429 / 1.429% | 953 / 0.953% |
| `superbwarfare:shotgun_ammo` | 32 | 953 / 0.953% | 1,429 / 1.429% | 953 / 0.953% |
| `superbwarfare:sniper_ammo` | 32 | 953 / 0.953% | 1,429 / 1.429% | 953 / 0.953% |
| `tacz:12g` | 32 | 953 / 0.953% | 1,429 / 1.429% | 953 / 0.953% |
| `tacz:22wmr` | 32 | 953 / 0.953% | 1,429 / 1.429% | 953 / 0.953% |
| `tacz:308` | 32 | 953 / 0.953% | 1,429 / 1.429% | 953 / 0.953% |
| `tacz:30_06` | 32 | 953 / 0.953% | 1,429 / 1.429% | 953 / 0.953% |
| `tacz:338` | 30 | 952 / 0.952% | 1,429 / 1.429% | 952 / 0.952% |
| `tacz:357mag` | 32 | 952 / 0.952% | 1,429 / 1.429% | 952 / 0.952% |
| `tacz:45_70` | 32 | 952 / 0.952% | 1,429 / 1.429% | 952 / 0.952% |
| `tacz:45acp` | 32 | 952 / 0.952% | 1,429 / 1.429% | 952 / 0.952% |
| `tacz:46x30` | 32 | 952 / 0.952% | 1,428 / 1.428% | 952 / 0.952% |
| `tacz:50ae` | 32 | 952 / 0.952% | 1,428 / 1.428% | 952 / 0.952% |
| `tacz:545x39` | 32 | 952 / 0.952% | 1,428 / 1.428% | 952 / 0.952% |
| `tacz:556x45` | 32 | 952 / 0.952% | 1,428 / 1.428% | 952 / 0.952% |
| `tacz:58x42` | 32 | 952 / 0.952% | 1,428 / 1.428% | 952 / 0.952% |
| `tacz:762x39` | 32 | 952 / 0.952% | 1,428 / 1.428% | 952 / 0.952% |
| `tacz:762x54` | 32 | 952 / 0.952% | 1,428 / 1.428% | 952 / 0.952% |
| `tacz:792x57` | 32 | 952 / 0.952% | 1,428 / 1.428% | 952 / 0.952% |
| `tacz:9mm` | 32 | 952 / 0.952% | 1,428 / 1.428% | 952 / 0.952% |

## Ordinary gun entries: exact odds

Every row grants the named unloaded gun ×1 plus the listed compatible ammo stack. These are the same 042 native identities, modes, counts and qualification checks; purchase prices are not used in reward settlement.

| Monument | Native gun ID | Rarity tier | Weight | Chance | Included ammunition |
|---|---|---:|---:|---:|---|
| Industrial Complex | `superbwarfare:ak_12` | L4 | 82 | 0.082% | `superbwarfare:rifle_ammo` ×32 |
| Industrial Complex | `superbwarfare:ak_47` | L2 | 327 | 0.327% | `superbwarfare:rifle_ammo` ×32 |
| Industrial Complex | `superbwarfare:hk_416` | L5 | 41 | 0.041% | `superbwarfare:rifle_ammo` ×32 |
| Industrial Complex | `superbwarfare:m_4` | L3 | 163 | 0.163% | `superbwarfare:rifle_ammo` ×32 |
| Industrial Complex | `superbwarfare:m_60` | L4 | 82 | 0.082% | `superbwarfare:rifle_ammo` ×32 |
| Industrial Complex | `superbwarfare:marlin` | L3 | 163 | 0.163% | `superbwarfare:rifle_ammo` ×32 |
| Industrial Complex | `superbwarfare:mk_14` | L5 | 41 | 0.041% | `superbwarfare:rifle_ammo` ×32 |
| Industrial Complex | `superbwarfare:qbz_191` | L4 | 82 | 0.082% | `superbwarfare:rifle_ammo` ×32 |
| Industrial Complex | `superbwarfare:qbz_95` | L3 | 163 | 0.163% | `superbwarfare:rifle_ammo` ×32 |
| Industrial Complex | `superbwarfare:rpk` | L3 | 163 | 0.163% | `superbwarfare:rifle_ammo` ×32 |
| Industrial Complex | `superbwarfare:sks` | L2 | 327 | 0.327% | `superbwarfare:rifle_ammo` ×32 |
| Industrial Complex | `tacz:ak12` | L3 | 163 | 0.163% | `tacz:545x39` ×32 |
| Industrial Complex | `tacz:ak47` | L1 | 490 | 0.490% | `tacz:762x39` ×32 |
| Industrial Complex | `tacz:ak74m` | L2 | 327 | 0.327% | `tacz:545x39` ×32 |
| Industrial Complex | `tacz:aug` | L3 | 163 | 0.163% | `tacz:556x45` ×32 |
| Industrial Complex | `tacz:fn_fal` | L4 | 82 | 0.082% | `tacz:308` ×32 |
| Industrial Complex | `tacz:g36k` | L3 | 163 | 0.163% | `tacz:556x45` ×32 |
| Industrial Complex | `tacz:hk416d` | L5 | 41 | 0.041% | `tacz:556x45` ×32 |
| Industrial Complex | `tacz:hk_g3` | L4 | 82 | 0.082% | `tacz:308` ×32 |
| Industrial Complex | `tacz:m16a1` | L2 | 327 | 0.327% | `tacz:556x45` ×32 |
| Industrial Complex | `tacz:m16a4` | L3 | 163 | 0.163% | `tacz:556x45` ×32 |
| Industrial Complex | `tacz:m249` | L4 | 82 | 0.082% | `tacz:556x45` ×32 |
| Industrial Complex | `tacz:m4a1` | L3 | 163 | 0.163% | `tacz:556x45` ×32 |
| Industrial Complex | `tacz:mk18` | L4 | 82 | 0.082% | `tacz:556x45` ×32 |
| Industrial Complex | `tacz:pkm` | L5 | 41 | 0.041% | `tacz:762x54` ×32 |
| Industrial Complex | `tacz:qbz_191` | L4 | 82 | 0.082% | `tacz:58x42` ×32 |
| Industrial Complex | `tacz:qbz_95` | L4 | 82 | 0.082% | `tacz:58x42` ×32 |
| Industrial Complex | `tacz:qr_24` | L3 | 163 | 0.163% | `tacz:545x39` ×32 |
| Industrial Complex | `tacz:rpk` | L3 | 163 | 0.163% | `tacz:762x39` ×32 |
| Industrial Complex | `tacz:scar_l` | L3 | 163 | 0.163% | `tacz:556x45` ×32 |
| Industrial Complex | `tacz:type_81` | L3 | 163 | 0.163% | `tacz:762x39` ×32 |
| Industrial Complex | `tacz:urgi` | L4 | 81 | 0.081% | `tacz:556x45` ×32 |
| Military Depot | `superbwarfare:awm` | L5 | 225 | 0.225% | `superbwarfare:sniper_ammo` ×32 |
| Military Depot | `superbwarfare:homemade_shotgun` | L1 | 2,702 | 2.702% | `superbwarfare:shotgun_ammo` ×32 |
| Military Depot | `superbwarfare:k_98` | L2 | 1,802 | 1.802% | `superbwarfare:sniper_ammo` ×32 |
| Military Depot | `superbwarfare:m_870` | L3 | 901 | 0.901% | `superbwarfare:shotgun_ammo` ×32 |
| Military Depot | `superbwarfare:m_98b` | L4 | 450 | 0.450% | `superbwarfare:sniper_ammo` ×32 |
| Military Depot | `superbwarfare:mosin_nagant` | L2 | 1,802 | 1.802% | `superbwarfare:sniper_ammo` ×32 |
| Military Depot | `superbwarfare:svd` | L3 | 901 | 0.901% | `superbwarfare:sniper_ammo` ×32 |
| Military Depot | `tacz:ai_awp` | L5 | 225 | 0.225% | `tacz:338` ×30 |
| Military Depot | `tacz:db_long` | L1 | 2,702 | 2.702% | `tacz:12g` ×32 |
| Military Depot | `tacz:db_short` | L1 | 2,702 | 2.702% | `tacz:12g` ×32 |
| Military Depot | `tacz:kar98` | L2 | 1,802 | 1.802% | `tacz:792x57` ×32 |
| Military Depot | `tacz:m1014` | L4 | 450 | 0.450% | `tacz:12g` ×32 |
| Military Depot | `tacz:m1866` | L1 | 2,702 | 2.702% | `tacz:45acp` ×32 |
| Military Depot | `tacz:m1897` | L2 | 1,802 | 1.802% | `tacz:12g` ×32 |
| Military Depot | `tacz:m700` | L3 | 901 | 0.901% | `tacz:30_06` ×32 |
| Military Depot | `tacz:m870` | L3 | 901 | 0.901% | `tacz:12g` ×32 |
| Military Depot | `tacz:mk14` | L4 | 450 | 0.450% | `tacz:308` ×32 |
| Military Depot | `tacz:sks_tactical` | L2 | 1,802 | 1.802% | `tacz:762x39` ×32 |
| Military Depot | `tacz:spas_12` | L5 | 225 | 0.225% | `tacz:12g` ×32 |
| Military Depot | `tacz:spr15hb` | L3 | 901 | 0.901% | `tacz:556x45` ×32 |
| Military Depot | `tacz:springfield1873` | L1 | 2,702 | 2.702% | `tacz:45_70` ×32 |
| Military Depot | `tacz:svd` | L4 | 450 | 0.450% | `tacz:762x54` ×32 |
| Refinery | `superbwarfare:glock_17` | L1 | 463 | 0.463% | `superbwarfare:handgun_ammo` ×32 |
| Refinery | `superbwarfare:glock_18` | L4 | 77 | 0.077% | `superbwarfare:handgun_ammo` ×32 |
| Refinery | `superbwarfare:m_1911` | L2 | 309 | 0.309% | `superbwarfare:handgun_ammo` ×32 |
| Refinery | `superbwarfare:mp_443` | L1 | 463 | 0.463% | `superbwarfare:handgun_ammo` ×32 |
| Refinery | `superbwarfare:mp_5` | L3 | 154 | 0.154% | `superbwarfare:handgun_ammo` ×32 |
| Refinery | `superbwarfare:vector` | L5 | 39 | 0.039% | `superbwarfare:handgun_ammo` ×32 |
| Refinery | `tacz:b93r` | L4 | 77 | 0.077% | `tacz:9mm` ×32 |
| Refinery | `tacz:cz75` | L3 | 154 | 0.154% | `tacz:9mm` ×32 |
| Refinery | `tacz:deagle` | L4 | 77 | 0.077% | `tacz:50ae` ×32 |
| Refinery | `tacz:glock_17` | Starter | 463 | 0.463% | `tacz:9mm` ×32 |
| Refinery | `tacz:hk_mk23` | L3 | 154 | 0.154% | `tacz:45acp` ×32 |
| Refinery | `tacz:hk_mp5a5` | L3 | 154 | 0.154% | `tacz:9mm` ×32 |
| Refinery | `tacz:hk_mp7a1` | L5 | 39 | 0.039% | `tacz:46x30` ×32 |
| Refinery | `tacz:m1911` | L1 | 463 | 0.463% | `tacz:45acp` ×32 |
| Refinery | `tacz:m9a4` | L2 | 309 | 0.309% | `tacz:9mm` ×32 |
| Refinery | `tacz:p320` | L3 | 154 | 0.154% | `tacz:45acp` ×32 |
| Refinery | `tacz:rhino357` | L3 | 154 | 0.154% | `tacz:357mag` ×32 |
| Refinery | `tacz:taurus943` | L1 | 463 | 0.463% | `tacz:22wmr` ×32 |
| Refinery | `tacz:tec_9` | L2 | 309 | 0.309% | `tacz:9mm` ×32 |
| Refinery | `tacz:timeless50` | L5 | 39 | 0.039% | `tacz:50ae` ×32 |
| Refinery | `tacz:ump45` | L4 | 77 | 0.077% | `tacz:45acp` ×32 |
| Refinery | `tacz:uzi` | L2 | 309 | 0.309% | `tacz:9mm` ×32 |

## Three explicitly qualified very rare candidates

| Monument | Native DROP gun | Weight | Chance | Delivered mode / ammunition | Raw reference damage / RPM / magazine |
|---|---|---:|---:|---|---|
| Industrial Complex | `tacz:scar_h` ×1 | 100 | 0.100% | SEMI; `tacz:308` ×32 | 14 / 570 / 20 |
| Military Depot | `tacz:scar_mk20` ×1 | 500 | 0.500% | SEMI; `tacz:308` ×32 | 18 / 275 / 10 |
| Refinery | `tacz:deagle_golden` ×1 | 100 | 0.100% | SEMI; `tacz:357mag` ×32 | 12 / 350 / 9 |

All three use the existing `tacz:xmag_reload_logic`, ordinary `magazine` feed, a first declared `semi` mode, and existing shop-resupplied ammo. Their pinned data has explosion disabled, ignition false, and no charge, heat or fire-mode override. SCAR-H and SCAR Mk20 have 50% raw armor-ignore; Golden Desert Eagle has 20% and is a collectible reserve, not a claim that it outperforms the ordinary Deagle. These raw figures are not sustained DPS or native combat-balance measurements. Native 043 test results must separately verify construction/recheck of all three before declaring the runtime path validated.

## Frozen identity and native audit

- Equipment revision: `monument_supply43_native_v1`; table revisions: `monument_supply43_provisional_v1_<monument type id>`. Historical/synthetic/unknown equipment revisions and changed kind/ID/count fail closed; they are not upgraded to current entries.
- Public canonical factory: `NativeReward43.entitlement(kind, nativeId)`; `entitlements()` contains exactly 100 distinct identities, and `validate` checks exact equality. `MonumentSupply43.table(type)` creates 57/47/47 entries and verifies complete coverage. No table includes `VEHICLE`.
- Ordinary rewards route through unchanged `NativeWeapon42` stock qualification without invoking purchase/debit code. The rare adapter uses a private price-free native request after the exact three-ID DROP allowlist, then the same full raw and parsed TaCZ gun/ammo/default-attachment, script, config, unloaded-state, native-builder and serialization checks. Shop `requireCurrent` and `requireShop` gates remain independent.
- Claims store the exact supplied serialized item and fingerprint. Rechecks use that frozen supply, including its original SBW UUID. A resource/config change keeps the original claim pending; it never rolls a replacement item. Root claim operations hold the existing native transaction lease; quote/recheck use the same reload epoch/readiness checks.
- Ordinary 042 fingerprints gain no unconditional schema fields. TaCZ config paths remain `COMMON|SERVER:filename` with the same nested key layout and canonical numeric values, while values are read from effective `ForgeConfigSpec.ConfigValue.get()` so the runtime terrain-policy overlay is covered. Exact retained 042 supply recheck across the new runtime remains a required compatibility test.

Exact inputs:

| Input | Pin |
|---|---|
| 113-gun tier policy resource | SHA-256 `230d8a97e9b744902c97534009bb8b6089a7d89314010e23771755f3b205181a` |
| Full TaCZ native reference resource | SHA-256 `2c24dc08835a7489cd836bf05debc206e71545183098bcf217f59fb4fedb5b05` |
| SBW native reference resource | SHA-256 `058fbac6a7059a63aa514f7f59b246690a9f94867a9af67331cf679511b43c6b` |
| Official TaCZ source | `42af1be5bbe4127c083cfa19b57482f8aae23df2` |
| Published TaCZ Forge 1.20.1 version 1.2.0 | Modrinth `QGCbNkTy`; JAR SHA-256 `1c2f2915f76908d396a0da577665c977564c279ccb89d459a00a29225aa45a92` |
| Shared xmag script, CRLF normalized to LF | SHA-256 `39a584d6fe688ed8b45cae738bc006acbd1fdd875d2220fddfb0b3e71ab3c1c1` |

[Official pinned gunpack data](https://github.com/MCModderAnchor/TACZ/tree/42af1be5bbe4127c083cfa19b57482f8aae23df2/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz) supplies `scar_h_data`, `scar_mk20_data` and `deagle_golden_data`. The 042 audit already proved all 140 gun index/data files match the published 1.2.0 JAR after line-ending normalization. This 043 source audit reused those verified inputs and existing native qualification; it read no installed mods, authored world or live server. Vehicle rewards remain out of active pools pending a separate safe-bay contract and the unresolved SEC-01 retrieval boundary.

Implementation/source inspection and deterministic ticket arithmetic are separate from GameTest/native/owner acceptance. Root-owned validation records provide actual executed/pass/fail counts; no native tests were run while generating this pool specification.
