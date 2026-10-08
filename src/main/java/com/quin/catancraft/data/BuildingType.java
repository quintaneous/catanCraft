package com.quin.catancraft.data;

import java.util.Map;

public enum BuildingType {
    STEEL_MILL(ResourceType.STEEL, 8),
    CONCRETE_PLANT(ResourceType.CONCRETE, 8),
    TEXTILE_MILL(ResourceType.FABRIC, 8),
    REFINERY(ResourceType.FUEL, 8),
    MACHINE_SHOP(ResourceType.MECHANICAL_PARTS, 6),
    ELECTRONICS_FACTORY(ResourceType.ELECTRONICS, 6),
    CHEMICAL_PLANT(ResourceType.EXPLOSIVES, 6);

    private final ResourceType output;
    private final int outputPerBatch;

    BuildingType(ResourceType output, int outputPerBatch) {
        this.output = output;
        this.outputPerBatch = outputPerBatch;
    }

    public ResourceType output() {
        return output;
    }

    public int outputPerBatch() {
        return outputPerBatch;
    }

    public Map<ResourceType, Integer> inputs() {
        return switch (this) {
            case STEEL_MILL -> Map.of(ResourceType.IRON, 10, ResourceType.COAL, 5);
            case CONCRETE_PLANT -> Map.of(ResourceType.STONE, 10);
            case TEXTILE_MILL -> Map.of(ResourceType.AGRICULTURE, 10);
            case REFINERY -> Map.of(ResourceType.OIL, 10);
            case MACHINE_SHOP -> Map.of(ResourceType.STEEL, 8);
            case ELECTRONICS_FACTORY -> Map.of(ResourceType.COPPER, 8, ResourceType.MECHANICAL_PARTS, 2);
            case CHEMICAL_PLANT -> Map.of(ResourceType.OIL, 6, ResourceType.COAL, 4);
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
}
