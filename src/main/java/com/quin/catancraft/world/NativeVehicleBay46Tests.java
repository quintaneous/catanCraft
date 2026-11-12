package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.map.MapAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@GameTestHolder("catancraft_vehicle46_native") @PrefixGameTestTemplate(false)
public final class NativeVehicleBay46Tests {
    @GameTest(template="townhall_state_empty",batch="vehicle46_native01",timeoutTicks=600)
    public static void pickup_zero_authored_junction_native_geometry(GameTestHelper h)throws Exception{qualify(h,0,0,NativeVehicleBay46.PICKUP);}
    @GameTest(template="townhall_state_empty",batch="vehicle46_native02",timeoutTicks=600)
    public static void pickup_rotated_authored_junction_native_geometry(GameTestHelper h)throws Exception{qualify(h,1,180,NativeVehicleBay46.PICKUP);}
    @GameTest(template="townhall_state_empty",batch="vehicle46_native03",timeoutTicks=600)
    public static void lav_zero_authored_junction_native_geometry(GameTestHelper h)throws Exception{qualify(h,2,0,NativeVehicleBay46.LAV);}
    @GameTest(template="townhall_state_empty",batch="vehicle46_native04",timeoutTicks=600)
    public static void lav_rotated_authored_junction_native_geometry(GameTestHelper h)throws Exception{qualify(h,3,180,NativeVehicleBay46.LAV);}
    private static void qualify(GameTestHelper h,int index,int rotation,NativeVehicleBay46.Spec spec)throws Exception {
        MapAnchor a=stage(h,index,rotation);
        var proof=NativeVehicleBay46.inspect(h.getLevel(),a,spec);
        require(proof.boxes()==spec.boxes()&&proof.seats()==spec.seats()&&proof.sweepSamples()==NativeVehicleBay46.ROUTE_SAMPLES&&proof.externalRoadQualified(),"Incorrect qualification scope");
        require(proof.seatResults().size()==spec.seats(),"Not every final native seat exit was observed");
        for(var seat:proof.seatResults())CatanCraft.LOGGER.info("VEHICLE46_NATIVE_SEAT vehicle={} rotation={} seat={} finalExit={} blockers={}",spec.id(),rotation,seat.seat(),seat.finalExit(),seat.blockers());
        if(spec.equals(NativeVehicleBay46.PICKUP)){
            require(!proof.dismountsQualified()&&proof.seatResults().stream().allMatch(seat->seat.blockers().size()==1&&seat.blockers().get(0).startsWith("Native final dismount intersects own vehicle OBB ")),"Pinned pickup final-exit blocker inventory changed");
            refuse(()->NativeVehicleBay46.requireQualifiedDismounts(proof));
        }else NativeVehicleBay46.requireQualifiedDismounts(proof);
        landmarks(h,a);
        CatanCraft.LOGGER.info("VEHICLE46_NATIVE_DISMOUNT vehicle={} rotation={} checkedSeats={} blockedSeats={} qualified={} firstSeatBoarded=true fullBayQualified=false",spec.id(),rotation,proof.seats(),proof.seatResults().stream().filter(seat->!seat.blockers().isEmpty()).count(),proof.dismountsQualified());
        CatanCraft.LOGGER.info("VEHICLE46_NATIVE_PASS vehicle={} rotation={} obbs={} seats={} sweepSamples={} checkedCells={} bounds={} externalRoadQualified=true northAndSouth=true authoredLandmarks=true",proof.id(),rotation,proof.boxes(),proof.seats(),proof.sweepSamples(),proof.checkedCells(),proof.initialBounds());h.succeed();
    }
    @GameTest(template="townhall_state_empty",batch="vehicle46_native05",timeoutTicks=600)
    public static void native_full_envelope_rejects_block_fluid_support_and_entity_hazards(GameTestHelper h)throws Exception {
        MapAnchor a=stage(h,4,0);Entity vehicle=NativeVehicleBay46.create(h.getLevel(),NativeVehicleBay46.PICKUP);
        try{
            NativeVehicleBay46.pose(vehicle,a,0);var boxes=NativeVehicleBay46.boxes(vehicle,NativeVehicleBay46.PICKUP);NativeVehicleBay46.clear(h.getLevel(),vehicle,a,boxes);
            BlockPos body=BlockPos.containing(boxes.get(0).getCenter());var original=h.getLevel().getBlockState(body);
            for(var hazard:List.of(Blocks.STONE.defaultBlockState(),Blocks.WATER.defaultBlockState())){
                h.getLevel().setBlock(body,hazard,2);refuse(()->NativeVehicleBay46.clear(h.getLevel(),vehicle,a,boxes));h.getLevel().setBlock(body,original,2);
            }
            BlockPos floor=new BlockPos(body.getX(),a.y()-1,body.getZ());var support=h.getLevel().getBlockState(floor);h.getLevel().setBlock(floor,Blocks.AIR.defaultBlockState(),2);
            refuse(()->NativeVehicleBay46.clear(h.getLevel(),vehicle,a,boxes));h.getLevel().setBlock(floor,support,2);
            var center=boxes.get(0).getCenter();ItemEntity blocker=new ItemEntity(h.getLevel(),center.x,center.y,center.z,new ItemStack(Items.DIAMOND));
            require(h.getLevel().addFreshEntity(blocker),"Could not install controlled entity obstruction");
            try{refuse(()->NativeVehicleBay46.clear(h.getLevel(),vehicle,a,boxes));}finally{blocker.discard();}
            NativeVehicleBay46.clear(h.getLevel(),vehicle,a,boxes);
            // A full solid replacement still refuses: collision safety alone is not exact-source authority.
            h.getLevel().setBlock(floor,Blocks.STONE.defaultBlockState(),2);
            refuse(()->NativeVehicleBay46.clear(h.getLevel(),vehicle,a,boxes));h.getLevel().setBlock(floor,support,2);
            NativeVehicleBay46.clear(h.getLevel(),vehicle,a,boxes);
            // The same current-world checks apply after leaving the factory onto the authored road.
            NativeVehicleBay46.pose(vehicle,a,16);var roadBoxes=NativeVehicleBay46.boxes(vehicle,NativeVehicleBay46.PICKUP);
            NativeVehicleBay46.clear(h.getLevel(),vehicle,a,roadBoxes);
            BlockPos roadBody=BlockPos.containing(roadBoxes.get(0).getCenter());var roadAir=h.getLevel().getBlockState(roadBody);
            h.getLevel().setBlock(roadBody,Blocks.STONE.defaultBlockState(),2);
            refuse(()->NativeVehicleBay46.clear(h.getLevel(),vehicle,a,roadBoxes));h.getLevel().setBlock(roadBody,roadAir,2);
            NativeVehicleBay46.clear(h.getLevel(),vehicle,a,roadBoxes);NativeVehicleBay46.pose(vehicle,a,0);
            var passenger=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"BayHazard46"));
            var exit=(net.minecraft.world.phys.Vec3)vehicle.getClass().getMethod("getDismountLocationForIndex",net.minecraft.world.entity.LivingEntity.class,int.class).invoke(vehicle,passenger,0);
            var capsule=new net.minecraft.world.phys.AABB(exit.x-.3,exit.y,exit.z-.3,exit.x+.3,exit.y+1.8,exit.z+.3);
            NativeVehicleBay46.clearPassenger(h.getLevel(),a,capsule,exit);
            BlockPos edge=new BlockPos((int)Math.floor(capsule.minX),a.y()-1,(int)Math.floor(capsule.minZ));var edgeState=h.getLevel().getBlockState(edge);
            h.getLevel().setBlock(edge,Blocks.AIR.defaultBlockState(),2);refuse(()->NativeVehicleBay46.clearPassenger(h.getLevel(),a,capsule,exit));h.getLevel().setBlock(edge,edgeState,2);
            ItemEntity exitBlocker=new ItemEntity(h.getLevel(),exit.x,exit.y+.5,exit.z,new ItemStack(Items.DIAMOND));require(h.getLevel().addFreshEntity(exitBlocker),"Could not install exit obstruction");
            try{refuse(()->NativeVehicleBay46.clearPassenger(h.getLevel(),a,capsule,exit));}finally{exitBlocker.discard();}
            NativeVehicleBay46.clearPassenger(h.getLevel(),a,capsule,exit);
            refuse(()->NativeVehicleBay46.clearOwnVehicle(vehicle,boxes.get(0)));
            landmarks(h,a);
            CatanCraft.LOGGER.info("VEHICLE46_NATIVE_HAZARDS_PASS stone=true fluid=true missingSupport=true entity=true passengerFootprint=true passengerEntity=true ownVehicle=true sourceDrift=true roadHazard=true authoredLandmarks=true noFallback=true");h.succeed();
        }finally{vehicle.discard();}
    }
    @GameTest(template="townhall_state_empty",batch="vehicle46_native06",timeoutTicks=600)
    public static void changed_native_definition_and_unstaged_source_envelope_refuse(GameTestHelper h)throws Exception {
        MapAnchor a=stage(h,5,0);
        refuse(()->NativeVehicleBay46.create(h.getLevel(),new NativeVehicleBay46.Spec("sodayo_pick_up","0".repeat(64),8,8)));
        Entity vehicle=NativeVehicleBay46.create(h.getLevel(),NativeVehicleBay46.PICKUP);
        try{
            double minimumRawDot=1;int checkedHeadings=0;
            for(float yaw:List.of(-175f,-135f,-95f,-85f,-45f,-5f,5f,45f,85f,95f,135f,175f)){
                NativeVehicleBay46.pose(vehicle,NativeVehicleBay46.spawn(a,16),yaw);
                NativeVehicleBay46.boxes(vehicle,NativeVehicleBay46.PICKUP);
                double radians=Math.toRadians(yaw);
                minimumRawDot=Math.min(minimumRawDot,vehicle.getViewVector(1).dot(new net.minecraft.world.phys.Vec3(-Math.sin(radians),0,Math.cos(radians))));checkedHeadings++;
            }
            require(minimumRawDot<.999999,"Pinned native view-vector regression was not exercised");
            for(var bad:List.of(net.minecraft.world.phys.Vec3.ZERO,new net.minecraft.world.phys.Vec3(Double.NaN,0,1),new net.minecraft.world.phys.Vec3(0,0,.5),new net.minecraft.world.phys.Vec3(1,0,0)))
                refuse(()->NativeVehicleBay46.requireViewDirection(bad,0));
            // A changed declared yaw without native OBB initialization must still fail independently.
            vehicle.setYRot(85);refuse(()->NativeVehicleBay46.boxes(vehicle,NativeVehicleBay46.PICKUP));
            CatanCraft.LOGGER.info("VEHICLE46_NATIVE_VIEW_PASS checkedHeadings={} minimumRawDot={} normalizedDirection=true invalidViewHeld=true staleObbHeadingHeld=true",checkedHeadings,minimumRawDot);
            NativeVehicleBay46.pose(vehicle,a,27);var boxes=NativeVehicleBay46.boxes(vehicle,NativeVehicleBay46.PICKUP);
            refuse(()->NativeVehicleBay46.clear(h.getLevel(),vehicle,a,boxes));
            CatanCraft.LOGGER.info("VEHICLE46_NATIVE_BOUNDARY_PASS changedIdentity=true unstagedSourceHeld=true");h.succeed();
        }finally{vehicle.discard();}
    }
    private static MapAnchor stage(GameTestHelper h,int index,int rotation)throws Exception {
        Path world=h.getLevel().getServer().getWorldPath(LevelResource.ROOT).toRealPath();
        require(h.getLevel().getServer() instanceof GameTestServer&&h.getLevel().getServer().isSameThread()
                &&world.getParent().getFileName().toString().equals("controlled-catancraft_vehicle46_native-run")
                &&Files.readString(world.getParent().resolve(".catancraft-controlled-fixture-v1")).equals("CatanCraft synthetic flat fixture v1\n")
                &&h.getLevel().getServer().getPlayerList().getPlayers().isEmpty(),"Exact no-player controlled native bay fixture required");
        // Widely separated authored copies belong only to this marked synthetic test world.
        MapAnchor a=new MapAnchor("vehicle46_fixture",1024+index*128,100,1024,rotation);
        int staged=0;
        for(int y=NativeVehicleBay46.MIN_Y;y<=NativeVehicleBay46.MAX_Y;y++)
            for(int z=NativeVehicleBay46.MIN_Z;z<=NativeVehicleBay46.MAX_Z;z++)
                for(int x=NativeVehicleBay46.MIN_X;x<=NativeVehicleBay46.MAX_X;x++){
                    BlockPos p=NativeVehicleBay46.sourceCell(a,x,y,z);var state=NativeVehicleBay46.source(a,p);
                    h.getLevel().getChunkAt(p);h.getLevel().setBlock(p,state,2);staged++;
                }
        require(staged==18400&&staged==NativeVehicleBay46.SOURCE_CELLS,"Bounded authored source slice changed");
        landmarks(h,a);
        int s=rotation==0?1:-1;
        require(h.getLevel().getBlockState(new BlockPos(a.x()-11*s,a.y()+1,a.z()-6*s)).is(Blocks.CRAFTING_TABLE),"Authored counter missing");
        return a;
    }
    private static void landmarks(GameTestHelper h,MapAnchor a)throws Exception {
        for(int z:List.of(-5,5)){
            BlockPos post=NativeVehicleBay46.sourceCell(a,1,0,z),lantern=post.above();
            require(NativeVehicleBay46.source(a,post).is(Blocks.POLISHED_ANDESITE)&&h.getLevel().getBlockState(post).equals(NativeVehicleBay46.source(a,post)),"Authored junction post was removed or changed");
            require(NativeVehicleBay46.source(a,lantern).is(Blocks.LANTERN)&&h.getLevel().getBlockState(lantern).equals(NativeVehicleBay46.source(a,lantern)),"Authored junction lantern was removed or changed");
        }
        BlockPos road=NativeVehicleBay46.sourceCell(a,4,-1,0);
        require(NativeVehicleBay46.source(a,road).is(Blocks.GRAY_CONCRETE)&&h.getLevel().getBlockState(road).equals(NativeVehicleBay46.source(a,road)),"Original starter road is missing");
    }
    @FunctionalInterface private interface Checked {void run()throws Exception;}
    private static void refuse(Checked action)throws Exception {boolean held=false;try{action.run();}catch(IOException expected){held=true;}require(held,"Native hazard or unknown source was accepted");}
    private static void require(boolean ok,String text){if(!ok)throw new AssertionError(text);}
}
