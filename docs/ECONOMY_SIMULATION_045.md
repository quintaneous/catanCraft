# ECO-02 / ECO-03 — offline economy simulation, 0.1.45 preparation

The current numbers point to **strong progression, production and management effects that must be measured before changing gun prices**. This is a reproducible affordability model of current source values, not a claim that these strategies are optimal, that every map site is cleared for placement, or that combat is balanced. No runtime price, world, installed mod or historical purchase was changed.

## Reproduce

Run `python3 tools/economy45/simulate.py` from the repository root. It reads Java/catalog/map files directly and writes `tools/economy45/results.json`, including SHA-256 provenance, all extracted cost schedules, six independent parity vectors, assumptions, purchase events and balances. Run with `--native-log PATH` to require exact agreement with all six `ECONOMY45_NATIVE_PASS` log vectors. A missing/mismatched vector fails verification.

## Actual starting economy

| Item | Current value |
|---|---:|
| New nation treasury | $12,000 |
| Starting material stockpile | 0 |
| Production interval | 15 active-server minutes |
| A L1 production per interval | 5 Wood, 5 Stone, 5 Agriculture |
| A L1 upkeep | 1 Agriculture, charged once for both nonfood outputs |
| A base cash income | $125/interval; $500/hour |
| Commercial L1 extra income | $125/interval; $500/hour |
| Commercial L1 construction | $3,000 + 50 Wood + 50 Stone |

The model uses completed scheduled intervals at 20 TPS, with no offline or catch-up income. The real schedule skips its ambiguous first adoption interval; a fresh arrival can wait up to one interval before the modeled first payment. Wall-clock time grows if the server runs below20TPS. Nation income is not multiplied by player count.

## Early balances with no purchases

| Active hours | Treasury | Wood | Stone | Agriculture |
|---:|---:|---:|---:|---:|
| 2 | $13,000 | 40 | 40 | 32 |
| 4 | $14,000 | 80 | 80 | 64 |
| 8 | $16,000 | 160 | 160 | 128 |
| 16 | $20,000 | 320 | 320 | 256 |

A first Commercial District cannot be funded in materials until2.5 hours without trade or grants. Building it then pays its $3,000 cash cost back after another6 hours: cash catches the no-purchase route at8.5 total hours, while the50 Wood/Stone remains an opportunity cost. It cannot be treated as instant free income.

## Constrained purchase strategies

The model enforces city level prerequisites, economic development caps, three physical industry plots per city, one instance of each scripted building, adjacency for neutral claims, and upgrade ceilings. The actual economic slot allowance rises2/4/6/8/10, but only three of those slots can be these industrial factories. Nine factory types therefore need at least three cities. The plans use A/F/G for factories and B as an adjacent expansion route to F; the stone variant also claims E. Availability here means economic/plot-count feasibility only: native source authorization, reserved geometry, pathfinding, occupied sites, travel and construction time remain outside the model.

The baseline buys Commercial, TH2 and producerL2 in A, claims B/F/G, then develops steel, concrete, fuel, weapons, vehicles, mechanical parts, electronics and chemicals. It buys one AK per player, four32-round ammo packs each, one pickup and one LAV, finishes the remaining industry/logistics, and buys four Basic sectors plus their Reinforced increments. This is an illustrative purchase queue, not the only or fastest way to play. Starter sidearms are independently priced in the catalog; players need not wait for this end-to-end queue to fight.

| Strategy | Completion of scripted target | At16 hours |
|---|---:|---|
| progression 2 players | 86 hours | $5,875; 8 purchases |
| progression 4 players | 86 hours | $5,875; 8 purchases |
| managed targets 2 players | 66.25 hours | $5,875; 8 purchases |
| stone expansion 2 players | 58.75 hours | $3,375; 9 purchases |
| managed targets stone 2 players | 59.25 hours | $3,375; 9 purchases |
| all industries to level5 | Incomplete at200h (20 actions left) | $3,375; 9 purchases |
| managed targets all industries to level5 | Incomplete at200h (20 actions left) | $3,375; 9 purchases |

The unattended high-target baseline concrete plant consumes10 Stone per batch while the A L2 starter only produces9 Stone each interval. With a high construction target, it continually diverts almost all available Stone into Concrete. This delays additional cities and factories even while money/other resources accumulate. Adding the adjacent stone territory reduces the scripted target from86 to58.75 hours without changing any price. That is a resource-allocation result, not evidence every gun should be cheaper. The implemented managed-target alternative uses the next purchase’s exact requirements, recursively expands processor inputs, offsets existing stock and pauses irrelevant outputs. It reduces the same no-extra-stone plan to66.25 hours; it is still not an optimum. With extra stone, just-in-time management takes59.25 hours versus58.75 with buffering: aggressive pausing can also delay useful inventory. Trade is not simulated and no free supplies are assumed. The86-hour result is a deliberately unattended management case, not a required play time or a reason by itself to discount prices.

