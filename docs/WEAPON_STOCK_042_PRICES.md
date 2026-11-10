# ECO-03 / ECO-02 — Weapon Stock 042 provisional prices

2026-11-10. **PROVISIONAL PLAYTEST PRICING — not final balance or installed-pack acceptance.** This schedule makes the authorized price-testing work concrete. It does not change the immutable 113-entry `weapon_tiers41.json` reference, activate conditional weapons, authorize loot, or reprice historical transactions.

`WeaponStock42.REVISION` is `physical_weapon_stock42_provisional_playtest_v1`. New offers carry this revision. The four exact existing offers retain `physical_weapon_shop40_existing_prices_v1`; their SKU, native ID, count, gate, mode and price are adapted directly from `WeaponShopCatalog40`. Saved 040 purchase intents and their recorded prices remain unchanged.

## Coverage and cumulative gates

There are **97 distinct products: 76 guns (52 TaCZ + 24 SBW) and 21 ammunition products (17 TaCZ + 4 SBW)**. The gun count includes the two existing TaCZ guns. The price revision adds 74 guns and 19 ammo products. All 28 DROP entries, both HOLD entries and all seven conditional/special SHOP entries remain excluded. No special, explosive-projectile, alternative-feed or energy ammunition is manufactured by this catalog.

Each new gun requires exactly its existing proposed tier. Unlocks are cumulative: a completed factory at L3 retains L1/L2 products. The existing TaCZ AK-47 alone is grandfathered at L1, while its unchanged reference proposal remains L2. Existing Glock and 9mm access remains L0. This catalog only describes gates; the physical counter must enforce current ownership, completed factory level, proximity, native qualification and quote validity.

| Minimum level | Newly available guns | Newly available ammo | Cumulative guns | Cumulative ammo | Cumulative products |
|---|---:|---:|---:|---:|---:|
| L0 | 1 | 1 | 1 | 1 | 2 |
| L1 | 10 | 7 | 11 | 8 | 19 |
| L2 | 13 | 5 | 24 | 13 | 37 |
| L3 | 24 | 2 | 48 | 15 | 63 |
| L4 | 18 | 4 | 66 | 19 | 85 |
| L5 | 10 | 2 | 76 | 21 | 97 |

New product IDs encode provider and kind (`tacz_gun_ak74m`, `superbwarfare_gun_ak_47`); they are not native IDs or aliases. A gun native ID is the full namespaced gun/config identity. An ammo native ID is the TaCZ ammo-pack identity or the actual SBW `AmmoSupplierItem` registry identity. Every product also records a qualified reference gun. Guns reference themselves; shared ammo references the lowest unlocked eligible consumer, breaking ties by full gun ID. Legacy AK-47 at L1 participates in this minimum.

Catalog construction is fail-closed on missing prices, changed coverage, unqualified categories/ammo, duplicate native products or mismatched policy gates. `requireCurrent` compares the complete known record, so a forged SKU, provider, native ID, reference gun, count, level, mode, price or revision cannot authorize a purchase. Pagination is zero-based, accepts page sizes 1–64 and rejects invalid/out-of-range pages; it grants no purchase authority.

## Explicit gun price matrix

The matrix is a reproducible starting hypothesis for playtesting, not a measured capability ranking. Use the same category/tier cell across providers to expose comparisons. The pinned allocation selects a cell; unused cells do not create additional gun eligibility. The legacy Glock and AK-47 bypass this new matrix and retain their exact historical prices.

| Category (SBW equivalent) | L1 money | L2 money | L3 money | L4 money | L5 money | Additional Steel |
|---|---:|---:|---:|---:|---:|---:|
| pistol / Handgun | $650 | $900 | $1,250 | $1,700 | $2,300 | 0 |
| smg / Smg | $1,100 | $1,500 | $2,100 | $2,900 | $3,900 | 3 |
| shotgun / Shotgun | $900 | $1,300 | $1,900 | $2,700 | $3,700 | 2 |
| rifle / Rifle | $1,250 | $1,800 | $2,500 | $3,400 | $4,600 | 4 |
| dmr | $1,200 | $1,700 | $2,400 | $3,300 | $4,500 | 4 |
| sniper / Sniper | $1,400 | $2,100 | $3,100 | $4,500 | $6,500 | 6 |
| mg / MachineGun | $1,800 | $2,600 | $3,600 | $4,900 | $6,500 | 10 |

