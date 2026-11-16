package com.quin.catancraft.world;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.*;
import com.quin.catancraft.economy.*;
import com.quin.catancraft.map.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Actual authored-world progression through the registered command/service paths.
 * This is an accelerated economic-order regression, not real-time pacing or positive
 * connected-client acceptance. No balances, paid levels, buildings, or target blocks
 * are seeded by this harness. Never run against the immutable source or a played save.
 */
@GameTestHolder("catancraft_progression53")
@PrefixGameTestTemplate(false)
public final class Progression53Tests {
    private static final String PURPOSE = "verified-clean-r7-progression53";
    private static final String READY = "Base city paths and six house designs ready";
    private static final UUID ACTOR = UUID.fromString("b048a6aa-8c51-4a4f-888b-82513aa00530");
    private static final List<String> CITIES = java.util.stream.IntStream.range(0,16)
            .mapToObj(i -> String.valueOf((char)('a'+i))).toList();
    private static final List<BuildingType> INDUSTRIES = List.of(BuildingType.STEEL_MILL,
            BuildingType.CONCRETE_PLANT, BuildingType.TEXTILE_MILL, BuildingType.REFINERY,
            BuildingType.MACHINE_SHOP, BuildingType.ELECTRONICS_FACTORY, BuildingType.CHEMICAL_PLANT,
            BuildingType.WEAPONS_FACTORY, BuildingType.VEHICLE_FACTORY);
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_ECONOMY_CYCLES_PER_ACTION = 20000;
    private Progression53Tests() {}

    @GameTest(template="townhall_state_empty",batch="progression53",timeoutTicks=120000)
    public static void authored_world_no_grant_cumulative_campaign(GameTestHelper h) throws Exception {
        new Campaign(h).start();
    }

    /** Capture the actual command refusal without changing its entity/permission checks. */
    private static final class Feedback implements CommandSource {
        private final ArrayDeque<String> messages=new ArrayDeque<>();
        public void sendSystemMessage(Component component) {
            String text=component.getString();if(text.length()>4096)text=text.substring(0,4096);
            if(messages.size()==32)messages.remove();messages.add(text);
        }
        public boolean acceptsSuccess(){return true;}
        public boolean acceptsFailure(){return true;}
        public boolean shouldInformAdmins(){return false;}
        void clear(){messages.clear();}
        String description(){return messages.isEmpty()?"No command feedback was emitted":String.join(" | ",messages);}
    }

    private record Step(String city, String command, EconomyCost price,
                        BooleanSupplier complete, String kind) {}

    private static final class Campaign {
        final GameTestHelper h;
        final MinecraftServer server;
        final ServerLevel level;
        final CatanSavedData data;
        final Path root, evidence;
        final String source, phase;
        final int layout, startTick;
        final JsonObject input;
        final List<Step> steps = new ArrayList<>();
        final JsonArray events = new JsonArray(), checkpoints = new JsonArray(), plannedClaims = new JsonArray();
        final Map<String,String> histories = new TreeMap<>();
        final Map<String,CompoundTag> managers = new TreeMap<>();
        final Map<String,Integer> covered = new TreeMap<>();
        final Map<String,Set<Integer>> purchasedLevels = new TreeMap<>();
        final FakePlayer actor;
        final CommandSourceStack commands;
        final Feedback feedback=new Feedback();
        NationData nation;
        SectorChunkLease lease;
        String leasedCity = "";
        int index, pendingTicks, waitingCycles, totalCycles, startedAt;
        long cashBefore, cycleBefore;
        Map<ResourceType,Long> stockBefore;
        boolean submitted;
        String action = "initialize";

        Campaign(GameTestHelper h) throws Exception {
            this.h=h;level=h.getLevel();server=level.getServer();
            require(server instanceof GameTestServer,"Isolated native GameTestServer required");
            require(server.isSameThread(),"Campaign must run on the main server thread");
            root=server.getWorldPath(LevelResource.ROOT).toRealPath();
            source=System.getProperty("catancraft.progression53Source", "");
            phase=System.getProperty("catancraft.progression53Phase", "");
            String selected=System.getProperty("catancraft.progression53Layout", "");
            require(source.matches("[0-9a-f]{40}")&&selected.matches("[0-8]")
                    &&Set.of("seed","verify").contains(phase),"Explicit exact source/layout/phase required");
            layout=Integer.parseInt(selected);startTick=server.getTickCount();
            Path manifest=Path.of("config/catancraft/progression53-input.json").toAbsolutePath();
            require(Files.isRegularFile(manifest,LinkOption.NOFOLLOW_LINKS)&&!Files.isSymbolicLink(manifest)
                    &&Files.size(manifest)<65536,"Missing bounded authored-input manifest");
            input=JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
            require(input.get("schema").getAsInt()==1&&input.get("purpose").getAsString().equals(PURPOSE)
                    &&input.get("sourceCommit").getAsString().equals(source)&&input.get("layout").getAsInt()==layout,
                    "Manifest is for a different campaign/source/layout");
            require(root.getFileName().toString().equals(input.get("worldFolder").getAsString())
                    &&server.getWorldData().getLevelName().equals(input.get("levelName").getAsString())
                    &&level.getSeed()==input.get("seed").getAsLong(),"Authored world identity changed");
            require(ShopSites51.MAP_SHA256.equals(input.get("mapSha256").getAsString())
                    &&ShopSites51.MAP_SHA256.equals(TownHallRevisions.sha(Files.readAllBytes(MapDefinitionManager.path()))),
                    "Staged map differs from reviewed A-P map");
            require(input.get("authoredWorldSha256").getAsString().matches("[0-9a-f]{64}"),"Missing source world digest");
            require(FreshDemoAuthorization.authorize(server).equals(root),"Fresh authored authorization required");
            data=CatanSavedData.get(server);
            evidence=root.resolve("data/progression53-layout-"+layout);
            require(!Files.isSymbolicLink(evidence),"Campaign evidence path is a symlink");
            actor=new FakePlayer(level,new GameProfile(ACTOR,"Progression53"));
            // Actor stays clear of every construction envelope. No player interaction
            // or authenticated network session is claimed by command execution.
            actor.setPos(0,200,0);
            commands=actor.createCommandSourceStack().withPermission(2).withSource(feedback);
        }

