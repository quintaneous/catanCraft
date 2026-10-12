# CatanCraft v0.1.9 — R5 guarded streetscape pilot (DISABLED BY DEFAULT)

Minecraft 1.20.1 / Forge 47.4.10 / Java 17 target. Built on top of the existing v0.1.8 **enhanced R5 diagnostics**. All v0.1.7 economy, R4-A checks, original R3 factory bundle, R5 original civic and street-art read-only checks, R6 offline materials and known vehicle crowbar behavior are unchanged.

## Why the limited pilot exists

User tested read-only R5 group contracts on live server:
- `/catan r5 group a front_public_park`: **537 checks, 0 mismatches**;
- `/catan r5 group a logistics_service_connection`: **158 checks, 0 mismatches**;
- `/catan r5 group a cleanup_tree_16_10`: **92 checks, 0 mismatches**.

Read-only validation alone cannot demonstrate the installation and rollback path. This release introduces a tightly gated, operator-only proof of the *write-mask* transaction, intended for a separate disposable test-world COPY; **not for your active server world**. The R5 house differences (R1/R2), 94 Commercial District conflicts (92 planned tree removal + 2 unresolved), civic physical buildings, R6 casemates/walls, R4 gates and siege restoration are NOT enabled by this installer.

## What the pilot can modify

Only these three group names are eligible, in any activated, nation-owned city a–p:

- `front_public_park`
- `logistics_service_connection`
- `commercial_sidewalk`

Every operation requires a fully valid pinned `R5_READ_ONLY_PROBE_INDEX.zip`, matching active map center/orientation, owned city, loaded chunks, entire exact old-state list, same 193×193 siege/restoration boundaries, no existing or destination block entities, no source/destination fluid states, and no entities overlapping the proposed solid voxels.

**No automatic purchase; no payment; no civic building; no R4/R6 placement; no house upgrade; no full schematic paste.** Only the exact Blender-provided sparse per-voxel after states are written. The original `schematics_bundle.zip`, player inventory, nation treasury/stockpile and city levels aren't touched by the R5 installer.

### Default: world writes are OFF

Running `/catan r5 apply a front_public_park confirm` on a normal server will be **refused**, unless you create this exact single-use approval file under its SERVER config folder:

`config/catancraft/R5_DISPOSABLE_WORLD_ONE_SHOT.txt`

File contents must be exactly:

```
I_BACKED_UP_THIS_DISPOSABLE_WORLD_AND_APPROVE_ONE_R5_WRITE
```

**Only do this on a disposable copy of the world, with independent backups**, not your main developed world. The one-shot approval file is deleted before saving the transaction journal and before any world write. Reusing the command requires manually creating a fresh one-shot approval file. Never place that approval file in your normal/live server config.

Without the one-shot file, all commands remain read-only or refuse.

## Safe solo test procedure (DISPOSABLE server copy only)

1. Stop your original live server. **Back up its world and server config**. Make a separate copy of the world/instance into a distinct test-server directory, keeping the same R3/R4-A/R5 sidecars; disable/close the original while testing.
2. Install `catancraft-0.1.9.jar` on server and matching client, replacing all older CatanCraft JARs. Confirm `/catan map assetstatus` = 38 ready, 0 invalid. The version inherited the existing R5 v0.1.8 server-log full mismatch diagnostics.
3. Go near City A so required chunks are loaded, stand outside the proposed park. Run read-only `/catan r5 group a front_public_park`. **Do not apply if any mismatch appears**.
4. Run `/catan r5 status` and `/catan r5 apply a front_public_park confirm` *without* the approval file. Expect refusal and **zero changed blocks**.
5. On the test server only, create the exact approval file described above, run `/catan r5 apply a front_public_park confirm` one time. It must create the journal file before the first write:

   `config/catancraft/r5_test_journal/A_front_public_park.json`

   The journal captures actual observed full block states, XYZ, desired states, dimension, transaction UUID and pending/committed status. It is flushed and atomically renamed before world writes. After a success, state is `COMMITTED`.
6. Compare the park visually and verify old factory sites, roads, Town Hall, ownership, treasury and raw stock unchanged. Server log includes `R5 PILOT COMMIT`.
7. Try duplicate application: should refuse because a journal already exists, with **no new writes**.
8. Run `/catan r5 rollback a front_public_park confirm` and check park restored. This requires current states to match that original transaction's before/after states and no entity obstruction; it **refuses arbitrary drift**, preserving the journal. Log includes `R5 PILOT ROLLBACK`; journal state becomes `ROLLED_BACK`.
9. Restart the disposable server and verify the journal remains. Test separately with `logistics_service_connection` if desired, using a NEW one-shot approval file and unloaded-chunk/overlap checks.

### Failure behavior and restrictions

- Unexpected old states, missing chunks, fluids, block entities, entity intersection, invalid/out-of-bounds geometry or unknown/missing index cause a complete refusal before writes; no automatic terrain clearing or force chunk loading.
- If server stops midway, the on-disk journal remains `PENDING`. Only a controlled rollback (not automatic guessing) is supported. Pending rollback first checks **all** cells are either actual-before or intended-after; if a third state appears it refuses and does not modify any voxel.
- The `COMMITTED` journal is never overwritten. After rollback it remains `ROLLED_BACK` to preserve a historical record, so another apply for that group is not possible without a separately reviewed reset procedure.
- This pilot intentionally rejects every block entity instead of attempting NBT recovery. It does not test payment reservation/refund or crash recovery under a real killed process. **Do not move it to your active world until controlled rollback and restart testing passes.**
- System-level backups remain essential. The journal and validation checks reduce risk but do not replace a full copy/backup.
- Physics/neighbor mutations outside explicit write mask are not expected with flag 2 but must still be checked on the disposable server.

## Additional known issues

- Purchased Superb Warfare vehicles still can be packed with a crowbar despite denial message. **Open issue #6**; not fixed in this patch. Do not give players crowbars.
- The R5 house façade baseline issue remains; house changes are not eligible for pilot.
- The R5 Commercial District still has 2 unidentified non-tree conflicts; Commercial and Logistics schematic installations are NOT enabled.
- Blender R6 delivered 272 offline-valid new candidate placements but has **0 live worlds verified**. R6 is not packaged into server placement runtime here.
- Server currently uses Java 25.0.1, not the Java 17 target. Recommend Java 17 before further extended testing.

## Release acceptance

- [ ] Install v0.1.9 on disposable copy without breaking startup/38-ready assets.
- [ ] One-shot approval absent: **apply REFUSED** and existing city untouched.
- [ ] Read-only group preflight accurate; current v0.1.8 verbose mismatch log still functional.
- [ ] With one-shot approval and matching live blocks: park write succeeds, after states match exactly, no unintended changes; journal `COMMITTED`.
- [ ] Duplicate apply denied with no second writes; approval file consumed once.
- [ ] Rolling back restores *actual-before* world states, and preserves `ROLLED_BACK` journal.
- [ ] Deliberate third-state conflict blocks rollback without changing additional cells.
- [ ] Missing chunk, fluid, protected tile entity or entity overlap refuses safely.
- [ ] v0.1.7 economic ledger, Logistics 8→9→10 test, nation data, R3 factories, R4-A and R5 read-only checks retain previous behavior.
- [ ] No surprises in `latest.log`. Provide journal and `R5 PILOT` output only, not screenshots.

**This release is developer-only pilot testing, not launch-ready civic/defense placement.**
