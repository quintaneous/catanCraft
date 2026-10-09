package com.quin.catancraft.world;

import com.quin.catancraft.data.BuildingInstance;
import com.quin.catancraft.data.BuildingType;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.data.TerritoryData;
import com.quin.catancraft.map.MapAnchor;
import com.quin.catancraft.map.MapDefinitionManager;
import com.quin.catancraft.map.TerritoryDefinition;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class MapAssetService {
    public record Result(boolean success, String message, int changedBlocks) {}

    private record Task(String assetId, MapAnchor anchor, String stateKey) {}

    private MapAssetService() {}

    @Nullable
    public static String requiredPlot(BuildingType type) {
        return SchematicAssetRegistry.requiredPlot(type);
    }

    public static Result activateTerritory(
            MinecraftServer server,
            CatanSavedData data,
            TerritoryDefinition territory,
            boolean prebuiltStartingCity
    ) {
        List<Task> tasks = new ArrayList<>();

        if (!prebuiltStartingCity) {
            addTerritoryTask(tasks, territory, "starter_settlement");
            addTerritoryTask(tasks, territory, "th1");
        }

        for (String assetId
                : SchematicAssetRegistry.rawProducerAssets(territory)) {
            addTerritoryTask(tasks, territory, assetId);
        }

        Result result =
                executeTasks(server, data, territory.dimension(), tasks, false);

        if (result.success() && prebuiltStartingCity) {
            recordPlacement(
                    data,
                    territoryKey(territory.id(), "city_base"),
                    "starter_settlement"
            );
            recordPlacement(
                    data,
                    territoryKey(territory.id(), "town_hall"),
                    "th1"
            );
            data.setDirty();
        }
        return result;
    }

    public static Result placeBuilding(
            MinecraftServer server,
            CatanSavedData data,
            TerritoryDefinition territory,
            BuildingType type
    ) {
        String assetId = SchematicAssetRegistry.buildingAsset(type);
        if (assetId == null) {
            return new Result(
                    true,
                    "No finished physical schematic exists for " +
                            type.displayName() + " yet; economy building is data-only.",
                    0
            );
        }

        SchematicAssetRegistry.Asset asset =
                SchematicAssetRegistry.asset(assetId);
        MapAnchor anchor =
                SchematicAssetRegistry.territoryAnchor(assetId, territory);
        if (asset == null || anchor == null) {
            return new Result(false,
                    "No valid physical placement contract for " + assetId + ".", 0);
        }

        String key = territoryKey(territory.id(), asset.plot());
        String existing = data.placedMapAsset(key);

        if (existing != null && !sameAsset(existing, assetId)) {
            return new Result(false,
                    "Physical plot " + asset.plot() +
                            " is already occupied by " + existing + ".", 0);
        }

        if (asset.placementToken().equals(existing)) {
            return new Result(true,
                    assetId + " is already placed at " + asset.plot() + ".", 0);
        }

        SchematicPlacementService.Result validation =
                SchematicPlacementService.validateAsset(assetId, anchor);
        if (!validation.success()) {
            return new Result(false, validation.message(), 0);
        }

        SchematicPlacementService.Result placement =
                SchematicPlacementService.placeAsset(
                        server,
                        territory.dimension(),
                        assetId,
                        anchor
                );
        if (!placement.success()) {
            return new Result(false, placement.message(), placement.changedBlocks());
        }

        data.setPlacedMapAsset(key, asset.placementToken());
        data.setDirty();
        return new Result(true, placement.message(), placement.changedBlocks());
    }

    public static Result upgradeTownHall(
            MinecraftServer server,
            CatanSavedData data,
            TerritoryDefinition territory,
            int cityLevel
    ) {
        String assetId = hallAsset(cityLevel);

        MapAnchor anchor =
                SchematicAssetRegistry.territoryAnchor(assetId, territory);
        if (anchor == null) {
            return new Result(false,
                    "No Town Hall placement contract for " + territory.id() + ".", 0);
        }

        SchematicPlacementService.Result validation =
                SchematicPlacementService.validateAsset(assetId, anchor);
        if (!validation.success()) {
            return new Result(false, validation.message(), 0);
        }

        SchematicPlacementService.Result placement =
                SchematicPlacementService.placeAsset(
                        server,
                        territory.dimension(),
                        assetId,
                        anchor
                );
        if (!placement.success()) {
            return new Result(false, placement.message(), placement.changedBlocks());
        }

        recordPlacement(
                data,
                territoryKey(territory.id(), "town_hall"),
                assetId
        );
        data.setDirty();
        return new Result(true, placement.message(), placement.changedBlocks());
    }

    /**
     * Installs the Visual Polish V3 central district base first, then all three
     * separately managed monuments. Existing legacy monument placement tokens
     * are treated as stale revisions and safely replaced.
     */
    public static Result placeMonuments(
            MinecraftServer server,
            CatanSavedData data
    ) {
        return placeMonuments(server, data, false);
    }

    public static Result placeMonuments(
            MinecraftServer server,
            CatanSavedData data,
            boolean force
    ) {
        List<Task> tasks = List.of(
                new Task(
                        "central_district_base",
                        SchematicAssetRegistry.fixedAnchor("central_district_base"),
                        "district:central_objective_district:base"
                ),
                new Task(
                        "industrial_complex",
                        SchematicAssetRegistry.monumentAnchor("industrial_complex"),
                        "monument:industrial:M1"
                ),
                new Task(
                        "military_depot",
                        SchematicAssetRegistry.monumentAnchor("military_depot"),
                        "monument:depot:M2"
                ),
                new Task(
                        "refinery_monument",
                        SchematicAssetRegistry.monumentAnchor("refinery_monument"),
                        "monument:refinery:M3"
                )
        );
        return executeTasks(
                server,
                data,
                "minecraft:overworld",
                tasks,
                force
        );
    }

    /**
     * Re-pastes only independently replaceable city assets plus the current
     * Town Hall tier. It intentionally NEVER re-pastes starter_settlement,
     * because doing so could erase purchased plot assets.
     */
    public static Result refreshOwnedCityVisuals(
            MinecraftServer server,
            CatanSavedData data
    ) {
        int changed = 0;
        int refreshed = 0;

        for (TerritoryData state : data.territories()) {
            if (state.ownerNationId() == null) continue;

            TerritoryDefinition definition =
                    MapDefinitionManager.territory(state.id());
            if (definition == null) continue;

            List<Task> tasks = new ArrayList<>();

            addTerritoryTask(
                    tasks,
                    definition,
                    hallAsset(state.cityLevel())
            );

            for (String assetId
                    : SchematicAssetRegistry.rawProducerAssets(definition)) {
                addTerritoryTask(tasks, definition, assetId);
            }

            for (BuildingInstance building : state.buildings()) {
                String assetId =
                        SchematicAssetRegistry.buildingAsset(building.type());
                if (assetId != null) {
                    addTerritoryTask(tasks, definition, assetId);
                }
            }

            Result result = executeTasks(
                    server,
                    data,
                    definition.dimension(),
                    tasks,
                    true
            );
            if (!result.success()) {
                return new Result(
                        false,
                        "Refresh stopped at " + definition.name() +
                                ": " + result.message(),
                        changed + result.changedBlocks()
                );
            }
            changed += result.changedBlocks();
            refreshed += tasks.size();
        }

        return new Result(
                true,
                "Refreshed " + refreshed +
                        " owned-city visual placements without repasting city bases.",
                changed
        );
    }

    private static String hallAsset(int cityLevel) {
        return switch (Math.max(1, cityLevel)) {
            case 1 -> "th1";
            case 2 -> "th2";
            default -> "th3";
        };
    }

    private static void addTerritoryTask(
            List<Task> tasks,
            TerritoryDefinition territory,
            String assetId
    ) {
        SchematicAssetRegistry.Asset asset =
                SchematicAssetRegistry.asset(assetId);
        MapAnchor anchor =
                SchematicAssetRegistry.territoryAnchor(assetId, territory);

        if (asset == null || anchor == null) {
            tasks.add(new Task(
                    assetId,
                    null,
                    territoryKey(territory.id(), "invalid:" + assetId)
            ));
            return;
        }

        tasks.add(new Task(
                assetId,
                anchor,
                territoryKey(territory.id(), asset.plot())
        ));
    }

    private static Result executeTasks(
            MinecraftServer server,
            CatanSavedData data,
            String dimensionId,
            List<Task> tasks,
            boolean force
    ) {
        for (Task task : tasks) {
            if (task.anchor() == null) {
                return new Result(false,
                        "Missing placement anchor for " + task.assetId() + ".", 0);
            }

            SchematicAssetRegistry.Asset asset =
                    SchematicAssetRegistry.asset(task.assetId());
            if (asset == null) {
                return new Result(false,
                        "Unknown placement asset " + task.assetId() + ".", 0);
            }

            String existing = data.placedMapAsset(task.stateKey());
            if (!force && asset.placementToken().equals(existing)) continue;

            if (!force && existing != null
                    && !sameAsset(existing, asset.id())) {
                return new Result(false,
                        task.stateKey() + " is already occupied by " +
                                existing + ".", 0);
            }

            SchematicPlacementService.Result validation =
                    SchematicPlacementService.validateAsset(
                            task.assetId(),
                            task.anchor()
                    );
            if (!validation.success()) {
                return new Result(false, validation.message(), 0);
            }
        }

        int changed = 0;
        for (Task task : tasks) {
            SchematicAssetRegistry.Asset asset =
                    SchematicAssetRegistry.asset(task.assetId());
            String existing = data.placedMapAsset(task.stateKey());

            if (!force && asset.placementToken().equals(existing)) continue;

            SchematicPlacementService.Result placement =
                    SchematicPlacementService.placeAsset(
                            server,
                            dimensionId,
                            task.assetId(),
                            task.anchor()
                    );
            if (!placement.success()) {
                return new Result(
                        false,
                        placement.message(),
                        changed + placement.changedBlocks()
                );
            }

            changed += placement.changedBlocks();
            data.setPlacedMapAsset(
                    task.stateKey(),
                    asset.placementToken()
            );
        }

        data.setDirty();
        return new Result(
                true,
                "Placed/synchronized " + tasks.size() +
                        " physical assets (" + changed + " changed blocks).",
                changed
        );
    }

    private static boolean sameAsset(String storedValue, String assetId) {
        if (storedValue == null || assetId == null) return false;
        return storedValue.equals(assetId)
                || storedValue.startsWith(assetId + "@r");
    }

    private static void recordPlacement(
            CatanSavedData data,
            String stateKey,
            String assetId
    ) {
        SchematicAssetRegistry.Asset asset =
                SchematicAssetRegistry.asset(assetId);
        data.setPlacedMapAsset(
                stateKey,
                asset == null ? assetId : asset.placementToken()
        );
    }

    private static String territoryKey(String territoryId, String slot) {
        return "territory:" + territoryId.toLowerCase() + ":" + slot.toLowerCase();
    }
}
