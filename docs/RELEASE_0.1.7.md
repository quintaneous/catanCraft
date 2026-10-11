# CatanCraft v0.1.7 — complete paginated economy audit

Forge 1.20.1, Forge 47.4.10, Java 17 target. This build retains v0.1.6 read-only R5 and R4-A systems, the Logistics Center's output bonus and existing vehicle procurement/crowbar behavior. **This patch does not change economy rates or modify the world when reporting.**

## Why v0.1.7

The prior `/catan debug production a` displayed raw producer output, a nominal Agriculture cost and installed processors, but not cash income, production inputs actually consumed by an active cycle, Logistics Center bonuses, inactive/utility buildings or the net changes to national resources. It also presented per-city previews from the live national stockpile even though the production engine processes ALL owned cities together and uses a shared stockpile, causing misleading diagnostics.

## New debug command

```
/catan debug production a
/catan debug production a 2
/catan debug production i
```

- Default page 1. Click `[Next]` / `[Prev]` in the Minecraft chat for pages, or pass a number after the territory.
- Each page has up to eight readable rows, to reduce chat overflow. The full ledger is **also emitted to the SERVER `latest.log`** when page 1 is requested, prefixed `ECONOMY AUDIT`; a single file upload replaces many overlapping screenshots.
- A report performs a read-only dry-run of the **next 15-minute economic cycle** using a clone of the owning nation's actual treasury/stockpile and the current production-building records.
- The simulation mirrors the engine's four phases in original territory order: (1) produce all Agriculture, (2) consume one Agriculture upkeep per nonfood raw territory and proportionally limit raw outputs during food shortages, (3) run processor tiers 1–3 using the real `EconomyEngine.processorPreview` and Logistics bonuses, (4) pay base territory and Commercial District income. Shared resource contention between cities is accounted for in this deterministic snapshot.
- For the selected territory, list City/Producer levels, raw resource percentages and outputs, exact Agriculture upkeep paid/required, missing-input or target-paused processors, exact consumed inputs and boosted outputs of active processors, all Commercial/base money income, all non-producing purchased buildings (Logistics Center, Weapons Factory, Vehicle Factory), and net resource change by resource type.
- Economics remain dynamic: a simulated next-cycle snapshot is NOT a guarantee of four stable cycles/hour if input supply/stock targets change. The command never triggers an actual cycle, deducts funds, updates persistent saved data, changes blocks, or alters the real stockpile.
- Debug commands are OP-only, like the existing production preview. The report includes only the requested territory's attributed movements even though the simulated shared stock tracks all the owning nation's territories.

## Installation

1. Stop client and server; back up `world/`, `mods/`, and `config/catancraft/`.
2. Replace all old `catancraft-*.jar` in both client/server `mods/` with `catancraft-0.1.7.jar`.
3. Keep **unchanged**:
   - `config/catancraft/schematics_bundle.zip` (R3 production, 38 required assets),
   - `config/catancraft/CatanCraft_R4A_Placement_Ready_Assets.zip`,
   - `config/catancraft/CatanCraft_R5_City_Rework_And_Civic_Assets.zip`,
   - `config/catancraft/R5_READ_ONLY_PROBE_INDEX.zip`.
4. Keep the current map/world/nation/TaCZ/Superb Warfare files. No additional schematic update is necessary.
5. Restart and verify `/catan map assetstatus` still reports 38 ready, 0 invalid.

## When user resumes testing

- [ ] v0.1.7 both sides loads with no Mixin errors; worlds, R3/R4/R5 asset status intact.
- [ ] `/catan debug production a` shows City/Producer, raw outputs and upkeep, processors, income, purchased utility buildings, net change, and page controls.
- [ ] `/catan debug production a 2` opens second page without running a cycle.
- [ ] `latest.log` contains `ECONOMY AUDIT BEGIN`, every line of the ledger, and `ECONOMY AUDIT END`.
- [ ] At 1,088 Fuel / 100 target, Refinery says `PAUSED AT TARGET`, inputs/output 0; increasing target above national Fuel and giving input Oil changes that to READY without changing balances from the debug command.
- [ ] With Logistics Center L1 in SAME city, Refinery L1 READY output +9 Fuel per batch, consuming 10 Oil (without Logistics L1 output +8 Fuel, with L2 +10).
- [ ] Commercial District income and territory base income appear, including total $/15m.
- [ ] An Agriculture shortage shows the smaller amount of upkeep actually paid and scaled output rather than claiming full upkeep paid.
- [ ] Save nation resource balances before and after displaying pages: no stock or money changed.
- [ ] Previous R5 commercial/tree preflight conflicts and approved R4 checkpoint remain read-only. Physical construction still disabled.
- [ ] Mark purchased-vehicle crowbar protection **KNOWN FAIL**, not solved in this build; do not give players crowbars.
- [ ] Pending newer version: guarded R5 installation and R4 purchases are not in this release.

## Established user testing and outstanding issues

- v0.1.6 user confirmed `/catan r5 group a logistics_service_connection` **158 checks, zero mismatches**. R5 City A front park 537/0, tree 92/0, City A Logistics candidate 18,259 checks/1 missing dependency, City A Commercial 16,182 checks/94 issues (92 planned tree removal, 2 unresolved), City I Commercial unloaded chunks, R4 checkpoint 4,939/0, blocked bunker refused.
- User reports v0.1.6 initial gameplay checks for Commercial income, Logistics upgrades/duplicate-purchase rejection, Military Depot capture income and solo restart persistence.
- The crowbar safeguard emits a denial but still lets a nation-purchased Superb Warfare vehicle be packed. GitHub issue #6 remains OPEN. v0.1.7 does not claim a fix.
- No R5 or R4 in-world structure installation is authorized; do not repaste developed cities.
