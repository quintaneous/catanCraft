package com.quin.catancraft.world;

import com.google.gson.JsonParser;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.data.MonumentData;
import com.quin.catancraft.data.MonumentType;
import com.quin.catancraft.data.NationData;
import com.quin.catancraft.data.ResourceType;
import com.quin.catancraft.monument.MonumentManager;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;

/** Bounded production-pool/model tests; only the last test writes its own marked disposable ledger.
 * No native gun construction, combat balance, player delivery or vehicle acceptance is implied. */
@GameTestHolder("catancraft_supply43") @PrefixGameTestTemplate(false)
public final class MonumentSupply43Tests {
    private static final Set<String> RARES = Set.of("tacz:scar_h", "tacz:scar_mk20", "tacz:deagle_golden");
    private static final Map<String, String> SBW_AMMO = Map.of(
            "@HandgunAmmo", "superbwarfare:handgun_ammo", "@RifleAmmo", "superbwarfare:rifle_ammo",
            "@ShotgunAmmo", "superbwarfare:shotgun_ammo", "@SniperAmmo", "superbwarfare:sniper_ammo");
    private static final int SAMPLES_PER_POOL = 32_768;
    private MonumentSupply43Tests() {}

    @GameTest(template="townhall_state_empty", batch="supply43_01", timeoutTicks=200)
    public static void production_pools_have_exact_ticket_budgets_and_expected_national_credits(GameTestHelper h) throws Exception {
        for (MonumentType type : MonumentType.values()) {
            RewardTable41 table = MonumentSupply43.table(type);
            boolean military = type == MonumentType.MILITARY_DEPOT;
            int expectedEntries = type == MonumentType.INDUSTRIAL_COMPLEX ? 57 : 47;
            h.assertTrue(table.entries().size() == expectedEntries && table.totalWeight() == 100_000,
                    "Production entry count or 100,000-ticket budget changed: " + type);
            h.assertTrue(table.revision().equals("monument_supply43_provisional_v1_" + type.id()), "Unversioned production pool");
            Map<String, Long> weights = new HashMap<>();
            Map<ResourceType, Long> resourceNumerators = new EnumMap<>(ResourceType.class);
            long moneyNumerator = 0, gunNumerator = 0, ammoNumerator = 0;
            for (RewardTable41.Entry entry : table.entries()) {
                weights.merge(band(entry), (long) entry.weight(), Long::sum);
                moneyNumerator += entry.weight() * entry.reward().money();
                entry.reward().resources().forEach((resource, amount) -> resourceNumerators.merge(resource, entry.weight() * amount, Long::sum));
                for (var entitlement : entry.reward().entitlements()) {
                    if (entitlement.kind() == RewardTable41.Kind.GUN) gunNumerator += entry.weight();
                    if (entitlement.kind() == RewardTable41.Kind.AMMO) ammoNumerator += entry.weight();
                }
            }
            Map<String, Long> expectedWeights = switch (type) {
                case INDUSTRIAL_COMPLEX -> Map.of("treasury", 20_000L, "materials", 55_000L, "ammunition", 20_000L, "ordinary", 4_900L, "very_rare", 100L);
                case MILITARY_DEPOT -> Map.of("treasury", 10_000L, "materials", 30_000L, "ammunition", 30_000L, "ordinary", 29_500L, "very_rare", 500L);
                case REFINERY -> Map.of("treasury", 15_000L, "materials", 60_000L, "ammunition", 20_000L, "ordinary", 4_900L, "very_rare", 100L);
            };
            Map<ResourceType, Long> expectedResources = switch (type) {
                case INDUSTRIAL_COMPLEX -> Map.of(ResourceType.STEEL, 2_500_000L, ResourceType.CONCRETE, 2_800_000L,
                        ResourceType.MECHANICAL_PARTS, 600_000L, ResourceType.ELECTRONICS, 160_000L);
                case MILITARY_DEPOT -> Map.of(ResourceType.STEEL, 1_000_000L, ResourceType.FABRIC, 1_200_000L,
                        ResourceType.EXPLOSIVES, 600_000L, ResourceType.MECHANICAL_PARTS, 200_000L, ResourceType.ELECTRONICS, 100_000L);
                case REFINERY -> Map.of(ResourceType.OIL, 2_700_000L, ResourceType.FUEL, 5_100_000L,
                        ResourceType.MECHANICAL_PARTS, 150_000L, ResourceType.ELECTRONICS, 300_000L);
            };
            long expectedMoney = switch (type) { case INDUSTRIAL_COMPLEX -> 80_000_000; case MILITARY_DEPOT -> 50_000_000; case REFINERY -> 75_000_000; };
            h.assertTrue(weights.equals(expectedWeights), "Rarity band budget changed: " + type);
            h.assertTrue(moneyNumerator == expectedMoney && resourceNumerators.equals(expectedResources),
                    "Exact expectation numerator changed; denominator is 100,000: " + type);
            h.assertTrue(gunNumerator == (military ? 30_000 : 5_000) && ammoNumerator == (military ? 60_000 : 25_000),
                    "Gun or compatible ammo stack expectation changed: " + type);
        }
        refuse(() -> MonumentSupply43.table(null));
        h.succeed();
    }

