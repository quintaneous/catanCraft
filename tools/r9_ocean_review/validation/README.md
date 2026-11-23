# R9 prototype compatibility checks

The four standalone schematics passed an independent offline readback against Java 1.20.1 data. They expand to **1,644,034 cells**, including each asset's encoded air and water. The pristine R7 manifest matched its pinned SHA-256. All 40 original files matched their recorded sizes and hashes, including the original level metadata, all 16 region files and the bootstrap assets. The active gameplay source contains the same map configuration bytes as the pristine input.

| Asset | Dimensions X × Y × Z | Proposed use |
| --- | --- | --- |
| Sheltered cove | 112 × 64 × 96 | Visual study of a possible coastal transition |
| Headland estuary | 112 × 64 × 96 | Visual study of a river connection and approach |
| Brinewatch Pilot Station | 55 × 50 × 47 | Lighthouse in the west reservation |
| Brinewake | 74 × 39 × 48 | Grounded freighter wreck in the east reservation |

Each file decoded as a named Sponge v2 schematic at Java data version 3465 with a zero offset. Checks cover dimension and offset tag types, unique palette IDs, published vanilla block names and explicit state properties, varint decoding and the exact voxel count. The assets carry no entities, populated block-entity records, loot or commands. The report distinguishes plain fluids, empty air and other blocks. Waterlogged blocks count as other blocks, and that category includes vegetation and other passable states. These counts describe the encoded asset, not collision geometry.

## Offshore registration

The atlas uses half-open X/Z rectangles. Clearance means the gap in blocks between rectangle boundaries. The full rectangular box, including every air cell, was checked. The lighthouse turns 180° around local (27,8,23), registering at (−1410,75,−430). The wreck registers local (37,15,24) at (1390,75,280) without rotation. These pivots are conventions for a future builder and differ from the files' zero offsets.

| Site | Margin to its reservation | Gap to the authored mainland chunk square | Gap to proposed outer border |
| --- | ---: | ---: | ---: |
| West lighthouse | 20 blocks | 358 blocks | 163 blocks |
| East wreck | 11 blocks | 329 blocks | 173 blocks |

Brinewatch encodes air around its structure. A full rectangular write into a flooded ocean would replace water there. A future placer must preserve external water while providing the intended dry interior and support its foundations. Brinewake includes its own shoal and water. Its placement needs to blend neighboring seabed, water levels and biome data. The coastal examples use mainland sample windows for visual comparisons; their registration does not authorize writing to those chunks.

## Checks for the first integrated test world

1. After R8 approval, verify the accepted R8 baseline and create a separate disposable R9 world. Diff actual changed cells and region coverage for that world. Protect city footprints and future building envelopes, roads, bridges, river beds, all registered wall guards and the central court. Compare city and monument IDs, claim boundaries and territory adjacency with the accepted baseline.
2. Inspect coast seams at actual authored chunk boundaries, bed and surface elevation, ocean biomes, lighting, fluid updates and the outer world border after save/restart. Sail one uninterrupted route around the map and dock, leave and return at both sites. Check land access and the previous river routes.
3. From a fresh nation in each starter slot, rerun all nine source-matched progression and restart scenarios covering 16 cities. Check physical weapon and vehicle counters, occupied plots, wall purchases and restoration, protected terrain, national resources and ownership. Keep the current three central monuments' capture and reward paths working.
4. Assess chunk load time, memory and distribution size for the full ocean world. An existing developed save needs a separate backup-and-compare migration rehearsal. Offshore claim, capture and reward behavior requires its own future integration test if enabled.

## Reproduce

From the qualification workspace, run the following. The script reads the clean source world, the `resource-ui` checkout, the existing P3 Java 1.20.1 block registry, the four schematic files and `atlas/atlas.json`. Use `--prototype PATH` if the prototype pack is elsewhere. Set `--registry-dir PATH` to use another copy of the same pinned registry.

```sh
python3 receipts/ocean-prototype/validation/verify.py --workspace "$PWD"
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s receipts/ocean-prototype/validation -p test_verify.py -v
```

The four unit tests cover corrupt varints, wrong block counts, nested command/loot payloads, rotation boundaries and mainland/border overlap. The JSON report pins the four actual schematic files and atlas used in the separate asset audit. The audit ran without starting a server, placing blocks or opening a connected client.
