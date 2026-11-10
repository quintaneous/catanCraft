package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.world.SiegeRestoration39.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Explicit synthetic structural cells and a protected real barrel in marked
 * disposable worlds. Damage is simulated with no-drop AIR writes, not weapons.
 * These are storage/restoration tests, not city geometry or modpack acceptance. */
@GameTestHolder("catancraft_snapshot39") @PrefixGameTestTemplate(false)
public final class SiegeRestoration39Tests {
    private static final int FLAGS=2|16|32;
    private static final AtomicInteger SITES=new AtomicInteger();
    private SiegeRestoration39Tests() {}

    @GameTest(template="townhall_state_empty",batch="snapshot39_01",timeoutTicks=5000)
    public static void snapshot_keeps_actual_mixed_source_and_precedes_damage_intent(GameTestHelper h){run(h,f->{
        try(Session s=f.capture()) {
            h.assertTrue(s.read().phase()==Phase.CAPTURED&&s.read().missing()==0&&f.allOriginal(),"Snapshot wrote blocks or lost actual mixed sources");
            h.assertTrue(Files.exists(f.snapshot())&&!Files.exists(f.journal())&&f.savedBeforeSnapshot,"Snapshot or durable source ordering is missing");
            byte[] original=Files.readAllBytes(f.snapshot());s.arm(f.allCells());f.damage(f.allCells());s.startRestore();f.finish(s);
            h.assertTrue(f.allOriginal(),"Restoration substituted a reusable template for actual captured block states");
            same(h,original,f.snapshot(),"Restoration changed the immutable source snapshot");
        }
    });}

    @GameTest(template="townhall_state_empty",batch="snapshot39_02",timeoutTicks=5000)
    public static void four_write_slices_resume_only_missing_original_cells(GameTestHelper h){run(h,f->{
        byte[] original;try(Session s=f.capture()) {
            original=Files.readAllBytes(f.snapshot());s.arm(f.allCells());f.damage(f.allCells());s.startRestore();
            h.assertTrue(s.step()==4&&f.originalCount()==4&&!s.done(),"First restoration slice was not exactly four writes");
        }
        try(Session resumed=f.open()) {
            h.assertTrue(resumed.read().phase()==Phase.RESTORING&&resumed.read().missing()==4,"Reopen lost partial restoration intent");
            h.assertTrue(resumed.step()==4&&resumed.done()&&f.allOriginal(),"Resume rewrote finished cells or omitted missing cells");
        }
        same(h,original,f.snapshot(),"Partial resume changed the captured source");
    });}

    @GameTest(template="townhall_state_empty",batch="snapshot39_03",timeoutTicks=5000)
    public static void armed_subset_is_frozen_and_rejects_foreign_damage(GameTestHelper h){run(h,f->{
        Set<BlockPos> subset=Set.copyOf(f.cells.subList(0,2));
        try(Session s=f.capture()) {
            refuse(()->s.arm(Set.of(f.cells.get(0).offset(0,4,0))));
        }
        try(Session s=f.open()) {
            s.arm(subset);byte[] journal=Files.readAllBytes(f.journal());s.arm(subset);
            same(h,journal,f.journal(),"Identical arm request rewrote the damage permission");
            refuse(()->s.arm(f.allCells()));same(h,journal,f.journal(),"Changed damage subset replaced the original permission");
        }
        f.damage(Set.of(f.cells.get(7)));byte[] bytes=Files.readAllBytes(f.journal());refuse(f::open);
        h.assertTrue(f.originalCount()==7,"Unarmed damage was repaired or otherwise changed");same(h,bytes,f.journal(),"Unarmed damage changed history");
    });}

