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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.*;
import net.minecraftforge.gametest.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;

/** Seed and verify run in distinct JVMs with the same native ledger and region files. */
@GameTestHolder("catancraft_service50_restart") @PrefixGameTestTemplate(false)
public final class VehicleService50RestartTests {
    static final int X=9000,Z=7000;
    @GameTest(template="townhall_state_empty",timeoutTicks=1200)
    public static void actual_native_locked_energy_and_paid_receipt_survive_restart(GameTestHelper h)throws Exception{
        String phase=System.getProperty("catancraft.service50Phase","");String source=System.getProperty("catancraft.service50Source","");check(Set.of("seed","verify").contains(phase)&&source.matches("[0-9a-f]{40}"),"Missing exact restart source/phase");
        ServerLevel level=h.getLevel();VehicleService50.fixtureWorld(level);Path root=level.getServer().getWorldPath(LevelResource.ROOT).toRealPath();check(root.getParent().getFileName().toString().equals("controlled-catancraft_service50_restart-run"),"Wrong restart fixture");
        Path dir=root.resolve("data/service50-restart");Files.createDirectories(dir);SectorChunkLease lease=SectorChunkLease.acquire(level,new AABB(X-10,95,Z-10,X+10,110,Z+10));
        await(h,level,dir,lease,phase,source,0);
    }
    static void await(GameTestHelper h,ServerLevel level,Path dir,SectorChunkLease lease,String phase,String source,int ticks){try{
        lease.keepAlive();boolean ready=lease.ready();
        if(ready&&phase.equals("verify")){CompoundTag seed=ProducerJournal.read(dir.resolve("seed.dat"));ready=level.getEntity(seed.getUUID("entity"))!=null;}
        if(!ready){check(ticks<800,"Native restart chunks/entities did not become ready");h.runAfterDelay(5,()->await(h,level,dir,lease,phase,source,ticks+5));return;}
        if(phase.equals("seed"))seed(level,dir,source);else verify(level,dir,source);
        lease.close();h.succeed();
    }catch(Exception ex){lease.close();throw new RuntimeException(ex);}}
    static void seed(ServerLevel level,Path dir,String source)throws Exception{
        check(!Files.exists(dir.resolve("seed.dat"))&&!Files.exists(dir.resolve("quote.dat")),"Existing seed history refused");
        for(BlockPos p:BlockPos.betweenClosed(X-8,99,Z-8,X+8,105,Z+8))level.setBlock(p,p.getY()==99?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2|16|32);
        FakePlayer player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"fuel50restart"));player.setPos(X+6,100,Z);
        NationData nation=CatanSavedData.get(level.getServer()).createNation("fuel50restart",player.getUUID());nation.addResource(ResourceType.FUEL,100);VehicleService50.ledgerReady(level);
        Entity vehicle=NativeVehicleBay46.create(level,NativeVehicleBay46.PICKUP);NativeVehicleBay46.pose(vehicle,new Vec3(X+.5,100,Z+.5),0);vehicle.getPersistentData().putBoolean(NationProcurement.PURCHASED_TAG,true);vehicle.getPersistentData().putString(NationProcurement.NATION_TAG,nation.id().toString());check(level.addFreshEntity(vehicle),"Native entity add failed");VehicleService50.saveVehicle(level,vehicle);
        UUID id=UUID.randomUUID();var meter=VehicleFuel49.emptyMeter(VehicleFuel49.PICKUP,VehicleService50.capacity(vehicle));CompoundTag facility=new CompoundTag();facility.putString("fixture","service50restart");
        var refill=new VehicleRefueling49.Intent(id,player.getUUID(),nation.id(),vehicle.getUUID(),VehicleService50.world(level),VehicleService50.digest(facility),VehicleFuel49.PICKUP,VehicleFuel49.Mode.ADD_QUARTER,meter,VehicleFuel49.quote(VehicleFuel49.PICKUP,meter.capacity(),0,VehicleFuel49.Mode.ADD_QUARTER,meter));
        var quote=new VehicleService50.Quote(id,player.getUUID(),nation.id(),vehicle.getUUID(),VehicleService50.Kind.REFUEL,"pickup",facility,level.getGameTime(),null,refill);ProducerJournal.write(dir.resolve("quote.dat"),VehicleService50.encode(quote),false);
        VehicleService50.lock(level,quote);var env=VehicleService50.fixtureEnvironment(player,quote,dir);var prepared=new VehicleRefueling49.Record(refill,VehicleRefueling49.Phase.PREPARED);env.save(null,prepared);var debit=new VehicleRefueling49.Record(refill,VehicleRefueling49.Phase.DEBIT_ATTEMPTED);env.save(prepared,debit);env.debitAndSave(refill);var paid=new VehicleRefueling49.Record(refill,VehicleRefueling49.Phase.PAID);env.save(debit,paid);var add=new VehicleRefueling49.Record(refill,VehicleRefueling49.Phase.ENERGY_ATTEMPTED);env.save(paid,add);env.addEnergyAndSave(refill);
        check(VehicleService50.locked(vehicle)&&VehicleService50.energy(vehicle)==1250000&&nation.resource(ResourceType.FUEL)==92,"Wrong seeded state");CompoundTag proof=new CompoundTag();proof.putString("source",source);proof.putString("process",process());proof.putUUID("entity",vehicle.getUUID());proof.putUUID("actor",player.getUUID());proof.putUUID("nation",nation.id());proof.put("ledger",VehiclePayment47.disk(level));proof.put("entitySnapshot",VehicleService50.saved(level,vehicle.getUUID(),vehicle.chunkPosition()));ProducerJournal.write(dir.resolve("seed.dat"),proof,false);
        CatanCraft.LOGGER.info("SERVICE50_RESTART_SEED_PASS nativeEnergy=1250000 fuel=92 locked=true phase=ENERGY_ATTEMPTED source={}",source);
    }
    static void verify(ServerLevel level,Path dir,String source)throws Exception{
        byte[] original=Files.readAllBytes(dir.resolve("seed.dat"));CompoundTag seed=ProducerJournal.read(original);check(seed.getString("source").equals(source)&&!seed.getString("process").equals(process()),"Restart source/JVM differs incorrectly");check(!Files.exists(dir.resolve("verified.dat")),"Already verified history refused");
        FakePlayer player=FakePlayerFactory.get(level,new GameProfile(seed.getUUID("actor"),"fuel50restart"));player.setPos(X+6,100,Z);NationData nation=CatanSavedData.get(level.getServer()).nation(seed.getUUID("nation"));Entity vehicle=level.getEntity(seed.getUUID("entity"));check(nation!=null&&vehicle!=null,"Original native nation/entity absent");
        check(nation.resource(ResourceType.FUEL)==92&&VehicleService50.energy(vehicle)==1250000&&VehicleService50.locked(vehicle),"Native locked data did not restore");
        var quote=VehicleService50.readQuote(dir.resolve("quote.dat"));var env=VehicleService50.fixtureEnvironment(player,quote,dir);check(env.debitProof(quote.refill())==VehicleRefueling49.Proof.EXACT&&env.energyProof(quote.refill())==VehicleRefueling49.Proof.EXACT,"Original saved receipts missing after restart");
        check(VehicleRefueling49.execute(env,quote.refill())==VehicleRefueling49.Result.COMPLETE,"Native original refill did not reconcile");VehicleService50.unlock(level,quote);check(nation.resource(ResourceType.FUEL)==92&&VehicleService50.energy(vehicle)==1250000&&!VehicleService50.locked(vehicle),"Restart recovery duplicated or retained lock");
        check(Arrays.equals(original,Files.readAllBytes(dir.resolve("seed.dat"))),"Seed proof was rewritten");CompoundTag verified=new CompoundTag();verified.putString("result","PASS");verified.putString("source",source);verified.putString("seedProcess",seed.getString("process"));verified.putString("verifyProcess",process());verified.putString("seedSHA256",TownHallRevisions.sha(original));verified.put("ledger",VehiclePayment47.disk(level));verified.put("entitySnapshot",VehicleService50.saved(level,vehicle.getUUID(),vehicle.chunkPosition()));ProducerJournal.write(dir.resolve("verified.dat"),verified,false);
        CatanCraft.LOGGER.info("SERVICE50_RESTART_VERIFY_PASS nativeEnergy=1250000 fuel=92 unlocked=true duplicateCharge=0 duplicateEnergy=0 distinctJVM=true source={}",source);
    }
    static String process(){return ProcessHandle.current().pid()+":"+ManagementFactory.getRuntimeMXBean().getStartTime();}
    static void check(boolean yes,String message){if(!yes)throw new IllegalStateException(message);}
}
