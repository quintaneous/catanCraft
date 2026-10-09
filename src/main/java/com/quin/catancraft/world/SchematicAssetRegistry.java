package com.quin.catancraft.world;

import com.quin.catancraft.data.BuildingType;
import com.quin.catancraft.data.ResourceType;
import com.quin.catancraft.map.MapAnchor;
import com.quin.catancraft.map.TerritoryDefinition;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Physical asset placement contract generated from the Blender schematic handoff.
 *
 * The city-center-relative offsets below reproduce the exact fixed placements in
 * catancraft-schematic-manifest.json for all 16 River & Bridges V3 territories.
 * Keeping this registry separate from gameplay data means the visual pack can be
 * swapped without changing ownership/economy saves.
 */
public final class SchematicAssetRegistry {
    public record Bounds(BlockPos min, BlockPos max) {}

    public record Asset(
            String id,
            String template,
            String plot,
            int width,
            int height,
            int length,
            int anchorX,
            int anchorY,
            int anchorZ,
            boolean includesAir,
            Set<Integer> allowedRotations,
            int northOffsetX,
            int northOffsetZ
    ) {
        public Bounds boundsAt(MapAnchor anchor) {
            int minDx = -anchorX;
            int maxDx = width - 1 - anchorX;
            int minDz = -anchorZ;
            int maxDz = length - 1 - anchorZ;

            int[] xs = new int[] {minDx, minDx, maxDx, maxDx};
            int[] zs = new int[] {minDz, maxDz, minDz, maxDz};

            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxZ = Integer.MIN_VALUE;

            for (int i = 0; i < 4; i++) {
                int rx;
                int rz;
                switch (anchor.rotation()) {
                    case 90 -> {
                        rx = -zs[i];
                        rz = xs[i];
                    }
                    case 180 -> {
                        rx = -xs[i];
                        rz = -zs[i];
                    }
                    case 270 -> {
                        rx = zs[i];
                        rz = -xs[i];
                    }
                    default -> {
                        rx = xs[i];
                        rz = zs[i];
                    }
                }
                minX = Math.min(minX, anchor.x() + rx);
                maxX = Math.max(maxX, anchor.x() + rx);
                minZ = Math.min(minZ, anchor.z() + rz);
                maxZ = Math.max(maxZ, anchor.z() + rz);
            }

            return new Bounds(
                    new BlockPos(
                            minX,
                            anchor.y() - anchorY,
                            minZ
                    ),
                    new BlockPos(
                            maxX,
                            anchor.y() + (height - 1 - anchorY),
                            maxZ
                    )
            );
        }
    }

    private static final Map<String, Asset> ASSETS = new LinkedHashMap<>();

    static {
        add(new Asset(
                "starter_settlement", "starter_settlement_v2", "city_base",
                161, 47, 169, 80, 2, 1, true,
                Set.of(0, 180), 0, -83));
        add(new Asset(
                "th1", "thall1", "town_hall",
                75, 47, 65, 37, 2, 1, true,
                Set.of(0, 180), 0, -39));
        add(new Asset(
                "th2", "thall2", "town_hall",
                75, 47, 65, 37, 2, 1, true,
                Set.of(0, 180), 0, -39));
        add(new Asset(
                "th3", "thall3", "town_hall",
                75, 47, 65, 37, 2, 1, true,
                Set.of(0, 180), 0, -39));

        add(new Asset(
                "vehicle_factory", "vehiclefactory", "plot_1",
                31, 47, 31, 29, 2, 15, true,
                Set.of(0, 180), -45, -24));
        add(new Asset(
                "quarry", "quarry", "plot_2",
                31, 47, 31, 1, 8, 15, true,
                Set.of(0, 180), 45, -24));
        add(new Asset(
                "weapons_factory", "weaponsfactory", "plot_3",
                31, 47, 31, 29, 2, 15, true,
                Set.of(0, 180), -45, 16));
        add(new Asset(
                "steel_mill", "steel_mill", "plot_4",
                31, 47, 31, 1, 2, 15, true,
                Set.of(0, 180), 45, 16));
        add(new Asset(
                "farm", "farm", "plot_5",
                31, 47, 31, 15, 2, 1, true,
                Set.of(0, 180), -22, 44));
        add(new Asset(
                "lumberyard", "lumberyard", "plot_6",
                31, 47, 31, 15, 2, 1, true,
                Set.of(0, 180), 22, 44));

        add(new Asset(
                "iron_oil_site", "iron_oil_site", "strategic_resource_anchor",
                31, 23, 11, 15, 2, 0, true,
                Set.of(0, 180), 0, 85));
        add(new Asset(
                "coal_copper_site", "coal_copper_site", "strategic_resource_anchor",
                31, 23, 11, 15, 2, 0, true,
                Set.of(0, 180), 0, 85));

        // Current monument art. The visual-polish handoff will intentionally
        // replace these with larger PvP-focused contracts.
        add(new Asset(
                "industrial_complex", "industrialcomplex", "M1",
                41, 35, 41, 20, 2, 20, true,
                Set.of(0), 0, 0));
        add(new Asset(
                "military_depot", "militarydepot", "M2",
                41, 35, 41, 20, 2, 20, true,
                Set.of(0), 0, 0));
        add(new Asset(
                "refinery_monument", "refinerymonument", "M3",
                41, 35, 41, 20, 2, 20, true,
                Set.of(0), 0, 0));
    }

