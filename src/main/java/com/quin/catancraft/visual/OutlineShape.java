package com.quin.catancraft.visual;

import com.quin.catancraft.data.MonumentData;
import com.quin.catancraft.map.MapRegion;
import net.minecraft.network.FriendlyByteBuf;

/** All geometry uses world coordinates. Siege maxima are the far faces of inclusive block bounds. */
record OutlineShape(Kind kind, double x0, double y0, double z0, double x1, double y1, double z1) {
    enum Kind { ACTIVE_MONUMENT, INACTIVE_MONUMENT, SIEGE_SURVEY, DISPATCH, RECEIVING, ACTIVE_INTERIOR, INACTIVE_INTERIOR }

    static OutlineShape monument(MonumentData monument, boolean active) {
        double x = monument.x() + 0.5, y = monument.y() + 0.5, z = monument.z() + 0.5;
        double radius = monument.radius();
        return new OutlineShape(active ? Kind.ACTIVE_MONUMENT : Kind.INACTIVE_MONUMENT,
                x - radius, y - 12, z - radius, x + radius, y + 12, z + radius);
    }

    static OutlineShape interior(boolean active, int x0, int y0, int z0, int x1, int y1, int z1) {
        return new OutlineShape(active ? Kind.ACTIVE_INTERIOR : Kind.INACTIVE_INTERIOR,
                x0, y0, z0, x1 + 1.0, y1 + 1.0, z1 + 1.0);
    }

    static OutlineShape siege(MapRegion region) {
        return new OutlineShape(Kind.SIEGE_SURVEY,
                region.minX(), region.minY(), region.minZ(),
                region.maxX() + 1.0, region.maxY() + 1.0, region.maxZ() + 1.0);
    }

    static OutlineShape counter(boolean dispatch, int x, int y, int z) {
        return new OutlineShape(dispatch ? Kind.DISPATCH : Kind.RECEIVING,
                x, y, z, x + 1.0, y + 1.0, z + 1.0);
    }

    boolean near(double x, double y, double z, double range) {
        double dx = Math.max(Math.max(x0 - x, x - x1), 0.0);
        double dy = Math.max(Math.max(y0 - y, y - y1), 0.0);
        double dz = Math.max(Math.max(z0 - z, z - z1), 0.0);
        return dx * dx + dy * dy + dz * dz <= range * range;
    }

    void write(FriendlyByteBuf buf) {
        buf.writeEnum(kind);
        buf.writeDouble(x0); buf.writeDouble(y0); buf.writeDouble(z0);
        buf.writeDouble(x1); buf.writeDouble(y1); buf.writeDouble(z1);
    }

    static OutlineShape read(FriendlyByteBuf buf) {
        OutlineShape shape = new OutlineShape(buf.readEnum(Kind.class), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble());
        if (!(shape.x0 >= -30_000_512 && shape.x1 <= 30_000_512 && shape.x0 < shape.x1
                && shape.y0 >= -32_768 && shape.y0 < shape.y1 && shape.y1 <= 32_768
                && shape.z0 >= -30_000_512 && shape.z0 < shape.z1 && shape.z1 <= 30_000_512)) {
            throw new IllegalArgumentException("Invalid outline coordinates");
        }
        if ((shape.kind == Kind.ACTIVE_INTERIOR || shape.kind == Kind.INACTIVE_INTERIOR)
                && (shape.x1 - shape.x0 > 128 || shape.z1 - shape.z0 > 128)) {
            throw new IllegalArgumentException("Invalid capture floor span");
        }
        return shape;
    }
}
