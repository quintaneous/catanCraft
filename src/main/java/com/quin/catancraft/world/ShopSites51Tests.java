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
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Real native block reads in an explicitly synthetic disposable world, using a detached
 * ledger for construction inspection. No authenticated player, purchase or client pass is
 * inferred. Pinned sparse contexts are fixtures, not an authored all-city terrain survey. */
@GameTestHolder("catancraft_shop51") @PrefixGameTestTemplate(false)
public final class ShopSites51Tests {
    private static final Gson GSON = new Gson();
    private static final String DIMENSION = "minecraft:overworld";
    private static final String MAP_SHA = "e50d9753c807b5fe6f8fdee9765ae09640d951cc0ba2d59f1612f0923485c986";
    private static final int[][] CONTEXT = {{19,3,18}, {19,2,18}, {19,2,17}, {19,3,17}, {19,1,17}};
    private static final UUID OWNER = UUID.fromString("3e227cab-3776-4fcf-8ab9-12117dcf91fd");
    private static final Path IDENTITY_ROOT = Path.of("/synthetic-shop51-identity-only");
    private ShopSites51Tests() {}

    @GameTest(template="townhall_state_empty", batch="shop51_sites", timeoutTicks=1200)
    public static void all_16_managers_and_48_factories_use_actual_placement_transforms(GameTestHelper h) throws Exception {
        controlled(h);
        int managers = 0, factories = 0, contextChecks = 0;
        Set<BlockPos> distinct = new HashSet<>();
        var volume = BasicConstructionAssets.load("weapons_factory");
        for (var definition : definitions()) {
            int index = definition.id().charAt(0) - 'a';
            int cx = -750 + 500 * (index % 4), cz = -750 + 500 * (index / 4), sign = index < 8 ? 1 : -1;
            ShopSites51.canonical(definition);
            List<ShopSites51.Site> sites = ShopSites51.sites(definition);
            require(sites.size() == 4, "A city lost its manager or ordinary factory sites");
            for (var site : sites) {
                require(distinct.add(site.counter()), "Two sites share a counter coordinate");
                var resolved = ShopSites51.resolve(MapDefinitionManager.territories(), DIMENSION, site.counter());
                require(resolved.city().equals(definition.id()) && resolved.plot().equals(site.plot()), "Resolver selected another city or slot");
                require(ShopSites51.recognizes(DIMENSION, site.counter(), false), "Reviewed local counter is not recognized");
                require(ShopSites51.recognizes(DIMENSION, site.counter(), true) == site.starter(), "Starter-only recognition includes a factory");
                if (site.starter()) {
                    managers++;
                    require(site.counter().equals(new BlockPos(cx,84,cz-sign*23)), "Management coordinate differs from the independent city layout");
                } else {
                    factories++;
                    int expectedX = cx + sign * (site.plot().equals("plot_4") ? 55 : -55);
                    int expectedZ = cz + sign * (site.plot().equals("plot_1") ? -21 : site.plot().equals("plot_3") ? 19 : 13);
                    require(site.counter().equals(new BlockPos(expectedX,82,expectedZ)), "Factory counter differs from independently recorded layout");
                    int rotation = (index < 8 ? 0 : 180) ^ (site.plot().equals("plot_4") ? 180 : 0);
                    require(site.anchor().rotation() == rotation, "Southern/plot-4 extra flip was lost");
                    List<SchematicPlacementService.PlanCell> placement = BasicConstructionAssets.plan("weapons_factory", site.anchor());
                    for (int[] xyz : CONTEXT) {
                        var cell = placement.get((xyz[1]*volume.length()+xyz[2])*volume.width()+xyz[0]);
                        require(cell.position().equals(ShopFacility40.local(site.anchor(),xyz[0],xyz[1],xyz[2])), "Counter transform differs from physical construction plan");
                        contextChecks++;
                    }
                    require(placement.get((3*volume.length()+18)*volume.width()+19).position().equals(site.counter()), "Counter is not the actual placed crafting-table cell");
                }
            }
        }
        require(managers == 16 && factories == 48 && contextChecks == 240 && distinct.size() == 64, "All-city coverage was incomplete");
        CatanCraft.LOGGER.info("SHOP51_TRANSFORMS managers={} factories={} pinnedContextPositions={} noWorldWrites=true", managers, factories, contextChecks);
        h.succeed();
    }

