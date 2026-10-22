package com.quin.catancraft.mixin;

import com.quin.catancraft.CatanCraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Compatibility guard for Superb Warfare's direct crowbar-to-container code.
 *
 * Forge PlayerInteractEvent alone did not stop the user's 0.1.2 pickup test.
 * Intercept VehicleEntity.onCrowbarInteract before getRetrieveItems() or
 * vehicle.discard() can execute. Do not depend on a Forge event being emitted.
 *
 * @Pseudo allows CatanCraft to run when Superb Warfare is not installed.
 */
@Pseudo
@Mixin(targets = {
        "com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity",
        "com.atsuishio.superbwarfare.entity.vehicle.base.AutoAimableEntity",
        "com.atsuishio.superbwarfare.entity.vehicle.MortarEntity",
        "com.atsuishio.superbwarfare.entity.vehicle.AnnihilatorEntity"
}, remap = false)
public abstract class VehicleCrowbarGuardMixin {
    @Inject(method = "onCrowbarInteract", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void catancraft$denyPurchasedVehiclePacking(
            ItemStack stack,
            Player player,
            InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> cir) {
        Entity vehicle = (Entity) (Object) this;
        if (!com.quin.catancraft.world.NationVehicleProtection.denyRetrieval(vehicle, player)) return;

        // Deny before Superb Warfare returns a portable container or discards
        // the vehicle. Handle both client and server to avoid interaction flicker.
        if (!player.level().isClientSide()) {
            player.displayClientMessage(Component.literal(
                    "This nation-purchased vehicle cannot be packed into an item."), true);
            CatanCraft.LOGGER.info(
                    "Denied crowbar retrieval of purchased nation vehicle {} by {}",
                    vehicle.getUUID(), player.getGameProfile().getName());
        }
        cir.setReturnValue(InteractionResult.FAIL);
    }
}
