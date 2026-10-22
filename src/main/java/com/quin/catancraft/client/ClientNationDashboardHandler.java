package com.quin.catancraft.client;

import net.minecraft.client.Minecraft;
import java.util.List;

public final class ClientNationDashboardHandler {
    private ClientNationDashboardHandler() {}
    public static void open(List<String> lines) {
        Minecraft client=Minecraft.getInstance();
        NationDashboardScreen dashboard=client.screen instanceof NationDashboardScreen existing?existing
                :client.screen instanceof DefenseConfirmationScreen confirmation?confirmation.parentDashboard():null;
        if(lines.contains("V|defense_confirmation")) {
            client.setScreen(new DefenseConfirmationScreen(lines,dashboard));
            return;
        }
        if(lines.contains("V|defense_refresh")&&dashboard==null)return;
        if(dashboard!=null){dashboard.updateLines(lines);if(client.screen!=dashboard)client.setScreen(dashboard);}
        else client.setScreen(new NationDashboardScreen(lines));
    }
}
