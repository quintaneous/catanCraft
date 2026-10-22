package com.quin.catancraft.world;

import com.mojang.authlib.GameProfile;
import com.quin.catancraft.command.NationProcurement;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.lang.reflect.Method;
import java.util.UUID;

/** Optional actual-SBW integration suite. No substitute entities or mocked SBW methods. */
@GameTestHolder("catancraft_sbw") @PrefixGameTestTemplate(false)
public final class SbwRetrievalTests {
    @GameTest(template="townhall_state_empty",timeoutTicks=600)
    public static void purchased_pickup_and_lav_direct_retrieval_refused(GameTestHelper h) {
        run(h, f -> {
            for (String id : new String[]{"sodayo_pick_up", "lav_150"}) {
                Entity e=f.vehicle(id,true);
                f.denied(e, () -> f.direct(e,InteractionHand.MAIN_HAND));
                e.discard();
            }
        });
    }
    @GameTest(template="townhall_state_empty",timeoutTicks=600)
    public static void purchased_helper_refuses_both_hands(GameTestHelper h) {
        run(h, f -> {
            for (InteractionHand hand : InteractionHand.values()) {
                Entity e=f.vehicle("sodayo_pick_up",true);
                f.denied(e, () -> f.helper(e,hand));e.discard();
            }
        });
    }
    @GameTest(template="townhall_state_empty",timeoutTicks=600)
    public static void actual_override_retrieval_paths_refused(GameTestHelper h) {
        run(h, f -> {
            String prefix="com.atsuishio.superbwarfare.entity.vehicle.";
            for(String name:new String[]{prefix+"MortarEntity",prefix+"AnnihilatorEntity",prefix+"base.AutoAimableEntity"}) {
                Class<?> base=Class.forName(name);Entity e=f.vehicleByClass(base);
                f.denied(e,()->f.direct(e,InteractionHand.MAIN_HAND));e.discard();
            }
        });
    }
    @GameTest(template="townhall_state_empty",timeoutTicks=600)
    public static void artillery_actual_offhand_interaction_refused(GameTestHelper h) {
        run(h, f -> {
            Entity e=f.vehicleByClass(Class.forName("com.atsuishio.superbwarfare.entity.vehicle.base.ArtilleryEntity"));
            f.player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            f.player.setItemInHand(InteractionHand.OFF_HAND,f.crowbar.copy());
            f.denied(e,()->e.interact(f.player,InteractionHand.OFF_HAND));e.discard();
        });
    }
    @GameTest(template="townhall_state_empty",timeoutTicks=600)
    public static void forge_event_guard_checks_both_hands(GameTestHelper h) {
        run(h, f -> {
            for (InteractionHand hand : InteractionHand.values()) {
                Entity e=f.vehicle("sodayo_pick_up",true);
                f.player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                f.player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
                f.player.setItemInHand(hand,f.crowbar.copy());
                var event=new net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract(f.player,hand,e);
                new NationVehicleProtection().onEntityInteract(event);
                h.assertTrue(event.isCanceled()&&event.getCancellationResult()==InteractionResult.FAIL&&!e.isRemoved(),"Forge purchased retrieval fallback missed a hand");
                e.discard();
            }
        });
    }
    @GameTest(template="townhall_state_empty",timeoutTicks=600)
    public static void unpurchased_pickup_retrieval_still_awards_item(GameTestHelper h) {
        run(h, f -> {
            Entity e=f.vehicle("sodayo_pick_up",false);
            int before=f.itemCount();Object result=f.helper(e,InteractionHand.MAIN_HAND);
            h.assertTrue(result==InteractionResult.SUCCESS&&e.isRemoved()&&f.itemCount()>before,
                    "Ordinary unpurchased SBW retrieval no longer grants its packed item");
        });
    }
    @GameTest(template="townhall_state_empty",timeoutTicks=600)
    public static void purchased_tag_survives_actual_entity_serialization(GameTestHelper h) {
        run(h, f -> {
            Entity e=f.vehicle("sodayo_pick_up",true);CompoundTag tag=new CompoundTag();
            h.assertTrue(e.save(tag),"Purchased SBW entity refused serialization");UUID id=e.getUUID();e.discard();
            Entity restored=EntityType.loadEntityRecursive(tag,f.level,entity->entity);
            h.assertTrue(restored!=null&&restored.getUUID().equals(id)
                    &&restored.getPersistentData().getBoolean(NationProcurement.PURCHASED_TAG),"SBW entity lost purchased marker/identity on save/load");
            f.denied(restored,()->f.helper(restored,InteractionHand.OFF_HAND));restored.discard();
        });
    }
    private interface Checked { Object call() throws Exception; }
    private interface Test { void run(Fixture f) throws Exception; }
    private static void run(GameTestHelper h,Test test) {
        Fixture f=null;
        try { f=new Fixture(h);test.run(f);h.succeed(); }
        catch(Exception e){throw new RuntimeException(e);}
        finally {if(f!=null)f.player.getInventory().clearContent();}
    }
    private static final class Fixture {
        final GameTestHelper h;final ServerLevel level;final FakePlayer player;final ItemStack crowbar;
        Fixture(GameTestHelper h)throws Exception {
            this.h=h;level=h.getLevel();
            if(!(level.getServer() instanceof GameTestServer))throw new IllegalStateException("Isolated GameTestServer required");
            Class.forName("com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity");
            player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"CatanSbwAudit"));
            var p=h.absolutePos(new BlockPos(2,2,2));player.setPos(p.getX(),p.getY(),p.getZ());player.setShiftKeyDown(true);player.getInventory().clearContent();
            Item item=ForgeRegistries.ITEMS.getValue(new ResourceLocation("superbwarfare","crowbar"));
            if(item==null||item==Items.AIR)throw new IllegalStateException("Actual SBW crowbar missing");crowbar=new ItemStack(item);
        }
        Entity vehicle(String id,boolean purchased) {
            EntityType<?> type=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("superbwarfare",id));
            if(type==null)throw new IllegalStateException("Missing actual SBW entity "+id);
            return prepare(type.create(level),purchased);
        }
        Entity vehicleByClass(Class<?> base) {
            for(var key:ForgeRegistries.ENTITY_TYPES.getKeys())if(key.getNamespace().equals("superbwarfare")) {
                Entity candidate=ForgeRegistries.ENTITY_TYPES.getValue(key).create(level);
                if(candidate!=null&&base.isInstance(candidate))return prepare(candidate,true);
            }
            throw new IllegalStateException("No actual registered SBW subtype of "+base.getName());
        }
        Entity prepare(Entity e,boolean purchased) {
            if(e==null)throw new IllegalStateException("Cannot create actual SBW entity");e.setPos(player.getX()+4,player.getY(),player.getZ());e.setNoGravity(true);
            e.getPersistentData().putBoolean(NationProcurement.PURCHASED_TAG,purchased);
            // Immediate same-tick calls avoid physics and never involve a connected player.
            if(!level.addFreshEntity(e))throw new IllegalStateException("Cannot stage actual SBW entity");return e;
        }
        Object direct(Entity e,InteractionHand hand)throws Exception {
            return e.getClass().getMethod("onCrowbarInteract",ItemStack.class,Player.class,InteractionHand.class).invoke(e,crowbar,player,hand);
        }
        Object helper(Entity e,InteractionHand hand)throws Exception {
            Class<?> vehicle=Class.forName("com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity");
            Method helper=Class.forName("com.atsuishio.superbwarfare.item.weapon.CrowbarItem").getMethod("crowbarInteract",vehicle,ItemStack.class,Player.class,InteractionHand.class);
            return helper.invoke(null,e,crowbar,player,hand);
        }
        int itemCount(){int count=0;for(ItemStack stack:player.getInventory().items)count+=stack.getCount();for(ItemStack stack:player.getInventory().offhand)count+=stack.getCount();for(ItemEntity e:level.getEntitiesOfClass(ItemEntity.class,player.getBoundingBox().inflate(32)))count+=e.getItem().getCount();return count;}
        void denied(Entity e,Checked action)throws Exception {
            int before=itemCount();UUID uuid=e.getUUID();Object result=action.call();
            h.assertTrue(result==InteractionResult.FAIL&&!e.isRemoved()&&e.getUUID().equals(uuid)&&itemCount()==before,
                    "Purchased actual SBW retrieval changed entity identity/removal or awarded an item");
        }
    }
}
