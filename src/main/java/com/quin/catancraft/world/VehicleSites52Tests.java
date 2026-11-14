package com.quin.catancraft.world;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.*;
import com.quin.catancraft.map.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Native sparse counter fixtures and detached construction ledgers. These tests do not
 * establish authenticated-client interaction or complete authored vehicle departure routes. */
@GameTestHolder("catancraft_vehicle52") @PrefixGameTestTemplate(false)
public final class VehicleSites52Tests {
    private static final Gson GSON=new Gson();
    private static final String DIMENSION="minecraft:overworld";
    private static final UUID OWNER=UUID.fromString("e2688e50-7924-4909-9eab-2b3ca524b8b1");
    private static final Path IDENTITY_ROOT=Path.of("/synthetic-vehicle52-identity-only");
    private static final String FACTORY_SHA="793e2a277fcb7287ba76298d2080808e315bcf626f76e9fe8f9ce5143e731649";
    private VehicleSites52Tests(){}

    @GameTest(template="townhall_state_empty",batch="vehicle52_sites",timeoutTicks=1200)
    public static void all_48_vehicle_counters_match_actual_placement_and_independent_coordinates(GameTestHelper h)throws Exception {
        controlled(h);Set<BlockPos> distinct=new HashSet<>();int count=0,context=0;
        var volume=BasicConstructionAssets.load("vehicle_factory");
        for(var definition:definitions()){
            VehicleFactory50.canonical(definition);
            int index=definition.id().charAt(0)-'a',cx=-750+500*(index%4),cz=-750+500*(index/4),sign=index<8?1:-1;
            var sites=VehicleSites52.sites(definition);require(sites.size()==3,"City lost an ordinary vehicle-factory slot");
            for(var site:sites){
                require(distinct.add(site.counter()),"Two vehicle sites share a counter");
                int x=cx+sign*(site.plot().equals("plot_4")?56:-56);
                int z=cz+sign*(site.plot().equals("plot_1")?-30:site.plot().equals("plot_3")?10:22);
                require(site.counter().equals(new BlockPos(x,82,z)),"Vehicle counter differs from independent city layout");
                int rotation=(index<8?0:180)^(site.plot().equals("plot_4")?180:0);
                require(site.anchor().rotation()==rotation,"Southern/plot-4 orientation changed");
                var resolved=VehicleSites52.resolve(MapDefinitionManager.territories(),DIMENSION,site.counter());
                require(resolved.city().equals(site.city())&&resolved.plot().equals(site.plot()),"Resolver selected another city/slot");
                var anchored=VehicleSites52.byAnchor(site.anchor());
                require(anchored.city().equals(site.city())&&anchored.plot().equals(site.plot()),"Anchor resolution selected another city/slot");
                require(site.legacyPurchase()==(site.city().equals("a")&&site.plot().equals("plot_1")),"Legacy delivery provider escaped A plot1");
                var plan=BasicConstructionAssets.plan("vehicle_factory",site.anchor());
                for(int[] xyz:context()){
                    var cell=plan.get((xyz[1]*volume.length()+xyz[2])*volume.width()+xyz[0]);
                    require(cell.position().equals(ShopFacility40.local(site.anchor(),xyz[0],xyz[1],xyz[2])),"Counter/access transform differs from construction plan");
                    context++;
                }
                require(plan.get((3*volume.length()+9)*volume.width()+18).position().equals(site.counter()),"Counter is not the placed crafting table");
                require(volume.at(18,3,9).is(Blocks.CRAFTING_TABLE),"Factory counter source changed");count++;
            }
        }
        require(count==48&&distinct.size()==48&&context==2496,"Incomplete all-city counter/access coverage");
        CatanCraft.LOGGER.info("VEHICLE52_TRANSFORMS cities=16 sites={} pinnedContextPositions={}",count,context);h.succeed();
    }

