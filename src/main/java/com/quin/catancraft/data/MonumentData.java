package com.quin.catancraft.data;

import net.minecraft.nbt.CompoundTag;
import javax.annotation.Nullable;
import java.util.UUID;

public final class MonumentData {
    private final String id;
    private final String name;
    private final MonumentType type;
    private final String dimensionId;
    private final int x;
    private final int y;
    private final int z;
    private final int radius;
    @Nullable private UUID capturingNationId;
    private int captureProgressTicks;

    public MonumentData(String id, String name, MonumentType type, String dimensionId,
                        int x, int y, int z, int radius) {
        this.id = id.toLowerCase();
        this.name = name;
        this.type = type;
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = Math.max(5, radius);
    }

    public String id() { return id; }
    public String name() { return name; }
    public MonumentType type() { return type; }
    public String dimensionId() { return dimensionId; }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public int radius() { return radius; }
    @Nullable public UUID capturingNationId() { return capturingNationId; }
    public void setCapturingNationId(@Nullable UUID value) { capturingNationId = value; }
    public int captureProgressTicks() { return captureProgressTicks; }
    public void setCaptureProgressTicks(int ticks) { captureProgressTicks = Math.max(0, ticks); }
    public void addCaptureProgressTicks(int ticks) { setCaptureProgressTicks(captureProgressTicks + ticks); }

    public void resetCapture() {
        capturingNationId = null;
        captureProgressTicks = 0;
    }

    public boolean contains(
            String dimension,
            double playerX,
            double playerY,
            double playerZ
    ) {
        if (!dimensionId.equals(dimension)) return false;

        double dx = playerX - (x + 0.5);
        double dz = playerZ - (z + 0.5);
        double dy = Math.abs(playerY - (y + 0.5));

        return dx * dx + dz * dz <= (double) radius * radius
                && dy <= 12.0;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("name", name);
        tag.putString("type", type.name());
        tag.putString("dimension", dimensionId);
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        tag.putInt("radius", radius);
        if (capturingNationId != null) tag.putUUID("capturingNationId", capturingNationId);
        tag.putInt("captureProgressTicks", captureProgressTicks);
        return tag;
    }

    public static MonumentData load(CompoundTag tag) {
        MonumentData data = new MonumentData(
                tag.getString("id"), tag.getString("name"),
                MonumentType.valueOf(tag.getString("type")),
                tag.getString("dimension"),
                tag.getInt("x"), tag.getInt("y"), tag.getInt("z"), tag.getInt("radius"));
        if (tag.hasUUID("capturingNationId")) {
            data.capturingNationId = tag.getUUID("capturingNationId");
        }
        data.captureProgressTicks = Math.max(0, tag.getInt("captureProgressTicks"));
        return data;
    }
}
