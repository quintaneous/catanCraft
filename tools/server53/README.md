CatanCraft 0.1.53 fresh server kit

Assembly alone is not a gameplay pass. Read KIT.json: a kit without
readyForFocusedSoloTest=true is developer preparation, not a test handoff.
Owner acceptance remains PENDING, including SEC-01 crowbar (HIGH PRIORITY).

Extract into a NEW folder outside installed Minecraft profiles. Keep baseline/
unchanged: it is an immutable authored map with no inherited player, nation or
purchase history. It cannot be launched directly. No funding grants are included.

Windows: double-click NEW_TEST_RUN.bat once to create a new folder under runs/.
The tool prints the exact folder. Read Minecraft's EULA and personally change
only that run's eula.txt to eula=true if you agree. Open START_SERVER.bat inside
that run. Windows Java 17 is portable when KIT.json says javaBundled=true.
Do not install Python for the Windows entry points.

Linux: Python 3.11+ and 64-bit Java 17 are needed; use NEW_TEST_RUN.sh, then
START_SERVER.sh inside the printed run folder. No installer downloads occur.

NEW always creates a distinct run identity/directory from baseline. START always
resumes the same run. Stop normally with the server console command stop; restart
with START in that same folder. Never delete progress, reset, move a bound run,
reuse a played run as a baseline, or copy files into the live server.
Verify without creating a run: NEW_TEST_RUN.bat -VerifyOnly (Windows) or
NEW_TEST_RUN.sh --verify-only (Linux). Each verifier rejects changed/extra
baseline/runtime/tool files and redirects. Keep interrupted copies for inspection.

Use a separate Minecraft 1.20.1 / Forge 47.4.10 client with the supplied client/mods.
The complete mod set is CatanCraft, TaCZ 1.2.0, Superb Warfare 0.8.9.2 and
KotlinForForge 4.11.0. Nested dependencies stay in their original published JARs.
TaCZ supplies handheld guns; SBW supplies vehicles/mounted equipment.
Join 127.0.0.1:25566. In the server console, whitelist add YOUR_USERNAME.
Authentication, whitelist and one-player limits remain enabled; no multiplayer test.

Follow SOLO_CHECKLIST.md for a fresh solo run. Choose any available starting city
in /nation and spot-check whichever features you reach. The authored starter map
preserves all region bytes; only the new copy's level.dat derives Adventure,
Normal difficulty and randomTickSpeed=3. Other authored rules remain unchanged.
Packaging checks hashes and explicit evidence counts; it does not execute or
independently interpret native evidence. In-game acceptance is separate.
