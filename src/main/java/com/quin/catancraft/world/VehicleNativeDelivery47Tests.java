package com.quin.catancraft.world;

import com.mojang.authlib.GameProfile;
import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.command.NationProcurement;
import com.quin.catancraft.data.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;

import static com.quin.catancraft.world.VehicleDelivery46.*;
import com.quin.catancraft.world.VehicleDelivery46.Record;

/** Real SBW entities, native nation saves and entity-region readback in marked disposable worlds.
 * Platforms are synthetic transaction fixtures, not a certificate for the authored factory exits.
 * The restart scenario restores original entity regions; it never imports/reconstructs entity NBT.
 */
@GameTestHolder("catancraft_vehicle47") @PrefixGameTestTemplate(false)
public final class VehicleNativeDelivery47Tests {
    private static final String NS="catancraft_vehicle47", RESTART=NS+"_restart", DIM="minecraft:overworld";
    private static final long FUNDS=100_000, STOCK=1000;
    private static final int FLAGS=2|16|32;
    private static final VehicleNativeDelivery47.Hooks NONE=p->{};
    private interface Work {void run(Fixture f)throws Exception;}
    private interface Checked {void run()throws Exception;}

    @GameTest(template="townhall_state_empty",batch="vehicle47_prices",timeoutTicks=1200)
    public static void actual_original_prices_tags_and_completed_loss(GameTestHelper h)throws Exception {
        run(h,"prices",0,f->{
            Intent pickup=f.order("pickup",0),lav=f.order("lav150",1);
            for(Intent i:List.of(pickup,lav)){
                f.prepare(i);check(f.execute(i).complete(),"Native delivery did not complete");f.assertNative(i);f.crowbar(i);f.snapshot(i);
            }
            f.charges(pickup,lav);f.count(2);f.assertDisk();
            for(Intent i:List.of(pickup,lav))f.level.getEntity(i.entity()).discard();f.save();
            f.authorized=false;
            for(Intent i:List.of(pickup,lav))for(int n=0;n<3;n++)check(f.execute(i).complete(),"Completed native loss ceased to be settled");
            f.count(0);f.charges(pickup,lav);f.assertDisk();f.finish("originalPricesOnce=2 nativeTags=2 crowbarRoutes=12 completedLossNoReplacement=2");
        });
    }

    @GameTest(template="townhall_state_empty",batch="vehicle47_debit",timeoutTicks=1200)
    public static void native_debit_and_spawn_markers_survive_adapter_recreation(GameTestHelper h)throws Exception {
        run(h,"debit",1,f->{
            Intent debit=f.order("pickup",0);f.prepare(debit);
            f.interrupt(debit,Phase.PAID);check(f.journal().read(debit.id()).phase()==Phase.DEBIT_ATTEMPTED,"Lost durable debit-attempt phase");
            f.charges(debit);f.count(0);f.assertDisk();
            check(f.execute(debit).complete(),"Fresh native adapter did not reconcile original saved debit");f.assertNative(debit);f.snapshot(debit);

            Intent spawn=f.order("lav150",1);f.prepare(spawn);
            Environment beforeAdd=f.environment(f.journal(),p->{if(p==VehicleNativeDelivery47.SavePoint.BEFORE_ENTITY_ADD)throw new IOException("Injected before actual entity add");});
            check(!VehicleDelivery46.execute(f.journal(),spawn.id(),beforeAdd).complete(),"Failed native add unexpectedly completed");
            check(f.journal().read(spawn.id()).phase()==Phase.SPAWN_ATTEMPTED,"Native add preceded spawn marker");
            for(int n=0;n<3;n++)check(!f.execute(spawn).complete(),"Unknown attempted native spawn was retried");
            f.count(1);f.charges(debit,spawn);f.assertDisk();

            Intent authority=f.order("pickup",2);f.prepare(authority);f.interrupt(authority,Phase.SPAWN_ATTEMPTED);
            check(f.journal().read(authority.id()).phase()==Phase.PAID,"Failed spawn marker changed paid phase");
            f.authorized=false;refuse(()->f.execute(authority));f.count(1);f.charges(debit,spawn,authority);
            f.authorized=true;check(f.execute(authority).complete(),"Restored original authority failed first native spawn");
            f.assertNative(authority);f.snapshot(authority);f.count(2);f.charges(debit,spawn,authority);f.assertDisk();
            f.finish("durableDebitReconciled=1 missingAttemptHeld=1 changedAuthorityHeld=1 originalPricesOnce=3");
        });
    }

