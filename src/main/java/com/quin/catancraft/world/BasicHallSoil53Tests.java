package com.quin.catancraft.world;

import com.quin.catancraft.map.MapDefinitionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Actual pinned TH1->TH2->TH3 composition, actual vanilla source decay and
 * exposed regrowth. Funded isolated fixtures; no normal-time growth claim. */
@GameTestHolder("catancraft_basic_hall_soil53") @PrefixGameTestTemplate(false)
public final class BasicHallSoil53Tests {
    private BasicHallSoil53Tests() {}
    private static BasicHallSoil53.Site site(ConstructionSafetyTests.F f)throws Exception{return BasicHallSoil53.site(MapDefinitionManager.territory(f.request.city()));}
    private static BasicConstructionPurchase.Request child(ConstructionSafetyTests.F f){return new BasicConstructionPurchase.Request(f.request.city(),f.actor,f.owner,"hall","th3","town_hall",2,3);}
    private static Path childPath(ConstructionSafetyTests.F f){return BasicConstructionPurchase.file(f.dir,child(f));}
    private static void prepareChild(ConstructionSafetyTests.F f)throws Exception{BasicConstructionPurchase.fixturePrepare(f.level.getServer(),f.data,f.dir,child(f),f::save);}
    private static PaidConstructionRecovery.Environment childEnv(ConstructionSafetyTests.F f)throws Exception{return BasicConstructionPurchase.fixture(f.level.getServer(),f.data,f.dir,childPath(f),f::save);}
    private static void decay(ConstructionSafetyTests.F f)throws Exception {
        var s=site(f);f.h.assertTrue(f.level.getBlockState(s.position()).is(Blocks.GRASS_BLOCK)
                &&f.level.getBlockState(s.position().above()).equals(s.cover()),"Native decay source/log missing");
        Blocks.GRASS_BLOCK.randomTick(f.level.getBlockState(s.position()),f.level,s.position(),RandomSource.create(53062));
        f.h.assertTrue(f.level.getBlockState(s.position()).equals(Blocks.DIRT.defaultBlockState()),"Actual vanilla retained-soil decay did not occur");
    }
    private static byte[] parent(ConstructionSafetyTests.F f,boolean late,boolean legacy)throws Exception {
        if(!late)decay(f);f.prepare();
        if(legacy){CompoundTag n=ProducerJournal.read(f.original());n.remove(BasicHallSoil53.POLICY_FIELD);ProducerJournal.write(f.original(),n,true);}
        byte[] original=Files.readAllBytes(f.original());
        if(late){PaidRecoveryTests.interrupt(f.h,f.env(),"prepared",-1);decay(f);}
        byte[] observation=null;Path sidecar=BasicHallSoil53.observation(f.original());
        if(late&&!legacy) {
            CompoundTag unpaid=f.data.save(new CompoundTag());
            PaidRecoveryTests.interrupt(f.h,f.env(),"physical_saved",-1);
            f.h.assertTrue(f.level.getBlockState(site(f).position().above()).isAir()
                    &&f.level.getBlockState(site(f).position()).is(Blocks.DIRT)&&unpaid.equals(f.data.save(new CompoundTag())),
                    "Late observation interruption did not preserve unpaid exposed dirt");
            observation=Files.readAllBytes(sidecar);f.reload();
            f.h.assertTrue(f.territory().cityLevel()==1,"Physical interruption unexpectedly paid the parent");
        }
        f.complete();f.reload();var s=site(f);
        f.h.assertTrue(observation==null||Arrays.equals(observation,Files.readAllBytes(sidecar)),"Restart after source-log removal rewrote the observation");
        f.h.assertTrue(f.level.getBlockState(s.position()).is(Blocks.DIRT)&&f.level.getBlockState(s.position().above()).isAir(),"TH2 failed to retain its actual exposed soil");
        f.h.assertTrue(Arrays.equals(original,Files.readAllBytes(f.original())),"Parent before-image rewritten");return original;
    }
    private static void chain(GameTestHelper h,String city,boolean late,boolean legacy)throws Exception {
        ConstructionSafetyTests.with(h,city,"th2","town_hall",1,f->{
            long cash=f.nation().treasury();CompoundTag book=f.level.getBlockEntity(f.plan.manager()).saveWithFullMetadata().copy();
            byte[] original=parent(f,late,legacy),companion=Files.readAllBytes(PaidConstructionRecovery.companion(f.original()));
            Path sidecar=BasicHallSoil53.observation(f.original());byte[] observation=Files.exists(sidecar)?Files.readAllBytes(sidecar):null;
            h.assertTrue((observation!=null)==late,"Unexpected late observation provenance");
            if(observation!=null)BasicHallSoil53.validateCompletedObservation(f.dir,f.data,MapDefinitionManager.territory(city),sidecar);
            h.assertTrue(BasicHallSoil53.completedDirt(f.dir,f.data,MapDefinitionManager.territory(city)).equals(Set.of(site(f).position())),"Missing completed parent soil authority");
            prepareChild(f);byte[] childBytes=Files.readAllBytes(childPath(f));CompoundTag intent=ProducerJournal.read(childBytes);
            h.assertTrue(intent.contains(BasicHallSoil53.FIELD,Tag.TAG_COMPOUND)
                    &&BasicHallSoil53.captured(intent.getCompound("snapshot"),site(f).index()).is(Blocks.DIRT),"Child did not retain actual soil and its basis");
            PaidRecoveryTests.interrupt(h,childEnv(f),"write",37);f.reload();
            PaidRecoveryTests.interrupt(h,childEnv(f),"ledger_saved",-1);f.reload();
            h.assertTrue(f.territory().cityLevel()==3,"Saved child settlement not reached");
            PaidConstructionRecovery.resume(childEnv(f));f.reload();
            h.assertTrue(f.level.getBlockState(site(f).position()).equals(Blocks.STONE_BRICKS.defaultBlockState()),"Inherited dirt survived structural TH3 target");
            h.assertTrue(BasicHallSoil53.completedDirt(f.dir,f.data,MapDefinitionManager.territory(city)).isEmpty(),"TH2 dirt authority escaped its tier");
            if(observation!=null)BasicHallSoil53.validateCompletedObservation(f.dir,f.data,MapDefinitionManager.territory(city),sidecar);
            long expected=cash-BasicConstructionPurchase.price(f.request).money()-BasicConstructionPurchase.price(child(f)).money();
            h.assertTrue(f.nation().treasury()==expected&&book.equals(f.level.getBlockEntity(f.plan.manager()).saveWithFullMetadata()),"Composed Hall price/book changed");
            CompoundTag paid=f.data.save(new CompoundTag());h.assertTrue(PaidConstructionRecovery.resume(childEnv(f)).writes()==0&&paid.equals(f.data.save(new CompoundTag())),"Child completion repeated charge/write");
            h.assertTrue(Arrays.equals(original,Files.readAllBytes(f.original()))&&Arrays.equals(companion,Files.readAllBytes(PaidConstructionRecovery.companion(f.original())))
                    &&Arrays.equals(childBytes,Files.readAllBytes(childPath(f)))&&(observation==null||Arrays.equals(observation,Files.readAllBytes(sidecar))),"Composed purchase rewrote immutable history");
            com.mojang.logging.LogUtils.getLogger().info("HALL SOIL53 COMPOSITION city={} actualNativeDecay=true lateObservation={} legacyParent={} tier=3 paidExactlyOnce=true",city,late,legacy);h.succeed();
        });
    }
    @GameTest(template="townhall_state_empty",batch="hallsoil53_01",timeoutTicks=14000)
    public static void legacy_captured_dirt_north_composes_and_recovers_after_payment(GameTestHelper h)throws Exception{chain(h,"a",false,true);}
    @GameTest(template="townhall_state_empty",batch="hallsoil53_02",timeoutTicks=14000)
    public static void captured_dirt_south_composes_and_recovers_after_payment(GameTestHelper h)throws Exception{chain(h,"i",false,false);}
    @GameTest(template="townhall_state_empty",batch="hallsoil53_03",timeoutTicks=14000)
    public static void late_native_decay_north_records_once_before_cover_removal(GameTestHelper h)throws Exception{chain(h,"a",true,false);}
    @GameTest(template="townhall_state_empty",batch="hallsoil53_04",timeoutTicks=14000)
    public static void late_native_decay_south_records_once_before_cover_removal(GameTestHelper h)throws Exception{chain(h,"i",true,false);}

