package com.quin.catancraft.network;

import com.quin.catancraft.client.ClientTerritoryHud;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server-authoritative territory label, never computed from client map files. */
public final class TerritoryHudPacket {
    private static final int MAX_LENGTH = 128;
    private final String label;

    public TerritoryHudPacket(String label) {
        this.label = label == null ? "" : label;
    }

    public static void encode(TerritoryHudPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.label, MAX_LENGTH);
    }

    public static TerritoryHudPacket decode(FriendlyByteBuf buffer) {
        return new TerritoryHudPacket(buffer.readUtf(MAX_LENGTH));
    }

    public static void handle(TerritoryHudPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT, () -> () -> ClientTerritoryHud.update(packet.label)));
        context.setPacketHandled(true);
    }
}
