package com.quin.catancraft.monument;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.quin.catancraft.data.MonumentData;
import com.quin.catancraft.map.CatanMapDefinition;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Exercises capture's production admission decisions using the landmarks in the shipped map. */
public final class AuthoredAdmissionTest {
    private static final BooleanSupplier MATCHING = () -> true;
    private static final BooleanSupplier MISMATCH = () -> false;
    // Outdoor and legacy checks should never load additional chunks just to examine fingerprints.
    private static final BooleanSupplier DO_NOT_READ = () -> { throw new AssertionError("unexpected block read"); };

    public static void main(String[] args) throws Exception {
        var full = new Gson().fromJson(Files.readString(Path.of(args[0])), CatanMapDefinition.class);
        var audit = JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonObject();
        if (!full.mapId().equals(audit.get("mapId").getAsString())) throw new AssertionError("wrong field map");
        var covered = new HashSet<String>();
        for (var d : full.monuments()) {
            var site = new MonumentData(d.id(), d.name(), d.type(), d.dimension(), d.x(), d.y(), d.z(), d.radius());
            var survey = audit.getAsJsonObject("sites").getAsJsonObject(site.id());
            var nav = survey.getAsJsonArray("navigationXYZ");
            double x = at(nav, 0), y = nav.get(1).getAsDouble(), z = at(nav, 2);
            var interior = AuthoredCaptureFloors.navigation(site).orElseThrow();
            check(interior.getX() + .5 == x && interior.getY() == y && interior.getZ() + .5 == z, site, "navigation");
            check(MonumentManager.admits(full.mapId(), site, d.dimension(), x, y, z, MATCHING), site, "ground floor");
            check(MonumentManager.admits(full.mapId(), site, d.dimension(), x, y + 1.25, z, MATCHING), site, "jump");
            check(!MonumentManager.admits(full.mapId(), site, d.dimension(), x, y - .001, z, DO_NOT_READ), site, "below");
            check(!MonumentManager.admits(full.mapId(), site, d.dimension(), x, y + 2, z, DO_NOT_READ), site, "upstairs");
            check(!MonumentManager.admits(full.mapId(), site, "minecraft:the_end", x, y, z, DO_NOT_READ), site, "world");
            check(!MonumentManager.admits(full.mapId(), site, d.dimension(), x, y, z, MISMATCH), site, "wrong terrain");
            boolean oldAnchorInside = survey.get("legacyAnchorInside").getAsBoolean();
            check(MonumentManager.admits(full.mapId(), site, d.dimension(), d.x()+.5, d.y(), d.z()+.5,
                    oldAnchorInside ? MATCHING : DO_NOT_READ) == oldAnchorInside, site, "old outdoor anchor");
            check(MonumentManager.admits("season1_river_bridges_v3", site, d.dimension(), d.x()+.5, d.y(),
                    d.z()+.5, DO_NOT_READ), site, "legacy map region");

            var door = survey.getAsJsonArray("exteriorToInterior");
            for (int i = 0; i < 3; i++) {
                var step = door.get(i).getAsJsonArray();
                boolean inside = i == 2;
                check(MonumentManager.admits(full.mapId(), site, d.dimension(), at(step,0), step.get(1).getAsDouble(),
                        at(step,2), inside ? MATCHING : DO_NOT_READ) == inside, site, "doorway step " + i);
            }
            var moved = new MonumentData(d.id(), d.name(), d.type(), d.dimension(), d.x()+1, d.y(), d.z(), d.radius());
            check(!MonumentManager.admits(full.mapId(), moved, d.dimension(), x, y, z, DO_NOT_READ), site, "moved");
            var unknown = new MonumentData("unknown", d.name(), d.type(), d.dimension(), d.x(), d.y(), d.z(), d.radius());
            check(!MonumentManager.admits(full.mapId(), unknown, d.dimension(), x, y, z, DO_NOT_READ), site, "unknown");
            if (!covered.add(d.id())) throw new AssertionError("duplicate site " + d.id());
        }
        if (!covered.equals(Set.of("industrial","depot","refinery"))) throw new AssertionError("coverage " + covered);
        System.out.println("PASS all three production capture admission paths, nine doorway positions, floor height, "
                + "world and block mismatch, two former outdoor anchors, legacy boundaries and unknown monuments");
    }

    private static double at(JsonArray pos, int axis) { return pos.get(axis).getAsDouble() + .5; }

    private static void check(boolean condition, MonumentData site, String sample) {
        if (!condition) throw new AssertionError(site.id() + " " + sample);
    }
}
