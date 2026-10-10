package com.quin.catancraft.map;

import com.quin.catancraft.data.MonumentType;

import java.util.Locale;

public final class MonumentDefinition {
    private String id = "";
    private String name = "";
    private String type = "industrial_complex";
    private String dimension = "minecraft:overworld";
    private int x;
    private int y;
    private int z;
    private int radius = 25;

    public String id() {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    public String name() {
        String clean = name == null ? "" : name.trim();
        return clean.isEmpty() ? id() : clean;
    }

    public MonumentType type() {
        return MonumentType.parse(type);
    }

    public String dimension() {
        String clean = dimension == null ? "" : dimension.trim();
        return clean.isEmpty() ? "minecraft:overworld" : clean;
    }

    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public int radius() { return Math.max(5, radius); }
}
