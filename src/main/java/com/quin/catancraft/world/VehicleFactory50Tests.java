package com.quin.catancraft.world;

import com.mojang.authlib.GameProfile;
import com.quin.catancraft.data.BuildingType;
import com.quin.catancraft.map.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/** Disposable source/native block checks, not authenticated-client acceptance. */
@GameTestHolder("catancraft_factory50") @PrefixGameTestTemplate(false)
public final class VehicleFactory50Tests {
    private VehicleFactory50Tests(){}
    @GameTest(template="townhall_state_empty",batch="factory50_source",timeoutTicks=200)
    public static void actual_city_a_factory_counter_and_preserved_lane(GameTestHelper h)throws Exception {
        controlled(h);var definition=MapDefinitionManager.territory("a");VehicleFactory50.canonical(definition);
        var anchor=IndustrialPlotService.anchorFor(BuildingType.VEHICLE_FACTORY,"plot_1",definition);
        h.assertTrue(ShopFacility40.local(anchor,18,3,9).equals(VehicleFactory50.COUNTER),"Factory counter transform moved");
        var volume=BasicConstructionAssets.load("vehicle_factory");
        h.assertTrue(volume.at(18,3,9).is(Blocks.CRAFTING_TABLE)&&!volume.at(18,3,9).hasBlockEntity(),"Source counter changed");
        h.assertTrue(volume.at(18,2,10).isAir()&&volume.at(18,3,10).isAir(),"Counter standing space blocked");
        var area=VehicleFactory50.serviceArea(anchor);
        h.assertTrue(area.equals(new AABB(-814,81,-777,-794,86,-770)),"Service area left authored lane");
        int[][] counters={{-806,82,-780},{-806,82,-740},{-694,82,-728}};
        int index=0;
        for(String plot:IndustrialPlotService.INDUSTRIAL_PLOTS){
            MapAnchor actual=IndustrialPlotService.anchorFor(BuildingType.VEHICLE_FACTORY,plot,definition);
            int[] expected=counters[index++];
            h.assertTrue(ShopFacility40.local(actual,18,3,9).equals(new BlockPos(expected[0],expected[1],expected[2])),"Actual industrial-slot counter transform changed");
            AABB lane=VehicleFactory50.serviceArea(actual);
            for(int x=10;x<=29;x++)for(int z=12;z<=18;z++)for(int y=2;y<=6;y++){
                BlockPos p=ShopFacility40.local(actual,x,y,z);
                h.assertTrue(lane.contains(Vec3.atCenterOf(p)),"Refueling lane omits authored local cell after rotation");
            }
        }
        int count=0;
        for(int x=10;x<=29;x++)for(int z=12;z<=18;z++){
            h.assertTrue(volume.at(x,1,z).isFaceSturdy(h.getLevel(),h.absolutePos(BlockPos.ZERO),Direction.UP),"Authored lane floor changed");
            for(int y=2;y<=6;y++){h.assertTrue(volume.at(x,y,z).isAir(),"Authored lane clearance changed");count++;}
        }
        h.assertTrue(count==700,"Lane clearance inventory changed");
        refuse(()->VehicleFactory50.canonical(MapDefinitionManager.territory("i")));
        h.succeed();
    }
    @GameTest(template="townhall_state_empty",batch="factory50_access",timeoutTicks=300)
    public static void native_counter_ray_and_sixteen_step_route(GameTestHelper h)throws Exception {
        controlled(h);ServerLevel level=h.getLevel();var volume=BasicConstructionAssets.load("vehicle_factory");
        Map<BlockPos,BlockState> restore=new LinkedHashMap<>();
        try{
            for(int rotation:new int[]{0,180}){
                BlockPos origin=h.absolutePos(new BlockPos(rotation==0?40:90,10,40));
                MapAnchor a=new MapAnchor("factory50_disposable_route",origin.getX(),origin.getY(),origin.getZ(),rotation);
                var turn=rotation==0?Rotation.NONE:Rotation.CLOCKWISE_180;
                List<int[]> route=new ArrayList<>();for(int x=29;x>=18;x--)route.add(new int[]{x,15});for(int z=14;z>=10;z--)route.add(new int[]{18,z});
                List<int[]> columns=new ArrayList<>(route);columns.add(new int[]{18,9});
                for(int[] column:columns)for(int y=1;y<=3;y++){
                    BlockPos at=ShopFacility40.local(a,column[0],y,column[1]);level.getChunkAt(at);restore.putIfAbsent(at,level.getBlockState(at));
                    level.setBlock(at,volume.at(column[0],y,column[1]).rotate(turn),18);
                }
                h.assertTrue(route.size()==17,"Factory access is no longer16steps");
                for(int n=1;n<route.size();n++){
                    int[] p=route.get(n-1),q=route.get(n);Vec3 from=Vec3.atBottomCenterOf(ShopFacility40.local(a,p[0],2,p[1])),to=Vec3.atBottomCenterOf(ShopFacility40.local(a,q[0],2,q[1]));
                    AABB corridor=new AABB(Math.min(from.x,to.x)-.3,from.y,Math.min(from.z,to.z)-.3,Math.max(from.x,to.x)+.3,from.y+1.8,Math.max(from.z,to.z)+.3);
                    h.assertTrue(clear(level,corridor),"Native standing-body route is obstructed");
                }
                BlockPos counter=ShopFacility40.local(a,18,3,9),stand=ShopFacility40.local(a,18,2,10);
                Vec3 eye=Vec3.atBottomCenterOf(stand).add(0,1.62,0);var ray=level.clip(new ClipContext(eye,Vec3.atCenterOf(counter),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,null));
                h.assertTrue(eye.distanceToSqr(Vec3.atCenterOf(counter))<=16&&ray.getType()==HitResult.Type.BLOCK&&ray.getBlockPos().equals(counter),"Native reach/ray does not reach counter");
                level.setBlock(stand.above(),Blocks.STONE.defaultBlockState(),18);
                var blocked=level.clip(new ClipContext(eye,Vec3.atCenterOf(counter),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,null));
                h.assertTrue(blocked.getType()!=HitResult.Type.BLOCK||!blocked.getBlockPos().equals(counter),"Intervening block still grants sightline");
            }
        }finally{restore.forEach((at,state)->level.setBlock(at,state,18));}
        var fake=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"Factory50Refusal"));
        h.assertTrue(VehicleFactory50.recognizes(fake,VehicleFactory50.COUNTER),"Physical counter recognition changed");
        h.assertTrue(!VehicleFactory50.recognizes(fake,ShopFacility40.STARTER)&&!VehicleFactory50.recognizes(fake,new BlockPos(-805,82,-771)),"Vehicle counter shadows another shop");
        refuse(()->VehicleFactory50.open(fake,VehicleFactory50.COUNTER));
        h.succeed();
    }
    @GameTest(template="townhall_state_empty",batch="factory50_envelope",timeoutTicks=600)
    public static void whole_authored_envelope_accepts_barrels_and_rejects_drift(GameTestHelper h)throws Exception {
        controlled(h);MapAnchor a=IndustrialPlotService.anchorFor(BuildingType.VEHICLE_FACTORY,"plot_1",MapDefinitionManager.territory("a"));
        AABB bounds=new AABB(a.x()-29,a.y()-2,a.z()-22,a.x()+11,a.y()+8,a.z()+24);
        SectorChunkLease lease=SectorChunkLease.acquire(h.getLevel(),bounds);
        envelopeWhenReady(h,a,lease,0);
    }
    private static void envelopeWhenReady(GameTestHelper h,MapAnchor a,SectorChunkLease lease,int waited){
        try{
            lease.keepAlive();
            if(!lease.ready()){
                if(waited>=400)throw new IOException("Factory fixture entity residency timed out");
                h.runAfterDelay(5,()->envelopeWhenReady(h,a,lease,waited+5));return;
            }
            var level=h.getLevel();var data=com.quin.catancraft.data.CatanSavedData.get(level.getServer());data.syncMapDefinitions();
            var city=data.territory("a");UUID original=city.ownerNationId(),owner=UUID.randomUUID();
            Map<BlockPos,BlockState> restore=new LinkedHashMap<>();
            try{
                city.setOwnerNationId(owner);
                BlockPos barrel=null;
                for(int x=NativeVehicleBay46.MIN_X;x<=NativeVehicleBay46.MAX_X;x++)for(int y=NativeVehicleBay46.MIN_Y;y<=NativeVehicleBay46.MAX_Y;y++)for(int z=NativeVehicleBay46.MIN_Z;z<=NativeVehicleBay46.MAX_Z;z++){
                    BlockPos p=NativeVehicleBay46.sourceCell(a,x,y,z);var source=NativeVehicleBay46.source(a,p);
                    restore.put(p,level.getBlockState(p));level.setBlock(p,source,18);
                    if(source.is(Blocks.BARREL))barrel=p;
                }
                h.assertTrue(restore.size()==18400&&barrel!=null,"Factory full-source fixture/barrel inventory changed");
                var container=(net.minecraft.world.level.block.entity.BarrelBlockEntity)level.getBlockEntity(barrel);
                container.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,3));
                VehicleFactory50.verifyEnvelope(level,a,owner);
                h.assertTrue(container.getItem(0).getCount()==3,"Envelope verification altered authored barrel contents");
                BlockPos road=NativeVehicleBay46.sourceCell(a,4,0,0);var saved=level.getBlockState(road);
                level.setBlock(road,Blocks.STONE.defaultBlockState(),18);refuse(()->VehicleFactory50.verifyEnvelope(level,a,owner));level.setBlock(road,saved,18);
                VehicleFactory50.verifyEnvelope(level,a,owner);
                city.setOwnerNationId(UUID.randomUUID());refuse(()->VehicleFactory50.verifyEnvelope(level,a,owner));
            }finally{city.setOwnerNationId(original);restore.forEach((at,state)->level.setBlock(at,state,18));lease.close();}
            h.succeed();
        }catch(Exception failure){lease.close();h.fail(failure.toString());}
    }
    private static boolean clear(ServerLevel level,AABB box){for(var shape:level.getBlockCollisions(null,box))if(!shape.isEmpty())return false;return true;}
    private interface Checked{void run()throws Exception;}
    private static void refuse(Checked work)throws Exception{try{work.run();}catch(IOException expected){return;}throw new AssertionError("Expected closed authority");}
    private static void controlled(GameTestHelper h)throws Exception {
        var level=h.getLevel();var root=level.getServer().getWorldPath(LevelResource.ROOT).toRealPath();
        if(!(level.getServer() instanceof GameTestServer)||!level.getServer().getPlayerList().getPlayers().isEmpty()
                ||!root.getFileName().toString().equals("world")||!root.getParent().getFileName().toString().equals("controlled-catancraft_factory50-run")
                ||!Files.readString(root.getParent().resolve(".catancraft-controlled-fixture-v1")).equals("CatanCraft synthetic flat fixture v1\n"))throw new IOException("Only marked disposable factory50 fixture is allowed");
    }
}
