package com.quin.catancraft.world;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.*;
import com.quin.catancraft.monument.MonumentManager;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import static com.quin.catancraft.world.NativeWeapon42Tests.bytes;
import static com.quin.catancraft.world.NativeWeapon42Tests.nativeNbt;
import static com.quin.catancraft.world.NativeWeapon42Tests.require;

/** Controlled equipment claims from actual settled monument records and actual
 * native player storage. Vanilla payloads are explicitly synthetic; provider
 * namespaces use NativeReward43 and real published native items throughout. */
@GameTestHolder("catancraft_claims43") @PrefixGameTestTemplate(false)
public final class RewardClaim43Tests {
    static final String FIXTURE="controlled_claim43_emerald_v1";
    static final long FUNDS=10_000,STOCK=100;
    static final RewardTable41.Entitlement SYNTHETIC=new RewardTable41.Entitlement(RewardTable41.Kind.AMMO,"minecraft:emerald",3,FIXTURE);
    static final RewardClaim43.Fault NONE=point->{};
    private RewardClaim43Tests(){}

    @GameTest(template="townhall_state_empty",batch="claims43_01",timeoutTicks=200)
    public static void settled_equipment_claims_once_without_charge_and_retries_are_inert(GameTestHelper h)throws Exception{
        Fixture f=fresh(h);f.settle();var selection=f.selection(0);var supply=f.quote(0);
        require(RewardClaim43.fixtureList(f).stream().anyMatch(v->v.selection().equals(selection)&&v.status().equals("AVAILABLE")),"Settled entitlement was not available to its winning leader");
        byte[] source=bytes(f.sourceIntent());CompoundTag ledger=f.ledger();
        require(f.prepare(0,supply,NONE).equals(selection)&&f.count(supply.item())==0,"Preparation delivered before a durable claim");
        byte[] intent=bytes(RewardClaim43.intentPath(f,selection));
        require(f.claim(0,NONE).complete(),"Original equipment claim did not complete");
        f.assertDelivered(selection,supply,1);f.assertUnchangedLedger(ledger);inert(f,selection,intent,source);h.succeed();
    }

    @GameTest(template="townhall_state_empty",batch="claims43_02",timeoutTicks=200)
    public static void full_inventory_retains_unclaimed_entitlement_and_never_overwrites_items(GameTestHelper h)throws Exception{
        Fixture f=fresh(h);f.settle();for(int i=0;i<36;i++)f.player.getInventory().setItem(i,new ItemStack(Items.STONE,64));f.persistPlayer();
        CompoundTag ledger=f.ledger();byte[] player=bytes(f.playerPath());var supply=f.quote(0);
        refuse(()->f.prepare(0,supply,NONE));
        require(!Files.exists(RewardClaim43.intentPath(f,f.selection(0)))&&Arrays.equals(player,bytes(f.playerPath())),"Full inventory reserved or overwrote native items");
        f.player.getInventory().setItem(0,ItemStack.EMPTY);f.persistPlayer();f.prepare(0,supply,NONE);require(f.claim(0,NONE).complete(),"Freed native slot could not receive retained entitlement");
        f.assertDelivered(f.selection(0),supply,1);for(int i=1;i<36;i++)require(f.player.getInventory().getItem(i).is(Items.STONE)&&f.player.getInventory().getItem(i).getCount()==64,"Claim overwrote protected full-inventory stack");
        f.assertUnchangedLedger(ledger);h.succeed();
    }

    @GameTest(template="townhall_state_empty",batch="claims43_03",timeoutTicks=200)
    public static void wrong_nation_and_nonleader_cannot_adopt_winning_entitlements(GameTestHelper h)throws Exception{
        Fixture f=fresh(h);f.settle();FakePlayer winner=f.player;var supply=f.quote(0);
        FakePlayer foreign=f.actor(UUID.randomUUID(),"CC43Foreign");f.data().createNation("Claim43Other_"+foreign.getUUID(),foreign.getUUID());
        FakePlayer member=f.actor(UUID.randomUUID(),"CC43Member");f.nation.addMember(member.getUUID());f.persist();CompoundTag ledger=f.ledger();
        for(FakePlayer unauthorized:List.of(foreign,member)){
            f.player=unauthorized;f.persistPlayer();refuse(()->f.prepare(0,supply,NONE));refuse(()->f.claim(0,NONE));
            require(f.player.getInventory().isEmpty(),"Unauthorized actor received native equipment");
        }
        f.player=winner;require(!Files.exists(RewardClaim43.intentPath(f,f.selection(0))),"Unauthorized actor reserved the winning entitlement");
        f.assertUnchangedLedger(ledger);f.prepare(0,supply,NONE);require(f.claim(0,NONE).complete(),"Winning leader lost retained entitlement");h.succeed();
    }