    @GameTest(template="townhall_state_empty",batch="vehicle47_save",timeoutTicks=1200)
    public static void real_entity_save_and_completion_boundaries_use_readback(GameTestHelper h)throws Exception {
        run(h,"save",2,f->{
            Intent unsaved=f.order("pickup",0);f.prepare(unsaved);
            Environment beforeSave=f.environment(f.journal(),p->{if(p==VehicleNativeDelivery47.SavePoint.BEFORE_ENTITY_SAVE)throw new IOException("Injected before native entity save");});
            check(!VehicleDelivery46.execute(f.journal(),unsaved.id(),beforeSave).complete(),"Unsaved native entity was treated as durable");
            check(f.level.getEntity(unsaved.entity())!=null&&f.journal().read(unsaved.id()).phase()==Phase.SPAWN_ATTEMPTED,"Native add/save boundary was not exercised");
            check(!f.execute(unsaved).complete(),"Unverified native entity authorized completion");
            f.save();check(f.execute(unsaved).complete(),"Real external save did not permit exact original-entity reconciliation");f.assertNative(unsaved);f.snapshot(unsaved);

            Intent saved=f.order("lav150",1);f.prepare(saved);
            Environment afterSave=f.environment(f.journal(),p->{if(p==VehicleNativeDelivery47.SavePoint.AFTER_LEDGER_SAVE||p==VehicleNativeDelivery47.SavePoint.AFTER_ENTITY_SAVE)throw new IOException("Injected callback failure after native save");});
            check(VehicleDelivery46.execute(f.journal(),saved.id(),afterSave).complete(),"Durable native saves were ignored after callback errors");f.assertNative(saved);f.snapshot(saved);

            Intent settled=f.order("pickup",2);f.prepare(settled);f.interrupt(settled,Phase.COMPLETE);
            check(f.journal().read(settled.id()).phase()==Phase.SPAWN_ATTEMPTED,"Settlement failure erased original spawn phase");
            f.assertNative(settled);check(f.execute(settled).complete(),"Fresh adapter could not settle saved original entity");f.snapshot(settled);
            f.count(3);f.charges(unsaved,saved,settled);f.assertDisk();f.finish("unsavedHeld=1 durableCallbackErrors=2 settlementReconciled=1 originalNativeEntities=3");
        });
    }

    @GameTest(template="townhall_state_empty",batch="vehicle47_holds",timeoutTicks=1200)
    public static void changed_provenance_moved_or_missing_native_entities_stay_held(GameTestHelper h)throws Exception {
        run(h,"holds",3,f->{
            Intent changed=f.order("pickup",0),moved=f.order("lav150",1),gone=f.order("pickup",2);
            f.prepare(changed);
            NationData other=f.data.createNation("Vehicle47 ambiguous member control",UUID.randomUUID());other.addMember(f.player.getUUID());f.save();
            CompoundTag before=f.nation.save().copy(),otherBefore=other.save().copy();
            refuse(()->VehicleNativeDelivery47.newIntent(f.player,"pickup",changed.bay()));
            refuse(()->f.execute(changed));refuse(()->VehiclePayment47.debit(f.level,changed,NONE));
            check(before.equals(f.nation.save())&&otherBefore.equals(other.save())&&f.data.placedMapAsset(VehicleNativeDelivery47.receiptKey(changed.id()))==null&&f.journal().read(changed.id()).phase()==Phase.PREPARED,"Ambiguous actor membership mutated money, receipt, or phase");
            other.removeMember(f.player.getUUID());f.save();
            for(Intent i:List.of(changed,moved,gone)){f.prepare(i);f.interrupt(i,Phase.COMPLETE);f.assertNative(i);f.snapshot(i);}
            Entity rolled=f.level.getEntity(changed.entity());check(VehicleNativeDelivery47.matches(rolled,changed),"Original native pose was not fixed before roll probe");
            rolled.getClass().getMethod("setRoll",float.class).invoke(rolled,15f);
            check(!f.execute(changed).complete()&&f.journal().read(changed.id()).phase()==Phase.SPAWN_ATTEMPTED,"Native roll change was treated as original fixed pose");
            rolled.getClass().getMethod("setRoll",float.class).invoke(rolled,0f);
            rolled.getClass().getMethod("setPrevRoll",float.class).invoke(rolled,15f);
            check(!f.execute(changed).complete(),"Native interpolated roll change was treated as original fixed pose");
            rolled.getClass().getMethod("setPrevRoll",float.class).invoke(rolled,0f);
            check(VehicleNativeDelivery47.matches(rolled,changed),"Roll probe did not restore exact original fixed pose");
            malformedRegionRefusals(f,changed);
            f.level.getEntity(changed.entity()).getPersistentData().putString(NationProcurement.NATION_TAG,UUID.randomUUID().toString());
            Entity e=f.level.getEntity(moved.entity());e.setPos(e.getX()+8,e.getY(),e.getZ());f.level.getEntity(gone.entity()).discard();f.save();
            for(Intent i:List.of(changed,moved,gone)){
                for(int n=0;n<3;n++)check(!f.execute(i).complete(),"Changed/moved/missing native entity was settled or replaced");
                check(f.journal().read(i.id()).phase()==Phase.SPAWN_ATTEMPTED,"Held delivery phase was rewritten");
            }
            f.count(2);check(f.level.getEntity(gone.entity())==null,"Missing native entity was recreated");
            f.charges(changed,moved,gone);f.assertDisk();f.finish("wrongProvenanceHeld=1 movedHeld=1 missingHeld=1 replacements=0 refunds=0 rawParserRefusals=13 rawParserControls=3 ambiguousMembershipRefusals=3 nativeRollHolds=2");
        });
    }

