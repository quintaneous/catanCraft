package com.quin.catancraft.client;

import net.minecraft.client.Minecraft;

import java.util.List;

public final class ClientNationDashboardHandler {
    private ClientNationDashboardHandler() {}

    public static void open(List<String> lines) {
        Minecraft.getInstance().setScreen(new NationDashboardScreen(lines));
    }
}
