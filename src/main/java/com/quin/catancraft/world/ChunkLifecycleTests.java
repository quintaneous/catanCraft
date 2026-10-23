package com.quin.catancraft.world;

import com.quin.catancraft.data.*;
import com.quin.catancraft.map.MapAnchor;
import com.quin.catancraft.ui.DefenseMenuRows;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Regressions without fixture force-loading: real server chunk/entity readiness and retained journals. */
@GameTestHolder("catancraft_chunk_lifecycle") @PrefixGameTestTemplate(false)
public final class ChunkLifecycleTests {
    private static final int FLAGS=2|16|32;
    private static final SectorContracts.Direction NORTH=SectorContracts.Direction.NORTH;
    @GameTest(template="townhall_state_empty",batch="load_01",timeoutTicks=1000)
    public static void temporary_lease_survives_idle_then_releases_without_forceload(GameTestHelper h)throws Exception {
        var level=h.getLevel();var lease=SectorChunkLease.acquire(level,new AABB(20000,180,20000,20032,190,20032));
        h.assertTrue(lease.chunkCount()<=25,"Small lease grew unexpectedly");
        for(var p:lease.positions())h.assertTrue(!level.getForcedChunks().contains(p.toLong()),"Lease changed persistent forceload state");
        keep(h,lease,0,()->{
            h.assertTrue(lease.ready(),"Leased distant entity/chunk region unloaded after normal ticket expiry");lease.close();lease.close();
            h.assertTrue(lease.closed(),"Lease not closed");
            for(var p:lease.positions())h.assertTrue(!level.getForcedChunks().contains(p.toLong()),"Lease left persistent forced chunks");
            h.runAfterDelay(250,()->{h.assertTrue(lease.positions().stream().allMatch(p->level.getChunkSource().getChunkNow(p.x,p.z)==null),"Closed lease retained distant chunks");h.succeed();});
        });
    }
    private static void keep(GameTestHelper h,SectorChunkLease lease,int ticks,Runnable after){h.runAfterDelay(1,()->{
        try{lease.keepAlive();if(ticks<160)keep(h,lease,ticks+1,after);else after.run();}catch(IOException e){throw new RuntimeException(e);}
    });}
    @GameTest(template="townhall_state_empty",batch="load_02",timeoutTicks=100)
    public static void unbounded_lease_is_refused(GameTestHelper h)throws Exception {
        boolean refused=false;try(var ignored=SectorChunkLease.acquire(h.getLevel(),new AABB(0,0,0,4096,256,4096))){}catch(IOException expected){refused=true;}
        h.assertTrue(refused,"Unbounded city lease accepted");h.succeed();
    }
    @GameTest(template="townhall_state_empty",batch="load_03",timeoutTicks=100)
    public static void pending_gui_can_resume_without_affording_another_purchase(GameTestHelper h)throws Exception {
        var data=new CatanSavedData();var nation=data.createNation("recovery GUI",UUID.randomUUID());nation.addTreasury(-nation.treasury());
        var state=new SectorService.MenuState(NORTH,0,SectorPriceCatalog.cost(NORTH,0),"Retained operation",false,"resume");
        var rows=DefenseMenuRows.rows("a",true,nation,List.of(state));
        h.assertTrue(rows.stream().anyMatch(r->r.startsWith("A|nation defense resume a north confirm|")),"Missing no-repeat-charge resume control");
        h.assertTrue(rows.stream().noneMatch(r->r.startsWith("A|nation defense review")||r.startsWith("Y|Missing:")),"Resume requires a second purchase balance");
        h.assertTrue(DefenseMenuRows.rows("a",false,nation,List.of(state)).stream().noneMatch(r->r.startsWith("A|nation defense resume")),"Nonleader recovery offered");h.succeed();
    }
    @GameTest(template="townhall_state_empty",batch="load_04",timeoutTicks=2200)
    public static void interrupted_sliced_purchase_reloads_and_finishes_once_without_external_tickets(GameTestHelper h)throws Exception {
        var f=new Fixture(h);ok(h,SectorService.quoteFixture(f.ctx,NORTH,f));
        ok(h,SectorService.operationFixtureBounded(f.ctx,NORTH,SectorTransaction.Action.APPLY,f,(stage,writes)->{if(stage.equals("write")&&writes==53)throw new IOException("deliberate 53-write interruption");}));
        await(h,f,0,()->{
            try {
                h.assertTrue(f.placed()==53&&SectorService.savedState(f.data,"a",NORTH).level()==0,"Partial state not retained");
                long paid=f.nation().treasury();h.assertTrue(paid==f.initialMoney-1500,"Initial reservation missing or repeated");
                h.assertTrue(SectorService.pendingHistories(f.ctx).size()==1,"Retained journal missing");
                var retry=SectorService.operationFixtureBounded(f.ctx,NORTH,SectorTransaction.Action.APPLY,f);
                h.assertTrue(!retry.success()&&f.nation().treasury()==paid,"A pending purchase was charged again");
                // A genuine protected support mutation must still stop recovery, never be papered over by residency.
                f.level.setBlock(f.support,Blocks.DIAMOND_BLOCK.defaultBlockState(),FLAGS);
                var conflict=SectorService.operationFixtureBounded(f.ctx,NORTH,SectorTransaction.Action.RESUME,f);
                if(conflict.success())await(h,f,0,()->checkConflictAndResume(h,f,paid));else checkConflictAndResume(h,f,paid);
            }catch(Exception e){throw new RuntimeException(e);}
        });
    }
    private static void checkConflictAndResume(GameTestHelper h,Fixture f,long paid){try{
        h.assertTrue(f.placed()==53&&f.nation().treasury()==paid,"Protected conflict wrote or charged");
        f.level.setBlock(f.support,Blocks.STONE.defaultBlockState(),FLAGS);
        SectorService.stopped(f.level.getServer());f.reload();
        ok(h,SectorService.operationFixtureBounded(f.ctx,NORTH,SectorTransaction.Action.RESUME,f));
        await(h,f,0,()->{try{
            h.assertTrue(f.placed()==900&&SectorService.savedState(f.data,"a",NORTH).level()==1,"Resumed wall did not finish");
            h.assertTrue(f.nation().treasury()==paid&&f.nation().resource(ResourceType.WOOD)==985&&f.nation().resource(ResourceType.STONE)==970,"Recovery charged a second time");
            h.assertTrue(SectorService.pendingHistories(f.ctx).isEmpty(),"Completed journal still pending");
            for(int x=f.base.getX()>>4;x<=f.support.getX()>>4;x++)for(int z=f.base.getZ()>>4;z<=f.support.getZ()>>4;z++)h.assertTrue(!f.level.getForcedChunks().contains(net.minecraft.world.level.ChunkPos.asLong(x,z)),"Runner altered persistent forceload state");
            ok(h,SectorService.operationFixtureBounded(f.ctx,NORTH,SectorTransaction.Action.RESUME,f));
            await(h,f,0,()->{h.assertTrue(f.nation().treasury()==paid,"Repeated resume charged");h.succeed();});
        }catch(Exception e){throw new RuntimeException(e);}});
    }catch(Exception e){throw new RuntimeException(e);}}
    private static void await(GameTestHelper h,Fixture f,int ticks,Runnable done){h.runAfterDelay(1,()->{
        h.assertTrue(ticks<1000,"Bounded operation did not settle or report failure");
        try{if(SectorService.activeFixture(f.ctx))await(h,f,ticks+1,done);else done.run();}catch(IOException error){throw new RuntimeException(error);}
    });}
    @GameTest(template="townhall_state_empty",batch="load_05",timeoutTicks=1500)
    public static void queued_base_city_paths_apply_without_public_command_and_preserve_ledger(GameTestHelper h)throws Exception {
        PublicFixture f=new PublicFixture(h,24000);f.stage();CityBasePreparation.queueFixture("public-test",f);
        awaitPublic(h,f,0,()->{try{
            h.assertTrue(Files.exists(f.file()),"Automatic initialization made no durable journal");
            h.assertTrue(StreetscapeTransaction.phase(StreetscapeJournal.read(f.file()))==StreetscapeTransaction.Phase.COMMITTED,"Base journal not committed");
            for(var c:f.plan.cells())h.assertTrue(StreetscapeR5.matches(f.level().getBlockState(c.pos()),c.after()),"Base path missing at "+c.pos());
            h.assertTrue(f.ledger().getInt("resources")==1234,"Base preparation changed resources");
            byte[] before=Files.readAllBytes(f.file());boolean refused=false;try{CityBasePreparation.queueFixture("public-again",f);}catch(IOException expected){refused=true;}
            h.assertTrue(refused&&Arrays.equals(before,Files.readAllBytes(f.file())),"Existing history reapplied/overwritten");h.succeed();
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    @GameTest(template="townhall_state_empty",batch="load_06",timeoutTicks=1500)
    public static void automatic_base_preparation_refuses_real_source_conflict(GameTestHelper h)throws Exception {
        PublicFixture f=new PublicFixture(h,26000);f.stage();var changed=f.plan.cells().stream().filter(StreetscapeR5.Cell::changes).findFirst().orElseThrow();
        f.level().setBlock(changed.pos(),Blocks.DIAMOND_BLOCK.defaultBlockState(),FLAGS);CityBasePreparation.queueFixture("public-conflict",f);
        awaitPublic(h,f,0,()->{h.assertTrue(!Files.exists(f.file())&&f.level().getBlockState(changed.pos()).is(Blocks.DIAMOND_BLOCK),"Conflict was overwritten");h.assertTrue(f.ledger().getInt("resources")==1234,"Refusal charged");h.succeed();});
    }
    private static void awaitPublic(GameTestHelper h,PublicFixture f,int ticks,Runnable done){h.runAfterDelay(1,()->{h.assertTrue(ticks<800,"Public initializer stuck");if(CityBasePreparation.isRunning(h.getLevel().getServer()))awaitPublic(h,f,ticks+1,done);else done.run();});}
    private static void ok(GameTestHelper h,MapAssetService.Result r){h.assertTrue(r.success(),r.message());}
    private static final class Fixture implements SectorService.Provider {
        final ServerLevel level;final BlockPos base=new BlockPos(22000,200,22000),support=base.offset(80,-1,80);
        CatanSavedData data=new CatanSavedData();final UUID actor=UUID.randomUUID(),owner;final Path root;final String worldId=UUID.randomUUID().toString();SectorService.Context ctx;final long initialMoney;
        Fixture(GameTestHelper h)throws IOException {
            level=h.getLevel();var n=data.createNation("unforced chunk lifecycle",actor);owner=n.id();n.addResource(ResourceType.WOOD,1000);n.addResource(ResourceType.STONE,1000);initialMoney=n.treasury();
            data.createTerritory("a","chunk test",ResourceType.WOOD).setOwnerNationId(owner);
            root=level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().resolve("data/chunk-fixture/"+worldId);context();
            for(int x=0;x<30;x++)for(int z=0;z<30;z++){level.getChunkAt(base.offset(x,0,z));level.setBlock(base.offset(x,0,z),Blocks.AIR.defaultBlockState(),FLAGS);}
            level.getChunkAt(support);level.setBlock(support,Blocks.STONE.defaultBlockState(),FLAGS);
        }
        void context()throws IOException{ctx=SectorService.fixtureContext(level.getServer(),root,data,"a",actor,worldId,()->100L);}
        void reload()throws IOException{data=SectorService.reloadFixture(ctx);context();}
        NationData nation(){return data.nation(owner);}
        int placed(){int n=0;for(int x=0;x<30;x++)for(int z=0;z<30;z++)if(level.getBlockState(base.offset(x,0,z)).is(Blocks.STONE_BRICKS))n++;return n;}
        public String priceVersion(){return SectorPriceCatalog.VERSION;}
        public SectorContracts.Contract resolve(SectorService.Context c,SectorContracts.Direction side,int source)throws IOException {
            if(source!=0)throw new IOException("Fixture has one tier");List<SectorTransaction.Cell> cells=new ArrayList<>();
            for(int x=0;x<30;x++)for(int z=0;z<30;z++)cells.add(new SectorTransaction.Cell(base.offset(x,0,z),Blocks.AIR.defaultBlockState(),Blocks.STONE_BRICKS.defaultBlockState()));
            var plan=new SectorTransaction.Plan("north","LEASE_FRESH","LEASE_BASIC",0,1,cells,List.of(new SectorTransaction.Read(support,Blocks.STONE.defaultBlockState())));
            var neighbors=new EnumMap<SectorContracts.Direction,SectorContracts.Neighbor>(SectorContracts.Direction.class);for(var d:side.adjacent())neighbors.put(d,new SectorContracts.Neighbor(0,""));
            return new SectorContracts.Contract(c.root().toString(),"a",side,plan,Map.of(SectorContracts.Corner.NORTH_WEST,NORTH,SectorContracts.Corner.NORTH_EAST,NORTH),neighbors,Set.of(),SectorPriceCatalog.cost(side,source));
        }
    }
    private static final class PublicFixture implements StreetscapeTransaction.Environment {
        final GameTestHelper helper;final StreetscapeR5.Plan plan;final Path path;final CompoundTag ledger=new CompoundTag();
        PublicFixture(GameTestHelper h,int offset)throws IOException{helper=h;plan=StreetscapeR5.plan(new MapAnchor("base-test",offset,200,offset,0));path=h.getLevel().getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().resolve("data/base-init-test/"+UUID.randomUUID()+".dat");Files.createDirectories(path.getParent());ledger.putInt("resources",1234);}
        void stage(){
            var positions=new HashSet<BlockPos>();for(var c:plan.cells())positions.add(c.pos());
            for(var c:plan.cells())if(c.before().getBlock() instanceof net.minecraft.world.level.block.FallingBlock&&!positions.contains(c.pos().below())){level().getChunkAt(c.pos());level().setBlock(c.pos().below(),Blocks.STONE.defaultBlockState(),FLAGS);}
            for(var c:plan.cells()){level().getChunkAt(c.pos());level().setBlock(c.pos(),c.before(),FLAGS);}
        }
        public ServerLevel level(){return helper.getLevel();}public Path file(){return path;}public StreetscapeR5.Plan plan(){return plan;}
        public CompoundTag identity(){var n=new CompoundTag();n.putString("isolated",path.toString());return n;}public CompoundTag ledger(){return ledger.copy();}
        public void save(){}public void allowWrite(BlockPos p)throws IOException{if(plan.cells().stream().noneMatch(c->c.pos().equals(p)&&c.changes()))throw new IOException("Outside public sparse delta");}
    }
}
