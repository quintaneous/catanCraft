package com.quin.catancraft.world;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.data.NationData;
import com.quin.catancraft.data.ResourceType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Real published-provider items and native player/SavedData persistence in isolated
 * marked flat GameTest worlds. Synthetic actors are never connected or registered.
 * Eight cases per provider; no authored/live-world or client acceptance claim. */
public final class NativeWeapon42Tests {
    static final long FUNDS = 100_000, STOCK = 10_000;
    static final String FIXTURE = "synthetic-native-weapon42-v1";
    private NativeWeapon42Tests() {}

    @GameTestHolder("catancraft_native42_tacz") @PrefixGameTestTemplate(false)
    public static final class Tacz {
        @GameTest(template="townhall_state_empty",batch="native42_01",timeoutTicks=600) public static void all_52_native_guns_are_unloaded_and_roundtrip(GameTestHelper h)throws Exception { all(h,"tacz",true,52); }
        @GameTest(template="townhall_state_empty",batch="native42_02",timeoutTicks=600) public static void all_17_native_ammunition_offers_roundtrip(GameTestHelper h)throws Exception { all(h,"tacz",false,17); }
        @GameTest(template="townhall_state_empty",batch="native42_03",timeoutTicks=200) public static void reserved_fake_and_changed_offers_refuse(GameTestHelper h)throws Exception { rejectedOffers(h,"tacz"); }
        @GameTest(template="townhall_state_empty",batch="native42_04",timeoutTicks=200) public static void quoted_item_and_fingerprint_are_frozen(GameTestHelper h)throws Exception { frozen(h,"tacz"); }
        @GameTest(template="townhall_state_empty",batch="native42_05",timeoutTicks=200) public static void changed_raw_reference_refuses_before_charge(GameTestHelper h)throws Exception { rawMutation(h,"tacz"); }
        @GameTest(template="townhall_state_empty",batch="native42_06",timeoutTicks=200) public static void effective_data_and_config_changes_invalidate_quote(GameTestHelper h)throws Exception { effectiveMutation(h,"tacz"); }
        @GameTest(template="townhall_state_empty",batch="native42_07",timeoutTicks=200) public static void native_purchase_charges_and_delivers_exactly_once(GameTestHelper h)throws Exception { purchase(h,"tacz"); }
        @GameTest(template="townhall_state_empty",batch="native42_08",timeoutTicks=200) public static void payment_and_consumed_delivery_recover_without_free_items(GameTestHelper h)throws Exception { interrupted(h,"tacz"); }
    }

    @GameTestHolder("catancraft_native42_sbw") @PrefixGameTestTemplate(false)
    public static final class Sbw {
        @GameTest(template="townhall_state_empty",batch="native42_01",timeoutTicks=600) public static void all_24_native_guns_are_unloaded_and_roundtrip(GameTestHelper h)throws Exception { all(h,"superbwarfare",true,24); }
        @GameTest(template="townhall_state_empty",batch="native42_02",timeoutTicks=600) public static void all_4_native_ammunition_offers_roundtrip(GameTestHelper h)throws Exception { all(h,"superbwarfare",false,4); }
        @GameTest(template="townhall_state_empty",batch="native42_03",timeoutTicks=200) public static void reserved_fake_and_changed_offers_refuse(GameTestHelper h)throws Exception { rejectedOffers(h,"superbwarfare"); }
        @GameTest(template="townhall_state_empty",batch="native42_04",timeoutTicks=200) public static void quoted_uuid_item_and_fingerprint_are_frozen(GameTestHelper h)throws Exception { frozen(h,"superbwarfare"); }
        @GameTest(template="townhall_state_empty",batch="native42_05",timeoutTicks=200) public static void changed_raw_reference_refuses_before_charge(GameTestHelper h)throws Exception { rawMutation(h,"superbwarfare"); }
        @GameTest(template="townhall_state_empty",batch="native42_06",timeoutTicks=200) public static void effective_data_and_config_changes_invalidate_quote(GameTestHelper h)throws Exception { effectiveMutation(h,"superbwarfare"); }
        @GameTest(template="townhall_state_empty",batch="native42_07",timeoutTicks=200) public static void native_purchase_charges_and_delivers_exactly_once(GameTestHelper h)throws Exception { purchase(h,"superbwarfare"); }
        @GameTest(template="townhall_state_empty",batch="native42_08",timeoutTicks=200) public static void payment_and_consumed_delivery_recover_without_free_items(GameTestHelper h)throws Exception { interrupted(h,"superbwarfare"); }
    }

