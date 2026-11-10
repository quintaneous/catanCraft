package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.*;
import com.quin.catancraft.monument.MonumentManager;
import com.quin.catancraft.world.MonumentRewards41.*;
import com.quin.catancraft.world.RewardTable41.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.StreamSupport;

/** Native nation saves and synthetic weighted rewards in a marked disposable world.
 * No actual gun, player, vehicle, physical claim or new production probability is enabled. */
@GameTestHolder("catancraft_rewards41") @PrefixGameTestTemplate(false)
public final class MonumentRewards41Tests {
    private static final long MONEY=10_000,WOOD=100;
    private static final AtomicInteger NAMES=new AtomicInteger();
    private static final Fault NONE=point->{};
    private MonumentRewards41Tests(){}

    @GameTest(template="townhall_state_empty",batch="rewards41_01",timeoutTicks=200)
    public static void weighted_draws_are_deterministic_and_follow_declared_weights(GameTestHelper h){run(h,f->{
        RewardTable41 table=weighted();Map<String,Integer> counts=new HashMap<>();SplittableRandom seeds=new SplittableRandom(0x41CA7A);
        for(int i=0;i<32_000;i++){
            long seed=seeds.nextLong();Roll first=table.roll(seed),second=table.roll(seed);
            h.assertTrue(first.equals(second),"Same frozen table and seed produced a different roll");
            h.assertTrue(Set.of("common","uncommon","rare").contains(first.entryId()),"Weighted table returned an unlisted outcome");
            counts.merge(first.entryId(),1,Integer::sum);
        }
        // Fixed reproducible sample with a wide (>six-sigma) tolerance; not a combat/balance claim.
        for(var expected:Map.of("common",0.60,"uncommon",0.30,"rare",0.10).entrySet())
            h.assertTrue(Math.abs(counts.getOrDefault(expected.getKey(),0)/32_000.0-expected.getValue())<0.02,
                    "Observed synthetic reward distribution differs from declared integer weights");
        Set<Long> boundaryTickets=new HashSet<>(Set.of(0L,5L,6L,8L,9L));
        for(long seed=0;seed<10_000&&!boundaryTickets.isEmpty();seed++){
            long ticket=new SplittableRandom(seed).nextLong(10);
            if(boundaryTickets.remove(ticket))h.assertTrue(table.roll(seed).entryId().equals(ticket<6?"common":ticket<9?"uncommon":"rare"),"Weighted interval boundary selected the wrong reward");
        }
        h.assertTrue(boundaryTickets.isEmpty(),"Deterministic sample omitted a weight boundary");
        h.assertTrue(RewardTable41.load(table.save()).save().equals(table.save()),"Native table roundtrip changed weights/rewards");
    });}

    @GameTest(template="townhall_state_empty",batch="rewards41_02",timeoutTicks=200)
    public static void malformed_weights_schema_amounts_and_entitlements_are_refused(GameTestHelper h){run(h,f->{
        Bundle reward=bundle();invalid(()->new Entry("zero",0,reward));invalid(()->new Entry("negative",-1,reward));
        invalid(()->new Entry("large",1_000_001,reward));invalid(()->new RewardTable41("empty",List.of()));
        invalid(()->new RewardTable41("duplicate",List.of(new Entry("same",1,reward),new Entry("same",2,reward))));
        invalid(()->new Bundle(-1,Map.of(),List.of()));invalid(()->new Bundle(0,Map.of(),List.of()));
        invalid(()->new Bundle(1,Map.of(ResourceType.WOOD,0L),List.of()));invalid(()->new Bundle(1_000_000_000_001L,Map.of(),List.of()));
        invalid(()->new Entitlement(Kind.VEHICLE,"superbwarfare:sodayo_pick_up",2,"fixture"));
        invalid(()->new Entitlement(Kind.AMMO,"tacz:9mm",4097,"fixture"));invalid(()->new Entitlement(Kind.GUN,"missing_namespace",1,"fixture"));
        CompoundTag unknown=weighted().save();unknown.putBoolean("trustUnknownField",true);refuse(()->RewardTable41.load(unknown));
        CompoundTag wrongType=weighted().save();wrongType.getList("entries",Tag.TAG_COMPOUND).getCompound(0).putLong("weight",1);refuse(()->RewardTable41.load(wrongType));
        CompoundTag unknownResource=reward.save();unknownResource.getCompound("resources").putLong("UNKNOWN",1);refuse(()->Bundle.load(unknownResource));
        CompoundTag badList=reward.save();ListTag wrong=new ListTag();wrong.add(StringTag.valueOf("not-a-record"));badList.put("entitlements",wrong);refuse(()->Bundle.load(badList));
    });}

