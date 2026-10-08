package com.quin.catancraft.ui;

import com.quin.catancraft.data.BuildingInstance;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.data.NationData;
import com.quin.catancraft.data.ResourceType;
import com.quin.catancraft.data.TerritoryData;
import com.quin.catancraft.economy.EconomyBalance;
import com.quin.catancraft.network.NationNetwork;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class NationDashboard {
    private NationDashboard() {}

    public static boolean open(ServerPlayer player) {
        CatanSavedData data = CatanSavedData.get(player.server);
        NationData nation = data.nationForPlayer(player.getUUID());
        if (nation == null) return false;

        List<TerritoryData> territories = data.territories().stream()
                .filter(t -> nation.id().equals(t.ownerNationId()))
                .sorted(Comparator.comparing(TerritoryData::name))
                .toList();

        List<String> lines = new ArrayList<>();
        lines.add("H|" + nation.name());
        lines.add("G|Treasury: $" + nation.treasury());
        lines.add("B|Members: " + nation.members().size());
        lines.add("B|Territories: " + territories.size());
        lines.add("D|Role: " + (nation.isLeader(player.getUUID()) ? "Leader" : "Member"));
        lines.add("");

        lines.add("H|RAW RESOURCES");
        addResource(lines, nation, ResourceType.WOOD);
        addResource(lines, nation, ResourceType.STONE);
        addResource(lines, nation, ResourceType.AGRICULTURE);
        addResource(lines, nation, ResourceType.IRON);
        addResource(lines, nation, ResourceType.COAL);
        addResource(lines, nation, ResourceType.OIL);
        addResource(lines, nation, ResourceType.COPPER);
        lines.add("");

        lines.add("H|PROCESSED RESOURCES");
        addResource(lines, nation, ResourceType.STEEL);
        addResource(lines, nation, ResourceType.CONCRETE);
        addResource(lines, nation, ResourceType.FABRIC);
        addResource(lines, nation, ResourceType.FUEL);
        addResource(lines, nation, ResourceType.MECHANICAL_PARTS);
        addResource(lines, nation, ResourceType.ELECTRONICS);
        addResource(lines, nation, ResourceType.EXPLOSIVES);
        lines.add("");

        lines.add("H|TERRITORIES");
        if (territories.isEmpty()) {
            lines.add("Y|No territory assigned yet.");
        } else {
            for (TerritoryData territory : territories) {
                int perHour = EconomyBalance.rawProductionPerCycle(territory.producerLevel()) * 4;
                lines.add("B|" + territory.name() + " • " +
                        pretty(territory.specialty()) + " • Producer L" + territory.producerLevel());
                lines.add("D|  Base output: +" + perHour + "/hr • City L" + territory.cityLevel());

                for (BuildingInstance building : territory.buildings()) {
                    lines.add("P|  " + pretty(building.type().name()) +
                            " L" + building.level() +
                            " • target " + building.targetStock() + " " +
                            pretty(building.type().output()));
                }
            }
        }

        lines.add("");
        lines.add("H|QUICK COMMANDS");
        lines.add("D|/nation invite <player>  • invite a player");
        lines.add("D|/nation leave  • leave your nation");
        lines.add("D|/catan nation stockpile  • text stockpile view");

        NationNetwork.openDashboard(player, lines);
        return true;
    }

    private static void addResource(List<String> lines, NationData nation, ResourceType type) {
        lines.add("G|" + pretty(type) + ": " + nation.resource(type));
    }

    private static String pretty(ResourceType type) {
        return pretty(type.name());
    }

    private static String pretty(String value) {
        String[] pieces = value.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();
        for (String piece : pieces) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(piece.charAt(0))).append(piece.substring(1));
        }
        return result.toString();
    }
}
