package com.quin.catancraft.data;

import net.minecraft.nbt.CompoundTag;

import java.util.Locale;
import java.util.UUID;

public final class TradeProposal {
    private final String id;
    private final UUID senderNationId;
    private final UUID recipientNationId;
    private final String offeredAsset;
    private final long offeredAmount;
    private final String requestedAsset;
    private final long requestedAmount;
    private final long createdGameTime;

    public TradeProposal(
            String id,
            UUID senderNationId,
            UUID recipientNationId,
            String offeredAsset,
            long offeredAmount,
            String requestedAsset,
            long requestedAmount,
            long createdGameTime
    ) {
        this.id = id.toLowerCase(Locale.ROOT);
        this.senderNationId = senderNationId;
        this.recipientNationId = recipientNationId;
        this.offeredAsset = offeredAsset.toLowerCase(Locale.ROOT);
        this.offeredAmount = Math.max(1, offeredAmount);
        this.requestedAsset = requestedAsset.toLowerCase(Locale.ROOT);
        this.requestedAmount = Math.max(1, requestedAmount);
        this.createdGameTime = Math.max(0, createdGameTime);
    }

    public String id() { return id; }
    public UUID senderNationId() { return senderNationId; }
    public UUID recipientNationId() { return recipientNationId; }
    public String offeredAsset() { return offeredAsset; }
    public long offeredAmount() { return offeredAmount; }
    public String requestedAsset() { return requestedAsset; }
    public long requestedAmount() { return requestedAmount; }
    public long createdGameTime() { return createdGameTime; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putUUID("senderNationId", senderNationId);
        tag.putUUID("recipientNationId", recipientNationId);
        tag.putString("offeredAsset", offeredAsset);
        tag.putLong("offeredAmount", offeredAmount);
        tag.putString("requestedAsset", requestedAsset);
        tag.putLong("requestedAmount", requestedAmount);
        tag.putLong("createdGameTime", createdGameTime);
        return tag;
    }

    public static TradeProposal load(CompoundTag tag) {
        return new TradeProposal(
                tag.getString("id"),
                tag.getUUID("senderNationId"),
                tag.getUUID("recipientNationId"),
                tag.getString("offeredAsset"),
                tag.getLong("offeredAmount"),
                tag.getString("requestedAsset"),
                tag.getLong("requestedAmount"),
                tag.getLong("createdGameTime")
        );
    }
}
