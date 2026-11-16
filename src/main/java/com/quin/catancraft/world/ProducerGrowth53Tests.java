package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.*;
import com.quin.catancraft.map.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraftforge.gametest.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Native spread under pinned top slabs, isolated fixtures only. No soil is
 * painted in positive tests: every DIRT->GRASS change comes from randomTick. */
@GameTestHolder("catancraft_producer_growth53") @PrefixGameTestTemplate(false)
public final class ProducerGrowth53Tests {
    private static final int FLAGS=2|16|32;
    private static final ProducerTransaction.Action APPLY=ProducerTransaction.Action.APPLY,RESUME=ProducerTransaction.Action.RESUME,STATUS=ProducerTransaction.Action.STATUS,ROLLBACK=ProducerTransaction.Action.ROLLBACK;
    private record Revision(int tier,boolean exterior){}
    private static final List<Revision> REVISIONS=List.of(new Revision(1,false),new Revision(2,false),new Revision(3,false),new Revision(4,false),new Revision(5,false),new Revision(3,true),new Revision(4,true),new Revision(5,true));
    @GameTest(template="townhall_state_empty",batch="producer_growth53_01",timeoutTicks=2500)
    public static void north_all_eight_pinned_revisions_grow_seventeen(GameTestHelper h)throws Exception {revision(h,0,0);}
    @GameTest(template="townhall_state_empty",batch="producer_growth53_02",timeoutTicks=2500)
    public static void south_all_eight_pinned_revisions_grow_seventeen(GameTestHelper h)throws Exception {revision(h,180,0);}
    private static void revision(GameTestHelper h,int rotation,int index)throws Exception {
        if(index==REVISIONS.size()){CatanCraft.LOGGER.info("PRODUCER_GROWTH53_REVISIONS rotation={} revisions=8 nativeGrassCells=136",rotation);h.succeed();return;}
        var asset=SchematicAssetRegistry.asset("farm");BlockPos origin=h.absolutePos(new BlockPos(2,2,2));
        var anchor=new MapAnchor("growth53",origin.getX()+(rotation==0?asset.anchorX():30-asset.anchorX()),origin.getY()+asset.anchorY(),origin.getZ()+(rotation==0?asset.anchorZ():30-asset.anchorZ()),rotation);
        Revision r=REVISIONS.get(index);var plan=r.exterior()?ProducerExteriorR2.plan("farm",r.tier(),anchor):ProducerRevisions.plan("farm",r.tier(),anchor);
        if(!(h.getLevel().getServer() instanceof GameTestServer))throw new IOException("Native growth fixture requires isolated GameTest server");
        for(var cell:plan){if(h.getLevel().getBlockEntity(cell.position()) instanceof Container c)c.clearContent();h.getLevel().setBlock(cell.position(),cell.state(),FLAGS);}
        grow(h,plan,ProducerGrowth53.covers("farm",r.tier(),r.exterior(),anchor),()->revision(h,rotation,index+1));
    }
    @GameTest(template="townhall_state_empty",batch="producer_growth53_03",timeoutTicks=1500)
    public static void north_native_growth_adopts_all_plots_without_writes(GameTestHelper h)throws Exception {adopt(h,"d");}
    @GameTest(template="townhall_state_empty",batch="producer_growth53_04",timeoutTicks=1500)
    public static void south_native_growth_adopts_all_plots_without_writes(GameTestHelper h)throws Exception {adopt(h,"m");}
    private static void adopt(GameTestHelper h,String city)throws Exception {
        var fixture=new ProducerAdoption53Tests.Fixture(h,city);
        var covers=ProducerGrowth53.covers("farm",1,false,SchematicAssetRegistry.territoryAnchor("farm",fixture.definition));
        grow(h,fixture.plans.get("farm"),covers,()->{
            try(fixture) {
                var blocks=fixture.blocks();var inventories=fixture.inventories();CompoundTag before=fixture.data.save(new CompoundTag());
                var result=ProducerService.adoptPrebuilt(fixture.server(),fixture.data,fixture.definition);
                h.assertTrue(result.success()&&result.changedBlocks()==0,"Native sub-slab source adoption failed: "+result.message());
                var expected=CatanSavedData.load(before);for(String family:fixture.plans.keySet())expected.setPlacedMapAsset(fixture.key(family),ProducerRevisions.token(family,1));
                h.assertTrue(expected.save(new CompoundTag()).equals(fixture.data.save(new CompoundTag()))&&blocks.equals(fixture.blocks())&&inventories.equals(fixture.inventories()),"Adoption changed soil, inventory, money or unrelated state");
                CatanCraft.LOGGER.info("PRODUCER_GROWTH53_ADOPTION city={} nativeGrassCells=17 writes=0 charge=0",city);h.succeed();
            }
        });
    }
    @GameTest(template="townhall_state_empty",batch="producer_growth53_05",timeoutTicks=2000)
    public static void north_growth_before_progression_preserves_reads(GameTestHelper h)throws Exception {progress(h,0,false);}
    @GameTest(template="townhall_state_empty",batch="producer_growth53_06",timeoutTicks=2000)
    public static void south_growth_after_prepared_intent_resumes(GameTestHelper h)throws Exception {progress(h,180,true);}
    private static void progress(GameTestHelper h,int rotation,boolean prepared)throws Exception {
        var e=new ProducerLineage53Tests.Fixture(h,rotation);e.base.prepare(1);e.select(1,2);
        if(prepared){boolean[] reached={false};refuse(h,()->ProducerTransaction.run(e,APPLY,(stage,writes)->{if(stage.equals("prepared")){reached[0]=true;throw new IOException("Intentional prepared farm growth interruption");}}));h.assertTrue(reached[0],"Did not reach original prepared intent");}
        var anchor=e.base.anchors.get("farm");var covers=ProducerGrowth53.covers("farm",1,false,anchor);
        grow(h,ProducerRevisions.plan("farm",1,anchor),covers,()->{
            Map<Path,byte[]> histories=new LinkedHashMap<>();var inventory=e.base.inventories();
            for(int from=1;from<5;from++) {
                if(from>1)e.select(from,from+1);CompoundTag before=e.nation().save();
                if(from==1&&prepared){e.base.reload();ProducerTransaction.run(e,RESUME);}else ProducerTransaction.run(e,APPLY);
                NationData old=NationData.load(before);h.assertTrue(e.nation().treasury()==old.treasury()-e.spec().cost().money(),"Growth changed the exact charge");
                for(ResourceType type:ResourceType.values())h.assertTrue(e.nation().resource(type)==old.resource(type)-e.spec().cost().resources().getOrDefault(type,0L),"Growth changed resource debit");
                for(BlockPos pos:covers.keySet())h.assertTrue(e.level().getBlockState(pos).equals(Blocks.GRASS_BLOCK.defaultBlockState()),"Progression repainted unchanged grown grass");
                var journal=ProducerJournal.read(e.file());h.assertTrue(ProducerGrowth53.describe(covers).equals(journal.getCompound(ProducerGrowth53.FIELD)),"Producer growth proof differs");
                List<BlockPos> positions=new ArrayList<>();for(var p:e.spec().plots()) {
                    var plan=p.sourceR2()?ProducerExteriorR2.plan(p.family(),p.source(),p.anchor()):ProducerRevisions.plan(p.family(),p.source(),p.anchor());
                    for(var cell:plan)positions.add(cell.position());
                }
                for(Tag value:journal.getList("blocks",Tag.TAG_COMPOUND)) {
                    int index=((CompoundTag)value).getInt("index");h.assertTrue(!covers.containsKey(positions.get(index)),"Growth soil was added to a sparse write before-image");
                }
                e.verifyTarget();e.base.reload();h.assertTrue(ProducerTransaction.run(e,APPLY).writes()==0,"Completed grown-source purchase replayed writes");
                for(var history:histories.entrySet())h.assertTrue(Arrays.equals(history.getValue(),Files.readAllBytes(history.getKey())),"Earlier grown-source journal changed");
                histories.put(e.file(),Files.readAllBytes(e.file()));
            }
            ProducerTransaction.run(e,ROLLBACK);ProducerTransaction.run(e,STATUS);
            for(BlockPos pos:covers.keySet())h.assertTrue(e.level().getBlockState(pos).equals(Blocks.GRASS_BLOCK.defaultBlockState()),"Rollback repainted natural growth");
            h.assertTrue(inventory.equals(e.base.inventories()),"Grown-source progression changed inventory");
            CatanCraft.LOGGER.info("PRODUCER_GROWTH53_PROGRESS rotation={} prepared={} nativeGrassCells=17 tiers=1..5 rollback=true immutableReads=true",rotation,prepared);h.succeed();
        });
    }
    @GameTest(template="townhall_state_empty",batch="producer_growth53_07",timeoutTicks=1500)
    public static void changed_slabs_and_unqualified_ground_refuse_adoption(GameTestHelper h)throws Exception {
        try(var f=new ProducerAdoption53Tests.Fixture(h,"d")) {
            var covers=ProducerGrowth53.covers("farm",1,false,SchematicAssetRegistry.territoryAnchor("farm",f.definition));BlockPos p=covers.keySet().iterator().next();BlockState original=covers.get(p);
            f.level().setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),FLAGS); // Deliberate negative fixture, never positive growth evidence.
            for(BlockState wrong:List.of(Blocks.AIR.defaultBlockState(),original.setValue(SlabBlock.TYPE,SlabType.BOTTOM),original.setValue(SlabBlock.TYPE,SlabType.DOUBLE),original.setValue(SlabBlock.WATERLOGGED,true),Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.TOP))) {
                f.level().setBlock(p.above(),wrong,FLAGS);h.assertTrue(!ProducerGrowth53.matches(f.level(),p,f.level().getBlockState(p),covers),"Wrong slab gained growth permission");f.refuses("Unrecognized prebuilt producer");
            }
            f.level().setBlock(p.above(),original,FLAGS);f.level().setBlock(p,Blocks.DIRT.defaultBlockState(),FLAGS);
            var outside=f.plans.get("farm").stream().filter(c->c.state().equals(Blocks.DIRT.defaultBlockState())&&!covers.containsKey(c.position())).findFirst().orElseThrow();
            f.level().setBlock(outside.position(),Blocks.GRASS_BLOCK.defaultBlockState(),FLAGS);f.refuses("Unrecognized prebuilt producer");
            h.assertTrue(ProducerGrowth53.covers("quarry",1,false,SchematicAssetRegistry.territoryAnchor("quarry",f.definition)).isEmpty(),"Farm growth policy leaked to quarry");h.succeed();
        }
    }
    @GameTest(template="townhall_state_empty",batch="producer_growth53_08",timeoutTicks=2000)
    public static void legacy_and_forged_growth_proofs_refuse_without_changes(GameTestHelper h)throws Exception {
        var e=new ProducerLineage53Tests.Fixture(h,0);e.base.prepare(1);e.select(1,2);boolean[] prepared={false};
        refuse(h,()->ProducerTransaction.run(e,APPLY,(stage,writes)->{if(stage.equals("prepared")){prepared[0]=true;throw new IOException("Intentional growth-proof preparation");}}));h.assertTrue(prepared[0],"Preparation not reached");
        CompoundTag original=ProducerJournal.read(e.file());var anchor=e.base.anchors.get("farm");
        grow(h,ProducerRevisions.plan("farm",1,anchor),ProducerGrowth53.covers("farm",1,false,anchor),()->{
            CompoundTag old=original.copy();old.remove(ProducerGrowth53.FIELD);ProducerJournal.write(e.file(),old,true);e.refuseUnchanged(RESUME);e.refuseUnchanged(ROLLBACK);
            CompoundTag changed=original.copy();changed.getCompound(ProducerGrowth53.FIELD).putInt("version",2);ProducerJournal.write(e.file(),changed,true);e.refuseUnchanged(RESUME);
            changed=original.copy();changed.getCompound(ProducerGrowth53.FIELD).getList("covers",Tag.TAG_COMPOUND).getCompound(0).putLong("pos",new BlockPos(0,0,0).asLong());ProducerJournal.write(e.file(),changed,true);e.refuseUnchanged(RESUME);
            ProducerJournal.write(e.file(),original,true);e.base.reload();ProducerTransaction.run(e,RESUME);e.verifyTarget();h.succeed();
        });
    }
    private interface Work{void run()throws Exception;}
    private static void refuse(GameTestHelper h,Work work)throws Exception {boolean refused=false;try{work.run();}catch(IOException expected){refused=true;}h.assertTrue(refused,"Expected growth-proof refusal missing");}
    private static void grow(GameTestHelper h,List<SchematicPlacementService.PlanCell> plan,Map<BlockPos,BlockState> covers,Work done)throws Exception {
        h.assertTrue(covers.size()==17,"Expected exact17-cell farm growth contract");ServerLevel level=h.getLevel();Map<BlockPos,BlockState> states=new HashMap<>();for(var c:plan)states.put(c.position(),c.state());
        Set<BlockPos> ticks=new LinkedHashSet<>();for(BlockPos p:covers.keySet())for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=-1;y<=3;y++) {
            BlockPos q=p.offset(x,y,z);BlockState above=states.get(q.above());if(Blocks.GRASS_BLOCK.defaultBlockState().equals(states.get(q))&&above!=null&&above.getFluidState().isEmpty()&&!ProducerService.naturalCover(above,q.above()))ticks.add(q);
        }
        h.assertTrue(!ticks.isEmpty(),"Missing original grass propagation source");ticks.addAll(covers.keySet());
        for(BlockPos p:covers.keySet())h.assertTrue(level.getBlockState(p).equals(Blocks.DIRT.defaultBlockState()),"Positive native growth did not begin at original dirt");
        level.setDayTime(6000);SectorChunkLease lease=SectorChunkLease.acquire(level,TownHallRepairTransaction.bounds(plan).inflate(4));
        await(h,covers,List.copyOf(ticks),lease,done,0);
    }
    private static void await(GameTestHelper h,Map<BlockPos,BlockState> covers,List<BlockPos> ticks,SectorChunkLease lease,Work done,int attempt) {
        h.runAfterDelay(attempt==0?20:2,()->{try {
            lease.keepAlive();boolean lit=lease.ready()&&ticks.stream().anyMatch(p->h.getLevel().getBlockState(p).is(Blocks.GRASS_BLOCK)&&h.getLevel().getMaxLocalRawBrightness(p.above())>=9);
            if(!lit&&attempt<100){await(h,covers,ticks,lease,done,attempt+1);return;}h.assertTrue(lit,"Native source light/chunks did not settle");
            RandomSource random=RandomSource.create(53017);int grown=0,sweeps=0;
            for(;sweeps<1024&&grown<17;sweeps++) {
                for(BlockPos p:ticks)if(h.getLevel().getBlockState(p).is(Blocks.GRASS_BLOCK))Blocks.GRASS_BLOCK.randomTick(h.getLevel().getBlockState(p),h.getLevel(),p,random);
                grown=(int)covers.keySet().stream().filter(p->h.getLevel().getBlockState(p).equals(Blocks.GRASS_BLOCK.defaultBlockState())).count();
            }
            h.assertTrue(grown==17,"Native top-slab growth reached only "+grown+"/17; no full qualification inferred");
            for(var entry:covers.entrySet())h.assertTrue(entry.getValue().equals(h.getLevel().getBlockState(entry.getKey().above())),"Native growth altered a slab");
            CatanCraft.LOGGER.info("PRODUCER_GROWTH53_NATIVE grown=17 sweeps={} resident={}",sweeps,lease.ready());
            lease.close();done.run();
        }catch(Exception|AssertionError error){lease.close();throw new RuntimeException(error);}});
    }
}
