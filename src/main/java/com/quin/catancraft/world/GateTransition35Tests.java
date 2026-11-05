package com.quin.catancraft.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Synthetic six-cell gate only: no City A placement, approval, vehicle clearance or siege claim. */
@GameTestHolder("catancraft_gate35") @PrefixGameTestTemplate(false)
public final class GateTransition35Tests {
    private static final AtomicInteger SITES=new AtomicInteger();
    private static final GateTransition35.State OPEN=GateTransition35.State.OPEN,CLOSED=GateTransition35.State.CLOSED;

    @GameTest(template="townhall_state_empty",batch="gate35_01",timeoutTicks=5000)
    public static void complete_cycle_is_bounded_and_source_saved_before_intent(GameTestHelper h){run(h,f->{
        UUID close=UUID.randomUUID();try(var r=f.begin(CLOSED,close)){
            h.assertTrue(f.all(OPEN)&&f.sourceSavedBeforeIntent,"Preparation wrote blocks or omitted durable source save");
            h.assertTrue(r.step()==4&&!r.done()&&f.count(CLOSED)==4,"First gate slice exceeded/fell short of four fixture writes");
            h.assertTrue(r.step()==2&&r.done()&&f.all(CLOSED),"Second slice did not finish exact closed mask");
        }
        f.finish(OPEN,UUID.randomUUID());h.assertTrue(f.all(OPEN)&&f.journal().getString("phase").equals("DONE")&&f.flushes>=6,"Full cycle lost exact state or physical-save ordering");
    });}

    @GameTest(template="townhall_state_empty",batch="gate35_02",timeoutTicks=5000)
    public static void obstructed_close_preserves_entity_blocks_and_absent_journal(GameTestHelper h){run(h,f->{
        ItemEntity obstruction=f.item(f.cells.get(4).pos());UUID id=obstruction.getUUID();CompoundTag before=obstruction.saveWithoutId(new CompoundTag()).copy();
        refuse(()->f.begin(CLOSED,UUID.randomUUID()));
        h.assertTrue(f.all(OPEN)&&!Files.exists(f.path())&&!obstruction.isRemoved()&&id.equals(obstruction.getUUID())&&before.equals(obstruction.saveWithoutId(new CompoundTag())),"Blocked close changed entity/world/history");
    });}

    @GameTest(template="townhall_state_empty",batch="gate35_03",timeoutTicks=5000)
    public static void partial_motion_resumes_only_its_original_intent(GameTestHelper h){run(h,f->{
        UUID id=UUID.randomUUID();try(var r=f.begin(CLOSED,id)){
            refuse(()->f.begin(CLOSED,id));h.assertTrue(r.step()==4,"Fixture did not interrupt after a bounded partial slice");
        }
        byte[] saved=Files.readAllBytes(f.path());refuse(()->f.begin(OPEN,UUID.randomUUID()));refuse(()->f.begin(CLOSED,UUID.randomUUID()));
        h.assertTrue(Arrays.equals(saved,Files.readAllBytes(f.path()))&&f.count(CLOSED)==4,"Competing intent changed pending motion");
        try(var resumed=f.begin(CLOSED,id)){h.assertTrue(resumed.step()==2&&resumed.done(),"Original resume rewrote completed cells or omitted remaining ones");}
        h.assertTrue(f.all(CLOSED),"Resumed gate incomplete");
    });}

    @GameTest(template="townhall_state_empty",batch="gate35_04",timeoutTicks=5000)
    public static void owner_world_and_live_guard_changes_refuse_before_writes(GameTestHelper h){run(h,f->{
        UUID id=UUID.randomUUID(),owner=f.owner;String world=f.worldId;
        try(var r=f.begin(CLOSED,id)){f.allowed=false;refuse(r::step);}
        byte[] saved=Files.readAllBytes(f.path());f.allowed=true;f.owner=UUID.randomUUID();refuse(()->f.begin(CLOSED,id));f.owner=owner;
        f.worldId=UUID.randomUUID().toString();refuse(()->f.begin(CLOSED,id));f.worldId=world;
        h.assertTrue(f.all(OPEN)&&Arrays.equals(saved,Files.readAllBytes(f.path())),"Changed authorization/owner/world adopted or wrote a gate");
        try(var r=f.begin(CLOSED,id)){f.owner=UUID.randomUUID();refuse(r::step);}f.owner=owner;
        h.assertTrue(f.all(OPEN),"Runner failed to recheck current owner");
    });}

