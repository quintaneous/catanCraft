package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.world.SiegeObjectives37.*;
import com.quin.catancraft.world.SiegeRules36.*;
import com.quin.catancraft.world.SiegeSession38.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.*;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;

/** ONE objective-state scenario, executed in separate seed and verify JVMs against
 * the same marked disposable world. Roster/ownership/time are explicit synthetic
 * model inputs; there are no connected clients, real nation edits or damage writes.
 * The verify phase reads the original journal, never copies/reseeds/reconstructs it.
 * This is not a damage-restoration or ownership-settlement restart proof. */
@GameTestHolder("catancraft_session38_restart") @PrefixGameTestTemplate(false)
public final class SiegeSession38RestartTests {
    private static final String NS = "catancraft_session38_restart";
    private static final String FIXTURE = "synthetic-session38-restart-v1";
    private static final String CITY = "fixture38_restart";
    private static final String DIM = "minecraft:overworld";
    private static final UUID SIEGE = UUID.fromString("38000000-0000-0000-0000-000000000001");
    private static final UUID ATTACKERS = UUID.fromString("38000000-0000-0000-0000-000000000002");
    private static final UUID DEFENDERS = UUID.fromString("38000000-0000-0000-0000-000000000003");
    private static final Contract CONTRACT = new Contract(SIEGE, FIXTURE, CITY, ATTACKERS, DEFENDERS,
            new Timing("synthetic-session38-timing-v1", 20, 2, 3, EmptyPolicy.HOLD));
    private static final Profile PROFILE = new Profile(CITY, "synthetic-session38-objectives-v1", DIM,
            new Box(-900,140,-900,-896,144,-896), new Box(-880,140,-900,-876,144,-896));
    private SiegeSession38RestartTests() {}

    @GameTest(template="townhall_state_empty", timeoutTicks=200)
    public static void saved_objectives_recover_in_a_distinct_jvm_and_preserve_defender_result(GameTestHelper h) throws Exception {
        String phase = System.getProperty("catancraft.siegeRestartPhase", "");
        h.assertTrue(phase.equals("seed") || phase.equals("verify"), "Explicit seed|verify JVM phase required");
        Model env = new Model(h.getLevel(), CONTRACT, PROFILE);
        Path root = env.root;
        Path proof = root.resolve("data/catancraft_session38_restart_seed.dat");
        Path verified = root.resolve("data/catancraft_session38_restart_verified.dat");
        h.assertTrue(!Files.exists(verified, LinkOption.NOFOLLOW_LINKS), "Already-verified restart fixture must not be reused");
        if (phase.equals("seed")) seed(h, env, proof);
        else verify(h, env, proof, verified);
        h.succeed();
    }

    private static void seed(GameTestHelper h, Model env, Path proof) throws Exception {
        Path journal = SiegeSession38.journalPath(env);
        h.assertTrue(!Files.exists(proof, LinkOption.NOFOLLOW_LINKS)
                && !Files.exists(journal, LinkOption.NOFOLLOW_LINKS), "Seed refuses preexisting proof or siege history");
        env.now = 100;
        View seeded;
        try (Session session = SiegeSession38.create(env)) {
            View view = session.read();
            h.assertTrue(view.revision() == 0 && !view.recoveryRequired() && view.snapshot().elapsedTicks() == 0,
                    "Creation did not establish an initial durable session");
            for (int i = 0; i < 3; i++) {
                env.now++;
                view = session.tick(view.revision());
            }
            seeded = session.read();
            h.assertTrue(seeded.revision() == 3 && seeded.snapshot().status() == Status.TOWN_HALL
                    && seeded.snapshot().elapsedTicks() == 3 && seeded.snapshot().outerProgress() == 2
                    && seeded.snapshot().townHallProgress() == 1 && seeded.snapshot().remainingTicks() == 17,
                    "Seed did not reach the intended partial Town Hall state through actual durable ticks");
        }
        byte[] bytes = journalBytes(journal);
        CompoundTag stored = ProducerJournal.read(bytes);
        h.assertTrue(stored.getCompound("state").equals(SiegeRules36.save(seeded.snapshot()))
                && stored.getLong("revision") == seeded.revision() && stored.getLong("lastTick") == 103,
                "Seed journal does not contain the actual returned durable state");
        CompoundTag record = new CompoundTag();
        record.putInt("schema", 1); record.putString("namespace", NS); record.putString("fixture", FIXTURE);
        record.putString("worldRoot", env.root.toString()); record.putString("dimension", DIM); record.putLong("seed", env.level.getSeed());
        record.putString("process", process()); record.putString("journalFile", journal.getFileName().toString());
        record.putString("journalSHA256", TownHallRevisions.sha(bytes)); record.put("state", SiegeRules36.save(seeded.snapshot()));
        record.putLong("revision", seeded.revision()); record.putLong("clock", env.now); record.putUUID("siege", SIEGE);
        // Original profile is proof metadata only; it is never used to reconstruct the journal.
        record.put("profile", stored.getCompound("profile").copy());
        ProducerJournal.write(proof, record, false);
        h.assertTrue(Arrays.equals(bytes, journalBytes(journal)), "Writing independent proof changed the siege journal");
        CatanCraft.LOGGER.info("SIEGE38_RESTART_SEED_PASS elapsed=3 outer=2 townHall=1 revision=3 originalProfile=PASS journalSaved=PASS process={} restartVerification=PENDING", process());
    }

