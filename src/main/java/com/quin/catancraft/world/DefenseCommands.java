package com.quin.catancraft.world;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.*;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.io.IOException;
import java.util.*;

/** Compass-sector player interface. Historical module purchases retain recovery only. */
@Mod.EventBusSubscriber(modid=CatanCraft.MOD_ID)
public final class DefenseCommands {
    private DefenseCommands(){}
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("nation").then(tree()));
    }
    static LiteralArgumentBuilder<CommandSourceStack> tree() {
        var defense=Commands.literal("defense");
        DefenseMenuCommands.addRoutes(defense);
        defense.then(Commands.literal("list").then(Commands.argument("city",StringArgumentType.word())
            .executes(c->list(c.getSource(),StringArgumentType.getString(c,"city")))));
        for(String verb:List.of("quote","buy","status","resume","rollback")) {
            var city=Commands.argument("city",StringArgumentType.word());
            for(String direction:List.of("north","east","south","west")) {
                var side=Commands.literal(direction);
                if(verb.equals("quote")||verb.equals("status"))side.executes(c->sector(c.getSource(),verb,StringArgumentType.getString(c,"city"),direction));
                else side.then(Commands.literal("confirm").executes(c->sector(c.getSource(),verb,StringArgumentType.getString(c,"city"),direction)));
                city.then(side);
            }
            defense.then(Commands.literal(verb).then(city));
        }
        var legacy=Commands.literal("legacy");
        for(String verb:List.of("status","resume","rollback")) {
            var socket=Commands.argument("socket",StringArgumentType.word());
            if(verb.equals("status"))socket.executes(c->legacy(c.getSource(),verb,StringArgumentType.getString(c,"city"),StringArgumentType.getString(c,"socket")));
            else socket.then(Commands.literal("confirm").executes(c->legacy(c.getSource(),verb,StringArgumentType.getString(c,"city"),StringArgumentType.getString(c,"socket"))));
            legacy.then(Commands.literal(verb).then(Commands.argument("city",StringArgumentType.word()).then(socket)));
        }
        return defense.then(legacy);
    }
    static NationData requireLeaderOwner(CatanSavedData data,UUID actor,String city)throws IOException {
        TerritoryData t=data.territory(city);NationData n=data.nationForPlayer(actor);
        if(t==null||n==null||!n.isLeader(actor)||!n.id().equals(t.ownerNationId()))
            throw new IOException("Only the owning nation's leader can purchase or recover this defense");
        return n;
    }
    private static String ownedCity(CommandSourceStack source,String city)throws Exception {
        String id=city.toLowerCase(Locale.ROOT);
        if(!id.matches("[a-p]"))throw new IOException("Unknown defense city");
        requireLeaderOwner(CatanSavedData.get(source.getServer()),source.getPlayerOrException().getUUID(),id);
        return id;
    }
    static final String UNAVAILABLE="Sector purchases require a validated isolated authored-demo package. City A P2/P3 appearance and footprints are approved; runtime validation is separate. Test fixtures are not approved demo worlds.";
    private static int list(CommandSourceStack source,String city) {
        try {
            String id=ownedCity(source,city);
            source.sendSuccess(()->Component.literal("Defense sectors for "+id+": North (-Z), East (+X), South (+Z), West (-X). One quoted price and saved level per side. Historical recovery: /nation defense legacy status|resume|rollback <city> <socket> [confirm]."),false);
            for(String direction:List.of("north","east","south","west")) {
                var result=SectorService.operation(source.getServer(),source.getPlayerOrException().getUUID(),id,direction,SectorTransaction.Action.STATUS);
                source.sendSuccess(()->Component.literal(direction.toUpperCase(Locale.ROOT)+": "+result.message()),false);
            }
            return 1;
        }catch(Exception ex){source.sendFailure(Component.literal(ex.getMessage()));return 0;}
    }
    private static int sector(CommandSourceStack source,String verb,String city,String direction) {
        try {
            city=ownedCity(source,city);
            var actor=source.getPlayerOrException().getUUID();
            var action=switch(verb){case "buy"->SectorTransaction.Action.APPLY;case "resume"->SectorTransaction.Action.RESUME;case "rollback"->SectorTransaction.Action.ROLLBACK;default->SectorTransaction.Action.STATUS;};
            var result=verb.equals("quote")?SectorService.quote(source.getServer(),actor,city,direction):SectorService.operation(source.getServer(),actor,city,direction,action);
            if(!result.success()){source.sendFailure(Component.literal(result.message()));return 0;}
            source.sendSuccess(()->Component.literal(result.message()),!verb.equals("status")&&!verb.equals("quote"));return 1;
        }catch(Exception ex){source.sendFailure(Component.literal("Defense request refused: "+ex.getMessage()));return 0;}
    }
    private static int legacy(CommandSourceStack source,String verb,String city,String socket) {
        try {
            city=ownedCity(source,city);socket=socket.toLowerCase(Locale.ROOT);
            var action=switch(verb){case "resume"->DefenseTransaction.Action.RESUME;case "rollback"->DefenseTransaction.Action.ROLLBACK;default->DefenseTransaction.Action.STATUS;};
            var result=DefenseService.operation(source.getServer(),city,socket,action,null);
            if(!result.success()){source.sendFailure(Component.literal(result.message()));return 0;}
            source.sendSuccess(()->Component.literal(result.message()),true);return 1;
        }catch(Exception ex){source.sendFailure(Component.literal("Historical defense recovery refused: "+ex.getMessage()));return 0;}
    }
}
