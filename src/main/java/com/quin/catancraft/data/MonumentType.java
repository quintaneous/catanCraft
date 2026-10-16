package com.quin.catancraft.data;

import java.util.Locale;
import java.util.Map;

public enum MonumentType {
    INDUSTRIAL_COMPLEX("Industrial Complex", 1000, Map.of(
            ResourceType.STEEL, 40L,
            ResourceType.CONCRETE, 40L,
            ResourceType.MECHANICAL_PARTS, 15L)),
    MILITARY_DEPOT("Military Depot", 1500, Map.of(
            ResourceType.STEEL, 25L,
            ResourceType.FABRIC, 30L,
            ResourceType.EXPLOSIVES, 20L)),
    REFINERY("Refinery", 1000, Map.of(
            ResourceType.OIL, 40L,
            ResourceType.FUEL, 60L,
            ResourceType.ELECTRONICS, 8L));

    private final String displayName;
    private final long moneyReward;
    private final Map<ResourceType, Long> resourceRewards;

    MonumentType(String displayName, long moneyReward, Map<ResourceType, Long> resourceRewards) {
        this.displayName = displayName;
        this.moneyReward = moneyReward;
        this.resourceRewards = Map.copyOf(resourceRewards);
    }

    public String displayName() { return displayName; }
    public long moneyReward() { return moneyReward; }
    public Map<ResourceType, Long> resourceRewards() { return resourceRewards; }
    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static MonumentType parse(String value) {
        return MonumentType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
