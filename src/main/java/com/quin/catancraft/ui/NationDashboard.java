package com.quin.catancraft.ui;

import com.quin.catancraft.data.BuildingInstance;
import com.quin.catancraft.data.BuildingType;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.data.NationData;
import com.quin.catancraft.data.MonumentData;
import com.quin.catancraft.data.ResourceType;
import com.quin.catancraft.data.TerritoryData;
import com.quin.catancraft.data.TradeProposal;
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

        lines.add("H|TRADE");
        List<TradeProposal> incomingTrades = data.tradeProposals().stream()
                .filter(t -> nation.id().equals(t.recipientNationId()))
                .sorted(Comparator.comparingLong(TradeProposal::createdGameTime))
                .toList();
        List<TradeProposal> outgoingTrades = data.tradeProposals().stream()
                .filter(t -> nation.id().equals(t.senderNationId()))
                .sorted(Comparator.comparingLong(TradeProposal::createdGameTime))
                .toList();

        if (incomingTrades.isEmpty() && outgoingTrades.isEmpty()) {
            lines.add("D|No pending trade proposals.");
        }

        for (TradeProposal proposal : incomingTrades) {
            NationData sender = data.nation(proposal.senderNationId());
            String senderName = sender == null ? "Unknown Nation" : sender.name();
            lines.add("Y|INCOMING #" + proposal.id() + " • " + senderName);
            lines.add("G|  They offer: " + tradeAssetText(
                    proposal.offeredAsset(), proposal.offeredAmount()));
            lines.add("D|  They request: " + tradeAssetText(
                    proposal.requestedAsset(), proposal.requestedAmount()));
            if (leader) {
                lines.add(action(
                        "nation trade accept " + proposal.id(),
                        "Accept trade #" + proposal.id()
                ));
                lines.add(action(
                        "nation trade decline " + proposal.id(),
                        "Decline trade #" + proposal.id()
                ));
            }
        }

        for (TradeProposal proposal : outgoingTrades) {
            NationData recipient = data.nation(proposal.recipientNationId());
            String recipientName = recipient == null ? "Unknown Nation" : recipient.name();
            lines.add("B|OUTGOING #" + proposal.id() + " • " + recipientName);
            lines.add("D|  Escrowed: " + tradeAssetText(
                    proposal.offeredAsset(), proposal.offeredAmount()));
            lines.add("D|  Requested: " + tradeAssetText(
                    proposal.requestedAsset(), proposal.requestedAmount()));
            if (leader) {
                lines.add(action(
                        "nation trade cancel " + proposal.id(),
                        "Cancel trade #" + proposal.id()
                ));
            }
        }

        if (leader) {
            lines.add("D|Propose: /nation trade propose \"Nation\" <offer> <amount> <want> <amount>");
            lines.add("D|Assets: money or any resource id (steel, oil, electronics, etc.)");
        }
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
                    " Y=" + activeMonument.y() +
                    " Z=" + activeMonument.z() +
                    " • Radius " + activeMonument.radius() +
                    " • Vertical ±12");
            lines.add("D|Capture: " + percent + "% • " + holder);
        }
        lines.add("");

        lines.add("H|TERRITORIES");
        if (territories.isEmpty()) {
            lines.add("Y|No territory assigned yet.");
        } else {
            for (TerritoryData territory : territories) {
                lines.add("T|" + territory.id() + "|" +
                        territory.name() + " • " + pretty(territory.specialty()) +
                        " • City L" + territory.cityLevel() +
                        " • Producer L" + territory.producerLevel());
                addTerritory(lines, nation, territory, leader);
                lines.add("E|" + territory.id());
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
            NationData nation,
            TerritoryData territory,
            boolean leader
    ) {
        int perCycle = EconomyBalance.rawProductionPerCycle(territory.producerLevel());
        int perHour = perCycle * 4;

        lines.add("D|City L" + territory.cityLevel() +
                " • Producer L" + territory.producerLevel());
        lines.add("G|Producer: +" + perCycle + " " +
                pretty(territory.specialty()) + "/15m • +" + perHour + "/hr");

        if (territory.specialty() == ResourceType.AGRICULTURE) {
            lines.add("D|Producer upkeep: none");
        } else {
            int upkeepCycle = EconomyBalance.agricultureUpkeepPerCycle(
                    territory.producerLevel());
            lines.add("Y|Producer upkeep: -" + upkeepCycle +
                    " Agriculture/15m • -" + (upkeepCycle * 4) + "/hr");
        }

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
                if (!building.plotId().isBlank()) {
                    detail += " • " + building.plotId();
                }
                if (building.type().isProcessor()) {
                    detail += " • target " + building.targetStock() + " " +
                            pretty(building.type().output());
                }
                lines.add(detail);

                if (building.type().isProcessor()) {
                    BuildingType type = building.type();
                    int batches = type.batchesPerCycle(building.level());
                    int outputCycle = type.outputPerBatch() * batches;
                    int outputHour = outputCycle * 4;

                    lines.add("D|  Recipe: " + recipeText(type) +
                            " -> " + type.outputPerBatch() + " " +
                            pretty(type.output()) + "/batch");
                    lines.add("G|  Max output: +" + outputCycle + " " +
                            pretty(type.output()) + "/15m • +" +
                            outputHour + "/hr");
                    lines.add("Y|  Max inputs: " +
                            inputRateText(type, batches));

                    if (nation.resource(type.output()) >= building.targetStock()) {
                        lines.add("D|  Status: PAUSED - target stock reached");
                    } else if (!nation.canConsume(type.inputs(), 1)) {
                        lines.add("R|  Status: WAITING - missing inputs");
                    } else {
                        lines.add("G|  Status: READY TO PRODUCE");
                    }
                }

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

    private static String recipeText(BuildingType type) {
        StringBuilder result = new StringBuilder();
        type.inputs().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().id()))
                .forEach(entry -> {
                    if (!result.isEmpty()) result.append(" + ");
                    result.append(entry.getValue())
                            .append(" ")
                            .append(pretty(entry.getKey()));
                });
        return result.toString();
    }

    private static String inputRateText(BuildingType type, int batchesPerCycle) {
        StringBuilder result = new StringBuilder();
        type.inputs().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().id()))
                .forEach(entry -> {
                    if (!result.isEmpty()) result.append(" • ");
                    int perCycle = entry.getValue() * batchesPerCycle;
                    int perHour = perCycle * 4;
                    result.append("-")
                            .append(perCycle)
                            .append(" ")
                            .append(pretty(entry.getKey()))
                            .append("/15m • -")
                            .append(perHour)
                            .append("/hr");
                });
        return result.toString();
    }

    private static String action(String command, String label) {
        return "A|" + command + "|" + label;
    }

    private static void addResource(List<String> lines, NationData nation, ResourceType type) {
        lines.add("G|" + pretty(type) + ": " + nation.resource(type));
    }

    private static String tradeAssetText(String asset, long amount) {
        if ("money".equalsIgnoreCase(asset)) {
            return "$" + amount;
        }
        try {
            return amount + " " + pretty(ResourceType.parse(asset));
        } catch (IllegalArgumentException ex) {
            return amount + " " + asset;
        }
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
