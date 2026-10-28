package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.*;
import com.quin.catancraft.economy.*;
import com.quin.catancraft.map.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Actual pinned designs and the production purchase adapter; isolated data, never an owner world. */
@GameTestHolder("catancraft_construction34") @PrefixGameTestTemplate(false)
public final class ConstructionSafetyTests {
    static final int FLAGS=PaidConstructionRecovery.FLAGS;
    @FunctionalInterface interface Work {void run(F f)throws Exception;}
    static final class F {
        final GameTestHelper h;final ServerLevel level;final Path dir,ledger;final UUID actor=UUID.randomUUID(),owner;
        final BasicConstructionPurchase.Request request;final BasicConstructionPurchase.Blueprint plan;
        CatanSavedData data=new CatanSavedData();boolean failSave;
        F(GameTestHelper h,String city,String asset,String plot,int from)throws Exception {
            this.h=h;level=h.getLevel();if(!(level.getServer() instanceof GameTestServer))throw new IOException("Native fixture only");
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
            dir=Files.createTempDirectory(level.getServer().getWorldPath(LevelResource.ROOT),"basic34-").toRealPath();Files.createDirectories(dir.resolve("data"));ledger=dir.resolve("ledger.nbt");
            var n=data.createNation("Construction fixture",actor);owner=n.id();n.setTreasury(10000000);for(ResourceType type:ResourceType.values())n.addResource(type,1000000);
            var t=data.createTerritory(city,"Fixture",ResourceType.WOOD);t.setOwnerNationId(owner);boolean hall=plot.equals("town_hall");t.setCityLevel(hall?from:5);
            request=new BasicConstructionPurchase.Request(city,actor,owner,hall?"hall":"factory",asset,plot,from,hall?from+1:1);
            if(hall)data.setPlacedMapAsset(BasicConstructionPurchase.stateKey(request),SchematicAssetRegistry.asset("th"+from).placementToken());
            plan=BasicConstructionPurchase.blueprint(request,MapDefinitionManager.territory(city));save();
        }
        NationData nation(){return data.nation(owner);}TerritoryData territory(){return data.territory(request.city());}
        Path original(){return BasicConstructionPurchase.file(dir,request);}
        void save()throws IOException{if(failSave)throw new IOException("injected-save-failure");NbtIo.writeCompressed(data.save(new CompoundTag()),ledger.toFile());}
        void reload()throws IOException{data=CatanSavedData.load(NbtIo.readCompressed(ledger.toFile()));}
        void stage()throws Exception {
            Set<BlockPos> inside=new HashSet<>();for(var c:plan.source())inside.add(c.position());
            Set<BlockPos> shell=new HashSet<>();for(var c:plan.source())for(var side:net.minecraft.core.Direction.values()){var p=c.position().relative(side);if(!inside.contains(p))shell.add(p);}
            for(var p:shell)clear(p,Blocks.AIR.defaultBlockState());for(var c:plan.source())clear(c.position(),c.state());
            if(plan.manager()!=null){var book=new ItemStack(Items.WRITTEN_BOOK);book.getOrCreateTag().putString("title","Actual owner book");book.getOrCreateTag().putString("author","fixture");book.getOrCreateTag().putUUID("owner",owner);((LecternBlockEntity)level.getBlockEntity(plan.manager())).setBook(book);}
        }
        void clear(BlockPos p,BlockState s){var be=level.getBlockEntity(p);if(be instanceof Container c)c.clearContent();if(be instanceof LecternBlockEntity l)l.setBook(ItemStack.EMPTY);level.setBlock(p,s,FLAGS);}
        void prepare()throws IOException{BasicConstructionPurchase.fixturePrepare(level.getServer(),data,dir,request,this::save);}
        PaidConstructionRecovery.Environment env()throws IOException{return BasicConstructionPurchase.fixture(level.getServer(),data,dir,original(),this::save);}
        void complete()throws Exception{PaidConstructionRecovery.resume(env());}
        BlockPos changed() throws IOException {for(int i=0;i<plan.source().size();i++)if(!plan.source().get(i).state().equals(plan.target().get(i).state())&&!plan.source().get(i).position().equals(plan.manager()))return plan.source().get(i).position();throw new IOException("No changes");}
        void target()throws Exception{for(var c:plan.target())h.assertTrue(TownHallInteractionPolicy.matches(level.getBlockState(c.position()),c.state(),c.position().equals(plan.manager())),"Target mismatch "+c.position());}
    }
    static void with(GameTestHelper h,String city,String asset,String plot,int from,Work work)throws Exception {
        F f=new F(h,city,asset,plot,from);PaidRecoveryTests.ready(h,BasicConstructionPurchase.bounds(f.request,MapDefinitionManager.territory(city)),()->{f.stage();work.run(f);});
    }
    static void hall(GameTestHelper h,String city,int from)throws Exception {
        with(h,city,"th"+(from+1),"town_hall",from,f->{
            // Normal interactive state is allowed; do not ask players to close every door.
            for(var c:f.plan.source())if(c.state().getBlock() instanceof DoorBlock)f.level.setBlock(c.position(),c.state().setValue(DoorBlock.OPEN,true),FLAGS);
            CompoundTag book=f.level.getBlockEntity(f.plan.manager()).saveWithFullMetadata().copy();long cash=f.nation().treasury();var cost=BasicConstructionPurchase.price(f.request);
            f.prepare();byte[] original=Files.readAllBytes(f.original());PaidRecoveryTests.interrupt(h,f.env(),"write",37);f.reload();f.complete();f.target();
            h.assertTrue(f.nation().treasury()==cash-cost.money()&&f.territory().cityLevel()==from+1,"Hall debit/tier mismatch");
            h.assertTrue(book.equals(f.level.getBlockEntity(f.plan.manager()).saveWithFullMetadata()),"Actual book NBT changed");
            f.reload();h.assertTrue(PaidConstructionRecovery.resume(f.env()).writes()==0,"Hall duplicate writes");h.assertTrue(f.nation().treasury()==cash-cost.money(),"Hall duplicate charge");
            h.assertTrue(Arrays.equals(original,Files.readAllBytes(f.original())),"Original purchase history rewritten");h.succeed();
        });
    }
    @GameTest(template="townhall_state_empty",batch="basic01",timeoutTicks=10000)
    public static void th1_to_th2_north_book_partial_restart_receipt(GameTestHelper h)throws Exception{hall(h,"a",1);}
    @GameTest(template="townhall_state_empty",batch="basic02",timeoutTicks=10000)
    public static void th2_to_th3_north_book_partial_restart_receipt(GameTestHelper h)throws Exception{hall(h,"a",2);}
    @GameTest(template="townhall_state_empty",batch="basic03",timeoutTicks=10000)
    public static void th1_to_th2_south_book_partial_restart_receipt(GameTestHelper h)throws Exception{hall(h,"i",1);}
    @GameTest(template="townhall_state_empty",batch="basic04",timeoutTicks=10000)
    public static void th2_to_th3_south_book_partial_restart_receipt(GameTestHelper h)throws Exception{hall(h,"i",2);}

