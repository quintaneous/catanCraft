package com.quin.catancraft.world;

import com.google.gson.*;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.map.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Source-covered soil during zero-write L1 adoption. These staged native
 * fixtures do not replace the independent actual-authored-world campaign. */
@GameTestHolder("catancraft_producer_adoption53")
@PrefixGameTestTemplate(false)
public final class ProducerAdoption53Tests {
    private static final int FLAGS=2|16|32;
    private ProducerAdoption53Tests() {}
    @GameTest(template="townhall_state_empty",batch="adoption53_north",timeoutTicks=1000)
    public static void north_native_decay_adopts_without_writes_or_charge(GameTestHelper h)throws Exception {positive(h,"a");}
    @GameTest(template="townhall_state_empty",batch="adoption53_south",timeoutTicks=1000)
    public static void south_native_decay_adopts_without_writes_or_charge(GameTestHelper h)throws Exception {positive(h,"m");}
    private static void positive(GameTestHelper h,String city)throws Exception {
        try(Fixture f=new Fixture(h,city)) {
            var covered=f.covered("quarry");BlockPos pos=covered.position();
            h.assertTrue(f.level().getBlockState(pos).equals(Blocks.GRASS_BLOCK.defaultBlockState()),"Native decay must begin at exact grass");
            Blocks.GRASS_BLOCK.randomTick(f.level().getBlockState(pos),f.level(),pos,RandomSource.create(53));
            h.assertTrue(f.level().getBlockState(pos).equals(Blocks.DIRT.defaultBlockState()),"Native covered grass did not decay");
            var blocks=f.blocks();var inventories=f.inventories();CompoundTag before=f.data.save(new CompoundTag());
            var result=ProducerService.adoptPrebuilt(f.server(),f.data,f.definition);
            h.assertTrue(result.success()&&result.changedBlocks()==0,"Native aged prebuilt adoption failed: "+result.message());
            var expected=CatanSavedData.load(before);
            for(String family:ProducerRevisions.applicable(f.definition))expected.setPlacedMapAsset(f.key(family),ProducerRevisions.token(family,1));
            h.assertTrue(expected.save(new CompoundTag()).equals(f.data.save(new CompoundTag())),"Adoption changed funds, ownership or unrelated records");
            h.assertTrue(blocks.equals(f.blocks())&&inventories.equals(f.inventories()),"Adoption repainted source or changed inventory NBT");
            CompoundTag adopted=f.data.save(new CompoundTag());
            var again=ProducerService.adoptPrebuilt(f.server(),f.data,f.definition);
            h.assertTrue(again.success()&&again.changedBlocks()==0&&adopted.equals(f.data.save(new CompoundTag()))
                    &&blocks.equals(f.blocks())&&inventories.equals(f.inventories()),"Repeated adoption changed accepted source or records");
            h.succeed();
        }
    }
    @GameTest(template="townhall_state_empty",batch="adoption53_exposed",timeoutTicks=1000)
    public static void exposed_dirt_refuses_all_plot_adoption(GameTestHelper h)throws Exception {
        try(Fixture f=new Fixture(h,"a")) {
            var cell=f.plans.get("lumberyard").stream().filter(c->c.state().equals(Blocks.GRASS_BLOCK.defaultBlockState())
                    &&f.level().getBlockState(c.position().above()).isAir()).findFirst().orElseThrow();
            f.level().setBlock(cell.position(),Blocks.DIRT.defaultBlockState(),FLAGS);f.refuses("Unrecognized prebuilt producer");h.succeed();
        }
    }
    @GameTest(template="townhall_state_empty",batch="adoption53_cover",timeoutTicks=1000)
    public static void changed_cover_cannot_authorize_dirt(GameTestHelper h)throws Exception {
        try(Fixture f=new Fixture(h,"m")) {
            BlockPos p=f.covered("lumberyard").position();f.level().setBlock(p,Blocks.DIRT.defaultBlockState(),FLAGS);
            f.level().setBlock(p.above(),Blocks.GOLD_BLOCK.defaultBlockState(),FLAGS);f.refuses("Unrecognized prebuilt producer");h.succeed();
        }
    }
    @GameTest(template="townhall_state_empty",batch="adoption53_ground",timeoutTicks=1000)
    public static void other_ground_material_remains_a_conflict(GameTestHelper h)throws Exception {
        try(Fixture f=new Fixture(h,"a")) {
            f.level().setBlock(f.covered("quarry").position(),Blocks.COARSE_DIRT.defaultBlockState(),FLAGS);
            f.refuses("Unrecognized prebuilt producer");h.succeed();
        }
    }
    @GameTest(template="townhall_state_empty",batch="adoption53_inventory",timeoutTicks=1000)
    public static void foreign_inventory_refuses_and_keeps_its_items(GameTestHelper h)throws Exception {
        try(Fixture f=new Fixture(h,"m")) {
            var cell=f.plans.get("lumberyard").stream().filter(c->c.state().is(Blocks.BARREL)).findFirst().orElseThrow();
            ((Container)f.level().getBlockEntity(cell.position())).clearContent();
            f.level().setBlock(cell.position(),Blocks.CHEST.defaultBlockState(),FLAGS);
            ((Container)f.level().getBlockEntity(cell.position())).setItem(0,new ItemStack(Items.DIAMOND,11));
            f.refuses("Unrecognized prebuilt producer");h.succeed();
        }
    }
    @GameTest(template="townhall_state_empty",batch="adoption53_map",timeoutTicks=1000)
    public static void changed_map_cannot_adopt_original_producer(GameTestHelper h)throws Exception {
        try(Fixture f=new Fixture(h,"a")) {
            var json=new Gson().toJsonTree(f.definition).getAsJsonObject();json.getAsJsonObject("cityCenter").addProperty("x",-749);
            var foreign=new Gson().fromJson(json,TerritoryDefinition.class);f.refuses(foreign,"Map differs from reviewed");h.succeed();
        }
    }
    /** Real native decay beneath the one pinned quarry barrel, including its
     * ordinary open state, without rewriting the barrel or its contents. */
    @GameTest(template="townhall_state_empty",batch="adoption53_barrel_probe",timeoutTicks=1000)
    public static void quarry_barrel_native_decay_adopts_without_inventory_or_open_changes(GameTestHelper h)throws Exception {
        for(String city:List.of("a","m"))try(Fixture f=new Fixture(h,city)) {
            var candidate=f.barrelGrass();BlockPos p=candidate.position();
            f.level().setBlock(p.above(),f.level().getBlockState(p.above()).setValue(net.minecraft.world.level.block.BarrelBlock.OPEN,city.equals("m")),FLAGS);
            CompoundTag inventory=f.level().getBlockEntity(p.above()).saveWithFullMetadata().copy();
            h.assertTrue(f.level().getBlockEntity(p.above()) instanceof Container,"Native source barrel missing its inventory");
            Blocks.GRASS_BLOCK.randomTick(f.level().getBlockState(p),f.level(),p,RandomSource.create(53));
            h.assertTrue(f.level().getBlockState(p).equals(Blocks.DIRT.defaultBlockState()),"Expected native barrel-cover decay was not observed");
            h.assertTrue(inventory.equals(f.level().getBlockEntity(p.above()).saveWithFullMetadata()),"Native grass tick changed barrel inventory");
            h.assertTrue(!ProducerService.sourceCoveredGrass(f.plans.get("quarry")).containsKey(p),"Producer barrel authority leaked into generic cover policy");
            var blocks=f.blocks();var inventories=f.inventories();CompoundTag before=f.data.save(new CompoundTag());
            var result=ProducerService.adoptPrebuilt(f.server(),f.data,f.definition);h.assertTrue(result.success()&&result.changedBlocks()==0,"Pinned barrel soil adoption failed: "+result.message());
            var expected=CatanSavedData.load(before);for(String family:ProducerRevisions.applicable(f.definition))expected.setPlacedMapAsset(f.key(family),ProducerRevisions.token(family,1));
            h.assertTrue(expected.save(new CompoundTag()).equals(f.data.save(new CompoundTag()))&&blocks.equals(f.blocks())&&inventories.equals(f.inventories()),"Barrel adoption changed charge, unrelated state, inventory or OPEN");
            CompoundTag adopted=f.data.save(new CompoundTag());h.assertTrue(ProducerService.adoptPrebuilt(f.server(),f.data,f.definition).success()
                    &&adopted.equals(f.data.save(new CompoundTag()))&&blocks.equals(f.blocks())&&inventories.equals(f.inventories()),"Repeated barrel adoption changed accepted records or world");
            com.quin.catancraft.CatanCraft.LOGGER.info("PRODUCER_ADOPTION53_BARREL_PASS city={} nativeDecay=DIRT inventoryPreserved=true openPreserved=true writes=0 charge=0 idempotent=true",city);
        }h.succeed();
    }
    @GameTest(template="townhall_state_empty",batch="adoption53_barrel_facing",timeoutTicks=1000)
    public static void changed_barrel_facing_cannot_authorize_soil(GameTestHelper h)throws Exception {
        try(Fixture f=new Fixture(h,"m")){
            var cell=f.barrelGrass();BlockPos p=cell.position();var cover=f.level().getBlockState(p.above());
            Blocks.GRASS_BLOCK.randomTick(f.level().getBlockState(p),f.level(),p,RandomSource.create(53));
            f.level().setBlock(p.above(),cover.setValue(net.minecraft.world.level.block.BarrelBlock.FACING,cover.getValue(net.minecraft.world.level.block.BarrelBlock.FACING).getOpposite()),FLAGS);
            h.assertTrue(!ProducerService.matchesNaturalCover(f.level(),p,cover),"Wrong barrel facing received cover authority");
            f.refuses("Unrecognized prebuilt producer");h.succeed();
        }
    }
    @GameTest(template="townhall_state_empty",batch="adoption53_foreign_cover",timeoutTicks=1000)
    public static void foreign_inventory_is_not_an_approved_soil_cover(GameTestHelper h)throws Exception {
        try(Fixture f=new Fixture(h,"a")){
            var cell=f.barrelGrass();BlockPos p=cell.position();var cover=f.level().getBlockState(p.above());
            Blocks.GRASS_BLOCK.randomTick(f.level().getBlockState(p),f.level(),p,RandomSource.create(53));
            ((Container)f.level().getBlockEntity(p.above())).clearContent();f.level().setBlock(p.above(),Blocks.FURNACE.defaultBlockState(),FLAGS);
            ((Container)f.level().getBlockEntity(p.above())).setItem(0,new ItemStack(Items.DIAMOND,13));
            h.assertTrue(!ProducerService.naturalCover(Blocks.FURNACE.defaultBlockState(),p.above())&&!ProducerService.matchesNaturalCover(f.level(),p,cover),"Generic inventory received barrel cover authority");
            f.refuses("Unrecognized prebuilt producer");h.succeed();
        }
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;final TerritoryDefinition definition;final CatanSavedData data=new CatanSavedData();
        final Map<String,List<SchematicPlacementService.PlanCell>> plans=new LinkedHashMap<>();final Path marker;
        Fixture(GameTestHelper h,String city)throws Exception {
            this.h=h;h.assertTrue(server() instanceof GameTestServer,"Isolated native fixture only");
            definition=MapDefinitionManager.territory(city);ProducerService.validateMap(definition);data.syncMapDefinitions();
            data.createNation("Adoption53-"+city,UUID.randomUUID());
            Path root=server().getWorldPath(LevelResource.ROOT).toRealPath();marker=root.resolve(FreshDemoAuthorization.MARKER);
            h.assertTrue(!Files.exists(marker),"Fixture refuses previous fresh-world marker");
            JsonObject identity=new JsonObject();identity.addProperty("schema",1);identity.addProperty("purpose",FreshDemoAuthorization.PURPOSE);
            identity.addProperty("worldId",UUID.randomUUID().toString());identity.addProperty("levelName",server().getWorldData().getLevelName());
            identity.addProperty("seed",level().getSeed());identity.addProperty("dimension","minecraft:overworld");
            identity.addProperty("mapSha256",TownHallRevisions.sha(Files.readAllBytes(MapDefinitionManager.path())));
            identity.addProperty("authoredWorldSha256","c".repeat(64));identity.addProperty("bootstrapRevision","producer-adoption53-native-fixture");
            identity.addProperty("installationBoundPath",root.toString());Files.writeString(marker,identity.toString(),StandardOpenOption.CREATE_NEW);
            try {
                for(String family:ProducerRevisions.applicable(definition)) {
                    var plan=ProducerRevisions.plan(family,1,SchematicAssetRegistry.territoryAnchor(family,definition));plans.put(family,plan);
                    for(var cell:plan)level().setBlock(cell.position(),cell.state(),FLAGS);
                    int count=0;for(var cell:plan)if(cell.state().is(Blocks.BARREL)) {
                        var barrel=level().getBlockEntity(cell.position());h.assertTrue(barrel instanceof Container,"Staged source barrel missing");
                        ((Container)barrel).clearContent();((Container)barrel).setItem(0,new ItemStack(Items.EMERALD,++count));
                    }
                }
            }catch(Exception error){Files.delete(marker);throw error;}
        }
        net.minecraft.server.MinecraftServer server(){return h.getLevel().getServer();}
        net.minecraft.server.level.ServerLevel level(){return h.getLevel();}
        String key(String family){return "territory:"+definition.id()+":"+SchematicAssetRegistry.asset(family).plot();}
        SchematicPlacementService.PlanCell covered(String family)throws IOException {
            var cover=ProducerService.sourceCoveredGrass(plans.get(family));
            return plans.get(family).stream().filter(c->cover.containsKey(c.position())&&cover.get(c.position()).is(Blocks.SPRUCE_LOG))
                    .findFirst().orElseThrow(()->new IOException("No immutable covered grass source"));
        }
        SchematicPlacementService.PlanCell barrelGrass(){
            var candidates=plans.get("quarry").stream().filter(c->c.state().equals(Blocks.GRASS_BLOCK.defaultBlockState())
                    &&level().getBlockState(c.position().above()).is(Blocks.BARREL)).toList();
            h.assertTrue(candidates.size()==1,"Pinned quarry barrel grass source changed");return candidates.get(0);
        }
        Map<BlockPos,BlockState> blocks(){var out=new HashMap<BlockPos,BlockState>();for(var plan:plans.values())for(var c:plan)out.put(c.position(),level().getBlockState(c.position()));return out;}
        Map<BlockPos,CompoundTag> inventories(){var out=new HashMap<BlockPos,CompoundTag>();for(var plan:plans.values())for(var c:plan){var be=level().getBlockEntity(c.position());if(be!=null)out.put(c.position(),be.saveWithFullMetadata());}return out;}
        void refuses(String reason)throws IOException {refuses(definition,reason);}
        void refuses(TerritoryDefinition selected,String reason)throws IOException {
            var blocks=blocks();var inventories=inventories();var ledger=data.save(new CompoundTag());
            var result=ProducerService.adoptPrebuilt(server(),data,selected);
            h.assertTrue(!result.success()&&result.changedBlocks()==0&&result.message().contains(reason),"Unexpected adoption refusal: "+result.message());
            h.assertTrue(ledger.equals(data.save(new CompoundTag()))&&blocks.equals(blocks())&&inventories.equals(inventories()),"Refusal changed records, blocks or inventory");
            for(String family:plans.keySet())h.assertTrue(data.placedMapAsset(key(family))==null,"Failed grouped adoption partially published a plot");
        }
        public void close()throws IOException {Files.delete(marker);}
    }
}
