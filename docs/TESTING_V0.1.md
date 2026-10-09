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


# V0.1.1 Real-map integration tests

These tests are for the new locked-world and map-definition layer.

## 12. Load map definition

Copy the real map coordinates into:

```
config/catancraft/map.json
```

Then:

```
/catan map reload
/catan map status
```

Expected:
- Map reload succeeds.
- Territory and monument counts match the file.
- Invalid neighbor IDs are rejected.
- Duplicate/blank building plot IDs are rejected.
- A siege region outside its restoration region is rejected.

## 13. World-coordinate lookup

Move through several territories and run:

```
/catan map here
```

Expected:
- Correct territory is reported at several points in each polygon.
- Outside-map positions report no map-defined territory.
- Siege/restoration-region flags only become true inside those city regions.

## 14. Anchor verification

For representative territories:

```
/catan map territory iron_valley
/catan map anchor iron_valley city
/catan map anchor iron_valley town_hall
/catan map anchor iron_valley resource_site
/catan map anchor iron_valley plot_1
```

Expected:
- Coordinates match the authored map.
- Town Hall faces the intended direction.
- Building plots line up with the prepared city footprints.
- Defense anchors line up with gates/walls/defensive positions.

## 15. Fixed building plots

Build multiple industries in one map-defined territory.

Expected:
- Each building receives a unique plot ID.
- Plot ID appears in `/nation`.
- Reloading the world preserves plot assignments.
- An old V0.1 save without plot IDs is migrated onto plots in deterministic order.
- Construction fails when no physical plot remains.

## 16. Locked-world protection

As a normal player, attempt to:
- break terrain
- break a city block
- place a block
- empty a water/lava bucket
- explode terrain

Expected:
- No blocks are changed by any attempt.
- Explosions can still affect entities normally.
- Administrative map commands still work.

## 17. City snapshot and restore

For one small test city with a configured `restorationRegion`:

```
/catan map snapshot iron_valley
```

As an administrator, deliberately alter several blocks inside the region, then:

```
/catan map restore iron_valley
```

Expected:
- Blocks return to the captured state.
- Air spaces are restored.
- Block entities survive restoration correctly.
- Players/mobs/vehicles are not duplicated by restoration.

Do this on a small city region first before using full production-sized bounds.

## 18. Trade proposal flow

Requires two nations.

Expected:
- Sender creates a proposal.
- Offered assets immediately leave usable stock and enter escrow.
- Recipient sees Accept/Decline controls in `/nation`.
- Decline refunds sender.
- Cancel refunds sender.
- Accept transfers both sides atomically.
- Acceptance fails safely if recipient no longer has the requested assets.
- Proposal survives server restart while pending.


# V0.1.2 River & Bridges V3 integration tests

## 19. Install the current map definition

With the V3 world loaded:

```
/catan map installseason1
/catan map status
```

Expected:
- Map ID is `season1_river_bridges_v3`.
- 16 territories load.
- 3 monuments load.
- 1 public objective zone loads.
- 4 bridge infrastructure points load.

## 20. Select a prebuilt start

Create a fresh nation and open `/nation`.

Expected:
- Four starting-city slots are listed if all are neutral.
- A/D/M/P correspond to the four prebuilt V3 settlements.
- Selecting one assigns exactly one starting territory.
- The same nation cannot take a second start.
- Another nation cannot take an occupied start.

## 21. Starter mixed production

At Producer L1, run one debug cycle.

Expected starter output before processor activity:
- +5 Wood
- +5 Stone
- +5 Agriculture generated
- one normal Agriculture upkeep charge supports the non-food raw outputs

Dedicated L1 Wood/Stone territories should still produce +10 of their specialty,
so a starter remains flexible but weaker at each individual resource.

## 22. Starter farm placement

After choosing a starting city:

Expected:
- The farm appears at reserved `plot_5`.
- A/D are north-facing.
- M/P are rotated 180 degrees.
- Farm doors/path line up with the prepared settlement.
- `plot_5` is not offered for normal industrial construction.

## 23. Claim a neutral prepared site

From an owned territory, claim an adjacent neutral V3 territory.

Expected:
- Claim cost is checked before activation.
- Accepted starter settlement appears on the neutral 193 x 193 pad.
- Orientation matches the site's north/south map orientation.
- Territory ownership changes only after placement succeeds.
- A placement error does not charge the nation.

## 24. Physical City II / III upgrade

At a claimed or starting city with sufficient resources:

- Upgrade City I -> II.
- Inspect Town Hall.
- Upgrade City II -> III.
- Inspect Town Hall again.

