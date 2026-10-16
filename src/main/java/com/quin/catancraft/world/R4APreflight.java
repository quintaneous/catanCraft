package com.quin.catancraft.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.quin.catancraft.map.MapAnchor;
import com.quin.catancraft.map.MapDefinitionManager;
import com.quin.catancraft.map.MapRegion;
import com.quin.catancraft.map.TerritoryDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * R4-A live-world, read-only probe of Blender's authored-world defense
 * contracts. No setBlock, currency, save marker or chunk load is performed.
 * R4 purchases are explicitly NOT enabled by any status this class returns.
 */
public final class R4APreflight {
    public static final String ARCHIVE_NAME =
            "CatanCraft_R4A_Placement_Ready_Assets.zip";
    private static final String EXPECTED_SHA256 =
            "19b7f649164bd9b256784394f0104fdea66a99d57b8329d7790e75d578c34032";
    private static final int MAX_DETAILS = 8;
    private static final int MAX_CHECKS = 100_000;

    public record Report(boolean eligible, int checked, int mismatches,
                         String headline, List<String> details) {}
    public record SocketSummary(int total, int candidates, int rejected,
                                List<String> candidatesPreview) {}

    private R4APreflight() {}

    public static Path archivePath() {
        return FMLPaths.CONFIGDIR.get().resolve("catancraft").resolve(ARCHIVE_NAME);
    }

