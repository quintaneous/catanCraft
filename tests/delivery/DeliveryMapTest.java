package com.quin.catancraft.client;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Checks the rendered route against the saved-world survey and two differently centred views. */
public final class DeliveryMapTest {
    public static void main(String[] args) throws Exception {
        var file = Path.of(args.length > 0 ? args[0] : "tools/delivery/route_audit.json");
        var audit = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        if (!audit.get("mapId").getAsString().equals("catancraft_full_map_review_02")) throw new AssertionError();
        var turns = audit.getAsJsonArray("loadedRouteTurnsXZ");
        var points = DeliveryMapPath.TURNS;
        eq(points.size(), turns.size());
        int length = 0;
        for (int i = 0; i < points.size(); i++) {
            var p = points.get(i);
            var coords = turns.get(i).getAsJsonArray();
            eq(p.x(), coords.get(0).getAsInt());
            eq(p.z(), coords.get(1).getAsInt());
            if (i > 0) {
                var q = points.get(i - 1);
                if ((q.x() == p.x()) == (q.z() == p.z())) throw new AssertionError("Invalid segment " + i);
                length += Math.abs(p.x() - q.x()) + Math.abs(p.z() - q.z());
            }
        }
        eq(281, length);
        for (var c : List.of(new DeliveryMapPath.Camera(-40, -68, 1, 0, 0),
                             new DeliveryMapPath.Camera(74.5, -28.5, 2.5, 80, 80))) {
            eq((int)c.centerX(), c.screenX(c.x()));
            eq((int)c.centerY(), c.screenY(c.z()));
        }
        System.out.println("PASS survey, 25 corners, 281 orthogonal steps and camera projection");
    }

    static void eq(int expected, int actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
