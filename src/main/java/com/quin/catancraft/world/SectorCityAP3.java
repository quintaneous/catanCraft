package com.quin.catancraft.world;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Approved City A art contracts, not a live purchase authorization.
 * Composition retains exclusive cell ownership across coordinated seam transitions.
 * Historical receipt/connection reconciliation must pass before a service enables these plans. */
final class SectorCityAP3 {
    static final String ARCHIVE_SHA="96278c6ebfd1aef8454ebff9e51c0c56a48a273349d87928bf84e73ced039095";
    static final String RESOURCE_SHA="facc415887db9cff673296c3c40ca8a30f80832570de36de8fe4d6a6827560c2";
    private static JsonObject data;
    private static Map<BlockPos,BlockState> baseline;
    private static final Map<String,BlockState> STATES=new HashMap<>();
    private static Map<BlockPos,SectorContracts.Direction> owners;
    private SectorCityAP3(){}
    record Assembly(Map<SectorContracts.Direction,Integer> levels,Set<String> openCorners,Map<BlockPos,BlockState> states){}
    record Transition(SectorTransaction.Plan plan,Map<BlockPos,SectorContracts.Direction> writeOwners,
                      Map<SectorContracts.Direction,Integer> beforeLevels,Map<SectorContracts.Direction,Integer> afterLevels,
                      Set<String> beforeConnections,Set<String> afterConnections){}
    static synchronized JsonObject metadata()throws IOException {
        if(data==null){
            byte[] raw;try(var in=SectorCityAP3.class.getResourceAsStream("/assets/catancraft/sector_p3/city_a.json.gz")){
                if(in==null)throw new IOException("Verified P3 resource unavailable");raw=in.readNBytes(8000001);
            }
            if(raw.length>8000000||!TownHallRevisions.sha(raw).equals(RESOURCE_SHA))throw new IOException("P3 resource hash mismatch");
            try(var gz=new GZIPInputStream(new ByteArrayInputStream(raw))){byte[] bytes=gz.readNBytes(64000001);if(bytes.length>64000000)throw new IOException("P3 expanded resource too large");data=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();}
            if(!data.get("archiveSHA256").getAsString().equals(ARCHIVE_SHA)||!data.get("city").getAsString().equals("a"))throw new IOException("P3 provenance/site mismatch");
            if(data.get("schemaVersion").getAsInt()!=1)throw new IOException("Unknown P3 schema");
        }
        return data.deepCopy();
    }
    private static JsonObject source()throws IOException{if(data==null)metadata();return data;}
    private static BlockPos pos(JsonArray row){return new BlockPos(row.get(0).getAsInt(),row.get(1).getAsInt(),row.get(2).getAsInt());}
    private static BlockState state(JsonElement text)throws IOException{String value=text.getAsString();BlockState parsed=STATES.get(value);if(parsed==null){parsed=ProducerExteriorR2.parseExact(value);STATES.put(value,parsed);}return parsed;}
    private static SectorContracts.Direction side(String text)throws IOException{return SectorContracts.Direction.parse(text);}
    private static String key(SectorContracts.Direction side){return side.name().toLowerCase(Locale.ROOT);}
    private static void putExact(Map<BlockPos,BlockState> map,BlockPos p,BlockState state)throws IOException{BlockState old=map.putIfAbsent(p,state);if(old!=null&&!old.equals(state))throw new IOException("P3 conflicting baseline at "+p+": "+old+" / "+state);}
    private static synchronized void prepare()throws IOException{
        if(baseline!=null)return;Map<BlockPos,BlockState> fresh=new HashMap<>();Map<BlockPos,SectorContracts.Direction> ownership=new HashMap<>();
        for(var entry:source().getAsJsonObject("ownership").entrySet())for(var e:entry.getValue().getAsJsonArray()){
            BlockPos p=pos(e.getAsJsonArray());if(ownership.putIfAbsent(p,side(entry.getKey()))!=null)throw new IOException("P3 shared cell ownership at "+p);
        }
        for(var entry:source().getAsJsonArray("assets")){var asset=entry.getAsJsonObject();for(var e:asset.getAsJsonArray("reads")){var row=e.getAsJsonArray();putExact(fresh,pos(row),state(row.get(3)));}
            for(var e:asset.getAsJsonArray("fresh")){var row=e.getAsJsonArray();putExact(fresh,pos(row),state(row.get(3)));}}
        if(ownership.isEmpty())throw new IOException("Missing P3 ownership union");
        for(var p:ownership.keySet())if(!fresh.containsKey(p)||!fresh.get(p).isAir())throw new IOException("P3 ownership must originate in exact source air: "+p);
        baseline=Collections.unmodifiableMap(fresh);owners=Collections.unmodifiableMap(ownership);
    }
    static Map<BlockPos,SectorContracts.Direction> ownership()throws IOException{prepare();return owners;}
    static Map<SectorContracts.Direction,Integer> levels(int north,int east,int south,int west)throws IOException{
        var m=new EnumMap<SectorContracts.Direction,Integer>(SectorContracts.Direction.class);m.put(SectorContracts.Direction.NORTH,north);m.put(SectorContracts.Direction.EAST,east);m.put(SectorContracts.Direction.SOUTH,south);m.put(SectorContracts.Direction.WEST,west);validateLevels(m);return Map.copyOf(m);
    }
    private static void validateLevels(Map<SectorContracts.Direction,Integer> levels)throws IOException{
        if(levels==null||levels.size()!=4)throw new IOException("Exact four-side levels required");for(var side:SectorContracts.Direction.values())if(levels.get(side)==null||levels.get(side)<0||levels.get(side)>2)throw new IOException("Unsupported City A structural level");
    }
    private static List<JsonObject> connections(Map<SectorContracts.Direction,Integer> levels)throws IOException{
        List<JsonObject> out=new ArrayList<>();for(var e:source().getAsJsonObject("corners").getAsJsonArray("connections")){
            var row=e.getAsJsonObject();int a=levels.get(side(row.get("transactionOwner").getAsString())),b=levels.get(side(row.get("neighbor").getAsString()));
            if(a>0&&b>0&&row.get("ownerTier").getAsString().equals(a==1?"basic":"reinforced")&&row.get("neighborTier").getAsString().equals(b==1?"basic":"reinforced"))out.add(row);
        }return out;
    }
    static Assembly assembly(Map<SectorContracts.Direction,Integer> levels,Set<String> requestedOpen)throws IOException{
        prepare();validateLevels(levels);if(requestedOpen==null)throw new IOException("Explicit corner state required");Map<BlockPos,BlockState> states=new HashMap<>(baseline);
        for(var side:SectorContracts.Direction.values())if(levels.get(side)>0)for(var e:source().getAsJsonObject("ownership").getAsJsonArray(key(side))){var row=e.getAsJsonArray();states.put(pos(row),state(row.get(levels.get(side)==1?3:4)));}
        Set<String> applied=new HashSet<>();for(var connection:connections(levels)){
            String corner=connection.get("corner").getAsString();if(!requestedOpen.contains(corner))continue;if(!applied.add(corner))throw new IOException("Duplicate P3 tier-pair connection");
            int count=0;for(var e:connection.getAsJsonArray("changes")){var row=e.getAsJsonObject();BlockPos p=pos(row.getAsJsonArray("worldXYZ"));if(owners.get(p)!=side(row.get("cellOwner").getAsString())||!Objects.equals(states.get(p),state(row.get("before"))))throw new IOException("P3 corner source/owner mismatch "+p);BlockState after=state(row.get("after"));if(!after.isAir())throw new IOException("Unsupported P3 seam overlay");states.put(p,after);count++;}
            if(count!=12)throw new IOException("P3 connection must contain both six-cell terminals");
        }
        if(!applied.equals(requestedOpen))throw new IOException("Cannot open a corner without both supported neighbors");
        return new Assembly(Map.copyOf(levels),Set.copyOf(applied),Collections.unmodifiableMap(states));
    }
    static Set<String> availableConnections(Map<SectorContracts.Direction,Integer> levels)throws IOException{validateLevels(levels);Set<String> result=new HashSet<>();for(var c:connections(levels))result.add(c.get("corner").getAsString());return Set.copyOf(result);}
    /** Pure geometry; creating this object does not grant ownership, charge or write a block. */
    static Transition transition(SectorContracts.Direction purchased,Map<SectorContracts.Direction,Integer> levels,Set<String> openBefore)throws IOException{
        validateLevels(levels);if(purchased==null||levels.get(purchased)>=2)throw new IOException("No supported next structural tier");
        var afterLevels=new EnumMap<SectorContracts.Direction,Integer>(SectorContracts.Direction.class);afterLevels.putAll(levels);afterLevels.put(purchased,levels.get(purchased)+1);
        var before=assembly(levels,openBefore);Set<String> openAfter=new HashSet<>(openBefore);
        // Existing closed unrelated interfaces stay closed; only this purchase's incident seams open.
        for(var c:connections(afterLevels))if(side(c.get("transactionOwner").getAsString())==purchased||side(c.get("neighbor").getAsString())==purchased)openAfter.add(c.get("corner").getAsString());
        var after=assembly(afterLevels,openAfter);List<SectorTransaction.Cell> cells=new ArrayList<>();List<SectorTransaction.Read> reads=new ArrayList<>();Map<BlockPos,SectorContracts.Direction> changedOwners=new HashMap<>();
        var points=new ArrayList<>(before.states().keySet());points.sort(Comparator.comparingLong(BlockPos::asLong));
        for(BlockPos p:points){BlockState a=before.states().get(p),b=after.states().get(p);if(!a.equals(b)){var owner=owners.get(p);if(owner==null)throw new IOException("P3 transition changes unowned context "+p);cells.add(new SectorTransaction.Cell(p,a,b));changedOwners.put(p,owner);}else reads.add(new SectorTransaction.Read(p,a));}
        String sourceRevision=revision(purchased,levels.get(purchased)),targetRevision=revision(purchased,afterLevels.get(purchased));
        var plan=new SectorTransaction.Plan(key(purchased),sourceRevision,targetRevision,levels.get(purchased),afterLevels.get(purchased),List.copyOf(cells),List.copyOf(reads));
        return new Transition(plan,Collections.unmodifiableMap(changedOwners),before.levels(),after.levels(),before.openCorners(),after.openCorners());
    }
    static String revision(SectorContracts.Direction side,int level)throws IOException{
        if(level==0)return "CITY_A_P3_FRESH_"+ARCHIVE_SHA;for(var e:source().getAsJsonArray("assets")){var a=e.getAsJsonObject();if(a.get("side").getAsString().equals(key(side))&&a.get("variant").getAsString().equals(level==1?"basic":"reinforced"))return "CITY_A_"+side+"_"+level+"_"+a.get("schematicSHA256").getAsString();}throw new IOException("P3 revision unavailable");
    }
}