    @GameTest(template="townhall_state_empty",batch="claims43_04",timeoutTicks=200)
    public static void forged_indices_unknown_rewards_and_unsettled_captures_refuse(GameTestHelper h)throws Exception{
        Fixture f=fresh(h);f.prepareSource();var supply=f.quote(0);
        refuse(()->f.prepare(0,supply,NONE));
        refuse(()->RewardClaim43.fixturePrepare(f,new RewardClaim43.Selection(UUID.randomUUID(),0),f.facility,supply,f.access,NONE));
        f.arm();interruptCapture(f,MonumentRewards41.SavePoint.AFTER_INTENT);byte[] source=bytes(f.sourceIntent());
        refuse(()->f.prepare(0,supply,NONE));require(f.player.getInventory().isEmpty(),"Unsettled reward yielded equipment");
        MonumentRewards41.fixtureRecover(f,point->{});CompoundTag ledger=f.ledger();
        refuse(()->f.prepare(1,supply,NONE));
        require(!Files.exists(RewardClaim43.intentPath(f,f.selection(0)))&&Arrays.equals(source,bytes(f.sourceIntent())),"Refused reward selection rewrote source or reserved valid entry");
        f.assertUnchangedLedger(ledger);h.succeed();
    }

    @GameTest(template="townhall_state_empty",batch="claims43_05",timeoutTicks=200)
    public static void competing_leader_cannot_rebind_an_original_reserved_claim(GameTestHelper h)throws Exception{
        Fixture f=fresh(h);f.settle();var supply=f.quote(0);f.prepare(0,supply,NONE);var selection=f.selection(0);byte[] intent=bytes(RewardClaim43.intentPath(f,selection));
        FakePlayer original=f.player,replacement=f.actor(UUID.randomUUID(),"CC43NextLeader");f.nation.addMember(replacement.getUUID());
        Field leader=NationData.class.getDeclaredField("leaderId");require(leader.trySetAccessible(),"Cannot stage controlled leader transfer");
        leader.set(f.nation,replacement.getUUID());f.player=replacement;f.persist();f.persistPlayer();
        try{refuse(()->f.prepare(0,supply,NONE));refuse(()->f.claim(0,NONE));require(f.player.getInventory().isEmpty(),"Competing leader stole reserved equipment");}
        finally{leader.set(f.nation,original.getUUID());f.player=original;f.persist();}
        require(Arrays.equals(intent,bytes(RewardClaim43.intentPath(f,selection))),"Competing leader rewrote original actor reservation");
        require(f.claim(0,NONE).complete(),"Original reserved leader could not recover");f.assertDelivered(selection,supply,1);h.succeed();
    }

    @GameTest(template="townhall_state_empty",batch="claims43_06",timeoutTicks=200)
    public static void silent_and_throwing_player_save_failures_do_not_release_free_items(GameTestHelper h)throws Exception{
        for(boolean throwing:List.of(false,true)){
            Fixture f=fresh(h);f.settle();var supply=f.quote(0);f.prepare(0,supply,NONE);CompoundTag ledger=f.ledger();
            f.suppressPlayer=!throwing;f.throwBeforePlayer=throwing;refuse(()->f.claim(0,NONE));
            require(f.player.getInventory().isEmpty()&&f.receipts().isEmpty()&&receipts(nativeNbt(bytes(f.playerPath()))).isEmpty(),"Unverified player save released a usable reward or delivery receipt");
            f.suppressPlayer=false;f.throwBeforePlayer=false;require(f.claim(0,NONE).complete(),"Clean failed save could not recover original equipment");
            f.assertDelivered(f.selection(0),supply,1);f.assertUnchangedLedger(ledger);
        }h.succeed();
    }

    @GameTest(template="townhall_state_empty",batch="claims43_07",timeoutTicks=200)
    public static void callback_failure_after_actual_player_save_retains_one_native_delivery(GameTestHelper h)throws Exception{
        Fixture f=fresh(h);f.settle();var supply=f.quote(0);f.prepare(0,supply,NONE);CompoundTag ledger=f.ledger();f.throwAfterPlayer=true;
        require(f.claim(0,NONE).complete(),"Verified native delivery was discarded after a callback exception");f.throwAfterPlayer=false;
        f.assertDelivered(f.selection(0),supply,1);f.assertUnchangedLedger(ledger);
        inert(f,f.selection(0),bytes(RewardClaim43.intentPath(f,f.selection(0))),bytes(f.sourceIntent()));h.succeed();
    }

    @GameTest(template="townhall_state_empty",batch="claims43_08",timeoutTicks=200)
    public static void consumed_delivered_item_is_not_regranted_during_recovery(GameTestHelper h)throws Exception{
        Fixture f=fresh(h);f.settle();var supply=f.quote(0);f.prepare(0,supply,NONE);var selection=f.selection(0);
        interruptClaim(f,0,RewardClaim43.SavePoint.AFTER_PLAYER_SAVE);f.assertDelivered(selection,supply,1);
        f.player.getInventory().setItem(0,ItemStack.EMPTY);f.persistPlayer();int saves=f.playerSaves;CompoundTag ledger=f.ledger();
        require(f.claim(0,NONE).complete()&&f.playerSaves==saves,"Consumed delivery caused another native player save/insertion");
        f.assertDelivered(selection,supply,0);f.assertUnchangedLedger(ledger);inert(f,selection,bytes(RewardClaim43.intentPath(f,selection)),bytes(f.sourceIntent()));h.succeed();
    }

