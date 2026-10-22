# INT-02: matching authored-world package assembler

`tools/package_authored_demo.py` extends the existing `tools/prepare_fresh_demo.py` component-staging workflow. It imports that tool's bounded regular-file, ZIP and JAR validators. It is not a new game launcher project or a live-world copying utility. It accepts only an explicitly transferred world ZIP and a separately pinned input manifest; it never reads a source world directory, installed mod folder, server settings or active save.

**Current deliverable: assembler and automated synthetic-fixture validation. A playable demo package remains blocked.** No approved clean authored-world archive, original working demo launcher payload, current reviewed bootstrap payload, or matching official Forge runtime checksum list was located in the transferred inputs and reviewable release folders. The historical `CatanCraft_Fresh_R7_Demo_Instructions.txt` describes a previous actual kit, including `START_DEMO.bat`, but the instructions are not that kit. P1 source/asset ZIPs and component-only staging folders do not supply a playable world. No fixture world is delivered as authored terrain.

## Inputs and invocation

Use a new input directory outside the source checkout and a new output directory outside those inputs. The manifest's expected SHA-256 must come from the matching transferred input evidence; computing a replacement digest after a mismatch is not verification.

```bash
python3 tools/package_authored_demo.py \
  --input-root /workspace/transferred-clean-demo \
  --manifest /workspace/transferred-clean-demo/input-manifest.json \
  --manifest-sha256 EXPECTED_TRANSFER_MANIFEST_SHA256 \
  --output /workspace/releases/CatanCraft-authored-demo-review
```

The assembler verifies everything before exclusively creating the output. It preserves any existing output and rejects source/destination symlinks, archive traversal, duplicate/case-colliding paths and unsafe Windows filenames. Its inherited bounded input/expanded ZIP limit is 256 MiB; this is not an invitation to use a played world. On an output-write failure it removes only the new directory it just created.

The manifest schema is deliberately explicit. These are field descriptions, **not approved values**:

| Field | Required content |
|---|---|
| `schemaVersion`, `packageKind` | `1`, `catancraft-authored-demo-input` |
| `approval` | Exactly `freshAuthoredWorld: true`, `bootstrapReviewed: true`, `sectorArtEnabled: false`; declarations must describe the actual reviewed transferred inputs |
| `sourceCommit` | Exact 40-character Git commit of the matching runtime |
| `runtime` | `javaMajor: 17`, `minecraft: "1.20.1"`, `forge: "47.4.10"` |
| `world` | `source`, archive `sha256`, safe single-directory `root`, exact NBT `levelName` and signed-long `seed`, `regionFiles` count, and exhaustive relative-member `files` SHA-256 map |
| `bootstrap` | `mode: "reviewed-transferred-bootstrap"`; `metadataFiles` listing transferred, hashed bootstrap files; `authorization` containing canonical `worldId` UUID, actual packaged `mapSha256`, and explicit `bootstrapRevision` |
| `jar` | `source`, `sha256`, exact expanded `version`; JAR metadata must identify CatanCraft, Minecraft 1.20.1 and Forge 47.4.10 |
| `sourceEvidence` | `source`, `sha256`; JSON must identify `kind: "matching-runtime-evidence"`, the same `sourceCommit` and `jarSHA256` |
| `config`, `assets` | Nonempty lists of `{source, target, sha256}`; targets must be unique under `config/`; `config/catancraft/map.json` is mandatory and its actual bytes must match `mapSha256` |
| `forgeRuntimeFiles` | Independently verified official runtime files and SHA-256 values under `libraries/`; include both exact platform argument files and the Forge runtime JAR for `1.20.1-47.4.10`; include the complete required dependency closure |
| `fixtureOnly` | Optional explicit synthetic-input label, propagated to package metadata; never substitute these fixtures for the missing authored world |

The world ZIP must contain one exact root with `level.dat` and authored `region/r.X.Z.mca` files. `poi` regions, explicitly reviewed metadata and pinned bootstrap datapacks are permitted. It rejects `data`, `playerdata`, `players`, `advancements`, `stats` and `entities` trees, inherited single-player `Player` NBT, old test authorization and copied bound authorization markers. This intentionally accepts a pristine pre-ledger input, not a conversion of a developed save. It decodes bounded standard NBT to check the source world name and seed; hashes and declared region count remain exact. Hash matching and a reviewed manifest do not independently certify gameplay or terrain geometry.

