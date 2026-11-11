package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.utils.CommentedConfigWrapper;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server-authoritative block damage prevention for optional combat mods.
 *
 * Ordinary Forge BreakEvent and ExplosionEvent hooks do not cover all direct
 * Level.destroyBlock() calls. TaCZ breaks panes via AmmoHitBlockEvent and
 * Superb Warfare destroys blocks in collision, projectile and explosion code.
 * Enforce their own server/common config gates instead of canceling bullet
 * hits (canceling AmmoHitBlockEvent could let bullets pass through walls).
 *
 * Forge 47.4.10's watched config is an autosaving NightConfig 3.6.4 wrapper.
 * ConfigValue.set mutates that wrapper before its inner file lock is taken;
 * periodic writes can race the file watcher's parser. The policy below never
 * writes a protected value to that config. A supported spec child-config view
 * supplies false to the native getters while preserving authored file bytes.
 *
 * No TaCZ or Superb Warfare classes are linked at class-load time, so those
 * optional mods can still be absent without causing a startup crash.
 */
public final class CombatTerrainSafety {
    private static final String TACZ_AMMO = "com.tacz.guns.config.common.AmmoConfig";
    private static final String SBW_VEHICLE =
            "com.atsuishio.superbwarfare.config.server.VehicleConfig";
    private static final String SBW_PROJECTILE =
            "com.atsuishio.superbwarfare.config.server.ProjectileConfig";
    private static final String SBW_EXPLOSION =
            "com.atsuishio.superbwarfare.config.server.ExplosionConfig";

    private record Guard(String type, String field) {}

    private static final List<Guard> TACZ = List.of(
            new Guard(TACZ_AMMO, "DESTROY_GLASS"),
            new Guard(TACZ_AMMO, "IGNITE_BLOCK"),
            new Guard(TACZ_AMMO, "EXPLOSIVE_AMMO_DESTROYS_BLOCK")
    );
    private static final List<Guard> SBW = List.of(
            new Guard(SBW_VEHICLE, "COLLISION_DESTROY_SOFT_BLOCKS"),
            new Guard(SBW_VEHICLE, "COLLISION_DESTROY_NORMAL_BLOCKS"),
            new Guard(SBW_VEHICLE, "COLLISION_DESTROY_HARD_BLOCKS"),
            new Guard(SBW_VEHICLE, "COLLISION_DESTROY_BLOCKS_BEASTLY"),
            new Guard(SBW_PROJECTILE, "PROJECTILE_DESTROY_BLOCKS"),
            new Guard(SBW_EXPLOSION, "EXPLOSION_DESTROY"),
            new Guard(SBW_EXPLOSION, "EXTRA_EXPLOSION_EFFECT")
    );

    private static final Map<String, Binding> BINDINGS = new LinkedHashMap<>();
    private static final Set<String> REGISTERED = new LinkedHashSet<>();

    private record Binding(ModConfig config, ForgeConfigSpec spec,
                           List<ForgeConfigSpec.BooleanValue> settings, long reloads) {}

    private CombatTerrainSafety() {}

    /** Called during mod construction, before Forge loads COMMON/SERVER configs. */
    public static synchronized void register() {
        for (String provider : List.of("tacz", "superbwarfare")) {
            if (!ModList.get().isLoaded(provider) || REGISTERED.contains(provider)) continue;
            try {
                Object container = ModList.get().getModContainerById(provider).orElseThrow();
                // Both accessors are public. KFF 4.11.0 publishes its Kotlin-internal
                // property using this exact JVM name; unsupported loaders fail visibly.
                String method = switch (container.getClass().getName()) {
                    case "net.minecraftforge.fml.javafmlmod.FMLModContainer" -> "getEventBus";
                    case "thedarkcolour.kotlinforforge.KotlinModContainer" -> "getEventBus$kfflang";
                    default -> throw new IllegalStateException("Unsupported native config container: " + container.getClass().getName());
                };
                IEventBus bus = (IEventBus) container.getClass().getMethod(method).invoke(container);
                bus.addListener(EventPriority.HIGHEST, CombatTerrainSafety::onConfig);
                REGISTERED.add(provider);
            } catch (ReflectiveOperationException | LinkageError failure) {
                throw new IllegalStateException("Cannot register CatanCraft terrain policy for " + provider, failure);
            }
        }
    }

