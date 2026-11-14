package com.quin.catancraft.world;

import com.mojang.authlib.GameProfile;
import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.command.NationProcurement;
import com.quin.catancraft.data.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.*;
import net.minecraftforge.gametest.*;
import java.nio.file.*;
import java.util.*;

/** Real ledger + region persistence and provider capabilities. Synthetic parking pads only. */
@GameTestHolder("catancraft_service50") @PrefixGameTestTemplate(false)
public final class VehicleService50Tests {
    interface Work {void run(Fixture f)throws Exception;}
    @GameTest(template="townhall_state_empty",batch="service50_a",timeoutTicks=1000)
    public static void native_refuel_meter_and_replay(GameTestHelper h)throws Exception{run(h,0,f->{
        var q=f.quote(VehicleFuel49.Mode.ADD_QUARTER);var env=f.prepare(q);
        check(VehicleRefueling49.execute(env,q.refill())==VehicleRefueling49.Result.COMPLETE,"Refuel failed");
        check(f.nation.resource(ResourceType.FUEL)==92,"Wrong native Fuel debit");check(VehicleService50.energy(f.vehicle)==1250000,"Wrong native energy added");
        check(env.debitProof(q.refill())==VehicleRefueling49.Proof.EXACT&&env.energyProof(q.refill())==VehicleRefueling49.Proof.EXACT,"Native receipts differ");
        VehicleService50.unlock(f.level,q);f.vehicle.getClass().getMethod("setEnergy",int.class).invoke(f.vehicle,1200000);VehicleService50.saveVehicle(f.level,f.vehicle);
        VehicleService50.lock(f.level,q);var fresh=VehicleService50.fixtureEnvironment(f.player,q,f.dir);
        check(VehicleRefueling49.execute(fresh,q.refill())==VehicleRefueling49.Result.COMPLETE,"Receipt did not survive adapter recreation");
        check(VehicleService50.energy(f.vehicle)==1200000&&f.nation.resource(ResourceType.FUEL)==92,"Recovery restored consumed energy or charged twice");
        f.proof("native_refuel_meter_and_replay",q);VehicleService50.unlock(f.level,q);
    });}
    @GameTest(template="townhall_state_empty",batch="service50_b",timeoutTicks=1000)
    public static void reservation_blocks_native_external_charge_mount_and_restores_nbt(GameTestHelper h)throws Exception{run(h,1,f->{
        f.vehicle.getClass().getMethod("setEnergy",int.class).invoke(f.vehicle,1000000);VehicleService50.saveVehicle(f.level,f.vehicle);
        var q=f.quote(VehicleFuel49.Mode.ADD_QUARTER);f.prepare(q);
        var capability=f.vehicle.getCapability(ForgeCapabilities.ENERGY).orElseThrow(()->new IllegalStateException("Missing native energy capability"));
        check(capability.receiveEnergy(10000,false)==0&&capability.extractEnergy(10000,false)==0,"External capability bypassed reservation");
        f.vehicle.getClass().getMethod("setEnergy",int.class).invoke(f.vehicle,2000000);check(VehicleService50.energy(f.vehicle)==1000000,"Native setter bypassed lock");
        check(!f.player.startRiding(f.vehicle,true),"Mount bypassed service lock");
        CompoundTag saved=VehicleService50.saved(f.level,f.vehicle.getUUID(),f.vehicle.chunkPosition());
        Entity restored=EntityType.loadEntityRecursive(saved,f.level,e->e);check(restored!=null&&VehicleService50.energy(restored)==1000000,"Native NBT load lost locked energy");
        VehicleService50.unlock(f.level,q);check(capability.receiveEnergy(10000,false)==10000,"Unlocked capability remained blocked");
        check(VehicleService50.energy(f.vehicle)==1010000,"Unlocked charge did not reach native storage");f.proof("native_lock_capability_load",q);
    });}
    @GameTest(template="townhall_state_empty",batch="service50_c",timeoutTicks=1000)
    public static void attempted_missing_debit_holds_without_charge(GameTestHelper h)throws Exception{run(h,2,f->{
        var q=f.quote(VehicleFuel49.Mode.FILL);var env=f.prepare(q);var prepared=new VehicleRefueling49.Record(q.refill(),VehicleRefueling49.Phase.PREPARED);env.save(null,prepared);env.save(prepared,new VehicleRefueling49.Record(q.refill(),VehicleRefueling49.Phase.DEBIT_ATTEMPTED));
        for(int i=0;i<3;i++)check(VehicleRefueling49.execute(VehicleService50.fixtureEnvironment(f.player,q,f.dir),q.refill())==VehicleRefueling49.Result.HELD,"Missing attempted debit was retried");
        check(f.nation.resource(ResourceType.FUEL)==100&&VehicleService50.energy(f.vehicle)==0,"Held attempt changed state");f.proof("native_missing_attempt_hold",q);
    });}
    @GameTest(template="townhall_state_empty",batch="service50_d",timeoutTicks=1000)
    public static void durable_debit_and_addition_reconcile_once(GameTestHelper h)throws Exception{run(h,3,f->{
        var q=f.quote(VehicleFuel49.Mode.FILL);var env=f.prepare(q);var prepared=new VehicleRefueling49.Record(q.refill(),VehicleRefueling49.Phase.PREPARED);env.save(null,prepared);var attempted=new VehicleRefueling49.Record(q.refill(),VehicleRefueling49.Phase.DEBIT_ATTEMPTED);env.save(prepared,attempted);env.debitAndSave(q.refill());
        var fresh=VehicleService50.fixtureEnvironment(f.player,q,f.dir);var paid=new VehicleRefueling49.Record(q.refill(),VehicleRefueling49.Phase.PAID);fresh.save(attempted,paid);var add=new VehicleRefueling49.Record(q.refill(),VehicleRefueling49.Phase.ENERGY_ATTEMPTED);fresh.save(paid,add);fresh.addEnergyAndSave(q.refill());
        check(VehicleRefueling49.execute(VehicleService50.fixtureEnvironment(f.player,q,f.dir),q.refill())==VehicleRefueling49.Result.COMPLETE,"Durable addition did not reconcile");
        check(f.nation.resource(ResourceType.FUEL)==68&&VehicleService50.energy(f.vehicle)==5000000,"Saved addition/debit repeated");f.proof("native_saved_boundaries_once",q);VehicleService50.unlock(f.level,q);
    });}
    @GameTest(template="townhall_state_empty",batch="service50_e",timeoutTicks=1000)
    public static void new_purchase_includes_quarter_once(GameTestHelper h)throws Exception{purchase(h,4,"pickup");}
    @GameTest(template="townhall_state_empty",batch="service50_g",timeoutTicks=1000)
    public static void new_lav_purchase_includes_quarter_once(GameTestHelper h)throws Exception{purchase(h,6,"lav150");}
    private static void purchase(GameTestHelper h,int index,String offer)throws Exception{run(h,index,f->{
        f.vehicle.discard();VehicleService50.ledgerReady(f.level);f.nation.setTreasury(10000);for(ResourceType r:ResourceType.values())if(r!=ResourceType.FUEL)f.nation.addResource(r,100);VehicleService50.ledgerReady(f.level);
        UUID id=UUID.randomUUID(),entity=UUID.randomUUID();var facility=new VehicleBay45.Facility("synthetic","service50",f.nation.id(),"minecraft:overworld","service50-synthetic-native-pad","a".repeat(64),new VehicleBay45.Cell(f.x,100,f.z),0,new VehicleBay45.Cell(f.x+6,100,f.z),1);
        Set<VehicleBay45.Cell> support=new HashSet<>(),clear=new HashSet<>();for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++){support.add(new VehicleBay45.Cell(x,-1,z));for(int y=0;y<6;y++)clear.add(new VehicleBay45.Cell(x,y,z));}
        var spec=VehicleService50.spec(offer);var bay=new VehicleBay45.Bay("service50-native-pad",facility,"superbwarfare:"+spec.id(),spec.sha(),new VehicleBay45.Position(.5,.1,.5),0,support,clear,Set.of(new VehicleBay45.Cell(4,0,0)),Set.of(new VehicleBay45.Cell(0,0,5)));
        var intent=new VehicleDelivery46.Intent(id,f.player.getUUID(),f.nation.id(),entity,VehicleService50.world(f.level),offer,VehicleService50.CATALOG,bay,VehiclePayment47.price(offer));
        var q=new VehicleService50.Quote(id,f.player.getUUID(),f.nation.id(),entity,VehicleService50.Kind.PURCHASE,offer,new CompoundTag(),f.level.getGameTime(),intent,null);
        VehicleService50.lock(f.level,q);var journal=new VehicleDelivery46.FileJournal(f.dir.resolve("purchases"));var env=VehicleService50.fixturePurchaseEnvironment(f.player,q,journal);VehicleDelivery46.prepare(journal,intent,env);check(VehicleDelivery46.execute(journal,id,env).complete(),"Native purchase failed");f.vehicle=f.level.getEntity(entity);
        check(f.vehicle!=null&&VehicleService50.energy(f.vehicle)==1250000,"New vehicle did not receive exact quarter tank");check(f.nation.resource(ResourceType.FUEL)==100-q.fuelCost()&&f.nation.treasury()==10000-q.price().money(),"Purchase price changed");
        VehicleService50.unlock(f.level,q);f.vehicle.getClass().getMethod("setEnergy",int.class).invoke(f.vehicle,1200000);VehicleService50.saveVehicle(f.level,f.vehicle);
        check(VehicleDelivery46.execute(journal,id,VehicleService50.fixturePurchaseEnvironment(f.player,q,journal)).complete(),"Completed purchase replay failed");check(VehicleService50.energy(f.vehicle)==1200000&&f.nation.resource(ResourceType.FUEL)==100-q.fuelCost(),"Purchase replay refilled or recharged");f.proof("native_"+offer+"_purchase_quarter_once",q);
    });}
    @GameTest(template="townhall_state_empty",batch="service50_f",timeoutTicks=1000)
    public static void completion_marker_before_unlock_is_recoverable_and_checks_receipts(GameTestHelper h)throws Exception{run(h,5,f->{
        var q=f.quote(VehicleFuel49.Mode.ADD_QUARTER);var env=f.prepare(q);check(VehicleRefueling49.execute(env,q.refill())==VehicleRefueling49.Result.COMPLETE,"Original fuel failed");VehicleService50.markComplete(f.dir,q);
        var data=CatanSavedData.get(f.level.getServer());check(VehicleService50.needsRecovery(f.dir,q,data),"Completion marker hid active lock");String original=data.placedMapAsset(VehicleService50.paidKey(q.id()));data.setPlacedMapAsset(VehicleService50.paidKey(q.id()),"conflict");VehicleService50.ledgerReady(f.level);
        boolean held=false;try{VehicleService50.finishCompleted(f.player,null,q,f.dir,true);}catch(java.io.IOException expected){held=true;}check(held&&VehicleService50.locked(f.vehicle),"Conflicting paid receipt released lock");
        data.setPlacedMapAsset(VehicleService50.paidKey(q.id()),original);VehicleService50.ledgerReady(f.level);check(VehicleService50.finishCompleted(f.player,null,q,f.dir,true),"Completion marker did not reconcile");check(!VehicleService50.locked(f.vehicle)&&!VehicleService50.needsRecovery(f.dir,q,data),"Completion did not durably release reservation");
        f.vehicle.getClass().getMethod("setEnergy",int.class).invoke(f.vehicle,1200000);VehicleService50.saveVehicle(f.level,f.vehicle);check(VehicleService50.finishCompleted(f.player,null,q,f.dir,true),"Completed retry failed");check(VehicleService50.energy(f.vehicle)==1200000&&f.nation.resource(ResourceType.FUEL)==92,"Completed retry granted fuel or charged");f.proof("native_complete_before_unlock",q);
    });}
    @GameTest(template="townhall_state_empty",batch="service50_h",timeoutTicks=1000)
    public static void fractional_credit_survives_native_ledger_readback_and_pays_next_fill(GameTestHelper h)throws Exception{run(h,7,f->{
        if("15".equals(System.getProperty("catancraft.native42CombinedRuntimeArtifacts"))){
            var mods=net.minecraftforge.fml.ModList.get();
            check(mods.isLoaded("tacz")&&mods.isLoaded("superbwarfare"),"Combined service run needs both native providers");
            String model=mods.getModContainerById("simplebedrockmodel").orElseThrow().getModInfo().getVersion().toString();
            check(model.equals("2.6.2-forge-mc1.20.1"),"Combined service run selected another model runtime");
            CatanCraft.LOGGER.info("SERVICE50_COMBINED_RUNTIME_PASS tacz=true superbwarfare=true simplebedrockmodel={} runtimeArtifacts=15",model);
        }
        f.vehicle.getClass().getMethod("setEnergy",int.class).invoke(f.vehicle,4999999);VehicleService50.saveVehicle(f.level,f.vehicle);
        var first=f.quote(VehicleFuel49.Mode.FILL);check(first.fuelCost()==1&&first.creditAfterNumerator()==4999968,"Wrong first fractional quote");var firstEnv=f.prepare(first);
        check(VehicleRefueling49.execute(firstEnv,first.refill())==VehicleRefueling49.Result.COMPLETE,"First fractional native fill failed");
        check(f.nation.resource(ResourceType.FUEL)==99&&VehicleService50.energy(f.vehicle)==5000000,"First fractional fill charged or delivered wrong amount");
        CompoundTag firstDisk=VehiclePayment47.disk(f.level);CatanSavedData firstReload=CatanSavedData.load(firstDisk);
        var reloadedMeter=VehicleService50.meter(firstReload,f.nation.id(),f.vehicle.getUUID(),VehicleFuel49.PICKUP,5000000);
        check(reloadedMeter.creditNumerator()==4999968&&reloadedMeter.equals(first.refill().quote().nextMeter()),"Actual native ledger lost nonzero prepaid credit on reload");
        check(firstReload.nation(f.nation.id()).resource(ResourceType.FUEL)==99,"Reloaded nation Fuel differs from native debit");
        VehicleService50.unlock(f.level,first);f.vehicle.getClass().getMethod("setEnergy",int.class).invoke(f.vehicle,4999999);VehicleService50.saveVehicle(f.level,f.vehicle);
        // Build the next immutable quote from the independently reloaded native meter.
        UUID id=UUID.randomUUID();var refill=new VehicleRefueling49.Intent(id,f.player.getUUID(),f.nation.id(),f.vehicle.getUUID(),VehicleService50.world(f.level),VehicleService50.digest(first.facility()),VehicleFuel49.PICKUP,VehicleFuel49.Mode.FILL,reloadedMeter,
                VehicleFuel49.quote(VehicleFuel49.PICKUP,5000000,VehicleService50.energy(f.vehicle),VehicleFuel49.Mode.FILL,reloadedMeter));
        var second=new VehicleService50.Quote(id,f.player.getUUID(),f.nation.id(),f.vehicle.getUUID(),VehicleService50.Kind.REFUEL,"pickup",first.facility(),f.level.getGameTime(),null,refill);
        check(second.fuelCost()==0&&second.creditAfterNumerator()==4999936,"Prepaid fraction did not cover second fill");var secondEnv=f.prepare(second);
        check(VehicleRefueling49.execute(secondEnv,refill)==VehicleRefueling49.Result.COMPLETE,"Second fractional native fill failed");
        check(f.nation.resource(ResourceType.FUEL)==99&&VehicleService50.energy(f.vehicle)==5000000,"Second fill did not use existing credit exactly once");
        CatanSavedData secondReload=CatanSavedData.load(VehiclePayment47.disk(f.level));var finalMeter=VehicleService50.meter(secondReload,f.nation.id(),f.vehicle.getUUID(),VehicleFuel49.PICKUP,5000000);
        check(finalMeter.creditNumerator()==4999936&&secondReload.nation(f.nation.id()).resource(ResourceType.FUEL)==99,"Second native meter/debit did not survive readback and reload");
        check(secondEnv.debitProof(refill)==VehicleRefueling49.Proof.EXACT&&secondEnv.energyProof(refill)==VehicleRefueling49.Proof.EXACT,"Second operation lacks exact native receipts");
        VehicleService50.unlock(f.level,second);f.vehicle.getClass().getMethod("setEnergy",int.class).invoke(f.vehicle,4999999);VehicleService50.saveVehicle(f.level,f.vehicle);VehicleService50.lock(f.level,second);
        var replay=VehicleService50.fixtureEnvironment(f.player,second,f.dir);check(VehicleRefueling49.execute(replay,refill)==VehicleRefueling49.Result.COMPLETE,"Fractional fill receipt did not survive adapter recreation");
        check(f.nation.resource(ResourceType.FUEL)==99&&VehicleService50.energy(f.vehicle)==4999999,"Fractional replay charged Fuel or restored consumed energy");
        var replayMeter=VehicleService50.meter(CatanSavedData.load(VehiclePayment47.disk(f.level)),f.nation.id(),f.vehicle.getUUID(),VehicleFuel49.PICKUP,5000000);check(replayMeter.equals(finalMeter),"Replay consumed prepaid credit twice");
        CompoundTag proof=new CompoundTag();proof.putString("result","PASS");proof.put("firstQuote",VehicleService50.encode(first));proof.put("firstNativeLedger",firstDisk);proof.put("secondQuote",VehicleService50.encode(second));proof.put("finalNativeLedger",VehiclePayment47.disk(f.level));proof.putLong("firstCreditNumerator",reloadedMeter.creditNumerator());proof.putLong("finalCreditNumerator",finalMeter.creditNumerator());proof.putLong("fuelAfterBothFills",f.nation.resource(ResourceType.FUEL));proof.putLong("energyAfterConsumedReplay",VehicleService50.energy(f.vehicle));ProducerJournal.write(f.dir.resolve("fractional_credit_roundtrip.dat"),proof,false);
        f.proof("native_fractional_credit_roundtrip",second);VehicleService50.unlock(f.level,second);
    });}
    static void run(GameTestHelper h,int index,Work work)throws Exception{Fixture f=new Fixture(h,index);await(f,0,work);}
    static void await(Fixture f,int ticks,Work work){try{f.lease.keepAlive();if(!f.lease.ready()){check(ticks<600,"Fixture not resident");f.h.runAfterDelay(5,()->await(f,ticks+5,work));return;}f.init();work.run(f);f.vehicle.discard();f.lease.close();f.h.succeed();}catch(Exception ex){f.lease.close();throw new RuntimeException(ex);}}
    static void check(boolean yes,String message){if(!yes)throw new IllegalStateException(message);}
    static final class Fixture{
        final GameTestHelper h;final ServerLevel level;final int x,z;final Path dir;final SectorChunkLease lease;FakePlayer player;NationData nation;Entity vehicle;
        Fixture(GameTestHelper helper,int index)throws Exception{h=helper;level=h.getLevel();VehicleService50.fixtureWorld(level);x=7000+index*80;z=7000;dir=level.getServer().getWorldPath(LevelResource.ROOT).toRealPath().resolve("data/service50-test-"+index);if(Files.exists(dir))throw new IllegalStateException("Fixture history already exists");Files.createDirectories(dir);lease=SectorChunkLease.acquire(level,new AABB(x-10,95,z-10,x+10,110,z+10));}
        void init()throws Exception{
            for(BlockPos p:BlockPos.betweenClosed(x-8,99,z-8,x+8,105,z+8))level.setBlock(p,p.getY()==99?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2|16|32);
            player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"fuel50"));player.setPos(x+6,100,z);nation=CatanSavedData.get(level.getServer()).createNation("fuel50-"+x,player.getUUID());nation.addResource(ResourceType.FUEL,100);VehicleService50.ledgerReady(level);
            vehicle=NativeVehicleBay46.create(level,NativeVehicleBay46.PICKUP);NativeVehicleBay46.pose(vehicle,new Vec3(x+.5,100,z+.5),0);vehicle.getPersistentData().putBoolean(NationProcurement.PURCHASED_TAG,true);vehicle.getPersistentData().putString(NationProcurement.NATION_TAG,nation.id().toString());check(level.addFreshEntity(vehicle),"Native add failed");VehicleService50.saveVehicle(level,vehicle);
        }
        VehicleService50.Quote quote(VehicleFuel49.Mode mode)throws Exception{
            UUID id=UUID.randomUUID();var meter=VehicleService50.meter(CatanSavedData.get(level.getServer()),nation.id(),vehicle.getUUID(),VehicleFuel49.PICKUP,VehicleService50.capacity(vehicle));var q=VehicleFuel49.quote(VehicleFuel49.PICKUP,meter.capacity(),VehicleService50.energy(vehicle),mode,meter);CompoundTag facility=new CompoundTag();facility.putString("fixture","service50");
            var intent=new VehicleRefueling49.Intent(id,player.getUUID(),nation.id(),vehicle.getUUID(),VehicleService50.world(level),VehicleService50.digest(facility),VehicleFuel49.PICKUP,mode,meter,q);
            return new VehicleService50.Quote(id,player.getUUID(),nation.id(),vehicle.getUUID(),VehicleService50.Kind.REFUEL,"pickup",facility,level.getGameTime(),null,intent);
        }
        VehicleRefueling49.Environment prepare(VehicleService50.Quote q)throws Exception{VehicleService50.lock(level,q);return VehicleService50.fixtureEnvironment(player,q,dir);}
        void proof(String name,VehicleService50.Quote q)throws Exception{CompoundTag n=new CompoundTag();n.putString("result","PASS");n.putString("scenario",name);n.put("quote",VehicleService50.encode(q));n.put("ledger",VehiclePayment47.disk(level));n.put("entity",VehicleService50.saved(level,vehicle.getUUID(),vehicle.chunkPosition()));ProducerJournal.write(dir.resolve("proof.dat"),n,false);CatanCraft.LOGGER.info("SERVICE50_NATIVE_PASS scenario={} fuel={} energy={} nativeLedgerAndRegion=true",name,nation.resource(ResourceType.FUEL),VehicleService50.energy(vehicle));}
    }
}
