# Brinewake scale comparison

Open [Brinewake_hull_comparison.png](Brinewake_hull_comparison.png) to compare the original and revised shipwrecks. The new hull is 15 blocks longer and its widest cross-section is four blocks broader. Docks, masts and the three-block sideways displacement of the stern do not count toward beam. Each panel has a 10-block scale.

The renderer decodes each supplied Sponge v2 schematic. It measures the length and maximum transverse plating span in a single section at local Y16, the first block layer above the shared source-water top block at Y15. It draws the actual above-water geometry in simplified material colors. The centered orthographic views share the same projection, magnification, lighting and illustrative sea plane. Their camera settings and input hashes are recorded in `comparison.json`.

Reproduce from the qualification workspace with Python, Pillow, NumPy and Open Sans:

```sh
python3 receipts/ocean-v2/comparison/render.py \
  receipts/ocean-prototype/wreck/brinewake.schem \
  receipts/ocean-v2/wreck/brinewake.schem
```
