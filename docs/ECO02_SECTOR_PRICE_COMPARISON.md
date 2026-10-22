# ECO-02 comparison: provisional DEF-04 sector prices

The owner authorized this **provisional two-tier iteration**: each compass side's basic purchase costs **$1,500 + 15 Wood + 30 Stone**; its incremental basic→reinforced upgrade costs **$4,000 + 50 Concrete + 15 Steel**. These are abstract nation stockpile units, not inventory blocks. Reinforced requires purchased basic and includes no gun, ammunition, crew or armed equipment. This report compares those authorized values; it changes no production, procurement or other balance constants and does not approve additional geometry.

`tools/simulate_sector_economy.py` and `tests/test_sector_economy.py` were added and executed. The tool parses the current Java price tables, nation initialization, raw output/upkeep/tax constants, existing ammo offers and actual City A map yields, recording SHA-256 for all eight source inputs. It also parses and pins the separate versioned sector catalog (`VERSION`, `BASIC`, `REINFORCED_INCREMENT`), rejects values inconsistent with this authorized iteration, and uses the parsed price in comparisons. It produces a static cost comparison and a deliberately bounded raw-production spending-policy model. It is **not balance certification**, an optimal strategy, a multiplayer trial or a full economic-growth simulator.

The final comparison uses the actual applied checkout catalog
`DEF04_SECTOR_PRICES_V1_20261018`, with its exact source hash recorded in
`/workspace/integration-evidence/def04-economy/applied-catalog-comparison/comparison.json`.
Earlier candidate-source runs remain preserved and labeled; they are not substituted
for the final applied-catalog execution.

## Static affordability and opportunity cost

| Purchase | Money | Wood | Stone | Concrete | Steel |
|---|---:|---:|---:|---:|---:|
| One basic side | 1,500 | 15 | 30 | — | — |
| One incremental reinforced upgrade | 4,000 | — | — | 50 | 15 |
| One side, basic plus reinforced | 5,500 | 15 | 30 | 50 | 15 |
| Four basic sides, financial hypothesis | 6,000 | 60 | 120 | — | — |
| Four sides, basic plus reinforced, financial hypothesis | 22,000 | 60 | 120 | 200 | 60 |
| Current producer L1→L2 | 2,000 | 30 | 30 | — | — |
| Current producer L2→L3 | 5,000 | — | — | 40 | 10 |
| Current city L1→L2 | 6,000 | 80 | 80 | — | — |
| Current ordinary factory L1→L2 base price | 5,000 | — | — | 60 | 20 |

The ordinary factory row is the 100% base used by types such as Steel Mill; Weapons/Vehicle Factory percentage multipliers are not silently treated as 100%.

One basic side competes with the first producer upgrade for **exactly the same 30 Stone**. Its cheaper currency/wood bill does not remove that starter bottleneck. Four basic sides cost the same currency as City L2 but **50% more Stone**, before weapons or buildings. One reinforced increment costs less cash than producer L2→L3 but **25% more Concrete and 50% more Steel**. The two purchases therefore are not interchangeable despite similar money costs.

Fresh nation creation supplies $12,000 and zero resource units in the inspected code. That is enough initial cash for four basic sides, but not their materials; the $22,000 complete reinforced perimeter also exceeds starting cash before industrial construction. These are budget comparisons, not availability claims. Only explicitly registered/approved site geometry may be purchased; multiplying by four does not enable East/South/West, other cities, seam transitions or four-sector authoring.

## Declared timed policy model

The timed cases begin with one **already-owned City A**, city/producer L1, $12,000 and zero stocks, immediately after a cycle boundary. Founding/claim placement and travel are outside the clock. The actual current map gives A 50% Wood/Stone/Agriculture yields. Raw L1 output is 10 before yield, so each 15-minute cycle yields 5 Wood, 5 Stone, 5 Agriculture; one Agriculture upkeep leaves net 4 Agriculture. Base territorial cash is $125/cycle. Thus one starter produces **20 Stone/hour**, $500/hour and net 16 Agriculture/hour while continuously running at 20 TPS. Stopped/paused-server time does not produce resources.

Producer L2 changes subsequent cycles to 9 Wood/Stone each (36/hour) and net 7 Agriculture/cycle. The tool does not retroactively apply upgraded output to the cycle that paid for it. One declared construction goal can be bought each cycle, after the equipment/ammunition priority described below; production and territorial income follow the inspected engine's phases.

Other explicit assumptions:

- No debug/demo funds, commerce, trade, capture rewards, processors, destruction, replacement equipment, travel delay, queues or extra territories.
- Shooting cases first buy **two existing-price pistols**, then buy ordinary 32-round 9mm packs to cover 64 or 128 rounds per **nation** per hour. This is 32 or 64 rounds per player/hour if split equally between two players; it is not a per-player multiplier.
- Ammunition spending has priority over the declared construction order. No initial/free rounds or backlog catch-up is assumed; unmet demand is recorded. The no-shooting case buys neither pistols nor ammo.
- The model never auto-purchases a reinforced tier without an industrial source. A lone A starter has no Steel/Concrete production. Timed advanced industry, expansion and reinforced perimeter growth would require additional explicit policies and evidence; they are not invented here.

