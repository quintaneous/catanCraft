package com.quin.catancraft.world;

import com.google.gson.*;
import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.*;
import com.quin.catancraft.economy.*;
import com.quin.catancraft.map.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The purchase-time R5 civic builder for the separately delivered R7 Season One
 * development world. No operator commands, no approval per building, no blind
 * schematic paste and no legacy-world migration. All 16 city designs have been
 * independently checked against the delivered R7 Anvil blocks. The world has to
 * opt in ONCE with a matching owner-created, disposable-only baseline marker.
 *
 * The installation journal precedes world writes, and the charge and building
 * record are saved together in CatanSavedData. If the process crashes before
 * completing the transaction, its immutable journal remains and repurchase is
 * REFUSED until a separate, audited recovery procedure is implemented.
 */
public final class R5CivicAutomaticInstaller {
    private static final String ROOT_NAME="CatanCraft_Season1_R7_Development";
    private static final String WORLD_LEVEL_NAME="CatanCraft Environment V4 - Visual Polish R2";
    public static final String MARKER_NAME="CATANCRAFT_R7_AUTOBUILD_DISPOSABLE_TEST_ONLY.txt";
    public static final String MARKER_VALUE="I_ACCEPT_AUTOMATIC_R5_CIVIC_CONSTRUCTION_ON_THIS_BACKED_UP_DISPOSABLE_R7_WORLD_ONLY";
    private static final int WRITE_FLAGS=Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final String STAGED_INDEX=R5ReadOnlyPreflight.INDEX_NAME;
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_CELLS=10_000;
    private static final int MAX_WRITES=12_000;
    private static final int MAX_JOURNAL_BYTES=2_800_000;

    public record Result(boolean success,String message) {}
    private record Write(BlockPos pos,BlockState before,BlockState after,String stage) {}
    private record Cell(BlockPos pos,BlockState before,BlockState after) {}
    private record Plan(List<Write> writes,LinkedHashMap<Long,Cell> finalCells,int barrels) {}

    private R5CivicAutomaticInstaller() {}

