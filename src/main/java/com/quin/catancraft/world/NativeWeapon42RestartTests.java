package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.data.NationData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.quin.catancraft.world.NativeWeapon42Tests.*;

/** One retained native purchase per provider across two distinct JVMs. Seed stops
 * after actual PlayerDataStorage save, leaving original DELIVERING progress;
 * verify reloads original actor, ledger and frozen intent, including the SBW UUID.
 * No actor, item, request or nation is reseeded by verification. */
public final class NativeWeapon42RestartTests {
    private NativeWeapon42RestartTests() {}

    @GameTestHolder("catancraft_native42_tacz_restart") @PrefixGameTestTemplate(false)
    public static final class Tacz {
        @GameTest(template="townhall_state_empty",timeoutTicks=200)
        public static void original_native_gun_and_receipts_recover_across_distinct_jvms(GameTestHelper h)throws Exception{run(h,"tacz");}
    }

    @GameTestHolder("catancraft_native42_sbw_restart") @PrefixGameTestTemplate(false)
    public static final class Sbw {
        @GameTest(template="townhall_state_empty",timeoutTicks=200)
        public static void original_native_uuid_gun_and_receipts_recover_across_distinct_jvms(GameTestHelper h)throws Exception{run(h,"superbwarfare");}
    }

    private static void run(GameTestHelper h,String provider)throws Exception {
        ServerLevel level=h.getLevel();Path root=controlled(level,provider,true);String phase=System.getProperty("catancraft.native42RestartPhase","");
        String shortProvider=provider;
        require(System.getProperty("catancraft.native42RestartProvider","").equals(shortProvider),"Explicit matching native42RestartProvider=tacz|superbwarfare required");
        require(phase.equals("seed")||phase.equals("verify"),"Explicit native42RestartPhase=seed|verify required");source();
        Path proof=root.resolve("data/"+namespace(provider,true)+"_seed.proof"),verified=root.resolve("data/"+namespace(provider,true)+"_verified.proof");
        require(!Files.exists(verified,LinkOption.NOFOLLOW_LINKS),"Already verified native42 world cannot be reused");
        if(phase.equals("seed"))seed(level,provider,root,proof);else verify(level,provider,root,proof,verified);
        require(level.getServer().getPlayerList().getPlayers().isEmpty(),"Synthetic native actor entered the connected roster");h.succeed();
    }

    private static void seed(ServerLevel level,String provider,Path root,Path proof)throws Exception {
        require(!Files.exists(proof,LinkOption.NOFOLLOW_LINKS),"Seed refuses to overwrite original evidence");
        CatanSavedData data=CatanSavedData.get(level.getServer());require(data.nations().isEmpty(),"Seed requires a fresh native nation ledger");
        UUID actor=actor(provider),operation=operation(provider);
        require(data.placedMapAsset("shop40_paid:"+operation)==null&&!Files.exists(root.resolve("playerdata/"+actor+".dat")),"Seed refuses existing actor/payment evidence");
        Fixture f=new Fixture(level,provider,true,actor,name(provider),null);var offer=sample(provider,true);var supply=f.quote(offer);
        CompoundTag quoted=supply.item().save(new CompoundTag());assertNative(offer,supply.item());
        require(f.player.getInventory().isEmpty(),"Native restart actor did not begin empty");
        ShopPurchase40.Request request=f.request(offer,supply,operation);
        require(ShopPurchase40.fixtureCreate(f,request,point->{}).equals(operation),"Original native request was not created");
        byte[] intent=bytes(ShopPurchase40.intentPath(f,operation));assertIntent(f,request,ProducerJournal.read(intent));
        interruptAt(f,operation,ShopPurchase40.SavePoint.AFTER_PLAYER_SAVE);
        f.assertPaid(offer,operation,intent);f.assertDelivered(offer,supply,operation,1);
        byte[] progress=interrupted(f,operation,TownHallRevisions.sha(intent));
        require(ShopPurchase40.pending(f).equals(List.of(operation)),"Original delivered purchase is not pending");
        byte[] player=bytes(f.playerPath()),ledger=bytes(root.resolve("data/catancraft.dat"));
        CompoundTag evidence=metadata(level,provider,root);evidence.putUUID("actor",actor);evidence.putString("name",name(provider));
        evidence.putUUID("operation",operation);evidence.putUUID("nation",f.nation.id());evidence.put("nationAfterDebit",f.nation.save());
        evidence.putString("offer",offer.id());evidence.putString("fingerprint",supply.fingerprint());evidence.put("item",quoted);
        if(provider.equals("superbwarfare"))evidence.putUUID("nativeGunUUID",gunUuid(supply.item()));
        evidence.putByteArray("originalIntentBytes",intent);evidence.putString("intentSHA256",TownHallRevisions.sha(intent));
        evidence.putByteArray("originalProgressBytes",progress);evidence.putString("progressSHA256",TownHallRevisions.sha(progress));
        evidence.putByteArray("originalPlayerBytes",player);evidence.putString("playerSHA256",TownHallRevisions.sha(player));
        evidence.putByteArray("originalLedgerBytes",ledger);evidence.putString("ledgerSHA256",TownHallRevisions.sha(ledger));
        evidence.put("nativeLedger",data.save(new CompoundTag()));evidence.putString("debitReceipt",data.placedMapAsset("shop40_paid:"+operation));
        evidence.putString("deliveryReceipt",receipts(nativeNbt(player)).getString(operation.toString()));
        evidence.putString("result","SEEDED_PENDING_DISTINCT_JVM_VERIFICATION");ProducerJournal.write(proof,evidence,false);
        require(Arrays.equals(intent,bytes(ShopPurchase40.intentPath(f,operation)))&&Arrays.equals(progress,bytes(ShopPurchase40.receiptPath(f,operation))),"Seed changed original transaction history");
        CatanCraft.LOGGER.info("NATIVE42_{}_RESTART_SEED_PASS nativeActor=1 originalNativePlayer=PASS nativeLedger=PASS immutableIntent=PASS nativeItem=1 unloaded=PASS uuid={} progress=DELIVERING process={} source={} verification=PENDING",
                marker(provider),provider.equals("superbwarfare")?gunUuid(supply.item()):"N/A",process(),source());
    }

