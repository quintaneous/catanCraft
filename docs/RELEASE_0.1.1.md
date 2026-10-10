# CatanCraft 0.1.1 — Territory HUD and versioned releases

**Minecraft:** Forge 1.20.1 (47.4.10), Java 17. This is a code-only update on top of R3. Nation economy, six R3 industry schematics, three flexible industry plots, procurement, and admin `/speed` remain in this release.

## What's new
- Compact top-center territory indicator while the in-world HUD is active: `Territory: Northwest Hearth [A]`. Outside authored territories: `Territory: Wilderness`.
- The authoritative server sends territory names to the client on login and border crossings (sampled every 10 server ticks and transmitted only when the label changes).
- No requirement to copy or synchronize the `map.json` file to the client.
- HUD is hidden while menus are open or the player has enabled Hide HUD (F1).
- The built JAR filename and internal mod metadata now both identify version **0.1.1**: `catancraft-0.1.1.jar`. Future builds should always increment `build.gradle` version, preserving a unique JAR filename. Do not keep old CatanCraft JARs in mods directories.
- CatanCraft networking protocol incremented to 3 to require matching versions of this client/server code.

## Installation
1. Stop the server, exit Minecraft, and back up the existing world, nation data, and CatanCraft config folder.
2. Delete/relocate old `mods/catancraft-0.1.0-alpha.jar` or any previous `catancraft-*.jar`. Install **only** `catancraft-0.1.1.jar` in **both server and client** mods folders.
3. Install/reuse the included `schematics_bundle.zip` at **server** `config/catancraft/schematics_bundle.zip`. This bundle has 34 unchanged R2 baseline/reference schematics + 6 R3 factories (40 total, 38 required).
4. Do **not** replace `world`, `data`, or `config/catancraft/map.json` with Blender fresh-world assets. Keep TaCZ, Superb Warfare, and their dependencies; run Java 17.
5. Start server and client; check `/catan map reloadassets` and `/catan map assetstatus` for **38 ready, 0 missing/invalid**.

## Solo test checklist
- [ ] Launcher mod list shows **CatanCraft 0.1.1**; server runs without a mod/network mismatch.
- [ ] Walking inside territory A shows `Territory: Northwest Hearth [A]` (or its map.json name).
- [ ] Crossing A/B border changes territory name within ~0.5 seconds.
- [ ] Leaving map-defined territories displays `Territory: Wilderness`.
- [ ] F1 hides the HUD; opening inventory, chat or the `/nation` dashboard does not obstruct controls.
- [ ] Label is centered and readable at common GUI scales/resolutions; F3/minimap UI remain usable.
- [ ] Multiplayer join and server restart do not leave a stale territory label.
- [ ] `/catan map assetstatus` = 38 ready, 0 missing/invalid.
- [ ] `/nation` collapsible menus work; `/nation build a textile_mill plot_1` works if city and plot are eligible.
- [ ] Textile Mill (L2), Chemical Plant / Machine Shop / Electronics Factory (L3) recipe+visuals work in disposable owned territory.
- [ ] TaCZ pistol / ammo, Superb Warfare purchased vehicle spawn/crowbar safety, `/speed` and capture payout chat remain intact.
- [ ] Restart preserves purchased plots, treasury, stockpile and vehicle entity tags.

## Deferred
- R2 guarded village-house visual migration is NOT included. No forced settlement paste onto developed worlds.
- Existing no-plot (legacy) data-only industries require manual migration.
- In-game client HUD, R3 industry, combat, and vehicle tests are still pending. CI compilation does not prove runtime correctness.
