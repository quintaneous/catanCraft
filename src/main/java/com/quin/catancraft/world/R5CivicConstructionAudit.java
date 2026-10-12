package com.quin.catancraft.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.data.TerritoryData;
import com.quin.catancraft.map.MapDefinitionManager;
import com.quin.catancraft.map.TerritoryDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * v0.1.13: read-only precommit audit. This class has NO method that sets blocks,
 * modifies saved data, creates world journals, charges currency or loads chunks.
 * It computes a deterministic *proposed* union mask from pinned R5 contracts,
 * preserving the real original before-state for overlaps, and identifies
 * physical blockers before a future journaled civic construction pilot.
 */
public final class R5CivicConstructionAudit {
    private static final int MAX_CELLS=10000;
    private static final String INDEX=R5ReadOnlyPreflight.INDEX_NAME;
    public record Report(boolean staticReady,int stagedChecks,int unionCells,
                         int collidedCells,int plannedBlockEntities,String digest,
                         String headline,List<String> details) {}
    private record Voxel(BlockPos pos,String before,String after) {}
    private static final class State {
        final Map<Long,Voxel> union=new HashMap<>();
        final Map<Long,String> virtual=new HashMap<>();
        final List<String> details=new ArrayList<>();
        int repeatedAcrossStages;
        int blocked;
        void notice(String s) {
            if(details.size()<14)details.add(s);
            CatanCraft.LOGGER.info("R5 CIVIC AUDIT {}",s);
        }
        void blocked(String s) {
            blocked++;
            notice("BLOCKED "+s);
        }
    }
    private R5CivicConstructionAudit() {}

