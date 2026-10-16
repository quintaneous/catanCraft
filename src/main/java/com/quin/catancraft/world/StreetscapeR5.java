package com.quin.catancraft.world;

import com.google.gson.*;
import com.quin.catancraft.map.MapAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Exact delivered R5 public-feature records. No house, civic, producer or terrain replacement. */
final class StreetscapeR5 {
    static final String HASH="7cbbf0c9cc38d9971e12e1b20fdba37aba630fd41d83c8819f2fdaa2d70c50cd";
    static final int READS=18766, WRITES=1661;
    record Cell(BlockPos pos,BlockState before,BlockState after) { boolean changes(){return !before.equals(after);} }
    record Plan(List<Cell> cells,List<AABB> boxes,MapAnchor center) {}
    private StreetscapeR5() {}
    static Plan plan(MapAnchor center) throws IOException {
        if(center==null || center.rotation()!=0 && center.rotation()!=180)throw new IOException("Unsupported R5 orientation");
        byte[] raw;
        try(InputStream in=StreetscapeR5.class.getResourceAsStream("/data/catancraft/r5_public/contract.b64")) {
            if(in==null)throw new IOException("Missing bundled R5 public contract");
            byte[] text=in.readNBytes(20001);if(text.length>20000)throw new IOException("Oversized R5 contract");
            try(GZIPInputStream gzip=new GZIPInputStream(new ByteArrayInputStream(Base64.getMimeDecoder().decode(text)))) {
                raw=gzip.readNBytes(100001);
            }
        }
        if(!HASH.equals(TownHallRevisions.sha(raw)))throw new IOException("R5 contract hash mismatch");
        JsonObject doc=JsonParser.parseString(new String(raw,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        List<BlockState> palette=new ArrayList<>();Rotation rot=center.rotation()==0?Rotation.NONE:Rotation.CLOCKWISE_180;
        for(JsonElement p:doc.getAsJsonArray("palette"))palette.add(parse(p.getAsString()).rotate(rot));
        Map<BlockPos,Cell> cells=new LinkedHashMap<>();
        for(JsonElement element:doc.getAsJsonArray("reads")) {
            JsonArray r=element.getAsJsonArray();int x=i(r,0),y=i(r,1),z=i(r,2),count=i(r,3);BlockState s=palette.get(i(r,4));
            for(int k=0;k<count;k++) {
                BlockPos p=world(center,x+k,y,z);
                if(cells.put(p,new Cell(p,s,s))!=null)throw new IOException("Duplicate R5 read cell");
            }
        }
        int writes=0;
        for(JsonElement element:doc.getAsJsonArray("writes")) {
            JsonArray r=element.getAsJsonArray();int x=i(r,0),y=i(r,1),z=i(r,2);
            if(protectedLocal(x,z))throw new IOException("R5 protected-volume write");
            BlockPos p=world(center,x,y,z);Cell c=cells.get(p);BlockState s=palette.get(i(r,3));
            if(c==null || c.changes() || c.before().equals(s) || c.before().hasBlockEntity() || s.hasBlockEntity()
                || !c.before().getFluidState().isEmpty() || !s.getFluidState().isEmpty())throw new IOException("Invalid R5 delta");
            cells.put(p,new Cell(p,c.before(),s));writes++;
        }
        List<AABB> boxes=new ArrayList<>();
        for(JsonElement element:doc.getAsJsonArray("groups")) {
            JsonArray g=element.getAsJsonArray(),a=g.get(1).getAsJsonArray(),b=g.get(2).getAsJsonArray();
            BlockPos p=world(center,i(a,0),i(a,1),i(a,2)),q=world(center,i(b,0),i(b,1),i(b,2));
            boxes.add(new AABB(Math.min(p.getX(),q.getX()),p.getY(),Math.min(p.getZ(),q.getZ()),
                Math.max(p.getX(),q.getX())+1,q.getY()+1,Math.max(p.getZ(),q.getZ())+1));
        }
        if(cells.size()!=READS || writes!=WRITES || boxes.size()!=10)throw new IOException("Incomplete R5 feature set");
        return new Plan(List.copyOf(cells.values()),List.copyOf(boxes),center);
    }
    private static int i(JsonArray a,int i){return a.get(i).getAsInt();}
    private static BlockPos world(MapAnchor a,int x,int y,int z){int s=a.rotation()==0?1:-1;return new BlockPos(a.x()+s*(x-80),a.y()+y-2,a.z()+s*(z-84));}
    static boolean protectedLocal(int x,int z) {
        if(x<0 || x>160 || z<1 || z>167 || x>=43&&x<=117&&z>=44&&z<=108)return true;
        int[][] zones={{6,36,45,75},{124,154,45,75},{6,36,85,115},{124,154,85,115},{43,73,127,157},{87,117,127,157},
            {4,34,3,31},{5,35,128,158}, // Accepted civic envelopes.
            {75,85,0,43},{4,156,35,41},{37,42,35,125},{118,123,35,125},{4,156,119,125},
            {43,117,109,115},{75,85,109,168},{43,117,15,17}};
        for(int[] b:zones)if(x>=b[0]&&x<=b[1]&&z>=b[2]&&z<=b[3])return true;
        int[][] homes={{43,3},{105,3},{43,20},{60,20},{88,20},{105,20}};
        for(int[] h:homes)if(x>=h[0]&&x<=h[0]+12&&z>=h[1]&&z<=h[1]+10)return true;
        for(int a:new int[]{48,110})if(x>=a&&x<=a+1&&z>=14&&z<=16)return true;
        for(int a:new int[]{48,65,93,110})if(x>=a&&x<=a+1&&z>=31&&z<=35)return true;
        return false;
    }
    static boolean matches(BlockState actual,BlockState expected) {
        if(actual.getBlock()!=expected.getBlock())return false;
        // Only natural distance drift on the same leaf type is irrelevant. Persistence is NOT ignored.
        if(actual.getBlock() instanceof LeavesBlock)actual=actual.setValue(LeavesBlock.DISTANCE,expected.getValue(LeavesBlock.DISTANCE));
        return actual.equals(expected);
    }
    private static BlockState parse(String text) throws IOException {
        int open=text.indexOf('[');String id=open<0?text:text.substring(0,open);ResourceLocation key=ResourceLocation.tryParse(id);
        if(key==null || !BuiltInRegistries.BLOCK.containsKey(key))throw new IOException("Unknown R5 block");
        BlockState state=BuiltInRegistries.BLOCK.get(key).defaultBlockState();Set<String> seen=new HashSet<>();
        if(open>=0) {
            if(!text.endsWith("]"))throw new IOException("Malformed R5 state");
            for(String pair:text.substring(open+1,text.length()-1).split(",")) {
                String[] kv=pair.split("=",2);if(kv.length!=2 || !seen.add(kv[0]))throw new IOException("Duplicate R5 property");
                Property<?> prop=state.getBlock().getStateDefinition().getProperty(kv[0]);
                if(prop==null)throw new IOException("Unknown R5 property");state=set(state,prop,kv[1]);
            }
        }
        if(seen.size()!=state.getProperties().size())throw new IOException("Incomplete R5 blockstate");return state;
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static BlockState set(BlockState state,Property p,String value)throws IOException {
        Optional parsed=p.getValue(value);if(parsed.isEmpty())throw new IOException("Invalid R5 property");return state.setValue(p,(Comparable)parsed.get());
    }
}
