# Central monuments and coastal expansion

**The three capture areas currently resolve to claimable city territory.** A coordinate test against the exact clean map gives the following results on gameplay source `6ed6eaf`. The public district and the claim lookup independently include the same location. This reproduces the complaint with zero nations founded and requires no special city setting or case conversion. The displayed owner of each territory will depend on the actual save. These are offline map and asset measurements.

| Monument | Capture center X, Z | Territory at every sampled capture point | Registered placement footprint, inclusive X × Z | Schematic columns with blocks above Y80 |
|---|---:|---|---|---|
| Industrial Complex | −86, −80 | F, 613 block centers | −124…−40 × −104…−20 | 2,910 in F |
| Military Depot | 88, −35 | G, 613 block centers | 40…124 × −96…−20 | 3,015 in G |
| Refinery | 22, 107 | K, 613 block centers | −39…39 × 37…125 | 772 in J and 1,517 in K |

Capture checks use the player's actual position, a horizontal radius of 14 around the anchor block center and a vertical tolerance of 12. The sampled counts use block centers inside that circle. The continuous circles also fit entirely in the listed cities. The final column uses the shipped Sponge schematics, includes decorative structures, and counts each X/Z column once. It describes the authored asset. A previously developed player's world can differ.

## Smallest change that covers the whole court

Give the full court public priority for location and ownership checks over X/Z **−150 through +150 inclusive**. Today's public polygon stops at block +149 on the east and south sides because block centers are tested. Extending its two high coordinate bounds to +151 includes the last row and column. **601 columns** are currently outside the public zone, split across G (150), J (150) and K (301). Expanding the polygon alone still leaves both lookups active. The implementation needs public priority before the map-defined territory lookup and before the saved-data fallback for legacy territory IDs. Preserve ordinary ID-based city ownership and the monument activation/reward identities.

The proposed 90,601-column overlay covers every capture circle and all three registered structure footprints. It intersects none of the 96 development plot anchors, 16 siege boxes or 16 restoration boxes. The nearest siege/restoration boxes have a nearest-coordinate distance of four blocks in X/Z using the maximum-axis metric, with three intervening columns along the closest diagonal. The nearest registered wall guard column is 13 blocks away. Bridge B2's anchor is 16 blocks away and B3's is 31. These are anchor distances, not surveyed bridge deck clearances. Neutralizing all of F/G/J/K instead would affect 1,000,000 columns and 24 development plots. Implementing only the lookup change requires no terrain writes, bridge relocation, structure paste or claim-graph change.

**Acceptance cases.** From F/G/J/K, cross into the court on both sides of zero and at all four outer edges. Explicitly sample coordinates +149, +150, +151 and −151, −150. Check the HUD, `/catan map here`, `/catan territory here`, and the ownership-sensitive vehicle procurement lookup. Test inside a nation-owned surrounding city, at a valid factory counter and inside a save with a matching legacy boundary. Verify reward capture/contest still recognizes the Refinery's K-side capture point and that its J-side buildings also read as public. Confirm siege/restoration checks remain restricted to registered city areas. The existing tests and checklists for the current gameplay kit retain their original scope.

## Future coastal test envelope

The pristine world's saved border is centered on zero, stationary, and 2,000 blocks wide. All 16,384 authored chunks are present (128 × 128). For an outer-only prototype, freeze every existing chunk and add new authored chunks beyond them. Terrain transitions or estuaries inside the existing 24-block fringe would require an explicit changed-column map. If a designer measures a navigable band out from a coastline near ±1,000, a square border 2,700 blocks wide adds **12,516 chunks** for a 350-block band. A width of 3,200 adds **23,616 chunks** for a 600-block band. These counts exclude exterior rendering margin. Navigable depth, winding coastlines and an approach to an offshore monument need a separate route and boat trial.

| Current border row | Surface water span | Wet columns | Water depth, min / median / max | Columns at least 3 blocks deep |
|---|---|---:|---|---:|
| North, Z = −1000 | X = 27…48 | 22 | 1 / 3 / 4 | 14 |
| South, Z = 999 | X = −46…−28 | 19 | 1 / 4 / 6 | 15 |
| East and west | None at either border row | 0 | — | 0 |

These measurements give the terrain author two river mouths to survey before connecting a coast. For offshore monuments, survey a candidate site's X/Z bounds, navigable approach depth and clearance to the selected border before committing to a placement. Full road/bridge extents, any future forest placement, uninterrupted boat navigation, chunk-load performance and offshore footprints remain unsurveyed in this audit.

## Reproduce

From the shared project directory, with the pinned gameplay checkout at `resource-ui` and unmodified clean R7 inputs in `inputs`, run the command below. The audit reads the shipped Java registry, compares registered and actual schematic dimensions and offsets, checks clean-map bytes against the built-in map, verifies original region/level file hashes, checks registered wall guard columns, and reads the pristine world perimeter. Its output is deterministic.

```sh
python3 receipts/parallel-roadmap/terrain/audit.py resource-ui inputs/CatanCraft_Clean_R7_Input
```

[Map definition](https://github.com/quintaneous/catanCraft/blob/6ed6eaf31f946195f8b75fa869308c915537a6ed/src/main/resources/data/catancraft/maps/season1_river_bridges_v3.json) · [Asset placement registry](https://github.com/quintaneous/catanCraft/blob/6ed6eaf31f946195f8b75fa869308c915537a6ed/src/main/java/com/quin/catancraft/world/SchematicAssetRegistry.java) · [Map lookup](https://github.com/quintaneous/catanCraft/blob/6ed6eaf31f946195f8b75fa869308c915537a6ed/src/main/java/com/quin/catancraft/map/MapDefinitionManager.java) · [Saved territory lookup](https://github.com/quintaneous/catanCraft/blob/6ed6eaf31f946195f8b75fa869308c915537a6ed/src/main/java/com/quin/catancraft/data/CatanSavedData.java) · [R8/R9 feasibility](https://github.com/quintaneous/catanCraft/blob/6ed6eaf31f946195f8b75fa869308c915537a6ed/docs/MAP_R8_R9_FEASIBILITY.md)
