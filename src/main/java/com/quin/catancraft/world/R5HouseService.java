package com.quin.catancraft.world;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.*;
import com.quin.catancraft.map.*;
import net.minecraft.commands.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Immutable house transactions shared by automatic base preparation and explicit maintenance. */
@Mod.EventBusSubscriber(modid=CatanCraft.MOD_ID)
public final class R5HouseService {
    private R5HouseService(){}
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        var root=Commands.literal("houses").requires(s->s.hasPermission(2));
        for(var action:R5HouseTransaction.Action.values()) {
            var city=Commands.argument("city",StringArgumentType.word());
            if(action==R5HouseTransaction.Action.STATUS)city.executes(c->execute(c.getSource(),StringArgumentType.getString(c,"city"),action));
            else city.then(Commands.literal("confirm").executes(c->execute(c.getSource(),StringArgumentType.getString(c,"city"),action)));
            root.then(Commands.literal(action.name().toLowerCase(Locale.ROOT)).then(city));
        }
        event.getDispatcher().register(Commands.literal("catan").then(Commands.literal("r5").then(root)));
    }
    private static synchronized int execute(CommandSourceStack source,String city,R5HouseTransaction.Action action) {
        var result=operation(source.getServer(),city,action);
        if(result.success())source.sendSuccess(()->Component.literal(result.message()),true);else source.sendFailure(Component.literal(result.message()));return result.success()?1:0;
    }
    static synchronized MapAssetService.Result operation(MinecraftServer server,String input,R5HouseTransaction.Action action) {
        try {
            var environment=context(server,input,action,action!=R5HouseTransaction.Action.STATUS,false);
            String city=input.toLowerCase(Locale.ROOT);
            var result=R5HouseTransaction.run(environment,action);CatanCraft.LOGGER.info("R5_HOUSES city={} action={} writes={} {}",city,action,result.writes(),result.message());
            return new MapAssetService.Result(true,result.message(),result.writes());
        }catch(Exception ex){CatanCraft.LOGGER.error("R5 house operation refused",ex);return new MapAssetService.Result(false,"R5 house operation stopped: "+ex.getMessage()+". Zero charge. Preserve blocks and history; inspect status before recovery.",0);}
    }
    static R5HouseTransaction.Environment context(MinecraftServer server,String input,
            R5HouseTransaction.Action action,boolean load,boolean automatic)throws IOException {
            Path root=TownHallRepairService.authorize(server);String city=input.toLowerCase(Locale.ROOT);
            if(!city.matches("[a-p]"))throw new IOException("Unknown city");
            var def=MapDefinitionManager.territory(city);ProducerService.validateMap(def);
            CatanSavedData data=CatanSavedData.get(server);TerritoryData t=data.territory(city);
            if(t==null||t.ownerNationId()==null||data.nation(t.ownerNationId())==null||data.placedMapAsset("territory:"+city+":city_base")==null)throw new IOException("Owned activated city required");
            Path directory=root.resolve("data");Files.createDirectories(directory);List<Path> history=new ArrayList<>();
            try(var paths=Files.newDirectoryStream(directory,"catancraft_r5_houses_"+city+"_*.dat")){for(Path p:paths){if(history.size()>128)throw new IOException("Unexpected house history count");history.add(p);}}
            Path active=null;List<Path> committed=new ArrayList<>();boolean rolledBack=false;
            for(Path p:history) {
                CompoundTag journal=ProducerJournal.read(p),identity=journal.getCompound("identity");
                if(!identity.getString("world").equals(root.toString())||!identity.getString("dimension").equals(def.dimension())||!identity.getString("city").equals(city)||!identity.getString("owner").equals(String.valueOf(t.ownerNationId())))throw new IOException("Foreign house history identity; retain for review");
                var phase=R5HouseTransaction.phase(journal);
                if(phase==R5HouseTransaction.Phase.COMMITTED)committed.add(p);
                else if(phase==R5HouseTransaction.Phase.ROLLED_BACK)rolledBack=true;
                else{if(active!=null)throw new IOException("Multiple pending house journals");active=p;}
            }
            if(automatic&&active==null&&committed.isEmpty()&&rolledBack)throw new IOException("House history was intentionally rolled back; automatic replay is disabled");
            if(committed.size()>1)throw new IOException("Multiple committed house histories require review");
            if(active!=null&&action==R5HouseTransaction.Action.APPLY)throw new IOException("Pending house operation; use status then resume or rollback");
            Path selected=active!=null?active:committed.isEmpty()?null:committed.get(0);
            if(selected==null&&(action==R5HouseTransaction.Action.RESUME||action==R5HouseTransaction.Action.ROLLBACK))throw new IOException("No current house journal");
            if(selected==null)selected=directory.resolve("catancraft_r5_houses_"+city+"_"+UUID.randomUUID()+".dat");
            final Path file=selected;final UUID owner=t.ownerNationId();final MapAnchor anchor=SchematicAssetRegistry.territoryAnchor("starter_settlement",def);
            final var level=server.overworld();List<net.minecraft.world.phys.AABB> boxes=new ArrayList<>();
            for(String group:R5HouseRevisions.groups()) {
                var plan=R5HouseRevisions.plan(group,R5HouseRevisions.Revision.R5,anchor);var box=TownHallRepairTransaction.bounds(plan);boxes.add(box);
                if(load)for(int x=((int)box.minX-2)>>4;x<=((int)box.maxX+2)>>4;x++)for(int z=((int)box.minZ-2)>>4;z<=((int)box.maxZ+2)>>4;z++)level.getChunk(x,z);
            }
            if(load)StreetscapeService.requireLoadedEntities(level,boxes);
            return new R5HouseTransaction.Environment() {
                public net.minecraft.server.level.ServerLevel level(){return level;}public Path file(){return file;}public MapAnchor anchor(){return anchor;}
                public CompoundTag identity(){CompoundTag n=new CompoundTag();n.putString("world",root.toString());n.putString("dimension",def.dimension());n.putString("city",city);n.putString("owner",String.valueOf(t.ownerNationId()));n.putIntArray("anchor",new int[]{anchor.x(),anchor.y(),anchor.z(),anchor.rotation()});return n;}
                public CompoundTag ledger(){return data.save(new CompoundTag());}
                public void save()throws IOException{if(!server.saveEverything(true,true,true))throw new IOException("House world save incomplete");}
                public void allowWrite(BlockPos p)throws IOException{if(!owner.equals(t.ownerNationId())||!def.siegeRegion().contains(p)||!def.restorationRegion().contains(p))throw new IOException("House mutation outside authorized ownership/bounds");}
            };
    }
    @SubscribeEvent public static void discoverPending(ServerStartedEvent event) {
        try {
            Path root=TownHallRepairService.authorize(event.getServer()),directory=root.resolve("data");if(!Files.isDirectory(directory))return;
            try(var paths=Files.newDirectoryStream(directory,"catancraft_r5_houses_*.dat")) {
                for(Path p:paths)try {
                    var phase=R5HouseTransaction.phase(ProducerJournal.read(p));
                    if(phase!=R5HouseTransaction.Phase.COMMITTED&&phase!=R5HouseTransaction.Phase.ROLLED_BACK)CatanCraft.LOGGER.warn("Pending R5 house history {} phase {}; use guarded status/resume/rollback. No startup writes.",p.getFileName(),phase);
                }catch(IOException ex){CatanCraft.LOGGER.error("Unreadable house history retained: "+p.getFileName(),ex);}
            }
        }catch(IOException notAuthorized){/* No writes outside an authorized development world. */}
    }
}
