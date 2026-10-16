# R5 public-space integration — v0.1.27 candidate

Base: owner-accepted v0.1.26, gameplay source 5a7b536ed5af86c9ed884d0f70d31664da5af1f7;
rechecked branch head a06288c8edc5a3704d84ebc826ffb18495392718 (two documentation-only commits).

## Scope

Ten remaining public-space groups, 1,661 exact authored changed cells per city and
18,766 full read-context cells: east isolated tree cleanup, two lamp bases, two arrival
courts, shared residential frontage/garden edges, two factory-gap pedestrian links,
and rear worker garden. These local cells were independently compared against all
16 original world-space R5 delta sets, R4 reservations, civic envelopes and original
protected zones. See tests/r5_public_asset_audit.json. No world was inspected here.

The source is the immutable original R5 archive, SHA-256
679b9d94d2f96175705e2e85f2e36aa0146cd8dd28a71826e25998c6c9755521.
contract.b64 is a gzip/base64 encoding of lossless JSON read-runs and exact target
cells, not an approximate geometry generator or a schematic paste. Runtime checks
its decoded SHA, registry states, protected locations and exact read/write counts.
All directional states and coordinates rotate together from local city coordinates.

Commercial, Logistics, their prerequisite groups, the accepted Town Hall and
producers are not written. The earlier front-public-park pilot and all six houses
are also excluded. House R1/R2 branch recognition is still pending. Corrected
producer exteriors have been requested from Blender but no new corrected assets
were available at the start of this integration. A separate Producer Exterior R2
handoff arrived during validation; those new assets are not included in this
candidate. Existing R1 art remains immutable.
R4-A/R6 defenses are not enabled; crowbar issue #6 remains unresolved/high priority.
R8-B is not an approved sixteen-territory world. Orientation pavilion is deferred.

## Transaction and recovery

/catan r5 public status <city>
/catan r5 public apply <city> confirm
/catan r5 public resume <city> confirm
/catan r5 public rollback <city> confirm

Operator-only, server-thread-only, same exact backed-up R7 development-world
folder, LevelName and existing authorization marker as v0.1.26. No new marker or
manual asset imports. Both orientations have native tests; owner acceptance still
starts in developed City A. Status never loads chunks or writes journals/blocks.
Apply loads only bounded affected chunks and validates the entire ten-feature set.
The service additionally refuses writes while any affected chunk's saved entities
are still loading; loaded blocks alone are not evidence of a clear occupancy query.
After this preflight refusal, wait near the city and check status before retrying.
All old/after states, full context, inventories/fluids and occupancy are checked.
Unknown mixed states are not adopted. One separate sealed journal is flushed before
any block writes; it carries world, owner, city, anchor, contract and actual before
states. Phases support resume and reverse rollback. Saves precede commit markers.
The journal itself is the cosmetic revision receipt; no purchase or economy data
is written. Current outside-mask states and actual neighbor NBT are checked around
each operation; existing purchase/producer/TH journals are never read as authority
or rewritten. Leaf distance is the only normalized transient property.

Restart discovery reports incomplete journals without starting a new migration.
Use status and the appropriate explicit recovery command for retained work. A
committed repeat validates all target context and performs zero writes and charge.
A rolled-back history stays terminal; do not delete it to force another migration.
No forced-crash or process-power-loss guarantee is made from injected tests.

## Producer message cleanup

Only an explicitly typed occupancy refusal that occurs before a fresh journal
exists may say no upgrade blocks/payment changed and suggest retry. Existing
pending-journal errors retain recovery guidance. No string-matching heuristics,
weakened occupancy checks, changed costs or producer geometry changes.

## Focused owner acceptance (only after CI is verified)

Stop normally, back up the complete existing developed world in place, replace
only the CatanCraft JAR on client/server, leave all other mods/configuration and
historical journals alone. No reset or world import. On City A run status first,
then stand outside affected paths/gardens and apply once. Expected 1,661 changed
cells and zero charge. Check the new paths/gardens/entrance courts and lamp bases;
nearby buildings, books, inventories and paid levels remain unchanged. Repeat
apply once (zero writes) and save/stop/reload once for the new cosmetic journal.
Do not repeat accepted TH/producer/civic purchases or intentionally roll back a
successful result. Any refusal: preserve exact message and blocks/journals.

Native CI must pass 61 tests in two isolated runs/worlds: all retained 50 in the
original catancraft namespace, then ten streetscape tests plus the targeted producer
occupancy classification test in catancraft_public27. All 50 original tests and their
assertions are retained. ProducerTests fixture setup now keeps read/boundary chunks
loaded, and before-batch hooks remove stale drops left by native template clearing
before any intentional occupancy fixture is created. TownHallRepairTests likewise
keeps its full native fixture chunks loaded and logs expected-refusal reasons. Its
source setup clears prior inventory contents before replacing fixture blocks and
removes stale item drops inside that fixture after placement, before any deliberate
obstruction is created. This accounts for native template clearing after the batch
hook; it does not suppress drops generated during an operation. Assertions are not
relaxed. The files covering the original 20 tests, Town Hall gameplay source,
and all existing schematic assets remain unchanged. Separate worlds avoid inter-batch structure/entity cleanup
contaminating other fixtures. The new occupancy fixture explicitly holds its chunks
accessible and proves its entity is visible to the native query before testing denial.
Candidate publication must stop if either run fails.
Producer source setup now also clears earlier inventory contents before block replacement
and discards only stale setup item drops within its own bounds after template placement.
The corruption regression positively asserts that its intentional PREPARED interruption
was reached and its journal exists before testing bad identity and corrupt bytes. A generic
preflight failure can no longer masquerade as the injected interruption. No production
occupancy or recovery guard was relaxed by these fixture corrections.
Runtime owner acceptance is pending until the owner reports results.
