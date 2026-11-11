package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.*;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;

import static com.quin.catancraft.world.NativeWeapon42Tests.bytes;
import static com.quin.catancraft.world.NativeWeapon42Tests.nativeNbt;
import static com.quin.catancraft.world.NativeWeapon42Tests.require;
import static com.quin.catancraft.world.RewardClaim43Tests.*;

/** One retained real native equipment claim per provider across two distinct JVMs.
 * Seed completes the original monument credit, reserves its entitlement and stops
 * after actual PlayerDataStorage delivery. Verify never reseeds the actor, nation,
 * monument, source history, claim request, item UUID or receipt. */
public final class RewardClaim43RestartTests {
    private RewardClaim43RestartTests(){}

    @GameTestHolder("catancraft_native43_tacz_restart") @PrefixGameTestTemplate(false)
    public static final class Tacz{
        @GameTest(template="townhall_state_empty",timeoutTicks=200)
        public static void original_native_tacz_claim_recovers_without_charge_or_redelivery(GameTestHelper h)throws Exception{run(h,"tacz");}
    }
    @GameTestHolder("catancraft_native43_sbw_restart") @PrefixGameTestTemplate(false)
    public static final class Sbw{
        @GameTest(template="townhall_state_empty",timeoutTicks=200)
        public static void original_sbw_reservation_stays_held_without_delivery_after_restart(GameTestHelper h)throws Exception{run(h,"superbwarfare");}
    }

    private static void run(GameTestHelper h,String provider)throws Exception{
        ServerLevel level=h.getLevel();Path root=controlled(level,namespace(provider,true));String phase=System.getProperty("catancraft.native43RestartPhase","");
        require(System.getProperty("catancraft.native43RestartProvider","").equals(provider),"Explicit matching native43RestartProvider=tacz|superbwarfare required");
        require(phase.equals("seed")||phase.equals("verify"),"Explicit native43RestartPhase=seed|verify required");source();
        Path proof=root.resolve("data/"+namespace(provider,true)+"_seed.proof"),verified=root.resolve("data/"+namespace(provider,true)+"_verified.proof");
        require(!Files.exists(verified,LinkOption.NOFOLLOW_LINKS),"Already verified native claim world cannot be reused");
        if(provider.equals("superbwarfare")){if(phase.equals("seed"))seedHeld(level,root,proof);else verifyHeld(level,root,proof,verified);}
        else if(phase.equals("seed"))seed(level,provider,root,proof);else verify(level,provider,root,proof,verified);
        require(level.getServer().getPlayerList().getPlayers().isEmpty(),"Synthetic claim actor entered connected/ticking roster");h.succeed();
    }

