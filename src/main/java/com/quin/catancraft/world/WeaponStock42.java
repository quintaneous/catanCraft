package com.quin.catancraft.world;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.quin.catancraft.data.ResourceType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Explicit provisional playtest stock. The immutable 041 reference is never a price fallback. */
final class WeaponStock42 {
    static final String REVISION = "physical_weapon_stock42_provisional_playtest_v1";
    private static final int MAX_PAGE_SIZE = 64;
    private static final Set<String> LEGACY_GUNS = Set.of("tacz:glock_17", "tacz:ak47");
    private static final Set<String> LEGACY_AMMO = Set.of("tacz:9mm", "tacz:762x39");

    record Offer(String id, String title, WeaponShopCatalog40.Kind kind, String provider,
                 String nativeId, String referenceGun, int count, int requiredLevel,
                 String fireMode, ShopPurchase40.Price price, String revision) {
        String kindName() { return kind == WeaponShopCatalog40.Kind.GUN ? "Gun" : "Ammo"; }
        String description() { return title + " x" + count + " — " + price.describe(); }
    }

    private record Catalog(List<Offer> offers, Map<String, Offer> byId) {}
    private record AmmoSpec(String title, ShopPurchase40.Price price) {}
    private record Materials(long wood, long steel, long mechanicalParts, long electronics) {}

    // All five cells are explicit, bounded playtest prices; exact policy gates select the cell.
    private static final Map<String, List<Long>> GUN_MONEY = Map.of(
            "pistol", List.of(650L, 900L, 1250L, 1700L, 2300L),
            "smg", List.of(1100L, 1500L, 2100L, 2900L, 3900L),
            "shotgun", List.of(900L, 1300L, 1900L, 2700L, 3700L),
            "rifle", List.of(1250L, 1800L, 2500L, 3400L, 4600L),
            "dmr", List.of(1200L, 1700L, 2400L, 3300L, 4500L),
            "sniper", List.of(1400L, 2100L, 3100L, 4500L, 6500L),
            "mg", List.of(1800L, 2600L, 3600L, 4900L, 6500L));
    private static final List<Materials> GUN_MATERIALS = List.of(
            new Materials(8, 6, 0, 0), new Materials(8, 10, 0, 0),
            new Materials(6, 16, 3, 0), new Materials(4, 24, 6, 2),
            new Materials(0, 34, 10, 4));
    private static final Map<String, Long> CATEGORY_STEEL = Map.of(
            "pistol", 0L, "smg", 3L, "shotgun", 2L, "rifle", 4L,
            "dmr", 4L, "sniper", 6L, "mg", 10L);

    // Published SBW 0.8.9.2 AmmoSupplierItem(type, 1, Properties()) registrations.
    // These are registry item IDs, never the @AmmoType tokens from gun data.
    private static final Map<String, String> SBW_AMMO = Map.of(
            "@HandgunAmmo", "superbwarfare:handgun_ammo",
            "@RifleAmmo", "superbwarfare:rifle_ammo",
            "@ShotgunAmmo", "superbwarfare:shotgun_ammo",
            "@SniperAmmo", "superbwarfare:sniper_ammo");
    private static final Map<String, AmmoSpec> AMMO = Map.ofEntries(
            Map.entry("tacz:12g", ammo("TaCZ 12 gauge ammunition", 220, 2, 2)),
            Map.entry("tacz:22wmr", ammo("TaCZ .22 WMR ammunition", 110, 1, 1)),
            Map.entry("tacz:45acp", ammo("TaCZ .45 ACP ammunition", 150, 1, 1)),
            Map.entry("tacz:357mag", ammo("TaCZ .357 Magnum ammunition", 200, 2, 1)),
            Map.entry("tacz:50ae", ammo("TaCZ .50 AE ammunition", 280, 3, 2)),
            Map.entry("tacz:46x30", ammo("TaCZ 4.6x30 ammunition", 320, 3, 2)),
            Map.entry("tacz:545x39", ammo("TaCZ 5.45x39 ammunition", 200, 2, 1)),
            Map.entry("tacz:556x45", ammo("TaCZ 5.56x45 ammunition", 210, 2, 1)),
            Map.entry("tacz:58x42", ammo("TaCZ 5.8x42 ammunition", 240, 3, 1)),
            Map.entry("tacz:308", ammo("TaCZ .308 ammunition", 280, 3, 2)),
            Map.entry("tacz:792x57", ammo("TaCZ 7.92x57 ammunition", 260, 3, 2)),
            Map.entry("tacz:30_06", ammo("TaCZ .30-06 ammunition", 280, 3, 2)),
            Map.entry("tacz:762x54", ammo("TaCZ 7.62x54 ammunition", 300, 3, 2)),
            Map.entry("tacz:338", ammo("TaCZ .338 ammunition", 450, 5, 3)),
            Map.entry("tacz:45_70", ammo("TaCZ .45-70 ammunition", 240, 3, 2)),
            Map.entry("superbwarfare:handgun_ammo", ammo("SBW handgun ammunition", 150, 1, 1)),
            Map.entry("superbwarfare:rifle_ammo", ammo("SBW rifle ammunition", 210, 2, 1)),
            Map.entry("superbwarfare:shotgun_ammo", ammo("SBW shotgun ammunition", 220, 2, 2)),
            Map.entry("superbwarfare:sniper_ammo", ammo("SBW sniper ammunition", 300, 3, 2)));
    private static volatile Catalog cached;