    @GameTest(template="townhall_state_empty", batch="shop51_resolution", timeoutTicks=200)
    public static void ambiguous_absent_wrong_dimension_and_changed_map_sites_refuse(GameTestHelper h) throws Exception {
        controlled(h);
        var d = MapDefinitionManager.territory("d");
        var site = site(d, "town_hall");
        refuse(() -> ShopSites51.resolve(List.of(d,d), DIMENSION, site.counter()));
        refuse(() -> ShopSites51.resolve(List.of(), DIMENSION, site.counter()));
        refuse(() -> ShopSites51.resolve(List.of(d), "minecraft:the_nether", site.counter()));
        refuse(() -> ShopSites51.resolve(List.of(d), DIMENSION, new BlockPos(750,81,-750)));
        require(!ShopSites51.recognizes("minecraft:the_nether", site.counter(), false), "Other dimension recognized an overworld counter");
        for (String city : List.of("d", "i")) {
            TerritoryDefinition original = MapDefinitionManager.territory(city);
            JsonObject changed = GSON.toJsonTree(original).getAsJsonObject();
            changed.getAsJsonObject("managementAnchor").addProperty("x", original.managementAnchor().x()+1);
            TerritoryDefinition moved = GSON.fromJson(changed, TerritoryDefinition.class);
            refuse(() -> ShopSites51.canonical(moved));
            refuse(() -> ShopSites51.resolve(List.of(moved), DIMENSION, site(moved,"town_hall").counter()));
            changed = GSON.toJsonTree(original).getAsJsonObject();
            changed.getAsJsonArray("buildingPlots").get(0).getAsJsonObject().addProperty("x",0);
            TerritoryDefinition movedPlot = GSON.fromJson(changed, TerritoryDefinition.class);
            refuse(() -> ShopSites51.canonical(movedPlot));
            changed = GSON.toJsonTree(original).getAsJsonObject();
            changed.getAsJsonObject("cityCenter").addProperty("rotation",90);
            TerritoryDefinition unsupported = GSON.fromJson(changed, TerritoryDefinition.class);
            refuse(() -> ShopSites51.sites(unsupported));
            require(ShopSites51.recognizes(DIMENSION,site(original,"town_hall").counter(),true), "Rejected clone altered production recognition");
        }
        h.succeed();
    }

    @GameTest(template="townhall_state_empty", batch="shop51_legacy", timeoutTicks=200)
    public static void legacy_city_a_identity_retains_exact_16_key_compounds(GameTestHelper h) throws Exception {
        controlled(h);
        String[] plots = {"town_hall", "plot_1", "plot_3", "plot_4"};
        int[][] counters = {{-750,84,-773}, {-805,82,-771}, {-805,82,-731}, {-695,82,-737}};
        int[][] anchors = {{-750,84,-773,0}, {-795,81,-774,0}, {-795,81,-734,0}, {-705,81,-734,180}};
        int cases = 0;
        for (int i=0;i<plots.length;i++) {
            var site = site(MapDefinitionManager.territory("a"),plots[i]);
            for (int tier = i==0 ? 0 : 1; tier <= (i==0 ? 0 : 5); tier++) {
                CompoundTag expected = new CompoundTag();
                expected.putInt("schema",1); expected.putString("revision","city_a_weapon_counter40_v1");
                expected.putString("world",IDENTITY_ROOT.toString()); expected.putString("worldId","authorized_historical_r7");
                expected.putString("levelName","frozen-level-name"); expected.putString("dimension","minecraft:overworld");
                expected.putLong("seed",1042026L); expected.putString("city","a"); expected.putUUID("owner",OWNER);
                expected.putString("type",i==0 ? "starter" : "weapons"); expected.putString("plot",plots[i]);
                expected.putString("sourceToken","frozen-source-token"); expected.putLong("counter",new BlockPos(counters[i][0],counters[i][1],counters[i][2]).asLong());
                expected.putIntArray("anchor",anchors[i]); expected.putInt("quotedLevel",tier); expected.putInt("requiredLevel",0);
                CompoundTag actual = identity(site,tier);
                require(expected.size()==16 && actual.equals(expected), "A historical facility identity changed field, value or NBT type");
                require(!actual.contains("siteMapSHA256"), "New field was injected into historical A identity");
                cases++;
            }
        }
        require(cases==16, "Legacy tier cases incomplete");
        CatanCraft.LOGGER.info("SHOP51_LEGACY_A identities={} exactKeys=16 frozenRevision=true",cases);
        h.succeed();
    }

