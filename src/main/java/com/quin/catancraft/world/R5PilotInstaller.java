package com.quin.catancraft.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;
import com.google.gson.Gson;
import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.data.TerritoryData;
import com.quin.catancraft.map.MapAnchor;
import com.quin.catancraft.map.MapDefinitionManager;
import com.quin.catancraft.map.TerritoryDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.io.InputStreamReader;

/**
 * R5-2 PILOT: opt-in, single-group test-world writes with an on-disk,
 * actual-before journal. Deliberately cannot paste houses or civic structures.
 * Do not grant this to ordinary players or run on an unbacked-up live world.
 */
public final class R5PilotInstaller {
    private static final String SOURCE_INDEX = "R5_READ_ONLY_PROBE_INDEX.zip";
    private static final String INDEX_SHA256 =
            "4f5a445347adbb62e73d1c38bfab5ff850c7307733a619847d9eeac9245834ba";
    private static final String APPROVAL_NAME = "R5_DISPOSABLE_WORLD_ONE_SHOT.txt";
    private static final String APPROVAL_TEXT =
            "I_BACKED_UP_THIS_DISPOSABLE_WORLD_AND_APPROVE_ONE_R5_WRITE";
    private static final Set<String> APPROVED_GROUPS = Set.of(
            "front_public_park", "logistics_service_connection",
            "commercial_sidewalk");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record Result(boolean success, String message) {}
    private record Change(BlockPos pos, BlockState before, BlockState after) {}
    private R5PilotInstaller() {}

