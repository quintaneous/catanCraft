# CatanCraft 0.1.34 combined A06 / A09 release record

Status: built and automated checks passed; owner installation and in-game acceptance remain pending. This release carries forward delivered 0.1.33 source `967e9faaa2f954e51c7612a48551a9a33d50cad5` and its documentation `1cb34f3435bb9a7b733acc16864e5ffef049b8bb`. No assumption is made that 0.1.33 was installed or accepted.

## Exact delivered binary

- Development branch: `chatgpt/construction-safety-0.1.34`.
- JAR source: [`e7f9e67345de36ca90e31008fddc1f610f4805e2`](https://github.com/quintaneous/catanCraft/commit/e7f9e67345de36ca90e31008fddc1f610f4805e2). Later release-documentation commits do not change that binary.
- File: `catancraft-0.1.34-construction-safety.jar`; 2,829,805 bytes.
- SHA-256: `5694c312ae97ce1b99441f81b6cf701bd13d4ccf41b1e93933c1d60ac9d3a0bb`.
- Final combined [workflow 40782665120](https://github.com/quintaneous/catanCraft/actions/runs/40782665120): **251 executed / 251 passed / 0 failed** across 20 selected native namespaces. The seven startup-fixture bootstrap tests are auxiliary and are not counted a second time.
- CI evidence artifact: `12857758652`; ZIP SHA-256 `482a37ee6dc64db68806a5cf3c548a778c49cf061b5de04d830a700ac63e1840`.
- Python: **73 executed / 73 passed / 0 failed / 0 skipped** on the same source (51 packaging/demo, 14 economy, 8 original-P3 contract cases).

All 20 builds produced identical source/JAR bytes. The downloaded artifact digest, every manifest file hash, exact native completion counts and zero exits, JAR source stamp, and every archived source file were independently checked. All 44 resource/fixture files inherited from delivered 0.1.33 are unchanged; all 13 bundled early Hall/factory pins match their provenance. No wall artwork, accepted corner ownership, prices or production rates changed.

## Delivered behavior

**A06:** normal Town Hall 1->2 and 2->3 upgrades and all nine ordinary industrial purchases now use sealed preflight/source intents and the existing 0.1.33 receipt-backed recovery engine. Actual books/inventories survive; full source checks precede placement and original-price settlement. Public direct paid-paste helpers fail closed. TH4/5 and civic recovery remain intact.

**A09:** existing producer and legacy-defense journals are discovered on ordinary startup, with bounded chunk/entity leases and typed retries for temporary loading, occupancy and original-price waits. Existing rollbacks finish as rollbacks, with original accounting and duplicate/refund protection. Restart never creates a fresh purchase. Hard conflicts retain the history and report a blocked state.

New legacy-defense and producer intents record narrow natural-support evidence. Producer proof permits only exact dirt at unchanged, pinned source-covered vanilla grass reads. Ground is never repainted; foreign states, exposed dirt and altered proofs refuse. Older journals without proof remain strict. Early Hall recognition likewise requires exact source-cover evidence.

## Validation detail

| Native namespace | Passed | Failed |
|---|---:|---:|
| catancraft | 50 | 0 |
| catancraft_base_lifecycle | 8 | 0 |
| catancraft_basic_natural34 | 7 | 0 |
| catancraft_chunk_lifecycle | 6 | 0 |
| catancraft_construction34 | 28 | 0 |
| catancraft_defense | 8 | 0 |
| catancraft_economy_clock | 9 | 0 |
| catancraft_houses | 11 | 0 |
| catancraft_integration | 12 | 0 |
| catancraft_paid_recovery | 19 | 0 |
| catancraft_producer_r2 | 9 | 0 |
| catancraft_producer_source34 | 4 | 0 |
| catancraft_producer_stability | 7 | 0 |
| catancraft_public27 | 11 | 0 |
| catancraft_sector_prices | 7 | 0 |
| catancraft_sectors | 22 | 0 |
| catancraft_sectors_natural | 7 | 0 |
| catancraft_sectors_service | 15 | 0 |
| catancraft_startup34 | 3 | 0 |
| catancraft_wall_gui | 8 | 0 |

The 251 total retains the 209 selected 0.1.33 cases and adds 42 cases. Startup tests use a separately generated flat fixture with asserted provenance, real production journal discovery/adapters, normal server ticks, SavedData disk readback and queue rebind checks. It is neither an authored-R7 validation nor an owner-world test. Native tests run in isolated CI worlds using Java 17, Gradle 8.8, Minecraft 1.20.1 and Forge 47.4.10.

Earlier evidence is retained under its original source: initial candidate `7588c0f` passed 237 selected tests; `4314be4` passed 54 focused checks but startup fixture staging failed; `626de56` also failed fixture staging; `f91a465` passed the three startup cases. Combined `c6fb56c` then executed 247 / passed 245 / failed 2: native producer source grass decay during a resource wait, and a test assertion about unrelated city activity. The source-proof correction and city-scoped assertion passed the 73-case focused run [40781923082](https://github.com/quintaneous/catanCraft/actions/runs/40781923082) on `6cac5b6`, then the final 251-case run above. No failed run is presented as release evidence.

## Scope and remaining limits

Use the single [solo checklist](CONSTRUCTION_SAFETY_034_SOLO_CHECKLIST.md). Preserve City A **North Reinforced; East, South and West Basic**, accepted mixed connections/restart, and the accepted 0.1.32 base-city-readiness result. No accepted purchase retests, reset, manual imports, deliberate crashes, multiplayer or Blender work are required. The owner's live world and installed mods were not accessed or modified.

Windows/client/current-modpack installation and owner acceptance remain untested. Queue rebind and injected interruptions do not establish arbitrary process-kill/power-loss safety. Old proofless histories or target-only newly covered grass can still block safely on drift; no missing proof is manufactured. Completed-path memoization can require the next service/server restart for an operator-created interruption later in the same session. These tests do not add hard frame-time guarantees or supersede the recorded R7 preparation/slice timing limits. SEC-01 purchased-SBW crowbar exploit remains **UNRESOLVED / HIGH PRIORITY**.