    private static void all(GameTestHelper h,String provider,boolean guns,int expected)throws Exception {
        controlled(h.getLevel(),provider,false);
        CompoundTag ledger=CatanSavedData.get(h.getLevel().getServer()).save(new CompoundTag());
        var offers=WeaponStock42.offers().stream().filter(o->o.provider().equals(provider)
                &&(o.kind()==WeaponShopCatalog40.Kind.GUN)==guns).toList();
        require(offers.size()==expected,"Unexpected native stock coverage for "+provider+": "+offers.size());
        Set<UUID> unique=new java.util.HashSet<>();
        for(var offer:offers){
            NativeWeapon42.Supply supply=NativeWeapon42.quote(h.getLevel().getServer(),offer);
            require(supply.fingerprint().matches("[0-9a-f]{64}"),"Quote has no effective-state fingerprint");
            assertNative(offer,supply.item());
            CompoundTag original=supply.item().save(new CompoundTag());
            ItemStack restored=ItemStack.of(original.copy());
            require(original.equals(restored.save(new CompoundTag())),"Native item failed exact NBT roundtrip: "+offer.id());
            NativeWeapon42.recheck(h.getLevel().getServer(),offer,supply);
            require(original.equals(supply.item().save(new CompoundTag())),"Recheck altered the native quote");
            if(guns&&provider.equals("superbwarfare"))require(unique.add(gunUuid(supply.item())),"Different native guns reused a UUID");
        }
        require(ledger.equals(CatanSavedData.get(h.getLevel().getServer()).save(new CompoundTag()))
                &&h.getLevel().getServer().getPlayerList().getPlayers().isEmpty(),"Quoting granted items or changed accounting/roster");
        h.succeed();
    }

    private static void rejectedOffers(GameTestHelper h,String provider)throws Exception {
        controlled(h.getLevel(),provider,false);var offer=sample(provider,true);
        List<String> forbidden=provider.equals("tacz")?List.of("tacz:minigun","tacz:m95","tacz:unclassified_new_gun","minecraft:diamond")
                :List.of("superbwarfare:empty_gun","superbwarfare:vehicle_gun","superbwarfare:unclassified_new_gun","minecraft:diamond");
        for(String id:forbidden){
            var forged=new WeaponStock42.Offer(offer.id(),offer.title(),offer.kind(),provider,id,id,1,
                    offer.requiredLevel(),offer.fireMode(),offer.price(),offer.revision());
            refuse(()->NativeWeapon42.quote(h.getLevel().getServer(),forged));
        }
        for(var forged:List.of(
                changed(offer,offer.count()+1,offer.requiredLevel(),offer.price(),offer.revision()),
                changed(offer,offer.count(),offer.requiredLevel()+1,offer.price(),offer.revision()),
                changed(offer,offer.count(),offer.requiredLevel(),new ShopPurchase40.Price(1,Map.of()),offer.revision()),
                changed(offer,offer.count(),offer.requiredLevel(),offer.price(),"unknown-revision")))
            refuse(()->NativeWeapon42.quote(h.getLevel().getServer(),forged));
        h.succeed();
    }

    private static WeaponStock42.Offer changed(WeaponStock42.Offer o,int count,int level,ShopPurchase40.Price price,String revision){
        return new WeaponStock42.Offer(o.id(),o.title(),o.kind(),o.provider(),o.nativeId(),o.referenceGun(),count,level,o.fireMode(),price,revision);
    }

