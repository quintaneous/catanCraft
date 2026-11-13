package com.quin.catancraft.world;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Supplier;
import java.io.IOException;

/** Controlled investigator only. No production registration, path teleport or engine override. */
final class NativeVehicleDriving48 {
    static final int ENERGY=1000000;
    static void input(ServerPlayer player,int keys)throws Exception {
        Connection connection=new Connection(PacketFlow.SERVERBOUND);connection.setListener(player.connection);
        var ctor=NetworkEvent.Context.class.getDeclaredConstructor(Connection.class,NetworkDirection.class,int.class);ctor.setAccessible(true);
        var context=ctor.newInstance(connection,NetworkDirection.PLAY_TO_SERVER,0);
        if(context.getSender()!=player)throw new IOException("Foreign native movement sender");
        Class<?> type=Class.forName("com.atsuishio.superbwarfare.network.message.send.VehicleMovementMessage");
        Object message=type.getConstructor(short.class).newInstance((short)keys);
        type.getMethod("handler",Supplier.class).invoke(message,(Supplier<NetworkEvent.Context>)()->context);
    }
    static int energy(Entity vehicle)throws Exception{return ((Number)vehicle.getClass().getMethod("getEnergy").invoke(vehicle)).intValue();}
    static List<AABB> bounds(Entity vehicle)throws Exception {
        List<?> boxes=(List<?>)vehicle.getClass().getMethod("getOBBs").invoke(vehicle);List<AABB> result=new ArrayList<>();
        for(Object box:boxes){Object vertices=box.getClass().getMethod("getVertices").invoke(box);if(Array.getLength(vertices)!=8)throw new IOException("Incomplete native geometry");AABB extent=null;
            for(int n=0;n<8;n++){Object p=Array.get(vertices,n);Vec3 v=new Vec3(p.getClass().getField("x").getDouble(p),p.getClass().getField("y").getDouble(p),p.getClass().getField("z").getDouble(p));
                if(!Double.isFinite(v.x)||!Double.isFinite(v.y)||!Double.isFinite(v.z))throw new IOException("Nonfinite native geometry");
                AABB point=new AABB(v,v);extent=extent==null?point:extent.minmax(point);}
            result.add(extent);
        }return result;
    }
}
