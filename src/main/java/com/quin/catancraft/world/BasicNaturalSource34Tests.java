package com.quin.catancraft.world;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.*;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/** Native vanilla decay of exact delivered source halls; no relaxed source fixture. */
@GameTestHolder("catancraft_basic_natural34") @PrefixGameTestTemplate(false)
public final class BasicNaturalSource34Tests {
    private static Map<BlockPos,BlockState> source(ConstructionSafetyTests.F f) {
        Map<BlockPos,BlockState> out=new HashMap<>();
        for(var c:f.plan.source())out.put(c.position(),c.state());
        return out;
    }
    private static List<BlockPos> covered(ConstructionSafetyTests.F f) {
        Map<BlockPos,BlockState> source=source(f);List<BlockPos> out=new ArrayList<>();
        for(var c:f.plan.source())if(c.state().equals(Blocks.GRASS_BLOCK.defaultBlockState())
                &&SectorNaturalSupportPolicy.blocksGrass(source.get(c.position().above()),c.position().above()))out.add(c.position());
        return out;
    }
    private static BlockPos exposed(ConstructionSafetyTests.F f)throws IOException {
        Map<BlockPos,BlockState> source=source(f);
        for(var c:f.plan.source())if(c.state().equals(Blocks.GRASS_BLOCK.defaultBlockState())
                &&Blocks.AIR.defaultBlockState().equals(source.get(c.position().above())))return c.position();
        throw new IOException("No exposed authored source grass");
    }
    private static void decay(ConstructionSafetyTests.F f,List<BlockPos> positions) {
        for(BlockPos p:positions){
            f.h.assertTrue(f.level.getBlockState(p).is(Blocks.GRASS_BLOCK),"Native decay source is not grass");
            Blocks.GRASS_BLOCK.randomTick(f.level.getBlockState(p),f.level,p,RandomSource.create(34));
            f.h.assertTrue(f.level.getBlockState(p).equals(Blocks.DIRT.defaultBlockState()),"Vanilla covered grass failed to decay at "+p);
        }
    }
    private static void verifyTarget(ConstructionSafetyTests.F f,List<BlockPos> natural)throws Exception {
        Set<BlockPos> allowed=new HashSet<>(natural);
        for(var c:f.plan.target()){
            BlockState actual=f.level.getBlockState(c.position());
            if(allowed.contains(c.position())&&c.state().is(Blocks.GRASS_BLOCK))
                f.h.assertTrue(actual.equals(Blocks.DIRT.defaultBlockState()),"Natural dirt was repainted at "+c.position());
            else f.h.assertTrue(TownHallInteractionPolicy.matches(actual,c.state(),c.position().equals(f.plan.manager())),"Unexpected target at "+c.position());
        }
    }
    private static void agedHall(GameTestHelper h,String city,int from,int expected)throws Exception {
        ConstructionSafetyTests.with(h,city,"th"+(from+1),"town_hall",from,f->{
            List<BlockPos> natural=covered(f);h.assertTrue(natural.size()==expected,"Pinned covered-grass source count changed");
            decay(f,natural);long cash=f.nation().treasury();CompoundTag book=f.level.getBlockEntity(f.plan.manager()).saveWithFullMetadata().copy();
            f.prepare();byte[] original=Files.readAllBytes(f.original());PaidRecoveryTests.interrupt(h,f.env(),"write",37);
            f.reload();f.complete();verifyTarget(f,natural);
            h.assertTrue(f.nation().treasury()==cash-BasicConstructionPurchase.price(f.request).money()&&f.territory().cityLevel()==from+1,"Aged Hall charged incorrectly");
            h.assertTrue(book.equals(f.level.getBlockEntity(f.plan.manager()).saveWithFullMetadata()),"Actual management book changed");
            f.reload();h.assertTrue(PaidConstructionRecovery.resume(f.env()).writes()==0,"Completed aged Hall rewrote blocks");
            h.assertTrue(f.nation().treasury()==cash-BasicConstructionPurchase.price(f.request).money(),"Completed aged Hall charged twice");
            h.assertTrue(Arrays.equals(original,Files.readAllBytes(f.original())),"Aged Hall intent rewritten");h.succeed();
        });
    }
    @GameTest(template="townhall_state_empty",batch="basicnatural01",timeoutTicks=12000)
    public static void aged_th1_north_native_decay_partial_recovery(GameTestHelper h)throws Exception {agedHall(h,"a",1,2);}
    @GameTest(template="townhall_state_empty",batch="basicnatural02",timeoutTicks=12000)
    public static void aged_th2_south_native_decay_partial_recovery(GameTestHelper h)throws Exception {agedHall(h,"i",2,62);}

