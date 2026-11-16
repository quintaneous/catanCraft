package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.*;
import java.util.*;

/** Serial startup continuation of retained histories only. It never quotes or buys a wall. */
@Mod.EventBusSubscriber(modid=CatanCraft.MOD_ID)
public final class SectorRecovery {
    private SectorRecovery(){}
    private static final Map<MinecraftServer,Queue<SectorService.RecoveryJob>> PENDING=new IdentityHashMap<>();
    @SubscribeEvent public static void started(ServerStartedEvent event) { start(event.getServer()); }
    static void start(MinecraftServer server) {
        Path root=server.getWorldPath(LevelResource.ROOT);
        if(!Files.exists(root.resolve(FreshDemoAuthorization.MARKER),LinkOption.NOFOLLOW_LINKS))return;
        Queue<SectorService.RecoveryJob> jobs=new ArrayDeque<>(SectorService.discoverRecoveryJobs(server));
        if(!jobs.isEmpty())PENDING.put(server,jobs);else PENDING.remove(server);
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase==TickEvent.Phase.END)continuePending(event.getServer());
    }
    static void continuePending(MinecraftServer server) {
        Queue<SectorService.RecoveryJob> jobs=PENDING.get(server);
        if(jobs==null||server.getTickCount()%20!=0)return;
        // These waits do not consume the city. The active journal owns its chunk-loading
        // continuation, and later cities remain queued until that continuation finishes.
        if(SectorService.hasActiveOperation(server)||CityBasePreparation.isRunning(server)||PaidRecoveryService.isRunning(server))return;
        SectorService.RecoveryJob job=jobs.poll();if(job==null){PENDING.remove(server);return;}
        for(var result:SectorService.recoverStartupJob(server,job))
            if(!result.success())CatanCraft.LOGGER.error("Sector recovery remains pending in city {}: {}",job.city(),result.message());
            else CatanCraft.LOGGER.info("Sector recovery in city {}: {}",job.city(),result.message());
        // A permanent source/history refusal is retained for review and never blocks
        // another city's valid operation. A normal restart rediscovers retained work.
        if(jobs.isEmpty())PENDING.remove(server);
    }
    static void stopped(MinecraftServer server) { PENDING.remove(server); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { stopped(event.getServer()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { stopped(event.getServer()); }
}
