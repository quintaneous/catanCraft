package com.quin.catancraft.ui;

import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.data.NationData;
import com.quin.catancraft.data.ResourceType;
import com.quin.catancraft.economy.EconomyEngine;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

public final class NationResourceRows {
    private static final EnumSet<ResourceType> RAW = EnumSet.of(ResourceType.WOOD, ResourceType.STONE,
            ResourceType.AGRICULTURE, ResourceType.IRON, ResourceType.COAL, ResourceType.OIL, ResourceType.COPPER);

    private NationResourceRows() {}

    public static List<String> section(CatanSavedData data, NationData nation) {
        Map<ResourceType, Long> changes = EconomyEngine.nextCycleResourceChanges(data, nation);
        long stocked = nation.stockpileView().values().stream().filter(amount -> amount > 0).count();
        List<String> rows = new ArrayList<>();
        rows.add("S|resources|RESOURCES • " + stocked + " types stocked");
        addGroup(rows, "RAW RESOURCES", RAW, nation, changes);
        addGroup(rows, "PROCESSED RESOURCES", EnumSet.complementOf(RAW), nation, changes);
        rows.add("X|resources");
        return rows;
    }

    private static void addGroup(List<String> rows, String heading, EnumSet<ResourceType> types,
                                 NationData nation, Map<ResourceType, Long> changes) {
        rows.add("H|" + heading);
        for (ResourceType type : types) {
            long change = changes.get(type);
            String signed = change > 0 ? "+" + change : Long.toString(change);
            rows.add("Q|" + NationDashboard.pretty(type) + "|" + nation.resource(type) + "|" + signed);
        }
    }
}
