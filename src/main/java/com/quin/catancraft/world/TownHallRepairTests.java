package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.*;
import com.quin.catancraft.map.MapAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Native fixtures and real file-I/O reconciliation, NOT owner-world acceptance. */
@GameTestHolder("catancraft")
@PrefixGameTestTemplate(false)
public final class TownHallRepairTests {
    private static final int FLAGS=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS;
    private static final TownHallRepairTransaction.Action APPLY=TownHallRepairTransaction.Action.APPLY;
    private static final TownHallRepairTransaction.Action RESUME=TownHallRepairTransaction.Action.RESUME;
    private static final TownHallRepairTransaction.Action ROLLBACK=TownHallRepairTransaction.Action.ROLLBACK;
    @GameTest(template="townhall_state_empty",batch="repair",timeoutTicks=600)
    public static void th4_north_repair_no_charge_and_rollback(GameTestHelper h){ lifecycle(h,4,0); }
    @GameTest(template="townhall_state_empty",batch="repair",timeoutTicks=600)
    public static void th4_south_repair_no_charge_and_rollback(GameTestHelper h){ lifecycle(h,4,180); }
    @GameTest(template="townhall_state_empty",batch="repair",timeoutTicks=600)
    public static void th5_north_repair_no_charge_and_rollback(GameTestHelper h){ lifecycle(h,5,0); }
    @GameTest(template="townhall_state_empty",batch="repair",timeoutTicks=600)
    public static void th5_south_repair_no_charge_and_rollback(GameTestHelper h){ lifecycle(h,5,180); }
    @GameTest(template="townhall_state_empty",batch="repair_recovery",timeoutTicks=600)
    public static void th4_north_partial_repair_recovery(GameTestHelper h){ recover(h,4,0); }
    @GameTest(template="townhall_state_empty",batch="repair_recovery",timeoutTicks=600)
    public static void th4_south_partial_repair_recovery(GameTestHelper h){ recover(h,4,180); }
    @GameTest(template="townhall_state_empty",batch="repair_recovery",timeoutTicks=600)
    public static void th5_north_partial_repair_recovery(GameTestHelper h){ recover(h,5,0); }
    @GameTest(template="townhall_state_empty",batch="repair_recovery",timeoutTicks=600)
    public static void th5_south_partial_repair_recovery(GameTestHelper h){ recover(h,5,180); }
    @GameTest(template="townhall_state_empty",batch="repair_guards",timeoutTicks=600)
    public static void foreign_block_and_wrong_identity_refuse(GameTestHelper h){ guards(h); }
    @GameTest(template="townhall_state_empty",batch="repair_guards",timeoutTicks=600)
    public static void saved_token_interruption_resumes_without_charge(GameTestHelper h){ tokenRecovery(h); }
    @GameTest(template="townhall_state_empty",batch="repair_routes",timeoutTicks=600)
    public static void th4_r2_north_all_delivered_routes(GameTestHelper h){ routes(h,4,0); }
    @GameTest(template="townhall_state_empty",batch="repair_routes",timeoutTicks=600)
    public static void th4_r2_south_all_delivered_routes(GameTestHelper h){ routes(h,4,180); }
    @GameTest(template="townhall_state_empty",batch="repair_routes",timeoutTicks=600)
    public static void th5_r2_north_all_delivered_routes(GameTestHelper h){ routes(h,5,0); }
    @GameTest(template="townhall_state_empty",batch="repair_routes",timeoutTicks=600)
    public static void th5_r2_south_all_delivered_routes(GameTestHelper h){ routes(h,5,180); }

