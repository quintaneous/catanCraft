package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.*;
import com.quin.catancraft.economy.EconomyCost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.gametest.framework.GameTestServer;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.LongSupplier;

/** Server binding for one journal/price/receipt per compass sector. City A north P2 is registered; prices and other sides remain separately gated.
 * Fixture providers are available only to package-private isolated native-test entrypoints.
 */
public final class SectorService {
    static final String PRODUCTION="reviewed-production-v1",FIXTURE="gametest-fixture-v1";
    private static final long QUOTE_TICKS=1200;
    private static boolean busy;
    private static final class Active {
        private final Context context;
        private SectorTransaction.Runner runner;
        private final SectorChunkLease lease;
        private final SectorTransaction.Environment environment;
        private final SectorTransaction.Action action;
        private final SectorTransaction.Fault fault;
        private long maxSlice,loadingStartedNanos;private int slices,maxWrites,loadingTicks;
        Active(Context context,SectorTransaction.Runner runner,SectorChunkLease lease){
            this.context=context;this.runner=runner;this.lease=lease;environment=null;action=null;fault=null;
        }
        Active(Context context,SectorTransaction.Environment environment,SectorTransaction.Action action,SectorTransaction.Fault fault,SectorChunkLease lease){
            this.context=context;this.environment=environment;this.action=action;this.fault=fault;this.lease=lease;
        }
        Context context(){return context;}SectorTransaction.Runner runner(){return runner;}
        void close(){lease.close();}
    }
    private static Active active;
    static boolean hasActiveOperation(MinecraftServer server){return busy||active!=null&&active.context().server()==server;}
    /** One main-thread slice per tick. Tickets live from preparation through durable settlement. */
    static void tick(MinecraftServer server){
        Active current=active;if(current==null||current.context().server()!=server)return;
        try{
            current.lease.keepAlive();
            if(!current.lease.ready()){
                if(current.loadingTicks++==0)current.loadingStartedNanos=System.nanoTime();
                if(current.loadingTicks>600&&System.nanoTime()-current.loadingStartedNanos>30_000_000_000L)throw new IOException("Timed out loading the bounded construction area; retained history is unchanged and can be resumed");
                return;
            }
            current.loadingTicks=0;
            if(current.runner==null){
                current.runner=SectorTransaction.begin(current.environment,current.action,current.fault);
                if(current.runner.done()){active=null;current.close();notifyActor(current.context(),current.runner.result().message());return;}
            }
            long start=System.nanoTime();int before=current.runner().writes();boolean done=current.runner().step(4,25_000_000L);
            current.maxWrites=Math.max(current.maxWrites,current.runner().writes()-before);
            if(current.maxWrites>4)throw new IllegalStateException("Sector runner exceeded four-write slice budget");
            current.maxSlice=Math.max(current.maxSlice,System.nanoTime()-start);current.slices++;
            if(!done){
                if(current.slices%64==0)CatanCraft.LOGGER.info("Sector bounded progress: {} slices, {} writes, maximum slice {} ms",current.slices,current.runner().writes(),current.maxSlice/1_000_000.0);
                return;
            }
            CatanCraft.LOGGER.info("Sector bounded placement: {} slices, {} writes, maximum {} writes/slice, maximum slice {} ms",current.slices,current.runner().writes(),current.maxWrites,current.maxSlice/1_000_000.0);
            active=null;current.close();notifyActor(current.context(),current.runner().result().message());
        }catch(Exception error){
            active=null;current.close();notifyActor(current.context(),failure(error).message());
        }
    }
    static void stopped(MinecraftServer server){
        if(active!=null&&active.context().server()==server){active.close();active=null;}
        QUOTES.keySet().removeIf(k->k.root().equals(server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().toString()));
    }
    private static void notifyActor(Context c,String message){
        CatanCraft.LOGGER.info("Sector operation: {}",message);
        var player=c.server().getPlayerList().getPlayer(c.actor());
        if(player!=null){
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
            com.quin.catancraft.ui.NationDashboard.refreshDefenses(player,message);
        }
    }
    private static final Map<Key,SavedQuote> QUOTES=new HashMap<>();
    private SectorService(){}
    record Context(MinecraftServer server,Path root,CatanSavedData data,String city,UUID actor,UUID owner,String worldId,String approval,LongSupplier ticks){}
    interface Provider {SectorContracts.Contract resolve(Context context,SectorContracts.Direction sector,int sourceLevel)throws IOException;default String priceVersion(){return "UNVERSIONED_CONTRACT";}}
    public record State(int level,String receipt,String revision){}
    private record Key(String root,String worldId,UUID actor,String city,SectorContracts.Direction side){}
    private record SavedQuote(SectorContracts.Quote quote,State source,Map<SectorContracts.Direction,String> neighborRevisions,long tick,String priceVersion,String token){}
    private record History(Path path,CompoundTag journal){}
    private record Selection(History pending,History selected,History owned){}
    private static String prefix(String city,SectorContracts.Direction side){return "sector_v1:"+city+":"+side.name().toLowerCase(Locale.ROOT)+":";}
    private static String value(CatanSavedData data,String key){String v=data.placedMapAsset(key);return v==null||v.equals("none")?"":v;}
    public static State savedState(CatanSavedData data,String city,SectorContracts.Direction side)throws IOException {
        String p=prefix(city,side),level=value(data,p+"level"),receipt=value(data,p+"receipt"),encoded=value(data,p+"revision");int n=0;
        try{if(!level.isEmpty()){if(!level.matches("0|[1-9][0-9]{0,9}"))throw new IOException("Invalid saved sector level");n=Integer.parseInt(level);}}catch(NumberFormatException e){throw new IOException("Sector level overflow",e);}
        String revision="";
        if(!encoded.isEmpty())try{if(!encoded.startsWith("hex:"))throw new IOException("Unknown sector revision encoding");byte[] b=HexFormat.of().parseHex(encoded.substring(4));revision=new String(b,StandardCharsets.UTF_8);if(!Arrays.equals(b,revision.getBytes(StandardCharsets.UTF_8)))throw new IOException("Invalid sector revision text");}catch(IllegalArgumentException e){throw new IOException("Invalid saved sector revision",e);}
        if(n>0&&(receipt.isEmpty()||revision.isEmpty()))throw new IOException("Purchased sector lacks durable receipt/revision");
        if(n==0&&!revision.isEmpty())throw new IOException("Unpurchased sector has a foreign revision");return new State(n,receipt,revision);
    }
    static SectorContracts.Contract resolveProduction(Context c,SectorContracts.Direction side,int sourceLevel)throws IOException {
        if(c.city().equals("a")&&sourceLevel>=0&&sourceLevel<=1)
            return SectorCityAP3Group.resolve(c,side,sourceLevel);
        throw new IOException("No available structural sector tier for "+c.city()+" "+side+" L"+(sourceLevel+1));
    }
    private static Context production(MinecraftServer server,UUID actor,String city)throws IOException {
        if(!server.isSameThread())throw new IOException("Sector service requires the server main thread");String id=city.toLowerCase(Locale.ROOT);if(!id.matches("[a-p]"))throw new IOException("Unknown sector city");
        Path root=FreshDemoAuthorization.authorize(server);CatanSavedData data=CatanSavedData.get(server);NationData nation=DefenseCommands.requireLeaderOwner(data,actor,id);
        return new Context(server,root,data,id,actor,nation.id(),FreshDemoAuthorization.worldId(server,root),PRODUCTION,server::getTickCount);
    }
    public static MapAssetService.Result quote(MinecraftServer server,UUID actor,String city,String compass){
        try{return quoteContext(production(server,actor,city),SectorContracts.Direction.parse(compass),SectorService::resolveProduction);}catch(Exception e){return failure(e);}
    }
    public static MapAssetService.Result operation(MinecraftServer server,UUID actor,String city,String compass,SectorTransaction.Action action){
        try{return operate(production(server,actor,city),SectorContracts.Direction.parse(compass),action,SectorService::resolveProduction,(s,n)->{});}catch(Exception e){return failure(e);}
    }
    /** Cheap dashboard state: never resolve a complete voxel plan merely to open a menu. */
    public record MenuState(SectorContracts.Direction side, int level, EconomyCost cost,
                            String unavailable, boolean pending, String recovery) {
        public MenuState(SectorContracts.Direction side,int level,EconomyCost cost,String unavailable,boolean pending){this(side,level,cost,unavailable,pending,"");}
    }
    public record MenuQuote(String token, String city, SectorContracts.Direction side,
                            int sourceLevel, int targetLevel, EconomyCost cost, String priceVersion) {}

