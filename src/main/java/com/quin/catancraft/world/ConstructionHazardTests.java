package com.quin.catancraft.world;

import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.*;
import java.io.IOException;
import java.util.*;

@GameTestHolder("catancraft_integration") @PrefixGameTestTemplate(false)
public final class ConstructionHazardTests {
    static final int FLAGS=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS;
    @GameTest(template="townhall_state_empty",timeoutTicks=600)
    public static void harmless_entities_and_same_support_north(GameTestHelper h){harmless(h,1);}
    @GameTest(template="townhall_state_empty",timeoutTicks=600)
    public static void harmless_entities_and_same_support_south(GameTestHelper h){harmless(h,-1);}
    @GameTest(template="townhall_state_empty",timeoutTicks=600)
    public static void collision_support_and_intermediate_north(GameTestHelper h){hazards(h,1);}
    @GameTest(template="townhall_state_empty",timeoutTicks=600)
    public static void collision_support_and_intermediate_south(GameTestHelper h){hazards(h,-1);}
    @GameTest(template="townhall_state_empty",timeoutTicks=600)
    public static void full_vehicle_bounds_cross_write_with_center_outside(GameTestHelper h) {
        Fixture f=new Fixture(h);BlockPos p=f.p;
        Boat boat=EntityType.BOAT.create(f.level);if(boat==null)throw new IllegalStateException("Missing native boat");
        boat.setPos(p.getX()-2,p.getY(),p.getZ()+.5);boat.setNoGravity(true);
        boat.setBoundingBox(new AABB(p.getX()-3,p.getY(),p.getZ(),p.getX()+.3,p.getY()+1,p.getZ()+1));
        f.add(boat);f.later(()->{
            // Keep the deliberately wide proxy bounds: native vanilla tick may recompute them.
            boat.setBoundingBox(new AABB(p.getX()-3,p.getY(),p.getZ(),p.getX()+.3,p.getY()+1,p.getZ()+1));
            CompoundTag before=boat.saveWithoutId(new CompoundTag()).copy();
            refused(h,()->ConstructionHazards.inspect(f.level,List.of(write(p,Blocks.STONE.defaultBlockState()))));
            h.assertTrue(before.equals(boat.saveWithoutId(new CompoundTag()))&&!boat.isRemoved(),"Vehicle changed during refusal");
            h.assertTrue(f.level.getBlockState(p).isAir(),"Hazard preflight wrote blocks");
        });
    }
    @GameTest(template="townhall_state_empty",timeoutTicks=600)
    public static void attachment_semantics_remain_conservative(GameTestHelper h) {
        Fixture f=new Fixture(h);BlockPos support=f.p.south();f.set(support,Blocks.STONE.defaultBlockState());
        ItemFrame frame=new ItemFrame(f.level,f.p,Direction.NORTH);frame.setItem(new ItemStack(Items.DIAMOND));f.add(frame);
        f.later(()->{
            CompoundTag before=frame.saveWithoutId(new CompoundTag()).copy();
            refused(h,()->ConstructionHazards.inspect(f.level,List.of(write(support,Blocks.DIRT.defaultBlockState()))));
            h.assertTrue(before.equals(frame.saveWithoutId(new CompoundTag())),"Attachment/item NBT changed");
        });
    }
    @GameTest(template="townhall_state_empty",timeoutTicks=600)
    public static void rail_support_semantics_and_entity_set_change(GameTestHelper h) {
        Fixture f=new Fixture(h);BlockPos p=f.p;
        f.set(p.below(),Blocks.STONE.defaultBlockState());f.set(p,Blocks.RAIL.defaultBlockState());
        var cart=EntityType.MINECART.create(f.level);if(cart==null)throw new IllegalStateException("Missing native minecart");
        cart.setPos(p.getX()+.5,p.getY()+.0625,p.getZ()+.5);f.add(cart);
        f.later(()->{
            refused(h,()->ConstructionHazards.inspect(f.level,List.of(write(p.below(),Blocks.AIR.defaultBlockState()))));
            var guard=ConstructionHazards.inspect(f.level,List.of(write(p.offset(4,2,0),Blocks.STONE.defaultBlockState())));
            ItemEntity added=new ItemEntity(f.level,p.getX()+4.5,p.getY()+2,p.getZ()+.5,new ItemStack(Items.STICK));
            added.setNoGravity(true);added.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);f.add(added);
            boolean changed=false;try{guard.verify();}catch(IOException expected){changed=expected.getMessage().contains("entity set changed");}
            h.assertTrue(changed,"New entity after preflight was invisible to guard");
        });
    }
    @GameTest(template="townhall_state_empty",timeoutTicks=600)
    public static void leash_knot_support_semantics(GameTestHelper h) {
        Fixture f=new Fixture(h);f.set(f.p,Blocks.OAK_FENCE.defaultBlockState());
        var knot=new net.minecraft.world.entity.decoration.LeashFenceKnotEntity(f.level,f.p);f.add(knot);
        f.later(()->refused(h,()->ConstructionHazards.inspect(f.level,List.of(write(f.p,Blocks.BIRCH_FENCE.defaultBlockState())))));
    }
    private static void harmless(GameTestHelper h,int sign) {
        Fixture f=new Fixture(h);BlockPos p=f.p;f.set(p.below(),Blocks.STONE.defaultBlockState());
        ItemStack stack=new ItemStack(Items.DIAMOND,17);stack.getOrCreateTag().putString("integration_identity","preserve complete stack");
        ItemEntity item=new ItemEntity(f.level,p.getX()+.5,p.getY(),p.getZ()+.5,stack);item.setUnlimitedLifetime();f.add(item);
        Chicken chicken=EntityType.CHICKEN.create(f.level);if(chicken==null)throw new IllegalStateException("Missing native chicken");
        chicken.setNoAi(true);chicken.setPos(p.getX()+sign*3+.5,p.getY()+1,p.getZ()+.5);f.add(chicken);
        Boat boat=EntityType.BOAT.create(f.level);if(boat==null)throw new IllegalStateException("Missing native boat");
        boat.setPos(p.getX()+.5,p.getY()+1,p.getZ()+3.5);f.add(boat);
        f.later(()->{
            Map<UUID,CompoundTag> before=new HashMap<>();for(Entity e:f.entities)before.put(e.getUUID(),e.saveWithoutId(new CompoundTag()).copy());
            var writes=List.of(write(p.below(),Blocks.DIRT.defaultBlockState()),write(p.offset(sign*6,2,6),Blocks.STONE.defaultBlockState()));
            var guard=ConstructionHazards.inspect(f.level,writes);
            for(var w:writes)f.set(w.pos(),w.target());guard.verify();
            for(Entity e:f.entities)h.assertTrue(!e.isRemoved()&&before.get(e.getUUID()).equals(e.saveWithoutId(new CompoundTag())),"Harmless entity or complete item NBT changed");
            h.assertTrue(item.getItem().getCount()==17&&item.getItem().getTag().getString("integration_identity").equals("preserve complete stack"),"Item stack lost data");
        });
    }
    private static void hazards(GameTestHelper h,int sign) {
        Fixture f=new Fixture(h);BlockPos p=f.p;f.set(p.below(),Blocks.STONE.defaultBlockState());
        ItemEntity item=new ItemEntity(f.level,p.getX()+.5,p.getY(),p.getZ()+.5,new ItemStack(Items.STICK));f.add(item);
        f.later(()->{
            CompoundTag before=item.saveWithoutId(new CompoundTag()).copy();
            refused(h,()->ConstructionHazards.inspect(f.level,List.of(write(p,Blocks.STONE.defaultBlockState()))));
            refused(h,()->ConstructionHazards.inspect(f.level,List.of(write(p.below(),Blocks.AIR.defaultBlockState()))));
            refused(h,()->ConstructionHazards.inspect(f.level,List.of(write(p.below(),Blocks.STONE_SLAB.defaultBlockState()))));
            // Final geometry equals the original, but temporary loss of footing is unsafe.
            refused(h,()->ConstructionHazards.inspect(f.level,List.of(write(p.below(),Blocks.AIR.defaultBlockState()),write(p.below(),Blocks.STONE.defaultBlockState()))));
            refused(h,()->ConstructionHazards.inspect(f.level,List.of(write(p.below(),Blocks.MAGMA_BLOCK.defaultBlockState()))));
            // A resumed no-op is harmless; reversed removal of a previously installed floor is not.
            ConstructionHazards.inspect(f.level,List.of(write(p.below(),Blocks.STONE.defaultBlockState()))).verify();
            h.assertTrue(before.equals(item.saveWithoutId(new CompoundTag()))&&f.level.getBlockState(p.below()).is(Blocks.STONE),"Refusal changed item/support");
        });
    }
    private static ConstructionHazards.Write write(BlockPos p,BlockState s){return new ConstructionHazards.Write(p,s);}
    private interface Work {void run()throws Exception;}
    private static void refused(GameTestHelper h,Work work)throws Exception {
        boolean refused=false;try{work.run();}catch(IOException ex){refused=ex.getMessage().startsWith("Construction hazard:");}
        h.assertTrue(refused,"Real construction hazard was not refused");
    }
    private static final class Fixture {
        final GameTestHelper h;final ServerLevel level;final BlockPos p;final List<Entity> entities=new ArrayList<>();
        Fixture(GameTestHelper h) {
            this.h=h;level=h.getLevel();if(!(level.getServer() instanceof GameTestServer))throw new IllegalStateException("Isolated GameTest only");
            p=h.absolutePos(new BlockPos(12,5,12));
            for(BlockPos q:BlockPos.betweenClosed(p.offset(-7,-2,-2),p.offset(7,5,7)))set(q,Blocks.AIR.defaultBlockState());
        }
        void set(BlockPos p,BlockState s){level.setBlock(p,s,FLAGS);}
        void add(Entity e){e.setNoGravity(true);e.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);h.assertTrue(level.addFreshEntity(e),"Native fixture spawn failed");entities.add(e);}
        void later(Work w){h.runAfterDelay(5,()->{try {
            for(Entity e:entities)h.assertTrue(level.getEntities((Entity)null,e.getBoundingBox().inflate(1)).contains(e),"Native entity query fixture unavailable");
            w.run();h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}finally{for(Entity e:entities)e.discard();}});}
    }
}
