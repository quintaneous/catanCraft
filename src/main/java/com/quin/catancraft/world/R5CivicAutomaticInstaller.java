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
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
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
 * recovered through its hash-bound recovery companion without replaying a charge.
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
    record Write(BlockPos pos,BlockState before,BlockState after,String stage) {}
    record Cell(BlockPos pos,BlockState before,BlockState after) {}
    record Plan(List<Write> writes,LinkedHashMap<Long,Cell> finalCells,int barrels) {}
    record NaturalSource(Cell soil,Cell cover) {}
    private record Prepared(Plan plan,Map<BlockPos,BlockState> actualNaturalSources) {}

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
    static void ensureTargetWorld(MinecraftServer server,Path root) throws IOException {
        if(Files.exists(root.resolve(FreshDemoAuthorization.MARKER),java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            if(!FreshDemoAuthorization.authorize(server).equals(root))throw new IOException("Foreign fresh civic world");
            return;
        }
        if(!ROOT_NAME.equals(root.getFileName().toString()) ||
                !WORLD_LEVEL_NAME.equals(server.getWorldData().getLevelName()))
            throw new IllegalStateException("Auto-build disabled outside the exact new R7 development-world folder and LevelName. No purchase was charged.");
        Path marker=root.resolve(MARKER_NAME);
        if(!Files.isRegularFile(marker)||Files.size(marker)>250 ||
                !MARKER_VALUE.equals(Files.readString(marker).trim()))
            throw new IllegalStateException("R7 automatic construction is not enabled for this disposable world. Missing one-time world-root marker: "+MARKER_NAME+". No purchase was charged.");
    }
    static String canonical(BlockState state) {
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
    static BlockState state(String source) {
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
    static Plan contract(TerritoryDefinition city,BuildingType type) throws Exception {
        R5ReadOnlyPreflight.verifyArchive();
        R5ReadOnlyPreflight.verifyIndex();
        Path idx=FMLPaths.CONFIGDIR.get().resolve("catancraft").resolve(STAGED_INDEX);
        try(ZipFile index=new ZipFile(idx.toFile());ZipFile art=new ZipFile(R5ReadOnlyPreflight.archivePath().toFile())) {
            return contract(city,type,(civic,path)->read(civic?art:index,path));
        }
    }
    interface Documents { JsonObject read(boolean civic,String path)throws Exception; }
    static Plan contract(TerritoryDefinition city,BuildingType type,Documents documents)throws Exception {
        String territory=city.id().toUpperCase(Locale.ROOT);
        String requested=kind(type);
        List<String> groupNames=type==BuildingType.COMMERCIAL_DISTRICT
                ?List.of("cleanup_tree_16_10","commercial_sidewalk")
                :List.of("logistics_service_connection");

        List<Write> writes=new ArrayList<>();
        LinkedHashMap<Long,Cell> all=new LinkedHashMap<>();
        Map<Long,BlockState> virtual=new HashMap<>();
        {
            List<String> stageNames=new ArrayList<>(groupNames);
            stageNames.add("CIVIC");
            for(String stage:stageNames) {
                boolean civic="CIVIC".equals(stage);
                String path=civic?"contracts/civic-deltas/"+territory+"_"+requested+".json":
                        "contracts/groups/"+territory+"/"+stage+".json";
                JsonObject doc=documents.read(civic,path);
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
                    if(!seenInStage.add(p.asLong())||protectedFromCivic(p,city))
                        throw new IllegalStateException("Duplicated/protected/unloaded R5 cell "+p);
                    BlockState before=state(row.get("expected_before_blockstate").getAsString());
                    BlockState after=state(row.get("after_blockstate").getAsString());
                    if(before.hasBlockEntity() ||
                            (after.hasBlockEntity()&&after.getBlock()!=Blocks.BARREL) ||
                            !before.getFluidState().isEmpty()||!after.getFluidState().isEmpty())
                        throw new IllegalStateException("R5 plan contains a fluid or unapproved block-entity at "+p);
                    BlockState now=virtual.get(p.asLong());
                    if(now==null) now=before;
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
        return new Plan(List.copyOf(writes),all,actualBarrels);
    }
    /** The one pinned commercial tree base is the only reviewed natural source.
     * Its cleanup is an earlier owned write; future civic floors never authorize dirt. */
    static Map<BlockPos,NaturalSource> naturalSources(Plan plan) throws IOException {
        Map<BlockPos,NaturalSource> result=new LinkedHashMap<>();
        for(Cell soil:plan.finalCells().values()) {
            if(!soil.before().equals(Blocks.GRASS_BLOCK.defaultBlockState())||!soil.after().equals(Blocks.SMOOTH_STONE.defaultBlockState()))continue;
            Cell cover=plan.finalCells().get(soil.pos().above().asLong());
            if(cover==null||!cover.before().equals(Blocks.OAK_LOG.defaultBlockState())||!cover.after().isAir()
                    ||!SectorNaturalSupportPolicy.blocksGrass(cover.before(),cover.pos()))continue;
            List<Write> soilWrites=plan.writes().stream().filter(w->w.pos().equals(soil.pos())).toList();
            List<Write> coverWrites=plan.writes().stream().filter(w->w.pos().equals(cover.pos())).toList();
            if(soilWrites.size()!=1||coverWrites.size()!=1)throw new IOException("Changed natural civic source route");
            Write s=soilWrites.get(0),c=coverWrites.get(0);
            if(!s.stage().equals("CIVIC")||!c.stage().equals("cleanup_tree_16_10")
                    ||!s.before().equals(soil.before())||!s.after().equals(soil.after())
                    ||!c.before().equals(cover.before())||!c.after().equals(cover.after())
                    ||plan.writes().indexOf(c)>=plan.writes().indexOf(s))throw new IOException("Unapproved natural civic source sequence");
            result.put(soil.pos(),new NaturalSource(soil,cover));
        }
        if(result.size()>1)throw new IOException("Unexpected natural civic source count");
        return Collections.unmodifiableMap(result);
    }
    static Map<BlockPos,BlockState> captureSource(ServerLevel level,Plan plan)throws Exception {
        Map<BlockPos,NaturalSource> natural=naturalSources(plan);Map<BlockPos,Cell> leaves=naturalLeafSources(plan);Map<BlockPos,BlockState> actual=new LinkedHashMap<>();
        for(Cell c:plan.finalCells().values()) {
            if(!level.hasChunkAt(c.pos()))throw new PreparationDeferred("Civic source chunks are still loading");
            BlockState live=level.getBlockState(c.pos());NaturalSource proof=natural.get(c.pos());
            boolean covered=proof!=null&&level.hasChunkAt(proof.cover().pos())
                    &&level.getBlockState(proof.cover().pos()).equals(proof.cover().before())&&level.getBlockEntity(proof.cover().pos())==null;
            if(!(live.equals(c.before())||covered&&live.equals(Blocks.DIRT.defaultBlockState())||leaves.containsKey(c.pos())&&StreetscapeR5.matches(live,c.before()))||level.getBlockEntity(c.pos())!=null||!level.getFluidState(c.pos()).isEmpty())
                throw new IllegalStateException("Unexpected R7 surface/tile/fluid at "+c.pos()+"; nothing charged");
            if(proof!=null){if(!covered)throw new IOException("Missing exact original civic source cover");actual.put(c.pos(),live);}
            if(leaves.containsKey(c.pos()))actual.put(c.pos(),live);
        }
        // Reject interference with any entity (including dropped items) that
        // intersects a new non-air block. No clearing or teleporting entities.
        for(Cell c:plan.finalCells().values()) {
            if(c.after().isAir()||c.after().equals(c.before()))continue;
            if(!level.getEntities((Entity)null,new AABB(c.pos()),Entity::isAlive).isEmpty())
                throw new IllegalStateException("Entity in construction footprint at "+c.pos());
        }
        return Collections.unmodifiableMap(actual);
    }
    static Map<BlockPos,Cell> naturalLeafSources(Plan plan)throws IOException {
        Map<BlockPos,Cell> leaves=new LinkedHashMap<>();
        BlockState expected=Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE,1).setValue(LeavesBlock.PERSISTENT,true);
        for(Write w:plan.writes())if(w.stage().equals("cleanup_tree_16_10")&&w.before().is(Blocks.OAK_LEAVES)) {
            Cell c=plan.finalCells().get(w.pos().asLong());
            if(!w.before().equals(expected)||!w.after().isAir()||c==null||!c.before().equals(expected)||leaves.put(w.pos(),c)!=null)
                throw new IOException("Changed pinned civic cleanup leaf contract");
        }
        if(!leaves.isEmpty()&&leaves.size()!=84)throw new IOException("Changed pinned cleanup leaf count");
        return Collections.unmodifiableMap(leaves);
    }
    static List<Write> executionWrites(Plan plan,int schema)throws IOException {
        if(schema==1)return plan.writes();
        if(schema!=2)throw new IOException("Unknown civic execution schema");
        Set<BlockPos> natural=naturalSources(plan).keySet();List<Write> writes=new ArrayList<>();
        // Stable partition of the single reviewed soil write. Replacing this
        // full solid support first keeps its exact source cover present until
        // the last possible grass/dirt preimage has been consumed.
        for(Write w:plan.writes())if(natural.contains(w.pos()))writes.add(w);
        for(Write w:plan.writes())if(!natural.contains(w.pos()))writes.add(w);
        return List.copyOf(writes);
    }
    /** Read-only full paid-civic geometry check; opening or using a barrel is
     * legitimate and must not clear/recreate its inventory. No target grass
     * remains in either pinned civic contract. */
    static int verifyInstalled(ServerLevel level,TerritoryDefinition city,BuildingType type)throws Exception {
        return verifyInstalled(level,contract(city,type));
    }
    static int verifyInstalled(ServerLevel level,Plan plan)throws IOException {
        for(Cell c:plan.finalCells().values()) {
            if(!level.hasChunkAt(c.pos()))throw new PreparationDeferred("Civic target chunks are still loading");
            BlockState live=level.getBlockState(c.pos()),target=c.after();BlockEntity be=level.getBlockEntity(c.pos());
            if(target.is(Blocks.BARREL)) {
                if(!live.is(Blocks.BARREL)||!live.setValue(BarrelBlock.OPEN,false).equals(target.setValue(BarrelBlock.OPEN,false))||!(be instanceof BarrelBlockEntity))
                    throw new IOException("Installed civic barrel conflict at "+c.pos());
            } else if(!live.equals(target)||be!=null)throw new IOException("Installed civic target conflict at "+c.pos());
        }
        return plan.finalCells().size();
    }
    private static Prepared plan(TerritoryDefinition city,ServerLevel level,BuildingType type)throws Exception {
        Plan plan=contract(city,type);return new Prepared(plan,captureSource(level,plan));
    }
    static JsonArray naturalSourcePreimages(Plan plan,Map<BlockPos,BlockState> actual)throws IOException {
        Map<BlockPos,NaturalSource> natural=naturalSources(plan);
        Set<BlockPos> expected=new HashSet<>(natural.keySet());expected.addAll(naturalLeafSources(plan).keySet());
        if(!actual.keySet().equals(expected))throw new IOException("Incomplete civic natural source preimage");
        JsonArray rows=new JsonArray();
        for(var entry:natural.entrySet()) {
            NaturalSource proof=entry.getValue();BlockState before=actual.get(entry.getKey());
            if(!proof.soil().before().equals(before)&&!Blocks.DIRT.defaultBlockState().equals(before))throw new IOException("Foreign civic natural preimage");
            JsonObject row=new JsonObject();row.add("xyz",xyz(proof.soil().pos()));
            row.addProperty("source_before",canonical(proof.soil().before()));row.addProperty("actual_before",canonical(before));
            row.addProperty("after",canonical(proof.soil().after()));row.add("source_cover_xyz",xyz(proof.cover().pos()));
            row.addProperty("source_cover",canonical(proof.cover().before()));rows.add(row);
        }
        return rows;
    }
    static JsonArray naturalLeafPreimages(Plan plan,Map<BlockPos,BlockState> actual)throws IOException {
        JsonArray rows=new JsonArray();for(Cell c:naturalLeafSources(plan).values()) {
            BlockState before=actual.get(c.pos());
            if(before==null||!StreetscapeR5.matches(before,c.before()))throw new IOException("Foreign civic cleanup leaf preimage");
            JsonObject row=new JsonObject();row.add("xyz",xyz(c.pos()));row.addProperty("source_before",canonical(c.before()));
            row.addProperty("actual_before",canonical(before));row.addProperty("after",canonical(c.after()));rows.add(row);
        }return rows;
    }
    static JsonArray xyz(BlockPos p) {
        JsonArray a=new JsonArray();a.add(p.getX());a.add(p.getY());a.add(p.getZ());return a;
    }
    static void seal(JsonObject j) throws Exception {
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
    static JsonObject journal(Path world,ServerLevel level,
                                      TerritoryDefinition city,BuildingType type,
                                      NationData nation,EconomyCost cost,Plan plan,Map<BlockPos,BlockState> actualNaturalSources) throws Exception {
        JsonObject j=new JsonObject();
        j.addProperty("schema",2);j.addProperty("phase","PENDING");j.addProperty("receipt_required",true);
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
        for(Write w:executionWrites(plan,2)) {
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
        // Source arrays remain the immutable artwork contract. This versioned
        // companion field records the actual preimage instead of repainting grass.
        j.add("natural_source_preimages",naturalSourcePreimages(plan,actualNaturalSources));
        j.add("natural_leaf_preimages",naturalLeafPreimages(plan,actualNaturalSources));
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
            PaidRecoveryService.requireClear(server,territory.id());
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
            Prepared prepared=plan(city,level,type);Plan plan=prepared.plan();
            JsonObject j=journal(world,level,city,type,nation,cost,plan,prepared.actualNaturalSources());
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
            for(Write w:executionWrites(plan,2)) {
                // A cleanup leaf may be followed by a second civic AIR→roof
                // write. Only the original source write consumes its preimage.
                BlockState before=w.before().equals(plan.finalCells().get(w.pos().asLong()).before())
                        ?prepared.actualNaturalSources().getOrDefault(w.pos(),w.before()):w.before();
                if(!level.hasChunkAt(w.pos())||!level.getBlockState(w.pos()).equals(before))
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
            PaidConstructionRecovery.receipt(data,UUID.fromString(j.get("transaction_id").getAsString()));
            data.setDirty();economyChanged=true;
            if(!server.saveEverything(true,true,true))throw new IOException("Civic save incomplete; original purchase retained for recovery");
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
                    (physicalStarted?". JOURNAL PRESERVED; blocks may have changed. Do NOT retry purchase or clear history. Recovery will reconcile this original purchase automatically when its prerequisites are ready.":
                            ". No world writes started and no resources charged."));
        }
    }
}
