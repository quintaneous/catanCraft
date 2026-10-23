package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.*;
import com.quin.catancraft.economy.EconomyCost;
import com.quin.catancraft.map.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Same canonical city, shared ledger, actual native transaction composition. Disposable server only. */
@GameTestHolder("catancraft_integration") @PrefixGameTestTemplate(false)
public final class CombinedIntegrationTests {
    private static final int FLAGS=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS;
    @GameTest(template="townhall_state_empty",batch="combined_north_a",timeoutTicks=2400)
    public static void canonical_city_a_four_components_shared_progress(GameTestHelper h){run(h,"a");}
    @GameTest(template="townhall_state_empty",batch="combined_south_i",timeoutTicks=2400)
    public static void canonical_city_i_four_components_shared_progress(GameTestHelper h){run(h,"i");}
    private static void run(GameTestHelper h,String city){
        try{
            Fixture f=new Fixture(h,city);f.prepare();
            h.runAfterDelay(20,()->{try{
                h.assertTrue(f.level.getEntitiesOfClass(ItemEntity.class,f.harmless.getBoundingBox().inflate(0.1)).stream().anyMatch(e->e.getUUID().equals(f.harmlessId)),"Deliberately preserved item must be visible in native entity query before transactions");
                f.compose();h.runAfterDelay(40,()->{try{f.delayed();h.succeed();}catch(Exception x){throw new RuntimeException(x);}});
            }catch(Exception x){throw new RuntimeException(x);}});
        }catch(Exception x){throw new RuntimeException(x);}
    }
    private static final class Fixture {
        final GameTestHelper h;final ServerLevel level;final TerritoryDefinition def;final String city;final Path dir;final UUID owner;
        CatanSavedData data=new CatanSavedData();final StreetscapeR5.Plan publicPlan;final MapAnchor settlement;
        final Map<BlockPos,BlockState> source=new LinkedHashMap<>();final Map<BlockPos,String> provenance=new HashMap<>();final Set<BlockPos> houseContext=new HashSet<>(),producerContext=new HashSet<>();
        final Map<BlockPos,BlockState> publicWrites=new HashMap<>(),houseWrites=new HashMap<>(),producerWrites=new HashMap<>(),defenseWrites=new HashMap<>();
        final List<ProducerTransaction.Plot> plots=new ArrayList<>();final List<R5HouseRevisions.Revision> branches=new ArrayList<>();
        final List<DefenseRevisions.Plan> defensePlans;final Set<String> purchased=new HashSet<>();final BlockPos book;
        final Map<BlockPos,CompoundTag> protectedNbt=new HashMap<>();ItemEntity harmless;UUID harmlessId;ItemStack item;CompoundTag initialNation,initialTerritory,freeLedger;Map<BlockPos,BlockState> freeStates;long paidMoney;final Map<ResourceType,Long> paidMaterials=new EnumMap<>(ResourceType.class);
        Fixture(GameTestHelper h,String city)throws IOException {
            this.h=h;this.level=h.getLevel();this.city=city;
            if(!(level.getServer() instanceof GameTestServer))throw new IOException("Combined fixtures require isolated GameTestServer");
            def=MapDefinitionManager.territory(city);if(def==null)throw new IOException("Canonical active map not loaded for city "+city);ProducerService.validateMap(def);
            settlement=def.settlementAnchor();if(settlement==null)throw new IOException("Missing canonical settlement anchor");
            publicPlan=StreetscapeR5.plan(def.cityCenter());defensePlans=DefenseRevisions.city(city);
            dir=level.getServer().getWorldPath(LevelResource.ROOT).resolve("data/combined-tests/"+city+"_"+UUID.randomUUID());Files.createDirectories(dir);
            NationData nation=data.createNation("Combined "+city,UUID.randomUUID());owner=nation.id();nation.addTreasury(1000000);for(ResourceType r:ResourceType.values())nation.addResource(r,100000);
            TerritoryData territory=data.createTerritory(city,"Combined "+city,def.specialty());territory.setOwnerNationId(owner);territory.setProducerLevel(5);territory.setCityLevel(5);
            for(var c:publicPlan.cells()){add(c.pos(),c.before(),"public read");if(c.changes())put(publicWrites,c.pos(),c.after(),"public");}
            var groups=R5HouseRevisions.groups();
            for(int i=0;i<groups.size();i++){
                var branch=i%2==0?R5HouseRevisions.Revision.R1:R5HouseRevisions.Revision.R2;branches.add(branch);
                for(var c:R5HouseRevisions.plan(groups.get(i),branch,settlement)){houseContext.add(c.position());add(c.position(),c.state(),"house "+groups.get(i)+" "+branch);}
                for(var c:R5HouseRevisions.changes(groups.get(i),branch,settlement))put(houseWrites,c.position(),c.after(),"house");
            }
            for(String family:ProducerRevisions.applicable(def)){
                MapAnchor anchor=SchematicAssetRegistry.territoryAnchor(family,def);if(anchor==null)throw new IOException("Missing producer anchor "+family);
                plots.add(new ProducerTransaction.Plot(family,5,5,anchor,false,true));
                var original=ProducerRevisions.plan(family,5,anchor);var corrected=ProducerExteriorR2.plan(family,5,anchor);
                for(int i=0;i<original.size();i++){var a=original.get(i);var b=corrected.get(i);producerContext.add(a.position());add(a.position(),a.state(),"producer "+family);if(!a.state().equals(b.state()))put(producerWrites,a.position(),b.state(),"producer");}
                data.setPlacedMapAsset("producer:"+family,ProducerRevisions.token(family,5));
            }
            for(var p:defensePlans){for(var c:p.cells()){add(c.pos(),c.before(),"defense "+p.socket());put(defenseWrites,c.pos(),c.after(),"defense "+p.socket());}for(var r:p.reads())add(r.pos(),r.state(),"defense read "+p.socket());}
            List<Map<BlockPos,BlockState>> sets=List.of(publicWrites,houseWrites,producerWrites,defenseWrites);Set<BlockPos> all=new HashSet<>();
            for(var set:sets)for(BlockPos p:set.keySet())if(!all.add(p))throw new IOException("Combined sparse write overlap at "+p);
            // Every other component's full source read remains compatible with each sparse target.
            for(BlockPos p:publicWrites.keySet())if(houseContext.contains(p)||producerContext.contains(p))throw new IOException("Public write enters protected complete context "+p);
            for(var plan:defensePlans)for(var read:plan.reads()){
                BlockState free=publicWrites.getOrDefault(read.pos(),houseWrites.getOrDefault(read.pos(),producerWrites.getOrDefault(read.pos(),source.get(read.pos()))));
                BlockState assembled=defenseWrites.getOrDefault(read.pos(),free);
                // Authored compatible modules may occupy each other's preserved-air envelope.
                if(!defenseWrites.containsKey(read.pos())&&!assembled.equals(read.state()))throw new IOException("Free targets invalidate defense protected read at "+read.pos()+" for "+plan.socket());
            }
            book=new BlockPos(def.managementAnchor().x(),def.managementAnchor().y(),def.managementAnchor().z());
            if(source.containsKey(book))throw new IOException("Management fixture intersects an immutable read context at "+book);
            add(book,Blocks.BARREL.defaultBlockState(),"protected management inventory");
        }
        private void put(Map<BlockPos,BlockState> target,BlockPos p,BlockState s,String name)throws IOException {if(target.put(p,s)!=null)throw new IOException("Duplicate "+name+" sparse write at "+p);}
        private void add(BlockPos p,BlockState state,String origin)throws IOException {
            BlockState old=source.putIfAbsent(p,state);if(old!=null&&!old.equals(state))throw new IOException("Canonical source conflict at "+p+": "+provenance.get(p)+"="+old+" vs "+origin+"="+state);provenance.putIfAbsent(p,origin);
        }
        NationData nation(){return data.nation(owner);}TerritoryData territory(){return data.territory(city);}CompoundTag ledger(){return data.save(new CompoundTag());}
        CompoundTag identity(String kind){CompoundTag n=new CompoundTag();n.putString("world",dir.toString());n.putString("city",city);n.putUUID("owner",owner);n.putString("kind",kind);return n;}
        void save()throws IOException {try(OutputStream out=Files.newOutputStream(dir.resolve("shared-ledger.nbt"))){NbtIo.writeCompressed(ledger(),out);}}
        void allow(BlockPos p)throws IOException {if(!owner.equals(territory().ownerNationId())||!def.siegeRegion().contains(p)||!def.restorationRegion().contains(p))throw new IOException("Combined fixture ownership/bounds changed");}
        void prepare()throws IOException {
            // Entirely artificial source fixture; retained source contexts are never runtime paste instructions.
            Set<Long> chunks=new HashSet<>();for(BlockPos p:source.keySet())for(int dx=-2;dx<=2;dx+=4)for(int dz=-2;dz<=2;dz+=4)chunks.add(net.minecraft.world.level.ChunkPos.asLong((p.getX()+dx)>>4,(p.getZ()+dz)>>4));
            for(long packed:chunks){var p=new net.minecraft.world.level.ChunkPos(packed);level.setChunkForced(p.x,p.z,true);level.getChunk(p.x,p.z);}
            // Complete the artificial fixture boundary before staging immutable contexts.
            // Canonical coordinates can retain unrelated blocks from an earlier fixture. A
            // floating gravel neighbor outside every read context must not become invented
            // authored terrain. Clear that unspecified shell, and provide an inert subsoil
            // bed only below the complete source ground. Never rewrite a contract cell.
            Set<BlockPos> shell=new HashSet<>();for(BlockPos position:source.keySet())for(net.minecraft.core.Direction direction:net.minecraft.core.Direction.values()){
                BlockPos outside=position.relative(direction);if(!source.containsKey(outside))shell.add(outside);
            }
            int bedY=source.keySet().stream().mapToInt(BlockPos::getY).min().orElseThrow()-1;
            Map<BlockPos,BlockState> boundary=new HashMap<>();for(BlockPos outside:shell)boundary.put(outside,outside.getY()<=bedY?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
            List<Map<BlockPos,BlockState>> scenes=List.of(source,publicWrites,houseWrites,producerWrites,defenseWrites);
            for(var scene:scenes)for(var e:scene.entrySet())if(e.getValue().getBlock() instanceof FallingBlock&&!source.containsKey(e.getKey().below()))boundary.put(e.getKey().below(),Blocks.STONE.defaultBlockState());
            // A one-cell air shell is not a fluid barrier. In the south fixture, generated
            // source water just beyond a diagonal/upper shell flowed into an exact public
            // read during the entity-readiness delay. Enclose this artificial scene two
            // cells beyond its complete source bounds, leaving its original adjacent air
            // shell intact. Remove only unspecified generated fluids inside the enclosure;
            // every explicit source/read cell, including authored Farm water, is preserved. Glass blocks
            // incoming fluids without adding an opaque ceiling over grass/light-sensitive reads.
            int isolationMinX=source.keySet().stream().mapToInt(BlockPos::getX).min().orElseThrow()-2;
            int isolationMaxX=source.keySet().stream().mapToInt(BlockPos::getX).max().orElseThrow()+2;
            int isolationMinY=source.keySet().stream().mapToInt(BlockPos::getY).min().orElseThrow()-2;
            int isolationMaxY=source.keySet().stream().mapToInt(BlockPos::getY).max().orElseThrow()+2;
            int isolationMinZ=source.keySet().stream().mapToInt(BlockPos::getZ).min().orElseThrow()-2;
            int isolationMaxZ=source.keySet().stream().mapToInt(BlockPos::getZ).max().orElseThrow()+2;
            // The source is fixed at canonical coordinates. An unrelated generated animal
            // below that source must not be buried by our artificial glass floor.
            // Move only that synthetic face downward; never move an entity or authored cell.
            int initialIsolationMinY=isolationMinY;
            while(true){
                var floor=new net.minecraft.world.phys.AABB(isolationMinX,isolationMinY,isolationMinZ,isolationMaxX+1,isolationMinY+1,isolationMaxZ+1);
                net.minecraft.world.entity.Entity conflict=null;
                for(var entity:level.getAllEntities())if(entity.getBoundingBox().intersects(floor)){conflict=entity;break;}
                if(conflict==null)break;
                int lowered=Math.min(isolationMinY-1,(int)Math.floor(conflict.getBoundingBox().minY)-2);
                if(initialIsolationMinY-lowered>32||lowered<level.getMinBuildHeight())throw new IOException("Fixture synthetic floor cannot safely clear entity within bounded 32-cell expansion: type="+net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(conflict.getType())+" uuid="+conflict.getUUID()+" pos="+conflict.position()+" box="+conflict.getBoundingBox());
                for(var scene:scenes)for(BlockPos p:scene.keySet())if(p.getY()==lowered&&p.getX()>=isolationMinX&&p.getX()<=isolationMaxX&&p.getZ()>=isolationMinZ&&p.getZ()<=isolationMaxZ)throw new IOException("Synthetic floor expansion enters immutable source/ownership at "+p);
                CatanCraft.LOGGER.info("COMBINED_FIXTURE_FLOOR_CLEARANCE city={} oldY={} newY={} entityType={} uuid={} pos={} box={}",city,isolationMinY,lowered,net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(conflict.getType()),conflict.getUUID(),conflict.position(),conflict.getBoundingBox());
                isolationMinY=lowered;
            }
            for(int x=isolationMinX>>4;x<=isolationMaxX>>4;x++)for(int z=isolationMinZ>>4;z<=isolationMaxZ>>4;z++){
                level.setChunkForced(x,z,true);level.getChunk(x,z);
            }
            // This is an artificial GameTest scene, not an authored user save.
            // Remove generated mobs before the deliberate occupancy-test actors exist.
            if(!(level.getServer() instanceof GameTestServer))throw new IOException("Fixture isolation outside GameTest server");
            var artificialScene=new net.minecraft.world.phys.AABB(isolationMinX-1,isolationMinY-1,isolationMinZ-1,isolationMaxX+2,isolationMaxY+2,isolationMaxZ+2);
            int generatedMobs=0;
            for(var entity:new ArrayList<>(level.getEntities((net.minecraft.world.entity.Entity)null,artificialScene)))
                if(entity instanceof net.minecraft.world.entity.Mob){entity.discard();generatedMobs++;}
            CatanCraft.LOGGER.info("COMBINED_FIXTURE_GENERATED_MOBS_REMOVED city={} count={} beforeTestEntities=true",city,generatedMobs);
            int enclosureCells=0,unspecifiedFluids=0;
            for(BlockPos mutable:BlockPos.betweenClosed(isolationMinX,isolationMinY,isolationMinZ,isolationMaxX,isolationMaxY,isolationMaxZ)){
                BlockPos position=mutable.immutable();if(source.containsKey(position))continue;
                boolean face=position.getX()==isolationMinX||position.getX()==isolationMaxX
                    ||position.getY()==isolationMinY||position.getY()==isolationMaxY
                    ||position.getZ()==isolationMinZ||position.getZ()==isolationMaxZ;
                if(face){boundary.put(position,Blocks.GLASS.defaultBlockState());enclosureCells++;}
                else if(!level.getFluidState(position).isEmpty()){boundary.put(position,Blocks.AIR.defaultBlockState());unspecifiedFluids++;}
            }
            // An artificial barrier must not bury or relocate an existing entity. The
            // deliberately preserved item is created later, inside the immutable scene.
            List<net.minecraft.world.phys.AABB> enclosureFaces=List.of(
                new net.minecraft.world.phys.AABB(isolationMinX,isolationMinY,isolationMinZ,isolationMinX+1,isolationMaxY+1,isolationMaxZ+1),
                new net.minecraft.world.phys.AABB(isolationMaxX,isolationMinY,isolationMinZ,isolationMaxX+1,isolationMaxY+1,isolationMaxZ+1),
                new net.minecraft.world.phys.AABB(isolationMinX,isolationMinY,isolationMinZ,isolationMaxX+1,isolationMinY+1,isolationMaxZ+1),
                new net.minecraft.world.phys.AABB(isolationMinX,isolationMaxY,isolationMinZ,isolationMaxX+1,isolationMaxY+1,isolationMaxZ+1),
                new net.minecraft.world.phys.AABB(isolationMinX,isolationMinY,isolationMinZ,isolationMaxX+1,isolationMaxY+1,isolationMinZ+1),
                new net.minecraft.world.phys.AABB(isolationMinX,isolationMinY,isolationMaxZ,isolationMaxX+1,isolationMaxY+1,isolationMaxZ+1));
            for(var entity:level.getAllEntities())for(var face:enclosureFaces)
                if(entity.getBoundingBox().intersects(face))throw new IOException("Fixture enclosure intersects existing entity type="+net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())+" uuid="+entity.getUUID()+" pos="+entity.position()+" box="+entity.getBoundingBox()+" face="+face);
            CatanCraft.LOGGER.info("COMBINED_FIXTURE_FLUID_ISOLATION city={} min=[{},{},{}] max=[{},{},{}] enclosureCells={} unspecifiedFluidsRemoved={} authoredCellsPreserved={}",
                city,isolationMinX,isolationMinY,isolationMinZ,isolationMaxX,isolationMaxY,isolationMaxZ,enclosureCells,unspecifiedFluids,source.size());
            for(var e:boundary.entrySet()){
                if(source.containsKey(e.getKey()))throw new IOException("Boundary fixture would modify immutable context");
                level.getChunkAt(e.getKey());var previous=level.getBlockEntity(e.getKey());if(previous instanceof Container inventory)inventory.clearContent();level.setBlock(e.getKey(),e.getValue(),FLAGS);
            }
            CatanCraft.LOGGER.info("COMBINED_FIXTURE_BOUNDARY city={} outsideReadContexts={} immutableCells={} sourceBedY={}",city,boundary.size(),source.size(),bedY);
            for(var e:source.entrySet()){var be=level.getBlockEntity(e.getKey());if(be instanceof Container c)c.clearContent();level.setBlock(e.getKey(),e.getValue(),FLAGS);}
            for(var e:source.entrySet())if(e.getValue().is(Blocks.BARREL)){
                var barrel=(BarrelBlockEntity)level.getBlockEntity(e.getKey());barrel.clearContent();ItemStack stack=new ItemStack(e.getKey().equals(book)?Items.WRITTEN_BOOK:Items.DIAMOND,e.getKey().equals(book)?1:17);
                stack.getOrCreateTag().putString("protectedPayload","combined actual inventory "+city);if(e.getKey().equals(book)){stack.getOrCreateTag().putString("title","Actual management book");stack.getOrCreateTag().putString("author","Owner");}barrel.setItem(0,stack);protectedNbt.put(e.getKey(),barrel.saveWithFullMetadata().copy());
            }
            // Fixture setup can release items from previous tests. Clear stale drops BEFORE adding the deliberately preserved item.
            var cityBox=new net.minecraft.world.phys.AABB(def.siegeRegion().minX(),def.siegeRegion().minY(),def.siegeRegion().minZ(),def.siegeRegion().maxX()+1,def.siegeRegion().maxY()+1,def.siegeRegion().maxZ()+1);
            for(var entity:new ArrayList<>(level.getEntities((net.minecraft.world.entity.Entity)null,cityBox)))if(entity instanceof ItemEntity)entity.discard();
            BlockPos support=null;outer:for(var plot:plots)for(var c:ProducerRevisions.plan(plot.family(),5,plot.anchor())){
                BlockPos p=c.position();if(c.state().isCollisionShapeFullBlock(level,p)&&level.getBlockState(p.above()).isAir()&&!producerWrites.containsKey(p.above())&&!producerWrites.containsKey(p)) {support=p;break outer;}
            }
            if(support==null)throw new IOException("No harmless producer-plot item fixture support");
            item=new ItemStack(Items.EMERALD,7);item.getOrCreateTag().putString("keep","same-city nearby item");harmless=new ItemEntity(level,support.getX()+0.5,support.getY()+1.1,support.getZ()+0.5,item.copy());harmless.setNoGravity(true);harmless.setDeltaMovement(Vec3.ZERO);level.addFreshEntity(harmless);harmlessId=harmless.getUUID();
            initialNation=nation().save();initialTerritory=territory().save();save();
        }
        ProducerTransaction.Environment producer(){return new ProducerTransaction.Environment(){
            public ServerLevel level(){return level;}public Path file(){return dir.resolve("producer.dat");}public ProducerTransaction.Spec spec(){return new ProducerTransaction.Spec(5,5,plots);}public CompoundTag identity(){return Fixture.this.identity("producer");}
            public NationData nation(){return Fixture.this.nation();}public TerritoryData territory(){return Fixture.this.territory();}
            public String token(String family){return data.placedMapAsset("producer:"+family);}public void token(String family,String value){data.setPlacedMapAsset("producer:"+family,value);}
            public String receipt(){return data.placedMapAsset("producer_receipt");}public void receipt(String value){data.setPlacedMapAsset("producer_receipt",value);}
            public CompoundTag ledger(){return Fixture.this.ledger();}public void dirty(){data.setDirty();}public void save()throws IOException{Fixture.this.save();}
            public void allowWrite(BlockPos p)throws IOException{allow(p);if(!producerWrites.containsKey(p))throw new IOException("Combined producer write outside corrected delta");}
        };}
        StreetscapeTransaction.Environment publicSpace(){return new StreetscapeTransaction.Environment(){
            public ServerLevel level(){return level;}public Path file(){return dir.resolve("public.dat");}public StreetscapeR5.Plan plan(){return publicPlan;}public CompoundTag identity(){return Fixture.this.identity("public");}public CompoundTag ledger(){return Fixture.this.ledger();}public void save()throws IOException{Fixture.this.save();}public void allowWrite(BlockPos p)throws IOException{allow(p);if(!publicWrites.containsKey(p))throw new IOException("Combined public write outside exact mask");}
        };}
        R5HouseTransaction.Environment houses(){return new R5HouseTransaction.Environment(){
            public ServerLevel level(){return level;}public Path file(){return dir.resolve("houses.dat");}public MapAnchor anchor(){return settlement;}public CompoundTag identity(){return Fixture.this.identity("houses");}public CompoundTag ledger(){return Fixture.this.ledger();}public void save()throws IOException{Fixture.this.save();}public void allowWrite(BlockPos p)throws IOException{allow(p);if(!houseWrites.containsKey(p))throw new IOException("Combined house write outside route");}
        };}
        DefenseTransaction.Environment defense(DefenseRevisions.Plan plan)throws IOException {
            EconomyCost cost=DefenseCostCatalog.cost(plan.module());return new DefenseTransaction.Environment(){
                public ServerLevel level(){return level;}public Path file(){return dir.resolve("defense_"+plan.socket()+".dat");}public DefenseRevisions.Plan plan(){return plan;}public CompoundTag identity(){return Fixture.this.identity("defense_"+plan.socket());}public EconomyCost cost(){return cost;}public NationData nation(){return Fixture.this.nation();}
                public String receipt(){return data.placedMapAsset("defense_receipt:"+plan.socket());}public void receipt(String v){data.setPlacedMapAsset("defense_receipt:"+plan.socket(),v);}
                public String owned(){String token=data.placedMapAsset("defense_owned:"+plan.socket());return "unowned".equals(token)?null:token;}public void owned(String v){data.setPlacedMapAsset("defense_owned:"+plan.socket(),v==null?"unowned":v);if(v==null)purchased.remove(plan.socket());else purchased.add(plan.socket());}
                public BlockState readState(DefenseRevisions.Read r){for(var p:defensePlans)if(purchased.contains(p.socket())&&!p.socket().equals(plan.socket()))for(var c:p.cells())if(c.pos().equals(r.pos()))return c.after();return r.state();}
                public void requirements(boolean rollback)throws IOException{
                    if(!owner.equals(territory().ownerNationId()))throw new IOException("Combined defense owner changed");
                    for(String dep:plan.dependencies())if(!purchased.contains(dep))throw new IOException("Combined defense dependency missing");
                    for(var p:defensePlans)if(purchased.contains(p.socket())&&!p.socket().equals(plan.socket())){
                        if(plan.excludes().contains(p.socket())||p.excludes().contains(plan.socket()))throw new IOException("Combined defense exclusion");if(rollback&&p.dependencies().contains(plan.socket()))throw new IOException("Combined reverse dependency");
                    }
                }
                public void allow(BlockPos p)throws IOException{Fixture.this.allow(p);if(!defenseWrites.containsKey(p))throw new IOException("Combined defense write outside approved masks");}public void save()throws IOException{Fixture.this.save();}
            };
        }
        void compose()throws IOException {
            CatanCraft.LOGGER.info("COMBINED_STAGE city={} stage=public",city);
            CompoundTag before=ledger();h.assertTrue(StreetscapeTransaction.run(publicSpace(),StreetscapeTransaction.Action.APPLY).writes()==1661,"Combined public write count");h.assertTrue(before.equals(ledger()),"Public changed shared ledger");
            CatanCraft.LOGGER.info("COMBINED_STAGE city={} stage=producer_r2",city);
            h.assertTrue(ProducerTransaction.run(producer(),ProducerTransaction.Action.APPLY).writes()==producerWrites.size(),"Combined free producer correction count");assertPaidProgress();
            CatanCraft.LOGGER.info("COMBINED_STAGE city={} stage=houses",city);
            before=ledger();h.assertTrue(R5HouseTransaction.run(houses(),R5HouseTransaction.Action.APPLY).writes()==houseWrites.size(),"Combined six-house mixed branch write count");h.assertTrue(before.equals(ledger()),"Houses changed shared ledger");assertFreeTargets();checkInventories();
            h.assertTrue(StreetscapeTransaction.run(publicSpace(),StreetscapeTransaction.Action.APPLY).writes()==0,"Public duplicate wrote");h.assertTrue(ProducerTransaction.run(producer(),ProducerTransaction.Action.APPLY).writes()==0,"Producer duplicate wrote");h.assertTrue(R5HouseTransaction.run(houses(),R5HouseTransaction.Action.APPLY).writes()==0,"House duplicate wrote");
            freeLedger=ledger();freeStates=new HashMap<>();for(var map:List.of(publicWrites,houseWrites,producerWrites))for(var e:map.entrySet())freeStates.put(e.getKey(),e.getValue());
            CatanCraft.LOGGER.info("COMBINED_STAGE city={} stage=defense_purchases",city);
            for(String socket:List.of("access_checkpoint","r6_casemate_w","r6_west_01_half_0")){
                var p=DefenseRevisions.plan(city,socket);var e=defense(p);EconomyCost cost=e.cost();
                h.assertTrue(DefenseTransaction.run(e,DefenseTransaction.Action.APPLY).writes()==p.cells().size(),"Combined defense sparse write count");paidMoney+=cost.money();cost.resources().forEach((r,n)->paidMaterials.merge(r,n,Long::sum));
                CompoundTag paid=nation().save();h.assertTrue(DefenseTransaction.run(e,DefenseTransaction.Action.APPLY).writes()==0&&paid.equals(nation().save()),"Combined defense duplicate charge/write");
                assertFreeTargets();checkInventories();
            }
            NationData original=NationData.load(initialNation);h.assertTrue(nation().treasury()==original.treasury()-paidMoney,"Combined one-charge money accounting");for(ResourceType r:ResourceType.values())h.assertTrue(nation().resource(r)==original.resource(r)-paidMaterials.getOrDefault(r,0L),"Combined one-charge resource accounting "+r);
            for(String socket:List.of("r6_west_01_half_0","r6_casemate_w","access_checkpoint")){
                var p=DefenseRevisions.plan(city,socket);DefenseTransaction.run(defense(p),DefenseTransaction.Action.ROLLBACK);for(var c:p.cells())h.assertTrue(level.getBlockState(c.pos()).equals(c.before()),"Combined defense reverse source mismatch");assertFreeTargets();checkInventories();
            }
            h.assertTrue(initialNation.equals(nation().save())&&initialTerritory.equals(territory().save()),"Defense rollback did not restore exact funds/paid ownership progress");
            save();try(InputStream input=Files.newInputStream(dir.resolve("shared-ledger.nbt"))){data=CatanSavedData.load(NbtIo.readCompressed(input));}assertPaidProgress();assertFreeTargets();checkInventories();
            h.assertTrue(!harmless.isRemoved()&&harmlessId.equals(harmless.getUUID())&&ItemStack.matches(item,harmless.getItem()),"Combined transactions modified harmless item UUID/stack");
            CatanCraft.LOGGER.info("COMBINED_NATIVE city={} rotation={} producerFamilies={} publicWrites=1661 houseWrites={} producerWrites={} defenseCandidates=54 defensePurchases=3 rollback=PASS sharedLedger=PASS NBT=PASS",city,def.cityCenter().rotation(),plots.size(),houseWrites.size(),producerWrites.size());
        }
        void assertPaidProgress(){h.assertTrue(initialNation.equals(nation().save())&&initialTerritory.equals(territory().save()),"Free migrations changed resources/paid levels/ownership");}
        void assertFreeTargets()throws IOException {
            for(var c:publicPlan.cells())h.assertTrue(level.getBlockState(c.pos()).equals(c.after()),"Combined public target/read drift "+c.pos());
            for(String group:R5HouseRevisions.groups())for(var c:R5HouseRevisions.plan(group,R5HouseRevisions.Revision.R5,settlement))h.assertTrue(level.getBlockState(c.position()).equals(c.state()),"Combined whole-house target drift "+c.position());
            for(var plot:plots)for(var c:ProducerExteriorR2.plan(plot.family(),5,plot.anchor()))h.assertTrue(ProducerRevisions.matches(level.getBlockState(c.position()),c.state()),"Combined producer target drift "+c.position());
        }
        void checkInventories(){for(var e:protectedNbt.entrySet()){var be=level.getBlockEntity(e.getKey());h.assertTrue(be!=null&&e.getValue().equals(be.saveWithFullMetadata()),"Combined lost actual management/inventory NBT "+e.getKey());}}
        void delayed()throws IOException {
            assertPaidProgress();assertFreeTargets();checkInventories();h.assertTrue(!harmless.isRemoved()&&harmlessId.equals(harmless.getUUID())&&ItemStack.matches(item,harmless.getItem()),"Delayed item/stack lost");
            h.assertTrue(ProducerTransaction.run(producer(),ProducerTransaction.Action.APPLY).writes()==0,"Delayed shared-ledger producer duplicate");h.assertTrue(R5HouseTransaction.run(houses(),R5HouseTransaction.Action.APPLY).writes()==0,"Delayed house duplicate");
            for(var e:freeStates.entrySet())h.assertTrue(level.getBlockState(e.getKey()).equals(e.getValue()),"Delayed free sparse state drift "+e.getKey());
            CatanCraft.LOGGER.info("COMBINED_DELAYED city={} ticks=40 sourceBranches=R1_R2 allFreeTargets=PASS sharedPaidProgress=PASS inventoryBook=PASS harmlessItem=PASS",city);
        }
    }
}
