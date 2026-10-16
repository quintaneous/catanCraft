# CatanCraft v0.1.27 — verified R5 public-space integration

2026-10-16. Status: **CI/build/delivery verified; owner in-world acceptance pending**. No main merge, live-world access or deployment.

## Provenance

- Candidate: `96e3fc6c9d9683ed94c9266548593f44661f74ba`.
- Published gameplay source: `f74b4002c07c9f451c7947c164444a59b4f2b3eb` on `v0.1-foundation`; rechecked before this documentation-only commit.
- GitHub Actions `38859793842`, job `116839241644`, artifact `12028833156` (`public27-validation`). Compilation, both native suites and guarded source publication all succeeded.
- Exact CI JAR SHA-256: `6922f42f77ec3136a080010dbf77a8b6d4b69799b5224c75c762c679cd31e6d4`.
- Published source ZIP SHA-256: `d9fd737d33e437ebee6a59aeaeb2dec261cbf4b6cf68a3666f9b097d223ba4b4`.
- Validation artifact ZIP SHA-256: `bb1fcaf341ee0e07f2094c6a22c01fc5525db8f571be6ac88fd4d67c0f96d292`.

The downloaded logs explicitly record all **50 retained required tests passed** and all **11 new required tests passed** (61 total), each with BUILD SUCCESSFUL. No new owner-runtime acceptance is inferred. The delivered JAR is the exact passing binary, not an unverified rebuild.

All 14 candidate-manifest entries match both the inspected candidate and published source. All 21 existing packaged schematics remain byte-identical. All original source resources, the three files containing the original 20 tests, and Town Hall gameplay files are unchanged. Producer/repair test fixture setup was isolated from prior template inventory drops; deliberate test occupancy remains. The producer corruption test now positively proves its PREPARED interruption and durable journal, rather than accidentally accepting an unrelated earlier refusal. Production guards and original assertions were not relaxed.

## Scope and original-asset recheck

Ten public-space groups: east isolated-tree cleanup; two smaller lamp bases; two arrival courts; shared residential frontage and garden edges; two factory-gap pedestrian links; rear worker garden. **1,661 exact writes; 18,766 full read-context cells** per authored city; zero charge and no purchase/level/economy changes. Existing producer occupancy messaging now distinguishes positively identified fresh preflight refusal from pending recovery without weakening protection.

An independent read-only audit recovered the original R5 archive with SHA-256 `679b9d94d2f96175705e2e85f2e36aa0146cd8dd28a71826e25998c6c9755521`, decoded the new contract, checked all 1,661 local before/after writes, all 18,766 source-context cells against its original padded north fixture, and all 26,576 world-space writes across A–P. No selected write intersects original R4 reservation bounds. This is a static asset audit, not live-world clearance or all-city permission.

The full operation preflights all features; separate sealed actual-before history provides guarded resume, reverse rollback and verified repeat-as-no-op. Startup discovers incomplete operations but never starts new installation. See `R5_PUBLIC_INTEGRATION.md` for the complete contract and limits.

## Focused owner acceptance

Keep the existing developed R7 City A, folder/location, authorization marker, Forge/modpack/configuration and historical journals. Stop normally, back up the complete world, replace only CatanCraft with the verified v0.1.27 JAR on the usual client/server installations, with no duplicate old JAR. No schematic import, new marker or replacement world.

1. `/catan r5 public status a` — ten features, 18,766 checks, 1,661 design cells, read-only/zero charge. Status does not load chunks. Allow City A to finish loading if reported; preserve any other refusal rather than editing blocks.
2. Stand outside affected courts, paths and gardens, preferably on a clear main-road section. `/catan r5 public apply a confirm` — fresh expected result: 1,661 block changes, zero charge, purchased levels unchanged. Incompletely loaded entities or occupancy safely refuse.
3. Inspect the new public spaces, lamp bases and intended tree removal. Preserve neighboring developed structures, access, inventories and paid progress. Do not repeat accepted Town Hall, producer or Commercial/Logistics purchase tests.
4. Repeat apply: zero writes/charge. Save, stop and restart normally once, then verify status and persistence. No multiplayer, deliberate crash or routine rollback test.

On a conflict retain exact output, blocks and journals. `/catan r5 public resume a confirm` and `/catan r5 public rollback a confirm` are recovery controls, not routine steps. Never delete receipts or force a paste. Reverting after world changes requires a consistent stopped-world backup, not a JAR-only downgrade.

## Still separate

All six house facades/roofs await exact R1/R2 branch handling. The earlier front-public-park pilot, completed civics/prerequisites, Town Hall and producer geometry are excluded. This is not the entire R5 rework. Defenses/gates remain unintegrated; crowbar #6 remains unresolved/high priority; old interrupted Town Hall purchases are not repaired by this feature. Orientation pavilion deferred. R8-B remains an unapproved three-area review, not a completed sixteen-territory world.

Producer Exterior R2 **has arrived**, archive SHA-256 `5ac5fc61de0e7716f4d4e4664048476b3843be579bba5e612a71334bd4b6f090`. Fresh read-only receipt checks passed all 186 member hashes, all 15 original producer bytes against accepted v0.1.26, and nine exact same-tier glass-cleanup deltas totaling 1,065 changes across nine asset pairs. **R2 exterior art is not included in v0.1.27.** Next integrate immutable R2 tokens with explicit free same-tier grouped migration and future upgrades from R1/R2, preserving old asset hashes/tokens and pending-journal interpretation. Never overwrite R1 resources or ask the owner to repurchase L5.

No owner world or installed mods were accessed. Injected interruptions and serialized reload tests are not independent process power-loss certification. Existing accepted v0.1.26 owner results remain valid.
