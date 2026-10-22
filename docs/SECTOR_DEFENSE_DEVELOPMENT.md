# Compass sectors: engineering checkpoint, not approved wall art

This follows the completed 0.1.28 release. It retains producer, house, entity and
historical socket transaction implementations. Development builds use the distinct
`0.1.29-sector-dev` version; they do not replace the preserved release archive.

Player commands now address `north`, `east`, `south`, `west`, always world compass
North=-Z, East=+X, South=+Z, West=-X. City rotation never relabels a side.
`/nation defense list <city>` explains availability. `quote`, `buy`, `status`,
`resume`, `rollback` take `<city> <direction>`; mutations require `confirm`.
No production sector contracts are installed yet, so these commands explicitly
refuse purchases. No fixture price or old wall geometry is silently substituted.
Historical modules have recovery-only access:
`/nation defense legacy status|resume|rollback <city> <socket> [confirm]`.
The old underlying transaction code and journals remain available and unchanged
except for the shared city reservation check. Ownership still requires the
owning nation's leader. Old socket receipts do not become sector levels.

Independent implementation:

- `SectorTransaction` handles one exact whole-side source/target plan, one debit,
  durable journal and completed level/receipt, interruption, duplicate requests,
  and reverse rollback before historical-cost refund. It is exercised using
  labeled isolated native fixtures, not approved city wall art.
- `SectorContracts` snapshots immutable plan/revisions, owner quote, exact price,
  world/city, adjacent levels AND receipts, road read masks and explicit corner
  table. It rejects stale quotes and conflicting partitions. This policy layer
  does not choose an artwork-specific level compatibility matrix or assign
  corner geometry; those require the delivered reviewed contract.
- `DefenseCityReservations` refuses competing pending socket/sector journals in
  the same city, including physical-complete operations whose ledger is pending.

`SectorService` now binds those contracts to owner-authorized server commands,
one saved level/receipt/revision per absolute side, quote expiration and current
neighbor/corner identity. Quotes do not spend or reserve; confirmation preflights
the entire plan before durable mutation. Recovery reconstructs the recorded plan
and cost without consulting a changed price catalog. Retained paid source proof
is required even after an upgrade rollback. Terminal status/duplicate requests
allow legitimate later neighbor purchases; writes and rollback keep their exact
historical neighbor constraints. `SectorRecovery` discovers existing approved
pending journals at fresh-demo server start; it never starts new purchases.

`FreshDemoAuthorization` binds seed, level name, dimension, world UUID, canonical
installation path and map hash separately from historical R7 maintenance. Invalid
fresh markers fail closed. Ordinary producer/prestige/civic paths accept this
binding; complete authored-world progression still needs the actual demo input.

Before production sector activation, register reviewed immutable geometry and
corner/road partition contracts, explicit adjacent-level transitions and approved
sector prices. The production resolver deliberately has no entries. Fixtures are
not a player catalog, and no hardcoded P1 width/component count is assumed there.

P1 is now available and verified, with four native prototype tests; see
[DEF02_P1_FIXTURE.md](DEF02_P1_FIXTURE.md). Its entrance/footprint is unapproved.
The clean authored-world payload and reviewed bootstrap/runtime input closure are
still missing; see [INT02_DEMO_PACKAGE.md](INT02_DEMO_PACKAGE.md). Official SBW
0.8.9.2 artifacts were separately audited without reading installed mods, but
weapon/modpack acceptance and SEC-01 closure remain pending. No siege or NPC work.
