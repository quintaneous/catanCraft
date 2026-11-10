# ECO-03 / ECO-02 / MON-01 — weapon tiers and monument supply drops

Owner-requested roadmap addition, 2026-11-10. **Planning and read-only catalog audit; no shop, prices, loot chances, recipes, runtime permissions or existing rewards were changed.** Continue the current Phase 2 gates/siege work. Deliver weapon tiering, physical procurement and randomized monument rewards together in the later Phase 3 economy/procurement update; use the existing ECO-03/ECO-02/MON-01 IDs rather than creating another master roadmap.

## Owner requirements

- Scan the available guns and assign a varied selection of weapon types across Weapons Factory levels, with stronger choices unlocked at higher levels.
- Keep the best guns out of the shop so special drops remain valuable.
- Test prices and ammunition costs; do not declare balance from the gun's name, nominal damage or an untested price list.
- Replace fixed monument payouts with supply-drop rewards drawn at different rarities. Guns, vehicles, ammunition and other useful supplies may appear; some rewards should be very rare.

These requirements are approved direction. **The assignments below are a first playtest allocation, not approved final balance.** Exact prices, weight percentages, quantity ranges, attachment/perk rules and the installed gun-pack revision remain to be reconciled before enabling the catalog.

## Verified current CatanCraft behavior

`BuildingInstance` stores factory levels 1–5. `BuildingType.WEAPONS_FACTORY` requires city level 2; ordinary building upgrades cannot exceed the city level. `NationProcurement.ownsFacility` currently checks only that some owned territory contains the factory type: it does not use factory level. The current six offers are:

| Current offer | Exact ID | Existing gate | Existing cost, recorded only |
|---|---|---|---|
| Glock 17 | `tacz:glock_17` | No factory | $350 + 5 Wood + 5 Stone |
| AK-47 | `tacz:ak47` | Any Weapons Factory | $1,200 + 12 Steel + 10 Wood |
| 9mm ×32 | `tacz:9mm` | No factory | $100 + 5 Stone |
| 7.62×39 ×32 | `tacz:762x39` | Any Weapons Factory | $175 + 2 Steel + 1 Explosives |
| Unarmed pickup | `superbwarfare:sodayo_pick_up` | Vehicle Factory | $4,000 + 20 Steel + 10 Mechanical Parts + 8 Fuel |
| LAV-150 | `superbwarfare:lav_150` | Vehicle Factory | $8,500 + 50 Steel + 25 Mechanical Parts + 20 Fuel + 5 Electronics |

These are existing code values, **not new recommendations or calibrated prices**. A proposal that moves AK-47 to L2 deliberately changes its current any-factory eligibility and must be included in the later balance release notes; it is not already implemented. Preserve historical purchases and starter access.

`MonumentManager.completeCapture` currently credits fixed national treasury and resource values from `MonumentType`, then clears the active monument and schedules the next activation. There is no random reward table, resolved loot receipt, claim record or vehicle reward delivery path.

| Monument | Current fixed treasury | Current fixed resources |
|---|---:|---|
| Industrial Complex | $1,000 | 40 Steel, 40 Concrete, 15 Mechanical Parts |
| Military Depot | $1,500 | 25 Steel, 30 Fabric, 20 Explosives |
| Refinery | $1,000 | 40 Oil, 60 Fuel, 8 Electronics |

The earlier Military Depot capture/resource acceptance remains historical evidence. It does not certify a future randomized payout or require the owner to repeat the accepted test now.

## Catalog actually scanned

The accessible workspace did not contain a TaCZ runtime JAR or the owner's complete custom gun-pack configuration. No installed mods or live world were read. To make concrete progress, this audit used **pinned official reference content**:

