package com.quin.catancraft.world;

import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.*;
import java.io.IOException;
import java.util.*;

@GameTestHolder("catancraft_integration") @PrefixGameTestTemplate(false)
public final class PlannedWorldTests {
    private static final int FLAGS=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS;
    @GameTest(template="townhall_state_empty",timeoutTicks=600)
    public static void simulation_matches_known_shape_write_policy_and_delayed_state(GameTestHelper h) {
        BlockPos p=h.absolutePos(new BlockPos(8,5,8));
        for(BlockPos q:BlockPos.betweenClosed(p.offset(-2,-1,-2),p.offset(2,3,2)))h.getLevel().setBlock(q,Blocks.AIR.defaultBlockState(),FLAGS);
        BlockState fence=Blocks.SPRUCE_FENCE.defaultBlockState();h.getLevel().setBlock(p,fence,FLAGS);
        Map<BlockPos,BlockState> target=Map.of(p,fence,p.east(),Blocks.STONE.defaultBlockState());
        try {
            boolean strict=false;try{PlannedWorld.verify(h.getLevel(),target);}catch(IOException ex){strict=ex.getMessage().contains("neighbor update");}
            h.assertTrue(strict,"Normal neighbor-propagating placement should detect connection update");
            PlannedWorld.verify(h.getLevel(),target,FLAGS);
            boolean callback=false;try{PlannedWorld.verify(h.getLevel(),target,FLAGS|Block.UPDATE_NEIGHBORS);}catch(IOException ex){callback=true;}
            h.assertTrue(callback,"Known-shape flag alone must not mask requested neighbor callbacks");
            h.getLevel().setBlock(p.east(),Blocks.STONE.defaultBlockState(),FLAGS);
            h.runAfterDelay(40,()->{h.assertTrue(h.getLevel().getBlockState(p).equals(fence),"Actual known-shape placement changed authored fence connection");h.succeed();});
        }catch(IOException ex){throw new RuntimeException(ex);}
    }
    @GameTest(template="townhall_state_empty",timeoutTicks=600)
    public static void known_shape_policy_does_not_bypass_native_survival(GameTestHelper h) {
        BlockPos p=h.absolutePos(new BlockPos(8,6,8));h.getLevel().setBlock(p.below(),Blocks.STONE.defaultBlockState(),FLAGS);
        h.getLevel().setBlock(p,Blocks.LANTERN.defaultBlockState(),FLAGS);
        try {
            boolean refused=false;try{PlannedWorld.verify(h.getLevel(),Map.of(p,Blocks.LANTERN.defaultBlockState(),p.below(),Blocks.AIR.defaultBlockState()),FLAGS);}
            catch(IOException ex){refused=ex.getMessage().contains("support");}
            h.assertTrue(refused&&h.getLevel().getBlockState(p.below()).is(Blocks.STONE),"Flags-aware simulation bypassed survival or changed source");h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
}