    @GameTest(template="townhall_state_empty",batch="rewards41_03",timeoutTicks=200)
    public static void reward_inputs_are_immutable_and_compatibility_preserves_each_existing_payout(GameTestHelper h){run(h,f->{
        Map<ResourceType,Long> resources=new EnumMap<>(ResourceType.class);resources.put(ResourceType.WOOD,2L);
        List<Entitlement> entitlements=new ArrayList<>(List.of(new Entitlement(Kind.GUN,"tacz:aa12",1,"synthetic41")));
        Bundle frozen=new Bundle(100,resources,entitlements);resources.clear();entitlements.clear();
        h.assertTrue(frozen.resources().equals(Map.of(ResourceType.WOOD,2L))&&frozen.entitlements().size()==1,"Caller changed a frozen bundle through its input collections");
        CompoundTag copy=frozen.save();copy.putLong("money",0);copy.getCompound("resources").remove("WOOD");
        h.assertTrue(frozen.money()==100&&frozen.resources().get(ResourceType.WOOD)==2,"Caller changed a frozen bundle through saved NBT");
        for(MonumentType type:MonumentType.values()){
            RewardTable41 table=RewardTable41.compatibility(type);h.assertTrue(table.entries().size()==1,"Compatibility mode introduced random live odds");
            for(long seed:new long[]{0,1,-1,Long.MIN_VALUE,Long.MAX_VALUE}){
                Bundle actual=table.roll(seed).reward();h.assertTrue(actual.money()==type.moneyReward()&&actual.resources().equals(type.resourceRewards())&&actual.entitlements().isEmpty(),"Compatibility mode changed accepted national reward values");
            }
        }
    });}

    @GameTest(template="townhall_state_empty",batch="rewards41_04",timeoutTicks=200)
    public static void activation_is_saved_before_capture_without_credit_or_delivery(GameTestHelper h){run(h,f->{
        f.prepare(weighted(),77);CompoundTag activation=f.state();
        h.assertTrue(activation.getString("phase").equals("OPEN")&&!activation.hasUUID("winner"),"Preparation exposed a captured winner");
        h.assertTrue(f.nation.treasury()==MONEY&&f.nation.resource(ResourceType.WOOD)==WOOD,"Preparing a reward paid before capture");
        h.assertTrue(f.diskMonument().get("rewardActivation41").equals(activation),"Hidden activation did not share the native saved monument state");
        h.assertTrue(!Files.exists(f.intentPath()),"Activation created a winning capture intent before capture");
        h.assertTrue(dataWithoutNativeFileChange(f),"Prepared native ledger differs from live SavedData");
    });}

    @GameTest(template="townhall_state_empty",batch="rewards41_05",timeoutTicks=200)
    public static void activation_interruption_and_new_profile_cannot_reroll_retained_outcome(GameTestHelper h){run(h,f->{
        refuse(()->f.prepare(weighted(),912,failAt(SavePoint.BEFORE_ACTIVATION_SAVE)));CompoundTag original=f.state();
        f.prepare(single(9999,99),7);h.assertTrue(f.state().equals(original),"Retry replaced an existing hidden roll with a new profile, seed or ID");
        h.assertTrue(f.diskMonument().get("rewardActivation41").equals(original),"Retained original activation was not persisted");
        f.arm();f.capture(NONE);f.assertCredit(Bundle.load(original.getCompound("roll")));
    });}

    @GameTest(template="townhall_state_empty",batch="rewards41_06",timeoutTicks=200)
    public static void suppressed_activation_save_is_refused_and_retry_preserves_same_roll(GameTestHelper h){run(h,f->{
        f.suppress=true;refuse(()->f.prepare(weighted(),321));CompoundTag original=f.state();
        h.assertTrue(f.nation.treasury()==MONEY&&!Files.exists(f.intentPath()),"Unverified activation created payout effects");
        f.suppress=false;f.prepare(single(900,9),555);
        h.assertTrue(f.state().equals(original)&&f.diskMonument().get("rewardActivation41").equals(original),"Native-save retry changed original hidden reward");
    });}

