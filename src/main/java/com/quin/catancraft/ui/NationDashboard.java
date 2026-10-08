package com.quin.catancraft.ui;

import com.quin.catancraft.data.BuildingInstance;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.data.NationData;
import com.quin.catancraft.data.ResourceType;
import com.quin.catancraft.data.TerritoryData;
import com.quin.catancraft.economy.EconomyBalance;
import com.quin.catancraft.economy.EconomyCatalog;
import com.quin.catancraft.economy.EconomyCost;
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

        List<TerritoryData> claimable = data.territories().stream()
                .filter(t -> t.ownerNationId() == null)
                .filter(t -> t.neighbors().stream().anyMatch(neighborId -> {
                    TerritoryData neighbor = data.territory(neighborId);
                    return neighbor != null && nation.id().equals(neighbor.ownerNationId());
                }))
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
                lines.add("D|  Base output: +" + perHour + "/hr • City L" + territory.cityLevel() +
                        " • Slots " + territory.buildings().size() + "/" +
                        EconomyCatalog.maxBuildingSlots(territory.cityLevel()));

                EconomyCost nextCity = EconomyCatalog.cityUpgradeCost(territory.cityLevel());
                if (nextCity != null) {
                    lines.add("D|  Next City: " + nextCity.describe());
                }

                EconomyCost nextProducer = EconomyCatalog.producerUpgradeCost(territory.producerLevel());
                if (nextProducer != null) {
                    lines.add("D|  Next Producer: " + nextProducer.describe());
                }

                for (int i = 0; i < territory.buildings().size(); i++) {
                    BuildingInstance building = territory.buildings().get(i);
                    String detail = "P|  [" + i + "] " + building.type().displayName() +
                            " L" + building.level();

                    if (building.type().isProcessor()) {
                        detail += " • target " + building.targetStock() + " " +
                                pretty(building.type().output());
                    }
                    lines.add(detail);

                    EconomyCost nextBuilding = EconomyCatalog.buildingUpgradeCost(
                            building.type(), building.level());
                    if (nextBuilding != null) {
                        lines.add("D|      Upgrade: " + nextBuilding.describe());
                    }
                }
            }
        }

        lines.add("");
        lines.add("H|AVAILABLE EXPANSION");
        EconomyCost claimCost = EconomyCatalog.neutralTerritoryClaimCost();
        if (claimable.isEmpty()) {
            lines.add("D|No adjacent neutral territories.");
        } else {
            lines.add("D|Claim cost: " + claimCost.describe());
            for (TerritoryData territory : claimable) {
                lines.add("Y|" + territory.name() + " • " + pretty(territory.specialty()) +
                        " • /nation claim " + territory.id());
            }
        }

        lines.add("");
        lines.add("H|LEADER COMMANDS");
        lines.add("D|/nation build <territory> <building>");
        lines.add("D|/nation upgrade city <territory>");
        lines.add("D|/nation upgrade producer <territory>");
        lines.add("D|/nation building upgrade <territory> <index>");
        lines.add("");
        lines.add("H|MEMBERSHIP");
        lines.add("D|/nation invite <player>  • leader only");
        lines.add("D|/nation leave  • members may leave");

        NationNetwork.openDashboard(player, lines);
        return true;
    }

    private static void addResource(List<String> lines, NationData nation, ResourceType type) {
        lines.add("G|" + pretty(type) + ": " + nation.resource(type));
    }

    private static String pretty(ResourceType type) {
        String[] pieces = type.id().split("_");
        StringBuilder result = new StringBuilder();
        for (String piece : pieces) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(piece.charAt(0))).append(piece.substring(1));
        }
        return result.toString();
    }
}
