package com.quin.catancraft.delivery;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server actions are called only from physical use on the surveyed block. */
public final class DeliveryActions {
    public static final String CARGO = "a sealed crate of pump fittings";
    private static final String SEAL = "CatanCraftShipment";

    private DeliveryActions() {}

    public static UUID crateId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return !stack.isEmpty() && stack.is(Items.BARREL) && tag != null && tag.hasUUID(SEAL)
                ? tag.getUUID(SEAL) : null;
    }

    public static boolean carryingCrate(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (crateId(player.getInventory().getItem(i)) != null) return true;
        }
        return false;
    }

    public static ItemStack pickup(DeliveryLedger ledger, UUID player, DeliveryRoute route, String dimension,
                                   BlockPos clicked, long gameTime) {
        requireCounter(route.pickup(), dimension, clicked);
        DeliveryLedger.Waybill bill = ledger.issue(player, route.pickup().key(), route.dropoff().key(), gameTime);
        ItemStack crate = new ItemStack(Items.BARREL);
        crate.getOrCreateTag().putUUID(SEAL, bill.id());
        crate.setHoverName(Component.literal("Sealed crate • Pump fittings").withStyle(ChatFormatting.GOLD));
        ListTag lore = new ListTag();
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("Right-click the receiving counter at "
                + route.dropoff().name() + ".").withStyle(ChatFormatting.GRAY))));
        crate.getOrCreateTagElement("display").put("Lore", lore);
        return crate;
    }

    public static DeliveryLedger.Waybill turnIn(DeliveryLedger ledger, UUID holder, DeliveryRoute route,
                                                String dimension, BlockPos clicked, ItemStack held) {
        requireCounter(route.dropoff(), dimension, clicked);
        UUID id = crateId(held);
        if (id == null) throw new IllegalStateException("Hold the sealed pump fittings crate in your main hand.");
        DeliveryLedger.Waybill receipt = ledger.redeem(holder, id, route.pickup().key(), route.dropoff().key());
        held.shrink(1);
        return receipt;
    }

    private static void requireCounter(DeliveryRoute.Endpoint endpoint, String dimension, BlockPos pos) {
        if (!endpoint.dimension().equals(dimension) || !endpoint.counter().equals(pos)) {
            throw new IllegalStateException("Use the marked counter at " + endpoint.name() + ".");
        }
    }

    /** Used only when vanilla keeps this inventory on death. Normal death drops already include cargo. */
    public static List<ItemStack> takeKeptCargo(List<ItemStack> slots) {
        List<ItemStack> cargo = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            ItemStack held = slots.get(i);
            if (crateId(held) != null) cargo.add(slots.set(i, ItemStack.EMPTY));
            else takeContainedCargo(held, cargo);
        }
        return cargo;
    }

    // A vanilla carried shulker or bundle must not turn keepInventory into insurance for the freight.
    static void takeContainedCargo(ItemStack container, List<ItemStack> cargo) {
        CompoundTag holder;
        if (container.is(Items.BUNDLE)) holder = container.getTag();
        else if (container.getItem() instanceof BlockItem item && item.getBlock() instanceof ShulkerBoxBlock) {
            holder = container.getTagElement("BlockEntityTag");
        } else return;
        if (holder == null) return;
        ListTag contents = holder.getList("Items", Tag.TAG_COMPOUND);
        for (int i = contents.size() - 1; i >= 0; i--) {
            CompoundTag row = contents.getCompound(i);
            ItemStack child = ItemStack.of(row);
            if (crateId(child) != null) {
                contents.remove(i);
                cargo.add(child);
            } else {
                int before = cargo.size();
                takeContainedCargo(child, cargo);
                if (cargo.size() > before) row.put("tag", child.getTag());
            }
        }
    }
}
