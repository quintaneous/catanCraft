package com.quin.catancraft.world;

import com.google.gson.*;
import com.quin.catancraft.map.TerritoryDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Versioned authored-map qualification. Art transforms do not establish source authority:
 * exact per-site source observations come from the separately pinned clean-R7 audit. */
final class SectorSites53 {
    static final String REVISION = "all_city_sectors53_v1";
    // Exact independently audited clean-R7 source/normal preparation catalog.
    static final String RESOURCE_SHA = "b2b4f028db5838e837887bc569d06068837d4df2e536442bd947250335acd5b5";
    static final String INPUT_MANIFEST_SHA = "e7ecd5d0f0ac5c1457be5c234d2e5e08c9239007787b20e0bdc45b3e2b8d862d";
    private static final BlockPos A_CENTER = new BlockPos(-750,81,-750);
    private static Map<String,Site> sites;
    private static final Map<Site,Map<BlockPos,SectorContracts.Direction>> OWNERSHIP=new IdentityHashMap<>();
    private SectorSites53() {}

    record Site(TerritoryDefinition definition, Map<BlockPos,BlockState> sourceOverrides,
                Map<BlockPos,BlockState> ownedSourceOverrides, Set<BlockPos> protectedRoads) {
        Site {
            sourceOverrides=spatialMap(sourceOverrides);ownedSourceOverrides=spatialMap(ownedSourceOverrides);
            Set<BlockPos> roads=new HashSet<>(protectedRoads);if(roads.contains(null))throw new NullPointerException("Null qualified road position");
            protectedRoads=Collections.unmodifiableSet(roads);
        }
        String city() { return definition.id(); }
        int rotation() { return definition.cityCenter().rotation(); }
        BlockPos center() { var p=definition.cityCenter();return new BlockPos(p.x(),p.y(),p.z()); }
        BlockPos point(BlockPos a) {
            BlockPos local=a.subtract(A_CENTER);
            if(rotation()==180)local=new BlockPos(-local.getX(),local.getY(),-local.getZ());
            return center().offset(local);
        }
        BlockPos original(BlockPos world) {
            BlockPos local=world.subtract(center());
            if(rotation()==180)local=new BlockPos(-local.getX(),local.getY(),-local.getZ());
            return A_CENTER.offset(local);
        }
        BlockState state(BlockState state) { return state.rotate(rotation()==180?Rotation.CLOCKWISE_180:Rotation.NONE); }
        SectorContracts.Direction direction(SectorContracts.Direction side) {
            if(rotation()==0)return side;
            return switch(side){case NORTH->SectorContracts.Direction.SOUTH;case EAST->SectorContracts.Direction.WEST;
                case SOUTH->SectorContracts.Direction.NORTH;case WEST->SectorContracts.Direction.EAST;};
        }
        String corner(String corner) throws IOException {
            if(!Set.of("NW","NE","SW","SE").contains(corner))throw new IOException("Unknown world compass corner");
            if(rotation()==0)return corner;
            return switch(corner){case "NW"->"SE";case "NE"->"SW";case "SW"->"NE";default->"NW";};
        }
        Map<SectorContracts.Direction,Integer> localLevels(Map<SectorContracts.Direction,Integer> levels)throws IOException {
            if(levels.size()!=4)throw new IOException("All four physical compass levels required");
            var out=new EnumMap<SectorContracts.Direction,Integer>(SectorContracts.Direction.class);
            for(var side:SectorContracts.Direction.values())out.put(direction(side),levels.get(side));
            return SectorCityAP3.levels(out.get(SectorContracts.Direction.NORTH),out.get(SectorContracts.Direction.EAST),
                    out.get(SectorContracts.Direction.SOUTH),out.get(SectorContracts.Direction.WEST));
        }
        Set<String> corners(Set<String> corners)throws IOException {Set<String> out=new HashSet<>();for(String c:corners)out.add(corner(c));return Set.copyOf(out);}
        boolean rearRoad(BlockPos p) {BlockPos a=original(p);return a.getX()>=-770&&a.getX()<=-730&&a.getZ()>=-666;}
    }

