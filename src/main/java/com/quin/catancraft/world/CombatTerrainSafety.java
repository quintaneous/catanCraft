package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
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
                        || !(spec.getSpec().getRaw(setting.getPath()) instanceof ForgeConfigSpec.ValueSpec definition)
                        || !definition.test(Boolean.FALSE)
                        || !paths.add(List.copyOf(setting.getPath())))
                    throw new IllegalStateException("Unexpected native terrain config field " + guard.type() + "." + guard.field());
                settings.add(setting);
            }
            CommentedConfig raw = config.getConfigData();
            CommentedConfig overlay = policyView(spec, raw, paths);
            // Forge starts watching BEFORE its initial correction and Loading
            // event. A watcher parser may therefore be rebuilding the raw map
            // even here. Validate the effective view, whose structure/comments
            // come from the immutable spec and whose missing-value behavior is
            // the same native default used by ConfigValue.get, not that map.
            if (!spec.isCorrect(overlay))
                throw new IllegalStateException("Native effective config does not satisfy its Forge spec");
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

    private record Schema(Map<String, Schema> children, ForgeConfigSpec.ValueSpec value, String comment) {
        Schema at(List<String> path) {
            Schema result = this;
            for (String part : path) {
                result = result.children().get(part);
                if (result == null) return null;
            }
            return result;
        }
    }

    private static Schema schema(ForgeConfigSpec spec, UnmodifiableConfig definitions, List<String> prefix) {
        Map<String, Schema> children = new LinkedHashMap<>();
        for (var entry : definitions.valueMap().entrySet()) {
            List<String> path = append(prefix, List.of(entry.getKey()));
            Object definition = entry.getValue();
            if (definition instanceof ForgeConfigSpec.ValueSpec value)
                children.put(entry.getKey(), new Schema(Map.of(), value, value.getComment()));
            else if (definition instanceof UnmodifiableConfig nested)
                children.put(entry.getKey(), schema(spec, nested, path));
            else throw new IllegalStateException("Unknown native config schema node " + path);
        }
        return new Schema(Collections.unmodifiableMap(children), null, spec.getLevelComment(prefix));
    }

    private static List<String> append(List<String> prefix, List<String> path) {
        List<String> result = new ArrayList<>(prefix);
        result.addAll(path);
        return List.copyOf(result);
    }

    // Package visibility permits a deterministic regression against a raw map
    // that is being cleared/repopulated, without replacing the live native spec.
    static CommentedConfig policyView(ForgeConfigSpec spec, CommentedConfig raw, Set<List<String>> paths) {
        Schema schema = schema(spec, spec.getSpec(), List.of());
        return raw instanceof CommentedFileConfig file ? new FilePolicyConfig(file, paths, schema)
                : new PolicyConfig(raw, paths, schema, List.of());
    }

    /** Only the spec receives this view; ModConfig keeps its original watched data. */
    private static class PolicyConfig extends CommentedConfigWrapper<CommentedConfig> {
        private final Set<List<String>> paths;
        private final Schema schema;
        private final List<String> prefix;

        PolicyConfig(CommentedConfig config, Set<List<String>> paths, Schema schema, List<String> prefix) {
            super(config);
            this.paths = Set.copyOf(paths);
            this.schema = schema;
            this.prefix = prefix;
        }

        @Override @SuppressWarnings("unchecked")
        public <T> T getRaw(List<String> path) {
            List<String> full = append(prefix, path);
            if (paths.contains(full)) return (T) Boolean.FALSE;
            Schema node = schema.at(path);
            if (node == null) return null;
            if (node.value() == null) return (T) new PolicyConfig(config, paths, node, full);
            // A missing leaf during the watcher's clear/repopulate has exactly
            // the same fallback as Forge ConfigValue.get. Never iterate the
            // watched map or retain one of its replaceable nested configs.
            return config.getOrElse(full, () -> (T) node.value().getDefault());
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
            List<String> full = append(prefix, path);
            if (paths.contains(full)) {
                // Throw before ConfigValue.set reaches its cachedValue assignment.
                if (!Boolean.FALSE.equals(value)) throw new IllegalArgumentException("CatanCraft requires terrain protection: " + path);
                return (T) Boolean.FALSE;
            }
            rejectProtectedTree(full);
            return super.set(full, value);
        }

        private void rejectProtectedTree(List<String> path) {
            if (paths.contains(path) || !descendants(path).isEmpty())
                throw new IllegalArgumentException("Cannot replace/remove a protected native config section: " + path);
        }

        @Override public boolean add(List<String> path, Object value) {
            List<String> full = append(prefix, path);
            rejectProtectedTree(full);
            return super.add(full, value);
        }

        @Override public <T> T remove(List<String> path) {
            List<String> full = append(prefix, path);
            rejectProtectedTree(full);
            return super.remove(full);
        }

        @Override public void clear() { throw new IllegalArgumentException("Cannot clear a protected native config"); }

        @Override public boolean contains(List<String> path) { return schema.at(path) != null; }
        @Override public int size() { return schema.children().size(); }
        @Override public boolean isEmpty() { return schema.children().isEmpty(); }
        @Override public String getComment(List<String> path) {
            Schema node = schema.at(path);
            return node == null ? null : node.comment();
        }
        @Override public boolean containsComment(List<String> path) { return getComment(path) != null; }
        @Override public Map<String, String> commentMap() {
            Map<String, String> result = new LinkedHashMap<>();
            schema.children().forEach((key, node) -> { if (node.comment() != null) result.put(key, node.comment()); });
            return Collections.unmodifiableMap(result);
        }

        // Forge validates through valueMap; copies and nested reads must observe
        // the same policy as ConfigValue.get. Bulk views are read-only, while
        // unrelated ConfigValue.set/save continue through the normal delegate.
        @Override public Map<String, Object> valueMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            for (String key : schema.children().keySet()) result.put(key, getRaw(List.of(key)));
            return Collections.unmodifiableMap(result);
        }

        @Override public Set<? extends CommentedConfig.Entry> entrySet() {
            Set<CommentedConfig.Entry> result = new LinkedHashSet<>();
            for (String key : schema.children().keySet()) result.add(new CommentedConfig.Entry() {
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
        FilePolicyConfig(CommentedFileConfig file, Set<List<String>> paths, Schema schema) {
            super(file, paths, schema, List.of());
            this.file = file;
        }
        public File getFile() { return file.getFile(); }
        public Path getNioPath() { return file.getNioPath(); }
        public void save() { file.save(); }
        public void load() { file.load(); }
        public void close() { file.close(); }
    }
}