Expected:
- TH2 and TH3 replace the prior Town Hall automatically.
- Air in the replacement envelope clears old-tier blocks.
- Roads, houses and development plots outside the hall envelope remain unchanged.
- Southern cities rotate correctly.
- The management lectern remains at its registered world coordinate.

## 25. Management lectern

Right-click the registered city lectern.

Expected:
- Owner nation member opens `/nation`.
- Neutral city terminal reports inactive.
- Foreign nation member is told which nation controls the city.
- No normal block editing is enabled.


## 26. Verify exact V3 world

With `CatanCraft_River_Bridges_V3` loaded:

```
/catan map verify
```

Expected:
- Verification passes.
- All 16 prepared city surfaces are found.
- A/D/M/P management lecterns exist at their registered anchors.
- All four bridge centers have solid deck blocks.
- The central court is solid at Y=80.

Then compare starter and dedicated production:

```
/catan debug production a
/catan debug production b
/catan debug production e
```

Expected at Producer L1:
- A shows Wood 5, Stone 5, Agriculture 5 per 15 minutes at 50% yield.
- B shows Wood 10 per 15 minutes at 100% yield.
- E shows Stone 10 per 15 minutes at 100% yield.
- A pays one Agriculture upkeep charge for its combined non-food outputs, not one
  charge per resource.


## 27. Deterministic physical asset integration

Install the current schematic bundle as:

```
config/catancraft/schematics_bundle.zip
```

Then:

```
/catan map reloadassets
/catan map assetstatus
```

Expected:
- Every current handoff asset validates.
- Wrong dimensions or stored offsets are rejected before any world blocks change.

Fresh starter-city test:
1. Create nation.
2. Select one of A/D/M/P.
3. Inspect plot_2, plot_5 and plot_6.

Expected:
- Quarry is at plot_2.
- Farm is at plot_5.
- Lumberyard is at plot_6.
- Starter production preview reports 5 Wood, 5 Stone, 5 Agriculture per 15m at L1.
- The prebuilt settlement itself is not repasted.

Fresh neutral claim:
- Claim a Wood territory: settlement + TH1 + Lumberyard appear.
- Claim a Stone territory: settlement + TH1 + Quarry appear.
- Claim F/K: settlement + TH1 + Iron/Oil rear strategic site appear.
- Claim G/J: settlement + TH1 + Coal/Copper rear strategic site appear.

Purchased factory test:
- Vehicle Factory always uses plot_1.
- Weapons Factory always uses plot_3.
- Steel Mill always uses plot_4.
- Purchase fails safely if its fixed physical plot is already occupied.
- The nation is not charged if physical placement validation fails.

Town Hall:
- City II replaces only the Town Hall with TH2.
- City III replaces only the Town Hall with TH3.
- Building level upgrades do not repaste Tier-1 factory geometry.

Monuments:

```
/catan map placemonuments
```

Expected:
- All three current central monument assets are placed once.
- Placement state persists after restart.


## 28. Visual Polish V3 refresh and processor visibility

Install the Visual Polish V3 `schematics_bundle.zip`, then run:

```
/catan map installseason1
/catan map reloadassets
/catan map assetstatus
```

Expected:
- All current runtime assets validate, including `central_district_base`.
- Monument dimensions/offsets validate against the enlarged V3 contracts.
- The season map reports the new monument capture coordinates/radius.

On an existing integration-test save:

```
/catan map refreshassets
```

Expected:
- The 301 x 301 central district base is installed first.
- The enlarged Industrial Complex, Military Depot and Refinery are placed at their
  new anchors.
- Current raw producer assets, physical purchased factories and the current Town Hall
  tier are refreshed in owned cities.
- Complete starter settlement/city-base schematics are NOT blindly repasted.
- Ownership, city levels, building levels, stockpiles and plot assignments do not reset.

Processor debug:

```
/catan debug production a
```

For a territory containing a Steel Mill, expected output includes the processor.
If current Steel is at/above its target it should report `PAUSED AT TARGET`.
After setting Steel below target and providing Iron + Coal it should report `READY`
with the expected Steel output for the next 15-minute cycle.

Nation dashboard:
- Main screen opens in a compact state.
- Resources, Trade, Active Monument, Territories and Available Expansion are
  individually collapsible.
- Expanding Territories reveals territory rows.
- Each territory row can independently expand/collapse its detailed controls.
- Collapsing a section actually removes its child rows from layout/scroll height.
- Dashboard remains functional with several owned territories/buildings.
