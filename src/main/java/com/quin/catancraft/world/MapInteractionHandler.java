package com.quin.catancraft.world;

import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.data.NationData;
import com.quin.catancraft.data.TerritoryData;
import com.quin.catancraft.map.MapAnchor;
import com.quin.catancraft.map.MapDefinitionManager;
import com.quin.catancraft.map.TerritoryDefinition;
import com.quin.catancraft.ui.NationDashboard;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class MapInteractionHandler {
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        BlockPos pos = event.getPos();
        if (!event.getLevel().getBlockState(pos).is(Blocks.LECTERN)) return;

        TerritoryDefinition definition = findManagementTerritory(player, pos);
        if (definition == null) return;

        CatanSavedData data = CatanSavedData.get(player.server);
        TerritoryData territory = data.territory(definition.id());
        NationData playerNation = data.nationForPlayer(player.getUUID());

        if (territory == null || territory.ownerNationId() == null) {
            player.sendSystemMessage(Component.literal(
                    definition.name() + " is neutral. Its management terminal is inactive."));
            finish(event);
            return;
        }

        NationData owner = data.nation(territory.ownerNationId());
        if (playerNation == null || !territory.ownerNationId().equals(playerNation.id())) {
            player.sendSystemMessage(Component.literal(
                    definition.name() + " is controlled by " +
                            (owner == null ? "another nation" : owner.name()) + "."));
            finish(event);
            return;
        }

        NationDashboard.open(player);
        finish(event);
    }

    private static TerritoryDefinition findManagementTerritory(
            ServerPlayer player,
            BlockPos pos
    ) {
        String dimension = player.serverLevel().dimension().location().toString();

        for (TerritoryDefinition territory : MapDefinitionManager.territories()) {
            if (!territory.dimension().equals(dimension)) continue;

            MapAnchor anchor = territory.managementAnchor();
            if (anchor == null) continue;

            if (anchor.x() == pos.getX()
                    && anchor.y() == pos.getY()
                    && anchor.z() == pos.getZ()) {
                return territory;
            }
        }
        return null;
    }

    private static void finish(PlayerInteractEvent.RightClickBlock event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
