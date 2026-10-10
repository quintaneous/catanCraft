# CatanCraft v0.1.5 — Blender R4-A live-world integration preflight

Forge 1.20.1 / Java 17. Base 0.1.4 code retained (including the **unverified** v0.1.3 Superb Warfare crowbar interception and v0.1.4 Logistics Center bonuses). No defense purchases or installation enabled.

## Source and validation

Reviewed Blender's `CatanCraft_R4A_Placement_Ready_Assets.zip` from the shared Minecraft Serrver library, archive SHA-256:

`19b7f649164bd9b256784394f0104fdea66a99d57b8329d7790e75d578c34032`

Re-executed bundled `python tools/validate.py`: **56 new module rotation tests**, **1,088 world-space socket transforms**, **704 authored-world PASS / 384 CONFLICT** across 16 cities. Original R4 prototypes unchanged. Blender provided 12 R4-A modular schematics and 2 R4-B gate source revisions with sparse voxel delta contracts, support/road/clearance checks, previews and candidate dependencies. Neither Blender's sources nor the server's active R3 bundle are modified. This offline validation is NOT a live-world installation approval.

**Both bunker sites are CONFLICT in all 16 cities.** They are refused by the preflight and must not be marketed as purchasable. R4-B gates remain future upgrades and are refused as well.

## In-game preparation — OP READ ONLY

Copy the unchanged **entire** `CatanCraft_R4A_Placement_Ready_Assets.zip` to:

`config/catancraft/CatanCraft_R4A_Placement_Ready_Assets.zip`

This is **separate from** `config/catancraft/schematics_bundle.zip` (which remains your verified R3 production assets). NEVER rename the R4-A ZIP to `schematics_bundle.zip`, manually paste its source schematics, or replace `world` or `map.json`.

Use:

- `/catan defense status` — no defense installation is enabled.
- `/catan defense list a` — shows example socket IDs, 43 supported R4-A candidates + 25 blocked/future gate items (the Blender survey's 44 PASS counts include one R4-B gate candidate per city).
- `/catan defense preflight a access_checkpoint` — uses Blender per-city **already-rotated sparse delta** to compare the currently loaded server's exact block properties, support profiles, road surface, road clearance and a conservative above-grade empty envelope, plus entity intersection with intended solids. Does NOT rotate that delta a second time.
- `/catan defense preflight a tower_nw` — checks a candidate tower.
- `/catan defense preflight i access_checkpoint` — validates 180° south-facing city I.
- `/catan defense preflight a bunker_w` — must refuse, as Blender rejected that site.
- `/catan defense survey a` — retains the earlier rough 15-band diagnostic.

**Important:** Output `checks matched` means this particular read-only comparison passed now, but **installation remains disabled**. No purchases, ownership assignment, gate controller, siege damage/restoration, or collision/rollback transactions have been implemented. The source Blender `live_install_authorized=false` is preserved as a hard refusal. The command does not charge currency, modify blocks, migrate world data, load missing chunks or place schematics.

**Conservative limitations:** The preflight uses Blender's exact sparse expected-before states and supplied support/road masks; because its above-grade clearance reconstruction scans the bounding volume (not the schematic's precise air subset), it may report additional obstructions. This is deliberate fail-closed behavior and not a claim of false free space. Entity overlap checks are instant snapshots only. OP must still visually inspect the live world and test actual vehicle approach/turning; no mechanic here reserves cells against simultaneous changes.

When chunks are unloaded, report mismatches instead of force-loading them. Never flatten terrain or fill missing foundation columns automatically.

## Install and rollback

1. Back up `world`, all nation data, `config/catancraft`, client/server `mods`.
2. Replace old CatanCraft JAR with **only** `catancraft-0.1.5.jar` on BOTH client and server; keep Forge 47.4.10, Minecraft 1.20.1 and Java 17.
3. Put the R4-A archive at the exact config path above. Keep the existing R3 schematic bundle and world intact.
4. Start and confirm `/catan map assetstatus` still says **38 ready / 0 invalid**.
5. Run the read-only commands. If 0.1.5 startup fails, stop server and replace only JAR with your last working release; the R4-A sidecar archive can remain unused without modifying the world.

## Acceptance checks

- [ ] 0.1.5 on client and server, without mixin errors or mismatch.
- [ ] 38 ready / 0 invalid, existing R3 buildings and nation resources unchanged.
- [ ] Missing/altered R4-A archive refused gracefully; no blocks touched.
- [ ] `/catan defense list a` returns 68 total surveyed sockets, 43 R4-A candidates, 25 conflicts/unsupported future gate slots.
- [ ] `/catan defense preflight a access_checkpoint` checks current voxels and prints pass/fail details; no site placement.
- [ ] `/catan defense preflight i tower_nw` loads south city I and uses its own recorded world coordinates.
- [ ] `/catan defense preflight a bunker_w` refuses. `/catan defense preflight a access_gate` refuses (R4-B).
- [ ] Modify **one block in a disposable backed-up test world** at a prospective socket: preflight detects mismatch, does not clear it. Restore backup/block after.
- [ ] No chunk loading, destructive operations, costs, nation ownership changes or forced world migration caused by preflight.
- [ ] Test earlier **v0.1.3 crowbar fix**, which has NOT been user-verified: bought pickup/LAV cannot become a portable item, other vehicles remain normal.
- [ ] Logistics Center purchase/production increase and no duplicate purchases from 0.1.4 (also not yet user-verified).
- [ ] TaCZ/Superb Warfare terrain protection preserved from user-tested v0.1.2.
- [ ] Standard /nation accordion GUI, /speed, capture rewards, R3 factory production, persistence and server restart.

## Next phase (requires additional integration)

After the user sends live preflight results, implement narrowly selected **purchasable R4-A checkpoint, wall, tower and end-cap sockets** using the exact masked transactions, server-side permission/ownership, dependencies/exclusions, actual-before snapshots (including block entity data), durable pending state/rollback and collision locks. Start with disposable worlds A and I; run complete 16-city regression before public launch. Gate controller is R4-B only; bunker and trenches remain blocked. Await separate Blender R5 city rework and civic assets without overwriting active R4-A sources.
