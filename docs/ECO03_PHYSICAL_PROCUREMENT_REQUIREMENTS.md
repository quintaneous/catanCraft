# ECO-03: physical procurement requirements audit

**Requirements/code audit only. No gameplay, prices, economy, world geometry or tests changed.** Source: current `NationProcurement`, `VehicleSpawnFinder`, map/industrial/building registries, the pinned official SBW 0.8.9.2 source audit, and live issue #10 revision 5 as fetched to `issue-10-current.md` on 2026-10-18. This report establishes the counter/bay contract before any authoring request. It does not dispatch a storefront redesign, NPC work or procurement implementation.

The owner requires in-person weapons/ammunition shops and vehicle storefronts with fixed facility deployment bays. A registered clickable block/GUI is the initial recommendation. Normal commands must not bypass physical presence. Preserve starter access, current factory gates and existing purchased progress. Queues are optional and not authorized by default. No new prices, recurring charges or economy calibration are approved by this audit.

## Actual current behavior and gaps

| Area | Current code behavior | Required change in the later implementation |
|---|---|---|
| Entry points | `/nation procure <id>` and the `/catan nation procure <id>` alias execute purchases directly; no counter/session required | Both aliases must use one server-authoritative physical-shop gate; informational listing can remain remote |
| Authority | Nation leader spends national treasury/stockpile | Preserve this permission until a separate decision changes it; verify again when confirming |
| Pistol / 9mm | No factory gate, no territory, distance, counter or dimension requirement; item delivered directly to player inventory | Preserve starter access through a reachable registered starter storefront; do not accidentally require a Weapons Factory |
| AK-47 / 7.62x39 | Any Weapons Factory `BuildingInstance` in any owned territory satisfies the gate | Preserve the intended factory requirement, but additionally require the actual local physical shop/session; resolve any desired same-city production rule explicitly rather than silently changing it |
| Pickup / LAV-150 | Any owned Vehicle Factory satisfies the gate, possibly in another territory; purchaser must stand in some owned territory | Bind the transaction to a registered physical facility and its specific approved bays; factory eligibility alone is not a location |
| Vehicle placement | `VehicleSpawnFinder` searches 7, 9, 11, 13 or 15 blocks from the player, at angles through 180 degrees and floor offsets 0, ±1, ±2; yaw follows the player | Remove arbitrary nearby-player placement from normal purchasing. Choose only among that facility's explicit fixed bay transforms |
| Existing spatial checks | Native AABB collision, entity clearance, nine support samples and territory containment; no selected factory/warehouse footprint or departure path | Retain genuine hazard checks and add exact registered bay, full model geometry, passenger/egress and road access checks |
| Purchase durability | TaCZ item insertion or entity spawn occurs before debit; `data.setDirty()` follows; no procurement operation journal, confirmation quote or durable spawn receipt | Add one recoverable payment/delivery boundary. Replayed confirms and process interruption must not create duplicate items/entities or charge twice |

`ownsFacility` checks only owned territory building records, not completed physical placement tokens, exact plot geometry, operating counter identity or a pending construction journal. Old data-only/unassigned building records therefore cannot themselves authorize a physical storefront. A future gate must distinguish a valid production entitlement from an actually available shop/bay. No existing records should be deleted or automatically converted during this audit.

Current TaCZ inventory insertion checks for an empty slot and uses the reflected runtime builder API. That is not an in-person shop interaction or durable delivery receipt. A future implementation must check inventory at confirmation and handle insertion atomically, including full stack/NBT identity. Existing entity ownership tags (`CatanCraftPurchasedVehicle`, `CatanCraftVehicleNation`) remain necessary but do not establish crash/replay accounting.

## Existing buildings and interaction points to reuse

`MapInteractionHandler` registers an exact management **lectern** location, verifies owning nation membership and opens `NationDashboard`. It does not register a gun counter, vehicle counter or warehouse deployment bay. Opening a remote dashboard is not proof of a physical procurement session. Other decorative lecterns, barrels or counters are not automatically authorized shops.

The actual current registry provides:

| Existing element | Verified source fact | Procurement readiness |
|---|---|---|
| Weapons Factory / Vehicle Factory | Both `BuildingType` entries exist; minimum city level 2; factory plot allocation is persisted in `BuildingInstance.plotId` | Reuse the purchased factory and its actual plot, subject to geometry/completion checks; no registered counter or bay exists yet |
| Industrial plots | `IndustrialPlotService` supports `plot_1`, `plot_3`, `plot_4`; factories can occupy different approved slots | Resolve `occupiedPlot` and `anchorFor`; never assume every Vehicle Factory is plot 1 or Weapons Factory plot 3 |
| Commercial District | Existing purchased civic building, minimum city level 1 | Possible existing interior to survey for starter trade; this is a candidate, not an approved gun-counter location |
| Logistics Center | Existing purchased civic building, minimum city level 2 | Existing warehouse/loading space is a candidate for vehicle storefront/bay survey; not a registered vehicle purchase facility today |
| Management terminal | City A map registers `(-750,84,-773)` in `minecraft:overworld` | Existing dashboard point only; not approval to repurpose it as a gun counter |
| Monument Military Depot | Existing fixed asset anchor `(82,81,-58)`, registered by `SchematicAssetRegistry.fixedAnchor` | Monument identity is not a local purchased armory/shop entitlement; do not silently turn it into a storefront |

