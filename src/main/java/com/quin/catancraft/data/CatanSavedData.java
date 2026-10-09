package com.quin.catancraft.data;

import com.quin.catancraft.map.MapDefinitionManager;
import com.quin.catancraft.map.MonumentDefinition;
import com.quin.catancraft.map.TerritoryDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
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
    private final Map<String, MonumentData> monuments = new LinkedHashMap<>();
    private final Map<String, TradeProposal> tradeProposals = new LinkedHashMap<>();
    @Nullable private String activeMonumentId;
    @Nullable private String lastActivatedMonumentId;
    private long nextMonumentActivationGameTime;

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

    public Collection<MonumentData> monuments() {
        return monuments.values();
    }

    public Collection<TradeProposal> tradeProposals() {
        return tradeProposals.values();
    }

    @Nullable
    public TradeProposal tradeProposal(String id) {
        return id == null ? null : tradeProposals.get(id.toLowerCase());
    }

    public TradeProposal createTradeProposal(
            UUID senderNationId,
            UUID recipientNationId,
            String offeredAsset,
            long offeredAmount,
            String requestedAsset,
            long requestedAmount,
            long createdGameTime
    ) {
        String id;
        do {
            id = UUID.randomUUID().toString().substring(0, 8);
        } while (tradeProposals.containsKey(id));

        TradeProposal proposal = new TradeProposal(
                id,
                senderNationId,
                recipientNationId,
                offeredAsset,
                offeredAmount,
                requestedAsset,
                requestedAmount,
                createdGameTime
        );
        tradeProposals.put(proposal.id(), proposal);
        setDirty();
        return proposal;
    }

    public void removeTradeProposal(String id) {
        if (id != null && tradeProposals.remove(id.toLowerCase()) != null) {
            setDirty();
        }
    }

    @Nullable
    public MonumentData monument(String id) {
        return id == null ? null : monuments.get(id.toLowerCase());
    }

    public MonumentData createMonument(String id, String name, MonumentType type,
                                       String dimensionId, int x, int y, int z, int radius) {
        MonumentData monument = new MonumentData(
                id, name, type, dimensionId, x, y, z, radius);
        monuments.put(monument.id(), monument);
        setDirty();
        return monument;
    }

    @Nullable
    public MonumentData activeMonument() {
        return monument(activeMonumentId);
    }

    public void setActiveMonumentId(@Nullable String id) {
        activeMonumentId = id == null ? null : id.toLowerCase();
        setDirty();
    }

    @Nullable
    public String lastActivatedMonumentId() {
        return lastActivatedMonumentId;
    }

    public void setLastActivatedMonumentId(@Nullable String id) {
        lastActivatedMonumentId = id == null ? null : id.toLowerCase();
        setDirty();
    }

    public long nextMonumentActivationGameTime() {
        return nextMonumentActivationGameTime;
    }

    public void setNextMonumentActivationGameTime(long value) {
        nextMonumentActivationGameTime = Math.max(0, value);
        setDirty();
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

    public boolean linkTerritories(String firstId, String secondId) {
        TerritoryData first = territory(firstId);
        TerritoryData second = territory(secondId);
        if (first == null || second == null || first.id().equals(second.id())) return false;

        first.addNeighbor(second.id());
        second.addNeighbor(first.id());
        setDirty();
        return true;
    }

    public void syncMapDefinitions() {
        for (TerritoryDefinition definition : MapDefinitionManager.territories()) {
            TerritoryData territory = territories.computeIfAbsent(
                    definition.id(),
                    ignored -> new TerritoryData(
                            definition.id(),
                            definition.name(),
                            definition.specialty()
                    )
            );

            // V0.1 saves predate physical plot IDs. Assign them deterministically
            // in existing building order so old test worlds remain usable.
            for (int i = 0; i < territory.buildings().size(); i++) {
                BuildingInstance building = territory.buildings().get(i);
                if (!building.plotId().isBlank()) continue;
                if (i >= definition.availableBuildingPlots().size()) break;
                building.setPlotId(definition.availableBuildingPlots().get(i).id());
            }
        }

        for (MonumentDefinition definition : MapDefinitionManager.monuments()) {
            MonumentData previous = monuments.get(definition.id());
            MonumentData synced = new MonumentData(
                    definition.id(),
                    definition.name(),
                    definition.type(),
                    definition.dimension(),
                    definition.x(),
                    definition.y(),
                    definition.z(),
                    definition.radius()
            );

            if (previous != null) {
                synced.setCapturingNationId(previous.capturingNationId());
                synced.setCaptureProgressTicks(previous.captureProgressTicks());
            }
            monuments.put(definition.id(), synced);
        }

        setDirty();
    }

    @Nullable
    public TerritoryData territoryAt(ServerLevel level, BlockPos pos) {
        TerritoryDefinition defined = MapDefinitionManager.territoryAt(level, pos);
        if (defined != null) {
            return territory(defined.id());
        }

        String dimensionId = level.dimension().location().toString();
        for (TerritoryData territory : territories.values()) {
            if (MapDefinitionManager.territory(territory.id()) != null) continue;

            TerritoryBoundary boundary = territory.boundary();
            if (boundary != null && boundary.contains(dimensionId, pos.getX(), pos.getZ())) {
                return territory;
            }
        }
        return null;
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

        ListTag monumentList = new ListTag();
        for (MonumentData monument : monuments.values()) {
            monumentList.add(monument.save());
        }
        tag.put("monuments", monumentList);

        ListTag tradeList = new ListTag();
        for (TradeProposal proposal : tradeProposals.values()) {
            tradeList.add(proposal.save());
        }
        tag.put("tradeProposals", tradeList);
        if (activeMonumentId != null) tag.putString("activeMonumentId", activeMonumentId);
        if (lastActivatedMonumentId != null) tag.putString("lastActivatedMonumentId", lastActivatedMonumentId);
        tag.putLong("nextMonumentActivationGameTime", nextMonumentActivationGameTime);
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

        ListTag monumentList = tag.getList("monuments", Tag.TAG_COMPOUND);
        for (int i = 0; i < monumentList.size(); i++) {
            MonumentData monument = MonumentData.load(monumentList.getCompound(i));
            data.monuments.put(monument.id(), monument);
        }

        ListTag tradeList = tag.getList("tradeProposals", Tag.TAG_COMPOUND);
        for (int i = 0; i < tradeList.size(); i++) {
            TradeProposal proposal = TradeProposal.load(tradeList.getCompound(i));
            data.tradeProposals.put(proposal.id(), proposal);
        }
        if (tag.contains("activeMonumentId", Tag.TAG_STRING)) {
            data.activeMonumentId = tag.getString("activeMonumentId");
        }
        if (tag.contains("lastActivatedMonumentId", Tag.TAG_STRING)) {
            data.lastActivatedMonumentId = tag.getString("lastActivatedMonumentId");
        }
        data.nextMonumentActivationGameTime =
                Math.max(0, tag.getLong("nextMonumentActivationGameTime"));
        return data;
    }
}
