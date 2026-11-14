# All-city vehicle factories — development checkpoint

ECO-03 / INT-01 / INT-03, 2026-11-14 UTC. Implementation source
`27f62bd279a43b7604c24d857dcc16fe70ac5c00` on
`chatgpt/combined-shop-acceptance`. This is a development checkpoint, not the
combined owner release. Keep the owner's checklist paused until wall coverage,
shared startup, the full clean-server package and connected-client gates pass.

## Implemented behavior

Vehicle counters resolve all 48 eligible factory placements (A–P, three ordinary
industry slots) using the reviewed map and the actual factory placement transform.
The selected city's owner, completed factory level/token, pending construction,
siege history, loaded chunks, reach and sightline determine access. FakePlayer,
foreign, ambiguous and changed-map access remains refused. Both offered vehicles
retain their current prices and arrive with 25% Fuel; no catalog or economy tuning
is part of this change.

Each bay uses its own city's actual starter-settlement source with the placed
Vehicle Factory overlaid. The normal R5 public-space revision is selected only
from its sealed, identity-bound complete journal and all 1,661 ordered source
rows. Absent or rolled-back history uses the original baseline; incomplete,
foreign or corrupt history refuses. This reads existing history and never starts
preparation, repairs blocks or edits a journal. Current block/source, inventory
block-entity type, support, entity occupancy and native vehicle geometry still
require validation before delivery or refueling.

Every eligible site has its authored internal service lane and separate roadside
service rectangle. Their bounding union is not a parking area: all vehicle parts
must fit one complete rectangle. Vehicles must remain stopped, empty, loaded and
owned by the purchasing nation. Delivery has no nearby fallback or automatic
movement to another city/plot.

City A retains its exact 15-field factory identity and original revision for all
three slots. The original A plot-1 Bay retains its ID, facility, masks, position,
heading and native-definition hashes. New cities use a map-pinned identity; new
bays use a versioned contract. Historical quotes, prices, receipts, locks and
strict exact-level recovery remain unchanged. Read-only menu hints identify
unfinished operations at other/original factory identities without migrating
orders, relaxing recovery or changing the local pending-operation policy.

Fractional Fuel credit remains keyed by nation and vehicle, so moving the same
vehicle between eligible factories does not reset or transfer its credit to
another vehicle. This batch does not change Fuel conversion or pricing.

## Source qualification

A read-only audit inspected 450 chunks across 16 regions in the previously
verified pristine authored R7 source. All 17 source-world files still matched
its original inventory. All 48 factory envelopes fit the actual starter template.
Each roadside service rectangle contains 600 AIR cells over 120 gray-concrete
floor cells. These palette counts are static inspection, not native driving proof.

The audit found why translating City A's plot-1 source is invalid: plot 3 differs
in 498 outside-factory cells and plot 4 in 141. Normal R5 public preparation also
changes 165/160/165 envelope cells in plots 1/3/4, away from the internal service
lane and roadside driving columns. Those exact changes are now recognized only
with matching revision history; arbitrary changed blocks remain refused.

The four starting cities match the original outside-factory source. The twelve
nonstarting cities have 120/93/96 raw R7 differences in plots 1/3/4 before founding.
Normal founding places their complete starter-settlement source. This is not
permission to bypass founding, import a city or treat raw pre-founding terrain as
a completed factory. Actual no-grant cumulative founding/progression remains a
separate full-server gate.

## Validation

**166/166 native phase executions passed, zero native test failures**, in fourteen
sequential server processes on the exact source above. Every namespace began in
its own new labeled directory; only each restart pair shared its seed/verify world.
Java 17.0.21+9, Gradle 8.8, Minecraft 1.20.1 and Forge 47.4.10 were used offline.

| Namespace | Passed / executed |
| --- | ---: |
| `catancraft_vehicle52` — sites, identities, construction | 7 / 7 |
| `catancraft_vehicle52_public` — public revision/source | 6 / 6 |
| `catancraft_factory50` — retained factory safeguards | 3 / 3 |
| `catancraft_vehicle52_native` — actual-coordinate vehicle cases | 96 / 96 |
| `catancraft_vehicle50_ui` — retained UI rules | 3 / 3 |
| `catancraft_vehicle46` — delivery accounting/recovery | 18 / 18 |
| `catancraft_vehicle45` — fixed-bay rules | 8 / 8 |
| `catancraft_shop51` — all-city shop regression | 9 / 9 |
| `catancraft_service50` — native purchase/refuel records | 8 / 8 |
| `catancraft_vehicle47` — native vehicle persistence | 4 / 4 |
| `catancraft_service50_restart`, seed + verify | 2 / 2 |
| `catancraft_vehicle47_restart`, seed + verify | 2 / 2 |

