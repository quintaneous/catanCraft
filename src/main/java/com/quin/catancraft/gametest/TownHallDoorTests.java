package com.quin.catancraft.gametest;

import com.quin.catancraft.world.TownHallInteractionPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@GameTestHolder("catancraft")
@PrefixGameTestTemplate(false)
public final class TownHallDoorTests {
    private static final BlockPos LOW = new BlockPos(3, 1, 3);
    private static final int SAFE = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static Map<BlockPos, BlockState> pair(Direction facing) {
        BlockState lower = Blocks.DARK_OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing);
        return Map.of(LOW, lower, LOW.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }
    private static boolean allowed(Map<BlockPos, BlockState> source, Map<BlockPos, BlockState> actual) {
        try {
            Set<BlockPos> missing = TownHallInteractionPolicy.missingDoorCells(source, actual::get);
            return source.entrySet().stream().allMatch(e -> missing.contains(e.getKey())
                    || TownHallInteractionPolicy.matches(actual.get(e.getKey()), e.getValue(), false));
        } catch (IllegalStateException expected) { return false; }
    }
    @GameTest(template="door_policy_empty")
    public static void open_closed_powered_doors(GameTestHelper h) {
        for (Direction d: new Direction[]{Direction.NORTH,Direction.SOUTH})
            for (boolean open: new boolean[]{false,true})
                for (boolean power: new boolean[]{false,true}) {
                    Map<BlockPos,BlockState> source=pair(d), actual=new HashMap<>();
                    source.forEach((p,s)->actual.put(p,s.setValue(DoorBlock.OPEN,open).setValue(DoorBlock.POWERED,power)));
                    h.assertTrue(allowed(source,actual),"Normal door interaction refused "+d+"/"+open+"/"+power);
                }
        h.succeed();
    }
    @GameTest(template="door_policy_empty")
    public static void missing_halves_and_whole_pair(GameTestHelper h) {
        for(Direction d:new Direction[]{Direction.NORTH,Direction.SOUTH})
            for(int mask=1;mask<=3;mask++) {
                Map<BlockPos,BlockState> source=pair(d), actual=new HashMap<>(source);
                if((mask&1)!=0)actual.put(LOW,Blocks.AIR.defaultBlockState());
                if((mask&2)!=0)actual.put(LOW.above(),Blocks.AIR.defaultBlockState());
                h.assertTrue(allowed(source,actual),"Missing known door pair refused "+mask);
                h.assertTrue(TownHallInteractionPolicy.missingDoorCells(source,actual::get).size()==Integer.bitCount(mask),"Wrong missing count");
            }
        h.succeed();
    }
    @GameTest(template="door_policy_empty")
    public static void foreign_blocks_and_door_geometry_refused(GameTestHelper h) {
        Map<BlockPos,BlockState> source=pair(Direction.NORTH);
        BlockState[] invalid={Blocks.CHEST.defaultBlockState(),Blocks.STONE.defaultBlockState(),
                Blocks.WATER.defaultBlockState(),Blocks.SPRUCE_DOOR.defaultBlockState(),
                source.get(LOW).setValue(DoorBlock.HINGE,DoorHingeSide.RIGHT),
                source.get(LOW).setValue(DoorBlock.FACING,Direction.EAST),
                source.get(LOW).setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER)};
        for(BlockState bad:invalid){
            Map<BlockPos,BlockState> actual=new HashMap<>(source); actual.put(LOW,bad);
            h.assertTrue(!allowed(source,actual),"Foreign door-cell block accepted "+bad);
            actual.put(LOW.above(),Blocks.AIR.defaultBlockState());
            h.assertTrue(!allowed(source,actual),"Missing counterpart hid foreign block "+bad);
        }
        h.succeed();
    }
    @GameTest(template="door_policy_empty")
    public static void missing_walls_and_malformed_templates_refused(GameTestHelper h) {
        h.assertTrue(!TownHallInteractionPolicy.matches(Blocks.AIR.defaultBlockState(),Blocks.STONE.defaultBlockState(),false),"Missing wall accepted");
        Map<BlockPos,BlockState> broken=new HashMap<>(pair(Direction.NORTH));broken.remove(LOW.above());
        try{TownHallInteractionPolicy.validateDoorPairs(broken);throw new AssertionError("Orphan source door accepted");}
        catch(IllegalStateException expected){ /* required refusal */ }
        h.succeed();
    }
    @GameTest(template="door_policy_empty")
    public static void gates_trapdoors_and_manager_normal_use(GameTestHelper h) {
        BlockState trap=Blocks.OAK_TRAPDOOR.defaultBlockState();
        BlockState gate=Blocks.OAK_FENCE_GATE.defaultBlockState();
        h.assertTrue(TownHallInteractionPolicy.matches(trap.setValue(TrapDoorBlock.OPEN,true).setValue(TrapDoorBlock.POWERED,true),trap,false),"Trapdoor use refused");
        h.assertTrue(TownHallInteractionPolicy.matches(gate.setValue(FenceGateBlock.OPEN,true).setValue(FenceGateBlock.POWERED,true),gate,false),"Gate use refused");
        h.assertTrue(!TownHallInteractionPolicy.matches(trap.setValue(TrapDoorBlock.WATERLOGGED,true),trap,false),"Water intrusion accepted");
        BlockState lectern=Blocks.LECTERN.defaultBlockState();
        h.assertTrue(TownHallInteractionPolicy.matches(lectern.setValue(LecternBlock.HAS_BOOK,true),lectern,true),"Manager book refused");
        h.assertTrue(!TownHallInteractionPolicy.matches(lectern.setValue(LecternBlock.HAS_BOOK,true),lectern,false),"Secondary book silently discarded");
        h.succeed();
    }
    @GameTest(template="door_policy_empty")
    public static void surviving_door_keeps_interaction(GameTestHelper h) {
        BlockState closed=pair(Direction.NORTH).get(LOW), open=closed.setValue(DoorBlock.OPEN,true);
        h.assertTrue(TownHallInteractionPolicy.targetFor(open,closed).equals(open),"Surviving door was closed");
        h.assertTrue(TownHallInteractionPolicy.targetFor(Blocks.AIR.defaultBlockState(),closed).equals(closed),"Missing door target was lost");
        h.assertTrue(TownHallInteractionPolicy.targetFor(open,Blocks.STONE.defaultBlockState()).is(Blocks.STONE),"Old door prevented authored redesign");
        h.succeed();
    }
    @GameTest(template="door_policy_empty",timeoutTicks=80)
    public static void drop_suppressed_replacement_keeps_pair(GameTestHelper h) {
        BlockPos p=h.absolutePos(LOW);var level=h.getLevel();
        BlockState lower=pair(Direction.NORTH).get(LOW);
        level.setBlock(p.below(),Blocks.STONE.defaultBlockState(),SAFE);
        level.setBlock(p,lower,SAFE);
        level.setBlock(p.east(),Blocks.STONE.defaultBlockState(),SAFE);
        level.setBlock(p.above(),lower.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),SAFE);
        h.runAfterDelay(10,()->{
            h.assertTrue(level.getBlockState(p).equals(lower),"Lower door disappeared");
            h.assertTrue(level.getBlockState(p.above()).getBlock()==Blocks.DARK_OAK_DOOR,"Upper door disappeared");
            h.assertTrue(level.getEntitiesOfClass(ItemEntity.class,new AABB(p).inflate(2)).isEmpty(),"New door drop");
            h.succeed();
        });
    }
    @GameTest(template="door_policy_empty")
    public static void old_flags_can_remove_unpaired_door(GameTestHelper h) {
        BlockPos p=h.absolutePos(LOW);var level=h.getLevel();
        BlockState lower=pair(Direction.NORTH).get(LOW);
        level.setBlock(p.below(),Blocks.STONE.defaultBlockState(),SAFE);
        level.setBlock(p,lower,Block.UPDATE_CLIENTS);
        // A later source-air write/removal above the lower half can trigger
        // survival shape handling in an ordinary legacy bulk replacement.
        level.setBlock(p.above(),Blocks.STONE.defaultBlockState(),Block.UPDATE_CLIENTS);
        level.setBlock(p.above(),Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS);
        h.assertTrue(level.getBlockState(p).isAir(),"Legacy shape-loss mechanism not reproduced");
        h.succeed();
    }
}
