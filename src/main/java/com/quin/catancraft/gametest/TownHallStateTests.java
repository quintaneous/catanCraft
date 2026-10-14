package com.quin.catancraft.gametest;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.map.MapAnchor;
import com.quin.catancraft.world.SchematicPlacementService;
import com.quin.catancraft.world.TownHallInteractionPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@GameTestHolder("catancraft")
@PrefixGameTestTemplate(false)
public final class TownHallStateTests {
    private static final int SAFE = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final BlockPos CENTER = new BlockPos(3, 2, 3);
    private static boolean matches(BlockState actual, BlockState source) {
        return TownHallInteractionPolicy.matches(actual, source, false);
    }

    @GameTest(template="door_policy_empty")
    public static void all_stair_shapes_both_halves_and_all_facings(GameTestHelper h) {
        for (Block block : new Block[]{Blocks.DEEPSLATE_TILE_STAIRS, Blocks.SANDSTONE_STAIRS,
                Blocks.SPRUCE_STAIRS, Blocks.DARK_OAK_STAIRS, Blocks.QUARTZ_STAIRS, Blocks.STONE_BRICK_STAIRS})
            for (Direction facing : Direction.Plane.HORIZONTAL)
                for (Half half : Half.values())
                    for (StairsShape shape : StairsShape.values()) {
                        BlockState expected = block.defaultBlockState().setValue(StairBlock.FACING, facing)
                                .setValue(StairBlock.HALF, half).setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);
                        h.assertTrue(matches(expected.setValue(StairBlock.SHAPE, shape), expected),
                                "Derived stair shape refused: " + expected + " -> " + shape);
                    }
        h.succeed();
    }

    @GameTest(template="door_policy_empty")
    public static void stair_material_facing_half_water_and_missing_still_refused(GameTestHelper h) {
        BlockState source = Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
        for (BlockState bad : new BlockState[]{
                Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH),
                source.setValue(StairBlock.FACING, Direction.NORTH), source.setValue(StairBlock.HALF, Half.TOP),
                source.setValue(StairBlock.WATERLOGGED, true), Blocks.AIR.defaultBlockState(),
                Blocks.CHEST.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.WATER.defaultBlockState()})
            h.assertTrue(!matches(bad, source), "Unsafe stair replacement accepted: " + bad);
        BlockState rail = Blocks.RAIL.defaultBlockState();
        h.assertTrue(!matches(rail.setValue(RailBlock.SHAPE, RailShape.ASCENDING_EAST), rail),
                "Unrelated property named shape was incorrectly ignored");
        h.succeed();
    }

    @GameTest(template="door_policy_empty")
    public static void fence_pane_wall_and_gate_connections(GameTestHelper h) {
        for (Block block : new Block[]{Blocks.DARK_OAK_FENCE, Blocks.SPRUCE_FENCE, Blocks.IRON_BARS, Blocks.GLASS_PANE}) {
            BlockState source = block.defaultBlockState();
            for (int mask = 0; mask < 16; mask++) {
                BlockState connected = source.setValue(BlockStateProperties.NORTH, (mask & 1) != 0)
                        .setValue(BlockStateProperties.EAST, (mask & 2) != 0)
                        .setValue(BlockStateProperties.SOUTH, (mask & 4) != 0)
                        .setValue(BlockStateProperties.WEST, (mask & 8) != 0);
                h.assertTrue(matches(connected, source), "Connection refused: " + connected);
                h.assertTrue(!matches(connected.setValue(BlockStateProperties.WATERLOGGED, true), source),
                        "Connection allowance hid water intrusion");
            }
        }
        BlockState wall = Blocks.COBBLESTONE_WALL.defaultBlockState();
        for (WallSide side : WallSide.values())
            for (boolean up : new boolean[]{false, true}) {
                BlockState connected = wall.setValue(WallBlock.UP, up).setValue(WallBlock.NORTH_WALL, side)
                        .setValue(WallBlock.EAST_WALL, side).setValue(WallBlock.SOUTH_WALL, side).setValue(WallBlock.WEST_WALL, side);
                h.assertTrue(matches(connected, wall), "Wall connection refused");
                h.assertTrue(!matches(connected.setValue(WallBlock.WATERLOGGED, true), wall), "Wet wall accepted");
            }
        BlockState gate = Blocks.OAK_FENCE_GATE.defaultBlockState();
        h.assertTrue(matches(gate.setValue(FenceGateBlock.IN_WALL, true).setValue(FenceGateBlock.OPEN, true), gate), "Gate in-wall/open refused");
        h.assertTrue(!matches(gate.setValue(FenceGateBlock.FACING, Direction.EAST), gate), "Gate rotation accepted");
        h.assertTrue(!matches(Blocks.CHEST.defaultBlockState(), Blocks.AIR.defaultBlockState()), "Foreign adjacent storage accepted");
        h.assertTrue(!matches(Blocks.SPRUCE_FENCE.defaultBlockState(), Blocks.DARK_OAK_FENCE.defaultBlockState()), "Fence material accepted");
        h.succeed();
    }

    @GameTest(template="door_policy_empty")
    public static void environment_properties_remain_narrow(GameTestHelper h) {
        BlockState leaves = Blocks.AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int distance = 1; distance <= 7; distance++)
            h.assertTrue(matches(leaves.setValue(LeavesBlock.DISTANCE, distance), leaves), "Leaf distance refused");
        h.assertTrue(!matches(leaves.setValue(LeavesBlock.PERSISTENT, false), leaves), "Leaf persistence edit accepted");
        h.assertTrue(!matches(leaves.setValue(LeavesBlock.WATERLOGGED, true), leaves), "Wet leaves accepted");
        BlockState rod = Blocks.LIGHTNING_ROD.defaultBlockState();
        h.assertTrue(matches(rod.setValue(LightningRodBlock.POWERED, true), rod), "Lightning state refused");
        h.assertTrue(!matches(rod.setValue(LightningRodBlock.FACING, Direction.NORTH), rod), "Rod rotation accepted");
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        h.assertTrue(matches(grass.setValue(SnowyDirtBlock.SNOWY, true), grass), "Snowy appearance refused");
        h.assertTrue(!matches(Blocks.DIRT.defaultBlockState(), grass), "Different terrain block accepted");
        h.succeed();
    }

    @GameTest(template="door_policy_empty")
    public static void native_stair_neighbor_update_reproduces_report(GameTestHelper h) {
        BlockPos p = h.absolutePos(CENTER);
        BlockState source = Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
        h.getLevel().setBlock(p, source, SAFE);
        h.getLevel().setBlock(p.south(), source.setValue(StairBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
        BlockState actual = h.getLevel().getBlockState(p);
        h.assertTrue(actual.getValue(StairBlock.SHAPE) == StairsShape.OUTER_LEFT, "Native outer_left mechanism not reproduced");
        h.assertTrue(matches(actual, source), "Screenshot state still refused");
        h.succeed();
    }

    @GameTest(template="door_policy_empty")
    public static void native_fence_neighbor_update_is_accepted(GameTestHelper h) {
        BlockPos p = h.absolutePos(CENTER);
        BlockState source = Blocks.DARK_OAK_FENCE.defaultBlockState();
        h.getLevel().setBlock(p, source, SAFE);
        h.getLevel().setBlock(p.east(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        BlockState actual = h.getLevel().getBlockState(p);
        h.assertTrue(actual.getValue(FenceBlock.EAST), "Native fence connection not reproduced");
        h.assertTrue(matches(actual, source), "Native fence connection refused");
        h.assertTrue(!matches(h.getLevel().getBlockState(p.east()), Blocks.AIR.defaultBlockState()),
                "Foreign neighbor must still fail its own envelope-cell comparison");
        h.succeed();
    }

    @GameTest(template="townhall_state_empty", batch="townhall_full", timeoutTicks=300)
    public static void full_th3_th4_th5_north_settle_without_false_edit_refusals(GameTestHelper h) {
        fullTier(h, 0, 3);
    }

    @GameTest(template="townhall_state_empty", batch="townhall_full", timeoutTicks=300)
    public static void full_th3_th4_th5_south_settle_without_false_edit_refusals(GameTestHelper h) {
        fullTier(h, 180, 3);
    }

    /** Native engine on complete authored envelopes, not mocked neighbor states.
     * This is physical/state regression, NOT the gated purchase/economy transaction.
     */
    private static void fullTier(GameTestHelper h, int rotation, int tier) {
        try {
            BlockPos origin = h.absolutePos(new BlockPos(2, 2, 2));
            MapAnchor anchor = new MapAnchor("state-test", origin.getX() + 37, origin.getY() + 2,
                    origin.getZ() + (rotation == 0 ? 1 : 63), rotation);
            List<SchematicPlacementService.PlanCell> cells = SchematicPlacementService.plannedCells("th" + tier, anchor);
            h.assertTrue(cells.size() == 229125, "Full authored envelope missing");
            for (var cell : cells)
                if (!h.getLevel().getBlockState(cell.position()).equals(cell.state()))
                    h.getLevel().setBlock(cell.position(), cell.state(), SAFE);
            int updated = 0;
            for (var cell : cells) {
                if (cell.state().isAir()) continue;
                BlockState normalized = Block.updateFromNeighbourShapes(cell.state(), h.getLevel(), cell.position());
                if (!normalized.equals(cell.state())) {
                    updated++;
                    h.getLevel().setBlock(cell.position(), normalized, SAFE);
                }
            }
            h.assertTrue(updated > 0, "Full fixture did not exercise native state changes");
            if (tier == 3) {
                var reported = cells.get((27 * 65 + 9) * 75 + 25);
                h.assertTrue(reported.state().getBlock() == Blocks.DEEPSLATE_TILE_STAIRS, "Reported cell not pinned to TH3 source");
                h.assertTrue(h.getLevel().getBlockState(reported.position()).getValue(StairBlock.SHAPE) == StairsShape.OUTER_LEFT,
                        "Reported source neighborhood did not produce outer_left");
            }
            CatanCraft.LOGGER.info("TH_STATE_FIXTURE tier={} rotation={} nativeNormalized={} cells={}", tier, rotation, updated, cells.size());
            assertEnvelope(h, cells, tier, rotation);
            h.runAfterDelay(5, () -> {
                assertEnvelope(h, cells, tier, rotation);
                if (tier < 5) fullTier(h, rotation, tier + 1);
                else h.succeed();
            });
        } catch (Exception error) {
            throw new AssertionError("Full Town Hall fixture failed at tier " + tier + " rotation " + rotation, error);
        }
    }

    private static void assertEnvelope(GameTestHelper h, List<SchematicPlacementService.PlanCell> cells, int tier, int rotation) {
        Map<BlockPos, BlockState> doors = new HashMap<>();
        for (var cell : cells) if (cell.state().getBlock() instanceof DoorBlock) doors.put(cell.position(), cell.state());
        Set<BlockPos> missing = TownHallInteractionPolicy.missingDoorCells(doors, p -> h.getLevel().getBlockState(p));
        for (var cell : cells) {
            BlockState actual = h.getLevel().getBlockState(cell.position());
            boolean manager = cell.localX() == 37 && cell.localY() == 5 && cell.localZ() == 17;
            if (!missing.contains(cell.position()) && !TownHallInteractionPolicy.matches(actual, cell.state(), manager))
                h.assertTrue(false, "TH" + tier + " rot=" + rotation + " unexpected edit at local " + cell.localX() + "," + cell.localY() + "," + cell.localZ()
                            + " expected " + cell.state() + " actual " + actual);
        }
    }
}
