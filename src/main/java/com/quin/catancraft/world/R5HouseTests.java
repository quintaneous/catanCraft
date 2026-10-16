package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.map.MapAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Native complete-house context, sparse write, sealed disk history and injected recovery checks. */
@GameTestHolder("catancraft_houses") @PrefixGameTestTemplate(false)
public final class R5HouseTests {
    private static final int FLAGS=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS;
    private static final R5HouseRevisions.Revision R1=R5HouseRevisions.Revision.R1,R2=R5HouseRevisions.Revision.R2,R5=R5HouseRevisions.Revision.R5;
    private static final R5HouseTransaction.Action APPLY=R5HouseTransaction.Action.APPLY,RESUME=R5HouseTransaction.Action.RESUME,ROLLBACK=R5HouseTransaction.Action.ROLLBACK;
    @GameTest(template="townhall_state_empty",batch="houses01",timeoutTicks=1600)
    public static void six_mixed_branches_north(GameTestHelper h){lifecycle(h,0);}
    @GameTest(template="townhall_state_empty",batch="houses02",timeoutTicks=1600)
    public static void six_mixed_branches_south(GameTestHelper h){lifecycle(h,180);}
    @GameTest(template="townhall_state_empty",batch="houses03",timeoutTicks=1600)
    public static void interrupted_forward_reverse_north(GameTestHelper h){recovery(h,0);}
    @GameTest(template="townhall_state_empty",batch="houses04",timeoutTicks=1600)
    public static void interrupted_forward_reverse_south(GameTestHelper h){recovery(h,180);}
    @GameTest(template="townhall_state_empty",batch="houses05",timeoutTicks=1600)
    public static void mixed_unjournaled_and_unowned_target_refuse(GameTestHelper h) {
        try {
            Env e=new Env(h,0);e.prepare();String last=R5HouseRevisions.groups().get(5);
            var change=R5HouseRevisions.changes(last,e.revisions.get(5),e.anchor()).get(0);e.level().setBlock(change.position(),change.after(),FLAGS);
            var before=e.snapshot();CompoundTag ledger=e.ledger();refuse(h,()->R5HouseTransaction.run(e,APPLY));
            h.assertTrue(!Files.exists(e.file())&&before.equals(e.snapshot())&&ledger.equals(e.ledger()),"Unjournaled mixture partially applied");
            e.prepare();for(String group:R5HouseRevisions.groups())for(var c:R5HouseRevisions.plan(group,R5,e.anchor()))e.level().setBlock(c.position(),c.state(),FLAGS);
            refuse(h,()->R5HouseTransaction.run(e,APPLY));h.assertTrue(!Files.exists(e.file()),"Unowned target accepted as committed");h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    @GameTest(template="townhall_state_empty",batch="houses06",timeoutTicks=1600)
    public static void full_context_foreign_state_outside_mask_refuses(GameTestHelper h) {
        try {
            Env e=new Env(h,180);e.prepare();String group=R5HouseRevisions.groups().get(0);
            Set<BlockPos> writes=new HashSet<>();for(var c:R5HouseRevisions.changes(group,e.revisions.get(0),e.anchor()))writes.add(c.position());
            BlockPos foreign=null;for(var c:R5HouseRevisions.plan(group,e.revisions.get(0),e.anchor()))if(c.state().isAir()&&!writes.contains(c.position())){foreign=c.position();break;}
            h.assertTrue(foreign!=null,"No unchanged source-air fixture");e.level().setBlock(foreign,Blocks.DIAMOND_BLOCK.defaultBlockState(),FLAGS);var before=e.snapshot();
            refuse(h,()->R5HouseTransaction.run(e,APPLY));h.assertTrue(before.equals(e.snapshot())&&!Files.exists(e.file()),"Full-context foreign air occupancy missed");h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    @GameTest(template="townhall_state_empty",batch="houses07",timeoutTicks=1600)
    public static void committed_drift_foreign_identity_corrupt_history_refuse(GameTestHelper h) {
        try {
            Env e=new Env(h,0);e.prepare();R5HouseTransaction.run(e,APPLY);byte[] intact=Files.readAllBytes(e.file());
            var c=R5HouseRevisions.changes(R5HouseRevisions.groups().get(0),e.revisions.get(0),e.anchor()).get(0);
            e.level().setBlock(c.position(),Blocks.DIAMOND_BLOCK.defaultBlockState(),FLAGS);var drift=e.snapshot();refuse(h,()->R5HouseTransaction.run(e,ROLLBACK));
            h.assertTrue(drift.equals(e.snapshot())&&Arrays.equals(intact,Files.readAllBytes(e.file())),"Drift refusal changed world/history");e.level().setBlock(c.position(),c.after(),FLAGS);
            e.identitySuffix="foreign";refuse(h,()->R5HouseTransaction.run(e,ROLLBACK));e.identitySuffix="";
            byte[] corrupt=intact.clone();corrupt[corrupt.length-3]^=1;Files.write(e.file(),corrupt);refuse(h,()->R5HouseTransaction.run(e,ROLLBACK));
            Files.write(e.file(),intact);R5HouseTransaction.run(e,ROLLBACK);e.assertSource();h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    @GameTest(template="townhall_state_empty",batch="houses08",timeoutTicks=1600)
    public static void final_target_native_delayed_survives(GameTestHelper h) {
        try {
            Env e=new Env(h,180);e.prepare();R5HouseTransaction.run(e,APPLY);
            h.runAfterDelay(10,()->{try{e.assertTarget();R5HouseTransaction.run(e,R5HouseTransaction.Action.STATUS);h.succeed();}catch(Exception ex){throw new RuntimeException(ex);}});
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    @GameTest(template="townhall_state_empty",batch="houses09",timeoutTicks=1600)
    public static void saved_physical_completion_recovers_without_duplicate_writes(GameTestHelper h) {
        try {
            Env e=new Env(h,0);e.prepare();var ledger=e.ledger();byte[] book=e.externalBook();
            refuse(h,()->R5HouseTransaction.run(e,APPLY,(s,n)->{if(s.equals("physical_saved"))throw new IOException("Saved physical completion window");}));
            h.assertTrue(R5HouseTransaction.phase(ProducerJournal.read(e.file()))==R5HouseTransaction.Phase.PHYSICAL_COMPLETE,"Missing durable physical completion phase");
            refuse(h,()->R5HouseTransaction.run(e,APPLY));
            h.assertTrue(R5HouseTransaction.run(e,RESUME).writes()==0,"Physical completion recovery duplicated writes");e.assertTarget();
            h.assertTrue(ledger.equals(e.ledger())&&Arrays.equals(book,e.externalBook()),"Saved completion changed ledger/shell book NBT");
            R5HouseTransaction.run(e,ROLLBACK);e.assertSource();h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    @GameTest(template="townhall_state_empty",batch="houses10",timeoutTicks=1600)
    public static void all_six_direct_r1_routes_native(GameTestHelper h){baseline(h,0,R1,2970);}
    @GameTest(template="townhall_state_empty",batch="houses11",timeoutTicks=1600)
    public static void all_six_staged_r2_routes_native(GameTestHelper h){baseline(h,180,R2,1602);}
    private static void baseline(GameTestHelper h,int rotation,R5HouseRevisions.Revision source,int count) {
        try {
            Env e=new Env(h,rotation);e.revisions.replaceAll(r->source);e.prepare();var before=e.snapshot();
            h.assertTrue(R5HouseTransaction.run(e,APPLY).writes()==count,"Six exact baseline-route write count");e.assertTarget();
            h.assertTrue(R5HouseTransaction.run(e,ROLLBACK).writes()==count,"Six exact baseline-route reverse count");
            h.assertTrue(before.equals(e.snapshot()),"Baseline route reverse changed actual original");h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    private static void lifecycle(GameTestHelper h,int rotation) {
        try {
            Env e=new Env(h,rotation);e.prepare();var initial=e.snapshot();CompoundTag ledger=e.ledger();byte[] book=e.externalBook();
            h.assertTrue(R5HouseTransaction.run(e,APPLY).writes()==2161,"Mixed six-house delta must total2161");e.assertTarget();
            h.assertTrue(ledger.equals(e.ledger())&&Arrays.equals(book,e.externalBook()),"House migration altered ledger/book NBT");
            byte[] journal=Files.readAllBytes(e.file());h.assertTrue(R5HouseTransaction.run(e,APPLY).writes()==0,"House duplicate wrote blocks");
            h.assertTrue(Arrays.equals(journal,Files.readAllBytes(e.file())),"House duplicate rewrote journal");
            h.assertTrue(R5HouseTransaction.run(e,ROLLBACK).writes()==2161,"House inverse count");e.assertSource();
            h.assertTrue(initial.equals(e.snapshot())&&ledger.equals(e.ledger())&&Arrays.equals(book,e.externalBook()),"House rollback lost state/NBT");
            h.assertTrue(R5HouseTransaction.run(e,ROLLBACK).writes()==0,"House rollback duplicated");refuse(h,()->R5HouseTransaction.run(e,APPLY));
            CatanCraft.LOGGER.info("R5_HOUSES_NATIVE rotation={} groups=6 mixedBranches=PASS writes=2161 duplicate=PASS rollback=PASS ledgerBook=PASS",rotation);h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    private static void recovery(GameTestHelper h,int rotation) {
        try {
            Env e=new Env(h,rotation);e.prepare();var initial=e.snapshot();var ledger=e.ledger();
            refuse(h,()->R5HouseTransaction.run(e,APPLY,(s,n)->{if(s.equals("write")&&n==7)throw new IOException("House forward interruption");}));
            var pending=e.snapshot();byte[] history=Files.readAllBytes(e.file());refuse(h,()->R5HouseTransaction.run(e,APPLY));
            h.assertTrue(pending.equals(e.snapshot())&&Arrays.equals(history,Files.readAllBytes(e.file())),"Pending APPLY resumed or rewrote history");
            // A legitimate inventory edit after interruption must survive recovery and reverse recovery.
            var barrel=(net.minecraft.world.level.block.entity.BarrelBlockEntity)e.level().getBlockEntity(e.book);
            barrel.setItem(1,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD,11));byte[] currentBook=e.externalBook();
            h.assertTrue(R5HouseTransaction.run(e,RESUME).writes()==2154,"House forward remaining count");e.assertTarget();
            refuse(h,()->R5HouseTransaction.run(e,ROLLBACK,(s,n)->{if(s.equals("rollback_write")&&n==9)throw new IOException("House reverse interruption");}));
            h.assertTrue(R5HouseTransaction.run(e,ROLLBACK).writes()==2152,"House reverse remaining count");
            h.assertTrue(initial.equals(e.snapshot())&&ledger.equals(e.ledger())&&Arrays.equals(currentBook,e.externalBook()),"House recovery changed original world/ledger/current inventory NBT");h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    private interface Checked {void run()throws IOException;}
    private static void refuse(GameTestHelper h,Checked operation)throws IOException{boolean refused=false;try{operation.run();}catch(IOException expected){refused=true;}h.assertTrue(refused,"Expected guarded house refusal");}
    static final class Env implements R5HouseTransaction.Environment {
        final GameTestHelper h;final Path file;final MapAnchor anchor;final List<R5HouseRevisions.Revision> revisions=new ArrayList<>(List.of(R1,R2,R1,R2,R1,R2));String identitySuffix="";
        final CompoundTag ledger=new CompoundTag();final BlockPos book;final Set<BlockPos> allowed=new HashSet<>();
        Env(GameTestHelper h,int rotation)throws IOException {
            this.h=h;BlockPos origin=h.absolutePos(new BlockPos(2,2,2));anchor=new MapAnchor("settlement",origin.getX()+80,262,origin.getZ()+1,rotation);
            file=h.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("data/houses-tests/"+UUID.randomUUID()+".dat");Files.createDirectories(file.getParent());
            ledger.putString("owner","protected-owner");ledger.putLong("money",987654);ledger.putInt("producerLevel",5);ledger.putInt("townHall",3);
            book=R5HouseRevisions.plan(R5HouseRevisions.groups().get(0),R1,anchor).get(3*11*13).position().relative(rotation==0?net.minecraft.core.Direction.WEST:net.minecraft.core.Direction.EAST);
        }
        void prepare()throws IOException {
            if(!(level().getServer() instanceof GameTestServer))throw new IOException("Disposable GameTestServer required");
            var groups=R5HouseRevisions.groups();
            // Fixture-only high sky + outside shell isolation, never normalize authored states.
            Set<BlockPos> contexts=new HashSet<>();List<net.minecraft.world.phys.AABB> envelopes=new ArrayList<>();
            for(int g=0;g<groups.size();g++) {
                var full=R5HouseRevisions.plan(groups.get(g),revisions.get(g),anchor);
                for(var c:full)contexts.add(c.position());envelopes.add(TownHallRepairTransaction.bounds(full));
            }
            for(var bounds:envelopes) {
                for(int x=((int)bounds.minX-2)>>4;x<=((int)bounds.maxX+2)>>4;x++)for(int z=((int)bounds.minZ-2)>>4;z<=((int)bounds.maxZ+2)>>4;z++){level().setChunkForced(x,z,true);level().getChunk(x,z);}
                for(var p:BlockPos.betweenClosed((int)bounds.minX-2,258,(int)bounds.minZ-2,(int)bounds.maxX+1,308,(int)bounds.maxZ+1)) {
                    if(contexts.contains(p))continue;
                    var previous=level().getBlockEntity(p);if(previous instanceof net.minecraft.world.Container container)container.clearContent();
                    level().setBlock(p,Blocks.AIR.defaultBlockState(),FLAGS);
                }
            }
            for(int g=0;g<groups.size();g++) {
                var plan=R5HouseRevisions.plan(groups.get(g),revisions.get(g),anchor);var bounds=TownHallRepairTransaction.bounds(plan);
                for(int x=((int)bounds.minX-2)>>4;x<=((int)bounds.maxX+2)>>4;x++)for(int z=((int)bounds.minZ-2)>>4;z<=((int)bounds.maxZ+2)>>4;z++){level().setChunkForced(x,z,true);level().getChunk(x,z);}
                for(var c:plan){allowed.add(c.position());var be=level().getBlockEntity(c.position());if(be instanceof net.minecraft.world.Container container)container.clearContent();level().setBlock(c.position(),c.state(),FLAGS);}
            }
            level().getChunk(book);level().setBlock(book,Blocks.BARREL.defaultBlockState(),FLAGS);
            var barrel=(net.minecraft.world.level.block.entity.BarrelBlockEntity)level().getBlockEntity(book);barrel.clearContent();
            var stack=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WRITTEN_BOOK);stack.getOrCreateTag().putString("title","Actual management book");stack.getOrCreateTag().putString("author","Owner");stack.getOrCreateTag().putString("protectedPayload","historical paid progress");barrel.setItem(0,stack);
            // Fixture-only stale drops from previous template clear, before transaction starts.
            for(var entity:new ArrayList<>(level().getEntities((net.minecraft.world.entity.Entity)null,new net.minecraft.world.phys.AABB(anchor.x()-170,anchor.y()-4,anchor.z()-100,anchor.x()+170,anchor.y()+55,anchor.z()+100))))if(entity instanceof net.minecraft.world.entity.item.ItemEntity)entity.discard();
        }
        byte[] externalBook()throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();NbtIo.writeCompressed(level().getBlockEntity(book).saveWithFullMetadata(),out);return out.toByteArray();}
        Map<BlockPos,BlockState> snapshot()throws IOException{Map<BlockPos,BlockState> result=new HashMap<>();for(String group:R5HouseRevisions.groups())for(var c:R5HouseRevisions.plan(group,R5,anchor))result.put(c.position(),level().getBlockState(c.position()));return result;}
        void assertTarget()throws IOException{for(String group:R5HouseRevisions.groups())for(var c:R5HouseRevisions.plan(group,R5,anchor))h.assertTrue(c.state().equals(level().getBlockState(c.position())),"House target native drift "+c.position());}
        void assertSource()throws IOException{var groups=R5HouseRevisions.groups();for(int i=0;i<groups.size();i++)for(var c:R5HouseRevisions.plan(groups.get(i),revisions.get(i),anchor))h.assertTrue(c.state().equals(level().getBlockState(c.position())),"House rollback source drift "+c.position());}
        public net.minecraft.server.level.ServerLevel level(){return h.getLevel();}public Path file(){return file;}public MapAnchor anchor(){return anchor;}
        public CompoundTag identity(){CompoundTag n=new CompoundTag();n.putString("owner","owner"+identitySuffix);n.putString("world",file.getParent().toString());n.putIntArray("anchor",new int[]{anchor.x(),anchor.y(),anchor.z(),anchor.rotation()});return n;}
        public CompoundTag ledger(){return ledger.copy();}public void save()throws IOException{if(!level().getServer().saveEverything(true,true,true))throw new IOException("Native house fixture save failed");}
        public void allowWrite(BlockPos pos)throws IOException{if(!allowed.contains(pos))throw new IOException("House test write outside exact six contexts");}
    }
}
