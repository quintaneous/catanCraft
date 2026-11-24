package com.quin.catancraft.client;

import com.quin.catancraft.CatanCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;

/** Optional, client-only adapter for the published Forge 1.20.1 world map 1.47.0. */
final class XaeroDeliveryMap {
    private static final String SCREEN = "xaero.map.gui.GuiMap";
    private static boolean checked;
    private static Fields fields;

    record Fields(Field cameraX, Field cameraZ, Field scale, Field guiScale, Field dimension) {
        Fields(Class<?> screen) throws ReflectiveOperationException {
            this(field(screen, "cameraX"), field(screen, "cameraZ"), field(screen, "scale"),
                 field(screen, "screenScale"), field(screen, "lastViewedDimensionId"));
        }

        DeliveryMapPath.Camera view(Object screen, Object overworld, int pixelWidth, int pixelHeight)
                throws IllegalAccessException {
            if (!overworld.equals(dimension.get(screen))) return null;
            double gui = guiScale.getDouble(screen);
            double zoom = scale.getDouble(screen) / gui;
            double x = cameraX.getDouble(screen);
            double z = cameraZ.getDouble(screen);
            if (!(gui > 0) || !(zoom > 0) || !Double.isFinite(zoom) || !Double.isFinite(x) || !Double.isFinite(z)) return null;

            // Xaero centres the physical framebuffer using integer division, then divides by GUI scale.
            return new DeliveryMapPath.Camera(x, z, zoom, (pixelWidth / 2) / gui, (pixelHeight / 2) / gui);
        }

        private static Field field(Class<?> screen, String name) throws ReflectiveOperationException {
            Field f = screen.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        }
    }

    private XaeroDeliveryMap() {}

    static DeliveryMapPath.Camera camera(Screen screen) {
        if (!screen.getClass().getName().equals(SCREEN)) return null;
        try {
            if (!checked) {
                checked = true;
                if (ModList.get().getModContainerById("xaeroworldmap")
                        .filter(mod -> mod.getModInfo().getVersion().toString().equals("1.47.0")).isPresent()) {
                    fields = new Fields(screen.getClass());
                }
            }
            if (fields == null) return null;
            var win = Minecraft.getInstance().getWindow();
            return fields.view(screen, Level.OVERWORLD, win.getWidth(), win.getHeight());
        } catch (ReflectiveOperationException | RuntimeException incompatible) {
            fields = null;
            CatanCraft.LOGGER.warn("Xaero delivery overlay unavailable. The carrier can use /deliverymap.", incompatible);
            return null;
        }
    }
}