    @GameTest(template="townhall_state_empty",batch="hallsoil53_05",timeoutTicks=14000)
    public static void legacy_unobserved_late_decay_never_gains_new_authority(GameTestHelper h)throws Exception {
        ConstructionSafetyTests.with(h,"a","th2","town_hall",1,f->{
            byte[] original=parent(f,true,true);CompoundTag ledger=f.data.save(new CompoundTag());
            h.assertTrue(BasicHallSoil53.completedDirt(f.dir,f.data,MapDefinitionManager.territory("a")).isEmpty(),"Legacy missing observation was invented");
            PaidRecoveryTests.refused(h,()->prepareChild(f),"Construction source conflict");
            h.assertTrue(!Files.exists(BasicHallSoil53.observation(f.original()))&&!Files.exists(childPath(f))
                    &&ledger.equals(f.data.save(new CompoundTag()))&&Arrays.equals(original,Files.readAllBytes(f.original())),"Readonly legacy refusal changed history/economy");h.succeed();
        });
    }
    @GameTest(template="townhall_state_empty",batch="hallsoil53_06",timeoutTicks=14000)
    public static void no_parent_and_outside_mask_dirt_remain_strict(GameTestHelper h)throws Exception {
        ConstructionSafetyTests.with(h,"i","th3","town_hall",2,f->{
            BlockPos p=site(f).position();f.level.setBlock(p,Blocks.DIRT.defaultBlockState(),ConstructionSafetyTests.FLAGS);
            CompoundTag ledger=f.data.save(new CompoundTag());PaidRecoveryTests.refused(h,f::prepare,"Construction source conflict");
            h.assertTrue(!Files.exists(f.original())&&ledger.equals(f.data.save(new CompoundTag())),"Absent parent authorized dirt");
            f.level.setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),ConstructionSafetyTests.FLAGS);
            var expected=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();for(var c:f.plan.source())expected.put(c.position(),c.state());
            BlockPos other=f.plan.source().stream().filter(c->!c.position().equals(p)&&c.state().is(Blocks.GRASS_BLOCK)&&expected.get(c.position().above()).isAir()).findFirst().orElseThrow().position();
            f.level.setBlock(other,Blocks.DIRT.defaultBlockState(),ConstructionSafetyTests.FLAGS);PaidRecoveryTests.refused(h,f::prepare,"Construction source conflict");
            h.assertTrue(!Files.exists(f.original())&&ledger.equals(f.data.save(new CompoundTag())),"Arbitrary exposed dirt authorized");h.succeed();
        });
    }
    @GameTest(template="townhall_state_empty",batch="hallsoil53_07",timeoutTicks=14000)
    public static void unrelated_conflict_and_wrong_log_cannot_mint_observation(GameTestHelper h)throws Exception {
        ConstructionSafetyTests.with(h,"a","th2","town_hall",1,f->{
            f.prepare();byte[] original=Files.readAllBytes(f.original());decay(f);var s=site(f);CompoundTag ledger=f.data.save(new CompoundTag());
            var last=f.plan.source().get(f.plan.source().size()-1);f.level.setBlock(last.position(),Blocks.GOLD_BLOCK.defaultBlockState(),ConstructionSafetyTests.FLAGS);
            PaidRecoveryTests.refused(h,f::complete,"Construction source conflict");
            h.assertTrue(!Files.exists(BasicHallSoil53.observation(f.original()))&&!Files.exists(PaidConstructionRecovery.companion(f.original())),"Unrelated preflight conflict minted observation/history");
            f.level.setBlock(last.position(),last.state(),ConstructionSafetyTests.FLAGS);
            f.level.setBlock(s.position().above(),s.cover().setValue(RotatedPillarBlock.AXIS,net.minecraft.core.Direction.Axis.X),ConstructionSafetyTests.FLAGS);
            PaidRecoveryTests.refused(h,f::complete,"Construction source conflict");
            h.assertTrue(!Files.exists(BasicHallSoil53.observation(f.original()))&&ledger.equals(f.data.save(new CompoundTag()))&&Arrays.equals(original,Files.readAllBytes(f.original())),"Wrong log minted observation/payment");
            f.level.setBlock(s.position().above(),s.cover(),ConstructionSafetyTests.FLAGS);PaidRecoveryTests.interrupt(h,f.env(),"prepared",-1);
            Path observation=BasicHallSoil53.observation(f.original());byte[] proof=Files.readAllBytes(observation);
            PaidRecoveryTests.interrupt(h,f.env(),"prepared",-1);h.assertTrue(Arrays.equals(proof,Files.readAllBytes(observation)),"Repeated observation rewrote its original bytes");h.succeed();
        });
    }
    @GameTest(template="townhall_state_empty",batch="hallsoil53_08",timeoutTicks=14000)
    public static void missing_observation_after_log_removal_refuses_without_invention(GameTestHelper h)throws Exception {
        ConstructionSafetyTests.with(h,"i","th2","town_hall",1,f->{
            f.prepare();PaidRecoveryTests.interrupt(h,f.env(),"physical_saved",-1);var s=site(f);
            h.assertTrue(f.level.getBlockState(s.position()).is(Blocks.GRASS_BLOCK)&&f.level.getBlockState(s.position().above()).isAir(),"Physical target not reached");
            byte[] original=Files.readAllBytes(f.original()),companion=Files.readAllBytes(PaidConstructionRecovery.companion(f.original()));CompoundTag ledger=f.data.save(new CompoundTag());
            f.level.setBlock(s.position(),Blocks.DIRT.defaultBlockState(),ConstructionSafetyTests.FLAGS);
            PaidRecoveryTests.refused(h,f::complete,"lacks an observed source log");
            h.assertTrue(!Files.exists(BasicHallSoil53.observation(f.original()))&&ledger.equals(f.data.save(new CompoundTag()))
                    &&Arrays.equals(original,Files.readAllBytes(f.original()))&&Arrays.equals(companion,Files.readAllBytes(PaidConstructionRecovery.companion(f.original()))),"Missing old observation was invented or charged");h.succeed();
        });
    }
    @GameTest(template="townhall_state_empty",batch="hallsoil53_09",timeoutTicks=14000)
    public static void parent_identity_commit_receipt_snapshot_and_child_proof_are_bound(GameTestHelper h)throws Exception {
        ConstructionSafetyTests.with(h,"a","th2","town_hall",1,f->{
            byte[] original=parent(f,false,false);Path cp=PaidConstructionRecovery.companion(f.original());byte[] companion=Files.readAllBytes(cp);CompoundTag ledger=f.data.save(new CompoundTag());
            for(String fault:List.of("phase","identity","unknown","originalHash")) {
                CompoundTag n=ProducerJournal.read(companion);
                if(fault.equals("phase"))n.putString("phase","PHYSICAL_COMPLETE");
                if(fault.equals("identity"))n.getCompound("identity").putString("city","b");
                if(fault.equals("unknown"))n.putInt("unrecognized",1);
                if(fault.equals("originalHash"))n.putString("originalSHA256","0".repeat(64));
                ProducerJournal.write(cp,n,true);byte[] changed=Files.readAllBytes(cp);
                PaidRecoveryTests.refused(h,()->prepareChild(f),"not paid and committed");
                h.assertTrue(!Files.exists(childPath(f))&&ledger.equals(f.data.save(new CompoundTag()))&&Arrays.equals(changed,Files.readAllBytes(cp)),"Invalid parent was repaired or acted on");Files.write(cp,companion);
            }
            UUID id=ProducerJournal.read(original).getUUID("id");CompoundTag withoutReceipt=f.data.save(new CompoundTag());withoutReceipt.getCompound("placedMapAssets").remove(PaidConstructionRecovery.receiptKey(id));
            f.data=com.quin.catancraft.data.CatanSavedData.load(withoutReceipt);
            PaidRecoveryTests.refused(h,()->prepareChild(f),"not paid and committed");PaidConstructionRecovery.receipt(f.data,id);
            f.territory().setOwnerNationId(UUID.randomUUID());PaidRecoveryTests.refused(h,()->BasicHallSoil53.completedDirt(f.dir,f.data,MapDefinitionManager.territory("a")),"Foreign retained Hall soil parent");f.territory().setOwnerNationId(f.owner);
            for(String fault:List.of("world","city","assets","source-cover")) {
                CompoundTag n=ProducerJournal.read(original);String message="Foreign retained Hall soil parent";
                if(fault.equals("world"))n.putString("world",f.dir+"-foreign");
                if(fault.equals("city"))n.getCompound("request").putString("city","b");
                if(fault.equals("assets")){n.getCompound("request").putString("assets","0".repeat(64));message="Malformed basic-purchase contract";}
                if(fault.equals("source-cover")) {
                    CompoundTag snapshot=n.getCompound("snapshot");ListTag palette=snapshot.getList("palette",Tag.TAG_COMPOUND);int air=palette.size();palette.add(NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));
                    int[] states=snapshot.getIntArray("states");states[site(f).aboveIndex()]=air;snapshot.putIntArray("states",states);message="lacks its exact source cover";
                }
                ProducerJournal.write(f.original(),n,true);CompoundTag rebound=ProducerJournal.read(companion);rebound.putString("originalSHA256",TownHallRevisions.sha(Files.readAllBytes(f.original())));ProducerJournal.write(cp,rebound,true);
                final String expected=message;PaidRecoveryTests.refused(h,()->prepareChild(f),expected);
                h.assertTrue(!Files.exists(childPath(f)),"Malformed parent created child");Files.write(f.original(),original);Files.write(cp,companion);
            }
            prepareChild(f);byte[] child=Files.readAllBytes(childPath(f));CompoundTag changed=ProducerJournal.read(child);changed.getCompound(BasicHallSoil53.FIELD).putString("parentOriginalSHA256","f".repeat(64));ProducerJournal.write(childPath(f),changed,true);
            PaidRecoveryTests.refused(h,()->PaidConstructionRecovery.resume(childEnv(f)),"child proof changed");
            h.assertTrue(!Files.exists(PaidConstructionRecovery.companion(childPath(f)))&&Arrays.equals(original,Files.readAllBytes(f.original()))&&Arrays.equals(companion,Files.readAllBytes(cp)),"Bad child proof changed parent/history");h.succeed();
        });
    }
    @GameTest(template="townhall_state_empty",batch="hallsoil53_10",timeoutTicks=14000)
    public static void late_observation_digest_and_schema_are_immutable_child_basis(GameTestHelper h)throws Exception {
        ConstructionSafetyTests.with(h,"i","th2","town_hall",1,f->{
            byte[] original=parent(f,true,false);prepareChild(f);Path sidecar=BasicHallSoil53.observation(f.original());byte[] bytes=Files.readAllBytes(sidecar);
            CompoundTag observation=ProducerJournal.read(bytes);observation.putInt("unknown",1);ProducerJournal.write(sidecar,observation,true);
            PaidRecoveryTests.refused(h,()->PaidConstructionRecovery.resume(childEnv(f)),"Invalid retained Hall soil observation");
            h.assertTrue(!Files.exists(PaidConstructionRecovery.companion(childPath(f)))&&f.territory().cityLevel()==2,"Invalid observation started child purchase");
            Files.write(sidecar,bytes);CompoundTag child=ProducerJournal.read(childPath(f));child.getCompound(BasicHallSoil53.FIELD).putString("observationSHA256","0".repeat(64));ProducerJournal.write(childPath(f),child,true);
            PaidRecoveryTests.refused(h,()->PaidConstructionRecovery.resume(childEnv(f)),"child proof changed");
            h.assertTrue(Arrays.equals(original,Files.readAllBytes(f.original()))&&Arrays.equals(bytes,Files.readAllBytes(sidecar)),"Observation mismatch rewrote parent evidence");h.succeed();
        });
    }

    private static void regrowth(GameTestHelper h,String city,boolean childAlreadyPrepared)throws Exception {
        ConstructionSafetyTests.with(h,city,"th2","town_hall",1,f->{
            byte[] original=parent(f,false,false);if(childAlreadyPrepared)prepareChild(f);
            f.level.setDayTime(6000);var lease=SectorChunkLease.acquire(f.level,BasicConstructionPurchase.bounds(f.request,MapDefinitionManager.territory(city)));
            awaitRegrowth(h,f,original,childAlreadyPrepared,lease,0);
        });
    }
    private static void awaitRegrowth(GameTestHelper h,ConstructionSafetyTests.F f,byte[] original,boolean childAlreadyPrepared,SectorChunkLease lease,int attempt) {
        h.runAfterDelay(attempt==0?20:2,()->{try {
            lease.keepAlive();BlockPos p=site(f).position();List<BlockPos> sources=new ArrayList<>();
            for(int x=-1;x<=1;x++)for(int y=-1;y<=3;y++)for(int z=-1;z<=1;z++) {
                BlockPos source=p.offset(x,y,z);if(f.level.getBlockState(source).is(Blocks.GRASS_BLOCK)&&f.level.getBlockState(source.above()).isAir()
                        &&f.level.getMaxLocalRawBrightness(source.above())>=9)sources.add(source);
            }
            boolean lit=lease.ready()&&f.level.getMaxLocalRawBrightness(p.above())>=9&&!sources.isEmpty();
            if(!lit&&attempt<100){awaitRegrowth(h,f,original,childAlreadyPrepared,lease,attempt+1);return;}
            h.assertTrue(lit,"Native retained-soil regrowth lighting/source did not settle");RandomSource random=RandomSource.create(5362);
            int sweeps=0;while(!f.level.getBlockState(p).is(Blocks.GRASS_BLOCK)&&sweeps++<512)for(BlockPos source:sources)
                Blocks.GRASS_BLOCK.randomTick(f.level.getBlockState(source),f.level,source,random);
            h.assertTrue(f.level.getBlockState(p).equals(Blocks.GRASS_BLOCK.defaultBlockState()),"Native retained-soil exposed regrowth did not occur");
            h.assertTrue(BasicHallSoil53.completedDirt(f.dir,f.data,MapDefinitionManager.territory(f.request.city())).equals(Set.of(p)),"Regrowth destroyed unchanged historical authority");
            if(!childAlreadyPrepared) {
                prepareChild(f);h.assertTrue(!ProducerJournal.read(childPath(f)).contains(BasicHallSoil53.FIELD),"Captured GRASS acquired inherited DIRT permission");
                f.level.setBlock(p,Blocks.DIRT.defaultBlockState(),ConstructionSafetyTests.FLAGS);
                PaidRecoveryTests.refused(h,()->PaidConstructionRecovery.resume(childEnv(f)),"Construction source conflict");
                f.level.setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),ConstructionSafetyTests.FLAGS);
            }
            PaidConstructionRecovery.resume(childEnv(f));h.assertTrue(f.level.getBlockState(p).is(Blocks.STONE_BRICKS)&&f.territory().cityLevel()==3,"Regrown valid source blocked TH3");
            h.assertTrue(Arrays.equals(original,Files.readAllBytes(f.original()))&&!Files.exists(BasicHallSoil53.observation(f.original())),"Regrowth rewrote original or minted an observation");
            com.mojang.logging.LogUtils.getLogger().info("HALL SOIL53 NATIVE REGROWTH city={} afterChildIntent={} sweeps={} target=stone_bricks",f.request.city(),childAlreadyPrepared,sweeps);
            lease.close();h.succeed();
        }catch(Exception e){lease.close();throw new RuntimeException(e);}});
    }
    @GameTest(template="townhall_state_empty",batch="hallsoil53_11",timeoutTicks=14000)
    public static void native_exposed_regrowth_before_child_capture_stays_strict(GameTestHelper h)throws Exception{regrowth(h,"a",false);}
    @GameTest(template="townhall_state_empty",batch="hallsoil53_12",timeoutTicks=14000)
    public static void native_exposed_regrowth_after_child_capture_recovers(GameTestHelper h)throws Exception{regrowth(h,"i",true);}
    @GameTest(template="townhall_state_empty",batch="hallsoil53_13",timeoutTicks=5000)
    public static void unique_pinned_soil_transition_is_qualified_in_all_sixteen_cities(GameTestHelper h)throws Exception {
        Set<BlockPos> sites=new HashSet<>();for(char id='a';id<='p';id++) {
            var def=MapDefinitionManager.territory(String.valueOf(id));var s=BasicHallSoil53.site(def);sites.add(s.position());
            var anchor=SchematicAssetRegistry.territoryAnchor("th2",def);var one=BasicConstructionAssets.plan("th1",anchor);var two=BasicConstructionAssets.plan("th2",anchor);var three=BasicConstructionAssets.plan("th3",anchor);
            h.assertTrue(one.get(s.index()).position().equals(s.position())&&one.get(s.index()).state().is(Blocks.GRASS_BLOCK)
                    &&one.get(s.aboveIndex()).state().equals(s.cover())&&two.get(s.index()).state().is(Blocks.GRASS_BLOCK)
                    &&two.get(s.aboveIndex()).state().isAir()&&three.get(s.index()).state().is(Blocks.STONE_BRICKS),"Pinned soil transform mismatch in "+id);
        }
        h.assertTrue(sites.size()==16,"Hall soil site alias across cities");h.succeed();
    }
}