    private static void frozen(GameTestHelper h,String provider)throws Exception {
        controlled(h.getLevel(),provider,false);var offer=sample(provider,true);var server=h.getLevel().getServer();
        var quoted=NativeWeapon42.quote(server,offer);CompoundTag original=quoted.item().save(new CompoundTag());
        ItemStack leaked=quoted.item();leaked.setCount(9);leaked.getOrCreateTag().putString("ForeignPayload","must-not-enter-quote");
        require(original.equals(quoted.item().save(new CompoundTag())),"Supply leaked a mutable native stack");
        ItemStack input=quoted.item();var copied=new NativeWeapon42.Supply(input,quoted.fingerprint());input.setCount(2);
        require(original.equals(copied.item().save(new CompoundTag())),"Supply retained its caller's mutable stack");
        NativeWeapon42.recheck(server,offer,quoted);NativeWeapon42.recheck(server,offer,quoted);
        require(original.equals(quoted.item().save(new CompoundTag())),"Recheck regenerated the frozen item or UUID");
        String forgedHash=(quoted.fingerprint().charAt(0)=='0'?"1":"0")+quoted.fingerprint().substring(1);
        refuse(()->NativeWeapon42.recheck(server,offer,new NativeWeapon42.Supply(quoted.item(),forgedHash)));
        ItemStack forged=quoted.item();forged.getOrCreateTag().putString("ForeignPayload","unquoted");
        refuse(()->NativeWeapon42.recheck(server,offer,new NativeWeapon42.Supply(forged,quoted.fingerprint())));
        if(provider.equals("superbwarfare")){
            require(gunUuid(ItemStack.of(original)).equals(gunUuid(quoted.item())),"Native UUID changed through item save/of");
            ItemStack foreignUuid=quoted.item();foreignUuid.getOrCreateTag().getCompound("GunData").putUUID("UUID",UUID.randomUUID());
            refuse(()->NativeWeapon42.recheck(server,offer,new NativeWeapon42.Supply(foreignUuid,quoted.fingerprint())));
        }
        h.succeed();
    }

    private static void rawMutation(GameTestHelper h,String provider)throws Exception {
        Fixture f=new Fixture(h.getLevel(),provider,false,UUID.randomUUID(),"CC42Raw",null);
        var offer=sample(provider,true);var supply=f.quote(offer);var request=f.request(offer,supply,UUID.randomUUID());
        CompoundTag before=f.data().save(new CompoundTag());byte[] player=bytes(f.playerPath());
        try(AutoCloseable ignored=mutateRaw(offer)){
            refuse(()->NativeWeapon42.quote(f.level.getServer(),offer));
            refuse(()->NativeWeapon42.recheck(f.level.getServer(),offer,supply));
            refuse(()->ShopPurchase40.create(f,request));
            require(!Files.exists(ShopPurchase40.intentPath(f,request.id()))&&f.player.getInventory().isEmpty()
                    &&before.equals(f.data().save(new CompoundTag()))&&Arrays.equals(player,bytes(f.playerPath())),
                    "Changed reference escaped pre-intent guard or granted free native stock");
        }
        NativeWeapon42.recheck(f.level.getServer(),offer,supply);h.succeed();
    }

    private static void effectiveMutation(GameTestHelper h,String provider)throws Exception {
        controlled(h.getLevel(),provider,false);var offer=sample(provider,true);var server=h.getLevel().getServer();
        var supply=NativeWeapon42.quote(server,offer);
        if(provider.equals("superbwarfare"))unorderedNativeProperties(server,supply);
        try(AutoCloseable ignored=mutateEffective(offer)){
            refuse(()->NativeWeapon42.quote(server,offer));refuse(()->NativeWeapon42.recheck(server,offer,supply));
        }
        NativeWeapon42.recheck(server,offer,supply);
        try(AutoCloseable ignored=mutateConfig(provider)){
            // Config variants may remain independently valid, but cannot authorize the old quote.
            refuse(()->NativeWeapon42.recheck(server,offer,supply));
        }
        NativeWeapon42.recheck(server,offer,supply);h.succeed();
    }

