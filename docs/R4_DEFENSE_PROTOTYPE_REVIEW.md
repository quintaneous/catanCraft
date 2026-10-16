# CatanCraft R4 city defenses — review and integration gates

**Reviewed:** 2026-10-10. **Status:** Prototypes accepted for isolated testing; **not approved for live world installation**.

## Delivered and independently checked
- Eight valid Sponge v2 `.schem` files: 16-block straight wall, 7×7 corner, 25-wide gate OPEN and CLOSED, 17-long trench, bunker, tower, and checkpoint.
- All eight source schematic SHA-256 digests match `validation-report.json`; prototype ZIP integrity passes.
- Source validation reports 32/32 cardinal rotation/access passes; gate open→closed→open offline reference checks pass, including obstruction/stale-state scenarios.
- Proposed 15 reservation regions are outside the center city core and rear protected corridor in Blender's planar test. Four alternative overlaps are documented (two bunker/wall and two trench/front-wall).
- Vehicle gate/checkpoint preserve an 11-wide × 7-high opening; **no real vehicle clearance proven**.
- **No new R4 world positions, actual territory clearance, gate Java controller or city-wide installation schematics** supplied. All prototype schematics are air-inclusive and contain foundation voxels; direct paste into an existing city is unsafe.
- R3 buildings and authored world remain unchanged.

## Design decisions for engineering (not yet approved for live install)
1. Keep a **partial perimeter** at first, preserving the rear road/resource corridor and 11-wide main access. No automatic sealed wall ring.
2. Treat checkpoint and controllable gate as **mutually exclusive upgrades of one socket**. Prefer purchase checkpoint first, then gate upgrade after live geometry is tested.
3. Walls and corner/tower/bunker modules must use **small, independently verified sockets**, not one massive schematic paste. Include short infill/end cap variants if gaps appear during plot survey.
4. **Defer underground trench excavation** in the first build. Do not remove original terrain without explicit siege/restoration rules; request raised/above-ground variant if terrain cutting is needed.
5. Never overwrite city streets, protected plots, Town Halls, purchased factories or original terrain outside an explicit mask; never blindly paste `air` from the module's rectangular volume.
6. Gate changes must apply **only the changed leaf blocks (154 reported delta cells)**. Require ownership/war permission, occupancy checks using full entity AABBs, stale-world preflight, persisted open/closed state and safe-open recovery after interrupted change.
7. Purchases require saved defense IDs, socket IDs, rotations, transactions, before-state snapshots, rollback and siege/restoration isolation.
8. Permanent sandbags are NOT an R4 module; temporary player-placed sandbags remain a future siege-phase exception with cleanup.

## Blockers before deployment
- [ ] User validates v0.1.2 TaCZ window and Superb Warfare block-grief protection in Minecraft; issue #4 stays open until confirmed.
- [ ] Survey all territory candidate defense bands against roads, rivers, bridges, slopes, existing developed cities and registered siege bounds.
- [ ] Select explicit world anchors/socket IDs, terrain heights, full-rotation transforms and independent per-socket write masks for a north-facing and south-facing city.
- [ ] Obtain actual model clearance for purchased Superb Warfare pickup and LAV-150: gate opening, road straight-through, turning sweep.
- [ ] Implement R4 defense data storage, secure purchases, placement snapshots/rollback and gate controller. Refuse if candidate footprint is unsafe rather than bulldoze or reposition.
- [ ] Validate installation in disposable backed-up world before enabling on developed season save.

## Recommended phased rollout
- **R4-A:** checkpoint, wall sections, guard towers, and bunkers on confirmed sockets, optionally OP-only dry-run first. No trenches or changing gates yet.
- **R4-B:** functional gate with entity collision/preflight, open/close persistence and rollback tests.
- **R4-C:** city siege/damage/restoration and trench policy; all gated by separate acceptance.

## Source of these facts
Blender shared Library `CatanCraft_City_Defenses_R4_Prototypes.zip` and `CatanCraft_R4_Placement_Proposal.md`. The static checks are not Minecraft runtime tests.

**Do not put the eight R4 `.schem` files into the R3 live `schematics_bundle.zip` yet.**