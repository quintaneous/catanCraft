package com.quin.catancraft.world;

import com.quin.catancraft.world.SiegeObjectives37.*;
import com.quin.catancraft.world.SiegeRules36.*;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** Durable objective state, NOT a damage snapshot or ownership settlement. No
 * commands, live catalog, tick subscriber or automatic recovery are registered.
 * A trusted server controller must authorize every action and supply the original
 * approved contract/profile independently of these files. */
final class SiegeSession38 {
    enum Action { OPEN, CREATE, READ, RESUME, TICK, SURRENDER }
    enum SavePoint { BEFORE_WRITE, AFTER_WRITE }
    interface Authorization { void check(Action action, Snapshot state) throws IOException; }
    /** Trusted server adapter; guard/observe must be read-only. The environment
     * and its presence flags must never be supplied by a client/network request. */
    interface Environment {
        ServerLevel level();
        Contract contract();
        Profile profile();
        long gameTime();
        void guard(Action action, Snapshot state) throws IOException;
        Report observe(Snapshot state) throws IOException;
    }
    interface Fault { void at(SavePoint point) throws IOException; }
    record View(long revision, Snapshot snapshot, boolean recoveryRequired) {}
    private static final Fault NONE = point -> {};
    private static final int MAX_FILE = 256 * 1024;
    private static final Set<String> KEYS = Set.of("schema", "identity", "profile", "state", "revision", "epoch", "lastTick");

    /** Production adapter always obtains presence from the real server observer.
     * Authorization must resolve the actual actor/role, not trust a nation UUID. */
    static Environment serverEnvironment(Bound bound, Authorization authorization) {
        Objects.requireNonNull(bound); Objects.requireNonNull(authorization);
        ServerLevel level = bound.level();
        return new Environment() {
            public ServerLevel level() { return level; }
            public Contract contract() { return bound.contract(); }
            public Profile profile() { return bound.profile(); }
            public long gameTime() { return level.getGameTime(); }
            public void guard(Action action, Snapshot state) throws IOException { authorization.check(action, state); }
            public Report observe(Snapshot state) throws IOException { return SiegeObjectives37.sample(bound, state); }
        };
    }

    static Session create(Environment env) throws IOException { return acquire(env, true, NONE); }
    static Session open(Environment env) throws IOException { return acquire(env, false, NONE); }

    /** Fault injection is restricted to marked disposable session38 GameTest worlds. */
    static Session fixture(Environment env, boolean create, Fault fault) throws IOException {
        thread(env);
        Path root = env.level().getServer().getWorldPath(LevelResource.ROOT).toRealPath();
        if (!(env.level().getServer() instanceof GameTestServer) || !root.getFileName().toString().equals("world")
                || !Set.of("controlled-catancraft_session38-run", "controlled-catancraft_session38_restart-run")
                    .contains(root.getParent().getFileName().toString())
                || !Files.readString(root.getParent().resolve(".catancraft-controlled-fixture-v1"))
                    .equals("CatanCraft synthetic flat fixture v1\n")) throw new IOException("Controlled session fixture required");
        return acquire(env, create, Objects.requireNonNull(fault));
    }

