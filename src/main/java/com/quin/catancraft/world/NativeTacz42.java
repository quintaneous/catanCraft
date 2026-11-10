package com.quin.catancraft.world;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Optional TaCZ 1.2.0 boundary. Only effective server resources authorize supply. */
final class NativeTacz42 {
    static final String REFERENCE_SHA256 = "2c24dc08835a7489cd836bf05debc206e71545183098bcf217f59fb4fedb5b05";
    private static final String REFERENCE = "/data/catancraft/economy/tacz_native42.json";
    private static final int MAX_RESOURCE = 512 * 1024;
    private static final int MAX_STATE = 16 * 1024 * 1024;

    private NativeTacz42() {}

    static NativeWeapon42.Supply quote(MinecraftServer server, WeaponStock42.Offer offer) throws IOException {
        if (server == null || !server.isSameThread()) throw unavailable("Native supply requires the server thread.");
        if (!"tacz".equals(offer.provider())) throw unavailable("Wrong native weapon provider.");
        if (!ModList.get().isLoaded("tacz")) throw unavailable("The TaCZ weapon mod is unavailable.");
        String version = ModList.get().getModContainerById("tacz").orElseThrow().getModInfo().getVersion().toString();
        if (!version.equals("1.2.0")) throw unavailable("This TaCZ version has not been qualified.");
        WeaponTierCatalog41.Entry tier = WeaponTierCatalog41.entry(offer.referenceGun());
        if (WeaponTierCatalog41.LEGACY_REVISION.equals(offer.revision())) {
            WeaponTierCatalog41.requireExistingOffer(offer.nativeId(), offer.requiredLevel());
        } else {
            WeaponTierCatalog41.requireShop(offer.referenceGun(), offer.requiredLevel(), WeaponTierCatalog41.CANDIDATE_REVISION);
        }
        try {
            JsonObject reference = NativeWeapon42.reference(REFERENCE, REFERENCE_SHA256, MAX_RESOURCE);
            JsonObject expected = reference.getAsJsonObject("guns").getAsJsonObject(offer.referenceGun());
            if (expected == null) throw unavailable("The reference gun is unknown.");
            Class<?> assetsClass = Class.forName("com.tacz.guns.resource.CommonAssetsManager");
            Object assets = assetsClass.getMethod("getInstance").invoke(null);
            if (assets == null) throw unavailable("The TaCZ server resources are unavailable.");
            Gson gson = (Gson) assetsClass.getField("GSON").get(null);
            Object cacheObject = call(assets, "getNetworkCache");
            if (!(cacheObject instanceof Map<?, ?> cache)) throw unavailable("TaCZ has no effective resource cache.");
            ResourceLocation gunId = new ResourceLocation(offer.referenceGun());
            ResourceLocation dataId = new ResourceLocation(tier.dataId());
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            Object index = index(api, "getCommonGunIndex", gunId);
            Object data = call(index, "getGunData");
            Object pojo = call(index, "getPojo");
            if (!dataId.equals(call(pojo, "getData")) || !tier.category().equals(call(index, "getType")))
                throw unavailable("The effective TaCZ gun index differs from the reference.");
            JsonObject rawIndex = raw(cache, "GUN_INDEX", gunId).getAsJsonObject();
            JsonObject comparableIndex = rawIndex.deepCopy();
            // TaCZ GunIndexManager adds this derived capability after parsing; it is
            // retained in the quote fingerprint but was never a source index field.
            comparableIndex.remove("has_unload_ammo_script");
            same(comparableIndex, expected.get("index"), "gun index");
            JsonElement rawData = raw(cache, "GUN_DATA", dataId);
            same(rawData, expected.get("data"), "gun data");
            same(gson.toJsonTree(pojo), gson.toJsonTree(gson.fromJson(expected.get("index"), pojo.getClass())), "parsed gun index");
            Object expectedData = normalizedData(gson, expected.get("data"), data.getClass());
            same(gson.toJsonTree(data), gson.toJsonTree(expectedData), "parsed gun data");
            ResourceLocation ammoId = (ResourceLocation) call(data, "getAmmoId");
            if (!new JsonPrimitive(ammoId.toString()).toString().equals(tier.ammoJson()))
                throw unavailable("The effective TaCZ ammunition differs from the reference.");
            Object ammoIndex = index(api, "getCommonAmmoIndex", ammoId);
            JsonElement expectedAmmo = reference.getAsJsonObject("ammo").get(ammoId.toString());
            if (expectedAmmo == null) throw unavailable("The native ammunition has no qualified reference.");
            same(raw(cache, "AMMO_INDEX", ammoId), expectedAmmo, "ammunition index");
            Object ammoPojo = call(ammoIndex, "getPojo");
            same(gson.toJsonTree(ammoPojo), gson.toJsonTree(gson.fromJson(expectedAmmo, ammoPojo.getClass())), "parsed ammunition index");
            if (((Number) call(ammoIndex, "getStackSize")).intValue()
                    != Math.max(expectedAmmo.getAsJsonObject().get("stack_size").getAsInt(), 1))
                throw unavailable("The parsed ammunition capacity differs from the reference.");
            qualifyDefaultAttachments(api, gson, cache, gunId, reference);
            List<?> modes = (List<?>) call(data, "getFireModeSet");
            JsonArray modeNames = new JsonArray();
            for (Object mode : modes) modeNames.add(((Enum<?>) mode).name().toLowerCase(Locale.ROOT));
            same(modeNames, JsonParser.parseString(tier.fireModesJson()), "fire modes");
            Object scriptId = call(data, "getScript");
            if (!java.util.Objects.equals(tier.script(), scriptId == null ? null : scriptId.toString()))
                throw unavailable("The native gun script differs from the reference.");
            JsonObject scripts = scriptState(server);
            if (scriptId != null) {
                String path = new ResourceLocation(((ResourceLocation) scriptId).getNamespace(),
                        "scripts/" + ((ResourceLocation) scriptId).getPath() + ".lua").toString();
                JsonElement expectedScript = reference.getAsJsonObject("scripts").get(scriptId.toString());
                if (expectedScript == null || !expectedScript.equals(scripts.get(path)) || call(index, "getScript") == null)
                    throw unavailable("The effective TaCZ gun script has not been qualified.");
            }
            boolean gun = offer.kind() == WeaponShopCatalog40.Kind.GUN;
            if (gun && !offer.nativeId().equals(offer.referenceGun()) || !gun && !offer.nativeId().equals(ammoId.toString()))
                throw unavailable("The offer does not identify its native gun or ammunition.");
            if (gun && modes.stream().noneMatch(mode -> ((Enum<?>) mode).name().equals(offer.fireMode())))
                throw unavailable("The offered firing mode is unavailable.");
            JsonObject state = new JsonObject();
            state.addProperty("revision", offer.revision());
            state.addProperty("offer", offer.id());
            state.addProperty("nativeId", offer.nativeId());
            state.addProperty("count", offer.count());
            state.addProperty("level", offer.requiredLevel());
            state.addProperty("fireMode", offer.fireMode());
            state.addProperty("version", version);
            state.add("resources", cacheState(cache));
            state.add("gunData", gson.toJsonTree(data));
            state.add("gunIndex", gson.toJsonTree(pojo));
            state.add("ammoIndex", gson.toJsonTree(ammoPojo));
            state.addProperty("ammoStackSize", ((Number) call(ammoIndex, "getStackSize")).intValue());
            state.addProperty("unloadScript", (Boolean) call(index, "hasUnloadAmmoScript"));
            state.add("scripts", scripts);
            state.add("configs", configState());
            // Attachments can affect built-in defaults without changing the gun JSON.
            state.add("attachments", attachmentState(api, gson));
            ItemStack stack = build(offer, api);
            state.addProperty("item", stack.save(new CompoundTag()).toString());
            byte[] bytes = NativeWeapon42.canonical(state).getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_STATE) throw unavailable("The native resource state exceeds the qualification bound.");
            return new NativeWeapon42.Supply(stack, NativeWeapon42.sha(bytes));
        } catch (IOException failure) {
            throw failure;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            Throwable cause = failure instanceof InvocationTargetException reflected ? reflected.getCause() : failure;
            throw new IOException("The current TaCZ resources cannot safely supply this item; no purchase was made.", cause);
        }
    }

    static void recheck(MinecraftServer server, WeaponStock42.Offer offer, NativeWeapon42.Supply quoted) throws IOException {
        NativeWeapon42.Supply current = quote(server, offer);
        if (!current.fingerprint().equals(quoted.fingerprint())
                || !current.item().save(new CompoundTag()).equals(quoted.item().save(new CompoundTag())))
            throw unavailable("The TaCZ resources changed after this quote; request a new quote.");
    }

    private static Object call(Object object, String name) throws ReflectiveOperationException {
        return object.getClass().getMethod(name).invoke(object);
    }

    private static Object index(Class<?> api, String method, ResourceLocation id) throws ReflectiveOperationException, IOException {
        Object value = api.getMethod(method, ResourceLocation.class).invoke(null, id);
        if (!(value instanceof Optional<?> present) || present.isEmpty()) throw unavailable("A native gun or ammunition index is missing.");
        return present.get();
    }

    private static JsonElement raw(Map<?, ?> cache, String type, ResourceLocation id) throws IOException {
        for (Map.Entry<?, ?> entry : cache.entrySet()) {
            if (((Enum<?>) entry.getKey()).name().equals(type) && entry.getValue() instanceof Map<?, ?> records) {
                Object text = records.get(id);
                if (text instanceof String json && json.length() <= MAX_RESOURCE) return JsonParser.parseString(json);
            }
        }
        throw unavailable("The effective TaCZ " + type + " resource is missing.");
    }

    private static void same(JsonElement actual, JsonElement expected, String what) throws IOException {
        if (actual == null || expected == null || !NativeWeapon42.canonical(actual).equals(NativeWeapon42.canonical(expected)))
            throw unavailable("The effective TaCZ " + what + " differs from the qualified reference.");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object normalizedData(Gson gson, JsonElement reference, Class<?> type) throws ReflectiveOperationException {
        Object data = gson.fromJson(reference, type);
        Class.forName("com.tacz.guns.resource.manager.GunDataManager")
                .getMethod("initExclusiveAttachmentModifiers", type, JsonElement.class).invoke(null, data, reference);
        Map defaults = (Map) Class.forName("com.tacz.guns.resource.pojo.data.gun.InaccuracyType")
                .getMethod("getDefaultInaccuracy").invoke(null);
        Map inaccuracy = (Map) call(data, "getInaccuracy");
        if (inaccuracy == null || inaccuracy.isEmpty()) type.getMethod("setInaccuracy", Map.class).invoke(data, defaults);
        else defaults.forEach(inaccuracy::putIfAbsent);
        Object recoil = call(data, "getRecoil");
        for (String axis : List.of("getPitch", "getYaw")) {
            Object[] frames = (Object[]) call(recoil, axis);
            if (frames != null) Arrays.sort(frames);
        }
        return data;
    }

    private static JsonObject cacheState(Map<?, ?> cache) throws IOException {
        JsonObject result = new JsonObject();
        int count = 0, bytes = 0;
        for (Map.Entry<?, ?> type : cache.entrySet()) {
            JsonObject records = new JsonObject();
            if (!(type.getValue() instanceof Map<?, ?> map)) throw unavailable("Invalid native resource map.");
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getValue() instanceof String text) || ++count > 10000 || (bytes += text.length()) > MAX_STATE)
                    throw unavailable("The native resource map exceeds the qualification bound.");
                records.add(entry.getKey().toString(), JsonParser.parseString(text));
            }
            result.add(((Enum<?>) type.getKey()).name(), records);
        }
        return result;
    }

    private static JsonObject scriptState(MinecraftServer server) throws IOException {
        JsonObject result = new JsonObject();
        var resources = server.getResourceManager().listResources("scripts", id -> id.getPath().endsWith(".lua"));
        if (resources.size() > 1024) throw unavailable("Too many effective native scripts.");
        int total = 0;
        for (var entry : resources.entrySet()) {
            try (InputStream stream = entry.getValue().open()) {
                byte[] bytes = stream.readNBytes(MAX_RESOURCE + 1);
                if (bytes.length > MAX_RESOURCE || (total += bytes.length) > MAX_STATE) throw unavailable("Native scripts exceed the qualification bound.");
                // Published scripts and Git source differ only in line endings.
                result.addProperty(entry.getKey().toString(), NativeWeapon42.sha(new String(bytes, StandardCharsets.UTF_8)
                        .replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8)));
            }
        }
        return result;
    }

    private static JsonObject configState() throws IOException {
        JsonObject result = new JsonObject();
        for (ModConfig.Type type : List.of(ModConfig.Type.COMMON, ModConfig.Type.SERVER)) {
            for (ModConfig config : ConfigTracker.INSTANCE.configSets().get(type)) {
                if (config.getModId().equals("tacz")) {
                    if (config.getConfigData() == null) throw unavailable("The TaCZ server configuration is not loaded.");
                    result.add(type.name() + ":" + config.getFileName(), configValue(config.getConfigData(), 0));
                }
            }
        }
        if (result.size() == 0) throw unavailable("The effective TaCZ configuration is missing.");
        return result;
    }

    private static JsonElement configValue(Object value, int depth) throws IOException {
        if (depth > 24) throw unavailable("The native configuration is too deeply nested.");
        if (value == null) return JsonNull.INSTANCE;
        if (value instanceof UnmodifiableConfig config) return configValue(config.valueMap(), depth + 1);
        if (value instanceof Map<?, ?> values) {
            JsonObject result = new JsonObject();
            for (var entry : values.entrySet()) result.add(entry.getKey().toString(), configValue(entry.getValue(), depth + 1));
            return result;
        }
        if (value instanceof List<?> list) {
            JsonArray result = new JsonArray();
            for (Object element : list) result.add(configValue(element, depth + 1));
            return result;
        }
        if (value instanceof String text) return new JsonPrimitive(text);
        if (value instanceof Boolean bool) return new JsonPrimitive(bool);
        if (value instanceof Number number) return new JsonPrimitive(number);
        if (value instanceof Enum<?> enumeration) return new JsonPrimitive(enumeration.name());
        throw unavailable("Unsupported native configuration value.");
    }

    private static JsonObject attachmentState(Class<?> api, Gson gson) throws ReflectiveOperationException, IOException {
        JsonObject result = new JsonObject();
        for (String method : List.of("getAllCommonAttachmentIndex", "getAllCommonAttachmentVariantIndex", "getAllCommonSlotAdapterIndex")) {
            JsonObject records = new JsonObject();
            Object value = api.getMethod(method).invoke(null);
            if (!(value instanceof Iterable<?> entries)) throw unavailable("Native attachment resources are missing.");
            int count = 0;
            for (Object element : entries) {
                if (!(element instanceof Map.Entry<?, ?> entry) || ++count > 4096) throw unavailable("Invalid native attachment resource state.");
                JsonObject record = new JsonObject();
                record.add("index", gson.toJsonTree(call(entry.getValue(), "getPojo")));
                record.add("data", gson.toJsonTree(call(entry.getValue(), "getData")));
                records.add(entry.getKey().toString(), record);
            }
            result.add(method, records);
        }
        return result;
    }

    private static void qualifyDefaultAttachments(Class<?> api, Gson gson, Map<?, ?> cache,
                                                   ResourceLocation gunId, JsonObject reference)
            throws ReflectiveOperationException, IOException {
        Object defaults = Class.forName("com.tacz.guns.util.DefaultGunAttachments")
                .getMethod("defaults", ResourceLocation.class).invoke(null, gunId);
        for (Object value : ((EnumMap<?, ?>) defaults).values()) {
            ResourceLocation id = (ResourceLocation) value;
            JsonObject expected = reference.getAsJsonObject("defaultAttachments").getAsJsonObject(id.toString());
            if (expected == null) throw unavailable("The native default attachment has no qualified reference.");
            Object index = index(api, "getCommonAttachmentIndex", id);
            Object pojo = call(index, "getPojo");
            ResourceLocation dataId = (ResourceLocation) call(pojo, "getData");
            same(raw(cache, "ATTACHMENT_INDEX", id), expected.get("index"), "default attachment index");
            same(raw(cache, "ATTACHMENT_DATA", dataId), expected.get("data"), "default attachment data");
            same(gson.toJsonTree(pojo), gson.toJsonTree(gson.fromJson(expected.get("index"), pojo.getClass())), "parsed default attachment index");
            Object data = call(index, "getData");
            Object expectedData = gson.fromJson(expected.get("data"), data.getClass());
            Class.forName("com.tacz.guns.resource.manager.AttachmentDataManager")
                    .getMethod("initModifiers", data.getClass(), JsonElement.class, Gson.class)
                    .invoke(null, expectedData, expected.get("data"), gson);
            same(gson.toJsonTree(data), gson.toJsonTree(expectedData), "parsed default attachment data");
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ItemStack build(WeaponStock42.Offer offer, Class<?> api) throws ReflectiveOperationException, IOException {
        boolean gun = offer.kind() == WeaponShopCatalog40.Kind.GUN;
        ResourceLocation id = new ResourceLocation(offer.nativeId());
        Class<?> builderClass = Class.forName("com.tacz.guns.api.item.builder." + (gun ? "GunItemBuilder" : "AmmoItemBuilder"));
        Object builder = builderClass.getMethod("create").invoke(null);
        builderClass.getMethod("setId", ResourceLocation.class).invoke(builder, id);
        builderClass.getMethod("setCount", int.class).invoke(builder, offer.count());
        Object mode = null;
        if (gun) {
            Class<? extends Enum> modeClass = (Class<? extends Enum>) Class.forName("com.tacz.guns.api.item.gun.FireMode");
            mode = Enum.valueOf(modeClass, offer.fireMode());
            builderClass.getMethod("setFireMode", modeClass).invoke(builder, mode);
            builderClass.getMethod("setAmmoCount", int.class).invoke(builder, 0);
            builderClass.getMethod("setAmmoInBarrel", boolean.class).invoke(builder, false);
            Object defaults = Class.forName("com.tacz.guns.util.DefaultGunAttachments").getMethod("defaults", ResourceLocation.class).invoke(null, id);
            for (Object attachment : ((EnumMap<?, ?>) defaults).values()) index(api, "getCommonAttachmentIndex", (ResourceLocation) attachment);
            builderClass.getMethod("putAllVirtualAttachment", EnumMap.class).invoke(builder, defaults);
        }
        Object built = builderClass.getMethod("build").invoke(builder);
        if (!(built instanceof ItemStack stack) || stack.isEmpty() || stack.getCount() != offer.count() || stack.getCount() > stack.getMaxStackSize())
            throw unavailable("The native builder did not produce the requested stack.");
        Class<?> itemApi = Class.forName("com.tacz.guns.api.item." + (gun ? "IGun" : "IAmmo"));
        if (!itemApi.isInstance(stack.getItem()) || !id.equals(itemApi.getMethod(gun ? "getGunId" : "getAmmoId", ItemStack.class).invoke(stack.getItem(), stack)))
            throw unavailable("The built TaCZ item has the wrong native identity.");
        if (gun && (!mode.equals(itemApi.getMethod("getFireMode", ItemStack.class).invoke(stack.getItem(), stack))
                || ((Number) itemApi.getMethod("getCurrentAmmoCount", ItemStack.class).invoke(stack.getItem(), stack)).intValue() != 0
                || Boolean.TRUE.equals(itemApi.getMethod("hasBulletInBarrel", ItemStack.class).invoke(stack.getItem(), stack))))
            throw unavailable("The native gun did not retain its requested empty state and firing mode.");
        CompoundTag saved = stack.save(new CompoundTag());
        if (!saved.equals(ItemStack.of(saved.copy()).save(new CompoundTag()))) throw unavailable("The native item cannot be saved exactly.");
        return stack.copy();
    }

    private static IOException unavailable(String message) { return new IOException(message); }
}
