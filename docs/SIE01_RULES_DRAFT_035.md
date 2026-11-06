# DEF-06 / SIE-01 — gates and siege rules draft, 2026-11-05

Status: **approved provisional core rules; gate and siege foundations remain unregistered**. This is
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
  capture timings and disconnect handling remain undecided.
- Basic contest behavior accepted: capture progresses while attackers are present
  and pauses when defenders enter the capture zone. Exact zone/presence definitions
  and behavior when nobody is present remain undecided.
- On 2026-11-05 the owner approved the recommended defender-win mechanics as
  provisional playtest rules: one overall clock across both stages, no stage
  reset, contested capture pauses without pausing that clock, and defender victory
  on timeout or explicit attacker surrender. The owner permits later tuning.
  Numeric durations were not proposed or chosen; keep them explicit and adjustable.

## Approved objective sequence

1. Attackers capture the designated outer point.
2. That unlocks the Town Hall capture phase; attackers must then hold the Town Hall.
3. Completing that objective wins the siege and triggers the agreed capture
   settlement, whose exact vehicle eligibility and restoration rules remain TBD.

Only one objective is active at a time. This gives the defenses a role without
requiring simultaneous captures. Breaching a gate alone does not complete the
two-stage objective. Exact objective geometry, stage-progress retention/recapture,
precise presence/contesting definitions and durations still need a rules contract.
The approved sequence does not enable siege damage or alter the accepted city.

## Approved provisional defender victory rules

Use one overall attack timer shared by both objective stages. If attackers
have not completed the Town Hall capture when it expires, defenders win, whether
the attackers are still at the outer point or have reached the Town Hall.
Capturing the outer point unlocks the next stage without resetting or extending
the overall timer. Contested capture pauses objective progress without pausing
this overall timer. This gives delaying and counterattacking a concrete
purpose and prevents an indefinitely contested siege.

Explicit attacker withdrawal/surrender ends the siege as a defender win. Who may surrender and when it is permitted still need agreement.
A momentary team wipe, empty objective or disconnect should not be treated as
surrender implicitly; respawn and disconnect rules remain separate decisions.

On a defender win, the city and its purchased vehicles stay with their existing
owners. The siege ends and temporary damage is handled by the eventual approved
restoration contract. A repeat-attack cooldown is proposed to prevent immediate
restart harassment; no duration or eligibility rule is assigned here.

Still undecided: total siege duration, timer start/preparation, objective hold
times, overtime at expiry, simultaneous completion/expiry ordering, attacker
respawns, disconnect/server-restart timing, cooldown and surrender permissions.
Approval covers the mechanisms stated above, not unchosen numerical durations,
cooldown or unrelated capture/restore policies. The isolated 0.1.36 rule evaluator
implements these mechanisms with required frozen timing inputs; see
[SIE-01 / SIE-04 implementation contract](SIEGE_RULES_036_CONTRACT.md). It is not
registered in a live server scheduler or player command.

## Rules still requiring a decision

With the objective selected, settle these together before enabling combat:

- Declaration: who may start, how the defender is notified, preparation time,
  cancellation, simultaneous sieges, cooldown and any cost.
- Contest and victory: exact objective locations/presence, empty-zone behavior,
  progress decay, hold/overall duration, expiry
  ordering, attacker defeat, disconnect and server restart behavior.
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

## 0.1.37 bounded presence implementation, 2026-11-06

The unregistered server adapter now binds explicit immutable objective boxes and
reads active-zone presence from server-owned player positions and live nation
membership. Living Survival/Adventure players count; Creative/Spectator/dead
players do not capture, while online status is recorded separately. Feet use
minimum-inclusive / maximum-exclusive box faces. These are provisional
implementation recommendations, not acceptance of live geometry. No production
profile, active siege service or player controls are registered. See the
[0.1.37 contract](SIEGE_OBJECTIVES_037_CONTRACT.md) for binding, fixture and durable
controller limits. Actual locations/durations and safe restoration remain pending.
