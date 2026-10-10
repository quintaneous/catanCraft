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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic placement contracts for the Environment V4 asset set.
 *
 * Environment V4 keeps every existing gameplay anchor fixed and adds one
 * installation-only 193x53x193 environment schematic per territory.
 */
public final class SchematicAssetRegistry {
    public record Bounds(BlockPos min, BlockPos max) {}

    public record Asset(
            String id,
            String template,
            String plot,
            int revision,
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
        public String placementToken() {
            return id + "@r" + revision;
        }

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
        // Environment V4 installation patches. These paste before any city
        // structure on a site that is not already part of the authored V4 world.
        for (char territory = 'a'; territory <= 'p'; territory++) {
            String suffix = String.valueOf(territory);
            add(new Asset(
                    "city_environment_" + suffix,
                    "city_environment_" + suffix,
                    "city_environment",
                    4,
                    193, 53, 193,
                    96, 8, 13,
                    true,
                    Set.of(0, 180),
                    0, -83
            ));
        }

        // V4 city assets. Placement contracts are unchanged from V3, but the
        // schematic bytes changed, so their visual revision is bumped.
        add(new Asset(
                "starter_settlement", "starter_settlement_v2", "city_base", 4,
                161, 47, 169, 80, 2, 1, true,
                Set.of(0, 180), 0, -83));
        add(new Asset(
                "th1", "thall1", "town_hall", 4,
                75, 47, 65, 37, 2, 1, true,
                Set.of(0, 180), 0, -39));
        add(new Asset(
                "th2", "thall2", "town_hall", 4,
                75, 47, 65, 37, 2, 1, true,
                Set.of(0, 180), 0, -39));
        add(new Asset(
                "th3", "thall3", "town_hall", 4,
                75, 47, 65, 37, 2, 1, true,
                Set.of(0, 180), 0, -39));

        add(new Asset(
                "vehicle_factory", "vehiclefactory", "plot_1", 4,
                31, 47, 31, 29, 2, 15, true,
                Set.of(0, 180), -45, -24));
        add(new Asset(
                "quarry", "quarry", "plot_2", 4,
                31, 47, 31, 1, 8, 15, true,
                Set.of(0, 180), 45, -24));
        add(new Asset(
                "weapons_factory", "weaponsfactory", "plot_3", 4,
                31, 47, 31, 29, 2, 15, true,
                Set.of(0, 180), -45, 16));
        add(new Asset(
                "steel_mill", "steel_mill", "plot_4", 4,
                31, 47, 31, 1, 2, 15, true,
                Set.of(0, 180), 45, 16));
        add(new Asset(
                "farm", "farm", "plot_5", 4,
                31, 47, 31, 15, 2, 1, true,
                Set.of(0, 180), -22, 44));
        add(new Asset(
                "lumberyard", "lumberyard", "plot_6", 4,
                31, 47, 31, 15, 2, 1, true,
                Set.of(0, 180), 22, 44));

        // R3 six selectable industrial masters, designed for any of the
        // three approved city industrial plots (1, 3 and 4).
        // The asset.plot field is a default compatibility anchor only:
        // IndustrialPlotService chooses the actual purchased slot and rotation.
        add(new Asset("textile_mill", "textile_mill", "plot_1", 1,
                31, 47, 31, 29, 2, 15, true,
                Set.of(0, 180), -45, -24));
        add(new Asset("fuel_refinery", "fuel_refinery", "plot_1", 1,
                31, 47, 31, 29, 2, 15, true,
                Set.of(0, 180), -45, -24));
        add(new Asset("concrete_plant", "concrete_plant", "plot_1", 1,
                31, 47, 31, 29, 2, 15, true,
                Set.of(0, 180), -45, -24));
        add(new Asset("machine_shop", "machine_shop", "plot_1", 1,
                31, 47, 31, 29, 2, 15, true,
                Set.of(0, 180), -45, -24));
        add(new Asset("chemical_plant", "chemical_plant", "plot_1", 1,
                31, 47, 31, 29, 2, 15, true,
                Set.of(0, 180), -45, -24));
        add(new Asset("electronics_factory", "electronics_factory", "plot_1", 1,
                31, 47, 31, 29, 2, 15, true,
                Set.of(0, 180), -45, -24));

        // Strategic-site schematics are byte-identical to the accepted V3 set.
        add(new Asset(
                "iron_oil_site", "iron_oil_site", "strategic_resource_anchor", 3,
                31, 23, 11, 15, 2, 0, true,
                Set.of(0, 180), 0, 85));
        add(new Asset(
                "coal_copper_site", "coal_copper_site", "strategic_resource_anchor", 3,
                31, 23, 11, 15, 2, 0, true,
                Set.of(0, 180), 0, 85));

        // Environment V4 updates the central approach/base geometry while
        // retaining the Visual Polish V3 placement contract.
        add(new Asset(
                "central_district_base", "central_district_base",
                "central_objective_district", 2,
                301, 61, 301, 150, 8, 150, true,
                Set.of(0), 0, 0));

        add(new Asset(
                "industrial_complex", "industrialcomplex", "M1", 4,
                85, 47, 85, 42, 2, 42, true,
                Set.of(0), 0, 0));
        add(new Asset(
                "military_depot", "militarydepot", "M2", 4,
                85, 45, 77, 42, 2, 38, true,
                Set.of(0), 0, 0));
        add(new Asset(
                "refinery_monument", "refinerymonument", "M3", 4,
                79, 55, 89, 39, 2, 44, true,
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
        return id == null ? null : ASSETS.get(id.toLowerCase(Locale.ROOT));
    }

    @Nullable
    public static String buildingAsset(BuildingType type) {
        return switch (type) {
            case VEHICLE_FACTORY -> "vehicle_factory";
            case WEAPONS_FACTORY -> "weapons_factory";
            case STEEL_MILL -> "steel_mill";
            case TEXTILE_MILL -> "textile_mill";
            case REFINERY -> "fuel_refinery";
            case CONCRETE_PLANT -> "concrete_plant";
            case MACHINE_SHOP -> "machine_shop";
            case CHEMICAL_PLANT -> "chemical_plant";
            case ELECTRONICS_FACTORY -> "electronics_factory";
            default -> null;
        };
    }

    @Nullable
    public static String requiredPlot(BuildingType type) {
        String assetId = buildingAsset(type);
        Asset asset = asset(assetId);
        return asset == null ? null : asset.plot();
    }

    @Nullable
    public static String cityEnvironmentAsset(String territoryId) {
        if (territoryId == null || territoryId.isBlank()) return null;
        String id = "city_environment_" +
                territoryId.trim().toLowerCase(Locale.ROOT);
        return ASSETS.containsKey(id) ? id : null;
    }

    public static boolean isCityEnvironmentAsset(String assetId) {
        return assetId != null
                && assetId.toLowerCase(Locale.ROOT).startsWith("city_environment_");
    }

    @Nullable
    public static String environmentTerritoryId(String assetId) {
        if (!isCityEnvironmentAsset(assetId)) return null;
        String suffix = assetId.substring("city_environment_".length())
                .toLowerCase(Locale.ROOT);
        return suffix.length() == 1 && suffix.charAt(0) >= 'a'
                && suffix.charAt(0) <= 'p'
                ? suffix
                : null;
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
    public static MapAnchor environmentAnchor(TerritoryDefinition territory) {
        String assetId = cityEnvironmentAsset(territory.id());
        return assetId == null ? null : territoryAnchor(assetId, territory);
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
    public static MapAnchor fixedAnchor(String assetId) {
        return switch (assetId) {
            case "central_district_base" ->
                    new MapAnchor("central_objective_district", 0, 81, 0, 0);
            case "industrial_complex" ->
                    new MapAnchor("M1", -82, 81, -62, 0);
            case "military_depot" ->
                    new MapAnchor("M2", 82, 81, -58, 0);
            case "refinery_monument" ->
                    new MapAnchor("M3", 0, 81, 81, 0);
            default -> null;
        };
    }

    @Nullable
    public static MapAnchor monumentAnchor(String assetId) {
        return fixedAnchor(assetId);
    }

    public static boolean isFixedWorldAsset(String assetId) {
        return fixedAnchor(assetId) != null;
    }
}
