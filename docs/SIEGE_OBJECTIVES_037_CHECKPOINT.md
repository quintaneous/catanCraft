# 0.1.37 objective registration / presence checkpoint

2026-11-06. Branch: `chatgpt/siege-objectives-0.1.37`. Base:
`a51b1f8e8a6b41e878731130464a724b8c969984` (validated 0.1.36).

## Current implementation

Explicit immutable objective profiles and read-only server-authoritative presence
adapter. Unknown/changed world, city, boundary, owner or membership bindings
refuse. Outer and Town Hall sample separately; defender contest feeds the existing
one-clock rules. Production uses PlayerList identity and an accepting connection.
No deployed profiles, commands, tick subscription, damage or ownership writes.
[Full contract and limits](SIEGE_OBJECTIVES_037_CONTRACT.md).

## Exact-source validation

Source commit: `1065450c3f8a482eb51fe2ca45481c15ea97108f`.
Source tree: `75c2c67b78822b752a770e514741bf445b0da29a`.
[Workflow 42134586506](https://github.com/quintaneous/catanCraft/actions/runs/42134586506)
completed successfully on its first run. Java 17 / Gradle 8.8 / Minecraft 1.20.1 /
Forge 47.4.10. Actual native results: **88 executed / 88 passed / 0 failed**.

| Namespace | Executed | Passed | Failed |
| --- | ---: | ---: | ---: |
| `catancraft_objectives37` | 22 | 22 | 0 |
| `catancraft_siege36` | 16 | 16 | 0 |
| `catancraft` | 50 | 50 | 0 |

The new cases cover objective bounds/registration, native player feet and modes,
active-stage switching, attacker capture, contested Town Hall defender timeout,
roster/membership changes, duplicate/ambiguous identities, owner/nation mismatch,
foreign/terminal snapshots and read-only state. Independent review completed;
connection acceptance, null-stage refusal and early fixture thread checks were
included before the first build. No failure retry or production guard relaxation.

JAR: `catancraft-0.1.37-siege-objectives.jar`, **2,941,681 bytes**.
SHA-256: `a5d58edc458ed967caa1036d9c788c498de134ef12466ce5c3348d290294a13b`.
The manifest stamps the exact source commit; mod metadata reports 0.1.37.

Combined artifact: `objectives37-combined-source-and-evidence`, ID
`13465405797`, **5,635,319 bytes**. Download from the workflow artifacts.
Archive SHA-256: `599374c22dbc134575111eb7a8b0845347305ad012bdf5d190068073d58036ae`.
Contains the source archive, JAR, build/assets/native logs, exit/count/source
records and manifest from all three jobs. The downloaded ZIP checksum/CRC and all
27 manifest file entries were verified. All 370 archived source files matched
Git at the exact source commit. All three jobs produced the same source/JAR.

Compared with the validated 0.1.36 JAR, **all 442 preexisting class files are
byte-identical**. Every preexisting archive entry is unchanged except the source
stamp and version metadata (`META-INF/MANIFEST.MF`, `META-INF/mods.toml`). Existing
assets, purchase/recovery logic, wall/gate behavior and siege rules are preserved.
[Machine-readable verification](evidence/objectives37_validation.json).
Later documentation-only commits do not change this tested JAR source.

## Validation limits and owner action

Fixtures use detached SavedData and non-networked FakePlayers in a controlled
GameTest world. They prove the shared collector against real native player
fields and negative connection exclusion, not authenticated client connectivity,
live siege play, process-restart durability or restoration. Numeric settings and
box locations are synthetic, not approved live City A configuration. No claim
that other namespaces were rerun; their existing exact-source evidence remains.

No installation or new in-game checklist for this internal checkpoint. Keep the
accepted city and installed mods as they are. Next integration work needs durable
session/profile ownership and recovery, plus reviewed real objective geometry
and safe damage/restoration before deployment. SEC-01 crowbar remains unresolved /
high priority pending its separate installed-runtime acceptance. No repeated
accepted purchases, live-world changes, reset, imports, multiplayer or Blender.
