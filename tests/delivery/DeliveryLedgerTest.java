package com.quin.catancraft.delivery;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Run the seed and verification phases in separate JVMs, sharing only the saved ledger data. */
public final class DeliveryLedgerTest {
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String ORIGIN = "depot", DEST = "refinery";
    private static int checks;

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: seed|verify NEW_TEST_DIRECTORY");
        Path directory = Path.of(args[1]);
        switch (args[0]) {
            case "seed" -> seed(directory);
            case "verify" -> verify(directory);
            default -> throw new IllegalArgumentException("Unknown phase");
        }
        System.out.println(args[0] + " passed " + checks + " assertions");
    }

    private static void seed(Path directory) throws Exception {
        Files.createDirectories(directory);
        Path data = directory.resolve("ledger.dat");
        if (Files.exists(data) || Files.exists(directory.resolve("ids.txt"))) throw new IllegalStateException("Preserve existing fixture");

        DeliveryLedger ledger = new DeliveryLedger();
        check(!ledger.isDirty() && ledger.latestReceipt(ALICE) == null);
        var lost = ledger.issue(ALICE, ORIGIN, DEST, 100);
        check(ledger.isDirty());
        ledger.setDirty(false);
        rejects(() -> ledger.issue(ALICE, ORIGIN, DEST, 100));
        rejects(() -> ledger.issue(ALICE, ORIGIN, DEST, 139));
        check(!ledger.isDirty());

        var intercepted = ledger.issue(ALICE, ORIGIN, DEST, 140);
        var independent = ledger.issue(BOB, ORIGIN, DEST, 140);
        check(!lost.id().equals(intercepted.id()) && !independent.id().equals(intercepted.id()));
        rejects(() -> ledger.redeem(BOB, intercepted.id(), ORIGIN, "wrong-receiver"));
        rejects(() -> ledger.redeem(BOB, intercepted.id(), "wrong-sender", DEST));
        rejects(() -> ledger.redeem(BOB, UUID.randomUUID(), ORIGIN, DEST));

        var receipt = ledger.redeem(BOB, intercepted.id(), ORIGIN, DEST);
        check(receipt.recipient().equals(BOB) && receipt.player().equals(ALICE) && receipt.delivered());
        check(ledger.latestReceipt(ALICE) == null && ledger.latestReceipt(BOB).equals(receipt));
        check(ledger.shipment(lost.id()).state() == DeliveryLedger.State.IN_TRANSIT);
        check(ledger.shipment(independent.id()).state() == DeliveryLedger.State.IN_TRANSIT);
        ledger.setDirty(false);
        rejects(() -> ledger.redeem(ALICE, intercepted.id(), ORIGIN, DEST));
        check(!ledger.isDirty());
        NbtIo.writeCompressed(ledger.save(new CompoundTag()), data.toFile());
        Files.write(directory.resolve("ids.txt"), List.of(lost.id().toString(), intercepted.id().toString(), independent.id().toString()));
    }

    private static void verify(Path directory) throws Exception {
        var ids = Files.readAllLines(directory.resolve("ids.txt")).stream().map(UUID::fromString).toList();
        UUID lost = ids.get(0), intercepted = ids.get(1), independent = ids.get(2);
        CompoundTag raw = NbtIo.readCompressed(directory.resolve("ledger.dat").toFile());
        DeliveryLedger ledger = DeliveryLedger.load(raw);
        check(!ledger.isDirty());
        check(ledger.shipment(lost).state() == DeliveryLedger.State.IN_TRANSIT);
        check(ledger.shipment(independent).state() == DeliveryLedger.State.IN_TRANSIT);
        check(ledger.latestReceipt(BOB).id().equals(intercepted) && ledger.latestReceipt(ALICE) == null);
        rejects(() -> ledger.redeem(BOB, intercepted, ORIGIN, DEST));
        rejects(() -> ledger.issue(ALICE, ORIGIN, DEST, 179));
        check(!ledger.isDirty());
        // Deliver older cargo after newer cargo. Persisted latest is determined by redemption time.
        ledger.redeem(BOB, lost, ORIGIN, DEST);
        ledger.redeem(ALICE, independent, ORIGIN, DEST);
        var restored = DeliveryLedger.load(ledger.save(new CompoundTag()));
        check(restored.latestReceipt(BOB).id().equals(lost));
        check(restored.latestReceipt(ALICE).id().equals(independent));
        check(restored.shipment(intercepted).recipient().equals(BOB));
        check(restored.issue(ALICE, ORIGIN, DEST, 180).state() == DeliveryLedger.State.IN_TRANSIT);

        // Legacy paper jobs have no item to surrender. Keep original receipts and archive unfinished jobs.
        CompoundTag legacy = raw.copy();
        legacy.putInt("schema", 1);
        for (Tag entry : legacy.getList("bills", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) entry;
            row.remove("issuedAt");
            row.remove("recipient");
        }
        var migrated = DeliveryLedger.load(legacy);
        check(migrated.shipment(lost).state() == DeliveryLedger.State.CANCELLED);
        rejects(() -> migrated.redeem(ALICE, lost, ORIGIN, DEST));
        check(migrated.latestReceipt(ALICE).id().equals(intercepted) && migrated.latestReceipt(BOB) == null);
        check(DeliveryLedger.load(migrated.save(new CompoundTag())).shipment(intercepted).delivered());

        CompoundTag brokenLegacy = legacy.copy();
        ListTag oldRows = brokenLegacy.getList("bills", Tag.TAG_COMPOUND);
        CompoundTag duplicateJob = oldRows.getCompound(0).copy();
        duplicateJob.putUUID("id", UUID.randomUUID());
        oldRows.add(duplicateJob);
        frozen(brokenLegacy);

        CompoundTag unknown = raw.copy();
        unknown.putInt("schema", 91);
        frozen(unknown);
        CompoundTag duplicate = raw.copy();
        ListTag rows = duplicate.getList("bills", Tag.TAG_COMPOUND);
        rows.add(rows.getCompound(0).copy());
        frozen(duplicate);
        CompoundTag malformed = raw.copy();
        malformed.getList("bills", Tag.TAG_COMPOUND).getCompound(0).putString("recipient", "bad-id");
        frozen(malformed);
    }

    private static void frozen(CompoundTag data) {
        DeliveryLedger ledger = DeliveryLedger.load(data);
        rejects(() -> ledger.issue(ALICE, ORIGIN, DEST, 1));
        check(!ledger.isDirty() && data.equals(ledger.save(new CompoundTag())));
    }

    private static void rejects(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            checks++;
            return;
        }
        throw new AssertionError("Operation was accepted");
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Failed check " + (checks + 1));
        checks++;
    }
}
