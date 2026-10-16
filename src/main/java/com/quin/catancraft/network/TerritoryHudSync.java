package com.quin.catancraft.network;

import com.quin.catancraft.map.MapDefinitionManager;
import com.quin.catancraft.map.TerritoryDefinition;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Checks location on the authoritative server every 10 ticks.
 * Network messages are sent only when the player's territory changes.
 */
public final class TerritoryHudSync {
    private static final Map<UUID, String> LAST_LABEL = new HashMap<>();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END ||
                !(event.player instanceof ServerPlayer player) ||
                player.tickCount % 10 != 0) return;

        TerritoryDefinition here = MapDefinitionManager.territoryAt(
                player.serverLevel(), player.blockPosition());
        String label = here == null
                ? "Wilderness"
                : here.name() + " [" + here.id().toUpperCase(Locale.ROOT) + "]";

        String previous = LAST_LABEL.put(player.getUUID(), label);
        if (!label.equals(previous)) {
            NationNetwork.sendTerritoryHud(player, label);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_LABEL.remove(event.getEntity().getUUID());
    }
}
