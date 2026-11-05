package com.quin.catancraft.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Exact-mask gate motion foundation. No production geometry, registration, price or command.
 * The caller must supply independently authorized geometry, a stable world identity and a
 * read-only city authorization/exclusivity guard, including existing construction/recovery.
 * The original contract remains mandatory on resume; a journal never authorizes new geometry.
 */
final class GateTransition35 {
    enum State { OPEN, CLOSED }
    record Cell(BlockPos pos, BlockState open, BlockState closed) {
        Cell { pos=Objects.requireNonNull(pos).immutable();Objects.requireNonNull(open);Objects.requireNonNull(closed); }
        BlockState state(State s){return s==State.OPEN?open:closed;}
    }
    record Read(BlockPos pos, BlockState state) {
        Read { pos=Objects.requireNonNull(pos).immutable();Objects.requireNonNull(state); }
    }
    record GateContract(String id,String revision,State initial,List<Cell> cells,List<Read> reads) {
        GateContract { cells=List.copyOf(cells);reads=List.copyOf(reads);Objects.requireNonNull(initial); }
    }
    interface Environment {
        ServerLevel level(); String worldId(); String city(); UUID owner();
        /** Must check current actor/owner, approved contract and other city operations without mutation. */
        void guard() throws IOException;
        /** Persist affected world chunks before a journal can claim this slice/terminal state durable. */
        void flush() throws IOException;
    }
    private static final int FLAGS=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS;
    private static final int MAX_REQUESTS=4096;
    private static final Map<Path,UUID> ACTIVE=new HashMap<>();
    private GateTransition35(){}

