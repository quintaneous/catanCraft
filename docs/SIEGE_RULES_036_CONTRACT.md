# SIE-01 / SIE-04 — provisional objective rules and 0.1.36 foundation

2026-11-05. The owner accepted the recommended mechanics and permits playtest
tuning. The underlying accepted city, purchases and 0.1.35 gate/security work are
preserved. [Living design decisions](SIE01_RULES_DRAFT_035.md).

## Approved playtest behavior

- Both nations need an online player to begin a siege.
- Capture the outer point, then hold the Town Hall; one objective is active.
- Attackers progress when present without defenders contesting the active zone.
  Defender presence pauses capture progress while the overall siege clock runs.
- Both stages share one overall timer. Completing the outer point does not reset
  or extend it. Defenders win if attackers have not captured the Town Hall before
  expiry, or if the attacking nation explicitly surrenders.
- A defender win preserves existing city and purchased vehicle ownership. An
  attacker win is intended to transfer the city and eligible local purchased
  vehicles, under the still-unfinished settlement/eligibility contract.

No numeric duration was proposed or approved. The implementation requires total,
outer-hold and Town-Hall-hold durations explicitly. Test numbers are synthetic;
there is no production default or automatically started siege.

## Implemented boundary

`SiegeRules36` is an immutable objective-rule evaluator, unregistered in
production. It consumes a server-provided observation for one active objective
and one running-siege tick, and returns a new state. It neither reads the live
world nor updates it. No command, tick subscriber, damage permission, payments,
vehicle transfer, sector ownership or restoration is installed.

Each contract freezes a siege UUID, world identity, city, distinct attacker and
defender nations, rules revision and all timing/policy inputs. Another contract
cannot be loaded over it. Future tuning applies to new contracts; existing
sessions keep their original rules. Durations must be positive and the combined
capture holds must fit strictly before the overall deadline.

One observed tick cannot count toward both stages. The caller supplies the exact
expected elapsed-tick cursor and active objective; stale/skipped cursors and
observations for the wrong objective refuse. This checks the supplied snapshot;
the future service must serialize transitions against its current durable state
before publishing or settling a result. Current presence is never applied
to an inferred unobserved time interval. At the exact deadline, timeout takes
precedence over capture credit: there is no implicit overtime. This is the
explicit first implementation of capture **before** expiry, subject to playtest
tuning through a new rules revision.

Unoccupied-zone behavior was not chosen. The contract therefore requires an
explicit `HOLD` or `RESET` policy instead of silently selecting one. Both are
exercised with test-only contracts. A defending occupant always pauses capture;
a reset-on-empty policy does not erase the already-completed outer stage.
Outer-point recapture is not implemented. Neither an empty zone nor a player
leaving the server is automatically surrender.

The surrender evaluator checks the requesting nation is the attacker; the future
command/service MUST additionally authorize the actual player/role. The core
never treats a caller-supplied nation UUID as authenticated player permission.
Finished results are immutable under further tick or surrender processing. They
report the winning nation and do not grant a settlement, reward or world write.

## Persistence and remaining contracts

The strict NBT codec round-trips state only with the independently supplied
original authorized contract. It rejects extra/missing fields, wrong tag types,
unknown schemas/statuses, changed identities/rules and impossible counters. It
is **not** an authenticated durable siege journal, proof against rollback, or a
full process-restart/damage-restoration implementation. A future service needs
session ownership, durable intent/result receipts, guarded original snapshots,
exactly-once settlement and recovery before enabling damage or transfer.

No login-loss, offline time or server-downtime policy is selected by a logical
running tick. Declaration/preparation, precise zone/presence definitions, actor
permissions, total/hold durations, empty policy, disconnect/respawn, cooldown,
vehicle eligibility, restoration and actual gate overlay/clearance remain
separate integration work. Existing global TaCZ/SBW protection stays enabled.
SEC-01 remains unresolved/high priority at its recorded installed-runtime scope.

## Validation and retained initial failure

Initial source `93b6eb32c2858db0bc7f4c64288216ef79d77697` passed all 16 new
objective-rule cases and all 50 retained baseline cases. In workflow
`42128183273`, the retained gate suite passed
11/12: its obstructed-close fixture timed out waiting for entity chunk residency
before running its assertions. The failed raw artifact `13460859239` is retained
(SHA256 `cb51d9cc0c78c9d8b60818f552b11c1c6baf75e9c19d3a70e9f0402e35e18eee`).
The initial run therefore executed 78 cases: 77 passed and 1 failed. Its
[raw failed native log](evidence/siege36_initial_gate_failure.log) and
[verified result summary](evidence/siege36_initial_validation.json) are preserved.
The fixture now uses the existing paid-recovery 4,000-attempt readiness budget,
a 5,000-tick outer timeout and per-chunk/timing diagnostics, while still requiring
actual chunk/entity readiness. No gate or siege safety predicate changes.
Final source `d1ee94ac7f6ad619118805647d9380460768f5d0` passed all **78/78**
selected cases in workflow `42128969056`: 16 siege, 12 gate and 50 baseline.
The new diagnostics directly observed one fixture still waiting on entity
residency at 400 attempts / 531 ms, then ready at 1,415 attempts / 1,161 ms.
This demonstrates that the old 400-attempt budget can expire before a normally
loading fixture is ready. It does not identify the exact first failure's missing
chunk or underlying I/O cause. No production safety condition was relaxed.
[Exact source, hashes and preserved evidence](SIEGE_RULES_036_CHECKPOINT.md). The rule cases are simulations inside Forge GameTest,
not multiplayer, connected-client or live city acceptance. A compressed NBT
readback is not a process-restart proof. Final evidence will record actual counts,
source/JAR hashes and any failed attempts separately.

No live world or installed mods, reset, imports, repeated accepted purchases,
multiplayer or Blender assignment. Production features stay gated; there is no
new in-game checklist for this foundation checkpoint.