    private static synchronized void onConfig(ModConfigEvent event) {
        ModConfig config = event.getConfig();
        String provider = config.getModId();
        ModConfig.Type required = switch (provider) {
            case "tacz" -> ModConfig.Type.COMMON;
            case "superbwarfare" -> ModConfig.Type.SERVER;
            default -> null;
        };
        if (required == null || config.getType() != required) return;
        // TaCZ explicitly fires COMMON events for this separate early gun-pack
        // spec, which has no gameplay ammo gates and is removed from ConfigTracker.
        if (provider.equals("tacz") && config.getFileName().equals("tacz-pre.toml")) return;
        if (event instanceof ModConfigEvent.Unloading) {
            BINDINGS.remove(provider);
            return;
        }
        if (!(event instanceof ModConfigEvent.Loading || event instanceof ModConfigEvent.Reloading)) return;
        try {
            if (!(config.getSpec() instanceof ForgeConfigSpec spec) || config.getConfigData() == null)
                throw new IllegalStateException("Native config is not a loaded Forge spec");
            List<ForgeConfigSpec.BooleanValue> settings = new ArrayList<>();
            Set<List<String>> paths = new LinkedHashSet<>();
            for (Guard guard : guards(provider)) {
                Field field = Class.forName(guard.type()).getField(guard.field());
                Object value = field.get(null);
                if (field.getType() != ForgeConfigSpec.BooleanValue.class
                        || !(value instanceof ForgeConfigSpec.BooleanValue setting)
                        || spec.getValues().getRaw(setting.getPath()) != setting
                        || !paths.add(List.copyOf(setting.getPath())))
                    throw new IllegalStateException("Unexpected native terrain config field " + guard.type() + "." + guard.field());
                settings.add(setting);
            }
            CommentedConfig raw = config.getConfigData();
            PolicyConfig overlay = raw instanceof CommentedFileConfig file
                    ? new FilePolicyConfig(file, paths) : new PolicyConfig(raw, paths);
            // Lifecycle events follow Forge's own correction. Refuse a changed
            // contract instead of letting setConfig correct/autosave through us.
            if (!spec.isCorrect(raw) || !spec.isCorrect(overlay))
                throw new IllegalStateException("Native config was not corrected before its lifecycle event");
            spec.setConfig(overlay); // Public API also clears every ConfigValue cache.
            Binding previous = BINDINGS.get(provider);
            long reloads = (previous == null ? 0 : previous.reloads())
                    + (event instanceof ModConfigEvent.Reloading ? 1 : 0);
            BINDINGS.put(provider, new Binding(config, spec, List.copyOf(settings), reloads));
            verify(provider, BINDINGS.get(provider));
            if (event instanceof ModConfigEvent.Loading) {
                CatanCraft.LOGGER.info("CatanCraft terrain policy installed {} effective false flags for {}; authored config preserved",
                        settings.size(), provider);
            }
        } catch (Exception | LinkageError failure) {
            BINDINGS.remove(provider);
            throw new IllegalStateException("CatanCraft terrain protection cannot support " + provider + " config " + config.getFileName(), failure);
        }
    }

    /** Read-only periodic verification; never touches NightConfig's autosave path. */
    public static void enforce() {
        for (String provider : List.of("tacz", "superbwarfare"))
            if (ModList.get().isLoaded(provider)) requireProtected(provider);
    }

    /** Native procurement must refuse unknown/unloaded protection contracts. */
    public static synchronized void requireProtected(String provider) {
        guards(provider);
        Binding binding = BINDINGS.get(provider);
        if (!REGISTERED.contains(provider) || binding == null)
            throw new IllegalStateException("CatanCraft terrain policy is not loaded for " + provider);
        verify(provider, binding);
    }

    private static void verify(String provider, Binding binding) {
        if (!binding.spec().isLoaded() || binding.config().getConfigData() == null
                || binding.settings().size() != guards(provider).size())
            throw new IllegalStateException("CatanCraft terrain policy is incomplete for " + provider);
        for (ForgeConfigSpec.BooleanValue setting : binding.settings()) {
            setting.clearCache();
            if (!Boolean.FALSE.equals(setting.get()))
                throw new IllegalStateException("Native terrain protection is not effective: " + provider + ":" + setting.getPath());
        }
    }

