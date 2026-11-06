# SIE-01 / SIE-04 — objective registration and presence, 0.1.37

2026-11-06. Continues the validated 0.1.36 branch and its approved provisional
outer-point / Town-Hall sequence and defender victory rules. This bounded step
adds explicit immutable objective profiles and a read-only server-position
adapter. It does not complete SIE-02 damage snapshots or SIE-05 restoration.

## Registration and observation

`SiegeObjectives37.Registry` requires explicit profiles, world identity and a
server level. Each city has two nonoverlapping block-aligned, half-open boxes
with an explicit revision and matching canonical dimension. Extents are limited
to 64 x 32 x 64 blocks per objective; the catalog contains at most 64 cities.
These are implementation bounds, not approved objective sizes or locations.
Every X/Z column must belong to the canonical territory under its existing
block-center containment rule, including concave boundaries. Build-height,
unknown city, duplicate city and cross-city objective overlaps refuse.

A binding freezes its full profile and siege contract in one server lifetime.
Observations recheck server/level identity, world directory, seed, canonical
boundary/dimension, participating nations and current defender ownership.
Changed ownership or missing nations refuse before producing capture evidence.
The immutable catalog has no reload/mutation API. No production catalog is
registered: synthetic fixture locations are not proposed City A capture zones.
The caller-supplied world label is a binding label, not authenticated authority.

The production entry point takes its roster from the current server PlayerList,
checks exact UUID-to-player identity and current connection state, and reads
current CatanSavedData membership. It accepts no caller-supplied nation or
presence flags. Multiple nation memberships or duplicate roster UUIDs refuse.
Only the active stage's box is sampled, using the player's current feet position:
minimum faces included, maximum faces excluded on all axes. Nonfinite positions
do not count. No chunk loading, entity insertion or block scan is needed.

Provisional presence semantics for the unregistered adapter:

- Living Survival/Adventure players may capture or contest; Creative, Spectator,
  dead, removed, other-dimension and outside-zone players cannot.
- Only attacker/defender nation members count in the zone. Third parties cannot
  capture or contest. Membership changes are reflected by the next sample.
- Online eligibility and capture presence are separate. A connected participant
  anywhere on the server counts as online even while dead or in Creative mode;
  this follows the current requirement of a nation member being online, without
  silently inventing a spawn/respawn rule. Leaving the roster is not surrender.
- Defender presence feeds the existing pause-capture / keep-clock-running rule.
  The outer-to-Hall transition changes the sampled box without resetting time.

These precise player-mode and feet-position semantics are implementation
recommendations for playtesting, not owner acceptance of a deployed siege.
Durations and empty-zone policy still require explicit inputs. This step adds
no declaration or surrender permissions, player command, visible marker or HUD.

## Integration boundary

Reports include the full profile, contract, objective, source elapsed cursor and
server game time. They are read-only observations, not persisted transitions or
unforgeable/replay-proof tokens. The future controller must consume a fresh
report in the same serialized server tick against its CURRENT durable snapshot,
ensure one tick per session, and persist before publication/settlement. The
existing rules evaluator checks only the supplied state, not global storage.
A future durable registration must save the original full geometry and rules;
it must never resolve an old siege against changed current profiles on restart.
This process-local binding is not process-restart validation or rollback proof.

No automatic start, damage permission, war snapshot, restoration, ownership or
vehicle transfer, gate geometry/overlay, price or live timer is introduced.
Production protection and all prior purchase/recovery behavior remain unchanged.
Actual City A zones, durations, empty-zone choice, declaration/surrender roles,
disconnect/downtime policy, settlement and restoration remain before deployment.

## Validation scope

New native cases use actual Forge FakePlayer positions, modes and health with
detached SavedData. A named fixture-only entry point models the connected roster;
it requires GameTestServer, the exact controlled objectives37 world path/marker,
and data distinct from the real world SavedData. It never adds a fake player to
PlayerList or authenticates a client. The production connection predicate is
checked separately to reject detached fake players. These are isolated native
simulations, not multiplayer or real connected-client acceptance.

Exact-source build/results will be recorded in the checkpoint after execution.
Retained siege rules and baseline tests are selected alongside the new cases.
No live world, installed mods, accepted purchase replay, imports, reset or Blender
work. Accepted North Reinforced / East-South-West Basic walls remain unchanged.
SEC-01 crowbar remains **UNRESOLVED / HIGH PRIORITY** at installed-runtime scope.
