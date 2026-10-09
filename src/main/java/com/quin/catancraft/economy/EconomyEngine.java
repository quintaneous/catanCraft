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
    private static long lastProcessedCycle = Long.MIN_VALUE;

    private EconomyEngine() {}

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
                int base = EconomyBalance.rawProductionPerCycle(
                        territory.producerLevel());
                int agriculture = scaledProduction(
                        base,
                        territory.rawResourceYieldPercent(ResourceType.AGRICULTURE));
                nation.addResource(ResourceType.AGRICULTURE, agriculture);
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
        int production = EconomyBalance.rawProductionPerCycle(level);
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
            int outputBase = scaledProduction(
                    production,
                    territory.rawResourceYieldPercent(output));
            int adjustedProduction = Math.max(
                    0,
                    (int) Math.floor(outputBase * supplyRatio)
            );
            nation.addResource(output, adjustedProduction);
        }
    }

    private static int scaledProduction(int base, int percent) {
        return Math.max(0, (int) Math.floor(base * (percent / 100.0)));
    }

    private static void processTier(
            NationData nation,
            TerritoryData territory,
            int tier
    ) {
        for (BuildingInstance building : territory.buildings()) {
            BuildingType type = building.type();
            if (!type.isProcessor() || type.processingTier() != tier) continue;

            ResourceType output = type.output();
            long current = nation.resource(output);
            if (current >= building.targetStock()) continue;

            int desiredBatches = type.batchesPerCycle(building.level());
            long missing = building.targetStock() - current;
            int batchesByTarget = (int) Math.max(
                    1,
                    Math.ceil((double) missing / type.outputPerBatch())
            );
            int batches = Math.min(desiredBatches, batchesByTarget);

            while (batches > 0 && !nation.canConsume(type.inputs(), batches)) {
                batches--;
            }
            if (batches <= 0) continue;

            nation.consume(type.inputs(), batches);
            nation.addResource(output, (long) type.outputPerBatch() * batches);
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
