package com.quin.catancraft.world;

import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;

import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** Bounded immutable pre-siege structural snapshots. Unregistered: no live damage
 * permission, scheduler, commands, inventory rollback or ownership settlement.
 * The trusted controller must independently approve the plan and exclusively
 * freeze city construction; RESTORE additionally requires damage to have ended.
 * Protection/drop/physics policy for a future weapon bridge is NOT supplied here. */
final class SiegeRestoration39 {
    enum Action { CAPTURE, OPEN, READ, ARM, RESTORE }
    enum Phase { CAPTURED, ARMED, RESTORING, DONE }
    enum SavePoint { BEFORE_SNAPSHOT, AFTER_SNAPSHOT, BEFORE_JOURNAL, AFTER_JOURNAL, AFTER_BLOCK, AFTER_WORLD_SAVE }
    interface Environment {
        ServerLevel level(); String worldId(); String city(); UUID siege(); UUID owner();
        /** Read-only current authority, phase, plan approval and construction exclusion. */
        void guard(Action action) throws IOException;
        /** Persist affected chunks/block entities before a progress receipt is written. */
        void flush() throws IOException;
    }
    interface Fault { void at(SavePoint point) throws IOException; }
    record Plan(String revision, List<BlockPos> cells, List<BlockPos> reads) {
        Plan { cells = cells.stream().map(BlockPos::immutable).toList(); reads = reads.stream().map(BlockPos::immutable).toList(); }
    }
    record View(Phase phase, long slice, int missing) {}
    private record Original(BlockState state, CompoundTag nbt) {}
    private static final Fault NONE = p -> {};
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final int MAX_FILE = 8 * 1024 * 1024 + 40;
    private static final Set<Block> STRUCTURAL = Set.of(Blocks.STONE, Blocks.STONE_BRICKS,
            Blocks.CRACKED_STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.COBBLESTONE,
            Blocks.BRICKS, Blocks.ANDESITE, Blocks.POLISHED_ANDESITE, Blocks.SMOOTH_STONE,
            Blocks.IRON_BLOCK, Blocks.WHITE_CONCRETE);
    private SiegeRestoration39() {}

