package com.quin.catancraft.world;

import com.google.gson.JsonParser;
import com.quin.catancraft.data.MonumentType;
import com.quin.catancraft.data.ResourceType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

/** TaCZ-only provisional pools for new activations: 100,000 tickets each.
 * Frozen 043 rounds are never rerolled or rewritten by this catalog change. */
public final class MonumentSupply44 {
    public static final String REVISION = "monument_supply44_tacz_only_provisional_v1";
    private static final int TOTAL = 100_000;
    private static volatile Map<MonumentType, RewardTable41> cached;

    private MonumentSupply44() {}

    public static RewardTable41 table(MonumentType type) throws IOException {
        if (type == null) throw new IOException("A monument type is required for its supply table.");
        Map<MonumentType, RewardTable41> value = cached;
        if (value == null) synchronized (MonumentSupply44.class) {
            if (cached == null) cached = build();
            value = cached;
        }
        return value.get(type);
    }

    /** Validation is exact even when no native provider is currently loaded. */
    public static void validate(RewardTable41.Entitlement entitlement) throws IOException { NativeReward43.validateActive(entitlement); }

    private static Map<MonumentType, RewardTable41> build() throws IOException {
        List<WeaponStock42.Offer> stock = ActiveWeaponStock44.offers();
        List<WeaponStock42.Offer> ammunition = stock.stream().filter(offer -> offer.kind() == WeaponShopCatalog40.Kind.AMMO)
                .sorted(Comparator.comparing(WeaponStock42.Offer::nativeId)).toList();
        if (stock.size() != 69 || ammunition.size() != 17) throw new IOException("The qualified reward stock changed.");
        Map<MonumentType, List<WeaponStock42.Offer>> guns = new EnumMap<>(MonumentType.class);
        for (MonumentType type : MonumentType.values()) guns.put(type, new ArrayList<>());
        for (WeaponStock42.Offer offer : stock) {
            if (offer.kind() == WeaponShopCatalog40.Kind.GUN)
                guns.get(home(WeaponTierCatalog41.entry(offer.nativeId()).category())).add(offer);
        }
        if (guns.get(MonumentType.INDUSTRIAL_COMPLEX).size() != 21
                || guns.get(MonumentType.MILITARY_DEPOT).size() != 15 || guns.get(MonumentType.REFINERY).size() != 16)
            throw new IOException("The monument gun role allocation changed.");
        Map<MonumentType, RewardTable41> tables = new EnumMap<>(MonumentType.class);
        Set<RewardTable41.Entitlement> covered = new HashSet<>();
        for (MonumentType type : MonumentType.values()) {
            List<RewardTable41.Entry> entries = nationalSupplies(type);
            boolean military = type == MonumentType.MILITARY_DEPOT;
            Map<String, Integer> ammoWeights = allocate(ammunition, military ? 30_000 : 20_000, offer -> 1);
            for (WeaponStock42.Offer ammo : ammunition) {
                var entitlement = NativeReward43.activeEntitlement(RewardTable41.Kind.AMMO, ammo.nativeId());
                entries.add(new RewardTable41.Entry("ammunition_" + ammo.id(), ammoWeights.get(ammo.id()),
                        new RewardTable41.Bundle(0, Map.of(), List.of(entitlement))));
            }
            List<WeaponStock42.Offer> ordinary = guns.get(type).stream()
                    .sorted(Comparator.comparing(WeaponStock42.Offer::nativeId)).toList();
            Map<String, Integer> gunWeights = allocate(ordinary, military ? 29_500 : 4_900, MonumentSupply44::tierWeight);
            for (WeaponStock42.Offer gun : ordinary) {
                var entitlement = NativeReward43.activeEntitlement(RewardTable41.Kind.GUN, gun.nativeId());
                var ammo = compatibleAmmo(gun.nativeId());
                entries.add(new RewardTable41.Entry("ordinary_" + gun.id(), gunWeights.get(gun.id()),
                        new RewardTable41.Bundle(0, Map.of(), List.of(entitlement, ammo))));
            }
            String rareId = switch (type) {
                case INDUSTRIAL_COMPLEX -> "tacz:scar_h";
                case MILITARY_DEPOT -> "tacz:scar_mk20";
                case REFINERY -> "tacz:deagle_golden";
            };
            entries.add(new RewardTable41.Entry("very_rare_" + rareId.substring(rareId.indexOf(':') + 1), military ? 500 : 100,
                    new RewardTable41.Bundle(0, Map.of(), List.of(
                            NativeReward43.activeEntitlement(RewardTable41.Kind.GUN, rareId), compatibleAmmo(rareId)))));
            RewardTable41 table = new RewardTable41(REVISION + "_" + type.id(), entries);
            if (table.totalWeight() != TOTAL || entries.size() != (type == MonumentType.INDUSTRIAL_COMPLEX ? 42 : type == MonumentType.MILITARY_DEPOT ? 36 : 37))
                throw new IOException("The provisional monument pool has an invalid ticket total or entry count.");
            for (RewardTable41.Entry entry : entries) for (var entitlement : entry.reward().entitlements()) {
                NativeReward43.validateActive(entitlement);
                covered.add(entitlement);
            }
            tables.put(type, table);
        }
        if (!covered.equals(Set.copyOf(NativeReward43.activeEntitlements())))
            throw new IOException("The production supply pools do not cover every qualified equipment identity.");
        return Map.copyOf(tables);
    }

