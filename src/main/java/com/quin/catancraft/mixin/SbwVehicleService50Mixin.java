package com.quin.catancraft.mixin;

import com.quin.catancraft.world.VehicleService50;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity",remap=false)
public abstract class SbwVehicleService50Mixin {
    @Inject(method={"baseTick()V","m_6075_()V"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private void catancraft$holdServiceTick(CallbackInfo ci){if(VehicleService50.locked((Entity)(Object)this))ci.cancel();}
    @Inject(method="setEnergy(I)V",at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private void catancraft$holdServiceEnergy(int energy,CallbackInfo ci){if(!VehicleService50.energyMutationAllowed((Entity)(Object)this))ci.cancel();}
}
