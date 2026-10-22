package com.quin.catancraft.mixin;

import com.quin.catancraft.world.NationVehicleProtection;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Exact SBW 0.8.9.2 Kotlin helper: the offhand artillery path bypasses the base method. */
@Pseudo
@Mixin(targets = "com.atsuishio.superbwarfare.item.weapon.CrowbarItem$Companion", remap = false)
public abstract class CrowbarHelperGuardMixin {
    @Inject(method = "crowbarInteract", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void catancraft$denyHelperRetrieval(@Coerce Object vehicle, ItemStack stack,
            Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (vehicle instanceof Entity entity && NationVehicleProtection.denyRetrieval(entity, player)) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