    @GameTest(template="townhall_state_empty",batch="claims43_09",timeoutTicks=200)
    public static void corrupted_source_claim_and_completion_history_are_retained_and_refused(GameTestHelper h)throws Exception{
        Fixture f=fresh(h);f.settle();var supply=f.quote(0);CompoundTag ledger=f.ledger();byte[] source=bytes(f.sourceIntent());
        malformedPlayerIdentities(f,supply);
        corruptAndRestore(f.sourceIntent(),()->f.prepare(0,supply,NONE));
        require(!Files.exists(RewardClaim43.intentPath(f,f.selection(0))),"Corrupt source authorized reservation");
        f.prepare(0,supply,NONE);Path intent=RewardClaim43.intentPath(f,f.selection(0));corruptAndRestore(intent,()->f.claim(0,NONE));
        require(f.player.getInventory().isEmpty(),"Corrupt claim history authorized delivery");require(f.claim(0,NONE).complete(),"Preserved original claim could not finish");
        byte[] player=bytes(f.playerPath());corruptAndRestore(RewardClaim43.receiptPath(f,f.selection(0)),()->f.claim(0,NONE));
        require(Arrays.equals(player,bytes(f.playerPath()))&&Arrays.equals(source,bytes(f.sourceIntent())),"Corrupt completion repair rewrote player or original reward");
        f.assertDelivered(f.selection(0),supply,1);f.assertUnchangedLedger(ledger);h.succeed();
    }

    @GameTest(template="townhall_state_empty",batch="claims43_10",timeoutTicks=200)
    public static void changed_item_saved_with_delivery_receipt_is_held_without_redelivery(GameTestHelper h)throws Exception{
        Fixture f=fresh(h);f.settle();var supply=f.quote(0);f.prepare(0,supply,NONE);var selection=f.selection(0);CompoundTag ledger=f.ledger();
        ItemStack foreign=new ItemStack(Items.DIAMOND,2);boolean[] changed={false};
        f.beforePlayer=()->{if(!changed[0]&&f.receipts().contains(RewardClaim43.claimId(selection).toString())){changed[0]=true;f.player.getInventory().setItem(0,foreign.copy());}};
        refuse(()->f.claim(0,NONE));f.beforePlayer=null;require(changed[0],"Native fixture did not reach staged delivery save");
        require(ProducerJournal.read(bytes(RewardClaim43.receiptPath(f,selection))).getString("phase").equals("HELD"),"Ambiguous native saved item was not retained in HELD state");
        byte[] player=bytes(f.playerPath()),held=bytes(RewardClaim43.receiptPath(f,selection));refuse(()->f.claim(0,NONE));
        require(f.count(supply.item())==0&&ItemStack.matches(foreign,f.player.getInventory().getItem(0))&&Arrays.equals(player,bytes(f.playerPath()))
                &&Arrays.equals(held,bytes(RewardClaim43.receiptPath(f,selection))),"Held native delivery was overwritten or regranted");
        f.assertUnchangedLedger(ledger);h.succeed();
    }

    @GameTestHolder("catancraft_claims43_tacz") @PrefixGameTestTemplate(false)
    public static final class Tacz{
        @GameTest(template="townhall_state_empty",batch="claims43_native_01",timeoutTicks=600) public static void all_native_tacz_reward_supplies_roundtrip_and_rare_guns_stay_off_counter(GameTestHelper h)throws Exception{nativeCoverage(h,"tacz");}
        @GameTest(template="townhall_state_empty",batch="claims43_native_02",timeoutTicks=200) public static void real_tacz_gun_and_ammunition_claim_once_without_shop_debit(GameTestHelper h)throws Exception{nativeClaims(h,"tacz");}
    }
    @GameTestHolder("catancraft_claims43_sbw") @PrefixGameTestTemplate(false)
    public static final class Sbw{
        @GameTest(template="townhall_state_empty",batch="claims43_native_01",timeoutTicks=600) public static void historical_sbw_identities_remain_readable_but_current_rewards_refuse(GameTestHelper h)throws Exception{nativeCoverage(h,"superbwarfare");}
        @GameTest(template="townhall_state_empty",batch="claims43_native_02",timeoutTicks=200) public static void historical_sbw_gun_and_ammo_claims_are_retained_without_delivery(GameTestHelper h)throws Exception{nativeClaims(h,"superbwarfare");}
    }

