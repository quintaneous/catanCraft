# DEF-03: delayed natural support blocker

Historical checkpoint. Current evidence: [DEF-03 validation](DEF03_VALIDATION_20261019.md).
Use the single [City A solo checklist](CITY_A_FRESH_SOLO_CHECKLIST.md) for the eventual matching package.

P2's immutable supplied read context expects grass below parts of the opaque
wall. Native placement, upgrade and immediate rollback succeed, but ordinary
random ticks change covered grass to dirt. The delayed assertion correctly
fails. The diagnostic log records exact coordinates and states; management-book
NBT is unchanged. Production `SectorNorthP2.contract` explicitly refuses until
this is resolved, independently of price and fresh-world authorization.

Do not disable random ticks, remove the delayed assertion, silently accept any
solid replacement, change frozen schematic bytes or repaint terrain on rollback.
A generic grass/dirt exception would also change the meaning of old sealed
journals and is therefore inappropriate.

A candidate fix needs a versioned policy recorded in the durable spec/identity,
with an exact whitelist derived from original grass supports directly beneath
approved opaque P2 blocks. Fresh placement must still recognize the supplied
source. Only later owned/pending operations may recognize the original grass or
vanilla dirt at those whitelisted positions, without fluids or block entities.
An upgrade must inherit the paid lower-tier proof. Recovery must use its frozen
historical policy; rollback must not manufacture grass or change neighboring
terrain. All other reads stay exact. This is a proposed implementation contract,
not code already implemented or a new claim of owner approval.

Required tests include natural ticks before upgrade; actual ledger/journal reload
before resume and rollback; mixed grass/dirt supports after partial installation;
foreign replacements, missing support and off-whitelist dirt refusal; duplicate
payment/refund safety; retained pre-policy histories; preserved NBT and roads.
The exact engine and parser boundary must be reviewed before lifting the gate.

Performance also needs further work: the diagnostic full-size synchronous
purchase/upgrade/reverse sequence emitted roughly 45 seconds of server lag in
this cloud runtime. Exact world and journal guards currently remain active before
each write. A passing static or immediate placement test would not establish
acceptable gameplay latency. No timing or safety claim should be inferred from
asset approval.
