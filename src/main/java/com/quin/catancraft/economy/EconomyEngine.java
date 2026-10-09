package com.quin.catancraft.economy;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.BuildingInstance;
import com.quin.catancraft.data.BuildingType;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.data.NationData;
import com.quin.catancraft.data.ResourceType;
import com.quin.catancraft.data.TerritoryData;
import net.minecraft.server.MinecraftServer;

import java.util.List;

public final class EconomyEngine {
    public enum ProcessorStatus {
        READY,
        WAITING_INPUTS,
        PAUSED_TARGET
    }

    public record ProcessorPreview(
            ProcessorStatus status,
            int desiredBatches,
            int runnableBatches,
            int outputPerCycle,
            long currentOutput,
            long targetStock
    ) {}

    private static long lastProcessedCycle = Long.MIN_VALUE;

    private EconomyEngine() {}

    public static int rawOutputPerCycle(
            TerritoryData territory,
            ResourceType resource
    ) {
        if (!territory.rawResources().contains(resource)) return 0;
        int base = EconomyBalance.rawProductionPerCycle(territory.producerLevel());
        return scaledProduction(
                base,
                territory.rawResourceYieldPercent(resource)
        );
    }

    public static int rawOutputPerHour(
            TerritoryData territory,
            ResourceType resource
    ) {
        return rawOutputPerCycle(territory, resource) * 4;
    }

    public static ProcessorPreview processorPreview(
            NationData nation,
            BuildingInstance building
    ) {
        BuildingType type = building.type();
        if (!type.isProcessor()) {
            throw new IllegalArgumentException(
                    type.displayName() + " is not a processor.");
        }

        ResourceType output = type.output();
        long current = nation.resource(output);
        int desiredBatches = type.batchesPerCycle(building.level());

        if (current >= building.targetStock()) {
            return new ProcessorPreview(
                    ProcessorStatus.PAUSED_TARGET,
                    desiredBatches,
                    0,
                    0,
                    current,
                    building.targetStock()
            );
        }

        long missing = building.targetStock() - current;
        int batchesByTarget = (int) Math.max(
                1,
                Math.ceil((double) missing / type.outputPerBatch())
        );
        int runnable = Math.min(desiredBatches, batchesByTarget);

        while (runnable > 0 && !nation.canConsume(type.inputs(), runnable)) {
            runnable--;
        }

        if (runnable <= 0) {
            return new ProcessorPreview(
                    ProcessorStatus.WAITING_INPUTS,
                    desiredBatches,
                    0,
                    0,
                    current,
                    building.targetStock()
            );
        }

        return new ProcessorPreview(
                ProcessorStatus.READY,
                desiredBatches,
                runnable,
                type.outputPerBatch() * runnable,
                current,
                building.targetStock()
        );
    }

    public static void tick(MinecraftServer server) {
        long gameTime = server.overworld().getGameTime();
        long cycle = gameTime / EconomyBalance.CYCLE_TICKS;
        if (cycle == lastProcessedCycle) return;
        lastProcessedCycle = cycle;

        if (gameTime < EconomyBalance.CYCLE_TICKS) return;
        runCycle(server);
    }

    public static void runCycle(MinecraftServer server) {
        CatanSavedData data = CatanSavedData.get(server);

        // Phase 1: every territory that contains Agriculture produces it first.
        // This keeps upkeep deterministic even for multi-resource starter regions.
        for (TerritoryData territory : data.territories()) {
            NationData nation = owner(data, territory);
            if (nation == null) continue;

            if (territory.rawResources().contains(ResourceType.AGRICULTURE)) {
                nation.addResource(
                        ResourceType.AGRICULTURE,
                        rawOutputPerCycle(territory, ResourceType.AGRICULTURE)
                );
            }
        }

        // Phase 2: a territory pays one Agriculture upkeep charge to operate all
        // of its non-food raw outputs. This supports the actual season-one map:
        // starts produce wood/stone/agriculture, F/K produce iron/oil, and G/J
        // produce coal/copper without double-charging a bundled territory.
        for (TerritoryData territory : data.territories()) {
            NationData nation = owner(data, territory);
            if (nation == null) continue;
            produceNonAgricultureResources(nation, territory);
        }

        // Phase 3: processors run by dependency tier.
        for (int tier = 1; tier <= 3; tier++) {
            for (TerritoryData territory : data.territories()) {
                NationData nation = owner(data, territory);
                if (nation == null) continue;
                processTier(nation, territory, tier);
            }
        }

        // Phase 4: territorial and commercial cash income.
        for (TerritoryData territory : data.territories()) {
            NationData nation = owner(data, territory);
            if (nation == null) continue;

            nation.addTreasury(EconomyBalance.BASE_TERRITORY_INCOME_PER_CYCLE);

            for (BuildingInstance building : territory.buildings()) {
                if (building.type() == BuildingType.COMMERCIAL_DISTRICT) {
                    nation.addTreasury(commercialIncomePerCycle(building.level()));
                }
            }
        }

        data.setDirty();
        CatanCraft.LOGGER.info(
                "CatanCraft production cycle complete: {} nations, {} territories",
                data.nations().size(),
                data.territories().size()
        );
    }

    private static NationData owner(CatanSavedData data, TerritoryData territory) {
        if (territory.ownerNationId() == null) return null;
        return data.nation(territory.ownerNationId());
    }

    private static void produceNonAgricultureResources(
            NationData nation,
            TerritoryData territory
    ) {
        List<ResourceType> outputs = territory.rawResources().stream()
                .filter(type -> type != ResourceType.AGRICULTURE)
                .toList();
        if (outputs.isEmpty()) return;

        int level = territory.producerLevel();
        int upkeep = EconomyBalance.agricultureUpkeepPerCycle(level);
        long available = nation.resource(ResourceType.AGRICULTURE);

        double supplyRatio = 1.0;
        if (available < upkeep) {
            supplyRatio = upkeep == 0 ? 1.0 : (double) available / upkeep;
            nation.addResource(ResourceType.AGRICULTURE, -available);
        } else {
            nation.addResource(ResourceType.AGRICULTURE, -upkeep);
        }

        for (ResourceType output : outputs) {
            int outputBase = rawOutputPerCycle(territory, output);
            int adjustedProduction = Math.max(
                    0,
                    (int) Math.floor(outputBase * supplyRatio)
            );
            nation.addResource(output, adjustedProduction);
        }
    }

    private static int scaledProduction(int base, int percent) {
        return Math.max(0, (int) Math.round(base * (percent / 100.0)));
    }

    private static void processTier(
            NationData nation,
            TerritoryData territory,
            int tier
    ) {
        for (BuildingInstance building : territory.buildings()) {
            BuildingType type = building.type();
            if (!type.isProcessor() || type.processingTier() != tier) continue;

            ProcessorPreview preview = processorPreview(nation, building);
            if (preview.status() != ProcessorStatus.READY
                    || preview.runnableBatches() <= 0) {
                continue;
            }

            int batches = preview.runnableBatches();
            nation.consume(type.inputs(), batches);
            nation.addResource(type.output(), preview.outputPerCycle());
        }
    }

    private static long commercialIncomePerCycle(int level) {
        return switch (Math.max(1, Math.min(5, level))) {
            case 1 -> 125L;
            case 2 -> 225L;
            case 3 -> 375L;
            case 4 -> 600L;
            default -> 900L;
        };
    }
}