    @GameTest(template="townhall_state_empty",batch="vehicle52_resolution",timeoutTicks=200)
    public static void absent_ambiguous_wrong_dimension_and_changed_map_refuse(GameTestHelper h)throws Exception {
        controlled(h);var d=MapDefinitionManager.territory("d");var selected=site(d,"plot_1");
        refuse(()->VehicleSites52.resolve(List.of(d,d),DIMENSION,selected.counter()));
        refuse(()->VehicleSites52.resolve(List.of(),DIMENSION,selected.counter()));
        refuse(()->VehicleSites52.resolve(List.of(d),"minecraft:the_nether",selected.counter()));
        var manager=d.managementAnchor();
        refuse(()->VehicleSites52.resolve(List.of(d),DIMENSION,new BlockPos(manager.x(),manager.y(),manager.z())));
        for(String city:List.of("d","i")){
            var original=MapDefinitionManager.territory(city);
            JsonObject changed=GSON.toJsonTree(original).getAsJsonObject();
            changed.getAsJsonObject("managementAnchor").addProperty("x",original.managementAnchor().x()+1);
            var moved=GSON.fromJson(changed,TerritoryDefinition.class);
            refuse(()->VehicleFactory50.canonical(moved));
            refuse(()->VehicleSites52.resolve(List.of(moved),DIMENSION,site(moved,"plot_1").counter()));
            changed=GSON.toJsonTree(original).getAsJsonObject();
            changed.getAsJsonArray("buildingPlots").get(0).getAsJsonObject().addProperty("x",0);
            var plot=GSON.fromJson(changed,TerritoryDefinition.class);refuse(()->VehicleFactory50.canonical(plot));
        }
        h.succeed();
    }

    @GameTest(template="townhall_state_empty",batch="vehicle52_legacy",timeoutTicks=200)
    public static void all_15_legacy_city_a_factory_identity_compounds_remain_exact(GameTestHelper h)throws Exception {
        controlled(h);String[] plots={"plot_1","plot_3","plot_4"};
        int[][] counters={{-806,82,-780},{-806,82,-740},{-694,82,-728}};
        int[][] anchors={{-795,81,-774,0},{-795,81,-734,0},{-705,81,-734,180}};int cases=0;
        for(int i=0;i<plots.length;i++)for(int tier=1;tier<=5;tier++){
            CompoundTag expected=new CompoundTag();
            expected.putInt("schema",1);expected.putString("revision","city_a_vehicle_factory50_v1");
            expected.putString("world",IDENTITY_ROOT.toString());expected.putString("worldId","authorized_historical_r7");
            expected.putString("levelName","frozen-level-name");expected.putLong("seed",1042026L);expected.putString("dimension",DIMENSION);
            expected.putString("city","a");expected.putString("plot",plots[i]);expected.putUUID("owner",OWNER);
            expected.putString("sourceToken","frozen-source-token");expected.putString("sourceSHA256",FACTORY_SHA);expected.putInt("level",tier);
            expected.putLong("counter",new BlockPos(counters[i][0],counters[i][1],counters[i][2]).asLong());expected.putIntArray("anchor",anchors[i]);
            CompoundTag actual=identity(site(MapDefinitionManager.territory("a"),plots[i]),tier);
            require(expected.size()==15&&actual.equals(expected),"Historical A identity changed value, field or NBT type");
            require(!actual.contains("siteMapSHA256"),"Historical A identity received a new hash field");cases++;
        }
        require(cases==15,"Legacy A slot/tier identity coverage incomplete");
        CatanCraft.LOGGER.info("VEHICLE52_LEGACY_A identities={} exactKeys=15",cases);h.succeed();
    }