    private static void seed(ServerLevel level,String provider,Path root,Path proof)throws Exception{
        require(!Files.exists(proof,LinkOption.NOFOLLOW_LINKS),"Seed cannot overwrite original claim evidence");
        CatanSavedData data=CatanSavedData.get(level.getServer());require(data.nations().isEmpty(),"Claim restart seed requires a fresh native nation ledger");
        var entitlement=sample(provider,true);Fixture f=new Fixture(level,namespace(provider,true),actor(provider),name(provider),reward(provider),List.of(entitlement),null,null);
        f.settle();CompoundTag settled=f.ledger();assertSourceCredit(f);byte[] sourceIntent=bytes(f.sourceIntent()),sourceReceipt=bytes(f.sourceReceipt());
        var supply=f.quote(0);assertNative(entitlement,supply);var selection=f.selection(0);f.prepare(0,supply,NONE);
        byte[] intent=bytes(RewardClaim43.intentPath(f,selection));assertRequest(f,RewardClaim43.fixtureInspect(f,selection),entitlement,supply);
        interruptClaim(f,0,RewardClaim43.SavePoint.AFTER_PLAYER_SAVE);f.assertDelivered(selection,supply,1);f.assertUnchangedLedger(settled);
        byte[] progress=interrupted(f,selection,TownHallRevisions.sha(intent));
        require(RewardClaim43.fixtureList(f).stream().anyMatch(v->v.selection().equals(selection)&&v.status().equals("RESERVED")&&v.reservedActor().equals(actor(provider))),"Original delivered claim is not retained as actor-bound pending work");
        byte[] player=bytes(f.playerPath()),ledger=bytes(f.ledgerPath());CompoundTag evidence=metadata(level,provider,root);
        evidence.putUUID("actor",actor(provider));evidence.putString("name",name(provider));evidence.putUUID("nation",f.nation.id());evidence.putString("monument",f.monument.id());
        evidence.putUUID("reward",reward(provider));evidence.putUUID("claim",RewardClaim43.claimId(selection));evidence.putInt("index",0);
        evidence.put("nationAfterSourceCredit",f.nation.save());evidence.put("nativeLedger",settled);evidence.put("item",supply.item().save(new CompoundTag()));evidence.put("entitlement",entitlement.save());evidence.putString("fingerprint",supply.fingerprint());
        if(provider.equals("superbwarfare"))evidence.putUUID("nativeGunUUID",NativeWeapon42Tests.gunUuid(supply.item()));
        retained(evidence,"sourceIntent",sourceIntent);retained(evidence,"sourceReceipt",sourceReceipt);retained(evidence,"claimIntent",intent);retained(evidence,"claimProgress",progress);retained(evidence,"nativePlayer",player);retained(evidence,"nativeLedger",ledger);
        evidence.putString("playerReceipt",receipts(nativeNbt(player)).getString(RewardClaim43.claimId(selection).toString()));evidence.putString("result","SEEDED_PENDING_DISTINCT_JVM_VERIFICATION");
        ProducerJournal.write(proof,evidence,false);
        require(Arrays.equals(sourceIntent,bytes(f.sourceIntent()))&&Arrays.equals(sourceReceipt,bytes(f.sourceReceipt()))&&Arrays.equals(intent,bytes(RewardClaim43.intentPath(f,selection))),"Seed rewrote original source or claim history");
        CatanCraft.LOGGER.info("NATIVE43_{}_RESTART_SEED_PASS originalSettledReward=PASS originalWinner=PASS originalNativePlayer=PASS nativeLedger=PASS immutableClaim=PASS nativeItem=1 claimCharge=0 progress=DELIVERING uuid={} process={} source={} verification=PENDING",marker(provider),provider.equals("superbwarfare")?NativeWeapon42Tests.gunUuid(supply.item()):"N/A",process(),source());
    }