    @GameTest(template="townhall_state_empty",batch="rewards41_07",timeoutTicks=200)
    public static void exception_after_real_activation_save_is_accepted_only_by_native_readback(GameTestHelper h){run(h,f->{
        f.throwAfter=true;f.prepare(single(100,2),3);f.throwAfter=false;
        h.assertTrue(f.state().getString("phase").equals("OPEN")&&f.diskMonument().get("rewardActivation41").equals(f.state()),"Proven activation save was discarded because its callback threw");
        f.arm();h.assertTrue(f.capture(NONE).complete(),"Proven activation could not complete");f.assertCredit(bundle());
    });}

    @GameTest(template="townhall_state_empty",batch="rewards41_08",timeoutTicks=200)
    public static void completed_capture_credits_exact_bundle_and_resets_round_in_one_native_save(GameTestHelper h){run(h,f->{
        f.prepare(single(100,2),3);f.arm();Result result=f.capture(NONE);
        h.assertTrue(result.complete()&&result.rewardId().equals(f.rewardId()),"Completed capture did not retain its activation identity");f.assertCredit(bundle());
        h.assertTrue(f.data.activeMonument()==null&&f.monument.captureProgressTicks()==0&&f.monument.capturingNationId()==null,"Completed credit did not reset the same captured round");
        h.assertTrue(f.data.nextMonumentActivationGameTime()==f.state().getLong("nextActivation"),"Capture cooldown was not committed with its reward");
        h.assertTrue(f.receipt().getString("phase").equals("COMMITTED")&&dataWithoutNativeFileChange(f),"Completion/native ledger readback is missing");
    });}

    @GameTest(template="townhall_state_empty",batch="rewards41_09",timeoutTicks=200)
    public static void completed_duplicate_does_not_save_credit_or_rewrite_history(GameTestHelper h){run(h,f->{
        f.prepare(single(100,2),8);f.arm();f.capture(NONE);byte[] intent=f.intentBytes(),receipt=f.receiptBytes(),ledger=f.ledgerBytes();
        CompoundTag monument=f.monument.save();int saves=f.saves;
        for(int i=0;i<3;i++)h.assertTrue(f.capture(NONE).complete(),"Completed duplicate could not recognize its original reward");
        f.assertCredit(bundle());h.assertTrue(f.saves==saves&&f.monument.save().equals(monument),"Completed duplicate mutated or saved the round");
        same(h,intent,f.intentPath(),"Duplicate rewrote original intent");same(h,receipt,f.receiptPath(),"Duplicate rewrote completion");same(h,ledger,f.ledgerPath(),"Duplicate rewrote native ledger");
    });}

    @GameTest(template="townhall_state_empty",batch="rewards41_10",timeoutTicks=200)
    public static void captured_winner_survives_interruption_and_cannot_switch_to_another_nation(GameTestHelper h){run(h,f->{
        f.prepare(single(100,2),1);f.arm();refuse(()->f.capture(failAt(SavePoint.AFTER_CAPTURE_SAVE)));
        CompoundTag captured=f.state();h.assertTrue(captured.getString("phase").equals("CAPTURED")&&captured.getUUID("winner").equals(f.nation.id()),"Verified capture did not freeze original winner");
        h.assertTrue(f.nation.treasury()==MONEY&&!Files.exists(f.intentPath()),"Capture checkpoint credited too early");
        NationData other=f.otherNation();refuse(()->MonumentRewards41.fixtureCapture(f,f.monument,other.id(),NONE));
        h.assertTrue(f.state().equals(captured)&&other.treasury()==MONEY,"Competing winner altered the retained capture");
        MonumentRewards41.fixtureRecover(f,NONE);f.assertCredit(bundle());h.assertTrue(other.treasury()==MONEY,"Recovery paid a later claimant");
    });}

    @GameTest(template="townhall_state_empty",batch="rewards41_11",timeoutTicks=200)
    public static void interrupted_immutable_intent_recovers_original_roll_and_keeps_exact_bytes(GameTestHelper h){run(h,f->{
        f.prepare(weighted(),871);Bundle original=f.rolled();f.arm();refuse(()->f.capture(failAt(SavePoint.AFTER_INTENT)));byte[] intent=f.intentBytes();
        h.assertTrue(f.nation.treasury()==MONEY,"Intent-only interruption had already credited supplies");
        refuse(()->f.prepare(single(50_000,5_000),99));MonumentRewards41.fixtureRecover(f,NONE);
        f.assertCredit(original);same(h,intent,f.intentPath(),"Recovery repriced or rerolled immutable captured reward");
    });}

