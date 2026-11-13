import com.quin.catancraft.world.VehicleFuel49;
import java.math.BigInteger;
import java.util.Random;

/** Standalone arithmetic tests; these do not claim native entity/persistence coverage. */
public final class VehicleFuel49Test {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void rejected(Runnable action) {
        checks++;
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid request accepted");
    }
    private static VehicleFuel49.Quote fill(VehicleFuel49.Tariff tariff, long capacity,
                                           long current, VehicleFuel49.Meter meter) {
        return VehicleFuel49.quote(tariff, capacity, current, VehicleFuel49.Mode.FILL, meter);
    }
    public static void main(String[] args) {
        long cap = 5_000_000;
        for (var tariff : new VehicleFuel49.Tariff[]{VehicleFuel49.PICKUP, VehicleFuel49.LAV}) {
            var meter = VehicleFuel49.emptyMeter(tariff, cap);
            var quarter = VehicleFuel49.quote(tariff, cap, 0, VehicleFuel49.Mode.ADD_QUARTER, meter);
            check(quarter.afterEnergy() == 1_250_000, "Quarter native energy");
            check(quarter.fuelDebit() == tariff.fullTankFuel() / 4, "Quarter price");
            check(quarter.nextMeter().creditNumerator() == 0, "Exact quarter credit");
            var full = fill(tariff, cap, 0, meter);
            check(full.afterEnergy() == cap && full.fuelDebit() == tariff.fullTankFuel(), "Full price");
            var top = VehicleFuel49.quote(tariff, cap, cap - 1, VehicleFuel49.Mode.ADD_QUARTER, meter);
            check(top.deliveredEnergy() == 1 && top.afterEnergy() == cap, "Near-full clipping");
            check(top.fuelDebit() == 1, "First tiny fill prepaid");
            var repeat = fill(tariff, cap, cap - 1, top.nextMeter());
            check(repeat.fuelDebit() == 0 && repeat.nextMeter().creditNumerator()
                    == top.nextMeter().creditNumerator() - tariff.fullTankFuel(), "Credit consumed exactly");
            var noOp = fill(tariff, cap, cap, top.nextMeter());
            check(noOp.deliveredEnergy() == 0 && noOp.fuelDebit() == 0
                    && noOp.nextMeter().equals(top.nextMeter()), "Full tank preserves credit");
        }
        check(VehicleFuel49.initialDeliveryEnergy(cap) == 1_250_000, "Initial quarter");
        check(VehicleFuel49.initialDeliveryEnergy(7) == 1, "Integer initial quarter rounds down");
        var meter = VehicleFuel49.emptyMeter(VehicleFuel49.PICKUP, cap);
        rejected(() -> fill(VehicleFuel49.PICKUP, cap, -1, meter));
        rejected(() -> fill(VehicleFuel49.PICKUP, cap, cap + 1, meter));
        rejected(() -> fill(VehicleFuel49.LAV, cap, 0, meter));
        rejected(() -> fill(VehicleFuel49.PICKUP, cap + 1, 0, meter));
        rejected(() -> fill(new VehicleFuel49.Tariff("next", "pickup", 32), cap, 0, meter));
        rejected(() -> fill(new VehicleFuel49.Tariff(VehicleFuel49.CATALOG, "pickup", 33), cap, 0, meter));
        rejected(() -> new VehicleFuel49.Tariff("", "pickup", 32));
        rejected(() -> new VehicleFuel49.Tariff("v1", "pickup", 0));
        rejected(() -> new VehicleFuel49.Tariff("v1", "pickup", -1));
        rejected(() -> new VehicleFuel49.Meter("v1", "pickup", cap, 32, -1));
        rejected(() -> new VehicleFuel49.Meter("v1", "pickup", cap, 32, cap));
        rejected(() -> VehicleFuel49.initialDeliveryEnergy(3));

        // Multiplication can exceed signed long by 18 orders of magnitude.
        var huge = new VehicleFuel49.Tariff("huge", "fixture", Long.MAX_VALUE);
        var hugeQuote = fill(huge, Long.MAX_VALUE, 0, VehicleFuel49.emptyMeter(huge, Long.MAX_VALUE));
        check(hugeQuote.afterEnergy() == Long.MAX_VALUE && hugeQuote.fuelDebit() == Long.MAX_VALUE,
                "Overflow-safe maximal quote");

        // Repeated partial refills: exact lifetime conservation including prepaid credit.
        Random random = new Random(490049);
        for (int scenario = 0; scenario < 100; scenario++) {
            long capacity = 4 + random.nextInt(1_000_000);
            var tariff = new VehicleFuel49.Tariff("random", "fixture", 1 + random.nextInt(1000));
            var credit = VehicleFuel49.emptyMeter(tariff, capacity);
            BigInteger paid = BigInteger.ZERO, delivered = BigInteger.ZERO;
            for (int step = 0; step < 100; step++) {
                long deficit = random.nextInt((int)capacity + 1);
                var quote = fill(tariff, capacity, capacity - deficit, credit);
                paid = paid.add(BigInteger.valueOf(quote.fuelDebit()));
                delivered = delivered.add(BigInteger.valueOf(quote.deliveredEnergy()));
                credit = quote.nextMeter();
                BigInteger spent = delivered.multiply(BigInteger.valueOf(tariff.fullTankFuel()));
                check(paid.multiply(BigInteger.valueOf(capacity)).equals(
                        spent.add(BigInteger.valueOf(credit.creditNumerator()))), "Exact lifetime conservation");
                BigInteger expected = spent.add(BigInteger.valueOf(capacity - 1)).divide(BigInteger.valueOf(capacity));
                check(paid.equals(expected), "Split fills equal cumulative rounded cost");
            }
        }
        System.out.println("VehicleFuel49 standalone: " + checks + " assertions passed; native runtime not exercised");
    }
}
