package com.quin.catancraft.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.InputStreamReader;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * DELIBERATELY NARROW v0.1.17 physical pilot: only R5 Logistics Center in
 * City A, on an explicitly approved, BACKED-UP DISPOSABLE WORLD COPY.
 *
 * One owner/server-thread transaction, fixed SHA-pinned sources, no economy
 * debit, reversible staged sparse block writes, journal flushed BEFORE the
 * first write. All historic journals are retained. A partial failure must
 * be recovered by explicit rollback; it never guesses a new before state.
 *
 * THIS IS NOT general-purpose installation: other cities, logistics, houses,
 * R4/R6 and player-facing purchases are deliberately not supported.
 */
public final class R5LogisticsTestInstaller {
    private static final String CITY="a";
    private static final String CIVIC="LOGISTICS_CENTER";
    private static final String INDEX=R5ReadOnlyPreflight.INDEX_NAME;
    private static final String APPROVAL="CATANCRAFT_R5_CITY_A_LOGISTICS_DISPOSABLE_APPROVAL.txt";
    private static final String APPROVAL_CONTENT=
            "I_CONFIRM_THIS_IS_AN_OFFLINE_BACKED_UP_DISPOSABLE_CITY_A_TEST_COPY_ONLY";
    private static final String JOURNAL="catancraft_r5_city_a_logistics_v1.json";
    private static final int EXPECTED_WRITES=3511;
    private static final int EXPECTED_UNIQUE=3511;
    private static final int EXPECTED_BARRELS=24;
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();

    public record Result(boolean success,String text) {}
    private record Change(BlockPos pos, BlockState before,BlockState after) {}
    private record Stage(String id,List<Change> writes) {}
    private record Union(BlockPos pos,BlockState before,BlockState after) {}
    private record Prepared(List<Stage> stages,Map<Long,Union> union) {}
    private R5LogisticsTestInstaller() {}

