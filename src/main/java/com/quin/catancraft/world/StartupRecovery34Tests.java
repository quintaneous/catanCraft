package com.quin.catancraft.world;

import com.quin.catancraft.data.*;
import com.quin.catancraft.economy.EconomyCost;
import com.quin.catancraft.map.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Real discovery/path/adapters on generated flat native fixtures, not queueFixture callbacks.
 * Transaction fault hooks create the retained histories; only normal server ticks recover them.
 * Queue rebind below models service restart, not a process-kill or power-loss test.
 */
@GameTestHolder("catancraft_startup34") @PrefixGameTestTemplate(false)
public final class StartupRecovery34Tests {
    private static final int FLAGS=2|16|32;
    private static final String INTERRUPTED="startup34 deliberate retained interruption";
    private static final String PROVENANCE="Generated flat startup recovery fixture; not an authored or owner world.\n";
    private static Fixture current;
    private interface Checked {void run() throws Exception;}

    // Keep an early failure from authorizing/running a different later fixture. Histories remain.
    @AfterBatch(batch="startup34_01") public static void afterProducer(ServerLevel level){cleanup(level);}
    @AfterBatch(batch="startup34_02") public static void afterForward(ServerLevel level){cleanup(level);}
    @AfterBatch(batch="startup34_03") public static void afterRollback(ServerLevel level){cleanup(level);}
    private static void cleanup(ServerLevel level){
        if(!(level.getServer() instanceof GameTestServer))throw new IllegalStateException("Synthetic tests only");
        PaidRecoveryService.stopped(level.getServer());
        if(current!=null){current.release();try{if(Files.exists(current.marker)&&Files.readString(current.marker).equals(R5CivicAutomaticInstaller.MARKER_VALUE))Files.delete(current.marker);}catch(IOException e){throw new RuntimeException(e);}current=null;}
    }