1. [Official TaCZ 1.20.1 source at `42af1be5bbe4127c083cfa19b57482f8aae23df2`](https://github.com/MCModderAnchor/TACZ/tree/42af1be5bbe4127c083cfa19b57482f8aae23df2/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz): all **70 gun index files and their 70 gun data files**, downloaded and parsed. Every downloaded byte stream was checked against its Git blob identity from the untruncated pinned tree. This is a source reference from 2026-11-08, not proof that these exact 70 guns or stats are installed on the owner's server. Other custom packs remain outside this scan.
2. [Published Superb Warfare 0.8.9.2 source artifact](https://cdn.modrinth.com/data/Cd3DYqzn/versions/81Iyv9ao/superbwarfare-0.8.9.2-mc1.20.1-65d985a64-sources.jar), 31,115,305 bytes, SHA-256 `06b4bc749e540879265e3f3829c6c242da81512b19a80de0fbd9b089847893d5`, matching the prior SEC-01 audit pin. All **43 configured gun definitions** under `data/superbwarfare/sbw/guns` were parsed and matched to `ModItems.kt` registrations. The two additional internal registrations, `superbwarfare:empty_gun` and `superbwarfare:vehicle_gun`, are excluded from ordinary shop/loot offers. This is the published reference artifact, not a read of installed mods.

Thus the tables cover **113 configured reference entries**, including SBW's repair utility; they are not a claim to have enumerated every installed add-on. A later read-only manifest/export of the exact test-pack gun IDs, pack load order and effective configuration must reconcile additions, removals and overrides before implementation. Do not upgrade the owner's mods to match this reference.

TaCZ raw fields below are `bullet.damage / rpm / ammo_amount`, plus `bullet.extra_damage.armor_ignore`. SBW fields are `Damage / RPM / Magazine`, plus `BypassesArmor`. “—” means the field is absent, **not zero or unlimited**. A listed RPM is not effective sustained rate for manual actions, burst scripts, charging, heat or special feeds. TaCZ's shotgun damage field is not directly equivalent to SBW's per-projectile field. These values support a first allocation; they are not cross-mod DPS, time-to-kill or best-weapon rankings.

## Recommended progression

Use cumulative unlocks: higher factories keep the cheaper lower-level selection. Give each new level useful role choices without forcing a weak duplicate into every type/level combination. Keep a distinct starter counter for the existing TaCZ Glock/9mm. Use the actual `BuildingInstance.level`, completed facility and authorized physical counter, not Town Hall level alone or decorative factory blocks.

| Factory tier | Intended additions |
|---|---|
| Starter, before factory | Existing Glock/9mm access through the future starter counter |
| L1 | Basic sidearms, double-barrel/low-capacity shotguns, slow single-shot or lever-action options |
| L2 | Early service rifles, entry SMGs, basic marksman/bolt-action choices |
| L3 | Broad standard-service selection: rifles, controlled SMGs, pump shotguns and first support weapons |
| L4 | Better precision/handling, semi-auto shotguns, stronger support weapons; limited explosive offers only after damage-policy testing |
| L5 | Best **shop** choices with remaining tradeoffs; the reserved top-end/special list stays unavailable for purchase |
| DROP | No shop offer at any level. Candidate special-drop pool, still subject to safety/balance tests |
| HOLD | Neither ordinary shop nor initial loot; unresolved utility/special behavior |

This is a shared progression across TaCZ/SBW, not permission to mix their same-named guns interchangeably. Keep full namespaced IDs and native ammo/attachment/perk data. For example, `tacz:ak47` and `superbwarfare:ak_47` are separate products. Resolve duplicate roles deliberately rather than hiding which implementation the player receives.

### Every TaCZ reference gun

The source's category is retained even where the design role differs. The final column highlights special risks or reserved candidates; all other rows remain provisional ordinary progression choices.

| Exact ID | Source type | Raw damage / RPM / capacity | Armor ignore | Proposed unlock | Reason / qualification |
|---|---|---|---|---|---|
| `tacz:aa12` | shotgun | 30 / 350 / 8 | 0.0 | DROP | Automatic shotgun; test spread, pellets and extended magazines. |
| `tacz:ai_awp` | sniper | 50 / 171 / 5 | 0.6 | L5 | Provisional role/handling comparison within this tier. |
| `tacz:ak12` | rifle | 7.5 / 660 / 30 | 0.25 | L3 | Provisional role/handling comparison within this tier. |
| `tacz:ak47` | rifle | 9 / 600 / 30 | 0.25 | L2 | Provisional role/handling comparison within this tier. |
| `tacz:ak74m` | rifle | 8 / 650 / 30 | 0.2 | L2 | Provisional role/handling comparison within this tier. |
| `tacz:aug` | rifle | 7 / 750 / 30 | 0.25 | L3 | Provisional role/handling comparison within this tier. |
| `tacz:b93r` | pistol | 7.5 / 900 / 20 | 0.2 | L4 | Provisional role/handling comparison within this tier. |
| `tacz:cz75` | pistol | 5 / 900 / 16 | 0.2 | L3 | Provisional role/handling comparison within this tier. |
| `tacz:db_long` | shotgun | 36 / 200 / 2 | 0.25 | L1 | Provisional role/handling comparison within this tier. |
| `tacz:db_short` | shotgun | 32 / 150 / 2 | 0.33 | L1 | Provisional role/handling comparison within this tier. |
| `tacz:deagle` | pistol | 16 / 300 / 7 | 0.25 | L4 | Provisional role/handling comparison within this tier. |
| `tacz:deagle_golden` | pistol | 12 / 350 / 9 | 0.2 | DROP | Collectible reserve; its base damage is LOWER than ordinary Deagle. |
| `tacz:fn_evolys` | mg | 12 / 750 / 75 | 0.4 | DROP | 750 RPM, 12 base damage, 75-round feed. |
| `tacz:fn_fal` | rifle | 13 / 350 / 20 | 0.4 | L4 | Provisional role/handling comparison within this tier. |
| `tacz:g36k` | rifle | 7 / 780 / 30 | 0.2 | L3 | Provisional role/handling comparison within this tier. |
| `tacz:glock_17` | pistol | 6 / 400 / 17 | 0 | Starter | Keep existing starter access through the later physical starter counter. |
| `tacz:hcar` | rifle | 18 / 350 / 20 | 0.4 | DROP | 18 base damage / 40% armor ignore; reserve heavy-rifle candidate. |
| `tacz:hk416d` | rifle | 7 / 943 / 30 | 0.2 | L5 | Provisional role/handling comparison within this tier. |
| `tacz:hk_g3` | rifle | 12 / 350 / 20 | 0.5 | L4 | Provisional role/handling comparison within this tier. |
| `tacz:hk_mk23` | pistol | 12 / 50 / 12 | 0.2 | L3 | Raw RPM 50; script/burst behavior needs runtime measurement. |
| `tacz:hk_mp5a5` | smg | 7 / 820 / 30 | 0.15 | L3 | Provisional role/handling comparison within this tier. |
| `tacz:hk_mp7a1` | smg | 6 / 950 / 20 | 0.65 | L5 | 65% armor ignore: candidate top shop PDW, subject to armored TTK tests. |
| `tacz:hm50b` | sniper | 70 / 180 / 10 | 0.6 | DROP | 70 base damage; high-caliber reserve. |
| `tacz:kar98` | sniper | 26 / 250 / 4 | 0.4 | L2 | Provisional role/handling comparison within this tier. |
| `tacz:lonetrail` | pistol | 21.5 / 90 / 1 | 0.4 | DROP | Special collectible; single-shot rifle-caliber pistol. |
| `tacz:m1014` | shotgun | 40 / 200 / 6 | 0.25 | L4 | Provisional role/handling comparison within this tier. |
| `tacz:m107` | sniper | 55 / 250 / 10 | 0.5 | DROP | 55 base damage, semi-automatic .50 BMG. |
| `tacz:m16a1` | rifle | 8 / 700 / 20 | 0.25 | L2 | Provisional role/handling comparison within this tier. |
| `tacz:m16a4` | rifle | 8 / 400 / 30 | 0.25 | L3 | Provisional role/handling comparison within this tier. |
| `tacz:m1866` | dmr | 12 / 350 / 5 | 0.0 | L1 | Source category DMR; manual action/5-round feed. |
| `tacz:m1897` | shotgun | 32 / 400 / 5 | 0.25 | L2 | Provisional role/handling comparison within this tier. |
| `tacz:m1911` | pistol | 11 / 350 / 7 | 0.2 | L1 | Provisional role/handling comparison within this tier. |
| `tacz:m249` | mg | 7.5 / 750 / 75 | 0.3 | L4 | Provisional role/handling comparison within this tier. |
| `tacz:m320` | rpg | 10 / 150 / 1 | 0.0 | L4 | Conditional: explosive/damage contract and ammo limits required. |
| `tacz:m4a1` | rifle | 7 / 800 / 30 | 0.2 | L3 | Provisional role/handling comparison within this tier. |
| `tacz:m700` | sniper | 24 / 180 / 5 | 0.5 | L3 | Provisional role/handling comparison within this tier. |
| `tacz:m870` | shotgun | 36 / 180 / 5 | 0.25 | L3 | Provisional role/handling comparison within this tier. |
| `tacz:m95` | sniper | 75 / 151 / 5 | 0.75 | DROP | 75 base damage / 75% armor ignore. |
| `tacz:m9a4` | pistol | 7 / 450 / 17 | 0.25 | L2 | Provisional role/handling comparison within this tier. |
| `tacz:mcx_spear_lt` | rifle | 8 / 1000 / 30 | 0.2 | DROP | 1000 RPM, 8 base damage; reserve strong automatic-rifle candidate. |
| `tacz:minigun` | mg | 8 / 1200 / — | 0.5 | DROP | 1200 RPM; special feed/heat/spin-up must be evaluated. |
| `tacz:mk14` | dmr | 16 / 300 / 10 | 0.5 | L4 | Provisional role/handling comparison within this tier. |
| `tacz:mk18` | rifle | 7.5 / 750 / 30 | 0.25 | L4 | Provisional role/handling comparison within this tier. |
| `tacz:mrad` | sniper | 45 / 151 / 10 | 0.75 | DROP | 45 base damage / 75% armor ignore. |
| `tacz:p320` | pistol | 10 / 450 / 12 | 0.2 | L3 | Provisional role/handling comparison within this tier. |
| `tacz:p90` | smg | 6 / 850 / 50 | 0.7 | DROP | 50-round capacity / 70% armor ignore. |
| `tacz:pkm` | mg | 13 / 650 / 75 | 0.45 | L5 | Provisional role/handling comparison within this tier. |
| `tacz:qbz_191` | rifle | 7.5 / 750 / 30 | 0.4 | L4 | Provisional role/handling comparison within this tier. |
| `tacz:qbz_95` | rifle | 7.5 / 660 / 30 | 0.4 | L4 | Provisional role/handling comparison within this tier. |
| `tacz:qr_24` | rifle | 7 / 880 / 32 | 0.1 | L3 | Provisional role/handling comparison within this tier. |
| `tacz:rhino357` | pistol | 15 / 200 / 6 | 0.3 | L3 | Provisional role/handling comparison within this tier. |
| `tacz:rpg7` | rpg | 20 / 150 / 1 | 0.0 | L5 | Conditional: explosive/damage contract and ammo limits required. |
| `tacz:rpk` | mg | 10 / 600 / 40 | 0.3 | L3 | Provisional role/handling comparison within this tier. |
| `tacz:scar_h` | rifle | 14 / 570 / 20 | 0.5 | DROP | 14 base damage / 50% armor ignore automatic-capable rifle. |
| `tacz:scar_l` | rifle | 7.5 / 625 / 30 | 0.25 | L3 | Provisional role/handling comparison within this tier. |
| `tacz:scar_mk20` | dmr | 18 / 275 / 10 | 0.5 | DROP | 18 base damage / 50% armor ignore precision role. |
| `tacz:sks_tactical` | dmr | 12 / 510 / 10 | 0.25 | L2 | Provisional role/handling comparison within this tier. |
| `tacz:spas_12` | shotgun | 48 / 200 / 5 | 0.25 | L5 | 48 base damage, manual-action/burst modes; compare against AA12 natively. |
| `tacz:spr15hb` | dmr | 10 / 700 / 15 | 0.3 | L3 | Provisional role/handling comparison within this tier. |
| `tacz:springfield1873` | sniper | 35 / 90 / 1 | 0.25 | L1 | 35 base damage but one-shot feed/90 RPM; do not rank on damage alone. |
| `tacz:svd` | sniper | 19 / 180 / 10 | 0.45 | L4 | Provisional role/handling comparison within this tier. |
| `tacz:taurus500` | pistol | 40 / 120 / 5 | 0.5 | DROP | 40 base damage / 50% armor ignore sidearm. |
| `tacz:taurus943` | pistol | 8 / 200 / 8 | 0.0 | L1 | Provisional role/handling comparison within this tier. |
| `tacz:tec_9` | pistol | 6 / 1200 / 20 | 0.1 | L2 | Provisional role/handling comparison within this tier. |
| `tacz:timeless50` | pistol | 15 / 300 / 8 | 0.25 | L5 | Provisional role/handling comparison within this tier. |
| `tacz:type_81` | rifle | 9 / 650 / 30 | 0.2 | L3 | Provisional role/handling comparison within this tier. |
| `tacz:ump45` | smg | 10 / 660 / 25 | 0.2 | L4 | Provisional role/handling comparison within this tier. |
| `tacz:urgi` | rifle | 7.5 / 750 / 30 | 0.25 | L4 | Provisional role/handling comparison within this tier. |
| `tacz:uzi` | smg | 8.5 / 600 / 20 | 0.15 | L2 | Provisional role/handling comparison within this tier. |
| `tacz:vector45` | smg | 8 / 1200 / 20 | 0.2 | DROP | 1200 RPM / 8 base damage. |

### Every Superb Warfare reference definition

These are an additional candidate catalog, not evidence that CatanCraft currently sells SBW handheld guns. Existing TaCZ offers remain unchanged in this planning update.

| Exact ID | Source type | Raw damage / RPM / capacity | Armor bypass | Proposed unlock | Reason / qualification |
|---|---|---|---|---|---|
| `superbwarfare:aa_12` | Shotgun | 2.5 / 360 / 25 | 0.05 | DROP | 25-round automatic shotgun, 12 projectiles per shot. |
| `superbwarfare:ak_12` | Rifle | 8.25 / 700 / 30 | 0.3 | L4 | Provisional role/handling comparison within this tier. |
| `superbwarfare:ak_47` | Rifle | 9 / 600 / 30 | 0.2 | L2 | Provisional role/handling comparison within this tier. |
| `superbwarfare:awm` | Sniper | 36 / — / 5 | 0.8 | L5 | 36 base damage / 80% bypass: candidate top shop sniper. |
| `superbwarfare:bocek` | Special | 48 / — / 1 | 0.25 | DROP | Special bow; 48 listed base damage. |
| `superbwarfare:devotion` | MachineGun | 9.5 / 400 / 55 | 0.25 | L5 | Raw 400 RPM is not final performance: spin-up/item behavior must be tested. |
| `superbwarfare:glock_17` | Handgun | 5.5 / 400 / 17 | 0.15 | L1 | Alternative mod implementation; do not silently substitute for existing TaCZ starter. |
| `superbwarfare:glock_18` | Handgun | 5.5 / 1300 / 17 | 0.15 | L4 | Provisional role/handling comparison within this tier. |
| `superbwarfare:hk_416` | Rifle | 8 / 900 / 30 | 0.25 | L5 | Provisional role/handling comparison within this tier. |
| `superbwarfare:homemade_shotgun` | Shotgun | 5 / 600 / 2 | 0.01 | L1 | Provisional role/handling comparison within this tier. |
| `superbwarfare:hunting_rifle` | Sniper | 65 / — / 1 | 0.7 | DROP | 65 base damage / 70% bypass / one round: not a beginner gun despite name. |
| `superbwarfare:igla_9k38` | DirectLauncher | 260 / — / 1 | — | L5 | Conditional: anti-air counter availability, guidance and ammo tests. |
| `superbwarfare:insidious` | Rifle | 11 / 900 / 48 | 0.4 | DROP | 11 base damage / 900 RPM burst / 48 rounds. |
| `superbwarfare:javelin` | DirectLauncher | 650 / — / 1 | — | DROP | Guided missile; 650 listed direct damage. |
| `superbwarfare:k_98` | Sniper | 35 / — / 5 | 0.5 | L2 | Provisional role/handling comparison within this tier. |
| `superbwarfare:marlin` | Rifle | 16 / — / 8 | 0.3 | L3 | Provisional role/handling comparison within this tier. |
| `superbwarfare:minigun` | MachineGun | 8 / 1200 / — | 0.3 | DROP | 1200 RPM; special feed/heat/spin-up. |
| `superbwarfare:mk_14` | Rifle | 12 / 700 / 20 | 0.4 | L5 | Provisional role/handling comparison within this tier. |
| `superbwarfare:mosin_nagant` | Sniper | 33 / — / 5 | 0.54 | L2 | Provisional role/handling comparison within this tier. |
| `superbwarfare:mp_443` | Handgun | 5.5 / 400 / 17 | 0.15 | L1 | Provisional role/handling comparison within this tier. |
| `superbwarfare:mp_5` | Smg | 6.5 / 900 / 30 | 0.12 | L3 | Provisional role/handling comparison within this tier. |
| `superbwarfare:m_1911` | Handgun | 9.5 / 400 / 7 | 0.2 | L2 | Provisional role/handling comparison within this tier. |
| `superbwarfare:m_2_hb` | MachineGun | 40 / 360 / 100 | 0.4 | DROP | 40 base damage / 100 rounds; emplacement/handling review. |
| `superbwarfare:m_4` | Rifle | 8 / 850 / 30 | 0.25 | L3 | Provisional role/handling comparison within this tier. |
| `superbwarfare:m_60` | MachineGun | 10 / 600 / 100 | 0.25 | L4 | Provisional role/handling comparison within this tier. |
| `superbwarfare:m_79` | CurvedLauncher | 100 / 180 / 1 | — | L4 | Conditional: explosives and damage-boundary tests. |
| `superbwarfare:m_870` | Shotgun | 3 / — / 8 | 0.05 | L3 | Provisional role/handling comparison within this tier. |
| `superbwarfare:m_98b` | Sniper | 38 / — / 5 | 0.6 | L4 | Provisional role/handling comparison within this tier. |
| `superbwarfare:ntw_20` | Sniper | 140 / — / 3 | 1 | DROP | 140 base damage / 100% armor bypass. |
| `superbwarfare:qbz_191` | Rifle | 8.5 / 750 / 30 | 0.28 | L4 | Provisional role/handling comparison within this tier. |
| `superbwarfare:qbz_95` | Rifle | 8.5 / 650 / 30 | 0.28 | L3 | Provisional role/handling comparison within this tier. |
| `superbwarfare:ql_1031` | Sniper | 9 / 700 / — | 0.5 | DROP | FE-powered automatic weapon; energy limits must be audited. |
| `superbwarfare:repair_tool` | Special | 3 / 1200 / — | — | HOLD | Utility, not combat progression; separate FE/vehicle-maintenance offer review. |
| `superbwarfare:rpg` | DirectLauncher | 300 / 120 / 1 | — | L5 | Conditional: different rocket types override direct/explosion damage. |
| `superbwarfare:rpk` | MachineGun | 9.5 / 600 / 40 | 0.23 | L3 | Provisional role/handling comparison within this tier. |
| `superbwarfare:secondary_cataclysm` | CurvedLauncher | 80 / 300 / 8 | — | DROP | Eight-round launcher with alternate firing feeds. |
| `superbwarfare:sentinel` | Sniper | 45 / — / 5 | 0.8 | DROP | 45 base damage / 80% bypass; special behavior requires testing. |
| `superbwarfare:sks` | Rifle | 10.5 / 400 / 20 | 0.23 | L2 | Provisional role/handling comparison within this tier. |
| `superbwarfare:super_star_shooter` | DirectLauncher | 60 / 300 / 300 | — | HOLD | Exclude ordinary catalog and initial loot: special star ammunition/projectile needs separate balance review. |
| `superbwarfare:svd` | Sniper | 18 / 300 / 10 | 0.45 | L3 | Provisional role/handling comparison within this tier. |
| `superbwarfare:taser` | Special | 5 / 240 / 1 | — | L2 | Conditional: stun/control behavior and counterplay need tests. |
| `superbwarfare:trachelium` | Handgun | 24 / 240 / 6 | 0.5 | DROP | 24 base damage / 50% bypass; alternate shotgun feed. |
| `superbwarfare:vector` | Smg | 6 / 1200 / 13 | 0.15 | L5 | Provisional role/handling comparison within this tier. |

The strongest-candidate reserve is deliberately conservative: heavy anti-materiel rifles, miniguns, several high-output rifles/PDWs, advanced guided/rapid-fire launchers and special weapons remain outside the shop. Collectibles such as Golden Deagle can also be rare without being stronger: its reference damage is lower than ordinary Deagle. Native comparisons may move candidates between the ordinary and reserved lists; the reserve must remain meaningful rather than letting another offer sell the same effective gun under a skin/NBT alias.

## Monument supply-drop design for the same later update

Recommended presentation: completing a monument creates one nation-owned supply-drop entitlement with a visible reward/claim interface. A physical crate can present it once its exact location and interaction policy are surveyed. The actual result is rolled server-side **once**, saved, and then revealed. Reopening the crate, reconnecting, switching players or restarting must show the same remaining contents.

Use a revisioned weighted table with common, uncommon, rare and very rare entries. Weights determine chances; quantities and eligible bundles may vary. Do not silently add randomized items on top of the entire old fixed payout—the old values are a comparison baseline, and the new expected value must be balanced as a replacement. An optional guaranteed useful supply component can soften empty-feeling captures, but that is a proposal, not a fixed payout requirement.

| Reward band | Candidate contents | Intended role |
|---|---|---|
| Common | Compatible ammo bundles, ordinary construction/industrial materials, fuel or modest treasury supplies | Frequent useful resupply |
| Uncommon | Larger or more specialized supplies; ordinary shop-tier guns with their exact compatible ammunition | Noticeable useful variety |
| Rare | Strong conventional guns, more valuable components, approved vehicle-delivery entitlements | Memorable captures without making shop progression irrelevant |
| Very rare | Selected DROP-reserved guns or specifically approved valuable vehicles | Chase rewards unavailable from the ordinary shop |

Keep monument identity through different weights: Military Depot emphasizes weapons/ammunition; Industrial Complex emphasizes components/construction; Refinery emphasizes fuel/energy supplies. This need not prohibit a rare cross-category reward. No percentages, guaranteed gun count, vehicle list, pity system or reroll currency is approved by this document.

A gun bundle should carry a bounded, tested amount of its exact ammo so a rare drop is usable. Its long-term resupply policy must be explicit—rare gun ammo can be scarce or high-tier without making the reward permanently unusable. Avoid accidentally granting maximum attachments, enchanted/perk rolls, loaded magazine capacity or infinite energy through a generic item builder. All ammo, attachments, energy and perk variants need exact native data identities and their own economic review.

Vehicle rewards should create a durable nation-bound **delivery entitlement**, redeemed through a compatible fixed facility bay, not an SBW portable vehicle item and not a spawn on top of a monument or player. A blocked bay keeps the same pending reward; it must not discard the prize, reroll it, charge again, spawn elsewhere or bypass clearance. Define whether a reward winner without the necessary facility can hold the entitlement until eligible; recommended default is to hold it without expiry. This is a design recommendation, not an implemented queue. SEC-01 crowbar/retrieval remains **UNRESOLVED / HIGH PRIORITY** and a prerequisite for describing rewarded vehicles as secured.

Preserve the current winning-nation reward ownership as the first implementation baseline. Decide the exact claimant role, member access, crate theft/despawn behavior and reward transfer rules before enabling physical loot. Do not silently turn a national reward into unrestricted public item drops. These choices do not require altering capture geometry or its already accepted art.

## Durable reward and shop requirements

1. Bind each capture reward to a unique persisted capture/reward ID, winning nation, monument ID, table revision and resolved exact item/entity-entitlement list. Persist the chosen result before exposing delivery. Never recompute a pending reward from the latest table or run the random draw again on load.
2. Track claim/delivery progress separately from the immutable rolled result. Retrying an interrupted claim reconciles the original item delivery/entity UUID rather than blindly inserting/spawning again. A log message or `setDirty()` alone is not a proven save boundary. Full inventory or unavailable bay leaves the exact reward pending.
3. Confirm eligibility server-side: current actor/nation, physical counter/crate, approved factory level, exact installed catalog revision, inventory capacity and live bay clearance. Remote commands and stale GUIs must not bypass the eventual physical-shop gate. No existing ownership or receipt can authorize a changed gun ID or silently substituted ammo.
4. Treat DROP exclusion as one centralized catalog rule applied to every shop category, alias, variant and confirmation path. Audit native recipes, blueprints, creative/debug paths and alternate vendors for implications, but do not silently delete existing crafting systems: the owner's requirement here is exclusion from the shop, and broader acquisition changes must be documented separately.
5. Preserve captured rewards and historical shop prices across balance revisions. Do not alter old reward receipts or charge a repriced amount when recovering an existing transaction. Missing mods/unknown IDs must keep unresolved claims intact and explain the blocker.

## Balance and validation work before enabling

- Reconcile the full **effective installed test-pack** catalog and overrides against this reference. Resolve every missing/additional ID, configured script/perk/attachment variant and ammo family. Produce a versioned final allocation with no unclassified purchasable gun.
- Measure each weapon within its intended role: native unarmored/armored damage, headshots, range falloff, realistic hit rate and recoil, reload downtime, burst/manual/charge behavior, movement/ADS, magazine extensions and ammunition/energy consumption. Do not compare only raw damage×RPM; test duplicate-role TaCZ/SBW alternatives together.
- Check that higher-level shop options improve practical capability while keeping cheaper guns worth using. Verify that the reserved list contains meaningfully desirable weapons and that shop alternatives/attachments do not recreate their full advantage too early. Preserve reasonable defensive counterplay, especially anti-vehicle/anti-air access; a rare guided weapon must not be the only usable defense.
- Set provisional acquisition and resupply prices from measured capability, resource scarcity, ordinary income and expected replacement rate. Compare cost per useful engagement and time-to-afford at each factory level. Tune factory upgrade payback, ammo sink and vehicle maintenance together under ECO-02. No numerical price or loot weight is finalized yet.
- Run deterministic seeded loot-table checks and a larger offline distribution simulation: exact allowed entries, normalized weights, rarity rates, expected value, variance, and frequency/time between top rewards. Compare reward income against ordinary production and captures over time. Record sample counts and confidence limits; a few lucky captures do not prove odds.
- Run focused native delivery/restart tests in disposable worlds: full inventory, disconnected claimant, missing mod/ID, partial item insertion, blocked bay, save failure before/after roll and delivery, duplicate claims, competing claimants, changed ownership/table revision, restart without reroll or duplication, and normal vehicle retrieval protection. Separate synthetic policy tests from the exact TaCZ/SBW native runtime checks.
- Finish with one combined solo checklist for the actual shop/tier/drop update. No current live-world reset, repeated accepted purchase checklist, manual import, multiplayer exercise or Blender assignment is required by this roadmap addition.

## Source and audit evidence

CatanCraft code inspected: `command/NationProcurement.java`, `data/BuildingInstance.java`, `data/BuildingType.java`, `command/CatanCommands.java` building-upgrade guard, `data/MonumentType.java`, and `monument/MonumentManager.java`. Existing scope: [ECO03_PHYSICAL_PROCUREMENT_REQUIREMENTS.md](ECO03_PHYSICAL_PROCUREMENT_REQUIREMENTS.md), [PROCUREMENT_PHASE1.md](PROCUREMENT_PHASE1.md), [SEC01_RETRIEVAL_AUDIT.md](SEC01_RETRIEVAL_AUDIT.md), and [TEST_RESULTS_MONUMENT_R1_2026-10-09.md](TEST_RESULTS_MONUMENT_R1_2026-10-09.md).

Reference scan performed: 70 TaCZ index/data pairs resolved; all 140 raw TaCZ files matched their pinned Git blob IDs; all 43 SBW definitions matched registered IDs; all 113 configured reference entries appear exactly once in the proposal tables. These are read-only data/coverage checks. No Minecraft build, combat test, price test, random-loot implementation or owner acceptance is claimed for this proposal.

Machine-readable reference stats, exact source pins, per-file digests and the complete allocation are retained in [weapon_catalog_reference39.json](evidence/weapon_catalog_reference39.json). The canonical owner requirements are recorded in [roadmap #10](https://github.com/quintaneous/catanCraft/issues/10).
