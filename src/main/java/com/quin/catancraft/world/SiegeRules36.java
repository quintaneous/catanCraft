package com.quin.catancraft.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Unregistered, immutable siege objective rules. No world reads/writes, scheduler,
 * command, damage permission, payment or ownership settlement is installed here.
 * A future server adapter must authorize actors, obtain authoritative presence and
 * persist a transition before publishing it. Numeric timing and empty-zone policy
 * must be supplied explicitly; there is no approved production timing default.
 */
final class SiegeRules36 {
    enum Stage { OUTER, TOWN_HALL }
    enum Status { OUTER, TOWN_HALL, ATTACKER_WON, DEFENDER_WON_TIMEOUT, DEFENDER_WON_SURRENDER }
    enum EmptyPolicy { HOLD, RESET }

    record Timing(String revision, int totalTicks, int outerTicks, int townHallTicks, EmptyPolicy emptyPolicy) {
        Timing {
            label(revision, "rules revision");
            Objects.requireNonNull(emptyPolicy, "Explicit empty-zone policy required");
            // Strictly before expiry: even uninterrupted capture must have a feasible win.
            if (totalTicks <= 0 || outerTicks <= 0 || townHallTicks <= 0
                    || (long) outerTicks + townHallTicks >= totalTicks) {
                throw new IllegalArgumentException("Positive hold durations must fit before the overall deadline");
            }
        }
    }

    record Contract(UUID siege, String world, String city, UUID attackers, UUID defenders, Timing timing) {
        Contract {
            nonzero(siege, "siege");
            label(world, "world identity");
            if (city == null || !city.matches("[a-z0-9_]{1,48}")) {
                throw new IllegalArgumentException("Explicit canonical city identity required");
            }
            nonzero(attackers, "attacker nation");
            nonzero(defenders, "defender nation");
            if (attackers.equals(defenders)) throw new IllegalArgumentException("Distinct nations required");
            Objects.requireNonNull(timing, "Explicit frozen timing required");
        }
    }

    /** Presence belongs to exactly one active objective and must come from the server.
     * This contains no login/disconnect signal: an empty zone is not surrender. */
    record Observation(Stage objective, boolean attackersPresent, boolean defendersPresent) {
        Observation { Objects.requireNonNull(objective, "Objective required"); }
    }

    record Snapshot(Contract contract, Status status, int elapsedTicks, int outerProgress, int townHallProgress) {
        Snapshot {
            Objects.requireNonNull(contract, "Original contract required");
            Objects.requireNonNull(status, "Status required");
            Timing t = contract.timing();
            if (elapsedTicks < 0 || elapsedTicks > t.totalTicks()
                    || outerProgress < 0 || outerProgress > t.outerTicks()
                    || townHallProgress < 0 || townHallProgress > t.townHallTicks()
                    || (long) outerProgress + townHallProgress > elapsedTicks
                    || (outerProgress < t.outerTicks() && townHallProgress != 0)) {
                throw new IllegalArgumentException("Impossible siege counters");
            }
            boolean expired = elapsedTicks == t.totalTicks();
            boolean outerComplete = outerProgress == t.outerTicks();
            boolean hallComplete = townHallProgress == t.townHallTicks();
            boolean valid = switch (status) {
                case OUTER -> !expired && !outerComplete && townHallProgress == 0;
                case TOWN_HALL -> !expired && outerComplete && !hallComplete;
                case ATTACKER_WON -> !expired && outerComplete && hallComplete;
                case DEFENDER_WON_TIMEOUT -> expired && !hallComplete;
                case DEFENDER_WON_SURRENDER -> !expired && !hallComplete;
            };
            if (!valid) throw new IllegalArgumentException("Siege status disagrees with its counters");
        }

        boolean terminal() { return status != Status.OUTER && status != Status.TOWN_HALL; }
        Stage activeStage() {
            if (terminal()) throw new IllegalStateException("Completed siege has no active objective");
            return status == Status.OUTER ? Stage.OUTER : Stage.TOWN_HALL;
        }
        int remainingTicks() { return contract.timing().totalTicks() - elapsedTicks; }
        Optional<UUID> winnerNationId() {
            if (!terminal()) return Optional.empty();
            return Optional.of(status == Status.ATTACKER_WON ? contract.attackers() : contract.defenders());
        }
    }

    private SiegeRules36() {}

    static Snapshot start(Contract contract, boolean attackersOnline, boolean defendersOnline) {
        Objects.requireNonNull(contract, "Contract required");
        if (!attackersOnline || !defendersOnline) {
            throw new IllegalArgumentException("Both nations must have an online player to start");
        }
        return new Snapshot(contract, Status.OUTER, 0, 0, 0);
    }

