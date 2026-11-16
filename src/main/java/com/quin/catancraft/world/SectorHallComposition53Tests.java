package com.quin.catancraft.world;

import com.quin.catancraft.data.NationData;
import com.quin.catancraft.economy.EconomyCost;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Real paid prestige history composed with a small explicitly synthetic wall
 * transaction. Full approved wall geometry is exercised by SectorAuthored53Tests. */
@GameTestHolder("catancraft_sector_hall_composition53") @PrefixGameTestTemplate(false)
public final class SectorHallComposition53Tests {
    private static final int FLAGS=2|16|32;
    private interface Work {void run(F f)throws Exception;}
    private interface Attempt {void run()throws Exception;}
    private static void refused(GameTestHelper h,Attempt action)throws Exception {
        boolean rejected=false;try{action.run();}catch(IOException expected){rejected=true;}
        h.assertTrue(rejected,"Expected read-only context refusal");
    }
    private static void run(GameTestHelper h,String city,Work body)throws Exception {
        F f=new F(h,city);SectorChunkLease lease=SectorChunkLease.acquire(f.hall.level,SectorService.entityBounds(f.plan));ready(h,f,lease,body,0);
    }
    private static void ready(GameTestHelper h,F f,SectorChunkLease lease,Work body,int attempts) {
        h.runAfterDelay(1,()->{
            try {
                lease.keepAlive();
                if(!lease.ready()){
                    if(attempts>=8000)throw new IOException("Composition fixture entities did not become ready");
                    ready(h,f,lease,body,attempts+1);return;
                }
                body.run(f);
            }catch(Exception failure){
                lease.close();try{f.close();}catch(Exception suppressed){failure.addSuppressed(suppressed);}
                throw new RuntimeException(failure);
            }
            lease.close();try{f.close();}catch(IOException failure){throw new RuntimeException(failure);}
            h.succeed();
        });
    }
    @GameTest(template="townhall_state_empty",batch="hallcompose53_01",timeoutTicks=14000)
    public static void older_completed_wall_accepts_later_paid_hall_without_rewriting_history(GameTestHelper h)throws Exception {
        run(h,"a",f->{SectorTransaction.run(f.env(),SectorTransaction.Action.APPLY);byte[] wall=Files.readAllBytes(f.file);f.upgrade();
            long cash=f.hall.nation().treasury();h.assertTrue(SectorTransaction.run(f.env(),SectorTransaction.Action.STATUS).writes()==0,"Status wrote wall");
            h.assertTrue(cash==f.hall.nation().treasury()&&Arrays.equals(wall,Files.readAllBytes(f.file)),"Later Hall rewrote/charged old wall history");f.assertApproach();});
    }
    @GameTest(template="townhall_state_empty",batch="hallcompose53_02",timeoutTicks=14000)
    public static void purchase_after_paid_south_hall_preserves_approach_and_exact_price(GameTestHelper h)throws Exception {
        run(h,"i",f->{f.upgrade();long cash=f.hall.nation().treasury();byte[] hall=Files.readAllBytes(f.hall.journal);
            h.assertTrue(SectorTransaction.run(f.env(),SectorTransaction.Action.APPLY).writes()==2&&f.hall.nation().treasury()==cash-73,"Wrong composed debit/writes");
            byte[] wall=Files.readAllBytes(f.file);SectorTransaction.run(f.env(),SectorTransaction.Action.APPLY);
            h.assertTrue(Arrays.equals(hall,Files.readAllBytes(f.hall.journal))&&Arrays.equals(wall,Files.readAllBytes(f.file))&&f.hall.nation().treasury()==cash-73,"Duplicate altered either paid history");f.assertApproach();});
    }
    @GameTest(template="townhall_state_empty",batch="hallcompose53_03",timeoutTicks=14000)
    public static void earned_context_replaces_grass_expectation_and_keeps_other_reads_strict(GameTestHelper h)throws Exception {
        run(h,"a",f->{f.upgrade();BlockPos p=f.approach.iterator().next();long cash=f.hall.nation().treasury();
            f.hall.level.setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),FLAGS);refused(h,()->SectorTransaction.run(f.env(),SectorTransaction.Action.APPLY));
            h.assertTrue(!Files.exists(f.file)&&f.hall.nation().treasury()==cash,"Fallback grass was accepted/charged");
            f.hall.level.setBlock(p,Blocks.POLISHED_ANDESITE.defaultBlockState(),FLAGS);f.hall.level.setBlock(f.other,Blocks.GOLD_BLOCK.defaultBlockState(),FLAGS);
            refused(h,()->SectorTransaction.run(f.env(),SectorTransaction.Action.APPLY));h.assertTrue(!Files.exists(f.file)&&f.hall.nation().treasury()==cash,"External context bypassed unrelated protected read");});
    }
    @GameTest(template="townhall_state_empty",batch="hallcompose53_04",timeoutTicks=14000)
    public static void changed_hall_stops_frozen_runner_then_resume_and_rollback_preserve_hall(GameTestHelper h)throws Exception {
        run(h,"i",f->{var runner=SectorTransaction.begin(f.env(),SectorTransaction.Action.APPLY);runner.step(1,Long.MAX_VALUE);
            h.assertTrue(runner.writes()==1&&f.progress==0,"Missing genuinely partial fixture wall");f.upgrade();long cash=f.hall.nation().treasury();
            byte[] wall=Files.readAllBytes(f.file),hall=Files.readAllBytes(f.hall.journal);refused(h,()->runner.step(1,Long.MAX_VALUE));
            h.assertTrue(Arrays.equals(wall,Files.readAllBytes(f.file))&&f.hall.nation().treasury()==cash,"Stale runner changed history/payment");
            h.assertTrue(SectorTransaction.run(f.env(),SectorTransaction.Action.RESUME).writes()==1&&f.hall.nation().treasury()==cash,"Resume repeated original debit");
            SectorTransaction.run(f.env(),SectorTransaction.Action.ROLLBACK);h.assertTrue(f.hall.nation().treasury()==cash+73&&f.progress==0,"Rollback did not refund original wall price");
            h.assertTrue(Arrays.equals(hall,Files.readAllBytes(f.hall.journal)),"Wall rollback rewrote Hall history");f.assertApproach();});
    }
    @GameTest(template="townhall_state_empty",batch="hallcompose53_05",timeoutTicks=14000)
    public static void changed_paid_hall_bytes_stop_next_slice_without_wall_mutation(GameTestHelper h)throws Exception {
        run(h,"a",f->{f.upgrade();var runner=SectorTransaction.begin(f.env(),SectorTransaction.Action.APPLY);runner.step(1,Long.MAX_VALUE);
            byte[] original=Files.readAllBytes(f.hall.journal),wall=Files.readAllBytes(f.file);long cash=f.hall.nation().treasury();byte[] corrupt=original.clone();corrupt[corrupt.length-1]^=1;
            try{Files.write(f.hall.journal,corrupt);refused(h,()->runner.step(1,Long.MAX_VALUE));
                h.assertTrue(runner.writes()==1&&Arrays.equals(wall,Files.readAllBytes(f.file))&&f.hall.nation().treasury()==cash,"Changed Hall proof allowed next wall slice");
            }finally{Files.write(f.hall.journal,original);}
            SectorTransaction.run(f.env(),SectorTransaction.Action.RESUME);f.assertApproach();});
    }
    @GameTest(template="townhall_state_empty",batch="hallcompose53_06",timeoutTicks=14000)
    public static void retained_partial_wall_waits_for_real_paid_hall_recovery_then_resumes(GameTestHelper h)throws Exception {
        run(h,"i",f->{
            var runner=SectorTransaction.begin(f.env(),SectorTransaction.Action.APPLY);runner.step(1,Long.MAX_VALUE);
            h.assertTrue(runner.writes()==1,"Missing retained partial wall");CompoundTag beforeHall=f.hall.data.save(new CompoundTag());
            var interrupted=f.hall.upgrade((stage,writes)->{if(stage.equals("write")&&writes==7)throw new IOException("Retained dual-pending fixture interruption");});
            h.assertTrue(!interrupted.success()&&Files.exists(f.hall.journal),"Missing captured partial Hall");
            byte[] originalHall=Files.readAllBytes(f.hall.journal),originalWall=Files.readAllBytes(f.file);
            var journal=PrestigePurchaseRecovery.read(originalHall);
            h.assertTrue(journal.getString("phase").equals("PENDING")&&beforeHall.equals(f.hall.data.save(new CompoundTag())),"Pending Hall charged before recovery");
            var job=new SectorService.RecoveryJob(f.hall.def.id(),f.file.getFileName().toString(),UUID.randomUUID());
            Queue<SectorService.RecoveryJob> jobs=new ArrayDeque<>(List.of(job));
            h.assertTrue(SectorRecovery.nextReady(jobs,c->SectorHallContext53.pending(f.hall.root,c),(j,m)->{})==null&&jobs.peek()==job,"Startup consumed pending wall identity");
            refused(h,()->SectorTransaction.run(f.env(),SectorTransaction.Action.RESUME));
            h.assertTrue(Arrays.equals(originalWall,Files.readAllBytes(f.file)),"Deferred wall rewrote retained history");
            f.hall.reload();PaidConstructionRecovery.resume(f.hall.recovery());f.hall.reload();f.hall.assertPaidOnce(beforeHall,journal);
            h.assertTrue(Arrays.equals(originalHall,Files.readAllBytes(f.hall.journal)),"Hall recovery rewrote original purchase");
            h.assertTrue(SectorRecovery.nextReady(jobs,c->SectorHallContext53.pending(f.hall.root,c),(j,m)->{})==job&&jobs.isEmpty(),"Canonical completed Hall did not release captured wall");
            long cash=f.hall.nation().treasury();h.assertTrue(SectorTransaction.run(f.env(),SectorTransaction.Action.RESUME).writes()==1&&f.hall.nation().treasury()==cash,"Released wall repeated debit or placement");
            h.assertTrue(SectorTransaction.run(f.env(),SectorTransaction.Action.STATUS).writes()==0&&Arrays.equals(originalHall,Files.readAllBytes(f.hall.journal)),"Recovered composition changed completed history");
            f.assertApproach();
        });
    }
    @GameTest(template="townhall_state_empty",batch="hallcompose53_07",timeoutTicks=1000)
    public static void retained_side_plan_receives_only_original_declared_hall_reads(GameTestHelper h)throws Exception {
        var def=com.quin.catancraft.map.MapDefinitionManager.territory("a");var qualified=SectorHallContext53.qualifiedPositions(def);
        h.assertTrue(qualified.size()==11,"Qualified Hall mask changed");
        for(int source=0;source<=1;source++){
            var legacy=SectorNorthP2.plan(h.getLevel(),source);CompoundTag spec=SectorTransaction.describe(legacy);
            h.assertTrue(SectorService.hallReadsForPlan(legacy,qualified).equals(qualified)&&spec.equals(SectorTransaction.describe(legacy)),"Retained P2 primary-road reads lost qualified Hall intersection or changed spec");
        }
        BlockPos one=qualified.keySet().iterator().next(),write=one.above(20);
        var cells=List.of(new SectorTransaction.Cell(write,Blocks.AIR.defaultBlockState(),Blocks.STONE.defaultBlockState()));
        var empty=new SectorTransaction.Plan("north","projection-source","projection-target",0,1,cells,List.of());
        h.assertTrue(SectorService.hallReadsForPlan(empty,qualified).isEmpty(),"Empty historical read mask acquired Hall observations");
        var partial=new SectorTransaction.Plan("north","projection-source","projection-target",0,1,cells,List.of(new SectorTransaction.Read(one,Blocks.GRASS_BLOCK.defaultBlockState())));
        var projected=SectorService.hallReadsForPlan(partial,qualified);
        h.assertTrue(projected.equals(Map.of(one,Blocks.POLISHED_ANDESITE.defaultBlockState())),"Read projection widened historical mask");
        var wrong=new SectorTransaction.Plan("north","projection-source","projection-target",0,1,cells,List.of(new SectorTransaction.Read(one,Blocks.GOLD_BLOCK.defaultBlockState())));
        refused(h,()->SectorService.hallReadsForPlan(wrong,qualified));h.succeed();
    }
    private static final class F implements AutoCloseable {
        final GameTestHelper h;final PrestigeNatural53Tests.Fixture hall;final Path file;final Set<BlockPos> approach;final BlockPos other;
        final SectorTransaction.Plan plan;int progress;String receipt="";
        F(GameTestHelper h,String city)throws Exception {
            this.h=h;hall=new PrestigeNatural53Tests.Fixture(h,city,3,1);file=hall.archive.resolve("synthetic-wall.dat");approach=SectorHallContext53.qualifiedPositions(hall.def).keySet();
            var center=hall.def.cityCenter();BlockPos p=new BlockPos(center.x(),81,center.z()-60);other=p.offset(0,0,3);
            for(int x=(p.getX()-2)>>4;x<=(p.getX()+3)>>4;x++)for(int z=(p.getZ()-2)>>4;z<=(p.getZ()+5)>>4;z++){hall.level.setChunkForced(x,z,true);hall.level.getChunk(x,z);}
            for(int x=-2;x<=3;x++)for(int y=-1;y<=2;y++)for(int z=-2;z<=5;z++)hall.level.setBlock(p.offset(x,y,z),y==-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),FLAGS);
            hall.level.setBlock(other,Blocks.STONE.defaultBlockState(),FLAGS);List<SectorTransaction.Read> reads=new ArrayList<>();
            for(BlockPos pos:approach)reads.add(new SectorTransaction.Read(pos,Blocks.GRASS_BLOCK.defaultBlockState()));reads.add(new SectorTransaction.Read(other,Blocks.STONE.defaultBlockState()));
            plan=new SectorTransaction.Plan("north","synthetic-before","synthetic-after",0,1,List.of(new SectorTransaction.Cell(p,Blocks.AIR.defaultBlockState(),Blocks.STONE_BRICKS.defaultBlockState()),new SectorTransaction.Cell(p.east(),Blocks.AIR.defaultBlockState(),Blocks.STONE_BRICKS.defaultBlockState())),reads);
        }
        void upgrade()throws Exception{var result=hall.upgrade((s,n)->{});h.assertTrue(result.success(),result.message());hall.save();}
        void assertApproach(){for(BlockPos p:approach)h.assertTrue(hall.level.getBlockState(p).equals(Blocks.POLISHED_ANDESITE.defaultBlockState()),"Wall operation changed paid Hall approach");}
        SectorTransaction.Environment env(){return new SectorTransaction.Environment(){
            SectorHallContext53.Context context;
            public ServerLevel level(){return hall.level;}public Path file(){return file;}public SectorTransaction.Plan plan(){return plan;}
            public CompoundTag identity(){CompoundTag n=new CompoundTag();n.putString("world",hall.root.toString());n.putString("city",hall.def.id());n.putUUID("owner",hall.owner);n.putString("scope","synthetic-wall-real-paid-hall");return n;}
            public EconomyCost cost(){return new EconomyCost(73,Map.of());}public NationData nation(){return hall.nation();}
            public int progress(){return progress;}public void progress(int value){progress=value;}public String receipt(){return receipt;}public void receipt(String value){receipt=value;}
            public void save()throws IOException{verifyProtectedReadContext();hall.save();}public void requirements(boolean rollback)throws IOException{verifyProtectedReadContext();}
            public void allow(BlockPos p)throws IOException{if(plan.cells().stream().noneMatch(c->c.pos().equals(p)))throw new IOException("Outside synthetic write mask");}
            public Map<BlockPos,BlockState> protectedReadContext()throws IOException {if(context==null)context=SectorHallContext53.capture(hall.root,hall.data,hall.def);return context.reads();}
            public void verifyProtectedReadContext()throws IOException{if(context!=null)context.verifyUnchanged();}
        };}
        public void close()throws IOException{hall.close();}
    }
}
