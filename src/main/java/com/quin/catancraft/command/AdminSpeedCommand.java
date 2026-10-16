package com.quin.catancraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Temporary admin-only map traversal, with exact ability restoration.
 * /speed toggles a 6x boost; /speed 2..10 sets a multiplier; /speed 1 resets.
 */
public final class AdminSpeedCommand {
    private static final int DEFAULT_MULTIPLIER = 6;
    private static final Map<UUID, SavedAbilities> ORIGINAL = new HashMap<>();

    private record SavedAbilities(boolean mayfly, boolean flying,
                                  float walkingSpeed, float flyingSpeed) {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("speed")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> toggle(ctx.getSource()))
                .then(Commands.argument("multiplier", IntegerArgumentType.integer(1, 10))
                        .executes(ctx -> apply(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "multiplier")))));
    }

    private static int toggle(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (ORIGINAL.containsKey(player.getUUID())) {
            restore(player);
            source.sendSuccess(() -> Component.literal(
                    "Admin speed disabled. Your original movement abilities are restored."), false);
        } else {
            boost(player, DEFAULT_MULTIPLIER);
            source.sendSuccess(() -> Component.literal(
                    "Admin speed enabled (x" + DEFAULT_MULTIPLIER
                            + "). Flight enabled. Use /speed again to turn off, or /speed 2-10 to adjust."), false);
        }
        return 1;
    }

    private static int apply(CommandSourceStack source, int multiplier)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (multiplier == 1) {
            restore(player);
            source.sendSuccess(() -> Component.literal(
                    "Admin speed disabled; original movement abilities restored."), false);
        } else {
            boost(player, multiplier);
            source.sendSuccess(() -> Component.literal(
                    "Admin speed x" + multiplier + " enabled. Flight enabled; /speed 1 resets."), false);
        }
        return 1;
    }

    private static void boost(ServerPlayer player, int multiplier) {
        Abilities abilities = player.getAbilities();
        ORIGINAL.computeIfAbsent(player.getUUID(),
                id -> new SavedAbilities(abilities.mayfly, abilities.flying,
                        abilities.getWalkingSpeed(), abilities.getFlyingSpeed()));
        abilities.mayfly = true;
        abilities.flying = true;
        abilities.setWalkingSpeed(0.1F * multiplier);
        abilities.setFlyingSpeed(0.05F * multiplier);
        player.onUpdateAbilities();
    }

    private static void restore(ServerPlayer player) {
        SavedAbilities prior = ORIGINAL.remove(player.getUUID());
        if (prior == null) return;
        Abilities abilities = player.getAbilities();
        abilities.mayfly = prior.mayfly();
        abilities.flying = prior.flying();
        abilities.setWalkingSpeed(prior.walkingSpeed());
        abilities.setFlyingSpeed(prior.flyingSpeed());
        player.onUpdateAbilities();
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            restore(player);
        }
    }
}
