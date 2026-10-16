package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.map.MapRegion;
import com.quin.catancraft.map.TerritoryDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import javax.annotation.Nullable;

public final class CityRestorationService {
    public record Result(boolean success, String message) {}

    private static final long MAX_SNAPSHOT_BLOCKS = 4_000_000L;

    private CityRestorationService() {}

    public static Result capture(
            MinecraftServer server,
            TerritoryDefinition territory
    ) {
        MapRegion region = territory.restorationRegion();
        if (region == null) {
            return new Result(false,
                    territory.name() + " has no restorationRegion in map.json.");
        }

        long volume = volume(region);
        if (volume > MAX_SNAPSHOT_BLOCKS) {
            return new Result(false,
                    "Restoration region is too large for one snapshot (" +
                            volume + " blocks; max " + MAX_SNAPSHOT_BLOCKS + ").");
        }

        ServerLevel level = findLevel(server, territory.dimension());
        if (level == null) {
            return new Result(false,
                    "Dimension is not loaded: " + territory.dimension());
        }

        ResourceLocation snapshotId = snapshotId(territory);
        StructureTemplateManager manager = level.getStructureManager();
        StructureTemplate template = manager.getOrCreate(snapshotId);

        BlockPos start = new BlockPos(region.minX(), region.minY(), region.minZ());
        Vec3i size = new Vec3i(region.sizeX(), region.sizeY(), region.sizeZ());

        // Entities are intentionally excluded. The repair system restores the
        // physical city without duplicating players, mobs, vehicles, or drops.
        template.fillFromWorld(level, start, size, false, null);
        template.setAuthor("CatanCraft pre-siege snapshot");

        if (!manager.save(snapshotId)) {
            return new Result(false,
                    "Snapshot was captured in memory but could not be saved to disk.");
        }

        CatanCraft.LOGGER.info(
                "Captured restoration snapshot {} for {} ({} blocks)",
                snapshotId,
                territory.id(),
                volume
        );

        return new Result(true,
                "Captured " + territory.name() +
                        " restoration snapshot (" + volume + " blocks).");
    }

    public static Result restore(
            MinecraftServer server,
            TerritoryDefinition territory
    ) {
        MapRegion region = territory.restorationRegion();
        if (region == null) {
            return new Result(false,
                    territory.name() + " has no restorationRegion in map.json.");
        }

        ServerLevel level = findLevel(server, territory.dimension());
        if (level == null) {
            return new Result(false,
                    "Dimension is not loaded: " + territory.dimension());
        }

        ResourceLocation snapshotId = snapshotId(territory);
        StructureTemplateManager manager = level.getStructureManager();
        StructureTemplate template = manager.get(snapshotId).orElse(null);

        if (template == null) {
            return new Result(false,
                    "No saved restoration snapshot exists for " + territory.name() + ".");
        }

        BlockPos start = new BlockPos(region.minX(), region.minY(), region.minZ());
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setIgnoreEntities(true)
                .setKeepLiquids(false)
                .setKnownShape(false);

        boolean placed = template.placeInWorld(
                level,
                start,
                start,
                settings,
                RandomSource.create(),
                2
        );

        if (!placed) {
            return new Result(false,
                    "Minecraft could not place the saved city snapshot.");
        }

        CatanCraft.LOGGER.info(
                "Restored city snapshot {} for {}",
                snapshotId,
                territory.id()
        );

        return new Result(true,
                "Restored " + territory.name() + " to its captured pre-siege state.");
    }

    public static boolean hasSnapshot(
            MinecraftServer server,
            TerritoryDefinition territory
    ) {
        ServerLevel level = findLevel(server, territory.dimension());
        return level != null
                && level.getStructureManager().get(snapshotId(territory)).isPresent();
    }

    private static long volume(MapRegion region) {
        return (long) region.sizeX() * region.sizeY() * region.sizeZ();
    }

    private static ResourceLocation snapshotId(TerritoryDefinition territory) {
        return new ResourceLocation(
                CatanCraft.MOD_ID,
                "siege_snapshots/" + territory.id()
        );
    }

    @Nullable
    private static ServerLevel findLevel(
            MinecraftServer server,
            String dimensionId
    ) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(dimensionId)) {
                return level;
            }
        }
        return null;
    }
}
