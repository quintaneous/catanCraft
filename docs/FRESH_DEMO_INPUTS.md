# Fresh demonstration inputs and component staging

**Current update:** P1 has since transferred and passed prototype tests; it remains unapproved. Official SBW artifacts were audited and seven native candidate-protection tests passed. Strict fresh-demo producer/prestige/civic authorization and the authored-input assembler now exist. See [ROADMAP_VALIDATION_20261018.md](ROADMAP_VALIDATION_20261018.md) and [INT02_DEMO_PACKAGE.md](INT02_DEMO_PACKAGE.md) for current evidence and remaining inputs. The older audit below is historical; no actual clean authored-world payload has since arrived.

The current workspace can prepare verified components. It cannot yet deliver the requested runnable fresh authored CatanCraft demonstration. Preserve developed City A and all historical release evidence.

The input audit found the original two handoff archives and the uploaded `latest.log`. The shared downloads and library-files directories contain no additional inputs. No approved new representative basic/advanced compass-sector Blender brief/art, clean authored world, or matching launcher/modpack profile has been located. Do not assume files from another conversation transferred.

## Available immutable assets

| Original handoff file | SHA-256 |
| --- | --- |
| `handoff/assets/schematics_bundle.zip` | `2a7a362047a6672b802c0077cd58db236183c10a69cc69dc928c64859d985edc` |
| `handoff/assets/R5_READ_ONLY_PROBE_INDEX.zip` | `4f5a445347adbb62e73d1c38bfab5ff850c7307733a619847d9eeac9245834ba` |

The schematic bundle has 40 schematic entries, including sixteen city environment layers, the starter settlement, Town Halls, resource/factory assets and central district/monuments. These bounded assets and the handoff's NPZ snapshots are not a complete approved River & Bridges world. The original starter is historical artwork, not evidence that every fresh city already receives all current house/public visuals. Existing R4-A/R6 artwork remains history/test input, not approved final compass-sector art.

The uploaded log identifies Minecraft 1.20.1, Forge 47.4.10 and Superb Warfare **0.8.9.2**. Its SHA-256 is `2e4b13b7d0456025a7e2afb022d4e0b4980180ca6f8027f0a091dbbe9c434514`. This establishes a reported version only. The intended SBW JAR/configuration, weapon IDs, dimensions, ammunition, damage, mod compatibility and anti-packing behavior require separate verification. Development remains Java 17 / Gradle 8.8; the log's different host Java is not a toolchain instruction.

## Fail-closed component staging

`tools/prepare_fresh_demo.py` uses Python 3.11+ and the standard library. Supply an explicit current build/release JAR and its expected checksum from matching build evidence. Do not point it at installed mods or a world. It verifies CatanCraft metadata and Minecraft/Forge compatibility, then verifies only the two named original handoff assets against the pins above. It does not search or copy arbitrary files from the input root, unpack a world, download mods, start Minecraft, create authorization markers or alter server settings.

Example, after replacing the JAR path and digest with the reviewed artifact evidence:

```sh
python3 tools/prepare_fresh_demo.py \
  --handoff-root /workspace/catancraft-inputs/CatanCraft-Dev \
  --jar /workspace/releases/reviewed-current/catancraft.jar \
  --jar-sha256 EXPECTED_DIGEST_FROM_MATCHING_BUILD_EVIDENCE \
  --output /workspace/fresh-demo-components-review
```

The output must not exist. Symlinks in source/output paths, mismatched checksums, unsafe ZIP entries, incompatible/missing JAR metadata and installed `mods`/`saves` paths refuse before staging. Inputs remain untouched.

Output consists only of `artifacts/catancraft.jar`, the two original ZIPs under `config/catancraft/`, deterministic `MANIFEST.json`, its `MANIFEST.sha256`, and `NOT_READY.txt`. The manifest records component hashes and actual mod version. No current JAR/source match is inferred merely from a filename or the tool succeeding; provide the digest of the artifact built from the intended checkpoint.

Exit 0 means verified **component staging succeeded**; the manifest still explicitly says `NOT_READY`, `runnable=false`, and `worldIncluded=false`. Exit 2 means input refusal. Neither result certifies gameplay or a runnable release.

## Remaining prerequisites before a runnable fresh demo

- Obtain and hash the clean approved authored world through an explicit separate transfer. A disposable GameTest world, blank generated map, per-city snapshot or renamed world is not its replacement.
- Obtain the current representative basic/advanced compass-wall brief and approved assets; keep placeholders clearly labeled.
- Obtain a matching isolated launcher/modpack profile, configuration and artifact hashes, including intended SBW and dependencies.
- Verify actual SBW behavior in a separately authorized isolated setup. The crowbar exploit remains **UNRESOLVED / HIGH PRIORITY**.
- Implement/review a deliberate fresh-initialization path with current visuals and appropriate new disposable-world authorization. Existing R7 maintenance/civic paths require a particular directory, LevelName and authorization marker. This staging tool creates none of them and must not disguise a blank save as R7.
- Validate normal progression, purchases/upgrades, persistence and the agreed compass-sector system before calling the package a runnable demo. Reuse existing producer/house/entity work and preserve legacy journals.

After a separate new world is supplied and verified within this task's authorization, existing operator commands can fund the demo nation's normal progression: `/nation create <name>`, `/nation start <slot>`, `/catan debug money <amount>`, `/catan debug give <resource> <amount>` or `/catan debug set <resource> <amount>`. Confirm syntax/resource names against the current build. Do not apply these to an active or developed world, use funding to bypass purchase logic, or change global economic prices.

Existing asset diagnostics include `/catan map assetstatus`, `reloadassets`, `environmentstatus`, and `verify`. Historical `installseason1`/refresh/whole-environment operations require context review before use; never blanket-paste over developed cities. Do not default the fresh demonstration to a sequence of legacy visual repair commands.

## Focused verification

```sh
python3 -m unittest discover -s tests -p test_prepare_fresh_demo.py -v
```

The focused tests use synthetic ZIP/JAR fixtures, temporary directories and substituted test pins. They check deterministic manifests, exact copies, input mismatches, missing files, existing-work preservation, source/output symlinks, unsafe archives, incompatible metadata, installed-mod path refusal and exclusion of unrelated world/mod/art files. They do not run Java, native GameTests, a client, a live server or network requests.