    private WeaponStock42() {}

    static List<Offer> offers() throws IOException { return catalog().offers(); }

    static Offer offer(String id) throws IOException {
        if (id == null) throw invalid("Missing weapon offer identity.");
        Offer result = catalog().byId().get(id);
        if (result == null) throw invalid("This item is not offered at the weapon counter.");
        return result;
    }

    /** Reject every altered identity, alias, count, gate, price or revision before native delivery. */
    static void requireCurrent(Offer offer) throws IOException {
        if (offer == null || offer.id() == null || !offer.equals(catalog().byId().get(offer.id())))
            throw invalid("The weapon offer has changed; request a new quote.");
    }

    /** Zero-based bounded slicing only; the physical counter owns access and navigation checks. */
    static List<Offer> page(int page, int pageSize) throws IOException {
        if (page < 0 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) throw invalid("Invalid weapon stock page.");
        List<Offer> all = offers();
        long start = (long) page * pageSize;
        if (start >= all.size()) throw invalid("Weapon stock page is out of range.");
        return List.copyOf(all.subList((int) start, (int) Math.min(start + pageSize, all.size())));
    }

    private static Catalog catalog() throws IOException {
        Catalog value = cached;
        if (value != null) return value;
        synchronized (WeaponStock42.class) {
            if (cached == null) {
                try { cached = build(); }
                catch (IOException failure) { throw failure; }
                catch (RuntimeException failure) { throw new IOException("Invalid provisional weapon stock.", failure); }
            }
            return cached;
        }
    }

    private static Catalog build() throws IOException {
        List<WeaponTierCatalog41.Entry> ordinary = WeaponTierCatalog41.entries().stream()
                .filter(e -> e.disposition() == WeaponTierCatalog41.Disposition.STARTER
                        || e.disposition() == WeaponTierCatalog41.Disposition.SHOP)
                .filter(e -> !e.requiresSpecialQualification()).toList();
        if (ordinary.size() != 76 || ordinary.stream().filter(e -> e.provider().equals("tacz")).count() != 52
                || ordinary.stream().filter(e -> e.provider().equals("superbwarfare")).count() != 24)
            throw invalid("Ordinary weapon coverage changed; stock requires a new audited revision.");

        List<Offer> offers = new ArrayList<>();
        for (WeaponShopCatalog40.Offer old : WeaponShopCatalog40.offers()) {
            WeaponTierCatalog41.requireExistingOffer(old.nativeId(), old.requiredLevel());
            String reference = old.kind() == WeaponShopCatalog40.Kind.GUN ? old.nativeId()
                    : old.nativeId().equals("tacz:9mm") ? "tacz:glock_17" : "tacz:ak47";
            offers.add(new Offer(old.id(), old.title(), old.kind(), "tacz", old.nativeId(), reference,
                    old.count(), old.requiredLevel(), old.fireMode(), old.price(), WeaponShopCatalog40.REVISION));
        }

        Map<String, WeaponTierCatalog41.Entry> ammoOwners = new LinkedHashMap<>();
        for (WeaponTierCatalog41.Entry gun : ordinary) {
            int level = level(gun);
            if (LEGACY_GUNS.contains(gun.id())) WeaponTierCatalog41.requireExistingOffer(gun.id(), level);
            else {
                WeaponTierCatalog41.requireShop(gun.id(), level, WeaponTierCatalog41.CANDIDATE_REVISION);
                offers.add(new Offer(sku(gun.provider(), "gun", gun.id()), title(gun), WeaponShopCatalog40.Kind.GUN,
                        gun.provider(), gun.id(), gun.id(), 1, level, fireMode(gun), gunPrice(gun), REVISION));
            }
            String ammo = ammoId(gun);
            WeaponTierCatalog41.Entry prior = ammoOwners.get(ammo);
            if (prior == null || level < level(prior) || level == level(prior) && gun.id().compareTo(prior.id()) < 0)
                ammoOwners.put(ammo, gun);
        }
        if (ammoOwners.size() != 21 || !ammoOwners.keySet().containsAll(LEGACY_AMMO))
            throw invalid("Compatible ammunition coverage changed.");
        Set<String> pricedAmmo = new HashSet<>(ammoOwners.keySet());
        pricedAmmo.removeAll(LEGACY_AMMO);
        if (!pricedAmmo.equals(AMMO.keySet())) throw invalid("A compatible ammunition offer has no explicit price.");
        for (Map.Entry<String, WeaponTierCatalog41.Entry> mapping : ammoOwners.entrySet()) {
            if (LEGACY_AMMO.contains(mapping.getKey())) continue;
            WeaponTierCatalog41.Entry gun = mapping.getValue();
            AmmoSpec spec = AMMO.get(mapping.getKey());
            WeaponTierCatalog41.requireShop(gun.id(), level(gun), WeaponTierCatalog41.CANDIDATE_REVISION);
            offers.add(new Offer(sku(gun.provider(), "ammo", mapping.getKey()), spec.title(), WeaponShopCatalog40.Kind.AMMO,
                    gun.provider(), mapping.getKey(), gun.id(), 32, level(gun), "", spec.price(), REVISION));
        }
        if (offers.size() != 97) throw invalid("Provisional weapon stock is incomplete.");
        offers.sort(Comparator.comparingInt(Offer::requiredLevel).thenComparing(Offer::kind)
                .thenComparing(Offer::provider).thenComparing(Offer::id));
        Map<String, Offer> byId = new LinkedHashMap<>();
        Set<String> nativeKinds = new HashSet<>();
        for (Offer offer : offers) {
            if (byId.putIfAbsent(offer.id(), offer) != null
                    || !nativeKinds.add(offer.kind() + ":" + offer.nativeId()))
                throw invalid("Duplicate or aliased native weapon offer.");
        }
        return new Catalog(List.copyOf(offers), Map.copyOf(byId));
    }