    @GameTest(template="townhall_state_empty", batch="shop51_origin", timeoutTicks=200)
    public static void all_city_identities_and_recovery_origins_stay_separate(GameTestHelper h) throws Exception {
        controlled(h);
        Set<CompoundTag> identities = new HashSet<>();
        for (var definition : definitions()) for (var site : ShopSites51.sites(definition)) {
            CompoundTag original = identity(site,site.starter()?0:3), before=original.copy();
            require(identities.add(original.copy()), "Different cities or sites share an identity");
            if (!definition.id().equals("a")) {
                require(original.size()==17 && original.getString("siteMapSHA256").equals(MAP_SHA)
                        && original.getString("revision").equals("all_city_weapon_counter51_v1"), "Expanded site lacks its frozen map/version contract");
            }
            ShopFacility40.requireSameSite(original.copy(),original);
            CompoundTag higher = original.copy(); higher.putInt("quotedLevel",5);
            ShopFacility40.requireSameSite(higher,original); // Tier checks remain the separate physical guard.
            require(original.equals(before), "Origin comparison mutated the frozen identity");
        }
        require(identities.size()==64, "Expanded identity coverage incomplete");
        CompoundTag a = identity(site(MapDefinitionManager.territory("a"),"plot_1"),3);
        CompoundTag d = identity(site(MapDefinitionManager.territory("d"),"plot_1"),3);
        refuse(() -> ShopFacility40.requireSameSite(d,a));
        refuse(() -> ShopFacility40.requireSameSite(identity(site(MapDefinitionManager.territory("a"),"plot_3"),3),a));
        refuse(() -> ShopFacility40.requireSameSite(identity(site(MapDefinitionManager.territory("a"),"town_hall"),0),a));
        for (String key : List.of("world","worldId","dimension","city","type","plot","counter","anchor")) {
            CompoundTag missing=a.copy(); missing.remove(key);
            refuse(() -> ShopFacility40.requireSameSite(missing,a));
            CompoundTag changed=a.copy(); changed.putString(key,"foreign");
            refuse(() -> ShopFacility40.requireSameSite(changed,a));
        }
        h.succeed();
    }

    @GameTest(template="townhall_state_empty", batch="shop51_match", timeoutTicks=200)
    public static void exact_quotes_and_frozen_paid_requests_keep_distinct_level_policies(GameTestHelper h) throws Exception {
        controlled(h); int cases=0;
        for(var definition:definitions()) for(var site:ShopSites51.sites(definition)) {
            if(site.starter())continue;
            CompoundTag original=identity(site,3);original.putInt("requiredLevel",2);
            CompoundTag before=original.copy();
            var same=new ShopFacility40.Binding(identity(site,3),"Synthetic binding",3,site.counter());
            var higher=new ShopFacility40.Binding(identity(site,5),"Synthetic binding",5,site.counter());
            var lower=new ShopFacility40.Binding(identity(site,1),"Synthetic binding",1,site.counter());
            require(ShopFacility40.match(same,original,true)==same,"Unchanged exact quote refused");
            require(ShopFacility40.match(higher,original,false)==higher,"Retained paid request refused allowed higher factory tier");
            refuse(()->ShopFacility40.match(higher,original,true));
            refuse(()->ShopFacility40.match(lower,original,false));
            for(String field:List.of("city","sourceToken","revision","worldId")) {
                CompoundTag changed=original.copy();changed.putString(field,"foreign");
                refuse(()->ShopFacility40.match(higher,changed,false));
            }
            CompoundTag ownerChanged=original.copy();ownerChanged.putUUID("owner",UUID.randomUUID());
            refuse(()->ShopFacility40.match(higher,ownerChanged,false));
            CompoundTag unearned=original.copy();unearned.putInt("requiredLevel",4);
            refuse(()->ShopFacility40.match(higher,unearned,false));
            CompoundTag malformed=original.copy();malformed.putString("counter","wrong-nbt-type");
            refuse(()->ShopFacility40.match(higher,malformed,false));
            require(original.equals(before),"Matching changed the original frozen facility or required tier");cases++;
        }
        require(cases==48,"All-city factory recovery/quote policy cases incomplete");
        CatanCraft.LOGGER.info("SHOP51_MATCH factorySites={} oldAAndExpandedIdentities=true exactQuoteAndPaidRecoveryPoliciesRetained=true",cases);
        h.succeed();
    }

