package com.quin.catancraft.delivery;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "catancraft")
public final class PhysicalDelivery {
    private PhysicalDelivery() {}

    /** Status belongs to the present holder, including after a drop, transfer or restart. */
    public static DeliveryLedger.Waybill carried(ServerPlayer player) {
        DeliveryLedger ledger = DeliveryLedger.get(player.server);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            UUID id = DeliveryActions.crateId(player.getInventory().getItem(i));
            if (id == null) continue;
            DeliveryLedger.Waybill bill = ledger.shipment(id);
            if (bill != null && bill.state() == DeliveryLedger.State.IN_TRANSIT) return bill;
        }
        return null;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void rightClick(PlayerInteractEvent.RightClickBlock event) {
        boolean crateInHand = DeliveryActions.crateId(event.getItemStack()) != null;
        // A sealed barrel is freight, irrespective of which world or block the player clicks.
        if (crateInHand) consumeUse(event);
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator() || !player.isAlive()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        DeliveryRoute route = DeliveryRoute.current().orElse(null);
        if (route == null) return;
        ServerLevel level = player.serverLevel();
        String dimension = level.dimension().location().toString();
        BlockPos pos = event.getPos();
        boolean pickup = route.pickup().counter().equals(pos);
        DeliveryRoute.Endpoint stop = pickup ? route.pickup() : route.dropoff();
        if (!stop.dimension().equals(dimension) || !stop.counter().equals(pos)) return;
        consumeUse(event);
        if (!player.canReach(pos, 0) || !visible(player, event.getHitVec())) return;
        if (!stop.isClear(level)) {
            player.displayClientMessage(Component.literal("This freight counter is unavailable.").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        try {
            DeliveryLedger ledger = DeliveryLedger.get(player.server);
            if (pickup) {
                if (!event.getItemStack().isEmpty()) throw new IllegalStateException("Right-click the dispatch counter with an empty main hand.");
                if (carried(player) != null) throw new IllegalStateException("You already have a shipment. Take it to its receiving counter.");
                // Reserving the empty hand up front works even when every other slot is occupied.
                ItemStack crate = DeliveryActions.pickup(ledger, player.getUUID(), route, dimension, pos, level.getGameTime());
                player.setItemInHand(InteractionHand.MAIN_HAND, crate);
                player.sendSystemMessage(Component.literal("Pump fittings collected. At " + route.dropoff().name()
                        + ", hold the crate and right-click the receiving counter. Route: /deliverymap").withStyle(ChatFormatting.GOLD));
            } else {
                DeliveryLedger.Waybill receipt = DeliveryActions.turnIn(ledger, player.getUUID(), route, dimension,
                        pos, player.getMainHandItem());
                player.sendSystemMessage(Component.literal("Cargo delivered. Your signed receipt: " + receipt.id())
                        .withStyle(ChatFormatting.GREEN));
            }
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            level.playSound(null, pos, pickup ? SoundEvents.BARREL_OPEN : SoundEvents.BARREL_CLOSE,
                    SoundSource.BLOCKS, 0.65F, pickup ? 1.06F : 0.87F);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SPRUCE_PLANKS.defaultBlockState()),
                    pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 5, 0.16, 0.08, 0.16, 0.025);
        } catch (IllegalStateException ex) {
            player.displayClientMessage(Component.literal(ex.getMessage()).withStyle(ChatFormatting.YELLOW), true);
        }
    }

    /** Check line of sight to the clicked face; the slight inward step handles clicks on an exact voxel boundary. */
    private static boolean visible(ServerPlayer player, BlockHitResult click) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = click.getLocation();
        Vec3 inside = end.add(end.subtract(eye).normalize().scale(0.02));
        BlockHitResult actual = player.level().clip(new ClipContext(eye, inside, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
        return actual.getType() == HitResult.Type.BLOCK && actual.getBlockPos().equals(click.getBlockPos());
    }

    private static void consumeUse(PlayerInteractEvent.RightClickBlock event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void dropKeptCargo(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) return;
        List<ItemStack> cargo = new ArrayList<>();
        cargo.addAll(DeliveryActions.takeKeptCargo(player.getInventory().items));
        cargo.addAll(DeliveryActions.takeKeptCargo(player.getInventory().armor));
        cargo.addAll(DeliveryActions.takeKeptCargo(player.getInventory().offhand));
        ItemStack cursor = player.containerMenu.getCarried();
        if (DeliveryActions.crateId(cursor) != null) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
            cargo.add(cursor);
        } else {
            DeliveryActions.takeContainedCargo(cursor, cargo);
        }
        for (ItemStack stack : cargo) {
            ItemEntity drop = new ItemEntity(player.level(), player.getX(), player.getY() + 0.25, player.getZ(), stack);
            drop.setDefaultPickUpDelay();
            event.getDrops().add(drop);
        }
        if (!cargo.isEmpty()) player.getInventory().setChanged();
    }
}