    private static void checkArchive(Path file) throws Exception {
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("Missing " + file +
                    "; install the R4-A sidecar archive next to schematics_bundle.zip.");
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[32768];
            int n;
            while ((n = in.read(buffer)) >= 0)
                if (n > 0) digest.update(buffer, 0, n);
        }
        StringBuilder actual = new StringBuilder();
        for (byte b : digest.digest()) actual.append(String.format("%02x", b & 0xff));
        if (!EXPECTED_SHA256.equals(actual.toString())) {
            throw new IllegalStateException("R4-A archive SHA256 mismatch; refusing " +
                    "unknown or modified contract. Expected " + EXPECTED_SHA256);
        }
    }

    private static JsonObject readObject(ZipFile zip, String entry) throws Exception {
        ZipEntry zipEntry = zip.getEntry(entry);
        if (zipEntry == null) throw new IllegalStateException("Missing R4-A contract: " + entry);
        try (InputStreamReader reader = new InputStreamReader(
                zip.getInputStream(zipEntry), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static JsonObject findRow(JsonObject survey, String territory, String socket) {
        for (JsonElement element : survey.getAsJsonArray("rows")) {
            JsonObject row = element.getAsJsonObject();
            if (territory.equalsIgnoreCase(row.get("territory_id").getAsString())
                    && socket.equalsIgnoreCase(row.get("socket_id").getAsString()))
                return row;
        }
        return null;
    }

    public static SocketSummary socketSummary(String territory) throws Exception {
        checkArchive(archivePath());
        try (ZipFile zip = new ZipFile(archivePath().toFile())) {
            JsonObject survey = readObject(zip, "contracts/territory-socket-survey.json");
            int count = 0, passed = 0, blocked = 0;
            List<String> examples = new ArrayList<>();
            for (JsonElement elem : survey.getAsJsonArray("rows")) {
                JsonObject row = elem.getAsJsonObject();
                if (!row.get("territory_id").getAsString().equalsIgnoreCase(territory))
                    continue;
                count++;
                boolean suitable = "PASS".equals(row.get("condition").getAsString())
                        && row.get("module_id").getAsString().startsWith("r4a_")
                        && !row.get("module_id").getAsString().equals("r4a_gate_wing");
                if (suitable) {
                    passed++;
                    if (examples.size() < 20)
                        examples.add(row.get("socket_id").getAsString());
                } else blocked++;
            }
            return new SocketSummary(count, passed, blocked, examples);
        }
    }

    public static Report inspect(MinecraftServer server, String territoryId,
                                 String socketId) {
        String territory = territoryId.toUpperCase(Locale.ROOT);
        String socket = socketId.toLowerCase(Locale.ROOT);
        int[] counts = new int[2];
        List<String> details = new ArrayList<>();
        try {
            Path file = archivePath();
            checkArchive(file);
            try (ZipFile zip = new ZipFile(file.toFile())) {
                JsonObject survey = readObject(zip, "contracts/territory-socket-survey.json");
                if (!"CANDIDATES_ONLY".equals(survey.get("approval").getAsString()))
                    throw new IllegalStateException("Unknown R4-A candidate approval state");
                JsonObject row = findRow(survey, territory, socket);
                if (row == null) {
                    throw new IllegalStateException("No R4-A socket named " + territory + "/" + socket);
                }
                if (!"PASS".equals(row.get("condition").getAsString()))
                    throw new IllegalStateException("Offline authored-world survey rejected " +
                            socket + " (" + row.get("condition").getAsString() + ")");
                if (!row.get("module_id").getAsString().startsWith("r4a_") ||
                        row.get("module_id").getAsString().equals("r4a_gate_wing"))
                    throw new IllegalStateException("R4-B gate or gate-wing socket is not part of R4-A live preflight");
                if (row.get("blocked_pending_survey").getAsBoolean())
                    throw new IllegalStateException("Socket blocked pending survey");
                if (row.get("live_install_authorized").getAsBoolean())
                    throw new IllegalStateException("Unexpected live-install flag in asset contract");

                TerritoryDefinition def = MapDefinitionManager.territory(territory.toLowerCase(Locale.ROOT));
                if (def == null || def.cityCenter() == null)
                    throw new IllegalStateException("No active canonical map city for " + territory);
                MapAnchor center = def.cityCenter();
                JsonArray recordedCenter = row.getAsJsonArray("city_center_world_xyz");
                if (center.x() != at(recordedCenter, 0) ||
                        center.y() != at(recordedCenter, 1) ||
                        center.z() != at(recordedCenter, 2) ||
                        center.rotation() != row.get("city_rotation").getAsInt() ||
                        !def.dimension().equals(row.get("dimension").getAsString()))
                    throw new IllegalStateException("Active map differs from Blender R4-A city contract");
                ServerLevel level = null;
                for (ServerLevel candidate : server.getAllLevels()) {
                    if (candidate.dimension().location().toString().equals(def.dimension())) {
                        level = candidate;
                        break;
                    }
                }
                if (level == null)
                    throw new IllegalStateException("City dimension is not active");
                if (def.siegeRegion() == null || def.restorationRegion() == null)
                    throw new IllegalStateException("Missing approved siege/restoration bounds");

                JsonObject delta = readObject(zip, "contracts/placement-deltas/" +
                        territory + "_" + socket + ".json");
                if (!territory.equals(delta.get("territory_id").getAsString()) ||
                        !socket.equals(delta.get("socket_id").getAsString()) ||
                        !delta.get("install_allowed_by_static_survey").getAsBoolean())
                    throw new IllegalStateException("R4-A delta ownership/status mismatch");
                JsonArray anchor = row.getAsJsonArray("world_anchor_xyz");
                JsonArray deltaAnchor = delta.getAsJsonArray("world_anchor_xyz");
                for (int i = 0; i < 3; i++)
                    if (at(anchor, i) != at(deltaAnchor, i))
                        throw new IllegalStateException("R4-A world anchor mismatch");

                int ax = at(anchor, 0), ay = at(anchor, 1), az = at(anchor, 2);
                JsonArray writes = delta.getAsJsonArray("writes");
                if (writes.size() != row.get("write_mask_count").getAsInt())
                    throw new IllegalStateException("R4-A write-mask size mismatch");
                if (writes.size() > MAX_CHECKS)
                    throw new IllegalStateException("R4-A write mask exceeds probe limit");

                Set<Long> writePositions = new HashSet<>();
                List<BlockPos> intendedSolids = new ArrayList<>();
                for (JsonElement elem : writes) {
                    JsonObject w = elem.getAsJsonObject();
                    JsonArray relative = w.getAsJsonArray("relative_xyz");
                    BlockPos pos = new BlockPos(ax + at(relative, 0),
                            ay + at(relative, 1), az + at(relative, 2));
                    if (!writePositions.add(pos.asLong()))
                        throw new IllegalStateException("Duplicate delta voxel " + pos);
                    checkPos(level, def, pos, w.get("expected_before").getAsString(),
                            "write", counts, details);
                    if (!"minecraft:air".equals(w.get("after").getAsString()))
                        intendedSolids.add(pos);
                }
                for (JsonElement elem : delta.getAsJsonArray("support_preconditions")) {
                    JsonObject support = elem.getAsJsonObject();
                    JsonArray worldXZ = support.getAsJsonArray("world_xz");
                    int x = at(worldXZ, 0), z = at(worldXZ, 1);
                    for (JsonElement profile : support.getAsJsonArray("profile")) {
                        JsonArray pair = profile.getAsJsonArray();
                        checkPos(level, def, new BlockPos(x, at(pair, 0), z),
                                pair.get(1).getAsString(), "support", counts, details);
                    }
                }
                verifyReadOnlyCells(delta, "public_road_surface_read_only_conditions",
                        level, def, counts, details);
                verifyReadOnlyCells(delta, "public_road_empty_clearance_read_only_conditions",
                        level, def, counts, details);

                // Conservative: every above-grade cell inside the schematic
                // cuboid not explicitly written must remain empty. Never clear it.
                JsonObject bounds = row.getAsJsonObject("world_bounds_inclusive");
                JsonArray min = bounds.getAsJsonArray("min"), max = bounds.getAsJsonArray("max");
                int minX = at(min,0), maxX = at(max,0), minZ = at(min,2), maxZ = at(max,2);
                int minY = Math.max(ay, at(min,1)), maxY = at(max,1);
                long volume = (long)(maxX-minX+1)*(maxY-minY+1)*(maxZ-minZ+1);
                if (volume > MAX_CHECKS)
                    throw new IllegalStateException("R4-A clearance volume exceeds probe limit");
                for (int y = minY; y <= maxY; y++)
                    for (int z = minZ; z <= maxZ; z++)
                        for (int x = minX; x <= maxX; x++) {
                            BlockPos p = new BlockPos(x,y,z);
                            if (!writePositions.contains(p.asLong()))
                                checkPos(level, def, p, "minecraft:air",
                                        "empty-clearance", counts, details);
                        }

                // The full entity hitbox must not overlap a proposed solid
                // voxel. This is a read-only warning; it cannot lock a vehicle.
                AABB envelope = new AABB(minX, at(min,1), minZ,
                        maxX+1, at(max,1)+1, maxZ+1);
                for (Entity entity : level.getEntities((Entity)null, envelope,
                        candidate -> candidate.isAlive())) {
                    for (BlockPos p : intendedSolids) {
                        if (entity.getBoundingBox().intersects(new AABB(p))) {
                            mismatch("entity-overlap " + entity.getType() + " @ " + p,
                                    counts, details);
                            break;
                        }
                    }
                }
                String headline = counts[1] == 0
                        ? "R4-A " + territory + "/" + socket + ": " + counts[0] +
                          " current-world checks matched, but INSTALLATION REMAINS DISABLED."
                        : "R4-A " + territory + "/" + socket + ": " + counts[1] +
                          " mismatches (" + counts[0] + " conditions examined); installation disabled.";
                return new Report(counts[1] == 0, counts[0], counts[1],
                        headline, List.copyOf(details));
            }
        } catch (Exception err) {
            details.add(err.getMessage() == null ? err.toString() : err.getMessage());
            return new Report(false, counts[0], Math.max(1, counts[1]),
                    "R4-A preflight refused " + territory + "/" + socket +
                    "; NO BLOCKS CHANGED.", List.copyOf(details));
        }
    }

    private static void verifyReadOnlyCells(JsonObject delta, String key,
                                            ServerLevel level, TerritoryDefinition def,
                                            int[] counts, List<String> details) {
        if (!delta.has(key)) return;
        for (JsonElement element : delta.getAsJsonArray(key)) {
            JsonObject cell = element.getAsJsonObject();
            JsonArray xyz = cell.getAsJsonArray("world_xyz");
            checkPos(level, def, new BlockPos(at(xyz,0),at(xyz,1),at(xyz,2)),
                    cell.get("expected_before").getAsString(), key, counts, details);
        }
    }

    private static int at(JsonArray list, int n) {
        return list.get(n).getAsInt();
    }

    private static void checkPos(ServerLevel level, TerritoryDefinition def,
                                 BlockPos pos, String expected, String purpose,
                                 int[] counts, List<String> details) {
        if (counts[0] >= MAX_CHECKS) {
            mismatch("Safety check limit exceeded", counts, details);
            return;
        }
        counts[0]++;
        MapRegion siege = def.siegeRegion(), restoration = def.restorationRegion();
        if (!siege.contains(pos) || !restoration.contains(pos)) {
            mismatch(purpose + " out-of-siege-bounds " + pos, counts, details);
            return;
        }
        if (!level.hasChunkAt(pos)) {
            mismatch(purpose + " UNLOADED chunk @ " + pos, counts, details);
            return;
        }
        if (!sameState(level.getBlockState(pos), expected))
            mismatch(purpose + " changed block @ " + pos +
                    "; expected " + expected, counts, details);
    }

    private static void mismatch(String msg, int[] counts, List<String> details) {
        counts[1]++;
        if (details.size() < MAX_DETAILS) details.add(msg);
    }

    private static <T extends Comparable<T>> String currentProperty(
            BlockState current, Property<T> property) {
        return property.getName(current.getValue(property));
    }

    private static boolean sameState(BlockState state, String expected) {
        int start = expected.indexOf('[');
        String name = start < 0 ? expected : expected.substring(0, start);
        if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(name))
            return false;
        if (start < 0) return state.getProperties().isEmpty();
        if (!expected.endsWith("]")) return false;
        String inner = expected.substring(start+1,expected.length()-1);
        Map<String, String> expectedProps = new HashMap<>();
        for (String pair : inner.split(",")) {
            String[] parts = pair.split("=",2);
            if (parts.length != 2 || expectedProps.put(parts[0],parts[1]) != null)
                return false;
        }
        if (expectedProps.size() != state.getProperties().size()) return false;
        for (Property<?> p : state.getProperties()) {
            if (!currentProperty(state,p).equals(expectedProps.get(p.getName())))
                return false;
        }
        return true;
    }
}
