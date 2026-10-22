# DEF-03: City A NORTH P2 integration audit

The owner's approval covers NORTH P2 appearance and its proposed footprint for an **isolated fresh-world test**. The P3 handoff preserves north while East/South/West artwork proceeds separately. Other sides/cities, final prices, weapons, functional gates and deployment remain gated. The original P2 manifest's pre-approval wording is retained as provenance; the later owner scope is recorded separately in the exported data.

## Immutable input

Input root: `/workspace/catancraft-p2-review-inputs-20261018/p2-assets/CatanCraft_Sector_Wall_P2`.

Original archive SHA-256: `039ff60335cf3d80f537adb689f11a833e3a15b7a2fcf54aeb6490bb2ae0b996`.

| Source | SHA-256 |
| --- | --- |
| `north_sector_p2_basic.schem` | `ccc09c654b399b8503207212d90cc6f16a6924cfdb11dea0142aa1cfcec60157` |
| `north_sector_p2_reinforced.schem` | `753131f9dc22dd73326cba7a078e2c462d28f6ffeddfe9ff2e2a7d3d3a8557bd` |
| Runtime resource `assets/catancraft/sector_p2/north.json.gz` | `685f117ad9ad12c95964b2393e94517f9647b2217028a12aee1fac5136a5a71b` |

`tools/export_sector_p2.py` checks all **180** input member hashes, original target/component hashes, exact disjoint component and sparse-mask reconstruction, reversible upgrade data, source-air and supporting-surface evidence, roads, protection bounds and retained caps. It never runs the original authoring scripts, changes schematic bytes, edits a world or produces a P1 repair route.

```sh
python3 tools/export_sector_p2.py \
  --input /workspace/catancraft-p2-review-inputs-20261018/p2-assets/CatanCraft_Sector_Wall_P2 \
  --runtime-root /workspace/catanCraft \
  --output /workspace/north-p2-reproduction.json.gz
```

## Exact plans and source reads

Both targets are XYZ **179×9×20** (32,220 cells), local anchor `[89,0,15]`, offset `[-89,0,-15]`, and City A world paste anchor `[-750,81,-838]`. Inclusive bounds are `[-839,81,-853]..[-661,89,-834]`. Basic/reinforced sparse fresh writes are **5,013 / 5,576**; the complete exact basic-to-reinforced change set contains **4,539** cells. Each variant retains ten internal components.

The source-and-support contracts contain all-air target volumes and **1,333 exact support columns** per variant at world Y80. Those are source-state checks, not excavation permission. The resource includes a bounded 39,380-cell read context, local `[0,-1,0]..[178,8,21]`, XYZ 179×10×22. It also includes 5,016 primary-road surface/headroom reads before deduplication, world X−755..−745, Y80..87, Z−846..−790. All primary-road cells above its surface decode as air.

The optional broad read context stops at world Z−832. Extending it another row would include historical composed houses/civic voxels at Z−831, creating an irrelevant source requirement for an unpurchased fresh civic slot. Actual target states, supports and road guards remain complete. The transaction engine protects the actual surrounding shell and current NBT separately.

## Current runtime reservations

The exporter parses current `SchematicAssetRegistry.java` directly and derives **19** City A envelopes: TH1–TH5, raw producers, factory masters and strategic sites. There are zero P2 write intersections. All three physical industrial slots are covered by their registered base envelopes; optional factory masters retain those footprints. Supplied civic envelopes and the entire city-core mask are also checked. All P2 writes remain outside the protected core beginning at Z−833. Exact authored road masks and their two-cell Manhattan margin have zero P2 write intersections.

The current map gives City A siege/restoration bounds Z−846..−654. P2 extends to Z−853. **843 basic / 933 reinforced write cells**, covering 224 XZ cells each, lie north of the current bound. Owner approval permits this localized north test footprint. Runtime authorization must allow only the immutable approved plan's exact changed-cell union in the separately authorized fresh demo. Do not widen map regions or grant the complete bounding box as general write authority. Current ownership, source, support, road, journal and entity checks still apply.

Comparison against the current `DefenseRevisions` data finds 17 City A candidate sockets with intersecting physical writes:

`front_w_00`–`front_w_04`, `front_e_01`–`front_e_04`, `access_checkpoint`, `tower_nw`, `tower_ne`, `tower_front_join_nw`, `tower_front_join_ne`, `tower_side_join_nw`, `tower_side_join_ne`, and `checkpoint_wing_w`.

These are candidate conflicts. P2 source/ownership/history checks must refuse existing incompatible owned or placed modules; the integration grants no overwrite or legacy conversion. The exported `runtimeAudit` records each socket's exact overlap counts and the audited runtime source/resource hashes.

## Corners, caps and gates

North retains sole ownership of NW/NE. P2 preserves their P1 world geometry and deck datum. The 12 explicit interface cases move the source-local cap plane to Z19 and neighbor deck start to Z20. A supported future neighbor and a coordinated north-owned transition are required before opening the six designated cap cells at each portal. Keep caps closed while other sides are absent or unavailable. P3 must allocate SW/SE separately; this audit does not invent their owner.

The three reviewed end-pier caps remain at `[-732,88,-849]`, `[-732,88,-848]`, and `[-716,88,-839]`. Their exact basic/reinforced states are included in the resource. The future-gate reservation is metadata only; no leaf, mechanism or automatic closure is provided. Gate design must account for those retained caps.

The two inherited reinforced emplacement sockets retain their world positions and explicit XYZ half-open clearance boxes. No SBW ID, model, ammunition, damage, traverse, firing or armed-readiness result follows from this audit. The crowbar/retrieval issue remains **UNRESOLVED / HIGH PRIORITY**.

## Validation boundary

Exporter/static integrity and reservation checks passed. Native P2 purchases/upgrades, payment/recovery, obstruction, movement and persistence require the central runtime test results. This document records no new native pass, owner gameplay acceptance, weapon verification or deployment.
