package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Tick continuations hold no background world writer and never create purchases. */
@Mod.EventBusSubscriber(modid=CatanCraft.MOD_ID)
public final class SectorRunnerEvents {
    private SectorRunnerEvents(){}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase==TickEvent.Phase.END){SectorService.tick(event.getServer());CityBasePreparation.tick(event.getServer());PaidRecoveryService.tick(event.getServer());}
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event){SectorService.stopped(event.getServer());CityBasePreparation.stopped(event.getServer());PaidRecoveryService.stopped(event.getServer());}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){SectorService.stopped(event.getServer());CityBasePreparation.stopped(event.getServer());PaidRecoveryService.stopped(event.getServer());}
}
