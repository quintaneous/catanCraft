package com.quin.catancraft.world;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="catancraft")
public final class VehicleService50Events {
    private VehicleService50Events(){}
    @SubscribeEvent public static void mount(EntityMountEvent event){
        if(event.isMounting()&&VehicleService50.locked(event.getEntityBeingMounted()))event.setCanceled(true);
    }
}
