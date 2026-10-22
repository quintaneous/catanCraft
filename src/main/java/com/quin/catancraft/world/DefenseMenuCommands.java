package com.quin.catancraft.world;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.network.NationNetwork;
import com.quin.catancraft.ui.DefenseMenuRows;
import com.quin.catancraft.ui.NationDashboard;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import java.util.List;

/** GUI routes use normal authenticated commands and the existing sector transaction service. */
final class DefenseMenuCommands {
    private DefenseMenuCommands() {}
    static void addRoutes(LiteralArgumentBuilder<CommandSourceStack> defense) {
        for(String action:List.of("review","confirm","cancel")) {
            var city=Commands.argument("city",StringArgumentType.word());
            for(String direction:List.of("north","east","south","west")) {
                var side=Commands.literal(direction);
                if(action.equals("review"))side.executes(c->execute(c.getSource(),action,StringArgumentType.getString(c,"city"),direction,""));
                else side.then(Commands.argument("quoteId",StringArgumentType.word()).executes(c->execute(c.getSource(),action,StringArgumentType.getString(c,"city"),direction,StringArgumentType.getString(c,"quoteId"))));
                city.then(side);
            }
            defense.then(Commands.literal(action).then(city));
        }
    }
    private static int execute(CommandSourceStack source,String action,String city,String side,String token) {
        try {
            var player=source.getPlayerOrException();
            if(action.equals("review")) {
                var quote=SectorService.menuQuote(source.getServer(),player.getUUID(),city,side);
                var territory=CatanSavedData.get(source.getServer()).territory(quote.city());
                NationNetwork.openDashboard(player,DefenseMenuRows.confirmation(territory.name(),quote));
                return 1;
            }
            var result=action.equals("confirm")
                    ?SectorService.confirmMenuQuote(source.getServer(),player.getUUID(),city,side,token)
                    :SectorService.cancelMenuQuote(source.getServer(),player.getUUID(),city,side,token);
            if(result.success())source.sendSuccess(()->Component.literal(result.message()),false);
            else source.sendFailure(Component.literal(result.message()));
            NationDashboard.open(player,result.message());
            return result.success()?1:0;
        } catch(Exception error) {
            String message="Defense request refused: "+DefenseMenuRows.safe(error.getMessage());
            source.sendFailure(Component.literal(message));
            try {NationDashboard.open(source.getPlayerOrException(),message);}catch(Exception ignored) {}
            return 0;
        }
    }
}
