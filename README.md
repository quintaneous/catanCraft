# CatanCraft

CatanCraft is a clean Forge 1.20.1 mod for a Catan-inspired Minecraft nation/tycoon/war server.

This repository intentionally does **not** reuse the old OPaC Warfare gameplay code.

## V0.1 foundation

Current work on the `v0.1-foundation` branch:

- Nations with leader/member identity
- Nation invitations, joining, and leaving
- Shared national treasury
- Shared national resource stockpile
- Fixed server-defined territories with one specialty resource
- Server-defined territory adjacency graph
- Paid claiming of adjacent neutral territories
- Territory ownership
- Persistent world save data
- 15-minute economy cycles
- Agriculture upkeep for raw-resource territories
- Processing buildings with level-based throughput
- Target-stock behavior so factories do not consume inputs forever
- In-game nation dashboard opened with `/nation`
- Admin/debug commands for fast balancing tests
- GitHub Actions build check

### Raw resources

- Wood
- Stone
- Agriculture
- Iron
- Coal
- Oil
- Copper

### Processed resources

- Steel
- Concrete
- Fabric
- Fuel
- Mechanical Parts
- Electronics
- Explosives

## Player commands

Create a nation and open its dashboard:

```
/nation create Britain
/nation
```

Invite and join players:

```
/nation invite PlayerName
/nation accept Britain
/nation leave
```

Text fallbacks:

```
/nation info
/nation stockpile
```

## Admin test commands

Create and assign a test territory:

```
/catan territory create iron_valley iron Iron Valley
/catan territory assign iron_valley Britain
/catan territory create coal_fields coal Coal Fields
/catan territory link iron_valley coal_fields
/catan territory info iron_valley
```

Add an industry and resources for testing:

```
/catan debug give agriculture 100
/catan debug give coal 100
/catan debug give iron 200
/catan building add iron_valley steel_mill
/catan debug cycle
/nation
```

Once the nation owns a bordering territory, neutral expansion is player-driven:

```
/nation claim coal_fields
```

Factories default to a target stock of 100 processed units:

```
/catan building target iron_valley 0 250
```

## Next milestones

1. Add paid building construction and upgrades.
2. Move building management into clickable GUI screens.
3. Define map territories with actual world boundaries.
4. Add the three monument event locations.
5. Integrate gun and vehicle mods after the economy loop is proven.
6. Build the new objective-based siege and restoration system.

## Build

Requires Java 17 and Forge 1.20.1 / 47.4.10.


## Defining territory world boundaries

Territories use server-defined X/Z polygons. Stand on each corner/vertex in order and run:

```
/catan territory boundary add iron_valley
```

Add at least three points. The polygon closes automatically between the last point and the first point.

Useful admin commands:

```
/catan territory here
/catan territory boundary clear iron_valley
```

The same territory lookup is intended to power later building placement, monuments, sieges, and capture rules.


## Monument testing

V0.1 supports up to three configured monuments, with only one active at a time.

Stand at the center of each site and create it:

```
/catan monument create industrial industrial_complex 25 Industrial Complex
/catan monument create depot military_depot 25 Military Depot
/catan monument create refinery refinery 25 Refinery
```

Useful commands:

```
/catan monument list
/catan monument activate industrial
```

Rules in the current prototype:

- One monument is active globally.
- A single nation must hold the radius for 120 seconds.
- Two or more nations in the zone make it contested and pause progress.
- Empty zones lose capture progress.
- After a capture there is a 5-minute gap before the next monument rotates in.
- Capture status is shown in the action bar to players inside the zone.
- Rewards currently go directly to the national stockpile/treasury; physical cargo delivery can replace this later.


## Authoritative map definition

The real CatanCraft world is a locked strategy map. Terrain, roads, rivers, cities,
resource sites, monuments, building plots, and defense positions are authored before
the season begins.

On first server start CatanCraft creates:

```
config/catancraft/map.json
```

The file is intentionally empty until the real map coordinates are ready. A complete
example lives at:

```
docs/map-definition.example.json
```

Each map-defined territory can provide:

- X/Z polygon boundary
- specialty resource
- fixed neighboring territories
- city-center anchor
- Town Hall anchor
- resource-site anchor
- fixed building plots
- fixed defense anchors
- siege damage region
- restoration region

Map definitions are authoritative. Ownership, City Level, Producer Level, purchased
buildings, building levels, and nation data remain world-save state.

Useful operator commands:

```
/catan map reload
/catan map status
/catan map here
/catan map territory iron_valley
/catan map anchor iron_valley town_hall
/catan map anchor iron_valley plot_1
```

Legacy `/catan territory create/link/boundary` commands remain available for throwaway
test territories, but cannot modify geometry or adjacency for territories defined in
`map.json`.

### Fixed building plots

Purchased buildings in map-defined territories are permanently assigned to the first
available configured plot. The plot ID is persisted with the building and shown in
`/nation`.

This lets future schematic placement, upgrades, siege damage, and repair use the exact
same physical location every time.

### City restoration

A territory's `siegeRegion` is the only city area that may eventually be allowed to
take wartime block damage. It must be fully contained inside the territory's
`restorationRegion`; invalid definitions are rejected.

The restoration service captures the city's current block state, excluding entities:

```
/catan map snapshot iron_valley
```

For testing, the saved state can be reapplied with:

```
/catan map restore iron_valley
```

Later siege code will call these automatically at siege start/end. This means a
developed city returns to its actual pre-war state rather than an old baseline layout.

Snapshots are currently capped at 4,000,000 blocks per city region to prevent an
accidentally huge definition from freezing the server.

## Locked-world rules

Normal players cannot:

- break blocks
- place blocks
- place liquids with buckets
- alter terrain through explosions

Explosion entity damage is left alone, but block destruction is suppressed globally.
When the siege system is added, only blocks inside an active city's registered
`siegeRegion` will be eligible for temporary destruction.

Players will not manually place trenches, walls, or sandbags. Purchased defenses will
be generated at predefined defense anchors.

## Nation trading

Leaders can create nation-to-nation trade proposals:

```
/nation trade propose "Germany" steel 100 money 4000
/nation trade propose "Germany" money 5000 coal 150
/nation trade propose "Germany" oil 100 electronics 20
```

The offering side is moved into escrow immediately. The receiving nation can Accept or
Decline from `/nation`; the sender can Cancel. Declined/canceled proposals refund the
escrow, and acceptance only succeeds if the receiving nation still has the requested
assets.


## Season one: River & Bridges V3 integration

The mod now ships with the current River & Bridges V3 world definition rather than
an empty placeholder map.

On an empty CatanCraft config, the first server start installs the bundled map to:

```
config/catancraft/map.json
```

If an older test map is already in that file, an operator can explicitly replace it:

```
/catan map installseason1
```

The bundled definition matches the current 16-territory / four-start layout:
A/D/M/P are the four prebuilt starting cities, the other twelve sites begin neutral,
the central oval court is a public objective overlay, and the four V3 bridges are
registered as permanent infrastructure.

### Starting-city production

Starting cities produce all three basic resources:

- Wood
- Stone
- Agriculture

They currently run at **75% of the per-resource output** of a dedicated territory.
At Producer L1 this is 8 of each resource per 15-minute cycle before the normal
territory Agriculture upkeep is applied to the non-food outputs. A dedicated L1
Wood or Stone territory produces 10 of its specialty per cycle.

This percentage is map data, not hard-coded balance, so it can be tuned without
rewriting the economy engine.

A new nation with no territory sees the available prebuilt starting cities directly
in `/nation` and can select one with the GUI or:

```
/nation start <1-4>
```

When a starting city is selected, the bundled farm is placed into its reserved
`plot_5` so Agriculture production has a physical city asset. The existing
resource-yard plot and farm plot are reserved and cannot be consumed by normal
industrial construction.

### Neutral city activation

The twelve neutral territories remain physically prepared but empty in the V3 save.
When a nation purchases an adjacent neutral territory, CatanCraft now pastes the
accepted `starter_settlement_v2` schematic at that site's predefined map anchor
before ownership is committed. If placement fails, the claim is canceled and the
nation is not charged.

### Town Hall visuals

City upgrades now drive the accepted physical Town Hall assets:

- City I -> II pastes `thall2`
- City II -> III pastes `thall3`
- City IV/V remain economy levels for now and retain the current TH3 visual until
  later visual tiers are authored.

The schematic loader uses each asset's stored anchor offset and the map-defined
0/180-degree orientation, so the southern cities use the same assets without manual
WorldEdit rotation.

### Management lecterns

The existing management lectern in each city is registered as a physical management
terminal. A nation member can right-click the lectern in a city their nation owns to
open the same nation dashboard as `/nation`. Neutral or foreign-city lecterns do not
grant management access.
