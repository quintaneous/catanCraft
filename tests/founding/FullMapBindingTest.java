package com.quin.catancraft.world;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/** File-only readback of the full-map construction identity. No server or world writer. */
public final class FullMapBindingTest {
    private interface Check { void run() throws IOException; }

    private static void refused(Check check) throws IOException {
        try { check.run(); }
        catch (IOException expected) { return; }
        throw new AssertionError("Expected the altered or unbound identity to be refused");
    }

    private static void check(Path root, Path map, String id, String name, long seed) throws IOException {
        JsonObject actual = FreshDemoAuthorization.validate(root, map, name, seed, "minecraft:overworld");
        FreshDemoAuthorization.verifyFullMapPins(id, name, seed, actual);
    }

    public static void main(String[] args) throws IOException {
        Path config = Path.of(args[0]).toAbsolutePath().toRealPath();
        Path root = Files.createTempDirectory("full-map-field-id-").toRealPath();
        Path marker = root.resolve(FreshDemoAuthorization.MARKER);
        String id = FreshDemoAuthorization.FULL_MAP_ID, name = FreshDemoAuthorization.FULL_MAP_NAME;
        long seed = FreshDemoAuthorization.FULL_MAP_SEED;
        String hash = TownHallRevisions.sha(Files.readAllBytes(config));
        if (!hash.equals(FreshDemoAuthorization.FULL_MAP_CONFIG)) throw new AssertionError("Wrong field map.json");
        refused(() -> check(root, config, id, name, seed));
        if (Files.exists(marker)) throw new AssertionError("Unbound validation wrote a marker");
        JsonObject signed = new JsonObject();
        signed.addProperty("schema", 1);
        signed.addProperty("purpose", FreshDemoAuthorization.PURPOSE);
        signed.addProperty("worldId", UUID.randomUUID().toString());
        signed.addProperty("levelName", name);
        signed.addProperty("seed", seed);
        signed.addProperty("dimension", "minecraft:overworld");
        signed.addProperty("mapSha256", hash);
        signed.addProperty("authoredWorldSha256", FreshDemoAuthorization.FULL_MAP_WORLD);
        signed.addProperty("bootstrapRevision", FreshDemoAuthorization.FULL_MAP_REVISION);
        signed.addProperty("installationBoundPath", root.toString());
        Files.writeString(marker, signed.toString(), StandardOpenOption.CREATE_NEW);
        check(root, config, id, name, seed);

        Path sentinel = root.resolve("existing-progress.txt");
        Files.writeString(sentinel, "keep the earned progress", StandardOpenOption.CREATE_NEW);
        check(root, config, id, name, seed);
        refused(() -> check(root, config, id, name, seed + 1));
        refused(() -> check(root, config, id, "Other world", seed));
        refused(() -> check(root, config, "other_profile", name, seed));
        Path other = Files.createTempDirectory("different-full-map-").toRealPath();
        Files.copy(marker, other.resolve(FreshDemoAuthorization.MARKER));
        refused(() -> check(other, config, id, name, seed));

        for (String key : new String[]{"bootstrapRevision", "authoredWorldSha256", "mapSha256"}) {
            JsonObject altered = signed.deepCopy();
            altered.addProperty(key, "a".repeat(64));
            Files.writeString(marker, altered.toString());
            refused(() -> check(root, config, id, name, seed));
        }
        Files.writeString(marker, signed.toString());
        Path alteredConfig = root.resolve("altered-map.json");
        Files.writeString(alteredConfig, Files.readString(config) + " ");
        refused(() -> check(root, alteredConfig, id, name, seed));
        check(root, config, id, name, seed);
        if (!Files.readString(sentinel).equals("keep the earned progress")) throw new AssertionError("History changed");
        System.out.println("PASS full-map binding, post-bind progress, unbound/copy/wrong profile, source, seed, title, config and revision refusals.");
    }
}