    static void factories(GameTestHelper h,String city)throws Exception {
        var def=MapDefinitionManager.territory(city);var region=def.restorationRegion();AABB box=new AABB(region.minX(),region.minY(),region.minZ(),region.maxX()+1,region.maxY()+1,region.maxZ()+1).inflate(2);
        PaidRecoveryTests.ready(h,box,()->{
            int count=0;for(BuildingType type:IndustrialPlotService.INDUSTRIES){String slot=IndustrialPlotService.INDUSTRIAL_PLOTS.get(count%3);F f=new F(h,city,SchematicAssetRegistry.buildingAsset(type),slot,0);f.stage();long cash=f.nation().treasury();var before=f.nation().save();var cost=BasicConstructionPurchase.price(f.request);
                f.prepare();byte[] original=Files.readAllBytes(f.original());PaidRecoveryTests.interrupt(h,f.env(),"write",17);f.reload();f.complete();f.target();
                h.assertTrue(f.territory().buildings().size()==1&&f.territory().buildings().get(0).type()==type&&f.territory().buildings().get(0).plotId().equals(slot),"Factory purchase state mismatch");
                h.assertTrue(f.nation().treasury()==cash-cost.money(),"Factory price not exactly once");
                var old=NationData.load(before);for(ResourceType r:ResourceType.values())h.assertTrue(f.nation().resource(r)==old.resource(r)-cost.resources().getOrDefault(r,0L),"Factory resources not exactly once");
                f.reload();h.assertTrue(PaidConstructionRecovery.resume(f.env()).writes()==0,"Factory duplicate writes");h.assertTrue(f.nation().treasury()==cash-cost.money(),"Factory repeat debit");h.assertTrue(Arrays.equals(original,Files.readAllBytes(f.original())),"Factory original rewritten");count++;
            }h.assertTrue(count==9,"Factory coverage");h.succeed();
        });
    }
    @GameTest(template="townhall_state_empty",batch="basic05",timeoutTicks=16000)
    public static void all_nine_factories_north_slots_partial_recovery(GameTestHelper h)throws Exception{factories(h,"a");}
    @GameTest(template="townhall_state_empty",batch="basic06",timeoutTicks=16000)
    public static void all_nine_factories_south_slots_partial_recovery(GameTestHelper h)throws Exception{factories(h,"i");}
    @GameTest(template="townhall_state_empty",batch="basic07",timeoutTicks=10000)
    public static void full_context_conflict_and_foreign_inventory_no_intent_no_charge(GameTestHelper h)throws Exception {
        with(h,"a","th2","town_hall",1,f->{var ledger=f.data.save(new CompoundTag());var last=f.plan.source().get(f.plan.source().size()-1);f.level.setBlock(last.position(),Blocks.DIAMOND_BLOCK.defaultBlockState(),FLAGS);
            PaidRecoveryTests.refused(h,f::prepare,"source conflict");h.assertTrue(!Files.exists(f.original())&&ledger.equals(f.data.save(new CompoundTag())),"Conflict created history/debit");f.level.setBlock(last.position(),last.state(),FLAGS);
            var p=f.changed();var prior=f.level.getBlockState(p);f.level.setBlock(p,Blocks.CHEST.defaultBlockState(),FLAGS);((ChestBlockEntity)f.level.getBlockEntity(p)).setItem(0,new ItemStack(Items.DIAMOND,7));CompoundTag inventory=f.level.getBlockEntity(p).saveWithFullMetadata().copy();
            PaidRecoveryTests.refused(h,f::prepare,"source conflict");h.assertTrue(inventory.equals(f.level.getBlockEntity(p).saveWithFullMetadata())&&!Files.exists(f.original()),"Foreign inventory changed");h.succeed();});
    }
    @GameTest(template="townhall_state_empty",batch="basic08",timeoutTicks=10000)
    public static void initial_save_failure_leaves_no_intent_or_blocks(GameTestHelper h)throws Exception {
        with(h,"a","steel_mill","plot_1",0,f->{CompoundTag before=f.data.save(new CompoundTag());BlockPos p=f.changed();var state=f.level.getBlockState(p);f.failSave=true;PaidRecoveryTests.refused(h,f::prepare,"injected-save-failure");h.assertTrue(!Files.exists(f.original())&&before.equals(f.data.save(new CompoundTag()))&&state.equals(f.level.getBlockState(p)),"Failed save left purchase work");h.succeed();});
    }
    @GameTest(template="townhall_state_empty",batch="basic09",timeoutTicks=10000)
    public static void physical_save_failure_retains_source_and_settles_once(GameTestHelper h)throws Exception {
        with(h,"a","textile_mill","plot_3",0,f->{long cash=f.nation().treasury();f.prepare();f.failSave=true;PaidRecoveryTests.refused(h,f::complete,"injected-save-failure");h.assertTrue(f.nation().treasury()==cash&&f.territory().buildings().isEmpty(),"Failed physical save charged");h.assertTrue(BasicConstructionPurchase.unfinished(f.original()),"Failed physical save called complete");f.failSave=false;f.complete();h.assertTrue(f.nation().treasury()==cash-BasicConstructionPurchase.price(f.request).money(),"Resume wrong price");h.succeed();});
    }
    @GameTest(template="townhall_state_empty",batch="basic10",timeoutTicks=10000)
    public static void saved_charge_original_receipt_not_repeated(GameTestHelper h)throws Exception {
        with(h,"a","vehicle_factory","plot_4",0,f->{long cash=f.nation().treasury();f.prepare();PaidRecoveryTests.interrupt(h,f.env(),"ledger_saved",-1);f.reload();f.nation().addTreasury(125);f.save();f.complete();h.assertTrue(f.nation().treasury()==cash-BasicConstructionPurchase.price(f.request).money()+125,"Receipt or unrelated income lost");h.succeed();});
    }
    @GameTest(template="townhall_state_empty",batch="basic11",timeoutTicks=10000)
    public static void altered_before_image_and_owner_are_refused(GameTestHelper h)throws Exception {
        with(h,"a","th2","town_hall",1,f->{f.prepare();byte[] original=Files.readAllBytes(f.original());CompoundTag n=ProducerJournal.read(original);n.getCompound("snapshot").putIntArray("states",new int[]{0});ProducerJournal.write(f.original(),n,true);PaidRecoveryTests.refused(h,f::complete,"before-image");h.assertTrue(!Files.exists(PaidConstructionRecovery.companion(f.original())),"Bad snapshot began writes");Files.write(f.original(),original);f.territory().setOwnerNationId(UUID.randomUUID());PaidRecoveryTests.refused(h,f::complete,"ownership");h.succeed();});
    }
    @GameTest(template="townhall_state_empty",batch="basic12",timeoutTicks=10000)
    public static void occupied_mutation_refuses_without_removing_item(GameTestHelper h)throws Exception {
        with(h,"a","steel_mill","plot_1",0,f->{BlockPos pos=null;for(int i=0;i<f.plan.source().size();i++){var a=f.plan.source().get(i);var b=f.plan.target().get(i);if(a.state().isAir()&&b.state().is(Blocks.STONE_BRICKS)){pos=a.position();break;}}if(pos==null)pos=f.changed();
            var item=new ItemEntity(f.level,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,new ItemStack(Items.STICK));item.setNoGravity(true);f.level.addFreshEntity(item);
            var held=SectorChunkLease.acquire(f.level,BasicConstructionPurchase.bounds(f.request,MapDefinitionManager.territory("a")));
            h.runAfterDelay(3,()->{try{held.keepAlive();PaidRecoveryTests.refused(h,f::prepare,"Construction hazard:");h.assertTrue(!item.isRemoved()&&!Files.exists(f.original()),"Occupancy removed item or created intent");item.discard();f.prepare();f.complete();h.succeed();}catch(Exception e){throw new RuntimeException(e);}finally{item.discard();held.close();}});
        });
    }
    @GameTest(template="townhall_state_empty",batch="basic13",timeoutTicks=10000)
    public static void missing_ordinary_doors_supported_book_not_recreated(GameTestHelper h)throws Exception {
        with(h,"a","th2","town_hall",1,f->{for(var c:f.plan.source())if(c.state().getBlock() instanceof DoorBlock)f.level.setBlock(c.position(),Blocks.AIR.defaultBlockState(),FLAGS);var book=f.level.getBlockEntity(f.plan.manager()).saveWithFullMetadata().copy();f.prepare();f.complete();h.assertTrue(book.equals(f.level.getBlockEntity(f.plan.manager()).saveWithFullMetadata()),"Manager book changed");f.target();h.succeed();});
    }
    @GameTest(template="townhall_state_empty",batch="basic14",timeoutTicks=10000)
    public static void unaffordable_source_and_duplicate_slot_no_intent(GameTestHelper h)throws Exception {
        with(h,"a","steel_mill","plot_1",0,f->{long money=f.nation().treasury();f.nation().setTreasury(0);PaidRecoveryTests.refused(h,f::prepare,"afford");h.assertTrue(!Files.exists(f.original()),"Unaffordable intent");f.nation().setTreasury(money);f.territory().buildings().add(new BuildingInstance(BuildingType.REFINERY,1,100,"plot_1"));PaidRecoveryTests.refused(h,f::prepare,"occupied");h.assertTrue(!Files.exists(f.original()),"Occupied intent");h.succeed();});
    }
    @GameTest(template="townhall_state_empty",batch="basic15",timeoutTicks=1500)
    public static void all_factory_anchors_and_hall_manager_match_pinned_bounds(GameTestHelper h)throws Exception {
        int count=0;for(String city:List.of("a","i")){var def=MapDefinitionManager.territory(city);for(var type:IndustrialPlotService.INDUSTRIES)for(String plot:IndustrialPlotService.INDUSTRIAL_PLOTS){var r=new BasicConstructionPurchase.Request(city,UUID.randomUUID(),UUID.randomUUID(),"factory",SchematicAssetRegistry.buildingAsset(type),plot,0,1);var b=BasicConstructionPurchase.blueprint(r,def);h.assertTrue(b.source().size()==45167,"Factory context size");for(var cell:b.target())h.assertTrue(def.restorationRegion().contains(cell.position())&&def.siegeRegion().contains(cell.position()),"Factory leaves city bounds");count++;}}
        h.assertTrue(count==54,"Factory mapping count");h.succeed();
    }
    @GameTest(template="townhall_state_empty",batch="basic16",timeoutTicks=500)
    public static void old_direct_paid_paste_entrypoints_refuse(GameTestHelper h)throws Exception {
        var data=new CatanSavedData();var d=MapDefinitionManager.territory("a");h.assertTrue(!MapAssetService.upgradeTownHall(h.getLevel().getServer(),data,d,2).success(),"Unguarded Hall paste available");h.assertTrue(!MapAssetService.placeBuilding(h.getLevel().getServer(),data,d,BuildingType.STEEL_MILL,"plot_1").success(),"Unguarded factory paste available");h.succeed();
    }

