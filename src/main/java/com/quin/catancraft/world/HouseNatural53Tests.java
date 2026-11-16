package com.quin.catancraft.world;

import com.quin.catancraft.map.MapAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Exact house-only grass evolution. Fixtures use native grass randomTick, never a dirt
 * normalization pass. Separate restart namespace reopens the actual saved world/journals. */
@GameTestHolder("catancraft_house_natural53") @PrefixGameTestTemplate(false)
public final class HouseNatural53Tests {
    private static final int FLAGS=2|16|32;
    private static final R5HouseRevisions.Revision R1=R5HouseRevisions.Revision.R1,R2=R5HouseRevisions.Revision.R2,R5=R5HouseRevisions.Revision.R5;
    private static final R5HouseTransaction.Action APPLY=R5HouseTransaction.Action.APPLY,STATUS=R5HouseTransaction.Action.STATUS,RESUME=R5HouseTransaction.Action.RESUME,ROLLBACK=R5HouseTransaction.Action.ROLLBACK;
    private interface Checked {void run()throws Exception;}
    private static void checked(GameTestHelper h,Checked work){try{work.run();h.succeed();}catch(Exception error){throw new RuntimeException(error);}}
    @GameTest(template="townhall_state_empty",batch="house_natural53_01",timeoutTicks=2400)
    public static void source_r1_decay_north(GameTestHelper h){checked(h,()->sourceDecay(h,0,R1,2970));}
    @GameTest(template="townhall_state_empty",batch="house_natural53_02",timeoutTicks=2400)
    public static void source_r1_decay_south(GameTestHelper h){checked(h,()->sourceDecay(h,180,R1,2970));}
    @GameTest(template="townhall_state_empty",batch="house_natural53_03",timeoutTicks=2400)
    public static void source_r2_decay_north(GameTestHelper h){checked(h,()->sourceDecay(h,0,R2,1602));}
    @GameTest(template="townhall_state_empty",batch="house_natural53_04",timeoutTicks=2400)
    public static void source_r2_decay_south(GameTestHelper h){checked(h,()->sourceDecay(h,180,R2,1602));}
    @GameTest(template="townhall_state_empty",batch="house_natural53_05",timeoutTicks=2400)
    public static void original_grass_journal_pending_and_reverse_north(GameTestHelper h){checked(h,()->pending(h,0));}
    @GameTest(template="townhall_state_empty",batch="house_natural53_06",timeoutTicks=2400)
    public static void original_grass_journal_pending_and_reverse_south(GameTestHelper h){checked(h,()->pending(h,180));}
    @GameTest(template="townhall_state_empty",batch="house_natural53_07",timeoutTicks=2400)
    public static void missing_wrong_exposed_cover_and_false_beforeimage_north(GameTestHelper h){checked(h,()->negatives(h,0));}
    @GameTest(template="townhall_state_empty",batch="house_natural53_08",timeoutTicks=2400)
    public static void missing_wrong_exposed_cover_and_false_beforeimage_south(GameTestHelper h){checked(h,()->negatives(h,180));}

