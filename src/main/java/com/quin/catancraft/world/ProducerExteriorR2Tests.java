package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.economy.*;
import com.quin.catancraft.data.NationData;
import com.quin.catancraft.data.ResourceType;
import com.quin.catancraft.map.MapAnchor;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.gametest.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Native R2 transaction regressions. Injected I/O interruptions do not claim process-kill coverage. */
@GameTestHolder("catancraft_producer_r2") @PrefixGameTestTemplate(false)
public final class ProducerExteriorR2Tests {
    private static final ProducerTransaction.Action APPLY=ProducerTransaction.Action.APPLY,RESUME=ProducerTransaction.Action.RESUME,ROLLBACK=ProducerTransaction.Action.ROLLBACK;
    private static final int FLAGS=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS;
    @GameTest(template="townhall_state_empty",batch="producer_r2_01",timeoutTicks=1200)
    public static void nine_exact_pairs_both_rotations(GameTestHelper h) {
        try {
            int[][] counts={{34,32,33},{40,179,179},{178,178,212}};
            for(int rotation:new int[]{0,180}) {
                int total=0;
                for(int fi=0;fi<3;fi++)for(int tier=3;tier<=5;tier++) {
                    String f=ProducerRevisions.FAMILIES.get(fi);MapAnchor a=new MapAnchor("audit",100,81,100,rotation);
                    var old=ProducerRevisions.plan(f,tier,a);var next=ProducerExteriorR2.plan(f,tier,a);int n=0;
                    h.assertTrue(next.size()==45167,"Incomplete R2 full read context");
                    for(int i=0;i<old.size();i++) {
                        h.assertTrue(old.get(i).position().equals(next.get(i).position()),"Changed geometry anchor");
                        if(!old.get(i).state().equals(next.get(i).state())) {
                            n++;h.assertTrue(old.get(i).state().is(net.minecraft.world.level.block.Blocks.GLASS)||old.get(i).state().is(net.minecraft.world.level.block.Blocks.LIGHT_BLUE_STAINED_GLASS),"R2 changed source outside exact approved glass IDs at "+old.get(i).position()+": "+old.get(i).state());
                            h.assertTrue(!ProducerRevisions.protectedCell(old.get(i).state())&&!ProducerRevisions.protectedCell(next.get(i).state()),"Protected producer cell changed");
                        }
                    }
                    h.assertTrue(n==counts[fi][tier-3],"Exact R2 delta count mismatch");total+=n;
                    h.assertTrue(ProducerExteriorR2.token(f,tier).equals(f+"@resource_exterior_r2_l"+tier),"R2 token changed");
                }
                h.assertTrue(total==1065,"Nine pairs must total 1065");
            }
            h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    @GameTest(template="townhall_state_empty",batch="producer_r2_02",timeoutTicks=1200)
    public static void paid_l5_free_north(GameTestHelper h){free(h,0);}
    @GameTest(template="townhall_state_empty",batch="producer_r2_03",timeoutTicks=1200)
    public static void paid_l5_free_south(GameTestHelper h){free(h,180);}
    @GameTest(template="townhall_state_empty",batch="producer_r2_04",timeoutTicks=1200)
    public static void interrupted_forward_reverse_north(GameTestHelper h){recovery(h,0);}
    @GameTest(template="townhall_state_empty",batch="producer_r2_05",timeoutTicks=1200)
    public static void interrupted_forward_reverse_south(GameTestHelper h){recovery(h,180);}
    @GameTest(template="townhall_state_empty",batch="producer_r2_06",timeoutTicks=1200)
    public static void future_purchase_original_source(GameTestHelper h){purchase(h,0,false);}
    @GameTest(template="townhall_state_empty",batch="producer_r2_07",timeoutTicks=1200)
    public static void future_purchase_corrected_source(GameTestHelper h){purchase(h,180,true);}
    @GameTest(template="townhall_state_empty",batch="producer_r2_08",timeoutTicks=1200)
    public static void pending_r1_contract_recovers_unchanged(GameTestHelper h) {
        try {
            var e=new ProducerTests.Env(h,0,3);prepareSky(e,3);e.select(3,4);CompoundTag description=ProducerTransaction.describe(e.spec());
            for(Tag tag:description.getList("plots",Tag.TAG_COMPOUND)) {
                CompoundTag plot=(CompoundTag)tag;
                h.assertTrue(!plot.contains("sourceExteriorR2")&&!plot.contains("targetExteriorR2"),"Legacy description changed");
                h.assertTrue(plot.getString("sourceHash").equals(ProducerRevisions.hash(plot.getString("family"),3)),"Legacy source pin changed");
            }
            interrupt(h,()->ProducerTransaction.run(e,APPLY,(s,n)->{if(s.equals("write")&&n==7)throw new IOException("Legacy pending fixture");}));
            CompoundTag journal=ProducerJournal.read(e.file());e.reload();
            h.assertTrue(description.equals(ProducerTransaction.describe(e.spec())),"Reload reinterpreted R1 contract");
            h.assertTrue(ProducerTransaction.run(e,RESUME).writes()==4775-7,"Legacy remaining count changed");e.assertGeometry(4);
            h.assertTrue(journal.getString("phase").equals("APPLYING"),"Fixture not pending");h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    @GameTest(template="townhall_state_empty",batch="producer_r2_09",timeoutTicks=1200)
    public static void last_plot_conflict_refuses_group_without_charge(GameTestHelper h) {
        try {
            var e=new ProducerTests.Env(h,0,5);prepareSky(e,5);select(e,5,5,false,true);
            var original=ProducerRevisions.plan("lumberyard",5,e.anchors.get("lumberyard"));
            var corrected=ProducerExteriorR2.plan("lumberyard",5,e.anchors.get("lumberyard"));
            int selected=-1;for(int i=0;i<original.size();i++)if(!original.get(i).state().equals(corrected.get(i).state())){selected=i;break;}
            h.assertTrue(selected>=0,"Missing conflict fixture");
            e.level().setBlock(original.get(selected).position(),net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK.defaultBlockState(),FLAGS);
            var before=e.snapshot();var ledger=e.ledger();boolean refused=false;
            try{ProducerTransaction.run(e,APPLY);}catch(IOException expected){refused=true;}
            h.assertTrue(refused&&!Files.exists(e.file()),"Unknown R2 source did not refuse before journal");
            h.assertTrue(before.equals(e.snapshot())&&ledger.equals(e.ledger()),"Group conflict changed earlier plots or ledger");h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    /** Fixture-only high sky isolation: source bytes/properties stay exact, natural terrain cannot
     * attach fences at the read boundary, and native crop survival sees unobstructed skylight. */
    private static void prepareSky(ProducerTests.Env e,int visual)throws IOException {
        if(!(e.level().getServer() instanceof GameTestServer))throw new IOException("Disposable GameTestServer required");
        for(String family:ProducerRevisions.FAMILIES) {
            MapAnchor old=e.anchors.get(family);var asset=SchematicAssetRegistry.asset(family);
            e.anchors.put(family,new MapAnchor(old.id(),old.x(),260+asset.anchorY(),old.z(),old.rotation()));
        }
        Set<net.minecraft.core.BlockPos> contexts=new HashSet<>();
        List<net.minecraft.world.phys.AABB> bounds=new ArrayList<>();
        for(String family:ProducerRevisions.FAMILIES) {
            var plan=ProducerRevisions.plan(family,visual,e.anchors.get(family));
            for(var c:plan)contexts.add(c.position());bounds.add(TownHallRepairTransaction.bounds(plan));
        }
        for(var box:bounds) {
            for(int x=((int)box.minX-2)>>4;x<=((int)box.maxX+2)>>4;x++)for(int z=((int)box.minZ-2)>>4;z<=((int)box.maxZ+2)>>4;z++){e.level().setChunkForced(x,z,true);e.level().getChunk(x,z);}
            for(var p:net.minecraft.core.BlockPos.betweenClosed((int)box.minX-2,258,(int)box.minZ-2,(int)box.maxX+1,308,(int)box.maxZ+1)) {
                if(contexts.contains(p))continue;
                var previous=e.level().getBlockEntity(p);if(previous instanceof net.minecraft.world.Container container)container.clearContent();
                e.level().setBlock(p,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),FLAGS);
            }
        }
        e.prepare(visual);
        CatanCraft.LOGGER.info("PRODUCER_R2_SKY_FIXTURE floor=260 ceiling=306 airShell=2 sourceStates=UNCHANGED rotation={}",e.rotation);
    }
    private static void select(ProducerTests.Env e,int source,int target,boolean sourceR2,boolean targetR2) {
        List<ProducerTransaction.Plot> plots=new ArrayList<>();for(String f:ProducerRevisions.FAMILIES)plots.add(new ProducerTransaction.Plot(f,source,target,e.anchors.get(f),sourceR2,targetR2));
        e.spec=new ProducerTransaction.Spec(e.territory().producerLevel(),target,plots);
    }
    private static void geometry(GameTestHelper h,ProducerTests.Env e,int tier,boolean r2)throws IOException {
        for(String f:ProducerRevisions.FAMILIES)for(var c:r2?ProducerExteriorR2.plan(f,tier,e.anchors.get(f)):ProducerRevisions.plan(f,tier,e.anchors.get(f)))
            h.assertTrue(ProducerRevisions.matches(e.level().getBlockState(c.position()),c.state()),"R2 geometry mismatch at "+c.position());
    }
    private static void free(GameTestHelper h,int rotation) {
        try {
            var e=new ProducerTests.Env(h,rotation,5);prepareSky(e,5);select(e,5,5,false,true);
            CompoundTag nation=e.nation().save(),territory=e.territory().save();var inventories=e.inventories();
            h.assertTrue(ProducerTransaction.run(e,APPLY).writes()==424,"L5 R2 must change 424 cells");geometry(h,e,5,true);
            h.assertTrue(nation.equals(e.nation().save())&&territory.equals(e.territory().save()),"Free repair changed paid progress/economy");
            h.assertTrue(inventories.equals(e.inventories()),"Free repair lost actual inventory NBT");
            byte[] journal=Files.readAllBytes(e.file());e.reload();h.assertTrue(ProducerTransaction.run(e,APPLY).writes()==0,"Repeat not no-op");
            h.assertTrue(Arrays.equals(journal,Files.readAllBytes(e.file())),"Repeat altered historical journal");
            h.assertTrue(ProducerTransaction.run(e,ROLLBACK).writes()==424,"Free rollback count");geometry(h,e,5,false);
            h.assertTrue(ProducerTransaction.run(e,ROLLBACK).writes()==0,"Repeated free rollback writes");
            h.assertTrue(nation.equals(e.nation().save())&&inventories.equals(e.inventories()),"Free rollback refunded or lost NBT");
            CatanCraft.LOGGER.info("PRODUCER_R2_FREE rotation={} changes=424 charge=0 duplicate=PASS rollback=PASS NBT=PASS",rotation);h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    private static void recovery(GameTestHelper h,int rotation) {
        try {
            var e=new ProducerTests.Env(h,rotation,5);prepareSky(e,5);select(e,5,5,false,true);var nation=e.nation().save();var inventory=e.inventories();
            interrupt(h,()->ProducerTransaction.run(e,APPLY,(s,n)->{if(s.equals("write")&&n==7)throw new IOException("R2 forward fixture");}));e.reload();
            h.assertTrue(ProducerTransaction.run(e,RESUME).writes()==417,"R2 remaining forward count");geometry(h,e,5,true);
            interrupt(h,()->ProducerTransaction.run(e,ROLLBACK,(s,n)->{if(s.equals("rollback_write")&&n==9)throw new IOException("R2 reverse fixture");}));e.reload();
            h.assertTrue(ProducerTransaction.run(e,ROLLBACK).writes()==415,"R2 remaining reverse count");geometry(h,e,5,false);
            h.assertTrue(nation.equals(e.nation().save())&&inventory.equals(e.inventories()),"R2 recovery changed money/NBT");h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    private static void purchase(GameTestHelper h,int rotation,boolean corrected) {
        try {
            var e=new ProducerTests.Env(h,rotation,4);prepareSky(e,4);
            if(corrected) {
                for(String f:ProducerRevisions.FAMILIES) {
                    var original=ProducerRevisions.plan(f,4,e.anchors.get(f));var next=ProducerExteriorR2.plan(f,4,e.anchors.get(f));
                    for(int i=0;i<next.size();i++)if(!original.get(i).state().equals(next.get(i).state()))e.level().setBlock(next.get(i).position(),next.get(i).state(),FLAGS);
                    e.token(f,ProducerExteriorR2.token(f,4));
                }
                e.save();
            }
            // Let source skylight/placement initialization settle before checking final survival.
            h.runAfterDelay(20,()->{try {
                select(e,4,5,corrected,true);var before=e.nation().save();var inventory=e.inventories();EconomyCost cost=e.spec().cost();
                ProducerTransaction.run(e,APPLY);geometry(h,e,5,true);NationData old=NationData.load(before);
                h.assertTrue(e.nation().treasury()==old.treasury()-cost.money(),"R2 upgrade money charged incorrectly");
                for(ResourceType type:ResourceType.values())h.assertTrue(e.nation().resource(type)==old.resource(type)-cost.resources().getOrDefault(type,0L),"R2 upgrade resources charged incorrectly");
                h.assertTrue(e.territory().producerLevel()==5&&inventory.equals(e.inventories()),"R2 purchase lost level/NBT");
                var paid=e.nation().save();e.reload();h.assertTrue(ProducerTransaction.run(e,APPLY).writes()==0&&paid.equals(e.nation().save()),"R2 purchase charged twice");
                h.runAfterDelay(40,()->{try {
                    geometry(h,e,5,true);h.assertTrue(inventory.equals(e.inventories())&&paid.equals(e.nation().save()),"Delayed R2 upgrade changed inventory/payment");
                    ProducerTransaction.run(e,ProducerTransaction.Action.STATUS);h.succeed();
                }catch(Exception ex){throw new RuntimeException(ex);}});
            }catch(Exception ex){throw new RuntimeException(ex);}});

        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    private interface Checked {void run()throws IOException;}
    private static void interrupt(GameTestHelper h,Checked operation)throws IOException {
        boolean interrupted=false;try{operation.run();}catch(IOException expected){interrupted=true;}
        h.assertTrue(interrupted,"Expected injected durable interruption");
    }
}