Production targets are100 by default. For the unattended scenarios, the model explicitly assumes the player raises Concrete/Steel/Mechanical Parts/Electronics targets to1000; other targets remain100. Without a target change, Concrete can stop around100–104 and cannot fund TH3’s160 requirement. Batch output can overshoot a target; the model preserves that behavior. Same-tier processors follow modeled territory order and building insertion order, which affects scarcity. Existing saved worlds with a different territory order require their own scenario.

## All-level capital schedule

The extraction includes each of the11 building types at construction and all four upgrade steps, city upgrades1→5, producer upgrades1→5, neutral claims, both whole-sector prices, legacy defense module prices,52 active TaCZ guns,17 ammo types and the two existing vehicle offers. Internal upgrade-base rows and legacy aliases remain labeled in JSON; do not sum the entire dictionary because those are overlapping representations. Buying every gun is not treated as a required progression expense.

Building every type once and taking each toL5 costs the following, before city upgrades, producer upgrades, claims, defenses or equipment:

| Resource | Total |
|---|---:|
| concrete | 9,600 |
| electronics | 482 |
| mechanical_parts | 1,560 |
| money | 1,200,000 |
| steel | 5,641 |
| stone | 601 |
| wood | 601 |

The all-industries-toL5 plans include city and producer upgrades in their event queues; completion or remaining actions at200 modeled hours are shown separately for unattended and managed production. This should not become an assumed intended match length. It shows why the desired campaign/session duration needs an explicit balance target before replacing prices.

## Combat replenishment sensitivity

These are declared consumption assumptions, not simulated TaCZ firing or player behavior. One pack is32 rounds of7.62×39 at $175 +2 Steel +1 Explosives.

| Players | Packs each per active hour | Treasury/hour | Steel/hour | Explosives/hour |
|---:|---:|---:|---:|---:|
| 2 | 1 | $350 | 4 | 2 |
| 2 | 2 | $700 | 8 | 4 |
| 2 | 4 | $1,400 | 16 | 8 |
| 4 | 1 | $700 | 8 | 4 |
| 4 | 2 | $1,400 | 16 | 8 |
| 4 | 4 | $2,800 | 32 | 16 |

Two players using two packs each per hour need$700/hour; four players need$1,400/hour. Starter base income alone is$500/hour. Four territories plus oneL1 Commercial produce$2,500/hour before any spending: four players using four packs each already need$2,800/hour in ammo cash alone. Materials and replacement guns add further demand. Vehicles in this report include acquisition cost only; mounted ammunition, repairs, fuel burn, losses and travel require separate native/behavioral inputs.

## Pricing recommendation

1. Keep current prices provisional while choosing an intended early-fight, first-vehicle and higher-tier progression window. The simulation can then solve against those windows instead of inventing a match length.
2. Prioritize early material access and processor target guidance. Cash discounts cannot solve missing Steel, Explosives, mechanical parts or the three-plot limit.
3. Compare equipment prices against the national recurring budget: separate infrastructure investment from replaceable guns/ammo. Model different team sizes without multiplying nation income.
4. Use deterministic affordability scenarios for the baseline and seeded stochastic losses/rewards later, once capture frequency, combat consumption and expected vehicle losses have explicit assumptions. Do not count rare monument drops as guaranteed progression income.
5. After matching a target pace, test proposed price/production variants offline before changing versioned runtime catalogs. Preserve historical charged prices and recovery records.

## Validation and limitations

Offline execution passed checks for the exact eight-cycle starter balance, Java half-up rounding, percentage-scaled material costs,52/17 active catalog coverage, nonnegative stock and slot limits. Six parity scenarios were executed independently by the Python model and matched all six actual Java engine log vectors exactly in [run43112426119](https://github.com/quintaneous/catanCraft/actions/runs/43112426119). The complete selected native suite passed73/73 tests. [Exact source, hashes and evidence](ECONOMY_VEHICLE_045_CHECKPOINT.md). Native fixtures additionally validate scheduled adoption/reload behavior that this affordability simulation does not emulate.

The model does not execute Minecraft, load a world, grant resources, perform purchases, prove native facility geometry, model nations bidding/trading, resolve combat, or certify balance. Map placement authorization may be narrower than economic capability. It uses actual repository values with a declared player strategy; full-game outcomes remain sensitive to player decisions. No in-game test, multiplayer task, reset or manual import is required to run it. SEC-01 remains unresolved/high priority; this analysis does not close crowbar acceptance.
