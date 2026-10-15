package com.quin.catancraft.world;

import com.quin.catancraft.map.MapAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Immutable, byte-pinned R1/R2 assets. Never reads or overwrites loose overrides. */
public final class TownHallRevisions {
    public static final int CELLS=75*47*65, REPAIR_CELLS=584;
    private static final Map<String,BlockState[]> CACHE=new HashMap<>();
    private TownHallRevisions() {}
    public static String token(int tier,int revision) { hash(tier,revision); return "th"+tier+"@r"+revision; }
    public static int revision(int tier,String token) {
        for(int r=1;r<=2;r++) if(token(tier,r).equals(token))return r;
        throw new IllegalStateException("Unknown TH"+tier+" visual revision: "+token);
    }
    public static String hash(int t,int r) {
        return switch(t+":"+r) {
            case "4:1" -> "39f2fda6a74d0d891d45b37d111079905a6f67e4261b591c13902b4215342c4b";
            case "5:1" -> "1ea2fd544c32ed5506a553bf6c56628ecd4b7ac8d29257009e7bd84a427bbc06";
            case "4:2" -> "4e2a9ac3bb8c0df38691403f390683a735ab7ec5275e89ec2eb24448ed0375b9";
            case "5:2" -> "ccc6db49d246d93754d84613f19c9411884ab52f3cf468b94de32a3367e9bc18";
            default -> throw new IllegalArgumentException("Unsupported prestige revision");
        };
    }
    public static List<SchematicPlacementService.PlanCell> plan(int tier,int revision,MapAnchor a) throws IOException {
        if(a==null || (a.rotation()!=0 && a.rotation()!=180))throw new IOException("Invalid hall anchor");
        BlockState[] source=load(tier,revision);
        Rotation r=a.rotation()==0?Rotation.NONE:Rotation.CLOCKWISE_180;
        int sign=a.rotation()==0?1:-1;
        ArrayList<SchematicPlacementService.PlanCell> out=new ArrayList<>(CELLS);
        for(int y=0;y<47;y++)for(int z=0;z<65;z++)for(int x=0;x<75;x++)
            out.add(new SchematicPlacementService.PlanCell(x,y,z,
                new BlockPos(a.x()+sign*(x-37),a.y()+y-2,a.z()+sign*(z-1)),
                source[(y*65+z)*75+x].rotate(r)));
        return List.copyOf(out);
    }
    public static int[] mask(List<SchematicPlacementService.PlanCell> a,List<SchematicPlacementService.PlanCell> b) {
        if(a.size()!=CELLS || b.size()!=CELLS)throw new IllegalStateException("Invalid hall envelope");
        ArrayList<Integer> mask=new ArrayList<>();
        for(int i=0;i<CELLS;i++) {
            if(!a.get(i).position().equals(b.get(i).position()))throw new IllegalStateException("Repair anchors differ");
            if(!a.get(i).state().equals(b.get(i).state())) {
                var c=a.get(i);
                if(c.localY()<5 || c.localY()>16 || i==managerIndex()
                    || c.state().hasBlockEntity() || b.get(i).state().hasBlockEntity()
                    || !c.state().getFluidState().isEmpty() || !b.get(i).state().getFluidState().isEmpty())
                    throw new IllegalStateException("Unapproved repair cell");
                mask.add(i);
            }
        }
        if(mask.size()!=REPAIR_CELLS)throw new IllegalStateException("Repair mask is not 584 cells");
        return mask.stream().mapToInt(Integer::intValue).toArray();
    }
    public static int managerIndex() { return (5*65+17)*75+37; }
    static String sha(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(Exception e){throw new IllegalStateException(e);}
    }
    private static synchronized BlockState[] load(int tier,int revision) throws IOException {
        String pin=hash(tier,revision);
        if(CACHE.containsKey(pin))return CACHE.get(pin);
        if(revision==2) {
            BlockState[] out=expandR2(tier,load(tier,1));
            String expected=tier==4
                ?"b74fa052fc6e1e2104fc07d2f2fabae53908194d5206566fafc4fa844c171f06"
                :"45b700d02425e7ee0d2813927a99b2d4de297e2158ba2b0e6aaeb785dcc2ce60";
            if(!voxelHash(out).equals(expected))throw new IOException("Expanded R2 differs from delivered schematic voxels");
            CACHE.put(pin,out);return out;
        }
        String file="thall"+tier+".schem";
        byte[] raw;
        try(InputStream in=TownHallRevisions.class.getResourceAsStream("/data/catancraft/townhall_revisions/"+file)){
            if(in==null)throw new IOException("Missing bundled immutable hall "+file);
            raw=in.readNBytes(100_001);
            if(raw.length>100_000)throw new IOException("Oversized hall source");
        }
        if(!pin.equals(sha(raw)))throw new IOException("Bundled hall hash mismatch");
        CompoundTag n=NbtIo.readCompressed(new ByteArrayInputStream(raw));
        if(n.getInt("Version")!=2 || n.getInt("DataVersion")!=3465
            || n.getShort("Width")!=75 || n.getShort("Height")!=47 || n.getShort("Length")!=65
            || !Arrays.equals(n.getIntArray("Offset"),new int[]{-37,-2,-1})
            || !n.getList("BlockEntities",Tag.TAG_COMPOUND).isEmpty()
            || !n.getList("Entities",Tag.TAG_COMPOUND).isEmpty())
            throw new IOException("Bundled hall contract changed");
        int max=n.getInt("PaletteMax");
        if(max<1 || max>4096)throw new IOException("Invalid palette");
        BlockState[] palette=new BlockState[max];
        CompoundTag p=n.getCompound("Palette");
        for(String text:p.getAllKeys()) {
            int id=p.getInt(text);
            if(id<0 || id>=max || palette[id]!=null)throw new IOException("Bad palette ID");
            palette[id]=parse(text);
        }
        for(BlockState s:palette)if(s==null)throw new IOException("Incomplete palette");
        BlockState[] out=new BlockState[CELLS];
        int count=0,value=0,shift=0;
        for(byte sb:n.getByteArray("BlockData")) {
            int b=sb&255; value|=(b&127)<<shift;
            if((b&128)!=0) { shift+=7;if(shift>28)throw new IOException("Invalid VarInt");continue; }
            if(count>=CELLS || value<0 || value>=max)throw new IOException("Invalid voxel");
            out[count++]=palette[value];value=0;shift=0;
        }
        if(count!=CELLS || shift!=0)throw new IOException("Incomplete hall");
        CACHE.put(pin,out);return out;
    }
    /**
     * Lossless expansion of Blender's reviewed sparse repair, not an approximate
     * redesign. The canonical hash above was calculated independently from ALL
     * 229125 decoded cells of the delivered R2 files. No runtime loose source can
     * override this plan. Original schematic SHA-256 values remain provenance.
     */
    private static BlockState[] expandR2(int tier,BlockState[] original) throws IOException {
        BlockState[] out=original.clone();
        int left=tier==4?7:5,right=tier==4?67:69,rear=tier==4?55:58;
        BlockState stone=parse(tier==4?"minecraft:smooth_sandstone":"minecraft:smooth_quartz");
        BlockState cap=parse("minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]");
        BlockState floor=parse("minecraft:polished_diorite"),air=parse("minecraft:air");
        for(int center:new int[]{left+10,right-10})for(int x=center-1;x<=center+1;x++)put(out,x,6,rear+1,air);
        for(int lo:new int[]{left+3,right-7}) {
            int hi=lo+4;
            for(int x=lo;x<=hi;x++)put(out,x,14,34,floor);
            for(int x:new int[]{lo-1,hi+1})for(int z=24;z<=33;z++) {
                int step=z-24;
                for(int y=5;y<7+step;y++)if(out[index(x,y,z)].isAir())put(out,x,y,z,stone);
                if(out[index(x,7+step,z)].isAir())put(out,x,7+step,z,cap);
            }
            for(int x:new int[]{lo-1,hi+1})for(int z=23;z<=33;z++) {
                put(out,x,15,z,stone);put(out,x,16,z,cap);
            }
            for(int x=lo;x<=hi;x++){put(out,x,15,23,stone);put(out,x,16,23,cap);}
        }
        for(int x=28;x<=46;x++)for(int z=22;z<=47;z++)
            if(x==28 || x==46 || z==22 || z==47){
                put(out,x,14,z,floor);put(out,x,15,z,stone);put(out,x,16,z,cap);
            }
        for(int x=26;x<=48;x++){put(out,x,15,18,stone);put(out,x,16,18,cap);}
        return out;
    }
    private static int index(int x,int y,int z){return (y*65+z)*75+x;}
    private static void put(BlockState[] a,int x,int y,int z,BlockState s){a[index(x,y,z)]=s;}
    private static String voxelHash(BlockState[] states) throws IOException {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            Map<BlockState,byte[]> text=new HashMap<>();
            for(BlockState state:states) {
                byte[] bytes=text.get(state);
                if(bytes==null){
                    ArrayList<String> props=new ArrayList<>();
                    for(Property<?> p:state.getProperties())props.add(p.getName()+"="+propertyText(state,p));
                    Collections.sort(props);
                    String line=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()
                        +(props.isEmpty()?"":"["+String.join(",",props)+"]")+"\n";
                    bytes=line.getBytes(java.nio.charset.StandardCharsets.UTF_8);text.put(state,bytes);
                }
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        }catch(Exception e){throw new IOException("Could not fingerprint R2 voxels",e);}
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static String propertyText(BlockState state,Property p){return p.getName(state.getValue(p));}
    private static BlockState parse(String text) throws IOException {
        int open=text.indexOf('[');
        String id=open<0?text:text.substring(0,open);
        ResourceLocation loc=ResourceLocation.tryParse(id);
        if(loc==null || !BuiltInRegistries.BLOCK.containsKey(loc))throw new IOException("Unknown block "+id);
        Block block=BuiltInRegistries.BLOCK.get(loc);
        BlockState state=block.defaultBlockState();
        if(open>=0) {
            if(!text.endsWith("]"))throw new IOException("Malformed state");
            for(String pair:text.substring(open+1,text.length()-1).split(",")) {
                String[] kv=pair.split("=",2);
                if(kv.length!=2)throw new IOException("Bad property");
                Property<?> prop=block.getStateDefinition().getProperty(kv[0]);
                if(prop==null)throw new IOException("Unknown property");
                state=set(state,prop,kv[1]);
            }
        }
        return state;
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static BlockState set(BlockState s,Property p,String v) throws IOException {
        Optional parsed=p.getValue(v);if(parsed.isEmpty())throw new IOException("Invalid property value");
        return s.setValue(p,(Comparable)parsed.get());
    }
}
