package com.quin.catancraft.world;

import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.data.NationData;
import com.quin.catancraft.data.TerritoryBoundary;
import com.quin.catancraft.map.MapDefinitionManager;
import com.quin.catancraft.map.TerritoryDefinition;
import com.quin.catancraft.world.SiegeRules36.*;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Read-only, unregistered objective catalog and server presence adapter. No live
 * catalog, tick subscriber, command, damage permission or settlement is installed.
 * Reports are observations, NOT durable progress or authorization to start a siege.
 */
final class SiegeObjectives37 {
    /** Block-aligned half-open volume; player FEET must be inside all three axes. */
    record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        Box {
            if (minX < -30000000 || minZ < -30000000 || maxX > 30000000 || maxZ > 30000000
                    || (long) maxX - minX <= 0 || (long) maxX - minX > 64
                    || (long) maxZ - minZ <= 0 || (long) maxZ - minZ > 64
                    || (long) maxY - minY <= 0 || (long) maxY - minY > 32) {
                throw new IllegalArgumentException("Objective must have positive bounded extents");
            }
        }
        boolean contains(double x, double y, double z) {
            return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                    && x >= minX && x < maxX && y >= minY && y < maxY && z >= minZ && z < maxZ;
        }
        boolean overlaps(Box b) {
            return minX < b.maxX && maxX > b.minX && minY < b.maxY && maxY > b.minY
                    && minZ < b.maxZ && maxZ > b.minZ;
        }
    }

    record Profile(String city, String revision, String dimension, Box outer, Box townHall) {
        Profile {
            if (city == null || !city.matches("[a-z0-9_]{1,48}")) throw new IllegalArgumentException("Canonical city required");
            label(revision); label(dimension);
            Objects.requireNonNull(outer); Objects.requireNonNull(townHall);
            if (outer.overlaps(townHall)) throw new IllegalArgumentException("Objectives must not overlap");
        }
        Box box(Stage stage) {
            Objects.requireNonNull(stage);
            return stage == Stage.OUTER ? outer : townHall;
        }
    }

    /** Immutable server-lifetime registration. No file is adopted or rewritten.
     * A later durable service must retain the full profile in its original contract,
     * not resolve a saved siege against whatever geometry is current after restart.
     */
    static final class Registry {
        private final ServerLevel level;
        private final String world;
        private final Path root;
        private final long seed;
        private final Map<String, Profile> profiles;
        private final Map<String, List<TerritoryBoundary.Point>> boundaries;

        Registry(ServerLevel level, String world, Collection<Profile> profiles) throws IOException {
            this.level = Objects.requireNonNull(level); label(world); this.world = world;
            thread(level);
            this.root = level.getServer().getWorldPath(LevelResource.ROOT).toRealPath();
            this.seed = level.getSeed();
            if (profiles == null || profiles.isEmpty() || profiles.size() > 64) {
                throw new IllegalArgumentException("Explicit bounded objective catalog required");
            }
            Map<String, Profile> entries = new LinkedHashMap<>();
            Map<String, List<TerritoryBoundary.Point>> shapes = new LinkedHashMap<>();
            for (Profile profile : profiles) {
                Objects.requireNonNull(profile);
                if (entries.putIfAbsent(profile.city(), profile) != null) throw new IllegalArgumentException("Duplicate city objectives");
                TerritoryDefinition city = MapDefinitionManager.territory(profile.city());
                if (city == null || !city.dimension().equals(profile.dimension())
                        || !level.dimension().location().toString().equals(profile.dimension())) {
                    throw new IllegalArgumentException("Unknown city or mismatched objective dimension");
                }
                TerritoryBoundary boundary = city.toBoundary();
                for (Box box : List.of(profile.outer(), profile.townHall())) {
                    if (box.minY() < level.getMinBuildHeight() || box.maxY() > level.getMaxBuildHeight()) {
                        throw new IllegalArgumentException("Objective exceeds level build height");
                    }
                    // Follow existing block-column territory semantics, including concave polygons.
                    for (int x = box.minX(); x < box.maxX(); x++) {
                        for (int z = box.minZ(); z < box.maxZ(); z++) {
                            if (!boundary.contains(profile.dimension(), x, z)) throw new IllegalArgumentException("Objective leaves city boundary");
                        }
                    }
                }
                shapes.put(profile.city(), boundary.points());
            }
            List<Profile> all = List.copyOf(entries.values());
            for (int i = 0; i < all.size(); i++) for (int j = i + 1; j < all.size(); j++) {
                for (Box a : List.of(all.get(i).outer(), all.get(i).townHall())) {
                    for (Box b : List.of(all.get(j).outer(), all.get(j).townHall())) {
                        if (a.overlaps(b)) throw new IllegalArgumentException("Cities cannot share capture volumes");
                    }
                }
            }
            this.profiles = Map.copyOf(entries); this.boundaries = Map.copyOf(shapes);
        }

        Bound bind(Contract contract) throws IOException {
            Objects.requireNonNull(contract);
            Profile profile = profiles.get(contract.city());
            if (!world.equals(contract.world()) || profile == null) throw new IllegalArgumentException("Unregistered world/city contract");
            Bound bound = new Bound(this, profile, contract);
            validate(bound);
            return bound;
        }

        private void validate(Bound bound) throws IOException {
            thread(level);
            if (bound.registry != this || profiles.get(bound.profile.city()) != bound.profile
                    || level.getServer().getLevel(level.dimension()) != level || level.getSeed() != seed
                    || !root.equals(level.getServer().getWorldPath(LevelResource.ROOT).toRealPath())) {
                throw new IllegalArgumentException("Stale or foreign objective registration");
            }
            TerritoryDefinition current = MapDefinitionManager.territory(bound.profile.city());
            if (current == null || !current.dimension().equals(bound.profile.dimension())
                    || !current.toBoundary().points().equals(boundaries.get(bound.profile.city()))) {
                throw new IllegalArgumentException("Registered city boundary changed");
            }
        }
    }

    static final class Bound {
        private final Registry registry;
        private final Profile profile;
        private final Contract contract;
        private Bound(Registry registry, Profile profile, Contract contract) {
            this.registry = registry; this.profile = profile; this.contract = contract;
        }
        ServerLevel level() { return registry.level; }
        Profile profile() { return profile; }
        Contract contract() { return contract; }
    }

    record Report(long gameTime, Profile profile, Contract contract, int elapsedTicks,
                  Stage objective, int attackersOnline, int defendersOnline,
                  int attackersInside, int defendersInside) {
        Observation observation() { return new Observation(objective, attackersInside > 0, defendersInside > 0); }
        boolean bothNationsOnline() { return attackersOnline > 0 && defendersOnline > 0; }
    }

    /** Always obtains current roster, connections, membership and owner from server
     * state. Caller cannot supply presence/nation flags. Must be consumed in the same
     * serialized server tick against the CURRENT durable snapshot by a future service.
     */
    static Report sample(Bound bound, Snapshot snapshot) throws IOException {
        Objects.requireNonNull(bound); thread(bound.registry.level);
        var server = bound.registry.level.getServer();
        List<ServerPlayer> roster = server.getPlayerList().getPlayers().stream()
                .filter(p -> connected(p, bound.registry.level)).toList();
        return collect(bound, snapshot, CatanSavedData.get(server), roster);
    }

    static boolean connected(ServerPlayer player, ServerLevel level) {
        thread(level);
        return player != null && player.getServer() == level.getServer()
                && level.getServer().getPlayerList().getPlayer(player.getUUID()) == player
                && player.connection != null && player.connection.isAcceptingMessages()
                && !player.hasDisconnected() && !player.isRemoved();
    }

    private static Report collect(Bound bound, Snapshot snapshot, CatanSavedData data,
                                  List<ServerPlayer> roster) throws IOException {
        Objects.requireNonNull(snapshot); bound.registry.validate(bound);
        if (!bound.contract.equals(snapshot.contract()) || snapshot.terminal()) {
            throw new IllegalArgumentException("Foreign or completed siege has no active observation");
        }
        var city = data.territory(bound.contract.city());
        if (city == null || !bound.contract.defenders().equals(city.ownerNationId())
                || data.nation(bound.contract.attackers()) == null || data.nation(bound.contract.defenders()) == null) {
            throw new IllegalArgumentException("City ownership or participating nations changed");
        }
        int ao = 0, dO = 0, ai = 0, di = 0;
        Set<UUID> seen = new HashSet<>();
        Box active = bound.profile.box(snapshot.activeStage());
        for (ServerPlayer player : roster) {
            Objects.requireNonNull(player);
            if (!seen.add(player.getUUID())) throw new IllegalArgumentException("Duplicate player in authoritative roster");
            if (player.getServer() != bound.registry.level.getServer()) throw new IllegalArgumentException("Foreign server player");
            if (player.isRemoved()) continue;
            UUID nation = null;
            for (NationData candidate : data.nations()) {
                if (!candidate.containsMember(player.getUUID())) continue;
                if (nation != null) throw new IllegalArgumentException("Ambiguous player nation membership");
                nation = candidate.id();
            }
            boolean attacker = bound.contract.attackers().equals(nation);
            boolean defender = bound.contract.defenders().equals(nation);
            if (attacker) ao++; if (defender) dO++;
            if ((!attacker && !defender) || !player.isAlive() || player.isSpectator() || player.isCreative()
                    || player.serverLevel() != bound.registry.level
                    || !active.contains(player.getX(), player.getY(), player.getZ())) continue;
            if (attacker) ai++; if (defender) di++;
        }
        return new Report(bound.registry.level.getGameTime(), bound.profile, bound.contract,
                snapshot.elapsedTicks(), snapshot.activeStage(), ao, dO, ai, di);
    }

    /** Native fixture seam ONLY: supplied roster models connected sessions, while
     * real player fields and detached SavedData exercise the same collector. This
     * never inserts FakePlayers into PlayerList or pretends to authenticate clients.
     */
    static Report sampleFixture(Bound bound, Snapshot snapshot, CatanSavedData detached,
                                List<ServerPlayer> modeledConnectedPlayers) throws IOException {
        thread(bound.registry.level);
        var server = bound.registry.level.getServer();
        Path root = server.getWorldPath(LevelResource.ROOT).toRealPath();
        if (!(server instanceof GameTestServer) || !root.getFileName().toString().equals("world")
                || !root.getParent().getFileName().toString().equals("controlled-catancraft_objectives37-run")
                || !Files.readString(root.getParent().resolve(".catancraft-controlled-fixture-v1"))
                    .equals("CatanCraft synthetic flat fixture v1\n")
                || detached == CatanSavedData.get(server)) {
            throw new IllegalArgumentException("Requires controlled isolated fixture and detached data");
        }
        return collect(bound, snapshot, detached, List.copyOf(modeledConnectedPlayers));
    }

    private static void thread(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Objective observation requires server thread");
    }
    private static void label(String text) {
        if (text == null || text.isBlank() || text.length() > 128 || text.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Explicit objective/world label required");
        }
    }
    private SiegeObjectives37() {}
}
