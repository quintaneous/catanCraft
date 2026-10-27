package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.map.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Server-thread recovery queue. Only discovers EXISTING unfinished purchases.
 * A transient obstruction retries; altered histories/blocks/ownership remain blocked.
 */
public final class PaidRecoveryService {
    private PaidRecoveryService(){}
    private record Job(Path path,String city,boolean civic,long retryAt){}
    private record Active(Job job,SectorChunkLease lease,int startTick){}
    private static MinecraftServer owner;
    private static final Map<Path,Job> queue=new LinkedHashMap<>();
    private static final Map<Path,String> status=new LinkedHashMap<>();
    private static final Map<Path,String> cities=new HashMap<>();
    private static Active active;
    static boolean isRunning(MinecraftServer server){return owner==server&&active!=null;}
    public static String status(MinecraftServer server,String city){
        if(owner!=server)return "";
        for(var e:status.entrySet())if(city.equals(cities.get(e.getKey()))&&!e.getValue().isEmpty())return e.getValue();return "";
    }
    static void stopped(MinecraftServer server){if(owner!=server)return;if(active!=null)active.lease().close();active=null;queue.clear();status.clear();cities.clear();owner=null;}
    private static List<Path> originals(Path root,String city){return List.of(
        root.resolve("data/catancraft_r5_auto_"+city+"_commercial_district_v1.json"),
        root.resolve("data/catancraft_r5_auto_"+city+"_logistics_center_v1.json"),
        root.resolve("data/catancraft_th_prestige_"+city+"_4_v1.dat"),
        root.resolve("data/catancraft_th_prestige_"+city+"_5_v1.dat"));}
    private static boolean unfinished(Path p)throws IOException{
        byte[] bytes=PaidConstructionRecovery.readBounded(p,8*1024*1024);boolean civic=p.toString().endsWith(".json");
        String phase=civic?CivicPurchaseRecovery.read(bytes).get("phase").getAsString():PrestigePurchaseRecovery.read(bytes).getString("phase");
        if(phase.equals("COMMITTED"))return false;
        return !PaidConstructionRecovery.complete(p,bytes);
    }
    public static void requireClear(MinecraftServer server,String city)throws IOException{
        if(!city.matches("[a-p]"))return;
        Path root=server.getWorldPath(LevelResource.ROOT).toRealPath();
        for(Path p:originals(root,city))if(Files.exists(p,LinkOption.NOFOLLOW_LINKS)&&unfinished(p))
            throw new IOException("Existing construction is awaiting recovery; do not buy again. Check this city's recovery status in /nation gui.");
    }
    private static void discover(MinecraftServer server)throws IOException{
        Path root=ConstructionWorldAuthorization.authorize(server);
        for(char c='a';c<='p';c++){
            String city=String.valueOf(c);
            for(Path path:originals(root,city)){
                if(status.containsKey(path)||!Files.exists(path,LinkOption.NOFOLLOW_LINKS))continue;
                cities.put(path,city);
                try{
                    if(!unfinished(path)){status.put(path,"");continue;}
                    queue.put(path,new Job(path,city,path.toString().endsWith(".json"),server.getTickCount()));
                    status.put(path,"Construction recovery queued; original purchase retained, no repeat charge.");
                }catch(Exception e){blocked(path,e);}
            }
        }
    }
    private static void blocked(Path path,Exception e){String text="Construction recovery blocked: "+e.getMessage()+". Preserve original blocks and journals.";status.put(path,text);CatanCraft.LOGGER.error("PAID_CONSTRUCTION_RECOVERY_BLOCKED {} {}",path.getFileName(),e.toString());}
    static void tick(MinecraftServer server){
        if(owner!=server){if(owner!=null)stopped(owner);owner=server;}
        if(SectorService.hasActiveOperation(server)||CityBasePreparation.isRunning(server))return;
        if(active!=null){
            Active work=active;Job job=work.job();boolean waiting=false;
            try{
                work.lease().keepAlive();
                if(!work.lease().ready()){
                    if(server.getTickCount()-work.startTick()>600)throw new PreparationDeferred("Construction chunks/entities are still loading");waiting=true;return;
                }
                Path root=ConstructionWorldAuthorization.authorize(server);var def=MapDefinitionManager.territory(job.city());ProducerService.validateMap(def);
                if(!root.resolve("data").equals(job.path().getParent()))throw new IOException("Recovery world path changed");
                var data=CatanSavedData.get(server);var level=server.overworld();
                TownHallRepairService.requireNoPending(server,job.city());
                CivicPurchaseRecovery.Authorization auth=()->{
                    // All writes here are one server-thread operation; recheck actual owner
                    // in the adapter per boundary. Never transplant a world authorization.
                    if(!root.equals(server.getWorldPath(LevelResource.ROOT).toRealPath()))throw new IOException("Recovery world changed");
                };
                var env=job.civic()?CivicPurchaseRecovery.environment(server,data,level,def,root,job.path(),auth):PrestigePurchaseRecovery.environment(server,data,level,def,root,job.path(),auth);
                var result=PaidConstructionRecovery.resume(env);status.put(job.path(),"");
                CatanCraft.LOGGER.info("PAID_CONSTRUCTION_RECOVERED city={} journal={} writes={} original preserved; receipt verified",job.city(),job.path().getFileName(),result.writes());
            }catch(Exception e){
                if(PreparationDeferred.causedBy(e)){
                    queue.put(job.path(),new Job(job.path(),job.city(),job.civic(),server.getTickCount()+100));
                    status.put(job.path(),"Construction recovery waiting: "+e.getMessage()+"; retries automatically, no repeat charge.");
                }else blocked(job.path(),e);
            }finally{
                // An early wait return must keep its lease; readiness is checked again next tick.
                if(!waiting){work.lease().close();active=null;}
            }
            return;
        }
        if(server.getTickCount()%100==0)try{discover(server);}catch(IOException unauthorized){return;}
        for(Job job:new ArrayList<>(queue.values())){
            if(server.getTickCount()<job.retryAt())continue;queue.remove(job.path());
            try{
                var def=MapDefinitionManager.territory(job.city());ProducerService.validateMap(def);
                var r=def.restorationRegion();AABB box=new AABB(r.minX(),r.minY(),r.minZ(),r.maxX()+1,r.maxY()+1,r.maxZ()+1).inflate(2);
                active=new Active(job,SectorChunkLease.acquire(server.overworld(),box),server.getTickCount());
                status.put(job.path(),"Construction recovery loading original transaction; no repeat charge.");
            }catch(Exception e){blocked(job.path(),e);}return;
        }
    }
}
