package com.quin.catancraft.world;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.quin.catancraft.data.BuildingType;
import com.quin.catancraft.map.*;
import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Exact physical sites from the reviewed map; recognition is never spending authority.
 * Uses the actual placement transform, including the extra plot-4 factory flip. */
final class ShopSites51 {
    static final String MAP_SHA256 = "e50d9753c807b5fe6f8fdee9765ae09640d951cc0ba2d59f1612f0923485c986";
    static final String REVISION = "all_city_weapon_counter51_v1";
    private static final Gson GSON = new Gson();
    private static Map<String, TerritoryDefinition> reviewed;

    record Site(TerritoryDefinition definition, String plot, MapAnchor anchor, BlockPos counter) {
        boolean starter() { return plot.equals("town_hall"); }
        String city() { return definition.id(); }
    }

    private ShopSites51() {}

    static Collection<TerritoryDefinition> reviewedDefinitions() throws IOException {
        return reviewed().values();
    }

    private static Map<String, TerritoryDefinition> reviewed() throws IOException {
        if (reviewed != null) return reviewed;
        try (var stream = ShopSites51.class.getResourceAsStream("/data/catancraft/maps/season1_river_bridges_v3.json")) {
            if (stream == null) throw new IOException("Reviewed shop map is unavailable.");
            byte[] bytes = stream.readAllBytes();
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!MAP_SHA256.equals(sha)) throw new IOException("Reviewed shop map changed; site qualification is required.");
            var map = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), CatanMapDefinition.class);
            Map<String, TerritoryDefinition> sites = new TreeMap<>();
            for (var city : map.territories()) {
                if (sites.putIfAbsent(city.id(), city) != null) throw new IOException("Duplicate reviewed shop city.");
            }
            if (sites.size() != 16) throw new IOException("Reviewed shop roster is incomplete.");
            reviewed = Map.copyOf(sites);
            return reviewed;
        } catch (java.security.NoSuchAlgorithmException | RuntimeException failure) {
            throw new IOException("Reviewed shop map could not be verified.", failure);
        }
    }

    static List<Site> sites(TerritoryDefinition definition) throws IOException {
        if (definition == null) throw new IOException("Shop city is unavailable.");
        List<Site> sites = new ArrayList<>();
        MapAnchor manager = definition.managementAnchor();
        if (manager != null) sites.add(new Site(definition, "town_hall", manager,
                new BlockPos(manager.x(), manager.y(), manager.z())));
        for (String plot : IndustrialPlotService.INDUSTRIAL_PLOTS) {
            if (definition.anchor(plot) == null || definition.reservedPlotIds().contains(plot)) continue;
            MapAnchor anchor = IndustrialPlotService.anchorFor(BuildingType.WEAPONS_FACTORY, plot, definition);
            sites.add(new Site(definition, plot, anchor, ShopFacility40.local(anchor, 19, 3, 18)));
        }
        return List.copyOf(sites);
    }

    /** Include reviewed coordinates so changed map data produces a refusal instead of vanilla crafting. */
    static boolean recognizes(String dimension, BlockPos counter, boolean starterOnly) {
        try {
            List<TerritoryDefinition> definitions = new ArrayList<>(reviewed().values());
            definitions.addAll(MapDefinitionManager.territories());
            for (var definition : definitions) {
                if (!definition.dimension().equals(dimension)) continue;
                try {
                    for (var site : sites(definition))
                        if ((!starterOnly || site.starter()) && site.counter().equals(counter)) return true;
                } catch (IOException | RuntimeException invalid) {
                    // Invalid current definitions cannot grant authority; reviewed locations remain recognized.
                }
            }
        } catch (IOException invalid) { return false; }
        return false;
    }

    static Site resolve(String dimension, BlockPos counter) throws IOException {
        return resolve(MapDefinitionManager.territories(), dimension, counter);
    }

    static Site resolve(Collection<TerritoryDefinition> definitions, String dimension, BlockPos counter) throws IOException {
        List<Site> matches = new ArrayList<>();
        for (var definition : definitions) {
            if (!definition.dimension().equals(dimension)) continue;
            for (var site : sites(definition)) if (site.counter().equals(counter)) matches.add(site);
        }
        if (matches.size() != 1)
            throw new IOException("This counter has no unique city/building binding; its map placement needs review.");
        Site site = matches.get(0);
        canonical(site.definition());
        return site;
    }

    static void canonical(TerritoryDefinition definition) throws IOException {
        ProducerService.validateMap(definition);
        TerritoryDefinition expected = reviewed().get(definition.id());
        if (expected == null) throw new IOException("This city has no reviewed shop placement.");
        JsonObject actual = GSON.toJsonTree(definition).getAsJsonObject();
        JsonObject original = GSON.toJsonTree(expected).getAsJsonObject();
        for (String field : List.of("id", "dimension", "boundary", "cityCenter", "townHall", "managementAnchor",
                "settlementAnchor", "buildingPlots", "reservedPlotIds", "siegeRegion", "restorationRegion")) {
            if (!Objects.equals(actual.get(field), original.get(field)))
                throw new IOException("City " + definition.id().toUpperCase(Locale.ROOT)
                        + " shop placement changed (" + field + "); qualification is required.");
        }
    }

    static String origin(net.minecraft.nbt.CompoundTag identity) {
        String city = identity.getString("city");
        if (!city.matches("[a-p]")) return "the original counter";
        String place = identity.getString("type").equals("starter") ? "Town Hall" : "Weapons Factory";
        return "City " + city.toUpperCase(Locale.ROOT) + " " + place;
    }
}