    private static void verify(ServerLevel level,String provider,Path root,Path proof,Path verified)throws Exception{
        byte[] proofBytes=bytes(proof);CompoundTag evidence=ProducerJournal.read(proofBytes);
        require(evidence.getInt("schema")==1&&namespace(provider,true).equals(evidence.getString("namespace"))&&provider.equals(evidence.getString("provider"))
                &&root.toString().equals(evidence.getString("root"))&&source().equals(evidence.getString("source"))&&level.getSeed()==evidence.getLong("seed")
                &&level.dimension().location().toString().equals(evidence.getString("dimension")),"Foreign native claim restart source/world/provider proof");
        require(!evidence.getString("process").isBlank()&&!process().equals(evidence.getString("process")),"Native claim verification requires a distinct JVM");
        require(evidence.getUUID("actor").equals(actor(provider))&&evidence.getString("name").equals(name(provider))&&evidence.getUUID("reward").equals(reward(provider))&&evidence.getInt("index")==0,"Original native actor/reward selection changed");
        CatanSavedData data=CatanSavedData.get(level.getServer());NationData nation=data.nation(evidence.getUUID("nation"));MonumentData monument=data.monument(evidence.getString("monument"));
        require(nation!=null&&nation==data.nationForPlayer(actor(provider))&&nation.isLeader(actor(provider))&&nation.save().equals(evidence.getCompound("nationAfterSourceCredit"))
                &&monument!=null&&data.save(new CompoundTag()).equals(evidence.getCompound("nativeLedger")),"Original native winner, monument or credited ledger did not survive restart");
        byte[] player=bytes(root.resolve("playerdata/"+actor(provider)+".dat")),ledger=bytes(root.resolve("data/catancraft.dat"));
        verifyRetained(evidence,"nativePlayer",player);
        byte[] seededLedger=evidence.getByteArray("nativeLedgerBytes");require(TownHallRevisions.sha(seededLedger).equals(evidence.getString("nativeLedgerSHA256"))
                &&nativeNbt(seededLedger).getCompound("data").equals(evidence.getCompound("nativeLedger"))&&nativeNbt(ledger).getCompound("data").equals(evidence.getCompound("nativeLedger")),"Original native source-credit ledger changed");
        var entitlement=sample(provider,true);require(entitlement.save().equals(evidence.getCompound("entitlement")),"Original native entitlement identity changed");
        Fixture f=new Fixture(level,namespace(provider,true),actor(provider),name(provider),reward(provider),List.of(entitlement),nation,monument);var selection=f.selection(0);
        require(RewardClaim43.claimId(selection).equals(evidence.getUUID("claim")),"Original global claim identity changed");
        byte[] sourceIntent=bytes(f.sourceIntent()),sourceReceipt=bytes(f.sourceReceipt()),intent=bytes(RewardClaim43.intentPath(f,selection));
        verifyRetained(evidence,"sourceIntent",sourceIntent);verifyRetained(evidence,"sourceReceipt",sourceReceipt);verifyRetained(evidence,"claimIntent",intent);
        RewardClaim43.Request original=RewardClaim43.fixtureInspect(f,selection);var supply=original.supply();
        require(supply.fingerprint().equals(evidence.getString("fingerprint"))&&supply.item().save(new CompoundTag()).equals(evidence.getCompound("item")),"Original intent's frozen item/fingerprint was replaced on restart");
        assertRequest(f,original,entitlement,supply);try(var lease=NativeWeapon42.transaction(level.getServer())){NativeReward43.recheck(level.getServer(),entitlement,supply);}assertNative(entitlement,supply);
        if(provider.equals("superbwarfare"))require(evidence.hasUUID("nativeGunUUID")&&NativeWeapon42Tests.gunUuid(supply.item()).equals(evidence.getUUID("nativeGunUUID"))
                &&NativeWeapon42Tests.gunUuid(f.player.getInventory().getItem(0)).equals(evidence.getUUID("nativeGunUUID")),"Native SBW reward UUID changed across JVMs");
        String hash=TownHallRevisions.sha(intent);require(hash.equals(evidence.getString("playerReceipt")),"Original delivery receipt is not bound to immutable claim intent");
        verifyRetained(evidence,"claimProgress",interrupted(f,selection,hash));f.assertDelivered(selection,supply,1);assertSourceCredit(f);
        require(f.claim(0,NONE).complete(),"Original native reward claim did not recover");
        require(f.playerSaves==0&&f.ledgerSaves==0&&Arrays.equals(player,bytes(f.playerPath()))&&Arrays.equals(ledger,bytes(f.ledgerPath())),"Delivered claim recovery saved player/ledger or repeated native delivery");
        f.assertDelivered(selection,supply,1);f.assertUnchangedLedger(evidence.getCompound("nativeLedger"));
        CompoundTag completion=ProducerJournal.read(bytes(RewardClaim43.receiptPath(f,selection)));
        require(completion.getString("phase").equals("COMMITTED")&&completion.getUUID("id").equals(RewardClaim43.claimId(selection))&&completion.getString("intentSHA256").equals(hash),"Claim completion lost its original intent binding");
        inert(f,selection,intent,sourceIntent);require(Arrays.equals(sourceReceipt,bytes(f.sourceReceipt()))&&Arrays.equals(proofBytes,bytes(proof)),"Native recovery rewrote original source completion or immutable seed proof");
        CompoundTag result=metadata(level,provider,root);result.putString("result","PASS");result.putString("seedProcess",evidence.getString("process"));result.putString("seedProofSHA256",TownHallRevisions.sha(proofBytes));
        result.putString("claimIntentSHA256",hash);result.putInt("nativeItemCount",1);result.putInt("additionalCharge",0);result.putInt("additionalSourceCredit",0);result.putInt("newItemsAfterRecovery",0);result.putInt("completedRetries",3);
        result.putInt("firstRecoveryPlayerSaves",f.playerSaves);result.putInt("firstRecoveryLedgerSaves",f.ledgerSaves);result.putInt("completedRetrySaves",0);
        if(provider.equals("superbwarfare"))result.putUUID("nativeGunUUID",NativeWeapon42Tests.gunUuid(supply.item()));ProducerJournal.write(verified,result,false);
        CatanCraft.LOGGER.info("NATIVE43_{}_RESTART_VERIFY_PASS distinctJVM=PASS originalSettledReward=PASS originalWinner=PASS originalNativePlayer=PASS originalNativeLedger=PASS immutableClaim=PASS originalProgress=PASS nativeItem=1 additionalCharge=0 additionalSourceCredit=0 newItems=0 recoveryPlayerSaves=0 recoveryLedgerSaves=0 completedRetrySaves=0 retries=3 uuid={} seedProcess={} verifyProcess={} source={}",marker(provider),provider.equals("superbwarfare")?NativeWeapon42Tests.gunUuid(supply.item()):"N/A",evidence.getString("process"),process(),source());
    }