    private static void verify(GameTestHelper h, Model env, Path proof, Path verified) throws Exception {
        Path journal = SiegeSession38.journalPath(env);
        byte[] proofBytes = journalBytes(proof);
        CompoundTag record = ProducerJournal.read(proofBytes);
        h.assertTrue(record.getInt("schema") == 1 && NS.equals(record.getString("namespace"))
                && FIXTURE.equals(record.getString("fixture")) && env.root.toString().equals(record.getString("worldRoot"))
                && DIM.equals(record.getString("dimension")) && record.getLong("seed") == env.level.getSeed()
                && SIEGE.equals(record.getUUID("siege")) && journal.getFileName().toString().equals(record.getString("journalFile")),
                "Foreign restart proof/world/fixture binding");
        h.assertTrue(!record.getString("process").isBlank() && !process().equals(record.getString("process")),
                "Verification must run in a distinct Java process");
        Snapshot expected = SiegeRules36.load(record.getCompound("state"), CONTRACT);
        h.assertTrue(expected.status() == Status.TOWN_HALL && expected.elapsedTicks() == 3
                && expected.outerProgress() == 2 && expected.townHallProgress() == 1 && expected.remainingTicks() == 17
                && record.getLong("revision") == 3 && record.getLong("clock") == 103,
                "Seed proof is not the independently expected fixture checkpoint");
        byte[] original = journalBytes(journal);
        h.assertTrue(TownHallRevisions.sha(original).equals(record.getString("journalSHA256")),
                "Original journal bytes changed across JVM restart before recovery");
        CompoundTag stored = ProducerJournal.read(original);
        h.assertTrue(stored.getCompound("state").equals(SiegeRules36.save(expected))
                && stored.getCompound("profile").equals(record.getCompound("profile")),
                "Original saved profile/state differs from sealed seed evidence");

        // Changed current configuration is not permission to adopt/rewrite old history.
        Profile changedProfile = new Profile(CITY, "changed-after-restart", DIM, PROFILE.outer(), PROFILE.townHall());
        refuseOpen(new Model(env.level, CONTRACT, changedProfile));
        Contract changedContract = new Contract(UUID.fromString("38000000-0000-0000-0000-000000000004"),
                CONTRACT.world(), CITY, ATTACKERS, DEFENDERS, CONTRACT.timing());
        refuseOpen(new Model(env.level, changedContract, PROFILE));
        h.assertTrue(Arrays.equals(original, journalBytes(journal)), "Refused restart configuration rewrote original history");

        // Deliberately different synthetic clock: no elapsed interval is inferred.
        env.now = 100000; env.defendersInside = 1;
        View terminal;
        try (Session session = SiegeSession38.open(env)) {
            View recovered = session.read();
            h.assertTrue(recovered.recoveryRequired() && recovered.revision() == 3
                    && recovered.snapshot().equals(expected), "Restart lost progress/timer or resumed without revalidation");
            refuse(() -> session.tick(recovered.revision()));
            h.assertTrue(Arrays.equals(original, journalBytes(journal)), "Blocked pre-recovery tick rewrote the saved state");
            View resumed = session.resume(recovered.revision());
            h.assertTrue(!resumed.recoveryRequired() && resumed.revision() == 4
                    && resumed.snapshot().equals(expected), "Recovery reset elapsed/progress or inferred downtime capture");
            CompoundTag resumedDisk = ProducerJournal.read(journalBytes(journal));
            h.assertTrue(resumedDisk.getLong("lastTick") == env.now
                    && !resumedDisk.getUUID("epoch").equals(stored.getUUID("epoch"))
                    && resumedDisk.getCompound("profile").equals(record.getCompound("profile")),
                    "Recovery did not establish a new epoch or changed original objective geometry");
            View view = resumed;
            while (!view.snapshot().terminal()) {
                env.now++;
                view = session.tick(view.revision());
            }
            terminal = session.read();
            h.assertTrue(terminal.revision() == 21 && terminal.snapshot().status() == Status.DEFENDER_WON_TIMEOUT
                    && terminal.snapshot().elapsedTicks() == 20 && terminal.snapshot().outerProgress() == 2
                    && terminal.snapshot().townHallProgress() == 1 && terminal.snapshot().remainingTicks() == 0
                    && terminal.snapshot().winnerNationId().orElseThrow().equals(DEFENDERS),
                    "Recovered contested Town Hall did not preserve time/progress and end in defender timeout victory");
        }
        byte[] finished = journalBytes(journal);
        env.now += 1000;
        try (Session session = SiegeSession38.open(env)) {
            h.assertTrue(session.read().equals(terminal), "Completed result changed or reopened as pending recovery");
            h.assertTrue(session.tick(terminal.revision()).equals(terminal)
                    && session.surrender(terminal.revision(), ATTACKERS).equals(terminal)
                    && session.resume(terminal.revision()).equals(terminal), "Completed result was not inert after another open");
            h.assertTrue(Arrays.equals(finished, journalBytes(journal)), "Terminal retry wrote another journal revision");
        }
        h.assertTrue(Arrays.equals(proofBytes, journalBytes(proof)), "Restart verification rewrote immutable seed evidence");
        CompoundTag receipt = new CompoundTag();
        receipt.putInt("schema", 1); receipt.putString("fixture", FIXTURE); receipt.putUUID("siege", SIEGE);
        receipt.putString("seedProcess", record.getString("process")); receipt.putString("verifyProcess", process());
        receipt.putString("seedProofSHA256", TownHallRevisions.sha(proofBytes));
        receipt.putString("seedJournalSHA256", record.getString("journalSHA256"));
        receipt.putString("terminalJournalSHA256", TownHallRevisions.sha(finished));
        receipt.put("terminalState", SiegeRules36.save(terminal.snapshot())); receipt.putLong("terminalRevision", terminal.revision());
        receipt.putString("result", "PASS"); ProducerJournal.write(verified, receipt, false);
        CatanCraft.LOGGER.info("SIEGE38_RESTART_VERIFY_PASS distinctJVM=PASS originalJournalBytes=PASS fullProfile=PASS explicitRecovery=PASS preservedClockAndProgress=PASS defenderTimeout=PASS terminalNoRewrite=PASS seedProcess={} verifyProcess={}", record.getString("process"), process());
    }