Add the following tier materials, plus the category-specific additional Steel above. Zero entries are omitted from the charged resource map. No resource or money fallback is free.

| Tier | Wood | Base Steel | Mechanical Parts | Electronics |
|---|---:|---:|---:|---:|
| L1 | 8 | 6 | 0 | 0 |
| L2 | 8 | 10 | 0 | 0 |
| L3 | 6 | 16 | 3 | 0 |
| L4 | 4 | 24 | 6 | 2 |
| L5 | 0 | 34 | 10 | 4 |

## Every gun product

Each gun is **one unloaded native gun**, with its own exact provider identity. New TaCZ guns request the first declared fire mode in uppercase; the existing two retain their original modes. SBW offers use an empty mode selector, meaning preserve and verify the effective native initialized default rather than writing a forced reference mode. The immutable SBW `k_98` reference declares `Auto` but omits `AvailableFireModes`; native initialization defaults the available list to `[Semi]` and selects `Semi`. This discrepancy is explicit and does not authorize invented modes or rewriting the reference. Other SBW declared defaults retain their original case in the 041 metadata.

Legacy rows use revision 040; all other rows use revision 042. Their reference gun is identical to the full native gun ID in the table.

| Product SKU | Full native gun ID | Minimum level | Requested mode | Count | Exact price |
|---|---|---:|---|---:|---|
| `pistol` | `tacz:glock_17` | L0 | SEMI | 1 | $350 + 5 Wood + 5 Stone |
| `superbwarfare_gun_glock_17` | `superbwarfare:glock_17` | L1 | native default | 1 | $650 + 8 Wood + 6 Steel |
| `superbwarfare_gun_homemade_shotgun` | `superbwarfare:homemade_shotgun` | L1 | native default | 1 | $900 + 8 Wood + 8 Steel |
| `superbwarfare_gun_mp_443` | `superbwarfare:mp_443` | L1 | native default | 1 | $650 + 8 Wood + 6 Steel |
| `ak47` | `tacz:ak47` | L1 | AUTO | 1 | $1,200 + 12 Steel + 10 Wood |
| `tacz_gun_db_long` | `tacz:db_long` | L1 | SEMI | 1 | $900 + 8 Wood + 8 Steel |
| `tacz_gun_db_short` | `tacz:db_short` | L1 | BURST | 1 | $900 + 8 Wood + 8 Steel |
| `tacz_gun_m1866` | `tacz:m1866` | L1 | AUTO | 1 | $1,200 + 8 Wood + 10 Steel |
| `tacz_gun_m1911` | `tacz:m1911` | L1 | SEMI | 1 | $650 + 8 Wood + 6 Steel |
| `tacz_gun_springfield1873` | `tacz:springfield1873` | L1 | SEMI | 1 | $1,400 + 8 Wood + 12 Steel |
| `tacz_gun_taurus943` | `tacz:taurus943` | L1 | SEMI | 1 | $650 + 8 Wood + 6 Steel |
| `superbwarfare_gun_ak_47` | `superbwarfare:ak_47` | L2 | native default | 1 | $1,800 + 8 Wood + 14 Steel |
| `superbwarfare_gun_k_98` | `superbwarfare:k_98` | L2 | native default | 1 | $2,100 + 8 Wood + 16 Steel |
| `superbwarfare_gun_m_1911` | `superbwarfare:m_1911` | L2 | native default | 1 | $900 + 8 Wood + 10 Steel |
| `superbwarfare_gun_mosin_nagant` | `superbwarfare:mosin_nagant` | L2 | native default | 1 | $2,100 + 8 Wood + 16 Steel |
| `superbwarfare_gun_sks` | `superbwarfare:sks` | L2 | native default | 1 | $1,800 + 8 Wood + 14 Steel |
| `tacz_gun_ak74m` | `tacz:ak74m` | L2 | AUTO | 1 | $1,800 + 8 Wood + 14 Steel |
| `tacz_gun_kar98` | `tacz:kar98` | L2 | SEMI | 1 | $2,100 + 8 Wood + 16 Steel |
| `tacz_gun_m16a1` | `tacz:m16a1` | L2 | AUTO | 1 | $1,800 + 8 Wood + 14 Steel |
| `tacz_gun_m1897` | `tacz:m1897` | L2 | AUTO | 1 | $1,300 + 8 Wood + 12 Steel |
| `tacz_gun_m9a4` | `tacz:m9a4` | L2 | SEMI | 1 | $900 + 8 Wood + 10 Steel |
| `tacz_gun_sks_tactical` | `tacz:sks_tactical` | L2 | SEMI | 1 | $1,700 + 8 Wood + 14 Steel |
| `tacz_gun_tec_9` | `tacz:tec_9` | L2 | AUTO | 1 | $900 + 8 Wood + 10 Steel |
| `tacz_gun_uzi` | `tacz:uzi` | L2 | AUTO | 1 | $1,500 + 8 Wood + 13 Steel |
| `superbwarfare_gun_m_4` | `superbwarfare:m_4` | L3 | native default | 1 | $2,500 + 6 Wood + 20 Steel + 3 Mechanical Parts |
| `superbwarfare_gun_m_870` | `superbwarfare:m_870` | L3 | native default | 1 | $1,900 + 6 Wood + 18 Steel + 3 Mechanical Parts |
| `superbwarfare_gun_marlin` | `superbwarfare:marlin` | L3 | native default | 1 | $2,500 + 6 Wood + 20 Steel + 3 Mechanical Parts |
| `superbwarfare_gun_mp_5` | `superbwarfare:mp_5` | L3 | native default | 1 | $2,100 + 6 Wood + 19 Steel + 3 Mechanical Parts |
| `superbwarfare_gun_qbz_95` | `superbwarfare:qbz_95` | L3 | native default | 1 | $2,500 + 6 Wood + 20 Steel + 3 Mechanical Parts |
| `superbwarfare_gun_rpk` | `superbwarfare:rpk` | L3 | native default | 1 | $3,600 + 6 Wood + 26 Steel + 3 Mechanical Parts |
| `superbwarfare_gun_svd` | `superbwarfare:svd` | L3 | native default | 1 | $3,100 + 6 Wood + 22 Steel + 3 Mechanical Parts |
| `tacz_gun_ak12` | `tacz:ak12` | L3 | AUTO | 1 | $2,500 + 6 Wood + 20 Steel + 3 Mechanical Parts |
| `tacz_gun_aug` | `tacz:aug` | L3 | AUTO | 1 | $2,500 + 6 Wood + 20 Steel + 3 Mechanical Parts |
| `tacz_gun_cz75` | `tacz:cz75` | L3 | AUTO | 1 | $1,250 + 6 Wood + 16 Steel + 3 Mechanical Parts |
| `tacz_gun_g36k` | `tacz:g36k` | L3 | AUTO | 1 | $2,500 + 6 Wood + 20 Steel + 3 Mechanical Parts |
| `tacz_gun_hk_mk23` | `tacz:hk_mk23` | L3 | SEMI | 1 | $1,250 + 6 Wood + 16 Steel + 3 Mechanical Parts |
| `tacz_gun_hk_mp5a5` | `tacz:hk_mp5a5` | L3 | AUTO | 1 | $2,100 + 6 Wood + 19 Steel + 3 Mechanical Parts |
| `tacz_gun_m16a4` | `tacz:m16a4` | L3 | BURST | 1 | $2,500 + 6 Wood + 20 Steel + 3 Mechanical Parts |
| `tacz_gun_m4a1` | `tacz:m4a1` | L3 | AUTO | 1 | $2,500 + 6 Wood + 20 Steel + 3 Mechanical Parts |
| `tacz_gun_m700` | `tacz:m700` | L3 | SEMI | 1 | $3,100 + 6 Wood + 22 Steel + 3 Mechanical Parts |
| `tacz_gun_m870` | `tacz:m870` | L3 | SEMI | 1 | $1,900 + 6 Wood + 18 Steel + 3 Mechanical Parts |
| `tacz_gun_p320` | `tacz:p320` | L3 | SEMI | 1 | $1,250 + 6 Wood + 16 Steel + 3 Mechanical Parts |
| `tacz_gun_qr_24` | `tacz:qr_24` | L3 | AUTO | 1 | $2,500 + 6 Wood + 20 Steel + 3 Mechanical Parts |
| `tacz_gun_rhino357` | `tacz:rhino357` | L3 | SEMI | 1 | $1,250 + 6 Wood + 16 Steel + 3 Mechanical Parts |
| `tacz_gun_rpk` | `tacz:rpk` | L3 | AUTO | 1 | $3,600 + 6 Wood + 26 Steel + 3 Mechanical Parts |
| `tacz_gun_scar_l` | `tacz:scar_l` | L3 | AUTO | 1 | $2,500 + 6 Wood + 20 Steel + 3 Mechanical Parts |
| `tacz_gun_spr15hb` | `tacz:spr15hb` | L3 | SEMI | 1 | $2,400 + 6 Wood + 20 Steel + 3 Mechanical Parts |
| `tacz_gun_type_81` | `tacz:type_81` | L3 | AUTO | 1 | $2,500 + 6 Wood + 20 Steel + 3 Mechanical Parts |
| `superbwarfare_gun_ak_12` | `superbwarfare:ak_12` | L4 | native default | 1 | $3,400 + 4 Wood + 28 Steel + 6 Mechanical Parts + 2 Electronics |
| `superbwarfare_gun_glock_18` | `superbwarfare:glock_18` | L4 | native default | 1 | $1,700 + 4 Wood + 24 Steel + 6 Mechanical Parts + 2 Electronics |
| `superbwarfare_gun_m_60` | `superbwarfare:m_60` | L4 | native default | 1 | $4,900 + 4 Wood + 34 Steel + 6 Mechanical Parts + 2 Electronics |
| `superbwarfare_gun_m_98b` | `superbwarfare:m_98b` | L4 | native default | 1 | $4,500 + 4 Wood + 30 Steel + 6 Mechanical Parts + 2 Electronics |
| `superbwarfare_gun_qbz_191` | `superbwarfare:qbz_191` | L4 | native default | 1 | $3,400 + 4 Wood + 28 Steel + 6 Mechanical Parts + 2 Electronics |
| `tacz_gun_b93r` | `tacz:b93r` | L4 | BURST | 1 | $1,700 + 4 Wood + 24 Steel + 6 Mechanical Parts + 2 Electronics |
| `tacz_gun_deagle` | `tacz:deagle` | L4 | SEMI | 1 | $1,700 + 4 Wood + 24 Steel + 6 Mechanical Parts + 2 Electronics |
| `tacz_gun_fn_fal` | `tacz:fn_fal` | L4 | SEMI | 1 | $3,400 + 4 Wood + 28 Steel + 6 Mechanical Parts + 2 Electronics |
| `tacz_gun_hk_g3` | `tacz:hk_g3` | L4 | SEMI | 1 | $3,400 + 4 Wood + 28 Steel + 6 Mechanical Parts + 2 Electronics |
| `tacz_gun_m1014` | `tacz:m1014` | L4 | SEMI | 1 | $2,700 + 4 Wood + 26 Steel + 6 Mechanical Parts + 2 Electronics |
| `tacz_gun_m249` | `tacz:m249` | L4 | AUTO | 1 | $4,900 + 4 Wood + 34 Steel + 6 Mechanical Parts + 2 Electronics |
| `tacz_gun_mk14` | `tacz:mk14` | L4 | SEMI | 1 | $3,300 + 4 Wood + 28 Steel + 6 Mechanical Parts + 2 Electronics |
| `tacz_gun_mk18` | `tacz:mk18` | L4 | AUTO | 1 | $3,400 + 4 Wood + 28 Steel + 6 Mechanical Parts + 2 Electronics |
| `tacz_gun_qbz_191` | `tacz:qbz_191` | L4 | AUTO | 1 | $3,400 + 4 Wood + 28 Steel + 6 Mechanical Parts + 2 Electronics |
| `tacz_gun_qbz_95` | `tacz:qbz_95` | L4 | AUTO | 1 | $3,400 + 4 Wood + 28 Steel + 6 Mechanical Parts + 2 Electronics |
| `tacz_gun_svd` | `tacz:svd` | L4 | SEMI | 1 | $4,500 + 4 Wood + 30 Steel + 6 Mechanical Parts + 2 Electronics |
| `tacz_gun_ump45` | `tacz:ump45` | L4 | AUTO | 1 | $2,900 + 4 Wood + 27 Steel + 6 Mechanical Parts + 2 Electronics |
| `tacz_gun_urgi` | `tacz:urgi` | L4 | AUTO | 1 | $3,400 + 4 Wood + 28 Steel + 6 Mechanical Parts + 2 Electronics |
| `superbwarfare_gun_awm` | `superbwarfare:awm` | L5 | native default | 1 | $6,500 + 40 Steel + 10 Mechanical Parts + 4 Electronics |
| `superbwarfare_gun_hk_416` | `superbwarfare:hk_416` | L5 | native default | 1 | $4,600 + 38 Steel + 10 Mechanical Parts + 4 Electronics |
| `superbwarfare_gun_mk_14` | `superbwarfare:mk_14` | L5 | native default | 1 | $4,600 + 38 Steel + 10 Mechanical Parts + 4 Electronics |
| `superbwarfare_gun_vector` | `superbwarfare:vector` | L5 | native default | 1 | $3,900 + 37 Steel + 10 Mechanical Parts + 4 Electronics |
| `tacz_gun_ai_awp` | `tacz:ai_awp` | L5 | SEMI | 1 | $6,500 + 40 Steel + 10 Mechanical Parts + 4 Electronics |
| `tacz_gun_hk416d` | `tacz:hk416d` | L5 | AUTO | 1 | $4,600 + 38 Steel + 10 Mechanical Parts + 4 Electronics |
| `tacz_gun_hk_mp7a1` | `tacz:hk_mp7a1` | L5 | AUTO | 1 | $3,900 + 37 Steel + 10 Mechanical Parts + 4 Electronics |
| `tacz_gun_pkm` | `tacz:pkm` | L5 | AUTO | 1 | $6,500 + 44 Steel + 10 Mechanical Parts + 4 Electronics |
| `tacz_gun_spas_12` | `tacz:spas_12` | L5 | SEMI | 1 | $3,700 + 36 Steel + 10 Mechanical Parts + 4 Electronics |
| `tacz_gun_timeless50` | `tacz:timeless50` | L5 | SEMI | 1 | $2,300 + 34 Steel + 10 Mechanical Parts + 4 Electronics |

