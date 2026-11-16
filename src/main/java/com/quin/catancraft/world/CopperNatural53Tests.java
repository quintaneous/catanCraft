package com.quin.catancraft.world;

import com.quin.catancraft.data.ResourceType;
import com.quin.catancraft.map.MapDefinitionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraftforge.gametest.*;
import java.nio.file.Files;
import java.util.*;

/** Exact pinned factory targets and native oxidation. Successful random draws
 * are forced to bound the fixture; this tests vanilla transitions and recovery,
 * not the elapsed time or probability of oxidation in an owner world. */
@GameTestHolder("catancraft_copper_natural53") @PrefixGameTestTemplate(false)
public final class CopperNatural53Tests {
    private CopperNatural53Tests() {}
    private static final List<BlockState> AGES=List.of(Blocks.CUT_COPPER.defaultBlockState(),
            Blocks.EXPOSED_CUT_COPPER.defaultBlockState(),Blocks.WEATHERED_CUT_COPPER.defaultBlockState(),
            Blocks.OXIDIZED_CUT_COPPER.defaultBlockState());
    private static Set<BlockPos> targets(ConstructionSafetyTests.F f)throws Exception {
        Set<BlockPos> positions=BasicConstructionPurchase.factoryCopperTargets(f.plan.target());
        f.h.assertTrue(positions.size()==42,"Pinned factory must have exactly42 eligible copper targets");
        for(var cell:f.plan.source())if(positions.contains(cell.position()))
            f.h.assertTrue(cell.state().equals(Blocks.AIR.defaultBlockState()),"Copper source must remain exact starter AIR");
        return positions;
    }
    private static void nativeAge(ConstructionSafetyTests.F f,Set<BlockPos> positions,int age) {
        RandomSource draws=new LegacyRandomSource(53){@Override public float nextFloat(){return 0.0F;}};
        int calls=0,sweeps=0;
        for(;sweeps<32&&!positions.stream().allMatch(p->f.level.getBlockState(p).equals(AGES.get(age)));sweeps++) {
            for(BlockPos p:positions) {
                BlockState live=f.level.getBlockState(p);
                f.h.assertTrue(live.getBlock() instanceof WeatheringCopper,"Expected a native weathering block");
                WeatheringCopper copper=(WeatheringCopper)live.getBlock();
                if(copper.getAge().ordinal()<age){live.getBlock().randomTick(live,f.level,p,draws);calls++;}
            }
        }
        for(BlockPos p:positions)f.h.assertTrue(f.level.getBlockState(p).equals(AGES.get(age)),
                "Native oxidation did not reach requested age at "+p);
        f.h.assertTrue(calls>0,"Oxidation fixture must invoke native random ticks");
        com.mojang.logging.LogUtils.getLogger().info(
                "COPPER53 NATIVE city={} asset={} plot={} age={} aged=42/42 ticks={} sweeps={} randomDraws=forced-eligible",
                f.request.city(),f.request.asset(),f.request.plot(),age,calls,sweeps);
    }
    private static void geometry(ConstructionSafetyTests.F f,Set<BlockPos> copper)throws Exception {
        for(var cell:f.plan.target()) {
            BlockState live=f.level.getBlockState(cell.position());
            f.h.assertTrue(TownHallInteractionPolicy.matches(live,cell.state(),false)
                    ||BasicConstructionPurchase.agedCopperTarget(cell.position(),live,copper),
                    "Factory target changed outside qualified copper at "+cell.position());
        }
    }
    private static void agedFactory(GameTestHelper h,String city,String asset,String plot)throws Exception {
        ConstructionSafetyTests.with(h,city,asset,plot,0,f->{
            Set<BlockPos> copper=targets(f);CompoundTag unpaid=f.data.save(new CompoundTag());
            long cash=f.nation().treasury();Map<ResourceType,Long> resources=new EnumMap<>(ResourceType.class);
            for(ResourceType type:ResourceType.values())resources.put(type,f.nation().resource(type));
            f.prepare();byte[] original=Files.readAllBytes(f.original());
            var cost=DefenseService.historicalCost(ProducerJournal.read(original).getCompound("cost"));
            PaidRecoveryTests.interrupt(h,f.env(),"physical_saved",-1);f.reload();
            h.assertTrue(unpaid.equals(f.data.save(new CompoundTag())),"Pending factory charged before physical save boundary");
            for(int age=1;age<=3;age++) {
                nativeAge(f,copper,age);geometry(f,copper);
                Map<BlockPos,BlockState> actual=new HashMap<>();for(BlockPos p:copper)actual.put(p,f.level.getBlockState(p));
                if(age<3)PaidRecoveryTests.interrupt(h,f.env(),"physical_saved",-1);else f.complete();
                f.reload();
                for(BlockPos p:copper)h.assertTrue(actual.get(p).equals(f.level.getBlockState(p)),"Recovery repainted naturally aged copper");
                if(age<3)h.assertTrue(unpaid.equals(f.data.save(new CompoundTag())),"Intermediate aging charged pending purchase");
                h.assertTrue(Arrays.equals(original,Files.readAllBytes(f.original())),"Oxidation recovery rewrote original purchase history");
            }
            geometry(f,copper);h.assertTrue(f.nation().treasury()==cash-cost.money(),"Oxidation changed original money charge");
            for(ResourceType type:ResourceType.values())h.assertTrue(f.nation().resource(type)
                    ==resources.get(type)-cost.resources().getOrDefault(type,0L),"Oxidation changed original resource charge: "+type);
            h.assertTrue(f.territory().buildings().size()==1&&f.territory().buildings().get(0).plotId().equals(plot),"Factory settled in wrong plot");
            CompoundTag paid=f.data.save(new CompoundTag());
            h.assertTrue(PaidConstructionRecovery.resume(f.env()).writes()==0&&paid.equals(f.data.save(new CompoundTag())),"Completed copper purchase charged/wrote twice");
            h.assertTrue(Arrays.equals(original,Files.readAllBytes(f.original())),"Duplicate recovery changed immutable intent");h.succeed();
        });
    }
    @GameTest(template="townhall_state_empty",batch="copper53_01",timeoutTicks=12000)
    public static void refinery_zero_native_ages_recover_once(GameTestHelper h)throws Exception{agedFactory(h,"a","fuel_refinery","plot_1");}
    @GameTest(template="townhall_state_empty",batch="copper53_02",timeoutTicks=12000)
    public static void refinery_halfturn_native_ages_recover_once(GameTestHelper h)throws Exception{agedFactory(h,"i","fuel_refinery","plot_1");}
    @GameTest(template="townhall_state_empty",batch="copper53_03",timeoutTicks=12000)
    public static void chemical_halfturn_native_ages_recover_once(GameTestHelper h)throws Exception{agedFactory(h,"a","chemical_plant","plot_4");}
    @GameTest(template="townhall_state_empty",batch="copper53_04",timeoutTicks=12000)
    public static void chemical_zero_native_ages_recover_once(GameTestHelper h)throws Exception{agedFactory(h,"i","chemical_plant","plot_4");}

