package com.quin.catancraft.world;

import com.quin.catancraft.map.MapAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Zero-price grouped six-house transaction; exact source signatures, separate sealed history. */
final class R5HouseTransaction {
    enum Action {STATUS,APPLY,RESUME,ROLLBACK}
    enum Phase {PREPARED,APPLYING,PHYSICAL_COMPLETE,COMMITTED,ROLLING_BACK,ROLLED_BACK}
    interface Environment {
        ServerLevel level();Path file();MapAnchor anchor();CompoundTag identity();CompoundTag ledger();
        void save()throws IOException;void allowWrite(BlockPos p)throws IOException;
    }
    interface Fault {void at(String stage,int writes)throws IOException;}
    record Result(String message,int writes){}
    private record Cell(BlockPos pos,BlockState source,BlockState target){}
    private static final int FLAGS=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS;
    private R5HouseTransaction(){}
    static Phase phase(CompoundTag journal)throws IOException {
        try{return Phase.valueOf(journal.getString("phase"));}catch(IllegalArgumentException ex){throw new IOException("Unknown house journal phase",ex);}
    }
    static Result run(Environment e,Action action)throws IOException{return run(e,action,(s,n)->{});}
    static Result run(Environment e,Action action,Fault fault)throws IOException {
        CompoundTag identity=e.identity().copy(),ledger=e.ledger().copy();
        boolean exists=Files.exists(e.file(),LinkOption.NOFOLLOW_LINKS);
        CompoundTag j=exists?ProducerJournal.read(e.file()):null;
        List<String> groups=R5HouseRevisions.groups();List<R5HouseRevisions.Revision> revisions=new ArrayList<>();
        if(exists) {
            if(j.getInt("schema")!=1||!j.getString("contract").equals("r5_houses_2722756e24b1d647da167dcc4b78db0f6d4a559d297379c3887e2b89890734fb")||!j.hasUUID("id")||!identity.equals(j.getCompound("identity")))throw new IOException("House journal identity/contract conflict");
            ListTag branches=j.getList("branches",Tag.TAG_COMPOUND);if(branches.size()!=groups.size())throw new IOException("Incomplete house branch contract");
            for(int i=0;i<groups.size();i++) {
                CompoundTag b=branches.getCompound(i);R5HouseRevisions.Revision r;
                try{r=R5HouseRevisions.Revision.valueOf(b.getString("source"));}catch(IllegalArgumentException ex){throw new IOException("Unknown house source branch",ex);}
                if(r==R5HouseRevisions.Revision.R5||!b.getString("group").equals(groups.get(i))||!b.getString("signature").equals(R5HouseRevisions.signature(groups.get(i),r)))throw new IOException("Changed house branch contract");
                revisions.add(r);
            }
        } else {
            if(action==Action.RESUME||action==Action.ROLLBACK)throw new IOException("No owned house journal");
            for(String group:groups) {
                R5HouseRevisions.Revision found=null;
                for(var r:List.of(R5HouseRevisions.Revision.R1,R5HouseRevisions.Revision.R2)) {
                    boolean matches=true;
                    for(var c:R5HouseRevisions.plan(group,r,e.anchor())) {
                        if(!e.level().hasChunkAt(c.position()))throw new IOException("House chunk unloaded at "+c.position());
                        if(!e.level().getBlockState(c.position()).equals(c.state())){matches=false;break;}
                    }
                    if(matches){if(found!=null)throw new IOException("Ambiguous house baseline");found=r;}
                }
                if(found==null)throw new IOException("Unknown/mixed complete house source: "+group+"; no writes or charge");
                revisions.add(found);
            }
        }
        List<Cell> cells=new ArrayList<>();List<Integer> mask=new ArrayList<>();Set<BlockPos> unique=new HashSet<>();
        for(int g=0;g<groups.size();g++) {
            var source=R5HouseRevisions.plan(groups.get(g),revisions.get(g),e.anchor());var target=R5HouseRevisions.plan(groups.get(g),R5HouseRevisions.Revision.R5,e.anchor());
            Map<BlockPos,R5HouseRevisions.Change> route=new HashMap<>();
            for(var c:R5HouseRevisions.changes(groups.get(g),revisions.get(g),e.anchor()))if(route.put(c.position(),c)!=null)throw new IOException("Duplicate house route cell");
            for(int i=0;i<source.size();i++) {
                var a=source.get(i);var b=target.get(i);if(!a.position().equals(b.position())||!unique.add(a.position()))throw new IOException("Overlapping house context");
                if(!a.state().equals(b.state())) {
                    var c=route.remove(a.position());if(c==null||!c.before().equals(a.state())||!c.after().equals(b.state())||a.state().hasBlockEntity()||b.state().hasBlockEntity())throw new IOException("House sparse route/protected NBT conflict");
                    e.allowWrite(a.position());mask.add(cells.size());
                }
                cells.add(new Cell(a.position(),a.state(),b.state()));
            }
            if(!route.isEmpty())throw new IOException("House route exceeds full read context");
        }
        Phase phase=exists?phase(j):null;
        if(action==Action.APPLY&&exists&&phase!=Phase.COMMITTED&&phase!=Phase.ROLLED_BACK)throw new IOException("Pending house journal requires explicit resume or rollback");
        boolean rollback=action==Action.ROLLBACK||phase==Phase.ROLLING_BACK;
        if(action!=Action.ROLLBACK&&action!=Action.STATUS&&phase==Phase.ROLLING_BACK)throw new IOException("Reverse recovery requires rollback action");
        ListTag actual=exists?j.getList("actualBefore",Tag.TAG_COMPOUND):new ListTag();
        if(exists&&actual.size()!=cells.size())throw new IOException("Incomplete actual house before image");
        List<ConstructionHazards.Write> writes=new ArrayList<>();Map<BlockPos,BlockState> finalStates=new LinkedHashMap<>();Map<BlockPos,CompoundTag> nbt=new HashMap<>();Map<BlockPos,BlockState> initial=new HashMap<>();
        for(int i=0;i<cells.size();i++) {
            Cell c=cells.get(i);if(!e.level().hasChunkAt(c.pos()))throw new IOException("House context chunk unloaded");
            if(exists) {
                CompoundTag saved=actual.getCompound(i);
                if(saved.getLong("pos")!=c.pos().asLong()||!saved.getString("state").equals(ProducerExteriorR2.canonical(c.source())))throw new IOException("House before image differs from immutable source contract");
            }
            BlockState live=e.level().getBlockState(c.pos());initial.put(c.pos(),live);boolean changed=!c.source().equals(c.target());
            boolean allowed=live.equals(c.source())||exists&&changed&&live.equals(c.target());
            if(phase==Phase.COMMITTED)allowed=live.equals(c.target());
            if(phase==Phase.ROLLED_BACK)allowed=live.equals(c.source());
            if(!allowed)throw new IOException("House source/journal drift at "+c.pos());
            var be=e.level().getBlockEntity(c.pos());
            if(be!=null) {
                if(!live.hasBlockEntity()||changed)throw new IOException("Foreign house block entity at "+c.pos());
                nbt.put(c.pos(),be.saveWithFullMetadata().copy());
            } else if(live.hasBlockEntity())throw new IOException("Missing house block entity at "+c.pos());
            BlockState desired=rollback?c.source():c.target();finalStates.put(c.pos(),desired);
            if(!live.equals(desired))writes.add(new ConstructionHazards.Write(c.pos(),desired));
        }
        // Native neighbor updates must not mutate the preserved shell around any write.
        Map<BlockPos,BlockState> shell=new LinkedHashMap<>();
        for(var w:writes)for(var direction:net.minecraft.core.Direction.values()) {
            BlockPos p=w.pos().relative(direction);
            if(unique.contains(p))continue;
            if(!e.level().hasChunkAt(p))throw new IOException("House neighbor shell chunk unloaded at "+p);
            shell.put(p,e.level().getBlockState(p));
            var be=e.level().getBlockEntity(p);if(be!=null)nbt.put(p,be.saveWithFullMetadata().copy());
        }
        if(rollback)Collections.reverse(writes);
        if(action==Action.STATUS)return new Result(exists?"House journal "+phase:"Six complete sources recognized; free R5 migration available",0);
        if(phase==Phase.ROLLED_BACK&&action!=Action.ROLLBACK)throw new IOException("Rolled-back immutable house history retained; initiate a new operation");
        if(phase==Phase.COMMITTED&&!rollback||phase==Phase.ROLLED_BACK&&rollback)return new Result("House operation already complete; zero charge",0);
        PlannedWorld.verify(e.level(),finalStates);
        var guard=ConstructionHazards.inspect(e.level(),writes);guard.verify();
        if(!exists) {
            if(action!=Action.APPLY)throw new IOException("No owned pending house operation");
            // Persist the unchanged actual source world before flushing its durable before-image.
            e.save();guard.verify();verifyNbt(e,nbt);verifyStates(e,initial);verifyStates(e,shell);
            if(!identity.equals(e.identity())||!ledger.equals(e.ledger()))throw new IOException("House source save changed owner/ledger");
            fault.at("source_saved",0);
            j=new CompoundTag();j.putInt("schema",1);j.putString("contract","r5_houses_2722756e24b1d647da167dcc4b78db0f6d4a559d297379c3887e2b89890734fb");j.putUUID("id",UUID.randomUUID());j.put("identity",identity);j.putString("phase",Phase.PREPARED.name());
            ListTag branches=new ListTag();
            for(int i=0;i<groups.size();i++) {
                CompoundTag b=new CompoundTag();b.putString("group",groups.get(i));b.putString("source",revisions.get(i).name());b.putString("signature",R5HouseRevisions.signature(groups.get(i),revisions.get(i)));branches.add(b);
            }
            j.put("branches",branches);
            for(Cell c:cells){CompoundTag b=new CompoundTag();b.putLong("pos",c.pos().asLong());b.putString("state",ProducerExteriorR2.canonical(e.level().getBlockState(c.pos())));actual.add(b);}
            j.put("actualBefore",actual);ListTag preserved=new ListTag();nbt.forEach((p,n)->{CompoundTag b=new CompoundTag();b.putLong("pos",p.asLong());b.put("actualNbt",n);preserved.add(b);});j.put("preservedNbt",preserved);
            ProducerJournal.write(e.file(),j,false);fault.at("prepared",0);
        }
        guard.verify();verifyStates(e,initial);verifyStates(e,shell);verifyNbt(e,nbt);
        if(!identity.equals(e.identity())||!ledger.equals(e.ledger()))throw new IOException("House source changed before first write");
        advance(e.file(),j,rollback?Phase.ROLLING_BACK:Phase.APPLYING);int count=0;
        for(var w:writes) {
            guard.verify();e.allowWrite(w.pos());
            if(!identity.equals(e.identity())||!ledger.equals(e.ledger()))throw new IOException("House owner or paid ledger changed during operation");
            if(!e.level().getBlockState(w.pos()).equals(initial.get(w.pos())))throw new IOException("House changed after preflight at "+w.pos());
            if(!e.level().setBlock(w.pos(),w.target(),FLAGS))throw new IOException("House write rejected at "+w.pos());
            count++;fault.at(rollback?"rollback_write":"write",count);
        }
        for(var entry:finalStates.entrySet())if(!e.level().getBlockState(entry.getKey()).equals(entry.getValue()))throw new IOException("House final-state drift at "+entry.getKey());
        verifyNbt(e,nbt);verifyStates(e,shell);guard.verify();
        if(!identity.equals(e.identity())||!ledger.equals(e.ledger()))throw new IOException("House migration altered owner/economy");
        advance(e.file(),j,rollback?Phase.ROLLING_BACK:Phase.PHYSICAL_COMPLETE);e.save();guard.verify();verifyNbt(e,nbt);verifyStates(e,finalStates);verifyStates(e,shell);fault.at("physical_saved",count);
        if(!identity.equals(e.identity())||!ledger.equals(e.ledger()))throw new IOException("House save altered owner/ledger");
        advance(e.file(),j,rollback?Phase.ROLLED_BACK:Phase.COMMITTED);fault.at("committed",count);
        return new Result("Six R5 houses "+(rollback?"restored":"committed")+"; zero charge",count);
    }
    private static void verifyNbt(Environment e,Map<BlockPos,CompoundTag> nbt)throws IOException {
        for(var entry:nbt.entrySet())if(e.level().getBlockEntity(entry.getKey())==null||!entry.getValue().equals(e.level().getBlockEntity(entry.getKey()).saveWithFullMetadata()))throw new IOException("House migration changed preserved NBT");
    }
    private static void verifyStates(Environment e,Map<BlockPos,BlockState> states)throws IOException {
        for(var entry:states.entrySet())if(!entry.getValue().equals(e.level().getBlockState(entry.getKey())))throw new IOException("House preserved/final context drift at "+entry.getKey());
    }
    private static void advance(Path path,CompoundTag j,Phase phase)throws IOException {
        if(!ProducerJournal.read(path).equals(j))throw new IOException("House journal changed concurrently; preserve history");
        j.putString("phase",phase.name());ProducerJournal.write(path,j,true);
    }
}
