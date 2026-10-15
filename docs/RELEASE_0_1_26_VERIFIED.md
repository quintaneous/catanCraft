# CatanCraft v0.1.26 — verified producer integration

Date: 2026-10-15. Status: source integrated; native validation passed; owner in-world acceptance pending.

## Exact release baseline

- Repository: quintaneous/catanCraft; branch: v0.1-foundation. No main merge.
- Validated candidate: 59e7e780585ab58e72799d7a9c9d0aa464f93e42.
- Published gameplay source: 5a7b536ed5af86c9ed884d0f70d31664da5af1f7.
- GitHub Actions run: 38799637845; job: 116644994977; both validation and guarded source publication succeeded.
- Artifact: producer26-validation, ID 12003233434.
- JAR SHA-256: 1fc86bec3888605606011f82297f779d7a7c6e23ee4d641b2bd09c475b98fb28.
- Published source ZIP SHA-256: 4936099b0dcc076184c626b84f287b56a23a9ec8deb08974cc737d2c9411610d.

This documentation commit does not change the tested gameplay source. The delivered JAR is the exact passing CI binary, not an unverified rebuild.

## Completed verification

The native Forge run completed all 50 required tests successfully: the retained 36 Town Hall tests and 14 producer tests. It covers both supported rotations, all 15 asset pins and 12 adjacent deltas, grouped L1-L5 upgrades and charges, paid-L5 zero-cost visual retrofit, duplicate no-op, inventories/crop/fluid state, interrupted forward/reverse writes, receipt settlement/refund windows, corrupt journals, authorization, occupancy, missing barrels and last-plot conflicts. Delayed L5 checks passed.

The previously failing tests needed fixture corrections: removing a block entity while retaining BARREL allowed Minecraft to recreate it; stale drops from earlier inventory fixtures contaminated the later guard batch. The correction removes/restores the actual test barrel with its NBT and clears prior fixture item drops before the guard batch. It does not remove deliberately created guard-test occupancy or weaken production guards. The correction was already present at the candidate head rechecked for this validation.

Independent delivery audit checked the original Blender R1 archive SHA-256 09baf1ca455c1ff6c7fa30113add2a38c785dd973b4bba24e02588476e7eef71, all 276 member hashes, all 15 schematic byte identities, and every exact adjacent delta and full read-context contract. The packaged producer resources match those original bytes. All 160 published source files match the locally inspected candidate; all 25 candidate-manifest entries match; the nine existing TownHall-prefixed Java files remain unchanged.

## What changes

Applicable Farm/Lumberyard/Quarry plots now evolve with the territory's producer level. All applicable plots are preflighted as one journaled transaction; writes are limited to exact design changes. Already-purchased levels can receive free visual catch-up. Normal /nation upgrade producer commands and the existing management dashboard use the same transaction and retain the existing next-tier price.

This does not give every territory all three resource types. Strategic producers, economy formulas, Town Hall repair, other accepted buildings, map layout and defenses are not changed. Producer schematics are bundled; no loose asset installation is required. Startup may reconcile an existing incomplete transaction, but does not initiate a new purchase or retrofit.

## Developed City A acceptance

1. Stop the server/world cleanly. Back up the entire existing developed world, including its data, journals and authorization marker. Preserve its existing folder name and location. Replace the old CatanCraft mod with catancraft-0.1.26.jar wherever the mod is installed, with no duplicate old JAR. Keep the current Forge/modpack/configuration and existing world.
2. Load the same authorized R7 development world. Record City A's producer level, resource balances and representative producer-barrel contents. Leave all applicable producer plots, including their structures and interiors, unoccupied; collect nearby dropped items. Do not delete markers or alter blocks to force acceptance.
3. As operator, run /catan producer status a. This checks the current source and reports the paid level, checked cells and design changes without a purchase or block writes. Status is not a guarantee that later occupancy checks will pass. Stop and retain any refusal message.
4. For an already-paid level, run /catan producer retrofit a confirm. Expected: the applicable visuals catch up to the purchased producer level, with no upgrade charge and no level change. At an already-matching level, zero changes is correct. Town Hall level is unrelated to producer level.
5. Check only applicable producer appearances, access and preserved inventories/crops/water; nearby buildings and labels should remain intact. Record command output. Natural production may change balances over elapsed time, but retrofit must not deduct an upgrade price. Repeat the retrofit: expected zero additional writes and charge. Save/stop/reload normally and verify persistence once.
6. Only when intentionally purchasing a new tier below L5, use the existing dashboard action or /nation upgrade producer a. Expected: one next-tier charge and one producer-level increment, with all applicable visuals advancing together. Do not purchase again merely to obtain already-paid visuals.

Operator /catan producer resume a confirm and /catan producer rollback a confirm are recovery tools, not routine smoke tests. On a refusal, retain all blocks and journals and report the exact message. Do not reset the city, rename the world, delete receipts or force a schematic paste. Do not roll back the mod alone after a world-changing test; restore a consistent stopped-world backup when reverting.

## Limits and next gate

No owner world was opened or modified for this validation. Injected interruption and serialized reload tests do not establish separate-process power-loss behavior or owner visual/modpack acceptance. Authorization remains restricted to the existing backed-up R7 development world. No multiplayer exercise or repeat of accepted Town Hall tests is requested. Crowbar #6 and general older prestige-purchase recovery remain outside this release. Owner producer smoke/visual acceptance is the next gate before expanding scope.
