package com.quin.catancraft.world;

import com.quin.catancraft.map.MapAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Narrow R1 -> R2 same-tier repair. No economy mutator exists in this engine.
 * Journals are sealed, bound to an immutable identity, and retained after rollback.
 * Reconciliation changes only owned before/after cells; foreign mixtures refuse.
 */
final class TownHallRepairTransaction {
    enum Action { STATUS, APPLY, RESUME, ROLLBACK }
    enum Phase { PREPARED, APPLYING, PHYSICAL_COMPLETE, COMMITTED, ROLLING_BACK, ROLLED_BACK }
    interface Environment {
        ServerLevel level();
        MapAnchor anchor();
        int tier();
        Path file();
        CompoundTag identity();
        CompoundTag history() throws IOException;
        CompoundTag ledger();
        String token();
        void token(String value);
        void save() throws IOException;
    }
    interface Fault { void at(String stage,int writes) throws IOException; }
    record Result(String message,int writes) {}
    private static final int FLAGS=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS;
    private static final int MAGIC=0x43544832, LIMIT=4*1024*1024;
    private static final Fault NONE=(s,n)->{};
    private TownHallRepairTransaction() {}
    static Result run(Environment e,Action action) throws IOException { return run(e,action,NONE); }

    static Result run(Environment e,Action action,Fault fault) throws IOException {
        var old=TownHallRevisions.plan(e.tier(),1,e.anchor());
        var target=TownHallRevisions.plan(e.tier(),2,e.anchor());
        int[] mask=TownHallRevisions.mask(old,target);
        BitSet edited=new BitSet(old.size()); for(int i:mask)edited.set(i);
        boolean exists=Files.exists(e.file(),LinkOption.NOFOLLOW_LINKS);
        CompoundTag journal=exists?read(e.file()):null;
        Phase phase=exists?phase(journal):null;
        int revision=TownHallRevisions.revision(e.tier(),e.token());
        CompoundTag history=e.history();
        if(exists)validateJournal(e,journal,old,target,mask,history);
        boolean mixed=phase!=null && phase!=Phase.COMMITTED && phase!=Phase.ROLLED_BACK;
        if(phase==Phase.COMMITTED && revision!=2 || phase==Phase.ROLLED_BACK && revision!=1)
            throw new IOException("Repair journal and saved visual revision disagree");
        // Full preflight before even advancing an owned pending journal.
        BlockState[] live=new BlockState[old.size()];
        Map<BlockPos,BlockState> doors=new HashMap<>();
        for(var c:old)if(c.state().getBlock() instanceof DoorBlock)doors.put(c.position(),c.state());
        for(var c:old)if(!e.level().hasChunkAt(c.position()))throw new IOException("Unloaded hall chunk at "+c.position());
        Set<BlockPos> missing=TownHallInteractionPolicy.missingDoorCells(doors,e.level()::getBlockState);
        BlockPos manager=old.get(TownHallRevisions.managerIndex()).position();
        CompoundTag managerNow=manager(e.level(),manager);
        if(mixed && !managerNow.equals(journal.getCompound("activeManager")))
            throw new IOException("Manager changed during an incomplete repair; preserve journal for review");
        for(int i=0;i<old.size();i++) {
            BlockPos p=old.get(i).position();
            BlockState state=e.level().getBlockState(p);
            live[i]=state;
            if(!e.level().getFluidState(p).isEmpty())throw new IOException("Unexpected fluid at "+p);
            if(i!=TownHallRevisions.managerIndex() && e.level().getBlockEntity(p)!=null)
                throw new IOException("Unexpected inventory/block entity at "+p);
            if(edited.get(i)) {
                BlockState before=old.get(i).state(),after=target.get(i).state();
                boolean valid=mixed?(state.equals(before)||state.equals(after)):state.equals(revision==1?before:after);
                if(!valid)throw new IOException("Unsafe repair cell at "+p+": "+state);
            } else if(!TownHallInteractionPolicy.matches(state,old.get(i).state(),i==TownHallRevisions.managerIndex())
                    && !missing.contains(p)) throw new IOException("Foreign/modified hall cell at "+p+": "+state);
        }
        if(action==Action.STATUS)
            return new Result("TH"+e.tier()+" R"+revision+"; "+(phase==null?"no repair journal":phase.name())+
                "; 229125 cells checked; "+(revision==1?"584-cell repair":"corrected design")+"; no writes or charge.",0);
        if(action==Action.APPLY && exists && mixed)
            throw new IOException("Incomplete repair; use resume or rollback, not another apply");
        if(action==Action.RESUME && !exists)throw new IOException("No owned repair journal to resume");
        if(action==Action.RESUME && phase==Phase.ROLLING_BACK)
            throw new IOException("Rollback already started; finish rollback");
        if(phase==Phase.ROLLED_BACK) {
            if(action==Action.ROLLBACK)return new Result("Already rolled back; verified R1, history retained.",0);
            throw new IOException("Repair was rolled back; history retained. Do not delete it or reapply.");
        }
        if(action!=Action.ROLLBACK && revision==2 && !mixed)
            return new Result("Already repaired: verified TH"+e.tier()+" R2. No writes or charge.",0);
        if(action==Action.ROLLBACK && !exists)
            throw new IOException("No owned repair before-image; a newly purchased R2 hall cannot be rolled back as a repair");

        AABB bounds=bounds(old);
        if(!e.level().getEntities((Entity)null,bounds,ent->!ent.isRemoved()).isEmpty())
            throw new IOException("Town Hall occupied. Stand on the road outside; leave doors as they are.");
        // Exact ephemeral before-images protect every untouched cell and the outer shell.
        Map<BlockPos,BlockState> shell=new LinkedHashMap<>();
        Map<BlockPos,CompoundTag> shellNbt=new LinkedHashMap<>();
        BlockPos lo=BlockPos.containing(bounds.minX-1,bounds.minY-1,bounds.minZ-1);
        BlockPos hi=BlockPos.containing(bounds.maxX,bounds.maxY,bounds.maxZ);
        for(BlockPos v:BlockPos.betweenClosed(lo,hi)) {
            if(bounds.contains(v.getX()+0.5,v.getY()+0.5,v.getZ()+0.5))continue;
            BlockPos p=v.immutable();
            if(!e.level().hasChunkAt(p))throw new IOException("Unloaded hall boundary at "+p);
            shell.put(p,e.level().getBlockState(p));
            BlockEntity be=e.level().getBlockEntity(p);
            if(be!=null)shellNbt.put(p,be.saveWithFullMetadata().copy());
        }
        Set<UUID> items=new HashSet<>();
        for(ItemEntity item:e.level().getEntitiesOfClass(ItemEntity.class,bounds.inflate(2)))items.add(item.getUUID());
        CompoundTag ledger=e.ledger().copy();
        if(!exists) {
            journal=new CompoundTag();
            journal.putInt("schema",1);
            journal.putUUID("transaction",UUID.randomUUID());
            journal.put("identity",e.identity().copy());
            journal.put("history",history.copy());
            journal.put("manager",managerNow.copy());
            journal.put("activeManager",managerNow.copy());
            journal.put("ledgerBefore",ledger.copy()); // Evidence only: NEVER restored/charged.
            journal.putString("sourceHash",TownHallRevisions.hash(e.tier(),1));
            journal.putString("targetHash",TownHallRevisions.hash(e.tier(),2));
            journal.putIntArray("mask",mask);
            ListTag blocks=new ListTag();
            for(int i:mask) {
                CompoundTag b=new CompoundTag();b.putInt("index",i);
                b.put("before",NbtUtils.writeBlockState(live[i]));
                b.put("after",NbtUtils.writeBlockState(target.get(i).state()));
                blocks.add(b);
            }
            journal.put("blocks",blocks);
            journal.putString("phase",Phase.PREPARED.name());
            write(e.file(),journal,false);
            fault.at("prepared",0);
        }
        boolean rollback=action==Action.ROLLBACK;
        if(rollback && phase==Phase.COMMITTED) {
            if(!read(e.file()).equals(journal))throw new IOException("Repair history changed");
            journal.put("activeManager",managerNow.copy());
            journal.putString("phase",Phase.ROLLING_BACK.name());
            write(e.file(),journal,true);
        } else advance(e.file(),journal,rollback?Phase.ROLLING_BACK:Phase.APPLYING);
        int writes=0;
        ListTag cells=journal.getList("blocks",Tag.TAG_COMPOUND);
        // Reverse ordering is important for removal of caps before their supports.
        for(int k=0;k<mask.length;k++) {
            int j=rollback?mask.length-1-k:k, i=mask[j];
            BlockPos p=old.get(i).position();
            BlockState wanted=rollback?NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),
                cells.getCompound(j).getCompound("before")):target.get(i).state();
            if(!e.level().getBlockState(p).equals(live[i]))throw new IOException("Cell changed after preflight at "+p);
            if(!live[i].equals(wanted)) {
                if(!e.level().setBlock(p,wanted,FLAGS))throw new IOException("Block write rejected at "+p);
                live[i]=wanted;writes++;
                fault.at(rollback?"rollback_write":"write",writes);
            }
        }
        verify(e,old,live,manager,managerNow,shell,shellNbt,ledger,history,items,bounds);
        advance(e.file(),journal,rollback?Phase.ROLLING_BACK:Phase.PHYSICAL_COMPLETE);
        e.save(); fault.at("physical_saved",writes);
        e.token(TownHallRevisions.token(e.tier(),rollback?1:2));
        e.save(); fault.at("token_saved",writes);
        verify(e,old,live,manager,managerNow,shell,shellNbt,ledger,history,items,bounds);
        advance(e.file(),journal,rollback?Phase.ROLLED_BACK:Phase.COMMITTED);
        fault.at("committed",writes);
        return new Result((rollback?"Rolled back to the actual saved before-states":"Repaired TH"+e.tier()+" to R2")+
            ": "+writes+" block changes; city level, resources, book and purchases preserved. Journal retained.",writes);
    }
    private static void validateJournal(Environment e,CompoundTag n,
            List<SchematicPlacementService.PlanCell> old,List<SchematicPlacementService.PlanCell> target,
            int[] mask,CompoundTag history) throws IOException {
        if(n.getInt("schema")!=1 || !n.hasUUID("transaction") || !n.getCompound("identity").equals(e.identity())
            || !n.getCompound("history").equals(history)
            || !n.getString("sourceHash").equals(TownHallRevisions.hash(e.tier(),1))
            || !n.getString("targetHash").equals(TownHallRevisions.hash(e.tier(),2))
            || !Arrays.equals(n.getIntArray("mask"),mask))
            throw new IOException("Repair identity, source, mask or purchase history mismatch");
        ListTag cells=n.getList("blocks",Tag.TAG_COMPOUND);
        if(cells.size()!=mask.length)throw new IOException("Incomplete before-image");
        for(int j=0;j<mask.length;j++) {
            int i=mask[j];CompoundTag c=cells.getCompound(j);
            // Compare structured tags exactly; unknown block IDs must not silently decode to AIR.
            if(c.getInt("index")!=i || !c.getCompound("before").equals(NbtUtils.writeBlockState(old.get(i).state()))
                || !c.getCompound("after").equals(NbtUtils.writeBlockState(target.get(i).state())))
                throw new IOException("Untrusted repair before/after state at index "+i);
        }
        phase(n);
    }
    private static void verify(Environment e,List<SchematicPlacementService.PlanCell> old,
            BlockState[] exact,BlockPos manager,CompoundTag nbt,Map<BlockPos,BlockState> shell,
            Map<BlockPos,CompoundTag> shellNbt,CompoundTag ledger,CompoundTag history,Set<UUID> items,AABB box) throws IOException {
        for(int i=0;i<old.size();i++)if(!e.level().getBlockState(old.get(i).position()).equals(exact[i]))
            throw new IOException("Post-write envelope mismatch at "+old.get(i).position());
        if(!manager(e.level(),manager).equals(nbt) || !e.ledger().equals(ledger) || !e.history().equals(history))
            throw new IOException("Book, nation state or purchase history changed; repair journal retained");
        for(var cell:shell.entrySet()) {
            BlockEntity be=e.level().getBlockEntity(cell.getKey());
            if(!e.level().getBlockState(cell.getKey()).equals(cell.getValue())
                || !Objects.equals(shellNbt.get(cell.getKey()),be==null?null:be.saveWithFullMetadata()))
                throw new IOException("Outside-envelope state changed at "+cell.getKey());
        }
        for(ItemEntity item:e.level().getEntitiesOfClass(ItemEntity.class,box.inflate(2)))
            if(!items.contains(item.getUUID()))throw new IOException("Unexpected repair item drop");
    }
    private static CompoundTag manager(ServerLevel l,BlockPos p) throws IOException {
        if(!l.getBlockState(p).is(Blocks.LECTERN) || !(l.getBlockEntity(p) instanceof LecternBlockEntity b))
            throw new IOException("Missing management lectern");
        return b.saveWithFullMetadata().copy();
    }
    static AABB bounds(List<SchematicPlacementService.PlanCell> cells) {
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        for(var c:cells){BlockPos p=c.position();minX=Math.min(minX,p.getX());maxX=Math.max(maxX,p.getX());
            minY=Math.min(minY,p.getY());maxY=Math.max(maxY,p.getY());minZ=Math.min(minZ,p.getZ());maxZ=Math.max(maxZ,p.getZ());}
        return new AABB(minX,minY,minZ,maxX+1,maxY+1,maxZ+1);
    }
    static Phase phase(CompoundTag n) throws IOException {
        try{return Phase.valueOf(n.getString("phase"));}catch(Exception x){throw new IOException("Unknown repair phase",x);}
    }
    private static void advance(Path p,CompoundTag n,Phase phase) throws IOException {
        if(!read(p).equals(n))throw new IOException("Repair journal changed during operation");
        n.putString("phase",phase.name());write(p,n,true);
    }
    static CompoundTag read(Path p) throws IOException {
        if(Files.isSymbolicLink(p) || !Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS) || Files.size(p)>LIMIT+40)
            throw new IOException("Invalid repair journal file");
        try(DataInputStream in=new DataInputStream(Files.newInputStream(p))) {
            if(in.readInt()!=MAGIC)throw new IOException("Wrong repair journal format");
            int size=in.readInt();if(size<1 || size>LIMIT)throw new IOException("Invalid repair length");
            byte[] digest=in.readNBytes(32),bytes=in.readNBytes(size);
            if(digest.length!=32 || bytes.length!=size || in.read()!=-1
                || !HexFormat.of().formatHex(digest).equals(TownHallRevisions.sha(bytes)))
                throw new IOException("Repair journal checksum mismatch");
            try(GZIPInputStream gzip=new GZIPInputStream(new ByteArrayInputStream(bytes))) {
                byte[] decoded=gzip.readNBytes(16*1024*1024+1);
                if(decoded.length>16*1024*1024)throw new IOException("Oversized repair NBT");
                return NbtIo.read(new DataInputStream(new ByteArrayInputStream(decoded)));
            }
        }
    }
    private static void write(Path p,CompoundTag n,boolean replace) throws IOException {
        Files.createDirectories(p.getParent());
        if(!replace && Files.exists(p,LinkOption.NOFOLLOW_LINKS))throw new IOException("Repair history already exists");
        if(Files.isSymbolicLink(p))throw new IOException("Refusing journal symlink");
        ByteArrayOutputStream out=new ByteArrayOutputStream();NbtIo.writeCompressed(n,out);
        byte[] bytes=out.toByteArray();if(bytes.length>LIMIT)throw new IOException("Oversized repair journal");
        Path temp=Files.createTempFile(p.getParent(),p.getFileName().toString(),".writing");
        try {
            try(DataOutputStream d=new DataOutputStream(Files.newOutputStream(temp))) {
                d.writeInt(MAGIC);d.writeInt(bytes.length);d.write(HexFormat.of().parseHex(TownHallRevisions.sha(bytes)));d.write(bytes);
            }
            try(FileChannel c=FileChannel.open(temp,StandardOpenOption.WRITE)){c.force(true);}
            Files.move(temp,p,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            try(FileChannel c=FileChannel.open(p,StandardOpenOption.WRITE)){c.force(true);}
            // Directory fsync is supported on Unix, not all Windows file systems.
            try(FileChannel c=FileChannel.open(p.getParent(),StandardOpenOption.READ)){c.force(true);}
            catch(UnsupportedOperationException|AccessDeniedException ignored){}
            if(!read(p).equals(n))throw new IOException("Repair journal read-back failed");
        }finally{Files.deleteIfExists(temp);}
    }
}
