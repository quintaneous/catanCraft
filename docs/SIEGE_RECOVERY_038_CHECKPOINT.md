# 0.1.38 durable objective state checkpoint

2026-11-10. Branch `chatgpt/siege-recovery-0.1.38`, based on validated 0.1.37
head `ff7307ffc2f2b60ea7950fefc7f8da5afac36bfc`.

## Implemented behavior

Saves the original full profile, rules, objective progress, shared timer and
outcome. Recovery holds preserve state until explicit revalidation. Exclusive
city writer, exact revisions, observed-frame checks and guarded save publication
prevent duplicate credit and ambiguous in-memory continuation. Reuses the
existing sealed atomic journal writer unchanged. No siege damage, live activation,
automatic recovery or ownership writes. [Contract](SIEGE_RECOVERY_038_CONTRACT.md).

## Exact-source build and tests

Source commit: `c261da8c5488ce1fca940f6106a1c9e5987c5e01`.
Source tree: `403709b9ab5756bab0f3c92edfcb5d4bfcca3b75`.
[Workflow 42884168426](https://github.com/quintaneous/catanCraft/actions/runs/42884168426)
completed successfully on its first run: **111 distinct native scenarios across
112 phase executions, 112 passed / 0 failed**. Java 17 / Gradle 8.8 /
Minecraft 1.20.1 / Forge 47.4.10. No failing native retry or guard relaxation.

| Selected suite | Executions | Passed | Failed |
| --- | ---: | ---: | ---: |
| New session storage/recovery | 22 | 22 | 0 |
| Retained objective presence | 22 | 22 | 0 |
| Retained siege rules | 16 | 16 | 0 |
| Retained baseline | 50 | 50 | 0 |
| One restart scenario: seed then verify | 2 | 2 | 0 |

New checks cover complete identity/profile persistence, explicit recovery,
stale revisions, duplicate/skipped/backward clocks, fresh same-handle epochs,
clock/authority changes immediately before writing, writer exclusion and lock
replacement, malformed/corrupt history, symlinks, initial online eligibility,
unauthorized surrender, immutable outcomes and failure before/after disk commit.
Independent static review findings were corrected before this first run.

## Actual second-process proof

Seed process `3160:1794338538878` wrote a session through the real storage API:
elapsed **3**, outer progress **2**, Town Hall progress **1**, revision **3**.
Verify process `3313:1794338558216` opened the same world and files. It checked the
original journal hash against independent sealed seed evidence before mutation,
refused changed contract/profile inputs, and required explicit recovery while
retaining the exact original progress, timer and boxes. A deliberately different
synthetic clock did not create downtime credit. After revalidation, continued
defender contest ended in `DEFENDER_WON_TIMEOUT` at elapsed **20**, revision **21**,
with outer **2** and Town Hall **1**. Reopening and repeated terminal actions did
not rewrite the result. No file copy, reseeding or journal reconstruction.

The downloaded sealed seed proof, terminal journal and verification receipt were
independently decoded and checked: checksum/header, distinct process identities,
linked SHA-256 values, original profile, seed counters and terminal result agree.
This proves objective-state recovery across two JVMs. It does not prove damage
restoration, settlement, actual connected-client behavior or power-loss recovery.

## Artifact verification

JAR: `catancraft-0.1.38-siege-recovery.jar`, **2,990,112 bytes**.
SHA-256: `ca9f1095c0f8da66da153a525add515a41836b1c11b12596771991accc6f4c38`.
Manifest source stamp and mod version match the tested source.

Combined artifact: `session38-combined-source-and-evidence`, ID `13796445094`,
**5,951,474 bytes**. Download from the workflow artifacts.
Archive SHA-256: `1fc921d0c7e88b267d7aeac52a01ee2992bd3fdd432d0b7133ac8190bf6de824`.
Includes exact source, JAR, all build/native logs, count/exit records, manifest
and sealed restart proof/receipt/journal. All 51 manifest entries were checked;
all 377 source files matched Git byte-for-byte. All five jobs built the same JAR.
[Machine-readable verification](evidence/session38_validation.json).

Compared with validated 0.1.37, **448 preexisting class files are byte-identical**.
The only intentional existing behavior addition is `Bound.level()`, a read-only
accessor used to derive the actual registered level. `SiegeObjectives37` and its
`Report` class differ only in `LineNumberTable` payloads after that source-line
insertion: constant pools, instructions, signatures and all other attributes
were independently compared and match. The artifact comparison initially stopped
on these two extra byte differences; they were investigated, not silently excluded.
Other existing entries match except version/source metadata. Existing assets,
purchases/recovery, gates and approved wall behavior remain intact. Later
checkpoint-only commits do not change this tested JAR source.

## Remaining boundary and owner action

No installation or new in-game checklist. The new code remains unregistered.
Live objective locations/durations, declaration/surrender permissions, offline
clock policy, repeated-siege archival, pre-war block snapshots, controlled damage,
restoration and exactly-once settlement are still needed before activation.
Storage requires a stable lock-file identity and cooperative locking; Linux test
results are not Windows filesystem acceptance or rollback/hostile-file protection.
Synchronous per-tick saves are not an interactive frame-time guarantee.

Accepted City A North Reinforced / East-South-West Basic walls and prior acceptance
remain intact. No live-world/mod change, reset, manual imports, accepted purchase
replay, multiplayer or Blender task. SEC-01 crowbar remains unresolved/high priority.
ECO-03 physical gun/ammunition counters and fixed vehicle deployment bays remain
formalized Phase 3 requirements; this checkpoint does not implement shops.