    @GameTest(template="townhall_state_empty",batch="snapshot39_04",timeoutTicks=5000)
    public static void snapshot_without_armed_intent_cannot_adopt_missing_blocks(GameTestHelper h){run(h,f->{
        try(Session s=f.capture()){h.assertTrue(s.read().phase()==Phase.CAPTURED,"Fixture capture failed");}
        byte[] original=Files.readAllBytes(f.snapshot());f.damage(Set.of(f.cells.get(7)));refuse(f::open);refuse(f::capture);
        h.assertTrue(f.originalCount()==7&&!Files.exists(f.journal()),"Unjournaled missing block was adopted or restored");
        same(h,original,f.snapshot(),"Unjournaled damage replaced its source snapshot");
    });}

    @GameTest(template="townhall_state_empty",batch="snapshot39_05",timeoutTicks=5000)
    public static void foreign_block_fluid_and_block_entity_at_damage_cell_are_not_overwritten(GameTestHelper h){run(h,f->{
        try(Session s=f.capture()){s.arm(f.allCells());}
        byte[] original=Files.readAllBytes(f.snapshot()),journal=Files.readAllBytes(f.journal());BlockPos p=f.cells.get(7);
        for(BlockState foreign:List.of(Blocks.GOLD_BLOCK.defaultBlockState(),Blocks.WATER.defaultBlockState(),Blocks.BARREL.defaultBlockState(),Blocks.CAVE_AIR.defaultBlockState())) {
            f.level.setBlock(p,foreign,FLAGS);refuse(f::open);
            h.assertTrue(f.level.getBlockState(p).equals(foreign)&&f.originalCount()==7,"Unknown damage-cell content was overwritten");
            same(h,original,f.snapshot(),"Foreign source rewrote snapshot");same(h,journal,f.journal(),"Foreign source rewrote permission");
            f.level.setBlock(p,f.originals.get(p),FLAGS);
        }
    });}

    @GameTest(template="townhall_state_empty",batch="snapshot39_06",timeoutTicks=5000)
    public static void protected_read_change_stops_the_next_slice_before_writes(GameTestHelper h){run(h,f->{
        Session s=f.capture();try(s) {
            s.arm(f.allCells());f.damage(f.allCells());s.startRestore();h.assertTrue(s.step()==4,"Missing partial fixture");
            BlockPos read=f.cells.get(0).below();f.level.setBlock(read,Blocks.GOLD_BLOCK.defaultBlockState(),FLAGS);
            byte[] journal=Files.readAllBytes(f.journal());refuse(s::step);
            h.assertTrue(f.originalCount()==4&&f.level.getBlockState(read).is(Blocks.GOLD_BLOCK),"Changed protected context allowed writes or was rolled back");
            same(h,journal,f.journal(),"Changed protected read advanced restoration receipt");
        }
    });}

    @GameTest(template="townhall_state_empty",batch="snapshot39_07",timeoutTicks=5000)
    public static void changed_inventory_is_preserved_and_blocks_restoration(GameTestHelper h){run(h,f->{
        try(Session s=f.capture()){s.arm(f.allCells());}
        f.damage(f.allCells());f.barrel().setItem(0,new ItemStack(Items.DIAMOND,11));f.barrel().setChanged();
        CompoundTag changed=f.barrelNbt();byte[] original=Files.readAllBytes(f.snapshot()),journal=Files.readAllBytes(f.journal());refuse(f::open);
        h.assertTrue(f.originalCount()==0&&f.barrelNbt().equals(changed)&&f.barrel().getItem(0).getCount()==11,"Changed inventory was rolled back or restoration proceeded");
        same(h,original,f.snapshot(),"Inventory change rewrote original snapshot");same(h,journal,f.journal(),"Inventory change rewrote permission");
    });}

