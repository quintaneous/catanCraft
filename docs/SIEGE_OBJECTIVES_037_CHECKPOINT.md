# 0.1.37 objective registration / presence checkpoint

Branch: `chatgpt/siege-objectives-0.1.37`. Base:
`a51b1f8e8a6b41e878731130464a724b8c969984` (validated 0.1.36).

## Current implementation

Explicit immutable objective profiles and read-only server-authoritative presence
adapter. Unknown/changed world, city, boundary, owner or membership bindings
refuse. Outer and Town Hall sample separately; defender contest feeds the existing
one-clock rules. Production uses PlayerList identity and an accepting connection.
No deployed profiles, commands, tick subscription, damage or ownership writes.
[Full contract and limits](SIEGE_OBJECTIVES_037_CONTRACT.md).

## Validation pending

Selected native matrix: 22 new objective/presence cases, 16 retained siege-rule
cases and 50 retained baseline cases (88 planned executions). Independent static
review completed; connection acceptance, null-stage refusal and early fixture
thread checks are included. Source has not yet built or passed the native matrix.
After execution this record will contain exact commit, workflow, artifact and
JAR hashes with actual passed/failed counts. Prior failure evidence remains intact.

Fixtures use detached SavedData and non-networked FakePlayers in a controlled
GameTest world. They do not prove authenticated client connectivity, live siege
play, process-restart durability or restoration. Numeric settings and box
locations are synthetic, not approved live City A configuration.

## Owner action

No installation or new in-game checklist for this internal checkpoint. Keep the
accepted city and installed mods as they are. SEC-01 crowbar remains unresolved /
high priority pending its separate installed-runtime acceptance. No repeated
accepted purchases, live-world changes, reset, imports, multiplayer or Blender.
