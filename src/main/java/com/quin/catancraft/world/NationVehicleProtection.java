package com.quin.catancraft.world;

import com.quin.catancraft.command.NationProcurement;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Prevent Superb Warfare's sneak + crowbar vehicle packing for vehicles
 * purchased from the nation's stockpile. Only tagged purchased vehicles are
 * affected; normal Superb Warfare vehicles retain normal behavior.
 */
public final class NationVehicleProtection {
    private static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> CROWBAR =
            ItemTags.create(new ResourceLocation("forge", "tools/crowbar"));

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        protect(event, event.getTarget());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        protect(event, event.getTarget());
    }

    private static void protect(PlayerInteractEvent event, Entity target) {
        if (!target.getPersistentData().getBoolean(NationProcurement.PURCHASED_TAG)) return;
        if (!event.getEntity().isShiftKeyDown()) return;

        ItemStack main = event.getEntity().getMainHandItem();
        if (!main.is(CROWBAR)) return;

        event.setCancellationResult(InteractionResult.FAIL);
        event.setCanceled(true);
        if (!event.getLevel().isClientSide()) {
            event.getEntity().displayClientMessage(
                    Component.literal("Nation-purchased vehicles cannot be packed with a crowbar."),
                    true);
        }
    }
}