    @GameTest(template="townhall_state_empty", batch="shop51_starters", timeoutTicks=1200)
    public static void every_city_th1_to_th5_native_management_context_is_read_only(GameTestHelper h) throws Exception {
        controlled(h); Fixture f=new Fixture(h.getLevel()); int cases=0;
        try {
            for (var definition : definitions()) {
                var site=site(definition,"town_hall"); f.put(site.counter(),Blocks.LECTERN.defaultBlockState());
                TerritoryData city=f.city(definition.id());
                for (int tier=1;tier<=5;tier++) {
                    city.setCityLevel(tier);
                    for (int revision=1;revision<=(tier>=4?2:1);revision++) {
                        String token=tier<=3 ? SchematicAssetRegistry.asset("th"+tier).placementToken() : TownHallRevisions.token(tier,revision);
                        f.data.setPlacedMapAsset("territory:"+definition.id()+":town_hall",token);
                        CompoundTag before=f.data.save(new CompoundTag()); Map<BlockPos,CompoundTag> blocks=f.snapshot();
                        var inspected=ShopFacility40.construction(f.level,f.data,f.nation,site);
                        require(inspected.level()==0 && inspected.token().equals(token), "Starter lost completed Town Hall tier/revision");
                        f.unchanged(before,blocks); cases++;
                    }
                }
            }
        } finally {f.restore();}
        require(cases==112,"All-city TH1-5 and both prestige revision cases incomplete");
        CatanCraft.LOGGER.info("SHOP51_STARTERS nativeContextInspections={} cities=16 observableLedgerAndBlockChanges=0 authenticatedClient=false",cases);
        h.succeed();
    }

    @GameTest(template="townhall_state_empty", batch="shop51_factories", timeoutTicks=1200)
    public static void every_city_all_three_factory_slots_and_five_tiers_inspect_without_mutation(GameTestHelper h) throws Exception {
        controlled(h); Fixture f=new Fixture(h.getLevel()); int cases=0;
        try {
            for (var definition : definitions()) for (var site : ShopSites51.sites(definition)) {
                if(site.starter())continue;
                f.factory(site); TerritoryData city=f.city(definition.id());
                for(int tier=1;tier<=5;tier++) {
                    city.buildings().get(0).setLevel(tier);
                    CompoundTag before=f.data.save(new CompoundTag()); Map<BlockPos,CompoundTag> blocks=f.snapshot();
                    var inspected=ShopFacility40.construction(f.level,f.data,f.nation,site);
                    require(inspected.level()==tier && inspected.token().equals(SchematicAssetRegistry.asset("weapons_factory").placementToken()),"Factory level/token changed during inspection");
                    f.unchanged(before,blocks); cases++;
                }
            }
        } finally {f.restore();}
        require(cases==240,"All-city factory tier/slot cases incomplete");
        CatanCraft.LOGGER.info("SHOP51_FACTORIES nativeContextInspections={} cities=16 slotsPerCity=3 tiers=5 observableLedgerAndBlockChanges=0 authenticatedClient=false",cases);
        h.succeed();
    }

