package com.quin.catancraft.client;

import com.quin.catancraft.CatanCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Compact, noninteractive HUD above the crosshair, hidden when a menu is open. */
@Mod.EventBusSubscriber(modid = CatanCraft.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientTerritoryHud {
    private static String label = "";

    private ClientTerritoryHud() {}

    public static void update(String newLabel) {
        label = newLabel == null ? "" : newLabel;
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            label = "";
            return;
        }
        if (client.options.hideGui || client.screen != null || label.isBlank()) return;

        Font font = client.font;
        String message = "Territory: " + label;
        GuiGraphics graphics = event.getGuiGraphics();
        int centerX = event.getWindow().getGuiScaledWidth() / 2;
        int width = font.width(message);
        int left = centerX - width / 2;

        graphics.fill(left - 6, 4, left + width + 6, 19, 0xB015202B);
        graphics.drawString(font, message, left, 7, 0xFFE6EEF6, true);
    }
}
