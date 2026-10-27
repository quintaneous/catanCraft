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

/** Approved public paths and house revisions belong to the activated base city.
 * Only bound disposable authored worlds opt in. All writes use the original sealed
 * transactions; neither foreign state nor a deliberate rollback is auto-repaired.
 */
public final class CityBasePreparation {
    enum Feature { PUBLIC, HOUSES }
    private record Key(String city, Feature feature) {}
    private record State(boolean done, boolean blocked, String message) {}
    @FunctionalInterface interface Action { int run() throws IOException; }
    private record Job(Key key, AABB bounds, Action action, long afterTick) {}
    private record Pending(Job job, SectorChunkLease lease, int startTick, long startNanos) {}
    private static MinecraftServer owner;
    private static final Map<Key, State> states = new LinkedHashMap<>();
    private static final Map<Key, Job> queue = new LinkedHashMap<>();
    private static Pending pending;
    private static final int RETRY_TICKS = 100;
    private CityBasePreparation() {}

    static boolean isRunning(MinecraftServer server) { return owner == server && pending != null; }
    // Houses are not a prerequisite for independent wall recovery/purchase. Only
    // public-path readiness is returned to the existing wall availability policy.
    static String reason(MinecraftServer server, String city) {
        if (owner != server) return "";
        State s = states.get(new Key(city, Feature.PUBLIC));
        return s == null || s.done() ? "" : s.message();
    }
    public static String status(MinecraftServer server, String city) {
        if (owner != server) return "";
        for (Feature f : Feature.values()) {
            State s = states.get(new Key(city, f));
            if (s != null && !s.done()) return s.message();
        }
        return states.getOrDefault(new Key(city,Feature.HOUSES),new State(false,false,"")).done()
                ? "Base city paths and six house designs ready" : "";
    }
    static void stopped(MinecraftServer server) {
        if (owner != server) return;
        if (pending != null) pending.lease().close();
        pending = null; queue.clear(); states.clear(); owner = null;
    }
    private static void bind(MinecraftServer server) {
        if (owner != server) { if (owner != null) stopped(owner); owner = server; }
    }
    private static void enqueue(MinecraftServer server, Key key, AABB bounds, Action action) {
        bind(server);
        queue.put(key,new Job(key,bounds,action,server.getTickCount()));
        states.put(key,new State(false,false,"Base city " + label(key.feature()) + " are being prepared automatically; zero charge."));
    }
    private static String label(Feature f) { return f == Feature.PUBLIC ? "paths" : "houses"; }

