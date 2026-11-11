package com.quin.catancraft.world;

import com.google.gson.JsonParser;
import com.quin.catancraft.data.MonumentType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.util.*;

/** Current TaCZ-only policy, distinct from the immutable mixed-provider historical catalogs. */
@GameTestHolder("catancraft_active44") @PrefixGameTestTemplate(false)
public final class ActiveWeapon44Tests {
    private static final Set<String> RARES=Set.of("tacz:scar_h","tacz:scar_mk20","tacz:deagle_golden");
    private ActiveWeapon44Tests(){}

    @GameTest(template="townhall_state_empty",timeoutTicks=200)
    public static void current_stock_is_exactly_tacz_with_preserved_progression(GameTestHelper h)throws Exception{
        var stock=ActiveWeaponStock44.offers();
        h.assertTrue(stock.size()==69&&stock.stream().filter(o->o.kind()==WeaponShopCatalog40.Kind.GUN).count()==52,"TaCZ gun/ammo stock changed");
        Set<String> ids=new HashSet<>();for(var o:stock){
            h.assertTrue(o.provider().equals("tacz")&&o.nativeId().startsWith("tacz:")&&ids.add(o.id()),"Non-TaCZ or duplicate current offer");
            h.assertTrue(o.equals(WeaponStock42.offer(o.id()))&&o.equals(ActiveWeaponStock44.offer(o.id())),"Retained TaCZ price, amount, mode or revision changed");
            ActiveWeaponStock44.requireCurrent(o);
        }
        int[] totals={2,14,25,44,61,69},guns={1,8,16,33,46,52},ammo={1,6,9,11,15,17};
        for(int level=0;level<=5;level++){final int gate=level;
            h.assertTrue(stock.stream().filter(o->o.requiredLevel()<=gate).count()==totals[level],"Cumulative tier total changed");
            h.assertTrue(stock.stream().filter(o->o.requiredLevel()<=gate&&o.kind()==WeaponShopCatalog40.Kind.GUN).count()==guns[level],"Gun progression changed");
            h.assertTrue(stock.stream().filter(o->o.requiredLevel()<=gate&&o.kind()==WeaponShopCatalog40.Kind.AMMO).count()==ammo[level],"Ammo progression changed");
        }h.succeed();
    }

    @GameTest(template="townhall_state_empty",timeoutTicks=200)
    public static void all_old_sbw_offers_and_forged_aliases_are_absent_from_current_counter(GameTestHelper h)throws Exception{
        var old=WeaponStock42.offers().stream().filter(o->o.provider().equals("superbwarfare")).toList();
        h.assertTrue(old.size()==28,"Historical SBW audit coverage changed");
        for(var o:old){refuse(()->ActiveWeaponStock44.offer(o.id()));refuse(()->ActiveWeaponStock44.requireCurrent(o));
            var forged=new WeaponStock42.Offer(o.id(),o.title(),o.kind(),"tacz",o.nativeId(),o.referenceGun(),o.count(),o.requiredLevel(),o.fireMode(),o.price(),o.revision());
            refuse(()->ActiveWeaponStock44.requireCurrent(forged));
        }
        List<WeaponStock42.Offer> pages=new ArrayList<>();for(int page=0;page<9;page++)pages.addAll(ActiveWeaponStock44.page(page,8));
        h.assertTrue(pages.equals(ActiveWeaponStock44.offers()),"Paging omits, duplicates or exposes hidden provider stock");
        refuse(()->ActiveWeaponStock44.page(9,8));refuse(()->ActiveWeaponStock44.offer("superbwarfare:ak_47"));
        h.succeed();
    }

    @GameTest(template="townhall_state_empty",timeoutTicks=200)
    public static void revised_pool_budgets_preserve_national_rewards_and_rarity_bands(GameTestHelper h)throws Exception{
        for(var type:MonumentType.values()){
            var table=MonumentSupply44.table(type);int entries=switch(type){case INDUSTRIAL_COMPLEX->42;case MILITARY_DEPOT->36;case REFINERY->37;};
            h.assertTrue(table.entries().size()==entries&&table.totalWeight()==100_000&&table.revision().equals(MonumentSupply44.REVISION+"_"+type.id()),"New pool identity or ticket budget changed");
            Map<String,Long> totals=new HashMap<>();
            for(var e:table.entries())totals.merge(band(e), (long)e.weight(),Long::sum);
            boolean military=type==MonumentType.MILITARY_DEPOT;
            h.assertTrue(totals.equals(Map.of("national",military?40_000L:75_000L,"ammo",military?30_000L:20_000L,"ordinary",military?29_500L:4_900L,"rare",military?500L:100L)),"Provider removal changed category rarity budgets");
            for(var e:MonumentSupply43.table(type).entries())if(e.reward().entitlements().isEmpty())
                h.assertTrue(table.entries().stream().anyMatch(n->n.id().equals(e.id())&&n.weight()==e.weight()&&n.reward().equals(e.reward())),"National reward amount or odds changed");
        }h.succeed();
    }

