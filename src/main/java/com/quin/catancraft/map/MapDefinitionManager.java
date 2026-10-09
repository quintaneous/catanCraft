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
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    private static final String DEFAULT_MAP_RESOURCE =
            "/data/catancraft/maps/season1_river_bridges_v3.json";

    private static CatanMapDefinition current = new CatanMapDefinition();
    private static Map<String, TerritoryDefinition> territories = Map.of();
    private static Map<String, MonumentDefinition> monuments = Map.of();
    private static Map<String, MapZoneDefinition> publicZones = Map.of();
    private static Map<String, InfrastructureDefinition> infrastructure = Map.of();

    private MapDefinitionManager() {}

    public static Path path() { return MAP_PATH; }
    public static String mapId() { return current.mapId(); }
    public static Collection<TerritoryDefinition> territories() { return territories.values(); }
    public static Collection<MonumentDefinition> monuments() { return monuments.values(); }
    public static Collection<MapZoneDefinition> publicZones() { return publicZones.values(); }
    public static Collection<InfrastructureDefinition> infrastructure() { return infrastructure.values(); }

    @Nullable
    public static TerritoryDefinition territory(String id) {
        return id == null ? null : territories.get(id.toLowerCase());
    }

    @Nullable
    public static MonumentDefinition monument(String id) {
        return id == null ? null : monuments.get(id.toLowerCase());
    }

    @Nullable
    public static MapZoneDefinition publicZone(String id) {
        return id == null ? null : publicZones.get(id.toLowerCase());
    }

    @Nullable
    public static TerritoryDefinition territoryAt(ServerLevel level, BlockPos pos) {
        String dimension = level.dimension().location().toString();
        for (TerritoryDefinition definition : territories.values()) {
            if (definition.contains(dimension, pos.getX(), pos.getZ())) return definition;
        }
        return null;
    }

    @Nullable
    public static MapZoneDefinition publicZoneAt(ServerLevel level, BlockPos pos) {
        String dimension = level.dimension().location().toString();
        for (MapZoneDefinition zone : publicZones.values()) {
            if (zone.contains(dimension, pos.getX(), pos.getZ())) return zone;
        }
        return null;
    }

    public static LoadResult installBundledSeasonOne(
            MinecraftServer server
    ) throws IOException {
        Files.createDirectories(MAP_PATH.getParent());
        try (InputStream input =
                     MapDefinitionManager.class.getResourceAsStream(DEFAULT_MAP_RESOURCE)) {
            if (input == null) {
                throw new IOException(
                        "Bundled default map is missing: " + DEFAULT_MAP_RESOURCE);
            }
            Files.copy(input, MAP_PATH, StandardCopyOption.REPLACE_EXISTING);
        }
        return reload(server);
    }

    public static LoadResult reload(MinecraftServer server) throws IOException {
        Files.createDirectories(MAP_PATH.getParent());
        installDefaultMapIfNeeded();

        CatanMapDefinition loaded;
        try (Reader reader = Files.newBufferedReader(MAP_PATH)) {
            loaded = GSON.fromJson(reader, CatanMapDefinition.class);
        }
        if (loaded == null) loaded = new CatanMapDefinition();

        List<String> warnings = new ArrayList<>();
        Map<String, TerritoryDefinition> territoryMap = validateTerritories(loaded, warnings);
        Map<String, MonumentDefinition> monumentMap = validateMonuments(loaded, warnings);
        Map<String, MapZoneDefinition> zoneMap = validateZones(loaded);
        Map<String, InfrastructureDefinition> infrastructureMap =
                validateInfrastructure(loaded);

        current = loaded;
        territories = Map.copyOf(territoryMap);
        monuments = Map.copyOf(monumentMap);
        publicZones = Map.copyOf(zoneMap);
        infrastructure = Map.copyOf(infrastructureMap);

        CatanSavedData data = CatanSavedData.get(server);
        data.syncMapDefinitions();
        data.setDirty();

        CatanCraft.LOGGER.info(
                "Loaded CatanCraft map {}: {} territories, {} monuments, {} public zones, {} infrastructure points from {}",
                loaded.mapId(),
                territories.size(),
                monuments.size(),
                publicZones.size(),
                infrastructure.size(),
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

    private static void installDefaultMapIfNeeded() throws IOException {
        boolean install = !Files.exists(MAP_PATH);

        if (!install) {
            try (Reader reader = Files.newBufferedReader(MAP_PATH)) {
                CatanMapDefinition existing = GSON.fromJson(reader, CatanMapDefinition.class);
                install = existing == null
                        || (existing.mapId().isBlank()
                        && existing.territories().isEmpty()
                        && existing.monuments().isEmpty());
            } catch (RuntimeException ex) {
                install = false;
            }
        }

        if (!install) return;

        try (InputStream input =
                     MapDefinitionManager.class.getResourceAsStream(DEFAULT_MAP_RESOURCE)) {
            if (input == null) {
                throw new IOException("Bundled default map is missing: " + DEFAULT_MAP_RESOURCE);
            }
            Files.copy(input, MAP_PATH, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Map<String, TerritoryDefinition> validateTerritories(
            CatanMapDefinition definition,
            List<String> warnings
    ) {
        if (definition.format() != 1 && definition.format() != 2) {
            throw new IllegalArgumentException(
                    "Unsupported CatanCraft map format " +
                            definition.format() + "; expected 1 or 2.");
        }

        Map<String, TerritoryDefinition> result = new LinkedHashMap<>();
        Set<Integer> startSlots = new HashSet<>();

        for (TerritoryDefinition territory : definition.territories()) {
            String id = territory.id();
            if (id.isBlank()) throw new IllegalArgumentException("Territory id cannot be blank.");
            if (result.putIfAbsent(id, territory) != null) {
                throw new IllegalArgumentException("Duplicate territory id: " + id);
            }

            for (ResourceType resource : territory.resources()) {
                if (!isRawResource(resource)) {
                    throw new IllegalArgumentException(
                            "Territory " + id + " uses processed resource " +
                                    resource.id() + "; territory resources must be raw resources.");
                }
            }

            if (territory.startSlot() > 0 && !startSlots.add(territory.startSlot())) {
                throw new IllegalArgumentException("Duplicate startSlot " + territory.startSlot() + ".");
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
            if (territory.startsWithSettlement() && territory.settlementAnchor() == null) {
                throw new IllegalArgumentException(
                        "Territory " + id +
                                " starts with a settlement but has no settlementAnchor.");
            }
            if (territory.startSlot() > 0 && !territory.startsWithSettlement()) {
                warnings.add("Territory " + id +
                        " is a start slot but startsWithSettlement=false.");
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
                if (territory.restorationRegion() == null
                        || !territory.restorationRegion().contains(territory.siegeRegion())) {
                    throw new IllegalArgumentException(
                            "Territory " + id +
                                    " siegeRegion must be fully inside its restorationRegion.");
                }
            }

            Set<String> plotIds = new HashSet<>();
            for (MapAnchor plot : territory.buildingPlots()) {
                validateAnchor(id, "building plot", plot, plotIds);
            }

            Set<String> defenseIds = new HashSet<>();
            for (MapAnchor anchor : territory.defenseAnchors()) {
                validateAnchor(id, "defense anchor", anchor, defenseIds);
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

    private static void validateAnchor(
            String territoryId,
            String label,
            MapAnchor anchor,
            Set<String> ids
    ) {
        if (anchor.id().isBlank()) {
            throw new IllegalArgumentException(
                    "Territory " + territoryId + " has a " + label + " with no id.");
        }
        if (!ids.add(anchor.id())) {
            throw new IllegalArgumentException(
                    "Territory " + territoryId + " has duplicate " +
                            label + " id " + anchor.id());
        }
        if (anchor.rotation() % 90 != 0) {
            throw new IllegalArgumentException(
                    "Territory " + territoryId + " " + label + " " +
                            anchor.id() + " rotation must be 0/90/180/270.");
        }
    }

    private static Map<String, MonumentDefinition> validateMonuments(
            CatanMapDefinition definition,
            List<String> warnings
    ) {
        Map<String, MonumentDefinition> result = new LinkedHashMap<>();
        for (MonumentDefinition monument : definition.monuments()) {
            String id = monument.id();
            if (id.isBlank()) throw new IllegalArgumentException("Monument id cannot be blank.");
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

    private static Map<String, MapZoneDefinition> validateZones(
            CatanMapDefinition definition
    ) {
        Map<String, MapZoneDefinition> result = new LinkedHashMap<>();
        for (MapZoneDefinition zone : definition.publicZones()) {
            if (zone.id().isBlank()) throw new IllegalArgumentException("Public zone id cannot be blank.");
            if (zone.type().isBlank()) {
                throw new IllegalArgumentException("Public zone " + zone.id() + " must define a type.");
            }
            if (zone.boundaryPoints().size() < 3) {
                throw new IllegalArgumentException(
                        "Public zone " + zone.id() + " must have at least 3 boundary points.");
            }
            if (result.putIfAbsent(zone.id(), zone) != null) {
                throw new IllegalArgumentException("Duplicate public zone id: " + zone.id());
            }
        }
        return result;
    }

    private static Map<String, InfrastructureDefinition> validateInfrastructure(
            CatanMapDefinition definition
    ) {
        Map<String, InfrastructureDefinition> result = new LinkedHashMap<>();
        for (InfrastructureDefinition point : definition.infrastructure()) {
            if (point.id().isBlank()) throw new IllegalArgumentException("Infrastructure id cannot be blank.");
            if (point.type().isBlank()) {
                throw new IllegalArgumentException(
                        "Infrastructure " + point.id() + " must define a type.");
            }
            if (result.putIfAbsent(point.id(), point) != null) {
                throw new IllegalArgumentException("Duplicate infrastructure id: " + point.id());
            }
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
