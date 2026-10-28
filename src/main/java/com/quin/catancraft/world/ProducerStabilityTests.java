package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.economy.ResourceType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.io.IOException;
import java.nio.file.Files;

/** Controlled native fixtures; no entity deletion or physics/guard suppression. */
@GameTestHolder("catancraft_producer_stability") @PrefixGameTestTemplate(false)
public final class ProducerStabilityTests {
    private static final ProducerTransaction.Action APPLY=ProducerTransaction.Action.APPLY;
    private static final ProducerTransaction.Action STATUS=ProducerTransaction.Action.STATUS;

    @GameTest(template="townhall_state_empty",batch="stability_north",timeoutTicks=4000)
    public static void repeated_native_north(GameTestHelper h){repeat(h,0,0);}
    @GameTest(template="townhall_state_empty",batch="stability_south",timeoutTicks=4000)
    public static void repeated_native_south(GameTestHelper h){repeat(h,180,0);}
    private static void repeat(GameTestHelper h,int rotation,int iteration){
        try {
            var e=new ProducerTests.Env(h,rotation,5);environment(h);e.prepare(1);e.select(1,5);
            var inventory=e.inventories();var nation=e.nation().save();
            ProducerTransaction.run(e,APPLY);
            h.runAfterDelay(10,()->{try {
                ProducerTransaction.run(e,STATUS);e.assertGeometry(5);
                h.assertTrue(inventory.equals(e.inventories()),"Repeated construction changed inventory");
                h.assertTrue(nation.equals(e.nation().save()),"Already-paid visual fixture charged");
                CatanCraft.LOGGER.info("PRODUCER_STABILITY_PASS rotation={} iteration={}",rotation,iteration);
                if(iteration==23)h.succeed();else repeat(h,rotation,iteration+1);
            }catch(Exception x){x.printStackTrace();h.fail(x.toString());}});
        }catch(Exception x){x.printStackTrace();h.fail(x.toString());}
    }

    @GameTest(template="townhall_state_empty",batch="stability_drift",timeoutTicks=600)
    public static void cave_air_drift_refuses_and_retains_original_transaction(GameTestHelper h)throws Exception {
        var e=new ProducerTests.Env(h,180,1);environment(h);e.prepare(1);e.select(1,2);
        var old=ProducerRevisions.plan("quarry",1,e.anchors.get("quarry"));
        var target=ProducerRevisions.plan("quarry",2,e.anchors.get("quarry"));BlockPos conflict=null;
        for(int i=0;i<old.size();i++)if(old.get(i).localY()>30&&old.get(i).state().is(Blocks.AIR)&&target.get(i).state().is(Blocks.AIR)){conflict=old.get(i).position();break;}
        h.assertTrue(conflict!=null,"Missing unchanged-air fixture");final BlockPos p=conflict;
        var before=e.nation().save();var inventories=e.inventories();
        try {
            ProducerTransaction.run(e,APPLY,(phase,n)->{if(phase.equals("prepared"))e.level().setBlock(p,Blocks.CAVE_AIR.defaultBlockState(),2|16|32);});
            h.fail("Post-preflight cave air was silently accepted");
        }catch(IOException expected){
            h.assertTrue(expected.getMessage().contains("Producer post-write mismatch at "+p)
                &&expected.getMessage().contains("expected=Block{minecraft:air}")
                &&expected.getMessage().contains("actual=Block{minecraft:cave_air}"),"Incomplete drift diagnostic: "+expected);
        }
        h.assertTrue(Files.isRegularFile(e.file())&&before.equals(e.nation().save()),"Drift lost history or charged");
        h.assertTrue(e.level().getBlockState(p).is(Blocks.CAVE_AIR),"Guard silently replaced foreign state");
        // Test-only removal of the deliberately injected fault, not a production auto-repair.
        e.level().setBlock(p,Blocks.AIR.defaultBlockState(),2|16|32);e.reload();
        ProducerTransaction.run(e,ProducerTransaction.Action.RESUME);e.assertGeometry(2);
        h.assertTrue(e.nation().treasury()==before.getLong("treasury")-e.spec().cost().money(),"Resume did not charge once");
        for(ResourceType type:ResourceType.values()) {
            var original=com.quin.catancraft.data.NationData.load(before);
            h.assertTrue(e.nation().resource(type)==original.resource(type)-e.spec().cost().resources().getOrDefault(type,0L),"Resume resource charge differs");
        }
        var paid=e.nation().save();h.assertTrue(ProducerTransaction.run(e,APPLY).writes()==0&&paid.equals(e.nation().save()),"Repeat after recovery charged/wrote again");
        h.assertTrue(inventories.equals(e.inventories()),"Drift/recovery changed inventories");h.succeed();
    }

    @GameTest(template="townhall_state_empty",batch="stability_entity",timeoutTicks=600)
    public static void genuine_falling_block_is_not_deleted_or_ignored(GameTestHelper h)throws Exception {
        var e=new ProducerTests.Env(h,0,1);environment(h);e.prepare(1);e.select(1,2);
        BlockPos p=ProducerTests.hazardPosition(e);var before=e.nation().save();var world=e.snapshot();
        FallingBlockEntity actor=FallingBlockEntity.fall(e.level(),p,Blocks.GRAVEL.defaultBlockState());
        try {
            try{ProducerTransaction.run(e,APPLY);h.fail("Falling entity in new construction was accepted");}
            catch(ProducerTransaction.FreshOccupancyRefusal expected){}
            h.assertTrue(actor.isAlive()&&!Files.exists(e.file())&&before.equals(e.nation().save())&&world.equals(e.snapshot()),"Occupancy refusal deleted actor, charged or wrote");
        }finally{actor.discard();}
        h.succeed();
    }

    @GameTest(template="townhall_state_empty",batch="stability_gravity_north",timeoutTicks=2000)
    public static void delivered_gravel_supported_north_all_five_levels(GameTestHelper h){gravity(h,0,1);}
    @GameTest(template="townhall_state_empty",batch="stability_gravity_south",timeoutTicks=2000)
    public static void delivered_gravel_supported_south_all_five_levels(GameTestHelper h){gravity(h,180,1);}
    private static void gravity(GameTestHelper h,int rotation,int tier){
        try {
            var e=new ProducerTests.Env(h,rotation,tier);environment(h);e.prepare(tier);e.select(tier,tier);var inventory=e.inventories();
            for(String family:ProducerRevisions.FAMILIES)for(var c:ProducerRevisions.plan(family,tier,e.anchors.get(family)))
                if(c.state().is(Blocks.GRAVEL))h.assertTrue(e.level().getBlockState(c.position().below()).is(Blocks.DIRT),"Delivered gravel support missing");
            h.runAfterDelay(40,()->{try {
                ProducerTransaction.run(e,STATUS);e.assertGeometry(tier);h.assertTrue(inventory.equals(e.inventories()),"Native gravity fixture changed inventory");
                CatanCraft.LOGGER.info("PRODUCER_GRAVITY_PASS rotation={} tier={}",rotation,tier);
                if(tier==5)h.succeed();else gravity(h,rotation,tier+1);
            }catch(Exception x){x.printStackTrace();h.fail(x.toString());}});
        }catch(Exception x){x.printStackTrace();h.fail(x.toString());}
    }
    private static void environment(GameTestHelper h){
        h.assertTrue(h.getLevel().getServer() instanceof GameTestServer,"Synthetic fixture outside GameTestServer");
        CatanCraft.LOGGER.info("PRODUCER_TEST_ENV generator={} seed={}",h.getLevel().getChunkSource().getGenerator().getClass().getName(),h.getLevel().getSeed());
    }
}
