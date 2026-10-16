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
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Explicit OP maintenance on the already authorized R7 development world; no install on startup. */
@Mod.EventBusSubscriber(modid=CatanCraft.MOD_ID)
public final class StreetscapeService {
    private StreetscapeService(){}
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        var root=Commands.literal("public").requires(s->s.hasPermission(2));
        for(var action:StreetscapeTransaction.Action.values()) {
            String word=action.name().toLowerCase(Locale.ROOT);
            var city=Commands.argument("city",StringArgumentType.word());
            if(action==StreetscapeTransaction.Action.STATUS)city.executes(c->execute(c.getSource(),StringArgumentType.getString(c,"city"),action));
            else city.then(Commands.literal("confirm").executes(c->execute(c.getSource(),StringArgumentType.getString(c,"city"),action)));
            root.then(Commands.literal(word).then(city));
        }
        event.getDispatcher().register(Commands.literal("catan").then(Commands.literal("r5").then(root)));
    }
    private static synchronized int execute(CommandSourceStack source,String city,StreetscapeTransaction.Action action) {
        try {
            var e=context(source.getServer(),city,action!=StreetscapeTransaction.Action.STATUS);
            var r=StreetscapeTransaction.run(e,action);
            CatanCraft.LOGGER.info("R5_PUBLIC city={} action={} writes={} {}",city,action,r.writes(),r.message());
            source.sendSuccess(()->Component.literal(r.message()),true);return 1;
        }catch(Exception ex) {
            CatanCraft.LOGGER.error("R5 public operation refused city={} action={}",city,action,ex);
            source.sendFailure(Component.literal("R5 public operation stopped: "+ex.getMessage()+
                ". Zero charge. Preserve blocks and journals; use status before any resume/rollback."));return 0;
        }
    }
    private static Path file(Path root,String city){return root.resolve("data/catancraft_r5_public_"+city+"_v1.dat");}
    static StreetscapeTransaction.Environment context(MinecraftServer server,String input,boolean load)throws IOException {
        Path root=TownHallRepairService.authorize(server);String city=input.toLowerCase(Locale.ROOT);
        if(!city.matches("[a-p]"))throw new IOException("Unknown city");
        var def=MapDefinitionManager.territory(city);ProducerService.validateMap(def);
        CatanSavedData data=CatanSavedData.get(server);TerritoryData territory=data.territory(city);
        if(territory==null || territory.ownerNationId()==null || data.nation(territory.ownerNationId())==null
            || data.placedMapAsset("territory:"+city+":city_base")==null)throw new IOException("Owned, activated city required");
        var plan=StreetscapeR5.plan(def.cityCenter());ServerLevel level=server.overworld();UUID owner=territory.ownerNationId();
        if(load)for(var box:plan.boxes())for(int x=((int)box.minX-1)>>4;x<=((int)box.maxX+1)>>4;x++)
            for(int z=((int)box.minZ-1)>>4;z<=((int)box.maxZ+1)>>4;z++)level.getChunk(x,z);
        if(load)requireLoadedEntities(level,plan.boxes());
        return new StreetscapeTransaction.Environment() {
            public ServerLevel level(){return level;}public Path file(){return StreetscapeService.file(root,city);}
            public StreetscapeR5.Plan plan(){return plan;}
            public CompoundTag identity(){
                CompoundTag n=new CompoundTag();n.putString("world",root.toString());n.putString("dimension",def.dimension());n.putString("city",city);
                n.putString("owner",String.valueOf(territory.ownerNationId()));MapAnchor a=def.cityCenter();
                n.putIntArray("center",new int[]{a.x(),a.y(),a.z(),a.rotation()});return n;
            }
            public CompoundTag ledger(){return data.save(new CompoundTag());}
            public void save()throws IOException {if(!server.saveEverything(true,true,true))throw new IOException("World save did not complete");}
            public void allowWrite(BlockPos pos)throws IOException {
                if(!owner.equals(territory.ownerNationId()) || !def.siegeRegion().contains(pos)||!def.restorationRegion().contains(pos))
                    throw new IOException("Public-feature write outside authorized city/owner");
            }
        };
    }
    /** A loaded block chunk is not proof that its saved entities have finished loading. */
    static void requireLoadedEntities(ServerLevel level,List<net.minecraft.world.phys.AABB> boxes)throws IOException {
        for(var box:boxes)for(int x=((int)box.minX-1)>>4;x<=((int)box.maxX+1)>>4;x++)
            for(int z=((int)box.minZ-1)>>4;z<=((int)box.maxZ+1)>>4;z++)
                if(!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(x,z)))
                    throw new IOException("Public-feature entities are still loading; wait near the city, then check status and retry. No blocks written by this request");
    }
    /** Discovery only: a restart never initiates a migration or guesses that mixed blocks are safe. */
    @SubscribeEvent public static void recoverNotice(ServerStartedEvent event) {
        try {
            Path root=TownHallRepairService.authorize(event.getServer());
            for(char c='a';c<='p';c++) {
                Path p=file(root,String.valueOf(c));if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))continue;
                try {
                    var phase=StreetscapeTransaction.phase(StreetscapeJournal.read(p));
                    if(phase!=StreetscapeTransaction.Phase.COMMITTED&&phase!=StreetscapeTransaction.Phase.ROLLED_BACK)
                        CatanCraft.LOGGER.warn("R5 public transaction {} retained in {}. Use /catan r5 public status {} then guarded resume/rollback; no automatic writes.",c,phase,c);
                }catch(Exception ex){CatanCraft.LOGGER.error("R5 public journal unreadable; retain history for city "+c,ex);}
            }
        }catch(IOException notAuthorized){/* Not an authorized development world: no changes. */}
    }
}
