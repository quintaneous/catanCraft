package com.quin.catancraft.delivery;

import com.quin.catancraft.data.MonumentType;
import com.quin.catancraft.map.MapDefinitionManager;
import com.quin.catancraft.map.MapZoneDefinition;
import com.quin.catancraft.map.MonumentDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;

/** Public foot courier route surveyed against the pristine Season 1 region files. */
public record DeliveryRoute(Endpoint pickup, Endpoint dropoff) {
    private static final String MAP = "season1_river_bridges_v3";
    private static final String DIMENSION = "minecraft:overworld";
    private static final String DISTRICT = "central_objective_district";

    public record Endpoint(String key, String name, String dimension, int x, int y, int z) {
        public boolean contains(String playerDimension, double playerX, double playerY, double playerZ) {
            double dx = playerX - (x + 0.5);
            double dz = playerZ - (z + 0.5);
            return dimension.equals(playerDimension) && dx * dx + dz * dz <= 4
                    && Math.abs(playerY - y) <= 0.5;
        }

        /** A changed or unloaded stop cannot accept a pickup or handoff. */
        public boolean isClear(ServerLevel level) {
            BlockPos feet = new BlockPos(x, y, z);
            return level.dimension().location().toString().equals(dimension) && level.hasChunkAt(feet)
                    && level.getBlockState(feet.below()).is(Blocks.GRAVEL)
                    && level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir();
        }
    }

    public static Optional<DeliveryRoute> current() {
        return resolve(MapDefinitionManager.mapId(), MapDefinitionManager.publicZone(DISTRICT),
                MapDefinitionManager.monument("depot"), MapDefinitionManager.monument("refinery"));
    }

    static Optional<DeliveryRoute> resolve(String mapId, MapZoneDefinition district,
                                           MonumentDefinition depot, MonumentDefinition refinery) {
        if (!MAP.equals(mapId) || district == null || !DISTRICT.equals(district.id())
                || !"public_objective".equals(district.type())) return Optional.empty();
        Endpoint pickup = surveyed(district, depot, "depot", MonumentType.MILITARY_DEPOT, 88, -35, 75, -16);
        Endpoint dropoff = surveyed(district, refinery, "refinery", MonumentType.REFINERY, 22, 107, 22, 89);
        if (pickup == null || dropoff == null) return Optional.empty();
        return Optional.of(new DeliveryRoute(pickup, dropoff));
    }

    private static Endpoint surveyed(MapZoneDefinition district, MonumentDefinition site, String id, MonumentType type,
                                     int monumentX, int monumentZ, int stopX, int stopZ) {
        if (site == null || !id.equals(site.id()) || !DIMENSION.equals(site.dimension()) || site.type() != type
                || site.x() != monumentX || site.y() != 81 || site.z() != monumentZ
                || site.radius() != 14 || !district.contains(DIMENSION, stopX, stopZ)) return null;
        String key = MAP + "/courier-v1/" + id + "/" + stopX + "/81/" + stopZ;
        return new Endpoint(key, site.name(), DIMENSION, stopX, 81, stopZ);
    }
}
