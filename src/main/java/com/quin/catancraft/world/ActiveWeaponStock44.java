package com.quin.catancraft.world;

import java.io.IOException;
import java.util.List;

/** Current owner policy: TaCZ-only gun/ammunition sales. The 042 catalog is retained
 * as immutable historical metadata; its SBW rows do not authorize new sales. */
final class ActiveWeaponStock44 {
    static final String POLICY_REVISION = "tacz_only_weapon_policy44_v1";
    private ActiveWeaponStock44() {}

    static List<WeaponStock42.Offer> offers() throws IOException {
        List<WeaponStock42.Offer> result = WeaponStock42.offers().stream()
                .filter(offer -> offer.provider().equals("tacz")).toList();
        if (result.size() != 69
                || result.stream().filter(offer -> offer.kind() == WeaponShopCatalog40.Kind.GUN).count() != 52
                || result.stream().filter(offer -> offer.kind() == WeaponShopCatalog40.Kind.AMMO).count() != 17
                || result.stream().anyMatch(offer -> !offer.nativeId().startsWith("tacz:")))
            throw new IOException("The audited TaCZ-only stock changed.");
        return result;
    }

    static WeaponStock42.Offer offer(String id) throws IOException {
        WeaponStock42.Offer offer = WeaponStock42.offer(id);
        requireCurrent(offer);
        return offer;
    }

    static void requireCurrent(WeaponStock42.Offer offer) throws IOException {
        WeaponStock42.requireCurrent(offer);
        if (!offer.provider().equals("tacz") || !offer.nativeId().startsWith("tacz:"))
            throw new IOException("Only TaCZ guns and ammunition are enabled. Superb Warfare handheld weapons are disabled.");
    }

    static List<WeaponStock42.Offer> page(int page, int pageSize) throws IOException {
        if (page < 0 || pageSize < 1 || pageSize > 64) throw new IOException("Invalid weapon stock page.");
        List<WeaponStock42.Offer> all = offers();
        long start = (long) page * pageSize;
        if (start >= all.size()) throw new IOException("Weapon stock page is out of range.");
        return List.copyOf(all.subList((int) start, (int) Math.min(start + pageSize, all.size())));
    }
}
