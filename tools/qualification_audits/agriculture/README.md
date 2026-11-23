# Agriculture in the current economy

Gameplay source `6ed6eaf31f946195f8b75fa869308c915537a6ed`. The farm produces enough to fund every claim and pay full territorial upkeep on all 40 replayed routes. A single starter feeding a larger, highly upgraded industrial nation can run a sustained deficit.

## What players can do with Agriculture

| Interaction | Measured effect |
| --- | --- |
| Own a farm | A, D, M and P are the only Agriculture-producing territories on the Season 1 map. Each starter produces 5, 9, 15, 23 or 33 units per 15-minute production cycle at producer levels 1–5. |
| Operate territories | Each owned territory with any nonfood raw output pays 1, 2, 3, 5 or 8 Agriculture per cycle according to its producer level. The starter pays too. A bundled Iron/Oil region pays once for both outputs. When supply runs short, the affected territory produces a proportionally reduced amount of each nonfood output, rounded down. |
| Claim a neutral territory | Each claim spends 40 Agriculture, $4,000, 60 Wood and 40 Stone. This is the only Agriculture charge found in the audited purchase-price catalogs. |
| Make Fabric | A Textile Mill uses 10 Agriculture per batch for 8 base Fabric. An L1 mill with adequate inputs, no Logistics bonus and the default stock target of 100 ran 13 cycles, used 130 Agriculture, reached 104 Fabric and paused on the following cycle. The current modeled purchase catalogs and processor recipes contain zero Fabric inputs. A Military Depot field-supplies reward can supply additional Fabric. |
| Trade | Either nation can offer or request Agriculture or Fabric. The offer is escrowed immediately. Acceptance transfers the goods and payment, and cancellation or rejection returns the offer. Trade can cover a national shortage or give producers another customer. It redistributes the server's stock. |

Source locations in the repository are `data/catancraft/maps/season1_river_bridges_v3.json` under resources, and `EconomyBalance`, `EconomyEngine`, `EconomyCatalog`, `BuildingType`, `MonumentSupply44` and the trade methods in `CatanCommands` under Java sources.

## What constrained the modeled routes

The replay matched every recorded purchase time, completed action count, final inventory and shortage count across four starters, two first expansion directions and five purchasing or processor-target policies. Each route bought three claims for 120 Agriculture total. No route entered a territory cycle with unpaid upkeep. At no point did a pending claim have too little Agriculture, alone or alongside another missing input.

| Outcome | Number of runs | Agriculture held |
| --- | ---: | ---: |
| Pickup-first route, at pickup purchase | 8 | 455–472 |
| Completed 26 planned actions, at final action | 32 | 651–1,119 |
| Default-target case stalled at the Town Hall 3 Concrete requirement, at the 200-hour observation limit | 8 | 3,111–3,120 |

The eight pickup-first runs are included among the 32 completed runs. None of these routes builds a Textile Mill. The 200-hour balance includes waiting at the Concrete bottleneck. The model uses normal national starting funds, no grants, four production cycles per active hour and immediate purchases when affordable. It omits trade, travel, construction delays, monument rewards, conflict and player downtime.

## Where the balance reverses

At L5 a starter produces 33 Agriculture and pays 8, leaving 25 per cycle. With three additional L5 producing territories it pays 32 total, leaving **+1 per cycle**. With four it owes 40, giving **−7 per cycle, or −28 per active hour** before any Textile production. The second setup shorted one territory's nonfood output during its first modeled cycle from empty stock. A nation carrying a surplus can absorb this deficit until the stock falls. This comparison holds producer levels fixed to measure capacity and does not claim either setup is reachable on a particular schedule.

For Quin’s fresh-world playtest, the highest-value observation is whether the first starter shows **+4 Agriculture per cycle** at L1, then whether buying a claim spends 40 immediately and adds the new region’s separate upkeep to the next cycle. That separates a one-time purchase from the live forecast players will use for expansion. Any future balance change should be evaluated at both the pickup milestone and the single-starter expansion limit.

## Reproduce

Run the following with Python 3.11 or newer and the matching gameplay checkout. The bundled compressed reference contains all 40 previously recorded routes. The script compares every replay to that reference and writes JSON to standard output.

```sh
python3 audit.py /path/to/catanCraft > replay.json
cmp replay.json results.json
```
