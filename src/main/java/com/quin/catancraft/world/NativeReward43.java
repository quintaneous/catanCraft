package com.quin.catancraft.world;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Frozen 043 reward identity schema plus the current TaCZ-only delivery policy.
 * Historical SBW entries remain readable metadata and never authorize delivery. */
public final class NativeReward43 {
    public static final String REVISION = "monument_supply43_native_v1";
    record Rare(String id, String title, String category, String ammoId, String fireMode) {}
    private static final List<Rare> RARES = List.of(
            new Rare("tacz:scar_h", "TaCZ SCAR-H", "rifle", "tacz:308", "SEMI"),
            new Rare("tacz:scar_mk20", "TaCZ SCAR Mk20", "dmr", "tacz:308", "SEMI"),
            new Rare("tacz:deagle_golden", "TaCZ Golden Desert Eagle", "pistol", "tacz:357mag", "SEMI"));
    private record Qualified(RewardTable41.Entitlement entitlement, String description,
                             WeaponStock42.Offer ordinary, Rare rare) {}
    private record Catalog(List<RewardTable41.Entitlement> entitlements, Map<String, Qualified> entries) {}
    private static volatile Catalog cached;

    private NativeReward43() {}

    /** Historical schema only; new production pools must use activeEntitlements(). */
    public static List<RewardTable41.Entitlement> entitlements() throws IOException { return catalog().entitlements(); }

    /** Returns a canonical frozen schema amount/revision, never current delivery authority. */
    public static RewardTable41.Entitlement entitlement(RewardTable41.Kind kind, String nativeId) throws IOException {
        Qualified entry = catalog().entries().get(key(kind, nativeId));
        if (entry == null) throw new IOException("This equipment is not in the monument supply pool.");
        return entry.entitlement();
    }

    /** Historical schema validation, not authorization to deliver or activate a new pool. */
    public static void validate(RewardTable41.Entitlement entitlement) throws IOException { qualified(entitlement); }

    public static List<RewardTable41.Entitlement> activeEntitlements() throws IOException {
        List<RewardTable41.Entitlement> result = entitlements().stream()
                .filter(entitlement -> entitlement.objectId().startsWith("tacz:")).toList();
        if (result.size() != 72) throw new IOException("The audited TaCZ-only reward coverage changed.");
        return result;
    }

    public static RewardTable41.Entitlement activeEntitlement(RewardTable41.Kind kind, String nativeId) throws IOException {
        RewardTable41.Entitlement result = entitlement(kind, nativeId);
        validateActive(result);
        return result;
    }

    public static void validateActive(RewardTable41.Entitlement entitlement) throws IOException {
        qualified(entitlement);
        if (!entitlement.objectId().startsWith("tacz:"))
            throw new IOException("This saved reward is retained, but Superb Warfare handheld weapons and ammunition are disabled.");
    }

    public static String description(RewardTable41.Entitlement entitlement) throws IOException {
        validateActive(entitlement);
        Qualified entry = qualified(entitlement);
        return entry.description() + " x" + entitlement.count();
    }

    static NativeWeapon42.Supply quote(MinecraftServer server, RewardTable41.Entitlement entitlement) throws IOException {
        validateActive(entitlement);
        long epoch = NativeWeapon42.qualificationEpoch(server);
        Qualified entry = qualified(entitlement);
        NativeWeapon42.Supply supply = entry.ordinary() != null
                ? NativeWeapon42.quote(server, entry.ordinary()) : NativeTacz42.quoteReward(server, entitlement);
        item(entitlement, supply);
        NativeWeapon42.requireEpoch(server, epoch);
        return supply;
    }

    static void recheck(MinecraftServer server, RewardTable41.Entitlement entitlement,
                        NativeWeapon42.Supply supply) throws IOException {
        validateActive(entitlement);
        long epoch = NativeWeapon42.qualificationEpoch(server);
        Qualified entry = qualified(entitlement);
        item(entitlement, supply);
        // Recheck the original item; disabled historical SBW entitlements cannot reach native delivery.
        if (entry.ordinary() != null) NativeWeapon42.recheck(server, entry.ordinary(), supply);
        else NativeTacz42.recheckReward(server, entitlement, supply);
        NativeWeapon42.requireEpoch(server, epoch);
    }

    static List<Rare> rares() { return RARES; }

    static Rare requireRare(RewardTable41.Entitlement entitlement) throws IOException {
        Qualified entry = qualified(entitlement);
        if (entry.rare() == null) throw new IOException("This is not an authorized rare monument reward.");
        return entry.rare();
    }

