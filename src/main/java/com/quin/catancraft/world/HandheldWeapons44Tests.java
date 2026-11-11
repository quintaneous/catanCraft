package com.quin.catancraft.world;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.quin.catancraft.CatanCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Supplier;

@GameTestHolder("catancraft_handheld44") @PrefixGameTestTemplate(false)
public final class HandheldWeapons44Tests {
    @GameTest(template="townhall_state_empty",batch="handheld44_01",timeoutTicks=600)
    public static void every_registered_sbw_gun_is_blocked_and_tacz_stays_available(GameTestHelper h)throws Exception{
        controlled(h);int count=0;for(Item item:ForgeRegistries.ITEMS){if(HandheldWeapons44.inherits(item,"com.atsuishio.superbwarfare.item.gun.GunItem")){require(HandheldWeapons44.blocked(new ItemStack(item)),"Native gun escaped policy");count++;}}
        require(count==45,"Pinned registered SBW gun inventory changed: "+count);
        require(!HandheldWeapons44.blocked(stack("tacz:modern_kinetic_gun")),"TaCZ gun was blocked");
        for(String id:List.of("heavy_ammo","rifle_ammo","crowbar","vehicle_key"))require(!HandheldWeapons44.blocked(stack("superbwarfare:"+id)),"Vehicle supplies/tool blocked: "+id);
        CatanCraft.LOGGER.info("HANDHELD44_CATALOG_PASS blockedSBWGuns={} taczAllowed=true sharedVehicleAmmoAllowed=true",count);h.succeed();
    }
    @GameTest(template="townhall_state_empty",batch="handheld44_02",timeoutTicks=600)
    public static void actual_native_gun_shooting_and_overrides_stop_before_any_side_effect(GameTestHelper h)throws Exception{
        controlled(h);Player player=player(h);int count=0;long denied=HandheldWeapons44.denied();
        for(Item item:ForgeRegistries.ITEMS)if(HandheldWeapons44.inherits(item,"com.atsuishio.superbwarfare.item.gun.GunItem")){
            ItemStack stack=new ItemStack(item);Object data=data(stack);setInt(data,"ammo",100);CompoundTag before=stack.save(new CompoundTag());
            Object params=parameters(h,data,player,player);Set<UUID> entities=entities(h.getLevel());
            item.getClass().getMethod("shoot",params.getClass()).invoke(item,params);
            require(stack.save(new CompoundTag()).equals(before)&&entities(h.getLevel()).equals(entities),"Native gun changed ammo/NBT/entities: "+ForgeRegistries.ITEMS.getKey(item));count++;
        }
        require(count==45&&HandheldWeapons44.denied()==denied+count,"Native shot interception did not execute for every registered gun");
        CatanCraft.LOGGER.info("HANDHELD44_FIRE_PASS nativeGunItems=45 unchangedAmmoNBT=true unchangedEntities=true");h.succeed();
    }
    @GameTest(template="townhall_state_empty",batch="handheld44_03",timeoutTicks=600)
    public static void real_handheld_packets_and_bow_release_are_inert_without_reading_context(GameTestHelper h)throws Exception{
        controlled(h);long before=HandheldWeapons44.denied();int count=0;
        for(String name:List.of("ShootMessage","FireKeyMessage","LaserShootMessage","MeleeAttackMessage","LungeMineAttackMessage")){
            Class<?> type=Class.forName("com.atsuishio.superbwarfare.network.message.send."+name);Object message=construct(type);
            type.getMethod("handler",Supplier.class).invoke(message,(Supplier<Object>)()->{throw new AssertionError("Blocked packet reached sender/damage code");});count++;
        }
        Player player=player(h);for(String id:List.of("superbwarfare:glock_17","superbwarfare:bocek")){
            ItemStack stack=stack(id);Object data=data(stack);setInt(data,"ammo",100);CompoundTag saved=stack.save(new CompoundTag());
            stack.getItem().getClass().getMethod("onFireKeyRelease",data.getClass(),Player.class,double.class,boolean.class).invoke(stack.getItem(),data,player,1d,true);
            require(stack.save(new CompoundTag()).equals(saved),"Release mutated blocked gun");count++;
        }
        require(HandheldWeapons44.denied()==before+count,"Native packet/release mixins missing");CatanCraft.LOGGER.info("HANDHELD44_PACKETS_PASS nativeHandlers=5 nativeReleasePaths=2");h.succeed();
    }
    @GameTest(template="townhall_state_empty",batch="handheld44_04",timeoutTicks=600)
    public static void loaded_recipes_and_final_forge_loot_filter_exclude_native_guns(GameTestHelper h)throws Exception{
        controlled(h);int gunRecipes=0;
        for(var recipe:h.getLevel().getRecipeManager().getRecipes())if(HandheldWeapons44.blocked(recipe.getResultItem(h.getLevel().registryAccess())))gunRecipes++;
        require(gunRecipes==0,"Native gun/deferred explosive recipe survived actual resource reload");
        require(HandheldWeapons44.blockedRecipe(JsonParser.parseString("{\"result\":{\"item\":\"superbwarfare:glock_17\"}}")),"Native smithing result not recognized");
        require(!HandheldWeapons44.blockedRecipe(JsonParser.parseString("{\"result\":{\"item\":\"superbwarfare:heavy_ammo\"}}")),"Vehicle ammo recipe blocked");
        var isolatedRecipes=new net.minecraft.world.item.crafting.RecipeManager();
        Map<ResourceLocation,com.google.gson.JsonElement> immutable=Map.of(new ResourceLocation("catancraft","blocked"),JsonParser.parseString("{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\"minecraft:diamond\"}],\"result\":{\"item\":\"superbwarfare:glock_17\"}}"),new ResourceLocation("catancraft","allowed"),JsonParser.parseString("{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\"minecraft:diamond\"}],\"result\":{\"item\":\"minecraft:diamond\"}}"));
        Method apply=Arrays.stream(isolatedRecipes.getClass().getDeclaredMethods()).filter(m->(m.getName().equals("apply")||m.getName().equals("m_5787_"))&&Arrays.equals(m.getParameterTypes(),new Class<?>[]{Map.class,net.minecraft.server.packs.resources.ResourceManager.class,net.minecraft.util.profiling.ProfilerFiller.class})).findFirst().orElseThrow();apply.setAccessible(true);
        apply.invoke(isolatedRecipes,immutable,h.getLevel().getServer().getResourceManager(),net.minecraft.util.profiling.InactiveProfiler.INSTANCE);
        require(immutable.size()==2&&isolatedRecipes.getRecipes().size()==1,"Native recipe filtering mutated its caller or kept a prohibited output");
        ObjectArrayList<ItemStack> loot=new ObjectArrayList<>();loot.add(stack("superbwarfare:glock_17"));loot.add(stack("superbwarfare:hand_grenade"));loot.add(new ItemStack(Items.DIAMOND));loot.add(stack("superbwarfare:heavy_ammo"));
        LootContext context=new LootContext.Builder(new LootParams.Builder(h.getLevel()).create(LootContextParamSets.EMPTY)).create(null);
        ObjectArrayList<ItemStack> result=ForgeHooks.modifyLoot(new ResourceLocation("catancraft","handheld44_control"),loot,context);
        require(result.size()==2&&result.stream().noneMatch(HandheldWeapons44::blocked)&&result.stream().anyMatch(s->s.is(Items.DIAMOND)),"Final native Forge loot hook did not filter only prohibited results");CatanCraft.LOGGER.info("HANDHELD44_ACQUISITION_PASS loadedGunRecipes=0 immutableInputPreserved=true finalForgeLootFiltered=true");h.succeed();
    }
    @GameTest(template="townhall_state_empty",batch="handheld44_05",timeoutTicks=600)
    public static void forge_interaction_and_pickup_refuse_both_hands_without_deleting_history(GameTestHelper h)throws Exception{
        controlled(h);Player player=player(h);ItemStack gun=stack("superbwarfare:ak_47");CompoundTag before=gun.save(new CompoundTag());
        for(InteractionHand hand:InteractionHand.values()){
            player.setItemInHand(hand,gun);var click=new PlayerInteractEvent.RightClickItem(player,hand);HandheldWeapons44.item(click);require(click.isCanceled(),"SBW hand interaction allowed");
            player.setItemInHand(hand,stack("tacz:modern_kinetic_gun"));var control=new PlayerInteractEvent.RightClickItem(player,hand);HandheldWeapons44.item(control);require(!control.isCanceled(),"TaCZ hand interaction denied");
        }
        ItemEntity drop=new ItemEntity(h.getLevel(),0,180,0,gun);var pickup=new EntityItemPickupEvent(player,drop);HandheldWeapons44.pickup(pickup);
        require(pickup.isCanceled()&&!drop.isRemoved()&&gun.save(new CompoundTag()).equals(before),"Refusal deleted/changed retained gun");
        for(String id:List.of("hand_grenade","rgo_grenade","m18_smoke_grenade","c4_bomb","lunge_mine"))require(HandheldWeapons44.blocked(stack("superbwarfare:"+id)),"Deferred explosive became available: "+id);
        var entries=new net.minecraftforge.common.util.MutableHashedLinkedMap<ItemStack,CreativeModeTab.TabVisibility>();
        ItemStack tacz=stack("tacz:modern_kinetic_gun"),vehicleAmmo=stack("superbwarfare:heavy_ammo");entries.put(gun,CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);entries.put(tacz,CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);entries.put(vehicleAmmo,CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        // This callback uses only the genuine Forge entry collection, not client tab/context state.
        HandheldWeapons44.Creative.entries(new net.minecraftforge.event.BuildCreativeModeTabContentsEvent(null,null,null,entries));
        require(!entries.contains(gun)&&entries.contains(tacz)&&entries.contains(vehicleAmmo),"Native creative entries did not retain only allowed controls");
        player.getInventory().clearContent();CatanCraft.LOGGER.info("HANDHELD44_EVENTS_PASS bothHandsDenied=true nativeCreativeFiltered=true legacyPickupPreserved=true taczAllowed=true");h.succeed();
    }
    @GameTest(template="townhall_state_empty",batch="handheld44_06",timeoutTicks=600)
    public static void genuine_vehicle_weapon_fires_but_forged_handheld_vehicle_context_cannot(GameTestHelper h)throws Exception{
        controlled(h);Player player=player(h);Entity vehicle=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("superbwarfare","lav_150")).create(h.getLevel());
        require(vehicle!=null,"Missing native LAV");var p=h.absolutePos(new BlockPos(2,4,2));vehicle.setPos(p.getX(),p.getY(),p.getZ());
        // Native LAV Cannon has Magazine=0 and consumes physical small_shell_ap inventory.
        // Do not seed backupAmmoCount: mounting must derive it from the inserted native shells.
        var inventory=(net.minecraftforge.items.IItemHandler)vehicle.getClass().getMethod("getInventory").invoke(vehicle);
        ItemStack shells=stack("superbwarfare:small_shell_ap");shells.setCount(8);
        require(inventory.insertItem(0,shells,false).isEmpty()&&inventory.getStackInSlot(0).getCount()==8,"Native vehicle shell insertion failed");
        require(player.startRiding(vehicle,true)&&player.getVehicle()==vehicle,"Native rider fixture failed");
        Map<?,?> map=(Map<?,?>)vehicle.getClass().getMethod("getGunDataMap").invoke(vehicle);Object data=map.get("Cannon");require(data!=null,"Native LAV Cannon did not initialize");
        ItemStack nativeStack=(ItemStack)data.getClass().getMethod("stack").invoke(data);
        require((Boolean)data.getClass().getMethod("canShoot",Entity.class).invoke(data,vehicle),"Physically loaded native vehicle gun is not fire-ready");
        Object params=parameters(h,data,vehicle,player);long allowed=HandheldWeapons44.allowed();int ammo=((Number)vehicle.getClass().getMethod("getAmmo",data.getClass()).invoke(vehicle,data)).intValue();Set<UUID> entities=entities(h.getLevel());
        require(ammo==8,"Native vehicle did not derive its ammunition count from the inserted shells");
        data.getClass().getMethod("shoot",params.getClass()).invoke(data,params);
        require(HandheldWeapons44.allowed()==allowed+1&&((Number)vehicle.getClass().getMethod("getAmmo",data.getClass()).invoke(vehicle,data)).intValue()==7
                &&inventory.getStackInSlot(0).is(shells.getItem())&&inventory.getStackInSlot(0).getCount()==7,"Vehicle gun did not consume exactly one physical native shell");
        Set<UUID> after=entities(h.getLevel());after.removeAll(entities);require(!after.isEmpty(),"Native vehicle firing produced no actual projectile");
        for(Entity e:h.getLevel().getAllEntities())if(after.contains(e.getUUID()))e.discard();
        ItemStack handheld=stack("superbwarfare:glock_17");Object handData=data(handheld);setInt(handData,"ammo",100);CompoundTag before=handheld.save(new CompoundTag());
        require(player.getVehicle()==vehicle,"Rider unexpectedly dismounted during native shot");
        Object forged=parameters(h,handData,vehicle,player);long denied=HandheldWeapons44.denied();handheld.getItem().getClass().getMethod("shoot",forged.getClass()).invoke(handheld.getItem(),forged);
        require(HandheldWeapons44.denied()==denied+1&&before.equals(handheld.save(new CompoundTag())),"Vehicle supplier granted handheld exemption");
        Object detached=data(nativeStack.copy());require(!HandheldWeapons44.allowNativeShot(nativeStack.getItem(),parameters(h,detached,vehicle,player)),"Copied vehicle weapon data gained owner authority");
        CatanCraft.LOGGER.info("HANDHELD44_VEHICLE_PASS actualNativeProjectile=true nativeAmmoConsumed=true forgedHandheldDenied=true detachedVehicleDataDenied=true physicalShellsBefore=8 physicalShellsAfter=7 mounted=true");player.stopRiding();vehicle.discard();h.succeed();
    }
    @GameTest(template="townhall_state_empty",batch="handheld44_07",timeoutTicks=600)
    public static void serialized_legacy_sbw_gun_remains_preserved_and_inert(GameTestHelper h)throws Exception{
        controlled(h);ItemStack original=stack("superbwarfare:glock_17");Object data=data(original);setInt(data,"ammo",15);CompoundTag saved=original.save(new CompoundTag());
        ItemStack loaded=ItemStack.of(saved.copy());require(loaded.save(new CompoundTag()).equals(saved),"Policy destroyed legacy native item serialization");
        Object retained=data(loaded),params=parameters(h,retained,player(h),player(h));CompoundTag before=loaded.save(new CompoundTag());long denied=HandheldWeapons44.denied();
        retained.getClass().getMethod("shoot",params.getClass()).invoke(retained,params);
        require(HandheldWeapons44.denied()==denied+1&&before.equals(loaded.save(new CompoundTag())),"Retained legacy item fired or was rewritten");
        CatanCraft.LOGGER.info("HANDHELD44_LEGACY_PASS nativeNBT=preserved nativeFiring=blocked noDeletion=true");h.succeed();
    }
    private static void controlled(GameTestHelper h)throws Exception{
        Path p=h.getLevel().getServer().getWorldPath(LevelResource.ROOT).toRealPath();require(h.getLevel().getServer() instanceof GameTestServer&&h.getLevel().getServer().isSameThread()&&p.getParent().getFileName().toString().equals("controlled-catancraft_handheld44-run")&&Files.readString(p.getParent().resolve(".catancraft-controlled-fixture-v1")).equals("CatanCraft synthetic flat fixture v1\n"),"Exact isolated handheld44 fixture required");
        require(h.getLevel().getServer().getPlayerList().getPlayers().isEmpty(),"No connected player allowed");
    }
    private static Player player(GameTestHelper h){return FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"CatanGun44"));}
    private static ItemStack stack(String id){Item item=ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));require(item!=null&&item!=Items.AIR,"Missing native item "+id);return new ItemStack(item);}
    private static Object data(ItemStack stack)throws Exception{return Class.forName("com.atsuishio.superbwarfare.data.gun.GunData").getMethod("from",ItemStack.class).invoke(null,stack);}
    private static void setInt(Object data,String field,int value)throws Exception{Object v=data.getClass().getField(field).get(data);v.getClass().getMethod("set",int.class).invoke(v,value);}
    private static int getInt(Object data,String field)throws Exception{Object v=data.getClass().getField(field).get(data);return ((Number)v.getClass().getMethod("get").invoke(v)).intValue();}
    private static Object parameters(GameTestHelper h,Object data,Entity supplier,Entity shooter)throws Exception{
        var p=h.absolutePos(new BlockPos(2,5,2));return Class.forName("com.atsuishio.superbwarfare.data.gun.ShootParameters").getConstructor(Entity.class,Entity.class,ServerLevel.class,Vec3.class,Vec3.class,data.getClass(),double.class,boolean.class,UUID.class,Vec3.class).newInstance(supplier,shooter,h.getLevel(),new Vec3(p.getX(),p.getY(),p.getZ()),new Vec3(0,1,0),data,0d,true,null,null);
    }
    private static Set<UUID> entities(ServerLevel level){Set<UUID> ids=new HashSet<>();for(Entity e:level.getAllEntities())ids.add(e.getUUID());return ids;}
    private static Object construct(Class<?> type)throws Exception{
        for(Constructor<?> c:type.getConstructors()){
            Class<?>[] types=c.getParameterTypes();if(Arrays.stream(types).anyMatch(t->t.getName().contains("ConstructorMarker")))continue;
            Object[] values=new Object[types.length];boolean supported=true;
            for(int i=0;i<types.length;i++){Class<?> t=types[i];if(t==int.class)values[i]=0;else if(t==double.class)values[i]=0d;else if(t==boolean.class)values[i]=false;else if(t==UUID.class)values[i]=UUID.randomUUID();else if(t==Vec3.class)values[i]=Vec3.ZERO;else if(List.class.isAssignableFrom(t))values[i]=List.of();else if(t.getName().equals("org.joml.Vector3f"))values[i]=null;else supported=false;}
            if(supported)return c.newInstance(values);
        }throw new IllegalStateException("Unqualified native packet constructor "+type.getName());
    }
    private static void require(boolean ok,String text){if(!ok)throw new AssertionError(text);}
}
