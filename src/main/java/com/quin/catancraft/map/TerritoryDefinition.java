package com.quin.catancraft.map;

import com.quin.catancraft.data.ResourceType;
import com.quin.catancraft.data.TerritoryBoundary;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
    private String dimension = "minecraft:overworld";
    private List<BoundaryPoint> boundary = new ArrayList<>();
    private List<String> neighbors = new ArrayList<>();

    @Nullable private MapAnchor cityCenter;
    @Nullable private MapAnchor townHall;
    @Nullable private MapAnchor resourceSite;
    private List<MapAnchor> buildingPlots = new ArrayList<>();
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

    public ResourceType specialty() {
        return ResourceType.parse(specialty);
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

    @Nullable public MapAnchor cityCenter() { return cityCenter; }
    @Nullable public MapAnchor townHall() { return townHall; }
    @Nullable public MapAnchor resourceSite() { return resourceSite; }

    public List<MapAnchor> buildingPlots() {
        return buildingPlots == null ? List.of() : List.copyOf(buildingPlots);
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

        if (wanted.equals("city") || wanted.equals("city_center")) return cityCenter;
        if (wanted.equals("townhall") || wanted.equals("town_hall")) return townHall;
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
