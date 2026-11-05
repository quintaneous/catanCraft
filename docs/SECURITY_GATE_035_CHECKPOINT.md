# SEC-01 / DEF-06 / SIE-01 — validated 0.1.35 development checkpoint

2026-11-05. Both authorized workstreams advanced from accepted 0.1.34. This is a
**validated code and design checkpoint**, not an enabled City A gate/siege release
or installed-modpack security acceptance. No in-game action or mod replacement
is requested for this checkpoint.

## Exact source and evidence

- Branch: `chatgpt/vehicle-security-gates-0.1.35`.
- JAR source: `694320186f3a14e1b1b345b7c38e91b310419712`, descendant of accepted
  `4fedb8d09634cd27a435d0fe60ca4f2c0a786087` and delivered 0.1.33.
- [Successful exact-source workflow](https://github.com/quintaneous/catanCraft/actions/runs/42124906880).
- Artifact: `security35-combined-source-and-evidence`, ID `13460991056`,
  5,919,954 bytes; SHA256
  `a0e1144867724fdf84495d53c945de43e5d5875f9dd2e9d0e9980685ef80d6aa`.
- JAR: `catancraft-0.1.35-security-gate-foundation.jar`, 2,885,541 bytes; SHA256
  `f35028608386f7f0d0d4637f3c06bf4825d20cf74e9efddceeedbfdc9f5b43db`.

The downloaded artifact checksum, ZIP CRC, every manifest file hash, common JAR
across jobs, embedded source stamp/version/classes and every archived Git source
file were independently verified. Source archive exactly matches the stated
commit. [Machine-readable verification](evidence/security35_validation.json).
Later documentation/verifier-evidence commits do not change the tested JAR source.

## Native validation actually executed

Java 17 / Gradle 8.8 / Minecraft 1.20.1 / Forge 47.4.10. All tests used isolated
generated development worlds. There were no failed native attempts in this run.

| Check | Executions | Passed | Failed |
| --- | ---: | ---: | ---: |
| New gate foundation (`catancraft_gate35`) | 12 | 12 | 0 |
| Retained baseline (`catancraft`) | 50 | 50 | 0 |
| Retained paid recovery | 19 | 19 | 0 |
| Retained sector contracts | 22 | 22 | 0 |
| Retained natural support | 7 | 7 | 0 |
| Retained published-SBW retrieval | 7 | 7 | 0 |
| Real procurement/restart, seed phase | 1 | 1 | 0 |
| Same scenario, separate-JVM verification | 1 | 1 | 0 |
| **Total phase executions** | **119** | **119** | **0** |

This is **118 unique scenarios**, because seed and verify are two executions of
one scenario. It does not repeat or replace the prior 0.1.34 251-test/73-Python
record. All suite processes exited zero and logged their exact completion count.

## SEC-01 outcome and remaining limits

The new scenario uses actual `/nation procure pickup` and `/nation procure lav150`
commands with a fixture nation/leader/facility and the real SBW registered
entities. It verifies exact original prices, ownership tags, no portable vehicle
item, save/readback and the same two persisted entity UUIDs in a second JVM.
No purchase tags, replacement entity NBT or NoGravity bypass is used.

Seed JVM identity: `3702:1793919258028`; verify JVM identity:
`3868:1793919292879`. Both phases use the same isolated world and pinned runtime
manifest `6e4424ef5a564609dc893c5d9d1ff3efcc7b897d8b9ae9a9acf96d7c331c9452`.
The saved seed proof remains immutable and a separate successful verification
receipt binds it. Normal shutdown/save plus restart is proven; arbitrary crash
atomicity between procurement entity creation and nation debit is not claimed.

After restart, direct and helper retrieval routes refuse both hand arguments;
normal main-stack interactions refuse, offhand-only interaction has no retrieval
side effects, both vehicles and accounting remain unchanged, and ordinary
unpurchased pickup retrieval still removes the entity and grants its item.
The seven retained tests also cover existing override/artillery/fallback paths.
The existing production crowbar guard required no code change in this batch.
Non-sneak artillery firing behavior was not replaced by unconditional denial.

The optional runtime is reproducibly prepared from the pinned published SBW
0.8.9.2 binary/sources and KotlinForForge 4.11.0 plus exact embedded dependencies.
It adds no dependency to ordinary builds. All 31 staged files and nine runtime
artifacts verify. [Six negative verifier checks](evidence/sec01_035_verifier.json)
passed: forged manifest, changed staged JAR/POM, changed published input, unknown
file and symlink are refused without changing fixture bytes/links/mtimes. The
canonical inventory is unchanged. Reproduce with
`python3 tools/sec01_035/check_verifier.py` after preparing the runtime.

Raw native logs retain Forge/Mixin development warnings and the optional SBW
`grant_manual` loot parse error; a passing gameplay test is not an error-free
full-modpack certificate. The installed SBW artifact hash/configuration has not
been supplied/compared in this batch. Connected owner interaction acceptance,
applicable actual part/custom-hit behavior and any future purchased weapon
contracts remain separate. **SEC-01 stays UNRESOLVED / HIGH PRIORITY.** Do not
repeat accepted purchases or touch the live installation to obtain this evidence.

## Gate and siege outcome

`GateTransition35` implements a reusable cyclic OPEN/CLOSED exact-mask primitive:
full source/read preflight, sequential native support checks, entity safety,
source-save-before-intent, at most four direct writes per step, physical save
before completion, immutable owner/world/contract/request binding, explicit
resume and duplicate/replay refusal. Tests cover both movement directions,
obstructions before/during motion, partial resume, ownership/authorization
changes, foreign/missing journals/contracts, unsupported blocks/read masks and
save failures. Gate interruption tests reconstruct the runner in the same server;
a separate-process gate recovery claim is not made.

The primitive is **unregistered in production**. It has no City A write mask,
player command/UI, price or siege-damage activation. Existing wall assets and
strict recovery contracts are unchanged. Closing the future North opening would
conflict with two P3 expected-air cells, so production use requires an explicit
shared contract. Caller coordination, geometry/access/pricing, bounded-history
archival and production performance remain before functional player deployment.

[The siege rules draft](SIE01_RULES_DRAFT_035.md) records the owner's decisions:
both nations online to begin; capture city plus eligible local purchased vehicles.
The owner subsequently approved outer point, then Town Hall, with one active
objective at a time (2026-11-05). This is objective-sequence approval only; exact
capture/contest rules, timers, disconnect behavior, damage area, vehicle
eligibility and stockpile transfer remain undecided. Existing global damage
protections stay enabled. The decision update changes no tested source or JAR.

## Preserved acceptance and next work

Accepted North Reinforced / East-South-West Basic, their mixed connections/restart,
0.1.32 base readiness and 0.1.34 solo acceptance remain accepted. No live world,
installed mod, reset, manual import, repeated accepted purchase, multiplayer,
NPC/Combined Arms or Blender work occurred.

Next: finish the selected two-stage objective's concise rules contract; define
an approved gate mask/overlay and access/price contract before production wiring.
SEC-01 installed-runtime acceptance can use existing purchased vehicles when
that narrow check is agreed; this checkpoint does not ask for another purchase.