    static synchronized Site site(String city)throws IOException {
        if(sites==null)load();Site site=sites.get(city);if(site==null)throw new IOException("No reviewed structural wall site for "+city);return site;
    }
    static void canonical(TerritoryDefinition definition)throws IOException {
        ShopSites51.canonical(definition);Site site=site(definition.id());
        if(!Objects.equals(site.definition(),definition)) {
            // ShopSites51 compares the placement fields exactly, including both protection regions.
            if(!site.center().equals(new BlockPos(definition.cityCenter().x(),definition.cityCenter().y(),definition.cityCenter().z()))
                    ||site.rotation()!=definition.cityCenter().rotation())throw new IOException("Wall site center/rotation changed");
        }
    }
    private static BlockPos pos(JsonArray row)throws IOException {
        if(row.size()<3)throw new IOException("Incomplete wall qualification position");
        try{return new BlockPos(row.get(0).getAsInt(),row.get(1).getAsInt(),row.get(2).getAsInt());}
        catch(RuntimeException e){throw new IOException("Invalid wall qualification position",e);}
    }
    private static void load()throws IOException {
        byte[] compressed;try(var stream=SectorSites53.class.getResourceAsStream("/assets/catancraft/sector53/sites.json.gz")){
            if(stream==null)throw new IOException("All-city structural source qualification is unavailable");compressed=stream.readNBytes(8_000_001);
        }
        if(compressed.length>8_000_000||!TownHallRevisions.sha(compressed).equals(RESOURCE_SHA))throw new IOException("All-city structural source qualification hash changed");
        JsonObject root;try(var gz=new GZIPInputStream(new ByteArrayInputStream(compressed))){
            byte[] bytes=gz.readNBytes(64_000_001);if(bytes.length>64_000_000)throw new IOException("Oversized wall source qualification");
            root=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(RuntimeException e){throw new IOException("Invalid wall qualification document",e);}
        if(root.get("schemaVersion").getAsInt()!=1||!ShopSites51.MAP_SHA256.equals(root.get("mapSHA256").getAsString())
                ||!INPUT_MANIFEST_SHA.equals(root.get("inputManifestSHA256").getAsString())
                ||!SectorCityAP3.RESOURCE_SHA.equals(root.get("templateResourceSHA256").getAsString()))throw new IOException("Wall qualification provenance changed");
        JsonObject entries=root.getAsJsonObject("cities");Map<String,Site> found=new TreeMap<>();
        var baseline=SectorCityAP3.assembly(SectorCityAP3.levels(0,0,0,0),Set.of()).states();
        for(var def:ShopSites51.reviewedDefinitions()){
            if(def.cityCenter()==null||!Set.of(0,180).contains(def.cityCenter().rotation()))throw new IOException("Unqualified wall orientation");
            JsonObject row=entries.getAsJsonObject(def.id());if(row==null||!row.get("qualified").getAsBoolean()
                    ||row.get("rotation").getAsInt()!=def.cityCenter().rotation()
                    ||!pos(row.getAsJsonArray("center")).equals(new BlockPos(def.cityCenter().x(),def.cityCenter().y(),def.cityCenter().z())))throw new IOException("Missing exact qualified wall site "+def.id());
            Site transform=new Site(def,Map.of(),Map.of(),Set.of());Map<BlockPos,BlockState> overrides=new HashMap<>();
            for(var item:row.getAsJsonArray("sourceOverrides")){
                var value=item.getAsJsonArray();if(value.size()!=4)throw new IOException("Unknown wall source override shape");BlockPos p=pos(value),original=transform.original(p);
                BlockState state=ProducerExteriorR2.parseExact(value.get(3).getAsString());
                if(!baseline.containsKey(original)||SectorCityAP3.ownership().containsKey(original)
                        ||state.equals(transform.state(baseline.get(original)))||overrides.putIfAbsent(p,state)!=null)
                    throw new IOException("Wall qualification may only override unique observed unchanged context: "+p);
            }
            Map<BlockPos,BlockState> ownedSources=new HashMap<>();
            var basic=SectorCityAP3.assembly(SectorCityAP3.levels(1,1,1,1),Set.of()).states();
            var reinforced=SectorCityAP3.assembly(SectorCityAP3.levels(2,2,2,2),Set.of()).states();
            for(var item:row.getAsJsonArray("ownedSourceOverrides")){
                var value=item.getAsJsonArray();if(value.size()!=6)throw new IOException("Unknown owned-source override shape");BlockPos p=pos(value),a=transform.original(p);
                BlockState before=ProducerExteriorR2.parseExact(value.get(3).getAsString()),low=ProducerExteriorR2.parseExact(value.get(4).getAsString()),high=ProducerExteriorR2.parseExact(value.get(5).getAsString());
                if(SectorCityAP3.ownership().get(a)!=SectorContracts.Direction.NORTH||a.getY()!=81||a.getZ() < -853||a.getZ()>-852
                        ||!(a.getX()>=-763&&a.getX()<=-761||a.getX()>=-736&&a.getX()<=-732)
                        ||!before.equals(Blocks.GRASS_BLOCK.defaultBlockState())||!low.equals(Blocks.STONE_BRICKS.defaultBlockState())||!high.equals(Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState())
                        ||!low.equals(transform.state(basic.get(a)))||!high.equals(transform.state(reinforced.get(a)))
                        ||ownedSources.putIfAbsent(p,before)!=null)throw new IOException("Unreviewed structural source soil/target tuple: "+p);
            }
            Set<BlockPos> roads=new HashSet<>();for(var item:row.getAsJsonArray("protectedRoads")){
                var value=item.getAsJsonArray();if(value.size()!=3)throw new IOException("Unknown wall road shape");BlockPos p=pos(value),original=transform.original(p);
                if(!baseline.containsKey(original)||SectorCityAP3.ownership().containsKey(original)||!roads.add(p))throw new IOException("Unqualified/owned wall road position "+p);
            }
            for(BlockPos p:baseline.keySet())if(transform.rearRoad(transform.point(p))&&!SectorCityAP3.ownership().containsKey(p)&&!roads.contains(transform.point(p)))throw new IOException("Missing rear resource corridor read");
            if(def.id().equals("a")&&(!overrides.isEmpty()||!ownedSources.isEmpty()))throw new IOException("Accepted City A source cannot be revised by the all-city provider");
            found.put(def.id(),new Site(def,overrides,ownedSources,roads));
        }
        if(entries.size()!=16||found.size()!=16)throw new IOException("Wall qualification must cover exactly A through P");sites=Map.copyOf(found);
    }
    static String revision(Site site,SectorContracts.Direction side,int level)throws IOException {
        return REVISION+":"+site.city()+":"+side+":"+RESOURCE_SHA+":"+SectorCityAP3.revision(site.direction(side),level)+":"+SectorNaturalSupportPolicy.SHAPED_VERSION;
    }
    /** BlockPos hashes cluster in spatial strips. Preserve HashMap's collision handling,
     * while copying before wrapping so callers cannot mutate published qualification. */
    private static <V> Map<BlockPos,V> spatialMap(Map<BlockPos,V> source) {
        Map<BlockPos,V> copy=new HashMap<>(source);
        if(copy.containsKey(null)||copy.containsValue(null))throw new NullPointerException("Null qualified spatial entry");
        return Collections.unmodifiableMap(copy);
    }
    static synchronized Map<BlockPos,SectorContracts.Direction> ownership(Site site)throws IOException {
        Map<BlockPos,SectorContracts.Direction> cached=OWNERSHIP.get(site);if(cached!=null)return cached;
        Map<BlockPos,SectorContracts.Direction> out=new HashMap<>();
        SectorCityAP3.ownership().forEach((p,d)->out.put(site.point(p),site.direction(d)));
        Map<BlockPos,SectorContracts.Direction> immutable=spatialMap(out);
        if(OWNERSHIP.size()>=16)OWNERSHIP.clear();OWNERSHIP.put(site,immutable);return immutable;
    }
    static Map<BlockPos,BlockState> assembly(Site site,Map<SectorContracts.Direction,Integer> levels,Set<String> open)throws IOException {
        var original=SectorCityAP3.assembly(site.localLevels(levels),site.corners(open));Map<BlockPos,BlockState> states=new HashMap<>();
        original.states().forEach((p,s)->states.put(site.point(p),site.state(s)));states.putAll(site.sourceOverrides());
        var owners=ownership(site);for(var row:site.ownedSourceOverrides().entrySet())if(levels.get(owners.get(row.getKey()))==0)states.put(row.getKey(),row.getValue());
        return spatialMap(states);
    }
    static Set<String> availableConnections(Site site,Map<SectorContracts.Direction,Integer> levels)throws IOException {
        return site.corners(SectorCityAP3.availableConnections(site.localLevels(levels)));
    }
    static SectorCityAP3.Transition transition(Site site,SectorContracts.Direction side,Map<SectorContracts.Direction,Integer> levels,Set<String> open)throws IOException {
        var original=SectorCityAP3.transition(site.direction(side),site.localLevels(levels),site.corners(open));
        var next=new EnumMap<SectorContracts.Direction,Integer>(SectorContracts.Direction.class);next.putAll(levels);next.put(side,levels.get(side)+1);
        var source=assembly(site,levels,open);var target=assembly(site,next,site.corners(original.afterConnections()));var allOwners=ownership(site);
        List<SectorTransaction.Cell> cells=new ArrayList<>();List<SectorTransaction.Read> reads=new ArrayList<>();Map<BlockPos,SectorContracts.Direction> owners=new HashMap<>();
        for(BlockPos p:source.keySet()){
            BlockState before=source.get(p),after=target.get(p);
            if(!before.equals(after)){
                if(!allOwners.containsKey(p))throw new IOException("All-city transition changes unowned source context: "+p);
                cells.add(new SectorTransaction.Cell(p,before,after));owners.put(p,allOwners.get(p));
            }else reads.add(new SectorTransaction.Read(p,before));
        }
        cells.sort(Comparator.comparingLong(c->c.pos().asLong()));reads.sort(Comparator.comparingLong(r->r.pos().asLong()));
        var strict=new SectorTransaction.Plan(side.name().toLowerCase(Locale.ROOT),revision(site,side,levels.get(side)),revision(site,side,next.get(side)),levels.get(side),next.get(side),List.copyOf(cells),List.copyOf(reads));
        var before=SectorNaturalSupportPolicy.geometry(strict,false);var after=SectorNaturalSupportPolicy.geometry(strict,true);Set<BlockPos> grass=new HashSet<>();Map<BlockPos,BlockState> fixed=new HashMap<>();
        Set<BlockPos> readPositions=new HashSet<>();for(var row:reads)readPositions.add(row.pos());
        for(var row:reads)if(row.state().equals(Blocks.GRASS_BLOCK.defaultBlockState())){
            BlockPos above=row.pos().above();
            if(allOwners.containsKey(above)){
                if(SectorNaturalSupportPolicy.blocksGrass(before.get(above),above)||SectorNaturalSupportPolicy.blocksGrass(after.get(above),above))grass.add(row.pos());
            }else if(readPositions.contains(above)&&Objects.equals(before.get(above),after.get(above))&&SectorNaturalSupportPolicy.blocksGrass(before.get(above),above))fixed.put(row.pos(),before.get(above));
        }
        var plan=new SectorTransaction.Plan(strict.sector(),strict.sourceRevision(),strict.targetRevision(),strict.sourceLevel(),strict.targetLevel(),strict.cells(),strict.reads(),SectorNaturalSupportPolicy.coveredGrassShaped(grass,fixed));
        plan.naturalSupport().validate(plan);
        return new SectorCityAP3.Transition(plan,spatialMap(owners),Map.copyOf(levels),Map.copyOf(next),Set.copyOf(open),site.corners(original.afterConnections()));
    }
}
