# SIE-04 / SIE-05 — durable objective state, 0.1.38

2026-11-10. Continues validated 0.1.37. This batch saves objective state and
revalidates it after reopening. It does not claim complete siege restoration,
pre-war block/NBT snapshots (SIE-02), or ownership/vehicle settlement (SIE-04).

## Saved state and authority

`SiegeSession38` retains the full original siege contract and immutable objective
profile, including exact boxes and revisions, stage/progress, shared timer,
result, storage revision, observation epoch and last sampled server tick.
World directory, dimension, seed, city and file identity are bound too. The
original approved contract/profile must be supplied independently on reopening;
a journal does not authorize attacks or replace current rules with saved input.
Changed contracts, objective boxes or world bindings refuse without repair.

The production environment derives its level from the registered objective
binding and obtains presence through the 0.1.37 server observer. It requires a
trusted authorization callback on each operation; that callback must check the
actual actor/role and operation eligibility. A caller-supplied nation UUID alone
never authorizes surrender. Test environments model authorization and time;
they are not exposed to players or network packets. The existing observer still
checks live ownership, nation membership, positions and connections.

One retained journal and cooperative OS writer lock exist per city. A second
writer refuses. The lock file's identity is retained and rechecked. A retained
journal cannot be replaced by a new session, even after completion: repeated
siege scheduling and archival are a separate unfinished contract. No automatic
history deletion or compaction is introduced. This is an implementation limit,
not a proposed one-siege-per-city gameplay rule.

## Publication and failures

The existing sealed ProducerJournal storage primitive is reused unchanged:
checksum, bounded decoding, same-directory atomic replacement, forced file
writes, directory sync where supported and readback. The session additionally
limits captured journal size to 256 KiB, checks exact field types and schema,
and compares disk bytes with the handle's previously captured version before
writing. It rejects symlink paths and requires a stable lock-file identity.
Only a completed write and matching readback publish the new View to the caller.

A save failure before replacement leaves the prior complete state. A failure
after replacement may leave the next complete state. Either failure closes the
handle; reopening reads the actual disk state and requires recovery, avoiding
an assumed result or replayed tick. Tests inject both boundaries. This is not a
physical power-loss guarantee, hostile-filesystem protection or authenticated
history: a complete external rollback while closed cannot be detected by this
single journal. File locking is cooperative, not a defense against an operator
rewriting files. Existing filesystem/platform sync limits still apply; native
validation is Linux, not Windows/NTFS acceptance. Full-state synchronous writes
per accepted tick are correctness-first infrastructure, not a frame-time claim.

## Tick and recovery rules

New sessions require both nations online before their initial state is saved.
Every transition uses the current durable revision. Duplicate/stale requests
refuse instead of minting progress. A running tick must be the immediately
following observed server tick; no unobserved elapsed interval is inferred.
Repeated same-tick samples refuse. Skipped or backward time requires recovery.
Surrender requires an authorized attacker and a current/next frame, and never
transfers ownership or awards anything.

Opening an active record enters a **revalidation hold**. It preserves progress,
timer and objective boxes exactly, adds no downtime ticks, and cannot tick or
surrender until the trusted controller explicitly resumes it. Resume rechecks
authority and current observation, saves a fresh epoch/time anchor and changes
no capture counters. This hold is conservative recovery infrastructure, not an
approved offline-clock/disconnect policy. Production still needs that policy
before automatic recovery or combat can be enabled. Finished results reopen
as finished and stay unchanged under later tick/surrender calls.

## Scope and validation

No production catalog, command, tick subscription, automatic start/recovery,
combat permission, block snapshot, restoration, price, city or vehicle transfer
is registered. The observer gains only a package-private level accessor so its
actual registered level can be used by the production storage adapter.
Actual City A boxes, durations, empty-zone policy, declaration/surrender roles,
disconnect handling and exactly-once settlement remain separate work.

Selected validation: new isolated session cases, retained 22 presence cases,
16 siege rules and 50 baseline checks, plus one scenario in seed/verify JVMs on
the same disposable world. The restart proof must preserve the exact seed
journal bytes before reopening, show a different process identity, retain a
partially captured Town Hall, then finish with a defender timeout and immutable
result. Test clocks, identities and durations are synthetic. Source `c261da8c5488ce1fca940f6106a1c9e5987c5e01` passed all 111 distinct
scenarios / 112 phase executions on its first native run, including the actual
second-JVM proof. Source/JAR/archive checksums and sealed restart receipts were
independently verified. [Exact checkpoint and limits](SIEGE_RECOVERY_038_CHECKPOINT.md).

No live world/installed mod changes, accepted purchase replay, reset, manual
imports, multiplayer or Blender task. Accepted City A walls and earlier recovery
work stay intact. SEC-01 crowbar remains **UNRESOLVED / HIGH PRIORITY**.
