package com.quin.catancraft.world;

import com.google.gson.JsonObject;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.map.MapDefinitionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Authorization and real map-site binding only. Authored-world initialization
 * and physical source checks are separately exercised by the progression suite. */
@GameTestHolder("catancraft_startup53")
@PrefixGameTestTemplate(false)
public final class Startup53Tests {
    private Startup53Tests() {}
    @FunctionalInterface private interface Check { void run() throws IOException; }
    private static void refused(Check check,String expected) throws IOException {
        try { check.run(); }
        catch (IOException ex) {
            if (ex.getMessage()!=null && ex.getMessage().contains(expected)) return;
            throw new IOException("Unexpected refusal instead of '"+expected+"'",ex);
        }
        throw new IOException("Expected refusal: "+expected);
    }

    @GameTest(template="townhall_state_empty",batch="startup53",timeoutTicks=400)
    public static void every_city_automatic_context_uses_fresh_identity_without_legacy_opt_in(GameTestHelper h) throws Exception {
        var server=h.getLevel().getServer();
        h.assertTrue(server instanceof GameTestServer,"Isolated native fixture required");
        Path root=server.getWorldPath(LevelResource.ROOT).toRealPath();
        Path marker=root.resolve(FreshDemoAuthorization.MARKER);
        h.assertTrue(!Files.exists(marker) && !Files.exists(root.resolve(R5CivicAutomaticInstaller.MARKER_NAME)),
                "Fixture refuses an existing authorization marker");
        var data=CatanSavedData.get(server);
        data.syncMapDefinitions();
        for (var def:MapDefinitionManager.territories()) {
            h.assertTrue(data.territory(def.id()).ownerNationId()==null
                    && data.placedMapAsset("territory:"+def.id()+":city_base")==null,
                    "Startup authorization fixture requires fresh unowned cities");
        }
        JsonObject identity=new JsonObject();
        identity.addProperty("schema",1);
        identity.addProperty("purpose",FreshDemoAuthorization.PURPOSE);
        identity.addProperty("worldId",UUID.randomUUID().toString());
        identity.addProperty("levelName",server.getWorldData().getLevelName());
        identity.addProperty("seed",server.overworld().getSeed());
        identity.addProperty("dimension","minecraft:overworld");
        identity.addProperty("mapSha256",TownHallRevisions.sha(Files.readAllBytes(MapDefinitionManager.path())));
        identity.addProperty("authoredWorldSha256","b".repeat(64));
        identity.addProperty("bootstrapRevision","startup53-authorization-fixture-only");
        identity.addProperty("installationBoundPath",root.toString());
        Set<Path> publicFiles=new HashSet<>(),houseFiles=new HashSet<>();
        try {
            Files.writeString(marker,identity.toString(),StandardOpenOption.CREATE_NEW);
            h.assertTrue(FreshDemoAuthorization.authorize(server).equals(root),"Fresh fixture identity was refused");
            refused(()->TownHallRepairService.authorize(server),"Only the existing backed-up R7 test world");
            int north=0,south=0;
            for (var def:MapDefinitionManager.territories()) {
                String city=def.id();
                refused(()->StreetscapeService.context(server,city,false,true),"Owned, activated city required");
                refused(()->R5HouseService.context(server,city,R5HouseTransaction.Action.STATUS,false,true),"Owned activated city required");
                var nation=data.createNation("Startup53 "+city,UUID.randomUUID());
                var territory=data.territory(city);
                territory.setOwnerNationId(nation.id());
                refused(()->StreetscapeService.context(server,city,false,true),"Owned, activated city required");
                refused(()->R5HouseService.context(server,city,R5HouseTransaction.Action.STATUS,false,true),"Owned activated city required");
                data.setPlacedMapAsset("territory:"+city+":city_base",SchematicAssetRegistry.asset("starter_settlement").placementToken());
                CompoundTag before=data.save(new CompoundTag());
                var pub=StreetscapeService.context(server,city,false,true);
                var houses=R5HouseService.context(server,city,R5HouseTransaction.Action.STATUS,false,true);
                h.assertTrue(pub.identity().getString("city").equals(city)
                        && houses.identity().getString("city").equals(city)
                        && pub.identity().getString("owner").equals(nation.id().toString())
                        && houses.identity().getString("owner").equals(nation.id().toString())
                        && pub.identity().getString("world").equals(root.toString())
                        && houses.identity().getString("world").equals(root.toString()),"Wrong city/owner/world binding: "+city);
                var expectedHouse=SchematicAssetRegistry.territoryAnchor("starter_settlement",def);
                h.assertTrue(pub.plan().center()==def.cityCenter()
                        && houses.anchor().x()==expectedHouse.x() && houses.anchor().y()==expectedHouse.y()
                        && houses.anchor().z()==expectedHouse.z() && houses.anchor().rotation()==expectedHouse.rotation(),
                        "Automatic context used another city's anchor: "+city);
                h.assertTrue(publicFiles.add(pub.file()) && houseFiles.add(houses.file())
                        && !Files.exists(pub.file()) && !Files.exists(houses.file()),"Context reused or wrote a journal");
                h.assertTrue(pub.plan().cells().size()==StreetscapeR5.READS
                        && pub.plan().cells().stream().filter(StreetscapeR5.Cell::changes).count()==StreetscapeR5.WRITES,
                        "Incomplete public source contract: "+city);
                var first=pub.plan().cells().stream().filter(StreetscapeR5.Cell::changes).findFirst().orElseThrow().pos();
                pub.allowWrite(first);
                BlockPos home=R5HouseRevisions.changes(R5HouseRevisions.groups().get(0),
                        R5HouseRevisions.Revision.R2,houses.anchor()).get(0).position();
                houses.allowWrite(home);
                territory.setOwnerNationId(UUID.randomUUID());
                refused(()->pub.allowWrite(first),"outside authorized city/owner");
                refused(()->houses.allowWrite(home),"outside authorized ownership/bounds");
                territory.setOwnerNationId(nation.id());
                var other=MapDefinitionManager.territory(city.equals("a")?"p":"a").cityCenter();
                var foreign=new BlockPos(other.x(),other.y(),other.z());
                refused(()->pub.allowWrite(foreign),"outside authorized city/owner");
                refused(()->houses.allowWrite(foreign),"outside authorized ownership/bounds");
                refused(()->StreetscapeService.context(server,city,false),"Only the existing backed-up R7 test world");
                refused(()->R5HouseService.context(server,city,R5HouseTransaction.Action.STATUS,false,false),"Only the existing backed-up R7 test world");
                h.assertTrue(before.equals(data.save(new CompoundTag())),"Read-only context/refusal changed city ledger: "+city);
                if(def.cityCenter().rotation()==0)north++;else if(def.cityCenter().rotation()==180)south++;
            }
            h.assertTrue(publicFiles.size()==16 && houseFiles.size()==16 && north==8 && south==8,"Incomplete A-P orientation coverage");
            CompoundTag ledger=data.save(new CompoundTag());
            identity.addProperty("purpose","foreign-package");
            Files.writeString(marker,identity.toString());
            for(var def:MapDefinitionManager.territories()) {
                refused(()->StreetscapeService.context(server,def.id(),false,true),"Foreign or unbound");
                refused(()->R5HouseService.context(server,def.id(),R5HouseTransaction.Action.STATUS,false,true),"Foreign or unbound");
            }
            h.assertTrue(ledger.equals(data.save(new CompoundTag())),"Invalid fresh identity modified progress");
            for(Path file:publicFiles)h.assertTrue(!Files.exists(file),"Authorization test created public history");
            for(Path file:houseFiles)h.assertTrue(!Files.exists(file),"Authorization test created house history");
            h.succeed();
        } finally {
            CityBasePreparation.stopped(server);
            Files.deleteIfExists(marker);
        }
    }
}
