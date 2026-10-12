# CatanCraft v0.1.12 — read-only civic staged preview checklist (solo)

No multiplayer testing. Keep the **main developed world/JAR unchanged**. Use the existing separate test instance with its backups.

1. Replace only the CatanCraft JAR on matching test server/client with \`catancraft-0.1.12.jar\`. Keep all existing config/catancraft supporting R3/R4-A/R5 files and historical R5 park journal.
2. Confirm startup and \`/catan map assetstatus\` (expected 38 ready / 0 invalid). No need to repeat previously accepted economy, park apply/rollback or restart tests.
3. In City A, with the civic area chunks loaded, run \`/catan r5 plan a commercial\`. Record comparisons and all REVIEW lines; this is a **simulated tree cleanup -> sidewalk -> Commercial District** plan only.
4. Run \`/catan r5 plan a logistics\` with City A Logistics site chunks loaded. Record comparisons and REVIEW lines; service-connection stage is *simulated* only.
5. Confirm existing purchased Commercial/Logistics building levels and national resources remain unchanged; no civic building appears. If City A has a logged/installed R5 group whose current state differs from the pinned old baseline, a refusal is an expected conservative behavior.
6. Optional: test an unloaded civic chunk. Command should refuse/flag UNLOADED without force-loading chunks.
7. Send exact two command reports (chat screenshots or \`latest.log\` lines tagged \`R5 STAGED\`) and PASS/FAIL. No construction approval, no token creation and **no crowbar retest** requested.

**Status:** read-only R5 planning. Physical civics, cleanup tree, houses, R6 walls/casemates and live-world migration remain disabled. Negative world-writing tests from v0.1.11 stay outstanding; they are not magically passed by this build.
