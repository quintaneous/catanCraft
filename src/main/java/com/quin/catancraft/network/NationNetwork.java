package com.quin.catancraft.network;

import com.quin.catancraft.CatanCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.List;
import java.util.Optional;

public final class NationNetwork {
    private static final String PROTOCOL = "7";

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
                NationDashboardPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextId++, TerritoryHudPacket.class,
                TerritoryHudPacket::encode,
                TerritoryHudPacket::decode,
                TerritoryHudPacket::handle);
        CHANNEL.registerMessage(nextId++, ResourceRefreshRequest.class,
                ResourceRefreshRequest::encode, ResourceRefreshRequest::decode,
                ResourceRefreshRequest::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, TradeChoicesPacket.class,
                TradeChoicesPacket::encode, TradeChoicesPacket::decode,
                TradeChoicesPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendTradeChoices(ServerPlayer player, TradeChoicesPacket choices) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), choices);
    }

    public static void sendTerritoryHud(ServerPlayer player, String label) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new TerritoryHudPacket(label));
    }

    public static void openDashboard(ServerPlayer player, List<String> lines) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new NationDashboardPacket(lines));
    }
}
