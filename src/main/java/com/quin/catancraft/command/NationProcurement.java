package com.quin.catancraft.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.BuildingInstance;
import com.quin.catancraft.data.BuildingType;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.data.NationData;
import com.quin.catancraft.data.ResourceType;
import com.quin.catancraft.data.TerritoryData;
import com.quin.catancraft.economy.EconomyCost;
import com.quin.catancraft.map.MapDefinitionManager;
import com.quin.catancraft.map.TerritoryDefinition;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase 1 nation-funded TaCZ and Superb Warfare procurement.
 * External mod classes are accessed only when installed; base CatanCraft
 * remains loadable without either mod. No portable vehicle item is issued.
 */
public final class NationProcurement {
    public static final String PURCHASED_TAG = "CatanCraftPurchasedVehicle";
    public static final String NATION_TAG = "CatanCraftVehicleNation";
    private enum Kind { GUN, AMMO, VEHICLE }

    private record Offer(String name, Kind kind, String mod, String objectId,
                         int count, String fireMode, BuildingType facility,
                         EconomyCost cost) {}

    private static final Map<String, Offer> CATALOG = new LinkedHashMap<>();

    static {
        offer("pistol", "Glock 17", Kind.GUN, "tacz", "tacz:glock_17",
                1, "SEMI", null, new EconomyCost(350, Map.of(
                        ResourceType.WOOD, 5L, ResourceType.STONE, 5L)));
        offer("ak47", "AK-47", Kind.GUN, "tacz", "tacz:ak47",
                1, "AUTO", BuildingType.WEAPONS_FACTORY, new EconomyCost(1200, Map.of(
                        ResourceType.STEEL, 12L, ResourceType.WOOD, 10L)));
        offer("ammo9", "9mm (32 rounds)", Kind.AMMO, "tacz", "tacz:9mm",
                32, null, null, new EconomyCost(100, Map.of(
                        ResourceType.STONE, 5L)));
        offer("ammo762", "7.62x39 (32 rounds)", Kind.AMMO, "tacz", "tacz:762x39",
                32, null, BuildingType.WEAPONS_FACTORY, new EconomyCost(175, Map.of(
                        ResourceType.STEEL, 2L, ResourceType.EXPLOSIVES, 1L)));
        offer("pickup", "Unarmed Pickup", Kind.VEHICLE, "superbwarfare",
                "superbwarfare:sodayo_pick_up", 1, null,
                BuildingType.VEHICLE_FACTORY, new EconomyCost(4000, Map.of(
                        ResourceType.STEEL, 20L, ResourceType.MECHANICAL_PARTS, 10L,
                        ResourceType.FUEL, 8L)));
        offer("lav150", "LAV-150", Kind.VEHICLE, "superbwarfare",
                "superbwarfare:lav_150", 1, null,
                BuildingType.VEHICLE_FACTORY, new EconomyCost(8500, Map.of(
                        ResourceType.STEEL, 50L, ResourceType.MECHANICAL_PARTS, 25L,
                        ResourceType.FUEL, 20L, ResourceType.ELECTRONICS, 5L)));
    }

    private static void offer(String key, String name, Kind kind, String mod,
                              String objectId, int count, String fireMode,
                              BuildingType facility, EconomyCost cost) {
        CATALOG.put(key, new Offer(name, kind, mod, objectId, count, fireMode,
                facility, cost));
    }

