package com.quin.catancraft.visual;

import com.google.gson.Gson;
import com.quin.catancraft.data.MonumentData;
import com.quin.catancraft.map.CatanMapDefinition;
import com.quin.catancraft.map.MapRegion;
import com.quin.catancraft.monument.AuthoredCaptureFloors;
import java.util.HashSet;
import java.util.Set;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/** Run against the packaged Full Map Review 02 configuration and the matching Forge classpath. */
public final class OutlineGeometryTest {
    public static void main(String[] args) throws Exception {
        CatanMapDefinition map = new Gson().fromJson(Files.readString(Path.of(args[0])), CatanMapDefinition.class);
        check(map.mapId().equals("catancraft_full_map_review_02"));
        check(map.monuments().size() == 3 && map.territories().size() == 16);
        List<OutlineShape> shapes = new ArrayList<>();
        for (var def : map.monuments()) {
            var site = new MonumentData(def.id(), def.name(), def.type(), def.dimension(),
                    def.x(), def.y(), def.z(), def.radius());
            var s = OutlineShape.monument(site, true);
            double x = (s.x0() + s.x1()) / 2, y = (s.y0() + s.y1()) / 2, z = (s.z0() + s.z1()) / 2;
            double r = (s.x1() - s.x0()) / 2;
            for (int i = 0; i < 96; i++) {
                double angle = i * Math.PI / 48;
                for (double d : new double[]{-.001, .001}) {
                    double px = x + (r + d) * Math.cos(angle), pz = z + (r + d) * Math.sin(angle);
                    check(site.contains(def.dimension(), px, y, pz) == (d < 0));
                }
            }
            check(site.contains(def.dimension(), x, s.y0(), z) && site.contains(def.dimension(), x, s.y1(), z));
            check(!site.contains(def.dimension(), x, s.y0() - .001, z));
            check(!site.contains(def.dimension(), x, s.y1() + .001, z));
            check(!site.contains("minecraft:the_nether", x, y, z));
            // Other maps use legacy circle geometry. On Full Map 02 authoritative authored cells replace it.
            var boxes = AuthoredCaptureFloors.boxes(site);
            List<OutlineShape> interiors = new ArrayList<>();
            for (var b : boxes) {
                interiors.add(OutlineShape.interior(true, b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()));
            }
            int expectedCells = switch (site.id()) {
                case "industrial" -> 1572;
                case "depot" -> 1241;
                case "refinery" -> 385;
                default -> throw new AssertionError("Unexpected Full Map 02 monument " + site.id());
            };
            check(verifyEdges(interiors) == expectedCells);
            shapes.addAll(interiors);
        }
        for (var city : map.territories()) {
            MapRegion region = city.siegeRegion();
            check(region != null && region.isReasonable());
            var s = OutlineShape.siege(region);
            for (int axis = 0; axis < 3; axis++) {
                int[] min = {(int)s.x0(), (int)s.y0(), (int)s.z0()};
                int[] max = {(int)s.x1() - 1, (int)s.y1() - 1, (int)s.z1() - 1};
                check(contains(region, min) && contains(region, max));
                min[axis]--;
                max[axis]++;
                check(!contains(region, min) && !contains(region, max));
            }
            check(s.near(s.x0() - 48, s.y0(), s.z0(), 48));
            check(!s.near(s.x0() - 48.001, s.y0(), s.z0(), 48));
            shapes.add(s);
        }
        shapes.add(OutlineShape.counter(true, 74, 82, -30));
        shapes.add(OutlineShape.counter(false, 28, 82, 75));
        check(shapes.stream().anyMatch(s -> s.kind() == OutlineShape.Kind.ACTIVE_INTERIOR));
        check(shapes.size() < ZoneOutlines.MAX_SHAPES);
        verifyEdges(List.of(OutlineShape.interior(false, 0,64,0, 2,64,0),
                OutlineShape.interior(false, 1,64,1, 2,64,2), OutlineShape.interior(false, 1,67,1, 2,67,2)));
        var snap = new ZoneOutlines.Snapshot("minecraft:overworld", shapes);
        FriendlyByteBuf wire = new FriendlyByteBuf(Unpooled.buffer());
        try {
            snap.encode(wire);
            check(snap.equals(ZoneOutlines.Snapshot.decode(wire)) && !wire.isReadable());
            wire.clear();
            wire.writeUtf("minecraft:overworld");
            wire.writeVarInt(ZoneOutlines.MAX_SHAPES + 1);
            try {
                ZoneOutlines.Snapshot.decode(wire);
                throw new AssertionError("Oversized packet accepted");
            } catch (IllegalArgumentException expected) {
                check(expected.getMessage().contains("count"));
            }
        } finally {
            wire.release();
        }
        System.out.println("PASS " + map.mapId() + ": authored capture floor edges, legacy cylinders, 16 inclusive siege boxes, 2 counter shapes, packet roundtrip and size bound");
    }

    private record Cell(int x, int y, int z) {}
    private record UnitEdge(boolean alongX, int fixed, int offset, int floor) {}

    private static int verifyEdges(List<OutlineShape> rooms) {
        Set<Cell> cells = new HashSet<>();
        for (var s : rooms) {
            for (int x = (int)s.x0(); x < s.x1(); x++) {
                for (int z = (int)s.z0(); z < s.z1(); z++) {
                    check(cells.add(new Cell(x, (int)s.y0(), z)));
                }
            }
        }
        Set<UnitEdge> expected = new HashSet<>(), actual = new HashSet<>();
        for (Cell c : cells) {
            if (!cells.contains(new Cell(c.x, c.y, c.z - 1))) expected.add(new UnitEdge(true, c.z, c.x, c.y));
            if (!cells.contains(new Cell(c.x, c.y, c.z + 1))) expected.add(new UnitEdge(true, c.z + 1, c.x, c.y));
            if (!cells.contains(new Cell(c.x - 1, c.y, c.z))) expected.add(new UnitEdge(false, c.x, c.z, c.y));
            if (!cells.contains(new Cell(c.x + 1, c.y, c.z))) expected.add(new UnitEdge(false, c.x + 1, c.z, c.y));
        }
        for (var e : InteriorEdges.build(rooms)) {
            for (int i = (int)e.begin(); i < e.end(); i++) {
                check(actual.add(new UnitEdge(e.alongX(), (int)e.fixed(), i, (int)e.floor())));
            }
        }
        check(actual.equals(expected));
        return cells.size();
    }

    private static boolean contains(MapRegion region, int[] p) {
        return region.contains(new BlockPos(p[0], p[1], p[2]));
    }

    private static void check(boolean result) {
        if (!result) throw new AssertionError();
    }
}
