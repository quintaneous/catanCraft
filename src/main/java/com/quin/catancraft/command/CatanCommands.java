package com.quin.catancraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.quin.catancraft.data.BuildingInstance;
import com.quin.catancraft.data.BuildingType;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.data.NationData;
import com.quin.catancraft.data.ResourceType;
import com.quin.catancraft.data.TerritoryData;
import com.quin.catancraft.economy.EconomyEngine;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;

public final class CatanCommands {
    private CatanCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("catan")
                .then(Commands.literal("nation")
                        .then(Commands.literal("create")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> createNation(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("info")
                                .executes(ctx -> nationInfo(ctx.getSource())))
                        .then(Commands.literal("stockpile")
                                .executes(ctx -> stockpile(ctx.getSource()))))
                .then(Commands.literal("territory")
                        .then(Commands.literal("create")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .then(Commands.argument("resource", StringArgumentType.word())
                                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                                        .executes(ctx -> createTerritory(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "id"),
                                                                StringArgumentType.getString(ctx, "resource"),
                                                                StringArgumentType.getString(ctx, "name")))))))
                        .then(Commands.literal("assign")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("territory", StringArgumentType.word())
                                        .then(Commands.argument("nation", StringArgumentType.greedyString())
                                                .executes(ctx -> assignTerritory(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "territory"),
                                                        StringArgumentType.getString(ctx, "nation"))))))
                        .then(Commands.literal("info")
                                .then(Commands.argument("territory", StringArgumentType.word())
                                        .executes(ctx -> territoryInfo(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "territory"))))))
                .then(Commands.literal("building")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("add")
                                .then(Commands.argument("territory", StringArgumentType.word())
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .executes(ctx -> addBuilding(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "territory"),
                                                        StringArgumentType.getString(ctx, "type"))))))
                        .then(Commands.literal("target")
                                .then(Commands.argument("territory", StringArgumentType.word())
                                        .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                                .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                                        .executes(ctx -> setBuildingTarget(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "territory"),
                                                                IntegerArgumentType.getInteger(ctx, "index"),
                                                                LongArgumentType.getLong(ctx, "amount"))))))))
                .then(Commands.literal("debug")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("cycle")
                                .executes(ctx -> {
                                    EconomyEngine.runCycle(ctx.getSource().getServer());
                                    ctx.getSource().sendSuccess(() -> Component.literal("Production cycle executed."), true);
                                    return 1;
                                }))
                        .then(Commands.literal("give")
                                .then(Commands.argument("resource", StringArgumentType.word())
                                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                                .executes(ctx -> debugGive(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "resource"),
                                                        LongArgumentType.getLong(ctx, "amount")))))));
    }

    private static int createNation(CommandSourceStack source, String name) throws Exception {
        ServerPlayer player = source.getPlayerOrException();
        CatanSavedData data = CatanSavedData.get(source.getServer());

        if (data.nationForPlayer(player.getUUID()) != null) {
            source.sendFailure(Component.literal("You are already in a nation."));
            return 0;
        }
        if (data.nationNameExists(name)) {
            source.sendFailure(Component.literal("A nation with that name already exists."));
            return 0;
        }

        NationData nation = data.createNation(name, player.getUUID());
        source.sendSuccess(() -> Component.literal(
                "Created nation " + nation.name() + " with starting treasury $" + nation.treasury()), true);
        return 1;
    }

    private static int nationInfo(CommandSourceStack source) throws Exception {
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
                nation.name() + " | Treasury: $" + nation.treasury() +
                        " | Territories: " + territoryCount +
                        " | Members: " + nation.members().size()), false);
        return 1;
    }

    private static int stockpile(CommandSourceStack source) throws Exception {
        ServerPlayer player = source.getPlayerOrException();
        NationData nation = CatanSavedData.get(source.getServer()).nationForPlayer(player.getUUID());
        if (nation == null) {
            source.sendFailure(Component.literal("You are not in a nation."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("=== " + nation.name() + " Stockpile ==="), false);
        nation.stockpileView().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().id()))
                .filter(entry -> entry.getValue() > 0)
                .forEach(entry -> source.sendSuccess(
                        () -> Component.literal(entry.getKey().id() + ": " + entry.getValue()), false));
        return 1;
    }

    private static int createTerritory(CommandSourceStack source, String id, String resource, String name) {
        CatanSavedData data = CatanSavedData.get(source.getServer());
        if (data.territory(id) != null) {
            source.sendFailure(Component.literal("Territory id already exists."));
            return 0;
        }

        try {
            ResourceType specialty = ResourceType.parse(resource);
            TerritoryData territory = data.createTerritory(id, name, specialty);
            source.sendSuccess(() -> Component.literal(
                    "Created territory " + territory.name() + " [" + territory.id() + "] specialty=" + specialty.id()), true);
            return 1;
        } catch (IllegalArgumentException ex) {
            source.sendFailure(Component.literal("Unknown resource: " + resource));
            return 0;
        }
    }

    private static int assignTerritory(CommandSourceStack source, String territoryId, String nationName) {
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
                        territory.specialty().id() + " | Owner: " + finalOwner +
                        " | Producer L" + territory.producerLevel() +
                        " | City L" + territory.cityLevel() +
                        " | Buildings: " + territory.buildings().size()), false);
        return 1;
    }

    private static int addBuilding(CommandSourceStack source, String territoryId, String typeName) {
        CatanSavedData data = CatanSavedData.get(source.getServer());
        TerritoryData territory = data.territory(territoryId);
        if (territory == null) {
            source.sendFailure(Component.literal("Unknown territory: " + territoryId));
            return 0;
        }

        try {
            BuildingType type = BuildingType.valueOf(typeName.toUpperCase());
            territory.buildings().add(new BuildingInstance(type, 1, 100));
            data.setDirty();
            int index = territory.buildings().size() - 1;
            source.sendSuccess(() -> Component.literal(
                    "Added " + type.name().toLowerCase() + " to " + territory.name() +
                            " at building index " + index), true);
            return 1;
        } catch (IllegalArgumentException ex) {
            source.sendFailure(Component.literal("Unknown building type: " + typeName));
            return 0;
        }
    }

    private static int setBuildingTarget(CommandSourceStack source, String territoryId, int index, long amount) {
        CatanSavedData data = CatanSavedData.get(source.getServer());
        TerritoryData territory = data.territory(territoryId);
        if (territory == null || index >= territory.buildings().size()) {
            source.sendFailure(Component.literal("Unknown territory or building index."));
            return 0;
        }

        territory.buildings().get(index).setTargetStock(amount);
        data.setDirty();
        source.sendSuccess(() -> Component.literal(
                "Building " + index + " target stock set to " + amount), true);
        return 1;
    }

    private static int debugGive(CommandSourceStack source, String resourceName, long amount) throws Exception {
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
                    "Added " + amount + " " + type.id() + " to " + nation.name()), true);
            return 1;
        } catch (IllegalArgumentException ex) {
            source.sendFailure(Component.literal("Unknown resource: " + resourceName));
            return 0;
        }
    }
}