    @GameTest(template="townhall_state_empty",batch="rewards41_12",timeoutTicks=200)
    public static void post_credit_interruption_retains_one_credit_and_later_unrelated_balances(GameTestHelper h){run(h,f->{
        f.prepare(single(100,2),4);f.arm();refuse(()->f.capture(failAt(SavePoint.AFTER_CREDIT)));f.assertCredit(bundle());
        f.nation.addTreasury(777);f.nation.addResource(ResourceType.WOOD,9);
        MonumentRewards41.fixtureRecover(f,NONE);
        h.assertTrue(f.nation.treasury()==MONEY+100+777&&f.nation.resource(ResourceType.WOOD)==WOOD+2+9,"Recovery credited twice or erased a later balance change");
        h.assertTrue(f.receipt().getString("phase").equals("COMMITTED"),"Retained original credit did not complete");
    });}

    @GameTest(template="townhall_state_empty",batch="rewards41_13",timeoutTicks=200)
    public static void silent_credit_save_is_not_completed_and_recovers_without_double_credit(GameTestHelper h){run(h,f->{
        f.prepare(single(100,2),5);f.arm();f.suppressWhenSettled=true;refuse(()->f.capture(NONE));f.assertCredit(bundle());
        h.assertTrue(!Files.exists(f.receiptPath()),"Unverified credit was marked complete");
        f.suppressWhenSettled=false;MonumentRewards41.fixtureRecover(f,NONE);f.assertCredit(bundle());
        h.assertTrue(dataWithoutNativeFileChange(f)&&f.receipt().getString("phase").equals("COMMITTED"),"Recovered original credit is not native-durable");
    });}

    @GameTest(template="townhall_state_empty",batch="rewards41_14",timeoutTicks=200)
    public static void callback_exception_after_real_credit_save_preserves_verified_payout(GameTestHelper h){run(h,f->{
        f.prepare(single(100,2),6);f.arm();f.throwWhenSettled=true;
        h.assertTrue(f.capture(NONE).complete(),"Native verified credit was rejected solely because callback threw");f.throwWhenSettled=false;f.assertCredit(bundle());
        h.assertTrue(f.receipt().getString("phase").equals("COMMITTED")&&dataWithoutNativeFileChange(f),"Verified payout proof was lost");
    });}

    @GameTest(template="townhall_state_empty",batch="rewards41_15",timeoutTicks=200)
    public static void callback_mutation_cannot_retarget_frozen_native_credit_verification(GameTestHelper h){run(h,f->{
        f.prepare(single(100,2),7);f.arm();boolean[] changed={false};
        f.afterSave=()->{if(f.phase().equals("SETTLED")&&!changed[0]){changed[0]=true;f.nation.addTreasury(1);}};
        refuse(()->f.capture(NONE));h.assertTrue(changed[0]&&!Files.exists(f.receiptPath()),"Changed live ledger was falsely verified as complete");
        h.assertTrue(f.nation.treasury()==MONEY+101&&f.nation.resource(ResourceType.WOOD)==WOOD+2,"Failed verification repeated or erased original credit");
        f.afterSave=null;f.persist();MonumentRewards41.fixtureRecover(f,NONE);
        h.assertTrue(f.nation.treasury()==MONEY+101&&f.nation.resource(ResourceType.WOOD)==WOOD+2,"Recovery repeated credit after callback mutation");
    });}

    @GameTest(template="townhall_state_empty",batch="rewards41_16",timeoutTicks=200)
    public static void malformed_activation_survives_serialization_and_blocks_replacement(GameTestHelper h){run(h,f->{
        f.prepare(single(100,2),8);CompoundTag prior=f.state();Tag malformed=StringTag.valueOf("retain unknown reward schema");f.monument.setRewardActivation41(malformed);
        h.assertTrue(MonumentData.load(f.monument.save()).rewardActivation41().equals(malformed),"Loading malformed history silently erased the hold");
        refuse(()->MonumentRewards41.canReplace(f.server(),f.data,f.monument));
        refuse(()->MonumentRewards41.readyActivation(f.server(),f.data,f.monument));h.assertTrue(f.monument.rewardActivation41().equals(malformed)&&f.nation.treasury()==MONEY,"Refusal erased history or paid supplies");
        f.monument.setRewardActivation41(prior);
    });}

