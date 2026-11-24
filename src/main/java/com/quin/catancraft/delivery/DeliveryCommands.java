package com.quin.catancraft.delivery;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.WeakHashMap;

/** The board gives directions. Dispatch and receipt of cargo happen at the two blocks. */
public final class DeliveryCommands {
    private static final String[] COMPASS = {"east", "southeast", "south", "southwest", "west", "northwest", "north", "northeast"};
    private static final Map<ServerPlayer, String> lastStop = new WeakHashMap<>();

    private DeliveryCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("delivery")
                .executes(guard(DeliveryCommands::show))
                .then(Commands.literal("status").executes(guard(DeliveryCommands::show)))
                .then(Commands.literal("receipt").executes(guard(DeliveryCommands::receipt))));
    }

    private static Command<CommandSourceStack> guard(Command<CommandSourceStack> action) {
        return ctx -> {
            try {
                return action.run(ctx);
            } catch (IllegalStateException ex) {
                ctx.getSource().sendFailure(Component.literal(ex.getMessage()));
                return 0;
            }
        };
    }

    public static void promptArrivals(MinecraftServer server) {
        DeliveryRoute route = DeliveryRoute.current().orElse(null);
        if (route == null) {
            lastStop.clear();
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            DeliveryRoute.Endpoint stop = null;
            if (!player.isSpectator() && player.isAlive()) {
                if (at(player, route.pickup())) stop = route.pickup();
                else if (at(player, route.dropoff())) stop = route.dropoff();
            }
            if (stop == null) {
                lastStop.remove(player);
            } else if (!stop.key().equals(lastStop.get(player)) && stop.isClear(player.serverLevel())) {
                lastStop.put(player, stop.key());
                String action = stop == route.pickup() ? "Use the timber dispatch counter with an empty main hand."
                        : "Use the copper receiving counter with a sealed crate in your main hand.";
                player.sendSystemMessage(Component.literal(stop.name() + " • " + action));
            }
        }
    }

    private static int show(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        DeliveryRoute route = DeliveryRoute.current().orElse(null);
        if (route == null) {
            ctx.getSource().sendFailure(Component.literal("No delivery counters are registered on this map."));
            return 0;
        }
        boolean carrying = PhysicalDelivery.carried(p) != null;
        DeliveryRoute.Endpoint stop = carrying ? route.dropoff() : route.pickup();
        p.sendSystemMessage(Component.literal("Pump fittings • " + (carrying ? "In transit" : "Free delivery"))
                .withStyle(ChatFormatting.GOLD));
        if (carrying) {
            p.sendSystemMessage(Component.literal("Route map: /deliverymap").withStyle(ChatFormatting.AQUA));
            p.sendSystemMessage(Component.literal("Follow the road west to Meridian Exchange, then south and into the refinery yard. Right-click the copper counter with the sealed crate in your main hand."));
        } else {
            p.sendSystemMessage(Component.literal("Right-click the timber dispatch counter with an empty main hand to collect a sealed crate for Copperline Refinery. A rival can take the crate if you drop it or die. The first carrier to deliver it earns a receipt."));
        }
        p.sendSystemMessage(Component.literal(stop.name() + " (" + stop.x() + ", " + stop.y() + ", " + stop.z() + ") • " + bearing(p, stop)));
        if (DeliveryLedger.get(p.server).latestReceipt(p.getUUID()) != null) {
            p.sendSystemMessage(Component.literal("[Last delivery receipt]").withStyle(s -> s.withColor(ChatFormatting.AQUA)
                    .withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/delivery receipt"))));
        }
        return 1;
    }

    private static int receipt(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        DeliveryLedger.Waybill closed = DeliveryLedger.get(player.server).latestReceipt(player.getUUID());
        if (closed == null) {
            ctx.getSource().sendFailure(Component.literal("You have no completed delivery yet."));
            return 0;
        }
        player.sendSystemMessage(Component.literal("Signed delivery receipt • " + closed.id()).withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static boolean at(ServerPlayer p, DeliveryRoute.Endpoint stop) {
        return stop.contains(p.level().dimension().location().toString(), p.getX(), p.getY(), p.getZ());
    }

    private static String bearing(ServerPlayer p, DeliveryRoute.Endpoint stop) {
        if (!p.level().dimension().location().toString().equals(stop.dimension())) return "in " + stop.dimension();
        if (at(p, stop)) return stop.isClear(p.serverLevel()) ? "counter in reach" : "check the counter area";
        double dx = stop.x() + 0.5 - p.getX(), dz = stop.z() + 0.5 - p.getZ();
        long distance = Math.round(Math.hypot(dx, dz));
        if (distance <= 2) return "check the counter elevation";
        int heading = Math.floorMod((int) Math.round(Math.atan2(dz, dx) / (Math.PI / 4)), 8);
        return distance + " blocks " + COMPASS[heading];
    }
}
