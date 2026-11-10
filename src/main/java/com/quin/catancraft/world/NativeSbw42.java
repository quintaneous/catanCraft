package com.quin.catancraft.world;

import com.google.gson.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Optional published SBW 0.8.9.2 bridge. Only the 24 ordinary, pinned shop guns
 * and their four one-round native ammunition items are qualified here. No live
 * data object, mutable JSON tree, or GunData cache entry is retained in a quote. */
final class NativeSbw42 {
    private static final String BASE = "com.atsuishio.superbwarfare.";
    private static final String VERSION = "0.8.9.2";
    private static final String RESOURCE = "/data/catancraft/economy/native_sbw42_reference.json";
    private static final String RESOURCE_SHA = "058fbac6a7059a63aa514f7f59b246690a9f94867a9af67331cf679511b43c6b";
    private static volatile Map<String, Reference> references;
    private record Reference(String json, String itemClass) {}
    private record AmmoMapping(String token, String enumName, String itemId) {}
    private record Built(ItemStack item, String fingerprint) {}
    private NativeSbw42() {}

    static NativeWeapon42.Supply quote(MinecraftServer server, WeaponStock42.Offer offer) throws IOException {
        Built built = inspect(server, offer, null);
        return new NativeWeapon42.Supply(built.item(), built.fingerprint());
    }

    static void recheck(MinecraftServer server, WeaponStock42.Offer offer, NativeWeapon42.Supply quoted) throws IOException {
        ItemStack original = quoted.item();
        UUID identity = offer.kind() == WeaponShopCatalog40.Kind.GUN ? nativeIdentity(original) : null;
        Built rebuilt = inspect(server, offer, identity);
        if (!rebuilt.fingerprint().equals(quoted.fingerprint()))
            throw invalid("The SBW weapon data changed; review this item again.");
        if (!rebuilt.item().save(new CompoundTag()).equals(original.save(new CompoundTag())))
            throw invalid("The native SBW stack changed; review this item again.");
    }