    @GameTest(template="townhall_state_empty", batch="supply43_02", timeoutTicks=200)
    public static void all_one_hundred_identities_are_covered_and_every_other_reservation_stays_excluded(GameTestHelper h) throws Exception {
        Set<RewardTable41.Entitlement> canonical = Set.copyOf(NativeReward43.entitlements());
        Set<RewardTable41.Entitlement> covered = new HashSet<>();
        Map<String, Integer> gunOccurrences = new HashMap<>(), ammoOnlyOccurrences = new HashMap<>();
        h.assertTrue(canonical.size() == 100 && NativeReward43.entitlements().size() == 100, "Reward identities are missing or duplicated");
        h.assertTrue(canonical.stream().filter(e -> e.kind() == RewardTable41.Kind.GUN).count() == 79
                && canonical.stream().filter(e -> e.kind() == RewardTable41.Kind.AMMO).count() == 21, "Reward gun/ammo coverage changed");
        for (MonumentType type : MonumentType.values()) for (RewardTable41.Entry entry : MonumentSupply43.table(type).entries()) {
            for (RewardTable41.Entitlement entitlement : entry.reward().entitlements()) {
                NativeReward43.validate(entitlement);
                h.assertTrue(entitlement.kind() != RewardTable41.Kind.VEHICLE, "Portable/entity vehicle reward entered the live pool");
                covered.add(entitlement);
                if (entitlement.kind() == RewardTable41.Kind.GUN) gunOccurrences.merge(entitlement.objectId(), 1, Integer::sum);
                else if (entry.reward().entitlements().size() == 1) ammoOnlyOccurrences.merge(entitlement.objectId(), 1, Integer::sum);
            }
        }
        h.assertTrue(covered.equals(canonical) && gunOccurrences.size() == 79
                && gunOccurrences.values().stream().allMatch(count -> count == 1), "Gun outcomes are omitted or duplicated across monument roles");
        h.assertTrue(ammoOnlyOccurrences.size() == 21 && ammoOnlyOccurrences.values().stream().allMatch(count -> count == 3),
                "Every ordinary ammo identity must appear in each pool");
        Set<String> ordinary = new HashSet<>();
        for (WeaponStock42.Offer offer : WeaponStock42.offers()) {
            RewardTable41.Kind kind = offer.kind() == WeaponShopCatalog40.Kind.GUN ? RewardTable41.Kind.GUN : RewardTable41.Kind.AMMO;
            h.assertTrue(canonical.contains(new RewardTable41.Entitlement(kind, offer.nativeId(), offer.count(), NativeReward43.REVISION)),
                    "An ordinary native stock identity was changed or omitted");
            if (kind == RewardTable41.Kind.GUN) ordinary.add(offer.nativeId());
        }
        Set<String> extras = new HashSet<>(gunOccurrences.keySet()); extras.removeAll(ordinary);
        h.assertTrue(extras.equals(RARES), "A reserved gun gained an unreviewed monument entitlement");
        int excludedDrops = 0, holds = 0, conditionalShop = 0;
        for (WeaponTierCatalog41.Entry gun : WeaponTierCatalog41.entries()) {
            boolean excluded = gun.disposition() == WeaponTierCatalog41.Disposition.HOLD
                    || gun.disposition() == WeaponTierCatalog41.Disposition.DROP && !RARES.contains(gun.id())
                    || gun.requiresSpecialQualification();
            if (gun.disposition() == WeaponTierCatalog41.Disposition.DROP && !RARES.contains(gun.id())) excludedDrops++;
            if (gun.disposition() == WeaponTierCatalog41.Disposition.HOLD) holds++;
            if (gun.disposition() == WeaponTierCatalog41.Disposition.SHOP && gun.requiresSpecialQualification()) conditionalShop++;
            if (excluded) {
                h.assertTrue(!gunOccurrences.containsKey(gun.id()), "Excluded native gun entered a production pool: " + gun.id());
                refuse(() -> NativeReward43.entitlement(RewardTable41.Kind.GUN, gun.id()));
            }
            if (RARES.contains(gun.id())) {
                refuse(() -> WeaponTierCatalog41.requireShop(gun.id(), 5, WeaponTierCatalog41.CANDIDATE_REVISION));
                h.assertTrue(WeaponStock42.offers().stream().noneMatch(o -> o.nativeId().equals(gun.id())), "A rare reward became purchasable");
            }
        }
        h.assertTrue(excludedDrops == 25 && holds == 2 && conditionalShop == 7, "Reserved/excluded policy coverage changed");
        for (String internal : List.of("superbwarfare:empty_gun", "superbwarfare:vehicle_gun", "tacz:unknown"))
            refuse(() -> NativeReward43.entitlement(RewardTable41.Kind.GUN, internal));
        refuse(() -> NativeReward43.entitlement(RewardTable41.Kind.VEHICLE, "superbwarfare:sodayo_pick_up"));
        h.succeed();
    }

