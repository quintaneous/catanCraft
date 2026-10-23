package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.map.MapDefinitionManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Public streets are part of activated base cities in an explicitly authorized disposable demo.
 * Reuses the exact R5 journaled transaction, never repairs an arbitrary save or replays existing history.
 */
final class CityBasePreparation {
    private static MinecraftServer owner;
    private static final Map<String,String> attempted=new HashMap<>();
    private static Pending pending;
    private interface EnvironmentSource { StreetscapeTransaction.Environment get() throws IOException; }
    private record Pending(String city,SectorChunkLease lease,int startTick,EnvironmentSource source){}
    private CityBasePreparation(){}
    static boolean isRunning(MinecraftServer server){return owner==server&&pending!=null;}
    static String reason(MinecraftServer server,String city){
        if(owner!=server)return "";
        if(pending!=null&&pending.city().equals(city))return "Base city paths are being prepared automatically. No purchase required.";
        return attempted.getOrDefault(city,"");
    }
    static void stopped(MinecraftServer server){
        if(owner!=server)return;
        if(pending!=null)pending.lease().close();pending=null;attempted.clear();owner=null;
    }
    /** Same queued execution path with a synthetic environment, exclusively in a GameTest server. */
    static void queueFixture(String key,StreetscapeTransaction.Environment env)throws IOException {
        MinecraftServer server=env.level().getServer();
        Path root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path file=env.file().toAbsolutePath().normalize();
        if(!(server instanceof net.minecraft.gametest.framework.GameTestServer)||!file.startsWith(root.resolve("data")))throw new IOException("Base city fixture requires isolated GameTest storage");
        if(Files.exists(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("Existing public history must be preserved, not initialized again");
        if(pending!=null)throw new IOException("Another city is being initialized");
        owner=server;AABB bounds=null;
        for(var cell:env.plan().cells()){AABB b=new AABB(cell.pos()).inflate(2);bounds=bounds==null?b:bounds.minmax(b);}
        if(bounds==null)throw new IOException("Empty public fixture");
        pending=new Pending(key,SectorChunkLease.acquire(env.level(),bounds),server.getTickCount(),()->env);attempted.put(key,"");
    }
    static void tick(MinecraftServer server){
        if(owner!=server){if(owner!=null)stopped(owner);owner=server;}
        if(SectorService.hasActiveOperation(server))return;
        if(pending!=null){
            Pending work=pending;
            try {
                work.lease().keepAlive();
                if(!work.lease().ready()){
                    if(server.getTickCount()-work.startTick()>600)throw new IOException("City preparation timed out loading entities; restart/review required");
                    return;
                }
                // Reauthorize at the actual write boundary, not only when the job was queued.
                var env=work.source().get();
                if(Files.exists(env.file(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Existing public-space history appeared; preserve it for explicit recovery");
                var result=StreetscapeTransaction.run(env,StreetscapeTransaction.Action.APPLY);
                attempted.put(work.city(),"");
                CatanCraft.LOGGER.info("BASE_CITY_PUBLIC_READY city={} writes={} zero charge; normal city initialization",work.city(),result.writes());
            }catch(Exception problem){
                attempted.put(work.city(),"Base city preparation refused: "+problem.getMessage()+". Existing blocks and history preserved.");
                CatanCraft.LOGGER.error("Base city preparation refused for {}",work.city(),problem);
            }
            work.lease().close();pending=null;return;
        }
        if(server.getTickCount()%20!=0)return;
        Path root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        if(!Files.isRegularFile(root.resolve(FreshDemoAuthorization.MARKER)))return;
        try{FreshDemoAuthorization.authorize(server);TownHallRepairService.authorize(server);}catch(IOException notAuthorized){return;}
        var data=CatanSavedData.get(server);
        for(var territory:data.territories()){
            String city=territory.id();
            if(!city.matches("[a-p]")||attempted.containsKey(city)||territory.ownerNationId()==null||data.nation(territory.ownerNationId())==null||data.placedMapAsset("territory:"+city+":city_base")==null)continue;
            Path history=root.resolve("data/catancraft_r5_public_"+city+"_v1.dat");
            // COMMITTED and pending manual history are both preserved, not adopted or overwritten.
            if(Files.exists(history,LinkOption.NOFOLLOW_LINKS)){attempted.put(city,"");continue;}
            try {
                DefenseCityReservations.requireAvailable(root,city,null);
                var def=MapDefinitionManager.territory(city);ProducerService.validateMap(def);
                var plan=StreetscapeR5.plan(def.cityCenter());AABB bounds=null;
                for(var cell:plan.cells()){AABB b=new AABB(cell.pos()).inflate(2);bounds=bounds==null?b:bounds.minmax(b);}
                for(var b:plan.boxes())bounds=bounds==null?b:bounds.minmax(b);
                if(bounds==null)throw new IOException("Empty city public contract");
                pending=new Pending(city,SectorChunkLease.acquire(server.overworld(),bounds),server.getTickCount(),()->{
                    FreshDemoAuthorization.authorize(server);
                    return StreetscapeService.context(server,city,true);
                });
                attempted.put(city,"");
                CatanCraft.LOGGER.info("Preparing built-in public paths for activated city {}",city);
            }catch(Exception conflict){attempted.put(city,"Base city preparation refused: "+conflict.getMessage());CatanCraft.LOGGER.error("City initialization refused for {}",city,conflict);}
            return;
        }
    }
}
