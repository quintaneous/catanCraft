package com.quin.catancraft.world;

import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Isolated durable reservation and startup dependency tests. No claim of a
 * full wall or Hall transaction: those are exercised by the composition suite. */
@GameTestHolder("catancraft_sector_hall_concurrency53") @PrefixGameTestTemplate(false)
public final class SectorHallConcurrency53Tests {
    private SectorHallConcurrency53Tests() {}
    private static Path root(GameTestHelper h)throws IOException {
        h.assertTrue(h.getLevel().getServer() instanceof GameTestServer,"Native fixture only");
        Path root=Files.createTempDirectory(h.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("data"),"hall-wall53-");
        Files.createDirectory(root.resolve("data"));return root;
    }
    private static Path prestige(Path root,String city,String phase)throws IOException {
        CompoundTag j=new CompoundTag();j.putInt("schema",1);j.putUUID("transaction",UUID.randomUUID());
        j.putString("worldRoot",root.toString());j.putString("ownerNation",UUID.randomUUID().toString());j.putString("city",city);j.putInt("fromLevel",3);j.putInt("toLevel",4);j.putString("phase",phase);
        Path path=root.resolve("data/catancraft_th_prestige_"+city+"_4_v1.dat");NbtIo.writeCompressed(j,path.toFile());return path;
    }
    private static SectorService.RecoveryJob job(String city){return new SectorService.RecoveryJob(city,"catancraft_sector_"+city+"_north_"+UUID.randomUUID()+"_v1.dat",UUID.randomUUID());}
    @GameTest(template="townhall_state_empty",batch="hall_concurrency53_01",timeoutTicks=100)
    public static void pending_hall_rotates_exact_wall_job_without_starving_other_city(GameTestHelper h)throws Exception {
        Path root=root(h);Path original=prestige(root,"a","PENDING");byte[] bytes=Files.readAllBytes(original);
        var a=job("a");var b=job("b");Queue<SectorService.RecoveryJob> jobs=new ArrayDeque<>(List.of(a,b));
        var next=SectorRecovery.nextReady(jobs,c->SectorHallContext53.pending(root,c),(j,m)->{});
        h.assertTrue(next==b&&jobs.size()==1&&jobs.peek()==a,"Pending Hall consumed/replaced captured wall or blocked unrelated city");
        h.assertTrue(SectorRecovery.nextReady(jobs,c->SectorHallContext53.pending(root,c),(j,m)->{})==null&&jobs.peek()==a,"Repeated waiting consumed identity");
        h.assertTrue(Arrays.equals(bytes,Files.readAllBytes(original)),"Dependency check changed original Hall history");
        // Simulate the independently completed original record, without creating a
        // new wall job or another payment. Full physical recovery is another suite.
        CompoundTag done=PrestigePurchaseRecovery.read(bytes);done.putString("phase","COMMITTED");NbtIo.writeCompressed(done,original.toFile());
        h.assertTrue(SectorRecovery.nextReady(jobs,c->SectorHallContext53.pending(root,c),(j,m)->{})==a&&jobs.isEmpty(),"Completed dependency did not release the original captured wall");h.succeed();
    }
    @GameTest(template="townhall_state_empty",batch="hall_concurrency53_02",timeoutTicks=100)
    public static void corrupt_foreign_hall_is_quarantined_without_starving_other_city(GameTestHelper h)throws Exception {
        Path root=root(h);Path original=prestige(root,"a","PENDING");CompoundTag foreign=PrestigePurchaseRecovery.read(Files.readAllBytes(original));foreign.putString("city","p");NbtIo.writeCompressed(foreign,original.toFile());
        byte[] bytes=Files.readAllBytes(original);var a=job("a");var b=job("b");Queue<SectorService.RecoveryJob> jobs=new ArrayDeque<>(List.of(a,b));List<String> warnings=new ArrayList<>();
        var next=SectorRecovery.nextReady(jobs,c->SectorHallContext53.pending(root,c),(j,m)->{if(!m.isEmpty())warnings.add(j.city()+":"+m);});
        h.assertTrue(next==b&&jobs.isEmpty()&&warnings.size()==1&&warnings.get(0).startsWith("a:"),"Corrupt city consumed another job or was silently accepted");
        h.assertTrue(Arrays.equals(bytes,Files.readAllBytes(original)),"Quarantine changed foreign history");h.succeed();
    }
    @GameTest(template="townhall_state_empty",batch="hall_concurrency53_03",timeoutTicks=100)
    public static void every_city_pending_defense_blocks_new_hall_before_any_write(GameTestHelper h)throws Exception {
        Path root=root(h);var server=h.getLevel().getServer();
        for(char c='a';c<='p';c++) {
            String city=String.valueOf(c);CompoundTag identity=new CompoundTag();identity.putString("world",root.toString());identity.putString("city",city);
            CompoundTag j=new CompoundTag();j.putUUID("id",UUID.randomUUID());j.put("identity",identity);j.putString("phase","APPLYING");
            Path history=root.resolve("data/catancraft_sector_"+city+"_north_"+j.getUUID("id")+"_v1.dat");ProducerJournal.write(history,j,false);byte[] bytes=Files.readAllBytes(history);
            boolean refused=false;try{TownHallPrestigeUpgradeService.requireDefenseClear(server,root,city);}catch(PreparationDeferred expected){refused=true;}
            h.assertTrue(refused&&Arrays.equals(bytes,Files.readAllBytes(history)),"Pending wall was ignored or rewritten in "+city);
            j.putString("phase","COMMITTED");ProducerJournal.write(history,j,true);bytes=Files.readAllBytes(history);
            TownHallPrestigeUpgradeService.requireDefenseClear(server,root,city);
            h.assertTrue(Arrays.equals(bytes,Files.readAllBytes(history)),"Completed wall history changed in "+city);
        }
        h.succeed();
    }
    @GameTest(template="townhall_state_empty",batch="hall_concurrency53_04",timeoutTicks=8000)
    public static void active_or_loading_wall_blocks_only_its_city_hall(GameTestHelper h)throws Exception {
        Path root=root(h);var server=h.getLevel().getServer();
        var data=new com.quin.catancraft.data.CatanSavedData();UUID actor=UUID.randomUUID();
        var nation=data.createNation("Hall concurrency native fixture",actor);
        nation.addResource(com.quin.catancraft.data.ResourceType.WOOD,1000);
        nation.addResource(com.quin.catancraft.data.ResourceType.STONE,1000);
        data.createTerritory("a","fixture A",com.quin.catancraft.data.ResourceType.WOOD).setOwnerNationId(nation.id());
        var context=SectorService.fixtureContext(server,root,data,"a",actor,UUID.randomUUID().toString(),()->100L);
        var north=SectorContracts.Direction.NORTH;var pos=new net.minecraft.core.BlockPos(31000,220,31000);
        for(int x=0;x<10;x++)for(int z=0;z<10;z++){h.getLevel().getChunkAt(pos.offset(x,0,z));h.getLevel().setBlock(pos.offset(x,0,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),50);}
        SectorService.Provider provider=(c,side,source)->{
            var neighbors=new EnumMap<SectorContracts.Direction,SectorContracts.Neighbor>(SectorContracts.Direction.class);
            for(var d:side.adjacent())neighbors.put(d,new SectorContracts.Neighbor(0,""));
            List<SectorTransaction.Cell> cells=new ArrayList<>();
            for(int x=0;x<10;x++)for(int z=0;z<10;z++)cells.add(new SectorTransaction.Cell(pos.offset(x,0,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState()));
            var plan=new SectorTransaction.Plan("north","CONCURRENCY_FRESH","CONCURRENCY_BASIC",0,1,cells,List.of());
            return new SectorContracts.Contract(c.root().toString(),"a",side,plan,
                Map.of(SectorContracts.Corner.NORTH_WEST,north,SectorContracts.Corner.NORTH_EAST,north),neighbors,Set.of(),SectorPriceCatalog.cost(side,0));
        };
        h.assertTrue(SectorService.quoteFixture(context,north,provider).success(),"Fixture quote failed");
        var started=SectorService.operationFixtureBounded(context,north,SectorTransaction.Action.APPLY,provider);
        h.assertTrue(started.success(),started.message());
        try {
            h.assertTrue(SectorService.hasActiveOperation(server,"a")&&!SectorService.hasActiveOperation(server,"b"),"Active city binding differs");
            CompoundTag ledger=data.save(new CompoundTag());boolean refused=false;
            try{TownHallPrestigeUpgradeService.requireDefenseClear(server,root,"a");}catch(PreparationDeferred expected){refused=true;}
            h.assertTrue(refused,"Active or loading wall permitted a Hall purchase");
            TownHallPrestigeUpgradeService.requireDefenseClear(server,root,"b");
            h.assertTrue(ledger.equals(data.save(new CompoundTag())),"Concurrency guard changed existing reservation/payment");
        } catch(Exception error){SectorService.stopped(server);throw error;}
        awaitWall(h,root,data,pos,false,0);
    }
    private static void awaitWall(GameTestHelper h,Path root,com.quin.catancraft.data.CatanSavedData data,net.minecraft.core.BlockPos pos,boolean sawPartial,int ticks){
        h.runAfterDelay(1,()->{var server=h.getLevel().getServer();try {
            h.assertTrue(ticks<7500,"Bounded fixture did not finish");int placed=0;
            for(int x=0;x<10;x++)for(int z=0;z<10;z++)if(h.getLevel().getBlockState(pos.offset(x,0,z)).is(net.minecraft.world.level.block.Blocks.STONE_BRICKS))placed++;
            boolean partial=sawPartial;
            if(placed>0&&placed<100){
                partial=true;CompoundTag ledger=data.save(new CompoundTag());boolean refused=false;
                try{TownHallPrestigeUpgradeService.requireDefenseClear(server,root,"a");}catch(PreparationDeferred expected){refused=true;}
                h.assertTrue(refused,"Partial bounded wall allowed overlapping Hall");
                TownHallPrestigeUpgradeService.requireDefenseClear(server,root,"b");
                h.assertTrue(ledger.equals(data.save(new CompoundTag())),"Guard changed partial reservation");
            }
            if(SectorService.hasActiveOperation(server)){awaitWall(h,root,data,pos,partial,ticks+1);return;}
            h.assertTrue(partial&&placed==100&&SectorService.savedState(data,"a",SectorContracts.Direction.NORTH).level()==1,"Fixture did not exercise partial through committed wall");
            CompoundTag ledger=data.save(new CompoundTag());TownHallPrestigeUpgradeService.requireDefenseClear(server,root,"a");
            h.assertTrue(ledger.equals(data.save(new CompoundTag())),"Completed wall retry changed ledger");h.succeed();
        }catch(Exception error){SectorService.stopped(server);throw new RuntimeException(error);}});
    }

}
