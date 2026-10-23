package com.quin.catancraft.economy;

import com.quin.catancraft.data.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.*;
import java.nio.file.*;
import java.util.*;

@GameTestHolder("catancraft_economy_clock") @PrefixGameTestTemplate(false)
public final class EconomyClockTests {
    private static final long C=EconomyBalance.CYCLE_TICKS;
    private static CatanSavedData sample() {
        CatanSavedData data=new CatanSavedData();var n=data.createNation("clock",UUID.randomUUID());
        data.createTerritory("a","clock",ResourceType.AGRICULTURE).setOwnerNationId(n.id());return data;
    }
    private static long money(CatanSavedData d){return d.nations().iterator().next().treasury();}
    private static CatanSavedData reload(CatanSavedData d){return CatanSavedData.load(d.save(new CompoundTag()));}
    private static void same(GameTestHelper h,CompoundTag a,CatanSavedData d,String why){h.assertTrue(a.equals(d.save(new CompoundTag())),why);}
    @GameTest(template="townhall_state_empty") public static void old_save_adoption_never_mints(GameTestHelper h) {
        var d=sample();long money=money(d);var result=EconomyEngine.scheduledCycle(d,4*C+20);
        h.assertTrue(result==EconomyEngine.ScheduledResult.INITIALIZED&&money(d)==money&&d.lastScheduledEconomyCycle()==4,"Migration replayed old interval");
        h.succeed();
    }
    @GameTest(template="townhall_state_empty") public static void next_boundary_posts_once(GameTestHelper h) {
        var d=sample();EconomyEngine.scheduledCycle(d,0);long money=money(d);
        h.assertTrue(EconomyEngine.scheduledCycle(d,C)==EconomyEngine.ScheduledResult.POSTED,"Boundary skipped");
        h.assertTrue(money(d)==money+EconomyBalance.BASE_TERRITORY_INCOME_PER_CYCLE,"Wrong income");
        CompoundTag after=d.save(new CompoundTag());EconomyEngine.scheduledCycle(d,C+99);same(h,after,d,"Same cycle paid twice");h.succeed();
    }
    @GameTest(template="townhall_state_empty") public static void saved_data_reload_same_cycle_does_not_repay(GameTestHelper h) {
        var d=sample();EconomyEngine.scheduledCycle(d,C-1);EconomyEngine.scheduledCycle(d,C+5);CompoundTag after=d.save(new CompoundTag());
        d=reload(d);EconomyEngine.scheduledCycle(d,C+5);same(h,after,d,"Reload replayed production");
        EconomyEngine.scheduledCycle(d,2*C);h.assertTrue(d.lastScheduledEconomyCycle()==2,"Next boundary did not resume");h.succeed();
    }
    @GameTest(template="townhall_state_empty") public static void native_compressed_disk_roundtrip_keeps_balances_and_cursor_together(GameTestHelper h)throws Exception {
        var d=sample();EconomyEngine.scheduledCycle(d,0);EconomyEngine.scheduledCycle(d,C);CompoundTag after=d.save(new CompoundTag());
        Path file=h.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("data/clock-tests/"+UUID.randomUUID()+".dat");Files.createDirectories(file.getParent());
        CompoundTag envelope=new CompoundTag();envelope.put("data",after);NbtIo.writeCompressed(envelope,file.toFile());
        d=CatanSavedData.load(NbtIo.readCompressed(file.toFile()).getCompound("data"));EconomyEngine.scheduledCycle(d,C+100);same(h,after,d,"Disk reload replayed or lost balances");h.succeed();
    }
    @GameTest(template="townhall_state_empty") public static void rewound_time_cannot_replay(GameTestHelper h) {
        var d=sample();EconomyEngine.scheduledCycle(d,0);EconomyEngine.scheduledCycle(d,8*C);var after=d.save(new CompoundTag());
        for(long t:new long[]{0,C,7*C,8*C+1})EconomyEngine.scheduledCycle(d,t);
        same(h,after,d,"Time rewind replayed high-water-mark cycle");h.succeed();
    }
    @GameTest(template="townhall_state_empty") public static void separate_world_ledgers_have_independent_cursors(GameTestHelper h) {
        var a=sample();var b=sample();EconomyEngine.scheduledCycle(a,0);EconomyEngine.scheduledCycle(b,0);
        EconomyEngine.scheduledCycle(a,C);h.assertTrue(EconomyEngine.scheduledCycle(b,C)==EconomyEngine.ScheduledResult.POSTED,"One server/world suppressed another");h.succeed();
    }
    @GameTest(template="townhall_state_empty") public static void skipped_intervals_do_not_mint_backlog(GameTestHelper h) {
        var d=sample();EconomyEngine.scheduledCycle(d,0);long initial=money(d);EconomyEngine.scheduledCycle(d,100*C);
        h.assertTrue(money(d)==initial+EconomyBalance.BASE_TERRITORY_INCOME_PER_CYCLE,"Catch-up windfall");h.succeed();
    }
    @GameTest(template="townhall_state_empty") public static void unknown_checkpoint_fails_closed_and_is_preserved(GameTestHelper h) {
        for(Tag receipt:List.of(StringTag.valueOf("bad"),new CompoundTag(),LongTag.valueOf(-1))) {
            var tag=sample().save(new CompoundTag());tag.put("economySchedule",receipt.copy());var d=CatanSavedData.load(tag);var before=d.save(new CompoundTag());
            boolean refused=false;try{EconomyEngine.scheduledCycle(d,3*C);}catch(IllegalStateException expected){refused=true;}
            h.assertTrue(refused,"Unknown checkpoint reset");same(h,before,d,"Unknown receipt/balances overwritten");
        }h.succeed();
    }
    @GameTest(template="townhall_state_empty") public static void cursor_never_moves_backwards_or_accepts_negative_time(GameTestHelper h) {
        var d=sample();EconomyEngine.scheduledCycle(d,2*C);var before=d.save(new CompoundTag());
        boolean backwards=false,negative=false;
        try{d.checkpointEconomyCycle(1);}catch(IllegalArgumentException expected){backwards=true;}
        try{EconomyEngine.scheduledCycle(d,-1);}catch(IllegalArgumentException expected){negative=true;}
        h.assertTrue(backwards&&negative,"Invalid clock accepted");same(h,before,d,"Invalid time mutated data");h.succeed();
    }
}
