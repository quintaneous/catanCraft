package com.quin.catancraft.world;

import com.google.gson.*;
import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.*;
import com.quin.catancraft.economy.EconomyCost;
import com.quin.catancraft.map.MapAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Actual immutable P1 candidate geometry. These native fixtures approve no city, artwork or price. */
@GameTestHolder("catancraft_sectors_p1") @PrefixGameTestTemplate(false)
public final class SectorP1Tests {
    private static final int FLAGS=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS;
    @GameTest(template="townhall_state_empty",batch="p1_01",timeoutTicks=6000)
    public static void real_basic_upgrade_and_reverse_one_group_payment(GameTestHelper h) {
        try {
            Env e=new Env(h,0);e.stage();var source=e.scene();var nation=e.nation.save();var book=e.book();
            h.assertTrue(SectorTransaction.run(e,SectorTransaction.Action.APPLY).writes()==3878,"P1 basic fullside writes");
            h.assertTrue(e.progress==1&&e.nation.treasury()==9899&&e.nation.resource(ResourceType.STONE)==993,"P1 basic wholeprice charged incorrectly");
            byte[] history=Files.readAllBytes(e.file());h.assertTrue(SectorTransaction.run(e,SectorTransaction.Action.APPLY).writes()==0&&Arrays.equals(history,Files.readAllBytes(e.file())),"P1 duplicate changed history");
            String basicReceipt=e.receipt;var basicScene=e.scene();e.select(1);
            h.assertTrue(SectorTransaction.run(e,SectorTransaction.Action.APPLY).writes()==3582,"P1 exact upgrade delta");
            h.assertTrue(e.progress==2&&e.nation.treasury()==9798&&e.nation.resource(ResourceType.STONE)==986,"P1 upgrade did not charge once");
            h.assertTrue(SectorTransaction.run(e,SectorTransaction.Action.ROLLBACK).writes()==3582&&e.progress==1&&e.receipt.equals(basicReceipt)&&basicScene.equals(e.scene()),"P1 upgrade rollback lost owned basic sector");
            e.select(0);h.assertTrue(SectorTransaction.run(e,SectorTransaction.Action.ROLLBACK).writes()==3878,"P1 fullside inverse");
            h.assertTrue(source.equals(e.scene())&&nation.equals(e.nation.save())&&book.equals(e.book())&&e.progress==0,"P1 rollback lost actual source/money/book");
            h.assertTrue(SectorTransaction.run(e,SectorTransaction.Action.ROLLBACK).writes()==0&&nation.equals(e.nation.save()),"P1 repeated refund");
            CatanCraft.LOGGER.info("P1_NATIVE_FIXTURE basic=3878 upgrade=3582 exactReverse=PASS wholeSideAccounting=PASS artworkApproval=NONE");h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    @GameTest(template="townhall_state_empty",batch="p1_02",timeoutTicks=6000)
    public static void final_authored_component_conflict_is_grouped_and_free(GameTestHelper h) {
        try {
            Env e=new Env(h,0);e.stage();JsonArray components=SectorP1Fixture.metadata(e.level).getAsJsonArray("variants").get(0).getAsJsonObject().getAsJsonArray("components");
            JsonArray writes=components.get(components.size()-1).getAsJsonObject().getAsJsonArray("writes");JsonArray xyz=writes.get(writes.size()-1).getAsJsonObject().getAsJsonArray("localXYZ");
            BlockPos last=e.world(xyz.get(0).getAsInt(),xyz.get(1).getAsInt(),xyz.get(2).getAsInt());
            e.level.setBlock(last,Blocks.DIAMOND_BLOCK.defaultBlockState(),FLAGS);var before=e.scene();var money=e.nation.save();var book=e.book();
            refuse(()->SectorTransaction.run(e,SectorTransaction.Action.APPLY));
            h.assertTrue(!Files.exists(e.file())&&before.equals(e.scene())&&money.equals(e.nation.save())&&book.equals(e.book())&&e.progress==0,"Last P1 component conflict charged or partially installed side");h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    @GameTest(template="townhall_state_empty",batch="p1_03",timeoutTicks=6000)
    public static void exact_rotated_geometry_and_authored_road_masks(GameTestHelper h) {
        try {
            ServerLevel level=h.getLevel();MapAnchor north=new MapAnchor("P1",100,260,100,0),rotated=new MapAnchor("P1",100,260,100,180);
            JsonObject d=SectorP1Fixture.metadata(level);h.assertTrue(!d.get("approved").getAsBoolean()&&d.get("testOnly").getAsBoolean(),"P1 fixture approval gate lost");
            Set<String> forbidden=new HashSet<>();for(JsonElement e:d.getAsJsonArray("turnBufferLocalXZ")){JsonArray p=e.getAsJsonArray();forbidden.add(p.get(0).getAsInt()+","+p.get(1).getAsInt());}
            for(int source=0;source<=1;source++) {
                var a=SectorP1Fixture.plan(level,source,north);var b=SectorP1Fixture.plan(level,source,rotated);h.assertTrue(a.cells().size()==17721&&a.reads().size()>7000,"P1 fullread/context missing");int count=0;
                for(int i=0;i<a.cells().size();i++) {
                    var c=a.cells().get(i);var r=b.cells().get(i);
                    h.assertTrue(r.pos().getX()==200-c.pos().getX()&&r.pos().getZ()==200-c.pos().getZ()&&r.pos().getY()==c.pos().getY(),"Double-rotated P1 geometry");
                    h.assertTrue(r.after().equals(c.after().rotate(Rotation.CLOCKWISE_180))&&r.before().equals(c.before().rotate(Rotation.CLOCKWISE_180)),"P1 directional state rotated incorrectly");
                    if(!c.before().equals(c.after())) {
                        count++;int x=c.pos().getX()-north.x()+89,z=c.pos().getZ()-north.z()+6;
                        h.assertTrue(!forbidden.contains(x+","+z),"P1 write intersects authored road/two-block turn margin");
                        for(JsonElement protectedRow:d.getAsJsonObject("protectedEnvelopes").getAsJsonArray("envelopes")) {
                            JsonObject row=protectedRow.getAsJsonObject();JsonArray lo=row.getAsJsonArray("worldMinInclusive"),hi=row.getAsJsonArray("worldMaxInclusive");int wx=x-839,wy=c.pos().getY()-north.y()+81,wz=z-844;
                            h.assertTrue(!(wx>=lo.get(0).getAsInt()&&wx<=hi.get(0).getAsInt()&&wy>=lo.get(1).getAsInt()&&wy<=hi.get(1).getAsInt()&&wz>=lo.get(2).getAsInt()&&wz<=hi.get(2).getAsInt()),"P1 write intersects supplied historical protected envelope");
                        }
                    }
                }
                h.assertTrue(count==(source==0?3878:3582),"P1 exact rotated change count");
            }
            h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    @GameTest(template="townhall_state_empty",batch="p1_04",timeoutTicks=6000)
    public static void native_corner_coupons_absent_and_both_tiers(GameTestHelper h) {
        try {
            ServerLevel level=h.getLevel();JsonObject d=SectorP1Fixture.metadata(level);int cases=0;
            for(int rotation:new int[]{0,180})for(JsonElement e:d.getAsJsonArray("coupons")) {
                String id=e.getAsJsonObject().get("id").getAsString();MapAnchor anchor=new MapAnchor("coupon",h.absolutePos(BlockPos.ZERO).getX(),260,h.absolutePos(BlockPos.ZERO).getZ(),rotation);
                var plan=SectorP1Fixture.coupon(level,id,anchor);Set<BlockPos> positions=new HashSet<>();Map<BlockPos,BlockState> target=new LinkedHashMap<>();for(var c:plan){positions.add(c.position());target.put(c.position(),c.state());}
                var box=TownHallRepairTransaction.bounds(plan);
                for(int x=((int)box.minX-2)>>4;x<=((int)box.maxX+2)>>4;x++)for(int z=((int)box.minZ-2)>>4;z<=((int)box.maxZ+2)>>4;z++){level.setChunkForced(x,z,true);level.getChunk(x,z);}
                for(BlockPos p:BlockPos.betweenClosed((int)box.minX-1,259,(int)box.minZ-1,(int)box.maxX,270,(int)box.maxZ)){var be=level.getBlockEntity(p);if(be instanceof net.minecraft.world.Container container)container.clearContent();level.setBlock(p,p.getY()==259?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),FLAGS);}
                PlannedWorld.verify(level,target,FLAGS);for(var c:plan)level.setBlock(c.position(),c.state(),FLAGS);
                int sign=rotation==0?1:-1;boolean absent=id.endsWith("_absent");
                for(int x=3;x<=5;x++) {
                    BlockPos cap=new BlockPos(anchor.x()+sign*x,263,anchor.z()+sign*10);
                    h.assertTrue(absent?!level.getBlockState(cap).getCollisionShape(level,cap).isEmpty():level.getBlockState(cap).isAir(),"P1 cap opened without neighbor or remained closed with coupon deck");
                    if(!absent)for(int z=8;z<18;z++)for(int y=3;y<6;y++){BlockPos p=new BlockPos(anchor.x()+sign*x,260+y,anchor.z()+sign*z);h.assertTrue(level.getBlockState(p).isAir(),"P1 coupon circulation blocked");}
                    for(int z=8;z<(absent?11:18);z++){BlockPos deck=new BlockPos(anchor.x()+sign*x,262,anchor.z()+sign*z);h.assertTrue(level.getBlockState(deck).isCollisionShapeFullBlock(level,deck),"P1 corner deck missing before cap removal");}
                }
                cases++;
            }
            h.assertTrue(cases==24,"All12 corner coupons require both geometry rotations");h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    private interface Checked{void run()throws IOException;}
    private static void refuse(Checked c)throws IOException{try{c.run();}catch(IOException expected){return;}throw new AssertionError("Expected P1 fail-closed refusal");}
    static final class Env implements SectorTransaction.Environment {
        final ServerLevel level;final MapAnchor anchor;final Path directory;final BlockPos bookPos;final Set<BlockPos> allowed=new HashSet<>();SectorTransaction.Plan plan;int source,progress;String receipt="";NationData nation;
        Env(GameTestHelper h,int rotation)throws IOException {
            level=h.getLevel();if(!(level.getServer() instanceof GameTestServer))throw new IOException("Isolated P1 fixture server required");BlockPos origin=h.absolutePos(new BlockPos(2,2,2));anchor=new MapAnchor("P1",origin.getX()+89,260,origin.getZ()+6,rotation);bookPos=world(-1,0,2);
            directory=level.getServer().getWorldPath(LevelResource.ROOT).resolve("data/p1-fixture/"+UUID.randomUUID());Files.createDirectories(directory);nation=new NationData(UUID.randomUUID(),"P1_TEST_ONLY",UUID.randomUUID(),10000);nation.addResource(ResourceType.STONE,1000);select(0);
        }
        BlockPos world(int x,int y,int z){int sign=anchor.rotation()==0?1:-1;return new BlockPos(anchor.x()+sign*(x-89),anchor.y()+y,anchor.z()+sign*(z-6));}
        void select(int source)throws IOException {
            this.source=source;var p=SectorP1Fixture.plan(level,source,anchor);List<SectorTransaction.Read> reads=new ArrayList<>(p.reads());reads.add(new SectorTransaction.Read(bookPos,Blocks.BARREL.defaultBlockState()));plan=new SectorTransaction.Plan(p.sector(),p.sourceRevision(),p.targetRevision(),p.sourceLevel(),p.targetLevel(),p.cells(),reads);allowed.clear();for(var c:plan.cells())if(!c.before().equals(c.after()))allowed.add(c.pos());
        }
        void stage()throws IOException {
            Set<BlockPos> all=new HashSet<>();for(var c:plan.cells())all.add(c.pos());for(var r:plan.reads())all.add(r.pos());
            int minX=all.stream().mapToInt(BlockPos::getX).min().orElseThrow()-2,maxX=all.stream().mapToInt(BlockPos::getX).max().orElseThrow()+2,minZ=all.stream().mapToInt(BlockPos::getZ).min().orElseThrow()-2,maxZ=all.stream().mapToInt(BlockPos::getZ).max().orElseThrow()+2;
            for(int x=minX>>4;x<=maxX>>4;x++)for(int z=minZ>>4;z<=maxZ>>4;z++){level.setChunkForced(x,z,true);level.getChunk(x,z);}
            for(BlockPos p:BlockPos.betweenClosed(minX,258,minZ,maxX,270,maxZ)){var be=level.getBlockEntity(p);if(be instanceof net.minecraft.world.Container container)container.clearContent();level.setBlock(p,p.getY()==258?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),FLAGS);}
            for(var c:plan.cells())level.setBlock(c.pos(),c.before(),FLAGS);for(var r:plan.reads())level.setBlock(r.pos(),r.state(),FLAGS);
            ItemStack book=new ItemStack(Items.WRITTEN_BOOK);book.getOrCreateTag().putString("title","P1 actual owner book");book.getOrCreateTag().putString("author","Owner");book.getOrCreateTag().putString("payload","preserve paid progress");((BarrelBlockEntity)level.getBlockEntity(bookPos)).setItem(0,book);
            // Only remove stale fixture-setup drops before the transaction/its hazard guard starts.
            for(var entity:new ArrayList<>(level.getEntities((net.minecraft.world.entity.Entity)null,new net.minecraft.world.phys.AABB(minX,257,minZ,maxX+1,272,maxZ+1))))if(entity instanceof net.minecraft.world.entity.item.ItemEntity)entity.discard();save();
        }
        Map<BlockPos,BlockState> scene(){Map<BlockPos,BlockState> out=new HashMap<>();for(var c:plan.cells())out.put(c.pos(),level.getBlockState(c.pos()));for(var r:plan.reads())out.put(r.pos(),level.getBlockState(r.pos()));return out;}
        CompoundTag book(){return level.getBlockEntity(bookPos).saveWithFullMetadata().copy();}
        public ServerLevel level(){return level;}public Path file(){return directory.resolve("source"+source+".dat");}public SectorTransaction.Plan plan(){return plan;}public CompoundTag identity(){CompoundTag n=new CompoundTag();n.putString("fixture",directory.toString());n.putUUID("owner",nation.id());n.putString("candidate","SECTOR_WALL_P1_UNAPPROVED");return n;}
        public EconomyCost cost(){return new EconomyCost(101,Map.of(ResourceType.STONE,7L));}public NationData nation(){return nation;}public int progress(){return progress;}public void progress(int p){progress=p;}public String receipt(){return receipt;}public void receipt(String r){receipt=r;}
        public void save()throws IOException{CompoundTag n=new CompoundTag();n.put("nation",nation.save());n.putInt("level",progress);n.putString("receipt",receipt);Path ledger=directory.resolve("fixture-ledger.dat");ProducerJournal.write(ledger,n,Files.exists(ledger));}
        public void requirements(boolean rollback)throws IOException{if(!(level.getServer() instanceof GameTestServer))throw new IOException("Unapproved P1 outside fixture");}
        public void allow(BlockPos p)throws IOException{if(!allowed.contains(p))throw new IOException("P1 write outside exact sparse union");}
    }
}
