package com.quin.catancraft.map;

public final class MapAnchor {
    private String id = "";
    private int x;
    private int y;
    private int z;
    private int rotation;

    public MapAnchor() {}

    public MapAnchor(
            String id,
            int x,
            int y,
            int z,
            int rotation
    ) {
        this.id = id == null ? "" : id;
        this.x = x;
        this.y = y;
        this.z = z;
        this.rotation = rotation;
    }

    public String id() {
        return id == null ? "" : id.trim().toLowerCase();
    }

    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }

    public int rotation() {
        int normalized = rotation % 360;
        return normalized < 0 ? normalized + 360 : normalized;
    }

    public String describe() {
        return "X=" + x + " Y=" + y + " Z=" + z + " rot=" + rotation();
    }
}
