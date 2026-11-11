package com.quin.catancraft.mixin;
import com.quin.catancraft.world.HandheldWeapons44;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.Supplier;
/** These are handheld-only handlers. VehicleFire/Reload handlers are not targeted. */
@Pseudo
@Mixin(targets={"com.atsuishio.superbwarfare.network.message.send.ShootMessage","com.atsuishio.superbwarfare.network.message.send.FireKeyMessage","com.atsuishio.superbwarfare.network.message.send.LaserShootMessage","com.atsuishio.superbwarfare.network.message.send.MeleeAttackMessage","com.atsuishio.superbwarfare.network.message.send.LungeMineAttackMessage"},remap=false)
public abstract class SbwHandheldPacketMixin {
    @Inject(method="handler(Ljava/util/function/Supplier;)V",at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private void catancraft$denyHandheldPacket(Supplier<?> context,CallbackInfo ci){HandheldWeapons44.deniedPacket();ci.cancel();}
}
