# Agriculture in CatanCraft

Agriculture goes into the national stockpile. On the Season 1 map, only the four starting cities (A, D, M and P) produce it. Each also produces Wood and Stone, so each spends some of its own harvest on upkeep. A new nation starts with zero materials. [Season 1 map](../src/main/resources/data/catancraft/maps/season1_river_bridges_v3.json) · [National stockpile](../src/main/java/com/quin/catancraft/data/NationData.java)

## What spends Agriculture?

| Use | Cost and effect |
|---|---|
| Raw production upkeep | Each owned territory that produces any nonfood raw materials pays once per cycle, including a city producing two such materials. All owned farms deposit first. If the stockpile runs short, the affected territory spends what is left and produces that same fraction of each nonfood raw output, rounded down. Later territories can receive nothing. [Production order and shortage handling](../src/main/java/com/quin/catancraft/economy/EconomyEngine.java) |
| Textile Mill | Available at Town Hall 2. Each complete batch spends 10 Agriculture for 8 Fabric before local Logistics bonuses. At mill levels 1 through 5, capacity is 1, 2, 3, 5 or 8 batches per cycle. The mill draws from the stockpile after upkeep. It pauses when the nation reaches its Fabric target, initially 100. With no Logistics Center, 13 batches spend 130 Agriculture and reach 104 Fabric. [Factory recipes and levels](../src/main/java/com/quin/catancraft/data/BuildingType.java) · [Factory targets](../src/main/java/com/quin/catancraft/world/BasicConstructionPurchase.java) · [Processor rules](../src/main/java/com/quin/catancraft/economy/EconomyEngine.java) |
| Neutral expansion | Claiming an adjacent neutral territory costs 40 Agriculture, 60 Wood, 40 Stone and $4,000. The initial starting-city assignment is free. [Claim price](../src/main/java/com/quin/catancraft/economy/EconomyCatalog.java) · [Starting city and claim rules](../src/main/java/com/quin/catancraft/command/CatanCommands.java) |

Nations can also offer or request Agriculture in trade. An offer reserves the sender’s stock until the other nation accepts or the deal is declined or canceled. On acceptance, the two nations exchange the agreed assets. A decline or cancellation returns the reserved stock. [Trade settlement](../src/main/java/com/quin/catancraft/command/CatanCommands.java)

## How much can one starter support?

All four starters have a 50% yield. The **producer level** determines harvest and upkeep. The table shows one starter with no other territories, mills or trades. An active hour means four completed 15-minute production cycles at 20 server ticks per second. A stopped server earns no offline production, and a slower server takes longer between payments. [Rates](../src/main/java/com/quin/catancraft/economy/EconomyBalance.java) · [Production clock and rounding](../src/main/java/com/quin/catancraft/economy/EconomyEngine.java)

| Producer level | Harvest per cycle | Starter’s upkeep per cycle | Remaining per cycle | Remaining per active hour |
|---|---:|---:|---:|---:|
| 1 | 5 | 1 | 4 | 16 |
| 2 | 9 | 2 | 7 | 28 |
| 3 | 15 | 3 | 12 | 48 |
| 4 | 23 | 5 | 18 | 72 |
| 5 | 33 | 8 | 25 | 100 |

**Early-game example.** A starter that has reached Town Hall 2 but still has a level-1 producer earns 4 Agriculture per cycle after upkeep. A level-1 Textile Mill needs 10 for one batch. Starting from zero, it first makes Fabric on cycle 3. After five cycles it has made 16 Fabric and the Agriculture balance is back to zero. The same starter without the mill would have saved 20 Agriculture toward its next claim.

Expansion increases the recurring bill as well as the one-time claim cost. A level-5 starter supporting three level-5 specialist territories harvests 33 and spends 32 across its four territories each cycle. That leaves **4 Agriculture per active hour** before milling or trading.

## Stockpiles, rewards and upgrades

Agriculture has no configured storage cap or spoilage. The Fabric target controls when the mill pauses. Current monument rewards can supply Fabric at the Military Depot, but the reward tables contain no Agriculture. The town, producer, factory and wall upgrades use other materials. Agriculture supports those purchases indirectly by keeping raw production running. [Stockpile accounting](../src/main/java/com/quin/catancraft/data/NationData.java) · [Monument rewards](../src/main/java/com/quin/catancraft/world/MonumentSupply44.java) · [Upgrade costs](../src/main/java/com/quin/catancraft/economy/EconomyCatalog.java) · [Wall prices](../src/main/java/com/quin/catancraft/world/SectorPriceCatalog.java)

The repository’s economy simulator reproduced all five hourly balances, both examples, and the unboosted 104-Fabric stopping point using the current map, rate tables and recipes. [Economy simulator](../tools/economy45/simulate.py)
