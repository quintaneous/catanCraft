package com.quin.catancraft.delivery;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** The waybill is the cargo. All transitions happen on the server thread in one SavedData object. */
public final class DeliveryLedger extends SavedData {
    public enum State { IN_TRANSIT, DELIVERED, CANCELLED }

    public record Waybill(UUID id, UUID player, String origin, String destination, State state) {
        public Waybill {
            Objects.requireNonNull(id);
            Objects.requireNonNull(player);
            Objects.requireNonNull(state);
            if (origin == null || origin.isBlank() || destination == null || destination.isBlank()
                    || origin.equals(destination)) {
                throw new IllegalArgumentException("A delivery requires two distinct stops.");
            }
        }

        public boolean delivered() {
            return state == State.DELIVERED;
        }
    }

    private final Map<UUID, Waybill> bills = new LinkedHashMap<>();
    private final Map<UUID, UUID> activeByPlayer = new HashMap<>();
    private final Map<UUID, Waybill> latestReceipts = new HashMap<>();
    private CompoundTag unreadable;

    public static DeliveryLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                DeliveryLedger::load, DeliveryLedger::new, "catancraft_deliveries");
    }

    /** Returns null when this player has no load in transit. */
    public Waybill active(UUID player) {
        checkReadable();
        return bills.get(activeByPlayer.get(player));
    }

    /** Only a successful turn-in produces a receipt. */
    public Waybill receipt(UUID player, UUID id) {
        checkReadable();
        Waybill bill = bills.get(id);
        return bill != null && bill.player().equals(player) && bill.delivered() ? bill : null;
    }

    /** Most recent successful hand-in for this courier, including across saves. */
    public Waybill latestReceipt(UUID player) {
        checkReadable();
        return latestReceipts.get(player);
    }

    public Waybill pickup(UUID player, String origin, String destination) {
        Waybill current = active(player);
        if (current != null) {
            if (current.origin().equals(origin) && current.destination().equals(destination)) return current;
            throw new IllegalStateException("You already have cargo for another route.");
        }
        UUID id;
        do {
            id = UUID.randomUUID();
        } while (bills.containsKey(id));
        Waybill bill = new Waybill(id, player, origin, destination, State.IN_TRANSIT);
        bills.put(id, bill);
        activeByPlayer.put(player, id);
        setDirty();
        return bill;
    }

    /** Returns false on a retry of a successful delivery. Never resolves a different active bill. */
    public boolean deliver(UUID player, UUID id, String destination) {
        Waybill bill = owned(player, id);
        if (!bill.destination().equals(destination)) {
            throw new IllegalStateException("This cargo belongs at a different destination.");
        }
        return finish(bill, State.DELIVERED);
    }

    /** Can be used anywhere if an origin or destination later becomes unavailable. */
    public boolean cancel(UUID player, UUID id) {
        return finish(owned(player, id), State.CANCELLED);
    }

    private Waybill owned(UUID player, UUID id) {
        checkReadable();
        Waybill bill = bills.get(id);
        if (bill == null || !bill.player().equals(player)) {
            throw new IllegalStateException("That waybill does not belong to you.");
        }
        return bill;
    }

    private boolean finish(Waybill bill, State to) {
        if (bill.state() == to) return false;
        if (bill.state() != State.IN_TRANSIT) throw new IllegalStateException("This waybill is closed.");
        Waybill closed = new Waybill(bill.id(), bill.player(), bill.origin(), bill.destination(), to);
        bills.put(bill.id(), closed);
        if (closed.delivered()) latestReceipts.put(closed.player(), closed);
        activeByPlayer.remove(bill.player(), bill.id());
        setDirty();
        return true;
    }

    private void checkReadable() {
        if (unreadable != null) throw new IllegalStateException("Deliveries are paused. The saved waybills need review.");
    }

    public static DeliveryLedger load(CompoundTag tag) {
        DeliveryLedger ledger = new DeliveryLedger();
        try {
            if (!tag.contains("schema", Tag.TAG_INT) || tag.getInt("schema") != 1
                    || !(tag.get("bills") instanceof ListTag list)
                    || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("Unsupported delivery ledger.");
            }
            for (Tag entry : list) {
                CompoundTag row = (CompoundTag) entry;
                if (!row.hasUUID("id") || !row.hasUUID("player")) throw new IllegalArgumentException("Missing identity.");
                Waybill bill = new Waybill(row.getUUID("id"), row.getUUID("player"), row.getString("origin"),
                        row.getString("destination"), State.valueOf(row.getString("state")));
                if (ledger.bills.putIfAbsent(bill.id(), bill) != null) throw new IllegalArgumentException("Duplicate waybill.");
                // Bills are saved in issuance order and a courier carries at most one at a time.
                if (bill.delivered()) ledger.latestReceipts.put(bill.player(), bill);
                if (bill.state() == State.IN_TRANSIT && ledger.activeByPlayer.putIfAbsent(bill.player(), bill.id()) != null) {
                    throw new IllegalArgumentException("Multiple loads for one courier.");
                }
            }
        } catch (IllegalArgumentException e) {
            // Returning a frozen ledger keeps a corrupt or newer file from becoming an empty writable save.
            ledger.bills.clear();
            ledger.activeByPlayer.clear();
            ledger.latestReceipts.clear();
            ledger.unreadable = tag.copy();
        }
        return ledger;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (unreadable != null) return unreadable.copy();
        tag.putInt("schema", 1);
        ListTag list = new ListTag();
        for (Waybill bill : bills.values()) {
            CompoundTag row = new CompoundTag();
            row.putUUID("id", bill.id());
            row.putUUID("player", bill.player());
            row.putString("origin", bill.origin());
            row.putString("destination", bill.destination());
            row.putString("state", bill.state().name());
            list.add(row);
        }
        tag.put("bills", list);
        return tag;
    }
}
