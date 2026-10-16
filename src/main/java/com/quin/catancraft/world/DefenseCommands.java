package com.quin.catancraft.world;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.*;
import com.quin.catancraft.economy.EconomyCost;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.io.IOException;
import java.util.*;

/** Leader-owned explicit module purchases. A displayed, current quote precedes confirmation. */
@Mod.EventBusSubscriber(modid=CatanCraft.MOD_ID)
public final class DefenseCommands {
    private DefenseCommands(){}
    private record Key(UUID actor,String city,String socket){}
    private record Quote(UUID owner,long tick,String module,net.minecraft.nbt.CompoundTag cost){}
    private static final Map<Key,Quote> QUOTES=new HashMap<>();
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        var defense=Commands.literal("defense");
        defense.then(Commands.literal("list").then(Commands.argument("city",StringArgumentType.word()).executes(c->list(c.getSource(),StringArgumentType.getString(c,"city")))));
        for(String verb:List.of("quote","buy","status","resume","rollback")) {
            var socket=Commands.argument("socket",StringArgumentType.word());
            if(verb.equals("quote")||verb.equals("status"))socket.executes(c->execute(c.getSource(),verb,StringArgumentType.getString(c,"city"),StringArgumentType.getString(c,"socket")));
            else socket.then(Commands.literal("confirm").executes(c->execute(c.getSource(),verb,StringArgumentType.getString(c,"city"),StringArgumentType.getString(c,"socket"))));
            defense.then(Commands.literal(verb).then(Commands.argument("city",StringArgumentType.word()).then(socket)));
        }
        event.getDispatcher().register(Commands.literal("nation").then(defense));
    }
    static NationData requireLeaderOwner(CatanSavedData data,UUID actor,String city)throws IOException {
        TerritoryData t=data.territory(city);NationData n=data.nationForPlayer(actor);
        if(t==null||n==null||!n.isLeader(actor)||!n.id().equals(t.ownerNationId()))
            throw new IOException("Only the owning nation's leader can purchase or recover this defense");
        return n;
    }
    private static int list(CommandSourceStack source,String city) {
        try {
            String id=city.toLowerCase(Locale.ROOT);List<String> sockets=new ArrayList<>();
            for(var plan:DefenseRevisions.city(id)){DefenseCostCatalog.cost(plan.module());sockets.add(plan.socket());}
            source.sendSuccess(()->Component.literal("Available compatible defense sockets for "+id+": "+String.join(", ",sockets)+". Use /nation defense quote "+id+" <socket> before buying."),false);return 1;
        }catch(Exception ex){source.sendFailure(Component.literal(ex.getMessage()));return 0;}
    }
    private static int execute(CommandSourceStack source,String verb,String city,String socket) {
        try {
            city=city.toLowerCase(Locale.ROOT);socket=socket.toLowerCase(Locale.ROOT);
            ServerPlayer player=source.getPlayerOrException();var data=CatanSavedData.get(source.getServer());
            NationData owner=requireLeaderOwner(data,player.getUUID(),city);var plan=DefenseRevisions.plan(city,socket);
            Key key=new Key(player.getUUID(),city,socket);long tick=source.getServer().getTickCount();
            EconomyCost price=DefenseCostCatalog.cost(plan.module());
            if(verb.equals("quote")) {
                QUOTES.entrySet().removeIf(e->tick-e.getValue().tick()>1200);
                QUOTES.put(key,new Quote(owner.id(),tick,plan.module(),DefenseTransaction.cost(price)));
                String text="Provisional defense price for "+city+"/"+socket+" ("+plan.module()+"): "+price.describe()+
                    ". One module; footing/support cells included. Paid from your nation's treasury/stockpile, never player inventory. Confirm within 60 seconds: /nation defense buy "+city+" "+socket+" confirm";
                source.sendSuccess(()->Component.literal(text),false);return 1;
            }
            DefenseTransaction.Action action=switch(verb){case "buy"->DefenseTransaction.Action.APPLY;case "resume"->DefenseTransaction.Action.RESUME;case "rollback"->DefenseTransaction.Action.ROLLBACK;default->DefenseTransaction.Action.STATUS;};
            if(action==DefenseTransaction.Action.APPLY) {
                Quote quote=QUOTES.remove(key);
                if(quote==null||!quote.owner().equals(owner.id())||tick-quote.tick()>1200||tick<quote.tick()
                    ||!quote.module().equals(plan.module())||!quote.cost().equals(DefenseTransaction.cost(price)))
                    throw new IOException("Display a current price first: /nation defense quote "+city+" "+socket);
            }
            var result=DefenseService.operation(source.getServer(),city,socket,action,null);
            if(!result.success()){source.sendFailure(Component.literal(result.message()));return 0;}
            source.sendSuccess(()->Component.literal(result.message()),true);return 1;
        }catch(Exception ex){source.sendFailure(Component.literal("Defense request refused: "+ex.getMessage()));return 0;}
    }
}