    @GameTest(template="townhall_state_empty", batch="shop51_refusals", timeoutTicks=1200)
    public static void every_city_wrong_owner_source_plot_and_changed_native_context_refuse(GameTestHelper h) throws Exception {
        controlled(h); Fixture f=new Fixture(h.getLevel()); int cases=0;
        try {
            for(var definition:definitions()) {
                var starter=site(definition,"town_hall"); TerritoryData city=f.city(definition.id());
                f.put(starter.counter(),Blocks.LECTERN.defaultBlockState());city.setCityLevel(1);
                String managerKey="territory:"+definition.id()+":town_hall";
                f.data.setPlacedMapAsset(managerKey,SchematicAssetRegistry.asset("th1").placementToken());
                city.setOwnerNationId(UUID.randomUUID()); f.refuses(starter);cases++;city.setOwnerNationId(f.nation.id());
                f.data.setPlacedMapAsset(managerKey,"wrong-token");f.refuses(starter);cases++;
                f.data.setPlacedMapAsset(managerKey,SchematicAssetRegistry.asset("th1").placementToken());
                f.put(starter.counter(),Blocks.STONE.defaultBlockState());f.refuses(starter);cases++;
                for(var site:ShopSites51.sites(definition)) {
                    if(site.starter())continue;
                    f.factory(site);
                    String key="territory:"+definition.id()+":"+site.plot();
                    f.data.setPlacedMapAsset(key,"unfinished-construction");f.refuses(site);cases++;
                    f.data.setPlacedMapAsset(key,SchematicAssetRegistry.asset("weapons_factory").placementToken());
                    city.buildings().add(new BuildingInstance(BuildingType.STEEL_MILL,1,0,site.plot()));f.refuses(site);cases++;city.buildings().remove(1);
                    city.buildings().add(new BuildingInstance(BuildingType.WEAPONS_FACTORY,1,0,"plot_2"));f.refuses(site);cases++;city.buildings().remove(1);
                    city.buildings().get(0).setPlotId("plot_2");f.refuses(site);cases++;city.buildings().get(0).setPlotId(site.plot());
                    f.put(site.counter(),Blocks.STONE.defaultBlockState());f.refuses(site);cases++;f.context(site);
                    BlockPos head=ShopFacility40.local(site.anchor(),19,3,17);
                    f.put(head,Blocks.STONE.defaultBlockState());f.refuses(site);cases++;f.context(site);
                    f.put(site.counter(),Blocks.CHEST.defaultBlockState());f.refuses(site);cases++;f.context(site);
                }
            }
        } finally {f.restore();}
        require(cases==384,"Expected 16*(3 starter + 3*7 factory) refusal cases");
        CatanCraft.LOGGER.info("SHOP51_REFUSALS nativeContextRefusals={} cities=16 observableLedgerAndBlockChanges=0",cases);
        h.succeed();
    }

