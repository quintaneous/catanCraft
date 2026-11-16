package com.quin.catancraft.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Zero-cost public features only. Journal is the revision receipt; economy data is never mutated. */
final class StreetscapeTransaction {
    enum Action { STATUS, APPLY, RESUME, ROLLBACK }
    enum Phase { PREPARED, APPLYING, COMMITTED, ROLLING_BACK, ROLLED_BACK }
    interface Environment {
        ServerLevel level();Path file();StreetscapeR5.Plan plan();CompoundTag identity();CompoundTag ledger();
        void save() throws IOException;void allowWrite(BlockPos pos) throws IOException;
    }
    interface Fault {void at(String stage,int writes)throws IOException;}
    record Result(String message,int writes){}
    private static final int FLAGS=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS;
    private StreetscapeTransaction(){}
    static Phase phase(CompoundTag n)throws IOException {
        try{return Phase.valueOf(n.getString("phase"));}catch(Exception e){throw new IOException("Unknown streetscape phase",e);}
    }
    static Result run(Environment e,Action action)throws IOException{return run(e,action,(s,n)->{});}
    static Result run(Environment e,Action action,Fault fault)throws IOException {
        var cells=e.plan().cells();boolean exists=Files.exists(e.file(),LinkOption.NOFOLLOW_LINKS);
        CompoundTag j=exists?StreetscapeJournal.read(e.file()):null;Phase phase=exists?phase(j):null;
        if(exists && (j.getInt("schema")!=27 || !j.hasUUID("id") || !j.getString("contract").equals(StreetscapeR5.HASH)
            || !j.getCompound("identity").equals(e.identity())))throw new IOException("Foreign streetscape journal or changed ownership/map");
        NaturalSoil soil=new NaturalSoil(cells,j);
        List<Integer> mask=new ArrayList<>();Map<BlockPos,BlockState> live=new LinkedHashMap<>();
        Map<BlockPos,CompoundTag> nbt=new HashMap<>();
        for(int i=0;i<cells.size();i++)if(cells.get(i).changes()){e.allowWrite(cells.get(i).pos());mask.add(i);}
        if(mask.size()!=StreetscapeR5.WRITES)throw new IOException("Incomplete streetscape write mask");
        ListTag saved=exists?j.getList("blocks",Tag.TAG_COMPOUND):new ListTag();Map<Integer,BlockState> before=new HashMap<>();
        if(exists && saved.size()!=mask.size())throw new IOException("Incomplete streetscape before-image");
        if(exists)for(int k=0;k<mask.size();k++) {
            int index=mask.get(k);var c=cells.get(index);CompoundTag row=saved.getCompound(k),tag=row.getCompound("before");
            BlockState b=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),tag);
            if(row.getInt("index")!=index || row.getLong("pos")!=c.pos().asLong() || !tag.equals(NbtUtils.writeBlockState(b))
                || !soil.storedBefore(b,c) || !row.getCompound("after").equals(NbtUtils.writeBlockState(c.after())))
                throw new IOException("Untrusted streetscape before-image");before.put(index,b);
        }
        boolean pending=exists && phase!=Phase.COMMITTED && phase!=Phase.ROLLED_BACK;
        for(int i=0;i<cells.size();i++) {
            var c=cells.get(i);if(!e.level().hasChunkAt(c.pos()))throw new IOException("Unloaded streetscape chunk at "+c.pos());
            BlockState actual=e.level().getBlockState(c.pos());
            boolean valid=c.changes()&&pending?soil.matches(e.level(),c.pos(),actual,before.get(i),true)||soil.matches(e.level(),c.pos(),actual,c.after(),true):
                soil.matches(e.level(),c.pos(),actual,phase==Phase.COMMITTED?c.after():c.before(),exists);
            if(!valid)throw new IOException("Streetscape conflict at "+c.pos()+": "+actual);
            if(e.level().getBlockEntity(c.pos())!=null || !actual.getFluidState().isEmpty())throw new IOException("Foreign block entity/fluid in public feature at "+c.pos());
            live.put(c.pos(),actual);
        }
        // A target is evidence only once its exact pinned cover is present, never
        // merely because a later design would put a block above this grass.
        if(exists)soil.observeCoveredDirt(e.level());
        if(action==Action.STATUS)return new Result("R5 public spaces: 10 features; "+cells.size()+" cells checked; "+mask.size()+
            " design cells; "+(exists?phase:"no transaction")+"; read-only, zero charge.",0);
        if(action==Action.APPLY&&pending)throw new IOException("Incomplete streetscape transaction; use resume or rollback");
        if(action!=Action.APPLY&&!exists)throw new IOException("No streetscape transaction to recover");
        if(phase==Phase.COMMITTED&&action!=Action.ROLLBACK)return new Result("Already completed: R5 public spaces verified; zero writes and zero charge.",0);
        if(phase==Phase.ROLLED_BACK) {
            if(action==Action.ROLLBACK)return new Result("Already rolled back: source verified; zero writes.",0);
            throw new IOException("Rolled-back streetscape history retained; do not delete or replay");
        }
        if(phase==Phase.ROLLING_BACK&&action!=Action.ROLLBACK)throw new IOException("Finish the started streetscape rollback");
        boolean rollback=action==Action.ROLLBACK;
        List<ConstructionHazards.Write> hazardWrites=new ArrayList<>();
        for(int k=0;k<mask.size();k++) {
            int index=mask.get(rollback?mask.size()-1-k:k);var c=cells.get(index);
            hazardWrites.add(new ConstructionHazards.Write(c.pos(),rollback?before.get(index):c.after()));
        }
        ConstructionHazards.Guard entities;
        try { entities=ConstructionHazards.inspect(e.level(),hazardWrites); }
        catch(IOException hazard) {
            if(!exists)throw new IOException(hazard.getMessage()+"; no public-space writes or charge occurred.",hazard);
            throw hazard;
        }
        // Preserve neighboring state AND actual current inventory NBT, even during a later recovery.
        Map<BlockPos,BlockState> shell=new HashMap<>();
        for(var c:cells)for(Direction d:Direction.values()) {
            BlockPos p=c.pos().relative(d);if(live.containsKey(p)||shell.containsKey(p))continue;
            if(!e.level().hasChunkAt(p))throw new IOException("Unloaded public-feature boundary");
            shell.put(p,e.level().getBlockState(p));BlockEntity be=e.level().getBlockEntity(p);
            if(be!=null)nbt.put(p,be.saveWithFullMetadata().copy());
        }
        Set<UUID> items=new HashSet<>();for(var box:e.plan().boxes())for(ItemEntity it:e.level().getEntitiesOfClass(ItemEntity.class,box.inflate(2)))items.add(it.getUUID());
        CompoundTag ledger=e.ledger().copy(),identity=e.identity().copy();
        if(!exists) {
            soil.verifySourceCovers(e.level());
            e.save();if(!e.ledger().equals(ledger)||!e.identity().equals(identity))throw new IOException("Source changed during save");
            j=new CompoundTag();j.putInt("schema",27);j.putUUID("id",UUID.randomUUID());j.putString("contract",StreetscapeR5.HASH);
            j.put("identity",identity.copy());
            for(int index:mask) {
                var c=cells.get(index);BlockState state=live.get(c.pos());CompoundTag row=new CompoundTag();
                row.putInt("index",index);row.putLong("pos",c.pos().asLong());row.put("before",NbtUtils.writeBlockState(state));
                row.put("after",NbtUtils.writeBlockState(c.after()));saved.add(row);before.put(index,state);
            }
            j.put("blocks",saved);soil.persist(j);j.putString("phase",Phase.PREPARED.name());StreetscapeJournal.write(e.file(),j,false);fault.at("prepared",0);
        }
        // In particular, durably record an observed target cover before rollback
        // can remove it and leave its naturally decayed, unchanged soil exposed.
        entities.verify();soil.persist(j);advance(e.file(),j,rollback?Phase.ROLLING_BACK:Phase.APPLYING);
        int writes=0;
        for(int k=0;k<mask.size();k++) {
            int index=mask.get(rollback?mask.size()-1-k:k);var c=cells.get(index);
            if(!e.identity().equals(identity))throw new IOException("Ownership changed during streetscape operation");
            e.allowWrite(c.pos());if(!e.level().getBlockState(c.pos()).equals(live.get(c.pos())))throw new IOException("Streetscape changed after preflight");
            BlockState wanted=rollback?before.get(index):c.after();
            if(!wanted.equals(live.get(c.pos()))) {
                if(!e.level().setBlock(c.pos(),wanted,FLAGS))throw new IOException("Streetscape block write rejected");
                writes++;fault.at(rollback?"rollback_write":"write",writes);
            }
        }
        entities.verify();
        for(int i=0;i<cells.size();i++) {
            var c=cells.get(i);BlockState wanted=c.changes()?(rollback?before.get(i):c.after()):live.get(c.pos());
            BlockState actual=e.level().getBlockState(c.pos());
            if(!StreetscapeR5.matches(actual,wanted)||!actual.canSurvive(e.level(),c.pos()))throw new IOException("Streetscape readback/support failure at "+c.pos());
        }
        for(var entry:shell.entrySet())if(!entry.getValue().equals(e.level().getBlockState(entry.getKey())))throw new IOException("Outside-mask state changed");
        for(var entry:nbt.entrySet()) {
            BlockEntity be=e.level().getBlockEntity(entry.getKey());
            if(be==null || !entry.getValue().equals(be.saveWithFullMetadata()))throw new IOException("Neighbor inventory NBT changed");
        }
        for(var box:e.plan().boxes())for(ItemEntity it:e.level().getEntitiesOfClass(ItemEntity.class,box.inflate(2)))if(!items.contains(it.getUUID()))throw new IOException("Unexpected streetscape item drop");
        if(!e.ledger().equals(ledger))throw new IOException("Unexpected economy/purchase change");
        e.save();entities.verify();fault.at("world_saved",writes);
        if(!e.ledger().equals(ledger)||!e.identity().equals(identity))throw new IOException("Unexpected saved-state change");
        soil.observeCoveredDirt(e.level());soil.persist(j);advance(e.file(),j,rollback?Phase.ROLLED_BACK:Phase.COMMITTED);
        return new Result((rollback?"R5 public spaces rolled back: ":"R5 public spaces completed: ")+writes+" block changes; zero charge; purchases and levels unchanged.",writes);
    }
    /** New journals opt into this exact pinned-source/observed-target policy.
     * Old schema-27 records without the field retain their original strict rules.
     * Soil reads remain reads; only actual design writes have before-images and
     * rollback always restores those actual captured states.
     */
    static NaturalSoil validatedSoilProof(StreetscapeR5.Plan plan,CompoundTag journal)throws IOException {
        if(plan==null||journal==null)throw new IOException("Missing streetscape soil proof inputs");
        return new NaturalSoil(plan.cells(),journal);
    }
    static boolean matchesRecordedBefore(int index,BlockState state,NaturalSoil proof) {
        return index>=0&&index<proof.cells.size()&&proof.storedBefore(state,proof.cells.get(index));
    }
    static final class NaturalSoil {
        private static final String FIELD="naturalSoilV1";
        private final List<StreetscapeR5.Cell> cells;
        private final Map<BlockPos,BlockState> source,target;
        private final Map<BlockPos,BlockState> sourceBefore=new HashMap<>();
        private final Set<BlockPos> sourceDirt=new HashSet<>(),targetDirt=new HashSet<>();
        private final boolean enabled,recorded;
        NaturalSoil(List<StreetscapeR5.Cell> cells,CompoundTag journal)throws IOException {
            this.cells=cells;
            source=covers(cells,false);target=covers(cells,true);
            recorded=journal!=null;enabled=!recorded||journal.contains(FIELD);
            if(recorded&&enabled) {
                if(!journal.contains(FIELD,Tag.TAG_COMPOUND))throw new IOException("Invalid streetscape natural soil proof type");
                CompoundTag proof=journal.getCompound(FIELD);
                if(!proof.getAllKeys().equals(Set.of("version","sourceCovers","targetCovers","sourceBefore","observedSourceDirt","observedTargetDirt"))
                    ||!proof.contains("version",Tag.TAG_INT)||proof.getInt("version")!=1
                    ||!proof.contains("sourceCovers",Tag.TAG_LIST)||!proof.get("sourceCovers").equals(describeCovers(source))
                    ||!proof.contains("targetCovers",Tag.TAG_LIST)||!proof.get("targetCovers").equals(describeCovers(target))
                    ||!proof.contains("sourceBefore",Tag.TAG_LIST))throw new IOException("Changed streetscape natural soil contract");
                ListTag before=proof.getList("sourceBefore",Tag.TAG_COMPOUND);
                if(before.size()!=source.size()||before.size()!=((ListTag)proof.get("sourceBefore")).size())throw new IOException("Incomplete streetscape source soil image");
                for(Tag value:before) {
                    CompoundTag row=(CompoundTag)value;
                    if(!row.getAllKeys().equals(Set.of("pos","state"))||!row.contains("pos",Tag.TAG_LONG)||!row.contains("state",Tag.TAG_COMPOUND))throw new IOException("Malformed streetscape source soil image");
                    BlockPos pos=BlockPos.of(row.getLong("pos"));BlockState state=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),row.getCompound("state"));
                    if(!source.containsKey(pos)||!row.getCompound("state").equals(NbtUtils.writeBlockState(state))
                        ||!state.equals(Blocks.GRASS_BLOCK.defaultBlockState())&&!state.equals(Blocks.DIRT.defaultBlockState())
                        ||sourceBefore.put(pos,state)!=null)throw new IOException("Untrusted streetscape source soil image");
                }
                if(!before.equals(describeStates(sourceBefore,"state")))throw new IOException("Noncanonical streetscape source soil order");
                readObserved(proof,"observedSourceDirt",source,sourceDirt);
                readObserved(proof,"observedTargetDirt",target,targetDirt);
                if(phase(journal)==Phase.PREPARED&&(!sourceDirt.isEmpty()||!targetDirt.isEmpty()))throw new IOException("Premature streetscape dirt observation");
            }
        }
        private static Map<BlockPos,BlockState> covers(List<StreetscapeR5.Cell> cells,boolean after) {
            Map<BlockPos,BlockState> states=new HashMap<>(),result=new HashMap<>();
            for(var c:cells)states.put(c.pos(),after?c.after():c.before());
            for(var c:cells)if((after?c.after():c.before()).equals(Blocks.GRASS_BLOCK.defaultBlockState())) {
                BlockState cover=states.get(c.pos().above());
                if(cover!=null&&BuiltInRegistries.BLOCK.getKey(cover.getBlock()).getNamespace().equals("minecraft")
                    &&SectorNaturalSupportPolicy.blocksGrass(cover,c.pos().above()))result.put(c.pos(),cover);
            }
            return Map.copyOf(result);
        }
        private static boolean exactCover(ServerLevel level,BlockPos pos,BlockState state) {
            return state!=null&&level.hasChunkAt(pos.above())&&state.equals(level.getBlockState(pos.above()))
                &&level.getBlockEntity(pos.above())==null;
        }
        boolean storedBefore(BlockState actual,StreetscapeR5.Cell cell) {
            if(enabled&&source.containsKey(cell.pos()))return actual.equals(sourceBefore.get(cell.pos()));
            return StreetscapeR5.matches(actual,cell.before());
        }
        boolean matches(ServerLevel level,BlockPos pos,BlockState actual,BlockState expected,boolean allowTarget) {
            if(StreetscapeR5.matches(actual,expected))return true;
            if(!enabled||!expected.equals(Blocks.GRASS_BLOCK.defaultBlockState())||!actual.equals(Blocks.DIRT.defaultBlockState()))return false;
            return source.containsKey(pos)&&(Blocks.DIRT.defaultBlockState().equals(sourceBefore.get(pos))||sourceDirt.contains(pos)||exactCover(level,pos,source.get(pos)))
                ||allowTarget&&target.containsKey(pos)&&(targetDirt.contains(pos)||exactCover(level,pos,target.get(pos)));
        }
        void verifySourceCovers(ServerLevel level)throws IOException {
            for(var row:source.entrySet()) {
                if(!exactCover(level,row.getKey(),row.getValue()))throw new IOException("Unproven streetscape source cover at "+row.getKey());
                BlockState actual=level.getBlockState(row.getKey());
                if(!actual.equals(Blocks.GRASS_BLOCK.defaultBlockState())&&!actual.equals(Blocks.DIRT.defaultBlockState()))throw new IOException("Unrecognized streetscape source soil");
                sourceBefore.put(row.getKey(),actual);
            }
        }
        void observeCoveredDirt(ServerLevel level) {
            if(!enabled)return;
            observe(level,source,sourceDirt);observe(level,target,targetDirt);
        }
        private static void observe(ServerLevel level,Map<BlockPos,BlockState> covers,Set<BlockPos> observed) {
            for(var row:covers.entrySet())if(level.getBlockState(row.getKey()).equals(Blocks.DIRT.defaultBlockState())
                &&exactCover(level,row.getKey(),row.getValue()))observed.add(row.getKey());
        }
        private static void readObserved(CompoundTag proof,String key,Map<BlockPos,BlockState> covers,Set<BlockPos> observed)throws IOException {
            if(!proof.contains(key,Tag.TAG_LONG_ARRAY))throw new IOException("Missing streetscape dirt observations");
            long[] positions=proof.getLongArray(key);
            if(positions.length>covers.size())throw new IOException("Oversized streetscape dirt observations");
            for(long value:positions)if(!covers.containsKey(BlockPos.of(value))||!observed.add(BlockPos.of(value)))throw new IOException("Unapproved/duplicate streetscape dirt observation");
            if(!Arrays.equals(positions,observed.stream().mapToLong(BlockPos::asLong).sorted().toArray()))throw new IOException("Noncanonical streetscape dirt observations");
        }
        private static ListTag describeCovers(Map<BlockPos,BlockState> covers) {
            return describeStates(covers,"cover");
        }
        private static ListTag describeStates(Map<BlockPos,BlockState> states,String key) {
            ListTag rows=new ListTag();states.keySet().stream().sorted(Comparator.comparingLong(BlockPos::asLong)).forEach(pos->{
                CompoundTag row=new CompoundTag();row.putLong("pos",pos.asLong());row.put(key,NbtUtils.writeBlockState(states.get(pos)));rows.add(row);
            });return rows;
        }
        void persist(CompoundTag journal) {
            if(!enabled)return;
            CompoundTag proof=new CompoundTag();proof.putInt("version",1);proof.put("sourceCovers",describeCovers(source));
            proof.put("targetCovers",describeCovers(target));proof.put("sourceBefore",describeStates(sourceBefore,"state"));
            proof.putLongArray("observedSourceDirt",sourceDirt.stream().mapToLong(BlockPos::asLong).sorted().toArray());
            proof.putLongArray("observedTargetDirt",targetDirt.stream().mapToLong(BlockPos::asLong).sorted().toArray());
            journal.put(FIELD,proof);
        }
    }
    private static void advance(Path file,CompoundTag n,Phase phase)throws IOException {n.putString("phase",phase.name());StreetscapeJournal.write(file,n,true);}
}