    static void awaitQueue(GameTestHelper h,Path path,AtomicInteger attempts,int expected,int ticks,Runnable finish){h.runAfterDelay(1,()->{
        h.assertTrue(ticks<1000,"Recovery queue did not settle");if(PaidRecoveryService.settledFixture(h.getLevel().getServer(),path)){h.assertTrue(attempts.get()==expected,"Unexpected retry count "+attempts);finish.run();PaidRecoveryService.stopped(h.getLevel().getServer());h.succeed();}
        else awaitQueue(h,path,attempts,expected,ticks+1,finish);
    });}
    @GameTest(template="townhall_state_empty",batch="queue01",timeoutTicks=1800)
    public static void typed_loading_and_occupancy_retry_then_complete(GameTestHelper h)throws Exception {
        var s=h.getLevel().getServer();PaidRecoveryService.stopped(s);var p=s.getWorldPath(LevelResource.ROOT).resolve("data/queue34-"+UUID.randomUUID());AtomicInteger attempts=new AtomicInteger();var pos=h.absolutePos(new BlockPos(4,2,4));
        PaidRecoveryService.queueFixture(s,p,"fixture",new AABB(pos).inflate(5),()->{int n=attempts.incrementAndGet();if(n<3)throw new PreparationDeferred(n==1?"test loading":"test occupancy");return 0;});awaitQueue(h,p,attempts,3,0,()->h.assertTrue(PaidRecoveryService.status(s,"fixture").isEmpty(),"Completed retry retained error"));
    }
    @GameTest(template="townhall_state_empty",batch="queue02",timeoutTicks=1800)
    public static void hard_conflict_not_retried_as_temporary(GameTestHelper h)throws Exception {
        var s=h.getLevel().getServer();PaidRecoveryService.stopped(s);var p=s.getWorldPath(LevelResource.ROOT).resolve("data/queue34-"+UUID.randomUUID());AtomicInteger attempts=new AtomicInteger();var pos=h.absolutePos(new BlockPos(4,2,4));
        PaidRecoveryService.queueFixture(s,p,"fixture",new AABB(pos).inflate(5),()->{attempts.incrementAndGet();throw new IOException("unexpected foreign block");});awaitQueue(h,p,attempts,1,0,()->h.assertTrue(PaidRecoveryService.status(s,"fixture").contains("blocked"),"Hard conflict hidden"));
    }
    @GameTest(template="townhall_state_empty",batch="queue03",timeoutTicks=1800)
    public static void stopped_queue_drops_no_paid_or_physical_state(GameTestHelper h)throws Exception {
        var s=h.getLevel().getServer();PaidRecoveryService.stopped(s);var p=s.getWorldPath(LevelResource.ROOT).resolve("data/queue34-"+UUID.randomUUID());AtomicInteger calls=new AtomicInteger();var pos=h.absolutePos(new BlockPos(4,2,4));
        PaidRecoveryService.queueFixture(s,p,"fixture",new AABB(pos).inflate(5),()->{calls.incrementAndGet();return 0;});PaidRecoveryService.stopped(s);
        h.runAfterDelay(120,()->{h.assertTrue(calls.get()==0&&!PaidRecoveryService.isRunning(s),"Stopped queue executed work");h.succeed();});
    }
    @GameTest(template="townhall_state_empty",batch="queue04",timeoutTicks=6000)
    public static void producer_partial_resume_uses_common_retry_queue(GameTestHelper h)throws Exception {
        var s=h.getLevel().getServer();PaidRecoveryService.stopped(s);ProducerTests.Env e=new ProducerTests.Env(h,0,1);e.prepare(1);e.select(1,2);long cash=e.nation().treasury();
        PaidRecoveryTests.refused(h,()->ProducerTransaction.run(e,ProducerTransaction.Action.APPLY,(stage,n)->{if(stage.equals("write")&&n==7)throw new IOException("native-interruption");}),"native-interruption");
        e.reload();AtomicInteger attempts=new AtomicInteger();Path path=e.file();AABB bounds=null;for(var plot:e.spec().plots()){var b=TownHallRepairTransaction.bounds(ProducerRevisions.plan(plot.family(),plot.source(),plot.anchor())).inflate(2);bounds=bounds==null?b:bounds.minmax(b);}
        PaidRecoveryService.queueFixture(s,path,"fixture",bounds,()->{if(attempts.incrementAndGet()==1)throw new PreparationDeferred("test residency delay");return ProducerTransaction.run(e,ProducerTransaction.Action.RESUME).writes();});
        awaitQueue(h,path,attempts,2,0,()->{try{e.assertGeometry(2);h.assertTrue(e.nation().treasury()==cash-EconomyCatalog.producerUpgradeCost(1).money(),"Producer retry charge");}catch(Exception x){throw new RuntimeException(x);}});
    }
    @GameTest(template="townhall_state_empty",batch="queue05",timeoutTicks=6000)
    public static void legacy_defense_partial_rollback_retries_and_refunds_once(GameTestHelper h)throws Exception {
        var server=h.getLevel().getServer();PaidRecoveryService.stopped(server);
        var f=new DefenseTests.Fixture(h.getLevel(),"a","front_w_04");AABB bounds=null;
        for(var c:f.plan.cells()){AABB b=new AABB(c.pos()).inflate(2);bounds=bounds==null?b:bounds.minmax(b);}for(var c:f.plan.reads())bounds=bounds.minmax(new AABB(c.pos()).inflate(2));
        final AABB area=bounds;PaidRecoveryTests.ready(h,area,()->{
            f.stage();long cash=f.nation.treasury();DefenseTransaction.run(f,DefenseTransaction.Action.APPLY);
            decayDefenseGrass(h,f);
            PaidRecoveryTests.refused(h,()->DefenseTransaction.run(f,DefenseTransaction.Action.ROLLBACK,(phase,n)->{if(phase.equals("rollback_write")&&n==3)throw new IOException("rollback-interrupted");}),"rollback-interrupted");
            AtomicInteger attempts=new AtomicInteger();PaidRecoveryService.queueFixture(server,f.file,"fixture",area,()->{if(attempts.incrementAndGet()==1)throw new PreparationDeferred("test loading");return DefenseTransaction.run(f,DefenseTransaction.Action.ROLLBACK).writes();});
            awaitQueue(h,f.file,attempts,2,0,()->{try(var verificationLease=SectorChunkLease.acquire(h.getLevel(),area)){h.assertTrue(f.nation.treasury()==cash&&f.nation.resource(ResourceType.STONE)==100,"Legacy refund not once");h.assertTrue(DefenseTransaction.run(f,DefenseTransaction.Action.ROLLBACK).writes()==0&&f.nation.treasury()==cash,"Repeat refund");}catch(Exception ex){throw new RuntimeException(ex);}});
        });
    }
    @GameTest(template="townhall_state_empty",batch="basic17",timeoutTicks=10000)
    public static void real_book_used_after_partial_hall_recovery_is_preserved(GameTestHelper h)throws Exception {
        with(h,"a","th3","town_hall",2,f->{f.prepare();PaidRecoveryTests.interrupt(h,f.env(),"write",23);
            var book=new ItemStack(Items.WRITTEN_BOOK);book.getOrCreateTag().putString("title","New actual book during interruption");book.getOrCreateTag().putString("author","owner");book.getOrCreateTag().putInt("customField",924);
            ((LecternBlockEntity)f.level.getBlockEntity(f.plan.manager())).setBook(book);var nbt=f.level.getBlockEntity(f.plan.manager()).saveWithFullMetadata().copy();
            f.complete();h.assertTrue(nbt.equals(f.level.getBlockEntity(f.plan.manager()).saveWithFullMetadata()),"Recovery restored stale manager snapshot");h.succeed();});
    }
    @GameTest(template="townhall_state_empty",batch="basic18",timeoutTicks=12000)
    public static void normal_target_doors_after_physical_interruption_resume(GameTestHelper h)throws Exception {
        with(h,"a","th3","town_hall",2,f->{f.prepare();PaidRecoveryTests.interrupt(h,f.env(),"physical_saved",-1);
            for(var c:f.plan.target())if(c.state().getBlock() instanceof DoorBlock)f.level.setBlock(c.position(),c.state().setValue(DoorBlock.OPEN,true),FLAGS);
            f.reload();f.complete();f.target();h.succeed();});
    }

