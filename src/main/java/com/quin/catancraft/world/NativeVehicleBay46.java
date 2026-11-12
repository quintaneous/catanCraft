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
 * Investigates only an exact authored factory/road slice, without driving physics or activation. */
final class NativeVehicleBay46 {
    record Spec(String id,String sha,int boxes,int seats) {}
    static final Spec PICKUP=new Spec("sodayo_pick_up","b53ee2f4cb22708e68decf7af5a26ddb8587d88303f8400c47b32c0b5c22841a",8,8);
    static final Spec LAV=new Spec("lav_150","1773f7ca88cb557dbb0ee3232bfae510f0e75c6ea928f5fabc2f5329f25337ae",10,5);
    record SeatResult(int seat,Vec3 finalExit,List<String> blockers) {}
    record Observation(String id,int rotation,int boxes,int seats,int sweepSamples,AABB initialBounds,
                       int checkedCells,boolean externalRoadQualified,List<SeatResult> seatResults) {
        boolean dismountsQualified(){return seatResults.size()==seats&&seatResults.stream().allMatch(s->s.blockers().isEmpty());}
    }
    static final int ROUTE_SAMPLES=117;
    // Original City A plot 1: factory (-795,81,-774), starter (-750,81,-833).
    // This explicit slice is the only staged/probed source authority, including rotated copies.
    static final int MIN_X=-29,MAX_X=10,MIN_Z=-22,MAX_Z=23,MIN_Y=-2,MAX_Y=7;
    static final int SOURCE_CELLS=(MAX_X-MIN_X+1)*(MAX_Z-MIN_Z+1)*(MAX_Y-MIN_Y+1);
    static BlockPos sourceCell(MapAnchor a,int x,int y,int z){
        int sign=a.rotation()==0?1:-1;return new BlockPos(a.x()+sign*x,a.y()+y,a.z()+sign*z);
    }
    static net.minecraft.world.level.block.state.BlockState source(MapAnchor a,BlockPos p)throws IOException {
        inside(a,p);int sign=a.rotation()==0?1:-1;int x=sign*(p.getX()-a.x()),z=sign*(p.getZ()-a.z()),y=p.getY()-a.y();
        if(x>=-29&&x<=1&&z>=-15&&z<=15){
            var factory=BasicConstructionAssets.load("vehicle_factory");
            return factory.at(x+factory.ax(),y+factory.ay(),z+factory.az()).rotate(ProducerExteriorR2.rotation(a));
        }
        var base=new MapAnchor("vehicle46_original_plot1_baseline",a.x()+45*sign,a.y(),a.z()-59*sign,a.rotation());
        return BasicConstructionAssets.baseline(base,p);
    }
    private static void unchanged(ServerLevel level,MapAnchor a,BlockPos p)throws IOException {
        if(!level.getBlockState(p).equals(source(a,p)))throw new IOException("Authored junction source drift at "+p);
    }
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
        pose(entity,spawn(anchor,outward),anchor.rotation()==0?-90:90);
    }
    static void pose(Entity entity,Vec3 p,float yaw)throws Exception {
        entity.setPos(p.x,p.y,p.z);entity.xo=p.x;entity.yo=p.y;entity.zo=p.z;
        entity.setYRot(yaw);entity.yRotO=yaw;entity.setXRot(0);entity.xRotO=0;
        entity.getClass().getMethod("setPositionO",Vec3.class).invoke(entity,p);
        for(String name:List.of("setRoll","setPrevRoll","setTurretYRot","setTurretYRotO","setGunYRot","setGunYRotO"))entity.getClass().getMethod(name,float.class).invoke(entity,0f);
        entity.getClass().getMethod("updateOBB").invoke(entity);
        entity.getClass().getMethod("invalidateAABBCache").invoke(entity);
        entity.getClass().getMethod("refreshBoundingBoxFromOBBs").invoke(entity);
        requireViewDirection(entity.getViewVector(1),yaw);
    }
    static void requireViewDirection(Vec3 view,float yaw)throws IOException {
        double length=view.length(),rawDot=view.dot(forward(yaw));
        // Vanilla Mth sine/cosine table lookup can produce a slightly non-unit view vector.
        // Compare directions as unit vectors; independently reject invalid/scaled native output.
        double directionalDot=length>0?rawDot/length:Double.NaN;
        if(!Double.isFinite(yaw)||!Double.isFinite(length)||Math.abs(length-1)>.0001
                ||!Double.isFinite(directionalDot)||directionalDot<.999999)
            throw new IOException("Native forward vector differs from declared route heading: yaw="+yaw+" view="+view+" length="+length+" rawDot="+rawDot+" directionalDot="+directionalDot);
    }
    private static Vec3 forward(float yaw){
        double radians=Math.toRadians(yaw);return new Vec3(-Math.sin(radians),0,Math.cos(radians));
    }
    private static Vec3 vertex(Object vertices,int index)throws Exception {
        Object v=Array.get(vertices,index);return new Vec3(v.getClass().getField("x").getDouble(v),v.getClass().getField("y").getDouble(v),v.getClass().getField("z").getDouble(v));
    }
    static List<AABB> boxes(Entity entity,Spec spec)throws Exception {
        List<?> nativeBoxes=(List<?>)entity.getClass().getMethod("getOBBs").invoke(entity);
        if(nativeBoxes.size()!=spec.boxes())throw new IOException("Native OBB inventory changed: "+nativeBoxes.size());
        List<AABB> result=new ArrayList<>();Vec3 nativeForward=null;
        for(Object box:nativeBoxes){
            Object vertices=box.getClass().getMethod("getVertices").invoke(box);
            if(Array.getLength(vertices)!=8)throw new IOException("Incomplete native OBB vertices");
            if(result.isEmpty())nativeForward=vertex(vertices,4).subtract(vertex(vertices,0)).normalize();
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
        Vec3 expected=forward(entity.getYRot());
        double expectedX=Math.abs(expected.x)*length+Math.abs(expected.z)*width,expectedZ=Math.abs(expected.z)*length+Math.abs(expected.x)*width;
        if(nativeForward==null||nativeForward.dot(expected)<.999999||Math.abs(main.getXsize()-expectedX)>.0001||Math.abs(main.getZsize()-expectedZ)>.0001)throw new IOException("Actual native OBB heading does not align with the route: "+main);
        return List.copyOf(result);
    }
    static int clear(ServerLevel level,Entity vehicle,MapAnchor anchor,List<AABB> boxes)throws IOException {
        Set<BlockPos> checked=new HashSet<>();
        for(AABB raw:boxes){
            AABB box=raw;
            for(BlockPos p:BlockPos.betweenClosed((int)Math.floor(box.minX),(int)Math.floor(box.minY),(int)Math.floor(box.minZ),(int)Math.floor(box.maxX),(int)Math.floor(box.maxY),(int)Math.floor(box.maxZ))){
                BlockPos at=p.immutable();inside(anchor,at);loaded(level,at);checked.add(at);
                var state=level.getBlockState(at);
                if(!state.getFluidState().isEmpty()||state.getCollisionShape(level,at).toAabbs().stream().anyMatch(b->b.move(at).intersects(box)))throw new IOException("Native vehicle clearance blocked at "+at);
                unchanged(level,anchor,at);
            }
            for(int x=(int)Math.floor(box.minX);x<=Math.floor(box.maxX);x++)for(int z=(int)Math.floor(box.minZ);z<=Math.floor(box.maxZ);z++){
                BlockPos floor=new BlockPos(x,anchor.y()-1,z);inside(anchor,floor);loaded(level,floor);checked.add(floor);
                if(!level.getBlockState(floor).isCollisionShapeFullBlock(level,floor)||!level.getFluidState(floor).isEmpty())throw new IOException("Unsupported native vehicle footprint at "+floor);
                unchanged(level,anchor,floor);
            }
            if(!level.getEntities(vehicle,box,e->e.isAlive()).isEmpty())throw new IOException("Native vehicle bay occupied by another entity");
        }
        return checked.size();
    }
    private static void inside(MapAnchor a,BlockPos p)throws IOException {
        if(a.rotation()!=0&&a.rotation()!=180)throw new IOException("Unsupported authored source rotation");
        int s=a.rotation()==0?1:-1;int x=s*(p.getX()-a.x()),z=s*(p.getZ()-a.z()),y=p.getY()-a.y();
        if(x<MIN_X||x>MAX_X||z<MIN_Z||z>MAX_Z||y<MIN_Y||y>MAX_Y)throw new IOException("Native envelope leaves explicitly staged authored junction source: "+p);
    }
    private static void loaded(ServerLevel level,BlockPos p)throws IOException{if(!level.hasChunkAt(p))throw new IOException("Native bay chunk is not loaded");}
    static Observation inspect(ServerLevel level,MapAnchor anchor,Spec spec)throws Exception {
        Entity vehicle=create(level,spec);Player player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"CatanBay46"));
        try{
            pose(vehicle,anchor,0);List<AABB> initial=boxes(vehicle,spec);AABB whole=initial.get(0);for(AABB b:initial)whole=whole.minmax(b);
            if(((Number)vehicle.getClass().getMethod("getMaxPassengers").invoke(vehicle)).intValue()!=spec.seats())throw new IOException("Native seat inventory changed");
            int checked=clear(level,vehicle,anchor,initial),samples=1;
            // Exact straight exit to the starter road center, followed independently by both turns.
            List<AABB> previous=initial;
            for(int sample=1;sample<=32;sample++){
                pose(vehicle,anchor,sample*.5);List<AABB> current=boxes(vehicle,spec);
                checked+=clear(level,vehicle,anchor,swept(previous,current,0));previous=current;samples++;
            }
            Vec3 junction=spawn(anchor,16);float startYaw=anchor.rotation()==0?-90:90;
            for(int turn:List.of(-1,1)){
                pose(vehicle,junction,startYaw);previous=boxes(vehicle,spec);
                for(int sample=1;sample<=18;sample++){
                    double radius=horizontalRadius(vehicle);pose(vehicle,junction,startYaw+turn*sample*5);
                    List<AABB> current=boxes(vehicle,spec);
                    // For every native vertex, linear-interpolation error <= max|r''| * theta^2 / 8.
                    // Y is unchanged; this includes the complete angular arc between the two poses.
                    double angularError=Math.max(radius,horizontalRadius(vehicle))*Math.pow(Math.toRadians(5),2)/8+0.00001;
                    checked+=clear(level,vehicle,anchor,swept(previous,current,angularError));previous=current;samples++;
                }
                float heading=startYaw+turn*90;Vec3 direction=forward(heading);
                for(int sample=1;sample<=24;sample++){
                    pose(vehicle,junction.add(direction.scale(sample*.5)),heading);List<AABB> current=boxes(vehicle,spec);
                    checked+=clear(level,vehicle,anchor,swept(previous,current,0));previous=current;samples++;
                }
            }
            com.quin.catancraft.CatanCraft.LOGGER.info("VEHICLE46_NATIVE_ROUTE_PASS vehicle={} rotation={} routePoses={} continuousAngularBound=true northAndSouth=true",spec.id(),anchor.rotation(),samples);
            // Return to the already-qualified bay for the actual final native dismount contract.
            // Complete every seat and retain every refusal; one bad exit must not erase route evidence.
            pose(vehicle,anchor,0);List<SeatResult> seats=new ArrayList<>();
            for(int seat=0;seat<spec.seats();seat++){
                if(vehicle.getClass().getMethod("getSeat",int.class).invoke(vehicle,seat)==null)throw new IOException("Missing native seat");
                player.getPersistentData().putInt("SBWSeatIndex",seat);
                if(((Number)vehicle.getClass().getMethod("getTagSeatIndex",Entity.class).invoke(vehicle,player)).intValue()!=seat)throw new IOException("Native passenger seat tag differs");
                Vec3 indexed=(Vec3)vehicle.getClass().getMethod("getDismountLocationForIndex",LivingEntity.class,int.class).invoke(vehicle,player,seat);
                Vec3 exit=vehicle.getDismountLocationForPassenger(player);
                if(!Double.isFinite(exit.x)||!Double.isFinite(exit.y)||!Double.isFinite(exit.z)||exit.distanceTo(vehicle.position())>10||exit.distanceTo(indexed)>.0001)throw new IOException("Invalid or adjusted native final seat exit");
                AABB body=new AABB(exit.x-.3,exit.y,exit.z-.3,exit.x+.3,exit.y+1.8,exit.z+.3);List<String> blockers=new ArrayList<>();
                try{checked+=clearPassenger(level,anchor,body,exit);}catch(IOException blocked){blockers.add(blocked.getMessage());}
                try{clearOwnVehicle(vehicle,body);}catch(IOException blocked){blockers.add(blocked.getMessage());}
                seats.add(new SeatResult(seat,exit,List.copyOf(blockers)));
                if(seat==0){
                    player.setPos(vehicle.position());if(!player.startRiding(vehicle,true)||player.getVehicle()!=vehicle)throw new IOException("Native first-seat boarding refused");
                    player.stopRiding();
                    if(player.position().distanceTo(exit)>.0001)throw new IOException("Actual stopRiding differs from the observed final native exit: "+player.position()+" vs "+exit);
                }
            }
            player.getPersistentData().remove("SBWSeatIndex");
            return new Observation(spec.id(),anchor.rotation(),spec.boxes(),spec.seats(),samples,whole,checked,true,List.copyOf(seats));
        }finally{player.stopRiding();vehicle.discard();}
    }
    static List<AABB> swept(List<AABB> previous,List<AABB> current,double angularError)throws IOException {
        if(previous.size()!=current.size()||!Double.isFinite(angularError)||angularError<0)throw new IOException("Invalid native sweep");
        List<AABB> result=new ArrayList<>();
        for(int i=0;i<current.size();i++)result.add(current.get(i).minmax(previous.get(i)).inflate(angularError,0,angularError));
        return List.copyOf(result);
    }
    private static double horizontalRadius(Entity entity)throws Exception {
        double radius=0;List<?> nativeBoxes=(List<?>)entity.getClass().getMethod("getOBBs").invoke(entity);
        for(Object box:nativeBoxes){Object vertices=box.getClass().getMethod("getVertices").invoke(box);
            for(int i=0;i<Array.getLength(vertices);i++){Vec3 v=vertex(vertices,i);radius=Math.max(radius,Math.hypot(v.x-entity.getX(),v.z-entity.getZ()));}}
        if(!Double.isFinite(radius)||radius<=0||radius>15)throw new IOException("Invalid native angular sweep radius");return radius;
    }
    static void requireQualifiedDismounts(Observation proof)throws IOException {
        if(!proof.dismountsQualified())throw new IOException("Native final dismount qualification HOLD: "+proof.seatResults());
    }
    static void clearOwnVehicle(Entity vehicle,AABB passenger)throws Exception {
        List<?> nativeBoxes=(List<?>)vehicle.getClass().getMethod("getOBBs").invoke(vehicle);
        for(int i=0;i<nativeBoxes.size();i++){
            Object box=nativeBoxes.get(i);
            // Native OBB/AABB SAT, including the collision/body parts the unadded entity cannot expose
            // through level.getEntities. Do not exempt the encompassing Collision OBB.
            boolean intersects=(Boolean)box.getClass().getMethod("isColliding",box.getClass(),AABB.class).invoke(null,box,passenger);
            if(intersects)throw new IOException("Native final dismount intersects own vehicle OBB "+i+" part="+box.getClass().getField("part").get(box));
        }
    }
    static int clearPassenger(ServerLevel level,MapAnchor anchor,AABB box,Vec3 feet)throws IOException {
        int checked=0;
        for(BlockPos p:BlockPos.betweenClosed((int)Math.floor(box.minX),(int)Math.floor(box.minY),(int)Math.floor(box.minZ),(int)Math.floor(box.maxX),(int)Math.floor(box.maxY),(int)Math.floor(box.maxZ))){
            inside(anchor,p);loaded(level,p);checked++;
            if(!level.getFluidState(p).isEmpty()||level.getBlockState(p).getCollisionShape(level,p).toAabbs().stream().anyMatch(b->b.move(p).intersects(box)))throw new IOException("Native dismount body is blocked at "+p);
            unchanged(level,anchor,p);
        }
        if(feet.y<anchor.y()-.001||feet.y>anchor.y()+1.0)throw new IOException("Native dismount is outside the supported landing height: "+feet);
        for(int x=(int)Math.floor(box.minX);x<=Math.floor(box.maxX);x++)for(int z=(int)Math.floor(box.minZ);z<=Math.floor(box.maxZ);z++){
            BlockPos support=new BlockPos(x,anchor.y()-1,z);inside(anchor,support);loaded(level,support);checked++;
            if(!level.getBlockState(support).isCollisionShapeFullBlock(level,support)||!level.getFluidState(support).isEmpty())throw new IOException("Native dismount footprint lacks nearby support: "+support);
            unchanged(level,anchor,support);
        }
        if(!level.getEntities((Entity)null,box,e->e.isAlive()).isEmpty())throw new IOException("Native dismount space occupied by another entity");
        return checked;
    }
}
