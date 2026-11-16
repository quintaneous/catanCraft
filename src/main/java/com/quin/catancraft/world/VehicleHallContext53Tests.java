package com.quin.catancraft.world;

import com.quin.catancraft.map.MapDefinitionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Funded isolated paid-Hall fixtures, all-site pinned qualification and two
 * explicitly staged factory envelopes. No vehicle driving or owner acceptance. */
@GameTestHolder("catancraft_vehicle_hall_context53") @PrefixGameTestTemplate(false)
public final class VehicleHallContext53Tests {
    private interface Work{void run()throws Exception;}
    private static void refused(GameTestHelper h,Work work)throws Exception{boolean rejected=false;try{work.run();}catch(IOException expected){rejected=true;}h.assertTrue(rejected,"Unproven vehicle Hall context accepted");}
    private static VehicleHallContext53.Context capture(PrestigeNatural53Tests.Fixture f,VehicleSites52.Site site)throws IOException{return VehicleHallContext53.capture(f.level,site,f.owner);}
    @GameTest(template="townhall_state_empty",batch="vehicle_hall53_01",timeoutTicks=20000)
    public static void all_48_sites_qualify_all_hall_tiers_revisions_and_both_public_sources(GameTestHelper h)throws Exception {
        int sites=0,reads=0;
        for(char city='a';city<='p';city++)for(var site:VehicleSites52.sites(MapDefinitionManager.territory(""+city))){
            var mask=VehicleHallContext53.qualifiedPositions(site);int expected=switch(site.plot()){case "plot_1"->120;case "plot_3"->93;case "plot_4"->96;default->throw new IOException("Unknown fixture site");};
            h.assertTrue(mask.size()==expected,"Wrong vehicle Hall mask at "+site.city()+":"+site.plot());
            int sign=site.anchor().rotation()==0?1:-1;
            for(var row:mask.entrySet()){
                BlockPos p=row.getKey();int x=sign*(p.getX()-site.anchor().x()),z=sign*(p.getZ()-site.anchor().z());
                h.assertTrue(p.getY()==79&&row.getValue().equals(Blocks.DIRT.defaultBlockState())&&!(x>=-29&&x<=1&&z>=-15&&z<=15),"Vehicle Hall authority reached support floor or factory cell");
                h.assertTrue(VehicleSites52.source(site,p,false).equals(Blocks.STONE.defaultBlockState())&&VehicleSites52.source(site,p,true).equals(Blocks.STONE.defaultBlockState()),"Original vehicle sources changed");
            }
            boolean immutable=false;try{mask.clear();}catch(UnsupportedOperationException expectedError){immutable=true;}h.assertTrue(immutable,"Mutable vehicle context mask");sites++;reads+=mask.size();
        }
        h.assertTrue(sites==48&&reads==4944,"Missing all-city/factory-slot qualification");h.succeed();
    }
    private static void earned(GameTestHelper h,String city,int from,int revision)throws Exception {
        try(var f=new PrestigeNatural53Tests.Fixture(h,city,from,revision,true,false)){
            var sites=VehicleSites52.sites(f.def);
            if(from==3)for(var site:sites)h.assertTrue(capture(f,site).reads().isEmpty(),"Early Hall earned prestige authority");
            var upgraded=f.upgrade((stage,writes)->{});h.assertTrue(upgraded.success(),upgraded.message());
            byte[] original=Files.readAllBytes(f.journal);CompoundTag ledger=f.data.save(new CompoundTag());
            for(var site:sites){
                var context=capture(f,site);h.assertTrue(context.reads().equals(VehicleHallContext53.qualifiedPositions(site)),"Paid factory context missing");context.verifyUnchanged();
                for(var row:context.reads().entrySet())h.assertTrue(f.level.getBlockState(row.getKey()).equals(row.getValue())&&f.level.getBlockEntity(row.getKey())==null,"Actual paid Hall differs from qualified foundation");
            }
            h.assertTrue(Arrays.equals(original,Files.readAllBytes(f.journal))&&ledger.equals(f.data.save(new CompoundTag())),"Vehicle read authority changed paid history/economy");
        }h.succeed();
    }
    @GameTest(template="townhall_state_empty",batch="vehicle_hall53_02",timeoutTicks=20000)
    public static void north_actual_paid_th4_qualifies_three_factory_slots(GameTestHelper h)throws Exception{earned(h,"a",3,1);}
    @GameTest(template="townhall_state_empty",batch="vehicle_hall53_03",timeoutTicks=20000)
    public static void south_actual_paid_th4_qualifies_three_factory_slots(GameTestHelper h)throws Exception{earned(h,"i",3,1);}
    @GameTest(template="townhall_state_empty",batch="vehicle_hall53_04",timeoutTicks=20000)
    public static void south_paid_th5_from_r1_preserves_original_history(GameTestHelper h)throws Exception{earned(h,"i",4,1);}
    @GameTest(template="townhall_state_empty",batch="vehicle_hall53_05",timeoutTicks=20000)
    public static void north_paid_th5_from_r2_preserves_original_history(GameTestHelper h)throws Exception{earned(h,"a",4,2);}
    @GameTest(template="townhall_state_empty",batch="vehicle_hall53_06",timeoutTicks=20000)
    public static void pending_paid_hall_defers_then_completed_recovery_binds_original_and_companion(GameTestHelper h)throws Exception {
        try(var f=new PrestigeNatural53Tests.Fixture(h,"i",3,1,true,false)){
            var site=VehicleSites52.sites(f.def).get(2);
            h.assertTrue(!f.upgrade((stage,writes)->{if(stage.equals("prepared"))throw new IOException("Pending vehicle-context Hall fixture");}).success(),"Expected pending paid capture");
            byte[] original=Files.readAllBytes(f.journal);refused(h,()->capture(f,site));PaidConstructionRecovery.resume(f.recovery());
            var context=capture(f,site);h.assertTrue(context.reads().size()==96&&Arrays.equals(original,Files.readAllBytes(f.journal)),"Canonical recovered Hall lost original context");
            Path companion=PaidConstructionRecovery.companion(f.journal);byte[] sealed=Files.readAllBytes(companion);
            try{Files.write(companion,new byte[]{1,2,3});refused(h,context::verifyUnchanged);refused(h,()->capture(f,site));}finally{Files.write(companion,sealed);}
            context.verifyUnchanged();
            try{Files.write(f.journal,new byte[]{1,2,3});refused(h,context::verifyUnchanged);refused(h,()->capture(f,site));}finally{Files.write(f.journal,original);}
            context.verifyUnchanged();
        }h.succeed();
    }
    @GameTest(template="townhall_state_empty",batch="vehicle_hall53_07",timeoutTicks=20000)
    public static void tier_marker_missing_history_foreign_owner_and_changed_token_refuse(GameTestHelper h)throws Exception {
        try(var f=new PrestigeNatural53Tests.Fixture(h,"a",3,1,true,false)){
            var site=VehicleSites52.sites(f.def).get(0);String before=f.data.placedMapAsset(f.key);
            f.territory().setCityLevel(4);f.data.setPlacedMapAsset(f.key,TownHallRevisions.token(4,2));refused(h,()->capture(f,site));
            h.assertTrue(!Files.exists(f.journal),"Vehicle helper fabricated Hall history");f.territory().setCityLevel(3);f.data.setPlacedMapAsset(f.key,before);
            h.assertTrue(f.upgrade((stage,writes)->{}).success(),"Paid Hall fixture failed");var context=capture(f,site);byte[] original=Files.readAllBytes(f.journal);
            refused(h,()->VehicleHallContext53.capture(f.level,site,UUID.randomUUID()));
            String token=f.data.placedMapAsset(f.key);f.data.setPlacedMapAsset(f.key,"foreign");refused(h,context::verifyUnchanged);refused(h,()->capture(f,site));f.data.setPlacedMapAsset(f.key,token);
            UUID owner=f.territory().ownerNationId();f.territory().setOwnerNationId(UUID.randomUUID());refused(h,context::verifyUnchanged);refused(h,()->capture(f,site));f.territory().setOwnerNationId(owner);
            context.verifyUnchanged();h.assertTrue(Arrays.equals(original,Files.readAllBytes(f.journal)),"Rejected vehicle authority changed original history");
        }h.succeed();
    }
    @GameTest(template="townhall_state_empty",batch="vehicle_hall53_08",timeoutTicks=24000)
    public static void actual_paid_north_and_south_envelopes_replace_baseline_strictly(GameTestHelper h)throws Exception {
        integration(h,0);
    }
    private static void integration(GameTestHelper h,int index)throws Exception {
        var f=new PrestigeNatural53Tests.Fixture(h,index==0?"a":"i",3,1,true,false);
        try{
            var site=VehicleSites52.sites(f.def).get(index==0?0:2);
            var lease=SectorChunkLease.acquire(f.level,VehicleSites52.envelope(site));
            integrationReady(h,f,site,lease,index,0);
        }catch(Exception failure){try{f.close();}catch(Exception suppressed){failure.addSuppressed(suppressed);}throw failure;}
    }
    private static void integrationReady(GameTestHelper h,PrestigeNatural53Tests.Fixture f,VehicleSites52.Site site,SectorChunkLease lease,int index,int waited){
        h.runAfterDelay(1,()->{
            try{
                lease.keepAlive();
                if(!lease.ready()){
                    if(waited>=8000)throw new IOException("Vehicle Hall integration residency timed out");
                    integrationReady(h,f,site,lease,index,waited+1);return;
                }
                h.assertTrue(!Files.exists(f.root.resolve("data/catancraft_r5_public_"+site.city()+"_v1.dat")),"Raw source integration refuses pre-existing public history");
                var upgraded=f.upgrade((stage,writes)->{});h.assertTrue(upgraded.success(),upgraded.message());
                byte[] original=Files.readAllBytes(f.journal);CompoundTag ledger=f.data.save(new CompoundTag());
                var context=capture(f,site);var mask=context.reads();
                for(int x=NativeVehicleBay46.MIN_X;x<=NativeVehicleBay46.MAX_X;x++)for(int y=NativeVehicleBay46.MIN_Y;y<=NativeVehicleBay46.MAX_Y;y++)for(int z=NativeVehicleBay46.MIN_Z;z<=NativeVehicleBay46.MAX_Z;z++){
                    BlockPos p=NativeVehicleBay46.sourceCell(site.anchor(),x,y,z);
                    var blockEntity=f.level.getBlockEntity(p);if(blockEntity instanceof net.minecraft.world.Container inventory)inventory.clearContent();
                    f.level.setBlock(p,mask.getOrDefault(p,VehicleSites52.source(site,p,false)),PaidConstructionRecovery.FLAGS);
                }
                VehicleFactory50.verifyEnvelope(f.level,site.anchor(),f.owner);
                BlockPos changed=mask.keySet().iterator().next();
                try{f.level.setBlock(changed,Blocks.STONE.defaultBlockState(),PaidConstructionRecovery.FLAGS);refused(h,()->VehicleFactory50.verifyEnvelope(f.level,site.anchor(),f.owner));}
                finally{f.level.setBlock(changed,mask.get(changed),PaidConstructionRecovery.FLAGS);}
                VehicleFactory50.verifyEnvelope(f.level,site.anchor(),f.owner);
                BlockPos ordinary=NativeVehicleBay46.sourceCell(site.anchor(),10,7,23);var expected=f.level.getBlockState(ordinary);
                h.assertTrue(!mask.containsKey(ordinary)&&!expected.equals(Blocks.GOLD_BLOCK.defaultBlockState()),"Ordinary context test overlaps paid foundation");
                try{f.level.setBlock(ordinary,Blocks.GOLD_BLOCK.defaultBlockState(),PaidConstructionRecovery.FLAGS);refused(h,()->VehicleFactory50.verifyEnvelope(f.level,site.anchor(),f.owner));}
                finally{f.level.setBlock(ordinary,expected,PaidConstructionRecovery.FLAGS);}
                VehicleFactory50.verifyEnvelope(f.level,site.anchor(),f.owner);context.verifyUnchanged();
                h.assertTrue(Arrays.equals(original,Files.readAllBytes(f.journal))&&ledger.equals(f.data.save(new CompoundTag())),"Vehicle envelope validation altered paid Hall history/ledger");
            }catch(Exception failure){
                lease.close();try{f.close();}catch(Exception suppressed){failure.addSuppressed(suppressed);}throw new RuntimeException(failure);
            }
            lease.close();try{f.close();if(index==0)integration(h,1);else h.succeed();}catch(Exception failure){throw new RuntimeException(failure);}
        });
    }

}