    @GameTest(template="townhall_state_empty",batch="snapshot39_08",timeoutTicks=5000)
    public static void protected_inventory_and_nearby_item_entity_survive_restoration_exactly(GameTestHelper h){run(h,f->{
        ItemEntity item=f.item(f.cells.get(0).offset(0,0,-1));UUID id=item.getUUID();CompoundTag entity=item.saveWithoutId(new CompoundTag()).copy(),barrel=f.barrelNbt();
        try(Session s=f.capture()){s.arm(f.allCells());f.damage(f.allCells());s.startRestore();f.finish(s);}
        h.assertTrue(f.allOriginal()&&barrel.equals(f.barrelNbt())&&f.barrel().getItem(0).getCount()==7,"Protected barrel state or inventory changed");
        h.assertTrue(!item.isRemoved()&&id.equals(item.getUUID())&&entity.equals(item.saveWithoutId(new CompoundTag())),"Restoration moved, removed, duplicated or changed a nearby item entity");
    });}

    @GameTest(template="townhall_state_empty",batch="snapshot39_09",timeoutTicks=5000)
    public static void genuine_entity_obstruction_refuses_without_crushing_or_deleting(GameTestHelper h){run(h,f->{
        try(Session s=f.capture()){s.arm(f.allCells());f.damage(f.allCells());s.startRestore();}
        ItemEntity obstruction=f.item(f.cells.get(7));CompoundTag entity=obstruction.saveWithoutId(new CompoundTag()).copy();UUID id=obstruction.getUUID();
        byte[] journal=Files.readAllBytes(f.journal());
        refuse(()->{try(Session s=f.open()){s.step();}});
        h.assertTrue(f.originalCount()==0&&!obstruction.isRemoved()&&id.equals(obstruction.getUUID())&&entity.equals(obstruction.saveWithoutId(new CompoundTag())),"Obstruction was crushed, moved or deleted");
        same(h,journal,f.journal(),"Obstructed restoration advanced its receipt");
    });}

    @GameTest(template="townhall_state_empty",batch="snapshot39_10",timeoutTicks=5000)
    public static void current_authority_owner_world_and_siege_are_rechecked(GameTestHelper h){run(h,f->{
        try(Session s=f.capture()){s.arm(f.allCells());f.damage(f.allCells());s.startRestore();f.allowed=false;refuse(s::step);}f.allowed=true;
        byte[] original=Files.readAllBytes(f.snapshot()),journal=Files.readAllBytes(f.journal());UUID owner=f.owner,siege=f.siege;String world=f.world;
        f.owner=UUID.randomUUID();refuse(f::open);f.owner=owner;f.world="foreign-world";refuse(f::open);f.world=world;f.siege=UUID.randomUUID();refuse(f::open);f.siege=siege;
        try(Session s=f.open()){f.owner=UUID.randomUUID();refuse(s::step);}f.owner=owner;
        h.assertTrue(f.originalCount()==0,"Changed authority or identity restored blocks");same(h,original,f.snapshot(),"Changed identity rewrote snapshot");same(h,journal,f.journal(),"Changed identity rewrote restoration intent");
    });}

    @GameTest(template="townhall_state_empty",batch="snapshot39_11",timeoutTicks=5000)
    public static void original_plan_and_supported_structural_sources_are_required(GameTestHelper h){run(h,f->{
        Plan incomplete=new Plan(f.plan.revision(),f.cells,f.reads.subList(1,f.reads.size()));refuse(()->SiegeRestoration39.capture(f,incomplete));
        BlockPos first=f.cells.get(0);f.level.setBlock(first,Blocks.BARREL.defaultBlockState(),FLAGS);refuse(f::capture);f.level.setBlock(first,f.originals.get(first),FLAGS);
        h.assertTrue(!Files.exists(f.snapshot())&&!Files.exists(f.journal()),"Invalid mask or writable inventory created retained history");
        try(Session ignored=f.capture()){}byte[] original=Files.readAllBytes(f.snapshot());
        Plan changed=new Plan("changed-revision",f.cells,f.reads);refuse(()->SiegeRestoration39.open(f,changed));
        same(h,original,f.snapshot(),"Changed revision replaced the original plan");
    });}

