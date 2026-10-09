package com.quin.catancraft;

import com.mojang.logging.LogUtils;
import com.quin.catancraft.command.CatanCommands;
import com.quin.catancraft.economy.EconomyEngine;
import com.quin.catancraft.monument.MonumentManager;
import com.quin.catancraft.map.MapDefinitionManager;
import com.quin.catancraft.network.NationNetwork;
import com.quin.catancraft.world.WorldProtection;
import com.quin.catancraft.world.MapInteractionHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(CatanCraft.MOD_ID)
public final class CatanCraft {
    public static final String MOD_ID = "catancraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CatanCraft() {
        NationNetwork.register();
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new WorldProtection());
        MinecraftForge.EVENT_BUS.register(new MapInteractionHandler());
        LOGGER.info("CatanCraft 0.1 foundation loading");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CatanCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        try {
            MapDefinitionManager.reload(event.getServer());
        } catch (Exception ex) {
            LOGGER.error("Failed to load CatanCraft map definition", ex);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            EconomyEngine.tick(event.getServer());
            MonumentManager.tick(event.getServer());
        }
    }
}
