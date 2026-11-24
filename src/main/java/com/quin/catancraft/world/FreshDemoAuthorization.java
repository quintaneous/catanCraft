package com.quin.catancraft.world;

import com.google.gson.*;
import com.quin.catancraft.map.MapDefinitionManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Separate fresh authored-demo identity; never impersonates or relaxes historical R7 authorization.
 * This identity does not approve sector artwork, prices, or weapons. The production catalog is separate.
 */
public final class FreshDemoAuthorization {
    public static final String MARKER="catancraft-fresh-demo.json";
    public static final String PURPOSE="catancraft-fresh-authored-demo";
    private static final Set<String> FIELDS=Set.of("schema","purpose","worldId","levelName","seed","dimension","mapSha256","authoredWorldSha256","bootstrapRevision","installationBoundPath");
    static final String FULL_MAP_ID = "catancraft_full_map_review_02";
    static final String FULL_MAP_NAME = "CatanCraft – Full Map Review 02";
    static final long FULL_MAP_SEED = 1042026L;
    static final String FULL_MAP_CONFIG = "baaa524daa111ce966a12dfe304df9575251fdb1c2092b601104d5b305864fc1";
    static final String FULL_MAP_WORLD = "e9d324768b3493bf0e7a7f7beda2c6cd1fc693a8051a7a7c6cbd2c0da098d050";
    static final String FULL_MAP_REVISION = "full-map-review-02-field-v3";

    private FreshDemoAuthorization(){}

    static boolean selectsFullMap(String mapId, String levelName) {
        return FULL_MAP_ID.equals(mapId) || FULL_MAP_NAME.equals(levelName);
    }

    static boolean selectsFullMap(MinecraftServer server) {
        return selectsFullMap(MapDefinitionManager.mapId(), server.getWorldData().getLevelName());
    }

    public static Path authorize(MinecraftServer server)throws IOException {
        if(!server.isSameThread())throw new IOException("Sector operation requires the server thread");
        Path root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        boundIdentity(server, root);
        return root.toRealPath();
    }

    public static String worldId(MinecraftServer server,Path root)throws IOException {
        return boundIdentity(server, root).get("worldId").getAsString();
    }

    private static JsonObject boundIdentity(MinecraftServer server, Path root) throws IOException {
        String mapId = MapDefinitionManager.mapId();
        String name = server.getWorldData().getLevelName();
        long seed = server.overworld().getSeed();
        if (selectsFullMap(mapId, name) && !Files.exists(root.resolve(MARKER), LinkOption.NOFOLLOW_LINKS))
            throw new IOException("This Full Map Review 02 save has no playtest installation binding. Start a fresh copy using the supplied installer.");
        JsonObject identity = validate(root, MapDefinitionManager.path(), name, seed, server.overworld().dimension().location().toString());
        verifyFullMapPins(mapId, name, seed, identity);
        return identity;
    }

    static void verifyFullMapPins(String mapId, String name, long seed, JsonObject identity) throws IOException {
        if (!selectsFullMap(mapId, name) && !FULL_MAP_REVISION.equals(identity.get("bootstrapRevision").getAsString())) return;
        if (!FULL_MAP_ID.equals(mapId) || !FULL_MAP_NAME.equals(name) || seed != FULL_MAP_SEED
                || !FULL_MAP_CONFIG.equals(identity.get("mapSha256").getAsString())
                || !FULL_MAP_WORLD.equals(identity.get("authoredWorldSha256").getAsString())
                || !FULL_MAP_REVISION.equals(identity.get("bootstrapRevision").getAsString()))
            throw new IOException("The loaded map, authored save, or installation binding differs from the Full Map Review 02 field profile.");
    }

    static JsonObject validate(Path root,Path map,String levelName,long seed,String dimension)throws IOException {
        root=root.toAbsolutePath().normalize();rejectSymlinks(root);
        if(!Files.isDirectory(root)||!root.equals(root.toRealPath()))throw new IOException("Fresh demo world path is unavailable or not canonical");
        Path marker=root.resolve(MARKER);rejectSymlinks(marker);rejectSymlinks(map.toAbsolutePath().normalize());
        if(!Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS)||Files.size(marker)>16384)throw new IOException("Missing bounded fresh authored-demo identity; do not copy old test authorization");
        if(!Files.isRegularFile(map,LinkOption.NOFOLLOW_LINKS)||Files.size(map)>16*1024*1024)throw new IOException("Fresh demo map configuration unavailable");
        try {
            JsonObject json=JsonParser.parseString(Files.readString(marker)).getAsJsonObject();
            if(!json.keySet().equals(FIELDS)||!json.get("schema").isJsonPrimitive()||!json.getAsJsonPrimitive("schema").isNumber()||!json.get("schema").getAsBigDecimal().equals(java.math.BigDecimal.ONE))throw new IOException("Unknown fresh demo identity schema");
            for(String field:FIELDS)if(!Set.of("schema","seed").contains(field)&&(!json.get(field).isJsonPrimitive()||!json.getAsJsonPrimitive(field).isString()||json.get(field).getAsString().isBlank()))throw new IOException("Invalid fresh demo identity field: "+field);
            if(!json.get("seed").isJsonPrimitive()||!json.getAsJsonPrimitive("seed").isNumber()||json.get("seed").getAsBigDecimal().longValueExact()!=seed)throw new IOException("Fresh demo seed differs from authored package");
            String id=json.get("worldId").getAsString();if(!UUID.fromString(id).toString().equals(id))throw new IOException("Invalid fresh demo world UUID");
            if(!PURPOSE.equals(json.get("purpose").getAsString())||!root.toString().equals(json.get("installationBoundPath").getAsString())||!levelName.equals(json.get("levelName").getAsString())||!dimension.equals(json.get("dimension").getAsString())||!dimension.equals("minecraft:overworld"))throw new IOException("Foreign or unbound fresh authored-demo identity");
            for(String field:List.of("mapSha256","authoredWorldSha256"))if(!json.get(field).getAsString().matches("[0-9a-f]{64}"))throw new IOException("Invalid fresh demo input digest");
            if(!json.get("bootstrapRevision").getAsString().matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"))throw new IOException("Invalid fresh bootstrap revision");
            if(!TownHallRevisions.sha(Files.readAllBytes(map)).equals(json.get("mapSha256").getAsString()))throw new IOException("Fresh demo map changed from bound authored contract");
            return json;
        }catch(IllegalArgumentException|IllegalStateException|ArithmeticException|NullPointerException|JsonParseException ex){throw new IOException("Malformed fresh authored-demo identity",ex);}
    }
    private static void rejectSymlinks(Path path)throws IOException {
        for(Path p=path;p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("Fresh demo identity refuses symlink paths");
    }
}
