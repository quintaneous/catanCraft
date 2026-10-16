package com.quin.catancraft.world;

import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.map.InfrastructureDefinition;
import com.quin.catancraft.map.MapAnchor;
import com.quin.catancraft.map.MapDefinitionManager;
import com.quin.catancraft.map.TerritoryDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class WorldMapVerifier {
    public record Report(
            int checks,
            int passed,
            List<String> failures
    ) {
        public boolean success() {
            return failures.isEmpty();
        }
    }

    private WorldMapVerifier() {}

    public static Report verify(MinecraftServer server) {
        List<String> failures = new ArrayList<>();
        int[] counts = new int[] {0, 0};

        if (!"season1_river_bridges_v3".equals(MapDefinitionManager.mapId())) {
            failures.add(
                    "Loaded map id is '" + MapDefinitionManager.mapId() +
                            "', expected season1_river_bridges_v3.");
        }

        CatanSavedData data = CatanSavedData.get(server);
        boolean v4Baseline =
                MapAssetService.isEnvironmentV4World(server)
                        || MapAssetService.allEnvironmentMarkersPresent(data);

        check(
                counts,
                failures,
                "Environment V4 baseline",
                v4Baseline
        );

        ServerLevel overworld = server.overworld();

        // Environment V4 preserves the authored city-center surface at Y=80.
        for (TerritoryDefinition territory : MapDefinitionManager.territories()) {
            MapAnchor center = territory.cityCenter();
            if (center == null) continue;

            check(counts, failures,
                    "city pad " + territory.id(),
                    isSolidSurface(overworld, new BlockPos(
                            center.x(), center.y() - 1, center.z()
                    )));

            // The four authored starting settlements retain the exact
            // management lectern anchor.
            if (territory.startsWithSettlement()) {
                MapAnchor management = territory.managementAnchor();
                boolean lectern = management != null
                        && overworld.getBlockState(new BlockPos(
                                management.x(), management.y(), management.z()
                        )).is(Blocks.LECTERN);

                check(counts, failures,
                        "starter lectern " + territory.id(),
                        lectern);
            }
        }

        // V4 intentionally leaves all four bridge collision volumes unchanged.
        for (InfrastructureDefinition point : MapDefinitionManager.infrastructure()) {
            if (!"bridge".equals(point.type())) continue;
            check(counts, failures,
                    "bridge " + point.id(),
                    isSolidSurface(overworld, new BlockPos(
                            point.x(), point.y(), point.z()
                    )));
        }

        check(counts, failures,
                "solid central district",
                isSolidSurface(overworld, new BlockPos(0, 80, 0)));

        return new Report(counts[0], counts[1], List.copyOf(failures));
    }

    private static void check(
            int[] counts,
            List<String> failures,
            String name,
            boolean passed
    ) {
        counts[0]++;
        if (passed) {
            counts[1]++;
        } else {
            failures.add(name + " did not match the Environment V4 baseline.");
        }
    }

    private static boolean isSolidSurface(
            ServerLevel level,
            BlockPos pos
    ) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && state.getFluidState().isEmpty();
    }
}