    @GameTestHolder("catancraft_vehicle47_restart") @PrefixGameTestTemplate(false)
    public static final class Restart {
        @GameTest(template="townhall_state_empty",batch="vehicle47_restart",timeoutTicks=1800)
        public static void native_regions_and_original_ledger_survive_separate_jvms(GameTestHelper h)throws Exception {
            String phase=System.getProperty("catancraft.vehicle47RestartPhase","");check(phase.equals("seed")||phase.equals("verify"),"Explicit vehicle47RestartPhase=seed|verify required");
            Fixture f=new Fixture(h,"restart",4,phase);
            await(f,0,()->{if(phase.equals("seed"))seed(f);else verify(f);});
        }
    }

    private static void seed(Fixture f)throws Exception {
        f.initialize();Intent complete=f.order("pickup",0),paid=f.order("lav150",1),pending=f.order("pickup",2);
        f.prepare(complete);check(f.execute(complete).complete(),"Restart completed seed delivery failed");
        f.prepare(paid);f.interrupt(paid,Phase.SPAWN_ATTEMPTED);
        f.prepare(pending);f.interrupt(pending,Phase.COMPLETE);
        f.charges(complete,paid,pending);f.count(2);f.assertDisk();
        // Let real vehicle physics settle. No NoGravity, manual import or rewritten intent.
        f.h.runAfterDelay(40,()->{try{
            f.lease.keepAlive();Entity body=f.level.getEntity(pending.entity());check(body!=null,"Original pending native body disappeared during settling");
            body.setPos(body.getX()+8,body.getY(),body.getZ());f.save();
            check(!f.execute(pending).complete(),"Moved pending native body was settled instead of held");
            f.charges(complete,paid,pending);f.count(2);f.assertDisk();
            CompoundTag proof=f.metadata();proof.putString("result","SEEDED_PENDING_DISTINCT_JVM_VERIFICATION");proof.putUUID("actor",f.player.getUUID());proof.putUUID("nation",f.nation.id());
            proof.put("nationBefore",f.original.copy());proof.put("nationAfter",f.nation.save());proof.put("nativeLedger",f.disk());
            ListTag rows=new ListTag();for(Intent i:List.of(complete,paid,pending))rows.add(f.row(i));proof.put("orders",rows);
            proof.put("journalFiles",f.journalFiles());ProducerJournal.write(f.seedPath(),proof,false);
            CatanCraft.LOGGER.info("VEHICLE47_RESTART_SEED_PASS nativeEntities=2 paidWithoutSpawn=1 movedPendingHeld=1 originalPricesOnce=3 nativeRegions=PASS process={} source={} verification=PENDING",process(),source());
            f.close();f.h.succeed();
        }catch(Exception ex){f.close();throw new RuntimeException(ex);}});
    }

    private static void verify(Fixture f)throws Exception {
        check(!process().equals(f.proof.getString("process")),"Restart verification reused the seed JVM");
        check(f.proof.getCompound("nationAfter").equals(f.nation.save()),"Native nation changed during actual restart");
        f.assertDisk();f.verifyJournalFiles();
        List<Intent> orders=f.orders;Intent complete=orders.get(0),paid=orders.get(1),pending=orders.get(2);
        for(int n:List.of(0,2)){
            Intent i=orders.get(n);CompoundTag row=f.proof.getList("orders",Tag.TAG_COMPOUND).getCompound(n),saved=VehicleNativeDelivery47.savedEntity(f.level,i);
            f.assertIdentity(i,f.level.getEntity(i.entity()));assertSavedIdentity(i,saved);
            check(row.getCompound("nativeEntity").getList("Pos",Tag.TAG_DOUBLE).equals(saved.getList("Pos",Tag.TAG_DOUBLE)),"Original saved native pose changed across restart");
            f.crowbar(i);
        }
        f.count(2);f.charges(complete,paid,pending);
        check(f.execute(complete).complete(),"Original completed UUID did not remain settled");
        check(f.execute(paid).complete(),"Original PAID record did not perform its first native spawn");f.assertNative(paid);f.crowbar(paid);
        for(int n=0;n<3;n++)check(!f.execute(pending).complete(),"Moved native pending body was replaced or settled after restart");
        check(f.journal().read(pending.id()).phase()==Phase.SPAWN_ATTEMPTED,"Restart changed held pending phase");
        f.count(3);f.charges(complete,paid,pending);f.assertDisk();
        for(Intent i:orders)f.snapshot(i);
        f.level.getEntity(complete.entity()).discard();f.save();
        for(int n=0;n<3;n++)check(f.execute(complete).complete(),"Completed loss replay failed after restart");
        f.count(2);f.charges(complete,paid,pending);f.assertDisk();f.verifyJournalFiles();
        check(Arrays.equals(f.seedBytes,Files.readAllBytes(f.seedPath())),"Verification rewrote immutable seed proof");
        CompoundTag verified=f.metadata();verified.putString("result","PASS");verified.putString("seedProcess",f.proof.getString("process"));verified.putString("seedSHA256",TownHallRevisions.sha(f.seedBytes));
        verified.putInt("restoredNativeUUIDs",2);verified.putInt("firstSpawnFromOriginalPaid",1);verified.putInt("movedPendingHeld",1);verified.putInt("completedLossReplacements",0);
        verified.put("nativeLedger",f.disk());verified.put("nativeEntitiesBeforeCompletedLoss",f.snapshots);verified.put("journalFiles",f.journalFiles());
        ProducerJournal.write(f.verifiedPath(),verified,false);
        CatanCraft.LOGGER.info("VEHICLE47_RESTART_VERIFY_PASS restoredNativeUUIDs=2 firstSpawnFromOriginalPaid=1 movedPendingHeld=1 completedLossReplacements=0 originalPricesOnce=3 actualCrowbarTags=PASS distinctJVM=PASS seedProcess={} verifyProcess={} source={}",f.proof.getString("process"),process(),source());
        f.close();f.h.succeed();
    }

