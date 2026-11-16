package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.map.MapAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Isolated native public-space fixtures, not authored-world or client acceptance. */
@GameTestHolder("catancraft_streetscape_natural53") @PrefixGameTestTemplate(false)
public final class StreetscapeNatural53Tests {
    static final int FLAGS=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS;
    static final StreetscapeTransaction.Action APPLY=StreetscapeTransaction.Action.APPLY, STATUS=StreetscapeTransaction.Action.STATUS,
        RESUME=StreetscapeTransaction.Action.RESUME, ROLLBACK=StreetscapeTransaction.Action.ROLLBACK;

    @GameTest(template="townhall_state_empty",batch="street_natural53_01",timeoutTicks=1000)
    public static void source_and_target_native_decay_north(GameTestHelper h){lifecycle(h,0,0);}
    @GameTest(template="townhall_state_empty",batch="street_natural53_02",timeoutTicks=1000)
    public static void source_and_target_native_decay_south(GameTestHelper h){lifecycle(h,180,1);}
    static void lifecycle(GameTestHelper h,int rotation,int area) {
        try {
            Env e=fresh(h,rotation,area);e.prepare();e.decay(e.source());var original=e.snapshot();CompoundTag ledger=e.ledger();
            h.assertTrue(StreetscapeTransaction.run(e,STATUS).writes()==0&&!Files.exists(e.file()),"Read-only source check wrote history");
            h.assertTrue(StreetscapeTransaction.run(e,APPLY).writes()==StreetscapeR5.WRITES,"Public design mask changed");
            CompoundTag journal=StreetscapeJournal.read(e.file());byte[] bytes=Files.readAllBytes(e.file());
            h.assertTrue(VehiclePublicSource52.validate(journal,e.plan(),e.identity()),"Actual completed public history rejected by vehicle source");
            h.assertTrue(Arrays.equals(bytes,Files.readAllBytes(e.file())),"Vehicle validation wrote public history");
            // A canonical-looking source soil image must still agree with each actual before row.
            CompoundTag inconsistent=journal.copy();BlockPos written=e.plan().cells().stream().filter(c->c.changes()&&e.source().containsKey(c.pos())).findFirst().orElseThrow().pos();
            for(Tag value:inconsistent.getCompound("naturalSoilV1").getList("sourceBefore",Tag.TAG_COMPOUND)) {
                CompoundTag row=(CompoundTag)value;if(row.getLong("pos")==written.asLong())row.put("state",NbtUtils.writeBlockState(Blocks.GRASS_BLOCK.defaultBlockState()));
            }
            refuse(h,()->VehiclePublicSource52.validate(inconsistent,e.plan(),e.identity()));
            e.decay(e.target());e.reload();
            h.assertTrue(StreetscapeTransaction.run(e,STATUS).writes()==0&&StreetscapeTransaction.run(e,APPLY).writes()==0,"Completed decay caused replay");
            h.assertTrue(Arrays.equals(bytes,Files.readAllBytes(e.file())),"Read-only/repeated completion rewrote history");
            StreetscapeTransaction.run(e,ROLLBACK);
            Map<BlockPos,BlockState> expected=new LinkedHashMap<>(original);for(BlockPos p:e.target().keySet())expected.put(p,Blocks.DIRT.defaultBlockState());
            e.verify(expected);byte[] rolled=Files.readAllBytes(e.file());
            h.assertTrue(StreetscapeTransaction.run(e,ROLLBACK).writes()==0&&Arrays.equals(rolled,Files.readAllBytes(e.file())),"Repeated rollback changed history");
            h.assertTrue(ledger.equals(e.ledger()),"Natural public preparation changed economy");
            CatanCraft.LOGGER.info("STREETSCAPE_NATURAL53 rotation={} nativeSourceDecay=19 nativeTargetDecay=18 exactRollback=true vehicleHistory=true",rotation);h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }

    @GameTest(template="townhall_state_empty",batch="street_natural53_03",timeoutTicks=1000)
    public static void exposed_future_missing_and_foreign_north(GameTestHelper h){negative(h,0,2);}
    @GameTest(template="townhall_state_empty",batch="street_natural53_04",timeoutTicks=1000)
    public static void exposed_future_missing_and_foreign_south(GameTestHelper h){negative(h,180,3);}
    static void negative(GameTestHelper h,int rotation,int area) {
        try {
            Env e=fresh(h,rotation,area);e.prepare();
            BlockPos future=e.target().keySet().iterator().next();e.level().setBlock(future,Blocks.DIRT.defaultBlockState(),FLAGS);
            e.refuseWithoutMutation(APPLY);e.level().setBlock(future,Blocks.GRASS_BLOCK.defaultBlockState(),FLAGS);
            BlockPos exposed=e.plan().cells().stream().filter(c->c.before().is(Blocks.GRASS_BLOCK)&&!e.source().containsKey(c.pos())&&!e.target().containsKey(c.pos())).findFirst().orElseThrow().pos();
            e.level().setBlock(exposed,Blocks.DIRT.defaultBlockState(),FLAGS);e.refuseWithoutMutation(APPLY);e.level().setBlock(exposed,Blocks.GRASS_BLOCK.defaultBlockState(),FLAGS);
            BlockPos covered=e.source().keySet().iterator().next();e.decay(Map.of(covered,e.source().get(covered)));
            for(BlockState foreign:List.of(Blocks.AIR.defaultBlockState(),Blocks.GOLD_BLOCK.defaultBlockState(),Blocks.BARREL.defaultBlockState())) {
                e.level().setBlock(covered.above(),foreign,FLAGS);e.refuseWithoutMutation(APPLY);
            }
            e.level().setBlock(covered.above(),e.source().get(covered),FLAGS);
            StreetscapeTransaction.run(e,APPLY);
            // Captured grass under a removed source log is not proof of later exposed dirt.
            BlockPos removed=e.plan().cells().stream().filter(c->!c.changes()&&e.source().containsKey(c.pos())).findFirst().orElseThrow().pos();
            if(removed.equals(covered))throw new IOException("Negative fixture selected the unchanged log soil");
            e.level().setBlock(removed,Blocks.DIRT.defaultBlockState(),FLAGS);e.refuseWithoutMutation(STATUS);e.level().setBlock(removed,Blocks.GRASS_BLOCK.defaultBlockState(),FLAGS);
            CompoundTag original=StreetscapeJournal.read(e.file()),changed=original.copy();changed.getCompound("naturalSoilV1").putInt("version",2);
            StreetscapeJournal.write(e.file(),changed,true);e.refuseWithoutMutation(ROLLBACK);
            changed=original.copy();changed.getCompound("naturalSoilV1").getList("sourceCovers",Tag.TAG_COMPOUND).getCompound(0).put("cover",NbtUtils.writeBlockState(Blocks.GOLD_BLOCK.defaultBlockState()));
            StreetscapeJournal.write(e.file(),changed,true);e.refuseWithoutMutation(STATUS);
            changed=original.copy();changed.getCompound("naturalSoilV1").putLongArray("observedTargetDirt",new long[]{exposed.asLong()});
            StreetscapeJournal.write(e.file(),changed,true);e.refuseWithoutMutation(STATUS);
            StreetscapeJournal.write(e.file(),original,true);StreetscapeTransaction.run(e,STATUS);h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }

    @GameTest(template="townhall_state_empty",batch="street_natural53_05",timeoutTicks=1000)
    public static void retained_schema27_history_north(GameTestHelper h){legacy(h,0,4);}
    @GameTest(template="townhall_state_empty",batch="street_natural53_06",timeoutTicks=1000)
    public static void retained_schema27_history_south(GameTestHelper h){legacy(h,180,5);}
    static void legacy(GameTestHelper h,int rotation,int area) {
        try {
            Env e=fresh(h,rotation,area);e.prepare();StreetscapeTransaction.run(e,APPLY);
            CompoundTag legacy=StreetscapeJournal.read(e.file());legacy.remove("naturalSoilV1");StreetscapeJournal.write(e.file(),legacy,true);
            byte[] bytes=Files.readAllBytes(e.file());e.reload();StreetscapeTransaction.run(e,STATUS);StreetscapeTransaction.run(e,APPLY);
            h.assertTrue(VehiclePublicSource52.validate(legacy,e.plan(),e.identity()),"Retained schema27 history rejected");
            h.assertTrue(Arrays.equals(bytes,Files.readAllBytes(e.file())),"Retained schema27 journal silently migrated");
            e.decay(e.target());e.refuseWithoutMutation(STATUS);
            h.assertTrue(Arrays.equals(bytes,Files.readAllBytes(e.file())),"Strict historical refusal changed journal");h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }

    static Env fresh(GameTestHelper h,int rotation,int area)throws IOException {
        Path path=h.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("data/streetscape-natural53/"+UUID.randomUUID()+"/journal.dat");
        return new Env(h,rotation,12288+area*512,path);
    }
    interface Work {void run()throws Exception;}
    static void refuse(GameTestHelper h,Work work)throws Exception {
        boolean refused=false;try{work.run();}catch(IOException expected){refused=true;}h.assertTrue(refused,"Expected precise natural-soil refusal missing");
    }
    static final class Env implements StreetscapeTransaction.Environment {
        final GameTestHelper h;final StreetscapeR5.Plan plan;final Path path;CompoundTag ledger=new CompoundTag();
        Env(GameTestHelper h,int rotation,int center,Path path)throws IOException {
            this.h=h;this.path=path;plan=StreetscapeR5.plan(new MapAnchor("natural53",center,81,12288,rotation));
            ledger.putInt("treasury",12000);ledger.putInt("townHall",1);ledger.putInt("producer",1);
            h.assertTrue(source().size()==19&&target().size()==18,"Pinned public covered-soil inventory differs");
        }
        Map<BlockPos,BlockState> source(){return covers(false);}Map<BlockPos,BlockState> target(){return covers(true);}
        private Map<BlockPos,BlockState> covers(boolean target) {
            Map<BlockPos,BlockState> states=new HashMap<>(),covers=new LinkedHashMap<>();for(var c:plan.cells())states.put(c.pos(),target?c.after():c.before());
            for(var c:plan.cells())if((target?c.after():c.before()).equals(Blocks.GRASS_BLOCK.defaultBlockState())) {
                BlockState cover=states.get(c.pos().above());if(SectorNaturalSupportPolicy.blocksGrass(cover,c.pos().above()))covers.put(c.pos(),cover);
            }return covers;
        }
        void loadChunks()throws IOException {
            if(!(level().getServer() instanceof GameTestServer))throw new IOException("Natural fixture outside isolated GameTest server");
            for(var b:plan.boxes())for(int x=((int)b.minX-4)>>4;x<=((int)b.maxX+4)>>4;x++)for(int z=((int)b.minZ-4)>>4;z<=((int)b.maxZ+4)>>4;z++){level().setChunkForced(x,z,true);level().getChunk(x,z);}
        }
        void prepare()throws IOException {
            if(Files.exists(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Never restamp an existing natural fixture");loadChunks();Files.createDirectories(path.getParent());
            for(var b:plan.boxes()) {
                for(ItemEntity item:level().getEntitiesOfClass(ItemEntity.class,b.inflate(3)))item.discard();
                for(BlockPos p:BlockPos.betweenClosed((int)b.minX-1,(int)b.minY-1,(int)b.minZ-1,(int)b.maxX+1,(int)b.maxY+2,(int)b.maxZ+1)) {
                    if(level().getBlockEntity(p) instanceof net.minecraft.world.Container container)container.clearContent();level().setBlock(p,Blocks.AIR.defaultBlockState(),FLAGS);
                }
            }
            for(var c:plan.cells())level().setBlock(c.pos(),c.before(),FLAGS);save();
        }
        void decay(Map<BlockPos,BlockState> covers)throws IOException {
            for(var row:covers.entrySet()) {
                BlockPos p=row.getKey();if(!level().isAreaLoaded(p,3)||!level().getBlockState(p).equals(Blocks.GRASS_BLOCK.defaultBlockState())||!level().getBlockState(p.above()).equals(row.getValue()))throw new IOException("Native decay fixture source differs at "+p);
                Blocks.GRASS_BLOCK.randomTick(level().getBlockState(p),level(),p,RandomSource.create(53));
                h.assertTrue(level().getBlockState(p).equals(Blocks.DIRT.defaultBlockState()),"Native pinned-cover random tick did not decay "+p);
            }
        }
        Map<BlockPos,BlockState> snapshot(){Map<BlockPos,BlockState> result=new LinkedHashMap<>();for(var c:plan.cells())result.put(c.pos(),level().getBlockState(c.pos()));return result;}
        void verify(Map<BlockPos,BlockState> expected){for(var row:expected.entrySet())h.assertTrue(StreetscapeR5.matches(level().getBlockState(row.getKey()),row.getValue()),"Natural fixture changed at "+row.getKey());}
        void refuseWithoutMutation(StreetscapeTransaction.Action action)throws Exception {
            var before=snapshot();CompoundTag money=ledger();byte[] history=Files.exists(path)?Files.readAllBytes(path):null;
            refuse(h,()->StreetscapeTransaction.run(this,action));h.assertTrue(before.equals(snapshot())&&money.equals(ledger()),"Refusal wrote geometry/economy");
            h.assertTrue(history==null?!Files.exists(path):Arrays.equals(history,Files.readAllBytes(path)),"Refusal rewrote history");
        }
        void reload()throws IOException {ledger=NbtIo.readCompressed(path.resolveSibling("ledger.nbt").toFile());}
        public ServerLevel level(){return h.getLevel();}public Path file(){return path;}public StreetscapeR5.Plan plan(){return plan;}
        public CompoundTag identity(){CompoundTag n=new CompoundTag();n.putString("world",path.getParent().toString());n.putInt("rotation",plan.center().rotation());return n;}
        public CompoundTag ledger(){return ledger.copy();}
        public void save()throws IOException {Files.createDirectories(path.getParent());NbtIo.writeCompressed(ledger,path.resolveSibling("ledger.nbt").toFile());}
        public void allowWrite(BlockPos pos){}
    }
}
