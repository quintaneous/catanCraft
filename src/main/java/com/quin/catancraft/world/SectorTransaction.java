package com.quin.catancraft.world;

import com.quin.catancraft.data.NationData;
import com.quin.catancraft.data.ResourceType;
import com.quin.catancraft.economy.EconomyCost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Whole-sector fixture transaction. No authored art is approved or selected by this engine.
 * A single reservation, receipt and purchased level cover the complete declared union.
 */
public final class SectorTransaction {
    public enum Action {STATUS, APPLY, RESUME, ROLLBACK}
    public record Cell(BlockPos pos,BlockState before,BlockState after){public Cell{pos=pos.immutable();Objects.requireNonNull(before);Objects.requireNonNull(after);}}
    public record Read(BlockPos pos,BlockState state){public Read{pos=pos.immutable();Objects.requireNonNull(state);}}
    public record Plan(String sector,String sourceRevision,String targetRevision,int sourceLevel,int targetLevel,List<Cell> cells,List<Read> reads,SectorNaturalSupportPolicy naturalSupport){
        public Plan{cells=List.copyOf(cells);reads=List.copyOf(reads);Objects.requireNonNull(naturalSupport);}
        public Plan(String sector,String sourceRevision,String targetRevision,int sourceLevel,int targetLevel,List<Cell> cells,List<Read> reads){this(sector,sourceRevision,targetRevision,sourceLevel,targetLevel,cells,reads,SectorNaturalSupportPolicy.NONE);}
    }
    public interface Environment {
        ServerLevel level();Path file();Plan plan();CompoundTag identity();EconomyCost cost();NationData nation();
        int progress();void progress(int level);String receipt();void receipt(String value);
        void save()throws IOException;void requirements(boolean rollback)throws IOException;void allow(BlockPos pos)throws IOException;
        /** Complete prior committed paid journal, already read through ProducerJournal; never guessed from a level alone. */
        default CompoundTag inheritedNaturalSupportProof()throws IOException{return new CompoundTag();}
    }
    public interface Fault {void at(String stage,int writes)throws IOException;}
    public record Result(String message,int writes){}
    private static final int FLAGS=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS;
    private static final Set<String> PHASES=Set.of("PREPARED","APPLYING","PHYSICAL_COMPLETE","COMMITTED","ROLLING_BACK","ROLLED_BACK");
    private SectorTransaction(){}
    public static EconomyCost historicalCost(CompoundTag tag)throws IOException {
        if(!tag.contains("money",Tag.TAG_LONG)||tag.getLong("money")<0)throw new IOException("Invalid sector historical money");
        Map<String,ResourceType> names=new HashMap<>();for(ResourceType r:ResourceType.values())names.put(r.id(),r);
        Map<ResourceType,Long> resources=new EnumMap<>(ResourceType.class);
        for(String key:tag.getAllKeys())if(!key.equals("money")){
            ResourceType type=names.get(key);if(type==null||!tag.contains(key,Tag.TAG_LONG)||tag.getLong(key)<=0)throw new IOException("Unknown/invalid sector historical material: "+key);
            resources.put(type,tag.getLong(key));
        }
        return new EconomyCost(tag.getLong("money"),resources);
    }
    private static CompoundTag costTag(EconomyCost cost)throws IOException {
        if(cost==null)throw new IOException("Whole-sector cost is unavailable");CompoundTag tag=new CompoundTag();tag.putLong("money",cost.money());
        for(var e:cost.resources().entrySet())tag.putLong(e.getKey().id(),e.getValue());return tag;
    }
    public static CompoundTag describe(Plan p)throws IOException {
        if(p==null||p.sector()==null||p.sector().isBlank()||p.sourceRevision()==null||p.sourceRevision().isBlank()||p.targetRevision()==null||p.targetRevision().isBlank()||p.sourceRevision().equals(p.targetRevision())||p.sourceLevel()<0||p.sourceLevel()==Integer.MAX_VALUE||p.targetLevel()<0||p.targetLevel()!=p.sourceLevel()+1||p.cells().isEmpty()||p.cells().size()>250000||p.reads().size()>500000)throw new IOException("Invalid whole-sector source/target contract");
        CompoundTag spec=new CompoundTag();spec.putString("sector",p.sector());spec.putString("sourceRevision",p.sourceRevision());spec.putString("targetRevision",p.targetRevision());spec.putInt("sourceLevel",p.sourceLevel());spec.putInt("targetLevel",p.targetLevel());
        Set<BlockPos> positions=new HashSet<>();ListTag cells=new ListTag(),reads=new ListTag();int changes=0;
        for(Cell c:p.cells()){
            if(!positions.add(c.pos()))throw new IOException("Overlapping sector union cells");boolean changesCell=!c.before().equals(c.after());if(changesCell){changes++;if(c.before().hasBlockEntity()||c.after().hasBlockEntity()||!c.before().getFluidState().isEmpty()||!c.after().getFluidState().isEmpty())throw new IOException("Sector changes would replace an inventory/block entity or fluid");}
            CompoundTag row=new CompoundTag();row.putLong("pos",c.pos().asLong());row.put("before",NbtUtils.writeBlockState(c.before()));row.put("after",NbtUtils.writeBlockState(c.after()));cells.add(row);
        }
        if(changes==0)throw new IOException("Whole-sector purchase has no declared changes");
        for(Read r:p.reads()){if(!positions.add(r.pos()))throw new IOException("Sector protected reads overlap union or each other");CompoundTag row=new CompoundTag();row.putLong("pos",r.pos().asLong());row.put("state",NbtUtils.writeBlockState(r.state()));reads.add(row);}
        spec.put("cells",cells);spec.put("reads",reads);p.naturalSupport().validate(p);
        if(p.naturalSupport().enabled())spec.put("naturalSupport",p.naturalSupport().describe());return spec;
    }
    public static Plan fromDescription(CompoundTag spec)throws IOException {
        List<Cell> cells=new ArrayList<>();List<Read> reads=new ArrayList<>();
        for(Tag t:spec.getList("cells",Tag.TAG_COMPOUND)){CompoundTag row=(CompoundTag)t;cells.add(new Cell(BlockPos.of(row.getLong("pos")),state(row.getCompound("before")),state(row.getCompound("after"))));}
        for(Tag t:spec.getList("reads",Tag.TAG_COMPOUND)){CompoundTag row=(CompoundTag)t;reads.add(new Read(BlockPos.of(row.getLong("pos")),state(row.getCompound("state"))));}
        if(spec.contains("naturalSupport")&&!spec.contains("naturalSupport",Tag.TAG_COMPOUND))throw new IOException("Invalid natural support spec type");
        Plan plan=new Plan(spec.getString("sector"),spec.getString("sourceRevision"),spec.getString("targetRevision"),spec.getInt("sourceLevel"),spec.getInt("targetLevel"),cells,reads,SectorNaturalSupportPolicy.read(spec.getCompound("naturalSupport")));
        if(!describe(plan).equals(spec))throw new IOException("Noncanonical recorded sector plan");return plan;
    }
    private static BlockState state(CompoundTag tag)throws IOException {
        BlockState state=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),tag);if(!NbtUtils.writeBlockState(state).equals(tag))throw new IOException("Unknown recorded sector block state");return state;
    }
    public static Result run(Environment e,Action action)throws IOException{return run(e,action,(stage,writes)->{});}
    public static Result run(Environment e,Action action,Fault fault)throws IOException {
        Runner runner=begin(e,action,fault);
        while(!runner.done())runner.step(Integer.MAX_VALUE,Long.MAX_VALUE);
        return runner.result();
    }
    public static Runner begin(Environment e,Action action)throws IOException{return begin(e,action,(stage,writes)->{});}
    public static Runner begin(Environment e,Action action,Fault fault)throws IOException {
        Plan plan=e.plan();CompoundTag spec=describe(plan),identity=e.identity().copy();if(e.nation()==null)throw new IOException("Whole-sector owner nation unavailable");UUID nationId=e.nation().id();boolean rollback=action==Action.ROLLBACK;
        boolean exists=Files.exists(e.file(),LinkOption.NOFOLLOW_LINKS);CompoundTag journal=exists?ProducerJournal.read(e.file()):null;String phase=exists?journal.getString("phase"):"NONE";
        if(exists&&(journal.getInt("schema")!=1||!journal.hasUUID("id")||!journal.hasUUID("nation")||!journal.getUUID("nation").equals(nationId)||!journal.getCompound("identity").equals(identity)||!journal.getCompound("spec").equals(spec)||!PHASES.contains(phase)))throw new IOException("Foreign/corrupt whole-sector journal or changed source revision");
        EconomyCost cost=exists?historicalCost(journal.getCompound("cost")):e.cost();costTag(cost);
        String previous=exists?journal.getString("previousReceipt"):safe(e.receipt());String id=exists?journal.getUUID("id").toString():"";
        String expectedReceipt=safe(e.receipt());boolean reserved=exists&&expectedReceipt.equals(id+":reserved"),paid=exists&&expectedReceipt.equals(id+":paid"),refunded=exists&&expectedReceipt.equals(previous)&&(phase.equals("ROLLING_BACK")||phase.equals("ROLLED_BACK"));
        if(exists&&!reserved&&!paid&&!refunded&&!expectedReceipt.equals(previous))throw new IOException("Sector receipt is superseded/ambiguous");
        int expectedProgress=paid?plan.targetLevel():plan.sourceLevel();
        if(e.progress()!=expectedProgress||phase.equals("COMMITTED")&&!paid||phase.equals("ROLLED_BACK")&&!refunded)throw new IOException("Whole-sector purchased level/receipt/phase disagree");
        CompoundTag supportProof=new CompoundTag();Set<BlockPos> inheritedSupport=Set.of();
        if(plan.naturalSupport().enabled()){
            if(exists&&journal.contains("inheritedNaturalSupportProof")&&!journal.contains("inheritedNaturalSupportProof",Tag.TAG_COMPOUND))throw new IOException("Invalid frozen natural support proof type");
            supportProof=exists?journal.getCompound("inheritedNaturalSupportProof").copy():SectorNaturalSupportPolicy.freeze(e.inheritedNaturalSupportProof());
            inheritedSupport=SectorNaturalSupportPolicy.inherited(plan,supportProof,previous,nationId,identity);
        }
        SectorNaturalSupportPolicy.Guard support=new SectorNaturalSupportPolicy.Guard(e.level(),plan,inheritedSupport,exists&&!phase.equals("PREPARED"),journal);
        check(e,rollback,identity,nationId,expectedProgress,expectedReceipt);
        boolean pending=exists&&!phase.equals("COMMITTED")&&!phase.equals("ROLLED_BACK");
        Map<BlockPos,BlockState> states=new LinkedHashMap<>(),reads=new LinkedHashMap<>(),targets=new LinkedHashMap<>();Map<BlockPos,CompoundTag> preservedNbt=new LinkedHashMap<>();
        List<Cell> changed=new ArrayList<>();ListTag snapshots=exists?journal.getList("actualBefore",Tag.TAG_COMPOUND):new ListTag();int snapshotIndex=0;
        for(Cell cell:plan.cells()){
            requireLoaded(e.level(),cell.pos());BlockState live=e.level().getBlockState(cell.pos());boolean changedCell=!cell.before().equals(cell.after());
            if(!(pending&&changedCell?(live.equals(cell.before())||live.equals(cell.after())):live.equals(paid?cell.after():cell.before())))throw new IOException("Sector source/target drift at "+cell.pos());
            captureNbt(e.level(),cell.pos(),live,preservedNbt);states.put(cell.pos(),live);targets.put(cell.pos(),rollback?cell.before():cell.after());
            if(changedCell){
                e.allow(cell.pos());changed.add(cell);
                if(exists){if(snapshotIndex>=snapshots.size())throw new IOException("Incomplete whole-sector actual-before snapshot");CompoundTag row=snapshots.getCompound(snapshotIndex);
                    if(row.getLong("pos")!=cell.pos().asLong()||!row.getCompound("before").equals(NbtUtils.writeBlockState(cell.before()))||!row.getCompound("after").equals(NbtUtils.writeBlockState(cell.after()))||!row.getCompound("beforeNbt").isEmpty())throw new IOException("Whole-sector before-image does not match exact source contract");
                }else{CompoundTag row=new CompoundTag();row.putLong("pos",cell.pos().asLong());row.put("before",NbtUtils.writeBlockState(live));row.put("beforeNbt",new CompoundTag());row.put("after",NbtUtils.writeBlockState(cell.after()));snapshots.add(row);}snapshotIndex++;
            }
        }
        if(exists&&snapshotIndex!=snapshots.size())throw new IOException("Unexpected whole-sector snapshot cells");
        for(Read r:plan.reads()){requireLoaded(e.level(),r.pos());BlockState live=e.level().getBlockState(r.pos());if(!support.matches(r.pos(),r.state(),live))throw new IOException("Sector protected road/corner/support read mismatch at "+r.pos()+" expected="+r.state()+" actual="+live);reads.put(r.pos(),live);captureNbt(e.level(),r.pos(),live,preservedNbt);}
        // Preserve the real one-block shell, not an authored approximation of neighboring development.
        for(Cell c:changed)for(Direction direction:Direction.values()){
            BlockPos p=c.pos().relative(direction);if(states.containsKey(p)||reads.containsKey(p))continue;requireLoaded(e.level(),p);BlockState live=e.level().getBlockState(p);reads.put(p,live);captureNbt(e.level(),p,live,preservedNbt);
        }
        if(action==Action.STATUS)return Runner.completed(new Result("Whole sector "+plan.sector()+": "+phase+"; source L"+plan.sourceLevel()+" to L"+plan.targetLevel()+"; "+changed.size()+" exact changes; read-only",0));
        if(!exists&&action!=Action.APPLY)throw new IOException("No whole-sector history to recover");
        if(action==Action.APPLY&&pending)throw new IOException("Whole-sector operation already pending; use resume or rollback");
        if(phase.equals("COMMITTED")&&!rollback)return Runner.completed(new Result("Whole sector already owned and verified; zero writes and zero charge",0));
        if(phase.equals("ROLLED_BACK")){if(rollback)return Runner.completed(new Result("Whole sector already rolled back; zero writes and zero refund",0));throw new IOException("Rolled-back sector history retained; fresh purchase needs a new journal");}
        if(phase.equals("ROLLING_BACK")&&!rollback)throw new IOException("Finish pending whole-sector rollback");
        List<ConstructionHazards.Write> writes=new ArrayList<>();List<Cell> ordered=new ArrayList<>(changed);if(rollback)Collections.reverse(ordered);
        for(Cell c:ordered){BlockState target=targets.get(c.pos());if(!states.get(c.pos()).equals(target))writes.add(new ConstructionHazards.Write(c.pos(),target));}
        PlannedWorld.verify(e.level(),targets,FLAGS);ConstructionHazards.Guard entities=ConstructionHazards.inspect(e.level(),writes);
        VerificationLayout verification=new VerificationLayout(states,reads,preservedNbt);
        if(!exists){
            if(!cost.canAfford(e.nation()))throw new IOException("Cannot afford complete sector: "+cost.describe());
            e.save();verification.verify(e,support);entities.verify();check(e,rollback,identity,nationId,expectedProgress,expectedReceipt);
            journal=new CompoundTag();journal.putInt("schema",1);journal.putUUID("id",UUID.randomUUID());journal.putUUID("nation",nationId);journal.put("identity",identity);journal.put("spec",spec);journal.put("cost",costTag(cost));journal.putString("previousReceipt",previous);journal.put("actualBefore",snapshots);
            if(!supportProof.isEmpty())journal.put("inheritedNaturalSupportProof",supportProof.copy());
            ListTag protectedSnapshot=new ListTag();preservedNbt.forEach((p,n)->{CompoundTag t=new CompoundTag();t.putLong("pos",p.asLong());t.put("actualNbt",n.copy());protectedSnapshot.add(t);});journal.put("preservedNbt",protectedSnapshot);journal.putString("phase","PREPARED");ProducerJournal.write(e.file(),journal,false);id=journal.getUUID("id").toString();fault.at("prepared",0);
        }
        check(e,rollback,identity,nationId,expectedProgress,expectedReceipt);verification.verify(e,support);entities.verify();
        if(!rollback&&!reserved&&!paid){
            if(!cost.canAfford(e.nation()))throw new IOException("Complete sector funds unavailable; journal retained without reservation");
            cost.charge(e.nation());expectedReceipt=id+":reserved";e.receipt(expectedReceipt);CompoundTag expectedNation=e.nation().save();e.save();if(!expectedNation.equals(e.nation().save()))throw new IOException("Nation changed during whole-sector reservation save");reserved=true;fault.at("reserved",0);
        }
        check(e,rollback,identity,nationId,expectedProgress,expectedReceipt);verification.verify(e,support);entities.verify();advance(e.file(),journal,rollback?"ROLLING_BACK":"APPLYING",support);support.pending=true;
        return new Runner(e,plan,journal,identity,nationId,rollback,expectedProgress,expectedReceipt,reserved,paid,refunded,previous,id,cost,support,verification,entities,states,targets,ordered,fault);
    }
    /** Main-thread continuation. Preparation and durable save boundaries are atomic;
     * each step limits placement work without ever skipping the full pre-write guards.
     * A failed runner must be discarded and recovered from its durable journal.
     */
    public static final class Runner {
        private Environment e;
        private Plan plan;
        private CompoundTag journal;
        private CompoundTag identity;
        private UUID nationId;
        private boolean rollback;
        private int expectedProgress;
        private String expectedReceipt;
        private boolean reserved;
        private boolean paid;
        private boolean refunded;
        private String previous;
        private String id;
        private EconomyCost cost;
        private SectorNaturalSupportPolicy.Guard support;
        private VerificationLayout verification;
        private ConstructionHazards.Guard entities;
        private Map<BlockPos,BlockState> states;
        private Map<BlockPos,BlockState> targets;
        private List<Cell> ordered;
        private Fault fault;
        private int cursor,count,lastTick;
        private byte[] sealedJournal;
        private net.minecraft.world.phys.AABB entityBounds;
        private Result result;
        private boolean failed;
        private Runner(Result result){this.result=result;}
        private static Runner completed(Result result){return new Runner(result);}
        private Runner(Environment e,Plan plan,CompoundTag journal,CompoundTag identity,UUID nationId,boolean rollback,int expectedProgress,String expectedReceipt,boolean reserved,boolean paid,boolean refunded,String previous,String id,EconomyCost cost,SectorNaturalSupportPolicy.Guard support,VerificationLayout verification,ConstructionHazards.Guard entities,Map<BlockPos,BlockState> states,Map<BlockPos,BlockState> targets,List<Cell> ordered,Fault fault)throws IOException{
            this.e=e;
            this.plan=plan;
            this.journal=journal;
            this.identity=identity;
            this.nationId=nationId;
            this.rollback=rollback;
            this.expectedProgress=expectedProgress;
            this.expectedReceipt=expectedReceipt;
            this.reserved=reserved;
            this.paid=paid;
            this.refunded=refunded;
            this.previous=previous;
            this.id=id;
            this.cost=cost;
            this.support=support;
            this.verification=verification;
            this.entities=entities;
            this.states=states;
            this.targets=targets;
            this.ordered=ordered;
            this.fault=fault;
            this.lastTick=e.level().getServer().getTickCount();
            this.entityBounds=SectorService.entityBounds(plan);
            sealedJournal=readSealed(e.file());
            if(!ProducerJournal.read(sealedJournal).equals(journal)||!Arrays.equals(sealedJournal,readSealed(e.file())))
                throw new IOException("Whole-sector journal changed while binding runner checkpoint");
        }
        private static byte[] readSealed(Path file)throws IOException{
            final int limit=8*1024*1024+40;
            if(Files.isSymbolicLink(file)||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>limit)
                throw new IOException("Invalid whole-sector runner journal file");
            try(var input=Files.newInputStream(file,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
                byte[] bytes=input.readNBytes(limit+1);
                if(bytes.length>limit)throw new IOException("Oversized whole-sector runner journal");
                return bytes;
            }
        }
        public boolean done(){return result!=null;}
        public int writes(){return count;}
        public Result result(){if(result==null)throw new IllegalStateException("Sector operation is still pending");return result;}
        /** Returns true only after durable settlement. The time budget is cooperative:
         * no validation, block write or world-save boundary is interrupted mid-call.
         */
        public boolean step(int maxWrites,long maxNanos)throws IOException {
            if(maxWrites<1||maxNanos<1)throw new IllegalArgumentException("Positive sector work budgets required");
            if(failed)throw new IOException("Failed sector runner must recover from retained journal");
            if(done())return true;
            if(!e.level().getServer().isSameThread())throw new IOException("Sector runner requires main server thread");
            try{return advanceStep(maxWrites,maxNanos);}catch(IOException|RuntimeException error){failed=true;throw error;}
        }
        private boolean advanceStep(int maxWrites,long maxNanos)throws IOException {
            long started=System.nanoTime();int initial=count;
            // Reopen and compare ALL sealed bytes on every slice. Only parsing is cached;
            // no metadata/mtime shortcut can hide a rewritten, missing or corrupt history.
            if(!Arrays.equals(sealedJournal,readSealed(e.file())))throw new IOException("Whole-sector journal changed between steps");
            int tick=e.level().getServer().getTickCount();
            if(tick==lastTick)entities.verify();
            // The complete transition was preflighted before the one debit. At a yield,
            // live entities may move/age normally. Recheck the next bounded write slice
            // against today's actual prefix, preserving exact support/attachment hazards.
            // Every later slice is independently inspected BEFORE any of its writes.
            check(e,rollback,identity,nationId,expectedProgress,expectedReceipt);
            verification.verify(e,support);
            if(tick!=lastTick)StreetscapeService.requireLoadedEntities(e.level(),List.of(entityBounds));
            List<ConstructionHazards.Write> remaining=new ArrayList<>();
            for(int i=cursor;i<ordered.size()&&remaining.size()<maxWrites;i++){
                Cell c=ordered.get(i);BlockState wanted=targets.get(c.pos());
                if(!states.get(c.pos()).equals(wanted))remaining.add(new ConstructionHazards.Write(c.pos(),wanted));
            }
            entities=ConstructionHazards.inspect(e.level(),remaining);lastTick=tick;
            while(cursor<ordered.size()){
                Cell c=ordered.get(cursor);
            check(e,rollback,identity,nationId,expectedProgress,expectedReceipt);e.allow(c.pos());verification.verify(e,support);entities.verify();
            BlockState wanted=targets.get(c.pos());if(!states.get(c.pos()).equals(wanted)){
                if(!e.level().setBlock(c.pos(),wanted,FLAGS))throw new IOException("Whole-sector block write rejected");verification.expectWrite(c.pos(),wanted);count++;fault.at(rollback?"rollback_write":"write",count);
            }
                cursor++;
                if(cursor<ordered.size()&&(count-initial>=maxWrites||System.nanoTime()-started>=maxNanos))return false;
            }
        verification.verify(e,support);entities.verify();advance(e.file(),journal,rollback?"ROLLING_BACK":"PHYSICAL_COMPLETE",support);e.save();fault.at("physical_saved",count);
        check(e,rollback,identity,nationId,expectedProgress,expectedReceipt);verification.verify(e,support);entities.verify();
        if(rollback){
            if(!refunded&&(reserved||paid))refund(e.nation(),cost);expectedReceipt=previous;expectedProgress=plan.sourceLevel();e.progress(expectedProgress);e.receipt(expectedReceipt);
        }else{expectedReceipt=id+":paid";expectedProgress=plan.targetLevel();e.progress(expectedProgress);e.receipt(expectedReceipt);}
        CompoundTag expectedNation=e.nation().save();e.save();fault.at("ledger_saved",count);check(e,rollback,identity,nationId,expectedProgress,expectedReceipt);verification.verify(e,support);entities.verify();if(!expectedNation.equals(e.nation().save()))throw new IOException("Nation changed during whole-sector settlement save");
        advance(e.file(),journal,rollback?"ROLLED_BACK":"COMMITTED",support);fault.at("committed",count);
        result=new Result("Whole sector "+plan.sector()+" "+(rollback?"restored/refunded":"purchased/upgraded")+" atomically; "+count+" sparse writes; one group receipt",count);
            return true;
        }
    }
    private static void refund(NationData nation,EconomyCost cost)throws IOException {
        try{Math.addExact(nation.treasury(),cost.money());for(var e:cost.resources().entrySet())Math.addExact(nation.resource(e.getKey()),e.getValue());}catch(ArithmeticException x){throw new IOException("Whole-sector refund would overflow",x);}
        nation.addTreasury(cost.money());cost.resources().forEach(nation::addResource);
    }
    private static void check(Environment e,boolean rollback,CompoundTag identity,UUID nation,int progress,String receipt)throws IOException {
        e.requirements(rollback);if(!identity.equals(e.identity())||e.nation()==null||!e.nation().id().equals(nation)||e.progress()!=progress||!safe(e.receipt()).equals(receipt))throw new IOException("Whole-sector owner/progress/receipt identity changed");
    }
    /** Compiles only immutable addresses/check kinds, never world observations or loaded chunks.
     * Every invocation still rereads every state and BE, serializes all current inventory NBT,
     * and resolves each current loaded chunk again. No validation is skipped between writes.
     */
    private static final class VerificationLayout {
        private static final class CellCheck {
            final BlockPos pos;final boolean supportRead;BlockState expected;
            CellCheck(BlockPos pos,BlockState expected,boolean supportRead){this.pos=pos;this.expected=expected;this.supportRead=supportRead;}
        }
        private record NbtCheck(BlockPos pos,CompoundTag expected){}
        private record ChunkChecks(int x,int z,CellCheck[] cells,NbtCheck[] inventories){}
        private static final class Builder {
            final int x,z;final List<CellCheck> cells=new ArrayList<>();final List<NbtCheck> inventories=new ArrayList<>();
            Builder(BlockPos pos){x=pos.getX()>>4;z=pos.getZ()>>4;}
            ChunkChecks finish(){return new ChunkChecks(x,z,cells.toArray(CellCheck[]::new),inventories.toArray(NbtCheck[]::new));}
        }
        private final Map<BlockPos,BlockState> states;
        private final Map<BlockPos,CellCheck> written=new HashMap<>();
        private final ChunkChecks[] chunks;
        VerificationLayout(Map<BlockPos,BlockState> states,Map<BlockPos,BlockState> reads,Map<BlockPos,CompoundTag> nbt)throws IOException {
            this.states=states;
            var grouped=new LinkedHashMap<Long,Builder>();
            for(var row:states.entrySet()){
                CellCheck check=new CellCheck(row.getKey(),row.getValue(),false);written.put(row.getKey(),check);builder(grouped,row.getKey()).cells.add(check);
            }
            for(var row:reads.entrySet()){
                if(written.containsKey(row.getKey()))throw new IOException("Verification read overlaps changed/preserved sector union");
                builder(grouped,row.getKey()).cells.add(new CellCheck(row.getKey(),row.getValue(),true));
            }
            for(var row:nbt.entrySet())builder(grouped,row.getKey()).inventories.add(new NbtCheck(row.getKey(),row.getValue()));
            chunks=grouped.values().stream().map(Builder::finish).toArray(ChunkChecks[]::new);
        }
        private static Builder builder(Map<Long,Builder> groups,BlockPos pos){
            long key=net.minecraft.world.level.ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4);
            return groups.computeIfAbsent(key,k->new Builder(pos));
        }
        /** Called only after this operation's successful setBlock, before any fault callback.
         * Keep the original source-state map and compiled expectation holder in lockstep.
         */
        void expectWrite(BlockPos pos,BlockState wanted)throws IOException {
            CellCheck cell=written.get(pos);if(cell==null)throw new IOException("Write outside compiled verification union");
            if(!Objects.equals(states.get(pos),cell.expected))throw new IOException("Compiled sector expectation changed unexpectedly");
            cell.expected=wanted;states.put(pos,wanted);
        }
        void verify(Environment e,SectorNaturalSupportPolicy.Guard support)throws IOException {
            ServerLevel level=e.level();
            for(ChunkChecks group:chunks){
                // Chunk references are local to this pass; unload/replacement cannot be hidden
                // by retaining a previously loaded chunk across a write or callback.
                var chunk=level.getChunkSource().getChunkNow(group.x(),group.z());
                if(chunk==null)throw new IOException("Whole-sector chunk/support is unloaded at "+group.x()+","+group.z());
                for(CellCheck cell:group.cells()){
                    BlockState actual=chunk.getBlockState(cell.pos);
                    boolean matches=cell.supportRead?support.matches(cell.pos,cell.expected,actual):actual.equals(cell.expected);
                    if(!matches)throw new IOException("Whole-sector world/neighbor changed at "+cell.pos);
                    if(!cell.expected.hasBlockEntity()&&chunk.getBlockEntity(cell.pos)!=null)throw new IOException("Unexpected whole-sector neighbor block entity");
                }
                for(NbtCheck inventory:group.inventories()){
                    BlockEntity be=chunk.getBlockEntity(inventory.pos());
                    if(be==null||!be.saveWithFullMetadata().equals(inventory.expected()))throw new IOException("Whole-sector changed current inventory/book NBT at "+inventory.pos());
                }
            }
        }
    }
    private static void captureNbt(ServerLevel level,BlockPos p,BlockState state,Map<BlockPos,CompoundTag> out)throws IOException {
        BlockEntity be=level.getBlockEntity(p);if(state.hasBlockEntity()){
            if(be==null||!(state.getBlock() instanceof EntityBlock block))throw new IOException("Missing whole-sector protected block entity");BlockEntity reference=block.newBlockEntity(p,state);
            if(reference==null||!reference.saveWithFullMetadata().getString("id").equals(be.saveWithFullMetadata().getString("id")))throw new IOException("Foreign whole-sector block entity type");out.put(p,be.saveWithFullMetadata().copy());
        }else if(be!=null)throw new IOException("Unexpected whole-sector block entity at "+p);
    }
    private static void requireLoaded(ServerLevel level,BlockPos pos)throws IOException {if(!level.hasChunkAt(pos))throw new IOException("Whole-sector chunk/support is unloaded at "+pos);}
    private static String safe(String value){return value==null?"":value;}
    private static void advance(Path file,CompoundTag journal,String phase,SectorNaturalSupportPolicy.Guard support)throws IOException {if(!ProducerJournal.read(file).equals(journal))throw new IOException("Whole-sector journal changed concurrently");support.persist(journal);journal.putString("phase",phase);ProducerJournal.write(file,journal,true);}
}