Source coordinates above are registered anchors, **not surveyed counter or bay coordinates**. For example, with City A center `(-750,81,-750)` and rotation 0, the registry's original Vehicle Factory placement anchor is `(-795,81,-774)` and Weapons Factory placement anchor is `(-795,81,-734)`. These are geometry origins only. Actual new R3 placement must use `IndustrialPlotService.anchorFor` for the selected slot and type, including its plot-4 orientation correction. Raw `buildingPlots` coordinates in the map JSON differ from these roadside schematic anchors; substituting one for the other would misplace a counter or vehicle. No new counter, spawn center, vehicle heading or bay polygon is established by this report.

Reuse and survey the already supplied, immutable factory/civic art first. Required survey output is a small revisioned facility contract: existing building/type/plot and source hash; interaction block(s) and permitted standing area; shop frontage/access; exact bay center/heading/support and clearance masks; passenger boarding/dismount space; and unobstructed route to the existing road. Test both supported city orientations. If the existing interiors/yards cannot fit, record that concrete failure before requesting minimal asset adjustments. Do not ask Blender to restart north walls or redesign shops in parallel with the approved wall work.

## Vehicle clearance evidence and limits

Read-only artifact source: `superbwarfare-0.8.9.2-mc1.20.1-65d985a64-sources.jar`, SHA-256 `06b4bc749e540879265e3f3829c6c242da81512b19a80de0fbd9b089847893d5`. Matching audited runtime JAR SHA-256 is `559930003f638080de648a8621821d785158768c3f69a762094ef4c8d3c4cc12`. These are isolated downloaded audit artifacts, not a read of installed mods or a claim that the owner's entire modpack is verified.

| Vehicle | `ModEntities.kt` registered width × height | Main `Collision` OBB full local XYZ from the pinned vehicle data |
|---|---|---|
| `superbwarfare:sodayo_pick_up` | 2.4 × 2.0 | 2.5 × 1.458 × 6.75 |
| `superbwarfare:lav_150` | 2.8 × 2.45 | 2.75 × 2.375 × 6.375 |

`OBBInfo.getOBB()` passes `Size` directly to OBB half-extents, so those data values must be doubled for full dimensions. `Position`, yaw, pitch, vehicle transforms, other body/turret/wheel OBBs, dismount positions and models remain relevant. These numbers are **not an approved bay envelope**. The historical approximate 4×4 LAV suggestion in `PROCUREMENT_PHASE1.md` is insufficient as a deployment clearance certificate. Registered entity width/height does not describe vehicle length. Runtime OBB updates can change the effective bounds after creation; a pre-tick placeholder AABB must not be treated as final initialized geometry.

The fixed-bay survey must evaluate full initialized geometry at its declared heading, overhead clearance, road entry/exit and turning sweep, passenger/door access and neighboring protected structures. Refuse a blocked bay without moving entities, clearing blocks, choosing a random nearby fallback, issuing a portable item or taking payment. Availability of another explicitly registered compatible bay may be checked deterministically. Waiting queues remain out of scope unless separately approved.

## Required transaction and interaction contract

1. Register a stable facility ID bound to city, owner, exact purchased building/plot/revision, dimension and counter identity. Preserve source revisions and existing inventories/management-book NBT. Do not infer facility completion from a visual block alone.
2. Open a server-side session only from an authorized counter interaction. Bind actor UUID, facility ID, counter revision, current dimension and range. Exact range and any line-of-sight requirement still need a reviewed policy; this audit does not invent a distance.
3. Display one offer with existing eligibility rules, exact immutable price, quantity/NBT and delivery destination. Bind the quote to the session/owner/facility and expiry; recheck the same conditions on confirmation. Reopening or remote commands cannot bypass the gate.
4. Recheck ownership, leader authority, catalog/runtime item IDs, completed-facility state, inventory space or fixed-bay clearance, pending construction, loaded chunks/entities and funds before reservation. Conquest, movement, dimension change, stale session, modified counter or blocked bay must refuse without delivery/debit.
5. Journal operation UUID, historical price, actor/nation/facility identity and exact item delivery or vehicle entity UUID. Use a recoverable reservation/delivery/settlement boundary, reconcile existing delivery before retry, and preserve one persistent vehicle. A refund must not leave a delivered usable vehicle or duplicate gun stack. Keep ordinary unpurchased entities unaffected.
6. Add focused later tests for both aliases, remote/stale/foreign sessions, starter eligibility, actual selected plots and both orientations, blocked bays, owner/construction changes, inventory exhaustion, duplicate confirms and save/restart boundaries. Integrate actual pinned TaCZ/SBW runtime checks separately from synthetic shop-policy tests.

## Unresolved decisions and exact next step

Required next work is the bounded survey/contract above, not economic implementation or a storefront art redesign. Resolve the starter counter location and local sales facility types; whether factory eligibility remains nation-wide; exact counter range/session lifetime; specific fixed bay locations/headings/clearance masks; and whether existing yards support the actual models. No locations or sizes should be invented to unblock code.

ECO-02 balance changes are separate. Preserve current prices during this audit; do not sum prior module prices into a new approved schedule. Fuel/energy, destruction/refunds and conquest policy remain linked to SEC-01 and SIE-04. Purchased-vehicle crowbar/retrieval protection remains **OPEN / HIGH PRIORITY** pending the full relevant procurement/restart and owner-modpack evidence. No NPC/shopkeeper AI dependency, Combined Arms work, ammunition automation, siege mechanics, active-world editing or multiplayer testing was performed.
