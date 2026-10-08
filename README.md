# CatanCraft

CatanCraft is a clean Forge 1.20.1 mod for a Catan-inspired Minecraft nation/tycoon/war server.

This repository intentionally does **not** reuse the old OPaC Warfare gameplay code. The first milestone is the economy and territory foundation.

## V0.1 foundation

Implemented on the `v0.1-foundation` branch:

- Nations with leader/member identity
- Shared national treasury
- Shared national resource stockpile
- Fixed server-defined territories with one specialty resource
- Territory ownership
- Persistent world save data
- 15-minute economy cycles
- Agriculture upkeep for raw-resource territories
- Processing buildings with level-based throughput
- Target-stock behavior so factories do not consume inputs forever
- Admin/debug commands for fast balancing tests

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

## Current test commands

Create your nation:

```
/catan nation create Britain
/catan nation info
/catan nation stockpile
```

Create and assign a test territory as an operator:

```
/catan territory create iron_valley iron Iron Valley
/catan territory assign iron_valley Britain
/catan territory info iron_valley
```

Add an industry and resources for testing:

```
/catan debug give agriculture 100
/catan debug give coal 100
/catan debug give iron 200
/catan building add iron_valley steel_mill
/catan debug cycle
/catan nation stockpile
```

Factories default to a target stock of 100 processed units. Example:

```
/catan building target iron_valley 0 250
```

## Next milestones

1. Build a proper nation GUI so ordinary players do not need commands.
2. Add nation membership/invitations and leadership permissions.
3. Add building purchase/upgrade costs instead of admin-only placement.
4. Define map territories with actual world boundaries.
5. Add the three monument event locations.
6. Integrate gun and vehicle mods after the economy loop is proven.
7. Build the new objective-based siege and restoration system.

## Build

Requires Java 17 and Forge 1.20.1 / 47.4.10.
