package com.quin.catancraft.world;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * CatanCraft treats the authored map as a locked strategy board.
 *
 * Players never directly edit world blocks. Administrative map tools and
 * commands can still change the world because they do not rely on normal
 * player break/place interactions.
 *
 * Explosions currently never damage blocks. When siege gameplay is added,
 * the explosion hook is the single place where affected blocks will be
 * filtered against an active registered city siege zone.
 */
public final class WorldProtection {
    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onMultiPlace(BlockEvent.EntityMultiPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer)) return;

        ItemStack held = event.getItemStack();
        if (held.getItem() instanceof BlockItem || held.getItem() instanceof BucketItem) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer)) return;

        if (event.getItemStack().getItem() instanceof BucketItem) {
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
