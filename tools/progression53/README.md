# Native authored progression 053

`Progression53Tests` runs only in the explicitly staged authored GameTest world.
It requires the fresh-world authorization created by the verified input stager;
it never creates that authorization or modifies the immutable input. A seed run
refuses existing nations, owned cities, purchases, or its own earlier evidence.
A verify phase reopens the same completed seed to check real process persistence.
Do not run seed again on that run; create another isolated copy instead.

Required JVM properties (passed by the authored test Gradle run):

- `catancraft.progression53Source`: exact 40-character source commit.
- `catancraft.progression53Layout`: integer 0–8.
- `catancraft.progression53Phase`: `seed` or `verify`.

Required `config/catancraft/progression53-input.json` fields: `schema: 1`,
`purpose: "verified-clean-r7-progression53"`, `sourceCommit`, `layout`,
`worldFolder`, `levelName`, `seed`, `mapSha256`, `authoredWorldSha256`.
The stager must independently verify the pristine world/config/assets. This JSON
is a binding to that reviewed input, not a substitute for source verification.

Each layout starts normally in D, expands through actual adjacent neutral claims,
waits for automatic public/house preparation and earns the cost of every purchase
through the real scheduled economy path. All acquisition uses ordinary registered
nation commands. Producer targets are managed through the existing normal command
so downstream factories pause instead of consuming every input needed for upgrades.
There are no money/material grants, direct paid-level/building seeding, target block
stamps, repair imports, or funds from another test nation.

The scheduler advances logical 15-minute production boundaries without waiting
15 real minutes; actual native ticks and artificial elapsed economy minutes are
recorded separately. This establishes acquisition order and accounting, **not**
real-time pacing, real-player optimization, or balance acceptance. The command
actor is a Forge FakePlayer and supplies no connected-client UI acceptance.

Each of nine new worlds has all 16 cities, three industries per city, both civics,
Town Halls and producers progressing to 5. The nine rotations jointly cover all
16 × 3 × 9 = 432 industry/site combinations and every paid industry level 1–5.
Every city checkpoint rechecks previously applicable physical structures, local
management/shop/vehicle construction bindings, exact retained purchase histories,
read-only compass-wall quotes/source geometry and SavedData roundtrips. Seed ends
with a real disk save/readback; verify repeats every city's final physical checks
and requires the balances, paid state, receipts, histories and books to persist.
Dedicated gun/vehicle delivery, crowd/obstruction/security, walls purchasing and
fault-injection suites remain separate obligations.

Evidence appears below the disposable world's `data/progression53-layout-N/`:
`seed.json`, immutable sealed `restart.dat`, and later `verify.json`. The strict
aggregate checker requires all nine distinct layouts and matching process-restart
reports. It does not relabel missing or failed runs, nor execute any Minecraft
process itself. Preserve the original native logs and their process exits alongside
these reports before citing an executed test pass.