    private static void run(GameTestHelper h,String key,int index,Work work)throws Exception {
        Fixture f=new Fixture(h,key,index,"base");await(f,0,()->{f.initialize();work.run(f);f.close();h.succeed();});
    }
    private static void await(Fixture f,int ticks,Checked work){
        try{
            f.lease.keepAlive();boolean ready=f.lease.ready();
            if(ready&&f.phase.equals("verify"))for(Tag t:f.proof.getList("orders",Tag.TAG_COMPOUND)){
                CompoundTag row=(CompoundTag)t;if(row.contains("nativeEntity",Tag.TAG_COMPOUND))ready&=f.level.getEntity(decode(row.getCompound("record")).intent().entity())!=null;
            }
            if(ready){work.run();return;}check(ticks<600,"Native vehicle fixture chunks/entities did not become resident");
            f.h.runAfterDelay(5,()->await(f,ticks+5,work));
        }catch(Exception ex){f.close();throw new RuntimeException(ex);}
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;final ServerLevel level;final CatanSavedData data;final String key,phase,namespace;final Path root,journalPath;
        final int x=-2400,z;final AABB area;final SectorChunkLease lease;final CompoundTag proof;final byte[] seedBytes;
        final List<Intent> orders=new ArrayList<>();final ListTag snapshots=new ListTag();
        FakePlayer player;NationData nation;CompoundTag original;boolean authorized=true;
        Fixture(GameTestHelper h,String key,int index,String phase)throws Exception {
            this.h=h;this.key=key;this.phase=phase;namespace=phase.equals("base")?NS:RESTART;level=h.getLevel();data=CatanSavedData.get(level.getServer());z=-2400-index*256;
            check(level.getServer() instanceof GameTestServer&&level.getServer().isSameThread()&&level.getServer().getPlayerList().getPlayers().isEmpty(),"Native delivery fixture requires isolated server thread and no connected players");
            root=level.getServer().getWorldPath(LevelResource.ROOT).toRealPath();
            check(root.getFileName().toString().equals("world")&&root.getParent().getFileName().toString().equals("controlled-"+namespace+"-run"),"Foreign native vehicle fixture path");
            check(Files.readString(root.getParent().resolve(".catancraft-controlled-fixture-v1")).equals("CatanCraft synthetic flat fixture v1\n"),"Missing controlled fixture provenance");
            Properties properties=new Properties();try(var in=Files.newInputStream(root.getParent().resolve("server.properties"))){properties.load(in);}
            check("world".equals(properties.getProperty("level-name"))&&"minecraft:flat".equals(properties.getProperty("level-type"))&&DIM.equals(level.dimension().location().toString())&&level.getSeed()==0,"Foreign fixture generator/dimension/seed");
            source();runtime();check(ModList.get().isLoaded("superbwarfare"),"Real Superb Warfare runtime required");
            journalPath=root.resolve("data/catancraft_vehicle47_journal_"+key);
            check(!Files.exists(verifiedPath(),LinkOption.NOFOLLOW_LINKS),"Already verified native delivery fixture cannot be reused");
            if(phase.equals("verify")){
                seedBytes=PaidConstructionRecovery.readBounded(seedPath(),16*1024*1024);proof=ProducerJournal.read(seedBytes);
                check(proof.getInt("schema")==1&&namespace.equals(proof.getString("namespace"))&&root.toString().equals(proof.getString("world"))&&source().equals(proof.getString("source"))&&runtime().equals(proof.getString("runtimeManifestSHA256"))&&"SEEDED_PENDING_DISTINCT_JVM_VERIFICATION".equals(proof.getString("result")),"Foreign native restart source/runtime/world proof");
                nation=data.nation(proof.getUUID("nation"));check(nation!=null&&nation.isLeader(proof.getUUID("actor")),"Original native nation/actor missing after restart");
                player=player(proof.getUUID("actor"));original=proof.getCompound("nationBefore").copy();
                for(Tag t:proof.getList("orders",Tag.TAG_COMPOUND))orders.add(decode(((CompoundTag)t).getCompound("record")).intent());
                check(orders.size()==3&&orders.stream().map(Intent::id).distinct().count()==3&&orders.stream().map(Intent::entity).distinct().count()==3,"Missing or duplicate original native restart intents");
            }else{
                check(!Files.exists(seedPath(),LinkOption.NOFOLLOW_LINKS)&&!Files.exists(journalPath,LinkOption.NOFOLLOW_LINKS),"Refusing preexisting native vehicle seed/journal history");
                if(phase.equals("seed"))check(data.nations().isEmpty(),"Restart seed requires fresh native nation data");
                proof=new CompoundTag();seedBytes=null;
            }
            area=new AABB(x-16,135,z-16,x+112,155,z+16);lease=SectorChunkLease.acquire(level,area);
            for(var chunk:lease.positions())check(!level.getForcedChunks().contains(chunk.toLong()),"Foreign persistent forceload in native vehicle fixture");
        }
        void initialize()throws Exception {
            check(!phase.equals("verify"),"Verification cannot recreate world/nation/intent");
            for(int a=x-15;a<=x+111;a++)for(int b=z-15;b<=z+15;b++){
                BlockPos p=new BlockPos(a,139,b);check(level.getBlockState(p).isAir()&&level.getBlockState(p.above()).isAir(),"Preexisting geometry at synthetic native vehicle platform");level.setBlock(p,Blocks.STONE.defaultBlockState(),FLAGS);
            }
            check(level.getEntitiesOfClass(Entity.class,area).isEmpty(),"Preexisting entity in native vehicle fixture");
            player=player(UUID.randomUUID());nation=data.createNation("Vehicle47 "+key,player.getUUID());nation.setTreasury(FUNDS);
            for(ResourceType r:ResourceType.values())nation.addResource(r,STOCK);original=nation.save().copy();save();assertDisk();
        }
        FakePlayer player(UUID uuid){FakePlayer p=FakePlayerFactory.get(level,new GameProfile(uuid,"CCVehicle47"));p.getInventory().clearContent();p.setShiftKeyDown(true);p.setPos(x-10.5,140,z+.5);return p;}
        Intent order(String offer,int slot)throws IOException {
            check(!phase.equals("verify"),"Verify cannot generate a replacement intent");
            int anchor=x+slot*40;var facility=new VehicleBay45.Facility("synthetic_vehicle47",key+"_"+slot,nation.id(),DIM,"native-save-platform-v1","a".repeat(64),new VehicleBay45.Cell(anchor,140,z),0,new VehicleBay45.Cell(anchor-10,140,z),1);
            Set<VehicleBay45.Cell> support=new HashSet<>(),clear=new HashSet<>();
            for(int a=-5;a<=5;a++)for(int b=-5;b<=5;b++){support.add(new VehicleBay45.Cell(a,-1,b));for(int y=0;y<=5;y++)clear.add(new VehicleBay45.Cell(a,y,b));}
            var spec=offer.equals("pickup")?NativeVehicleBay46.PICKUP:NativeVehicleBay46.LAV;
            var bay=new VehicleBay45.Bay("synthetic-native-save-"+slot,facility,"superbwarfare:"+spec.id(),spec.sha(),new VehicleBay45.Position(.5,.1,.5),0,support,clear,Set.of(new VehicleBay45.Cell(4,0,0)),Set.of(new VehicleBay45.Cell(0,0,5)));
            Intent i=VehicleNativeDelivery47.newIntent(player,offer,bay);check(i.price().equals(price(offer)),"Native adapter changed original approved price");orders.add(i);return i;
        }
        FileJournal journal()throws IOException{return new FileJournal(journalPath);}
        Environment environment(Journal journal,VehicleNativeDelivery47.Hooks hooks)throws IOException {
            return VehicleNativeDelivery47.fixture(player,journal,(i,beforeSpawn)->{
                check(authorized&&orders.contains(i)&&i.nation().equals(nation.id())&&data.nation(i.nation())==nation&&nation.isLeader(i.actor())&&lease.ready(),"Changed synthetic native authority/nation/residency");
                check(i.bay().facility().revision().equals("native-save-platform-v1"),"Changed synthetic platform identity");
                for(var c:i.bay().support()){var w=i.bay().world(c);BlockPos p=new BlockPos(w.x(),w.y(),w.z());check(level.getBlockState(p).is(Blocks.STONE),"Changed synthetic native support");}
                if(beforeSpawn)for(var c:i.bay().clearance()){var w=i.bay().world(c);check(level.getBlockState(new BlockPos(w.x(),w.y(),w.z())).isAir(),"Synthetic native body envelope obstructed");}
            },hooks);
        }
        void prepare(Intent i)throws IOException {var j=journal();VehicleDelivery46.prepare(j,i,environment(j,NONE));}
        Result execute(Intent i)throws IOException {var j=journal();return VehicleDelivery46.execute(j,i.id(),environment(j,NONE));}
        void interrupt(Intent i,Phase next)throws Exception {
            Journal stop=new StopJournal(journal(),next);refuse(()->VehicleDelivery46.execute(stop,i.id(),environment(stop,NONE)));
        }
        void charges(Intent... paid)throws IOException {
            NationData expected=NationData.load(original.copy());for(Intent i:paid){expected.addTreasury(-i.price().money());i.price().resources().forEach((r,v)->expected.addResource(r,-v));
                check(VehicleDelivery46.fingerprint(i).equals(data.placedMapAsset(VehicleNativeDelivery47.receiptKey(i.id()))),"Missing original native debit receipt");}
            check(expected.save().equals(nation.save()),"Native original price was changed, repeated, or refunded");
        }
        void count(int expected)throws IOException {
            Set<UUID> found=new HashSet<>();for(Entity e:level.getEntitiesOfClass(Entity.class,area))if(!e.isRemoved()&&String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(e.getType())).startsWith("superbwarfare:"))found.add(e.getUUID());
            check(found.size()==expected&&orders.stream().map(Intent::entity).collect(java.util.stream.Collectors.toSet()).containsAll(found),"Unexpected native vehicle count or replacement UUID: "+found);
            check(level.getEntitiesOfClass(ItemEntity.class,area).isEmpty()&&player.getInventory().isEmpty(),"Vehicle fixture created portable/dropped items");
        }
        void assertIdentity(Intent i,Entity e)throws IOException {
            check(e!=null&&!e.isRemoved()&&i.entity().equals(e.getUUID())&&i.bay().vehicle().equals(String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(e.getType())))&&e.getPersistentData().getBoolean(NationProcurement.PURCHASED_TAG)&&i.nation().toString().equals(e.getPersistentData().getString(NationProcurement.NATION_TAG))&&VehicleNativeDelivery47.provenance(i).equals(e.getPersistentData().getCompound(VehicleNativeDelivery47.PROVENANCE)),"Native entity UUID/type/purchased/nation provenance mismatch");
        }
        void assertNative(Intent i)throws IOException {assertIdentity(i,level.getEntity(i.entity()));assertSavedIdentity(i,VehicleNativeDelivery47.savedEntity(level,i));}
        void crowbar(Intent i)throws Exception {
            Entity e=level.getEntity(i.entity());assertIdentity(i,e);Item item=ForgeRegistries.ITEMS.getValue(new ResourceLocation("superbwarfare:crowbar"));check(item!=null&&item!=Items.AIR,"Actual SBW crowbar missing");
            CompoundTag before=nation.save().copy();
            for(InteractionHand hand:InteractionHand.values()){
                player.setItemInHand(InteractionHand.MAIN_HAND,hand==InteractionHand.MAIN_HAND?new ItemStack(item):ItemStack.EMPTY);player.setItemInHand(InteractionHand.OFF_HAND,hand==InteractionHand.OFF_HAND?new ItemStack(item):ItemStack.EMPTY);
                ListTag inventory=player.getInventory().save(new ListTag());
                check(e.getClass().getMethod("onCrowbarInteract",ItemStack.class,Player.class,InteractionHand.class).invoke(e,new ItemStack(item),player,hand)==InteractionResult.FAIL,"Native onCrowbarInteract did not deny purchased vehicle");
                Class<?> vehicle=Class.forName("com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity");
                check(Class.forName("com.atsuishio.superbwarfare.item.weapon.CrowbarItem").getMethod("crowbarInteract",vehicle,ItemStack.class,Player.class,InteractionHand.class).invoke(null,e,new ItemStack(item),player,hand)==InteractionResult.FAIL,"Native crowbar helper did not deny purchased vehicle");
                check(inventory.equals(player.getInventory().save(new ListTag())),"Native crowbar helper changed inventory");
                player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item));player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);inventory=player.getInventory().save(new ListTag());
                check(e.interact(player,hand)==InteractionResult.FAIL,"Native entity interaction did not deny purchased vehicle");
                check(inventory.equals(player.getInventory().save(new ListTag()))&&before.equals(nation.save())&&level.getEntitiesOfClass(ItemEntity.class,area).isEmpty(),"Native crowbar attempt changed items/accounting");assertIdentity(i,e);
            }
            player.getInventory().clearContent();
        }
        void save()throws IOException {data.setDirty();check(level.getServer().saveEverything(true,true,true),"Native world save failed");}
        CompoundTag disk()throws IOException{return NbtIo.readCompressed(root.resolve("data/catancraft.dat").toFile()).getCompound("data");}
        void assertDisk()throws IOException {CatanSavedData saved=CatanSavedData.load(disk());check(saved.nation(nation.id())!=null&&saved.nation(nation.id()).save().equals(nation.save()),"Actual disk nation ledger differs from native memory");for(Intent i:orders)check(Objects.equals(saved.placedMapAsset(VehicleNativeDelivery47.receiptKey(i.id())),data.placedMapAsset(VehicleNativeDelivery47.receiptKey(i.id()))),"Native disk debit receipt differs");}
        CompoundTag row(Intent i)throws IOException {
            CompoundTag row=new CompoundTag();row.put("record",encode(journal().read(i.id())));CompoundTag saved=VehicleNativeDelivery47.savedEntity(level,i);if(saved!=null)row.put("nativeEntity",saved.copy());return row;
        }
        void snapshot(Intent i)throws IOException {CompoundTag row=row(i);check(row.contains("nativeEntity",Tag.TAG_COMPOUND),"Missing native entity-region evidence");snapshots.add(row);}
        CompoundTag journalFiles()throws IOException {
            CompoundTag files=new CompoundTag();try(var paths=Files.list(journalPath)){for(Path p:paths.filter(p->p.getFileName().toString().endsWith(".dat")).sorted().toList())files.putString(p.getFileName().toString(),TownHallRevisions.sha(Files.readAllBytes(p)));}return files;
        }
        void verifyJournalFiles()throws IOException {CompoundTag original=proof.getCompound("journalFiles"),now=journalFiles();check(!original.isEmpty(),"Seed omitted original phase-file evidence");for(String name:original.getAllKeys())check(original.getString(name).equals(now.getString(name)),"Restart changed immutable original journal bytes: "+name);}
        CompoundTag metadata(){CompoundTag p=new CompoundTag();p.putInt("schema",1);p.putString("namespace",namespace);p.putString("world",root.toString());p.putString("process",process());p.putString("source",source());p.putString("runtimeManifestSHA256",runtime());return p;}
        Path seedPath(){return root.resolve("data/catancraft_vehicle47_"+key+"_seed.dat");}
        Path verifiedPath(){return root.resolve("data/catancraft_vehicle47_"+key+"_verified.dat");}
        void finish(String evidence)throws IOException {CompoundTag p=metadata();p.putString("result","PASS");p.putString("evidence",evidence);p.putUUID("nation",nation.id());p.put("nationBefore",original.copy());p.put("nationAfter",nation.save());p.put("nativeLedger",disk());p.put("nativeEntitySnapshots",snapshots);p.put("journalFiles",journalFiles());ProducerJournal.write(verifiedPath(),p,false);CatanCraft.LOGGER.info("VEHICLE47_NATIVE_{}_PASS {}",key.toUpperCase(Locale.ROOT),evidence);}
        public void close(){if(lease!=null)lease.close();if(player!=null)player.getInventory().clearContent();}
    }

    private record StopJournal(Journal real,Phase stop) implements Journal {
        public Record read(UUID id)throws IOException{return real.read(id);}
        public void create(Intent i)throws IOException{real.create(i);}
        public void transition(Record old,Phase next)throws IOException{if(next==stop)throw new IOException("Injected before immutable "+next+" append");real.transition(old,next);}
    }
    /** Offline parser fixtures live under data/, never in native entities/ and never get imported. */
    private static void malformedRegionRefusals(Fixture f,Intent i)throws Exception {
        Path dir=f.root.resolve("data/catancraft_vehicle47_parser_holds");Files.createDirectory(dir);
        CompoundTag entity=VehicleNativeDelivery47.savedEntity(f.level,i),valid=new CompoundTag();valid.putIntArray("Position",new int[]{0,0});
        ListTag entities=new ListTag();entities.add(entity.copy());valid.put("Entities",entities);
        Path good=region(dir,"valid",valid);check(entity.equals(VehicleNativeDelivery47.readEntityChunk(good,new net.minecraft.world.level.ChunkPos(0,0),i.entity())),"Independent native parser control failed");
        // RegionFile may create a zero-byte file during a cache read before its first write.
        Path empty=regionBytes(dir,"valid_empty",new byte[0]);
        check(VehicleNativeDelivery47.readEntityChunk(empty,new net.minecraft.world.level.ChunkPos(0,0),i.entity())==null&&Files.size(empty)==0,"Stable native empty region did not remain absent/read-only");
        // An open RegionFile writes the declared chunk bytes without padding its final sector.
        byte[] padded=Files.readAllBytes(good);int declared=java.nio.ByteBuffer.wrap(padded,8192,4).getInt();
        byte[] unpadded=Arrays.copyOf(padded,8192+4+declared);check(unpadded.length%4096!=0,"Unpadded native parser control unexpectedly has sector-aligned size");
        Path tail=regionBytes(dir,"valid_unpadded",unpadded);
        check(entity.equals(VehicleNativeDelivery47.readEntityChunk(tail,new net.minecraft.world.level.ChunkPos(0,0),i.entity()))&&Arrays.equals(unpadded,Files.readAllBytes(tail)),"Complete unpadded native payload was refused or modified");
        List<CompoundTag> bad=new ArrayList<>();
        CompoundTag wrongList=valid.copy();ListTag strings=new ListTag();strings.add(StringTag.valueOf("not-an-entity"));wrongList.put("Entities",strings);bad.add(wrongList);
        CompoundTag missingUuid=valid.copy();missingUuid.getList("Entities",Tag.TAG_COMPOUND).getCompound(0).remove("UUID");bad.add(missingUuid);
        CompoundTag wrongType=valid.copy();wrongType.getList("Entities",Tag.TAG_COMPOUND).getCompound(0).putByte("id",(byte)1);bad.add(wrongType);
        CompoundTag duplicate=valid.copy();duplicate.getList("Entities",Tag.TAG_COMPOUND).add(entity.copy());bad.add(duplicate);
        CompoundTag foreignChunk=valid.copy();foreignChunk.putIntArray("Position",new int[]{1,0});bad.add(foreignChunk);
        CompoundTag uuidShape=valid.copy();uuidShape.getList("Entities",Tag.TAG_COMPOUND).getCompound(0).putIntArray("UUID",new int[]{1,2,3});bad.add(uuidShape);
        for(int n=0;n<bad.size();n++){
            Path path=region(dir,"malformed_"+n,bad.get(n));byte[] before=Files.readAllBytes(path);
            refuse(()->VehicleNativeDelivery47.readEntityChunk(path,new net.minecraft.world.level.ChunkPos(0,0),i.entity()));
            check(Arrays.equals(before,Files.readAllBytes(path)),"Read-only refusal modified malformed region evidence");
        }
        for(int size:List.of(1,4095,4096,8191)){
            Path path=regionBytes(dir,"malformed_header_"+size,new byte[size]);
            refuse(()->VehicleNativeDelivery47.readEntityChunk(path,new net.minecraft.world.level.ChunkPos(0,0),i.entity()));
            check(Files.size(path)==size&&Arrays.equals(new byte[size],Files.readAllBytes(path)),"Partial-header refusal rewrote preserved bytes");
        }
        byte[] truncated=Arrays.copyOf(unpadded,unpadded.length-1),overlap=padded.clone(),extent=padded.clone();
        int location=java.nio.ByteBuffer.wrap(padded,0,4).getInt();java.nio.ByteBuffer.wrap(overlap,4,4).putInt(location);
        java.nio.ByteBuffer.wrap(extent,0,4).putInt(location+1);
        List<byte[]> invalid=List.of(truncated,overlap,extent);List<String> names=List.of("payload_truncated","overlap","extent");
        for(int n=0;n<invalid.size();n++){
            byte[] before=invalid.get(n);Path path=regionBytes(dir,"malformed_"+names.get(n),before);
            refuse(()->VehicleNativeDelivery47.readEntityChunk(path,new net.minecraft.world.level.ChunkPos(0,0),i.entity()));
            refuse(()->VehicleNativeDelivery47.readEntityChunk(path,new net.minecraft.world.level.ChunkPos(2,0),i.entity()));
            check(Arrays.equals(before,Files.readAllBytes(path)),"Physical-region refusal rewrote preserved bytes");
        }
    }
    private static Path region(Path dir,String name,CompoundTag chunk)throws IOException {
        var raw=new java.io.ByteArrayOutputStream();NbtIo.write(chunk,new java.io.DataOutputStream(raw));
        var compressed=new java.io.ByteArrayOutputStream();try(var zip=new java.util.zip.DeflaterOutputStream(compressed)){zip.write(raw.toByteArray());}
        byte[] payload=compressed.toByteArray();int sectors=(payload.length+5+4095)/4096;
        var bytes=java.nio.ByteBuffer.allocate(8192+sectors*4096);bytes.putInt((2<<8)|sectors);bytes.position(8192);bytes.putInt(payload.length+1);bytes.put((byte)2);bytes.put(payload);
        return regionBytes(dir,name,bytes.array());
    }
    private static Path regionBytes(Path dir,String name,byte[] bytes)throws IOException {Path path=dir.resolve(name+".mca");Files.write(path,bytes,StandardOpenOption.CREATE_NEW);return path;}
    private static ShopPurchase40.Price price(String offer){return offer.equals("pickup")?new ShopPurchase40.Price(4000,Map.of(ResourceType.STEEL,20L,ResourceType.MECHANICAL_PARTS,10L,ResourceType.FUEL,8L)):new ShopPurchase40.Price(8500,Map.of(ResourceType.STEEL,50L,ResourceType.MECHANICAL_PARTS,25L,ResourceType.FUEL,20L,ResourceType.ELECTRONICS,5L));}
    private static void assertSavedIdentity(Intent i,CompoundTag n)throws IOException {check(n!=null&&n.hasUUID("UUID")&&i.entity().equals(n.getUUID("UUID"))&&i.bay().vehicle().equals(n.getString("id"))&&n.getCompound("ForgeData").getBoolean(NationProcurement.PURCHASED_TAG)&&i.nation().toString().equals(n.getCompound("ForgeData").getString(NationProcurement.NATION_TAG))&&VehicleNativeDelivery47.provenance(i).equals(n.getCompound("ForgeData").getCompound(VehicleNativeDelivery47.PROVENANCE)),"Actual Anvil entity NBT lost original UUID/type/purchased/nation provenance");}
    private static void check(boolean condition,String message)throws IOException {if(!condition)throw new IOException(message);}
    private static void refuse(Checked work)throws Exception {try{work.run();}catch(IOException expected){return;}throw new AssertionError("Injected boundary/authority refusal did not occur");}
    private static String source(){String s=System.getProperty("catancraft.vehicle47RestartSource","");if(!s.matches("[0-9a-f]{40}"))throw new IllegalStateException("Exact vehicle47RestartSource SHA required");return s;}
    private static String runtime(){String s=System.getProperty("catancraft.sbwRuntimeManifestSHA256","");if(!s.matches("[0-9a-f]{64}"))throw new IllegalStateException("Pinned SBW runtime manifest SHA-256 required");return s;}
    private static String process(){return ProcessHandle.current().pid()+":"+ManagementFactory.getRuntimeMXBean().getStartTime();}
}
