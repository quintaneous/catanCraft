package com.quin.catancraft.world;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.player.PlayerBucketEmptyEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * CatanCraft treats the authored map as a locked strategy board.
 *
 * Normal players never directly edit world blocks. Operators retain a bypass
 * so the map can still be authored and repaired administratively.
 *
 * Explosions currently never damage blocks. When siege gameplay is added,
 * the explosion hook is the single place where affected blocks will be
 * filtered against an active registered city siege zone.
 */
public final class WorldProtection {
    private static boolean adminBypass(Entity entity) {
        return entity instanceof ServerPlayer player && player.hasPermissions(2);
    }

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        if (!adminBypass(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!adminBypass(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onMultiPlace(BlockEvent.EntityMultiPlaceEvent event) {
        if (!adminBypass(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!adminBypass(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Detonate event) {
        // Entity damage is untouched; only terrain/block destruction is removed.
        // Future siege logic will selectively retain registered city blocks here.
        event.getAffectedBlocks().clear();
    }
}
