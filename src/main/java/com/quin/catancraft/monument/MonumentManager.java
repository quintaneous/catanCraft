package com.quin.catancraft.monument;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.data.MonumentData;
import com.quin.catancraft.data.MonumentType;
import com.quin.catancraft.data.ResourceType;
import com.quin.catancraft.data.NationData;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.text.NumberFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MonumentManager {
    public static final int CAPTURE_SECONDS = 120;
    public static final int CAPTURE_TICKS = CAPTURE_SECONDS * 20;
    public static final long ROTATION_GAP_TICKS = 5L * 60L * 20L;
    private static long lastProcessedSecond = Long.MIN_VALUE;

    private MonumentManager() {}

    public static void tick(MinecraftServer server) {
        long gameTime = server.overworld().getGameTime();
        long second = gameTime / 20L;
        if (second == lastProcessedSecond) return;
        lastProcessedSecond = second;

        CatanSavedData data = CatanSavedData.get(server);
        MonumentData active = data.activeMonument();

        if (active == null) {
            if (!data.monuments().isEmpty() && gameTime >= data.nextMonumentActivationGameTime()) {
                activateNext(server, data);
            }
            return;
        }

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

    public static boolean forceActivate(MinecraftServer server, CatanSavedData data, MonumentData monument) {
        if (monument == null) return false;
        MonumentData previous = data.activeMonument();
        if (previous != null) previous.resetCapture();
        monument.resetCapture();
        data.setActiveMonumentId(monument.id());
        data.setLastActivatedMonumentId(monument.id());
        data.setNextMonumentActivationGameTime(0);
        data.setDirty();
        announceActivation(server, monument);
        return true;
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
        next.resetCapture();
        data.setActiveMonumentId(next.id());
        data.setLastActivatedMonumentId(next.id());
        data.setDirty();
        announceActivation(server, next);
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
                                        MonumentData monument, UUID nationId) {
        NationData nation = data.nation(nationId);
        if (nation == null) {
            monument.resetCapture();
            data.setDirty();
            return;
        }

        MonumentType type = monument.type();
        nation.addTreasury(type.moneyReward());
        type.resourceRewards().forEach(nation::addResource);

        server.getPlayerList().broadcastSystemMessage(
                Component.literal("[CatanCraft] " + nation.name() + " secured " +
                        monument.name() + " and received its national reward."), false);

        // Keep exact rewards private to the capturing nation, while the public
        // capture announcement still informs the entire server.
        StringBuilder rewards = new StringBuilder()
                .append("+$")
                .append(NumberFormat.getIntegerInstance(Locale.US).format(type.moneyReward()))
                .append(" treasury");
        type.resourceRewards().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(reward -> rewards.append(", +")
                        .append(reward.getValue())
                        .append(" ")
                        .append(displayResource(reward.getKey())));
        Component payout = Component.literal(
                "[CatanCraft] " + monument.name() + " capture rewards: " + rewards)
                .withStyle(ChatFormatting.GREEN);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            NationData playerNation = data.nationForPlayer(player.getUUID());
            if (playerNation != null && nation.id().equals(playerNation.id())) {
                player.sendSystemMessage(payout);
            }
        }

        monument.resetCapture();
        data.setActiveMonumentId(null);
        data.setNextMonumentActivationGameTime(
                server.overworld().getGameTime() + ROTATION_GAP_TICKS);
        data.setDirty();

        CatanCraft.LOGGER.info("Nation {} captured monument {} ({})",
                nation.name(), monument.id(), type.id());
    }

    private static String displayResource(ResourceType resource) {
        String[] words = resource.id().split("_");
        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (label.length() > 0) label.append(' ');
            label.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1));
        }
        return label.toString();
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
