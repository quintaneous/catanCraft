# ECO-03 — existing weapon-shop facility survey, 0.1.40 preparation

2026-11-10. Read-only survey of repository-owned immutable schematic bytes and current source, following the owner's request to bundle the next in-game acceptance with the weapon shop and crowbar protection. No world, installed mod, asset, registered counter, price or interaction was changed. This is a concrete implementation candidate; native interaction/access validation and production wiring remain separate.

## Available source and exact geometry

The necessary Weapons Factory geometry is already bundled. No new Blender asset or live-world read is needed to establish its authored source.

| Reference input | SHA-256 / binding |
|---|---|
| Decoded `data/catancraft/basic_construction/assets.b64` ZIP | `cbaf8b095958f7de19a58bbca2723f18a1273d908eb5f174d36cd4aa1e9608d1` |
| `weapons_factory.schem` | `9590a848409d177f0e4fa0238767ca6ee6f0aaac18563e9f9a14ae18ced5b229` |
| `vehicle_factory.schem` | `793e2a277fcb7287ba76298d2080808e315bcf626f76e9fe8f9ce5143e731649` |
| `th1.schem` | `dfe74c98ba01bc2d66496b2d306a61de951ad0a3a24af6d6de895d2dbb16ec88` |
| `starter_settlement.schem` | `fe03d2ddcc76555ad56f0767d601100380193053bfdca1f56dd2f45d7be9e443` |

These members match `tests/fixtures/basic-construction-provenance.json`, whose source is the previously supplied `mcwar.zip / mcwar/config/catancraft/schematics_bundle.zip` reference archive (`2a7a362047a6672b802c0077cd58db236183c10a69cc69dc928c64859d985edc`). The original archive and current installed world were not opened. The materialization script is historical source reconstruction and was **not executed**, because it applies patches; this survey only decoded the already bundled bytes.

Both factory schematics are XYZ31×47×31, local anchor `(29,2,15)`, Sponge offset `[-29,-2,-15]`, and support rotations0/180. Their persisted visual tokens are `weapons_factory@r4` and `vehicle_factory@r4`. Current building economic levels do not select five separate factory schematics. Do not assume L2–L5 factory upgrades create new counter positions.

## Recommended weapon counter

Use the existing **crafting table at local `(19,3,18)`** in the Weapons Factory as the physical interaction block. It is a block-entity-free authored workstation, so a shop click handler can use it without creating, replacing or clearing any blocks or inventories. Keep nearby barrels and their contents untouched. There is no authored lectern in this factory.

| Exact local point | State / role |
|---|---|
| `(19,3,18)` | `minecraft:crafting_table`, recommended counter |
| `(19,2,17)` | Air at feet and head `(19,3,17)`; immediate floor `(19,1,17)` is authored solid support; approach standing cell |
| `(24,2,13)` and `(24,3,13)` | East-facing spruce door lower/upper halves, native closed source |
| `(29,2,15)` | Existing roadside approach at the placement anchor; air feet/head above gravel at `(29,1,15)` |
| `(12,3,8)` | Alternate crafting table; longer route, not needed for the first counter |

The short static route from the roadside approach to the recommended standing cell consists of these joined axis-aligned segments (all feet Y2):

| Segment | From | To | Horizontal steps |
|---|---|---|---:|
| Road approach | `(29,2,15)` | `(25,2,15)` | 4 |
| Door approach | `(25,2,15)` | `(25,2,13)` | 2 |
| Door and main aisle | `(25,2,13)` | `(19,2,13)` | 6 |
| Counter approach | `(19,2,13)` | `(19,2,17)` | 4 |

This is **16 horizontal steps**, with ordinary air at feet/head except the two cells of the source door. The static check treats that door as passable after normal opening; it does not certify a closed door as collision-free. Every route cell has a supported full-block floor from the inspected palette. The alternate table has a 23-step route ending at `(12,2,9)`.

An initial exploratory search at feet Y3 found no continuous route; inspecting the actual door's lower half at Y2 corrected that probe. No geometry was edited to make the route pass. Native collision, reach/ray trace, door operation, held-item event handling and current-world obstruction must still be checked in disposable fixtures. No final interaction distance or session lifetime is inferred from this static route.