    /** Original fixture entrypoint remains strict: never invent a fresh operation over history. */
    static void queueFixture(String key, StreetscapeTransaction.Environment env) throws IOException {
        if (Files.exists(env.file(),LinkOption.NOFOLLOW_LINKS)) throw new IOException("Existing public history must be preserved, not initialized again");
        queuePublicFixture(key,env);
    }
    static void queuePublicFixture(String key, StreetscapeTransaction.Environment env) throws IOException {
        fixtureOnly(env.level().getServer(),env.file());
        AABB bounds=null;
        for(var c:env.plan().cells()) bounds=union(bounds,new AABB(c.pos()).inflate(2));
        if(bounds==null) throw new IOException("Empty public fixture");
        enqueue(env.level().getServer(),new Key(key,Feature.PUBLIC),bounds,()->runPublic(env));
        startNext(env.level().getServer());
    }
    static void queueHouseFixture(String key,R5HouseTransaction.Environment env) throws IOException {
        fixtureOnly(env.level().getServer(),env.file());
        enqueue(env.level().getServer(),new Key(key,Feature.HOUSES),houseBounds(env.anchor()),()->runHouses(env));
        startNext(env.level().getServer());
    }
    static boolean fixtureSettled(MinecraftServer server,String city,Feature feature) {
        State s=states.get(new Key(city,feature)); return owner==server&&s!=null&&(s.done()||s.blocked());
    }
    private static void fixtureOnly(MinecraftServer server,Path path)throws IOException {
        Path root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        if(!(server instanceof net.minecraft.gametest.framework.GameTestServer)||!path.toAbsolutePath().normalize().startsWith(root.resolve("data")))
            throw new IOException("Base city fixture requires isolated GameTest storage");
        if(pending!=null) throw new IOException("Another city is being initialized");
    }
    private static AABB union(AABB a,AABB b) { return a==null?b:a.minmax(b); }
    private static AABB houseBounds(com.quin.catancraft.map.MapAnchor anchor)throws IOException {
        AABB bounds=null;
        for(String group:R5HouseRevisions.groups())
            bounds=union(bounds,TownHallRepairTransaction.bounds(R5HouseRevisions.plan(group,R5HouseRevisions.Revision.R5,anchor)).inflate(2));
        if(bounds==null) throw new IOException("Empty house contract");
        return bounds;
    }
    private static int runPublic(StreetscapeTransaction.Environment env)throws IOException {
        var action=StreetscapeTransaction.Action.APPLY;
        if(Files.exists(env.file(),LinkOption.NOFOLLOW_LINKS)) {
            var phase=StreetscapeTransaction.phase(StreetscapeJournal.read(env.file()));
            action=switch(phase) {
                case COMMITTED -> StreetscapeTransaction.Action.STATUS;
                case PREPARED,APPLYING -> StreetscapeTransaction.Action.RESUME;
                case ROLLING_BACK,ROLLED_BACK -> throw new IOException("Public paths were intentionally rolled back or are rolling back; automatic replay is disabled");
            };
        }
        return StreetscapeTransaction.run(env,action).writes();
    }
    private static int runHouses(R5HouseTransaction.Environment env)throws IOException {
        var action=R5HouseTransaction.Action.APPLY;
        if(Files.exists(env.file(),LinkOption.NOFOLLOW_LINKS)) {
            var phase=R5HouseTransaction.phase(ProducerJournal.read(env.file()));
            action=switch(phase) {
                case COMMITTED -> R5HouseTransaction.Action.STATUS;
                case PREPARED,APPLYING,PHYSICAL_COMPLETE -> R5HouseTransaction.Action.RESUME;
                case ROLLING_BACK,ROLLED_BACK -> throw new IOException("House history was intentionally rolled back or is rolling back; automatic replay is disabled");
            };
        }
        return R5HouseTransaction.run(env,action).writes();
    }
    private static void failed(MinecraftServer server,Job job,Exception error) {
        boolean retry=PreparationDeferred.causedBy(error);
        String text="Base city " + label(job.key().feature()) + (retry?" waiting: ":" blocked: ") + error.getMessage()
                + (retry?". Retrying automatically; no repeat charge.":". Existing blocks and history preserved; review required.");
        State old=states.put(job.key(),new State(false,!retry,text));
        if(retry) queue.put(job.key(),new Job(job.key(),job.bounds(),job.action(),(long)server.getTickCount()+RETRY_TICKS));
        if(old==null||!old.message().equals(text)) {
            if(retry) CatanCraft.LOGGER.info("BASE_CITY_WAIT {} {}",job.key(),error.getMessage());
            else CatanCraft.LOGGER.error("BASE_CITY_BLOCKED {}",job.key(),error);
        }
    }
    private static void startNext(MinecraftServer server) {
        if(pending!=null||SectorService.hasActiveOperation(server)||PaidRecoveryService.isRunning(server)) return;
        Job next=null;
        for(Job j:queue.values()) if(server.getTickCount()>=j.afterTick()){next=j;break;}
        if(next==null)return;
        queue.remove(next.key());
        try { pending=new Pending(next,SectorChunkLease.acquire(server.overworld(),next.bounds()),server.getTickCount(),System.nanoTime()); }
        catch(Exception ex){failed(server,next,ex);}
    }
    static void tick(MinecraftServer server) {
        bind(server);
        if(SectorService.hasActiveOperation(server))return;
        if(pending!=null) {
            Pending work=pending;
            try {
                work.lease().keepAlive();
                if(!work.lease().ready()) {
                    if(server.getTickCount()-work.startTick()>600 && System.nanoTime()-work.startNanos()>30_000_000_000L)
                        throw new PreparationDeferred("Chunks/entities are still loading");
                    return;
                }
                int writes=work.job().action().run();
                states.put(work.job().key(),new State(true,false,""));
                CatanCraft.LOGGER.info("BASE_CITY_{}_READY city={} writes={} zero charge; normal city initialization",work.job().key().feature(),work.job().key().city(),writes);
            } catch(Exception error) { failed(server,work.job(),error); }
            work.lease().close(); pending=null; return;
        }
        if(server.getTickCount()%20==0)discover(server);
        startNext(server);
    }
    private static void discover(MinecraftServer server) {
        Path root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        if(!Files.isRegularFile(root.resolve(FreshDemoAuthorization.MARKER)))return;
        try{FreshDemoAuthorization.authorize(server);TownHallRepairService.authorize(server);}catch(IOException unauthorized){return;}
        var data=CatanSavedData.get(server);
        for(var t:data.territories()) {
            String city=t.id();
            if(!city.matches("[a-p]")||t.ownerNationId()==null||data.nation(t.ownerNationId())==null||data.placedMapAsset("territory:"+city+":city_base")==null)continue;
            Key publicKey=new Key(city,Feature.PUBLIC),houseKey=new Key(city,Feature.HOUSES);
            Feature feature;
            if(!states.containsKey(publicKey)) feature=Feature.PUBLIC;
            else if(states.get(publicKey).done()&&!states.containsKey(houseKey))feature=Feature.HOUSES;
            else continue;
            Key key=new Key(city,feature);
            try {
                var def=MapDefinitionManager.territory(city);ProducerService.validateMap(def);
                AABB bounds=null;
                if(feature==Feature.PUBLIC) {
                    var plan=StreetscapeR5.plan(def.cityCenter());
                    for(var cell:plan.cells())bounds=union(bounds,new AABB(cell.pos()).inflate(2));
                    for(var box:plan.boxes())bounds=union(bounds,box);
                } else bounds=houseBounds(SchematicAssetRegistry.territoryAnchor("starter_settlement",def));
                if(bounds==null)throw new IOException("Empty base city contract");
                enqueue(server,key,bounds,()->{
                    // Reauthorize, check competing histories, and resolve actual owner
                    // again at every write/recovery boundary, including delayed retries.
                    FreshDemoAuthorization.authorize(server);
                    DefenseCityReservations.requireAvailable(root,city,null);
                    if(feature==Feature.PUBLIC)return runPublic(StreetscapeService.context(server,city,true));
                    return runHouses(R5HouseService.context(server,city,R5HouseTransaction.Action.STATUS,true,true));
                });
            } catch(Exception error) {
                states.put(key,new State(false,true,"Base city "+label(feature)+" blocked: "+error.getMessage()+". Existing blocks and history preserved."));
                CatanCraft.LOGGER.error("Base city contract unavailable {}",key,error);
            }
            return;
        }
    }
}
