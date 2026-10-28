package com.quin.catancraft.world;

import com.quin.catancraft.data.CatanSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Completes an existing paid construction, never creates a purchase. The original
 * purchase journal is immutable during recovery. A sealed, hash-bound companion
 * records recovery; the charge receipt lives in the SAME SavedData as the debit.
 */
final class PaidConstructionRecovery {
    private PaidConstructionRecovery() {}
    record Cell(BlockPos pos, List<BlockState> allowed, BlockState target, boolean emptyLecternRemoval) {
        Cell { pos=pos.immutable(); allowed=List.copyOf(allowed); }
        Cell(BlockPos pos,List<BlockState> allowed,BlockState target){this(pos,allowed,target,false);}
    }
    interface Environment {
        ServerLevel level(); Path original(); byte[] originalBytes();
        CompoundTag identity(); List<Cell> cells() throws IOException;
        void authorize() throws IOException;
        boolean paid() throws IOException;
        void checkPayment() throws IOException;
        void settle() throws IOException;
        CompoundTag ledger(); void save() throws IOException;
    }
    interface Fault { void at(String phase,int writes) throws IOException; }
    record Result(int writes,boolean alreadyComplete) {}
    static final int FLAGS=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS;
    static String receiptKey(UUID id){return "paid_construction:"+id;}
    static void receipt(CatanSavedData data,UUID id){data.setPlacedMapAsset(receiptKey(id),id+":paid");}
    static boolean paid(CatanSavedData data,UUID id,boolean targetSaved,boolean sourceSaved,boolean required)throws IOException {
        String receipt=data.placedMapAsset(receiptKey(id));
        if(receipt!=null&&!receipt.equals(id+":paid"))throw new IOException("Unrecognized construction receipt; preserve history");
        if(targetSaved&&sourceSaved||!targetSaved&&!sourceSaved)throw new IOException("Ambiguous purchased level/building; recovery blocked");
        if(receipt!=null&&!targetSaved)throw new IOException("Payment receipt disagrees with purchased state");
        // Legacy charge and purchased marker were written in the same SavedData.
        // Never infer debit from today's balance, which may include later production.
        if(targetSaved&&required&&receipt==null)throw new IOException("Purchased state lacks required transaction receipt");
        return targetSaved;
    }
    static Path companion(Path original){return original.resolveSibling(original.getFileName()+".recovery-v1.dat");}
    static byte[] readBounded(Path file,int limit)throws IOException {
        if(Files.isSymbolicLink(file)||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>limit)
            throw new IOException("Missing, linked or oversized construction history: "+file.getFileName());
        byte[] bytes=Files.readAllBytes(file);if(bytes.length>limit)throw new IOException("Construction history grew during read");return bytes;
    }
    static void unchanged(Environment e)throws IOException {
        if(!Arrays.equals(e.originalBytes(),readBounded(e.original(),8*1024*1024)))
            throw new IOException("Original construction journal changed; refusal without replacement");
    }
    static boolean complete(Path original,byte[] bytes)throws IOException {
        Path p=companion(original);if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))return false;
        CompoundTag n=ProducerJournal.read(p);
        if(n.getInt("schema")!=1||!n.getString("originalSHA256").equals(TownHallRevisions.sha(bytes)))
            throw new IOException("Recovery companion does not match original purchase");
        String phase=n.getString("phase");
        if(!Set.of("PREPARED","PHYSICAL_COMPLETE","COMMITTED").contains(phase))throw new IOException("Unknown paid-construction recovery phase");
        return phase.equals("COMMITTED");
    }
    static Result resume(Environment e)throws IOException{return resume(e,(s,n)->{});}
    static Result resume(Environment e,Fault fault)throws IOException {
        if(!e.level().getServer().isSameThread())throw new IOException("Recovery must run on server thread");
        e.authorize();unchanged(e);
        Path file=companion(e.original());CompoundTag identity=e.identity().copy();
        String digest=TownHallRevisions.sha(e.originalBytes());
        CompoundTag j=Files.exists(file,LinkOption.NOFOLLOW_LINKS)?ProducerJournal.read(file):null;
        if(j!=null&&(j.getInt("schema")!=1||!j.getCompound("identity").equals(identity)||!j.getString("originalSHA256").equals(digest)))
            throw new IOException("Foreign recovery companion; original history preserved");
        if(j!=null&&!Set.of("PREPARED","PHYSICAL_COMPLETE","COMMITTED").contains(j.getString("phase")))
            throw new IOException("Unknown recovery phase");
        e.checkPayment();
        if(j!=null&&j.getString("phase").equals("COMMITTED")) {
            if(!e.paid())throw new IOException("Completed recovery disagrees with saved purchase");
            return new Result(0,true); // Never restore an old tier over later legitimate use.
        }
        List<Cell> cells=e.cells();if(cells.isEmpty()||cells.size()>250_000)throw new IOException("Invalid bounded recovery context");
        Map<BlockPos,BlockState> observed=new LinkedHashMap<>(),shell=new LinkedHashMap<>();
        Map<BlockPos,CompoundTag> preserved=new LinkedHashMap<>();List<ConstructionHazards.Write> mutations=new ArrayList<>();
        for(Cell c:cells){
            if(observed.containsKey(c.pos()))throw new IOException("Duplicate recovery cell");
            if(!e.level().hasChunkAt(c.pos()))throw new PreparationDeferred("Construction chunks are still loading");
            BlockState live=e.level().getBlockState(c.pos());
            if(!c.allowed().contains(live)||!live.getFluidState().isEmpty())throw new IOException("Construction source conflict at "+c.pos());
            observed.put(c.pos(),live);BlockEntity be=e.level().getBlockEntity(c.pos());
            if(be!=null){
                if(live.equals(c.target()))preserved.put(c.pos(),be.saveWithFullMetadata().copy());
                else if(!c.emptyLecternRemoval()||!(be instanceof LecternBlockEntity lectern)||!lectern.getBook().isEmpty())
                    throw new IOException("Recovery would replace an inventory at "+c.pos());
            }else if(live.hasBlockEntity())throw new IOException("Missing construction block entity at "+c.pos());
            if(!live.equals(c.target()))mutations.add(new ConstructionHazards.Write(c.pos(),c.target()));
        }
        // Snapshot the immediate outside shell, including its actual inventories.
        for(Cell c:cells)for(var direction:net.minecraft.core.Direction.values()){
            BlockPos p=c.pos().relative(direction);if(observed.containsKey(p)||shell.containsKey(p))continue;
            if(!e.level().hasChunkAt(p))throw new PreparationDeferred("Construction boundary is still loading");
            shell.put(p,e.level().getBlockState(p));var be=e.level().getBlockEntity(p);if(be!=null)preserved.put(p,be.saveWithFullMetadata().copy());
        }
        ConstructionHazards.Guard guard;
        try{guard=ConstructionHazards.inspect(e.level(),mutations);}catch(IOException hazard){
            if(hazard.getMessage()!=null&&hazard.getMessage().startsWith("Construction hazard:"))throw new PreparationDeferred(hazard.getMessage());throw hazard;
        }
        CompoundTag beforeLedger=e.ledger().copy();
        if(j==null){
            j=new CompoundTag();j.putInt("schema",1);j.put("identity",identity);j.putString("originalSHA256",digest);j.putString("phase","PREPARED");
            ProducerJournal.write(file,j,false);
        }
        fault.at("prepared",0);e.authorize();int writes=0;
        for(Cell c:cells){
            if(!observed.get(c.pos()).equals(e.level().getBlockState(c.pos())))throw new IOException("Construction changed after preflight at "+c.pos());
            if(!observed.get(c.pos()).equals(c.target())){
                if(!e.level().setBlock(c.pos(),c.target(),FLAGS)||!e.level().getBlockState(c.pos()).equals(c.target()))throw new IOException("Recovery placement refused at "+c.pos());
                observed.put(c.pos(),c.target());writes++;fault.at("write",writes);
            }
        }
        verify(e,observed,shell,preserved);guard.verify();unchanged(e);
        if(!beforeLedger.equals(e.ledger())||!identity.equals(e.identity()))throw new IOException("Ledger/owner changed during recovery");
        advance(file,j,"PHYSICAL_COMPLETE");e.save();fault.at("physical_saved",writes);
        e.authorize();unchanged(e);e.checkPayment();
        if(!beforeLedger.equals(e.ledger()))throw new IOException("Ledger changed during physical save");
        e.settle();fault.at("settled",writes);CompoundTag paidLedger=e.ledger().copy();e.save();fault.at("ledger_saved",writes);
        e.authorize();
        if(!e.paid()||!paidLedger.equals(e.ledger())||!identity.equals(e.identity()))throw new IOException("Construction settlement mismatch");
        verify(e,observed,shell,preserved);guard.verify();unchanged(e);advance(file,j,"COMMITTED");fault.at("committed",writes);
        return new Result(writes,false);
    }
    private static void advance(Path file,CompoundTag j,String phase)throws IOException {
        if(!j.equals(ProducerJournal.read(file)))throw new IOException("Recovery companion changed concurrently");
        j.putString("phase",phase);ProducerJournal.write(file,j,true);
    }
    private static void verify(Environment e,Map<BlockPos,BlockState> inside,Map<BlockPos,BlockState> shell,Map<BlockPos,CompoundTag> nbt)throws IOException {
        for(var states:List.of(inside,shell))for(var c:states.entrySet()){
            if(!c.getValue().equals(e.level().getBlockState(c.getKey())))throw new IOException("Recovery or adjacent-block drift at "+c.getKey());
            if(c.getValue().hasBlockEntity()&&e.level().getBlockEntity(c.getKey())==null)throw new IOException("Missing target inventory at "+c.getKey());
        }
        for(var c:nbt.entrySet()){var be=e.level().getBlockEntity(c.getKey());if(be==null||!c.getValue().equals(be.saveWithFullMetadata()))throw new IOException("Preserved construction inventory changed at "+c.getKey());}
    }
}
