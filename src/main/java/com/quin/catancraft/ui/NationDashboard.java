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
import com.quin.catancraft.economy.EconomyEngine;
import com.quin.catancraft.monument.MonumentManager;
import com.quin.catancraft.map.MapDefinitionManager;
import com.quin.catancraft.map.TerritoryDefinition;
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
        lines.add("D|Quick stock: W " + nation.resource(ResourceType.WOOD) +
                " • S " + nation.resource(ResourceType.STONE) +
                " • A " + nation.resource(ResourceType.AGRICULTURE) +
                " • Steel " + nation.resource(ResourceType.STEEL));
        lines.add("");

        long stockedTypes = java.util.Arrays.stream(ResourceType.values())
                .filter(type -> nation.resource(type) > 0)
                .count();
        lines.add("S|resources|RESOURCES • " + stockedTypes + " types stocked");
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
        lines.add("X|resources");
        lines.add("");

        if (territories.isEmpty()) {
            lines.add("H|CHOOSE STARTING CITY");
            List<TerritoryDefinition> starts = MapDefinitionManager.territories().stream()
                    .filter(definition -> definition.startSlot() > 0)
                    .sorted(Comparator.comparingInt(TerritoryDefinition::startSlot))
                    .toList();

            boolean anyAvailable = false;
            for (TerritoryDefinition definition : starts) {
                TerritoryData state = data.territory(definition.id());
                boolean available = state != null && state.ownerNationId() == null;
                String yieldNote = definition.resources().size() > 1
                        ? " • reduced starter yield"
                        : "";

                if (available) {
                    anyAvailable = true;
                    lines.add("Y|Slot " + definition.startSlot() + " • " +
                            definition.name() + " • " +
                            resourceListText(definition.resources()) + yieldNote);
                    if (leader) {
                        lines.add(action(
                                "nation start " + definition.startSlot(),
                                "Choose " + definition.name()
                        ));
                    }
                } else {
                    lines.add("D|Slot " + definition.startSlot() + " • " +
                            definition.name() + " • TAKEN");
                }
            }

            if (!anyAvailable) {
                lines.add("R|No starting cities are currently available.");
            }
            lines.add("");
        }

        List<TradeProposal> incomingTrades = data.tradeProposals().stream()
                .filter(t -> nation.id().equals(t.recipientNationId()))
                .sorted(Comparator.comparingLong(TradeProposal::createdGameTime))
                .toList();
        List<TradeProposal> outgoingTrades = data.tradeProposals().stream()
                .filter(t -> nation.id().equals(t.senderNationId()))
                .sorted(Comparator.comparingLong(TradeProposal::createdGameTime))
                .toList();

        lines.add("S|trade|TRADE • " +
                (incomingTrades.size() + outgoingTrades.size()) + " pending");
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
            lines.add("C|trade_create|Create Trade Proposal");
            lines.add("D|Offered assets are escrowed until accepted, declined, or canceled.");
        }
        lines.add("X|trade");
        lines.add("");

        MonumentData activeMonument = data.activeMonument();
        lines.add("S|monument|ACTIVE MONUMENT • " +
                (activeMonument == null ? "None" : activeMonument.name()));
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
        lines.add("X|monument");
        lines.add("");

        lines.add("S|territories|TERRITORIES • " + territories.size());
        if (territories.isEmpty()) {
            lines.add("Y|No territory assigned yet.");
        } else {
            for (TerritoryData territory : territories) {
                lines.add("T|" + territory.id() + "|" +
                        territory.name() + " • " + resourceListText(territory.rawResources()) +
                        " • City L" + territory.cityLevel() +
                        " • Producer L" + territory.producerLevel());
                addTerritory(lines, nation, territory, leader);
                lines.add("E|" + territory.id());
            }
        }
        lines.add("X|territories");

        lines.add("");
        lines.add("S|expansion|AVAILABLE EXPANSION • " + claimable.size());
        EconomyCost claimCost = EconomyCatalog.neutralTerritoryClaimCost();
        if (claimable.isEmpty()) {
            lines.add("D|No adjacent neutral territories.");
        } else {
            lines.add("D|Standard expansion cost: " + claimCost.describe());
            for (TerritoryData territory : claimable) {
                lines.add("Y|" + territory.name() + " • " + resourceListText(territory.rawResources()));
                if (leader) {
                    lines.add(action(
                            "nation claim " + territory.id(),
                            "Claim " + territory.name() + " • " + claimCost.describe()
                    ));
                }
            }
        }

        lines.add("X|expansion");

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
        for (ResourceType raw : territory.rawResources()) {
            int percent = territory.rawResourceYieldPercent(raw);
            int actualCycle = EconomyEngine.rawOutputPerCycle(territory, raw);
            String yield = percent == 100 ? "" : " • " + percent + "%";
            lines.add("G|" + pretty(raw) + " +" + actualCycle +
                    "/15m" + yield);
        }

        boolean hasNonAgriculture = territory.rawResources().stream()
                .anyMatch(type -> type != ResourceType.AGRICULTURE);
        if (hasNonAgriculture) {
            int upkeepCycle = EconomyBalance.agricultureUpkeepPerCycle(
                    territory.producerLevel());
            lines.add("Y|Upkeep -" + upkeepCycle + " Agriculture/15m");
        }

        int developmentCap = developmentSlotCap(territory);
        lines.add("D|Development " + territory.buildings().size() + "/" +
                developmentCap + " • City L" + territory.cityLevel() +
                " • Producer L" + territory.producerLevel());

        EconomyCost nextCity = EconomyCatalog.cityUpgradeCost(territory.cityLevel());
        if (nextCity != null) {
            if (leader) {
                lines.add(action(
                        "nation upgrade city " + territory.id(),
                        "City L" + territory.cityLevel() + " → L" +
                                (territory.cityLevel() + 1) +
                                " • " + nextCity.describe()
                ));
            } else {
                lines.add("D|Next city upgrade: " + nextCity.describe());
            }
        } else {
            lines.add("G|City max level");
        }

        EconomyCost nextProducer =
                EconomyCatalog.producerUpgradeCost(territory.producerLevel());
        if (nextProducer != null) {
            if (leader) {
                lines.add(action(
                        "nation upgrade producer " + territory.id(),
                        "Producer L" + territory.producerLevel() + " → L" +
                                (territory.producerLevel() + 1) +
                                " • " + nextProducer.describe()
                ));
            } else {
                lines.add("D|Next producer upgrade: " + nextProducer.describe());
            }
        } else {
            lines.add("G|Producer max level");
        }

        if (!territory.buildings().isEmpty()) {
            lines.add("P|BUILDINGS");
            for (int i = 0; i < territory.buildings().size(); i++) {
                BuildingInstance building = territory.buildings().get(i);
                BuildingType type = building.type();

                String detail = "[" + i + "] " + type.displayName() +
                        " L" + building.level();

                if (type.isProcessor()) {
                    EconomyEngine.ProcessorPreview preview =
                            EconomyEngine.processorPreview(nation, building);
                    detail += switch (preview.status()) {
                        case PAUSED_TARGET -> " • Paused " +
                                preview.currentOutput() + "/" +
                                preview.targetStock();
                        case WAITING_INPUTS -> " • Missing inputs";
                        case READY -> " • Ready • +" +
                                preview.outputPerCycle() + " " +
                                pretty(type.output()) + "/15m";
                    };
                }

                lines.add("P|" + detail);

                if (type.isProcessor()) {
                    lines.add("D|  " + recipeText(type) + " → " +
                            type.outputPerBatch() + " " +
                            pretty(type.output()) + "/batch" +
                            " • target " + building.targetStock());
                }

                EconomyCost nextBuilding =
                        EconomyCatalog.buildingUpgradeCost(type, building.level());
                if (nextBuilding != null
                        && building.level() < territory.cityLevel()) {
                    if (leader) {
                        lines.add(action(
                                "nation building upgrade " + territory.id() +
                                        " " + i,
                                "Upgrade " + type.displayName() + " → L" +
                                        (building.level() + 1) +
                                        " • " + nextBuilding.describe()
                        ));
                    }
                } else if (building.level() >= territory.cityLevel()
                        && building.level() < 5) {
                    lines.add("D|  City level limits this building");
                }

                if (leader && type.isProcessor()) {
                    long down = Math.max(0, building.targetStock() - 50);
                    long up = building.targetStock() + 50;
                    lines.add(action(
                            "nation building target " + territory.id() +
                                    " " + i + " " + down,
                            "Target -50"
                    ));
                    lines.add(action(
                            "nation building target " + territory.id() +
                                    " " + i + " " + up,
                            "Target +50"
                    ));
                }
            }
        }

        if (territory.buildings().size() < developmentCap) {
            lines.add("P|BUILD NEW");
            for (BuildingType type : BuildingType.values()) {
                if (type.minCityLevel() > territory.cityLevel()) continue;

                EconomyCost cost = EconomyCatalog.buildingCost(type);
                if (leader) {
                    lines.add(action(
                            "nation build " + territory.id() + " " + type.id(),
                            type.displayName() + " • " + cost.describe()
                    ));
                } else {
                    lines.add("D|" + type.displayName() + " • " + cost.describe());
                }
            }
        } else {
            lines.add("Y|No open development slots");
        }
    }

    private static int developmentSlotCap(TerritoryData territory) {
        int economyCap = EconomyCatalog.maxBuildingSlots(territory.cityLevel());
        TerritoryDefinition definition =
                MapDefinitionManager.territory(territory.id());
        if (definition == null) return economyCap;
        return Math.min(economyCap, definition.availableBuildingPlots().size());
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

    private static String resourceListText(List<ResourceType> resources) {
        StringBuilder result = new StringBuilder();
        for (ResourceType resource : resources) {
            if (!result.isEmpty()) result.append(" + ");
            result.append(pretty(resource));
        }
        return result.toString();
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
