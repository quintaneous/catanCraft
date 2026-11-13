package com.quin.catancraft.world;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.map.MapAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import java.nio.file.*;
import java.util.*;

@GameTestHolder("catancraft_vehicle50") @PrefixGameTestTemplate(false)
public final class NativeVehicleDriving50Tests {
    @GameTest(template="townhall_state_empty",batch="drive500",timeoutTicks=2400)
    public static void pickup_0_left(GameTestHelper h)throws Exception{start(h,0,0,NativeVehicleBay46.PICKUP,-1);}
    @GameTest(template="townhall_state_empty",batch="drive501",timeoutTicks=2400)
    public static void pickup_0_right(GameTestHelper h)throws Exception{start(h,1,0,NativeVehicleBay46.PICKUP,1);}
    @GameTest(template="townhall_state_empty",batch="drive502",timeoutTicks=2400)
    public static void pickup_180_left(GameTestHelper h)throws Exception{start(h,2,180,NativeVehicleBay46.PICKUP,-1);}
    @GameTest(template="townhall_state_empty",batch="drive503",timeoutTicks=2400)
    public static void pickup_180_right(GameTestHelper h)throws Exception{start(h,3,180,NativeVehicleBay46.PICKUP,1);}
    @GameTest(template="townhall_state_empty",batch="drive504",timeoutTicks=2400)
    public static void lav_0_left(GameTestHelper h)throws Exception{start(h,4,0,NativeVehicleBay46.LAV,-1);}
    @GameTest(template="townhall_state_empty",batch="drive505",timeoutTicks=2400)
    public static void lav_0_right(GameTestHelper h)throws Exception{start(h,5,0,NativeVehicleBay46.LAV,1);}
    @GameTest(template="townhall_state_empty",batch="drive506",timeoutTicks=2400)
    public static void lav_180_left(GameTestHelper h)throws Exception{start(h,6,180,NativeVehicleBay46.LAV,-1);}
    @GameTest(template="townhall_state_empty",batch="drive507",timeoutTicks=2400)
    public static void lav_180_right(GameTestHelper h)throws Exception{start(h,7,180,NativeVehicleBay46.LAV,1);}
    private static void start(GameTestHelper h,int id,int rotation,NativeVehicleBay46.Spec spec,int turn)throws Exception {
        Path root=h.getLevel().getServer().getWorldPath(LevelResource.ROOT).toRealPath();
        check(h.getLevel().getServer() instanceof GameTestServer&&h.getLevel().getServer().getPlayerList().getPlayers().isEmpty()
                &&root.getParent().getFileName().toString().equals("controlled-catancraft_vehicle50-run")
                &&Files.readString(root.getParent().resolve(".catancraft-controlled-fixture-v1")).equals("CatanCraft synthetic flat fixture v1\n"),"Exact no-player controlled fixture required");
        MapAnchor anchor=new MapAnchor("drive50",4096+id*160,100,4096,rotation);
        for(int y=NativeVehicleBay46.MIN_Y;y<=NativeVehicleBay46.MAX_Y;y++)for(int z=NativeVehicleBay46.MIN_Z;z<=NativeVehicleBay46.MAX_Z;z++)for(int x=NativeVehicleBay46.MIN_X;x<=NativeVehicleBay46.MAX_X;x++){
            BlockPos p=NativeVehicleBay46.sourceCell(anchor,x,y,z);h.getLevel().getChunkAt(p);h.getLevel().setBlock(p,NativeVehicleBay46.source(anchor,p),2);
        }
        var one=NativeVehicleBay46.sourceCell(anchor,NativeVehicleBay46.MIN_X,NativeVehicleBay46.MIN_Y,NativeVehicleBay46.MIN_Z);
        var two=NativeVehicleBay46.sourceCell(anchor,NativeVehicleBay46.MAX_X,NativeVehicleBay46.MAX_Y,NativeVehicleBay46.MAX_Z);
        AABB bounds=new AABB(Math.min(one.getX(),two.getX()),99,Math.min(one.getZ(),two.getZ()),Math.max(one.getX(),two.getX())+1,108,Math.max(one.getZ(),two.getZ())+1);
        Run run=new Run(h,id,anchor,spec,root,bounds,SectorChunkLease.acquire(h.getLevel(),bounds),turn);run.await(0);
    }
    private static final class Run {
        final GameTestHelper h;final int id;final MapAnchor anchor;final NativeVehicleBay46.Spec spec;final Path root;final AABB envelope;final SectorChunkLease lease;
        final int turn;final NativeVehicleDriving50.Controller controller;List<AABB> previous;double previousYaw,previousPitch,previousRoll;int clearanceCells;
        final JsonArray rows=new JsonArray();Entity vehicle;ServerPlayer player;Vec3 origin;float yaw;int initialEnergy;double maxDistance,maxYaw,maxSteering;float turnYaw;boolean turning;int unchangedCells;int turnTicks;String stop="";
        Run(GameTestHelper h,int id,MapAnchor a,NativeVehicleBay46.Spec s,Path root,AABB bounds,SectorChunkLease lease,int turn){this.turn=turn;controller=new NativeVehicleDriving50.Controller(turn);this.h=h;this.id=id;anchor=a;spec=s;this.root=root;envelope=bounds;this.lease=lease;}
        void await(int ticks){try{lease.keepAlive();if(!lease.ready()){check(ticks<400,"Entity residency timeout");h.runAfterDelay(5,()->await(ticks+5));return;}
            vehicle=NativeVehicleBay46.create(h.getLevel(),spec);NativeVehicleBay46.pose(vehicle,anchor,0);
            vehicle.getClass().getMethod("setEnergy",int.class).invoke(vehicle,NativeVehicleDriving48.ENERGY);initialEnergy=NativeVehicleDriving48.energy(vehicle);check(initialEnergy>0,"Native energy seed refused");
            check(h.getLevel().addFreshEntity(vehicle),"Native entity add refused");
            player=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"Drive50"));player.getInventory().clearContent();player.setPos(vehicle.position());check(!player.isCreative()&&player.startRiding(vehicle,true)&&vehicle.getFirstPassenger()==player,"Native driver boarding failed");
            origin=vehicle.position();yaw=vehicle.getYRot();step(0);
        }catch(Exception|AssertionError failure){fail(failure);}}
        void step(int tick){try{lease.keepAlive();check(lease.ready(),"Lost block/entity residency");check(vehicle.isAlive()&&vehicle.getFirstPassenger()==player,"Lost native vehicle/driver");
            Vec3 p=vehicle.position();double distance=p.subtract(origin).horizontalDistance();double heading=Math.abs(Math.IEEEremainder(vehicle.getYRot()-yaw,360));maxDistance=Math.max(maxDistance,distance);maxYaw=Math.max(maxYaw,heading);if(turning)maxSteering=Math.max(maxSteering,Math.abs(Math.IEEEremainder(vehicle.getYRot()-turnYaw,360)));
            JsonObject row=new JsonObject();row.addProperty("tick",tick);row.addProperty("entityTick",vehicle.tickCount);row.addProperty("x",p.x);row.addProperty("y",p.y);row.addProperty("z",p.z);row.addProperty("yaw",vehicle.getYRot());row.addProperty("energy",NativeVehicleDriving48.energy(vehicle));row.addProperty("speed",vehicle.getDeltaMovement().horizontalDistance());row.addProperty("turning",turning);row.addProperty("gameTime",h.getLevel().getGameTime());row.addProperty("pitch",vehicle.getXRot());row.addProperty("roll",((Number)vehicle.getClass().getMethod("getRoll",float.class).invoke(vehicle,1f)).doubleValue());row.addProperty("vx",vehicle.getDeltaMovement().x);row.addProperty("vy",vehicle.getDeltaMovement().y);row.addProperty("vz",vehicle.getDeltaMovement().z);row.addProperty("onGround",vehicle.onGround());row.addProperty("horizontalCollision",vehicle.horizontalCollision);row.addProperty("verticalCollision",vehicle.verticalCollision);
            List<AABB> nativeBounds=NativeVehicleDriving48.bounds(vehicle);check(nativeBounds.size()==spec.boxes(),"Native part inventory changed");JsonArray parts=new JsonArray();for(AABB b:nativeBounds){JsonArray part=new JsonArray();for(double v:new double[]{b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ})part.add(v);parts.add(part);}row.add("nativeBounds",parts);rows.add(row);
            for(double value:new double[]{p.x,p.y,p.z,vehicle.getDeltaMovement().x,vehicle.getDeltaMovement().y,vehicle.getDeltaMovement().z,vehicle.getYRot(),vehicle.getXRot(),row.get("roll").getAsDouble(),distance,heading})check(Double.isFinite(value),"Nonfinite trajectory");
            if(tick<=20)check(distance<.1,"Neutral native control drifted horizontally before propulsion");
            for(AABB box:nativeBounds)if(!envelope.contains(box.minX,box.minY,box.minZ)||!envelope.contains(box.maxX,box.maxY,box.maxZ)){stop="AUTHORED_ENVELOPE_BOUNDARY";throw new AssertionError("Native route left authored envelope");}
            check(!vehicle.horizontalCollision,"Native horizontal collision during departure");
            for(AABB b:nativeBounds)check(b.getCenter().distanceTo(p)+b.getSize()*2<15,"Native geometry exceeds declared angular sweep bound");
            double roll=row.get("roll").getAsDouble();
            double angular=previous==null?0:Math.toRadians(Math.abs(Math.IEEEremainder(vehicle.getYRot()-previousYaw,360))+Math.abs(vehicle.getXRot()-previousPitch)+Math.abs(roll-previousRoll));
            List<AABB> swept=previous==null?nativeBounds:NativeVehicleBay46.swept(previous,nativeBounds,15*angular*angular/8+.00001);
            clearanceCells+=NativeVehicleDriving50.clear(h.getLevel(),vehicle,player,anchor,swept);
            previous=nativeBounds;previousYaw=vehicle.getYRot();previousPitch=vehicle.getXRot();previousRoll=roll;
            var decision=controller.next(tick,p,origin,vehicle.getYRot(),yaw,vehicle.getDeltaMovement().horizontalDistance());
            row.addProperty("phase",decision.phase());
            if(decision.phase().equals("TURN")&&!turning){turning=true;turnYaw=vehicle.getYRot();}
            if(decision.complete()){stop="QUALIFIED_DEPARTURE";finish();return;}
            check(tick<1900,"Native departure exceeded tick budget in "+decision.phase());
            int keys=decision.keys();row.addProperty("keys",keys);NativeVehicleDriving48.input(player,keys);
            h.runAfterDelay(1,()->step(tick+1));
        }catch(Exception|AssertionError failure){fail(failure);}}
        void finish()throws Exception {
            NativeVehicleDriving48.input(player,0);check(maxDistance>2,"Native propulsion did not move the vehicle");check(turning&&maxSteering>80,"Native steering did not complete a turn");check(stop.equals("QUALIFIED_DEPARTURE"),"Native route did not complete");check(NativeVehicleDriving48.energy(vehicle)<initialEnergy,"Native powered motion consumed no energy");
            for(int y=NativeVehicleBay46.MIN_Y;y<=NativeVehicleBay46.MAX_Y;y++)for(int z=NativeVehicleBay46.MIN_Z;z<=NativeVehicleBay46.MAX_Z;z++)for(int x=NativeVehicleBay46.MIN_X;x<=NativeVehicleBay46.MAX_X;x++){
                BlockPos p=NativeVehicleBay46.sourceCell(anchor,x,y,z);check(h.getLevel().getBlockState(p).equals(NativeVehicleBay46.source(anchor,p)),"Natural driving changed authored source at "+p);unchangedCells++;
            }
            write("PASS","");CatanCraft.LOGGER.info("VEHICLE50_DRIVE_PASS case={} vehicle={} rotation={} samples={} distance={} heading={} stop={} routeQualified=true fullBayQualified=false",id,spec.id(),anchor.rotation(),rows.size(),maxDistance,maxYaw,stop);close();h.succeed();
        }
        void write(String result,String failure)throws Exception {
            String source=System.getProperty("catancraft.vehicle50Source","");check(source.matches("[0-9a-f]{40}"),"Exact source SHA required");
            JsonObject proof=new JsonObject();proof.addProperty("source",source);proof.addProperty("namespace","catancraft_vehicle50");proof.addProperty("runtimeSHA256",System.getProperty("catancraft.sbwRuntimeManifestSHA256",""));proof.addProperty("entityUUID",vehicle==null?"":vehicle.getUUID().toString());proof.addProperty("driverUUID",player==null?"":player.getUUID().toString());proof.addProperty("controlPath","VehicleMovementMessage.handler");proof.addProperty("naturalServerTicks",true);proof.addProperty("anchorX",anchor.x());proof.addProperty("anchorY",anchor.y());proof.addProperty("anchorZ",anchor.z());proof.addProperty("case",id);proof.addProperty("vehicle",spec.id());proof.addProperty("rotation",anchor.rotation());proof.addProperty("result",result);proof.addProperty("failure",failure);proof.addProperty("stop",stop);proof.addProperty("stagedCells",NativeVehicleBay46.SOURCE_CELLS);proof.addProperty("sourceCellsUnchanged",unchangedCells);proof.addProperty("initialEnergy",initialEnergy);proof.addProperty("turn",turn);proof.addProperty("clearanceCells",clearanceCells);proof.addProperty("routeQualified",result.equals("PASS"));proof.addProperty("turnYaw",turnYaw);proof.addProperty("maxSteering",maxSteering);proof.addProperty("fullBayQualified",false);proof.addProperty("postSpawnPoseWrites",0);proof.add("trajectory",rows);
            Files.writeString(root.resolve("data/catancraft_vehicle50_"+id+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(proof),StandardOpenOption.CREATE_NEW);
        }
        void fail(Throwable failure){try{write("FAIL",failure.toString());}catch(Exception evidence){failure.addSuppressed(evidence);}finally{close();}throw new RuntimeException(failure);}
        void close(){if(vehicle!=null){vehicle.ejectPassengers();vehicle.discard();}lease.close();}
    }
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
}
