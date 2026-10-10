# CatanCraft R3 six factory integration — development and QA

Blender delivered six valid Sponge v2 31×47×31 physical schematics. Static placements and NBT metadata verified; Minecraft client acceptance pending. This is a **test build**, not a completed production-safe migration.

## New factory assets / economy IDs

| Building ID | Physical schematic | Product | Unlock |
|---|---|---|---|
| textile_mill | textile_mill.schem | Fabric | City L2 |
| refinery | fuel_refinery.schem | Fuel | City L2 |
| concrete_plant | concrete_plant.schem | Concrete | City L2 |
| machine_shop | machine_shop.schem | Mechanical Parts | City L3 |
| chemical_plant | chemical_plant.schem | Explosives | City L3 |
| electronics_factory | electronics_factory.schem | Electronics | City L3 |

Three industrial slots **plot_1, plot_3, plot_4**; nine possible factory types including Vehicle, Weapons, Steel. The existing Quarry (plot_2), Farm (plot_5), Lumberyard (plot_6) are permanently reserved raw producers. One copy of each factory type per city; no automatic eviction or swapping. Each city can specialize. Uses exact road-facing offsets and rotation tables from R3, including the existing west-facing Steel Mill.

## Commands

`/nation build <territory> <building_type> <plot_1|plot_3|plot_4>`

Example: `/nation build a textile_mill plot_1`. Omitting the plot chooses the first open industrial plot. The **/nation Territories → Construction** accordion now offers each eligible free plot for each industry. Economy recipes and level requirements are unchanged.

## Persistence and safety

Selected plot is already stored in BuildingInstance.plotId NBT; the physical placement marker uses the selected plot. Placement validation and checking the marker occur before charging national stockpile resources. Routine visual refresh now checks existing revision markers without forcing identical physical assets to repaste. Legacy fixed Vehicle/Weapons/Steel slots are inferred by type when a plot field is blank; any pre-existing **data-only** industry without a plot blocks additional industry purchases in that city until manual migration. No nation resource or building record is silently deleted.

**Known limits:** This integration does not implement a fully atomic block-by-block world rollback if the Minecraft engine throws during schematic paste; operators must back up before testing. Old data-only building instances are preserved as-is, not automatically converted. A dedicated guarded R2 house migration is **not included**. The R2 village-house detail pass and previous developed-city R2 visuals should be deployed separately only after a safe guarded migration path has been implemented and tested. Do not use broad full-city paste commands on an existing developed world.

## Preinstallation / single-player test

1. STOP server and client; back up whole world (including data and map config). Replace only CatanCraft JAR in both mods folders. Keep Forge 1.20.1 / Java 17 and latest TaCZ / Superb Warfare dependencies.
2. Replace `config/catancraft/schematics_bundle.zip` with the R3 compatible **38-required** schematic asset ZIP. Remove stale loose override schematics from `config/catancraft/schematics/` for changed names, after backup.
3. Restart and run `/catan map reloadassets`, `/catan map assetstatus`; expect **38 ready, zero missing/invalid**.
4. OP: fund treasury/resource stockpile and upgrade City A to L2, then `/nation build a textile_mill plot_1`. Expect new mill physically at vacant plot_1 and one national money/resources charge.
5. Attempt the exact same type or same slot twice: refused without second charge. Purchase another factory on plot_3 and verify it faces the road. Upgrade the city to L3 and use plot_4 for a Machine Shop/Chemical/Electronics Factory in a disposable test copy.
6. Confirm factory normal output every 15 min when supplied and target stock; it should produce 8 Fabric per batch for Textile, 8 Fuel, 8 Concrete; 6 Mechanical Parts, 6 Explosives, 6 Electronics, per approved inputs.
7. Restart; confirm plot allocations, levels, stockpile, physical facades, and nation ownership persist; repeated `/catan map refreshassets` should skip unchanged marked factories, but avoid all admin force-refresh workflows on developed worlds unless specifically approved.
8. Perform test in both a north- and south-facing city. Ensure original Town Hall/roads/houses and surrounding raw producers remain intact. Verify an existing Steel Mill or Weapons/Vehicle Factory is not overwritten.
9. Inspect nighttime interior lighting, door protection/interactions, and vehicle loading access. Actual Minecraft collision, shaders and door permissions were not modeled.
10. Regression: `/speed`, capture reward chat receipt, TaCZ Glock/AK ammo, Superb Warfare vehicle deployment, non-retrievable nation vehicles, R1 monument capture and persistence.

## Deferred

- R2 guarded residential-house migration and developed-city visual-polish update.
- Physical placement for Commercial District / Logistics Center (data-only).
- Migration of existing unassigned/data-only factories; manual explicit slot mapping required.
- Guaranteed transaction-level rollback for failures mid-voxel paste and world snapshots.
- Full equipment purchasing GUI / multiplayer contested capture.

Source: Blender R3 Main Development Handoff and R3 Factory Placement Contracts. R3 static-only review is not a claim of Minecraft client acceptance.
