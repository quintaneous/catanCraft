package com.quin.catancraft.world;

import com.quin.catancraft.world.SiegeRules36.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Rules-only fixtures with synthetic tick durations, never live gameplay defaults.
 * No actors, city blocks, inventories, ownership transfers or siege damage are staged.
 * The disk case tests compressed NBT readback, not a separate-process restart service.
 */
@GameTestHolder("catancraft_siege36") @PrefixGameTestTemplate(false)
public final class SiegeRules36Tests {
    private SiegeRules36Tests() {}

    @GameTest(template="townhall_state_empty")
    public static void both_nations_must_be_online_and_distinct_to_start(GameTestHelper h)throws Exception {
        Contract c=contract(20,2,3,EmptyPolicy.HOLD);
        refuse(()->SiegeRules36.start(c,false,false));
        refuse(()->SiegeRules36.start(c,true,false));
        refuse(()->SiegeRules36.start(c,false,true));
        refuse(()->new Contract(c.siege(),c.world(),c.city(),c.attackers(),c.attackers(),c.timing()));
        Snapshot s=SiegeRules36.start(c,true,true);
        h.assertTrue(s.status()==Status.OUTER&&s.elapsedTicks()==0&&s.outerProgress()==0
            &&s.townHallProgress()==0&&s.remainingTicks()==20&&!s.terminal()
            &&s.winnerNationId().isEmpty(),"Starting eligibility or initial siege state is wrong");
        h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void attackers_complete_outer_before_town_hall_and_win(GameTestHelper h) {
        Contract c=contract(20,2,3,EmptyPolicy.HOLD);Snapshot initial=SiegeRules36.start(c,true,true);
        Snapshot s=advance(initial,true,false);
        h.assertTrue(s.status()==Status.OUTER&&s.outerProgress()==1&&s.townHallProgress()==0,"Town Hall advanced before outer capture");
        s=advance(s,true,false);
        h.assertTrue(s.status()==Status.TOWN_HALL&&s.outerProgress()==2&&s.townHallProgress()==0
            &&s.elapsedTicks()==2,"One observation captured two stages or lost the clock");
        s=advance(s,true,false);s=advance(s,true,false);
        h.assertTrue(s.status()==Status.TOWN_HALL&&s.townHallProgress()==2&&!s.terminal(),"Attacker won before the complete Town Hall hold");
        s=advance(s,true,false);
        h.assertTrue(s.status()==Status.ATTACKER_WON&&s.terminal()&&s.elapsedTicks()==5
            &&s.remainingTicks()==15&&s.winnerNationId().orElseThrow().equals(c.attackers()),"Completed two-stage objective did not award attacker victory");
        h.assertTrue(initial.elapsedTicks()==0&&initial.outerProgress()==0,"Advancing mutated the caller's earlier snapshot");
        h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void defenders_win_when_outer_point_never_falls(GameTestHelper h) {
        Contract c=contract(8,2,3,EmptyPolicy.HOLD);Snapshot s=SiegeRules36.start(c,true,true);
        for(int tick=0;tick<7;tick++)s=advance(s,true,true);
        h.assertTrue(!s.terminal()&&s.remainingTicks()==1,"Defender won before expiry");
        s=advance(s,true,true);
        h.assertTrue(s.status()==Status.DEFENDER_WON_TIMEOUT&&s.elapsedTicks()==8&&s.remainingTicks()==0
            &&s.outerProgress()==0&&s.townHallProgress()==0&&s.winnerNationId().orElseThrow().equals(c.defenders()),"Contesting outer point did not end in defender victory at expiry");
        h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void defenders_can_lose_outer_and_still_win_at_town_hall(GameTestHelper h) {
        Contract c=contract(10,2,3,EmptyPolicy.HOLD);Snapshot s=SiegeRules36.start(c,true,true);
        s=advance(s,true,false);s=advance(s,true,false);s=advance(s,true,false);
        h.assertTrue(s.status()==Status.TOWN_HALL&&s.townHallProgress()==1,"Missing partially captured Town Hall fixture");
        while(!s.terminal())s=advance(s,true,true);
        h.assertTrue(s.status()==Status.DEFENDER_WON_TIMEOUT&&s.elapsedTicks()==10&&s.outerProgress()==2
            &&s.townHallProgress()==1&&s.winnerNationId().orElseThrow().equals(c.defenders()),"Losing outer point prevented a later defender win");
        h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void defender_presence_pauses_capture_but_not_overall_clock(GameTestHelper h) {
        Snapshot s=SiegeRules36.start(contract(20,2,3,EmptyPolicy.HOLD),true,true);
        s=advance(s,true,false);
        for(int i=0;i<3;i++)s=advance(s,true,true);
        h.assertTrue(s.elapsedTicks()==4&&s.remainingTicks()==16&&s.outerProgress()==1,"Contested outer point froze the siege clock or advanced capture");
        s=advance(s,true,false);s=advance(s,true,false);
        s=advance(s,false,true);s=advance(s,true,true);
        h.assertTrue(s.status()==Status.TOWN_HALL&&s.elapsedTicks()==8&&s.outerProgress()==2
            &&s.townHallProgress()==1,"Defender presence did not pause Town Hall capture while time elapsed");
        h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void outer_capture_never_resets_or_extends_attack_deadline(GameTestHelper h) {
        Snapshot s=SiegeRules36.start(contract(12,2,3,EmptyPolicy.HOLD),true,true);
        for(int i=0;i<7;i++)s=advance(s,true,true);
        s=advance(s,true,false);s=advance(s,true,false);
        h.assertTrue(s.status()==Status.TOWN_HALL&&s.elapsedTicks()==9&&s.remainingTicks()==3,"Outer completion restarted or extended siege time");
        s=advance(s,true,false);s=advance(s,true,false);s=advance(s,true,false);
        h.assertTrue(s.status()==Status.DEFENDER_WON_TIMEOUT&&s.elapsedTicks()==12
            &&s.townHallProgress()==2,"Late outer capture gave attackers an extra timer or overtime");
        h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void only_attacker_nation_can_surrender_and_either_stage_can_end(GameTestHelper h)throws Exception {
        Contract c=contract(20,2,3,EmptyPolicy.HOLD);
        Snapshot outer=SiegeRules36.start(c,true,true);Snapshot hall=advance(advance(outer,true,false),true,false);
        for(Snapshot before:List.of(outer,hall)) {
            CompoundTag unchanged=SiegeRules36.save(before);
            refuse(()->SiegeRules36.surrender(before,c.defenders()));
            refuse(()->SiegeRules36.surrender(before,UUID.randomUUID()));
            h.assertTrue(unchanged.equals(SiegeRules36.save(before)),"Foreign surrender changed the siege");
            Snapshot surrendered=SiegeRules36.surrender(before,c.attackers());
            h.assertTrue(surrendered.status()==Status.DEFENDER_WON_SURRENDER&&surrendered.terminal()
                &&surrendered.elapsedTicks()==before.elapsedTicks()&&surrendered.outerProgress()==before.outerProgress()
                &&surrendered.townHallProgress()==before.townHallProgress()
                &&surrendered.winnerNationId().orElseThrow().equals(c.defenders()),"Authorized nation surrender changed progress or failed to award defender victory");
        }
        h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void terminal_results_never_advance_or_change_winner(GameTestHelper h)throws Exception {
        Contract c=contract(8,2,3,EmptyPolicy.HOLD);
        Snapshot won=SiegeRules36.start(c,true,true);for(int i=0;i<5;i++)won=advance(won,true,false);
        Snapshot timeout=SiegeRules36.start(c,true,true);for(int i=0;i<8;i++)timeout=advance(timeout,true,true);
        Snapshot surrendered=SiegeRules36.surrender(SiegeRules36.start(c,true,true),c.attackers());
        for(Snapshot end:List.of(won,timeout,surrendered)) {
            CompoundTag before=SiegeRules36.save(end);
            Snapshot ticked=SiegeRules36.tick(end,end.elapsedTicks(),new Observation(Stage.OUTER,true,false));
            Snapshot repeated=SiegeRules36.surrender(ticked,c.attackers());
            h.assertTrue(end==ticked&&end==repeated&&before.equals(SiegeRules36.save(repeated)),"Terminal action replay changed time, progress or winner");
            h.assertTrue(SiegeRules36.load(before,c).equals(end),"Terminal result codec lost its winner");
            refuse(end::activeStage);
        }
        h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void stale_or_skipped_tick_cursor_cannot_mint_capture_progress(GameTestHelper h)throws Exception {
        Snapshot s=advance(SiegeRules36.start(contract(20,2,3,EmptyPolicy.HOLD),true,true),true,false);
        CompoundTag before=SiegeRules36.save(s);Observation capture=new Observation(Stage.OUTER,true,false);
        for(int wrong:new int[]{-1,0,2,Integer.MAX_VALUE})refuse(()->SiegeRules36.tick(s,wrong,capture));
        h.assertTrue(before.equals(SiegeRules36.save(s)),"Rejected clock replay mutated original snapshot");
        Snapshot next=SiegeRules36.tick(s,1,capture);
        h.assertTrue(next.elapsedTicks()==2&&next.status()==Status.TOWN_HALL&&next.townHallProgress()==0,"Exact next observation was not applied once");
        h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void observation_for_wrong_or_previous_objective_is_refused(GameTestHelper h)throws Exception {
        Snapshot outer=SiegeRules36.start(contract(20,2,3,EmptyPolicy.HOLD),true,true);
        refuse(()->SiegeRules36.tick(outer,0,new Observation(Stage.TOWN_HALL,true,false)));
        Snapshot hall=advance(advance(outer,true,false),true,false);CompoundTag before=SiegeRules36.save(hall);
        refuse(()->SiegeRules36.tick(hall,2,new Observation(Stage.OUTER,true,false)));
        h.assertTrue(before.equals(SiegeRules36.save(hall))&&outer.elapsedTicks()==0,"Wrong-objective observation changed progress or time");
        h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void exact_deadline_precedes_last_capture_tick_but_earlier_finish_wins(GameTestHelper h) {
        Contract c=contract(6,2,2,EmptyPolicy.HOLD);
        Snapshot common=advance(advance(SiegeRules36.start(c,true,true),true,false),true,false);
        Snapshot late=advance(advance(common,false,false),false,false);
        late=advance(late,true,false);
        h.assertTrue(late.elapsedTicks()==5&&late.townHallProgress()==1&&!late.terminal(),"Missing exact-deadline fixture");
        late=advance(late,true,false);
        h.assertTrue(late.status()==Status.DEFENDER_WON_TIMEOUT&&late.townHallProgress()==1,"Expiry tick was also credited as final capture or overtime");
        Snapshot early=advance(advance(advance(common,false,false),true,false),true,false);
        h.assertTrue(early.status()==Status.ATTACKER_WON&&early.elapsedTicks()==5,"A fully completed hold before expiry was refused");
        h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void explicit_timing_contract_rejects_invalid_and_impossible_values(GameTestHelper h)throws Exception {
        for(int[] values:new int[][]{{0,1,1},{-1,1,1},{10,0,1},{10,1,0},{10,-1,1},{10,1,-1},{2,1,1},{5,3,3},{Integer.MAX_VALUE,Integer.MAX_VALUE-1,Integer.MAX_VALUE-1}})
            refuse(()->new Timing("synthetic-siege36-v1",values[0],values[1],values[2],EmptyPolicy.HOLD));
        refuse(()->new Timing("",10,2,3,EmptyPolicy.HOLD));
        Snapshot minimum=SiegeRules36.start(contract(3,1,1,EmptyPolicy.HOLD),true,true);
        minimum=advance(advance(minimum,true,false),true,false);
        h.assertTrue(minimum.status()==Status.ATTACKER_WON&&minimum.remainingTicks()==1,"Smallest feasible synthetic timing could not complete before expiry");
        Timing large=new Timing("synthetic-siege36-v1",Integer.MAX_VALUE,2,3,EmptyPolicy.HOLD);
        h.assertTrue(large.totalTicks()==Integer.MAX_VALUE,"Positive bounded int timing was silently clamped");
        Contract big=new Contract(UUID.randomUUID(),"synthetic-siege36-world","a",UUID.randomUUID(),UUID.randomUUID(),large);
        Snapshot nearLimit=new Snapshot(big,Status.OUTER,Integer.MAX_VALUE-1,0,0);
        Snapshot expired=advance(nearLimit,true,true);
        h.assertTrue(expired.elapsedTicks()==Integer.MAX_VALUE&&expired.remainingTicks()==0
            &&expired.status()==Status.DEFENDER_WON_TIMEOUT,"Largest allowed deadline overflowed or failed to expire");
        h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void empty_zone_behavior_requires_explicit_hold_or_reset_contract(GameTestHelper h) {
        for(EmptyPolicy policy:EmptyPolicy.values()) {
            Snapshot s=SiegeRules36.start(contract(20,2,3,policy),true,true);
            s=advance(s,true,false);s=advance(s,false,false);
            int expected=policy==EmptyPolicy.HOLD?1:0;
            h.assertTrue(s.outerProgress()==expected&&s.elapsedTicks()==2,"Empty outer zone ignored the frozen contract policy");
            while(s.status()==Status.OUTER)s=advance(s,true,false);
            s=advance(s,true,false);int before=s.elapsedTicks();s=advance(s,false,false);
            h.assertTrue(s.outerProgress()==2&&s.townHallProgress()==expected&&s.elapsedTicks()==before+1,"Empty Town Hall reset completed outer capture or stopped overall time");
        }
        h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void malformed_snapshot_fields_and_impossible_counters_are_refused(GameTestHelper h)throws Exception {
        Contract c=contract(20,2,3,EmptyPolicy.HOLD);CompoundTag saved=SiegeRules36.save(SiegeRules36.start(c,true,true));
        CompoundTag extra=saved.copy();extra.putString("unrecognizedAuthority","approved");refuse(()->SiegeRules36.load(extra,c));
        CompoundTag schema=saved.copy();schema.putInt("schema",2);refuse(()->SiegeRules36.load(schema,c));
        CompoundTag status=saved.copy();status.putString("status","UNRECOGNIZED_WINNER");refuse(()->SiegeRules36.load(status,c));
        for(String key:saved.getAllKeys()) {
            CompoundTag missing=saved.copy();missing.remove(key);refuse(()->SiegeRules36.load(missing,c));
            CompoundTag wrong=saved.copy();Tag previous=saved.get(key);
            wrong.put(key,previous instanceof StringTag?IntTag.valueOf(1):StringTag.valueOf("wrong-type"));
            refuse(()->SiegeRules36.load(wrong,c));
        }
        refuse(()->new Snapshot(c,Status.OUTER,-1,0,0));
        refuse(()->new Snapshot(c,Status.OUTER,1,2,0));
        refuse(()->new Snapshot(c,Status.OUTER,1,1,1));
        refuse(()->new Snapshot(c,Status.TOWN_HALL,2,1,0));
        refuse(()->new Snapshot(c,Status.TOWN_HALL,2,2,1));
        refuse(()->new Snapshot(c,Status.ATTACKER_WON,20,2,3));
        refuse(()->new Snapshot(c,Status.ATTACKER_WON,5,2,2));
        refuse(()->new Snapshot(c,Status.DEFENDER_WON_TIMEOUT,19,0,0));
        refuse(()->new Snapshot(c,Status.DEFENDER_WON_SURRENDER,20,2,0));
        h.assertTrue(saved.equals(SiegeRules36.save(SiegeRules36.start(c,true,true))),"Malformed read attempts modified canonical state");
        h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void reloaded_snapshot_cannot_adopt_foreign_identity_or_changed_rules(GameTestHelper h)throws Exception {
        Contract c=contract(20,2,3,EmptyPolicy.HOLD);
        Snapshot original=advance(advance(advance(SiegeRules36.start(c,true,true),true,false),true,false),true,false);
        CompoundTag saved=SiegeRules36.save(original),unchanged=saved.copy();
        List<Contract> foreign=List.of(
            new Contract(UUID.randomUUID(),c.world(),c.city(),c.attackers(),c.defenders(),c.timing()),
            new Contract(c.siege(),"foreign-world",c.city(),c.attackers(),c.defenders(),c.timing()),
            new Contract(c.siege(),c.world(),"b",c.attackers(),c.defenders(),c.timing()),
            new Contract(c.siege(),c.world(),c.city(),UUID.randomUUID(),c.defenders(),c.timing()),
            new Contract(c.siege(),c.world(),c.city(),c.attackers(),UUID.randomUUID(),c.timing()),
            new Contract(c.siege(),c.world(),c.city(),c.attackers(),c.defenders(),new Timing("synthetic-siege36-v2",20,2,3,EmptyPolicy.HOLD)),
            new Contract(c.siege(),c.world(),c.city(),c.attackers(),c.defenders(),new Timing(c.timing().revision(),21,2,3,EmptyPolicy.HOLD)),
            new Contract(c.siege(),c.world(),c.city(),c.attackers(),c.defenders(),new Timing(c.timing().revision(),20,3,3,EmptyPolicy.HOLD)),
            new Contract(c.siege(),c.world(),c.city(),c.attackers(),c.defenders(),new Timing(c.timing().revision(),20,2,4,EmptyPolicy.HOLD)),
            new Contract(c.siege(),c.world(),c.city(),c.attackers(),c.defenders(),new Timing(c.timing().revision(),20,2,3,EmptyPolicy.RESET)));
        for(Contract wrong:foreign)refuse(()->SiegeRules36.load(saved,wrong));
        Snapshot restored=SiegeRules36.load(saved,c);
        h.assertTrue(restored.equals(original)&&saved.equals(unchanged),"Foreign contract read changed original rules or valid progress");
        h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void compressed_nbt_readback_retains_partial_clock_and_future_result(GameTestHelper h)throws Exception {
        h.assertTrue(h.getLevel().getServer() instanceof GameTestServer,"Disk fixture requires isolated GameTestServer");
        Path root=h.getLevel().getServer().getWorldPath(LevelResource.ROOT).toRealPath();
        h.assertTrue(root.getFileName().toString().equals("world")
            &&root.getParent().getFileName().toString().equals("controlled-catancraft_siege36-run"),"Foreign world path refused by siege codec fixture");
        Contract c=contract(12,2,3,EmptyPolicy.HOLD);Snapshot original=SiegeRules36.start(c,true,true);
        original=advance(original,true,false);original=advance(original,true,true);original=advance(original,true,false);original=advance(original,true,false);
        h.assertTrue(original.status()==Status.TOWN_HALL&&original.elapsedTicks()==4&&original.townHallProgress()==1,"Missing partial Town Hall snapshot");
        Path file=root.resolve("data/siege36-codec-tests/"+UUID.randomUUID()+".dat");Files.createDirectories(file.getParent());
        try(var output=Files.newOutputStream(file,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){NbtIo.writeCompressed(SiegeRules36.save(original),output);}
        byte[] bytes=Files.readAllBytes(file);Snapshot restored=SiegeRules36.load(NbtIo.readCompressed(file.toFile()),c);
        h.assertTrue(restored.equals(original)&&restored.remainingTicks()==8,"Codec readback reset progress, timing or the frozen contract");
        for(int tick=0;tick<3;tick++) {original=advance(original,true,true);restored=advance(restored,true,true);}
        original=advance(advance(original,true,false),true,false);restored=advance(advance(restored,true,false),true,false);
        h.assertTrue(original.equals(restored)&&restored.status()==Status.ATTACKER_WON&&restored.elapsedTicks()==9
            &&Arrays.equals(bytes,Files.readAllBytes(file)),"Continuing a reloaded snapshot changed outcome or rewrote its source evidence");
        h.succeed();
    }

    private interface Attempt {void run()throws Exception;}
    private static void refuse(Attempt attempt)throws Exception {
        try {attempt.run();}catch(IllegalArgumentException|IllegalStateException|IOException expected){return;}
        throw new AssertionError("Expected siege rules refusal");
    }
    private static Contract contract(int total,int outer,int townHall,EmptyPolicy empty) {
        return new Contract(UUID.randomUUID(),"synthetic-siege36-world","a",UUID.randomUUID(),UUID.randomUUID(),
            new Timing("synthetic-siege36-v1",total,outer,townHall,empty));
    }
    private static Snapshot advance(Snapshot snapshot,boolean attackers,boolean defenders) {
        return SiegeRules36.tick(snapshot,snapshot.elapsedTicks(),new Observation(snapshot.activeStage(),attackers,defenders));
    }
}