    private static void item(RewardTable41.Entitlement entitlement, NativeWeapon42.Supply supply) throws IOException {
        if (supply == null) throw new IOException("The frozen native reward item is missing.");
        ItemStack stack = supply.item();
        CompoundTag saved = stack.save(new CompoundTag());
        if (stack.isEmpty() || stack.getCount() != entitlement.count() || stack.getCount() > stack.getMaxStackSize()
                || !saved.equals(ItemStack.of(saved.copy()).save(new CompoundTag())))
            throw new IOException("The native reward did not retain its exact amount and identity.");
    }

    private static Qualified qualified(RewardTable41.Entitlement entitlement) throws IOException {
        if (entitlement == null || !REVISION.equals(entitlement.revision()))
            throw new IOException("This historical or unknown equipment revision has no native claim implementation.");
        Qualified entry = catalog().entries().get(key(entitlement.kind(), entitlement.objectId()));
        if (entry == null || !entry.entitlement().equals(entitlement))
            throw new IOException("The exact reward kind, identity or amount is not authorized.");
        return entry;
    }

    private static Catalog catalog() throws IOException {
        Catalog value = cached;
        if (value != null) return value;
        synchronized (NativeReward43.class) {
            if (cached == null) {
                try { cached = build(); }
                catch (RuntimeException failure) { throw new IOException("Invalid native reward catalog.", failure); }
            }
            return cached;
        }
    }

    private static Catalog build() throws IOException {
        Map<String, Qualified> entries = new LinkedHashMap<>();
        List<WeaponStock42.Offer> stock = WeaponStock42.offers();
        if (stock.size() != 97) throw new IOException("The audited ordinary reward stock changed.");
        for (WeaponStock42.Offer offer : stock) {
            WeaponStock42.requireCurrent(offer);
            RewardTable41.Kind kind = offer.kind() == WeaponShopCatalog40.Kind.GUN ? RewardTable41.Kind.GUN : RewardTable41.Kind.AMMO;
            RewardTable41.Entitlement entitlement = new RewardTable41.Entitlement(kind, offer.nativeId(), offer.count(), REVISION);
            add(entries, new Qualified(entitlement, offer.title(), offer, null));
        }
        JsonObject reference = NativeWeapon42.reference("/data/catancraft/economy/tacz_native42.json",
                NativeTacz42.REFERENCE_SHA256, 512 * 1024);
        for (Rare rare : RARES) {
            WeaponTierCatalog41.Entry tier = WeaponTierCatalog41.entry(rare.id());
            if (!tier.provider().equals("tacz") || tier.disposition() != WeaponTierCatalog41.Disposition.DROP
                    || tier.requiresSpecialQualification() || !tier.category().equals(rare.category())
                    || !tier.dataId().equals(rare.id() + "_data")
                    || !tier.ammoJson().equals('"' + rare.ammoId() + '"')
                    || !"tacz:xmag_reload_logic".equals(tier.script()))
                throw new IOException("The rare monument reward no longer matches its audited policy.");
            JsonObject data = reference.getAsJsonObject("guns").getAsJsonObject(rare.id()).getAsJsonObject("data");
            if (!data.getAsJsonObject("reload").get("type").getAsString().equals("magazine")
                    || data.has("charging") || data.has("heat") || data.has("fire_mode_adjust")
                    || data.getAsJsonObject("bullet").getAsJsonObject("explosion").get("explode").getAsBoolean()
                    || data.getAsJsonObject("bullet").get("ignite").getAsBoolean()
                    || !data.getAsJsonArray("fire_mode").get(0).getAsString().equals("semi"))
                throw new IOException("The rare monument reward requires an unsupported native behavior contract.");
            RewardTable41.Entitlement entitlement = new RewardTable41.Entitlement(RewardTable41.Kind.GUN, rare.id(), 1, REVISION);
            add(entries, new Qualified(entitlement, rare.title(), null, rare));
            if (!entries.containsKey(key(RewardTable41.Kind.AMMO, rare.ammoId())))
                throw new IOException("The rare gun has no qualified ordinary ammunition supply.");
        }
        if (entries.size() != 100) throw new IOException("Monument equipment coverage changed.");
        List<RewardTable41.Entitlement> entitlements = new ArrayList<>();
        entries.values().forEach(entry -> entitlements.add(entry.entitlement()));
        return new Catalog(List.copyOf(entitlements), Map.copyOf(entries));
    }

    private static String key(RewardTable41.Kind kind, String nativeId) { return kind + ":" + nativeId; }

    private static void add(Map<String, Qualified> entries, Qualified entry) throws IOException {
        if (entries.putIfAbsent(key(entry.entitlement().kind(), entry.entitlement().objectId()), entry) != null)
            throw new IOException("Duplicate native monument equipment identity.");
    }
}