    private static Built inspect(MinecraftServer server, WeaponStock42.Offer offer, UUID identity) throws IOException {
        if (server == null || !server.isSameThread()) throw invalid("SBW supply requires the current server thread.");
        if (!"superbwarfare".equals(offer.provider()) || !offer.fireMode().isEmpty())
            throw invalid("An ordinary native SBW offer and its native default mode are required.");
        if (!ModList.get().isLoaded("superbwarfare") || !ModList.get().getModContainerById("superbwarfare")
                .orElseThrow().getModInfo().getVersion().toString().equals(VERSION))
            throw invalid("The qualified SBW version is unavailable.");
        try {
            boolean gunOffer = offer.kind() == WeaponShopCatalog40.Kind.GUN;
            String gunId = offer.referenceGun();
            if (gunOffer && (!offer.nativeId().equals(gunId) || offer.count() != 1))
                throw invalid("The SBW gun offer has a different native reference.");
            WeaponTierCatalog41.Entry reference = WeaponTierCatalog41.requireShop(gunId, offer.requiredLevel(),
                    WeaponTierCatalog41.CANDIDATE_REVISION);
            Reference nativeReference = references().get(gunId);
            if (!"superbwarfare".equals(reference.provider()) || nativeReference == null)
                throw invalid("This SBW weapon has no ordinary native qualification.");
            AmmoMapping mapping = mapping(reference.ammoJson());
            if (!gunOffer && (!mapping.itemId().equals(offer.nativeId()) || offer.count() < 1 || offer.count() > 64))
                throw invalid("The SBW ammunition offer has a different native consumer.");

            Class<?> gunDataClass = type("data.gun.GunData"), gunPropClass = type("data.gun.GunProp");
            Class<?> gunItemClass = type("item.gun.GunItem"), defaultClass = type("data.gun.DefaultGunData");
            Gson gson = (Gson) type("data.DataLoader").getField("GSON").get(null);
            Map<?, ?> live = (Map<?, ?>) type("data.CustomData").getField("GUN_DATA").get(null);
            if (!live.containsKey(gunId)) throw invalid("The SBW gun data is missing or reloading.");
            Object raw = gunDataClass.getMethod("getDefault", String.class).invoke(null, gunId);
            if (raw == null || live.get(gunId) != raw || !defaultClass.isInstance(raw)
                    || Boolean.TRUE.equals(call(raw, "isDefaultData")) || !gunId.equals(call(raw, "getId")))
                throw invalid("Fallback SBW gun data cannot authorize a purchase.");
            String rawBefore = rawSnapshot(raw, gson);
            Object expected = decodeReference(nativeReference.json(), defaultClass);
            if (!rawBefore.equals(rawSnapshot(expected, gson)))
                throw invalid("The effective SBW gun definition differs from its qualified reference.");
            String configBefore = configuration(gson);

            Item registeredGun = registered(gunId);
            if (!gunItemClass.isInstance(registeredGun) || !nativeReference.itemClass().equals(registeredGun.getClass().getName()))
                throw invalid("The registered SBW item is not the qualified native gun.");
            ItemStack gun = new ItemStack(registeredGun, 1);
            Object data = gunDataClass.getMethod("from", ItemStack.class).invoke(null, gun);
            if (!gunId.equals(field(data, "id")) || call(data, "getDefault") != raw)
                throw invalid("The native gun resolved a different SBW definition.");
            CompoundTag gunTag = (CompoundTag) field(data, "gunDataTag");
            // A new quote lets native initialization assign the UUID. Recheck
            // supplies only the already-quoted native identity to the fresh copy.
            if (identity != null) gunTag.putUUID("UUID", identity);
            call(data, "initialize");
            if (!Boolean.TRUE.equals(call(data, "initialized"))) throw invalid("The native SBW gun did not initialize.");
            UUID actualIdentity = nativeIdentity(gun);
            if (identity != null && !identity.equals(actualIdentity)) throw invalid("The native SBW identity changed.");
            emptyGun(data, gun);

            Method get = gunDataClass.getMethod("get", gunPropClass);
            Object consumer = call(data, "selectedAmmoConsumer");
            validateConsumer(consumer, mapping);
            Object ammoType = call(consumer, "getPlayerAmmoType");
            Item ammoItem = (Item) call(ammoType, "getItem");
            if (registered(mapping.itemId()) != ammoItem) throw invalid("The native SBW ammunition mapping changed.");
            validateAmmoItem(ammoItem, ammoType);
            List<?> consumers = list(get.invoke(data, gunPropClass.getField("AMMO_CONSUMER").get(null)));
            if (consumers.size() != 1 || consumers.get(0) != consumer) throw invalid("Alternate SBW ammunition is held out.");
            requireNumber(get.invoke(data, gunPropClass.getField("MAX_ENERGY").get(null)), 0);
            requireNumber(get.invoke(data, gunPropClass.getField("EXPLOSION_DAMAGE").get(null)), 0);
            requireNumber(get.invoke(data, gunPropClass.getField("EXPLOSION_RADIUS").get(null)), 0);
            requireNumber(get.invoke(data, gunPropClass.getField("AMMO_COST_PER_SHOOT").get(null)), 1);
            if (((Number) get.invoke(data, gunPropClass.getField("MAGAZINE").get(null))).intValue() <= 0)
                throw invalid("Backpack-fed SBW guns are held out.");
            Object projectile = get.invoke(data, gunPropClass.getField("PROJECTILE").get(null));
            if (!"superbwarfare:projectile".equals(call(projectile, "getId")) || field(projectile, "data") != null)
                throw invalid("Special SBW projectiles are held out.");

            JsonObject computed = new JsonObject();
            Object companion = gunPropClass.getField("Companion").get(null);
            List<?> props = list(call(companion, "getEntries"));
            if (props.size() < 50 || props.size() > 200) throw invalid("Unknown SBW property schema.");
            for (Object prop : props) {
                String name = (String) call(prop, "getSerializationName");
                if (computed.has(name)) throw invalid("Duplicate SBW effective property.");
                Object value = get.invoke(data, prop);
                computed.add(name, name.equals("AvailablePerks") ? perks(value) : json(value, gson));
            }
            Object selectedMode = call(data, "selectedFireModeInfo");
            for (Object mode : list(get.invoke(data, gunPropClass.getField("AVAILABLE_FIRE_MODES").get(null)))) {
                if (call(mode, "getOverride") != null || !Set.of("Semi", "Auto", "Burst").contains(field(mode, "name"))
                        || field(mode, "mode") == null) throw invalid("Special SBW fire-mode overrides are held out.");
            }
            JsonObject fingerprint = new JsonObject();
            fingerprint.addProperty("fingerprintSchema", "sbw42_native_semantics_v2");
            fingerprint.addProperty("provider", "superbwarfare"); fingerprint.addProperty("version", VERSION);
            fingerprint.addProperty("qualification", RESOURCE_SHA); fingerprint.addProperty("referenceGun", gunId);
            fingerprint.addProperty("gunClass", registeredGun.getClass().getName());
            fingerprint.addProperty("raw", rawBefore); fingerprint.add("effective", computed);
            fingerprint.addProperty("configuration", configBefore);
            fingerprint.addProperty("modeName", (String) field(selectedMode, "name"));
            fingerprint.addProperty("mode", ((Enum<?>) field(selectedMode, "mode")).name());
            fingerprint.addProperty("ammoConsumer", mapping.token()); fingerprint.addProperty("ammoItem", mapping.itemId());
            fingerprint.addProperty("ammoClass", ammoItem.getClass().getName());
            fingerprint.addProperty("ammoLimit", ((Number) call(ammoType, "getLimit")).intValue());
            fingerprint.addProperty("ammoBoxLimit", ((Number) call(ammoType, "getAmmoBoxLimit")).intValue());
            if (gunOffer) fingerprint.addProperty("nativeUUID", actualIdentity.toString());

            ItemStack delivery = gunOffer ? gun : (ItemStack) ammoType.getClass().getMethod("getItemStack", int.class)
                    .invoke(ammoType, offer.count());
            call(data, "save");
            if (!gunOffer && (delivery.getItem() != ammoItem || delivery.hasTag()))
                throw invalid("The native SBW ammunition stack has unexpected data.");
            if (delivery.isEmpty() || delivery.getCount() != offer.count() || delivery.getCount() > delivery.getMaxStackSize())
                throw invalid("The native SBW stack count is invalid.");
            CompoundTag saved = delivery.save(new CompoundTag());
            ItemStack readback = ItemStack.of(saved.copy());
            if (!readback.save(new CompoundTag()).equals(saved) || readback.getItem() != delivery.getItem()
                    || readback.getCount() != offer.count()) throw invalid("The native SBW stack did not save exactly.");
            if (gunOffer) {
                Object reread = gunDataClass.getMethod("from", ItemStack.class).invoke(null, readback);
                emptyGun(reread, readback);
                if (!actualIdentity.equals(nativeIdentity(readback)) || !Boolean.TRUE.equals(call(reread, "initialized")))
                    throw invalid("The saved native SBW gun identity changed.");
            } else validateAmmoItem(readback.getItem(), ammoType);
            // SBW replaces this map during datapack prepare. Reject a changed
            // object, a changed object in place, or a changed global config.
            if (!live.containsKey(gunId) || live.get(gunId) != raw
                    || !rawBefore.equals(rawSnapshot(raw, gson))
                    || !configBefore.equals(configuration(gson)))
                throw invalid("SBW data changed during supply validation.");
            return new Built(readback, NativeWeapon42.sha(NativeWeapon42.canonical(fingerprint).getBytes(StandardCharsets.UTF_8)));
        } catch (IOException failure) { throw failure; }
        catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            Throwable cause = failure instanceof InvocationTargetException reflected ? reflected.getCause() : failure;
            throw new IOException("The current SBW data cannot safely supply this ordinary item.", cause);
        }
    }

    private static Object decodeReference(String json, Class<?> defaultClass) throws ReflectiveOperationException {
        Object serializer = call(defaultClass.getField("Companion").get(null), "serializer");
        Object loader = type("data.DataLoader").getField("INSTANCE").get(null);
        Object nativeJson = call(loader, "getJSON");
        return Class.forName("kotlinx.serialization.json.Json").getMethod("decodeFromString",
                Class.forName("kotlinx.serialization.DeserializationStrategy"), String.class).invoke(nativeJson, serializer, json);
    }

    private static String rawSnapshot(Object raw, Gson gson) throws ReflectiveOperationException, IOException {
        JsonObject snapshot = gson.toJsonTree(raw).getAsJsonObject();
        // Native reloadTypes is a Set. Its iteration order is not a weapon
        // setting, unlike fire-mode/consumer lists whose order selects defaults.
        Object reloadTypes = call(raw, "getReloadTypes");
        if (!(reloadTypes instanceof Set<?>)) throw invalid("Unknown SBW reload-type representation.");
        snapshot.add("ReloadTypes", json(reloadTypes, gson));
        return NativeWeapon42.canonical(snapshot);
    }

    private static void emptyGun(Object data, ItemStack stack) throws ReflectiveOperationException, IOException {
        for (String key : List.of("ammo", "virtualAmmo", "backupAmmoCount", "selectedAmmoType", "level", "exp", "heat"))
            requireNumber(call(field(data, key), "get"), 0);
        if (!((CompoundTag) field(data, "perkTag")).isEmpty() || !((CompoundTag) field(data, "attachmentTag")).isEmpty()
                || !((CompoundTag) field(data, "gunDataTag")).getCompound("AmmoSlot").isEmpty()
                || !"".equals(call(field(data, "propertyOverrideString"), "get")) || stack.getDamageValue() != 0)
            throw invalid("The supplied SBW gun is not an empty ordinary gun.");
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.getAllKeys().equals(Set.of("GunData", "Perks", "Attachments"))
                || !tag.getCompound("GunData").getAllKeys().equals(Set.of("UUID")))
            throw invalid("The native SBW gun has unexpected initial state.");
    }

    private static void validateConsumer(Object consumer, AmmoMapping mapping) throws ReflectiveOperationException, IOException {
        if (!mapping.token().equals(call(consumer, "getAmmo")) || !"Default".equals(call(consumer, "getAmmoSlot"))
                || !"PLAYER_AMMO".equals(((Enum<?>) call(consumer, "getType")).name())
                || !mapping.enumName().equals(((Enum<?>) call(consumer, "getPlayerAmmoType")).name())
                || call(consumer, "getOverride") != null || call(consumer, "getProjectile") != null
                || !Boolean.TRUE.equals(call(consumer, "initialized"))
                || !call(consumer, "getStrategy").getClass().getName().equals(BASE + "data.gun.ammo_consumer_strategy.PlayerAmmoStrategy"))
            throw invalid("The SBW ammunition consumer is not the qualified ordinary feed.");
        requireNumber(call(consumer, "getLoadAmount"), 1);
        ItemStack stack = (ItemStack) call(consumer, "stack");
        if (stack.getItem() != registered(mapping.itemId()) || stack.getCount() != 1 || stack.hasTag())
            throw invalid("The SBW consumer maps to unexpected ammunition.");
    }

    private static void validateAmmoItem(Item item, Object ammoType) throws ReflectiveOperationException, IOException {
        if (item.getClass() != type("item.ammo.AmmoSupplierItem") || call(item, "getType") != ammoType)
            throw invalid("The SBW ammunition supplier type changed.");
        requireNumber(call(item, "getAmmoToAdd"), 1);
    }

    private static UUID nativeIdentity(ItemStack stack) throws IOException {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.getCompound("GunData").hasUUID("UUID")) throw invalid("The native SBW UUID is missing.");
        UUID value = tag.getCompound("GunData").getUUID("UUID");
        if (value.equals(new UUID(0, 0))) throw invalid("The native SBW UUID is invalid.");
        return value;
    }

    private static AmmoMapping mapping(String referenceAmmo) throws IOException {
        return switch (referenceAmmo) {
            case "\"@HandgunAmmo\"" -> new AmmoMapping("@HandgunAmmo", "HANDGUN", "superbwarfare:handgun_ammo");
            case "\"@RifleAmmo\"" -> new AmmoMapping("@RifleAmmo", "RIFLE", "superbwarfare:rifle_ammo");
            case "\"@ShotgunAmmo\"" -> new AmmoMapping("@ShotgunAmmo", "SHOTGUN", "superbwarfare:shotgun_ammo");
            case "\"@SniperAmmo\"" -> new AmmoMapping("@SniperAmmo", "SNIPER", "superbwarfare:sniper_ammo");
            default -> throw invalid("Special SBW ammunition is held out.");
        };
    }

    private static Map<String, Reference> references() throws IOException {
        Map<String, Reference> value = references;
        if (value != null) return value;
        synchronized (NativeSbw42.class) {
            if (references == null) {
                JsonObject root = NativeWeapon42.reference(RESOURCE, RESOURCE_SHA, 128 * 1024);
                if (root.get("schema").getAsInt() != 1 || !VERSION.equals(root.get("version").getAsString())
                        || !"superbwarfare".equals(root.get("provider").getAsString())) throw invalid("Unknown SBW reference schema.");
                JsonObject guns = root.getAsJsonObject("guns"), classes = root.getAsJsonObject("gunClasses");
                if (!guns.keySet().equals(classes.keySet())) throw invalid("Incomplete SBW native class qualification.");
                Map<String, Reference> found = new TreeMap<>();
                for (var entry : guns.entrySet()) found.put(entry.getKey(),
                        new Reference(entry.getValue().toString(), classes.get(entry.getKey()).getAsString()));
                if (found.size() != 24) throw invalid("Incomplete ordinary SBW qualification.");
                references = Map.copyOf(found);
            }
            return references;
        }
    }

    private static String configuration(Gson gson) throws ReflectiveOperationException, IOException {
        JsonObject result = new JsonObject();
        for (String kind : List.of("Server", "Common")) {
            Object spec = type("config." + kind + "ConfigKt").getMethod("get" + kind.toUpperCase(Locale.ROOT) + "_CONFIG").invoke(null);
            Object values = spec.getClass().getMethod("getValues").invoke(spec);
            result.add(kind, configValue(values, gson));
        }
        return NativeWeapon42.canonical(result);
    }

    private static JsonElement configValue(Object value, Gson gson) throws ReflectiveOperationException, IOException {
        if (value instanceof ForgeConfigSpec.ConfigValue<?> config) return json(config.get(), gson);
        Class<?> configClass = Class.forName("com.electronwill.nightconfig.core.UnmodifiableConfig");
        if (configClass.isInstance(value)) value = configClass.getMethod("valueMap").invoke(value);
        if (value instanceof Map<?, ?> map) {
            JsonObject result = new JsonObject();
            for (var entry : map.entrySet()) result.add((String) entry.getKey(), configValue(entry.getValue(), gson));
            return result;
        }
        return json(value, gson);
    }

    private static JsonElement perks(Object value) throws ReflectiveOperationException, IOException {
        JsonArray result = new JsonArray();
        for (Object perk : list(value)) {
            JsonObject entry = new JsonObject();
            entry.addProperty("item", call(call(perk, "getItem"), "getId").toString());
            entry.addProperty("class", perk.getClass().getName());
            entry.addProperty("description", (String) call(perk, "getDescriptionId"));
            entry.addProperty("type", ((Enum<?>) call(perk, "getType")).name());
            result.add(entry);
        }
        // SBW discovers perk JSONs with Files.list and expands @Ammo from a
        // registry entry set. That incidental order can differ in a new JVM.
        // No perks are active on these supplied guns; retain every available
        // identity and duplicate count, but canonicalize this membership list.
        // Authored AvailablePerks order remains covered by the full raw policy.
        return unordered(result);
    }

    private static JsonElement json(Object value, Gson gson) throws ReflectiveOperationException, IOException {
        if (value == null) return JsonNull.INSTANCE;
        if (value instanceof JsonElement element) return element.deepCopy();
        if (value instanceof Collection<?> list) {
            JsonArray result = new JsonArray(); for (Object item : list) result.add(json(item, gson));
            return value instanceof Set<?> ? unordered(result) : result;
        }
        if (value instanceof Number number && !Double.isFinite(number.doubleValue())) throw invalid("Non-finite SBW data.");
        if (value.getClass().getName().equals(BASE + "data.StringToObject")) return json(field(value, "value"), gson);
        return gson.toJsonTree(value);
    }

    private static JsonArray unordered(JsonArray values) throws IOException {
        List<Map.Entry<String, JsonElement>> sorted = new ArrayList<>();
        for (JsonElement value : values) sorted.add(Map.entry(NativeWeapon42.canonical(value), value));
        sorted.sort(Map.Entry.comparingByKey());
        JsonArray result = new JsonArray();
        for (var entry : sorted) result.add(entry.getValue());
        return result;
    }

    private static Item registered(String id) throws IOException {
        ResourceLocation key = new ResourceLocation(id);
        if (!ForgeRegistries.ITEMS.containsKey(key)) throw invalid("The SBW item is not registered.");
        Item item = ForgeRegistries.ITEMS.getValue(key);
        if (item == null || !key.equals(ForgeRegistries.ITEMS.getKey(item))) throw invalid("The SBW registry identity changed.");
        return item;
    }
    private static Class<?> type(String suffix) throws ClassNotFoundException { return Class.forName(BASE + suffix); }
    private static Object call(Object object, String name) throws ReflectiveOperationException { return object.getClass().getMethod(name).invoke(object); }
    private static Object field(Object object, String name) throws ReflectiveOperationException { return object.getClass().getField(name).get(object); }
    private static List<?> list(Object value) throws IOException { if (value instanceof List<?> list) return list; throw invalid("Invalid SBW list."); }
    private static void requireNumber(Object value, double expected) throws IOException {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue()) || number.doubleValue() != expected)
            throw invalid("Unqualified ordinary SBW property.");
    }
    private static IOException invalid(String message) { return new IOException(message); }
}