    private static void verify(ServerLevel level,String provider,Path root,Path proof,Path verified)throws Exception {
        byte[] proofBytes=bytes(proof);CompoundTag evidence=ProducerJournal.read(proofBytes);
        require(evidence.getInt("schema")==1&&namespace(provider,true).equals(evidence.getString("namespace"))&&provider.equals(evidence.getString("provider"))
                &&FIXTURE.equals(evidence.getString("fixture"))&&root.toString().equals(evidence.getString("worldRoot"))
                &&level.dimension().location().toString().equals(evidence.getString("dimension"))&&level.getSeed()==evidence.getLong("seed")
                &&source().equals(evidence.getString("source")),"Foreign native restart source/world/provider evidence");
        require(!evidence.getString("process").isBlank()&&!process().equals(evidence.getString("process")),"Verify requires a distinct JVM");
        UUID actor=actor(provider),operation=operation(provider);
        require(actor.equals(evidence.getUUID("actor"))&&operation.equals(evidence.getUUID("operation"))&&name(provider).equals(evidence.getString("name")),"Original native restart actor/operation changed");
        CatanSavedData data=CatanSavedData.get(level.getServer());NationData nation=data.nation(evidence.getUUID("nation"));
        require(nation!=null&&nation==data.nationForPlayer(actor)&&nation.isLeader(actor)&&nation.save().equals(evidence.getCompound("nationAfterDebit"))
                &&data.save(new CompoundTag()).equals(evidence.getCompound("nativeLedger")),"Original nation/debit did not survive restart");
        byte[] player=bytes(root.resolve("playerdata/"+actor+".dat")),ledger=bytes(root.resolve("data/catancraft.dat"));
        require(Arrays.equals(player,evidence.getByteArray("originalPlayerBytes"))&&TownHallRevisions.sha(player).equals(evidence.getString("playerSHA256"))
                &&nativeNbt(ledger).getCompound("data").equals(evidence.getCompound("nativeLedger")),"Original native files changed before recovery");
        // Native SavedData may be recompressed on ordinary shutdown. Preserve its exact seed
        // bytes as evidence and require the same logical ledger before any verify-side write.
        require(TownHallRevisions.sha(evidence.getByteArray("originalLedgerBytes")).equals(evidence.getString("ledgerSHA256"))
                &&nativeNbt(evidence.getByteArray("originalLedgerBytes")).getCompound("data").equals(evidence.getCompound("nativeLedger")),"Retained native seed ledger evidence changed");
        Fixture f=new Fixture(level,provider,true,actor,name(provider),nation);
        byte[] intent=bytes(ShopPurchase40.intentPath(f,operation));String hash=TownHallRevisions.sha(intent);
        require(Arrays.equals(intent,evidence.getByteArray("originalIntentBytes"))&&hash.equals(evidence.getString("intentSHA256"))
                &&hash.equals(evidence.getString("debitReceipt"))&&hash.equals(evidence.getString("deliveryReceipt")),"Original frozen intent/payment/delivery proof changed");
        var offer=WeaponStock42.offer(evidence.getString("offer"));ShopPurchase40.Request original=ShopPurchase40.inspect(f,operation);
        var supply=NativeWeapon42.recovered(original);
        require(supply!=null&&supply.fingerprint().equals(evidence.getString("fingerprint"))&&supply.item().save(new CompoundTag()).equals(evidence.getCompound("item")),"Recovery did not extract the original intent's native fingerprint and item");
        f.restore(offer,supply);NativeWeapon42.recheck(level.getServer(),offer,supply);assertIntent(f,original,ProducerJournal.read(intent));
        require(original.catalog().equals(NativeWeapon42.frozenCatalog(offer,supply))&&original.offer().equals(offer.id())
                &&original.price().equals(offer.price())&&ItemStack.matches(original.item(),supply.item()),"Recovery did not retain original exact native item/price/fingerprint");
        if(provider.equals("superbwarfare"))require(evidence.hasUUID("nativeGunUUID")&&gunUuid(supply.item()).equals(evidence.getUUID("nativeGunUUID"))
                &&gunUuid(f.player.getInventory().getItem(0)).equals(evidence.getUUID("nativeGunUUID")),"Native SBW UUID changed between JVMs");
        f.assertPaid(offer,operation,intent);f.assertDelivered(offer,supply,operation,1);
        byte[] progress=interrupted(f,operation,hash);
        require(Arrays.equals(progress,evidence.getByteArray("originalProgressBytes"))&&TownHallRevisions.sha(progress).equals(evidence.getString("progressSHA256"))
                &&ShopPurchase40.pending(f).equals(List.of(operation)),"Original delivered pending progress changed across JVMs");
        require(ShopPurchase40.resume(f,operation).complete(),"Original native purchase did not recover");
        // The established 040 recovery contract verifies native ledger persistence once
        // for every nonterminal operation, even with its original debit and delivery.
        // This is a persistence verification, not another charge or item delivery.
        int firstRecoveryLedgerSaves=f.ledgerSaves,firstRecoveryPlayerSaves=f.playerSaves;
        require(firstRecoveryLedgerSaves==1&&firstRecoveryPlayerSaves==0&&Arrays.equals(player,bytes(f.playerPath()))
                &&data.save(new CompoundTag()).equals(evidence.getCompound("nativeLedger"))
                &&nativeNbt(bytes(root.resolve("data/catancraft.dat"))).getCompound("data").equals(nativeNbt(ledger).getCompound("data")),
                "Delivered restart recovery changed accounting/player state or skipped its one verified ledger persistence");
        f.assertPaid(offer,operation,intent);f.assertDelivered(offer,supply,operation,1);
        CompoundTag completion=ProducerJournal.read(bytes(ShopPurchase40.receiptPath(f,operation)));
        require(completion.getString("phase").equals("COMMITTED")&&completion.getUUID("id").equals(operation)&&completion.getString("intentSHA256").equals(hash),"Native completion is not bound to original intent");
        inert(f,operation,intent);
        int completedRetryLedgerSaves=f.ledgerSaves-firstRecoveryLedgerSaves,completedRetryPlayerSaves=f.playerSaves-firstRecoveryPlayerSaves;
        require(completedRetryLedgerSaves==0&&completedRetryPlayerSaves==0,"Completed native retries performed physical saves");
        require(Arrays.equals(proofBytes,bytes(proof)),"Verify rewrote immutable seed proof");
        CompoundTag result=metadata(level,provider,root);result.putString("result","PASS");result.putString("seedProcess",evidence.getString("process"));
        result.putString("seedProofSHA256",TownHallRevisions.sha(proofBytes));result.putString("intentSHA256",hash);
        result.putInt("additionalDebit",0);result.putInt("newItemsAfterRecovery",0);result.putInt("nativeItemCount",1);result.putInt("completedRetries",3);
        result.putInt("firstRecoveryLedgerSaves",firstRecoveryLedgerSaves);result.putInt("firstRecoveryPlayerSaves",firstRecoveryPlayerSaves);
        result.putInt("completedRetryLedgerSaves",completedRetryLedgerSaves);result.putInt("completedRetryPlayerSaves",completedRetryPlayerSaves);
        result.putInt("completedRetrySaves",completedRetryLedgerSaves+completedRetryPlayerSaves);
        result.putString("nativeLedgerBeforeRecoverySHA256",TownHallRevisions.sha(ledger));
        result.putString("nativeLedgerAfterRecoverySHA256",TownHallRevisions.sha(bytes(root.resolve("data/catancraft.dat"))));
        if(provider.equals("superbwarfare"))result.putUUID("nativeGunUUID",gunUuid(supply.item()));
        ProducerJournal.write(verified,result,false);
        CatanCraft.LOGGER.info("NATIVE42_{}_RESTART_VERIFY_PASS distinctJVM=PASS originalNativeActor=PASS originalNativePlayer=PASS originalNativeLedger=PASS immutableIntent=PASS originalProgress=PASS nativeItem=1 additionalDebit=0 newItems=0 firstRecoveryLedgerSaves={} firstRecoveryPlayerSaves={} completedRetrySaves={} retries=3 uuid={} seedProcess={} verifyProcess={} source={}",
                marker(provider),firstRecoveryLedgerSaves,firstRecoveryPlayerSaves,completedRetryLedgerSaves+completedRetryPlayerSaves,
                provider.equals("superbwarfare")?gunUuid(supply.item()):"N/A",evidence.getString("process"),process(),source());
    }