    private static String cityId(String s) {
        String out=s.trim().toLowerCase(Locale.ROOT);
        if(!out.matches("[a-p]"))throw new IllegalArgumentException("City must be A-P");
        return out;
    }
    private static String module(String s) {
        return switch(s.trim().toLowerCase(Locale.ROOT)) {
            case "commercial", "commercial_district" -> "COMMERCIAL_DISTRICT";
            case "logistics", "logistics_center" -> "LOGISTICS_CENTER";
            default -> throw new IllegalArgumentException("Civic must be commercial or logistics");
        };
    }
    private static BlockPos position(JsonArray a) {
        if(a==null||a.size()!=3)throw new IllegalStateException("Malformed world XYZ");
        return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());
    }
    private static JsonObject read(ZipFile zip,String key) throws Exception {
        ZipEntry entry=zip.getEntry(key);
        if(entry==null||entry.getSize()<1||entry.getSize()>6_000_000)
            throw new IllegalStateException("Invalid pinned R5 contract "+key);
        try(InputStreamReader in=new InputStreamReader(zip.getInputStream(entry),StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(in).getAsJsonObject();
        }
    }
    private static void record(State out,ServerLevel level,TerritoryDefinition city,
                               JsonArray writes,String label) {
        Set<Long> withinStep=new HashSet<>();
        if(writes==null||writes.size()<1||writes.size()>6000)
            throw new IllegalStateException("Unsafe contract size in "+label);
        for(JsonElement el:writes) {
            JsonObject row=el.getAsJsonObject();
            BlockPos p=position(row.getAsJsonArray("world_xyz"));
            long key=p.asLong();
            if(!withinStep.add(key))
                throw new IllegalStateException("Duplicate coordinate in "+label+" at "+p);
            if(!city.restorationRegion().contains(p)||!city.siegeRegion().contains(p)||
                    !level.hasChunkAt(p))
                throw new IllegalStateException("Out-of-bounds or UNLOADED "+label+" at "+p);
            String before=row.get("expected_before_blockstate").getAsString();
            String after=row.get("after_blockstate").getAsString();
            // This projection is only permitted after the complete read-only
            // staged preflight returned zero differences.
            String prior=out.virtual.get(key);
            if(prior!=null&&!prior.equals(before))
                throw new IllegalStateException("Inconsistent staged old-state "+label+" at "+p);
            if(out.union.containsKey(key)) {
                Voxel original=out.union.get(key);
                out.union.put(key,new Voxel(p,original.before(),after));
                out.repeatedAcrossStages++;
            } else {
                String actual=canonical(level,p);
                if(!actual.equals(before))
                    throw new IllegalStateException("Live world changed during audit: "+p+
                            " expected="+before+" actual="+actual);
                out.union.put(key,new Voxel(p,actual,after));
            }
            out.virtual.put(key,after);
            if(out.union.size()>MAX_CELLS)
                throw new IllegalStateException("Civic union exceeds fixed safety cap");
        }
    }
    private static String canonical(ServerLevel level,BlockPos p) {
        var block=level.getBlockState(p);
        StringBuilder name=new StringBuilder(net.minecraft.core.registries.BuiltInRegistries
                .BLOCK.getKey(block.getBlock()).toString());
        List<String> props=new ArrayList<>();
        for(var prop:block.getProperties())
            props.add(property(block,prop));
        props.sort(String::compareTo);
        if(!props.isEmpty()) name.append("[").append(String.join(",",props)).append("]");
        return name.toString();
    }
    private static <T extends Comparable<T>> String property(
            net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.level.block.state.properties.Property<T> p) {
        return p.getName()+"="+p.getName(state.getValue(p));
    }
    private static String digest(State audit,String city,String kind) throws Exception {
        MessageDigest sha=MessageDigest.getInstance("SHA-256");
        sha.update(("R5-1|"+city+"|"+kind+"\n").getBytes(StandardCharsets.UTF_8));
        List<Voxel> ordered=new ArrayList<>(audit.union.values());
        ordered.sort(Comparator.comparingLong(v->v.pos().asLong()));
        for(Voxel v:ordered) {
            String line=v.pos().getX()+","+v.pos().getY()+","+v.pos().getZ()+
                    "|"+v.before()+"|"+v.after()+"\n";
            sha.update(line.getBytes(StandardCharsets.UTF_8));
        }
        StringBuilder s=new StringBuilder();
        for(byte b:sha.digest())s.append(String.format("%02x",b&0xff));
        return s.toString();
    }
    public static Report inspect(MinecraftServer server,String requestedCity,String requestedKind) {
        State audit=new State();
        String label=requestedCity+"/"+requestedKind;
        int checks=0,plannedTileEntities=0,collisions=0;
        String hash="NOT_COMPUTED";
        try {
            String id=cityId(requestedCity),kind=module(requestedKind);
            R5CivicStagedPreview.Report preview=R5CivicStagedPreview.preview(server,id,kind);
            checks=preview.checks();
            if(!preview.matched()) {
                audit.blocked("Staged preflight failed: "+preview.mismatches()+
                        " issues. Resolve without manual clearing.");
                for(String item:preview.details())
                    if(audit.details.size()<14) audit.notice(item);
                throw new IllegalStateException("Prerequisite snapshot is not exact");
            }
            TerritoryDefinition city=MapDefinitionManager.territory(id);
            if(city==null||city.restorationRegion()==null||city.siegeRegion()==null)
                throw new IllegalStateException("Unrecognized city bounds");
            ServerLevel level=null;
            for(ServerLevel current:server.getAllLevels())
                if(current.dimension().location().toString().equals(city.dimension()))
                    level=current;
            if(level==null)throw new IllegalStateException("World dimension unavailable");
            TerritoryData saved=CatanSavedData.get(server).territory(id);
            if(saved==null||saved.ownerNationId()==null||
                    CatanSavedData.get(server).nation(saved.ownerNationId())==null)
                throw new IllegalStateException("Territory not actively owned");
            long legacy=saved.buildings().stream().filter(b->b.type().name().equals(kind)).count();
            if(legacy!=1) {
                audit.blocked("Need exactly one existing saved "+kind+" purchase, found "+legacy+
                        ". No duplicate charge, new purchase or save repair supported.");
                throw new IllegalStateException("Unreconciled legacy civic purchase");
            }
            R5ReadOnlyPreflight.verifyArchive();
            R5ReadOnlyPreflight.verifyIndex();
            Path indexFile=FMLPaths.CONFIGDIR.get().resolve("catancraft").resolve(INDEX);
            if(!Files.isRegularFile(indexFile))
                throw new IllegalStateException("Missing pinned R5 compact index");
            try(ZipFile art=new ZipFile(R5ReadOnlyPreflight.archivePath().toFile());
                ZipFile compact=new ZipFile(indexFile.toFile())) {
                String prefix=id.toUpperCase(Locale.ROOT);
                List<String> prereqs=kind.equals("COMMERCIAL_DISTRICT")
                        ?List.of("cleanup_tree_16_10","commercial_sidewalk")
                        :List.of("logistics_service_connection");
                for(String group:prereqs) {
                    JsonObject contract=read(compact,"contracts/groups/"+prefix+"/"+group+".json");
                    if(!group.equals(contract.get("local_group_id").getAsString())||
                            !prefix.equals(contract.get("territory_id").getAsString()))
                        throw new IllegalStateException("Prerequisite identity mismatch "+group);
                    record(audit,level,city,contract.getAsJsonArray("changes"),group);
                }
                String location="contracts/civic-deltas/"+prefix+"_"+kind.toLowerCase(Locale.ROOT)+".json";
                JsonObject civic=read(art,location);
                if(!kind.equals(civic.get("module_id").getAsString())||
                        !prefix.equals(civic.get("territory_id").getAsString())||
                        !"PASS_STATIC".equals(civic.get("status").getAsString()))
                    throw new IllegalStateException("Civic identity/status mismatch");
                record(audit,level,city,civic.getAsJsonArray("writes"),kind);
            }
            hash=digest(audit,id,kind);
            List<Voxel> solids=new ArrayList<>();
            BlockPos min=null,max=null;
            for(Voxel cell:audit.union.values()) {
                BlockPos p=cell.pos();
                if(level.getBlockEntity(p)!=null||!level.getFluidState(p).isEmpty()) {
                    audit.blocked("Existing inventory/fluid on construction mask "+p);
                    break;
                }
                // Vanilla barrels are inventory block entities even when initially empty.
                if(cell.after().startsWith("minecraft:barrel"))plannedTileEntities++;
                if(!cell.after().equals("minecraft:air")&&!cell.after().equals(cell.before())) {
                    solids.add(cell);
                    if(min==null) {min=p;max=p;}
                    else {
                        min=new BlockPos(Math.min(min.getX(),p.getX()),
                                Math.min(min.getY(),p.getY()),Math.min(min.getZ(),p.getZ()));
                        max=new BlockPos(Math.max(max.getX(),p.getX()),
                                Math.max(max.getY(),p.getY()),Math.max(max.getZ(),p.getZ()));
                    }
                }
            }
            if(min!=null) {
                AABB aabb=new AABB(min.getX(),min.getY(),min.getZ(),
                        max.getX()+1,max.getY()+1,max.getZ()+1);
                List<Entity> entities=level.getEntities((Entity)null,aabb,Entity::isAlive);
                for(Entity entity:entities) {
                    AABB entityBounds=entity.getBoundingBox();
                    for(Voxel solid:solids) {
                        if(entityBounds.intersects(new AABB(solid.pos()))) {
                            collisions++;
                            if(collisions<=4)
                                audit.notice("ENTITY OCCUPANCY at "+solid.pos()+
                                        " type="+entity.getType().toString());
                            break;
                        }
                    }
                }
            }
            if(collisions>0)audit.blocked("Entity AABB overlaps "+collisions+
                    " living entities in intended non-air construction cells.");
            audit.notice("Proposed union="+audit.union.size()+" unique block positions; "+
                    audit.repeatedAcrossStages+" dependency-to-civic repeated coordinates.");
            audit.notice("Source-validated legacy purchase="+legacy+
                    ", no economic purchase or currency charge in this release.");
            audit.notice("Proposed inventory block entities="+plannedTileEntities+
                    " (barrels); must support NBT/inventory preservation before any install/rollback.");
            audit.notice("SHA-256 plan digest="+hash);
            audit.notice("REMAINING ENGINEERING GATES: inventory-safe journal/restart recovery, "+
                    "R3/R4/R6 runtime exclusions, actual block physics, payment/lock handling.");
        } catch(Exception ex) {
            audit.blocked("Construction audit refused: "+ex.getMessage());
        }
        boolean zeroPhysicalBlockers=audit.blocked==0;
        String headline="R5 CONSTRUCTION AUDIT "+label.toUpperCase(Locale.ROOT)+
                ": staged checks="+checks+", proposed unique="+audit.union.size()+
                ", entity collisions="+collisions+", blockers="+audit.blocked+
                ". READ-ONLY, ALWAYS NO CONSTRUCTION APPROVAL.";
        CatanCraft.LOGGER.info("R5 CONSTRUCTION AUDIT END {} ready={} checks={} union={} blockers={}",
                label,zeroPhysicalBlockers,checks,audit.union.size(),audit.blocked);
        return new Report(zeroPhysicalBlockers,checks,audit.union.size(),
                collisions,plannedTileEntities,hash,headline,List.copyOf(audit.details));
    }
}
