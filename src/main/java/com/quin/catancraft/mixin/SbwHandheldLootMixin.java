package com.quin.catancraft.mixin;
import com.quin.catancraft.world.HandheldWeapons44;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Runs after every global loot modifier, including SBW's chest modifier. */
@Mixin(value=ForgeHooks.class,remap=false)
public abstract class SbwHandheldLootMixin {
    @Inject(method="modifyLoot(Lnet/minecraft/resources/ResourceLocation;Lit/unimi/dsi/fastutil/objects/ObjectArrayList;Lnet/minecraft/world/level/storage/loot/LootContext;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",at=@At("RETURN"),remap=false,require=1)
    private static void catancraft$filterLoot(ResourceLocation id,ObjectArrayList<ItemStack> input,LootContext context,CallbackInfoReturnable<ObjectArrayList<ItemStack>> cir){HandheldWeapons44.filterLoot(cir.getReturnValue());}
}