    static synchronized long reloadCount(String provider) {
        requireProtected(provider);
        return BINDINGS.get(provider).reloads();
    }

    private static List<Guard> guards(String provider) {
        return switch (provider) {
            case "tacz" -> TACZ;
            case "superbwarfare" -> SBW;
            default -> throw new IllegalArgumentException("Unknown terrain provider " + provider);
        };
    }

    /** Only the spec receives this view; ModConfig keeps its original watched data. */
    private static class PolicyConfig extends CommentedConfigWrapper<CommentedConfig> {
        private final Set<List<String>> paths;

        PolicyConfig(CommentedConfig config, Set<List<String>> paths) {
            super(config);
            this.paths = Set.copyOf(paths);
        }

        @Override @SuppressWarnings("unchecked")
        public <T> T getRaw(List<String> path) {
            if (paths.contains(path)) return (T) Boolean.FALSE;
            T value = super.getRaw(path);
            Set<List<String>> descendants = descendants(path);
            if (descendants.isEmpty()) return value;
            if (!(value instanceof CommentedConfig nested))
                throw new IllegalStateException("Unexpected native config section " + path);
            return (T) new PolicyConfig(nested, descendants);
        }

        private Set<List<String>> descendants(List<String> prefix) {
            Set<List<String>> result = new LinkedHashSet<>();
            for (List<String> path : paths)
                if (path.size() > prefix.size() && path.subList(0, prefix.size()).equals(prefix))
                    result.add(List.copyOf(path.subList(prefix.size(), path.size())));
            return result;
        }

        @Override @SuppressWarnings("unchecked")
        public <T> T set(List<String> path, Object value) {
            if (paths.contains(path)) {
                // Throw before ConfigValue.set reaches its cachedValue assignment.
                if (!Boolean.FALSE.equals(value)) throw new IllegalArgumentException("CatanCraft requires terrain protection: " + path);
                return (T) Boolean.FALSE;
            }
            rejectProtectedTree(path);
            return super.set(path, value);
        }

        private void rejectProtectedTree(List<String> path) {
            if (paths.contains(path) || !descendants(path).isEmpty())
                throw new IllegalArgumentException("Cannot replace/remove a protected native config section: " + path);
        }

        @Override public boolean add(List<String> path, Object value) {
            rejectProtectedTree(path);
            return super.add(path, value);
        }

        @Override public <T> T remove(List<String> path) {
            rejectProtectedTree(path);
            return super.remove(path);
        }

        @Override public void clear() { throw new IllegalArgumentException("Cannot clear a protected native config"); }

        // Forge validates through valueMap; copies and nested reads must observe
        // the same policy as ConfigValue.get. Bulk views are read-only, while
        // unrelated ConfigValue.set/save continue through the normal delegate.
        @Override public Map<String, Object> valueMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            for (String key : config.valueMap().keySet()) result.put(key, getRaw(List.of(key)));
            return Collections.unmodifiableMap(result);
        }

        @Override public Set<? extends CommentedConfig.Entry> entrySet() {
            Set<CommentedConfig.Entry> result = new LinkedHashSet<>();
            for (String key : config.valueMap().keySet()) result.add(new CommentedConfig.Entry() {
                public String getKey() { return key; }
                public <T> T getRawValue() { return PolicyConfig.this.getRaw(List.of(key)); }
                public String getComment() { return PolicyConfig.this.getComment(List.of(key)); }
                public <T> T setValue(Object value) { throw new UnsupportedOperationException("Read-only terrain policy view"); }
                public String setComment(String comment) { throw new UnsupportedOperationException("Read-only terrain policy view"); }
                public String removeComment() { throw new UnsupportedOperationException("Read-only terrain policy view"); }
            });
            return Collections.unmodifiableSet(result);
        }
    }

    /** Retains ForgeConfigSpec.save() behavior for unrelated file-backed settings. */
    private static final class FilePolicyConfig extends PolicyConfig implements CommentedFileConfig {
        private final CommentedFileConfig file;
        FilePolicyConfig(CommentedFileConfig file, Set<List<String>> paths) { super(file, paths); this.file = file; }
        public File getFile() { return file.getFile(); }
        public Path getNioPath() { return file.getNioPath(); }
        public void save() { file.save(); }
        public void load() { file.load(); }
        public void close() { file.close(); }
    }
}
