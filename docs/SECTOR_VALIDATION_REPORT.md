# Sector engineering validation — 2026-10-18

**Build PASS; 118 native executed / 118 passed / 0 failed. Python 17 / 17 / 0.**
This is a verified engineering checkpoint, not a completed playable sector/demo
release or owner artwork/modpack acceptance.

Tested source: `f7579bafbb2fa3d6ddd283e9400714d709879eca`, clean at validation
start. Later report edits change documentation only. Java 17.0.16 / Gradle 8.8;
Minecraft 1.20.1 / Forge 47.4.10. No dependency versions changed.

| Namespace | Executed | Passed | Failed | Exit |
| --- | ---: | ---: | ---: | ---: |
| catancraft | 50 | 50 | 0 | 0 |
| catancraft_public27 | 11 | 11 | 0 | 0 |
| catancraft_integration | 12 | 12 | 0 | 0 |
| catancraft_producer_r2 | 9 | 9 | 0 | 0 |
| catancraft_houses | 11 | 11 | 0 | 0 |
| catancraft_defense | 8 | 8 | 0 | 0 |
| catancraft_sectors | 17 | 17 | 0 | 0 |

The sector suite has ten group transaction tests, five contract tests, one
command registration test and one cross-backend reservation test. It exercises
whole-side preflight, insufficient money/materials separately, one-time charges,
upgrade/rollback, original receipts and historical costs, journal/ledger fault
boundaries, current inventory/book preservation, stale adjacent receipts,
road/partition conflicts, entity hazards, and integer-overflow refusal. These
synthetic fixtures do not approve any new wall artwork. Journal/ledger reload
fixtures are not a substitute for final full-client restart/modpack acceptance.
The retained 101 tests are freshly rerun, not reused historical passes.
Conventional Gradle `test` remains NO-SOURCE; it is not counted as a passing suite.

Exact activation and runner:

```sh
export JAVA_HOME=/workspace/toolchains/jdk-17.0.16+8
export PATH="$JAVA_HOME/bin:/workspace/toolchains/gradle-8.8/bin:$PATH"
export GRADLE_USER_HOME=/workspace/gradle-cache
bash tools/validate_combined.sh /workspace/integration-evidence/sectors-final --offline
python3 -m unittest discover -s tests -p test_prepare_fresh_demo.py -v
```

The runner invokes `gradle build --offline --no-daemon --max-workers=4`, then
`gradle runGameTestServer -PgameTestNamespace=<namespace> --offline --no-daemon
--max-workers=4` for every row above. It archives old disposable test directories,
creates fresh ones and stages `tests/fixtures/thall4.schem` and `thall5.schem` under
each working directory's `config/catancraft/schematics/`. Original fixture hashes
are checked first. All native runs are sequential and isolated; no multiplayer.

Full logs, command expansion, exits, tool versions, hashes and `RESULTS.json`:
`/workspace/integration-evidence/sectors-final/`. Initial focused 17/17 pass and
compile logs: `/workspace/integration-evidence/sectors-focused/`. The final run
includes the subsequent overflow fix and regression assertion. No failing native
run occurred in this follow-up. Background authentication lookup/configuration
warnings did not prevent the native runner from executing and completing all
required tests. No proxy or TLS bypass was used.

Reviewable checkpoints: `87f7198` instructions/status, `acd8e48` sector engine,
contracts/interface/reservation guard, `f7579ba` verified-component staging and
fresh-world checklist. Producer/house/obstruction runtime files, original resource
assets and original fixtures are unchanged from preserved release `a72e7b0`.

Development JAR: `build/libs/catancraft-0.1.29-sector-dev.jar`.
SHA-256: `959026d4ed4201b44586b8654b1900f77551cb0eceee94df87bab771654d4b5e`.
Matching components are staged at
`/workspace/releases/CatanCraft-0.1.29-sector-dev-components/`; the manifest says
`NOT_READY`, `runnable=false`, `worldIncluded=false`. Both original configuration
ZIPs passed their pinned hashes. This directory is not a runnable demo or final
release. Existing 0.1.28 archives remain untouched; nothing new was pushed,
merged, deployed or installed.

## Remaining work and exact next step

1. Obtain the representative basic/advanced wall brief and reviewed immutable
   assets, with corner-cell ownership, preserved roads and adjacent-level
   contracts. Confirm the sector price schedule; earlier module prices remain
   historical and fixture prices are test-only.
2. Bind those approved contracts to the production sector service: durable saved
   levels/receipts, quote lifetime, full server authorization/ownership and loaded
   entities, shared city reservation scans and restart recovery discovery. The
   tested transaction engine/contract policy is implemented; that production
   adapter is not yet implemented or claimed complete. Normal sector commands
   explicitly refuse purchases while production contracts are absent. Historical
   socket status/resume/rollback remain accessible.
3. Obtain and hash the clean authored world and matching isolated launcher/modpack
   inputs. Implement the deliberate fresh initialization using that world and
   current visuals; do not disguise a blank/test world as the historical R7 save
   or require a legacy repair sequence by default. No developed City A access.
4. Verify the actual intended SBW artifact (log reports 0.8.9.2), weapons and
   packing behavior in the isolated demo. Crowbar is **UNRESOLVED / HIGH PRIORITY**.
5. Run the one `FRESH_WORLD_CHECKLIST.md` against the complete matching package,
   then produce the consolidated release. No siege, NPC or multiplayer work.
