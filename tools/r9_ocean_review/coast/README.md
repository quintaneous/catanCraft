# Two R9 coastline review tiles

The cove has a curved sandy beach with wooded slopes, two low offshore rocks and a sheltered anchorage. The estuary carries a narrower river between a high western headland and the eastern ridge. Stone and andesite faces alternate with tuff strata, wet gravel and clay. Oak canopies and spruce crowns are built into both landscapes. Each tile is an independent study; the two edges do not stitch together.

| Java 1.20.1 schematic | Tile dimensions X × Y × Z | Reference lower NW corner, X/Y/Z | Decoded terrain range | Tested route |
| --- | --- | --- | --- | --- |
| `r9_sheltered_cove.schem` | 112 × 64 × 96 | (−368, 57, −1024) | Y60–101 | Open water (54, 0) to anchorage (54, 35), 35 horizontal steps |
| `r9_headland_estuary.schem` | 112 × 64 × 96 | (−40, 57, −1024) | Y59–105 | Open water (67, 0) through estuary to inland cut (59, 95), 103 horizontal steps |

The path coordinates in the table are local X/Z. The reference corners identify the matching R7 comparison windows for preview cameras. They are **not placement recommendations**. The samples contain a complete rectangular volume, including terrain and air. Keep them in standalone art review until the final R8 mainland, protected-area map and R9 shoreline transition are agreed. Use a disposable isolated art world for any manual inspection. No live world, existing authored chunk, economy, claim, construction journal, or server config was changed.

## Water and route evidence

The pristine R7 save has source water at Y75 and air at Y76 at the measured northern and southern river mouths, including their outer authored edge. Both tiles use the same water block Y75 (local Y18). The bottom is Y57, so the local vertical offset always equals world Y minus 57 in these comparisons. Local +X is east and +Z is south. Both Sponge v2 assets have Offset [0, 0, 0] and DataVersion 3465.

The decoded cove reaches 15 blocks of depth, the estuary 16. For every step of each orange line in **navigation.png**, the test requires a five-by-five footprint with at least three contiguous source-water blocks per column and two unobstructed air blocks above each column. At an inlet or outlet touching the schematic edge, the test covers only the portion of the footprint inside the tile. The chosen route has at least six blocks of depth along the cove centerline and at least three along the estuary centerline. **verification.json** includes the per-step centerline, material counts, coordinates and checksums.

These are static, local passages. Each outer join, boat turn, docking approach, broader ocean circuit, biome, light and shoreline seam still require map integration and in-game trials. The supplied surface and geologic strata imply no fixed border size, final ocean bathymetry, or acceptance of these locations.

## Reproduce

`build.py OUTPUT_DIRECTORY` regenerates the two schematic files with Python 3 and NumPy. A second build produced identical bytes. Both assets decoded in the project's independent R7 NBT reader and the vendored nbtlib reader. Reproduce the geometry and reference checks with `python3 -B verify.py QUALIFICATION_WORKSPACE TILE_DIRECTORY`. It reads the verified R7 source at `inputs/CatanCraft_Clean_R7_Input` and the region reader under `resource-ui/tools/walls53`. Regenerate the navigation image with `python3 -B navigation.py QUALIFICATION_WORKSPACE TILE_DIRECTORY OUTPUT_PNG` using NumPy, Pillow and DejaVu Sans.
