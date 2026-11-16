package com.quin.catancraft.world;

import com.google.gson.*;
import com.quin.catancraft.data.*;
import com.quin.catancraft.economy.*;
import com.quin.catancraft.map.*;
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
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Native natural-source cases against the pinned consumed civic contracts.
 * These isolated funded fixtures are separate from the all-city authored campaign. */
@GameTestHolder("catancraft_civic_natural53") @PrefixGameTestTemplate(false)
public final class CivicNatural53Tests {
    private static final int FLAGS=PaidConstructionRecovery.FLAGS;
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final BuildingType TYPE=BuildingType.COMMERCIAL_DISTRICT;
    private interface Checked {void run()throws Exception;}
    @GameTest(template="townhall_state_empty",batch="civic_natural53_01",timeoutTicks=6000)
    public static void recorded_native_dirt_north(GameTestHelper h)throws Exception{with(h,"a",f->lifecycle(f,true));}
    @GameTest(template="townhall_state_empty",batch="civic_natural53_02",timeoutTicks=6000)
    public static void recorded_native_dirt_south(GameTestHelper h)throws Exception{with(h,"i",f->lifecycle(f,true));}
    @GameTest(template="townhall_state_empty",batch="civic_natural53_03",timeoutTicks=6000)
    public static void grass_capture_decay_then_soil_first_interruption_north(GameTestHelper h)throws Exception{with(h,"a",f->lifecycle(f,false));}
    @GameTest(template="townhall_state_empty",batch="civic_natural53_04",timeoutTicks=6000)
    public static void grass_capture_decay_then_soil_first_interruption_south(GameTestHelper h)throws Exception{with(h,"i",f->lifecycle(f,false));}
    @GameTest(template="townhall_state_empty",batch="civic_natural53_05",timeoutTicks=6000)
    public static void exposed_foreign_cover_and_changed_history_north(GameTestHelper h)throws Exception{with(h,"a",CivicNatural53Tests::negatives);}
    @GameTest(template="townhall_state_empty",batch="civic_natural53_06",timeoutTicks=6000)
    public static void exposed_foreign_cover_and_changed_history_south(GameTestHelper h)throws Exception{with(h,"i",CivicNatural53Tests::negatives);}
    @GameTest(template="townhall_state_empty",batch="civic_natural53_07",timeoutTicks=6000)
    public static void legacy_original_contract_is_not_reinterpreted_north(GameTestHelper h)throws Exception{with(h,"a",CivicNatural53Tests::legacy);}
    @GameTest(template="townhall_state_empty",batch="civic_natural53_08",timeoutTicks=6000)
    public static void legacy_original_contract_is_not_reinterpreted_south(GameTestHelper h)throws Exception{with(h,"i",CivicNatural53Tests::legacy);}
    @GameTest(template="townhall_state_empty",batch="civic_natural53_09",timeoutTicks=6000)
    public static void native_cleanup_leaf_distance_preimage_and_recovery_north(GameTestHelper h)throws Exception{with(h,"a",CivicNatural53Tests::leafLifecycle);}
    @GameTest(template="townhall_state_empty",batch="civic_natural53_10",timeoutTicks=6000)
    public static void native_cleanup_leaf_distance_preimage_and_recovery_south(GameTestHelper h)throws Exception{with(h,"i",CivicNatural53Tests::leafLifecycle);}