    @GameTest(template="townhall_state_empty",batch="basicnatural03",timeoutTicks=12000)
    public static void original_source_cover_proves_decay_after_intent(GameTestHelper h)throws Exception {
        ConstructionSafetyTests.with(h,"a","th2","town_hall",1,f->{
            List<BlockPos> natural=covered(f);h.assertTrue(natural.size()==2,"Pinned source cover count");
            f.prepare();byte[] original=Files.readAllBytes(f.original());long cash=f.nation().treasury();
            decay(f,natural);PaidRecoveryTests.interrupt(h,f.env(),"write",37);f.reload();f.complete();verifyTarget(f,natural);
            h.assertTrue(f.nation().treasury()==cash-BasicConstructionPurchase.price(f.request).money(),"Deferred native decay changed price");
            h.assertTrue(Arrays.equals(original,Files.readAllBytes(f.original())),"Deferred native decay rewrote history");h.succeed();
        });
    }
    @GameTest(template="townhall_state_empty",batch="basicnatural04",timeoutTicks=12000)
    public static void exposed_source_dirt_is_not_covered_grass(GameTestHelper h)throws Exception {
        ConstructionSafetyTests.with(h,"a","th2","town_hall",1,f->{
            CompoundTag before=f.data.save(new CompoundTag());BlockPos p=exposed(f);
            f.level.setBlock(p,Blocks.DIRT.defaultBlockState(),ConstructionSafetyTests.FLAGS);
            PaidRecoveryTests.refused(h,f::prepare,"source conflict");
            h.assertTrue(!Files.exists(f.original())&&before.equals(f.data.save(new CompoundTag())),"Unexposed dirt created intent/payment");
            h.assertTrue(f.level.getBlockState(p).is(Blocks.DIRT),"Refusal repainted exposed ground");h.succeed();
        });
    }
    @GameTest(template="townhall_state_empty",batch="basicnatural05",timeoutTicks=12000)
    public static void covered_foreign_material_is_not_natural_dirt(GameTestHelper h)throws Exception {
        ConstructionSafetyTests.with(h,"a","th2","town_hall",1,f->{
            CompoundTag before=f.data.save(new CompoundTag());BlockPos p=covered(f).get(0);
            f.level.setBlock(p,Blocks.GOLD_BLOCK.defaultBlockState(),ConstructionSafetyTests.FLAGS);
            PaidRecoveryTests.refused(h,f::prepare,"source conflict");
            h.assertTrue(!Files.exists(f.original())&&before.equals(f.data.save(new CompoundTag())),"Foreign support created intent/payment");
            h.assertTrue(f.level.getBlockState(p).is(Blocks.GOLD_BLOCK),"Refusal erased foreign support");h.succeed();
        });
    }
    @GameTest(template="townhall_state_empty",batch="basicnatural06",timeoutTicks=12000)
    public static void captured_dirt_cannot_invent_a_source_cover(GameTestHelper h)throws Exception {
        ConstructionSafetyTests.with(h,"a","th2","town_hall",1,f->{
            f.prepare();CompoundTag before=f.data.save(new CompoundTag());BlockPos p=exposed(f);
            CompoundTag intent=ProducerJournal.read(f.original()),snapshot=intent.getCompound("snapshot");
            ListTag palette=snapshot.getList("palette",Tag.TAG_COMPOUND);int dirt=palette.size();palette.add(NbtUtils.writeBlockState(Blocks.DIRT.defaultBlockState()));
            int[] states=snapshot.getIntArray("states");int index=-1;
            for(int i=0;i<f.plan.source().size();i++)if(f.plan.source().get(i).position().equals(p)){index=i;break;}
            h.assertTrue(index>=0,"Missing exposed source cell");states[index]=dirt;snapshot.putIntArray("states",states);
            ProducerJournal.write(f.original(),intent,true);byte[] altered=Files.readAllBytes(f.original());
            PaidRecoveryTests.refused(h,f::complete,"Captured source");
            h.assertTrue(before.equals(f.data.save(new CompoundTag()))&&!Files.exists(PaidConstructionRecovery.companion(f.original())),"Invalid captured dirt began recovery/payment");
            h.assertTrue(Arrays.equals(altered,Files.readAllBytes(f.original())),"Refusal rewrote altered source history");h.succeed();
        });
    }
    @GameTest(template="townhall_state_empty",batch="basicnatural07",timeoutTicks=12000)
    public static void future_target_cover_alone_does_not_authorize_dirt(GameTestHelper h)throws Exception {
        ConstructionSafetyTests.with(h,"a","th2","town_hall",1,f->{
            Map<BlockPos,BlockState> source=source(f),target=new HashMap<>();
            for(var c:f.plan.target())target.put(c.position(),c.state());
            BlockPos newlyCovered=null;
            for(var c:f.plan.source())if(c.state().is(Blocks.GRASS_BLOCK)
                    &&Blocks.AIR.defaultBlockState().equals(source.get(c.position().above()))
                    &&Blocks.GRASS_BLOCK.defaultBlockState().equals(target.get(c.position()))
                    &&SectorNaturalSupportPolicy.blocksGrass(target.get(c.position().above()),c.position().above())){
                newlyCovered=c.position();break;
            }
            h.assertTrue(newlyCovered!=null,"Pinned target has no newly covered grass");
            f.prepare();byte[] original=Files.readAllBytes(f.original());long cash=f.nation().treasury();
            PaidRecoveryTests.interrupt(h,f.env(),"physical_saved",-1);decay(f,List.of(newlyCovered));
            byte[] companion=Files.readAllBytes(PaidConstructionRecovery.companion(f.original()));
            PaidRecoveryTests.refused(h,f::complete,"Construction source conflict");
            h.assertTrue(f.nation().treasury()==cash&&f.territory().cityLevel()==1,"Unproven target decay charged");
            h.assertTrue(f.level.getBlockState(newlyCovered).is(Blocks.DIRT),"Unproven target decay repainted ground");
            h.assertTrue(Arrays.equals(original,Files.readAllBytes(f.original()))
                    &&Arrays.equals(companion,Files.readAllBytes(PaidConstructionRecovery.companion(f.original()))),"Unproven exposure rewrote history");h.succeed();
        });
    }
}
