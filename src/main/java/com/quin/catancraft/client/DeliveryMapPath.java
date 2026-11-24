package com.quin.catancraft.client;

import java.util.List;

final class DeliveryMapPath {
    record Point(int x, int z) {}
    record Camera(double x, double z, double scale, double centerX, double centerY) {
        int screenX(double worldX) { return (int) Math.round(centerX + (worldX - x) * scale); }
        int screenY(double worldZ) { return (int) Math.round(centerY + (worldZ - z) * scale); }
    }

    // Block centres along the surveyed pedestrian approach, from pickup to hand-in.
    // The exact path is checked against tools/delivery/route_audit.json.
    static final List<Point> TURNS = List.of(
        new Point(74, -29), new Point(50, -29), new Point(50, -45), new Point(45, -45),
        new Point(45, -53), new Point(28, -53), new Point(28, -44), new Point(17, -44),
        new Point(17, -33), new Point(7, -33), new Point(7, -25), new Point(-3, -25),
        new Point(-3, -14), new Point(-8, -14), new Point(-8, -9), new Point(-13, -9),
        new Point(-13, 7), new Point(-11, 7), new Point(-11, 10), new Point(-6, 10),
        new Point(-6, 12), new Point(4, 12), new Point(4, 71), new Point(29, 71),
        new Point(29, 75)
    );

    private DeliveryMapPath() {}
}
