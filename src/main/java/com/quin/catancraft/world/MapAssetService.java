package com.quin.catancraft.world;

import com.quin.catancraft.data.BuildingType;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.map.MapAnchor;
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
            // River & Bridges V3 already contains the base city and TH1.
            // Record them only after all added producer assets validated and placed.
            data.setPlacedMapAsset(
                    territoryKey(territory.id(), "city_base"),
                    "starter_settlement"
            );
            data.setPlacedMapAsset(
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
        if (existing != null && !existing.equals(assetId)) {
            return new Result(false,
                    "Physical plot " + asset.plot() +
                            " is already occupied by " + existing + ".", 0);
        }

        if (assetId.equals(existing)) {
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

        data.setPlacedMapAsset(key, assetId);
        data.setDirty();
        return new Result(true, placement.message(), placement.changedBlocks());
    }

    public static Result upgradeTownHall(
            MinecraftServer server,
            CatanSavedData data,
            TerritoryDefinition territory,
            int cityLevel
    ) {
        String assetId = switch (cityLevel) {
            case 1 -> "th1";
            case 2 -> "th2";
            default -> "th3";
        };

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

        data.setPlacedMapAsset(
                territoryKey(territory.id(), "town_hall"),
                assetId
        );
        data.setDirty();
        return new Result(true, placement.message(), placement.changedBlocks());
    }

    public static Result placeMonuments(
            MinecraftServer server,
            CatanSavedData data
    ) {
        List<Task> tasks = List.of(
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
                false
        );
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
        // Preflight every task before touching the world so a missing or stale
        // schematic cannot leave half of a city activated.
        for (Task task : tasks) {
            if (task.anchor() == null) {
                return new Result(false,
                        "Missing placement anchor for " + task.assetId() + ".", 0);
            }

            String existing = data.placedMapAsset(task.stateKey());
            if (!force && task.assetId().equals(existing)) continue;
            if (!force && existing != null && !existing.equals(task.assetId())) {
                return new Result(false,
                        task.stateKey() + " is already occupied by " + existing + ".", 0);
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
            String existing = data.placedMapAsset(task.stateKey());
            if (!force && task.assetId().equals(existing)) continue;

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
            data.setPlacedMapAsset(task.stateKey(), task.assetId());
        }

        data.setDirty();
        return new Result(
                true,
                "Placed/synchronized " + tasks.size() +
                        " physical assets (" + changed + " changed blocks).",
                changed
        );
    }

    private static String territoryKey(String territoryId, String slot) {
        return "territory:" + territoryId.toLowerCase() + ":" + slot.toLowerCase();
    }
}
