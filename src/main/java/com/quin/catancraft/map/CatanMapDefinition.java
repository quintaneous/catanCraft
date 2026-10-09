package com.quin.catancraft.map;

import java.util.ArrayList;
import java.util.List;

public final class CatanMapDefinition {
    private int format = 1;
    private String mapId = "";
    private List<TerritoryDefinition> territories = new ArrayList<>();
    private List<MapZoneDefinition> publicZones = new ArrayList<>();
    private List<InfrastructureDefinition> infrastructure = new ArrayList<>();
    private List<MonumentDefinition> monuments = new ArrayList<>();

    public int format() { return format; }
    public String mapId() { return mapId == null ? "" : mapId.trim(); }
    public List<TerritoryDefinition> territories() {
        return territories == null ? List.of() : List.copyOf(territories);
    }
    public List<MapZoneDefinition> publicZones() {
        return publicZones == null ? List.of() : List.copyOf(publicZones);
    }
    public List<InfrastructureDefinition> infrastructure() {
        return infrastructure == null ? List.of() : List.copyOf(infrastructure);
    }
    public List<MonumentDefinition> monuments() {
        return monuments == null ? List.of() : List.copyOf(monuments);
    }
}
