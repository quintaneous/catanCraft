package com.quin.catancraft.world;

import com.quin.catancraft.data.*;
import com.quin.catancraft.economy.*;
import com.quin.catancraft.map.MapAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Grouped producer transaction: all plots preflight, durable sparse before-image, then one charge.
 * Completion receipt, paid level and visual tokens live in the SAME CatanSavedData save.
 * Recovery reconciles that receipt, never guesses payment from the current balance.
 */
final class ProducerTransaction {
    static final class FreshOccupancyRefusal extends IOException {
        FreshOccupancyRefusal(String message){super(message);}
    }
    enum Action { STATUS, APPLY, RESUME, ROLLBACK }
    enum Phase { PREPARED, APPLYING, PHYSICAL_COMPLETE, COMMITTED, ROLLING_BACK, ROLLED_BACK }
    record Plot(String family,int source,int target,MapAnchor anchor) {}
    record Spec(int paidBefore,int paidAfter,List<Plot> plots) {
        Spec { plots=List.copyOf(plots); }
        EconomyCost cost() { return paidAfter==paidBefore?new EconomyCost(0,Map.of()):EconomyCatalog.producerUpgradeCost(paidBefore); }
    }
    interface Environment {
        ServerLevel level(); Path file(); Spec spec(); CompoundTag identity();
        NationData nation(); TerritoryData territory();
        String token(String family); void token(String family,String value);
        String receipt(); void receipt(String value);
        CompoundTag ledger(); void dirty(); void save() throws IOException;
        void allowWrite(BlockPos position) throws IOException;
    }
    interface Fault { void at(String stage,int writes) throws IOException; }
    record Result(String message,int writes) {}
    private record Cell(String family,int index,BlockPos pos,BlockState old,BlockState next) {}
    private static final int FLAGS=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS;
    private ProducerTransaction() {}
    static Result run(Environment e,Action action) throws IOException { return run(e,action,(s,n)->{}); }
    static Phase phase(CompoundTag j) throws IOException {
        try {return Phase.valueOf(j.getString("phase"));}catch(Exception x){throw new IOException("Unknown producer journal phase",x);}
    }
    static CompoundTag describe(Spec s) throws IOException {
        if(s.paidBefore()<1 || s.paidBefore()>5 || s.paidAfter()<s.paidBefore() || s.paidAfter()>5
            || s.paidAfter()>s.paidBefore()+1 || s.plots().isEmpty())throw new IOException("Invalid producer upgrade");
        CompoundTag n=new CompoundTag();n.putInt("paidBefore",s.paidBefore());n.putInt("paidAfter",s.paidAfter());
        ListTag plots=new ListTag();Set<String> families=new HashSet<>();
        for(Plot p:s.plots()) {
            if(!families.add(p.family()) || !ProducerRevisions.managed(p.family()) || p.source()<1 || p.source()>s.paidBefore()
                || p.target()!=s.paidAfter())throw new IOException("Invalid producer selection");
            CompoundTag t=new CompoundTag();t.putString("family",p.family());t.putInt("source",p.source());t.putInt("target",p.target());
            t.putIntArray("anchor",new int[]{p.anchor().x(),p.anchor().y(),p.anchor().z(),p.anchor().rotation()});
            t.putString("sourceHash",ProducerRevisions.hash(p.family(),p.source()));t.putString("targetHash",ProducerRevisions.hash(p.family(),p.target()));plots.add(t);
        }
        n.put("plots",plots);n.put("cost",costTag(s.cost()));return n;
    }
    private static CompoundTag costTag(EconomyCost cost) throws IOException {
        if(cost==null)throw new IOException("No producer upgrade at this level");
        CompoundTag n=new CompoundTag();n.putLong("money",cost.money());
        for(var v:cost.resources().entrySet())n.putLong(v.getKey().id(),v.getValue());return n;
    }
    static Result run(Environment e,Action action,Fault fault) throws IOException {
        Spec spec=e.spec();CompoundTag description=describe(spec);
        List<Cell> cells=new ArrayList<>();List<Integer> mask=new ArrayList<>();List<AABB> boxes=new ArrayList<>();Set<BlockPos> unique=new HashSet<>();
        for(Plot plot:spec.plots()) {
            var old=ProducerRevisions.plan(plot.family(),plot.source(),plot.anchor());
            var next=ProducerRevisions.plan(plot.family(),plot.target(),plot.anchor());
            boxes.add(TownHallRepairTransaction.bounds(old));
            for(int i=0;i<old.size();i++) {
                var a=old.get(i);var b=next.get(i);
                if(!a.position().equals(b.position()) || !unique.add(a.position()))throw new IOException("Overlapping producer envelopes");
                if(!a.state().equals(b.state())) {
                    if(ProducerRevisions.protectedCell(a.state()) || ProducerRevisions.protectedCell(b.state())
                        || a.state().hasBlockEntity() || b.state().hasBlockEntity())throw new IOException("Delta touches protected inventory/cultivation/fluid");
                    e.allowWrite(a.position());mask.add(cells.size());
                }
                cells.add(new Cell(plot.family(),i,a.position(),a.state(),b.state()));
            }
        }
        boolean exists=Files.exists(e.file(),LinkOption.NOFOLLOW_LINKS);
        CompoundTag j=exists?ProducerJournal.read(e.file()):null;Phase phase=exists?phase(j):null;
        if(exists && (j.getInt("schema")!=1 || !j.hasUUID("id") || !j.getCompound("identity").equals(e.identity())
            || !j.getCompound("spec").equals(description)))throw new IOException("Foreign producer journal or changed contract");
        boolean pending=phase!=null && phase!=Phase.COMMITTED && phase!=Phase.ROLLED_BACK;
        String previous=exists?j.getString("previousReceipt"):safe(e.receipt());
        String paid=exists?j.getUUID("id")+":paid":"",rolled=exists?j.getUUID("id")+":rolled_back":"";
        boolean paidReceipt=exists && paid.equals(e.receipt()),rolledReceipt=exists && rolled.equals(e.receipt());
        if(exists && !paidReceipt && !rolledReceipt && !previous.equals(safe(e.receipt())))
            throw new IOException("Producer transaction was superseded; refusing old recovery");
        int expectedPaid=paidReceipt?spec.paidAfter():spec.paidBefore();
        if(e.territory().producerLevel()!=expectedPaid)throw new IOException("Paid producer level and transaction receipt disagree");
        if(phase==Phase.COMMITTED && !paidReceipt || phase==Phase.ROLLED_BACK && !rolledReceipt)
            throw new IOException("Producer journal completion disagrees with saved receipt");
        for(Plot p:spec.plots()) {
            String expected=ProducerRevisions.token(p.family(),paidReceipt?p.target():p.source());
            if(!expected.equals(e.token(p.family())))throw new IOException("Producer visual token mismatch for "+p.family());
        }
        ListTag saved=exists?j.getList("blocks",Tag.TAG_COMPOUND):new ListTag();
        if(exists && saved.size()!=mask.size())throw new IOException("Incomplete producer before-image");
        Map<Integer,BlockState> before=new HashMap<>();Set<Integer> changed=new HashSet<>(mask);
        for(int k=0;k<mask.size();k++)if(exists) {
            int index=mask.get(k);Cell c=cells.get(index);CompoundTag tag=saved.getCompound(k);
            CompoundTag stateTag=tag.getCompound("before");BlockState state=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),stateTag);
            if(tag.getInt("index")!=index || !NbtUtils.writeBlockState(state).equals(stateTag)
                || !ProducerRevisions.matches(state,c.old()) || !tag.getCompound("after").equals(NbtUtils.writeBlockState(c.next())))
                throw new IOException("Untrusted producer before-image at "+c.pos());
            before.put(index,state);
        }
        BlockState[] live=new BlockState[cells.size()];Map<BlockPos,CompoundTag> inventories=new HashMap<>();
        for(int i=0;i<cells.size();i++) {
            Cell c=cells.get(i);if(!e.level().hasChunkAt(c.pos()))throw new IOException("Unloaded producer chunk at "+c.pos());
            BlockState state=e.level().getBlockState(c.pos());live[i]=state;
            boolean valid;
            if(changed.contains(i) && pending) valid=ProducerRevisions.matches(state,before.get(i))||ProducerRevisions.matches(state,c.next());
            else valid=ProducerRevisions.matches(state,paidReceipt?c.next():c.old());
            if(!valid)throw new IOException("Modified producer cell at "+c.pos()+": "+state);
            BlockEntity be=e.level().getBlockEntity(c.pos());
            if(c.old().is(Blocks.BARREL)) {
                if(!(be instanceof BarrelBlockEntity))throw new IOException("Missing original barrel at "+c.pos());
                inventories.put(c.pos(),be.saveWithFullMetadata().copy());
            } else if(be!=null)throw new IOException("Foreign block entity at "+c.pos());
        }
        if(action==Action.STATUS)return new Result("Producer paid L"+e.territory().producerLevel()+"; "+cells.size()+
            " cells checked; "+mask.size()+" design changes; "+(phase==null?"no transaction":phase)+"; read-only.",0);
        if(action==Action.APPLY && pending)throw new IOException("Incomplete producer transaction; use resume or rollback");
        if(action!=Action.APPLY && !exists)throw new IOException("No producer transaction to recover");
        if(action!=Action.ROLLBACK && phase==Phase.COMMITTED)return new Result("Already completed: verified producer visuals, zero writes and zero charge.",0);
        if(phase==Phase.ROLLED_BACK) {
            if(action==Action.ROLLBACK)return new Result("Already rolled back: verified source, zero writes and zero refund.",0);
            throw new IOException("Rolled-back producer journal retained; do not delete or replay it");
        }
        if(action!=Action.ROLLBACK && phase==Phase.ROLLING_BACK)throw new IOException("Rollback started; finish rollback");
        if(!exists && mask.isEmpty() && spec.paidBefore()==spec.paidAfter())return new Result("Producer visuals already match paid level; zero writes and zero charge.",0);
        if(!paidReceipt && action!=Action.ROLLBACK && !spec.cost().canAfford(e.nation()))throw new IOException("Cannot afford producer upgrade: "+spec.cost().describe());
        boolean rollback=action==Action.ROLLBACK;
        List<ConstructionHazards.Write> hazardWrites=new ArrayList<>();
        for(int k=0;k<mask.size();k++) {
            int index=mask.get(rollback?mask.size()-1-k:k);Cell c=cells.get(index);
            hazardWrites.add(new ConstructionHazards.Write(c.pos(),rollback?before.get(index):c.next()));
        }
        ConstructionHazards.Guard entities;
        try { entities=ConstructionHazards.inspect(e.level(),hazardWrites); }
        catch(IOException hazard) {
            if(!exists && hazard.getMessage().startsWith("Construction hazard:"))
                throw new FreshOccupancyRefusal(hazard.getMessage()+"; no upgrade writes or charge occurred.");
            throw hazard;
        }
        // Guard shell readback and current inventories, including on recovery after legitimate use.
        Map<BlockPos,BlockState> shell=new HashMap<>();Map<BlockPos,CompoundTag> shellNbt=new HashMap<>();
        for(AABB box:boxes) {
            for(BlockPos b:BlockPos.betweenClosed((int)box.minX-1,(int)box.minY-1,(int)box.minZ-1,(int)box.maxX,(int)box.maxY,(int)box.maxZ)) {
                BlockPos p=b.immutable();if(unique.contains(p))continue;
                if(!e.level().hasChunkAt(p))throw new IOException("Unloaded producer boundary");
                shell.put(p,e.level().getBlockState(p));BlockEntity be=e.level().getBlockEntity(p);
                if(be!=null)shellNbt.put(p,be.saveWithFullMetadata().copy());
            }
        }
        Set<UUID> items=new HashSet<>();for(AABB box:boxes)for(ItemEntity item:e.level().getEntitiesOfClass(ItemEntity.class,box.inflate(2)))items.add(item.getUUID());
        CompoundTag ledger=e.ledger().copy();
        if(!exists) {
            e.save(); // Flush the unmodified source before durable ownership of any block write.
            if(!e.ledger().equals(ledger))throw new IOException("Ledger changed during source save");
            j=new CompoundTag();j.putInt("schema",1);j.putUUID("id",UUID.randomUUID());j.put("identity",e.identity().copy());
            j.put("spec",description);j.putString("previousReceipt",previous);j.put("ledgerBefore",ledger.copy());
            ListTag nbt=new ListTag();for(var entry:inventories.entrySet()) {
                CompoundTag n=new CompoundTag();n.putLong("pos",entry.getKey().asLong());n.put("actualNbt",entry.getValue().copy());nbt.add(n);
            }
            j.put("preservedInventories",nbt);
            for(int index:mask) {
                Cell c=cells.get(index);CompoundTag n=new CompoundTag();n.putInt("index",index);
                n.put("before",NbtUtils.writeBlockState(live[index]));n.put("after",NbtUtils.writeBlockState(c.next()));saved.add(n);before.put(index,live[index]);
            }
            j.put("blocks",saved);j.putString("phase",Phase.PREPARED.name());ProducerJournal.write(e.file(),j,false);fault.at("prepared",0);
        }
        entities.verify();
        advance(e.file(),j,rollback?Phase.ROLLING_BACK:Phase.APPLYING);
        int writes=0;
        for(int k=0;k<mask.size();k++) {
            int index=mask.get(rollback?mask.size()-1-k:k);Cell c=cells.get(index);
            BlockState wanted=rollback?before.get(index):c.next();
            if(!e.level().getBlockState(c.pos()).equals(live[index]))throw new IOException("Producer changed after preflight");
            if(!live[index].equals(wanted)) {
                if(!e.level().setBlock(c.pos(),wanted,FLAGS))throw new IOException("Producer block write rejected");
                live[index]=wanted;writes++;fault.at(rollback?"rollback_write":"write",writes);
            }
        }
        entities.verify();
        verify(e,cells,live,inventories,shell,shellNbt,ledger,items,boxes);
        advance(e.file(),j,rollback?Phase.ROLLING_BACK:Phase.PHYSICAL_COMPLETE);
        e.save();entities.verify();fault.at("physical_saved",writes);
        // This method and its receipt are the only payment path, also used by native tests.
        settle(e,j,rollback);e.dirty();e.save();entities.verify();fault.at("ledger_saved",writes);
        verify(e,cells,live,inventories,shell,shellNbt,e.ledger(),items,boxes);
        advance(e.file(),j,rollback?Phase.ROLLED_BACK:Phase.COMMITTED);fault.at("committed",writes);
        return new Result((rollback?"Producer rollback":"Producer upgrade/retrofit")+" completed: "+writes+
            " block changes; paid L"+e.territory().producerLevel()+"; "+(spec.paidBefore()==spec.paidAfter()?"zero charge":"one territory charge, receipt verified")+".",writes);
    }
    private static void settle(Environment e,CompoundTag j,boolean rollback) throws IOException {
        String paid=j.getUUID("id")+":paid",rolled=j.getUUID("id")+":rolled_back",previous=j.getString("previousReceipt");
        Spec spec=e.spec();EconomyCost cost=spec.cost();String receipt=safe(e.receipt());
        if(rollback) {
            if(receipt.equals(rolled))return;
            if(receipt.equals(paid)) {
                if(e.territory().producerLevel()!=spec.paidAfter())throw new IOException("Cannot refund changed paid level");
                // Preflight all overflows before changing any amount.
                try {Math.addExact(e.nation().treasury(),cost.money());for(var v:cost.resources().entrySet())Math.addExact(e.nation().resource(v.getKey()),v.getValue());}
                catch(ArithmeticException x){throw new IOException("Refund would overflow",x);}
                e.nation().addTreasury(cost.money());cost.resources().forEach((type,amount)->e.nation().addResource(type,amount));
            } else if(!receipt.equals(previous))throw new IOException("Unrecognized payment receipt");
            e.territory().setProducerLevel(spec.paidBefore());
            for(Plot p:spec.plots())e.token(p.family(),ProducerRevisions.token(p.family(),p.source()));
            e.receipt(rolled);
        } else {
            if(receipt.equals(paid))return;
            if(!receipt.equals(previous) || e.territory().producerLevel()!=spec.paidBefore())throw new IOException("Ambiguous producer charge");
            if(!cost.canAfford(e.nation()))throw new IOException("Insufficient funds at completion; rollback remains available");
            cost.charge(e.nation());e.territory().setProducerLevel(spec.paidAfter());
            for(Plot p:spec.plots())e.token(p.family(),ProducerRevisions.token(p.family(),p.target()));
            e.receipt(paid);
        }
    }
    private static String safe(String value){return value==null?"":value;}
    private static void advance(Path p,CompoundTag j,Phase next) throws IOException {
        if(!ProducerJournal.read(p).equals(j))throw new IOException("Producer journal changed concurrently");
        j.putString("phase",next.name());ProducerJournal.write(p,j,true);
    }
    private static void verify(Environment e,List<Cell> cells,BlockState[] states,Map<BlockPos,CompoundTag> inventories,
            Map<BlockPos,BlockState> shell,Map<BlockPos,CompoundTag> shellNbt,CompoundTag ledger,Set<UUID> items,List<AABB> boxes) throws IOException {
        for(int i=0;i<cells.size();i++)if(!states[i].equals(e.level().getBlockState(cells.get(i).pos())))throw new IOException("Producer post-write mismatch");
        for(var n:inventories.entrySet()) {
            BlockEntity b=e.level().getBlockEntity(n.getKey());if(b==null || !n.getValue().equals(b.saveWithFullMetadata()))throw new IOException("Producer inventory changed");
        }
        for(var c:shell.entrySet()) {
            BlockEntity b=e.level().getBlockEntity(c.getKey());
            if(!e.level().getBlockState(c.getKey()).equals(c.getValue()) || !Objects.equals(shellNbt.get(c.getKey()),b==null?null:b.saveWithFullMetadata()))throw new IOException("Outside producer envelope changed");
        }
        if(!ledger.equals(e.ledger()))throw new IOException("Unrelated ledger change during producer writes");
        for(AABB box:boxes)for(ItemEntity item:e.level().getEntitiesOfClass(ItemEntity.class,box.inflate(2)))
            if(!items.contains(item.getUUID()))throw new IOException("Producer upgrade generated item drops");
    }
}