    private static void seedHeld(ServerLevel level,Path root,Path proof)throws Exception{
        String provider="superbwarfare";require(!Files.exists(proof,LinkOption.NOFOLLOW_LINKS),"Historical seed cannot overwrite evidence");
        require(CatanSavedData.get(level.getServer()).nations().isEmpty(),"Historical seed requires a fresh native ledger");
        var entitlement=sample(provider,true);Fixture f=new Fixture(level,namespace(provider,true),actor(provider),name(provider),reward(provider),List.of(entitlement),null,null);
        f.settle();assertSourceCredit(f);var access=historicalAccess(f);NativeWeapon42.Supply supply;
        try(var lease=NativeWeapon42.transaction(f.server())){
            supply=access.quote(entitlement);assertNative(entitlement,supply);
            RewardClaim43.fixturePrepare(f,f.selection(0),f.facility,supply,access,NONE);
        }
        byte[] player=bytes(f.playerPath()),ledger=bytes(f.ledgerPath());int saves=f.playerSaves+f.ledgerSaves;
        refuse(()->f.claim(0,NONE));require(saves==f.playerSaves+f.ledgerSaves&&f.player.getInventory().isEmpty()&&f.receipts().isEmpty()
                &&Arrays.equals(player,bytes(f.playerPath()))&&Arrays.equals(ledger,bytes(f.ledgerPath()))
                &&!Files.exists(RewardClaim43.receiptPath(f,f.selection(0))),"Current policy delivered or changed historical SBW seed");
        CompoundTag evidence=metadata(level,provider,root);evidence.putInt("schema",2);evidence.putString("policy","TACZ_ONLY_HELD");
        evidence.putUUID("actor",actor(provider));evidence.putString("name",name(provider));evidence.putUUID("nation",f.nation.id());evidence.putString("monument",f.monument.id());
        evidence.putUUID("reward",reward(provider));evidence.putUUID("claim",RewardClaim43.claimId(f.selection(0)));evidence.putInt("index",0);
        evidence.put("nationAfterSourceCredit",f.nation.save());evidence.put("nativeLedger",f.ledger());evidence.put("item",supply.item().save(new CompoundTag()));
        evidence.put("entitlement",entitlement.save());evidence.putString("fingerprint",supply.fingerprint());evidence.putUUID("nativeGunUUID",NativeWeapon42Tests.gunUuid(supply.item()));
        retained(evidence,"sourceIntent",bytes(f.sourceIntent()));retained(evidence,"sourceReceipt",bytes(f.sourceReceipt()));
        retained(evidence,"claimIntent",bytes(RewardClaim43.intentPath(f,f.selection(0))));retained(evidence,"nativePlayer",player);retained(evidence,"nativeLedger",ledger);
        evidence.putString("result","SEEDED_PENDING_DISTINCT_JVM_VERIFICATION");ProducerJournal.write(proof,evidence,false);
        CatanCraft.LOGGER.info("NATIVE43_SBW_RESTART_SEED_PASS policy=TACZ_ONLY_HELD nativeItem=0 claimCharge=0 immutableReservation=PASS process={} source={}",process(),source());
    }
    private static void verifyHeld(ServerLevel level,Path root,Path proof,Path verified)throws Exception{
        String provider="superbwarfare";byte[] proofBytes=bytes(proof);CompoundTag e=ProducerJournal.read(proofBytes);
        require(e.getInt("schema")==2&&e.getString("policy").equals("TACZ_ONLY_HELD")&&e.getString("source").equals(source())
                &&e.getString("namespace").equals(namespace(provider,true))&&e.getString("provider").equals(provider)
                &&e.getString("root").equals(root.toString())&&e.getLong("seed")==level.getSeed()
                &&e.getString("dimension").equals(level.dimension().location().toString()),"Foreign held-reservation proof");
        require(!e.getString("process").isBlank()&&!e.getString("process").equals(process()),"Held reservation requires a distinct JVM");
        require(e.getUUID("actor").equals(actor(provider))&&e.getUUID("reward").equals(reward(provider))&&e.getInt("index")==0
                &&e.getString("name").equals(name(provider)),"Original held actor/reward changed");
        CatanSavedData data=CatanSavedData.get(level.getServer());NationData nation=data.nation(e.getUUID("nation"));MonumentData monument=data.monument(e.getString("monument"));
        require(nation!=null&&nation==data.nationForPlayer(actor(provider))&&nation.isLeader(actor(provider))&&monument!=null
                &&nation.save().equals(e.getCompound("nationAfterSourceCredit"))&&data.save(new CompoundTag()).equals(e.getCompound("nativeLedger")),"Held winner or credited ledger changed");
        var entitlement=sample(provider,true);require(entitlement.save().equals(e.getCompound("entitlement")),"Historical entitlement changed");
        Fixture f=new Fixture(level,namespace(provider,true),actor(provider),name(provider),reward(provider),List.of(entitlement),nation,monument);
        var selection=f.selection(0);require(RewardClaim43.claimId(selection).equals(e.getUUID("claim")),"Original reservation identity changed");
        verifyRetained(e,"sourceIntent",bytes(f.sourceIntent()));verifyRetained(e,"sourceReceipt",bytes(f.sourceReceipt()));verifyRetained(e,"claimIntent",bytes(RewardClaim43.intentPath(f,selection)));
        byte[] player=bytes(f.playerPath()),ledger=bytes(f.ledgerPath());verifyRetained(e,"nativePlayer",player);
        require(TownHallRevisions.sha(e.getByteArray("nativeLedgerBytes")).equals(e.getString("nativeLedgerSHA256"))
                &&nativeNbt(e.getByteArray("nativeLedgerBytes")).getCompound("data").equals(e.getCompound("nativeLedger"))
                &&nativeNbt(ledger).getCompound("data").equals(e.getCompound("nativeLedger")),"Historical credited native ledger changed");
        var request=RewardClaim43.fixtureInspect(f,selection);var supply=request.supply();assertRequest(f,request,entitlement,supply);assertNative(entitlement,supply);
        require(supply.fingerprint().equals(e.getString("fingerprint"))&&supply.item().save(new CompoundTag()).equals(e.getCompound("item"))
                &&NativeWeapon42Tests.gunUuid(supply.item()).equals(e.getUUID("nativeGunUUID")),"Historical item/UUID was rebound");
        try(var lease=NativeWeapon42.transaction(f.server())){refuse(()->NativeReward43.quote(f.server(),entitlement));refuse(()->NativeReward43.recheck(f.server(),entitlement,supply));}
        for(int retry=0;retry<3;retry++)refuse(()->f.claim(0,NONE));
        assertSourceCredit(f);require(f.playerSaves==0&&f.ledgerSaves==0&&f.player.getInventory().isEmpty()&&f.receipts().isEmpty()
                &&Arrays.equals(player,bytes(f.playerPath()))&&Arrays.equals(ledger,bytes(f.ledgerPath()))
                &&!Files.exists(RewardClaim43.receiptPath(f,selection)),"Held retry delivered, saved, charged or completed SBW equipment");
        verifyRetained(e,"sourceIntent",bytes(f.sourceIntent()));verifyRetained(e,"sourceReceipt",bytes(f.sourceReceipt()));verifyRetained(e,"claimIntent",bytes(RewardClaim43.intentPath(f,selection)));
        require(Arrays.equals(proofBytes,bytes(proof)),"Held retry rewrote seed evidence");
        CompoundTag result=metadata(level,provider,root);result.putInt("schema",2);result.putString("policy","TACZ_ONLY_HELD");result.putString("result","PASS");
        result.putString("seedProcess",e.getString("process"));result.putString("seedProofSHA256",TownHallRevisions.sha(proofBytes));result.putString("claimIntentSHA256",e.getString("claimIntentSHA256"));
        result.putInt("nativeItemCount",0);result.putInt("additionalCharge",0);result.putInt("additionalSourceCredit",0);result.putInt("newItemsAfterRecovery",0);result.putInt("refusedRetries",3);
        result.putInt("recoveryPlayerSaves",f.playerSaves);result.putInt("recoveryLedgerSaves",f.ledgerSaves);result.putUUID("nativeGunUUID",e.getUUID("nativeGunUUID"));ProducerJournal.write(verified,result,false);
        CatanCraft.LOGGER.info("NATIVE43_SBW_RESTART_VERIFY_PASS policy=TACZ_ONLY_HELD distinctJVM=PASS immutableReservation=PASS nativeItem=0 additionalCharge=0 additionalSourceCredit=0 newItems=0 recoveryPlayerSaves=0 recoveryLedgerSaves=0 refusedRetries=3 seedProcess={} verifyProcess={} source={}",e.getString("process"),process(),source());
    }