    private static void foreignSource(GameTestHelper h,String city,String asset)throws Exception {
        ConstructionSafetyTests.with(h,city,asset,"plot_3",0,f->{
            BlockPos p=targets(f).iterator().next();CompoundTag ledger=f.data.save(new CompoundTag());
            for(BlockState aged:AGES) {
                f.level.setBlock(p,aged,ConstructionSafetyTests.FLAGS);
                PaidRecoveryTests.refused(h,f::prepare,"Construction source conflict");
                h.assertTrue(!Files.exists(f.original())&&ledger.equals(f.data.save(new CompoundTag())),"Target copper relaxed starter source or charged");
                h.assertTrue(f.level.getBlockState(p).equals(aged),"Refusal erased existing copper");
            }
            h.succeed();
        });
    }
    @GameTest(template="townhall_state_empty",batch="copper53_05",timeoutTicks=12000)
    public static void refinery_copper_is_not_an_empty_plot_source(GameTestHelper h)throws Exception{foreignSource(h,"a","fuel_refinery");}
    @GameTest(template="townhall_state_empty",batch="copper53_06",timeoutTicks=12000)
    public static void chemical_copper_is_not_an_empty_plot_source(GameTestHelper h)throws Exception{foreignSource(h,"i","chemical_plant");}

    private static void foreignTarget(GameTestHelper h,String city,String asset)throws Exception {
        ConstructionSafetyTests.with(h,city,asset,"plot_1",0,f->{
            Set<BlockPos> copper=targets(f);BlockPos p=copper.iterator().next();f.prepare();
            PaidRecoveryTests.interrupt(h,f.env(),"physical_saved",-1);f.reload();
            byte[] original=Files.readAllBytes(f.original()),companion=Files.readAllBytes(PaidConstructionRecovery.companion(f.original()));
            CompoundTag ledger=f.data.save(new CompoundTag());
            List<BlockState> foreign=List.of(Blocks.WAXED_CUT_COPPER.defaultBlockState(),Blocks.WAXED_OXIDIZED_CUT_COPPER.defaultBlockState(),
                    Blocks.OXIDIZED_CUT_COPPER_STAIRS.defaultBlockState(),Blocks.OXIDIZED_CUT_COPPER_SLAB.defaultBlockState(),
                    Blocks.OXIDIZED_COPPER.defaultBlockState(),Blocks.GOLD_BLOCK.defaultBlockState());
            for(BlockState state:foreign) {
                h.assertTrue(!BasicConstructionPurchase.agedCopperTarget(p,state,copper),"Foreign copper shape/material qualified");
                f.level.setBlock(p,state,ConstructionSafetyTests.FLAGS);
                PaidRecoveryTests.refused(h,f::complete,"Construction source conflict");
                h.assertTrue(ledger.equals(f.data.save(new CompoundTag()))&&f.level.getBlockState(p).equals(state),"Foreign target was charged or overwritten");
                h.assertTrue(Arrays.equals(original,Files.readAllBytes(f.original()))
                        &&Arrays.equals(companion,Files.readAllBytes(PaidConstructionRecovery.companion(f.original()))),"Refusal rewrote history");
            }
            f.level.setBlock(p,Blocks.CUT_COPPER.defaultBlockState(),ConstructionSafetyTests.FLAGS);
            BlockPos outside=f.plan.target().stream().filter(c->c.state().isAir()&&c.localY()>2)
                    .map(SchematicPlacementService.PlanCell::position).findFirst().orElseThrow();
            h.assertTrue(!BasicConstructionPurchase.agedCopperTarget(outside,AGES.get(3),copper),"Copper permission escaped pinned target mask");
            f.level.setBlock(outside,AGES.get(3),ConstructionSafetyTests.FLAGS);
            PaidRecoveryTests.refused(h,f::complete,"Construction source conflict");
            h.assertTrue(ledger.equals(f.data.save(new CompoundTag()))&&f.level.getBlockState(outside).equals(AGES.get(3)),"Outside copper was charged or overwritten");
            h.assertTrue(Arrays.equals(original,Files.readAllBytes(f.original()))
                    &&Arrays.equals(companion,Files.readAllBytes(PaidConstructionRecovery.companion(f.original()))),"Outside-mask refusal rewrote history");h.succeed();
        });
    }
    @GameTest(template="townhall_state_empty",batch="copper53_07",timeoutTicks=12000)
    public static void refinery_wax_shape_foreign_and_outside_copper_refused(GameTestHelper h)throws Exception{foreignTarget(h,"a","fuel_refinery");}
    @GameTest(template="townhall_state_empty",batch="copper53_08",timeoutTicks=12000)
    public static void chemical_wax_shape_foreign_and_outside_copper_refused(GameTestHelper h)throws Exception{foreignTarget(h,"i","chemical_plant");}

