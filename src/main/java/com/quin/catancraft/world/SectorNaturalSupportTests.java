package com.quin.catancraft.world;

import com.quin.catancraft.data.*;
import com.quin.catancraft.economy.EconomyCost;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Synthetic tiny construction plans; actual native grass random ticks, sealed ledger/history reload.
 * No terrain restore, production art, game-rule changes or live-world operations.
 */
@GameTestHolder("catancraft_sectors_natural") @PrefixGameTestTemplate(false)
public final class SectorNaturalSupportTests {
    @FunctionalInterface interface Attempt{void run()throws IOException;}
    static IOException refuse(Attempt action)throws IOException {try{action.run();}catch(IOException expected){return expected;}throw new AssertionError("Expected exact support refusal");}
    @GameTest(template="townhall_state_empty",batch="natural01",timeoutTicks=1200)
    public static void fresh_is_exact_and_unrelated_reads_foreign_soil_stay_exact(GameTestHelper h){
        try{Env e=new Env(h);e.stage();e.set(e.grass(0),Blocks.DIRT.defaultBlockState());long cash=e.nation.treasury();
            refuse(()->SectorTransaction.run(e,SectorTransaction.Action.APPLY));h.assertTrue(!Files.exists(e.file)&&e.nation.treasury()==cash,"Fresh dirt created journal or charged");
            e.set(e.grass(0),Blocks.GRASS_BLOCK.defaultBlockState());SectorTransaction.run(e,SectorTransaction.Action.APPLY);e.decay(0);var nbt=e.book();byte[] journal=Files.readAllBytes(e.file);
            for(Block block:List.of(Blocks.COARSE_DIRT,Blocks.ROOTED_DIRT,Blocks.STONE,Blocks.AIR,Blocks.WATER)){
                e.set(e.grass(0),block.defaultBlockState());refuse(()->SectorTransaction.run(e,SectorTransaction.Action.STATUS));h.assertTrue(Arrays.equals(journal,Files.readAllBytes(e.file)),"Foreign support rewrote history");
            }
            e.set(e.grass(0),Blocks.DIRT.defaultBlockState());e.set(e.offWhitelist,Blocks.DIRT.defaultBlockState());refuse(()->SectorTransaction.run(e,SectorTransaction.Action.STATUS));
            e.set(e.offWhitelist,Blocks.GRASS_BLOCK.defaultBlockState());SectorTransaction.run(e,SectorTransaction.Action.STATUS);h.assertTrue(nbt.equals(e.book()),"Support validation touched protected inventory/book");h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    @GameTest(template="townhall_state_empty",batch="natural02",timeoutTicks=1200)
    public static void natural_decay_paid_upgrade_reverse_and_prior_receipt_are_preserved(GameTestHelper h){
        try{Env e=new Env(h);e.stage();var initial=e.nation.save();SectorTransaction.run(e,SectorTransaction.Action.APPLY);for(int i=0;i<3;i++)e.decay(i);
            byte[] basic=Files.readAllBytes(e.file);String previous=e.receipt;var book=e.book();e.reload();e.upgrade();
            SectorTransaction.run(e,SectorTransaction.Action.APPLY);h.assertTrue(e.progress==2&&e.nation.treasury()==854,"Upgrade charge/level wrong");
            SectorTransaction.run(e,SectorTransaction.Action.ROLLBACK);h.assertTrue(e.progress==1&&e.receipt.equals(previous)&&e.nation.treasury()==927,"Upgrade rollback lost historical lower paid state");
            h.assertTrue(Arrays.equals(basic,Files.readAllBytes(e.basicFile))&&book.equals(e.book()),"Upgrade modified old journal or current NBT");
            for(int i=0;i<3;i++)h.assertTrue(e.level.getBlockState(e.grass(i)).is(Blocks.DIRT)&&e.level.getBlockState(e.cover(i)).is(Blocks.STONE),"Rollback manufactured grass or failed to restore basic");
            SectorTransaction.run(e,SectorTransaction.Action.ROLLBACK);h.assertTrue(e.nation.treasury()==927&&!initial.equals(e.nation.save()),"Duplicate rollback refunded basic purchase");h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    @GameTest(template="townhall_state_empty",batch="natural03",timeoutTicks=1200)
    public static void partial_install_mixed_soil_and_reverse_reload_keep_exposure_proof(GameTestHelper h){
        try{Env e=new Env(h);e.stage();var initial=e.nation.save();IOException interrupted=refuse(()->SectorTransaction.run(e,SectorTransaction.Action.APPLY,(stage,count)->{if(stage.equals("write")&&count==1){e.decay(0);throw new IOException("native forward interruption");}}));
            h.assertTrue(interrupted.getMessage().equals("native forward interruption")&&e.progress==0&&e.receipt.endsWith(":reserved"),"Forward boundary not reached");
            e.reload();h.assertTrue(e.level.getBlockState(e.grass(0)).is(Blocks.DIRT)&&e.level.getBlockState(e.grass(1)).is(Blocks.GRASS_BLOCK),"Mixed native support fixture missing");
            h.assertTrue(SectorTransaction.run(e,SectorTransaction.Action.RESUME).writes()==2&&e.nation.treasury()==927,"Resume repeated writes/charge");for(int i=1;i<3;i++)e.decay(i);
            IOException reverse=refuse(()->SectorTransaction.run(e,SectorTransaction.Action.ROLLBACK,(stage,count)->{if(stage.equals("rollback_write")&&count==1)throw new IOException("native reverse interruption");}));
            h.assertTrue(reverse.getMessage().equals("native reverse interruption")&&e.level.getBlockState(e.cover(2)).isAir(),"Reverse fixture not reached");
            CompoundTag sealed=ProducerJournal.read(e.file);h.assertTrue(sealed.getLongArray("naturalSupportExposure").length==3,"Reverse cover exposure not durable before removal");
            e.reload();h.assertTrue(SectorTransaction.run(e,SectorTransaction.Action.ROLLBACK).writes()==2&&initial.equals(e.nation.save()),"Reverse reload failed or refunded twice");
            SectorTransaction.run(e,SectorTransaction.Action.ROLLBACK);for(int i=0;i<3;i++)h.assertTrue(e.level.getBlockState(e.grass(i)).is(Blocks.DIRT),"Rollback repainted native dirt");
            h.assertTrue(initial.equals(e.nation.save()),"Duplicate reverse payment");h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    @GameTest(template="townhall_state_empty",batch="natural04",timeoutTicks=1200)
    public static void legacy_strict_specs_and_never_covered_pending_support_refuse_dirt(GameTestHelper h){
        try{Env e=new Env(h);e.stage();SectorTransaction.Plan modern=e.plan;e.plan=new SectorTransaction.Plan(modern.sector(),modern.sourceRevision(),modern.targetRevision(),0,1,modern.cells(),modern.reads());
            CompoundTag legacy=SectorTransaction.describe(e.plan);h.assertTrue(!legacy.contains("naturalSupport")&&SectorTransaction.describe(SectorTransaction.fromDescription(legacy)).equals(legacy),"Old description semantics changed");
            SectorTransaction.run(e,SectorTransaction.Action.APPLY);e.decay(0);byte[] history=Files.readAllBytes(e.file);refuse(()->SectorTransaction.run(e,SectorTransaction.Action.STATUS));h.assertTrue(Arrays.equals(history,Files.readAllBytes(e.file)),"Legacy history rewritten to accept dirt");
            Env pending=new Env(h);pending.stage();IOException first=refuse(()->SectorTransaction.run(pending,SectorTransaction.Action.APPLY,(stage,count)->{if(stage.equals("write")&&count==1)throw new IOException("first cover only");}));h.assertTrue(first.getMessage().equals("first cover only"),"Pending fixture not reached");
            pending.set(pending.grass(2),Blocks.DIRT.defaultBlockState());var nation=pending.nation.save();byte[] retained=Files.readAllBytes(pending.file);refuse(()->SectorTransaction.run(pending,SectorTransaction.Action.RESUME));
            h.assertTrue(pending.level.getBlockState(pending.cover(2)).isAir()&&nation.equals(pending.nation.save())&&Arrays.equals(retained,Files.readAllBytes(pending.file)),"Future opaque target alone granted natural dirt proof");h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    @GameTest(template="townhall_state_empty",batch="natural05",timeoutTicks=1200)
    public static void upgrade_requires_exact_paid_proof_and_recovery_freezes_it(GameTestHelper h){
        try{Env e=new Env(h);e.stage();SectorTransaction.run(e,SectorTransaction.Action.APPLY);for(int i=0;i<3;i++)e.decay(i);e.upgrade();CompoundTag valid=e.proof.copy();
            e.proof.putUUID("id",UUID.randomUUID());refuse(()->SectorTransaction.run(e,SectorTransaction.Action.APPLY));h.assertTrue(!Files.exists(e.file)&&e.nation.treasury()==927,"Wrong paid proof accepted");e.proof=valid;
            IOException fault=refuse(()->SectorTransaction.run(e,SectorTransaction.Action.APPLY,(stage,count)->{if(stage.equals("write")&&count==1)throw new IOException("upgrade interruption");}));h.assertTrue(fault.getMessage().equals("upgrade interruption"),"Upgrade boundary not reached");
            e.reload();e.proof=new CompoundTag();e.proofForbidden=true;e.price=new EconomyCost(99999,Map.of(ResourceType.STONE,999L));
            SectorTransaction.run(e,SectorTransaction.Action.RESUME);h.assertTrue(e.nation.treasury()==854&&e.progress==2,"Recovery ignored frozen paid proof or historical price");
            SectorTransaction.run(e,SectorTransaction.Action.ROLLBACK);h.assertTrue(e.nation.treasury()==927&&e.progress==1,"Frozen-proof reverse accounting failed");h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    @GameTest(template="townhall_state_empty",batch="natural06",timeoutTicks=1200)
    public static void fresh_fixed_context_decay_is_exact_and_never_future_cover(GameTestHelper h){
        try{Env e=new Env(h);SectorTransaction.Plan old=e.plan;
            BlockPos support=e.origin.offset(0,-1,10),cover=support.above();BlockState stone=Blocks.STONE_BRICKS.defaultBlockState();
            List<SectorTransaction.Read> reads=new ArrayList<>(old.reads());reads.add(new SectorTransaction.Read(support,Blocks.GRASS_BLOCK.defaultBlockState()));reads.add(new SectorTransaction.Read(cover,stone));
            var policy=SectorNaturalSupportPolicy.coveredGrass(old.naturalSupport().grassSupports(),Map.of(support,stone));
            e.plan=new SectorTransaction.Plan(old.sector(),old.sourceRevision(),old.targetRevision(),0,1,old.cells(),reads,policy);
            CompoundTag sealed=SectorTransaction.describe(e.plan);h.assertTrue(SectorNaturalSupportPolicy.FIXED_VERSION.equals(policy.version())&&SectorTransaction.describe(SectorTransaction.fromDescription(sealed)).equals(sealed),"Fixed-cover contract did not roundtrip exactly");
            e.stage();Blocks.GRASS_BLOCK.randomTick(e.level.getBlockState(support),e.level,support,RandomSource.create(7));h.assertTrue(e.level.getBlockState(support).is(Blocks.DIRT),"Fixed context did not decay through actual native tick");
            long cash=e.nation.treasury();e.set(cover,Blocks.STONE.defaultBlockState());refuse(()->SectorTransaction.run(e,SectorTransaction.Action.APPLY));h.assertTrue(!Files.exists(e.file)&&cash==e.nation.treasury(),"Changed fixed opaque cover was accepted/charged");
            e.set(cover,stone);e.set(e.offWhitelist,Blocks.DIRT.defaultBlockState());refuse(()->SectorTransaction.run(e,SectorTransaction.Action.APPLY));h.assertTrue(!Files.exists(e.file),"Off-whitelist dirt accepted by fixed context");
            e.set(e.offWhitelist,Blocks.GRASS_BLOCK.defaultBlockState());SectorTransaction.run(e,SectorTransaction.Action.APPLY);h.assertTrue(e.progress==1&&e.nation.treasury()==cash-73&&e.level.getBlockState(support).is(Blocks.DIRT)&&e.level.getBlockState(cover).equals(stone),"Fresh fixed-context purchase rewrote terrain or charged incorrectly");
            SectorTransaction.run(e,SectorTransaction.Action.ROLLBACK);h.assertTrue(e.nation.treasury()==cash&&e.level.getBlockState(support).is(Blocks.DIRT)&&e.level.getBlockState(cover).equals(stone),"Fixed-context reverse changed readonly terrain");h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    @GameTest(template="townhall_state_empty",batch="natural07",timeoutTicks=1200)
    public static void bottom_stair_native_decay_upgrade_and_reverse_preserve_paid_state(GameTestHelper h){
        try{Env e=new Env(h);var old=e.plan;BlockState stairs=Blocks.STONE_BRICK_STAIRS.defaultBlockState();
            h.assertTrue(SectorNaturalSupportPolicy.blocksGrass(stairs,e.cover(0))&&!SectorNaturalSupportPolicy.opaque(stairs,e.cover(0)),"Bottom stair must use versioned light-occlusion semantics");
            List<SectorTransaction.Cell> cells=new ArrayList<>();for(var c:old.cells())cells.add(new SectorTransaction.Cell(c.pos(),c.before(),c.after().is(Blocks.STONE)?stairs:c.after()));
            var legacy=new SectorTransaction.Plan(old.sector(),old.sourceRevision(),old.targetRevision(),0,1,cells,old.reads(),old.naturalSupport());
            refuse(()->legacy.naturalSupport().validate(legacy));
            var policy=SectorNaturalSupportPolicy.coveredGrassShaped(old.naturalSupport().grassSupports(),Map.of());
            e.plan=new SectorTransaction.Plan(old.sector(),old.sourceRevision(),old.targetRevision(),0,1,cells,old.reads(),policy);
            CompoundTag spec=SectorTransaction.describe(e.plan);h.assertTrue(SectorTransaction.describe(SectorTransaction.fromDescription(spec)).equals(spec),"V3 exact roundtrip failed");
            e.stage();e.set(e.grass(0),Blocks.DIRT.defaultBlockState());refuse(()->SectorTransaction.run(e,SectorTransaction.Action.APPLY));h.assertTrue(!Files.exists(e.file)&&e.nation.treasury()==1000,"Fresh unproved dirt charged");
            e.set(e.grass(0),Blocks.GRASS_BLOCK.defaultBlockState());SectorTransaction.run(e,SectorTransaction.Action.APPLY);for(int i=0;i<3;i++)e.decay(i);
            byte[] basic=Files.readAllBytes(e.basicFile);String receipt=e.receipt;var book=e.book();e.reload();e.upgrade();
            var upgrade=e.plan;List<SectorTransaction.Cell> next=new ArrayList<>();for(var c:upgrade.cells())next.add(new SectorTransaction.Cell(c.pos(),c.before().is(Blocks.STONE)?stairs:c.before(),c.after()));
            e.plan=new SectorTransaction.Plan(upgrade.sector(),upgrade.sourceRevision(),upgrade.targetRevision(),1,2,next,upgrade.reads(),policy);
            SectorTransaction.run(e,SectorTransaction.Action.APPLY);h.assertTrue(e.progress==2&&e.nation.treasury()==854,"Stair upgrade charge wrong");
            SectorTransaction.run(e,SectorTransaction.Action.ROLLBACK);SectorTransaction.run(e,SectorTransaction.Action.ROLLBACK);
            h.assertTrue(e.progress==1&&e.nation.treasury()==927&&receipt.equals(e.receipt)&&Arrays.equals(basic,Files.readAllBytes(e.basicFile))&&book.equals(e.book()),"Stair rollback changed original paid state/history/NBT");
            for(int i=0;i<3;i++)h.assertTrue(e.level.getBlockState(e.cover(i)).equals(stairs)&&e.level.getBlockState(e.grass(i)).is(Blocks.DIRT),"Reverse changed natural dirt or stair source");h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    static final class Env implements SectorTransaction.Environment {
        private static int serial;
        final ServerLevel level;final Path dir,ledger,basicFile;final BlockPos origin,offWhitelist,bookPos;Path file;SectorTransaction.Plan plan;
        NationData nation;int progress;String receipt="";CompoundTag proof=new CompoundTag();boolean proofForbidden;EconomyCost price=new EconomyCost(73,Map.of(ResourceType.STONE,3L));
        Env(GameTestHelper h)throws IOException {
            level=h.getLevel();if(!(level.getServer() instanceof GameTestServer))throw new IOException("Native policy fixture only");
            origin=new BlockPos(28672+(++serial)*32,120,28672);offWhitelist=origin.offset(0,-1,4);bookPos=origin.offset(0,0,6);
            // Forge's native grass decay returns without changing anything when its
            // radius-one neighborhood is unloaded; spreading separately checks radius three.
            // These fixtures sit on chunk boundaries. Load the complete small neighborhood
            // synchronously rather than substituting dirt or altering random-tick rules.
            int chunkX=origin.getX()>>4,chunkZ=origin.getZ()>>4;
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)level.getChunk(chunkX+dx,chunkZ+dz);
            dir=level.getServer().getWorldPath(LevelResource.ROOT).resolve("data/natural-support-fixture/"+UUID.randomUUID());Files.createDirectories(dir);ledger=dir.resolve("ledger.dat");basicFile=dir.resolve("basic.dat");file=basicFile;
            nation=new NationData(UUID.randomUUID(),"natural-policy-fixture",UUID.randomUUID(),1000);nation.addResource(ResourceType.STONE,100);plan=make(0,true);
        }
        BlockPos cover(int i){return origin.offset(i*2,0,0);}BlockPos grass(int i){return cover(i).below();}
        SectorTransaction.Plan make(int source,boolean natural){
            List<SectorTransaction.Cell> cells=new ArrayList<>();List<SectorTransaction.Read> reads=new ArrayList<>();Set<BlockPos> grass=new HashSet<>();
            for(int i=0;i<3;i++){cells.add(new SectorTransaction.Cell(cover(i),source==0?Blocks.AIR.defaultBlockState():Blocks.STONE.defaultBlockState(),source==0?Blocks.STONE.defaultBlockState():Blocks.STONE_BRICKS.defaultBlockState()));reads.add(new SectorTransaction.Read(grass(i),Blocks.GRASS_BLOCK.defaultBlockState()));grass.add(grass(i));}
            cells.add(new SectorTransaction.Cell(bookPos,Blocks.BARREL.defaultBlockState(),Blocks.BARREL.defaultBlockState()));reads.add(new SectorTransaction.Read(offWhitelist,Blocks.GRASS_BLOCK.defaultBlockState()));
            return new SectorTransaction.Plan("north","natural-fixture-L"+source,"natural-fixture-L"+(source+1),source,source+1,cells,reads,natural?SectorNaturalSupportPolicy.coveredGrass(grass):SectorNaturalSupportPolicy.NONE);
        }
        void set(BlockPos p,BlockState state){level.getChunkAt(p);level.setBlock(p,state,2|16|32);}
        void stage()throws IOException {for(var c:plan.cells()){set(c.pos(),c.before());for(var d:net.minecraft.core.Direction.values())if(!d.equals(net.minecraft.core.Direction.DOWN))set(c.pos().relative(d),Blocks.AIR.defaultBlockState());}for(var r:plan.reads())set(r.pos(),r.state());set(bookPos,Blocks.BARREL.defaultBlockState());
            ItemStack book=new ItemStack(Items.WRITTEN_BOOK);book.getOrCreateTag().putString("title","Native current management NBT");book.getOrCreateTag().putString("author","Owner");((BarrelBlockEntity)level.getBlockEntity(bookPos)).setItem(0,book);save();}
        void decay(int i){BlockPos p=grass(i);BlockState before=level.getBlockState(p);if(!before.is(Blocks.GRASS_BLOCK))throw new AssertionError("Expected native grass before decay");if(!level.isAreaLoaded(p,3))throw new AssertionError("Native grass fixture neighborhood not fully loaded");Blocks.GRASS_BLOCK.randomTick(before,level,p,RandomSource.create(7));if(!level.getBlockState(p).is(Blocks.DIRT))throw new AssertionError("Native opaque-cover random tick did not decay grass");}
        CompoundTag book(){return level.getBlockEntity(bookPos).saveWithFullMetadata().copy();}
        void upgrade()throws IOException {proof=ProducerJournal.read(basicFile);plan=make(1,true);file=dir.resolve("upgrade.dat");}
        void reload()throws IOException {CompoundTag n=ProducerJournal.read(ledger);nation=NationData.load(n.getCompound("nation"));progress=n.getInt("progress");receipt=n.getString("receipt");ProducerJournal.read(file);}
        public ServerLevel level(){return level;}public Path file(){return file;}public SectorTransaction.Plan plan(){return plan;}public EconomyCost cost(){return price;}public NationData nation(){return nation;}public int progress(){return progress;}public void progress(int value){progress=value;}public String receipt(){return receipt;}public void receipt(String value){receipt=value;}
        public CompoundTag identity(){CompoundTag n=new CompoundTag();n.putString("world",dir.toString());n.putString("city","a");n.putString("sector","north");n.putUUID("owner",nation.id());return n;}
        public CompoundTag inheritedNaturalSupportProof()throws IOException {if(proofForbidden)throw new IOException("Recovery queried mutable external proof");return proof.copy();}
        public void requirements(boolean rollback){}public void allow(BlockPos pos)throws IOException {if(plan.cells().stream().noneMatch(c->c.pos().equals(pos)&&!c.before().equals(c.after())))throw new IOException("Outside tiny fixture writes");}
        public void save()throws IOException {CompoundTag n=new CompoundTag();n.put("nation",nation.save());n.putInt("progress",progress);n.putString("receipt",receipt);ProducerJournal.write(ledger,n,Files.exists(ledger));}
    }
}
