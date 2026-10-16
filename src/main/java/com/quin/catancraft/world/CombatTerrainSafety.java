package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Server-authoritative block damage prevention for optional combat mods.
 *
 * Ordinary Forge BreakEvent and ExplosionEvent hooks do not cover all direct
 * Level.destroyBlock() calls. TaCZ breaks panes via AmmoHitBlockEvent and
 * Superb Warfare destroys blocks in collision, projectile and explosion code.
 * Enforce their own server/common config gates instead of canceling bullet
 * hits (canceling AmmoHitBlockEvent could let bullets pass through walls).
 *
 * No TaCZ or Superb Warfare classes are linked at class-load time, so those
 * optional mods can still be absent without causing a startup crash.
 */
public final class CombatTerrainSafety {
    private static final String TACZ_AMMO = "com.tacz.guns.config.common.AmmoConfig";
    private static final String SBW_VEHICLE =
            "com.atsuishio.superbwarfare.config.server.VehicleConfig";
    private static final String SBW_PROJECTILE =
            "com.atsuishio.superbwarfare.config.server.ProjectileConfig";
    private static final String SBW_EXPLOSION =
            "com.atsuishio.superbwarfare.config.server.ExplosionConfig";

    private record Guard(String type, String field) {}

    private static final List<Guard> TACZ = List.of(
            new Guard(TACZ_AMMO, "DESTROY_GLASS"),
            new Guard(TACZ_AMMO, "IGNITE_BLOCK"),
            new Guard(TACZ_AMMO, "EXPLOSIVE_AMMO_DESTROYS_BLOCK")
    );
    private static final List<Guard> SBW = List.of(
            new Guard(SBW_VEHICLE, "COLLISION_DESTROY_SOFT_BLOCKS"),
            new Guard(SBW_VEHICLE, "COLLISION_DESTROY_NORMAL_BLOCKS"),
            new Guard(SBW_VEHICLE, "COLLISION_DESTROY_HARD_BLOCKS"),
            new Guard(SBW_VEHICLE, "COLLISION_DESTROY_BLOCKS_BEASTLY"),
            new Guard(SBW_PROJECTILE, "PROJECTILE_DESTROY_BLOCKS"),
            new Guard(SBW_EXPLOSION, "EXPLOSION_DESTROY"),
            new Guard(SBW_EXPLOSION, "EXTRA_EXPLOSION_EFFECT")
    );

    private CombatTerrainSafety() {}

    public static void enforce() {
        if (ModList.get().isLoaded("tacz")) {
            for (Guard entry : TACZ) disable(entry);
        }
        if (ModList.get().isLoaded("superbwarfare")) {
            for (Guard entry : SBW) disable(entry);
        }
    }

    private static void disable(Guard guard) {
        try {
            Class<?> configClass = Class.forName(guard.type());
            Field field = configClass.getField(guard.field());
            Object value = field.get(null);
            if (!(value instanceof ForgeConfigSpec.BooleanValue setting)) {
                CatanCraft.LOGGER.warn("CatanCraft terrain guard: {}.{} is not a Forge boolean config",
                        guard.type(), guard.field());
                return;
            }
            if (Boolean.TRUE.equals(setting.get())) {
                setting.set(Boolean.FALSE);
                CatanCraft.LOGGER.info(
                        "CatanCraft terrain protection disabled {}.{} block griefing",
                        guard.type(), guard.field());
            }
        } catch (Exception | LinkageError failure) {
            CatanCraft.LOGGER.warn(
                    "CatanCraft could not enforce {}.{}; check installed combat-mod version",
                    guard.type(), guard.field(), failure);
        }
    }
}
