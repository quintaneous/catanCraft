package com.quin.catancraft.world;

import com.google.gson.*;
import com.quin.catancraft.map.MapAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Immutable delivered R2 revisions; old R1 resources and interpretation remain separate. */
public final class ProducerExteriorR2 {
    private ProducerExteriorR2() {}
    private static JsonObject data;
    static synchronized JsonObject readPinned(String file,String pin) throws IOException {
        byte[] raw;
        try(InputStream in=ProducerExteriorR2.class.getResourceAsStream("/data/catancraft/integration/"+file)) {
            if(in==null)throw new IOException("Missing integration context: "+file);
            raw=in.readNBytes(500001);
        }
        if(raw.length>500000 || !TownHallRevisions.sha(raw).equals(pin))throw new IOException("Integration context hash mismatch: "+file);
        try(var gz=new GZIPInputStream(new ByteArrayInputStream(raw))) {
            byte[] json=gz.readNBytes(20000001);
            if(json.length>20000000)throw new IOException("Integration context too large");
            return JsonParser.parseString(new String(json,StandardCharsets.UTF_8)).getAsJsonObject();
        } catch(RuntimeException ex) {throw new IOException("Invalid integration context",ex);}
    }
    private static synchronized JsonObject data() throws IOException {
        if(data==null)data=readPinned("producer-r2-contexts.json.gz","1e47d1b8cd6c31a1065a8562efbce0db22a37a1fc7127ac5a1a56067f646ef09");
        return data;
    }
    private static JsonObject row(String family,int tier) throws IOException {
        for(JsonElement e:data().getAsJsonArray("producers")) {
            JsonObject o=e.getAsJsonObject();
            if(o.get("family").getAsString().equals(family)&&o.get("level").getAsInt()==tier)return o;
        }
        throw new IOException("Unknown producer R2 revision: "+family+":"+tier);
    }
    public static boolean hasRevision(String family,int tier) {return ProducerRevisions.managed(family)&&tier>=3&&tier<=5;}
    public static String token(String family,int tier) throws IOException {return row(family,tier).get("targetToken").getAsString();}
    public static String schematicHash(String family,int tier) throws IOException {return row(family,tier).get("targetSchematicSHA256").getAsString();}
    public static List<SchematicPlacementService.PlanCell> plan(String family,int tier,MapAnchor anchor) throws IOException {
        validateRotation(anchor);var asset=SchematicAssetRegistry.asset(family);
        if(asset==null)throw new IOException("Unknown producer family");
        BlockState[] states=decode(row(family,tier).getAsJsonObject("target"),31*47*31);
        int sign=anchor.rotation()==0?1:-1;Rotation rotation=rotation(anchor);
        List<SchematicPlacementService.PlanCell> result=new ArrayList<>(states.length);
        for(int y=0;y<47;y++)for(int z=0;z<31;z++)for(int x=0;x<31;x++)
            result.add(new SchematicPlacementService.PlanCell(x,y,z,new BlockPos(anchor.x()+sign*(x-asset.anchorX()),anchor.y()+y-asset.anchorY(),anchor.z()+sign*(z-asset.anchorZ())),states[(y*31+z)*31+x].rotate(rotation)));
        return List.copyOf(result);
    }
    static void validateRotation(MapAnchor anchor) throws IOException {
        if(anchor==null || (anchor.rotation()!=0&&anchor.rotation()!=180))throw new IOException("Integration supports only rotations 0 and 180");
    }
    static Rotation rotation(MapAnchor anchor) {return anchor.rotation()==0?Rotation.NONE:Rotation.CLOCKWISE_180;}
    static BlockState[] decode(JsonObject context,int count) throws IOException {
        if(!context.get("order").getAsString().equals("YZX"))throw new IOException("Invalid context order");
        JsonArray palette=context.getAsJsonArray("palette"),blocks=context.getAsJsonArray("blocks");
        if(blocks.size()!=count||palette.size()<1||palette.size()>4096)throw new IOException("Invalid context dimensions");
        List<String> texts=new ArrayList<>();List<BlockState> states=new ArrayList<>();
        for(JsonElement e:palette) {
            String text=e.getAsString();BlockState state=parseExact(text);texts.add(text);states.add(state);
        }
        BlockState[] out=new BlockState[count];StringBuilder signature=new StringBuilder();
        for(int i=0;i<count;i++) {
            int id=blocks.get(i).getAsInt();if(id<0||id>=states.size())throw new IOException("Invalid context palette index");
            out[i]=states.get(id);if(i>0)signature.append('\n');signature.append(texts.get(id));
        }
        if(!TownHallRevisions.sha(signature.toString().getBytes(StandardCharsets.UTF_8)).equals(context.get("signature").getAsString()))throw new IOException("Full context signature mismatch");
        return out;
    }
    static BlockState parseExact(String text) throws IOException {
        try {
            BlockState state=BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(),text,false).blockState();
            if(!canonical(state).equals(text))throw new IOException("Noncanonical/incomplete integration state: "+text);
            return state;
        } catch(com.mojang.brigadier.exceptions.CommandSyntaxException e) {throw new IOException("Invalid integration state: "+text,e);}
    }
    public static String canonical(BlockState state) {
        StringBuilder text=new StringBuilder(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        List<String> properties=new ArrayList<>();
        state.getValues().forEach((p,v)->properties.add(p.getName()+"="+propertyValue(p,v)));
        Collections.sort(properties);if(!properties.isEmpty())text.append('[').append(String.join(",",properties)).append(']');
        return text.toString();
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static String propertyValue(net.minecraft.world.level.block.state.properties.Property p,Comparable v) {return p.getName(v);}
}
