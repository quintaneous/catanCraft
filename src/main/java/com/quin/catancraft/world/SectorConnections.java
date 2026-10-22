package com.quin.catancraft.world;

import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Internal connection primitive; production is gated until grouped purchase/overlay integration exists.
 * It neither buys a side nor changes purchased levels, base receipts, resources or cell ownership.
 */
final class SectorConnections {
    enum Action {APPLY,RESUME,ROLLBACK,STATUS}
    record Participant(SectorContracts.Direction side,int level,String paidReceipt,String revision){
        Participant{if(side==null||level<1||level>2||paidReceipt==null||!paidReceipt.matches("[0-9a-f-]{36}:paid")||revision==null||revision.isBlank())throw new IllegalArgumentException("Missing purchased participant proof");}
    }
    record Cap(BlockPos pos,BlockState closed,SectorContracts.Direction cellOwner){}
    record Plan(SectorContracts.Corner corner,SectorContracts.Direction transactionOwner,List<Participant> participants,List<Cap> caps,List<SectorTransaction.Read> reads){
        Plan{participants=List.copyOf(participants);caps=List.copyOf(caps);reads=List.copyOf(reads);}
    }
    interface Environment {
        ServerLevel level();Path file();Plan plan();CompoundTag identity();List<Participant> currentParticipants();
        String firstReceipt();String secondReceipt();void receipt(String receipt);void save()throws IOException;
        void requirements()throws IOException;void allow(BlockPos pos,SectorContracts.Direction cellOwner)throws IOException;
    }
    interface Fault {void at(String stage,int writes)throws IOException;}
    record Result(String message,int writes){}
    private static boolean busy;
    private static final int FLAGS=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS;
    private static final Set<String> PHASES=Set.of("PREPARED","APPLYING","COMMITTED","ROLLING_BACK","ROLLED_BACK");
    private SectorConnections(){}
    static CompoundTag describe(Plan p)throws IOException {
        if(p==null||p.corner()==null||p.transactionOwner()==null||p.participants().size()!=2||p.caps().size()!=12)throw new IOException("Connection requires two purchased participants and twelve exact exclusive cap cells");
        SectorContracts.Direction owner=switch(p.corner()){case NORTH_WEST,NORTH_EAST->SectorContracts.Direction.NORTH;case SOUTH_WEST,SOUTH_EAST->SectorContracts.Direction.SOUTH;};
        if(p.transactionOwner()!=owner)throw new IOException("Incorrect connection coordinator; cell ownership does not transfer");
        Set<SectorContracts.Direction> sides=new HashSet<>();for(var participant:p.participants())if(!p.corner().incident.contains(participant.side())||!sides.add(participant.side()))throw new IOException("Missing/duplicate incident participant");
        CompoundTag spec=new CompoundTag();spec.putString("corner",p.corner().name());spec.putString("transactionOwner",owner.name());ListTag participants=new ListTag();for(var part:p.participants()){CompoundTag n=new CompoundTag();n.putString("side",part.side().name());n.putInt("level",part.level());n.putString("receipt",part.paidReceipt());n.putString("revision",part.revision());participants.add(n);}spec.put("participants",participants);
        Set<BlockPos> positions=new HashSet<>();Map<SectorContracts.Direction,Integer> counts=new EnumMap<>(SectorContracts.Direction.class);ListTag caps=new ListTag(),reads=new ListTag();
        for(var cap:p.caps()){
            if(cap==null||cap.pos()==null||cap.closed()==null||!sides.contains(cap.cellOwner())||!positions.add(cap.pos())||cap.closed().hasBlockEntity()||!cap.closed().getFluidState().isEmpty()||!(cap.closed().is(Blocks.STONE_BRICKS)||cap.closed().is(Blocks.STONE_BRICK_SLAB)||cap.closed().is(Blocks.LIGHT_GRAY_CONCRETE)||cap.closed().is(Blocks.POLISHED_ANDESITE_SLAB)))throw new IOException("Unknown/shared cap source or cell ownership");
            counts.merge(cap.cellOwner(),1,Integer::sum);CompoundTag n=new CompoundTag();n.putLong("pos",cap.pos().asLong());n.put("closed",NbtUtils.writeBlockState(cap.closed()));n.putString("cellOwner",cap.cellOwner().name());caps.add(n);
        }
        if(counts.size()!=2||counts.values().stream().anyMatch(n->n!=6))throw new IOException("Each incident sector must retain its own six terminal cells");
        if(p.reads().isEmpty()||p.reads().size()>500000)throw new IOException("Both participant support/route/source reads required");
        for(var r:p.reads()){if(r==null||r.pos()==null||r.state()==null||!positions.add(r.pos()))throw new IOException("Connection reads overlap caps or each other");CompoundTag n=new CompoundTag();n.putLong("pos",r.pos().asLong());n.put("state",NbtUtils.writeBlockState(r.state()));reads.add(n);}spec.put("caps",caps);spec.put("reads",reads);return spec;
    }
    static synchronized Result run(Environment e,Action action,Fault fault)throws IOException {
        if(busy)throw new IOException("Another coordinated connection operation is active");busy=true;
        try{return execute(e,action,fault);}finally{busy=false;}
    }
    static Result run(Environment e,Action action)throws IOException{return run(e,action,(stage,writes)->{});}
    private static Result execute(Environment e,Action action,Fault fault)throws IOException {
        if(!(e.level().getServer() instanceof GameTestServer)||!e.level().getServer().isSameThread())throw new IOException("Connection primitive is isolated GameTest-only until grouped purchase/overlay integration is complete");
        Plan plan=e.plan();CompoundTag spec=describe(plan),identity=e.identity().copy();boolean rollback=action==Action.ROLLBACK,exists=Files.exists(e.file(),LinkOption.NOFOLLOW_LINKS);CompoundTag j=exists?ProducerJournal.read(e.file()):null;
        String phase=exists?j.getString("phase"):"NONE",id=exists&&j.hasUUID("id")?j.getUUID("id").toString():"",receipt=receipt(e),previous=exists?j.getString("previousReceipt"):receipt;
        if(!exists&&!previous.isEmpty())throw new IOException("Fresh connection has an unexplained retained receipt; preserve history and recover missing lineage");
        if(exists&&(j.getInt("schema")!=1||id.isEmpty()||!PHASES.contains(phase)||!j.getCompound("identity").equals(identity)||!j.getCompound("spec").equals(spec)))throw new IOException("Foreign/corrupt connection journal or changed participant proof");
        if(exists&&!receipt.equals(previous)&&!receipt.equals(id+":pending")&&!receipt.equals(id+":open"))throw new IOException("Connection receipt superseded");
        if(phase.equals("COMMITTED")&&!receipt.equals(id+":open")||phase.equals("ROLLED_BACK")&&!receipt.equals(previous))throw new IOException("Connection terminal receipt/phase disagree");
        check(e,identity,plan.participants(),receipt);boolean pending=exists&&!Set.of("COMMITTED","ROLLED_BACK").contains(phase);
        Map<BlockPos,BlockState> states=new LinkedHashMap<>(),reads=new LinkedHashMap<>(),targets=new LinkedHashMap<>();Map<BlockPos,CompoundTag> nbt=new LinkedHashMap<>();ListTag before=new ListTag();
        for(var cap:plan.caps()){
            loaded(e.level(),cap.pos());e.allow(cap.pos(),cap.cellOwner());BlockState live=e.level().getBlockState(cap.pos()),open=Blocks.AIR.defaultBlockState();
            if(pending?(!live.equals(cap.closed())&&!live.equals(open)):!live.equals(phase.equals("COMMITTED")?open:cap.closed()))throw new IOException("Connection source/target cap conflict at "+cap.pos());
            if(e.level().getBlockEntity(cap.pos())!=null)throw new IOException("Connection would destroy unexpected cap inventory");states.put(cap.pos(),live);targets.put(cap.pos(),rollback?cap.closed():open);
            CompoundTag n=new CompoundTag();n.putLong("pos",cap.pos().asLong());n.put("before",NbtUtils.writeBlockState(cap.closed()));n.put("beforeNbt",new CompoundTag());before.add(n);
        }
        if(exists&&!j.getList("actualBefore",Tag.TAG_COMPOUND).equals(before))throw new IOException("Connection exact before-images changed");
        for(var read:plan.reads()){loaded(e.level(),read.pos());if(!e.level().getBlockState(read.pos()).equals(read.state()))throw new IOException("Connection protected support/route/source changed at "+read.pos());reads.put(read.pos(),read.state());capture(e.level(),read.pos(),read.state(),nbt);}
        for(var cap:plan.caps())for(Direction d:Direction.values()){BlockPos p=cap.pos().relative(d);if(states.containsKey(p)||reads.containsKey(p))continue;loaded(e.level(),p);var state=e.level().getBlockState(p);reads.put(p,state);capture(e.level(),p,state,nbt);}
        if(action==Action.STATUS)return new Result("Connection "+plan.corner()+": "+phase+" (purchased levels unchanged)",0);
        if(!exists&&action!=Action.APPLY)throw new IOException("No retained connection operation");
        if(action==Action.APPLY&&pending)throw new IOException("Connection pending; resume or rollback retained operation");
        if(phase.equals("COMMITTED")&&!rollback)return new Result("Connection already open; zero writes",0);
        if(phase.equals("ROLLED_BACK")){if(rollback)return new Result("Connection already restored; zero writes",0);throw new IOException("Retain rolled-back history and create a fresh connection journal");}
        if(phase.equals("ROLLING_BACK")&&!rollback)throw new IOException("Finish coordinated connection rollback");
        StreetscapeService.requireLoadedEntities(e.level(),List.of(bounds(states,reads)));List<ConstructionHazards.Write> writes=new ArrayList<>();for(var row:targets.entrySet())if(!row.getValue().equals(states.get(row.getKey())))writes.add(new ConstructionHazards.Write(row.getKey(),row.getValue()));PlannedWorld.verify(e.level(),targets,FLAGS);var entities=ConstructionHazards.inspect(e.level(),writes);
        if(!exists){e.save();verify(e,states,reads,nbt);entities.verify();check(e,identity,plan.participants(),receipt);j=new CompoundTag();j.putInt("schema",1);j.putUUID("id",UUID.randomUUID());j.put("identity",identity);j.put("spec",spec);j.putString("previousReceipt",previous);j.put("actualBefore",before);j.putString("phase","PREPARED");ProducerJournal.write(e.file(),j,false);id=j.getUUID("id").toString();fault.at("prepared",0);}
        verify(e,states,reads,nbt);entities.verify();check(e,identity,plan.participants(),receipt);
        if(!rollback&&!receipt.equals(id+":open")&&!receipt.equals(id+":pending")){receipt=id+":pending";e.receipt(receipt);e.save();check(e,identity,plan.participants(),receipt);verify(e,states,reads,nbt);entities.verify();fault.at("reserved",0);}
        advance(e.file(),j,rollback?"ROLLING_BACK":"APPLYING");List<Cap> ordered=new ArrayList<>(plan.caps());if(rollback)Collections.reverse(ordered);int count=0;
        for(var cap:ordered){check(e,identity,plan.participants(),receipt);verify(e,states,reads,nbt);entities.verify();e.allow(cap.pos(),cap.cellOwner());BlockState wanted=targets.get(cap.pos());if(!states.get(cap.pos()).equals(wanted)){if(!e.level().setBlock(cap.pos(),wanted,FLAGS))throw new IOException("Connection cap write refused");states.put(cap.pos(),wanted);fault.at(rollback?"rollback_write":"write",++count);}}
        verify(e,states,reads,nbt);entities.verify();check(e,identity,plan.participants(),receipt);e.save();fault.at("physical_saved",count);verify(e,states,reads,nbt);entities.verify();check(e,identity,plan.participants(),receipt);
        receipt=rollback?previous:id+":open";e.receipt(receipt);e.save();fault.at("ledger_saved",count);check(e,identity,plan.participants(),receipt);verify(e,states,reads,nbt);entities.verify();advance(e.file(),j,rollback?"ROLLED_BACK":"COMMITTED");return new Result("Coordinated connection "+(rollback?"restored":"opened")+"; purchased receipts/levels unchanged",count);
    }
    private static String receipt(Environment e)throws IOException {String a=e.firstReceipt()==null?"":e.firstReceipt(),b=e.secondReceipt()==null?"":e.secondReceipt();if(!a.equals(b))throw new IOException("Split participant connection receipt; preserve and recover retained journal");return a;}
    private static void check(Environment e,CompoundTag identity,List<Participant> pair,String receipt)throws IOException {e.requirements();if(!e.identity().equals(identity)||!e.currentParticipants().equals(pair)||!receipt(e).equals(receipt))throw new IOException("Connection owner, purchased participant or linked receipt changed");}
    private static void loaded(ServerLevel l,BlockPos p)throws IOException {if(!l.hasChunkAt(p))throw new IOException("Connection source/support unloaded");}
    private static void capture(ServerLevel l,BlockPos p,BlockState s,Map<BlockPos,CompoundTag> out)throws IOException {BlockEntity be=l.getBlockEntity(p);if(s.hasBlockEntity()){if(be==null||!(s.getBlock() instanceof EntityBlock b))throw new IOException("Missing connection protected inventory");BlockEntity ref=b.newBlockEntity(p,s);if(ref==null||!ref.saveWithFullMetadata().getString("id").equals(be.saveWithFullMetadata().getString("id")))throw new IOException("Foreign connection inventory");out.put(p,be.saveWithFullMetadata().copy());}else if(be!=null)throw new IOException("Unexpected connection read inventory");}
    private static void verify(Environment e,Map<BlockPos,BlockState> states,Map<BlockPos,BlockState> reads,Map<BlockPos,CompoundTag> nbt)throws IOException {for(var map:List.of(states,reads))for(var row:map.entrySet()){loaded(e.level(),row.getKey());if(!e.level().getBlockState(row.getKey()).equals(row.getValue())||!row.getValue().hasBlockEntity()&&e.level().getBlockEntity(row.getKey())!=null)throw new IOException("Connection source/support changed");}for(var row:nbt.entrySet()){BlockEntity be=e.level().getBlockEntity(row.getKey());if(be==null||!be.saveWithFullMetadata().equals(row.getValue()))throw new IOException("Connection current management book/inventory changed");}}
    private static net.minecraft.world.phys.AABB bounds(Map<BlockPos,BlockState> a,Map<BlockPos,BlockState> b){net.minecraft.world.phys.AABB box=null;for(var map:List.of(a,b))for(BlockPos p:map.keySet()){var one=new net.minecraft.world.phys.AABB(p).inflate(2);box=box==null?one:box.minmax(one);}return box;}
    private static void advance(Path file,CompoundTag j,String phase)throws IOException {if(!ProducerJournal.read(file).equals(j))throw new IOException("Connection journal changed during operation");j.putString("phase",phase);ProducerJournal.write(file,j,true);}
}
