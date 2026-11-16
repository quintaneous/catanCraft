package com.quin.catancraft.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.io.IOException;
import java.util.*;

/** Read-only recognition of vanilla decay under exact, pinned Town Hall cover.
 * New purchase journals retain actual ground preimages; legacy journals opt out.
 * This policy never authorizes dirt under a future or foreign cover. */
final class PrestigeNaturalSupport {
    static final String FIELD="naturalCover", VERSION="pinned-town-hall-covered-grass-v1";
    private record Point(BlockState source,BlockState target,BlockState sourceCover,BlockState targetCover) {}
    private final Map<BlockPos,Point> points;
    private final Map<BlockPos,BlockState> actualBefore;
    private PrestigeNaturalSupport(Map<BlockPos,Point> points,Map<BlockPos,BlockState> actualBefore){
        this.points=Map.copyOf(points);this.actualBefore=Map.copyOf(actualBefore);
    }
    private static Map<BlockPos,Point> points(List<SchematicPlacementService.PlanCell> before,
            List<SchematicPlacementService.PlanCell> after)throws IOException {
        if(before.size()!=after.size())throw new IOException("Natural cover envelope mismatch");
        var source=ProducerService.sourceCoveredGrass(before);var target=ProducerService.sourceCoveredGrass(after);
        Map<BlockPos,Point> out=new HashMap<>();
        for(int i=0;i<before.size();i++){
            var old=before.get(i);var next=after.get(i);BlockPos p=old.position();
            if(!p.equals(next.position()))throw new IOException("Natural cover geometry changed");
            if(source.containsKey(p)||target.containsKey(p))out.put(p,new Point(old.state(),next.state(),source.get(p),target.get(p)));
        }
        return out;
    }
    static PrestigeNaturalSupport capture(ServerLevel level,List<SchematicPlacementService.PlanCell> before,
            List<SchematicPlacementService.PlanCell> after)throws IOException {
        var points=points(before,after);Map<BlockPos,BlockState> actual=new HashMap<>();
        for(var entry:points.entrySet()){
            BlockPos p=entry.getKey();Point point=entry.getValue();
            if(!level.hasChunkAt(p))throw new IOException("Unloaded natural cover source at "+p);
            BlockState live=level.getBlockState(p);
            if(!live.equals(point.source())&&!decayed(level,p,point.source(),live,point.sourceCover()))
                throw new IOException("Unrecognized Town Hall natural-cover source at "+p);
            actual.put(p,live);
        }
        return new PrestigeNaturalSupport(points,actual);
    }
    static PrestigeNaturalSupport read(CompoundTag journal,List<SchematicPlacementService.PlanCell> before,
            List<SchematicPlacementService.PlanCell> after)throws IOException {
        if(!journal.contains(FIELD))return new PrestigeNaturalSupport(Map.of(),Map.of());
        if(!journal.contains(FIELD,Tag.TAG_COMPOUND))throw new IOException("Invalid prestige natural-cover proof");
        CompoundTag proof=journal.getCompound(FIELD);
        if(!proof.getAllKeys().equals(Set.of("version","cells"))||!VERSION.equals(proof.getString("version"))
                ||!proof.contains("cells",Tag.TAG_LIST))throw new IOException("Unknown prestige natural-cover contract");
        var points=points(before,after);var rows=proof.getList("cells",Tag.TAG_COMPOUND);
        if(rows.size()!=points.size())throw new IOException("Incomplete prestige natural-cover proof");
        Map<BlockPos,BlockState> actual=new HashMap<>();
        for(int i=0;i<rows.size();i++){
            CompoundTag row=rows.getCompound(i);BlockPos p=BlockPos.of(row.getLong("pos"));Point point=points.get(p);
            if(point==null||!row.contains("pos",Tag.TAG_LONG)||!row.contains("actualBefore",Tag.TAG_COMPOUND))
                throw new IOException("Foreign prestige natural-cover position");
            CompoundTag encoded=row.getCompound("actualBefore");BlockState state=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),encoded);
            if(!NbtUtils.writeBlockState(state).equals(encoded)||actual.put(p,state)!=null
                    ||(!state.equals(point.source())&&!(point.source().equals(Blocks.GRASS_BLOCK.defaultBlockState())
                        &&state.equals(Blocks.DIRT.defaultBlockState())&&point.sourceCover()!=null)))
                throw new IOException("Untrusted prestige natural-cover before-state at "+p);
        }
        var policy=new PrestigeNaturalSupport(points,actual);
        if(!policy.describe().equals(proof))throw new IOException("Changed or noncanonical prestige natural-cover proof");
        return policy;
    }
    CompoundTag describe(){
        CompoundTag proof=new CompoundTag();proof.putString("version",VERSION);ListTag rows=new ListTag();
        points.keySet().stream().sorted(Comparator.comparingLong(BlockPos::asLong)).forEach(p->{
            Point point=points.get(p);CompoundTag row=new CompoundTag();row.putLong("pos",p.asLong());
            row.put("actualBefore",NbtUtils.writeBlockState(actualBefore.get(p)));
            if(point.sourceCover()!=null)row.put("sourceCover",NbtUtils.writeBlockState(point.sourceCover()));
            if(point.targetCover()!=null)row.put("targetCover",NbtUtils.writeBlockState(point.targetCover()));
            rows.add(row);
        });proof.put("cells",rows);return proof;
    }
    boolean has(BlockPos p){return points.containsKey(p);}
    BlockState before(BlockPos p){return actualBefore.get(p);}
    boolean sourceMatches(ServerLevel level,BlockPos p,BlockState live){
        Point point=points.get(p);return point!=null&&decayed(level,p,point.source(),live,point.sourceCover());
    }
    boolean targetMatches(ServerLevel level,BlockPos p,BlockState live){
        Point point=points.get(p);return point!=null&&decayed(level,p,point.target(),live,point.targetCover());
    }
    BlockState targetFor(BlockPos p,BlockState actual,BlockState requested){
        Point point=points.get(p);
        // An unchanged covered ground read must never become a dirt->grass write.
        if(point!=null&&actual.equals(Blocks.DIRT.defaultBlockState())&&actual.equals(actualBefore.get(p))
                &&point.source().equals(Blocks.GRASS_BLOCK.defaultBlockState())&&point.source().equals(requested)
                &&point.target().equals(requested)&&point.sourceCover()!=null&&point.sourceCover().equals(point.targetCover()))return actual;
        return TownHallInteractionPolicy.targetFor(actual,requested);
    }
    static Map<BlockPos,BlockState> unchangedCovers(List<SchematicPlacementService.PlanCell> before,
            List<SchematicPlacementService.PlanCell> after)throws IOException {
        Map<BlockPos,BlockState> result=new HashMap<>();
        for(var entry:points(before,after).entrySet()){
            Point point=entry.getValue();
            if(point.source().equals(point.target())&&point.sourceCover()!=null&&point.sourceCover().equals(point.targetCover()))
                result.put(entry.getKey(),point.sourceCover());
        }return Map.copyOf(result);
    }
    /** An interrupted bottom-up paste must not leave newly introduced grass
     * beneath obsolete source masonry. Clear only exact journaled cover->AIR
     * deltas first; this grants no recognition of exposed dirt. */
    static Set<BlockPos> obsoleteGrassCovers(List<SchematicPlacementService.PlanCell> before,
            List<SchematicPlacementService.PlanCell> after)throws IOException {
        if(before.size()!=after.size())throw new IOException("Prestige ordering envelope mismatch");
        Set<BlockPos> aboveGrass=new HashSet<>();
        for(var cell:after)if(cell.state().equals(Blocks.GRASS_BLOCK.defaultBlockState()))aboveGrass.add(cell.position().above());
        Set<BlockPos> removals=new HashSet<>();
        for(int i=0;i<before.size();i++){
            var old=before.get(i);var next=after.get(i);BlockPos p=old.position();
            if(!p.equals(next.position()))throw new IOException("Prestige ordering coordinates changed");
            if(!aboveGrass.contains(p)||!SectorNaturalSupportPolicy.blocksGrass(old.state(),p)||old.state().equals(next.state()))continue;
            if(!next.state().equals(Blocks.AIR.defaultBlockState()))throw new IOException("Unsupported changing cover over target grass at "+p);
            removals.add(p);
        }
        return Set.copyOf(removals);
    }
    static boolean decayed(ServerLevel level,BlockPos p,BlockState expected,BlockState live,BlockState cover){
        return expected.equals(Blocks.GRASS_BLOCK.defaultBlockState())&&live.equals(Blocks.DIRT.defaultBlockState())
                &&cover!=null&&level.hasChunkAt(p.above())&&cover.equals(level.getBlockState(p.above()))
                &&level.getBlockEntity(p.above())==null;
    }
}
