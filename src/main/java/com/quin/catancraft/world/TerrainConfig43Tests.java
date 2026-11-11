package com.quin.catancraft.world;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import com.google.gson.JsonObject;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Actual published native configs, their Forge lifecycle, and their FileWatcher.
 * No replacement native classes, edited native JARs, or owner worlds are used.
 * CI must additionally reject watcher/parser exceptions in the complete log. */
@GameTestHolder("catancraft_terrain43")
@PrefixGameTestTemplate(false)
public final class TerrainConfig43Tests {
    private static final String TACZ = "com.tacz.guns.config.common.AmmoConfig";
    private static final String SBW = "com.atsuishio.superbwarfare.config.server.";
    private TerrainConfig43Tests() {}

    private record NativeConfig(String provider, ModConfig mod, ForgeConfigSpec spec,
                                List<ForgeConfigSpec.BooleanValue> guards,
                                ForgeConfigSpec.BooleanValue ordinary, Path path, byte[] original) {}

    @GameTest(template="townhall_state_empty", batch="terrain43_01", timeoutTicks=200)
    public static void actual_config_load_and_cache_resets_preserve_all_ten_flags(GameTestHelper h) throws Exception {
        Path root = controlled(h);
        List<NativeConfig> configs = configs(root);
        for (int i = 0; i < 32; i++) {
            for (NativeConfig config : configs) config.spec().afterReload();
            CombatTerrainSafety.enforce();
            assertSafe(configs);
        }
        unchanged(configs);
        proof(root, "loaded", configs, "32 cache resets; ten native getters false; no authored-byte changes");
        h.succeed();
    }

