# 0.1.45 economy simulation and fixed vehicle bay policy

Validated development checkpoint; no owner installation or live vehicle shop activation.

## Exact source and artifact

- Source: `c04f7bc7bd9e1306fe446bac85c01d60db9e2f32`; tree `e5ac8860e348825f71941295ff692c916e68ecbe`.
- [Workflow43112426119](https://github.com/quintaneous/catanCraft/actions/runs/43112426119): SUCCESS on the first attempt,73/73 selected native tests, zero failures.
- JAR `catancraft-0.1.45-economy-bays.jar`:3,623,264 bytes; SHA-256 `cad8c1a504951142bfb80fe22a2662acff3fb234c486fc9864e67e5e1c862ce0`.
- [Artifact13897431502](https://github.com/quintaneous/catanCraft/actions/runs/43112426119/artifacts/13897431502):6,979,527 bytes; SHA-256 `8b946715ab983aa2243b14127ab33800315b1681bd5b7e355e01df20c643eeb1`.
- [Machine verification](evidence/economy45_validation.json). Later simulation/report commits do not change the exact compiled source.

`tools/review45/verify.py` independently checked ZIP integrity/hash/path safety, all488 committed source files byte-for-byte, source/JAR identity, each test exit/count/pass log and six actual native economy vectors. All624 existing044 compiled classes and224 existing data/mixin entries are unchanged. New code is the unregistered bay policy and test classes; no existing runtime purchasing or economy logic changed. The earlier044220-phase native optional-provider result is preserved, not claimed rerun here.

## Coverage and findings

Six actual `EconomyEngine.scheduledCycle` tests cover starter production, Java rounding, partial food upkeep, processor dependency order, logistics target overshoot/pause, commercial income and unowned/reload behavior. Nine original clock and50 baseline tests pass. Eight new bay-policy tests cover exact facility/session/actor identity,0/180 fractional transforms, source/runtime geometry binding, whole clearance/support/boarding/departure masks, blocked/unloaded/foreign sites, expiry and refusal to move a confirmed quote elsewhere. Probes are synthetic; native geometry and entity delivery remain separate gates.

[Offline model/report](ECONOMY_SIMULATION_045.md) extracts17 source inputs, current52 TaCZ gun/17 ammo prices and construction/upgrade/defense/vehicle costs. Nine strategies cover2–200 active-server hours, two/four-player spending and managed/unattended production. The six Python vectors matched the actual native log JSON exactly using `--native-log`; this validates selected production calculations, not an optimal strategy or full combat simulation. All monetary/resource prices remain provisional and unchanged.

[Read-only authored survey](VEHICLE_PROCUREMENT_045_SURVEY.md) identifies a16-step supported counter route and700 clear lane cells in the pinned factory. `python3 tools/vehicle45/survey.py` reproduces its hash/source checks without opening a world. Candidate geometry is not an approved native bay.

## Next vehicle work and limits

Qualify initialized pickup/LAV full OBBs, support, boarding/dismount and departure on the authored lane in controlled fixtures. Add durable entity reservation, original price/UUID provenance and conservative interruption/restart reconciliation before wiring the physical counter and both command aliases. Existing player-relative command procurement remains unchanged until that replacement passes; this checkpoint does not pretend it is a finished vehicle shop.

No live world or installed mods accessed; preserve accepted City A and033/034 recovery. No reset, import, multiplayer, repeated accepted purchase tests or Blender assignment. Keep one combined owner build/checklist. Actual installed-pack/client acceptance and combat/price calibration remain separate. SEC-01 crowbar remains **UNRESOLVED / HIGH PRIORITY**.
