package com.quin.catancraft.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pinned, inactive tier policy: it is not an offer list, price list or installed-pack acceptance.
 * A candidate purchase additionally needs an explicit priced offer and verified effective native
 * data. Reference-only metadata must never stand in for either of those checks.
 */
final class WeaponTierCatalog41 {
    static final String LEGACY_REVISION = "physical_weapon_shop40_existing_prices_v1";
    static final String CANDIDATE_REVISION = "weapon_tiers41_reference_candidate_v1";
    static final String RESOURCE_SHA256 = "230d8a97e9b744902c97534009bb8b6089a7d89314010e23771755f3b205181a";
    static final String AUDIT_SHA256 = "3e6e15cd57bf16da5b1976608ec56f0bec70204535348578171c1a2cf34c8b0c";
    static final String TACZ_COMMIT = "42af1be5bbe4127c083cfa19b57482f8aae23df2";
    static final String SBW_SHA256 = "06b4bc749e540879265e3f3829c6c242da81512b19a80de0fbd9b089847893d5";
    static final String ID_SET_SHA256 = "6bc571cd770b0ba7b737192c8b5aa402eb876e370a6eacd27e2197375b36607b";
    private static final String RESOURCE = "/data/catancraft/economy/weapon_tiers41.json";
    private static final String STATUS = "REFERENCE_ONLY_NOT_RUNTIME_OR_BALANCE_ACCEPTANCE";
    private static final int MAX_BYTES = 128 * 1024;
    private static final Set<String> INTERNAL_IDS = Set.of("superbwarfare:empty_gun", "superbwarfare:vehicle_gun");
    private static final Set<String> TACZ_CATEGORIES = Set.of("dmr", "mg", "pistol", "rifle", "rpg", "shotgun", "smg", "sniper");
    private static final Set<String> SBW_CATEGORIES = Set.of("CurvedLauncher", "DirectLauncher", "Handgun", "MachineGun",
            "Rifle", "Shotgun", "Smg", "Sniper", "Special");
    private static final Map<String, Integer> LEGACY_LEVELS = Map.of(
            "tacz:glock_17", 0, "tacz:ak47", 1, "tacz:9mm", 0, "tacz:762x39", 1);
    private static volatile Catalog cached;

    enum Disposition { STARTER, SHOP, DROP, HOLD }
    enum Qualification {
        REFERENCE_ONLY, EXPLOSIVE_DAMAGE, ENERGY_CONSUMER, ALTERNATE_AMMO,
        SPECIAL_BEHAVIOR, SPECIAL_HANDLING, FIRE_MODE_OVERRIDES
    }

    /** JSON strings preserve native object/array/null shapes without exposing mutable Gson trees. */
    record Entry(String id, String provider, String category, Disposition disposition, int proposedLevel,
                 String dataId, String ammoJson, String fireModesJson, String declaredDefaultFireMode,
                 String script, Set<Qualification> qualificationFlags, String reason) {
        Entry { qualificationFlags = Set.copyOf(qualificationFlags); }
        boolean requiresSpecialQualification() {
            return qualificationFlags.stream().anyMatch(flag -> flag != Qualification.REFERENCE_ONLY);
        }
    }
    private record Catalog(List<Entry> entries, Map<String, Entry> byId) {}

    private WeaponTierCatalog41() {}

    static List<Entry> entries() throws IOException { return catalog().entries(); }

    static Entry entry(String nativeId) throws IOException {
        requireId(nativeId);
        Entry result = catalog().byId().get(nativeId);
        if (result == null) throw invalid("Unknown or internal weapon: " + nativeId);
        return result;
    }

    /** Exact grandfathered 040 gates only. This does not validate or change any price. */
    static void requireExistingOffer(String nativeId, int requiredLevel) throws IOException {
        requireId(nativeId);
        Integer expected = LEGACY_LEVELS.get(nativeId);
        if (expected == null || expected != requiredLevel)
            throw invalid("This is not an unchanged 040 weapon or ammunition gate.");
        String gunId = nativeId.equals("tacz:9mm") ? "tacz:glock_17"
                : nativeId.equals("tacz:762x39") ? "tacz:ak47" : nativeId;
        Entry gun = entry(gunId);
        if ((gun.disposition() != Disposition.STARTER && gun.disposition() != Disposition.SHOP)
                || gun.requiresSpecialQualification())
            throw invalid("The existing offer is not eligible for the weapon counter.");
        // Ammunition grandfathering is tied to the corresponding exact reference gun consumer.
        if (!gunId.equals(nativeId) && !gun.ammoJson().equals(new JsonPrimitive(nativeId).toString()))
            throw invalid("The existing ammunition does not match its reference gun.");
    }

    /**
     * Candidate policy gate, not native qualification. requiredLevel is the offer's exact minimum,
     * not the player's current factory level. No legacy fallback and no special-gun override.
     */
    static Entry requireShop(String nativeId, int requiredLevel, String profileRevision) throws IOException {
        if (!CANDIDATE_REVISION.equals(profileRevision)) throw invalid("Unknown candidate tier revision.");
        Entry weapon = entry(nativeId);
        if (weapon.disposition() != Disposition.STARTER && weapon.disposition() != Disposition.SHOP)
            throw invalid("This weapon is reserved for drops or held out of the shop.");
        if (weapon.proposedLevel() != requiredLevel)
            throw invalid("The candidate offer does not match its proposed factory tier.");
        if (weapon.requiresSpecialQualification())
            throw invalid("This weapon still requires special native-data qualification.");
        return weapon;
    }

