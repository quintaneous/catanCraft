# Server-wide release requirement

Owner decision, 2026-11-14 UTC: every update must apply to the entire server,
not selected territories. This follows the owner's blocked weapon-shop and
wall interactions in City D. [Issue #10](https://github.com/quintaneous/catanCraft/issues/10)
remains the canonical roadmap; this is its release contract and source audit,
not a second roadmap. The requirement also applies to future configured maps.

## Definition of completion

1. Every eligible territory and facility supports the feature through normal
   gameplay. Ownership, available resources, earned tiers and legitimate building
   requirements still apply; those rules must be consistent across the server.
2. Use shared behavior and map/site data. No hardcoded A or D default, hidden
   city allowlist, command-only workaround, or move-to-another-city requirement.
   Ambiguous or changed sites must still fail safely with an actionable message.
3. Each release records a feature-by-city matrix covering all configured cities
   (currently A–P), actual orientations, eligible slots and relevant tiers.
   Distinguish implementation, static source/geometry qualification, executed
   native tests and owner acceptance. Mark blocked/unverified rows explicitly;
   mark not applicable only with a gameplay reason. Unknown is not a pass.
4. Automated validation covers normal access and transactions, wrong owner,
   cross-city quote/receipt isolation, stale or changed sites, relevant physical
   boundaries, startup, restart and interruption recovery. A translated/rotated
   City A fixture alone cannot establish another site's terrain or infrastructure.
5. Preserve existing inventories, balances, paid prices, entity identities,
   Fuel credit, reward rolls and journal recovery. Expanded contracts must be
   versioned; retained operations must recover under their recorded contract.
6. A city-limited development checkpoint may be retained as incomplete work,
   but must not be presented as a completed server-wide update or owner-ready
   package. Finish automated coverage before one focused owner solo checklist,
   beginning with their actual city. The owner need not repeat all 16 cities.

This changes the development target, not deployment permission. No live-world,
installed-mod or server-setting changes, resets, manual imports, multiplayer
tests or Blender assignment are authorized by this instruction. Preserve the
accepted City A North Reinforced / East, South and West Basic assembly, mixed
connections/restart, prior base readiness and 034 acceptance. Do not repeat those
accepted purchases. Handheld guns remain TaCZ-only; SBW handheld explosives are
deferred. SEC-01 crowbar remains **UNRESOLVED / HIGH PRIORITY** pending installed-pack
acceptance. Keep 033/034 purchase and startup recovery intact.

## Audited baseline: delivered 0.1.50

Read-only source audit at `6119e8c38135acba014bbee69bd5f0264363f2a7`;
delivered JAR source `715a22786c4bc050814b6e5b6130084a950f525c`.
[Original release evidence and hashes](VEHICLE_SERVICE_050_RELEASE.md) remain
unchanged. This audit ran no build or gameplay tests and inspected no live world.
The map below establishes configured sites, not installed-world qualification.

| System | Implemented scope found in source | Server-wide gap |
| --- | --- | --- |
| TaCZ starter/weapon counters and monument equipment claims | `ShopFacility40` binds A coordinates, ownership, placement keys and recovery; `PhysicalWeaponShop40` uses a singleton starter position | B–P cannot use their local interfaces. Nation-wide reward entitlement does not supply a local claim interface. |
| Vehicle purchase/refueling | `VehicleFactory50` binds A; delivery only at original plot 1, service at three A slots | Resolve and qualify every eligible facility. The original road/source composition and unrotated world envelope are not portable proof. |
| Basic/Reinforced compass walls | `SectorService` menus and production provider bind A; grouped validation/write permissions/recovery call `SectorCityAP3Group` | A new versioned site/provider layer and per-site source/support/corner qualification are required. Removing menu guards is insufficient. |
| Town Halls, ordinary factories, producers, houses and public paths | General A–P paths exist in `MapAssetService`, `BasicConstructionPurchase`, `TownHallPrestigeUpgradeService`, `ProducerService`, `R5HouseRevisions` and `StreetscapeR5` | All-city native readiness is unverified. Resource-specific producer eligibility and strategic economic-only upgrades must be distinguished from missing support. |
| Base preparation and civic assets | `ConstructionWorldAuthorization` uses fresh-demo or historical-R7 authorization; `CityBasePreparation.discover` requires both. Some house/path installers directly use historical authorization; civic/base assets have external input dependencies | Establish supported server/world initialization and complete asset packaging across all sites. Do not silently remove authorizers or manufacture authorization markers. |
| Economy/reward accounting and purchased-vehicle crowbar protection | Territory/nation accounting and tagged-vehicle protection do not show the same A-only interface gate | This source observation is not full server-wide or installed-pack acceptance; preserve existing tests and unresolved SEC-01 status. |

Roster source: `src/main/resources/data/catancraft/maps/season1_river_bridges_v3.json`.
Every city has center, management and building-plot metadata. All have unreserved
ordinary industry slots `plot_1`, `plot_3`, `plot_4`; actual facility transforms
still matter, including the extra flip for southern plot 4.

In the table, **limited A** means the implemented scope above, not full acceptance;
**blocked** means an explicit city restriction; **unverified** is not a failure
claim about a world that was not inspected. Wall A acceptance covers only the
already accepted assembly, not all possible combinations. Every row's complete
server-wide native qualification remains **unverified**.

| City | Center X, Z (Y=81) | City rotation | Local shop/claims | Vehicle purchase/service | Walls | Complete native qualification |
| --- | --- | --- | --- | --- | --- | --- |
| A | -750, -750 | 0° | limited A | limited A | accepted assembly; A provider | unverified |
| B | -250, -750 | 0° | blocked | blocked | blocked | unverified |
| C | 250, -750 | 0° | blocked | blocked | blocked | unverified |
| D | 750, -750 | 0° | blocked | blocked | blocked | unverified |
| E | -750, -250 | 0° | blocked | blocked | blocked | unverified |
| F | -250, -250 | 0° | blocked | blocked | blocked | unverified |
| G | 250, -250 | 0° | blocked | blocked | blocked | unverified |
| H | 750, -250 | 0° | blocked | blocked | blocked | unverified |
| I | -750, 250 | 180° | blocked | blocked | blocked | unverified |
| J | -250, 250 | 180° | blocked | blocked | blocked | unverified |
| K | 250, 250 | 180° | blocked | blocked | blocked | unverified |
| L | 750, 250 | 180° | blocked | blocked | blocked | unverified |
| M | -750, 750 | 180° | blocked | blocked | blocked | unverified |
| N | -250, 750 | 180° | blocked | blocked | blocked | unverified |
| O | 250, 750 | 180° | blocked | blocked | blocked | unverified |
| P | 750, 750 | 180° | blocked | blocked | blocked | unverified |

## Retrofit priorities under existing roadmap IDs

- **INT-01 / INT-03:** establish all-site source/asset and world-startup coverage;
  require the matrix above for every subsequent feature. Record explicit blockers
  and keep physical source qualification distinct from accounting tests.
- **ECO-03:** replace singleton counter bindings with shared site resolution from
  the clicked block, dimension and actual facility. Preserve leader/ownership,
  reach, tier, siege and pending-operation checks. Qualify starter and factory
  access, claim delivery and vehicle service at all eligible slots and tiers;
  qualify actual vehicle bays, roads, boarding, exits and departure geometry.
  An unsafe slot needs a qualified solution or explicit gameplay design, not a
  silent rollout omission. Preserve old binding serialization/revisions and
  discover pending operations after facility upgrades. Display the original site
  for an existing recovery operation; never rebind it to the current counter.
- **INT-02 / DEF-02 / DEF-03 / DEF-04:** derive a pinned all-16 site catalog from
  immutable authored inputs and normal base composition. Preserve the City A
  schema-1 provider/resources and exact historical recovery; dispatch new and
  old operations by recorded metadata in every grouped path. Transform blocks,
  states, corners and infrastructure while keeping world compass directions.
  North's sparse extension beyond standard bounds and West's lower support are
  asymmetric; do not widen whole restoration boxes or copy A terrain as truth.
  Validate all 81 structural level combinations and supported corner histories,
  all-site masks/protected roads, native purchases/upgrades, mixed orders and
  interruption/restart isolation in disposable developer worlds. Retain accepted
  A evidence without repeating the owner's accepted purchases.
- **SEC-01:** retain the unresolved/high-priority installed-pack crowbar check in
  the eventual combined solo checklist. No unrelated new feature takes priority
  over closing these server-wide gaps.

Next owner testing is blocked on this retrofit. The delivered 050 JAR is not a
fix for City D. Do not ask the owner to relocate, rebuild, reset or install a
metadata-only replacement. No new JAR or new test pass accompanies this contract.
