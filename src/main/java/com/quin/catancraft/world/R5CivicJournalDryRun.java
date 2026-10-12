package com.quin.catancraft.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.map.MapDefinitionManager;
import com.quin.catancraft.map.TerritoryDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * v0.1.14 - IN-MEMORY ONLY model of a R5 multi-stage civic journal.
 * The model is discarded at the end of the command. No world/save/config writes
 * and absolutely no actual transaction, reservation, payment or rollback.
 *
 * A future author must not treat this preview JSON as a committed journal.
 */
public final class R5CivicJournalDryRun {
    private static final int MAX_STAGE=6000;
    private static final int MAX_UNION=10000;
    private record StepCell(long packed,BlockPos pos,String before,String after) {}
    public record Result(boolean modeled,int stages,int stepWrites,int uniqueCells,
                         int reusedCells,int beforeNbt,int targetBarrels,
                         String fingerprint,String headline,List<String> messages) {}
    private R5CivicJournalDryRun() {}

    private static String city(String value) {
        String normalized=value.trim().toLowerCase(Locale.ROOT);
        if(!normalized.matches("[a-p]"))
            throw new IllegalArgumentException("Territory must be A-P.");
        return normalized;
    }
    private static String kind(String value) {
        return switch(value.trim().toLowerCase(Locale.ROOT)) {
            case "commercial","commercial_district" -> "COMMERCIAL_DISTRICT";
            case "logistics","logistics_center" -> "LOGISTICS_CENTER";
            default -> throw new IllegalArgumentException("Civic must be commercial or logistics.");
        };
    }
    private static BlockPos pos(JsonArray coords) {
        if(coords==null||coords.size()!=3)
            throw new IllegalStateException("Missing/malformed world XYZ.");
        return new BlockPos(coords.get(0).getAsInt(),coords.get(1).getAsInt(),
                coords.get(2).getAsInt());
    }
    private static JsonObject json(ZipFile zip,String key) throws Exception {
        ZipEntry e=zip.getEntry(key);
        if(e==null||e.getSize()<1||e.getSize()>6_000_000)
            throw new IllegalStateException("Missing or oversized contract "+key);
        try(InputStreamReader in=new InputStreamReader(zip.getInputStream(e),
                StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(in).getAsJsonObject();
        }
    }
    private static <T extends Comparable<T>> String prop(BlockState state,Property<T> p) {
        return p.getName()+"="+p.getName(state.getValue(p));
    }
    private static String canonical(BlockState state) {
        StringBuilder out=new StringBuilder(BuiltInRegistries.BLOCK
                .getKey(state.getBlock()).toString());
        List<String> props=new ArrayList<>();
        for(Property<?> p:state.getProperties())props.add(prop(state,p));
        props.sort(String::compareTo);
        if(!props.isEmpty())out.append("[").append(String.join(",",props)).append("]");
        return out.toString();
    }
    private static String sha(String raw) throws Exception {
        byte[] bytes=MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(StandardCharsets.UTF_8));
        StringBuilder result=new StringBuilder();
        for(byte b:bytes)result.append(String.format("%02x",b&0xff));
        return result.toString();
    }
    private static List<StepCell> step(JsonArray rows,String expectedId,
                                       String stage,ServerLevel level,
                                       TerritoryDefinition city,
                                       Map<Long,String> current,
                                       Map<Long,String> original,
                                       Map<Long,String> tileNbt,
                                       JsonArray journalEntries) {
        if(rows==null||rows.size()<1||rows.size()>MAX_STAGE)
            throw new IllegalStateException("Invalid stage size "+stage);
        Set<Long> seen=new HashSet<>();
        List<StepCell> output=new ArrayList<>();
        for(JsonElement element:rows) {
            JsonObject cell=element.getAsJsonObject();
            BlockPos p=pos(cell.getAsJsonArray("world_xyz"));
            long packed=p.asLong();
            if(!seen.add(packed))
                throw new IllegalStateException("Duplicate within stage "+stage+" at "+p);
            if(!city.restorationRegion().contains(p)||!city.siegeRegion().contains(p)||
                    !level.hasChunkAt(p))
                throw new IllegalStateException("Out of bounds/unloaded stage "+stage+" "+p);
            String before=cell.get("expected_before_blockstate").getAsString();
            String after=cell.get("after_blockstate").getAsString();
            String previous=current.get(packed);
            if(previous==null) {
                BlockState live=level.getBlockState(p);
                previous=canonical(live);
                if(!before.equals(previous))
                    throw new IllegalStateException("Live cell drift at "+p+" stage "+stage);
                // Source block entities are incompatible with this proposed
                // construction until explicit inventory-safe serialization exists.
                BlockEntity oldTile=level.getBlockEntity(p);
                if(oldTile!=null) {
                    CompoundTag tag=oldTile.saveWithFullMetadata();
                    tileNbt.put(packed,tag.toString());
                    throw new IllegalStateException("Existing block entity at "+p+
                            " (captured in memory; transaction NOT authorized).");
                }
                if(!level.getFluidState(p).isEmpty())
                    throw new IllegalStateException("Source fluid at "+p);
                original.put(packed,previous);
                if(original.size()>MAX_UNION)
                    throw new IllegalStateException("Transaction union size cap exceeded.");
            }
            if(!before.equals(previous))
                throw new IllegalStateException("Stage ordering conflict at "+p+
                        " in "+stage);
            JsonObject snapshot=new JsonObject();
            snapshot.addProperty("pos",p.getX()+","+p.getY()+","+p.getZ());
            snapshot.addProperty("before",before);
            snapshot.addProperty("after",after);
            journalEntries.add(snapshot);
            current.put(packed,after);
            output.add(new StepCell(packed,p,before,after));
        }
        return output;
    }

