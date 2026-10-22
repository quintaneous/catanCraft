package com.quin.catancraft.world;

import com.google.gson.JsonObject;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@GameTestHolder("catancraft_sectors_service") @PrefixGameTestTemplate(false)
public final class FreshDemoAuthorizationTests {
    @GameTest(template="townhall_state_empty",batch="fresh_demo_identity",timeoutTicks=200)
    public static void bound_fresh_identity_preserves_progress_and_refuses_foreign_world(GameTestHelper h) {
        try {
            Path root=Files.createTempDirectory("catancraft-fresh-auth-test-").toRealPath(),map=root.resolve("fixture-map.json");Files.writeString(map,"{}");
            JsonObject n=new JsonObject();n.addProperty("schema",1);n.addProperty("purpose",FreshDemoAuthorization.PURPOSE);n.addProperty("worldId",UUID.randomUUID().toString());n.addProperty("levelName","Synthetic authored identity fixture");n.addProperty("seed",7);n.addProperty("dimension","minecraft:overworld");n.addProperty("mapSha256",TownHallRevisions.sha(Files.readAllBytes(map)));n.addProperty("authoredWorldSha256","a".repeat(64));n.addProperty("bootstrapRevision","synthetic-fixture-v1");n.addProperty("installationBoundPath",root.toString());
            Path marker=root.resolve(FreshDemoAuthorization.MARKER);Files.writeString(marker,n.toString());
            FreshDemoAuthorization.validate(root,map,"Synthetic authored identity fixture",7,"minecraft:overworld");
            Files.writeString(root.resolve("normal-gameplay-progress.dat"),"legitimate future saved progress");
            FreshDemoAuthorization.validate(root,map,"Synthetic authored identity fixture",7,"minecraft:overworld");
            refused(()->FreshDemoAuthorization.validate(root,map,"Other world",7,"minecraft:overworld"));
            refused(()->FreshDemoAuthorization.validate(root,map,"Synthetic authored identity fixture",8,"minecraft:overworld"));
            n.addProperty("installationBoundPath",root.resolve("another-save").toString());Files.writeString(marker,n.toString());refused(()->FreshDemoAuthorization.validate(root,map,"Synthetic authored identity fixture",7,"minecraft:overworld"));
            n.addProperty("installationBoundPath",root.toString());Files.writeString(marker,n.toString());Files.writeString(map,"{\"changed\":true}");refused(()->FreshDemoAuthorization.validate(root,map,"Synthetic authored identity fixture",7,"minecraft:overworld"));
            h.assertTrue(Files.readString(root.resolve("normal-gameplay-progress.dat")).equals("legitimate future saved progress"),"Authorization altered progress");h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    @GameTest(template="townhall_state_empty",batch="fresh_demo_progression_auth",timeoutTicks=200)
    public static void ordinary_progression_uses_bound_demo_without_legacy_repair_marker(GameTestHelper h) {
        Path marker=null;
        try {
            var server=h.getLevel().getServer();
            if(!(server instanceof GameTestServer))throw new IOException("Disposable native fixture only");
            Path root=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toRealPath();
            Path map=com.quin.catancraft.map.MapDefinitionManager.path();
            JsonObject n=new JsonObject();n.addProperty("schema",1);n.addProperty("purpose",FreshDemoAuthorization.PURPOSE);n.addProperty("worldId",UUID.randomUUID().toString());n.addProperty("levelName",server.getWorldData().getLevelName());n.addProperty("seed",server.overworld().getSeed());n.addProperty("dimension","minecraft:overworld");n.addProperty("mapSha256",TownHallRevisions.sha(Files.readAllBytes(map)));n.addProperty("authoredWorldSha256","b".repeat(64));n.addProperty("bootstrapRevision","synthetic-auth-only-fixture");n.addProperty("installationBoundPath",root.toString());
            Path candidate=root.resolve(FreshDemoAuthorization.MARKER);
            Files.writeString(candidate,n.toString(),StandardOpenOption.CREATE_NEW);marker=candidate;
            h.assertTrue(ConstructionWorldAuthorization.authorize(server).equals(root),"Normal progression refused separately bound demo");
            R5CivicAutomaticInstaller.ensureTargetWorld(server,root);
            refused(()->TownHallRepairService.authorize(server));
            n.addProperty("purpose","foreign-purpose");Files.writeString(marker,n.toString());
            refused(()->ConstructionWorldAuthorization.authorize(server));
            refused(()->R5CivicAutomaticInstaller.ensureTargetWorld(server,root));
            n.addProperty("purpose",FreshDemoAuthorization.PURPOSE);n.add("seed",new JsonObject());Files.writeString(marker,n.toString());
            refused(()->ConstructionWorldAuthorization.authorize(server));h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
        finally{if(marker!=null)try{Files.delete(marker);}catch(IOException ex){throw new RuntimeException(ex);}}
    }
    private interface Check{void run()throws IOException;}
    private static void refused(Check c)throws IOException{try{c.run();}catch(IOException expected){return;}throw new IOException("Expected fresh world identity refusal");}
}
