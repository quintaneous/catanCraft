package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
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
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One saved weighted capture in two distinct JVMs against original native files.
 * The explicitly scoped synthetic table is recovered through the marked fixture
 * adapter, not automatic production startup. No actor, item or vehicle is spawned;
 * all three equipment types remain the exact original pending entitlements. */
@GameTestHolder("catancraft_rewards41_restart") @PrefixGameTestTemplate(false)
public final class MonumentRewards41RestartTests {
    private static final String NS="catancraft_rewards41_restart";
    private static final String FIXTURE="synthetic-rewards41-restart-v1";
    private static final String MONUMENT="fixture41_restart";
    private static final UUID ACTIVATION=UUID.fromString("41000000-0000-0000-0000-000000000041");
    private static final UUID LEADER=UUID.fromString("41000000-0000-0000-0000-000000000042");
    private static final long MONEY=10_000,STOCK=80;
    private MonumentRewards41RestartTests() {}

    @GameTest(template="townhall_state_empty",timeoutTicks=200)
    public static void captured_weighted_outcome_recovers_once_in_a_distinct_jvm(GameTestHelper h)throws Exception {
        Model env=new Model(h.getLevel());String phase=System.getProperty("catancraft.rewardsRestartPhase","");
        require(phase.equals("seed")||phase.equals("verify"),"Explicit reward seed|verify JVM phase required");source();
        Path proof=env.root.resolve("data/catancraft_rewards41_restart_seed.proof");
        Path verified=env.root.resolve("data/catancraft_rewards41_restart_verified.proof");
        require(!Files.exists(verified,LinkOption.NOFOLLOW_LINKS),"Already-verified reward world cannot be reused");
        if(phase.equals("seed"))seed(env,proof);else verify(env,proof,verified);
        require(env.server.getPlayerList().getPlayers().isEmpty(),"Reward restart fixture must never register a player");
        h.succeed();
    }

