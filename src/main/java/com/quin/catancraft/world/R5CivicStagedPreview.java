package com.quin.catancraft.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.data.TerritoryData;
import com.quin.catancraft.map.MapAnchor;
import com.quin.catancraft.map.MapDefinitionManager;
import com.quin.catancraft.map.TerritoryDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Model-only, read-only preview of ordered R5 civic dependencies.
 *
 * Each prerequisite is checked against live expected-before block states, then
 * projected into an in-memory overlay. The civic write/support/air conditions
 * are checked against the projected overlay, WITHOUT editing the level.
 * This is diagnostic evidence, never placement authorization. In particular,
 * reservations, block-entity restoration, economy and crash-safe grouped
 * transactions have NOT been implemented.
 */
public final class R5CivicStagedPreview {
    private static final int MAX_RECORDS=85_000;
    private static final int MAX_DETAILS=10;

    public record Report(boolean matched,int checks,int mismatches,
                         String headline,List<String> details) {}
    private static final class Audit {
        int checks;
        int mismatches;
        final List<String> details=new ArrayList<>();
        final Map<Long,String> projected=new HashMap<>();
        final List<String> steps=new ArrayList<>();
        void fail(String text) {
            mismatches++;
            if(details.size()<MAX_DETAILS)details.add("REVIEW "+text);
            if(mismatches>=MAX_DETAILS)
                throw new IllegalStateException("At least "+MAX_DETAILS+
                        " mismatches. Stop and review the first reported cells.");
        }
        void note(String text) {
            if(details.size()<MAX_DETAILS) details.add(text);
        }
        void checked() {
            if(++checks>MAX_RECORDS)
                throw new IllegalStateException("Read-only preview comparison cap exceeded.");
        }
    }
    private R5CivicStagedPreview() {}

