package com.quin.catancraft.economy;

import com.quin.catancraft.data.NationData;
import com.quin.catancraft.data.ResourceType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.StringJoiner;

public final class EconomyCost {
    private final long money;
    private final EnumMap<ResourceType, Long> resources;

    public EconomyCost(long money, Map<ResourceType, Long> resources) {
        this.money = Math.max(0, money);
        this.resources = new EnumMap<>(ResourceType.class);
        resources.forEach((type, amount) -> {
            if (amount != null && amount > 0) this.resources.put(type, amount);
        });
    }

    public long money() {
        return money;
    }

    public Map<ResourceType, Long> resources() {
        return Collections.unmodifiableMap(resources);
    }

    public boolean canAfford(NationData nation) {
        if (nation.treasury() < money) return false;
        for (Map.Entry<ResourceType, Long> entry : resources.entrySet()) {
            if (nation.resource(entry.getKey()) < entry.getValue()) return false;
        }
        return true;
    }

    public void charge(NationData nation) {
        if (!canAfford(nation)) {
            throw new IllegalStateException("Nation cannot afford cost");
        }
        nation.addTreasury(-money);
        resources.forEach((type, amount) -> nation.addResource(type, -amount));
    }

    public EconomyCost scaled(int percent) {
        int safePercent = Math.max(0, percent);
        EnumMap<ResourceType, Long> scaledResources = new EnumMap<>(ResourceType.class);
        resources.forEach((type, amount) ->
                scaledResources.put(type, Math.max(1, Math.round(amount * safePercent / 100.0))));
        return new EconomyCost(
                Math.max(0, Math.round(money * safePercent / 100.0)),
                scaledResources
        );
    }

    public String describe() {
        StringJoiner joiner = new StringJoiner(" + ");
        if (money > 0) joiner.add("$" + money);
        resources.forEach((type, amount) ->
                joiner.add(amount + " " + pretty(type)));
        String value = joiner.toString();
        return value.isEmpty() ? "Free" : value;
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
