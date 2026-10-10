package com.quin.catancraft.data;

import net.minecraft.nbt.CompoundTag;

public final class BuildingInstance {
    private final BuildingType type;
    private int level;
    private long targetStock;
    private String plotId;

    public BuildingInstance(BuildingType type, int level, long targetStock) {
        this(type, level, targetStock, "");
    }

    public BuildingInstance(
            BuildingType type,
            int level,
            long targetStock,
            String plotId
    ) {
        this.type = type;
        this.level = Math.max(1, Math.min(5, level));
        this.targetStock = Math.max(0, targetStock);
        this.plotId = plotId == null ? "" : plotId.trim().toLowerCase();
    }

    public BuildingType type() {
        return type;
    }

    public int level() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(1, Math.min(5, level));
    }

    public long targetStock() {
        return targetStock;
    }

    public void setTargetStock(long targetStock) {
        this.targetStock = Math.max(0, targetStock);
    }

    public String plotId() {
        return plotId;
    }

    public void setPlotId(String plotId) {
        this.plotId = plotId == null ? "" : plotId.trim().toLowerCase();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", type.name());
        tag.putInt("level", level);
        tag.putLong("targetStock", targetStock);
        if (!plotId.isBlank()) tag.putString("plotId", plotId);
        return tag;
    }

    public static BuildingInstance load(CompoundTag tag) {
        return new BuildingInstance(
                BuildingType.valueOf(tag.getString("type")),
                tag.getInt("level"),
                tag.getLong("targetStock"),
                tag.getString("plotId")
        );
    }
}