    /** Use real native computed values, then a real quote over the native multi-member
     * reload-type Set. This distinguishes incidental registry/Set iteration from
     * changed membership, duplicate multiplicity, and ordered firing-mode lists. */
    private static void unorderedNativeProperties(MinecraftServer server,NativeWeapon42.Supply supply)throws Exception {
        Class<?> dataClass=Class.forName("com.atsuishio.superbwarfare.data.gun.GunData"),propClass=Class.forName("com.atsuishio.superbwarfare.data.gun.GunProp");
        Object data=dataClass.getMethod("from",ItemStack.class).invoke(null,supply.item());
        var get=dataClass.getMethod("get",propClass);
        List<?> nativePerks=(List<?>)get.invoke(data,propClass.getField("AVAILABLE_PERKS").get(null));
        require(nativePerks.size()>1,"Regression requires several actual registry-expanded native perks");
        var serializePerks=NativeSbw42.class.getDeclaredMethod("perks",Object.class);
        require(serializePerks.trySetAccessible(),"Cannot inspect native available-perk fingerprint boundary");
        List<Object> reversedPerks=new ArrayList<>(nativePerks);Collections.reverse(reversedPerks);
        require(!reversedPerks.equals(nativePerks),"Native perk reordering fixture did not alter enumeration order");
        String originalPerks=NativeWeapon42.canonical((JsonElement)serializePerks.invoke(null,nativePerks));
        require(originalPerks.equals(NativeWeapon42.canonical((JsonElement)serializePerks.invoke(null,reversedPerks))),
                "Semantically unordered native perk enumeration changed its fingerprint input");
        List<Object> missingPerk=new ArrayList<>(nativePerks);missingPerk.remove(0);
        List<Object> duplicatePerk=new ArrayList<>(nativePerks);duplicatePerk.add(nativePerks.get(0));
        require(!originalPerks.equals(NativeWeapon42.canonical((JsonElement)serializePerks.invoke(null,missingPerk)))
                &&!originalPerks.equals(NativeWeapon42.canonical((JsonElement)serializePerks.invoke(null,duplicatePerk))),
                "Native perk fingerprint discarded membership or duplicate multiplicity");

        List<?> nativeModes=(List<?>)get.invoke(data,propClass.getField("AVAILABLE_FIRE_MODES").get(null));
        require(nativeModes.size()>1,"Regression requires actual multiple native firing modes");
        List<Object> reversedModes=new ArrayList<>(nativeModes);Collections.reverse(reversedModes);
        Gson gson=(Gson)Class.forName("com.atsuishio.superbwarfare.data.DataLoader").getField("GSON").get(null);
        var serializeProperty=NativeSbw42.class.getDeclaredMethod("json",Object.class,Gson.class);
        require(serializeProperty.trySetAccessible(),"Cannot inspect native property fingerprint boundary");
        require(!NativeWeapon42.canonical((JsonElement)serializeProperty.invoke(null,nativeModes,gson))
                .equals(NativeWeapon42.canonical((JsonElement)serializeProperty.invoke(null,reversedModes,gson))),
                "Native ordered firing-mode lists were incorrectly normalized as sets");

        WeaponStock42.Offer setOffer=WeaponStock42.offers().stream().filter(o->o.kind()==WeaponShopCatalog40.Kind.GUN
                &&o.nativeId().equals("superbwarfare:k_98")).findFirst().orElseThrow();
        NativeWeapon42.Supply setSupply=NativeWeapon42.quote(server,setOffer);CompoundTag frozen=setSupply.item().save(new CompoundTag());
        Object raw=sbwData().get(setOffer.referenceGun());Set<?> original=(Set<?>)call(raw,"getReloadTypes");
        require(original.size()>1,"Native k_98 reload-type Set lacks the qualified multiple members");
        List<Object> order=new ArrayList<>(original);Collections.reverse(order);Set<Object> reversed=new LinkedHashSet<>(order);
        require(reversed.equals(original)&&!new ArrayList<>(reversed).equals(new ArrayList<>(original)),"Native Set iteration fixture did not preserve membership while reversing order");
        var setReloadTypes=raw.getClass().getMethod("setReloadTypes",Set.class);
        try{
            setReloadTypes.invoke(raw,reversed);
            NativeWeapon42.recheck(server,setOffer,setSupply);
            require(frozen.equals(setSupply.item().save(new CompoundTag())),"Set-order-only recheck changed original native item or UUID");
            Set<Object> changed=new LinkedHashSet<>(reversed);changed.remove(order.get(0));setReloadTypes.invoke(raw,changed);
            refuse(()->NativeWeapon42.quote(server,setOffer));
            refuse(()->NativeWeapon42.recheck(server,setOffer,setSupply));
        }finally{setReloadTypes.invoke(raw,original);}
        require(call(raw,"getReloadTypes")==original,"Native reload-type mutation did not restore the exact original Set");
        NativeWeapon42.recheck(server,setOffer,setSupply);
    }

    private static void purchase(GameTestHelper h,String provider)throws Exception {
        for(boolean gun:List.of(true,false)){
            Fixture f=new Fixture(h.getLevel(),provider,false,UUID.randomUUID(),gun?"CC42BuyGun":"CC42BuyAmmo",null);
            var offer=sample(provider,gun);var supply=f.quote(offer);UUID id=UUID.randomUUID();
            f.player.getInventory().setItem(12,new ItemStack(Items.BOOK,2));f.persistPlayer();
            var request=f.request(offer,supply,id);ShopPurchase40.create(f,request);byte[] intent=bytes(ShopPurchase40.intentPath(f,id));
            require(f.nation.treasury()==FUNDS&&f.player.getInventory().getItem(0).isEmpty(),"Preparing quote charged or delivered");
            require(ShopPurchase40.resume(f,id).complete(),"Native purchase did not complete");
            f.assertPaid(offer,id,intent);f.assertDelivered(offer,supply,id,1);
            require(f.player.getInventory().getItem(12).is(Items.BOOK)&&f.player.getInventory().getItem(12).getCount()==2,
                    "Native purchase overwrote unrelated inventory");
            inert(f,id,intent);
        }
        h.succeed();
    }