    static Path journalPath(Environment env) throws IOException {
        Objects.requireNonNull(env.contract());
        Path root = env.level().getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path file = root.resolve("data/catancraft_siege38_" + env.contract().city() + ".dat");
        safePath(file); return file;
    }
    private static void safePath(Path path) throws IOException {
        Path walk = path.getRoot();
        for (Path part : path) {
            walk = walk.resolve(part);
            if (Files.isSymbolicLink(walk)) throw new IOException("Siege path contains a symlink");
            if (!walk.equals(path) && Files.exists(walk, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isDirectory(walk, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Siege path ancestor is not a directory");
        }
    }
    private static void thread(Environment env) throws IOException {
        if (!env.level().getServer().isSameThread()) throw new IOException("Siege state requires server thread");
    }
    private static Snapshot initial(Contract contract) { return new Snapshot(contract, Status.OUTER, 0, 0, 0); }
    private static CompoundTag profileTag(Profile p) {
        CompoundTag n = new CompoundTag(); n.putString("city", p.city()); n.putString("revision", p.revision());
        n.putString("dimension", p.dimension()); n.putIntArray("outer", coordinates(p.outer()));
        n.putIntArray("townHall", coordinates(p.townHall())); return n;
    }
    private static int[] coordinates(Box b) { return new int[]{b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()}; }
    private static CompoundTag identity(Environment env) throws IOException {
        Path file = journalPath(env); Profile profile = Objects.requireNonNull(env.profile());
        if (!env.contract().city().equals(profile.city())
                || !env.level().dimension().location().toString().equals(profile.dimension())
                || env.level().getServer().getLevel(env.level().dimension()) != env.level()) throw new IOException("Foreign city/level profile");
        for (Box b : List.of(profile.outer(), profile.townHall())) {
            if (b.minY() < env.level().getMinBuildHeight() || b.maxY() > env.level().getMaxBuildHeight()) throw new IOException("Objective exceeds level height");
        }
        CompoundTag n = new CompoundTag(); n.putString("root", file.getParent().getParent().toRealPath().toString());
        n.putString("world", env.contract().world()); n.putString("dimension", profile.dimension());
        n.putLong("seed", env.level().getSeed()); n.putString("city", profile.city()); n.putString("file", file.getFileName().toString()); return n;
    }
    private static byte[] capture(Path file) throws IOException {
        safePath(file);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_FILE) throw new IOException("Invalid siege journal file");
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length > MAX_FILE) throw new IOException("Oversized siege journal");
        return bytes;
    }

    private static Session acquire(Environment env, boolean create, Fault fault) throws IOException {
        Objects.requireNonNull(env); thread(env); Contract contract = Objects.requireNonNull(env.contract());
        env.guard(create ? Action.CREATE : Action.OPEN, initial(contract));
        CompoundTag id = identity(env), profile = profileTag(env.profile()); Path file = journalPath(env);
        Path lockPath = file.resolveSibling(file.getFileName() + ".lock"); safePath(lockPath);
        Files.createDirectories(file.getParent());
        FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        FileLock lock = null;
        try {
            if (!Files.isRegularFile(lockPath, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Invalid siege lock file");
            try { lock = channel.tryLock(); } catch (OverlappingFileLockException busy) { throw new IOException("City already has a siege state writer", busy); }
            if (lock == null) throw new IOException("City already has a siege state writer");
            Session s = new Session(env, contract, env.profile(), id, profile, file, lockPath, channel, lock, fault);
            if (create) {
                if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Retained siege history cannot be replaced");
                s.snapshot = initial(contract); s.revision = -1;
                Report report = s.observation();
                if (!report.bothNationsOnline()) throw new IOException("Both nations must be online to begin");
                s.guard(Action.CREATE); s.persist(s.snapshot, 0, report.gameTime(), Action.CREATE); s.ready = true;
            } else {
                s.bytes = capture(file); s.load(ProducerJournal.read(s.bytes));
                s.guard(Action.OPEN);
                // Reopening NEVER grants permission to continue or invents downtime ticks.
                s.ready = false;
            }
            return s;
        } catch (IOException | RuntimeException failure) {
            try { if (lock != null && lock.isValid()) lock.release(); } catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
            try { channel.close(); } catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
    }

    static final class Session implements AutoCloseable {
        private final Environment env;
        private final ServerLevel level;
        private final Contract contract;
        private final Profile profile;
        private final CompoundTag id, spec;
        private final Path file, lockPath;
        private final FileChannel channel;
        private final FileLock lock;
        private final Fault fault;
        private final Object lockKey;
        private UUID epoch = UUID.randomUUID();
        private byte[] bytes;
        private Snapshot snapshot;
        private long revision, lastTick;
        private boolean ready, closed;
        private Session(Environment env, Contract contract, Profile profile, CompoundTag id, CompoundTag spec,
                        Path file, Path lockPath, FileChannel channel, FileLock lock, Fault fault) throws IOException {
            this.env = env; this.level = env.level(); this.contract = contract; this.profile = profile; this.id = id; this.spec = spec;
            this.file = file; this.lockPath = lockPath; this.channel = channel; this.lock = lock; this.fault = fault;
            this.lockKey = Files.readAttributes(lockPath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).fileKey();
            if (lockKey == null) throw new IOException("Siege storage requires stable lock-file identity");
        }
        private void load(CompoundTag n) throws IOException {
            if (!n.getAllKeys().equals(KEYS) || !n.contains("schema", Tag.TAG_INT) || n.getInt("schema") != 1
                    || !n.contains("identity", Tag.TAG_COMPOUND) || !n.getCompound("identity").equals(id)
                    || !n.contains("profile", Tag.TAG_COMPOUND) || !n.getCompound("profile").equals(spec)
                    || !n.contains("state", Tag.TAG_COMPOUND) || !n.contains("revision", Tag.TAG_LONG)
                    || !n.contains("lastTick", Tag.TAG_LONG) || !n.hasUUID("epoch")
                    || n.getUUID("epoch").equals(new UUID(0,0)) || n.getLong("lastTick") < 0) throw new IOException("Foreign or malformed siege journal");
            Snapshot parsed;
            try { parsed = SiegeRules36.load(n.getCompound("state"), contract); }
            catch (RuntimeException invalid) { throw new IOException("Invalid stored siege state", invalid); }
            if (n.getLong("revision") < parsed.elapsedTicks()) throw new IOException("Invalid siege revision");
            snapshot = parsed; revision = n.getLong("revision"); lastTick = n.getLong("lastTick");
        }
        private void sameFile() throws IOException {
            if (bytes == null) {
                if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Siege journal appeared during preparation");
            } else if (!Arrays.equals(bytes, capture(file))) throw new IOException("Siege journal changed since last observation");
        }
        private void guard(Action action) throws IOException {
            thread(env);
            if (closed || !lock.isValid() || env.level() != level) throw new IOException("Closed siege handle; reopen and revalidate");
            safePath(lockPath);
            if (!Files.isRegularFile(lockPath, LinkOption.NOFOLLOW_LINKS)
                    || !lockKey.equals(Files.readAttributes(lockPath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).fileKey())
                    || !contract.equals(env.contract()) || !profile.equals(env.profile()) || !identity(env).equals(id)
                    || !journalPath(env).equals(file)) throw new IOException("Siege world/profile identity changed");
            env.guard(action, snapshot); sameFile();
        }
        private void expected(long expected) throws IOException {
            if (expected != revision) throw new IOException("Stale siege revision");
            if (revision == Long.MAX_VALUE) throw new IOException("Siege revision exhausted");
        }
        private Report observation() throws IOException {
            Report r = Objects.requireNonNull(env.observe(snapshot)); long now = env.gameTime();
            if (now < 0 || r.gameTime() != now || !r.contract().equals(contract) || !r.profile().equals(profile)
                    || r.elapsedTicks() != snapshot.elapsedTicks() || r.objective() != snapshot.activeStage()
                    || r.attackersOnline() < 0 || r.defendersOnline() < 0 || r.attackersInside() < 0 || r.defendersInside() < 0
                    || r.attackersInside() > r.attackersOnline() || r.defendersInside() > r.defendersOnline()) {
                throw new IOException("Foreign, stale or impossible objective observation");
            }
            return r;
        }
        private View result() { return new View(revision, snapshot, !ready && !snapshot.terminal()); }
        View read() throws IOException { guard(Action.READ); return result(); }

        /** Explicit trusted-controller recovery gate. Keeps progress/time unchanged,
         * establishes a fresh observation epoch, and never authorizes siege damage.
         * A deployment must separately decide offline/downtime policy before using it. */
        View resume(long expectedRevision) throws IOException {
            guard(Action.RESUME); expected(expectedRevision);
            if (snapshot.terminal()) return result();
            if (ready) throw new IOException("Running siege does not need recovery");
            Report report = observation(); guard(Action.RESUME);
            epoch = UUID.randomUUID();
            persist(snapshot, revision + 1, report.gameTime(), Action.RESUME); ready = true; return result();
        }
        View tick(long expectedRevision) throws IOException {
            guard(Action.TICK); expected(expectedRevision);
            if (snapshot.terminal()) return result();
            if (!ready) throw new IOException("Recovered siege requires explicit revalidation");
            long now = env.gameTime();
            if (lastTick == Long.MAX_VALUE || now != lastTick + 1) {
                // Duplicates cannot mint progress; a skipped/backwards clock needs a new recovery gate.
                if (now != lastTick) ready = false;
                throw new IOException("Duplicate, skipped or backwards server observation tick");
            }
            Report report = observation(); requireTime(now);
            if (report.gameTime() != now) { ready = false; throw new IOException("Observation frame changed"); }
            Snapshot next = SiegeRules36.tick(snapshot, snapshot.elapsedTicks(), report.observation());
            guard(Action.TICK); persist(next, revision + 1, now, Action.TICK); return result();
        }
        View surrender(long expectedRevision, UUID requestingNation) throws IOException {
            guard(Action.SURRENDER); expected(expectedRevision);
            if (!contract.attackers().equals(requestingNation)) throw new IOException("Only attackers may surrender");
            if (snapshot.terminal()) return result();
            if (!ready) throw new IOException("Recovered siege requires explicit revalidation");
            long now = env.gameTime();
            if (now != lastTick && (lastTick == Long.MAX_VALUE || now != lastTick + 1)) {
                ready = false; throw new IOException("Changed server clock requires recovery before surrender");
            }
            Report report = observation(); requireTime(now);
            if (report.gameTime() != now) { ready = false; throw new IOException("Observation frame changed"); }
            Snapshot next = SiegeRules36.surrender(snapshot, requestingNation);
            guard(Action.SURRENDER); persist(next, revision + 1, report.gameTime(), Action.SURRENDER); return result();
        }
        private void requireTime(long expectedTime) throws IOException {
            if (env.gameTime() != expectedTime) { ready = false; throw new IOException("Server clock changed during observation/save"); }
        }
        private void persist(Snapshot next, long nextRevision, long tick, Action action) throws IOException {
            try {
                requireTime(tick);
                if (tick < 0 || !identity(env).equals(id) || !contract.equals(env.contract()) || !profile.equals(env.profile())) throw new IOException("Changed siege binding before save");
                CompoundTag n = new CompoundTag(); n.putInt("schema", 1); n.put("identity", id.copy()); n.put("profile", spec.copy());
                n.put("state", SiegeRules36.save(next)); n.putLong("revision", nextRevision); n.putUUID("epoch", epoch); n.putLong("lastTick", tick);
                fault.at(SavePoint.BEFORE_WRITE); guard(action); requireTime(tick);
                ProducerJournal.write(file, n, bytes != null);
                fault.at(SavePoint.AFTER_WRITE);
                byte[] written = capture(file);
                if (!ProducerJournal.read(written).equals(n)) throw new IOException("Siege write readback disagrees");
                // Publish only after the completed durable write/readback. On ambiguous failure,
                // close; a new handle reads whichever complete disk state actually survived.
                load(n); bytes = written;
            } catch (IOException | RuntimeException failure) {
                try { close(); } catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
                throw failure;
            }
        }
        @Override public void close() throws IOException {
            if (!closed) { closed = true; ready = false; try { if (lock.isValid()) lock.release(); } finally { channel.close(); } }
        }
    }
    private SiegeSession38() {}
}