    @GameTest(template="townhall_state_empty",batch="rewards41_17",timeoutTicks=200)
    public static void changed_monument_geometry_cannot_adopt_an_existing_activation(GameTestHelper h){run(h,f->{
        f.prepare(single(100,2),9);CompoundTag original=f.state();
        MonumentData moved=f.data.createMonument(f.monument.id(),f.monument.name(),f.monument.type(),f.monument.dimensionId(),f.monument.x()+1,f.monument.y(),f.monument.z(),f.monument.radius());
        moved.setRewardActivation41(original);refuse(()->MonumentRewards41.fixtureReadyActivation(f,moved));
        refuse(()->MonumentRewards41.fixtureCanReplace(f,moved));
        h.assertTrue(moved.rewardActivation41().equals(original)&&f.nation.treasury()==MONEY,"Changed geometry replaced or settled original reward");
    });}

    @GameTest(template="townhall_state_empty",batch="rewards41_18",timeoutTicks=200)
    public static void missing_captured_nation_keeps_original_reward_held(GameTestHelper h){run(h,f->{
        f.prepare(single(100,2),10);f.arm();refuse(()->f.capture(failAt(SavePoint.AFTER_CAPTURE_SAVE)));CompoundTag original=f.state();
        f.data.nations().remove(f.nation);f.persist();refuse(()->MonumentRewards41.fixtureRecover(f,NONE));
        h.assertTrue(f.state().equals(original)&&!Files.exists(f.receiptPath())&&!MonumentRewards41.fixtureCanReplace(f,f.monument),"Missing winner was reassigned, discarded or completed");
    });}

    @GameTest(template="townhall_state_empty",batch="rewards41_19",timeoutTicks=200)
    public static void treasury_or_resource_overflow_refuses_all_credit_before_mutation(GameTestHelper h){run(h,f->{
        f.prepare(single(100,2),11);f.nation.setTreasury(Long.MAX_VALUE-50);f.arm();long wood=f.nation.resource(ResourceType.WOOD);
        refuse(()->f.capture(NONE));h.assertTrue(f.nation.treasury()==Long.MAX_VALUE-50&&f.nation.resource(ResourceType.WOOD)==wood,"Treasury overflow partially credited or clamped balance");
        f.nation.setTreasury(MONEY);f.nation.addResource(ResourceType.WOOD,Long.MAX_VALUE-WOOD-1);f.persist();
        refuse(()->MonumentRewards41.fixtureRecover(f,NONE));h.assertTrue(f.nation.treasury()==MONEY&&f.nation.resource(ResourceType.WOOD)==Long.MAX_VALUE-1,"Resource overflow partially credited treasury or clamped stockpile");
    });}

    @GameTest(template="townhall_state_empty",batch="rewards41_20",timeoutTicks=200)
    public static void corrupt_or_foreign_capture_intent_is_retained_and_refused(GameTestHelper h){run(h,f->{
        f.prepare(single(100,2),12);f.arm();refuse(()->f.capture(failAt(SavePoint.AFTER_INTENT)));byte[] original=f.intentBytes();
        byte[] corrupt=original.clone();corrupt[corrupt.length-1]^=1;Files.write(f.intentPath(),corrupt);
        Files.write(f.intentPath().resolveSibling(f.intentPath().getFileName()+".intentional-corruption-proof"),corrupt,StandardOpenOption.CREATE_NEW);
        try{refuse(()->MonumentRewards41.fixtureRecover(f,NONE));same(h,corrupt,f.intentPath(),"Refusal rewrote corrupt immutable intent");h.assertTrue(f.nation.treasury()==MONEY,"Corrupt capture credited supplies");}
        finally{Files.write(f.intentPath(),original);}
        CompoundTag foreign=ProducerJournal.read(original);foreign.putUUID("foreignWinner",UUID.randomUUID());ProducerJournal.write(f.intentPath(),foreign,true);byte[] resealed=f.intentBytes();
        Files.write(f.intentPath().resolveSibling(f.intentPath().getFileName()+".intentional-foreign-schema-proof"),resealed,StandardOpenOption.CREATE_NEW);
        try{refuse(()->MonumentRewards41.fixtureRecover(f,NONE));same(h,resealed,f.intentPath(),"Foreign schema was silently normalized");}
        finally{Files.write(f.intentPath(),original);}
    });}

