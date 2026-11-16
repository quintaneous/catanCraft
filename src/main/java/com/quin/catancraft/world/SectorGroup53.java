package com.quin.catancraft.world;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.nbt.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Qualified city composite lineage: one new side price/level with coordinated exclusive corner writes.
 * Schema 2 never adopts schema-1 City A or unrelated paid worlds; journals remain immutable. */
final class SectorGroup53 {
    private SectorGroup53(){}
    private static final Map<SectorContracts.Contract,Map<BlockPos,SectorContracts.Direction>> VALIDATED=Collections.synchronizedMap(new IdentityHashMap<>());
    private record Cached(byte[] bytes,CompoundTag journal){}
    private static final LinkedHashMap<Path,Cached> CACHE=new LinkedHashMap<>(16,.75f,true){protected boolean removeEldestEntry(Map.Entry<Path,Cached> e){return size()>12;}};
    private static Set<String> legacySockets;
    private static final Map<CompoundTag,Boolean> VERIFIED_HISTORY=new IdentityHashMap<>();
    private static final Map<CompoundTag,SectorTransaction.Plan> VERIFIED_OWN=new IdentityHashMap<>();
    private static String key(SectorService.Context c,String field){return "city_group_v1:"+c.city()+":"+field;}
    private static String value(SectorService.Context c,String field){String v=c.data().placedMapAsset(key(c,field));return v==null||v.equals("none")?"":v;}
    private static synchronized CompoundTag history(Path file)throws IOException {
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>8*1024*1024+40)throw new IOException("Invalid bounded city group history");
        byte[] before=sealedBytes(file);Cached old=CACHE.get(file);if(old!=null&&Arrays.equals(old.bytes(),before))return old.journal();
        CompoundTag j=ProducerJournal.read(before);byte[] after=sealedBytes(file);if(!Arrays.equals(before,after))throw new IOException("City history changed while verifying");CACHE.put(file,new Cached(after,j));return j;
    }
    private static byte[] sealedBytes(Path file)throws IOException {
        try(var in=Files.newByteChannel(file,Set.of(StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS))){
            if(in.size()>8*1024*1024+40)throw new IOException("Oversized qualified city history");var out=new ByteArrayOutputStream();var buffer=java.nio.ByteBuffer.allocate(16384);int total=0;
            for(int n;(n=in.read(buffer))!=-1;){if(n==0)continue;if((total+=n)>8*1024*1024+40)throw new IOException("Growing qualified city history");out.write(buffer.array(),0,n);buffer.clear();}return out.toByteArray();
        }
    }
    private static Path path(SectorService.Context c,String name)throws IOException {
        if(!name.matches("catancraft_sector_"+c.city()+"_(north|east|south|west)_[0-9a-f-]{36}_v1\\.dat"))throw new IOException("Invalid retained city group filename");
        Path dir=c.root().resolve("data");if(Files.isSymbolicLink(dir))throw new IOException("City group history directory symlink");return dir.resolve(name);
    }
    private static void scope(SectorService.Context c,BlockPos translation)throws IOException {
        if(c.city().equals("a"))throw new IOException("City A retains its original schema-1 wall provider");
        SectorSites53.site(c.city());
        if(c.approval().equals(SectorService.FIXTURE)){
            if(!(c.server() instanceof GameTestServer))throw new IOException("Translated wall site requires GameTestServer");
        }else{
            if(!c.approval().equals(SectorService.PRODUCTION)||!translation.equals(BlockPos.ZERO)
                    ||!FreshDemoAuthorization.authorize(c.server()).equals(c.root()))throw new IOException("Wall site requires its bound isolated world");
            var def=com.quin.catancraft.map.MapDefinitionManager.territory(c.city());
            if(def==null||!def.dimension().equals(c.server().overworld().dimension().location().toString()))throw new IOException("Qualified wall dimension changed");
            SectorSites53.canonical(def);
        }
    }
    private static CompoundTag states(SectorService.Context c)throws IOException {CompoundTag out=new CompoundTag();for(var d:SectorContracts.Direction.values()){var state=SectorService.savedState(c.data(),c.city(),d);CompoundTag row=new CompoundTag();row.putInt("level",state.level());row.putString("receipt",state.receipt());row.putString("revision",state.revision());out.put(d.name(),row);}return out;}
    private static Map<SectorContracts.Direction,Integer> levels(String city,CompoundTag states)throws IOException {
        if(!states.getAllKeys().equals(Set.of("NORTH","EAST","SOUTH","WEST")))throw new IOException("Missing exact city sector state vector");
        var out=new EnumMap<SectorContracts.Direction,Integer>(SectorContracts.Direction.class);
        for(var d:SectorContracts.Direction.values()){CompoundTag row=states.getCompound(d.name());int level=row.getInt("level");if(!row.getAllKeys().equals(Set.of("level","receipt","revision"))||!row.contains("level",Tag.TAG_INT)||!row.contains("receipt",Tag.TAG_STRING)||!row.contains("revision",Tag.TAG_STRING)||level<0||level>2||level==0&&(!row.getString("receipt").isEmpty()||!row.getString("revision").isEmpty())||level>0&&(row.getString("receipt").isBlank()||!row.getString("revision").equals(revision(city,d,level))))throw new IOException("Invalid current qualified city group state");out.put(d,level);}return out;
    }
    private static String revision(String city,SectorContracts.Direction side,int level)throws IOException{return SectorSites53.revision(SectorSites53.site(city),side,level);}
    private static ListTag strings(Set<String> set){ListTag out=new ListTag();set.stream().sorted().forEach(s->out.add(StringTag.valueOf(s)));return out;}
    private static Set<String> corners(CompoundTag group,String field)throws IOException {
        if(!group.contains(field,Tag.TAG_LIST))throw new IOException("Missing explicit connection state");
        ListTag raw=(ListTag)group.get(field),list=group.getList(field,Tag.TAG_STRING);
        if(raw.size()!=list.size())throw new IOException("Unknown connection state element type");
        Set<String> out=new HashSet<>();for(Tag value:list)
            if(!Set.of("NW","NE","SW","SE").contains(value.getAsString())||!out.add(value.getAsString()))throw new IOException("Invalid repeated connection state");
        if(!strings(out).equals(list))throw new IOException("Noncanonical connection state");return Set.copyOf(out);
    }
    /** Full schema-2 metadata validation is shared with the natural-support lineage reader. */
    static CompoundTag supportHeader(CompoundTag group)throws IOException {
        Set<String> expected=new HashSet<>(Set.of("schema","parentFile","parentCityReceipt","archiveSHA256","resourceSHA256","translation","beforeStates","beforeOpenCorners","afterOpenCorners","siteCity","siteRotation","siteCenter","mapSHA256","qualificationSHA256"));
        if(group.contains("parentId"))expected.add("parentId");
        if(!group.getAllKeys().equals(expected)||!group.contains("schema",Tag.TAG_INT)||group.getInt("schema")!=2
                ||!group.contains("translation",Tag.TAG_LONG)||!group.contains("siteRotation",Tag.TAG_INT)||!group.contains("siteCenter",Tag.TAG_LONG))throw new IOException("Unknown all-city wall group schema");
        for(String key:List.of("parentFile","parentCityReceipt","archiveSHA256","resourceSHA256","siteCity","mapSHA256","qualificationSHA256"))
            if(!group.contains(key,Tag.TAG_STRING))throw new IOException("Malformed wall group pin: "+key);
        if(!SectorCityAP3.ARCHIVE_SHA.equals(group.getString("archiveSHA256"))||!SectorCityAP3.RESOURCE_SHA.equals(group.getString("resourceSHA256"))
                ||!ShopSites51.MAP_SHA256.equals(group.getString("mapSHA256"))||!SectorSites53.RESOURCE_SHA.equals(group.getString("qualificationSHA256")))throw new IOException("Changed all-city wall asset/source pins");
        String city=group.getString("siteCity");if(!city.matches("[b-p]"))throw new IOException("Schema-2 wall city is not qualified; City A keeps schema 1");
        var site=SectorSites53.site(city);
        if(group.getInt("siteRotation")!=site.rotation()||group.getLong("siteCenter")!=site.center().asLong())throw new IOException("Changed all-city wall transform");
        boolean parent=!group.getString("parentFile").isEmpty();
        if(parent){
            if(!group.hasUUID("parentId")||!group.getString("parentFile").matches("catancraft_sector_"+city+"_(north|east|south|west)_[0-9a-f-]{36}_v1\\.dat")
                    ||!group.getString("parentCityReceipt").equals(group.getUUID("parentId")+":paid"))throw new IOException("Invalid exact-city group parent pointer");
        }else if(group.contains("parentId")||!group.getString("parentCityReceipt").isEmpty())throw new IOException("Fresh wall group has unexplained prior receipt");
        if(!group.contains("beforeStates",Tag.TAG_COMPOUND))throw new IOException("Missing exact wall group state vector");
        levels(city,group.getCompound("beforeStates"));
        if(!parent)for(String side:group.getCompound("beforeStates").getAllKeys())if(group.getCompound("beforeStates").getCompound(side).getInt("level")!=0)throw new IOException("Purchased wall lacks parent proof");
        corners(group,"beforeOpenCorners");corners(group,"afterOpenCorners");return group;
    }
    private static BlockPos translation(CompoundTag group)throws IOException {return BlockPos.of(supportHeader(group).getLong("translation"));}
    static SectorTransaction.Plan plan(String city,SectorContracts.Direction side,Map<SectorContracts.Direction,Integer> levels,Set<String> open,BlockPos delta)throws IOException {
        var original=SectorSites53.transition(SectorSites53.site(city),side,levels,open).plan();
        if(delta.equals(BlockPos.ZERO))return original;
        List<SectorTransaction.Cell> cells=new ArrayList<>();List<SectorTransaction.Read> reads=new ArrayList<>();
        for(var row:original.cells())cells.add(new SectorTransaction.Cell(row.pos().offset(delta),row.before(),row.after()));
        for(var row:original.reads())reads.add(new SectorTransaction.Read(row.pos().offset(delta),row.state()));
        Set<BlockPos> grass=new HashSet<>();original.naturalSupport().grassSupports().forEach(p->grass.add(p.offset(delta)));
        Map<BlockPos,net.minecraft.world.level.block.state.BlockState> fixed=new HashMap<>();original.naturalSupport().fixedCoverGrass().forEach((p,state)->fixed.put(p.offset(delta),state));
        return new SectorTransaction.Plan(original.sector(),original.sourceRevision(),original.targetRevision(),original.sourceLevel(),original.targetLevel(),List.copyOf(cells),List.copyOf(reads),SectorNaturalSupportPolicy.coveredGrassShaped(grass,fixed));
    }
    private static SectorCityAP3.Transition transition(String city,SectorContracts.Direction side,Map<SectorContracts.Direction,Integer> levels,Set<String> open)throws IOException {
        return SectorSites53.transition(SectorSites53.site(city),side,levels,open);
    }
    private static Map<SectorContracts.Corner,SectorContracts.Direction> cornerOwners() {
        return Map.of(SectorContracts.Corner.NORTH_WEST,SectorContracts.Direction.NORTH,SectorContracts.Corner.NORTH_EAST,SectorContracts.Direction.NORTH,
                SectorContracts.Corner.SOUTH_WEST,SectorContracts.Direction.SOUTH,SectorContracts.Corner.SOUTH_EAST,SectorContracts.Direction.SOUTH);
    }
    private static Set<BlockPos> roads(String city,BlockPos delta)throws IOException {
        Set<BlockPos> roads=new HashSet<>();for(BlockPos p:SectorSites53.site(city).protectedRoads())roads.add(p.offset(delta));return Set.copyOf(roads);
    }
    static SectorContracts.Contract fixtureContract(SectorService.Context c,SectorContracts.Direction side,int sourceLevel,BlockPos delta)throws IOException {
        if(!c.approval().equals(SectorService.FIXTURE))throw new IOException("Fixture entrypoint refused");return contract(c,side,sourceLevel,delta);
    }
    static SectorContracts.Contract resolve(SectorService.Context c,SectorContracts.Direction side,int sourceLevel)throws IOException {
        // Production identity is mandatory; fixture contexts never select the player-facing catalog.
        if(!c.approval().equals(SectorService.PRODUCTION))throw new IOException("qualified city P3 production catalog requires bound fresh-demo authorization");
        return contract(c,side,sourceLevel,BlockPos.ZERO);
    }
    private static SectorContracts.Contract contract(SectorService.Context c,SectorContracts.Direction side,int sourceLevel,BlockPos delta)throws IOException {
        scope(c,delta);requireNoLegacy(c);CompoundTag before=states(c);var levels=levels(c.city(),before);if(levels.get(side)!=sourceLevel)throw new IOException("Quoted source level changed");
        String parentFile=value(c,"operation"),parentReceipt=value(c,"receipt");Set<String> open;CompoundTag parent=null;
        if(parentFile.isEmpty()){
            if(!parentReceipt.isEmpty())throw new IOException("Orphan city group receipt");for(var d:SectorContracts.Direction.values()){var row=before.getCompound(d.name());if(row.getInt("level")!=0||!row.getString("receipt").isEmpty()||!row.getString("revision").isEmpty())throw new IOException("Existing paid non-group sectors cannot be adopted by a fresh qualified city group");}open=Set.of();
        }else{parent=history(path(c,parentFile));validateLineage(c,parentFile,parent,delta);if(!parentReceipt.equals(parent.getUUID("id")+":paid")||!afterStates(parent).equals(before))throw new IOException("Current city ledger does not match its retained group head");open=corners(parent.getCompound("identity").getCompound("cityGroup"),"afterOpenCorners");}
        var transition=transition(c.city(),side,levels,open);var plan=plan(c.city(),side,levels,open,delta);CompoundTag group=new CompoundTag();group.putInt("schema",2);var site=SectorSites53.site(c.city());group.putString("siteCity",c.city());group.putInt("siteRotation",site.rotation());group.putLong("siteCenter",site.center().asLong());group.putString("mapSHA256",ShopSites51.MAP_SHA256);group.putString("qualificationSHA256",SectorSites53.RESOURCE_SHA);group.putString("archiveSHA256",SectorCityAP3.ARCHIVE_SHA);group.putString("resourceSHA256",SectorCityAP3.RESOURCE_SHA);group.putLong("translation",delta.asLong());group.putString("parentFile",parentFile);group.putString("parentCityReceipt",parentReceipt);if(parent!=null)group.putUUID("parentId",parent.getUUID("id"));group.put("beforeStates",before);group.put("beforeOpenCorners",strings(open));group.put("afterOpenCorners",strings(transition.afterConnections()));
        Map<SectorContracts.Direction,SectorContracts.Neighbor> neighbors=new EnumMap<>(SectorContracts.Direction.class);for(var d:side.adjacent()){var row=before.getCompound(d.name());neighbors.put(d,new SectorContracts.Neighbor(row.getInt("level"),row.getString("receipt")));}
        var ownerTable=cornerOwners();
        // Every preserved road/context position remains an exact read; no generic footprint-box grant.
        Set<BlockPos> roads=roads(c.city(),delta);
        return new SectorContracts.Contract(c.root().toString(),c.city(),side,plan,ownerTable,neighbors,roads,SectorPriceCatalog.cost(side,sourceLevel),group);
    }
    private static CompoundTag afterStates(CompoundTag journal)throws IOException {
        CompoundTag id=journal.getCompound("identity"),group=id.getCompound("cityGroup"),out=group.getCompound("beforeStates").copy();levels(group.getString("siteCity"),out);var side=SectorContracts.Direction.parse(id.getString("sector"));var row=out.getCompound(side.name());row.putInt("level",journal.getCompound("spec").getInt("targetLevel"));row.putString("receipt",journal.getUUID("id")+":paid");row.putString("revision",journal.getCompound("spec").getString("targetRevision"));return out;
    }
    private static void validateHead(SectorService.Context c,String file,CompoundTag j,BlockPos delta)throws IOException {
        CompoundTag id=j.getCompound("identity"),group=id.getCompound("cityGroup");
        if(j.getInt("schema")!=1||!j.hasUUID("id")||!j.hasUUID("nation")||!j.getUUID("nation").equals(c.owner())||!j.getString("phase").equals("COMMITTED")||!id.getString("world").equals(c.root().toString())||!id.getString("worldId").equals(c.worldId())||!id.getString("city").equals(c.city())||!group.getString("siteCity").equals(c.city())||!id.getString("dimension").equals(c.server().overworld().dimension().location().toString())||!id.hasUUID("owner")||!id.getUUID("owner").equals(c.owner())||!id.getString("approval").equals(c.approval())||!id.getString("operationFile").equals(file)||!translation(group).equals(delta))throw new IOException("Foreign or noncommitted qualified city group head");
    }
    private static void validateLineage(SectorService.Context c,String name,CompoundTag head,BlockPos delta)throws IOException {
        Set<UUID> seen=new HashSet<>();CompoundTag child=head;String file=name;
        for(int depth=0;depth<9;depth++){
            validateHead(c,file,child,delta);if(!seen.add(child.getUUID("id")))throw new IOException("Cyclic qualified city group lineage");
            CompoundTag id=child.getCompound("identity"),g=id.getCompound("cityGroup");
            if(!VERIFIED_HISTORY.containsKey(child)){
                var side=SectorContracts.Direction.parse(id.getString("sector"));var expected=plan(c.city(),side,levels(c.city(),g.getCompound("beforeStates")),corners(g,"beforeOpenCorners"),delta);
                if(!SectorTransaction.describe(expected).equals(child.getCompound("spec"))||!transition(c.city(),side,levels(c.city(),g.getCompound("beforeStates")),corners(g,"beforeOpenCorners")).afterConnections().equals(corners(g,"afterOpenCorners")))throw new IOException("Retained city lineage has foreign geometry or connections");
                if(VERIFIED_HISTORY.size()>24)VERIFIED_HISTORY.clear();VERIFIED_HISTORY.put(child,Boolean.TRUE);
            }
            String parent=g.getString("parentFile");
            if(parent.isEmpty()){
                if(g.hasUUID("parentId")||!g.getString("parentCityReceipt").isEmpty()||!corners(g,"beforeOpenCorners").isEmpty())throw new IOException("Orphan initial city lineage");
                for(var d:SectorContracts.Direction.values()){var row=g.getCompound("beforeStates").getCompound(d.name());if(row.getInt("level")!=0||!row.getString("receipt").isEmpty()||!row.getString("revision").isEmpty())throw new IOException("Initial city lineage adopts unproven paid sectors");}return;
            }
            CompoundTag previous=history(path(c,parent));
            if(!previous.hasUUID("id")||!g.hasUUID("parentId")||!previous.getUUID("id").equals(g.getUUID("parentId"))||!g.getString("parentCityReceipt").equals(previous.getUUID("id")+":paid")||!afterStates(previous).equals(g.getCompound("beforeStates"))||!corners(previous.getCompound("identity").getCompound("cityGroup"),"afterOpenCorners").equals(corners(g,"beforeOpenCorners")))throw new IOException("Retained qualified city ancestor/paid receipt is missing or changed");
            child=previous;file=parent;
        }
        throw new IOException("qualified city lineage exceeds eight structural purchases");
    }
    static CompoundTag inheritedSupportProof(SectorService.Context c,CompoundTag group)throws IOException {
        supportHeader(group);if(!group.getString("siteCity").equals(c.city()))throw new IOException("Foreign group parent city");String name=group.getString("parentFile");if(name.isEmpty()){if(group.hasUUID("parentId")||!group.getString("parentCityReceipt").isEmpty())throw new IOException("Orphan group parent proof");return new CompoundTag();}
        CompoundTag j=history(path(c,name));validateLineage(c,name,j,translation(group));if(!group.hasUUID("parentId")||!group.getUUID("parentId").equals(j.getUUID("id"))||!group.getString("parentCityReceipt").equals(j.getUUID("id")+":paid")||!afterStates(j).equals(group.getCompound("beforeStates"))||!corners(j.getCompound("identity").getCompound("cityGroup"),"afterOpenCorners").equals(corners(group,"beforeOpenCorners")))throw new IOException("City group parent state/proof changed");return j.copy();
    }
    private static void validateContract(SectorService.Context c,SectorContracts.Contract contract)throws IOException {
        CompoundTag group=contract.cityGroup();BlockPos delta=translation(group);scope(c,delta);
        if(!group.getString("siteCity").equals(c.city())||!contract.city().equals(c.city()))throw new IOException("Wall group belongs to another city");
        if(VALIDATED.containsKey(contract))return;
        var source=levels(c.city(),group.getCompound("beforeStates"));var open=corners(group,"beforeOpenCorners");var t=transition(c.city(),contract.sector(),source,open);var expected=plan(c.city(),contract.sector(),source,open,delta);
        if(!expected.equals(contract.plan())||!t.afterConnections().equals(corners(group,"afterOpenCorners"))||!roads(c.city(),delta).equals(contract.roads())||!cornerOwners().equals(contract.corners()))throw new IOException("Changed canonical qualified city group geometry/connection mode");
        Map<BlockPos,SectorContracts.Direction> writes=new HashMap<>();t.writeOwners().forEach((p,d)->writes.put(p.offset(delta),d));if(VALIDATED.size()>32)VALIDATED.clear();VALIDATED.put(contract,Collections.unmodifiableMap(writes));
    }
    static boolean approvedWrite(SectorService.Context c,SectorContracts.Contract contract,BlockPos p)throws IOException {validateContract(c,contract);return VALIDATED.get(contract).containsKey(p);}
    static void requireCurrent(SectorService.Context c,SectorContracts.Contract contract,CompoundTag frozen,Path file,boolean terminalReadOnly)throws IOException {
        validateContract(c,contract);CompoundTag group=contract.cityGroup();if(!group.equals(frozen.getCompound("cityGroup")))throw new IOException("Changed frozen qualified city group metadata");requireNoLegacy(c);
        String parentFile=group.getString("parentFile"),parentReceipt=group.getString("parentCityReceipt"),cursor=value(c,"operation"),receipt=value(c,"receipt");
        String filename=file.getFileName().toString(),prefix="catancraft_sector_"+c.city()+"_"+contract.sector().name().toLowerCase(Locale.ROOT)+"_";if(!filename.startsWith(prefix)||!filename.endsWith("_v1.dat"))throw new IOException("Invalid current group operation filename");String filenameId=filename.substring(prefix.length(),filename.length()-7);try{UUID.fromString(filenameId);}catch(IllegalArgumentException e){throw new IOException("Invalid group operation filename id",e);}
        // Filename uniqueness and durable transaction identity are deliberately independent UUIDs.
        String ownId="";if(Files.exists(file,LinkOption.NOFOLLOW_LINKS)){CompoundTag own=history(file);if(!own.hasUUID("id")||!own.getCompound("identity").equals(frozen))throw new IOException("Changed current group journal identity");ownId=own.getUUID("id").toString();
            if(VERIFIED_OWN.get(own)!=contract.plan()){
                if(own.getInt("schema")!=1||!SectorTransaction.describe(contract.plan()).equals(own.getCompound("spec")))throw new IOException("Current group source specification changed");
                var cost=SectorTransaction.historicalCost(own.getCompound("cost"));if(cost.money()!=contract.cost().money()||!cost.resources().equals(contract.cost().resources()))throw new IOException("Current group historical price changed");
                if(VERIFIED_OWN.size()>24)VERIFIED_OWN.clear();VERIFIED_OWN.put(own,contract.plan());
            }
        }
        if(!(cursor.equals(parentFile)&&receipt.equals(parentReceipt))&&!(cursor.equals(filename)&&(receipt.equals(ownId+":reserved")||receipt.equals(ownId+":paid"))))throw new IOException("City group superseded; old operation cannot overwrite later sector connections");
        if(!parentFile.isEmpty()){
            CompoundTag parent=history(path(c,parentFile));validateLineage(c,parentFile,parent,translation(group));if(!group.hasUUID("parentId")||!parent.getUUID("id").equals(group.getUUID("parentId"))||!afterStates(parent).equals(group.getCompound("beforeStates")))throw new IOException("Retained city parent changed");
        }
        CompoundTag expected=group.getCompound("beforeStates"),actual=states(c);for(var side:SectorContracts.Direction.values()){
            CompoundTag before=expected.getCompound(side.name()),now=actual.getCompound(side.name());if(side!=contract.sector()){if(!before.equals(now))throw new IOException("Another sector's paid state changed during grouped placement");continue;}
            boolean source=now.equals(before),reserved=now.getInt("level")==contract.plan().sourceLevel()&&now.getString("receipt").equals(ownId+":reserved")&&now.getString("revision").equals(before.getString("revision")),target=now.getInt("level")==contract.plan().targetLevel()&&now.getString("receipt").equals(ownId+":paid")&&now.getString("revision").equals(contract.plan().targetRevision());
            if(!source&&!reserved&&!target)throw new IOException("Group purchased side ledger drift");
        }
    }
    static void verifyCurrentAssembly(SectorService.Context c)throws IOException {
        String name=value(c,"operation");if(name.isEmpty())throw new IOException("No retained current qualified city assembly proof");var file=path(c,name);CompoundTag j=history(file);var delta=translation(j.getCompound("identity").getCompound("cityGroup"));scope(c,delta);validateLineage(c,name,j,delta);
        if(!value(c,"receipt").equals(j.getUUID("id")+":paid")||!afterStates(j).equals(states(c)))throw new IOException("Current qualified city assembly ledger differs from retained proof");SectorService.verifyRecordedGroup(c,file);
    }
    private static synchronized void requireNoLegacy(SectorService.Context c)throws IOException {
        if(legacySockets==null){Set<String> sockets=new HashSet<>();for(JsonElement e:SectorCityAP3.metadata().getAsJsonObject("runtimeAudit").getAsJsonArray("legacyWriteOverlaps"))sockets.add(e.getAsJsonObject().get("socket").getAsString());legacySockets=Set.copyOf(sockets);}
        for(String socket:legacySockets){String marker=c.data().placedMapAsset(DefenseService.ownedKey(c.city(),socket)),receipt=c.data().placedMapAsset("defense_receipt:"+c.city()+":"+socket);if(marker!=null&&!marker.isBlank()&&!marker.equals("none")&&!marker.equals("defense_unowned"))throw new IOException("Legacy owned defense conflicts with new qualified city group: "+socket);if(receipt!=null&&!receipt.isBlank()&&!receipt.equals("none"))throw new IOException("Legacy defense receipt requires explicit review; no group adoption: "+socket);}
        Path dir=c.root().resolve("data");if(Files.isSymbolicLink(dir))throw new IOException("Legacy history directory symlink");if(Files.isDirectory(dir))try(var files=Files.newDirectoryStream(dir,"catancraft_defense_"+c.city()+"_*_r4a_r6_1.dat")){int count=0;for(Path p:files){if(++count>4096)throw new IOException("Too many legacy histories");var j=history(p);if(legacySockets.contains(j.getCompound("identity").getString("socket")))throw new IOException("Retained overlapping legacy history is not a fresh group source");}}
    }
}
