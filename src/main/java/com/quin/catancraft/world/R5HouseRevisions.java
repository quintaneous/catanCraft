package com.quin.catancraft.world;

import com.google.gson.*;
import com.quin.catancraft.map.MapAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import java.io.IOException;
import java.util.*;

/** Complete immutable house contexts: exact branch recognition, no natural-state allowances. */
public final class R5HouseRevisions {
    public static final int WIDTH=13,HEIGHT=47,LENGTH=11,CELLS=WIDTH*HEIGHT*LENGTH;
    public enum Revision { R1,R2,R5 }
    public record Change(BlockPos position,BlockState before,BlockState after) {}
    private static JsonObject data;
    private R5HouseRevisions() {}
    private static synchronized JsonObject data() throws IOException {
        if(data==null)data=ProducerExteriorR2.readPinned("r5-house-contexts.json.gz","2722756e24b1d647da167dcc4b78db0f6d4a559d297379c3887e2b89890734fb");
        return data;
    }
    public static List<String> groups() throws IOException {
        List<String> out=new ArrayList<>();for(JsonElement e:data().getAsJsonArray("houses"))out.add(e.getAsJsonObject().get("id").getAsString());return List.copyOf(out);
    }
    private static JsonObject row(String group) throws IOException {
        for(JsonElement e:data().getAsJsonArray("houses"))if(e.getAsJsonObject().get("id").getAsString().equals(group))return e.getAsJsonObject();
        throw new IOException("Unknown R5 house: "+group);
    }
    public static String signature(String group,Revision revision) throws IOException {
        return row(group).getAsJsonObject("revisions").getAsJsonObject(revision.name()).get("signature").getAsString();
    }
    public static String token(String group,Revision revision) throws IOException {return group+"@"+revision.name();}
    /** Settlement anchor, NOT city center: world=anchor+R(local-[80,2,1]). */
    private static BlockPos world(MapAnchor anchor,int x,int y,int z) {
        int sign=anchor.rotation()==0?1:-1;return new BlockPos(anchor.x()+sign*(x-80),anchor.y()+y-2,anchor.z()+sign*(z-1));
    }
    public static List<SchematicPlacementService.PlanCell> plan(String group,Revision revision,MapAnchor settlementAnchor) throws IOException {
        ProducerExteriorR2.validateRotation(settlementAnchor);JsonObject h=row(group);JsonArray min=h.getAsJsonArray("localMin");
        int x0=min.get(0).getAsInt(),z0=min.get(2).getAsInt();
        BlockState[] states=ProducerExteriorR2.decode(h.getAsJsonObject("revisions").getAsJsonObject(revision.name()),CELLS);
        List<SchematicPlacementService.PlanCell> out=new ArrayList<>(CELLS);
        for(int y=0;y<HEIGHT;y++)for(int z=0;z<LENGTH;z++)for(int x=0;x<WIDTH;x++)
            out.add(new SchematicPlacementService.PlanCell(x0+x,y,z0+z,world(settlementAnchor,x0+x,y,z0+z),states[(y*LENGTH+z)*WIDTH+x].rotate(ProducerExteriorR2.rotation(settlementAnchor))));
        return List.copyOf(out);
    }
    /** Exact provided sparse route, with rotation performed exactly once. */
    public static List<Change> changes(String group,Revision source,MapAnchor settlementAnchor) throws IOException {
        ProducerExteriorR2.validateRotation(settlementAnchor);if(source==Revision.R5)return List.of();
        JsonArray route=row(group).getAsJsonObject("routes").getAsJsonArray(source.name()+"_to_R5");List<Change> out=new ArrayList<>();
        for(JsonElement element:route) {
            JsonObject c=element.getAsJsonObject();JsonArray p=c.getAsJsonArray("local_xyz");
            out.add(new Change(world(settlementAnchor,p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt()),
                ProducerExteriorR2.parseExact(c.get("expected_before_blockstate").getAsString()).rotate(ProducerExteriorR2.rotation(settlementAnchor)),
                ProducerExteriorR2.parseExact(c.get("after_blockstate").getAsString()).rotate(ProducerExteriorR2.rotation(settlementAnchor))));
        }
        return List.copyOf(out);
    }
}
