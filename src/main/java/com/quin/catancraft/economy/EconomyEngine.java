package com.quin.catancraft.economy;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.BuildingInstance;
import com.quin.catancraft.data.BuildingType;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.data.NationData;
import com.quin.catancraft.data.ResourceType;
import com.quin.catancraft.data.TerritoryData;
import net.minecraft.server.MinecraftServer;

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

        // Phase 1: agriculture is produced first so same-cycle upkeep never
        // depends on territory insertion order.
        for (TerritoryData territory : data.territories()) {
            NationData nation = owner(data, territory);
            if (nation == null) continue;
            if (territory.specialty() == ResourceType.AGRICULTURE) {
                nation.addResource(
                        ResourceType.AGRICULTURE,
                        EconomyBalance.rawProductionPerCycle(territory.producerLevel())
                );
            }
        }

        // Phase 2: all other raw territories draw upkeep from the now-current
        // national Agriculture stockpile.
        for (TerritoryData territory : data.territories()) {
            NationData nation = owner(data, territory);
            if (nation == null) continue;
            if (territory.specialty() != ResourceType.AGRICULTURE) {
                produceNonAgricultureResource(nation, territory);
            }
        }

        // Phase 3: processors run by dependency tier. This makes Steel happen
        // before Mechanical Parts, and Mechanical Parts before Electronics,
        // regardless of territory creation order.
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

    private static void produceNonAgricultureResource(
            NationData nation,
            TerritoryData territory
    ) {
        int level = territory.producerLevel();
        int production = EconomyBalance.rawProductionPerCycle(level);
        int upkeep = EconomyBalance.agricultureUpkeepPerCycle(level);
        long available = nation.resource(ResourceType.AGRICULTURE);

        if (available < upkeep) {
            double supplyRatio = upkeep == 0 ? 1.0 : (double) available / upkeep;
            production = Math.max(0, (int) Math.floor(production * supplyRatio));
            nation.addResource(ResourceType.AGRICULTURE, -available);
        } else {
            nation.addResource(ResourceType.AGRICULTURE, -upkeep);
        }

        nation.addResource(territory.specialty(), production);
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
