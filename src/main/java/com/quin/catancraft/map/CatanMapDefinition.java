package com.quin.catancraft.map;

import java.util.ArrayList;
import java.util.List;

public final class CatanMapDefinition {
    private int format = 1;
    private List<TerritoryDefinition> territories = new ArrayList<>();
    private List<MonumentDefinition> monuments = new ArrayList<>();

    public int format() { return format; }

    public List<TerritoryDefinition> territories() {
        return territories == null ? List.of() : List.copyOf(territories);
    }

    public List<MonumentDefinition> monuments() {
        return monuments == null ? List.of() : List.copyOf(monuments);
    }
}
