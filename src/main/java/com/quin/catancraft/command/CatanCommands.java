package com.quin.catancraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.quin.catancraft.data.BuildingInstance;
import com.quin.catancraft.data.BuildingType;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.data.NationData;
import com.quin.catancraft.data.MonumentData;
import com.quin.catancraft.data.MonumentType;
import com.quin.catancraft.data.ResourceType;
import com.quin.catancraft.data.TerritoryData;
import com.quin.catancraft.data.TradeProposal;
import com.quin.catancraft.economy.EconomyCatalog;
import com.quin.catancraft.economy.EconomyCost;
import com.quin.catancraft.economy.EconomyEngine;
import com.quin.catancraft.monument.MonumentManager;
import com.quin.catancraft.map.MapAnchor;
import com.quin.catancraft.map.MapDefinitionManager;
import com.quin.catancraft.map.MapZoneDefinition;
import com.quin.catancraft.map.TerritoryDefinition;
import com.quin.catancraft.ui.NationDashboard;
import com.quin.catancraft.world.CityRestorationService;
import com.quin.catancraft.world.SchematicPlacementService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Comparator;

public final class CatanCommands {
    private CatanCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(nationNode("nation"));

        LiteralArgumentBuilder<CommandSourceStack> catan = Commands.literal("catan");
        catan.then(nationNode("nation"));
        catan.then(territoryAdminNode());
        catan.then(buildingAdminNode());
        catan.then(monumentAdminNode());
        catan.then(mapAdminNode());
        catan.then(debugNode());
        dispatcher.register(catan);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> nationNode(String literal) {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(literal)
                .executes(ctx -> openDashboard(ctx.getSource()));

        node.then(Commands.literal("gui")
                .executes(ctx -> openDashboard(ctx.getSource())));

        node.then(Commands.literal("create")
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> createNation(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "name")))));

        node.then(Commands.literal("invite")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> invitePlayer(
                                ctx.getSource(),
                                EntityArgument.getPlayer(ctx, "player")))));

        node.then(Commands.literal("accept")
                .then(Commands.argument("nation", StringArgumentType.greedyString())
                        .executes(ctx -> acceptInvite(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "nation")))));

        node.then(Commands.literal("leave")
                .executes(ctx -> leaveNation(ctx.getSource())));

        node.then(Commands.literal("info")
                .executes(ctx -> nationInfo(ctx.getSource())));

        node.then(Commands.literal("stockpile")
                .executes(ctx -> stockpile(ctx.getSource())));

        node.then(Commands.literal("start")
                .then(Commands.argument("slot", IntegerArgumentType.integer(1, 4))
                        .executes(ctx -> chooseStartingCity(
                                ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "slot")))));

        node.then(Commands.literal("claim")
                .then(Commands.argument("territory", StringArgumentType.word())
                        .executes(ctx -> claimNeutralTerritory(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "territory")))));

        node.then(Commands.literal("build")
                .then(Commands.argument("territory", StringArgumentType.word())
                        .then(Commands.argument("type", StringArgumentType.word())
                                .executes(ctx -> buildIndustry(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "territory"),
                                        StringArgumentType.getString(ctx, "type"))))));

        LiteralArgumentBuilder<CommandSourceStack> upgrade = Commands.literal("upgrade");
        upgrade.then(Commands.literal("city")
                .then(Commands.argument("territory", StringArgumentType.word())
                        .executes(ctx -> upgradeCity(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "territory")))));
        upgrade.then(Commands.literal("producer")
                .then(Commands.argument("territory", StringArgumentType.word())
                        .executes(ctx -> upgradeProducer(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "territory")))));
        node.then(upgrade);

        LiteralArgumentBuilder<CommandSourceStack> building = Commands.literal("building");
        building.then(Commands.literal("upgrade")
                .then(Commands.argument("territory", StringArgumentType.word())
                        .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                .executes(ctx -> upgradeBuilding(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "territory"),
                                        IntegerArgumentType.getInteger(ctx, "index"))))));
        building.then(Commands.literal("target")
                .then(Commands.argument("territory", StringArgumentType.word())
                        .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                        .executes(ctx -> setNationBuildingTarget(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "territory"),
                                                IntegerArgumentType.getInteger(ctx, "index"),
                                                LongArgumentType.getLong(ctx, "amount")))))));
        node.then(building);

        LiteralArgumentBuilder<CommandSourceStack> trade = Commands.literal("trade");
        trade.then(Commands.literal("propose")
                .then(Commands.argument("nation", StringArgumentType.string())
                        .then(Commands.argument("offerAsset", StringArgumentType.word())
                                .then(Commands.argument("offerAmount", LongArgumentType.longArg(1))
                                        .then(Commands.argument("requestAsset", StringArgumentType.word())
                                                .then(Commands.argument("requestAmount", LongArgumentType.longArg(1))
                                                        .executes(ctx -> proposeTrade(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "nation"),
                                                                StringArgumentType.getString(ctx, "offerAsset"),
                                                                LongArgumentType.getLong(ctx, "offerAmount"),
                                                                StringArgumentType.getString(ctx, "requestAsset"),
                                                                LongArgumentType.getLong(ctx, "requestAmount")))))))));

        trade.then(Commands.literal("accept")
                .then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> acceptTrade(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))));

        trade.then(Commands.literal("decline")
                .then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> declineTrade(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))));

        trade.then(Commands.literal("cancel")
                .then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> cancelTrade(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))));

        node.then(trade);

        return node;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> territoryAdminNode() {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal("territory");

        node.then(Commands.literal("create")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("resource", StringArgumentType.word())
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> createTerritory(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "id"),
                                                StringArgumentType.getString(ctx, "resource"),
                                                StringArgumentType.getString(ctx, "name")))))));

        node.then(Commands.literal("assign")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("territory", StringArgumentType.word())
                        .then(Commands.argument("nation", StringArgumentType.greedyString())
                                .executes(ctx -> assignTerritory(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "territory"),
                                        StringArgumentType.getString(ctx, "nation"))))));

        node.then(Commands.literal("link")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("first", StringArgumentType.word())
                        .then(Commands.argument("second", StringArgumentType.word())
                                .executes(ctx -> linkTerritories(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "first"),
                                        StringArgumentType.getString(ctx, "second"))))));

        LiteralArgumentBuilder<CommandSourceStack> boundary = Commands.literal("boundary")
                .requires(source -> source.hasPermission(2));

        boundary.then(Commands.literal("add")
                .then(Commands.argument("territory", StringArgumentType.word())
                        .executes(ctx -> addBoundaryPoint(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "territory")))));

        boundary.then(Commands.literal("clear")
                .then(Commands.argument("territory", StringArgumentType.word())
                        .executes(ctx -> clearBoundary(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "territory")))));

        node.then(boundary);

        node.then(Commands.literal("here")
                .executes(ctx -> territoryHere(ctx.getSource())));

        node.then(Commands.literal("info")
                .then(Commands.argument("territory", StringArgumentType.word())
                        .executes(ctx -> territoryInfo(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "territory")))));

        return node;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildingAdminNode() {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal("building")
                .requires(source -> source.hasPermission(2));

        node.then(Commands.literal("add")
                .then(Commands.argument("territory", StringArgumentType.word())
                        .then(Commands.argument("type", StringArgumentType.word())
                                .executes(ctx -> adminAddBuilding(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "territory"),
                                        StringArgumentType.getString(ctx, "type"))))));

        node.then(Commands.literal("target")
                .then(Commands.argument("territory", StringArgumentType.word())
                        .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                        .executes(ctx -> adminSetBuildingTarget(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "territory"),
                                                IntegerArgumentType.getInteger(ctx, "index"),
                                                LongArgumentType.getLong(ctx, "amount")))))));

        return node;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> monumentAdminNode() {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal("monument");

        node.then(Commands.literal("create")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("type", StringArgumentType.word())
                                .then(Commands.argument("radius", IntegerArgumentType.integer(5, 100))
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> createMonument(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "id"),
                                                        StringArgumentType.getString(ctx, "type"),
                                                        IntegerArgumentType.getInteger(ctx, "radius"),
                                                        StringArgumentType.getString(ctx, "name"))))))));

        node.then(Commands.literal("activate")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> activateMonument(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))));

        node.then(Commands.literal("list")
                .executes(ctx -> listMonuments(ctx.getSource())));

        return node;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> mapAdminNode() {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal("map")
                .requires(source -> source.hasPermission(2));

        node.then(Commands.literal("reload")
                .executes(ctx -> reloadMapDefinition(ctx.getSource())));

        node.then(Commands.literal("installseason1")
                .executes(ctx -> installSeasonOneMap(ctx.getSource())));

        node.then(Commands.literal("status")
                .executes(ctx -> mapStatus(ctx.getSource())));

        node.then(Commands.literal("starts")
                .executes(ctx -> mapStarts(ctx.getSource())));

        node.then(Commands.literal("assignstart")
                .then(Commands.argument("slot", IntegerArgumentType.integer(1))
                        .then(Commands.argument("nation", StringArgumentType.greedyString())
                                .executes(ctx -> assignStartTerritory(
                                        ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "slot"),
                                        StringArgumentType.getString(ctx, "nation"))))));

        node.then(Commands.literal("here")
                .executes(ctx -> mapHere(ctx.getSource())));

        node.then(Commands.literal("territory")
                .then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> mapTerritoryInfo(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))));

        node.then(Commands.literal("anchor")
                .then(Commands.argument("territory", StringArgumentType.word())
                        .then(Commands.argument("anchor", StringArgumentType.word())
                                .executes(ctx -> mapAnchorInfo(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "territory"),
                                        StringArgumentType.getString(ctx, "anchor"))))));

        node.then(Commands.literal("snapshot")
                .then(Commands.argument("territory", StringArgumentType.word())
                        .executes(ctx -> snapshotCity(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "territory")))));

        node.then(Commands.literal("restore")
                .then(Commands.argument("territory", StringArgumentType.word())
                        .executes(ctx -> restoreCity(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "territory")))));

        return node;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> debugNode() {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal("debug")
                .requires(source -> source.hasPermission(2));

        node.then(Commands.literal("cycle")
                .executes(ctx -> {
                    EconomyEngine.runCycle(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(
                            () -> Component.literal("Production cycle executed."), true);
                    return 1;
                }));

        node.then(Commands.literal("give")
                .then(Commands.argument("resource", StringArgumentType.word())
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(ctx -> debugGive(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "resource"),
                                        LongArgumentType.getLong(ctx, "amount"))))));

        node.then(Commands.literal("set")
                .then(Commands.argument("resource", StringArgumentType.word())
                        .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                .executes(ctx -> debugSetResource(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "resource"),
                                        LongArgumentType.getLong(ctx, "amount"))))));

        node.then(Commands.literal("money")
                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                        .executes(ctx -> debugMoney(
                                ctx.getSource(),
                                LongArgumentType.getLong(ctx, "amount")))));

        node.then(Commands.literal("cycles")
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                        .executes(ctx -> debugCycles(
                                ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "count")))));

        return node;
    }

    private static int installSeasonOneMap(CommandSourceStack source) {
        try {
            MapDefinitionManager.LoadResult result =
                    MapDefinitionManager.installBundledSeasonOne(source.getServer());
            source.sendSuccess(() -> Component.literal(
                    "Installed bundled River & Bridges V3 season map: " +
                            result.territories() + " territories, " +
                            result.monuments() + " monuments."), true);
            source.sendSuccess(() -> Component.literal(
                    "This overwrote config/catancraft/map.json with the map bundled in the mod."),
                    false);
            return 1;
        } catch (Exception ex) {
            source.sendFailure(Component.literal(
                    "Season map install failed: " + ex.getMessage()));
            return 0;
        }
    }

    private static int reloadMapDefinition(CommandSourceStack source) {
        try {
            MapDefinitionManager.LoadResult result =
                    MapDefinitionManager.reload(source.getServer());
            source.sendSuccess(() -> Component.literal(
                    "Reloaded CatanCraft map: " + result.territories() +
                            " territories, " + result.monuments() +
                            " monuments. Warnings: " + result.warnings().size()), true);
            for (String warning : result.warnings()) {
                source.sendSuccess(() -> Component.literal(
                        "[Map warning] " + warning), false);
            }
            return 1;
        } catch (Exception ex) {
            source.sendFailure(Component.literal(
                    "Map reload failed: " + ex.getMessage()));
            return 0;
        }
    }

    private static int mapStarts(CommandSourceStack source) {
        CatanSavedData data = CatanSavedData.get(source.getServer());

        source.sendSuccess(() -> Component.literal("=== CatanCraft Starting Cities ==="), false);

        MapDefinitionManager.territories().stream()
                .filter(definition -> definition.startSlot() > 0)
                .sorted(Comparator.comparingInt(TerritoryDefinition::startSlot))
                .forEach(definition -> {
                    TerritoryData territory = data.territory(definition.id());
                    String ownerName = "Neutral";
                    if (territory != null && territory.ownerNationId() != null) {
                        NationData owner = data.nation(territory.ownerNationId());
                        ownerName = owner == null ? "Unknown" : owner.name();
                    }

                    String finalOwnerName = ownerName;
                    source.sendSuccess(() -> Component.literal(
                            "Slot " + definition.startSlot() + " • " +
                                    definition.name() + " [" + definition.id() +
                                    "] • " + finalOwnerName), false);
                });
        return 1;
    }

    private static int assignStartTerritory(
            CommandSourceStack source,
            int slot,
            String nationName
    ) {
        CatanSavedData data = CatanSavedData.get(source.getServer());
        NationData nation = data.nationByName(nationName.trim());
        if (nation == null) {
            source.sendFailure(Component.literal("Unknown nation: " + nationName));
            return 0;
        }

        TerritoryDefinition definition = MapDefinitionManager.territories().stream()
                .filter(value -> value.startSlot() == slot)
                .findFirst()
                .orElse(null);
        if (definition == null) {
            source.sendFailure(Component.literal("No map start slot " + slot + "."));
            return 0;
        }

        TerritoryData territory = data.territory(definition.id());
        if (territory == null) {
            source.sendFailure(Component.literal(
                    "Map territory " + definition.id() + " is not synchronized."));
            return 0;
        }

        if (territory.ownerNationId() != null
                && !territory.ownerNationId().equals(nation.id())) {
            NationData currentOwner = data.nation(territory.ownerNationId());
            source.sendFailure(Component.literal(
                    definition.name() + " is already assigned to " +
                            (currentOwner == null ? "another nation" : currentOwner.name()) + "."));
            return 0;
        }

        boolean alreadyHasStart = MapDefinitionManager.territories().stream()
                .filter(value -> value.startSlot() > 0)
                .map(value -> data.territory(value.id()))
                .anyMatch(value -> value != null
                        && nation.id().equals(value.ownerNationId()));

        if (alreadyHasStart && !nation.id().equals(territory.ownerNationId())) {
            source.sendFailure(Component.literal(
                    nation.name() + " already owns a starting city."));
            return 0;
        }

        MapAnchor farmAnchor = definition.anchor("plot_5");
        if (farmAnchor != null
                && definition.resources().contains(ResourceType.AGRICULTURE)) {
            SchematicPlacementService.Result farmPlacement =
                    SchematicPlacementService.place(
                            source.getServer(),
                            definition.dimension(),
                            "farm",
                            farmAnchor,
                            false
                    );
            if (!farmPlacement.success()) {
                source.sendFailure(Component.literal(
                        "Starting-city farm placement failed; assignment canceled. " +
                                farmPlacement.message()));
                return 0;
            }
        }

        territory.setOwnerNationId(nation.id());
        data.setDirty();

        source.sendSuccess(() -> Component.literal(
                "Assigned start slot " + slot + " (" + definition.name() +
                        ") to " + nation.name() + "."), true);
        return 1;
    }

    private static int mapStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "CatanCraft map: " + MapDefinitionManager.mapId()), false);
        source.sendSuccess(() -> Component.literal(
                "Definition: " + MapDefinitionManager.path()), false);
        source.sendSuccess(() -> Component.literal(
                MapDefinitionManager.territories().size() + " territories • " +
                        MapDefinitionManager.monuments().size() + " monuments • " +
                        MapDefinitionManager.publicZones().size() + " public zones • " +
                        MapDefinitionManager.infrastructure().size() +
                        " infrastructure points"), false);
        return 1;
    }

    private static int mapHere(CommandSourceStack source)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TerritoryDefinition definition = MapDefinitionManager.territoryAt(
                player.serverLevel(),
                player.blockPosition()
        );
        MapZoneDefinition zone = MapDefinitionManager.publicZoneAt(
                player.serverLevel(),
                player.blockPosition()
        );

        if (definition == null) {
            if (zone != null) {
                source.sendSuccess(() -> Component.literal(
                        "Public zone: " + zone.name() + " [" + zone.id() +
                                "] • " + zone.type()), false);
                return 1;
            }
            source.sendSuccess(() -> Component.literal(
                    "This position is not inside a map-defined territory."), false);
            return 0;
        }

        boolean siege = definition.siegeRegion() != null
                && definition.siegeRegion().contains(player.blockPosition());
        boolean restoration = definition.restorationRegion() != null
                && definition.restorationRegion().contains(player.blockPosition());
        String zoneText = zone == null
                ? ""
                : " • PUBLIC: " + zone.name();

        source.sendSuccess(() -> Component.literal(
                definition.name() + " [" + definition.id() + "] • " +
                        resourceListText(definition.resources()) +
                        zoneText +
                        " • siegeRegion=" + siege +
                        " • restorationRegion=" + restoration), false);
        return 1;
    }

    private static int mapTerritoryInfo(
            CommandSourceStack source,
            String territoryId
    ) {
        TerritoryDefinition definition = MapDefinitionManager.territory(territoryId);
        if (definition == null) {
            source.sendFailure(Component.literal(
                    "No map-defined territory: " + territoryId));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                definition.name() + " [" + definition.id() + "] • " +
                        resourceListText(definition.resources()) + " • " +
                        definition.dimension() +
                        (definition.startSlot() > 0
                                ? " • Start slot " + definition.startSlot()
                                : "")), false);

        String city = definition.cityCenter() == null
                ? "not set"
                : definition.cityCenter().describe();
        String hall = definition.townHall() == null
                ? "not set"
                : definition.townHall().describe();
        String resource = definition.resourceSite() == null
                ? "not set"
                : definition.resourceSite().describe();

        source.sendSuccess(() -> Component.literal(
                "City center: " + city), false);
        source.sendSuccess(() -> Component.literal(
                "Town Hall: " + hall), false);
        source.sendSuccess(() -> Component.literal(
                "Resource site: " + resource), false);
        source.sendSuccess(() -> Component.literal(
                "Building plots: " + definition.buildingPlots().size() +
                        " • Defense anchors: " + definition.defenseAnchors().size() +
                        " • Neighbors: " + String.join(", ", definition.neighbors())), false);

        if (definition.siegeRegion() != null) {
            source.sendSuccess(() -> Component.literal(
                    "Siege region: " + definition.siegeRegion().describe()), false);
        }
        if (definition.restorationRegion() != null) {
            source.sendSuccess(() -> Component.literal(
                    "Restoration region: " +
                            definition.restorationRegion().describe()), false);
        }
        return 1;
    }

    private static int mapAnchorInfo(
            CommandSourceStack source,
            String territoryId,
            String anchorId
    ) {
        TerritoryDefinition definition = MapDefinitionManager.territory(territoryId);
        if (definition == null) {
            source.sendFailure(Component.literal(
                    "No map-defined territory: " + territoryId));
            return 0;
        }

        MapAnchor anchor = definition.anchor(anchorId);
        if (anchor == null) {
            source.sendFailure(Component.literal(
                    "Unknown anchor '" + anchorId + "' in " + definition.name() + "."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                definition.name() + " • " + anchorId + " • " + anchor.describe()), false);
        return 1;
    }

    private static int snapshotCity(
            CommandSourceStack source,
            String territoryId
    ) {
        TerritoryDefinition territory = MapDefinitionManager.territory(territoryId);
        if (territory == null) {
            source.sendFailure(Component.literal(
                    "No map-defined territory: " + territoryId));
            return 0;
        }

        CityRestorationService.Result result =
                CityRestorationService.capture(source.getServer(), territory);
        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(result.message()), true);
        return 1;
    }

    private static int restoreCity(
            CommandSourceStack source,
            String territoryId
    ) {
        TerritoryDefinition territory = MapDefinitionManager.territory(territoryId);
        if (territory == null) {
            source.sendFailure(Component.literal(
                    "No map-defined territory: " + territoryId));
            return 0;
        }

        CityRestorationService.Result result =
                CityRestorationService.restore(source.getServer(), territory);
        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(result.message()), true);
        return 1;
    }

    private static int openDashboard(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!NationDashboard.open(player)) {
            source.sendFailure(Component.literal(
                    "You are not in a nation. Use /nation create <name> or accept an invitation."));
            return 0;
        }
        return 1;
    }

    private static int createNation(CommandSourceStack source, String name)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CatanSavedData data = CatanSavedData.get(source.getServer());
        String cleanName = name.trim();

        if (cleanName.length() < 2 || cleanName.length() > 32) {
            source.sendFailure(Component.literal("Nation names must be 2-32 characters."));
            return 0;
        }
        if (data.nationForPlayer(player.getUUID()) != null) {
            source.sendFailure(Component.literal("You are already in a nation."));
            return 0;
        }
        if (data.nationNameExists(cleanName)) {
            source.sendFailure(Component.literal("A nation with that name already exists."));
            return 0;
        }

        NationData nation = data.createNation(cleanName, player.getUUID());
        source.sendSuccess(() -> Component.literal(
                "Created nation " + nation.name() +
                        " with starting treasury $" + nation.treasury()), true);
        NationDashboard.open(player);
        return 1;
    }

    private static int invitePlayer(CommandSourceStack source, ServerPlayer target)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CatanSavedData data = CatanSavedData.get(source.getServer());
        NationData nation = requireLeaderNation(source, player, data);
        if (nation == null) return 0;

        if (data.nationForPlayer(target.getUUID()) != null) {
            source.sendFailure(Component.literal(
                    target.getGameProfile().getName() + " is already in a nation."));
            return 0;
        }

        nation.invite(target.getUUID());
        data.setDirty();

        source.sendSuccess(() -> Component.literal(
                "Invited " + target.getGameProfile().getName() + " to " + nation.name()), false);
        target.sendSystemMessage(Component.literal(
                "You were invited to " + nation.name() +
                        ". Use /nation accept " + nation.name()));
        return 1;
    }

    private static int acceptInvite(CommandSourceStack source, String nationName)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CatanSavedData data = CatanSavedData.get(source.getServer());

        if (data.nationForPlayer(player.getUUID()) != null) {
            source.sendFailure(Component.literal("You are already in a nation."));
            return 0;
        }

        NationData nation = data.nationByName(nationName.trim());
        if (nation == null) {
            source.sendFailure(Component.literal("Unknown nation: " + nationName));
            return 0;
        }
        if (!nation.isInvited(player.getUUID())) {
            source.sendFailure(Component.literal(
                    "You do not have an invitation to " + nation.name() + "."));
            return 0;
        }

        nation.addMember(player.getUUID());
        data.setDirty();
        source.sendSuccess(() -> Component.literal("Joined " + nation.name() + "."), true);
        NationDashboard.open(player);
        return 1;
    }

    private static int leaveNation(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CatanSavedData data = CatanSavedData.get(source.getServer());
        NationData nation = data.nationForPlayer(player.getUUID());

        if (nation == null) {
            source.sendFailure(Component.literal("You are not in a nation."));
            return 0;
        }
        if (nation.isLeader(player.getUUID())) {
            source.sendFailure(Component.literal(
                    "Nation leaders cannot leave yet. Transfer/disband will be added separately."));
            return 0;
        }

        nation.removeMember(player.getUUID());
        data.setDirty();
        source.sendSuccess(() -> Component.literal("Left " + nation.name() + "."), true);
        return 1;
    }

    private static int nationInfo(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CatanSavedData data = CatanSavedData.get(source.getServer());
        NationData nation = data.nationForPlayer(player.getUUID());

        if (nation == null) {
            source.sendFailure(Component.literal("You are not in a nation."));
            return 0;
        }

        long territoryCount = data.territories().stream()
                .filter(t -> nation.id().equals(t.ownerNationId()))
                .count();

        source.sendSuccess(() -> Component.literal(
                nation.name() +
                        " | Treasury: $" + nation.treasury() +
                        " | Territories: " + territoryCount +
                        " | Members: " + nation.members().size()), false);
        return 1;
    }

    private static int stockpile(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        NationData nation = CatanSavedData.get(source.getServer())
                .nationForPlayer(player.getUUID());

        if (nation == null) {
            source.sendFailure(Component.literal("You are not in a nation."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "=== " + nation.name() + " Stockpile ==="), false);

        nation.stockpileView().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().id()))
                .forEach(entry -> source.sendSuccess(
                        () -> Component.literal(
                                entry.getKey().id() + ": " + entry.getValue()), false));
        return 1;
    }

    private static int chooseStartingCity(
            CommandSourceStack source,
            int slot
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CatanSavedData data = CatanSavedData.get(source.getServer());
        NationData nation = requireLeaderNation(source, player, data);
        if (nation == null) return 0;

        boolean alreadyOwnsTerritory = data.territories().stream()
                .anyMatch(territory -> nation.id().equals(territory.ownerNationId()));
        if (alreadyOwnsTerritory) {
            source.sendFailure(Component.literal(
                    "Your nation already has a starting territory."));
            return 0;
        }

        TerritoryDefinition definition = MapDefinitionManager.territories().stream()
                .filter(value -> value.startSlot() == slot)
                .findFirst()
                .orElse(null);
        if (definition == null) {
            source.sendFailure(Component.literal(
                    "Starting city slot " + slot + " is not defined."));
            return 0;
        }

        TerritoryData territory = data.territory(definition.id());
        if (territory == null) {
            source.sendFailure(Component.literal(
                    "Starting territory " + definition.id() +
                            " is not synchronized with the world save."));
            return 0;
        }
        if (territory.ownerNationId() != null) {
            NationData owner = data.nation(territory.ownerNationId());
            source.sendFailure(Component.literal(
                    definition.name() + " is already controlled by " +
                            (owner == null ? "another nation" : owner.name()) + "."));
            return 0;
        }
        if (!definition.startsWithSettlement()) {
            source.sendFailure(Component.literal(
                    definition.name() +
                            " is not configured as a prebuilt starting settlement."));
            return 0;
        }

        MapAnchor farmAnchor = definition.anchor("plot_5");
        if (farmAnchor != null
                && definition.resources().contains(ResourceType.AGRICULTURE)) {
            SchematicPlacementService.Result farmPlacement =
                    SchematicPlacementService.place(
                            source.getServer(),
                            definition.dimension(),
                            "farm",
                            farmAnchor,
                            false
                    );
            if (!farmPlacement.success()) {
                source.sendFailure(Component.literal(
                        "Starting-city farm placement failed; the city was not assigned. " +
                                farmPlacement.message()));
                return 0;
            }
        }

        territory.setOwnerNationId(nation.id());
        data.setDirty();

        source.sendSuccess(() -> Component.literal(
                nation.name() + " selected " + definition.name() +
                        " as its starting city. Mixed starter production is active."), true);
        NationDashboard.open(player);
        return 1;
    }

    private static int claimNeutralTerritory(
            CommandSourceStack source,
            String territoryId
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CatanSavedData data = CatanSavedData.get(source.getServer());
        NationData nation = requireLeaderNation(source, player, data);
        if (nation == null) return 0;

        TerritoryData territory = data.territory(territoryId);
        if (territory == null) {
            source.sendFailure(Component.literal("Unknown territory: " + territoryId));
            return 0;
        }
        if (territory.ownerNationId() != null) {
            source.sendFailure(Component.literal(territory.name() + " is already owned."));
            return 0;
        }

        boolean hasOwnedTerritory = data.territories().stream()
                .anyMatch(t -> nation.id().equals(t.ownerNationId()));
        if (!hasOwnedTerritory) {
            source.sendFailure(Component.literal(
                    "Your starting territory must be assigned by the server before expanding."));
            return 0;
        }

        boolean adjacent = territory.neighbors().stream().anyMatch(neighborId -> {
            TerritoryData neighbor = data.territory(neighborId);
            return neighbor != null && nation.id().equals(neighbor.ownerNationId());
        });
        if (!adjacent) {
            source.sendFailure(Component.literal(
                    territory.name() + " does not border your nation."));
            return 0;
        }

        EconomyCost cost = EconomyCatalog.neutralTerritoryClaimCost();
        if (!cost.canAfford(nation)) {
            source.sendFailure(Component.literal(
                    "Cannot afford territorial expansion. Need: " + cost.describe()));
            return 0;
        }

        TerritoryDefinition mapDefinition =
                MapDefinitionManager.territory(territory.id());

        if (mapDefinition != null
                && !mapDefinition.startsWithSettlement()
                && !mapDefinition.settlementTemplate().isBlank()) {
            if (mapDefinition.settlementAnchor() == null) {
                source.sendFailure(Component.literal(
                        "This territory has no settlement anchor in the map definition."));
                return 0;
            }

            SchematicPlacementService.Result placement =
                    SchematicPlacementService.place(
                            source.getServer(),
                            mapDefinition.dimension(),
                            mapDefinition.settlementTemplate(),
                            mapDefinition.settlementAnchor(),
                            false
                    );

            if (!placement.success()) {
                source.sendFailure(Component.literal(
                        "City activation failed; no resources were charged. " +
                                placement.message()));
                return 0;
            }
        }

        cost.charge(nation);
        territory.setOwnerNationId(nation.id());
        data.setDirty();

        source.sendSuccess(() -> Component.literal(
                nation.name() + " expanded into " + territory.name() +
                        " for " + cost.describe() +
                        (mapDefinition != null && !mapDefinition.startsWithSettlement()
                                ? ". The prepared site is now an active TH1 settlement."
                                : "")), true);
        NationDashboard.open(player);
        return 1;
    }

    private static int buildIndustry(
            CommandSourceStack source,
            String territoryId,
            String typeName
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CatanSavedData data = CatanSavedData.get(source.getServer());
        NationData nation = requireLeaderNation(source, player, data);
        if (nation == null) return 0;

        TerritoryData territory = requireOwnedTerritory(source, data, nation, territoryId);
        if (territory == null) return 0;

        final BuildingType type;
        try {
            type = BuildingType.parse(typeName);
        } catch (IllegalArgumentException ex) {
            source.sendFailure(Component.literal("Unknown building type: " + typeName));
            return 0;
        }

        if (territory.cityLevel() < type.minCityLevel()) {
            source.sendFailure(Component.literal(
                    type.displayName() + " requires City Level " + type.minCityLevel() + "."));
            return 0;
        }

        int slotCap = EconomyCatalog.maxBuildingSlots(territory.cityLevel());
        TerritoryDefinition mapDefinition =
                MapDefinitionManager.territory(territory.id());

        String plotId = "";
        if (mapDefinition != null) {
            int physicalPlotCap = mapDefinition.availableBuildingPlots().size();
            slotCap = Math.min(slotCap, physicalPlotCap);

            java.util.Set<String> occupiedPlots = territory.buildings().stream()
                    .map(BuildingInstance::plotId)
                    .filter(value -> !value.isBlank())
                    .collect(java.util.stream.Collectors.toSet());

            MapAnchor openPlot = mapDefinition.availableBuildingPlots().stream()
                    .filter(plot -> !occupiedPlots.contains(plot.id()))
                    .findFirst()
                    .orElse(null);

            if (openPlot == null) {
                source.sendFailure(Component.literal(
                        "No open physical building plots are defined for " +
                                territory.name() + "."));
                return 0;
            }
            plotId = openPlot.id();
        }

        if (territory.buildings().size() >= slotCap) {
            source.sendFailure(Component.literal(
                    "No open development slots. Upgrade the city first."));
            return 0;
        }

        EconomyCost cost = EconomyCatalog.buildingCost(type);
        if (!cost.canAfford(nation)) {
            source.sendFailure(Component.literal(
                    "Cannot afford " + type.displayName() + ". Need: " + cost.describe()));
            return 0;
        }

        cost.charge(nation);
        long target = type.isProcessor() ? 100 : 0;
        String assignedPlot = plotId;
        territory.buildings().add(
                new BuildingInstance(type, 1, target, assignedPlot));
        data.setDirty();

        source.sendSuccess(() -> Component.literal(
                "Built " + type.displayName() + " in " + territory.name() +
                        (assignedPlot.isBlank() ? "" : " at " + assignedPlot) +
                        " for " + cost.describe()), true);
        NationDashboard.open(player);
        return 1;
    }

    private static int upgradeCity(CommandSourceStack source, String territoryId)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CatanSavedData data = CatanSavedData.get(source.getServer());
        NationData nation = requireLeaderNation(source, player, data);
        if (nation == null) return 0;

        TerritoryData territory = requireOwnedTerritory(source, data, nation, territoryId);
        if (territory == null) return 0;

        EconomyCost cost = EconomyCatalog.cityUpgradeCost(territory.cityLevel());
        if (cost == null) {
            source.sendFailure(Component.literal("City is already Level 5."));
            return 0;
        }
        if (!cost.canAfford(nation)) {
            source.sendFailure(Component.literal(
                    "Cannot afford city upgrade. Need: " + cost.describe()));
            return 0;
        }

        int nextLevel = territory.cityLevel() + 1;
        TerritoryDefinition mapDefinition =
                MapDefinitionManager.territory(territory.id());

        String townHallTemplate = switch (nextLevel) {
            case 2 -> "thall2";
            case 3 -> "thall3";
            default -> "";
        };

        if (!townHallTemplate.isBlank() && mapDefinition != null) {
            if (mapDefinition.townHall() == null) {
                source.sendFailure(Component.literal(
                        "This city has no Town Hall anchor in the map definition."));
                return 0;
            }

            SchematicPlacementService.Result placement =
                    SchematicPlacementService.place(
                            source.getServer(),
                            mapDefinition.dimension(),
                            townHallTemplate,
                            mapDefinition.townHall(),
                            true
                    );

            if (!placement.success()) {
                source.sendFailure(Component.literal(
                        "Town Hall upgrade failed; no resources were charged. " +
                                placement.message()));
                return 0;
            }
        }

        cost.charge(nation);
        territory.setCityLevel(nextLevel);
        data.setDirty();

        String visualNote = nextLevel <= 3
                ? " Town Hall upgraded in-world."
                : " Town Hall remains at the current visual tier until the next art tier is added.";

        source.sendSuccess(() -> Component.literal(
                territory.name() + " upgraded to City Level " +
                        territory.cityLevel() + "." + visualNote), true);
        NationDashboard.open(player);
        return 1;
    }

    private static int upgradeProducer(CommandSourceStack source, String territoryId)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CatanSavedData data = CatanSavedData.get(source.getServer());
        NationData nation = requireLeaderNation(source, player, data);
        if (nation == null) return 0;

        TerritoryData territory = requireOwnedTerritory(source, data, nation, territoryId);
        if (territory == null) return 0;

        EconomyCost cost = EconomyCatalog.producerUpgradeCost(territory.producerLevel());
        if (cost == null) {
            source.sendFailure(Component.literal("Resource producer is already Level 5."));
            return 0;
        }
        if (!cost.canAfford(nation)) {
            source.sendFailure(Component.literal(
                    "Cannot afford producer upgrade. Need: " + cost.describe()));
            return 0;
        }

        cost.charge(nation);
        territory.setProducerLevel(territory.producerLevel() + 1);
        data.setDirty();

        source.sendSuccess(() -> Component.literal(
                territory.name() + " resource production upgraded to Level " +
                        territory.producerLevel()), true);
        NationDashboard.open(player);
        return 1;
    }

    private static int upgradeBuilding(
            CommandSourceStack source,
            String territoryId,
            int index
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CatanSavedData data = CatanSavedData.get(source.getServer());
        NationData nation = requireLeaderNation(source, player, data);
        if (nation == null) return 0;

        TerritoryData territory = requireOwnedTerritory(source, data, nation, territoryId);
        if (territory == null) return 0;

        BuildingInstance building = buildingAt(source, territory, index);
        if (building == null) return 0;

        if (building.level() >= territory.cityLevel()) {
            source.sendFailure(Component.literal(
                    "Building level cannot exceed City Level " + territory.cityLevel() + "."));
            return 0;
        }

        EconomyCost cost = EconomyCatalog.buildingUpgradeCost(
                building.type(), building.level());
        if (cost == null) {
            source.sendFailure(Component.literal("Building is already Level 5."));
            return 0;
        }
        if (!cost.canAfford(nation)) {
            source.sendFailure(Component.literal(
                    "Cannot afford building upgrade. Need: " + cost.describe()));
            return 0;
        }

        cost.charge(nation);
        building.setLevel(building.level() + 1);
        data.setDirty();

        source.sendSuccess(() -> Component.literal(
                building.type().displayName() + " upgraded to Level " + building.level()), true);
        NationDashboard.open(player);
        return 1;
    }

    private static int setNationBuildingTarget(
            CommandSourceStack source,
            String territoryId,
            int index,
            long amount
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CatanSavedData data = CatanSavedData.get(source.getServer());
        NationData nation = requireLeaderNation(source, player, data);
        if (nation == null) return 0;

        TerritoryData territory = requireOwnedTerritory(source, data, nation, territoryId);
        if (territory == null) return 0;

        BuildingInstance building = buildingAt(source, territory, index);
        if (building == null) return 0;

        if (!building.type().isProcessor()) {
            source.sendFailure(Component.literal(
                    building.type().displayName() + " does not use a production target."));
            return 0;
        }

        building.setTargetStock(amount);
        data.setDirty();

        source.sendSuccess(() -> Component.literal(
                building.type().displayName() +
                        " target stock set to " + amount), false);
        NationDashboard.open(player);
        return 1;
    }

    private static int proposeTrade(
            CommandSourceStack source,
            String recipientNationName,
            String offeredAssetRaw,
            long offeredAmount,
            String requestedAssetRaw,
            long requestedAmount
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CatanSavedData data = CatanSavedData.get(source.getServer());
        NationData sender = requireLeaderNation(source, player, data);
        if (sender == null) return 0;

        NationData recipient = data.nationByName(recipientNationName.trim());
        if (recipient == null) {
            source.sendFailure(Component.literal(
                    "Unknown nation: " + recipientNationName));
            return 0;
        }
        if (sender.id().equals(recipient.id())) {
            source.sendFailure(Component.literal("You cannot trade with your own nation."));
            return 0;
        }

        final String offeredAsset;
        final String requestedAsset;
        try {
            offeredAsset = normalizeTradeAsset(offeredAssetRaw);
            requestedAsset = normalizeTradeAsset(requestedAssetRaw);
        } catch (IllegalArgumentException ex) {
            source.sendFailure(Component.literal(
                    "Trade assets must be money or a valid resource id."));
            return 0;
        }

        if (!hasTradeAsset(sender, offeredAsset, offeredAmount)) {
            source.sendFailure(Component.literal(
                    "Your nation does not have enough " +
                            tradeAssetDisplay(offeredAsset) + " to escrow this offer."));
            return 0;
        }

        debitTradeAsset(sender, offeredAsset, offeredAmount);

        TradeProposal proposal = data.createTradeProposal(
                sender.id(),
                recipient.id(),
                offeredAsset,
                offeredAmount,
                requestedAsset,
                requestedAmount,
                source.getServer().overworld().getGameTime()
        );
        data.setDirty();

        source.sendSuccess(() -> Component.literal(
                "Trade #" + proposal.id() + " sent to " + recipient.name() +
                        ": offer " + tradeAssetAmountText(offeredAsset, offeredAmount) +
                        " for " + tradeAssetAmountText(requestedAsset, requestedAmount) +
                        ". Offered assets are now in escrow."), true);

        source.getServer().getPlayerList().getPlayers().stream()
                .filter(p -> recipient.containsMember(p.getUUID()))
                .forEach(p -> p.sendSystemMessage(Component.literal(
                        "[CatanCraft] New trade proposal #" + proposal.id() +
                                " from " + sender.name() + ". Open /nation to review it.")));

        NationDashboard.open(player);
        return 1;
    }

    private static int acceptTrade(
            CommandSourceStack source,
            String proposalId
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CatanSavedData data = CatanSavedData.get(source.getServer());
        NationData recipient = requireLeaderNation(source, player, data);
        if (recipient == null) return 0;

        TradeProposal proposal = data.tradeProposal(proposalId);
        if (proposal == null || !recipient.id().equals(proposal.recipientNationId())) {
            source.sendFailure(Component.literal(
                    "No incoming trade proposal with id " + proposalId + "."));
            return 0;
        }

        NationData sender = data.nation(proposal.senderNationId());
        if (sender == null) {
            source.sendFailure(Component.literal(
                    "The sending nation no longer exists."));
            return 0;
        }

        if (!hasTradeAsset(
                recipient,
                proposal.requestedAsset(),
                proposal.requestedAmount())) {
            source.sendFailure(Component.literal(
                    "Your nation does not have enough " +
                            tradeAssetDisplay(proposal.requestedAsset()) +
                            " to accept this trade."));
            return 0;
        }

        debitTradeAsset(
                recipient,
                proposal.requestedAsset(),
                proposal.requestedAmount());
        creditTradeAsset(
                sender,
                proposal.requestedAsset(),
                proposal.requestedAmount());
        creditTradeAsset(
                recipient,
                proposal.offeredAsset(),
                proposal.offeredAmount());

        data.removeTradeProposal(proposal.id());
        data.setDirty();

        source.sendSuccess(() -> Component.literal(
                "Accepted trade #" + proposal.id() + " with " + sender.name() + "."), true);

        source.getServer().getPlayerList().getPlayers().stream()
                .filter(p -> sender.containsMember(p.getUUID()))
                .forEach(p -> p.sendSystemMessage(Component.literal(
                        "[CatanCraft] " + recipient.name() +
                                " accepted trade #" + proposal.id() + ".")));

        NationDashboard.open(player);
        return 1;
    }

    private static int declineTrade(
            CommandSourceStack source,
            String proposalId
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CatanSavedData data = CatanSavedData.get(source.getServer());
        NationData recipient = requireLeaderNation(source, player, data);
        if (recipient == null) return 0;

        TradeProposal proposal = data.tradeProposal(proposalId);
        if (proposal == null || !recipient.id().equals(proposal.recipientNationId())) {
            source.sendFailure(Component.literal(
                    "No incoming trade proposal with id " + proposalId + "."));
            return 0;
        }

        NationData sender = data.nation(proposal.senderNationId());
        if (sender != null) {
            creditTradeAsset(sender, proposal.offeredAsset(), proposal.offeredAmount());
        }

        data.removeTradeProposal(proposal.id());
        data.setDirty();

        source.sendSuccess(() -> Component.literal(
                "Declined trade #" + proposal.id() + "."), true);

        if (sender != null) {
            source.getServer().getPlayerList().getPlayers().stream()
                    .filter(p -> sender.containsMember(p.getUUID()))
                    .forEach(p -> p.sendSystemMessage(Component.literal(
                            "[CatanCraft] " + recipient.name() +
                                    " declined trade #" + proposal.id() +
                                    ". Escrow was refunded.")));
        }

        NationDashboard.open(player);
        return 1;
    }

    private static int cancelTrade(
            CommandSourceStack source,
            String proposalId
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CatanSavedData data = CatanSavedData.get(source.getServer());
        NationData sender = requireLeaderNation(source, player, data);
        if (sender == null) return 0;

        TradeProposal proposal = data.tradeProposal(proposalId);
        if (proposal == null || !sender.id().equals(proposal.senderNationId())) {
            source.sendFailure(Component.literal(
                    "No outgoing trade proposal with id " + proposalId + "."));
            return 0;
        }

        creditTradeAsset(sender, proposal.offeredAsset(), proposal.offeredAmount());
        data.removeTradeProposal(proposal.id());
        data.setDirty();

        source.sendSuccess(() -> Component.literal(
                "Canceled trade #" + proposal.id() + ". Escrow was refunded."), true);
        NationDashboard.open(player);
        return 1;
    }

    private static String resourceListText(
            java.util.List<ResourceType> resources
    ) {
        StringBuilder result = new StringBuilder();
        for (ResourceType resource : resources) {
            if (!result.isEmpty()) result.append(" + ");
            result.append(resource.id().replace('_', ' '));
        }
        return result.toString();
    }

    private static String normalizeTradeAsset(String raw) {
        if (raw.equalsIgnoreCase("money") || raw.equalsIgnoreCase("cash")) {
            return "money";
        }
        return ResourceType.parse(raw).id();
    }

    private static boolean hasTradeAsset(NationData nation, String asset, long amount) {
        if ("money".equals(asset)) {
            return nation.treasury() >= amount;
        }
        return nation.resource(ResourceType.parse(asset)) >= amount;
    }

    private static void debitTradeAsset(NationData nation, String asset, long amount) {
        if ("money".equals(asset)) {
            nation.setTreasury(nation.treasury() - amount);
        } else {
            nation.addResource(ResourceType.parse(asset), -amount);
        }
    }

    private static void creditTradeAsset(NationData nation, String asset, long amount) {
        if ("money".equals(asset)) {
            nation.addTreasury(amount);
        } else {
            nation.addResource(ResourceType.parse(asset), amount);
        }
    }

    private static String tradeAssetDisplay(String asset) {
        if ("money".equals(asset)) return "money";
        return ResourceType.parse(asset).id().replace('_', ' ');
    }

    private static String tradeAssetAmountText(String asset, long amount) {
        if ("money".equals(asset)) return "$" + amount;
        return amount + " " + tradeAssetDisplay(asset);
    }

    private static int createTerritory(
            CommandSourceStack source,
            String id,
            String resource,
            String name
    ) {
        if (MapDefinitionManager.territory(id) != null) {
            source.sendFailure(Component.literal(
                    "Territory " + id +
                            " is map-defined. Edit config/catancraft/map.json instead."));
            return 0;
        }

        CatanSavedData data = CatanSavedData.get(source.getServer());
        if (data.territory(id) != null) {
            source.sendFailure(Component.literal("Territory id already exists."));
            return 0;
        }

        try {
            ResourceType specialty = ResourceType.parse(resource);
            TerritoryData territory = data.createTerritory(id, name, specialty);
            source.sendSuccess(() -> Component.literal(
                    "Created territory " + territory.name() +
                            " [" + territory.id() + "] specialty=" + specialty.id()), true);
            return 1;
        } catch (IllegalArgumentException ex) {
            source.sendFailure(Component.literal("Unknown resource: " + resource));
            return 0;
        }
    }

    private static int assignTerritory(
            CommandSourceStack source,
            String territoryId,
            String nationName
    ) {
        CatanSavedData data = CatanSavedData.get(source.getServer());
        TerritoryData territory = data.territory(territoryId);
        NationData nation = data.nationByName(nationName);

        if (territory == null) {
            source.sendFailure(Component.literal("Unknown territory: " + territoryId));
            return 0;
        }
        if (nation == null) {
            source.sendFailure(Component.literal("Unknown nation: " + nationName));
            return 0;
        }

        territory.setOwnerNationId(nation.id());
        data.setDirty();

        source.sendSuccess(() -> Component.literal(
                territory.name() + " is now owned by " + nation.name()), true);
        return 1;
    }

    private static int linkTerritories(
            CommandSourceStack source,
            String firstId,
            String secondId
    ) {
        if (MapDefinitionManager.territory(firstId) != null
                || MapDefinitionManager.territory(secondId) != null) {
            source.sendFailure(Component.literal(
                    "Map-defined adjacency cannot be changed in-game. Edit map.json instead."));
            return 0;
        }

        CatanSavedData data = CatanSavedData.get(source.getServer());
        TerritoryData first = data.territory(firstId);
        TerritoryData second = data.territory(secondId);

        if (first == null || second == null) {
            source.sendFailure(Component.literal(
                    "Both territory ids must already exist."));
            return 0;
        }
        if (!data.linkTerritories(firstId, secondId)) {
            source.sendFailure(Component.literal("Could not link those territories."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "Linked " + first.name() + " <-> " + second.name()), true);
        return 1;
    }

    private static int addBoundaryPoint(
            CommandSourceStack source,
            String territoryId
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (MapDefinitionManager.territory(territoryId) != null) {
            source.sendFailure(Component.literal(
                    "This territory boundary is map-defined. Edit map.json instead."));
            return 0;
        }

        CatanSavedData data = CatanSavedData.get(source.getServer());
        TerritoryData territory = data.territory(territoryId);

        if (territory == null) {
            source.sendFailure(Component.literal("Unknown territory: " + territoryId));
            return 0;
        }

        String dimensionId = player.serverLevel().dimension().location().toString();
        var boundary = territory.ensureBoundary(dimensionId);

        if (!boundary.dimensionId().equals(dimensionId)) {
            source.sendFailure(Component.literal(
                    "This boundary already belongs to dimension " +
                            boundary.dimensionId() + ". Clear it before redefining."));
            return 0;
        }

        int x = player.blockPosition().getX();
        int z = player.blockPosition().getZ();
        boundary.addPoint(x, z);
        data.setDirty();

        source.sendSuccess(() -> Component.literal(
                "Added boundary point " + boundary.points().size() +
                        " for " + territory.name() +
                        " at X=" + x + " Z=" + z), true);
        return 1;
    }

    private static int clearBoundary(
            CommandSourceStack source,
            String territoryId
    ) {
        if (MapDefinitionManager.territory(territoryId) != null) {
            source.sendFailure(Component.literal(
                    "This territory boundary is map-defined. Edit map.json instead."));
            return 0;
        }

        CatanSavedData data = CatanSavedData.get(source.getServer());
        TerritoryData territory = data.territory(territoryId);

        if (territory == null) {
            source.sendFailure(Component.literal("Unknown territory: " + territoryId));
            return 0;
        }

        territory.clearBoundary();
        data.setDirty();
        source.sendSuccess(() -> Component.literal(
                "Cleared world boundary for " + territory.name()), true);
        return 1;
    }

    private static int territoryHere(CommandSourceStack source)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CatanSavedData data = CatanSavedData.get(source.getServer());
        TerritoryData territory = data.territoryAt(
                player.serverLevel(),
                player.blockPosition()
        );

        if (territory == null) {
            source.sendSuccess(() -> Component.literal(
                    "You are not inside a defined CatanCraft territory."), false);
            return 0;
        }

        String owner = "Neutral";
        if (territory.ownerNationId() != null) {
            NationData nation = data.nation(territory.ownerNationId());
            owner = nation == null ? "Unknown" : nation.name();
        }

        String finalOwner = owner;
        source.sendSuccess(() -> Component.literal(
                "Current territory: " + territory.name() +
                        " [" + territory.id() + "] • " +
                        territory.specialty().id() +
                        " • Owner: " + finalOwner), false);
        return 1;
    }

    private static int territoryInfo(CommandSourceStack source, String territoryId) {
        CatanSavedData data = CatanSavedData.get(source.getServer());
        TerritoryData territory = data.territory(territoryId);

        if (territory == null) {
            source.sendFailure(Component.literal("Unknown territory: " + territoryId));
            return 0;
        }

        String owner = "Neutral";
        if (territory.ownerNationId() != null) {
            NationData nation = data.nation(territory.ownerNationId());
            owner = nation == null ? "Unknown" : nation.name();
        }

        String finalOwner = owner;
        source.sendSuccess(() -> Component.literal(
                territory.name() + " [" + territory.id() + "] | " +
                        resourceListText(territory.rawResources()) +
                        " | Owner: " + finalOwner +
                        " | Producer L" + territory.producerLevel() +
                        " | City L" + territory.cityLevel() +
                        " | Buildings: " + territory.buildings().size()), false);
        return 1;
    }

    private static int adminAddBuilding(
            CommandSourceStack source,
            String territoryId,
            String typeName
    ) {
        CatanSavedData data = CatanSavedData.get(source.getServer());
        TerritoryData territory = data.territory(territoryId);

        if (territory == null) {
            source.sendFailure(Component.literal("Unknown territory: " + territoryId));
            return 0;
        }

        try {
            BuildingType type = BuildingType.parse(typeName);
            String plotId = "";
            TerritoryDefinition definition =
                    MapDefinitionManager.territory(territory.id());

            if (definition != null) {
                java.util.Set<String> occupiedPlots = territory.buildings().stream()
                        .map(BuildingInstance::plotId)
                        .filter(value -> !value.isBlank())
                        .collect(java.util.stream.Collectors.toSet());

                MapAnchor openPlot = definition.availableBuildingPlots().stream()
                        .filter(plot -> !occupiedPlots.contains(plot.id()))
                        .findFirst()
                        .orElse(null);

                if (openPlot == null) {
                    source.sendFailure(Component.literal(
                            "No open map-defined building plot in " +
                                    territory.name() + "."));
                    return 0;
                }
                plotId = openPlot.id();
            }

            String assignedPlot = plotId;
            territory.buildings().add(
                    new BuildingInstance(
                            type,
                            1,
                            type.isProcessor() ? 100 : 0,
                            assignedPlot));
            data.setDirty();
            int index = territory.buildings().size() - 1;

            source.sendSuccess(() -> Component.literal(
                    "Admin-added " + type.displayName() +
                            " to " + territory.name() +
                            (assignedPlot.isBlank() ? "" : " at " + assignedPlot) +
                            " at building index " + index), true);
            return 1;
        } catch (IllegalArgumentException ex) {
            source.sendFailure(Component.literal("Unknown building type: " + typeName));
            return 0;
        }
    }

    private static int adminSetBuildingTarget(
            CommandSourceStack source,
            String territoryId,
            int index,
            long amount
    ) {
        CatanSavedData data = CatanSavedData.get(source.getServer());
        TerritoryData territory = data.territory(territoryId);
        BuildingInstance building = territory == null ? null : buildingAt(source, territory, index);

        if (territory == null || building == null) {
            if (territory == null) {
                source.sendFailure(Component.literal("Unknown territory: " + territoryId));
            }
            return 0;
        }

        building.setTargetStock(amount);
        data.setDirty();
        source.sendSuccess(() -> Component.literal(
                "Building " + index + " target stock set to " + amount), true);
        return 1;
    }

    private static int createMonument(
            CommandSourceStack source,
            String id,
            String typeName,
            int radius,
            String name
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CatanSavedData data = CatanSavedData.get(source.getServer());

        if (data.monument(id) != null) {
            source.sendFailure(Component.literal("Monument id already exists: " + id));
            return 0;
        }
        if (data.monuments().size() >= 3) {
            source.sendFailure(Component.literal(
                    "V0.1 is capped at three monuments to keep interaction concentrated."));
            return 0;
        }

        final MonumentType type;
        try {
            type = MonumentType.parse(typeName);
        } catch (IllegalArgumentException ex) {
            source.sendFailure(Component.literal(
                    "Unknown type. Use industrial_complex, military_depot, or refinery."));
            return 0;
        }

        String dimension = player.serverLevel().dimension().location().toString();
        var pos = player.blockPosition();
        MonumentData monument = data.createMonument(
                id, name.trim(), type, dimension,
                pos.getX(), pos.getY(), pos.getZ(), radius);

        source.sendSuccess(() -> Component.literal(
                "Created " + monument.name() + " [" + monument.id() + "] at X=" +
                        monument.x() + " Z=" + monument.z() +
                        " radius=" + monument.radius()), true);
        return 1;
    }

    private static int activateMonument(CommandSourceStack source, String id) {
        CatanSavedData data = CatanSavedData.get(source.getServer());
        MonumentData monument = data.monument(id);
        if (monument == null) {
            source.sendFailure(Component.literal("Unknown monument: " + id));
            return 0;
        }

        MonumentManager.forceActivate(source.getServer(), data, monument);
        source.sendSuccess(() -> Component.literal("Activated " + monument.name()), true);
        return 1;
    }

    private static int listMonuments(CommandSourceStack source) {
        CatanSavedData data = CatanSavedData.get(source.getServer());
        MonumentData active = data.activeMonument();

        if (data.monuments().isEmpty()) {
            source.sendSuccess(() -> Component.literal("No monuments configured."), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("=== CatanCraft Monuments ==="), false);
        for (MonumentData monument : data.monuments()) {
            boolean isActive = active != null && active.id().equals(monument.id());
            source.sendSuccess(() -> Component.literal(
                    (isActive ? "[ACTIVE] " : "") +
                            monument.name() + " [" + monument.id() + "] • " +
                            monument.type().displayName() +
                            " • X=" + monument.x() +
                            " Y=" + monument.y() +
                            " Z=" + monument.z() +
                            " • R=" + monument.radius()), false);
        }
        return 1;
    }

    private static int debugGive(
            CommandSourceStack source,
            String resourceName,
            long amount
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CatanSavedData data = CatanSavedData.get(source.getServer());
        NationData nation = data.nationForPlayer(player.getUUID());

        if (nation == null) {
            source.sendFailure(Component.literal("You are not in a nation."));
            return 0;
        }

        try {
            ResourceType type = ResourceType.parse(resourceName);
            nation.addResource(type, amount);
            data.setDirty();

            source.sendSuccess(() -> Component.literal(
                    "Added " + amount + " " + type.id() +
                            " to " + nation.name()), true);
            return 1;
        } catch (IllegalArgumentException ex) {
            source.sendFailure(Component.literal("Unknown resource: " + resourceName));
            return 0;
        }
    }

    private static int debugSetResource(
            CommandSourceStack source,
            String resourceName,
            long amount
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CatanSavedData data = CatanSavedData.get(source.getServer());
        NationData nation = data.nationForPlayer(player.getUUID());

        if (nation == null) {
            source.sendFailure(Component.literal("You are not in a nation."));
            return 0;
        }

        try {
            ResourceType type = ResourceType.parse(resourceName);
            long current = nation.resource(type);
            nation.addResource(type, amount - current);
            data.setDirty();

            source.sendSuccess(() -> Component.literal(
                    "Set " + type.id() + " to " + amount +
                            " for " + nation.name()), true);
            return 1;
        } catch (IllegalArgumentException ex) {
            source.sendFailure(Component.literal("Unknown resource: " + resourceName));
            return 0;
        }
    }

    private static int debugMoney(
            CommandSourceStack source,
            long amount
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CatanSavedData data = CatanSavedData.get(source.getServer());
        NationData nation = data.nationForPlayer(player.getUUID());

        if (nation == null) {
            source.sendFailure(Component.literal("You are not in a nation."));
            return 0;
        }

        nation.addTreasury(amount);
        data.setDirty();
        source.sendSuccess(() -> Component.literal(
                "Added $" + amount + " to " + nation.name() +
                        ". Treasury is now $" + nation.treasury()), true);
        return 1;
    }

    private static int debugCycles(
            CommandSourceStack source,
            int count
    ) {
        for (int i = 0; i < count; i++) {
            EconomyEngine.runCycle(source.getServer());
        }
        source.sendSuccess(() -> Component.literal(
                "Executed " + count + " production cycles (" +
                        (count * 15) + " simulated minutes)."), true);
        return count;
    }

    @Nullable
    private static NationData requireLeaderNation(
            CommandSourceStack source,
            ServerPlayer player,
            CatanSavedData data
    ) {
        NationData nation = data.nationForPlayer(player.getUUID());
        if (nation == null) {
            source.sendFailure(Component.literal("You are not in a nation."));
            return null;
        }
        if (!nation.isLeader(player.getUUID())) {
            source.sendFailure(Component.literal(
                    "Only the nation leader can spend national resources right now."));
            return null;
        }
        return nation;
    }

    @Nullable
    private static TerritoryData requireOwnedTerritory(
            CommandSourceStack source,
            CatanSavedData data,
            NationData nation,
            String territoryId
    ) {
        TerritoryData territory = data.territory(territoryId);
        if (territory == null) {
            source.sendFailure(Component.literal("Unknown territory: " + territoryId));
            return null;
        }
        if (!nation.id().equals(territory.ownerNationId())) {
            source.sendFailure(Component.literal(
                    nation.name() + " does not own " + territory.name() + "."));
            return null;
        }
        return territory;
    }

    @Nullable
    private static BuildingInstance buildingAt(
            CommandSourceStack source,
            TerritoryData territory,
            int index
    ) {
        if (index < 0 || index >= territory.buildings().size()) {
            source.sendFailure(Component.literal(
                    "No building at index " + index + " in " + territory.name() + "."));
            return null;
        }
        return territory.buildings().get(index);
    }
}