    private static Catalog catalog() throws IOException {
        Catalog value = cached;
        if (value != null) return value;
        synchronized (WeaponTierCatalog41.class) {
            if (cached == null) {
                try (InputStream stream = WeaponTierCatalog41.class.getResourceAsStream(RESOURCE)) {
                    if (stream == null) throw invalid("Missing bundled tier reference.");
                    cached = parse(stream.readNBytes(MAX_BYTES + 1));
                }
            }
            return cached;
        }
    }

    /** Same bounded, pinned parser used by runtime; deliberately accepts no unpinned override. */
    static List<Entry> validate(byte[] bytes) throws IOException { return parse(bytes).entries(); }

    private static Catalog parse(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) throw invalid("Invalid reference size.");
        try (JsonReader reader = new JsonReader(new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
            reader.setLenient(false);
            JsonObject root = object(readValue(reader, 0, new int[]{0}));
            if (reader.peek() != JsonToken.END_DOCUMENT) throw invalid("Trailing reference content.");
            keys(root, "schema", "revision", "status", "source", "excludedInternalIds", "entries");
            if (integer(root, "schema") != 1 || !CANDIDATE_REVISION.equals(string(root, "revision"))
                    || !STATUS.equals(string(root, "status"))) throw invalid("Unknown reference schema or status.");
            JsonObject source = object(root.get("source"));
            keys(source, "auditSHA256", "taczCommit", "sbwSHA256", "idSetSHA256");
            if (!AUDIT_SHA256.equals(string(source, "auditSHA256")) || !TACZ_COMMIT.equals(string(source, "taczCommit"))
                    || !SBW_SHA256.equals(string(source, "sbwSHA256")) || !ID_SET_SHA256.equals(string(source, "idSetSHA256")))
                throw invalid("Reference source pins changed.");
            Set<String> excluded = new HashSet<>();
            for (JsonElement value : array(root.get("excludedInternalIds")))
                if (!excluded.add(string(value))) throw invalid("Duplicate internal exclusion.");
            if (!excluded.equals(INTERNAL_IDS)) throw invalid("Internal exclusions changed.");
            JsonArray rawEntries = array(root.get("entries"));
            if (rawEntries.size() != 113) throw invalid("Reference coverage is incomplete.");
            List<Entry> entries = new ArrayList<>();
            Map<String, Entry> byId = new LinkedHashMap<>();
            int tacz = 0, sbw = 0, starter = 0, shop = 0, drop = 0, hold = 0;
            for (JsonElement raw : rawEntries) {
                Entry entry = parseEntry(object(raw));
                if (byId.putIfAbsent(entry.id(), entry) != null) throw invalid("Duplicate weapon ID.");
                entries.add(entry);
                if (entry.provider().equals("tacz")) tacz++; else sbw++;
                switch (entry.disposition()) {
                    case STARTER -> starter++;
                    case SHOP -> shop++;
                    case DROP -> drop++;
                    case HOLD -> hold++;
                }
            }
            String ids = String.join("\n", new TreeSet<>(byId.keySet())) + "\n";
            if (tacz != 70 || sbw != 43 || starter != 1 || shop != 82 || drop != 28 || hold != 2
                    || !ID_SET_SHA256.equals(sha256(ids.getBytes(StandardCharsets.UTF_8))))
                throw invalid("Reference weapon coverage or allocation changed.");
            if (!RESOURCE_SHA256.equals(sha256(bytes))) throw invalid("Bundled reference digest changed.");
            return new Catalog(List.copyOf(entries), Map.copyOf(byId));
        } catch (IOException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new IOException("Invalid bundled weapon tier reference.", failure);
        }
    }

