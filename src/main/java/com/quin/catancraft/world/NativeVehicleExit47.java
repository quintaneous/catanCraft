package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.command.NationProcurement;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;

/** Narrow voluntary-egress correction; never creates, buys, moves or repairs a vehicle. */
@Mod.EventBusSubscriber(modid="catancraft")
public final class NativeVehicleExit47 {
    private static final ResourceLocation PICKUP=new ResourceLocation("superbwarfare","sodayo_pick_up");
    private static final ResourceLocation DEFINITION=new ResourceLocation("superbwarfare","sbw/vehicles/sodayo_pick_up.json");
    private static final double[][] BOXES={
        {1.25,.729,3.375,0,.719,.563},{.25,.5,.5,1,.5,2.875},{.25,.5,.5,-1,.5,2.875},
        {.25,.5,.5,1,.5,-1.28},{.25,.5,.5,-1,.5,-1.28},{1.1875,.5,3.41,0,1,.593},
        {1.1875,.375,.1875,0,1.875,.375},{1.1875,.094,.34375,0,2.15625,.9}};
    private static final String[] PARTS={"COLLISION","WHEEL_LEFT","WHEEL_RIGHT","WHEEL_LEFT","WHEEL_RIGHT","BODY","BODY","BODY"};
    private static final double[][] EXITS={{1.5,.11,.81},{-1.5,.11,.81},{1.5,.75,-.7},{-1.5,.75,-.7},{1.5,.75,-2.18},{-1.5,.75,-2.18},{0,.75,-3},{0,.75,-3}};
    private static final int[][] CORNERS={{-1,-1,-1},{1,-1,-1},{1,1,-1},{-1,1,-1},{-1,-1,1},{1,-1,1},{1,1,1},{-1,1,1}};
    private static final ThreadLocal<Attempt> ACTIVE=new ThreadLocal<>();
    private static final ThreadLocal<Probe> PROBE=new ThreadLocal<>();
    record Plan(Entity vehicle,ServerPlayer player,UUID vehicleId,Vec3 position,float yaw,int seat,
                Vec3 nativeExit,Vec3 exit,double correction,int checkedCells) {}
    private static final class Attempt {
        Plan plan;boolean used;String refusal="";
        Attempt(Plan plan){this.plan=plan;}
    }
    private static final class Probe {
        final Entity vehicle;final LivingEntity player;final int seat;int calls;
        Probe(Entity vehicle,LivingEntity player,int seat){this.vehicle=vehicle;this.player=player;this.seat=seat;}
    }
    private NativeVehicleExit47(){}

    /** These existing tags scope egress protection only, never acquisition or accounting authority. */
    static boolean managed(Entity vehicle){
        if(vehicle==null||!PICKUP.equals(ForgeRegistries.ENTITY_TYPES.getKey(vehicle.getType()))
                ||!vehicle.getPersistentData().getBoolean(NationProcurement.PURCHASED_TAG))return false;
        try{UUID nation=UUID.fromString(vehicle.getPersistentData().getString(NationProcurement.NATION_TAG));return !nation.equals(new UUID(0,0));}
        catch(IllegalArgumentException invalid){return false;}
    }

