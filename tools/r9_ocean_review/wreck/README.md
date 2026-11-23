# Brinewake grounded freighter

Brinewake is a standalone offshore exploration landmark for Minecraft Java 1.20.1. A broad rocky shoal supports the broken hull. The forward half carries a flooded cargo hold, two containers, a standing mast, and a weathered cargo derrick. A two-block-wide salvage walkway crosses the flooded break to the surviving wheelhouse section. The aft cabin has an open doorway, a ladder to its upper deck and a small bridge overlooking the wreck. Its two timber boat landings give players a choice on arrival.

Oblique view of the encoded wreck geometry — see `Brinewake_preview.png` in the review archive or regenerate locally.

## Reservation and mounting

The atlas proposes the east reservation at X 1,390 / Z 280. The pivot is local **(37, 15, 24)** at world **(1,390, 75, 280)**. Use the north-west lower corner **(1,353, 60, 256)** and zero rotation. Schematic offset is (0, 0, 0). X increases east, Z increases south. The bow faces west toward the approach. The 74 × 39 × 48 block stamp occupies X 1,353–1,426, Y 60–98 and Z 256–303, inclusive.

The tile contains a full rectangular volume of source water, air and solid seabed. Local Y15 holds the top water block (world Y75) and the water surface is Y76. Treat the **full volume** as the write footprint when testing placement. Use a disposable flat review area or a new ocean prototype. The surrounding ocean floor and edge transitions still need blending during final integration. A no-air paste into occupied space would leave obstructions. The original four corners are the bounding box of a standalone sample.

| Review point | Local XYZ | Proposed world XYZ | What to inspect |
| --- | --- | --- | --- |
| West entrance to north approach | 1, 15, 4 | 1354, 75, 260 | Travel east along the north side of the bow, then turn south to the landing. |
| North timber landing, standing | 33, 17, 6 | 1386, 77, 262 | Reach the dry deck by climbing toward the center of the ship. |
| West entrance to south approach | 1, 15, 40 | 1354, 75, 296 | Travel east keeping the hull to port, then turn north to the landing. |
| South timber landing, standing | 20, 17, 38 | 1373, 77, 294 | Board the wider forward deck from the bow side. |
| Copper-bordered deck marker, standing | 35, 20, 15 | 1388, 80, 271 | Candidate survey or capture location on the forward deck. |
| Lower cabin, standing | 58, 19, 28 | 1411, 79, 284 | Follow the narrow salvage crossing over the split hull to the aft cabin. |

## Geometry checks

The independent decoder reads the compressed Sponge v2 NBT, 1.20.1 data version 3465, palette and 138,528 serialized block entries. It found 374 keel columns with uninterrupted solid support to the base. Each approach allows a 3×3 boat footprint with at least three source-water blocks of depth and two air blocks above. The two dry paths from the landings reach the marker in 11 and 38 cardinal moves, with two blocks of headroom and a maximum one-block change in floor elevation. A further 42 moves reach the lower cabin. No entity or block-entity records were present. The in-game dismount, exact collision, water updates after paste, player sightlines and ship-to-shore sailing need playtesting.

There are no active reward tables, charge points or operating objectives. The copper marker is entirely decorative. The matching terrain, public ownership and monument rules belong to a later integration.

## Reproduce

Run `python3 build.py` in this directory to generate `brinewake.schem`. Then run `python3 review.py > verification.json` to decode the asset independently, check its geometry and rebuild the preview. The build uses the Python standard library. The review image also requires Pillow and DejaVu Sans. The ship preview uses the actual schematic geometry with illustrative material colors, simplified rails and surface shading.
