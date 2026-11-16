package com.quin.catancraft.world;

import com.quin.catancraft.map.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Isolated paid native Hall histories. These tests grant fixture funds, never
 * constitute no-grant progression or connected-client acceptance. */
@GameTestHolder("catancraft_sector_hall_context53") @PrefixGameTestTemplate(false)
public final class SectorHallContext53Tests {
    interface Work{void run()throws Exception;}
    private static void refused(GameTestHelper h,Work work)throws Exception{boolean refused=false;try{work.run();}catch(IOException|IllegalStateException e){refused=true;}h.assertTrue(refused,"Foreign Hall authority accepted");}
    private static SectorHallContext53.Context capture(PrestigeNatural53Tests.Fixture f)throws IOException{return SectorHallContext53.capture(f.root,f.data,f.def);}
    @GameTest(template="townhall_state_empty",batch="hall_context53_01",timeoutTicks=16000)
    public static void all_city_all_revision_readonly_masks_are_exact(GameTestHelper h)throws Exception{
        Set<BlockPos> all=new HashSet<>();for(char c='a';c<='p';c++){
            var def=MapDefinitionManager.territory(""+c);var rows=SectorHallContext53.qualifiedPositions(def);h.assertTrue(rows.size()==11,"Wrong approach mask");
            var center=def.cityCenter();for(var e:rows.entrySet())h.assertTrue(e.getKey().getY()==80&&Math.abs(e.getKey().getX()-center.x())<=5&&e.getKey().getZ()==center.z()+(center.rotation()==0?-40:40)&&e.getValue().equals(Blocks.POLISHED_ANDESITE.defaultBlockState())&&all.add(e.getKey()),"Wrong/cross-city approach authority");
        }h.assertTrue(all.size()==176,"Incomplete all-city approach qualification");h.succeed();
    }
    private static void earned(GameTestHelper h,String city,int from,int revision)throws Exception{
        try(var f=new PrestigeNatural53Tests.Fixture(h,city,from,revision)){
            if(from==3)h.assertTrue(capture(f).reads().isEmpty(),"Unearned prestige context");
            h.assertTrue(f.upgrade((s,n)->{}).success(),"Native paid prestige failed");byte[] original=Files.readAllBytes(f.journal);CompoundTag ledger=f.data.save(new CompoundTag());
            var context=capture(f);h.assertTrue(context.reads().size()==11,"Paid target context missing");context.verifyUnchanged();
            h.assertTrue(Arrays.equals(original,Files.readAllBytes(f.journal))&&ledger.equals(f.data.save(new CompoundTag())),"Readonly context rewrote history/economy");
            String token=f.data.placedMapAsset(f.key);f.data.setPlacedMapAsset(f.key,"foreign");refused(h,context::verifyUnchanged);f.data.setPlacedMapAsset(f.key,token);context.verifyUnchanged();h.succeed();
        }
    }
    @GameTest(template="townhall_state_empty",batch="hall_context53_02",timeoutTicks=16000)
    public static void north_paid_th4_context_and_frozen_token(GameTestHelper h)throws Exception{earned(h,"a",3,1);}
    @GameTest(template="townhall_state_empty",batch="hall_context53_03",timeoutTicks=16000)
    public static void south_paid_th5_context_from_r1(GameTestHelper h)throws Exception{earned(h,"i",4,1);}
    @GameTest(template="townhall_state_empty",batch="hall_context53_04",timeoutTicks=16000)
    public static void south_paid_th5_context_from_r2(GameTestHelper h)throws Exception{earned(h,"i",4,2);}
    @GameTest(template="townhall_state_empty",batch="hall_context53_05",timeoutTicks=16000)
    public static void pending_then_completed_recovery_context_keeps_original(GameTestHelper h)throws Exception{
        try(var f=new PrestigeNatural53Tests.Fixture(h,"i",3,1)){
            h.assertTrue(!f.upgrade((s,n)->{if(s.equals("prepared"))throw new IOException("Deliberate pending Hall");}).success(),"Missing interruption");
            byte[] original=Files.readAllBytes(f.journal);h.assertTrue(SectorHallContext53.pending(f.root,"i"),"Pending history hidden");refused(h,()->capture(f));
            PaidConstructionRecovery.resume(f.recovery());h.assertTrue(!SectorHallContext53.pending(f.root,"i")&&capture(f).reads().size()==11,"Completed recovery not recognized");
            h.assertTrue(Arrays.equals(original,Files.readAllBytes(f.journal)),"Recovery rewrote original Hall");
            var ctx=capture(f);Path companion=PaidConstructionRecovery.companion(f.journal);byte[] sealed=Files.readAllBytes(companion);Files.write(companion,new byte[]{1,2,3});refused(h,ctx::verifyUnchanged);refused(h,()->capture(f));Files.write(companion,sealed);ctx.verifyUnchanged();h.succeed();
        }
    }
    @GameTest(template="townhall_state_empty",batch="hall_context53_06",timeoutTicks=16000)
    public static void original_identity_delta_receipt_and_owner_tampering_refused(GameTestHelper h)throws Exception{
        try(var f=new PrestigeNatural53Tests.Fixture(h,"a",3,1)){
            h.assertTrue(f.upgrade((s,n)->{}).success(),"Native paid prestige failed");byte[] original=Files.readAllBytes(f.journal);CompoundTag saved=PrestigePurchaseRecovery.read(original);var ctx=capture(f);
            for(String field:List.of("city","worldRoot","ownerNation","targetToken","sourceToken","targetSHA256")){
                CompoundTag changed=saved.copy();changed.putString(field,"foreign");NbtIo.writeCompressed(changed,f.journal.toFile());refused(h,()->capture(f));refused(h,ctx::verifyUnchanged);Files.write(f.journal,original);
            }
            CompoundTag changed=saved.copy();ListTag rows=changed.getList("blocks",Tag.TAG_COMPOUND);rows.remove(0);NbtIo.writeCompressed(changed,f.journal.toFile());refused(h,()->capture(f));Files.write(f.journal,original);
            String receiptKey=PaidConstructionRecovery.receiptKey(saved.getUUID("transaction")),receipt=f.data.placedMapAsset(receiptKey);f.data.setPlacedMapAsset(receiptKey,"foreign");refused(h,()->capture(f));refused(h,ctx::verifyUnchanged);f.data.setPlacedMapAsset(receiptKey,receipt);
            UUID owner=f.territory().ownerNationId();f.territory().setOwnerNationId(UUID.randomUUID());refused(h,()->capture(f));refused(h,ctx::verifyUnchanged);f.territory().setOwnerNationId(owner);ctx.verifyUnchanged();h.succeed();
        }
    }
    @GameTest(template="townhall_state_empty",batch="hall_context53_07",timeoutTicks=16000)
    public static void tier_marker_without_original_never_earns_context(GameTestHelper h)throws Exception{
        try(var f=new PrestigeNatural53Tests.Fixture(h,"a",3,1)){
            f.territory().setCityLevel(4);f.data.setPlacedMapAsset(f.key,TownHallRevisions.token(4,2));refused(h,()->capture(f));h.assertTrue(!Files.exists(f.journal),"Read helper invented history");h.succeed();
        }
    }
    @GameTest(template="townhall_state_empty",batch="hall_context53_08",timeoutTicks=20000)
    public static void retained_r1_purchase_accepts_only_canonical_completed_repair(GameTestHelper h)throws Exception {
        for(String city:List.of("a","i"))try(var f=new PrestigeNatural53Tests.Fixture(h,city,3,1)){
            h.assertTrue(f.upgrade((stage,writes)->{}).success(),"Native paid fixture purchase failed");
            // Reconstruct the historical R1 target contract from the paid fixture.
            // This is explicitly a funded legacy fixture, not a production purchase path.
            var original=PrestigePurchaseRecovery.read(Files.readAllBytes(f.journal));
            var anchor=SchematicAssetRegistry.territoryAnchor("th4",f.def);var r1=TownHallRevisions.plan(4,1,anchor);
            Map<BlockPos,CompoundTag> recorded=new HashMap<>();
            for(Tag tag:original.getList("blocks",Tag.TAG_COMPOUND)){var row=(CompoundTag)tag;recorded.put(BlockPos.of(row.getLong("pos")),row);}
            ListTag rows=new ListTag();int flags=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS;
            for(int i=0;i<r1.size();i++){
                var old=f.before.get(i);BlockPos p=old.position();if(p.equals(f.manager()))continue;
                var captured=recorded.get(p);var a=old.state();
                var b=TownHallInteractionPolicy.targetFor(a,r1.get(i).state());
                if(!a.equals(b)){var row=new CompoundTag();row.putLong("pos",p.asLong());row.putString("before",a.toString());row.putString("after",b.toString());row.put("beforeState",NbtUtils.writeBlockState(a));row.put("afterState",NbtUtils.writeBlockState(b));if(captured!=null&&captured.contains("beforeBlockEntity"))row.put("beforeBlockEntity",captured.getCompound("beforeBlockEntity").copy());rows.add(row);}
                f.level.setBlock(p,b,flags);
            }
            original.put("blocks",rows);original.putString("targetToken",TownHallRevisions.token(4,1));original.remove("targetSHA256");original.remove("receiptRequired");original.remove(PrestigeNaturalSupport.FIELD);
            NbtIo.writeCompressed(original,f.journal.toFile());f.data.setPlacedMapAsset(f.key,TownHallRevisions.token(4,1));
            String receiptKey=PaidConstructionRecovery.receiptKey(original.getUUID("transaction"));var legacyLedger=f.data.save(new CompoundTag());legacyLedger.getCompound("placedMapAssets").remove(receiptKey);f.data=com.quin.catancraft.data.CatanSavedData.load(legacyLedger);f.save();
            byte[] paid=Files.readAllBytes(f.journal);var initial=capture(f);h.assertTrue(initial.reads().size()==11,"Original paid R1 context missing");
            h.assertTrue(f.data.placedMapAsset(receiptKey)==null&&!original.contains("receiptRequired")&&!original.contains("targetSHA256"),"Fixture did not retain true legacy optional-field absence");
            var invalid=original.copy();invalid.putBoolean("receiptRequired",true);NbtIo.writeCompressed(invalid,f.journal.toFile());refused(h,()->capture(f));Files.write(f.journal,paid);
            invalid=original.copy();invalid.putString("targetSHA256","foreign");NbtIo.writeCompressed(invalid,f.journal.toFile());refused(h,()->capture(f));Files.write(f.journal,paid);
            f.data.setPlacedMapAsset(receiptKey,"foreign");refused(h,()->capture(f));refused(h,initial::verifyUnchanged);
            var restored=f.data.save(new CompoundTag());restored.getCompound("placedMapAssets").remove(receiptKey);f.data=com.quin.catancraft.data.CatanSavedData.load(restored);
            var legacyContext=capture(f);legacyContext.verifyUnchanged();
            Path repair=f.root.resolve("data/catancraft_th_repair_"+city+"_4_r1_r2.dat");h.assertTrue(!Files.exists(repair),"Legacy fixture refuses existing repair");
            var e=new TownHallRepairTransaction.Environment(){
                public net.minecraft.server.level.ServerLevel level(){return f.level;}public MapAnchor anchor(){return anchor;}public int tier(){return 4;}public Path file(){return repair;}
                public CompoundTag identity(){var id=new CompoundTag();id.putString("world",f.root.toString());id.putString("dimension",f.def.dimension());id.putString("city",city);id.putString("owner",f.owner.toString());id.putInt("tier",4);id.putIntArray("anchor",new int[]{anchor.x(),anchor.y(),anchor.z(),anchor.rotation()});return id;}
                public CompoundTag history()throws IOException{var n=new CompoundTag();n.putString("tier4",TownHallRevisions.sha(Files.readAllBytes(f.journal)));n.putString("tier5","ABSENT");return n;}
                public CompoundTag ledger(){var n=f.data.save(new CompoundTag());n.getCompound("placedMapAssets").putString(f.key,"NORMALIZED_CURRENT_HALL_ONLY");return n;}
                public String token(){return f.data.placedMapAsset(f.key);}public void token(String value){f.data.setPlacedMapAsset(f.key,value);}public void save()throws IOException{f.save();}
            };
            try{
                refused(h,()->TownHallRepairTransaction.run(e,TownHallRepairTransaction.Action.APPLY,(stage,writes)->{if(stage.equals("prepared"))throw new IOException("Deliberate pending visual repair");}));
                h.assertTrue(SectorHallContext53.pending(f.root,city),"Pending repair hidden");refused(h,()->capture(f));refused(h,legacyContext::verifyUnchanged);
                h.assertTrue(TownHallRepairTransaction.run(e,TownHallRepairTransaction.Action.RESUME).writes()==584,"Historical repair mask changed");
                var context=capture(f);context.verifyUnchanged();byte[] sealed=Files.readAllBytes(repair);
                f.data.setPlacedMapAsset(f.key,TownHallRevisions.token(4,1));refused(h,()->capture(f));f.data.setPlacedMapAsset(f.key,TownHallRevisions.token(4,2));
                Files.write(repair,new byte[]{1,2,3});refused(h,context::verifyUnchanged);refused(h,()->capture(f));Files.write(repair,sealed);context.verifyUnchanged();
                h.assertTrue(TownHallRepairTransaction.run(e,TownHallRepairTransaction.Action.ROLLBACK).writes()==584,"Historical rollback mask changed");
                h.assertTrue(!SectorHallContext53.pending(f.root,city)&&capture(f).reads().size()==11,"Rolled-back earned R1 context missing");
                h.assertTrue(Arrays.equals(paid,Files.readAllBytes(f.journal)),"Read/repair changed original paid history");
            }finally{if(Files.exists(repair))Files.move(repair,f.archive.resolve(repair.getFileName()));}
        }h.succeed();
    }

}
