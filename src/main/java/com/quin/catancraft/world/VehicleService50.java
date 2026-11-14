package com.quin.catancraft.world;

import com.quin.catancraft.command.NationProcurement;
import com.quin.catancraft.data.*;
import com.quin.catancraft.economy.EconomyCost;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.registries.ForgeRegistries;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Physical vehicle operations. Every mutation runs on the server thread, behind durable
 * attempt boundaries. Ambiguous debit/spawn/refuel attempts remain locked, never replayed. */
public final class VehicleService50 {
    static final String CATALOG="vehicle50_quarter_tank_v1", RECEIPT="CatanCraftVehicle50Receipt";
    private static final String PREFIX="vehicle50:", NONE="none";
    private static final int LIMIT=4096;
    private static final ThreadLocal<Set<UUID>> INTERNAL=ThreadLocal.withInitial(HashSet::new);
    public enum Kind { PURCHASE, REFUEL }
    public record Pending(UUID operation,String description) {}
    public record Quote(UUID id,UUID actor,UUID nation,UUID vehicle,Kind kind,String offer,
                        CompoundTag facility,long createdTick,VehicleDelivery46.Intent purchase,
                        VehicleRefueling49.Intent refill) {
        public Quote { Objects.requireNonNull(id);Objects.requireNonNull(actor);Objects.requireNonNull(nation);Objects.requireNonNull(vehicle);Objects.requireNonNull(kind);Objects.requireNonNull(offer);facility=facility.copy();
            if(createdTick<0||(kind==Kind.PURCHASE)!=(purchase!=null)||(kind==Kind.REFUEL)!=(refill!=null))throw new IllegalArgumentException("Invalid vehicle quote"); }
        public CompoundTag facility(){return facility.copy();}
        public long fuelCost(){return kind==Kind.PURCHASE?purchase.price().resources().getOrDefault(ResourceType.FUEL,0L):refill.fuelCost();}
        public long beforeEnergy(){return kind==Kind.PURCHASE?0:refill.beforeEnergy();}
        public long afterEnergy(){return kind==Kind.PURCHASE?VehicleFuel49.initialDeliveryEnergy(5000000):refill.targetEnergy();}
        public long capacity(){return kind==Kind.PURCHASE?5000000:refill.beforeMeter().capacity();}
        public long creditBeforeNumerator(){return kind==Kind.PURCHASE?0:refill.beforeMeter().creditNumerator();}
        public long creditAfterNumerator(){return kind==Kind.PURCHASE?0:refill.quote().nextMeter().creditNumerator();}
        public ShopPurchase40.Price price(){return kind==Kind.PURCHASE?purchase.price():new ShopPurchase40.Price(0, fuelCost()==0?Map.of():Map.of(ResourceType.FUEL,fuelCost()));}
    }
    private VehicleService50(){}
    static String lockKey(UUID id){return PREFIX+"lock:"+id;}
    static String paidKey(UUID id){return PREFIX+"paid:"+id;}
    static String meterKey(UUID nation,UUID vehicle){return PREFIX+"meter:"+nation+":"+vehicle;}
    public static boolean locked(Entity entity){
        if(!(entity.level() instanceof ServerLevel level))return false;
        String lock=CatanSavedData.get(level.getServer()).placedMapAsset(lockKey(entity.getUUID()));
        return lock!=null&&!NONE.equals(lock);
    }
    public static boolean energyMutationAllowed(Entity entity){
        // Native deserializeNBT restores saved storage directly; runtime mutations use these guards.
        return !locked(entity)||INTERNAL.get().contains(entity.getUUID());
    }
    static NativeVehicleBay46.Spec spec(String offer)throws IOException{return switch(offer){case "pickup"->NativeVehicleBay46.PICKUP;case "lav150"->NativeVehicleBay46.LAV;default->throw new IOException("Unsupported vehicle");};}
    public static String offer(Entity entity)throws IOException{
        String id=String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()));
        return switch(id){case "superbwarfare:sodayo_pick_up"->"pickup";case "superbwarfare:lav_150"->"lav150";default->throw new IOException("This vehicle is not offered for factory servicing");};
    }
    static VehicleFuel49.Tariff tariff(String offer)throws IOException{return switch(offer){case "pickup"->VehicleFuel49.PICKUP;case "lav150"->VehicleFuel49.LAV;default->throw new IOException("Unknown fuel tariff");};}
    static void definition(ServerLevel level,String offer)throws IOException{
        var s=spec(offer);try(var in=level.getServer().getResourceManager().getResourceOrThrow(new ResourceLocation("superbwarfare","sbw/vehicles/"+s.id()+".json")).open()){
            byte[] bytes=in.readNBytes(200001);if(bytes.length>200000||!TownHallRevisions.sha(bytes).equals(s.sha()))throw new IOException("Vehicle definition changed; service held");
        }
    }
    public static long energy(Entity entity)throws IOException{
        try{return ((Number)entity.getClass().getMethod("getEnergy").invoke(entity)).longValue();}catch(ReflectiveOperationException|ClassCastException ex){throw new IOException("Native vehicle energy unavailable",ex);}
    }
    public static long capacity(Entity entity)throws IOException{
        definition((ServerLevel)entity.level(),offer(entity));
        // Both exact pinned data definitions declare 5,000,000; do not infer from arbitrary mod entities.
        long current=energy(entity);long actual;
        try{actual=((Number)entity.getClass().getMethod("getMaxEnergy").invoke(entity)).longValue();}catch(ReflectiveOperationException|ClassCastException ex){throw new IOException("Native capacity unavailable",ex);}
        if(actual!=5000000||current<0||current>actual)throw new IOException("Native energy/capacity differs from qualified definition");return actual;
    }
    private static void setEnergy(Entity e,long target)throws IOException{
        if(target<0||target>capacity(e))throw new IOException("Invalid energy target");
        INTERNAL.get().add(e.getUUID());try{e.getClass().getMethod("setEnergy",int.class).invoke(e,Math.toIntExact(target));}
        catch(ReflectiveOperationException ex){throw new IOException("Native energy write failed",ex);}finally{INTERNAL.get().remove(e.getUUID());}
        if(energy(e)!=target)throw new IOException("Native energy write refused");
    }
    static String world(ServerLevel level)throws IOException{return "vehicle50:"+TownHallRevisions.sha((level.getServer().getWorldPath(LevelResource.ROOT).toRealPath()+"\n"+level.getSeed()+"\n"+level.dimension().location()).getBytes(StandardCharsets.UTF_8));}
    public static Quote quotePurchase(ServerPlayer player,VehicleFactory50.Binding binding,String offer)throws IOException{
        binding=VehicleFactory50.revalidate(player,binding);definition(player.serverLevel(),offer);ensureNoPending(player,binding);
        VehicleBay45.Bay bay=VehicleFactory50.bayFor(player,binding,offer);VehicleFactory50.validateBay(player,binding,bay,null);
        UUID id=UUID.randomUUID(),entity=UUID.randomUUID();
        var intent=new VehicleDelivery46.Intent(id,player.getUUID(),binding.nation(),entity,world(player.serverLevel()),offer,CATALOG,bay,VehiclePayment47.price(offer));
        return new Quote(id,player.getUUID(),binding.nation(),entity,Kind.PURCHASE,offer,binding.identity(),player.serverLevel().getGameTime(),intent,null);
    }
    public static Quote quoteRefuel(ServerPlayer player,VehicleFactory50.Binding binding,UUID vehicle,VehicleFuel49.Mode mode)throws IOException{
        binding=VehicleFactory50.revalidate(player,binding);ensureNoPending(player,binding);Entity e=entity(player.serverLevel(),vehicle);VehicleFactory50.checkVehicle(player,binding,e);
        if(locked(e))throw new IOException("Vehicle has an unfinished service operation");
        String offer=offer(e);long cap=capacity(e);var meter=meter(CatanSavedData.get(player.server),binding.nation(),vehicle,tariff(offer),cap);
        var q=VehicleFuel49.quote(tariff(offer),cap,energy(e),mode,meter);if(q.deliveredEnergy()==0)throw new IOException("Vehicle is already full");
        UUID id=UUID.randomUUID();var intent=new VehicleRefueling49.Intent(id,player.getUUID(),binding.nation(),vehicle,world(player.serverLevel()),digest(binding.identity()),tariff(offer),mode,meter,q);
        return new Quote(id,player.getUUID(),binding.nation(),vehicle,Kind.REFUEL,offer,binding.identity(),player.serverLevel().getGameTime(),null,intent);
    }
    public static String confirm(ServerPlayer player,Quote quote)throws IOException{
        var binding=guard(player,quote,false);long now=player.serverLevel().getGameTime();
        if(now<quote.createdTick()||now-quote.createdTick()>=1200)throw new IOException("Vehicle quote expired; open the counter again");
        Path dir=directory(binding.root());Path intent=dir.resolve(quote.id()+".quote");
        if(Files.exists(intent,LinkOption.NOFOLLOW_LINKS))throw new IOException("This quote has already been confirmed; use recovery");
        ensureNoPending(player,binding);
        try(var files=Files.newDirectoryStream(dir,"*.quote")){int count=0;for(Path ignored:files)if(++count>=LIMIT)throw new IOException("Vehicle history limit reached; preserve records for review");}
        ledgerReady(player.serverLevel());
        if(!new EconomyCost(quote.price().money(),quote.price().resources()).canAfford(ShopFacility40.leader(player)))throw new IOException("Not enough nation supplies");
        if(quote.kind()==Kind.PURCHASE)VehicleFactory50.validateBay(player,binding,quote.purchase().bay(),null);
        else {Entity e=entity(player.serverLevel(),quote.vehicle());VehicleFactory50.checkVehicle(player,binding,e);if(locked(e)||energy(e)!=quote.beforeEnergy()||!meter(CatanSavedData.get(player.server),quote.nation(),quote.vehicle(),quote.refill().tariff(),quote.capacity()).equals(quote.refill().beforeMeter()))throw new IOException("Vehicle fuel changed; request a new quote");}
        if(quote.kind()==Kind.REFUEL)saveVehicle(player.serverLevel(),entity(player.serverLevel(),quote.vehicle()));
        writeNew(intent,encode(quote));
        return execute(player,binding,quote);
    }
    public static String recover(ServerPlayer player,VehicleFactory50.Binding binding,UUID operation)throws IOException{
        binding=VehicleFactory50.revalidate(player,binding);Quote q=readQuote(directory(binding.root()).resolve(operation+".quote"));
        if(!q.facility().equals(binding.identity()))throw new IOException("Operation belongs to a different factory");
        guard(player,q,true);return execute(player,binding,q);
    }
    public static List<Pending> pending(ServerPlayer player,VehicleFactory50.Binding binding)throws IOException{
        binding=VehicleFactory50.revalidate(player,binding);Path dir=directory(binding.root());List<Pending> result=new ArrayList<>();int count=0;
        try(var files=Files.newDirectoryStream(dir,"*.quote")){for(Path file:files){if(++count>LIMIT)throw new IOException("Vehicle history limit reached");Quote q=readQuote(file);
            if(q.nation().equals(binding.nation())&&q.facility().equals(binding.identity())&&needsRecovery(dir,q,CatanSavedData.get(player.server)))result.add(new Pending(q.id(),(q.kind()==Kind.PURCHASE?"Vehicle purchase: ":"Refuel: ")+q.offer()));}}
        result.sort(Comparator.comparing(p->p.operation().toString()));return List.copyOf(result);
    }
    /** Location hints only. Do not migrate old intents, change the local pending policy,
     * or reset the nation/vehicle-scoped fractional Fuel meter. */
    public static List<Pending> otherFactoryOperations(ServerPlayer player,VehicleFactory50.Binding binding)throws IOException {
        binding=VehicleFactory50.revalidate(player,binding);Path dir=directory(binding.root());
        List<Pending> result=new ArrayList<>();int count=0;
        try(var files=Files.newDirectoryStream(dir,"*.quote")){for(Path file:files){
            if(++count>LIMIT)throw new IOException("Vehicle history limit reached");Quote q=readQuote(file);
            if(q.actor().equals(player.getUUID())&&q.nation().equals(binding.nation())&&!q.facility().equals(binding.identity())
                    &&needsRecovery(dir,q,CatanSavedData.get(player.server)))
                result.add(new Pending(q.id(),(q.kind()==Kind.PURCHASE?"Unfinished vehicle purchase at ":"Unfinished refuel at ")
                        +VehicleSites52.origin(q.facility())+" · recorded level "+q.facility().getInt("level")+". Original factory checks still apply."));
        }}
        result.sort(Comparator.comparing(p->p.operation().toString()));return List.copyOf(result);
    }
    private static void ensureNoPending(ServerPlayer player,VehicleFactory50.Binding binding)throws IOException{if(!pending(player,binding).isEmpty())throw new IOException("Recover the unfinished factory operation before starting another");}
    private static VehicleFactory50.Binding guard(ServerPlayer player,Quote q,boolean recovery)throws IOException{
        var binding=VehicleFactory50.recheck(player,q.facility());
        if(!player.getUUID().equals(q.actor())||!binding.nation().equals(q.nation()))throw new IOException("Original nation leader is required");
        definition(player.serverLevel(),q.offer());
        if(q.kind()==Kind.PURCHASE){var i=q.purchase();if(!i.id().equals(q.id())||!i.actor().equals(q.actor())||!i.nation().equals(q.nation())||!i.entity().equals(q.vehicle())||!i.offer().equals(q.offer())||!i.world().equals(world(player.serverLevel()))||!CATALOG.equals(i.catalog())||!VehiclePayment47.price(q.offer()).equals(i.price())||!VehicleFactory50.bayIdentityFor(player,binding,q.offer()).equals(i.bay()))throw new IOException("Changed purchase catalog or identity");}
        else {var i=q.refill();if(!i.operation().equals(q.id())||!i.actor().equals(q.actor())||!i.nation().equals(q.nation())||!i.vehicle().equals(q.vehicle())||!i.world().equals(world(player.serverLevel()))||!i.facility().equals(digest(q.facility()))||!i.tariff().equals(tariff(q.offer())))throw new IOException("Changed refueling catalog or identity");}
        return binding;
    }
    private static String execute(ServerPlayer player,VehicleFactory50.Binding binding,Quote q)throws IOException{
        guard(player,q,true);Path dir=directory(binding.root());
        if(finishCompleted(player,binding,q,dir,false))return "This operation is already complete. No charge or fuel was repeated.";
        lock(player.serverLevel(),q);
        if(q.kind()==Kind.PURCHASE){var journal=new VehicleDelivery46.FileJournal(dir.resolve("purchases"));var env=new Purchase(player,binding,q,journal);VehicleDelivery46.prepare(journal,q.purchase(),env);var result=VehicleDelivery46.execute(journal,q.id(),env);if(!result.complete())return "Vehicle purchase held: "+result.reason();}
        else {var env=new Refuel(player,binding,q,dir);if(VehicleRefueling49.execute(env,q.refill())!=VehicleRefueling49.Result.COMPLETE)return "Refueling is held for recovery; no charge or fuel will be repeated.";}
        markComplete(dir,q);if(!finishCompleted(player,binding,q,dir,false))throw new IOException("Completion receipt was not retained");
        return q.kind()==Kind.PURCHASE?"Vehicle purchased with 25% fuel. Your nation was charged once.":"Refueling complete. Added "+q.refill().addedEnergy()*100/q.capacity()+"% capacity for "+q.fuelCost()+" Fuel.";
    }
    static boolean finishCompleted(ServerPlayer player,VehicleFactory50.Binding binding,Quote q,Path dir,boolean fixture)throws IOException{
        if(fixture)fixtureScope(player);
        if(complete(dir,q)){
            CatanSavedData data=CatanSavedData.get(player.server);
            if(!fingerprint(q).equals(data.placedMapAsset(paidKey(q.id()))))throw new IOException("Completed operation has no matching payment receipt");
            String lock=data.placedMapAsset(lockKey(q.vehicle()));
            if(fingerprint(q).equals(lock)){
                ledgerReady(player.serverLevel());
                if(q.kind()==Kind.REFUEL){var env=new Refuel(player,binding,q,dir,fixture);if(env.debitProof(q.refill())!=VehicleRefueling49.Proof.EXACT||env.energyProof(q.refill())!=VehicleRefueling49.Proof.EXACT)throw new IOException("Completed refill needs exact native receipts before unlocking");}
                else {var env=new Purchase(player,binding,q,new VehicleDelivery46.FileJournal(dir.resolve("purchases")),fixture);if(env.debitState(q.purchase())!=VehicleDelivery46.Debit.EXACT||env.deliveryState(q.purchase())!=VehicleDelivery46.Delivery.EXACT_SAVED)throw new IOException("Completed purchase needs exact native receipts before unlocking");}
                unlock(player.serverLevel(),q);
            }
            else if(lock!=null&&!NONE.equals(lock))throw new IOException("Vehicle lock belongs to another operation");
            return true;
        }
        return false;
    }
    static boolean needsRecovery(Path dir,Quote q,CatanSavedData data)throws IOException{return !complete(dir,q)||fingerprint(q).equals(data.placedMapAsset(lockKey(q.vehicle())));}
    static void lock(ServerLevel level,Quote q)throws IOException{
        CatanSavedData data=CatanSavedData.get(level.getServer());String key=lockKey(q.vehicle()),expected=fingerprint(q),old=data.placedMapAsset(key);
        if(expected.equals(old)){ledgerReady(level);return;}
        if(old!=null&&!NONE.equals(old))throw new IOException("Vehicle is reserved by another operation");
        ledgerReady(level);data.setPlacedMapAsset(key,expected);saveLedger(level);
    }
    static void unlock(ServerLevel level,Quote q)throws IOException{
        CatanSavedData data=CatanSavedData.get(level.getServer());if(!fingerprint(q).equals(data.placedMapAsset(lockKey(q.vehicle()))))throw new IOException("Vehicle lock changed");
        ledgerReady(level);String original=fingerprint(q);data.setPlacedMapAsset(lockKey(q.vehicle()),NONE);
        try{saveLedger(level);}catch(IOException|RuntimeException failed){
            // A failed release must not expose the vehicle to driving/charging merely because
            // memory was updated before its durable save/readback completed.
            data.setPlacedMapAsset(lockKey(q.vehicle()),original);throw failed;
        }
    }
    static void ledgerReady(ServerLevel level)throws IOException{CatanSavedData data=CatanSavedData.get(level.getServer());data.setDirty();level.getServer().overworld().getDataStorage().save();if(!data.save(new CompoundTag()).equals(VehiclePayment47.disk(level)))throw new IOException("Nation ledger native save/readback differs");}
    private static void saveLedger(ServerLevel level)throws IOException{ledgerReady(level);}
    static VehicleFuel49.Meter meter(CatanSavedData data,UUID nation,UUID entity,VehicleFuel49.Tariff tariff,long capacity)throws IOException{
        String raw=data.placedMapAsset(meterKey(nation,entity));if(raw==null)return VehicleFuel49.emptyMeter(tariff,capacity);
        try{String[] parts=raw.split(":",-1);if(parts.length!=5)throw new IllegalArgumentException();var m=new VehicleFuel49.Meter(parts[0],parts[1],Long.parseLong(parts[2]),Long.parseLong(parts[3]),Long.parseLong(parts[4]));VehicleFuel49.quote(tariff,capacity,capacity,VehicleFuel49.Mode.FILL,m);return m;}catch(RuntimeException bad){throw new IOException("Fuel meter is corrupt or from another tariff; preserve for review",bad);}
    }
    static String meterString(VehicleFuel49.Meter m){return m.catalog()+":"+m.offer()+":"+m.capacity()+":"+m.fullTankFuel()+":"+m.creditNumerator();}
    static Entity entity(ServerLevel level,UUID id)throws IOException{Entity e=level.getEntity(id);if(e==null||!e.isAlive()||e.isRemoved())throw new IOException("Vehicle is absent or unloaded; no replacement will be created");return e;}
    static CompoundTag saved(ServerLevel level,UUID id,ChunkPos c)throws IOException{
        if(!level.dimension().location().toString().equals("minecraft:overworld")||level.getChunkSource().getChunkNow(c.x,c.z)==null||!level.areEntitiesLoaded(c.toLong()))throw new IOException("Native vehicle chunk is not ready");
        Path root=level.getServer().getWorldPath(LevelResource.ROOT).toRealPath();return VehicleNativeDelivery47.readEntityChunk(root.resolve("entities/r."+(c.x>>5)+"."+(c.z>>5)+".mca"),c,id);
    }
    static void saveVehicle(ServerLevel level,Entity entity)throws IOException{
        if(!level.getServer().saveEverything(true,true,true))throw new IOException("Native world save refused");
        CompoundTag stored=saved(level,entity.getUUID(),entity.chunkPosition());if(stored==null||!stored.equals(VehicleNativeDelivery47.snapshot(entity)))throw new IOException("Native entity save/readback differs");
    }
    private static boolean purchaseIdentity(Entity e,Quote q)throws IOException{
        return e.isAlive()&&!e.isRemoved()&&e.getUUID().equals(q.vehicle())&&offer(e).equals(q.offer())
                &&e.level().dimension().location().toString().equals(q.purchase().bay().facility().dimension())
                &&e.getPersistentData().contains(NationProcurement.PURCHASED_TAG,Tag.TAG_BYTE)&&e.getPersistentData().getBoolean(NationProcurement.PURCHASED_TAG)
                &&e.getPersistentData().getString(RECEIPT).equals(fingerprint(q))&&e.getPersistentData().getString(NationProcurement.NATION_TAG).equals(q.nation().toString());
    }
    private static boolean purchasePose(Entity e,Quote q){
        var p=q.purchase().bay().worldSpawn();if(!Double.isFinite(e.getX())||!Double.isFinite(e.getY())||!Double.isFinite(e.getZ())||e.position().distanceToSqr(new net.minecraft.world.phys.Vec3(p.x(),p.y(),p.z()))>0.00000001
                ||!Float.isFinite(e.getYRot())||!Float.isFinite(e.getXRot())||Math.abs(Math.IEEEremainder(e.getYRot()-q.purchase().bay().worldHeading(),360))>0.0001||Math.abs(e.getXRot())>0.000001)return false;
        try{for(float t:new float[]{0,1}){double roll=((Number)e.getClass().getMethod("getRoll",float.class).invoke(e,t)).doubleValue();if(!Double.isFinite(roll)||Math.abs(roll)>0.000001)return false;}return true;}catch(ReflectiveOperationException|RuntimeException unsupported){return false;}
    }
    private static final class Purchase implements VehicleDelivery46.Environment{
        final ServerPlayer player;final VehicleFactory50.Binding binding;final Quote quote;final VehicleDelivery46.Journal journal;final ServerLevel level;final boolean fixture;
        Purchase(ServerPlayer p,VehicleFactory50.Binding b,Quote q,VehicleDelivery46.Journal j){this(p,b,q,j,false);}
        Purchase(ServerPlayer p,VehicleFactory50.Binding b,Quote q,VehicleDelivery46.Journal j,boolean f){player=p;binding=b;quote=q;journal=j;level=p.serverLevel();fixture=f;}
        public void guard(VehicleDelivery46.Intent i)throws IOException{if(fixture)fixturePurchaseGuard(player,quote);else VehicleService50.guard(player,quote,true);if(!i.equals(quote.purchase()))throw new IOException("Foreign purchase intent");Entity e=level.getEntity(i.entity());if(e!=null&&!purchaseIdentity(e,quote))throw new IOException("Existing UUID is not this original purchased vehicle");if(!fixture)VehicleFactory50.validateBay(player,binding,i.bay(),e);}
        public VehicleDelivery46.Debit debitState(VehicleDelivery46.Intent i)throws IOException{
            if(!CatanSavedData.get(player.server).save(new CompoundTag()).equals(VehiclePayment47.disk(level)))return VehicleDelivery46.Debit.UNKNOWN;
            String receipt=CatanSavedData.get(player.server).placedMapAsset(paidKey(i.id()));return receipt==null?VehicleDelivery46.Debit.ABSENT:receipt.equals(fingerprint(quote))?VehicleDelivery46.Debit.EXACT:VehicleDelivery46.Debit.CONFLICT;
        }
        public void debitAndSave(VehicleDelivery46.Intent i)throws IOException{
            guard(i);if(journal.read(i.id()).phase()!=VehicleDelivery46.Phase.DEBIT_ATTEMPTED||debitState(i)!=VehicleDelivery46.Debit.ABSENT)throw new IOException("Missing clean payment attempt");
            CatanSavedData data=CatanSavedData.get(player.server);NationData nation=ShopFacility40.leader(player);EconomyCost cost=new EconomyCost(i.price().money(),i.price().resources());if(!cost.canAfford(nation))throw new IOException("Not enough nation supplies");
            cost.charge(nation);data.setPlacedMapAsset(paidKey(i.id()),fingerprint(quote));saveLedger(level);
        }
        public VehicleDelivery46.Delivery deliveryState(VehicleDelivery46.Intent i)throws IOException{
            for(ServerLevel other:player.server.getAllLevels())if(other!=level&&other.getEntity(i.entity())!=null)return VehicleDelivery46.Delivery.CONFLICT;
            var p=i.bay().worldSpawn();ChunkPos c=new ChunkPos(((int)Math.floor(p.x()))>>4,((int)Math.floor(p.z()))>>4);CompoundTag disk=saved(level,i.entity(),c);Entity e=level.getEntity(i.entity());
            if(e==null){if(disk!=null)return VehicleDelivery46.Delivery.UNKNOWN;var r=journal.read(i.id());return r==null||r.phase().ordinal()<VehicleDelivery46.Phase.SPAWN_ATTEMPTED.ordinal()?VehicleDelivery46.Delivery.ABSENT_LOADED:VehicleDelivery46.Delivery.UNKNOWN;}
            if(!purchaseIdentity(e,quote))return VehicleDelivery46.Delivery.CONFLICT;
            if(!purchasePose(e,quote)||energy(e)!=quote.afterEnergy())return VehicleDelivery46.Delivery.UNKNOWN;
            return disk!=null&&disk.equals(VehicleNativeDelivery47.snapshot(e))?VehicleDelivery46.Delivery.EXACT_SAVED:VehicleDelivery46.Delivery.EXACT_UNSAVED;
        }
        public void spawnAndSave(VehicleDelivery46.Intent i)throws IOException{
            guard(i);if(journal.read(i.id()).phase()!=VehicleDelivery46.Phase.SPAWN_ATTEMPTED||debitState(i)!=VehicleDelivery46.Debit.EXACT)throw new IOException("Missing durable spawn boundary");
            for(ServerLevel other:player.server.getAllLevels())if(other.getEntity(i.entity())!=null)throw new IOException("Preselected UUID already exists");
            var destination=i.bay().worldSpawn();if(saved(level,i.entity(),new ChunkPos(((int)Math.floor(destination.x()))>>4,((int)Math.floor(destination.z()))>>4))!=null)throw new IOException("Preselected UUID already saved");
            Entity e;try{e=NativeVehicleBay46.create(level,spec(i.offer()));e.setUUID(i.entity());var p=i.bay().worldSpawn();NativeVehicleBay46.pose(e,new net.minecraft.world.phys.Vec3(p.x(),p.y(),p.z()),i.bay().worldHeading());}catch(Exception ex){throw new IOException("Native vehicle creation failed",ex);}
            e.getPersistentData().putBoolean(NationProcurement.PURCHASED_TAG,true);e.getPersistentData().putString(NationProcurement.NATION_TAG,i.nation().toString());e.getPersistentData().putString(RECEIPT,fingerprint(quote));setEnergy(e,quote.afterEnergy());
            if(!level.addFreshEntity(e))throw new IOException("Native world refused vehicle");saveVehicle(level,e);
        }
    }
    private static final class Refuel implements VehicleRefueling49.Environment{
        final ServerPlayer player;final VehicleFactory50.Binding binding;final Quote quote;final Path dir;final ServerLevel level;final boolean fixture;
        Refuel(ServerPlayer p,VehicleFactory50.Binding b,Quote q,Path d){this(p,b,q,d,false);}
        Refuel(ServerPlayer p,VehicleFactory50.Binding b,Quote q,Path d,boolean f){player=p;binding=b;quote=q;dir=d;level=p.serverLevel();fixture=f;}
        public void guard(VehicleRefueling49.Intent i)throws IOException{if(fixture)fixtureGuard(player,quote);else VehicleService50.guard(player,quote,true);if(!i.equals(quote.refill()))throw new IOException("Foreign refueling intent");Entity e=entity(level,i.vehicle());if(capacity(e)!=i.beforeMeter().capacity()||!offer(e).equals(quote.offer()))throw new IOException("Native refueling capacity/type changed");if(!fixture)VehicleFactory50.checkVehicle(player,binding,e);if(!fingerprint(quote).equals(CatanSavedData.get(player.server).placedMapAsset(lockKey(i.vehicle()))))throw new IOException("Vehicle reservation missing");}
        public VehicleRefueling49.Record read(UUID id)throws IOException{
            if(!id.equals(quote.id()))throw new IOException("Foreign fuel record");VehicleRefueling49.Record last=null;boolean gap=false;
            for(var phase:VehicleRefueling49.Phase.values()){Path file=dir.resolve(id+".fuel."+phase.ordinal());if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS)){gap=true;continue;}if(gap)throw new IOException("Gapped fuel phases");CompoundTag n=ProducerJournal.read(file);if(!n.equals(phaseTag(quote,phase.name())))throw new IOException("Changed fuel phase identity");last=new VehicleRefueling49.Record(quote.refill(),phase);}return last;
        }
        public void save(VehicleRefueling49.Record expected,VehicleRefueling49.Record next)throws IOException{
            if(!Objects.equals(read(quote.id()),expected)||!next.intent().equals(quote.refill())||(expected==null?next.phase()!=VehicleRefueling49.Phase.PREPARED:next.phase().ordinal()!=expected.phase().ordinal()+1))throw new IOException("Fuel journal transition refused");
            writeNew(dir.resolve(quote.id()+".fuel."+next.phase().ordinal()),phaseTag(quote,next.phase().name()));
        }
        public VehicleRefueling49.Proof debitProof(VehicleRefueling49.Intent i)throws IOException{
            CatanSavedData data=CatanSavedData.get(player.server);if(!data.save(new CompoundTag()).equals(VehiclePayment47.disk(level)))return VehicleRefueling49.Proof.UNKNOWN;
            String receipt=data.placedMapAsset(paidKey(i.operation()));return receipt==null?VehicleRefueling49.Proof.ABSENT:receipt.equals(fingerprint(quote))?VehicleRefueling49.Proof.EXACT:VehicleRefueling49.Proof.CONFLICT;
        }
        public VehicleFuel49.Meter currentMeter(VehicleRefueling49.Intent i)throws IOException{return meter(CatanSavedData.get(player.server),i.nation(),i.vehicle(),i.tariff(),i.beforeMeter().capacity());}
        public void debitAndSave(VehicleRefueling49.Intent i)throws IOException{
            guard(i);if(read(i.operation()).phase()!=VehicleRefueling49.Phase.DEBIT_ATTEMPTED||debitProof(i)!=VehicleRefueling49.Proof.ABSENT||!currentMeter(i).equals(i.beforeMeter())||currentEnergy(i)!=i.beforeEnergy())throw new IOException("Fuel debit precondition changed");
            CatanSavedData data=CatanSavedData.get(player.server);NationData nation=ShopFacility40.leader(player);if(nation.resource(ResourceType.FUEL)<i.fuelCost())throw new IOException("Not enough national Fuel");
            nation.addResource(ResourceType.FUEL,-i.fuelCost());data.setPlacedMapAsset(meterKey(i.nation(),i.vehicle()),meterString(i.quote().nextMeter()));data.setPlacedMapAsset(paidKey(i.operation()),fingerprint(quote));saveLedger(level);
        }
        public VehicleRefueling49.Proof energyProof(VehicleRefueling49.Intent i)throws IOException{
            Entity e=entity(level,i.vehicle());CompoundTag disk=saved(level,i.vehicle(),e.chunkPosition());String key=i.operation().toString();String memory=e.getPersistentData().getCompound("CatanCraftFuel50").getString(key);
            if(disk==null)return VehicleRefueling49.Proof.UNKNOWN;String stored=disk.getCompound("ForgeData").getCompound("CatanCraftFuel50").getString(key);
            if(memory.isEmpty()&&stored.isEmpty())return VehicleRefueling49.Proof.ABSENT;
            return memory.equals(fingerprint(quote))&&stored.equals(memory)?VehicleRefueling49.Proof.EXACT:VehicleRefueling49.Proof.CONFLICT;
        }
        public long currentEnergy(VehicleRefueling49.Intent i)throws IOException{return energy(entity(level,i.vehicle()));}
        public void addEnergyAndSave(VehicleRefueling49.Intent i)throws IOException{
            guard(i);if(read(i.operation()).phase()!=VehicleRefueling49.Phase.ENERGY_ATTEMPTED||debitProof(i)!=VehicleRefueling49.Proof.EXACT||energyProof(i)!=VehicleRefueling49.Proof.ABSENT||currentEnergy(i)!=i.beforeEnergy())throw new IOException("Fuel addition precondition changed");
            Entity e=entity(level,i.vehicle());CompoundTag receipts=e.getPersistentData().getCompound("CatanCraftFuel50").copy();if(receipts.size()>=LIMIT)throw new IOException("Vehicle fuel history limit reached");
            setEnergy(e,i.targetEnergy());receipts.putString(i.operation().toString(),fingerprint(quote));e.getPersistentData().put("CatanCraftFuel50",receipts);saveVehicle(level,e);
        }
    }
    /** Tests replace only physical counter authority; all ledger, entity, phase and lock code is real. */
    static VehicleRefueling49.Environment fixtureEnvironment(ServerPlayer player,Quote quote,Path dir)throws IOException{
        fixtureGuard(player,quote);Path root=player.server.getWorldPath(LevelResource.ROOT).toRealPath();
        if(!dir.toAbsolutePath().normalize().startsWith(root.resolve("data")))throw new IOException("Foreign test journal");
        VehiclePayment47.safe(dir);Files.createDirectories(dir);return new Refuel(player,null,quote,dir,true);
    }
    private static void fixtureScope(ServerPlayer player)throws IOException{fixtureWorld(player.serverLevel());}
    static void fixtureWorld(ServerLevel level)throws IOException{
        var server=level.getServer();Path root=server.getWorldPath(LevelResource.ROOT).toRealPath();
        if(!(server instanceof net.minecraft.gametest.framework.GameTestServer)||!server.isSameThread()||!server.getPlayerList().getPlayers().isEmpty()
                ||!root.getFileName().toString().equals("world")||!Set.of("controlled-catancraft_service50-run","controlled-catancraft_service50_restart-run").contains(root.getParent().getFileName().toString())
                ||!Files.readString(root.getParent().resolve(".catancraft-controlled-fixture-v1")).equals("CatanCraft synthetic flat fixture v1\n")
                ||level.getSeed()!=0||!level.dimension().location().toString().equals("minecraft:overworld"))throw new IOException("Foreign service fixture");
        if(root.getParent().getFileName().toString().equals("controlled-catancraft_service50_restart-run")&&(!System.getProperty("catancraft.service50Source","").matches("[0-9a-f]{40}")||!Set.of("seed","verify").contains(System.getProperty("catancraft.service50Phase",""))))throw new IOException("Unbound restart service fixture");
    }
    private static void fixtureGuard(ServerPlayer player,Quote q)throws IOException{
        fixtureScope(player);ServerLevel level=player.serverLevel();
        if(q.kind()!=Kind.REFUEL||!q.actor().equals(player.getUUID())||!ShopFacility40.leader(player).id().equals(q.nation())||!q.refill().world().equals(world(level)))throw new IOException("Foreign fixture identity");
        Entity e=entity(level,q.vehicle());if(!e.getPersistentData().getBoolean(NationProcurement.PURCHASED_TAG)||!e.getPersistentData().getString(NationProcurement.NATION_TAG).equals(q.nation().toString())||!e.getPassengers().isEmpty()||e.getDeltaMovement().lengthSqr()>0.000001||!offer(e).equals(q.offer()))throw new IOException("Invalid fixture vehicle");
        capacity(e);
    }
    static VehicleDelivery46.Environment fixturePurchaseEnvironment(ServerPlayer player,Quote q,VehicleDelivery46.Journal journal)throws IOException{
        fixturePurchaseGuard(player,q);return new Purchase(player,null,q,journal,true);
    }
    private static void fixturePurchaseGuard(ServerPlayer player,Quote q)throws IOException{
        fixtureScope(player);var i=q.purchase();
        if(q.kind()!=Kind.PURCHASE||!q.actor().equals(player.getUUID())||!ShopFacility40.leader(player).id().equals(q.nation())||!i.world().equals(world(player.serverLevel()))||!i.catalog().equals(CATALOG)||!i.price().equals(VehiclePayment47.price(q.offer()))||!i.bay().facility().revision().equals("service50-synthetic-native-pad"))throw new IOException("Foreign native purchase fixture");
        definition(player.serverLevel(),q.offer());
    }
    private static Path directory(Path root)throws IOException{Path dir=root.resolve("data/catancraft_vehicle50");VehiclePayment47.safe(dir);Files.createDirectories(dir);VehiclePayment47.safe(dir);return dir;}
    private static void writeNew(Path file,CompoundTag value)throws IOException{VehiclePayment47.safe(file);if(Files.exists(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("Operation history already exists");ProducerJournal.write(file,value,false);if(!ProducerJournal.read(file).equals(value))throw new IOException("Operation history readback differs");}
    private static CompoundTag phaseTag(Quote q,String phase)throws IOException{CompoundTag n=new CompoundTag();n.putString("fingerprint",fingerprint(q));n.putString("phase",phase);return n;}
    private static boolean complete(Path dir,Quote q)throws IOException{Path p=dir.resolve(q.id()+".complete");if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))return false;if(!ProducerJournal.read(p).equals(phaseTag(q,"complete")))throw new IOException("Changed completion receipt");return true;}
    static void markComplete(Path dir,Quote q)throws IOException{if(!complete(dir,q))writeNew(dir.resolve(q.id()+".complete"),phaseTag(q,"complete"));}
    static String fingerprint(Quote q)throws IOException{return digest(encode(q));}
    static String digest(CompoundTag n)throws IOException{return TownHallRevisions.sha(canonical(n).getBytes(StandardCharsets.UTF_8));}
    private static String canonical(Tag n){if(n instanceof CompoundTag c){List<String> keys=new ArrayList<>(c.getAllKeys());Collections.sort(keys);StringBuilder s=new StringBuilder("{");for(String k:keys)s.append(StringTag.valueOf(k)).append(':').append(canonical(c.get(k))).append(',');return s.append('}').toString();}if(n instanceof ListTag l){StringBuilder s=new StringBuilder("[");for(Tag t:l)s.append(canonical(t)).append(',');return s.append(']').toString();}return n.toString();}
    static CompoundTag encode(Quote q){
        CompoundTag n=new CompoundTag();n.putInt("schema",1);n.putUUID("id",q.id());n.putUUID("actor",q.actor());n.putUUID("nation",q.nation());n.putUUID("vehicle",q.vehicle());n.putString("kind",q.kind().name());n.putString("offer",q.offer());n.put("facility",q.facility());n.putLong("created",q.createdTick());
        if(q.kind()==Kind.PURCHASE)n.put("purchase",VehicleDelivery46.encode(new VehicleDelivery46.Record(q.purchase(),VehicleDelivery46.Phase.PREPARED)));
        else {var i=q.refill();CompoundTag f=new CompoundTag();f.putString("world",i.world());f.putString("facility",i.facility());f.putString("catalog",i.tariff().catalog());f.putString("offer",i.tariff().offer());f.putLong("full",i.tariff().fullTankFuel());f.putString("mode",i.mode().name());f.putLong("capacity",i.beforeMeter().capacity());f.putLong("credit",i.beforeMeter().creditNumerator());f.putLong("before",i.beforeEnergy());n.put("refill",f);}return n;
    }
    static Quote readQuote(Path file)throws IOException{
        VehiclePayment47.safe(file);CompoundTag n=ProducerJournal.read(file);try{
            if(n.getInt("schema")!=1)throw new IllegalArgumentException();UUID id=n.getUUID("id"),actor=n.getUUID("actor"),nation=n.getUUID("nation"),vehicle=n.getUUID("vehicle");Kind kind=Kind.valueOf(n.getString("kind"));VehicleDelivery46.Intent purchase=null;VehicleRefueling49.Intent refill=null;
            if(kind==Kind.PURCHASE)purchase=VehicleDelivery46.decode(n.getCompound("purchase")).intent();else {CompoundTag f=n.getCompound("refill");var t=new VehicleFuel49.Tariff(f.getString("catalog"),f.getString("offer"),f.getLong("full"));var m=new VehicleFuel49.Meter(t.catalog(),t.offer(),f.getLong("capacity"),t.fullTankFuel(),f.getLong("credit"));var mode=VehicleFuel49.Mode.valueOf(f.getString("mode"));refill=new VehicleRefueling49.Intent(id,actor,nation,vehicle,f.getString("world"),f.getString("facility"),t,mode,m,VehicleFuel49.quote(t,m.capacity(),f.getLong("before"),mode,m));}
            Quote q=new Quote(id,actor,nation,vehicle,kind,n.getString("offer"),n.getCompound("facility"),n.getLong("created"),purchase,refill);if(!n.equals(encode(q)))throw new IOException("Unknown or malformed vehicle quote fields");return q;
        }catch(RuntimeException bad){throw new IOException("Malformed vehicle quote; preserve for review",bad);}
    }
}
