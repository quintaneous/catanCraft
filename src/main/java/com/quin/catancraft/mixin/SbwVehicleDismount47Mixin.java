package com.quin.catancraft.mixin;

import com.quin.catancraft.world.NativeVehicleExit47;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity",remap=false)
public abstract class SbwVehicleDismount47Mixin {
    @Inject(method="getDismountLocationForIndex(Lnet/minecraft/world/entity/LivingEntity;I)Lnet/minecraft/world/phys/Vec3;",at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private void catancraft$checkedVoluntaryExit(LivingEntity passenger,int index,CallbackInfoReturnable<Vec3> result){
        Vec3 checked=NativeVehicleExit47.overrideExit((Entity)(Object)this,passenger,index);
        if(checked!=null)result.setReturnValue(checked);
    }
}