    @GameTest(template="townhall_state_empty",batch="snapshot39_12",timeoutTicks=5000)
    public static void same_city_file_lock_excludes_competing_handles_and_releases_on_close(GameTestHelper h){run(h,f->{
        byte[] original;try(Session first=f.capture()) {
            original=Files.readAllBytes(f.snapshot());refuse(f::open);refuse(f::capture);
            h.assertTrue(first.read().phase()==Phase.CAPTURED,"Competing request disrupted the original handle");
        }
        try(Session reopened=f.open()){h.assertTrue(reopened.read().phase()==Phase.CAPTURED,"Closed handle retained its city lock");}
        same(h,original,f.snapshot(),"Lock contention or reopening rewrote the snapshot");
    });}

    @GameTest(template="townhall_state_empty",batch="snapshot39_13",timeoutTicks=5000)
    public static void changed_or_checksum_corrupted_snapshot_is_retained_and_refused(GameTestHelper h){run(h,f->{
        byte[] hostile;try(Session s=f.capture()) {
            byte[] original=Files.readAllBytes(f.snapshot());retain(f.snapshot(),"original",original);hostile=original.clone();hostile[hostile.length-1]^=1;Files.write(f.snapshot(),hostile);
            refuse(s::read);same(h,hostile,f.snapshot(),"Stale handle rewrote altered snapshot");
        }
        refuse(f::open);h.assertTrue(f.allOriginal(),"Corrupt snapshot caused block writes");same(h,hostile,f.snapshot(),"Fresh open replaced corrupt evidence");
    });}

    @GameTest(template="townhall_state_empty",batch="snapshot39_14",timeoutTicks=5000)
    public static void resealed_unknown_journal_fields_and_changed_bytes_refuse(GameTestHelper h){run(h,f->{
        byte[] hostile;try(Session s=f.capture()) {
            s.arm(f.allCells());CompoundTag altered=ProducerJournal.read(f.journal());altered.putString("inventedPermission","approved");ProducerJournal.write(f.journal(),altered,true);hostile=Files.readAllBytes(f.journal());
            refuse(s::read);same(h,hostile,f.journal(),"Current handle overwrote a changed journal");
        }
        refuse(f::open);h.assertTrue(f.allOriginal(),"Malformed sealed intent authorized block writes");same(h,hostile,f.journal(),"Fresh reader accepted or replaced noncanonical journal");
    });}

    @GameTest(template="townhall_state_empty",batch="snapshot39_15",timeoutTicks=5000)
    public static void lost_damage_history_does_not_authorize_recovery_or_recapture(GameTestHelper h){run(h,f->{
        try(Session s=f.capture()){s.arm(f.allCells());f.damage(f.allCells());s.startRestore();h.assertTrue(s.step()==4,"Missing partial fixture");}
        byte[] source=Files.readAllBytes(f.snapshot()),intent=Files.readAllBytes(f.journal());Path retained=f.journal().resolveSibling(f.journal().getFileName()+".retained-test-evidence");Files.move(f.journal(),retained);
        refuse(f::open);refuse(f::capture);
        h.assertTrue(f.originalCount()==4&&!Files.exists(f.journal()),"Lost history was reconstructed from mixed blocks");same(h,source,f.snapshot(),"Lost history caused recapture");same(h,intent,retained,"Lost history evidence changed");
    });}

    @GameTest(template="townhall_state_empty",batch="snapshot39_16",timeoutTicks=5000)
    public static void source_save_failure_creates_no_snapshot_or_damage_permission(GameTestHelper h){run(h,f->{
        f.failFlush=true;refuse(f::capture);
        h.assertTrue(f.allOriginal()&&!Files.exists(f.snapshot())&&!Files.exists(f.journal()),"Failed source save wrote a snapshot, permission or blocks");
        f.failFlush=false;try(Session s=f.capture()){h.assertTrue(s.read().phase()==Phase.CAPTURED,"Source-save retry lost the original capture opportunity");}
    });}

