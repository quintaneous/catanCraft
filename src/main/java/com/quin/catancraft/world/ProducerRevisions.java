package com.quin.catancraft.world;

import com.quin.catancraft.map.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import java.io.*;
import java.util.*;

/** Exact delivered Resource R1 bytes. Does not read loose schematic overrides. */
public final class ProducerRevisions {
    static final int CELLS=31*47*31;
    static final List<String> FAMILIES=List.of("quarry","farm","lumberyard");
    private static final Map<String,BlockState[]> CACHE=new HashMap<>();
    private ProducerRevisions() {}
    public static boolean managed(String family) { return FAMILIES.contains(family); }
    public static List<String> applicable(TerritoryDefinition def) {
        return SchematicAssetRegistry.rawProducerAssets(def).stream().filter(ProducerRevisions::managed).toList();
    }
    static String hash(String family,int tier) {
        return switch(family+":"+tier) {
            case "farm:1" -> "45e05e84dcc167e7d5ccad29141f13e3e1a43a8930b3796137245a24c70a3c84";
            case "farm:2" -> "ea5c95506a21efdf6141ac326a2208c0664790bf0c69351ef3fb3eeec77b49d9";
            case "farm:3" -> "16d1e098ffc6845348ff30092172407d474f75ecbba3f3a5d1bb31e3456c8a92";
            case "farm:4" -> "9074b1b870a09cab9e5a61c16a3ff87e79fceb3ca295b9457bcca69e3b7e2bbb";
            case "farm:5" -> "2f630045c2c7d13c1ebc8096718437f8e5a2ed63c490013968023086915b0b05";
            case "lumberyard:1" -> "2703c470f819ce0ee46dc4da3af065f41f03f9c64db30686b6e30b80e769a24c";
            case "lumberyard:2" -> "21a183d630e3af089f02b17f6d28193c658aa9b90bb28abec9c9b4b83a56477b";
            case "lumberyard:3" -> "2333154aa56473e7ac7b0fbc724acf06059fab5293b35c2c7419d59af940d950";
            case "lumberyard:4" -> "27125ecb1b67c23d2015351d85c7487312e68405e8561758ddef2b06470eab06";
            case "lumberyard:5" -> "c5e601b5f794fab938577c6a03d3dc07845b5d8578c5544da25d9ece2cf18e34";
            case "quarry:1" -> "2129de14577a7a52df9f2b81dd0eb71e1a470c2944d21bafd96e9e830c153713";
            case "quarry:2" -> "fbf87528db88f6ae4f8c69a4ba8dbc92cb1d715bdf2bd20fc40e110efa98fbf8";
            case "quarry:3" -> "1440a3180627267fc9d812e05aff2d6909a5b360fca800c2277fe34943ac7c0b";
            case "quarry:4" -> "bfae81b17cb9a5e86eb2ec0717053e871472353ce6d529f23cb99282f5f6a2a0";
            case "quarry:5" -> "5f1f7e3654869ee8851836098b6e24fa4bfd5e3164b94804e8544a972e4f67ac";
            default -> throw new IllegalArgumentException("Unknown producer revision");
        };
    }
    public static String token(String family,int tier) {
        hash(family,tier);
        return tier==1?family+"@r4":family+"@resource_r1_l"+tier;
    }
    static int revision(String family,String token) throws IOException {
        for(int l=1;l<=5;l++)if(token(family,l).equals(token))return l;
        throw new IOException("Unknown "+family+" source revision: "+token);
    }
    static List<SchematicPlacementService.PlanCell> plan(String family,int tier,MapAnchor a) throws IOException {
        if(a==null || (a.rotation()!=0 && a.rotation()!=180))throw new IOException("Invalid producer anchor");
        var asset=SchematicAssetRegistry.asset(family);
        if(asset==null || !managed(family))throw new IOException("Not a managed producer");
        BlockState[] states=load(family,tier);int sign=a.rotation()==0?1:-1;
        Rotation rot=a.rotation()==0?Rotation.NONE:Rotation.CLOCKWISE_180;
        ArrayList<SchematicPlacementService.PlanCell> result=new ArrayList<>(CELLS);
        for(int y=0;y<47;y++)for(int z=0;z<31;z++)for(int x=0;x<31;x++)
            result.add(new SchematicPlacementService.PlanCell(x,y,z,new BlockPos(a.x()+sign*(x-asset.anchorX()),
                a.y()+y-asset.anchorY(),a.z()+sign*(z-asset.anchorZ())),states[(y*31+z)*31+x].rotate(rot)));
        return List.copyOf(result);
    }
    static boolean protectedCell(BlockState s) {
        return s.is(Blocks.BARREL)||s.is(Blocks.FARMLAND)||s.is(Blocks.WHEAT)||s.is(Blocks.CARROTS)
            || !s.getFluidState().isEmpty();
    }
    /** Explicit normal-use allowances; no missing doors or missing crops are accepted. */
    static boolean matches(BlockState actual,BlockState expected) {
        if(actual.getBlock()!=expected.getBlock())return false;
        if(expected.is(Blocks.BARREL))return actual.setValue(BarrelBlock.OPEN,expected.getValue(BarrelBlock.OPEN)).equals(expected);
        if(expected.is(Blocks.FARMLAND))return actual.setValue(FarmBlock.MOISTURE,expected.getValue(FarmBlock.MOISTURE)).equals(expected);
        if(expected.is(Blocks.WHEAT)||expected.is(Blocks.CARROTS))
            return actual.setValue(CropBlock.AGE,expected.getValue(CropBlock.AGE)).equals(expected);
        return TownHallInteractionPolicy.matches(actual,expected,false);
    }
    static synchronized BlockState[] load(String family,int tier) throws IOException {
        String pin=hash(family,tier);if(CACHE.containsKey(pin))return CACHE.get(pin).clone();
        byte[] raw;
        try(InputStream in=ProducerRevisions.class.getResourceAsStream("/data/catancraft/producer_revisions/"+family+"_l"+tier+".schem")) {
            if(in==null)throw new IOException("Missing bundled producer schematic");raw=in.readNBytes(100001);
        }
        if(raw.length>100000 || !TownHallRevisions.sha(raw).equals(pin))throw new IOException("Producer schematic hash mismatch");
        CompoundTag n=NbtIo.readCompressed(new ByteArrayInputStream(raw));var asset=SchematicAssetRegistry.asset(family);
        if(n.getInt("Version")!=2 || n.getInt("DataVersion")!=3465 || n.getShort("Width")!=31 || n.getShort("Height")!=47
            || n.getShort("Length")!=31 || !Arrays.equals(n.getIntArray("Offset"),new int[]{-asset.anchorX(),-asset.anchorY(),-asset.anchorZ()})
            || !n.getList("BlockEntities",Tag.TAG_COMPOUND).isEmpty() || !n.getList("Entities",Tag.TAG_COMPOUND).isEmpty())
            throw new IOException("Producer placement contract changed");
        int max=n.getInt("PaletteMax");if(max<1 || max>4096)throw new IOException("Invalid producer palette");
        BlockState[] palette=new BlockState[max];CompoundTag p=n.getCompound("Palette");
        for(String text:p.getAllKeys()) {
            int id=p.getInt(text);if(id<0 || id>=max || palette[id]!=null)throw new IOException("Invalid palette index");
            palette[id]=parse(text);
        }
        for(BlockState s:palette)if(s==null)throw new IOException("Incomplete palette");
        BlockState[] out=new BlockState[CELLS];int count=0,value=0,shift=0;
        for(byte sb:n.getByteArray("BlockData")) {
            int b=sb&255;value|=(b&127)<<shift;
            if((b&128)!=0){shift+=7;if(shift>28)throw new IOException("Invalid VarInt");continue;}
            if(count>=CELLS || value<0 || value>=max)throw new IOException("Invalid producer voxel");
            out[count++]=palette[value];value=0;shift=0;
        }
        if(count!=CELLS || shift!=0)throw new IOException("Incomplete producer volume");
        CACHE.put(pin,out);return out.clone();
    }
    private static BlockState parse(String text) throws IOException {
        int split=text.indexOf('[');String name=split<0?text:text.substring(0,split);
        ResourceLocation id=ResourceLocation.tryParse(name);
        if(id==null || !BuiltInRegistries.BLOCK.containsKey(id))throw new IOException("Unknown producer block "+name);
        Block block=BuiltInRegistries.BLOCK.get(id);BlockState state=block.defaultBlockState();Set<String> seen=new HashSet<>();
        if(split>=0) {
            if(!text.endsWith("]"))throw new IOException("Malformed state");
            for(String pair:text.substring(split+1,text.length()-1).split(",")) {
                String[] kv=pair.split("=",2);
                if(kv.length!=2 || !seen.add(kv[0]))throw new IOException("Malformed/duplicate property");
                Property<?> property=block.getStateDefinition().getProperty(kv[0]);
                if(property==null)throw new IOException("Unknown property");state=set(state,property,kv[1]);
            }
        }
        if(seen.size()!=state.getProperties().size())throw new IOException("Incomplete producer properties "+text);
        return state;
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static BlockState set(BlockState s,Property p,String v) throws IOException {
        Optional parsed=p.getValue(v);if(parsed.isEmpty())throw new IOException("Invalid property value");
        return s.setValue(p,(Comparable)parsed.get());
    }
}