    @GameTest(template="townhall_state_empty", batch="supply43_03", timeoutTicks=200)
    public static void bounded_seeded_draws_repeat_exactly_and_follow_each_production_entry_weight(GameTestHelper h) throws Exception {
        for (MonumentType type : MonumentType.values()) {
            RewardTable41 table = MonumentSupply43.table(type), restored = RewardTable41.load(table.save());
            Map<String, RewardTable41.Entry> entries = new HashMap<>();
            Map<String, Integer> counts = new HashMap<>();
            for (var entry : table.entries()) entries.put(entry.id(), entry);
            SplittableRandom seeds = new SplittableRandom(0x43C0FFEE1234L + type.ordinal());
            for (int index = 0; index < SAMPLES_PER_POOL; index++) {
                long seed = seeds.nextLong();
                RewardTable41.Roll first = table.roll(seed);
                h.assertTrue(first.equals(table.roll(seed)) && first.equals(restored.roll(seed)), "Saved production table or repeated seed rerolled an outcome");
                h.assertTrue(entries.containsKey(first.entryId()) && entries.get(first.entryId()).reward().equals(first.reward()),
                        "Weighted draw emitted an unknown or altered bundle");
                counts.merge(first.entryId(), 1, Integer::sum);
            }
            for (RewardTable41.Entry entry : table.entries()) {
                double probability = entry.weight() / 100_000.0;
                double expected = SAMPLES_PER_POOL * probability;
                double tolerance = 6 * Math.sqrt(expected * (1 - probability)) + 4;
                int actual = counts.getOrDefault(entry.id(), 0);
                h.assertTrue(actual > 0 && Math.abs(actual - expected) <= tolerance,
                        "Fixed sample differs from declared entry weight: " + type + "/" + entry.id());
            }
            h.assertTrue(counts.values().stream().mapToInt(Integer::intValue).sum() == SAMPLES_PER_POOL,
                    "Bounded distribution sample lost outcomes");
            h.assertTrue(restored.save().equals(table.save()), "Production table native serialization changed");
        }
        h.succeed();
    }