    private static String kind(BuildingType type) {
        return switch(type) {
            case COMMERCIAL_DISTRICT -> "commercial_district";
            case LOGISTICS_CENTER -> "logistics_center";
            default -> throw new IllegalArgumentException("Not an R5 civic: "+type);
        };
    }
    public static boolean isCivic(BuildingType type) {
        return type==BuildingType.COMMERCIAL_DISTRICT || type==BuildingType.LOGISTICS_CENTER;
    }
    private static Path root(MinecraftServer server) throws IOException {
        return server.getWorldPath(LevelResource.ROOT).toRealPath();
    }
    private static Path journal(Path root,String city,String kind) {
        return root.resolve("data").resolve("catancraft_r5_auto_"+city+"_"+kind+"_v1.json");
    }
    private static void ensureTargetWorld(MinecraftServer server,Path root) throws IOException {
        if(!ROOT_NAME.equals(root.getFileName().toString()) ||
                !WORLD_LEVEL_NAME.equals(server.getWorldData().getLevelName()))
            throw new IllegalStateException("Auto-build disabled outside the exact new R7 development-world folder and LevelName. No purchase was charged.");
        Path marker=root.resolve(MARKER_NAME);
        if(!Files.isRegularFile(marker)||Files.size(marker)>250 ||
                !MARKER_VALUE.equals(Files.readString(marker).trim()))
            throw new IllegalStateException("R7 automatic construction is not enabled for this disposable world. Missing one-time world-root marker: "+MARKER_NAME+". No purchase was charged.");
    }
    private static String canonical(BlockState state) {
        StringBuilder result=new StringBuilder(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        List<String> props=new ArrayList<>();
        for(Property<?> p:state.getProperties()) props.add(canonicalProperty(state,p));
        props.sort(String::compareTo);
        if(!props.isEmpty())result.append('[').append(String.join(",",props)).append(']');
        return result.toString();
    }
    private static <T extends Comparable<T>> String canonicalProperty(BlockState state,Property<T> property) {
        return property.getName()+"="+property.getName(state.getValue(property));
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static BlockState withProperty(BlockState state,Property property,String value) {
        Optional<?> parsed=property.getValue(value);
        if(parsed.isEmpty())throw new IllegalStateException("Invalid blockstate property "+property.getName()+"="+value);
        return state.setValue(property,(Comparable)parsed.get());
    }
    private static BlockState state(String source) {
        int split=source.indexOf('[');
        String blockId=split<0?source:source.substring(0,split);
        ResourceLocation key=ResourceLocation.tryParse(blockId);
        if(key==null || !BuiltInRegistries.BLOCK.containsKey(key))
            throw new IllegalStateException("Unknown R5 block "+source);
        BlockState state=BuiltInRegistries.BLOCK.get(key).defaultBlockState();
        if(split<0) {
            if(!state.getProperties().isEmpty())
                throw new IllegalStateException("Missing required properties "+source);
            return state;
        }
        if(!source.endsWith("]"))throw new IllegalStateException("Malformed R5 blockstate "+source);
        Map<String,String> args=new HashMap<>();
        for(String attr:source.substring(split+1,source.length()-1).split(",")) {
            String[] kv=attr.split("=",2);
            if(kv.length!=2 || args.put(kv[0],kv[1])!=null)
                throw new IllegalStateException("Duplicate/malformed R5 block property "+source);
        }
        if(args.size()!=state.getProperties().size())
            throw new IllegalStateException("Incomplete R5 blockstate "+source);
        for(Property<?> p:state.getProperties()) {
            String value=args.get(p.getName());
            if(value==null)throw new IllegalStateException("Missing property "+p.getName());
            state=withProperty(state,p,value);
        }
        return state;
    }
    private static BlockPos position(JsonObject row) {
        JsonArray x=row.getAsJsonArray("world_xyz");
        if(x==null||x.size()!=3)throw new IllegalStateException("Missing R5 world coordinate");
        return new BlockPos(x.get(0).getAsInt(),x.get(1).getAsInt(),x.get(2).getAsInt());
    }
    private static JsonObject read(ZipFile zip,String entry) throws IOException {
        ZipEntry file=zip.getEntry(entry);
        if(file==null || file.getSize()<10 || file.getSize()>6_000_000)
            throw new IOException("Missing/oversized R5 plan "+entry);
        try(Reader reader=new InputStreamReader(zip.getInputStream(file),StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
    private static boolean protectedFromCivic(BlockPos p,TerritoryDefinition city) {
        if(!city.siegeRegion().contains(p)||!city.restorationRegion().contains(p))return true;
        // This excludes all six industry sites, both town-hall footprints,
        // their successors and all adjoining road boundary cells conservatively.
        for(String id:List.of("vehicle_factory","quarry","weapons_factory","steel_mill",
                "farm","lumberyard","th1","th2","th3","th4","th5")) {
            SchematicAssetRegistry.Asset asset=SchematicAssetRegistry.asset(id);
            MapAnchor anchor=SchematicAssetRegistry.territoryAnchor(id,city);
            if(asset==null||anchor==null)return true;
            var bounds=asset.boundsAt(anchor);
            if(p.getX()>=bounds.min().getX()-1 && p.getX()<=bounds.max().getX()+1 &&
                    p.getZ()>=bounds.min().getZ()-1 && p.getZ()<=bounds.max().getZ()+1)return true;
        }
        return false;
    }
    private static Plan plan(MinecraftServer server,TerritoryDefinition city,
                             ServerLevel level,BuildingType type) throws Exception {
        R5ReadOnlyPreflight.verifyArchive();
        R5ReadOnlyPreflight.verifyIndex();
        String territory=city.id().toUpperCase(Locale.ROOT);
        String requested=kind(type);
        List<String> groupNames=type==BuildingType.COMMERCIAL_DISTRICT
                ?List.of("cleanup_tree_16_10","commercial_sidewalk")
                :List.of("logistics_service_connection");
        Path idx=FMLPaths.CONFIGDIR.get().resolve("catancraft").resolve(STAGED_INDEX);
        if(!Files.isRegularFile(idx))throw new IllegalStateException("Missing pinned R5 index archive");
        List<Write> writes=new ArrayList<>();
        LinkedHashMap<Long,Cell> all=new LinkedHashMap<>();
        Map<Long,BlockState> virtual=new HashMap<>();
        try(ZipFile index=new ZipFile(idx.toFile());ZipFile art=new ZipFile(R5ReadOnlyPreflight.archivePath().toFile())) {
            List<String> stageNames=new ArrayList<>(groupNames);
            stageNames.add("CIVIC");
            for(String stage:stageNames) {
                boolean civic="CIVIC".equals(stage);
                String path=civic?"contracts/civic-deltas/"+territory+"_"+requested+".json":
                        "contracts/groups/"+territory+"/"+stage+".json";
                JsonObject doc=read(civic?art:index,path);
                String actualTerritory=doc.get("territory_id").getAsString();
                if(!territory.equals(actualTerritory))throw new IllegalStateException("R5 stage wrong territory: "+path);
                if(civic) {
                    if(!type.name().equals(doc.get("module_id").getAsString()) ||
                            !"PASS_STATIC".equals(doc.get("status").getAsString()))
                        throw new IllegalStateException("R5 civic module or status invalid: "+path);
                } else if(!stage.equals(doc.get("local_group_id").getAsString()) ||
                        !"PASS_STATIC_ACTIVATED_R2_FIXTURE".equals(doc.get("status").getAsString()))
                    throw new IllegalStateException("R5 support stage invalid: "+path);
                JsonArray changes=doc.getAsJsonArray(civic?"writes":"changes");
                if(changes==null||changes.size()==0||changes.size()>6000)
                    throw new IllegalStateException("Invalid R5 change count "+path);
                Set<Long> seenInStage=new HashSet<>();
                for(JsonElement entry:changes) {
                    JsonObject row=entry.getAsJsonObject();
                    BlockPos p=position(row);
                    if(!seenInStage.add(p.asLong())||protectedFromCivic(p,city)||!level.hasChunkAt(p))
                        throw new IllegalStateException("Duplicated/protected/unloaded R5 cell "+p);
                    BlockState before=state(row.get("expected_before_blockstate").getAsString());
                    BlockState after=state(row.get("after_blockstate").getAsString());
                    if(before.hasBlockEntity() ||
                            (after.hasBlockEntity()&&after.getBlock()!=Blocks.BARREL) ||
                            !before.getFluidState().isEmpty()||!after.getFluidState().isEmpty())
                        throw new IllegalStateException("R5 plan contains a fluid or unapproved block-entity at "+p);
                    BlockState now=virtual.get(p.asLong());
                    if(now==null) {
                        now=level.getBlockState(p);
                        if(level.getBlockEntity(p)!=null||!level.getFluidState(p).isEmpty())
                            throw new IllegalStateException("Existing tile/fluid at R5 cell "+p);
                    }
                    if(!now.equals(before))
                        throw new IllegalStateException("Unexpected R7 surface at "+p+"; expected="+canonical(before)+" actual="+canonical(now)+". Refusing construction; nothing charged.");
                    Cell prior=all.get(p.asLong());
                    all.put(p.asLong(),new Cell(p,prior==null?now:prior.before(),after));
                    virtual.put(p.asLong(),after);
                    writes.add(new Write(p,before,after,stage));
                    if(all.size()>MAX_CELLS||writes.size()>MAX_WRITES)
                        throw new IllegalStateException("Excessive R5 construction mask");
                }
            }
        }
        int actualBarrels=(int)all.values().stream().filter(c->c.after().getBlock()==Blocks.BARREL).count();
        int requiredWrites=type==BuildingType.COMMERCIAL_DISTRICT?2835:3511;
        int requiredUnique=type==BuildingType.COMMERCIAL_DISTRICT?2793:3511;
        int requiredBarrels=type==BuildingType.COMMERCIAL_DISTRICT?8:24;
        if(writes.size()!=requiredWrites||all.size()!=requiredUnique||actualBarrels!=requiredBarrels)
            throw new IllegalStateException("R5 source counts changed; refuse unreviewed revision");
        // Reject interference with any entity (including dropped items) that
        // intersects a new non-air block. No clearing or teleporting entities.
        for(Cell c:all.values()) {
            if(c.after().isAir()||c.after().equals(c.before()))continue;
            if(!level.getEntities((Entity)null,new AABB(c.pos()),Entity::isAlive).isEmpty())
                throw new IllegalStateException("Entity in construction footprint at "+c.pos());
        }
        return new Plan(List.copyOf(writes),all,actualBarrels);
    }
    private static JsonArray xyz(BlockPos p) {
        JsonArray a=new JsonArray();a.add(p.getX());a.add(p.getY());a.add(p.getZ());return a;
    }
    private static void seal(JsonObject j) throws Exception {
        j.remove("sha256");
        byte[] b=MessageDigest.getInstance("SHA-256").digest(GSON.toJson(j).getBytes(StandardCharsets.UTF_8));
        StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format(Locale.ROOT,"%02x",x&255));
        j.addProperty("sha256",s.toString());
    }
    private static void persist(Path path,JsonObject j) throws Exception {
        seal(j);
        String serialized=GSON.toJson(j);
        if(serialized.getBytes(StandardCharsets.UTF_8).length>MAX_JOURNAL_BYTES)
            throw new IllegalStateException("Refusing to write oversized R5 journal");
        Files.createDirectories(path.getParent());
        Path tmp=path.resolveSibling(path.getFileName()+".pending-write");
        try {
            Files.writeString(tmp,serialized,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
            try(FileChannel channel=FileChannel.open(tmp,StandardOpenOption.WRITE)){channel.force(true);}
            Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            try(FileChannel channel=FileChannel.open(path,StandardOpenOption.WRITE)){channel.force(true);}
        } finally {Files.deleteIfExists(tmp);}
    }
    private static JsonObject journal(MinecraftServer server,Path world,ServerLevel level,
                                      TerritoryDefinition city,BuildingType type,
                                      NationData nation,EconomyCost cost,Plan plan) throws Exception {
        JsonObject j=new JsonObject();
        j.addProperty("schema",1);j.addProperty("phase","PENDING");
        j.addProperty("transaction_id",UUID.randomUUID().toString());
        j.addProperty("source_revision","R5-1 VERIFIED AGAINST R7");
        j.addProperty("world_root",world.toString());
        j.addProperty("dimension",level.dimension().location().toString());
        j.addProperty("territory",city.id());j.addProperty("module",type.name());
        j.addProperty("owner_nation_uuid",nation.id().toString());
        j.addProperty("treasury_before",nation.treasury());
        j.addProperty("money_cost",cost.money());
        JsonObject resources=new JsonObject();
        for(ResourceType r:ResourceType.values()) {
            JsonObject entry=new JsonObject();entry.addProperty("before",nation.resource(r));
            entry.addProperty("cost",cost.resources().getOrDefault(r,0L));
            resources.add(r.name(),entry);
        }
        j.add("resources",resources);
        JsonArray writes=new JsonArray();
        for(Write w:plan.writes()) {
            JsonObject cell=new JsonObject();cell.add("xyz",xyz(w.pos()));
            cell.addProperty("stage",w.stage());cell.addProperty("before",canonical(w.before()));
            cell.addProperty("after",canonical(w.after()));writes.add(cell);
        }
        j.add("ordered_writes",writes);
        JsonArray originals=new JsonArray();
        for(Cell c:plan.finalCells().values()) {
            JsonObject cell=new JsonObject();cell.add("xyz",xyz(c.pos()));
            cell.addProperty("before",canonical(c.before()));
            cell.addProperty("after",canonical(c.after()));cell.addProperty("before_nbt","NONE");
            originals.add(cell);
        }
        j.add("originals",originals);
        j.add("installed_barrels",new JsonObject());
        j.addProperty("physical_committed",false);j.addProperty("economic_committed",false);
        seal(j);return j;
    }
    private static void assertNoDroppedItems(ServerLevel level,AABB area,Set<UUID> previous) {
        for(ItemEntity item:level.getEntitiesOfClass(ItemEntity.class,area))
            if(!previous.contains(item.getUUID()))
                throw new IllegalStateException("Unplanned dropped item entity detected at "+item.blockPosition());
    }
    public static synchronized Result purchase(MinecraftServer server,CatanSavedData data,
                                               NationData nation,TerritoryData territory,
                                               BuildingType type,EconomyCost cost) {
        boolean physicalStarted=false,economyChanged=false;
        Path record=null;
        try {
            if(!isCivic(type))throw new IllegalArgumentException("Invalid R5 civic type");
            Path world=root(server);
            ensureTargetWorld(server,world);
            if(!nation.id().equals(territory.ownerNationId())||
                    data.nation(nation.id())==null)
                throw new IllegalStateException("Changed civic owner; refusing purchase");
            if(territory.cityLevel()<type.minCityLevel() ||
                    territory.buildings().size()>=EconomyCatalog.maxBuildingSlots(territory.cityLevel()) ||
                    territory.buildings().stream().anyMatch(b->b.type()==type) ||
                    !cost.canAfford(nation))
                throw new IllegalStateException("Civic purchase capacity, uniqueness, level or resources changed");
            String kind=kind(type);
            record=journal(world,territory.id(),kind);
            if(Files.exists(record))
                throw new IllegalStateException("A construction journal already exists for this city/civic (including history). Do not clear it: "+record);
            if("a".equals(territory.id())) {
                Path previous=world.resolve("data").resolve(type==BuildingType.COMMERCIAL_DISTRICT?
                        "catancraft_r5_city_a_commercial_v1.json":"catancraft_r5_city_a_logistics_v1.json");
                if(Files.exists(previous))
                    throw new IllegalStateException("Historical City A pilot journal present. Refusing to overwrite a previously tested building history.");
            }
            TerritoryDefinition city=MapDefinitionManager.territory(territory.id());
            if(city==null || city.cityCenter()==null || city.siegeRegion()==null ||
                    city.restorationRegion()==null)
                throw new IllegalStateException("No approved R5 city placement contract");
            ServerLevel level=null;
            for(ServerLevel l:server.getAllLevels())
                if(l.dimension().location().toString().equals(city.dimension())){level=l;break;}
            if(level==null)throw new IllegalStateException("Civic world dimension unavailable");
            Plan plan=plan(server,city,level,type);
            JsonObject j=journal(server,world,level,city,type,nation,cost,plan);
            // Persist a complete pre-image of every touched block and economy
            // balance before any mutation. Never rewrite a prior transaction.
            persist(record,j);
            if(!Files.isRegularFile(record))throw new IllegalStateException("Journal durability check failed");
            physicalStarted=true;
            int x0=Integer.MAX_VALUE,y0=Integer.MAX_VALUE,z0=Integer.MAX_VALUE;
            int x1=Integer.MIN_VALUE,y1=Integer.MIN_VALUE,z1=Integer.MIN_VALUE;
            for(Cell c:plan.finalCells().values()) {
                BlockPos p=c.pos();x0=Math.min(x0,p.getX());y0=Math.min(y0,p.getY());z0=Math.min(z0,p.getZ());
                x1=Math.max(x1,p.getX());y1=Math.max(y1,p.getY());z1=Math.max(z1,p.getZ());
            }
            AABB observation=new AABB(x0-2,y0-2,z0-2,x1+3,y1+3,z1+3);
            Set<UUID> previousItems=new HashSet<>();
            for(ItemEntity item:level.getEntitiesOfClass(ItemEntity.class,observation))previousItems.add(item.getUUID());
            for(Write w:plan.writes()) {
                if(!level.hasChunkAt(w.pos())||!level.getBlockState(w.pos()).equals(w.before()))
                    throw new IllegalStateException("Write-time R5 block drift at "+w.pos());
                if(level.getBlockEntity(w.pos())!=null||!level.getFluidState(w.pos()).isEmpty())
                    throw new IllegalStateException("Unexpected tile/fluid appeared at "+w.pos());
                if(!w.after().isAir() && !w.before().equals(w.after()) &&
                        !level.getEntities((Entity)null,new AABB(w.pos()),Entity::isAlive).isEmpty())
                    throw new IllegalStateException("Entity moved into R5 write at "+w.pos());
                if(!w.before().equals(w.after()) &&
                        !level.setBlock(w.pos(),w.after(),WRITE_FLAGS))
                    throw new IllegalStateException("Block placement refused at "+w.pos());
                if(!level.getBlockState(w.pos()).equals(w.after()))
                    throw new IllegalStateException("Immediate placed state drift at "+w.pos());
            }
            for(Cell c:plan.finalCells().values())
                if(!level.getBlockState(c.pos()).equals(c.after()))
                    throw new IllegalStateException("Final R5 placement mask mismatch at "+c.pos());
            JsonObject barrels=j.getAsJsonObject("installed_barrels");
            for(Cell c:plan.finalCells().values()) {
                if(c.after().getBlock()!=Blocks.BARREL)continue;
                BlockEntity be=level.getBlockEntity(c.pos());
                if(!(be instanceof Container container)||!container.isEmpty())
                    throw new IllegalStateException("Missing/nonempty new barrel at "+c.pos());
                barrels.addProperty(Long.toString(c.pos().asLong()),be.saveWithFullMetadata().toString());
            }
            if(barrels.size()!=plan.barrels())throw new IllegalStateException("R5 barrel count mismatch");
            assertNoDroppedItems(level,observation,previousItems);
            j.addProperty("phase","PHYSICAL_COMPLETE_ECONOMY_PENDING");
            j.addProperty("physical_committed",true);persist(record,j);
            // Charge once and create exactly one building in the same NBT save.
            // A crash before both records have been saved leaves the journal as
            // pending and REFUSES any re-purchase rather than double-charging.
            if(!cost.canAfford(nation) || territory.buildings().stream().anyMatch(b->b.type()==type))
                throw new IllegalStateException("Civic purchase eligibility changed after placement");
            cost.charge(nation);
            territory.buildings().add(new BuildingInstance(type,1,0,""));
            data.setDirty();economyChanged=true;
            server.saveEverything(true,true,true);
            j.addProperty("phase","COMMITTED");
            j.addProperty("economic_committed",true);persist(record,j);
            CatanCraft.LOGGER.info("R5 AUTO CIVIC {} {} COMMITTED, {} writes, 1 charge, journal {}",
                    territory.id(),kind,plan.writes().size(),record);
            return new Result(true,"Automatically constructed "+type.displayName()+" in city "+
                    territory.id().toUpperCase(Locale.ROOT)+" ("+plan.writes().size()+
                    " safe source-validated block writes). Purchased once; persists with its journal.");
        } catch(Exception failure) {
            CatanCraft.LOGGER.error("R5 automatic civic construction REFUSED for {} in {}. "+
                    "World writes started={} economyChanged={} journal={}",
                    type,territory.id(),physicalStarted,economyChanged,record,failure);
            return new Result(false,"Civic auto-construction refused: "+failure.getMessage()+
                    (physicalStarted?". JOURNAL PRESERVED; blocks may have changed. Do NOT retry purchase; do not clear history. Contact development for guarded recovery.":
                            ". No world writes started and no resources charged."));
        }
    }
}