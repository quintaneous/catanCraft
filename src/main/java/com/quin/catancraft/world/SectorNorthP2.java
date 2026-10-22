package com.quin.catancraft.world;

import com.google.gson.*;
import com.quin.catancraft.economy.EconomyCost;
import com.quin.catancraft.map.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import net.minecraft.nbt.CompoundTag;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Immutable City A north P2. Approval covers the isolated fresh-world prototype only.
 * No rotations, other sites, cap-opening transitions or default prices are inferred. */
public final class SectorNorthP2 {
    public static final String SOURCE_SHA256="039ff60335cf3d80f537adb689f11a833e3a15b7a2fcf54aeb6490bb2ae0b996";
    public static final String PIN="685f117ad9ad12c95964b2393e94517f9647b2217028a12aee1fac5136a5a71b";
    public static final BlockPos CANONICAL_ANCHOR=new BlockPos(-750,81,-838);
    public static final String FRESH="P2_A_NORTH_FRESH_"+SOURCE_SHA256;
    public static final String BASIC="P2_A_NORTH_BASIC_ccc09c654b399b8503207212d90cc6f16a6924cfdb11dea0142aa1cfcec60157";
    private static JsonObject cached;
    private record PlanKey(int level,BlockPos anchor){}
    private static final Map<PlanKey,SectorTransaction.Plan> PLANS=new HashMap<>();
    private SectorNorthP2(){}
    private static synchronized JsonObject data()throws IOException {
        if(cached==null){
            byte[] raw;try(InputStream in=SectorNorthP2.class.getResourceAsStream("/assets/catancraft/sector_p2/north.json.gz")){if(in==null)throw new IOException("Pinned P2 data unavailable");raw=in.readNBytes(2000001);}
            if(raw.length>2000000||!TownHallRevisions.sha(raw).equals(PIN))throw new IOException("P2 pinned data hash mismatch");
            try(var gz=new GZIPInputStream(new ByteArrayInputStream(raw))){byte[] json=gz.readNBytes(16000001);if(json.length>16000000)throw new IOException("P2 data oversized");cached=JsonParser.parseString(new String(json,StandardCharsets.UTF_8)).getAsJsonObject();}
            if(cached.get("schemaVersion").getAsInt()!=1)throw new IOException("Unknown P2 data schema");
        }return cached;
    }
    static JsonObject metadata()throws IOException{return data().deepCopy();}
    private static JsonObject variant(JsonObject d,String name)throws IOException {for(JsonElement e:d.getAsJsonArray("variants")){JsonObject row=e.getAsJsonObject();if(row.get("variant").getAsString().equals(name))return row;}throw new IOException("Missing P2 variant");}
    private static BlockPos at(BlockPos anchor,int x,int y,int z){return anchor.offset(x-89,y,z-15);}
    private static void read(Map<BlockPos,BlockState> reads,BlockPos p,BlockState s)throws IOException {BlockState old=reads.putIfAbsent(p,s);if(old!=null&&!old.equals(s))throw new IOException("Conflicting pinned P2 source/support/road state at "+p);}
    public static SectorTransaction.Plan plan(ServerLevel level,int sourceLevel)throws IOException {return makePlan(sourceLevel,CANONICAL_ANCHOR);}
    static SectorTransaction.Plan fixturePlan(ServerLevel level,int sourceLevel,BlockPos anchor)throws IOException {if(!(level.getServer() instanceof GameTestServer))throw new IOException("Translated P2 plans require GameTestServer");return makePlan(sourceLevel,anchor);}
    private static synchronized SectorTransaction.Plan makePlan(int sourceLevel,BlockPos anchor)throws IOException {
        PlanKey key=new PlanKey(sourceLevel,anchor.immutable());SectorTransaction.Plan known=PLANS.get(key);if(known!=null)return known;
        if(sourceLevel<0||sourceLevel>1)throw new IOException("P2 north supports only fresh/basic to basic/reinforced");JsonObject d=data();
        JsonObject readContext=d.getAsJsonObject("readContext");JsonArray size=readContext.getAsJsonArray("sizeXYZ");int depth=size.get(2).getAsInt();if(size.get(0).getAsInt()!=179||size.get(1).getAsInt()!=10||(depth!=22&&depth!=23))throw new IOException("Unexpected bounded P2 source context");
        BlockState[] context=ProducerExteriorR2.decode(readContext,179*10*depth);
        JsonObject basicRow=variant(d,"basic"),targetRow=variant(d,sourceLevel==0?"basic":"reinforced");
        BlockState[] basic=ProducerExteriorR2.decode(basicRow.getAsJsonObject("target"),179*9*20),target=ProducerExteriorR2.decode(targetRow.getAsJsonObject("target"),179*9*20);
        Map<BlockPos,BlockState> reads=new LinkedHashMap<>();
        for(int y=-1;y<=8;y++)for(int z=0;z<depth;z++)for(int x=0;x<179;x++)read(reads,at(anchor,x,y,z),context[((y+1)*depth+z)*179+x]);
        for(JsonElement e:d.getAsJsonArray("primaryRoadReads")){JsonObject r=e.getAsJsonObject();JsonArray xyz=r.getAsJsonArray("localXYZ");read(reads,at(anchor,xyz.get(0).getAsInt(),xyz.get(1).getAsInt(),xyz.get(2).getAsInt()),ProducerExteriorR2.parseExact(r.get("state").getAsString()));}
        for(JsonElement e:targetRow.getAsJsonArray("supports")){JsonObject r=e.getAsJsonObject();JsonArray xyz=r.getAsJsonArray("localXYZ");read(reads,at(anchor,xyz.get(0).getAsInt(),xyz.get(1).getAsInt(),xyz.get(2).getAsInt()),ProducerExteriorR2.parseExact(r.get("state").getAsString()));}
        List<SectorTransaction.Cell> cells=new ArrayList<>();
        for(int y=0;y<9;y++)for(int z=0;z<20;z++)for(int x=0;x<179;x++){
            int i=(y*20+z)*179+x;BlockPos p=at(anchor,x,y,z);BlockState before=sourceLevel==0?reads.get(p):basic[i],after=target[i];
            if(sourceLevel==0&&!after.isAir()&&!before.isAir())throw new IOException("P2 fresh placement is not exact source air");
            if(!before.equals(after)){if(sourceLevel==0&&after.isAir())throw new IOException("P2 fresh placement cannot erase air");cells.add(new SectorTransaction.Cell(p,before,after));reads.remove(p);}else reads.put(p,before);
        }
        if(cells.size()!=(sourceLevel==0?5013:4539))throw new IOException("P2 exact sparse change count mismatch");
        List<SectorTransaction.Read> protectedReads=new ArrayList<>();reads.forEach((p,s)->protectedReads.add(new SectorTransaction.Read(p,s)));
        String reinforced="P2_A_NORTH_REINFORCED_"+variant(d,"reinforced").get("schematicSHA256").getAsString();
        SectorTransaction.Plan strict=new SectorTransaction.Plan("north",sourceLevel==0?FRESH:BASIC,sourceLevel==0?BASIC:reinforced,sourceLevel,sourceLevel+1,List.copyOf(cells),List.copyOf(protectedReads));
        Map<BlockPos,BlockState> before=SectorNaturalSupportPolicy.geometry(strict,false),after=SectorNaturalSupportPolicy.geometry(strict,true);Set<BlockPos> grass=new HashSet<>();
        for(var r:strict.reads())if(r.state().equals(net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState())&&(SectorNaturalSupportPolicy.blocksGrass(before.get(r.pos().above()),r.pos().above())||SectorNaturalSupportPolicy.blocksGrass(after.get(r.pos().above()),r.pos().above())))grass.add(r.pos());
        var policy=SectorNaturalSupportPolicy.coveredGrassShaped(grass,Map.of());String suffix="_"+SectorNaturalSupportPolicy.SHAPED_VERSION;
        SectorTransaction.Plan result=new SectorTransaction.Plan(strict.sector(),strict.sourceRevision()+suffix,strict.targetRevision()+suffix,strict.sourceLevel(),strict.targetLevel(),strict.cells(),strict.reads(),policy);
        policy.validate(result);PLANS.put(key,result);return result;
    }
    /** Narrow owner-approved local extension; never expands an entire city reservation. */
    public static boolean approvedExtensionWrite(String city,SectorTransaction.Plan plan,BlockPos pos)throws IOException {
        if(!"a".equals(city)||plan==null||pos==null||pos.getZ()>=-846||plan.sourceLevel()<0||plan.sourceLevel()>1)return false;
        SectorTransaction.Plan canonical=makePlan(plan.sourceLevel(),CANONICAL_ANCHOR);if(!canonical.equals(plan))return false;
        for(var cell:canonical.cells())if(cell.pos().equals(pos)&&!cell.before().equals(cell.after()))return true;return false;
    }
    /** No legacy socket adoption or credit conversion. Owned, pending or unexplained receipts refuse. */
    public static void requireNoLegacyOwnership(SectorService.Context c,SectorTransaction.Plan plan)throws IOException {
        if(!plan.targetRevision().startsWith("P2_A_NORTH_")&&!plan.sourceRevision().startsWith("P2_A_NORTH_"))return;
        if(!c.city().equals("a"))throw new IOException("P2 legacy reconciliation is scoped to City A");
        Set<String> sockets=new HashSet<>();for(JsonElement e:data().getAsJsonObject("runtimeAudit").getAsJsonArray("legacyWriteOverlaps"))sockets.add(e.getAsJsonObject().get("socket").getAsString());
        Map<String,Set<String>> refunded=new HashMap<>();Set<String> histories=new HashSet<>();Path dir=c.root().resolve("data");
        if(Files.isSymbolicLink(dir))throw new IOException("P2 legacy history directory symlink refused");
        if(Files.isDirectory(dir))try(var paths=Files.newDirectoryStream(dir,"catancraft_defense_a_*_r4a_r6_1.dat")){
            int count=0;for(Path file:paths){if(++count>4096)throw new IOException("Too many legacy defense histories");CompoundTag journal=ProducerJournal.read(file),identity=journal.getCompound("identity");String socket=identity.getString("socket");if(!sockets.contains(socket))continue;
                if(journal.getInt("schema")!=1||!journal.hasUUID("id")||!identity.getString("world").equals(c.root().toString())||!identity.getString("city").equals("a")||!identity.getString("dimension").equals(c.server().overworld().dimension().location().toString())||!identity.hasUUID("owner")||!identity.getUUID("owner").equals(c.owner()))throw new IOException("Foreign/malformed overlapping legacy defense history: "+socket);
                histories.add(socket);if(!journal.getString("phase").equals("ROLLED_BACK"))throw new IOException("Overlapping retained legacy defense requires explicit recovery; no P2 adoption: "+socket);
                refunded.computeIfAbsent(socket,k->new HashSet<>()).add(journal.getUUID("id")+":refunded");
            }
        }
        for(String socket:sockets){String marker=c.data().placedMapAsset(DefenseService.ownedKey("a",socket)),receipt=c.data().placedMapAsset("defense_receipt:a:"+socket);
            if(marker!=null&&!marker.isEmpty()&&!marker.equals("defense_unowned"))throw new IOException("Owned/unknown legacy defense overlaps P2: "+socket);
            if(receipt!=null&&!receipt.isEmpty()){if(!refunded.getOrDefault(socket,Set.of()).contains(receipt))throw new IOException("Unproven legacy defense receipt overlaps P2: "+socket);}
            else if(histories.contains(socket))throw new IOException("Overlapping legacy defense history lost its receipt: "+socket);
        }
    }
    public static SectorContracts.Contract contract(SectorService.Context c,SectorContracts.Direction side,int sourceLevel,EconomyCost approvedAggregateCost)throws IOException {
        if(!c.approval().equals(SectorService.PRODUCTION)||!FreshDemoAuthorization.authorize(c.server()).equals(c.root())||!FreshDemoAuthorization.worldId(c.server(),c.root()).equals(c.worldId()))throw new IOException("P2 requires its authorized fresh demonstration world");
        var def=MapDefinitionManager.territory("a");MapAnchor a=def==null?null:def.settlementAnchor();MapAnchor center=def==null?null:def.cityCenter();
        if(def==null||!def.dimension().equals(c.server().overworld().dimension().location().toString())||a==null||a.x()!=-750||a.y()!=81||a.z()!=-833||a.rotation()!=0||center==null||center.x()!=-750||center.y()!=81||center.z()!=-750||center.rotation()!=0)throw new IOException("P2 City A canonical map/site changed");
        // The approved artwork remains immutable. Native delayed checks found grass support
        // decays under opaque walls; do not enable a contract that fails ordinary later recovery.
        throw new IOException("P2 north is not runtime-ready: natural grass-to-dirt support handling remains unresolved (DEF-03)");
    }
    static SectorContracts.Contract fixtureContract(SectorService.Context c,SectorContracts.Direction side,int sourceLevel,EconomyCost cost,BlockPos anchor)throws IOException {
        if(!(c.server() instanceof GameTestServer)||!c.approval().equals(SectorService.FIXTURE))throw new IOException("P2 fixture requires isolated GameTestServer");return contractAt(c,side,sourceLevel,cost,anchor);
    }
    private static SectorContracts.Contract contractAt(SectorService.Context c,SectorContracts.Direction side,int sourceLevel,EconomyCost cost,BlockPos anchor)throws IOException {
        if(!c.city().equals("a")||side!=SectorContracts.Direction.NORTH||cost==null)throw new IOException("Only explicitly priced City A NORTH P2 is approved for isolated fresh tests");
        Map<SectorContracts.Direction,SectorContracts.Neighbor> neighbors=new EnumMap<>(SectorContracts.Direction.class);
        for(var d:side.adjacent()){var state=SectorService.savedState(c.data(),c.city(),d);if(state.level()!=0||!state.receipt().isEmpty())throw new IOException("P2 north neighbor interface requires absent east/west; P3 seam transitions unavailable");neighbors.put(d,new SectorContracts.Neighbor(0,""));}
        var plan=makePlan(sourceLevel,anchor);requireNoLegacyOwnership(c,plan);Map<SectorContracts.Corner,SectorContracts.Direction> corners=Map.of(SectorContracts.Corner.NORTH_WEST,side,SectorContracts.Corner.NORTH_EAST,side);
        Set<BlockPos> roads=new HashSet<>();for(JsonElement e:data().getAsJsonArray("primaryRoadReads")){JsonArray xyz=e.getAsJsonObject().getAsJsonArray("localXYZ");roads.add(at(anchor,xyz.get(0).getAsInt(),xyz.get(1).getAsInt(),xyz.get(2).getAsInt()));}
        return new SectorContracts.Contract(c.root().toString(),c.city(),side,plan,corners,neighbors,roads,cost);
    }
}