    @GameTest(template="townhall_state_empty", batch="supply43_04", timeoutTicks=200)
    public static void every_gun_bundle_contains_one_exact_compatible_native_ammunition_stack(GameTestHelper h) throws Exception {
        int pairedGuns = 0, ammoOnly = 0, thirtyRoundStacks = 0;
        for (MonumentType type : MonumentType.values()) for (RewardTable41.Entry entry : MonumentSupply43.table(type).entries()) {
            List<RewardTable41.Entitlement> items = entry.reward().entitlements();
            if (items.isEmpty()) continue;
            h.assertTrue(entry.reward().money() == 0 && entry.reward().resources().isEmpty(), "Equipment was added on top of national payout");
            if (items.get(0).kind() == RewardTable41.Kind.GUN) {
                pairedGuns++;
                h.assertTrue(items.size() == 2 && items.get(0).count() == 1 && items.get(1).kind() == RewardTable41.Kind.AMMO,
                        "Gun reward lacks its bounded gun/ammo pair");
                WeaponTierCatalog41.Entry reference = WeaponTierCatalog41.entry(items.get(0).objectId());
                String token = JsonParser.parseString(reference.ammoJson()).getAsString();
                String expectedAmmo = reference.provider().equals("tacz") ? token : SBW_AMMO.get(token);
                h.assertTrue(items.get(1).objectId().equals(expectedAmmo), "Reward gun received another provider's or caliber's ammo");
            } else {
                ammoOnly++;
                h.assertTrue(items.size() == 1 && items.get(0).kind() == RewardTable41.Kind.AMMO, "Unsupported equipment bundle shape");
            }
            for (var item : items) {
                NativeReward43.validate(item);
                if (item.kind() == RewardTable41.Kind.AMMO) {
                    int expected = item.objectId().equals("tacz:338") ? 30 : 32;
                    h.assertTrue(item.count() == expected, "Native ammunition count exceeds its qualified bundle");
                    if (expected == 30) thirtyRoundStacks++;
                }
            }
        }
        h.assertTrue(pairedGuns == 79 && ammoOnly == 63 && thirtyRoundStacks == 4,
                "Gun/ammo pair or .338 native-stack coverage changed");
        h.assertTrue(NativeReward43.entitlement(RewardTable41.Kind.AMMO, "tacz:338").count() == 30, ".338 native stack bound changed");
        h.succeed();
    }

    @GameTest(template="townhall_state_empty", batch="supply43_05", timeoutTicks=200)
    public static void forged_reward_alias_kind_amount_and_revision_never_gain_native_authority(GameTestHelper h) throws Exception {
        var ammo = NativeReward43.entitlement(RewardTable41.Kind.AMMO, "tacz:338");
        var gun = NativeReward43.entitlement(RewardTable41.Kind.GUN, "tacz:scar_h");
        List<RewardTable41.Entitlement> forged = List.of(
                new RewardTable41.Entitlement(ammo.kind(), ammo.objectId(), 32, ammo.revision()),
                new RewardTable41.Entitlement(ammo.kind(), ammo.objectId(), 29, ammo.revision()),
                new RewardTable41.Entitlement(ammo.kind(), "tacz:338_alias", ammo.count(), ammo.revision()),
                new RewardTable41.Entitlement(RewardTable41.Kind.GUN, ammo.objectId(), 1, ammo.revision()),
                new RewardTable41.Entitlement(RewardTable41.Kind.AMMO, gun.objectId(), 1, gun.revision()),
                new RewardTable41.Entitlement(RewardTable41.Kind.VEHICLE, gun.objectId(), 1, gun.revision()),
                new RewardTable41.Entitlement(gun.kind(), gun.objectId(), 1, "monument_supply43_native_v2"),
                new RewardTable41.Entitlement(gun.kind(), gun.objectId(), 1, WeaponShopCatalog40.REVISION),
                new RewardTable41.Entitlement(gun.kind(), "tacz:aa12", 1, gun.revision()),
                new RewardTable41.Entitlement(gun.kind(), "superbwarfare:empty_gun", 1, gun.revision()));
        for (var entitlement : forged) {
            refuse(() -> NativeReward43.validate(entitlement));
            refuse(() -> MonumentSupply43.validate(entitlement));
            refuse(() -> NativeReward43.description(entitlement));
        }
        refuse(() -> NativeReward43.validate(null));
        invalid(() -> new RewardTable41.Entitlement(gun.kind(), gun.objectId(), 2, gun.revision()));
        invalid(() -> new RewardTable41.Entitlement(ammo.kind(), ammo.objectId(), 0, ammo.revision()));
        h.assertTrue(NativeReward43.entitlement(gun.kind(), gun.objectId()).equals(gun)
                && NativeReward43.entitlement(ammo.kind(), ammo.objectId()).equals(ammo), "Rejected record changed canonical entitlement");
        h.succeed();
    }