    private interface Attempt { void run() throws Exception; }
    private static void refuse(Attempt action) throws Exception {
        try { action.run(); } catch (IOException | IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("Expected guarded session restart refusal");
    }
    private static void refuseOpen(Model env) throws Exception {
        refuse(() -> { try (Session unexpected = SiegeSession38.open(env)) {
            throw new AssertionError("Changed contract/profile opened existing siege history");
        }});
    }
    private static String process() { return ProcessHandle.current().pid() + ":" + ManagementFactory.getRuntimeMXBean().getStartTime(); }
    private static byte[] journalBytes(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file) || Files.size(file) > 256 * 1024) {
            throw new IOException("Missing or invalid retained restart evidence");
        }
        return Files.readAllBytes(file);
    }
    private static final class Model implements Environment {
        final ServerLevel level; final Contract contract; final Profile profile; final Path root;
        final UUID owner = DEFENDERS;
        long now = 100;
        int defendersInside;
        Model(ServerLevel level, Contract contract, Profile profile) throws IOException {
            this.level = level; this.contract = contract; this.profile = profile;
            this.root = level.getServer().getWorldPath(LevelResource.ROOT).toRealPath();
            controlled();
        }
        private void controlled() throws IOException {
            if (!(level.getServer() instanceof GameTestServer) || !level.getServer().isSameThread()
                    || !root.getFileName().toString().equals("world")
                    || !root.getParent().getFileName().toString().equals("controlled-" + NS + "-run")
                    || !Files.readString(root.getParent().resolve(".catancraft-controlled-fixture-v1"))
                        .equals("CatanCraft synthetic flat fixture v1\n")
                    || !DIM.equals(level.dimension().location().toString()) || level.getSeed() != 0) {
                throw new IOException("Restart model requires its isolated controlled native world");
            }
        }
        public ServerLevel level() { return level; }
        public Contract contract() { return contract; }
        public Profile profile() { return profile; }
        public long gameTime() { return now; }
        public void guard(Action action, Snapshot state) throws IOException {
            controlled();
            if (!owner.equals(contract.defenders()) || !state.contract().equals(contract)) throw new IOException("Synthetic owner/contract changed");
        }
        public Report observe(Snapshot state) throws IOException {
            guard(Action.READ, state);
            return new Report(now, profile, contract, state.elapsedTicks(), state.activeStage(), 1, 1, 1, defendersInside);
        }
    }
}