    @GameTest(template="townhall_state_empty",batch="copper53_09",timeoutTicks=12000)
    public static void target_qualification_covers_all_cities_slots_and_exact_pins(GameTestHelper h)throws Exception {
        h.assertTrue(BasicConstructionAssets.BUNDLE_SHA.equals("cbaf8b095958f7de19a58bbca2723f18a1273d908eb5f174d36cd4aa1e9608d1"),"Factory bundle pin changed");
        int sites=0;Set<Integer> rotations=new HashSet<>();
        for(char city='a';city<='p';city++)for(String asset:List.of("fuel_refinery","chemical_plant"))for(String plot:IndustrialPlotService.INDUSTRIAL_PLOTS) {
            var def=MapDefinitionManager.territory(String.valueOf(city));
            var request=new BasicConstructionPurchase.Request(String.valueOf(city),UUID.randomUUID(),UUID.randomUUID(),"factory",asset,plot,0,1);
            var plan=BasicConstructionPurchase.blueprint(request,def);Set<BlockPos> copper=BasicConstructionPurchase.factoryCopperTargets(plan.target());
            h.assertTrue(copper.size()==42,"Missing qualified copper in "+city+"/"+asset+"/"+plot);
            for(var source:plan.source())if(copper.contains(source.position()))h.assertTrue(source.state().equals(Blocks.AIR.defaultBlockState()),"Copper site source is not starter AIR");
            rotations.add(BasicConstructionPurchase.anchor(request,def).rotation());sites++;
            var altered=new ArrayList<>(plan.target());var first=altered.get(0);
            altered.set(0,new SchematicPlacementService.PlanCell(first.localX(),first.localY(),first.localZ(),first.position(),Blocks.GOLD_BLOCK.defaultBlockState()));
            h.assertTrue(BasicConstructionPurchase.factoryCopperTargets(altered).isEmpty(),"Changed complete target inherited copper mask");
        }
        h.assertTrue(sites==96&&rotations.equals(Set.of(0,180)),"Incomplete all-city/slot/orientation copper qualification");
        for(var type:IndustrialPlotService.INDUSTRIES) {
            String asset=SchematicAssetRegistry.buildingAsset(type);if(asset.equals("fuel_refinery")||asset.equals("chemical_plant"))continue;
            var def=MapDefinitionManager.territory("d");var request=new BasicConstructionPurchase.Request("d",UUID.randomUUID(),UUID.randomUUID(),"factory",asset,"plot_3",0,1);
            h.assertTrue(BasicConstructionPurchase.factoryCopperTargets(BasicConstructionPurchase.blueprint(request,def).target()).isEmpty(),"Other factory inherited copper permission");
        }
        h.assertTrue(BasicConstructionPurchase.factoryCopperTargets(BasicConstructionAssets.plan("th1",
                SchematicAssetRegistry.territoryAnchor("th1",MapDefinitionManager.territory("d")))).isEmpty(),"Town Hall inherited factory copper permission");
        h.succeed();
    }
}
