package com.quin.catancraft.delivery;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** One bearer shipment can settle once. Issuing a new crate never revokes an older one in someone else's hands. */
public final class DeliveryLedger extends SavedData {
    public enum State { IN_TRANSIT, DELIVERED, CANCELLED }

    public record Waybill(UUID id, UUID player, String origin, String destination, State state,
                          long issuedAt, UUID recipient) {
        public Waybill {
            Objects.requireNonNull(id);
            Objects.requireNonNull(player);
            Objects.requireNonNull(state);
            if (origin == null || origin.isBlank() || destination == null || destination.isBlank()
                    || origin.equals(destination) || issuedAt < -1 || (issuedAt == -1 && state == State.IN_TRANSIT)
                    || (state == State.DELIVERED) != (recipient != null)) {
                throw new IllegalArgumentException("Invalid saved shipment.");
            }
        }

        public boolean delivered() { return state == State.DELIVERED; }
    }

    // Closed shipments move to the end so receipt order is settlement order, even with intercepted freight.
    private final Map<UUID, Waybill> bills = new LinkedHashMap<>();
    private final Map<UUID, Long> lastIssue = new HashMap<>();
    private final Map<UUID, Waybill> latestReceipts = new HashMap<>();
    private CompoundTag unreadable;

    public static DeliveryLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                DeliveryLedger::load, DeliveryLedger::new, "catancraft_deliveries");
    }

    public Waybill shipment(UUID id) {
        checkReadable();
        return bills.get(id);
    }

    public Waybill latestReceipt(UUID recipient) {
        checkReadable();
        return latestReceipts.get(recipient);
    }

    public Waybill issue(UUID player, String origin, String destination, long gameTime) {
        checkReadable();
        Long last = lastIssue.get(player);
        if (gameTime < 0 || (last != null && gameTime >= last && gameTime - last < 40)) {
            throw new IllegalStateException("The crate is already being dispatched. Wait a moment.");
        }
        UUID id;
        do { id = UUID.randomUUID(); } while (bills.containsKey(id));
        Waybill bill = new Waybill(id, player, origin, destination, State.IN_TRANSIT, gameTime, null);
        bills.put(id, bill);
        lastIssue.put(player, gameTime);
        setDirty();
        return bill;
    }

    /** The caller has validated the seal on the physical stack actually in the presenter's hand. */
    public Waybill redeem(UUID holder, UUID id, String origin, String destination) {
        Waybill bill = shipment(id);
        if (bill == null || bill.state() != State.IN_TRANSIT) {
            throw new IllegalStateException("This seal is unknown or its shipment has already closed.");
        }
        if (!bill.origin().equals(origin) || !bill.destination().equals(destination)) {
            throw new IllegalStateException("This shipment is assigned to a different route.");
        }
        Waybill closed = new Waybill(id, bill.player(), origin, destination, State.DELIVERED, bill.issuedAt(), holder);
        bills.remove(id);
        bills.put(id, closed);
        latestReceipts.put(holder, closed);
        setDirty();
        return closed;
    }

    private void checkReadable() {
        if (unreadable != null) throw new IllegalStateException("Deliveries are paused. The saved waybills need review.");
    }

    public static DeliveryLedger load(CompoundTag tag) {
        DeliveryLedger ledger = new DeliveryLedger();
        try {
            int version = tag.getInt("schema");
            if (!tag.contains("schema", Tag.TAG_INT) || (version != 1 && version != 2)
                    || !(tag.get("bills") instanceof ListTag list)
                    || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("Unsupported delivery ledger.");
            }
            var legacyOpenCouriers = new HashSet<UUID>();
            for (Tag entry : list) {
                CompoundTag row = (CompoundTag) entry;
                if (!row.hasUUID("id") || !row.hasUUID("player")) throw new IllegalArgumentException("Missing shipment identity.");
                UUID player = row.getUUID("player");
                State state = State.valueOf(row.getString("state"));
                UUID recipient = null;
                long issuedAt = -1;
                if (version == 1) {
                    // A paper waybill has no physical seal. Archive unfinished paper jobs without granting cargo.
                    if (state == State.IN_TRANSIT) {
                        if (!legacyOpenCouriers.add(player)) throw new IllegalArgumentException("Duplicate legacy open waybill.");
                        state = State.CANCELLED;
                    }
                    if (state == State.DELIVERED) recipient = player;
                } else {
                    if (!row.contains("issuedAt", Tag.TAG_LONG)) throw new IllegalArgumentException("Missing shipment time.");
                    issuedAt = row.getLong("issuedAt");
                    if (row.contains("recipient")) {
                        if (!row.hasUUID("recipient")) throw new IllegalArgumentException("Invalid receipt recipient.");
                        recipient = row.getUUID("recipient");
                    }
                }
                Waybill bill = new Waybill(row.getUUID("id"), player, row.getString("origin"),
                        row.getString("destination"), state, issuedAt, recipient);
                if (ledger.bills.putIfAbsent(bill.id(), bill) != null) throw new IllegalArgumentException("Duplicate shipment.");
                if (bill.delivered()) ledger.latestReceipts.put(recipient, bill);
                if (issuedAt >= 0) ledger.lastIssue.merge(player, issuedAt, Math::max);
            }
        } catch (IllegalArgumentException | ClassCastException e) {
            ledger.bills.clear();
            ledger.lastIssue.clear();
            ledger.latestReceipts.clear();
            ledger.unreadable = tag.copy();
        }
        return ledger;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (unreadable != null) return unreadable.copy();
        tag.putInt("schema", 2);
        ListTag list = new ListTag();
        for (Waybill bill : bills.values()) {
            CompoundTag row = new CompoundTag();
            row.putUUID("id", bill.id());
            row.putUUID("player", bill.player());
            row.putString("origin", bill.origin());
            row.putString("destination", bill.destination());
            row.putString("state", bill.state().name());
            row.putLong("issuedAt", bill.issuedAt());
            if (bill.recipient() != null) row.putUUID("recipient", bill.recipient());
            list.add(row);
        }
        tag.put("bills", list);
        return tag;
    }
}