    @GameTest(template="townhall_state_empty",batch="gate35_05",timeoutTicks=5000)
    public static void unknown_source_and_unjournaled_target_mixture_are_not_adopted(GameTestHelper h){run(h,f->{
        BlockPos last=f.cells.get(5).pos();f.level.setBlock(last,Blocks.GOLD_BLOCK.defaultBlockState(),2);refuse(()->f.begin(CLOSED,UUID.randomUUID()));
        h.assertTrue(f.count(CLOSED)==0&&!Files.exists(f.path()),"Foreign last source wrote an earlier gate cell");
        f.level.setBlock(last,Blocks.STONE_BRICKS.defaultBlockState(),2);refuse(()->f.begin(CLOSED,UUID.randomUUID()));
        h.assertTrue(f.count(CLOSED)==1&&!Files.exists(f.path()),"Target mixture without original intent was adopted");
    });}

    @GameTest(template="townhall_state_empty",batch="gate35_06",timeoutTicks=5000)
    public static void changed_read_and_new_obstruction_stop_the_next_slice(GameTestHelper h){run(h,f->{
        UUID id=UUID.randomUUID();var first=f.begin(CLOSED,id);h.assertTrue(first.step()==4,"Missing partial fixture");
        var read=f.reads.get(0);f.level.setBlock(read.pos(),Blocks.GOLD_BLOCK.defaultBlockState(),2);byte[] saved=Files.readAllBytes(f.path());refuse(first::step);
        h.assertTrue(f.count(CLOSED)==4&&Arrays.equals(saved,Files.readAllBytes(f.path())),"Changed protected read allowed another write");
        f.level.setBlock(read.pos(),read.state(),2);
        try(var resumed=f.begin(CLOSED,id)){
            BlockPos remaining=f.cells.stream().filter(c->f.level.getBlockState(c.pos()).isAir()).findFirst().orElseThrow().pos();ItemEntity item=f.item(remaining);refuse(resumed::step);
            h.assertTrue(f.count(CLOSED)==4&&!item.isRemoved()&&item.getItem().getCount()==3,"Later obstruction was deleted or crushed by resumed slice");
        }
    });}

    @GameTest(template="townhall_state_empty",batch="gate35_07",timeoutTicks=5000)
    public static void duplicate_is_read_only_and_old_request_cannot_replay_after_cycle(GameTestHelper h){run(h,f->{
        UUID close=UUID.randomUUID();f.finish(CLOSED,close);byte[] saved=Files.readAllBytes(f.path());int flushes=f.flushes;
        try(var repeated=f.begin(CLOSED,close)){h.assertTrue(repeated.done()&&repeated.step()==0,"Duplicate closed request restarted motion");}
        h.assertTrue(f.flushes==flushes&&Arrays.equals(saved,Files.readAllBytes(f.path())),"Duplicate changed durable receipt");
        refuse(()->f.begin(OPEN,close));f.finish(OPEN,UUID.randomUUID());refuse(()->f.begin(CLOSED,close));h.assertTrue(f.all(OPEN),"Historical intent replay closed a later opened gate");
    });}

