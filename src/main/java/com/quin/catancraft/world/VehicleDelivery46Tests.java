package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.ResourceType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.io.*;
import java.util.*;
import java.nio.file.*;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.world.level.storage.LevelResource;
import static com.quin.catancraft.world.VehicleDelivery46.*;
import com.quin.catancraft.world.VehicleDelivery46.Record;

/** Byte-serialized adapter failure fixtures, not native entity/chunk persistence qualification. */
@GameTestHolder("catancraft_vehicle46") @PrefixGameTestTemplate(false)
public final class VehicleDelivery46Tests {
    private static final UUID ACTOR=UUID.fromString("46000000-0000-0000-0000-000000000001");
    private static final UUID NATION=UUID.fromString("46000000-0000-0000-0000-000000000002");
    private static final long START=100_000;
    private static Intent intent(String offer) {
        var f=new VehicleBay45.Facility("synthetic46","plot_1",NATION,"minecraft:overworld","fixture46","a".repeat(64),new VehicleBay45.Cell(100,80,100),0,new VehicleBay45.Cell(89,81,94),1);
        var spawn=new VehicleBay45.Cell(0,0,0);var board=new VehicleBay45.Cell(1,0,0);var exit=new VehicleBay45.Cell(2,0,0);
        String type=offer.equals("pickup")?"superbwarfare:sodayo_pick_up":"superbwarfare:lav_150";
        var bay=new VehicleBay45.Bay("fixture46-lane",f,type,"b".repeat(64),new VehicleBay45.Position(0.5,0.1,0.5),90,Set.of(new VehicleBay45.Cell(0,-1,0)),Set.of(spawn,board,exit),Set.of(board),Set.of(exit));
        var price=offer.equals("pickup")?new ShopPurchase40.Price(4000,Map.of(ResourceType.STEEL,20L,ResourceType.MECHANICAL_PARTS,10L,ResourceType.FUEL,8L)):
                new ShopPurchase40.Price(8500,Map.of(ResourceType.STEEL,50L,ResourceType.MECHANICAL_PARTS,25L,ResourceType.FUEL,20L,ResourceType.ELECTRONICS,5L));
        return new Intent(UUID.randomUUID(),ACTOR,NATION,UUID.randomUUID(),"fixture46-world",offer,"legacy-vehicle-prices-v1",bay,price);
    }
    private enum Behavior { NORMAL, SILENT, BEFORE, AFTER }
    private static final class MemoryJournal implements Journal {
        final Map<UUID,byte[]> saved=new HashMap<>();
        Phase suppress,throwBefore,throwAfter;boolean suppressCreate;int writes;
        public Record read(UUID id)throws IOException {byte[] bytes=saved.get(id);return bytes==null?null:decode(tag(bytes));}
        public void create(Intent i)throws IOException {
            if(saved.containsKey(i.id()))throw new IOException("Existing immutable intent");
            if(!suppressCreate){saved.put(i.id(),bytes(encode(new Record(i,Phase.PREPARED))));writes++;}
        }
        public void transition(Record expected,Phase next)throws IOException {
            if(!Objects.equals(read(expected.intent().id()),expected))throw new IOException("Changed journal state");
            if(throwBefore==next)throw new IOException("Injected pre-journal save fault");
            if(suppress!=next){saved.put(expected.intent().id(),bytes(encode(new Record(expected.intent(),next))));writes++;}
            if(throwAfter==next)throw new IOException("Injected post-journal save fault");
        }
        MemoryJournal restart(){var n=new MemoryJournal();saved.forEach((k,v)->n.saved.put(k,v.clone()));return n;}
    }
    private record EntityProof(UUID uuid,String type,UUID nation,byte[] intentBytes) {
        static EntityProof from(Intent i)throws IOException{return new EntityProof(i.entity(),i.bay().vehicle(),i.nation(),identity(i));}
        boolean matches(Intent i)throws IOException{return uuid.equals(i.entity())&&type.equals(i.bay().vehicle())&&nation.equals(i.nation())&&Arrays.equals(intentBytes,identity(i));}
        EntityProof copy(){return new EntityProof(uuid,type,nation,intentBytes.clone());}
    }
    private static final class Fixture implements Environment {
        MemoryJournal journal=new MemoryJournal();final Intent order;boolean authority=true,forbidQueries;
        Behavior debitBehavior=Behavior.NORMAL,spawnBehavior=Behavior.NORMAL;
        long money=START,savedMoney=START;Map<ResourceType,Long> stock=new EnumMap<>(ResourceType.class),savedStock=new EnumMap<>(ResourceType.class);
        byte[] debitReceipt;EntityProof liveEntity,savedEntity;Debit forcedDebit;Delivery forcedDelivery;
        int debitCalls,spawnCalls,queries;
        Fixture(String offer){order=intent(offer);for(ResourceType r:ResourceType.values()){stock.put(r,1000L);savedStock.put(r,1000L);}}
        Fixture(Intent i){order=i;}
        public void guard(Intent i)throws IOException {query();if(!authority||!i.equals(order))throw new IOException("Changed physical authority");}
        public Debit debitState(Intent i)throws IOException {
            query();if(forcedDebit!=null)return forcedDebit;
            if(debitReceipt==null)return Debit.ABSENT;
            return Arrays.equals(debitReceipt,identity(i))?Debit.EXACT:Debit.CONFLICT;
        }
        public void debitAndSave(Intent i)throws IOException {
            debitCalls++;if(debitBehavior==Behavior.BEFORE)throw new IOException("Debit failed before effects");
            check(money>=i.price().money(),"Insufficient fixture treasury");money-=i.price().money();
            for(var e:i.price().resources().entrySet()){check(stock.get(e.getKey())>=e.getValue(),"Insufficient fixture resource");stock.put(e.getKey(),stock.get(e.getKey())-e.getValue());}
            if(debitBehavior!=Behavior.SILENT){savedMoney=money;savedStock=new EnumMap<>(stock);debitReceipt=identity(i);}
            if(debitBehavior==Behavior.AFTER)throw new IOException("Debit callback failed after durable save");
        }
        public Delivery deliveryState(Intent i)throws IOException {
            query();if(forcedDelivery!=null)return forcedDelivery;
            if(savedEntity!=null)return savedEntity.matches(i)?Delivery.EXACT_SAVED:Delivery.CONFLICT;
            if(liveEntity!=null)return liveEntity.matches(i)?Delivery.EXACT_UNSAVED:Delivery.CONFLICT;
            return Delivery.ABSENT_LOADED;
        }
        public void spawnAndSave(Intent i)throws IOException {
            spawnCalls++;check(journal.read(i.id()).phase()==Phase.SPAWN_ATTEMPTED,"Spawn preceded durable attempt marker");
            check(debitReceipt!=null&&savedMoney==START-i.price().money(),"Spawn preceded durable original debit");
            if(spawnBehavior==Behavior.BEFORE)throw new IOException("Native spawn failed before creating entity");
            check(liveEntity==null&&savedEntity==null,"Duplicate spawn attempted");liveEntity=EntityProof.from(i);
            if(spawnBehavior!=Behavior.SILENT)savedEntity=liveEntity.copy();
            if(spawnBehavior==Behavior.AFTER)throw new IOException("Spawn callback failed after durable entity save");
        }
        void query()throws IOException{queries++;if(forbidQueries)throw new IOException("Completed replay queried mutable external state");}
        void prepare()throws IOException{VehicleDelivery46.prepare(journal,order,this);}
        Result execute()throws IOException{return VehicleDelivery46.execute(journal,order.id(),this);}
        Fixture restart(){var n=new Fixture(order);n.journal=journal.restart();n.money=n.savedMoney=savedMoney;n.stock=new EnumMap<>(savedStock);n.savedStock=new EnumMap<>(savedStock);n.debitReceipt=debitReceipt==null?null:debitReceipt.clone();n.savedEntity=savedEntity==null?null:savedEntity.copy();n.liveEntity=n.savedEntity==null?null:n.savedEntity.copy();n.debitCalls=debitCalls;n.spawnCalls=spawnCalls;return n;}
        void once()throws IOException{
            check(money==START-order.price().money()&&savedMoney==money&&debitCalls==1,"Original cash charge was changed or repeated");
            for(ResourceType r:ResourceType.values())check(stock.get(r)==1000-order.price().resources().getOrDefault(r,0L)&&savedStock.get(r).equals(stock.get(r)),"Original resource charge changed: "+r);
            check(spawnCalls==1&&savedEntity!=null&&savedEntity.matches(order),"Expected one exact durable entity");
        }
    }
    @GameTest(template="townhall_state_empty") public static void immutable_intent_is_saved_before_any_money_or_entity_effect(GameTestHelper h)throws Exception{
        var f=new Fixture("pickup");f.prepare();check(f.journal.read(f.order.id()).phase()==Phase.PREPARED&&f.money==START&&f.debitCalls==0&&f.spawnCalls==0,"Preparation produced external effects");
        check(f.journal.read(f.order.id()).intent().equals(f.order),"Original intent lost identity");pass(h,"PREPARED");
    }
    @GameTest(template="townhall_state_empty") public static void pickup_uses_original_price_and_exact_entity_provenance(GameTestHelper h)throws Exception{
        var f=new Fixture("pickup");f.prepare();check(f.execute().complete(),"Pickup did not complete");f.once();check(f.journal.read(f.order.id()).phase()==Phase.COMPLETE,"Missing completion");pass(h,"PICKUP");
    }
    @GameTest(template="townhall_state_empty") public static void lav_uses_original_price_including_electronics(GameTestHelper h)throws Exception{
        var f=new Fixture("lav150");f.prepare();check(f.execute().complete(),"LAV did not complete");f.once();check(f.stock.get(ResourceType.ELECTRONICS)==995,"LAV original electronics charge changed");pass(h,"LAV");
    }
    @GameTest(template="townhall_state_empty") public static void completed_retry_after_loss_or_owner_change_does_not_recreate_vehicle(GameTestHelper h)throws Exception{
        var f=new Fixture("pickup");f.prepare();f.execute();f=f.restart();f.liveEntity=f.savedEntity=null;f.authority=false;f.forbidQueries=true;int writes=f.journal.writes;
        for(int n=0;n<3;n++)check(f.execute().complete(),"Completed order did not remain settled");
        check(f.debitCalls==1&&f.spawnCalls==1&&f.money==START-4000&&f.journal.writes==writes&&f.queries==0,"Completed replay touched funds/entities/journal");pass(h,"COMPLETED_REPLAY");
    }
    @GameTest(template="townhall_state_empty") public static void request_serialization_and_price_are_defensively_immutable(GameTestHelper h)throws Exception{
        var f=new Fixture("pickup");var encoded=encode(new Record(f.order,Phase.PREPARED));byte[] original=bytes(encoded);var restored=decode(tag(original));
        encoded.putString("offer","changed");check(restored.intent().equals(f.order)&&Arrays.equals(bytes(encode(restored)),original),"Decoded identity aliases caller NBT");
        invalid(()->f.order.price().resources().put(ResourceType.STEEL,1L));invalid(()->f.order.bay().clearance().clear());
        invalid(()->new Intent(new UUID(0,0),ACTOR,NATION,f.order.entity(),f.order.world(),f.order.offer(),f.order.catalog(),f.order.bay(),f.order.price()));
        invalid(()->new Intent(f.order.id(),ACTOR,NATION,f.order.entity(),f.order.world(),"lav150",f.order.catalog(),f.order.bay(),f.order.price()));
        var reversed=new LinkedHashMap<ResourceType,Long>();var entries=new ArrayList<>(f.order.price().resources().entrySet());Collections.reverse(entries);for(var e:entries)reversed.put(e.getKey(),e.getValue());
        var same=new Intent(f.order.id(),ACTOR,NATION,f.order.entity(),f.order.world(),f.order.offer(),f.order.catalog(),f.order.bay(),new ShopPurchase40.Price(f.order.price().money(),reversed));
        check(fingerprint(f.order).equals(fingerprint(same))&&fingerprint(f.order).equals(fingerprint(restored.intent())),"Fingerprint depends on equivalent ordering or serialization");pass(h,"IMMUTABLE");
    }
    @GameTest(template="townhall_state_empty") public static void silently_failed_intent_save_never_authorizes_debit(GameTestHelper h)throws Exception{
        var f=new Fixture("pickup");f.journal.suppressCreate=true;refuse(f::prepare);check(f.journal.read(f.order.id())==null&&f.debitCalls==0&&f.spawnCalls==0,"Missing intent authorized effects");pass(h,"INTENT_SAVE");
    }
    @GameTest(template="townhall_state_empty") public static void debit_attempt_marker_must_be_read_back_before_calling_adapter(GameTestHelper h)throws Exception{
        var f=new Fixture("pickup");f.prepare();f.journal.suppress=Phase.DEBIT_ATTEMPTED;held(f);check(f.debitCalls==0&&f.spawnCalls==0&&f.money==START,"Unsaved debit attempt executed");
        f=f.restart();check(f.execute().complete(),"Untouched PREPARED state could not retry");f.once();pass(h,"DEBIT_MARKER");
    }
    @GameTest(template="townhall_state_empty") public static void absent_or_unverified_debit_after_attempt_is_held_across_restart(GameTestHelper h)throws Exception{
        for(Behavior b:List.of(Behavior.BEFORE,Behavior.SILENT)){
            var f=new Fixture("pickup");f.prepare();f.debitBehavior=b;held(f);check(f.spawnCalls==0,"Unverified payment spawned vehicle");f=f.restart();long cash=f.money;
            for(int n=0;n<3;n++)held(f);check(f.debitCalls==1&&f.spawnCalls==0&&f.money==cash,"Unknown debit was replayed or refunded");
        }pass(h,"DEBIT_UNCERTAINTY");
    }
    @GameTest(template="townhall_state_empty") public static void debit_callback_error_after_exact_durable_save_uses_readback(GameTestHelper h)throws Exception{
        var f=new Fixture("pickup");f.prepare();f.debitBehavior=Behavior.AFTER;check(f.execute().complete(),"Durable debit ignored after callback exception");f.once();pass(h,"DEBIT_READBACK");
    }
    @GameTest(template="townhall_state_empty") public static void restart_after_saved_debit_preserves_original_charge_once(GameTestHelper h)throws Exception{
        var f=new Fixture("pickup");f.prepare();f.journal.suppress=Phase.PAID;held(f);check(f.debitCalls==1&&f.spawnCalls==0,"Post-debit journal failure spawned");
        byte[] original=bytes(encode(new Record(f.order,Phase.PREPARED)));f=f.restart();check(f.execute().complete(),"Original durable debit did not recover");f.once();
        check(Arrays.equals(original,bytes(encode(new Record(f.journal.read(f.order.id()).intent(),Phase.PREPARED)))),"Recovery changed original price/intent");pass(h,"PAID_RESTART");
    }
    @GameTest(template="townhall_state_empty") public static void spawn_attempt_marker_failure_never_calls_spawn_and_can_retry_from_paid(GameTestHelper h)throws Exception{
        var f=new Fixture("pickup");f.prepare();f.journal.suppress=Phase.SPAWN_ATTEMPTED;held(f);check(f.debitCalls==1&&f.spawnCalls==0&&f.journal.read(f.order.id()).phase()==Phase.PAID,"Unsaved spawn marker produced entity");
        f=f.restart();check(f.execute().complete(),"Saved paid state failed safe first spawn");f.once();pass(h,"SPAWN_MARKER");
    }
    @GameTest(template="townhall_state_empty") public static void failed_or_unsaved_spawn_attempt_never_creates_replacement(GameTestHelper h)throws Exception{
        for(Behavior b:List.of(Behavior.BEFORE,Behavior.SILENT)){
            var f=new Fixture("pickup");f.prepare();f.spawnBehavior=b;held(f);check(f.spawnCalls==1,"Initial spawn not attempted once");held(f);
            f=f.restart();for(int n=0;n<3;n++)held(f);check(f.spawnCalls==1&&f.debitCalls==1&&f.money==START-4000,"Ambiguous spawn duplicated or refunded");
        }pass(h,"SPAWN_UNCERTAINTY");
    }
    @GameTest(template="townhall_state_empty") public static void spawn_callback_error_after_durable_exact_entity_completes_once(GameTestHelper h)throws Exception{
        var f=new Fixture("lav150");f.prepare();f.spawnBehavior=Behavior.AFTER;check(f.execute().complete(),"Durable entity ignored after callback exception");f.once();pass(h,"ENTITY_READBACK");
    }
    @GameTest(template="townhall_state_empty") public static void durable_entity_before_completion_recovers_without_new_spawn(GameTestHelper h)throws Exception{
        var f=new Fixture("pickup");f.prepare();f.journal.suppress=Phase.COMPLETE;held(f);f.once();check(f.journal.read(f.order.id()).phase()==Phase.SPAWN_ATTEMPTED,"Failed completion destroyed attempted state");
        f=f.restart();check(f.execute().complete(),"Saved exact entity did not settle");f.once();
        var committed=new Fixture("pickup");committed.prepare();committed.journal.throwAfter=Phase.COMPLETE;refuse(committed::execute);committed=committed.restart();committed.forbidQueries=true;
        check(committed.execute().complete()&&committed.debitCalls==1&&committed.spawnCalls==1,"Post-completion exception replayed original purchase");pass(h,"ENTITY_RESTART");
    }
    @GameTest(template="townhall_state_empty") public static void unknown_unloaded_or_conflicting_delivery_never_mints_or_refunds(GameTestHelper h)throws Exception{
        for(Delivery d:List.of(Delivery.UNKNOWN,Delivery.CONFLICT,Delivery.EXACT_UNSAVED,Delivery.ABSENT_LOADED)){
            var f=new Fixture("pickup");f.prepare();f.journal.suppress=Phase.COMPLETE;held(f);f=f.restart();f.forcedDelivery=d;long cash=f.money;
            held(f);check(f.spawnCalls==1&&f.debitCalls==1&&f.money==cash,"Uncertain delivery permitted replacement/refund");
        }
        for(Debit d:List.of(Debit.UNKNOWN,Debit.CONFLICT,Debit.ABSENT)){
            var f=new Fixture("pickup");f.prepare();f.journal.suppress=Phase.SPAWN_ATTEMPTED;held(f);f=f.restart();f.forcedDebit=d;held(f);
            check(f.spawnCalls==0&&f.debitCalls==1&&f.money==START-4000,"Changed original debit permitted spawn or another charge");
        }pass(h,"DELIVERY_HOLD");
    }
    @GameTest(template="townhall_state_empty") public static void changed_physical_authority_refuses_before_new_effects(GameTestHelper h)throws Exception{
        var before=new Fixture("pickup");before.authority=false;refuse(before::prepare);check(before.journal.read(before.order.id())==null&&before.debitCalls==0,"Foreign authority prepared purchase");
        var f=new Fixture("pickup");f.prepare();f.journal.suppress=Phase.SPAWN_ATTEMPTED;held(f);f=f.restart();f.authority=false;held(f);
        check(f.spawnCalls==0&&f.debitCalls==1&&f.money==START-4000,"Changed owner/facility allowed new delivery or refund");pass(h,"AUTHORITY");
    }
    @GameTest(template="townhall_state_empty") public static void mismatched_entity_uuid_type_nation_or_intent_cannot_settle(GameTestHelper h)throws Exception{
        for(int field=0;field<4;field++){
            var f=new Fixture("pickup");f.prepare();f.journal.suppress=Phase.COMPLETE;held(f);f=f.restart();var p=f.savedEntity;byte[] identity=p.intentBytes().clone();if(field==3)identity[identity.length-1]^=1;
            f.savedEntity=new EntityProof(field==0?UUID.randomUUID():p.uuid(),field==1?"superbwarfare:lav_150":p.type(),field==2?UUID.randomUUID():p.nation(),identity);f.liveEntity=f.savedEntity.copy();held(f);
            check(f.journal.read(f.order.id()).phase()!=Phase.COMPLETE&&f.spawnCalls==1&&f.debitCalls==1,"Wrong native provenance settled or replayed");
        }pass(h,"PROVENANCE");
    }
    @GameTest(template="townhall_state_empty") public static void unknown_journal_schema_or_corrupt_bytes_fail_closed(GameTestHelper h)throws Exception{
        var f=new Fixture("pickup");f.prepare();byte[] original=f.journal.saved.get(f.order.id()).clone();
        var n=tag(original);n.putInt("schema",999);f.journal.saved.put(f.order.id(),bytes(n));refuse(f::execute);check(f.debitCalls==0&&f.spawnCalls==0,"Unknown schema executed");
        f.journal.saved.put(f.order.id(),Arrays.copyOf(original,original.length/2));refuse(f::execute);check(f.money==START,"Corrupt journal changed money");
        Path world=h.getLevel().getServer().getWorldPath(LevelResource.ROOT).toRealPath();
        check(h.getLevel().getServer() instanceof GameTestServer&&h.getLevel().getServer().isSameThread()
                &&world.getParent().getFileName().toString().equals("controlled-catancraft_vehicle46-run")
                &&Files.readString(world.getParent().resolve(".catancraft-controlled-fixture-v1")).equals("CatanCraft synthetic flat fixture v1\n")
                &&h.getLevel().getServer().getPlayerList().getPlayers().isEmpty(),"Exact controlled journal fixture required");
        Path directory=world.resolve("data/vehicle46-journal-tests/"+UUID.randomUUID());var disk=new FileJournal(directory);disk.create(f.order);
        Path first=directory.resolve(f.order.id()+".0.dat");byte[] firstBytes=Files.readAllBytes(first);
        for(Phase phase:Phase.values()){
            if(phase!=Phase.PREPARED)disk.transition(disk.read(f.order.id()),phase);
            var reopened=new FileJournal(directory);check(reopened.read(f.order.id()).equals(new Record(f.order,phase)),"Fresh FileJournal lost phase or original identity");
            check(Arrays.equals(firstBytes,Files.readAllBytes(first)),"Later phase overwrote immutable original intent");
        }
        Path unknown=directory.resolve(f.order.id()+".foreign");Files.write(unknown,new byte[]{7},StandardOpenOption.CREATE_NEW);refuse(()->new FileJournal(directory).read(f.order.id()));check(Files.exists(unknown),"Unknown evidence was deleted");
        Intent gap=intent("pickup");disk.create(gap);Path gapPath=directory.resolve(gap.id()+".2.dat");ProducerJournal.write(gapPath,encode(new Record(gap,Phase.PAID)),false);
        refuse(()->new FileJournal(directory).read(gap.id()));check(Files.exists(gapPath),"Gapped record was erased");pass(h,"JOURNAL_REFUSAL");
    }
    private static byte[] identity(Intent i)throws IOException{return fingerprint(i).getBytes(java.nio.charset.StandardCharsets.US_ASCII);}
    private static byte[] bytes(CompoundTag n)throws IOException{var b=new ByteArrayOutputStream();NbtIo.writeCompressed(n,b);return b.toByteArray();}
    private static CompoundTag tag(byte[] b)throws IOException{
        try{return NbtIo.readCompressed(new ByteArrayInputStream(b));}
        catch(net.minecraft.ReportedException failure){
            // Minecraft's NBT loader can wrap a truncated compressed payload's IO error.
            // Preserve the Journal adapter's checked-IO contract; all other crash reports escape.
            if(failure.getCause() instanceof IOException io)throw new IOException("Malformed compressed fixture NBT",io);
            throw failure;
        }
    }
    @FunctionalInterface private interface Action {void run()throws Exception;}
    private static void refuse(Action action)throws Exception{try{action.run();}catch(IOException|IllegalArgumentException expected){return;}throw new AssertionError("Unsafe operation accepted");}
    private static void invalid(Action action)throws Exception{try{action.run();}catch(UnsupportedOperationException|IllegalArgumentException expected){return;}throw new AssertionError("Mutable/invalid request accepted");}
    private static void held(Fixture f)throws Exception{try{check(!f.execute().complete(),"Ambiguous operation completed");}catch(IOException expected){/* Fail-closed adapter refusal is also a hold. */}}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void pass(GameTestHelper h,String id){CatanCraft.LOGGER.info("VEHICLE46_DELIVERY_PASS {}",id);h.succeed();}
}
