package com.quin.catancraft.world;

import com.quin.catancraft.data.*;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import java.io.IOException;
import java.util.UUID;

/** In-memory authorization policy; no connected clients or multiplayer session. */
@GameTestHolder("catancraft_defense") @PrefixGameTestTemplate(false)
public final class DefenseCommandTests {
    @GameTest(template="townhall_state_empty",batch="defense_command_policy",timeoutTicks=200)
    public static void only_owning_leader_can_confirm_purchase(GameTestHelper h) {
        var data=new CatanSavedData();UUID leader=UUID.randomUUID(),member=UUID.randomUUID(),other=UUID.randomUUID();
        var nation=data.createNation("Defense command fixture",leader);nation.addMember(member);
        var foreign=data.createNation("Other fixture",other);var city=data.createTerritory("a","Fixture",ResourceType.WOOD);city.setOwnerNationId(nation.id());
        try {
            h.assertTrue(DefenseCommands.requireLeaderOwner(data,leader,"a")==nation,"Owning leader refused");
            for(UUID actor:new UUID[]{member,other,UUID.randomUUID()}) {
                boolean refused=false;try{DefenseCommands.requireLeaderOwner(data,actor,"a");}catch(IOException expected){refused=true;}
                h.assertTrue(refused,"Non-owner/nonleader could confirm purchase");
            }
            city.setOwnerNationId(foreign.id());boolean refused=false;
            try{DefenseCommands.requireLeaderOwner(data,leader,"a");}catch(IOException expected){refused=true;}
            h.assertTrue(refused,"Previous owner retained purchase authority");h.succeed();
        }catch(IOException ex){throw new RuntimeException(ex);}
    }
}
