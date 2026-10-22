# Initial compatible R4-A / R6 defenses

**Historical 0.1.28 socket implementation.** The owner's 2026-10-18 direction is recorded in [INTEGRATION_PRIORITIES.md](INTEGRATION_PRIORITIES.md). The per-socket commands and prices below are not the newly requested player-facing compass-sector system. Retain safe transactions, histories and test input; R4-A/R6 artwork is not visually accepted as the final sector design. Sector prices, grouped accounting, levels, corners and road ownership require the revised implementation contract.

The immutable defense registry contains the reviewed 37-piece R4-A assembly and 17 R6 successors for each of cities A–P (864 individually selectable socket plans). It is not an automatic city installation. Gate controllers, gate-dependent wings, trenches, rejected large bunkers/adapters, siege rollout and unapproved terrain work are excluded.

`tools/prepare_defense_inputs.py <audited-inputs-root> <output>` creates the compact registry from the verified R4-A/R6 packages. Only sparse writes, exact support/clearance/road reads, module IDs and dependency/exclusion edges are included. No world saves, screenshots or authoring archives are bundled. The compressed resource digest is pinned in `DefenseRevisions`. Coordinates and target block properties are already world-rotated and never rotated again.

## Approved provisional purchase schedule

| Explicit module IDs | Money | Materials |
| --- | ---: | --- |
| `r4a_wall_6`, `r4a_wall_7`, `r4a_wall_end_cap`, `r4a_checkpoint_wing`, `r6_low_wall_8` | 500 | wood 5, stone 15 |
| `r4a_wall_14`, `r4a_wall_straight_16`, `r6_low_wall_16` | 1,000 | wood 10, stone 30 |
| `r4a_wall_corner` | 750 | wood 10, stone 20 |
| `r4a_checkpoint` | 2,000 | wood 20, stone 40 |
| `r4a_guard_tower` | 3,000 | wood 20, stone 50, steel 10 |
| `r6_compact_casemate` | 4,000 | concrete 40, steel 15 |

The catalog never enables a socket: corner alternatives remain unavailable when absent from the selected registry. Short authored connector-wall and checkpoint-wing sockets use the small wall band; wall14 uses the long wall band. Separately authored connectors are separate wall purchases. Bundled footing/support cells add no extra charge. Unknown module IDs have no price and refuse; they never become free. No upkeep or technology system is added.

## Transaction and preservation

`DefenseService.operation(server, city, socket, action, approvedCost)` provides the main-thread backend. `/nation defense list <city>` lists supported sockets. The owning nation leader uses `quote <city> <socket>` to see every cost, then `buy <city> <socket> confirm` within 60 seconds. `status`, `resume ... confirm` and `rollback ... confirm` expose durable recovery. Ownership and the quoted catalog cost are rechecked at confirmation. Production calls can pass `null` to use `DefenseCostCatalog`; native transaction fixtures inject known test costs.

A sealed actual-before journal is persisted before payment or blocks change. Payment is reserved once in the same saved-data ledger as its receipt, then exact sparse writes are applied and the owned socket revision is committed. Recovery uses that journal's historical price, strictly rejecting unknown fields, wrong types and invalid amounts. A pending transaction holds the city reservation; incompatible or unrelated new socket operations wait for explicit recovery. Reentrant operations refuse.

Loaded blocks and entity sections are required. Entity geometry uses the shared construction-hazard guard, including actual remaining reverse order for rollback, with entity preservation checked again after callbacks and saves. Ownership, write bounds and dependency/exclusion requirements are rechecked before reservation, every write and settlement. Actual support/road/clearance states remain exact read-only conditions. Compatible already-owned modules can occupy overlapping preserve-air envelopes only at their exact registered after-states; their existing inventories/NBT are read back unchanged.

Duplicate owned purchases verify the target and charge/write nothing. Existing barrel contents are retained on ordinary status/no-op. Rollback refuses modified block entity NBT rather than deleting contents. A successful rollback restores actual before-states and refunds once. A later fresh purchase creates a new UUID journal and retains the old history. No existing producer, house, Town Hall, resources, ownership or historical journals are rewritten.

## Validation scope

`DefenseTests` uses namespace `catancraft_defense`, isolated disposable native fixtures and injected costs. Tests cover all 864 plans and explicit catalog coverage, A/I wall/casemate/half-wall roundtrips, duplicate safety, interrupted reserved-payment application/resume, rollback drift refusal, source/support/authorization refusal, checkpoint/wing dependencies and compatible read overlaps, and separate insufficient-money and insufficient-material refusals. Actual execution results belong in the combined integration status and preserved central logs; this document does not assert a test pass.

Actual TaCZ aperture use and Superb Warfare pickup/LAV clearance remain narrow in-game acceptance checks. Proxy bounds and authored geometry do not certify those mods. No multiplayer or live-world testing is authorized. Crowbar exploit #6 remains unresolved/high priority.
