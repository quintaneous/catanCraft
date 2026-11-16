package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Funded native geometry/accounting fixtures, separate from no-grant progression. */
@GameTestHolder("catancraft_producer_lineage53") @PrefixGameTestTemplate(false)
public final class ProducerLineage53Tests {
    private static final ProducerTransaction.Action APPLY=ProducerTransaction.Action.APPLY,RESUME=ProducerTransaction.Action.RESUME,
        STATUS=ProducerTransaction.Action.STATUS,ROLLBACK=ProducerTransaction.Action.ROLLBACK;
    @GameTest(template="townhall_state_empty",batch="producer_lineage53_01",timeoutTicks=2000)
    public static void native_decay_all_tiers_north(GameTestHelper h){progress(h,0);}
    @GameTest(template="townhall_state_empty",batch="producer_lineage53_02",timeoutTicks=2000)
    public static void native_decay_all_tiers_south(GameTestHelper h){progress(h,180);}
    private static void progress(GameTestHelper h,int rotation) {
        try {
            Fixture e=new Fixture(h,rotation);e.base.prepare(1);Map<Path,byte[]> histories=new LinkedHashMap<>();
            CompoundTag beforeLast=null;
            for(int from=1;from<5;from++) {
                e.select(from,from+1);int decays=e.decay(from);var inventory=e.base.inventories();CompoundTag before=e.nation().save();
                h.assertTrue(decays>0,"Each earned tier must exercise real covered-source decay");
                if(from==2||from==3) {
                    boolean[] interrupted={false};
                    refuse(h,()->ProducerTransaction.run(e,APPLY,(stage,writes)->{if(stage.equals("write")&&writes==7){interrupted[0]=true;throw new IOException("Intentional producer lineage write interruption");}}));
                    h.assertTrue(interrupted[0],"Recovery fixture failed before actual seventh write");
                    CompoundTag prepared=ProducerJournal.read(e.file());e.base.reload();ProducerTransaction.run(e,RESUME);
                    h.assertTrue(prepared.get("blocks").equals(ProducerJournal.read(e.file()).get("blocks")),"Recovery changed actual before-images");
                }else ProducerTransaction.run(e,APPLY);
                exactCharge(h,before,e);e.verifyTarget();
                h.assertTrue(inventory.equals(e.base.inventories()),"Producer lineage changed current barrel inventory");
                CompoundTag journal=ProducerJournal.read(e.file());
                if(from==2) {
                    long capturedDirt=journal.getList("blocks",Tag.TAG_COMPOUND).stream().map(t->((CompoundTag)t).getCompound("before"))
                        .filter(t->t.equals(NbtUtils.writeBlockState(Blocks.DIRT.defaultBlockState()))).count();
                    h.assertTrue(capturedDirt==16,"Expected16 actual decayed lumberyard soil before-images");
                }
                if(from>=3)h.assertTrue(!journal.getCompound("naturalSoil53").getCompound("parent").isEmpty(),"Later tier lost exact committed soil parent");
                for(var old:histories.entrySet())h.assertTrue(Arrays.equals(old.getValue(),Files.readAllBytes(old.getKey())),"Previous committed producer journal changed");
                histories.put(e.file(),Files.readAllBytes(e.file()));
                h.assertTrue(ProducerTransaction.run(e,APPLY).writes()==0&&Arrays.equals(histories.get(e.file()),Files.readAllBytes(e.file())),"Completed producer replay wrote history");
                beforeLast=before;
            }
            e.decay(5);ProducerTransaction.run(e,ROLLBACK);e.base.reload();
            h.assertTrue(beforeLast.equals(e.nation().save())&&e.territory().producerLevel()==4,"Rollback did not refund only original final-tier cost");
            ProducerTransaction.run(e,STATUS);byte[] rolled=Files.readAllBytes(e.file());
            h.assertTrue(ProducerTransaction.run(e,ROLLBACK).writes()==0&&Arrays.equals(rolled,Files.readAllBytes(e.file())),"Natural target dirt caused rollback replay");
            CatanCraft.LOGGER.info("PRODUCER_LINEAGE53 rotation={} tiers=1..5 nativeDecay=true actualChangedDirt=16 partialRecovery=2 immutableParents=true exactRefund=true",rotation);h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }

    @GameTest(template="townhall_state_empty",batch="producer_lineage53_03",timeoutTicks=2000)
    public static void forged_history_and_unproved_dirt_north(GameTestHelper h){negative(h,0);}
    @GameTest(template="townhall_state_empty",batch="producer_lineage53_04",timeoutTicks=2000)
    public static void forged_history_and_unproved_dirt_south(GameTestHelper h){negative(h,180);}
    private static void negative(GameTestHelper h,int rotation) {
        try {
            Fixture e=new Fixture(h,rotation);e.base.prepare(1);e.select(1,2);ProducerTransaction.run(e,APPLY);Path first=e.file();byte[] original=Files.readAllBytes(first);
            // A formerly covered source is insufficient if no dirt was captured or observed.
            var old=ProducerRevisions.plan("quarry",1,e.base.anchors.get("quarry"));var next=ProducerRevisions.plan("quarry",2,e.base.anchors.get("quarry"));
            Map<BlockPos,BlockState> covers=ProducerService.producerCoveredGrass(old),nextCovers=ProducerService.producerCoveredGrass(next);
            BlockPos exposed=old.stream().filter(c->covers.containsKey(c.position())&&!nextCovers.containsKey(c.position())
                &&next.get(c.localY()*31*31+c.localZ()*31+c.localX()).state().equals(Blocks.GRASS_BLOCK.defaultBlockState())).findFirst().orElseThrow().position();
            e.level().setBlock(exposed,Blocks.DIRT.defaultBlockState(),2|16|32);e.refuseUnchanged(STATUS);e.select(2,3);e.refuseUnchanged(APPLY);
            e.level().setBlock(exposed,Blocks.GRASS_BLOCK.defaultBlockState(),2|16|32);
            CompoundTag parent=ProducerTransaction.naturalProof(ProducerJournal.read(first));
            for(String fault:List.of("owner","receipt","phase","anchor","observation")) {
                e.parentOverride=parent.copy();
                if(fault.equals("owner"))e.parentOverride.getCompound("identity").putString("owner",UUID.randomUUID().toString());
                if(fault.equals("receipt"))e.parentOverride.putUUID("id",UUID.randomUUID());
                if(fault.equals("phase"))e.parentOverride.putString("phase","ROLLED_BACK");
                if(fault.equals("anchor"))e.parentOverride.getCompound("spec").getList("plots",Tag.TAG_COMPOUND).getCompound(0).putIntArray("anchor",new int[]{0,81,0,0});
                if(fault.equals("observation"))e.parentOverride.getCompound("naturalSoil53").putLongArray("sourceDirt",new long[]{new BlockPos(0,81,0).asLong()});
                e.refuseUnchanged(APPLY);
            }
            e.parentOverride=null;
            var source=ProducerRevisions.plan("lumberyard",2,e.base.anchors.get("lumberyard"));
            var qualified=ProducerService.producerCoveredGrass(source);BlockPos grass=qualified.entrySet().stream().filter(v->!v.getValue().hasBlockEntity()).findFirst().orElseThrow().getKey();
            BlockState cover=e.level().getBlockState(grass.above());
            e.level().setBlock(grass.above(),Blocks.GOLD_BLOCK.defaultBlockState(),2|16|32);e.refuseUnchanged(APPLY);e.level().setBlock(grass.above(),cover,2|16|32);
            h.assertTrue(Arrays.equals(original,Files.readAllBytes(first)),"Refused child operation altered its original parent");h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    private interface Work{void run()throws Exception;}
    private static void refuse(GameTestHelper h,Work work)throws Exception {boolean refused=false;try{work.run();}catch(IOException expected){refused=true;}h.assertTrue(refused,"Expected producer lineage refusal missing");}
    private static void exactCharge(GameTestHelper h,CompoundTag before,Fixture e) {
        NationData prior=NationData.load(before);var cost=e.spec().cost();h.assertTrue(e.nation().treasury()==prior.treasury()-cost.money(),"Producer charge differs");
        for(ResourceType resource:ResourceType.values())h.assertTrue(e.nation().resource(resource)==prior.resource(resource)-cost.resources().getOrDefault(resource,0L),"Producer resource charge differs");
    }
    private static final class Fixture implements ProducerTransaction.Environment {
        final GameTestHelper h;final ProducerTests.Env base;CompoundTag parentOverride;
        Fixture(GameTestHelper h,int rotation)throws IOException {this.h=h;base=new ProducerTests.Env(h,rotation,1);}
        void select(int from,int to) {
            List<ProducerTransaction.Plot> plots=new ArrayList<>();for(String family:ProducerRevisions.FAMILIES)plots.add(new ProducerTransaction.Plot(family,from,to,base.anchors.get(family),from>=3,to>=3));
            base.spec=new ProducerTransaction.Spec(territory().producerLevel(),to,plots);
        }
        int decay(int tier)throws IOException {
            int count=0;for(String family:ProducerRevisions.FAMILIES) {
                var plan=tier>=3?ProducerExteriorR2.plan(family,tier,base.anchors.get(family)):ProducerRevisions.plan(family,tier,base.anchors.get(family));
                for(var row:ProducerService.producerCoveredGrass(plan).entrySet())if(level().getBlockState(row.getKey()).equals(Blocks.GRASS_BLOCK.defaultBlockState())) {
                    if(!level().isAreaLoaded(row.getKey(),3)||!ProducerService.matchesNaturalCover(level(),row.getKey(),row.getValue()))throw new IOException("Unready exact producer native cover");
                    Blocks.GRASS_BLOCK.randomTick(level().getBlockState(row.getKey()),level(),row.getKey(),RandomSource.create(53));
                    h.assertTrue(level().getBlockState(row.getKey()).equals(Blocks.DIRT.defaultBlockState()),"Native producer grass failed to decay");count++;
                }
            }return count;
        }
        void verifyTarget()throws IOException {
            CompoundTag journal=ProducerJournal.read(file());Set<BlockPos> proved=ProducerTransaction.completedNaturalDirt(spec(),journal,identity(),receipt());
            for(var plot:spec().plots()) {
                var plan=plot.targetR2()?ProducerExteriorR2.plan(plot.family(),plot.target(),plot.anchor()):ProducerRevisions.plan(plot.family(),plot.target(),plot.anchor());
                var covers=ProducerService.producerCoveredGrass(plan);
                for(var cell:plan) {
                    BlockState actual=level().getBlockState(cell.position());h.assertTrue(ProducerRevisions.matches(actual,cell.state())
                        ||ProducerService.matchesProducerCoveredSourceGrass(level(),cell,covers)
                        ||proved.contains(cell.position())&&cell.state().equals(Blocks.GRASS_BLOCK.defaultBlockState())&&actual.equals(Blocks.DIRT.defaultBlockState()),"Earned producer target differs at "+cell.position());
                }
            }
        }
        void refuseUnchanged(ProducerTransaction.Action action)throws Exception {
            var world=base.snapshot();var inventory=base.inventories();var ledger=ledger();byte[] journal=Files.exists(file())?Files.readAllBytes(file()):null;
            refuse(h,()->ProducerTransaction.run(this,action));h.assertTrue(world.equals(base.snapshot())&&inventory.equals(base.inventories())&&ledger.equals(ledger()),"Lineage refusal mutated world/economy/inventory");
            h.assertTrue(journal==null?!Files.exists(file()):Arrays.equals(journal,Files.readAllBytes(file())),"Lineage refusal rewrote journal");
        }
        public CompoundTag previousNaturalProof()throws IOException {
            if(parentOverride!=null)return parentOverride.copy();if(receipt()==null||receipt().isBlank())return new CompoundTag();
            try(var files=Files.newDirectoryStream(base.dir,"from*to*.dat")) {
                CompoundTag found=null;for(Path path:files){CompoundTag journal=ProducerJournal.read(path);if(receipt().equals(journal.getUUID("id")+":paid")){if(found!=null)throw new IOException("Duplicate fixture parent");found=journal;}}
                if(found==null)throw new IOException("Fixture parent missing");return ProducerTransaction.naturalProof(found);
            }
        }
        public ServerLevel level(){return base.level();}public Path file(){return base.file();}public ProducerTransaction.Spec spec(){return base.spec();}
        public CompoundTag identity(){return base.identity();}public NationData nation(){return base.nation();}public TerritoryData territory(){return base.territory();}
        public String token(String family){return base.token(family);}public void token(String family,String value){base.token(family,value);}
        public String receipt(){return base.receipt();}public void receipt(String value){base.receipt(value);}
        public CompoundTag ledger(){return base.ledger();}public void dirty(){base.dirty();}public void save()throws IOException{base.save();}public void allowWrite(BlockPos pos)throws IOException{base.allowWrite(pos);}
    }
}
