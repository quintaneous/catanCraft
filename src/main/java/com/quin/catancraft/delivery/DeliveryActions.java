package com.quin.catancraft.delivery;

import java.util.UUID;

/** Physical handoffs shared by the command entry points and offline route checks. */
public final class DeliveryActions {
    public static final String CARGO = "a crate of pump fittings";

    private DeliveryActions() {}

    public static DeliveryLedger.Waybill pickup(DeliveryLedger ledger, UUID player, DeliveryRoute route,
                                               String dimension, double x, double y, double z) {
        requireAt(route.pickup(), dimension, x, y, z);
        return ledger.pickup(player, route.pickup().key(), route.dropoff().key());
    }

    public static boolean turnIn(DeliveryLedger ledger, UUID player, UUID bill, DeliveryRoute route,
                                 String dimension, double x, double y, double z) {
        requireAt(route.dropoff(), dimension, x, y, z);
        DeliveryLedger.Waybill active = ledger.active(player);
        if (active != null && active.id().equals(bill) && !active.origin().equals(route.pickup().key())) {
            throw new IllegalStateException("The pickup for this waybill has changed. Open /delivery to abandon it.");
        }
        return ledger.deliver(player, bill, route.dropoff().key());
    }

    private static void requireAt(DeliveryRoute.Endpoint endpoint, String dimension, double x, double y, double z) {
        if (!endpoint.contains(dimension, x, y, z)) {
            throw new IllegalStateException("Travel to the roadside handoff at " + endpoint.name()
                    + " (" + endpoint.x() + ", " + endpoint.y() + ", " + endpoint.z() + ").");
        }
    }
}
