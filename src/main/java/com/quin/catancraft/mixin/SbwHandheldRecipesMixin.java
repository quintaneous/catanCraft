package com.quin.catancraft.mixin;
import com.google.gson.JsonElement;
import com.quin.catancraft.world.HandheldWeapons44;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import java.util.Map;
import java.util.LinkedHashMap;
@Mixin(value=RecipeManager.class,remap=false)
public abstract class SbwHandheldRecipesMixin {
    // MCPConfig 1.20.1 joined.tsrg: cjd.a(Map,akx,ban) -> m_5787_.
    @ModifyVariable(method={"apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V","m_5787_(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V"},at=@At("HEAD"),argsOnly=true,index=1,require=1,remap=false)
    private Map<ResourceLocation,JsonElement> catancraft$filterRecipes(Map<ResourceLocation,JsonElement> recipes){Map<ResourceLocation,JsonElement> copy=new LinkedHashMap<>(recipes);copy.entrySet().removeIf(entry->HandheldWeapons44.blockedRecipe(entry.getValue()));return copy;}
}
