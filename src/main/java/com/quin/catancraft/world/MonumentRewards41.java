package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.*;
import com.quin.catancraft.monument.MonumentManager;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.SecureRandom;
import java.util.*;

/** Frozen hidden reward, durable winner, and same-native-ledger nation credit.
 * New rounds use explicit provisional supply pools; legacy open rounds keep their
 * frozen outcome. Equipment delivery is a separate winner-bound claim operation. */
public final class MonumentRewards41 {
    private static final int MAX_HISTORY=4096,MAX_MONUMENTS=64,MAX_FILE=8*1024*1024+40;
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final Fault NONE=p->{};
    private static boolean busy;
    public enum SavePoint { BEFORE_ACTIVATION_SAVE,AFTER_ACTIVATION_SAVE,BEFORE_CAPTURE_SAVE,AFTER_CAPTURE_SAVE,BEFORE_INTENT,AFTER_INTENT,AFTER_CREDIT,AFTER_CREDIT_SAVE,BEFORE_COMMITTED,AFTER_COMMITTED }
    public interface Fault { void at(SavePoint point)throws IOException; }
    public interface Environment { MinecraftServer server();CatanSavedData data();void persist()throws IOException; }
    public record Result(boolean complete,String message,UUID rewardId,String description) {}
    private MonumentRewards41() {}
    private static Environment runtime(MinecraftServer server,CatanSavedData data){return new Environment(){
        public MinecraftServer server(){return server;}public CatanSavedData data(){return data;}
        public void persist(){data.setDirty();server.overworld().getDataStorage().save();}
    };}
    public static void stopped(MinecraftServer server) { /* No server/player cache. */ }
    public static void prepareActivation(MinecraftServer server,CatanSavedData data,MonumentData monument)throws IOException {
        prepare(runtime(server,data),monument,MonumentSupply44.table(monument.type()),RANDOM.nextLong(),UUID.randomUUID(),NONE,false);
    }
    public static boolean readyActivation(MinecraftServer server,CatanSavedData data,MonumentData monument)throws IOException {
        Environment e=runtime(server,data);if(controlledRecord(e,monument))return false;
        // An already-active pre-journal round retains its original fixed payout.
        // New rounds have their supply table frozen by prepareActivation above.
        if(monument.rewardActivation41()==null)
            prepare(e,monument,RewardTable41.compatibility(monument.type()),RANDOM.nextLong(),UUID.randomUUID(),NONE,false);
        return readyActivation(e,monument,false);
    }
    private static boolean readyActivation(Environment e,MonumentData monument,boolean fixture)throws IOException {
        try(Lock lock=lock(e)){
            CompoundTag state=state(e,monument,fixture);if(!state.getString("phase").equals("OPEN"))return false;
            check(e,lock,monument,state,fixture);
            if(!nativeActivation(e,monument,state))saveVerified(e,monument,state,fixture);
            check(e,lock,monument,state,fixture);return true;
        }
    }
    public static Result completeCapture(MinecraftServer server,CatanSavedData data,MonumentData monument,UUID winner)throws IOException {
        return capture(runtime(server,data),monument,winner,NONE,false);
    }
    /** Called before presence and activation. Includes post-credit records whose active pointer is already cleared. */
    public static boolean recover(MinecraftServer server,CatanSavedData data)throws IOException {return recover(runtime(server,data),NONE,false);}
    public static boolean canReplace(MinecraftServer server,CatanSavedData data,MonumentData monument)throws IOException {
        return canReplace(runtime(server,data),monument,false);
    }
    private static boolean canReplace(Environment e,MonumentData monument,boolean fixture)throws IOException {
        try(Lock lock=lock(e)){
            requireMonument(e,monument);for(MonumentData current:monuments(e)){
                if(current.rewardActivation41()==null)continue;
                if(!fixture&&controlledRecord(e,current))return false;
                CompoundTag state=state(e,current,recordScope(e,current,fixture));
                if(!state.getString("phase").equals("OPEN")&&!terminal(e,current,state))return false;
            }
            lock.verify();return true;
        }
    }
    /** Reveals only credited, terminal winner-bound pending references; active hidden rolls stay private. */
    public static List<CompoundTag> pendingRewards(MinecraftServer server,CatanSavedData data,UUID nationId)throws IOException {
        return pendingRewards(runtime(server,data),nationId,false);
    }
    private static List<CompoundTag> pendingRewards(Environment e,UUID nationId,boolean fixture)throws IOException {
        try(Lock lock=lock(e)){
            if(nationId==null||e.data().nation(nationId)==null)throw new IOException("Original reward nation is unavailable");
            List<CompoundTag> result=new ArrayList<>();for(Path path:history(e)){
                byte[] bytes=read(path);CompoundTag intent=readIntent(e,bytes,fixture);UUID id=intent.getUUID("id");
                if(!path.equals(intentPath(e,id)))throw new IOException("Foreign reward history filename");
                if(!intent.getUUID("winner").equals(nationId))continue;String hash=TownHallRevisions.sha(bytes);
                if(!committed(e,id,hash)||!paid(e,id,hash)||!nativePaid(e,id,hash))continue;
                if(!RewardTable41.Bundle.load(intent.getCompound("roll")).entitlements().isEmpty())result.add(intent.copy());
            }
            lock.verify();return List.copyOf(result);
        }
    }
    static void fixturePrepare(Environment e,MonumentData m,RewardTable41 table,long seed,UUID id,Fault fault)throws IOException {fixture(e);prepare(e,m,table,seed,id,fault,true);}
    static Result fixtureCapture(Environment e,MonumentData m,UUID winner,Fault fault)throws IOException {fixture(e);return capture(e,m,winner,fault,true);}
    static boolean fixtureRecover(Environment e,Fault fault)throws IOException {fixture(e);return recover(e,fault,true);}
    static boolean fixtureReadyActivation(Environment e,MonumentData m)throws IOException {fixture(e);return readyActivation(e,m,true);}
    static boolean fixtureCanReplace(Environment e,MonumentData m)throws IOException {fixture(e);return canReplace(e,m,true);}
    static List<CompoundTag> fixturePendingRewards(Environment e,UUID nationId)throws IOException {fixture(e);return pendingRewards(e,nationId,true);}
    static List<CompoundTag> fixturePendingRewards43(MinecraftServer server,CatanSavedData data,UUID nationId)throws IOException {
        Environment e=runtime(server,data);fixture(e);
        if(!Set.of("controlled-catancraft_claims43-run","controlled-catancraft_claims43_tacz-run",
                "controlled-catancraft_claims43_sbw-run","controlled-catancraft_native43_tacz_restart-run",
                "controlled-catancraft_native43_sbw_restart-run").contains(root(e).getParent().getFileName().toString()))
            throw new IOException("Controlled claim43 reward fixture required");
        return pendingRewards(e,nationId,true);
    }
    private static void prepare(Environment e,MonumentData m,RewardTable41 table,long seed,UUID id,Fault fault,boolean fixture)throws IOException {
        try(Lock lock=lock(e)){
            requireMonument(e,m);if(e.data().activeMonument()!=m)throw new IOException("Set the current monument before preparing its activation");
            for(MonumentData other:monuments(e))if(other.rewardActivation41()!=null){
                CompoundTag prior=state(e,other,recordScope(e,other,fixture));
                if(!prior.getString("phase").equals("OPEN")&&!terminal(e,other,prior))throw new IOException("An earlier monument reward must finish recovery first");
            }
            if(m.rewardActivation41()==null&&nativeRecord(e,m)!=null&&nativeRecord(e,m).contains("rewardActivation41"))throw new IOException("Saved reward activation is missing from live state; retain native history for recovery");
            if(m.rewardActivation41()!=null){
                CompoundTag prior=state(e,m,fixture);
                if(prior.getString("phase").equals("OPEN")){
                    // An interrupted activation or forced reopening retains the original hidden roll.
                    if(!nativeActivation(e,m,prior)){fault.at(SavePoint.BEFORE_ACTIVATION_SAVE);check(e,lock,m,prior,fixture);saveVerified(e,m,prior,fixture);}
                    check(e,lock,m,prior,fixture);fault.at(SavePoint.AFTER_ACTIVATION_SAVE);check(e,lock,m,prior,fixture);return;
                }
            }
            if(id==null||id.equals(new UUID(0,0)))throw new IOException("Explicit reward activation identity required");
            if(history(e).size()>=MAX_HISTORY||Files.exists(intentPath(e,id),LinkOption.NOFOLLOW_LINKS)||Files.exists(receiptPath(e,id),LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Reward history is full or activation identity already exists");
            for(MonumentData other:monuments(e))if(other.rewardActivation41() instanceof CompoundTag old&&old.hasUUID("id")&&old.getUUID("id").equals(id))throw new IOException("Reward activation identity already assigned");
            RewardTable41.Roll roll=table.roll(seed);CompoundTag state=new CompoundTag();state.putInt("schema",1);state.putUUID("id",id);state.putString("phase","OPEN");
            state.put("identity",identity(e,fixture));state.put("monument",geometry(m));state.putString("tableRevision",table.revision());state.putString("tableSHA256",hashTag(table.save()));
            state.putString("entry",roll.entryId());state.put("roll",roll.reward().save());m.setRewardActivation41(state);e.data().setDirty();
            fault.at(SavePoint.BEFORE_ACTIVATION_SAVE);check(e,lock,m,state,fixture);saveVerified(e,m,state,fixture);fault.at(SavePoint.AFTER_ACTIVATION_SAVE);check(e,lock,m,state,fixture);
        }
    }
    private static Result capture(Environment e,MonumentData m,UUID winner,Fault fault,boolean fixture)throws IOException {
        try(Lock lock=lock(e)){
            CompoundTag state=state(e,m,fixture);
            if(state.getString("phase").equals("OPEN")){
                if(e.data().activeMonument()!=m||winner==null||e.data().nation(winner)==null||!winner.equals(m.capturingNationId())
                        ||m.captureProgressTicks()<MonumentManager.CAPTURE_TICKS)throw new IOException("Capture has no completed original winner");
                if(!nativeActivation(e,m,state))saveVerified(e,m,state,fixture);check(e,lock,m,state,fixture);
                long time=e.server().overworld().getGameTime(),next;
                try{next=Math.addExact(time,MonumentManager.ROTATION_GAP_TICKS);}catch(ArithmeticException failure){throw new IOException("Monument cooldown overflow",failure);}
                if(time<0)throw new IOException("Invalid capture clock");
                state=state.copy();state.putString("phase","CAPTURED");state.putUUID("winner",winner);state.putLong("captureTime",time);state.putLong("nextActivation",next);
                m.setRewardActivation41(state);e.data().setDirty();fault.at(SavePoint.BEFORE_CAPTURE_SAVE);check(e,lock,m,state,fixture);saveVerified(e,m,state,fixture);
                fault.at(SavePoint.AFTER_CAPTURE_SAVE);check(e,lock,m,state,fixture);
            }else if(winner==null||!state.getUUID("winner").equals(winner))throw new IOException("A different nation cannot adopt the original reward");
            return settle(e,lock,m,state,fault,fixture);
        }
    }
    private static boolean recover(Environment e,Fault fault,boolean fixture)throws IOException {
        try(Lock lock=lock(e)){
            boolean processed=false;for(MonumentData m:monuments(e)){
                if(m.rewardActivation41()==null||(!fixture&&controlledRecord(e,m)))continue;
                boolean scope=recordScope(e,m,fixture);CompoundTag state=state(e,m,scope);if(state.getString("phase").equals("OPEN"))continue;
                if(fixture&&!scope){if(!terminal(e,m,state))throw new IOException("Runtime reward must finish recovery before fixture settlement");continue;}
                if(terminal(e,m,state))continue;
                settle(e,lock,m,state,fault,fixture);processed=true;
            }
            return processed;
        }
    }
    private static Result settle(Environment e,Lock lock,MonumentData m,CompoundTag original,Fault fault,boolean fixture)throws IOException {
        CompoundTag state=original.copy();UUID id=state.getUUID("id");
        if(terminal(e,m,state))return result(state,"Reward already settled; no repeated credit.");
        if(e.data().activeMonument()!=null&&e.data().activeMonument()!=m)throw new IOException("Another capture is active; original reward retained for recovery");
        check(e,lock,m,state,fixture);
        if(e.data().nation(state.getUUID("winner"))==null)throw new IOException("Original winning nation is unavailable; reward retained");
        if(state.getString("phase").equals("CAPTURED")&&!nativeActivation(e,m,state))saveVerified(e,m,state,fixture);
        check(e,lock,m,state,fixture);
        CompoundTag intent=intent(state);Path path=intentPath(e,id);
        if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS)){
            if(!state.getString("phase").equals("CAPTURED")||e.data().placedMapAsset(paidKey(id))!=null)throw new IOException("Credited reward lost its original intent");
            if(history(e).size()>=MAX_HISTORY)throw new IOException("Reward history exhausted; retain existing records");
            fault.at(SavePoint.BEFORE_INTENT);check(e,lock,m,state,fixture);ProducerJournal.write(path,intent,false);
        }
        byte[] bytes=read(path);if(!readIntent(e,bytes,fixture).equals(intent))throw new IOException("Original reward intent changed");String hash=TownHallRevisions.sha(bytes);
        fault.at(SavePoint.AFTER_INTENT);check(e,lock,m,state,fixture);unchanged(e,id,bytes);
        if(state.getString("phase").equals("CAPTURED")&&!nativeActivation(e,m,state))throw new IOException("Original captured reward is not yet durable");nativeHistoryGuard(e,m);
        if(!paid(e,id,hash)){
            if(!state.getString("phase").equals("CAPTURED"))throw new IOException("Settled reward lost its credit receipt");
            NationData nation=e.data().nation(state.getUUID("winner"));RewardTable41.Bundle bundle=RewardTable41.Bundle.load(state.getCompound("roll"));
            preflight(nation,bundle);check(e,lock,m,state,fixture);unchanged(e,id,bytes);
            nation.addTreasury(bundle.money());bundle.resources().forEach(nation::addResource);e.data().setPlacedMapAsset(paidKey(id),hash);
            state=state.copy();state.putString("phase","SETTLED");m.setRewardActivation41(state);m.resetCapture();
            if(e.data().activeMonument()==m)e.data().setActiveMonumentId(null);
            e.data().setNextMonumentActivationGameTime(state.getLong("nextActivation"));e.data().setDirty();fault.at(SavePoint.AFTER_CREDIT);
        }else if(!state.getString("phase").equals("SETTLED"))throw new IOException("Reward credit receipt conflicts with captured state");
        check(e,lock,m,state,fixture);unchanged(e,id,bytes);saveVerified(e,m,state,fixture);fault.at(SavePoint.AFTER_CREDIT_SAVE);
        check(e,lock,m,state,fixture);unchanged(e,id,bytes);
        if(!paid(e,id,hash)||!nativePaid(e,id,hash))throw new IOException("Reward credit was not durably verified");
        fault.at(SavePoint.BEFORE_COMMITTED);check(e,lock,m,state,fixture);unchanged(e,id,bytes);
        if(!paid(e,id,hash)||!nativePaid(e,id,hash))throw new IOException("Reward credit changed at completion");
        Path receipt=receiptPath(e,id);
        if(Files.exists(receipt,LinkOption.NOFOLLOW_LINKS)){if(!committed(e,id,hash))throw new IOException("Invalid reward completion");}
        else {CompoundTag done=new CompoundTag();done.putInt("schema",1);done.putUUID("id",id);done.putString("intentSHA256",hash);done.putString("phase","COMMITTED");ProducerJournal.write(receipt,done,false);}
        if(!committed(e,id,hash))throw new IOException("Reward completion readback failed");fault.at(SavePoint.AFTER_COMMITTED);return result(state,"National reward settled once.");
    }
    private static Result result(CompoundTag state,String message)throws IOException{return new Result(true,message,state.getUUID("id"),RewardTable41.Bundle.load(state.getCompound("roll")).describe());}
    private static void preflight(NationData nation,RewardTable41.Bundle bundle)throws IOException {
        if(nation==null)throw new IOException("Original winning nation missing");try{if(nation.treasury()<0)throw new IOException("Malformed negative national treasury");Math.addExact(nation.treasury(),bundle.money());for(var e:bundle.resources().entrySet()){if(nation.resource(e.getKey())<0)throw new IOException("Malformed negative national resource");Math.addExact(nation.resource(e.getKey()),e.getValue());}}
        catch(ArithmeticException failure){throw new IOException("Reward would overflow the national stockpile; retain it for recovery",failure);}
    }
    private static boolean terminal(Environment e,MonumentData m,CompoundTag state)throws IOException {
        if(state.getString("phase").equals("OPEN"))return false;UUID id=state.getUUID("id");Path path=intentPath(e,id),receipt=receiptPath(e,id);
        if(!Files.exists(receipt,LinkOption.NOFOLLOW_LINKS))return false;
        if(!state.getString("phase").equals("SETTLED"))throw new IOException("Terminal reward conflicts with activation phase");
        byte[] bytes=read(path);String hash=TownHallRevisions.sha(bytes);
        if(!readIntent(e,bytes,state.getCompound("identity").getString("scope").equals("controlled_fixture")).equals(intent(state))
                ||!committed(e,id,hash)||!paid(e,id,hash)||!nativePaid(e,id,hash))throw new IOException("Terminal reward lost its original credit proof");
        CompoundTag disk=nativeRecord(e,m);if(disk==null||!state.equals(disk.get("rewardActivation41")))throw new IOException("Terminal reward lost its saved activation");
        return true;
    }
    private static String paidKey(UUID id){return "monument41_paid:"+id;}
    private static boolean paid(Environment e,UUID id,String hash)throws IOException {String recorded=e.data().placedMapAsset(paidKey(id));if(recorded==null)return false;if(!hash.equals(recorded))throw new IOException("Foreign reward credit receipt");return true;}
    private static boolean nativePaid(Environment e,UUID id,String hash)throws IOException {return nativeData(e).getCompound("placedMapAssets").getString(paidKey(id)).equals(hash);}
    private static boolean committed(Environment e,UUID id,String hash)throws IOException {
        Path p=receiptPath(e,id);if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))return false;CompoundTag done=ProducerJournal.read(read(p));
        RewardTable41.keys(done,Set.of("schema","id","intentSHA256","phase"));RewardTable41.type(done,"schema",Tag.TAG_INT);RewardTable41.type(done,"intentSHA256",Tag.TAG_STRING);RewardTable41.type(done,"phase",Tag.TAG_STRING);
        if(done.getInt("schema")!=1||!done.hasUUID("id")||!done.getUUID("id").equals(id)||!done.getString("intentSHA256").equals(hash)||!done.getString("phase").equals("COMMITTED"))throw new IOException("Unrecognized reward completion");return true;
    }
    private static CompoundTag state(Environment e,MonumentData m,boolean fixture)throws IOException {
        requireMonument(e,m);Tag raw=m.rewardActivation41();if(!(raw instanceof CompoundTag n))throw new IOException("Missing or malformed monument reward activation");
        validateState(n);if(!n.getCompound("identity").equals(identity(e,fixture))||!n.getCompound("monument").equals(geometry(m)))throw new IOException("Monument reward world or geometry changed");return n;
    }
    private static void validateState(CompoundTag n)throws IOException {
        RewardTable41.type(n,"phase",Tag.TAG_STRING);String phase=n.getString("phase");
        if(!Set.of("OPEN","CAPTURED","SETTLED").contains(phase))throw new IOException("Unknown reward activation phase");
        Set<String> keys=new HashSet<>(Set.of("schema","id","phase","identity","monument","tableRevision","tableSHA256","entry","roll"));
        if(!phase.equals("OPEN"))keys.addAll(Set.of("winner","captureTime","nextActivation"));RewardTable41.keys(n,keys);
        RewardTable41.type(n,"schema",Tag.TAG_INT);RewardTable41.type(n,"identity",Tag.TAG_COMPOUND);RewardTable41.type(n,"monument",Tag.TAG_COMPOUND);
        for(String key:List.of("tableRevision","tableSHA256","entry"))RewardTable41.type(n,key,Tag.TAG_STRING);
        RewardTable41.type(n,"roll",Tag.TAG_COMPOUND);
        if(n.getInt("schema")!=1||!n.hasUUID("id")||n.getUUID("id").equals(new UUID(0,0))||!n.getString("tableSHA256").matches("[0-9a-f]{64}"))throw new IOException("Malformed reward activation identity");
        try{RewardTable41.label(n.getString("tableRevision"));RewardTable41.label(n.getString("entry"));}catch(IllegalArgumentException failure){throw new IOException("Malformed reward table identity",failure);}
        CompoundTag source=n.getCompound("monument");RewardTable41.keys(source,Set.of("id","type","dimension","x","y","z","radius"));
        for(String key:List.of("id","type","dimension"))RewardTable41.type(source,key,Tag.TAG_STRING);
        for(String key:List.of("x","y","z","radius"))RewardTable41.type(source,key,Tag.TAG_INT);
        try{RewardTable41.label(source.getString("id"));RewardTable41.label(source.getString("dimension"));MonumentType.valueOf(source.getString("type"));}catch(IllegalArgumentException failure){throw new IOException("Malformed frozen monument geometry",failure);}
        if(source.getInt("radius")<5||net.minecraft.resources.ResourceLocation.tryParse(source.getString("dimension"))==null)throw new IOException("Malformed frozen monument dimension/radius");
        RewardTable41.Bundle.load(n.getCompound("roll"));
        if(!phase.equals("OPEN")){
            RewardTable41.type(n,"captureTime",Tag.TAG_LONG);RewardTable41.type(n,"nextActivation",Tag.TAG_LONG);
            if(!n.hasUUID("winner")||n.getUUID("winner").equals(new UUID(0,0))||n.getLong("captureTime")<0||n.getLong("nextActivation")<n.getLong("captureTime")
                    ||n.getLong("nextActivation")-n.getLong("captureTime")!=MonumentManager.ROTATION_GAP_TICKS)throw new IOException("Malformed captured reward winner/cooldown");
        }
    }
    private static CompoundTag intent(CompoundTag state){CompoundTag n=state.copy();n.remove("phase");return n;}
    private static CompoundTag readIntent(Environment e,byte[] bytes,boolean fixture)throws IOException {
        CompoundTag intent=ProducerJournal.read(bytes);if(intent.contains("phase"))throw new IOException("Reward intent has an unexpected mutable phase");CompoundTag asState=intent.copy();asState.putString("phase","CAPTURED");validateState(asState);
        if(!intent.getCompound("identity").equals(identity(e,fixture)))throw new IOException("Foreign reward history world");return intent;
    }
    private static CompoundTag geometry(MonumentData m){CompoundTag n=new CompoundTag();n.putString("id",m.id());n.putString("type",m.type().name());n.putString("dimension",m.dimensionId());n.putInt("x",m.x());n.putInt("y",m.y());n.putInt("z",m.z());n.putInt("radius",m.radius());return n;}
    private static CompoundTag identity(Environment e,boolean fixture)throws IOException {CompoundTag n=new CompoundTag();n.putString("root",root(e).toString());n.putString("levelName",e.server().getWorldData().getLevelName());n.putLong("seed",e.server().overworld().getSeed());n.putString("scope",fixture?"controlled_fixture":"runtime");return n;}
    private static void requireMonument(Environment e,MonumentData m)throws IOException {thread(e);if(m==null||e.data().monument(m.id())!=m)throw new IOException("Reward requires the current native monument record");}
    private static List<MonumentData> monuments(Environment e)throws IOException {if(e.data().monuments().size()>MAX_MONUMENTS)throw new IOException("Too many monument reward records");return List.copyOf(e.data().monuments());}
    private static void check(Environment e,Lock lock,MonumentData m,CompoundTag expected,boolean fixture)throws IOException {
        lock.verify();if(!state(e,m,fixture).equals(expected))throw new IOException("Frozen reward activation changed");
        if(expected.getString("phase").equals("SETTLED")){
            if(e.data().activeMonument()!=null||e.data().nextMonumentActivationGameTime()!=expected.getLong("nextActivation"))throw new IOException("Pending reward round/cooldown changed");
        }else if(e.data().activeMonument()!=m)throw new IOException("Original reward is no longer the active capture");
    }
    private static void unchanged(Environment e,UUID id,byte[] original)throws IOException {if(!Arrays.equals(original,read(intentPath(e,id))))throw new IOException("Immutable reward history changed");}
    /** Native history may be at the same or immediately previous save boundary, never ahead or foreign. */
    private static boolean nativeActivation(Environment e,MonumentData m,CompoundTag state)throws IOException {
        CompoundTag disk=nativeRecord(e,m);Tag raw=disk==null?null:disk.get("rewardActivation41");String phase=state.getString("phase");UUID id=state.getUUID("id");
        String liveStamp=e.data().placedMapAsset(paidKey(id));CompoundTag nativeLedger=nativeDataIfPresent(e);String diskStamp=nativeStamp(nativeLedger,id);
        if(!phase.equals("SETTLED")&&(liveStamp!=null||diskStamp!=null))throw new IOException("Unsettled reward has prior credit proof; retain history");
        if(raw==null){if(!phase.equals("OPEN"))throw new IOException("Captured reward lost its saved activation");return false;}
        if(!(raw instanceof CompoundTag previous))throw new IOException("Malformed saved reward activation");validateState(previous);
        if(!previous.getCompound("identity").equals(state.getCompound("identity"))||!previous.getCompound("monument").equals(state.getCompound("monument")))throw new IOException("Saved reward source changed");
        if(!previous.getUUID("id").equals(id)){
            if(!phase.equals("OPEN")||!terminal(e,m,previous))throw new IOException("Another saved reward must remain intact");return false;
        }
        String previousPhase=previous.getString("phase");
        if(phase.equals("OPEN")){
            if(!previous.equals(state))throw new IOException("Saved hidden reward differs from live activation");
            return nativeLedger.getString("activeMonumentId").equals(m.id());
        }
        if(phase.equals("CAPTURED")){
            CompoundTag expectedPrevious=state.copy();expectedPrevious.putString("phase","OPEN");expectedPrevious.remove("winner");expectedPrevious.remove("captureTime");expectedPrevious.remove("nextActivation");
            if(!(previous.equals(state)||(previousPhase.equals("OPEN")&&previous.equals(expectedPrevious))))throw new IOException("Saved winner/outcome is ahead of or conflicts with this capture");
            if(!nativeLedger.getString("activeMonumentId").equals(m.id()))throw new IOException("Saved captured round lost its active pointer");return previous.equals(state);
        }
        byte[] bytes=read(intentPath(e,id));String hash=TownHallRevisions.sha(bytes);
        if(!readIntent(e,bytes,state.getCompound("identity").getString("scope").equals("controlled_fixture")).equals(intent(state))||!hash.equals(liveStamp))throw new IOException("Settled reward lost its original live credit proof");
        CompoundTag captured=state.copy();captured.putString("phase","CAPTURED");
        if(previous.equals(captured)){
            if(diskStamp!=null||!nativeLedger.getString("activeMonumentId").equals(m.id()))throw new IOException("Saved captured round or credit proof conflicts with recovery");return false;
        }
        if(!previous.equals(state)||!hash.equals(diskStamp)||nativeLedger.contains("activeMonumentId")||nativeLedger.getLong("nextMonumentActivationGameTime")!=state.getLong("nextActivation"))throw new IOException("Saved settled round or credit proof conflicts with recovery");return true;
    }
    private static CompoundTag nativeRecord(Environment e,MonumentData m)throws IOException {
        CompoundTag data=nativeDataIfPresent(e);if(data==null)return null;
        ListTag list=data.getList("monuments",Tag.TAG_COMPOUND);CompoundTag found=null;
        for(int i=0;i<list.size();i++)if(list.getCompound(i).getString("id").equals(m.id())){if(found!=null)throw new IOException("Ambiguous native monument identity");found=list.getCompound(i);}
        return found;
    }
    private static CompoundTag nativeDataIfPresent(Environment e)throws IOException {
        Path file=root(e).resolve("data/catancraft.dat");return Files.exists(file,LinkOption.NOFOLLOW_LINKS)?nativeData(e):null;
    }
    private static String nativeStamp(CompoundTag data,UUID id)throws IOException {
        if(data==null)return null;CompoundTag stamps=data.getCompound("placedMapAssets");String key=paidKey(id);
        if(!stamps.contains(key))return null;RewardTable41.type(stamps,key,Tag.TAG_STRING);return stamps.getString(key);
    }
    /** A target write must not regress unrelated native reward records or credit stamps. */
    private static void nativeHistoryGuard(Environment e,MonumentData target)throws IOException {
        CompoundTag disk=nativeDataIfPresent(e);if(disk==null)return;CompoundTag live=e.data().save(new CompoundTag());
        Map<String,Tag> saved=new HashMap<>(),current=new HashMap<>();
        for(CompoundTag ledger:List.of(disk,live)){
            Map<String,Tag> records=ledger==disk?saved:current;ListTag list=ledger.getList("monuments",Tag.TAG_COMPOUND);
            for(int i=0;i<list.size();i++){CompoundTag m=list.getCompound(i);String id=m.getString("id");if(id.equals(target.id()))continue;
                if(m.contains("rewardActivation41")&&records.putIfAbsent(id,m.get("rewardActivation41"))!=null)throw new IOException("Duplicate native reward identity");}
        }
        if(!saved.equals(current))throw new IOException("Unrelated native reward history differs from live state");
        CompoundTag savedStamps=disk.getCompound("placedMapAssets"),liveStamps=live.getCompound("placedMapAssets");Set<String> keys=new HashSet<>(savedStamps.getAllKeys());keys.addAll(liveStamps.getAllKeys());
        UUID targetId=target.rewardActivation41() instanceof CompoundTag n&&n.hasUUID("id")?n.getUUID("id"):null;
        for(String key:keys)if(key.startsWith("monument41_paid:")&&!key.equals(targetId==null?"":paidKey(targetId))&&!Objects.equals(savedStamps.get(key),liveStamps.get(key)))throw new IOException("Unrelated native reward credit proof differs from live state");
    }
    private static void saveVerified(Environment e,MonumentData m,CompoundTag state,boolean fixture)throws IOException {
        if(!state(e,m,fixture).equals(state))throw new IOException("Frozen reward changed before native save");nativeActivation(e,m,state);nativeHistoryGuard(e,m);
        CompoundTag expected=e.data().save(new CompoundTag()).copy();Exception callback=null;
        try{e.data().setDirty();e.persist();}catch(Exception failure){callback=failure;}
        try{if(!nativeData(e).equals(expected)||!e.data().save(new CompoundTag()).equals(expected))throw new IOException("Native monument reward save was not verified");}
        catch(IOException|RuntimeException failure){e.data().setDirty();if(callback!=null)failure.addSuppressed(callback);throw failure;}
        if(callback!=null)CatanCraft.LOGGER.warn("Reward save callback failed after verified native persistence: {}",callback.toString());
    }
    private static CompoundTag nativeData(Environment e)throws IOException {try(InputStream in=new ByteArrayInputStream(read(root(e).resolve("data/catancraft.dat")))){return NbtIo.readCompressed(in).getCompound("data");}}
    private static String hashTag(CompoundTag tag)throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();NbtIo.writeCompressed(tag,out);return TownHallRevisions.sha(out.toByteArray());}
    private static Path root(Environment e)throws IOException {return e.server().getWorldPath(LevelResource.ROOT).toRealPath();}
    static Path intentPath(Environment e,UUID id)throws IOException {Path p=root(e).resolve("data/catancraft_reward41_"+Objects.requireNonNull(id)+".dat");safe(p);return p;}
    static Path receiptPath(Environment e,UUID id)throws IOException {Path p=intentPath(e,id);return p.resolveSibling(p.getFileName()+".receipt");}
    private static List<Path> history(Environment e)throws IOException {
        Path dir=root(e).resolve("data");safe(dir);if(!Files.isDirectory(dir))return List.of();List<Path> paths=new ArrayList<>();
        try(var files=Files.newDirectoryStream(dir,"catancraft_reward41_*.dat")){for(Path path:files){if(paths.size()>=MAX_HISTORY)throw new IOException("Reward history exhausted");paths.add(path);}}
        return List.copyOf(paths);
    }
    private static byte[] read(Path p)throws IOException {safe(p);if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||Files.size(p)>MAX_FILE)throw new IOException("Missing/invalid bounded reward file");byte[] bytes=Files.readAllBytes(p);if(bytes.length>MAX_FILE)throw new IOException("Oversized reward file");return bytes;}
    private static void safe(Path path)throws IOException {Path walk=path.getRoot();for(Path part:path){walk=walk.resolve(part);if(Files.isSymbolicLink(walk)||(!walk.equals(path)&&Files.exists(walk,LinkOption.NOFOLLOW_LINKS)&&!Files.isDirectory(walk,LinkOption.NOFOLLOW_LINKS)))throw new IOException("Unsafe reward storage path");}}
    private static void thread(Environment e)throws IOException {if(e.server()==null||!e.server().isSameThread()||e.data()!=CatanSavedData.get(e.server()))throw new IOException("Reward requires current native data/server thread");}
    private static void fixture(Environment e)throws IOException {
        thread(e);Path root=root(e);if(!(e.server() instanceof GameTestServer)||!root.getFileName().toString().equals("world")
                ||!Set.of("controlled-catancraft_rewards41-run","controlled-catancraft_rewards41_restart-run",
                        "controlled-catancraft_claims43-run","controlled-catancraft_claims43_tacz-run",
                        "controlled-catancraft_claims43_sbw-run","controlled-catancraft_native43_tacz_restart-run",
                        "controlled-catancraft_native43_sbw_restart-run").contains(root.getParent().getFileName().toString())
                ||!Files.readString(root.getParent().resolve(".catancraft-controlled-fixture-v1")).equals("CatanCraft synthetic flat fixture v1\n"))throw new IOException("Controlled reward fixture required");
    }
    private static boolean recordScope(Environment e,MonumentData m,boolean fixture)throws IOException {
        if(!fixture)return false;fixture(e);
        if(m.rewardActivation41() instanceof CompoundTag tag){String scope=tag.getCompound("identity").getString("scope");if(scope.equals("runtime"))return false;if(scope.equals("controlled_fixture"))return true;}
        throw new IOException("Unrecognized reward scope in controlled fixture");
    }
    private static boolean controlledRecord(Environment e,MonumentData m)throws IOException {
        if(!(m.rewardActivation41() instanceof CompoundTag tag)||!tag.getCompound("identity").getString("scope").equals("controlled_fixture"))return false;
        fixture(e);return true;
    }
    private static Lock lock(Environment e)throws IOException {
        thread(e);if(busy)throw new IOException("Another monument reward operation is active");busy=true;FileChannel channel=null;
        try{Path path=root(e).resolve("data/catancraft_reward41.lock");safe(path);Files.createDirectories(path.getParent());channel=FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
            FileLock lock;try{lock=channel.tryLock();}catch(OverlappingFileLockException failure){throw new IOException("Reward storage is locked",failure);}if(lock==null)throw new IOException("Reward storage is locked");return new Lock(path,channel,lock);
        }catch(IOException|RuntimeException failure){try{if(channel!=null)channel.close();}finally{busy=false;}throw failure;}
    }
    private static final class Lock implements AutoCloseable {
        final Path path;final FileChannel channel;final FileLock lock;final Object key;
        Lock(Path path,FileChannel channel,FileLock lock)throws IOException{this.path=path;this.channel=channel;this.lock=lock;key=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).fileKey();if(key==null)throw new IOException("Reward storage needs stable lock identity");}
        void verify()throws IOException{safe(path);if(!lock.isValid()||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||!key.equals(Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).fileKey()))throw new IOException("Reward lock changed");}
        public void close()throws IOException{try{if(lock.isValid())lock.release();}finally{try{channel.close();}finally{busy=false;}}}
    }
}
