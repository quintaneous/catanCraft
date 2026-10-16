package com.quin.catancraft.network;

import com.quin.catancraft.client.ClientNationDashboardHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class NationDashboardPacket {
    private static final int MAX_LINES = 1024;
    private static final int MAX_LINE_LENGTH = 512;

    private final List<String> lines;

    public NationDashboardPacket(List<String> lines) {
        this.lines = List.copyOf(lines);
    }

    public static void encode(NationDashboardPacket packet, FriendlyByteBuf buf) {
        int size = Math.min(packet.lines.size(), MAX_LINES);
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            buf.writeUtf(packet.lines.get(i), MAX_LINE_LENGTH);
        }
    }

    public static NationDashboardPacket decode(FriendlyByteBuf buf) {
        int size = Math.min(buf.readVarInt(), MAX_LINES);
        List<String> lines = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            lines.add(buf.readUtf(MAX_LINE_LENGTH));
        }
        return new NationDashboardPacket(lines);
    }

    public static void handle(NationDashboardPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientNationDashboardHandler.open(packet.lines)));
        context.setPacketHandled(true);
    }
}