    public static List<MenuState> menuStates(MinecraftServer server, UUID actor, String city) {
        CatanSavedData data=CatanSavedData.get(server);
        String id=city.toLowerCase(Locale.ROOT);
        NationData nation=data.nationForPlayer(actor);
        TerritoryData territory=data.territory(id);
        if(nation==null||territory==null||!nation.id().equals(territory.ownerNationId()))return List.of();
        String reason=""; boolean pending=active!=null||busy;
        try {
            if(!id.equals("a"))throw new IOException("Only City A has reviewed sector placements in this test build.");
            Context c=production(server,actor,id);
            if(pending)throw new IOException("A defense operation is in progress. Wait for completion; do not buy again.");
            DefenseCityReservations.requireAvailable(c.root(),id,null);
        } catch(Exception e) {
            reason=e.getMessage()==null?"Defense availability could not be checked.":e.getMessage();
        }
        List<MenuState> out=new ArrayList<>();
        for(var side:SectorContracts.Direction.values()) {
            try {
                State state=savedState(data,id,side);
                if(state.level()<0||state.level()>2)throw new IOException("Unsupported saved sector level; review retained history.");
                EconomyCost cost=state.level()<2?SectorPriceCatalog.cost(side,state.level()):null;
                String recovery="";
                if(!pending&&!reason.isEmpty()&&nation.isLeader(actor)) {
                    try {
                        Context c=production(server,actor,id);
                        History interrupted=select(c,side).pending();
                        if(interrupted!=null)recovery=interrupted.journal().getString("phase").equals("ROLLING_BACK")?"rollback":"resume";
                    }catch(Exception invalidHistory){/* Existing reason remains fail-closed; never invent recovery. */}
                }
                String preparation=CityBasePreparation.reason(server,id);
                out.add(new MenuState(side,state.level(),cost,preparation.isEmpty()?reason:preparation,pending,recovery));
            } catch(Exception e) {
                out.add(new MenuState(side,-1,null,"Invalid sector state: "+e.getMessage(),pending));
            }
        }
        return List.copyOf(out);
    }