    private static Guarded prepared(GameTestHelper h,int rotation,R5HouseRevisions.Revision revision)throws IOException {
        var original=new R5HouseTests.Env(h,rotation);original.revisions.replaceAll(r->revision);original.prepare();
        return new Guarded(h,original,revision,original.book);
    }
    private static void sourceDecay(GameTestHelper h,int rotation,R5HouseRevisions.Revision revision,int count)throws Exception {
        Guarded e=prepared(h,rotation,revision);e.decay();var source=e.snapshot();var ledger=e.ledger();var book=e.book();
        h.assertTrue(R5HouseTransaction.run(e,STATUS).writes()==0&&!Files.exists(e.file()),"Source status changed world or made history");
        h.assertTrue(R5HouseTransaction.run(e,APPLY).writes()==count,"Natural soil enlarged or shrank the immutable house route");e.assertGeometry(true);
        CompoundTag history=ProducerJournal.read(e.file());assertBeforeSoil(h,e,history,"minecraft:dirt");
        byte[] committed=Files.readAllBytes(e.file());
        for(var action:List.of(STATUS,APPLY,RESUME))h.assertTrue(R5HouseTransaction.run(e,action).writes()==0,"Committed house soil was repainted");
        h.assertTrue(Arrays.equals(committed,Files.readAllBytes(e.file())),"Committed status/reopen rewrote original history");
        h.assertTrue(R5HouseTransaction.run(e,ROLLBACK).writes()==count,"Reverse gained natural soil writes");e.assertGeometry(false);
        CompoundTag rolled=ProducerJournal.read(e.file());sameBefore(h,history,rolled);
        h.assertTrue(source.equals(e.snapshot())&&ledger.equals(e.ledger())&&book.equals(e.book()),"Actual dirt preimage/ledger/book changed on rollback");
        h.assertTrue(R5HouseTransaction.run(e,ROLLBACK).writes()==0,"Duplicate reverse wrote soil");
    }
    private static void pending(GameTestHelper h,int rotation)throws Exception {
        Guarded e=prepared(h,rotation,R1);int stop=R5HouseRevisions.changes(R5HouseRevisions.groups().get(0),R1,e.anchor()).size()+7;
        interrupt(h,()->R5HouseTransaction.run(e,APPLY,(stage,count)->{if(stage.equals("write")&&count==stop)throw new IOException("house-natural53-injected");}));
        CompoundTag original=ProducerJournal.read(e.file());assertBeforeSoil(h,e,original,"minecraft:grass_block[snowy=false]");e.decay();
        byte[] pending=Files.readAllBytes(e.file());var snapshot=e.snapshot();var book=e.book();var ledger=e.ledger();
        h.assertTrue(R5HouseTransaction.run(e,STATUS).writes()==0,"Pending soil status wrote blocks");
        refuse(h,()->R5HouseTransaction.run(e,APPLY));h.assertTrue(Arrays.equals(pending,Files.readAllBytes(e.file()))&&snapshot.equals(e.snapshot()),"Pending APPLY or status rewrote retained work");
        h.assertTrue(R5HouseTransaction.run(e,RESUME).writes()==2970-stop,"Resume repeated a floor write or wrote soil");e.assertGeometry(true);sameBefore(h,original,ProducerJournal.read(e.file()));
        interrupt(h,()->R5HouseTransaction.run(e,ROLLBACK,(stage,count)->{if(stage.equals("rollback_write")&&count==9)throw new IOException("house-natural53-injected");}));
        h.assertTrue(R5HouseTransaction.run(e,ROLLBACK).writes()==2961,"Reverse recovery repeated writes or painted grass");e.assertGeometry(false);
        sameBefore(h,original,ProducerJournal.read(e.file()));h.assertTrue(book.equals(e.book())&&ledger.equals(e.ledger()),"Natural-state recovery changed book/ledger");
    }
    private static void negatives(GameTestHelper h,int rotation)throws Exception {
        Guarded e=prepared(h,rotation,R1);e.decay();BlockPos p=e.soil.iterator().next(),cover=p.above();BlockState original=e.level().getBlockState(cover);
        // Every soil in this source is covered. Expose one, substitute another solid
        // floor, then a block entity; none can authorize dirt by mere opacity.
        for(BlockState wrong:List.of(Blocks.AIR.defaultBlockState(),Blocks.DIAMOND_BLOCK.defaultBlockState(),Blocks.BARREL.defaultBlockState())){
            e.level().setBlock(cover,wrong,FLAGS);var snapshot=e.snapshot();refuse(h,()->R5HouseTransaction.run(e,APPLY));
            h.assertTrue(!Files.exists(e.file())&&snapshot.equals(e.snapshot()),"Unapproved cover started a house transaction");e.level().setBlock(cover,original,FLAGS);
        }
        BlockPos air=e.source.entrySet().stream().filter(row->row.getValue().isAir()).map(Map.Entry::getKey).findFirst().orElseThrow();
        e.level().setBlock(air,Blocks.DIRT.defaultBlockState(),FLAGS);refuse(h,()->R5HouseTransaction.run(e,APPLY));
        h.assertTrue(!Files.exists(e.file()),"Dirt outside original grass was normalized");e.level().setBlock(air,e.source.get(air),FLAGS);
        interrupt(h,()->R5HouseTransaction.run(e,APPLY,(stage,count)->{if(stage.equals("prepared"))throw new IOException("house-natural53-injected");}));
        byte[] intact=Files.readAllBytes(e.file());CompoundTag saved=ProducerJournal.read(e.file());
        BlockPos changedFloor=e.writeMask.stream().filter(pos->e.source.get(pos).is(Blocks.COBBLESTONE)&&e.target.get(pos).is(Blocks.STONE_BRICKS)&&e.soil.contains(pos.below())).findFirst().orElseThrow();
        // A future target floor is not evidence that the original source covered dirt.
        for(Tag value:saved.getList("actualBefore",Tag.TAG_COMPOUND)){
            CompoundTag row=(CompoundTag)value;if(row.getLong("pos")==changedFloor.asLong())row.putString("state","minecraft:stone_bricks");
        }
        ProducerJournal.write(e.file(),saved,true);byte[] corrupt=Files.readAllBytes(e.file());var before=e.snapshot();
        refuse(h,()->R5HouseTransaction.run(e,RESUME));h.assertTrue(Arrays.equals(corrupt,Files.readAllBytes(e.file()))&&before.equals(e.snapshot()),"False source-floor proof was adopted or rewritten");
        Files.write(e.file(),intact);h.assertTrue(R5HouseTransaction.run(e,RESUME).writes()==2970,"Restored exact source proof did not resume original route");e.assertGeometry(true);
        // Committed dirt requires its actual target cover, even if a source floor
        // would have been legitimate earlier in this recorded transaction.
        e.level().setBlock(changedFloor,e.source.get(changedFloor),FLAGS);byte[] committed=Files.readAllBytes(e.file());
        refuse(h,()->R5HouseTransaction.run(e,STATUS));h.assertTrue(Arrays.equals(committed,Files.readAllBytes(e.file())),"Committed source-cover drift rewrote history");
        e.level().setBlock(changedFloor,e.target.get(changedFloor),FLAGS);R5HouseTransaction.run(e,STATUS);
    }
    private static void sameBefore(GameTestHelper h,CompoundTag original,CompoundTag current){
        h.assertTrue(original.getUUID("id").equals(current.getUUID("id"))&&original.get("actualBefore").equals(current.get("actualBefore"))
                &&original.get("branches").equals(current.get("branches"))&&original.get("identity").equals(current.get("identity")),"Original house transaction/before-images were reinterpreted");
    }
    private static void assertBeforeSoil(GameTestHelper h,Guarded e,CompoundTag journal,String state){
        int count=0;for(Tag value:journal.getList("actualBefore",Tag.TAG_COMPOUND)){CompoundTag row=(CompoundTag)value;if(e.soil.contains(BlockPos.of(row.getLong("pos")))){h.assertTrue(row.getString("state").equals(state),"Actual soil before-image was normalized");count++;}}
        h.assertTrue(count==858,"Incomplete six-house original soil image");
    }
    private static void refuse(GameTestHelper h,Checked action)throws Exception {boolean refused=false;try{action.run();}catch(IOException expected){refused=true;}h.assertTrue(refused,"Expected exact house source refusal");}
    private static void interrupt(GameTestHelper h,Checked action)throws Exception {try{action.run();throw new AssertionError("Missing injected interruption");}catch(IOException expected){h.assertTrue(expected.getMessage().equals("house-natural53-injected"),"Unexpected house fault: "+expected);}}