    private static void interrupted(GameTestHelper h,String provider)throws Exception {
        Fixture f=new Fixture(h.getLevel(),provider,false,UUID.randomUUID(),"CC42Paid",null);
        var offer=sample(provider,true);var supply=f.quote(offer);UUID id=UUID.randomUUID();
        ShopPurchase40.create(f,f.request(offer,supply,id));byte[] intent=bytes(ShopPurchase40.intentPath(f,id));
        interruptAt(f,id,ShopPurchase40.SavePoint.AFTER_LEDGER_SAVE);
        f.assertPaid(offer,id,intent);require(f.player.getInventory().isEmpty(),"Payment interruption granted native gun/ammo");
        interruptAt(f,id,ShopPurchase40.SavePoint.AFTER_PLAYER_SAVE);f.assertDelivered(offer,supply,id,1);
        f.player.getInventory().setItem(0,ItemStack.EMPTY);f.persistPlayer();
        int playerSaves=f.playerSaves;require(ShopPurchase40.resume(f,id).complete(),"Consumed native delivery did not recover");
        require(f.playerSaves==playerSaves,"Consumed native delivery attempted another player insertion/save");
        f.assertPaid(offer,id,intent);f.assertDelivered(offer,supply,id,0);inert(f,id,intent);h.succeed();
    }

    static WeaponStock42.Offer sample(String provider,boolean gun)throws IOException {
        String chosen=provider.equals("tacz")?(gun?"tacz:glock_17":"tacz:9mm"):(gun?"superbwarfare:ak_47":"superbwarfare:rifle_ammo");
        return WeaponStock42.offers().stream().filter(o->o.nativeId().equals(chosen)
                &&(o.kind()==WeaponShopCatalog40.Kind.GUN)==gun).findFirst().orElseThrow(()->new IOException("Missing native fixture offer "+chosen));
    }

    static void assertNative(WeaponStock42.Offer offer,ItemStack stack)throws Exception {
        require(!stack.isEmpty()&&stack.getCount()==offer.count(),"Wrong native stack count: "+offer.id());
        if(offer.provider().equals("tacz")){
            boolean gun=offer.kind()==WeaponShopCatalog40.Kind.GUN;
            Class<?> api=Class.forName("com.tacz.guns.api.item."+(gun?"IGun":"IAmmo"));
            require(api.isInstance(stack.getItem())&&new ResourceLocation(offer.nativeId()).equals(api.getMethod(gun?"getGunId":"getAmmoId",ItemStack.class).invoke(stack.getItem(),stack)),"Wrong native TaCZ identity");
            if(gun)require(((Number)api.getMethod("getCurrentAmmoCount",ItemStack.class).invoke(stack.getItem(),stack)).intValue()==0
                    &&Boolean.FALSE.equals(api.getMethod("hasBulletInBarrel",ItemStack.class).invoke(stack.getItem(),stack))
                    &&offer.fireMode().equals(((Enum<?>)api.getMethod("getFireMode",ItemStack.class).invoke(stack.getItem(),stack)).name()),"TaCZ gun contains unintended ammunition or mode");
        }else{
            require(new ResourceLocation(offer.nativeId()).equals(ForgeRegistries.ITEMS.getKey(stack.getItem())),"Wrong SBW registry identity");
            if(offer.kind()==WeaponShopCatalog40.Kind.GUN){
                gunUuid(stack);CompoundTag data=stack.getOrCreateTag().getCompound("GunData");
                require(data.getAllKeys().equals(Set.of("UUID"))&&stack.getOrCreateTag().getCompound("Attachments").isEmpty()&&stack.getOrCreateTag().getCompound("Perks").isEmpty(),"SBW quote has foreign overrides/perks/attachments/ammunition slots");
                Object nativeData=Class.forName("com.atsuishio.superbwarfare.data.gun.GunData").getMethod("from",ItemStack.class).invoke(null,stack);
                require(Boolean.TRUE.equals(call(nativeData,"initialized")),"SBW gun was not initialized natively");
                for(String field:List.of("ammo","virtualAmmo","backupAmmoCount"))require(((Number)call(field(nativeData.getClass(),field).get(nativeData),"get")).intValue()==0,"SBW gun contains unintended "+field);
            }else{
                require(stack.getItem().getClass().getName().equals("com.atsuishio.superbwarfare.item.ammo.AmmoSupplierItem")
                        &&((Number)call(stack.getItem(),"getAmmoToAdd")).intValue()==1&&!stack.hasTag(),"SBW ammunition has wrong native supplier amount/state");
            }
        }
    }

