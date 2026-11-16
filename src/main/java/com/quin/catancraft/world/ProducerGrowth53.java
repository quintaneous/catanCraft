package com.quin.catancraft.world;

import com.quin.catancraft.map.MapAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import java.io.IOException;
import java.util.*;

/** Seventeen immutable farm sub-slab soil reads. A top slab leaves enough
 * space/light for vanilla grass to spread down from the authored surface.
 * No other producer soil, slab, cultivation block or future geometry qualifies.
 */
final class ProducerGrowth53 {
    static final String FIELD="producerGrowth53";
    private ProducerGrowth53(){}
    static Map<BlockPos,BlockState> covers(String family,int tier,boolean exterior,MapAnchor anchor)throws IOException {
        if(!family.equals("farm"))return Map.of();
        var plan=exterior?ProducerExteriorR2.plan(family,tier,anchor):ProducerRevisions.plan(family,tier,anchor);
        Map<BlockPos,BlockState> states=new HashMap<>(),covers=new LinkedHashMap<>();
        for(var cell:plan)states.put(cell.position(),cell.state());
        BlockState slab=Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.TOP).setValue(SlabBlock.WATERLOGGED,false);
        for(var cell:plan)if(cell.localY()==0&&(cell.localZ()==21&&cell.localX()>=3&&cell.localX()<=11
                ||cell.localZ()==15&&cell.localX()>=21&&cell.localX()<=28)) {
            if(!cell.state().equals(Blocks.DIRT.defaultBlockState())||!slab.equals(states.get(cell.position().above())))throw new IOException("Pinned farm sub-slab soil changed");
            covers.put(cell.position(),slab);
        }
        if(covers.size()!=17)throw new IOException("Incomplete pinned farm sub-slab soil");
        // The direction is source -> target: vanilla chooses dy in [-3,+1].
        // Every eligible cell must connect to immutable authored grass through
        // these same seventeen cells; arbitrary neighboring dirt never extends it.
        Set<BlockPos> reached=new HashSet<>();
        for(var cell:plan)if(cell.state().equals(Blocks.GRASS_BLOCK.defaultBlockState())) {
            BlockState above=states.get(cell.position().above());
            if(above!=null&&above.getFluidState().isEmpty()&&!ProducerService.naturalCover(above,cell.position().above()))reached.add(cell.position());
        }
        boolean added;
        do {
            added=false;
            for(BlockPos pos:covers.keySet())if(!reached.contains(pos)) {
                boolean adjacent=false;
                for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=-1;y<=3;y++)if(reached.contains(pos.offset(x,y,z)))adjacent=true;
                if(adjacent){reached.add(pos);added=true;}
            }
        }while(added);
        if(!reached.containsAll(covers.keySet()))throw new IOException("Farm sub-slab soil lost its authored grass route");
        return Map.copyOf(covers);
    }
    static boolean matches(ServerLevel level,BlockPos pos,BlockState actual,Map<BlockPos,BlockState> covers) {
        BlockState cover=covers.get(pos);
        return actual.equals(Blocks.GRASS_BLOCK.defaultBlockState())&&cover!=null&&level.hasChunkAt(pos.above())
            &&cover.equals(level.getBlockState(pos.above()))&&level.getBlockEntity(pos)==null&&level.getBlockEntity(pos.above())==null;
    }
    static Map<BlockPos,BlockState> transaction(ProducerTransaction.Spec spec)throws IOException {
        Map<BlockPos,BlockState> result=new HashMap<>();
        for(var plot:spec.plots()) {
            var before=covers(plot.family(),plot.source(),plot.sourceR2(),plot.anchor());
            var after=covers(plot.family(),plot.target(),plot.targetR2(),plot.anchor());
            if(!before.equals(after))throw new IOException("Producer growth contract must remain an unchanged read");
            for(var entry:before.entrySet())if(result.put(entry.getKey(),entry.getValue())!=null)throw new IOException("Overlapping producer growth reads");
        }return Map.copyOf(result);
    }
    static CompoundTag describe(Map<BlockPos,BlockState> covers) {
        CompoundTag proof=new CompoundTag();proof.putInt("version",1);ListTag rows=new ListTag();
        covers.keySet().stream().sorted(Comparator.comparingLong(BlockPos::asLong)).forEach(pos->{
            CompoundTag row=new CompoundTag();row.putLong("pos",pos.asLong());row.put("cover",NbtUtils.writeBlockState(covers.get(pos)));rows.add(row);
        });proof.put("covers",rows);return proof;
    }
    static Map<BlockPos,BlockState> read(CompoundTag journal,Map<BlockPos,BlockState> qualified)throws IOException {
        if(!journal.contains(FIELD))return Map.of();
        if(!journal.contains(FIELD,Tag.TAG_COMPOUND)||!describe(qualified).equals(journal.getCompound(FIELD)))throw new IOException("Changed producer sub-slab growth proof");
        return qualified;
    }
}
