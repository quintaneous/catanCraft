package com.quin.catancraft.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class CatanSavedData extends SavedData {
    private static final String DATA_NAME = "catancraft";

    private final Map<UUID, NationData> nations = new LinkedHashMap<>();
    private final Map<String, TerritoryData> territories = new LinkedHashMap<>();

    public static CatanSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                CatanSavedData::load,
                CatanSavedData::new,
                DATA_NAME
        );
    }

    public Collection<NationData> nations() {
        return nations.values();
    }

    public Collection<TerritoryData> territories() {
        return territories.values();
    }

    @Nullable
    public NationData nation(UUID id) {
        return nations.get(id);
    }

    @Nullable
    public NationData nationByName(String name) {
        return nations.values().stream()
                .filter(n -> n.name().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    @Nullable
    public NationData nationForPlayer(UUID playerId) {
        return nations.values().stream()
                .filter(n -> n.containsMember(playerId))
                .findFirst()
                .orElse(null);
    }

    public boolean nationNameExists(String name) {
        return nationByName(name) != null;
    }

    public NationData createNation(String name, UUID leaderId) {
        NationData nation = new NationData(UUID.randomUUID(), name, leaderId, 12_000);
        nations.put(nation.id(), nation);
        setDirty();
        return nation;
    }

    @Nullable
    public TerritoryData territory(String id) {
        return territories.get(id.toLowerCase());
    }

    public TerritoryData createTerritory(String id, String name, ResourceType specialty) {
        TerritoryData territory = new TerritoryData(id.toLowerCase(), name, specialty);
        territories.put(territory.id(), territory);
        setDirty();
        return territory;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag nationList = new ListTag();
        for (NationData nation : nations.values()) {
            nationList.add(nation.save());
        }
        tag.put("nations", nationList);

        ListTag territoryList = new ListTag();
        for (TerritoryData territory : territories.values()) {
            territoryList.add(territory.save());
        }
        tag.put("territories", territoryList);
        return tag;
    }

    public static CatanSavedData load(CompoundTag tag) {
        CatanSavedData data = new CatanSavedData();

        ListTag nationList = tag.getList("nations", Tag.TAG_COMPOUND);
        for (int i = 0; i < nationList.size(); i++) {
            NationData nation = NationData.load(nationList.getCompound(i));
            data.nations.put(nation.id(), nation);
        }

        ListTag territoryList = tag.getList("territories", Tag.TAG_COMPOUND);
        for (int i = 0; i < territoryList.size(); i++) {
            TerritoryData territory = TerritoryData.load(territoryList.getCompound(i));
            data.territories.put(territory.id(), territory);
        }
        return data;
    }
}
