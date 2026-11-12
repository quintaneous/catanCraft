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
    public static void pickup_zero_authored_factory_native_geometry(GameTestHelper h)throws Exception{qualify(h,0,0,NativeVehicleBay46.PICKUP);}
    @GameTest(template="townhall_state_empty",batch="vehicle46_native02",timeoutTicks=600)
    public static void pickup_rotated_authored_factory_native_geometry(GameTestHelper h)throws Exception{qualify(h,1,180,NativeVehicleBay46.PICKUP);}
    @GameTest(template="townhall_state_empty",batch="vehicle46_native03",timeoutTicks=600)
    public static void lav_zero_authored_factory_native_geometry(GameTestHelper h)throws Exception{qualify(h,2,0,NativeVehicleBay46.LAV);}
    @GameTest(template="townhall_state_empty",batch="vehicle46_native04",timeoutTicks=600)
    public static void lav_rotated_authored_factory_native_geometry(GameTestHelper h)throws Exception{qualify(h,3,180,NativeVehicleBay46.LAV);}
    private static void qualify(GameTestHelper h,int index,int rotation,NativeVehicleBay46.Spec spec)throws Exception {
        MapAnchor a=stage(h,index,rotation);
        var proof=NativeVehicleBay46.inspect(h.getLevel(),a,spec);
        require(proof.boxes()==spec.boxes()&&proof.seats()==spec.seats()&&proof.sweepSamples()==17&&!proof.externalRoadQualified(),"Incorrect qualification scope");
        CatanCraft.LOGGER.info("VEHICLE46_NATIVE_PASS vehicle={} rotation={} obbs={} seats={} sweepSamples={} checkedCells={} bounds={} externalRoadQualified=false",proof.id(),rotation,proof.boxes(),proof.seats(),proof.sweepSamples(),proof.checkedCells(),proof.initialBounds());h.succeed();
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
            var passenger=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"BayHazard46"));
            var exit=(net.minecraft.world.phys.Vec3)vehicle.getClass().getMethod("getDismountLocationForIndex",net.minecraft.world.entity.LivingEntity.class,int.class).invoke(vehicle,passenger,0);
            var capsule=new net.minecraft.world.phys.AABB(exit.x-.3,exit.y,exit.z-.3,exit.x+.3,exit.y+1.8,exit.z+.3);
            NativeVehicleBay46.clearPassenger(h.getLevel(),a,capsule,exit);
            BlockPos edge=new BlockPos((int)Math.floor(capsule.minX),a.y()-1,(int)Math.floor(capsule.minZ));var edgeState=h.getLevel().getBlockState(edge);
            h.getLevel().setBlock(edge,Blocks.AIR.defaultBlockState(),2);refuse(()->NativeVehicleBay46.clearPassenger(h.getLevel(),a,capsule,exit));h.getLevel().setBlock(edge,edgeState,2);
            ItemEntity exitBlocker=new ItemEntity(h.getLevel(),exit.x,exit.y+.5,exit.z,new ItemStack(Items.DIAMOND));require(h.getLevel().addFreshEntity(exitBlocker),"Could not install exit obstruction");
            try{refuse(()->NativeVehicleBay46.clearPassenger(h.getLevel(),a,capsule,exit));}finally{exitBlocker.discard();}
            NativeVehicleBay46.clearPassenger(h.getLevel(),a,capsule,exit);
            CatanCraft.LOGGER.info("VEHICLE46_NATIVE_HAZARDS_PASS stone=true fluid=true missingSupport=true entity=true passengerFootprint=true passengerEntity=true noFallback=true");h.succeed();
        }finally{vehicle.discard();}
    }
    @GameTest(template="townhall_state_empty",batch="vehicle46_native06",timeoutTicks=600)
    public static void changed_native_definition_and_external_source_envelope_refuse(GameTestHelper h)throws Exception {
        MapAnchor a=stage(h,5,0);
        refuse(()->NativeVehicleBay46.create(h.getLevel(),new NativeVehicleBay46.Spec("sodayo_pick_up","0".repeat(64),8,8)));
        Entity vehicle=NativeVehicleBay46.create(h.getLevel(),NativeVehicleBay46.PICKUP);
        try{
            NativeVehicleBay46.pose(vehicle,a,13);var boxes=NativeVehicleBay46.boxes(vehicle,NativeVehicleBay46.PICKUP);
            refuse(()->NativeVehicleBay46.clear(h.getLevel(),vehicle,a,boxes));
            CatanCraft.LOGGER.info("VEHICLE46_NATIVE_BOUNDARY_PASS changedIdentity=true externalRoadHeld=true");h.succeed();
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
        var plan=BasicConstructionAssets.plan("vehicle_factory",a);require(plan.size()==31*47*31,"Factory volume changed");
        for(var cell:plan){h.getLevel().getChunkAt(cell.position());h.getLevel().setBlock(cell.position(),cell.state(),2);}
        int s=rotation==0?1:-1;
        require(h.getLevel().getBlockState(new BlockPos(a.x()-11*s,a.y()+1,a.z()-6*s)).is(Blocks.CRAFTING_TABLE),"Authored counter missing");
        return a;
    }
    @FunctionalInterface private interface Checked {void run()throws Exception;}
    private static void refuse(Checked action)throws Exception {boolean held=false;try{action.run();}catch(IOException expected){held=true;}require(held,"Native hazard or unknown source was accepted");}
    private static void require(boolean ok,String text){if(!ok)throw new AssertionError(text);}
}