## Plot and orientation mapping

Resolve the actual purchased `BuildingInstance` using `IndustrialPlotService.occupiedPlot`, then call `IndustrialPlotService.anchorFor(WEAPONS_FACTORY, plot, territory)`. Do not use raw map `buildingPlots` origins or assume every Weapons Factory is `plot_3`.

For resolved anchor `A=(Ax,Ay,Az)` and sign `s=+1` for its rotation0 or `s=-1` for180:

- Counter: `(Ax−10s, Ay+1, Az+3s)`.
- Standing cell: `(Ax−10s, Ay, Az+2s)`.
- Road approach: `(Ax, Ay, Az)`.
- Door lower: `(Ax−5s, Ay, Az−2s)`.

These are the same transform used by `BasicConstructionAssets.plan`; rotate block facing properties with the resolved anchor too. The **resolved factory rotation**, rather than city rotation alone, matters because plot4 flips an east-facing Weapons Factory master by180 degrees.

The following table uses the current registry's world Y81 anchors. Offsets are relative to city-center X/Z, not raw plot origins.

| City rotation | Actual plot | Factory rotation | Anchor X/Z offset | Counter X/Y/Z offset | Standing X/Y/Z offset |
|---:|---|---:|---|---|---|
| 0 | plot_1 | 0 | `(-45,-24)` | `(-55,82,-21)` | `(-55,81,-22)` |
| 0 | plot_3 | 0 | `(-45,+16)` | `(-55,82,+19)` | `(-55,81,+18)` |
| 0 | plot_4 | 180 | `(+45,+16)` | `(+55,82,+13)` | `(+55,81,+14)` |
| 180 | plot_1 | 180 | `(+45,+24)` | `(+55,82,+21)` | `(+55,81,+22)` |
| 180 | plot_3 | 180 | `(+45,-16)` | `(+55,82,-19)` | `(+55,81,-18)` |
| 180 | plot_4 | 0 | `(-45,-16)` | `(-55,82,-13)` | `(-55,81,-14)` |

In the counter/standing columns, X/Z are offsets and Y is the **absolute current registry Y**, deliberately not an offset from a potentially changed map center. Implementation should use the anchor formula above. City A's original plot3 yields counter `(-805,82,-731)`, standing `(-805,81,-732)`, and door lower `(-800,81,-736)`. This is a source-derived candidate for whichever city currently owns a completed Weapons Factory in that plot, not evidence the user's current City A contains that building.

The local route transforms identically into all six plot/orientation cases. That does not approve every city for deployment or prove live route clearance; current reservations and site/source checks still apply. City I is an existing map example of city rotation180.

## Starter access without a factory

The existing Glock/9mm starter access has no factory requirement and must remain reachable. The bundled starter settlement contains its management lectern at local `(80,5,61)`; TH1 contains it at `(37,5,17)`. Both map to the same registered terminal. City A's map point is `(-750,84,-773)`; City I's is `(-750,84,273)`. `TownHallRevisions.managerIndex()` explicitly preserves the `(37,5,17)` manager cell through the later Town Hall revision logic.

Recommended first presentation: a **Starter Armory** choice available after physically interacting with the existing management lectern, while preserving its existing nation dashboard and management book. This avoids requiring a paid Commercial District or a Weapons Factory before starter access. It is a proposed shared interaction flow, not a claim the lectern is already a shop. It should not rewrite, remove or replace the book, lectern NBT or dashboard controls. A remote `/nation gui` opening must not mint a physical shopping session.

`MapInteractionHandler` currently accepts main-hand server-side right clicks on the exact registered lectern, verifies owning-nation membership and opens `NationDashboard`. It does not enforce procurement leader authority; the later confirmation must retain `NationProcurement`'s leader spending rule. The new factory click also needs exact position/source identity and nation checks rather than making every crafting table a counter.

`WorldProtection.onRightClickBlock` cancels interactions while holding block items, buckets, flint/steel, fire charges or bone meal. A shop handler must respect protection/event cancellation and avoid allowing the same click to open vanilla crafting or place/use an item. Native test the intended main/offhand and held-item behavior; do not weaken global protection for shop convenience.

