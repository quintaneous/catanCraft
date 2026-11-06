package com.quin.catancraft.world;

import com.mojang.authlib.GameProfile;
import com.quin.catancraft.data.*;
import com.quin.catancraft.map.MapDefinitionManager;
import com.quin.catancraft.world.SiegeObjectives37.*;
import com.quin.catancraft.world.SiegeRules36.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Synthetic read-only objective fixtures. Detached FakePlayers model roster entries;
 * they are NEVER inserted into PlayerList or the entity world, and do not prove
 * authenticated client connections. All nation/ownership mutations are detached.
 * Numeric positions/durations are fixture values, not approved City A objectives.
 */
@GameTestHolder("catancraft_objectives37") @PrefixGameTestTemplate(false)
public final class SiegeObjectives37Tests {
    private static final AtomicInteger NAMES=new AtomicInteger();
    private static final String DIM="minecraft:overworld";
    private static final Box OUTER=new Box(-900,140,-900,-896,144,-896);
    private static final Box HALL=new Box(-880,140,-900,-876,144,-896);
    private SiegeObjectives37Tests() {}

    @GameTest(template="townhall_state_empty")
    public static void boxes_reject_empty_reversed_oversized_and_world_overflow_bounds(GameTestHelper h)throws Exception {
        for(int[] v:new int[][]{
            {0,0,0,0,1,1},{0,0,0,-1,1,1},{0,0,0,1,0,1},{0,0,0,1,1,0},
            {0,0,0,65,1,1},{0,0,0,1,33,1},{0,0,0,1,1,65},
            {-30000001,0,0,-30000000,1,1},{29999999,0,0,30000001,1,1},
            {0,0,-30000001,1,1,-30000000},{0,0,29999999,1,1,30000001},
            {Integer.MIN_VALUE,0,0,Integer.MAX_VALUE,1,1},{0,Integer.MIN_VALUE,0,1,Integer.MAX_VALUE,1}})
            refuse(()->new Box(v[0],v[1],v[2],v[3],v[4],v[5]));
        Box maximum=new Box(0,0,0,64,32,64);
        h.assertTrue(maximum.contains(63.99,31.99,63.99),"Maximum allowed bounded volume was rejected");
        for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
            h.assertTrue(!maximum.contains(invalid,1,1)&&!maximum.contains(1,invalid,1)&&!maximum.contains(1,1,invalid),"Nonfinite player position counted inside objective");
        h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void real_player_feet_obey_min_inclusive_max_exclusive_on_xyz(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h);f.roster=List.of(f.attacker);
        double[][] inside={{-900,140,-900},{-896.0001,143.9999,-896.0001},{-898,142,-898}};
        for(double[] p:inside){position(f.attacker,p);h.assertTrue(f.sample().attackersInside()==1,"Inside/min-boundary player feet were excluded");}
        double[][] outside={{-900.0001,142,-898},{-896,142,-898},{-898,139.9999,-898},
            {-898,144,-898},{-898,142,-900.0001},{-898,142,-896}};
        for(double[] p:outside){position(f.attacker,p);Report r=f.sample();h.assertTrue(r.attackersInside()==0&&r.attackersOnline()==1,"Exterior/max-boundary feet were counted, or outside meant offline");}
        h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void profiles_reject_overlap_and_bad_labels_but_allow_touching_faces(GameTestHelper h)throws Exception {
        refuse(()->new Profile("a","fixture",DIM,OUTER,new Box(-899,141,-899,-895,145,-895)));
        refuse(()->new Profile("A","fixture",DIM,OUTER,HALL));
        refuse(()->new Profile("a","",DIM,OUTER,HALL));
        refuse(()->new Profile("a","fixture","",OUTER,HALL));
        refuse(()->new Profile("a","fixture\nchanged",DIM,OUTER,HALL));
        Box adjacent=new Box(-896,140,-900,-892,144,-896);
        Profile p=new Profile("a","synthetic-touching-v1",DIM,OUTER,adjacent);
        new Registry(h.getLevel(),"synthetic-touching-world",List.of(p));
        h.assertTrue(!OUTER.overlaps(adjacent)&&p.box(Stage.OUTER)==OUTER&&p.box(Stage.TOWN_HALL)==adjacent,"Face-touching objectives overlap or select the wrong stage");
        boolean nullRefused=false;try{p.box(null);}catch(NullPointerException expected){nullRefused=true;}
        h.assertTrue(nullRefused,"Unknown objective silently selected Town Hall");h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void catalog_requires_bounded_unique_city_entries(GameTestHelper h)throws Exception {
        Profile p=profile();ServerLevel level=h.getLevel();
        refuse(()->new Registry(level,"fixture",List.of()));
        refuse(()->new Registry(level,"fixture",null));
        refuse(()->new Registry(level,"fixture",List.of(p,p)));
        refuse(()->new Registry(level,"fixture",Collections.nCopies(65,p)));
        refuse(()->new Registry(level,"",List.of(p)));
        h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void catalog_refuses_unknown_city_wrong_dimension_and_foreign_city_geometry(GameTestHelper h)throws Exception {
        ServerLevel level=h.getLevel();
        refuse(()->new Registry(level,"fixture",List.of(new Profile("unknown_city37","fixture",DIM,OUTER,HALL))));
        refuse(()->new Registry(level,"fixture",List.of(new Profile("a","fixture","minecraft:the_nether",OUTER,HALL))));
        refuse(()->new Registry(level,"fixture",List.of(new Profile("a","fixture","not-a-dimension",OUTER,HALL))));
        // A second city cannot adopt City A's objective volumes; boundary checks refuse first.
        refuse(()->new Registry(level,"fixture",List.of(profile(),new Profile("b","fixture",DIM,OUTER,HALL))));
        h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void every_objective_column_and_full_height_must_fit_registered_city(GameTestHelper h)throws Exception {
        ServerLevel level=h.getLevel();
        h.assertTrue(MapDefinitionManager.territory("a").contains(DIM,-502,-900)
            &&!MapDefinitionManager.territory("a").contains(DIM,-499,-900),"Unexpected canonical boundary for crossing fixture");
        Box crossing=new Box(-502,140,-900,-498,144,-896);
        Box elsewhere=new Box(100,140,100,104,144,104);
        Box low=new Box(-900,level.getMinBuildHeight()-1,-900,-896,level.getMinBuildHeight()+1,-896);
        Box high=new Box(-900,level.getMaxBuildHeight()-1,-900,-896,level.getMaxBuildHeight()+1,-896);
        for(Box invalid:List.of(crossing,elsewhere,low,high))refuse(()->new Registry(level,"fixture",List.of(new Profile("a","fixture",DIM,invalid,HALL))));
        Box validTop=new Box(-900,level.getMaxBuildHeight()-2,-900,-896,level.getMaxBuildHeight(),-896);
        new Registry(level,"fixture",List.of(new Profile("a","fixture",DIM,validTop,HALL)));
        h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void registry_copies_caller_catalog_and_bound_profile_cannot_drift(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h);List<Profile> input=new ArrayList<>(List.of(f.profile));
        Registry registry=new Registry(f.level,f.contract.world(),input);Bound first=registry.bind(f.contract);
        Profile replacement=new Profile("a","replacement-not-adopted",DIM,new Box(-908,140,-900,-904,144,-896),HALL);
        input.clear();input.add(replacement);
        Bound again=registry.bind(f.contract);
        h.assertTrue(first.profile()==f.profile&&again.profile()==f.profile&&again.contract().equals(f.contract),"Caller collection replacement changed frozen profile/contract");
        Contract wrongWorld=new Contract(f.contract.siege(),"foreign-world","a",f.attackers.id(),f.defenders.id(),f.contract.timing());
        Contract wrongCity=new Contract(f.contract.siege(),f.contract.world(),"b",f.attackers.id(),f.defenders.id(),f.contract.timing());
        refuse(()->registry.bind(wrongWorld));refuse(()->registry.bind(wrongCity));h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void survival_roster_uses_actual_positions_and_reports_current_frame(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h);Report r=f.sample();
        h.assertTrue(r.attackersOnline()==1&&r.defendersOnline()==1&&r.attackersInside()==1&&r.defendersInside()==0
            &&r.bothNationsOnline()&&r.objective()==Stage.OUTER&&r.elapsedTicks()==0
            &&r.gameTime()==f.level.getGameTime()&&r.profile()==f.profile&&r.contract().equals(f.contract),"Native player positions/roster/frame produced wrong observation");
        h.assertTrue(r.observation().attackersPresent()&&!r.observation().defendersPresent(),"Observation does not match actual participant counts");
        h.assertTrue(!f.attacker.connection.isAcceptingMessages()&&!f.defender.connection.isAcceptingMessages(),"Detached FakePlayer dummy connection unexpectedly accepts network messages");
        h.assertTrue(!SiegeObjectives37.connected(f.attacker,f.level)&&!SiegeObjectives37.connected(f.defender,f.level),"Detached FakePlayer was accepted as an authenticated connection");
        refuse(()->SiegeObjectives37.sample(f.bound,f.state));
        h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void active_stage_switch_counts_only_the_new_objective(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h);place(f.defender,HALL);Report outer=f.sample();
        h.assertTrue(outer.attackersInside()==1&&outer.defendersInside()==0,"Inactive Town Hall defender contested the outer point");
        f.tick();f.tick();Report hall=f.sample();
        h.assertTrue(hall.objective()==Stage.TOWN_HALL&&hall.elapsedTicks()==2&&hall.attackersInside()==0
            &&hall.defendersInside()==1,"Stage transition counted players in the previous objective");
        place(f.attacker,HALL);Report contested=f.sample();
        h.assertTrue(contested.attackersInside()==1&&contested.defendersInside()==1,"New active objective did not use current player positions");h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void actual_presence_reports_drive_two_stage_attacker_win_without_timer_reset(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h);f.tick();f.tick();
        h.assertTrue(f.state.status()==Status.TOWN_HALL&&f.state.elapsedTicks()==2&&f.state.remainingTicks()==10,"Outer observation reset timer or failed to unlock Town Hall");
        // Staying in the old objective grants no Town Hall progress.
        f.tick();h.assertTrue(f.state.townHallProgress()==0&&f.state.elapsedTicks()==3,"Old objective granted Town Hall progress");
        place(f.attacker,HALL);f.tick();f.tick();f.tick();
        h.assertTrue(f.state.status()==Status.ATTACKER_WON&&f.state.elapsedTicks()==6
            &&f.state.winnerNationId().orElseThrow().equals(f.attackers.id()),"Server-observed objective progression did not reach attacker victory");h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void defender_presence_holds_town_hall_until_defender_timeout_win(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h,8);f.tick();f.tick();place(f.attacker,HALL);f.tick();place(f.defender,HALL);
        while(!f.state.terminal())f.tick();
        h.assertTrue(f.state.status()==Status.DEFENDER_WON_TIMEOUT&&f.state.elapsedTicks()==8
            &&f.state.outerProgress()==2&&f.state.townHallProgress()==1
            &&f.state.winnerNationId().orElseThrow().equals(f.defenders.id()),"Actual defender presence stopped the clock, advanced capture or prevented defender victory");h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void third_party_nonmember_and_invited_only_players_do_not_capture_or_contest(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h);FakePlayer third=f.player(f.level),unaffiliated=f.player(f.level),invited=f.player(f.level);
        NationData thirdNation=f.data.createNation("Third37",UUID.randomUUID());thirdNation.addMember(third.getUUID());f.defenders.invite(invited.getUUID());
        for(FakePlayer p:List.of(third,unaffiliated,invited))place(p,OUTER);
        f.roster=List.of(third,unaffiliated,invited);Report ignored=f.sample();
        h.assertTrue(ignored.attackersOnline()==0&&ignored.defendersOnline()==0&&ignored.attackersInside()==0
            &&ignored.defendersInside()==0&&!ignored.bothNationsOnline(),"Unrelated/invited/nonmember player counted as participant");
        f.roster=List.of(f.attacker,third,unaffiliated,invited);Report r=f.sample();
        h.assertTrue(r.attackersInside()==1&&r.defendersInside()==0,"Third party presence contested a real attacker");h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void creative_and_spectator_players_stay_online_but_cannot_capture(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h);place(f.defender,OUTER);
        for(GameType mode:List.of(GameType.CREATIVE,GameType.SPECTATOR)) {
            f.attacker.setGameMode(mode);f.defender.setGameMode(mode);Report r=f.sample();
            h.assertTrue(r.attackersOnline()==1&&r.defendersOnline()==1&&r.bothNationsOnline()
                &&r.attackersInside()==0&&r.defendersInside()==0,"Creative/spectator counted for capture or was incorrectly counted offline");
        }
        f.attacker.setGameMode(GameType.SURVIVAL);f.defender.setGameMode(GameType.SURVIVAL);
        h.assertTrue(f.sample().attackersInside()==1&&f.sample().defendersInside()==1,"Returning to survival did not restore eligibility");h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void dead_players_cannot_capture_and_removed_players_are_not_online(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h);place(f.defender,OUTER);f.attacker.setHealth(0);f.defender.setHealth(0);
        h.assertTrue(!f.attacker.isAlive()&&!f.defender.isAlive(),"Dead native player fixture is still alive");
        Report dead=f.sample();h.assertTrue(dead.attackersOnline()==1&&dead.defendersOnline()==1
            &&dead.attackersInside()==0&&dead.defendersInside()==0,"Dead player granted capture or implied disconnected surrender");
        f.attacker.discard();f.defender.discard();Report removed=f.sample();
        h.assertTrue(removed.attackersOnline()==0&&removed.defendersOnline()==0&&!removed.bothNationsOnline(),"Removed roster entry remained online");h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void player_in_another_dimension_is_online_but_outside_objective(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h);ServerLevel nether=f.level.getServer().getLevel(Level.NETHER);
        h.assertTrue(nether!=null,"Controlled server did not provide its native Nether level");
        FakePlayer elsewhere=f.player(nether);f.attackers.addMember(elsewhere.getUUID());place(elsewhere,OUTER);
        f.roster=List.of(elsewhere,f.defender);Report r=f.sample();
        h.assertTrue(elsewhere.serverLevel()!=f.level&&r.attackersOnline()==1&&r.defendersOnline()==1
            &&r.attackersInside()==0,"Matching XYZ in a different dimension captured the objective");h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void removed_roster_entries_take_effect_immediately_and_old_report_is_immutable(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h);List<ServerPlayer> roster=new ArrayList<>(List.of(f.attacker,f.defender));f.roster=roster;
        Report first=f.sample();roster.remove(f.attacker);Report second=f.sample();roster.clear();Report empty=f.sample();
        h.assertTrue(first.attackersOnline()==1&&first.defendersOnline()==1&&first.attackersInside()==1
            &&second.attackersOnline()==0&&second.defendersOnline()==1&&second.attackersInside()==0
            &&empty.attackersOnline()==0&&empty.defendersOnline()==0,"Roster removal was cached or mutated a previous report");
        h.assertTrue(!empty.observation().attackersPresent()&&!empty.observation().defendersPresent()
            &&f.state.status()==Status.OUTER&&f.state.elapsedTicks()==0,"Empty roster advanced/ended siege implicitly");h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void membership_is_resolved_again_on_each_sample(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h);f.roster=List.of(f.attacker);Report original=f.sample();
        f.attackers.removeMember(f.attacker.getUUID());Report removed=f.sample();f.defenders.addMember(f.attacker.getUUID());Report switched=f.sample();
        h.assertTrue(original.attackersInside()==1&&removed.attackersInside()==0&&removed.attackersOnline()==0
            &&switched.attackersOnline()==0&&switched.defendersOnline()==1&&switched.defendersInside()==1,"Sample cached former nation membership or role");
        h.assertTrue(f.state.elapsedTicks()==0,"Membership observation changed siege progression");h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void ambiguous_membership_refuses_instead_of_choosing_first_nation(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h);f.defenders.addMember(f.attacker.getUUID());CompoundTag before=f.data.save(new CompoundTag());
        refuse(f::sample);h.assertTrue(before.equals(f.data.save(new CompoundTag())),"Ambiguous membership read modified nation data");
        f.defenders.removeMember(f.attacker.getUUID());NationData unrelated=f.data.createNation("AmbiguousThird37",UUID.randomUUID());unrelated.addMember(f.attacker.getUUID());
        refuse(f::sample);h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void duplicate_player_uuid_in_roster_is_refused_without_double_counting(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h);f.roster=List.of(f.attacker,f.attacker);refuse(f::sample);
        FakePlayer alias=new FakePlayer(f.level,new GameProfile(f.attacker.getUUID(),"Siege37Alias"));
        alias.setGameMode(GameType.SURVIVAL);alias.setHealth(alias.getMaxHealth());place(alias,OUTER);
        h.assertTrue(alias!=f.attacker&&alias.getUUID().equals(f.attacker.getUUID()),"Duplicate-identity fixture is not distinct instances sharing UUID");
        f.roster=List.of(f.attacker,alias);refuse(f::sample);
        h.assertTrue(f.state.elapsedTicks()==0,"Duplicate roster advanced siege");h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void changed_city_owner_or_missing_participating_nation_refuses(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h);
        for(UUID wrong:List.of(f.attackers.id(),UUID.randomUUID())) {
            f.data.territory("a").setOwnerNationId(wrong);CompoundTag before=f.data.save(new CompoundTag());refuse(f::sample);
            h.assertTrue(before.equals(f.data.save(new CompoundTag())),"Owner mismatch caused data repair or ownership transfer");
        }
        f.data.territory("a").setOwnerNationId(null);refuse(f::sample);
        for(boolean removeAttacker:List.of(true,false)) {
            Fixture missing=new Fixture(h);missing.data.nations().remove(removeAttacker?missing.attackers:missing.defenders);refuse(missing::sample);
        }
        h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void foreign_contract_terminal_state_and_real_saved_data_are_refused(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h);
        Contract changed=new Contract(UUID.randomUUID(),f.contract.world(),"a",f.attackers.id(),f.defenders.id(),f.contract.timing());
        Snapshot foreign=SiegeRules36.start(changed,true,true);refuse(()->SiegeObjectives37.sampleFixture(f.bound,foreign,f.data,f.roster));
        Snapshot surrendered=SiegeRules36.surrender(f.state,f.attackers.id());
        refuse(()->SiegeObjectives37.sampleFixture(f.bound,surrendered,f.data,f.roster));
        Snapshot won=f.state;for(int i=0;i<5;i++)won=SiegeRules36.tick(won,won.elapsedTicks(),new Observation(won.activeStage(),true,false));
        Snapshot terminalWon=won;refuse(()->SiegeObjectives37.sampleFixture(f.bound,terminalWon,f.data,f.roster));
        Snapshot timedOut=new Snapshot(f.contract,Status.DEFENDER_WON_TIMEOUT,12,0,0);
        refuse(()->SiegeObjectives37.sampleFixture(f.bound,timedOut,f.data,f.roster));
        CatanSavedData real=CatanSavedData.get(f.level.getServer());CompoundTag realBefore=real.save(new CompoundTag());
        refuse(()->SiegeObjectives37.sampleFixture(f.bound,f.state,real,f.roster));
        h.assertTrue(realBefore.equals(real.save(new CompoundTag())),"Fixture seam changed real SavedData");h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void observation_is_read_only_for_players_data_boundaries_and_progress(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h);place(f.defender,OUTER);
        CompoundTag data=f.data.save(new CompoundTag()),state=SiegeRules36.save(f.state);
        CompoundTag attacker=f.attacker.saveWithoutId(new CompoundTag()),defender=f.defender.saveWithoutId(new CompoundTag());
        CompoundTag actual=CatanSavedData.get(f.level.getServer()).save(new CompoundTag());
        var boundary=MapDefinitionManager.territory("a").toBoundary().points();
        var playerList=List.copyOf(f.level.getServer().getPlayerList().getPlayers());
        Report first=f.sample(),second=f.sample();
        h.assertTrue(first.equals(second)&&first.attackersInside()==1&&first.defendersInside()==1,"Same tick/positions produced inconsistent reports");
        h.assertTrue(data.equals(f.data.save(new CompoundTag()))&&state.equals(SiegeRules36.save(f.state))
            &&attacker.equals(f.attacker.saveWithoutId(new CompoundTag()))&&defender.equals(f.defender.saveWithoutId(new CompoundTag()))
            &&actual.equals(CatanSavedData.get(f.level.getServer()).save(new CompoundTag()))
            &&boundary.equals(MapDefinitionManager.territory("a").toBoundary().points())
            &&playerList.equals(f.level.getServer().getPlayerList().getPlayers()),"Read-only observation mutated data/player/contract/boundary/connection state");
        h.assertTrue(f.level.getEntity(f.attacker.getUUID())==null&&f.level.getEntity(f.defender.getUUID())==null
            &&f.level.getServer().getPlayerList().getPlayer(f.attacker.getUUID())==null
            &&f.level.getServer().getPlayerList().getPlayer(f.defender.getUUID())==null,"Fixture inserted FakePlayer into live roster/entity storage");h.succeed();
    }

    private interface Attempt {void run()throws Exception;}
    private static void refuse(Attempt action)throws Exception {
        try{action.run();}catch(IllegalArgumentException|IllegalStateException|IOException expected){return;}
        throw new AssertionError("Expected guarded objective refusal");
    }
    private static Profile profile(){return new Profile("a","synthetic-objectives37-v1",DIM,OUTER,HALL);}
    private static void position(ServerPlayer p,double[] xyz){p.setPos(xyz[0],xyz[1],xyz[2]);}
    private static void place(ServerPlayer p,Box box){p.setPos(box.minX()+.5,box.minY()+.25,box.minZ()+.5);}
    private static final class Fixture {
        final ServerLevel level;final CatanSavedData data=new CatanSavedData();final NationData attackers,defenders;
        final FakePlayer attacker,defender;final Profile profile=profile();final Contract contract;final Registry registry;final Bound bound;
        List<ServerPlayer> roster;Snapshot state;
        Fixture(GameTestHelper h)throws Exception{this(h,12);}
        Fixture(GameTestHelper h,int total)throws Exception {
            level=h.getLevel();h.assertTrue(level.getServer() instanceof GameTestServer,"Synthetic objectives require isolated GameTestServer");
            attackers=data.createNation("Attack37",UUID.randomUUID());defenders=data.createNation("Defend37",UUID.randomUUID());
            data.createTerritory("a","Synthetic objective city",ResourceType.WOOD).setOwnerNationId(defenders.id());
            attacker=player(level);defender=player(level);attackers.addMember(attacker.getUUID());defenders.addMember(defender.getUUID());
            place(attacker,OUTER);defender.setPos(-910,140,-910);roster=List.of(attacker,defender);
            contract=new Contract(UUID.randomUUID(),"synthetic-objective-world-"+UUID.randomUUID(),"a",attackers.id(),defenders.id(),
                new Timing("synthetic-objective-timing-v1",total,2,3,EmptyPolicy.HOLD));
            registry=new Registry(level,contract.world(),List.of(profile));bound=registry.bind(contract);state=SiegeRules36.start(contract,true,true);
        }
        FakePlayer player(ServerLevel at) {
            FakePlayer p=FakePlayerFactory.get(at,new GameProfile(UUID.randomUUID(),"Siege37_"+NAMES.incrementAndGet()));
            p.setGameMode(GameType.SURVIVAL);p.setHealth(p.getMaxHealth());p.getInventory().clearContent();return p;
        }
        Report sample()throws IOException{return SiegeObjectives37.sampleFixture(bound,state,data,roster);}
        void tick()throws IOException{Report report=sample();state=SiegeRules36.tick(state,report.elapsedTicks(),report.observation());}
    }
}
