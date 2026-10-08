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
