# Reusable clean server and cumulative progression regression

Owner requirement, 2026-11-14 UTC, under INT-01/INT-03 and roadmap #10:
keep a pristine authored CatanCraft baseline so every update can be tested from
the beginning through later tiers, repeating earlier functionality as the game
grows. This complements [server-wide coverage](SERVER_WIDE_RELEASE_REQUIREMENTS.md).
It is not permission to reset the owner's world or discard accepted saves.

## Intended workflow

Keep one immutable source map and versioned matching runtime/configuration inputs.
"Blank" means no players, nations, claimed territories, purchases or inventories;
the authored 16-city terrain, roads, plots and scenery remain present. Create each
new session in a separate directory with a unique world identity. Never launch
the source, reset an existing run, or copy a played run as the fresh baseline.
Keep previous runs, failure logs and checkpoints for diagnosis and compatibility.

Normal-economy runs use normal starting resources and actual earned unlocks.
Funded fixtures may speed up isolated technical checks in separate clearly labeled
runs; they cannot establish normal pacing, affordability or progression. Do not
silently seed inventories, auto-purchase buildings or enable showroom funding.
Fresh-start tests also cannot replace upgrades/restarts of existing paid progress.

| Checkpoint | Required progression and repeat coverage |
| --- | --- |
| Clean start | Complete map/assets; no inherited progress; ordinary first startup |
| Founding / TH1 | Start or claim as applicable; automatic base preparation; ownership; starting economy and starter access |
| TH2 | Earn resources through appropriate producers/trade; upgrade; exercise all currently eligible factories, civic options and services |
| TH3 | Interleave production/factory purchases needed for the next tier; check new unlocks and earlier functionality again |
| TH4 | Prestige placement, applicable producer/factory upgrades, earlier purchases and services |
| TH5 | Remaining eligible tiers and slots; TaCZ offers/rewards, vehicles/Fuel, Basic/Reinforced compass walls and mixed connections |
| Persistence | Normal restart and developer-controlled interruption recovery; same items, balances, receipts, fuel and levels; no cross-city leakage |

Each checkpoint includes every earlier applicable check across A–P. This is not
an instruction to reach TH5 before buying producers/factories, or to place nine
industries simultaneously in three plots: alternative layouts require separate
fresh scenarios. Resource-specific eligibility is explicit, not a city rollout
exception. Preserve physical source validation, protected roads/corners, exact
prices, ownership/refusal checks and 033/034 recovery. Owner acceptance remains
one focused solo checklist, initially in D, without repeating accepted A walls.

## Implemented developer tools

`tools/create_progression_run.py` reuses the existing `package_authored_demo`
assembler and its pinned ZIP/JAR/asset, NBT, path and inherited-progress checks.
It verifies the original input-manifest hash, derives a unique UUID in a separate
manifest, records both hashes, and exclusively creates a new output. It preserves
the historical inner world folder/LevelName required by current authorization.
No source file changes, server execution, downloads, installed-mod reads, EULA
consent, automatic funding or purchases occur. Existing outputs and symlinks refuse;
a publish failure retains any new partial output for inspection.

The generated server properties request Adventure mode (forced for new joins),
Normal difficulty and PvP, retaining localhost-only authenticated whitelist and
one player. This is a **candidate progression profile**, not a native settings
pass. Source `level.dat` and gamerules remain unchanged. No datapacks are accepted
by this wrapper until there is a separately reviewed no-grant contract. EULA stays
false. Existing start scripts still verify prerequisites, bind the first launch
and preserve legitimate subsequent saves. No fresh-on-every-start behavior exists.

After supplying a reviewed assembler input manifest for the intended current
runtime, prepare a run (developer invocation, not owner installation instructions):

```sh
python3 tools/create_progression_run.py \
  --input-root /path/to/reviewed-inputs \
  --manifest /path/to/reviewed-inputs/runtime-input.json \
  --manifest-sha256 REVIEWED_MANIFEST_SHA256 \
  --output /path/to/test-runs/run-001
```

The recovered `INPUT_MANIFEST.json` is the original asset-transfer inventory,
**not** this assembler runtime manifest. Do not invent runtime hashes or fabricate
matching-source validation evidence to make assembly succeed.

Each generated run contains immutable `PROGRESSION_RUN.json` provenance,
`PROGRESSION_PLAN.json` with **112 stage-level obligations (16 cities × 7 checkpoints)**,
and separate cumulative result templates for each checkpoint, all `NOT_RUN`.
The plan carries actual city rotation, resource specialization, founding route
and eligible industry slots. Per-tier/slot native adapters are still to be built;
a stage row is an obligation to collect that coverage, not proof it exists.

