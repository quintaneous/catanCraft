package com.quin.catancraft.world;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.*;
import com.quin.catancraft.map.*;
import net.minecraft.commands.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Existing resource-upgrade action plus explicit zero-price retrofit and recovery.
 * Only the authorized developed R7 test-world is enabled. No source-world imports.
 */
@Mod.EventBusSubscriber(modid=CatanCraft.MOD_ID)
public final class ProducerService {
    private ProducerService() {}
    private static String key(String city,String family) {return "territory:"+city+":"+SchematicAssetRegistry.asset(family).plot();}
    private static String receiptKey(String city) {return "producer_transaction:"+city;}
    public static boolean hasVisuals(TerritoryDefinition def){return def!=null && !ProducerRevisions.applicable(def).isEmpty();}
    public static synchronized MapAssetService.Result upgrade(MinecraftServer server,String city) {
        return operation(server,city,"upgrade",ProducerTransaction.Action.APPLY);
    }
    public static synchronized MapAssetService.Result retrofit(MinecraftServer server,String city) {
        return operation(server,city,"retrofit",ProducerTransaction.Action.APPLY);
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        var root=Commands.literal("producer").requires(s->s.hasPermission(2));
        root.then(Commands.literal("status").then(Commands.argument("city",StringArgumentType.word())
            .executes(c->command(c.getSource(),StringArgumentType.getString(c,"city"),"status",ProducerTransaction.Action.STATUS))));
        for(String name:List.of("retrofit","resume","rollback")) {
            ProducerTransaction.Action action=name.equals("retrofit")?ProducerTransaction.Action.APPLY:
                name.equals("resume")?ProducerTransaction.Action.RESUME:ProducerTransaction.Action.ROLLBACK;
            root.then(Commands.literal(name).then(Commands.argument("city",StringArgumentType.word()).then(Commands.literal("confirm")
                .executes(c->command(c.getSource(),StringArgumentType.getString(c,"city"),name,action)))));
        }
        event.getDispatcher().register(Commands.literal("catan").then(root));
    }
    private static int command(CommandSourceStack source,String city,String kind,ProducerTransaction.Action action) {
        var result=operation(source.getServer(),city,kind,action);
        if(result.success())source.sendSuccess(()->Component.literal(result.message()),true);
        else source.sendFailure(Component.literal(result.message()));return result.success()?1:0;
    }
    private static synchronized MapAssetService.Result operation(MinecraftServer server,String input,String kind,ProducerTransaction.Action action) {
        try {
            String city=input.toLowerCase(Locale.ROOT);Path root=TownHallRepairService.authorize(server);
            if(!city.matches("[a-p]"))throw new IOException("Unknown city");
            CatanSavedData data=CatanSavedData.get(server);TerritoryData t=data.territory(city);
            TerritoryDefinition def=MapDefinitionManager.territory(city);
            if(t==null || t.ownerNationId()==null || data.nation(t.ownerNationId())==null)throw new IOException("Owned territory required");
            validateMap(def);if(!hasVisuals(def))throw new IOException("Strategic sites have no new visual tiers in this release");
            List<Path> histories=histories(root,city);Path pending=null;
            for(Path p:histories) {
                var phase=ProducerTransaction.phase(ProducerJournal.read(p));
                if(phase!=ProducerTransaction.Phase.COMMITTED && phase!=ProducerTransaction.Phase.ROLLED_BACK) {
                    if(pending!=null)throw new IOException("Multiple pending producer journals; manual review required");pending=p;
                }
            }
            Path path;ProducerTransaction.Spec spec;
            if(action==ProducerTransaction.Action.RESUME || action==ProducerTransaction.Action.ROLLBACK || (action==ProducerTransaction.Action.STATUS && pending!=null)) {
                path=pending;
                if(path==null) {
                    String receipt=data.placedMapAsset(receiptKey(city));
                    for(Path p:histories) {
                        CompoundTag j=ProducerJournal.read(p);
                        if(receipt!=null && receipt.startsWith(j.getUUID("id").toString()+":"))path=p;
                    }
                }
                if(path==null)throw new IOException("No current producer transaction to recover");
                spec=readSpec(ProducerJournal.read(path).getCompound("spec"),def);
            } else {
                if(pending!=null)throw new IOException("Incomplete producer transaction: use /catan producer resume "+city+" confirm");
                int paid=t.producerLevel(),target=kind.equals("upgrade")?paid+1:paid;
                if(target>5)throw new IOException("Resource producer is already Level 5");
                path=root.resolve("data/catancraft_producer_"+city+"_"+kind+"_l"+target+"_r1.dat");
                if(Files.exists(path,LinkOption.NOFOLLOW_LINKS)
                    && ProducerTransaction.phase(ProducerJournal.read(path))==ProducerTransaction.Phase.ROLLED_BACK)
                    path=root.resolve("data/catancraft_producer_"+city+"_"+kind+"_l"+target+"_"+UUID.randomUUID()+"_r1.dat");
                if(Files.exists(path,LinkOption.NOFOLLOW_LINKS))spec=readSpec(ProducerJournal.read(path).getCompound("spec"),def);
                else {
                    List<ProducerTransaction.Plot> plots=new ArrayList<>();
                    for(String f:ProducerRevisions.applicable(def))plots.add(new ProducerTransaction.Plot(f,
                        ProducerRevisions.revision(f,data.placedMapAsset(key(city,f))),target,SchematicAssetRegistry.territoryAnchor(f,def)));
                    spec=new ProducerTransaction.Spec(paid,target,plots);
                }
            }
            var env=context(server,root,data,t,def,spec,path,action!=ProducerTransaction.Action.STATUS);
            var result=ProducerTransaction.run(env,action);
            CatanCraft.LOGGER.info("PRODUCER_R1 city={} kind={} writes={} {}",city,kind,result.writes(),result.message());
            return new MapAssetService.Result(true,result.message(),result.writes());
        }catch(Exception ex) {
            CatanCraft.LOGGER.error("Producer operation refused city={} kind={}",input,kind,ex);
            return new MapAssetService.Result(false,"Producer operation stopped: "+ex.getMessage()+
                ". Preserve blocks and journals. A retained incomplete transaction can be resumed or rolled back; never repeat a purchase to recover it.",0);
        }
    }
    /** Only reconcile existing incomplete owned journals, never initiate an upgrade on startup. */
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void recoverStarted(ServerStartedEvent event) {
        MinecraftServer server=event.getServer();Path root;
        try {root=TownHallRepairService.authorize(server);}catch(IOException notAuthorized){return;}
        for(char c='a';c<='p';c++)try {
            String city=String.valueOf(c);
            for(Path p:histories(root,city)) {
                var phase=ProducerTransaction.phase(ProducerJournal.read(p));
                if(phase==ProducerTransaction.Phase.COMMITTED || phase==ProducerTransaction.Phase.ROLLED_BACK)continue;
                var result=operation(server,city,"restart-recovery",phase==ProducerTransaction.Phase.ROLLING_BACK?
                    ProducerTransaction.Action.ROLLBACK:ProducerTransaction.Action.RESUME);
                if(!result.success())CatanCraft.LOGGER.error("Producer restart recovery remains blocked: {}",result.message());
                break;
            }
        }catch(Exception e){CatanCraft.LOGGER.error("Producer recovery refused; retain journal for city "+c,e);}
    }
    private static List<Path> histories(Path root,String city) throws IOException {
        Path dir=root.resolve("data");if(!Files.isDirectory(dir))return List.of();
        try(var stream=Files.newDirectoryStream(dir,"catancraft_producer_"+city+"_*_r1.dat")) {
            List<Path> paths=new ArrayList<>();for(Path p:stream){if(paths.size()>=128)throw new IOException("Unexpected producer history count");paths.add(p);}
            return paths;
        }
    }
    private static ProducerTransaction.Spec readSpec(CompoundTag n,TerritoryDefinition def) throws IOException {
        List<ProducerTransaction.Plot> plots=new ArrayList<>();ListTag tags=n.getList("plots",Tag.TAG_COMPOUND);
        List<String> families=ProducerRevisions.applicable(def);
        if(tags.size()!=families.size())throw new IOException("Producer family set changed");
        for(int i=0;i<tags.size();i++) {
            CompoundTag p=tags.getCompound(i);String f=families.get(i);MapAnchor a=SchematicAssetRegistry.territoryAnchor(f,def);
            if(!f.equals(p.getString("family")) || !Arrays.equals(p.getIntArray("anchor"),new int[]{a.x(),a.y(),a.z(),a.rotation()}))
                throw new IOException("Producer selection/anchor changed");
            plots.add(new ProducerTransaction.Plot(f,p.getInt("source"),p.getInt("target"),a));
        }
        var result=new ProducerTransaction.Spec(n.getInt("paidBefore"),n.getInt("paidAfter"),plots);
        if(!ProducerTransaction.describe(result).equals(n))throw new IOException("Producer source/cost contract changed");return result;
    }
    static void validateMap(TerritoryDefinition d) throws IOException {
        if(d==null || !d.id().matches("[a-p]") || d.cityCenter()==null || d.siegeRegion()==null || d.restorationRegion()==null)
            throw new IOException("Missing producer placement contract");
        int index=d.id().charAt(0)-'a';MapAnchor c=d.cityCenter();
        if(!d.dimension().equals("minecraft:overworld") || c.x()!=-750+500*(index%4) || c.z()!=-750+500*(index/4)
            || c.y()!=81 || c.rotation()!=(index<8?0:180))throw new IOException("Map differs from reviewed Resource R1 placements");
        List<String> expected="admp".contains(d.id())?List.of("quarry","farm","lumberyard"):
            "bhio".contains(d.id())?List.of("lumberyard"):"celn".contains(d.id())?List.of("quarry"):List.of();
        if(!ProducerRevisions.applicable(d).equals(expected))throw new IOException("Map resource applicability changed");
    }
    private static ProducerTransaction.Environment context(MinecraftServer server,Path root,CatanSavedData data,TerritoryData t,
            TerritoryDefinition def,ProducerTransaction.Spec spec,Path file,boolean loadChunks) throws IOException {
        ServerLevel found=null;for(ServerLevel l:server.getAllLevels())if(l.dimension().location().toString().equals(def.dimension()))found=l;
        if(found==null)throw new IOException("Producer dimension unavailable");final ServerLevel level=found;
        if(loadChunks)for(var p:spec.plots()) {
            var b=SchematicAssetRegistry.asset(p.family()).boundsAt(p.anchor());
            for(int x=(b.min().getX()-2)>>4;x<=(b.max().getX()+2)>>4;x++)
                for(int z=(b.min().getZ()-2)>>4;z<=(b.max().getZ()+2)>>4;z++)level.getChunk(x,z);
        }
        UUID owner=t.ownerNationId();NationData nation=data.nation(owner);
        return new ProducerTransaction.Environment() {
            public ServerLevel level(){return level;}public Path file(){return file;}public ProducerTransaction.Spec spec(){return spec;}
            public NationData nation(){return nation;}public TerritoryData territory(){return t;}
            public CompoundTag identity(){
                CompoundTag n=new CompoundTag();n.putString("world",root.toString());n.putString("dimension",def.dimension());n.putString("city",def.id());
                n.putString("owner",String.valueOf(t.ownerNationId()));n.putString("mapContract","resource_r1_2026_10_14");return n;
            }
            public String token(String f){return data.placedMapAsset(key(def.id(),f));}
            public void token(String f,String value){data.setPlacedMapAsset(key(def.id(),f),value);}
            public String receipt(){return data.placedMapAsset(receiptKey(def.id()));}
            public void receipt(String value){data.setPlacedMapAsset(receiptKey(def.id()),value);}
            public CompoundTag ledger(){return data.save(new CompoundTag());}public void dirty(){data.setDirty();}
            public void save() throws IOException {if(!server.saveEverything(true,true,true))throw new IOException("Server save did not complete");}
            public void allowWrite(BlockPos pos) throws IOException {
                if(!owner.equals(t.ownerNationId()) || !def.siegeRegion().contains(pos) || !def.restorationRegion().contains(pos))
                    throw new IOException("Producer write outside authorized city or ownership changed");
            }
        };
    }
    /** Adopt prebuilt L1 without replacing a barrel, crop, or any world block. All plots preflight first. */
    public static MapAssetService.Result adoptPrebuilt(MinecraftServer server,CatanSavedData data,TerritoryDefinition def) {
        try {
            TownHallRepairService.authorize(server);validateMap(def);ServerLevel level=server.overworld();List<String> adopt=new ArrayList<>();
            for(String f:ProducerRevisions.applicable(def)) {
                String token=data.placedMapAsset(key(def.id(),f));
                if(token!=null){ProducerRevisions.revision(f,token);continue;}
                var plan=ProducerRevisions.plan(f,1,SchematicAssetRegistry.territoryAnchor(f,def));
                var bounds=SchematicAssetRegistry.asset(f).boundsAt(SchematicAssetRegistry.territoryAnchor(f,def));
                for(int x=bounds.min().getX()>>4;x<=bounds.max().getX()>>4;x++)
                    for(int z=bounds.min().getZ()>>4;z<=bounds.max().getZ()>>4;z++)level.getChunk(x,z);
                for(var cell:plan) {
                    if(!level.hasChunkAt(cell.position()))throw new IOException("Prebuilt producer chunk unloaded");
                    if(!ProducerRevisions.matches(level.getBlockState(cell.position()),cell.state()))throw new IOException("Unrecognized prebuilt producer at "+cell.position());
                    var be=level.getBlockEntity(cell.position());
                    if(cell.state().is(net.minecraft.world.level.block.Blocks.BARREL)) {
                        if(!(be instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity))throw new IOException("Missing prebuilt barrel");
                    } else if(be!=null)throw new IOException("Foreign prebuilt producer block entity");
                }
                adopt.add(f);
            }
            for(String f:adopt)data.setPlacedMapAsset(key(def.id(),f),ProducerRevisions.token(f,1));
            return new MapAssetService.Result(true,"Prebuilt producer records adopted without block writes",0);
        }catch(Exception e){return new MapAssetService.Result(false,"Prebuilt producer adoption refused: "+e.getMessage(),0);}
    }
}