    static UUID gunUuid(ItemStack stack){CompoundTag data=stack.getOrCreateTag().getCompound("GunData");require(data.hasUUID("UUID"),"SBW native gun has no UUID");return data.getUUID("UUID");}

    private static AutoCloseable mutateRaw(WeaponStock42.Offer offer)throws Exception {
        if(offer.provider().equals("superbwarfare")){
            Map<String,Object> map=sbwData();Object original=map.remove(offer.referenceGun());
            require(original!=null,"Missing actual SBW reference data mutation target");return ()->map.put(offer.referenceGun(),original);
        }
        Class<?> assets=Class.forName("com.tacz.guns.resource.CommonAssetsManager");Object manager=field(assets,"gunData").get(assets.getMethod("getInstance").invoke(null));
        Field cache=field(manager.getClass(),"networkCache");Object original=cache.get(manager);
        @SuppressWarnings("unchecked") Map<ResourceLocation,String> old=(Map<ResourceLocation,String>)original;
        ResourceLocation id=new ResourceLocation(WeaponTierCatalog41.entry(offer.referenceGun()).dataId());
        JsonObject raw=JsonParser.parseString(old.get(id)).getAsJsonObject();raw.addProperty("ammo_amount",raw.get("ammo_amount").getAsInt()+1);
        Map<ResourceLocation,String> altered=new HashMap<>(old);altered.put(id,raw.toString());cache.set(manager,Map.copyOf(altered));
        return ()->cache.set(manager,original);
    }

    private static AutoCloseable mutateEffective(WeaponStock42.Offer offer)throws Exception {
        if(offer.provider().equals("superbwarfare")){
            Object data=sbwData().get(offer.referenceGun());int old=((Number)call(data,"getMagazine")).intValue();
            data.getClass().getMethod("setMagazine",int.class).invoke(data,old+1);
            return ()->data.getClass().getMethod("setMagazine",int.class).invoke(data,old);
        }
        Class<?> api=Class.forName("com.tacz.guns.api.TimelessAPI");Object index=((Optional<?>)api.getMethod("getCommonGunIndex",ResourceLocation.class).invoke(null,new ResourceLocation(offer.referenceGun()))).orElseThrow();
        Object data=call(index,"getGunData");Field rpm=field(data.getClass(),"roundsPerMinute");Object old=rpm.get(data);rpm.set(data,((Number)old).intValue()+1);
        return ()->rpm.set(data,old);
    }

    @SuppressWarnings("unchecked") private static Map<String,Object> sbwData()throws Exception {
        return (Map<String,Object>)Class.forName("com.atsuishio.superbwarfare.data.CustomData").getField("GUN_DATA").get(null);
    }

    private static AutoCloseable mutateConfig(String provider)throws Exception {
        if(provider.equals("superbwarfare")){
            var value=(net.minecraftforge.common.ForgeConfigSpec.BooleanValue)Class.forName("com.atsuishio.superbwarfare.config.server.ProjectileConfig").getField("PROJECTILE_CHUNK_LOADING").get(null);
            boolean original=value.get();value.set(!original);return ()->value.set(original);
        }
        if(provider.equals("tacz")){
            // Mutate native effective configuration, not the raw TOML beneath the
            // server's protected terrain overlay. This setting is outside that policy.
            var value=(net.minecraftforge.common.ForgeConfigSpec.BooleanValue)Class.forName("com.tacz.guns.config.common.AmmoConfig").getField("IGNORE_TEAM_FRIENDLY_FIRE").get(null);
            boolean original=value.get();value.set(!original);return ()->value.set(original);
        }
        throw new AssertionError("No qualified native configuration mutation target for "+provider);
    }

