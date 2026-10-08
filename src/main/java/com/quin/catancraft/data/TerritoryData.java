package com.quin.catancraft.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class TerritoryData {
    private final String id;
    private String name;
    private ResourceType specialty;
    @Nullable
    private UUID ownerNationId;
    private int producerLevel = 1;
    private int cityLevel = 1;
    private final List<BuildingInstance> buildings = new ArrayList<>();
    private final Set<String> neighbors = new LinkedHashSet<>();
    @Nullable
    private TerritoryBoundary boundary;

    public TerritoryData(String id, String name, ResourceType specialty) {
        this.id = id;
        this.name = name;
        this.specialty = specialty;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public ResourceType specialty() {
        return specialty;
    }

    @Nullable
    public UUID ownerNationId() {
        return ownerNationId;
    }

    public void setOwnerNationId(@Nullable UUID ownerNationId) {
        this.ownerNationId = ownerNationId;
    }

    public int producerLevel() {
        return producerLevel;
    }

    public void setProducerLevel(int level) {
        producerLevel = Math.max(1, Math.min(5, level));
    }

    public int cityLevel() {
        return cityLevel;
    }

    public void setCityLevel(int level) {
        cityLevel = Math.max(1, Math.min(5, level));
    }

    public List<BuildingInstance> buildings() {
        return buildings;
    }

    public Set<String> neighbors() {
        return Set.copyOf(neighbors);
    }

    public void addNeighbor(String territoryId) {
        if (territoryId != null && !territoryId.equalsIgnoreCase(id)) {
            neighbors.add(territoryId.toLowerCase());
        }
    }

    @Nullable
    public TerritoryBoundary boundary() {
        return boundary;
    }

    public TerritoryBoundary ensureBoundary(String dimensionId) {
        if (boundary == null) {
            boundary = new TerritoryBoundary(dimensionId);
        }
        return boundary;
    }

    public void clearBoundary() {
        boundary = null;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("name", name);
        tag.putString("specialty", specialty.name());
        if (ownerNationId != null) tag.putUUID("ownerNationId", ownerNationId);
        tag.putInt("producerLevel", producerLevel);
        tag.putInt("cityLevel", cityLevel);

        ListTag buildingList = new ListTag();
        for (BuildingInstance building : buildings) {
            buildingList.add(building.save());
        }
        tag.put("buildings", buildingList);

        ListTag neighborList = new ListTag();
        for (String neighbor : neighbors) {
            neighborList.add(StringTag.valueOf(neighbor));
        }
        tag.put("neighbors", neighborList);

        if (boundary != null) {
            tag.put("boundary", boundary.save());
        }

        return tag;
    }

    public static TerritoryData load(CompoundTag tag) {
        TerritoryData territory = new TerritoryData(
                tag.getString("id"),
                tag.getString("name"),
                ResourceType.valueOf(tag.getString("specialty"))
        );

        if (tag.hasUUID("ownerNationId")) {
            territory.ownerNationId = tag.getUUID("ownerNationId");
        }

        territory.setProducerLevel(tag.getInt("producerLevel"));
        territory.setCityLevel(tag.getInt("cityLevel"));

        ListTag buildingList = tag.getList("buildings", Tag.TAG_COMPOUND);
        for (int i = 0; i < buildingList.size(); i++) {
            territory.buildings.add(BuildingInstance.load(buildingList.getCompound(i)));
        }

        ListTag neighborList = tag.getList("neighbors", Tag.TAG_STRING);
        for (int i = 0; i < neighborList.size(); i++) {
            territory.neighbors.add(neighborList.getString(i).toLowerCase());
        }

        if (tag.contains("boundary", Tag.TAG_COMPOUND)) {
            territory.boundary = TerritoryBoundary.load(tag.getCompound("boundary"));
        }

        return territory;
    }
}
