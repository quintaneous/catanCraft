package com.quin.catancraft.map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.quin.catancraft.client.ClientTerritoryHud;
import com.quin.catancraft.network.TerritoryHudPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.TreeSet;
import java.util.zip.ZipFile;

/** Tests saved-map place names against independently surveyed construction bounds. No game process required. */
public final class PlaceLabelTest {
    private static final String MAP_ENTRY = "payload/common/config/catancraft/map.json";
    private static final String MAP_HASH = "baaa524daa111ce966a12dfe304df9575251fdb1c2092b601104d5b305864fc1";
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static int labels;

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Pass the friends ZIP and city-survey.json");
        byte[] mapBytes;
        try (var zip = new ZipFile(args[0])) {
            mapBytes = zip.getInputStream(zip.getEntry(MAP_ENTRY)).readAllBytes();
        }
        var survey = JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonObject();
        var digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapBytes));
        if (!MAP_HASH.equals(digest) || !digest.equals(survey.get("fullMapConfigSha256").getAsString())) {
            throw new AssertionError("ZIP/survey map identity mismatch");
        }
        var mapJson = JsonParser.parseString(new String(mapBytes, StandardCharsets.UTF_8)).getAsJsonObject();
        var map = new Gson().fromJson(mapJson, CatanMapDefinition.class);
        var resolver = new PlaceLabel(map);
        var cities = new TreeSet<String>();
        for (var territory : map.territories()) {
            if (!cities.add(territory.id())) throw new AssertionError("Duplicate city " + territory.id());
            var surveyed = survey.getAsJsonObject("cities").getAsJsonObject(territory.id());
            checkCity(resolver, territory, surveyed.getAsJsonObject("assets").getAsJsonObject("city_base"));
        }
        if (!String.join("", cities).equals("abcdefghijklmnop")) throw new AssertionError(cities);

        var district = "Central Objective District";
        expect(resolver, OVERWORLD, 0, 81, 23, district);  // Authored spawn inside the district.
        expect(resolver, OVERWORLD, -150, 81, -150, district);
        expect(resolver, OVERWORLD, 149, 81, 149, district);
        expect(resolver, OVERWORLD, -151, 81, -150, "");
        expect(resolver, OVERWORLD, 150, 81, 149, "");
        expect(resolver, NETHER, 0, 81, 23, "");
        expect(resolver, OVERWORLD, 1200, 81, 1200, "");
        expect(resolver, OVERWORLD, -16, 81, -561, "");  // North Cross-Valley Bridge
        expect(resolver, OVERWORLD, 16, 81, 561, "");    // South Cross-Valley Bridge
        var unsupported = mapJson.deepCopy();
        unsupported.getAsJsonArray("territories").get(0).getAsJsonObject()
                .addProperty("settlementTemplate", "unknown_layout");
        expect(new PlaceLabel(new Gson().fromJson(unsupported, CatanMapDefinition.class)),
                OVERWORLD, -750, 81, -750, "");

        // Same client walks out, enters the district, then leaves the dimension.
        var clientLabel = ClientTerritoryHud.class.getDeclaredField("label");
        clientLabel.setAccessible(true);
        for (var name : new String[]{"Northwest Hearth", "", district, ""}) {
            ClientTerritoryHud.update(packetRoundTrip(name, name));
            if (!name.equals(clientLabel.get(null))) throw new AssertionError("Stale client label after " + name);
        }
        ClientTerritoryHud.update(district);
        ClientTerritoryHud.onLogout(null);
        if (!"".equals(clientLabel.get(null))) throw new AssertionError("Stale label on disconnect");
        String prefix = "x".repeat(127);
        packetRoundTrip(prefix + "AB", prefix + "A");
        packetRoundTrip(prefix + "🌆", prefix);
        System.out.printf("PASS %d resolved labels; 16 cities, 2 bridges, district and wilderness; "
                + "6 packet round trips; 4 client transitions and disconnect clear%n", labels);
    }

    private static void checkCity(PlaceLabel resolver, TerritoryDefinition city, JsonObject base) {
        var center = city.cityCenter();
        String dim = city.dimension();
        String name = city.name();
        if (base.get("stateDifference").getAsInt() != 0) throw new AssertionError("Survey differs at " + city.id());
        var min = base.getAsJsonArray("min");
        var max = base.getAsJsonArray("max");
        int x0 = min.get(0).getAsInt(), x1 = max.get(0).getAsInt(), x = center.x();
        int y0 = min.get(1).getAsInt(), y1 = max.get(1).getAsInt(), y = center.y();
        int z0 = min.get(2).getAsInt(), z1 = max.get(2).getAsInt(), z = center.z();
        for (int bx : new int[]{x0, x, x1}) {
            for (int bz : new int[]{z0, z, z1}) {
                expect(resolver, dim, bx, y, bz, name);    // Inclusive edges, corners and center.
            }
        }
        for (var anchor : city.buildingPlots()) expect(resolver, dim, anchor.x(), anchor.y(), anchor.z(), name);
        expect(resolver, dim, city.townHall().x(), city.townHall().y(), city.townHall().z(), name);
        expect(resolver, dim, x, y0, z, name);
        expect(resolver, dim, x, y1, z, name);
        expect(resolver, dim, x, y0 - 1, z, "");
        expect(resolver, dim, x, y1 + 1, z, "");
        expect(resolver, NETHER, x, y, z, "");

        for (var point : new int[][]{{x0-1,z},{x1+1,z},{x,z0-1},{x,z1+1}}) {
            if (!city.contains(dim, point[0], point[1])) throw new AssertionError("Expected same territory");
            expect(resolver, dim, point[0], y, point[1], "");
        }
    }

    private static void expect(PlaceLabel resolver, String dimension, int x, int y, int z, String expected) {
        var actual = resolver.at(dimension, new BlockPos(x, y, z));
        if (!expected.equals(actual)) {
            throw new AssertionError(dimension + " (" + x + ", " + y + ", " + z
                    + ") expected '" + expected + "', got '" + actual + "'");
        }
        labels++;
    }

    private static String packetRoundTrip(String label, String expected) {
        var wire = new FriendlyByteBuf(Unpooled.buffer());
        var decoded = new FriendlyByteBuf(Unpooled.buffer());
        try {
            TerritoryHudPacket.encode(new TerritoryHudPacket(label), wire);
            TerritoryHudPacket.encode(TerritoryHudPacket.decode(wire), decoded);
            String text = decoded.readUtf(128);
            if (!expected.equals(text) || wire.isReadable() || decoded.isReadable()) {
                throw new AssertionError("HUD label changed in transit: " + label);
            }
            return text;
        } finally {
            wire.release();
            decoded.release();
        }
    }
}
