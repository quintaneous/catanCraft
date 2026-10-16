package com.quin.catancraft.map;

import com.quin.catancraft.data.TerritoryBoundary;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MapZoneDefinition {
    public static final class Point {
        private int x;
        private int z;
        public int x() { return x; }
        public int z() { return z; }
    }

    private String id = "";
    private String name = "";
    private String type = "public";
    private String dimension = "minecraft:overworld";
    private List<Point> boundary = new ArrayList<>();

    public String id() { return id == null ? "" : id.trim().toLowerCase(Locale.ROOT); }
    public String name() {
        String clean = name == null ? "" : name.trim();
        return clean.isEmpty() ? id() : clean;
    }
    public String type() { return type == null ? "" : type.trim().toLowerCase(Locale.ROOT); }
    public String dimension() {
        String clean = dimension == null ? "" : dimension.trim();
        return clean.isEmpty() ? "minecraft:overworld" : clean;
    }
    public List<Point> boundaryPoints() {
        return boundary == null ? List.of() : List.copyOf(boundary);
    }
    public TerritoryBoundary toBoundary() {
        TerritoryBoundary result = new TerritoryBoundary(dimension());
        for (Point point : boundaryPoints()) result.addPoint(point.x(), point.z());
        return result;
    }
    public boolean contains(String dimensionId, int x, int z) {
        return toBoundary().contains(dimensionId, x, z);
    }
}
