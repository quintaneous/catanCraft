package com.quin.catancraft.world;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.quin.catancraft.map.MapAnchor;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;

/** Controlled road-aligned departure/seat qualification; never a production driver. */
final class NativeVehicleRoad50 {
    record Decision(String phase,int keys,boolean complete) {}
    static final class Controller {
        String phase="NEUTRAL";int entered;
        Decision next(int tick,Vec3 p,Vec3 origin,float yaw,float initialYaw,double speed)throws IOException {
            double radians=Math.toRadians(initialYaw);
            double distance=p.subtract(origin).dot(new Vec3(-Math.sin(radians),0,Math.cos(radians)));
            if(!Double.isFinite(distance)||!Double.isFinite(speed))throw new IOException("Invalid road movement");
            if(phase.equals("NEUTRAL")&&tick>=20)phase="FORWARD";
            if(phase.equals("FORWARD")&&distance>=10){phase="FINAL_BRAKE";entered=tick;}
            return switch(phase){
                case "NEUTRAL"->new Decision(phase,0,false);
                case "FORWARD"->new Decision(phase,4,false);
                case "FINAL_BRAKE"->new Decision(phase,16,speed<.002&&tick-entered>=20);
                default->throw new IOException("Unknown road phase");
            };
        }
    }
    static JsonArray seats(ServerLevel level,Entity vehicle,MapAnchor anchor,NativeVehicleBay46.Spec spec)throws Exception {
        JsonArray result=new JsonArray();
        for(int seat=0;seat<spec.seats();seat++){
            ServerPlayer player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"Road50Seat"));
            player.getInventory().clearContent();player.setPos(vehicle.position());
            if(!player.startRiding(vehicle,true))throw new IOException("Native road seat boarding failed");
            vehicle.getClass().getMethod("changeSeat",Entity.class,int.class).invoke(vehicle,player,seat);
            if(((Number)vehicle.getClass().getMethod("getSeatIndex",Entity.class).invoke(vehicle,player)).intValue()!=seat)throw new IOException("Native road seat selection failed");
            result.add(exit(level,vehicle,player,anchor,seat));
        }
        return result;
    }
    static JsonObject exit(ServerLevel level,Entity vehicle,ServerPlayer player,MapAnchor anchor,int seat)throws Exception {
        Vec3 before=vehicle.position();float yaw=vehicle.getYRot();
        Vec3 expected=NativeVehicleExit47.managed(vehicle)?NativeVehicleExit47.resolve(vehicle,player).exit():vehicle.getDismountLocationForPassenger(player);
        packet(player);
        if(player.getVehicle()!=null||player.position().distanceTo(expected)>.0001||vehicle.position().distanceTo(before)>.000001||vehicle.getYRot()!=yaw)throw new IOException("Actual road dismount differed or moved vehicle");
        Vec3 feet=player.position();
        if(feet.y<anchor.y()-.001||feet.y>anchor.y()+1)throw new IOException("Road exit landing height unsupported");
        AABB body=new AABB(feet.x-.3,feet.y,feet.z-.3,feet.x+.3,feet.y+1.8,feet.z+.3);
        NativeVehicleBay46.clearOwnVehicle(vehicle,body);
        int checked=NativeVehicleDriving50.clear(level,vehicle,player,anchor,List.of(body));
        JsonObject row=new JsonObject();row.addProperty("seat",seat);row.addProperty("actualPacket",true);row.addProperty("ownGeometryClear",true);row.addProperty("checkedCells",checked);
        row.addProperty("x",feet.x);row.addProperty("y",feet.y);row.addProperty("z",feet.z);return row;
    }
    private static void packet(ServerPlayer player)throws Exception {
        Connection connection=new Connection(PacketFlow.SERVERBOUND);connection.setListener(player.connection);
        var constructor=NetworkEvent.Context.class.getDeclaredConstructor(Connection.class,NetworkDirection.class,int.class);constructor.setAccessible(true);
        var context=constructor.newInstance(connection,NetworkDirection.PLAY_TO_SERVER,0);
        if(context.getSender()!=player)throw new IOException("Foreign road dismount sender");
        Class<?> type=Class.forName("com.atsuishio.superbwarfare.network.message.send.PlayerStopRidingMessage");
        Object message=type.getConstructor(boolean.class).newInstance(false);
        type.getMethod("handler",Supplier.class).invoke(message,(Supplier<NetworkEvent.Context>)()->context);
    }
    private NativeVehicleRoad50(){}
}
