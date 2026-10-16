package com.quin.catancraft.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.data.TerritoryData;
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
 * R5-1 read-only survey of Blender's existing-world sparse migration contracts.
 * Never writes blocks, changes CatanCraft saved data, gives/takes currency,
 * loads chunks, or approves construction.
 */
public final class R5ReadOnlyPreflight {
    public static final String ARCHIVE_NAME = "CatanCraft_R5_City_Rework_And_Civic_Assets.zip";
    private static final String ARCHIVE_SHA256 =
            "679b9d94d2f96175705e2e85f2e36aa0146cd8dd28a71826e25998c6c9755521";
    static final String INDEX_NAME = "R5_READ_ONLY_PROBE_INDEX.zip";
    private static final String INDEX_SHA256 =
            "4f5a445347adbb62e73d1c38bfab5ff850c7307733a619847d9eeac9245834ba";
    private static final int MAX_ISSUES = 9;
    private static final int MAX_PROBES = 85_000;

    public record Report(boolean matched, long examined, long mismatches,
                         String headline, List<String> issues) {}
    public record Overview(int groups, int civicSlots, List<String> groupNames) {}

    private static final class Stats {
        long examined;
        long mismatches;
        final List<String> issues = new ArrayList<>();
        final Map<String, Integer> kinds = new java.util.LinkedHashMap<>();
        String auditName = "r5";
        void title(String name) { auditName = name; }
        void logStart() { CatanCraft.LOGGER.info("R5 DIAGNOSTIC BEGIN {} (read-only)", auditName); }
        void logFinish() { CatanCraft.LOGGER.info("R5 DIAGNOSTIC SUMMARY {} checked={} mismatches={} reasons={}",auditName,examined,mismatches,kinds); CatanCraft.LOGGER.info("R5 DIAGNOSTIC END {}",auditName); }
        void fail(String description) {
            mismatches++;
            if (issues.size() < MAX_ISSUES) issues.add(description);
            String kind = description.startsWith("source-air clearance") ? "source-air"
                    : description.startsWith("civic write") ? "civic-write"
                    : description.startsWith("support") ? "support"
                    : description.startsWith("R5 group") ? "group-write"
                    : description.contains("UNLOADED") ? "unloaded"
                    : description.startsWith("Civic requires") ? "prerequisite" : "other";
            kinds.merge(kind, 1, Integer::sum);
            CatanCraft.LOGGER.info("R5 DIAGNOSTIC {} | #{} [{}] {}",
                    auditName, mismatches, kind, description);
        }
        Report report(String prefix) {
            String message = prefix + " — " + examined + " checks, " + mismatches +
                    " mismatches. READ-ONLY; R5 INSTALLATION DISABLED.";
            return new Report(mismatches == 0, examined, mismatches,
                    message, List.copyOf(issues));
        }
    }

    private R5ReadOnlyPreflight() {}

    public static Path archivePath() {
        return FMLPaths.CONFIGDIR.get().resolve("catancraft").resolve(ARCHIVE_NAME);
    }

    private static Path indexPath() {
        return FMLPaths.CONFIGDIR.get().resolve("catancraft").resolve(INDEX_NAME);
    }

