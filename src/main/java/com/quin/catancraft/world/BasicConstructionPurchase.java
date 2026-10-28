package com.quin.catancraft.world;

import com.quin.catancraft.data.*;
import com.quin.catancraft.economy.*;
import com.quin.catancraft.map.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Early halls and ordinary factories use the same receipt-backed recovery as paid civic work.
 * An immutable intent captures the validated source BEFORE any writes. Original older
 * journals/tokens remain untouched. No maintenance command or loose schematic is needed.
 */
public final class BasicConstructionPurchase {
    private BasicConstructionPurchase() {}
    record Request(String city,UUID actor,UUID owner,String kind,String asset,String plot,int from,int to) {}
    record Blueprint(List<SchematicPlacementService.PlanCell> source,List<SchematicPlacementService.PlanCell> target,BlockPos manager) {
        Blueprint { source=List.copyOf(source);target=List.copyOf(target); }
    }
    static String stateKey(Request r){return "territory:"+r.city()+":"+r.plot();}
    static Path file(Path root,Request r){return root.resolve("data/catancraft_basic_purchase_"+r.city()+"_"+(r.kind().equals("hall")?r.asset():r.plot())+"_v1.dat");}
    static List<Path> originals(Path root,String city){List<Path> out=new ArrayList<>();for(String id:List.of("th2","th3","plot_1","plot_3","plot_4"))out.add(root.resolve("data/catancraft_basic_purchase_"+city+"_"+id+"_v1.dat"));return out;}
    static CompoundTag describe(Request r){CompoundTag n=new CompoundTag();n.putString("city",r.city());n.putUUID("actor",r.actor());n.putUUID("owner",r.owner());n.putString("kind",r.kind());n.putString("asset",r.asset());n.putString("plot",r.plot());n.putInt("from",r.from());n.putInt("to",r.to());n.putString("assets",BasicConstructionAssets.BUNDLE_SHA);return n;}
    static Request request(CompoundTag n)throws IOException {
        try{
            Request r=new Request(n.getString("city"),n.getUUID("actor"),n.getUUID("owner"),n.getString("kind"),n.getString("asset"),n.getString("plot"),n.getInt("from"),n.getInt("to"));
            if(!r.city().matches("[a-p]")||!describe(r).equals(n))throw new IOException("Malformed basic-purchase contract");
            if(r.kind().equals("hall")) {if(r.from()<1||r.from()>2||r.to()!=r.from()+1||!r.asset().equals("th"+r.to())||!r.plot().equals("town_hall"))throw new IOException("Invalid early Hall transition");}
            else if(!r.kind().equals("factory")||r.from()!=0||r.to()!=1||!IndustrialPlotService.INDUSTRIAL_PLOTS.contains(r.plot())||factory(r.asset())==null)throw new IOException("Invalid factory contract");
            return r;
        }catch(IllegalArgumentException e){throw new IOException("Malformed basic-purchase identity",e);}
    }
    static BuildingType factory(String asset){for(BuildingType t:IndustrialPlotService.INDUSTRIES)if(asset.equals(SchematicAssetRegistry.buildingAsset(t)))return t;return null;}
    public static MapAssetService.Result hall(MinecraftServer server,UUID actor,String city,int target){
        try{var data=CatanSavedData.get(server);var nation=DefenseCommands.requireLeaderOwner(data,actor,city);var t=data.territory(city);
            return submit(server,new Request(city,actor,nation.id(),"hall","th"+target,"town_hall",t.cityLevel(),target));
        }catch(Exception e){return refused(e);}
    }
    public static MapAssetService.Result industry(MinecraftServer server,UUID actor,String city,BuildingType type,String plot){
        try{var nation=DefenseCommands.requireLeaderOwner(CatanSavedData.get(server),actor,city);
            if(!IndustrialPlotService.isIndustry(type))throw new IOException("Not an approved industrial building");
            return submit(server,new Request(city,actor,nation.id(),"factory",SchematicAssetRegistry.buildingAsset(type),plot,0,1));
        }catch(Exception e){return refused(e);}
    }
    private static MapAssetService.Result submit(MinecraftServer server,Request r)throws IOException {
        if(!server.isSameThread())throw new IOException("Construction must start on the server thread");request(describe(r));
        Path root=ConstructionWorldAuthorization.authorize(server);ProducerService.validateMap(MapDefinitionManager.territory(r.city()));
        PaidRecoveryService.requireClear(server,r.city());TownHallRepairService.requireNoPending(server,r.city());
        checkNew(server,r);Path path=file(root,r);if(Files.exists(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Retained purchase history exists; do not buy again");
        PaidRecoveryService.submitFresh(server,r,path);
        return new MapAssetService.Result(true,"Construction queued for "+r.asset()+" in "+r.city()+"; the full site is checked before writes/payment. Status is in /nation gui.",0);
    }
    private static MapAssetService.Result refused(Exception e){return new MapAssetService.Result(false,"Construction not started: "+e.getMessage()+". No new charge or placement by this request.",0);}
    static EconomyCost price(Request r){return r.kind().equals("hall")?EconomyCatalog.cityUpgradeCost(r.from()):EconomyCatalog.buildingCost(factory(r.asset()));}
    private static void checkNew(MinecraftServer server,Request r)throws IOException {checkNew(CatanSavedData.get(server),r,MapDefinitionManager.territory(r.city()));}
    private static void checkNew(CatanSavedData data,Request r,TerritoryDefinition def)throws IOException {
        var nation=data.nation(r.owner());var t=data.territory(r.city());
        if(t==null||nation==null||!r.owner().equals(t.ownerNationId())||!nation.isLeader(r.actor()))throw new IOException("Construction owner/leader changed");
        if(r.kind().equals("hall")){
            if(t.cityLevel()!=r.from()||!SchematicAssetRegistry.asset("th"+r.from()).placementToken().equals(data.placedMapAsset(stateKey(r))))throw new IOException("Early Town Hall level/source revision changed");
        }else{
            BuildingType type=factory(r.asset());
            if(t.cityLevel()<type.minCityLevel()||t.buildings().size()>=EconomyCatalog.maxBuildingSlots(t.cityLevel()))throw new IOException("Factory city-level or slot requirement not met");
            if(IndustrialPlotService.hasUnassignedLegacyIndustry(t)||!IndustrialPlotService.vacantPlots(t,def).contains(r.plot())||data.placedMapAsset(stateKey(r))!=null||t.buildings().stream().anyMatch(b->b.type()==type))throw new IOException("Factory plot or type already occupied/ambiguous");
        }
        EconomyCost cost=price(r);if(cost==null||!cost.canAfford(nation))throw new IOException("Cannot afford original construction price");
    }
    static MapAnchor anchor(Request r,TerritoryDefinition d)throws IOException {
        MapAnchor a=r.kind().equals("hall")?SchematicAssetRegistry.territoryAnchor(r.asset(),d):IndustrialPlotService.anchorFor(factory(r.asset()),r.plot(),d);
        if(a==null)throw new IOException("Missing construction anchor");return a;
    }
    static Blueprint blueprint(Request r,TerritoryDefinition d)throws IOException {return blueprint(r,anchor(r,d),SchematicAssetRegistry.territoryAnchor("starter_settlement",d));}
    /** Pure plan, also used by native tests with translated anchors. */
    static Blueprint blueprint(Request r,MapAnchor a,MapAnchor base)throws IOException {
        var after=BasicConstructionAssets.plan(r.asset(),a);List<SchematicPlacementService.PlanCell> before;
        if(r.kind().equals("hall"))before=BasicConstructionAssets.plan("th"+r.from(),a);
        else {before=new ArrayList<>();for(var c:after)before.add(new SchematicPlacementService.PlanCell(c.localX(),c.localY(),c.localZ(),c.position(),BasicConstructionAssets.baseline(base,c.position())));}
        return new Blueprint(before,after,r.kind().equals("hall")?after.get(TownHallRevisions.managerIndex()).position():null);
    }
    static AABB bounds(Request r,TerritoryDefinition d)throws IOException {var b=SchematicAssetRegistry.asset(r.asset()).boundsAt(anchor(r,d));return new AABB(b.min(),b.max().offset(1,1,1)).inflate(2);}
    /** Only grass already covered in the exact authored SOURCE qualifies. Future
     * construction cover and exposed plot grass never establish this permission. */
    private static Map<BlockPos,BlockState> sourceGrassCover(Blueprint b) {
        Map<BlockPos,BlockState> source=new HashMap<>(),covers=new HashMap<>();
        for(var c:b.source())source.put(c.position(),c.state());
        for(var c:b.source())if(c.state().equals(Blocks.GRASS_BLOCK.defaultBlockState())){
            BlockState cover=source.get(c.position().above());
            if(SectorNaturalSupportPolicy.blocksGrass(cover,c.position().above()))covers.put(c.position(),cover);
        }
        return covers;
    }
    private static boolean sourceDirt(BlockPos pos,BlockState actual,Map<BlockPos,BlockState> covers,
            java.util.function.Function<BlockPos,BlockState> observed) {
        BlockState cover=covers.get(pos);
        return actual.equals(Blocks.DIRT.defaultBlockState())&&cover!=null&&cover.equals(observed.apply(pos.above()));
    }
    private static BlockState preserveSourceDirt(BlockState before,BlockState target,boolean provenCover) {
        return provenCover&&before.equals(Blocks.DIRT.defaultBlockState())&&target.is(Blocks.GRASS_BLOCK)
                ?before:TownHallInteractionPolicy.targetFor(before,target);
    }
    static CompoundTag capture(ServerLevel level,Blueprint b)throws IOException {
        if(b.source().size()!=b.target().size()||b.source().isEmpty()||b.source().size()>250000)throw new IOException("Invalid basic-construction volume");
        Map<BlockPos,BlockState> doors=new HashMap<>();for(var c:b.source())if(c.state().getBlock() instanceof DoorBlock)doors.put(c.position(),c.state());
        Set<BlockPos> missing=TownHallInteractionPolicy.missingDoorCells(doors,level::getBlockState);
        Map<BlockPos,BlockState> grassCover=sourceGrassCover(b);
        Map<BlockState,Integer> palette=new LinkedHashMap<>();ListTag encoded=new ListTag();int[] states=new int[b.source().size()];List<ConstructionHazards.Write> writes=new ArrayList<>();CompoundTag result=new CompoundTag();
        for(int i=0;i<states.length;i++){
            var c=b.source().get(i);var target=b.target().get(i);BlockPos p=c.position();
            if(!p.equals(target.position()))throw new IOException("Construction envelope mismatch");
            if(!level.hasChunkAt(p))throw new PreparationDeferred("Construction chunks are still loading");
            BlockState actual=level.getBlockState(p);boolean manager=p.equals(b.manager());
            if(!missing.contains(p)&&!TownHallInteractionPolicy.matches(actual,c.state(),manager)
                    &&!sourceDirt(p,actual,grassCover,level::getBlockState))throw new IOException("Construction source conflict at "+p+": "+actual);
            BlockEntity be=level.getBlockEntity(p);
            if(manager){
                if(!(be instanceof LecternBlockEntity)||!target.state().is(Blocks.LECTERN))throw new IOException("Original management lectern is missing or changed");
                result.put("managerNbt",be.saveWithFullMetadata().copy());
            }else if(be!=null||actual.hasBlockEntity())throw new IOException("Unexpected existing inventory at "+p);
            if(!actual.getFluidState().isEmpty()||!target.state().getFluidState().isEmpty())throw new IOException("Unapproved construction fluid at "+p);
            Integer id=palette.get(actual);if(id==null){id=palette.size();palette.put(actual,id);encoded.add(NbtUtils.writeBlockState(actual));}states[i]=id;
            BlockState next=manager?actual:preserveSourceDirt(actual,target.state(),grassCover.containsKey(p));
            if(!actual.equals(next))writes.add(new ConstructionHazards.Write(p,next));
        }
        ConstructionHazards.inspect(level,writes).verify();
        result.put("palette",encoded);result.putIntArray("states",states);return result;
    }
    static List<PaidConstructionRecovery.Cell> cells(ServerLevel level,Blueprint b,CompoundTag snapshot)throws IOException {
        int[] indices=snapshot.getIntArray("states");ListTag p=snapshot.getList("palette",Tag.TAG_COMPOUND);
        if(indices.length!=b.source().size()||p.isEmpty()||p.size()>4096)throw new IOException("Incomplete construction before-image");
        List<BlockState> palette=new ArrayList<>();for(int i=0;i<p.size();i++){
            var tag=p.getCompound(i);BlockState s=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),tag);
            if(!NbtUtils.writeBlockState(s).equals(tag))throw new IOException("Unknown or malformed captured block state");palette.add(s);
        }
        Map<BlockPos,BlockState> saved=new HashMap<>(),doors=new HashMap<>();
        for(int i=0;i<indices.length;i++){if(indices[i]<0||indices[i]>=palette.size())throw new IOException("Invalid captured state index");var c=b.source().get(i);saved.put(c.position(),palette.get(indices[i]));if(c.state().getBlock() instanceof DoorBlock)doors.put(c.position(),c.state());}
        Set<BlockPos> missing=TownHallInteractionPolicy.missingDoorCells(doors,saved::get);List<PaidConstructionRecovery.Cell> cells=new ArrayList<>(indices.length);
        Map<BlockPos,BlockState> grassCover=sourceGrassCover(b);
        for(int i=0;i<indices.length;i++){
            var source=b.source().get(i);var target=b.target().get(i);BlockPos pos=source.position();BlockState before=saved.get(pos);boolean manager=pos.equals(b.manager());
            if(!missing.contains(pos)&&!TownHallInteractionPolicy.matches(before,source.state(),manager)
                    &&!sourceDirt(pos,before,grassCover,saved::get))throw new IOException("Captured source is not an approved revision at "+pos);
            // The sealed before-image proves the authored source cover existed;
            // delayed recovery may now observe that same grass naturally decayed.
            boolean covered=grassCover.containsKey(pos)&&grassCover.get(pos).equals(saved.get(pos.above()));
            BlockState after=manager?level.getBlockState(pos):preserveSourceDirt(before,target.state(),covered);
            if(manager){if(!TownHallInteractionPolicy.matches(after,target.state(),true)||!(level.getBlockEntity(pos) instanceof LecternBlockEntity)||!snapshot.contains("managerNbt",Tag.TAG_COMPOUND))throw new IOException("Management lectern changed during recovery");cells.add(new PaidConstructionRecovery.Cell(pos,List.of(after),after));}
            else {
                // A restart may derive stairs/fence shapes or a player may use a door
                // normally. Accept only this typed policy, never arbitrary air/material.
                BlockState live=level.getBlockState(pos);LinkedHashSet<BlockState> allowed=new LinkedHashSet<>(List.of(before,after));
                if(covered&&(live.equals(Blocks.DIRT.defaultBlockState())||live.equals(Blocks.GRASS_BLOCK.defaultBlockState()))){
                    allowed.add(live);
                    if(target.state().is(Blocks.GRASS_BLOCK))after=live; // Never repaint naturally derived ground.
                }
                if(TownHallInteractionPolicy.matches(live,before,false)||TownHallInteractionPolicy.matches(live,after,false))allowed.add(live);
                if(TownHallInteractionPolicy.matches(live,after,false))after=live;
                cells.add(new PaidConstructionRecovery.Cell(pos,List.copyOf(allowed),after));
            }
        }
        return List.copyOf(cells);
    }
    interface Checked {void run()throws IOException;}
    static void prepare(MinecraftServer server,Path path,Request r)throws IOException {
        Path root=ConstructionWorldAuthorization.authorize(server);var data=CatanSavedData.get(server);
        recordIntent(server.overworld(),data,root,path,r,()->{if(!root.equals(ConstructionWorldAuthorization.authorize(server)))throw new IOException("Construction world changed");},()->{if(!server.saveEverything(true,true,true))throw new IOException("Pre-construction save did not complete");});
    }
    private static void fixtureOnly(MinecraftServer server,Path root)throws IOException {
        if(!(server instanceof net.minecraft.gametest.framework.GameTestServer)||!root.toRealPath().startsWith(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toRealPath()))throw new IOException("Basic purchase fixture requires isolated GameTest storage");
    }
    static void fixturePrepare(MinecraftServer server,CatanSavedData data,Path root,Request r,Checked save)throws IOException {
        fixtureOnly(server,root);recordIntent(server.overworld(),data,root,file(root,r),r,()->fixtureOnly(server,root),save);
    }
    private static void recordIntent(ServerLevel level,CatanSavedData data,Path root,Path path,Request r,Checked auth,Checked save)throws IOException {
        if(!level.getServer().isSameThread())throw new IOException("Construction must start on server thread");
        request(describe(r));auth.run();if(!path.equals(file(root,r))||Files.exists(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Purchase path changed or original history already exists");
        var def=MapDefinitionManager.territory(r.city());ProducerService.validateMap(def);checkNew(data,r,def);Blueprint b=blueprint(r,def);
        for(var c:b.target())if(!def.siegeRegion().contains(c.position())||!def.restorationRegion().contains(c.position()))throw new IOException("Construction leaves approved city bounds");
        CompoundTag snapshot=capture(level,b),ledger=data.save(new CompoundTag());
        // Both observed world and ledger must survive a successful pre-purchase save.
        // No original intent, debit or construction write exists on a failure here.
        save.run();auth.run();checkNew(data,r,def);
        if(!snapshot.equals(capture(level,b))||!ledger.equals(data.save(new CompoundTag())))throw new IOException("Construction changed during pre-purchase save");
        CompoundTag intent=new CompoundTag();intent.putInt("schema",1);intent.putString("phase","PENDING");intent.putUUID("id",UUID.randomUUID());intent.putString("world",root.toString());intent.put("request",describe(r));intent.put("cost",DefenseTransaction.cost(price(r)));intent.put("snapshot",snapshot);intent.put("ledgerBefore",ledger);
        ProducerJournal.write(path,intent,false);
    }
    static boolean unfinished(Path file)throws IOException {byte[] bytes=PaidConstructionRecovery.readBounded(file,8*1024*1024);read(bytes);return !PaidConstructionRecovery.complete(file,bytes);}
    static CompoundTag read(byte[] bytes)throws IOException {CompoundTag n=ProducerJournal.read(bytes);if(n.getInt("schema")!=1||!n.getString("phase").equals("PENDING")||!n.hasUUID("id"))throw new IOException("Invalid basic-construction intent");request(n.getCompound("request"));return n;}
    static PaidConstructionRecovery.Environment environment(MinecraftServer server,Path path)throws IOException {
        Path root=ConstructionWorldAuthorization.authorize(server);var data=CatanSavedData.get(server);
        return environment(server,data,root,path,()->{if(!root.equals(ConstructionWorldAuthorization.authorize(server)))throw new IOException("Construction world changed");},()->{data.setDirty();if(!server.saveEverything(true,true,true))throw new IOException("Construction save did not complete");});
    }
    static PaidConstructionRecovery.Environment fixture(MinecraftServer server,CatanSavedData data,Path root,Path path,Checked save)throws IOException {
        fixtureOnly(server,root);return environment(server,data,root,path,()->fixtureOnly(server,root),save);
    }
    private static PaidConstructionRecovery.Environment environment(MinecraftServer server,CatanSavedData data,Path root,Path path,Checked authorizer,Checked save)throws IOException {
        byte[] bytes=PaidConstructionRecovery.readBounded(path,8*1024*1024);CompoundTag intent=read(bytes);Request r=request(intent.getCompound("request"));
        authorizer.run();
        if(!root.toString().equals(intent.getString("world"))||!path.equals(file(root,r)))throw new IOException("Foreign basic-construction intent");
        var def=MapDefinitionManager.territory(r.city());ProducerService.validateMap(def);var t=data.territory(r.city());var nation=data.nation(r.owner());
        if(t==null||nation==null||!r.owner().equals(t.ownerNationId()))throw new IOException("Construction ownership changed");
        var cost=DefenseService.historicalCost(intent.getCompound("cost"));Blueprint b=blueprint(r,def);UUID id=intent.getUUID("id");
        CompoundTag identity=describe(r);identity.putString("world",root.toString());identity.putUUID("transaction",id);
        return new PaidConstructionRecovery.Environment(){
            public ServerLevel level(){return server.overworld();}public Path original(){return path;}public byte[] originalBytes(){return bytes;}
            public CompoundTag identity(){return identity.copy();}public List<PaidConstructionRecovery.Cell> cells()throws IOException{return BasicConstructionPurchase.cells(level(),b,intent.getCompound("snapshot"));}
            public void authorize()throws IOException{
                authorizer.run();if(!r.owner().equals(t.ownerNationId())||data.nation(r.owner())!=nation||data.territory(r.city())!=t)throw new IOException("Construction world/owner changed");
                if(!def.equals(MapDefinitionManager.territory(r.city())))throw new IOException("Construction map changed");
                for(var c:b.target())if(!def.siegeRegion().contains(c.position())||!def.restorationRegion().contains(c.position()))throw new IOException("Construction leaves approved bounds");
            }
            private boolean source(){return r.kind().equals("hall")?t.cityLevel()==r.from()&&SchematicAssetRegistry.asset("th"+r.from()).placementToken().equals(data.placedMapAsset(stateKey(r))):data.placedMapAsset(stateKey(r))==null&&!IndustrialPlotService.occupied(t,r.plot())&&t.buildings().stream().noneMatch(v->v.type()==factory(r.asset()));}
            private boolean target(){return SchematicAssetRegistry.asset(r.asset()).placementToken().equals(data.placedMapAsset(stateKey(r)))&&(r.kind().equals("hall")?t.cityLevel()==r.to():t.buildings().stream().filter(v->v.type()==factory(r.asset())&&v.plotId().equals(r.plot())).count()==1);}
            public boolean paid()throws IOException{return PaidConstructionRecovery.paid(data,id,target(),source(),true);}
            public void checkPayment()throws IOException{
                authorize();if(paid())return;
                if(r.kind().equals("factory")&&(t.cityLevel()<factory(r.asset()).minCityLevel()||t.buildings().size()>=EconomyCatalog.maxBuildingSlots(t.cityLevel())||IndustrialPlotService.hasUnassignedLegacyIndustry(t)||def.reservedPlotIds().contains(r.plot())))throw new IOException("Factory requirements changed");
                if(!cost.canAfford(nation))throw new PreparationDeferred("Original construction price is still outstanding: "+cost.describe());
            }
            public void settle()throws IOException {checkPayment();if(paid())return;cost.charge(nation);if(r.kind().equals("hall"))t.setCityLevel(r.to());else t.buildings().add(new BuildingInstance(factory(r.asset()),1,factory(r.asset()).isProcessor()?100:0,r.plot()));data.setPlacedMapAsset(stateKey(r),SchematicAssetRegistry.asset(r.asset()).placementToken());PaidConstructionRecovery.receipt(data,id);data.setDirty();}
            public CompoundTag ledger(){return data.save(new CompoundTag());}
            public void save()throws IOException{data.setDirty();save.run();}
        };
    }
}
