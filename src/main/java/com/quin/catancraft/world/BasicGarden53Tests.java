package com.quin.catancraft.world;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.*;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/** Calls vanilla grass spreading on the exact pinned TH1 source, without painting
 * a target grass block. Explicit random ticks accelerate the observation; this
 * does not establish normal-time growth frequency or owner-world acceptance. */
@GameTestHolder("catancraft_basic_garden53") @PrefixGameTestTemplate(false)
public final class BasicGarden53Tests {
    private BasicGarden53Tests() {}
    private static final String PIN="cbaf8b095958f7de19a58bbca2723f18a1273d908eb5f174d36cd4aa1e9608d1";

    static boolean gardenLocal(SchematicPlacementService.PlanCell c) {
        return c.localY()==1 && c.localZ()>=30 && c.localZ()<=33
                && (c.localX()>=15 && c.localX()<=21 || c.localX()>=53 && c.localX()<=59);
    }
    static List<BlockPos> garden(BasicConstructionPurchase.Blueprint plan) {
        return plan.source().stream().filter(BasicGarden53Tests::gardenLocal)
                .map(SchematicPlacementService.PlanCell::position).toList();
    }
    private static void proof(GameTestHelper h,String city)throws Exception {
        ConstructionSafetyTests.with(h,city,"th2","town_hall",1,f->{
            h.assertTrue(BasicConstructionAssets.BUNDLE_SHA.equals(PIN),"TH1 bundle pin changed");
            Map<BlockPos,BlockState> source=new HashMap<>();
            for(var cell:f.plan.source())source.put(cell.position(),cell.state());
            List<BlockPos> garden=garden(f.plan);h.assertTrue(garden.size()==56,"Pinned garden count changed");
            Set<BlockPos> sourceGrass=new LinkedHashSet<>();
            int adjacent=0;
            for(BlockPos pos:garden) {
                h.assertTrue(source.get(pos).equals(Blocks.DIRT.defaultBlockState()),"Garden source is not dirt");
                BlockState leaf=source.get(pos.above());
                h.assertTrue(leaf.is(Blocks.OAK_LEAVES) && leaf.getValue(LeavesBlock.PERSISTENT)
                        && leaf.getValue(LeavesBlock.DISTANCE)==1 && leaf.getFluidState().isEmpty(),
                        "Garden leaf source changed");
                h.assertTrue(source.get(pos.above(2)).isAir(),"Garden source sky column changed");
                boolean hasGrass=false;
                for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++) {
                    BlockPos neighbor=pos.offset(x,0,z);
                    if(Blocks.GRASS_BLOCK.defaultBlockState().equals(source.get(neighbor))
                            && source.get(neighbor.above()).isAir()) {
                        sourceGrass.add(neighbor);hasGrass=true;
                    }
                }
                if(hasGrass)adjacent++;
            }
            h.assertTrue(adjacent==28,"Pinned direct source-grass adjacency changed: "+adjacent);
            List<BlockPos> ticks=new ArrayList<>(sourceGrass);ticks.addAll(garden);
            if(city.equals("i"))f.prepare(); // Prove native growth after a sealed dirt before-image too.
            f.level.setDayTime(6000); // Fixture lighting only; no source/runtime setting change.
            SectorChunkLease lease=SectorChunkLease.acquire(f.level,BasicConstructionPurchase.bounds(
                    f.request,com.quin.catancraft.map.MapDefinitionManager.territory(city)));
            awaitLight(h,f,garden,ticks,lease,0);
        });
    }
    private static void awaitLight(GameTestHelper h,ConstructionSafetyTests.F f,
                                   List<BlockPos> garden,List<BlockPos> ticks,SectorChunkLease lease,int attempt) {
        h.runAfterDelay(attempt==0?20:2,()->{try {
            lease.keepAlive();
            boolean lit=lease.ready() && ticks.stream().allMatch(p->f.level.getMaxLocalRawBrightness(p.above())>=9);
            if(!lit && attempt<100){awaitLight(h,f,garden,ticks,lease,attempt+1);return;}
            h.assertTrue(lit,"Pinned garden light engine did not settle; no grass permission inferred");
            RandomSource random=RandomSource.create(0x530000L+f.request.city().charAt(0));
            int sweeps=0,grown=0;
            for(;sweeps<512 && grown<56;sweeps++) {
                for(BlockPos pos:ticks)if(f.level.getBlockState(pos).is(Blocks.GRASS_BLOCK))
                    Blocks.GRASS_BLOCK.randomTick(f.level.getBlockState(pos),f.level,pos,random);
                grown=(int)garden.stream().filter(p->f.level.getBlockState(p)
                        .equals(Blocks.GRASS_BLOCK.defaultBlockState())).count();
            }
            h.assertTrue(grown==56,"Vanilla random ticks did not reproduce all56 garden cells: "+grown);
            com.mojang.logging.LogUtils.getLogger().info(
                    "GARDEN53 NATIVE GROWTH city={} grown={}/56 sweeps={} resident={}",
                    f.request.city(),grown,sweeps,lease.ready());
            for(BlockPos pos:garden) {
                BlockState now=f.level.getBlockState(pos);
                h.assertTrue(now.equals(Blocks.DIRT.defaultBlockState()) || now.equals(Blocks.GRASS_BLOCK.defaultBlockState()),
                        "Unexpected native garden result");
                h.assertTrue(f.level.getBlockState(pos.above()).is(Blocks.OAK_LEAVES),"Native spreading changed the leaf cover");
            }
            // The authored hedges carry distance=1 but have no connecting logs.
            // Invoke the real scheduled leaf-tick method; never paint distance=7.
            for(int sweep=0;sweep<7;sweep++)for(BlockPos pos:garden) {
                BlockPos leaf=pos.above();Blocks.OAK_LEAVES.tick(f.level.getBlockState(leaf),f.level,leaf,random);
            }
            for(BlockPos pos:garden) {
                BlockState leaf=f.level.getBlockState(pos.above());
                h.assertTrue(leaf.is(Blocks.OAK_LEAVES)&&leaf.getValue(LeavesBlock.DISTANCE)==7
                        &&leaf.getValue(LeavesBlock.PERSISTENT)&&leaf.getFluidState().isEmpty(),
                        "Native hedge leaf distance did not settle at7");
            }
            com.mojang.logging.LogUtils.getLogger().info("GARDEN53 NATIVE LEAF DISTANCE city={} derived7=56/56",f.request.city());
            BasicConstructionPurchase.capture(f.level,f.plan);
            com.mojang.logging.LogUtils.getLogger().info(
                    "GARDEN53 NATIVE PROOF city={} rotation={} grown={}/56 sweeps={} capture={} bundle={}",
                    f.request.city(),com.quin.catancraft.map.MapDefinitionManager.territory(f.request.city()).cityCenter().rotation(),
                    grown,sweeps,"ACCEPTED",PIN);
            if(!Files.exists(f.original()))f.prepare();
            byte[] original=Files.readAllBytes(f.original());CompoundTag intent=ProducerJournal.read(original);
            CompoundTag snapshot=intent.getCompound("snapshot");Set<BlockPos> gardenSet=new HashSet<>(garden);
            for(int i=0;i<f.plan.source().size();i++)if(gardenSet.contains(f.plan.source().get(i).position())) {
                BlockState sealed=captured(snapshot,i),expected=f.request.city().equals("i")
                        ?Blocks.DIRT.defaultBlockState():f.level.getBlockState(f.plan.source().get(i).position());
                h.assertTrue(sealed.equals(expected),"Garden before-image was normalized instead of recording actual source");
            }
            long cash=f.nation().treasury();CompoundTag book=f.level.getBlockEntity(f.plan.manager()).saveWithFullMetadata().copy();
            PaidRecoveryTests.interrupt(h,f.env(),"write",37);f.reload();f.complete();verifyTarget(f);
            h.assertTrue(f.nation().treasury()==cash-BasicConstructionPurchase.price(f.request).money()
                    &&f.territory().cityLevel()==2,"Garden growth changed Hall settlement");
            h.assertTrue(book.equals(f.level.getBlockEntity(f.plan.manager()).saveWithFullMetadata()),"Actual management book changed");
            f.reload();h.assertTrue(PaidConstructionRecovery.resume(f.env()).writes()==0,"Garden recovery repeated writes");
            h.assertTrue(f.nation().treasury()==cash-BasicConstructionPurchase.price(f.request).money(),"Garden recovery charged twice");
            h.assertTrue(Arrays.equals(original,Files.readAllBytes(f.original())),"Garden recovery rewrote original intent");
            lease.close();h.succeed();
        }catch(Exception error){lease.close();throw new RuntimeException(error);}});
    }
    @GameTest(template="townhall_state_empty",batch="garden53proof01",timeoutTicks=12000)
    public static void native_grass_spreads_into_pinned_th1_garden_north(GameTestHelper h)throws Exception {proof(h,"a");}
    @GameTest(template="townhall_state_empty",batch="garden53proof02",timeoutTicks=12000)
    public static void native_grass_spreads_into_pinned_th1_garden_south(GameTestHelper h)throws Exception {proof(h,"i");}

    private static BlockState captured(CompoundTag snapshot,int index) {
        return NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),
                snapshot.getList("palette",Tag.TAG_COMPOUND).getCompound(snapshot.getIntArray("states")[index]));
    }
    private static void verifyTarget(ConstructionSafetyTests.F f) {
        Map<BlockPos,BlockState> source=new HashMap<>();for(var c:f.plan.source())source.put(c.position(),c.state());
        for(var c:f.plan.target()) {
            BlockState actual=f.level.getBlockState(c.position());
            boolean oldCoveredDecay=actual.equals(Blocks.DIRT.defaultBlockState())&&c.state().is(Blocks.GRASS_BLOCK)
                    &&source.get(c.position()).is(Blocks.GRASS_BLOCK)
                    &&SectorNaturalSupportPolicy.blocksGrass(source.get(c.position().above()),c.position().above());
            f.h.assertTrue(oldCoveredDecay||TownHallInteractionPolicy.matches(actual,c.state(),c.position().equals(f.plan.manager())),
                    "Unexpected target after garden recovery at "+c.position());
        }
        for(BlockPos p:garden(f.plan))f.h.assertTrue(f.level.getBlockState(p).equals(Blocks.STONE_BRICKS.defaultBlockState()),
                "Source garden grass incorrectly survived the structural TH2 target");
    }
    @GameTest(template="townhall_state_empty",batch="garden53strict01",timeoutTicks=12000)
    public static void garden_grass_requires_exact_leaf_before_and_after_intent(GameTestHelper h)throws Exception {
        ConstructionSafetyTests.with(h,"a","th2","town_hall",1,f->{
            BlockPos p=garden(f.plan).get(0);BlockState leaf=f.level.getBlockState(p.above());
            CompoundTag ledger=f.data.save(new CompoundTag());
            List<BlockState> invalid=List.of(Blocks.AIR.defaultBlockState(),leaf.setValue(LeavesBlock.PERSISTENT,false),
                    leaf.setValue(LeavesBlock.WATERLOGGED,true),Blocks.BIRCH_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true));
            var covers=BasicConstructionPurchase.th1GardenCover(f.plan.source());
            for(BlockState wrong:invalid) {
                f.level.setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),ConstructionSafetyTests.FLAGS);
                f.level.setBlock(p.above(),wrong,ConstructionSafetyTests.FLAGS);
                h.assertTrue(!BasicConstructionPurchase.gardenGrass(p,f.level.getBlockState(p),covers,f.level::getBlockState),
                        "Wrong leaf cover inherited garden permission: "+wrong);
                PaidRecoveryTests.refused(h,f::prepare,"source conflict");
                h.assertTrue(!Files.exists(f.original())&&ledger.equals(f.data.save(new CompoundTag())),"Wrong leaf created intent/payment");
            }
            f.level.setBlock(p,Blocks.DIRT.defaultBlockState(),ConstructionSafetyTests.FLAGS);
            f.level.setBlock(p.above(),leaf,ConstructionSafetyTests.FLAGS);f.prepare();byte[] original=Files.readAllBytes(f.original());
            for(BlockState wrong:invalid) {
                f.level.setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),ConstructionSafetyTests.FLAGS);
                f.level.setBlock(p.above(),wrong,ConstructionSafetyTests.FLAGS);
                PaidRecoveryTests.refused(h,f::complete,"Construction source conflict");
                h.assertTrue(ledger.equals(f.data.save(new CompoundTag()))&&!Files.exists(PaidConstructionRecovery.companion(f.original())),
                        "Unqualified growth began recovery/payment");
            }
            h.assertTrue(Arrays.equals(original,Files.readAllBytes(f.original())),"Cover refusal rewrote original intent");h.succeed();
        });
    }
    @GameTest(template="townhall_state_empty",batch="garden53strict02",timeoutTicks=12000)
    public static void foreign_garden_material_remains_refused(GameTestHelper h)throws Exception {
        ConstructionSafetyTests.with(h,"i","th2","town_hall",1,f->{
            BlockPos p=garden(f.plan).get(0);CompoundTag ledger=f.data.save(new CompoundTag());
            f.level.setBlock(p,Blocks.GOLD_BLOCK.defaultBlockState(),ConstructionSafetyTests.FLAGS);
            PaidRecoveryTests.refused(h,f::prepare,"source conflict");
            h.assertTrue(!Files.exists(f.original())&&ledger.equals(f.data.save(new CompoundTag())),"Foreign garden material created intent/payment");
            h.assertTrue(f.level.getBlockState(p).is(Blocks.GOLD_BLOCK),"Refusal erased foreign garden material");h.succeed();
        });
    }
    @GameTest(template="townhall_state_empty",batch="garden53strict03",timeoutTicks=12000)
    public static void sealed_grass_cannot_invent_authored_leaf(GameTestHelper h)throws Exception {
        ConstructionSafetyTests.with(h,"a","th2","town_hall",1,f->{
            f.prepare();CompoundTag ledger=f.data.save(new CompoundTag()),intent=ProducerJournal.read(f.original());
            CompoundTag snapshot=intent.getCompound("snapshot");ListTag palette=snapshot.getList("palette",Tag.TAG_COMPOUND);
            int grass=palette.size();palette.add(NbtUtils.writeBlockState(Blocks.GRASS_BLOCK.defaultBlockState()));
            int air=palette.size();palette.add(NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));
            int[] states=snapshot.getIntArray("states");BlockPos p=garden(f.plan).get(0);
            for(int i=0;i<states.length;i++) {
                BlockPos pos=f.plan.source().get(i).position();if(pos.equals(p))states[i]=grass;else if(pos.equals(p.above()))states[i]=air;
            }
            snapshot.putIntArray("states",states);ProducerJournal.write(f.original(),intent,true);byte[] altered=Files.readAllBytes(f.original());
            PaidRecoveryTests.refused(h,f::complete,"Captured source");
            h.assertTrue(ledger.equals(f.data.save(new CompoundTag()))&&!Files.exists(PaidConstructionRecovery.companion(f.original())),
                    "Invalid captured garden began recovery/payment");
            h.assertTrue(Arrays.equals(altered,Files.readAllBytes(f.original())),"Refusal rewrote altered source history");h.succeed();
        });
    }
    @GameTest(template="townhall_state_empty",batch="garden53strict04",timeoutTicks=12000)
    public static void qualification_binds_full_pin_orientation_and_all_cities(GameTestHelper h)throws Exception {
        for(char id='a';id<='p';id++) {
            var def=com.quin.catancraft.map.MapDefinitionManager.territory(String.valueOf(id));
            var anchor=SchematicAssetRegistry.territoryAnchor("th1",def);
            var source=BasicConstructionAssets.plan("th1",anchor);var covers=BasicConstructionPurchase.th1GardenCover(source);
            h.assertTrue(covers.size()==56,"Pinned garden coverage missing in "+id);
            var changed=new ArrayList<>(source);var first=source.get(0);
            changed.set(0,new SchematicPlacementService.PlanCell(first.localX(),first.localY(),first.localZ(),first.position(),Blocks.GOLD_BLOCK.defaultBlockState()));
            h.assertTrue(BasicConstructionPurchase.th1GardenCover(changed).isEmpty(),"Changed source inherited garden permission");
            h.assertTrue(BasicConstructionPurchase.th1GardenCover(BasicConstructionAssets.plan("th2",anchor)).isEmpty(),"TH2 inherited TH1 permission");
        }
        for(var type:IndustrialPlotService.INDUSTRIES)for(String plot:IndustrialPlotService.INDUSTRIAL_PLOTS) {
            var def=com.quin.catancraft.map.MapDefinitionManager.territory("d");
            var request=new BasicConstructionPurchase.Request("d",UUID.randomUUID(),UUID.randomUUID(),"factory",
                    SchematicAssetRegistry.buildingAsset(type),plot,0,1);
            h.assertTrue(BasicConstructionPurchase.th1GardenCover(BasicConstructionPurchase.blueprint(request,def).source()).isEmpty(),
                    "Factory inherited TH1 garden permission: "+type+"/"+plot);
        }
        h.succeed();
    }
}
