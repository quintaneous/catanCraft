package com.quin.catancraft.client;

import java.lang.reflect.Field;

/**
 * Optional ABI check on the original Xaero 1.47.0 JAR, its nested XaeroLib 1.7.3,
 * and the SRG 1.20.1-47.4.10 client JAR + Forge runtime classpath.
 * The screen constructor normally opens an active client session. Allocate without
 * that constructor to exercise the adapter on the published class with explicit frame data.
 */
public final class XaeroMapAbiTest {
    public static void main(String[] args) throws Exception {
        var screenClass = Class.forName("xaero.map.gui.GuiMap");
        var fields = new XaeroDeliveryMap.Fields(screenClass);
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field instance = unsafeClass.getDeclaredField("theUnsafe");
        instance.setAccessible(true);
        var allocate = unsafeClass.getMethod("allocateInstance", Class.class);
        Object unsafe = instance.get(null);
        Object screen = allocate.invoke(unsafe, screenClass);
        // Construct actual 1.20.1 keys without booting a game or its global registries.
        Class<?> id = Class.forName("net.minecraft.resources.ResourceLocation");
        var name = id.getConstructor(String.class, String.class);
        var key = fields.dimension().getType().getDeclaredConstructor(id, id);
        key.setAccessible(true);
        Object dimension = name.newInstance("minecraft", "dimension");
        Object overworld = key.newInstance(dimension, name.newInstance("minecraft", "overworld"));
        Object nether = key.newInstance(dimension, name.newInstance("minecraft", "the_nether"));

        fields.dimension().set(screen, overworld);
        fields.cameraX().setDouble(screen, 74.5);
        fields.cameraZ().setDouble(screen, -28.5);
        fields.scale().setDouble(screen, 8);
        fields.guiScale().setDouble(screen, 2);
        var c = fields.view(screen, overworld, 1281, 721);
        check(c.screenX(74.5) == 320 && c.screenY(-28.5) == 180, "centre at pickup");
        check(c.screenX(50.5) == 224 && c.screenY(-44.5) == 116, "western bend at zoom");
        fields.cameraX().setDouble(screen, 29.5);
        fields.cameraZ().setDouble(screen, 75.5);
        fields.scale().setDouble(screen, 1.5);
        fields.guiScale().setDouble(screen, 3);
        c = fields.view(screen, overworld, 1920, 1080);
        check(c.screenX(29.5) == 320 && c.screenY(75.5) == 180, "pan + GUI scale");
        check(c.screenX(74.5) == 343 && c.screenY(-28.5) == 128, "zoomed out");
        fields.dimension().set(screen, nether);
        check(fields.view(screen, overworld, 1920, 1080) == null, "foreign dimension");
        fields.dimension().set(screen, null);
        check(fields.view(screen, overworld, 1920, 1080) == null, "map loading");
        fields.dimension().set(screen, overworld);
        fields.scale().setDouble(screen, 0);
        check(fields.view(screen, overworld, 1920, 1080) == null, "invalid zoom");
        System.out.println("PASS actual published GuiMap ABI, camera, pan, two zoom/GUI scales, dimension filtering and invalid zoom");
    }

    private static void check(boolean ok, String reason) {
        if (!ok) throw new AssertionError(reason);
    }
}