    /** Quote identity is echoed by the GUI, not a client-provided price or target level. */
    public static MenuQuote menuQuote(MinecraftServer server, UUID actor, String city, String compass) throws IOException {
        Context c=production(server,actor,city);
        return menuQuoteContext(c,SectorContracts.Direction.parse(compass),SectorService::resolveProduction);
    }
    static synchronized MenuQuote menuQuoteContext(Context c,SectorContracts.Direction side,Provider provider) throws IOException {
        quoteContext(c,side,provider);
        SavedQuote q=QUOTES.get(key(c,side));
        EconomyCost cost=q.quote().contract().cost();
        if(!cost.canAfford(c.data().nation(c.owner()))) {
            QUOTES.remove(key(c,side));
            throw new IOException("Cannot afford this sector. Need "+cost.describe()+". No charge or placement occurred.");
        }
        return new MenuQuote(q.token(),c.city(),side,q.source().level(),q.quote().contract().plan().targetLevel(),cost,q.priceVersion());
    }
    public static MapAssetService.Result confirmMenuQuote(MinecraftServer server,UUID actor,String city,String compass,String token) {
        try {return confirmMenuContext(production(server,actor,city),SectorContracts.Direction.parse(compass),token,SectorService::resolveProduction,true);}
        catch(Exception e){return failure(e);}
    }
    static synchronized MapAssetService.Result confirmMenuContext(Context c,SectorContracts.Direction side,String token,Provider provider,boolean bounded) throws IOException {
        authority(c);
        SavedQuote q=QUOTES.get(key(c,side));
        if(q==null||!q.token().equals(token))throw new IOException("This confirmation is stale, canceled or already used. Review a new quote; nothing was charged.");
        // The established operation rechecks owner, source, neighbors, expiry, price and all placement hazards.
        return operate(c,side,SectorTransaction.Action.APPLY,provider,(stage,writes)->{},bounded);
    }
    public static MapAssetService.Result cancelMenuQuote(MinecraftServer server,UUID actor,String city,String compass,String token) {
        try {return cancelMenuContext(production(server,actor,city),SectorContracts.Direction.parse(compass),token);}
        catch(Exception e){return failure(e);}
    }
    static synchronized MapAssetService.Result cancelMenuContext(Context c,SectorContracts.Direction side,String token) throws IOException {
        authority(c);SavedQuote q=QUOTES.get(key(c,side));
        if(q!=null&&q.token().equals(token))QUOTES.remove(key(c,side));
        return new MapAssetService.Result(true,"Purchase canceled. No resources charged or blocks placed.",0);
    }
    static Context fixtureContext(MinecraftServer server,Path root,CatanSavedData data,String city,UUID actor,String worldId,LongSupplier clock)throws IOException {
        if(!(server instanceof GameTestServer))throw new IOException("Sector fixture provider requires GameTestServer");
        Path world=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize(),isolated=root.toAbsolutePath().normalize();
        validateFixtureRoot(server,isolated);Files.createDirectories(isolated.resolve("data"));validateFixtureRoot(server,isolated);
        String id=city.toLowerCase(Locale.ROOT);if(!id.matches("[a-p]"))throw new IOException("Unknown fixture city");NationData nation=DefenseCommands.requireLeaderOwner(data,actor,id);
        return new Context(server,isolated,data,id,actor,nation.id(),worldId,FIXTURE,clock);
    }
    static MapAssetService.Result quoteFixture(Context c,SectorContracts.Direction d,Provider p){try{requireFixture(c);return quoteContext(c,d,p);}catch(Exception e){return failure(e);}}
    static MapAssetService.Result operationFixture(Context c,SectorContracts.Direction d,SectorTransaction.Action a,Provider p){return operationFixture(c,d,a,p,(s,n)->{});}
    static MapAssetService.Result operationFixture(Context c,SectorContracts.Direction d,SectorTransaction.Action a,Provider p,SectorTransaction.Fault fault){try{requireFixture(c);return operate(c,d,a,p,fault);}catch(Exception e){return failure(e);}}
    static MapAssetService.Result operationFixtureBounded(Context c,SectorContracts.Direction d,SectorTransaction.Action a,Provider p){
        return operationFixtureBounded(c,d,a,p,(s,n)->{});
    }
    static MapAssetService.Result operationFixtureBounded(Context c,SectorContracts.Direction d,SectorTransaction.Action a,Provider p,SectorTransaction.Fault fault){
        try{requireFixture(c);return operate(c,d,a,p,fault,true);}catch(Exception e){return failure(e);}
    }
    static boolean activeFixture(Context c)throws IOException{requireFixture(c);return active!=null&&active.context().root().equals(c.root());}
    static CatanSavedData reloadFixture(Context c)throws IOException {requireFixture(c);return CatanSavedData.load(ProducerJournal.read(c.root().resolve("data/sector_fixture_ledger_v1.dat")).getCompound("ledger"));}
    private static void requireFixture(Context c)throws IOException {if(!(c.server() instanceof GameTestServer)||!c.approval().equals(FIXTURE))throw new IOException("Unapproved sector fixture provider");validateFixtureRoot(c.server(),c.root());}
    private static void validateFixtureRoot(MinecraftServer server,Path root)throws IOException {
        Path world=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize(),isolated=root.toAbsolutePath().normalize();
        if(!isolated.startsWith(world)||isolated.equals(world))throw new IOException("Fixture sector root must be a separate directory inside the disposable test world");
        Path realWorld=world.toRealPath(),destination=isolated.resolve("data"),walk=destination.getRoot();
        // Check every existing ancestor BEFORE any directory creation, not just the leaf.
        for(Path part:destination){walk=walk.resolve(part);if(Files.isSymbolicLink(walk))throw new IOException("Fixture ancestor symlink refused");if(Files.exists(walk,LinkOption.NOFOLLOW_LINKS)){if(!Files.isDirectory(walk,LinkOption.NOFOLLOW_LINKS))throw new IOException("Fixture ancestor is not a directory");if(walk.startsWith(world)&&!walk.toRealPath().startsWith(realWorld))throw new IOException("Fixture canonical path escapes disposable world");}}
    }
    private static void authority(Context c)throws IOException {
        if(!c.server().isSameThread())throw new IOException("Sector operation requires main thread");if(!DefenseCommands.requireLeaderOwner(c.data(),c.actor(),c.city()).id().equals(c.owner()))throw new IOException("Sector ownership/leader changed");
        if(c.approval().equals(PRODUCTION)){Path root=FreshDemoAuthorization.authorize(c.server());if(!root.equals(c.root())||!FreshDemoAuthorization.worldId(c.server(),root).equals(c.worldId()))throw new IOException("Authorized sector world identity changed");}
        else requireFixture(c);
    }
    private static Key key(Context c,SectorContracts.Direction d){return new Key(c.root().toString(),c.worldId(),c.actor(),c.city(),d);}
    private static Map<SectorContracts.Direction,String> neighborRevisions(Context c,SectorContracts.Direction side)throws IOException {
        Map<SectorContracts.Direction,String> out=new EnumMap<>(SectorContracts.Direction.class);for(var d:side.adjacent())out.put(d,savedState(c.data(),c.city(),d).revision());return Map.copyOf(out);
    }
    private static void contractCurrent(Context c,SectorContracts.Direction d,SectorContracts.Contract contract,State source)throws IOException {
        if(contract==null||!contract.world().equals(c.root().toString())||!contract.city().equals(c.city())||contract.sector()!=d||contract.plan().sourceLevel()!=source.level())throw new IOException("Sector registry contract world/city/compass/level mismatch");
        SectorTransaction.describe(contract.plan());if(source.level()>0&&!source.revision().equals(contract.plan().sourceRevision()))throw new IOException("Purchased sector revision is not the quoted source");
        for(var n:contract.neighbors().entrySet()){State current=savedState(c.data(),c.city(),n.getKey());if(current.level()!=n.getValue().level()||!current.receipt().equals(n.getValue().receipt()))throw new IOException("Quoted adjacent level/receipt is stale");}
        requireCornerTable(c,d,contract.corners(),true);
    }
    private static void requireCornerTable(Context c,SectorContracts.Direction own,Map<SectorContracts.Corner,SectorContracts.Direction> proposed,boolean includeOwn)throws IOException {
        CompoundTag table=new CompoundTag();proposed.forEach((corner,side)->table.putString(corner.name(),side.name()));
        for(var side:SectorContracts.Direction.values()){
            if(side==own&&!includeOwn)continue;State state=savedState(c.data(),c.city(),side);if(state.level()==0)continue;Selection selection=select(c,side);
            if(selection.pending()!=null)throw new IOException("Neighbor sector is pending; corner interface cannot change");History paid=selection.owned();
            if(paid==null)throw new IOException("Purchased sector lacks corner ownership proof");
            CompoundTag existing=paid.journal().getCompound("identity").getCompound("corners");
            for(String corner:table.getAllKeys())if(existing.contains(corner)&&!existing.getString(corner).equals(table.getString(corner)))throw new IOException("Corner ownership conflicts with an existing purchased sector; coordinated transition is unavailable");
            if(side==own)for(String corner:existing.getAllKeys())if(!table.contains(corner))throw new IOException("Upgrade cannot discard previously recorded corner ownership");
        }
    }
    private static String priceVersion(Context c,Provider provider)throws IOException {String version=PRODUCTION.equals(c.approval())?SectorPriceCatalog.VERSION:provider.priceVersion();if(version==null||version.isBlank()||version.length()>128)throw new IOException("Invalid sector price catalog version");return version;}
    private static synchronized MapAssetService.Result quoteContext(Context c,SectorContracts.Direction side,Provider provider)throws IOException {
        if(busy||active!=null)throw new IOException("Another defense operation is active");authority(c);DefenseCityReservations.requireAvailable(c.root(),c.city(),null);State source=savedState(c.data(),c.city(),side);select(c,side);
        SectorContracts.Contract contract=provider.resolve(c,side,source.level());contractCurrent(c,side,contract,source);
        String priceVersion=priceVersion(c,provider);
        QUOTES.put(key(c,side),new SavedQuote(new SectorContracts.Quote(c.owner(),contract),source,neighborRevisions(c,side),c.ticks().getAsLong(),priceVersion,UUID.randomUUID().toString()));
        return new MapAssetService.Result(true,side+" L"+contract.plan().targetLevel()+": "+contract.cost().describe()+" for the complete sector"+(SectorPriceCatalog.VERSION.equals(priceVersion)?" (PROVISIONAL PLAYTEST PRICING)":"")+"; revision "+contract.plan().targetRevision()+". Confirm within 1200 server ticks. No purchase or reservation yet.",0);
    }
    private static synchronized MapAssetService.Result operate(Context c,SectorContracts.Direction side,SectorTransaction.Action action,Provider provider,SectorTransaction.Fault fault)throws IOException {
        return operate(c,side,action,provider,fault,c.approval().equals(PRODUCTION));
    }
    private static synchronized MapAssetService.Result operate(Context c,SectorContracts.Direction side,SectorTransaction.Action action,Provider provider,SectorTransaction.Fault fault,boolean bounded)throws IOException {
        if(busy||active!=null)throw new IOException("Another defense operation is active; no reentrant sector purchase");busy=true;
        SectorChunkLease lease=null;
        try{
            authority(c);State state=savedState(c.data(),c.city(),side);Selection selection=select(c,side);History selected=selection.pending()!=null?selection.pending():selection.selected();Path file;CompoundTag identity;SectorContracts.Contract contract;boolean terminalReadOnly=false;
            if(action==SectorTransaction.Action.APPLY){
                if(selection.pending()!=null)throw new IOException("Whole-sector operation pending; use resume or rollback");SavedQuote quote=QUOTES.remove(key(c,side));
                if(quote==null){if(selected==null||!selected.journal().getString("phase").equals("COMMITTED"))throw new IOException("Display a current whole-sector quote before confirmation");file=selected.path();identity=selected.journal().getCompound("identity").copy();contract=recorded(c,selected);terminalReadOnly=true;}
                else{
                    DefenseCityReservations.requireAvailable(c.root(),c.city(),null);long now=c.ticks().getAsLong();if(now<quote.tick()||now-quote.tick()>QUOTE_TICKS)throw new IOException("Sector quote expired; display the full price again");
                    if(!quote.source().equals(state)||!quote.neighborRevisions().equals(neighborRevisions(c,side)))throw new IOException("Sector quote source or neighboring revision changed");
                    if(!quote.priceVersion().equals(priceVersion(c,provider)))throw new IOException("Sector price catalog version changed; display a new full quote");
                    contract=provider.resolve(c,side,state.level());contractCurrent(c,side,contract,state);quote.quote().requireCurrent(c.owner(),contract);
                    file=c.root().resolve("data/catancraft_sector_"+c.city()+"_"+side.name().toLowerCase(Locale.ROOT)+"_"+UUID.randomUUID()+"_v1.dat");identity=freshIdentity(c,side,contract,state,file,quote.tick(),quote.priceVersion());
                }
            }else{
                if(selected==null){if(action==SectorTransaction.Action.STATUS)return new MapAssetService.Result(true,side+": completed L0; no sector transaction. Use quote to check the approved next geometry/price; other sides are not inferred from north.",0);throw new IOException("No retained whole-sector operation to recover");}
                file=selected.path();identity=selected.journal().getCompound("identity").copy();contract=recorded(c,selected);terminalReadOnly=selected.journal().getString("phase").equals("COMMITTED")&&action!=SectorTransaction.Action.ROLLBACK;
            }
            DefenseCityReservations.requireAvailable(c.root(),c.city(),file);
            if(terminalReadOnly&&!contract.cityGroup().isEmpty()){SectorCityAP3Group.verifyCurrentAssembly(c);return new MapAssetService.Result(true,"Current complete City A assembly and retained group lineage verified; zero writes and zero charge",0);}
            if(CityBasePreparation.isRunning(c.server()))throw new IOException("Base city paths are being prepared automatically; retry after completion");
            if(bounded&&action!=SectorTransaction.Action.STATUS)lease=SectorChunkLease.acquire(c.server().overworld(),entityBounds(contract.plan()));
            var env=environment(c,side,contract,identity,file,terminalReadOnly);
            if(lease!=null&&!lease.ready()) {
                active=new Active(c,env,action,fault,lease);lease=null;
                return new MapAssetService.Result(true,"Loading the bounded wall area automatically. Do not repeat purchase; preparation and entity checks will finish before new payment or placement.",0);
            }
            if(action!=SectorTransaction.Action.STATUS)StreetscapeService.requireLoadedEntities(c.server().overworld(),List.of(entityBounds(contract.plan())));
            if(bounded&&action!=SectorTransaction.Action.STATUS){
                long preparationStart=System.nanoTime();var runner=SectorTransaction.begin(env,action,fault);
                CatanCraft.LOGGER.info("Sector bounded preparation: {} ms",(System.nanoTime()-preparationStart)/1_000_000.0);
                if(runner.done())return new MapAssetService.Result(true,runner.result().message(),runner.result().writes());
                active=new Active(c,runner,lease);lease=null;
                return new MapAssetService.Result(true,"Whole-sector operation reserved and queued; placement proceeds in bounded server-tick slices. Completed level is unchanged until the entire side is saved. Do not repeat purchase; retained history supports resume/rollback after interruption.",0);
            }
            var result=SectorTransaction.run(env,action,fault);
            return new MapAssetService.Result(true,(selection.pending()!=null&&action==SectorTransaction.Action.STATUS?"PENDING; not a completed new sector. ":"")+result.message(),result.writes());
        }finally{if(lease!=null)lease.close();busy=false;}
    }
    /** Same bounds for production readiness and fixture preparation; no alternate test policy. */
    static AABB entityBounds(SectorTransaction.Plan plan)throws IOException {
        AABB bounds=null;for(var cell:plan.cells()){var b=new AABB(cell.pos()).inflate(2);bounds=bounds==null?b:bounds.minmax(b);}for(var r:plan.reads()){var b=new AABB(r.pos());bounds=bounds==null?b:bounds.minmax(b);}if(bounds==null)throw new IOException("Missing sector entity read bounds");return bounds;
    }
    private static CompoundTag freshIdentity(Context c,SectorContracts.Direction side,SectorContracts.Contract contract,State source,Path file,long tick,String priceVersion)throws IOException {
        CompoundTag id=new CompoundTag();id.putInt("adapterSchema",1);id.putString("world",c.root().toString());id.putString("worldId",c.worldId());id.putString("dimension",c.server().overworld().dimension().location().toString());id.putString("city",c.city());id.putString("sector",side.name().toLowerCase(Locale.ROOT));id.putUUID("owner",c.owner());id.putUUID("quotedActor",c.actor());id.putString("approval",c.approval());id.putString("operationFile",file.getFileName().toString());id.putString("previousRevision",source.revision());id.putLong("quotedTick",tick);id.putString("priceCatalogVersion",priceVersion);
        CompoundTag corners=new CompoundTag();contract.corners().forEach((corner,owner)->corners.putString(corner.name(),owner.name()));id.put("corners",corners);
        CompoundTag neighbors=new CompoundTag();for(var d:side.adjacent()){State s=savedState(c.data(),c.city(),d);CompoundTag n=new CompoundTag();n.putInt("level",s.level());n.putString("receipt",s.receipt());n.putString("revision",s.revision());neighbors.put(d.name(),n);}id.put("neighbors",neighbors);
        if(!contract.cityGroup().isEmpty())id.put("cityGroup",contract.cityGroup());
        ListTag roads=new ListTag();contract.roads().stream().sorted(Comparator.comparingLong(BlockPos::asLong)).forEach(p->roads.add(LongTag.valueOf(p.asLong())));id.put("roads",roads);return id;
    }
    private static SectorTransaction.Environment environment(Context c,SectorContracts.Direction side,SectorContracts.Contract contract,CompoundTag frozen,Path file,boolean terminalReadOnly){return new SectorTransaction.Environment(){
        private final DefenseReservationVerifier reservations=new DefenseReservationVerifier();
        public net.minecraft.server.level.ServerLevel level(){return c.server().overworld();}public Path file(){return file;}public SectorTransaction.Plan plan(){return contract.plan();}public CompoundTag identity(){return frozen.copy();}public EconomyCost cost(){return contract.cost();}public NationData nation(){return c.data().nation(c.owner());}
        public CompoundTag inheritedNaturalSupportProof()throws IOException {return contract.cityGroup().isEmpty()?inheritedSupportProof(c,side,contract.plan(),file):SectorCityAP3Group.inheritedSupportProof(c,contract.cityGroup());}
        public int progress(){try{return savedState(c.data(),c.city(),side).level();}catch(IOException e){throw new IllegalStateException(e);}}
        public void progress(int n){String revision=n==contract.plan().targetLevel()?contract.plan().targetRevision():frozen.getString("previousRevision");c.data().setPlacedMapAsset(prefix(c.city(),side)+"level",Integer.toString(n));setRevision(c.data(),c.city(),side,revision);}
        public String receipt(){return value(c.data(),prefix(c.city(),side)+"receipt");}
        public void receipt(String receipt){
            String groupFile=null,groupReceipt=null;
            if(!contract.cityGroup().isEmpty())try{
                CompoundTag journal=ProducerJournal.read(file);header(c,file,journal.getCompound("identity"));if(!journal.hasUUID("id"))throw new IOException("Group journal has no durable operation id");String id=journal.getUUID("id").toString();
                if(receipt.equals(id+":reserved")||receipt.equals(id+":paid")){groupFile=file.getFileName().toString();groupReceipt=receipt;}
                else if(receipt.equals(journal.getString("previousReceipt"))){CompoundTag group=contract.cityGroup();groupFile=group.getString("parentFile");groupReceipt=group.getString("parentCityReceipt");}
                else throw new IOException("Unexpected whole-city group receipt transition");
            }catch(IOException error){throw new IllegalStateException("Cannot bind city group receipt to retained journal",error);}
            c.data().setPlacedMapAsset(prefix(c.city(),side)+"receipt",receipt.isEmpty()?"none":receipt);c.data().setPlacedMapAsset(prefix(c.city(),side)+"operation",file.getFileName().toString());
            if(groupFile!=null){c.data().setPlacedMapAsset("city_group_v1:"+c.city()+":operation",groupFile.isEmpty()?"none":groupFile);c.data().setPlacedMapAsset("city_group_v1:"+c.city()+":receipt",groupReceipt.isEmpty()?"none":groupReceipt);}
        }
        public void save()throws IOException {
            c.data().setDirty();if(c.approval().equals(FIXTURE)){CompoundTag n=new CompoundTag();n.put("ledger",c.data().save(new CompoundTag()));Path p=c.root().resolve("data/sector_fixture_ledger_v1.dat");ProducerJournal.write(p,n,Files.exists(p,LinkOption.NOFOLLOW_LINKS));}
            else if(!c.server().saveEverything(true,true,true))throw new IOException("Whole-sector world/ledger save failed");
        }
        public void requirements(boolean rollback)throws IOException {
            authority(c);header(c,file,frozen);if(!contract.cityGroup().isEmpty())SectorCityAP3Group.requireCurrent(c,contract,frozen,file,terminalReadOnly);else SectorNorthP2.requireNoLegacyOwnership(c,contract.plan());reservations.requireAvailable(c.root(),c.city(),file);State current=savedState(c.data(),c.city(),side);
            String revision=current.level()==contract.plan().targetLevel()?contract.plan().targetRevision():frozen.getString("previousRevision");if(!current.revision().equals(revision))throw new IOException("Current purchased sector revision changed");
            CompoundTag neighbors=frozen.getCompound("neighbors");if(!terminalReadOnly)for(var d:side.adjacent()){State state=savedState(c.data(),c.city(),d);CompoundTag pinned=neighbors.getCompound(d.name());if(state.level()!=pinned.getInt("level")||!state.receipt().equals(pinned.getString("receipt"))||!state.revision().equals(pinned.getString("revision")))throw new IOException("Adjacent sector level/receipt/revision changed during operation");}
            if(contract.cityGroup().isEmpty())requireCornerTable(c,side,contract.corners(),false);
        }
        public void allow(BlockPos p)throws IOException {authority(c);if(!contract.cityGroup().isEmpty()){if(!SectorCityAP3Group.approvedWrite(c,contract,p))throw new IOException("Write outside exact approved grouped sector and participant-owned seam masks");}else if(c.approval().equals(PRODUCTION)){var def=com.quin.catancraft.map.MapDefinitionManager.territory(c.city());if(def==null||!def.dimension().equals(level().dimension().location().toString())||def.siegeRegion()==null||def.restorationRegion()==null)throw new IOException("Missing approved city restoration bounds");if((!def.siegeRegion().contains(p)||!def.restorationRegion().contains(p))&&!SectorNorthP2.approvedExtensionWrite(c.city(),contract.plan(),p))throw new IOException("Sector write outside approved city restoration bounds and exact north P2 extension");}if(contract.plan().cells().stream().noneMatch(cell->cell.pos().equals(p)&&!cell.before().equals(cell.after())))throw new IOException("Write outside approved whole-sector union");}
    };}
    /** Helper validates latest city lineage/canonical geometry first; this verifies all actual targets/reads. */
    static void verifyRecordedGroup(Context c,Path file)throws IOException {
        authority(c);if(!file.toAbsolutePath().normalize().getParent().equals(c.root().resolve("data").toAbsolutePath().normalize()))throw new IOException("Group verification journal outside current city history directory");
        CompoundTag journal=ProducerJournal.read(file),id=journal.getCompound("identity");header(c,file,id);if(!journal.getString("phase").equals("COMMITTED")||id.getCompound("cityGroup").isEmpty())throw new IOException("Current assembled city requires a retained committed group journal");
        var side=SectorContracts.Direction.parse(id.getString("sector"));var contract=recorded(c,new History(file,journal));SectorTransaction.run(environment(c,side,contract,id,file,true),SectorTransaction.Action.STATUS);
    }
    private static CompoundTag inheritedSupportProof(Context c,SectorContracts.Direction side,SectorTransaction.Plan plan,Path operation)throws IOException {
        if(plan.sourceLevel()==0||plan.naturalSupport().equals(SectorNaturalSupportPolicy.NONE))return new CompoundTag();
        String receipt=value(c.data(),prefix(c.city(),side)+"receipt");
        if(Files.exists(operation,LinkOption.NOFOLLOW_LINKS)){CompoundTag current=ProducerJournal.read(operation);header(c,operation,current.getCompound("identity"));receipt=current.getString("previousReceipt");}
        CompoundTag proof=null;int checked=0;Path dir=c.root().resolve("data");if(Files.isSymbolicLink(dir))throw new IOException("Sector proof directory symlink refused");
        if(Files.isDirectory(dir))try(var paths=Files.newDirectoryStream(dir,"catancraft_sector_"+c.city()+"_*_v1.dat")){
            for(Path file:paths){if(++checked>4096)throw new IOException("Excess sector source proofs");CompoundTag j=ProducerJournal.read(file);header(c,file,j.getCompound("identity"));if(!j.hasUUID("id")||!receipt.equals(j.getUUID("id")+":paid"))continue;
                CompoundTag spec=j.getCompound("spec");if(proof!=null||!j.getString("phase").equals("COMMITTED")||!j.getCompound("identity").getString("sector").equalsIgnoreCase(side.name())||spec.getInt("targetLevel")!=plan.sourceLevel()||!spec.getString("targetRevision").equals(plan.sourceRevision()))throw new IOException("Prior natural-support ownership proof disagrees with exact purchased source");proof=j;
            }
        }
        if(proof==null)throw new IOException("Missing retained committed predecessor for natural-support upgrade");return proof.copy();
    }
    private static void setRevision(CatanSavedData data,String city,SectorContracts.Direction d,String revision){data.setPlacedMapAsset(prefix(city,d)+"revision",revision.isEmpty()?"none":"hex:"+HexFormat.of().formatHex(revision.getBytes(StandardCharsets.UTF_8)));}
    private static void header(Context c,Path file,CompoundTag id)throws IOException {
        if(id.getInt("adapterSchema")!=1||!id.getString("world").equals(c.root().toString())||!id.getString("worldId").equals(c.worldId())||!id.getString("dimension").equals(c.server().overworld().dimension().location().toString())||!id.getString("city").equals(c.city())||!id.hasUUID("owner")||!id.getUUID("owner").equals(c.owner())||!id.getString("approval").equals(c.approval())||!id.getString("operationFile").equals(file.getFileName().toString()))throw new IOException("Foreign/unapproved sector journal world, owner or fixture context");
        SectorContracts.Direction.parse(id.getString("sector"));
    }
    private static Selection select(Context c,SectorContracts.Direction side)throws IOException {
        Path dir=c.root().resolve("data");if(Files.isSymbolicLink(dir))throw new IOException("Sector data symlink refused");State state=savedState(c.data(),c.city(),side);String pointer=value(c.data(),prefix(c.city(),side)+"operation");History pending=null,pointed=null,paid=null;Set<UUID> ids=new HashSet<>();int count=0,sideHistories=0;
        if(Files.isDirectory(dir))try(var paths=Files.newDirectoryStream(dir,"catancraft_sector_"+c.city()+"_*_v1.dat")){
            for(Path p:paths){if(++count>4096)throw new IOException("Too many sector histories");CompoundTag j=ProducerJournal.read(p),identity=j.getCompound("identity");header(c,p,identity);if(j.getInt("schema")!=1||!j.hasUUID("id")||!ids.add(j.getUUID("id"))||!Set.of("PREPARED","APPLYING","PHYSICAL_COMPLETE","COMMITTED","ROLLING_BACK","ROLLED_BACK").contains(j.getString("phase")))throw new IOException("Malformed/duplicate sector history");
                SectorContracts.Direction d=SectorContracts.Direction.parse(identity.getString("sector"));if(d!=side)continue;sideHistories++;History h=new History(p,j);if(!j.getString("phase").equals("COMMITTED")&&!j.getString("phase").equals("ROLLED_BACK")){if(pending!=null)throw new IOException("Multiple pending sector histories");pending=h;}
                if(p.getFileName().toString().equals(pointer))pointed=h;if(state.receipt().equals(j.getUUID("id")+":paid")){if(paid!=null)throw new IOException("Multiple matching sector receipts");paid=h;}
            }
        }
        if(!pointer.isEmpty()&&pointed==null)throw new IOException("Saved sector operation has no retained journal");
        if(pending!=null)return new Selection(pending,pointed,paid);
        if(state.level()>0&&(paid==null||!paid.journal().getString("phase").equals("COMMITTED")||paid.journal().getCompound("spec").getInt("targetLevel")!=state.level()||!paid.journal().getCompound("spec").getString("targetRevision").equals(state.revision())))throw new IOException("Current purchased sector lacks its retained committed source receipt/revision proof");
        History selected=pointed!=null?pointed:paid;
        if(selected==null&&(sideHistories!=0||state.level()!=0||!state.receipt().isEmpty()||!state.revision().isEmpty()))throw new IOException("Sector progress/receipt has no retained historical proof");
        if(selected!=null){CompoundTag j=selected.journal();String phase=j.getString("phase");int expected=phase.equals("COMMITTED")?j.getCompound("spec").getInt("targetLevel"):j.getCompound("spec").getInt("sourceLevel");String receipt=phase.equals("COMMITTED")?j.getUUID("id")+":paid":j.getString("previousReceipt");if(!Set.of("COMMITTED","ROLLED_BACK").contains(phase)||state.level()!=expected||!state.receipt().equals(receipt))throw new IOException("Saved sector progress disagrees with terminal historical receipt");}
        return new Selection(null,selected,paid);
    }
    private static SectorContracts.Contract recorded(Context c,History history)throws IOException {
        CompoundTag j=history.journal(),id=j.getCompound("identity"),spec=j.getCompound("spec");header(c,history.path(),id);List<SectorTransaction.Cell> cells=new ArrayList<>();List<SectorTransaction.Read> reads=new ArrayList<>();
        for(Tag tag:spec.getList("cells",Tag.TAG_COMPOUND)){CompoundTag n=(CompoundTag)tag;cells.add(new SectorTransaction.Cell(BlockPos.of(n.getLong("pos")),state(n.getCompound("before")),state(n.getCompound("after"))));}
        for(Tag tag:spec.getList("reads",Tag.TAG_COMPOUND)){CompoundTag n=(CompoundTag)tag;reads.add(new SectorTransaction.Read(BlockPos.of(n.getLong("pos")),state(n.getCompound("state"))));}
        var plan=new SectorTransaction.Plan(spec.getString("sector"),spec.getString("sourceRevision"),spec.getString("targetRevision"),spec.getInt("sourceLevel"),spec.getInt("targetLevel"),cells,reads,SectorNaturalSupportPolicy.read(spec.getCompound("naturalSupport")));if(!SectorTransaction.describe(plan).equals(spec))throw new IOException("Changed or incomplete recorded sector plan");
        Map<SectorContracts.Corner,SectorContracts.Direction> corners=new EnumMap<>(SectorContracts.Corner.class);for(String key:id.getCompound("corners").getAllKeys()){
            SectorContracts.Corner corner;try{corner=SectorContracts.Corner.valueOf(key);}catch(IllegalArgumentException ex){throw new IOException("Unknown historical corner",ex);}
            corners.put(corner,SectorContracts.Direction.parse(id.getCompound("corners").getString(key)));}
        var side=SectorContracts.Direction.parse(id.getString("sector"));Map<SectorContracts.Direction,SectorContracts.Neighbor> neighbors=new EnumMap<>(SectorContracts.Direction.class);for(var d:side.adjacent()){CompoundTag n=id.getCompound("neighbors").getCompound(d.name());try{neighbors.put(d,new SectorContracts.Neighbor(n.getInt("level"),n.getString("receipt")));}catch(IllegalArgumentException e){throw new IOException("Invalid historical sector neighbor",e);}}
        Set<BlockPos> roads=new HashSet<>();for(Tag t:id.getList("roads",Tag.TAG_LONG))if(!roads.add(BlockPos.of(((LongTag)t).getAsLong())))throw new IOException("Duplicate historical road read");
        return new SectorContracts.Contract(c.root().toString(),c.city(),side,plan,corners,neighbors,roads,SectorTransaction.historicalCost(j.getCompound("cost")),id.getCompound("cityGroup"));
    }
    private static BlockState state(CompoundTag tag)throws IOException {BlockState s=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),tag);if(!NbtUtils.writeBlockState(s).equals(tag))throw new IOException("Unknown/noncanonical historical sector block state");return s;}
    static List<Path> pendingHistories(Context c)throws IOException {authority(c);List<Path> out=new ArrayList<>();Path dir=c.root().resolve("data");if(!Files.isDirectory(dir))return out;try(var paths=Files.newDirectoryStream(dir,"catancraft_sector_"+c.city()+"_*_v1.dat")){for(Path p:paths){CompoundTag j=ProducerJournal.read(p);header(c,p,j.getCompound("identity"));if(!Set.of("COMMITTED","ROLLED_BACK").contains(j.getString("phase")))out.add(p);}}return List.copyOf(out);}
    private interface RecoveryContext {Context forCity(String city)throws IOException;}
    /** Startup caller may retry existing approved operations; never initiates a sector purchase. */
    public static List<MapAssetService.Result> recoverPending(MinecraftServer server){
        try{
            if(!server.isSameThread())throw new IOException("Sector recovery requires main thread");
            FreshDemoAuthorization.authorize(server);CatanSavedData data=CatanSavedData.get(server);
            return recoverCities(city->{
                var territory=data.territory(city);if(territory==null||territory.ownerNationId()==null)return null;
                NationData nation=data.nation(territory.ownerNationId());if(nation==null)return null;
                return production(server,nation.leaderId(),city);
            });
        }catch(Exception e){return List.of(failure(e));}
    }
    static List<MapAssetService.Result> recoverFixture(List<Context> contexts)throws IOException {
        Map<String,Context> byCity=new HashMap<>();
        for(Context c:contexts){requireFixture(c);authority(c);if(byCity.put(c.city(),c)!=null)throw new IOException("Duplicate fixture recovery city");}
        return recoverCities(byCity::get);
    }
    private static List<MapAssetService.Result> recoverCities(RecoveryContext contexts){
        List<MapAssetService.Result> results=new ArrayList<>();
        for(char ch='a';ch<='p';ch++){
            String city=String.valueOf(ch);
            try{
                Context c=contexts.forCity(city);if(c==null)continue;
                for(Path file:pendingHistories(c)){
                    try{
                        CompoundTag journal=ProducerJournal.read(file);String phase=journal.getString("phase");SectorContracts.Direction side=SectorContracts.Direction.parse(journal.getCompound("identity").getString("sector"));
                        results.add(operate(c,side,phase.equals("ROLLING_BACK")?SectorTransaction.Action.ROLLBACK:SectorTransaction.Action.RESUME,SectorService::resolveProduction,(stage,writes)->{}));
                    }catch(Exception e){results.add(recoveryFailure(city,file.getFileName().toString(),e));}
                }
            }catch(Exception e){results.add(recoveryFailure(city,"city histories",e));}
        }
        return List.copyOf(results);
    }
    private static MapAssetService.Result recoveryFailure(String city,String history,Exception e){return failure(new IOException("Recovery city "+city+" ("+history+"): "+e.getMessage(),e));}
    private static MapAssetService.Result failure(Exception e){CatanCraft.LOGGER.debug("Sector service refused: {}",e.getMessage());return new MapAssetService.Result(false,"Sector request refused: "+e.getMessage()+". Preserve retained journals; recovery never repeats a purchase.",0);}
}