        void start() throws Exception {
            if(phase.equals("verify")) { restart(); return; }
            require(!Files.exists(evidence,LinkOption.NOFOLLOW_LINKS),"Fresh campaign refuses previous evidence/history");
            require(data.nations().isEmpty()&&data.territories().size()==16
                    &&data.territories().stream().allMatch(t -> t.ownerNationId()==null&&t.cityLevel()==1
                    &&t.producerLevel()==1&&t.buildings().isEmpty()),"Campaign requires clean nations/purchases, never resets them");
            for(String city:CITIES) ShopSites51.canonical(MapDefinitionManager.territory(city));
            try(var files=Files.newDirectoryStream(root.resolve("data"),"catancraft_*")) {
                for(Path p:files) require(!p.getFileName().toString().matches("catancraft_(basic_purchase|producer|r5_auto|sector|th_prestige)_.*"),
                        "Inherited paid history refused: "+p.getFileName());
            }
            Files.createDirectory(evidence);
            writeReport("RUNNING");
            execute("nation create Progression53 Layout "+layout);
            nation=data.nationForPlayer(ACTOR);
            require(nation!=null&&nation.treasury()==12000&&nation.stockpileView().values().stream().allMatch(v->v==0),
                    "Normal new-nation starting funds changed; no grants are authorized");
            steps.add(new Step("d","nation start 2",null,()->owned("d"),"found"));
            for(String city:claimOrder()) if(!city.equals("d"))
                steps.add(new Step(city,"nation claim "+city,EconomyCatalog.neutralTerritoryClaimCost(),()->owned(city),"claim"));
            for(String city:CITIES) steps.add(checkpoint(city,1));
            // Income and production purchases are interleaved with Town Halls;
            // no TH5-first grant shortcut is used.
            for(String city:CITIES) steps.add(build(city,BuildingType.COMMERCIAL_DISTRICT,""));
            for(int tier=2;tier<=5;tier++) {
                int target=tier;
                for(String city:CITIES) steps.add(new Step(city,"nation upgrade city "+city,
                        EconomyCatalog.cityUpgradeCost(target-1),()->data.territory(city).cityLevel()==target,"hall"));
                // Install every currently eligible layout before any upgrade that
                // requires its processed output. All nine types occur per run.
                for(String city:CITIES) for(int slot=0;slot<3;slot++) {
                    BuildingType type=industry(city,slot);
                    if(type.minCityLevel()==tier) steps.add(build(city,type,IndustrialPlotService.INDUSTRIAL_PLOTS.get(slot)));
                }
                if(tier==3) for(String city:CITIES) steps.add(build(city,BuildingType.LOGISTICS_CENTER,""));
                for(String city:CITIES) steps.add(new Step(city,"nation upgrade producer "+city,
                        EconomyCatalog.producerUpgradeCost(target-1),()->data.territory(city).producerLevel()==target,"producer"));
                for(String city:CITIES) {
                    for(BuildingType type:buildingTypes(city,target)) {
                        int first=type==BuildingType.COMMERCIAL_DISTRICT?target-1:
                                type==BuildingType.LOGISTICS_CENTER?(target==3?1:target-1):
                                type.minCityLevel()==target?1:target-1;
                        for(int from=first;from<target;from++) {
                            int next=from+1;
                            steps.add(new Step(city,"@upgrade "+type.id(),EconomyCatalog.buildingUpgradeCost(type,from),
                                    ()->building(city,type).level()==next,"building_upgrade"));
                        }
                    }
                }
                for(String city:CITIES) steps.add(checkpoint(city,target));
            }
            next();
        }