    /** Guards the existing adapter's ACTUAL write authorization, not just reported counts. */
    private static final class Guarded implements R5HouseTransaction.Environment {
        final GameTestHelper h;final R5HouseTransaction.Environment original;final BlockPos bookPos;
        final Map<BlockPos,BlockState> source=new HashMap<>(),target=new HashMap<>();final Set<BlockPos> soil=new HashSet<>(),writeMask=new HashSet<>();
        Guarded(GameTestHelper h,R5HouseTransaction.Environment original,R5HouseRevisions.Revision revision,BlockPos book)throws IOException {
            this.h=h;this.original=original;bookPos=book;
            for(String group:R5HouseRevisions.groups()){
                for(var c:R5HouseRevisions.plan(group,revision,anchor()))source.put(c.position(),c.state());
                for(var c:R5HouseRevisions.plan(group,R5,anchor()))target.put(c.position(),c.state());
            }
            for(var row:source.entrySet()){
                if(!row.getValue().equals(target.get(row.getKey())))writeMask.add(row.getKey());
                if(row.getValue().equals(Blocks.GRASS_BLOCK.defaultBlockState())&&row.getValue().equals(target.get(row.getKey())))soil.add(row.getKey());
            }
            h.assertTrue(soil.size()==858&&Collections.disjoint(soil,writeMask),"Pinned house soil/routes changed");
        }
        void decay(){for(BlockPos p:soil){h.assertTrue(level().getBlockState(p).is(Blocks.GRASS_BLOCK),"Fixture was not original grass before native tick");Blocks.GRASS_BLOCK.randomTick(level().getBlockState(p),level(),p,RandomSource.create(53));h.assertTrue(level().getBlockState(p).equals(Blocks.DIRT.defaultBlockState()),"Native opaque-floor grass did not decay");}}
        Map<BlockPos,BlockState> snapshot(){Map<BlockPos,BlockState> out=new HashMap<>();for(BlockPos p:source.keySet())out.put(p,level().getBlockState(p));return out;}
        void assertGeometry(boolean built){for(var row:(built?target:source).entrySet())h.assertTrue(level().getBlockState(row.getKey()).equals(soil.contains(row.getKey())?Blocks.DIRT.defaultBlockState():row.getValue()),"House geometry/readonly soil drift at "+row.getKey());}
        CompoundTag book(){var be=level().getBlockEntity(bookPos);h.assertTrue(be!=null,"Preserved native management book barrel is missing");return be.saveWithFullMetadata().copy();}
        AABB bounds(){int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;for(BlockPos p:source.keySet()){minX=Math.min(minX,p.getX());minY=Math.min(minY,p.getY());minZ=Math.min(minZ,p.getZ());maxX=Math.max(maxX,p.getX());maxY=Math.max(maxY,p.getY());maxZ=Math.max(maxZ,p.getZ());}return new AABB(minX-3,minY-3,minZ-3,maxX+4,maxY+4,maxZ+4);}
        public ServerLevel level(){return original.level();}public Path file(){return original.file();}public MapAnchor anchor(){return original.anchor();}
        public CompoundTag identity(){return original.identity();}public CompoundTag ledger(){return original.ledger();}public void save()throws IOException{original.save();}
        public void allowWrite(BlockPos p)throws IOException{if(soil.contains(p)||!writeMask.contains(p))throw new IOException("House natural test forbids soil/context writes");original.allowWrite(p);}
    }