    private static void nativeCoverage(GameTestHelper h,String provider)throws Exception{
        controlled(h.getLevel(),namespace(provider,false));CompoundTag before=CatanSavedData.get(h.getLevel().getServer()).save(new CompoundTag());
        frozenStock42(h.getLevel().getServer(),provider);
        List<RewardTable41.Entitlement> all=NativeReward43.entitlements().stream().filter(e->e.objectId().startsWith(provider+":")).toList();
        require(all.size()==(provider.equals("tacz")?72:28),"Native production entitlement coverage changed: "+all.size());
        for(var entitlement:all)try(var lease=NativeWeapon42.transaction(h.getLevel().getServer())){
            if(provider.equals("tacz")){var supply=NativeReward43.quote(h.getLevel().getServer(),entitlement);assertNative(entitlement,supply);NativeReward43.recheck(h.getLevel().getServer(),entitlement,supply);}
            else {var offer=historicalOffer(entitlement);var supply=NativeWeapon42.quote(h.getLevel().getServer(),offer);assertNative(entitlement,supply);
                refuse(()->NativeReward43.quote(h.getLevel().getServer(),entitlement));refuse(()->NativeReward43.recheck(h.getLevel().getServer(),entitlement,supply));}
        }
        if(provider.equals("tacz"))for(String rare:List.of("tacz:scar_h","tacz:scar_mk20","tacz:deagle_golden"))
            require(all.stream().anyMatch(e->e.kind()==RewardTable41.Kind.GUN&&e.objectId().equals(rare))&&WeaponStock42.offers().stream().noneMatch(o->o.nativeId().equals(rare)),"Rare monument entitlement leaked into ordinary paid stock");
        require(before.equals(CatanSavedData.get(h.getLevel().getServer()).save(new CompoundTag())),"Native reward qualification changed nation accounting");h.succeed();
    }
    /** Exact retained 042 intents prove overlay compatibility without rebuilding a
     * quote, replacing its UUID, repricing it, or adopting its old world identity. */
    private static void frozenStock42(MinecraftServer server,String provider)throws Exception{
        String key=provider.equals("tacz")?"tacz":"sbw",file="stock42_"+key+".dat";
        String expected=provider.equals("tacz")?"43a980b0d707a1b95a52457c953ed70c87d8f45e09f43106c0667ea590000d9a":"517663b5a9fc7c0a9e81e0d24334562292d2f3a95d4bafc429e9423121c02f6d";
        byte[] original=resource(file);var provenance=JsonParser.parseString(new String(resource("stock42_provenance.json"),StandardCharsets.UTF_8)).getAsJsonObject();
        var record=provenance.getAsJsonObject("records").getAsJsonObject(key);
        require(provenance.get("source").getAsString().equals("9b3eb646a494348632e4b5c6a692c57f9a9f2f16")&&record.get("file").getAsString().equals(file)
                &&record.get("sha256").getAsString().equals(expected)&&record.get("bytes").getAsInt()==original.length&&NativeWeapon42.sha(original).equals(expected),"Authentic 042 stock intent provenance changed");
        CompoundTag intent=ProducerJournal.read(original);String catalog=intent.getString("catalog");int delimiter=catalog.lastIndexOf('@');
        require(delimiter>0,"Original 042 native intent has no frozen fingerprint");
        var offer=WeaponStock42.offer(intent.getString("offer"));var supply=new NativeWeapon42.Supply(ItemStack.of(intent.getCompound("item")),catalog.substring(delimiter+1));
        require(offer.provider().equals(provider)&&catalog.substring(0,delimiter).equals(offer.revision()),"Original 042 native catalog identity changed");
        try(var lease=NativeWeapon42.transaction(server)){NativeWeapon42.recheck(server,offer,supply);}
        require(supply.item().save(new CompoundTag()).equals(intent.getCompound("item"))&&Arrays.equals(original,resource(file)),"Old native item, UUID or immutable fixture bytes were rebound");
        CatanCraft.LOGGER.info("CLAIMS43_{}_STOCK42_FROZEN_SUPPLY_PASS source=9b3eb646a494348632e4b5c6a692c57f9a9f2f16 intentSHA256={} originalFingerprint=PASS originalNativeItem=PASS noRequote=PASS",provider.equals("tacz")?"TACZ":"SBW",expected);
    }
    private static byte[] resource(String name)throws IOException{
        try(var in=RewardClaim43Tests.class.getResourceAsStream("/data/catancraft/claim43_fixtures/"+name)){
            if(in==null)throw new IOException("Missing original 042 compatibility evidence");byte[] bytes=in.readNBytes(64*1024+1);if(bytes.length>64*1024)throw new IOException("Oversized original compatibility evidence");return bytes;
        }
    }