    private static Path root() {
        return FMLPaths.CONFIGDIR.get().resolve("catancraft");
    }
    private static Path journal(String city, String group) {
        return root().resolve("r5_test_journal")
                .resolve(city.toUpperCase(Locale.ROOT) + "_" + group + ".json");
    }
    private static void requireIndex() throws Exception {
        Path path = root().resolve(SOURCE_INDEX);
        if (!Files.isRegularFile(path))
            throw new IllegalStateException("Missing verified R5 index ZIP.");
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        try (InputStream in=Files.newInputStream(path)) {
            byte[] b=new byte[32768];
            int n; while((n=in.read(b))!=-1) hash.update(b,0,n);
        }
        StringBuilder got=new StringBuilder();
        for(byte b:hash.digest()) got.append(String.format("%02x",b&0xff));
        if(!INDEX_SHA256.equals(got.toString()))
            throw new IllegalStateException("R5 index is not the pinned v0.1.6 original.");
    }
    private static String norm(String id) {
        String value=id.toLowerCase(Locale.ROOT).trim();
        if (!value.matches("[a-p]")) throw new IllegalArgumentException("City must be a-p");
        return value;
    }
    private static String group(String id) {
        String value=id.toLowerCase(Locale.ROOT).trim();
        if(!APPROVED_GROUPS.contains(value))
            throw new IllegalArgumentException("Only pilot streetscape groups " +
                    APPROVED_GROUPS + " are currently installable; houses/civics remain blocked.");
        return value;
    }
    private static ServerLevel requireLevel(MinecraftServer server,TerritoryDefinition city) {
        for(ServerLevel l:server.getAllLevels())
            if(l.dimension().location().toString().equals(city.dimension())) return l;
        throw new IllegalStateException("Target dimension is unavailable.");
    }
    private static TerritoryDefinition requireCity(MinecraftServer server,String id,
                                                  boolean requireOwner) {
        TerritoryDefinition def=MapDefinitionManager.territory(id);
        if(def==null||def.cityCenter()==null||def.restorationRegion()==null)
            throw new IllegalStateException("City map, center or restoration bounds missing.");
        TerritoryData t=CatanSavedData.get(server).territory(id);
        if(requireOwner && (t==null||t.ownerNationId()==null ||
                CatanSavedData.get(server).nation(t.ownerNationId())==null))
            throw new IllegalStateException("City must belong to an active nation.");
        return def;
    }
    private static JsonObject readIndexed(String city,String group)throws Exception {
        requireIndex();
        try(ZipFile zip=new ZipFile(root().resolve(SOURCE_INDEX).toFile())) {
            String name="contracts/groups/"+city.toUpperCase(Locale.ROOT)+"/"+group+".json";
            ZipEntry entry=zip.getEntry(name);
            if(entry==null||entry.getSize()<1||entry.getSize()>2_000_000L)
                throw new IllegalStateException("Missing/big approved R5 delta.");
            try(InputStreamReader in=new InputStreamReader(zip.getInputStream(entry),
                    StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(in).getAsJsonObject();
            }
        }
    }
    private static int v(JsonArray a,int i){return a.get(i).getAsInt();}
    private static BlockPos pos(JsonArray xyz){
        return new BlockPos(v(xyz,0),v(xyz,1),v(xyz,2));
    }
    private static List<Change> changes(MinecraftServer server, ServerLevel level,
                                        TerritoryDefinition city,String id,String local)
            throws Exception {
        JsonObject g=readIndexed(id,local);
        if(!id.equalsIgnoreCase(g.get("territory_id").getAsString()) ||
                !local.equals(g.get("local_group_id").getAsString()) ||
                !city.dimension().equals(g.get("dimension").getAsString()) ||
                !"PASS_STATIC_ACTIVATED_R2_FIXTURE".equals(g.get("status").getAsString()))
            throw new IllegalStateException("Invalid or nonapproved R5 group metadata.");
        MapAnchor active=city.cityCenter();
        JsonArray center=g.getAsJsonArray("city_center");
        if(active.x()!=v(center,0)||active.y()!=v(center,1)||
                active.z()!=v(center,2)||
                active.rotation()!=g.get("rotation").getAsInt())
            throw new IllegalStateException("City center/rotation differs from source R5.");
        JsonArray list=g.getAsJsonArray("changes");
        if(list.size()<1||list.size()>2500)
            throw new IllegalStateException("Pilot group exceeds 2500-voxel limit.");
        List<Change> out=new ArrayList<>();
        Set<Long> seen=new HashSet<>();
        for(JsonElement element:list) {
            JsonObject row=element.getAsJsonObject();
            BlockPos p=pos(row.getAsJsonArray("world_xyz"));
            if(!seen.add(p.asLong())) throw new IllegalStateException("Duplicate voxel "+p);
            if(!city.restorationRegion().contains(p)||!city.siegeRegion().contains(p))
                throw new IllegalStateException("Outside city safe bounds: "+p);
            if(!level.hasChunkAt(p))
                throw new IllegalStateException("Unloaded chunk at "+p);
            BlockState before=parse(row.get("expected_before_blockstate").getAsString());
            BlockState after=parse(row.get("after_blockstate").getAsString());
            if(before.hasBlockEntity()||after.hasBlockEntity() ||
                    level.getBlockEntity(p)!=null)
                throw new IllegalStateException("Block entity footprint not supported: "+p);
            if(!before.getFluidState().isEmpty()||!after.getFluidState().isEmpty()||
                    !level.getFluidState(p).isEmpty())
                throw new IllegalStateException("Fluid in pilot footprint: "+p);
            out.add(new Change(p,before,after));
        }
        return out;
    }
    private static void verifyCurrent(ServerLevel level,List<Change> cells,
                                      boolean beforeOnly) {
        for(Change c:cells) {
            if(!level.hasChunkAt(c.pos()))
                throw new IllegalStateException("Chunk unloaded at "+c.pos());
            if(level.getBlockEntity(c.pos())!=null)
                throw new IllegalStateException("New block entity found at "+c.pos());
            BlockState state=level.getBlockState(c.pos());
            if(!state.equals(c.before()) && !( !beforeOnly && state.equals(c.after())))
                throw new IllegalStateException("World drift: "+c.pos()+", found "+
                        BuiltInRegistries.BLOCK.getKey(state.getBlock())+
                        " not the required previous state");
        }
    }
    private static void verifyEntities(ServerLevel level,List<Change> cells) {
        // Apply to an unoccupied area. Never displace even non-player entities.
        for(Change c:cells) {
            if(c.after().isAir()||c.before().equals(c.after())) continue;
            AABB cube=new AABB(c.pos());
            List<Entity> entities=level.getEntities((Entity)null,cube,Entity::isAlive);
            if(!entities.isEmpty())
                throw new IllegalStateException("Entity occupies proposed structure: "+c.pos());
        }
    }
    private static JsonObject makeJournal(String city,String group,ServerLevel level,
                                          List<Change> cells) {
        JsonObject doc=new JsonObject();
        doc.addProperty("schema",1);
        doc.addProperty("revision","R5-1");
        doc.addProperty("transaction_id",UUID.randomUUID().toString());
        doc.addProperty("city",city);
        doc.addProperty("group",group);
        doc.addProperty("dimension",level.dimension().location().toString());
        doc.addProperty("state","PENDING");
        JsonArray writes=new JsonArray();
        for(Change c:cells) {
            JsonObject w=new JsonObject();
            JsonArray xyz=new JsonArray();
            xyz.add(c.pos().getX());xyz.add(c.pos().getY());xyz.add(c.pos().getZ());
            w.add("world_xyz",xyz);
            // Before values are from the real world, NOT reconstructed
            // from the Blender old-state assumption.
            w.addProperty("before",canonical(level.getBlockState(c.pos())));
            w.addProperty("after",canonical(c.after()));
            writes.add(w);
        }
        doc.add("writes",writes);
        return doc;
    }
    private static void persist(Path path,JsonObject journal)throws Exception {
        Files.createDirectories(path.getParent());
        Path staging=path.resolveSibling(path.getFileName().toString()+".pending-write");
        try {
            Files.writeString(staging,GSON.toJson(journal),StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
            try(FileChannel channel=FileChannel.open(staging,StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(staging,path,StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(staging);
        }
    }
    public static Result apply(MinecraftServer server,String cityName,String groupName) {
        try {
            String id=norm(cityName),local=group(groupName);
            Path record=journal(id,local);
            if(Files.exists(record))
                return new Result(false,"Journal already exists: "+record+
                        ". Inspect/rollback it first. Never overwrite a historical transaction.");
            TerritoryDefinition city=requireCity(server,id,true);
            ServerLevel level=requireLevel(server,city);
            List<Change> cells=changes(server,level,city,id,local);
            verifyCurrent(level,cells,true);
            verifyEntities(level,cells);
            // Require explicit one-shot TEST-WORLD approval. Consume it before
            // creating any journal or changing anything in the world.
            Path approval=root().resolve(APPROVAL_NAME);
            if(!Files.isRegularFile(approval) ||
                    !Files.readString(approval).trim().equals(APPROVAL_TEXT))
                return new Result(false,"World-writing DISABLED. On a backed-up disposable "+
                        "WORLD COPY only, create "+approval+" containing exactly "+
                        APPROVAL_TEXT+". This file is consumed on the next apply.");
            Files.delete(approval);
            JsonObject snapshot=makeJournal(id,local,level,cells);
            persist(record,snapshot); // Durable pending before first voxel
            int writes=0;
            try {
                for(Change c:cells) {
                    if(c.before().equals(c.after())) continue;
                    if(!level.setBlock(c.pos(),c.after(),2))
                        throw new IllegalStateException("Could not set block "+c.pos());
                    writes++;
                }
                snapshot.addProperty("state","COMMITTED");
                persist(record,snapshot);
                CatanCraft.LOGGER.warn("R5 PILOT COMMIT {}:{} {}, {} writes; journal {}",
                        id,local,snapshot.get("transaction_id").getAsString(),writes,record);
                return new Result(true,"R5 pilot applied "+writes+" exact cells; journal "+
                        record+". Use /catan r5 rollback "+id+" "+local+
                        " confirm to revert (only if after-states remain unchanged).");
            } catch(Exception error) {
                // Keep PENDING journal. A failed save or mutation is never
                // untracked; explicit rollback handles before/after mixtures.
                CatanCraft.LOGGER.error("R5 pilot interrupted; PENDING journal at {}",
                        record,error);
                return new Result(false,"R5 pilot interrupted: "+error.getMessage()+
                        ". PENDING journal saved at "+record+
                        "; use explicit R5 rollback on the test world.");
            }
        } catch(Exception e) {
            return new Result(false,"R5 pilot refused without changes: "+e.getMessage());
        }
    }
    public static Result rollback(MinecraftServer server,String cityName,String groupName) {
        try {
            String id=norm(cityName),local=group(groupName);
            Path record=journal(id,local);
            if(!Files.isRegularFile(record))
                return new Result(false,"No journal for "+id+"/"+local);
            JsonObject doc=JsonParser.parseString(Files.readString(record)).getAsJsonObject();
            if(!id.equals(doc.get("city").getAsString()) ||
                    !local.equals(doc.get("group").getAsString()) ||
                    doc.get("schema").getAsInt()!=1)
                throw new IllegalStateException("Journal identity mismatch");
            String phase=doc.get("state").getAsString();
            if(!phase.equals("COMMITTED")&&!phase.equals("PENDING"))
                return new Result(false,"Cannot rollback transaction state "+phase);
            TerritoryDefinition city=requireCity(server,id,false);
            ServerLevel level=requireLevel(server,city);
            if(!doc.get("dimension").getAsString().equals(city.dimension()))
                throw new IllegalStateException("Journal dimension mismatch");
            List<Change> cells=new ArrayList<>();
            for(JsonElement element:doc.getAsJsonArray("writes")) {
                JsonObject w=element.getAsJsonObject();
                BlockPos p=pos(w.getAsJsonArray("world_xyz"));
                if(!city.restorationRegion().contains(p)||!city.siegeRegion().contains(p)||
                        !level.hasChunkAt(p))
                    throw new IllegalStateException("Unknown/ unloaded rollback cell "+p);
                BlockState before=parse(w.get("before").getAsString());
                BlockState after=parse(w.get("after").getAsString());
                if(before.hasBlockEntity()||after.hasBlockEntity()||
                        level.getBlockEntity(p)!=null)
                    throw new IllegalStateException("Rollback block entity conflict "+p);
                cells.add(new Change(p,before,after));
            }
            // Fully validate EVERY current cell before touching any.
            for(Change c:cells) {
                BlockState current=level.getBlockState(c.pos());
                if(!current.equals(c.before())&&!current.equals(c.after()))
                    throw new IllegalStateException("Rollback drift; unchanged blocks "+
                            "required at "+c.pos());
            }
            verifyEntitiesForRollback(level,cells);
            int restored=0;
            for(Change c:cells) {
                if(level.getBlockState(c.pos()).equals(c.before())) continue;
                if(!level.setBlock(c.pos(),c.before(),2))
                    throw new IllegalStateException("Rollback interrupted at "+c.pos());
                restored++;
            }
            doc.addProperty("state","ROLLED_BACK");
            persist(record,doc);
            CatanCraft.LOGGER.warn("R5 PILOT ROLLBACK {}:{} restored={} journal={}",
                    id,local,restored,record);
            return new Result(true,"R5 pilot rollback completed: "+restored+
                    " cells, journal preserved "+record);
        } catch(Exception e) {
            return new Result(false,"R5 pilot rollback refused/interrupted: "+e.getMessage());
        }
    }
    private static void verifyEntitiesForRollback(ServerLevel level,List<Change> cells) {
        for(Change c:cells) {
            if(c.before().isAir()||level.getBlockState(c.pos()).equals(c.before()))continue;
            if(!level.getEntities((Entity)null,new AABB(c.pos()),Entity::isAlive).isEmpty())
                throw new IllegalStateException("Entity inside rollback footprint "+c.pos());
        }
    }
    private static BlockState parse(String descriptor) throws Exception {
        int start=descriptor.indexOf('[');
        String name=start<0?descriptor:descriptor.substring(0,start);
        ResourceLocation id=ResourceLocation.tryParse(name);
        if(id==null||!BuiltInRegistries.BLOCK.containsKey(id))
            throw new IllegalStateException("Unknown block "+name);
        Block block=BuiltInRegistries.BLOCK.get(id);
        BlockState state=block.defaultBlockState();
        if(start<0) {
            if(!state.getProperties().isEmpty())
                throw new IllegalStateException("Incomplete block properties "+descriptor);
            return state;
        }
        if(!descriptor.endsWith("]"))throw new IllegalStateException("Malformed block state.");
        Map<String,String> props=new HashMap<>();
        for(String pair:descriptor.substring(start+1,descriptor.length()-1).split(",")) {
            String[] kv=pair.split("=",2);
            if(kv.length!=2||props.put(kv[0],kv[1])!=null)
                throw new IllegalStateException("Invalid property "+descriptor);
        }
        if(props.size()!=state.getProperties().size())
            throw new IllegalStateException("Incomplete property set "+descriptor);
        for(Property<?> p:state.getProperties()) {
            String value=props.get(p.getName());
            if(value==null)throw new IllegalStateException("Property missing "+p.getName());
            state=withProp(state,p,value);
        }
        return state;
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static BlockState withProp(BlockState state,Property p,String value) {
        Optional parsed=p.getValue(value);
        if(parsed.isEmpty())throw new IllegalStateException("Invalid block property "+value);
        return state.setValue(p,(Comparable)parsed.get());
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static String property(BlockState s,Property p) {
        return p.getName(s.getValue(p));
    }
    private static String canonical(BlockState state) {
        StringBuilder name=new StringBuilder(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        List<String> vals=new ArrayList<>();
        for(Property<?> prop:state.getProperties())
            vals.add(prop.getName()+"="+property(state,prop));
        if(!vals.isEmpty()) {
            vals.sort(String::compareTo);
            name.append("[").append(String.join(",",vals)).append("]");
        }
        return name.toString();
    }
}
