package com.quin.catancraft.network;

import com.quin.catancraft.CatanCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.List;

public final class NationNetwork {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(CatanCraft.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

    private static int nextId;

    private NationNetwork() {}

    public static void register() {
        CHANNEL.registerMessage(nextId++, NationDashboardPacket.class,
                NationDashboardPacket::encode,
                NationDashboardPacket::decode,
                NationDashboardPacket::handle);
    }

    public static void openDashboard(ServerPlayer player, List<String> lines) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new NationDashboardPacket(lines));
    }
}
