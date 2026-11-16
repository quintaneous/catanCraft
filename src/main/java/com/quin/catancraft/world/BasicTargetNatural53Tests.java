package com.quin.catancraft.world;

import com.quin.catancraft.map.MapDefinitionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.*;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/** Native target-cover decay during an actual pending TH1->TH2 purchase.
 * This does not normalize source dirt or grant permission from a future cover. */
@GameTestHolder("catancraft_basic_target_natural53") @PrefixGameTestTemplate(false)
public final class BasicTargetNatural53Tests {
    private BasicTargetNatural53Tests() {}
    private static Map<BlockPos,BlockState> covers(ConstructionSafetyTests.F f)throws Exception {
        var covers=BasicConstructionPurchase.th2TargetGrassCover(f.plan.target());
        f.h.assertTrue(covers.size()==62,"Pinned TH2 target cover count changed");return covers;
    }
    private static void decay(ConstructionSafetyTests.F f,Map<BlockPos,BlockState> covers) {
        for(var entry:covers.entrySet()) {
            BlockPos p=entry.getKey();f.h.assertTrue(f.level.getBlockState(p.above()).equals(entry.getValue()),"Target cover not physically present");
            f.h.assertTrue(f.level.getBlockState(p).equals(Blocks.GRASS_BLOCK.defaultBlockState()),"Target grass has not been placed");
            Blocks.GRASS_BLOCK.randomTick(f.level.getBlockState(p),f.level,p,RandomSource.create(5302));
            f.h.assertTrue(f.level.getBlockState(p).equals(Blocks.DIRT.defaultBlockState()),"Native TH2 target decay did not occur at "+p);
        }
        com.mojang.logging.LogUtils.getLogger().info("TH2 TARGET53 NATIVE city={} decayed=62/62 physicallyPresentCovers=62/62",f.request.city());
    }
    private static void agedTarget(GameTestHelper h,String city)throws Exception {
        ConstructionSafetyTests.with(h,city,"th2","town_hall",1,f->{
            var covers=covers(f);f.prepare();byte[] original=Files.readAllBytes(f.original());
            CompoundTag unpaid=f.data.save(new CompoundTag()),book=f.level.getBlockEntity(f.plan.manager()).saveWithFullMetadata().copy();
            long cash=f.nation().treasury();boolean[] interrupted={false};
            PaidRecoveryTests.refused(h,()->PaidConstructionRecovery.resume(f.env(),(stage,writes)->{
                if(stage.equals("write")&&covers.entrySet().stream().allMatch(e->f.level.getBlockState(e.getKey().above()).equals(e.getValue()))) {
                    decay(f,covers);interrupted[0]=true;throw new IOException("injected-native-target-decay");
                }
            }),"injected-native-target-decay");
            h.assertTrue(interrupted[0]&&unpaid.equals(f.data.save(new CompoundTag())),"Target decay interruption charged or missed physical cover boundary");
            f.reload();
            for(var cell:f.env().cells())if(covers.containsKey(cell.pos()))h.assertTrue(cell.target().equals(Blocks.DIRT.defaultBlockState()),
                    "Recovery scheduled a write over naturally decayed soil");
            f.complete();f.reload();
            for(var cell:f.plan.target()) {
                BlockState actual=f.level.getBlockState(cell.position());
                h.assertTrue(TownHallInteractionPolicy.matches(actual,cell.state(),cell.position().equals(f.plan.manager()))
                        ||covers.containsKey(cell.position())&&actual.equals(Blocks.DIRT.defaultBlockState())
                        &&f.level.getBlockState(cell.position().above()).equals(covers.get(cell.position())),
                        "Unexpected recovered TH2 cell at "+cell.position());
            }
            for(BlockPos p:covers.keySet())h.assertTrue(f.level.getBlockState(p).equals(Blocks.DIRT.defaultBlockState()),"Recovery repainted target soil");
            h.assertTrue(f.territory().cityLevel()==2&&f.nation().treasury()==cash-BasicConstructionPurchase.price(f.request).money(),"TH2 target decay changed settlement");
            h.assertTrue(book.equals(f.level.getBlockEntity(f.plan.manager()).saveWithFullMetadata()),"Target decay changed management book");
            CompoundTag paid=f.data.save(new CompoundTag());h.assertTrue(PaidConstructionRecovery.resume(f.env()).writes()==0
                    &&paid.equals(f.data.save(new CompoundTag())),"Target decay recovery wrote or charged twice");
            h.assertTrue(Arrays.equals(original,Files.readAllBytes(f.original())),"Target decay recovery rewrote original before-image");h.succeed();
        });
    }
    @GameTest(template="townhall_state_empty",batch="targetnatural53_01",timeoutTicks=12000)
    public static void north_partial_th2_target_decay_preserves_soil_and_receipt(GameTestHelper h)throws Exception{agedTarget(h,"a");}
    @GameTest(template="townhall_state_empty",batch="targetnatural53_02",timeoutTicks=12000)
    public static void south_partial_th2_target_decay_preserves_soil_and_receipt(GameTestHelper h)throws Exception{agedTarget(h,"i");}

