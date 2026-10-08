# CatanCraft V0.1 Test Checklist

This checklist is for the first economy/territory/monument prototype on Forge 1.20.1.

## 1. Nation creation and membership

Player A:

```
/nation create Britain
```

Expected:
- Nation is created.
- Treasury starts at $12,000.
- Nation dashboard opens.
- Player A is shown as Leader.

Player A invites Player B:

```
/nation invite PlayerB
```

Player B:

```
/nation accept Britain
```

Expected:
- Player B joins Britain.
- `/nation` shows two members.
- Player B is shown as Member.
- Player B cannot spend national resources.

## 2. Create test territories

As an operator:

```
/catan territory create farm agriculture Green Fields
/catan territory create forest wood Black Forest
/catan territory create quarry stone Stone Ridge
/catan territory create iron iron Iron Valley
/catan territory create coal coal Coal Fields
```

Link them:

```
/catan territory link farm forest
/catan territory link forest quarry
/catan territory link quarry iron
/catan territory link iron coal
```

Assign the starting grid:

```
/catan territory assign farm Britain
```

Expected:
- Britain owns Green Fields.
- Forest is shown as available expansion.
- Iron/Coal are not claimable yet because they are not adjacent to owned land.

## 3. Define a world polygon

Stand at each corner of Green Fields and run:

```
/catan territory boundary add farm
```

Use at least 3 points.

Then stand inside it:

```
/catan territory here
```

Expected:
- Current territory reports Green Fields.
- Standing outside the polygon reports no defined territory.

## 4. Production cycle

Run:

```
/catan debug cycle
/nation
```

Expected:
- Green Fields adds Agriculture.
- Nation treasury gains base territory income.

Assign Wood/Stone territories for faster economy testing if needed:

```
/catan territory assign forest Britain
/catan territory assign quarry Britain
/catan debug cycle
```

Expected:
- Agriculture is produced before non-food territory upkeep is charged.
- Forest and quarry then produce Wood/Stone.

## 5. Clickable dashboard

Open:

```
/nation
```

Expected:
- Upgrade and construction actions appear as clickable rows for the leader.
- Clicking an action executes it and refreshes the dashboard.
- Member sees information but no spending buttons.

## 6. Expansion

Give temporary test resources if needed:

```
/catan debug give wood 200
/catan debug give stone 200
/catan debug give agriculture 200
```

Claim the next neutral grid from the dashboard or:

```
/nation claim forest
```

Expected:
- Expansion costs are deducted.
- Forest becomes British territory.
- Quarry becomes the next adjacent claim option.

## 7. City and producer upgrades

Give resources as needed, then use the dashboard.

Expected:
- City upgrade deducts correct cost.
- Producer upgrade increases raw output.
- Building levels cannot exceed City Level.
- Development slot count increases with City Level.

## 8. Industry

At City Level 2:

```
/nation build iron steel_mill
```

Or click the equivalent dashboard action.

Seed raw inputs:

```
/catan debug give iron 200
/catan debug give coal 100
/catan debug cycle
```

Expected:
- Steel Mill creates Steel.
- It stops processing once its target stock is reached.
- Target +/- buttons alter the desired stock level.
- Machine Shop production occurs after Steel production in the same cycle.
- Electronics production occurs after Mechanical Parts.

## 9. Configure three monuments

Stand at each desired monument center.

```
/catan monument create industrial industrial_complex 25 Industrial Complex
/catan monument create depot military_depot 25 Military Depot
/catan monument create refinery refinery 25 Refinery
```

Check:

```
/catan monument list
```

Expected:
- Maximum of 3 monuments.
- Only one becomes active at a time.

## 10. Monument capture

Force a test:

```
/catan monument activate industrial
```

Have one nation stand inside the 25-block radius.

Expected:
- Action bar shows capture progress.
- 120 seconds completes capture.
- Reward enters the national treasury/stockpile.
- Monument deactivates.
- Next monument rotates in after 5 minutes.

## 11. Monument contest

Use two different nations.

Expected:
- One nation begins capture.
- When players from a second nation enter, status becomes CONTESTED.
- Progress pauses while both nations are present.
- If the original nation leaves and the other remains, the new nation begins from 0%.
- Empty monument progress decays.

## Pass criteria

V0.1 is ready for the next milestone when:

- [ ] Nations persist through server restart.
- [ ] Stockpiles/treasury persist.
- [ ] Territory ownership persists.
- [ ] Territory polygons persist and resolve correctly.
- [ ] Production is deterministic.
- [ ] Dashboard buttons work.
- [ ] Expansion adjacency works.
- [ ] Costs cannot be bypassed by normal players.
- [ ] Factory targets work.
- [ ] Monument rotation/capture/rewards work.
- [ ] No crash occurs when players log out during any of the above.
