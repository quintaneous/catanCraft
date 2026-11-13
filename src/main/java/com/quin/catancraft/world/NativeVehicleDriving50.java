package com.quin.catancraft.world;

import com.quin.catancraft.map.MapAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.io.IOException;
import java.util.*;

/** Disposable fixture controller. Emits ordinary native movement key bits only. */
final class NativeVehicleDriving50 {
    record Decision(String phase,int keys,boolean complete) {}
    static final class Controller {
        final int turn;String phase="NEUTRAL";int entered;Vec3 turnEnd;
        Controller(int turn){if(turn!=-1&&turn!=1)throw new IllegalArgumentException();this.turn=turn;}
        private void enter(String next,int tick){phase=next;entered=tick;}
        Decision next(int tick,Vec3 p,Vec3 origin,float yaw,float initialYaw,double speed)throws IOException {
            double a=Math.toRadians(initialYaw),target=initialYaw+turn*90;
            Vec3 outward=new Vec3(-Math.sin(a),0,Math.cos(a));
            Vec3 road=new Vec3(-Math.sin(Math.toRadians(target)),0,Math.cos(Math.toRadians(target)));
            double advance=p.subtract(origin).dot(outward),angle=Math.IEEEremainder(yaw-initialYaw,360)*turn;
            double error=Math.IEEEremainder(target-yaw,360);
            if(!Double.isFinite(advance)||!Double.isFinite(angle)||!Double.isFinite(speed))throw new IOException("Invalid drive observation");
            int steer=turn<0?1:2;
            switch(phase){
                case "NEUTRAL" -> {if(tick>=20)enter("APPROACH",tick);else return new Decision(phase,0,false);}
                case "APPROACH" -> {if(advance>=9.5)enter("STOP",tick);else return new Decision(phase,20,false);}
                case "STOP" -> {if(speed<.002&&tick-entered>=20)enter("PRESTEER",tick);else return new Decision(phase,16,false);}
                case "PRESTEER" -> {if(tick-entered>=60)enter("TURN",tick);else return new Decision(phase,16|steer,false);}
                case "TURN" -> {if(angle>=87)enter("ALIGN",tick);else return new Decision(phase,20|steer,false);}
                case "ALIGN" -> {if(Math.abs(error)<=2){turnEnd=p;enter("ROAD",tick);}else return new Decision(phase,20|(error<0?1:2),false);}
                case "ROAD" -> {if(p.subtract(turnEnd).dot(road)>=6)enter("FINAL_BRAKE",tick);else return new Decision(phase,20|(Math.abs(error)>1?(error<0?1:2):0),false);}
                case "FINAL_BRAKE" -> {if(speed<.002&&tick-entered>=20){if(Math.abs(error)>5)throw new IOException("Final departure heading differs from road");return new Decision(phase,16,true);}else return new Decision(phase,16,false);}
                default -> throw new IOException("Unknown driving phase");
            }
            return new Decision(phase,16,false);
        }
    }

    /** Full native-part swept AABB collision, floor and source checks. The actual driver
     * is excluded from external-entity occupancy, but no block or vehicle part is exempt. */
    static int clear(ServerLevel level,Entity vehicle,ServerPlayer driver,MapAnchor anchor,List<AABB> boxes)throws IOException {
        Set<BlockPos> checked=new HashSet<>();
        for(AABB box:boxes){
            for(BlockPos p:BlockPos.betweenClosed((int)Math.floor(box.minX),(int)Math.floor(box.minY),(int)Math.floor(box.minZ),(int)Math.floor(box.maxX),(int)Math.floor(box.maxY),(int)Math.floor(box.maxZ))){
                BlockPos at=p.immutable();checkSource(level,anchor,at);checked.add(at);
                var state=level.getBlockState(at);
                if(!state.getFluidState().isEmpty()||state.getCollisionShape(level,at).toAabbs().stream().anyMatch(b->b.move(at).intersects(box)))throw new IOException("Native swept route blocked at "+at+" bounds="+box);
            }
            for(int x=(int)Math.floor(box.minX);x<=Math.floor(box.maxX);x++)for(int z=(int)Math.floor(box.minZ);z<=Math.floor(box.maxZ);z++){
                BlockPos floor=new BlockPos(x,anchor.y()-1,z);checkSource(level,anchor,floor);checked.add(floor);
                if(!level.getBlockState(floor).isCollisionShapeFullBlock(level,floor)||!level.getFluidState(floor).isEmpty())throw new IOException("Native swept route unsupported at "+floor);
            }
            if(!level.getEntities(vehicle,box,e->e!=driver&&e.isAlive()).isEmpty())throw new IOException("Native route occupied by other entity");
        }
        return checked.size();
    }
    private static void checkSource(ServerLevel level,MapAnchor anchor,BlockPos p)throws IOException {
        if(!level.hasChunkAt(p))throw new IOException("Native route chunk unloaded");
        if(!level.getBlockState(p).equals(NativeVehicleBay46.source(anchor,p)))throw new IOException("Native route authored source drift at "+p);
    }
    private NativeVehicleDriving50(){}
}