    private static void nativeClaims(GameTestHelper h,String provider)throws Exception{
        if(provider.equals("superbwarfare")){heldSbwClaims(h);return;}
        List<RewardTable41.Entitlement> entitlements=List.of(sample(provider,true),sample(provider,false));
        Fixture f=new Fixture(h.getLevel(),namespace(provider,false),UUID.randomUUID(),"CC43Native",UUID.randomUUID(),entitlements,null,null);
        f.settle();CompoundTag ledger=f.ledger();byte[] source=bytes(f.sourceIntent());
        for(int index=0;index<entitlements.size();index++){
            var supply=f.quote(index);var selection=f.selection(index);f.prepare(index,supply,NONE);byte[] intent=bytes(RewardClaim43.intentPath(f,selection));
            require(f.claim(index,NONE).complete(),"Native entitlement claim failed");f.assertDelivered(selection,supply,1);assertNative(entitlements.get(index),supply);
            f.assertUnchangedLedger(ledger);inert(f,selection,intent,source);
        }
        require(f.player.getInventory().getItem(0).getCount()==1&&f.player.getInventory().getItem(1).getCount()==entitlements.get(1).count(),"Native claim changed gun/ammo quantities");h.succeed();
    }

    private static void heldSbwClaims(GameTestHelper h)throws Exception{
        for(boolean gun:List.of(true,false)){
            var entitlement=sample("superbwarfare",gun);
            Fixture f=new Fixture(h.getLevel(),namespace("superbwarfare",false),UUID.randomUUID(),"CC44SbwHeld",UUID.randomUUID(),List.of(entitlement),null,null);
            f.settle();var selection=f.selection(0);var historical=historicalAccess(f);NativeWeapon42.Supply supply;
            try(var lease=NativeWeapon42.transaction(f.server())){supply=historical.quote(entitlement);}
            assertNative(entitlement,supply);refuse(()->f.quote(0));refuse(()->f.prepare(0,supply,NONE));
            require(!Files.exists(RewardClaim43.intentPath(f,selection)),"Current policy created an SBW reservation");
            try(var lease=NativeWeapon42.transaction(f.server())){RewardClaim43.fixturePrepare(f,selection,f.facility,supply,historical,NONE);}
            byte[] intent=bytes(RewardClaim43.intentPath(f,selection)),source=bytes(f.sourceIntent()),receipt=bytes(f.sourceReceipt()),player=bytes(f.playerPath()),ledger=bytes(f.ledgerPath());
            int saves=f.playerSaves+f.ledgerSaves;
            for(int retry=0;retry<3;retry++)refuse(()->f.claim(0,NONE));
            require(saves==f.playerSaves+f.ledgerSaves&&f.player.getInventory().isEmpty()&&f.receipts().isEmpty()
                    &&Arrays.equals(intent,bytes(RewardClaim43.intentPath(f,selection)))&&Arrays.equals(source,bytes(f.sourceIntent()))
                    &&Arrays.equals(receipt,bytes(f.sourceReceipt()))&&Arrays.equals(player,bytes(f.playerPath()))&&Arrays.equals(ledger,bytes(f.ledgerPath()))
                    &&!Files.exists(RewardClaim43.receiptPath(f,selection)),"Held historical SBW claim delivered, saved, charged or rewrote history");
        }h.succeed();
    }
    static WeaponStock42.Offer historicalOffer(RewardTable41.Entitlement entitlement)throws IOException{
        return WeaponStock42.offers().stream().filter(o->o.nativeId().equals(entitlement.objectId())
                &&(o.kind()==WeaponShopCatalog40.Kind.GUN)==(entitlement.kind()==RewardTable41.Kind.GUN))
                .findFirst().orElseThrow(()->new IOException("Missing frozen native supply identity"));
    }
    /** Fixture-only reproduction of a pre-policy reservation; never used by a live counter. */
    static RewardClaim43.NativeAccess historicalAccess(Fixture f)throws IOException{
        controlled(f.level,f.namespace);
        if(!Set.of("catancraft_claims43_sbw","catancraft_native43_sbw_restart").contains(f.namespace))throw new IOException("Historical SBW seed requires its isolated fixture");
        return new RewardClaim43.NativeAccess(){
            public NativeWeapon42.Supply quote(RewardTable41.Entitlement e)throws IOException{NativeReward43.validate(e);return NativeWeapon42.quote(f.server(),historicalOffer(e));}
            public void recheck(RewardTable41.Entitlement e,NativeWeapon42.Supply supply)throws IOException{NativeReward43.validate(e);NativeWeapon42.recheck(f.server(),historicalOffer(e),supply);}
        };
    }

