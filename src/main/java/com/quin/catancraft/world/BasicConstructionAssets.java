package com.quin.catancraft.world;

import com.quin.catancraft.map.MapAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Exact delivered early Town Halls/factories and empty-city baseline. No loose overrides. */
final class BasicConstructionAssets {
    private BasicConstructionAssets() {}
    static final String BUNDLE_SHA="cbaf8b095958f7de19a58bbca2723f18a1273d908eb5f174d36cd4aa1e9608d1";
    private static final Map<String,Volume> cache=new HashMap<>();
    record Volume(int width,int height,int length,int ax,int ay,int az,BlockState[] states) {
        BlockState at(int x,int y,int z)throws IOException {
            if(x<0||y<0||z<0||x>=width||y>=height||z>=length)throw new IOException("Outside pinned base-city envelope");
            return states[(y*length+z)*width+x];
        }
    }
    static synchronized Volume load(String id)throws IOException {
        if(cache.containsKey(id))return cache.get(id);
        Map<String,byte[]> members=new HashMap<>();byte[] encoded;
        try(InputStream in=BasicConstructionAssets.class.getResourceAsStream("/data/catancraft/basic_construction/assets.b64")){
            if(in==null)throw new IOException("Missing pinned construction assets");encoded=in.readNBytes(200001);
        }
        if(encoded.length>200000)throw new IOException("Oversized construction assets");
        byte[] raw;try{raw=Base64.getMimeDecoder().decode(encoded);}catch(IllegalArgumentException e){throw new IOException("Invalid construction assets",e);}
        if(!BUNDLE_SHA.equals(TownHallRevisions.sha(raw)))throw new IOException("Construction asset bundle hash mismatch");
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(raw))){
            ZipEntry entry;while((entry=zip.getNextEntry())!=null){
                byte[] bytes=zip.readNBytes(100001);
                if(entry.isDirectory()||bytes.length>100000||members.put(entry.getName(),bytes)!=null||members.size()>13)throw new IOException("Invalid construction asset member");
            }
        }
        if(members.size()!=13)throw new IOException("Incomplete construction asset bundle");
        Map<String,Volume> validated=new HashMap<>();
        for(var member:members.entrySet()){
            String name=member.getKey().replace(".schem","");var a=SchematicAssetRegistry.asset(name);
            if(a==null)throw new IOException("Unknown pinned construction asset");
            CompoundTag n=NbtIo.readCompressed(new ByteArrayInputStream(member.getValue()));
            int w=n.getShort("Width"),h=n.getShort("Height"),l=n.getShort("Length");
            if(n.getInt("Version")!=2||n.getInt("DataVersion")!=3465||w!=a.width()||h!=a.height()||l!=a.length()
                ||!Arrays.equals(n.getIntArray("Offset"),new int[]{-a.anchorX(),-a.anchorY(),-a.anchorZ()})
                ||!n.getList("BlockEntities",Tag.TAG_COMPOUND).isEmpty()||!n.getList("Entities",Tag.TAG_COMPOUND).isEmpty())throw new IOException("Pinned construction asset contract changed: "+name);
            int max=n.getInt("PaletteMax");if(max<1||max>4096)throw new IOException("Invalid construction palette");
            BlockState[] palette=new BlockState[max];var p=n.getCompound("Palette");
            for(String text:p.getAllKeys()){int i=p.getInt(text);if(i<0||i>=max||palette[i]!=null)throw new IOException("Invalid palette entry");palette[i]=parsePalette(text);}
            for(var s:palette)if(s==null)throw new IOException("Missing palette entry");
            BlockState[] states=new BlockState[w*h*l];int i=0,value=0,shift=0;
            for(byte sb:n.getByteArray("BlockData")){int b=sb&255;value|=(b&127)<<shift;if((b&128)!=0){shift+=7;if(shift>28)throw new IOException("Bad construction VarInt");continue;}
                if(i>=states.length||value<0||value>=max)throw new IOException("Bad construction voxel");states[i++]=palette[value];value=0;shift=0;}
            if(i!=states.length||shift!=0)throw new IOException("Incomplete construction volume");
            validated.put(name,new Volume(w,h,l,a.anchorX(),a.anchorY(),a.anchorZ(),states));
        }
        cache.putAll(validated);
        if(!cache.containsKey(id))throw new IOException("Unknown pinned asset: "+id);return cache.get(id);
    }
    /** Sponge palettes may omit default properties; still reject unknown/ambiguous syntax. */
    static BlockState parsePalette(String text)throws IOException {
        if(text==null||text.isEmpty()||text.length()>1024)throw new IOException("Invalid construction palette state");
        int open=text.indexOf('[');String name=text,props=null;
        if(open>=0){
            if(!text.endsWith("]")||text.indexOf('[',open+1)>=0)throw new IOException("Invalid construction palette syntax: "+text);
            name=text.substring(0,open);props=text.substring(open+1,text.length()-1);
        }
        if(!name.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IOException("Invalid construction block ID: "+name);
        ResourceLocation id=ResourceLocation.tryParse(name);
        if(id==null||!BuiltInRegistries.BLOCK.containsKey(id))throw new IOException("Unknown construction block: "+name);
        var block=BuiltInRegistries.BLOCK.get(id);BlockState state=block.defaultBlockState();
        if(props!=null&&!props.isEmpty()){
            Set<String> seen=new HashSet<>();
            for(String pair:props.split(",",-1)){
                String[] kv=pair.split("=",-1);
                if(kv.length!=2||!seen.add(kv[0]))throw new IOException("Invalid/duplicate construction property: "+text);
                Property<?> property=block.getStateDefinition().getProperty(kv[0]);
                if(property==null)throw new IOException("Unknown construction property: "+text);
                state=property(state,property,kv[1],text);
            }
        }
        return state;
    }
    private static <T extends Comparable<T>> BlockState property(BlockState state,Property<T> property,String value,String text)throws IOException {
        T parsed=property.getValue(value).orElseThrow(()->new IOException("Invalid construction property value: "+text));
        return state.setValue(property,parsed);
    }
    static List<SchematicPlacementService.PlanCell> plan(String id,MapAnchor a)throws IOException {
        ProducerExteriorR2.validateRotation(a);var v=load(id);int sign=a.rotation()==0?1:-1;Rotation r=ProducerExteriorR2.rotation(a);
        List<SchematicPlacementService.PlanCell> out=new ArrayList<>(v.states().length);
        for(int y=0;y<v.height();y++)for(int z=0;z<v.length();z++)for(int x=0;x<v.width();x++)
            out.add(new SchematicPlacementService.PlanCell(x,y,z,new BlockPos(a.x()+sign*(x-v.ax()),a.y()+y-v.ay(),a.z()+sign*(z-v.az())),v.at(x,y,z).rotate(r)));
        return List.copyOf(out);
    }
    static BlockState baseline(MapAnchor a,BlockPos pos)throws IOException {
        ProducerExteriorR2.validateRotation(a);var v=load("starter_settlement");int sign=a.rotation()==0?1:-1;
        return v.at(sign*(pos.getX()-a.x())+v.ax(),pos.getY()-a.y()+v.ay(),sign*(pos.getZ()-a.z())+v.az()).rotate(ProducerExteriorR2.rotation(a));
    }
}
