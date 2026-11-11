package com.quin.catancraft.economy;

import com.google.gson.JsonObject;
import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.*;
import com.quin.catancraft.map.MapDefinitionManager;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.*;
import java.util.Map;
import java.util.UUID;

/** Native reference vectors for the offline economy projection. No live SavedData or world writes. */
@GameTestHolder("catancraft_economy45")
@PrefixGameTestTemplate(false)
public final class EconomyProjection45Tests {
    private record Fixture(CatanSavedData data, NationData nation, TerritoryData territory) {}
    private static Fixture fixture(String id, ResourceType raw, int level) {
        CatanSavedData data = new CatanSavedData();
        NationData nation = data.createNation("projection45", UUID.randomUUID());
        nation.setTreasury(0);
        TerritoryData territory = data.createTerritory(id, "projection45", raw);
        territory.setOwnerNationId(nation.id()); territory.setProducerLevel(level);
        return new Fixture(data, nation, territory);
    }
    private static void cycle(Fixture f, int count) {
        if (!f.data.hasEconomySchedule())
            require(EconomyEngine.scheduledCycle(f.data, 0) == EconomyEngine.ScheduledResult.INITIALIZED, "Unexpected adoption");
        for (int i = 0; i < count; i++) {
            long next = f.data.lastScheduledEconomyCycle() + 1;
            require(EconomyEngine.scheduledCycle(f.data, next * EconomyBalance.CYCLE_TICKS) == EconomyEngine.ScheduledResult.POSTED, "Cycle did not post");
        }
    }
    private static void balances(Fixture f, long cash, Map<ResourceType, Long> expected) {
        require(f.nation.treasury() == cash, "Cash mismatch: " + f.nation.treasury());
        for (ResourceType type : ResourceType.values())
            require(f.nation.resource(type) == expected.getOrDefault(type, 0L), "Resource mismatch " + type + "=" + f.nation.resource(type));
    }
    private static void proof(String name, Fixture f) {
        JsonObject result = new JsonObject(); result.addProperty("case", name);
        result.addProperty("cash", f.nation.treasury()); result.addProperty("cycles", f.data.lastScheduledEconomyCycle());
        JsonObject stock = new JsonObject();
        for (ResourceType type : ResourceType.values()) stock.addProperty(type.id(), f.nation.resource(type));
        result.add("stock", stock);
        CatanCraft.LOGGER.info("ECONOMY45_NATIVE_PASS {}", result);
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static BuildingInstance building(BuildingType type, int level, long target) {
        return new BuildingInstance(type, level, target);
    }

    @GameTest(template="townhall_state_empty")
    public static void starter_two_hours_uses_real_map_yields_and_one_upkeep(GameTestHelper h) {
        require(MapDefinitionManager.territory("a") != null, "Actual starter map definition required");
        Fixture f = fixture("a", ResourceType.WOOD, 1);
        cycle(f, 8);
        balances(f, 1000, Map.of(ResourceType.WOOD, 40L, ResourceType.STONE, 40L, ResourceType.AGRICULTURE, 32L));
        proof("starter_8_cycles", f); h.succeed();
    }
    @GameTest(template="townhall_state_empty")
    public static void half_yield_rounds_up_before_food_upkeep(GameTestHelper h) {
        require(MapDefinitionManager.territory("a") != null, "Actual starter map definition required");
        Fixture f = fixture("a", ResourceType.WOOD, 4);
        cycle(f, 1);
        balances(f, 125, Map.of(ResourceType.WOOD, 23L, ResourceType.STONE, 23L, ResourceType.AGRICULTURE, 18L));
        proof("starter_l4_rounding", f); h.succeed();
    }
    @GameTest(template="townhall_state_empty")
    public static void food_shortage_scales_raw_output_but_does_not_cancel_cash(GameTestHelper h) {
        Fixture f = fixture("projection45_wood", ResourceType.WOOD, 4);
        require(MapDefinitionManager.territory(f.territory.id()) == null, "Synthetic fixture collided with map");
        f.nation.addResource(ResourceType.AGRICULTURE, 2);
        cycle(f, 1);
        balances(f, 125, Map.of(ResourceType.WOOD, 18L));
        proof("partial_upkeep", f); h.succeed();
    }
    @GameTest(template="townhall_state_empty")
    public static void processing_dependency_tiers_ignore_reverse_building_list_order(GameTestHelper h) {
        Fixture f = fixture("projection45_chain", ResourceType.AGRICULTURE, 1);
        f.nation.addResource(ResourceType.IRON, 10); f.nation.addResource(ResourceType.COAL, 5); f.nation.addResource(ResourceType.COPPER, 8);
        // Deliberately reverse dependency order: tier scheduling must produce inputs first.
        f.territory.buildings().add(building(BuildingType.ELECTRONICS_FACTORY, 1, 100));
        f.territory.buildings().add(building(BuildingType.MACHINE_SHOP, 1, 100));
        f.territory.buildings().add(building(BuildingType.STEEL_MILL, 1, 100));
        cycle(f, 1);
        balances(f, 125, Map.of(ResourceType.AGRICULTURE, 10L, ResourceType.MECHANICAL_PARTS, 4L, ResourceType.ELECTRONICS, 6L));
        proof("dependency_chain", f); h.succeed();
    }
    @GameTest(template="townhall_state_empty")
    public static void logistics_batch_can_overshoot_target_then_pause_without_consuming(GameTestHelper h) {
        Fixture f = fixture("projection45_target", ResourceType.AGRICULTURE, 1);
        f.nation.addResource(ResourceType.CONCRETE, 6); f.nation.addResource(ResourceType.STONE, 10);
        f.territory.buildings().add(building(BuildingType.CONCRETE_PLANT, 1, 7));
        f.territory.buildings().add(building(BuildingType.LOGISTICS_CENTER, 1, 0));
        f.territory.buildings().add(building(BuildingType.COMMERCIAL_DISTRICT, 2, 0));
        cycle(f, 1);
        balances(f, 350, Map.of(ResourceType.AGRICULTURE, 10L, ResourceType.CONCRETE, 15L));
        f.nation.addResource(ResourceType.STONE, 10); cycle(f, 1);
        balances(f, 700, Map.of(ResourceType.AGRICULTURE, 20L, ResourceType.CONCRETE, 15L, ResourceType.STONE, 10L));
        proof("logistics_target_pause", f); h.succeed();
    }
    @GameTest(template="townhall_state_empty")
    public static void unowned_producers_and_reload_do_not_add_projected_income(GameTestHelper h) {
        Fixture f = fixture("projection45_owned", ResourceType.AGRICULTURE, 1);
        TerritoryData unowned = f.data.createTerritory("projection45_unowned", "unowned", ResourceType.WOOD);
        unowned.setProducerLevel(5); unowned.buildings().add(building(BuildingType.COMMERCIAL_DISTRICT, 5, 0));
        cycle(f, 1); balances(f, 125, Map.of(ResourceType.AGRICULTURE, 10L));
        CatanSavedData loaded = CatanSavedData.load(f.data.save(new CompoundTag()));
        Fixture reloaded = new Fixture(loaded, loaded.nation(f.nation.id()), loaded.territory(f.territory.id()));
        require(EconomyEngine.scheduledCycle(loaded, EconomyBalance.CYCLE_TICKS + 1) == EconomyEngine.ScheduledResult.UNCHANGED, "Reload repeated current cycle");
        balances(reloaded, 125, Map.of(ResourceType.AGRICULTURE, 10L)); cycle(reloaded, 1);
        balances(reloaded, 250, Map.of(ResourceType.AGRICULTURE, 20L));
        proof("unowned_reload", reloaded); h.succeed();
    }
}