    public static Result preview(MinecraftServer server,String requestedCity,String requestedKind) {
        List<String> details=new ArrayList<>();
        int stages=0,writes=0,unique=0,reused=0,barrels=0,tiles=0;
        String fingerprint="NOT_COMPUTED";
        boolean complete=false;
        String label=requestedCity+"/"+requestedKind;
        CatanCraft.LOGGER.info("R5 JOURNAL MODEL BEGIN {} READ-ONLY",label);
        try {
            String id=city(requestedCity),module=kind(requestedKind);
            // The already tested v0.1.13 read-only audit enforces civic purchase,
            // source air, loaded chunks, live-entity AABB, and SHA-pinned files.
            R5CivicConstructionAudit.Report audited=
                    R5CivicConstructionAudit.inspect(server,id,module);
            if(!audited.staticReady())
                throw new IllegalStateException("Construction audit reports blockers. "+
                        "Run /catan r5 constructioncheck "+id+" "+
                        requestedKind+" for details.");
            TerritoryDefinition territory=MapDefinitionManager.territory(id);
            if(territory==null||territory.cityCenter()==null||
                    territory.restorationRegion()==null||territory.siegeRegion()==null)
                throw new IllegalStateException("Unknown city map.");
            ServerLevel level=null;
            for(ServerLevel l:server.getAllLevels())
                if(territory.dimension().equals(l.dimension().location().toString()))
                    level=l;
            if(level==null)throw new IllegalStateException("City dimension unavailable.");
            R5ReadOnlyPreflight.verifyArchive();
            R5ReadOnlyPreflight.verifyIndex();
            Path index=FMLPaths.CONFIGDIR.get().resolve("catancraft")
                    .resolve(R5ReadOnlyPreflight.INDEX_NAME);
            if(!Files.isRegularFile(index))
                throw new IllegalStateException("Pinned compact index missing.");
            String world=server.getWorldPath(LevelResource.ROOT).toRealPath().toString();
            JsonObject virtualJournal=new JsonObject();
            virtualJournal.addProperty("format","R5_CIVIC_JOURNAL_MODEL_V1");
            virtualJournal.addProperty("state","SIMULATED_IN_MEMORY_ONLY");
            virtualJournal.addProperty("world_root",world);
            virtualJournal.addProperty("dimension",territory.dimension());
            virtualJournal.addProperty("territory",id);
            virtualJournal.addProperty("module",module);
            virtualJournal.addProperty("source_union_digest",audited.digest());
            virtualJournal.addProperty("purchase_policy","EXISTING_SAVED_INSTANCE_NO_RECHARGE");
            JsonArray stagedJournal=new JsonArray();
            virtualJournal.add("stages",stagedJournal);

            Map<Long,String> virtual=new HashMap<>();
            Map<Long,String> initial=new LinkedHashMap<>();
            Map<Long,String> beforeTileNbt=new HashMap<>();
            List<List<StepCell>> reversible=new ArrayList<>();
            try(ZipFile zip=new ZipFile(R5ReadOnlyPreflight.archivePath().toFile());
                ZipFile compact=new ZipFile(index.toFile())) {
                String uppercase=id.toUpperCase(Locale.ROOT);
                List<String> needed=module.equals("COMMERCIAL_DISTRICT")
                        ?List.of("cleanup_tree_16_10","commercial_sidewalk")
                        :List.of("logistics_service_connection");
                for(String neededGroup:needed) {
                    JsonObject group=json(compact,"contracts/groups/"+uppercase+
                            "/"+neededGroup+".json");
                    if(!id.equalsIgnoreCase(group.get("territory_id").getAsString())||
                            !neededGroup.equals(group.get("local_group_id").getAsString())||
                            !territory.dimension().equals(group.get("dimension").getAsString()))
                        throw new IllegalStateException("Dependency contract identity mismatch.");
                    JsonObject stage=new JsonObject();
                    stage.addProperty("name",neededGroup);
                    JsonArray entries=new JsonArray();
                    stage.add("writes",entries);
                    List<StepCell> applied=step(group.getAsJsonArray("changes"),id,
                            neededGroup,level,territory,virtual,initial,beforeTileNbt,entries);
                    reversible.add(applied);
                    stagedJournal.add(stage);
                    stages++;
                    writes+=applied.size();
                }
                String source="contracts/civic-deltas/"+uppercase+"_"+
                        module.toLowerCase(Locale.ROOT)+".json";
                JsonObject civic=json(zip,source);
                if(!uppercase.equals(civic.get("territory_id").getAsString())||
                        !module.equals(civic.get("module_id").getAsString())||
                        !"PASS_STATIC".equals(civic.get("status").getAsString()))
                    throw new IllegalStateException("Unknown civic contract identity.");
                JsonObject finalStage=new JsonObject();
                finalStage.addProperty("name",module);
                JsonArray entries=new JsonArray();
                finalStage.add("writes",entries);
                List<StepCell> placed=step(civic.getAsJsonArray("writes"),id,module,
                        level,territory,virtual,initial,beforeTileNbt,entries);
                reversible.add(placed);
                stagedJournal.add(finalStage);
                stages++;
                writes+=placed.size();
                for(StepCell row:placed)
                    if(row.after().startsWith("minecraft:barrel["))barrels++;
            }
            unique=initial.size();
            reused=writes-unique;
            tiles=beforeTileNbt.size();
            if(unique!=audited.unionCells())
                throw new IllegalStateException("Union cell count changed since construction audit.");
            if(tiles!=0)
                throw new IllegalStateException("Source inventory NBT cannot be overwritten.");
            if(barrels!=audited.plannedBlockEntities())
                throw new IllegalStateException("New inventory block entity count changed.");
            // Model rollback in reverse dependency order, never touching the level.
            for(int s=reversible.size()-1;s>=0;s--) {
                List<StepCell> back=reversible.get(s);
                for(int i=back.size()-1;i>=0;i--) {
                    StepCell cell=back.get(i);
                    if(!cell.after().equals(virtual.get(cell.packed())))
                        throw new IllegalStateException("In-memory rollback stage drift at "+cell.pos());
                    virtual.put(cell.packed(),cell.before());
                }
            }
            for(Map.Entry<Long,String> entry:initial.entrySet())
                if(!entry.getValue().equals(virtual.get(entry.getKey())))
                    throw new IllegalStateException("In-memory rollback failed to restore original cell.");
            virtualJournal.addProperty("original_cell_count",unique);
            virtualJournal.addProperty("stage_write_count",writes);
            virtualJournal.addProperty("future_new_barrels",barrels);
            virtualJournal.addProperty("rollback_model","PASS_NO_PHYSICS");
            fingerprint=sha(virtualJournal.toString());
            details.add("Stages modeled="+stages+"; ordered writes="+writes+
                    "; unique original snapshots="+unique+
                    "; positions reused across stages="+reused+".");
            details.add("Simulation rollback roundtrip=PASS for all "+unique+
                    " original states; NO actual blocks were written or restored.");
            details.add("Source block entity NBT snapshots="+tiles+
                    "; new barrel block entities="+barrels+". NBT/inventory "+
                    "commit-and-recovery path STILL NOT IMPLEMENTED.");
            details.add("In-memory journal SHA-256="+fingerprint+
                    ". No journal file created and no saved state changed.");
            details.add("CRITICAL: physics/lighting, filesystem fsync, interruptions, "+
                    "live purchase locks, protectors and real rollback are NOT tested.");
            complete=true;
        } catch(Exception ex) {
            details.add("BLOCKED "+ex.getMessage());
        }
        String headline="R5 JOURNAL MODEL "+label.toUpperCase(Locale.ROOT)+
                ": "+(complete?"PASS SIMULATION":"REFUSED")+
                "; stages="+stages+"; unique="+unique+
                "; READ-ONLY, NO CONSTRUCTION AUTHORIZED.";
        CatanCraft.LOGGER.info("R5 JOURNAL MODEL END {} pass={} stages={} unique={}",
                label,complete,stages,unique);
        return new Result(complete,stages,writes,unique,reused,tiles,barrels,
                fingerprint,headline,List.copyOf(details));
    }
}
