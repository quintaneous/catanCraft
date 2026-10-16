package com.quin.catancraft.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

public final class TerritoryBoundary {
    public record Point(int x, int z) {}

    private String dimensionId;
    private final List<Point> points = new ArrayList<>();

    public TerritoryBoundary(String dimensionId) {
        this.dimensionId = dimensionId;
    }

    public String dimensionId() {
        return dimensionId;
    }

    public List<Point> points() {
        return List.copyOf(points);
    }

    public void addPoint(int x, int z) {
        points.add(new Point(x, z));
    }

    public void clear() {
        points.clear();
    }

    public boolean isUsable() {
        return dimensionId != null && !dimensionId.isBlank() && points.size() >= 3;
    }

    public boolean contains(String dimension, int x, int z) {
        if (!isUsable() || !dimensionId.equals(dimension)) return false;

        double px = x + 0.5;
        double pz = z + 0.5;

        boolean inside = false;
        int j = points.size() - 1;
        for (int i = 0; i < points.size(); i++) {
            Point a = points.get(i);
            Point b = points.get(j);

            if (onSegment(px, pz, a.x(), a.z(), b.x(), b.z())) {
                return true;
            }

            boolean crosses = ((a.z() > pz) != (b.z() > pz))
                    && (px < (double) (b.x() - a.x()) * (pz - a.z())
                    / (double) (b.z() - a.z()) + a.x());

            if (crosses) inside = !inside;
            j = i;
        }

        return inside;
    }

    private static boolean onSegment(
            double px,
            double pz,
            double ax,
            double az,
            double bx,
            double bz
    ) {
        double cross = (px - ax) * (bz - az) - (pz - az) * (bx - ax);
        if (Math.abs(cross) > 0.0001) return false;

        double minX = Math.min(ax, bx) - 0.0001;
        double maxX = Math.max(ax, bx) + 0.0001;
        double minZ = Math.min(az, bz) - 0.0001;
        double maxZ = Math.max(az, bz) + 0.0001;
        return px >= minX && px <= maxX && pz >= minZ && pz <= maxZ;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", dimensionId == null ? "" : dimensionId);

        ListTag pointList = new ListTag();
        for (Point point : points) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("x", point.x());
            entry.putInt("z", point.z());
            pointList.add(entry);
        }
        tag.put("points", pointList);
        return tag;
    }

    public static TerritoryBoundary load(CompoundTag tag) {
        TerritoryBoundary boundary = new TerritoryBoundary(tag.getString("dimension"));
        ListTag pointList = tag.getList("points", Tag.TAG_COMPOUND);
        for (int i = 0; i < pointList.size(); i++) {
            CompoundTag point = pointList.getCompound(i);
            boundary.addPoint(point.getInt("x"), point.getInt("z"));
        }
        return boundary;
    }
}
