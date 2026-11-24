package com.quin.catancraft.visual;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.data.MonumentData;
import com.quin.catancraft.delivery.DeliveryRoute;
import com.quin.catancraft.monument.AuthoredCaptureFloors;
import com.quin.catancraft.map.MapDefinitionManager;
import com.quin.catancraft.map.MapRegion;
import com.quin.catancraft.map.TerritoryDefinition;
import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = CatanCraft.MOD_ID)
public final class ZoneOutlines {
    enum Mode { OFF, NEARBY, SURVEY }

    static final int MAX_SHAPES = 512;
    private static final String VERSION = "2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(CatanCraft.MOD_ID, "zone_outlines"))
            .networkProtocolVersion(() -> VERSION)
            .clientAcceptedVersions(NetworkRegistry.acceptMissingOr(VERSION))
            .serverAcceptedVersions(NetworkRegistry.acceptMissingOr(VERSION)).simpleChannel();
    private static final WeakHashMap<ServerPlayer, Mode> preferences = new WeakHashMap<>();
    private static final WeakHashMap<ServerPlayer, Snapshot> last = new WeakHashMap<>();

    private ZoneOutlines() {}

    @Mod.EventBusSubscriber(modid = CatanCraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent
        public static void common(FMLCommonSetupEvent event) {
            CHANNEL.registerMessage(0, Snapshot.class, Snapshot::encode, Snapshot::decode,
                    Snapshot::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        }
    }

    record Snapshot(String dimension, List<OutlineShape> shapes) {
        Snapshot { shapes = List.copyOf(shapes); }

        void encode(FriendlyByteBuf buf) {
            buf.writeUtf(dimension, 256);
            buf.writeCollection(shapes, (out, shape) -> shape.write(out));
        }

        static Snapshot decode(FriendlyByteBuf buf) {
            String dimension = buf.readUtf(256);
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_SHAPES) throw new IllegalArgumentException("Invalid outline count");
            List<OutlineShape> shapes = new ArrayList<>(count);
            for (int i = 0; i < count; i++) shapes.add(OutlineShape.read(buf));
            return new Snapshot(dimension, shapes);
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> OutlineRenderer.accept(this)));
            ctx.setPacketHandled(true);
        }
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        var command = Commands.literal("outlines").executes(ctx -> {
            ctx.getSource().sendSuccess(() -> Component.literal("Nearby outlines the active capture area and delivery counters. Survey also shows inactive monuments and city siege planning bounds. Use /outlines nearby, survey or off."), false);
            return 1;
        });
        for (Mode mode : Mode.values()) {
            String name = mode.name().toLowerCase(java.util.Locale.ROOT);
            command.then(Commands.literal(name).executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                preferences.put(p, mode);
                update(p, true);
                String text = switch (mode) {
                    case OFF -> "Zone outlines off.";
                    case NEARBY -> "Nearby outlines: solid red capture boundary, cyan dispatch box, green receiving box.";
                    case SURVEY -> "Survey outlines: short red dashes for inactive monuments, long red dashes with uprights for city siege planning. A planning boundary does not indicate an active siege.";
                };
                p.sendSystemMessage(Component.literal(text));
                return 1;
            }));
        }
        event.getDispatcher().register(command);
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player
                && player.tickCount % 10 == 0) update(player, false);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            preferences.remove(p);
            last.remove(p);
        }
    }

    private static void update(ServerPlayer p, boolean force) {
        if (!CHANNEL.isRemotePresent(p.connection.connection)) return;
        String dimension = p.level().dimension().location().toString();
        List<OutlineShape> shapes = new ArrayList<>();
        Mode mode = preferences.getOrDefault(p, Mode.NEARBY);
        if (mode != Mode.OFF) {
            CatanSavedData data = CatanSavedData.get(p.server);
            MonumentData active = data.activeMonument();
            for (MonumentData site : data.monuments()) {
                boolean isActive = active != null && active.id().equals(site.id());
                if (dimension.equals(site.dimensionId()) && (isActive || mode == Mode.SURVEY)) {
                    addMonument(p, shapes, site, isActive);
                }
            }
            if (mode == Mode.SURVEY) {
                for (TerritoryDefinition city : MapDefinitionManager.territories()) {
                    MapRegion region = city.siegeRegion();
                    if (region != null && region.isReasonable() && dimension.equals(city.dimension())) {
                        addNearby(p, shapes, OutlineShape.siege(region), 48);
                    }
                }
            }
            DeliveryRoute.current().ifPresent(route -> {
                addCounter(p, shapes, dimension, route.pickup(), true);
                addCounter(p, shapes, dimension, route.dropoff(), false);
            });
        }
        Snapshot snap = new Snapshot(dimension, shapes);
        if (force || !snap.equals(last.get(p))) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), snap);
            last.put(p, snap);
        }
    }

    private static void addMonument(ServerPlayer p, List<OutlineShape> shapes, MonumentData site, boolean active) {
        if (!AuthoredCaptureFloors.applies(MapDefinitionManager.mapId())) {
            addNearby(p, shapes, OutlineShape.monument(site, active), 48);
            return;
        }
        // Cull by authored floor coordinates before asking the world to verify the local fixture.
        if (AuthoredCaptureFloors.boxes(site).stream().map(box -> floor(box, active))
                .noneMatch(s -> s.near(p.getX(), p.getY(), p.getZ(), 48))) return;
        var boxes = AuthoredCaptureFloors.boxes(p.serverLevel(), site);
        if (boxes.isEmpty() || boxes.size() > MAX_SHAPES - shapes.size()) return;
        for (var box : boxes) shapes.add(floor(box, active));
    }

    private static OutlineShape floor(AuthoredCaptureFloors.Box b, boolean active) {
        return OutlineShape.interior(active, b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
    }

    private static void addNearby(ServerPlayer p, List<OutlineShape> shapes, OutlineShape s, int range) {
        if (shapes.size() < MAX_SHAPES && s.near(p.getX(), p.getY(), p.getZ(), range)) shapes.add(s);
    }

    private static void addCounter(ServerPlayer p, List<OutlineShape> shapes, String dimension,
                                   DeliveryRoute.Endpoint endpoint, boolean dispatch) {
        OutlineShape s = OutlineShape.counter(dispatch, endpoint.x(), endpoint.y(), endpoint.z());
        if (dimension.equals(endpoint.dimension()) && shapes.size() < MAX_SHAPES
                && s.near(p.getX(), p.getY(), p.getZ(), 12) && endpoint.isClear(p.serverLevel())) {
            shapes.add(s);
        }
    }
}