    @GameTest(template="townhall_state_empty", batch="shop51_actor", timeoutTicks=200)
    public static void all_city_recognition_never_authorizes_a_detached_or_fake_player(GameTestHelper h) throws Exception {
        controlled(h); FakePlayer actor=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"Shop51Refusal"));
        actor.setGameMode(GameType.ADVENTURE);
        for(var definition:definitions()) for(var site:ShopSites51.sites(definition)) {
            actor.setPos(site.counter().getX()+0.5,site.counter().getY(),site.counter().getZ()+0.5);
            require(ShopFacility40.recognizes(actor,site.counter()),"Valid site not recognized for refusal test");
            require(!ShopFacility40.connected(actor),"FakePlayer became authenticated");
            refuse(() -> ShopFacility40.open(actor,site.counter()));
        }
        require(actor.getInventory().isEmpty(),"Refused actor received an item");
        h.succeed();
    }

    private static CompoundTag identity(ShopSites51.Site site,int tier) {
        return ShopFacility40.identity(site,new ShopFacility40.Construction("frozen-source-token",tier),OWNER,
                IDENTITY_ROOT,"authorized_historical_r7","frozen-level-name",1042026L);
    }
    private static List<TerritoryDefinition> definitions() {
        List<TerritoryDefinition> definitions=MapDefinitionManager.territories().stream().sorted(Comparator.comparing(TerritoryDefinition::id)).toList();
        require(definitions.size()==16,"Native configured map must contain all 16 cities");return definitions;
    }
    private static ShopSites51.Site site(TerritoryDefinition definition,String plot)throws IOException {
        return ShopSites51.sites(definition).stream().filter(s->s.plot().equals(plot)).findFirst().orElseThrow(()->new IOException("Missing test site"));
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    @FunctionalInterface private interface Attempt {void run()throws Exception;}
    private static void refuse(Attempt attempt)throws Exception {
        try{attempt.run();}catch(IOException expected){return;}throw new AssertionError("Expected guarded shop refusal");
    }
    private static void controlled(GameTestHelper h)throws IOException {
        var server=h.getLevel().getServer();Path root=server.getWorldPath(LevelResource.ROOT).toRealPath();
        if(!(server instanceof GameTestServer)||!server.isSameThread()||!server.getPlayerList().getPlayers().isEmpty()
                ||!root.getFileName().toString().equals("world")||!root.getParent().getFileName().toString().equals("controlled-catancraft_shop51-run")
                ||!Files.readString(root.getParent().resolve(".catancraft-controlled-fixture-v1")).equals("CatanCraft synthetic flat fixture v1\n"))
            throw new IOException("Shop51 tests require their exact marked disposable native world");
    }
    private static final class Fixture {
        final ServerLevel level;
        final CatanSavedData data=new CatanSavedData();
        final NationData nation=data.createNation("Synthetic Shop51 construction inspection",UUID.randomUUID());
        final Map<BlockPos,BlockState> originals=new LinkedHashMap<>();
        Fixture(ServerLevel level){this.level=level;data.syncMapDefinitions();for(var city:data.territories())city.setOwnerNationId(nation.id());}
        TerritoryData city(String id){return Objects.requireNonNull(data.territory(id));}
        void put(BlockPos pos,BlockState state){
            level.getChunkAt(pos);
            if(!originals.containsKey(pos)){
                require(level.getBlockEntity(pos)==null,"Synthetic fixture would overwrite an unrelated block entity");
                originals.put(pos.immutable(),level.getBlockState(pos));
            }
            level.setBlock(pos,state,18);
            require(level.getBlockState(pos).equals(state),"Synthetic context write did not take effect");
        }
        void context(ShopSites51.Site site)throws IOException{
            var source=BasicConstructionAssets.load("weapons_factory");
            Rotation rotation=site.anchor().rotation()==0?Rotation.NONE:Rotation.CLOCKWISE_180;
            for(int[] xyz:CONTEXT)put(ShopFacility40.local(site.anchor(),xyz[0],xyz[1],xyz[2]),source.at(xyz[0],xyz[1],xyz[2]).rotate(rotation));
        }
        void factory(ShopSites51.Site site)throws IOException{
            context(site);var city=city(site.city());city.setCityLevel(5);city.buildings().clear();
            city.buildings().add(new BuildingInstance(BuildingType.WEAPONS_FACTORY,1,0,site.plot()));
            data.setPlacedMapAsset("territory:"+site.city()+":"+site.plot(),SchematicAssetRegistry.asset("weapons_factory").placementToken());
        }
        Map<BlockPos,CompoundTag> snapshot(){
            Map<BlockPos,CompoundTag> result=new LinkedHashMap<>();
            for(var pos:originals.keySet()){
                CompoundTag cell=new CompoundTag();cell.putString("state",level.getBlockState(pos).toString());
                var entity=level.getBlockEntity(pos);if(entity!=null)cell.put("blockEntity",entity.saveWithFullMetadata());
                result.put(pos,cell);
            }return result;
        }
        void unchanged(CompoundTag ledger,Map<BlockPos,CompoundTag> blocks){
            require(data.save(new CompoundTag()).equals(ledger),"Read-only construction inspection changed ledger data");
            require(snapshot().equals(blocks),"Read-only construction inspection changed blocks or block entities");
        }
        void refuses(ShopSites51.Site site)throws Exception{
            CompoundTag ledger=data.save(new CompoundTag());var blocks=snapshot();
            refuse(()->ShopFacility40.construction(level,data,nation,site));unchanged(ledger,blocks);
        }
        void restore(){for(var entry:originals.entrySet())level.setBlock(entry.getKey(),entry.getValue(),18);}
    }
}