    @GameTest(template="townhall_state_empty",batch="queue06",timeoutTicks=6000)
    public static void legacy_prepared_defense_waits_for_original_price_without_writes(GameTestHelper h)throws Exception {
        var server=h.getLevel().getServer();PaidRecoveryService.stopped(server);
        var f=new DefenseTests.Fixture(h.getLevel(),"a","front_w_04");AABB bounds=null;
        for(var c:f.plan.cells()){AABB b=new AABB(c.pos()).inflate(2);bounds=bounds==null?b:bounds.minmax(b);}for(var c:f.plan.reads())bounds=bounds.minmax(new AABB(c.pos()).inflate(2));
        final AABB area=bounds;PaidRecoveryTests.ready(h,area,()->{
            f.stage();long cash=f.nation.treasury();
            PaidRecoveryTests.refused(h,()->DefenseTransaction.run(f,DefenseTransaction.Action.APPLY,(phase,n)->{if(phase.equals("prepared"))throw new IOException("prepared-interrupted");}),"prepared-interrupted");
            byte[] journal=Files.readAllBytes(f.file);f.nation.setTreasury(0);
            try{DefenseTransaction.run(f,DefenseTransaction.Action.RESUME);throw new IOException("Expected deferred original-price wait");}
            catch(PreparationDeferred expected){h.assertTrue(expected.getMessage().contains("Original defense price"),"Missing original-price reason");}
            h.assertTrue(Arrays.equals(journal,Files.readAllBytes(f.file))&&f.nation.treasury()==0,"Deferred reservation changed history or balance");
            for(var c:f.plan.cells())h.assertTrue(f.level().getBlockState(c.pos()).equals(c.before()),"Price wait changed world");
            f.nation.setTreasury(cash);DefenseTransaction.run(f,DefenseTransaction.Action.RESUME);
            long paid=f.nation.treasury();h.assertTrue(paid==cash-f.cost().money(),"Original price not settled once");
            h.assertTrue(DefenseTransaction.run(f,DefenseTransaction.Action.RESUME).writes()==0&&f.nation.treasury()==paid,"Repeated payment");h.succeed();
        });
    }