    @GameTestHolder("catancraft_house_natural53_restart") @PrefixGameTestTemplate(false)
    public static final class Restart {
        @GameTest(template="townhall_state_empty",batch="house_natural53_restart",timeoutTicks=4800)
        public static void committed_original_grass_two_orientations_process_restart(GameTestHelper h)throws Exception {
            String phase=System.getProperty("catancraft.natural53Phase",""),source=System.getProperty("catancraft.natural53Source","");
            h.assertTrue(Set.of("seed","verify").contains(phase)&&source.matches("[0-9a-f]{40}"),"Explicit exact-source seed/verify phase required");
            List<RestartEnv> environments=new ArrayList<>();List<Guarded> fixtures=new ArrayList<>();List<SectorChunkLease> leases=new ArrayList<>();
            try{
                for(int rotation:List.of(0,180)){RestartEnv e=new RestartEnv(h,rotation);Guarded fixture=new Guarded(h,e,R1,e.book);environments.add(e);fixtures.add(fixture);leases.add(SectorChunkLease.acquire(h.getLevel(),fixture.bounds()));}
                await(h,leases,0,()->{
                    for(int i=0;i<fixtures.size();i++){
                        RestartEnv e=environments.get(i);Guarded fixture=fixtures.get(i);
                        if(phase.equals("seed"))seed(h,e,fixture,source);else verify(h,e,fixture,source);
                    }
                });
            }catch(Exception failure){for(var lease:leases)lease.close();throw failure;}
        }
    }
    private static void await(GameTestHelper h,List<SectorChunkLease> leases,int ticks,Checked work){h.runAfterDelay(1,()->{
        try{
            for(var lease:leases)lease.keepAlive();
            if(leases.stream().anyMatch(lease->!lease.ready())){h.assertTrue(ticks<600,"Restart house fixture chunks/entities did not load");await(h,leases,ticks+1,work);return;}
            try{work.run();h.succeed();}finally{for(var lease:leases)lease.close();}
        }catch(Exception failure){for(var lease:leases)lease.close();throw new RuntimeException(failure);}
    });}
    private static void seed(GameTestHelper h,RestartEnv e,Guarded fixture,String source)throws Exception {
        h.assertTrue(!Files.exists(e.marker)&&!Files.exists(e.file()),"Refusing to overwrite a prior restart fixture");e.stage(fixture.source);
        h.assertTrue(R5HouseTransaction.run(fixture,APPLY).writes()==2970,"Restart seed route count changed");
        CompoundTag original=ProducerJournal.read(e.file());assertBeforeSoil(h,fixture,original,"minecraft:grass_block[snowy=false]");fixture.decay();fixture.assertGeometry(true);fixture.save();
        byte[] before=Files.readAllBytes(e.file());h.assertTrue(R5HouseTransaction.run(fixture,STATUS).writes()==0&&Arrays.equals(before,Files.readAllBytes(e.file())),"Committed native decay changed journal before restart");
        CompoundTag marker=new CompoundTag();marker.putInt("schema",1);marker.putString("source",source);marker.put("identity",e.identity());marker.putString("journalSHA256",TownHallRevisions.sha(before));marker.putUUID("id",original.getUUID("id"));marker.put("actualBefore",original.get("actualBefore").copy());marker.put("branches",original.get("branches").copy());marker.put("book",fixture.book());marker.put("ledger",fixture.ledger());ProducerJournal.write(e.marker,marker,false);
    }
    private static void verify(GameTestHelper h,RestartEnv e,Guarded fixture,String source)throws Exception {
        CompoundTag marker=ProducerJournal.read(e.marker);byte[] before=Files.readAllBytes(e.file());
        h.assertTrue(marker.getInt("schema")==1&&marker.getString("source").equals(source)&&marker.getCompound("identity").equals(e.identity())&&marker.getString("journalSHA256").equals(TownHallRevisions.sha(before)),"Restart source/identity/history changed");
        CompoundTag original=ProducerJournal.read(e.file());h.assertTrue(original.getUUID("id").equals(marker.getUUID("id"))&&original.get("actualBefore").equals(marker.get("actualBefore"))&&original.get("branches").equals(marker.get("branches")),"Restart replaced original journal/before-images");fixture.assertGeometry(true);
        for(var action:List.of(STATUS,APPLY,RESUME))h.assertTrue(R5HouseTransaction.run(fixture,action).writes()==0,"Restart duplicated migration or repainted readonly soil");
        h.assertTrue(Arrays.equals(before,Files.readAllBytes(e.file()))&&marker.getCompound("book").equals(fixture.book())&&marker.getCompound("ledger").equals(fixture.ledger()),"Restart changed history/book/ledger");
        h.assertTrue(R5HouseTransaction.run(fixture,ROLLBACK).writes()==2970,"Restart reverse wrote extra natural soil");fixture.assertGeometry(false);sameBefore(h,original,ProducerJournal.read(e.file()));
        h.assertTrue(R5HouseTransaction.run(fixture,ROLLBACK).writes()==0,"Restart duplicate reverse repeated writes");
    }
    private static final class RestartEnv implements R5HouseTransaction.Environment {
        final GameTestHelper h;final MapAnchor anchor;final Path file,marker;final BlockPos book;final CompoundTag ledger=new CompoundTag();final Set<BlockPos> allowed=new HashSet<>();
        RestartEnv(GameTestHelper h,int rotation)throws IOException {
            this.h=h;if(!(h.getLevel().getServer() instanceof GameTestServer))throw new IOException("Native disposable GameTestServer required");
            anchor=new MapAnchor("settlement",2000+(rotation==0?0:512),262,2000,rotation);
            Path root=h.getLevel().getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            if(!root.getParent().getFileName().toString().equals("controlled-catancraft_house_natural53_restart-run"))throw new IOException("Wrong isolated house restart world");
            Path dir=root.resolve("data/house-natural53-restart");Files.createDirectories(dir);file=dir.resolve("houses-"+rotation+".dat");marker=dir.resolve("seed-"+rotation+".dat");
            var first=R5HouseRevisions.plan(R5HouseRevisions.groups().get(0),R1,anchor).get(3*11*13).position();book=first.relative(rotation==0?net.minecraft.core.Direction.WEST:net.minecraft.core.Direction.EAST);
            for(String group:R5HouseRevisions.groups())for(var cell:R5HouseRevisions.plan(group,R1,anchor))allowed.add(cell.position());
            ledger.putString("owner","HouseNatural53Restart");ledger.putLong("money",987654);ledger.putInt("rotation",rotation);
        }
        void stage(Map<BlockPos,BlockState> source)throws IOException {
            for(var row:source.entrySet()){if(!level().hasChunkAt(row.getKey()))throw new IOException("Restart source chunk not leased");level().setBlock(row.getKey(),row.getValue(),FLAGS);}
            if(allowed.contains(book))throw new IOException("Restart book intersects house context");level().setBlock(book.below(),Blocks.GLASS.defaultBlockState(),FLAGS);level().setBlock(book,Blocks.BARREL.defaultBlockState(),FLAGS);
            ItemStack item=new ItemStack(Items.WRITTEN_BOOK);item.getOrCreateTag().putString("title","Retained house restart management book");item.getOrCreateTag().putString("author","Owner");((BarrelBlockEntity)level().getBlockEntity(book)).setItem(0,item);
        }
        public ServerLevel level(){return h.getLevel();}public Path file(){return file;}public MapAnchor anchor(){return anchor;}
        public CompoundTag identity(){CompoundTag id=new CompoundTag();id.putString("owner","HouseNatural53Restart");id.putString("world",file.getParent().toString());id.putIntArray("anchor",new int[]{anchor.x(),anchor.y(),anchor.z(),anchor.rotation()});return id;}
        public CompoundTag ledger(){return ledger.copy();}public void save()throws IOException{if(!level().getServer().saveEverything(true,true,true))throw new IOException("House restart fixture save failed");}
        public void allowWrite(BlockPos p)throws IOException{if(!allowed.contains(p))throw new IOException("House restart write outside pinned context");}
    }
}
