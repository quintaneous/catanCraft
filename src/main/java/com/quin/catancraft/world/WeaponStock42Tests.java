package com.quin.catancraft.world;

import com.google.gson.*;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

@GameTestHolder("catancraft_stock42") @PrefixGameTestTemplate(false)
public final class WeaponStock42Tests {
    @GameTest(template="townhall_state_empty")
    public static void full_stock_has_exact_unique_native_products_and_reserved_exclusions(GameTestHelper h)throws Exception {
        var all=WeaponStock42.offers();Set<String> sku=new HashSet<>(),nativeIds=new HashSet<>();int guns=0,ammo=0;
        for(var o:all){h.assertTrue(sku.add(o.id())&&nativeIds.add(o.nativeId()),"Duplicate stock identity");WeaponStock42.requireCurrent(o);
            var ref=WeaponTierCatalog41.entry(o.referenceGun());h.assertTrue(!ref.requiresSpecialQualification()&&(ref.disposition()==WeaponTierCatalog41.Disposition.SHOP||ref.disposition()==WeaponTierCatalog41.Disposition.STARTER),"Reserved or conditional gun entered ordinary stock");
            if(o.kind()==WeaponShopCatalog40.Kind.GUN)guns++;else ammo++;
        }
        h.assertTrue(all.size()==97&&guns==76&&ammo==21,"Ordinary stock coverage changed");
        for(var e:WeaponTierCatalog41.entries())if(e.requiresSpecialQualification()||e.disposition()==WeaponTierCatalog41.Disposition.DROP||e.disposition()==WeaponTierCatalog41.Disposition.HOLD)
            h.assertTrue(all.stream().noneMatch(o->o.referenceGun().equals(e.id())),"Reserved weapon authorized ordinary stock");h.succeed();
    }
    @GameTest(template="townhall_state_empty")
    public static void factory_unlocks_are_cumulative_and_preserve_all_legacy_gates(GameTestHelper h)throws Exception {
        int[] counts={2,19,37,63,85,97};
        for(int level=0;level<=5;level++){int tier=level;h.assertTrue(WeaponStock42.offers().stream().filter(o->o.requiredLevel()<=tier).count()==counts[level],"Factory tier coverage changed");}
        for(var old:WeaponShopCatalog40.offers()){
            var now=WeaponStock42.offer(old.id());h.assertTrue(now.nativeId().equals(old.nativeId())&&now.count()==old.count()&&now.requiredLevel()==old.requiredLevel()&&now.price().equals(old.price())&&now.revision().equals(WeaponShopCatalog40.REVISION),"Legacy offer gate, price or identity changed");
        }
        h.assertTrue(WeaponTierCatalog41.entry("tacz:ak47").proposedLevel()==2&&WeaponStock42.offer("ak47").requiredLevel()==1,"Legacy AK gate and separate reference proposal collapsed");h.succeed();
    }
    @GameTest(template="townhall_state_empty")
    public static void ammunition_matches_ordinary_native_consumers_and_minimum_unlocked_tier(GameTestHelper h)throws Exception {
        var all=WeaponStock42.offers();Map<String,String> sbw=Map.of("@HandgunAmmo","superbwarfare:handgun_ammo","@RifleAmmo","superbwarfare:rifle_ammo","@ShotgunAmmo","superbwarfare:shotgun_ammo","@SniperAmmo","superbwarfare:sniper_ammo");
        for(var a:all)if(a.kind()==WeaponShopCatalog40.Kind.AMMO){int minimum=99;boolean found=false;
            for(var g:all)if(g.kind()==WeaponShopCatalog40.Kind.GUN){String consumer=JsonParser.parseString(WeaponTierCatalog41.entry(g.referenceGun()).ammoJson()).getAsString();String nativeAmmo=g.provider().equals("tacz")?consumer:sbw.get(consumer);if(a.nativeId().equals(nativeAmmo)){minimum=Math.min(minimum,g.requiredLevel());found|=g.nativeId().equals(a.referenceGun());}}
            h.assertTrue(found&&a.requiredLevel()==minimum&&a.count()==32,"Ammunition has no qualified reference or incorrect minimum tier");
        }h.succeed();
    }
    @GameTest(template="townhall_state_empty")
    public static void forged_alias_price_count_mode_and_revision_never_authorize_supply(GameTestHelper h)throws Exception {
        var o=WeaponStock42.offer("pistol");
        for(int field=0;field<6;field++){int f=field;var forged=new WeaponStock42.Offer(o.id(),o.title(),o.kind(),o.provider(),f==0?"tacz:minigun":o.nativeId(),o.referenceGun(),f==1?64:o.count(),f==2?5:o.requiredLevel(),f==3?"AUTO":o.fireMode(),f==4?new ShopPurchase40.Price(1,Map.of()):o.price(),f==5?"unknown":o.revision());refuse(()->WeaponStock42.requireCurrent(forged));refuse(()->NativeWeapon42.quote(h.getLevel().getServer(),forged));}
        for(String id:List.of("tacz:minigun","superbwarfare:empty_gun","unknown",""))refuse(()->WeaponStock42.offer(id));h.succeed();
    }
    @GameTest(template="townhall_state_empty")
    public static void stock_pagination_is_complete_bounded_and_bound_to_the_original_session(GameTestHelper h)throws Exception {
        List<WeaponStock42.Offer> collected=new ArrayList<>();for(int i=0;i<13;i++)collected.addAll(WeaponStock42.page(i,8));h.assertTrue(collected.equals(WeaponStock42.offers()),"Pages omit or duplicate stock");
        for(int[] x:new int[][]{{-1,8},{13,8},{Integer.MAX_VALUE,64},{0,0},{0,65}})refuse(()->WeaponStock42.page(x[0],x[1]));
        UUID session=UUID.randomUUID();h.assertTrue(PhysicalWeaponShop40.selectedPage(session,session+".12")==12,"Valid page refused");
        for(String text:List.of(session+".-1",session+".01",session+".10000",session+".1.2",UUID.randomUUID()+".0",session.toString()))refuse(()->PhysicalWeaponShop40.selectedPage(session,text));h.succeed();
    }
    @GameTest(template="townhall_state_empty")
    public static void price_records_and_catalog_views_are_immutable_and_never_default_free(GameTestHelper h)throws Exception {
        for(var o:WeaponStock42.offers())h.assertTrue(o.price().money()>0&&o.price().money()<=6500&&o.price().resources().values().stream().allMatch(x->x>0&&x<100),"Missing, free or unbounded provisional price");
        try{WeaponStock42.offers().clear();throw new AssertionError("Mutable stock");}catch(UnsupportedOperationException expected){}
        try{WeaponStock42.offer("pistol").price().resources().clear();throw new AssertionError("Mutable price");}catch(UnsupportedOperationException expected){}
        h.succeed();
    }
    @GameTest(template="townhall_state_empty")
    public static void native_snapshots_are_copied_and_canonical_hashes_preserve_meaning(GameTestHelper h)throws Exception {
        ItemStack original=new ItemStack(Items.PAPER);var supply=new NativeWeapon42.Supply(original,"a".repeat(64));original.setCount(2);supply.item().setCount(3);h.assertTrue(supply.item().getCount()==1,"Caller changed frozen native item");
        h.assertTrue(NativeWeapon42.canonical(JsonParser.parseString("{\"b\":1.00,\"a\":[true,0]}" )).equals(NativeWeapon42.canonical(JsonParser.parseString("{\"a\":[true,-0.0],\"b\":1}"))),"Equivalent native values hash differently");
        h.assertTrue(!NativeWeapon42.canonical(JsonParser.parseString("[1,2]")).equals(NativeWeapon42.canonical(JsonParser.parseString("[2,1]"))),"Ordered native fields were reordered");
        refuse(()->NativeWeapon42.canonical(new JsonPrimitive(new BigDecimal("1e10001"))));
        refuse(()->NativeWeapon42.reference("/data/catancraft/economy/weapon_tiers41.json","0".repeat(64),131072));h.succeed();
    }
    @GameTest(template="townhall_state_empty")
    public static void missing_native_mods_refuse_without_touching_legacy_recovery(GameTestHelper h)throws Exception {
        for(String id:List.of("pistol","superbwarfare_gun_ak_47"))refuse(()->NativeWeapon42.quote(h.getLevel().getServer(),WeaponStock42.offer(id)));
        var o=WeaponStock42.offer("pistol");CompoundTag facility=new CompoundTag();facility.putString("id","synthetic-stock42");
        var original=new ShopPurchase40.Request(UUID.randomUUID(),o.id(),WeaponShopCatalog40.REVISION,facility,o.price(),new ItemStack(Items.PAPER));h.assertTrue(NativeWeapon42.recovered(original)==null,"Unbound040 intent acquired fabricated native proof");
        for(String catalog:List.of(o.revision()+"@bad","unknown@"+"a".repeat(64),o.revision()+"@"+"a".repeat(65))){var request=new ShopPurchase40.Request(UUID.randomUUID(),o.id(),catalog,facility,o.price(),new ItemStack(Items.PAPER));refuse(()->NativeWeapon42.recovered(request));}h.succeed();
    }
    private interface Attempt{void run()throws Exception;}
    private static void refuse(Attempt action)throws Exception{try{action.run();}catch(IOException expected){return;}throw new AssertionError("Expected refusal");}
}
