package com.quin.catancraft.map;

import java.util.Locale;

public final class InfrastructureDefinition {
    private String id = "";
    private String name = "";
    private String type = "";
    private String dimension = "minecraft:overworld";
    private int x;
    private int y;
    private int z;

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
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
}
