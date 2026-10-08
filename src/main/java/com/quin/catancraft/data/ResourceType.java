package com.quin.catancraft.data;

import java.util.Locale;

public enum ResourceType {
    WOOD,
    STONE,
    AGRICULTURE,
    IRON,
    COAL,
    OIL,
    COPPER,
    STEEL,
    CONCRETE,
    FABRIC,
    FUEL,
    MECHANICAL_PARTS,
    ELECTRONICS,
    EXPLOSIVES;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ResourceType parse(String value) {
        return ResourceType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