    @GameTest(template="townhall_state_empty",batch="rewards41_21",timeoutTicks=200)
    public static void equipment_rewards_keep_exact_pending_entitlements_without_spawning_items_or_vehicles(GameTestHelper h){run(h,f->{
        List<Entitlement> expected=List.of(new Entitlement(Kind.GUN,"tacz:aa12",1,"synthetic41"),new Entitlement(Kind.AMMO,"tacz:12g",32,"synthetic41"),new Entitlement(Kind.VEHICLE,"superbwarfare:sodayo_pick_up",1,"synthetic41"));
        Bundle reward=new Bundle(100,Map.of(ResourceType.WOOD,2L),expected);RewardTable41 table=new RewardTable41("synthetic_equipment41",List.of(new Entry("rare_equipment",1,reward)));
        long entities=StreamSupport.stream(f.level.getAllEntities().spliterator(),false).count();f.prepare(table,2);f.arm();f.capture(NONE);f.assertCredit(reward);
        h.assertTrue(StreamSupport.stream(f.level.getAllEntities().spliterator(),false).count()==entities,"Pending reference directly spawned an item or vehicle");
        List<CompoundTag> pending=MonumentRewards41.fixturePendingRewards(f,f.nation.id());h.assertTrue(pending.size()==1,"Captured equipment entitlement disappeared or duplicated");
        CompoundTag before=pending.get(0).copy();pending.get(0).putString("callerMutation","must not persist");
        h.assertTrue(MonumentRewards41.fixturePendingRewards(f,f.nation.id()).get(0).equals(before),"Caller mutated retained pending entitlement view");
        h.assertTrue(f.rolled().entitlements().equals(expected),"Pending reward substituted native ID/count/revision");
        NationData other=f.otherNation();h.assertTrue(MonumentRewards41.fixturePendingRewards(f,other.id()).isEmpty(),"Another nation can read private reward entitlements");
        refuse(()->MonumentRewards41.fixturePendingRewards(f,UUID.randomUUID()));
    });}

    @GameTest(template="townhall_state_empty",batch="rewards41_22",timeoutTicks=200)
    public static void map_sync_preserves_native_reward_state_verbatim(GameTestHelper h){run(h,f->{
        MonumentData canonical=f.data.monuments().stream().filter(m->!m.id().startsWith("fixture41_")).findFirst().orElseThrow();
        Tag prior=canonical.rewardActivation41();UUID winner=canonical.capturingNationId();int progress=canonical.captureProgressTicks();String id=canonical.id();
        CompoundTag unknown=new CompoundTag();unknown.putInt("futureSchema",99);unknown.putString("retain","do not reset on map reload");canonical.setRewardActivation41(unknown);
        try{f.data.syncMapDefinitions();MonumentData reloaded=f.data.monument(id);
            h.assertTrue(reloaded!=canonical&&unknown.equals(reloaded.rewardActivation41()),"Map definition sync lost or normalized saved reward history");
            h.assertTrue(Objects.equals(winner,reloaded.capturingNationId())&&progress==reloaded.captureProgressTicks(),"Map sync reset accepted capture progress");
        }finally{MonumentData current=f.data.monument(id);current.setRewardActivation41(prior);current.setCapturingNationId(winner);current.setCaptureProgressTicks(progress);}
    });}

    @GameTest(template="townhall_state_empty",batch="rewards41_23",timeoutTicks=200)
    public static void forced_activation_cannot_reset_a_pending_captured_winner(GameTestHelper h){run(h,f->{
        f.prepare(single(100,2),13);f.arm();refuse(()->f.capture(failAt(SavePoint.AFTER_CAPTURE_SAVE)));CompoundTag original=f.state();
        MonumentData next=f.extraMonument();
        h.assertTrue(!MonumentRewards41.fixtureCanReplace(f,next),"Pending captured reward passed the shared replacement guard");
        h.assertTrue(!MonumentManager.forceActivate(f.server(),f.data,next),"Admin activation replaced an unsettled captured reward");
        h.assertTrue(f.data.activeMonument()==f.monument&&f.state().equals(original)&&next.rewardActivation41()==null,"Refused activation changed original pending round");
        MonumentRewards41.fixtureRecover(f,NONE);f.assertCredit(bundle());
    });}

