package com.quin.catancraft.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;
import java.io.IOException;
import java.util.*;

/** Read-only native geometry preflight of the exact remaining write order.
 * Plot/read envelopes are not exclusion zones. Every intermediate shape, including
 * contextual neighbor shapes, must preserve occupied space, footing and attachments.
 * Entities are never moved, removed, collected or rewritten by this class.
 */
final class ConstructionHazards {
    record Write(BlockPos pos, BlockState target) {
        Write { pos=pos.immutable();Objects.requireNonNull(target); }
    }
    private record Saved(Entity entity,CompoundTag nbt) {}
    static final class Guard {
        private final List<Saved> saved;
        private final ServerLevel level;private final AABB bounds;
        private Guard(ServerLevel level,AABB bounds,List<Saved> saved){this.level=level;this.bounds=bounds;this.saved=List.copyOf(saved);}
        void verify() throws IOException {
            if(bounds!=null) {
                Set<UUID> expected=new HashSet<>();for(Saved s:saved)expected.add(s.entity().getUUID());
                Set<UUID> actual=new HashSet<>();for(Entity e:level.getEntities((Entity)null,bounds))actual.add(e.getUUID());
                if(!actual.equals(expected))throw new IOException("Construction entity set changed after preflight; no further writes permitted");
            }
            for(Saved s:saved)if(s.entity().isRemoved() || !s.nbt().equals(s.entity().saveWithoutId(new CompoundTag())))
                throw new IOException("Construction changed entity identity/state: "+s.entity().getUUID());
        }
    }
    private ConstructionHazards() {}
    static Guard inspect(ServerLevel level,List<Write> ordered) throws IOException {
        if(ordered.isEmpty())return new Guard(level,null,List.of());
        AABB bounds=null;
        for(Write w:ordered) {
            if(!level.hasChunkAt(w.pos()))throw new IOException("Unloaded construction chunk at "+w.pos());
            AABB b=new AABB(w.pos()).inflate(2);bounds=bounds==null?b:bounds.minmax(b);
        }
        // Native query intersects full bounds, including vehicles whose center is outside.
        List<Entity> entities=level.getEntities((Entity)null,bounds);
        List<Saved> saved=new ArrayList<>();
        for(Entity e:entities)saved.add(new Saved(e,e.saveWithoutId(new CompoundTag()).copy()));
        Overlay view=new Overlay(level);
        for(Write w:ordered) {
            BlockState old=view.getBlockState(w.pos());if(old.equals(w.target()))continue;
            Set<BlockPos> probes=new LinkedHashSet<>();probes.add(w.pos());
            for(Direction d:Direction.values())probes.add(w.pos().relative(d));
            Map<Entity,Map<BlockPos,VoxelShape>> previous=new IdentityHashMap<>();
            Map<BlockPos,Boolean> railSupport=new HashMap<>();
            for(BlockPos p:probes)if(view.getBlockState(p).getBlock() instanceof BaseRailBlock)
                railSupport.put(p,view.getBlockState(p.below()).isFaceSturdy(view,p.below(),Direction.UP));
            for(Entity e:entities) {
                // Shape changes farther away cannot affect this entity or its attachment.
                if(!e.getBoundingBox().inflate(2).intersects(new AABB(w.pos())))continue;
                Map<BlockPos,VoxelShape> shapes=new LinkedHashMap<>();
                for(BlockPos p:probes) {
                    if(!level.hasChunkAt(p))throw new IOException("Unloaded construction boundary at "+p);
                    shapes.put(p,shape(view,p,e));
                }
                previous.put(e,shapes);
            }
            view.states.put(w.pos(),w.target());
            for(var entry:previous.entrySet()) {
                Entity e=entry.getKey();AABB box=e.getBoundingBox();
                VoxelShape body=Shapes.create(box.deflate(1.0e-7));
                VoxelShape feet=Shapes.create(new AABB(box.minX,box.minY-0.0625,box.minZ,box.maxX,box.minY+1.0e-6,box.maxZ));
                for(var probe:entry.getValue().entrySet()) {
                    BlockPos p=probe.getKey();VoxelShape before=probe.getValue(),after=shape(view,p,e);
                    VoxelShape added=Shapes.join(before,after,BooleanOp.ONLY_SECOND);
                    VoxelShape removed=Shapes.join(before,after,BooleanOp.ONLY_FIRST);
                    if(Shapes.joinIsNotEmpty(added,body,BooleanOp.AND))throw hazard(e,p,"new collision intersects occupied bounds");
                    if(Shapes.joinIsNotEmpty(removed,feet,BooleanOp.AND))throw hazard(e,p,"support beneath entity would be removed or lowered");
                }
                if((unsafeMaterial(old)||unsafeMaterial(w.target())) && box.inflate(0.0625).intersects(new AABB(w.pos())))
                    throw hazard(e,w.pos(),"unsafe material/contact semantics would change");
                if(e instanceof AbstractMinecart)for(var rail:railSupport.entrySet())
                    if(rail.getValue()&&!view.getBlockState(rail.getKey().below()).isFaceSturdy(view,rail.getKey().below(),Direction.UP)
                        && box.inflate(0.25).intersects(new AABB(rail.getKey())))
                        throw hazard(e,rail.getKey(),"rail support would be removed");
                // Hanging/leashed objects have semantic support requirements beyond shape.
                // Unknown attachment changes are refused locally, even for equal solid shapes.
                if((e instanceof HangingEntity || e instanceof LeashFenceKnotEntity)
                    && box.inflate(1.01).intersects(new AABB(w.pos())))
                    throw hazard(e,w.pos(),"attachment support requires unchanged local state");
                if(e instanceof AbstractMinecart && (old.getBlock() instanceof BaseRailBlock || w.target().getBlock() instanceof BaseRailBlock)
                    && box.inflate(0.1).intersects(new AABB(w.pos())))
                    throw hazard(e,w.pos(),"rail attachment/path would change");
                if(!old.getFluidState().equals(w.target().getFluidState()) && box.inflate(0.0625).intersects(new AABB(w.pos())))
                    throw hazard(e,w.pos(),"fluid support or occupied medium would change");
            }
        }
        Guard guard=new Guard(level,bounds,saved);guard.verify();return guard;
    }
    private static boolean unsafeMaterial(BlockState s) {
        return s.is(Blocks.FIRE)||s.is(Blocks.SOUL_FIRE)||s.is(Blocks.MAGMA_BLOCK)||s.is(Blocks.CACTUS)
            ||s.is(Blocks.COBWEB)||s.is(Blocks.POWDER_SNOW)||s.is(Blocks.SWEET_BERRY_BUSH)
            ||s.is(Blocks.CAMPFIRE)||s.is(Blocks.SOUL_CAMPFIRE)||s.is(Blocks.WITHER_ROSE);
    }
    private static VoxelShape shape(Overlay view,BlockPos p,Entity e)throws IOException {
        try{return view.getBlockState(p).getCollisionShape(view,p,CollisionContext.of(e)).move(p.getX(),p.getY(),p.getZ());}
        catch(RuntimeException ex){throw new IOException("Cannot prove safe native shape at "+p,ex);}
    }
    private static IOException hazard(Entity e,BlockPos p,String reason) {
        return new IOException("Construction hazard: "+e.getType()+" "+e.getUUID()+" near "+p+": "+reason);
    }
    private static final class Overlay implements BlockGetter {
        final ServerLevel level;final Map<BlockPos,BlockState> states=new HashMap<>();
        Overlay(ServerLevel level){this.level=level;}
        public BlockState getBlockState(BlockPos p){return states.getOrDefault(p,level.getBlockState(p));}
        public FluidState getFluidState(BlockPos p){return getBlockState(p).getFluidState();}
        public BlockEntity getBlockEntity(BlockPos p){return level.getBlockEntity(p);}
        public int getHeight(){return level.getHeight();}
        public int getMinBuildHeight(){return level.getMinBuildHeight();}
    }
}
