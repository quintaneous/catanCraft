package com.quin.catancraft.client;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.delivery.DeliveryActions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CatanCraft.MOD_ID, value = Dist.CLIENT)
public final class DeliveryMapClient {
    private static boolean openNextTick;
    private DeliveryMapClient() {}

    static boolean carrying(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.level.dimension().equals(Level.OVERWORLD)
                && DeliveryActions.carryingCrate(mc.player);
    }

    @SubscribeEvent
    public static void command(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("deliverymap").executes(ctx -> {
            Minecraft mc = Minecraft.getInstance();
            if (!carrying(mc)) {
                if (mc.player != null) mc.player.displayClientMessage(
                        Component.literal("Carry the sealed crate in the overworld to view its route."), false);
                return 0;
            }
            openNextTick = true;
            return 1;
        }));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !openNextTick) return;
        openNextTick = false;
        Minecraft mc = Minecraft.getInstance();
        if (carrying(mc)) mc.setScreen(new DeliveryMapScreen());
    }

    @SubscribeEvent
    public static void drawXaero(ScreenEvent.Render.Post event) {
        if (!event.getScreen().getClass().getName().equals("xaero.map.gui.GuiMap")) return;
        Minecraft mc = Minecraft.getInstance();
        if (!carrying(mc)) return;
        var camera = XaeroDeliveryMap.camera(event.getScreen());
        if (camera == null) return;
        drawPath(event.getGuiGraphics(), camera, 0, 0, event.getScreen().width, event.getScreen().height);
    }

    static void drawPath(GuiGraphics g, DeliveryMapPath.Camera c, int left, int top, int right, int bottom) {
        g.enableScissor(left, top, right, bottom);
        try {
            // A two-pass stroke preserves the violet centre around corners. Clip before emitting vertices.
            for (int radius = 2; radius >= 1; radius--) {
                int color = radius == 2 ? 0xff30253e : 0xffd89dff;
                var p = DeliveryMapPath.TURNS.get(0);
                for (int i = 1; i < DeliveryMapPath.TURNS.size(); i++) {
                    var q = DeliveryMapPath.TURNS.get(i);
                    int x = c.screenX(p.x() + .5), y = c.screenY(p.z() + .5);
                    int xx = c.screenX(q.x() + .5), yy = c.screenY(q.z() + .5);
                    int x0 = Math.max(left, Math.min(x, xx) - radius);
                    int y0 = Math.max(top, Math.min(y, yy) - radius);
                    int x1 = Math.min(right, Math.max(x, xx) + radius + 1);
                    int y1 = Math.min(bottom, Math.max(y, yy) + radius + 1);
                    if (x1 > x0 && y1 > y0) g.fill(x0, y0, x1, y1, color);
                    p = q;
                }
            }
            var a = DeliveryMapPath.TURNS.get(0);
            var b = DeliveryMapPath.TURNS.get(DeliveryMapPath.TURNS.size() - 1);
            marker(g, c.screenX(a.x()+.5), c.screenY(a.z()+.5), 0xff78dabf);
            marker(g, c.screenX(b.x()+.5), c.screenY(b.z()+.5), 0xffffcd65);
        } finally {
            g.disableScissor();
        }
    }

    private static void marker(GuiGraphics g, int x, int y, int color) {
        g.fill(x - 3, y - 3, x + 4, y + 4, 0xff30253e);
        g.fill(x - 2, y - 2, x + 3, y + 3, color);
    }
}
