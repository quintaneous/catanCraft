# DEF-06 / SIE-01 — gates and siege rules draft, 2026-11-05

Status: **design discussion plus unregistered gate movement foundation**. This is
not an enabled siege system or an installed City A gate. The owner authorized
SEC-01 verification and functional gates/siege as the next two workstreams.
That supersedes the earlier batch's no-gates/no-siege scope restriction, while
leaving artwork, exact masks, prices and gameplay rules subject to their own
contracts. Accepted 0.1.34 and City A wall states are preserved.

## Decisions supplied by the owner

- Siege initiation requires at least one player from each of the two nations
  online. Disconnect handling after initiation is still undecided.
- Capture should transfer the city and eligible local purchased vehicles.
  Exact eligibility and transfer boundaries remain to be agreed.
- Objective approved on 2026-11-05: **capture an outer point, then hold the Town
  Hall**, with one active objective at a time. The owner replied "yeah I like
  that" to the two-stage recommendation. This approves the objective sequence;
  capture timings, contest rules and disconnect handling remain undecided.

## Approved objective sequence

1. Attackers capture the designated outer point.
2. That unlocks the Town Hall capture phase; attackers must then hold the Town Hall.
3. Completing that objective wins the siege and triggers the agreed capture
   settlement, whose exact vehicle eligibility and restoration rules remain TBD.

Only one objective is active at a time. This gives the defenses a role without
requiring simultaneous captures. Breaching a gate alone does not complete the
two-stage objective. Exact objective geometry, stage-progress retention/recapture,
attacker presence, contest behavior and durations still need a rules contract.
The approved sequence does not enable siege damage or alter the accepted city.

## Rules still requiring a decision

With the objective selected, settle these together before enabling combat:

- Declaration: who may start, how the defender is notified, preparation time,
  cancellation, simultaneous sieges, cooldown and any cost.
- Contest: exact objective locations, presence/contesting rules, progress decay,
  duration, attacker defeat, defender disconnect and server restart behavior.
- Damage: approved siege boundary, legal targets and weapons, and exact restoration
  policy. Existing global protection remains active until a scoped replacement
  is proven. Do not turn off global grief protection to simulate a battle zone.
- Capture: city/building/defense ownership transfer, historical receipts,
  recovery exclusion and whether any stockpiles/treasury change. City capture
  does not implicitly authorize taking national money or all vehicles.
- Vehicles: a possible rule is defender-owned purchased vehicles physically
  inside the frozen siege boundary at settlement, excluding unrelated nations'
  vehicles. This is a proposal; decide eligibility timing, occupied/absent
  vehicles, movement across the boundary and ownership receipt handling.
- Restore/settle: unique durable snapshot, frozen participants/area, restart-safe
  outcome and exactly-once ownership transfer. Restoration must not clone vehicles
  or undo unrelated progression.

No multiplayer or live-world tests are authorized. These rules can first be
validated with isolated native fixtures and simulated player identities.

## Gate contract and compatibility

The implemented foundation accepts an explicit immutable versioned OPEN/CLOSED
write mask plus exact read context. It supports cyclic movement, at most four
direct writes per step, full preflight, entity/support checks, guarded ownership,
retained intent identity and restart-compatible journal recovery. It does not
register a production gate, add a command/UI, charge a price or alter City A.
Callers must authorize the actor/contract, coordinate all city construction,
keep chunks/entities resident and provide durable world saves. Saves and
preparation are not a hard frame-time guarantee. The retained request history
is bounded to 4,096 transitions and then refuses until an explicit archival
contract exists. These are foundation limits, not a ready-to-install gate system.

North P2 contains a future reservation, not an approved moving leaf or clearance
contract. P3 also expects air at `(-724,84,-844)` and `(-724,84,-843)` inside that
future opening. A closed gate would conflict with current wall recovery. An
explicit shared gate/wall overlay or ordering contract must resolve this before
production registration; do not weaken the current strict air checks globally.
North caps, road floor/margins, mixed wall connections and the rear resource
corridor remain protected. No East/South/West gate geometry is approved here.
Pricing and interaction permissions are also undecided; missing prices do not
mean free gates. No Blender assignment is needed for this foundation step.

## Siege implementation boundary

The existing operator restoration helper uses a reusable whole-box template; it
is not a durable siege lifecycle. Its current box also omits some accepted North
wall cells. Do not broaden the combat area from wall bounds by inference. A real
siege needs independently approved spatial and restoration contracts, unique
snapshot identity, settlement receipts and tested crash recovery before damage
or ownership transfer can be enabled.