        List<String> claimOrder() {
            LinkedHashSet<String> order=new LinkedHashSet<>();order.add("d");
            while(order.size()<16) {
                boolean needsFoodCity=CITIES.stream().anyMatch(c->!order.contains(c)&&foodCity(c));
                String next;
                if(needsFoodCity) {
                    // Breadth-first paths from the owned frontier prioritize the
                    // nearest additional food source. A lexicographic expansion
                    // can exhaust all Agriculture surplus before reaching it.
                    ArrayDeque<List<String>> paths=new ArrayDeque<>();
                    Set<String> seen=new HashSet<>(order);
                    for(String city:CITIES)if(!seen.contains(city)
                            &&data.territory(city).neighbors().stream().anyMatch(order::contains)) {
                        seen.add(city);paths.add(List.of(city));
                    }
                    List<String> chosen=null;
                    while(!paths.isEmpty()) {
                        List<String> path=paths.remove();String last=path.get(path.size()-1);
                        if(foodCity(last)){chosen=path;break;}
                        for(String neighbor:data.territory(last).neighbors().stream().sorted().toList())
                            if(seen.add(neighbor)) {List<String> extended=new ArrayList<>(path);extended.add(neighbor);paths.add(List.copyOf(extended));}
                    }
                    require(chosen!=null,"No connected route to an unclaimed Agriculture territory");
                    next=chosen.get(0);
                } else next=CITIES.stream().filter(c->!order.contains(c))
                        .filter(c->data.territory(c).neighbors().stream().anyMatch(order::contains)).findFirst().orElseThrow();
                require(!order.contains(next)&&data.territory(next).neighbors().stream().anyMatch(order::contains),
                        "Planned claim must be newly adjacent: "+next);
                order.add(next);
                int food=order.stream().mapToInt(c->EconomyEngine.rawOutputPerCycle(data.territory(c),ResourceType.AGRICULTURE)).sum();
                int upkeep=order.stream().mapToInt(c->EconomyBalance.agricultureUpkeepPerCycle(data.territory(c).producerLevel())).sum();
                require(food>upkeep,"Expansion would consume all normal Agriculture surplus before next claim: "+next);
            }
            for(String city:order)plannedClaims.add(city);
            CatanCraft.LOGGER.info("PROGRESSION53_CLAIM_PLAN layout={} policy=nearest-unclaimed-agriculture-first order={}",layout,order);
            return List.copyOf(order);
        }
        boolean foodCity(String city) {return MapDefinitionManager.territory(city).resources().contains(ResourceType.AGRICULTURE);}
        List<BuildingType> buildingTypes(String city,int tier) {
            List<BuildingType> types=new ArrayList<>();types.add(BuildingType.COMMERCIAL_DISTRICT);
            for(int i=0;i<3;i++)if(industry(city,i).minCityLevel()<=tier)types.add(industry(city,i));
            if(tier>=3)types.add(BuildingType.LOGISTICS_CENTER);return types;
        }
        BuildingType industry(String city,int slot) {return INDUSTRIES.get(((city.charAt(0)-'a')*3+slot+layout)%9);}
        Step build(String city,BuildingType type,String plot) {
            return new Step(city,"nation build "+city+" "+type.id()+(plot.isEmpty()?"":" "+plot),
                    EconomyCatalog.buildingCost(type),()->data.territory(city).buildings().stream().anyMatch(b->b.type()==type),"build");
        }
        Step checkpoint(String city,int tier) {return new Step(city,"@checkpoint "+tier,null,()->true,"checkpoint");}
        boolean owned(String city) {return nation.id().equals(data.territory(city).ownerNationId());}
        BuildingInstance building(String city,BuildingType type) {
            return data.territory(city).buildings().stream().filter(b->b.type()==type).findFirst().orElseThrow();
        }
        int buildingIndex(String city,BuildingType type) {return data.territory(city).buildings().indexOf(building(city,type));}

