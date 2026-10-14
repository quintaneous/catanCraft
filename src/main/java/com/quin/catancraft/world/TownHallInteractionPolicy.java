package com.quin.catancraft.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Source-verified normal-use compatibility, not a wildcard prefab overwrite.
 * Missing blocks are accepted ONLY at the two cells of a validated source door.
 * The other half must be matching door geometry (normal use allowed) or air.
 * Containers, fluid, geometry changes and missing non-door blocks are not allowed.
 * No world writes, item deletion, charge, chunk load or journal reset occurs here.
 */
public final class TownHallInteractionPolicy {
    private TownHallInteractionPolicy() {}

    public static boolean isInteractive(BlockState state) {
        return state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock;
    }

    public static boolean matches(BlockState actual, BlockState expected, boolean manager) {
        if (actual.equals(expected)) return true;
        if (actual.getBlock() != expected.getBlock()) return false;
        for (Property<?> p : expected.getProperties()) {
            if (manager && (p == LecternBlock.HAS_BOOK || p == LecternBlock.POWERED)) continue;
            if (isInteractive(expected) && ("open".equals(p.getName()) || "powered".equals(p.getName()))) continue;
            if (expected.getBlock() instanceof LeavesBlock && p == LeavesBlock.DISTANCE) continue;
            if (!sameProperty(actual, expected, p)) return false;
        }
        return true;
    }

    /** Validate both authored halves before making any missing-door allowance. */
    public static void validateDoorPairs(Map<BlockPos, BlockState> expectedDoors) {
        for (Map.Entry<BlockPos, BlockState> e : expectedDoors.entrySet()) {
            BlockState state = e.getValue();
            if (!(state.getBlock() instanceof DoorBlock))
                throw new IllegalStateException("Non-door in source door map at " + e.getKey());
            BlockPos otherPos = counterpart(e.getKey(), state);
            BlockState other = expectedDoors.get(otherPos);
            if (other == null || other.getBlock() != state.getBlock()
                    || other.getValue(DoorBlock.HALF) == state.getValue(DoorBlock.HALF)
                    || other.getValue(DoorBlock.FACING) != state.getValue(DoorBlock.FACING)
                    || other.getValue(DoorBlock.HINGE) != state.getValue(DoorBlock.HINGE)
                    || other.getValue(DoorBlock.OPEN) != state.getValue(DoorBlock.OPEN)
                    || other.getValue(DoorBlock.POWERED) != state.getValue(DoorBlock.POWERED))
                throw new IllegalStateException("Malformed authored Town Hall door pair at " + e.getKey());
        }
    }

    public static Set<BlockPos> missingDoorCells(Map<BlockPos, BlockState> expectedDoors,
                                                 Function<BlockPos, BlockState> actualAt) {
        validateDoorPairs(expectedDoors);
        Set<BlockPos> missing = new HashSet<>();
        for (Map.Entry<BlockPos, BlockState> e : expectedDoors.entrySet()) {
            BlockState actual = actualAt.apply(e.getKey());
            if (!actual.isAir()) continue;
            BlockPos otherPos = counterpart(e.getKey(), e.getValue());
            BlockState otherActual = actualAt.apply(otherPos);
            if (!otherActual.isAir() && !matches(otherActual, expectedDoors.get(otherPos), false))
                throw new IllegalStateException("Foreign block in missing Town Hall door pair at " + otherPos);
            missing.add(e.getKey());
        }
        return Set.copyOf(missing);
    }

    /**
     * New doors initialize both halves from the validated target pair. Retaining
     * just one old open half when its other half is missing would create an
     * inconsistent new pair. No old door needs closing before the purchase.
     * One-block gates/trapdoors can retain their compatible interaction state.
     */
    public static BlockState targetFor(BlockState actual, BlockState target) {
        if (target.getBlock() instanceof DoorBlock) return target;
        return isInteractive(target) && matches(actual, target, false) ? actual : target;
    }

    private static BlockPos counterpart(BlockPos p, BlockState state) {
        return state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? p.above() : p.below();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean sameProperty(BlockState a, BlockState b, Property p) {
        return a.getValue(p).equals(b.getValue(p));
    }
}
