# CatanCraft 0.1.8 — R5 Complete Conflict Logging (read-only)

Forge 1.20.1 / 47.4.10, Java 17 target. This is a strict compatibility update over accepted v0.1.7. **No R5 or R6 construction or world migration is enabled.**

## New behavior

Previously `/catan r5 civic a commercial`, `/catan r5 civic a logistics` and `/catan r5 group a front_public_park` showed only the first nine mismatch reasons in game chat, concealing important residual problems. Version 0.1.8 writes every mismatch and a categorized summary to the **server's** `logs/latest.log` with searchable markers:

```
R5 DIAGNOSTIC BEGIN civic/a/commercial (read-only)
R5 DIAGNOSTIC civic/a/commercial | #1 [source-air] ...
R5 DIAGNOSTIC SUMMARY civic/a/commercial checked=... mismatches=... reasons=...
R5 DIAGNOSTIC END civic/a/commercial
```

Full expected and actual block identities/positions are captured by existing `check()`. The original same-state, support, save/map, bounds, unloaded-chunk and prerequisite checks are otherwise unchanged. This is explicitly **not** a dependency-approved design or auto-cleanup operation.

Use `/catan r5 civic a commercial` to reveal the two unresolved discrepancies beyond the **92 authored oak-leaf/log removal blocks** (84 leaves, 8 logs) in Blender's `cleanup_tree_16_10` group. The first nine are still displayed in chat; upload `latest.log` to see all of them. The 92-tree relation is inferred from R5 source delta and prior game screenshots: the full user-world actual state still requires confirmation.

Additional useful read-only checks:

```
/catan r5 civic a logistics
/catan r5 group a logistics_service_connection
/catan r5 group a house_1_coherent_facade
/catan r5 civic i commercial
```

For City I, **travel to/load the target chunks first**, otherwise every unloaded location will be listed as a refusal, creating many log lines; do not treat them as geometry mismatches.

## Install

- Stop both server and client and back up `world/`, `mods/` and `config/catancraft/`.
- Replace `catancraft-0.1.7.jar` with **only** `catancraft-0.1.8.jar` on **both** server and client.
- Preserve `schematics_bundle.zip` (R3), `CatanCraft_R4A_Placement_Ready_Assets.zip`, `CatanCraft_R5_City_Rework_And_Civic_Assets.zip` and `R5_READ_ONLY_PROBE_INDEX.zip` exactly as currently installed. No new art is needed.
- Keep R6 candidate files outside the server config for now; R6 has not been registered. Do not rename or overwrite the R4 or R5 ZIPs.

## Solo checklist

- [ ] Client/server launch with v0.1.8 and `/catan map assetstatus` is 38 ready / 0 invalid.
- [ ] `/catan r5 status` still says R5 is read-only; `/catan defense status` still disables R4 installation.
- [ ] `/catan r5 civic a commercial` emits the complete `R5 DIAGNOSTIC BEGIN` ... `END` range in server `logs/latest.log`. Categorized total agrees with in-game mismatch count.
- [ ] Send log to main development. Don't clear tree or overwrite any mismatch in-game.
- [ ] `/catan r5 group a logistics_service_connection` still gives 158 checks / 0 mismatches (previously accepted).
- [ ] `/catan debug production a` complete economic ledger remains functional and logs `ECONOMY AUDIT` as v0.1.7 did.
- [ ] No blocks, treasury or saved-building data are changed by any R5 preflight.
- [ ] v0.1.3–0.1.8 Superb Warfare bought-vehicle crowbar exploit remains **known unresolved**; do not distribute crowbars, see issue #6.

## Known limitations

The R5 source has separate R1 versus R2 house baselines; do not paste updates from a mismatched house revision. R5's Commercial District still depends on approved city landscaping/sidewalk prerequisites and actual rollback, which are not implemented. R6 has 272 offline-valid recoveries but **zero live-world checks** and is not part of this release.

This diagnostic step is a prerequisite for implementing a safe, journaled, opt-in R5 incremental migration on the user's developed save, rather than inferring missing blocks or automatically forcing structures.