`tools/progression_ladder.py` checks submitted evidence indexes:

```sh
python3 tools/progression_ladder.py \
  --plan /path/to/test-runs/run-001/PROGRESSION_PLAN.json \
  --checkpoint tier_3 \
  --results /path/to/test-runs/run-001/evidence/results-tier_3.json \
  --evidence-root /path/to/test-runs/run-001
```

Missing earlier stages/cities, duplicated rows, foreign run/plan/checkpoint IDs,
unreviewed N/A, funded results and absent/changed/empty PASS evidence refuse.
`FAIL`, `BLOCKED` and `NOT_RUN` keep the checkpoint blocked. Exit 0 means only
`EVIDENCE_COMPLETE_REQUIRES_REVIEW`: hashes and coverage entries match. The tool
does not execute tests, interpret logs, establish evidence semantics or prove
fresh reruns; those flags explicitly remain false. Native adapters and reviewed
execution provenance are required before this becomes an automated gameplay gate.
Keep failed records; use `--new-results NEW_FILE` for an additional attempt.

## Verified source recovered in this task

The exact original clean R7 input archives were recovered, not a developed save
or a terrain redesign. Their historical hashes match:

| Input | SHA-256 |
| --- | --- |
| `CatanCraft_Clean_R7_Input_Part_1.zip` | `5b764eda3052c917a1000081ada7ea916ea4f6b917b2d766ddcab063584ce894` |
| `CatanCraft_Clean_R7_Input_Part_2.zip` | `daff27f63af1938bdbbae2144f89162d128a94f5e509eea9bc963e1c5bf83036` |
| Original `INPUT_MANIFEST.json` | `e7ecd5d0f0ac5c1457be5c234d2e5e08c9239007787b20e0bdc45b3e2b8d862d` |
| Current and transferred map (identical) | `e50d9753c807b5fe6f8fdee9765ae09640d951cc0ba2d59f1612f0923485c986` |

Verified extraction: `/workspace/scratch/a1b6a42f71e7/fresh-baseline-verified/CatanCraft_Clean_R7_Input`.
Safe archive preflight/readback covered 41 regular files and 96,665,399 expanded
bytes. Deep verification passed 40/40 manifest hashes, 17 world files, 16 regions
and 16,384 decoded chunk records; all source files remained unchanged. Map is A–P;
R4A/R5/probe/TH4/TH5 pins match current source. The world has no player tag, nation
save, playerdata or purchase histories. Funding and authorization references are
outside the pristine world. [Deep source report](evidence/fresh_progression_source_verification.json).

Original identity: `CatanCraft_Season1_R7_Development`, LevelName
`CatanCraft Environment V4 - Visual Polish R2`, seed 1042026, DataVersion 3465.
Source metadata is Creative/Peaceful, Time/DayTime 6000, `randomTickSpeed=0`,
`keepInventory=true`, with mob/daylight/weather/fire updates disabled. These are
inspection settings and **not qualified normal-progression/balance settings**.
Retain this source unchanged; review and validate any derived gameplay settings.

## Validation and remaining delivery gates

Executed 13 new synthetic packaging/evidence tests plus all 51 retained assembler
and component-stager tests: **64 passed, 0 failed**. New coverage includes separate
run identities/files, preserved inputs and paid prior-run state, exclusive output
and concurrent-create refusal, inherited-ledger/datapack rejection, cumulative
all-city coverage, stale/funded/missing evidence refusal and compatibility with
the existing launcher verifier. Java/Forge calls are mocked; these are Python
tests, not native gameplay, Windows launch, modpack or owner acceptance.
[Machine record and retained logs](evidence/fresh_progression_validation.json).

Still required before a playable owner package:

1. Assemble a matching current Forge runtime, CatanCraft, TaCZ, SBW and all required
   dependency/content/config assets with exact hashes and source evidence. The
   existing assembler does not bundle or certify the full modpack.
2. Qualify normal world initialization/settings and all-city automatic base
   preparation. Resolve the existing shop/vehicle/wall coverage gaps.
3. Implement native progression scenarios and cumulative reruns, including actual
   economy/resource acquisition and alternative building layouts; retain separate
   existing-save upgrade/recovery coverage and failed logs.
4. Validate dedicated startup/restart and the eventual supported owner launcher
   and client interaction. Personal EULA acceptance remains separate.

No actual progression-server package, mod rebuild or native gameplay test was
performed in this task. No live world/installed mod changed. SEC-01 remains
**UNRESOLVED / HIGH PRIORITY**. This work supports the server-wide retrofit; it
does not delay it in favor of unrelated features or ask the owner to test again now.
