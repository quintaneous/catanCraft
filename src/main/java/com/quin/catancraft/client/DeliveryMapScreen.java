package com.quin.catancraft.client;

import com.quin.catancraft.CatanCraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

final class DeliveryMapScreen extends Screen {
    // North-up 160 by 160 block-surface crop. Texel (0,0) covers the block at (-40,-68).
    private static final int SIZE = 160, WEST = -40, NORTH = -68;
    private static final ResourceLocation DISTRICT = new ResourceLocation(CatanCraft.MOD_ID, "textures/gui/delivery_district.png");

    DeliveryMapScreen() {
        super(Component.literal("Meridian delivery route"));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!DeliveryMapClient.carrying(minecraft)) {
            onClose();
            return;
        }
        renderBackground(g);
        int side = Math.max(1, Math.min(width - 12, height - 47));
        int l = (width - side) / 2, t = (height - side) / 2;
        double scale = (double) side / SIZE;
        var c = new DeliveryMapPath.Camera(WEST, NORTH, scale, l, t);
        g.drawCenteredString(font, title, width / 2, t - 16, 0xfff5eee3);
        g.fill(l - 1, t - 1, l + side + 1, t + side + 1, 0xffd1c9b4);
        g.blit(DISTRICT, l, t, side, side, 0f, 0f, SIZE, SIZE, SIZE, SIZE);
        DeliveryMapClient.drawPath(g, c, l, t, l + side, t + side);
        g.drawString(font, "N", l + 4, t + 3, 0xffffffff);
        int x = c.screenX(minecraft.player.getX()), y = c.screenY(minecraft.player.getZ());
        boolean inside = x >= l && x < l + side && y >= t && y < t + side;
        if (inside) {
            g.enableScissor(l, t, l + side, t + side);
            g.fill(x - 2, y - 2, x + 3, y + 3, 0xff17202a);
            g.fill(x - 1, y - 1, x + 2, y + 2, 0xffffffff);
            g.disableScissor();
        }
        int legend = t + side + 3;
        g.drawCenteredString(font, Component.literal("■ Meridian Airlift Depot").withStyle(s -> s.withColor(0x78dabf))
                .append(Component.literal("   ■ Copperline Refinery").withStyle(s -> s.withColor(0xffcd65))),
                width / 2, legend, 0xffffffff);
        g.drawCenteredString(font, inside ? "White marker: you" : "You are outside the district",
                width / 2, legend + 10, 0xffeeeeee);
    }
}