    @GameTest(template="townhall_state_empty",batch="vehicle52_match",timeoutTicks=200)
    public static void all_48_exact_facilities_refuse_cross_city_plot_world_owner_and_level_changes(GameTestHelper h)throws Exception {
        controlled(h);int sites=0,refusals=0;Set<CompoundTag> identities=new HashSet<>();
        for(var definition:definitions())for(var site:VehicleSites52.sites(definition)){
            CompoundTag original=identity(site,3),before=original.copy();var same=binding(site,3);
            require(identities.add(original.copy()),"Different sites share a vehicle identity");
            if(!site.city().equals("a"))require(original.size()==16&&original.getString("siteMapSHA256").equals(ShopSites51.MAP_SHA256)
                    &&original.getString("revision").equals(VehicleSites52.REVISION),"Expanded facility has no pinned map/revision");
            require(VehicleFactory50.match(same,original)==same,"Unchanged exact facility refused");
            refuse(()->VehicleFactory50.match(binding(site,5),original));refusals++;
            refuse(()->VehicleFactory50.match(binding(site,1),original));refusals++;
            for(String field:List.of("revision","world","worldId","levelName","dimension","city","plot","sourceToken","sourceSHA256")){
                CompoundTag changed=original.copy();changed.putString(field,"foreign");
                refuse(()->VehicleFactory50.match(same,changed));refusals++;
            }
            CompoundTag owner=original.copy();owner.putUUID("owner",UUID.randomUUID());refuse(()->VehicleFactory50.match(same,owner));refusals++;
            CompoundTag malformed=original.copy();malformed.putString("counter","wrong-nbt-type");refuse(()->VehicleFactory50.match(same,malformed));refusals++;
            CompoundTag missing=original.copy();missing.remove("counter");refuse(()->VehicleFactory50.match(same,missing));refusals++;
            CompoundTag extra=original.copy();extra.putBoolean("unreviewed",true);refuse(()->VehicleFactory50.match(same,extra));refusals++;
            CompoundTag anchor=original.copy();int[] xyz=anchor.getIntArray("anchor");xyz[0]++;anchor.putIntArray("anchor",xyz);refuse(()->VehicleFactory50.match(same,anchor));refusals++;
            require(original.equals(before),"Identity comparison changed original recovery data");sites++;
        }
        var a=site(MapDefinitionManager.territory("a"),"plot_1");
        refuse(()->VehicleFactory50.match(binding(site(MapDefinitionManager.territory("d"),"plot_1"),3),identity(a,3)));
        refuse(()->VehicleFactory50.match(binding(site(MapDefinitionManager.territory("a"),"plot_3"),3),identity(a,3)));
        require(sites==48&&identities.size()==48&&refusals==768,"Exact identity coverage incomplete");
        CatanCraft.LOGGER.info("VEHICLE52_MATCH sites={} identityRefusals={} explicitCrossSiteRefusals=2",sites,refusals);h.succeed();
    }

    @GameTest(template="townhall_state_empty",batch="vehicle52_native_tiers",timeoutTicks=1400)
    public static void all_240_native_factory_counter_and_tier_inspections_are_read_only(GameTestHelper h)throws Exception {
        controlled(h);fixture(h,f->{int cases=0;
            for(var definition:definitions())for(var site:VehicleSites52.sites(definition)){
                f.factory(site);var city=f.city(site.city());
                for(int tier=1;tier<=5;tier++){
                    city.buildings().get(0).setLevel(tier);var ledger=f.data.save(new CompoundTag());var blocks=f.snapshot();
                    var inspected=VehicleFactory50.construction(f.level,f.data,f.nation,site);
                    require(inspected.level()==tier&&inspected.token().equals(SchematicAssetRegistry.asset("vehicle_factory").placementToken()),"Inspected factory level/token changed");
                    f.unchanged(ledger,blocks);cases++;
                }
            }
            require(cases==240,"All-city/slot/tier native inspections incomplete");
            CatanCraft.LOGGER.info("VEHICLE52_FACTORIES nativeContextInspections={} cities=16 slots=3 tiers=5 observableMutations=0 authenticatedClient=false",cases);
        });
    }

    @GameTest(template="townhall_state_empty",batch="vehicle52_native_refusals",timeoutTicks=1400)
    public static void all_48_native_factories_refuse_wrong_owner_incomplete_source_and_blocked_context(GameTestHelper h)throws Exception {
        controlled(h);fixture(h,f->{int cases=0;
            for(var definition:definitions())for(var site:VehicleSites52.sites(definition)){
                f.factory(site);var city=f.city(site.city());String key="territory:"+site.city()+":"+site.plot();
                city.setOwnerNationId(UUID.randomUUID());f.refuses(site);cases++;city.setOwnerNationId(f.nation.id());
                var absent=city.buildings().remove(0);f.refuses(site);cases++;city.buildings().add(absent);
                f.data.setPlacedMapAsset(key,"unfinished-construction");f.refuses(site);cases++;f.data.setPlacedMapAsset(key,SchematicAssetRegistry.asset("vehicle_factory").placementToken());
                city.buildings().add(new BuildingInstance(BuildingType.VEHICLE_FACTORY,1,0,"plot_2"));f.refuses(site);cases++;city.buildings().remove(1);
                city.buildings().add(new BuildingInstance(BuildingType.STEEL_MILL,1,0,site.plot()));f.refuses(site);cases++;city.buildings().remove(1);
                city.buildings().get(0).setPlotId("plot_2");f.refuses(site);cases++;city.buildings().get(0).setPlotId(site.plot());
                f.put(site.counter(),Blocks.STONE.defaultBlockState());f.refuses(site);cases++;f.context(site);
                f.put(site.counter(),Blocks.CHEST.defaultBlockState());f.refuses(site);cases++;f.context(site);
                f.put(ShopFacility40.local(site.anchor(),18,3,10),Blocks.STONE.defaultBlockState());f.refuses(site);cases++;f.context(site);
                f.put(ShopFacility40.local(site.anchor(),29,1,15),Blocks.AIR.defaultBlockState());f.refuses(site);cases++;f.context(site);
            }
            require(cases==480,"All-city native refusal coverage incomplete");
            CatanCraft.LOGGER.info("VEHICLE52_REFUSALS nativeContextRefusals={} cities=16 slots=3 observableMutations=0",cases);
        });
    }

