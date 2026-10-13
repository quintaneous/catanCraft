# CatanCraft v0.1.20 — TH4/TH5 schema staging (NO world placement)

### Why work can continue before Blender R7 world is ready
The two separately authored TH4 and TH5 schematics from Blender have passed independent downloaded-archive SHA256 and CRC validation. This release adds the two **read-only asset/placement registry contracts**. It deliberately does not enable physical Town Hall L3→L4→L5 upgrades; a raw full-envelope paste would erase existing management lectern data/books and could trample foreign edits. Preserve the existing working 0.1.19 environment/world and test journals. No new-world installation is authorized by this release.

### Package verification
- Blender ZIP `CatanCraft_TH4_TH5_Prestige_Town_Hall_Assets.zip`: downloaded file **12,640,000 bytes**, SHA-256 `75478483b9bce024cc436f5e493c3478bed1d86f88c356c8321f69bfd4040ef8`; 124 archive entries, all CRC PASS.
- `schematics/thall4.schem` compressed SHA256 `39f2fda6a74d0d891d45b37d111079905a6f67e4261b591c13902b4215342c4b` (3,478 bytes)
- `schematics/thall5.schem` compressed SHA256 `1ea2fd544c32ed5506a553bf6c56628ecd4b7ac8d29257009e7bd84a427bbc06` (5,291 bytes)
- Both 75x47x65 Sponge v2, DataVersion3465, local anchor [37,2,1] / offset [-37,-2,-1], city north/south rotations 0/180, Y81, plot town_hall. TH4 contains 37,210 non-air cells; TH5 contains 39,879. Validation summary claims 16/16 candidate placements avoid all protected R5/R4/R6 areas and match lectern coordinate.
- SHA, CRC, metadata contract and placement parameters verified offline against source; Minecraft import/player visuals/UI/neighbor dynamics and world backups remain UNTESTED.

### Exact implementation
- Adds immutable SchematicAssetRegistry entries th4 -> thall4 and th5 -> thall5, revision 1, 75x47x65, local anchor 37,2,1, supported rotations 0/180, north offsets (0,-39).
- Does not modify MapAssetService.hallAsset, CatanCommands.upgradeCity, purchase/economy, or existing Town Hall schematics. Economic L4/L5 still intentionally show TH3.
- Zip includes new Blender thall4.schem and thall5.schem as optional individual overrides under `config/catancraft/schematics/`. These are **validation-only** at this stage. SchematicAssetRegistry.assets() includes two additional entries; with overrides correctly installed, `/catan map assetstatus` expected **40 ready 0 invalid** versus previously 38. Existing worlds running v0.1.19 remain at 38; do not upgrade or run asset status with 0.1.20 unless the two overrides are installed.
- No R7 material or new world generation occurs; terrain needs a complete Blender R7 enhanced world ZIP with independent verified SHA/CRC before any permanent buildings are placed.

### Upcoming safe TH4/5 work
Build an independent journaling transaction with a complete TH envelope read mask (229,125 cells), protected outside mask and occupied-entity scan. Keep the real management lectern at world [C.x,84,C.z-23] (for rotation 0; see Blender placement contracts), preserve and restore its block entity/book contents and city UI binding, and audit decorative lecterns in old TH3. Refuse unknown NBT rather than quietly discarding contents. Require exactly one economy charge when upgrading to L4/L5 and zero for already-paid retrofits, write durable before-state journal first, use drop-safe physics, make rollback safe, and test restarts in a NEW disposable R7 world only. Never paste these schematics onto old live/developed world as unguarded WorldEdit.