The seven site tests contain 48 counter bindings, 2,496 source-position checks,
15 independently reconstructed legacy A compounds, 768 identity-change refusals,
240 factory-tier inspections and 480 construction/context refusals. These are
internal cases, not additional distinct GameTests. Positive construction checks
use detached ledgers and sparse native blocks; FakePlayer counter access refuses.

The 96 vehicle cases cover every city/plot with both supported vehicles. Each
constructs the production Bay, boards and exits every seat through the native
packet, drives under normal server ticks and native movement inputs, brakes,
then exits the driver. There are 720 verified exits and 1,766,400 per-case source
cell checks. Each case retains identical block/entity source fingerprints, with
no post-spawn pose writes. Both A plot-1 cases independently reconstruct and
compare the original Bay. These are actual-coordinate, composed-source fixtures,
not a copied authored-world campaign or positive authenticated counter purchase.

The native vehicle/service suites use the existing pinned 15-artifact published
TaCZ/SBW combination and unchanged SimpleBedrockModel 2.6.2 selection. The combined
init script now honors labeled fresh-run paths and verifies the selected working
directory. This is not a claim about the owner's installed add-ons or client.
Restart suites exercise their retained synthetic transaction-authority fixtures
and confirm original native entities, locks, energy and payment evidence across
JVMs. A same-vehicle authenticated refill at two factories remains an owner/client
check; the unchanged nation/vehicle meter keying is source evidence here.

The initial compile failure (a test helper missing its `IOException` declaration)
is retained. It was corrected before the source commit; the subsequent compile
and all native phases passed. Earlier whole release/shop/Python runs are retained
history and are not added to this count.

The full offline build succeeded in 19 seconds. Gradle `test NO-SOURCE` is not
counted as a test pass. The development JAR retains the previous
`catancraft-0.1.50-vehicle-service.jar` filename; it is **not the delivered 050 JAR**
and is not offered for installation. Size: 4,041,017 bytes. SHA-256:
`b3f03c81743c127517216b2e4ee54a62f8f3f6a3a796decf77b3c88da0cc8adf`.

Validation archive SHA-256:
`7e23eb10d77c6d1e209f1229218d8cc06ac16cd1cfda5da2a55c25a771bf62b7`.
Native proofs archive SHA-256:
`664488e79b86fc52a4db8196df657200b6d4ce3749a6bd06f1a20488b6827042`.

[Machine evidence](evidence/vehicle52_validation.json),
[raw validation logs and runner snapshots](evidence/vehicle52_validation_evidence.zip),
[96 complete native vehicle proofs](evidence/vehicle52_native_proofs.zip), and
[pristine-world geometry audit](evidence/vehicle52_geometry_audit.json) record exact
scope, inputs and hashes. Archive inventories were independently read back and
verified. All `src/main/resources` bytes still match delivered 050; the original
050 owner ZIP is unchanged. Both 033 source/documentation commits remain ancestors.

## Remaining release gates

- Expand and qualify wall providers across all eligible cities without changing
  accepted A North Reinforced / East–South–West Basic and their mixed connections.
- Complete shared world startup/asset qualification, the matching mod/runtime
  package and actual cumulative no-grant progression in fresh authored runs.
- Perform one focused authenticated-client solo checklist, starting in the owner's
  current City D, after the combined build is ready. Static/synthetic/native
  fixtures do not establish that client interaction or full campaign acceptance.
- SEC-01 crowbar remains **UNRESOLVED / HIGH PRIORITY** pending installed-pack
  acceptance. TaCZ-only handheld guns and deferred SBW handheld explosives remain.

No live world or installed mods were accessed or changed. No reset, manual import,
accepted-purchase replay, multiplayer test or Blender assignment was added.
