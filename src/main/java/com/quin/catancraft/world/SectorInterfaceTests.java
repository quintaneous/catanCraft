package com.quin.catancraft.world;

import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import java.util.Set;
import java.util.stream.Collectors;

/** Command registration test without connected clients or a multiplayer session. */
@GameTestHolder("catancraft_sectors") @PrefixGameTestTemplate(false)
public final class SectorInterfaceTests {
    @GameTest(template="townhall_state_empty",batch="sector_interface",timeoutTicks=200)
    public static void compass_shopping_and_retained_legacy_recovery(GameTestHelper h) {
        var root=DefenseCommands.tree().build();
        for(String verb:Set.of("quote","buy","status","resume","rollback")) {
            var city=root.getChild(verb).getChild("city");
            h.assertTrue(city.getChildren().stream().map(n->n.getName()).collect(Collectors.toSet()).equals(Set.of("north","east","south","west")),"Shopping exposes individual modules or omits compass side");
            if(Set.of("buy","resume","rollback").contains(verb))for(var side:city.getChildren())
                h.assertTrue(side.getCommand()==null&&side.getChild("confirm").getCommand()!=null,"Mutation missing explicit confirmation");
        }
        var legacy=root.getChild("legacy");
        h.assertTrue(legacy.getChildren().stream().map(n->n.getName()).collect(Collectors.toSet()).equals(Set.of("status","resume","rollback")),"Legacy purchases exposed or recovery removed");
        h.assertTrue(DefenseCommands.UNAVAILABLE.contains("not approved"),"Fixture caveat missing");h.succeed();
    }
}