    @GameTest(template="townhall_state_empty",batch="snapshot39_17",timeoutTicks=5000)
    public static void failure_after_one_actual_write_reopens_original_pending_intent(GameTestHelper h){run(h,f->{
        try(Session s=f.capture()){s.arm(f.allCells());f.damage(f.allCells());s.startRestore();}
        byte[] original=Files.readAllBytes(f.snapshot());
        try(Session interrupted=f.open(point->{if(point==SavePoint.AFTER_BLOCK)throw new IOException("Injected post-block failure");})) {refuse(interrupted::step);refuse(interrupted::read);}
        h.assertTrue(f.originalCount()==1,"Fault did not occur after exactly one actual restore write");
        try(Session s=f.open()){h.assertTrue(s.read().phase()==Phase.RESTORING&&s.read().missing()==7,"Partial native write was not recognized through original intent");h.assertTrue(s.step()==4&&s.step()==3&&s.done(),"Partial-write recovery duplicated a write or lost remaining cells");}
        same(h,original,f.snapshot(),"Fault recovery changed the immutable source");
    });}

    @GameTest(template="townhall_state_empty",batch="snapshot39_18",timeoutTicks=5000)
    public static void world_save_failure_and_saved_world_before_receipt_are_recoverable(GameTestHelper h){run(h,f->{
        try(Session s=f.capture()){s.arm(f.allCells());f.damage(f.allCells());s.startRestore();}
        byte[] pending=Files.readAllBytes(f.journal());f.failFlush=true;
        try(Session s=f.open()){refuse(s::step);}f.failFlush=false;
        h.assertTrue(f.originalCount()==4,"Physical-save failure did not retain the bounded partial write");same(h,pending,f.journal(),"Failed physical save advanced restoration receipt");
        try(Session s=f.open(point->{if(point==SavePoint.AFTER_WORLD_SAVE)throw new IOException("Injected saved-world/pre-receipt failure");})){refuse(s::step);}
        h.assertTrue(f.allOriginal(),"Saved-world fault lost the actual remaining writes");same(h,pending,f.journal(),"Pre-receipt fault falsely advanced journal");
        try(Session s=f.open()){h.assertTrue(s.step()==0&&s.done(),"Saved original blocks were rewritten instead of reconciling pending completion");}
    });}

    @GameTest(template="townhall_state_empty",batch="snapshot39_19",timeoutTicks=5000)
    public static void snapshot_faults_leave_absence_or_one_immutable_complete_capture(GameTestHelper h){run(h,f->{
        refuse(()->f.capture(point->{if(point==SavePoint.BEFORE_SNAPSHOT)throw new IOException("Injected pre-snapshot failure");}));
        h.assertTrue(f.allOriginal()&&!Files.exists(f.snapshot())&&!Files.exists(f.journal()),"Pre-snapshot failure created history or wrote blocks");
        refuse(()->f.capture(point->{if(point==SavePoint.AFTER_SNAPSHOT)throw new IOException("Injected post-snapshot failure");}));
        h.assertTrue(f.allOriginal()&&Files.exists(f.snapshot())&&!Files.exists(f.journal()),"Post-snapshot failure did not retain exactly the captured source");
        byte[] original=Files.readAllBytes(f.snapshot());refuse(f::capture);
        try(Session s=f.open()){h.assertTrue(s.read().phase()==Phase.CAPTURED,"Complete ambiguous snapshot could not be reopened read-only");}
        same(h,original,f.snapshot(),"Reopening replaced the retained capture after fault");
    });}