    private SchematicAssetRegistry() {}

    private static void add(Asset asset) {
        ASSETS.put(asset.id(), asset);
    }

    public static Collection<Asset> assets() {
        return List.copyOf(ASSETS.values());
    }

    @Nullable
    public static Asset asset(String id) {
        return id == null ? null : ASSETS.get(id.toLowerCase());
    }

    @Nullable
    public static String buildingAsset(BuildingType type) {
        return switch (type) {
            case VEHICLE_FACTORY -> "vehicle_factory";
            case WEAPONS_FACTORY -> "weapons_factory";
            case STEEL_MILL -> "steel_mill";
            default -> null;
        };
    }

    @Nullable
    public static String requiredPlot(BuildingType type) {
        String assetId = buildingAsset(type);
        Asset asset = asset(assetId);
        return asset == null ? null : asset.plot();
    }

    public static List<String> rawProducerAssets(TerritoryDefinition territory) {
        List<String> result = new ArrayList<>();
        List<ResourceType> resources = territory.resources();

        if (resources.contains(ResourceType.STONE)) result.add("quarry");
        if (resources.contains(ResourceType.AGRICULTURE)) result.add("farm");
        if (resources.contains(ResourceType.WOOD)) result.add("lumberyard");

        if (resources.contains(ResourceType.IRON)
                && resources.contains(ResourceType.OIL)) {
            result.add("iron_oil_site");
        }
        if (resources.contains(ResourceType.COAL)
                && resources.contains(ResourceType.COPPER)) {
            result.add("coal_copper_site");
        }
        return result;
    }

    @Nullable
    public static MapAnchor territoryAnchor(
            String assetId,
            TerritoryDefinition territory
    ) {
        Asset asset = asset(assetId);
        MapAnchor center = territory.cityCenter();
        if (asset == null || center == null) return null;

        int rotation = center.rotation();
        if (!asset.allowedRotations().contains(rotation)) return null;

        int x;
        int z;
        if (rotation == 180) {
            x = center.x() - asset.northOffsetX();
            z = center.z() - asset.northOffsetZ();
        } else if (rotation == 0) {
            x = center.x() + asset.northOffsetX();
            z = center.z() + asset.northOffsetZ();
        } else {
            return null;
        }

        return new MapAnchor(
                asset.plot(),
                x,
                81,
                z,
                rotation
        );
    }

    @Nullable
    public static MapAnchor monumentAnchor(String assetId) {
        return switch (assetId) {
            case "industrial_complex" ->
                    new MapAnchor("M1", -55, 81, 0, 0);
            case "military_depot" ->
                    new MapAnchor("M2", 0, 81, -55, 0);
            case "refinery_monument" ->
                    new MapAnchor("M3", 55, 81, 0, 0);
            default -> null;
        };
    }
}