    /** True means this exact voluntary packet was handled (including a refusal), so its handler stops. */
    public static boolean handleVoluntaryPacket(@Nullable ServerPlayer player,boolean ejection){
        Entity vehicle=player==null?null:player.getVehicle();
        if(ejection||!managed(vehicle)||!player.isAlive()||!vehicle.isAlive()||vehicle.isRemoved())return false;
        if(!(player.level() instanceof ServerLevel level)||!level.getServer().isSameThread())return false;
        if(ACTIVE.get()!=null){blocked(player,"An exit is already being checked.");return true;}
        try{
            Plan plan=resolve(vehicle,player);
            Attempt attempt=new Attempt(plan);ACTIVE.set(attempt);
            try{
                player.stopRiding();
                if(player.getVehicle()==vehicle){blocked(player,attempt.refusal.isEmpty()?"The exit was refused; you are still aboard.":attempt.refusal);return true;}
                if(!attempt.used||player.getVehicle()!=null||player.position().distanceTo(attempt.plan.exit())>.0001)
                    throw new IOException("The native dismount did not use the checked exit");
            }finally{ACTIVE.remove();}
            player.setJumping(false);
            var effect=ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("superbwarfare","strike_protection"));
            if(effect!=null)player.addEffect(new MobEffectInstance(effect,10,0,false,false),player);
        }catch(Exception refused){
            ACTIVE.remove();blocked(player,refused.getMessage());
            CatanCraft.LOGGER.debug("Managed pickup voluntary exit refused: {}",refused.toString());
        }
        return true;
    }
    private static void blocked(ServerPlayer player,String reason){
        String message=reason!=null&&reason.startsWith("Stop the pickup")?"Stop the pickup on level ground before leaving.":"Exit blocked. Clear space beside your seat before leaving.";
        player.displayClientMessage(Component.literal(message),true);
    }

    /** Recheck only our active voluntary attempt, before Forge clears the riding relationship. */
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public static void beforeDismount(EntityMountEvent event){
        Attempt attempt=ACTIVE.get();
        if(attempt==null||!event.isDismounting()||event.getEntityMounting()!=attempt.plan.player()
                ||event.getEntityBeingMounted()!=attempt.plan.vehicle()||event.isCanceled())return;
        try{attempt.plan=resolve(attempt.plan.vehicle(),attempt.plan.player());}
        catch(Exception refused){attempt.refusal=refused.getMessage();event.setCanceled(true);}
    }

    /** Only the scoped accepted stopRiding may substitute the provider's indexed final location. */
    @Nullable public static Vec3 overrideExit(Entity vehicle,LivingEntity passenger,int seat){
        Probe probe=PROBE.get();
        if(probe!=null){if(probe.vehicle==vehicle&&probe.player==passenger&&probe.seat==seat)probe.calls++;return null;}
        Attempt attempt=ACTIVE.get();if(attempt==null)return null;Plan plan=attempt.plan;
        if(attempt.used||plan.vehicle()!=vehicle||plan.player()!=passenger||plan.seat()!=seat)return null;
        if(!vehicle.getUUID().equals(plan.vehicleId())||!finite(vehicle.position())||!finite(vehicle.getDeltaMovement())
                ||!Float.isFinite(vehicle.getYRot())||!Float.isFinite(vehicle.getXRot())||vehicle.position().distanceTo(plan.position())>.0001
                ||Math.abs(vehicle.getYRot()-plan.yaw())>.0001||Math.abs(vehicle.getXRot())>.0001
                ||passenger.level()!=vehicle.level()||!vehicle.isAlive()||!passenger.isAlive())
            throw new IllegalStateException("Managed pickup changed during native dismount");
        attempt.used=true;return plan.exit();
    }

    static Plan resolve(Entity vehicle,ServerPlayer player)throws Exception {
        if(!managed(vehicle)||!(vehicle.level() instanceof ServerLevel level)||player.level()!=level
                ||player.getVehicle()!=vehicle||!player.isAlive()||!vehicle.isAlive()||!level.getServer().isSameThread())throw new IOException("Unknown managed pickup/passenger context");
        double roll=((Number)vehicle.getClass().getMethod("getRoll",float.class).invoke(vehicle,1f)).doubleValue();
        if(!finite(vehicle.position())||!finite(vehicle.getDeltaMovement())||!Float.isFinite(vehicle.getYRot())
                ||!Float.isFinite(vehicle.getXRot())||!Double.isFinite(roll))throw new IOException("Nonfinite native pickup pose or motion");
        if(vehicle.getDeltaMovement().lengthSqr()>.0001||Math.abs(vehicle.getXRot())>.0001||Math.abs(roll)>.0001)
            throw new IOException("Stop the pickup on level ground before leaving");
        try(var in=level.getServer().getResourceManager().getResourceOrThrow(DEFINITION).open()){
            byte[] bytes=in.readNBytes(200001);
            if(bytes.length>200000||!TownHallRevisions.sha(bytes).equals(NativeVehicleBay46.PICKUP.sha()))throw new IOException("Changed effective pickup definition");
        }
        vehicle.getClass().getMethod("updateOBB").invoke(vehicle);
        List<?> nativeBoxes=(List<?>)vehicle.getClass().getMethod("getOBBs").invoke(vehicle);
        if(nativeBoxes.size()!=8||((Number)vehicle.getClass().getMethod("getMaxPassengers").invoke(vehicle)).intValue()!=8)throw new IOException("Changed native pickup inventory");
        List<AABB> bounds=new ArrayList<>();
        for(int i=0;i<8;i++){
            Object box=nativeBoxes.get(i);Object vertices=box.getClass().getMethod("getVertices").invoke(box);
            if(Array.getLength(vertices)!=8||!PARTS[i].equals(box.getClass().getField("part").get(box).toString()))throw new IOException("Changed native pickup part");
            AABB extent=null;
            for(int v=0;v<8;v++){
                Vec3 actual=vertex(vertices,v);double[] b=BOXES[i];int[] c=CORNERS[v];
                Vec3 expected=transform(vehicle,b[3]+c[0]*b[0],b[4]+c[1]*b[1],b[5]+c[2]*b[2]);
                if(!finite(actual)||actual.distanceTo(expected)>.0001)throw new IOException("Changed or stale native pickup OBB "+i+" vertex "+v);
                AABB point=new AABB(actual,actual);extent=extent==null?point:extent.minmax(point);
            }
            bounds.add(extent);
        }
        int seat=((Number)vehicle.getClass().getMethod("getSeatIndex",Entity.class).invoke(vehicle,player)).intValue();
        int tag=((Number)vehicle.getClass().getMethod("getTagSeatIndex",Entity.class).invoke(vehicle,player)).intValue();
        if(seat<0||seat>=8||seat!=tag)throw new IOException("Unknown current native seat");
        Probe probe=new Probe(vehicle,player,seat);Vec3 nativeExit;PROBE.set(probe);
        try{nativeExit=(Vec3)vehicle.getClass().getMethod("getDismountLocationForIndex",LivingEntity.class,int.class).invoke(vehicle,player,seat);}
        finally{PROBE.remove();}
        double[] expectedExit=EXITS[seat];
        if(probe.calls!=1||!finite(nativeExit)||nativeExit.distanceTo(transform(vehicle,expectedExit[0],expectedExit[1],expectedExit[2]))>.0001)
            throw new IOException("Unknown native final exit or missing scoped hook");
        int checked=0;int floor=(int)Math.floor(vehicle.getY())-1;
        for(AABB box:bounds)checked+=clear(level,vehicle,player,box,floor,true);
        double yaw=Math.toRadians(vehicle.getYRot());
        Vec3 outward=seat<6?new Vec3(Math.cos(yaw)*(seat%2==0?1:-1),0,Math.sin(yaw)*(seat%2==0?1:-1)):new Vec3(Math.sin(yaw),0,-Math.cos(yaw));
        List<String> reasons=new ArrayList<>();
        for(int step=1;step<=4;step++){
            double correction=step*.125;Vec3 displaced=nativeExit.add(outward.scale(correction));
            double landing=Math.floor(nativeExit.y);Vec3 exit=new Vec3(displaced.x,landing,displaced.z);
            if(nativeExit.y-landing<0||nativeExit.y-landing>1)throw new IOException("Exit landing exceeds bounded drop");
            // Full standing body plus its complete <=1-block downward landing sweep.
            AABB capsule=new AABB(exit.x-.3,exit.y,exit.z-.3,exit.x+.3,nativeExit.y+1.8,exit.z+.3);
            try{
                int candidateChecked=clear(level,vehicle,player,capsule,(int)landing-1,false);
                NativeVehicleBay46.clearOwnVehicle(vehicle,capsule);
                return new Plan(vehicle,player,vehicle.getUUID(),vehicle.position(),vehicle.getYRot(),seat,nativeExit,exit,correction,checked+candidateChecked);
            }catch(IOException blocked){reasons.add(blocked.getMessage());}
        }
        throw new IOException("No safe bounded seat exit: "+reasons);
    }
    private static int clear(ServerLevel level,Entity vehicle,Entity passenger,AABB box,int floor,boolean vehicleBody)throws IOException {
        int checked=0;
        if(!Double.isFinite(box.getSize())||box.getSize()<=0||box.getSize()>12)throw new IOException("Invalid exit envelope");
        for(BlockPos mutable:BlockPos.betweenClosed((int)Math.floor(box.minX),(int)Math.floor(box.minY),(int)Math.floor(box.minZ),(int)Math.floor(box.maxX),(int)Math.floor(box.maxY),(int)Math.floor(box.maxZ))){
            BlockPos p=mutable.immutable();loaded(level,vehicle,p);checked++;
            var state=level.getBlockState(p);
            if(!state.getFluidState().isEmpty()||state.getCollisionShape(level,p).toAabbs().stream().anyMatch(shape->shape.move(p).intersects(box)))throw new IOException("Blocked exit envelope at "+p);
        }
        for(int x=(int)Math.floor(box.minX);x<=Math.floor(box.maxX);x++)for(int z=(int)Math.floor(box.minZ);z<=Math.floor(box.maxZ);z++){
            BlockPos p=new BlockPos(x,floor,z);loaded(level,vehicle,p);checked++;
            if(!level.getBlockState(p).isCollisionShapeFullBlock(level,p)||!level.getFluidState(p).isEmpty())throw new IOException("Missing full-footprint exit support at "+p);
        }
        if(!level.getEntities(vehicle,box,e->e.isAlive()&&e!=passenger&&(!vehicleBody||e.getRootVehicle()!=vehicle)).isEmpty())throw new IOException("Exit envelope occupied by another entity");
        return checked;
    }
    private static void loaded(ServerLevel level,Entity vehicle,BlockPos p)throws IOException {
        if(Math.abs(p.getX()-vehicle.getX())>7||Math.abs(p.getZ()-vehicle.getZ())>7||p.getY()<Math.floor(vehicle.getY())-1||p.getY()>Math.floor(vehicle.getY())+4||level.isOutsideBuildHeight(p)||!level.getWorldBorder().isWithinBounds(p)||!level.hasChunkAt(p))throw new IOException("Exit leaves bounded loaded neighborhood");
    }
    private static Vec3 transform(Entity vehicle,double x,double y,double z){double a=Math.toRadians(vehicle.getYRot());return vehicle.position().add(Math.cos(a)*x-Math.sin(a)*z,y,Math.sin(a)*x+Math.cos(a)*z);}
    private static Vec3 vertex(Object vertices,int index)throws Exception {Object v=Array.get(vertices,index);return new Vec3(v.getClass().getField("x").getDouble(v),v.getClass().getField("y").getDouble(v),v.getClass().getField("z").getDouble(v));}
    private static boolean finite(Vec3 v){return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
}