    static Object call(Object target,String name)throws Exception{return target.getClass().getMethod(name).invoke(target);}
    static Field field(Class<?> type,String name)throws NoSuchFieldException {
        for(Class<?> current=type;current!=null;current=current.getSuperclass())try{Field field=current.getDeclaredField(name);if(!field.trySetAccessible())throw new NoSuchFieldException(name);return field;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(type.getName()+"."+name);
    }
    interface Attempt{void run()throws Exception;}
    static void refuse(Attempt attempt)throws Exception{try{attempt.run();}catch(IOException expected){return;}throw new AssertionError("Expected explicit native supply refusal");}
    static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static byte[] bytes(Path path)throws IOException{if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(path)||Files.size(path)>8*1024*1024+40)throw new IOException("Missing bounded native evidence: "+path.getFileName());return Files.readAllBytes(path);}
    static CompoundTag nativeNbt(byte[] bytes)throws IOException{try(var in=new ByteArrayInputStream(bytes)){return NbtIo.readCompressed(in);}}
    static CompoundTag receipts(CompoundTag player){return player.getCompound("ForgeData").getCompound(Player.PERSISTED_NBT_TAG).getCompound(ShopPurchase40.RECEIPTS);}
    static String namespace(String provider,boolean restart){return "catancraft_native42_"+(provider.equals("tacz")?"tacz":"sbw")+(restart?"_restart":"");}
    static Path controlled(ServerLevel level,String provider,boolean restart)throws IOException{
        Path root=level.getServer().getWorldPath(LevelResource.ROOT).toRealPath();
        if(!(level.getServer() instanceof GameTestServer)||!level.getServer().isSameThread()||!level.getServer().getPlayerList().getPlayers().isEmpty()
                ||!root.getFileName().toString().equals("world")||!root.getParent().getFileName().toString().equals("controlled-"+namespace(provider,restart)+"-run")
                ||!root.getParent().getParent().getFileName().toString().equals("build")
                ||!Files.readString(root.getParent().resolve(".catancraft-controlled-fixture-v1")).equals("CatanCraft synthetic flat fixture v1\n")
                ||!level.dimension().location().toString().equals("minecraft:overworld")||level.getSeed()!=0)
            throw new IOException("Native42 requires its empty marked disposable provider GameTest world");
        return root;
    }

    static void interruptAt(Fixture f,UUID id,ShopPurchase40.SavePoint wanted)throws IOException{
        IOException interruption=new IOException("Intentional native42 interruption at "+wanted);
        try{ShopPurchase40.fixtureResume(f,id,point->{if(point==wanted)throw interruption;});}catch(IOException failure){if(failure==interruption)return;throw failure;}
        throw new AssertionError("Native transaction did not reach "+wanted);
    }

    static void inert(Fixture f,UUID id,byte[] original)throws Exception{
        byte[] player=bytes(f.playerPath()),ledger=bytes(f.root.resolve("data/catancraft.dat")),completion=bytes(ShopPurchase40.receiptPath(f,id));
        CompoundTag memory=f.player.saveWithoutId(new CompoundTag()),account=f.data().save(new CompoundTag());int saves=f.playerSaves+f.ledgerSaves;
        for(int retry=0;retry<3;retry++)require(ShopPurchase40.resume(f,id).complete(),"Completed native retry failed");
        require(saves==f.playerSaves+f.ledgerSaves&&memory.equals(f.player.saveWithoutId(new CompoundTag()))&&account.equals(f.data().save(new CompoundTag()))
                &&Arrays.equals(player,bytes(f.playerPath()))&&Arrays.equals(ledger,bytes(f.root.resolve("data/catancraft.dat")))
                &&Arrays.equals(completion,bytes(ShopPurchase40.receiptPath(f,id)))&&Arrays.equals(original,bytes(ShopPurchase40.intentPath(f,id)))
                &&ShopPurchase40.pending(f).isEmpty(),"Native retry changed original history/player/ledger or repeated a save");
    }

