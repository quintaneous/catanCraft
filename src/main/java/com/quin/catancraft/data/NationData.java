package com.quin.catancraft.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class NationData {
    private final UUID id;
    private String name;
    private UUID leaderId;
    private final Set<UUID> members = new HashSet<>();
    private final EnumMap<ResourceType, Long> stockpile = new EnumMap<>(ResourceType.class);
    private long treasury;

    public NationData(UUID id, String name, UUID leaderId, long treasury) {
        this.id = id;
        this.name = name;
        this.leaderId = leaderId;
        this.treasury = Math.max(0, treasury);
        this.members.add(leaderId);
        for (ResourceType type : ResourceType.values()) {
            stockpile.put(type, 0L);
        }
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public UUID leaderId() {
        return leaderId;
    }

    public Set<UUID> members() {
        return Set.copyOf(members);
    }

    public long treasury() {
        return treasury;
    }

    public void setTreasury(long treasury) {
        this.treasury = Math.max(0, treasury);
    }

    public void addTreasury(long amount) {
        setTreasury(treasury + amount);
    }

    public long resource(ResourceType type) {
        return stockpile.getOrDefault(type, 0L);
    }

    public Map<ResourceType, Long> stockpileView() {
        return Map.copyOf(stockpile);
    }

    public void addResource(ResourceType type, long amount) {
        stockpile.put(type, Math.max(0, resource(type) + amount));
    }

    public boolean canConsume(Map<ResourceType, Integer> inputs, int batches) {
        for (Map.Entry<ResourceType, Integer> entry : inputs.entrySet()) {
            long required = (long) entry.getValue() * batches;
            if (resource(entry.getKey()) < required) return false;
        }
        return true;
    }

    public void consume(Map<ResourceType, Integer> inputs, int batches) {
        if (!canConsume(inputs, batches)) {
            throw new IllegalStateException("Insufficient resources");
        }
        inputs.forEach((type, amount) -> addResource(type, -(long) amount * batches));
    }

    public void addMember(UUID playerId) {
        members.add(playerId);
    }

    public void removeMember(UUID playerId) {
        if (!playerId.equals(leaderId)) members.remove(playerId);
    }

    public boolean containsMember(UUID playerId) {
        return members.contains(playerId);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("name", name);
        tag.putUUID("leaderId", leaderId);
        tag.putLong("treasury", treasury);

        ListTag memberList = new ListTag();
        for (UUID member : members) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", member);
            memberList.add(entry);
        }
        tag.put("members", memberList);

        CompoundTag resources = new CompoundTag();
        for (ResourceType type : ResourceType.values()) {
            resources.putLong(type.id(), resource(type));
        }
        tag.put("stockpile", resources);
        return tag;
    }

    public static NationData load(CompoundTag tag) {
        NationData nation = new NationData(
                tag.getUUID("id"),
                tag.getString("name"),
                tag.getUUID("leaderId"),
                tag.getLong("treasury")
        );

        nation.members.clear();
        ListTag memberList = tag.getList("members", Tag.TAG_COMPOUND);
        for (int i = 0; i < memberList.size(); i++) {
            nation.members.add(memberList.getCompound(i).getUUID("id"));
        }
        nation.members.add(nation.leaderId);

        CompoundTag resources = tag.getCompound("stockpile");
        for (ResourceType type : ResourceType.values()) {
            nation.stockpile.put(type, resources.getLong(type.id()));
        }
        return nation;
    }
}
