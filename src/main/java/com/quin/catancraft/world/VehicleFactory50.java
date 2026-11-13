package com.quin.catancraft.world;

import com.quin.catancraft.command.NationProcurement;
import com.quin.catancraft.data.*;
import com.quin.catancraft.map.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Physical authority for City A factory counters and their authored internal lanes.
 * New purchase departure authority remains restricted to original plot1.
 * Reads current authored source and native geometry; never loads chunks, repairs
 * blocks, moves vehicles, or substitutes another factory/bay. Driving qualification
 * is a separate release gate, not inferred from this authority's source checks. */
final class VehicleFactory50 {
    static final BlockPos COUNTER = new BlockPos(-806,82,-780);
    static final String REVISION = "city_a_vehicle_factory50_v1";
    static final String SOURCE_SHA = "793e2a277fcb7287ba76298d2080808e315bcf626f76e9fe8f9ce5143e731649";
    private static final String DIMENSION = "minecraft:overworld";
    record Binding(CompoundTag identity, String title, int level, BlockPos counter,
                   MapAnchor anchor, UUID nation, Path root) {
        Binding { identity=identity.copy();counter=counter.immutable();Objects.requireNonNull(anchor);Objects.requireNonNull(nation);Objects.requireNonNull(root); }
        public CompoundTag identity(){return identity.copy();}
    }
    private VehicleFactory50(){}
    static boolean recognizes(ServerPlayer player, BlockPos pos) {
        return player != null && DIMENSION.equals(player.serverLevel().dimension().location().toString()) && Set.of(COUNTER,new BlockPos(-806,82,-740),new BlockPos(-694,82,-728)).contains(pos);
    }
    static Binding open(ServerPlayer player, BlockPos counter)throws IOException {
        ShopFacility40.actor(player);
        if(!recognizes(player,counter))throw new IOException("Visit the original City A Vehicle Factory counter.");
        Path root=ConstructionWorldAuthorization.authorize(player.server).toRealPath();
        TerritoryDefinition definition=MapDefinitionManager.territory("a");
        canonical(definition);
        NationData nation=ShopFacility40.leader(player);
        CatanSavedData data=CatanSavedData.get(player.server);
        TerritoryData city=data.territory("a");
        if(city==null||!nation.id().equals(city.ownerNationId())||city.cityLevel()<1)
            throw new IOException("Your nation must own the completed City A Town Hall.");
        if(SectorService.hasActiveOperation(player.server)||CityBasePreparation.isRunning(player.server))
            throw new IOException("City construction is active; return when it finishes.");
        PaidRecoveryService.requireClear(player.server,"a");
        TownHallRepairService.requireNoPending(player.server,"a");
        for(String file:List.of("catancraft_siege38_a.dat","catancraft_snapshot39_a.dat","catancraft_restore39_a.dat"))
            if(Files.exists(root.resolve("data").resolve(file),LinkOption.NOFOLLOW_LINKS))
                throw new IOException("City siege or restoration history requires review before vehicle service.");
        List<BuildingInstance> factories=city.buildings().stream().filter(b->b.type()==BuildingType.VEHICLE_FACTORY).toList();
        if(factories.size()!=1)throw new IOException("A unique completed Vehicle Factory is required.");
        BuildingInstance factory=factories.get(0);
        String plot=IndustrialPlotService.occupiedPlot(factory);
        if(!IndustrialPlotService.INDUSTRIAL_PLOTS.contains(plot)||definition.reservedPlotIds().contains(plot)
                ||city.buildings().stream().filter(b->plot.equals(IndustrialPlotService.occupiedPlot(b))).count()!=1)
            throw new IOException("The completed factory plot is ambiguous or reserved.");
        if(factory.level()<1||factory.level()>5)throw new IOException("Invalid completed Vehicle Factory level.");
        MapAnchor anchor=IndustrialPlotService.anchorFor(BuildingType.VEHICLE_FACTORY,plot,definition);
        canonicalFactory(anchor,plot);
        if(!ShopFacility40.local(anchor,18,3,9).equals(counter))throw new IOException("Visit the counter inside your completed Vehicle Factory.");
        String token=data.placedMapAsset("territory:a:"+plot);
        if(!SchematicAssetRegistry.asset("vehicle_factory").placementToken().equals(token))
            throw new IOException("Vehicle Factory construction is incomplete.");
        if(!definition.toBoundary().contains(DIMENSION,player.blockPosition().getX(),player.blockPosition().getZ()))
            throw new IOException("Return to the counter inside your city.");
        loaded(player.serverLevel(),counter);
        double distance=player.getEyePosition().distanceToSqr(Vec3.atCenterOf(counter));
        if(!Double.isFinite(distance)||distance>16)throw new IOException("Move within four blocks of the counter.");
        var hit=player.serverLevel().clip(new ClipContext(player.getEyePosition(),Vec3.atCenterOf(counter),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        if(hit.getType()!=HitResult.Type.BLOCK||!counter.equals(hit.getBlockPos()))throw new IOException("The counter must be in clear reach.");
        verifyCounter(player.serverLevel(),anchor);
        CompoundTag tag=new CompoundTag();
        tag.putInt("schema",1);tag.putString("revision",REVISION);tag.putString("world",root.toString());
        tag.putString("worldId",Files.exists(root.resolve(FreshDemoAuthorization.MARKER),LinkOption.NOFOLLOW_LINKS)?FreshDemoAuthorization.worldId(player.server,root):"authorized_historical_r7");
        tag.putString("levelName",player.server.getWorldData().getLevelName());tag.putLong("seed",player.serverLevel().getSeed());
        tag.putString("dimension",DIMENSION);tag.putString("city","a");tag.putString("plot",plot);tag.putUUID("owner",nation.id());
        tag.putString("sourceToken",token);tag.putString("sourceSHA256",SOURCE_SHA);tag.putInt("level",factory.level());
        tag.putLong("counter",counter.asLong());tag.putIntArray("anchor",new int[]{anchor.x(),anchor.y(),anchor.z(),anchor.rotation()});
        return new Binding(tag,"City A Vehicle Factory · Level "+factory.level(),factory.level(),counter,anchor,nation.id(),root);
    }
    static Binding recheck(ServerPlayer player,CompoundTag original)throws IOException {
        if(original==null||!original.contains("counter",Tag.TAG_LONG))throw new IOException("Incomplete vehicle counter selection.");
        Binding current=open(player,BlockPos.of(original.getLong("counter")));
        if(!current.identity().equals(original))throw new IOException("Vehicle factory, nation or level changed; reopen the counter.");
        return current;
    }
    static Binding revalidate(ServerPlayer player,Binding binding)throws IOException {
        Binding current=recheck(player,binding.identity());
        if(!sameAnchor(current.anchor(),binding.anchor())||!current.counter().equals(binding.counter())
                ||!current.nation().equals(binding.nation())||!current.root().equals(binding.root())||current.level()!=binding.level())
            throw new IOException("Vehicle factory binding differs from current authority.");
        return current;
    }
    static List<Entity> serviceVehicles(ServerPlayer player,Binding binding)throws IOException {
        Binding current=revalidate(player,binding);ServerLevel level=player.serverLevel();
        AABB area=serviceArea(current.anchor());loadedArea(level,area);
        List<Entity> result=new ArrayList<>();
        for(Entity vehicle:level.getEntities((Entity)null,area,e->e.isAlive())) {
            if(specOrNull(vehicle)==null)continue;
            try{checkVehicleCurrent(level,current,vehicle);result.add(vehicle);}catch(IOException unavailable){/* unavailable vehicles are not offered */}
        }
        result.sort(Comparator.comparing(e->e.getUUID().toString()));
        if(result.size()>16)throw new IOException("Too many vehicles in service area; clear the bay first.");
        return List.copyOf(result);
    }
    static void checkVehicle(ServerPlayer player,Binding binding,Entity vehicle)throws IOException {
        Binding current=revalidate(player,binding);checkVehicleCurrent(player.serverLevel(),current,vehicle);
    }
    private static void checkVehicleCurrent(ServerLevel level,Binding binding,Entity vehicle)throws IOException {
        if(vehicle==null||vehicle.level()!=level||!vehicle.isAlive()||vehicle.isRemoved()||level.getEntity(vehicle.getUUID())!=vehicle
                ||vehicle.isPassenger()||!vehicle.getPassengers().isEmpty())throw new IOException("Park and leave the selected vehicle before refueling.");
        var data=vehicle.getPersistentData();
        if(!data.contains(NationProcurement.PURCHASED_TAG,Tag.TAG_BYTE)||!data.getBoolean(NationProcurement.PURCHASED_TAG)
                ||!data.contains(NationProcurement.NATION_TAG,Tag.TAG_STRING)||!binding.nation().toString().equals(data.getString(NationProcurement.NATION_TAG)))
            throw new IOException("This vehicle is not a recorded purchase owned by your nation.");
        Vec3 velocity=vehicle.getDeltaMovement();
        if(!vehicle.onGround()||!Double.isFinite(velocity.lengthSqr())||velocity.lengthSqr()>0.0001)
            throw new IOException("Stop the selected vehicle before refueling.");
        NativeVehicleBay46.Spec spec=specOrNull(vehicle);if(spec==null)throw new IOException("Unsupported vehicle type.");
        verifyDefinition(level,spec);
        AABB area=serviceArea(binding.anchor());loadedArea(level,area);
        try{
            List<AABB> bounds=NativeVehicleDriving48.bounds(vehicle);
            if(bounds.size()!=spec.boxes())throw new IOException("Native vehicle geometry changed.");
            for(AABB box:bounds){
                if(!finite(box)||box.getXsize()<=0||box.getYsize()<=0||box.getZsize()<=0||!contains(area,box))
                    throw new IOException("Park the whole vehicle inside the marked factory lane.");
                clearBox(level,binding.anchor(),box,vehicle);
            }
        }catch(IOException e){throw e;}catch(Exception e){throw new IOException("Cannot verify native vehicle service geometry.",e);}
    }
    /** This is the existing surveyed straight lane, not a new placed pad. */
    static AABB serviceArea(MapAnchor anchor){
        if(anchor.rotation()!=0&&anchor.rotation()!=180)throw new IllegalArgumentException("Unsupported factory orientation");
        return anchor.rotation()==0?new AABB(anchor.x()-19,anchor.y(),anchor.z()-3,anchor.x()+1,anchor.y()+5,anchor.z()+4)
                :new AABB(anchor.x(),anchor.y(),anchor.z()-3,anchor.x()+20,anchor.y()+5,anchor.z()+4);
    }
    static NativeVehicleBay46.Spec spec(String offer)throws IOException {
        return switch(offer){case "pickup"->NativeVehicleBay46.PICKUP;case "lav150"->NativeVehicleBay46.LAV;default->throw new IOException("Unknown vehicle offer.");};
    }
    private static NativeVehicleBay46.Spec specOrNull(Entity vehicle){
        String id=String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(vehicle.getType()));
        return id.equals("superbwarfare:sodayo_pick_up")?NativeVehicleBay46.PICKUP:id.equals("superbwarfare:lav_150")?NativeVehicleBay46.LAV:null;
    }
    static void verifyDefinition(ServerLevel level,NativeVehicleBay46.Spec spec)throws IOException {
        var resource=new net.minecraft.resources.ResourceLocation("superbwarfare","sbw/vehicles/"+spec.id()+".json");
        try(var in=level.getServer().getResourceManager().getResourceOrThrow(resource).open()){
            byte[] bytes=in.readNBytes(200001);if(bytes.length>200000||!TownHallRevisions.sha(bytes).equals(spec.sha()))throw new IOException("Native vehicle definition differs from the qualified version.");
        }
    }
    /** Definition identity only, for comparison during recovery. Confers no occupancy permission. */
    static VehicleBay45.Bay bayIdentityFor(ServerPlayer player,Binding binding,String offer)throws IOException {
        Binding current=revalidate(player,binding);return buildBay(player.serverLevel(),current,spec(offer));
    }
    static VehicleBay45.Bay bayFor(ServerPlayer player,Binding binding,String offer)throws IOException {
        Binding current=revalidate(player,binding);VehicleBay45.Bay bay=buildBay(player.serverLevel(),current,spec(offer));
        validateBayCurrent(player,current,bay,null);return bay;
    }
    static void validateBay(ServerPlayer player,Binding binding,VehicleBay45.Bay bay,Entity ignored)throws IOException {
        Binding current=revalidate(player,binding);
        NativeVehicleBay46.Spec spec=bay.vehicle().equals("superbwarfare:sodayo_pick_up")?NativeVehicleBay46.PICKUP:bay.vehicle().equals("superbwarfare:lav_150")?NativeVehicleBay46.LAV:null;
        if(spec==null||!buildBay(player.serverLevel(),current,spec).equals(bay))throw new IOException("Original fixed bay identity changed.");
        validateBayCurrent(player,current,bay,ignored);
    }
    /** Whole authored envelope masks deliberately exceed the driven swept path.
     * Every source state is checked separately; solid landmarks are never cleared.
     * The release's native driving proof must remain inside this same envelope. */
    static VehicleBay45.Bay buildBay(ServerLevel level,Binding binding,NativeVehicleBay46.Spec spec)throws IOException {
        canonicalAnchor(binding.anchor());verifyDefinition(level,spec);
        Set<VehicleBay45.Cell> support=new HashSet<>(),clearance=new HashSet<>();MapAnchor a=binding.anchor();
        for(int x=NativeVehicleBay46.MIN_X;x<=NativeVehicleBay46.MAX_X;x++)for(int z=NativeVehicleBay46.MIN_Z;z<=NativeVehicleBay46.MAX_Z;z++){
            BlockPos floor=NativeVehicleBay46.sourceCell(a,x,-1,z);loaded(level,floor);
            var floorSource=NativeVehicleBay46.source(a,floor);
            if(floorSource.isCollisionShapeFullBlock(level,floor)&&floorSource.getFluidState().isEmpty())support.add(new VehicleBay45.Cell(x,-1,z));
            for(int y=0;y<=NativeVehicleBay46.MAX_Y;y++){
                BlockPos at=NativeVehicleBay46.sourceCell(a,x,y,z);loaded(level,at);var source=NativeVehicleBay46.source(a,at);
                if(source.getCollisionShape(level,at).isEmpty()&&source.getFluidState().isEmpty())clearance.add(new VehicleBay45.Cell(x,y,z));
            }
        }
        VehicleBay45.Facility f=new VehicleBay45.Facility("a","plot_1",binding.nation(),DIMENSION,REVISION,SOURCE_SHA,
                new VehicleBay45.Cell(a.x(),a.y(),a.z()),a.rotation(),new VehicleBay45.Cell(-11,1,-6),binding.level());
        // Explicit broad masks include boarding/departure space; current source and
        // native geometry validation are still mandatory, not implied by masks.
        return new VehicleBay45.Bay("plot1_native50",f,"superbwarfare:"+spec.id(),spec.sha(),
                new VehicleBay45.Position(-11.5,.1,.5),270,support,clearance,clearance,clearance);
    }
    private static void validateBayCurrent(ServerPlayer player,Binding binding,VehicleBay45.Bay bay,Entity ignored)throws IOException {
        ServerLevel level=player.serverLevel();MapAnchor a=binding.anchor();
        verifyEnvelope(level,a,binding.nation());
        AABB whole=new AABB(a.x()+NativeVehicleBay46.MIN_X,a.y(),a.z()+NativeVehicleBay46.MIN_Z,
                a.x()+NativeVehicleBay46.MAX_X+1,a.y()+NativeVehicleBay46.MAX_Y+1,a.z()+NativeVehicleBay46.MAX_Z+1);
        if(!level.getEntities(ignored,whole,e->e.isAlive()&&e!=player).isEmpty())throw new IOException("The factory departure area is occupied; clear it before buying.");
        Entity candidate=null;
        try{
            var s=bay.vehicle().endsWith(":sodayo_pick_up")?NativeVehicleBay46.PICKUP:NativeVehicleBay46.LAV;
            candidate=NativeVehicleBay46.create(level,s);NativeVehicleBay46.pose(candidate,a,0);
            for(AABB box:NativeVehicleBay46.boxes(candidate,s))clearBox(level,a,box,ignored);
        }catch(IOException e){throw e;}catch(Exception e){throw new IOException("Cannot verify initial native vehicle geometry.",e);}
        finally{if(candidate!=null)candidate.discard();}
    }
    static void verifyEnvelope(ServerLevel level,MapAnchor anchor,UUID nation)throws IOException {
        var definition=MapDefinitionManager.territory("a");var city=CatanSavedData.get(level.getServer()).territory("a");
        if(city==null||!nation.equals(city.ownerNationId()))throw new IOException("Factory ownership changed.");
        for(int x=NativeVehicleBay46.MIN_X;x<=NativeVehicleBay46.MAX_X;x++)for(int y=NativeVehicleBay46.MIN_Y;y<=NativeVehicleBay46.MAX_Y;y++)for(int z=NativeVehicleBay46.MIN_Z;z<=NativeVehicleBay46.MAX_Z;z++){
            BlockPos p=NativeVehicleBay46.sourceCell(anchor,x,y,z);loaded(level,p);
            var expected=NativeVehicleBay46.source(anchor,p);var blockEntity=level.getBlockEntity(p);
            // Authored barrels are legitimate inventories. Verify their block/type,
            // preserve contents, and never require an empty or synthetic source NBT.
            boolean entityMatches=expected.hasBlockEntity()?blockEntity!=null&&blockEntity.getType().isValid(expected):blockEntity==null;
            if(!definition.toBoundary().contains(DIMENSION,p.getX(),p.getZ())||!entityMatches||!level.getBlockState(p).equals(expected))
                throw new IOException("Factory departure source, ownership or block entity changed at "+p+".");
        }
    }
    private static void clearBox(ServerLevel level,MapAnchor anchor,AABB box,Entity ignored)throws IOException {
        for(BlockPos p:BlockPos.betweenClosed((int)Math.floor(box.minX),(int)Math.floor(box.minY),(int)Math.floor(box.minZ),(int)Math.floor(box.maxX),(int)Math.floor(box.maxY),(int)Math.floor(box.maxZ))){
            loaded(level,p);var state=level.getBlockState(p);
            if(!state.equals(NativeVehicleBay46.source(anchor,p))||!state.getFluidState().isEmpty()||level.getBlockEntity(p)!=null
                    ||state.getCollisionShape(level,p).toAabbs().stream().anyMatch(b->b.move(p).intersects(box)))throw new IOException("Vehicle space is blocked or changed at "+p+".");
        }
        for(int x=(int)Math.floor(box.minX);x<=Math.floor(box.maxX);x++)for(int z=(int)Math.floor(box.minZ);z<=Math.floor(box.maxZ);z++){
            BlockPos floor=new BlockPos(x,anchor.y()-1,z);loaded(level,floor);var state=level.getBlockState(floor);
            if(!state.equals(NativeVehicleBay46.source(anchor,floor))||!state.isCollisionShapeFullBlock(level,floor)||!state.getFluidState().isEmpty())throw new IOException("Vehicle footprint is unsupported.");
        }
        if(!level.getEntities(ignored,box,e->e.isAlive()).isEmpty())throw new IOException("Vehicle space is occupied.");
    }
    static void verifyCounter(ServerLevel level,MapAnchor anchor)throws IOException {
        var source=BasicConstructionAssets.load("vehicle_factory");
        List<BlockPos> cells=new ArrayList<>();cells.add(new BlockPos(18,3,9));
        for(int x=18;x<=29;x++)for(int y=1;y<=3;y++)cells.add(new BlockPos(x,y,15));
        for(int z=10;z<15;z++)for(int y=1;y<=3;y++)cells.add(new BlockPos(18,y,z));
        for(BlockPos local:cells){
            BlockPos p=ShopFacility40.local(anchor,local.getX(),local.getY(),local.getZ());loaded(level,p);
            if(level.getBlockEntity(p)!=null||!level.getBlockState(p).equals(source.at(local.getX(),local.getY(),local.getZ()).rotate(ProducerExteriorR2.rotation(anchor))))
                throw new IOException("The factory counter or its access path has changed.");
        }
    }
    static void canonical(TerritoryDefinition definition)throws IOException {
        ProducerService.validateMap(definition);MapAnchor manager=definition.managementAnchor();
        if(!definition.id().equals("a")||!DIMENSION.equals(definition.dimension())||manager==null||manager.x()!=-750||manager.y()!=84||manager.z()!=-773||manager.rotation()!=0)
            throw new IOException("Factory is outside approved City A.");
        for(String plot:IndustrialPlotService.INDUSTRIAL_PLOTS)canonicalFactory(IndustrialPlotService.anchorFor(BuildingType.VEHICLE_FACTORY,plot,definition),plot);
    }
    private static void canonicalFactory(MapAnchor anchor,String plot)throws IOException {
        int x=plot.equals("plot_4")?-705:-795,z=plot.equals("plot_1")?-774:-734;
        if(anchor==null||anchor.x()!=x||anchor.y()!=81||anchor.z()!=z||anchor.rotation()!=(plot.equals("plot_4")?180:0))
            throw new IOException("Factory placement differs from approved City A map.");
    }
    private static void canonicalAnchor(MapAnchor anchor)throws IOException {
        if(anchor==null||anchor.x()!=-795||anchor.y()!=81||anchor.z()!=-774||anchor.rotation()!=0)throw new IOException("Unsupported factory placement; only original City A plot 1 is surveyed.");
    }
    private static boolean sameAnchor(MapAnchor a,MapAnchor b){return a!=null&&b!=null&&a.id().equals(b.id())&&a.x()==b.x()&&a.y()==b.y()&&a.z()==b.z()&&a.rotation()==b.rotation();}
    static void loaded(ServerLevel level,BlockPos p)throws IOException {
        ChunkPos chunk=new ChunkPos(p);if(level.getChunkSource().getChunkNow(chunk.x,chunk.z)==null||!level.areEntitiesLoaded(chunk.toLong()))throw new IOException("Factory blocks or entities are not fully loaded.");
    }
    private static void loadedArea(ServerLevel level,AABB area)throws IOException {
        for(int x=((int)Math.floor(area.minX))>>4;x<=((int)Math.floor(area.maxX))>>4;x++)for(int z=((int)Math.floor(area.minZ))>>4;z<=((int)Math.floor(area.maxZ))>>4;z++)loaded(level,new BlockPos(x*16,(int)area.minY,z*16));
    }
    private static boolean contains(AABB area,AABB b){return b.minX>=area.minX&&b.minY>=area.minY&&b.minZ>=area.minZ&&b.maxX<=area.maxX&&b.maxY<=area.maxY&&b.maxZ<=area.maxZ;}
    private static boolean finite(AABB b){return Double.isFinite(b.minX)&&Double.isFinite(b.minY)&&Double.isFinite(b.minZ)&&Double.isFinite(b.maxX)&&Double.isFinite(b.maxY)&&Double.isFinite(b.maxZ);}
}
