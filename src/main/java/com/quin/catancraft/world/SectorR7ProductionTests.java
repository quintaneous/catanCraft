package com.quin.catancraft.world;

import com.google.gson.*;
import com.quin.catancraft.data.*;
import com.quin.catancraft.map.MapDefinitionManager;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Only the separately staged, hash-verified complete clean R7 copy. Never stages schematic preview geometry. */
@GameTestHolder("catancraft_r7_production") @PrefixGameTestTemplate(false)
public final class SectorR7ProductionTests {
    private static final TicketType<ChunkPos> TICKET=TicketType.create("catancraft_r7_production_test",Comparator.comparingLong(ChunkPos::toLong));
    private static final List<String> SEQUENCE=List.of("north","east","south","west","north","east","south","west");
    private static final AABB CITY=new AABB(-855,65,-865,-640,140,-640);
    @GameTest(template="townhall_state_empty",batch="r7_production",timeoutTicks=30000)
    public static void authored_r7_normal_bootstrap_four_sectors_and_connected_upgrades(GameTestHelper h){try{
        var server=h.getLevel().getServer();h.assertTrue(server instanceof GameTestServer,"Requires isolated native GameTestServer");
        Path root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().toRealPath();
        JsonObject input=JsonParser.parseString(Files.readString(Path.of("config/catancraft/r7-native-input.json"))).getAsJsonObject();
        h.assertTrue(input.get("schema").getAsInt()==1&&input.get("purpose").getAsString().equals("verified-clean-r7-native-test"),"Missing verified raw-world manifest");
        h.assertTrue(root.getFileName().toString().equals(input.get("worldFolder").getAsString())&&server.getWorldData().getLevelName().equals(input.get("levelName").getAsString())&&server.overworld().getSeed()==input.get("seed").getAsLong(),"Authored world identity was replaced");
        h.assertTrue(TownHallRevisions.sha(Files.readAllBytes(MapDefinitionManager.path())).equals(input.get("mapSha256").getAsString()),"Staged current map digest differs");
        TownHallRepairService.authorize(server);
        var data=CatanSavedData.get(server);h.assertTrue(data.territory("a")!=null&&data.territory("a").ownerNationId()==null,"Expected clean unowned City A");
        UUID actor=UUID.randomUUID();var nation=data.createNation("Isolated R7 native test",actor);
        var missing=SectorService.quote(server,actor,"a","north");no(h,missing);h.assertTrue(missing.message().contains("Missing bounded fresh authored-demo identity"),"Wrong missing-identity refusal: "+missing.message());
        Path marker=root.resolve(FreshDemoAuthorization.MARKER);h.assertTrue(!Files.exists(marker),"Refusing existing fresh authorization identity");
        JsonObject identity=new JsonObject();identity.addProperty("schema",1);identity.addProperty("purpose",FreshDemoAuthorization.PURPOSE);identity.addProperty("worldId",UUID.randomUUID().toString());
        for(String key:List.of("levelName","seed","mapSha256","authoredWorldSha256","bootstrapRevision"))identity.add(key,input.get(key));
        identity.addProperty("dimension","minecraft:overworld");identity.addProperty("installationBoundPath",root.toString());Files.writeString(marker,identity.toString(),StandardOpenOption.CREATE_NEW);
        FreshDemoAuthorization.authorize(server);
        for(int x=-55;x<=-39;x++)for(int z=-55;z<=-39;z++){ChunkPos pos=new ChunkPos(x,z);h.getLevel().getChunkSource().addRegionTicket(TICKET,pos,2,pos);h.getLevel().getChunk(x,z);}
        ready(h,root,actor,nation.id(),0);
    }catch(Exception e){throw new RuntimeException(e);}}
    private static void ready(GameTestHelper h,Path root,UUID actor,UUID owner,int tries){h.runAfterDelay(tries==0?20:1,()->{try{
        try{StreetscapeService.requireLoadedEntities(h.getLevel(),List.of(CITY));}catch(IOException loading){if(tries<600){ready(h,root,actor,owner,tries+1);return;}throw loading;}
        var server=h.getLevel().getServer();var data=CatanSavedData.get(server);var nation=data.nation(owner);
        ok(h,MapAssetService.activateTerritory(server,data,MapDefinitionManager.territory("a"),true));
        data.territory("a").setOwnerNationId(owner);data.setDirty();
        var result=StreetscapeTransaction.run(StreetscapeService.context(server,"a",true),StreetscapeTransaction.Action.APPLY);
        com.mojang.logging.LogUtils.getLogger().info("R7 production normal public bootstrap: {} writes; NO-GRANT treasury={} stockpile={}",result.writes(),nation.treasury(),nation.stockpileView());
        no(h,SectorService.quote(server,UUID.randomUUID(),"a","north"));
        var noGrant=nation.save();ok(h,SectorService.quote(server,actor,"a","north"));
        var unfunded=SectorService.operation(server,actor,"a","north",SectorTransaction.Action.APPLY);no(h,unfunded);
        h.assertTrue(unfunded.message().contains("Cannot afford complete sector")&&nation.treasury()>=1500&&nation.resource(ResourceType.WOOD)<15&&nation.resource(ResourceType.STONE)<30&&nation.save().equals(noGrant)&&SectorService.savedState(data,"a",SectorContracts.Direction.NORTH).level()==0,"Unfunded raw-world request was not a free material refusal: "+unfunded.message());
        com.mojang.logging.LogUtils.getLogger().info("R7 NO-GRANT purchase refusal: {}",unfunded.message());
        nation.addTreasury(100000);for(var resource:List.of(ResourceType.WOOD,ResourceType.STONE,ResourceType.CONCRETE,ResourceType.STEEL))nation.addResource(resource,1000);data.setDirty();
        com.mojang.logging.LogUtils.getLogger().info("R7 production explicit SHOWROOM grant only: money=100000, Wood/Stone/Concrete/Steel=1000 each");
        buy(h,root,actor,owner,0,nation.treasury(),Map.copyOf(nation.stockpileView()));
    }catch(Exception e){throw new RuntimeException(e);}});}
    private static void buy(GameTestHelper h,Path root,UUID actor,UUID owner,int index,long money,Map<ResourceType,Long> resources)throws IOException{
        var server=h.getLevel().getServer();var data=CatanSavedData.get(server);
        if(index==SEQUENCE.size()){
            var nation=data.nation(owner);
            com.mojang.logging.LogUtils.getLogger().info("R7 eight exact same-tick charges verified: money=22000 Wood=60 Stone=120 Concrete=200 Steel=60; running economy remains enabled; final treasury={} stockpile={}",nation.treasury(),nation.stockpileView());
            for(var side:SectorContracts.Direction.values()){h.assertTrue(SectorService.savedState(data,"a",side).level()==2,"Reinforced level missing");ok(h,SectorService.operation(server,actor,"a",side.name().toLowerCase(Locale.ROOT),SectorTransaction.Action.STATUS));}
            h.assertTrue(server.saveEverything(true,true,true),"Production save failed");
            var disk=CatanSavedData.load(NbtIo.readCompressed(root.resolve("data/catancraft.dat").toFile()).getCompound("data"));
            h.assertTrue(disk.nation(owner).save().equals(nation.save()),"Real SavedData disk nation differs");
            for(var side:SectorContracts.Direction.values())h.assertTrue(SectorService.savedState(disk,"a",side).equals(SectorService.savedState(data,"a",side)),"Real SavedData disk sector differs");
            com.mojang.logging.LogUtils.getLogger().info("R7 PRODUCTION PASS: normal bootstrap, eight grouped purchases/upgrades, actual persisted SavedData; process restart remains separate");h.succeed();return;
        }
        String side=SEQUENCE.get(index);int before=SectorService.savedState(data,"a",SectorContracts.Direction.valueOf(side.toUpperCase(Locale.ROOT))).level();
        long start=System.nanoTime();ok(h,SectorService.quote(server,actor,"a",side));
        var payer=data.nation(owner);long preMoney=payer.treasury();var preStock=Map.copyOf(payer.stockpileView());ok(h,SectorService.operation(server,actor,"a",side,SectorTransaction.Action.APPLY));
        h.assertTrue(payer.treasury()==preMoney-(before==0?1500:4000),"Exact same-tick money debit differs");
        var cost=before==0?Map.of(ResourceType.WOOD,15L,ResourceType.STONE,30L):Map.of(ResourceType.CONCRETE,50L,ResourceType.STEEL,15L);
        for(var resource:ResourceType.values())h.assertTrue(payer.resource(resource)==preStock.getOrDefault(resource,0L)-cost.getOrDefault(resource,0L),"Exact same-tick material debit differs: "+resource);
        com.mojang.logging.LogUtils.getLogger().info("R7 production {} target {} quote/preparation {} ms",side,before+1,(System.nanoTime()-start)/1e6);
        long charged=data.nation(owner).treasury();no(h,SectorService.operation(server,actor,"a",side,SectorTransaction.Action.APPLY));h.assertTrue(data.nation(owner).treasury()==charged,"Active duplicate charged again");
        waitPurchase(h,root,actor,owner,index,before,money,resources,0);
    }
    private static void waitPurchase(GameTestHelper h,Path root,UUID actor,UUID owner,int index,int before,long money,Map<ResourceType,Long> resources,int ticks){h.runAfterDelay(1,()->{try{
        var server=h.getLevel().getServer();var data=CatanSavedData.get(server);String side=SEQUENCE.get(index);
        int now=SectorService.savedState(data,"a",SectorContracts.Direction.valueOf(side.toUpperCase(Locale.ROOT))).level();
        if(now==before){h.assertTrue(ticks<5000,"Production runner did not finish; inspect refusal log/pending journal");waitPurchase(h,root,actor,owner,index,before,money,resources,ticks+1);return;}
        h.assertTrue(now==before+1,"Wrong completed level");
        String pointer=data.placedMapAsset("sector_v1:a:"+side+":operation");
        var journal=ProducerJournal.read(root.resolve("data").resolve(pointer));var cost=journal.getCompound("cost");
        h.assertTrue(journal.getString("phase").equals("COMMITTED")&&cost.getLong("money")== (before==0?1500:4000),"Original agreed price or durable completion missing");
        ok(h,SectorService.operation(server,actor,"a",side,SectorTransaction.Action.STATUS));buy(h,root,actor,owner,index+1,money,resources);
    }catch(Exception e){throw new RuntimeException(e);}});}
    private static void ok(GameTestHelper h,MapAssetService.Result r){h.assertTrue(r.success(),r.message());}
    private static void no(GameTestHelper h,MapAssetService.Result r){h.assertTrue(!r.success(),"Expected refusal: "+r.message());}
}