The deterministic current-art bootstrap must be supplied and reviewed, then tied to the runtime/source evidence. A nonempty revision identifier is a binding, not proof that an unknown bootstrap works. The tool does not fabricate a bootstrap, replay accepted repair operations, auto-purchase tiers or enable P1 walls.

Current source now routes normal producer adoption/upgrades/recovery, Town Hall prestige purchases and their pending-repair guard through `ConstructionWorldAuthorization`: a valid fresh marker uses the strict fresh identity; an existing malformed marker refuses instead of falling back to historical R7 authorization. Historical maintenance authorization remains separate. This source support is not a completed fresh-world walkthrough. Automatic Commercial/Logistics construction now accepts the same valid fresh identity before its unchanged legacy R7 authorization path. Native authorization fixtures check valid and malformed markers; actual bootstrap and complete normal civic progression still require the transferred authored world and acceptance. Do not copy old test markers as a workaround.

## Extracted package behavior

Both `START_DEMO.sh` and `START_DEMO.bat` are present immediately. They invoke the small package-local Python verifier; Python 3.11+ is an explicit prerequisite. Java/Forge are not downloaded, installed or executed during assembly.

Before launch, the package-local verifier requires:

1. Intact immutable JAR/config/assets/source evidence and package checksums.
2. Ordinary personal Minecraft EULA consent: read the linked EULA and change this package's `eula=false` to `eula=true` yourself.
3. A manually installed official Forge 1.20.1-47.4.10 runtime in this isolated folder, whose supplied platform argument files and complete library checksum list match.
4. Exactly 64-bit Java 17, checked through Java version properties.
5. On the first launch, the exact transferred pristine world files, with no added ledger or player data.

Only after those checks does first launch bind generated `catancraft-fresh-demo.json` to the canonical **world directory** using `installationBoundPath` and save a separate `.demo-launch-state.json` receipt beside the world. The generated marker uses the same strict fields as `FreshDemoAuthorization`: schema, purpose, canonical world UUID, level name, seed, dimension, actual map digest, transferred archive digest and bootstrap revision. It is not copied from the old R7 test-world marker.

Normal subsequent starts permit legitimate changed `level.dat`, regions, nation data, inventories and purchase journals. They still check the immutable runtime/config, exact authorization fields, bound path and independent launch receipt. A missing marker/receipt, changed world UUID or moved installation refuses; it never deletes journals, resets the world or re-copies pristine terrain. `--verify-only` checks prerequisites without starting Java's server entry point or binding a first-launch world. Java's version check still runs.

The default server properties bind localhost port 25566 with authenticated whitelist enabled and no RCON/query. This is isolation configuration, not a multiplayer test or public hosting setup. Close normally with `stop`. The matching client requires a separate Minecraft 1.20.1 / Forge 47.4.10 profile and the exact packaged CatanCraft JAR. TaCZ, Superb Warfare and their dependency/content packs are **not bundled or certified**; verified artifacts/model/security details remain separate missing inputs. This package must not be called the full modpack.

The README documents the existing operator funding shortcut after normal nation founding: `/catan debug money <amount>` and `/catan debug give <resource> <amount>`. It installs no auto-buy function and does not consume or replace normal progression. No live-world migration, reset, deployment or automatic purchases are included.

## Validation and remaining gates

Executed:

```bash
python3 -m unittest discover -s tests -p test_package_authored_demo.py -v
```

Result: **21 tests passed**, using tiny artificial ZIP/NBT inputs and mocked Java/server subprocesses. Coverage includes both scripts, provenance, exact first binding, normal changed-world restart, missing/foreign authorization or receipt, checksum/source/map mismatches, inherited ledgers/player data, unsafe archives/symlinks, EULA refusal and missing runtime prerequisites. This is not execution of a native Minecraft world, Windows batch launcher, Linux shell launcher, Forge installation or client connection.

Exact next step: transfer the original clean authored-world/demo-kit bytes with their original hashes and matching reviewed bootstrap/config metadata. Verify the complete current runtime evidence and official prerequisite library hashes; then run this assembler against those actual inputs. Keep final archives below 25 MiB where practical using separate ordinary ZIPs with documented extraction order. No actual demo archive has been created while these inputs are absent. Final owner acceptance and any enabled sector artwork/prices remain independent gates.
