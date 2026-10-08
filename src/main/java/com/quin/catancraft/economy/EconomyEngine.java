package com.quin.catancraft.economy;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.BuildingInstance;
import com.quin.catancraft.data.BuildingType;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.data.NationData;
import com.quin.catancraft.data.ResourceType;
import com.quin.catancraft.data.TerritoryData;
import net.minecraft.server.MinecraftServer;

import java.util.Map;

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

        for (TerritoryData territory : data.territories()) {
            if (territory.ownerNationId() == null) continue;
            NationData nation = data.nation(territory.ownerNationId());
            if (nation == null) continue;

            produceRawResource(nation, territory);
            processBuildings(nation, territory);
            nation.addTreasury(EconomyBalance.BASE_TERRITORY_INCOME_PER_CYCLE);
        }

        data.setDirty();
        CatanCraft.LOGGER.info("CatanCraft production cycle complete: {} nations, {} territories",
                data.nations().size(), data.territories().size());
    }

    private static void produceRawResource(NationData nation, TerritoryData territory) {
        ResourceType specialty = territory.specialty();
        int level = territory.producerLevel();
        int production = EconomyBalance.rawProductionPerCycle(level);

        if (specialty != ResourceType.AGRICULTURE) {
            int upkeep = EconomyBalance.agricultureUpkeepPerCycle(level);
            long available = nation.resource(ResourceType.AGRICULTURE);

            if (available < upkeep) {
                double supplyRatio = upkeep == 0 ? 1.0 : (double) available / upkeep;
                production = Math.max(0, (int) Math.floor(production * supplyRatio));
                nation.addResource(ResourceType.AGRICULTURE, -available);
            } else {
                nation.addResource(ResourceType.AGRICULTURE, -upkeep);
            }
        }

        nation.addResource(specialty, production);
    }

    private static void processBuildings(NationData nation, TerritoryData territory) {
        for (BuildingInstance building : territory.buildings()) {
            BuildingType type = building.type();
            ResourceType output = type.output();
            long current = nation.resource(output);

            if (current >= building.targetStock()) continue;

            int desiredBatches = type.batchesPerCycle(building.level());
            long missing = building.targetStock() - current;
            int batchesByTarget = (int) Math.max(1,
                    Math.ceil((double) missing / type.outputPerBatch()));
            int batches = Math.min(desiredBatches, batchesByTarget);

            while (batches > 0 && !nation.canConsume(type.inputs(), batches)) {
                batches--;
            }
            if (batches <= 0) continue;

            nation.consume(type.inputs(), batches);
            nation.addResource(output, (long) type.outputPerBatch() * batches);
        }
    }
}
