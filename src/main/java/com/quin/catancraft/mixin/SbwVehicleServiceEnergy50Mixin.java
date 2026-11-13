package com.quin.catancraft.mixin;

import com.quin.catancraft.world.VehicleService50;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Charging stations mutate the capability directly, bypassing VehicleEntity.setEnergy. */
@Pseudo
@Mixin(targets="com.atsuishio.superbwarfare.capability.energy.VehicleEnergyStorage",remap=false)
public abstract class SbwVehicleServiceEnergy50Mixin {
    @Unique private boolean catancraft$serviceMayChange(){
        try{
            Class<?> c=this.getClass();
            while(c!=null){try{var f=c.getDeclaredField("vehicle");f.setAccessible(true);return f.get(this) instanceof Entity e&&VehicleService50.energyMutationAllowed(e);}catch(NoSuchFieldException absent){c=c.getSuperclass();}}
        }catch(ReflectiveOperationException|RuntimeException unsupported){return false;}
        return false;
    }
    @Inject(method={"receiveEnergy(IZ)I","extractEnergy(IZ)I"},at=@At("HEAD"),cancellable=true,remap=false,require=2)
    private void catancraft$holdServiceCapability(int amount,boolean simulate,CallbackInfoReturnable<Integer> ci){if(!catancraft$serviceMayChange())ci.setReturnValue(0);}
}
