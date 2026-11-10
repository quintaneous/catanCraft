package com.quin.catancraft.world;

import com.quin.catancraft.world.SiegeObjectives37.*;
import com.quin.catancraft.world.SiegeRules36.*;
import com.quin.catancraft.world.SiegeSession38.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.UnaryOperator;

/** Disposable, unique-city journal fixtures. The trusted adapter models server
 * observations/authorization and a clock starting at100; actual server time,
 * SavedData, players and city geometry are never modified. These are native
 * storage/handle tests, not an authenticated client or separate-process restart.
 */
@GameTestHolder("catancraft_session38") @PrefixGameTestTemplate(false)
public final class SiegeSession38Tests {
    private SiegeSession38Tests() {}
    private static final Fault NONE=point->{};

    @GameTest(template="townhall_state_empty")
    public static void creation_durably_binds_full_profile_contract_and_initial_state(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h);try(Session s=f.create()) {
            View v=s.read();CompoundTag n=ProducerJournal.read(f.path());
            h.assertTrue(v.revision()==0&&!v.recoveryRequired()&&v.snapshot().equals(SiegeRules36.start(f.contract,true,true)),"Created view is not initial running state");
            h.assertTrue(n.getLong("lastTick")==100&&n.getLong("revision")==0&&n.hasUUID("epoch")
                &&!n.getUUID("epoch").equals(new UUID(0,0))&&n.getCompound("state").equals(SiegeRules36.save(v.snapshot()))
                &&n.getCompound("identity").getString("world").equals(f.contract.world())
                &&n.getCompound("identity").getString("city").equals(f.contract.city()),"Durable state lost clock, identity or frozen contract");
            CompoundTag p=n.getCompound("profile");
            h.assertTrue(p.getString("revision").equals(f.profile.revision())&&p.getString("dimension").equals(f.profile.dimension())
                &&Arrays.equals(p.getIntArray("outer"),new int[]{-900,140,-900,-896,144,-896})
                &&Arrays.equals(p.getIntArray("townHall"),new int[]{-880,140,-900,-876,144,-896}),"Full objective profile was not retained");
        }h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void reopening_never_resumes_or_invents_downtime_progress(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h);View saved;try(Session s=f.create()){saved=f.tick(s);}
        byte[] bytes=Files.readAllBytes(f.path());f.time=900;
        try(Session reopened=f.open()) {
            View read=reopened.read();h.assertTrue(read.recoveryRequired()&&read.revision()==saved.revision()
                &&read.snapshot().equals(saved.snapshot()),"Reopening invented progress or authorized automatic resume");
            refuse(()->reopened.tick(read.revision()));refuse(()->reopened.surrender(read.revision(),f.contract.attackers()));
            same(h,bytes,f.path(),"Denied unresumed actions changed durable state");
        }h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void explicit_resume_preserves_capture_time_and_starts_new_epoch(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h);View saved;try(Session s=f.create()){f.tick(s);saved=f.tick(s);}
        UUID oldEpoch=ProducerJournal.read(f.path()).getUUID("epoch");f.time=500;
        try(Session reopened=f.open()) {
            View resumed=reopened.resume(saved.revision());
            h.assertTrue(!resumed.recoveryRequired()&&resumed.revision()==saved.revision()+1
                &&resumed.snapshot().equals(saved.snapshot()),"Resume changed capture progress or remaining siege time");
            CompoundTag disk=ProducerJournal.read(f.path());
            h.assertTrue(disk.getLong("lastTick")==500&&!oldEpoch.equals(disk.getUUID("epoch")),"Resume did not establish an explicit fresh epoch/frame");
            byte[] bytes=Files.readAllBytes(f.path());refuse(()->reopened.resume(resumed.revision()));same(h,bytes,f.path(),"Repeated resume rewrote a running siege");
            View next=f.tick(reopened);h.assertTrue(next.snapshot().elapsedTicks()==3&&next.snapshot().townHallProgress()==1,"First post-resume observation included downtime or lost current stage");
        }h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void stale_or_future_revisions_never_advance_or_surrender(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h);try(Session s=f.create()) {
            View one=f.tick(s);byte[] bytes=Files.readAllBytes(f.path());f.time++;
            for(long wrong:new long[]{-1,0,one.revision()+1,Long.MAX_VALUE}) {
                refuse(()->s.tick(wrong));refuse(()->s.surrender(wrong,f.contract.attackers()));
            }
            same(h,bytes,f.path(),"Stale/future revision modified the journal");
            View valid=s.tick(one.revision());h.assertTrue(valid.revision()==2&&valid.snapshot().elapsedTicks()==2,"Rejected revisions consumed current frame or duplicated progress");
        }h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void duplicate_server_frame_refuses_without_forcing_recovery(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h);try(Session s=f.create()) {
            View initial=s.read();byte[] before=Files.readAllBytes(f.path());refuse(()->s.tick(initial.revision()));
            same(h,before,f.path(),"Duplicate initial frame changed state");h.assertTrue(!s.read().recoveryRequired(),"Duplicate frame unnecessarily changed recovery permission");
            View once=f.tick(s);byte[] after=Files.readAllBytes(f.path());refuse(()->s.tick(once.revision()));same(h,after,f.path(),"Duplicate frame minted capture credit");
            View twice=f.tick(s);h.assertTrue(twice.snapshot().elapsedTicks()==2&&twice.revision()==2,"Valid successor frame did not advance once");
        }h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void skipped_clock_requires_explicit_recovery_for_tick_and_surrender(GameTestHelper h)throws Exception {
        for(boolean surrender:List.of(false,true)) {
            Fixture f=new Fixture(h);try(Session s=f.create()) {
                View old=f.tick(s);byte[] bytes=Files.readAllBytes(f.path());UUID oldEpoch=ProducerJournal.read(bytes).getUUID("epoch");f.time+=5;
                if(surrender)refuse(()->s.surrender(old.revision(),f.contract.attackers()));else refuse(()->s.tick(old.revision()));
                h.assertTrue(s.read().recoveryRequired()&&s.read().snapshot().equals(old.snapshot()),"Skipped frame was accepted or failed to require recovery");same(h,bytes,f.path(),"Skipped frame invented elapsed/capture time");
                f.time++;refuse(()->s.tick(old.revision()));View resumed=s.resume(old.revision());
                h.assertTrue(resumed.snapshot().equals(old.snapshot())&&!resumed.recoveryRequired()
                    &&!oldEpoch.equals(ProducerJournal.read(f.path()).getUUID("epoch")),"Explicit same-handle recovery changed progress or reused its previous epoch");
                View next=f.tick(s);h.assertTrue(next.snapshot().elapsedTicks()==2,"Gap recovery credited unseen frames");
            }
        }h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void backwards_clock_cannot_replay_or_surrender_without_recovery(GameTestHelper h)throws Exception {
        for(boolean surrender:List.of(false,true)) {
            Fixture f=new Fixture(h);try(Session s=f.create()) {
                View old=f.tick(s);byte[] bytes=Files.readAllBytes(f.path());f.time=90;
                if(surrender)refuse(()->s.surrender(old.revision(),f.contract.attackers()));else refuse(()->s.tick(old.revision()));
                h.assertTrue(s.read().recoveryRequired()&&s.read().snapshot().equals(old.snapshot()),"Backwards clock rewound progress or remained authorized");same(h,bytes,f.path(),"Clock rewind changed durable state");
                View resumed=s.resume(old.revision());h.assertTrue(resumed.snapshot().equals(old.snapshot()),"Revalidated epoch rewound timer");
                View next=f.tick(s);h.assertTrue(next.snapshot().elapsedTicks()==2&&next.revision()==3,"Revalidated successor did not advance exactly once");
            }
        }h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void city_os_lock_excludes_concurrent_writers_and_releases_on_close(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h);byte[] bytes;
        try(Session first=f.create()) {
            bytes=Files.readAllBytes(f.path());refuse(f::open);refuse(f::create);
            Contract original=f.contract;f.contract=changedContract(original,UUID.randomUUID(),original.world(),original.attackers(),original.defenders(),original.timing());
            refuse(f::create);f.contract=original;same(h,bytes,f.path(),"Competing same-city writer changed original history");
            h.assertTrue(first.read().revision()==0,"Competing writer disrupted original handle");
        }
        try(Session reopened=f.open()){h.assertTrue(reopened.read().recoveryRequired(),"Closed city lock did not permit a gated new handle");}
        same(h,bytes,f.path(),"Open/close lock cycle rewrote history");h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void all_terminal_outcomes_remain_immutable_across_reopen_and_actions(GameTestHelper h)throws Exception {
        for(Status wanted:List.of(Status.ATTACKER_WON,Status.DEFENDER_WON_TIMEOUT,Status.DEFENDER_WON_SURRENDER)) {
            Fixture f=new Fixture(h,8);View terminal;
            try(Session s=f.create()) {
                if(wanted==Status.DEFENDER_WON_SURRENDER)terminal=s.surrender(0,f.contract.attackers());
                else {f.defendersInside=wanted==Status.DEFENDER_WON_TIMEOUT?1:0;terminal=s.read();while(!terminal.snapshot().terminal())terminal=f.tick(s);}
                h.assertTrue(terminal.snapshot().status()==wanted,"Fixture did not reach requested terminal outcome");
            }
            byte[] bytes=Files.readAllBytes(f.path());f.time+=500;
            try(Session s=f.open()) {
                View read=s.read();h.assertTrue(!read.recoveryRequired()&&read.snapshot().equals(terminal.snapshot()),"Terminal reopen changed outcome or requested gameplay resume");
                h.assertTrue(s.resume(read.revision()).equals(read)&&s.tick(read.revision()).equals(read)
                    &&s.surrender(read.revision(),f.contract.attackers()).equals(read),"Completed action replay changed terminal result");
                same(h,bytes,f.path(),"Terminal replay rewrote result or epoch");
            }
        }h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void original_siege_nations_world_and_timing_cannot_be_rebound(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h);try(Session ignored=f.create()){}byte[] original=Files.readAllBytes(f.path());Contract c=f.contract;
        List<Contract> changed=List.of(
            changedContract(c,UUID.randomUUID(),c.world(),c.attackers(),c.defenders(),c.timing()),
            changedContract(c,c.siege(),"foreign-world",c.attackers(),c.defenders(),c.timing()),
            changedContract(c,c.siege(),c.world(),UUID.randomUUID(),c.defenders(),c.timing()),
            changedContract(c,c.siege(),c.world(),c.attackers(),UUID.randomUUID(),c.timing()),
            changedContract(c,c.siege(),c.world(),c.attackers(),c.defenders(),new Timing("changed",20,2,3,EmptyPolicy.HOLD)),
            changedContract(c,c.siege(),c.world(),c.attackers(),c.defenders(),new Timing(c.timing().revision(),21,2,3,EmptyPolicy.HOLD)));
        for(Contract foreign:changed){f.contract=foreign;refuse(f::open);same(h,original,f.path(),"Foreign contract replaced retained original history");}
        f.contract=c;try(Session valid=f.open()){h.assertTrue(valid.read().snapshot().contract().equals(c),"Original contract no longer loads");}h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void complete_profile_and_runtime_dimension_must_match_original(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h);try(Session ignored=f.create()){}byte[] bytes=Files.readAllBytes(f.path());Profile p=f.profile;
        List<Profile> changed=List.of(new Profile(p.city(),"changed",p.dimension(),p.outer(),p.townHall()),
            new Profile(p.city(),p.revision(),p.dimension(),new Box(-908,140,-900,-904,144,-896),p.townHall()),
            new Profile(p.city(),p.revision(),p.dimension(),p.outer(),new Box(-872,140,-900,-868,144,-896)),
            new Profile(p.city(),p.revision(),"minecraft:the_nether",p.outer(),p.townHall()),
            new Profile("other_fixture38",p.revision(),p.dimension(),p.outer(),p.townHall()));
        for(Profile foreign:changed){f.profile=foreign;refuse(f::open);same(h,bytes,f.path(),"Changed objective profile was adopted");}
        f.profile=p;byte[] resumedBytes;try(Session s=f.open()){s.resume(s.read().revision());resumedBytes=Files.readAllBytes(f.path());f.profile=changed.get(0);refuse(s::read);}
        f.profile=p;same(h,resumedBytes,f.path(),"Profile check changed data");h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void checksum_truncation_and_oversized_journals_are_retained_and_refused(GameTestHelper h)throws Exception {
        for(int mode=0;mode<3;mode++) {
            Fixture f=new Fixture(h);try(Session ignored=f.create()){}byte[] old=Files.readAllBytes(f.path());retain(f.path(),"original",old);
            byte[] bad;if(mode==0){bad=old.clone();bad[bad.length-1]^=1;}else if(mode==1)bad=Arrays.copyOf(old,old.length-1);else bad=new byte[256*1024+1];
            Files.write(f.path(),bad,StandardOpenOption.TRUNCATE_EXISTING);refuse(f::open);same(h,bad,f.path(),"Invalid file was reset or rewritten");
        }h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void sealed_but_malformed_schema_metadata_and_counters_are_refused(GameTestHelper h)throws Exception {
        List<UnaryOperator<CompoundTag>> mutations=List.of(
            n->{n.putInt("schema",2);return n;},n->{n.putString("unexpected","authority");return n;},
            n->{n.remove("epoch");return n;},n->{n.putUUID("epoch",new UUID(0,0));return n;},
            n->{n.putInt("revision",0);return n;},n->{n.putLong("revision",-1);return n;},
            n->{n.putLong("lastTick",-1);return n;},n->{n.getCompound("state").putInt("elapsed",-1);return n;},
            n->{n.getCompound("state").putInt("townHallProgress",1);return n;},
            n->{n.getCompound("state").putString("status","UNRECOGNIZED");return n;},
            n->{n.getCompound("profile").putString("extra","unapproved");return n;});
        for(UnaryOperator<CompoundTag> mutation:mutations) {
            Fixture f=new Fixture(h);try(Session ignored=f.create()){}byte[] original=Files.readAllBytes(f.path());retain(f.path(),"original",original);
            ProducerJournal.write(f.path(),mutation.apply(ProducerJournal.read(original)),true);byte[] hostile=Files.readAllBytes(f.path());
            refuse(f::open);same(h,hostile,f.path(),"Malformed sealed state was normalized or replaced");
        }h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void external_journal_or_lock_inode_replacement_invalidates_open_handle(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h);try(Session s=f.create()) {
            byte[] original=Files.readAllBytes(f.path());retain(f.path(),"original",original);CompoundTag changed=ProducerJournal.read(original);changed.putLong("revision",9);
            ProducerJournal.write(f.path(),changed,true);byte[] external=Files.readAllBytes(f.path());refuse(s::read);f.time++;refuse(()->s.tick(0));same(h,external,f.path(),"Open handle overwrote external journal change");
        }
        Fixture replaced=new Fixture(h);byte[] journal;
        try(Session old=replaced.create()) {
            journal=Files.readAllBytes(replaced.path());Path lock=replaced.lockPath(),retained=lock.resolveSibling(lock.getFileName()+".retained-original");
            Files.move(lock,retained);Files.write(lock,new byte[0],StandardOpenOption.CREATE_NEW);
            refuse(old::read);replaced.time++;refuse(()->old.tick(0));same(h,journal,replaced.path(),"Stale inode owner wrote after lock replacement");
        }
        try(Session fresh=replaced.open()){h.assertTrue(fresh.read().recoveryRequired(),"Replacement lock did not permit only a freshly gated handle");}
        h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void authorization_is_checked_for_creation_open_read_and_each_action(GameTestHelper h)throws Exception {
        Fixture createDenied=new Fixture(h);createDenied.denied.add(Action.CREATE);refuse(createDenied::create);
        h.assertTrue(!Files.exists(createDenied.path()),"Unauthorized creation wrote a journal");
        Fixture f=new Fixture(h);try(Session s=f.create()) {
            byte[] bytes=Files.readAllBytes(f.path());
            refuse(()->s.surrender(0,f.contract.defenders()));refuse(()->s.surrender(0,UUID.randomUUID()));
            same(h,bytes,f.path(),"Non-attacker nation surrendered someone else's siege");
            for(Action action:List.of(Action.READ,Action.TICK,Action.SURRENDER)) {
                f.denied.add(action);
                if(action==Action.READ)refuse(s::read);else if(action==Action.TICK)refuse(()->s.tick(0));else refuse(()->s.surrender(0,f.contract.attackers()));
                f.denied.remove(action);same(h,bytes,f.path(),"Unauthorized action changed journal");
            }
        }
        f.denied.add(Action.OPEN);refuse(f::open);f.denied.clear();
        try(Session s=f.open()) {byte[] bytes=Files.readAllBytes(f.path());f.denied.add(Action.RESUME);refuse(()->s.resume(0));same(h,bytes,f.path(),"Unauthorized resume rewrote epoch");}
        h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void owner_or_authority_revocation_before_write_preserves_original(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h);boolean[] armed={false};Fault revoke=point->{if(armed[0]&&point==SavePoint.BEFORE_WRITE)f.ownerValid=false;};
        try(Session s=f.create(revoke)) {
            byte[] before=Files.readAllBytes(f.path());f.ownerValid=false;refuse(s::read);same(h,before,f.path(),"Changed owner read repaired history");
            f.ownerValid=true;armed[0]=true;f.time++;refuse(()->s.tick(0));same(h,before,f.path(),"Owner revoked during save preparation still wrote progress");refuse(s::read);
        }
        f.ownerValid=true;try(Session s=f.open()){h.assertTrue(s.read().revision()==0&&s.read().snapshot().elapsedTicks()==0,"Revoked write published uncommitted progress");}h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void both_nations_online_is_required_before_first_journal(GameTestHelper h)throws Exception {
        for(int[] online:new int[][]{{0,0},{0,1},{1,0}}) {
            Fixture f=new Fixture(h);f.attackersOnline=online[0];f.defendersOnline=online[1];f.attackersInside=0;f.defendersInside=0;
            refuse(f::create);h.assertTrue(!Files.exists(f.path()),"Missing online nation left an authorized siege journal");
            f.attackersOnline=1;f.defendersOnline=1;try(Session s=f.create()){h.assertTrue(s.read().revision()==0,"Safe creation retry did not release prior lock");}
        }h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void stale_foreign_or_impossible_observation_never_reaches_disk(GameTestHelper h)throws Exception {
        for(int mode=0;mode<7;mode++) {
            Fixture f=new Fixture(h);try(Session s=f.create()) {
                byte[] before=Files.readAllBytes(f.path());f.reportMode=mode+1;f.time++;refuse(()->s.tick(0));same(h,before,f.path(),"Invalid report changed durable progress");
                h.assertTrue(s.read().snapshot().elapsedTicks()==0,"Invalid observation published memory-only progress");
            }
        }h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void failure_before_write_retains_old_state_and_closes_ambiguous_handle(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h);boolean[] armed={false};Fault fail=point->{if(armed[0]&&point==SavePoint.BEFORE_WRITE)throw new IOException("Injected before-write failure");};
        byte[] old;try(Session s=f.create(fail)) {
            old=Files.readAllBytes(f.path());armed[0]=true;f.time++;refuse(()->s.tick(0));refuse(s::read);same(h,old,f.path(),"Before-write failure changed original journal");
        }
        try(Session fresh=f.open()){View read=fresh.read();h.assertTrue(read.recoveryRequired()&&read.revision()==0&&read.snapshot().elapsedTicks()==0,"New handle adopted failed in-memory progress");}
        Fixture jumped=new Fixture(h);boolean[] jumpArmed={false};
        Fault jump=point->{if(jumpArmed[0]&&point==SavePoint.BEFORE_WRITE)jumped.time+=5;};
        try(Session s=jumped.create(jump)) {
            byte[] before=Files.readAllBytes(jumped.path());jumpArmed[0]=true;jumped.time++;
            refuse(()->s.tick(0));same(h,before,jumped.path(),"Clock change inside save hook persisted an observation from another frame");refuse(s::read);
        }
        try(Session fresh=jumped.open()){h.assertTrue(fresh.read().recoveryRequired()&&fresh.read().revision()==0,"Save-time clock drift adopted unpublished progress");}
        h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void failure_after_write_reopens_actual_committed_state_without_replay(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h);boolean[] armed={false};Fault fail=point->{if(armed[0]&&point==SavePoint.AFTER_WRITE)throw new IOException("Injected after-write failure");};
        byte[] committed;try(Session s=f.create(fail)) {
            byte[] old=Files.readAllBytes(f.path());armed[0]=true;f.time++;refuse(()->s.tick(0));refuse(s::read);
            committed=Files.readAllBytes(f.path());h.assertTrue(!Arrays.equals(old,committed),"After-write fault fixture did not actually commit new bytes");
        }
        try(Session fresh=f.open()) {
            View read=fresh.read();h.assertTrue(read.recoveryRequired()&&read.revision()==1&&read.snapshot().elapsedTicks()==1
                &&read.snapshot().outerProgress()==1,"New handle lost committed result or replayed failed action");
            same(h,committed,f.path(),"Readback rewrote ambiguous committed result");fresh.resume(read.revision());View next=f.tick(fresh);
            h.assertTrue(next.snapshot().elapsedTicks()==2&&next.snapshot().outerProgress()==2&&next.snapshot().townHallProgress()==0,"After-write recovery duplicated original observation");
        }h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void creation_collision_and_creation_save_failures_preserve_surviving_history(GameTestHelper h)throws Exception {
        Fixture collision=new Fixture(h);try(Session ignored=collision.create()){}byte[] existing=Files.readAllBytes(collision.path());refuse(collision::create);same(h,existing,collision.path(),"New create replaced retained city history");
        Fixture before=new Fixture(h);refuse(()->before.create(point->{if(point==SavePoint.BEFORE_WRITE)throw new IOException("Before-create fault");}));
        h.assertTrue(!Files.exists(before.path()),"Pre-write create fault left a journal");
        Fixture after=new Fixture(h);refuse(()->after.create(point->{if(point==SavePoint.AFTER_WRITE)throw new IOException("After-create fault");}));
        byte[] durable=Files.readAllBytes(after.path());refuse(after::create);same(h,durable,after.path(),"Retry after ambiguous create replaced surviving first history");
        try(Session recovered=after.open()){h.assertTrue(recovered.read().recoveryRequired()&&recovered.read().revision()==0,"After-create fault did not reopen complete initial state behind recovery gate");}
        h.succeed();
    }

    @GameTest(template="townhall_state_empty")
    public static void journal_lock_symlinks_and_nonfile_history_are_refused_without_cleanup(GameTestHelper h)throws Exception {
        Fixture journal=new Fixture(h);try(Session ignored=journal.create()){}Path original=journal.path();Path retained=original.resolveSibling(original.getFileName()+".retained-original");Files.move(original,retained);Files.createSymbolicLink(original,retained.getFileName());
        byte[] originalBytes=Files.readAllBytes(retained);refuse(journal::open);h.assertTrue(Files.isSymbolicLink(original),"Refusal deleted journal symlink");same(h,originalBytes,retained,"Symlink target changed");
        Fixture locked=new Fixture(h);Path lock=locked.lockPath();Files.createDirectories(lock.getParent());Path target=lock.resolveSibling(lock.getFileName()+".target");Files.writeString(target,"retained lock target",StandardOpenOption.CREATE_NEW);Files.createSymbolicLink(lock,target.getFileName());
        refuse(locked::create);h.assertTrue(Files.isSymbolicLink(lock)&&Files.readString(target).equals("retained lock target")&&!Files.exists(locked.path()),"Lock symlink refusal changed files");
        Fixture directory=new Fixture(h);Files.createDirectories(directory.path());refuse(directory::open);h.assertTrue(Files.isDirectory(directory.path()),"Nonfile journal was removed or replaced");h.succeed();
    }

    private interface Attempt {void run()throws Exception;}
    private static void refuse(Attempt action)throws Exception {
        try{action.run();}catch(IOException|IllegalArgumentException|IllegalStateException expected){return;}
        throw new AssertionError("Expected guarded siege-session refusal");
    }
    private static void same(GameTestHelper h,byte[] expected,Path path,String message)throws IOException{h.assertTrue(Arrays.equals(expected,Files.readAllBytes(path)),message);}
    private static void retain(Path path,String label,byte[] bytes)throws IOException {Files.write(path.resolveSibling(path.getFileName()+"."+label+"-evidence"),bytes,StandardOpenOption.CREATE_NEW);}
    private static Contract changedContract(Contract c,UUID siege,String world,UUID attackers,UUID defenders,Timing timing){return new Contract(siege,world,c.city(),attackers,defenders,timing);}
    private static final class Fixture implements Environment {
        final ServerLevel level;Contract contract;Profile profile;long time=100;
        final EnumSet<Action> denied=EnumSet.noneOf(Action.class);boolean ownerValid=true;
        int attackersOnline=1,defendersOnline=1,attackersInside=1,defendersInside,reportMode;
        Fixture(GameTestHelper h){this(h,20);}
        Fixture(GameTestHelper h,int total) {
            level=h.getLevel();String city="fixture38_"+UUID.randomUUID().toString().replace("-","");
            contract=new Contract(UUID.randomUUID(),"synthetic-session38-world",city,UUID.randomUUID(),UUID.randomUUID(),new Timing("synthetic-session38-v1",total,2,3,EmptyPolicy.HOLD));
            profile=new Profile(city,"synthetic-session38-geometry-v1",level.dimension().location().toString(),
                new Box(-900,140,-900,-896,144,-896),new Box(-880,140,-900,-876,144,-896));
        }
        public ServerLevel level(){return level;}public Contract contract(){return contract;}public Profile profile(){return profile;}public long gameTime(){return time;}
        public void guard(Action action,Snapshot state)throws IOException{if(!ownerValid||denied.contains(action))throw new IOException("Modeled actor/owner authorization revoked");}
        public Report observe(Snapshot state) {
            Contract c=reportMode==2?changedContract(contract,UUID.randomUUID(),contract.world(),contract.attackers(),contract.defenders(),contract.timing()):contract;
            Profile p=reportMode==3?new Profile(profile.city(),"foreign-profile",profile.dimension(),profile.outer(),profile.townHall()):profile;
            Stage stage=reportMode==5?(state.activeStage()==Stage.OUTER?Stage.TOWN_HALL:Stage.OUTER):state.activeStage();
            return new Report(time+(reportMode==1?1:0),p,c,state.elapsedTicks()+(reportMode==4?1:0),stage,
                reportMode==6?-1:attackersOnline,defendersOnline,reportMode==7?attackersOnline+1:attackersInside,defendersInside);
        }
        Path path()throws IOException{return SiegeSession38.journalPath(this);}
        Path lockPath()throws IOException{Path p=path();return p.resolveSibling(p.getFileName()+".lock");}
        Session create()throws IOException{return create(NONE);}
        Session create(Fault fault)throws IOException{return SiegeSession38.fixture(this,true,fault);}
        Session open()throws IOException{return SiegeSession38.fixture(this,false,NONE);}
        View tick(Session s)throws IOException{long revision=s.read().revision();time++;return s.tick(revision);}
    }
}
