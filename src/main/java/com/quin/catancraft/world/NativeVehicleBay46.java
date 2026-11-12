package com.quin.catancraft.world;

import com.mojang.authlib.GameProfile;
import com.quin.catancraft.map.MapAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.registries.ForgeRegistries;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;

/** Unregistered native geometry investigator. No purchasing, spawning, debit or world repair.
 * Qualifies only the authored factory interior; the external road remains a separate gate. */
final class NativeVehicleBay46 {
    record Spec(String id,String sha,int boxes,int seats) {}
    static final Spec PICKUP=new Spec("sodayo_pick_up","b53ee2f4cb22708e68decf7af5a26ddb8587d88303f8400c47b32c0b5c22841a",8,8);
    static final Spec LAV=new Spec("lav_150","1773f7ca88cb557dbb0ee3232bfae510f0e75c6ea928f5fabc2f5329f25337ae",10,5);
    record Observation(String id,int rotation,int boxes,int seats,int sweepSamples, AABB initialBounds,
                       int checkedCells, boolean externalRoadQualified) {}
    private NativeVehicleBay46(){}
    static Entity create(ServerLevel level,Spec spec)throws Exception {
        if(!List.of(PICKUP,LAV).contains(spec))throw new IOException("Unknown native bay vehicle identity");
        ResourceLocation resource=new ResourceLocation("superbwarfare","sbw/vehicles/"+spec.id()+".json");
        try(var in=level.getServer().getResourceManager().getResourceOrThrow(resource).open()){
            byte[] bytes=in.readNBytes(200001);
            if(bytes.length>200000||!TownHallRevisions.sha(bytes).equals(spec.sha()))throw new IOException("Changed native vehicle definition");
        }
        var type=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("superbwarfare",spec.id()));
        if(type==null)throw new IOException("Missing native vehicle type");
        Entity entity=type.create(level);if(entity==null)throw new IOException("Native vehicle initialization failed");
        return entity;
    }
    static Vec3 spawn(MapAnchor anchor,double outward) {
        int sign=anchor.rotation()==0?1:-1;
        if(anchor.rotation()!=0&&anchor.rotation()!=180)throw new IllegalArgumentException("Unsupported factory rotation");
        return new Vec3(anchor.x()+.5+sign*(-12+outward),anchor.y()+.1,anchor.z()+.5);
    }
    static void pose(Entity entity,MapAnchor anchor,double outward)throws Exception {
        Vec3 p=spawn(anchor,outward);entity.setPos(p.x,p.y,p.z);entity.xo=p.x;entity.yo=p.y;entity.zo=p.z;
        float yaw=anchor.rotation()==0?-90:90;
        entity.setYRot(yaw);entity.yRotO=yaw;entity.setXRot(0);entity.xRotO=0;
        entity.getClass().getMethod("setPositionO",Vec3.class).invoke(entity,p);
        for(String name:List.of("setTurretYRot","setTurretYRotO","setGunYRot","setGunYRotO"))entity.getClass().getMethod(name,float.class).invoke(entity,0f);
        entity.getClass().getMethod("updateOBB").invoke(entity);
        entity.getClass().getMethod("invalidateAABBCache").invoke(entity);
        entity.getClass().getMethod("refreshBoundingBoxFromOBBs").invoke(entity);
        if(entity.getViewVector(1).dot(new Vec3(anchor.rotation()==0?1:-1,0,0))<.999)throw new IOException("Native forward vector differs from declared lane heading");
    }
    static List<AABB> boxes(Entity entity,Spec spec)throws Exception {
        List<?> nativeBoxes=(List<?>)entity.getClass().getMethod("getOBBs").invoke(entity);
        if(nativeBoxes.size()!=spec.boxes())throw new IOException("Native OBB inventory changed: "+nativeBoxes.size());
        List<AABB> result=new ArrayList<>();
        for(Object box:nativeBoxes){
            Object vertices=box.getClass().getMethod("getVertices").invoke(box);
            if(Array.getLength(vertices)!=8)throw new IOException("Incomplete native OBB vertices");
            double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
            for(int i=0;i<8;i++){
                Object v=Array.get(vertices,i);double x=v.getClass().getField("x").getDouble(v),y=v.getClass().getField("y").getDouble(v),z=v.getClass().getField("z").getDouble(v);
                if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IOException("Nonfinite native OBB");
                minX=Math.min(minX,x);minY=Math.min(minY,y);minZ=Math.min(minZ,z);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);maxZ=Math.max(maxZ,z);
            }
            AABB a=new AABB(minX,minY,minZ,maxX,maxY,maxZ);
            if(a.getXsize()<=0||a.getYsize()<=0||a.getZsize()<=0||a.getSize()>20||a.getCenter().distanceTo(entity.position())>15)throw new IOException("Uninitialized or unbounded native OBB");
            result.add(a);
        }
        AABB main=result.get(0);double length=spec.equals(PICKUP)?6.75:6.375,width=spec.equals(PICKUP)?2.5:2.75;
        if(Math.abs(main.getXsize()-length)>.0001||Math.abs(main.getZsize()-width)>.0001)throw new IOException("Actual native OBB heading does not align with the departure lane: "+main);
        return List.copyOf(result);
    }
    static int clear(ServerLevel level,Entity vehicle,MapAnchor anchor,List<AABB> boxes)throws IOException {
        Set<BlockPos> checked=new HashSet<>();
        for(AABB raw:boxes){
            AABB box=raw.deflate(.00001);
            for(BlockPos p:BlockPos.betweenClosed((int)Math.floor(box.minX),(int)Math.floor(box.minY),(int)Math.floor(box.minZ),(int)Math.floor(box.maxX),(int)Math.floor(box.maxY),(int)Math.floor(box.maxZ))){
                BlockPos at=p.immutable();inside(anchor,at);loaded(level,at);checked.add(at);
                var state=level.getBlockState(at);
                if(!state.getFluidState().isEmpty()||state.getCollisionShape(level,at).toAabbs().stream().anyMatch(b->b.move(at).intersects(box)))throw new IOException("Native vehicle clearance blocked at "+at);
            }
            for(int x=(int)Math.floor(box.minX);x<=Math.floor(box.maxX);x++)for(int z=(int)Math.floor(box.minZ);z<=Math.floor(box.maxZ);z++){
                BlockPos floor=new BlockPos(x,anchor.y()-1,z);inside(anchor,floor);loaded(level,floor);checked.add(floor);
                if(!level.getBlockState(floor).isCollisionShapeFullBlock(level,floor)||!level.getFluidState(floor).isEmpty())throw new IOException("Unsupported native vehicle footprint at "+floor);
            }
            if(!level.getEntities(vehicle,box,e->e.isAlive()).isEmpty())throw new IOException("Native vehicle bay occupied by another entity");
        }
        return checked.size();
    }
    private static void inside(MapAnchor a,BlockPos p)throws IOException {
        int s=a.rotation()==0?1:-1;int x=s*(p.getX()-a.x())+29,z=s*(p.getZ()-a.z())+15,y=p.getY()-a.y()+2;
        if(x<0||x>=31||z<0||z>=31||y<0||y>=47)throw new IOException("Native envelope leaves authored factory source: "+p);
    }
    private static void loaded(ServerLevel level,BlockPos p)throws IOException{if(!level.hasChunkAt(p))throw new IOException("Native bay chunk is not loaded");}
    static Observation inspect(ServerLevel level,MapAnchor anchor,Spec spec)throws Exception {
        Entity vehicle=create(level,spec);Player player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"CatanBay46"));
        try{
            pose(vehicle,anchor,0);List<AABB> initial=boxes(vehicle,spec);AABB whole=initial.get(0);for(AABB b:initial)whole=whole.minmax(b);
            if(((Number)vehicle.getClass().getMethod("getMaxPassengers").invoke(vehicle)).intValue()!=spec.seats())throw new IOException("Native seat inventory changed");
            int checked=clear(level,vehicle,anchor,initial);
            for(int seat=0;seat<spec.seats();seat++){
                if(vehicle.getClass().getMethod("getSeat",int.class).invoke(vehicle,seat)==null)throw new IOException("Missing native seat");
                Vec3 exit=(Vec3)vehicle.getClass().getMethod("getDismountLocationForIndex",LivingEntity.class,int.class).invoke(vehicle,player,seat);
                if(!Double.isFinite(exit.x)||!Double.isFinite(exit.y)||!Double.isFinite(exit.z)||exit.distanceTo(vehicle.position())>10)throw new IOException("Invalid native seat exit");
                AABB body=new AABB(exit.x-.3,exit.y,exit.z-.3,exit.x+.3,exit.y+1.8,exit.z+.3);
                checked+=clearPassenger(level,anchor,body,exit);
                if(seat==0){player.setPos(exit);if(!player.startRiding(vehicle,true)||player.getVehicle()!=vehicle)throw new IOException("Native boarding refused");player.stopRiding();}
            }
            // A conservative continuous swept AABB for every native part between each half-block pose.
            // Stop inside authored frontage; external road/turning remains unqualified.
            List<AABB> previous=initial;
            for(int sample=1;sample<=16;sample++){
                pose(vehicle,anchor,sample*.5);List<AABB> current=boxes(vehicle,spec);List<AABB> sweep=new ArrayList<>();
                for(int i=0;i<current.size();i++)sweep.add(current.get(i).minmax(previous.get(i)));
                checked+=clear(level,vehicle,anchor,sweep);previous=current;
            }
            return new Observation(spec.id(),anchor.rotation(),spec.boxes(),spec.seats(),17,whole,checked,false);
        }finally{player.stopRiding();vehicle.discard();}
    }
    static int clearPassenger(ServerLevel level,MapAnchor anchor,AABB box,Vec3 feet)throws IOException {
        int checked=0;
        for(BlockPos p:BlockPos.betweenClosed((int)Math.floor(box.minX),(int)Math.floor(box.minY),(int)Math.floor(box.minZ),(int)Math.floor(box.maxX),(int)Math.floor(box.maxY),(int)Math.floor(box.maxZ))){
            inside(anchor,p);loaded(level,p);checked++;
            if(!level.getFluidState(p).isEmpty()||level.getBlockState(p).getCollisionShape(level,p).toAabbs().stream().anyMatch(b->b.move(p).intersects(box)))throw new IOException("Native dismount body is blocked at "+p);
        }
        if(feet.y<anchor.y()-.001||feet.y>anchor.y()+1.0)throw new IOException("Native dismount is outside the supported landing height: "+feet);
        for(int x=(int)Math.floor(box.minX);x<=Math.floor(box.maxX);x++)for(int z=(int)Math.floor(box.minZ);z<=Math.floor(box.maxZ);z++){
            BlockPos support=new BlockPos(x,anchor.y()-1,z);inside(anchor,support);loaded(level,support);checked++;
            if(!level.getBlockState(support).isCollisionShapeFullBlock(level,support)||!level.getFluidState(support).isEmpty())throw new IOException("Native dismount footprint lacks nearby support: "+support);
        }
        if(!level.getEntities((Entity)null,box,e->e.isAlive()).isEmpty())throw new IOException("Native dismount space occupied by another entity");
        return checked;
    }
}