    static void verifyIndex() throws Exception {
        Path file = indexPath();
        if (!Files.isRegularFile(file))
            throw new IllegalStateException("Missing R5 read-only probe index at " + file);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input=Files.newInputStream(file)) {
            byte[] bytes=new byte[32768];
            int count;
            while ((count=input.read(bytes))!=-1) digest.update(bytes,0,count);
        }
        StringBuilder actual=new StringBuilder();
        for (byte b : digest.digest())
            actual.append(String.format("%02x", b & 0xff));
        if (!INDEX_SHA256.equals(actual.toString()))
            throw new IllegalStateException("R5 probe index SHA-256 mismatch.");
    }

    static void verifyArchive() throws Exception {
        Path path = archivePath();
        if (!Files.isRegularFile(path))
            throw new IllegalStateException("Missing R5 sidecar ZIP at " + path);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) != -1)
                digest.update(buffer, 0, n);
        }
        StringBuilder actual = new StringBuilder();
        for (byte b : digest.digest())
            actual.append(String.format("%02x", b & 0xff));
        if (!ARCHIVE_SHA256.equals(actual.toString()))
            throw new IllegalStateException("R5 sidecar SHA-256 mismatch; unknown contract refused.");
    }

    private static JsonObject readJson(ZipFile zip, String name) throws Exception {
        ZipEntry file = zip.getEntry(name);
        if (file == null)
            throw new IllegalStateException("R5 contract not found: " + name);
        if (file.getSize() > 16_000_000L)
            throw new IllegalStateException("Oversized R5 JSON: " + name);
        try (InputStreamReader in = new InputStreamReader(
                zip.getInputStream(file), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(in).getAsJsonObject();
        }
    }

    private static int at(JsonArray a, int i) { return a.get(i).getAsInt(); }

    private static String canonicalId(String city) {
        String id = city.trim().toLowerCase(Locale.ROOT);
        if (!id.matches("[a-p]"))
            throw new IllegalArgumentException("City must be one of a through p.");
        return id;
    }

    private static TerritoryDefinition requireCity(String id) {
        TerritoryDefinition city = MapDefinitionManager.territory(id);
        if (city == null || city.cityCenter() == null ||
                city.restorationRegion() == null || city.siegeRegion() == null)
            throw new IllegalStateException("Map definition for city " + id +
                    " is incomplete; refusing R5 preflight.");
        return city;
    }

    private static ServerLevel level(MinecraftServer server, TerritoryDefinition city) {
        for (ServerLevel level : server.getAllLevels())
            if (level.dimension().location().toString().equals(city.dimension()))
                return level;
        throw new IllegalStateException("R5 city dimension is not running.");
    }

    private static void checkCenter(JsonArray center, int rotation, TerritoryDefinition city) {
        MapAnchor active = city.cityCenter();
        if (active.x() != at(center,0) || active.y() != at(center,1) ||
                active.z() != at(center,2) || active.rotation() != rotation)
            throw new IllegalStateException(
                    "Current server city center/rotation differs from R5 contract.");
    }

    private static JsonObject findSocket(JsonObject root, String id, String kind) {
        for (JsonElement el : root.getAsJsonArray("rows")) {
            JsonObject row = el.getAsJsonObject();
            if (row.get("territory_id").getAsString().equalsIgnoreCase(id)
                    && row.get("module_id").getAsString().equalsIgnoreCase(kind))
                return row;
        }
        return null;
    }

    public static Overview overview(String territory) throws Exception {
        String id = canonicalId(territory);
        verifyArchive();
        try (ZipFile zip = new ZipFile(archivePath().toFile())) {
            JsonObject sockets = readJson(zip, "contracts/city-r5-candidate-sockets.json");
            int slots = 0;
            for (JsonElement el : sockets.getAsJsonArray("rows"))
                if (id.equalsIgnoreCase(el.getAsJsonObject()
                        .get("territory_id").getAsString())) slots++;
            verifyIndex();
            List<String> names = new ArrayList<>();
            try (ZipFile index = new ZipFile(indexPath().toFile())) {
                String prefix = "contracts/groups/" + id.toUpperCase(Locale.ROOT) + "/";
                for (java.util.Enumeration<? extends ZipEntry> entries = index.entries();
                        entries.hasMoreElements();) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.getName().startsWith(prefix) &&
                            entry.getName().endsWith(".json"))
                        names.add(entry.getName().substring(prefix.length(),
                                entry.getName().length()-5));
                }
            }
            names.sort(String::compareTo);
            return new Overview(names.size(), slots, List.copyOf(names));
        }
    }

    public static Report civic(MinecraftServer server, String territory, String requestedKind) {
        Stats stats = new Stats();
        String label = territory + "/" + requestedKind;
        stats.title("civic/" + label);
        stats.logStart();
        try {
            String id = canonicalId(territory);
            String kind = switch (requestedKind.toLowerCase(Locale.ROOT)) {
                case "commercial", "commercial_district" -> "COMMERCIAL_DISTRICT";
                case "logistics", "logistics_center" -> "LOGISTICS_CENTER";
                default -> throw new IllegalArgumentException(
                        "Civic must be commercial or logistics.");
            };
            verifyArchive();
            try (ZipFile zip = new ZipFile(archivePath().toFile())) {
                TerritoryDefinition city = requireCity(id);
                ServerLevel level = level(server, city);
                JsonObject root = readJson(zip, "contracts/city-r5-candidate-sockets.json");
                if (!"MAIN_CHAT_CIVIC_REGISTRATION_REQUIRED".equals(
                        root.get("status").getAsString()))
                    throw new IllegalStateException("Unknown R5 civic proposal revision.");
                JsonObject row = findSocket(root, id, kind);
                if (row == null || !row.get("status").getAsString()
                        .equals("PASS_STATIC_CANDIDATE") ||
                        row.get("approved").getAsBoolean())
                    throw new IllegalStateException("R5 civic site is not an unapproved static candidate.");
                if (!city.dimension().equals(row.get("dimension").getAsString()))
                    throw new IllegalStateException("R5 civic dimension differs from map.");
                checkCenter(row.getAsJsonArray("city_center"),
                        row.get("rotation").getAsInt(), city);
                String deltaFile = row.get("delta_file").getAsString();
                String requiredPrefix = "contracts/civic-deltas/" + id.toUpperCase(Locale.ROOT) + "_";
                if (!deltaFile.startsWith(requiredPrefix) ||
                        !deltaFile.equals(requiredPrefix +
                            ("COMMERCIAL_DISTRICT".equals(kind) ?
                                    "commercial_district.json" : "logistics_center.json")))
                    throw new IllegalStateException("R5 civic delta path does not match site.");
                JsonObject delta = readJson(zip, deltaFile);
                if (!delta.get("territory_id").getAsString().equalsIgnoreCase(id) ||
                        !delta.get("module_id").getAsString().equals(kind) ||
                        !delta.get("status").getAsString().equals("PASS_STATIC"))
                    throw new IllegalStateException("R5 civic delta identity mismatch.");
                JsonArray worldAnchor = row.getAsJsonArray("world_anchor_xyz");
                for (int i=0;i<3;i++)
                    if (at(worldAnchor,i) != at(delta.getAsJsonArray("world_anchor_xyz"),i))
                        throw new IllegalStateException("R5 civic delta anchor does not match socket.");

                JsonArray writes = delta.getAsJsonArray("writes");
                if (writes.size() != row.get("write_count").getAsInt() ||
                        writes.size() > MAX_PROBES)
                    throw new IllegalStateException("R5 civic writes invalid/excessive.");
                Set<Long> writeCells = new HashSet<>();
                for (JsonElement el : writes) {
                    JsonObject w = el.getAsJsonObject();
                    BlockPos pos = xyz(w.getAsJsonArray("world_xyz"));
                    if (!writeCells.add(pos.asLong()))
                        throw new IllegalStateException("Duplicate civic write position " + pos);
                    check(level, city, pos,
                            w.get("expected_before_blockstate").getAsString(),
                            "civic write", stats);
                }
                for (JsonElement el : delta.getAsJsonArray("support_read_only_conditions")) {
                    JsonObject check = el.getAsJsonObject();
                    check(level, city, xyz(check.getAsJsonArray("world_xyz")),
                            check.get("expected_state").getAsString(),
                            "support", stats);
                }
                // The entire unlisted above-grade source-air envelope must
                // already be clear. Never replace vegetation or player blocks.
                JsonObject bounds = row.getAsJsonObject("bounds_inclusive");
                JsonArray min = bounds.getAsJsonArray("min");
                JsonArray max = bounds.getAsJsonArray("max");
                int x0=at(min,0), y0=at(min,1), z0=at(min,2);
                int x1=at(max,0), y1=at(max,1), z1=at(max,2);
                long v=(long)(x1-x0+1)*(y1-y0+1)*(z1-z0+1);
                if (v < 1 || v > MAX_PROBES)
                    throw new IllegalStateException("Civic geometry bounds are unreasonable.");
                for (int y=Math.max(y0,city.cityCenter().y()); y<=y1; y++)
                    for (int z=z0; z<=z1; z++)
                        for (int x=x0; x<=x1; x++) {
                            BlockPos pos = new BlockPos(x,y,z);
                            if (!writeCells.contains(pos.asLong()))
                                check(level,city,pos,"minecraft:air",
                                        "source-air clearance",stats);
                        }

                // A civic building is not deployable without the prerequisite
                // adjacent public groups and a purchased or funded civic record.
                for (JsonElement el : delta.getAsJsonArray("dependencies")) {
                    String dependency = el.getAsString();
                    if (!"activated_city".equals(dependency))
                        stats.fail("Civic requires streetscape group " + dependency +
                                " to be safely installed first; unverified on server.");
                }
                CatanSavedData save = CatanSavedData.get(server);
                TerritoryData current = save.territory(id);
                if (current == null || current.ownerNationId() == null)
                    stats.fail("City is not nation-owned/activated; no civic installation allowed.");
                else if (current.buildings().stream().filter(b -> b.type().name().equals(kind))
                        .count() > 1)
                    stats.fail("Duplicate legacy civic purchases require save reconciliation.");
                stats.logFinish();
                return stats.report("R5 CIVIC " + id.toUpperCase(Locale.ROOT) + "/" + kind);
            }
        } catch (Exception ex) {
            stats.fail("Preflight refused: " + ex.getMessage());
            stats.logFinish();
            return stats.report("R5 CIVIC " + label);
        }
    }

    public static Report group(MinecraftServer server, String territory, String requestedGroup) {
        Stats stats = new Stats();
        stats.title("group/" + territory + "/" + requestedGroup);
        stats.logStart();
        try {
            String id = canonicalId(territory);
            String groupId = requestedGroup.trim().toLowerCase(Locale.ROOT);
            if (!groupId.matches("[a-z0-9_]{1,90}"))
                throw new IllegalArgumentException("Invalid R5 group ID.");
            verifyArchive();
            try (ZipFile zip = new ZipFile(archivePath().toFile())) {
                TerritoryDefinition city = requireCity(id);
                ServerLevel level = level(server, city);
                verifyIndex();
                JsonObject group;
                try (ZipFile index = new ZipFile(indexPath().toFile())) {
                    group = readJson(index,"contracts/groups/" +
                            id.toUpperCase(Locale.ROOT)+"/"+groupId+".json");
                }
                if (!id.equalsIgnoreCase(group.get("territory_id").getAsString())
                        || !groupId.equals(group.get("local_group_id").getAsString()))
                    throw new IllegalStateException("R5 indexed group identity mismatch.");
                if (!city.dimension().equals(group.get("dimension").getAsString()))
                    throw new IllegalStateException("R5 group dimension mismatch.");
                checkCenter(group.getAsJsonArray("city_center"),
                        group.get("rotation").getAsInt(),city);
                if (!"PASS_STATIC_ACTIVATED_R2_FIXTURE".equals(group.get("status").getAsString()))
                    throw new IllegalStateException("R5 group not static-valid.");
                if (groupId.startsWith("house_"))
                    stats.fail("House requires verified WHOLE-HOUSE R1/R2 signature " +
                            "and the matching rebased branch; local write matches alone are insufficient.");
                if (CatanSavedData.get(server).territory(id) == null)
                    stats.fail("City has no saved territory state.");
                JsonArray writes=group.getAsJsonArray("changes");
                if (writes.size()>MAX_PROBES)
                    throw new IllegalStateException("Excessive R5 streetscape change count.");
                Set<Long> seen = new HashSet<>();
                for (JsonElement el : writes) {
                    JsonObject cell=el.getAsJsonObject();
                    BlockPos pos=xyz(cell.getAsJsonArray("world_xyz"));
                    if (!seen.add(pos.asLong()))
                        throw new IllegalStateException("Duplicate group write position " + pos);
                    check(level,city,pos,
                            cell.get("expected_before_blockstate").getAsString(),
                            "R5 group",stats);
                }
                stats.logFinish();
                return stats.report("R5 GROUP " + id.toUpperCase(Locale.ROOT)+"/"+groupId);
            }
        } catch (Exception ex) {
            stats.fail("Preflight refused: "+ex.getMessage());
            stats.logFinish();
            return stats.report("R5 GROUP " + territory+"/"+requestedGroup);
        }
    }

    private static BlockPos xyz(JsonArray coords) {
        return new BlockPos(at(coords,0),at(coords,1),at(coords,2));
    }

    private static void check(ServerLevel level, TerritoryDefinition city,
                              BlockPos pos, String expected, String label, Stats stats) {
        stats.examined++;
        if (stats.examined > MAX_PROBES) {
            stats.fail("Preflight maximum comparisons exceeded."); return;
        }
        MapRegion region=city.restorationRegion();
        if (!region.contains(pos)) {
            stats.fail(label+" outside authored city envelope at "+pos); return;
        }
        if (!level.hasChunkAt(pos)) {
            stats.fail(label+" UNLOADED chunk at "+pos); return;
        }
        BlockState actual=level.getBlockState(pos);
        if (!matches(actual,expected))
            stats.fail(label+" mismatch at "+pos+
                    " expected "+expected+" got "+canonical(actual));
    }

    private static <T extends Comparable<T>> String value(BlockState state,
                                                            Property<T> p) {
        return p.getName(state.getValue(p));
    }
    private static String canonical(BlockState state) {
        StringBuilder sb=new StringBuilder(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        if (!state.getProperties().isEmpty()) {
            List<String> pairs=new ArrayList<>();
            for (Property<?> p : state.getProperties())
                pairs.add(p.getName()+"="+value(state,p));
            pairs.sort(String::compareTo);
            sb.append("[").append(String.join(",",pairs)).append("]");
        }
        return sb.toString();
    }

    private static boolean matches(BlockState state, String expected) {
        int bracket=expected.indexOf('[');
        String name=bracket<0?expected:expected.substring(0,bracket);
        if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(name))
            return false;
        if (bracket<0) return state.getProperties().isEmpty();
        if (!expected.endsWith("]")) return false;
        Map<String,String> props=new HashMap<>();
        for (String item : expected.substring(bracket+1,expected.length()-1).split(",")) {
            String[] kv=item.split("=",2);
            if (kv.length!=2 || props.put(kv[0],kv[1])!=null) return false;
        }
        if (props.size()!=state.getProperties().size()) return false;
        for (Property<?> p : state.getProperties()) {
            if (!value(state,p).equals(props.get(p.getName()))) return false;
        }
        return true;
    }
}
