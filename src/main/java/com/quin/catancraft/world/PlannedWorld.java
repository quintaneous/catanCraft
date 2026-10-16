package com.quin.catancraft.world;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;

/** Read-only final-state survival and immediate neighbor-update simulation.
 * Native delayed-state tests remain necessary: this is not a lighting/modpack certificate.
 */
final class PlannedWorld {
    private PlannedWorld() {}
    static void verify(ServerLevel level,Map<net.minecraft.core.BlockPos,BlockState> states)throws IOException {
        verify(level,states,0);
    }
    static void verify(ServerLevel level,Map<net.minecraft.core.BlockPos,BlockState> states,int writeFlags)throws IOException {
        // Match actual placement effects, not hypothetical neighbor writes. Imported
        // authored connection states remain immutable under this existing write policy.
        boolean knownShapes=(writeFlags&Block.UPDATE_KNOWN_SHAPE)!=0&&(writeFlags&Block.UPDATE_NEIGHBORS)==0;
        Map<BlockPos,BlockState> plan=new HashMap<>();states.forEach((p,s)->plan.put(p.immutable(),s));
        Set<BlockPos> probes=new HashSet<>();
        for(var row:plan.entrySet())if(!row.getValue().equals(level.getBlockState(row.getKey()))) {
            probes.add(row.getKey());for(Direction d:Direction.values())probes.add(row.getKey().relative(d));
        }
        LevelAccessor view=(LevelAccessor)Proxy.newProxyInstance(LevelAccessor.class.getClassLoader(),new Class<?>[]{LevelAccessor.class},(proxy,method,args)->{
            String name=method.getName();
            if(name.equals("getBlockState"))return plan.getOrDefault((BlockPos)args[0],level.getBlockState((BlockPos)args[0]));
            if(name.equals("getFluidState"))return plan.getOrDefault((BlockPos)args[0],level.getBlockState((BlockPos)args[0])).getFluidState();
            // updateShape may request a later fluid tick. Do not schedule one in the real world.
            if(name.equals("scheduleTick"))return null;
            if(name.equals("setBlock")||name.equals("destroyBlock")||name.equals("removeBlock")||name.equals("addFreshEntity")
                ||name.equals("setBlockEntity")||name.equals("blockUpdated")||name.equals("levelEvent")||name.equals("gameEvent"))
                throw new IllegalStateException("Mutation attempted during construction simulation: "+name);
            if(method.isDefault())return InvocationHandler.invokeDefault(proxy,method,args);
            try{return method.invoke(level,args);}catch(InvocationTargetException ex){throw ex.getCause();}
        });
        for(BlockPos p:probes) {
            if(!level.hasChunkAt(p))throw new IOException("Unloaded construction support boundary at "+p);
            BlockState s=plan.getOrDefault(p,level.getBlockState(p));
            try {
                if(!s.canSurvive(view,p))throw new IOException("Planned block loses native support at "+p+": "+s);
                if(s.getBlock() instanceof FallingBlock && FallingBlock.isFree(view.getBlockState(p.below())))
                    throw new IOException("Planned gravity block lacks support at "+p);
                BlockState updated=knownShapes?s:Block.updateFromNeighbourShapes(s,view,p);
                if(!updated.equals(s))throw new IOException("Planned neighbor update would change exact state at "+p+": "+s+" -> "+updated);
            }catch(IOException ex){throw ex;}catch(RuntimeException ex){throw new IOException("Cannot prove native final support at "+p,ex);}
        }
    }
}
