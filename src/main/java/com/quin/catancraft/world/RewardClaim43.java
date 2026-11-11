package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.*;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** Equipment entitlement delivery, independent of purchases and national accounting.
 * One global immutable reservation binds each original reward/index to its first actor.
 * The item and delivery receipt are verified in the same native player file. */
final class RewardClaim43 {
    static final String RECEIPTS="CatanCraftReward43";
    private static final int MAX_HISTORY=4096,MAX_FILE=8*1024*1024+40;
    private static final Fault NONE=p->{};
    private static boolean busy;
    enum SavePoint { BEFORE_INTENT,AFTER_INTENT,AFTER_DELIVERY,AFTER_PLAYER_SAVE,BEFORE_COMMITTED,AFTER_COMMITTED }
    interface Fault { void at(SavePoint point)throws IOException; }
    interface Guard { void check(CompoundTag originalFacility)throws IOException; }
    interface Environment { ServerPlayer player();CatanSavedData data();void guard(CompoundTag originalFacility)throws IOException;void persistPlayer()throws IOException; }
    interface NativeAccess { NativeWeapon42.Supply quote(RewardTable41.Entitlement entitlement)throws IOException;void recheck(RewardTable41.Entitlement entitlement,NativeWeapon42.Supply supply)throws IOException; }
    record Selection(UUID rewardId,int index) {
        Selection { if(rewardId==null||rewardId.equals(new UUID(0,0))||index<0||index>=16)throw new IllegalArgumentException("Explicit reward and entitlement index required"); }
    }
    record View(Selection selection,RewardTable41.Entitlement entitlement,String status,UUID reservedActor) {}
    record Request(Selection selection,UUID actor,UUID nation,CompoundTag facility,RewardTable41.Entitlement entitlement,NativeWeapon42.Supply supply) {
        Request { facility=facility.copy();supply=new NativeWeapon42.Supply(supply.item(),supply.fingerprint()); }
        public CompoundTag facility(){return facility.copy();}
    }
    record Result(boolean complete,String message) {}
    private record Source(CompoundTag intent,String hash,RewardTable41.Entitlement entitlement) {}
    private RewardClaim43(){}
    static Environment runtime(ServerPlayer player,Guard guard){return new Environment(){
        public ServerPlayer player(){return player;}public CatanSavedData data(){return CatanSavedData.get(player.server);}
        public void guard(CompoundTag facility)throws IOException{guard.check(facility);}public void persistPlayer(){player.server.getPlayerList().saveAll();}
    };}
    private static NativeAccess nativeAccess(Environment e){return new NativeAccess(){
        public NativeWeapon42.Supply quote(RewardTable41.Entitlement entitlement)throws IOException{return NativeReward43.quote(e.player().server,entitlement);}
        public void recheck(RewardTable41.Entitlement entitlement,NativeWeapon42.Supply supply)throws IOException{NativeReward43.recheck(e.player().server,entitlement,supply);}
    };}
    static UUID claimId(Selection selection){return UUID.nameUUIDFromBytes(("CatanCraftReward43:"+selection.rewardId()+":"+selection.index()).getBytes(StandardCharsets.UTF_8));}
    static List<View> list(Environment e)throws IOException{return list(e,false);}
    static List<Selection> pending(Environment e)throws IOException{return list(e).stream().filter(v->Set.of("RESERVED","HELD").contains(v.status())&&e.player().getUUID().equals(v.reservedActor())).map(View::selection).toList();}
    static Request inspect(Environment e,Selection selection)throws IOException{return inspect(e,selection,false);}
    static Request inspectRequest(Environment e,Selection selection)throws IOException{return inspect(e,selection);}
    static Selection prepare(Environment e,Selection selection,CompoundTag facility,NativeWeapon42.Supply quoted)throws IOException{return prepare(e,selection,facility,quoted,nativeAccess(e),NONE,false);}
    static Result claim(Environment e,Selection selection)throws IOException{return claim(e,selection,nativeAccess(e),NONE,false);}
    static Result recover(Environment e,Selection selection)throws IOException{return claim(e,selection);}
    static Selection fixturePrepare(Environment e,Selection selection,CompoundTag facility,NativeWeapon42.Supply quoted,NativeAccess access,Fault fault)throws IOException{fixture(e);return prepare(e,selection,facility,quoted,access,fault,true);}
    static Result fixtureClaim(Environment e,Selection selection,NativeAccess access,Fault fault)throws IOException{fixture(e);return claim(e,selection,access,fault,true);}
    static List<View> fixtureList(Environment e)throws IOException{fixture(e);return list(e,true);}
    static Request fixtureInspect(Environment e,Selection selection)throws IOException{fixture(e);return inspect(e,selection,true);}
    private static List<View> list(Environment e,boolean fixture)throws IOException {
        try(Lock lock=lock(e)){
            List<View> result=new ArrayList<>();UUID nation=nation(e).id();validateHistory(e,fixture);
            for(CompoundTag reward:rewards(e,fixture,nation)){
                RewardTable41.Bundle bundle=RewardTable41.Bundle.load(reward.getCompound("roll"));
                for(int index=0;index<bundle.entitlements().size();index++){
                    if(result.size()>=MAX_HISTORY)throw new IOException("Too many equipment entitlement views; retain reward history");
                    Selection selection=new Selection(reward.getUUID("id"),index);Path path=intentPath(e,selection);String status="AVAILABLE";UUID actor=null;
                    if(Files.exists(path,LinkOption.NOFOLLOW_LINKS)){
                        byte[] bytes=read(path);CompoundTag original=load(e,selection,bytes,fixture);verifySource(e,selection,original,fixture);actor=original.getCompound("identity").getUUID("actor");
                        String phase=progress(e,selection,TownHallRevisions.sha(bytes));status=phase.equals("COMMITTED")?"COMMITTED":phase.equals("HELD")?"HELD":"RESERVED";
                    }else if(Files.exists(receiptPath(e,selection),LinkOption.NOFOLLOW_LINKS))throw new IOException("Reward claim lost its original reservation");
                    result.add(new View(selection,bundle.entitlements().get(index),status,actor));
                }
            }
            lock.verify();result.sort(Comparator.comparing((View v)->v.selection().rewardId().toString()).thenComparingInt(v->v.selection().index()));return List.copyOf(result);
        }
    }
    private static Request inspect(Environment e,Selection selection,boolean fixture)throws IOException {
        try(Lock lock=lock(e)){CompoundTag original=load(e,selection,read(intentPath(e,selection)),fixture);requireActor(e,original,fixture);verifySource(e,selection,original,fixture);lock.verify();return request(original,selection);}
    }
    private static Request request(CompoundTag original,Selection selection)throws IOException {
        CompoundTag id=original.getCompound("identity");return new Request(selection,id.getUUID("actor"),id.getUUID("nation"),original.getCompound("facility"),entitlement(original.getCompound("entitlement")),supply(original));
    }
    private static Selection prepare(Environment e,Selection selection,CompoundTag facility,NativeWeapon42.Supply quoted,NativeAccess access,Fault fault,boolean fixture)throws IOException {
        try(Lock lock=lock(e)){
            if(facility==null||facility.isEmpty()||quoted==null)throw new IOException("Review a physical reward claim before confirming");
            CompoundTag frozenFacility=facility.copy();e.guard(frozenFacility.copy());CompoundTag identity=identity(e,fixture);Source source=source(e,selection,fixture);
            NativeWeapon42.Supply frozen=new NativeWeapon42.Supply(item(quoted.item().save(new CompoundTag())),quoted.fingerprint());if(frozen.item().getCount()!=source.entitlement().count())throw new IOException("Reviewed equipment amount differs from the original entitlement");access.recheck(source.entitlement(),frozen);
            Path path=intentPath(e,selection);if(Files.exists(path,LinkOption.NOFOLLOW_LINKS)||Files.exists(receiptPath(e,selection),LinkOption.NOFOLLOW_LINKS))throw new IOException("This entitlement already has its original reservation; recover that claim");
            List<CompoundTag> history=validateHistory(e,fixture);if(history.size()>=MAX_HISTORY)throw new IOException("Reward claim history exhausted; preserve records");
            for(CompoundTag prior:history)if(prior.getCompound("identity").getUUID("actor").equals(e.player().getUUID())){
                Selection old=selection(prior);String phase=progress(e,old,TownHallRevisions.sha(read(intentPath(e,old))));if(!phase.equals("COMMITTED"))throw new IOException("Recover the existing equipment claim before reserving another");
            }
            receiptAgreement(e);if(memoryReceipts(e).size()>=MAX_HISTORY)throw new IOException("Reward delivery receipt history exhausted");
            int slot=e.player().getInventory().getFreeSlot();if(slot<0||slot>=36)throw new IOException("Keep one empty inventory slot for equipment delivery");
            savePlayerVerified(e);e.guard(frozenFacility.copy());lock.verify();access.recheck(source.entitlement(),frozen);
            if(!identity(e,fixture).equals(identity)||!e.player().getInventory().getItem(slot).isEmpty())throw new IOException("Equipment claim source changed during preparation");
            CompoundTag n=new CompoundTag();n.putInt("schema",1);n.putUUID("id",claimId(selection));n.putUUID("reward",selection.rewardId());n.putInt("index",selection.index());n.put("identity",identity);
            n.putString("sourceSHA256",source.hash());n.put("entitlement",entitlementTag(source.entitlement()));n.put("facility",frozenFacility);n.put("item",frozen.item().save(new CompoundTag()));n.putString("fingerprint",frozen.fingerprint());n.putInt("slot",slot);
            fault.at(SavePoint.BEFORE_INTENT);e.guard(frozenFacility.copy());lock.verify();access.recheck(source.entitlement(),frozen);verifySource(e,selection,n,fixture);receiptAgreement(e);
            if(!identity(e,fixture).equals(identity)||!e.player().getInventory().getItem(slot).isEmpty())throw new IOException("Equipment claim source changed before reservation");
            ProducerJournal.write(path,n,false);if(!load(e,selection,read(path),fixture).equals(n))throw new IOException("Reward reservation readback failed");fault.at(SavePoint.AFTER_INTENT);return selection;
        }
    }
    private static Result claim(Environment e,Selection selection,NativeAccess access,Fault fault,boolean fixture)throws IOException {
        try(Lock lock=lock(e)){
            byte[] original=read(intentPath(e,selection));String hash=TownHallRevisions.sha(original);CompoundTag n=load(e,selection,original,fixture);requireActor(e,n,fixture);verifySource(e,selection,n,fixture);e.guard(n.getCompound("facility").copy());receiptAgreement(e);
            UUID id=claimId(selection);String phase=progress(e,selection,hash),delivered=receipt(e,id);
            if(!delivered.isEmpty()&&!delivered.equals(hash))throw new IOException("Foreign equipment delivery receipt");
            if(phase.equals("COMMITTED")){if(!delivered.equals(hash))throw new IOException("Completed equipment claim lost its original player receipt");return new Result(true,"Equipment claim already completed; no repeated delivery.");}
            if(phase.equals("HELD"))throw new IOException("Equipment delivery is held for recovery review");
            if(phase.equals("DELIVERING")&&delivered.isEmpty())throw new IOException("Interrupted equipment delivery requires recovery review; no automatic redelivery");
            if(!delivered.isEmpty()&&!phase.equals("DELIVERING"))throw new IOException("Delivered equipment has no original delivery phase");
            int slot=n.getInt("slot");ItemStack stack=item(n.getCompound("item"));check(e,lock,selection,n,original,access,fixture);
            if(delivered.isEmpty()){
                if(!e.player().getInventory().getItem(slot).isEmpty())throw new IOException("Free original equipment delivery slot "+(slot+1)+" before recovery");
                receiptAgreement(e);savePlayerVerified(e);check(e,lock,selection,n,original,access,fixture);receiptAgreement(e);
                if(!e.player().getInventory().getItem(slot).isEmpty()||!receipt(e,id).isEmpty())throw new IOException("Equipment delivery source changed");
                if(memoryReceipts(e).size()>=MAX_HISTORY)throw new IOException("Reward player history exhausted");
                CompoundTag before=nativePlayer(e),delivery=null;boolean durable=false,insertionAttempted=false;advance(e,selection,hash,"DELIVERING");
                try{
                    setReceipt(e,id,hash);insertionAttempted=true;e.player().getInventory().setItem(slot,stack.copy());delivery=nativePlayer(e);
                    fault.at(SavePoint.AFTER_DELIVERY);check(e,lock,selection,n,original,access,fixture);
                    if(!sameDeliveryImage(nativePlayer(e),delivery))throw new IOException("Staged equipment delivery image changed");
                    savePlayerVerified(e);durable=true;fault.at(SavePoint.AFTER_PLAYER_SAVE);
                }catch(IOException|RuntimeException failure){if(!durable)rollback(e,selection,hash,slot,stack,insertionAttempted,before,delivery,failure);else preserveDeliveryProof(e,selection,hash,failure);throw failure;}
            }
            try{
                check(e,lock,selection,n,original,access,fixture);receiptAgreement(e);if(!receipt(e,id).equals(hash))throw new IOException("Equipment receipt changed before completion");
                fault.at(SavePoint.BEFORE_COMMITTED);check(e,lock,selection,n,original,access,fixture);receiptAgreement(e);if(!receipt(e,id).equals(hash))throw new IOException("Equipment receipt changed at completion");
                advance(e,selection,hash,"COMMITTED");fault.at(SavePoint.AFTER_COMMITTED);return new Result(true,"Equipment claimed once; no national resources charged.");
            }catch(IOException|RuntimeException failure){preserveDeliveryProof(e,selection,hash,failure);throw failure;}
        }
    }
    private static List<CompoundTag> rewards(Environment e,boolean fixture,UUID nation)throws IOException {
        return fixture?MonumentRewards41.fixturePendingRewards43(e.player().server,e.data(),nation):MonumentRewards41.pendingRewards(e.player().server,e.data(),nation);
    }
    private static Source source(Environment e,Selection selection,boolean fixture)throws IOException {
        UUID nation=nation(e).id();CompoundTag found=null;
        for(CompoundTag candidate:rewards(e,fixture,nation))if(candidate.hasUUID("id")&&candidate.getUUID("id").equals(selection.rewardId())){if(found!=null)throw new IOException("Ambiguous original monument reward");found=candidate;}
        if(found==null||!found.hasUUID("winner")||!found.getUUID("winner").equals(nation))throw new IOException("This nation has no settled equipment entitlement for that reward");
        List<RewardTable41.Entitlement> entries=RewardTable41.Bundle.load(found.getCompound("roll")).entitlements();if(selection.index()>=entries.size())throw new IOException("Equipment entitlement index is not in the original reward");
        Path path=root(e).resolve("data/catancraft_reward41_"+selection.rewardId()+".dat");byte[] bytes=read(path);if(!ProducerJournal.read(bytes).equals(found))throw new IOException("Original settled reward changed during review");
        return new Source(found.copy(),TownHallRevisions.sha(bytes),entries.get(selection.index()));
    }
    private static void verifySource(Environment e,Selection selection,CompoundTag original,boolean fixture)throws IOException {
        Source source=source(e,selection,fixture);
        if(!source.hash().equals(original.getString("sourceSHA256"))||!entitlementTag(source.entitlement()).equals(original.getCompound("entitlement"))||!source.intent().getUUID("winner").equals(original.getCompound("identity").getUUID("nation")))throw new IOException("Original winner or frozen equipment entitlement changed");
    }
    private static NationData nation(Environment e)throws IOException {
        List<NationData> nations=e.data().nations().stream().filter(n->n.members().contains(e.player().getUUID())).toList();
        if(nations.size()!=1||!nations.get(0).isLeader(e.player().getUUID()))throw new IOException("Only the current winning nation leader may claim equipment");return nations.get(0);
    }
    private static CompoundTag identity(Environment e,boolean fixture)throws IOException {
        CompoundTag n=new CompoundTag();n.putString("root",root(e).toString());n.putLong("seed",e.player().server.overworld().getSeed());n.putString("levelName",e.player().server.getWorldData().getLevelName());n.putString("scope",fixture?"controlled_fixture":"runtime");
        n.putString("dimension",e.player().serverLevel().dimension().location().toString());n.putUUID("actor",e.player().getUUID());n.putUUID("nation",nation(e).id());return n;
    }
    private static void requireActor(Environment e,CompoundTag original,boolean fixture)throws IOException {
        if(!identity(e,fixture).equals(original.getCompound("identity")))throw new IOException("Only the original reserving leader in the original nation and world may recover this equipment claim");
    }
    private static CompoundTag entitlementTag(RewardTable41.Entitlement value){return value.save();}
    private static RewardTable41.Entitlement entitlement(CompoundTag n)throws IOException {
        RewardTable41.keys(n,Set.of("kind","id","count","revision"));for(String key:List.of("kind","id","revision"))RewardTable41.type(n,key,Tag.TAG_STRING);RewardTable41.type(n,"count",Tag.TAG_INT);
        try{return new RewardTable41.Entitlement(RewardTable41.Kind.valueOf(n.getString("kind")),n.getString("id"),n.getInt("count"),n.getString("revision"));}catch(IllegalArgumentException failure){throw new IOException("Malformed historical equipment entitlement",failure);}
    }
    private static ItemStack item(CompoundTag n)throws IOException {ItemStack stack=ItemStack.of(n);if(stack.isEmpty()||stack.getCount()<1||stack.getCount()>stack.getMaxStackSize()||!stack.save(new CompoundTag()).equals(n))throw new IOException("Unknown or changed frozen equipment item");return stack;}
    private static NativeWeapon42.Supply supply(CompoundTag n)throws IOException {return new NativeWeapon42.Supply(item(n.getCompound("item")),n.getString("fingerprint"));}
    private static Selection selection(CompoundTag n)throws IOException {try{return new Selection(n.getUUID("reward"),n.getInt("index"));}catch(IllegalArgumentException failure){throw new IOException("Malformed equipment selection",failure);}}
    private static CompoundTag load(Environment e,Selection selection,byte[] bytes,boolean fixture)throws IOException {
        CompoundTag n=ProducerJournal.read(bytes);RewardTable41.keys(n,Set.of("schema","id","reward","index","identity","sourceSHA256","entitlement","facility","item","fingerprint","slot"));
        for(String key:List.of("schema","index","slot"))RewardTable41.type(n,key,Tag.TAG_INT);for(String key:List.of("identity","entitlement","facility","item"))RewardTable41.type(n,key,Tag.TAG_COMPOUND);for(String key:List.of("sourceSHA256","fingerprint"))RewardTable41.type(n,key,Tag.TAG_STRING);
        if(n.getInt("schema")!=1||!n.hasUUID("id")||!n.getUUID("id").equals(claimId(selection))||!n.hasUUID("reward")||!selection(n).equals(selection)||n.getCompound("facility").isEmpty()||n.getInt("slot")<0||n.getInt("slot")>=36||!n.getString("sourceSHA256").matches("[0-9a-f]{64}")||!n.getString("fingerprint").matches("[0-9a-f]{64}"))throw new IOException("Malformed equipment reservation");
        CompoundTag recorded=n.getCompound("identity");RewardTable41.keys(recorded,Set.of("root","seed","levelName","scope","dimension","actor","nation"));for(String key:List.of("root","levelName","scope","dimension"))RewardTable41.type(recorded,key,Tag.TAG_STRING);RewardTable41.type(recorded,"seed",Tag.TAG_LONG);
        if(!recorded.hasUUID("actor")||!recorded.hasUUID("nation")||recorded.getUUID("actor").equals(new UUID(0,0))||recorded.getUUID("nation").equals(new UUID(0,0))||!recorded.getString("root").equals(root(e).toString())||recorded.getLong("seed")!=e.player().server.overworld().getSeed()||!recorded.getString("levelName").equals(e.player().server.getWorldData().getLevelName())||!recorded.getString("scope").equals(fixture?"controlled_fixture":"runtime")||net.minecraft.resources.ResourceLocation.tryParse(recorded.getString("dimension"))==null)throw new IOException("Foreign equipment reservation world");
        if(item(n.getCompound("item")).getCount()!=entitlement(n.getCompound("entitlement")).count())throw new IOException("Frozen equipment amount differs from its entitlement");return n;
    }
    private static List<CompoundTag> validateHistory(Environment e,boolean fixture)throws IOException {
        List<CompoundTag> result=new ArrayList<>();Path dir=root(e).resolve("data");safe(dir);if(!Files.isDirectory(dir))return result;
        try(var files=Files.newDirectoryStream(dir,"catancraft_reward43_*.dat")){for(Path path:files){if(result.size()>=MAX_HISTORY)throw new IOException("Equipment claim history exhausted");byte[] bytes=read(path);CompoundTag raw=ProducerJournal.read(bytes);
            if(!raw.hasUUID("reward")||!raw.contains("index",Tag.TAG_INT))throw new IOException("Malformed equipment history identity");Selection selection=selection(raw);if(!path.equals(intentPath(e,selection)))throw new IOException("Foreign equipment history filename");CompoundTag n=load(e,selection,bytes,fixture);progress(e,selection,TownHallRevisions.sha(bytes));result.add(n);}}
        return List.copyOf(result);
    }
    private static void check(Environment e,Lock lock,Selection selection,CompoundTag original,byte[] bytes,NativeAccess access,boolean fixture)throws IOException {
        thread(e);lock.verify();requireActor(e,original,fixture);if(!Arrays.equals(bytes,read(intentPath(e,selection))))throw new IOException("Original equipment reservation changed");e.guard(original.getCompound("facility").copy());verifySource(e,selection,original,fixture);access.recheck(entitlement(original.getCompound("entitlement")),supply(original));
    }
    private static String progress(Environment e,Selection selection,String hash)throws IOException {
        Path path=receiptPath(e,selection);if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS))return "";CompoundTag n=ProducerJournal.read(read(path));RewardTable41.keys(n,Set.of("schema","id","intentSHA256","phase"));RewardTable41.type(n,"schema",Tag.TAG_INT);RewardTable41.type(n,"intentSHA256",Tag.TAG_STRING);RewardTable41.type(n,"phase",Tag.TAG_STRING);
        if(n.getInt("schema")!=1||!n.hasUUID("id")||!n.getUUID("id").equals(claimId(selection))||!n.getString("intentSHA256").equals(hash)||!Set.of("READY","DELIVERING","COMMITTED","HELD").contains(n.getString("phase")))throw new IOException("Unrecognized equipment claim progress; retain history");return n.getString("phase");
    }
    private static void advance(Environment e,Selection selection,String hash,String next)throws IOException {
        Path path=receiptPath(e,selection);byte[] prior=Files.exists(path,LinkOption.NOFOLLOW_LINKS)?read(path):null;String old=progress(e,selection,hash);
        boolean allowed=next.equals("DELIVERING")&&(old.isEmpty()||old.equals("READY"))||next.equals("READY")&&old.equals("DELIVERING")||next.equals("COMMITTED")&&old.equals("DELIVERING")||next.equals("HELD")&&!old.equals("COMMITTED");if(!allowed)throw new IOException("Invalid equipment claim progress transition");
        CompoundTag n=new CompoundTag();n.putInt("schema",1);n.putUUID("id",claimId(selection));n.putString("intentSHA256",hash);n.putString("phase",next);
        if(prior==null?Files.exists(path,LinkOption.NOFOLLOW_LINKS):!Arrays.equals(prior,read(path)))throw new IOException("Equipment claim progress changed during save");ProducerJournal.write(path,n,prior!=null);if(!progress(e,selection,hash).equals(next))throw new IOException("Equipment progress readback failed");
    }
    private static CompoundTag compound(CompoundTag n,String key)throws IOException {if(n.contains(key)&&!n.contains(key,Tag.TAG_COMPOUND))throw new IOException("Malformed equipment/player compound "+key);return n.getCompound(key);}
    private static CompoundTag persisted(CompoundTag player)throws IOException {return compound(compound(player,"ForgeData"),Player.PERSISTED_NBT_TAG);}
    private static CompoundTag receipts(CompoundTag player)throws IOException {
        CompoundTag receipts=compound(persisted(player),RECEIPTS);if(receipts.size()>MAX_HISTORY)throw new IOException("Equipment receipt history exhausted");
        for(String key:receipts.getAllKeys()){try{if(!UUID.fromString(key).toString().equals(key))throw new IllegalArgumentException();}catch(IllegalArgumentException failure){throw new IOException("Malformed equipment receipt key",failure);}if(!receipts.contains(key,Tag.TAG_STRING)||!receipts.getString(key).matches("[0-9a-f]{64}"))throw new IOException("Malformed equipment receipt");}return receipts;
    }
    private static CompoundTag nativePlayer(Environment e){return e.player().saveWithoutId(new CompoundTag());}
    private static CompoundTag memoryReceipts(Environment e)throws IOException{return receipts(nativePlayer(e));}
    private static String receipt(Environment e,UUID id)throws IOException{return memoryReceipts(e).getString(id.toString());}
    private static void setReceipt(Environment e,UUID id,String hash)throws IOException {
        CompoundTag all=memoryReceipts(e).copy();if(hash==null)all.remove(id.toString());else {if(!all.contains(id.toString())&&all.size()>=MAX_HISTORY)throw new IOException("Equipment receipt history exhausted");all.putString(id.toString(),hash);}
        CompoundTag forge=e.player().getPersistentData(),persisted=compound(forge,Player.PERSISTED_NBT_TAG).copy();persisted.put(RECEIPTS,all);forge.put(Player.PERSISTED_NBT_TAG,persisted);
    }
    private static void receiptAgreement(Environment e)throws IOException {
        boolean exists=Files.exists(playerFile(e),LinkOption.NOFOLLOW_LINKS);CompoundTag live=nativePlayer(e),disk=exists?nativeFile(playerFile(e)):new CompoundTag();
        if(exists&&(!disk.hasUUID("UUID")||!disk.getUUID("UUID").equals(e.player().getUUID())))throw new IOException("Foreign or malformed native player file");
        if(!receipts(live).equals(receipts(disk))||!compound(persisted(live),ShopPurchase40.RECEIPTS).equals(compound(persisted(disk),ShopPurchase40.RECEIPTS)))throw new IOException("Player equipment or purchase delivery save is ambiguous; reconnect before recovery");
    }
    private static boolean sameDeliveryImage(CompoundTag a,CompoundTag b)throws IOException {return a.getList("Inventory",Tag.TAG_COMPOUND).equals(b.getList("Inventory",Tag.TAG_COMPOUND))&&persisted(a).equals(persisted(b));}
    private static void savePlayerVerified(Environment e)throws IOException {
        CompoundTag expected=nativePlayer(e).copy();receipts(expected);Exception callback=null;try{e.persistPlayer();}catch(Exception failure){callback=failure;}
        try{CompoundTag live=nativePlayer(e),disk=nativeFile(playerFile(e));if(!disk.hasUUID("UUID")||!disk.getUUID("UUID").equals(e.player().getUUID())||!sameDeliveryImage(disk,expected)||!sameDeliveryImage(live,expected))throw new IOException("Native equipment inventory/receipt save was not verified");}
        catch(IOException|RuntimeException failure){if(callback!=null)failure.addSuppressed(callback);throw failure;}if(callback!=null)CatanCraft.LOGGER.warn("Equipment player save callback failed after verified native persistence: {}",callback.toString());
    }
    private static void rollback(Environment e,Selection selection,String hash,int slot,ItemStack staged,boolean inserted,CompoundTag before,CompoundTag delivery,Exception failure) {
        UUID id=claimId(selection);try{
            CompoundTag disk=nativeFile(playerFile(e));if(hash.equals(receipts(disk).getString(id.toString()))){if(delivery==null||!sameDeliveryImage(disk,delivery))throw new IOException("Saved equipment delivery image is ambiguous");preserveDeliveryProof(e,selection,hash,failure);return;}
            if(!sameDeliveryImage(disk,before))throw new IOException("Native pre-delivery image changed; retain equipment hold");ItemStack current=e.player().getInventory().getItem(slot);
            if(inserted&&!ItemStack.matches(current,staged)||!inserted&&!current.isEmpty())throw new IOException("Staged equipment moved or changed");String receipt=receipt(e,id);if(!receipt.isEmpty()&&!receipt.equals(hash))throw new IOException("Staged equipment receipt changed");
            if(inserted)e.player().getInventory().setItem(slot,ItemStack.EMPTY);if(receipt.equals(hash))restoreReceiptPreimage(e,before);
            if(!sameDeliveryImage(nativePlayer(e),before))throw new IOException("Player changed during equipment rollback");advance(e,selection,hash,"READY");return;
        }catch(Exception invalid){failure.addSuppressed(invalid);}try{hold(e,selection,hash);}catch(Exception invalid){failure.addSuppressed(invalid);}disconnect(e);
    }
    private static void restoreReceiptPreimage(Environment e,CompoundTag before)throws IOException {
        CompoundTag forge=e.player().getPersistentData(),current=compound(forge,Player.PERSISTED_NBT_TAG).copy(),original=persisted(before);
        if(original.contains(RECEIPTS))current.put(RECEIPTS,original.get(RECEIPTS).copy());else current.remove(RECEIPTS);
        if(!compound(before,"ForgeData").contains(Player.PERSISTED_NBT_TAG)&&current.isEmpty())forge.remove(Player.PERSISTED_NBT_TAG);else forge.put(Player.PERSISTED_NBT_TAG,current);
    }
    private static void preserveDeliveryProof(Environment e,Selection selection,String hash,Exception failure) {
        try{receiptAgreement(e);if(hash.equals(receipt(e,claimId(selection)))&&hash.equals(receipts(nativeFile(playerFile(e))).getString(claimId(selection).toString())))return;}catch(Exception invalid){failure.addSuppressed(invalid);}try{hold(e,selection,hash);}catch(Exception invalid){failure.addSuppressed(invalid);}disconnect(e);
    }
    private static void hold(Environment e,Selection selection,String hash)throws IOException {if(!progress(e,selection,hash).equals("COMMITTED"))advance(e,selection,hash,"HELD");}
    private static void disconnect(Environment e){if(e.player().connection!=null)e.player().connection.disconnect(net.minecraft.network.chat.Component.literal("Equipment delivery requires recovery review. Your original claim has been retained."));}
    private static void thread(Environment e)throws IOException {if(e.player()==null||!e.player().server.isSameThread()||e.data()!=CatanSavedData.get(e.player().server))throw new IOException("Equipment claim requires the current native server data/thread");}
    private static Path root(Environment e)throws IOException{return e.player().server.getWorldPath(LevelResource.ROOT).toRealPath();}
    private static void safe(Path path)throws IOException {Path walk=path.getRoot();for(Path part:path){walk=walk.resolve(part);if(Files.isSymbolicLink(walk)||!walk.equals(path)&&Files.exists(walk,LinkOption.NOFOLLOW_LINKS)&&!Files.isDirectory(walk,LinkOption.NOFOLLOW_LINKS))throw new IOException("Unsafe equipment claim storage path");}}
    static Path intentPath(Environment e,Selection selection)throws IOException {Path path=root(e).resolve("data/catancraft_reward43_"+selection.rewardId()+"_"+selection.index()+".dat");safe(path);return path;}
    static Path receiptPath(Environment e,Selection selection)throws IOException {Path path=intentPath(e,selection);return path.resolveSibling(path.getFileName()+".receipt");}
    private static Path playerFile(Environment e)throws IOException {Path path=root(e).resolve("playerdata/"+e.player().getUUID()+".dat");safe(path);return path;}
    private static byte[] read(Path path)throws IOException {safe(path);if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>MAX_FILE)throw new IOException("Missing/invalid bounded equipment claim file");byte[] bytes=Files.readAllBytes(path);if(bytes.length>MAX_FILE)throw new IOException("Oversized equipment claim file");return bytes;}
    private static CompoundTag nativeFile(Path path)throws IOException {try(InputStream input=new ByteArrayInputStream(read(path))){return NbtIo.readCompressed(input);}}
    private static void fixture(Environment e)throws IOException {
        thread(e);Path root=root(e);if(!(e.player().server instanceof GameTestServer)||!root.getFileName().toString().equals("world")||!Set.of("controlled-catancraft_claims43-run","controlled-catancraft_claims43_tacz-run","controlled-catancraft_claims43_sbw-run","controlled-catancraft_native43_tacz_restart-run","controlled-catancraft_native43_sbw_restart-run").contains(root.getParent().getFileName().toString())||!Files.readString(root.getParent().resolve(".catancraft-controlled-fixture-v1")).equals("CatanCraft synthetic flat fixture v1\n"))throw new IOException("Controlled equipment claim fixture required");
    }
    private static Lock lock(Environment e)throws IOException {
        thread(e);if(busy)throw new IOException("Another equipment claim operation is active");busy=true;FileChannel channel=null;
        try{Path path=root(e).resolve("data/catancraft_reward43.lock");safe(path);Files.createDirectories(path.getParent());channel=FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);FileLock fileLock;try{fileLock=channel.tryLock();}catch(OverlappingFileLockException failure){throw new IOException("Equipment claim storage is locked",failure);}if(fileLock==null)throw new IOException("Equipment claim storage is locked");return new Lock(path,channel,fileLock);}
        catch(IOException|RuntimeException failure){try{if(channel!=null)channel.close();}finally{busy=false;}throw failure;}
    }
    private static final class Lock implements AutoCloseable {
        final Path path;final FileChannel channel;final FileLock lock;final Object key;
        Lock(Path path,FileChannel channel,FileLock lock)throws IOException{this.path=path;this.channel=channel;this.lock=lock;key=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).fileKey();if(key==null)throw new IOException("Equipment claim storage requires stable lock identity");}
        void verify()throws IOException{safe(path);if(!lock.isValid()||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||!key.equals(Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).fileKey()))throw new IOException("Equipment claim storage lock changed");}
        public void close()throws IOException{try{if(lock.isValid())lock.release();}finally{try{channel.close();}finally{busy=false;}}}
    }
}
