package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.map.MapAnchor;
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
import net.minecraftforge.gametest.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

@GameTestHolder("catancraft_public27") @PrefixGameTestTemplate(false)
public final class StreetscapeTests {
    private static final int FLAGS=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS;
    private static final StreetscapeTransaction.Action APPLY=StreetscapeTransaction.Action.APPLY,STATUS=StreetscapeTransaction.Action.STATUS,
        RESUME=StreetscapeTransaction.Action.RESUME,ROLLBACK=StreetscapeTransaction.Action.ROLLBACK;
    @GameTest(template="townhall_state_empty",batch="public27_01",timeoutTicks=1000)
    public static void delivered_public_contract_both_rotations(GameTestHelper h) {
        try {
            for(int r:new int[]{0,180}) {
                var p=StreetscapeR5.plan(new MapAnchor("fixture",8192,81,8192,r));
                h.assertTrue(p.cells().size()==18766 && p.cells().stream().filter(StreetscapeR5.Cell::changes).count()==1661,"Exact delivered mask/read-context changed");
                h.assertTrue(p.boxes().size()==10,"Unexpected feature set");
            }
            h.assertTrue(StreetscapeR5.protectedLocal(80,80)&&StreetscapeR5.protectedLocal(10,50)&&StreetscapeR5.protectedLocal(20,20),"TH/plot/civic protections missing");
            CatanCraft.LOGGER.info("R5_PUBLIC_ASSETS pinnedOriginalDelta=1661 fullReadContext=18766 bothRotations=PASS");h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    @GameTest(template="townhall_state_empty",batch="public27_02",timeoutTicks=1000)
    public static void public_north_apply_repeat_and_reverse(GameTestHelper h){lifecycle(h,0);}
    @GameTest(template="townhall_state_empty",batch="public27_03",timeoutTicks=1000)
    public static void public_south_apply_repeat_and_reverse(GameTestHelper h){lifecycle(h,180);}
    private static void lifecycle(GameTestHelper h,int rotation) {
        try {
            Env e=new Env(h,rotation);e.prepare();var old=e.snapshot();CompoundTag ledger=e.ledger().copy();
            h.assertTrue(StreetscapeTransaction.run(e,STATUS).writes()==0&&!Files.exists(e.file()),"Status wrote state");
            h.assertTrue(StreetscapeTransaction.run(e,APPLY).writes()==1661,"Incorrect delta size");e.verify(true);
            byte[] journal=Files.readAllBytes(e.file());h.assertTrue(StreetscapeTransaction.run(e,APPLY).writes()==0,"Repeat wrote");
            h.assertTrue(Arrays.equals(journal,Files.readAllBytes(e.file())),"Repeat rewrote history");
            e.reload();h.assertTrue(StreetscapeTransaction.run(e,APPLY).writes()==0,"Reload repeated migration");
            StreetscapeTransaction.run(e,ROLLBACK);h.assertTrue(old.equals(e.snapshot()),"Actual before states not restored");
            h.assertTrue(StreetscapeTransaction.run(e,ROLLBACK).writes()==0,"Repeat rollback wrote");
            h.assertTrue(ledger.equals(e.ledger()),"Purchase/economy changed");h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    @GameTest(template="townhall_state_empty",batch="public27_04",timeoutTicks=1000)
    public static void public_interrupted_forward_and_reverse_reload(GameTestHelper h) {
        try {
            Env e=new Env(h,0);e.prepare();var old=e.snapshot();
            refuse(h,()->StreetscapeTransaction.run(e,APPLY,(s,n)->{if(s.equals("write")&&n==83)throw new IOException("injected write interruption");}));
            e.reload();refuse(h,()->StreetscapeTransaction.run(e,APPLY));StreetscapeTransaction.run(e,RESUME);e.verify(true);
            refuse(h,()->StreetscapeTransaction.run(e,ROLLBACK,(s,n)->{if(s.equals("rollback_write")&&n==39)throw new IOException("injected reverse interruption");}));
            e.reload();refuse(h,()->StreetscapeTransaction.run(e,RESUME));StreetscapeTransaction.run(e,ROLLBACK);
            h.assertTrue(old.equals(e.snapshot()),"Reverse recovery lost before states");h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    @GameTest(template="townhall_state_empty",batch="public27_05",timeoutTicks=1000)
    public static void public_saved_world_uncommitted_journal(GameTestHelper h) {
        try {
            Env e=new Env(h,180);e.prepare();
            refuse(h,()->StreetscapeTransaction.run(e,APPLY,(s,n)->{if(s.equals("world_saved"))throw new IOException("injected commit window");}));
            e.reload();h.assertTrue(StreetscapeTransaction.run(e,RESUME).writes()==0,"Saved target was written twice");e.verify(true);h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    @GameTest(template="townhall_state_empty",batch="public27_06",timeoutTicks=1000)
    public static void public_last_feature_conflict_refuses_whole_group(GameTestHelper h) {
        try {
            Env e=new Env(h,0);e.prepare();var cell=e.plan().cells().stream().filter(StreetscapeR5.Cell::changes).reduce((a,b)->b).orElseThrow();
            e.level().setBlock(cell.pos(),Blocks.DIAMOND_BLOCK.defaultBlockState(),FLAGS);var snapshot=e.snapshot();
            refuse(h,()->StreetscapeTransaction.run(e,APPLY));h.assertTrue(!Files.exists(e.file())&&snapshot.equals(e.snapshot()),"Conflict allowed partial writes");
            e.level().setBlock(cell.pos(),cell.before(),FLAGS);e.denied=true;refuse(h,()->StreetscapeTransaction.run(e,APPLY));
            h.assertTrue(!Files.exists(e.file()),"Protected write created journal");h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    @GameTest(template="townhall_state_empty",batch="public27_07",timeoutTicks=1000)
    public static void public_occupancy_fluid_and_missing_support_refuse(GameTestHelper h) {
        try {
            Env e=new Env(h,0);e.prepare();var c=e.plan().cells().stream().filter(StreetscapeR5.Cell::changes).findFirst().orElseThrow();
            ItemEntity item=new ItemEntity(e.level(),c.pos().getX()+0.5,c.pos().getY()+0.5,c.pos().getZ()+0.5,new ItemStack(Items.STICK));
            e.level().addFreshEntity(item);try{refuse(h,()->StreetscapeTransaction.run(e,APPLY));}finally{item.discard();}
            h.assertTrue(!Files.exists(e.file()),"Occupancy wrote journal");
            var support=e.plan().cells().stream().filter(x->!x.changes()&&!x.before().isAir()).findFirst().orElseThrow();
            e.level().setBlock(support.pos(),Blocks.AIR.defaultBlockState(),FLAGS);refuse(h,()->StreetscapeTransaction.run(e,APPLY));
            e.level().setBlock(support.pos(),Blocks.WATER.defaultBlockState(),FLAGS);refuse(h,()->StreetscapeTransaction.run(e,APPLY));
            h.assertTrue(!Files.exists(e.file()),"Missing support/fluid passed");h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    @GameTest(template="townhall_state_empty",batch="public27_08",timeoutTicks=1000)
    public static void public_corrupt_foreign_and_drifted_history_refuse(GameTestHelper h) {
        try {
            Env e=new Env(h,0);e.prepare();StreetscapeTransaction.run(e,APPLY);byte[] good=Files.readAllBytes(e.file());
            byte[] bad=good.clone();bad[bad.length-1]^=1;Files.write(e.file(),bad);refuse(h,()->StreetscapeTransaction.run(e,ROLLBACK));
            Files.write(e.file(),good);e.suffix="foreign-owner";refuse(h,()->StreetscapeTransaction.run(e,ROLLBACK));e.suffix="";
            var c=e.plan().cells().stream().filter(StreetscapeR5.Cell::changes).findFirst().orElseThrow();e.level().setBlock(c.pos(),Blocks.GOLD_BLOCK.defaultBlockState(),FLAGS);
            var snapshot=e.snapshot();refuse(h,()->StreetscapeTransaction.run(e,ROLLBACK));h.assertTrue(snapshot.equals(e.snapshot()),"Drift rollback wrote");h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    @GameTest(template="townhall_state_empty",batch="public27_09",timeoutTicks=1000)
    public static void public_neighbor_inventory_and_native_delayed_state(GameTestHelper h) {
        try {
            Env e=new Env(h,0);e.prepare();Set<BlockPos> occupied=new HashSet<>();for(var c:e.plan().cells())occupied.add(c.pos());
            BlockPos pos=e.plan().cells().stream().map(c->c.pos().west()).filter(p->!occupied.contains(p)).findFirst().orElseThrow();
            e.level().setBlock(pos,Blocks.BARREL.defaultBlockState(),FLAGS);BarrelBlockEntity be=(BarrelBlockEntity)e.level().getBlockEntity(pos);
            ItemStack stack=new ItemStack(Items.DIAMOND,19);stack.setHoverName(net.minecraft.network.chat.Component.literal("Do not alter neighbor inventory"));be.setItem(0,stack);
            CompoundTag nbt=be.saveWithFullMetadata().copy();StreetscapeTransaction.run(e,APPLY);
            h.runAfterDelay(40,()->{try{e.verify(true);StreetscapeTransaction.run(e,STATUS);
                h.assertTrue(nbt.equals(e.level().getBlockEntity(pos).saveWithFullMetadata()),"Neighbor NBT changed");h.succeed();}catch(Exception ex){throw new RuntimeException(ex);}});
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    @GameTest(template="townhall_state_empty",batch="public27_10",timeoutTicks=1000)
    public static void public_service_denies_unauthorized_world(GameTestHelper h) {
        try {
            refuse(h,()->StreetscapeService.context(h.getLevel().getServer(),"a",true));
            var distant=new net.minecraft.world.phys.AABB(15000000,80,15000000,15000002,82,15000002);
            refuse(h,()->StreetscapeService.requireLoadedEntities(h.getLevel(),java.util.List.of(distant)));
            h.assertTrue(!h.getLevel().hasChunkAt(new BlockPos(15000000,81,15000000)),"Readiness guard loaded a remote chunk");h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    private interface Work {void run()throws Exception;}
    private static void refuse(GameTestHelper h,Work w)throws Exception {boolean refused=false;try{w.run();}catch(IOException expected){refused=true;}h.assertTrue(refused,"Expected refusal missing");}
    private static final class Env implements StreetscapeTransaction.Environment {
        final GameTestHelper h;final StreetscapeR5.Plan plan;final Path path;CompoundTag ledger=new CompoundTag();String suffix="";boolean denied;
        Env(GameTestHelper h,int rotation)throws IOException {
            this.h=h;plan=StreetscapeR5.plan(new MapAnchor("test",8192,81,8192,rotation));
            path=h.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("data/public27-tests/"+UUID.randomUUID()+"/journal.dat");Files.createDirectories(path.getParent());
            ledger.putInt("paidProducerLevel",5);ledger.putInt("paidTownHallLevel",5);ledger.putInt("resources",1234);
        }
        void prepare()throws IOException {
            if(!(level().getServer() instanceof GameTestServer))throw new IOException("Fixture outside GameTest server");
            // Dedicated off-template area; no owner worlds and no interference with retained tests.
            for(var b:plan.boxes()) {
                for(int x=((int)b.minX-2)>>4;x<=((int)b.maxX+2)>>4;x++)for(int z=((int)b.minZ-2)>>4;z<=((int)b.maxZ+2)>>4;z++)level().getChunk(x,z);
                for(ItemEntity item:level().getEntitiesOfClass(ItemEntity.class,b.inflate(2)))item.discard();
                for(BlockPos p:BlockPos.betweenClosed((int)b.minX-1,(int)b.minY-1,(int)b.minZ-1,(int)b.maxX+1,(int)b.maxY+2,(int)b.maxZ+1)) {
                    if(level().getBlockEntity(p) instanceof net.minecraft.world.Container c)c.clearContent();level().setBlock(p,Blocks.AIR.defaultBlockState(),FLAGS);
                }
            }
            for(var c:plan.cells())level().setBlock(c.pos(),c.before(),FLAGS);save();
        }
        Map<BlockPos,BlockState> snapshot(){Map<BlockPos,BlockState> r=new LinkedHashMap<>();for(var c:plan.cells())r.put(c.pos(),level().getBlockState(c.pos()));return r;}
        void verify(boolean after){for(var c:plan.cells())h.assertTrue(StreetscapeR5.matches(level().getBlockState(c.pos()),after?c.after():c.before()),"Public geometry mismatch at "+c.pos());}
        public ServerLevel level(){return h.getLevel();}public Path file(){return path;}public StreetscapeR5.Plan plan(){return plan;}
        public CompoundTag identity(){CompoundTag n=new CompoundTag();n.putString("world",path.getParent()+suffix);n.putInt("rotation",plan.center().rotation());return n;}
        public CompoundTag ledger(){return ledger.copy();}
        public void save()throws IOException {try(OutputStream out=Files.newOutputStream(path.resolveSibling("ledger.nbt"))){NbtIo.writeCompressed(ledger,out);}}
        void reload()throws IOException {try(InputStream in=Files.newInputStream(path.resolveSibling("ledger.nbt"))){ledger=NbtIo.readCompressed(in);}}
        public void allowWrite(BlockPos p)throws IOException {if(denied)throw new IOException("Injected protected location");}
    }
}
