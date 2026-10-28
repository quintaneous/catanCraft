package com.quin.catancraft.world;

import com.quin.catancraft.data.NationData;
import com.quin.catancraft.economy.EconomyCost;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Socket transaction. Reservation and ownership receipts share the durable economy save. */
public final class DefenseTransaction {
    public enum Action { STATUS, APPLY, RESUME, ROLLBACK }
    public interface Environment {
        ServerLevel level();Path file();DefenseRevisions.Plan plan();CompoundTag identity();EconomyCost cost();NationData nation();
        String receipt();void receipt(String value);String owned();void owned(String value);
        default BlockState readState(DefenseRevisions.Read read) throws IOException {return read.state();}
        void requirements(boolean rollback) throws IOException;void allow(BlockPos pos) throws IOException;void save() throws IOException;
    }
    public interface Fault {void at(String stage,int writes) throws IOException;}
    public record Result(String message,int writes){}
    private static final int FLAGS=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS;
    private DefenseTransaction(){}
    static CompoundTag cost(EconomyCost c) throws IOException {
        if(c==null)throw new IOException("Defense price schedule requires explicit approval");CompoundTag n=new CompoundTag();n.putLong("money",c.money());
        for(var e:c.resources().entrySet())n.putLong(e.getKey().id(),e.getValue());return n;
    }
    private static String safe(String s){return s==null?"":s;}
    public static Result run(Environment e,Action a) throws IOException{return run(e,a,(s,n)->{});}
    public static Result run(Environment e,Action action,Fault fault) throws IOException {
        var p=e.plan();boolean exists=Files.exists(e.file(),LinkOption.NOFOLLOW_LINKS);CompoundTag j=exists?ProducerJournal.read(e.file()):null;
        boolean rollback=action==Action.ROLLBACK;
        if(exists && (j.getInt("schema")!=1 || !j.hasUUID("id") || !j.getCompound("identity").equals(e.identity()) || !j.getString("revision").equals(p.token()) || !j.getCompound("cost").equals(cost(e.cost()))))throw new IOException("Foreign defense journal/price/source identity");
        String phase=exists?j.getString("phase"):"NONE";
        if(!Set.of("NONE","PREPARED","RESERVED","APPLYING","COMMITTED","ROLLING_BACK","ROLLED_BACK").contains(phase))throw new IOException("Unknown defense journal phase");
        String id=exists?j.getUUID("id").toString():"",receipt=safe(e.receipt());
        String previous=exists?j.getString("previousReceipt"):receipt;
        boolean reserved=exists && receipt.equals(id+":reserved"),paid=exists && receipt.equals(id+":paid"),refunded=exists && receipt.equals(id+":refunded");
        if(exists && !reserved&&!paid&&!refunded&&!receipt.equals(previous))throw new IOException("Defense receipt superseded");
        if(paid!=p.token().equals(e.owned()) || (!paid && !safe(e.owned()).isEmpty()))throw new IOException("Defense owned marker/receipt disagree");
        if(phase.equals("COMMITTED")&&!paid || phase.equals("ROLLED_BACK")&&!refunded)throw new IOException("Defense phase/receipt disagree");
        e.requirements(rollback);
        Map<BlockPos,BlockState> readStates=new HashMap<>();Map<BlockPos,CompoundTag> readNbt=new HashMap<>();
        for(var r:p.reads()){
            if(!e.level().hasChunkAt(r.pos()))throw new PreparationDeferred("Defense read-only chunk unloaded");
            BlockState expected=e.readState(r);CompoundTag actual=nbt(e.level(),r.pos()),canonical=defaultNbt(r.pos(),expected);
            if(!e.level().getBlockState(r.pos()).equals(expected) || !actual.getString("id").equals(canonical.getString("id")))throw new IOException("Defense read-only condition changed at "+r.pos());
            readStates.put(r.pos(),expected);readNbt.put(r.pos(),actual);
        }
        ListTag cells=exists?j.getList("cells",Tag.TAG_COMPOUND):new ListTag();if(exists&&cells.size()!=p.cells().size())throw new IOException("Incomplete defense snapshot");
        List<BlockState> live=new ArrayList<>();List<ConstructionHazards.Write> writes=new ArrayList<>();
        for(int i=0;i<p.cells().size();i++){
            var c=p.cells().get(i);e.allow(c.pos());if(!e.level().hasChunkAt(c.pos()))throw new PreparationDeferred("Defense chunk unloaded");
            CompoundTag tag=exists?cells.getCompound(i):new CompoundTag();
            CompoundTag expectedNbt=defaultNbt(c.pos(),c.after());
            if(exists && (tag.getLong("pos")!=c.pos().asLong() || !tag.getCompound("before").equals(NbtUtils.writeBlockState(c.before())) || !tag.getCompound("after").equals(NbtUtils.writeBlockState(c.after())) || !tag.getCompound("afterNbt").equals(expectedNbt) || !tag.getCompound("beforeNbt").isEmpty()))throw new IOException("Defense snapshot contract changed");
            BlockState state=e.level().getBlockState(c.pos());boolean old=state.equals(c.before()),next=state.equals(c.after());
            boolean pending=exists&&!phase.equals("COMMITTED")&&!phase.equals("ROLLED_BACK");
            if(!(pending?(old||next):(paid?next:old)))throw new IOException("Defense source/after drift at "+c.pos());
            CompoundTag nbt=nbt(e.level(),c.pos());
            boolean acceptedOwnedInventory=paid&&phase.equals("COMMITTED")&&!rollback&&next&&nbt.getString("id").equals(expectedNbt.getString("id"));
            if(!acceptedOwnedInventory&&!nbt.equals(next?expectedNbt:new CompoundTag()))throw new IOException("Defense block entity was modified at "+c.pos());
            live.add(state);BlockState target=rollback?c.before():c.after();if(!state.equals(target))writes.add(new ConstructionHazards.Write(c.pos(),target));
            if(!exists){tag.putLong("pos",c.pos().asLong());tag.put("before",NbtUtils.writeBlockState(state));tag.put("beforeNbt",nbt.copy());tag.put("after",NbtUtils.writeBlockState(c.after()));tag.put("afterNbt",expectedNbt);cells.add(tag);}
        }
        if(action==Action.STATUS)return new Result("Defense "+p.city()+"/"+p.socket()+": "+phase+"; "+p.cells().size()+" writes and "+p.reads().size()+" read conditions checked; read-only",0);
        if(!exists&&action!=Action.APPLY)throw new IOException("No defense transaction to recover");
        if(exists&&action==Action.APPLY&&!phase.equals("COMMITTED"))throw new IOException("Retained defense history: use resume/rollback");
        if(phase.equals("COMMITTED")&&!rollback)return new Result("Owned defense verified; zero writes and zero charge",0);
        if(phase.equals("ROLLED_BACK")){if(rollback)return new Result("Defense already rolled back; zero writes/refund",0);throw new IOException("Rolled-back defense history retained");}
        if(phase.equals("ROLLING_BACK")&&!rollback)throw new IOException("Finish pending defense rollback");
        if(rollback)Collections.reverse(writes);
        var guard=ConstructionHazards.inspect(e.level(),writes);
        if(!exists){
            if(!e.cost().canAfford(e.nation()))throw new IOException("Cannot afford defense: "+e.cost().describe());e.save();
            j=new CompoundTag();j.putInt("schema",1);j.putUUID("id",UUID.randomUUID());j.put("identity",e.identity().copy());j.putString("revision",p.token());j.put("cost",cost(e.cost()));j.putString("previousReceipt",previous);j.put("cells",cells);j.putString("phase","PREPARED");ProducerJournal.write(e.file(),j,false);id=j.getUUID("id").toString();fault.at("prepared",0);
        }
        if(!rollback&&!reserved&&!paid){
            e.requirements(false);guard.verify();verifyReads(e,readStates,readNbt);verifySource(e,p,live);
            if(!j.getCompound("identity").equals(e.identity()) || !safe(e.receipt()).equals(previous))throw new IOException("Ambiguous defense reservation");
            if(!e.cost().canAfford(e.nation()))throw new PreparationDeferred("Original defense price is outstanding: "+e.cost().describe());
            e.cost().charge(e.nation());e.receipt(id+":reserved");e.save();reserved=true;fault.at("reserved",0);
        }
        e.requirements(rollback);guard.verify();verifyReads(e,readStates,readNbt);verifySource(e,p,live);
        advance(e.file(),j,rollback?"ROLLING_BACK":"APPLYING");int count=0;
        for(int step=0;step<p.cells().size();step++){
            int i=rollback?p.cells().size()-1-step:step;var c=p.cells().get(i);BlockState target=rollback?c.before():c.after();
            e.requirements(rollback);e.allow(c.pos());guard.verify();if(!j.getCompound("identity").equals(e.identity()))throw new IOException("Defense identity changed during writes");
            if(!e.level().getBlockState(c.pos()).equals(live.get(i)))throw new IOException("Defense changed after preflight");
            if(!live.get(i).equals(target)){
                if(!e.level().setBlock(c.pos(),target,FLAGS))throw new IOException("Defense write refused");
                CompoundTag n=cells.getCompound(i).getCompound(rollback?"beforeNbt":"afterNbt");BlockEntity be=e.level().getBlockEntity(c.pos());if(be!=null&&!n.isEmpty()){be.load(n.copy());be.setChanged();}count++;fault.at(rollback?"rollback_write":"write",count);
            }
        }
        for(var c:p.cells()){
            BlockState target=rollback?c.before():c.after();
            if(!e.level().getBlockState(c.pos()).equals(target) || !nbt(e.level(),c.pos()).equals(rollback?new CompoundTag():defaultNbt(c.pos(),c.after())))throw new IOException("Defense final state/NBT failed");
            if(!target.canSurvive(e.level(),c.pos()))throw new IOException("Defense final support failed at "+c.pos());
        }
        verifyReads(e,readStates,readNbt);
        guard.verify();e.save();fault.at("physical_saved",count);
        e.requirements(rollback);guard.verify();verifyReads(e,readStates,readNbt);verifyFinal(e,p,rollback);if(!j.getCompound("identity").equals(e.identity()))throw new IOException("Defense identity changed before settlement");
        String expectedReceipt=paid?id+":paid":reserved?id+":reserved":refunded?id+":refunded":previous;
        if(!safe(e.receipt()).equals(expectedReceipt))throw new IOException("Defense payment receipt changed before settlement");
        if(rollback){
            if(!refunded&&(reserved||paid)){
                try{Math.addExact(e.nation().treasury(),e.cost().money());for(var v:e.cost().resources().entrySet())Math.addExact(e.nation().resource(v.getKey()),v.getValue());}catch(ArithmeticException x){throw new IOException("Refund overflow",x);}
                e.nation().addTreasury(e.cost().money());e.cost().resources().forEach((t,n)->e.nation().addResource(t,n));
            }
            e.owned(null);e.receipt(id+":refunded");
        }else {e.owned(p.token());e.receipt(id+":paid");}
        e.save();fault.at("ledger_saved",count);guard.verify();e.requirements(rollback);verifyReads(e,readStates,readNbt);verifyFinal(e,p,rollback);if(!safe(e.receipt()).equals(id+(rollback?":refunded":":paid")) || !safe(e.owned()).equals(rollback?"":p.token()))throw new IOException("Defense settled receipt/ownership changed");advance(e.file(),j,rollback?"ROLLED_BACK":"COMMITTED");return new Result("Defense "+(rollback?"rollback/refund":"purchase")+" completed; "+count+" sparse writes",count);
    }
    private static void verifyFinal(Environment e,DefenseRevisions.Plan plan,boolean rollback) throws IOException {
        for(var cell:plan.cells()){
            BlockState target=rollback?cell.before():cell.after();
            if(!e.level().hasChunkAt(cell.pos()) || !e.level().getBlockState(cell.pos()).equals(target) || !nbt(e.level(),cell.pos()).equals(rollback?new CompoundTag():defaultNbt(cell.pos(),target)))throw new IOException("Defense final world/NBT changed after save");
        }
    }
    private static void verifyReads(Environment e,Map<BlockPos,BlockState> states,Map<BlockPos,CompoundTag> tags) throws IOException {
        for(var entry:states.entrySet())if(!e.level().hasChunkAt(entry.getKey()) || !e.level().getBlockState(entry.getKey()).equals(entry.getValue()) || !nbt(e.level(),entry.getKey()).equals(tags.get(entry.getKey())))throw new IOException("Defense preserved read state/NBT changed");
    }
    private static void verifySource(Environment e,DefenseRevisions.Plan p,List<BlockState> live) throws IOException {
        for(int i=0;i<p.cells().size();i++)if(!e.level().hasChunkAt(p.cells().get(i).pos()) || !e.level().getBlockState(p.cells().get(i).pos()).equals(live.get(i)) || !nbt(e.level(),p.cells().get(i).pos()).equals(defaultNbt(p.cells().get(i).pos(),live.get(i))))throw new IOException("Defense changed after saved preflight");
    }
    private static CompoundTag defaultNbt(BlockPos p,BlockState s) throws IOException {
        if(!s.hasBlockEntity())return new CompoundTag();if(!(s.getBlock() instanceof EntityBlock b))throw new IOException("Unknown defense block entity");BlockEntity be=b.newBlockEntity(p,s);if(be==null)throw new IOException("Missing defense block entity factory");return be.saveWithFullMetadata();
    }
    private static CompoundTag nbt(ServerLevel l,BlockPos p){BlockEntity b=l.getBlockEntity(p);return b==null?new CompoundTag():b.saveWithFullMetadata().copy();}
    private static void advance(Path path,CompoundTag j,String phase) throws IOException {if(!ProducerJournal.read(path).equals(j))throw new IOException("Defense journal changed concurrently");j.putString("phase",phase);ProducerJournal.write(path,j,true);}
}
