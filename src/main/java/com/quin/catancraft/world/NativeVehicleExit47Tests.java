package com.quin.catancraft.world;

import com.mojang.authlib.GameProfile;
import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.command.NationProcurement;
import com.quin.catancraft.map.MapAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.*;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

@GameTestHolder("catancraft_vehicle47_exit") @PrefixGameTestTemplate(false)
public final class NativeVehicleExit47Tests {
    @GameTest(template="townhall_state_empty",batch="vehicle47_exit01",timeoutTicks=600)
    public static void every_pickup_seat_zero_uses_checked_actual_packet_exit(GameTestHelper h)throws Exception{everySeat(h,0,0);}
    @GameTest(template="townhall_state_empty",batch="vehicle47_exit02",timeoutTicks=600)
    public static void every_pickup_seat_rotated_uses_checked_actual_packet_exit(GameTestHelper h)throws Exception{everySeat(h,1,180);}
    private static void everySeat(GameTestHelper h,int index,int rotation)throws Exception {
        withFixture(h,index,rotation,anchor->{
        Entity vehicle=pickup(h,anchor,true);int completed=0;
        try{
            for(int seat=0;seat<8;seat++){
                ExitPlayer player=player(h);player.setShiftKeyDown(true);
                require(player.providerWantsToStopRiding(),"Unmounted Shift control did not request dismount");
                ride(vehicle,player,seat);
                require(!player.providerWantsToStopRiding(),"Pinned provider no longer suppresses ordinary Shift dismount");
                player.setShiftKeyDown(false);
                Vec3 baseline=vehicle.getDismountLocationForPassenger(player);
                refuse(()->NativeVehicleBay46.clearOwnVehicle(vehicle,body(baseline)));
                var plan=NativeVehicleExit47.resolve(vehicle,player);
                require(plan.seat()==seat&&plan.correction()>=.125&&plan.correction()<=.5&&plan.nativeExit().distanceTo(baseline)<.0001,"Bounded seat contract changed");
                packet(player,false);
                require(player.getVehicle()==null&&player.position().distanceTo(plan.exit())<.0001,"Actual packet failed to use checked final exit");
                NativeVehicleBay46.clearOwnVehicle(vehicle,body(player.position()));
                player.getPersistentData().putInt("SBWSeatIndex",seat);
                require(vehicle.getDismountLocationForPassenger(player).distanceTo(baseline)<.0001,"Scoped override leaked into provider baseline");
                player.getPersistentData().remove("SBWSeatIndex");completed++;
                CatanCraft.LOGGER.info("VEHICLE47_EXIT_SEAT_PASS rotation={} seat={} nativeExit={} checkedExit={} correction={} checkedCells={} actualPacket=true baselinePreserved=true",rotation,seat,baseline,plan.exit(),plan.correction(),plan.checkedCells());
            }
            require(completed==8,"Not every seat executed");landmarks(h,anchor);
            CatanCraft.LOGGER.info("VEHICLE47_EXIT_ALL_SEATS_PASS rotation={} seats={} actualPacket=true noLongTeleport=true",rotation,completed);h.succeed();
        }finally{vehicle.ejectPassengers();vehicle.discard();}
        });
    }
    @GameTest(template="townhall_state_empty",batch="vehicle47_exit03",timeoutTicks=600)
    public static void blocked_fluid_support_and_entity_exits_retain_the_passenger(GameTestHelper h)throws Exception {
        withFixture(h,2,0,anchor->{
        Entity vehicle=pickup(h,anchor,true);ServerPlayer player=player(h);ride(vehicle,player,0);
        try{
            var plan=NativeVehicleExit47.resolve(vehicle,player);BlockPos body=BlockPos.containing(plan.exit());var air=h.getLevel().getBlockState(body);
            for(var hazard:List.of(Blocks.STONE.defaultBlockState(),Blocks.WATER.defaultBlockState())){
                h.getLevel().setBlock(body,hazard,2);
                try{heldPacket(vehicle,player);}finally{h.getLevel().setBlock(body,air,2);}
            }
            BlockPos support=body.below();var floor=h.getLevel().getBlockState(support);h.getLevel().setBlock(support,Blocks.AIR.defaultBlockState(),2);
            try{heldPacket(vehicle,player);}finally{h.getLevel().setBlock(support,floor,2);}
            ItemEntity blocker=new ItemEntity(h.getLevel(),plan.exit().x,plan.exit().y+.3,plan.exit().z,new ItemStack(Items.DIAMOND));
            require(h.getLevel().addFreshEntity(blocker),"Could not install controlled exit occupant");
            try{heldPacket(vehicle,player);}finally{blocker.discard();}
            packet(player,false);require(player.getVehicle()==null&&player.position().distanceTo(plan.exit())<.0001,"Cleared exit could not be retried");landmarks(h,anchor);
            CatanCraft.LOGGER.info("VEHICLE47_EXIT_HAZARDS_PASS stone=true fluid=true fullFootprintSupport=true entity=true actualPacket=true retainedSeat=true retry=true");h.succeed();
        }finally{player.stopRiding();vehicle.discard();}
        });
    }
    @GameTest(template="townhall_state_empty",batch="vehicle47_exit04",timeoutTicks=600)
    public static void ordinary_forced_ejection_death_and_other_provider_paths_are_unchanged(GameTestHelper h)throws Exception {
        withFixture(h,3,0,anchor->{
        Entity vehicle=pickup(h,anchor,false);ServerPlayer player=player(h);
        try{
            ride(vehicle,player,0);Vec3 baseline=vehicle.getDismountLocationForPassenger(player);packet(player,false);
            require(player.getVehicle()==null&&player.position().distanceTo(baseline)<.0001,"Ordinary unpurchased provider exit changed");
            mark(vehicle);ride(vehicle,player,0);vehicle.ejectPassengers();
            require(player.getVehicle()==null&&player.position().distanceTo(baseline)<.0001,"Forced ejection changed");
            ride(vehicle,player,0);packet(player,true);
            require(player.getVehicle()==null&&player.position().distanceTo(baseline)<.0001,"Native ejection packet changed");
            ride(vehicle,player,0);player.setHealth(0);
            require(!NativeVehicleExit47.handleVoluntaryPacket(player,false),"Dead passenger was captured by voluntary exit protocol");
            player.stopRiding();require(player.getVehicle()==null,"Death-related removal was blocked");player.setHealth(20);
            vehicle.getPersistentData().putString(NationProcurement.NATION_TAG,"invalid");require(!NativeVehicleExit47.managed(vehicle),"Malformed ownership was accepted");
            Entity lav=NativeVehicleBay46.create(h.getLevel(),NativeVehicleBay46.LAV);
            try{
                NativeVehicleBay46.pose(lav,anchor,0);mark(lav);ride(lav,player,0);Vec3 lavExit=lav.getDismountLocationForPassenger(player);
                require(!NativeVehicleExit47.managed(lav),"Other vehicle provider type was captured");packet(player,false);
                require(player.getVehicle()==null&&player.position().distanceTo(lavExit)<.0001,"Other native vehicle exit changed");
            }finally{lav.ejectPassengers();lav.discard();}
            CatanCraft.LOGGER.info("VEHICLE47_EXIT_BYPASS_PASS ordinary=true forced=true ejection=true death=true malformedOwnership=true otherVehicle=true scopedStateCleared=true");h.succeed();
        }finally{player.setHealth(20);player.stopRiding();vehicle.discard();}
        });
    }
    @GameTest(template="townhall_state_empty",batch="vehicle47_exit05",timeoutTicks=600)
    public static void changed_effective_geometry_seat_movement_and_unloaded_bounds_refuse(GameTestHelper h)throws Exception {
        withFixture(h,4,0,anchor->{
        Entity vehicle=pickup(h,anchor,true);ServerPlayer player=player(h);ride(vehicle,player,0);
        try{
            List<?> parts=(List<?>)vehicle.getClass().getMethod("getOBBs").invoke(vehicle);Object extent=parts.get(1).getClass().getMethod("extents").invoke(parts.get(1));
            var x=extent.getClass().getField("x");double width=x.getDouble(extent);x.setDouble(extent,width+.125);
            try{heldPacket(vehicle,player);}finally{x.setDouble(extent,width);}
            vehicle.setDeltaMovement(.2,0,0);try{heldPacket(vehicle,player);}finally{vehicle.setDeltaMovement(Vec3.ZERO);}
            vehicle.setDeltaMovement(Double.NaN,0,0);try{heldPacket(vehicle,player);}finally{vehicle.setDeltaMovement(Vec3.ZERO);}
            player.getPersistentData().putInt("SBWSeatIndex",99);try{heldPacket(vehicle,player);}finally{player.getPersistentData().putInt("SBWSeatIndex",0);}
            vehicle.yRotO=vehicle.getYRot()+5;try{heldPacket(vehicle,player);}finally{NativeVehicleBay46.pose(vehicle,anchor,0);}
            Vec3 far=new Vec3(10000000.5,100.1,10000000.5);require(!h.getLevel().hasChunkAt(BlockPos.containing(far)),"Unloaded-boundary fixture is already loaded");
            NativeVehicleBay46.pose(vehicle,far,-90);try{heldPacket(vehicle,player);}finally{NativeVehicleBay46.pose(vehicle,anchor,0);}
            var restored=NativeVehicleExit47.resolve(vehicle,player);packet(player,false);
            require(player.getVehicle()==null&&player.position().distanceTo(restored.exit())<.0001,"Restored native identity did not recover");
            CatanCraft.LOGGER.info("VEHICLE47_EXIT_IDENTITY_PASS everyObbPinned=true changedGeometryHeld=true movingHeld=true nonfiniteMotionHeld=true seatHeld=true stalePoseHeld=true unloadedHeld=true noChunkLoads=true");h.succeed();
        }finally{player.stopRiding();vehicle.discard();}
        });
    }
    @GameTest(template="townhall_state_empty",batch="vehicle47_exit06",timeoutTicks=600)
    public static void forge_pre_removal_drift_and_external_cancellation_preserve_seat(GameTestHelper h)throws Exception {
        withFixture(h,5,0,anchor->{
        Entity vehicle=pickup(h,anchor,true);ServerPlayer player=player(h);ride(vehicle,player,0);
        try{
            var plan=NativeVehicleExit47.resolve(vehicle,player);BlockPos exit=BlockPos.containing(plan.exit());var air=h.getLevel().getBlockState(exit);
            Consumer<EntityMountEvent> drift=event->{if(event.isDismounting()&&event.getEntityMounting()==player&&event.getEntityBeingMounted()==vehicle)h.getLevel().setBlock(exit,Blocks.STONE.defaultBlockState(),2);};
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH,false,EntityMountEvent.class,drift);
            try{heldPacket(vehicle,player);}finally{MinecraftForge.EVENT_BUS.unregister(drift);h.getLevel().setBlock(exit,air,2);}
            Consumer<EntityMountEvent> cancel=event->{if(event.isDismounting()&&event.getEntityMounting()==player&&event.getEntityBeingMounted()==vehicle)event.setCanceled(true);};
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH,false,EntityMountEvent.class,cancel);
            try{heldPacket(vehicle,player);}finally{MinecraftForge.EVENT_BUS.unregister(cancel);}
            var retry=NativeVehicleExit47.resolve(vehicle,player);packet(player,false);
            require(player.getVehicle()==null&&player.position().distanceTo(retry.exit())<.0001,"Scoped Forge refusal leaked across retry");
            CatanCraft.LOGGER.info("VEHICLE47_EXIT_FORGE_PASS actualOrdering=true lateObstacleHeld=true externalCancellation=true retainedSeat=true scopedRetry=true");h.succeed();
        }finally{player.stopRiding();vehicle.discard();}
        });
    }
    private static void heldPacket(Entity vehicle,ServerPlayer player)throws Exception {
        Vec3 before=player.position();int seat=((Number)vehicle.getClass().getMethod("getSeatIndex",Entity.class).invoke(vehicle,player)).intValue();
        int originalTag=player.getPersistentData().getInt("SBWSeatIndex");
        packet(player,false);
        require(player.getVehicle()==vehicle&&player.position().distanceTo(before)<.0001&&((Number)vehicle.getClass().getMethod("getSeatIndex",Entity.class).invoke(vehicle,player)).intValue()==seat,"Refused voluntary packet removed, moved, or reseated passenger");
        require(player.getPersistentData().getInt("SBWSeatIndex")==originalTag,"Refused packet changed the native seat tag");
    }
    private static void packet(ServerPlayer player,boolean ejection)throws Exception {
        Connection connection=new Connection(PacketFlow.SERVERBOUND);connection.setListener(player.connection);
        var constructor=NetworkEvent.Context.class.getDeclaredConstructor(Connection.class,NetworkDirection.class,int.class);constructor.setAccessible(true);
        var context=constructor.newInstance(connection,NetworkDirection.PLAY_TO_SERVER,0);require(context.getSender()==player,"Native packet context has wrong sender");
        Class<?> packet=Class.forName("com.atsuishio.superbwarfare.network.message.send.PlayerStopRidingMessage");Object message=packet.getConstructor(boolean.class).newInstance(ejection);
        packet.getMethod("handler",Supplier.class).invoke(message,(Supplier<NetworkEvent.Context>)()->context);
    }
    private static void ride(Entity vehicle,ServerPlayer player,int seat)throws Exception {
        player.setPos(vehicle.position());require(player.startRiding(vehicle,true),"Native boarding failed");
        vehicle.getClass().getMethod("changeSeat",Entity.class,int.class).invoke(vehicle,player,seat);
        require(((Number)vehicle.getClass().getMethod("getSeatIndex",Entity.class).invoke(vehicle,player)).intValue()==seat,"Native seat selection failed");
    }
    private static Entity pickup(GameTestHelper h,MapAnchor anchor,boolean managed)throws Exception {Entity vehicle=NativeVehicleBay46.create(h.getLevel(),NativeVehicleBay46.PICKUP);NativeVehicleBay46.pose(vehicle,anchor,0);if(managed)mark(vehicle);return vehicle;}
    private static void mark(Entity vehicle){vehicle.getPersistentData().putBoolean(NationProcurement.PURCHASED_TAG,true);vehicle.getPersistentData().putString(NationProcurement.NATION_TAG,"47000000-0000-0000-0000-000000000047");}
    private static final class ExitPlayer extends FakePlayer {
        ExitPlayer(ServerLevel level,GameProfile profile){super(level,profile);}
        boolean providerWantsToStopRiding(){return super.wantsToStopRiding();}
    }
    private static ExitPlayer player(GameTestHelper h){return new ExitPlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"Exit47Probe"));}
    private static net.minecraft.world.phys.AABB body(Vec3 feet){return new net.minecraft.world.phys.AABB(feet.x-.3,feet.y,feet.z-.3,feet.x+.3,feet.y+1.8,feet.z+.3);}
    @FunctionalInterface private interface FixtureWork{void run(MapAnchor anchor)throws Exception;}
    private static void withFixture(GameTestHelper h,int index,int rotation,FixtureWork work)throws Exception {
        MapAnchor anchor=stage(h,index,rotation);
        BlockPos probe=BlockPos.containing(NativeVehicleBay46.spawn(anchor,0));
        boolean blocks=h.getLevel().hasChunkAt(probe),entities=h.getLevel().areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(probe.getX()>>4,probe.getZ()>>4));
        if(blocks&&!entities){
            boolean refused=false;try{NativeVehicleExit47.requireResident(h.getLevel(),probe);}catch(IOException expected){refused=expected.getMessage().startsWith("Exit block/entity chunk is not resident");}
            require(refused,"Resident blocks were accepted before saved entities finished loading");
            CatanCraft.LOGGER.info("VEHICLE47_EXIT_RESIDENCY_PENDING_PASS case={} blockResident=true entitiesLoaded=false directGuardRefused=true",index);
        }else CatanCraft.LOGGER.info("VEHICLE47_EXIT_RESIDENCY_OBSERVATION case={} blockResident={} entitiesLoaded={} pendingStateObserved=false",index,blocks,entities);
        BlockPos one=NativeVehicleBay46.sourceCell(anchor,NativeVehicleBay46.MIN_X,NativeVehicleBay46.MIN_Y,NativeVehicleBay46.MIN_Z);
        BlockPos two=NativeVehicleBay46.sourceCell(anchor,NativeVehicleBay46.MAX_X,NativeVehicleBay46.MAX_Y,NativeVehicleBay46.MAX_Z);
        var bounds=new net.minecraft.world.phys.AABB(Math.min(one.getX(),two.getX()),Math.min(one.getY(),two.getY()),Math.min(one.getZ(),two.getZ()),Math.max(one.getX(),two.getX())+1,Math.max(one.getY(),two.getY())+1,Math.max(one.getZ(),two.getZ())+1);
        SectorChunkLease lease=SectorChunkLease.acquire(h.getLevel(),bounds);
        await(h,index,anchor,lease,0,work);
    }
    private static void await(GameTestHelper h,int index,MapAnchor anchor,SectorChunkLease lease,int ticks,FixtureWork work){
        try{
            lease.keepAlive();
            if(lease.ready()){
                for(var chunk:lease.positions())NativeVehicleExit47.requireResident(h.getLevel(),new BlockPos(chunk.x<<4,anchor.y(),chunk.z<<4));
                CatanCraft.LOGGER.info("VEHICLE47_EXIT_RESIDENCY_READY case={} chunks={} waitedTicks={} blockAndEntityReady=true stagedOnce=true",index,lease.chunkCount(),ticks);
                try{work.run(anchor);}finally{lease.close();}
                return;
            }
            require(ticks<400,"Native exit fixture block/entity residency did not become ready");
            h.runAfterDelay(5,()->await(h,index,anchor,lease,ticks+5,work));
        }catch(Exception|AssertionError failure){lease.close();throw new RuntimeException(failure);}
    }
    private static MapAnchor stage(GameTestHelper h,int index,int rotation)throws Exception {
        Path world=h.getLevel().getServer().getWorldPath(LevelResource.ROOT).toRealPath();
        require(h.getLevel().getServer() instanceof GameTestServer&&h.getLevel().getServer().isSameThread()
                &&world.getParent().getFileName().toString().equals("controlled-catancraft_vehicle47_exit-run")
                &&Files.readString(world.getParent().resolve(".catancraft-controlled-fixture-v1")).equals("CatanCraft synthetic flat fixture v1\n")
                &&h.getLevel().getServer().getPlayerList().getPlayers().isEmpty(),"Exact no-player controlled exit fixture required");
        MapAnchor a=new MapAnchor("vehicle47_exit_fixture",2048+index*128,100,2048,rotation);int cells=0;
        for(int y=NativeVehicleBay46.MIN_Y;y<=NativeVehicleBay46.MAX_Y;y++)for(int z=NativeVehicleBay46.MIN_Z;z<=NativeVehicleBay46.MAX_Z;z++)for(int x=NativeVehicleBay46.MIN_X;x<=NativeVehicleBay46.MAX_X;x++){
            BlockPos p=NativeVehicleBay46.sourceCell(a,x,y,z);h.getLevel().getChunkAt(p);h.getLevel().setBlock(p,NativeVehicleBay46.source(a,p),2);cells++;
        }
        require(cells==18400,"Authored source slice changed");landmarks(h,a);return a;
    }
    private static void landmarks(GameTestHelper h,MapAnchor a)throws Exception {for(int z:List.of(-5,5))for(int y:List.of(0,1)){BlockPos p=NativeVehicleBay46.sourceCell(a,1,y,z);require(h.getLevel().getBlockState(p).equals(NativeVehicleBay46.source(a,p)),"Authored junction obstacle changed");}}
    @FunctionalInterface private interface Checked{void run()throws Exception;}
    private static void refuse(Checked action)throws Exception{boolean held=false;try{action.run();}catch(IOException expected){held=true;}require(held,"Unsafe native provider baseline unexpectedly passed");}
    private static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
}
