package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.map.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** One server-thread queue for paid construction and EXISTING producer/legacy defense recovery.
 * Leases are bounded and released on every exit; only typed transient conditions retry.
 * Fresh requests exist only after a normal player purchase, never from startup discovery.
 */
public final class PaidRecoveryService {
    private PaidRecoveryService(){}
    private enum Kind { CIVIC, PRESTIGE, BASIC, PRODUCER, DEFENSE, FIXTURE }
    private record Job(Path path,String city,Kind kind,long retryAt,BasicConstructionPurchase.Request fresh){}
    private record Active(Job job,SectorChunkLease lease,int startTick){}
    private static MinecraftServer owner;
    private static final Map<Path,Job> queue=new LinkedHashMap<>();
    private static final Map<Path,String> status=new LinkedHashMap<>();
    private static final Map<Path,String> cities=new HashMap<>();
    private static final Map<Path,Fixture> fixtures=new HashMap<>();
    private static Active active;
    interface Work { int run() throws Exception; }
    private record Fixture(AABB bounds,Work work){}
    static boolean isRunning(MinecraftServer server){return owner==server&&active!=null;}
    public static String status(MinecraftServer server,String city){
        if(owner!=server)return "";
        for(var e:status.entrySet())if(city.equals(cities.get(e.getKey()))&&!e.getValue().isEmpty())return e.getValue();return "";
    }
    static void stopped(MinecraftServer server){if(owner!=server)return;if(active!=null)active.lease().close();active=null;queue.clear();status.clear();cities.clear();fixtures.clear();owner=null;}
    private static void bind(MinecraftServer server){if(owner!=server){if(owner!=null)stopped(owner);owner=server;}}
    private static List<Path> originals(Path root,String city){List<Path> out=new ArrayList<>(List.of(
        root.resolve("data/catancraft_r5_auto_"+city+"_commercial_district_v1.json"),
        root.resolve("data/catancraft_r5_auto_"+city+"_logistics_center_v1.json"),
        root.resolve("data/catancraft_th_prestige_"+city+"_4_v1.dat"),
        root.resolve("data/catancraft_th_prestige_"+city+"_5_v1.dat")));
        out.addAll(BasicConstructionPurchase.originals(root,city));return out;
    }
    private static Kind kind(Path p){String n=p.getFileName().toString();return n.startsWith("catancraft_basic_purchase_")?Kind.BASIC:n.startsWith("catancraft_producer_")?Kind.PRODUCER:n.startsWith("catancraft_defense_")?Kind.DEFENSE:n.endsWith(".json")?Kind.CIVIC:Kind.PRESTIGE;}
    private static boolean unfinished(Path p)throws IOException {
        Kind k=kind(p);
        if(k==Kind.BASIC)return BasicConstructionPurchase.unfinished(p);
        if(k==Kind.PRODUCER){var phase=ProducerTransaction.phase(ProducerJournal.read(p));return phase!=ProducerTransaction.Phase.COMMITTED&&phase!=ProducerTransaction.Phase.ROLLED_BACK;}
        if(k==Kind.DEFENSE){String phase=ProducerJournal.read(p).getString("phase");if(!Set.of("PREPARED","RESERVED","APPLYING","COMMITTED","ROLLING_BACK","ROLLED_BACK").contains(phase))throw new IOException("Unknown legacy defense phase");return !phase.equals("COMMITTED")&&!phase.equals("ROLLED_BACK");}
        byte[] bytes=PaidConstructionRecovery.readBounded(p,8*1024*1024);
        String phase=k==Kind.CIVIC?CivicPurchaseRecovery.read(bytes).get("phase").getAsString():PrestigePurchaseRecovery.read(bytes).getString("phase");
        return !phase.equals("COMMITTED")&&!PaidConstructionRecovery.complete(p,bytes);
    }
    private static List<Path> legacy(Path root,String city)throws IOException {
        Path dir=root.resolve("data");if(!Files.isDirectory(dir))return List.of();List<Path> out=new ArrayList<>();
        for(String pattern:List.of("catancraft_producer_"+city+"_*_r{1,2}.dat","catancraft_defense_"+city+"_*_r4a_r6_1.dat"))
            try(var stream=Files.newDirectoryStream(dir,pattern)){for(Path p:stream){if(out.size()>=4224)throw new IOException("Too many construction histories");out.add(p);}}
        out.sort(Comparator.comparing(Path::toString));return out;
    }
    public static void requireClear(MinecraftServer server,String city)throws IOException {
        if(!city.matches("[a-p]"))return;
        if(owner==server){if(active!=null&&active.job().city().equals(city)||queue.values().stream().anyMatch(j->j.city().equals(city)))throw new IOException("A construction request/recovery is already queued for this city");}
        Path root=server.getWorldPath(LevelResource.ROOT).toRealPath();List<Path> all=originals(root,city);all.addAll(legacy(root,city));
        for(Path p:all)if(Files.exists(p,LinkOption.NOFOLLOW_LINKS)&&unfinished(p))throw new IOException("Existing construction awaits recovery; do not buy again. Check this city's status in /nation gui.");
    }
    static void submitFresh(MinecraftServer server,BasicConstructionPurchase.Request request,Path path)throws IOException {
        bind(server);if(queue.containsKey(path)||active!=null&&active.job().path().equals(path))throw new IOException("This purchase is already queued");
        queue.put(path,new Job(path,request.city(),Kind.BASIC,server.getTickCount(),request));cities.put(path,request.city());status.put(path,"Construction queued; full-site preflight before placement or charge.");
    }
    /** Private-to-package native harness: no production authorization bypass. */
    static void queueFixture(MinecraftServer server,Path path,String city,AABB bounds,Work work)throws IOException {
        if(!(server instanceof net.minecraft.gametest.framework.GameTestServer)||!path.toAbsolutePath().normalize().startsWith(server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().resolve("data")))throw new IOException("Recovery fixture requires isolated GameTest storage");
        bind(server);if(active!=null)throw new IOException("Another construction fixture is active");fixtures.put(path,new Fixture(bounds,work));queue.put(path,new Job(path,city,Kind.FIXTURE,server.getTickCount(),null));cities.put(path,city);status.put(path,"Fixture queued");
    }
    static boolean settledFixture(MinecraftServer server,Path path){return owner==server&&!queue.containsKey(path)&&(active==null||!active.job().path().equals(path))&&status.containsKey(path);}
    private static void discover(MinecraftServer server)throws IOException {
        Path root=ConstructionWorldAuthorization.authorize(server);
        for(char c='a';c<='p';c++){
            String city=String.valueOf(c);List<Path> all=originals(root,city);all.addAll(legacy(root,city));
            for(Path path:all){
                if(status.containsKey(path)||!Files.exists(path,LinkOption.NOFOLLOW_LINKS))continue;cities.put(path,city);
                try{
                    if(!unfinished(path)){status.put(path,"");continue;}
                    if(kind(path)==Kind.PRODUCER||kind(path)==Kind.DEFENSE){CompoundTag identity=ProducerJournal.read(path).getCompound("identity");if(!city.equals(identity.getString("city"))||!root.toString().equals(identity.getString("world")))throw new IOException("Foreign historical construction identity");}
                    queue.put(path,new Job(path,city,kind(path),server.getTickCount(),null));status.put(path,"Construction recovery queued; original transaction retained, no repeat charge.");
                }catch(Exception e){blocked(server,path,e);}
            }
        }
    }
    private static void notifyOwner(MinecraftServer server,Path path,String text){var t=CatanSavedData.get(server).territory(cities.get(path));if(t==null||t.ownerNationId()==null)return;var n=CatanSavedData.get(server).nation(t.ownerNationId());if(n==null)return;var p=server.getPlayerList().getPlayer(n.leaderId());if(p!=null){p.sendSystemMessage(net.minecraft.network.chat.Component.literal(text));com.quin.catancraft.ui.NationDashboard.refreshDefenses(p,text);}}
    private static void blocked(MinecraftServer server,Path path,Exception e){String text="Construction blocked: "+e.getMessage()+". Preserve any existing blocks/journals; do not repurchase interrupted work.";status.put(path,text);CatanCraft.LOGGER.error("CONSTRUCTION_RECOVERY_BLOCKED {} {}",path.getFileName(),e.toString());notifyOwner(server,path,text);}
    private static int execute(MinecraftServer server,Job job)throws Exception {
        if(job.kind()==Kind.FIXTURE)return fixtures.get(job.path()).work().run();
        Path root=ConstructionWorldAuthorization.authorize(server);var def=MapDefinitionManager.territory(job.city());ProducerService.validateMap(def);
        if(!root.resolve("data").equals(job.path().getParent()))throw new IOException("Recovery world path changed");
        TownHallRepairService.requireNoPending(server,job.city());
        if(job.kind()==Kind.PRODUCER)return ProducerService.recoverExisting(server,job.path()).changedBlocks();
        if(job.kind()==Kind.DEFENSE)return DefenseService.recoverExisting(server,job.path()).changedBlocks();
        if(job.kind()==Kind.BASIC){
            if(job.fresh()!=null&&!Files.exists(job.path(),LinkOption.NOFOLLOW_LINKS))BasicConstructionPurchase.prepare(server,job.path(),job.fresh());
            return PaidConstructionRecovery.resume(BasicConstructionPurchase.environment(server,job.path())).writes();
        }
        var data=CatanSavedData.get(server);var level=server.overworld();
        CivicPurchaseRecovery.Authorization auth=()->{if(!root.equals(server.getWorldPath(LevelResource.ROOT).toRealPath()))throw new IOException("Recovery world changed");};
        var env=job.kind()==Kind.CIVIC?CivicPurchaseRecovery.environment(server,data,level,def,root,job.path(),auth):PrestigePurchaseRecovery.environment(server,data,level,def,root,job.path(),auth);
        return PaidConstructionRecovery.resume(env).writes();
    }
    static void tick(MinecraftServer server){
        bind(server);if(SectorService.hasActiveOperation(server)||CityBasePreparation.isRunning(server))return;
        if(active!=null){
            Active work=active;Job job=work.job();boolean waiting=false;
            try{
                work.lease().keepAlive();if(!work.lease().ready()){if(server.getTickCount()-work.startTick()>600)throw new PreparationDeferred("Construction chunks/entities are still loading");waiting=true;return;}
                int writes=execute(server,job);status.put(job.path(),"");CatanCraft.LOGGER.info("CONSTRUCTION_RECOVERED city={} journal={} writes={} receipt/history verified",job.city(),job.path().getFileName(),writes);notifyOwner(server,job.path(),"Construction completed in "+job.city()+"; original transaction settled, "+writes+" block changes.");
            }catch(Exception e){
                if(PreparationDeferred.causedBy(e)){
                    queue.put(job.path(),new Job(job.path(),job.city(),job.kind(),server.getTickCount()+100,job.fresh()));status.put(job.path(),"Construction waiting: "+e.getMessage()+"; retries automatically, no repeat charge.");
                }else blocked(server,job.path(),e);
            }finally{if(!waiting){work.lease().close();active=null;}}
            return;
        }
        if(server.getTickCount()%100==0)try{discover(server);}catch(IOException unauthorized){/* Authorization never created here. */}
        for(Job job:new ArrayList<>(queue.values())){
            if(server.getTickCount()<job.retryAt())continue;queue.remove(job.path());
            try{
                AABB box;if(job.kind()==Kind.FIXTURE)box=fixtures.get(job.path()).bounds();else{
                    var def=MapDefinitionManager.territory(job.city());ProducerService.validateMap(def);
                    if(job.kind()==Kind.BASIC&&job.fresh()!=null)box=BasicConstructionPurchase.bounds(job.fresh(),def);else{var r=def.restorationRegion();box=new AABB(r.minX(),r.minY(),r.minZ(),r.maxX()+1,r.maxY()+1,r.maxZ()+1).inflate(2);}
                }
                active=new Active(job,SectorChunkLease.acquire(server.overworld(),box),server.getTickCount());status.put(job.path(),"Construction loading checked area; no repeat charge.");
            }catch(Exception e){blocked(server,job.path(),e);}return;
        }
    }
}
