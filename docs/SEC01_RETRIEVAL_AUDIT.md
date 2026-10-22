# SEC-01 — published-runtime retrieval audit

2026-10-18. **OPEN / HIGH PRIORITY.** Candidate protection now passes seven isolated native tests against the official published Superb Warfare 0.8.9.2 Forge artifact. This is narrower than owner-modpack acceptance or a verified process-restart/procurement test. No installed mods, active world, live server or multiplayer were accessed.

## Runtime provenance

The supplied October16 log identifies SBW0.8.9.2, but does not identify the installed artifact hash. The official Modrinth project `Cd3DYqzn` (`superb-warfare`) links the upstream source repository `https://github.com/Mercurows/SuperbWarfare`. Its Forge1.20.1 release is `81Iyv9ao`, published 2026-09-30. Binary and matching sources were downloaded through ordinary HTTPS from its declared CDN URLs; both published SHA1 and SHA512 checks matched.

| Input | Bytes | SHA256 |
|---|---:|---|
| superbwarfare-0.8.9.2-mc1.20.1-65d985a64-all.jar | 42118965 | `559930003f638080de648a8621821d785158768c3f69a762094ef4c8d3c4cc12` |
| superbwarfare-0.8.9.2-mc1.20.1-65d985a64-sources.jar | 31115305 | `06b4bc749e540879265e3f3829c6c242da81512b19a80de0fbd9b089847893d5` |
| kotlinforforge-4.11.0-all.jar, release `hmeyC41q` | — | `ef988f86d170af499d147ea8a5c34c99dbd6d8f4a5ad236a4b43818dc4470a10` |

Official metadata: `https://api.modrinth.com/v2/version/81Iyv9ao` and `https://api.modrinth.com/v2/version/hmeyC41q`. Sources, binary metadata, hash inventory, javap output and selected source excerpts are retained outside the checkout in `/workspace/sec01-audit`. Original archives remain unchanged and are not bundled in the CatanCraft release.

SBW binary metadata requires Minecraft[1.20.1,1.21), Forge[47,), KotlinForForge loader[4.11.0,) and SimpleBedrockModel[2.5.1,). Its all-JAR embeds SimpleBedrockModel2.5.1, Curios5.14.1, GeckoLib4.4.6, Rhino1.8.1-SNAPSHOT, Ponder1.0.91, Flywheel1.0.5 and ClothConfig11.1.106. The optional test setup stages these unchanged embedded artifacts as local Maven peers, remapping those marked obfuscated for the named development runtime. SimpleBedrockModel's extracted SHA256 is `323612d16de1d68a1dadc02b3329bfc0614d015ef70995bce7098adf38a437dc`. KotlinForForge is an FML library bundle, with its language provider nested inside; it does not have an outer mods.toml.

## Identified defect and candidate changes

SBW `ArtilleryEntity.interact` processes offhand `IVehicleInteract` before base interaction. `CrowbarItem.onInteractVehicle` calls Kotlin `CrowbarItem$Companion.crowbarInteract`, which grants retrieved items and removes the entity directly. The former base-method mixin cannot intercept that branch; the former Forge fallback inspected only the main hand. Mortar, Annihilator and AutoAimable override `onCrowbarInteract` with retrieval paths that do not call the base method.

The published binary's base descriptor matches the existing mixin: `(ItemStack, Player, InteractionHand) -> InteractionResult`. This was not a demonstrated method-renaming failure.

Candidate safeguards intercept the base method and three overrides plus the companion helper before item grant/removal. The helper descriptor is `(VehicleEntity, ItemStack, Player, InteractionHand) -> InteractionResult`; its Kotlin companion instance implementation must be guarded, not merely the static Java bridge. Forge fallback checks both hands and resolves a Forge `PartEntity` to its purchased parent. No generic removal, damage or discard interception is added; ordinary unpurchased retrieval remains enabled.

## Actual validation

Root ran `catancraft_sbw` sequentially in its disposable Forge GameTestServer after the other writable native suites finished. Result: **7 executed / 7 passed / 0 failed**, Gradle exit0. Tests use actual SBW registered entities, methods and crowbar, not SBW mocks:

1. Purchased pickup and LAV direct base retrieval refuse, retain UUID/entity and grant no item.
2. Purchased helper retrieval refuses both hand arguments.
3. Actual Mortar, Annihilator and AutoAimable override paths refuse.
4. Actual artillery offhand interaction refuses.
5. Real Forge entity-interact fallback checks main and offhand.
6. Ordinary unpurchased pickup retrieval succeeds, removes the entity and awards its packed item.
7. Actual SBW entity serialization/load preserves its UUID and purchased tag; subsequent helper retrieval refuses.

These fixtures set the purchased tag explicitly. Item-count assertions include player inventory/offhand and nearby dropped items. They are immediate isolated interactions, not connected-player or multiplayer acceptance.

Executed command, with the prepared Java17/Gradle8.8 environment:

```bash
gradle runGameTestServer -PgameTestNamespace=catancraft_sbw \
  --init-script /workspace/sec01-audit/sbw-test.init.gradle \
  --offline --no-daemon --max-workers=4
```

Evidence: `/workspace/integration-evidence/roadmap-focused/sbw-mapped.log`, corresponding `.exit`0. Log SHA256: `c48ddc4e65b837dec7c2361da1b2c26ae5facde29baec93f9109d31cc6bee94f`.

Preserved earlier attempts executed zero tests: `sbw.log` failed on SBW's SRG refmap in the named runtime; `sbw-retry.log` failed because the generated run task was referenced before registration. The successful init enables `mixin.env.remapRefMap=true`, points `mixin.env.refMapRemappingFile` at `build/createSrgToMcp/output.srg`, adds that task dependency through `configureEach`, and explicitly loads `catancraft.mixins.json`. The successful log still reports optional Patchouli manual loot parsing, public-key retrieval and initial absent-server.properties messages; these are not suppressed or represented as a clean full-modpack acceptance.

`tools/validate_sbw.gradle` is the reviewable equivalent optional init, with `-PsbwAuditRoot=/path/to/audit` (default `/workspace/sec01-audit`). It restricts execution to the SBW namespace, verifies the pinned `runtime-artifacts.json` SHA256 (`c468a1e6bd77be2b15fe486085b2ea267410b04e201b8b975191f1b3f255c55c`) and each original/staged JAR hash before adding optional dependencies. This hash-checking wrapper was added after the successful run; it has not itself been rerun as native evidence. It does not alter ordinary build dependencies or add SBW to released CatanCraft jars.

## Remaining closure requirements

- Compare owner-approved installed artifact hashes/configuration without accessing the active installation implicitly; published version identity alone is insufficient.
- Exercise real procurement-produced entities, including resources, ownership and persistence through a full process restart. Entity NBT round-trip is not a process restart.
- Validate applicable actual part/custom hit interactions; no SBW VehiclePart class was found in this pinned source audit. Forge parent resolution is implemented but lacks an actual SBW multipart runtime acceptance case.
- Validate fixed purchased weapons when their actual approved entities and procurement contracts exist. VehicleAssemblingTable's non-sneak block conversion is not a currently purchasable module and is not covered by this bounded crowbar policy.
- Confirm owner-facing single-player interactions and ordinary unpurchased behavior in the matching approved modpack. No multiplayer test is authorized.

Until these close, SEC-01 remains open/high priority and advanced defenses must not be advertised as verified safe armed emplacements.