    @GameTest(template="townhall_state_empty",batch="startup34_01",timeoutTicks=10000)
    public static void production_producer_discovery_retries_original_price_then_settles_once(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h,"b");ProducerSeed seed=new ProducerSeed(f);
        ready(f,()->{
            seed.stage();interrupt(h,()->ProducerTransaction.run(seed,ProducerTransaction.Action.APPLY,(stage,n)->{if(stage.equals("prepared"))throw new IOException(INTERRUPTED);}));
            seed.decayProvenSourceGrass();
            byte[] original=Files.readAllBytes(seed.file());CompoundTag inventories=seed.inventories();long money=f.nation.treasury();
            f.nation.setTreasury(0);f.save();f.release();PaidRecoveryService.stopped(f.server);
            await(f,0,()->PaidRecoveryService.status(f.server,f.city).contains("Original producer price"),()->{
                h.assertTrue(Arrays.equals(original,Files.readAllBytes(seed.file()))&&f.nation.treasury()==0,"Original-price wait changed history or charged");
                h.assertTrue(f.territory.producerLevel()==1,"Unpaid startup recovery advanced producer level");
                try(var lease=SectorChunkLease.acquire(f.level,f.bounds)){seed.geometry(1);h.assertTrue(seed.inventories().equals(inventories),"Waiting changed inventory");}
                f.nation.setTreasury(money);f.save();
                await(f,0,()->ProducerTransaction.phase(ProducerJournal.read(seed.file()))==ProducerTransaction.Phase.COMMITTED,()->{
                    long paid=money-seed.spec().cost().money();
                    h.assertTrue(f.nation.treasury()==paid&&f.territory.producerLevel()==2,"Production adapter did not settle one original charge");
                    for(ResourceType r:ResourceType.values())h.assertTrue(f.nation.resource(r)==1000-seed.spec().cost().resources().getOrDefault(r,0L),"Producer material payment differs: "+r);
                    try(var lease=SectorChunkLease.acquire(f.level,f.bounds)){seed.geometry(2);h.assertTrue(seed.inventories().equals(inventories),"Production recovery lost actual inventory");}
                    f.diskMatches();noReplay(f,seed.file(),()->h.assertTrue(f.nation.treasury()==paid&&f.territory.producerLevel()==2,"Completed producer replayed on queue restart"));
                });
            });
        });
    }

    @GameTest(template="townhall_state_empty",batch="startup34_02",timeoutTicks=10000)
    public static void production_legacy_defense_discovery_resumes_original_price_and_receipt(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h,"a");DefenseSeed seed=new DefenseSeed(f);
        ready(f,()->{
            seed.stage();long money=f.nation.treasury();
            interrupt(h,()->DefenseTransaction.run(seed,DefenseTransaction.Action.APPLY,(stage,n)->{if(stage.equals("write")&&n==3)throw new IOException(INTERRUPTED);}));
            h.assertTrue(ProducerJournal.read(seed.file()).getString("phase").equals("APPLYING"),"Expected retained partial forward history");
            String reserved=seed.receipt();h.assertTrue(reserved.endsWith(":reserved")&&f.nation.treasury()==money-73,"Seed reservation did not save original price");
            f.save();f.release();PaidRecoveryService.stopped(f.server);
            await(f,0,()->ProducerJournal.read(seed.file()).getString("phase").equals("COMMITTED"),()->{
                h.assertTrue(f.nation.treasury()==money-73&&f.nation.resource(ResourceType.STONE)==997,"Recovery used a new catalog price or repeated original debit");
                h.assertTrue(seed.receipt().equals(reserved.replace(":reserved",":paid"))&&seed.owned().equals(seed.plan.token()),"Production adapter lost original identity/ownership");
                try(var lease=SectorChunkLease.acquire(f.level,f.bounds)){seed.geometry(false);}
                f.diskMatches();noReplay(f,seed.file(),()->h.assertTrue(f.nation.treasury()==money-73&&f.nation.resource(ResourceType.STONE)==997,"Completed legacy defense replayed"));
            });
        });
    }

    @GameTest(template="townhall_state_empty",batch="startup34_03",timeoutTicks=10000)
    public static void production_legacy_defense_discovery_finishes_rollback_and_refunds_once(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h,"i");DefenseSeed seed=new DefenseSeed(f);
        ready(f,()->{
            seed.stage();long money=f.nation.treasury();DefenseTransaction.run(seed,DefenseTransaction.Action.APPLY);
            BlockPos support=seed.decayOneSupport();
            interrupt(h,()->DefenseTransaction.run(seed,DefenseTransaction.Action.ROLLBACK,(stage,n)->{if(stage.equals("rollback_write")&&n==3)throw new IOException(INTERRUPTED);}));
            h.assertTrue(ProducerJournal.read(seed.file()).getString("phase").equals("ROLLING_BACK"),"Expected retained rollback direction");
            f.save();f.release();PaidRecoveryService.stopped(f.server);
            await(f,0,()->ProducerJournal.read(seed.file()).getString("phase").equals("ROLLED_BACK"),()->{
                h.assertTrue(f.nation.treasury()==money&&f.nation.resource(ResourceType.STONE)==1000,"Production rollback did not refund exactly once");
                h.assertTrue(seed.receipt().endsWith(":refunded")&&seed.owned().isEmpty(),"Rollback became a forward purchase");
                try(var lease=SectorChunkLease.acquire(f.level,f.bounds)){seed.geometry(true);h.assertTrue(f.level.getBlockState(support).is(Blocks.DIRT),"Recovery repainted decayed read-only support");}
                f.diskMatches();noReplay(f,seed.file(),()->h.assertTrue(f.nation.treasury()==money&&f.nation.resource(ResourceType.STONE)==1000,"Completed rollback refunded twice"));
            });
        });
    }

    private interface Condition {boolean test() throws Exception;}
    private static void await(Fixture f,int ticks,Condition condition,Checked done){f.h.runAfterDelay(1,()->{
        try{
            String status=PaidRecoveryService.status(f.server,f.city);
            f.h.assertTrue(!status.contains("blocked"),"Production discovery/adapter blocked: "+status);
            f.h.assertTrue(ticks<3000,"Production recovery did not reach expected state: "+status);
            if(condition.test())done.run();else await(f,ticks+1,condition,done);
        }catch(Exception e){f.release();throw new RuntimeException(e);}
    });}
    private static void ready(Fixture f,Checked done){
        f.h.runAfterDelay(1,()->{try{f.lease.keepAlive();if(f.lease.ready())done.run();else ready(f,done);}catch(Exception e){f.release();throw new RuntimeException(e);}});
    }
    private static void interrupt(GameTestHelper h,Checked work)throws Exception {
        try{work.run();throw new AssertionError("Expected retained interruption");}
        catch(IOException expected){h.assertTrue(expected.getMessage().equals(INTERRUPTED),"Unexpected seed failure: "+expected);}
    }
    private static void noReplay(Fixture f,Path journal,Checked verify)throws Exception {
        byte[] completed=Files.readAllBytes(journal);CompoundTag nation=f.nation.save();PaidRecoveryService.stopped(f.server);
        f.h.runAfterDelay(220,()->{try{
            verify.run();f.h.assertTrue(nation.equals(f.nation.save())&&Arrays.equals(completed,Files.readAllBytes(journal)),"Completed history or balance changed after rediscovery");
            // Other cities may retain legitimate blocked work. This journal/city must not replay.
            f.h.assertTrue(PaidRecoveryService.status(f.server,f.city).isEmpty(),"Completed city history remained queued");
            f.diskMatches();f.finish();f.h.succeed();
        }catch(Exception e){f.release();throw new RuntimeException(e);}});
    }

    private static final class Fixture {
        final GameTestHelper h;final MinecraftServer server;final ServerLevel level;final Path root,marker;final String city;
        final CatanSavedData data;final TerritoryDefinition def;final TerritoryData territory;final NationData nation;final AABB bounds;
        SectorChunkLease lease;
        Fixture(GameTestHelper h,String city)throws Exception {
            this.h=h;this.city=city;server=h.getLevel().getServer();level=h.getLevel();
            h.assertTrue(server instanceof GameTestServer,"Generated GameTest server required");
            root=server.getWorldPath(LevelResource.ROOT).toRealPath();
            h.assertTrue(root.getParent().getFileName().toString().equals("controlled-catancraft_startup34-run"),"Refusing a nonisolated fixture path");
            h.assertTrue(Files.readString(root.resolve(".catancraft-startup34-synthetic-v1")).equals(PROVENANCE),"Generated fixture provenance is missing or changed");
            h.assertTrue(root.getFileName().toString().equals("CatanCraft_Season1_R7_Development")&&server.getWorldData().getLevelName().equals("CatanCraft Environment V4 - Visual Polish R2"),"Runner must stage the generated flat legacy-identity fixture");
            marker=root.resolve(R5CivicAutomaticInstaller.MARKER_NAME);Files.writeString(marker,R5CivicAutomaticInstaller.MARKER_VALUE,StandardOpenOption.CREATE_NEW);
            current=this;
            h.assertTrue(TownHallRepairService.authorize(server).equals(root),"Fixture failed unchanged production authorization");
            PaidRecoveryService.stopped(server);data=CatanSavedData.get(server);def=MapDefinitionManager.territory(city);ProducerService.validateMap(def);
            TerritoryData found=data.territory(city);territory=found==null?data.createTerritory(city,"startup34 "+city,ResourceType.WOOD):found;
            h.assertTrue(territory.ownerNationId()==null,"Refusing preexisting owned fixture territory");
            nation=data.createNation("startup34 "+city,UUID.randomUUID());nation.setTreasury(10000);for(ResourceType type:ResourceType.values())nation.addResource(type,1000);
            territory.setOwnerNationId(nation.id());territory.setCityLevel(5);territory.setProducerLevel(1);data.setDirty();
            var r=def.restorationRegion();bounds=new AABB(r.minX(),r.minY(),r.minZ(),r.maxX()+1,r.maxY()+1,r.maxZ()+1).inflate(2);
            lease=SectorChunkLease.acquire(level,bounds);
            for(var chunk:lease.positions())h.assertTrue(!level.getForcedChunks().contains(chunk.toLong()),"Fixture unexpectedly has persistent forceload state");
        }
        void save()throws IOException{data.setDirty();if(!server.saveEverything(true,true,true))throw new IOException("Native fixture save failed");}
        void release(){if(lease!=null){lease.close();lease=null;}}
        void diskMatches()throws Exception {
            CatanSavedData disk=CatanSavedData.load(NbtIo.readCompressed(root.resolve("data/catancraft.dat").toFile()).getCompound("data"));
            h.assertTrue(disk.nation(nation.id()).save().equals(nation.save()),"Actual SavedData disk accounting differs");
            h.assertTrue(disk.territory(city).save().equals(territory.save()),"Actual SavedData disk progression differs");
        }
        void finish()throws IOException{release();PaidRecoveryService.stopped(server);h.assertTrue(Files.readString(marker).equals(R5CivicAutomaticInstaller.MARKER_VALUE),"Fixture authorization changed");Files.delete(marker);}
    }

    /** This adapter only seeds the exact production-shaped journal. Recovery uses ProducerService. */
    private static final class ProducerSeed implements ProducerTransaction.Environment {
        final Fixture f;final ProducerTransaction.Spec spec;final Path file;
        ProducerSeed(Fixture f)throws IOException {
            this.f=f;List<ProducerTransaction.Plot> plots=new ArrayList<>();
            for(String family:ProducerRevisions.applicable(f.def))plots.add(new ProducerTransaction.Plot(family,1,2,SchematicAssetRegistry.territoryAnchor(family,f.def)));
            spec=new ProducerTransaction.Spec(1,2,plots);file=f.root.resolve("data/catancraft_producer_"+f.city+"_upgrade_l2_r1.dat");
            f.h.assertTrue(!Files.exists(file),"Refusing existing fixture history");
        }
        void stage()throws IOException {
            for(var plot:spec.plots()){
                for(var c:ProducerRevisions.plan(plot.family(),1,plot.anchor())){
                    f.level.setBlock(c.position(),c.state(),FLAGS);
                    if(c.state().is(Blocks.BARREL))((BarrelBlockEntity)f.level.getBlockEntity(c.position())).setItem(0,new ItemStack(Items.DIAMOND,17));
                }
                token(plot.family(),ProducerRevisions.token(plot.family(),1));
            }f.save();
        }
        Set<BlockPos> provenGrass()throws IOException{Set<BlockPos> out=new HashSet<>();for(Tag t:ProducerJournal.read(file).getCompound("sourceCoveredGrassV1").getList("covers",Tag.TAG_COMPOUND))out.add(BlockPos.of(((CompoundTag)t).getLong("pos")));return out;}
        void decayProvenSourceGrass()throws IOException {
            Set<BlockPos> positions=provenGrass();f.h.assertTrue(positions.size()==2,"Pinned L1 lumberyard source-cover count changed");
            for(BlockPos pos:positions){Blocks.GRASS_BLOCK.randomTick(f.level.getBlockState(pos),f.level,pos,net.minecraft.util.RandomSource.create(7));f.h.assertTrue(f.level.getBlockState(pos).equals(Blocks.DIRT.defaultBlockState()),"Native covered source grass did not decay");}
        }
        void geometry(int tier)throws IOException{Set<BlockPos> proven=provenGrass();for(var plot:spec.plots())for(var c:ProducerRevisions.plan(plot.family(),tier,plot.anchor())){
            var actual=f.level.getBlockState(c.position());boolean expectedDirt=proven.contains(c.position())&&c.state().equals(Blocks.GRASS_BLOCK.defaultBlockState())&&actual.equals(Blocks.DIRT.defaultBlockState());
            f.h.assertTrue(expectedDirt||ProducerRevisions.matches(actual,c.state()),"Production producer geometry differs at "+c.position());
        }}
        CompoundTag inventories()throws IOException {CompoundTag out=new CompoundTag();for(var plot:spec.plots())for(var c:ProducerRevisions.plan(plot.family(),1,plot.anchor()))if(c.state().is(Blocks.BARREL))out.put(Long.toString(c.position().asLong()),f.level.getBlockEntity(c.position()).saveWithFullMetadata());return out;}
        public ServerLevel level(){return f.level;}public Path file(){return file;}public ProducerTransaction.Spec spec(){return spec;}public NationData nation(){return f.nation;}public TerritoryData territory(){return f.territory;}
        public CompoundTag identity(){CompoundTag n=new CompoundTag();n.putString("world",f.root.toString());n.putString("dimension",f.def.dimension());n.putString("city",f.city);n.putString("owner",String.valueOf(f.territory.ownerNationId()));n.putString("mapContract","resource_r1_2026_10_14");return n;}
        private String key(String family){return "territory:"+f.city+":"+SchematicAssetRegistry.asset(family).plot();}
        public String token(String family){return f.data.placedMapAsset(key(family));}public void token(String family,String value){f.data.setPlacedMapAsset(key(family),value);}
        public String receipt(){return f.data.placedMapAsset("producer_transaction:"+f.city);}public void receipt(String value){f.data.setPlacedMapAsset("producer_transaction:"+f.city,value);}
        public CompoundTag ledger(){return f.data.save(new CompoundTag());}public void dirty(){f.data.setDirty();}public void save()throws IOException{f.save();}
        public void allowWrite(BlockPos pos)throws IOException {if(!f.nation.id().equals(f.territory.ownerNationId())||!f.def.siegeRegion().contains(pos)||!f.def.restorationRegion().contains(pos))throw new IOException("Seed producer leaves production authorization");}
    }

    /** Historical noncatalog price deliberately proves that the production adapter uses the journal. */
    private static final class DefenseSeed implements DefenseTransaction.Environment {
        final Fixture f;final DefenseRevisions.Plan plan;final Path file;
        DefenseSeed(Fixture f)throws IOException {this.f=f;plan=DefenseRevisions.plan(f.city,"front_w_04");file=f.root.resolve("data/catancraft_defense_"+f.city+"_front_w_04_r4a_r6_1.dat");f.h.assertTrue(!Files.exists(file),"Refusing existing fixture defense history");}
        void stage()throws IOException{for(var r:plan.reads())f.level.setBlock(r.pos(),r.state(),FLAGS);for(var c:plan.cells())f.level.setBlock(c.pos(),c.before(),FLAGS);f.save();}
        void geometry(boolean rollback){for(var c:plan.cells())f.h.assertTrue(f.level.getBlockState(c.pos()).equals(rollback?c.before():c.after()),"Production defense geometry differs at "+c.pos());}
        BlockPos decayOneSupport()throws IOException {for(var r:plan.reads())if(r.state().is(Blocks.GRASS_BLOCK)&&SectorNaturalSupportPolicy.blocksGrass(f.level.getBlockState(r.pos().above()),r.pos().above())){Blocks.GRASS_BLOCK.randomTick(f.level.getBlockState(r.pos()),f.level,r.pos(),net.minecraft.util.RandomSource.create(7));f.h.assertTrue(f.level.getBlockState(r.pos()).is(Blocks.DIRT),"Native covered grass did not decay");return r.pos();}throw new IOException("No covered grass in actual legacy plan");}
        public ServerLevel level(){return f.level;}public Path file(){return file;}public DefenseRevisions.Plan plan(){return plan;}public EconomyCost cost(){return new EconomyCost(73,Map.of(ResourceType.STONE,3L));}public NationData nation(){return f.nation;}
        public CompoundTag identity(){CompoundTag n=new CompoundTag();n.putString("world",f.root.toString());n.putString("dimension",f.def.dimension());n.putString("city",f.city);n.putString("socket",plan.socket());n.putUUID("owner",f.nation.id());return n;}
        public String receipt(){String s=f.data.placedMapAsset("defense_receipt:"+f.city+":"+plan.socket());return s==null?"":s;}public void receipt(String value){f.data.setPlacedMapAsset("defense_receipt:"+f.city+":"+plan.socket(),value);}
        public String owned(){String s=f.data.placedMapAsset(DefenseService.ownedKey(f.city,plan.socket()));return s==null||s.equals("defense_unowned")?"":s;}public void owned(String value){f.data.setPlacedMapAsset(DefenseService.ownedKey(f.city,plan.socket()),value==null?"defense_unowned":value);}
        public void requirements(boolean rollback)throws IOException {if(!f.nation.id().equals(f.territory.ownerNationId()))throw new IOException("Seed owner changed");}
        public void allow(BlockPos pos)throws IOException{if(!f.def.siegeRegion().contains(pos)||!f.def.restorationRegion().contains(pos))throw new IOException("Seed defense outside production bounds");}
        public void save()throws IOException{f.save();}
    }
}
