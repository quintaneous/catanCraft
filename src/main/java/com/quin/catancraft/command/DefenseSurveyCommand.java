package com.quin.catancraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.quin.catancraft.map.MapAnchor;
import com.quin.catancraft.map.MapDefinitionManager;
import com.quin.catancraft.map.MapRegion;
import com.quin.catancraft.map.TerritoryDefinition;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;
import java.util.Locale;

/**
 * R4-A NON-DESTRUCTIVE reconnaissance. These are Blender R4 proposal bands,
 * not verified installation sockets. Neither this command nor the release
 * contains a way to buy/place an R4 defense module.
 */
public final class DefenseSurveyCommand {
    private record Band(String id, int minX, int maxX, int minZ, int maxZ,
                        boolean alternative) {}

    // North-settlement-local coordinates, inclusive. 160x168 south mirroring
    // is applied relative to the local city center (80,84) for candidate
    // reporting only. Grounded placement anchors must come from R4-A.
    private static final List<Band> BANDS = List.of(
            new Band("west_wall", -12, -3, 1, 167, false),
            new Band("east_wall", 163, 172, 1, 167, false),
            new Band("front_west_wall", -2, 67, -10, -2, false),
            new Band("front_east_wall", 93, 162, -10, -2, false),
            new Band("main_checkpoint_gate", 68, 92, -12, 0, false),
            new Band("rear_west_wall", -2, 59, 171, 179, false),
            new Band("rear_east_wall", 101, 162, 171, 179, false),
            new Band("front_west_tower", -15, -3, -12, 0, false),
            new Band("front_east_tower", 163, 175, -12, 0, false),
            new Band("rear_west_tower", -15, -3, 168, 180, false),
            new Band("rear_east_tower", 163, 175, 168, 180, false),
            new Band("west_bunker_alt", -15, -1, 96, 108, true),
            new Band("east_bunker_alt", 161, 175, 96, 108, true),
            new Band("front_west_trench_alt", 12, 28, -12, -2, true),
            new Band("front_east_trench_alt", 132, 148, -12, -2, true)
    );

    private DefenseSurveyCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("catan")
                .then(Commands.literal("defense")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("status")
                                .executes(ctx -> status(ctx.getSource())))
                        .then(Commands.literal("survey")
                                .then(Commands.argument("territory", StringArgumentType.word())
                                        .executes(ctx -> survey(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "territory")))))));
    }

    private static int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "R4 defenses: candidate reconnaissance only. No structures may be " +
                "purchased or pasted in this release. Run /catan defense survey <a-p>. " +
                "Results are NOT placement approvals."), false);
        return 1;
    }

    private static int survey(CommandSourceStack source, String requestedId) {
        String id = requestedId.trim().toLowerCase(Locale.ROOT);
        TerritoryDefinition definition = MapDefinitionManager.territory(id);
        if (definition == null || definition.cityCenter() == null) {
            source.sendFailure(Component.literal("Unknown city/territory or missing center: " + id));
            return 0;
        }
        MapAnchor center = definition.cityCenter();
        if (center.rotation() != 0 && center.rotation() != 180) {
            source.sendFailure(Component.literal(
                    "R4 preliminary survey supports only 0/180 degree cities."));
            return 0;
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                new ResourceLocation(definition.dimension()));
        ServerLevel level = source.getServer().getLevel(dimension);
        if (level == null) {
            source.sendFailure(Component.literal("City dimension not loaded."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "R4 READ-ONLY SURVEY / " + definition.name() + " [" + id +
                "], " + BANDS.size() + " proposed sockets. Candidate transforms " +
                "are PROVISIONAL, not approved world placements."), false);
        int flagged = 0;
        for (Band band : BANDS) {
            Site site = inspect(level, center, definition.siegeRegion(), band);
            if (site.flagged) flagged++;
            source.sendSuccess(() -> Component.literal(site.description), false);
        }
        final int flaggedCount = flagged;
        source.sendSuccess(() -> Component.literal(
                "R4 review: " + flaggedCount + "/" + BANDS.size() +
                " bands flagged. All require Blender R4-A anchor contracts, " +
                "per-voxel masks, world backups, and entity/vehicle clearance before placement. " +
                "No blocks changed."), false);
        return 1;
    }

    private record Site(boolean flagged, String description) {}

    private static Site inspect(ServerLevel level, MapAnchor center,
                                MapRegion siege, Band band) {
        int wx0 = worldX(center, band.minX);
        int wx1 = worldX(center, band.maxX);
        int wz0 = worldZ(center, band.minZ);
        int wz1 = worldZ(center, band.maxZ);
        int minX = Math.min(wx0, wx1), maxX = Math.max(wx0, wx1);
        int minZ = Math.min(wz0, wz1), maxZ = Math.max(wz0, wz1);
        int referenceY = center.y();

        boolean outsideSiege = siege == null;
        if (siege != null) {
            for (int x : new int[]{minX, maxX}) {
                for (int z : new int[]{minZ, maxZ}) {
                    if (!siege.contains(new BlockPos(x, referenceY, z)))
                        outsideSiege = true;
                }
            }
        }

        int unloaded = 0, fluid = 0, heightConflicts = 0, aboveGround = 0;
        // Sample at five-block intervals including edges; never load chunks.
        for (int x = minX; x <= maxX; x += 5) {
            for (int z = minZ; z <= maxZ; z += 5) {
                BlockPos pos = new BlockPos(x, referenceY, z);
                if (!level.hasChunkAt(pos)) { unloaded++; continue; }
                if (!level.getFluidState(pos).isEmpty()
                        || !level.getFluidState(pos.below()).isEmpty()) fluid++;
                int top = level.getHeight(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (Math.abs(top - referenceY) > 2) heightConflicts++;
                if (!level.getBlockState(pos.above(2)).isAir()) aboveGround++;
            }
        }
        // Cover the far corner too; all scans remain unloaded-safe.
        BlockPos last = new BlockPos(maxX, referenceY, maxZ);
        if (!level.hasChunkAt(last)) unloaded++;

        boolean risk = outsideSiege || unloaded > 0 || fluid > 0
                || heightConflicts > 0 || aboveGround > 0;
        String reasons = "";
        if (outsideSiege) reasons += " outside-siege-bounds";
        if (unloaded > 0) reasons += " unloaded=" + unloaded;
        if (fluid > 0) reasons += " fluid=" + fluid;
        if (heightConflicts > 0) reasons += " height-delta=" + heightConflicts;
        if (aboveGround > 0) reasons += " nonair-above-ground=" + aboveGround;
        String status = risk ? "REVIEW" : "SAMPLED (NOT APPROVED)";
        return new Site(risk, band.id + " [X " + minX + ".." + maxX +
                ", Z " + minZ + ".." + maxZ + "] " + status +
                (band.alternative ? " [alternative only]" : "") + reasons);
    }

    private static int worldX(MapAnchor center, int localX) {
        return center.x() + (center.rotation() == 180 ? 80 - localX : localX - 80);
    }

    private static int worldZ(MapAnchor center, int localZ) {
        return center.z() + (center.rotation() == 180 ? 84 - localZ : localZ - 84);
    }
}
