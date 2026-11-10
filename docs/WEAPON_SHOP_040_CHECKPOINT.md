# ECO-03 / SEC-01 — 0.1.40 validation checkpoint

Combined owner acceptance remains pending. Do not install an intermediate build
for this checkpoint. See WEAPON_SHOP_040_CONTRACT.md for implemented scope and
COMBINED_SHOP_ACCEPTANCE_PLAN.md for the linked remaining work.

## First run: retained failure evidence

Source `c3b54ec226cb8335783da474b372de44a44c8e58`, workflow
[42896885413](https://github.com/quintaneous/catanCraft/actions/runs/42896885413).
Java 17 / Gradle 8.8 / Forge 47.4.10 builds succeeded. Actual native executions:
136, of which 107 passed and 29 failed; the second shop JVM never ran. This is
not a validated release.

- Shop: 35 executed, 7 passed, 28 failed. All 27 purchase fixtures and the detached
  actor fixture attempted to modify Forge's unmodifiable public player roster.
- Shop restart seed: 1 executed, 1 failed. The same roster insertion exception
  prevented native player persistence; the verified-save boundary refused the
  absent player file. Verify phase did not run.
- Retained baseline 50/50, purchase recovery 19/19, structural restoration 22/22,
  published SBW 7/7 and SBW procurement seed/verify 1/1 each passed.
- CI evidence copying also tried nonexistent shop-restart files in unrelated
  jobs, causing job failure after successful test execution. The aggregate
  artifact still retained the actual logs/results with `all_passed: false`.

Retained combined artifact `13800813040`, 6,387,713 bytes, SHA-256
`91b01f8cfd3442417152065dd15d204112c6f7b7b553bb1db624b5453c105059`.
Downloaded archive CRC, digest and all 67 manifest file hashes verified.

Corrections use a marked controlled-world helper to call the actual native
PlayerDataStorage.save method, verify its real directory, and avoid roster edits.
Production saveAll, accounting, recovery, assertions and test counts stay unchanged.
The copy step now checks whether each optional evidence file exists. The corrected
exact-source run is pending at this commit; no new pass is claimed here.