    private static RewardTable41 table(){
        RewardTable41.Bundle common=new RewardTable41.Bundle(250,Map.of(ResourceType.WOOD,4L),List.of());
        return new RewardTable41(FIXTURE,List.of(new RewardTable41.Entry("common_fixture",9,common),
                new RewardTable41.Entry("equipment_fixture",1,equipment())));
    }
    private static RewardTable41.Bundle equipment(){
        return new RewardTable41.Bundle(625,Map.of(ResourceType.STEEL,7L,ResourceType.MECHANICAL_PARTS,2L),List.of(
                new RewardTable41.Entitlement(RewardTable41.Kind.GUN,"tacz:ai_awp",1,FIXTURE),
                new RewardTable41.Entitlement(RewardTable41.Kind.AMMO,"tacz:338",32,FIXTURE),
                new RewardTable41.Entitlement(RewardTable41.Kind.VEHICLE,"superbwarfare:sodayo_pick_up",1,FIXTURE)));
    }
    /** Select an equipment outcome only for this disclosed synthetic boundary test.
     * Production rolls and statistical distribution coverage are separate. */
    private static long equipmentSeed(RewardTable41 table){
        for(long seed=0;seed<4096;seed++)if(table.roll(seed).entryId().equals("equipment_fixture"))return seed;
        throw new AssertionError("Bounded synthetic weighted-table seed selection failed");
    }
    private static void seed(Model env,Path proof)throws Exception {
        require(!Files.exists(proof,LinkOption.NOFOLLOW_LINKS)&&env.data.nationForPlayer(LEADER)==null&&env.data.monument(MONUMENT)==null,
                "Seed refuses prior synthetic nation/monument/proof");
        require(!Files.exists(MonumentRewards41.intentPath(env,ACTIVATION),LinkOption.NOFOLLOW_LINKS)
                &&!Files.exists(MonumentRewards41.receiptPath(env,ACTIVATION),LinkOption.NOFOLLOW_LINKS),"Seed refuses existing reward history");
        NationData nation=env.data.createNation("CCReward41Restart",LEADER);nation.setTreasury(MONEY);
        for(ResourceType resource:ResourceType.values())nation.addResource(resource,STOCK);
        MonumentData monument=env.data.createMonument(MONUMENT,"Synthetic reward restart",MonumentType.MILITARY_DEPOT,
                "minecraft:overworld",4096,140,4096,8);
        env.data.setActiveMonumentId(monument.id());env.data.setLastActivatedMonumentId(monument.id());
        env.data.setNextMonumentActivationGameTime(0);env.persist();
        CompoundTag before=nation.save().copy();Set<UUID> entities=entities(env.level);
        RewardTable41 table=table();long seed=equipmentSeed(table);
        MonumentRewards41.fixturePrepare(env,monument,table,seed,ACTIVATION,point->{});
        CompoundTag prepared=activation(monument);
        require(prepared.getString("phase").equals("OPEN")&&prepared.hasUUID("id")&&prepared.getUUID("id").equals(ACTIVATION)
                &&!prepared.hasUUID("winner")&&prepared.getCompound("identity").getString("scope").equals("controlled_fixture")
                &&prepared.getString("entry").equals("equipment_fixture")&&prepared.getCompound("roll").equals(equipment().save()),
                "Activation did not save the exact selected weighted equipment outcome");
        require(nation.save().equals(before),"Hidden activation paid the nation before a capture");
        require(nativeLedger(env).equals(env.data.save(new CompoundTag())),"Hidden outcome was not saved through actual native SavedData");
        byte[] preparedLedger=bytes(env.ledger());
        monument.setCapturingNationId(nation.id());monument.setCaptureProgressTicks(MonumentManager.CAPTURE_TICKS);env.data.setDirty();
        IOException stop=new IOException("Intentional reward restart interruption after native winner save");boolean[] reached={false};
        try {
            MonumentRewards41.fixtureCapture(env,monument,nation.id(),point->{
                if(point==MonumentRewards41.SavePoint.AFTER_CAPTURE_SAVE){reached[0]=true;throw stop;}
            });
            throw new AssertionError("Capture did not stop at the requested durable boundary");
        }catch(IOException failure){if(failure!=stop)throw failure;}
        require(reached[0],"Requested saved winner boundary was not reached");
        CompoundTag captured=activation(monument);assertCaptured(captured,nation.id(),prepared);
        require(nation.save().equals(before),"Interrupted winner save had already credited the reward");
        require(nativeLedger(env).equals(env.data.save(new CompoundTag())),"Captured winner/outcome native readback differs");
        require(!Files.exists(MonumentRewards41.intentPath(env,ACTIVATION),LinkOption.NOFOLLOW_LINKS)
                &&!Files.exists(MonumentRewards41.receiptPath(env,ACTIVATION),LinkOption.NOFOLLOW_LINKS),
                "AFTER_CAPTURE_SAVE must precede reward intent and credit receipt");
        require(entities(env.level).equals(entities),"Preparing/capturing entitlement references spawned entities");
        byte[] ledger=bytes(env.ledger());
        CompoundTag record=metadata(env);record.putUUID("activation",ACTIVATION);record.putUUID("nation",nation.id());
        record.putString("monument",MONUMENT);record.putLong("syntheticDrawSeed",seed);record.put("originalTable",table.save());
        record.put("preparedActivation",prepared);record.put("capturedActivation",captured);record.put("nationBefore",before);
        record.putByteArray("preparedNativeLedgerBytes",preparedLedger);record.putString("preparedNativeLedgerSHA256",TownHallRevisions.sha(preparedLedger));
        record.put("expectedReward",equipment().save());record.put("capturedNativeLedger",env.data.save(new CompoundTag()));
        record.putByteArray("originalNativeLedgerBytes",ledger);record.putString("originalNativeLedgerSHA256",TownHallRevisions.sha(ledger));
        record.putInt("nativeSavesBeforeRestart",env.saves);record.putString("result","SEEDED_CAPTURED_PENDING_DISTINCT_JVM_VERIFICATION");
        ProducerJournal.write(proof,record,false);
        CatanCraft.LOGGER.info("REWARDS41_RESTART_SEED_PASS hiddenWeightedRoll=PASS nativeWinner=PASS phase=CAPTURED credit=0 equipmentSpawned=0 entitlements=3 fixtureRecoveryScope=EXPLICIT process={} source={}",process(),source());
    }
    private static void verify(Model env,Path proof,Path verified)throws Exception {
        byte[] originalProof=bytes(proof);CompoundTag record=ProducerJournal.read(originalProof);
        require(record.getInt("schema")==1&&record.getString("namespace").equals(NS)&&record.getString("fixture").equals(FIXTURE)
                &&record.getString("source").equals(source())&&record.getString("worldRoot").equals(env.root.toString())
                &&record.getLong("worldSeed")==env.level.getSeed()&&record.getUUID("activation").equals(ACTIVATION)
                &&record.getString("monument").equals(MONUMENT),"Foreign restart source/world/activation evidence");
        require(!record.getString("process").isBlank()&&!record.getString("process").equals(process()),"Verification requires a distinct JVM");
        byte[] originalLedger=bytes(env.ledger());
        require(Arrays.equals(originalLedger,record.getByteArray("originalNativeLedgerBytes"))
                &&TownHallRevisions.sha(originalLedger).equals(record.getString("originalNativeLedgerSHA256")),
                "Original captured native ledger bytes changed before explicit fixture recovery");
        require(nativeLedger(env).equals(record.getCompound("capturedNativeLedger"))
                &&env.data.save(new CompoundTag()).equals(record.getCompound("capturedNativeLedger")),"Original native winner/outcome did not survive restart");
        NationData nation=env.data.nation(record.getUUID("nation"));MonumentData monument=env.data.monument(MONUMENT);
        require(nation!=null&&nation==env.data.nationForPlayer(LEADER)&&nation.isLeader(LEADER)&&monument!=null,
                "Original winning nation/monument missing; verification never reseeds them");
        require(nation.save().equals(record.getCompound("nationBefore")),"Nation was credited before explicit fixture recovery");
        require(activation(monument).equals(record.getCompound("capturedActivation")),"Original captured outcome/winner changed across JVMs");
        assertCaptured(activation(monument),nation.id(),record.getCompound("preparedActivation"));
        byte[] preparedLedger=record.getByteArray("preparedNativeLedgerBytes");
        require(TownHallRevisions.sha(preparedLedger).equals(record.getString("preparedNativeLedgerSHA256"))
                &&nativeMonument(nativeNbt(preparedLedger).getCompound("data")).get("rewardActivation41").equals(record.getCompound("preparedActivation")),
                "Original hidden activation lacks its exact native-save bytes");
        require(!Files.exists(MonumentRewards41.intentPath(env,ACTIVATION),LinkOption.NOFOLLOW_LINKS)
                &&!Files.exists(MonumentRewards41.receiptPath(env,ACTIVATION),LinkOption.NOFOLLOW_LINKS),
                "Original pre-credit boundary unexpectedly already has reward history");
        require(record.getCompound("expectedReward").equals(equipment().save()),"Expected equipment bundle changed");
        require(RewardTable41.load(record.getCompound("originalTable")).roll(record.getLong("syntheticDrawSeed")).reward().save()
                .equals(record.getCompound("expectedReward")),"Recorded original table does not explain its original weighted outcome");
        // Recovery receives no table or random seed. A different current table cannot reroll this saved capture.
        Set<UUID> entities=entities(env.level);
        require(MonumentRewards41.fixtureRecover(env,point->{}),"Original captured reward did not recover");
        assertCredited(nation,record.getCompound("nationBefore"),equipment());
        require(env.data.activeMonument()==null&&monument.captureProgressTicks()==0&&monument.capturingNationId()==null,
                "Successful settlement did not atomically close the captured monument");
        require(entities(env.level).equals(entities),"Reward entitlements were delivered as spawned entities");
        CompoundTag settled=activation(monument);require(settled.getString("phase").equals("SETTLED")
                &&settled.getUUID("winner").equals(nation.id())&&settled.getCompound("roll").equals(equipment().save()),
                "Settlement lost the original winner or reserved item/vehicle references");
        Path intent=MonumentRewards41.intentPath(env,ACTIVATION),receipt=MonumentRewards41.receiptPath(env,ACTIVATION);
        byte[] intentBytes=bytes(intent),receiptBytes=bytes(receipt),settledLedger=bytes(env.ledger());
        CompoundTag expectedIntent=record.getCompound("capturedActivation").copy();expectedIntent.remove("phase");
        CompoundTag actualIntent=ProducerJournal.read(intentBytes),actualReceipt=ProducerJournal.read(receiptBytes);
        String intentHash=TownHallRevisions.sha(intentBytes),paidKey="monument41_paid:"+ACTIVATION;
        require(actualIntent.equals(expectedIntent),"Immutable intent differs from the exact original captured winner/outcome");
        require(actualReceipt.getAllKeys().equals(Set.of("schema","id","intentSHA256","phase"))
                &&actualReceipt.getInt("schema")==1&&actualReceipt.hasUUID("id")&&actualReceipt.getUUID("id").equals(ACTIVATION)
                &&actualReceipt.getString("phase").equals("COMMITTED")&&actualReceipt.getString("intentSHA256").equals(intentHash),
                "Completion receipt does not seal the original reward intent");
        require(intentHash.equals(env.data.placedMapAsset(paidKey))
                &&nativeLedger(env).getCompound("placedMapAssets").getString(paidKey).equals(intentHash)
                &&nativeLedger(env).equals(env.data.save(new CompoundTag())),
                "National credit and original intent hash were not saved together in the native ledger");
        require(RewardTable41.Bundle.load(actualIntent.getCompound("roll")).entitlements().equals(equipment().entitlements()),
                "Pending gun, ammo or vehicle identities changed in the durable intent");
        CompoundTag savedLedger=env.data.save(new CompoundTag());int saved=env.saves;
        for(int retry=0;retry<3;retry++)require(!MonumentRewards41.fixtureRecover(env,point->{}),"Completed reward remained pending on retry");
        require(env.saves==saved&&savedLedger.equals(env.data.save(new CompoundTag()))
                &&Arrays.equals(settledLedger,bytes(env.ledger()))&&Arrays.equals(intentBytes,bytes(intent))
                &&Arrays.equals(receiptBytes,bytes(receipt))&&Arrays.equals(originalProof,bytes(proof)),
                "Completed retries credited, saved, rerolled or rewrote original reward evidence");
        assertCredited(nation,record.getCompound("nationBefore"),equipment());
        require(entities(env.level).equals(entities),"Completed retries spawned pending equipment");
        CompoundTag outcome=record.copy();outcome.putString("seedProcess",record.getString("process"));outcome.putString("process",process());
        outcome.putString("seedProofSHA256",TownHallRevisions.sha(originalProof));outcome.putString("result","PASS");
        outcome.put("settledActivation",settled);outcome.put("nationAfterRecovery",nation.save());
        outcome.putInt("recoveryNativeSaves",saved);outcome.putInt("completedRetries",3);outcome.putInt("completedRetrySaves",0);
        outcome.putInt("pendingEntitlements",equipment().entitlements().size());outcome.putInt("spawnedEquipment",0);
        outcome.putString("intentFile",intent.getFileName().toString());outcome.putString("intentSHA256",TownHallRevisions.sha(intentBytes));
        outcome.putByteArray("intentBytes",intentBytes);outcome.putByteArray("receiptBytes",receiptBytes);
        outcome.putString("receiptFile",receipt.getFileName().toString());outcome.putString("receiptSHA256",TownHallRevisions.sha(receiptBytes));
        outcome.putString("settledNativeLedgerSHA256",TownHallRevisions.sha(settledLedger));outcome.put("settledNativeLedger",savedLedger);
        ProducerJournal.write(verified,outcome,false);
        CatanCraft.LOGGER.info("REWARDS41_RESTART_VERIFY_PASS distinctJVM=PASS originalCapturedNativeBytes=PASS originalWeightedOutcome=PASS originalWinner=PASS creditOnce=PASS entitlements=3 equipmentSpawned=0 completedRetrySaves=0 fixtureRecoveryScope=EXPLICIT seedProcess={} verifyProcess={} source={}",record.getString("process"),process(),source());
    }
    private static void assertCaptured(CompoundTag captured,UUID winner,CompoundTag prepared){
        require(captured.getUUID("id").equals(ACTIVATION)&&captured.getString("phase").equals("CAPTURED")
                &&captured.getUUID("winner").equals(winner)&&captured.getString("tableRevision").equals(prepared.getString("tableRevision"))
                &&captured.getString("tableSHA256").equals(prepared.getString("tableSHA256"))
                &&captured.getCompound("identity").equals(prepared.getCompound("identity"))
                &&captured.getCompound("monument").equals(prepared.getCompound("monument"))
                &&captured.getString("entry").equals(prepared.getString("entry"))&&captured.getCompound("roll").equals(prepared.getCompound("roll")),
                "Captured outcome, original table or winning nation changed");
    }
    private static void assertCredited(NationData nation,CompoundTag before,RewardTable41.Bundle reward){
        require(nation.treasury()==Math.addExact(before.getLong("treasury"),reward.money()),"Original money reward was not credited exactly once");
        NationData baseline=NationData.load(before);
        for(ResourceType type:ResourceType.values())require(nation.resource(type)==Math.addExact(baseline.resource(type),reward.resources().getOrDefault(type,0L)),
                "Original resource reward was not credited exactly once: "+type);
    }
    private static CompoundTag activation(MonumentData monument)throws IOException {
        Tag state=monument.rewardActivation41();if(!(state instanceof CompoundTag n))throw new IOException("Missing native reward activation");return n;
    }
    private static Set<UUID> entities(ServerLevel level){Set<UUID> ids=new HashSet<>();level.getAllEntities().forEach(entity->ids.add(entity.getUUID()));return Set.copyOf(ids);}
    private static CompoundTag nativeLedger(Model env)throws IOException {return nativeNbt(bytes(env.ledger())).getCompound("data");}
    private static CompoundTag nativeMonument(CompoundTag ledger)throws IOException {
        var monuments=ledger.getList("monuments",Tag.TAG_COMPOUND);CompoundTag found=null;
        for(int i=0;i<monuments.size();i++)if(monuments.getCompound(i).getString("id").equals(MONUMENT)){
            if(found!=null)throw new IOException("Ambiguous retained native monument");found=monuments.getCompound(i);
        }
        if(found==null)throw new IOException("Retained native monument missing");return found;
    }
    private static CompoundTag nativeNbt(byte[] bytes)throws IOException {try(var in=new ByteArrayInputStream(bytes)){return NbtIo.readCompressed(in);}}
    private static byte[] bytes(Path path)throws IOException {
        if(Files.isSymbolicLink(path)||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>8*1024*1024+40)
            throw new IOException("Missing/invalid retained native reward evidence: "+path.getFileName());return Files.readAllBytes(path);
    }
    private static CompoundTag metadata(Model env){
        CompoundTag n=new CompoundTag();n.putInt("schema",1);n.putString("namespace",NS);n.putString("fixture",FIXTURE);
        n.putString("source",source());n.putString("worldRoot",env.root.toString());n.putLong("worldSeed",env.level.getSeed());n.putString("process",process());return n;
    }
    private static String process(){return ProcessHandle.current().pid()+":"+ManagementFactory.getRuntimeMXBean().getStartTime();}
    private static String source(){String s=System.getProperty("catancraft.rewardsRestartSource","");require(s.matches("[0-9a-f]{40}"),"Same exact source commit required in both reward JVMs");return s;}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static final class Model implements MonumentRewards41.Environment {
        final ServerLevel level;final MinecraftServer server;final CatanSavedData data;final Path root;int saves;
        Model(ServerLevel level)throws IOException {
            this.level=level;server=level.getServer();root=server.getWorldPath(LevelResource.ROOT).toRealPath();
            if(!(server instanceof GameTestServer)||!server.isSameThread()||!server.getPlayerList().getPlayers().isEmpty()
                    ||!root.getFileName().toString().equals("world")||!root.getParent().getFileName().toString().equals("controlled-"+NS+"-run")
                    ||!Files.readString(root.getParent().resolve(".catancraft-controlled-fixture-v1")).equals("CatanCraft synthetic flat fixture v1\n"))
                throw new IOException("Reward restart requires its marked empty disposable native world");
            data=CatanSavedData.get(server);
        }
        public MinecraftServer server(){return server;}public CatanSavedData data(){return data;}
        public void persist(){saves++;data.setDirty();server.overworld().getDataStorage().save();}
        Path ledger(){return root.resolve("data/catancraft.dat");}
    }
}
