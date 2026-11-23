# Pine Reach forest pilot

The B1 approach now has six irregular stands of mixed spruce and birch on its existing upland shelves. Younger trees and lower crowns break up the outer edge. Boulder clusters, small fallen logs, ferns and patches of podzol and coarse dirt sit among the original trees. The opening over the road and the last approach to the bridge remain visible.

| Measure | Result |
|---|---:|
| New standing spruce / birch | 76 / 31 |
| Fallen logs / rock clusters | 35 / 85 |
| Ground replacements / understory blocks | 2,128 / 1,769 |
| Changed blocks / affected X/Z columns | 14,943 / 5,596 |
| Actual extent, inclusive XYZ | (−391, 81, −642) to (−115, 111, −508) |
| Nearest edit to the surveyed road center / B1 anchor, maximum-axis distance | 33 / 99 blocks |

The layout occupies Pine Reach in the existing R7 map. It makes no excavation, bridge, road, city or river change. Full crowns use the same X/Z exclusion as tree trunks. Planning excludes the surveyed pavement and a twelve-block buffer, the middle sightline strip, registered city siege/restoration boxes and the transformed all-city wall guard coordinates. Individual tree shapes must fit entirely in original air above original grass. Ground replacements require exact original grass and isolated decorations require original air.

The placement file contains every original and replacement block, grouped into vertical runs at each X/Z. Both placement and restore verify the entire expected before-image before the first write. Using the pristine region NBT, the independent replay compared all 14,943 before and after states and restored every one to its original value. A mismatch deliberately introduced at the far end of the plan aborted before any writes. The replay used an in-memory copy. Long-term vegetation behavior, lighting and vehicle movement need an actual game session.

Use `mainland.build(world)` and `mainland.restore(world)` with the sibling `mainland-placements.json.gz`. The world adapter exposes `get(x,y,z)` and `set(x,y,z,state)`. Running `python3 -B mainland_verify.py` within this directory independently replays the stored plan against the qualification workspace. The plan's decompressed SHA-256 is pinned in `mainland.py`. `mainland-details.json` includes each tree's species, height and anchor.
