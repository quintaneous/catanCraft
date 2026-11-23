package com.quin.catancraft.network;

import com.quin.catancraft.client.ClientNationDashboardHandler;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.data.NationData;
import com.quin.catancraft.data.ResourceType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Visible trade partners and the sender's spendable stock when opening a new offer. */
public record TradeChoicesPacket(UUID nationId, List<String> partners, Map<String, Long> available) {
    public TradeChoicesPacket {
        partners = List.copyOf(partners);
        available = Map.copyOf(available);
    }

    public static TradeChoicesPacket from(CatanSavedData data, NationData sender) {
        List<String> partners = data.nations().stream()
                .filter(nation -> !nation.id().equals(sender.id()))
                .map(NationData::name)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        Map<String, Long> available = new LinkedHashMap<>();
        available.put("money", sender.treasury());
        for (ResourceType type : ResourceType.values()) available.put(type.id(), sender.resource(type));
        return new TradeChoicesPacket(sender.id(), partners, available);
    }

    public static void encode(TradeChoicesPacket choices, FriendlyByteBuf buf) {
        buf.writeUUID(choices.nationId);
        buf.writeCollection(choices.partners, (out, name) -> out.writeUtf(name));
        buf.writeLong(choices.available.get("money"));
        for (ResourceType type : ResourceType.values()) buf.writeLong(choices.available.get(type.id()));
    }

    public static TradeChoicesPacket decode(FriendlyByteBuf buf) {
        UUID sender = buf.readUUID();
        List<String> partners = buf.readList(in -> in.readUtf());
        Map<String, Long> available = new LinkedHashMap<>();
        available.put("money", buf.readLong());
        for (ResourceType type : ResourceType.values()) available.put(type.id(), buf.readLong());
        return new TradeChoicesPacket(sender, partners, available);
    }

    public static void handle(TradeChoicesPacket choices, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientNationDashboardHandler.openTrade(choices)));
        context.setPacketHandled(true);
    }
}
