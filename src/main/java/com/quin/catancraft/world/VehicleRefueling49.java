package com.quin.catancraft.world;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/** Refueling protocol only. No world adapter or public route is registered here.
 * Adapter implementations must keep debit+receipt atomic in the nation ledger,
 * and energy+receipt atomic in the native vehicle save. Ambiguity never retries
 * a mutation. A receipt identifies the complete immutable Intent, not just its ID.
 */
public final class VehicleRefueling49 {
    private VehicleRefueling49() {}
    public enum Phase { PREPARED, DEBIT_ATTEMPTED, PAID, ENERGY_ATTEMPTED, COMPLETE }
    public enum Proof { ABSENT, EXACT, UNKNOWN, CONFLICT }
    public enum Result { COMPLETE, HELD }
    public record Intent(UUID operation, UUID actor, UUID nation, UUID vehicle,
                         String world, String facility, VehicleFuel49.Tariff tariff,
                         VehicleFuel49.Mode mode, VehicleFuel49.Meter beforeMeter,
                         VehicleFuel49.Quote quote) {
        public Intent {
            Objects.requireNonNull(operation); Objects.requireNonNull(actor);
            Objects.requireNonNull(nation); Objects.requireNonNull(vehicle);
            Objects.requireNonNull(tariff); Objects.requireNonNull(mode);
            Objects.requireNonNull(beforeMeter); Objects.requireNonNull(quote);
            if (world == null || world.isBlank() || facility == null || facility.isBlank()
                    || quote.deliveredEnergy() <= 0 || !quote.equals(VehicleFuel49.quote(
                    tariff, beforeMeter.capacity(), quote.beforeEnergy(), mode, beforeMeter)))
                throw new IllegalArgumentException("Invalid immutable refueling intent");
        }
        public long beforeEnergy() { return quote.beforeEnergy(); }
        public long addedEnergy() { return quote.deliveredEnergy(); }
        public long targetEnergy() { return quote.afterEnergy(); }
        public long fuelCost() { return quote.fuelDebit(); }
    }
    public record Record(Intent intent, Phase phase) {
        public Record { Objects.requireNonNull(intent); Objects.requireNonNull(phase); }
    }
    public interface Environment {
        /** Server thread, exact world/vehicle/catalog, leader/nation ownership,
         * authored active factory, proximity, stationary/unoccupied vehicle,
         * quote lifetime and exclusive per-vehicle operation reservation.
         * Recovery may require a fresh physical session for the same immutable
         * operation; authority failure never permits a mutation or automatic refund. */
        void guard(Intent intent) throws IOException;
        Record read(UUID operation) throws IOException;
        /** Durable compare-and-set. Null expected means absent. Verify readback. */
        void save(Record expected, Record next) throws IOException;
        Proof debitProof(Intent intent) throws IOException;
        /** Meter is scoped to this nation AND vehicle; no transferable credit. */
        VehicleFuel49.Meter currentMeter(Intent intent) throws IOException;
        /** Debit national Fuel, replace beforeMeter with quote.nextMeter and persist exact receipt together; no retry here. */
        void debitAndSave(Intent intent) throws IOException;
        Proof energyProof(Intent intent) throws IOException;
        /** Authoritative current energy; reject absent/unloaded/unsupported state. */
        long currentEnergy(Intent intent) throws IOException;
        /** Add exactly addedEnergy and save receipt in same vehicle record.
         * Revalidate beforeEnergy immediately before mutation. No target reset. */
        void addEnergyAndSave(Intent intent) throws IOException;
    }
    public static Result execute(Environment env, Intent intent) throws IOException {
        Objects.requireNonNull(env); Objects.requireNonNull(intent);
        Record record = env.read(intent.operation());
        if (record != null && !record.intent().equals(intent))
            throw new IOException("Conflicting refueling operation identity");
        env.guard(intent);
        if (record != null && record.phase() == Phase.COMPLETE)
            return env.debitProof(intent) == Proof.EXACT && env.energyProof(intent) == Proof.EXACT
                    ? Result.COMPLETE : Result.HELD;
        if (record == null) {
            if (env.debitProof(intent) != Proof.ABSENT || !env.currentMeter(intent).equals(intent.beforeMeter())
                    || env.energyProof(intent) != Proof.ABSENT
                    || env.currentEnergy(intent) != intent.beforeEnergy()) return Result.HELD;
            record = advance(env, null, intent, Phase.PREPARED);
        }
        if (record.phase() == Phase.PREPARED) {
            env.guard(intent);
            if (env.debitProof(intent) != Proof.ABSENT || !env.currentMeter(intent).equals(intent.beforeMeter())
                    || env.energyProof(intent) != Proof.ABSENT
                    || env.currentEnergy(intent) != intent.beforeEnergy()) return Result.HELD;
            record = advance(env, record, intent, Phase.DEBIT_ATTEMPTED);
            // Only this uninterrupted transition may perform the debit.
            env.debitAndSave(intent);
        }
        if (record.phase() == Phase.DEBIT_ATTEMPTED) {
            if (env.debitProof(intent) != Proof.EXACT) return Result.HELD;
            record = advance(env, record, intent, Phase.PAID);
        }
        if (record.phase() == Phase.PAID) {
            env.guard(intent);
            if (env.debitProof(intent) != Proof.EXACT || env.energyProof(intent) != Proof.ABSENT
                    || env.currentEnergy(intent) != intent.beforeEnergy()) return Result.HELD;
            record = advance(env, record, intent, Phase.ENERGY_ATTEMPTED);
            // Never replay an attempted addition, even if current energy equals beforeEnergy.
            env.addEnergyAndSave(intent);
        }
        if (record.phase() == Phase.ENERGY_ATTEMPTED) {
            if (env.debitProof(intent) != Proof.EXACT || env.energyProof(intent) != Proof.EXACT)
                return Result.HELD;
            advance(env, record, intent, Phase.COMPLETE);
            return Result.COMPLETE;
        }
        return Result.HELD;
    }
    private static Record advance(Environment env, Record previous, Intent intent, Phase phase)
            throws IOException {
        Record next = new Record(intent, phase);
        env.save(previous, next);
        if (!next.equals(env.read(intent.operation()))) throw new IOException("Refueling journal readback differs");
        return next;
    }
}
