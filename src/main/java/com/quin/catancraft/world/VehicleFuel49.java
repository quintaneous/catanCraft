package com.quin.catancraft.world;

import java.math.BigInteger;
import java.util.Objects;

/** Pure, versioned national-Fuel quotes. Does not authorize, debit or mutate entities. */
public final class VehicleFuel49 {
    public static final String CATALOG = "vehicle_fuel49_v1";
    public static final Tariff PICKUP = new Tariff(CATALOG, "pickup", 32);
    public static final Tariff LAV = new Tariff(CATALOG, "lav150", 80);
    public enum Mode { ADD_QUARTER, FILL }

    public record Tariff(String catalog, String offer, long fullTankFuel) {
        public Tariff {
            if (catalog == null || catalog.isBlank() || offer == null || offer.isBlank()
                    || fullTankFuel <= 0) throw new IllegalArgumentException("Invalid fuel tariff");
        }
    }

    /** Prepaid fraction of a Fuel unit, with capacity as denominator. Persist per nation/vehicle.
     * Never discard this meter after a partial fill or silently migrate it to a new tariff. */
    public record Meter(String catalog, String offer, long capacity, long fullTankFuel,
                        long creditNumerator) {
        public Meter {
            new Tariff(catalog, offer, fullTankFuel);
            requireCapacity(capacity);
            if (creditNumerator < 0 || creditNumerator >= capacity)
                throw new IllegalArgumentException("Invalid prepaid fuel credit");
        }
    }

    public record Quote(long beforeEnergy, long afterEnergy, long deliveredEnergy,
                        long fuelDebit, Meter nextMeter) {}

    private VehicleFuel49() {}

    private static void requireCapacity(long capacity) {
        if (capacity < 4) throw new IllegalArgumentException("Unsupported native energy capacity");
    }

    public static Meter emptyMeter(Tariff tariff, long capacity) {
        Objects.requireNonNull(tariff, "tariff");
        return new Meter(tariff.catalog(), tariff.offer(), capacity, tariff.fullTankFuel(), 0);
    }

    /** Only newly created vehicles under the new delivery catalog receive this amount.
     * Old purchases/recovery records must never call this as a retrofit. */
    public static long initialDeliveryEnergy(long capacity) {
        requireCapacity(capacity);
        return capacity / 4;
    }

    /** Compute a quote against observed native energy. Caller must revalidate it at confirmation.
     * Integer native units round the quarter down; capacity=5,000,000 divides exactly.
     * Whole Fuel debits round up only the unpaid portion. The remainder is prepaid credit,
     * so splitting a refill never costs more than the same delivered amount in one operation. */
    public static Quote quote(Tariff tariff, long capacity, long currentEnergy, Mode mode,
                              Meter meter) {
        Objects.requireNonNull(tariff, "tariff");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(meter, "meter");
        requireCapacity(capacity);
        if (currentEnergy < 0 || currentEnergy > capacity)
            throw new IllegalArgumentException("Invalid native energy reading");
        if (!meter.catalog().equals(tariff.catalog()) || !meter.offer().equals(tariff.offer())
                || meter.capacity() != capacity || meter.fullTankFuel() != tariff.fullTankFuel())
            throw new IllegalArgumentException("Fuel meter tariff/capacity mismatch; preserve for review");
        long delivered = mode == Mode.FILL ? capacity - currentEnergy
                : Math.min(capacity / 4, capacity - currentEnergy);
        BigInteger denominator = BigInteger.valueOf(capacity);
        BigInteger unpaid = BigInteger.valueOf(delivered).multiply(BigInteger.valueOf(tariff.fullTankFuel()))
                .subtract(BigInteger.valueOf(meter.creditNumerator()));
        BigInteger debit = unpaid.signum() <= 0 ? BigInteger.ZERO
                : unpaid.add(denominator).subtract(BigInteger.ONE).divide(denominator);
        long credit = debit.multiply(denominator).subtract(unpaid).longValueExact();
        Meter next = new Meter(tariff.catalog(), tariff.offer(), capacity, tariff.fullTankFuel(), credit);
        return new Quote(currentEnergy, currentEnergy + delivered, delivered, debit.longValueExact(), next);
    }
}