    @GameTest(template="townhall_state_empty",batch="rewards41_24",timeoutTicks=200)
    public static void post_native_credit_and_terminal_marker_faults_reconcile_without_replay(GameTestHelper h){run(h,f->{
        f.prepare(single(100,2),14);f.arm();refuse(()->f.capture(failAt(SavePoint.AFTER_INTENT)));CompoundTag captured=f.state();
        refuse(()->MonumentRewards41.fixtureRecover(f,failAt(SavePoint.AFTER_CREDIT_SAVE)));f.assertCredit(bundle());
        byte[] intent=f.intentBytes(),ledger=f.ledgerBytes();CompoundTag settled=f.state();int saves=f.saves;
        f.monument.setRewardActivation41(captured);f.data.setActiveMonumentId(f.monument.id());
        try{
            refuse(()->MonumentRewards41.fixtureRecover(f,NONE));f.assertCredit(bundle());
            h.assertTrue(f.saves==saves&&f.state().equals(captured),"Stale captured state was saved over a native settled reward");
            same(h,ledger,f.ledgerPath(),"Stale live activation overwrote original native credit proof");
            same(h,intent,f.intentPath(),"Stale live activation rewrote original capture intent");
        }finally{f.monument.setRewardActivation41(settled);f.data.setActiveMonumentId(null);}
        refuse(()->MonumentRewards41.fixtureRecover(f,failAt(SavePoint.AFTER_COMMITTED)));f.assertCredit(bundle());byte[] completion=f.receiptBytes();
        h.assertTrue(f.capture(NONE).complete(),"Terminal fault could not recognize committed original reward");f.assertCredit(bundle());
        same(h,intent,f.intentPath(),"Terminal recovery changed original roll");same(h,completion,f.receiptPath(),"Terminal recovery rewrote committed proof");
    });}

