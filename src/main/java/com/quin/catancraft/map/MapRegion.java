package com.quin.catancraft.map;

import net.minecraft.core.BlockPos;

public final class MapRegion {
    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;

    public int minX() { return Math.min(minX, maxX); }
    public int minY() { return Math.min(minY, maxY); }
    public int minZ() { return Math.min(minZ, maxZ); }
    public int maxX() { return Math.max(minX, maxX); }
    public int maxY() { return Math.max(minY, maxY); }
    public int maxZ() { return Math.max(minZ, maxZ); }

    public int sizeX() { return maxX() - minX() + 1; }
    public int sizeY() { return maxY() - minY() + 1; }
    public int sizeZ() { return maxZ() - minZ() + 1; }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= minX() && pos.getX() <= maxX()
                && pos.getY() >= minY() && pos.getY() <= maxY()
                && pos.getZ() >= minZ() && pos.getZ() <= maxZ();
    }

    public boolean isReasonable() {
        return sizeX() > 0 && sizeY() > 0 && sizeZ() > 0
                && sizeX() <= 1024 && sizeY() <= 384 && sizeZ() <= 1024;
    }

    public String describe() {
        return "[" + minX() + "," + minY() + "," + minZ() + "] -> ["
                + maxX() + "," + maxY() + "," + maxZ() + "]";
    }
}
