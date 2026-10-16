package com.quin.catancraft.world;

import com.quin.catancraft.data.ResourceType;
import com.quin.catancraft.economy.EconomyCost;
import java.io.IOException;
import java.util.Map;

/** Explicit provisional initial-purchase prices, approved for this combined release.
 * Short authored wall/end-cap/checkpoint-wing sockets use wall8; wall14/straight16 use wall16.
 * Separate connector sockets are wall purchases. Bundled footing/support cells cost no extra.
 * This catalog does not enable sockets: the immutable registry separately excludes gates/bunkers.
 */
public final class DefenseCostCatalog {
    private DefenseCostCatalog(){}
    public static EconomyCost cost(String module) throws IOException {
        return switch(module){
            case "r6_low_wall_8","r4a_wall_6","r4a_wall_7","r4a_wall_end_cap","r4a_checkpoint_wing" -> new EconomyCost(500,Map.of(ResourceType.WOOD,5L,ResourceType.STONE,15L));
            case "r6_low_wall_16","r4a_wall_straight_16","r4a_wall_14" -> new EconomyCost(1000,Map.of(ResourceType.WOOD,10L,ResourceType.STONE,30L));
            case "r4a_wall_corner" -> new EconomyCost(750,Map.of(ResourceType.WOOD,10L,ResourceType.STONE,20L));
            case "r4a_checkpoint" -> new EconomyCost(2000,Map.of(ResourceType.WOOD,20L,ResourceType.STONE,40L));
            case "r4a_guard_tower" -> new EconomyCost(3000,Map.of(ResourceType.WOOD,20L,ResourceType.STONE,50L,ResourceType.STEEL,10L));
            case "r6_compact_casemate" -> new EconomyCost(4000,Map.of(ResourceType.CONCRETE,40L,ResourceType.STEEL,15L));
            default -> throw new IOException("No approved price for unavailable defense module: "+module);
        };
    }
}
