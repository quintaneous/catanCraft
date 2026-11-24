package com.quin.catancraft.delivery;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Run seed and verify in distinct JVMs against the same temporary directory. */
public final class DeliveryLedgerTest {
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static int assertions;

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: seed|verify NEW_TEST_DIRECTORY");
        Path directory = Path.of(args[1]);
        switch (args[0]) {
            case "seed" -> seed(directory);
            case "verify" -> verify(directory);
            default -> throw new IllegalArgumentException("Unknown phase");
        }
        System.out.println(args[0] + " passed " + assertions + " assertions");
    }

    private static void seed(Path directory) throws Exception {
        Files.createDirectories(directory);
        Path data = directory.resolve("ledger.dat");
        if (Files.exists(data) || Files.exists(directory.resolve("ids.txt"))) throw new IllegalStateException("Preserve existing fixture");
        DeliveryLedger ledger = new DeliveryLedger();
        check(!ledger.isDirty());
        check(ledger.active(ALICE) == null);
        check(ledger.latestReceipt(ALICE) == null);
        var first = ledger.pickup(ALICE, "depot", "landmark");
        check(ledger.isDirty());
        ledger.setDirty(false);
        check(first.equals(ledger.pickup(ALICE, "depot", "landmark")));
        check(!ledger.isDirty());
        var other = ledger.pickup(BOB, "depot", "landmark");
        check(!first.id().equals(other.id()));
        rejects(() -> ledger.pickup(ALICE, "another-depot", "landmark"));
        rejects(() -> ledger.deliver(BOB, first.id(), "landmark"));
        rejects(() -> ledger.deliver(ALICE, first.id(), "wrong-stop"));
        rejects(() -> ledger.cancel(ALICE, other.id()));
        check(ledger.receipt(ALICE, first.id()) == null);
        check(first.equals(ledger.active(ALICE)));
        check(ledger.deliver(ALICE, first.id(), "landmark"));
        check(ledger.receipt(ALICE, first.id()).delivered());
        check(first.id().equals(ledger.latestReceipt(ALICE).id()));
        check(ledger.latestReceipt(BOB) == null);
        check(ledger.receipt(BOB, first.id()) == null);
        check(ledger.active(ALICE) == null);
        var cancelled = ledger.pickup(ALICE, "depot", "landmark");
        check(ledger.cancel(ALICE, cancelled.id()));
        check(ledger.receipt(ALICE, cancelled.id()) == null);
        var current = ledger.pickup(ALICE, "depot", "landmark");
        check(!current.id().equals(first.id()) && !current.id().equals(cancelled.id()));
        check(ledger.latestReceipt(ALICE).id().equals(first.id()));
        NbtIo.writeCompressed(ledger.save(new CompoundTag()), data.toFile());
        Files.write(directory.resolve("ids.txt"), List.of(first.id().toString(), cancelled.id().toString(),
                current.id().toString(), other.id().toString()));
    }

    private static void verify(Path directory) throws Exception {
        var ids = Files.readAllLines(directory.resolve("ids.txt")).stream().map(UUID::fromString).toList();
        UUID first = ids.get(0), cancelled = ids.get(1), current = ids.get(2), bob = ids.get(3);
        CompoundTag raw = NbtIo.readCompressed(directory.resolve("ledger.dat").toFile());
        DeliveryLedger ledger = DeliveryLedger.load(raw);
        check(!ledger.isDirty());
        check(ledger.active(ALICE).id().equals(current));
        check(ledger.active(BOB).id().equals(bob));
        check(ledger.receipt(ALICE, first).delivered());
        check(ledger.latestReceipt(ALICE).id().equals(first));
        check(ledger.latestReceipt(BOB) == null);
        check(!ledger.deliver(ALICE, first, "landmark"));
        check(!ledger.cancel(ALICE, cancelled));
        rejects(() -> ledger.cancel(ALICE, first));
        rejects(() -> ledger.deliver(ALICE, cancelled, "landmark"));
        rejects(() -> ledger.deliver(ALICE, first, "wrong-stop"));
        rejects(() -> ledger.deliver(BOB, current, "landmark"));
        check(!ledger.isDirty());
        check(ledger.active(ALICE).id().equals(current));
        check(ledger.deliver(ALICE, current, "landmark"));
        check(ledger.active(BOB).id().equals(bob));
        check(ledger.active(ALICE) == null);
        check(ledger.deliver(BOB, bob, "landmark"));
        check(ledger.receipt(BOB, bob).delivered());
        check(ledger.latestReceipt(BOB).id().equals(bob));
        check(ledger.latestReceipt(ALICE).id().equals(current));
        check(!ledger.deliver(ALICE, first, "landmark"));
        check(ledger.latestReceipt(ALICE).id().equals(current));
        var restored = DeliveryLedger.load(ledger.save(new CompoundTag()));
        check(restored.latestReceipt(ALICE).id().equals(current));
        check(restored.latestReceipt(BOB).id().equals(bob));

        CompoundTag unknown = raw.copy();
        unknown.putInt("schema", 91);
        frozen(unknown);
        CompoundTag duplicate = raw.copy();
        ListTag rows = duplicate.getList("bills", Tag.TAG_COMPOUND);
        rows.add(rows.getCompound(0).copy());
        frozen(duplicate);
        CompoundTag multipleActive = raw.copy();
        ListTag duplicateActive = multipleActive.getList("bills", Tag.TAG_COMPOUND);
        CompoundTag extra = duplicateActive.getCompound(duplicateActive.size() - 1).copy();
        extra.putUUID("id", UUID.randomUUID());
        duplicateActive.add(extra);
        frozen(multipleActive);
    }

    private static void frozen(CompoundTag unsupported) {
        DeliveryLedger ledger = DeliveryLedger.load(unsupported);
        rejects(() -> ledger.pickup(ALICE, "depot", "landmark"));
        check(!ledger.isDirty());
        check(unsupported.equals(ledger.save(new CompoundTag())));
    }

    private static void rejects(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            assertions++;
            return;
        }
        throw new AssertionError("Operation was accepted");
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Failed check " + (assertions + 1));
        assertions++;
    }
}