    private static int level(WeaponTierCatalog41.Entry gun) {
        // This one grandfathered gun retains its existing any-factory gate; the pinned L2 proposal is unchanged.
        return gun.id().equals("tacz:ak47") ? 1 : gun.proposedLevel();
    }

    private static String ammoId(WeaponTierCatalog41.Entry gun) throws IOException {
        JsonElement value = JsonParser.parseString(gun.ammoJson());
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw invalid("Ordinary stock requires a single exact ammunition identity.");
        String ammo = value.getAsString();
        if (gun.provider().equals("tacz")) {
            if (!ammo.startsWith("tacz:")) throw invalid("Ammunition provider mismatch.");
            return ammo;
        }
        String nativeId = SBW_AMMO.get(ammo);
        if (nativeId == null) throw invalid("Unqualified SBW ammunition family.");
        return nativeId;
    }

    private static String fireMode(WeaponTierCatalog41.Entry gun) throws IOException {
        if (gun.provider().equals("superbwarfare")) {
            // Empty means retain/verify the effective native initialized default, never force a reference mode.
            // k_98 declares Auto but omits AvailableFireModes, whose native default is [Semi].
            return "";
        }
        JsonElement modes = JsonParser.parseString(gun.fireModesJson());
        if (!modes.isJsonArray() || modes.getAsJsonArray().isEmpty()) throw invalid("Missing TaCZ firing mode.");
        return modes.getAsJsonArray().get(0).getAsString().toUpperCase(Locale.ROOT);
    }

    private static ShopPurchase40.Price gunPrice(WeaponTierCatalog41.Entry gun) throws IOException {
        String category = switch (gun.category()) {
            case "pistol", "Handgun" -> "pistol";
            case "smg", "Smg" -> "smg";
            case "shotgun", "Shotgun" -> "shotgun";
            case "rifle", "Rifle" -> "rifle";
            case "dmr" -> "dmr";
            case "sniper", "Sniper" -> "sniper";
            case "mg", "MachineGun" -> "mg";
            default -> throw invalid("This gun category has no provisional price.");
        };
        int level = gun.proposedLevel();
        if (level < 1 || level > 5) throw invalid("This gun tier has no provisional price.");
        Materials materials = GUN_MATERIALS.get(level - 1);
        Map<ResourceType, Long> resources = new EnumMap<>(ResourceType.class);
        add(resources, ResourceType.WOOD, materials.wood());
        add(resources, ResourceType.STEEL, materials.steel() + CATEGORY_STEEL.get(category));
        add(resources, ResourceType.MECHANICAL_PARTS, materials.mechanicalParts());
        add(resources, ResourceType.ELECTRONICS, materials.electronics());
        return new ShopPurchase40.Price(GUN_MONEY.get(category).get(level - 1), resources);
    }

    private static void add(Map<ResourceType, Long> resources, ResourceType resource, long count) {
        if (count > 0) resources.put(resource, count);
    }

    private static AmmoSpec ammo(String title, long money, long steel, long explosives) {
        return new AmmoSpec(title, new ShopPurchase40.Price(money,
                Map.of(ResourceType.STEEL, steel, ResourceType.EXPLOSIVES, explosives)));
    }

    private static String sku(String provider, String kind, String nativeId) {
        return provider + "_" + kind + "_" + nativeId.substring(nativeId.indexOf(':') + 1);
    }

    private static String title(WeaponTierCatalog41.Entry gun) {
        return (gun.provider().equals("tacz") ? "TaCZ " : "SBW ")
                + gun.id().substring(gun.id().indexOf(':') + 1).replace('_', ' ').toUpperCase(Locale.ROOT);
    }

    private static IOException invalid(String message) { return new IOException(message); }
}
