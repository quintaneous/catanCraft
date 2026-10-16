package com.quin.catancraft.data;

import java.util.Locale;
import java.util.Map;

public enum BuildingType {
    STEEL_MILL,
    CONCRETE_PLANT,
    TEXTILE_MILL,
    REFINERY,
    MACHINE_SHOP,
    ELECTRONICS_FACTORY,
    CHEMICAL_PLANT,
    WEAPONS_FACTORY,
    VEHICLE_FACTORY,
    COMMERCIAL_DISTRICT,
    LOGISTICS_CENTER;

    public boolean isProcessor() {
        return switch (this) {
            case STEEL_MILL, CONCRETE_PLANT, TEXTILE_MILL, REFINERY,
                    MACHINE_SHOP, ELECTRONICS_FACTORY, CHEMICAL_PLANT -> true;
            default -> false;
        };
    }

    public int processingTier() {
        return switch (this) {
            case STEEL_MILL, CONCRETE_PLANT, TEXTILE_MILL, REFINERY, CHEMICAL_PLANT -> 1;
            case MACHINE_SHOP -> 2;
            case ELECTRONICS_FACTORY -> 3;
            default -> 0;
        };
    }

    public ResourceType output() {
        return switch (this) {
            case STEEL_MILL -> ResourceType.STEEL;
            case CONCRETE_PLANT -> ResourceType.CONCRETE;
            case TEXTILE_MILL -> ResourceType.FABRIC;
            case REFINERY -> ResourceType.FUEL;
            case MACHINE_SHOP -> ResourceType.MECHANICAL_PARTS;
            case ELECTRONICS_FACTORY -> ResourceType.ELECTRONICS;
            case CHEMICAL_PLANT -> ResourceType.EXPLOSIVES;
            default -> null;
        };
    }

    public int outputPerBatch() {
        return switch (this) {
            case STEEL_MILL, CONCRETE_PLANT, TEXTILE_MILL, REFINERY -> 8;
            case MACHINE_SHOP, ELECTRONICS_FACTORY, CHEMICAL_PLANT -> 6;
            default -> 0;
        };
    }

    public Map<ResourceType, Integer> inputs() {
        return switch (this) {
            case STEEL_MILL -> Map.of(ResourceType.IRON, 10, ResourceType.COAL, 5);
            case CONCRETE_PLANT -> Map.of(ResourceType.STONE, 10);
            case TEXTILE_MILL -> Map.of(ResourceType.AGRICULTURE, 10);
            case REFINERY -> Map.of(ResourceType.OIL, 10);
            case MACHINE_SHOP -> Map.of(ResourceType.STEEL, 8);
            case ELECTRONICS_FACTORY -> Map.of(
                    ResourceType.COPPER, 8,
                    ResourceType.MECHANICAL_PARTS, 2);
            case CHEMICAL_PLANT -> Map.of(ResourceType.OIL, 6, ResourceType.COAL, 4);
            default -> Map.of();
        };
    }

    public int minCityLevel() {
        return switch (this) {
            case COMMERCIAL_DISTRICT -> 1;
            case STEEL_MILL, CONCRETE_PLANT, TEXTILE_MILL, REFINERY,
                    WEAPONS_FACTORY, VEHICLE_FACTORY, LOGISTICS_CENTER -> 2;
            case MACHINE_SHOP, ELECTRONICS_FACTORY, CHEMICAL_PLANT -> 3;
        };
    }

    public int costPercent() {
        return switch (this) {
            case WEAPONS_FACTORY -> 125;
            case VEHICLE_FACTORY -> 175;
            default -> 100;
        };
    }

    public int batchesPerCycle(int level) {
        return switch (Math.max(1, Math.min(5, level))) {
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 3;
            case 4 -> 5;
            default -> 8;
        };
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String displayName() {
        String[] pieces = id().split("_");
        StringBuilder result = new StringBuilder();
        for (String piece : pieces) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(piece.charAt(0))).append(piece.substring(1));
        }
        return result.toString();
    }

    public static BuildingType parse(String value) {
        return BuildingType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
