# CatanCraft development instructions

Continue the existing checkout and completed 0.1.28 integration. Inspect git status,
local history and remote development heads before editing; preserve newer commits,
uncommitted work, release archives, immutable assets and transaction histories.
Do not reset to v0.1-foundation, clone another project, merge into main, force-push,
or deploy. Input source snapshots are reference evidence only.

Canonical roadmap: https://github.com/quintaneous/catanCraft/issues/10. Read the
current issue before choosing work; use existing INT-01, INT-02, INT-03, DEF-02,
DEF-03/04 and SEC-01 IDs in the implementation log, not another master plan.
P1 remains historical prototype input. Roadmap revision 5 and the owner approve
City A NORTH P2 appearance and its P1 wider strip/local P2 extension for isolated
fresh-world testing. Preserve its frozen bytes and NW/NE ownership. Owner approval in issue #10 comment 6256056792 now also covers the delivered
City A P3 East/South/West Basic/Reinforced designs and exact sparse footprints
for the same isolated fresh-world test. Original P3 archive SHA-256:
96278c6ebfd1aef8454ebff9e51c0c56a48a273349d87928bf84e73ced039095.
All four appearance/footprint gates are cleared; do not ask again. This is not
native transaction, support, vehicle/gun/gate or modpack acceptance. Preserve
North P2 bytes, its NW/NE ownership, South SW/SE ownership and the full rear
resource corridor X[-770..-730]. Reconcile current reservations and validate
all connected-source upgrade modes, including North, before runtime enablement.
DEF-04 uses owner-approved PROVISIONAL PLAYTEST PRICING: Basic $1,500 + 15 Wood +
30 Stone; Reinforced increment $4,000 + 50 Concrete + 15 Steel, same for all
compass sides. Two structural tiers this iteration, not a final maximum. Pricing
does not approve missing geometry, weapons, ammunition, resupply or gates. Use
versioned catalog and historical journal prices; compare the schedule in ECO-02.
ECO-03 physical gun counters and facility-specific vehicle bays require a survey
of existing facilities and clearances first; no simultaneous Blender redesign.
Preserve ECO-02 analysis and track SEC-02 production-cycle replay as unresolved.

Current owner contract: docs/INTEGRATION_PRIORITIES.md and the active sector/demo
work in docs/INTEGRATION_STATUS.md. User instructions override older handoff setup
instructions. Producer R2, six R5 house groups and precise obstruction handling
remain implemented; do not restart them or remove repair/recovery code.

Defense purchases must represent actual world compass sectors (North=-Z,
East=+X, South=+Z, West=-X), not rotated local axes. One quote, confirmation,
recorded price and completed level per sector. Whole-sector preflight precedes
payment and placement. Preserve durable recovery, original agreed costs, exact
source recognition, ownership, neighbor receipts, corner ownership, road space,
rollback and duplicate protection. Modules are internal. Preserve historical
socket recovery; old receipts do not imply sector ownership. Unapproved artwork,
prices or compatibility contracts must fail closed, never default free.

Fresh demonstration releases require a separate clean authored world, matching
JAR/configuration/assets and launcher. Never access, copy, reset or overwrite the
owner's developed City A, active world, installed mods or live server. No new
legacy visual-repair requirement by default. Normal purchases, progression,
inventory and restart safety still require validation. Test fixtures must be
labeled and must not be shipped as visually approved artwork. Missing artwork
must not block independent accounting and interface development.

SBW weapon identifiers/geometry/ammunition/anti-packing require the actual
installed-version artifacts. The crowbar exploit is UNRESOLVED / HIGH PRIORITY.
No NPC/Combined Arms research, dependencies or implementation. Siege is next
but requires its own contract and is not implemented in this task. No functional
gates, trenches, rejected bunkers, orientation pavilion or terrain rollout.
No multiplayer tests.

Use Java 17 / Gradle 8.8 and existing Minecraft 1.20.1 / Forge 47.4.10 versions.
Run native GameTests in separate disposable development worlds with staged
fixtures. Retain every existing namespace and safety assertion. Preserve failed
logs and actual executed/passed/failed counts; Gradle NO-SOURCE is not a test pass.
Only one agent runs writable native worlds at a time. Delegate independent
asset audits/code review/tests when useful, with disjoint file ownership.
Record changes, dependencies, test evidence, checkpoints and exact blockers in
INTEGRATION_STATUS.md. Never claim owner visual/modpack acceptance from tests.

Current technical checkpoint: docs/INT02_CLEAN_R7_VALIDATION_20261019.md
continues the earlier P3 report. The complete clean authored-world input is
verified under /workspace/catancraft-clean-r7-inputs-20261019/. Preserve its
immutable source and folder/LevelName; stage both exact disposable-R7 and
fresh-demo identities only into new test copies. Normal founding/start plus
existing journaled R5 public bootstrap resolves the 28 P3 source differences
(the public operation itself has 1,661 writes). Never paste preview scenes.
Showroom grants remain separate from no-grant progression.

The bounded main-thread runner and actual copied-R7 production test now pass,
including all eight sector purchases/upgrades and real SavedData readback.
Keep the production resolver restricted to bound fresh-demo authorization,
canonical City A and the exact approved P2/P3 scope. No other-city activation.
Placement uses at most four writes per slice and a cooperative time budget;
atomic preparation and saves are not hard latency-bounded. Actual R7 quote /
preparation reached 7.188 seconds and a full slice 1.286 seconds. Do not erase
these limitations or infer an interactive frame-rate guarantee. Dedicated
packaged launcher startup/restart and owner client acceptance remain separate;
EULA acceptance has been requested but not supplied. Never ship the played
native test world as the clean demonstration world.
