package com.quin.catancraft.world;

import com.quin.catancraft.data.ResourceType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The four existing offers only. The larger surveyed catalog is deliberately inactive. */
final class WeaponShopCatalog40 {
    static final String REVISION = "physical_weapon_shop40_existing_prices_v1";
    enum Kind { GUN, AMMO }
    record Offer(String id, String title, Kind kind, String nativeId, String fireMode,
                 int count, int requiredLevel, ShopPurchase40.Price price) {
        String kindName() { return kind == Kind.GUN ? "Gun" : "Ammo"; }
        String description() { return title + " x" + count + " — " + price.describe(); }
    }
    private static final List<Offer> OFFERS = List.of(
            new Offer("pistol", "Glock 17", Kind.GUN, "tacz:glock_17", "SEMI", 1, 0,
                    new ShopPurchase40.Price(350, Map.of(ResourceType.WOOD, 5L, ResourceType.STONE, 5L))),
            new Offer("ak47", "AK-47", Kind.GUN, "tacz:ak47", "AUTO", 1, 1,
                    new ShopPurchase40.Price(1200, Map.of(ResourceType.STEEL, 12L, ResourceType.WOOD, 10L))),
            new Offer("ammo9", "9mm ammunition", Kind.AMMO, "tacz:9mm", "", 32, 0,
                    new ShopPurchase40.Price(100, Map.of(ResourceType.STONE, 5L))),
            new Offer("ammo762", "7.62x39 ammunition", Kind.AMMO, "tacz:762x39", "", 32, 1,
                    new ShopPurchase40.Price(175, Map.of(ResourceType.STEEL, 2L, ResourceType.EXPLOSIVES, 1L))));
    private WeaponShopCatalog40() {}
    static List<Offer> offers() { return OFFERS; }
    static Offer offer(String id) throws IOException {
        return OFFERS.stream().filter(o -> o.id().equals(id)).findFirst()
                .orElseThrow(() -> new IOException("This item is not offered at the weapon counter."));
    }

    /** Native builders and API identity checks; never forceBuild or manufacture fallback NBT.
     * AmmoItemBuilder itself does not validate its pack index, so both indices are checked here. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static ItemStack build(Offer offer) throws IOException {
        if (!ModList.get().isLoaded("tacz")) throw new IOException("The weapon mod is unavailable; no purchase was made.");
        try {
            ResourceLocation id = new ResourceLocation(offer.nativeId());
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            Object index = api.getMethod(offer.kind() == Kind.GUN ? "getCommonGunIndex" : "getCommonAmmoIndex", ResourceLocation.class)
                    .invoke(null, id);
            if (!(index instanceof Optional<?> present) || present.isEmpty())
                throw new IOException("This weapon or ammunition is unavailable in the current gun pack.");
            Class<?> builderClass = Class.forName("com.tacz.guns.api.item.builder."
                    + (offer.kind() == Kind.GUN ? "GunItemBuilder" : "AmmoItemBuilder"));
            Object builder = builderClass.getMethod("create").invoke(null);
            builderClass.getMethod("setId", ResourceLocation.class).invoke(builder, id);
            builderClass.getMethod("setCount", int.class).invoke(builder, offer.count());
            Object mode = null;
            if (offer.kind() == Kind.GUN) {
                Class<? extends Enum> modeClass = (Class<? extends Enum>) Class.forName("com.tacz.guns.api.item.gun.FireMode");
                mode = Enum.valueOf(modeClass, offer.fireMode());
                builderClass.getMethod("setFireMode", modeClass).invoke(builder, mode);
                builderClass.getMethod("setAmmoCount", int.class).invoke(builder, 0);
                builderClass.getMethod("setAmmoInBarrel", boolean.class).invoke(builder, false);
            }
            Object built = builderClass.getMethod("build").invoke(builder);
            if (!(built instanceof ItemStack stack) || stack.isEmpty() || stack.getCount() != offer.count()
                    || stack.getCount() > stack.getMaxStackSize())
                throw new IOException("The native weapon builder did not produce the requested item.");
            Class<?> itemApi = Class.forName("com.tacz.guns.api.item." + (offer.kind() == Kind.GUN ? "IGun" : "IAmmo"));
            if (!itemApi.isInstance(stack.getItem()) || !id.equals(itemApi.getMethod(
                    offer.kind() == Kind.GUN ? "getGunId" : "getAmmoId", ItemStack.class).invoke(stack.getItem(), stack)))
                throw new IOException("The native weapon identity could not be verified.");
            if (offer.kind() == Kind.GUN && (!mode.equals(itemApi.getMethod("getFireMode", ItemStack.class).invoke(stack.getItem(), stack))
                    || ((Number) itemApi.getMethod("getCurrentAmmoCount", ItemStack.class).invoke(stack.getItem(), stack)).intValue() != 0
                    || Boolean.TRUE.equals(itemApi.getMethod("hasBulletInBarrel", ItemStack.class).invoke(stack.getItem(), stack))))
                throw new IOException("The native gun's empty magazine or firing mode could not be verified.");
            // ItemStack.save preserves the native builder's complete item identity for recovery.
            if (!ItemStack.of(stack.save(new net.minecraft.nbt.CompoundTag())).save(new net.minecraft.nbt.CompoundTag())
                    .equals(stack.save(new net.minecraft.nbt.CompoundTag())))
                throw new IOException("The native weapon cannot be saved exactly.");
            return stack.copy();
        } catch (IOException failure) {
            throw failure;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            Throwable cause = failure instanceof InvocationTargetException reflected ? reflected.getCause() : failure;
            throw new IOException("The current weapon mod cannot safely supply this item; no purchase was made.", cause);
        }
    }
}
