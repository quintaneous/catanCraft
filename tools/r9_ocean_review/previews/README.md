# Coastal profile comparisons

Each sheet contains the same two camera views of a pristine R7 sample and the corresponding isolated R9 design. Open the PNGs at full size to inspect the cove beach, the estuary mouth, and their sidewalls.

- [Sheltered cove](sheltered_cove_review.png) uses the northern reference window starting at X −368, Z −1024. The straight wall in its R7 sea-level view is the exposed edge of the cropped grassland, seen from outside the authored chunk edge.
- [Headland estuary](headland_estuary_review.png) uses the northern river window starting at X −40, Z −1024. The sea-level camera faces inland through the mouth.
  
Both volumes span 112 × 64 × 96 blocks in X/Y/Z and start at Y57. The top surface of the top water block at Y75 is Y76. The sea-level cameras sit 2.4 blocks above that surface. These window positions define the comparison only. R9 installation sites remain open.

The renderer reads the before blocks from the pristine R7 Anvil regions and the after blocks from the supplied Sponge v2 schematics. It draws block shape, topography and water depth in a simplified daytime palette. Plants use approximate shapes. Biome tint, foliage alpha textures, lighting propagation, wave dynamics and external terrain are outside this rendering. The sheets are offline design views; a Minecraft boat test would check movement and real client appearance.

## Reproduce

Run from the qualification workspace with Python 3.11+, Pillow, NumPy, Open Sans and a C++17 compiler. The source includes the existing R7 NBT reader and `resource-ui/tools/walls53/audit_sites.py`. Example:

```sh
PYTHONDONTWRITEBYTECODE=1 python3 receipts/ocean-prototype/previews/render.py --workspace "$PWD"
```

The renderer compiles into a temporary directory. It writes both review sheets and `view_sources.json` to the preview directory. The JSON records the contributing region and schematic hashes, PNG hashes and camera coordinates. Use `--coast` or `--output` to select different asset and output directories.