    @GameTest(template="townhall_state_empty",batch="palette34")
    public static void delivered_palette_defaults_and_invalid_properties(GameTestHelper h)throws Exception {
        h.assertTrue(BasicConstructionAssets.parsePalette("minecraft:grass_block").equals(Blocks.GRASS_BLOCK.defaultBlockState()),"Omitted defaults not resolved");
        var stairs=BasicConstructionAssets.parsePalette("minecraft:oak_stairs[facing=south,half=top]");
        h.assertTrue(stairs.getValue(StairBlock.FACING)==net.minecraft.core.Direction.SOUTH&&stairs.getValue(StairBlock.HALF)==net.minecraft.world.level.block.state.properties.Half.TOP&&!stairs.getValue(StairBlock.WATERLOGGED),"Partial property defaults changed");
        for(String invalid:List.of("minecraft:not_a_real_block","minecraft:grass_block[bogus=true]","minecraft:grass_block[snowy=invalid]","minecraft:grass_block[snowy=true,snowy=false]","minecraft:grass_block[snowy=true,]","minecraft:grass_block[snowy=true","minecraft:grass_block]")){
            boolean refused=false;try{BasicConstructionAssets.parsePalette(invalid);}catch(IOException expected){refused=true;}
            h.assertTrue(refused,"Invalid palette accepted: "+invalid);
        }
        String[] ids={"starter_settlement","th1","th2","th3","vehicle_factory","weapons_factory","steel_mill","textile_mill","fuel_refinery","concrete_plant","machine_shop","chemical_plant","electronics_factory"};
        for(String id:ids){var v=BasicConstructionAssets.load(id);h.assertTrue(v.states().length==v.width()*v.height()*v.length(),"Incomplete decoded asset: "+id);}
        h.succeed();
    }

