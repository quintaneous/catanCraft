# CatanCraft v0.1.25 — TH4/TH5 R2 integration candidate
Date: 2026-10-15. Backed-up existing R7 solo test world ONLY; no live approval.
Native CI and owner acceptance must be verified against the delivered artifact.

## Changes
Bundled immutable TH4/TH5 R1 sources are checked by schematic SHA-256. R2 is
expanded losslessly from the reviewed Blender changes and verified by the canonical
SHA-256 of ALL 229125 decoded cells of the delivered R2 schematic. This is not
a reauthored/approximate building. Original R2 schematic hashes are provenance;
canonical voxel hashes certify the exact runtime result. No loose R2 override.
The exact 584-cell R1-to-R2 difference repairs rear headroom, upper landings and
finished guardrails without changing the manager, paid level or city architecture.
Operator maintenance commands:
- /catan townhall status a
- /catan townhall repair a confirm
- /catan townhall resume a confirm
- /catan townhall rollback a confirm

Status performs full-envelope validation without writes. Repair is zero-cost.
Repair journals preserve exact before/after states, manager data, identity, source
hashes and original purchase-journal hashes, with checksums and atomic read-back
verified persistence. A repeated committed repair is a verified no-op. Partial
owned before/after mixtures may resume or roll back; unknown third states,
ownership/identity drift, liquids, inventory-bearing blocks, occupants, missing
history or corrupt journals refuse. No automatic startup mutation.

Rollback restores only this repair's actual 584-cell preimage, not a prior Town
Hall tier. It never refunds/recharges purchases. History remains after rollback.
Reapplying a completed rollback is deliberately blocked pending a new revision.

Future TH3 -> TH4/R2 and TH4/R1 or R2 -> TH5/R2 purchases use corrected assets and
retain the existing one-charge prestige path. An incomplete repair blocks a new
prestige purchase. General recovery of older prestige PURCHASE journals is still
not implemented. This repair service cannot reconcile those purchase journals.

The old external schematic files, original purchase journals and civic data are
not overwritten. Only the JAR needs replacing on test server and client.
Existing 40-asset registry count stays unchanged.

## Scope and limits
Native tests exercise actual Minecraft block states/collision shapes and actual
repair journal file I/O, including simulated interruptions and saved-data reload.
They do not constitute a process/power-loss test, the user's full modpack test or
approval of every visual finish. The full gameplay-world environment guard remains
R7-only; no terrain/border, resource producer art, R5 streetscape or defense feature
is enabled by this release. The orientation pavilion is deferred. Crowbar issue #6
remains unresolved high priority; do not distribute crowbars to ordinary players.