    @GameTest(template="townhall_state_empty",batch="snapshot39_20",timeoutTicks=5000)
    public static void completed_restore_is_byte_inert_and_never_repairs_later_drift(GameTestHelper h){run(h,f->{
        try(Session s=f.capture()){s.arm(f.allCells());f.damage(f.allCells());s.startRestore();f.finish(s);}
        byte[] original=Files.readAllBytes(f.snapshot()),done=Files.readAllBytes(f.journal());int saves=f.flushes;
        try(Session s=f.open()) {
            h.assertTrue(s.done()&&s.startRestore().phase()==Phase.DONE&&s.step()==0,"Completed restore repeated its work");
            refuse(()->s.arm(f.allCells()));
        }
        h.assertTrue(f.flushes==saves&&f.allOriginal(),"Completed request repeated physical saves or block writes");same(h,done,f.journal(),"Completed request rewrote its receipt");same(h,original,f.snapshot(),"Completed request rewrote original source");
        f.damage(Set.of(f.cells.get(7)));refuse(f::open);h.assertTrue(f.originalCount()==7,"Completed history authorized repairing unrelated later damage");same(h,done,f.journal(),"Later drift rewrote completion");
    });}

    @GameTest(template="townhall_state_empty",batch="snapshot39_21",timeoutTicks=5000)
    public static void journal_fault_boundaries_preserve_durable_permission_and_completion(GameTestHelper h){run(h,f->{
        try(Session ignored=f.capture()){}byte[] original=Files.readAllBytes(f.snapshot());
        Fault before=point->{if(point==SavePoint.BEFORE_JOURNAL)throw new IOException("Injected pre-journal failure");};
        Fault after=point->{if(point==SavePoint.AFTER_JOURNAL)throw new IOException("Injected post-journal failure");};
        try(Session s=f.open(before)){refuse(()->s.arm(f.allCells()));refuse(s::read);}
        h.assertTrue(f.allOriginal()&&!Files.exists(f.journal()),"Pre-journal arm failure created damage permission");
        try(Session s=f.open(after)){refuse(()->s.arm(f.allCells()));refuse(s::read);}
        try(Session s=f.open()){h.assertTrue(s.read().phase()==Phase.ARMED,"Post-journal arm failure lost durable permission");}
        f.damage(f.allCells());byte[] armed=Files.readAllBytes(f.journal());
        try(Session s=f.open(before)){refuse(s::startRestore);}
        h.assertTrue(f.originalCount()==0,"Failed restore intent wrote blocks");same(h,armed,f.journal(),"Pre-journal restore failure replaced ARMED state");
        try(Session s=f.open(after)){refuse(s::startRestore);}
        try(Session s=f.open()){h.assertTrue(s.read().phase()==Phase.RESTORING&&s.step()==4,"Post-journal restore intent was not recoverable");}
        byte[] partial=Files.readAllBytes(f.journal());
        try(Session s=f.open(before)){refuse(s::step);}
        h.assertTrue(f.allOriginal(),"Pre-completion journal fault did not retain saved native writes");same(h,partial,f.journal(),"Pre-completion journal fault advanced receipt");
        try(Session s=f.open(after)){refuse(s::step);}
        int saves=f.flushes;byte[] done=Files.readAllBytes(f.journal());
        try(Session s=f.open()){h.assertTrue(s.done()&&s.step()==0,"Post-completion journal fault lost durable DONE");}
        h.assertTrue(f.flushes==saves&&f.allOriginal(),"Recovered DONE repeated saves or writes");same(h,done,f.journal(),"Recovered DONE rewrote its receipt");same(h,original,f.snapshot(),"Journal faults changed immutable source");
    });}

    @GameTest(template="townhall_state_empty",batch="snapshot39_22",timeoutTicks=5000)
    public static void replaced_lock_identity_and_symlinked_snapshot_refuse_without_mutation(GameTestHelper h){run(h,f->{
        byte[] original;try(Session s=f.capture()) {
            original=Files.readAllBytes(f.snapshot());Path lock=f.snapshot().resolveSibling(f.snapshot().getFileName()+".lock");
            Files.move(lock,lock.resolveSibling(lock.getFileName()+".retained-test-evidence"));Files.write(lock,new byte[0],StandardOpenOption.CREATE_NEW);
            refuse(s::read);same(h,original,f.snapshot(),"Replaced lock identity changed the source snapshot");
        }
        Path snapshot=f.snapshot(),retained=snapshot.resolveSibling(snapshot.getFileName()+".retained-test-evidence");Files.move(snapshot,retained);Files.createSymbolicLink(snapshot,retained);
        refuse(f::open);
        h.assertTrue(Files.isSymbolicLink(snapshot)&&f.allOriginal()&&!Files.exists(f.journal()),"Symlink refusal mutated blocks or path evidence");same(h,original,retained,"Symlink refusal changed retained snapshot target");
    });}

