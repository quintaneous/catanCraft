package com.quin.catancraft.world;

import com.quin.catancraft.data.*;
import com.quin.catancraft.economy.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

@GameTestHolder("catancraft_paid_recovery") @PrefixGameTestTemplate(false)
public final class PaidRecoveryTests {
    @FunctionalInterface interface Action {void run()throws Exception;}
    static void ready(GameTestHelper h,AABB box,Action action)throws Exception{waitReady(h,SectorChunkLease.acquire(h.getLevel(),box),0,action);}
    private static void waitReady(GameTestHelper h,SectorChunkLease lease,int ticks,Action action){h.runAfterDelay(1,()->{
        try{h.assertTrue(ticks<4000,"Recovery fixture residency timeout");lease.keepAlive();if(!lease.ready()){waitReady(h,lease,ticks+1,action);return;}try{action.run();}finally{lease.close();}}
        catch(Exception e){lease.close();throw new RuntimeException(e);}
    });}
    static void refused(GameTestHelper h,Action action,String contains)throws Exception{
        boolean refused=false;try{action.run();}catch(IOException expected){refused=expected.getMessage().contains(contains);if(!refused)throw expected;}
        h.assertTrue(refused,"Expected refusal containing "+contains);
    }
    static void interrupt(GameTestHelper h,PaidConstructionRecovery.Environment e,String phase,int at)throws Exception{
        refused(h,()->PaidConstructionRecovery.resume(e,(s,n)->{if(s.equals(phase)&&(at<0||n==at))throw new IOException("injected-boundary");}),"injected-boundary");
    }
    static class F implements PaidConstructionRecovery.Environment {
        final GameTestHelper h;final Path dir,original;final byte[] bytes;final UUID id=UUID.randomUUID(),owner;
        final BlockPos p,q;CatanSavedData data=new CatanSavedData();boolean failSave;int saveCalls;boolean allowedOwner=true;
        final EconomyCost cost=new EconomyCost(250,Map.of(ResourceType.WOOD,5L));
        F(GameTestHelper h)throws Exception{
            this.h=h;if(!(h.getLevel().getServer() instanceof GameTestServer))throw new IOException("Fixture only");
            dir=Files.createTempDirectory(h.getLevel().getServer().getWorldPath(LevelResource.ROOT),"paid-fixture-");original=dir.resolve("original.journal");bytes=("immutable-original:"+id).getBytes(java.nio.charset.StandardCharsets.UTF_8);Files.write(original,bytes);
            var nation=data.createNation("fixture",UUID.randomUUID());owner=nation.id();nation.setTreasury(10000);nation.addResource(ResourceType.WOOD,100);data.createTerritory("a","A",ResourceType.WOOD).setOwnerNationId(owner);
            p=h.absolutePos(new BlockPos(1,2,1));q=p.east();save();
        }
        void stage(){for(BlockPos b:BlockPos.betweenClosed(p.offset(-2,-1,-2),q.offset(2,2,2)))level().setBlock(b,b.getY()<p.getY()?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),PaidConstructionRecovery.FLAGS);}
        void reload()throws Exception{data=CatanSavedData.load(NbtIo.readCompressed(dir.resolve("ledger.nbt").toFile()));}
        NationData nation(){return data.nation(owner);}
        public ServerLevel level(){return h.getLevel();}public Path original(){return original;}public byte[] originalBytes(){return bytes.clone();}
        public CompoundTag identity(){var n=new CompoundTag();n.putUUID("id",id);n.putUUID("owner",owner);return n;}
        public List<PaidConstructionRecovery.Cell> cells(){return List.of(new PaidConstructionRecovery.Cell(p,List.of(Blocks.AIR.defaultBlockState(),Blocks.STONE_BRICKS.defaultBlockState()),Blocks.STONE_BRICKS.defaultBlockState()),new PaidConstructionRecovery.Cell(q,List.of(Blocks.AIR.defaultBlockState(),Blocks.BARREL.defaultBlockState()),Blocks.BARREL.defaultBlockState()));}
        public void authorize()throws IOException{if(!allowedOwner||!owner.equals(data.territory("a").ownerNationId()))throw new IOException("owner changed");}
        public boolean paid()throws IOException{boolean target="done".equals(data.placedMapAsset("fixture:level"));return PaidConstructionRecovery.paid(data,id,target,!target,true);}
        public void checkPayment()throws IOException{authorize();if(!paid()&&!cost.canAfford(nation()))throw new IOException("insufficient resources");}
        public void settle()throws IOException{checkPayment();if(!paid()){cost.charge(nation());data.setPlacedMapAsset("fixture:level","done");}PaidConstructionRecovery.receipt(data,id);}
        public CompoundTag ledger(){return data.save(new CompoundTag());}
        public void save()throws IOException{saveCalls++;if(failSave)throw new IOException("injected-save-failure");NbtIo.writeCompressed(ledger(),dir.resolve("ledger.nbt").toFile());}
        void assertPaid()throws Exception{h.assertTrue(paid()&&nation().treasury()==9750&&nation().resource(ResourceType.WOOD)==95,"Wrong charge or receipt");h.assertTrue(Arrays.equals(bytes,Files.readAllBytes(original)),"Original journal rewritten");h.assertTrue(PaidConstructionRecovery.complete(original,bytes),"Companion not committed");}
    }
    private static void with(GameTestHelper h,java.util.function.Consumer<F> action)throws Exception{
        F f=new F(h);ready(h,new AABB(f.p).inflate(6),()->{f.stage();action.accept(f);});
    }
    private static void run(GameTestHelper h,F f,Action action){try{action.run();h.succeed();}catch(Exception e){throw new RuntimeException(e);}}
    @GameTest(template="townhall_state_empty",batch="paid_core01",timeoutTicks=5000)
    public static void unpaid_resume_and_repeated_completion_are_idempotent(GameTestHelper h)throws Exception{with(h,f->run(h,f,()->{PaidConstructionRecovery.resume(f);f.assertPaid();byte[] side=Files.readAllBytes(PaidConstructionRecovery.companion(f.original));f.reload();h.assertTrue(PaidConstructionRecovery.resume(f).alreadyComplete(),"Repeat not no-op");f.assertPaid();h.assertTrue(Arrays.equals(side,Files.readAllBytes(PaidConstructionRecovery.companion(f.original))),"Completed companion rewritten");}));}
    @GameTest(template="townhall_state_empty",batch="paid_core02",timeoutTicks=5000)
    public static void partial_physical_recovery_preserves_original_and_debits_once(GameTestHelper h)throws Exception{with(h,f->run(h,f,()->{interrupt(h,f,"write",1);f.reload();PaidConstructionRecovery.resume(f);f.assertPaid();}));}
    @GameTest(template="townhall_state_empty",batch="paid_core03",timeoutTicks=5000)
    public static void unsaved_debit_reloads_source_ledger_then_pays_once(GameTestHelper h)throws Exception{with(h,f->run(h,f,()->{interrupt(h,f,"settled",-1);f.reload();h.assertTrue(f.nation().treasury()==10000,"Fixture did not simulate unsaved ledger");PaidConstructionRecovery.resume(f);f.assertPaid();}));}
    @GameTest(template="townhall_state_empty",batch="paid_core04",timeoutTicks=5000)
    public static void saved_debit_before_final_journal_is_not_repeated(GameTestHelper h)throws Exception{with(h,f->run(h,f,()->{interrupt(h,f,"ledger_saved",-1);f.reload();f.nation().addTreasury(125);PaidConstructionRecovery.resume(f);h.assertTrue(f.nation().treasury()==9875&&f.nation().resource(ResourceType.WOOD)==95,"Recovery repeated charge or erased later production");}));}
    @GameTest(template="townhall_state_empty",batch="paid_core05",timeoutTicks=5000)
    public static void failed_save_cannot_mark_committed(GameTestHelper h)throws Exception{with(h,f->run(h,f,()->{f.failSave=true;refused(h,()->PaidConstructionRecovery.resume(f),"injected-save-failure");h.assertTrue(!PaidConstructionRecovery.complete(f.original,f.bytes)&&!f.paid(),"Failed physical save committed or charged");f.failSave=false;PaidConstructionRecovery.resume(f);f.assertPaid();}));}
    @GameTest(template="townhall_state_empty",batch="paid_core06",timeoutTicks=5000)
    public static void unexpected_block_refuses_all_writes_and_charge(GameTestHelper h)throws Exception{with(h,f->run(h,f,()->{f.level().setBlock(f.q,Blocks.DIAMOND_BLOCK.defaultBlockState(),PaidConstructionRecovery.FLAGS);var ledger=f.ledger();refused(h,()->PaidConstructionRecovery.resume(f),"conflict");h.assertTrue(f.level().getBlockState(f.p).isAir()&&ledger.equals(f.ledger())&&!Files.exists(PaidConstructionRecovery.companion(f.original)),"Conflict mutated purchase");}));}
    @GameTest(template="townhall_state_empty",batch="paid_core07",timeoutTicks=5000)
    public static void already_installed_barrel_inventory_survives_completion(GameTestHelper h)throws Exception{with(h,f->run(h,f,()->{f.level().setBlock(f.q,Blocks.BARREL.defaultBlockState(),PaidConstructionRecovery.FLAGS);var barrel=(BarrelBlockEntity)f.level().getBlockEntity(f.q);barrel.setItem(0,new ItemStack(Items.DIAMOND,17));var n=barrel.saveWithFullMetadata().copy();PaidConstructionRecovery.resume(f);h.assertTrue(n.equals(barrel.saveWithFullMetadata()),"Recovery changed existing inventory");f.assertPaid();}));}
    @GameTest(template="townhall_state_empty",batch="paid_core08",timeoutTicks=5000)
    public static void original_journal_change_refuses_recovery(GameTestHelper h)throws Exception{with(h,f->run(h,f,()->{Files.writeString(f.original,"foreign history");refused(h,()->PaidConstructionRecovery.resume(f),"Original construction journal changed");h.assertTrue(f.level().getBlockState(f.p).isAir()&&!f.paid(),"Changed original was acted on");}));}
    @GameTest(template="townhall_state_empty",batch="paid_core09",timeoutTicks=5000)
    public static void corrupted_companion_is_not_overwritten(GameTestHelper h)throws Exception{with(h,f->run(h,f,()->{Files.writeString(PaidConstructionRecovery.companion(f.original),"corrupt");refused(h,()->PaidConstructionRecovery.resume(f),"journal");h.assertTrue(Files.readString(PaidConstructionRecovery.companion(f.original)).equals("corrupt")&&!f.paid(),"Corrupt history replaced");}));}
    @GameTest(template="townhall_state_empty",batch="paid_core10",timeoutTicks=5000)
    public static void missing_required_receipt_is_not_inferred_from_balance(GameTestHelper h)throws Exception{with(h,f->run(h,f,()->{f.data.setPlacedMapAsset("fixture:level","done");f.nation().addTreasury(-250);refused(h,()->PaidConstructionRecovery.resume(f),"lacks required");h.assertTrue(f.level().getBlockState(f.p).isAir(),"Ambiguous payment wrote blocks");}));}
    @GameTest(template="townhall_state_empty",batch="paid_core11",timeoutTicks=5000)
    public static void changed_owner_refuses_without_mutation(GameTestHelper h)throws Exception{with(h,f->run(h,f,()->{f.allowedOwner=false;refused(h,()->PaidConstructionRecovery.resume(f),"owner changed");h.assertTrue(!Files.exists(PaidConstructionRecovery.companion(f.original))&&!f.paid(),"Changed ownership acted on");}));}
    @GameTest(template="townhall_state_empty",batch="paid_core12",timeoutTicks=5000)
    public static void occupied_cell_waits_without_removing_entity(GameTestHelper h)throws Exception{
        F f=new F(h);ready(h,new AABB(f.p).inflate(6),()->{f.stage();var item=new ItemEntity(f.level(),f.p.getX()+.5,f.p.getY()+.2,f.p.getZ()+.5,new ItemStack(Items.STICK));item.setNoGravity(true);item.setDeltaMovement(0,0,0);f.level().addFreshEntity(item);
            h.runAfterDelay(5,()->run(h,f,()->{refused(h,()->PaidConstructionRecovery.resume(f),"Construction hazard:");h.assertTrue(!item.isRemoved()&&!Files.exists(PaidConstructionRecovery.companion(f.original)),"Occupancy changed entity or journal");item.discard();PaidConstructionRecovery.resume(f);f.assertPaid();}));});
    }
}
