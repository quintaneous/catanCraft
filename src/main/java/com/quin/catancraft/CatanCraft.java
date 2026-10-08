package com.quin.catancraft;

import com.mojang.logging.LogUtils;
import com.quin.catancraft.command.CatanCommands;
import com.quin.catancraft.economy.EconomyEngine;
import com.quin.catancraft.network.NationNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
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
        LOGGER.info("CatanCraft 0.1 foundation loading");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CatanCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            EconomyEngine.tick(event.getServer());
        }
    }
}
