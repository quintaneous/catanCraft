# R9 offshore reservation atlas

[R9-offshore-atlas.png](R9-offshore-atlas.png) overlays candidate ocean routes on the actual R7 map. North is negative Z. The mainland colors come from saved surface blocks sampled every four blocks, so roads and river bends follow the original map. The 16 city centers and four bridge anchors come directly from the map configuration. The dotted line is the live ±1,000 border. The thin solid line is the edge of the 16,384 authored chunks, at block coordinates −1,024…+1,023 inclusive.

## Shoreline and access

The design proposes a 3,200-block square centered on the origin. A 96-block strip immediately outside the authored mainland is reserved for sculpting the coast. A coastline anywhere in that strip gives **480–576 blocks from shore to the proposed border along a side**, before accounting for islands. Detailed beach contours, depths and islands still need authoring. Bringing all those chunks inside the new border would add 23,616 chunks before a rendering margin.

R7's top water blocks are at **Y75**, so the surface plane is Y76. The two river exits carry water through the 24-block fringe between the old playable border and the outermost authored rows. The offline survey found a continuous eight-block-wide path in each fringe, with at least three water blocks and four air blocks above. Its center can shift one block per row as the river bends.

| Exit | At current playable edge | At outer authored row | Centerline at outer row |
| --- | --- | --- | --- |
| North | Z −1000, wet X 27…48 | Z −1024, wet X 33…54 | X 44 |
| South | Z +999, wet X −46…−28 | Z +1023, wet X −52…−34 | X −43 |

Match those centerlines and water level when authoring the exterior estuaries. The proposed first 48 exterior rows reserve an eight-block-wide throat at a depth of at least three water blocks. The outer approach widens to 24 blocks with at least six water blocks. The continuous square boat circuit is 32 blocks wide and calls for at least eight water blocks throughout. The circuit follows X/Z ±1,220, and its four right-angle joins each have the full 32×32 turning area. Each site has a dedicated 32-block-wide approach with at least six water blocks up to the reservation edge. A grounded wreck or pier will need its own shallower terminal pocket.

## Four survey reservations

Coordinates below use X then Z. Every 96×96 reservation has a notional survey marker at Y76. Those markers are authoring references. The atlas does not spawn marker blocks, assign events, or award rewards. Distance to the frozen edge and distance to the future border are measured from the closest side of the reservation envelope.

| Site | Survey center | Approach | Frozen edge clearance | Proposed border clearance | Work in this review |
| --- | --- | --- | ---: | ---: | --- |
| West harbor light | −1410, −430 | From east | 338 blocks | 142 blocks | Lighthouse pilot and two landings |
| East grounded freighter | +1390, +280 | From west | 318 | 162 | Wreck pilot with north and south boarding |
| North reserve | +510, −1420 | From south | 348 | 132 | Unassigned space |
| South reserve | −495, +1410 | From north | 338 | 142 | Unassigned space |

For the lighthouse, align local block (27,8,23) to world (−1410,75,−430) and turn 180°. The primary landing at (−1385,76,−431) faces the east approach. For the freighter, align local (37,0,24) to (1390,60,280) without rotation. A boat coming from the west can split around the bow toward the proposed north approach (Z ≤260) or south approach (Z ≥296). Boarding targets are (1386,77,262) and (1373,77,294). The JSON includes indicative interior waypoints. Both terminals need collision, draft and walk-off checks against the eventual seafloor.

## Checks and reproduction

[atlas.json](atlas.json) carries half-open X/Z bounds in the order `[minX,minZ,maxXExclusive,maxZExclusive]`. The checks verify that every site and proposed lane has zero area inside the original authored mainland, all four sites stay disjoint and within the proposed border, each meets only its own terminal approach, and all 12 external lane rectangles form one connected network. Each terminal and its approach share an edge. These geometry checks establish the reservation layout; no world edits or game runs were made. In-game tests still need to cover a full boat circuit, both estuaries in both directions, boarding at both assets, border behavior, chunk and lighting seams, and restart.

From the qualification workspace, run:

```sh
python3 -B receipts/ocean-prototype/atlas/build_atlas.py resource-ui inputs/CatanCraft_Clean_R7_Input /tmp/r9-atlas
```

The generator requires Python, NumPy and Matplotlib. It reads the 16 original region files, heightmaps, level metadata, protected geometry extent and registry, checks pinned hashes and writes only the output directory. The pristine input manifest SHA-256 is `e7ecd5d0f0ac5c1457be5c234d2e5e08c9239007787b20e0bdc45b3e2b8d862d`. The map JSON SHA-256 is `e50d9753c807b5fe6f8fdee9765ae09640d951cc0ba2d59f1612f0923485c986`. Gameplay source is `6ed6eaf31f946195f8b75fa869308c915537a6ed`.
