package com.quin.catancraft.world;

import com.google.gson.*;
import com.quin.catancraft.map.MapAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Unapproved P1 input. Every public access requires an actual isolated GameTestServer.
 * No production registry, command, site approval or price catalog references this class.
 */
public final class SectorP1Fixture {
    public static final String PIN="701a70af260a34ac16b439e18dc15a24668bf212352b86ff199b5386a968dcaa";
    private static JsonObject cached;
    private SectorP1Fixture(){}
    private static synchronized JsonObject data(ServerLevel level)throws IOException {
        if(level==null||!(level.getServer() instanceof GameTestServer))throw new IOException("P1 is unapproved: isolated GameTestServer fixture only");
        if(cached==null) {
            byte[] raw;try(InputStream in=SectorP1Fixture.class.getResourceAsStream("/assets/catancraft/sector_fixture/p1.json.gz")){if(in==null)throw new IOException("P1 fixture unavailable");raw=in.readNBytes(500001);}
            if(raw.length>500000||!TownHallRevisions.sha(raw).equals(PIN))throw new IOException("P1 fixture hash mismatch");
            try(var gz=new GZIPInputStream(new ByteArrayInputStream(raw))){byte[] json=gz.readNBytes(10000001);if(json.length>10000000)throw new IOException("P1 fixture oversized");cached=JsonParser.parseString(new String(json,StandardCharsets.UTF_8)).getAsJsonObject();}
            if(cached.get("schemaVersion").getAsInt()!=1||cached.get("approved").getAsBoolean()||!cached.get("testOnly").getAsBoolean())throw new IOException("P1 fixture approval invariant violated");
        }
        return cached;
    }
    public static JsonObject metadata(ServerLevel level)throws IOException{return data(level).deepCopy();}
    private static JsonObject variant(JsonObject d,String variant)throws IOException {for(JsonElement e:d.getAsJsonArray("variants"))if(e.getAsJsonObject().get("variant").getAsString().equals(variant))return e.getAsJsonObject();throw new IOException("Unknown P1 variant");}
    private static BlockPos world(MapAnchor a,int x,int y,int z)throws IOException {
        ProducerExteriorR2.validateRotation(a);int sign=a.rotation()==0?1:-1;return new BlockPos(a.x()+sign*(x-89),a.y()+y,a.z()+sign*(z-6));
    }
    private static BlockState rotated(BlockState state,MapAnchor a){return state.rotate(a.rotation()==0?Rotation.NONE:Rotation.CLOCKWISE_180);}
    public static SectorTransaction.Plan plan(ServerLevel level,int sourceLevel,MapAnchor anchor)throws IOException {
        JsonObject d=data(level);ProducerExteriorR2.validateRotation(anchor);if(sourceLevel<0||sourceLevel>1)throw new IOException("P1 fixture has only basic and reinforced endpoints");
        BlockState[] fresh=ProducerExteriorR2.decode(d.getAsJsonObject("readContext"),179*10*14);
        BlockState[] basic=ProducerExteriorR2.decode(variant(d,"basic").getAsJsonObject("target"),179*9*11);
        BlockState[] target=ProducerExteriorR2.decode(variant(d,sourceLevel==0?"basic":"reinforced").getAsJsonObject("target"),179*9*11);
        Map<BlockPos,BlockState> read=new LinkedHashMap<>();
        for(int y=-1;y<=8;y++)for(int z=0;z<14;z++)for(int x=0;x<179;x++)read.put(world(anchor,x,y,z),rotated(fresh[((y+1)*14+z)*179+x],anchor));
        for(JsonElement e:d.getAsJsonArray("primaryRoadReads")) {
            JsonObject r=e.getAsJsonObject();JsonArray p=r.getAsJsonArray("localXYZ");BlockPos pos=world(anchor,p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt());BlockState state=rotated(ProducerExteriorR2.parseExact(r.get("state").getAsString()),anchor);
            BlockState previous=read.putIfAbsent(pos,state);if(previous!=null&&!previous.equals(state))throw new IOException("Contradictory P1 road/source read");
        }
        List<SectorTransaction.Cell> cells=new ArrayList<>();
        for(int y=0;y<9;y++)for(int z=0;z<11;z++)for(int x=0;x<179;x++) {
            int i=(y*11+z)*179+x;BlockPos p=world(anchor,x,y,z);BlockState before=sourceLevel==0?read.get(p):rotated(basic[i],anchor);
            cells.add(new SectorTransaction.Cell(p,before,rotated(target[i],anchor)));read.remove(p);
        }
        List<SectorTransaction.Read> reads=new ArrayList<>();read.forEach((p,s)->reads.add(new SectorTransaction.Read(p,s)));
        return new SectorTransaction.Plan(anchor.rotation()==0?"north":"south","P1_TEST_ONLY_"+(sourceLevel==0?"fresh":"basic"),"P1_TEST_ONLY_"+(sourceLevel==0?"basic":"reinforced"),sourceLevel,sourceLevel+1,cells,reads);
    }
    public static List<SchematicPlacementService.PlanCell> coupon(ServerLevel level,String id,MapAnchor anchor)throws IOException {
        JsonObject d=data(level);ProducerExteriorR2.validateRotation(anchor);
        for(JsonElement e:d.getAsJsonArray("coupons")) {
            JsonObject row=e.getAsJsonObject();if(!row.get("id").getAsString().equals(id))continue;
            BlockState[] states=ProducerExteriorR2.decode(row.getAsJsonObject("context"),9*9*18);List<SchematicPlacementService.PlanCell> out=new ArrayList<>();int sign=anchor.rotation()==0?1:-1;
            for(int y=0;y<9;y++)for(int z=0;z<18;z++)for(int x=0;x<9;x++)out.add(new SchematicPlacementService.PlanCell(x,y,z,new BlockPos(anchor.x()+sign*x,anchor.y()+y,anchor.z()+sign*z),rotated(states[(y*18+z)*9+x],anchor)));
            return List.copyOf(out);
        }
        throw new IOException("Unknown P1 corner coupon");
    }
}