    private static byte[] interrupted(Fixture f,UUID operation,String hash)throws IOException {
        byte[] bytes=bytes(ShopPurchase40.receiptPath(f,operation));CompoundTag progress=ProducerJournal.read(bytes);
        require(progress.getAllKeys().equals(Set.of("schema","id","intentSHA256","phase"))&&progress.getInt("schema")==1
                &&progress.getUUID("id").equals(operation)&&progress.getString("intentSHA256").equals(hash)&&progress.getString("phase").equals("DELIVERING"),"Original native interruption is not DELIVERING");
        return bytes;
    }

    private static void assertIntent(Fixture f,ShopPurchase40.Request original,CompoundTag intent){
        CompoundTag identity=intent.getCompound("identity"),price=new CompoundTag(),resources=new CompoundTag();
        price.putLong("money",original.price().money());original.price().resources().forEach((type,count)->resources.putLong(type.name(),count));price.put("resources",resources);
        require(intent.getUUID("id").equals(original.id())&&intent.getString("offer").equals(original.offer())&&intent.getString("catalog").equals(original.catalog())
                &&intent.getCompound("facility").equals(f.facility)&&intent.getCompound("price").equals(price)&&intent.getCompound("item").equals(original.item().save(new CompoundTag()))
                &&identity.getUUID("actor").equals(f.player.getUUID())&&identity.getUUID("nation").equals(f.nation.id())&&identity.getString("root").equals(f.root.toString())
                &&identity.getString("dimension").equals("minecraft:overworld")&&identity.getLong("seed")==f.level.getSeed()&&intent.getInt("slot")==0,"Original native request identity/price/item changed");
    }