    private interface Case {void run(F fixture)throws Exception;}
    private static void with(GameTestHelper h,String city,Case test)throws Exception {
        F f=new F(h,city);PaidRecoveryTests.ready(h,f.bounds,()->{f.stage();test.run(f);h.succeed();});
    }
    private static void lifecycle(F f,boolean dirtBeforeCapture)throws Exception {
        f.checkOrder();if(dirtBeforeCapture)f.decay();JsonObject j=f.capture();
        String captured=dirtBeforeCapture?"minecraft:dirt":"minecraft:grass_block[snowy=false]";
        f.h.assertTrue(j.getAsJsonArray("natural_source_preimages").get(0).getAsJsonObject().get("actual_before").getAsString().equals(captured),"Actual source preimage not recorded");
        f.write(j);byte[] original=Files.readAllBytes(f.file);CompoundTag unpaid=f.data.save(new CompoundTag());
        if(!dirtBeforeCapture)f.decay();
        PaidRecoveryTests.interrupt(f.h,f.environment(),"write",1);
        f.h.assertTrue(f.level.getBlockState(f.soil).equals(f.proof.soil().after())&&f.level.getBlockState(f.cover).equals(f.proof.cover().before()),"First recovery write exposed unconsumed natural soil");
        f.h.assertTrue(unpaid.equals(f.data.save(new CompoundTag()))&&Arrays.equals(original,Files.readAllBytes(f.file)),"First interruption charged or rewrote history");
        f.reload();PaidRecoveryTests.interrupt(f.h,f.environment(),"write",40);
        f.h.assertTrue(f.level.getBlockState(f.cover).isAir()&&f.level.getBlockState(f.soil).equals(f.proof.soil().after()),"Cleanup did not follow consumed soil");
        f.reload();PaidRecoveryTests.interrupt(f.h,f.environment(),"physical_saved",-1);
        f.h.assertTrue(unpaid.equals(f.data.save(new CompoundTag())),"Physical completion charged before settlement");
        BlockPos barrel=f.plan.finalCells().values().stream().filter(c->c.after().is(Blocks.BARREL)).findFirst().orElseThrow().pos();
        f.level.setBlock(barrel,f.level.getBlockState(barrel).setValue(BarrelBlock.OPEN,true),FLAGS);
        ((BarrelBlockEntity)f.level.getBlockEntity(barrel)).setItem(0,new ItemStack(Items.DIAMOND,17));
        CompoundTag inventory=f.level.getBlockEntity(barrel).saveWithFullMetadata().copy();
        f.h.assertTrue(R5CivicAutomaticInstaller.verifyInstalled(f.level,f.plan)==2793,"Incomplete full civic target verification");
        f.reload();PaidRecoveryTests.interrupt(f.h,f.environment(),"ledger_saved",-1);f.reload();
        f.nation().addTreasury(125);f.save();long paid=f.nation().treasury();
        var result=PaidConstructionRecovery.resume(f.environment());
        f.h.assertTrue(result.writes()==0&&f.nation().treasury()==paid&&paid==1_000_000-EconomyCatalog.buildingCost(TYPE).money()+125,"Civic recovery repeated payment or blocks");
        for(ResourceType r:ResourceType.values())f.h.assertTrue(f.nation().resource(r)==100_000-EconomyCatalog.buildingCost(TYPE).resources().getOrDefault(r,0L),"Civic resource debit changed");
        f.h.assertTrue(f.data.territory(f.def.id()).buildings().stream().filter(b->b.type()==TYPE).count()==1,"Civic duplicate building");
        f.h.assertTrue(inventory.equals(f.level.getBlockEntity(barrel).saveWithFullMetadata())&&f.level.getBlockState(barrel).getValue(BarrelBlock.OPEN),"Recovery or verification changed opened barrel/inventory");
        f.h.assertTrue(R5CivicAutomaticInstaller.verifyInstalled(f.level,f.plan)==2793&&Arrays.equals(original,Files.readAllBytes(f.file)),"Civic final source history/target changed");
        byte[] companion=Files.readAllBytes(PaidConstructionRecovery.companion(f.file));f.reload();
        f.h.assertTrue(PaidConstructionRecovery.resume(f.environment()).alreadyComplete(),"Duplicate recovery not idempotent");
        f.h.assertTrue(Arrays.equals(companion,Files.readAllBytes(PaidConstructionRecovery.companion(f.file))),"Completed companion rewritten");
        UUID id=UUID.fromString(j.get("transaction_id").getAsString());f.h.assertTrue(PaidConstructionRecovery.paid(f.data,id,true,false,true),"Saved purchase receipt lost");
    }
    private static void negatives(F f)throws Exception {
        f.decay();CompoundTag ledger=f.data.save(new CompoundTag());
        for(BlockState wrong:List.of(Blocks.AIR.defaultBlockState(),Blocks.DIAMOND_BLOCK.defaultBlockState(),Blocks.BARREL.defaultBlockState())){
            f.level.setBlock(f.cover,wrong,FLAGS);var before=f.snapshot();refuse(f.h,f::capture);
            f.h.assertTrue(before.equals(f.snapshot())&&!Files.exists(f.file)&&ledger.equals(f.data.save(new CompoundTag())),"Foreign/exposed cover was changed or charged");
            f.level.setBlock(f.cover,f.proof.cover().before(),FLAGS);
        }
        BlockPos exposed=f.plan.finalCells().values().stream().filter(c->c.before().equals(Blocks.GRASS_BLOCK.defaultBlockState())&&!c.pos().equals(f.soil)).findFirst().orElseThrow().pos();
        f.level.setBlock(exposed,Blocks.DIRT.defaultBlockState(),FLAGS);refuse(f.h,f::capture);f.level.setBlock(exposed,Blocks.GRASS_BLOCK.defaultBlockState(),FLAGS);
        // A GRASS beforeimage cannot authorize dirt after the log has disappeared.
        f.level.setBlock(f.soil,Blocks.GRASS_BLOCK.defaultBlockState(),FLAGS);JsonObject valid=f.capture();f.write(valid);f.decay();
        for(BlockState wrong:List.of(Blocks.AIR.defaultBlockState(),Blocks.DIAMOND_BLOCK.defaultBlockState(),Blocks.BARREL.defaultBlockState())){
            f.level.setBlock(f.cover,wrong,FLAGS);f.refuseResume();f.level.setBlock(f.cover,f.proof.cover().before(),FLAGS);
        }
        List<JsonObject> bad=new ArrayList<>();JsonObject changed=valid.deepCopy();changed.getAsJsonArray("natural_source_preimages").get(0).getAsJsonObject().addProperty("source_cover","minecraft:diamond_block");bad.add(changed);
        changed=valid.deepCopy();changed.getAsJsonArray("natural_source_preimages").get(0).getAsJsonObject().addProperty("actual_before","minecraft:coarse_dirt");bad.add(changed);
        changed=valid.deepCopy();changed.add("natural_source_preimages",new JsonArray());bad.add(changed);
        changed=valid.deepCopy();changed.getAsJsonArray("natural_source_preimages").get(0).getAsJsonObject().addProperty("future_cover",true);bad.add(changed);
        changed=valid.deepCopy();changed.add("ordered_writes",ordered(f.plan,1));bad.add(changed);
        changed=valid.deepCopy();changed.getAsJsonArray("originals").get(0).getAsJsonObject().addProperty("before","minecraft:dirt");bad.add(changed);
        for(JsonObject corrupt:bad){f.write(corrupt);f.refuseResume();}
        f.write(valid);var bound=f.environment();byte[] intact=Files.readAllBytes(f.file);JsonObject changedId=valid.deepCopy();changedId.addProperty("transaction_id",UUID.randomUUID().toString());f.write(changedId);
        refuse(f.h,()->PaidConstructionRecovery.resume(bound));f.h.assertTrue(!Files.exists(PaidConstructionRecovery.companion(f.file))&&ledger.equals(f.data.save(new CompoundTag())),"Changed original journal was adopted");
        Files.write(f.file,intact);PaidConstructionRecovery.resume(f.environment());f.h.assertTrue(R5CivicAutomaticInstaller.verifyInstalled(f.level,f.plan)==2793,"Original verified source could not resume");
        var clean=f.snapshot();f.level.setBlock(f.soil,Blocks.DIRT.defaultBlockState(),FLAGS);refuse(f.h,()->R5CivicAutomaticInstaller.verifyInstalled(f.level,f.plan));
        f.h.assertTrue(f.level.getBlockState(f.soil).is(Blocks.DIRT),"Read-only final verification repainted foreign soil");f.level.setBlock(f.soil,clean.get(f.soil),FLAGS);
    }
    private static void legacy(F f)throws Exception {
        JsonObject j=f.capture();j.addProperty("schema",1);j.remove("natural_source_preimages");j.remove("natural_leaf_preimages");j.add("ordered_writes",ordered(f.plan,1));f.write(j);byte[] original=Files.readAllBytes(f.file);
        f.decay();f.refuseResume();f.h.assertTrue(Arrays.equals(original,Files.readAllBytes(f.file)),"Legacy strict refusal rewrote original");
        f.level.setBlock(f.soil,Blocks.GRASS_BLOCK.defaultBlockState(),FLAGS);PaidRecoveryTests.interrupt(f.h,f.environment(),"write",1);
        f.h.assertTrue(f.level.getBlockState(f.soil).is(Blocks.GRASS_BLOCK),"Legacy recovery was silently reordered");
        f.reload();PaidConstructionRecovery.resume(f.environment());f.h.assertTrue(Arrays.equals(original,Files.readAllBytes(f.file))&&R5CivicAutomaticInstaller.verifyInstalled(f.level,f.plan)==2793,"Legacy exact contract changed");
    }
    private static void leafLifecycle(F f)throws Exception {
        Map<BlockPos,R5CivicAutomaticInstaller.Cell> leaves=R5CivicAutomaticInstaller.naturalLeafSources(f.plan);f.h.assertTrue(leaves.size()==84,"Pinned cleanup leaf count changed");
        tickLeaves(f,leaves.keySet());Map<Integer,Integer> distances=new TreeMap<>();int oldExactFailures=0;
        for(var c:leaves.values()){
            BlockState live=f.level.getBlockState(c.pos());distances.merge(live.getValue(LeavesBlock.DISTANCE),1,Integer::sum);
            f.h.assertTrue(StreetscapeR5.matches(live,c.before()),"Native leaf tick changed a non-distance property");if(!live.equals(c.before()))oldExactFailures++;
        }
        f.h.assertTrue(distances.equals(Map.of(1,16,2,28,3,28,4,12))&&oldExactFailures==68,"Native cleanup distances differ from pinned log graph: "+distances);
        com.mojang.logging.LogUtils.getLogger().info("CIVIC_NATURAL53 LEAF PROOF city={} distances={} old_exact_mismatches={}/84",f.def.id(),distances,oldExactFailures);
        BlockPos leaf=leaves.keySet().iterator().next();BlockState correct=f.level.getBlockState(leaf);
        for(BlockState wrong:List.of(Blocks.BIRCH_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true),correct.setValue(LeavesBlock.PERSISTENT,false),correct.setValue(LeavesBlock.WATERLOGGED,true))){
            f.level.setBlock(leaf,wrong,FLAGS);var before=f.snapshot();refuse(f.h,f::capture);f.h.assertTrue(before.equals(f.snapshot())&&!Files.exists(f.file),"Foreign leaf type/persistence/fluid was normalized");f.level.setBlock(leaf,correct,FLAGS);
        }
        JsonObject valid=f.capture();JsonArray preimages=valid.getAsJsonArray("natural_leaf_preimages");f.h.assertTrue(preimages.size()==84,"Leaf actual preimages incomplete");
        for(var value:preimages){JsonObject row=value.getAsJsonObject();JsonArray xyz=row.getAsJsonArray("xyz");BlockPos p=new BlockPos(xyz.get(0).getAsInt(),xyz.get(1).getAsInt(),xyz.get(2).getAsInt());f.h.assertTrue(row.get("actual_before").getAsString().equals(R5CivicAutomaticInstaller.canonical(f.level.getBlockState(p))),"Native leaf beforeimage normalized");}
        JsonObject legacy=valid.deepCopy();legacy.addProperty("schema",1);legacy.remove("natural_source_preimages");legacy.remove("natural_leaf_preimages");legacy.add("ordered_writes",ordered(f.plan,1));f.write(legacy);f.refuseResume();
        for(String key:List.of("actual_before","source_before")){
            JsonObject bad=valid.deepCopy();bad.getAsJsonArray("natural_leaf_preimages").get(0).getAsJsonObject().addProperty(key,"minecraft:oak_leaves[distance=2,persistent=false,waterlogged=false]");f.write(bad);f.refuseResume();
        }
        JsonObject missing=valid.deepCopy();missing.getAsJsonArray("natural_leaf_preimages").remove(0);f.write(missing);f.refuseResume();
        f.write(valid);byte[] original=Files.readAllBytes(f.file);PaidRecoveryTests.interrupt(f.h,f.environment(),"write",58);
        tickLeaves(f,leaves.keySet());f.reload();PaidConstructionRecovery.resume(f.environment());
        f.h.assertTrue(R5CivicAutomaticInstaller.verifyInstalled(f.level,f.plan)==2793&&Arrays.equals(original,Files.readAllBytes(f.file)),"Partial native leaf recovery changed preimages or final target");
        f.h.assertTrue(f.nation().treasury()==1_000_000-EconomyCatalog.buildingCost(TYPE).money(),"Native leaves changed civic price");
        f.reload();f.h.assertTrue(PaidConstructionRecovery.resume(f.environment()).alreadyComplete(),"Leaf recovery repeated payment");
    }
    private static void tickLeaves(F f,Collection<BlockPos> leaves){
        RandomSource random=RandomSource.create(0x5300);for(int sweep=0;sweep<7;sweep++)for(BlockPos p:leaves)if(f.level.getBlockState(p).is(Blocks.OAK_LEAVES))Blocks.OAK_LEAVES.tick(f.level.getBlockState(p),f.level,p,random);
    }
    private static void refuse(GameTestHelper h,Checked work)throws Exception {
        boolean refused=false;try{work.run();}catch(IOException|IllegalStateException expected){refused=true;}h.assertTrue(refused,"Expected precise source/history refusal");
    }
    private static JsonArray ordered(R5CivicAutomaticInstaller.Plan plan,int schema)throws Exception {
        JsonArray rows=new JsonArray();for(var w:R5CivicAutomaticInstaller.executionWrites(plan,schema)){
            JsonObject row=new JsonObject();row.add("xyz",R5CivicAutomaticInstaller.xyz(w.pos()));row.addProperty("stage",w.stage());row.addProperty("before",R5CivicAutomaticInstaller.canonical(w.before()));row.addProperty("after",R5CivicAutomaticInstaller.canonical(w.after()));rows.add(row);
        }return rows;
    }
    private static R5CivicAutomaticInstaller.Plan plan(TerritoryDefinition def,BuildingType type)throws Exception {
        byte[] packed;try(var in=CivicNatural53Tests.class.getResourceAsStream("/data/catancraft_paid_recovery/fixtures/r5_contracts.b64")){if(in==null)throw new IOException("Missing pinned civic fixture");packed=Base64.getMimeDecoder().decode(in.readAllBytes());}
        if(!TownHallRevisions.sha(packed).equals("84d7095b0b3a50b6c02f8175309436c78ed9077b02cfac854f10e5e7f6f70ec3"))throw new IOException("Changed civic fixture");
        JsonObject fixture;try(var in=new GZIPInputStream(new ByteArrayInputStream(packed))){fixture=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();}
        JsonArray palette=fixture.getAsJsonArray("palette");JsonObject docs=fixture.getAsJsonObject("documents");
        return R5CivicAutomaticInstaller.contract(def,type,(civic,path)->{
            JsonObject source=docs.getAsJsonObject(path),doc=new JsonObject();for(String key:List.of("territory_id","module_id","local_group_id","status"))if(source.has(key))doc.add(key,source.get(key));
            JsonArray rows=new JsonArray();for(var entry:source.getAsJsonArray("rows")){
                JsonArray a=entry.getAsJsonArray();JsonObject row=new JsonObject();JsonArray xyz=new JsonArray();for(int n=0;n<3;n++)xyz.add(a.get(n));row.add("world_xyz",xyz);
                row.add("expected_before_blockstate",palette.get(a.get(3).getAsInt()));row.add("after_blockstate",palette.get(a.get(4).getAsInt()));rows.add(row);
            }doc.add(source.get("rowKey").getAsString(),rows);return doc;
        });
    }
    private static final class F {
        final GameTestHelper h;final ServerLevel level;final TerritoryDefinition def;final R5CivicAutomaticInstaller.Plan plan;
        final R5CivicAutomaticInstaller.NaturalSource proof;final BlockPos soil,cover;final Path dir,file,ledgerFile;final AABB bounds;final UUID owner;
        CatanSavedData data=new CatanSavedData();
        F(GameTestHelper h,String city)throws Exception {
            this.h=h;level=h.getLevel();if(!(level.getServer() instanceof GameTestServer))throw new IOException("Controlled native fixture only");
            def=MapDefinitionManager.territory(city);plan=plan(def,TYPE);var natural=R5CivicAutomaticInstaller.naturalSources(plan);h.assertTrue(natural.size()==1,"Expected one pinned commercial source");proof=natural.values().iterator().next();soil=proof.soil().pos();cover=proof.cover().pos();
            h.assertTrue(R5CivicAutomaticInstaller.naturalSources(plan(def,BuildingType.LOGISTICS_CENTER)).isEmpty(),"Logistics gained natural-source permission");
            AABB b=null;for(var c:plan.finalCells().values()){var next=new AABB(c.pos()).inflate(2);b=b==null?next:b.minmax(next);}bounds=b;
            dir=Files.createTempDirectory(level.getServer().getWorldPath(LevelResource.ROOT),"civic-natural53-").toRealPath();Files.createDirectories(dir.resolve("data"));file=dir.resolve("data/catancraft_r5_auto_"+city+"_commercial_district_v1.json");ledgerFile=dir.resolve("ledger.nbt");
            var n=data.createNation("Civic natural source fixture",UUID.randomUUID());owner=n.id();n.setTreasury(1_000_000);for(ResourceType r:ResourceType.values())n.addResource(r,100_000);
            var t=data.createTerritory(city,"fixture",ResourceType.WOOD);t.setOwnerNationId(owner);t.setCityLevel(4);save();
        }
        NationData nation(){return data.nation(owner);}
        void save()throws IOException{NbtIo.writeCompressed(data.save(new CompoundTag()),ledgerFile.toFile());}
        void reload()throws IOException{data=CatanSavedData.load(NbtIo.readCompressed(ledgerFile.toFile()));}
        void stage(){
            for(var entity:new ArrayList<>(level.getEntities((net.minecraft.world.entity.Entity)null,bounds)))if(entity instanceof net.minecraft.world.entity.Mob)entity.discard();
            Set<BlockPos> inside=new HashSet<>();for(var c:plan.finalCells().values())inside.add(c.pos());
            for(BlockPos p:inside)for(var d:net.minecraft.core.Direction.values())if(!inside.contains(p.relative(d)))level.setBlock(p.relative(d),Blocks.STONE.defaultBlockState(),FLAGS);
            for(var c:plan.finalCells().values()){var be=level.getBlockEntity(c.pos());if(be instanceof net.minecraft.world.Container inventory)inventory.clearContent();level.setBlock(c.pos(),c.before(),FLAGS);}
        }
        void decay(){
            h.assertTrue(level.getBlockState(soil).equals(proof.soil().before())&&level.getBlockState(cover).equals(proof.cover().before()),"Native decay lacks exact source grass/log");
            Blocks.GRASS_BLOCK.randomTick(level.getBlockState(soil),level,soil,RandomSource.create(53));h.assertTrue(level.getBlockState(soil).equals(Blocks.DIRT.defaultBlockState()),"Native grass randomTick did not decay under log");
        }
        JsonObject capture()throws Exception{return R5CivicAutomaticInstaller.journal(dir,level,def,TYPE,nation(),EconomyCatalog.buildingCost(TYPE),plan,R5CivicAutomaticInstaller.captureSource(level,plan));}
        void write(JsonObject j)throws Exception{R5CivicAutomaticInstaller.seal(j);Files.writeString(file,JSON.toJson(j));}
        Map<BlockPos,BlockState> snapshot(){Map<BlockPos,BlockState> out=new HashMap<>();for(var c:plan.finalCells().values())out.put(c.pos(),level.getBlockState(c.pos()));return out;}
        void checkOrder()throws Exception {
            List<R5CivicAutomaticInstaller.Write> expected=new ArrayList<>(plan.writes());var s=expected.stream().filter(w->w.pos().equals(soil)).findFirst().orElseThrow();expected.remove(s);expected.add(0,s);
            h.assertTrue(plan.writes().size()==2835&&plan.finalCells().size()==2793&&R5CivicAutomaticInstaller.executionWrites(plan,1).equals(plan.writes())&&R5CivicAutomaticInstaller.executionWrites(plan,2).equals(expected),"New execution reordered more than its one dependency");
        }
        void refuseResume()throws Exception {
            byte[] original=Files.readAllBytes(file);var world=snapshot();var ledger=data.save(new CompoundTag());refuse(h,()->PaidConstructionRecovery.resume(environment()));
            h.assertTrue(Arrays.equals(original,Files.readAllBytes(file))&&world.equals(snapshot())&&ledger.equals(data.save(new CompoundTag()))&&!Files.exists(PaidConstructionRecovery.companion(file)),"Refusal mutated blocks, funds, or retained history");
        }
        PaidConstructionRecovery.Environment environment()throws Exception {
            var e=CivicPurchaseRecovery.fixture(level.getServer(),data,level,def,dir,file,()->{},plan);
            return new PaidConstructionRecovery.Environment(){
                public ServerLevel level(){return e.level();}public Path original(){return e.original();}public byte[] originalBytes(){return e.originalBytes();}public CompoundTag identity(){return e.identity();}public List<PaidConstructionRecovery.Cell> cells()throws IOException{return e.cells();}
                public void authorize()throws IOException{e.authorize();}public boolean paid()throws IOException{return e.paid();}public void checkPayment()throws IOException{e.checkPayment();}public void settle()throws IOException{e.settle();}
                public CompoundTag ledger(){return e.ledger();}public void save()throws IOException{F.this.save();}
            };
        }
    }
}