    @GameTest(template="townhall_state_empty", batch="supply43_06", timeoutTicks=200)
    public static void pool_inputs_and_pinned_sources_are_immutable_and_legacy_prices_and_tiers_stay_exact(GameTestHelper h) throws Exception {
        Map<String, String> pins = Map.of(
                "/data/catancraft/economy/weapon_tiers41.json", "230d8a97e9b744902c97534009bb8b6089a7d89314010e23771755f3b205181a",
                "/data/catancraft/economy/tacz_native42.json", "2c24dc08835a7489cd836bf05debc206e71545183098bcf217f59fb4fedb5b05",
                "/data/catancraft/economy/native_sbw42_reference.json", "058fbac6a7059a63aa514f7f59b246690a9f94867a9af67331cf679511b43c6b");
        for (var pin : pins.entrySet()) h.assertTrue(TownHallRevisions.sha(resource(pin.getKey())).equals(pin.getValue()), "Pinned immutable source changed");
        immutable(() -> NativeReward43.entitlements().clear());
        for (MonumentType type : MonumentType.values()) {
            RewardTable41 table = MonumentSupply43.table(type);
            CompoundTag frozen = table.save(), mutated = table.save();
            mutated.putString("revision", "forged"); mutated.getList("entries", Tag.TAG_COMPOUND).getCompound(0).putInt("weight", 1);
            h.assertTrue(table.save().equals(frozen), "Caller mutated the production table through exported NBT");
            immutable(() -> table.entries().clear());
            immutable(() -> table.entries().get(1).reward().resources().clear());
            var equipment = table.entries().stream().filter(e -> !e.reward().entitlements().isEmpty()).findFirst().orElseThrow();
            immutable(() -> equipment.reward().entitlements().clear());
        }
        Map<String, WeaponShopCatalog40.Offer> expected = Map.of(
                "pistol", new WeaponShopCatalog40.Offer("pistol", "Glock 17", WeaponShopCatalog40.Kind.GUN, "tacz:glock_17", "SEMI", 1, 0,
                        new ShopPurchase40.Price(350, Map.of(ResourceType.WOOD, 5L, ResourceType.STONE, 5L))),
                "ak47", new WeaponShopCatalog40.Offer("ak47", "AK-47", WeaponShopCatalog40.Kind.GUN, "tacz:ak47", "AUTO", 1, 1,
                        new ShopPurchase40.Price(1200, Map.of(ResourceType.STEEL, 12L, ResourceType.WOOD, 10L))),
                "ammo9", new WeaponShopCatalog40.Offer("ammo9", "9mm ammunition", WeaponShopCatalog40.Kind.AMMO, "tacz:9mm", "", 32, 0,
                        new ShopPurchase40.Price(100, Map.of(ResourceType.STONE, 5L))),
                "ammo762", new WeaponShopCatalog40.Offer("ammo762", "7.62x39 ammunition", WeaponShopCatalog40.Kind.AMMO, "tacz:762x39", "", 32, 1,
                        new ShopPurchase40.Price(175, Map.of(ResourceType.STEEL, 2L, ResourceType.EXPLOSIVES, 1L))));
        h.assertTrue(WeaponShopCatalog40.offers().size() == 4, "Historical 040 catalog expanded");
        for (var old : WeaponShopCatalog40.offers()) {
            h.assertTrue(old.equals(expected.get(old.id())), "An existing 040 price, gate, native ID or amount changed");
            var current = WeaponStock42.offer(old.id());
            h.assertTrue(current.price().equals(old.price()) && current.requiredLevel() == old.requiredLevel()
                    && current.count() == old.count() && current.nativeId().equals(old.nativeId())
                    && current.revision().equals("physical_weapon_shop40_existing_prices_v1"), "Reward work repriced or retiered an old shop offer");
        }
        h.assertTrue(WeaponTierCatalog41.entries().size() == 113 && WeaponTierCatalog41.entry("tacz:ak47").proposedLevel() == 2
                && WeaponStock42.offer("ak47").requiredLevel() == 1, "Grandfathered AK gate rewrote the immutable policy");
        for (var pin : pins.entrySet()) h.assertTrue(TownHallRevisions.sha(resource(pin.getKey())).equals(pin.getValue()), "Pool access rewrote an input source");
        h.succeed();
    }