    static final class Fixture implements ShopPurchase40.Environment {
        final ServerLevel level;final String provider;final boolean restart;final Path root;final FakePlayer player;final NationData nation;final CompoundTag facility;
        WeaponStock42.Offer offer;NativeWeapon42.Supply supply;int ledgerSaves,playerSaves;
        Fixture(ServerLevel level,String provider,boolean restart,UUID actor,String name,NationData originalNation)throws Exception{
            this.level=level;this.provider=provider;this.restart=restart;root=controlled(level,provider,restart);
            player=new FakePlayer(level,new GameProfile(actor,name));player.setGameMode(GameType.SURVIVAL);player.setHealth(20);player.setPos(0.5,140,0.5);
            if(originalNation==null){
                require(!Files.exists(playerPath()),"New fixture refuses existing native actor file");
                player.getInventory().clearContent();nation=data().createNation(name+"_"+actor,actor);nation.setTreasury(FUNDS);
                for(ResourceType resource:ResourceType.values())nation.addResource(resource,STOCK);
            }else{
                nation=originalNation;CompoundTag original=nativeNbt(bytes(playerPath()));
                require(original.hasUUID("UUID")&&original.getUUID("UUID").equals(actor),"Original native actor identity differs");player.load(original);
            }
            facility=new CompoundTag();facility.putString("id",FIXTURE+":"+provider);facility.putUUID("owner",nation.id());
            if(originalNation==null){persistLedger();persistPlayer();}
        }
        public ServerPlayer player(){return player;}public CatanSavedData data(){return CatanSavedData.get(level.getServer());}
        NativeWeapon42.Supply quote(WeaponStock42.Offer selected)throws IOException{offer=selected;supply=NativeWeapon42.quote(level.getServer(),selected);return supply;}
        ShopPurchase40.Request request(WeaponStock42.Offer selected,NativeWeapon42.Supply quoted,UUID id)throws IOException{offer=selected;supply=quoted;return new ShopPurchase40.Request(id,selected.id(),NativeWeapon42.frozenCatalog(selected,quoted),facility,selected.price(),quoted.item());}
        void restore(WeaponStock42.Offer selected,NativeWeapon42.Supply quoted){offer=selected;supply=quoted;}
        public void guard(CompoundTag original)throws IOException{
            if(!root.equals(controlled(level,provider,restart))||!facility.equals(original)||player.serverLevel()!=level
                    ||data().nation(nation.id())!=nation||data().nationForPlayer(player.getUUID())!=nation||!nation.isLeader(player.getUUID()))throw new IOException("Native fixture identity/facility changed");
            if(offer!=null)NativeWeapon42.recheck(level.getServer(),offer,supply);
        }
        public void persistLedger()throws IOException{ledgerSaves++;data().setDirty();level.getServer().overworld().getDataStorage().save();}
        public void persistPlayer()throws IOException{playerSaves++;ShopNativePlayerFixture40.save(player);}
        Path playerPath(){return root.resolve("playerdata/"+player.getUUID()+".dat");}
        void assertPaid(WeaponStock42.Offer selected,UUID id,byte[] intent)throws IOException{
            require(nation.treasury()==FUNDS-selected.price().money(),"Native money charge differs from exact quoted price");
            for(ResourceType resource:ResourceType.values())require(nation.resource(resource)==STOCK-selected.price().resources().getOrDefault(resource,0L),"Native resource charge differs: "+resource);
            require(TownHallRevisions.sha(intent).equals(data().placedMapAsset("shop40_paid:"+id)),"Native debit receipt is not bound to frozen intent");
            require(nativeNbt(bytes(root.resolve("data/catancraft.dat"))).getCompound("data").equals(data().save(new CompoundTag())),"Native SavedData file differs from ledger");
        }
        void assertDelivered(WeaponStock42.Offer selected,NativeWeapon42.Supply quoted,UUID id,int expected)throws Exception{
            CompoundTag disk=nativeNbt(bytes(playerPath())),memory=player.saveWithoutId(new CompoundTag());ListTag inventory=disk.getList("Inventory",Tag.TAG_COMPOUND);
            int nativeStacks=0;for(int i=0;i<inventory.size();i++){ItemStack item=ItemStack.of(inventory.getCompound(i));
                if(item.is(Items.BOOK))continue;require(ItemStack.matches(item,quoted.item()),"Native delivery granted an extra gun/ammo or changed full quoted NBT");assertNative(selected,item);nativeStacks++;}
            String hash=TownHallRevisions.sha(bytes(ShopPurchase40.intentPath(this,id)));
            require(nativeStacks==expected&&disk.hasUUID("UUID")&&disk.getUUID("UUID").equals(player.getUUID())
                    &&inventory.equals(memory.getList("Inventory",Tag.TAG_COMPOUND))&&hash.equals(receipts(disk).getString(id.toString()))
                    &&receipts(disk).equals(receipts(memory)),"Native player inventory/receipt did not share exact saved delivery");
        }
    }
}