        void next() {
            h.runAfterDelay(1,()->{
                try { tick(); }
                catch(Throwable failure) {
                    closeLease();
                    try {JsonObject row=new JsonObject();row.addProperty("kind","FAIL");row.addProperty("action",action);
                        row.addProperty("error",failure.toString());events.add(row);writeReport("FAIL");}
                    catch(Exception ignored) {failure.addSuppressed(ignored);}
                    throw new RuntimeException("Progression53 layout="+layout+" step="+index+" action="+action,failure);
                }
            });
        }
        void tick() throws Exception {
            if(index==steps.size()) { finish();return; }
            Step s=steps.get(index);action=s.city()+" "+s.command();
            if(!leasedCity.equals(s.city())) {
                closeLease();var r=MapDefinitionManager.territory(s.city()).restorationRegion();
                lease=SectorChunkLease.acquire(level,new AABB(r.minX(),r.minY(),r.minZ(),r.maxX()+1,r.maxY()+1,r.maxZ()+1).inflate(3));
                leasedCity=s.city();pendingTicks=0;
            }
            lease.keepAlive();require(++pendingTicks<6000,"Action did not settle: "+action+"; "+PaidRecoveryService.status(server,s.city())+"; "+CityBasePreparation.status(server,s.city()));
            if(!lease.ready()||CityBasePreparation.isRunning(server)||PaidRecoveryService.isRunning(server)) {next();return;}
            if(owned(s.city())) {
                String ready=CityBasePreparation.status(server,s.city());
                require(!ready.contains(" blocked:"),ready);
                String recovery=PaidRecoveryService.status(server,s.city());require(!recovery.contains("blocked:"),recovery);
                if(!READY.equals(ready)) {next();return;}
            }
            if(s.kind().equals("checkpoint")) {
                verifyCity(s.city(),Integer.parseInt(s.command().substring("@checkpoint ".length())));
                completed();return;
            }
            if(submitted) {
                if(!s.complete().getAsBoolean()) {next();return;}
                verifyCharge(s);recordAction(s);completed();return;
            }
            if(s.price()!=null&&!s.price().canAfford(nation)) {
                if(waitingCycles==0) manageTargets(s.price());
                require(++waitingCycles<=MAX_ECONOMY_CYCLES_PER_ACTION,"Normal economy cannot afford action after bounded cycles: "+action+" need="+s.price().describe()+" stock="+nation.stockpileView());
                produce();next();return;
            }
            cashBefore=nation.treasury();stockBefore=nation.stockpileView();cycleBefore=data.lastScheduledEconomyCycle();startedAt=server.getTickCount();
            String command=s.command();
            if(command.startsWith("@upgrade "))command="nation building upgrade "+s.city()+" "+buildingIndex(s.city(),BuildingType.parse(command.substring(9)));
            execute(command);submitted=true;
            if(s.complete().getAsBoolean()) {verifyCharge(s);recordAction(s);completed();} else next();
        }
        void manageTargets(EconomyCost price) throws Exception {
            // National target stock is normal gameplay. Demand includes conservative
            // upstream batches, so downstream factories eventually pause and leave
            // enough Steel/Mechanical Parts for the actual purchase as well.
            EnumMap<ResourceType,Long> need=new EnumMap<>(ResourceType.class);
            need.putAll(price.resources());
            for(int dependency=3;dependency>=1;dependency--) for(BuildingType type:INDUSTRIES) {
                if(!type.isProcessor()||type.processingTier()!=dependency)continue;
                long target=need.getOrDefault(type.output(),0L);
                long missing=Math.max(0,target-nation.resource(type.output()));
                long batches=(missing+type.outputPerBatch()-1)/type.outputPerBatch();
                for(var input:type.inputs().entrySet())need.merge(input.getKey(),batches*input.getValue(),Long::sum);
            }
            for(String city:CITIES)for(BuildingInstance b:data.territory(city).buildings())if(b.type().isProcessor()) {
                long target=need.getOrDefault(b.type().output(),0L);
                if(b.targetStock()!=target)execute("nation building target "+city+" "+buildingIndex(city,b.type())+" "+target);
            }
        }
        void produce() {
            if(!data.hasEconomySchedule()) EconomyEngine.scheduledCycle(data,level.getGameTime());
            long cycle=data.lastScheduledEconomyCycle()+1;
            require(EconomyEngine.scheduledCycle(data,cycle*EconomyBalance.CYCLE_TICKS)==EconomyEngine.ScheduledResult.POSTED,"Expected one scheduled production boundary");
            require(EconomyEngine.scheduledCycle(data,cycle*EconomyBalance.CYCLE_TICKS)==EconomyEngine.ScheduledResult.UNCHANGED,"Same boundary replayed income");
            totalCycles++;
        }
        void execute(String command) throws Exception {
            feedback.clear();
            int result=server.getCommands().getDispatcher().execute(command,commands);
            if(result!=1)CatanCraft.LOGGER.error("PROGRESSION53_COMMAND_REFUSAL layout={} command={} feedback={}",layout,command,feedback.description());
            require(result==1,"Normal registered command refused: "+command+"; "+feedback.description());
            CatanCraft.LOGGER.info("PROGRESSION53_COMMAND layout={} {}",layout,command);
        }
        void verifyCharge(Step s) {
            require(cycleBefore==data.lastScheduledEconomyCycle(),"Economy advanced during purchase; exact-charge proof would be ambiguous");
            long amount=s.price()==null?0:s.price().money();
            require(nation.treasury()==cashBefore-amount,"Expected single exact cash debit: "+action);
            for(ResourceType type:ResourceType.values())require(nation.resource(type)==stockBefore.getOrDefault(type,0L)
                    -(s.price()==null?0:s.price().resources().getOrDefault(type,0L)),"Expected single exact "+type+" debit: "+action);
        }
        void recordAction(Step s) throws Exception {
            if(s.kind().equals("build")) {
                BuildingType type=BuildingType.parse(s.command().split(" ")[3]);
                if(type.isProcessor()) execute("nation building target "+s.city()+" "+buildingIndex(s.city(),type)+" 0");
            }
            if(s.kind().equals("build")) {
                BuildingType type=BuildingType.parse(s.command().split(" ")[3]);
                if(IndustrialPlotService.isIndustry(type))verifyFactoryPurchase(s.city(),type);
            } else if(s.kind().equals("building_upgrade")) {
                BuildingType type=BuildingType.parse(s.command().substring(9));
                if(IndustrialPlotService.isIndustry(type))verifyFactoryPurchase(s.city(),type);
            }
            JsonObject row=new JsonObject();row.addProperty("city",s.city());row.addProperty("command",s.command());row.addProperty("kind",s.kind());
            row.addProperty("cyclesWaited",waitingCycles);row.addProperty("nativeTicks",server.getTickCount()-startedAt);
            row.addProperty("exactDebit",true);row.addProperty("treasury",nation.treasury());events.add(row);
            writeReport("RUNNING");
        }
        void completed() {index++;submitted=false;pendingTicks=waitingCycles=0;next();}
        void closeLease() {if(lease!=null){lease.close();lease=null;}leasedCity="";}

