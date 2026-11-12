# ECO-02 / ECO-03 — illustrative economy pacing comparison

These are **offline alternatives, not approved prices, runtime settings or a chosen match length**. The user has not selected a campaign duration. The existing15-minute economy remains unchanged. This bounded comparison changes only the interval between economic cycles, preserving every per-cycle income, raw output, processing batch, food upkeep, price, starting balance and physical slot cap.

The purpose is to distinguish faster progression from cheaper equipment. Shortening the cycle speeds up all production and income together, while fixed real-time combat ammunition consumption does not speed up automatically. That changes the relative burden of fighting even when no shop price changes.

## Three illustrative profiles

| Profile | Economic cycle | Cycles/hour | Economic cycle rate versus current |
|---|---:|---:|---:|
| Long / current baseline | 15 minutes | 4 | 1× |
| Medium hypothesis | 5 minutes | 12 | 3× |
| Fast hypothesis | 1 minute | 60 | 15× |

“Long,” “medium” and “fast” are relative labels for this comparison. They are not target durations or recommendations to change `EconomyBalance.CYCLE_MINUTES`. Food upkeep runs at the same increased cycle rate as agriculture; no hidden free food, bonus stockpile, reduced purchase cost or free industrial resource is added.

## Economic milestones

The two starter experiments each begin from a fresh independent nation with the actual $12,000 treasury and zero materials. The rest use the same managed-target, two-player41-action queue from045. Starter expenses are **not** silently granted or added free to that separate queue. Vehicle timestamps reflect its declared purchase order, not the earliest possible vehicle strategy. The queue covers developed industry, two AK kits, pickup/LAV and four Basic-to-Reinforced sectors; it is not all buildings throughL5 or a simulation of winning the game.

| Milestone | Current15-minute cycle | Hypothetical5-minute cycle | Hypothetical1-minute cycle |
|---|---:|---:|---:|
| One player: pistol +32 rounds, independent starter test | 30 min | 10 min | 2 min |
| Two players: pistol +32 rounds each, independent starter test | 1 h | 20 min | 4 min |
| Commercial District in41-action queue | 2 h30 min | 50 min | 10 min |
| Vehicle Factory constructed in queue | 24 h15 min | 8 h5 min | 1 h37 min |
| Pickup purchased in queue | 44 h30 min | 14 h50 min | 2 h58 min |
| LAV purchased in queue | 48 h | 16 h | 3 h12 min |
| Complete41-action queue | 66 h15 min | 22 h5 min | 4 h25 min |

All three complete the exact same purchase sequence in265 economic cycles, with identical individual debits and final balances. The different wall-clock timestamps are intentional time conversion. The wrapper does not use045’s hard-coded four-cycles-per-hour labels: it derives each event from its actual cycle index and the selected interval.

These numbers assume20TPS active-server time and completed scheduled cycles, no offline/catch-up income, immediate affordable purchases, no travel/construction delays, no trade, no contested expansion, no losses and no monument rewards. Native factory/source authorization may be narrower than economic capability. The ambiguous first schedule-adoption interval is excluded, as in045. Factory construction does not by itself prove a usable native vehicle bay or finished purchase interface.

## Replenishment becomes much easier at faster cadence

Hold player behavior constant for this sensitivity example: four players each use four32-round AK ammunition packs per real active hour. That always costs **$2,800 +32 Steel +16 Explosives per hour** at current prices. It is an assumption, not measured combat behavior.

| Cash-only comparison | Current15-minute cycle | Hypothetical5-minute cycle | Hypothetical1-minute cycle |
|---|---:|---:|---:|
| One starter territory income/hour | $500 | $1,500 | $7,500 |
| Four territories +oneL1 Commercial income/hour | $2,500 | $7,500 | $37,500 |
| Fixed ammo cash demand as share of that four-territory income | 112% | 37.33% | 7.47% |

The materials still require their real processing chain. This table compares cash burden only; it does not prove sustainable ammunition manufacture, vehicle fuel supply or combat balance. Faster cadence gives more production and replenishment opportunities during the same real-time fight. Monument reward pools and existing timers would also need review if the economic interval changed. More frequent production work would require native scheduling/performance validation before adoption.

## Recommendation from this comparison

Use the profiles to select desired milestones first: starter combat access, first vehicle and sustained replacement spending. A1-minute cycle compresses this particular midgame queue into roughly4.4 active hours, but also makes the example recurring ammo bill a small fraction of mature income. A5-minute cycle still leaves this queue near22 active hours. That may fit a persistent campaign or be too slow for a short session; the user has not chosen between those goals.

Do not apply a blanket cycle-speed or price cut from this table alone. If early combat should start quickly while later industry remains valuable, the next offline comparison should separately tune starter access/infrastructure and recurring equipment expenditure. Preserve historical transaction prices and validate any new versioned schedule before runtime use.

## Reproduce and verify

Run `python3 tools/economy46/pace.py`. It dynamically imports `tools/economy45/simulate.py` without executing its `main()` or rewriting045 outputs, then reads current source values. Only `tools/economy46/results.json` is written. JSON includes source/model hashes, the three profiles, event-by-event debits, snapshots, assumptions and checks.

Executed checks passed: current managed baseline265 cycles/66.25 hours; exact same actions/cycle indices/debits/final stock across all profiles; correct independent starter readiness at two/four cycles; unchanged per-cycle rules/prices; nonnegative balances; physical slot caps; and unchanged real-hour ammunition demand. The045 source-native six-vector comparison remains prior evidence for selected cycle calculations. This pacing experiment does **not** claim a new native engine run, full-game simulation, optimal purchase order, combat acceptance or owner approval.

No runtime price/configuration, world, installed mod or historical record changed. TaCZ-only handheld policy and deferred SBW explosives remain intact. This does not close SEC-01 crowbar acceptance or bypass native vehicle delivery qualification.
