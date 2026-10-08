package com.quin.catancraft.economy;

public final class EconomyBalance {
    private EconomyBalance() {}

    public static final int CYCLE_MINUTES = 15;
    public static final long CYCLE_TICKS = CYCLE_MINUTES * 60L * 20L;
    public static final long BASE_TERRITORY_INCOME_PER_CYCLE = 125L;

    public static int rawProductionPerCycle(int level) {
        return switch (clampLevel(level)) {
            case 1 -> 10;
            case 2 -> 18;
            case 3 -> 30;
            case 4 -> 45;
            default -> 65;
        };
    }

    public static int agricultureUpkeepPerCycle(int level) {
        return switch (clampLevel(level)) {
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 3;
            case 4 -> 5;
            default -> 8;
        };
    }

    private static int clampLevel(int level) {
        return Math.max(1, Math.min(5, level));
    }
}