        void verifyFactoryPurchase(String city,BuildingType type) throws IOException {
            BuildingInstance b=building(city,type);var def=MapDefinitionManager.territory(city);String plot=b.plotId();
            String asset=SchematicAssetRegistry.buildingAsset(type);
            require(SchematicAssetRegistry.asset(asset).placementToken().equals(data.placedMapAsset("territory:"+city+":"+plot)),"Paid factory lacks completed placement token");
            verifyPlan(BasicConstructionAssets.plan(asset,IndustrialPlotService.anchorFor(type,plot,def)),null,false);
            if(type==BuildingType.WEAPONS_FACTORY) {
                var site=ShopSites51.sites(def).stream().filter(v->v.plot().equals(plot)).findFirst().orElseThrow();
                require(ShopFacility40.construction(level,data,nation,site).level()==b.level(),"Purchased weapons counter level changed");
            }
            if(type==BuildingType.VEHICLE_FACTORY) {
                var site=VehicleSites52.sites(def).stream().filter(v->v.plot().equals(plot)).findFirst().orElseThrow();
                require(VehicleFactory50.construction(level,data,nation,site).level()==b.level(),"Purchased vehicle counter level changed");
            }
            require(purchasedLevels.computeIfAbsent(city+":"+plot+":"+type.id(),k->new TreeSet<>()).add(b.level()),"Paid factory level unexpectedly repeats");
        }
        void verifyCity(String city,int tier) throws Exception {
            var t=data.territory(city);var def=MapDefinitionManager.territory(city);
            require(owned(city)&&t.cityLevel()==tier&&t.producerLevel()==tier,"Progression levels/ownership differ in "+city);
            require(READY.equals(CityBasePreparation.status(server,city)),"Automatic city preparation incomplete: "+city);
            ShopSites51.Site manager=ShopSites51.sites(def).stream().filter(ShopSites51.Site::starter).findFirst().orElseThrow();
            var hall=ShopFacility40.construction(level,data,nation,manager);
            require(hall.level()==0,"Town Hall accidentally grants factory tier");
            var lectern=(LecternBlockEntity)level.getBlockEntity(manager.counter());
            require(lectern!=null,"Management lectern missing after normal progression: "+city);
            CompoundTag book=lectern.getBook().save(new CompoundTag());
            CompoundTag original=managers.putIfAbsent(city,book.copy());
            require(original==null||original.equals(book),"Management book changed across tier progression: "+city);
            List<SchematicPlacementService.PlanCell> hallPlan=tier<=3?
                    BasicConstructionAssets.plan("th"+tier,SchematicAssetRegistry.territoryAnchor("th"+tier,def)):
                    TownHallRevisions.plan(tier,TownHallRevisions.revision(tier,hall.token()),SchematicAssetRegistry.territoryAnchor("th"+tier,def));
            long checked=verifyPlan(hallPlan,manager.counter(),false);
            Set<net.minecraft.core.BlockPos> producerDirt=ProducerService.completedNaturalDirt(server,city);
            for(String family:ProducerRevisions.applicable(def)) {
                String token=data.placedMapAsset("territory:"+city+":"+SchematicAssetRegistry.asset(family).plot());
                require(ProducerRevisions.revision(family,token)==tier,"Producer paid visual tier mismatch: "+city+"/"+family);
                var anchor=SchematicAssetRegistry.territoryAnchor(family,def);
                boolean exterior=ProducerRevisions.exterior(family,token);
                checked+=verifyPlan(exterior?ProducerExteriorR2.plan(family,tier,anchor):ProducerRevisions.plan(family,tier,anchor),null,true,producerDirt,
                    ProducerGrowth53.covers(family,tier,exterior,anchor));
            }
            JsonArray facilities=new JsonArray();
            for(int slot=0;slot<3;slot++) {
                BuildingType type=industry(city,slot);String plot=IndustrialPlotService.INDUSTRIAL_PLOTS.get(slot);
                if(type.minCityLevel()>tier)continue;
                BuildingInstance b=building(city,type);
                require(b.level()==tier&&b.plotId().equals(plot),"Factory level/plot differs: "+city+"/"+plot);
                String asset=SchematicAssetRegistry.buildingAsset(type);
                require(SchematicAssetRegistry.asset(asset).placementToken().equals(data.placedMapAsset("territory:"+city+":"+plot)),"Factory placement receipt missing");
                checked+=verifyPlan(BasicConstructionAssets.plan(asset,IndustrialPlotService.anchorFor(type,plot,def)),null,false);
                if(type==BuildingType.WEAPONS_FACTORY) {
                    var site=ShopSites51.sites(def).stream().filter(v->v.plot().equals(plot)).findFirst().orElseThrow();
                    require(ShopFacility40.construction(level,data,nation,site).level()==tier,"Weapon counter lost earned level");
                }
                if(type==BuildingType.VEHICLE_FACTORY) {
                    var site=VehicleSites52.sites(def).stream().filter(v->v.plot().equals(plot)).findFirst().orElseThrow();
                    require(VehicleFactory50.construction(level,data,nation,site).level()==tier,"Vehicle counter lost earned level");
                    VehicleFactory50.verifyEnvelope(level,site.anchor(),nation.id());
                }
                JsonObject facility=new JsonObject();facility.addProperty("plot",plot);facility.addProperty("type",type.id());facility.addProperty("level",tier);facilities.add(facility);
                covered.merge(city+":"+plot+":"+type.id(),1,Integer::sum);
            }
            for(BuildingType civic:List.of(BuildingType.COMMERCIAL_DISTRICT,BuildingType.LOGISTICS_CENTER)) {
                if(tier==1||civic==BuildingType.LOGISTICS_CENTER&&tier<3)continue;
                require(building(city,civic).level()==tier,"Civic tier differs");
                checked+=R5CivicAutomaticInstaller.verifyInstalled(level,def,civic);
            }
            long wallCells=verifyUnpurchasedWalls(city);
            verifyHistories(city);
            CompoundTag ledger=data.save(new CompoundTag());var reload=CatanSavedData.load(ledger);
            require(reload.save(new CompoundTag()).equals(ledger),"SavedData roundtrip changed cumulative city/accounting state");
            JsonObject row=new JsonObject();row.addProperty("city",city);row.addProperty("townHallLevel",tier);row.addProperty("producerLevel",tier);
            row.addProperty("rotation",def.cityCenter().rotation());row.addProperty("checkedPhysicalCells",checked);
            row.addProperty("managementBookPreserved",true);row.addProperty("managementBookPresent",!lectern.getBook().isEmpty());row.addProperty("automaticPublicAndHousesReady",true);
            row.addProperty("immutableEarlierHistories",true);row.addProperty("savedDataRoundtrip",true);
            row.addProperty("earlierEligibleFacilitiesRechecked",true);row.addProperty("unpaidWallSourceCellsChecked",wallCells);row.addProperty("compassWallQuotes",4);row.addProperty("scheduledEconomyCycles",totalCycles);
            row.add("facilities",facilities);checkpoints.add(row);
            CatanCraft.LOGGER.info("PROGRESSION53_CHECKPOINT layout={} city={} TH{} cells={}",layout,city,tier,checked);
            writeReport("RUNNING");
        }
        long verifyPlan(List<SchematicPlacementService.PlanCell> cells,net.minecraft.core.BlockPos manager,boolean producer)throws IOException {
            return verifyPlan(cells,manager,producer,Set.of());
        }
        long verifyPlan(List<SchematicPlacementService.PlanCell> cells,net.minecraft.core.BlockPos manager,boolean producer,Set<net.minecraft.core.BlockPos> producerDirt)throws IOException {
            return verifyPlan(cells,manager,producer,producerDirt,Map.of());
        }
        long verifyPlan(List<SchematicPlacementService.PlanCell> cells,net.minecraft.core.BlockPos manager,boolean producer,Set<net.minecraft.core.BlockPos> producerDirt,Map<net.minecraft.core.BlockPos,BlockState> growth)throws IOException {
            Map<net.minecraft.core.BlockPos,BlockState> expected=new HashMap<>();for(var c:cells)expected.put(c.position(),c.state());
            Map<net.minecraft.core.BlockPos,BlockState> producerCover=producer?ProducerService.producerCoveredGrass(cells):Map.of();
            Map<net.minecraft.core.BlockPos,BlockState> gardenCover=!producer&&manager!=null?BasicConstructionPurchase.th1GardenCover(cells):Map.of();
            Set<net.minecraft.core.BlockPos> copperTargets=!producer&&manager==null?BasicConstructionPurchase.factoryCopperTargets(cells):Set.of();
            for(var c:cells) {
                BlockState actual=level.getBlockState(c.position());boolean matches=producer?ProducerRevisions.matches(actual,c.state()):TownHallInteractionPolicy.matches(actual,c.state(),c.position().equals(manager));
                if(!matches&&producer)matches=ProducerService.matchesProducerCoveredSourceGrass(level,c,producerCover)
                    ||producerDirt.contains(c.position())&&c.state().equals(Blocks.GRASS_BLOCK.defaultBlockState())&&actual.equals(Blocks.DIRT.defaultBlockState())
                    ||c.state().equals(Blocks.DIRT.defaultBlockState())&&ProducerGrowth53.matches(level,c.position(),actual,growth);
                if(!matches&&!gardenCover.isEmpty())matches=BasicConstructionPurchase.gardenGrass(c.position(),actual,gardenCover,level::getBlockState);
                if(!matches&&!copperTargets.isEmpty())matches=BasicConstructionPurchase.agedCopperTarget(c.position(),actual,copperTargets);
                if(!matches&&!producer&&c.state().is(Blocks.GRASS_BLOCK)&&actual.is(Blocks.DIRT)) {
                    BlockState cover=expected.get(c.position().above());
                    matches=cover!=null&&SectorNaturalSupportPolicy.blocksGrass(cover,c.position().above())
                            &&TownHallInteractionPolicy.matches(level.getBlockState(c.position().above()),cover,false);
                }
                require(matches,"Cumulative physical source drift at "+c.position()+" expected="+c.state()+" actual="+actual);
            }
            return cells.size();
        }
        long verifyUnpurchasedWalls(String city) throws IOException {
            var zero=SectorCityAP3.levels(0,0,0,0);
            var expected=city.equals("a")?SectorCityAP3.assembly(zero,Set.of()).states():
                    SectorSites53.assembly(SectorSites53.site(city),zero,Set.of());
            for(var row:expected.entrySet()) {
                var pos=row.getKey();var state=row.getValue();var actual=level.getBlockState(pos);
                boolean match=TownHallInteractionPolicy.matches(actual,state,false);
                if(!match&&state.is(Blocks.GRASS_BLOCK)&&actual.is(Blocks.DIRT)) {
                    var cover=expected.get(pos.above());
                    match=cover!=null&&SectorNaturalSupportPolicy.blocksGrass(cover,pos.above())
                            &&TownHallInteractionPolicy.matches(level.getBlockState(pos.above()),cover,false);
                }
                require(match,"Earlier wall source changed after progression in "+city+" at "+pos+" expected="+state+" actual="+actual);
            }
            CompoundTag before=data.save(new CompoundTag());
            for(var side:SectorContracts.Direction.values()) {
                require(SectorService.savedState(data,city,side).level()==0,"Campaign must not adopt paid wall fixtures");
                var result=SectorService.quote(server,ACTOR,city,side.name().toLowerCase(Locale.ROOT));
                require(result.success(),"Wall quote unavailable after progression: "+result.message());
            }
            require(before.equals(data.save(new CompoundTag())),"Readonly wall quote changed economy/paid state");
            return expected.size();
        }
        void verifyHistories(String city) throws IOException {
            for(var entry:histories.entrySet()) {
                Path file=root.resolve("data").resolve(entry.getKey());
                require(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)&&!Files.isSymbolicLink(file)
                        &&TownHallRevisions.sha(Files.readAllBytes(file)).equals(entry.getValue()),"Completed original history changed: "+entry.getKey());
            }
            try(var paths=Files.newDirectoryStream(root.resolve("data"))) {
                for(Path p:paths) {
                    String name=p.getFileName().toString();
                    if(!(name.startsWith("catancraft_basic_purchase_"+city+"_")||name.startsWith("catancraft_producer_"+city+"_")
                            ||name.startsWith("catancraft_r5_auto_"+city+"_")||name.startsWith("catancraft_th_prestige_"+city+"_")))continue;
                    require(!Files.isSymbolicLink(p)&&Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&Files.size(p)<=8*1024*1024,"Unbounded or linked purchase history");
                    // Basic immutable intents intentionally retain PENDING while their
                    // separate recovery companion/receipt certifies completion.
                    if(name.endsWith(".recovery-v1.dat"))require(ProducerJournal.read(p).getString("phase").equals("COMMITTED"),"Unfinished recovery companion");
                    else if(name.endsWith(".dat")&&name.startsWith("catancraft_basic_purchase_"))require(!BasicConstructionPurchase.unfinished(p),"Unfinished basic purchase");
                    else if(name.endsWith(".dat")&&name.startsWith("catancraft_producer_"))require(ProducerTransaction.phase(ProducerJournal.read(p))==ProducerTransaction.Phase.COMMITTED,"Unfinished producer history");
                    else if(name.endsWith(".dat")&&name.startsWith("catancraft_th_prestige_")) {
                        byte[] bytes=PaidConstructionRecovery.readBounded(p,8*1024*1024);CompoundTag journal=PrestigePurchaseRecovery.read(bytes);
                        boolean recovered=PaidConstructionRecovery.complete(p,bytes);
                        require(journal.getString("phase").equals("COMMITTED")||recovered,"Unfinished prestige original/recovery companion");
                        require(!Files.exists(PaidConstructionRecovery.companion(p))||recovered,"Unfinished prestige companion");
                        UUID transaction=journal.getUUID("transaction");
                        require(city.equals(journal.getString("city"))&&root.toString().equals(journal.getString("worldRoot"))
                            &&nation.id().toString().equals(journal.getString("ownerNation"))
                            &&(transaction+":paid").equals(data.placedMapAsset(PaidConstructionRecovery.receiptKey(transaction))),"Prestige history identity/payment differs");
                    }
                    else if(name.endsWith(".json")) {
                        byte[] bytes=PaidConstructionRecovery.readBounded(p,8*1024*1024);
                        require(CivicPurchaseRecovery.read(bytes).get("phase").getAsString().equals("COMMITTED")||PaidConstructionRecovery.complete(p,bytes),"Unfinished civic history/companion");
                    }
                    histories.putIfAbsent(name,TownHallRevisions.sha(Files.readAllBytes(p)));
                }
            }
        }
        void finish() throws Exception {
            closeLease();require(checkpoints.size()==80,"Expected five cumulative all-city checkpoints");
            require(covered.size()==48&&purchasedLevels.size()==48&&purchasedLevels.values().stream().allMatch(v->v.equals(Set.of(1,2,3,4,5))),"Expected all48 industrial placements andfivepaidlevels in this layout");
            for(String city:CITIES) {
                require(data.territory(city).cityLevel()==5&&data.territory(city).producerLevel()==5,"Unfinished tier5");
                CompoundTag before=data.save(new CompoundTag());
                require(server.getCommands().getDispatcher().execute("nation upgrade city "+city,commands)==0,"Max Town Hall upgraded again");
                require(server.getCommands().getDispatcher().execute("nation upgrade producer "+city,commands)==0,"Max producer upgraded again");
                require(data.save(new CompoundTag()).equals(before),"Max-tier refusal changed balances/levels/history");
            }
            require(server.saveEverything(true,true,true),"Final real save failed");
            CompoundTag disk=NbtIo.readCompressed(root.resolve("data/catancraft.dat").toFile()).getCompound("data");
            require(CatanSavedData.load(disk).save(new CompoundTag()).equals(data.save(new CompoundTag())),"Real disk SavedData differs");
            CompoundTag proof=new CompoundTag();proof.putInt("schema",1);proof.putInt("layout",layout);proof.putString("source",source);
            proof.put("ledger",data.save(new CompoundTag()));proof.putUUID("nation",nation.id());
            CompoundTag original=new CompoundTag();histories.forEach(original::putString);proof.put("histories",original);
            CompoundTag books=new CompoundTag();managers.forEach(books::put);proof.put("books",books);
            ProducerJournal.write(evidence.resolve("restart.dat"),proof,false);
            writeReport("PASS");
            CatanCraft.LOGGER.info("PROGRESSION53_SEED_PASS layout={} checkpoints=80 factorySites=48 acceleratedCycles={} actualTicks={}",layout,totalCycles,server.getTickCount()-startTick);
            h.succeed();
        }
        void restart() throws Exception {
            Path file=evidence.resolve("restart.dat");require(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)&&!Files.isSymbolicLink(file),"Completed seed proof missing");
            CompoundTag proof=ProducerJournal.read(file);
            require(proof.getInt("schema")==1&&proof.getInt("layout")==layout&&proof.getString("source").equals(source),"Foreign restart proof");
            nation=data.nation(proof.getUUID("nation"));require(nation!=null&&nation.isLeader(ACTOR),"Restart owner changed");
            require(data.save(new CompoundTag()).equals(proof.getCompound("ledger")),"Process restart changed balances/levels/receipts");
            for(String name:proof.getCompound("histories").getAllKeys())histories.put(name,proof.getCompound("histories").getString(name));
            for(String city:proof.getCompound("books").getAllKeys())managers.put(city,proof.getCompound("books").getCompound(city));
            for(String city:CITIES)steps.add(checkpoint(city,5));
            restartTick(proof);
        }
        void restartTick(CompoundTag proof) {
            h.runAfterDelay(1,()->{try {
                if(index==steps.size()) {
                    closeLease();require(data.save(new CompoundTag()).equals(proof.getCompound("ledger")),"Restart readonly checks changed ledger");
                    writeReport("RESTART_PASS");CatanCraft.LOGGER.info("PROGRESSION53_RESTART_PASS layout={} cities=16",layout);h.succeed();return;
                }
                Step s=steps.get(index);action="restart "+s.city();
                if(!leasedCity.equals(s.city())) {closeLease();var r=MapDefinitionManager.territory(s.city()).restorationRegion();
                    lease=SectorChunkLease.acquire(level,new AABB(r.minX(),r.minY(),r.minZ(),r.maxX()+1,r.maxY()+1,r.maxZ()+1).inflate(3));leasedCity=s.city();pendingTicks=0;}
                lease.keepAlive();require(++pendingTicks<6000,"Restart city readiness timeout");
                String ready=CityBasePreparation.status(server,s.city());require(!ready.contains(" blocked:"),ready);
                if(lease.ready()&&READY.equals(ready)&&!CityBasePreparation.isRunning(server)&&!PaidRecoveryService.isRunning(server)) {verifyCity(s.city(),5);index++;}
                restartTick(proof);
            }catch(Throwable failure){closeLease();try{writeReport("RESTART_FAIL");}catch(Exception ignored){failure.addSuppressed(ignored);}throw new RuntimeException(action,failure);}});
        }
        void writeReport(String result) throws IOException {
            JsonObject out=new JsonObject();out.addProperty("schema",1);out.addProperty("purpose",PURPOSE);out.addProperty("result",result);
            out.addProperty("source",source);out.addProperty("layout",layout);out.addProperty("phase",phase);out.addProperty("worldId",FreshDemoAuthorization.worldId(server,root));
            out.addProperty("authoredWorldSHA256",input.get("authoredWorldSha256").getAsString());out.addProperty("mapSHA256",ShopSites51.MAP_SHA256);
            out.addProperty("artificialMoneyOrMaterialGrants",0);out.addProperty("directPaidLevelOrBuildingSeeding",false);
            out.addProperty("manualTargetBlockWrites",0);out.addProperty("connectedClientAcceptance",false);out.addProperty("realTimePacingAcceptance",false);
            out.addProperty("acceleratedScheduledCycles",totalCycles);out.addProperty("simulatedActiveMinutes",totalCycles*15L);
            out.addProperty("actualNativeTicks",server.getTickCount()-startTick);out.addProperty("step",index);out.addProperty("activeAction",action);
            out.addProperty("claimResourcePolicy","nearest-unclaimed-agriculture-first");out.add("claimOrder",plannedClaims.deepCopy());
            out.add("actions",events.deepCopy());out.add("checkpoints",checkpoints.deepCopy());
            JsonObject inventory=new JsonObject();histories.forEach(inventory::addProperty);out.add("completedHistorySHA256",inventory);
            JsonObject coverage=new JsonObject();covered.forEach(coverage::addProperty);out.add("factorySiteCumulativeChecks",coverage);
            JsonObject purchases=new JsonObject();purchasedLevels.forEach((key,values)->{JsonArray levels=new JsonArray();values.forEach(levels::add);purchases.add(key,levels);});out.add("factoryPurchasedLevels",purchases);
            Files.writeString(evidence.resolve(phase+".json"),JSON.toJson(out)+"\n",StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
        }
    }
    private static void require(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
