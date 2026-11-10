package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.world.SiegeRestoration39.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.*;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;

/** One restoration scenario in two independent Java processes against the same
 * marked disposable world. The verify phase never reseeds, copies or clears the
 * fixture. Authorization and the damage mask are synthetic trusted inputs, not
 * a claim that a live siege controller or weapon hook is registered. */
@GameTestHolder("catancraft_snapshot39_restart") @PrefixGameTestTemplate(false)
public final class SiegeRestoration39RestartTests {
    private static final String NS = "catancraft_snapshot39_restart";
    private static final String FIXTURE = "synthetic-snapshot39-restart-v1";
    private static final String CITY = "fixture39_restart";
    private static final String DIM = "minecraft:overworld";
    private static final UUID SIEGE = UUID.fromString("39000000-0000-0000-0000-000000000001");
    private static final UUID OWNER = UUID.fromString("39000000-0000-0000-0000-000000000002");
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final BlockPos ORIGIN = new BlockPos(4096, 140, 4096);
    private static final BlockPos BARREL = ORIGIN.offset(3, 0, 1);
    private static final List<BlockState> ORIGINAL = List.of(
            Blocks.STONE_BRICKS.defaultBlockState(), Blocks.IRON_BLOCK.defaultBlockState(),
            Blocks.WHITE_CONCRETE.defaultBlockState(), Blocks.STONE.defaultBlockState(),
            Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), Blocks.BRICKS.defaultBlockState(),
            Blocks.SMOOTH_STONE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState());
    private SiegeRestoration39RestartTests() {}

    @GameTest(template="townhall_state_empty", timeoutTicks=5000)
    public static void partial_physical_restoration_recovers_in_a_distinct_jvm(GameTestHelper h) throws Exception {
        String phase = System.getProperty("catancraft.snapshotRestartPhase", "");
        h.assertTrue(phase.equals("seed") || phase.equals("verify"), "Explicit seed|verify JVM phase required");
        Model env = new Model(h.getLevel());
        Path proof = env.root.resolve("data/catancraft_snapshot39_restart_seed.dat");
        Path verified = env.root.resolve("data/catancraft_snapshot39_restart_verified.dat");
        h.assertTrue(!Files.exists(verified, LinkOption.NOFOLLOW_LINKS), "Already-verified fixture must not be reused");
        ready(h, env, phase, proof, verified, 0);
    }

    private static void ready(GameTestHelper h, Model env, String phase, Path proof, Path verified, int attempts) {
        h.runAfterDelay(attempts == 0 ? 10 : 1, () -> {
            try {
                env.lease.keepAlive();
                if (!env.lease.ready()) {
                    if (attempts == 400 || attempts == 4000) CatanCraft.LOGGER.warn(
                            "SNAPSHOT39_RESTART_WAIT phase={} attempts={} pending={}", phase, attempts, env.pendingChunks());
                    if (attempts >= 4000) throw new IOException("Restart fixture chunks/entities never became resident: " + env.pendingChunks());
                    ready(h, env, phase, proof, verified, attempts + 1); return;
                }
                StreetscapeService.requireLoadedEntities(env.level, List.of(SiegeRestoration39.bounds(env.plan)));
                CatanCraft.LOGGER.info("SNAPSHOT39_RESTART_READY phase={} attempts={} chunks={}", phase, attempts, env.lease.chunkCount());
                try {
                    if (phase.equals("seed")) seed(h, env, proof);
                    else verify(h, env, proof, verified);
                    h.succeed();
                } finally { env.lease.close(); }
            } catch (Exception error) {
                env.lease.close(); throw new RuntimeException(error);
            }
        });
    }

    private static void seed(GameTestHelper h, Model env, Path proof) throws Exception {
        Path snapshot = SiegeRestoration39.snapshotPath(env), journal = SiegeRestoration39.journalPath(env);
        h.assertTrue(!Files.exists(proof, LinkOption.NOFOLLOW_LINKS)
                && !Files.exists(snapshot, LinkOption.NOFOLLOW_LINKS) && !Files.exists(journal, LinkOption.NOFOLLOW_LINKS),
                "Seed refuses existing proof, snapshot or restoration history");
        env.seedFixture();
        CompoundTag barrelBefore = env.barrelNbt();
        View partial;
        byte[] originalSnapshot;
        try (Session session = SiegeRestoration39.capture(env, env.plan)) {
            h.assertTrue(session.read().phase() == Phase.CAPTURED && session.read().missing() == 0,
                    "Capture did not preserve an intact native source");
            originalSnapshot = bytes(snapshot);
            h.assertTrue(session.arm(Set.copyOf(env.cells)).phase() == Phase.ARMED,
                    "Explicit damage mask did not become durable before fixture damage");
            // Test-only damage; this does not implement or authorize a weapon hook.
            for (BlockPos p : env.cells) if (!env.level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS)) {
                throw new IOException("Unable to create test-only structural damage at " + p);
            }
            h.assertTrue(session.read().missing() == 8 && env.barrelNbt().equals(barrelBefore),
                    "Synthetic damage changed protected inventory or omitted a structural cell");
            h.assertTrue(session.startRestore().phase() == Phase.RESTORING, "Restore intent was not durable");
            h.assertTrue(session.step() == 4 && !session.done(), "First slice was not exactly four physical writes");
            partial = session.read();
            h.assertTrue(partial.phase() == Phase.RESTORING && partial.missing() == 4,
                    "Partial restore lost its remaining work");
        }
        env.assertBlocks(h, 4);
        env.assertInventory(h, barrelBefore);
        // Actual chunks, block entities and level data are persisted before the process exits.
        env.flush();
        env.assertBlocks(h, 4); env.assertInventory(h, barrelBefore);
        h.assertTrue(Arrays.equals(originalSnapshot, bytes(snapshot)), "Partial restore modified its immutable snapshot");
        byte[] partialJournal = bytes(journal);
        CompoundTag record = new CompoundTag();
        record.putInt("schema", 1); record.putString("namespace", NS); record.putString("fixture", FIXTURE);
        record.putString("worldRoot", env.root.toString()); record.putString("dimension", DIM); record.putLong("seed", env.level.getSeed());
        record.putString("process", process()); record.putUUID("siege", SIEGE); record.putUUID("owner", OWNER);
        record.putString("snapshotFile", snapshot.getFileName().toString()); record.putString("journalFile", journal.getFileName().toString());
        record.putString("snapshotSHA256", TownHallRevisions.sha(originalSnapshot)); record.putByteArray("snapshotBytes", originalSnapshot);
        record.putString("journalSHA256", TownHallRevisions.sha(partialJournal)); record.putByteArray("journalBytes", partialJournal);
        record.putString("phase", partial.phase().name()); record.putLong("slice", partial.slice());
        record.putInt("restored", 4); record.putInt("missing", 4); record.putLong("barrelPos", BARREL.asLong());
        record.put("barrelNBT", barrelBefore.copy()); record.put("originalCells", env.cellStates(8));
        record.put("partialCells", env.cellStates(4));
        ProducerJournal.write(proof, record, false);
        h.assertTrue(Arrays.equals(originalSnapshot, bytes(snapshot)) && Arrays.equals(partialJournal, bytes(journal)),
                "Independent seed proof changed restoration history");
        CatanCraft.LOGGER.info("SNAPSHOT39_RESTART_SEED_PASS restored=4 missing=4 originalSnapshot=PASS protectedInventory=PASS worldSaved=PASS process={} restartVerification=PENDING", process());
    }

    private static void verify(GameTestHelper h, Model env, Path proof, Path verified) throws Exception {
        Path snapshot = SiegeRestoration39.snapshotPath(env), journal = SiegeRestoration39.journalPath(env);
        byte[] proofBytes = bytes(proof);
        CompoundTag record = ProducerJournal.read(proofBytes);
        h.assertTrue(record.getInt("schema") == 1 && NS.equals(record.getString("namespace"))
                && FIXTURE.equals(record.getString("fixture")) && env.root.toString().equals(record.getString("worldRoot"))
                && DIM.equals(record.getString("dimension")) && record.getLong("seed") == env.level.getSeed()
                && SIEGE.equals(record.getUUID("siege")) && OWNER.equals(record.getUUID("owner"))
                && snapshot.getFileName().toString().equals(record.getString("snapshotFile"))
                && journal.getFileName().toString().equals(record.getString("journalFile")), "Foreign restart proof/world binding");
        h.assertTrue(!record.getString("process").isBlank() && !process().equals(record.getString("process")),
                "Verification must run in a distinct Java process");
        h.assertTrue(record.getString("phase").equals(Phase.RESTORING.name()) && record.getLong("slice") >= 0
                && record.getInt("restored") == 4 && record.getInt("missing") == 4 && record.getLong("barrelPos") == BARREL.asLong()
                && record.getList("originalCells", Tag.TAG_COMPOUND).equals(env.cellStates(8))
                && record.getList("partialCells", Tag.TAG_COMPOUND).equals(env.cellStates(4)),
                "Seed evidence is not the independently expected eight-cell partial restoration");
        byte[] originalSnapshot = bytes(snapshot), partialJournal = bytes(journal);
        h.assertTrue(Arrays.equals(originalSnapshot, record.getByteArray("snapshotBytes"))
                && TownHallRevisions.sha(originalSnapshot).equals(record.getString("snapshotSHA256")),
                "Original pre-siege snapshot bytes changed across JVM restart");
        h.assertTrue(Arrays.equals(partialJournal, record.getByteArray("journalBytes"))
                && TownHallRevisions.sha(partialJournal).equals(record.getString("journalSHA256")),
                "Partial restoration journal bytes changed across JVM restart");
        // Read the original saved physical blocks and actual reloaded inventory BEFORE opening recovery.
        env.assertBlocks(h, 4); env.assertInventory(h, record.getCompound("barrelNBT"));
        View terminal;
        try (Session session = SiegeRestoration39.open(env, env.plan)) {
            View recovered = session.read();
            h.assertTrue(recovered.phase() == Phase.RESTORING && recovered.missing() == 4 && recovered.slice() == record.getLong("slice"),
                    "Restart did not recover the original partial restore intent");
            h.assertTrue(Arrays.equals(partialJournal, bytes(journal)), "Opening recovery rewrote the saved partial journal");
            h.assertTrue(session.step() == 4 && session.done(), "Recovery rewrote completed cells or omitted remaining cells");
            terminal = session.read();
            h.assertTrue(terminal.phase() == Phase.DONE && terminal.missing() == 0, "Restoration did not finish exactly");
            env.assertBlocks(h, 8); env.assertInventory(h, record.getCompound("barrelNBT"));
            byte[] finished = bytes(journal); int saves = env.flushes;
            h.assertTrue(session.step() == 0 && session.startRestore().equals(terminal), "Completed restoration was not inert");
            h.assertTrue(env.flushes == saves && Arrays.equals(finished, bytes(journal)), "Completed retry saved or revised history");
        }
        byte[] finished = bytes(journal); int saves = env.flushes;
        try (Session repeated = SiegeRestoration39.open(env, env.plan)) {
            h.assertTrue(repeated.read().equals(terminal) && repeated.done() && repeated.step() == 0,
                    "Another open replayed completed physical restoration");
        }
        h.assertTrue(env.flushes == saves && Arrays.equals(finished, bytes(journal))
                && Arrays.equals(originalSnapshot, bytes(snapshot)) && Arrays.equals(proofBytes, bytes(proof)),
                "Repeat recovery changed saves, immutable snapshot, terminal journal or seed proof");
        env.assertBlocks(h, 8); env.assertInventory(h, record.getCompound("barrelNBT"));
        CompoundTag receipt = new CompoundTag();
        receipt.putInt("schema", 1); receipt.putString("fixture", FIXTURE); receipt.putUUID("siege", SIEGE);
        receipt.putString("seedProcess", record.getString("process")); receipt.putString("verifyProcess", process());
        receipt.putString("seedProofSHA256", TownHallRevisions.sha(proofBytes));
        receipt.putString("snapshotSHA256", TownHallRevisions.sha(originalSnapshot));
        receipt.putString("seedJournalSHA256", TownHallRevisions.sha(partialJournal));
        receipt.putString("terminalJournalSHA256", TownHallRevisions.sha(finished));
        receipt.putString("phase", terminal.phase().name()); receipt.putLong("slice", terminal.slice());
        receipt.putInt("recoveredWrites", 4); receipt.putInt("restored", 8); receipt.putInt("missing", 0);
        receipt.put("restoredCells", env.cellStates(8)); receipt.put("barrelNBT", env.barrelNbt());
        receipt.putString("result", "PASS"); ProducerJournal.write(verified, receipt, false);
        CatanCraft.LOGGER.info("SNAPSHOT39_RESTART_VERIFY_PASS distinctJVM=PASS originalSnapshotBytes=PASS originalJournalBytes=PASS savedPartialWorld=PASS recoveredWrites=4 restored=8 protectedInventory=PASS terminalNoRewrite=PASS seedProcess={} verifyProcess={}", record.getString("process"), process());
    }

    private static String process() { return ProcessHandle.current().pid() + ":" + ManagementFactory.getRuntimeMXBean().getStartTime(); }
    private static byte[] bytes(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file) || Files.size(file) > 4 * 1024 * 1024) {
            throw new IOException("Missing or invalid retained snapshot restart evidence");
        }
        return Files.readAllBytes(file);
    }

    private static final class Model implements Environment {
        final ServerLevel level; final Path root; final List<BlockPos> cells; final Plan plan; final SectorChunkLease lease;
        int flushes;
        Model(ServerLevel level) throws IOException {
            this.level = level; this.root = level.getServer().getWorldPath(LevelResource.ROOT).toRealPath(); controlled();
            List<BlockPos> positions = new ArrayList<>();
            for (int x = 0; x < 8; x++) positions.add(ORIGIN.offset(x, 0, 0));
            cells = List.copyOf(positions);
            Set<BlockPos> reads = new HashSet<>();
            for (BlockPos p : cells) for (Direction direction : Direction.values()) if (!cells.contains(p.relative(direction))) reads.add(p.relative(direction));
            reads.add(BARREL); reads.add(BARREL.below());
            plan = new Plan(FIXTURE, cells, reads.stream().sorted(Comparator.comparingLong(BlockPos::asLong)).toList());
            lease = SectorChunkLease.acquire(level, SiegeRestoration39.bounds(plan));
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
        void seedFixture() throws IOException {
            controlled();
            Set<BlockPos> footprint = new HashSet<>(plan.reads()); footprint.addAll(cells);
            for (BlockPos p : footprint) if (!level.getBlockState(p).is(Blocks.AIR) || level.getBlockEntity(p) != null) {
                throw new IOException("Seed refuses a preexisting fixture block at " + p);
            }
            for (int i = 0; i < cells.size(); i++) {
                level.setBlock(cells.get(i).below(), Blocks.STONE.defaultBlockState(), FLAGS);
                level.setBlock(cells.get(i), ORIGINAL.get(i), FLAGS);
            }
            level.setBlock(BARREL.below(), Blocks.STONE.defaultBlockState(), FLAGS);
            level.setBlock(BARREL, Blocks.BARREL.defaultBlockState(), FLAGS);
            BarrelBlockEntity barrel = barrel();
            barrel.setItem(0, new ItemStack(Items.EMERALD, 13)); barrel.setItem(5, new ItemStack(Items.BOOK, 2)); barrel.setChanged();
        }
        BarrelBlockEntity barrel() throws IOException {
            if (!(level.getBlockEntity(BARREL) instanceof BarrelBlockEntity barrel)) throw new IOException("Protected fixture barrel is missing");
            return barrel;
        }
        CompoundTag barrelNbt() throws IOException { return barrel().saveWithFullMetadata().copy(); }
        void assertInventory(GameTestHelper h, CompoundTag expected) throws IOException {
            BarrelBlockEntity barrel = barrel();
            h.assertTrue(barrelNbt().equals(expected) && barrel.getItem(0).is(Items.EMERALD) && barrel.getItem(0).getCount() == 13
                    && barrel.getItem(5).is(Items.BOOK) && barrel.getItem(5).getCount() == 2,
                    "Protected inventory/full block-entity NBT changed or was reconstructed");
        }
        void assertBlocks(GameTestHelper h, int restored) {
            for (int i = 0; i < cells.size(); i++) h.assertTrue(level.getBlockState(cells.get(i)).equals(i < restored ? ORIGINAL.get(i) : Blocks.AIR.defaultBlockState()),
                    "Saved physical fixture mismatch at " + cells.get(i) + "; expected restored count " + restored);
        }
        ListTag cellStates(int restored) {
            ListTag rows = new ListTag();
            for (int i = 0; i < cells.size(); i++) {
                CompoundTag row = new CompoundTag(); row.putLong("pos", cells.get(i).asLong());
                row.put("state", NbtUtils.writeBlockState(i < restored ? ORIGINAL.get(i) : Blocks.AIR.defaultBlockState())); rows.add(row);
            }
            return rows;
        }
        List<String> pendingChunks() {
            List<String> result = new ArrayList<>();
            for (var p : lease.positions()) {
                boolean chunk = level.getChunkSource().getChunkNow(p.x, p.z) != null, entities = level.areEntitiesLoaded(p.toLong());
                if (!chunk || !entities) result.add(p + ":chunk=" + chunk + ",entities=" + entities);
            }
            return List.copyOf(result);
        }
        public ServerLevel level() { return level; }
        public String worldId() { return FIXTURE; }
        public String city() { return CITY; }
        public UUID siege() { return SIEGE; }
        public UUID owner() { return OWNER; }
        public void guard(Action action) throws IOException { controlled(); }
        public void flush() throws IOException {
            controlled();
            if (!level.getServer().saveEverything(true, true, true)) throw new IOException("Native snapshot fixture world save failed");
            flushes++;
        }
    }
}