## Existing completion and recovery evidence to bind

A building record or decorative workstation alone is insufficient. The current source provides these pieces:

| Source/API | Required use / limit |
|---|---|
| `TerritoryData.ownerNationId`, `NationData.isLeader`, player nation membership | Resolve current nation/actor authority at opening and confirmation |
| `BuildingInstance.type/level/plotId` | Require exactly one completed local Weapons Factory in the selected supported plot; use the actual level for tiers |
| `IndustrialPlotService.occupiedPlot` | Recognizes only known legacy physical types when plotId is blank; unknown/data-only records remain ambiguous |
| `IndustrialPlotService.anchorFor` | Resolves actual slot and extra plot4 orientation; reject absent/unsupported anchor |
| `CatanSavedData.placedMapAsset("territory:"+city+":"+plot)` | Require the matching exact asset placement token, not an unrelated building's marker |
| `BasicConstructionPurchase` original plus recovery companion | Factory construction settles its building record, placement token and payment receipt together, with saved recovery state; a marker alone is not immutable journal evidence |
| `PaidRecoveryService.requireClear(server,city)` | Rejects queued/active/recoverable basic, civic, prestige, producer and legacy-defense construction; validates existing histories |
| `TownHallRepairService.requireNoPending(server,city)` | Rejects incomplete/foreign Town Hall repair history; it also invokes construction-world authorization, so cannot be copied blindly into an unrestricted shop adapter |
| `SectorService.hasActiveOperation` | Package-private active-run check, not a complete durable all-city siege/construction exclusion API |

`BasicConstructionPurchase.unfinished` and `PaidConstructionRecovery.complete` are package-private and validate retained source/companion relationships. Do not manufacture an old purchase journal for a legitimately completed legacy factory. A production physical-shop resolver needs a deliberate current/legacy completion policy: exact existing marker plus unique valid building and verified source, with every existing relevant pending history held. It must not delete or normalize old records merely to make a counter available.

The loaded world should still match the counter and its immediate support/access context. A shop opening must not repair missing artwork, recreate a factory, or grant authority from one matching block. Freeze the facility identity/quote and re-resolve it at confirmation. A changed owner, plot, level, source revision, pending construction or siege phase should invalidate the session before delivery/payment.

## Vehicle and civic scope

The Vehicle Factory has a source crafting table at local `(18,3,9)`, but this audit's simple fixed-height roadside path search did not connect the main approach to it. That is a static-search limitation/finding, not proof the building has no usable route: stairs, multiple door approaches and native paths require a dedicated survey. It is **not** selected as a ready vehicle counter here. No fixed vehicle bay, heading, turning sweep or boarding clearance is approved by this weapon-counter survey; retain the published OBB caveats in `ECO03_PHYSICAL_PROCUREMENT_REQUIREMENTS.md`.

Commercial/Logistics R5 contract references exist, but they are unnecessary to unblock the proposed starter lectern plus existing Weapons Factory workstation. No civic interior is declared a shop or vehicle bay by this document, and no redesign is requested. The separate crowbar acceptance can use already purchased vehicles; it does not require adding a new vehicle purchase/bay feature solely to exercise retrieval protection.

## Bounded next implementation/test scope

Use the pinned existing weapon workstation and registered starter terminal to develop the physical-session gate; use synthetic identities only in isolated tests. Validate source/rotation mapping for all three factory plots and both supported city orientations, then native click/range/ownership/level/pending-work refusals. Reuse the existing four TaCZ gun/ammo offers until exact installed catalog/tier/price readiness permits expansion; the 113-entry reference proposal is not automatic runtime registration.

Before an owner-facing combined release, finish durable purchase/delivery, both command-alias bypass checks, reconnect/restart behavior and actual TaCZ gun/ammo compatibility. Keep one consolidated solo checklist covering shop use and the existing purchased-vehicle crowbar check. This survey executed only byte/hash/decode and static path checks; no Minecraft build, native GameTest, in-game acceptance or live-site survey was performed by this audit.
