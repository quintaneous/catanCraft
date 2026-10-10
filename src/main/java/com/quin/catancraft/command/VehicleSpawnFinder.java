package com.quin.catancraft.command;

import com.quin.catancraft.map.MapDefinitionManager;
import com.quin.catancraft.map.TerritoryDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Locate a safe ground-level parking space near the player.
 *
 * Do not use the MOTION_BLOCKING heightmap: a factory or garage roof
 * may be much higher than the pavement below it. Check a small, bounded
 * range around the player's feet instead.
 */
final class VehicleSpawnFinder {
    private static final int[] DISTANCES = {7, 9, 11, 13, 15};
    private static final int[] FLOOR_OFFSETS = {0, 1, -1, 2, -2};
    private static final int[] ANGLES = {0, 30, -30, 60, -60, 90, -90, 135, -135, 180};
    private static final double HORIZONTAL_MARGIN = 0.25;

    record Placement(int x, int y, int z) {}

    private VehicleSpawnFinder() {}

    static Placement find(ServerLevel level, ServerPlayer player,
                          Entity vehicle, TerritoryDefinition territory) {
        Vec3 look = player.getLookAngle();
        double horizontal = Math.hypot(look.x, look.z);
        double forwardX;
        double forwardZ;
        if (horizontal < 0.05) {
            double yaw = Math.toRadians(player.getYRot());
            forwardX = -Math.sin(yaw);
            forwardZ = Math.cos(yaw);
        } else {
            forwardX = look.x / horizontal;
            forwardZ = look.z / horizontal;
        }

        int playerFeet = (int) Math.floor(player.getY());

        // Prefer the direction the player faces, followed by slightly offset
        // deployment bays. The selection is deterministic for repeatable QA.
        for (int angle : ANGLES) {
            double radians = Math.toRadians(angle);
            double dirX = forwardX * Math.cos(radians) - forwardZ * Math.sin(radians);
            double dirZ = forwardX * Math.sin(radians) + forwardZ * Math.cos(radians);

            for (int distance : DISTANCES) {
                double targetX = Math.floor(player.getX() + dirX * distance) + 0.5;
                double targetZ = Math.floor(player.getZ() + dirZ * distance) + 0.5;
                for (int offset : FLOOR_OFFSETS) {
                    int floorY = playerFeet + offset;
                    vehicle.setPos(targetX, floorY + 0.1, targetZ);
                    vehicle.setYRot(player.getYRot());

                    if (isSafe(level, vehicle, territory, floorY)) {
                        return new Placement((int) Math.floor(targetX), floorY,
                                (int) Math.floor(targetZ));
                    }
                }
            }
        }
        return null;
    }

    private static boolean isSafe(ServerLevel level, Entity vehicle,
                                  TerritoryDefinition territory, int floorY) {
        AABB bounds = vehicle.getBoundingBox();
        if (bounds.getXsize() <= 0 || bounds.getZsize() <= 0
                || bounds.getYsize() <= 0) return false;

        // Collision margin may extend sideways and upward, but never below
        // ground. Expanding 0.25 downward rejected normal solid roads.
        AABB clearance = new AABB(
                bounds.minX - HORIZONTAL_MARGIN,
                bounds.minY + 0.01,
                bounds.minZ - HORIZONTAL_MARGIN,
                bounds.maxX + HORIZONTAL_MARGIN,
                bounds.maxY + 0.15,
                bounds.maxZ + HORIZONTAL_MARGIN);

        int minX = (int) Math.floor(bounds.minX + 0.01);
        int maxX = (int) Math.floor(bounds.maxX - 0.01);
        int minZ = (int) Math.floor(bounds.minZ + 0.01);
        int maxZ = (int) Math.floor(bounds.maxZ - 0.01);
        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;

        // All corners and the middle must have solid support at exactly the
        // same height. Don't spawn through overhangs, over holes or across
        // territory boundaries. Do not load remote chunks.
        for (int x : new int[]{minX, centerX, maxX}) {
            for (int z : new int[]{minZ, centerZ, maxZ}) {
                BlockPos support = new BlockPos(x, floorY - 1, z);
                BlockPos foot = support.above();
                if (!level.hasChunkAt(support)) return false;
                if (!territory.contains(level.dimension().location().toString(), x, z))
                    return false;
                BlockState floor = level.getBlockState(support);
                if (!floor.isFaceSturdy(level, support, Direction.UP)) return false;
                if (!level.getFluidState(support).isEmpty()
                        || !level.getFluidState(foot).isEmpty()) return false;
            }
        }

        // Disallow clipping into walls, parked vehicles or players.
        return level.noCollision(vehicle, clearance)
                && level.getEntities(vehicle, clearance).isEmpty();
    }
}