    @GameTest(template="townhall_state_empty",timeoutTicks=200)
    public static void active_pools_cover_every_tacz_identity_with_compatible_ammo_and_no_sbw(GameTestHelper h)throws Exception{
        Set<RewardTable41.Entitlement> found=new HashSet<>();Map<String,Integer> guns=new HashMap<>();int ammoOnly=0;
        for(var type:MonumentType.values())for(var e:MonumentSupply44.table(type).entries()){
            var items=e.reward().entitlements();for(var item:items){NativeReward43.validateActive(item);h.assertTrue(item.objectId().startsWith("tacz:")&&item.kind()!=RewardTable41.Kind.VEHICLE,"Non-TaCZ/vehicle active prize");found.add(item);}
            if(items.size()==1){h.assertTrue(items.get(0).kind()==RewardTable41.Kind.AMMO,"Unexpected single equipment prize");ammoOnly++;}
            if(items.size()==2){var gun=items.get(0);var ammo=items.get(1);guns.merge(gun.objectId(),1,Integer::sum);
                String expected=JsonParser.parseString(WeaponTierCatalog41.entry(gun.objectId()).ammoJson()).getAsString();
                h.assertTrue(gun.kind()==RewardTable41.Kind.GUN&&gun.count()==1&&ammo.kind()==RewardTable41.Kind.AMMO&&ammo.objectId().equals(expected),"Gun prize lacks exact compatible ammo");
                h.assertTrue(ammo.count()==(expected.equals("tacz:338")?30:32),"Ammo bundle exceeds native limit");
            }
        }
        h.assertTrue(found.equals(Set.copyOf(NativeReward43.activeEntitlements()))&&found.size()==72&&guns.size()==55&&guns.values().stream().allMatch(n->n==1)&&ammoOnly==51,"TaCZ pool coverage omitted or duplicated equipment");
        for(String rare:RARES)h.assertTrue(guns.containsKey(rare)&&ActiveWeaponStock44.offers().stream().noneMatch(o->o.nativeId().equals(rare)),"Rare reward gun entered paid stock");
        h.succeed();
    }

    @GameTest(template="townhall_state_empty",timeoutTicks=200)
    public static void current_tables_roundtrip_without_migrating_frozen_historical_outcomes(GameTestHelper h)throws Exception{
        for(var type:MonumentType.values()){
            var old=MonumentSupply43.table(type);CompoundTag frozen=old.save();var current=MonumentSupply44.table(type);var restored=RewardTable41.load(current.save());
            h.assertTrue(!current.revision().equals(old.revision())&&restored.save().equals(current.save()),"Provider policy reused old pool revision or lost saved content");
            Set<String> selected=new HashSet<>();
            for(int i=0;i<32768;i++){long seed=0x44000000L+i;var roll=current.roll(seed);h.assertTrue(roll.equals(restored.roll(seed)),"Saved table rerolled the same seed");selected.add(roll.entryId());}
            h.assertTrue(selected.size()==current.entries().size(),"Deterministic sample omitted a current prize entry");
            h.assertTrue(old.save().equals(frozen)&&RewardTable41.load(frozen).save().equals(frozen),"Historical mixed-provider table was rewritten");
        }h.succeed();
    }

    @GameTest(template="townhall_state_empty",timeoutTicks=200)
    public static void active_entitlements_reject_old_sbw_and_forged_counts_or_revisions(GameTestHelper h)throws Exception{
        int refused=0;for(var e:NativeReward43.entitlements()){
            NativeReward43.validate(e);
            if(e.objectId().startsWith("superbwarfare:")){refuse(()->NativeReward43.validateActive(e));refuse(()->NativeReward43.description(e));refused++;}
            else{NativeReward43.validateActive(e);h.assertTrue(NativeReward43.activeEntitlement(e.kind(),e.objectId()).equals(e),"Historical TaCZ claim identity changed");
                refuse(()->NativeReward43.validateActive(new RewardTable41.Entitlement(e.kind(),e.objectId(),e.count(),"forged44_revision")));
                if(e.kind()==RewardTable41.Kind.AMMO)refuse(()->NativeReward43.validateActive(new RewardTable41.Entitlement(e.kind(),e.objectId(),e.count()+1,e.revision())));
            }
        }
        h.assertTrue(refused==28,"Not every historical SBW equipment identity is denied");
        refuse(()->NativeReward43.activeEntitlement(RewardTable41.Kind.GUN,"superbwarfare:ak_47"));
        refuse(()->NativeReward43.activeEntitlement(RewardTable41.Kind.AMMO,"superbwarfare:rifle_ammo"));h.succeed();
    }

    private static String band(RewardTable41.Entry e){return e.reward().entitlements().isEmpty()?"national":e.id().startsWith("ammunition_")?"ammo":e.id().startsWith("very_rare_")?"rare":"ordinary";}
    private interface Attempt{void run()throws Exception;}
    private static void refuse(Attempt a)throws Exception{try{a.run();}catch(IOException expected){return;}throw new AssertionError("Expected current TaCZ-only policy refusal");}
}