## Compatible ammunition products

All ammunition products contain **32 actual rounds**. TaCZ ammo identities come from each eligible gun's exact `ammo` JSON string. The four SBW mappings below come from the pinned Superb Warfare 0.8.9.2 source artifact, SHA-256 `06b4bc749e540879265e3f3829c6c242da81512b19a80de0fbd9b089847893d5`: `ModItems.kt` registers each as `AmmoSupplierItem(type, 1, Item.Properties())`, so one native item supplies one round. Ordinary native stack size is 64; the delivery provider must still verify the effective item, count, ammo type, one-round unit and maximum stack size. These source facts do not substitute for effective installed native-data validation.

| SBW gun-data token | Actual registry ID | Native ammo type | Rounds per item |
|---|---|---|---:|
| `@HandgunAmmo` | `superbwarfare:handgun_ammo` | `HANDGUN` | 1 |
| `@RifleAmmo` | `superbwarfare:rifle_ammo` | `RIFLE` | 1 |
| `@ShotgunAmmo` | `superbwarfare:shotgun_ammo` | `SHOTGUN` | 1 |
| `@SniperAmmo` | `superbwarfare:sniper_ammo` | `SNIPER` | 1 |

TaCZ and SBW ammunition remains separate even when gun names or nominal calibers resemble one another. The following table is the entire ammo price schedule; ammo rows have an empty fire mode. `ammo9` and `ammo762` retain revision 040; all other rows use revision 042.