    private static Entry parseEntry(JsonObject raw) throws IOException {
        keys(raw, "id", "provider", "category", "disposition", "proposedLevel", "dataId", "ammo", "fireModes",
                "declaredDefaultFireMode", "script", "qualificationFlags", "reason");
        String id = string(raw, "id"), provider = string(raw, "provider"), category = string(raw, "category");
        requireId(id);
        if (INTERNAL_IDS.contains(id) || !id.startsWith(provider + ":")
                || !(provider.equals("tacz") ? TACZ_CATEGORIES : provider.equals("superbwarfare") ? SBW_CATEGORIES : Set.of()).contains(category))
            throw invalid("Unknown weapon provider or category.");
        Disposition disposition = Disposition.valueOf(string(raw, "disposition"));
        int level = integer(raw, "proposedLevel");
        if (disposition == Disposition.STARTER ? level != 0
                : disposition == Disposition.SHOP ? level < 1 || level > 5 : level != -1)
            throw invalid("Invalid proposed factory tier.");
        String dataId = string(raw, "dataId");
        requireId(dataId);
        if (!dataId.startsWith(provider + ":")) throw invalid("Reference data provider mismatch.");
        JsonElement ammo = raw.get("ammo"), modes = raw.get("fireModes");
        if (ammo == null || ammo.isJsonNull() || !(ammo.isJsonArray() || isString(ammo))) throw invalid("Invalid ammo reference.");
        if (ammo.isJsonArray() && ammo.getAsJsonArray().isEmpty()) throw invalid("Empty ammo alternatives.");
        if (modes == null || !(modes.isJsonNull() || modes.isJsonArray() || isString(modes))) throw invalid("Invalid fire-mode reference.");
        String defaultMode = nullableString(raw.get("declaredDefaultFireMode"));
        String script = nullableString(raw.get("script"));
        if (script != null) requireId(script);
        if (provider.equals("tacz")) {
            requireId(string(ammo));
            if (!modes.isJsonArray() || modes.getAsJsonArray().isEmpty() || defaultMode != null)
                throw invalid("Invalid TaCZ fire-mode reference.");
            for (JsonElement mode : modes.getAsJsonArray())
                if (!Set.of("semi", "auto", "burst").contains(string(mode))) throw invalid("Unknown TaCZ fire mode.");
        } else if (script != null || !dataId.equals(id)) throw invalid("Invalid SBW data reference.");
        Set<Qualification> flags = new HashSet<>();
        for (JsonElement flag : array(raw.get("qualificationFlags")))
            if (!flags.add(Qualification.valueOf(string(flag)))) throw invalid("Duplicate qualification flag.");
        if (!flags.contains(Qualification.REFERENCE_ONLY)) throw invalid("Reference qualification missing.");
        if (ammo.isJsonArray() != flags.contains(Qualification.ALTERNATE_AMMO)) throw invalid("Ammo qualification mismatch.");
        if (isString(ammo) && ammo.getAsString().equals("FE") != flags.contains(Qualification.ENERGY_CONSUMER))
            throw invalid("Energy qualification mismatch.");
        String reason = string(raw, "reason");
        return new Entry(id, provider, category, disposition, level, dataId, ammo.toString(), modes.toString(),
                defaultMode, script, flags, reason);
    }

    private static JsonElement readValue(JsonReader reader, int depth, int[] count) throws IOException {
        if (depth > 32 || ++count[0] > 20000) throw invalid("Reference nesting or node limit exceeded.");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject result = new JsonObject();
                Set<String> seen = new HashSet<>();
                reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (key.length() > 128 || !seen.add(key)) throw invalid("Duplicate or oversized JSON member.");
                    result.add(key, readValue(reader, depth + 1, count));
                }
                reader.endObject();
                yield result;
            }
            case BEGIN_ARRAY -> {
                JsonArray result = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) {
                    if (result.size() >= 256) throw invalid("Reference array limit exceeded.");
                    result.add(readValue(reader, depth + 1, count));
                }
                reader.endArray();
                yield result;
            }
            case STRING -> {
                String value = reader.nextString();
                if (value.length() > 4096) throw invalid("Reference string limit exceeded.");
                yield new JsonPrimitive(value);
            }
            case NUMBER -> {
                String value = reader.nextString();
                if (value.length() > 40 || !value.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]{1,3})?"))
                    throw invalid("Invalid reference number.");
                yield new JsonPrimitive(new BigDecimal(value));
            }
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw invalid("Unexpected reference token.");
        };
    }

    private static void keys(JsonObject object, String... expected) throws IOException {
        if (!object.keySet().equals(Set.of(expected))) throw invalid("Missing or unknown reference field.");
    }
    private static JsonObject object(JsonElement value) throws IOException {
        if (value == null || !value.isJsonObject()) throw invalid("Expected reference object.");
        return value.getAsJsonObject();
    }
    private static JsonArray array(JsonElement value) throws IOException {
        if (value == null || !value.isJsonArray()) throw invalid("Expected reference array.");
        return value.getAsJsonArray();
    }
    private static boolean isString(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
    }
    private static String string(JsonElement value) throws IOException {
        if (!isString(value) || value.getAsString().isBlank()) throw invalid("Expected nonempty reference string.");
        return value.getAsString();
    }
    private static String string(JsonObject object, String key) throws IOException { return string(object.get(key)); }
    private static String nullableString(JsonElement value) throws IOException {
        if (value == null) throw invalid("Missing nullable reference field.");
        return value.isJsonNull() ? null : string(value);
    }
    private static int integer(JsonObject object, String key) throws IOException {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || !value.toString().matches("-?(0|[1-9][0-9]*)")) throw invalid("Expected reference integer.");
        try { return value.getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException failure) { throw invalid("Reference integer is out of bounds."); }
    }
    private static void requireId(String id) throws IOException {
        if (id == null || id.length() > 192 || !id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))
            throw invalid("Malformed native weapon identifier.");
    }
    private static String sha256(byte[] bytes) throws IOException {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException failure) { throw new IOException("SHA-256 is unavailable.", failure); }
    }
    private static IOException invalid(String message) { return new IOException(message); }
}