    static Session capture(Environment env, Plan plan) throws IOException { return acquire(env, plan, true, NONE); }
    static Session open(Environment env, Plan plan) throws IOException { return acquire(env, plan, false, NONE); }
    static Session fixture(Environment env, Plan plan, boolean create, Fault fault) throws IOException {
        thread(env);
        Path root = env.level().getServer().getWorldPath(LevelResource.ROOT).toRealPath();
        if (!(env.level().getServer() instanceof GameTestServer) || !root.getFileName().toString().equals("world")
                || !Set.of("controlled-catancraft_snapshot39-run", "controlled-catancraft_snapshot39_restart-run")
                    .contains(root.getParent().getFileName().toString())
                || !Files.readString(root.getParent().resolve(".catancraft-controlled-fixture-v1"))
                    .equals("CatanCraft synthetic flat fixture v1\n")) throw new IOException("Controlled snapshot fixture required");
        return acquire(env, plan, create, Objects.requireNonNull(fault));
    }
    static Path snapshotPath(Environment env) throws IOException { return path(env, "snapshot"); }
    static Path journalPath(Environment env) throws IOException { return path(env, "restore"); }
    private static Path path(Environment env, String kind) throws IOException {
        if (env.city() == null || !env.city().matches("[a-z0-9_]{1,48}")) throw new IOException("Invalid snapshot city");
        Path root = env.level().getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path p = root.resolve("data/catancraft_" + kind + "39_" + env.city() + ".dat"); safePath(p); return p;
    }
    private static void safePath(Path path) throws IOException {
        Path walk = path.getRoot();
        for (Path part : path) {
            walk = walk.resolve(part);
            if (Files.isSymbolicLink(walk)) throw new IOException("Snapshot path contains a symlink");
            if (!walk.equals(path) && Files.exists(walk, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isDirectory(walk, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Snapshot path ancestor is not a directory");
        }
    }
    private static void thread(Environment env) throws IOException {
        if (!env.level().getServer().isSameThread()) throw new IOException("Snapshot requires server thread");
    }
    private static List<BlockPos> ordered(Collection<BlockPos> positions) {
        return positions.stream().sorted(Comparator.comparingLong(BlockPos::asLong)).toList();
    }
    private static long[] positions(Collection<BlockPos> positions) { return ordered(positions).stream().mapToLong(BlockPos::asLong).toArray(); }
    private static AABB envelope(Plan plan) {
        AABB box = new AABB(plan.cells().get(0));
        for (BlockPos pos : plan.cells()) box = box.minmax(new AABB(pos));
        return box;
    }
    private static CompoundTag describe(Plan plan) throws IOException {
        if (plan.revision() == null || plan.revision().isBlank() || plan.revision().length() > 128
                || plan.cells().isEmpty() || plan.cells().size() > 512 || plan.reads().size() > 4096)
            throw new IOException("Invalid bounded snapshot plan");
        Set<BlockPos> all = new HashSet<>(plan.cells());
        if (all.size() != plan.cells().size()) throw new IOException("Duplicate snapshot cell");
        AABB box = envelope(plan);
        if (box.getXsize() > 64 || box.getYsize() > 32 || box.getZsize() > 64) throw new IOException("Snapshot exceeds structural envelope");
        for (BlockPos pos : plan.reads()) {
            if (!all.add(pos)) throw new IOException("Duplicate/overlapping snapshot read");
            if (!box.inflate(2).contains(pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5)) throw new IOException("Snapshot read outside bounded context");
        }
        for (BlockPos pos : plan.cells()) for (Direction d : Direction.values())
            if (!all.contains(pos.relative(d))) throw new IOException("Snapshot requires six-face protected context");
        CompoundTag n = new CompoundTag(); n.putString("revision", plan.revision());
        n.putLongArray("cells", positions(plan.cells())); n.putLongArray("reads", positions(plan.reads())); return n;
    }
    static AABB bounds(Plan plan) throws IOException { describe(plan); return envelope(plan).inflate(2); }
    private static CompoundTag identity(Environment env) throws IOException {
        if (env.worldId() == null || env.worldId().isBlank() || env.worldId().length() > 128
                || env.owner() == null || env.siege() == null || env.owner().equals(new UUID(0,0))
                || env.siege().equals(new UUID(0,0)) || env.level().getServer().getLevel(env.level().dimension()) != env.level())
            throw new IOException("Explicit current world/siege/owner binding required");
        Path file = snapshotPath(env); CompoundTag n = new CompoundTag();
        n.putString("root", file.getParent().getParent().toRealPath().toString()); n.putString("world", env.worldId());
        n.putString("city", env.city()); n.putString("dimension", env.level().dimension().location().toString());
        n.putLong("seed", env.level().getSeed()); n.putUUID("siege", env.siege()); n.putUUID("owner", env.owner()); return n;
    }
    private static byte[] bytes(Path file) throws IOException {
        safePath(file);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_FILE) throw new IOException("Invalid snapshot/progress file");
        byte[] result = Files.readAllBytes(file);
        if (result.length > MAX_FILE) throw new IOException("Oversized snapshot/progress file"); return result;
    }
    private static void same(Path path, byte[] expected) throws IOException {
        safePath(path);
        if (expected == null ? Files.exists(path, LinkOption.NOFOLLOW_LINKS) : !Arrays.equals(expected, bytes(path)))
            throw new IOException("Snapshot/progress history changed since observation");
    }
    private static Original current(ServerLevel level, BlockPos pos) throws IOException {
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(pos) || !level.hasChunkAt(pos)) throw new IOException("Unloaded/outside snapshot cell " + pos);
        BlockState state = level.getBlockState(pos);
        if (!state.getFluidState().isEmpty()) throw new IOException("Fluid in snapshot context " + pos);
        BlockEntity be = level.getBlockEntity(pos); CompoundTag nbt = new CompoundTag();
        if (state.hasBlockEntity()) {
            if (be == null || !(state.getBlock() instanceof EntityBlock block)) throw new IOException("Missing protected block entity");
            BlockEntity reference = block.newBlockEntity(pos, state);
            nbt = be.saveWithFullMetadata().copy();
            if (reference == null || !reference.saveWithFullMetadata().getString("id").equals(nbt.getString("id")))
                throw new IOException("Foreign protected block entity type");
        } else if (be != null) throw new IOException("Unexpected snapshot block entity");
        return new Original(state, nbt);
    }
    private static void structural(Original original) throws IOException {
        if (!STRUCTURAL.contains(original.state().getBlock()) || original.state().hasBlockEntity()
                || !original.nbt().isEmpty() || !original.state().getFluidState().isEmpty())
            throw new IOException("Only explicitly supported simple structural blocks may be restored");
    }
    private static Session acquire(Environment env, Plan plan, boolean create, Fault fault) throws IOException {
        Objects.requireNonNull(env); thread(env); CompoundTag spec = describe(plan); env.guard(create ? Action.CAPTURE : Action.OPEN);
        CompoundTag id = identity(env); Path file = snapshotPath(env), journal = journalPath(env);
        Path lockPath = file.resolveSibling(file.getFileName() + ".lock"); safePath(lockPath);
        Files.createDirectories(file.getParent());
        FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        FileLock lock = null;
        try {
            try { lock = channel.tryLock(); } catch (OverlappingFileLockException busy) { throw new IOException("City already has a snapshot writer", busy); }
            if (lock == null) throw new IOException("City already has a snapshot writer");
            Session s = new Session(env, plan, id, spec, file, journal, lockPath, channel, lock, fault);
            if (create) s.capture(); else s.load();
            s.guard(create ? Action.CAPTURE : Action.OPEN); s.world(); return s;
        } catch (IOException | RuntimeException failure) {
            try { if (lock != null && lock.isValid()) lock.release(); } catch (IOException e) { failure.addSuppressed(e); }
            try { channel.close(); } catch (IOException e) { failure.addSuppressed(e); }
            throw failure;
        }
    }

    static final class Session implements AutoCloseable {
        private final Environment env;
        private final ServerLevel level;
        private final Plan plan;
        private final CompoundTag id, spec;
        private final Path file, journal, lockPath;
        private final FileChannel channel;
        private final FileLock lock;
        private final Object lockKey;
        private final Fault fault;
        private final Map<BlockPos, Original> originals = new LinkedHashMap<>();
        private Set<BlockPos> damage = Set.of();
        private Phase phase = Phase.CAPTURED;
        private long slice;
        private byte[] snapshotBytes, journalBytes;
        private boolean closed;
        private Session(Environment env, Plan plan, CompoundTag id, CompoundTag spec, Path file, Path journal,
                        Path lockPath, FileChannel channel, FileLock lock, Fault fault) throws IOException {
            this.env=env; this.level=env.level(); this.plan=plan; this.id=id; this.spec=spec; this.file=file; this.journal=journal;
            this.lockPath=lockPath; this.channel=channel; this.lock=lock; this.fault=fault;
            lockKey=Files.readAttributes(lockPath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).fileKey();
            if (lockKey == null || !Files.isRegularFile(lockPath, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Stable regular lock file required");
        }
        private void guard(Action action) throws IOException {
            thread(env); safePath(lockPath);
            if (closed || !lock.isValid() || env.level()!=level || !Files.isRegularFile(lockPath, LinkOption.NOFOLLOW_LINKS)
                    || !lockKey.equals(Files.readAttributes(lockPath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).fileKey())
                    || !identity(env).equals(id) || !describe(plan).equals(spec) || !snapshotPath(env).equals(file)
                    || !journalPath(env).equals(journal)) throw new IOException("Closed or changed snapshot binding");
            env.guard(action); same(file, snapshotBytes); same(journal, journalBytes);
        }
        private List<BlockPos> all() { List<BlockPos> p = new ArrayList<>(plan.cells()); p.addAll(plan.reads()); return ordered(p); }
        private void capture() throws IOException {
            guard(Action.CAPTURE);
            StreetscapeService.requireLoadedEntities(level, List.of(bounds(plan)));
            for (BlockPos p : all()) originals.put(p, current(level,p));
            for (BlockPos p : plan.cells()) structural(originals.get(p));
            env.flush(); guard(Action.CAPTURE); world();
            ListTag cells = new ListTag();
            for (var entry : originals.entrySet()) {
                CompoundTag row = new CompoundTag(); row.putLong("pos",entry.getKey().asLong());
                row.put("state",NbtUtils.writeBlockState(entry.getValue().state())); row.put("nbt",entry.getValue().nbt().copy()); cells.add(row);
            }
            CompoundTag n = new CompoundTag(); n.putInt("schema",1); n.put("identity",id.copy()); n.put("plan",spec.copy()); n.put("originals",cells);
            fault.at(SavePoint.BEFORE_SNAPSHOT); guard(Action.CAPTURE); world();
            ProducerJournal.write(file,n,false); snapshotBytes=bytes(file);
            if (!ProducerJournal.read(snapshotBytes).equals(n)) throw new IOException("Snapshot readback differs");
            fault.at(SavePoint.AFTER_SNAPSHOT); guard(Action.CAPTURE); world();
        }
        private void load() throws IOException {
            snapshotBytes=bytes(file); CompoundTag n=ProducerJournal.read(snapshotBytes);
            if (!n.getAllKeys().equals(Set.of("schema","identity","plan","originals")) || !n.contains("schema",Tag.TAG_INT)
                    || n.getInt("schema")!=1 || !n.getCompound("identity").equals(id) || !n.getCompound("plan").equals(spec)
                    || !n.contains("originals",Tag.TAG_LIST)) throw new IOException("Foreign or malformed snapshot");
            ListTag rows=n.getList("originals",Tag.TAG_COMPOUND); List<BlockPos> positions=all();
            if (rows.size()!=positions.size()) throw new IOException("Incomplete original snapshot");
            for (int i=0;i<rows.size();i++) {
                CompoundTag row=rows.getCompound(i); BlockPos p=positions.get(i);
                if (!row.getAllKeys().equals(Set.of("pos","state","nbt")) || !row.contains("pos",Tag.TAG_LONG)
                        || row.getLong("pos")!=p.asLong() || !row.contains("state",Tag.TAG_COMPOUND)
                        || !row.contains("nbt",Tag.TAG_COMPOUND)) throw new IOException("Malformed original cell");
                BlockState state=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),row.getCompound("state"));
                if (!NbtUtils.writeBlockState(state).equals(row.getCompound("state")) || !state.getFluidState().isEmpty()
                        || state.hasBlockEntity()==row.getCompound("nbt").isEmpty()) throw new IOException("Invalid original state/NBT");
                originals.put(p,new Original(state,row.getCompound("nbt").copy()));
            }
            for (BlockPos p:plan.cells()) structural(originals.get(p));
            if (Files.exists(journal, LinkOption.NOFOLLOW_LINKS)) {
                journalBytes=bytes(journal); CompoundTag j=ProducerJournal.read(journalBytes);
                if (!j.getAllKeys().equals(Set.of("schema","identity","plan","snapshotSHA256","phase","damage","slice"))
                        || !j.contains("schema",Tag.TAG_INT) || j.getInt("schema")!=1 || !j.getCompound("identity").equals(id)
                        || !j.getCompound("plan").equals(spec) || !j.contains("snapshotSHA256",Tag.TAG_STRING)
                        || !j.getString("snapshotSHA256").equals(TownHallRevisions.sha(snapshotBytes))
                        || !j.contains("phase",Tag.TAG_STRING) || !j.contains("damage",Tag.TAG_LONG_ARRAY)
                        || !j.contains("slice",Tag.TAG_LONG) || j.getLong("slice")<0) throw new IOException("Foreign/malformed restore progress");
                try { phase=Phase.valueOf(j.getString("phase")); } catch(IllegalArgumentException bad) { throw new IOException("Invalid restore phase",bad); }
                long[] mask=j.getLongArray("damage");
                if(mask.length==0 || mask.length>plan.cells().size()) throw new IOException("Oversized or empty damage mask");
                Set<BlockPos> selected=new HashSet<>();
                for(long p:mask) selected.add(BlockPos.of(p));
                if (phase==Phase.CAPTURED || selected.isEmpty() || !plan.cells().containsAll(selected)
                        || !Arrays.equals(mask,positions(selected))) throw new IOException("Invalid durable damage mask");
                damage=Set.copyOf(selected); slice=j.getLong("slice");
                if (phase==Phase.ARMED && slice!=0) throw new IOException("Invalid armed progress");
            }
        }
        /** Original or exact ordinary AIR in the frozen mask only; protected NBT is never restored. */
        private int world() throws IOException {
            StreetscapeService.requireLoadedEntities(level,List.of(bounds(plan))); int missing=0;
            for(var entry:originals.entrySet()) {
                BlockPos p=entry.getKey(); Original now=current(level,p);
                if (now.equals(entry.getValue())) continue;
                if (phase!=Phase.CAPTURED && phase!=Phase.DONE && damage.contains(p)
                        && now.state().equals(Blocks.AIR.defaultBlockState()) && now.nbt().isEmpty()) { missing++; continue; }
                throw new IOException("Changed protected or unrecognized snapshot cell " + p);
            }
            return missing;
        }
        private List<ConstructionHazards.Write> remaining() {
            List<ConstructionHazards.Write> writes=new ArrayList<>();
            for(BlockPos p:ordered(damage)) if (!level.getBlockState(p).equals(originals.get(p).state()))
                writes.add(new ConstructionHazards.Write(p,originals.get(p).state())); return writes;
        }
        private ConstructionHazards.Guard safety(List<ConstructionHazards.Write> writes) throws IOException {
            Map<BlockPos,BlockState> prefix=new LinkedHashMap<>();
            for(var write:writes) { prefix.put(write.pos(),write.target()); PlannedWorld.verify(level,prefix,FLAGS); }
            return ConstructionHazards.inspect(level,writes);
        }
        View read() throws IOException { guard(Action.READ); return new View(phase,slice,world()); }
        boolean done() throws IOException { return read().phase()==Phase.DONE; }
        View arm(Set<BlockPos> selected) throws IOException {
            try {
                guard(Action.ARM); world();
                Set<BlockPos> mask=new HashSet<>(); for(BlockPos p:selected) mask.add(p.immutable());
                if(mask.isEmpty() || !plan.cells().containsAll(mask)) throw new IOException("Invalid allowed damage subset");
                if(phase==Phase.ARMED && damage.equals(mask)) return read();
                if(phase!=Phase.CAPTURED) throw new IOException("Snapshot already armed or restoration started");
                persist(Phase.ARMED,Set.copyOf(mask),0,Action.ARM); return read();
            } catch(IOException|RuntimeException failure) { failed(failure); throw failure; }
        }
        View startRestore() throws IOException {
            try {
                guard(Action.RESTORE); world();
                if(phase==Phase.DONE || phase==Phase.RESTORING) return read();
                if(phase!=Phase.ARMED) throw new IOException("No durable allowed-damage record");
                safety(remaining()).verify(); persist(Phase.RESTORING,damage,slice,Action.RESTORE); return read();
            } catch(IOException|RuntimeException failure) { failed(failure); throw failure; }
        }
        int step() throws IOException {
            try {
                guard(Action.RESTORE); world();
                if(phase==Phase.DONE) return 0;
                if(phase!=Phase.RESTORING) throw new IOException("Explicit restoration start required");
                if(slice==Long.MAX_VALUE) throw new IOException("Restore slice exhausted");
                List<ConstructionHazards.Write> writes=remaining(); ConstructionHazards.Guard entities=safety(writes); int changed=0;
                for(var write:writes) {
                    if(changed==4) break;
                    guard(Action.RESTORE); world(); entities.verify();
                    if(!level.setBlock(write.pos(),write.target(),FLAGS) || !level.getBlockState(write.pos()).equals(write.target()))
                        throw new IOException("Native restoration write mismatch at " + write.pos());
                    changed++; fault.at(SavePoint.AFTER_BLOCK); entities.verify(); world();
                }
                env.flush(); fault.at(SavePoint.AFTER_WORLD_SAVE); guard(Action.RESTORE); entities.verify();
                int missing=world(); persist(missing==0?Phase.DONE:Phase.RESTORING,damage,slice+1,Action.RESTORE); return changed;
            } catch(IOException|RuntimeException failure) { failed(failure); throw failure; }
        }
        private void persist(Phase next, Set<BlockPos> mask, long nextSlice, Action action) throws IOException {
            CompoundTag n=new CompoundTag(); n.putInt("schema",1); n.put("identity",id.copy()); n.put("plan",spec.copy());
            n.putString("snapshotSHA256",TownHallRevisions.sha(snapshotBytes)); n.putString("phase",next.name());
            n.putLongArray("damage",positions(mask)); n.putLong("slice",nextSlice);
            fault.at(SavePoint.BEFORE_JOURNAL); guard(action);
            if (world()!=0 && next==Phase.DONE) throw new IOException("Restoration changed before completion receipt");
            ProducerJournal.write(journal,n,journalBytes!=null); byte[] nextBytes=bytes(journal);
            if(!ProducerJournal.read(nextBytes).equals(n)) throw new IOException("Restore progress readback differs");
            // Publish only after readback. A fault closes this handle; reopening reads actual durable history.
            journalBytes=nextBytes; phase=next; damage=mask; slice=nextSlice;
            fault.at(SavePoint.AFTER_JOURNAL); guard(action); world();
        }
        private void failed(Exception failure) { try { close(); } catch(IOException closeFailure) { failure.addSuppressed(closeFailure); } }
        public void close() throws IOException {
            if(closed) return; closed=true;
            try { if(lock.isValid()) lock.release(); } finally { channel.close(); }
        }
    }
}
