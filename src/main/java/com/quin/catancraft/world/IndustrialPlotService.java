package com.quin.catancraft.world;

import com.quin.catancraft.data.BuildingInstance;
import com.quin.catancraft.data.BuildingType;
import com.quin.catancraft.data.TerritoryData;
import com.quin.catancraft.map.MapAnchor;
import com.quin.catancraft.map.TerritoryDefinition;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

/** R3: independent factory geometry and existing V4 three-slot placement. */
public final class IndustrialPlotService {
    public static final List<String> INDUSTRIAL_PLOTS =
            List.of("plot_1", "plot_3", "plot_4");
    public static final Set<BuildingType> INDUSTRIES = Set.of(
            BuildingType.VEHICLE_FACTORY, BuildingType.WEAPONS_FACTORY,
            BuildingType.STEEL_MILL, BuildingType.TEXTILE_MILL,
            BuildingType.CHEMICAL_PLANT, BuildingType.MACHINE_SHOP,
            BuildingType.REFINERY, BuildingType.CONCRETE_PLANT,
            BuildingType.ELECTRONICS_FACTORY);

    private IndustrialPlotService() {}

    public static boolean isIndustry(BuildingType type) {
        return INDUSTRIES.contains(type);
    }

    /** Infer only three known pre-R3 physically pasted types. Never guess
     * a slot for old data-only factory purchases. */
    public static String occupiedPlot(BuildingInstance building) {
        if (!building.plotId().isBlank()) return building.plotId();
        return switch (building.type()) {
            case VEHICLE_FACTORY -> "plot_1";
            case WEAPONS_FACTORY -> "plot_3";
            case STEEL_MILL -> "plot_4";
            default -> "";
        };
    }

    public static boolean hasUnassignedLegacyIndustry(TerritoryData territory) {
        return territory.buildings().stream().anyMatch(
                building -> isIndustry(building.type())
                        && occupiedPlot(building).isBlank());
    }

    public static boolean occupied(TerritoryData territory, String plotId) {
        return territory.buildings().stream()
                .anyMatch(building -> plotId.equals(occupiedPlot(building)));
    }

    public static List<String> vacantPlots(TerritoryData territory,
                                           TerritoryDefinition definition) {
        return INDUSTRIAL_PLOTS.stream()
                .filter(plot -> definition.anchor(plot) != null)
                .filter(plot -> !definition.reservedPlotIds().contains(plot))
                .filter(plot -> !occupied(territory, plot))
                .toList();
    }

    @Nullable
    public static String originalPlot(BuildingType type) {
        return switch (type) {
            case VEHICLE_FACTORY -> "plot_1";
            case WEAPONS_FACTORY -> "plot_3";
            case STEEL_MILL -> "plot_4";
            default -> null;
        };
    }

    /**
     * The approved fixed roadside anchor is always the original plot anchor.
     * All new R3 masters face east; the pre-existing steel master faces west.
     * Plot 4 requires an extra 180-degree orientation change.
     */
    @Nullable
    public static MapAnchor anchorFor(BuildingType type, String plot,
                                      TerritoryDefinition territory) {
        if (!isIndustry(type) || !INDUSTRIAL_PLOTS.contains(plot))
            return null;
        String oldAsset = switch (plot) {
            case "plot_1" -> "vehicle_factory";
            case "plot_3" -> "weapons_factory";
            case "plot_4" -> "steel_mill";
            default -> null;
        };
        if (oldAsset == null) return null;
        MapAnchor original =
                SchematicAssetRegistry.territoryAnchor(oldAsset, territory);
        if (original == null) return null;
        int rot = territory.cityCenter().rotation();
        boolean westMaster = type == BuildingType.STEEL_MILL;
        // Original plot1 / plot3 masters are east-facing; plot4 is west.
        boolean requiresFlip = ("plot_4".equals(plot) != westMaster);
        if (requiresFlip) rot = (rot + 180) % 360;
        return new MapAnchor(plot, original.x(), original.y(),
                original.z(), rot);
    }
}
