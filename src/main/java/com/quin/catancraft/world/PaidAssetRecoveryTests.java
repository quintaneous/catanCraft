package com.quin.catancraft.world;

import com.google.gson.*;
import com.quin.catancraft.data.*;
import com.quin.catancraft.economy.*;
import com.quin.catancraft.map.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Native recovery tests against exact consumed fields of the delivered R5
 * contracts and the bundled full TH revisions, not hand-drawn replacement assets. */
@GameTestHolder("catancraft_paid_recovery") @PrefixGameTestTemplate(false)
public final class PaidAssetRecoveryTests {
    private static final int FLAGS=PaidConstructionRecovery.FLAGS;
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static JsonObject fixture()throws Exception{
        byte[] encoded;
        try(var in=PaidAssetRecoveryTests.class.getResourceAsStream("/data/catancraft_paid_recovery/fixtures/r5_contracts.b64")){if(in==null)throw new IOException("Missing delivered contract fixture");encoded=in.readAllBytes();}
        byte[] compressed=Base64.getMimeDecoder().decode(encoded);
        if(!TownHallRevisions.sha(compressed).equals("84d7095b0b3a50b6c02f8175309436c78ed9077b02cfac854f10e5e7f6f70ec3"))throw new IOException("Changed delivered fixture bytes");
        try(var in=new GZIPInputStream(new ByteArrayInputStream(compressed))){return JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();}
    }
    private static R5CivicAutomaticInstaller.Plan plan(TerritoryDefinition d,BuildingType type)throws Exception{
        var fixture=fixture();var palette=fixture.getAsJsonArray("palette");var docs=fixture.getAsJsonObject("documents");
        return R5CivicAutomaticInstaller.contract(d,type,(civic,path)->{
            JsonObject packed=docs.getAsJsonObject(path),doc=new JsonObject();
            for(String key:List.of("territory_id","module_id","local_group_id","status"))if(packed.has(key))doc.add(key,packed.get(key));
            JsonArray rows=new JsonArray();
            for(var entry:packed.getAsJsonArray("rows")){
                var a=entry.getAsJsonArray();var row=new JsonObject();var xyz=new JsonArray();for(int k=0;k<3;k++)xyz.add(a.get(k));row.add("world_xyz",xyz);
                row.add("expected_before_blockstate",palette.get(a.get(3).getAsInt()));row.add("after_blockstate",palette.get(a.get(4).getAsInt()));rows.add(row);
            }
            doc.add(packed.get("rowKey").getAsString(),rows);return doc;
        });
    }
    private static final class F {
        final GameTestHelper h;final ServerLevel level;final Path dir,ledgerFile;final TerritoryDefinition def;final UUID owner;
        CatanSavedData data=new CatanSavedData();
        F(GameTestHelper h,String city)throws Exception{
            this.h=h;level=h.getLevel();if(!(level.getServer() instanceof GameTestServer))throw new IOException("Fixture only");
            def=MapDefinitionManager.territory(city);ProducerService.validateMap(def);
            dir=Files.createTempDirectory(level.getServer().getWorldPath(LevelResource.ROOT),"paid-asset-").toRealPath();Files.createDirectories(dir.resolve("data"));ledgerFile=dir.resolve("ledger.nbt");
            var n=data.createNation("native fixture",UUID.randomUUID());owner=n.id();n.setTreasury(1000000);for(ResourceType r:ResourceType.values())n.addResource(r,100000);
            var t=data.createTerritory(city,"fixture",ResourceType.WOOD);t.setOwnerNationId(owner);t.setCityLevel(4);save();
        }
        TerritoryData territory(){return data.territory(def.id());}NationData nation(){return data.nation(owner);}
        void save()throws IOException{NbtIo.writeCompressed(data.save(new CompoundTag()),ledgerFile.toFile());}
        void reload()throws IOException{data=CatanSavedData.load(NbtIo.readCompressed(ledgerFile.toFile()));}
        void stage(Map<BlockPos,BlockState> source){
            AABB box=null;for(BlockPos p:source.keySet()){var b=new AABB(p).inflate(2);box=box==null?b:box.minmax(b);}
            // Test-generated mobs only, before deliberate test entities. No owner world is used.
            for(var entity:new ArrayList<>(level.getEntities((net.minecraft.world.entity.Entity)null,box)))if(entity instanceof net.minecraft.world.entity.Mob)entity.discard();
            Set<BlockPos> shell=new HashSet<>();for(BlockPos p:source.keySet())for(var d:net.minecraft.core.Direction.values()){BlockPos q=p.relative(d);if(!source.containsKey(q))shell.add(q);}
            for(BlockPos p:shell)level.setBlock(p,Blocks.STONE.defaultBlockState(),FLAGS);
            for(var e:source.entrySet()){
                var be=level.getBlockEntity(e.getKey());if(be instanceof net.minecraft.world.Container c)c.clearContent();if(be instanceof LecternBlockEntity l)l.setBook(ItemStack.EMPTY);
                level.setBlock(e.getKey(),e.getValue(),FLAGS);
            }
        }
        PaidConstructionRecovery.Environment saving(PaidConstructionRecovery.Environment e){return new PaidConstructionRecovery.Environment(){
            public ServerLevel level(){return e.level();}public Path original(){return e.original();}public byte[] originalBytes(){return e.originalBytes();}public CompoundTag identity(){return e.identity();}public List<PaidConstructionRecovery.Cell> cells()throws IOException{return e.cells();}
            public void authorize()throws IOException{e.authorize();}public boolean paid()throws IOException{return e.paid();}public void checkPayment()throws IOException{e.checkPayment();}public void settle()throws IOException{e.settle();}
            public CompoundTag ledger(){return e.ledger();}public void save()throws IOException{F.this.save();}
        };}
    }
    private static AABB bounds(Collection<BlockPos> positions){AABB out=null;for(var p:positions){var b=new AABB(p).inflate(2);out=out==null?b:out.minmax(b);}return out;}
    private static JsonObject civicJournal(F f,BuildingType type,R5CivicAutomaticInstaller.Plan plan,UUID id)throws Exception{
        var cost=EconomyCatalog.buildingCost(type);JsonObject j=new JsonObject();j.addProperty("schema",1);j.addProperty("phase","PENDING");j.addProperty("transaction_id",id.toString());j.addProperty("source_revision","R5-1 VERIFIED AGAINST R7");
        j.addProperty("world_root",f.dir.toString());j.addProperty("dimension",f.def.dimension());j.addProperty("territory",f.def.id());j.addProperty("module",type.name());j.addProperty("owner_nation_uuid",f.owner.toString());j.addProperty("treasury_before",f.nation().treasury());j.addProperty("money_cost",cost.money());
        JsonObject resources=new JsonObject();for(ResourceType r:ResourceType.values()){JsonObject row=new JsonObject();row.addProperty("before",f.nation().resource(r));row.addProperty("cost",cost.resources().getOrDefault(r,0L));resources.add(r.name(),row);}j.add("resources",resources);
        JsonArray ordered=new JsonArray(),originals=new JsonArray();for(var w:plan.writes()){JsonObject row=new JsonObject();row.add("xyz",R5CivicAutomaticInstaller.xyz(w.pos()));row.addProperty("stage",w.stage());row.addProperty("before",R5CivicAutomaticInstaller.canonical(w.before()));row.addProperty("after",R5CivicAutomaticInstaller.canonical(w.after()));ordered.add(row);}
        for(var c:plan.finalCells().values()){JsonObject row=new JsonObject();row.add("xyz",R5CivicAutomaticInstaller.xyz(c.pos()));row.addProperty("before",R5CivicAutomaticInstaller.canonical(c.before()));row.addProperty("after",R5CivicAutomaticInstaller.canonical(c.after()));row.addProperty("before_nbt","NONE");originals.add(row);}
        j.add("ordered_writes",ordered);j.add("originals",originals);j.add("installed_barrels",new JsonObject());j.addProperty("physical_committed",false);j.addProperty("economic_committed",false);R5CivicAutomaticInstaller.seal(j);return j;
    }
    private static void civic(GameTestHelper h,String city,BuildingType type,boolean alreadyPaid)throws Exception{
        F f=new F(h,city);var plan=plan(f.def,type);Map<BlockPos,BlockState> source=new LinkedHashMap<>();for(var c:plan.finalCells().values())source.put(c.pos(),c.before());
        PaidRecoveryTests.ready(h,bounds(source.keySet()),()->{
            f.stage(source);UUID id=UUID.randomUUID();JsonObject j=civicJournal(f,type,plan,id);Path file=f.dir.resolve("data/catancraft_r5_auto_"+city+"_"+type.name().toLowerCase(Locale.ROOT)+"_v1.json");Files.writeString(file,JSON.toJson(j));byte[] original=Files.readAllBytes(file);
            var cost=EconomyCatalog.buildingCost(type);long before=f.nation().treasury();
            if(alreadyPaid){for(var c:plan.finalCells().values())f.level.setBlock(c.pos(),c.after(),FLAGS);cost.charge(f.nation());f.territory().buildings().add(new BuildingInstance(type,1,0,""));f.save();f.reload();f.nation().addTreasury(125);}
            var e=f.saving(CivicPurchaseRecovery.fixture(f.level.getServer(),f.data,f.level,f.def,f.dir,file,()->{},plan));
            if(!alreadyPaid){PaidRecoveryTests.interrupt(h,e,"write",31);f.reload();e=f.saving(CivicPurchaseRecovery.fixture(f.level.getServer(),f.data,f.level,f.def,f.dir,file,()->{},plan));}
            var barrelCell=plan.finalCells().values().stream().filter(c->c.after().is(Blocks.BARREL)).findFirst().orElseThrow();
            if(alreadyPaid)((BarrelBlockEntity)f.level.getBlockEntity(barrelCell.pos())).setItem(0,new ItemStack(Items.DIAMOND,23));
            PaidConstructionRecovery.resume(e);
            h.assertTrue(f.nation().treasury()==before-cost.money()+(alreadyPaid?125:0),"Civic debit repeated or unrelated funds overwritten");h.assertTrue(f.territory().buildings().stream().filter(b->b.type()==type).count()==1,"Duplicate building");
            if(alreadyPaid)h.assertTrue(((BarrelBlockEntity)f.level.getBlockEntity(barrelCell.pos())).getItem(0).getCount()==23,"Civic inventory lost");
            for(var c:plan.finalCells().values())h.assertTrue(f.level.getBlockState(c.pos()).equals(c.after()),"Actual delivered civic target mismatch");
            h.assertTrue(Arrays.equals(original,Files.readAllBytes(file)),"Original civic journal rewritten");f.reload();h.assertTrue(PaidConstructionRecovery.paid(f.data,id,true,false,false),"Receipt did not persist");h.succeed();
        });
    }
    @GameTest(template="townhall_state_empty",batch="paid_asset01",timeoutTicks=10000)
    public static void city_a_commercial_partial_original_recovers(GameTestHelper h)throws Exception{civic(h,"a",BuildingType.COMMERCIAL_DISTRICT,false);}
    @GameTest(template="townhall_state_empty",batch="paid_asset02",timeoutTicks=10000)
    public static void city_a_logistics_legacy_paid_inventory_preserved(GameTestHelper h)throws Exception{civic(h,"a",BuildingType.LOGISTICS_CENTER,true);}
    @GameTest(template="townhall_state_empty",batch="paid_asset03",timeoutTicks=10000)
    public static void south_commercial_rotated_contract_recovers(GameTestHelper h)throws Exception{civic(h,"i",BuildingType.COMMERCIAL_DISTRICT,false);}
    @GameTest(template="townhall_state_empty",batch="paid_asset04",timeoutTicks=10000)
    public static void south_logistics_rotated_contract_recovers(GameTestHelper h)throws Exception{civic(h,"i",BuildingType.LOGISTICS_CENTER,false);}

