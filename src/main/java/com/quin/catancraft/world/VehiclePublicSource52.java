package com.quin.catancraft.world;

import com.quin.catancraft.map.TerritoryDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.state.BlockState;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Read-only authority for the exact public-space revision around vehicle sites.
 * A sealed journal alone is insufficient: its identity and every ordered before/after
 * row must match the pinned contract. Never starts, resumes or rewrites preparation. */
final class VehiclePublicSource52 {
    private static final Map<String, Map<BlockPos, BlockState>> AFTER = new HashMap<>();
    private VehiclePublicSource52() {}

    static boolean prepared(Path root, VehicleSites52.Site site, UUID owner) throws IOException {
        Objects.requireNonNull(root); Objects.requireNonNull(site); Objects.requireNonNull(owner);
        ShopSites51.canonical(site.definition());
        Path normalized = root.toAbsolutePath().normalize();
        VehiclePayment47.safe(normalized);
        if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS) || !normalized.toRealPath().equals(normalized))
            throw new IOException("Vehicle public source requires the original canonical world directory.");
        Path file = normalized.resolve("data/catancraft_r5_public_" + site.definition().id() + "_v1.dat");
        VehiclePayment47.safe(file);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return false;
        return validate(StreetscapeJournal.read(file), StreetscapeR5.plan(site.definition().cityCenter()),
                identity(normalized, site.definition(), owner));
    }

    static CompoundTag identity(Path root, TerritoryDefinition definition, UUID owner) {
        CompoundTag expected = new CompoundTag();
        expected.putString("world", root.toString()); expected.putString("dimension", definition.dimension());
        expected.putString("city", definition.id()); expected.putString("owner", owner.toString());
        var center = definition.cityCenter();
        expected.putIntArray("center", new int[]{center.x(), center.y(), center.z(), center.rotation()});
        return expected;
    }

    /** Validates stored revision history only. Current source blocks must still be
     * checked by the caller before spending or delivery. Inputs are never mutated. */
    static boolean validate(CompoundTag journal, StreetscapeR5.Plan plan, CompoundTag expected) throws IOException {
        if (journal == null || plan == null || expected == null
                || !journal.getAllKeys().equals(Set.of("schema", "id", "contract", "identity", "blocks", "phase"))
                || !journal.contains("schema", Tag.TAG_INT) || journal.getInt("schema") != 27
                || !journal.hasUUID("id") || !journal.contains("contract", Tag.TAG_STRING)
                || !journal.getString("contract").equals(StreetscapeR5.HASH)
                || !journal.contains("identity", Tag.TAG_COMPOUND) || !journal.getCompound("identity").equals(expected)
                || !journal.contains("phase", Tag.TAG_STRING) || !journal.contains("blocks", Tag.TAG_LIST)
                || plan.cells().size() != StreetscapeR5.READS)
            throw new IOException("Vehicle public-space history has a foreign identity or contract.");
        StreetscapeTransaction.Phase phase = StreetscapeTransaction.phase(journal);
        if (phase != StreetscapeTransaction.Phase.COMMITTED && phase != StreetscapeTransaction.Phase.ROLLED_BACK)
            throw new IOException("City public-space preparation is unfinished; retain its original recovery history.");
        ListTag rows = journal.getList("blocks", Tag.TAG_COMPOUND);
        if (rows.size() != StreetscapeR5.WRITES || rows.getElementType() != Tag.TAG_COMPOUND)
            throw new IOException("Vehicle public-space history has an incomplete source image.");
        int rowIndex = 0;
        for (int index = 0; index < plan.cells().size(); index++) {
            var cell = plan.cells().get(index); if (!cell.changes()) continue;
            if (rowIndex >= rows.size()) throw new IOException("Vehicle public-space source mask differs.");
            CompoundTag row = rows.getCompound(rowIndex++);
            if (!row.getAllKeys().equals(Set.of("index", "pos", "before", "after"))
                    || !row.contains("index", Tag.TAG_INT) || row.getInt("index") != index
                    || !row.contains("pos", Tag.TAG_LONG) || row.getLong("pos") != cell.pos().asLong()
                    || !row.contains("before", Tag.TAG_COMPOUND) || !row.contains("after", Tag.TAG_COMPOUND))
                throw new IOException("Vehicle public-space source row is missing, foreign or reordered.");
            CompoundTag savedBefore = row.getCompound("before");
            BlockState before = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), savedBefore);
            if (!savedBefore.equals(NbtUtils.writeBlockState(before))
                    || !StreetscapeR5.matches(before, cell.before())
                    || !row.getCompound("after").equals(NbtUtils.writeBlockState(cell.after())))
                throw new IOException("Vehicle public-space source row differs from the pinned revision.");
        }
        if (rowIndex != StreetscapeR5.WRITES) throw new IOException("Vehicle public-space source mask is incomplete.");
        return phase == StreetscapeTransaction.Phase.COMMITTED;
    }

    static synchronized Map<BlockPos, BlockState> after(TerritoryDefinition definition) throws IOException {
        // Validate every caller before using the id-keyed cache; changed coordinates
        // must never inherit a previous city's approved transformation.
        ShopSites51.canonical(definition);
        Map<BlockPos, BlockState> cached = AFTER.get(definition.id());
        if (cached != null) return cached;
        Map<BlockPos, BlockState> states = new HashMap<>();
        for (var cell : StreetscapeR5.plan(definition.cityCenter()).cells()) if (cell.changes()) {
            if (states.putIfAbsent(cell.pos().immutable(), cell.after()) != null)
                throw new IOException("Ambiguous vehicle public-space source cell.");
        }
        if (states.size() != StreetscapeR5.WRITES) throw new IOException("Incomplete vehicle public-space source revision.");
        cached = Map.copyOf(states); AFTER.put(definition.id(), cached); return cached;
    }
}