    @GameTest(template="townhall_state_empty", batch="supply43_07", timeoutTicks=200)
    public static void reward_selection_tokens_bind_sessions_and_pagination_is_complete_bounded_and_immutable(GameTestHelper h) throws Exception {
        UUID session = UUID.fromString("43abcdef-1234-4567-89ab-123456789abc");
        UUID reward = UUID.fromString("43abcdef-1234-4567-89ab-abcdef123456");
        for (int index : new int[]{0, 9, 10, 15}) h.assertTrue(
                PhysicalWeaponShop40.selectedReward(session, session + "." + reward + "." + index).equals(new RewardClaim43.Selection(reward, index)),
                "Canonical in-session reward selection was rejected");
        for (String text : Arrays.asList(null, "", session.toString(), session + "." + reward,
                session + "." + reward + ".", session + "." + reward + ".00", session + "." + reward + ".01",
                session + "." + reward + ".-1", session + "." + reward + ".16", session + "." + reward + ".99999",
                session + "." + reward + ".0.extra", UUID.randomUUID() + "." + reward + ".0",
                session.toString().toUpperCase(Locale.ROOT) + "." + reward + ".0",
                session + "." + reward.toString().toUpperCase(Locale.ROOT) + ".0",
                session + ".00000000-0000-0000-0000-000000000000.0", session + ".1-1-1-1-1.0",
                " " + session + "." + reward + ".0", session + "." + reward + ".0 "))
            refuse(() -> PhysicalWeaponShop40.selectedReward(session, text));
        List<RewardClaim43.View> original = new ArrayList<>();
        var entitlement = NativeReward43.entitlement(RewardTable41.Kind.AMMO, "tacz:9mm");
        for (int index = 0; index < 19; index++) original.add(new RewardClaim43.View(
                new RewardClaim43.Selection(new UUID(43, index + 1), index % 16), entitlement, "AVAILABLE", null));
        List<RewardClaim43.View> collected = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            List<RewardClaim43.View> part = PhysicalWeaponShop40.rewardPage(original, page);
            h.assertTrue(part.size() == (page < 2 ? 8 : 3), "Reward page size changed");
            immutable(part::clear); collected.addAll(part);
        }
        h.assertTrue(collected.equals(original), "Reward pages omitted, duplicated or reordered entitlements");
        List<RewardClaim43.View> frozenPage = PhysicalWeaponShop40.rewardPage(original, 0);
        original.clear(); h.assertTrue(frozenPage.size() == 8 && collected.size() == 19, "Caller changed a previously returned reward page");
        h.assertTrue(PhysicalWeaponShop40.rewardPage(List.of(), 0).isEmpty(), "Empty rewards cannot display their first page");
        for (int page : new int[]{-1, 1, Integer.MAX_VALUE}) refuse(() -> PhysicalWeaponShop40.rewardPage(List.of(), page));
        for (int page : new int[]{-1, 3, Integer.MAX_VALUE}) refuse(() -> PhysicalWeaponShop40.rewardPage(collected, page));
        h.succeed();
    }

    @GameTest(template="townhall_state_empty", batch="supply43_08", timeoutTicks=400)
    public static void public_activation_uses_new_pools_while_prejournal_round_keeps_its_frozen_legacy_capture(GameTestHelper h) throws Exception {
        try (ActivationFixture fixture = new ActivationFixture(h)) {
            MonumentData fresh = fixture.monument("fresh", MonumentType.INDUSTRIAL_COMPLEX);
            fixture.activate(fresh);
            MonumentRewards41.prepareActivation(fixture.server, fixture.data, fresh);
            CompoundTag first = state(fresh);
            h.assertTrue(first.getString("tableRevision").equals(MonumentSupply44.table(fresh.type()).revision())
                    && first.getCompound("identity").getString("scope").equals("runtime"), "Public new activation did not freeze the production supply profile");
            h.assertTrue(MonumentRewards41.readyActivation(fixture.server, fixture.data, fresh), "Saved public supply round is not ready");
            MonumentRewards41.prepareActivation(fixture.server, fixture.data, fresh);
            h.assertTrue(state(fresh).equals(first) && fixture.diskMonument(fresh).getCompound("rewardActivation41").equals(first),
                    "Repeated public activation rerolled an already frozen production round");

            MonumentData legacy = fixture.monument("legacy", MonumentType.MILITARY_DEPOT);
            fixture.activate(legacy);
            h.assertTrue(legacy.rewardActivation41() == null && MonumentRewards41.readyActivation(fixture.server, fixture.data, legacy),
                    "Pre-journal active round was not initialized safely");
            CompoundTag frozen = state(legacy);
            RewardTable41.Bundle oldReward = RewardTable41.Bundle.load(frozen.getCompound("roll"));
            h.assertTrue(frozen.getString("tableRevision").equals("monument41_legacy_military_depot_v1")
                    && oldReward.money() == 1500 && oldReward.resources().equals(Map.of(ResourceType.STEEL, 25L, ResourceType.FABRIC, 30L, ResourceType.EXPLOSIVES, 20L))
                    && oldReward.entitlements().isEmpty(), "Already-active legacy monument acquired new random odds or equipment");
            MonumentRewards41.prepareActivation(fixture.server, fixture.data, legacy);
            h.assertTrue(state(legacy).equals(frozen), "New profile replaced an existing hidden legacy roll");
            legacy.setCapturingNationId(fixture.nation.id()); legacy.setCaptureProgressTicks(MonumentManager.CAPTURE_TICKS);
            MonumentRewards41.Result result = MonumentRewards41.completeCapture(fixture.server, fixture.data, legacy, fixture.nation.id());
            h.assertTrue(result.complete() && result.rewardId().equals(frozen.getUUID("id")), "Legacy capture lost its original reward identity");
            h.assertTrue(fixture.nation.treasury() == 2500, "Legacy capture no longer pays its original fixed treasury exactly once");
            for (ResourceType resource : ResourceType.values()) h.assertTrue(fixture.nation.resource(resource) == oldReward.resources().getOrDefault(resource, 0L),
                    "Legacy capture changed its fixed materials");
            Path intentPath = fixture.root.resolve("data/catancraft_reward41_" + result.rewardId() + ".dat");
            byte[] originalIntent = Files.readAllBytes(intentPath);
            CompoundTag intent = ProducerJournal.read(originalIntent);
            h.assertTrue(intent.getCompound("roll").equals(frozen.getCompound("roll")) && intent.getString("tableRevision").equals(frozen.getString("tableRevision")),
                    "Captured legacy reward intent was migrated to the new production profile");
            h.assertTrue(MonumentRewards41.completeCapture(fixture.server, fixture.data, legacy, fixture.nation.id()).complete()
                    && fixture.nation.treasury() == 2500 && Arrays.equals(originalIntent, Files.readAllBytes(intentPath)), "Duplicate legacy capture rerolled, rewrote or paid again");
            h.assertTrue(fixture.diskMonument(legacy).getCompound("rewardActivation41").getString("phase").equals("SETTLED"), "Legacy capture lacks native settlement readback");
        }
        h.succeed();
    }

    private static String band(RewardTable41.Entry entry) {
        if (entry.id().startsWith("ammunition_")) return "ammunition";
        if (entry.id().startsWith("ordinary_")) return "ordinary";
        if (entry.id().startsWith("very_rare_")) return "very_rare";
        return entry.reward().money() > 0 ? "treasury" : "materials";
    }

    private static byte[] resource(String path) throws IOException {
        try (InputStream stream = MonumentSupply43Tests.class.getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing pinned source " + path);
            return stream.readAllBytes();
        }
    }

    private static CompoundTag state(MonumentData monument) throws IOException {
        if (!(monument.rewardActivation41() instanceof CompoundTag tag)) throw new IOException("Missing saved activation");
        return tag.copy();
    }

    private interface Attempt { void run() throws Exception; }
    private static void refuse(Attempt action) throws Exception {
        try { action.run(); } catch (IOException expected) { return; }
        throw new AssertionError("Expected exact reward authority or token refusal");
    }
    private static void invalid(Attempt action) throws Exception {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected bounded entitlement model refusal");
    }
    private static void immutable(Attempt action) throws Exception {
        try { action.run(); } catch (UnsupportedOperationException expected) { return; }
        throw new AssertionError("Expected immutable reward view");
    }

    private static final class ActivationFixture implements AutoCloseable {
        final MinecraftServer server;
        final CatanSavedData data;
        final Path root;
        final NationData nation;
        final String prefix = "supply43_" + UUID.randomUUID().toString().replace("-", "") + "_";
        final List<String> ownMonuments = new ArrayList<>();
        final String priorActive, priorLast;
        final long priorNext;

        ActivationFixture(GameTestHelper helper) throws IOException {
            server = helper.getLevel().getServer();
            root = server.getWorldPath(LevelResource.ROOT).toRealPath();
            if (!(server instanceof GameTestServer) || !server.isSameThread() || !root.getFileName().toString().equals("world")
                    || !root.getParent().getFileName().toString().equals("controlled-catancraft_supply43-run")
                    || !Files.readString(root.getParent().resolve(".catancraft-controlled-fixture-v1")).equals("CatanCraft synthetic flat fixture v1\n"))
                throw new IOException("Supply43 activation test requires its own marked disposable native world");
            data = CatanSavedData.get(server);
            priorActive = data.activeMonument() == null ? null : data.activeMonument().id();
            priorLast = data.lastActivatedMonumentId(); priorNext = data.nextMonumentActivationGameTime();
            nation = data.createNation(prefix + "nation", UUID.randomUUID()); nation.setTreasury(1000);
        }

        MonumentData monument(String suffix, MonumentType type) {
            MonumentData monument = data.createMonument(prefix + suffix, "Synthetic Supply43 " + suffix, type,
                    "minecraft:overworld", 0, 180, 0, 5);
            ownMonuments.add(monument.id()); return monument;
        }

        void activate(MonumentData monument) {
            data.setActiveMonumentId(monument.id()); data.setLastActivatedMonumentId(monument.id()); data.setNextMonumentActivationGameTime(0);
            persist();
        }

        CompoundTag diskMonument(MonumentData monument) throws IOException {
            ListTag records = NbtIo.readCompressed(root.resolve("data/catancraft.dat").toFile()).getCompound("data").getList("monuments", Tag.TAG_COMPOUND);
            for (int index = 0; index < records.size(); index++) if (records.getCompound(index).getString("id").equals(monument.id())) return records.getCompound(index).copy();
            throw new IOException("Missing native Supply43 monument readback");
        }

        void persist() { data.setDirty(); server.overworld().getDataStorage().save(); }

        public void close() {
            // Remove only this test's temporary records; retain immutable intent/receipt evidence and credit stamps.
            data.monuments().removeIf(monument -> ownMonuments.contains(monument.id())); data.nations().remove(nation);
            data.setActiveMonumentId(priorActive); data.setLastActivatedMonumentId(priorLast); data.setNextMonumentActivationGameTime(priorNext);
            persist();
        }
    }
}