    private static void prestige(GameTestHelper h,int from,int revision,boolean alreadyPaid)throws Exception{
        F f=new F(h,"a");int to=from+1;var a=SchematicAssetRegistry.territoryAnchor("th"+from,f.def);
        var before=from==3?SchematicPlacementService.plannedCells("th3",a):TownHallRevisions.plan(from,revision,a);var after=TownHallRevisions.plan(to,2,a);
        f.territory().setCityLevel(from);String sourceToken=from==3?SchematicAssetRegistry.asset("th3").placementToken():TownHallRevisions.token(from,revision),targetToken=TownHallRevisions.token(to,2),key="territory:a:town_hall";
        f.data.setPlacedMapAsset(key,sourceToken);f.save();Map<BlockPos,BlockState> source=new LinkedHashMap<>();for(var c:before)source.put(c.position(),c.state());
        PaidRecoveryTests.ready(h,bounds(source.keySet()),()->{
            f.stage(source);UUID id=UUID.randomUUID();var cost=EconomyCatalog.cityUpgradeCost(from);long treasury=f.nation().treasury();BlockPos manager=before.get(TownHallRevisions.managerIndex()).position();
            var book=new ItemStack(Items.WRITTEN_BOOK);book.getOrCreateTag().putString("title","Actual manager data");book.getOrCreateTag().putString("author","Owner");book.getOrCreateTag().putUUID("protectedOwner",f.owner);((LecternBlockEntity)f.level.getBlockEntity(manager)).setBook(book);
            CompoundTag managerNbt=f.level.getBlockEntity(manager).saveWithFullMetadata().copy();CompoundTag j=new CompoundTag();j.putInt("schema",1);j.putString("phase","PENDING");j.putUUID("transaction",id);j.putString("worldRoot",f.dir.toString());j.putString("city","a");j.putString("ownerNation",f.owner.toString());j.putInt("fromLevel",from);j.putInt("toLevel",to);j.putString("sourceToken",sourceToken);j.putString("targetToken",targetToken);j.putString("targetSHA256",TownHallRevisions.hash(to,2));j.putLong("treasuryBefore",treasury);j.putLong("treasuryCost",cost.money());
            CompoundTag resources=new CompoundTag();for(ResourceType r:ResourceType.values()){var row=new CompoundTag();row.putLong("before",f.nation().resource(r));row.putLong("cost",cost.resources().getOrDefault(r,0L));resources.put(r.name(),row);}j.put("resources",resources);
            j.putInt("managerX",manager.getX());j.putInt("managerY",manager.getY());j.putInt("managerZ",manager.getZ());j.put("managerNbt",managerNbt);
            ListTag deltas=new ListTag();for(int i=0;i<before.size();i++){var old=before.get(i);var next=after.get(i);if(old.position().equals(manager)||old.state().equals(next.state()))continue;CompoundTag row=new CompoundTag();row.putLong("pos",old.position().asLong());row.put("beforeState",NbtUtils.writeBlockState(old.state()));row.put("afterState",NbtUtils.writeBlockState(next.state()));var be=f.level.getBlockEntity(old.position());if(be!=null)row.put("beforeBlockEntity",be.saveWithFullMetadata());deltas.add(row);}j.put("blocks",deltas);
            Path file=f.dir.resolve("data/catancraft_th_prestige_a_"+to+"_v1.dat");NbtIo.writeCompressed(j,file.toFile());byte[] original=Files.readAllBytes(file);
            if(alreadyPaid){for(var cell:after)if(!cell.position().equals(manager))f.level.setBlock(cell.position(),cell.state(),FLAGS);cost.charge(f.nation());f.territory().setCityLevel(to);f.data.setPlacedMapAsset(key,targetToken);f.save();f.reload();f.nation().addTreasury(125);}
            var e=f.saving(PrestigePurchaseRecovery.environment(f.level.getServer(),f.data,f.level,f.def,f.dir,file,()->{}));
            if(!alreadyPaid){PaidRecoveryTests.interrupt(h,e,"write",53);f.reload();e=f.saving(PrestigePurchaseRecovery.environment(f.level.getServer(),f.data,f.level,f.def,f.dir,file,()->{}));}
            PaidConstructionRecovery.resume(e);h.assertTrue(f.territory().cityLevel()==to&&targetToken.equals(f.data.placedMapAsset(key)),"Prestige paid tier/token mismatch");h.assertTrue(f.nation().treasury()==treasury-cost.money()+(alreadyPaid?125:0),"Prestige charge repeated or unrelated funds lost");
            h.assertTrue(managerNbt.equals(f.level.getBlockEntity(manager).saveWithFullMetadata()),"Actual management book NBT lost");h.assertTrue(Arrays.equals(original,Files.readAllBytes(file)),"Prestige original history rewritten");
            for(var cell:after)h.assertTrue(TownHallInteractionPolicy.matches(f.level.getBlockState(cell.position()),cell.state(),cell.position().equals(manager)),"Full prestige target mismatch "+cell.position());h.succeed();
        });
    }
    @GameTest(template="townhall_state_empty",batch="paid_asset05",timeoutTicks=12000)
    public static void th3_to_th4_original_paid_journal_partial_recovery(GameTestHelper h)throws Exception{prestige(h,3,1,false);}
    @GameTest(template="townhall_state_empty",batch="paid_asset06",timeoutTicks=12000)
    public static void th4_r1_to_th5_paid_journal_partial_recovery(GameTestHelper h)throws Exception{prestige(h,4,1,false);}
    @GameTest(template="townhall_state_empty",batch="paid_asset07",timeoutTicks=12000)
    public static void th4_r2_to_th5_legacy_saved_payment_not_repeated(GameTestHelper h)throws Exception{prestige(h,4,2,true);}
}
