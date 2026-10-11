# CatanCraft v0.1.6 — R5 read-only integration stage

**Source:** `quintaneous/catanCraft` branch `v0.1-foundation`. Forge 1.20.1, Java 17, mod version `0.1.6`.

This release RETAINS everything in v0.1.5 (unverified v0.1.3 nation-purchased Superb Warfare crowbar protection, unverified v0.1.4 Logistics Center efficiency, v0.1.5 R4-A per-socket preflight, verified v0.1.2 terrain protection). **It installs no R5 structures and alters no live-world blocks or nation financial records.**

## R5 assets checked

Original Blender package: `CatanCraft_R5_City_Rework_And_Civic_Assets.zip`

Pinned SHA-256: `679b9d94d2f96175705e2e85f2e36aa0146cd8dd28a71826e25998c6c9755521`.

It contains **320 per-city street/house feature groups (20 per city)** with **68,496 total changed cells**, 32 civic socket candidates (commercial and logistics for each A–P city), 32 civic-delta files with expected-before states and support read conditions, two new Sponge-v2 civic schematics, full source previews and migration contracts. The original 18.9-MB ZIP passes `ZipFile.testzip()`; this is not a full repeat of Blender's resource-heavy 352-transaction validator.

The original `contracts/r5-city-block-deltas.json` is 52 MB uncompressed. Instead of parsing it on the server for every one-feature lookup, generated a compact `R5_READ_ONLY_PROBE_INDEX.zip` **derived only from those source rows** (same world XYZ and old/new full-state strings, no new geometry or authority). Index source entry SHA-256: `4b4e94778bf6436a629e86efa3ea3ec9595f14ef56577711f053a06cfa2a5281`. Index is 320 per-group JSON files + manifest, SHA-256 `4f5a445347adbb62e73d1c38bfab5ff850c7307733a619847d9eeac9245834ba`. Runtime hard-checks both the original R5 and index ZIP file hashes; a modified/incomplete file is refused, not silently adopted.

## Installation alongside existing R3 and R4-A

1. STOP server and client; back up the entire developed world and `config/catancraft`.
2. Replace **all** older `catancraft-*.jar` in **both** client/server `mods` with **only** `catancraft-0.1.6.jar`.
3. Do not overwrite `config/catancraft/schematics_bundle.zip`. Keep its **existing** R3 40 schematics (38 required).
4. Keep `config/catancraft/CatanCraft_R4A_Placement_Ready_Assets.zip` unchanged for previous defense survey commands.
5. Copy both supplied *new* sidecars to the **SERVER** `config/catancraft/` folder:
   - `CatanCraft_R5_City_Rework_And_Civic_Assets.zip` — original Blender ZIP, **unextracted**.
   - `R5_READ_ONLY_PROBE_INDEX.zip` — derived index, **unextracted**.
6. Keep all existing world files, town halls, industrial plots, claims, player-placed blocks, economy config, map JSON, vehicle/weapon mods unchanged. Do not run whole-city paste/reimport commands.

## Operator commands — no placement path exists

- `/catan r5 status` — confirms all R5 construction/migration disabled.
- `/catan r5 city a` — reports 20 city street/house groups and 2 civic sites from pinned source/index.
- `/catan r5 civic a commercial` — strict current-world old-state, support, above-grade source-air, map-center and ownership preflight for proposed Commercial District socket.
- `/catan r5 civic a logistics` — same for Logistics Center.
- `/catan r5 civic i commercial` — exact 180° south-facing City I world coordinates; NO second rotation.
- `/catan r5 group a front_public_park` — checks only this group's recorded voxel old states in the actual world.
- `/catan r5 group a cleanup_tree_16_10` — checks tree removal group, but **does not remove the tree**.
- `/catan r5 group i logistics_service_connection` — checks City I service path.

All of these are **OP-only**. They may report mismatches when chunks are unloaded or when your existing city differs from Blender's authored R2 fixture; neither case triggers automated replacement or terrain flattening. They never request chunk loads or force current blocks to expected values. A status of zero mismatches means only the listed read-only checks currently matched; it does **not** approve world edits or purchases.

The civic preflight **always reports unmet pre-installation dependencies** for public groups (e.g. tree cleanup/sidewalk); this is intentional, because v0.1.6 does not persist committed R5 group revisions. House group checks explicitly flag that full-house R1 vs R2 SHA-256 baseline detection and rebasing is not implemented. No payment, checkpoint/gate, building assignment, journal, or undo code is introduced.

## Combined acceptance checklist

### Install and archive safety
- [ ] 0.1.6 loads on both client and server, no duplicate old JARs or Mixin errors.
- [ ] `/catan map assetstatus` = **38 ready / 0 invalid**.
- [ ] `/catan r5 status` reports **READ-ONLY**, construction disabled.
- [ ] `/catan r5 city a` shows **20 groups + 2 civic sockets**.
- [ ] Temporarily rename/remove the R5 ZIP or index in a **stopped-server backup copy**, reload and verify it refuses safely; restore the correct, exact files. No blocks should have changed.

### R5 live-world survey
- [ ] `/catan r5 civic a commercial` reports exact old-state / support / source-air checks or mismatches, without placing anything.
- [ ] `/catan r5 civic a logistics` runs independently and reports its prerequisite service-connection group.
- [ ] `/catan r5 civic i commercial` checks the south-facing map orientation.
- [ ] `/catan r5 group a front_public_park` reports old-state comparison with no block changes.
- [ ] `/catan r5 group a house_1_coherent_facade` refuses full deployment until whole-house R1/R2 migration signatures are implemented.
- [ ] If a check reports `UNLOADED chunk`, **do not assume pass**, visit/load site then retry.
- [ ] No Town Hall, industrial plots, roads, resource paths, or R4 reserved defense regions were altered.

### Prior features to test together (not yet accepted)
- [ ] v0.1.3 crowbar: a **purchased** Superb Warfare pickup and LAV-150 cannot be packed with a crowbar; item isn't awarded, vehicle not deleted, denial log appears; persists after restart. Ordinary unpurchased Superb Warfare vehicle retrieval still behaves normally.
- [ ] v0.1.4 Logistics: buy in City L2; +10% factory processor output; no duplicate civic purchase; +15% at building L2; no change to other cities or input costs.
- [ ] v0.1.5 R4: `/catan defense list a` and `/catan defense preflight a access_checkpoint` can read the unchanged R4A ZIP; `bunker_w` denied.
- [ ] v0.1.2 terrain protection: TaCZ glass, Superb Warfare collisions/explosions still cannot damage authored scenery.
- [ ] Nation GUI accordions, economy save/load, monuments and R3 industry plots still function.

## Future R5 Java development gates

Do NOT attempt a world-writing R5 build from this code. Before opening purchasable civic structures or streetscape updates, implement a durable per-feature per-city journal, exact source branch detection (R1 vs R2 house signature), actual-before snapshots including tile entity data, mixed-state recovery, transaction locks and ownership, protected geometry checks, payment reservation/refund for new civic purchases, idempotent visual realization for previously bought data-only civics without charging them again, and replay safety through server restart. Require tests on copies of the user's developed world, including all R4 restrictions, before rollout.

**R5 source is designed to preserve town halls, roads, R3 factory slots and R4-A reservation cuboids. Static proof does not authorize deployment into an unknown live save.**
