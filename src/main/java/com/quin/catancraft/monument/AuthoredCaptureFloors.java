package com.quin.catancraft.monument;

import com.google.gson.Gson;
import com.quin.catancraft.data.MonumentData;
import com.quin.catancraft.map.MapDefinitionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/** Ground-floor capture rooms surveyed from the authored Full Map Review 02 save. */
public final class AuthoredCaptureFloors {
    private static final String MAP = "catancraft_full_map_review_02";
    private static final String RESOURCE = "/data/catancraft/maps/full_review_02_capture_floors.json";
    private static final List<Site> SITES = load();

    private AuthoredCaptureFloors() {}

    /** Inclusive block indices. Player coordinates belong to half-open block volumes. */
    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public boolean contains(double x, double y, double z) {
            return x >= minX && x < maxX + 1 && y >= minY && y < maxY + 1 && z >= minZ && z < maxZ + 1;
        }
    }

    private record Point(int x, int y, int z) {
        BlockPos pos() { return new BlockPos(x, y, z); }
    }

    private record Pin(int x, int y, int z, String block) {
        boolean matches(ServerLevel level) {
            var pos = new BlockPos(x, y, z);
            return level.hasChunkAt(pos)
                    && block.equals(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString());
        }
    }

    private record Site(String id, String name, String type, String dimension, int x, int y, int z, int radius,
                        Point navigation, List<Pin> fingerprints, List<Box> boxes) {
        private Site {
            fingerprints = List.copyOf(fingerprints);
            boxes = List.copyOf(boxes);
        }

        boolean matches(MonumentData m) {
            return m != null && id.equals(m.id()) && name.equals(m.name()) && type.equals(m.type().id())
                    && dimension.equals(m.dimensionId()) && x == m.x() && y == m.y() && z == m.z() && radius == m.radius();
        }
    }

    private record Catalog(String mapId, List<Site> sites) {}

    private static List<Site> load() {
        try (var stream = AuthoredCaptureFloors.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing authored capture floor catalog");
            var catalog = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Catalog.class);
            if (!applies(catalog.mapId())) throw new IllegalStateException("Wrong authored capture floor map");
            return List.copyOf(catalog.sites());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read authored capture floor catalog", e);
        }
    }

    public static boolean applies(String mapId) {
        return MAP.equals(mapId);
    }

    private static Optional<Site> find(MonumentData monument) {
        return SITES.stream().filter(site -> site.matches(monument)).findFirst();
    }

    /** Surveyed geometry for an identified monument. */
    public static List<Box> boxes(MonumentData monument) {
        return find(monument).map(Site::boxes).orElse(List.of());
    }

    public static Optional<BlockPos> navigation(MonumentData monument) {
        return find(monument).map(site -> site.navigation().pos());
    }

    /** Check the active map and real local blocks before admitting players or showing a capture floor. */
    public static List<Box> boxes(ServerLevel level, MonumentData monument) {
        if (!applies(MapDefinitionManager.mapId())) return List.of();
        var site = find(monument).orElse(null);
        if (site == null || !level.dimension().location().toString().equals(site.dimension())) return List.of();
        for (var pin : site.fingerprints()) {
            if (!pin.matches(level)) return List.of();
        }
        return site.boxes();
    }
}
