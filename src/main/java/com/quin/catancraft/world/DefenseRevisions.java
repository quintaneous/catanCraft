package com.quin.catancraft.world;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Immutable reviewed 37 R4-A + 17 R6 socket plans, already in world coordinates. */
public final class DefenseRevisions {
    public record Cell(BlockPos pos, BlockState before, BlockState after) {}
    public record Read(BlockPos pos, BlockState state) {}
    public record Plan(String city,String socket,String module,List<String> dependencies,List<String> excludes,List<Cell> cells,List<Read> reads) {
        public String token(){return "defense_r4a_r6_1:"+module+":"+HASH;}
    }
    public static final String HASH="3a2eaf758fb8e9563d41ad6cc71c10bfd4c09b98dea714ace8875eb5c4c42fd5";
    private static Map<String,Plan> plans;
    private DefenseRevisions(){}
    private static BlockState state(String s) throws Exception {
        return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(),s,false).blockState();
    }
    private static List<String> strings(JsonArray a){List<String> v=new ArrayList<>();for(var e:a)v.add(e.getAsString());return List.copyOf(v);}
    public static synchronized Plan plan(String city,String socket) throws IOException {
        if(plans==null)try(var in=DefenseRevisions.class.getResourceAsStream("/assets/catancraft/defense/contracts.json.gz")) {
            if(in==null)throw new IOException("Missing defense contracts");byte[] bytes=in.readAllBytes();
            if(!TownHallRevisions.sha(bytes).equals(HASH))throw new IOException("Defense contract digest mismatch");
            JsonArray array=JsonParser.parseReader(new InputStreamReader(new GZIPInputStream(new ByteArrayInputStream(bytes)),StandardCharsets.UTF_8)).getAsJsonArray();
            Map<String,Plan> loaded=new HashMap<>();
            for(var e:array){var n=e.getAsJsonObject();List<Cell> cells=new ArrayList<>();List<Read> reads=new ArrayList<>();
                for(var w:n.getAsJsonArray("writes")){var a=w.getAsJsonArray();cells.add(new Cell(pos(a),state(a.get(3).getAsString()),state(a.get(4).getAsString())));}
                for(var r:n.getAsJsonArray("reads")){var a=r.getAsJsonArray();reads.add(new Read(pos(a),state(a.get(3).getAsString())));}
                Plan p=new Plan(n.get("city").getAsString(),n.get("socket").getAsString(),n.get("module").getAsString(),strings(n.getAsJsonArray("dependencies")),strings(n.getAsJsonArray("excludes")),List.copyOf(cells),List.copyOf(reads));
                if(loaded.put(p.city()+":"+p.socket(),p)!=null)throw new IOException("Duplicate defense socket");
            }
            if(loaded.size()!=864)throw new IOException("Incomplete defense registry");plans=Map.copyOf(loaded);
        }catch(IOException x){throw x;}catch(Exception x){throw new IOException("Invalid defense contracts",x);}
        Plan result=plans.get(city+":"+socket);if(result==null)throw new IOException("Unregistered/deferred defense socket");return result;
    }
    public static List<Plan> city(String city) throws IOException { plan(city,"access_checkpoint");return plans.values().stream().filter(p->p.city().equals(city)).sorted(java.util.Comparator.comparing(Plan::socket)).toList(); }
    private static BlockPos pos(JsonArray a){return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}
}
