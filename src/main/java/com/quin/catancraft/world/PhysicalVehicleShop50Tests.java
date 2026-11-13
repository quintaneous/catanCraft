package com.quin.catancraft.world;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.quin.catancraft.command.NationProcurement;
import com.quin.catancraft.data.CatanSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

import static net.minecraft.commands.Commands.literal;

@GameTestHolder("catancraft_vehicle50_ui") @PrefixGameTestTemplate(false)
public final class PhysicalVehicleShop50Tests {
    @FunctionalInterface private interface Check { void run() throws Exception; }
    private static void refuse(Check work) throws Exception {
        try { work.run(); } catch (IOException | IllegalArgumentException expected) { return; }
        throw new IllegalStateException("Invalid factory selection was accepted");
    }
    @GameTest(template="townhall_state_empty",batch="vehicle50_ui_1",timeoutTicks=100)
    public static void session_tokens_and_expiry_cannot_be_reused(GameTestHelper h) throws Exception {
        UUID session=UUID.randomUUID(), vehicle=UUID.randomUUID();
        h.assertTrue(PhysicalVehicleShop50.withinLifetime(100,100)
                && PhysicalVehicleShop50.withinLifetime(100,1299),"Live session refused");
        for(long tick:new long[]{-1,99,1300,Long.MAX_VALUE})
            h.assertTrue(!PhysicalVehicleShop50.withinLifetime(100,tick),"Expired/reversed session accepted");
        h.assertTrue(!PhysicalVehicleShop50.withinLifetime(-1,0)
                && PhysicalVehicleShop50.withinLifetime(Long.MAX_VALUE-1199,Long.MAX_VALUE)
                && !PhysicalVehicleShop50.withinLifetime(Long.MAX_VALUE-1200,Long.MAX_VALUE),"Overflow session boundary failed");
        h.assertTrue(PhysicalVehicleShop50.selectedOffer(session,session+".pickup").equals("pickup"),"Valid purchase refused");
        h.assertTrue(PhysicalVehicleShop50.selectedRefuel(session,session+"."+vehicle+".fill")
                .equals(new PhysicalVehicleShop50.RefuelSelection(vehicle,VehicleFuel49.Mode.FILL)),"Valid fill refused");
        h.assertTrue(PhysicalVehicleShop50.selectedRefuel(session,session+"."+vehicle+".quarter").mode()==VehicleFuel49.Mode.ADD_QUARTER,"Valid quarter refused");
        for(String text:List.of(session+".pickup.extra",UUID.randomUUID()+".pickup",session+".unknown",session+".PICKUP"))
            refuse(()->PhysicalVehicleShop50.selectedOffer(session,text));
        for(String text:List.of(session+"."+vehicle+".FILL",session+"."+vehicle+".fill.extra",UUID.randomUUID()+"."+vehicle+".fill",session+".1-1-1-1-1.fill"))
            refuse(()->PhysicalVehicleShop50.selectedRefuel(session,text));
        refuse(()->PhysicalVehicleShop50.token("1-1-1-1-1"));
        h.succeed();
    }
    @GameTest(template="townhall_state_empty",batch="vehicle50_ui_2",timeoutTicks=100)
    public static void fuel_display_and_vehicle_pages_are_bounded(GameTestHelper h) throws Exception {
        h.assertTrue(PhysicalVehicleShop50.percent(1_250_000,5_000_000).equals("25.0%"),"Quarter display wrong");
        h.assertTrue(PhysicalVehicleShop50.percent(4_999_999,5_000_000).equals("99.9%"),"Nonfull tank rounded to full");
        h.assertTrue(PhysicalVehicleShop50.percent(Long.MAX_VALUE,Long.MAX_VALUE).equals("100.0%"),"Percentage overflow");
        h.assertTrue(PhysicalVehicleShop50.credit(1,5_000_000).equals("less than 0.0001"),"Small retained credit hidden");
        refuse(()->PhysicalVehicleShop50.percent(-1,5)); refuse(()->PhysicalVehicleShop50.percent(6,5));
        refuse(()->PhysicalVehicleShop50.credit(5,5)); refuse(()->PhysicalVehicleShop50.credit(0,0));
        List<Integer> all=new ArrayList<>();for(int n=0;n<14;n++)all.add(n);
        List<Integer> joined=new ArrayList<>();for(int p=0;p<3;p++)joined.addAll(PhysicalVehicleShop50.page(all,p));
        h.assertTrue(joined.equals(all),"Vehicle pagination duplicated or lost a choice");
        refuse(()->PhysicalVehicleShop50.page(all,Integer.MAX_VALUE));refuse(()->PhysicalVehicleShop50.page(all,-1));
        h.assertTrue(PhysicalVehicleShop50.page(List.of(),0).isEmpty(),"Empty page missing");
        UUID session=UUID.randomUUID();
        for(String value:List.of("-1","01","10000","1.2")) refuse(()->PhysicalVehicleShop50.selectedPage(session,session+"."+value));
        h.succeed();
    }
    @GameTest(template="townhall_state_empty",batch="vehicle50_ui_3",timeoutTicks=100)
    public static void public_commands_cannot_create_factory_authority(GameTestHelper h) throws Exception {
        var level=h.getLevel();var server=level.getServer();
        var root=server.getWorldPath(LevelResource.ROOT).toRealPath();
        h.assertTrue(server instanceof GameTestServer && server.getPlayerList().getPlayers().isEmpty()
                &&root.getParent().getFileName().toString().equals("controlled-catancraft_vehicle50_ui-run")
                &&Files.readString(root.getParent().resolve(".catancraft-controlled-fixture-v1")).equals("CatanCraft synthetic flat fixture v1\n"),"Foreign UI refusal fixture");
        FakePlayer player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"Vehicle50Remote"));
        player.setGameMode(GameType.SURVIVAL);player.setPos(0,140,0);
        var data=CatanSavedData.get(server);CompoundTag before=data.save(new CompoundTag());
        CommandDispatcher<CommandSourceStack> commands=new CommandDispatcher<>();
        commands.register(literal("nation").then(NationProcurement.node()));
        PhysicalVehicleShop50.stopped(server);
        UUID session=UUID.randomUUID(),vehicle=UUID.randomUUID();
        for(String command:List.of("nation procure pickup","nation procure lav150",
                "nation procure vehicle review "+session+".pickup",
                "nation procure vehicle refuel "+session+"."+vehicle+".fill",
                "nation procure vehicle confirm "+session,"nation procure vehicle cancel "+session,
                "nation procure vehicle menu "+session,"nation procure vehicle page "+session+".0",
                "nation procure vehicle recover "+session+"."+vehicle,"nation procure vehicle close "+session))
            h.assertTrue(commands.execute(command,player.createCommandSourceStack())==0,"Typed route minted factory authority: "+command);
        h.assertTrue(!PhysicalVehicleShop50.openCounter(player,new BlockPos(0,140,0)),"Unregistered block minted a session");
        h.assertTrue(PhysicalVehicleShop50.openCounter(player,VehicleFactory50.COUNTER),"Recognized factory did not consume refused FakePlayer interaction");
        h.assertTrue(PhysicalVehicleShop50.review(player.createCommandSourceStack(),"pickup")==0,"Refused counter click left a session");
        refuse(()->NationProcurement.legacyFixturePurchase(player,"pickup"));
        h.assertTrue(data.save(new CompoundTag()).equals(before),"Refused routes changed the nation ledger");
        PhysicalVehicleShop50.stopped(server);h.succeed();
    }
}
