package com.quin.catancraft.economy;

import com.quin.catancraft.data.BuildingType;
import com.quin.catancraft.data.ResourceType;

import java.util.Map;

public final class EconomyCatalog {
    private EconomyCatalog() {}

    public static EconomyCost neutralTerritoryClaimCost() {
        return new EconomyCost(4_000, Map.of(
                ResourceType.WOOD, 60L,
                ResourceType.STONE, 40L,
                ResourceType.AGRICULTURE, 40L
        ));
    }

    public static EconomyCost buildingCost(BuildingType type) {
        EconomyCost base = new EconomyCost(3_000, Map.of(
                ResourceType.WOOD, 50L,
                ResourceType.STONE, 50L
        ));
        return base.scaled(type.costPercent());
    }

    public static EconomyCost buildingUpgradeCost(BuildingType type, int currentLevel) {
        EconomyCost base = switch (currentLevel) {
            case 1 -> new EconomyCost(5_000, Map.of(
                    ResourceType.CONCRETE, 60L,
                    ResourceType.STEEL, 20L
            ));
            case 2 -> new EconomyCost(12_000, Map.of(
                    ResourceType.CONCRETE, 120L,
                    ResourceType.STEEL, 60L,
                    ResourceType.MECHANICAL_PARTS, 15L
            ));
            case 3 -> new EconomyCost(25_000, Map.of(
                    ResourceType.CONCRETE, 220L,
                    ResourceType.STEEL, 130L,
                    ResourceType.MECHANICAL_PARTS, 35L,
                    ResourceType.ELECTRONICS, 10L
            ));
            case 4 -> new EconomyCost(55_000, Map.of(
                    ResourceType.CONCRETE, 400L,
                    ResourceType.STEEL, 260L,
                    ResourceType.MECHANICAL_PARTS, 80L,
                    ResourceType.ELECTRONICS, 30L
            ));
            default -> null;
        };
        return base == null ? null : base.scaled(type.costPercent());
    }

    public static EconomyCost cityUpgradeCost(int currentLevel) {
        return switch (currentLevel) {
            case 1 -> new EconomyCost(6_000, Map.of(
                    ResourceType.WOOD, 80L,
                    ResourceType.STONE, 80L
            ));
            case 2 -> new EconomyCost(18_000, Map.of(
                    ResourceType.CONCRETE, 160L,
                    ResourceType.STEEL, 100L
            ));
            case 3 -> new EconomyCost(45_000, Map.of(
                    ResourceType.CONCRETE, 300L,
                    ResourceType.STEEL, 200L,
                    ResourceType.MECHANICAL_PARTS, 40L
            ));
            case 4 -> new EconomyCost(100_000, Map.of(
                    ResourceType.CONCRETE, 600L,
                    ResourceType.STEEL, 400L,
                    ResourceType.MECHANICAL_PARTS, 100L,
                    ResourceType.ELECTRONICS, 40L
            ));
            default -> null;
        };
    }

    public static EconomyCost producerUpgradeCost(int currentLevel) {
        return switch (currentLevel) {
            case 1 -> new EconomyCost(2_000, Map.of(
                    ResourceType.WOOD, 30L,
                    ResourceType.STONE, 30L
            ));
            case 2 -> new EconomyCost(5_000, Map.of(
                    ResourceType.CONCRETE, 40L,
                    ResourceType.STEEL, 10L
            ));
            case 3 -> new EconomyCost(12_000, Map.of(
                    ResourceType.CONCRETE, 80L,
                    ResourceType.STEEL, 30L,
                    ResourceType.MECHANICAL_PARTS, 5L
            ));
            case 4 -> new EconomyCost(25_000, Map.of(
                    ResourceType.CONCRETE, 150L,
                    ResourceType.STEEL, 60L,
                    ResourceType.MECHANICAL_PARTS, 15L
            ));
            default -> null;
        };
    }

    public static int maxBuildingSlots(int cityLevel) {
        return switch (Math.max(1, Math.min(5, cityLevel))) {
            case 1 -> 2;
            case 2 -> 4;
            case 3 -> 6;
            case 4 -> 8;
            default -> 10;
        };
    }
}
