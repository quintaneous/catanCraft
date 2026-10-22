package com.quin.catancraft.world;

import com.quin.catancraft.data.*;
import com.quin.catancraft.economy.EconomyCost;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Explicit synthetic fixture contracts only; these tests approve no defense artwork or terrain. */
@GameTestHolder("catancraft_sectors") @PrefixGameTestTemplate(false)
public final class SectorTransactionTests {
    @GameTest(template="townhall_state_empty",batch="sector01",timeoutTicks=1200)
    public static void whole_side_last_cell_conflict_is_atomic(GameTestHelper h){
        try{Fixture f=new Fixture(h,0,0);f.stage();var last=f.changed().get(f.changed().size()-1);f.level.setBlock(last.pos(),Blocks.DIAMOND_BLOCK.defaultBlockState(),2);var before=f.scene();var money=f.nation.save();var book=f.book();
            refuse(()->SectorTransaction.run(f,SectorTransaction.Action.APPLY));h.assertTrue(!Files.exists(f.file)&&money.equals(f.nation.save())&&before.equals(f.scene())&&book.equals(f.book()),"Late whole-side conflict changed earlier pieces, resources, book or journal");h.succeed();
        }catch(Exception x){throw new RuntimeException(x);}
    }
    @GameTest(template="townhall_state_empty",batch="sector02",timeoutTicks=1200)
    public static void insufficient_money_has_no_partial_side_or_debit(GameTestHelper h){
        try{Fixture f=new Fixture(h,0,0);f.stage();f.nation.addTreasury(-1000);var before=f.scene();refuse(()->SectorTransaction.run(f,SectorTransaction.Action.APPLY));h.assertTrue(f.nation.treasury()==0&&f.nation.resource(ResourceType.STONE)==100&&!Files.exists(f.file)&&before.equals(f.scene()),"Whole-side insufficient money changed world/economy/history");h.succeed();}catch(Exception x){throw new RuntimeException(x);}
    }
    @GameTest(template="townhall_state_empty",batch="sector03",timeoutTicks=1200)
    public static void insufficient_materials_has_no_partial_side_or_debit(GameTestHelper h){
        try{Fixture f=new Fixture(h,0,0);f.stage();f.nation.addResource(ResourceType.STONE,-100);var before=f.scene();refuse(()->SectorTransaction.run(f,SectorTransaction.Action.APPLY));h.assertTrue(f.nation.treasury()==1000&&f.nation.resource(ResourceType.STONE)==0&&!Files.exists(f.file)&&before.equals(f.scene()),"Whole-side insufficient materials changed world/economy/history");h.succeed();}catch(Exception x){throw new RuntimeException(x);}
    }
    @GameTest(template="townhall_state_empty",batch="sector04",timeoutTicks=1200)
    public static void upgrade_one_group_charge_duplicate_and_exact_previous_receipt(GameTestHelper h){
        try{Fixture f=new Fixture(h,1,0);f.stage();var before=f.scene();var nation=f.nation.save();var book=f.book();String previous=f.receipt;
            h.assertTrue(SectorTransaction.run(f,SectorTransaction.Action.APPLY).writes()==12,"Whole L1-to-L2 side did not write all union cells");h.assertTrue(f.progress==2&&f.nation.treasury()==927&&f.nation.resource(ResourceType.STONE)==97,"Whole upgrade charge/level wrong");
            byte[] history=Files.readAllBytes(f.file);h.assertTrue(SectorTransaction.run(f,SectorTransaction.Action.APPLY).writes()==0&&Arrays.equals(history,Files.readAllBytes(f.file))&&f.nation.treasury()==927,"Duplicate side purchase charged or rewrote history");
            h.assertTrue(SectorTransaction.run(f,SectorTransaction.Action.ROLLBACK).writes()==12,"Whole upgrade rollback incomplete");h.assertTrue(f.progress==1&&f.receipt.equals(previous)&&nation.equals(f.nation.save())&&before.equals(f.scene())&&book.equals(f.book()),"Rollback lost previous purchased level/receipt, resources or actual NBT");
            h.assertTrue(SectorTransaction.run(f,SectorTransaction.Action.ROLLBACK).writes()==0&&nation.equals(f.nation.save()),"Repeated rollback refunded twice");h.succeed();
        }catch(Exception x){throw new RuntimeException(x);}
    }
    @GameTest(template="townhall_state_empty",batch="sector05",timeoutTicks=1200)
    public static void interrupted_group_resume_historical_cost_and_current_inventory(GameTestHelper h){
        try{Fixture f=new Fixture(h,0,0);f.stage();var initial=f.nation.save();
            refuse(()->SectorTransaction.run(f,SectorTransaction.Action.APPLY,(stage,n)->{if(stage.equals("write")&&n==2)throw new IOException("Injected whole-side forward interruption");}));
            h.assertTrue(f.progress==0&&f.receipt.endsWith(":reserved")&&f.nation.treasury()==927,"Pending sector was exposed as complete or unreserved");var scene=f.scene();byte[] journal=Files.readAllBytes(f.file);refuse(()->SectorTransaction.run(f,SectorTransaction.Action.APPLY));h.assertTrue(scene.equals(f.scene())&&Arrays.equals(journal,Files.readAllBytes(f.file)),"Pending APPLY silently resumed");
            f.reload();f.price=new EconomyCost(9999,Map.of(ResourceType.STONE,99L));f.barrel().setItem(1,new ItemStack(Items.EMERALD,11));var currentBook=f.book();
            h.assertTrue(SectorTransaction.run(f,SectorTransaction.Action.RESUME).writes()==4&&f.nation.treasury()==927&&f.progress==1,"Resume charged current price or repeated reserved debit");h.assertTrue(currentBook.equals(f.book()),"Resume restored stale inventory snapshot");
            refuse(()->SectorTransaction.run(f,SectorTransaction.Action.ROLLBACK,(stage,n)->{if(stage.equals("rollback_write")&&n==2)throw new IOException("Injected reverse interruption");}));f.reload();
            h.assertTrue(SectorTransaction.run(f,SectorTransaction.Action.ROLLBACK).writes()==4&&initial.equals(f.nation.save())&&f.progress==0&&f.receipt.isEmpty()&&currentBook.equals(f.book()),"Reverse recovery double-refunded or lost current book/inventory");h.succeed();
        }catch(Exception x){throw new RuntimeException(x);}
    }
    @GameTest(template="townhall_state_empty",batch="sector06",timeoutTicks=1200)
    public static void rollback_drift_refuses_before_refund_or_other_piece_writes(GameTestHelper h){
        try{Fixture f=new Fixture(h,0,0);f.stage();SectorTransaction.run(f,SectorTransaction.Action.APPLY);var cell=f.changed().get(f.changed().size()-1);f.level.setBlock(cell.pos(),Blocks.GOLD_BLOCK.defaultBlockState(),2);var scene=f.scene();var nation=f.nation.save();refuse(()->SectorTransaction.run(f,SectorTransaction.Action.ROLLBACK));h.assertTrue(scene.equals(f.scene())&&nation.equals(f.nation.save())&&f.progress==1,"Drift rollback changed earlier side pieces, level or refund");h.succeed();}catch(Exception x){throw new RuntimeException(x);}
    }
    @GameTest(template="townhall_state_empty",batch="sector07",timeoutTicks=1200)
    public static void stale_protected_read_and_neighbor_quote_refuse(GameTestHelper h){
        try{Fixture f=new Fixture(h,0,0);f.stage();var read=f.plan.reads().get(0);f.level.setBlock(read.pos(),Blocks.GOLD_BLOCK.defaultBlockState(),2);refuse(()->SectorTransaction.run(f,SectorTransaction.Action.APPLY));h.assertTrue(!Files.exists(f.file)&&f.nation.treasury()==1000,"Stale protected corner/road changed payment/history");f.level.setBlock(read.pos(),read.state(),2);
            refuse(()->SectorTransaction.run(f,SectorTransaction.Action.APPLY,(stage,n)->{if(stage.equals("prepared"))f.neighbor="changed-neighbor-receipt";}));h.assertTrue(f.nation.treasury()==1000&&f.progress==0&&!f.receipt.endsWith(":reserved"),"Changed neighbor quote allowed reservation/writes");for(var c:f.changed())h.assertTrue(f.level.getBlockState(c.pos()).equals(c.before()),"Stale neighbor wrote partial side");h.succeed();
        }catch(Exception x){throw new RuntimeException(x);}
    }
    @GameTest(template="townhall_state_empty",batch="sector08",timeoutTicks=1200)
    public static void crash_after_paid_and_refunded_ledger_never_repeats_money(GameTestHelper h){
        try{Fixture f=new Fixture(h,1,0);f.stage();var initial=f.nation.save();String previous=f.receipt;
            refuse(()->SectorTransaction.run(f,SectorTransaction.Action.APPLY,(stage,n)->{if(stage.equals("ledger_saved"))throw new IOException("Injected paid ledger boundary");}));f.reload();h.assertTrue(f.progress==2&&f.nation.treasury()==927,"Paid ledger fixture missing");
            h.assertTrue(SectorTransaction.run(f,SectorTransaction.Action.RESUME).writes()==0&&f.nation.treasury()==927,"Post-ledger recovery charged twice");
            refuse(()->SectorTransaction.run(f,SectorTransaction.Action.ROLLBACK,(stage,n)->{if(stage.equals("ledger_saved"))throw new IOException("Injected restored ledger boundary");}));f.reload();
            h.assertTrue(f.progress==1&&f.receipt.equals(previous)&&initial.equals(f.nation.save()),"Original level/receipt/refund not restored atomically");h.assertTrue(SectorTransaction.run(f,SectorTransaction.Action.ROLLBACK).writes()==0&&initial.equals(f.nation.save()),"Recovered reverse ledger refunded twice");h.succeed();
        }catch(Exception x){throw new RuntimeException(x);}
    }
    @GameTest(template="townhall_state_empty",batch="sector09",timeoutTicks=1200)
    public static void harmless_entity_survives_and_actual_solid_obstruction_refuses(GameTestHelper h){
        try{Fixture f=new Fixture(h,0,0),blocked=new Fixture(h,0,20);f.stage();blocked.stage();BlockPos safe=f.origin.offset(4,0,1);ItemStack stack=new ItemStack(Items.EMERALD,7);stack.getOrCreateTag().putUUID("fixture",UUID.randomUUID());ItemEntity item=item(f.level,safe,stack);var obstacle=blocked.changed().get(blocked.changed().size()-1);ItemEntity obstructing=item(blocked.level,obstacle.pos(),new ItemStack(Items.STICK,3));
            h.runAfterDelay(5,()->{try{
                h.assertTrue(f.level.getEntitiesOfClass(ItemEntity.class,item.getBoundingBox()).contains(item),"Harmless test entity not loaded");UUID id=item.getUUID();SectorTransaction.run(f,SectorTransaction.Action.APPLY);h.assertTrue(!item.isRemoved()&&id.equals(item.getUUID())&&ItemStack.matches(stack,item.getItem()),"Whole-side operation changed harmless item");
                var scene=blocked.scene();refuse(()->SectorTransaction.run(blocked,SectorTransaction.Action.APPLY));h.assertTrue(!Files.exists(blocked.file)&&blocked.nation.treasury()==1000&&scene.equals(blocked.scene())&&!obstructing.isRemoved()&&obstructing.getItem().getCount()==3,"Actual collision refusal moved entity, charged, or wrote side");h.succeed();
            }catch(Exception x){throw new RuntimeException(x);}});
        }catch(Exception x){throw new RuntimeException(x);}
    }
    @GameTest(template="townhall_state_empty",batch="sector10",timeoutTicks=1200)
    public static void malformed_history_cost_and_inventory_replacement_are_refused(GameTestHelper h){
        try{CompoundTag bad=new CompoundTag();bad.putLong("money",-1);refuse(()->SectorTransaction.historicalCost(bad));bad.putLong("money",10);bad.putLong("unknown_material",1);refuse(()->SectorTransaction.historicalCost(bad));
            Fixture f=new Fixture(h,0,0);var original=f.plan;var cell=new SectorTransaction.Cell(f.bookPos,Blocks.BARREL.defaultBlockState(),Blocks.STONE.defaultBlockState());var invalid=new SectorTransaction.Plan("north","fixture-source","fixture-target",0,1,List.of(cell),List.of());refuse(()->SectorTransaction.describe(invalid));refuse(()->SectorTransaction.describe(new SectorTransaction.Plan("north","overflow-source","overflow-target",Integer.MAX_VALUE,Integer.MIN_VALUE,original.cells(),original.reads())));h.assertTrue(original.cells().size()==7,"Fixture union changed");h.succeed();
        }catch(Exception x){throw new RuntimeException(x);}
    }
    @GameTest(template="townhall_state_empty",batch="sector_runner01",timeoutTicks=1200)
    public static void bounded_runner_yields_without_exposing_paid_level(GameTestHelper h){
        try{
            Fixture f=new Fixture(h,0,0);f.stage();
            ItemEntity harmless=item(f.level,f.origin.offset(4,0,1),new ItemStack(Items.EMERALD,7));
            h.runAfterDelay(5,()->{try{
                SectorTransaction.Runner runner=SectorTransaction.begin(f,SectorTransaction.Action.APPLY);
                h.assertTrue(runner.writes()==0&&f.progress==0&&f.nation.treasury()==927,"Preparation exposed level or wrote blocks");
                boundedTick(h,f,runner,harmless,0);
            }catch(Exception x){throw new RuntimeException(x);}});
        }catch(Exception x){throw new RuntimeException(x);}
    }
    private static void boundedTick(GameTestHelper h,Fixture f,SectorTransaction.Runner runner,ItemEntity harmless,int slices){
        try{
            int before=runner.writes();boolean done=runner.step(1,Long.MAX_VALUE);
            h.assertTrue(runner.writes()-before==1,"Bounded slice exceeded one write or stalled");
            h.assertTrue(!harmless.isRemoved()&&harmless.getItem().getCount()==7,"Slice changed harmless entity");
            if(!done){h.assertTrue(f.progress==0&&f.receipt.endsWith(":reserved"),"Partial side exposed completed level");h.runAfterDelay(1,()->boundedTick(h,f,runner,harmless,slices+1));return;}
            h.assertTrue(slices==5&&runner.result().writes()==6&&f.progress==1&&f.nation.treasury()==927,"Bounded completion accounting wrong");
            h.assertTrue(runner.step(1,1)&&f.nation.treasury()==927,"Completed runner charged again");
            h.assertTrue(SectorTransaction.run(f,SectorTransaction.Action.APPLY).writes()==0,"Duplicate bounded purchase wrote again");h.succeed();
        }catch(Exception x){throw new RuntimeException(x);}
    }
    @GameTest(template="townhall_state_empty",batch="sector_runner02",timeoutTicks=1200)
    public static void bounded_runner_detects_between_slice_read_drift_and_recovers(GameTestHelper h){
        try{
            Fixture f=new Fixture(h,0,0);f.stage();var runner=SectorTransaction.begin(f,SectorTransaction.Action.APPLY);
            h.assertTrue(!runner.step(1,Long.MAX_VALUE),"One write completed whole side");
            var read=f.plan.reads().get(0);f.level.setBlock(read.pos(),Blocks.DIAMOND_BLOCK.defaultBlockState(),2);var scene=f.scene();
            refuse(()->runner.step(1,Long.MAX_VALUE));h.assertTrue(scene.equals(f.scene())&&f.nation.treasury()==927&&f.progress==0,"Drift wrote another cell, refunded or completed");
            f.level.setBlock(read.pos(),read.state(),2);refuse(()->runner.step(1,Long.MAX_VALUE));
            f.reload();f.price=new EconomyCost(9999,Map.of(ResourceType.STONE,99L));
            h.assertTrue(SectorTransaction.run(f,SectorTransaction.Action.RESUME).writes()==5&&f.nation.treasury()==927,"Discarded runner recovery repeated charge");
            var reverse=SectorTransaction.begin(f,SectorTransaction.Action.ROLLBACK);h.assertTrue(!reverse.step(1,Long.MAX_VALUE)&&f.nation.treasury()==927,"Partial reverse refunded before completion");
            f.reload();h.assertTrue(SectorTransaction.run(f,SectorTransaction.Action.ROLLBACK).writes()==5&&f.nation.treasury()==1000&&f.nation.resource(ResourceType.STONE)==100,"Reverse runner recovery lost original price");h.succeed();
        }catch(Exception x){throw new RuntimeException(x);}
    }
    @GameTest(template="townhall_state_empty",batch="sector_runner03",timeoutTicks=1200)
    public static void bounded_runner_refuses_new_collision_on_next_tick(GameTestHelper h){
        try{
            Fixture f=new Fixture(h,0,0);f.stage();var runner=SectorTransaction.begin(f,SectorTransaction.Action.APPLY);runner.step(1,Long.MAX_VALUE);
            var next=f.changed().get(1);var obstacle=item(f.level,next.pos(),new ItemStack(Items.STICK,3));
            h.runAfterDelay(5,()->{try{
                var scene=f.scene();refuse(()->runner.step(1,Long.MAX_VALUE));
                h.assertTrue(scene.equals(f.scene())&&f.progress==0&&f.nation.treasury()==927&&!obstacle.isRemoved(),"Entering obstruction was ignored or moved");h.succeed();
            }catch(Exception x){throw new RuntimeException(x);}});
        }catch(Exception x){throw new RuntimeException(x);}
    }
    @GameTest(template="townhall_state_empty",batch="sector_runner04",timeoutTicks=1200)
    public static void bounded_runner_reopens_and_rejects_resealed_journal_rewrite(GameTestHelper h){
        try{
            Fixture f=new Fixture(h,0,0);f.stage();var runner=SectorTransaction.begin(f,SectorTransaction.Action.APPLY);runner.step(1,Long.MAX_VALUE);
            byte[] original=Files.readAllBytes(f.file);var rewritten=ProducerJournal.read(f.file);rewritten.getCompound("cost").putLong("money",74);ProducerJournal.write(f.file,rewritten,true);
            var scene=f.scene();refuse(()->runner.step(1,Long.MAX_VALUE));
            h.assertTrue(scene.equals(f.scene())&&f.nation.treasury()==927&&f.progress==0,"Resealed journal rewrite permitted writes, refund or completion");
            Files.write(f.file,original);refuse(()->runner.step(1,Long.MAX_VALUE));f.reload();
            h.assertTrue(SectorTransaction.run(f,SectorTransaction.Action.RESUME).writes()==5&&f.nation.treasury()==927&&f.progress==1,"Restored original journal recovery repeated debit");h.succeed();
        }catch(Exception error){throw new RuntimeException(error);}
    }
    private static ItemEntity item(ServerLevel level,BlockPos p,ItemStack stack){ItemEntity item=new ItemEntity(level,p.getX()+0.5,p.getY()+0.15,p.getZ()+0.5,stack);item.setNoGravity(true);item.setDeltaMovement(Vec3.ZERO);level.addFreshEntity(item);return item;}
    private interface Checked {void run()throws IOException;}
    private static void refuse(Checked action)throws IOException {try{action.run();}catch(IOException expected){return;}throw new AssertionError("Expected whole-sector safety refusal");}
    private static final class Fixture implements SectorTransaction.Environment {
        final ServerLevel level;final BlockPos origin,bookPos;final Path file,ledgerFile;final SectorTransaction.Plan plan;NationData nation;int progress;String receipt,neighbor="neighbor-fixture-L0";EconomyCost price=new EconomyCost(73,Map.of(ResourceType.STONE,3L));boolean authorized=true;
        Fixture(GameTestHelper h,int source,int offset)throws IOException {
            level=h.getLevel();if(!(level.getServer() instanceof GameTestServer))throw new IOException("Synthetic whole-side fixture requires isolated GameTestServer");origin=h.absolutePos(new BlockPos(2+offset,2,2));bookPos=origin.offset(-2,0,0);progress=source;receipt=source==0?"":"historical-source-side:paid";
            nation=new NationData(UUID.randomUUID(),"sector-fixture",UUID.randomUUID(),1000);nation.addResource(ResourceType.STONE,100);
            Path dir=level.getServer().getWorldPath(LevelResource.ROOT).resolve("data/sector-fixture/"+UUID.randomUUID());Files.createDirectories(dir);file=dir.resolve("catancraft_sector_a_north_"+UUID.randomUUID()+"_v1.dat");ledgerFile=dir.resolve("fixture-ledger.dat");
            List<SectorTransaction.Cell> cells=new ArrayList<>();for(int z:new int[]{0,3})for(int x=0;x<3;x++)cells.add(new SectorTransaction.Cell(origin.offset(x,0,z),source==0?Blocks.AIR.defaultBlockState():Blocks.STONE_BRICKS.defaultBlockState(),source==0?Blocks.STONE_BRICKS.defaultBlockState():Blocks.POLISHED_ANDESITE.defaultBlockState()));
            if(source>0)for(int z:new int[]{0,3})for(int x=0;x<3;x++)cells.add(new SectorTransaction.Cell(origin.offset(x,1,z),Blocks.AIR.defaultBlockState(),Blocks.STONE_BRICKS.defaultBlockState()));
            cells.add(new SectorTransaction.Cell(bookPos,Blocks.BARREL.defaultBlockState(),Blocks.BARREL.defaultBlockState()));
            plan=new SectorTransaction.Plan("north","synthetic-fixture-L"+source,"synthetic-fixture-L"+(source+1),source,source+1,cells,List.of(new SectorTransaction.Read(origin.offset(-2,0,3),Blocks.BEDROCK.defaultBlockState()),new SectorTransaction.Read(origin.offset(4,0,1),Blocks.AIR.defaultBlockState())));
        }
        List<SectorTransaction.Cell> changed(){return plan.cells().stream().filter(c->!c.before().equals(c.after())).toList();}
        void stage()throws IOException {
            for(int x=-3;x<=5;x++)for(int z=-1;z<=4;z++)for(int y=-1;y<=2;y++){
                BlockPos p=origin.offset(x,y,z);level.getChunkAt(p);var be=level.getBlockEntity(p);if(be instanceof net.minecraft.world.Container c)c.clearContent();level.setBlock(p,y==-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2|16|32);
            }
            for(var c:plan.cells())level.setBlock(c.pos(),c.before(),2|16|32);for(var r:plan.reads())level.setBlock(r.pos(),r.state(),2|16|32);
            ItemStack book=new ItemStack(Items.WRITTEN_BOOK);book.getOrCreateTag().putString("title","Actual protected sector fixture book");book.getOrCreateTag().putString("author","Owner");book.getOrCreateTag().putString("payload","paid resources and original NBT");barrel().setItem(0,book);save();
        }
        BarrelBlockEntity barrel(){return (BarrelBlockEntity)level.getBlockEntity(bookPos);}CompoundTag book(){return barrel().saveWithFullMetadata().copy();}
        Map<BlockPos,BlockState> scene(){Map<BlockPos,BlockState> scene=new LinkedHashMap<>();for(var c:plan.cells())scene.put(c.pos(),level.getBlockState(c.pos()));for(var r:plan.reads())scene.put(r.pos(),level.getBlockState(r.pos()));return scene;}
        void reload()throws IOException {CompoundTag saved=ProducerJournal.read(ledgerFile);nation=NationData.load(saved.getCompound("nation"));progress=saved.getInt("level");receipt=saved.getString("receipt");}
        public ServerLevel level(){return level;}public Path file(){return file;}public SectorTransaction.Plan plan(){return plan;}public EconomyCost cost(){return price;}public NationData nation(){return nation;}public int progress(){return progress;}public void progress(int value){progress=value;}public String receipt(){return receipt;}public void receipt(String value){receipt=value;}
        public CompoundTag identity(){CompoundTag n=new CompoundTag();n.putString("world",file.getParent().toString());n.putString("city","a");n.putString("sector","north");n.putUUID("owner",nation.id());n.putString("neighborQuote",neighbor);return n;}
        public void requirements(boolean rollback)throws IOException {if(!authorized)throw new IOException("Whole-side authorization/neighbor reservation unavailable");}
        public void allow(BlockPos p)throws IOException {if(!authorized||changed().stream().noneMatch(c->c.pos().equals(p)))throw new IOException("Whole-side write outside authorized union");}
        public void save()throws IOException {CompoundTag n=new CompoundTag();n.put("nation",nation.save());n.putInt("level",progress);n.putString("receipt",receipt);ProducerJournal.write(ledgerFile,n,Files.exists(ledgerFile,LinkOption.NOFOLLOW_LINKS));}
    }
}
