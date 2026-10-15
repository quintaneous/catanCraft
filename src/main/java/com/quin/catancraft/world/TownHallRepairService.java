package com.quin.catancraft.world;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.*;
import com.quin.catancraft.map.*;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Explicit operator maintenance only. Never repairs a world at startup. */
@Mod.EventBusSubscriber(modid=CatanCraft.MOD_ID)
public final class TownHallRepairService {
    private TownHallRepairService() {}
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        var root=Commands.literal("townhall").requires(s->s.hasPermission(2));
        root.then(Commands.literal("status").then(Commands.argument("city",StringArgumentType.word())
            .executes(c->execute(c.getSource(),StringArgumentType.getString(c,"city"),TownHallRepairTransaction.Action.STATUS))));
        for(var action:new TownHallRepairTransaction.Action[]{
            TownHallRepairTransaction.Action.APPLY,TownHallRepairTransaction.Action.RESUME,TownHallRepairTransaction.Action.ROLLBACK}) {
            String name=action==TownHallRepairTransaction.Action.APPLY?"repair":action.name().toLowerCase(Locale.ROOT);
            root.then(Commands.literal(name).then(Commands.argument("city",StringArgumentType.word())
                .then(Commands.literal("confirm").executes(c->execute(c.getSource(),
                    StringArgumentType.getString(c,"city"),action)))));
        }
        event.getDispatcher().register(Commands.literal("catan").then(root));
    }
    private static synchronized int execute(CommandSourceStack source,String city,TownHallRepairTransaction.Action action) {
        try {
            var context=context(source.getServer(),city);
            var result=TownHallRepairTransaction.run(context,action);
            source.sendSuccess(()->Component.literal(result.message()),true);
            CatanCraft.LOGGER.info("TH_R2 city={} action={} writes={} {}",city,action,result.writes(),result.message());
            return 1;
        } catch(Exception e) {
            CatanCraft.LOGGER.error("TH_R2 refusal city={} action={}",city,action,e);
            source.sendFailure(Component.literal("Town Hall repair refused: "+e.getMessage()+
                ". No charge. Keep blocks/journals unchanged; use status before recovery."));
            return 0;
        }
    }
    static Path authorize(MinecraftServer server) throws IOException {
        if(!server.isSameThread())throw new IOException("Town Hall changes require the server thread");
        Path world=server.getWorldPath(LevelResource.ROOT).toRealPath();
        if(!world.getFileName().toString().equals("CatanCraft_Season1_R7_Development")
            || !server.getWorldData().getLevelName().equals("CatanCraft Environment V4 - Visual Polish R2"))
            throw new IOException("Only the existing backed-up R7 test world is authorized");
        Path marker=world.resolve(R5CivicAutomaticInstaller.MARKER_NAME);
        if(!Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS) || Files.size(marker)>250
            || !Files.readString(marker).trim().equals(R5CivicAutomaticInstaller.MARKER_VALUE))
            throw new IOException("Missing R7 test-world authorization");
        return world;
    }
    public static void requireNoPending(MinecraftServer server,String city) throws IOException {
        Path world=authorize(server);
        if(!city.matches("[a-p]"))throw new IOException("Unknown city");
        for(int tier:new int[]{4,5}) {
            Path p=path(world,city,tier);
            if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))continue;
            CompoundTag n=TownHallRepairTransaction.read(p);
            CompoundTag id=n.getCompound("identity");
            if(!id.getString("world").equals(world.toString()) || !id.getString("city").equals(city)
                || id.getInt("tier")!=tier)throw new IOException("Foreign repair history");
            var phase=TownHallRepairTransaction.phase(n);
            if(phase!=TownHallRepairTransaction.Phase.COMMITTED && phase!=TownHallRepairTransaction.Phase.ROLLED_BACK)
                throw new IOException("An incomplete TH"+tier+" repair blocks purchases; finish resume/rollback first");
        }
    }
    private static Path path(Path world,String city,int tier) {
        return world.resolve("data").resolve("catancraft_th_repair_"+city+"_"+tier+"_r1_r2.dat");
    }
    private static TownHallRepairTransaction.Environment context(MinecraftServer server,String input) throws IOException {
        Path world=authorize(server);
        String city=input.toLowerCase(Locale.ROOT);
        if(!city.matches("[a-p]"))throw new IOException("Unknown city");
        CatanSavedData data=CatanSavedData.get(server);
        TerritoryData territory=data.territory(city);
        if(territory==null || (territory.cityLevel()!=4 && territory.cityLevel()!=5)
            || territory.ownerNationId()==null || data.nation(territory.ownerNationId())==null)
            throw new IOException("A purchased TH4 or TH5 is required");
        TerritoryDefinition def=MapDefinitionManager.territory(city);
        if(def==null || def.cityCenter()==null || def.siegeRegion()==null || def.restorationRegion()==null)
            throw new IOException("Missing protected city geometry");
        MapAnchor anchor=SchematicAssetRegistry.territoryAnchor("th"+territory.cityLevel(),def);
        if(anchor==null || anchor.y()!=81 || (anchor.rotation()!=0 && anchor.rotation()!=180))
            throw new IOException("Invalid city anchor");
        ServerLevel found=null;
        for(ServerLevel l:server.getAllLevels())if(l.dimension().location().toString().equals(def.dimension()))found=l;
        if(found==null)throw new IOException("Unloaded dimension");
        final ServerLevel level=found;
        var plan=TownHallRevisions.plan(territory.cityLevel(),1,anchor);
        for(var c:plan)if(!def.siegeRegion().contains(c.position()) || !def.restorationRegion().contains(c.position()))
            throw new IOException("Hall outside protected city");
        String key="territory:"+city+":town_hall";
        int tier=territory.cityLevel();
        // Block an unfinished purchase even if its old visual token still looks valid.
        purchaseHistory(world,city,territory);
        return new TownHallRepairTransaction.Environment() {
            public ServerLevel level(){return level;}
            public MapAnchor anchor(){return anchor;}
            public int tier(){return tier;}
            public Path file(){return path(world,city,tier);}
            public CompoundTag identity(){
                CompoundTag n=new CompoundTag();
                n.putString("world",world.toString());n.putString("dimension",def.dimension());n.putString("city",city);
                n.putString("owner",String.valueOf(territory.ownerNationId()));n.putInt("tier",territory.cityLevel());
                n.putIntArray("anchor",new int[]{anchor.x(),anchor.y(),anchor.z(),anchor.rotation()});return n;
            }
            public CompoundTag history() throws IOException {return purchaseHistory(world,city,territory);}
            public CompoundTag ledger(){
                CompoundTag n=data.save(new CompoundTag());
                n.getCompound("placedMapAssets").putString(key,"NORMALIZED_CURRENT_HALL_ONLY");return n;
            }
            public String token(){return data.placedMapAsset(key);}
            public void token(String value){data.setPlacedMapAsset(key,value);}
            public void save() throws IOException {
                if(!server.saveEverything(true,true,true))throw new IOException("Server save did not complete");
            }
        };
    }
    private static CompoundTag purchaseHistory(Path world,String city,TerritoryData t) throws IOException {
        CompoundTag result=new CompoundTag();
        for(int tier:new int[]{4,5}) {
            Path p=world.resolve("data/catancraft_th_prestige_"+city+"_"+tier+"_v1.dat");
            boolean exists=Files.exists(p,LinkOption.NOFOLLOW_LINKS);
            if(!exists) {
                if(tier<=t.cityLevel())throw new IOException("Missing original TH"+tier+" purchase journal; cannot prove paid history");
                result.putString("tier"+tier,"ABSENT");continue;
            }
            if(Files.isSymbolicLink(p) || !Files.isRegularFile(p) || Files.size(p)>32*1024*1024)
                throw new IOException("Invalid purchase journal");
            byte[] bytes=Files.readAllBytes(p);
            CompoundTag n=NbtIo.readCompressed(new ByteArrayInputStream(bytes));
            if(tier>t.cityLevel() || !n.getString("phase").equals("COMMITTED")
                || !n.getString("city").equals(city) || !n.getString("worldRoot").equals(world.toString())
                || n.getInt("toLevel")!=tier || !n.getString("ownerNation").equals(String.valueOf(t.ownerNationId())))
                throw new IOException("Incomplete or foreign TH"+tier+" purchase journal; repair cannot reconcile purchases");
            result.putString("tier"+tier,TownHallRevisions.sha(bytes));
        }
        return result;
    }
}