    private static Path save(MinecraftServer server)throws Exception {
        return server.getWorldPath(LevelResource.ROOT).toRealPath();
    }
    private static Path record(MinecraftServer server)throws Exception {
        return save(server).resolve("data").resolve(JOURNAL);
    }
    private static Path approval(MinecraftServer server)throws Exception {
        return save(server).resolve(APPROVAL);
    }
    private static ServerLevel level(MinecraftServer server,TerritoryDefinition city) {
        for(ServerLevel level:server.getAllLevels())
            if(level.dimension().location().toString().equals(city.dimension()))return level;
        throw new IllegalStateException("Civic dimension is unavailable");
    }
    private static TerritoryDefinition city(MinecraftServer server, boolean owned) {
        TerritoryDefinition city=MapDefinitionManager.territory(CITY);
        if(city==null||city.cityCenter()==null||city.siegeRegion()==null||
                city.restorationRegion()==null||
                city.cityCenter().x()!=-750||city.cityCenter().y()!=81||
                city.cityCenter().z()!=-750||city.cityCenter().rotation()!=0||
                !"minecraft:overworld".equals(city.dimension()))
            throw new IllegalStateException("Unexpected City A world map/rotation.");
        if(owned) {
            TerritoryData territory=CatanSavedData.get(server).territory(CITY);
            if(territory==null||territory.ownerNationId()==null||
                    CatanSavedData.get(server).nation(territory.ownerNationId())==null)
                throw new IllegalStateException("City A must be owned by an active nation.");
            long ownedCivics=territory.buildings().stream()
                    .filter(b->CIVIC.equals(b.type().name())).count();
            if(ownedCivics!=1)
                throw new IllegalStateException("Exactly one existing Logistics purchase "+
                        "required; found "+ownedCivics+". No double charge or save repair.");
        }
        return city;
    }
    private static BlockPos pos(JsonArray array) {
        if(array==null||array.size()!=3)
            throw new IllegalStateException("Missing / malformed world XYZ.");
        return new BlockPos(array.get(0).getAsInt(),array.get(1).getAsInt(),
                array.get(2).getAsInt());
    }
    private static JsonArray xyz(BlockPos p) {
        JsonArray a=new JsonArray();
        a.add(p.getX());a.add(p.getY());a.add(p.getZ());
        return a;
    }
    private static JsonObject json(ZipFile zip,String filename)throws Exception {
        ZipEntry entry=zip.getEntry(filename);
        if(entry==null||entry.getSize()<1||entry.getSize()>6_000_000)
            throw new IllegalStateException("Missing/oversized pinned R5 contract "+filename);
        try(InputStreamReader reader=new InputStreamReader(
                zip.getInputStream(entry),StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
    private static boolean supported(BlockPos p,TerritoryDefinition city) {
        if(!city.siegeRegion().contains(p)||!city.restorationRegion().contains(p))
            return false;
        // Explicit fixed-prototype civic write area. Never allow the rest of City A.
        if(p.getX() < -827 || p.getX()>-794 ||
                p.getY()<80||p.getY()>95||p.getZ() < -708||p.getZ()>-675)
            return false;
        // The JSON 'buildingPlots' markers are NOT schematic paste anchors.
        // Use actual registered V4/R3 source transforms, or innocent public
        // sidewalk cells are incorrectly flagged as overlapping a factory.
        // Includes 1-block margin and ALL six fixed city envelopes. Alternative
        // industry types share their corresponding licensed plot geometry.
        for(String id:List.of("vehicle_factory","quarry","weapons_factory",
                "steel_mill","farm","lumberyard","th1","th2","th3")) {
            var asset=SchematicAssetRegistry.asset(id);
            var anchor=SchematicAssetRegistry.territoryAnchor(id,city);
            if(asset==null||anchor==null)return false;
            var bounds=asset.boundsAt(anchor);
            if(p.getX()>=bounds.min().getX()-1 &&
                    p.getX()<=bounds.max().getX()+1 &&
                    p.getZ()>=bounds.min().getZ()-1 &&
                    p.getZ()<=bounds.max().getZ()+1)
                return false;
        }
        return true;
    }
    private static Prepared prepare(MinecraftServer server,TerritoryDefinition city,
                                    ServerLevel level)throws Exception {
        R5ReadOnlyPreflight.verifyArchive();
        R5ReadOnlyPreflight.verifyIndex();
        Path compact=FMLPaths.CONFIGDIR.get().resolve("catancraft").resolve(INDEX);
        if(!Files.isRegularFile(compact))
            throw new IllegalStateException("Missing pinned R5 compact contract.");
        List<Stage> stages=new ArrayList<>();
        Map<Long,Union> original=new LinkedHashMap<>();
        Map<Long,BlockState> virtual=new HashMap<>();
        try(ZipFile index=new ZipFile(compact.toFile());
            ZipFile art=new ZipFile(R5ReadOnlyPreflight.archivePath().toFile())) {
            for(String step:List.of("logistics_service_connection",CIVIC)) {
                boolean civic=CIVIC.equals(step);
                String source=civic?"contracts/civic-deltas/A_logistics_center.json":
                        "contracts/groups/A/"+step+".json";
                JsonObject group=json(civic?art:index,source);
                if(!CITY.equalsIgnoreCase(group.get("territory_id").getAsString())||
                        !(civic? CIVIC.equals(group.get("module_id").getAsString()) &&
                                "PASS_STATIC".equals(group.get("status").getAsString()):
                                step.equals(group.get("local_group_id").getAsString()) &&
                                "PASS_STATIC_ACTIVATED_R2_FIXTURE".equals(
                                        group.get("status").getAsString())))
                    throw new IllegalStateException("Unexpected pinned R5 stage "+step);
                JsonArray writes=group.getAsJsonArray(civic?"writes":"changes");
                int required=civic?3353:158;
                if(writes==null||writes.size()!=required)
                    throw new IllegalStateException("Unexpected stage change count "+step);
                List<Change> changes=new ArrayList<>();
                Set<Long> seen=new HashSet<>();
                for(JsonElement e:writes) {
                    JsonObject row=e.getAsJsonObject();
                    BlockPos p=pos(row.getAsJsonArray("world_xyz"));
                    if(!seen.add(p.asLong())||!supported(p,city)||!level.hasChunkAt(p))
                        throw new IllegalStateException("Duplicated/protected/unloaded stage "+
                                step+" at "+p);
                    BlockState before=parse(row.get("expected_before_blockstate").getAsString());
                    BlockState after=parse(row.get("after_blockstate").getAsString());
                    if(before.hasBlockEntity() ||
                            (after.hasBlockEntity() && after.getBlock()!=Blocks.BARREL) ||
                            !before.getFluidState().isEmpty() || !after.getFluidState().isEmpty())
                        throw new IllegalStateException("Unexpected tile/fluid stage at "+p);
                    BlockState now=virtual.get(p.asLong());
                    if(now==null) {
                        now=level.getBlockState(p);
                        if(level.getBlockEntity(p)!=null||!level.getFluidState(p).isEmpty())
                            throw new IllegalStateException("Live tile/fluid at "+p);
                    }
                    if(!now.equals(before))
                        throw new IllegalStateException("Preflight world/stage drift at "+p+
                                " in "+step+": required "+canonical(before)+
                                ", found "+canonical(now));
                    Union old=original.get(p.asLong());
                    original.put(p.asLong(),new Union(p,old==null?now:old.before(),after));
                    virtual.put(p.asLong(),after);
                    changes.add(new Change(p,before,after));
                }
                stages.add(new Stage(step,List.copyOf(changes)));
            }
        }
        int operations=stages.stream().mapToInt(s->s.writes().size()).sum();
        long barrels=original.values().stream()
                .filter(v->v.after().getBlock()==Blocks.BARREL).count();
        if(operations!=EXPECTED_WRITES||original.size()!=EXPECTED_UNIQUE||
                barrels!=EXPECTED_BARRELS)
            throw new IllegalStateException("Pinned civic mask/source counts diverged.");
        return new Prepared(List.copyOf(stages),original);
    }
    private static String initialTag(BlockEntity tile) {
        return tile.saveWithFullMetadata().toString();
    }
    private static void clearBarrel(ServerLevel level,Union voxel,String expectedNbt,
                                    boolean exactNbt) {
        BlockEntity tile=level.getBlockEntity(voxel.pos());
        if(tile==null || !(tile instanceof Container container) ||
                tile.getBlockState().getBlock()!=Blocks.BARREL)
            throw new IllegalStateException("Expected own barrel block entity at "+voxel.pos());
        if(!container.isEmpty())
            throw new IllegalStateException("REFUSE rollback: barrel has inventory at "+
                    voxel.pos()+". Remove items safely, then retry.");
        if(exactNbt && expectedNbt!=null &&
                !expectedNbt.equals(initialTag(tile)))
            throw new IllegalStateException("Barrel metadata changed at "+voxel.pos()+
                    "; manual inspection required before rollback.");
    }
    private static boolean sameOrKnown(BlockState actual,BlockState expected) {
        if(actual.equals(expected))return true;
        // Vanilla leaves 'distance' can change as neighboring logs change,
        // but the persistent/waterlogged and all other states must remain.
        if(expected.getBlock()!=Blocks.OAK_LEAVES ||
                actual.getBlock()!=Blocks.OAK_LEAVES) return false;
        for(Property<?> p:expected.getProperties()) {
            if("distance".equals(p.getName()))continue;
            if(!property(actual,p).equals(property(expected,p)))return false;
        }
        return true;
    }
    /**
     * Narrow runtime rollback allowance: players can open the installed
     * spruce shop doors without losing the right to revert this civic.
     * Facing, hinge, half, powered, block ID and every other property must
     * remain identical. This is NOT a generic fuzzy block-state comparison.
     */
    private static boolean matchesRollbackInstalled(BlockState actual,BlockState desired) {
        if(sameOrKnown(actual,desired)) return true;
        if(actual.getBlock()!=Blocks.SPRUCE_DOOR ||
                desired.getBlock()!=Blocks.SPRUCE_DOOR) return false;
        for(Property<?> property:desired.getProperties()) {
            if("open".equals(property.getName()))continue;
            if(!property(actual,property).equals(property(desired,property)))
                return false;
        }
        return true;
    }
    private static String diag(BlockState state) {
        return canonical(state);
    }
    private static <T extends Comparable<T>> String property(BlockState state,Property<T> p) {
        return p.getName()+"="+p.getName(state.getValue(p));
    }
    private static String canonical(BlockState state) {
        StringBuilder s=new StringBuilder(BuiltInRegistries.BLOCK
                .getKey(state.getBlock()).toString());
        List<String> props=new ArrayList<>();
        for(Property<?> p:state.getProperties())props.add(property(state,p));
        props.sort(String::compareTo);
        if(!props.isEmpty())s.append("[").append(String.join(",",props)).append("]");
        return s.toString();
    }
    private static BlockState parse(String raw) {
        int offset=raw.indexOf('[');
        String id=offset<0?raw:raw.substring(0,offset);
        ResourceLocation resource=ResourceLocation.tryParse(id);
        if(resource==null||!BuiltInRegistries.BLOCK.containsKey(resource))
            throw new IllegalStateException("Unknown blockstate "+id);
        Block block=BuiltInRegistries.BLOCK.get(resource);
        BlockState state=block.defaultBlockState();
        if(offset<0) {
            if(!state.getProperties().isEmpty())
                throw new IllegalStateException("Incomplete state "+raw);
            return state;
        }
        if(!raw.endsWith("]"))throw new IllegalStateException("Malformed state "+raw);
        Map<String,String> values=new HashMap<>();
        for(String part:raw.substring(offset+1,raw.length()-1).split(",")) {
            String[] kv=part.split("=",2);
            if(kv.length!=2||values.put(kv[0],kv[1])!=null)
                throw new IllegalStateException("Malformed state property "+raw);
        }
        if(values.size()!=state.getProperties().size())
            throw new IllegalStateException("Incomplete state property set "+raw);
        for(Property<?> p:state.getProperties()) {
            String value=values.get(p.getName());
            if(value==null)throw new IllegalStateException("Missing property "+p.getName());
            state=withProperty(state,p,value);
        }
        return state;
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static BlockState withProperty(BlockState s,Property p,String value) {
        Optional<?> property=p.getValue(value);
        if(property.isEmpty())throw new IllegalStateException("Unknown property "+value);
        return s.setValue(p,(Comparable)property.get());
    }
    private static JsonObject makeJournal(MinecraftServer server,
                                          Prepared plan,ServerLevel level)throws Exception {
        JsonObject journal=new JsonObject();
        journal.addProperty("schema",1);
        journal.addProperty("phase","PENDING");
        journal.addProperty("transaction_id",UUID.randomUUID().toString());
        journal.addProperty("world_root",save(server).toString());
        journal.addProperty("dimension",level.dimension().location().toString());
        journal.addProperty("territory",CITY);
        journal.addProperty("module",CIVIC);
        journal.addProperty("source_revision","R5-1 PINNED");
        journal.addProperty("purchase_policy","EXISTING_ONE_NO_CHARGE");
        JsonArray stages=new JsonArray();
        for(Stage s:plan.stages()) {
            JsonObject stage=new JsonObject();
            stage.addProperty("id",s.id());
            JsonArray rows=new JsonArray();
            for(Change cell:s.writes()) {
                JsonObject row=new JsonObject();
                row.add("xyz",xyz(cell.pos()));
                row.addProperty("before",canonical(cell.before()));
                row.addProperty("after",canonical(cell.after()));
                rows.add(row);
            }
            stage.add("writes",rows);
            stages.add(stage);
        }
        journal.add("stages",stages);
        JsonArray originals=new JsonArray();
        for(Union voxel:plan.union().values()) {
            JsonObject cell=new JsonObject();
            cell.add("xyz",xyz(voxel.pos()));
            cell.addProperty("before",canonical(level.getBlockState(voxel.pos())));
            cell.addProperty("after",canonical(voxel.after()));
            // No source block-entities were permitted; future versions must
            // serialize before NBT rather than skip it.
            cell.addProperty("before_nbt","NONE");
            originals.add(cell);
        }
        journal.add("originals",originals);
        journal.add("installed_barrel_nbt",new JsonObject());
        seal(journal);
        return journal;
    }
    private static void seal(JsonObject j)throws Exception {
        j.remove("sha256");
        byte[] hashed=MessageDigest.getInstance("SHA-256")
                .digest(GSON.toJson(j).getBytes(StandardCharsets.UTF_8));
        StringBuilder hex=new StringBuilder();
        for(byte b:hashed)hex.append(String.format(Locale.ROOT,"%02x",b&0xff));
        j.addProperty("sha256",hex.toString());
    }
    private static JsonObject readJournal(Path file)throws Exception {
        if(!Files.isRegularFile(file)||Files.size(file)>2_000_000)
            throw new IllegalStateException("Missing or oversized civic journal.");
        JsonObject j=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        if(j.get("schema").getAsInt()!=1||!CITY.equals(j.get("territory").getAsString())||
                !CIVIC.equals(j.get("module").getAsString()))
            throw new IllegalStateException("Unrecognized civic journal identity.");
        String digest=j.get("sha256").getAsString();
        seal(j);
        if(!digest.equals(j.get("sha256").getAsString()))
            throw new IllegalStateException("Journal SHA-256 integrity mismatch.");
        return j;
    }
    private static void persist(Path path,JsonObject j)throws Exception {
        seal(j);
        Files.createDirectories(path.getParent());
        Path temporary=path.resolveSibling(path.getFileName()+".pending-write");
        try {
            Files.writeString(temporary,GSON.toJson(j),StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.CREATE);
            try(FileChannel channel=FileChannel.open(temporary,StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            // The atomic replace is journaled within the save, not server-wide
            // config. Flush the file and attempt to force the parent directory.
            try(FileChannel channel=FileChannel.open(path,StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try(FileChannel directory=FileChannel.open(path.getParent(),StandardOpenOption.READ)) {
                directory.force(true);
            } catch(java.io.IOException|UnsupportedOperationException portability) {
                CatanCraft.LOGGER.warn("Directory fsync unsupported: {}",portability.toString());
            }
        } finally {Files.deleteIfExists(temporary);}
    }
    private static void validateEntityFree(ServerLevel level,Prepared plan) {
        // Check every *solid new write* against complete entity bounding boxes,
        // not only living entities or centers; even dropped items are preserved.
        for(Union voxel:plan.union().values()) {
            if(voxel.after().isAir()||sameOrKnown(voxel.after(),voxel.before()))continue;
            if(!level.getEntities((Entity)null,new AABB(voxel.pos()),Entity::isAlive).isEmpty())
                throw new IllegalStateException("Entity occupies civic write at "+voxel.pos());
        }
    }
    private static void validateBarrels(ServerLevel level,Prepared plan,JsonObject targetNbt,
                                        boolean exact) {
        for(Union voxel:plan.union().values())
            if(voxel.after().getBlock()==Blocks.BARREL) {
                String tag=targetNbt.has(voxel.pos().asLong()+"")?
                        targetNbt.get(voxel.pos().asLong()+"").getAsString():null;
                clearBarrel(level,voxel,tag,exact);
            }
    }
    public static synchronized Result apply(MinecraftServer server) {
        boolean writesStarted=false;
        Path file=null;
        try {
            file=record(server);
            if(Files.exists(file))
                return new Result(false,"Civic journal already exists (including rollback history): "+
                        file+". Never overwrite an existing transaction.");
            // Avoid simultaneous writes from the existing park pilot inside the
            // same city; existing park rollback journal itself is harmless.
            TerritoryDefinition def=city(server,true);
            ServerLevel level=level(server,def);
            R5CivicConstructionAudit.Report audit=
                    R5CivicConstructionAudit.inspect(server,CITY,"logistics");
            if(!audit.staticReady())
                return new Result(false,"R5 civic AUDIT BLOCKED; run /catan r5 "+
                        "constructioncheck a logistics and review details.");
            Prepared plan=prepare(server,def,level);
            validateEntityFree(level,plan);
            Path consent=approval(server);
            if(!Files.isRegularFile(consent)||
                    !APPROVAL_CONTENT.equals(Files.readString(consent).trim()))
                return new Result(false,"Physical pilot DENIED. On a BACKED-UP DISPOSABLE "+
                        "City A test-world copy only, create "+consent+
                        " with the exact one-shot approval phrase: "+APPROVAL_CONTENT);
            // Approval consumed before journal/write. Never create a new
            // approval automatically or on behalf of a real server.
            Files.delete(consent);
            JsonObject j=makeJournal(server,plan,level);
            persist(file,j);
            // Confirm the durable journal can actually be re-read intact BEFORE
            // the first Minecraft voxel write.
            readJournal(file);
            writesStarted=true;
            int done=0;
            try {
                for(Stage stage:plan.stages()) {
                    for(Change cell:stage.writes()) {
                        if(!level.hasChunkAt(cell.pos())||
                                !sameOrKnown(level.getBlockState(cell.pos()),cell.before()))
                            throw new IllegalStateException("Write-time drift/unloaded "+
                                    stage.id()+" "+cell.pos());
                        if(level.getBlockEntity(cell.pos())!=null||
                                !level.getFluidState(cell.pos()).isEmpty())
                            throw new IllegalStateException("Write-time tile/fluid at "+
                                    cell.pos());
                        if(!cell.after().isAir()&&!sameOrKnown(cell.before(),cell.after()) &&
                                !level.getEntities((Entity)null,
                                        new AABB(cell.pos()),Entity::isAlive).isEmpty())
                            throw new IllegalStateException("Entity moved into "+cell.pos());
                        if(!sameOrKnown(cell.before(),cell.after()) &&
                                !level.setBlock(cell.pos(),cell.after(),2))
                            throw new IllegalStateException("Minecraft refused voxel "+cell.pos());
                        done++;
                        if(!sameOrKnown(level.getBlockState(cell.pos()),cell.after()))
                            throw new IllegalStateException("Immediate block drift at "+cell.pos());
                    }
                }
                // Verify final union, no silent physics-induced drift.
                for(Union voxel:plan.union().values())
                    if(!sameOrKnown(level.getBlockState(voxel.pos()),voxel.after()))
                        throw new IllegalStateException("Final state drift at "+voxel.pos());
                JsonObject newBarrels=j.getAsJsonObject("installed_barrel_nbt");
                for(Union voxel:plan.union().values())
                    if(voxel.after().getBlock()==Blocks.BARREL) {
                        clearBarrel(level,voxel,null,false);
                        newBarrels.addProperty(Long.toString(voxel.pos().asLong()),
                                initialTag(level.getBlockEntity(voxel.pos())));
                    }
                if(newBarrels.size()!=EXPECTED_BARRELS)
                    throw new IllegalStateException("Barrel block entity count unexpected.");
                j.addProperty("phase","COMMITTED");
                persist(file,j);
                CatanCraft.LOGGER.warn("R5 CITY A LOGISTICS PILOT COMMITTED {} writes; journal {}",
                        done,file);
                return new Result(true,"R5 City A LOGISTICS physically installed "+
                        done+" ordered sparse writes. NO PURCHASE CHARGE. Journal "+file+
                        ". Walk through and inspect BEFORE testing rollback.");
            } catch(Exception e) {
                CatanCraft.LOGGER.error("R5 City A Logistics interrupted; PENDING journal. "+
                        "Do not manually clear terrain.",e);
                return new Result(false,"R5 Logistics interrupted: "+e.getMessage()+
                        ". PENDING JOURNAL: "+file+
                        ". Do not manually modify; inspect and use "+
                        "/catan r5 logisticspilot rollback confirm on this SAME test world.");
            }
        } catch(Exception e) {
            return new Result(false,(writesStarted?"PENDING PILOT ":"PILOT DENIED: ")+
                    e.getMessage()+(file==null?"":" | journal "+file));
        }
    }
    private static Map<Long,Union> loadOriginals(JsonObject j,TerritoryDefinition city,
                                                  ServerLevel level) {
        JsonArray rows=j.getAsJsonArray("originals");
        if(rows==null||rows.size()!=EXPECTED_UNIQUE)
            throw new IllegalStateException("Unexpected journal originals count.");
        Map<Long,Union> out=new LinkedHashMap<>();
        for(JsonElement e:rows) {
            JsonObject item=e.getAsJsonObject();
            BlockPos p=pos(item.getAsJsonArray("xyz"));
            if(!supported(p,city)||!level.hasChunkAt(p)||out.containsKey(p.asLong()))
                throw new IllegalStateException("Invalid/unloaded rollback coordinate "+p);
            if(!"NONE".equals(item.get("before_nbt").getAsString()))
                throw new IllegalStateException("Unsupported before-NBT journal at "+p);
            BlockState before=parse(item.get("before").getAsString());
            BlockState after=parse(item.get("after").getAsString());
            if(before.hasBlockEntity()||
                    (after.hasBlockEntity()&&after.getBlock()!=Blocks.BARREL))
                throw new IllegalStateException("Unsupported journal block entity "+p);
            out.put(p.asLong(),new Union(p,before,after));
        }
        return out;
    }
    private static Map<Long,List<BlockState>> allowedStages(JsonObject j,
                                                              Map<Long,Union> originals) {
        JsonArray stages=j.getAsJsonArray("stages");
        if(stages==null||stages.size()!=2)
            throw new IllegalStateException("Unexpected stage count in journal.");
        Map<Long,List<BlockState>> allowed=new HashMap<>();
        for(Union v:originals.values()) {
            List<BlockState> states=new ArrayList<>();
            states.add(v.before());
            allowed.put(v.pos().asLong(),states);
        }
        int total=0;
        for(JsonElement el:stages) {
            JsonObject stage=el.getAsJsonObject();
            JsonArray rows=stage.getAsJsonArray("writes");
            if(rows==null||rows.size()>4000)
                throw new IllegalStateException("Malformed rollback stage.");
            total+=rows.size();
            for(JsonElement e:rows) {
                JsonObject row=e.getAsJsonObject();
                BlockPos p=pos(row.getAsJsonArray("xyz"));
                List<BlockState> states=allowed.get(p.asLong());
                if(states==null)
                    throw new IllegalStateException("Journal stage escapes snapshot "+p);
                BlockState previous=states.get(states.size()-1);
                BlockState claimed=parse(row.get("before").getAsString());
                BlockState after=parse(row.get("after").getAsString());
                if(!sameOrKnown(previous,claimed))
                    throw new IllegalStateException("Journal stage transition invalid at "+p);
                states.add(after);
            }
        }
        if(total!=EXPECTED_WRITES)
            throw new IllegalStateException("Unexpected ordered journal write count.");
        for(Union v:originals.values()) {
            List<BlockState> states=allowed.get(v.pos().asLong());
            if(!sameOrKnown(v.after(),states.get(states.size()-1)))
                throw new IllegalStateException("Journal final-mask mismatch "+v.pos());
        }
        return allowed;
    }
    /**
     * Read-only classification of every journal cell before the user attempts
     * to recover the installed building. Reports up to six unsafe cells.
     * Does not write a file, change a block or mark any journal phase.
     */
    public static synchronized Result diagnose(MinecraftServer server) {
        try {
            Path file=record(server);
            JsonObject journal=readJournal(file);
            if(!save(server).toString().equals(journal.get("world_root").getAsString())||
                    !"minecraft:overworld".equals(journal.get("dimension").getAsString()))
                throw new IllegalStateException("Journal belongs to another world/dimension.");
            TerritoryDefinition definition=city(server,false);
            ServerLevel world=level(server,definition);
            Map<Long,Union> originals=loadOriginals(journal,definition,world);
            Map<Long,List<BlockState>> allowed=allowedStages(journal,originals);
            boolean committed="COMMITTED".equals(journal.get("phase").getAsString());
            int before=0,expected=0,opened=0,unsafe=0,tiles=0;
            List<String> details=new ArrayList<>();
            for(Union cell:originals.values()) {
                BlockState live=world.getBlockState(cell.pos());
                if(sameOrKnown(live,cell.before()))before++;
                else if(sameOrKnown(live,cell.after()))expected++;
                else if(matchesRollbackInstalled(live,cell.after())) {
                    opened++;
                    if(details.size()<6)
                        details.add("NORMAL_OPEN_SPRUCE_DOOR "+cell.pos()+
                                " installed="+canonical(cell.after())+
                                " current="+canonical(live));
                } else {
                    boolean intermediate=false;
                    for(BlockState step:allowed.get(cell.pos().asLong()))
                        if(matchesRollbackInstalled(live,step)) {
                            intermediate=true;
                            break;
                        }
                    if(!committed && intermediate)expected++;
                    else {
                        unsafe++;
                        if(details.size()<6)
                            details.add("UNSAFE_THIRD_STATE "+cell.pos()+
                                    " installed="+canonical(cell.after())+
                                    " original="+canonical(cell.before())+
                                    " current="+canonical(live));
                    }
                }
                if(world.getBlockEntity(cell.pos())!=null)tiles++;
            }
            String outcome="R5 City A LOGISTICS JOURNAL DIAGNOSIS phase="+
                    journal.get("phase").getAsString()+
                    "; original="+before+"; installed/intermediate="+expected+
                    "; normal-open-doors="+opened+"; unsafe-third-states="+unsafe+
                    "; current-block-entities="+tiles+
                    ". READ-ONLY; NO ROLLBACK EXECUTED.";
            CatanCraft.LOGGER.info("{} details={}",outcome,String.join(" | ",details));
            return new Result(unsafe==0,outcome+
                    (details.isEmpty()?"":" | "+String.join(" | ",details)));
        } catch(Exception problem) {
            return new Result(false,"R5 Logistics journal diagnosis REFUSED: "+
                    problem.getMessage()+". No world changes made.");
        }
    }

    public static synchronized Result rollback(MinecraftServer server) {
        try {
            Path file=record(server);
            JsonObject j=readJournal(file);
            if(!save(server).toString().equals(j.get("world_root").getAsString())||
                    !"minecraft:overworld".equals(j.get("dimension").getAsString())||
                    !"EXISTING_ONE_NO_CHARGE".equals(j.get("purchase_policy").getAsString()))
                throw new IllegalStateException("Journal wrong world/dimension/economy identity.");
            String phase=j.get("phase").getAsString();
            if(!Set.of("PENDING","COMMITTED","ROLLING_BACK").contains(phase))
                throw new IllegalStateException("Cannot rollback phase "+phase);
            TerritoryDefinition city=city(server,false);
            ServerLevel level=level(server,city);
            Map<Long,Union> originals=loadOriginals(j,city,level);
            Map<Long,List<BlockState>> expected=allowedStages(j,originals);
            JsonObject barrelNbt=j.getAsJsonObject("installed_barrel_nbt");
            if(barrelNbt==null)
                throw new IllegalStateException("Barrel NBT journal is missing.");
            boolean committed=phase.equals("COMMITTED");
            // STRICT all-cell preflight before restoring a single block. On
            // PENDING interrupted installs, any known stage-state is allowed.
            for(Union voxel:originals.values()) {
                BlockState actual=level.getBlockState(voxel.pos());
                List<BlockState> allowable=expected.get(voxel.pos().asLong());
                boolean matches=false;
                for(BlockState known:allowable)
                    if(matchesRollbackInstalled(actual,known)) {matches=true;break;}
                if(!matches || (committed&&!matchesRollbackInstalled(actual,voxel.after())))
                    throw new IllegalStateException("ROLLBACK REFUSED: third-state drift at "+
                            voxel.pos()+"; expected-installed="+diag(voxel.after())+
                            "; original="+diag(voxel.before())+
                            "; current="+diag(actual)+
                            ". Keep journal and investigate.");
                BlockEntity tile=level.getBlockEntity(voxel.pos());
                if(actual.getBlock()==Blocks.BARREL) {
                    String key=Long.toString(voxel.pos().asLong());
                    if(voxel.after().getBlock()!=Blocks.BARREL)
                        throw new IllegalStateException("Unexpected barrel at "+voxel.pos());
                    // A COMMITTED install must remain pristine in full metadata,
                    // including inventory and custom name. Pending may not have
                    // recorded the new barrel yet; still refuse nonempty items.
                    clearBarrel(level,voxel,barrelNbt.has(key)?
                            barrelNbt.get(key).getAsString():null,
                            committed&&barrelNbt.has(key));
                } else if(tile!=null)
                    throw new IllegalStateException("Unexpected block entity at "+voxel.pos());
                if(!sameOrKnown(actual,voxel.before())&&!voxel.before().isAir()&&
                        !level.getEntities((Entity)null,new AABB(voxel.pos()),Entity::isAlive)
                                .isEmpty())
                    throw new IllegalStateException("Entity blocks restoring "+voxel.pos());
            }
            j.addProperty("phase","ROLLING_BACK");
            persist(file,j);
            // Restore every actual BEFORE snapshot. Reverse order is based on
            // recorded ordered stages; unique positions are restored once.
            List<Union> reverse=new ArrayList<>(originals.values());
            java.util.Collections.reverse(reverse);
            int restored=0;
            try {
                for(Union voxel:reverse) {
                    BlockState actual=level.getBlockState(voxel.pos());
                    if(sameOrKnown(actual,voxel.before()))continue;
                    // Recheck first due to possible prior Minecraft block updates.
                    boolean known=false;
                    for(BlockState expectedState:expected.get(voxel.pos().asLong()))
                        if(matchesRollbackInstalled(actual,expectedState)){known=true;break;}
                    if(!known)
                        throw new IllegalStateException("New drift during rollback at "+voxel.pos()+
                                "; expected="+diag(voxel.after())+
                                "; current="+diag(actual));
                    if(actual.getBlock()==Blocks.BARREL)
                        clearBarrel(level,voxel,
                                barrelNbt.has(Long.toString(voxel.pos().asLong()))?
                                barrelNbt.get(Long.toString(voxel.pos().asLong())).getAsString():null,
                                committed&&barrelNbt.has(Long.toString(voxel.pos().asLong())));
                    if(!level.setBlock(voxel.pos(),voxel.before(),2))
                        throw new IllegalStateException("Minecraft refused rollback "+voxel.pos());
                    if(!sameOrKnown(level.getBlockState(voxel.pos()),voxel.before()))
                        throw new IllegalStateException("After-rollback drift "+voxel.pos());
                    restored++;
                }
                for(Union voxel:originals.values())
                    if(!sameOrKnown(level.getBlockState(voxel.pos()),voxel.before()))
                        throw new IllegalStateException("Final restored mask drift at "+voxel.pos());
                j.addProperty("phase","ROLLED_BACK");
                persist(file,j);
                return new Result(true,"R5 City A Logistics rollback COMPLETE: restored "+
                        restored+" unique positions; journal retained "+file+
                        ". No purchase or economy state changed.");
            } catch(Exception e) {
                CatanCraft.LOGGER.error("R5 City A rollback interrupted; ROLLING_BACK journal",e);
                return new Result(false,"ROLLING_BACK journal retained: "+e.getMessage()+
                        ". Do not delete; retry after diagnosing on same test world.");
            }
        } catch(Exception ex) {
            return new Result(false,"R5 City A Logistics rollback REFUSED: "+
                    ex.getMessage()+". World unchanged if refused before ROLLING_BACK.");
        }
    }
}
