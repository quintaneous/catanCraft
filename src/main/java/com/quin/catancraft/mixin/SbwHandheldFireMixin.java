package com.quin.catancraft.mixin;
import com.quin.catancraft.world.HandheldWeapons44;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets={"com.atsuishio.superbwarfare.item.gun.GunItem","com.atsuishio.superbwarfare.item.gun.launcher.IglaItem","com.atsuishio.superbwarfare.item.gun.launcher.JavelinItem","com.atsuishio.superbwarfare.item.gun.special.BocekItem"},remap=false)
public abstract class SbwHandheldFireMixin {
    @Inject(method="shoot(Lcom/atsuishio/superbwarfare/data/gun/ShootParameters;)V",at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private void catancraft$onlyVehicleWeapon(@Coerce Object parameters,CallbackInfo ci){if(!HandheldWeapons44.allowNativeShot(this,parameters))ci.cancel();}
}
