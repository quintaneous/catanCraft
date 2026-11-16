package com.quin.catancraft.world;

import com.google.gson.JsonObject;
import com.quin.catancraft.data.*;
import com.quin.catancraft.economy.*;
import com.quin.catancraft.map.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Native synthetic fixtures using pinned complete halls and real production
 * journal capture. Not authored-world, client, or owner acceptance evidence. */
@GameTestHolder("catancraft_prestige_natural53") @PrefixGameTestTemplate(false)
public final class PrestigeNatural53Tests {
    private static final int FLAGS=PaidConstructionRecovery.FLAGS;
    private PrestigeNatural53Tests(){}
    @GameTest(template="townhall_state_empty",batch="prestige53_01",timeoutTicks=12000)
    public static void north_th3_partial_target_decay_recovers_once(GameTestHelper h)throws Exception{partial(h,"a",false);}
    @GameTest(template="townhall_state_empty",batch="prestige53_02",timeoutTicks=12000)
    public static void south_th3_partial_stair_cover_decay_recovers_once(GameTestHelper h)throws Exception{partial(h,"i",true);}
    private static void partial(GameTestHelper h,String city,boolean stair)throws Exception {
        try(Fixture f=new Fixture(h,city,3,1)){
            var covers=ProducerService.sourceCoveredGrass(f.after);
            BlockPos p=covers.entrySet().stream().filter(e->stair?e.getValue().getBlock() instanceof StairBlock:e.getValue().is(Blocks.POLISHED_DEEPSLATE))
                    .map(Map.Entry::getKey).findFirst().orElseThrow();
            CompoundTag ledger=f.data.save(new CompoundTag()),book=f.book();boolean[] interrupted={false};
            var result=f.upgrade((stage,writes)->{
                if(stage.equals("write")&&f.level.getBlockState(p.above()).equals(covers.get(p))){
                    decay(h,f.level,p);interrupted[0]=true;throw new IOException("Injected native target-decay interruption");
                }
            });
            h.assertTrue(!result.success()&&interrupted[0]&&ledger.equals(f.data.save(new CompoundTag())),"Expected pending production journal without charge");
            byte[] original=Files.readAllBytes(f.journal);CompoundTag journal=PrestigePurchaseRecovery.read(original);
            h.assertTrue(journal.getString("phase").equals("PENDING")&&journal.contains(PrestigeNaturalSupport.FIELD),"Missing actual production natural-cover proof");
            f.reload();var e=f.recovery();PaidRecoveryTests.interrupt(h,e,"write",31);f.reload();
            PaidConstructionRecovery.resume(f.recovery());f.reload();f.assertPaidOnce(ledger,journal);
            h.assertTrue(f.level.getBlockState(p).equals(Blocks.DIRT.defaultBlockState())&&f.book().equals(book),"Recovery repainted dirt or changed management book");
            f.assertTarget();CompoundTag paid=f.data.save(new CompoundTag());
            h.assertTrue(PaidConstructionRecovery.resume(f.recovery()).alreadyComplete()&&paid.equals(f.data.save(new CompoundTag())),"Repeated recovery changed paid ledger");
            h.assertTrue(Arrays.equals(original,Files.readAllBytes(f.journal)),"Recovery rewrote original captured history");h.succeed();
        }
    }
    @GameTest(template="townhall_state_empty",batch="prestige53_03",timeoutTicks=12000)
    public static void north_th4_r1_aged_reads_upgrade_without_soil_writes(GameTestHelper h)throws Exception{aged(h,"a",1);}
    @GameTest(template="townhall_state_empty",batch="prestige53_04",timeoutTicks=12000)
    public static void south_th4_r2_aged_reads_upgrade_without_soil_writes(GameTestHelper h)throws Exception{aged(h,"i",2);}
    private static void aged(GameTestHelper h,String city,int revision)throws Exception{
        try(Fixture f=new Fixture(h,city,4,revision)){
            var covers=ProducerService.sourceCoveredGrass(f.before);h.assertTrue(covers.size()==14,"Pinned TH4 covered source changed");
            for(BlockPos p:covers.keySet())decay(h,f.level,p);
            CompoundTag ledger=f.data.save(new CompoundTag()),book=f.book();var result=f.upgrade((s,n)->{});
            h.assertTrue(result.success(),"Aged production upgrade refused: "+result.message());
            CompoundTag journal=PrestigePurchaseRecovery.read(Files.readAllBytes(f.journal));f.assertPaidOnce(ledger,journal);
            var proof=PrestigeNaturalSupport.read(journal,f.before,f.after);
            for(BlockPos p:covers.keySet())h.assertTrue(f.level.getBlockState(p).equals(Blocks.DIRT.defaultBlockState())
                    &&proof.before(p).equals(Blocks.DIRT.defaultBlockState()),"Captured dirt read lost");
            for(var tag:journal.getList("blocks",Tag.TAG_COMPOUND))h.assertTrue(!covers.containsKey(BlockPos.of(((CompoundTag)tag).getLong("pos"))),"Unchanged aged soil entered write set");
            f.assertTarget();h.assertTrue(book.equals(f.book()),"Management book changed");
            CompoundTag paid=f.data.save(new CompoundTag());byte[] original=Files.readAllBytes(f.journal);
            h.assertTrue(!f.upgrade((s,n)->{}).success()&&paid.equals(f.data.save(new CompoundTag()))&&Arrays.equals(original,Files.readAllBytes(f.journal)),"Duplicate purchase changed history or payment");h.succeed();
        }
    }
    @GameTest(template="townhall_state_empty",batch="prestige53_05",timeoutTicks=12000)
    public static void target_dirt_without_owned_cover_refuses_recovery(GameTestHelper h)throws Exception{
        try(Fixture f=new Fixture(h,"a",3,1)){
            h.assertTrue(!f.upgrade((s,n)->{if(s.equals("prepared"))throw new IOException("Injected before-write interruption");}).success(),"Expected pending journal");
            BlockPos p=ProducerService.sourceCoveredGrass(f.after).keySet().iterator().next();
            h.assertTrue(f.level.getBlockState(p.above()).isAir(),"TH3 future cover must begin as air");
            f.level.setBlock(p,Blocks.DIRT.defaultBlockState(),FLAGS);f.refuseRecovery();
            f.level.setBlock(p.above(),Blocks.GOLD_BLOCK.defaultBlockState(),FLAGS);f.refuseRecovery();
            f.level.setBlock(p,Blocks.COARSE_DIRT.defaultBlockState(),FLAGS);f.refuseRecovery();h.succeed();
        }
    }
    @GameTest(template="townhall_state_empty",batch="prestige53_06",timeoutTicks=12000)
    public static void source_exposed_soil_and_foreign_cover_refuse_before_capture(GameTestHelper h)throws Exception{
        try(Fixture f=new Fixture(h,"i",4,2)){
            var covers=ProducerService.sourceCoveredGrass(f.before);BlockPos p=covers.keySet().iterator().next();
            decay(h,f.level,p);f.level.setBlock(p.above(),Blocks.GOLD_BLOCK.defaultBlockState(),FLAGS);f.refusePurchase();
            f.level.setBlock(p.above(),covers.get(p),FLAGS);f.level.setBlock(p,Blocks.COARSE_DIRT.defaultBlockState(),FLAGS);f.refusePurchase();
            f.level.setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),FLAGS);
            BlockPos exposed=f.before.stream().filter(c->c.state().equals(Blocks.GRASS_BLOCK.defaultBlockState())
                    &&!covers.containsKey(c.position())&&f.level.getBlockState(c.position().above()).isAir()).map(SchematicPlacementService.PlanCell::position).findFirst().orElseThrow();
            f.level.setBlock(exposed,Blocks.DIRT.defaultBlockState(),FLAGS);f.refusePurchase();h.succeed();
        }
    }
    @GameTest(template="townhall_state_empty",batch="prestige53_07",timeoutTicks=12000)
    public static void proof_is_canonical_and_legacy_histories_stay_strict(GameTestHelper h)throws Exception{
        try(Fixture f=new Fixture(h,"a",4,2)){
            var proof=PrestigeNaturalSupport.capture(f.level,f.before,f.after).describe();
            List<CompoundTag> invalid=new ArrayList<>();CompoundTag q=proof.copy();q.putBoolean("unknown",true);invalid.add(q);
            q=proof.copy();q.putString("version","future");invalid.add(q);
            q=proof.copy();q.getList("cells",Tag.TAG_COMPOUND).getCompound(0).putLong("pos",BlockPos.ZERO.asLong());invalid.add(q);
            q=proof.copy();q.getList("cells",Tag.TAG_COMPOUND).getCompound(0).put("sourceCover",NbtUtils.writeBlockState(Blocks.GOLD_BLOCK.defaultBlockState()));invalid.add(q);
            q=proof.copy();q.getList("cells",Tag.TAG_COMPOUND).getCompound(0).put("actualBefore",NbtUtils.writeBlockState(Blocks.COARSE_DIRT.defaultBlockState()));invalid.add(q);
            for(CompoundTag bad:invalid){CompoundTag j=new CompoundTag();j.put(PrestigeNaturalSupport.FIELD,bad);refuse(h,()->PrestigeNaturalSupport.read(j,f.before,f.after));}
            var legacy=PrestigeNaturalSupport.read(new CompoundTag(),f.before,f.after);BlockPos p=ProducerService.sourceCoveredGrass(f.before).keySet().iterator().next();decay(h,f.level,p);
            h.assertTrue(!legacy.sourceMatches(f.level,p,f.level.getBlockState(p))&&!legacy.targetMatches(f.level,p,f.level.getBlockState(p)),"Legacy history silently gained natural-cover authority");h.succeed();
        }
    }
    @GameTest(template="townhall_state_empty",batch="prestige53_08",timeoutTicks=12000)
    public static void repair_and_rollback_preserve_aged_reads_and_history(GameTestHelper h)throws Exception{
        for(int tier:new int[]{4,5})for(int rotation:new int[]{0,180}){
            TownHallRepairTests.Env e=new TownHallRepairTests.Env(h,tier,rotation);e.prepare(1);
            var old=TownHallRevisions.plan(tier,1,e.anchor());var covers=ProducerService.sourceCoveredGrass(old);
            for(BlockPos p:covers.keySet())decay(h,e.level(),p);
            CompoundTag ledger=e.ledger(),book=e.book();byte[] history=Files.readAllBytes(e.historyFile);
            refuse(h,()->TownHallRepairTransaction.run(e,TownHallRepairTransaction.Action.APPLY,(s,n)->{if(s.equals("write")&&n==7)throw new IOException("Interrupted repair");}));
            e.reload();h.assertTrue(TownHallRepairTransaction.run(e,TownHallRepairTransaction.Action.RESUME).writes()==577,"Repair resumed wrong delta");
            h.assertTrue(TownHallRepairTransaction.run(e,TownHallRepairTransaction.Action.ROLLBACK).writes()==584,"Rollback changed historical mask");
            for(BlockPos p:covers.keySet())h.assertTrue(e.level().getBlockState(p).equals(Blocks.DIRT.defaultBlockState()),"Repair or rollback repainted ground");
            h.assertTrue(ledger.equals(e.ledger())&&book.equals(e.book())&&Arrays.equals(history,Files.readAllBytes(e.historyFile)),"Repair changed ledger, book or purchase history");
            byte[] rolled=Files.readAllBytes(e.file());h.assertTrue(TownHallRepairTransaction.run(e,TownHallRepairTransaction.Action.ROLLBACK).writes()==0&&Arrays.equals(rolled,Files.readAllBytes(e.file())),"Duplicate rollback rewrote history");
        }h.succeed();
    }
    @GameTest(template="townhall_state_empty",batch="prestige53_09",timeoutTicks=12000)
    public static void north_obsolete_covers_clear_before_target_grass(GameTestHelper h)throws Exception{orderedGrass(h,"a");}
    @GameTest(template="townhall_state_empty",batch="prestige53_10",timeoutTicks=12000)
    public static void south_obsolete_covers_clear_before_target_grass(GameTestHelper h)throws Exception{orderedGrass(h,"i");}
    private static void orderedGrass(GameTestHelper h,String city)throws Exception{
        try(Fixture f=new Fixture(h,city,3,1)){
            var covers=PrestigeNaturalSupport.obsoleteGrassCovers(f.before,f.after);h.assertTrue(covers.size()==51,"Pinned obsolete TH3 cover count changed");
            var prerequisites=PrestigeNaturalSupport.soilPrerequisites(f.before,f.after);h.assertTrue(prerequisites.size()==103,"Pinned complete soil-prerequisite count changed");
            CompoundTag before=f.data.save(new CompoundTag());
            h.assertTrue(!f.upgrade((stage,writes)->{if(stage.equals("prepared"))throw new IOException("Interrupt before first ordered write");}).success(),"Expected captured pending purchase");
            byte[] original=Files.readAllBytes(f.journal);CompoundTag journal=PrestigePurchaseRecovery.read(original);var deltas=journal.getList("blocks",Tag.TAG_COMPOUND);
            for(int i=0;i<deltas.size();i++)h.assertTrue(prerequisites.contains(BlockPos.of(deltas.getCompound(i).getLong("pos")))==(i<103),"Journal failed to record deterministic soil-prerequisite order");
            PaidRecoveryTests.interrupt(h,f.recovery(),"write",103);f.reload();
            for(BlockPos cover:covers)h.assertTrue(f.level.getBlockState(cover).equals(Blocks.AIR.defaultBlockState())
                    &&!f.level.getBlockState(cover.below()).equals(Blocks.GRASS_BLOCK.defaultBlockState()),"Recovery introduced grass before clearing every obsolete blocker");
            BlockPos p=covers.stream().min(Comparator.comparingLong(BlockPos::asLong)).orElseThrow().below();boolean[] reached={false};
            refuse(h,()->PaidConstructionRecovery.resume(f.recovery(),(stage,writes)->{
                if(stage.equals("write")&&f.level.getBlockState(p).equals(Blocks.GRASS_BLOCK.defaultBlockState())){
                    for(BlockPos cover:covers)h.assertTrue(f.level.getBlockState(cover).equals(Blocks.AIR.defaultBlockState()),"Old cover returned during target placement");
                    Blocks.GRASS_BLOCK.randomTick(f.level.getBlockState(p),f.level,p,RandomSource.create(53));
                    h.assertTrue(f.level.getBlockState(p).equals(Blocks.GRASS_BLOCK.defaultBlockState()),"Exposed target grass decayed despite ordered blocker removal");
                    reached[0]=true;throw new IOException("Interrupt after native exposed-grass tick");
                }
            }));h.assertTrue(reached[0]&&before.equals(f.data.save(new CompoundTag())),"Recovery did not reach native ordering probe without charging");
            f.reload();PaidConstructionRecovery.resume(f.recovery());f.reload();f.assertPaidOnce(before,journal);f.assertTarget();
            h.assertTrue(Arrays.equals(original,Files.readAllBytes(f.journal)),"Ordered recovery rewrote captured history");h.succeed();
        }
    }
    @GameTest(template="townhall_state_empty",batch="prestige53_11",timeoutTicks=12000)
    public static void north_th3_planters_have_no_native_grass_route(GameTestHelper h)throws Exception{planters(h,"a");}
    @GameTest(template="townhall_state_empty",batch="prestige53_12",timeoutTicks=12000)
    public static void south_th3_planters_have_no_native_grass_route(GameTestHelper h)throws Exception{planters(h,"i");}
    private static void planters(GameTestHelper h,String city)throws Exception{
        try(Fixture f=new Fixture(h,city,3,1)){
            var dirt=f.before.stream().filter(c->c.state().equals(Blocks.DIRT.defaultBlockState())).toList();
            var grass=f.before.stream().filter(c->c.state().equals(Blocks.GRASS_BLOCK.defaultBlockState())).toList();
            h.assertTrue(dirt.size()==56&&grass.size()==735,"Pinned TH3 planter/grass counts changed");
            for(var d:dirt){
                h.assertTrue(f.level.getBlockState(d.position().above()).is(Blocks.AZALEA_LEAVES)
                        &&f.level.getBlockState(d.position().above()).getValue(LeavesBlock.PERSISTENT),"Planter source cover changed");
                for(var g:grass){int dx=g.position().getX()-d.position().getX(),dy=g.position().getY()-d.position().getY(),dz=g.position().getZ()-d.position().getZ();
                    h.assertTrue(Math.abs(dx)>1||Math.abs(dz)>1||dy< -1||dy>3,"Source grass can reach protected TH3 planter dirt");}
            }
            RandomSource random=RandomSource.create(53);
            for(int sweep=0;sweep<128;sweep++)for(var g:grass)Blocks.GRASS_BLOCK.randomTick(f.level.getBlockState(g.position()),f.level,g.position(),random);
            for(var d:dirt)h.assertTrue(f.level.getBlockState(d.position()).equals(Blocks.DIRT.defaultBlockState()),"Native grass reached an isolated TH3 planter");
            BlockPos altered=dirt.get(0).position();f.level.setBlock(altered,Blocks.GRASS_BLOCK.defaultBlockState(),FLAGS);f.refusePurchase();
            // Restore only the intentional fixture mutation, then capture through
            // the normal guarded path; no production regrowth permission exists.
            f.level.setBlock(altered,Blocks.DIRT.defaultBlockState(),FLAGS);
            boolean[] captured={false};h.assertTrue(!f.upgrade((stage,writes)->{if(stage.equals("prepared")){captured[0]=true;throw new IOException("Stop after intact planter capture");}}).success()&&captured[0],"Intact native-ticked source failed normal capture");h.succeed();
        }
    }
    @GameTest(template="townhall_state_empty",batch="prestige53_13",timeoutTicks=12000)
    public static void north_partial_new_ground_cannot_regrow_old_planters(GameTestHelper h)throws Exception{partialGround(h,"a");}
    @GameTest(template="townhall_state_empty",batch="prestige53_14",timeoutTicks=12000)
    public static void south_partial_new_ground_cannot_regrow_old_planters(GameTestHelper h)throws Exception{partialGround(h,"i");}
    private static void partialGround(GameTestHelper h,String city)throws Exception{
        try(Fixture f=new Fixture(h,city,3,1)){
            Map<BlockPos,BlockState> target=new HashMap<>();for(var cell:f.after)target.put(cell.position(),cell.state());
            var sourceDirt=f.before.stream().filter(c->c.state().equals(Blocks.DIRT.defaultBlockState())).toList();
            var targetGrass=f.after.stream().filter(c->c.state().equals(Blocks.GRASS_BLOCK.defaultBlockState())).toList();
            var foundation=f.after.stream().filter(c->c.state().equals(Blocks.DIRT.defaultBlockState())).toList();
            h.assertTrue(sourceDirt.size()==56&&targetGrass.size()==1626&&foundation.size()==4875,"Pinned complete soil-transition counts changed");
            int[] groundBoundary={-1};boolean[] reached={false};CompoundTag before=f.data.save(new CompoundTag());
            var result=f.upgrade((stage,writes)->{
                if(stage.equals("prepared")){
                    var rows=PrestigePurchaseRecovery.read(Files.readAllBytes(f.journal)).getList("blocks",Tag.TAG_COMPOUND);
                    for(int i=0;i<rows.size();i++)if(rows.getCompound(i).getCompound("afterState").equals(NbtUtils.writeBlockState(Blocks.GRASS_BLOCK.defaultBlockState())))groundBoundary[0]=i+1;
                    h.assertTrue(groundBoundary[0]>103,"Missing target-ground placement boundary");
                }
                if(stage.equals("write")&&writes==groundBoundary[0]){
                    for(var cell:sourceDirt)h.assertTrue(f.level.getBlockState(cell.position()).equals(target.get(cell.position())),"Raised source dirt survived into new-grass placement");
                    RandomSource random=RandomSource.create(5304);
                    for(int sweep=0;sweep<128;sweep++)for(var cell:targetGrass)Blocks.GRASS_BLOCK.randomTick(f.level.getBlockState(cell.position()),f.level,cell.position(),random);
                    for(var cell:sourceDirt)h.assertTrue(f.level.getBlockState(cell.position()).equals(target.get(cell.position())),"Partial-ground ticks regrew obsolete raised soil");
                    for(var cell:foundation)h.assertTrue(f.level.getBlockState(cell.position()).equals(Blocks.DIRT.defaultBlockState()),"Covered foundation dirt regrew during partial placement");
                    reached[0]=true;throw new IOException("Interrupted after adversarial whole-ground native ticks");
                }
            });
            h.assertTrue(!result.success()&&reached[0]&&before.equals(f.data.save(new CompoundTag())),"Did not retain unpaid pending journal at whole-ground boundary");
            byte[] original=Files.readAllBytes(f.journal);var journal=PrestigePurchaseRecovery.read(original);f.reload();PaidConstructionRecovery.resume(f.recovery());f.reload();
            f.assertPaidOnce(before,journal);f.assertTarget();h.assertTrue(Arrays.equals(original,Files.readAllBytes(f.journal)),"Whole-transition recovery changed original history");h.succeed();
        }
    }
    private static void decay(GameTestHelper h,ServerLevel level,BlockPos p){
        h.assertTrue(level.getBlockState(p).equals(Blocks.GRASS_BLOCK.defaultBlockState()),"Native decay source must be exact grass");
        Blocks.GRASS_BLOCK.randomTick(level.getBlockState(p),level,p,RandomSource.create(53));
        h.assertTrue(level.getBlockState(p).equals(Blocks.DIRT.defaultBlockState()),"Native source cover did not kill grass at "+p);
    }
    private interface Check{void run()throws Exception;}
    private static void refuse(GameTestHelper h,Check check)throws Exception{boolean failed=false;try{check.run();}catch(IOException expected){failed=true;}h.assertTrue(failed,"Expected strict refusal");}
    static final class Fixture implements AutoCloseable {
        final GameTestHelper h;final ServerLevel level;final TerritoryDefinition def;final int from,to;final UUID owner;
        final List<SchematicPlacementService.PlanCell> before,after;final Path root,marker,journal,archive,ledgerFile;final String key;
        CatanSavedData data=new CatanSavedData();
        Fixture(GameTestHelper h,String city,int from,int revision)throws Exception{
            this(h,city,from,revision,false,false);
        }
        Fixture(GameTestHelper h,String city,int from,int revision,boolean nativeLedger,boolean sharedMarker)throws Exception{
            this.h=h;this.level=h.getLevel();this.from=from;to=from+1;h.assertTrue(level.getServer() instanceof GameTestServer,"Native fixture only");
            if(nativeLedger)data=CatanSavedData.get(level.getServer());
            def=MapDefinitionManager.territory(city);ProducerService.validateMap(def);key="territory:"+city+":town_hall";
            root=level.getServer().getWorldPath(LevelResource.ROOT).toRealPath();marker=root.resolve(FreshDemoAuthorization.MARKER);
            journal=root.resolve("data/catancraft_th_prestige_"+city+"_"+to+"_v1.dat");
            h.assertTrue((!Files.exists(marker)||sharedMarker)&&!Files.exists(journal),"Fixture refuses existing authorization or purchase history");
            if(Files.exists(marker))FreshDemoAuthorization.authorize(level.getServer());
            Files.createDirectories(root.resolve("data"));
            archive=Files.createTempDirectory(root.resolve("data"),"prestige-natural53-");ledgerFile=archive.resolve("ledger.nbt");
            var nation=data.createNation("Prestige natural fixture",UUID.randomUUID());owner=nation.id();nation.setTreasury(1000000);for(var r:ResourceType.values())nation.addResource(r,100000);
            var territory=data.createTerritory(city,"fixture",ResourceType.WOOD);territory.setOwnerNationId(owner);territory.setCityLevel(from);
            data.setPlacedMapAsset(key,from==3?SchematicAssetRegistry.asset("th3").placementToken():TownHallRevisions.token(from,revision));
            var anchor=SchematicAssetRegistry.territoryAnchor("th"+from,def);
            before=from==3?SchematicPlacementService.plannedCells("th3",anchor):TownHallRevisions.plan(from,revision,anchor);after=TownHallRevisions.plan(to,2,anchor);
            var bounds=TownHallRepairTransaction.bounds(before);
            for(int x=((int)bounds.minX-2)>>4;x<=((int)bounds.maxX+2)>>4;x++)for(int z=((int)bounds.minZ-2)>>4;z<=((int)bounds.maxZ+2)>>4;z++){level.setChunkForced(x,z,true);level.getChunk(x,z);}
            for(var cell:before){var be=level.getBlockEntity(cell.position());if(be instanceof net.minecraft.world.Container c)c.clearContent();if(be instanceof LecternBlockEntity l)l.setBook(ItemStack.EMPTY);level.setBlock(cell.position(),cell.state(),FLAGS);}
            // Only generated fixture entities, removed before transaction capture.
            for(var entity:new ArrayList<>(level.getEntities((net.minecraft.world.entity.Entity)null,bounds.inflate(2))))
                if(entity instanceof net.minecraft.world.entity.Mob||entity instanceof net.minecraft.world.entity.item.ItemEntity)entity.discard();
            var book=new ItemStack(Items.WRITABLE_BOOK);ListTag pages=new ListTag();pages.add(StringTag.valueOf("Preserve current management data"));book.getOrCreateTag().put("pages",pages);
            ((LecternBlockEntity)level.getBlockEntity(manager())).setBook(book);save();
            if(!Files.exists(marker)){
                JsonObject identity=new JsonObject();identity.addProperty("schema",1);identity.addProperty("purpose",FreshDemoAuthorization.PURPOSE);identity.addProperty("worldId",UUID.randomUUID().toString());identity.addProperty("levelName",level.getServer().getWorldData().getLevelName());identity.addProperty("seed",level.getSeed());identity.addProperty("dimension","minecraft:overworld");identity.addProperty("mapSha256",TownHallRevisions.sha(Files.readAllBytes(MapDefinitionManager.path())));identity.addProperty("authoredWorldSha256","c".repeat(64));identity.addProperty("bootstrapRevision","prestige-natural53-native-fixture");identity.addProperty("installationBoundPath",root.toString());Files.writeString(marker,identity.toString(),StandardOpenOption.CREATE_NEW);
            }
        }
        NationData nation(){return data.nation(owner);}TerritoryData territory(){return data.territory(def.id());}
        BlockPos manager(){return before.get(TownHallRevisions.managerIndex()).position();}CompoundTag book(){return level.getBlockEntity(manager()).saveWithFullMetadata().copy();}
        void save()throws IOException{NbtIo.writeCompressed(data.save(new CompoundTag()),ledgerFile.toFile());}void reload()throws IOException{data=CatanSavedData.load(NbtIo.readCompressed(ledgerFile.toFile()));}
        TownHallPrestigeUpgradeService.Result upgrade(TownHallPrestigeUpgradeService.Fault fault){return TownHallPrestigeUpgradeService.upgrade(level.getServer(),data,nation(),territory(),EconomyCatalog.cityUpgradeCost(from),to,fault);}
        PaidConstructionRecovery.Environment recovery()throws Exception{
            var e=PrestigePurchaseRecovery.environment(level.getServer(),data,level,def,root,journal,()->ConstructionWorldAuthorization.authorize(level.getServer()));
            return new PaidConstructionRecovery.Environment(){public ServerLevel level(){return e.level();}public Path original(){return e.original();}public byte[] originalBytes(){return e.originalBytes();}public CompoundTag identity(){return e.identity();}public List<PaidConstructionRecovery.Cell> cells()throws IOException{return e.cells();}public void authorize()throws IOException{e.authorize();}public boolean paid()throws IOException{return e.paid();}public void checkPayment()throws IOException{e.checkPayment();}public void settle()throws IOException{e.settle();}public CompoundTag ledger(){return e.ledger();}public void save()throws IOException{Fixture.this.save();}};
        }
        void assertPaidOnce(CompoundTag original,CompoundTag journal){var expected=CatanSavedData.load(original);EconomyCatalog.cityUpgradeCost(from).charge(expected.nation(owner));expected.territory(def.id()).setCityLevel(to);expected.setPlacedMapAsset(key,TownHallRevisions.token(to,2));PaidConstructionRecovery.receipt(expected,journal.getUUID("transaction"));h.assertTrue(expected.save(new CompoundTag()).equals(data.save(new CompoundTag())),"Prestige charge/receipt or unrelated ledger changed");}
        void assertTarget(){var covers=ProducerService.sourceCoveredGrass(after);for(var cell:after){BlockState live=level.getBlockState(cell.position());h.assertTrue(TownHallInteractionPolicy.matches(live,cell.state(),cell.position().equals(manager()))||PrestigeNaturalSupport.decayed(level,cell.position(),cell.state(),live,covers.get(cell.position())),"Full target conflict at "+cell.position());}}
        Map<BlockPos,BlockState> snapshot(){Map<BlockPos,BlockState> out=new HashMap<>();for(var c:before)out.put(c.position(),level.getBlockState(c.position()));return out;}
        void refuseRecovery()throws Exception{CompoundTag ledger=data.save(new CompoundTag()),book=book();var blocks=snapshot();byte[] original=Files.readAllBytes(journal);refuse(h,()->PaidConstructionRecovery.resume(recovery()));h.assertTrue(ledger.equals(data.save(new CompoundTag()))&&book.equals(book())&&blocks.equals(snapshot())&&Arrays.equals(original,Files.readAllBytes(journal)),"Refusal changed captured history, world or ledger");}
        void refusePurchase()throws Exception{var blocks=snapshot();CompoundTag ledger=data.save(new CompoundTag()),book=book();h.assertTrue(!upgrade((s,n)->{}).success()&&!Files.exists(journal)&&ledger.equals(data.save(new CompoundTag()))&&blocks.equals(snapshot())&&book.equals(book()),"Source conflict was changed or charged");}
        public void close()throws IOException{Files.delete(marker);for(Path file:List.of(journal,PaidConstructionRecovery.companion(journal)))if(Files.exists(file))Files.move(file,archive.resolve(file.getFileName()));}
    }
}
