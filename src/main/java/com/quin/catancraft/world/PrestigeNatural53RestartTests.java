package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import com.google.gson.*;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.economy.EconomyCatalog;
import com.quin.catancraft.map.MapDefinitionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.*;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;

/** Distinct-JVM native SavedData/region proof with two original prestige
 * purchases recovered by the normal startup queue, never a test resume call. */
@GameTestHolder("catancraft_prestige_natural53_restart") @PrefixGameTestTemplate(false)
public final class PrestigeNatural53RestartTests {
    @GameTest(template="townhall_state_empty",timeoutTicks=1800)
    public static void pending_aged_purchases_resume_automatically_in_new_process(GameTestHelper h)throws Exception{
        String phase=System.getProperty("catancraft.natural53Phase","");String source=System.getProperty("catancraft.natural53Source","");
        require(Set.of("seed","verify").contains(phase)&&source.matches("[0-9a-f]{40}"),"Exact natural53 phase/source required");
        var level=h.getLevel();Path root=level.getServer().getWorldPath(LevelResource.ROOT).toRealPath();
        require(level.getServer() instanceof GameTestServer&&root.getParent().getFileName().toString().equals("controlled-catancraft_prestige_natural53_restart-run"),"Isolated controlled restart world required");
        Path dir=root.resolve("data/prestige-natural53-restart");Files.createDirectories(dir);
        if(phase.equals("seed")){seed(h,root,dir,source);h.succeed();return;}
        CompoundTag seed=ProducerJournal.read(dir.resolve("seed.dat"));
        require(seed.getString("source").equals(source)&&seed.getString("world").equals(root.toString())&&!seed.getString("process").equals(process())&&!Files.exists(dir.resolve("verified.dat")),"Foreign or reused restart proof");
        requirePostExitGate(root,dir,source,seed);
        List<SectorChunkLease> leases=new ArrayList<>();
        for(String city:List.of("a","i")){var def=MapDefinitionManager.territory(city);leases.add(SectorChunkLease.acquire(level,TownHallRepairTransaction.bounds(TownHallRevisions.plan(4,2,SchematicAssetRegistry.territoryAnchor("th4",def))).inflate(2)));}
        await(h,root,dir,source,seed,leases,0);
    }
    private static void seed(GameTestHelper h,Path root,Path dir,String source)throws Exception{
        require(!Files.exists(dir.resolve("seed.dat"))&&CatanSavedData.get(h.getLevel().getServer()).nations().isEmpty(),"Restart seed requires a new empty nation ledger");
        ListTag rows=new ListTag();
        for(String city:List.of("a","i")){
            int from=city.equals("a")?3:4;
            var f=new PrestigeNatural53Tests.Fixture(h,city,from,2,true,city.equals("i"));
            var covers=ProducerService.sourceCoveredGrass(from==3?f.after:f.before);
            BlockPos retained=covers.keySet().iterator().next();
            if(from==4)for(BlockPos p:covers.keySet())decay(f.level,p);
            boolean[] stopped={false};var result=f.upgrade((stage,writes)->{
                boolean ready=from==3?stage.equals("write")&&f.level.getBlockState(retained.above()).equals(covers.get(retained)):stage.equals("write")&&writes==31;
                if(ready){if(from==3)decay(f.level,retained);stopped[0]=true;throw new IOException("Intentional prestige natural restart interruption");}
            });
            require(!result.success()&&stopped[0],"Actual purchase capture did not reach interruption");
            byte[] original=Files.readAllBytes(f.journal);CompoundTag journal=PrestigePurchaseRecovery.read(original);
            CompoundTag row=new CompoundTag();row.putString("city",city);row.putInt("from",from);row.putUUID("owner",f.owner);
            row.putUUID("transaction",journal.getUUID("transaction"));row.putString("journalFile",f.journal.getFileName().toString());row.putByteArray("journal",original);
            row.putLong("retainedDirt",retained.asLong());row.put("book",f.book());rows.add(row);
        }
        var server=h.getLevel().getServer();var data=CatanSavedData.get(server);data.setDirty();require(server.saveEverything(true,true,true),"Native seed save failed");
        CompoundTag seed=new CompoundTag();seed.putString("source",source);seed.putString("world",root.toString());seed.putString("process",process());seed.put("ledger",data.save(new CompoundTag()));seed.put("purchases",rows);
        ProducerJournal.write(dir.resolve("seed.dat"),seed,false);
        writeSeedManifest(root,dir,source,seed);
        CatanCraft.LOGGER.info("PRESTIGE_NATURAL53_RESTART_SEED_PASS cities=A,I pending=2 actualNativeDecay=true nativeSavedData=true source={}",source);
    }
    private static void await(GameTestHelper h,Path root,Path dir,String source,CompoundTag seed,List<SectorChunkLease> leases,int ticks){try{
        for(var lease:leases)lease.keepAlive();var data=CatanSavedData.get(h.getLevel().getServer());
        boolean ready=true;for(var tag:seed.getList("purchases",Tag.TAG_COMPOUND)){var row=(CompoundTag)tag;var t=data.territory(row.getString("city"));ready&=t!=null&&t.cityLevel()==row.getInt("from")+1;}
        if(!ready){require(ticks<1500,"Normal startup recovery did not complete both original prestige purchases: "+PaidRecoveryService.status(h.getLevel().getServer(),"a")+"; "+PaidRecoveryService.status(h.getLevel().getServer(),"i"));h.runAfterDelay(5,()->await(h,root,dir,source,seed,leases,ticks+5));return;}
        var expected=CatanSavedData.load(seed.getCompound("ledger"));
        for(var tag:seed.getList("purchases",Tag.TAG_COMPOUND)){
            var row=(CompoundTag)tag;String city=row.getString("city");int from=row.getInt("from"),to=from+1;
            EconomyCatalog.cityUpgradeCost(from).charge(expected.nation(row.getUUID("owner")));expected.territory(city).setCityLevel(to);expected.setPlacedMapAsset("territory:"+city+":town_hall",TownHallRevisions.token(to,2));PaidConstructionRecovery.receipt(expected,row.getUUID("transaction"));
            Path journal=root.resolve("data").resolve(row.getString("journalFile"));require(Arrays.equals(row.getByteArray("journal"),Files.readAllBytes(journal))&&PaidConstructionRecovery.complete(journal,row.getByteArray("journal")),"Startup changed original purchase or did not commit companion");
            var plan=TownHallRevisions.plan(to,2,SchematicAssetRegistry.territoryAnchor("th"+to,MapDefinitionManager.territory(city)));var covers=ProducerService.sourceCoveredGrass(plan);var level=h.getLevel();BlockPos manager=plan.get(TownHallRevisions.managerIndex()).position();
            for(var cell:plan){var live=level.getBlockState(cell.position());require(TownHallInteractionPolicy.matches(live,cell.state(),cell.position().equals(manager))||PrestigeNaturalSupport.decayed(level,cell.position(),cell.state(),live,covers.get(cell.position())),"Restart target mismatch at "+cell.position());}
            require(level.getBlockState(BlockPos.of(row.getLong("retainedDirt"))).equals(Blocks.DIRT.defaultBlockState())&&row.getCompound("book").equals(level.getBlockEntity(manager).saveWithFullMetadata()),"Restart repainted retained soil or management book");
        }
        require(expected.save(new CompoundTag()).equals(data.save(new CompoundTag())),"Restart charged more than original prices or changed unrelated ledger");
        require(h.getLevel().getServer().saveEverything(true,true,true),"Native verification save failed");
        requirePostExitGate(root,dir,source,seed);
        CompoundTag result=new CompoundTag();result.putString("result","PASS");result.putString("source",source);result.putString("seedProcess",seed.getString("process"));result.putString("verifyProcess",process());result.put("ledger",data.save(new CompoundTag()));result.putString("seedSHA256",TownHallRevisions.sha(Files.readAllBytes(dir.resolve("seed.dat"))));result.putString("postExitGateSHA256",TownHallRevisions.sha(Files.readAllBytes(dir.resolve("post-exit-gate.json"))));ProducerJournal.write(dir.resolve("verified.dat"),result,false);
        for(var lease:leases)lease.close();CatanCraft.LOGGER.info("PRESTIGE_NATURAL53_RESTART_VERIFY_PASS cities=A,I automaticStartup=true distinctJVM=true retainedSoil=true originalHistory=true originalPricesOnce=true source={}",source);h.succeed();
    }catch(Exception failure){for(var lease:leases)lease.close();throw new RuntimeException(failure);}}
    private static void writeSeedManifest(Path root,Path dir,String source,CompoundTag seed)throws IOException{
        Path dataFile=root.resolve("data/catancraft.dat");
        require(NbtIo.readCompressed(dataFile.toFile()).getCompound("data").equals(seed.getCompound("ledger")),"Native seed file differs from captured ledger");
        JsonObject manifest=new JsonObject();manifest.addProperty("schema",1);manifest.addProperty("purpose","prestige-natural53-pending-seed-v1");
        manifest.addProperty("source",source);manifest.addProperty("world",root.toString());manifest.addProperty("seedProcess",seed.getString("process"));
        manifest.addProperty("seedProofFile","data/prestige-natural53-restart/seed.dat");manifest.addProperty("seedProofSHA256",TownHallRevisions.sha(Files.readAllBytes(dir.resolve("seed.dat"))));
        manifest.addProperty("savedDataFile","data/catancraft.dat");manifest.addProperty("savedDataSHA256",TownHallRevisions.sha(Files.readAllBytes(dataFile)));
        JsonArray purchases=new JsonArray();
        for(var tag:seed.getList("purchases",Tag.TAG_COMPOUND)){
            var row=(CompoundTag)tag;int from=row.getInt("from");var journal=PrestigePurchaseRecovery.read(row.getByteArray("journal"));
            JsonObject p=new JsonObject();p.addProperty("city",row.getString("city"));p.addProperty("fromLevel",from);p.addProperty("toLevel",from+1);p.addProperty("ownerNation",row.getUUID("owner").toString());p.addProperty("transaction",row.getUUID("transaction").toString());
            p.addProperty("sourceToken",journal.getString("sourceToken"));p.addProperty("targetToken",journal.getString("targetToken"));p.addProperty("journalFile","data/"+row.getString("journalFile"));p.addProperty("journalSHA256",TownHallRevisions.sha(row.getByteArray("journal")));p.addProperty("expectedPhase","PENDING");p.addProperty("companionFile","data/"+row.getString("journalFile")+".recovery-v1.dat");p.addProperty("companionMustBeAbsent",true);p.addProperty("receiptKey",PaidConstructionRecovery.receiptKey(row.getUUID("transaction")));p.addProperty("receiptMustBeAbsent",true);purchases.add(p);
        }
        manifest.add("purchases",purchases);Files.writeString(dir.resolve("seed-manifest.json"),new GsonBuilder().setPrettyPrinting().create().toJson(manifest)+"\n",StandardOpenOption.CREATE_NEW);
    }
    /** Written only by the outer runner after observing seed process exit and
     * checking persisted pending state. The verify JVM cannot certify its own
     * pre-start state after the automatic recovery queue has already run. */
    private static void requirePostExitGate(Path root,Path dir,String source,CompoundTag seed)throws IOException{
        Path file=dir.resolve("post-exit-gate.json"),manifestFile=dir.resolve("seed-manifest.json");
        require(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)&&!Files.isSymbolicLink(file)&&Files.size(file)<16384,"Missing checked post-exit seed gate; outer runner must inspect seed files before verify starts");
        require(Files.isRegularFile(manifestFile,LinkOption.NOFOLLOW_LINKS)&&!Files.isSymbolicLink(manifestFile)&&Files.size(manifestFile)<32768,"Missing seed manifest");
        JsonObject gate=JsonParser.parseString(Files.readString(file)).getAsJsonObject(),manifest=JsonParser.parseString(Files.readString(manifestFile)).getAsJsonObject();
        require(gate.keySet().equals(Set.of("schema","purpose","source","world","seedProcess","seedManifestSHA256","seedProofSHA256","savedDataSHA256","checkedAfterSeedExit","seedProcessAlive","originalsPending","companionsAbsent")),"Unknown post-exit gate fields");
        require(gate.get("schema").getAsInt()==1&&gate.get("purpose").getAsString().equals("prestige-natural53-post-exit-gate-v1")
                &&gate.get("source").getAsString().equals(source)&&gate.get("world").getAsString().equals(root.toString())&&gate.get("seedProcess").getAsString().equals(seed.getString("process"))
                &&gate.get("seedManifestSHA256").getAsString().equals(TownHallRevisions.sha(Files.readAllBytes(manifestFile)))
                &&gate.get("seedProofSHA256").getAsString().equals(TownHallRevisions.sha(Files.readAllBytes(dir.resolve("seed.dat"))))
                &&gate.get("savedDataSHA256").getAsString().equals(manifest.get("savedDataSHA256").getAsString())
                &&gate.get("checkedAfterSeedExit").getAsBoolean()&&!gate.get("seedProcessAlive").getAsBoolean()
                &&gate.get("originalsPending").getAsBoolean()&&gate.get("companionsAbsent").getAsBoolean(),"Post-exit gate does not prove the original pending seed state");
    }
    private static void decay(ServerLevel level,BlockPos p){require(level.getBlockState(p).equals(Blocks.GRASS_BLOCK.defaultBlockState()),"Exact grass required before native decay");Blocks.GRASS_BLOCK.randomTick(level.getBlockState(p),level,p,RandomSource.create(53));require(level.getBlockState(p).equals(Blocks.DIRT.defaultBlockState()),"Native cover did not decay grass");}
    private static String process(){return ProcessHandle.current().pid()+":"+ManagementFactory.getRuntimeMXBean().getStartTime();}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
