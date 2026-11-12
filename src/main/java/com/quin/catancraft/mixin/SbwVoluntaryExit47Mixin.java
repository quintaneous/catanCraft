package com.quin.catancraft.mixin;

import com.quin.catancraft.world.NativeVehicleExit47;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.Supplier;

@Pseudo
@Mixin(targets="com.atsuishio.superbwarfare.network.message.send.PlayerStopRidingMessage",remap=false)
public abstract class SbwVoluntaryExit47Mixin {
    @Shadow public abstract boolean getEjection();
    @Inject(method="handler(Ljava/util/function/Supplier;)V",at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private void catancraft$preflightVoluntaryExit(Supplier<NetworkEvent.Context> context,CallbackInfo callback){
        if(NativeVehicleExit47.handleVoluntaryPacket(context.get().getSender(),getEjection()))callback.cancel();
    }
}