    static Path journalPath(Environment e,GateContract c)throws IOException {
        token(e.city());token(c.id());
        Path root=e.level().getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path path=root.resolve("data/catancraft_gate35_"+e.city()+"_"+c.id()+".dat");
        Path walk=path.getRoot();
        for(Path part:path){walk=walk.resolve(part);if(Files.isSymbolicLink(walk))throw new IOException("Gate journal path contains a symlink");
            if(!walk.equals(path)&&Files.exists(walk,LinkOption.NOFOLLOW_LINKS)&&!Files.isDirectory(walk,LinkOption.NOFOLLOW_LINKS))throw new IOException("Gate journal ancestor is not a directory");}
        return path;
    }
    private static void token(String value)throws IOException {
        if(value==null||!value.matches("[a-z0-9_]{1,48}"))throw new IOException("Invalid gate/city identity");
    }
    private static CompoundTag identity(Environment e,GateContract c)throws IOException {
        if(e.worldId()==null||e.worldId().isBlank()||e.worldId().length()>128||e.owner()==null)throw new IOException("Explicit gate world/owner identity required");
        Path file=journalPath(e,c);CompoundTag n=new CompoundTag();
        n.putString("worldRoot",file.getParent().getParent().toString());n.putString("worldId",e.worldId());
        n.putString("dimension",e.level().dimension().location().toString());n.putString("city",e.city());
        n.putUUID("owner",e.owner());n.putString("file",file.getFileName().toString());return n;
    }
    private static void plain(BlockState state)throws IOException {
        if(state.hasBlockEntity()||!state.getFluidState().isEmpty())throw new IOException("Gate foundation requires block-entity-free, fluid-free states and read context");
    }
    private static List<Cell> ordered(GateContract c){return c.cells().stream().sorted(Comparator.comparingLong(x->x.pos().asLong())).toList();}
    private static CompoundTag describe(GateContract c)throws IOException {
        token(c.id());if(c.revision()==null||c.revision().isBlank()||c.revision().length()>128||c.cells().isEmpty()||c.cells().size()>512||c.reads().size()>4096)throw new IOException("Invalid bounded gate contract");
        Set<BlockPos> all=new HashSet<>();ListTag cells=new ListTag(),reads=new ListTag();AABB box=null;
        for(Cell cell:ordered(c)){
            if(!all.add(cell.pos())||cell.open().equals(cell.closed()))throw new IOException("Duplicate or unchanged gate write");plain(cell.open());plain(cell.closed());
            CompoundTag row=new CompoundTag();row.putLong("pos",cell.pos().asLong());row.put("open",NbtUtils.writeBlockState(cell.open()));row.put("closed",NbtUtils.writeBlockState(cell.closed()));cells.add(row);
            box=box==null?new AABB(cell.pos()):box.minmax(new AABB(cell.pos()));
        }
        if(box.getXsize()>64||box.getYsize()>32||box.getZsize()>64)throw new IOException("Gate mask exceeds bounded motion envelope");
        for(Read read:c.reads().stream().sorted(Comparator.comparingLong(x->x.pos().asLong())).toList()){
            if(!all.add(read.pos()))throw new IOException("Duplicate/overlapping gate read");plain(read.state());
            if(!box.inflate(2).contains(read.pos().getX()+.5,read.pos().getY()+.5,read.pos().getZ()+.5))throw new IOException("Gate read outside bounded context");
            CompoundTag row=new CompoundTag();row.putLong("pos",read.pos().asLong());row.put("state",NbtUtils.writeBlockState(read.state()));reads.add(row);
        }
        for(Cell cell:c.cells())for(Direction d:Direction.values())if(!all.contains(cell.pos().relative(d)))throw new IOException("Gate requires an exact six-face boundary read mask");
        CompoundTag n=new CompoundTag();n.putInt("schema",1);n.putString("id",c.id());n.putString("revision",c.revision());n.putString("initial",c.initial().name());n.put("cells",cells);n.put("reads",reads);return n;
    }
    static AABB bounds(GateContract c)throws IOException {
        describe(c);AABB box=null;for(Cell cell:c.cells())box=box==null?new AABB(cell.pos()):box.minmax(new AABB(cell.pos()));return box.inflate(2);
    }
    private static State state(String value)throws IOException {try{return State.valueOf(value);}catch(IllegalArgumentException ex){throw new IOException("Unknown gate state",ex);}}
    private static void validateJournal(CompoundTag j,CompoundTag identity,CompoundTag contract)throws IOException {
        if(!j.getAllKeys().equals(Set.of("schema","identity","contract","request","from","target","phase","requests","slice"))
            ||!j.contains("schema",Tag.TAG_INT)||j.getInt("schema")!=1||!j.contains("identity",Tag.TAG_COMPOUND)||!j.getCompound("identity").equals(identity)
            ||!j.contains("contract",Tag.TAG_COMPOUND)||!j.getCompound("contract").equals(contract)||!j.hasUUID("request")
            ||!j.contains("from",Tag.TAG_STRING)||!j.contains("target",Tag.TAG_STRING)||!j.contains("phase",Tag.TAG_STRING)
            ||!Set.of("MOVING","DONE").contains(j.getString("phase"))||!j.contains("slice",Tag.TAG_LONG)||j.getLong("slice")<0
            ||!j.contains("requests",Tag.TAG_LIST))throw new IOException("Foreign, malformed or changed gate journal binding");
        State from=state(j.getString("from")),target=state(j.getString("target"));if(from==target)throw new IOException("Gate journal has no transition");
        ListTag requests=j.getList("requests",Tag.TAG_COMPOUND);if(requests.isEmpty()||requests.size()>MAX_REQUESTS)throw new IOException("Invalid gate intent history");
        Set<UUID> ids=new HashSet<>();State previous=state(contract.getString("initial"));
        for(Tag tag:requests){CompoundTag row=(CompoundTag)tag;
            if(!row.getAllKeys().equals(Set.of("request","from","target"))||!row.hasUUID("request")||!ids.add(row.getUUID("request"))||!row.contains("from",Tag.TAG_STRING)||!row.contains("target",Tag.TAG_STRING))throw new IOException("Invalid/replayed gate intent");
            State a=state(row.getString("from")),b=state(row.getString("target"));if(a!=previous||a==b)throw new IOException("Broken gate state lineage");previous=b;
        }
        CompoundTag last=requests.getCompound(requests.size()-1);if(!last.getUUID("request").equals(j.getUUID("request"))||!last.getString("from").equals(from.name())||!last.getString("target").equals(target.name()))throw new IOException("Gate current intent disagrees with history");
    }
    private static void authority(Environment e)throws IOException {
        if(!e.level().getServer().isSameThread())throw new IOException("Gate transition requires the server thread");e.guard();
    }
    private static void sources(Environment e,GateContract c,State from,State target,boolean moving)throws IOException {
        for(Read r:c.reads())check(e.level(),r.pos(),r.state(),r.state());
        for(Cell cell:c.cells())check(e.level(),cell.pos(),cell.state(from),cell.state(moving?target:from));
    }
    private static void check(ServerLevel level,BlockPos p,BlockState a,BlockState b)throws IOException {
        if(level.isOutsideBuildHeight(p))throw new IOException("Gate context is outside world build height");
        if(!level.hasChunkAt(p))throw new PreparationDeferred("Gate context chunk is unloaded at "+p);
        BlockState live=level.getBlockState(p);if((!live.equals(a)&&!live.equals(b))||level.getBlockEntity(p)!=null||!live.getFluidState().isEmpty())throw new IOException("Gate source/read mismatch at "+p);
    }
    private static List<ConstructionHazards.Write> remaining(Environment e,GateContract c,State target){
        List<ConstructionHazards.Write> writes=new ArrayList<>();for(Cell cell:ordered(c))if(!e.level().getBlockState(cell.pos()).equals(cell.state(target)))writes.add(new ConstructionHazards.Write(cell.pos(),cell.state(target)));return writes;
    }
    private static ConstructionHazards.Guard safety(Environment e,GateContract c,State target)throws IOException {
        StreetscapeService.requireLoadedEntities(e.level(),List.of(bounds(c)));List<ConstructionHazards.Write> writes=remaining(e,c,target);
        // Every intermediate world, including a paused four-write slice, must remain valid.
        Map<BlockPos,BlockState> prefix=new LinkedHashMap<>();for(var write:writes){prefix.put(write.pos(),write.target());PlannedWorld.verify(e.level(),prefix,FLAGS);}
        return ConstructionHazards.inspect(e.level(),writes);
    }

