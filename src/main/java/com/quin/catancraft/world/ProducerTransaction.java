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
    record Plot(String family,int source,int target,MapAnchor anchor,boolean sourceR2,boolean targetR2) {
        // Legacy journals/tests retain exactly their original R1 interpretation.
        Plot(String family,int source,int target,MapAnchor anchor){this(family,source,target,anchor,false,false);}
    }
    static String token(Plot p,boolean target)throws IOException {
        int tier=target?p.target():p.source();boolean r2=target?p.targetR2():p.sourceR2();
        return r2?ProducerExteriorR2.token(p.family(),tier):ProducerRevisions.token(p.family(),tier);
    }
    private static String hash(Plot p,boolean target)throws IOException {
        int tier=target?p.target():p.source();return (target?p.targetR2():p.sourceR2())?
            ProducerExteriorR2.schematicHash(p.family(),tier):ProducerRevisions.hash(p.family(),tier);
    }
    private static List<SchematicPlacementService.PlanCell> plan(Plot p,boolean target)throws IOException {
        int tier=target?p.target():p.source();return (target?p.targetR2():p.sourceR2())?
            ProducerExteriorR2.plan(p.family(),tier,p.anchor()):ProducerRevisions.plan(p.family(),tier,p.anchor());
    }
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
        default CompoundTag previousNaturalProof() throws IOException {return new CompoundTag();}
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
                || p.target()!=s.paidAfter() || p.sourceR2()&&!ProducerExteriorR2.hasRevision(p.family(),p.source())
                || p.targetR2()&&!ProducerExteriorR2.hasRevision(p.family(),p.target()))throw new IOException("Invalid producer selection");
            CompoundTag t=new CompoundTag();t.putString("family",p.family());t.putInt("source",p.source());t.putInt("target",p.target());
            t.putIntArray("anchor",new int[]{p.anchor().x(),p.anchor().y(),p.anchor().z(),p.anchor().rotation()});
            t.putString("sourceHash",hash(p,false));t.putString("targetHash",hash(p,true));
            if(p.sourceR2())t.putBoolean("sourceExteriorR2",true);
            if(p.targetR2())t.putBoolean("targetExteriorR2",true);
            plots.add(t);
        }
        n.put("plots",plots);n.put("cost",costTag(s.cost()));return n;
    }
    private static CompoundTag costTag(EconomyCost cost) throws IOException {
        if(cost==null)throw new IOException("No producer upgrade at this level");
        CompoundTag n=new CompoundTag();n.putLong("money",cost.money());
        for(var v:cost.resources().entrySet())n.putLong(v.getKey().id(),v.getValue());return n;
    }
    static Result run(Environment e,Action action,Fault fault) throws IOException {
        Spec spec=e.spec();CompoundTag description=describe(spec),identity=e.identity().copy();
        List<Cell> cells=new ArrayList<>();List<Integer> mask=new ArrayList<>();List<AABB> boxes=new ArrayList<>();Set<BlockPos> unique=new HashSet<>();
        for(Plot plot:spec.plots()) {
            var old=plan(plot,false);
            var next=plan(plot,true);
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
        Map<BlockPos,BlockState> sourceCovers=sourceCoveredGrass(cells);
        // Absence is meaningful: old journals keep their original strict interpretation.
        Set<BlockPos> provedGrass=exists?readSourceCoverProof(j,sourceCovers):sourceCovers.keySet();
        NaturalSoil soil=new NaturalSoil(spec,cells,identity,exists?j:null,exists?null:e.previousNaturalProof(),safe(e.receipt()),0);
        Map<BlockPos,BlockState> qualifiedGrowth=ProducerGrowth53.transaction(spec);
        Map<BlockPos,BlockState> growth=exists?ProducerGrowth53.read(j,qualifiedGrowth):qualifiedGrowth;
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
            String expected=token(p,paidReceipt);
            if(!expected.equals(e.token(p.family())))throw new IOException("Producer visual token mismatch for "+p.family());
        }
        ListTag saved=exists?j.getList("blocks",Tag.TAG_COMPOUND):new ListTag();
        if(exists && saved.size()!=mask.size())throw new IOException("Incomplete producer before-image");
        Map<Integer,BlockState> before=new HashMap<>();Set<Integer> changed=new HashSet<>(mask);
        for(int k=0;k<mask.size();k++)if(exists) {
            int index=mask.get(k);Cell c=cells.get(index);CompoundTag tag=saved.getCompound(k);
            CompoundTag stateTag=tag.getCompound("before");BlockState state=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),stateTag);
            if(tag.getInt("index")!=index || !NbtUtils.writeBlockState(state).equals(stateTag)
                || !soil.beforeMatches(state,c) || !tag.getCompound("after").equals(NbtUtils.writeBlockState(c.next())))
                throw new IOException("Untrusted producer before-image at "+c.pos());
            before.put(index,state);
        }
        BlockState[] live=new BlockState[cells.size()];Map<BlockPos,CompoundTag> inventories=new HashMap<>();
        for(int i=0;i<cells.size();i++) {
            Cell c=cells.get(i);if(!e.level().hasChunkAt(c.pos()))throw new PreparationDeferred("Unloaded producer chunk at "+c.pos());
            BlockState state=e.level().getBlockState(c.pos());live[i]=state;
            boolean valid;
            if(changed.contains(i) && pending) valid=ProducerRevisions.matches(state,before.get(i))||ProducerRevisions.matches(state,c.next());
            else valid=ProducerRevisions.matches(state,paidReceipt?c.next():c.old());
            if(!valid&&soil.enabled)valid=soil.matches(e.level(),c.pos(),state,pending||paidReceipt?c.next():c.old(),pending||paidReceipt)
                ||pending&&soil.matches(e.level(),c.pos(),state,c.old(),true);
            if(!valid&&!soil.enabled&&!changed.contains(i)&&provedGrass.contains(c.pos())&&state.equals(Blocks.DIRT.defaultBlockState()))
                valid=exists||exactSourceCover(e,c.pos(),sourceCovers.get(c.pos()));
            if(!valid&&!changed.contains(i)&&c.old().equals(Blocks.DIRT.defaultBlockState())&&c.next().equals(c.old()))
                valid=ProducerGrowth53.matches(e.level(),c.pos(),state,growth);
            if(!valid)throw new IOException("Modified producer cell at "+c.pos()+": "+state);
            BlockEntity be=e.level().getBlockEntity(c.pos());
            if(c.old().is(Blocks.BARREL)) {
                if(!(be instanceof BarrelBlockEntity))throw new IOException("Missing original barrel at "+c.pos());
                inventories.put(c.pos(),be.saveWithFullMetadata().copy());
            } else if(be!=null)throw new IOException("Foreign block entity at "+c.pos());
        }
        if(exists)soil.observe(e.level());
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
        if(!paidReceipt && action!=Action.ROLLBACK && !spec.cost().canAfford(e.nation())){if(pending)throw new PreparationDeferred("Original producer price is outstanding: "+spec.cost().describe());throw new IOException("Cannot afford producer upgrade: "+spec.cost().describe());}
        boolean rollback=action==Action.ROLLBACK;
        List<ConstructionHazards.Write> hazardWrites=new ArrayList<>();
        for(int k=0;k<mask.size();k++) {
            int index=mask.get(rollback?mask.size()-1-k:k);Cell c=cells.get(index);
            hazardWrites.add(new ConstructionHazards.Write(c.pos(),rollback?before.get(index):c.next()));
        }
        if(spec.plots().stream().anyMatch(p->p.sourceR2()||p.targetR2())) {
            Map<BlockPos,BlockState> finalStates=new HashMap<>();
            for(int i=0;i<cells.size();i++) {
                Cell c=cells.get(i);finalStates.put(c.pos(),changed.contains(i)?(rollback?before.get(i):c.next()):live[i]);
            }
            PlannedWorld.verify(e.level(),finalStates,FLAGS);
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
                if(!e.level().hasChunkAt(p))throw new PreparationDeferred("Unloaded producer boundary");
                shell.put(p,e.level().getBlockState(p));BlockEntity be=e.level().getBlockEntity(p);
                if(be!=null)shellNbt.put(p,be.saveWithFullMetadata().copy());
            }
        }
        Set<UUID> items=new HashSet<>();for(AABB box:boxes)for(ItemEntity item:e.level().getEntitiesOfClass(ItemEntity.class,box.inflate(2)))items.add(item.getUUID());
        CompoundTag ledger=e.ledger().copy();
        if(!exists) {
            soil.capture(e.level(),live);
            e.save(); // Flush the unmodified source before durable ownership of any block write.
            if(!e.ledger().equals(ledger)||!identity.equals(e.identity()))throw new IOException("Ledger/owner changed during source save");
            entities.verify();
            for(var cover:sourceCovers.entrySet())if(!exactSourceCover(e,cover.getKey(),cover.getValue()))
                throw new IOException("Producer source cover changed before intent at "+cover.getKey());
            j=new CompoundTag();j.putInt("schema",1);j.putUUID("id",UUID.randomUUID());j.put("identity",e.identity().copy());
            j.put("spec",description);j.putString("previousReceipt",previous);j.put("ledgerBefore",ledger.copy());
            j.put("sourceCoveredGrassV1",sourceCoverProof(sourceCovers));
            j.put(ProducerGrowth53.FIELD,ProducerGrowth53.describe(qualifiedGrowth));
            soil.persist(j);
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
        advance(e.file(),j,rollback?Phase.ROLLING_BACK:Phase.APPLYING,soil);
        int writes=0;
        for(int k=0;k<mask.size();k++) {
            int index=mask.get(rollback?mask.size()-1-k:k);Cell c=cells.get(index);
            e.allowWrite(c.pos());if(!identity.equals(e.identity()))throw new IOException("Producer owner changed after preflight");
            BlockState wanted=rollback?before.get(index):c.next();
            if(!e.level().getBlockState(c.pos()).equals(live[index]))throw new IOException("Producer changed after preflight");
            if(!live[index].equals(wanted)) {
                if(!e.level().setBlock(c.pos(),wanted,FLAGS))throw new IOException("Producer block write rejected");
                live[index]=wanted;writes++;fault.at(rollback?"rollback_write":"write",writes);
            }
        }
        entities.verify();
        verify(e,cells,live,inventories,shell,shellNbt,ledger,items,boxes);
        soil.observe(e.level());advance(e.file(),j,rollback?Phase.ROLLING_BACK:Phase.PHYSICAL_COMPLETE,soil);
        e.save();entities.verify();
        if(!identity.equals(e.identity())||!ledger.equals(e.ledger()))throw new IOException("Producer identity/ledger changed during physical save");
        fault.at("physical_saved",writes);
        // This method and its receipt are the only payment path, also used by native tests.
        if(!identity.equals(e.identity()))throw new IOException("Producer owner changed before settlement");
        settle(e,j,rollback);e.dirty();CompoundTag settled=e.ledger().copy();e.save();entities.verify();
        if(!identity.equals(e.identity())||!settled.equals(e.ledger()))throw new IOException("Producer settlement changed during save");
        fault.at("ledger_saved",writes);
        verify(e,cells,live,inventories,shell,shellNbt,e.ledger(),items,boxes);
        soil.observe(e.level());advance(e.file(),j,rollback?Phase.ROLLED_BACK:Phase.COMMITTED,soil);fault.at("committed",writes);
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
            for(Plot p:spec.plots())e.token(p.family(),token(p,false));
            e.receipt(rolled);
        } else {
            if(receipt.equals(paid))return;
            if(!receipt.equals(previous) || e.territory().producerLevel()!=spec.paidBefore())throw new IOException("Ambiguous producer charge");
            if(!cost.canAfford(e.nation()))throw new IOException("Insufficient funds at completion; rollback remains available");
            cost.charge(e.nation());e.territory().setProducerLevel(spec.paidAfter());
            for(Plot p:spec.plots())e.token(p.family(),token(p,true));
            e.receipt(paid);
        }
    }
    private static String safe(String value){return value==null?"":value;}
    /** Only source-covered unchanged reads; target-only cover grants no permission. */
    private static Map<BlockPos,BlockState> sourceCoveredGrass(List<Cell> cells) {
        Map<BlockPos,BlockState> source=new HashMap<>();for(Cell c:cells)source.put(c.pos(),c.old());
        Map<BlockPos,BlockState> covers=new HashMap<>();
        for(Cell c:cells)if(c.old().equals(Blocks.GRASS_BLOCK.defaultBlockState())&&c.old().equals(c.next())){
            BlockState cover=source.get(c.pos().above());
            if(cover!=null&&BuiltInRegistries.BLOCK.getKey(cover.getBlock()).getNamespace().equals("minecraft")
                &&SectorNaturalSupportPolicy.blocksGrass(cover,c.pos().above()))covers.put(c.pos(),cover);
        }
        return Map.copyOf(covers);
    }
    private static boolean exactSourceCover(Environment e,BlockPos grass,BlockState cover) {
        return cover!=null&&e.level().hasChunkAt(grass.above())&&cover.equals(e.level().getBlockState(grass.above()))
            &&e.level().getBlockEntity(grass.above())==null;
    }
    private static CompoundTag sourceCoverProof(Map<BlockPos,BlockState> covers) {
        CompoundTag proof=new CompoundTag();proof.putInt("version",1);ListTag list=new ListTag();
        covers.keySet().stream().sorted(Comparator.comparingLong(BlockPos::asLong)).forEach(pos->{
            CompoundTag entry=new CompoundTag();entry.putLong("pos",pos.asLong());entry.put("sourceCover",NbtUtils.writeBlockState(covers.get(pos)));list.add(entry);
        });proof.put("covers",list);return proof;
    }
    private static Set<BlockPos> readSourceCoverProof(CompoundTag journal,Map<BlockPos,BlockState> covers)throws IOException {
        if(!journal.contains("sourceCoveredGrassV1"))return Set.of();
        if(!journal.contains("sourceCoveredGrassV1",Tag.TAG_COMPOUND)||!sourceCoverProof(covers).equals(journal.getCompound("sourceCoveredGrassV1")))
            throw new IOException("Changed producer source-cover proof");
        // The exact spec/identity and this canonical proof share the journal's checksum.
        return covers.keySet();
    }
    private static final String SOIL_FIELD="naturalSoil53";
    /** Only the previous completed operation can supply exposed-soil lineage. */
    static CompoundTag naturalProof(CompoundTag journal) {
        if(journal==null||!journal.contains(SOIL_FIELD))return new CompoundTag();
        CompoundTag out=new CompoundTag();
        for(String key:List.of("schema","id","phase","identity","spec","previousReceipt","sourceCoveredGrassV1",SOIL_FIELD))
            if(journal.contains(key))out.put(key,journal.get(key).copy());
        return out;
    }
    static Set<BlockPos> completedNaturalDirt(Spec spec,CompoundTag journal,CompoundTag identity,String receipt)throws IOException {
        if(journal.getInt("schema")!=1||!journal.hasUUID("id")||phase(journal)!=Phase.COMMITTED
            ||!receipt.equals(journal.getUUID("id")+":paid")||!journal.getCompound("identity").equals(identity)
            ||!journal.getCompound("spec").equals(describe(spec)))throw new IOException("Untrusted completed producer soil history");
        NaturalSoil soil=new NaturalSoil(spec,soilCells(spec),identity,journal,null,"",0);
        return soil.completedDirt();
    }
    private static List<Cell> soilCells(Spec spec)throws IOException {
        List<Cell> cells=new ArrayList<>();Set<BlockPos> seen=new HashSet<>();
        for(Plot plot:spec.plots()) {
            var before=plan(plot,false);var after=plan(plot,true);
            if(before.size()!=after.size())throw new IOException("Producer soil context differs");
            for(int i=0;i<before.size();i++) {
                var a=before.get(i);var b=after.get(i);
                if(!a.position().equals(b.position())||!seen.add(a.position()))throw new IOException("Overlapping producer soil context");
                cells.add(new Cell(plot.family(),i,a.position(),a.state(),b.state()));
            }
        }return cells;
    }
    private static Spec soilSpec(CompoundTag tag)throws IOException {
        List<Plot> plots=new ArrayList<>();ListTag rows=tag.getList("plots",Tag.TAG_COMPOUND);
        if(rows.isEmpty()||rows.size()>3)throw new IOException("Invalid producer soil parent selection");
        for(Tag value:rows) {
            CompoundTag row=(CompoundTag)value;int[] a=row.getIntArray("anchor");
            if(a.length!=4)throw new IOException("Malformed producer soil parent anchor");
            String family=row.getString("family");plots.add(new Plot(family,row.getInt("source"),row.getInt("target"),
                new MapAnchor(family,a[0],a[1],a[2],a[3]),row.getBoolean("sourceExteriorR2"),row.getBoolean("targetExteriorR2")));
        }
        Spec spec=new Spec(tag.getInt("paidBefore"),tag.getInt("paidAfter"),plots);
        if(!describe(spec).equals(tag))throw new IOException("Producer soil parent revision/cost changed");return spec;
    }
    /** Versioned actual dirt evidence. Canonical cover geometry alone is not an
     * observation of dirt, and never authorizes an exposed future target.
     * Existing journals without this field keep their exact interpretation.
     */
    private static final class NaturalSoil {
        final boolean enabled,recorded;
        final List<Cell> cells;
        final Map<BlockPos,BlockState> source,target,sourceBefore=new HashMap<>();
        final Set<BlockPos> inherited,sourceDirt=new HashSet<>(),targetDirt=new HashSet<>();
        final CompoundTag parent;
        NaturalSoil(Spec spec,List<Cell> cells,CompoundTag identity,CompoundTag journal,CompoundTag previous,String receipt,int depth)throws IOException {
            if(depth>5)throw new IOException("Producer soil lineage exceeds five tiers");
            this.cells=cells;recorded=journal!=null;
            enabled=!recorded||journal.contains(SOIL_FIELD);
            source=covered(cells,false);target=covered(cells,true);
            if(!enabled){parent=new CompoundTag();inherited=Set.of();return;}
            CompoundTag proof=recorded?journal.getCompound(SOIL_FIELD):new CompoundTag();
            if(recorded&&(!journal.contains(SOIL_FIELD,Tag.TAG_COMPOUND)||!proof.getAllKeys().equals(Set.of("version","sourceCovers","targetCovers","sourceBefore","sourceDirt","targetDirt","parent"))
                ||!proof.contains("version",Tag.TAG_INT)||proof.getInt("version")!=1
                ||!proof.contains("sourceCovers",Tag.TAG_LIST)||!states(source,"cover").equals(proof.get("sourceCovers"))
                ||!proof.contains("targetCovers",Tag.TAG_LIST)||!states(target,"cover").equals(proof.get("targetCovers"))
                ||!proof.contains("parent",Tag.TAG_COMPOUND)))throw new IOException("Changed producer natural soil contract");
            if(recorded) {
                if(!journal.contains("sourceCoveredGrassV1",Tag.TAG_COMPOUND))throw new IOException("Modified producer cell authorization: original source-cover proof is missing");
                readSourceCoverProof(journal,sourceCoveredGrass(cells));
            }
            parent=recorded?proof.getCompound("parent").copy():naturalProof(previous);
            inherited=parent.isEmpty()?Set.of():inherit(spec,identity,parent,recorded?journal.getString("previousReceipt"):receipt,depth);
            if(recorded) {
                Set<BlockPos> eligible=new HashSet<>(source.keySet());eligible.addAll(inherited);
                if(!proof.contains("sourceBefore",Tag.TAG_LIST))throw new IOException("Missing actual producer soil image");
                ListTag rows=proof.getList("sourceBefore",Tag.TAG_COMPOUND);
                if(rows.size()!=eligible.size()||rows.size()!=((ListTag)proof.get("sourceBefore")).size())throw new IOException("Incomplete actual producer soil image");
                for(Tag value:rows) {
                    CompoundTag row=(CompoundTag)value;BlockPos pos=BlockPos.of(row.getLong("pos"));
                    if(!row.getAllKeys().equals(Set.of("pos","state"))||!row.contains("pos",Tag.TAG_LONG)||!row.contains("state",Tag.TAG_COMPOUND)||!eligible.contains(pos))throw new IOException("Foreign actual producer soil image");
                    BlockState state=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),row.getCompound("state"));
                    if(!NbtUtils.writeBlockState(state).equals(row.getCompound("state"))||!soilState(state)||sourceBefore.put(pos,state)!=null)throw new IOException("Invalid actual producer soil image");
                }
                if(!states(sourceBefore,"state").equals(rows))throw new IOException("Noncanonical actual producer soil image");
                readPositions(proof,"sourceDirt",source.keySet(),sourceDirt);readPositions(proof,"targetDirt",target.keySet(),targetDirt);
                if(phase(journal)==Phase.PREPARED&&(!sourceDirt.isEmpty()||!targetDirt.isEmpty()))throw new IOException("Premature producer dirt observation");
            }
        }
        private static Set<BlockPos> inherit(Spec current,CompoundTag identity,CompoundTag parent,String receipt,int depth)throws IOException {
            if(!parent.getAllKeys().equals(Set.of("schema","id","phase","identity","spec","previousReceipt","sourceCoveredGrassV1",SOIL_FIELD))
                ||parent.getInt("schema")!=1||!parent.hasUUID("id")||phase(parent)!=Phase.COMMITTED
                ||!receipt.equals(parent.getUUID("id")+":paid")||!identity.equals(parent.getCompound("identity")))throw new IOException("Foreign/unpaid producer soil parent");
            Spec prior=soilSpec(parent.getCompound("spec"));
            if(prior.paidAfter()!=current.paidBefore()||prior.plots().size()!=current.plots().size())throw new IOException("Producer soil parent paid level differs");
            for(int i=0;i<prior.plots().size();i++) {
                Plot a=prior.plots().get(i),b=current.plots().get(i);
                if(!a.family().equals(b.family())||a.target()!=b.source()||a.targetR2()!=b.sourceR2()
                    ||!hash(a,true).equals(hash(b,false))||a.anchor().x()!=b.anchor().x()||a.anchor().y()!=b.anchor().y()
                    ||a.anchor().z()!=b.anchor().z()||a.anchor().rotation()!=b.anchor().rotation())throw new IOException("Producer soil parent route/site differs");
            }
            NaturalSoil previous=new NaturalSoil(prior,soilCells(prior),identity,parent,null,"",depth+1);
            return previous.completedDirt();
        }
        private static boolean soilState(BlockState state){return state.equals(Blocks.GRASS_BLOCK.defaultBlockState())||state.equals(Blocks.DIRT.defaultBlockState());}
        private static Map<BlockPos,BlockState> covered(List<Cell> cells,boolean after) {
            Map<BlockPos,BlockState> all=new HashMap<>(),covers=new HashMap<>();for(Cell c:cells)all.put(c.pos(),after?c.next():c.old());
            for(Cell c:cells)if((after?c.next():c.old()).equals(Blocks.GRASS_BLOCK.defaultBlockState())) {
                BlockState cover=all.get(c.pos().above());
                if(ProducerService.naturalCover(cover,c.pos().above()))covers.put(c.pos(),cover);
            }return Map.copyOf(covers);
        }
        private static boolean exactCover(ServerLevel level,BlockPos pos,BlockState cover){return ProducerService.matchesNaturalCover(level,pos,cover);}
        boolean beforeMatches(BlockState state,Cell cell) {
            if(enabled&&recorded&&sourceBefore.containsKey(cell.pos()))return state.equals(sourceBefore.get(cell.pos()));
            return ProducerRevisions.matches(state,cell.old());
        }
        boolean matches(ServerLevel level,BlockPos pos,BlockState actual,BlockState expected,boolean allowTarget) {
            if(!enabled||!actual.equals(Blocks.DIRT.defaultBlockState())||!expected.equals(Blocks.GRASS_BLOCK.defaultBlockState()))return false;
            return Blocks.DIRT.defaultBlockState().equals(sourceBefore.get(pos))||sourceDirt.contains(pos)||targetDirt.contains(pos)
                ||!recorded&&inherited.contains(pos)||exactCover(level,pos,source.get(pos))||allowTarget&&exactCover(level,pos,target.get(pos));
        }
        void capture(ServerLevel level,BlockState[] live)throws IOException {
            if(!enabled)return;
            Set<BlockPos> eligible=new HashSet<>(source.keySet());eligible.addAll(inherited);
            for(var row:source.entrySet())if(!exactCover(level,row.getKey(),row.getValue()))throw new IOException("Producer source cover changed before soil capture");
            for(int i=0;i<cells.size();i++)if(eligible.contains(cells.get(i).pos())) {
                if(!soilState(live[i]))throw new IOException("Unrecognized actual producer soil");sourceBefore.put(cells.get(i).pos(),live[i]);
            }
            if(sourceBefore.size()!=eligible.size())throw new IOException("Producer inherited soil leaves current source");
        }
        void observe(ServerLevel level) {
            if(!enabled)return;
            observe(level,source,sourceDirt);observe(level,target,targetDirt);
        }
        private static void observe(ServerLevel level,Map<BlockPos,BlockState> covers,Set<BlockPos> observed) {
            for(var row:covers.entrySet())if(level.getBlockState(row.getKey()).equals(Blocks.DIRT.defaultBlockState())&&exactCover(level,row.getKey(),row.getValue()))observed.add(row.getKey());
        }
        Set<BlockPos> completedDirt() {
            if(!enabled)return Set.of();Set<BlockPos> proved=new HashSet<>(sourceDirt);proved.addAll(targetDirt);
            sourceBefore.forEach((pos,state)->{if(state.equals(Blocks.DIRT.defaultBlockState()))proved.add(pos);});
            Set<BlockPos> result=new HashSet<>();for(Cell c:cells)if(c.next().equals(Blocks.GRASS_BLOCK.defaultBlockState())&&proved.contains(c.pos()))result.add(c.pos());return Set.copyOf(result);
        }
        private static ListTag states(Map<BlockPos,BlockState> map,String key) {
            ListTag rows=new ListTag();map.keySet().stream().sorted(Comparator.comparingLong(BlockPos::asLong)).forEach(pos->{CompoundTag row=new CompoundTag();row.putLong("pos",pos.asLong());row.put(key,NbtUtils.writeBlockState(map.get(pos)));rows.add(row);});return rows;
        }
        private static void readPositions(CompoundTag proof,String key,Set<BlockPos> allowed,Set<BlockPos> into)throws IOException {
            if(!proof.contains(key,Tag.TAG_LONG_ARRAY))throw new IOException("Missing producer dirt observations");long[] positions=proof.getLongArray(key);
            if(positions.length>allowed.size())throw new IOException("Oversized producer dirt observations");
            for(long value:positions)if(!allowed.contains(BlockPos.of(value))||!into.add(BlockPos.of(value)))throw new IOException("Foreign/duplicate producer dirt observation");
            if(!Arrays.equals(positions,into.stream().mapToLong(BlockPos::asLong).sorted().toArray()))throw new IOException("Noncanonical producer dirt observation");
        }
        void persist(CompoundTag journal) {
            if(!enabled)return;CompoundTag proof=new CompoundTag();proof.putInt("version",1);proof.put("sourceCovers",states(source,"cover"));proof.put("targetCovers",states(target,"cover"));
            proof.put("sourceBefore",states(sourceBefore,"state"));proof.put("parent",parent.copy());proof.putLongArray("sourceDirt",sourceDirt.stream().mapToLong(BlockPos::asLong).sorted().toArray());
            proof.putLongArray("targetDirt",targetDirt.stream().mapToLong(BlockPos::asLong).sorted().toArray());journal.put(SOIL_FIELD,proof);
        }
    }
    private static void advance(Path p,CompoundTag j,Phase next,NaturalSoil soil) throws IOException {
        if(!ProducerJournal.read(p).equals(j))throw new IOException("Producer journal changed concurrently");
        soil.persist(j);
        j.putString("phase",next.name());ProducerJournal.write(p,j,true);
    }
    private static void verify(Environment e,List<Cell> cells,BlockState[] states,Map<BlockPos,CompoundTag> inventories,
            Map<BlockPos,BlockState> shell,Map<BlockPos,CompoundTag> shellNbt,CompoundTag ledger,Set<UUID> items,List<AABB> boxes) throws IOException {
        for(int i=0;i<cells.size();i++) {
            Cell cell=cells.get(i);BlockState actual=e.level().getBlockState(cell.pos());
            if(!states[i].equals(actual))throw new IOException("Producer post-write mismatch at "+cell.pos()
                +" family="+cell.family()+" expected="+states[i]+" actual="+actual
                +" source="+cell.old()+" target="+cell.next());
        }
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