    @GameTest(template="townhall_state_empty",batch="vehicle52_actor",timeoutTicks=200)
    public static void all_48_recognized_vehicle_counters_still_refuse_fake_players(GameTestHelper h)throws Exception {
        controlled(h);var actor=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"Vehicle52Refusal"));actor.setGameMode(GameType.ADVENTURE);int cases=0;
        for(var definition:definitions())for(var site:VehicleSites52.sites(definition)){
            actor.setPos(site.counter().getX()+.5,site.counter().getY(),site.counter().getZ()+.5);
            require(VehicleFactory50.recognizes(actor,site.counter()),"Reviewed vehicle counter was not recognized");
            require(!ShopFacility40.connected(actor),"Fake actor became connected");refuse(()->VehicleFactory50.open(actor,site.counter()));cases++;
        }
        require(cases==48&&actor.getInventory().isEmpty(),"Fake counter refusal coverage incomplete or inventory changed");h.succeed();
    }

    private static CompoundTag identity(VehicleSites52.Site site,int tier){return VehicleFactory50.identity(site,new VehicleFactory50.Construction("frozen-source-token",tier),OWNER,IDENTITY_ROOT,"authorized_historical_r7","frozen-level-name",1042026L);}
    private static VehicleFactory50.Binding binding(VehicleSites52.Site site,int tier){return new VehicleFactory50.Binding(identity(site,tier),"Synthetic vehicle binding",tier,site.counter(),site.anchor(),OWNER,IDENTITY_ROOT);}
    private static List<TerritoryDefinition> definitions(){var definitions=MapDefinitionManager.territories().stream().sorted(Comparator.comparing(TerritoryDefinition::id)).toList();require(definitions.size()==16,"Configured map must contain all 16 cities");return definitions;}
    private static VehicleSites52.Site site(TerritoryDefinition definition,String plot)throws IOException{return VehicleSites52.sites(definition).stream().filter(s->s.plot().equals(plot)).findFirst().orElseThrow(()->new IOException("Missing vehicle test site"));}
    private static List<int[]> context(){List<int[]> cells=new ArrayList<>();cells.add(new int[]{18,3,9});for(int x=18;x<=29;x++)for(int y=1;y<=3;y++)cells.add(new int[]{x,y,15});for(int z=10;z<15;z++)for(int y=1;y<=3;y++)cells.add(new int[]{18,y,z});return cells;}
    private static void require(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}
    @FunctionalInterface private interface Attempt{void run()throws Exception;}
    @FunctionalInterface private interface Work{void run(Fixture fixture)throws Exception;}
    private static void refuse(Attempt attempt)throws Exception{try{attempt.run();}catch(IOException expected){return;}throw new AssertionError("Expected guarded vehicle refusal");}
    private static void controlled(GameTestHelper h)throws IOException{
        var server=h.getLevel().getServer();Path root=server.getWorldPath(LevelResource.ROOT).toRealPath();
        if(!(server instanceof GameTestServer)||!server.isSameThread()||!server.getPlayerList().getPlayers().isEmpty()
                ||!root.getFileName().toString().equals("world")||!root.getParent().getFileName().toString().equals("controlled-catancraft_vehicle52-run")
                ||!Files.readString(root.getParent().resolve(".catancraft-controlled-fixture-v1")).equals("CatanCraft synthetic flat fixture v1\n"))
            throw new IOException("Vehicle52 tests require their exact marked disposable native world");
    }
    private static void fixture(GameTestHelper h,Work work)throws Exception{Fixture f=new Fixture(h.getLevel());try{f.lease();await(h,f,work,0);}catch(Exception|AssertionError failure){f.close();throw failure;}}
    private static void await(GameTestHelper h,Fixture f,Work work,int ticks){
        try{
            for(var lease:f.leases)lease.keepAlive();
            if(f.leases.stream().anyMatch(l->!l.ready())){
                require(ticks<900,"Sparse native fixture entity residency timed out");
                java.util.concurrent.locks.LockSupport.parkNanos(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(25));
                h.runAfterDelay(5,()->await(h,f,work,ticks+5));return;
            }
            try{work.run(f);}finally{f.close();}h.succeed();
        }catch(Exception|AssertionError failure){f.close();h.fail(failure.toString());}
    }
    private static final class Fixture {
        final ServerLevel level;final CatanSavedData data=new CatanSavedData();
        final NationData nation=data.createNation("Synthetic Vehicle52 construction inspection",UUID.randomUUID());
        final Map<BlockPos,BlockState> originals=new LinkedHashMap<>();final List<SectorChunkLease> leases=new ArrayList<>();boolean closed;
        Fixture(ServerLevel level){this.level=level;data.syncMapDefinitions();for(var city:data.territories())city.setOwnerNationId(nation.id());}
        void lease()throws IOException{for(var definition:definitions())for(var site:VehicleSites52.sites(definition)){
            BlockPos a=ShopFacility40.local(site.anchor(),18,1,9),b=ShopFacility40.local(site.anchor(),29,3,15);
            leases.add(SectorChunkLease.acquire(level,new AABB(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ()),Math.max(a.getX(),b.getX())+1,Math.max(a.getY(),b.getY())+1,Math.max(a.getZ(),b.getZ())+1)));
        }}
        TerritoryData city(String id){return Objects.requireNonNull(data.territory(id));}
        void put(BlockPos pos,BlockState state){
            require(level.hasChunkAt(pos),"Fixture chunk is unavailable");
            if(!originals.containsKey(pos)){require(level.getBlockEntity(pos)==null,"Sparse fixture would overwrite unrelated block entity");originals.put(pos.immutable(),level.getBlockState(pos));}
            level.setBlock(pos,state,18);require(level.getBlockState(pos).equals(state),"Sparse fixture write did not take effect");
        }
        void context(VehicleSites52.Site site)throws IOException{var source=BasicConstructionAssets.load("vehicle_factory");for(int[] xyz:VehicleSites52Tests.context())put(ShopFacility40.local(site.anchor(),xyz[0],xyz[1],xyz[2]),source.at(xyz[0],xyz[1],xyz[2]).rotate(ProducerExteriorR2.rotation(site.anchor())));}
        void factory(VehicleSites52.Site site)throws IOException{context(site);var city=city(site.city());city.setCityLevel(5);city.buildings().clear();city.buildings().add(new BuildingInstance(BuildingType.VEHICLE_FACTORY,1,0,site.plot()));data.setPlacedMapAsset("territory:"+site.city()+":"+site.plot(),SchematicAssetRegistry.asset("vehicle_factory").placementToken());}
        Map<BlockPos,CompoundTag> snapshot(){Map<BlockPos,CompoundTag> result=new LinkedHashMap<>();for(var pos:originals.keySet()){CompoundTag tag=new CompoundTag();tag.putString("state",level.getBlockState(pos).toString());var entity=level.getBlockEntity(pos);if(entity!=null)tag.put("blockEntity",entity.saveWithFullMetadata());result.put(pos,tag);}return result;}
        void unchanged(CompoundTag ledger,Map<BlockPos,CompoundTag> blocks){require(data.save(new CompoundTag()).equals(ledger),"Construction inspection changed ledger");require(snapshot().equals(blocks),"Construction inspection changed blocks/entities");}
        void refuses(VehicleSites52.Site site)throws Exception{var ledger=data.save(new CompoundTag());var blocks=snapshot();refuse(()->VehicleFactory50.construction(level,data,nation,site));unchanged(ledger,blocks);}
        void close(){if(closed)return;closed=true;try{for(var entry:originals.entrySet())level.setBlock(entry.getKey(),entry.getValue(),18);}finally{for(var lease:leases)lease.close();}}
    }
}