    static RewardTable41.Entitlement sample(String provider,boolean gun)throws IOException{
        String id=provider.equals("tacz")?(gun?"tacz:glock_17":"tacz:9mm"):(gun?"superbwarfare:ak_47":"superbwarfare:rifle_ammo");
        return NativeReward43.entitlements().stream().filter(e->e.objectId().equals(id)&&(e.kind()==RewardTable41.Kind.GUN)==gun).findFirst().orElseThrow(()->new IOException("Missing real native entitlement "+id));
    }
    static void assertNative(RewardTable41.Entitlement entitlement,NativeWeapon42.Supply supply)throws Exception{
        ItemStack item=supply.item();CompoundTag frozen=item.save(new CompoundTag());require(item.getCount()==entitlement.count()&&ItemStack.of(frozen.copy()).save(new CompoundTag()).equals(frozen),"Native reward item/count failed exact NBT roundtrip");
        boolean gun=entitlement.kind()==RewardTable41.Kind.GUN;
        if(entitlement.objectId().startsWith("tacz:")){
            Class<?> api=Class.forName("com.tacz.guns.api.item."+(gun?"IGun":"IAmmo"));
            require(api.isInstance(item.getItem())&&new ResourceLocation(entitlement.objectId()).equals(api.getMethod(gun?"getGunId":"getAmmoId",ItemStack.class).invoke(item.getItem(),item)),"Wrong native TaCZ reward identity");
            if(gun)require(((Number)api.getMethod("getCurrentAmmoCount",ItemStack.class).invoke(item.getItem(),item)).intValue()==0&&!((Boolean)api.getMethod("hasBulletInBarrel",ItemStack.class).invoke(item.getItem(),item)),"Reward gun granted unearned loaded ammunition");
        }else{
            require(new ResourceLocation(entitlement.objectId()).equals(ForgeRegistries.ITEMS.getKey(item.getItem())),"Wrong native SBW reward identity");
            if(gun){UUID uuid=NativeWeapon42Tests.gunUuid(item);require(uuid.equals(NativeWeapon42Tests.gunUuid(ItemStack.of(frozen))),"Native SBW reward UUID changed on save/of");}
        }
        require(frozen.equals(supply.item().save(new CompoundTag())),"Native assertion mutated the original reward supply");
    }
    static String namespace(String provider,boolean restart){return restart?"catancraft_native43_"+(provider.equals("tacz")?"tacz":"sbw")+"_restart":"catancraft_claims43"+(provider.isEmpty()?"":provider.equals("tacz")?"_tacz":"_sbw");}
    static Path controlled(ServerLevel level,String namespace)throws IOException{
        Path root=level.getServer().getWorldPath(LevelResource.ROOT).toRealPath();
        if(!(level.getServer() instanceof GameTestServer)||!level.getServer().isSameThread()||!level.getServer().getPlayerList().getPlayers().isEmpty()
                ||!root.getFileName().toString().equals("world")||!root.getParent().getFileName().toString().equals("controlled-"+namespace+"-run")
                ||!root.getParent().getParent().getFileName().toString().equals("build")||!Files.readString(root.getParent().resolve(".catancraft-controlled-fixture-v1")).equals("CatanCraft synthetic flat fixture v1\n")
                ||level.getSeed()!=0||!level.dimension().location().toString().equals("minecraft:overworld"))throw new IOException("Claims43 requires its empty marked native fixture world");return root;
    }
    interface Attempt{void run()throws Exception;}
    static void refuse(Attempt action)throws Exception{try{action.run();}catch(IOException expected){return;}throw new AssertionError("Expected explicit retained claim refusal");}
    private static void malformedPlayerIdentities(Fixture f,NativeWeapon42.Supply supply)throws Exception{
        byte[] original=bytes(f.playerPath());CompoundTag memory=f.player.saveWithoutId(new CompoundTag());int saves=f.playerSaves;
        for(int variant=0;variant<3;variant++){
            CompoundTag corrupt=nativeNbt(original);if(variant==0)corrupt.remove("UUID");else if(variant==1)corrupt.putString("UUID",f.player.getUUID().toString());else corrupt.putUUID("UUID",UUID.randomUUID());
            NbtIo.writeCompressed(corrupt,f.playerPath().toFile());byte[] malformed=bytes(f.playerPath());
            try{refuse(()->f.prepare(0,supply,NONE));require(f.playerSaves==saves&&Arrays.equals(malformed,bytes(f.playerPath()))
                    &&memory.equals(f.player.saveWithoutId(new CompoundTag()))&&!Files.exists(RewardClaim43.intentPath(f,f.selection(0))),"Invalid existing native player identity was overwritten or authorized a claim");}
            finally{Files.write(f.playerPath(),original);}
        }
    }
    private static void corruptAndRestore(Path path,Attempt action)throws Exception{
        byte[] original=bytes(path),corrupt={43,7,9};Files.write(path,corrupt);
        try{refuse(action);require(Arrays.equals(corrupt,bytes(path)),"Rejected corrupt history was silently replaced");}finally{Files.write(path,original);}
    }
    static void interruptClaim(Fixture f,int index,RewardClaim43.SavePoint wanted)throws IOException{
        IOException stop=new IOException("Intentional claim43 interruption at "+wanted);
        try{f.claim(index,point->{if(point==wanted)throw stop;});}catch(IOException ex){if(ex==stop)return;throw ex;}throw new AssertionError("Claim did not reach native boundary "+wanted);
    }
    private static void interruptCapture(Fixture f,MonumentRewards41.SavePoint wanted)throws IOException{
        IOException stop=new IOException("Intentional source capture interruption at "+wanted);
        try{MonumentRewards41.fixtureCapture(f,f.monument,f.nation.id(),point->{if(point==wanted)throw stop;});}catch(IOException ex){if(ex==stop)return;throw ex;}throw new AssertionError("Source capture did not reach "+wanted);
    }
    static CompoundTag receipts(CompoundTag player){return player.getCompound("ForgeData").getCompound(Player.PERSISTED_NBT_TAG).getCompound(RewardClaim43.RECEIPTS);}
    static void inert(Fixture f,RewardClaim43.Selection selection,byte[] intent,byte[] source)throws Exception{
        byte[] player=bytes(f.playerPath()),ledger=bytes(f.ledgerPath()),done=bytes(RewardClaim43.receiptPath(f,selection));CompoundTag memory=f.player.saveWithoutId(new CompoundTag()),account=f.ledger();int saves=f.playerSaves+f.ledgerSaves;
        for(int retry=0;retry<3;retry++)require(f.claim(selection.index(),NONE).complete(),"Completed equipment retry failed");
        require(saves==f.playerSaves+f.ledgerSaves&&memory.equals(f.player.saveWithoutId(new CompoundTag()))&&account.equals(f.ledger())
                &&Arrays.equals(player,bytes(f.playerPath()))&&Arrays.equals(ledger,bytes(f.ledgerPath()))&&Arrays.equals(done,bytes(RewardClaim43.receiptPath(f,selection)))
                &&Arrays.equals(intent,bytes(RewardClaim43.intentPath(f,selection)))&&Arrays.equals(source,bytes(f.sourceIntent())),"Completed retries saved, granted, charged or rewrote original history");
    }
    private static Fixture fresh(GameTestHelper h)throws Exception{return new Fixture(h.getLevel(),namespace("",false),UUID.randomUUID(),"CC43Fixture",UUID.randomUUID(),List.of(SYNTHETIC),null,null);}

