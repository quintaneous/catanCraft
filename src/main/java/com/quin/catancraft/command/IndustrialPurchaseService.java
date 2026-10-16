package com.quin.catancraft.command;

import com.quin.catancraft.data.BuildingInstance;
import com.quin.catancraft.data.BuildingType;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.data.NationData;
import com.quin.catancraft.data.TerritoryData;
import com.quin.catancraft.economy.EconomyCatalog;
import com.quin.catancraft.economy.EconomyCost;
import com.quin.catancraft.map.MapDefinitionManager;
import com.quin.catancraft.map.TerritoryDefinition;
import com.quin.catancraft.ui.NationDashboard;
import com.quin.catancraft.world.IndustrialPlotService;
import com.quin.catancraft.world.MapAssetService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;

/** Shared R3 server-side validation for GUI and /nation build commands. */
public final class IndustrialPurchaseService {
    private IndustrialPurchaseService() {}

    public static int purchase(CommandSourceStack source, ServerPlayer player,
            CatanSavedData data, NationData nation, TerritoryData territory,
            BuildingType type, String requestedPlot) {
        if (territory.cityLevel() < type.minCityLevel()) {
            source.sendFailure(Component.literal(type.displayName() +
                    " requires City Level " + type.minCityLevel() + "."));
            return 0;
        }
        if (territory.buildings().size() >=
                EconomyCatalog.maxBuildingSlots(territory.cityLevel())) {
            source.sendFailure(Component.literal(
                    "No available city development slots. Upgrade your city first."));
            return 0;
        }
        if (territory.buildings().stream().anyMatch(b -> b.type() == type)) {
            source.sendFailure(Component.literal(
                    "This city already has a " + type.displayName() +
                    ". Only one of each industry per city is allowed."));
            return 0;
        }

        TerritoryDefinition map = MapDefinitionManager.territory(territory.id());
        if (map == null) {
            source.sendFailure(Component.literal("No authored city map for this territory."));
            return 0;
        }
        if (IndustrialPlotService.hasUnassignedLegacyIndustry(territory)) {
            source.sendFailure(Component.literal(
                    "Legacy factory without a plot is recorded in this city. " +
                    "Nothing was overwritten. Ask an operator to review its placement."));
            return 0;
        }

        List<String> free = IndustrialPlotService.vacantPlots(territory, map);
        String chosen = requestedPlot == null || requestedPlot.isBlank()
                ? (free.isEmpty() ? "" : free.get(0))
                : requestedPlot.trim().toLowerCase(Locale.ROOT);
        if (!free.contains(chosen)) {
            source.sendFailure(Component.literal(
                    "Plot unavailable: " + chosen + ". Free industrial plots: " +
                    (free.isEmpty() ? "none" : String.join(", ", free))));
            return 0;
        }

        EconomyCost cost = EconomyCatalog.buildingCost(type);
        if (!cost.canAfford(nation)) {
            source.sendFailure(Component.literal(
                    "Cannot afford " + type.displayName() +
                    ". Need " + cost.describe()));
            return 0;
        }

        MapAssetService.Result result = MapAssetService.placeBuilding(
                source.getServer(), data, map, type, chosen);
        if (!result.success()) {
            source.sendFailure(Component.literal(
                    "Physical construction failed: " + result.message() +
                    " No economy cost was charged."));
            return 0;
        }

        // No external unit production occurs until the purchase is recorded.
        cost.charge(nation);
        territory.buildings().add(new BuildingInstance(
                type, 1, type.isProcessor() ? 100 : 0, chosen));
        data.setDirty();
        source.sendSuccess(() -> Component.literal(
                "Built " + type.displayName() + " on " + chosen +
                " in " + territory.name() + " for " + cost.describe() +
                " (" + result.changedBlocks() + " changed blocks)."), true);
        NationDashboard.open(player);
        return 1;
    }
}