    @GameTest(template="townhall_state_empty",batch="gate35_08",timeoutTicks=5000)
    public static void fluid_block_entity_and_incomplete_read_masks_refuse(GameTestHelper h){run(h,f->{
        // The wall torch's east support would appear only in a later write: final-state-only
        // physics would accept it, but the first intermediate native world is unsupported.
        for(BlockState bad:List.of(Blocks.WATER.defaultBlockState(),Blocks.BARREL.defaultBlockState(),Blocks.WALL_TORCH.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,Direction.WEST))){
            var cells=new ArrayList<>(f.cells);var first=cells.get(0);cells.set(0,new GateTransition35.Cell(first.pos(),first.open(),bad));
            var contract=new GateTransition35.GateContract("fixture","synthetic-gate35-v1",OPEN,cells,f.reads);refuse(()->GateTransition35.begin(f,contract,CLOSED,UUID.randomUUID()));
        }
        var truncated=new ArrayList<>(f.reads);truncated.remove(0);var contract=new GateTransition35.GateContract("fixture","synthetic-gate35-v1",OPEN,f.cells,truncated);
        refuse(()->GateTransition35.begin(f,contract,CLOSED,UUID.randomUUID()));h.assertTrue(f.all(OPEN)&&!Files.exists(f.path()),"Unsupported states/missing boundary produced a gate journal or writes");
    });}

    @GameTest(template="townhall_state_empty",batch="gate35_09",timeoutTicks=5000)
    public static void hostile_sealed_proof_and_changed_file_are_retained_and_refused(GameTestHelper h){run(h,f->{
        UUID id=UUID.randomUUID();try(var r=f.begin(CLOSED,id)){
            CompoundTag forged=f.journal();forged.putString("unrecognizedAuthority","approved");ProducerJournal.write(f.path(),forged,true);byte[] hostile=Files.readAllBytes(f.path());refuse(r::step);
            h.assertTrue(f.all(OPEN)&&Arrays.equals(hostile,Files.readAllBytes(f.path())),"Changed sealed proof was overwritten or trusted");
        }
        refuse(()->f.begin(CLOSED,id));h.assertTrue(f.all(OPEN),"Fresh reader accepted noncanonical hostile proof");
    });}

    @GameTest(template="townhall_state_empty",batch="gate35_10",timeoutTicks=5000)
    public static void changed_contract_and_missing_history_do_not_authorize_recovery(GameTestHelper h){run(h,f->{
        UUID id=UUID.randomUUID();try(var r=f.begin(CLOSED,id)){r.step();}
        var cells=new ArrayList<>(f.cells);var first=cells.get(0);cells.set(0,new GateTransition35.Cell(first.pos(),first.open(),Blocks.GOLD_BLOCK.defaultBlockState()));
        var changed=new GateTransition35.GateContract("fixture","synthetic-gate35-v1",OPEN,cells,f.reads);byte[] saved=Files.readAllBytes(f.path());refuse(()->GateTransition35.begin(f,changed,CLOSED,id));
        h.assertTrue(f.count(CLOSED)==4&&Arrays.equals(saved,Files.readAllBytes(f.path())),"Changed source contract replaced original intent");
        Path retained=f.path().resolveSibling(f.path().getFileName()+".retained-test-evidence");Files.move(f.path(),retained);refuse(()->f.begin(CLOSED,id));
        h.assertTrue(!Files.exists(f.path())&&Arrays.equals(saved,Files.readAllBytes(retained))&&f.count(CLOSED)==4,"Missing history caused mixed-state adoption");
    });}

    @GameTest(template="townhall_state_empty",batch="gate35_11",timeoutTicks=5000)
    public static void opening_refuses_native_support_loss_without_mutating_context(GameTestHelper h){run(h,CLOSED,true,f->{
        refuse(()->f.begin(OPEN,UUID.randomUUID()));h.assertTrue(f.all(CLOSED)&&f.level.getBlockState(f.cells.get(0).pos().above()).is(Blocks.TORCH)&&!Files.exists(f.path()),"Opening removed support for an unchanged neighboring torch");
    });}

    @GameTest(template="townhall_state_empty",batch="gate35_12",timeoutTicks=5000)
    public static void source_save_failure_is_clean_and_target_save_failure_remains_pending(GameTestHelper h){run(h,f->{
        UUID id=UUID.randomUUID();f.failFlush=true;refuse(()->f.begin(CLOSED,id));h.assertTrue(f.all(OPEN)&&!Files.exists(f.path()),"Source-save failure wrote intent/blocks");
        f.failFlush=false;try(var r=f.begin(CLOSED,id)){f.failFlush=true;refuse(r::step);}
        h.assertTrue(f.count(CLOSED)==4&&f.journal().getString("phase").equals("MOVING"),"Failed physical save claimed terminal gate success");
        f.failFlush=false;try(var resumed=f.begin(CLOSED,id)){h.assertTrue(resumed.step()==2&&resumed.done(),"Save failure was not recoverable through original exact intent");}
    });}

    private interface Attempt {void run()throws Exception;}
    private interface Body {void run(Fixture f)throws Exception;}
    private static void refuse(Attempt action)throws Exception {try{action.run();}catch(IOException expected){return;}throw new AssertionError("Expected guarded gate refusal");}
    private static void run(GameTestHelper h,Body body){run(h,OPEN,false,body);}
    private static void run(GameTestHelper h,GateTransition35.State initial,boolean torch,Body body){
        try{Fixture f=new Fixture(h,initial,torch);ready(h,f,body,0);}catch(Exception error){throw new RuntimeException(error);}
    }
    private static void ready(GameTestHelper h,Fixture f,Body body,int attempts){h.runAfterDelay(attempts==0?10:1,()->{
        try{
            f.lease.keepAlive();if(!f.lease.ready()){
                // Match the existing paid-recovery fixture's bounded residency budget.
                // Never run assertions until real chunks AND entity sections are ready.
                if(attempts==400||attempts==4000)com.quin.catancraft.CatanCraft.LOGGER.warn(
                    "GATE35_FIXTURE_WAIT attempts={} wallMillis={} pending={}",attempts,f.wallMillis(),f.pendingChunks());
                if(attempts>=4000)throw new IOException("Gate fixture entity chunks never became ready: "+f.pendingChunks());
                ready(h,f,body,attempts+1);return;
            }
            com.quin.catancraft.CatanCraft.LOGGER.info("GATE35_FIXTURE_READY attempts={} wallMillis={} chunks={}",attempts,f.wallMillis(),f.lease.chunkCount());
            StreetscapeService.requireLoadedEntities(f.level,List.of(GateTransition35.bounds(f.contract)));
            try{body.run(f);h.succeed();}finally{f.lease.close();}
        }catch(Exception error){f.lease.close();throw new RuntimeException(error);}
    });}
    private static final class Fixture implements GateTransition35.Environment {
        final long startedNanos=System.nanoTime();
        final ServerLevel level;final List<GateTransition35.Cell> cells;final List<GateTransition35.Read> reads;final GateTransition35.GateContract contract;final SectorChunkLease lease;
        final String city="g35_"+UUID.randomUUID().toString().replace("-","");UUID owner=UUID.randomUUID();String worldId=UUID.randomUUID().toString();boolean allowed=true,failFlush,sourceSavedBeforeIntent;int flushes;
        Fixture(GameTestHelper h,GateTransition35.State initial,boolean torch)throws IOException {
            level=h.getLevel();if(!(level.getServer() instanceof GameTestServer))throw new IOException("Synthetic gate tests require GameTestServer");
            BlockPos origin=h.absolutePos(new BlockPos(3000+48*SITES.getAndIncrement(),10,3000));
            for(int x=-3;x<=8;x++)for(int y=-3;y<=3;y++)for(int z=-3;z<=3;z++){
                BlockPos p=origin.offset(x,y,z);level.getChunkAt(p);level.setBlock(p,y==-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2|16|32);
            }
            List<GateTransition35.Cell> writes=new ArrayList<>();for(int x=0;x<6;x++){
                BlockPos p=origin.offset(x,0,0);var cell=new GateTransition35.Cell(p,Blocks.AIR.defaultBlockState(),Blocks.STONE_BRICKS.defaultBlockState());writes.add(cell);level.setBlock(p,cell.state(initial),2|16|32);
            }
            cells=List.copyOf(writes);if(torch)level.setBlock(cells.get(0).pos().above(),Blocks.TORCH.defaultBlockState(),2|16|32);
            Set<BlockPos> writePositions=new HashSet<>(),context=new HashSet<>();for(var cell:cells)writePositions.add(cell.pos());for(var cell:cells)for(Direction d:Direction.values())if(!writePositions.contains(cell.pos().relative(d)))context.add(cell.pos().relative(d));
            reads=context.stream().sorted(Comparator.comparingLong(BlockPos::asLong)).map(p->new GateTransition35.Read(p,level.getBlockState(p))).toList();
            contract=new GateTransition35.GateContract("fixture","synthetic-gate35-v1",initial,cells,reads);lease=SectorChunkLease.acquire(level,GateTransition35.bounds(contract));
        }
        long wallMillis(){return (System.nanoTime()-startedNanos)/1_000_000L;}
        List<String> pendingChunks(){
            List<String> result=new ArrayList<>();
            for(var p:lease.positions()){
                boolean chunk=level.getChunkSource().getChunkNow(p.x,p.z)!=null,entities=level.areEntitiesLoaded(p.toLong());
                if(!chunk||!entities)result.add(p+":chunk="+chunk+",entities="+entities);
            }
            return List.copyOf(result);
        }
        public ServerLevel level(){return level;}public String worldId(){return worldId;}public String city(){return city;}public UUID owner(){return owner;}
        public void guard()throws IOException {if(!allowed)throw new IOException("Fixture caller authorization revoked");}
        public void flush()throws IOException {
            if(failFlush)throw new IOException("Injected gate source/target save failure");
            if(flushes==0)sourceSavedBeforeIntent=!Files.exists(path());level.getChunkSource().save(true);flushes++;
        }
        Path path()throws IOException{return GateTransition35.journalPath(this,contract);}
        CompoundTag journal()throws IOException{return ProducerJournal.read(path());}
        GateTransition35.Runner begin(GateTransition35.State target,UUID request)throws IOException{return GateTransition35.begin(this,contract,target,request);}
        void finish(GateTransition35.State target,UUID id)throws IOException {try(var runner=begin(target,id)){for(int i=0;i<4&&!runner.done();i++)runner.step();if(!runner.done())throw new IOException("Bounded six-cell fixture did not complete");}}
        int count(GateTransition35.State state){int count=0;for(var cell:cells)if(level.getBlockState(cell.pos()).equals(cell.state(state)))count++;return count;}
        boolean all(GateTransition35.State state){return count(state)==cells.size();}
        ItemEntity item(BlockPos p){ItemEntity item=new ItemEntity(level,p.getX()+.5,p.getY()+.15,p.getZ()+.5,new ItemStack(Items.EMERALD,3));item.setNoGravity(true);item.setDeltaMovement(Vec3.ZERO);level.addFreshEntity(item);return item;}
    }
}
