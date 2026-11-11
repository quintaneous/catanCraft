package com.quin.catancraft.world;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.utils.CommentedConfigWrapper;
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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;

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

    private record EditProof(List<Long> before, List<Long> edited, List<Long> restored,
                             List<byte[]> editedBytes, List<Boolean> ordinaryBefore,
                             List<JsonObject> authoredGuards) {}

    @GameTest(template="townhall_state_empty", batch="terrain43_01", timeoutTicks=200)
    public static void actual_config_load_and_cache_resets_preserve_all_ten_flags(GameTestHelper h) throws Exception {
        Path root = controlled(h);
        List<NativeConfig> configs = configs(root);
        for (NativeConfig config : configs) rawRebuildRegression(config);
        for (int i = 0; i < 32; i++) {
            for (NativeConfig config : configs) config.spec().afterReload();
            CombatTerrainSafety.enforce();
            assertSafe(configs);
        }
        unchanged(configs);
        proof(root, "loaded", configs, "raw rebuild regression; 32 cache resets; ten native getters false; no authored-byte changes", null);
        h.succeed();
    }

    @GameTest(template="townhall_state_empty", batch="terrain43_02", timeoutTicks=15000)
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
        // NightConfig3.6.4 deliberately drops coalesced MODIFY events. Give the
        // completed ordinary save notifications a scheduler turn, then emit a
        // single same-byte modification as an external editor would. Success
        // still requires actual Forge callbacks and the exact edited values.
        Thread.sleep(100);
        for (NativeConfig config : configs) signalModify(config.path());
        awaitReload(h, configs, reloads, inverted(originalValues), false, () -> {
            assertSafe(configs);
            List<byte[]> edited = new ArrayList<>();
            for (int i = 0; i < configs.size(); i++) {
                NativeConfig config = configs.get(i);
                require(config.ordinary().get() == !originalValues.get(i), "Unrelated native getter did not retain its edit");
                require(parse(bytes(config.path())).get(config.ordinary().getPath()).equals(!originalValues.get(i)),
                        "Unrelated ConfigValue.set no longer persists normally");
                edited.add(bytes(config.path()));
            }
            List<Long> restoredAfter = counts(configs);
            restore(configs);
            awaitReload(h, configs, restoredAfter, originalValues, false, () -> {
                assertSafe(configs);
                unchanged(configs);
                for (int i = 0; i < configs.size(); i++)
                    require(configs.get(i).ordinary().get().equals(originalValues.get(i)), "Unrelated getter did not reload the restored file");
                proof(root, "setters", configs, "ten true writes rejected; false writes caused no IO; two unrelated edits persisted/reloaded",
                        new EditProof(reloads, restoredAfter, counts(configs), edited, originalValues, null));
                h.succeed();
            });
        });
    }

    @GameTest(template="townhall_state_empty", batch="terrain43_03", timeoutTicks=15000)
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
        awaitReload(h, configs, before, inverted(ordinary), true, () -> {
            assertSafe(configs);
            List<JsonObject> authored = new ArrayList<>();
            for (int i = 0; i < configs.size(); i++) {
                NativeConfig config = configs.get(i);
                for (ForgeConfigSpec.BooleanValue guard : config.guards())
                    require(Boolean.TRUE.equals(config.mod().getConfigData().get(guard.getPath())),
                            "Watcher did not load the deliberately true authored guard");
                require(config.ordinary().get() == !ordinary.get(i), "Watcher did not refresh an unrelated setting");
                require(Arrays.equals(edited.get(i), bytes(config.path())), "Policy rewrote the edited authored file");
                JsonObject actual = new JsonObject();
                for (ForgeConfigSpec.BooleanValue guard : config.guards())
                    actual.addProperty(String.join(".", guard.getPath()),
                            (Boolean) config.mod().getConfigData().get(guard.getPath()));
                authored.add(actual);
                config.spec().afterReload();
            }
            CombatTerrainSafety.enforce();
            assertSafe(configs);
            List<Long> restoredAfter = counts(configs);
            restore(configs);
            awaitReload(h, configs, restoredAfter, ordinary, false, () -> {
                assertSafe(configs);
                unchanged(configs);
                for (int i = 0; i < configs.size(); i++)
                    require(configs.get(i).ordinary().get().equals(ordinary.get(i)), "Ordinary setting was frozen by the policy");
                proof(root, "watcher", configs, "all ten authored flags true after real FileWatcher reload; all ten effective false; byte-exact restore reloaded",
                        new EditProof(before, restoredAfter, counts(configs), edited, ordinary, authored));
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

    private static void rawRebuildRegression(NativeConfig nativeConfig) {
        AtomicReference<CommentedConfig> current = new AtomicReference<>(parse(nativeConfig.original()));
        CommentedConfig raw = new CommentedConfigWrapper<CommentedConfig>(current.get()) {
            @Override public <T> T getRaw(List<String> path) { return current.get().getRaw(path); }
            @Override public Map<String, Object> valueMap() { throw new AssertionError("Policy enumerated the watched map"); }
            @Override public Set<? extends CommentedConfig.Entry> entrySet() { throw new AssertionError("Policy enumerated watched entries"); }
            @Override public String getComment(List<String> path) { throw new AssertionError("Policy read watched comments"); }
            @Override public Map<String, String> commentMap() { throw new AssertionError("Policy enumerated watched comments"); }
        };
        Set<List<String>> paths = nativeConfig.guards().stream().map(g -> List.copyOf(g.getPath())).collect(Collectors.toSet());
        CommentedConfig view = CombatTerrainSafety.policyView(nativeConfig.spec(), raw, paths);
        require(nativeConfig.spec().isCorrect(view), "Policy validation failed on a complete raw config");
        List<String> ordinaryPath = nativeConfig.ordinary().getPath();
        List<String> parent = ordinaryPath.subList(0, ordinaryPath.size() - 1);
        CommentedConfig nested = parent.isEmpty() ? view : view.get(parent);
        // Models parser clear()/repopulation deterministically, without a race
        // whose absence might let the old implementation pass by chance.
        current.set(CommentedConfig.inMemory());
        require(nativeConfig.spec().isCorrect(view), "Native defaults failed while the raw map was being rebuilt");
        for (ForgeConfigSpec.BooleanValue guard : nativeConfig.guards())
            require(Boolean.FALSE.equals(view.get(guard.getPath())), "Raw rebuild enabled a protected flag");
        CommentedConfig edited = parse(nativeConfig.original());
        edited.set(ordinaryPath, !nativeConfig.ordinary().get());
        for (ForgeConfigSpec.BooleanValue guard : nativeConfig.guards()) edited.set(guard.getPath(), true);
        current.set(edited);
        require(nativeConfig.spec().isCorrect(view), "Repopulated raw config failed policy validation");
        require(nested.get(List.of(ordinaryPath.get(ordinaryPath.size() - 1))).equals(!nativeConfig.ordinary().get()),
                "Nested policy view retained a stale raw section");
        edited.set(ordinaryPath, "not a boolean");
        require(!nativeConfig.spec().isCorrect(view), "Policy hid an invalid unrelated native value");
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

    private static List<Boolean> inverted(List<Boolean> values) { return values.stream().map(v -> !v).toList(); }

    private static void awaitReload(GameTestHelper h, List<NativeConfig> configs, List<Long> before,
                                    List<Boolean> expected, boolean authoredGuardsTrue, Checked done) {
        awaitReload(h, configs, before, expected, authoredGuardsTrue, System.nanoTime() + TimeUnit.SECONDS.toNanos(10), done);
    }

    private static void awaitReload(GameTestHelper h, List<NativeConfig> configs, List<Long> before,
                                    List<Boolean> expected, boolean authoredGuardsTrue, long deadline, Checked done) {
        h.runAfterDelay(1, () -> {
            try {
                // GameTestServer runs unthrottled; ticks are not wall-clock
                // time. A short test-only yield lets the real watcher execute.
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(2));
                assertSafe(configs);
                boolean reloaded = true;
                for (int i = 0; i < configs.size(); i++) {
                    NativeConfig config = configs.get(i);
                    reloaded &= CombatTerrainSafety.reloadCount(config.provider()) > before.get(i);
                    reloaded &= expected.get(i).equals(config.mod().getConfigData().get(config.ordinary().getPath()));
                    config.ordinary().clearCache();
                    reloaded &= expected.get(i).equals(config.ordinary().get());
                    if (authoredGuardsTrue) for (ForgeConfigSpec.BooleanValue guard : config.guards())
                        reloaded &= Boolean.TRUE.equals(config.mod().getConfigData().get(guard.getPath()));
                }
                if (reloaded) done.run();
                else if (System.nanoTime() < deadline) awaitReload(h, configs, before, expected, authoredGuardsTrue, deadline, done);
                else throw new AssertionError("Ten-second wall-clock deadline awaiting actual Forge FileWatcher reloads; before="
                        + before + ", observed=" + counts(configs) + ", expectedOrdinary=" + expected + ", rawOrdinary="
                        + configs.stream().map(c -> c.mod().getConfigData().get(c.ordinary().getPath())).toList());
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
        // NightConfig3.6.4 watches ENTRY_MODIFY only, not rename/ENTRY_CREATE.
        // Rewriting an identical existing byte emits MODIFY while the complete
        // valid TOML stays byte-identical throughout the notification.
        signalModify(path);
    }

    private static void signalModify(Path path) throws IOException {
        byte[] value = bytes(path);
        require(value.length > 0, "Cannot notify an empty native config");
        try (FileChannel file = FileChannel.open(path, StandardOpenOption.WRITE)) {
            require(file.write(ByteBuffer.wrap(new byte[] { value[0] }), 0) == 1, "Failed to signal complete native config edit");
        }
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

    private static void proof(Path root, String phase, List<NativeConfig> configs, String detail, EditProof edit) throws IOException {
        JsonObject proof = new JsonObject();
        proof.addProperty("schema", "catancraft-terrain43-native-config-v2");
        proof.addProperty("phase", phase);
        proof.addProperty("flags_false", 10);
        proof.addProperty("detail", detail);
        JsonObject providers = new JsonObject();
        for (int i = 0; i < configs.size(); i++) {
            NativeConfig config = configs.get(i);
            JsonObject entry = new JsonObject();
            entry.addProperty("file", config.path().getFileName().toString());
            entry.addProperty("authored_sha256", NativeWeapon42.sha(config.original()));
            entry.addProperty("restored_sha256", NativeWeapon42.sha(bytes(config.path())));
            entry.addProperty("observed_reloads", CombatTerrainSafety.reloadCount(config.provider()));
            JsonObject guards = new JsonObject();
            for (ForgeConfigSpec.BooleanValue guard : config.guards()) guards.addProperty(String.join(".", guard.getPath()), guard.get());
            entry.add("effective_guards", guards);
            if (edit != null) {
                require(edit.before().get(i) < edit.edited().get(i) && edit.edited().get(i) < edit.restored().get(i),
                        "Scenario did not observe distinct edit and restoration reloads");
                entry.addProperty("reloads_before_edit", edit.before().get(i));
                entry.addProperty("reloads_after_edit", edit.edited().get(i));
                entry.addProperty("reloads_after_restore", edit.restored().get(i));
                entry.addProperty("edited_sha256", NativeWeapon42.sha(edit.editedBytes().get(i)));
                entry.addProperty("ordinary_path", String.join(".", config.ordinary().getPath()));
                entry.addProperty("ordinary_before", edit.ordinaryBefore().get(i));
                entry.addProperty("ordinary_edited", (Boolean) parse(edit.editedBytes().get(i)).get(config.ordinary().getPath()));
                entry.addProperty("ordinary_restored", config.ordinary().get());
                if (edit.authoredGuards() != null) entry.add("authored_guards_after_edit", edit.authoredGuards().get(i));
            }
            providers.add(config.provider(), entry);
        }
        proof.add("providers", providers);
        Files.writeString(root.resolve("data/catancraft_terrain43_" + phase + ".json"), proof + "\n", StandardOpenOption.CREATE_NEW);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
