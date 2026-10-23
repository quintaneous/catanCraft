package com.quin.catancraft.world;

import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Production queue/transaction path using the delivered immutable public/house contracts. */
@GameTestHolder("catancraft_base_lifecycle") @PrefixGameTestTemplate(false)
public final class BaseCityLifecycleTests {
    private static final CityBasePreparation.Feature PUBLIC=CityBasePreparation.Feature.PUBLIC,HOUSES=CityBasePreparation.Feature.HOUSES;
    private static ChunkLifecycleTests.PublicFixture pub(GameTestHelper h,int offset)throws Exception {
        CityBasePreparation.stopped(h.getLevel().getServer());var f=new ChunkLifecycleTests.PublicFixture(h,offset);f.stage();return f;
    }
    private static void await(GameTestHelper h,String key,CityBasePreparation.Feature feature,int ticks,Runnable done) {
        h.runAfterDelay(1,()->{h.assertTrue(ticks<8000,"Base queue did not settle: "+CityBasePreparation.status(h.getLevel().getServer(),key));
            if(CityBasePreparation.fixtureSettled(h.getLevel().getServer(),key,feature))done.run();else await(h,key,feature,ticks+1,done);});
    }
    private static void requireCommitted(GameTestHelper h,ChunkLifecycleTests.PublicFixture f)throws Exception {
        h.assertTrue(StreetscapeTransaction.phase(StreetscapeJournal.read(f.file()))==StreetscapeTransaction.Phase.COMMITTED,"Public journal incomplete");
        for(var c:f.plan.cells())h.assertTrue(StreetscapeR5.matches(f.level().getBlockState(c.pos()),c.after()),"Path missing "+c.pos());
        h.assertTrue(f.ledger().getInt("resources")==1234,"Preparation charged");
    }
    @GameTest(template="townhall_state_empty",batch="base01",timeoutTicks=10000)
    public static void temporary_entity_collision_retries_without_restart_or_command(GameTestHelper h)throws Exception {
        var f=pub(h,30000);var c=f.plan.cells().stream().filter(v->v.before().isAir()&&!v.after().getCollisionShape(f.level(),v.pos()).isEmpty()).findFirst().orElseThrow();
        ItemEntity item=new ItemEntity(f.level(),c.pos().getX()+0.5,c.pos().getY()+0.1,c.pos().getZ()+0.5,new ItemStack(Items.COBBLESTONE));item.setNoGravity(true);item.setDeltaMovement(0,0,0);item.setNeverPickUp();f.level().addFreshEntity(item);
        CityBasePreparation.queuePublicFixture("entity",f);waitForObstruction(h,f,item,0);
    }
    private static void waitForObstruction(GameTestHelper h,ChunkLifecycleTests.PublicFixture f,ItemEntity item,int ticks) {
        h.runAfterDelay(1,()->{
            h.assertTrue(ticks<3000,"No precise obstruction wait");String reason=CityBasePreparation.reason(h.getLevel().getServer(),"entity");
            if(!reason.contains("waiting:")){waitForObstruction(h,f,item,ticks+1);return;}
            h.assertTrue(!Files.exists(f.file())&&!item.isRemoved(),"Waiting path mutated journal or removed entity");
            item.discard(); // Explicit test-fixture clearance; production never moves/removes occupants.
            await(h,"entity",PUBLIC,0,()->{try{requireCommitted(h,f);f.finish();h.succeed();}catch(Exception e){throw new RuntimeException(e);}});
        });
    }
    @GameTest(template="townhall_state_empty",batch="base02",timeoutTicks=10000)
    public static void interrupted_public_journal_is_resumed_not_skipped(GameTestHelper h)throws Exception {
        var f=pub(h,32000);try{StreetscapeTransaction.run(f,StreetscapeTransaction.Action.APPLY,(stage,n)->{if(stage.equals("write")&&n==17)throw new IOException("injected interruption");});}catch(IOException expected){}
        h.assertTrue(Files.exists(f.file()),"No retained interruption");var id=StreetscapeJournal.read(f.file()).getUUID("id");
        CityBasePreparation.queuePublicFixture("resume",f);await(h,"resume",PUBLIC,0,()->{try{requireCommitted(h,f);h.assertTrue(id.equals(StreetscapeJournal.read(f.file()).getUUID("id")),"Invented replacement journal");f.finish();h.succeed();}catch(Exception e){throw new RuntimeException(e);}});
    }
    @GameTest(template="townhall_state_empty",batch="base03",timeoutTicks=10000)
    public static void intentional_public_rollback_is_not_reapplied(GameTestHelper h)throws Exception {
        var f=pub(h,34000);StreetscapeTransaction.run(f,StreetscapeTransaction.Action.APPLY);StreetscapeTransaction.run(f,StreetscapeTransaction.Action.ROLLBACK);byte[] before=Files.readAllBytes(f.file());
        CityBasePreparation.queuePublicFixture("rollback",f);await(h,"rollback",PUBLIC,0,()->{try{
            h.assertTrue(CityBasePreparation.reason(h.getLevel().getServer(),"rollback").contains("intentionally"),"Rollback marked ready");
            h.assertTrue(Arrays.equals(before,Files.readAllBytes(f.file())),"Rollback history overwritten");
            for(var c:f.plan.cells())h.assertTrue(StreetscapeR5.matches(f.level().getBlockState(c.pos()),c.before()),"Rollback replayed");f.finish();h.succeed();
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    @GameTest(template="townhall_state_empty",batch="base04",timeoutTicks=10000)
    public static void corrupt_public_history_is_blocked_not_ready(GameTestHelper h)throws Exception {
        var f=pub(h,36000);Files.writeString(f.file(),"not a journal");byte[] before=Files.readAllBytes(f.file());
        CityBasePreparation.queuePublicFixture("corrupt",f);await(h,"corrupt",PUBLIC,0,()->{try{
            h.assertTrue(CityBasePreparation.reason(h.getLevel().getServer(),"corrupt").contains("blocked"),"Corrupt receipt treated as complete");
            h.assertTrue(Arrays.equals(before,Files.readAllBytes(f.file())),"Corrupt history replaced");f.finish();h.succeed();
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    @GameTest(template="townhall_state_empty",batch="base05",timeoutTicks=10000)
    public static void committed_public_verification_does_not_rewrite_receipt(GameTestHelper h)throws Exception {
        var f=pub(h,38000);StreetscapeTransaction.run(f,StreetscapeTransaction.Action.APPLY);byte[] before=Files.readAllBytes(f.file());
        CityBasePreparation.queuePublicFixture("committed",f);await(h,"committed",PUBLIC,0,()->{try{requireCommitted(h,f);h.assertTrue(Arrays.equals(before,Files.readAllBytes(f.file())),"Committed receipt rewritten");f.finish();h.succeed();}catch(Exception e){throw new RuntimeException(e);}});
    }
    @GameTest(template="townhall_state_empty",batch="base06",timeoutTicks=10000)
    public static void six_houses_initialize_without_maintenance_command(GameTestHelper h)throws Exception {
        CityBasePreparation.stopped(h.getLevel().getServer());var f=new R5HouseTests.Env(h,0);f.prepare();byte[] book=f.externalBook();CompoundTag ledger=f.ledger();
        CityBasePreparation.queueHouseFixture("houses",f);await(h,"houses",HOUSES,0,()->{try{
            f.assertTarget();h.assertTrue(R5HouseTransaction.phase(ProducerJournal.read(f.file()))==R5HouseTransaction.Phase.COMMITTED,"House initialization incomplete");
            h.assertTrue(Arrays.equals(book,f.externalBook())&&ledger.equals(f.ledger()),"House preparation changed book or ledger");h.succeed();
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    @GameTest(template="townhall_state_empty",batch="base07",timeoutTicks=10000)
    public static void interrupted_house_operation_resumes_original_history(GameTestHelper h)throws Exception {
        CityBasePreparation.stopped(h.getLevel().getServer());var f=new R5HouseTests.Env(h,180);f.prepare();
        try{R5HouseTransaction.run(f,R5HouseTransaction.Action.APPLY,(stage,n)->{if(stage.equals("write")&&n==17)throw new IOException("injected interruption");});}catch(IOException expected){}
        var id=ProducerJournal.read(f.file()).getUUID("id");CityBasePreparation.queueHouseFixture("houses-resume",f);
        await(h,"houses-resume",HOUSES,0,()->{try{f.assertTarget();h.assertTrue(id.equals(ProducerJournal.read(f.file()).getUUID("id")),"House history replaced");h.succeed();}catch(Exception e){throw new RuntimeException(e);}});
    }
    @GameTest(template="townhall_state_empty",batch="base08",timeoutTicks=10000)
    public static void intentionally_rolled_back_houses_stay_rolled_back(GameTestHelper h)throws Exception {
        CityBasePreparation.stopped(h.getLevel().getServer());var f=new R5HouseTests.Env(h,0);f.prepare();R5HouseTransaction.run(f,R5HouseTransaction.Action.APPLY);R5HouseTransaction.run(f,R5HouseTransaction.Action.ROLLBACK);byte[] receipt=Files.readAllBytes(f.file());
        CityBasePreparation.queueHouseFixture("houses-rollback",f);await(h,"houses-rollback",HOUSES,0,()->{try{f.assertSource();h.assertTrue(Arrays.equals(receipt,Files.readAllBytes(f.file())),"House rollback receipt replaced");h.assertTrue(CityBasePreparation.status(h.getLevel().getServer(),"houses-rollback").contains("intentionally"),"House rollback not visible");h.succeed();}catch(Exception e){throw new RuntimeException(e);}});
    }
}
