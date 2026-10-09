package com.quin.catancraft.map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.data.ResourceType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MapDefinitionManager {
    public record LoadResult(
            int territories,
            int monuments,
            List<String> warnings,
            Path path
    ) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path MAP_PATH =
            FMLPaths.CONFIGDIR.get().resolve("catancraft").resolve("map.json");

    private static CatanMapDefinition current = new CatanMapDefinition();
    private static Map<String, TerritoryDefinition> territories = Map.of();
    private static Map<String, MonumentDefinition> monuments = Map.of();

    private MapDefinitionManager() {}

    public static Path path() {
        return MAP_PATH;
    }

    public static Collection<TerritoryDefinition> territories() {
        return territories.values();
    }

    public static Collection<MonumentDefinition> monuments() {
        return monuments.values();
    }

    @Nullable
    public static TerritoryDefinition territory(String id) {
        return id == null ? null : territories.get(id.toLowerCase());
    }

    @Nullable
    public static MonumentDefinition monument(String id) {
        return id == null ? null : monuments.get(id.toLowerCase());
    }

    @Nullable
    public static TerritoryDefinition territoryAt(ServerLevel level, BlockPos pos) {
        String dimension = level.dimension().location().toString();
        for (TerritoryDefinition definition : territories.values()) {
            if (definition.contains(dimension, pos.getX(), pos.getZ())) {
                return definition;
            }
        }
        return null;
    }

    public static LoadResult reload(MinecraftServer server) throws IOException {
        Files.createDirectories(MAP_PATH.getParent());
        if (!Files.exists(MAP_PATH)) {
            try (Writer writer = Files.newBufferedWriter(MAP_PATH)) {
                GSON.toJson(new CatanMapDefinition(), writer);
            }
        }

        CatanMapDefinition loaded;
        try (Reader reader = Files.newBufferedReader(MAP_PATH)) {
            loaded = GSON.fromJson(reader, CatanMapDefinition.class);
        }
        if (loaded == null) loaded = new CatanMapDefinition();

        List<String> warnings = new ArrayList<>();
        Map<String, TerritoryDefinition> territoryMap = validateTerritories(loaded, warnings);
        Map<String, MonumentDefinition> monumentMap = validateMonuments(loaded, warnings);

        current = loaded;
        territories = Map.copyOf(territoryMap);
        monuments = Map.copyOf(monumentMap);

        CatanSavedData data = CatanSavedData.get(server);
        data.syncMapDefinitions();
        data.setDirty();

        CatanCraft.LOGGER.info(
                "Loaded CatanCraft map definition: {} territories, {} monuments from {}",
                territories.size(),
                monuments.size(),
                MAP_PATH
        );
        for (String warning : warnings) {
            CatanCraft.LOGGER.warn("CatanCraft map definition: {}", warning);
        }

        return new LoadResult(
                territories.size(),
                monuments.size(),
                List.copyOf(warnings),
                MAP_PATH
        );
    }

    private static Map<String, TerritoryDefinition> validateTerritories(
            CatanMapDefinition definition,
            List<String> warnings
    ) {
        if (definition.format() != 1) {
            throw new IllegalArgumentException(
                    "Unsupported CatanCraft map format " + definition.format() + "; expected 1.");
        }

        Map<String, TerritoryDefinition> result = new LinkedHashMap<>();
        for (TerritoryDefinition territory : definition.territories()) {
            String id = territory.id();
            if (id.isBlank()) {
                throw new IllegalArgumentException("Territory id cannot be blank.");
            }
            if (result.putIfAbsent(id, territory) != null) {
                throw new IllegalArgumentException("Duplicate territory id: " + id);
            }

            ResourceType specialty = territory.specialty();
            if (!isRawResource(specialty)) {
                throw new IllegalArgumentException(
                        "Territory " + id + " uses processed resource " +
                                specialty.id() + "; territory specialties must be raw resources.");
            }

            if (territory.boundaryPoints().size() < 3) {
                warnings.add("Territory " + id + " has fewer than 3 boundary points.");
            }
            if (territory.cityCenter() == null) {
                warnings.add("Territory " + id + " has no cityCenter anchor yet.");
            }
            if (territory.townHall() == null) {
                warnings.add("Territory " + id + " has no townHall anchor yet.");
            }
            if (territory.restorationRegion() == null) {
                warnings.add("Territory " + id + " has no restorationRegion yet.");
            } else if (!territory.restorationRegion().isReasonable()) {
                throw new IllegalArgumentException(
                        "Territory " + id + " has an invalid restorationRegion.");
            }

            if (territory.siegeRegion() != null) {
                if (!territory.siegeRegion().isReasonable()) {
                    throw new IllegalArgumentException(
                            "Territory " + id + " has an invalid siegeRegion.");
                }
                if (territory.restorationRegion() == null) {
                    throw new IllegalArgumentException(
                            "Territory " + id +
                                    " defines a siegeRegion but no restorationRegion.");
                }
                if (!territory.restorationRegion().contains(territory.siegeRegion())) {
                    throw new IllegalArgumentException(
                            "Territory " + id +
                                    " siegeRegion extends outside its restorationRegion.");
                }
            }

            Set<String> plotIds = new HashSet<>();
            for (MapAnchor plot : territory.buildingPlots()) {
                if (plot.id().isBlank()) {
                    throw new IllegalArgumentException(
                            "Territory " + id + " has a building plot with no id.");
                }
                if (!plotIds.add(plot.id())) {
                    throw new IllegalArgumentException(
                            "Territory " + id +
                                    " has duplicate building plot id " + plot.id());
                }
                if (plot.rotation() % 90 != 0) {
                    throw new IllegalArgumentException(
                            "Territory " + id + " plot " + plot.id() +
                                    " rotation must be 0/90/180/270.");
                }
            }

            Set<String> defenseIds = new HashSet<>();
            for (MapAnchor anchor : territory.defenseAnchors()) {
                if (anchor.id().isBlank()) {
                    throw new IllegalArgumentException(
                            "Territory " + id + " has a defense anchor with no id.");
                }
                if (!defenseIds.add(anchor.id())) {
                    throw new IllegalArgumentException(
                            "Territory " + id +
                                    " has duplicate defense anchor id " + anchor.id());
                }
                if (anchor.rotation() % 90 != 0) {
                    throw new IllegalArgumentException(
                            "Territory " + id + " defense anchor " + anchor.id() +
                                    " rotation must be 0/90/180/270.");
                }
            }
        }

        for (TerritoryDefinition territory : result.values()) {
            for (String neighbor : territory.neighbors()) {
                TerritoryDefinition other = result.get(neighbor);
                if (other == null) {
                    throw new IllegalArgumentException(
                            "Territory " + territory.id() +
                                    " references missing neighbor " + neighbor);
                }
                if (!other.neighbors().contains(territory.id())) {
                    warnings.add("Adjacency is one-way: " + territory.id() +
                            " -> " + neighbor + ". Add the reverse link.");
                }
            }
        }
        return result;
    }

    private static Map<String, MonumentDefinition> validateMonuments(
            CatanMapDefinition definition,
            List<String> warnings
    ) {
        Map<String, MonumentDefinition> result = new LinkedHashMap<>();
        for (MonumentDefinition monument : definition.monuments()) {
            String id = monument.id();
            if (id.isBlank()) {
                throw new IllegalArgumentException("Monument id cannot be blank.");
            }
            monument.type();
            if (result.putIfAbsent(id, monument) != null) {
                throw new IllegalArgumentException("Duplicate monument id: " + id);
            }
        }
        if (result.size() > 3) {
            warnings.add("More than 3 monuments are defined; current gameplay is designed around 3.");
        }
        return result;
    }

    private static boolean isRawResource(ResourceType type) {
        return switch (type) {
            case WOOD, STONE, AGRICULTURE, IRON, COAL, OIL, COPPER -> true;
            default -> false;
        };
    }
}
