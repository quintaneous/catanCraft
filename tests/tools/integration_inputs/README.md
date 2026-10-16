# Immutable integration asset reproduction

The gzip resources under `src/main/resources/data/catancraft/integration/` derive from the verified handoff archives, extracted outside the checkout. Existing R1 assets are never overwritten.

Reproduce into a separate output directory (requires Python 3, NumPy, and the extracted package's vendored nbtlib):

```sh
python3 tests/tools/integration_inputs/export_runtime.py \
  --assets /workspace/catancraft-inputs/audit-assets \
  --output /workspace/catancraft-inputs/runtime-reproduction
```

`MANIFEST.json` pins compressed JSON bytes, uncompressed JSON, and the nine unchanged corrected schematics. Gzip mtime is zero. The exporter checks every original/target producer schematic checksum, all nine producer sparse-route reconstructions, all 18 full house signatures, and both exact routes for each of six houses. Palette indices are YZX. House read contexts cover local Y0..46 even though bounded feature-export schematics are shorter. House tokens are integration IDs; source contracts supply exact signatures. No world data is used.