    private static String id(String input) {
        String value=input.trim().toLowerCase(Locale.ROOT);
        if(!value.matches("[a-p]"))throw new IllegalArgumentException("City must be a-p.");
        return value;
    }
    private static String kind(String input) {
        return switch(input.trim().toLowerCase(Locale.ROOT)) {
            case "commercial", "commercial_district" -> "COMMERCIAL_DISTRICT";
            case "logistics", "logistics_center" -> "LOGISTICS_CENTER";
            default -> throw new IllegalArgumentException("Civic must be commercial or logistics.");
        };
    }
    private static BlockPos pos(JsonArray a) {
        if(a==null||a.size()!=3)throw new IllegalStateException("Malformed contract coordinate.");
        return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());
    }
    private static JsonObject json(ZipFile zip,String name,long maxBytes)throws Exception {
        ZipEntry entry=zip.getEntry(name);
        if(entry==null||entry.getSize()<1||entry.getSize()>maxBytes)
            throw new IllegalStateException("Missing or oversized pinned contract: "+name);
        try(InputStreamReader reader=new InputStreamReader(zip.getInputStream(entry),
                StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
    private static void center(JsonArray anchor,int rotation,TerritoryDefinition city) {
        MapAnchor active=city.cityCenter();
        if(active==null||anchor==null||anchor.size()!=3||
                active.x()!=anchor.get(0).getAsInt()||
                active.y()!=anchor.get(1).getAsInt()||
                active.z()!=anchor.get(2).getAsInt()||active.rotation()!=rotation)
            throw new IllegalStateException("City center/rotation differs from pinned R5.");
    }
    private static String block(ServerLevel level,BlockPos p,TerritoryDefinition city,Audit audit) {
        audit.checked();
        if(!city.restorationRegion().contains(p)||!city.siegeRegion().contains(p))
            throw new IllegalStateException("Outside city authorized read bounds: "+p);
        if(!level.hasChunkAt(p))
            throw new IllegalStateException("UNLOADED chunk (no force loading): "+p);
        return canonical(level.getBlockState(p));
    }
    private static String planned(ServerLevel level,BlockPos p,TerritoryDefinition city,Audit audit) {
        String projected=audit.projected.get(p.asLong());
        // Even overlay values require a loaded live chunk and valid city bounds.
        if(projected!=null) {
            audit.checked();
            if(!city.restorationRegion().contains(p)||!city.siegeRegion().contains(p)||
                    !level.hasChunkAt(p))
                throw new IllegalStateException("Projected cell out of bounds/unloaded: "+p);
            return projected;
        }
        return block(level,p,city,audit);
    }
    private static void expect(String actual,String expected,BlockPos p,String stage,Audit audit) {
        if(!actual.equals(expected))audit.fail(stage+" at "+p+
                " expected "+expected+" but projected/live="+actual);
    }
    private static void safeVoxel(ServerLevel level,BlockPos p,String after,String stage,
                                  TerritoryDefinition city,Audit audit) {
        // Diagnostic refuses known inventories/fluids and incomplete chunk data.
        if(!city.restorationRegion().contains(p)||!city.siegeRegion().contains(p)||
                !level.hasChunkAt(p))
            throw new IllegalStateException(stage+" out of bounds/unloaded at "+p);
        if(level.getBlockEntity(p)!=null||!level.getFluidState(p).isEmpty())
            audit.fail(stage+" touches a live block entity or fluid at "+p);
        if(after.isBlank())throw new IllegalStateException("Missing desired block state at "+p);
    }
    private static void group(ZipFile index,String id,String groupId,
                              ServerLevel level,TerritoryDefinition city,Audit audit)
            throws Exception {
        JsonObject group=json(index,"contracts/groups/"+id.toUpperCase(Locale.ROOT)+
                "/"+groupId+".json",2_000_000L);
        if(!id.equalsIgnoreCase(group.get("territory_id").getAsString())||
                !groupId.equals(group.get("local_group_id").getAsString())||
                !city.dimension().equals(group.get("dimension").getAsString())||
                !"PASS_STATIC_ACTIVATED_R2_FIXTURE".equals(group.get("status").getAsString()))
            throw new IllegalStateException("Unapproved dependency contract "+groupId);
        center(group.getAsJsonArray("city_center"),group.get("rotation").getAsInt(),city);
        JsonArray changes=group.getAsJsonArray("changes");
        if(changes==null||changes.size()<1||changes.size()>2500)
            throw new IllegalStateException("Unsupported R5 group size: "+groupId);
        Set<Long> unique=new HashSet<>();
        Map<Long,String> step=new HashMap<>();
        for(JsonElement element:changes) {
            JsonObject row=element.getAsJsonObject();
            BlockPos p=pos(row.getAsJsonArray("world_xyz"));
            if(!unique.add(p.asLong()))
                throw new IllegalStateException("Repeated group voxel "+p);
            String before=row.get("expected_before_blockstate").getAsString();
            String after=row.get("after_blockstate").getAsString();
            expect(planned(level,p,city,audit),before,p,groupId,audit);
            safeVoxel(level,p,after,groupId,city,audit);
            step.put(p.asLong(),after);
        }
        // Never project any portion of a mismatching prerequisite.
        if(audit.mismatches>0)throw new IllegalStateException(
                "Dependency "+groupId+" did not match the live old-state baseline.");
        audit.projected.putAll(step);
        audit.steps.add(groupId+" ("+changes.size()+" changes projected)");
    }
    private static JsonObject socket(JsonObject source,String id,String module) {
        if(!"MAIN_CHAT_CIVIC_REGISTRATION_REQUIRED".equals(
                source.get("status").getAsString()))
            throw new IllegalStateException("Unexpected civic registry revision.");
        for(JsonElement element:source.getAsJsonArray("rows")) {
            JsonObject row=element.getAsJsonObject();
            if(id.equalsIgnoreCase(row.get("territory_id").getAsString())&&
                    module.equals(row.get("module_id").getAsString()))
                return row;
        }
        throw new IllegalStateException("No civic proposal for this city.");
    }
    public static Report preview(MinecraftServer server,String requestedCity,String requestedKind) {
        Audit audit=new Audit();
        String label=requestedCity+"/"+requestedKind;
        CatanCraft.LOGGER.info("R5 STAGED PREVIEW BEGIN {} (read-only)",label);
        try {
            String id=id(requestedCity),module=kind(requestedKind);
            TerritoryDefinition city=MapDefinitionManager.territory(id);
            if(city==null||city.cityCenter()==null||
                    city.restorationRegion()==null||city.siegeRegion()==null)
                throw new IllegalStateException("Current city definition incomplete.");
            ServerLevel level=null;
            for(ServerLevel candidate:server.getAllLevels())
                if(candidate.dimension().location().toString().equals(city.dimension()))
                    level=candidate;
            if(level==null)throw new IllegalStateException("City dimension not available.");
            TerritoryData territory=CatanSavedData.get(server).territory(id);
            if(territory==null||territory.ownerNationId()==null||
                    CatanSavedData.get(server).nation(territory.ownerNationId())==null)
                throw new IllegalStateException("City must belong to an active nation.");
            final long existing=territory.buildings().stream()
                    .filter(b->module.equals(b.type().name())).count();
            if(existing>1)
                throw new IllegalStateException("Duplicate purchased civic save records need reconciliation.");

            // Hash-pin BOTH sources, even though this code will never write to disk/world.
            R5ReadOnlyPreflight.verifyArchive();
            R5ReadOnlyPreflight.verifyIndex();
            Path indexPath=FMLPaths.CONFIGDIR.get().resolve("catancraft")
                    .resolve(R5ReadOnlyPreflight.INDEX_NAME);
            if(!Files.isRegularFile(indexPath))
                throw new IllegalStateException("R5 compact index missing.");
            try(ZipFile art=new ZipFile(R5ReadOnlyPreflight.archivePath().toFile());
                ZipFile index=new ZipFile(indexPath.toFile())) {
                JsonObject registry=json(art,"contracts/city-r5-candidate-sockets.json",500_000L);
                JsonObject site=socket(registry,id,module);
                if(!"PASS_STATIC_CANDIDATE".equals(site.get("status").getAsString())||
                        site.get("approved").getAsBoolean()||
                        !city.dimension().equals(site.get("dimension").getAsString()))
                    throw new IllegalStateException("Civic proposal has not passed pinned static review.");
                center(site.getAsJsonArray("city_center"),site.get("rotation").getAsInt(),city);
                String basename=module.toLowerCase(Locale.ROOT);
                String deltaFile="contracts/civic-deltas/"+id.toUpperCase(Locale.ROOT)+
                        "_"+basename+".json";
                if(!deltaFile.equals(site.get("delta_file").getAsString()))
                    throw new IllegalStateException("Unrecognized civic source path.");
                JsonObject civic=json(art,deltaFile,5_000_000L);
                if(!id.equalsIgnoreCase(civic.get("territory_id").getAsString())||
                        !module.equals(civic.get("module_id").getAsString())||
                        !"PASS_STATIC".equals(civic.get("status").getAsString())||
                        !pos(site.getAsJsonArray("world_anchor_xyz"))
                                .equals(pos(civic.getAsJsonArray("world_anchor_xyz"))))
                    throw new IllegalStateException("Civic delta/source identity mismatch.");
                JsonArray deps=civic.getAsJsonArray("dependencies");
                List<String> expected=module.equals("COMMERCIAL_DISTRICT")
                        ?List.of("activated_city",id.toUpperCase(Locale.ROOT)+"_cleanup_tree_16_10",
                                 id.toUpperCase(Locale.ROOT)+"_commercial_sidewalk")
                        :List.of("activated_city",id.toUpperCase(Locale.ROOT)+
                                "_logistics_service_connection");
                List<String> declared=new ArrayList<>();
                for(JsonElement e:deps)declared.add(e.getAsString());
                if(!declared.equals(expected))
                    throw new IllegalStateException("Unsupported dependency order "+declared);
                for(int i=1;i<declared.size();i++) {
                    String prefix=id.toUpperCase(Locale.ROOT)+"_";
                    String dependency=declared.get(i);
                    if(!dependency.startsWith(prefix))
                        throw new IllegalStateException("Cross-city prerequisite.");
                    group(index,id,dependency.substring(prefix.length()),level,city,audit);
                }
                // Check actual Civic writes against the post-dependency virtual state.
                JsonArray writes=civic.getAsJsonArray("writes");
                if(writes==null||writes.size()!=site.get("write_count").getAsInt()||
                        writes.size()>MAX_RECORDS)
                    throw new IllegalStateException("Civic write count/source changed.");
                Set<Long> writeMask=new HashSet<>();
                for(JsonElement element:writes) {
                    JsonObject row=element.getAsJsonObject();
                    BlockPos p=pos(row.getAsJsonArray("world_xyz"));
                    if(!writeMask.add(p.asLong()))
                        throw new IllegalStateException("Duplicate civic write "+p);
                    expect(planned(level,p,city,audit),
                            row.get("expected_before_blockstate").getAsString(),
                            p,"civic-write",audit);
                    safeVoxel(level,p,row.get("after_blockstate").getAsString(),
                            "civic-write",city,audit);
                }
                for(JsonElement element:civic.getAsJsonArray("support_read_only_conditions")) {
                    JsonObject support=element.getAsJsonObject();
                    BlockPos p=pos(support.getAsJsonArray("world_xyz"));
                    expect(planned(level,p,city,audit),support.get("expected_state").getAsString(),
                            p,"civic-support",audit);
                }
                JsonObject bounds=site.getAsJsonObject("bounds_inclusive");
                BlockPos min=pos(bounds.getAsJsonArray("min"));
                BlockPos max=pos(bounds.getAsJsonArray("max"));
                long volume=(long)(max.getX()-min.getX()+1)*
                        (max.getY()-min.getY()+1)*(max.getZ()-min.getZ()+1);
                if(volume<1||volume>MAX_RECORDS)
                    throw new IllegalStateException("Civic bounds exceed supported preview volume.");
                for(int y=Math.max(min.getY(),city.cityCenter().y());y<=max.getY();y++)
                    for(int z=min.getZ();z<=max.getZ();z++)
                        for(int x=min.getX();x<=max.getX();x++) {
                            BlockPos p=new BlockPos(x,y,z);
                            if(!writeMask.contains(p.asLong()))
                                expect(planned(level,p,city,audit),"minecraft:air",
                                        p,"civic-source-air",audit);
                        }
                audit.note("Ordered projected steps: "+String.join(" -> ",audit.steps)+
                        " -> "+module+" ("+writes.size()+" proposed civic cells).");
                audit.note("Existing saved "+module+" purchases: "+existing+
                        ". Visual installation must never charge this legacy purchase twice.");
                audit.note("NO WORLD WRITES. Physics/entity clearance, complete R3/R4/R6 " +
                        "exclusions, payment, dependency journals and crash recovery remain unapproved.");
            }
        } catch(Exception failure) {
            audit.fail("Preview refused: "+failure.getMessage());
        }
        String headline="R5 STAGED "+label.toUpperCase(Locale.ROOT)+": "+
                audit.checks+" comparisons, "+audit.mismatches+
                " issues. READ-ONLY — no civic placement authorized.";
        CatanCraft.LOGGER.info("R5 STAGED PREVIEW END {} checks={} issues={}",
                label,audit.checks,audit.mismatches);
        return new Report(audit.mismatches==0,audit.checks,audit.mismatches,
                headline,List.copyOf(audit.details));
    }
    private static <T extends Comparable<T>> String value(BlockState state,Property<T> property) {
        return property.getName(state.getValue(property));
    }
    private static String canonical(BlockState state) {
        StringBuilder out=new StringBuilder(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        List<String> pairs=new ArrayList<>();
        for(Property<?> p:state.getProperties())
            pairs.add(p.getName()+"="+value(state,p));
        if(!pairs.isEmpty()) {
            pairs.sort(String::compareTo);
            out.append("[").append(String.join(",",pairs)).append("]");
        }
        return out.toString();
    }
}
