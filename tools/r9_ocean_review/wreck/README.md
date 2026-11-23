# Brinewake II

The east-coast freighter grew by fifteen blocks from bow to stern, with four more blocks across its widest deck section. A second cargo hatch sits forward of the open hold. Two tiers of cargo, a deck winch and a taller twin-post derrick make the longer working deck recognizable from shore. The salvage crossing spans the split hull and connects both landings to the aft cabin and wheelhouse.

Orthographic view of Brinewake II — see `Brinewake_preview.png` in the review archive or regenerate locally.

| Measured in blocks | Original | Revised |
| --- | ---: | ---: |
| Hull length | 61 | 76 |
| Widest beam across one hull section | 19 | 23 |
| Schematic footprint X × Z | 74 × 48 | 88 × 54 |
| Schematic height | 39 | 40 |
| World elevation of landing floor block | 76 | 76 |
| Standing feet on landing | 77 | 77 |

Length and beam are measured from the hull plating at local Y16. Doorways, ordinary railings, stairs and the five-block ladder retain Minecraft player scale.

## East reservation

Use zero rotation. The bow points west. Mount the local pivot **(44, 15, 27)** at world **(1390, 75, 280)**. The lowest north-west corner is **(1346, 60, 253)** with a schematic offset of zero. The complete rectangular stamp occupies **X1346–1433, Y60–99, Z253–306**, inclusive. In the existing 96 × 96 reservation the nearest X edge is four blocks away and the nearest Z edge is 21 blocks away.

The top source-water block is local Y15, or world Y75. Water surface elevation is 76. The schematic includes its surrounding air, water and rocky shoal, so allocate the full stamp volume in a disposable world. Final seabed blending depends on the surrounding ocean terrain.

| Point | Local XYZ | World XYZ | Use |
| --- | --- | --- | --- |
| West entrance to north channel | 1, 15, 5 | 1347, 75, 258 | Sail east with the bow to starboard. |
| North landing, feet | 41, 17, 7 | 1387, 77, 260 | Turn toward the four-block timber landing and climb toward the forward deck. |
| West entrance to south channel | 1, 15, 45 | 1347, 75, 298 | Sail east with the bow to port. |
| South landing, feet | 22, 17, 43 | 1368, 77, 296 | Approach the southern staircase from open water. |
| Copper-bordered marker, feet | 44, 20, 17 | 1390, 80, 270 | Reach the dry meeting spot beside the forward hold. |
| Lower cabin, feet | 70, 19, 32 | 1416, 79, 285 | Walk across the span to the surviving accommodation deck. |
| Wheelhouse, feet | 71, 24, 30 | 1417, 84, 283 | Climb the exterior ladder and enter the wheelhouse. |

## Verification

The independent Sponge v2 reader decoded 190,080 block entries, 49 palette states and Java data version 3465. Each west-entry approach maintained three-block width, at least three continuous source-water blocks of depth and two air blocks above the entire tested straight leg. Dry paths from the north and south landings reached the marker in 13 and 48 cardinal moves. The lower cabin was another 47 moves away. The route from the lower cabin into the wheelhouse took 21 moves including the ladder climb. The decoder found 603 keel columns directly supported down to the bottom of the shoal.

The copper marker is decorative. There are no serialized entities, block entities, commands, rewards or active capture points in the asset. Boat dismounts, exact player collision, water updates after pasting, lighting and the full approach from the coast still require in-game review.

Run `python3 build.py` in this directory to rebuild `brinewake.schem`. Run `python3 review.py > verification.json` to decode the schematic, test geometry, and render `Brinewake_preview.png`. The build uses standard Python. The renderer also uses Pillow and DejaVu Sans. The preview shows the encoded block geometry with illustrative material colors and simplified railings.
