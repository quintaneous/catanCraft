# CatanCraft v0.1.7 — User-reported solo acceptance

**Recorded:** 2026-10-12  
**Environment:** Minecraft 1.20.1, Forge 47.4.10; `catancraft-0.1.7.jar`.  
**Result:** **17 PASS, 0 FAIL, 1 SKIP** across the v0.1.7-focused checklist. Results below are reported by the user, not individually backed by a new uploaded latest.log.

## A — Installation and stability: 3/3 PASS

- PASS — Server and client load v0.1.7 without errors or duplicate CatanCraft JARs.
- PASS — `/catan map assetstatus` = 38 ready, 0 invalid.
- PASS — Existing world, buildings, nation ownership and stockpiles remain unchanged.

## B — New complete economic debug report: 7 PASS, 1 SKIP

- PASS — `/catan debug production a` displays the full economic ledger.
- PASS — Page navigation works via Next/Prev or `/catan debug production a 2`.
- PASS — Raw-resource production and Agriculture upkeep displayed.
- PASS — Every purchased factory displays consumption, output and operating status.
- PASS — Logistics Center bonus, Commercial District income and base territory income displayed.
- PASS — Net stockpile changes for the next 15-minute cycle displayed.
- PASS — Viewing the report does not change money, materials, buildings or production targets.
- SKIP — Not yet checked whether `logs/latest.log` includes complete `ECONOMY AUDIT BEGIN`…`ECONOMY AUDIT END` output.

## C — Production behavior: 4/4 PASS

- PASS — Refinery stops when Fuel stock is above target.
- PASS — Increasing target above stock lets the Refinery run when Oil is available.
- PASS — Logistics Center L1 increases Refinery L1 to 9 Fuel per 15-minute cycle, consuming 10 Oil.
- PASS — Logistics Center L2 increases Refinery L1 to 10 Fuel per 15-minute cycle, consuming 10 Oil.

## D — Safety regression: 3/3 PASS

- PASS — `/catan r5 status` says read-only.
- PASS — `/catan defense status` says defense installation disabled.
- PASS — Existing city blocks, roads, factories and Town Halls were not changed.

## Accepted scope and still-open items

The reported tests accept the **v0.1.7 production ledger and Logistics Center game mechanics on the solo test server**. The single logging verification remains untested, not failed. No repeated economy tests needed unless a future patch changes economic logic.

Do **not** treat this as acceptance of:
- Superb Warfare purchased-vehicle crowbar protection. User already reproduced packing despite warning in v0.1.6; security issue **#6 OPEN**, still unfixed in v0.1.7.
- R5 physical city/civic migration or R4 defense purchases. Both are intentionally disabled pending safe journaled sparse-world edits.
- R5 City A Commercial District's 94 world preflight mismatches (92 planned tree-removal changes + 2 unresolved), house façade R1/R2 baseline conflicts, City I unloaded-site recheck.
- Multiplayer contested capture/trade/warfare testing (intentionally excluded).

**Next engineering work:** durable per-city R5 world migration and exact source-state detection, while separately investigating Superb Warfare's actual crowbar retrieval path. Do not flatten, fully repaste, or automatically delete developed cities.