    @GameTest(template="townhall_state_empty",batch="repair_guards",timeoutTicks=300)
    public static void unapproved_world_cannot_repair(GameTestHelper h) {
        try {
            refuse(h,()->TownHallRepairService.authorize(h.getLevel().getServer()));
            h.succeed();
        }catch(Exception e){throw new RuntimeException(e);}
    }
    @GameTest(template="townhall_state_empty",batch="repair_guards",timeoutTicks=300)
    public static void revision_matrix_has_both_sources_and_corrected_targets(GameTestHelper h) {
        try {
            h.assertTrue(TownHallRevisions.revision(4,"th4@r1")==1,"Lost original source");
            h.assertTrue(TownHallRevisions.revision(4,"th4@r2")==2,"Lost repaired source");
            refuse(h,()->TownHallRevisions.revision(4,"th5@r1"));
            h.assertTrue(TownHallRevisions.token(5,2).equals("th5@r2"),"Wrong future target");
            h.succeed();
        }catch(Exception e){throw new RuntimeException(e);}
    }
    private static void lifecycle(GameTestHelper h,int tier,int rot) {
        try {
            Env e=new Env(h,tier,rot);e.prepare(1);
            var old=TownHallRevisions.plan(tier,1,e.anchor());
            var r2=TownHallRevisions.plan(tier,2,e.anchor());
            // Keep an ordinary opened door AND a missing original half.
            ArrayList<BlockPos> doors=new ArrayList<>();
            for(var c:old)if(c.state().getBlock() instanceof DoorBlock) {
                e.level().setBlock(c.position(),c.state().setValue(DoorBlock.OPEN,true),FLAGS);doors.add(c.position());
            }
            e.level().setBlock(doors.get(0),Blocks.AIR.defaultBlockState(),FLAGS);
            BlockState[] before=snapshot(e,old);
            CompoundTag ledger=e.ledger(),book=e.book();
            byte[] history=Files.readAllBytes(e.historyFile);
            var result=TownHallRepairTransaction.run(e,APPLY);
            h.assertTrue(result.writes()==584,"Repair did not write exactly 584 cells");
            h.assertTrue(e.token().equals(TownHallRevisions.token(tier,2)),"Wrong R2 token");
            assertPatch(h,e,old,r2,before);
            h.assertTrue(ledger.equals(e.ledger()) && book.equals(e.book()),"Repair changed money/level/book");
            byte[] journal=Files.readAllBytes(e.file());
            h.assertTrue(TownHallRepairTransaction.run(e,APPLY).writes()==0,"Duplicate apply mutated");
            h.assertTrue(Arrays.equals(journal,Files.readAllBytes(e.file())),"Duplicate apply rewrote journal");
            e.reload();
            h.assertTrue(e.token().equals(TownHallRevisions.token(tier,2)),"R2 token not on disk");
            // A legitimate book edit after commit must be retained by rollback, not reverted.
            var be=(LecternBlockEntity)e.level().getBlockEntity(old.get(TownHallRevisions.managerIndex()).position());
            ItemStack replacement=new ItemStack(Items.WRITABLE_BOOK);
            CompoundTag nt=new CompoundTag();ListTag pages=new ListTag();pages.add(StringTag.valueOf("Changed after repair"));
            nt.put("pages",pages);replacement.setTag(nt);be.setBook(replacement);
            CompoundTag editedBook=e.book();
            h.assertTrue(TownHallRepairTransaction.run(e,ROLLBACK).writes()==584,"Rollback write count");
            h.assertTrue(e.book().equals(editedBook),"Rollback replaced current book with stale snapshot");
            for(int i=0;i<old.size();i++) {
                if(i==TownHallRevisions.managerIndex())continue;
                h.assertTrue(e.level().getBlockState(old.get(i).position()).equals(before[i]),"Rollback lost actual before state");
            }
            byte[] rolled=Files.readAllBytes(e.file());
            h.assertTrue(TownHallRepairTransaction.run(e,ROLLBACK).writes()==0,"Repeated rollback changed world");
            h.assertTrue(Arrays.equals(rolled,Files.readAllBytes(e.file())),"Repeated rollback rewrote history");
            h.assertTrue(Arrays.equals(history,Files.readAllBytes(e.historyFile)),"Purchase history changed");
            h.assertTrue(ledger.equals(e.ledger()),"Economy or city changed");
            CatanCraft.LOGGER.info("TH_REPAIR_LIFECYCLE tier={} rotation={} writes=584 rollback=584 idempotence=PASS ledger=PASS book=PASS diskToken=PASS",tier,rot);
            h.succeed();
        }catch(Exception x){throw new RuntimeException("Repair lifecycle",x);}
    }
    private static void recover(GameTestHelper h,int tier,int rot) {
        try {
            Env e=new Env(h,tier,rot);e.prepare(1);
            CompoundTag ledger=e.ledger(),book=e.book();
            refuse(h,()->TownHallRepairTransaction.run(e,APPLY,(stage,n)->{if(stage.equals("write")&&n==7)throw new IOException("Injected interruption");}));
            h.assertTrue(TownHallRepairTransaction.phase(TownHallRepairTransaction.read(e.file()))==TownHallRepairTransaction.Phase.APPLYING,"Missing pending phase");
            e.reload();refuse(h,()->TownHallRepairTransaction.run(e,APPLY));
            h.assertTrue(TownHallRepairTransaction.run(e,RESUME).writes()==577,"Resume did not complete only remaining cells");
            e.reload();h.assertTrue(TownHallRepairTransaction.run(e,RESUME).writes()==0,"Committed resume must be no-op");
            refuse(h,()->TownHallRepairTransaction.run(e,ROLLBACK,(stage,n)->{if(stage.equals("rollback_write")&&n==9)throw new IOException("Interrupted rollback");}));
            e.reload();
            refuse(h,()->TownHallRepairTransaction.run(e,RESUME));
            h.assertTrue(TownHallRepairTransaction.run(e,ROLLBACK).writes()==575,"Partial rollback not reconciled");
            h.assertTrue(ledger.equals(e.ledger()) && book.equals(e.book()),"Recovery changed economic data/book");
            CatanCraft.LOGGER.info("TH_REPAIR_RECOVERY tier={} rotation={} partialForward=7 resume=577 partialReverse=9 rollback=575 diskReload=PASS",tier,rot);
            h.succeed();
        }catch(Exception x){throw new RuntimeException("Repair recovery",x);}
    }
    private static void guards(GameTestHelper h) {
        try {
            Env e=new Env(h,5,180);e.prepare(1);
            var old=TownHallRevisions.plan(5,1,e.anchor());var after=TownHallRevisions.plan(5,2,e.anchor());
            int[] mask=TownHallRevisions.mask(old,after);BlockPos p=old.get(mask[1]).position();
            e.level().setBlock(p,Blocks.CHEST.defaultBlockState(),FLAGS);
            refuse(h,()->TownHallRepairTransaction.run(e,APPLY));
            h.assertTrue(!Files.exists(e.file()) && e.level().getBlockState(p).is(Blocks.CHEST),"Foreign block was changed");
            e.level().setBlock(p,old.get(mask[1]).state(),FLAGS);
            var item=new net.minecraft.world.entity.item.ItemEntity(e.level(),p.getX()+.5,p.getY()+.5,p.getZ()+.5,new ItemStack(Items.DIAMOND));
            e.level().addFreshEntity(item);
            refuse(h,()->TownHallRepairTransaction.run(e,APPLY));
            h.assertTrue(!Files.exists(e.file()) && !item.isRemoved(),"Occupied repair was not refused");
            item.discard(); // Only the fixture entity created immediately above.
            refuse(h,()->TownHallRepairTransaction.run(e,APPLY,(s,n)->{if(s.equals("write")&&n==3)throw new IOException("stop");}));
            byte[] journal=Files.readAllBytes(e.file());
            e.identitySuffix="other-world";
            refuse(h,()->TownHallRepairTransaction.run(e,RESUME));
            e.identitySuffix="";
            e.level().setBlock(p,Blocks.CHEST.defaultBlockState(),FLAGS);
            refuse(h,()->TownHallRepairTransaction.run(e,ROLLBACK));
            h.assertTrue(Arrays.equals(journal,Files.readAllBytes(e.file())),"Refusal advanced history");
            // Restore only the intentional test tamper, never provide such a bypass in commands.
            e.level().setBlock(p,after.get(mask[1]).state(),FLAGS);
            byte[] corrupt=journal.clone();corrupt[corrupt.length-1]^=1;Files.write(e.file(),corrupt);
            refuse(h,()->TownHallRepairTransaction.run(e,RESUME));
            Files.write(e.file(),journal);
            h.assertTrue(TownHallRepairTransaction.run(e,RESUME).writes()==581,"Guard test recovery count");
            CatanCraft.LOGGER.info("TH_REPAIR_GUARDS foreignChest=PASS wrongIdentity=PASS thirdState=PASS checksum=PASS");
            h.succeed();
        }catch(Exception x){throw new RuntimeException("Repair guards",x);}
    }
    private static void tokenRecovery(GameTestHelper h) {
        try {
            Env e=new Env(h,4,0);e.prepare(1);CompoundTag ledger=e.ledger();
            refuse(h,()->TownHallRepairTransaction.run(e,APPLY,(s,n)->{if(s.equals("token_saved"))throw new IOException("after saved token");}));
            e.reload();
            h.assertTrue(e.token().equals("th4@r2"),"Token was not persisted");
            h.assertTrue(TownHallRepairTransaction.run(e,RESUME).writes()==0,"Resume rewrote completed physics");
            h.assertTrue(ledger.equals(e.ledger()),"Resume changed nation records");
            CatanCraft.LOGGER.info("TH_REPAIR_SAVE_BOUNDARY physicalCompleteTokenSaved=PASS resumeWrites=0 ledger=PASS");
            h.succeed();
        }catch(Exception x){throw new RuntimeException(x);}
    }
    interface Check { void run() throws Exception; }
    private static void refuse(GameTestHelper h,Check c) throws Exception {
        boolean refused=false;try{c.run();}catch(IOException|IllegalStateException expected){refused=true;CatanCraft.LOGGER.info("TH_REPAIR_EXPECTED_REFUSAL {}",expected.getMessage());}
        h.assertTrue(refused,"Expected fail-closed refusal");
    }
    private static BlockState[] snapshot(Env e,List<SchematicPlacementService.PlanCell> plan) {
        return plan.stream().map(c->e.level().getBlockState(c.position())).toArray(BlockState[]::new);
    }
    private static void assertPatch(GameTestHelper h,Env e,List<SchematicPlacementService.PlanCell> old,
            List<SchematicPlacementService.PlanCell> after,BlockState[] before) {
        for(int i=0;i<old.size();i++) {
            BlockState expected=old.get(i).state().equals(after.get(i).state())?before[i]:after.get(i).state();
            h.assertTrue(e.level().getBlockState(old.get(i).position()).equals(expected),"Sparse repair touched unrelated state at "+old.get(i).position());
        }
    }
    static final class Env implements TownHallRepairTransaction.Environment {
        final GameTestHelper h;final int tier;final MapAnchor anchor;final Path dir,historyFile;
        CatanSavedData data=new CatanSavedData();TerritoryData territory;UUID owner;String identitySuffix="";
        Env(GameTestHelper h,int tier,int rotation) throws IOException {
            this.h=h;this.tier=tier;
            BlockPos origin=h.absolutePos(new BlockPos(2,2,2));
            anchor=new MapAnchor("repair",origin.getX()+37,origin.getY()+2,origin.getZ()+(rotation==0?1:63),rotation);
            dir=h.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("data/th25-tests/"+UUID.randomUUID());
            Files.createDirectories(dir);historyFile=dir.resolve("purchase-history.fixture");
            Files.writeString(historyFile,"Immutable completed purchase fixture (not a player journal)");
            NationData nation=data.createNation("Fixture",UUID.randomUUID());owner=nation.id();
            nation.addResource(ResourceType.STEEL,321);
            territory=data.createTerritory("a","Fixture",ResourceType.WOOD);territory.setOwnerNationId(owner);territory.setCityLevel(tier);
        }
        void prepare(int revision) throws IOException {
            var cells=TownHallRevisions.plan(tier,revision,anchor);
            if(!(level().getServer() instanceof GameTestServer))throw new IOException("Town Hall fixture requires GameTestServer");
            var bounds=TownHallRepairTransaction.bounds(cells);
            for(int x=((int)bounds.minX-2)>>4;x<=((int)bounds.maxX+2)>>4;x++)
                for(int z=((int)bounds.minZ-2)>>4;z<=((int)bounds.maxZ+2)>>4;z++) {
                    level().setChunkForced(x,z,true);level().getChunk(x,z);
                }
            // Native template clearing can drop inventory items AFTER a BeforeBatch hook.
            // This is source-fixture construction, not a transaction or recovery path.
            for(var c:cells) {
                var previous=level().getBlockEntity(c.position());
                if(previous instanceof net.minecraft.world.Container container)container.clearContent();
                if(previous instanceof LecternBlockEntity lectern)lectern.setBook(ItemStack.EMPTY);
                level().setBlock(c.position(),c.state(),FLAGS);
            }
            // Only stale item drops within this fixture are removed, before the test adds
            // any deliberate occupancy entity. Transaction-created drops remain test failures.
            var stale=new ArrayList<net.minecraft.world.entity.item.ItemEntity>();
            for(var entity:level().getAllEntities())
                if(entity instanceof net.minecraft.world.entity.item.ItemEntity item
                    && item.getBoundingBox().intersects(bounds.inflate(2)))stale.add(item);
            for(var item:stale)item.discard();
            CatanCraft.LOGGER.info("TH_FIXTURE_SOURCE_READY tier={} rotation={} priorFixtureDrops={}",tier,anchor.rotation(),stale.size());
            BlockPos p=cells.get(TownHallRevisions.managerIndex()).position();
            ItemStack book=new ItemStack(Items.WRITABLE_BOOK);CompoundTag n=new CompoundTag();
            ListTag pages=new ListTag();pages.add(StringTag.valueOf("Preserve this management book"));
            n.put("pages",pages);book.setTag(n);((LecternBlockEntity)level().getBlockEntity(p)).setBook(book);
            token(TownHallRevisions.token(tier,revision));save();
        }
        public ServerLevel level(){return h.getLevel();}
        public MapAnchor anchor(){return anchor;}
        public int tier(){return tier;}
        public Path file(){return dir.resolve("repair.dat");}
        public CompoundTag identity(){
            CompoundTag n=new CompoundTag();n.putString("world",dir.toString()+identitySuffix);
            n.putString("owner",owner.toString());n.putString("city","a");n.putInt("tier",tier);
            n.putIntArray("anchor",new int[]{anchor.x(),anchor.y(),anchor.z(),anchor.rotation()});return n;
        }
        public CompoundTag history() throws IOException {
            CompoundTag n=new CompoundTag();n.putString("purchase",TownHallRevisions.sha(Files.readAllBytes(historyFile)));return n;
        }
        public CompoundTag ledger(){
            CompoundTag n=data.save(new CompoundTag());n.getCompound("placedMapAssets").putString("territory:a:town_hall","NORMALIZED");return n;
        }
        public String token(){return data.placedMapAsset("territory:a:town_hall");}
        public void token(String t){data.setPlacedMapAsset("territory:a:town_hall",t);}
        public void save() throws IOException {try(OutputStream out=Files.newOutputStream(dir.resolve("saved-data.nbt"))){NbtIo.writeCompressed(data.save(new CompoundTag()),out);}}
        void reload() throws IOException {try(InputStream in=Files.newInputStream(dir.resolve("saved-data.nbt"))){data=CatanSavedData.load(NbtIo.readCompressed(in));territory=data.territory("a");}}
        CompoundTag book(){return level().getBlockEntity(new BlockPos(anchor.x(),anchor.y()+3,anchor.z()+(anchor.rotation()==0?16:-16))).saveWithFullMetadata().copy();}
    }
    private static void routes(GameTestHelper h,int tier,int rotation) {
        try {
            Env e=new Env(h,tier,rotation);e.prepare(2);
            var plan=TownHallRevisions.plan(tier,2,e.anchor());
            for(var c:plan)if(c.state().getBlock() instanceof DoorBlock)
                e.level().setBlock(c.position(),c.state().setValue(DoorBlock.OPEN,true),FLAGS);
            List<double[][]> rows=deliveredPaths(tier);
            h.assertTrue(rows.size()==39,"Missing delivered route definitions");
            int count=0;
            for(double[][] row:rows) {
                List<double[]> points=new ArrayList<>();
                for(double[] a:row)points.add(world(e,a[0],a[1],a[2]));
                walk(h,e,points,"route "+count);count++;
                Collections.reverse(points);walk(h,e,points,"reverse route "+count);count++;
            }
            final int passed=count;
            h.runAfterDelay(5,()->{
                try {
                    h.assertTrue(TownHallRepairTransaction.run(e,TownHallRepairTransaction.Action.STATUS).writes()==0,"R2 delayed state drift");
                    CatanCraft.LOGGER.info("TH_R2_NATIVE_ROUTES tier={} rotation={} directionalRoutes={} sampleStep=1/32 nativeShapes=PASS delayedState=PASS",tier,rotation,passed);
                    h.succeed();
                }catch(Exception x){throw new RuntimeException(x);}
            });
        }catch(Exception x){throw new RuntimeException("Native R2 routes",x);}
    }
    // These 39 waypoint arrays per tier were independently compared against
    // every forward entry in Blender's delivered route files (all equal).
    private static List<double[][]> deliveredPaths(int tier) {
        int l=tier==4?7:5,r=tier==4?67:69,rear=tier==4?55:58;
        List<double[][]> out=new ArrayList<>();
        for(int x:new int[]{36,37,38})out.add(new double[][]{{x+.5,2,.5},{x+.5,5,16.5}});
        out.add(new double[][]{{37.5,2,.5},{37.5,5,16.5}});
        out.add(new double[][]{{37.5,5,16.5},{35.5,5,16.5},{35.5,5,25.5},{37.5,5,25.5}});
        for(int side=0;side<2;side++) {
            int center=side==0?l+10:r-10;double access=side==0?l+8.5:r-7.5;
            for(int x=center-1;x<=center+1;x++) {
                out.add(new double[][]{{x+.5,2,rear+5.5},{x+.5,5,rear-.5}});
                out.add(new double[][]{{x+.5,5,rear-.5},{access,5,rear-.5},{access,5,48.5},{37.5,5,48.5},{37.5,5,25.5}});
            }
        }
        for(int side=0;side<2;side++) {
            int lo=side==0?l+3:r-7;
            double mid=side==0?l+5.5:r-4.5,aside=side==0?l+8.5:r-7.5;
            double outer=side==0?l+1.5:r-.5,front=side==0?20.5:54.5;
            for(int x=lo;x<lo+5;x++)out.add(new double[][]{{x+.5,5,22.5},{x+.5,15,36.5}});
            out.add(new double[][]{{mid,15,35.5},{front,15,35.5},{front,15,21.5},{37.5,15,21.5},{37.5,15,20.5}});
            out.add(new double[][]{{mid,15,35.5},{aside,15,35.5},{aside,15,49.5},{37.5,15,49.5}});
            out.add(new double[][]{{outer,15,22.5},{outer,15,36.5},{mid,15,36.5}});
            out.add(new double[][]{{lo-.5,15,34.5},{lo+5.5,15,34.5}});
            out.add(new double[][]{{lo-.5,15,35.5},{lo+5.5,15,35.5}});
            out.add(new double[][]{{37.5,5,25.5},{37.5,5,21.5},{mid,5,21.5},{mid,5,22.5}});
        }
        return out;
    }
    private static double[] world(Env e,double x,double y,double z){
        int sign=e.anchor.rotation()==0?1:-1;
        // Continuous rotation is around block centers, not integer block corners.
        return new double[]{e.anchor.x()+0.5+sign*(x-37.5),e.anchor.y()+y-2,e.anchor.z()+0.5+sign*(z-1.5)};
    }
    private static AABB body(double x,double y,double z){return new AABB(x-.3,y+.00001,z-.3,x+.3,y+1.8,z+.3);}
    private static List<AABB> shapes(Env e,AABB region) {
        ArrayList<AABB> out=new ArrayList<>();
        for(BlockPos p:BlockPos.betweenClosed(BlockPos.containing(region.minX-1,region.minY-1,region.minZ-1),
                BlockPos.containing(region.maxX+1,region.maxY+1,region.maxZ+1)))
            for(AABB b:e.level().getBlockState(p).getCollisionShape(e.level(),p).toAabbs())
                out.add(b.move(p.getX(),p.getY(),p.getZ()));
        return out;
    }
    private static boolean clear(Env e,AABB body){for(AABB b:shapes(e,body))if(b.intersects(body))return false;return true;}
    private static void walk(GameTestHelper h,Env e,List<double[]> points,String name) {
        double[] start=points.get(0);double x=start[0],y=start[1],z=start[2];
        h.assertTrue(clear(e,body(x,y,z)),"Blocked route start "+name);
        for(int k=1;k<points.size();k++) {
            double[] end=points.get(k);double sx=x,sz=z;int steps=(int)Math.ceil(Math.hypot(end[0]-sx,end[2]-sz)*32);
            for(int j=1;j<=steps;j++) {
                double nx=sx+(end[0]-sx)*j/steps,nz=sz+(end[2]-sz)*j/steps,ny=Double.NEGATIVE_INFINITY;
                AABB floor=new AABB(nx-.3,y-.6001,nz-.3,nx+.3,y+.6001,nz+.3);
                for(AABB b:shapes(e,floor))if(b.maxX>floor.minX && b.minX<floor.maxX && b.maxZ>floor.minZ && b.minZ<floor.maxZ
                    && b.maxY>=y-.6001 && b.maxY<=y+.6001)ny=Math.max(ny,b.maxY);
                h.assertTrue(Double.isFinite(ny),"Unsupported route "+name+" at "+nx+","+y+","+nz);
                double high=Math.max(y,ny);
                AABB horizontal=body(x,high,z).minmax(body(nx,high,nz));
                AABB vertical=ny>y?body(x,y,z).minmax(body(x,ny,z)):body(nx,y,nz).minmax(body(nx,ny,nz));
                h.assertTrue(clear(e,horizontal)&&clear(e,vertical),"Native collision on "+name+" at "+nx+","+ny+","+nz);
                x=nx;y=ny;z=nz;
            }
            h.assertTrue(Math.abs(y-end[1])<.001,"Incorrect route end height "+name+": "+y+" expected "+end[1]);
        }
    }
}