| Product SKU | Exact native ammo ID | Qualified reference gun | Minimum level | Count | Exact price |
|---|---|---|---:|---:|---|
| `ammo9` | `tacz:9mm` | `tacz:glock_17` | L0 | 32 | $100 + 5 Stone |
| `superbwarfare_ammo_handgun_ammo` | `superbwarfare:handgun_ammo` | `superbwarfare:glock_17` | L1 | 32 | $150 + 1 Steel + 1 Explosives |
| `superbwarfare_ammo_shotgun_ammo` | `superbwarfare:shotgun_ammo` | `superbwarfare:homemade_shotgun` | L1 | 32 | $220 + 2 Steel + 2 Explosives |
| `tacz_ammo_12g` | `tacz:12g` | `tacz:db_long` | L1 | 32 | $220 + 2 Steel + 2 Explosives |
| `tacz_ammo_22wmr` | `tacz:22wmr` | `tacz:taurus943` | L1 | 32 | $110 + 1 Steel + 1 Explosives |
| `tacz_ammo_45_70` | `tacz:45_70` | `tacz:springfield1873` | L1 | 32 | $240 + 3 Steel + 2 Explosives |
| `tacz_ammo_45acp` | `tacz:45acp` | `tacz:m1866` | L1 | 32 | $150 + 1 Steel + 1 Explosives |
| `ammo762` | `tacz:762x39` | `tacz:ak47` | L1 | 32 | $175 + 2 Steel + 1 Explosives |
| `superbwarfare_ammo_rifle_ammo` | `superbwarfare:rifle_ammo` | `superbwarfare:ak_47` | L2 | 32 | $210 + 2 Steel + 1 Explosives |
| `superbwarfare_ammo_sniper_ammo` | `superbwarfare:sniper_ammo` | `superbwarfare:k_98` | L2 | 32 | $300 + 3 Steel + 2 Explosives |
| `tacz_ammo_545x39` | `tacz:545x39` | `tacz:ak74m` | L2 | 32 | $200 + 2 Steel + 1 Explosives |
| `tacz_ammo_556x45` | `tacz:556x45` | `tacz:m16a1` | L2 | 32 | $210 + 2 Steel + 1 Explosives |
| `tacz_ammo_792x57` | `tacz:792x57` | `tacz:kar98` | L2 | 32 | $260 + 3 Steel + 2 Explosives |
| `tacz_ammo_30_06` | `tacz:30_06` | `tacz:m700` | L3 | 32 | $280 + 3 Steel + 2 Explosives |
| `tacz_ammo_357mag` | `tacz:357mag` | `tacz:rhino357` | L3 | 32 | $200 + 2 Steel + 1 Explosives |
| `tacz_ammo_308` | `tacz:308` | `tacz:fn_fal` | L4 | 32 | $280 + 3 Steel + 2 Explosives |
| `tacz_ammo_50ae` | `tacz:50ae` | `tacz:deagle` | L4 | 32 | $280 + 3 Steel + 2 Explosives |
| `tacz_ammo_58x42` | `tacz:58x42` | `tacz:qbz_191` | L4 | 32 | $240 + 3 Steel + 1 Explosives |
| `tacz_ammo_762x54` | `tacz:762x54` | `tacz:svd` | L4 | 32 | $300 + 3 Steel + 2 Explosives |
| `tacz_ammo_338` | `tacz:338` | `tacz:ai_awp` | L5 | 32 | $450 + 5 Steel + 3 Explosives |
| `tacz_ammo_46x30` | `tacz:46x30` | `tacz:hk_mp7a1` | L5 | 32 | $320 + 3 Steel + 2 Explosives |

## Price-testing interpretation

Every new ammo offer has a fixed, explicit cash/Steel/Explosives cost; its unlock is derived only from qualified ordinary consumers. Sharing ammunition does not grant access to an excluded gun. Some eligible guns share ammunition with DROP-reserved guns; this catalog sells no reserved gun, skin substitute, alternate feed or attachment package. The existing legacy 9mm price intentionally retains its Stone-only material cost.

For playtests, compare money and material affordability, useful engagements per 32-round bundle, replacement frequency, reload/manual-action behavior, armor interactions, hit rate and ordinary national production at each completed factory level. Compare same-role TaCZ/SBW choices together. A later numerical change must create a new price revision while historical intent recovery continues using the original recorded price and item.

This document records the deterministic provisional schedule and source evidence only. It does not claim combat calibration, economy acceptance, native delivery/restart acceptance or owner visual/modpack approval. Validation results belong in the current integration evidence and must distinguish synthetic policy checks from exact installed native tests.

References: [existing tier and loot plan](ECO03_WEAPON_TIERS_AND_MONUMENT_LOOT_PLAN.md), [immutable reference evidence](evidence/weapon_catalog_reference39.json), and [stock implementation](../src/main/java/com/quin/catancraft/world/WeaponStock42.java).
