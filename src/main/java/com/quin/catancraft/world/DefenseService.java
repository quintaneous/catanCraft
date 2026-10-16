package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.*;
import com.quin.catancraft.economy.EconomyCost;
import com.quin.catancraft.map.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Main-thread serialized, authorized defense backend. Commands must enforce actor permission. */
@Mod.EventBusSubscriber(modid=CatanCraft.MOD_ID)
public final class DefenseService {
    private DefenseService(){}
    private static boolean busy;
    public static String ownedKey(String city,String socket){return "defense_owned:"+city+":"+socket;}
    private static String receiptKey(String city,String socket){return "defense_receipt:"+city+":"+socket;}
    public static synchronized MapAssetService.Result operation(MinecraftServer server,String city,String socket,DefenseTransaction.Action action,EconomyCost approvedCost){
        if(busy)return new MapAssetService.Result(false,"Defense transaction already active; retry after it completes",0);
        busy=true;
        try{
            if(!server.isSameThread())throw new IOException("Defense operation requires server main thread");
            city=city.toLowerCase(Locale.ROOT);socket=socket.toLowerCase(Locale.ROOT);
            if(!city.matches("[a-p]")||!socket.matches("[a-z0-9_]+"))throw new IOException("Unknown defense city/socket");
            Path root=TownHallRepairService.authorize(server);var plan=DefenseRevisions.plan(city,socket);
            CatanSavedData data=CatanSavedData.get(server);TerritoryData territory=data.territory(city);TerritoryDefinition def=MapDefinitionManager.territory(city);
            if(territory==null||territory.ownerNationId()==null||data.nation(territory.ownerNationId())==null)throw new IOException("Owned defense city required");
            ProducerService.validateMap(def);UUID owner=territory.ownerNationId();NationData nation=data.nation(owner);
            final Path file=chooseHistory(root,data,city,socket,action);
            // Recovery uses the historical immutable price, never a newly chosen catalog entry.
            final EconomyCost cost;
            if(Files.exists(file,LinkOption.NOFOLLOW_LINKS)) {
                cost=historicalCost(ProducerJournal.read(file).getCompound("cost"));
            } else {
                cost=DefenseCostCatalog.cost(plan.module());
                if(approvedCost!=null&&!DefenseTransaction.cost(approvedCost).equals(DefenseTransaction.cost(cost)))
                    throw new IOException("Defense service price override differs from approved catalog");
            }
            ServerLevel level=null;for(ServerLevel l:server.getAllLevels())if(l.dimension().location().toString().equals(def.dimension()))level=l;
            if(level==null)throw new IOException("Defense dimension unavailable");final ServerLevel world=level;final EconomyCost actualCost=cost;
            // No broad chunk loading; an unknown/unloaded authored location remains a refusal.
            final String selectedCity=city,selectedSocket=socket;
            if(action!=DefenseTransaction.Action.STATUS){
                net.minecraft.world.phys.AABB bounds=null;
                for(var cell:plan.cells()){var box=new net.minecraft.world.phys.AABB(cell.pos()).inflate(2);bounds=bounds==null?box:bounds.minmax(box);}
                for(var read:plan.reads()){var box=new net.minecraft.world.phys.AABB(read.pos());bounds=bounds==null?box:bounds.minmax(box);}
                if(bounds!=null)StreetscapeService.requireLoadedEntities(world,List.of(bounds));
            }

            Map<BlockPos,net.minecraft.world.level.block.state.BlockState> ownedOverlay=new HashMap<>();
            for(var candidate:DefenseRevisions.city(selectedCity)){
                if(candidate.socket().equals(selectedSocket)||!candidate.token().equals(data.placedMapAsset(ownedKey(selectedCity,candidate.socket()))))continue;
                for(var cell:candidate.cells())if(ownedOverlay.put(cell.pos(),cell.after())!=null)throw new IOException("Overlapping owned defense writes");
            }
            var env=new DefenseTransaction.Environment(){
                public ServerLevel level(){return world;}public Path file(){return file;}public DefenseRevisions.Plan plan(){return plan;}public EconomyCost cost(){return actualCost;}public NationData nation(){return nation;}
                public CompoundTag identity(){CompoundTag n=new CompoundTag();n.putString("world",root.toString());n.putString("dimension",def.dimension());n.putString("city",selectedCity);n.putString("socket",selectedSocket);n.putUUID("owner",owner);return n;}
                public String receipt(){return data.placedMapAsset(receiptKey(selectedCity,selectedSocket));}public void receipt(String value){data.setPlacedMapAsset(receiptKey(selectedCity,selectedSocket),value);data.setDirty();}
                public String owned(){String value=data.placedMapAsset(ownedKey(selectedCity,selectedSocket));return "defense_unowned".equals(value)?null:value;}public void owned(String value){data.setPlacedMapAsset(ownedKey(selectedCity,selectedSocket),value==null?"defense_unowned":value);data.setDirty();}
                public void save() throws IOException {data.setDirty();if(!server.saveEverything(true,true,true))throw new IOException("Defense ledger/world save did not complete");}
                public void allow(BlockPos pos) throws IOException {
                    if(!owner.equals(territory.ownerNationId()) || !def.siegeRegion().contains(pos)||!def.restorationRegion().contains(pos))throw new IOException("Defense ownership or approved restoration bounds changed");
                    // Whole developed core and resource corridor, including unpurchased future upgrades, remain reserved.
                    var center=def.cityCenter();int dx=pos.getX()-center.x(),dz=pos.getZ()-center.z();if(center.rotation()==180){dx=-dx;dz=-dz;}int x=dx+80,z=dz+84;
                    if((x>=0&&x<=160&&z>=1&&z<=167)||(x>=60&&x<=100&&z>=168&&z<=180))throw new IOException("Defense write enters protected developed-city reservation");
                }
                public net.minecraft.world.level.block.state.BlockState readState(DefenseRevisions.Read read) throws IOException {
                    // Compatible owned modules can occupy one another's authored preserve-air envelope.
                    // Only exact reviewed after-states with a durable owned marker are recognized.
                    return ownedOverlay.getOrDefault(read.pos(),read.state());
                }
                public void requirements(boolean rollback) throws IOException {
                    if(!owner.equals(territory.ownerNationId()))throw new IOException("Defense territory owner changed");
                    Path journalDir=root.resolve("data");
                    if(Files.isDirectory(journalDir))try(var paths=Files.newDirectoryStream(journalDir,"catancraft_defense_"+selectedCity+"_*_r4a_r6_1.dat")){
                        int count=0;for(Path other:paths){
                            if(++count>4096)throw new IOException("Excess defense histories require review");
                            CompoundTag existing=ProducerJournal.read(other);String phase=existing.getString("phase");
                            if(!existing.getCompound("identity").getString("world").equals(root.toString()) || !existing.getCompound("identity").getString("city").equals(selectedCity) || !Set.of("PREPARED","RESERVED","APPLYING","COMMITTED","ROLLING_BACK","ROLLED_BACK").contains(phase))throw new IOException("Unknown/corrupt defense city history");
                            if(!other.equals(file)&&!phase.equals("COMMITTED")&&!phase.equals("ROLLED_BACK"))throw new IOException("Pending defense city reservation; recover socket "+existing.getCompound("identity").getString("socket")+" first");
                        }
                    }

                    for(var candidate:DefenseRevisions.city(selectedCity)){
                        String marker=data.placedMapAsset(ownedKey(selectedCity,candidate.socket()));if(marker==null||marker.isEmpty()||marker.equals("defense_unowned"))continue;
                        if(!marker.equals(candidate.token()))throw new IOException("Unknown defense revision at "+candidate.socket());
                        if(!candidate.socket().equals(selectedSocket)&&(plan.excludes().contains(candidate.socket())||candidate.excludes().contains(selectedSocket)))throw new IOException("Exclusive owned defense: "+candidate.socket());
                        if(rollback&&candidate.dependencies().contains(selectedSocket))throw new IOException("Rollback defense dependent first: "+candidate.socket());
                    }
                    if(!rollback)for(String dep:plan.dependencies())if(!DefenseRevisions.plan(selectedCity,dep).token().equals(data.placedMapAsset(ownedKey(selectedCity,dep))))throw new IOException("Missing owned defense dependency: "+dep);
                    // Unknown/obsolete excluded IDs must also refuse even though deliberately absent from registry.
                    for(String excluded:plan.excludes()){String marker=data.placedMapAsset(ownedKey(selectedCity,excluded));if(marker!=null&&!marker.isEmpty()&&!marker.equals("defense_unowned"))throw new IOException("Excluded defense already owned: "+excluded);}
                }
            };
            var result=DefenseTransaction.run(env,action);return new MapAssetService.Result(true,result.message(),result.writes());
        }catch(Exception x){CatanCraft.LOGGER.error("Defense operation refused city={} socket={}",city,socket,x);return new MapAssetService.Result(false,"Defense refused: "+x.getMessage()+". Preserve retained journal; no duplicate purchase to recover.",0);}finally{busy=false;}
    }
    /** Strict historical pricing: unknown fields/types and nonpositive material amounts fail closed. */
    static EconomyCost historicalCost(CompoundTag c) throws IOException {
        if(!c.contains("money",Tag.TAG_LONG)||c.getLong("money")<0)throw new IOException("Invalid historical defense money");
        Map<ResourceType,Long> resources=new EnumMap<>(ResourceType.class);Map<String,ResourceType> names=new HashMap<>();for(ResourceType r:ResourceType.values())names.put(r.id(),r);
        for(String key:c.getAllKeys())if(!key.equals("money")){
            ResourceType r=names.get(key);if(r==null||!c.contains(key,Tag.TAG_LONG)||c.getLong(key)<=0)throw new IOException("Unknown/invalid historical defense material: "+key);resources.put(r,c.getLong(key));
        }
        return new EconomyCost(c.getLong("money"),resources);
    }
    static Path chooseHistory(Path root,CatanSavedData data,String city,String socket,DefenseTransaction.Action action) throws IOException {
        Path dir=root.resolve("data"),base=dir.resolve("catancraft_defense_"+city+"_"+socket+"_r4a_r6_1.dat");if(!Files.isDirectory(dir))return base;
        String receipt=data.placedMapAsset(receiptKey(city,socket));Path current=null,pending=null;int matches=0;
        try(var paths=Files.newDirectoryStream(dir,"catancraft_defense_"+city+"_*_r4a_r6_1.dat")){
            int checked=0;for(Path p:paths){
                if(++checked>4096)throw new IOException("Too many defense histories");CompoundTag j=ProducerJournal.read(p),id=j.getCompound("identity");
                if(!id.getString("world").equals(root.toString())||!id.getString("city").equals(city)||!j.hasUUID("id"))throw new IOException("Foreign defense city history");
                String phase=j.getString("phase");if(!Set.of("PREPARED","RESERVED","APPLYING","COMMITTED","ROLLING_BACK","ROLLED_BACK").contains(phase))throw new IOException("Invalid defense history phase");
                if(!id.getString("socket").equals(socket))continue;matches++;
                if(!phase.equals("COMMITTED")&&!phase.equals("ROLLED_BACK")){if(pending!=null)throw new IOException("Multiple pending socket histories");pending=p;}
                if(receipt!=null&&receipt.startsWith(j.getUUID("id")+":")){if(current!=null)throw new IOException("Duplicate defense receipt histories");current=p;}
            }
        }
        if(pending!=null)return pending;
        if(current!=null){
            if(action==DefenseTransaction.Action.APPLY&&ProducerJournal.read(current).getString("phase").equals("ROLLED_BACK"))return dir.resolve("catancraft_defense_"+city+"_"+socket+"_"+UUID.randomUUID()+"_r4a_r6_1.dat");
            return current;
        }
        if(matches!=0 || (receipt!=null&&!receipt.isEmpty()))throw new IOException("Defense receipt has no matching retained history; preserve ledger and recover the missing journal");return base;
    }
    @SubscribeEvent public static void recoverStarted(ServerStartedEvent event){
        MinecraftServer server=event.getServer();Path root;try{root=TownHallRepairService.authorize(server);}catch(IOException denied){return;}
        try(var paths=Files.newDirectoryStream(root.resolve("data"),"catancraft_defense_*_r4a_r6_1.dat")){
            for(Path p:paths)try{
                CompoundTag j=ProducerJournal.read(p);String phase=j.getString("phase");if(phase.equals("COMMITTED")||phase.equals("ROLLED_BACK"))continue;
                CompoundTag id=j.getCompound("identity");var result=operation(server,id.getString("city"),id.getString("socket"),phase.equals("ROLLING_BACK")?DefenseTransaction.Action.ROLLBACK:DefenseTransaction.Action.RESUME,null);
                if(!result.success())CatanCraft.LOGGER.error("Defense recovery blocked: {}",result.message());
            }catch(Exception x){CatanCraft.LOGGER.error("Retain unresolved defense journal {}",p,x);}
        }catch(NoSuchFileException empty){}catch(IOException x){CatanCraft.LOGGER.error("Defense recovery scan refused",x);}
    }
}
