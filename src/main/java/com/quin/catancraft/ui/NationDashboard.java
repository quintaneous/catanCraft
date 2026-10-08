package com.quin.catancraft.ui;

import com.quin.catancraft.data.BuildingInstance;
import com.quin.catancraft.data.BuildingType;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.data.NationData;
import com.quin.catancraft.data.MonumentData;
import com.quin.catancraft.data.ResourceType;
import com.quin.catancraft.data.TerritoryData;
import com.quin.catancraft.economy.EconomyBalance;
import com.quin.catancraft.economy.EconomyCatalog;
import com.quin.catancraft.economy.EconomyCost;
import com.quin.catancraft.monument.MonumentManager;
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

        boolean leader = nation.isLeader(player.getUUID());

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
        lines.add("D|Role: " + (leader ? "Leader" : "Member"));
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

        lines.add("H|ACTIVE MONUMENT");
        MonumentData activeMonument = data.activeMonument();
        if (activeMonument == null) {
            lines.add("D|No monument is active right now.");
        } else {
            int percent = Math.min(100, (int) Math.floor(
                    100.0 * activeMonument.captureProgressTicks()
                            / MonumentManager.CAPTURE_TICKS));
            String holder = "Unclaimed";
            if (activeMonument.capturingNationId() != null) {
                NationData holdingNation = data.nation(activeMonument.capturingNationId());
                if (holdingNation != null) holder = holdingNation.name();
            }

            lines.add("Y|" + activeMonument.name() +
                    " • " + activeMonument.type().displayName());
            lines.add("D|X=" + activeMonument.x() +
                    " Z=" + activeMonument.z() +
                    " • Radius " + activeMonument.radius());
            lines.add("D|Capture: " + percent + "% • " + holder);
        }
        lines.add("");

        lines.add("H|TERRITORIES");
        if (territories.isEmpty()) {
            lines.add("Y|No territory assigned yet.");
        } else {
            for (TerritoryData territory : territories) {
                addTerritory(lines, territory, leader);
            }
        }

        lines.add("");
        lines.add("H|AVAILABLE EXPANSION");
        EconomyCost claimCost = EconomyCatalog.neutralTerritoryClaimCost();
        if (claimable.isEmpty()) {
            lines.add("D|No adjacent neutral territories.");
        } else {
            lines.add("D|Standard expansion cost: " + claimCost.describe());
            for (TerritoryData territory : claimable) {
                lines.add("Y|" + territory.name() + " • " + pretty(territory.specialty()));
                if (leader) {
                    lines.add(action(
                            "nation claim " + territory.id(),
                            "Claim " + territory.name() + " • " + claimCost.describe()
                    ));
                }
            }
        }

        if (!leader) {
            lines.add("");
            lines.add("D|Only the nation leader can spend national resources in V0.1.");
        }

        NationNetwork.openDashboard(player, lines);
        return true;
    }

    private static void addTerritory(
            List<String> lines,
            TerritoryData territory,
            boolean leader
    ) {
        int perHour = EconomyBalance.rawProductionPerCycle(territory.producerLevel()) * 4;

        lines.add("");
        lines.add("B|" + territory.name() + " • " +
                pretty(territory.specialty()) + " Territory");
        lines.add("D|City L" + territory.cityLevel() +
                " • Producer L" + territory.producerLevel() +
                " • Base output +" + perHour + "/hr");
        lines.add("D|Development slots: " + territory.buildings().size() + "/" +
                EconomyCatalog.maxBuildingSlots(territory.cityLevel()));

        EconomyCost nextCity = EconomyCatalog.cityUpgradeCost(territory.cityLevel());
        if (nextCity != null) {
            lines.add("D|Next City Level: " + nextCity.describe());
            if (leader) {
                lines.add(action(
                        "nation upgrade city " + territory.id(),
                        "Upgrade City → L" + (territory.cityLevel() + 1) +
                                " • " + nextCity.describe()
                ));
            }
        } else {
            lines.add("G|City is max level.");
        }

        EconomyCost nextProducer = EconomyCatalog.producerUpgradeCost(territory.producerLevel());
        if (nextProducer != null) {
            lines.add("D|Next Producer Level: " + nextProducer.describe());
            if (leader) {
                lines.add(action(
                        "nation upgrade producer " + territory.id(),
                        "Upgrade " + pretty(territory.specialty()) +
                                " Production → L" + (territory.producerLevel() + 1) +
                                " • " + nextProducer.describe()
                ));
            }
        } else {
            lines.add("G|Resource producer is max level.");
        }

        if (territory.buildings().isEmpty()) {
            lines.add("D|No functional city buildings yet.");
        } else {
            lines.add("P|Functional Buildings");
            for (int i = 0; i < territory.buildings().size(); i++) {
                BuildingInstance building = territory.buildings().get(i);

                String detail = "P|[" + i + "] " + building.type().displayName() +
                        " L" + building.level();
                if (building.type().isProcessor()) {
                    detail += " • target " + building.targetStock() + " " +
                            pretty(building.type().output());
                }
                lines.add(detail);

                EconomyCost nextBuilding = EconomyCatalog.buildingUpgradeCost(
                        building.type(), building.level());

                if (nextBuilding != null && building.level() < territory.cityLevel()) {
                    lines.add("D|  Upgrade: " + nextBuilding.describe());
                    if (leader) {
                        lines.add(action(
                                "nation building upgrade " + territory.id() + " " + i,
                                "Upgrade " + building.type().displayName() +
                                        " → L" + (building.level() + 1) +
                                        " • " + nextBuilding.describe()
                        ));
                    }
                } else if (building.level() >= territory.cityLevel() && building.level() < 5) {
                    lines.add("Y|  Upgrade city first to raise this building.");
                }

                if (leader && building.type().isProcessor()) {
                    long down = Math.max(0, building.targetStock() - 50);
                    long up = building.targetStock() + 50;
                    lines.add(action(
                            "nation building target " + territory.id() + " " + i + " " + down,
                            "Lower " + building.type().displayName() + " target to " + down
                    ));
                    lines.add(action(
                            "nation building target " + territory.id() + " " + i + " " + up,
                            "Raise " + building.type().displayName() + " target to " + up
                    ));
                }
            }
        }

        int slotCap = EconomyCatalog.maxBuildingSlots(territory.cityLevel());
        if (territory.buildings().size() < slotCap) {
            lines.add("P|Available Construction");
            for (BuildingType type : BuildingType.values()) {
                if (type.minCityLevel() > territory.cityLevel()) continue;

                EconomyCost cost = EconomyCatalog.buildingCost(type);
                String label = "Build " + type.displayName() + " • " + cost.describe();

                if (leader) {
                    lines.add(action(
                            "nation build " + territory.id() + " " + type.id(),
                            label
                    ));
                } else {
                    lines.add("D|" + label);
                }
            }
        } else {
            lines.add("Y|No open development slots.");
        }
    }

    private static String action(String command, String label) {
        return "A|" + command + "|" + label;
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
