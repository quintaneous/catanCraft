package com.quin.catancraft.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
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
                || !StreetscapeR5.matches(b,c.before()) || !row.getCompound("after").equals(NbtUtils.writeBlockState(c.after())))
                throw new IOException("Untrusted streetscape before-image");before.put(index,b);
        }
        boolean pending=exists && phase!=Phase.COMMITTED && phase!=Phase.ROLLED_BACK;
        for(int i=0;i<cells.size();i++) {
            var c=cells.get(i);if(!e.level().hasChunkAt(c.pos()))throw new IOException("Unloaded streetscape chunk at "+c.pos());
            BlockState actual=e.level().getBlockState(c.pos());
            boolean valid=c.changes()&&pending?StreetscapeR5.matches(actual,before.get(i))||StreetscapeR5.matches(actual,c.after()):
                StreetscapeR5.matches(actual,phase==Phase.COMMITTED?c.after():c.before());
            if(!valid)throw new IOException("Streetscape conflict at "+c.pos()+": "+actual);
            if(e.level().getBlockEntity(c.pos())!=null || !actual.getFluidState().isEmpty())throw new IOException("Foreign block entity/fluid in public feature at "+c.pos());
            live.put(c.pos(),actual);
        }
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
            e.save();if(!e.ledger().equals(ledger)||!e.identity().equals(identity))throw new IOException("Source changed during save");
            j=new CompoundTag();j.putInt("schema",27);j.putUUID("id",UUID.randomUUID());j.putString("contract",StreetscapeR5.HASH);
            j.put("identity",identity.copy());
            for(int index:mask) {
                var c=cells.get(index);BlockState state=live.get(c.pos());CompoundTag row=new CompoundTag();
                row.putInt("index",index);row.putLong("pos",c.pos().asLong());row.put("before",NbtUtils.writeBlockState(state));
                row.put("after",NbtUtils.writeBlockState(c.after()));saved.add(row);before.put(index,state);
            }
            j.put("blocks",saved);j.putString("phase",Phase.PREPARED.name());StreetscapeJournal.write(e.file(),j,false);fault.at("prepared",0);
        }
        entities.verify();advance(e.file(),j,rollback?Phase.ROLLING_BACK:Phase.APPLYING);
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
        advance(e.file(),j,rollback?Phase.ROLLED_BACK:Phase.COMMITTED);
        return new Result((rollback?"R5 public spaces rolled back: ":"R5 public spaces completed: ")+writes+" block changes; zero charge; purchases and levels unchanged.",writes);
    }
    private static void advance(Path file,CompoundTag n,Phase phase)throws IOException {n.putString("phase",phase.name());StreetscapeJournal.write(file,n,true);}
}
