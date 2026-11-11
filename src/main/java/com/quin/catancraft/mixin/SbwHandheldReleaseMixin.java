package com.quin.catancraft.mixin;
import com.quin.catancraft.world.HandheldWeapons44;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets={"com.atsuishio.superbwarfare.item.gun.GunItem","com.atsuishio.superbwarfare.item.gun.special.BocekItem"},remap=false)
public abstract class SbwHandheldReleaseMixin {
    @Inject(method="onFireKeyRelease",at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private void catancraft$denyRelease(@Coerce Object data,Player player,double power,boolean zoom,CallbackInfo ci){HandheldWeapons44.deniedPacket();ci.cancel();}
}