    private static byte[] interrupted(Fixture f,RewardClaim43.Selection selection,String hash)throws IOException{
        byte[] bytes=bytes(RewardClaim43.receiptPath(f,selection));CompoundTag progress=ProducerJournal.read(bytes);
        require(progress.getAllKeys().equals(Set.of("schema","id","intentSHA256","phase"))&&progress.getInt("schema")==1&&progress.getUUID("id").equals(RewardClaim43.claimId(selection))
                &&progress.getString("intentSHA256").equals(hash)&&progress.getString("phase").equals("DELIVERING"),"Original claim did not retain DELIVERING progress");return bytes;
    }
    private static void assertRequest(Fixture f,RewardClaim43.Request request,RewardTable41.Entitlement entitlement,NativeWeapon42.Supply supply){
        require(request.selection().equals(f.selection(0))&&request.actor().equals(f.player.getUUID())&&request.nation().equals(f.nation.id())&&request.facility().equals(f.facility)
                &&request.entitlement().equals(entitlement)&&request.supply().fingerprint().equals(supply.fingerprint())&&ItemStack.matches(request.supply().item(),supply.item()),"Frozen native claim request no longer matches original winner/actor/item");
    }
    private static void assertSourceCredit(Fixture f){require(f.nation.treasury()==FUNDS+500,"Original settled monument money was charged or credited again");for(ResourceType type:ResourceType.values())require(f.nation.resource(type)==STOCK+(type==ResourceType.WOOD?7:0),"Original monument resource credit changed: "+type);}
    private static void retained(CompoundTag proof,String key,byte[] bytes){proof.putByteArray(key+"Bytes",bytes);proof.putString(key+"SHA256",TownHallRevisions.sha(bytes));}
    private static void verifyRetained(CompoundTag proof,String key,byte[] current){require(Arrays.equals(current,proof.getByteArray(key+"Bytes"))&&TownHallRevisions.sha(current).equals(proof.getString(key+"SHA256")),"Original retained "+key+" bytes changed");}
    private static CompoundTag metadata(ServerLevel level,String provider,Path root){CompoundTag tag=new CompoundTag();tag.putInt("schema",1);tag.putString("namespace",namespace(provider,true));tag.putString("provider",provider);tag.putString("root",root.toString());tag.putLong("seed",level.getSeed());tag.putString("dimension",level.dimension().location().toString());tag.putString("source",source());tag.putString("process",process());return tag;}
    private static UUID actor(String provider){return UUID.fromString(provider.equals("tacz")?"43000000-0000-0000-0000-0000000000a1":"43000000-0000-0000-0000-0000000000b1");}
    private static UUID reward(String provider){return UUID.fromString(provider.equals("tacz")?"43000000-0000-0000-0000-0000000000a2":"43000000-0000-0000-0000-0000000000b2");}
    private static String name(String provider){return provider.equals("tacz")?"CCClaim43TaCZ":"CCClaim43SBW";}private static String marker(String provider){return provider.equals("tacz")?"TACZ":"SBW";}
    private static String process(){return ProcessHandle.current().pid()+":"+ManagementFactory.getRuntimeMXBean().getStartTime();}
    private static String source(){String source=System.getProperty("catancraft.native43RestartSource","");require(source.matches("[0-9a-f]{40}"),"Explicit 40-hex native43RestartSource required in both JVMs");return source;}
}
