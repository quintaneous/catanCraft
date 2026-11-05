# SIE-01 / SIE-04 — 0.1.36 validated objective-rule checkpoint

2026-11-05. The approved provisional siege rules are implemented in an isolated,
unregistered evaluator. This is a code checkpoint, not an enabled city siege or
an installation release. No new in-game checklist is requested.

## Result and exact source

**78 executed / 78 passed / 0 failed** on final source
`d1ee94ac7f6ad619118805647d9380460768f5d0`, branch
`chatgpt/siege-rules-0.1.36`. All three native harness processes exited zero and
logged exact completion counts. Java17 / Gradle8.8 / Minecraft1.20.1 / Forge47.4.10.
[Successful workflow](https://github.com/quintaneous/catanCraft/actions/runs/42128969056).

| Namespace | Executed | Passed | Failed |
| --- | ---: | ---: | ---: |
| `catancraft_siege36` | 16 | 16 | 0 |
| `catancraft_gate35` | 12 | 12 | 0 |
| `catancraft` | 50 | 50 | 0 |

This is one final-source validation run. The earlier 0.1.34 and 0.1.35 evidence
remains separate; their additional suites were not silently counted or repeated.
The siege tests simulate rule inputs in Forge GameTest, not connected players,
actual city combat or multiplayer.

- Artifact `siege36-combined-source-and-evidence`, ID `13462441609`;
  5,577,897 bytes; SHA256
  `2255af819541f71c7e7aa60922e91e21b21ac0b200b765625d09b0048fdbc48a`.
- `catancraft-0.1.36-siege-rules-foundation.jar`, 2,911,133 bytes; SHA256
  `203ce07461a40e5bf44a0774485fd7ee5acf72ba352f9fb7dd2214fe2464f83e`.
- Downloaded ZIP checksum/CRC, all manifest hashes, identical JARs across jobs,
  JAR source/version/classes and every archived Git file verified independently.
  [Machine-readable verification](evidence/siege36_validation.json).

Compared with the verified 0.1.35 JAR, all 427 preexisting class files outside
changed gate test classes are byte-identical. The only changed preexisting JAR
entries are the manifest, version metadata and two gate test classes. New siege
classes and the isolated test-template namespace are additional. Existing
production code and immutable city assets remain unchanged. Later evidence/docs
commits do not change the tested JAR source.

## Gameplay rules implemented

- Both nations must be represented online at initiation (authoritative eligibility
  input to the core; live player collection remains for the future adapter).
- Outer point, then Town Hall, one objective active at a time.
- Attacker presence without defenders advances capture. Defender presence pauses
  capture while the shared siege clock continues.
- Outer completion neither resets the clock nor grants same-tick Town Hall credit.
- Defenders win on the overall timeout or explicit attacker-nation surrender.
  At the exact deadline, timeout wins before any new capture credit; no implicit
  overtime. Finished results cannot switch winners on later ticks/surrender.
- Each session freezes its identities, rules revision and timing inputs. New
  settings cannot be substituted into an existing snapshot. Numeric durations
  remain explicit and unset for production; synthetic test values are not balance
  recommendations. Unoccupied-zone HOLD/RESET must also be supplied explicitly.

The evaluator reports an outcome only. Surrender still needs actual actor/role
permission in its future command/service. Defender victory means intended
retention of city/vehicles; attacker settlement, eligibility, damage and
restoration are not implemented or simulated by changing real ownership here.

The strict NBT codec rejects malformed/foreign snapshots and round-trips partial
progress through compressed disk storage. This is not an authenticated durable
siege journal, a real process restart or proof of exactly-once world settlement.
[Full implementation contract and remaining integration work](SIEGE_RULES_036_CONTRACT.md).

## Initial failure retained and bounded fixture change

Initial source `93b6eb32c2858db0bc7f4c64288216ef79d77697`, workflow
[42128183273](https://github.com/quintaneous/catanCraft/actions/runs/42128183273),
executed 78 cases: **77 passed / 1 failed**. Siege16 and baseline50 passed; gate
11/12 passed. `obstructed_close_preserves_entity_blocks_and_absent_journal` failed
waiting for real entity chunk residency after the old 400-attempt limit; its
assertion body had not run. The raw failed log is retained permanently in
[the repository](evidence/siege36_initial_gate_failure.log), with
[verified result metadata](evidence/siege36_initial_validation.json). Original
combined artifact ID `13460859239`, SHA256
`cb51d9cc0c78c9d8b60818f552b11c1c6baf75e9c19d3a70e9f0402e35e18eee`, was downloaded
and all manifest files verified. The failure is not erased by the later pass.

The test-only change aligns readiness with the existing paid-recovery fixture:
4,000 readiness attempts inside a 5,000-tick test timeout, with read-only
chunk/entity diagnostics. Both native residency checks and all gate safety
assertions remain mandatory. No body retry, entity deletion, forced readiness,
production lease change or world reset was introduced.

The final [raw gate log](evidence/siege36_final_gate.log) records one fixture at
400 attempts / 531 ms with chunk `[212,187]` present but entities not yet loaded.
It became ready at 1,415 attempts / 1,161 ms and its assertions then ran. This is
direct evidence that the original attempt budget can expire during a short
normal loading interval in the fast GameTest harness. It does not retrospectively
identify the initial failure's exact pending chunk or underlying I/O cause, and
is not presented as a production gate fix. All 12 final cases reached readiness
and passed their assertions.

## Preserved scope and next integration

Accepted City A North Reinforced / East-South-West Basic, mixed connections and
restart, 0.1.32 readiness and 0.1.34 solo acceptance remain accepted. SEC-01 stays
unresolved/high priority at its documented installed-runtime scope; its tested
production classes are unchanged. No live-world/installed-mod changes, resets,
manual imports, repeated accepted purchases, multiplayer, NPC or Blender work.

Next integration requires precise objective/zone registration and real server
presence, selected total/hold durations and empty policy, actor permissions,
disconnect/downtime rules, gate geometry/overlay and durable damage/restoration/
settlement contracts. Global TaCZ/SBW scenery protection remains active. No
player-facing siege command or GUI, gate placement or automatic capture is
introduced by this checkpoint.
