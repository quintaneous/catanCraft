package com.quin.catancraft.monument;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.data.MonumentData;
import com.quin.catancraft.data.MonumentType;
import com.quin.catancraft.world.MonumentRewards41;
import java.io.IOException;
import com.quin.catancraft.data.NationData;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class MonumentManager {
    public static final int CAPTURE_SECONDS = 120;
    public static final int CAPTURE_TICKS = CAPTURE_SECONDS * 20;
    public static final long ROTATION_GAP_TICKS = 5L * 60L * 20L;
    private static long lastProcessedSecond = Long.MIN_VALUE;
    private static MinecraftServer clockOwner;
    private static long lastRewardWarning = Long.MIN_VALUE;
    private static String lastRewardProblem = "";

    private MonumentManager() {}

    public static void tick(MinecraftServer server) {
        if (clockOwner != server) {
            clockOwner = server; lastProcessedSecond = Long.MIN_VALUE;
            lastRewardWarning = Long.MIN_VALUE; lastRewardProblem = "";
        }
        long gameTime = server.overworld().getGameTime();
        long second = gameTime / 20L;
        if (second == lastProcessedSecond) return;
        lastProcessedSecond = second;

        CatanSavedData data = CatanSavedData.get(server);
        try { tickSecond(server, data, gameTime); }
        catch (IOException | RuntimeException failure) { rewardHold(server, second, failure); }
    }

    private static void tickSecond(MinecraftServer server, CatanSavedData data, long gameTime) throws IOException {
        // Settle the recorded winner before considering present players or another round.
        if (MonumentRewards41.recover(server, data)) return;
        MonumentData active = data.activeMonument();

        if (active == null) {
            if (!data.monuments().isEmpty() && gameTime >= data.nextMonumentActivationGameTime()) {
                activateNext(server, data);
            }
            return;
        }

        if (!MonumentRewards41.readyActivation(server, data, active)) return;
        Set<UUID> present = nationsPresent(server, data, active);
        if (present.size() > 1) {
            sendZoneStatus(server, active, "CONTESTED • " + progressPercent(active) + "%");
            return;
        }

        if (present.isEmpty()) {
            active.addCaptureProgressTicks(-40);
            if (active.captureProgressTicks() == 0) active.setCapturingNationId(null);
            data.setDirty();
            return;
        }

        UUID nationId = present.iterator().next();
        if (!nationId.equals(active.capturingNationId())) {
            active.setCapturingNationId(nationId);
            active.setCaptureProgressTicks(0);
        }

        active.addCaptureProgressTicks(20);
        data.setDirty();

        NationData capturingNation = data.nation(nationId);
        String nationName = capturingNation == null ? "Unknown" : capturingNation.name();
        sendZoneStatus(
                server,
                active,
                "CAPTURING FOR " + nationName + " • " + progressPercent(active) + "%"
        );

        if (active.captureProgressTicks() >= CAPTURE_TICKS) {
            completeCapture(server, data, active, nationId);
        }
    }

    private static void rewardHold(MinecraftServer server, long second, Exception failure) {
        String problem = String.valueOf(failure.getMessage());
        if (!problem.equals(lastRewardProblem) || lastRewardWarning == Long.MIN_VALUE
                || second < lastRewardWarning || second - lastRewardWarning >= 60) {
            lastRewardProblem = problem; lastRewardWarning = second;
            CatanCraft.LOGGER.warn("Monument reward held; preserving saved capture and reward history", failure);
        }
        MonumentData active = CatanSavedData.get(server).activeMonument();
        if (active != null) sendZoneStatus(server, active, "REWARD RECOVERY PENDING");
    }

    public static boolean forceActivate(MinecraftServer server, CatanSavedData data, MonumentData monument) {
        if (monument == null || data.monument(monument.id()) != monument) return false;
        try {
            MonumentData previous = data.activeMonument();
            for (MonumentData existing : data.monuments())
                if (!MonumentRewards41.canReplace(server, data, existing)) return false;
            if (previous != null) previous.resetCapture();
            monument.resetCapture();
            data.setActiveMonumentId(monument.id());
            data.setLastActivatedMonumentId(monument.id());
            data.setNextMonumentActivationGameTime(0);
            data.setDirty();
            MonumentRewards41.prepareActivation(server, data, monument);
            announceActivation(server, monument);
            return true;
        } catch (IOException | RuntimeException failure) {
            rewardHold(server, server.overworld().getGameTime() / 20, failure);
            return false;
        }
    }

    private static void activateNext(MinecraftServer server, CatanSavedData data) {
        List<MonumentData> monuments = data.monuments().stream()
                .sorted((a, b) -> a.id().compareToIgnoreCase(b.id()))
                .toList();
        if (monuments.isEmpty()) return;

        int nextIndex = 0;
        String lastId = data.lastActivatedMonumentId();
        if (lastId != null) {
            for (int i = 0; i < monuments.size(); i++) {
                if (monuments.get(i).id().equals(lastId)) {
                    nextIndex = (i + 1) % monuments.size();
                    break;
                }
            }
        }

        MonumentData next = monuments.get(nextIndex);
        forceActivate(server, data, next);
    }

    private static Set<UUID> nationsPresent(MinecraftServer server, CatanSavedData data, MonumentData monument) {
        Set<UUID> result = new LinkedHashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String dimension = player.serverLevel().dimension().location().toString();
            if (!monument.contains(dimension, player.getX(), player.getY(), player.getZ())) continue;
            NationData nation = data.nationForPlayer(player.getUUID());
            if (nation != null) result.add(nation.id());
        }
        return result;
    }

    private static int progressPercent(MonumentData monument) {
        return Math.min(
                100,
                (int) Math.floor(
                        100.0 * monument.captureProgressTicks() / CAPTURE_TICKS
                )
        );
    }

    private static void sendZoneStatus(
            MinecraftServer server,
            MonumentData monument,
            String status
    ) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String dimension = player.serverLevel().dimension().location().toString();
            if (monument.contains(dimension, player.getX(), player.getY(), player.getZ())) {
                player.displayClientMessage(
                        Component.literal(monument.name() + " • " + status),
                        true
                );
            }
        }
    }

    private static void completeCapture(MinecraftServer server, CatanSavedData data,
                                        MonumentData monument, UUID nationId) throws IOException {
        MonumentRewards41.Result result = MonumentRewards41.completeCapture(server, data, monument, nationId);
        if (!result.complete()) return;
        NationData nation = data.nation(nationId);
        if (nation == null) throw new IOException("Recorded reward winner is unavailable");
        server.getPlayerList().broadcastSystemMessage(Component.literal(
                "[CatanCraft] " + nation.name() + " secured " + monument.name() + " and received its national reward."), false);
        Component payout = Component.literal("[CatanCraft] " + monument.name() + " capture rewards: " + result.description())
                .withStyle(ChatFormatting.GREEN);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            NationData playerNation = data.nationForPlayer(player.getUUID());
            if (playerNation != null && nation.id().equals(playerNation.id())) player.sendSystemMessage(payout);
        }
        CatanCraft.LOGGER.info("Nation {} captured monument {} with saved reward {}", nation.name(), monument.id(), result.rewardId());
    }

    private static void announceActivation(MinecraftServer server, MonumentData monument) {
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("[CatanCraft] Monument active: " + monument.name() +
                        " at X=" + monument.x() +
                        " Y=" + monument.y() +
                        " Z=" + monument.z() +
                        ". Hold the zone for " + CAPTURE_SECONDS + " seconds."), false);
    }
}