    static BlockPos decayDefenseGrass(GameTestHelper h,DefenseTests.Fixture f)throws Exception {
        for(var r:f.plan.reads())if(r.state().is(Blocks.GRASS_BLOCK)&&SectorNaturalSupportPolicy.blocksGrass(f.level.getBlockState(r.pos().above()),r.pos().above())){
            f.level.getChunkAt(r.pos());Blocks.GRASS_BLOCK.randomTick(f.level.getBlockState(r.pos()),f.level,r.pos(),net.minecraft.util.RandomSource.create(7));
            h.assertTrue(f.level.getBlockState(r.pos()).is(Blocks.DIRT),"Native covered grass did not decay");return r.pos();
        }throw new IOException("No native covered support in delivered socket");
    }
    static AABB defenseBounds(DefenseTests.Fixture f){AABB box=null;for(var r:f.plan.reads())box=box==null?new AABB(r.pos()).inflate(4):box.minmax(new AABB(r.pos()).inflate(4));for(var c:f.plan.cells())box=box.minmax(new AABB(c.pos()).inflate(4));return box;}
    @GameTest(template="townhall_state_empty",batch="natural34a",timeoutTicks=6000)
    public static void native_legacy_support_proof_retains_dirt_and_rejects_foreign_material(GameTestHelper h)throws Exception {
        var f=new DefenseTests.Fixture(h.getLevel(),"a","front_w_04");PaidRecoveryTests.ready(h,defenseBounds(f),()->{
            f.stage();DefenseTransaction.run(f,DefenseTransaction.Action.APPLY);long paid=f.nation.treasury();var support=decayDefenseGrass(h,f);
            h.assertTrue(DefenseTransaction.run(f,DefenseTransaction.Action.RESUME).writes()==0&&f.nation.treasury()==paid,"Native support replayed purchase");
            f.level.setBlock(support,Blocks.GOLD_BLOCK.defaultBlockState(),FLAGS);PaidRecoveryTests.refused(h,()->DefenseTransaction.run(f,DefenseTransaction.Action.ROLLBACK),"read-only condition changed");
            h.assertTrue(f.nation.treasury()==paid,"Foreign support caused refund");f.level.setBlock(support,Blocks.DIRT.defaultBlockState(),FLAGS);
            DefenseTransaction.run(f,DefenseTransaction.Action.ROLLBACK);h.assertTrue(f.level.getBlockState(support).is(Blocks.DIRT)&&f.nation.treasury()==1000,"Natural dirt repainted or refund wrong");h.succeed();
        });
    }
    @GameTest(template="townhall_state_empty",batch="natural34b",timeoutTicks=6000)
    public static void missing_legacy_support_proof_stays_strict_without_rewriting_history(GameTestHelper h)throws Exception {
        var f=new DefenseTests.Fixture(h.getLevel(),"a","front_w_04");PaidRecoveryTests.ready(h,defenseBounds(f),()->{
            f.stage();DefenseTransaction.run(f,DefenseTransaction.Action.APPLY);var journal=ProducerJournal.read(f.file);journal.remove("naturalSupportV1");ProducerJournal.write(f.file,journal,true);
            byte[] before=Files.readAllBytes(f.file);long paid=f.nation.treasury();decayDefenseGrass(h,f);
            PaidRecoveryTests.refused(h,()->DefenseTransaction.run(f,DefenseTransaction.Action.ROLLBACK),"read-only condition changed");
            h.assertTrue(Arrays.equals(before,Files.readAllBytes(f.file))&&f.nation.treasury()==paid,"Historical proof invented or unsafe refund");h.succeed();
        });
    }
    @GameTest(template="townhall_state_empty",batch="natural34c",timeoutTicks=6000)
    public static void unexposed_and_corrupt_defense_support_proof_refuse_without_charge(GameTestHelper h)throws Exception {
        var f=new DefenseTests.Fixture(h.getLevel(),"a","front_w_04");PaidRecoveryTests.ready(h,defenseBounds(f),()->{
            f.stage();PaidRecoveryTests.refused(h,()->DefenseTransaction.run(f,DefenseTransaction.Action.APPLY,(phase,n)->{if(phase.equals("prepared"))throw new IOException("prepared-stop");}),"prepared-stop");
            var support=f.plan.reads().stream().filter(r->r.state().is(Blocks.GRASS_BLOCK)).findFirst().orElseThrow().pos();byte[] before=Files.readAllBytes(f.file);
            f.level.setBlock(support,Blocks.DIRT.defaultBlockState(),FLAGS);PaidRecoveryTests.refused(h,()->DefenseTransaction.run(f,DefenseTransaction.Action.RESUME),"read-only condition changed");
            h.assertTrue(Arrays.equals(before,Files.readAllBytes(f.file))&&f.nation.treasury()==1000,"Unexposed support charged");f.level.setBlock(support,Blocks.GRASS_BLOCK.defaultBlockState(),FLAGS);
            var journal=ProducerJournal.read(f.file);journal.getCompound("naturalSupportV1").putLongArray("exposed",new long[]{support.offset(999,0,0).asLong()});ProducerJournal.write(f.file,journal,true);
            PaidRecoveryTests.refused(h,()->DefenseTransaction.run(f,DefenseTransaction.Action.RESUME),"natural-support contract");h.assertTrue(f.nation.treasury()==1000,"Corrupt proof charged");h.succeed();
        });
    }

}
