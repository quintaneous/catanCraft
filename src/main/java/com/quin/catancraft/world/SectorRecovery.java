package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.*;

/** Existing approved journals only; never buys or loads a prototype at startup. */
@Mod.EventBusSubscriber(modid=CatanCraft.MOD_ID)
public final class SectorRecovery {
    private SectorRecovery(){}
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        Path root=event.getServer().getWorldPath(LevelResource.ROOT);
        if(!Files.exists(root.resolve(FreshDemoAuthorization.MARKER),LinkOption.NOFOLLOW_LINKS))return;
        for(var result:SectorService.recoverPending(event.getServer()))
            if(!result.success())CatanCraft.LOGGER.error("Sector recovery remains pending: {}",result.message());
            else CatanCraft.LOGGER.info("Sector recovery: {}",result.message());
    }
}