    private static Bundle bundle(){return new Bundle(100,Map.of(ResourceType.WOOD,2L),List.of());}
    private static RewardTable41 single(long money,long wood){return new RewardTable41("synthetic_fixed41_"+money+"_"+wood,List.of(new Entry("supplies",1,new Bundle(money,Map.of(ResourceType.WOOD,wood),List.of()))));}
    private static RewardTable41 weighted(){return new RewardTable41("synthetic_weighted41",List.of(new Entry("common",6,new Bundle(50,Map.of(ResourceType.WOOD,1L),List.of())),new Entry("uncommon",3,bundle()),new Entry("rare",1,new Bundle(250,Map.of(ResourceType.WOOD,7L),List.of()))));}
    private interface Attempt{void run()throws Exception;}
    private interface Body{void run(Fixture f)throws Exception;}
    private static void run(GameTestHelper h,Body body){try(Fixture f=new Fixture(h)){body.run(f);h.succeed();}catch(Exception failure){CatanCraft.LOGGER.error("Controlled monument reward41 test failed",failure);throw new RuntimeException(failure);}}
    private static void refuse(Attempt attempt)throws Exception{try{attempt.run();}catch(IOException expected){return;}throw new AssertionError("Expected retained reward refusal");}
    private static void invalid(Attempt attempt)throws Exception{try{attempt.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("Expected invalid reward model rejection");}
    private static Fault failAt(SavePoint point){return at->{if(at==point)throw new IOException("Intentional reward41 fault at "+point);};}
    private static void same(GameTestHelper h,byte[] bytes,Path path,String message)throws IOException{h.assertTrue(Arrays.equals(bytes,Files.readAllBytes(path)),message);}
    private static boolean dataWithoutNativeFileChange(Fixture f)throws IOException{return NbtIo.readCompressed(f.ledgerPath().toFile()).getCompound("data").equals(f.data.save(new CompoundTag()));}

    private static final class Fixture implements Environment,AutoCloseable {
        final GameTestHelper h;final ServerLevel level;final CatanSavedData data;final NationData nation;final Path root;final MonumentData monument;final String prefix;
        final List<NationData> nations=new ArrayList<>();final List<String> monuments=new ArrayList<>();final String priorActive,priorLast;final long priorNext;
        boolean suppress,throwAfter,suppressWhenSettled,throwWhenSettled;int saves;Attempt afterSave;
        Fixture(GameTestHelper h)throws Exception{
            this.h=h;level=h.getLevel();root=level.getServer().getWorldPath(LevelResource.ROOT).toRealPath();
            if(!(level.getServer() instanceof GameTestServer)||!level.getServer().isSameThread()||!root.getFileName().toString().equals("world")
                    ||!root.getParent().getFileName().toString().equals("controlled-catancraft_rewards41-run")
                    ||!Files.readString(root.getParent().resolve(".catancraft-controlled-fixture-v1")).equals("CatanCraft synthetic flat fixture v1\n"))throw new IOException("Reward tests require their marked disposable native world");
            data=CatanSavedData.get(server());priorActive=data.activeMonument()==null?null:data.activeMonument().id();priorLast=data.lastActivatedMonumentId();priorNext=data.nextMonumentActivationGameTime();prefix="fixture41_"+NAMES.incrementAndGet()+"_";
            nation=data.createNation(prefix+"nation",UUID.randomUUID());nation.setTreasury(MONEY);nation.addResource(ResourceType.WOOD,WOOD);nations.add(nation);
            monument=data.createMonument(prefix+"monument","Synthetic Reward41",MonumentType.MILITARY_DEPOT,"minecraft:overworld",0,140,0,5);monuments.add(monument.id());
            data.setActiveMonumentId(monument.id());data.setLastActivatedMonumentId(monument.id());data.setNextMonumentActivationGameTime(0);persist();
        }
        public MinecraftServer server(){return level.getServer();}public CatanSavedData data(){return data;}
        public void persist()throws IOException{
            saves++;boolean settled=monument.rewardActivation41() instanceof CompoundTag c&&c.getString("phase").equals("SETTLED");
            if(suppress||suppressWhenSettled&&settled)return;
            data.setDirty();server().overworld().getDataStorage().save();
            if(afterSave!=null)try{afterSave.run();}catch(IOException failure){throw failure;}catch(Exception failure){throw new IOException(failure);}
            if(throwAfter||throwWhenSettled&&settled)throw new IOException("Intentional callback after native reward save");
        }
        void prepare(RewardTable41 table,long seed)throws IOException{prepare(table,seed,NONE);}
        void prepare(RewardTable41 table,long seed,Fault fault)throws IOException{MonumentRewards41.fixturePrepare(this,monument,table,seed,UUID.randomUUID(),fault);}
        void arm(){monument.setCapturingNationId(nation.id());monument.setCaptureProgressTicks(MonumentManager.CAPTURE_TICKS);data.setDirty();}
        Result capture(Fault fault)throws IOException{return MonumentRewards41.fixtureCapture(this,monument,nation.id(),fault);}
        CompoundTag state(){return ((CompoundTag)monument.rewardActivation41()).copy();}String phase(){return state().getString("phase");}
        UUID rewardId(){return state().getUUID("id");}Bundle rolled()throws IOException{return Bundle.load(state().getCompound("roll"));}
        Path intentPath()throws IOException{return MonumentRewards41.intentPath(this,rewardId());}Path receiptPath()throws IOException{return MonumentRewards41.receiptPath(this,rewardId());}
        byte[] intentBytes()throws IOException{return Files.readAllBytes(intentPath());}byte[] receiptBytes()throws IOException{return Files.readAllBytes(receiptPath());}
        CompoundTag receipt()throws IOException{return ProducerJournal.read(receiptPath());}Path ledgerPath(){return root.resolve("data/catancraft.dat");}byte[] ledgerBytes()throws IOException{return Files.readAllBytes(ledgerPath());}
        CompoundTag diskMonument()throws IOException{ListTag list=NbtIo.readCompressed(ledgerPath().toFile()).getCompound("data").getList("monuments",Tag.TAG_COMPOUND);for(int i=0;i<list.size();i++)if(list.getCompound(i).getString("id").equals(monument.id()))return list.getCompound(i);throw new IOException("Native fixture monument missing");}
        void assertCredit(Bundle reward){h.assertTrue(nation.treasury()==MONEY+reward.money()&&nation.resource(ResourceType.WOOD)==WOOD+reward.resources().getOrDefault(ResourceType.WOOD,0L),"Original national reward was not credited exactly once");}
        NationData otherNation(){NationData other=data.createNation(prefix+"other",UUID.randomUUID());other.setTreasury(MONEY);nations.add(other);return other;}
        MonumentData extraMonument(){MonumentData other=data.createMonument(prefix+"other","Synthetic Other",MonumentType.REFINERY,"minecraft:overworld",32,140,0,5);monuments.add(other.id());return other;}
        public void close(){
            data.monuments().removeIf(m->monuments.contains(m.id()));for(NationData n:nations)data.nations().remove(n);
            if(data.activeMonument()==null||monuments.contains(data.activeMonument().id()))data.setActiveMonumentId(priorActive);
            data.setLastActivatedMonumentId(priorLast);data.setNextMonumentActivationGameTime(priorNext);
            data.setDirty();server().overworld().getDataStorage().save();
        }
    }
}
