package com.quin.catancraft.delivery;

import com.quin.catancraft.data.MonumentType;
import com.quin.catancraft.map.MapDefinitionManager;
import com.quin.catancraft.map.MapZoneDefinition;
import com.quin.catancraft.map.MonumentDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.Optional;

/** Counter locations in the authored Full Map Review 02 overworld. */
public record DeliveryRoute(Endpoint pickup, Endpoint dropoff) {
    private static final String MAP = "catancraft_full_map_review_02";
    private static final String DIMENSION = "minecraft:overworld";
    private static final String DISTRICT = "central_objective_district";
    private static final BlockPos DEPOT_COUNTER = new BlockPos(74, 82, -30);
    private static final BlockPos REFINERY_COUNTER = new BlockPos(28, 82, 75);

    public record Endpoint(String key, String name, String dimension, int x, int y, int z) {
        public BlockPos counter() {
            return new BlockPos(x, y, z);
        }

        public boolean contains(String playerDimension, double playerX, double playerY, double playerZ) {
            double dx = playerX - (x + 0.5);
            double dy = playerY + 1.62 - (y + 0.5);
            double dz = playerZ - (z + 0.5);
            return dimension.equals(playerDimension) && dx * dx + dy * dy + dz * dz <= 4.5 * 4.5;
        }

        public boolean isClear(ServerLevel level) {
            BlockPos block = counter();
            if (!level.dimension().location().toString().equals(dimension) || !level.hasChunkAt(block)) return false;
            BlockPos feet;
            if (block.equals(DEPOT_COUNTER)) {
                var slab = level.getBlockState(block);
                if (!slab.is(Blocks.SPRUCE_SLAB) || slab.getValue(SlabBlock.TYPE) != SlabType.BOTTOM
                        || !level.getBlockState(block.below()).is(Blocks.SPRUCE_PLANKS)
                        || !level.getBlockState(block.west(2).below()).is(Blocks.STRIPPED_SPRUCE_LOG)) return false;
                feet = block.south().below();
                if (!level.getBlockState(feet.below()).is(Blocks.ANDESITE)) return false;
            } else if (block.equals(REFINERY_COUNTER)) {
                if (!level.getBlockState(block).is(Blocks.WAXED_EXPOSED_CUT_COPPER)
                        || !level.getBlockState(block.below()).is(Blocks.DEEPSLATE_BRICKS)
                        || !level.getBlockState(block.west().above()).is(Blocks.CHISELED_POLISHED_BLACKSTONE)) return false;
                feet = block.east().below();
                if (!level.getBlockState(feet.below()).is(Blocks.POLISHED_ANDESITE)) return false;
            } else {
                return false;
            }
            return level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()
                    && level.getBlockState(feet.above(2)).isAir();
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
        Endpoint pickup = surveyed(district, depot, "depot", "Meridian Airlift Depot", MonumentType.MILITARY_DEPOT,
                88, -35, DEPOT_COUNTER);
        Endpoint dropoff = surveyed(district, refinery, "refinery", "Copperline Refinery", MonumentType.REFINERY,
                22, 107, REFINERY_COUNTER);
        if (pickup == null || dropoff == null) return Optional.empty();
        return Optional.of(new DeliveryRoute(pickup, dropoff));
    }

    private static Endpoint surveyed(MapZoneDefinition district, MonumentDefinition site, String id, String name,
                                     MonumentType type, int monumentX, int monumentZ, BlockPos counter) {
        if (site == null || !id.equals(site.id()) || !name.equals(site.name()) || !DIMENSION.equals(site.dimension())
                || site.type() != type || site.x() != monumentX || site.y() != 81 || site.z() != monumentZ
                || site.radius() != 14 || !district.contains(DIMENSION, counter.getX(), counter.getZ())) return null;
        String key = MAP + "/courier-v2/" + id + "/" + counter.getX() + "/" + counter.getY() + "/" + counter.getZ();
        return new Endpoint(key, name, DIMENSION, counter.getX(), counter.getY(), counter.getZ());
    }
}