    static final class Fixture implements RewardClaim43.Environment,MonumentRewards41.Environment{
        final ServerLevel level;final String namespace;final Path root;final NationData nation;final MonumentData monument;final UUID rewardId;final CompoundTag facility;final List<RewardTable41.Entitlement> entitlements;final RewardClaim43.NativeAccess access;
        FakePlayer player;int playerSaves,ledgerSaves;boolean suppressPlayer,throwBeforePlayer,throwAfterPlayer;Attempt beforePlayer;
        Fixture(ServerLevel level,String namespace,UUID actor,String name,UUID rewardId,List<RewardTable41.Entitlement> entitlements,NationData originalNation,MonumentData originalMonument)throws Exception{
            this.level=level;this.namespace=namespace;this.root=controlled(level,namespace);this.rewardId=rewardId;this.entitlements=List.copyOf(entitlements);player=actor(actor,name);
            if(originalNation==null){
                require(!Files.exists(playerPath()),"New claim fixture refuses existing native player data");nation=data().createNation("Claim43_"+actor,actor);nation.setTreasury(FUNDS);for(ResourceType type:ResourceType.values())nation.addResource(type,STOCK);
                monument=data().createMonument("claims43_"+rewardId,"Synthetic Claim Source",MonumentType.MILITARY_DEPOT,"minecraft:overworld",0,140,0,5);
            }else{nation=originalNation;monument=Objects.requireNonNull(originalMonument);CompoundTag disk=nativeNbt(bytes(playerPath()));require(disk.hasUUID("UUID")&&disk.getUUID("UUID").equals(actor),"Retained native claim actor changed");player.load(disk);}
            facility=new CompoundTag();facility.putString("id","controlled_claims43_counter");facility.putUUID("owner",nation.id());
            access=new RewardClaim43.NativeAccess(){
                public NativeWeapon42.Supply quote(RewardTable41.Entitlement entitlement)throws IOException{
                    if(namespace.equals("catancraft_claims43")){if(!entitlement.equals(SYNTHETIC))throw new IOException("Unknown controlled vanilla claim binding");return syntheticSupply();}
                    return NativeReward43.quote(server(),entitlement);
                }
                public void recheck(RewardTable41.Entitlement entitlement,NativeWeapon42.Supply supply)throws IOException{
                    if(namespace.equals("catancraft_claims43")){var expected=quote(entitlement);if(!expected.fingerprint().equals(supply.fingerprint())||!ItemStack.matches(expected.item(),supply.item()))throw new IOException("Changed controlled vanilla supply");}
                    else NativeReward43.recheck(server(),entitlement,supply);
                }
            };
            if(originalNation==null){persist();persistPlayer();}
        }
        public MinecraftServer server(){return level.getServer();}public ServerPlayer player(){return player;}public CatanSavedData data(){return CatanSavedData.get(server());}
        public void guard(CompoundTag original)throws IOException{if(!root.equals(controlled(level,namespace))||!facility.equals(original)||player.serverLevel()!=level)throw new IOException("Controlled claim counter/world changed");}
        public void persist()throws IOException{ledgerSaves++;data().setDirty();server().overworld().getDataStorage().save();}
        public void persistPlayer()throws IOException{
            playerSaves++;if(throwBeforePlayer)throw new IOException("Injected before actual native player save");if(suppressPlayer)return;
            if(beforePlayer!=null)try{beforePlayer.run();}catch(IOException ex){throw ex;}catch(Exception ex){throw new IOException(ex);}
            ShopNativePlayerFixture40.save(player);if(throwAfterPlayer)throw new IOException("Injected callback after actual native player save");
        }
        FakePlayer actor(UUID id,String name){FakePlayer actor=new FakePlayer(level,new GameProfile(id,name));actor.setGameMode(GameType.SURVIVAL);actor.setHealth(20);actor.setPos(0.5,140,0.5);actor.getInventory().clearContent();return actor;}
        void prepareSource()throws IOException{
            data().setActiveMonumentId(monument.id());data().setLastActivatedMonumentId(monument.id());data().setNextMonumentActivationGameTime(0);
            RewardTable41 table=new RewardTable41("controlled_claim43_source_v1",List.of(new RewardTable41.Entry("equipment",1,new RewardTable41.Bundle(500,Map.of(ResourceType.WOOD,7L),entitlements))));
            MonumentRewards41.fixturePrepare(this,monument,table,43,rewardId,point->{});
        }
        void arm(){monument.setCapturingNationId(nation.id());monument.setCaptureProgressTicks(MonumentManager.CAPTURE_TICKS);data().setDirty();}
        void settle()throws IOException{prepareSource();arm();require(MonumentRewards41.fixtureCapture(this,monument,nation.id(),point->{}).complete(),"Native source reward did not settle");}
        RewardClaim43.Selection selection(int index){return new RewardClaim43.Selection(rewardId,index);}
        NativeWeapon42.Supply quote(int index)throws IOException{try(var lease=nativeLease()){return access.quote(entitlements.get(index));}}
        RewardClaim43.Selection prepare(int index,NativeWeapon42.Supply supply,RewardClaim43.Fault fault)throws IOException{try(var lease=nativeLease()){return RewardClaim43.fixturePrepare(this,selection(index),facility,supply,access,fault);}}
        RewardClaim43.Result claim(int index,RewardClaim43.Fault fault)throws IOException{try(var lease=nativeLease()){return RewardClaim43.fixtureClaim(this,selection(index),access,fault);}}
        private NativeWeapon42.Lease nativeLease()throws IOException{return namespace.equals("catancraft_claims43")?null:NativeWeapon42.transaction(server());}
        Path playerPath(){return root.resolve("playerdata/"+player.getUUID()+".dat");}Path ledgerPath(){return root.resolve("data/catancraft.dat");}
        Path sourceIntent()throws IOException{return MonumentRewards41.intentPath(this,rewardId);}Path sourceReceipt()throws IOException{return MonumentRewards41.receiptPath(this,rewardId);}
        CompoundTag ledger(){return data().save(new CompoundTag());}CompoundTag receipts(){return RewardClaim43Tests.receipts(player.saveWithoutId(new CompoundTag()));}
        int count(ItemStack stack){int count=0;for(int i=0;i<36;i++)if(ItemStack.matches(stack,player.getInventory().getItem(i)))count++;return count;}
        void assertUnchangedLedger(CompoundTag original)throws IOException{require(original.equals(ledger())&&original.equals(nativeNbt(bytes(ledgerPath())).getCompound("data")),"Equipment claim charged, credited or changed native nation ledger");}
        void assertDelivered(RewardClaim43.Selection selection,NativeWeapon42.Supply supply,int count)throws IOException{
            CompoundTag disk=nativeNbt(bytes(playerPath())),memory=player.saveWithoutId(new CompoundTag());String hash=TownHallRevisions.sha(bytes(RewardClaim43.intentPath(this,selection)));
            require(count(supply.item())==count&&disk.hasUUID("UUID")&&disk.getUUID("UUID").equals(player.getUUID())
                    &&disk.getList("Inventory",Tag.TAG_COMPOUND).equals(memory.getList("Inventory",Tag.TAG_COMPOUND))
                    &&hash.equals(RewardClaim43Tests.receipts(disk).getString(RewardClaim43.claimId(selection).toString()))
                    &&RewardClaim43Tests.receipts(disk).equals(receipts()),"Native claim inventory and receipt did not share the exact saved delivery");
        }
    }
    private static NativeWeapon42.Supply syntheticSupply(){ItemStack stack=new ItemStack(Items.EMERALD,3);stack.getOrCreateTag().putString("ControlledClaim43",FIXTURE);return new NativeWeapon42.Supply(stack,NativeWeapon42.sha((FIXTURE+stack.save(new CompoundTag())).getBytes(StandardCharsets.UTF_8)));}
}
