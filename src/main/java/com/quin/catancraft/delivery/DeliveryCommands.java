package com.quin.catancraft.delivery;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class DeliveryCommands {
    private static final String[] COMPASS = {"east", "southeast", "south", "southwest", "west", "northwest", "north", "northeast"};

    private static final Map<ServerPlayer, String> lastStop = new WeakHashMap<>();

    private DeliveryCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("delivery")
                .executes(guard(DeliveryCommands::show))
                .then(Commands.literal("status").executes(guard(DeliveryCommands::show)))
                .then(Commands.literal("receipt").executes(guard(DeliveryCommands::receipt)))
                .then(Commands.literal("pickup").executes(guard(DeliveryCommands::pickup)))
                .then(Commands.literal("turnin")
                        .executes(guard(ctx -> turnIn(ctx, null)))
                        .then(Commands.argument("waybill", UuidArgument.uuid())
                                .executes(guard(ctx -> turnIn(ctx, UuidArgument.getUuid(ctx, "waybill"))))))
                .then(Commands.literal("abandon")
                        .then(Commands.argument("waybill", UuidArgument.uuid())
                                .executes(guard(DeliveryCommands::abandon)))));
    }

    private static Command<CommandSourceStack> guard(Command<CommandSourceStack> action) {
        return ctx -> {
            try {
                return action.run(ctx);
            } catch (IllegalStateException ex) {
                return fail(ctx, ex.getMessage());
            }
        };
    }

    /** Only entering a surveyed stop prompts the courier. No ticket or chunk load is required. */
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
                player.sendSystemMessage(Component.literal(stop.name() + " roadside handoff  ")
                        .append(button("Open waybill", "/delivery")));
            }
        }
    }

    private static int show(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        DeliveryLedger.Waybill bill = DeliveryLedger.get(p.server).active(p.getUUID());
        DeliveryRoute route = DeliveryRoute.current().orElse(null);
        if (bill != null) {
            p.sendSystemMessage(Component.literal("Waybill • " + DeliveryActions.CARGO).withStyle(ChatFormatting.GOLD));
            if (route == null || !route.pickup().key().equals(bill.origin())
                    || !route.dropoff().key().equals(bill.destination())) {
                p.sendSystemMessage(Component.literal("This route is no longer available."));
            } else {
                directions(p, route.dropoff());
                p.sendSystemMessage(Component.literal("From the depot, go south to Z 4, west to X 44, then south to Z 82. The refinery yard is west of the road."));
                if (at(p, route.dropoff()) && route.dropoff().isClear(p.serverLevel())) {
                    p.sendSystemMessage(button("Unload the crate", "/delivery turnin " + bill.id()));
                }
            }
            p.sendSystemMessage(button("Abandon this waybill", "/delivery abandon " + bill.id()));
            return 1;
        }
        if (route == null) return fail(ctx, "There is no surveyed delivery route on this map.");
        p.sendSystemMessage(Component.literal("Depot delivery • Pump fittings").withStyle(ChatFormatting.GOLD));
        p.sendSystemMessage(Component.literal("Collect " + DeliveryActions.CARGO + " at " + route.pickup().name()
                + " and take it to " + route.dropoff().name() + ". Stand on the roadside gravel at each stop."));
        directions(p, route.pickup());
        if (at(p, route.pickup()) && route.pickup().isClear(p.serverLevel())) p.sendSystemMessage(button("Pick up the crate", "/delivery pickup"));
        if (DeliveryLedger.get(p.server).latestReceipt(p.getUUID()) != null) {
            p.sendSystemMessage(button("Last delivery receipt", "/delivery receipt"));
        }
        return 1;
    }

    private static int receipt(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        DeliveryLedger.Waybill closed = DeliveryLedger.get(player.server).latestReceipt(player.getUUID());
        if (closed == null) return fail(ctx, "You have no completed delivery yet.");
        player.sendSystemMessage(Component.literal("Signed delivery receipt • " + closed.id()).withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int pickup(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (p.isSpectator() || !p.isAlive()) return fail(ctx, "You must be present as a living player to collect cargo.");
        DeliveryRoute route = DeliveryRoute.current().orElse(null);
        if (route == null) return fail(ctx, "There is no surveyed delivery route on this map.");
        DeliveryLedger ledger = DeliveryLedger.get(p.server);
        boolean hadCargo = ledger.active(p.getUUID()) != null;
        checkPad(p, route.pickup());
        DeliveryActions.pickup(ledger, p.getUUID(), route, dimension(p), p.getX(), p.getY(), p.getZ());
        if (!hadCargo) p.playNotifySound(SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.75F, 1.0F);
        p.sendSystemMessage(Component.literal(hadCargo ? "You already have this crate." : "Crate collected. The load is recorded on your waybill.")
                .withStyle(ChatFormatting.GREEN));
        return show(ctx);
    }

    private static int turnIn(CommandContext<CommandSourceStack> ctx, UUID id) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (p.isSpectator() || !p.isAlive()) return fail(ctx, "You must be present as a living player to unload cargo.");
        DeliveryLedger ledger = DeliveryLedger.get(p.server);
        if (id == null) {
            DeliveryLedger.Waybill active = ledger.active(p.getUUID());
            if (active == null) return fail(ctx, "You have no cargo to unload. Open /delivery to find a job.");
            id = active.id();
        }
        DeliveryRoute route = DeliveryRoute.current().orElse(null);
        if (route == null) return fail(ctx, "This route is unavailable. Open /delivery to check your waybill.");
        checkPad(p, route.dropoff());
        boolean changed = DeliveryActions.turnIn(ledger, p.getUUID(), id, route, dimension(p), p.getX(), p.getY(), p.getZ());
        if (changed) p.playNotifySound(SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 0.75F, 1.0F);
        p.sendSystemMessage(Component.literal(changed ? "Crate delivered to " + route.dropoff().name() + "."
                : "This crate was already delivered.").withStyle(ChatFormatting.GREEN));
        p.sendSystemMessage(Component.literal("Delivery receipt " + id).withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int abandon(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        boolean closed = DeliveryLedger.get(p.server).cancel(p.getUUID(), UuidArgument.getUuid(ctx, "waybill"));
        p.sendSystemMessage(Component.literal(closed ? "Load abandoned. You can collect a new one at the depot."
                : "That waybill was already abandoned."));
        return 1;
    }

    private static void checkPad(ServerPlayer p, DeliveryRoute.Endpoint stop) {
        if (at(p, stop) && !stop.isClear(p.serverLevel())) {
            throw new IllegalStateException("The roadside pad at " + stop.name() + " is obstructed.");
        }
    }

    private static void directions(ServerPlayer p, DeliveryRoute.Endpoint stop) {
        String location = stop.name() + " (" + stop.x() + ", " + stop.y() + ", " + stop.z() + ")";
        String hint;
        if (!dimension(p).equals(stop.dimension())) {
            hint = "in " + stop.dimension();
        } else if (at(p, stop)) {
            hint = stop.isClear(p.serverLevel()) ? "you are at the handoff" : "the handoff pad is obstructed";
        } else {
            double dx = stop.x() + 0.5 - p.getX();
            double dz = stop.z() + 0.5 - p.getZ();
            long distance = Math.round(Math.hypot(dx, dz));
            int heading = Math.floorMod((int) Math.round(Math.atan2(dz, dx) / (Math.PI / 4)), 8);
            hint = distance <= 2 ? "check the handoff elevation" : distance + " blocks " + COMPASS[heading];
        }
        p.sendSystemMessage(Component.literal(location + " • " + hint));
    }

    private static boolean at(ServerPlayer p, DeliveryRoute.Endpoint stop) {
        return stop.contains(dimension(p), p.getX(), p.getY(), p.getZ());
    }

    private static String dimension(ServerPlayer player) {
        return player.level().dimension().location().toString();
    }

    private static MutableComponent button(String label, String command) {
        return Component.literal("[" + label + "]").withStyle(s -> s.withColor(ChatFormatting.AQUA)
                .withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, String message) {
        ctx.getSource().sendFailure(Component.literal(message));
        return 0;
    }
}
