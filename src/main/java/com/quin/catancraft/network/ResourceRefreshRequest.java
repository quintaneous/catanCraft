package com.quin.catancraft.network;

import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.data.NationData;
import com.quin.catancraft.ui.NationResourceRows;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** The client identifies its current view; membership and all balances are resolved on the server. */
public record ResourceRefreshRequest(UUID displayedNation, UUID viewId) {
    public static final int INTERVAL_TICKS = 20;
    private static final Map<ServerPlayer, Integer> LAST_REQUEST = new WeakHashMap<>();

    static void encode(ResourceRefreshRequest packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.displayedNation);
        buffer.writeUUID(packet.viewId);
    }

    static ResourceRefreshRequest decode(FriendlyByteBuf buffer) {
        return new ResourceRefreshRequest(buffer.readUUID(), buffer.readUUID());
    }

    static void handle(ResourceRefreshRequest packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) context.enqueueWork(() -> packet.refresh(player));
        context.setPacketHandled(true);
    }

    private void refresh(ServerPlayer player) {
        int now = player.server.getTickCount();
        Integer last = LAST_REQUEST.get(player);
        if (last != null && now - last < INTERVAL_TICKS) return;
        LAST_REQUEST.put(player, now);

        NationNetwork.openDashboard(player, rowsFor(CatanSavedData.get(player.server), player.getUUID()));
    }

    List<String> rowsFor(CatanSavedData data, UUID memberId) {
        NationData nation = data.nationForPlayer(memberId);
        String identity = "I|" + displayedNation;
        String view = "U|" + viewId;
        if (nation == null || !nation.id().equals(displayedNation)) {
            return List.of("V|resource_unavailable", identity, view);
        }

        List<String> rows = new ArrayList<>();
        rows.add("V|resource_refresh");
        rows.add(identity);
        rows.add(view);
        rows.addAll(NationResourceRows.section(data, nation));
        return rows;
    }
}
