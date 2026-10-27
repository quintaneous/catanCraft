# Paid construction recovery checkpoint — 2026-10-27

Status: IMPLEMENTED / SCOPED TESTS PASS / INSTALLATION RELEASE HELD. Keep the owner's working 0.1.32 installation unchanged. This is not a published gameplay release or full regression acceptance.

## Exact sources

- Delivered baseline: `2f02120b0b489421650c83bf7dab305995c2a92a` (0.1.32).
- Recovery source: `5412009058af6fa81e5655115d6a0b99899e912d`, isolated branch `chatgpt/purchase-recovery-0.1.33`.
- Diagnostic workflow-only checkpoint: `1c82c144220298f9dbd78ff23858986f4346f794`.
- Compiled candidate: `catancraft-0.1.33-purchase-recovery.jar`, SHA-256 `6c03c5f0ee64cdfb863f5b532498ed0a9eaee69b4bdb61404e2dfbc9c33bc702` (2,690,862 bytes).
- No merge to foundation, release publication, owner-world deployment, or installed-mod change occurred.

## Implemented under audit A05 / A07

Existing unfinished Commercial District, Logistics Center, TH3-to-TH4 and TH4-to-TH5 purchases can resume automatically in the already-authorized isolated authored world. The original journal is immutable during recovery. A sealed `.recovery-v1.dat` companion binds its exact original bytes; a unique payment receipt is saved in the same CatanCraft SavedData as the debit and purchased state.

Recovery completes physical work, settles an originally outstanding price once, or recognizes a saved charge without repeating it. Current balances alone never prove payment. Legacy purchased markers are accepted only when unambiguous; missing structured before-images, corrupted history, foreign ownership or unexpected blocks remain blocked. Actual management-book NBT and current inventories are preserved. Recovery holds required chunks, retries recognized temporary loading/occupancy/resource waits, and displays its status in the nation territory GUI.

The original civic path now checks the server save result before declaring COMMITTED. Fresh civic/prestige purchases refuse to start over unfinished paid construction. Completed histories are left alone. This is resume/reconciliation, not a new rollback/refund implementation or a guarantee against arbitrary whole-world power loss.

## Executed evidence

1. Initial GitHub workflow `40602987904`: Java 17, Forge 47.4.10, Gradle 8.8 compilation succeeded for all 15 namespaces. **201 of 202 selected native tests passed**. All **19 new paid-recovery tests passed**. The failed retained test was `l5_producers_native_delayed_state_south`, with `Producer post-write mismatch`. Original failure logs are retained; the initial workflow remains failed.
2. Diagnostic comparison workflow `40603909456`: added block-coordinate/state detail to that exception only, without changing a guard, expectation, fixture or success path. Two independent fresh-world 50-test runs on baseline 0.1.32 passed; two on candidate 0.1.33 passed. This is 100 diagnostic executions per version, not 200 additional unique tests. The failure was **not reproduced or explained**. Passing diagnostic runs do not prove its cause or a fix, and they used a diagnostic-message-only patch rather than the exact candidate JAR.
3. All 73 Python tests passed with the original P3 archive/fixtures. The new R5 fixture's ten consumed documents were compared against the delivered R5 asset/probe archives, including City A and rotated City I contracts. Existing resources/fixtures (43 files) and original Town Hall test sources are unchanged.
4. Four normal candidate Linux startups and clean stops on a separate copy of the owner's uploaded seven-mod server (local Java 21). Initial two preserved progress. A synthetic exact-contract PENDING Commercial District journal was then staged in that disposable copy. The real automatic startup queue completed 2,793 unique writes, settled $3,000 + 50 Wood + 50 Stone once, and preserved the original journal. Another normal process restart did not repeat placement or payment; journals and receipt stayed unchanged. Partial-write cases were separately exercised by native fault-injection tests, not an OS-kill test.

Windows execution, authenticated graphical client interaction, arbitrary power-loss scenarios and the owner's latest walled save were not tested. No new owner checklist is requested while the release is held. Existing nonfatal SBW loot-table/offline Mojang-key messages also occurred on the baseline and are not claimed fixed.

## Immediate next engineering step

Investigate the intermittent retained south-producer mismatch using the preserved initial failure and diagnostic-only patch. Do not weaken equality/source/inventory checks, hide the failed run, or treat repeated green runs as proof of a fix. Keep actual source and test artifacts above; do not recreate this recovery implementation or ask the owner to reinstall a candidate merely to exercise earlier accepted civic construction.

A06 early-Town-Hall/ordinary-factory hardening, A09 producer/legacy-defense startup retries, A02 front-park scope and SEC-01 purchased-vehicle crowbar protection remain open. No gates, siege, mounted guns, terrain/ocean, storefronts, balance-rate changes or NPCs were added. No Blender asset dependency or new handoff is required for this code-only work. Preserve accepted City A wall progress and the 0.1.32 base-readiness screenshot without upgrading their scope of acceptance.