    private NationProcurement() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("procure")
                .executes(ctx -> list(ctx.getSource()))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource())))
                .then(Commands.argument("item", StringArgumentType.word())
                        .executes(ctx -> buy(ctx.getSource(),
                                StringArgumentType.getString(ctx, "item"))));
    }

    private static int list(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "Nation procurement (leader only). Buy with /nation procure <id>:"), false);
        CATALOG.forEach((id, offer) -> source.sendSuccess(
                () -> Component.literal(id + " - " + offer.name() + ": " +
                        offer.cost().describe() + (offer.facility() == null ? ""
                        : " [requires " + offer.facility().displayName() + "]")), false));
        return 1;
    }

    private static int buy(CommandSourceStack source, String key)
            throws CommandSyntaxException {
        Offer offer = CATALOG.get(key.toLowerCase(java.util.Locale.ROOT));
        if (offer == null) {
            source.sendFailure(Component.literal("Unknown offer. Use /nation procure list."));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        CatanSavedData data = CatanSavedData.get(source.getServer());
        NationData nation = data.nationForPlayer(player.getUUID());
        if (nation == null || !nation.isLeader(player.getUUID())) {
            source.sendFailure(Component.literal(
                    "Only your nation's leader can spend the national procurement budget."));
            return 0;
        }
        if (!ModList.get().isLoaded(offer.mod())) {
            source.sendFailure(Component.literal("Required mod not installed: " +
                    offer.mod() + ". No funds charged."));
            return 0;
        }
        if (offer.facility() != null && !ownsFacility(data, nation, offer.facility())) {
            source.sendFailure(Component.literal("Your nation must own a " +
                    offer.facility().displayName() + " to purchase this item."));
            return 0;
        }
        if (!offer.cost().canAfford(nation)) {
            source.sendFailure(Component.literal("Cannot afford " + offer.name() +
                    ". Need: " + offer.cost().describe()));
            return 0;
        }
        if (offer.kind() != Kind.VEHICLE) {
            return buyTacz(source, player, data, nation, offer);
        }
        return buyVehicle(source, player, data, nation, offer);
    }

    private static boolean ownsFacility(CatanSavedData data, NationData nation,
                                        BuildingType wanted) {
        for (TerritoryData territory : data.territories()) {
            if (!nation.id().equals(territory.ownerNationId())) continue;
            for (BuildingInstance building : territory.buildings()) {
                if (building.type() == wanted) return true;
            }
        }
        return false;
    }

    private static int buyTacz(CommandSourceStack source, ServerPlayer player,
                               CatanSavedData data, NationData nation, Offer offer) {
        if (player.getInventory().getFreeSlot() < 0) {
            source.sendFailure(Component.literal(
                    "Keep at least one empty inventory slot for procurement."));
            return 0;
        }
        ItemStack stack;
        try {
            String builderName = offer.kind() == Kind.GUN
                    ? "com.tacz.guns.api.item.builder.GunItemBuilder"
                    : "com.tacz.guns.api.item.builder.AmmoItemBuilder";
            Class<?> builderClass = Class.forName(builderName);
            Object builder = builderClass.getMethod("create").invoke(null);
            builder = builderClass.getMethod("setId", ResourceLocation.class)
                    .invoke(builder, new ResourceLocation(offer.objectId()));
            if (offer.kind() == Kind.GUN) {
                @SuppressWarnings({"rawtypes", "unchecked"})
                Class<? extends Enum> modeClass =
                        (Class<? extends Enum>) Class.forName(
                                "com.tacz.guns.api.item.gun.FireMode");
                @SuppressWarnings({"rawtypes", "unchecked"})
                Enum<?> mode = Enum.valueOf((Class) modeClass, offer.fireMode());
                Method setMode = builderClass.getMethod("setFireMode", modeClass);
                builder = setMode.invoke(builder, mode);
            } else {
                builder = builderClass.getMethod("setCount", int.class)
                        .invoke(builder, offer.count());
            }
            stack = (ItemStack) builderClass.getMethod("build").invoke(builder);
            if (stack == null || stack.isEmpty()) {
                source.sendFailure(Component.literal(
                        "TaCZ did not recognize " + offer.objectId() +
                        "; check your gun pack. No funds charged."));
                return 0;
            }
        } catch (ReflectiveOperationException | IllegalArgumentException ex) {
            CatanCraft.LOGGER.error("TaCZ procurement integration failed", ex);
            source.sendFailure(Component.literal(
                    "TaCZ gun pack/API unavailable. No funds charged; see server log."));
            return 0;
        }

        if (!player.getInventory().add(stack)) {
            source.sendFailure(Component.literal(
                    "Not enough inventory space. No funds charged."));
            return 0;
        }
        offer.cost().charge(nation);
        data.setDirty();
        source.sendSuccess(() -> Component.literal(
                "Purchased " + offer.name() + " for " + offer.cost().describe() +
                ". Item delivered to your inventory."), false);
        return 1;
    }

    private static int buyVehicle(CommandSourceStack source, ServerPlayer player,
                                  CatanSavedData data, NationData nation, Offer offer) {
        ServerLevel level = player.serverLevel();
        TerritoryDefinition standing = MapDefinitionManager.territoryAt(
                level, player.blockPosition());
        TerritoryData owned = standing == null ? null : data.territory(standing.id());
        if (owned == null || !nation.id().equals(owned.ownerNationId())) {
            source.sendFailure(Component.literal(
                    "Vehicle procurement requires standing inside your nation's territory."));
            return 0;
        }
        EntityType<?> vehicleType = ForgeRegistries.ENTITY_TYPES.getValue(
                new ResourceLocation(offer.objectId()));
        if (vehicleType == null ||
                !offer.objectId().equals(String.valueOf(
                        ForgeRegistries.ENTITY_TYPES.getKey(vehicleType)))) {
            source.sendFailure(Component.literal(
                    "Superb Warfare vehicle type is missing. No funds charged."));
            return 0;
        }
        Entity entity = vehicleType.create(level);
        if (entity == null) {
            source.sendFailure(Component.literal(
                    "Cannot initialize vehicle entity. No funds charged."));
            return 0;
        }
        double x = player.getX() + player.getLookAngle().x * 8.0;
        double z = player.getZ() + player.getLookAngle().z * 8.0;
        int ix = (int) Math.floor(x);
        int iz = (int) Math.floor(z);
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ix, iz);
        if (Math.abs(surfaceY - player.getY()) > 4.0) {
            source.sendFailure(Component.literal(
                    "Stand on level ground near a clear deployment yard, not above a roof."));
            return 0;
        }
        entity.setPos(x, surfaceY + 0.15, z);
        entity.setYRot(player.getYRot());
        AABB bounds = entity.getBoundingBox();
        for (int xx : new int[] { (int) Math.floor(bounds.minX), (int) Math.floor(bounds.maxX) }) {
            for (int zz : new int[] { (int) Math.floor(bounds.minZ), (int) Math.floor(bounds.maxZ) }) {
                TerritoryDefinition corner = MapDefinitionManager.territoryAt(level,
                        new BlockPos(xx, surfaceY, zz));
                if (corner == null || !standing.id().equals(corner.id())) {
                    source.sendFailure(Component.literal(
                            "The entire vehicle must fit inside your territory."));
                    return 0;
                }
            }
        }
        if (!level.getFluidState(new BlockPos(ix, surfaceY - 1, iz)).isEmpty()
                || !level.noCollision(entity, bounds.inflate(0.35, 0.25, 0.35))
                || !level.getEntities(entity, bounds.inflate(0.4)).isEmpty()) {
            source.sendFailure(Component.literal(
                    "Deployment blocked by obstacles, other entities, or water. No funds charged."));
            return 0;
        }
        entity.getPersistentData().putBoolean(PURCHASED_TAG, true);
        entity.getPersistentData().putString(NATION_TAG, nation.id().toString());
        if (!level.addFreshEntity(entity)) {
            source.sendFailure(Component.literal(
                    "World refused vehicle spawn. No funds charged."));
            return 0;
        }
        offer.cost().charge(nation);
        data.setDirty();
        source.sendSuccess(() -> Component.literal(
                "Purchased and deployed " + offer.name() + " at " + ix +
                " " + surfaceY + " " + iz + " for " + offer.cost().describe() +
                ". This nation vehicle cannot be crowbar-retrieved."), false);
        return 1;
    }
}
