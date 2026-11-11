package com.quin.catancraft.world;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Current game policy: TaCZ handheld guns; SBW vehicle-owned weapons only.
 * Existing stacks/history are retained, never silently deleted or converted. */
@Mod.EventBusSubscriber(modid="catancraft")
public final class HandheldWeapons44 {
    private static final String GUN="com.atsuishio.superbwarfare.item.gun.GunItem";
    private static final String VEHICLE_GUN="com.atsuishio.superbwarfare.item.gun.vehicle.VehicleGunItem";
    private static final String VEHICLE="com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity";
    private static final Set<String> DEFERRED=Set.of("hand_grenade","rgo_grenade","m18_smoke_grenade","blu_43_mine","claymore_mine","c4_bomb","c4_bomb_rc","lunge_mine");
    private static final AtomicLong DENIED=new AtomicLong(),ALLOWED=new AtomicLong();
    private HandheldWeapons44(){}
    static boolean inherits(Object value,String name){if(value==null)return false;for(Class<?> c=value.getClass();c!=null;c=c.getSuperclass())if(c.getName().equals(name))return true;return false;}
    public static boolean blocked(ItemStack stack){
        if(stack==null||stack.isEmpty())return false;
        if(inherits(stack.getItem(),GUN))return true;
        ResourceLocation id=ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id!=null&&id.getNamespace().equals("superbwarfare")&&DEFERRED.contains(id.getPath());
    }
    /** Exact native ownership, not a passenger/riding flag or an arbitrary supplier. */
    public static boolean allowNativeShot(Object item,Object parameters){
        boolean allowed=false;
        try{
            if(inherits(item,VEHICLE_GUN)&&parameters!=null){
                Class<?> type=parameters.getClass();Object supplier=type.getField("ammoSupplier").get(parameters),data=type.getField("data").get(parameters);
                if(inherits(supplier,VEHICLE)&&supplier instanceof Entity vehicle&&!vehicle.isRemoved()
                        &&vehicle.level()==type.getField("level").get(parameters)){
                    Object map=supplier.getClass().getMethod("getGunDataMap").invoke(supplier);
                    allowed=map instanceof Map<?,?> values&&values.values().stream().anyMatch(value->value==data);
                }
            }
        }catch(ReflectiveOperationException|LinkageError failure){throw new IllegalStateException("Unsupported native SBW firing contract",failure);}
        if(allowed)ALLOWED.incrementAndGet();else DENIED.incrementAndGet();return allowed;
    }
    public static void deniedPacket(){DENIED.incrementAndGet();}
    static long denied(){return DENIED.get();}static long allowed(){return ALLOWED.get();}
    public static boolean blockedRecipe(JsonElement recipe){
        if(recipe==null||!recipe.isJsonObject())return false;
        JsonObject object=recipe.getAsJsonObject();return blockedResult(object.get("result"))||blockedResult(object.get("results"));
    }
    private static boolean blockedResult(JsonElement value){
        if(value==null||value.isJsonNull())return false;
        if(value.isJsonArray()){for(JsonElement entry:value.getAsJsonArray())if(blockedResult(entry))return true;return false;}
        if(value.isJsonObject()){JsonObject object=value.getAsJsonObject();return blockedResult(object.get("item"))||blockedResult(object.get("id"));}
        if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())return false;
        ResourceLocation id=ResourceLocation.tryParse(value.getAsString());
        return id!=null&&ForgeRegistries.ITEMS.containsKey(id)&&blocked(new ItemStack(ForgeRegistries.ITEMS.getValue(id)));
    }
    public static void filterLoot(List<ItemStack> loot){loot.removeIf(HandheldWeapons44::blocked);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void pickup(EntityItemPickupEvent e){if(blocked(e.getItem().getItem()))e.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void attack(AttackEntityEvent e){if(blocked(e.getEntity().getMainHandItem()))e.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void item(PlayerInteractEvent.RightClickItem e){if(blocked(e.getItemStack())){e.setCanceled(true);e.setCancellationResult(InteractionResult.FAIL);}}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void block(PlayerInteractEvent.RightClickBlock e){if(blocked(e.getItemStack())){e.setCanceled(true);e.setCancellationResult(InteractionResult.FAIL);}}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void entity(PlayerInteractEvent.EntityInteract e){if(blocked(e.getItemStack())){e.setCanceled(true);e.setCancellationResult(InteractionResult.FAIL);}}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void specific(PlayerInteractEvent.EntityInteractSpecific e){if(blocked(e.getItemStack())){e.setCanceled(true);e.setCancellationResult(InteractionResult.FAIL);}}
    @Mod.EventBusSubscriber(modid="catancraft",bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Creative {
        @SubscribeEvent(priority=EventPriority.LOWEST) public static void entries(BuildCreativeModeTabContentsEvent e){List<ItemStack> removed=new ArrayList<>();for(var entry:e.getEntries())if(blocked(entry.getKey()))removed.add(entry.getKey());for(ItemStack stack:removed)e.getEntries().remove(stack);}
    }
}