## Executed 48-hour comparison

Times below are completion times for the **entire named goal sequence**, in active running-server hours:

| Declared construction sequence | No shooting | 64 rounds/nation/hour | 128 rounds/nation/hour |
|---|---:|---:|---|
| One basic side, stay producer L1 | 1.50 h | 3.50 h | Not reached by 48 h |
| Producer L2 first, then one basic side | 2.50 h | 4.75 h | Not reached by 48 h |
| One basic side first, then producer L2 | 3.00 h | 6.50 h | Not reached by 48 h |
| Four basic sides, stay producer L1 | 6.00 h | 12.50 h | Not reached by 48 h |
| Producer L2 first, then four basic sides | 5.00 h | 8.25 h | Not reached by 48 h |

Without shooting, either the first basic side or producer L2 is initially affordable after 1.5 hours. Buying defense first delays that production investment; buying production first completes the two-goal pair half an hour earlier and the four-side financial target an hour earlier. This illustrates an actual opportunity cost, not a recommendation to leave every city undefended.

A current 9mm pack costs $100 + 5 Stone. Maintaining 64 rounds/hour consumes $200 + 10 Stone/hour—half the lone L1 starter's Stone output. Maintaining 128 consumes $400 + 20 Stone/hour—all of it. Under this declared ammo-first policy, the 128-round cases never save the first 30 Stone for **either** basic defense **or** producer L2 within 48 hours. That is a policy/resource-contention result, not an unavoidable game softlock: stopping/slowing ammo purchases, trade or a different expansion/reward policy changes it. No replacement ammo price is authorized or implemented by this report.

## Industrial and baseline issues still needing a broader model

Current ordinary L1 processors output 8 Concrete or Steel per batch when funded: Concrete consumes 10 Stone; Steel consumes 10 Iron + 5 Coal. A reinforced increment's 50 Concrete and 15 Steel therefore imply at least 7 and 2 ordinary base batches respectively, with batch overshoot. Those are **pre-existing-capacity arithmetic**, not fresh-nation unlock/build timelines. Logistics, batching level, input starvation, processor order and targets alter the actual outcome. Default target 100 is a soft stopping threshold; saving 200 Concrete for one hypothetical aggregate perimeter can pause at 104 unless the target is raised, while separate 50-unit upgrades can allow production to resume as stocks are spent. Do not confuse this with a hard resource cap or a four-side purchase transaction.

Earlier ECO-02 findings remain relevant: early Stone/upgrade/ammo contention, unbounded idle stockpiles, narrow Agriculture/Fabric demand, processor input/order/target traps and reward shortcuts. Do not nerf the general economy to offset optional rich demo grants. Existing ammo, fuel/energy, retrieval/security, destruction/refund and conquest policies remain separate; no armed equipment is included in the wall price.

The transferred P2 review package contains the prior Markdown economy audit, but its original executable simulation/results ZIP was not located for this task. Its reported historical 250 Java/Python matches and route timelines are **not reused as fresh results**. This smaller current-source model has Python arithmetic/policy tests; it was not compared against a running Java economy engine or an actual modpack session. Source hashes make its inputs auditable without overstating equivalence.

## Commands and preserved evidence

```bash
PYTHONDONTWRITEBYTECODE=1 python3 tools/simulate_sector_economy.py \
  --repo /workspace/catanCraft \
  --output /workspace/integration-evidence/def04-economy/applied-catalog-comparison \
  --hours 48
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tests -p test_sector_economy.py -v
```

Actual results: **15 scenario variants, 2,895 cycle snapshots, 14 Python tests passed**, zero failed. Reports are `comparison.json` (exact inputs, catalog version/hash/status, assumptions, costs and goals), `cycles.csv`, and `summary.csv`; final applied-source logs are `/workspace/integration-evidence/def04-focused/economy-simulation.log` and `python-all.log` (52/52 total Python tests, including these 14). Earlier directories/logs remain as intermediate evidence; catalog-source binding and an assumption's wording changed, not the listed economic outcomes. Existing output directories are never overwritten. The executions used Python's equivalent `-B` bytecode-disable flag; the commands above state the no-bytecode environment setting explicitly.

Next step: retain these provisional values for the authorized iteration, use the bounded north P2 transaction tests and isolated walkthrough to observe actual progression trade-offs, then decide whether a broader calibrated ECO-02 model or any separately approved balance change is warranted. This audit supplies no price rebalance, all-city geometry approval, deployable demo world, functional guns, NPC work or multiplayer acceptance.
