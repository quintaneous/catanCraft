package com.quin.catancraft.map;

import com.quin.catancraft.data.ResourceType;
import com.quin.catancraft.data.TerritoryBoundary;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class TerritoryDefinition {
    public static final class BoundaryPoint {
        private int x;
        private int z;
        public int x() { return x; }
        public int z() { return z; }
    }

    private String id = "";
    private String name = "";
    private String specialty = "wood";
    private List<String> resources = new ArrayList<>();
    private Map<String, Integer> resourceYieldPercent = new LinkedHashMap<>();
    private String dimension = "minecraft:overworld";
    private List<BoundaryPoint> boundary = new ArrayList<>();
    private List<String> neighbors = new ArrayList<>();

    private int startSlot;
    private int productionPercent = 100;
    private boolean startsWithSettlement;
    private String settlementTemplate = "";
    @Nullable private MapAnchor settlementAnchor;
    @Nullable private MapAnchor cityCenter;
    @Nullable private MapAnchor townHall;
    @Nullable private MapAnchor managementAnchor;
    @Nullable private MapAnchor resourceSite;
    private List<MapAnchor> buildingPlots = new ArrayList<>();
    private List<String> reservedPlotIds = new ArrayList<>();
    private List<MapAnchor> defenseAnchors = new ArrayList<>();

    @Nullable private MapRegion siegeRegion;
    @Nullable private MapRegion restorationRegion;

    public String id() {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    public String name() {
        String clean = name == null ? "" : name.trim();
        return clean.isEmpty() ? id() : clean;
    }

    public String dimension() {
        String clean = dimension == null ? "" : dimension.trim();
        return clean.isEmpty() ? "minecraft:overworld" : clean;
    }

    public List<ResourceType> resources() {
        Set<ResourceType> result = new LinkedHashSet<>();
        if (resources != null) {
            for (String value : resources) {
                if (value == null || value.isBlank()) continue;
                result.add(ResourceType.parse(value));
            }
        }
        if (result.isEmpty()) {
            result.add(ResourceType.parse(specialty));
        }
        return List.copyOf(result);
    }

    public ResourceType specialty() {
        return resources().get(0);
    }

    public int resourceYieldPercent(ResourceType resource) {
        if (resourceYieldPercent == null) return 100;
        Integer value = resourceYieldPercent.get(resource.id());
        if (value == null) return 100;
        return Math.max(1, Math.min(500, value));
    }

    public int startSlot() { return Math.max(0, startSlot); }

    public int productionPercent() {
        return Math.max(1, productionPercent);
    }

    public boolean startsWithSettlement() { return startsWithSettlement; }

    public String settlementTemplate() {
        return settlementTemplate == null ? "" : settlementTemplate.trim();
    }

    public List<String> neighbors() {
        if (neighbors == null) return List.of();
        return neighbors.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    public List<BoundaryPoint> boundaryPoints() {
        return boundary == null ? List.of() : List.copyOf(boundary);
    }

    @Nullable public MapAnchor settlementAnchor() { return settlementAnchor; }
    @Nullable public MapAnchor cityCenter() { return cityCenter; }
    @Nullable public MapAnchor townHall() { return townHall; }
    @Nullable public MapAnchor managementAnchor() { return managementAnchor; }
    @Nullable public MapAnchor resourceSite() { return resourceSite; }

    public List<MapAnchor> buildingPlots() {
        return buildingPlots == null ? List.of() : List.copyOf(buildingPlots);
    }

    public Set<String> reservedPlotIds() {
        if (reservedPlotIds == null) return Set.of();
        return reservedPlotIds.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public List<MapAnchor> availableBuildingPlots() {
        Set<String> reserved = reservedPlotIds();
        return buildingPlots().stream()
                .filter(plot -> !reserved.contains(plot.id()))
                .toList();
    }

    public List<MapAnchor> defenseAnchors() {
        return defenseAnchors == null ? List.of() : List.copyOf(defenseAnchors);
    }

    @Nullable public MapRegion siegeRegion() { return siegeRegion; }
    @Nullable public MapRegion restorationRegion() { return restorationRegion; }

    public TerritoryBoundary toBoundary() {
        TerritoryBoundary result = new TerritoryBoundary(dimension());
        for (BoundaryPoint point : boundaryPoints()) {
            result.addPoint(point.x(), point.z());
        }
        return result;
    }

    public boolean contains(String dimensionId, int x, int z) {
        return toBoundary().contains(dimensionId, x, z);
    }

    @Nullable
    public MapAnchor anchor(String anchorId) {
        if (anchorId == null) return null;
        String wanted = anchorId.trim().toLowerCase(Locale.ROOT);
        if (wanted.equals("settlement") || wanted.equals("settlement_anchor")) return settlementAnchor;
        if (wanted.equals("city") || wanted.equals("city_center")) return cityCenter;
        if (wanted.equals("townhall") || wanted.equals("town_hall")) return townHall;
        if (wanted.equals("management") || wanted.equals("management_anchor")
                || wanted.equals("lectern")) return managementAnchor;
        if (wanted.equals("resource") || wanted.equals("resource_site")) return resourceSite;

        for (MapAnchor anchor : buildingPlots()) {
            if (wanted.equals(anchor.id())) return anchor;
        }
        for (MapAnchor anchor : defenseAnchors()) {
            if (wanted.equals(anchor.id())) return anchor;
        }
        return null;
    }
}