    /** Consume ONE observed running-siege tick, never an inferred elapsed interval.
     * expectedElapsedTicks checks the supplied snapshot, not global storage: the
     * adapter must serialize/CAS against current durable state before publishing.
     * Terminal snapshots are inert. The production adapter's disconnected/downtime clock
     * behavior remains undefined until its separate contract is agreed.
     */
    static Snapshot tick(Snapshot before, int expectedElapsedTicks, Observation observation) {
        Objects.requireNonNull(before, "Snapshot required");
        Objects.requireNonNull(observation, "Server observation required");
        if (before.terminal()) return before;
        if (expectedElapsedTicks != before.elapsedTicks()) {
            throw new IllegalArgumentException("Stale or skipped siege tick cursor");
        }
        if (observation.objective() != before.activeStage()) {
            throw new IllegalArgumentException("Observation belongs to another objective");
        }
        int elapsed = before.elapsedTicks() + 1; // Cannot overflow: active elapsed < total <= MAX_VALUE.
        Timing timing = before.contract().timing();
        int outer = before.outerProgress(), hall = before.townHallProgress();
        // Deterministic strict-deadline rule: no overtime or last-tick capture credit.
        if (elapsed == timing.totalTicks()) {
            return new Snapshot(before.contract(), Status.DEFENDER_WON_TIMEOUT, elapsed, outer, hall);
        }
        if (observation.defendersPresent()) {
            return new Snapshot(before.contract(), before.status(), elapsed, outer, hall);
        }
        if (!observation.attackersPresent()) {
            if (timing.emptyPolicy() == EmptyPolicy.RESET) {
                if (before.status() == Status.OUTER) outer = 0;
                else hall = 0; // Never undo the completed outer stage implicitly.
            }
            return new Snapshot(before.contract(), before.status(), elapsed, outer, hall);
        }
        Status status = before.status();
        if (status == Status.OUTER) {
            outer++;
            if (outer == timing.outerTicks()) status = Status.TOWN_HALL;
        } else {
            hall++;
            if (hall == timing.townHallTicks()) status = Status.ATTACKER_WON;
        }
        // No carry-over capture credit and no timer reset on an objective transition.
        return new Snapshot(before.contract(), status, elapsed, outer, hall);
    }

    /** Caller must separately check the actual actor's permission to surrender for
     * this nation. Nation identity alone is not a player authorization mechanism. */
    static Snapshot surrender(Snapshot before, UUID requestingNation) {
        Objects.requireNonNull(before, "Snapshot required");
        if (!before.contract().attackers().equals(requestingNation)) {
            throw new IllegalArgumentException("Only the attacking nation may surrender its attack");
        }
        if (before.terminal()) return before;
        return new Snapshot(before.contract(), Status.DEFENDER_WON_SURRENDER,
                before.elapsedTicks(), before.outerProgress(), before.townHallProgress());
    }

    /** Strict transport codec, not an authenticated journal, restoration snapshot,
     * or permission to adopt arbitrary saved siege progress. Retain the original
     * authorized contract separately and supply it again when decoding. */
    static CompoundTag save(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "Snapshot required");
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", 1);
        tag.put("contract", contractTag(snapshot.contract()));
        tag.putString("status", snapshot.status().name());
        tag.putInt("elapsed", snapshot.elapsedTicks());
        tag.putInt("outerProgress", snapshot.outerProgress());
        tag.putInt("townHallProgress", snapshot.townHallProgress());
        return tag;
    }

    static Snapshot load(CompoundTag tag, Contract expected) {
        Objects.requireNonNull(tag, "Snapshot tag required");
        Objects.requireNonNull(expected, "Independently authorized original contract required");
        if (!tag.getAllKeys().equals(Set.of("schema", "contract", "status", "elapsed", "outerProgress", "townHallProgress"))
                || !tag.contains("schema", Tag.TAG_INT) || tag.getInt("schema") != 1
                || !tag.contains("contract", Tag.TAG_COMPOUND) || !tag.getCompound("contract").equals(contractTag(expected))
                || !tag.contains("status", Tag.TAG_STRING) || !tag.contains("elapsed", Tag.TAG_INT)
                || !tag.contains("outerProgress", Tag.TAG_INT) || !tag.contains("townHallProgress", Tag.TAG_INT)) {
            throw new IllegalArgumentException("Foreign, changed or malformed siege snapshot/contract");
        }
        return new Snapshot(expected, Status.valueOf(tag.getString("status")), tag.getInt("elapsed"),
                tag.getInt("outerProgress"), tag.getInt("townHallProgress"));
    }

    private static CompoundTag contractTag(Contract contract) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("siege", contract.siege());
        tag.putString("world", contract.world());
        tag.putString("city", contract.city());
        tag.putUUID("attackers", contract.attackers());
        tag.putUUID("defenders", contract.defenders());
        Timing t = contract.timing();
        tag.putString("rulesRevision", t.revision());
        tag.putInt("totalTicks", t.totalTicks());
        tag.putInt("outerTicks", t.outerTicks());
        tag.putInt("townHallTicks", t.townHallTicks());
        tag.putString("emptyPolicy", t.emptyPolicy().name());
        return tag;
    }

    private static void label(String value, String kind) {
        if (value == null || value.isBlank() || value.length() > 128 || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid " + kind);
        }
    }

    private static void nonzero(UUID value, String kind) {
        if (value == null || (value.getMostSignificantBits() == 0 && value.getLeastSignificantBits() == 0)) {
            throw new IllegalArgumentException("Explicit " + kind + " identity required");
        }
    }
}