    private interface Attempt {void run()throws Exception;}
    private interface Body {void run(Fixture f)throws Exception;}
    private static void refuse(Attempt attempt)throws Exception {try{attempt.run();}catch(IOException expected){return;}throw new AssertionError("Expected guarded snapshot/restoration refusal");}
    private static void same(GameTestHelper h,byte[] before,Path path,String message)throws IOException {h.assertTrue(Arrays.equals(before,Files.readAllBytes(path)),message);}
    private static void retain(Path path,String suffix,byte[] bytes)throws IOException {Files.write(path.resolveSibling(path.getFileName()+"."+suffix+".retained-test-evidence"),bytes,StandardOpenOption.CREATE_NEW);}
    private static void run(GameTestHelper h,Body body){try{Fixture f=new Fixture(h);ready(h,f,body,0);}catch(Exception error){throw new RuntimeException(error);}}
    private static void ready(GameTestHelper h,Fixture f,Body body,int attempts){h.runAfterDelay(attempts==0?10:1,()->{
        try {
            f.lease.keepAlive();if(!f.lease.ready()) {
                if(attempts==400||attempts==4000)CatanCraft.LOGGER.warn("SNAPSHOT39_FIXTURE_WAIT attempts={} wallMillis={} pending={}",attempts,f.wallMillis(),f.pendingChunks());
                if(attempts>=4000)throw new IOException("Snapshot fixture entity chunks never became ready: "+f.pendingChunks());
                ready(h,f,body,attempts+1);return;
            }
            CatanCraft.LOGGER.info("SNAPSHOT39_FIXTURE_READY attempts={} wallMillis={} chunks={}",attempts,f.wallMillis(),f.lease.chunkCount());
            StreetscapeService.requireLoadedEntities(f.level,List.of(SiegeRestoration39.bounds(f.plan)));
            try{body.run(f);h.succeed();}finally{f.lease.close();}
        }catch(Exception error){f.lease.close();throw new RuntimeException(error);}
    });}

