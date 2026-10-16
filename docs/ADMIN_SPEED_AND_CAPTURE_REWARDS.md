# CatanCraft: admin fast travel and monument reward receipts

Forge 1.20.1 / Java 17. Code-only patch on `v0.1-foundation`. Keep Environment V4 + Monument Gameplay R1 and its existing schematic bundle unchanged.

## Admin `/speed`
- Permission requirement: server operator level 2 or higher. Player-only.
- `/speed` toggles x6 boosted walking and flight.
- `/speed 2` ... `/speed 10` enables flight and sets the corresponding multiplier.
- `/speed 1` restores prior flight permissions and movement speeds.
- Logging out while boosted restores preexisting abilities rather than granting permanent flight.

## Monument capture
- The server-wide announcement of a secured monument remains.
- Online members of the winning nation also receive an exact payout in normal chat.
- Example: `[CatanCraft] Military Depot capture rewards: +$1,500 treasury, +25 Steel, +30 Fabric, +20 Explosives`.
- Other nations do not see exact awarded resources, only the public announcement.

## Installation
Back up the existing JAR. Replace CatanCraft JAR in the server `mods/` and every modded client `mods/`, restart both sides. Keep `config/catancraft/schematics_bundle.zip` and world as-is.

## Single-player smoke tests
1. OP `/speed` enables fast fly, `/speed 10` increases it, and `/speed 1` restores the original Creative/Survival behavior.
2. `/speed` toggle twice restores normal speed. Logout/rejoin should not retain boosted flight.
3. Capture Military Depot and confirm one payout receipt and the correct stockpile/treasury changes.
4. Capture a different monument and compare its distinct payout against `MonumentType.java`.
5. Check `/nation`, monument layouts, schematic status, and world verification still function.

## Deferred until a second player is available
- Non-OP command rejection; two-nation contested capture; team-specific chat visibility; other multiplayer economic/warfare tests.