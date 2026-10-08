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
import com.quin.catancraft.data.ResourceType;
import com.quin.catancraft.data.TerritoryData;
import com.quin.catancraft.economy.EconomyCatalog;
import com.quin.catancraft.economy.EconomyCost;
import com.quin.catancraft.economy.EconomyEngine;
import com.quin.catancraft.ui.NationDashboard;
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

        return node;
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

        cost.charge(nation);
        territory.setOwnerNationId(nation.id());
        data.setDirty();

        source.sendSuccess(() -> Component.literal(
                nation.name() + " expanded into " + territory.name() +
                        " for " + cost.describe()), true);
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
        territory.buildings().add(new BuildingInstance(type, 1, target));
        data.setDirty();

        source.sendSuccess(() -> Component.literal(
                "Built " + type.displayName() + " in " + territory.name() +
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

        cost.charge(nation);
        territory.setCityLevel(territory.cityLevel() + 1);
        data.setDirty();

        source.sendSuccess(() -> Component.literal(
                territory.name() + " upgraded to City Level " + territory.cityLevel()), true);
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

    private static int createTerritory(
            CommandSourceStack source,
            String id,
            String resource,
            String name
    ) {
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
                        territory.specialty().id() +
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
            territory.buildings().add(
                    new BuildingInstance(type, 1, type.isProcessor() ? 100 : 0));
            data.setDirty();
            int index = territory.buildings().size() - 1;

            source.sendSuccess(() -> Component.literal(
                    "Admin-added " + type.displayName() +
                            " to " + territory.name() +
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