    private static final class Fixture implements Environment {
        final long started=System.nanoTime();final ServerLevel level;final List<BlockPos> cells,reads;final Map<BlockPos,BlockState> originals;final Plan plan;final SectorChunkLease lease;final BlockPos barrelPos;
        final String city="s39_"+UUID.randomUUID().toString().replace("-","");String world="synthetic-snapshot39-v1";UUID owner=UUID.randomUUID(),siege=UUID.randomUUID();boolean allowed=true,failFlush,savedBeforeSnapshot;int flushes;
        Fixture(GameTestHelper h)throws IOException {
            level=h.getLevel();if(!(level.getServer() instanceof GameTestServer))throw new IOException("Synthetic snapshot tests require GameTestServer");
            BlockPos origin=h.absolutePos(new BlockPos(3000+48*SITES.getAndIncrement(),10,3500));
            for(int x=-3;x<=10;x++)for(int y=-3;y<=3;y++)for(int z=-3;z<=3;z++) {
                BlockPos p=origin.offset(x,y,z);level.getChunkAt(p);level.setBlock(p,y==-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),FLAGS);
            }
            List<BlockState> states=List.of(Blocks.STONE_BRICKS.defaultBlockState(),Blocks.IRON_BLOCK.defaultBlockState(),Blocks.COBBLESTONE.defaultBlockState(),Blocks.BRICKS.defaultBlockState(),Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),Blocks.SMOOTH_STONE.defaultBlockState(),Blocks.WHITE_CONCRETE.defaultBlockState(),Blocks.POLISHED_ANDESITE.defaultBlockState());
            Map<BlockPos,BlockState> source=new LinkedHashMap<>();for(int x=0;x<8;x++){BlockPos p=origin.offset(x,0,0);source.put(p,states.get(x));level.setBlock(p,states.get(x),FLAGS);}originals=Map.copyOf(source);cells=source.keySet().stream().sorted(Comparator.comparingLong(BlockPos::asLong)).toList();
            barrelPos=origin.offset(0,0,1);level.setBlock(barrelPos,Blocks.BARREL.defaultBlockState(),FLAGS);barrel().setItem(0,new ItemStack(Items.EMERALD,7));barrel().setChanged();
            Set<BlockPos> context=new HashSet<>();for(BlockPos p:cells)for(Direction direction:Direction.values())if(!source.containsKey(p.relative(direction)))context.add(p.relative(direction));reads=context.stream().sorted(Comparator.comparingLong(BlockPos::asLong)).toList();
            plan=new Plan("synthetic-snapshot39-plan-v1",cells,reads);lease=SectorChunkLease.acquire(level,SiegeRestoration39.bounds(plan));
        }
        public ServerLevel level(){return level;}public String worldId(){return world;}public String city(){return city;}public UUID owner(){return owner;}public UUID siege(){return siege;}
        public void guard(Action action)throws IOException {if(!allowed)throw new IOException("Fixture caller authorization revoked");}
        public void flush()throws IOException {if(failFlush)throw new IOException("Injected native world-save failure");if(flushes==0)savedBeforeSnapshot=!Files.exists(snapshot())&&!Files.exists(journal());level.getChunkSource().save(true);flushes++;}
        Path snapshot()throws IOException{return SiegeRestoration39.snapshotPath(this);}Path journal()throws IOException{return SiegeRestoration39.journalPath(this);}
        Session capture()throws IOException{return SiegeRestoration39.capture(this,plan);}Session open()throws IOException{return SiegeRestoration39.open(this,plan);}
        Session capture(Fault fault)throws IOException{return SiegeRestoration39.fixture(this,plan,true,fault);}Session open(Fault fault)throws IOException{return SiegeRestoration39.fixture(this,plan,false,fault);}
        Set<BlockPos> allCells(){return Set.copyOf(cells);}void damage(Set<BlockPos> positions){for(BlockPos p:positions)level.setBlock(p,Blocks.AIR.defaultBlockState(),FLAGS);}
        int originalCount(){int n=0;for(BlockPos p:cells)if(level.getBlockState(p).equals(originals.get(p)))n++;return n;}boolean allOriginal(){return originalCount()==cells.size();}
        BarrelBlockEntity barrel(){return (BarrelBlockEntity)level.getBlockEntity(barrelPos);}CompoundTag barrelNbt(){return barrel().saveWithFullMetadata().copy();}
        void finish(Session s)throws IOException {for(int i=0;i<4&&!s.done();i++)s.step();if(!s.done())throw new IOException("Eight-cell fixture did not finish within four slices");}
        ItemEntity item(BlockPos p){ItemEntity item=new ItemEntity(level,p.getX()+.5,p.getY()+.15,p.getZ()+.5,new ItemStack(Items.EMERALD,3));item.setNoGravity(true);item.setDeltaMovement(Vec3.ZERO);level.addFreshEntity(item);return item;}
        long wallMillis(){return (System.nanoTime()-started)/1_000_000L;}
        List<String> pendingChunks(){List<String> pending=new ArrayList<>();for(var p:lease.positions()){boolean chunk=level.getChunkSource().getChunkNow(p.x,p.z)!=null,entities=level.areEntitiesLoaded(p.toLong());if(!chunk||!entities)pending.add(p+":chunk="+chunk+",entities="+entities);}return List.copyOf(pending);}
    }
}
