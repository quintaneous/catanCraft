package com.quin.catancraft.map;

import com.quin.catancraft.world.SchematicAssetRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Display-only regions, resolved once per map load from the placement contracts. */
public final class PlaceLabel {
    private record Place(String dimension, String name, Predicate<BlockPos> contains) {}

    private final List<Place> places;

    public PlaceLabel(CatanMapDefinition map) {
        List<Place> regions = new ArrayList<>();
        // A named public district takes precedence if a city overlaps its boundary.
        for (MapZoneDefinition zone : map.publicZones()) {
            var boundary = zone.toBoundary();
            String dimension = zone.dimension();
            regions.add(new Place(dimension, zone.name(),
                    pos -> boundary.contains(dimension, pos.getX(), pos.getZ())));
        }
        var settlement = SchematicAssetRegistry.asset("starter_settlement");
        for (TerritoryDefinition city : map.territories()) {
            if (settlement == null || !settlement.template().equals(city.settlementTemplate())) continue;
            var anchor = SchematicAssetRegistry.territoryAnchor(settlement.id(), city);
            if (anchor == null) continue;
            var footprint = settlement.boundsAt(anchor);
            var bounds = BoundingBox.fromCorners(footprint.min(), footprint.max());
            regions.add(new Place(city.dimension(), city.name(), bounds::isInside));
        }
        places = List.copyOf(regions);
    }

    public String at(String dimension, BlockPos pos) {
        for (Place place : places) {
            if (place.dimension().equals(dimension) && place.contains().test(pos)) return place.name();
        }
        return "";
    }
}