    /** Repeating an intent resumes it; a different intent cannot replace pending work.
     * Completed older intents never cause a new motion after a later completed cycle. */
    static Runner begin(Environment e,GateContract c,State target,UUID request)throws IOException {
        Objects.requireNonNull(target);Objects.requireNonNull(request);authority(e);CompoundTag spec=describe(c),id=identity(e,c);Path file=journalPath(e,c);
        Path cityKey=file.getParent().resolve("gate35_city_"+e.city());UUID lock=UUID.randomUUID();
        synchronized(ACTIVE){if(ACTIVE.putIfAbsent(cityKey,lock)!=null)throw new IOException("Another gate motion owns this city; close its runner before recovery");}
        try{
            CompoundTag journal=null;byte[] bytes=null;State from=c.initial();
            if(Files.exists(file,LinkOption.NOFOLLOW_LINKS)){
                bytes=capture(file);journal=ProducerJournal.read(bytes);validateJournal(journal,id,spec);
                State oldTarget=state(journal.getString("target"));boolean moving=journal.getString("phase").equals("MOVING");
                if(moving){if(!journal.getUUID("request").equals(request)||oldTarget!=target)throw new IOException("An immutable gate intent is still pending");from=state(journal.getString("from"));}
                else{
                    from=oldTarget;
                    for(Tag tag:journal.getList("requests",Tag.TAG_COMPOUND)){CompoundTag row=(CompoundTag)tag;if(row.getUUID("request").equals(request)&&(target!=from||!row.getString("target").equals(target.name())))throw new IOException("Completed gate intent replay cannot start another motion");}
                }
                sources(e,c,from,target,moving);
                if(!moving&&target==from){sameFile(file,bytes);Runner result=new Runner(e,c,id,spec,file,cityKey,lock,journal,bytes,from,target);result.complete=true;result.close();return result;}
                if(moving){safety(e,c,target);return new Runner(e,c,id,spec,file,cityKey,lock,journal,bytes,from,target);}
            }else{
                sources(e,c,from,target,false);
                if(from==target){Runner result=new Runner(e,c,id,spec,file,cityKey,lock,null,null,from,target);result.complete=true;result.close();return result;}
            }
            safety(e,c,target);authority(e);if(!identity(e,c).equals(id))throw new IOException("Gate owner/world changed during preparation");sources(e,c,from,target,false);
            ListTag history=journal==null?new ListTag():journal.getList("requests",Tag.TAG_COMPOUND).copy();if(history.size()>=MAX_REQUESTS)throw new IOException("Gate retained intent history is full; explicit archival contract required");
            CompoundTag intent=new CompoundTag();intent.putUUID("request",request);intent.putString("from",from.name());intent.putString("target",target.name());history.add(intent);
            CompoundTag next=new CompoundTag();next.putInt("schema",1);next.put("identity",id.copy());next.put("contract",spec.copy());next.putUUID("request",request);next.putString("from",from.name());next.putString("target",target.name());next.putString("phase","MOVING");next.put("requests",history);next.putLong("slice",0);
            // Establish a durable source before recording permission to reconcile a mixture.
            // A source-save failure has no journal and no gate writes from this request.
            envSourceFlush(e,c,id,from,target);sameFile(file,bytes);ProducerJournal.write(file,next,bytes!=null);return new Runner(e,c,id,spec,file,cityKey,lock,next,captureWritten(file,next),from,target);
        }catch(IOException|RuntimeException error){release(cityKey,lock);throw error;}
    }
    private static void envSourceFlush(Environment e,GateContract c,CompoundTag id,State from,State target)throws IOException {
        e.flush();authority(e);if(!identity(e,c).equals(id))throw new IOException("Gate identity changed while saving source");sources(e,c,from,target,false);safety(e,c,target);
    }
    private static void sameFile(Path file,byte[] expected)throws IOException {
        if(Files.isSymbolicLink(file))throw new IOException("Gate journal symlink refused");
        if(expected==null){if(Files.exists(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("Gate journal appeared during preparation");}
        else if(!Arrays.equals(expected,capture(file)))throw new IOException("Gate journal changed since it was read");
    }
    private static byte[] capture(Path file)throws IOException {
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>8*1024*1024+40)throw new IOException("Invalid bounded gate journal file");
        byte[] bytes=Files.readAllBytes(file);ProducerJournal.read(bytes);return bytes;
    }
    private static byte[] captureWritten(Path file,CompoundTag expected)throws IOException {
        byte[] bytes=capture(file);if(!ProducerJournal.read(bytes).equals(expected))throw new IOException("Gate journal changed immediately after durable write");return bytes;
    }
    private static void release(Path key,UUID lock){synchronized(ACTIVE){ACTIVE.remove(key,lock);}}

    static final class Runner implements AutoCloseable {
        private final Environment env;private final GateContract contract;private final CompoundTag identity,spec;private final Path file,cityKey;private final UUID lock;
        private final State from,target;private CompoundTag journal;private byte[] bytes;private boolean closed,complete;
        private Runner(Environment e,GateContract c,CompoundTag id,CompoundTag spec,Path file,Path cityKey,UUID lock,CompoundTag journal,byte[] bytes,State from,State target){this.env=e;this.contract=c;this.identity=id;this.spec=spec;this.file=file;this.cityKey=cityKey;this.lock=lock;this.journal=journal;this.bytes=bytes;this.from=from;this.target=target;}
        boolean done(){return complete;}
        private void guard()throws IOException {
            authority(env);if(!identity(env,contract).equals(identity)||!describe(contract).equals(spec))throw new IOException("Gate owner/world/contract changed");
            synchronized(ACTIVE){if(!lock.equals(ACTIVE.get(cityKey)))throw new IOException("Gate runner lost exclusive city ownership");}
            if(!journalPath(env,contract).equals(file))throw new IOException("Gate canonical journal path changed");sameFile(file,bytes);validateJournal(journal,identity,spec);
        }
        /** One caller-scheduled slice, at most four direct writes. Failure retains the durable
         * MOVING intent and releases the runner; an explicitly authorized retry may resume it. */
        int step()throws IOException {
            if(complete)return 0;if(closed)throw new IOException("Closed gate runner requires explicit resume");
            try{
                guard();sources(env,contract,from,target,true);ConstructionHazards.Guard entities=safety(env,contract,target);int changed=0;
                for(ConstructionHazards.Write write:remaining(env,contract,target)){
                    if(changed==4)break;guard();sources(env,contract,from,target,true);entities.verify();
                    if(!env.level().setBlock(write.pos(),write.target(),FLAGS)||!env.level().getBlockState(write.pos()).equals(write.target()))throw new IOException("Gate write did not reach exact target at "+write.pos());
                    changed++;entities.verify();sources(env,contract,from,target,true);
                }
                env.flush();guard();sources(env,contract,from,target,true);entities.verify();boolean all=remaining(env,contract,target).isEmpty();
                CompoundTag next=journal.copy();if(journal.getLong("slice")==Long.MAX_VALUE)throw new IOException("Gate slice counter exhausted");next.putLong("slice",journal.getLong("slice")+1);if(all)next.putString("phase","DONE");
                sameFile(file,bytes);ProducerJournal.write(file,next,true);journal=next;bytes=captureWritten(file,next);
                if(all){complete=true;close();}return changed;
            }catch(IOException|RuntimeException error){close();throw error;}
        }
        @Override public void close(){if(!closed){closed=true;release(cityKey,lock);}}
    }
}
