package com.quin.catancraft;

import com.mojang.logging.LogUtils;
import com.quin.catancraft.command.CatanCommands;
import com.quin.catancraft.command.AdminSpeedCommand;
import com.quin.catancraft.economy.EconomyEngine;
import com.quin.catancraft.monument.MonumentManager;
import com.quin.catancraft.map.MapDefinitionManager;
import com.quin.catancraft.network.NationNetwork;
import com.quin.catancraft.network.TerritoryHudSync;
import com.quin.catancraft.world.WorldProtection;
import com.quin.catancraft.world.CombatTerrainSafety;
import com.quin.catancraft.world.MapInteractionHandler;
import com.quin.catancraft.world.NationVehicleProtection;
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
        MinecraftForge.EVENT_BUS.register(new NationVehicleProtection());
        MinecraftForge.EVENT_BUS.register(new AdminSpeedCommand());
        MinecraftForge.EVENT_BUS.register(new TerritoryHudSync());
        LOGGER.info("CatanCraft 0.1.3 loading");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CatanCommands.register(event.getDispatcher());
        AdminSpeedCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        try {
            MapDefinitionManager.reload(event.getServer());
            CombatTerrainSafety.enforce();
        } catch (Exception ex) {
            LOGGER.error("Failed to load CatanCraft map definition", ex);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            EconomyEngine.tick(event.getServer());
            MonumentManager.tick(event.getServer());
            if (event.getServer().getTickCount() % 200 == 0) {
                CombatTerrainSafety.enforce();
            }
        }
    }
}