    private static List<RewardTable41.Entry> nationalSupplies(MonumentType type) {
        List<RewardTable41.Entry> entries = new ArrayList<>();
        switch (type) {
            case INDUSTRIAL_COMPLEX -> {
                entries.add(national("treasury", 20_000, 4_000, Map.of()));
                entries.add(national("construction_materials", 35_000, 0,
                        Map.of(ResourceType.STEEL, 60L, ResourceType.CONCRETE, 80L)));
                entries.add(national("industrial_components", 20_000, 0,
                        Map.of(ResourceType.STEEL, 20L, ResourceType.MECHANICAL_PARTS, 30L, ResourceType.ELECTRONICS, 8L)));
            }
            case MILITARY_DEPOT -> {
                entries.add(national("treasury", 10_000, 5_000, Map.of()));
                entries.add(national("field_supplies", 20_000, 0,
                        Map.of(ResourceType.STEEL, 40L, ResourceType.FABRIC, 60L, ResourceType.EXPLOSIVES, 30L)));
                entries.add(national("military_components", 10_000, 0,
                        Map.of(ResourceType.STEEL, 20L, ResourceType.MECHANICAL_PARTS, 20L, ResourceType.ELECTRONICS, 10L)));
            }
            case REFINERY -> {
                entries.add(national("treasury", 15_000, 5_000, Map.of()));
                entries.add(national("fuel_supplies", 45_000, 0,
                        Map.of(ResourceType.OIL, 60L, ResourceType.FUEL, 100L)));
                entries.add(national("refinery_components", 15_000, 0,
                        Map.of(ResourceType.FUEL, 40L, ResourceType.MECHANICAL_PARTS, 10L, ResourceType.ELECTRONICS, 20L)));
            }
        }
        return entries;
    }

    private static RewardTable41.Entry national(String id, int weight, long money, Map<ResourceType, Long> resources) {
        return new RewardTable41.Entry(id, weight, new RewardTable41.Bundle(money, resources, List.of()));
    }

    private static MonumentType home(String category) throws IOException {
        return switch (category) {
            case "rifle", "mg" -> MonumentType.INDUSTRIAL_COMPLEX;
            case "shotgun", "dmr", "sniper" -> MonumentType.MILITARY_DEPOT;
            case "pistol", "smg" -> MonumentType.REFINERY;
            default -> throw new IOException("The ordinary gun has no audited monument role.");
        };
    }

    private static RewardTable41.Entitlement compatibleAmmo(String gunId) throws IOException {
        WeaponTierCatalog41.Entry gun = WeaponTierCatalog41.entry(gunId);
        var ammo = JsonParser.parseString(gun.ammoJson());
        if (!ammo.isJsonPrimitive() || !ammo.getAsJsonPrimitive().isString())
            throw new IOException("A reward gun requires one qualified ammunition identity.");
        String nativeId = ammo.getAsString();
        if (!gun.provider().equals("tacz") || !nativeId.startsWith("tacz:")) throw new IOException("The reward gun's native ammunition is unavailable.");
        return NativeReward43.activeEntitlement(RewardTable41.Kind.AMMO, nativeId);
    }

    private static int tierWeight(WeaponStock42.Offer offer) {
        return switch (offer.requiredLevel()) {
            case 0, 1 -> 12;
            case 2 -> 8;
            case 3 -> 4;
            case 4 -> 2;
            case 5 -> 1;
            default -> throw new IllegalArgumentException("Unqualified ordinary reward tier");
        };
    }

    /** Hamilton allocation preserves each explicit ticket budget. Equal remainder ties
     * use native ID, making every integer entry weight stable and independently auditable. */
    private static Map<String, Integer> allocate(List<WeaponStock42.Offer> offers, int tickets,
                                                 ToIntFunction<WeaponStock42.Offer> score) throws IOException {
        int totalScore = offers.stream().mapToInt(score).sum();
        if (totalScore < 1 || offers.isEmpty()) throw new IOException("The supply allocation has no qualified entries.");
        Map<String, Integer> weights = new HashMap<>();
        int allocated = 0;
        for (WeaponStock42.Offer offer : offers) {
            int weight = tickets * score.applyAsInt(offer) / totalScore;
            if (weight < 1) throw new IOException("A supply entry lost its positive weight.");
            weights.put(offer.id(), weight);
            allocated += weight;
        }
        List<WeaponStock42.Offer> remainder = offers.stream().sorted(Comparator
                .<WeaponStock42.Offer>comparingInt(offer -> tickets * score.applyAsInt(offer) % totalScore).reversed()
                .thenComparing(WeaponStock42.Offer::nativeId)).toList();
        for (int index = 0; index < tickets - allocated; index++) weights.compute(remainder.get(index).id(), (id, weight) -> weight + 1);
        return Map.copyOf(weights);
    }
}