    private static CompoundTag metadata(ServerLevel level,String provider,Path root){
        CompoundTag tag=new CompoundTag();tag.putInt("schema",1);tag.putString("namespace",namespace(provider,true));tag.putString("provider",provider);tag.putString("fixture",FIXTURE);
        tag.putString("source",source());tag.putString("worldRoot",root.toString());tag.putString("dimension",level.dimension().location().toString());tag.putLong("seed",level.getSeed());tag.putString("process",process());return tag;
    }
    private static UUID actor(String provider){return UUID.fromString(provider.equals("tacz")?"42000000-0000-0000-0000-0000000000a1":"42000000-0000-0000-0000-0000000000b1");}
    private static UUID operation(String provider){return UUID.fromString(provider.equals("tacz")?"42000000-0000-0000-0000-0000000000a2":"42000000-0000-0000-0000-0000000000b2");}
    private static String name(String provider){return provider.equals("tacz")?"CCNative42TaCZ":"CCNative42SBW";}
    private static String marker(String provider){return provider.equals("tacz")?"TACZ":"SBW";}
    private static String process(){return ProcessHandle.current().pid()+":"+ManagementFactory.getRuntimeMXBean().getStartTime();}
    private static String source(){String source=System.getProperty("catancraft.native42RestartSource","");require(source.matches("[0-9a-f]{40}"),"Explicit 40-hex native42RestartSource required in both JVMs");return source;}
}