    @GameTest(template="townhall_state_empty", batch="terrain43_02", timeoutTicks=1200)
    public static void guarded_set_refuses_and_unrelated_native_setting_remains_mutable(GameTestHelper h) throws Exception {
        Path root = controlled(h);
        List<NativeConfig> configs = configs(root);
        for (NativeConfig config : configs) for (ForgeConfigSpec.BooleanValue guard : config.guards()) {
            boolean rejected = false;
            try { guard.set(true); } catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected && !guard.get(), "Guarded setter accepted true or poisoned its cached value");
            guard.set(false);
        }
        unchanged(configs);
        List<Long> reloads = counts(configs);
        List<Boolean> originalValues = configs.stream().map(c -> c.ordinary().get()).toList();
        // One ordinary edit per file, followed by the real watcher before any
        // restoration. The guard itself performs no map mutation or file write.
        for (int i = 0; i < configs.size(); i++) configs.get(i).ordinary().set(!originalValues.get(i));
        awaitReload(h, configs, reloads, 0, () -> {
            assertSafe(configs);
            for (int i = 0; i < configs.size(); i++) {
                NativeConfig config = configs.get(i);
                require(config.ordinary().get() == !originalValues.get(i), "Unrelated native getter did not retain its edit");
                require(parse(bytes(config.path())).get(config.ordinary().getPath()).equals(!originalValues.get(i)),
                        "Unrelated ConfigValue.set no longer persists normally");
            }
            List<Long> restoredAfter = counts(configs);
            restore(configs);
            awaitReload(h, configs, restoredAfter, 0, () -> {
                assertSafe(configs);
                unchanged(configs);
                for (int i = 0; i < configs.size(); i++)
                    require(configs.get(i).ordinary().get().equals(originalValues.get(i)), "Unrelated getter did not reload the restored file");
                proof(root, "setters", configs, "ten true writes rejected; false writes caused no IO; two unrelated edits persisted/reloaded");
                h.succeed();
            });
        });
    }

    @GameTest(template="townhall_state_empty", batch="terrain43_03", timeoutTicks=1200)
    public static void external_toml_edits_reload_without_reenabling_terrain_damage(GameTestHelper h) throws Exception {
        Path root = controlled(h);
        List<NativeConfig> configs = configs(root);
        List<Boolean> ordinary = configs.stream().map(c -> c.ordinary().get()).toList();
        List<byte[]> edited = new ArrayList<>();
        for (int i = 0; i < configs.size(); i++) {
            NativeConfig config = configs.get(i);
            CommentedConfig detached = parse(config.original());
            for (ForgeConfigSpec.BooleanValue guard : config.guards()) detached.set(guard.getPath(), true);
            detached.set(config.ordinary().getPath(), !ordinary.get(i));
            StringWriter writer = new StringWriter();
            new TomlWriter().write(detached, writer);
            edited.add(writer.toString().getBytes(StandardCharsets.UTF_8));
        }
        List<Long> before = counts(configs);
        for (int i = 0; i < configs.size(); i++) atomicReplace(configs.get(i).path(), edited.get(i));
        awaitReload(h, configs, before, 0, () -> {
            assertSafe(configs);
            for (int i = 0; i < configs.size(); i++) {
                NativeConfig config = configs.get(i);
                for (ForgeConfigSpec.BooleanValue guard : config.guards())
                    require(Boolean.TRUE.equals(config.mod().getConfigData().get(guard.getPath())),
                            "Watcher did not load the deliberately true authored guard");
                require(config.ordinary().get() == !ordinary.get(i), "Watcher did not refresh an unrelated setting");
                require(Arrays.equals(edited.get(i), bytes(config.path())), "Policy rewrote the edited authored file");
                config.spec().afterReload();
            }
            CombatTerrainSafety.enforce();
            assertSafe(configs);
            List<Long> restoredAfter = counts(configs);
            restore(configs);
            awaitReload(h, configs, restoredAfter, 0, () -> {
                assertSafe(configs);
                unchanged(configs);
                for (int i = 0; i < configs.size(); i++)
                    require(configs.get(i).ordinary().get().equals(ordinary.get(i)), "Ordinary setting was frozen by the policy");
                proof(root, "watcher", configs, "all ten authored flags true after real FileWatcher reload; all ten effective false; byte-exact restore reloaded");
                h.succeed();
            });
        });
    }

    private static List<NativeConfig> configs(Path root) throws Exception {
        List<NativeConfig> result = new ArrayList<>();
        for (String provider : List.of("tacz", "superbwarfare")) {
            CombatTerrainSafety.requireProtected(provider);
            ModConfig.Type type = provider.equals("tacz") ? ModConfig.Type.COMMON : ModConfig.Type.SERVER;
            List<ModConfig> matching = ConfigTracker.INSTANCE.configSets().get(type).stream()
                    .filter(c -> c.getModId().equals(provider) && !c.getFileName().equals("tacz-pre.toml")).toList();
            require(matching.size() == 1, "Unexpected native config inventory for " + provider);
            ModConfig mod = matching.get(0);
            require(mod.getSpec() instanceof ForgeConfigSpec && mod.getConfigData() instanceof CommentedFileConfig,
                    "Expected actual file-backed native Forge config");
            ForgeConfigSpec spec = (ForgeConfigSpec) mod.getSpec();
            List<ForgeConfigSpec.BooleanValue> guards = new ArrayList<>();
            if (provider.equals("tacz")) {
                for (String field : List.of("DESTROY_GLASS", "IGNITE_BLOCK", "EXPLOSIVE_AMMO_DESTROYS_BLOCK"))
                    guards.add(setting(TACZ, field));
            } else {
                for (String field : List.of("COLLISION_DESTROY_SOFT_BLOCKS", "COLLISION_DESTROY_NORMAL_BLOCKS",
                        "COLLISION_DESTROY_HARD_BLOCKS", "COLLISION_DESTROY_BLOCKS_BEASTLY")) guards.add(setting(SBW + "VehicleConfig", field));
                guards.add(setting(SBW + "ProjectileConfig", "PROJECTILE_DESTROY_BLOCKS"));
                guards.add(setting(SBW + "ExplosionConfig", "EXPLOSION_DESTROY"));
                guards.add(setting(SBW + "ExplosionConfig", "EXTRA_EXPLOSION_EFFECT"));
            }
            for (ForgeConfigSpec.BooleanValue guard : guards)
                require(spec.getValues().getRaw(guard.getPath()) == guard, "Guard does not belong to the actual provider spec");
            ForgeConfigSpec.BooleanValue ordinary = ordinary(spec.getValues(), guards);
            require(ordinary != null, "No unrelated boolean available to check normal config behavior");
            Path path = ((CommentedFileConfig) mod.getConfigData()).getNioPath().toAbsolutePath().normalize();
            for (Path part = path; part != null; part = part.getParent())
                require(!Files.isSymbolicLink(part), "Native config fixture path contains a symlink");
            require(path.startsWith(root.getParent()) && path.toRealPath().equals(path), "Refusing a config outside the dedicated fixture");
            result.add(new NativeConfig(provider, mod, spec, List.copyOf(guards), ordinary, path, bytes(path)));
        }
        require(result.stream().mapToInt(c -> c.guards().size()).sum() == 10, "Expected exactly ten real native protection flags");
        assertSafe(result);
        return List.copyOf(result);
    }

    private static ForgeConfigSpec.BooleanValue setting(String type, String field) throws Exception {
        Object value = Class.forName(type).getField(field).get(null);
        require(value instanceof ForgeConfigSpec.BooleanValue, "Native protection field type changed: " + field);
        return (ForgeConfigSpec.BooleanValue) value;
    }

    private static ForgeConfigSpec.BooleanValue ordinary(UnmodifiableConfig config, List<ForgeConfigSpec.BooleanValue> guards) {
        for (Object value : config.valueMap().values()) {
            if (value instanceof ForgeConfigSpec.BooleanValue bool && guards.stream().noneMatch(g -> g == bool)) return bool;
            if (value instanceof UnmodifiableConfig nested) {
                ForgeConfigSpec.BooleanValue found = ordinary(nested, guards);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void assertSafe(List<NativeConfig> configs) {
        for (NativeConfig config : configs) {
            CombatTerrainSafety.requireProtected(config.provider());
            for (ForgeConfigSpec.BooleanValue guard : config.guards()) {
                require(!guard.get(), "Native getter enabled terrain damage");
                guard.clearCache();
                require(!guard.get(), "Clearing a native cache bypassed terrain protection");
            }
        }
    }

    private static List<Long> counts(List<NativeConfig> configs) {
        return configs.stream().map(c -> CombatTerrainSafety.reloadCount(c.provider())).toList();
    }

    @FunctionalInterface private interface Checked { void run() throws Exception; }

    private static void awaitReload(GameTestHelper h, List<NativeConfig> configs, List<Long> before, int attempts, Checked done) {
        h.runAfterDelay(5, () -> {
            try {
                assertSafe(configs);
                boolean reloaded = true;
                for (int i = 0; i < configs.size(); i++)
                    reloaded &= CombatTerrainSafety.reloadCount(configs.get(i).provider()) > before.get(i);
                if (reloaded) done.run();
                else if (attempts < 90) awaitReload(h, configs, before, attempts + 1, done);
                else throw new AssertionError("Timed out awaiting both actual Forge FileWatcher reloads");
            } catch (Exception | AssertionError failure) {
                // The entire disposable failed fixture remains evidence. Never
                // hide a failed reload behind a synthetic success or edited log.
                h.fail(failure.toString());
            }
        });
    }

    private static void unchanged(List<NativeConfig> configs) throws IOException {
        for (NativeConfig config : configs)
            require(Arrays.equals(config.original(), bytes(config.path())), "Enforcement changed authored config bytes");
    }

    private static void restore(List<NativeConfig> configs) throws IOException {
        for (NativeConfig config : configs) atomicReplace(config.path(), config.original());
    }

    private static void atomicReplace(Path path, byte[] value) throws IOException {
        // A complete external edit: the watched map itself is never mutated by
        // the test's editor, and FileWatcher cannot observe a half-written TOML.
        Path staged = Files.createTempFile(path.getParent(), ".terrain43-edit-", ".tmp");
        Files.write(staged, value, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(staged, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private static CommentedConfig parse(byte[] value) {
        return new TomlParser().parse(new String(value, StandardCharsets.UTF_8));
    }

    private static byte[] bytes(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > 2 * 1024 * 1024)
            throw new IOException("Missing bounded native config evidence " + path.getFileName());
        return Files.readAllBytes(path);
    }

    private static Path controlled(GameTestHelper h) throws IOException {
        var level = h.getLevel();
        Path requested = level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        for (Path path = requested; path != null; path = path.getParent())
            require(!Files.isSymbolicLink(path), "Terrain43 fixture path contains a symlink");
        Path root = requested.toRealPath();
        require(level.getServer() instanceof GameTestServer && level.getServer().isSameThread()
                        && level.getServer().getPlayerList().getPlayers().isEmpty()
                        && root.getFileName().toString().equals("world")
                        && root.getParent().getFileName().toString().equals("controlled-catancraft_terrain43-run")
                        && root.getParent().getParent().getFileName().toString().equals("build")
                        && Files.readString(root.getParent().resolve(".catancraft-controlled-fixture-v1"))
                        .equals("CatanCraft synthetic flat fixture v1\n")
                        && level.dimension().location().toString().equals("minecraft:overworld") && level.getSeed() == 0
                        && ModList.get().isLoaded("tacz") && ModList.get().isLoaded("superbwarfare")
                        && System.getProperty("catancraft.taczRuntimeManifestSHA256", "").matches("[0-9a-f]{64}")
                        && System.getProperty("catancraft.sbwRuntimeManifestSHA256", "").matches("[0-9a-f]{64}"),
                "Terrain43 requires both verified published providers in its empty marked disposable GameTest world");
        return root;
    }

    private static void proof(Path root, String phase, List<NativeConfig> configs, String detail) throws IOException {
        JsonObject proof = new JsonObject();
        proof.addProperty("schema", "catancraft-terrain43-native-config-v1");
        proof.addProperty("phase", phase);
        proof.addProperty("flags_false", 10);
        proof.addProperty("detail", detail);
        JsonObject providers = new JsonObject();
        for (NativeConfig config : configs) {
            JsonObject entry = new JsonObject();
            entry.addProperty("file", config.path().getFileName().toString());
            entry.addProperty("authored_sha256", NativeWeapon42.sha(config.original()));
            entry.addProperty("restored_sha256", NativeWeapon42.sha(bytes(config.path())));
            entry.addProperty("observed_reloads", CombatTerrainSafety.reloadCount(config.provider()));
            JsonObject guards = new JsonObject();
            for (ForgeConfigSpec.BooleanValue guard : config.guards()) guards.addProperty(String.join(".", guard.getPath()), guard.get());
            entry.add("effective_guards", guards);
            providers.add(config.provider(), entry);
        }
        proof.add("providers", providers);
        Files.writeString(root.resolve("data/catancraft_terrain43_" + phase + ".json"), proof + "\n", StandardOpenOption.CREATE_NEW);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