    @GameTest(template="townhall_state_empty",batch="targetnatural53_03",timeoutTicks=12000)
    public static void future_missing_foreign_and_altered_target_covers_refuse(GameTestHelper h)throws Exception {
        ConstructionSafetyTests.with(h,"a","th2","town_hall",1,f->{
            var covers=covers(f);Map<BlockPos,BlockState> source=new HashMap<>();for(var c:f.plan.source())source.put(c.position(),c.state());
            BlockPos p=covers.keySet().stream().filter(pos->source.get(pos).is(Blocks.GRASS_BLOCK)
                    &&source.get(pos.above()).isAir()&&covers.get(pos).is(Blocks.STONE_BRICKS)).findFirst().orElseThrow();
            f.prepare();byte[] original=Files.readAllBytes(f.original());CompoundTag ledger=f.data.save(new CompoundTag());
            f.level.setBlock(p,Blocks.DIRT.defaultBlockState(),ConstructionSafetyTests.FLAGS);
            PaidRecoveryTests.refused(h,f::complete,"Construction source conflict");
            h.assertTrue(ledger.equals(f.data.save(new CompoundTag()))&&!Files.exists(PaidConstructionRecovery.companion(f.original())),"Future cover began recovery or charged");
            f.level.setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),ConstructionSafetyTests.FLAGS);
            PaidRecoveryTests.interrupt(h,f.env(),"physical_saved",-1);decay(f,covers);
            byte[] companion=Files.readAllBytes(PaidConstructionRecovery.companion(f.original()));
            for(BlockState wrong:List.of(Blocks.AIR.defaultBlockState(),Blocks.GOLD_BLOCK.defaultBlockState())) {
                f.level.setBlock(p.above(),wrong,ConstructionSafetyTests.FLAGS);
                PaidRecoveryTests.refused(h,f::complete,"Construction source conflict");
                h.assertTrue(ledger.equals(f.data.save(new CompoundTag()))&&f.level.getBlockState(p).is(Blocks.DIRT),"Missing/foreign cover charged or repainted soil");
                h.assertTrue(Arrays.equals(original,Files.readAllBytes(f.original()))
                        &&Arrays.equals(companion,Files.readAllBytes(PaidConstructionRecovery.companion(f.original()))),"Cover refusal rewrote history");
            }
            f.level.setBlock(p.above(),covers.get(p),ConstructionSafetyTests.FLAGS);
            BlockPos log=covers.keySet().stream().filter(pos->covers.get(pos).is(Blocks.SPRUCE_LOG)).findFirst().orElseThrow();
            f.level.setBlock(log.above(),covers.get(log).setValue(RotatedPillarBlock.AXIS,net.minecraft.core.Direction.Axis.X),ConstructionSafetyTests.FLAGS);
            PaidRecoveryTests.refused(h,f::complete,"Construction source conflict");
            h.assertTrue(ledger.equals(f.data.save(new CompoundTag()))&&Arrays.equals(original,Files.readAllBytes(f.original()))
                    &&Arrays.equals(companion,Files.readAllBytes(PaidConstructionRecovery.companion(f.original()))),"Altered target cover changed ledger/history");h.succeed();
        });
    }
    @GameTest(template="townhall_state_empty",batch="targetnatural53_04",timeoutTicks=12000)
    public static void source_capture_and_sealed_source_cannot_borrow_target_cover(GameTestHelper h)throws Exception {
        ConstructionSafetyTests.with(h,"i","th2","town_hall",1,f->{
            var covers=covers(f);Map<BlockPos,BlockState> source=new HashMap<>();for(var c:f.plan.source())source.put(c.position(),c.state());
            BlockPos p=covers.keySet().stream().filter(pos->source.get(pos).is(Blocks.GRASS_BLOCK)&&source.get(pos.above()).isAir()).findFirst().orElseThrow();
            CompoundTag ledger=f.data.save(new CompoundTag());f.level.setBlock(p,Blocks.DIRT.defaultBlockState(),ConstructionSafetyTests.FLAGS);
            PaidRecoveryTests.refused(h,f::prepare,"Construction source conflict");
            h.assertTrue(!Files.exists(f.original())&&ledger.equals(f.data.save(new CompoundTag())),"Future target cover relaxed original source capture");
            f.level.setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),ConstructionSafetyTests.FLAGS);f.prepare();
            CompoundTag intent=ProducerJournal.read(f.original()),snapshot=intent.getCompound("snapshot");ListTag palette=snapshot.getList("palette",Tag.TAG_COMPOUND);
            int dirt=palette.size();palette.add(NbtUtils.writeBlockState(Blocks.DIRT.defaultBlockState()));int[] states=snapshot.getIntArray("states");
            for(int i=0;i<states.length;i++)if(f.plan.source().get(i).position().equals(p))states[i]=dirt;
            snapshot.putIntArray("states",states);ProducerJournal.write(f.original(),intent,true);byte[] altered=Files.readAllBytes(f.original());
            f.level.setBlock(p,Blocks.DIRT.defaultBlockState(),ConstructionSafetyTests.FLAGS);
            f.level.setBlock(p.above(),covers.get(p),ConstructionSafetyTests.FLAGS);
            PaidRecoveryTests.refused(h,f::complete,"Captured source");
            h.assertTrue(ledger.equals(f.data.save(new CompoundTag()))&&!Files.exists(PaidConstructionRecovery.companion(f.original()))
                    &&Arrays.equals(altered,Files.readAllBytes(f.original())),"Invalid sealed source gained target-cover authority");h.succeed();
        });
    }
    @GameTest(template="townhall_state_empty",batch="targetnatural53_05",timeoutTicks=12000)
    public static void th2_target_cover_is_exact_and_qualified_across_all_cities(GameTestHelper h)throws Exception {
        for(char city='a';city<='p';city++) {
            var def=MapDefinitionManager.territory(String.valueOf(city));var anchor=SchematicAssetRegistry.territoryAnchor("th2",def);
            var target=BasicConstructionAssets.plan("th2",anchor);var source=BasicConstructionAssets.plan("th1",anchor);
            var covers=BasicConstructionPurchase.th2TargetGrassCover(target);h.assertTrue(covers.size()==62,"Missing TH2 target cover in "+city);
            int grass=0,andesite=0;for(var cell:source)if(covers.containsKey(cell.position())) {
                if(cell.state().is(Blocks.GRASS_BLOCK))grass++;else if(cell.state().is(Blocks.ANDESITE))andesite++;
            }
            h.assertTrue(grass==48&&andesite==14,"Pinned TH1 source under target cover changed");
            var changed=new ArrayList<>(target);var first=changed.get(0);
            changed.set(0,new SchematicPlacementService.PlanCell(first.localX(),first.localY(),first.localZ(),first.position(),Blocks.GOLD_BLOCK.defaultBlockState()));
            h.assertTrue(BasicConstructionPurchase.th2TargetGrassCover(changed).isEmpty(),"Changed target inherited cover authority");
            h.assertTrue(BasicConstructionPurchase.th2TargetGrassCover(source).isEmpty()
                    &&BasicConstructionPurchase.th2TargetGrassCover(BasicConstructionAssets.plan("th3",anchor)).isEmpty(),"Other Hall inherited TH2 target cover");
        }
        h.succeed();
    }
}
