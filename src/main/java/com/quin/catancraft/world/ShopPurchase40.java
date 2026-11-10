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
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** Nation-funded item delivery with immutable intent, a ledger debit receipt,
 * and a player receipt saved in the SAME native player file as inventory.
 * No vehicle delivery, refunds, archive pruning or arbitrary power-loss claim.
 * Native saves may swallow errors: every boundary is independently read back. */
final class ShopPurchase40 {
    static final String RECEIPTS = "CatanCraftShop40";
    private static final int MAX_HISTORY=4096, MAX_FILE=8*1024*1024+40;
    private static final Fault NONE=p->{};
    private static boolean busy;
    enum SavePoint { BEFORE_INTENT, AFTER_INTENT, AFTER_DEBIT, AFTER_LEDGER_SAVE, AFTER_DELIVERY, AFTER_PLAYER_SAVE, BEFORE_COMMITTED, AFTER_COMMITTED }
    interface Fault { void at(SavePoint point)throws IOException; }
    interface Guard { void check(CompoundTag originalFacility)throws IOException; }
    interface Environment {
        ServerPlayer player(); CatanSavedData data();
        void guard(CompoundTag originalFacility)throws IOException;
        void persistLedger()throws IOException;
        void persistPlayer()throws IOException;
    }
    record Price(long money,Map<ResourceType,Long> resources) {
        Price {
            if(money<0||resources==null||resources.size()>ResourceType.values().length)throw new IllegalArgumentException("Invalid shop price");
            for(var e:resources.entrySet())if(e.getKey()==null||e.getValue()==null||e.getValue()<=0)throw new IllegalArgumentException("Invalid shop resource price");
            resources=Map.copyOf(resources);
            if(money==0&&resources.isEmpty())throw new IllegalArgumentException("Missing shop price is not free");
        }
        String describe(){return new com.quin.catancraft.economy.EconomyCost(money,resources).describe();}
    }
    record Request(UUID id,String offer,String catalog,CompoundTag facility,Price price,ItemStack item) {
        Request { Objects.requireNonNull(id); label(offer);label(catalog);Objects.requireNonNull(price);facility=facility.copy();item=item.copy(); }
        public CompoundTag facility(){return facility.copy();}
        public ItemStack item(){return item.copy();}
    }
    record Result(boolean complete,String message){}
    private ShopPurchase40(){}
    static Environment runtime(ServerPlayer player,Guard guard) {
        return new Environment(){
            public ServerPlayer player(){return player;}
            public CatanSavedData data(){return CatanSavedData.get(player.server);}
            public void guard(CompoundTag facility)throws IOException{guard.check(facility);}
            public void persistLedger(){data().setDirty();player.server.overworld().getDataStorage().save();}
            public void persistPlayer(){player.server.getPlayerList().saveAll();}
        };
    }
    private static void label(String s){if(s==null||s.isBlank()||s.length()>160)throw new IllegalArgumentException("Explicit bounded shop identity required");}
    private static void thread(Environment e)throws IOException {
        if(e.player()==null||!e.player().server.isSameThread()||e.data()!=CatanSavedData.get(e.player().server))throw new IOException("Shop requires current native server data/thread");
    }
    private static Path root(Environment e)throws IOException{return e.player().server.getWorldPath(LevelResource.ROOT).toRealPath();}
    private static void safe(Path p)throws IOException {
        Path walk=p.getRoot();for(Path part:p){walk=walk.resolve(part);if(Files.isSymbolicLink(walk))throw new IOException("Shop storage contains a symlink");
            if(!walk.equals(p)&&Files.exists(walk,LinkOption.NOFOLLOW_LINKS)&&!Files.isDirectory(walk,LinkOption.NOFOLLOW_LINKS))throw new IOException("Shop path ancestor is not a directory");}
    }
    static Path intentPath(Environment e,UUID id)throws IOException {
        Path p=root(e).resolve("data/catancraft_shop40_"+e.player().getUUID()+"_"+Objects.requireNonNull(id)+".dat");safe(p);return p;
    }
    static Path receiptPath(Environment e,UUID id)throws IOException{return intentPath(e,id).resolveSibling(intentPath(e,id).getFileName()+".receipt");}
    private static byte[] read(Path p)throws IOException {
        safe(p);if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||Files.size(p)>MAX_FILE)throw new IOException("Missing/invalid bounded shop file");
        byte[] b=Files.readAllBytes(p);if(b.length>MAX_FILE)throw new IOException("Oversized shop file");return b;
    }
    private static CompoundTag nativeFile(Path p)throws IOException {
        byte[] b=read(p);try(InputStream in=new ByteArrayInputStream(b)){return NbtIo.readCompressed(in);}
    }
    private static Path playerFile(Environment e)throws IOException {Path p=root(e).resolve("playerdata/"+e.player().getUUID()+".dat");safe(p);return p;}
    private static CompoundTag nativePlayer(Environment e){return e.player().saveWithoutId(new CompoundTag());}
    private static CompoundTag receipts(CompoundTag nativePlayer)throws IOException {
        CompoundTag forge=compound(nativePlayer,"ForgeData"),persisted=compound(forge,Player.PERSISTED_NBT_TAG);
        CompoundTag receipts=compound(persisted,RECEIPTS);
        if(receipts.size()>MAX_HISTORY)throw new IOException("Shop player history exhausted");
        for(String key:receipts.getAllKeys()){
            try{if(!UUID.fromString(key).toString().equals(key))throw new IllegalArgumentException();}catch(IllegalArgumentException ex){throw new IOException("Malformed delivery receipt key",ex);}
            if(!receipts.contains(key,Tag.TAG_STRING)||!receipts.getString(key).matches("[0-9a-f]{64}"))throw new IOException("Malformed delivery receipt");
        }
        return receipts;
    }
    private static CompoundTag compound(CompoundTag n,String key)throws IOException {
        if(n.contains(key)&&!n.contains(key,Tag.TAG_COMPOUND))throw new IOException("Malformed shop/player compound "+key);
        return n.getCompound(key);
    }
    private static CompoundTag memoryReceipts(Environment e)throws IOException{return receipts(nativePlayer(e));}
    private static void setReceipt(Environment e,UUID id,String hash)throws IOException {
        CompoundTag all=memoryReceipts(e).copy();
        if(hash==null)all.remove(id.toString());else{if(!all.contains(id.toString())&&all.size()>=MAX_HISTORY)throw new IOException("Shop player history exhausted");all.putString(id.toString(),hash);}
        CompoundTag forge=e.player().getPersistentData();CompoundTag persisted=compound(forge,Player.PERSISTED_NBT_TAG).copy();
        persisted.put(RECEIPTS,all);forge.put(Player.PERSISTED_NBT_TAG,persisted);
    }
    /** Never overwrite a durable post-delivery receipt with stale in-memory state. */
    private static void receiptAgreement(Environment e)throws IOException {
        CompoundTag current=memoryReceipts(e);Path file=playerFile(e);
        CompoundTag saved=Files.exists(file,LinkOption.NOFOLLOW_LINKS)?receipts(nativeFile(file)):new CompoundTag();
        if(!saved.equals(current))throw new IOException("Player delivery save is ambiguous; reconnect before recovering this purchase");
    }
    private static void savePlayerVerified(Environment e)throws IOException {
        CompoundTag expected=nativePlayer(e);ListTag expectedInventory=expected.getList("Inventory",Tag.TAG_COMPOUND).copy();CompoundTag expectedReceipts=receipts(expected).copy();
        Exception failure=null;try{e.persistPlayer();}catch(Exception ex){failure=ex;}
        try{
            CompoundTag live=nativePlayer(e),disk=nativeFile(playerFile(e));
            if(!disk.hasUUID("UUID")||!disk.getUUID("UUID").equals(e.player().getUUID())
                    ||!disk.getList("Inventory",Tag.TAG_COMPOUND).equals(expectedInventory)||!live.getList("Inventory",Tag.TAG_COMPOUND).equals(expectedInventory)
                    ||!receipts(disk).equals(expectedReceipts)||!receipts(live).equals(expectedReceipts))throw new IOException("Native player inventory/receipt save was not verified");
        }catch(IOException|RuntimeException invalid){if(failure!=null)invalid.addSuppressed(failure);throw invalid;}
        if(failure!=null)CatanCraft.LOGGER.warn("Shop player save callback failed after verified native persistence: {}",failure.toString());
    }
    private static void saveLedgerVerified(Environment e)throws IOException {
        CompoundTag expected=e.data().save(new CompoundTag()).copy();
        Exception failure=null;try{e.data().setDirty();e.persistLedger();}catch(Exception ex){failure=ex;}
        try{
            CompoundTag disk=nativeFile(root(e).resolve("data/catancraft.dat"));
            if(!disk.getCompound("data").equals(expected)||!e.data().save(new CompoundTag()).equals(expected))throw new IOException("Nation debit/receipt save was not verified");
        }catch(IOException|RuntimeException invalid){e.data().setDirty();if(failure!=null)invalid.addSuppressed(failure);throw invalid;}
        if(failure!=null)CatanCraft.LOGGER.warn("Shop ledger save callback failed after verified native persistence: {}",failure.toString());
    }
    private static NationData nation(Environment e)throws IOException {
        List<NationData> matches=e.data().nations().stream().filter(n->n.members().contains(e.player().getUUID())).toList();
        if(matches.size()!=1||!matches.get(0).isLeader(e.player().getUUID()))throw new IOException("Only the current nation leader may spend shop funds");return matches.get(0);
    }
    private static CompoundTag identity(Environment e)throws IOException {
        CompoundTag n=new CompoundTag();n.putString("root",root(e).toString());n.putLong("seed",e.player().serverLevel().getSeed());
        n.putString("dimension",e.player().serverLevel().dimension().location().toString());n.putUUID("actor",e.player().getUUID());n.putUUID("nation",nation(e).id());return n;
    }
    private static CompoundTag priceTag(Price p){CompoundTag n=new CompoundTag();n.putLong("money",p.money());CompoundTag resources=new CompoundTag();p.resources().forEach((k,v)->resources.putLong(k.name(),v));n.put("resources",resources);return n;}
    private static Price price(CompoundTag n)throws IOException {
        if(!n.getAllKeys().equals(Set.of("money","resources"))||!n.contains("money",Tag.TAG_LONG)||!n.contains("resources",Tag.TAG_COMPOUND))throw new IOException("Malformed historical shop price");
        Map<ResourceType,Long> r=new EnumMap<>(ResourceType.class);CompoundTag values=n.getCompound("resources");
        try{for(String k:values.getAllKeys()){if(!values.contains(k,Tag.TAG_LONG))throw new IOException("Malformed resource amount");r.put(ResourceType.valueOf(k),values.getLong(k));}return new Price(n.getLong("money"),r);}
        catch(IllegalArgumentException ex){throw new IOException("Invalid historical shop price",ex);}
    }
    private static ItemStack item(CompoundTag n)throws IOException {
        ItemStack s=ItemStack.of(n);
        if(s.isEmpty()||s.getCount()<1||s.getCount()>s.getMaxStackSize()||!s.save(new CompoundTag()).equals(n))throw new IOException("Unknown or changed historical shop item");return s;
    }
    private static CompoundTag load(Environment e,UUID id,byte[] bytes)throws IOException {
        CompoundTag n=ProducerJournal.read(bytes);
        if(!n.getAllKeys().equals(Set.of("schema","id","identity","offer","catalog","facility","price","item","slot"))
                ||!n.contains("schema",Tag.TAG_INT)||n.getInt("schema")!=1||!n.hasUUID("id")||!n.getUUID("id").equals(id)
                ||!historicalIdentity(e,n.getCompound("identity"))||!n.contains("offer",Tag.TAG_STRING)||!n.contains("catalog",Tag.TAG_STRING)
                ||!n.contains("facility",Tag.TAG_COMPOUND)||n.getCompound("facility").isEmpty()||!n.contains("price",Tag.TAG_COMPOUND)
                ||!n.contains("item",Tag.TAG_COMPOUND)||!n.contains("slot",Tag.TAG_INT)||n.getInt("slot")<0||n.getInt("slot")>=36)
            throw new IOException("Foreign or malformed shop purchase intent");
        try{label(n.getString("offer"));label(n.getString("catalog"));}catch(IllegalArgumentException ex){throw new IOException("Invalid offer identity",ex);}
        price(n.getCompound("price"));item(n.getCompound("item"));return n;
    }
    private static boolean historicalIdentity(Environment e,CompoundTag recorded)throws IOException {
        if(!recorded.hasUUID("nation")||recorded.getUUID("nation").equals(new UUID(0,0)))return false;
        CompoundTag current=identity(e);current.putUUID("nation",recorded.getUUID("nation"));return current.equals(recorded);
    }
    private static void roomForHistory(Environment e)throws IOException {
        int count=0;try(var files=Files.newDirectoryStream(root(e).resolve("data"),"catancraft_shop40_"+e.player().getUUID()+"_*.dat")){
            for(Path ignored:files)if(++count>=MAX_HISTORY)throw new IOException("Shop history exhausted; preserve records for archival review");
        }
    }
    private static String receipt(Environment e,UUID id)throws IOException{return memoryReceipts(e).getString(id.toString());}
    private static String paidKey(UUID id){return "shop40_paid:"+id;}
    private static boolean paid(Environment e,UUID id,String hash)throws IOException {
        String value=e.data().placedMapAsset(paidKey(id));if(value==null)return false;if(!hash.equals(value))throw new IOException("Foreign shop debit receipt");return true;
    }
    private static String progress(Environment e,UUID id,String hash)throws IOException {
        Path p=receiptPath(e,id);safe(p);if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))return "";
        CompoundTag n=ProducerJournal.read(read(p));
        if(!n.getAllKeys().equals(Set.of("schema","id","intentSHA256","phase"))||!n.contains("schema",Tag.TAG_INT)||n.getInt("schema")!=1
                ||!n.hasUUID("id")||!n.getUUID("id").equals(id)||!n.contains("intentSHA256",Tag.TAG_STRING)||!n.getString("intentSHA256").equals(hash)
                ||!n.contains("phase",Tag.TAG_STRING)||!Set.of("READY","DELIVERING","COMMITTED","HELD").contains(n.getString("phase")))
            throw new IOException("Unrecognized shop progress; preserve history");
        return n.getString("phase");
    }
    private static boolean committed(Environment e,UUID id,String hash)throws IOException {
        String phase=progress(e,id,hash);if(phase.equals("HELD"))throw new IOException("Shop delivery is held for recovery review");return phase.equals("COMMITTED");
    }
    private static void advance(Environment e,UUID id,String hash,String next)throws IOException {
        Path p=receiptPath(e,id);byte[] prior=Files.exists(p,LinkOption.NOFOLLOW_LINKS)?read(p):null;String old=progress(e,id,hash);
        boolean allowed=next.equals("DELIVERING")&&(old.isEmpty()||old.equals("READY"))
                ||next.equals("READY")&&old.equals("DELIVERING")||next.equals("COMMITTED")&&old.equals("DELIVERING")
                ||next.equals("HELD")&&!old.equals("COMMITTED");
        if(!allowed)throw new IOException("Invalid shop progress transition");
        CompoundTag n=new CompoundTag();n.putInt("schema",1);n.putUUID("id",id);n.putString("intentSHA256",hash);n.putString("phase",next);
        if(prior==null?Files.exists(p,LinkOption.NOFOLLOW_LINKS):!Arrays.equals(prior,read(p)))throw new IOException("Shop progress changed during save");
        ProducerJournal.write(p,n,prior!=null);if(!progress(e,id,hash).equals(next))throw new IOException("Shop progress readback failed");
    }
    private static List<UUID> pendingInternal(Environment e)throws IOException {
        List<UUID> result=new ArrayList<>();Path dir=root(e).resolve("data");safe(dir);if(!Files.isDirectory(dir))return result;
        String prefix="catancraft_shop40_"+e.player().getUUID()+"_";int count=0;
        try(var files=Files.newDirectoryStream(dir,prefix+"*.dat")){
            for(Path p:files){if(++count>MAX_HISTORY)throw new IOException("Shop history exhausted; retain records for archival review");UUID id;
                try{id=UUID.fromString(p.getFileName().toString().substring(prefix.length(),p.getFileName().toString().length()-4));}catch(IllegalArgumentException ex){throw new IOException("Malformed shop history name",ex);}
                byte[] b=read(p);load(e,id,b);if(!committed(e,id,TownHallRevisions.sha(b)))result.add(id);
            }
        }
        if(result.size()>1)throw new IOException("Multiple pending shop purchases; preserve records");return List.copyOf(result);
    }
    static List<UUID> pending(Environment e)throws IOException {try(Lock ignored=lock(e)){return pendingInternal(e);}}
    static UUID create(Environment e,Request r)throws IOException{return create(e,r,NONE);}
    static Result resume(Environment e,UUID id)throws IOException{return resume(e,id,NONE);}
    static UUID fixtureCreate(Environment e,Request r,Fault f)throws IOException{fixture(e);return create(e,r,f);}
    static Result fixtureResume(Environment e,UUID id,Fault f)throws IOException{fixture(e);return resume(e,id,f);}
    private static void fixture(Environment e)throws IOException {
        thread(e);Path p=root(e);
        if(!(e.player().server instanceof GameTestServer)||!p.getFileName().toString().equals("world")
                ||!Set.of("controlled-catancraft_shop40-run","controlled-catancraft_shop40_restart-run").contains(p.getParent().getFileName().toString())
                ||!Files.readString(p.getParent().resolve(".catancraft-controlled-fixture-v1")).equals("CatanCraft synthetic flat fixture v1\n"))throw new IOException("Controlled shop fixture required");
    }
    private static UUID create(Environment e,Request r,Fault fault)throws IOException {
        try(Lock l=lock(e)){
            if(r.id().equals(new UUID(0,0))||r.facility().isEmpty())throw new IOException("Explicit shop request/facility required");
            e.guard(r.facility());CompoundTag id=identity(e);ItemStack stack=item(r.item().save(new CompoundTag()));
            Path path=intentPath(e,r.id());if(Files.exists(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Shop intent already exists; resume its original request");
            if(!pendingInternal(e).isEmpty())throw new IOException("Recover the existing purchase before buying again");
            roomForHistory(e);
            receiptAgreement(e);if(memoryReceipts(e).size()>=MAX_HISTORY)throw new IOException("Shop receipt history exhausted");
            int slot=e.player().getInventory().getFreeSlot();if(slot<0||slot>=36)throw new IOException("Keep one empty inventory slot for shop delivery");
            if(!afford(nation(e),r.price()))throw new IOException("Cannot afford this shop offer");
            savePlayerVerified(e);e.guard(r.facility());l.verify();
            if(!identity(e).equals(id)||!e.player().getInventory().getItem(slot).isEmpty())throw new IOException("Shop source changed during preparation");
            CompoundTag n=new CompoundTag();n.putInt("schema",1);n.putUUID("id",r.id());n.put("identity",id);n.putString("offer",r.offer());n.putString("catalog",r.catalog());
            n.put("facility",r.facility());n.put("price",priceTag(r.price()));n.put("item",stack.save(new CompoundTag()));n.putInt("slot",slot);
            fault.at(SavePoint.BEFORE_INTENT);e.guard(r.facility());l.verify();
            if(!identity(e).equals(id)||!e.player().getInventory().getItem(slot).isEmpty())throw new IOException("Shop source changed before intent");
            ProducerJournal.write(path,n,false);load(e,r.id(),read(path));fault.at(SavePoint.AFTER_INTENT);return r.id();
        }
    }
    private static boolean afford(NationData n,Price p){return n.treasury()>=p.money()&&p.resources().entrySet().stream().allMatch(x->n.resource(x.getKey())>=x.getValue());}
    private static Result resume(Environment e,UUID id,Fault fault)throws IOException {
        try(Lock l=lock(e)){
            Path path=intentPath(e,id);byte[] original=read(path);String hash=TownHallRevisions.sha(original);CompoundTag n=load(e,id,original);
            if(!identity(e).equals(n.getCompound("identity")))throw new IOException("Original purchase nation/world changed");
            CompoundTag facility=n.getCompound("facility");e.guard(facility.copy());receiptAgreement(e);
            if(committed(e,id,hash)){
                if(!paid(e,id,hash)||!hash.equals(receipt(e,id)))throw new IOException("Completed purchase lost its debit/delivery receipt");
                return new Result(true,"Purchase already completed; no repeated charge or delivery.");
            }
            if(!pendingInternal(e).equals(List.of(id)))throw new IOException("Different pending purchase");
            Price cost=price(n.getCompound("price"));ItemStack stack=item(n.getCompound("item"));int slot=n.getInt("slot");
            String delivered=receipt(e,id);if(!delivered.isEmpty()&&!delivered.equals(hash))throw new IOException("Foreign delivery receipt");
            String phase=progress(e,id,hash);
            if(phase.equals("DELIVERING")&&delivered.isEmpty())throw new IOException("Interrupted delivery needs recovery review; no automatic redelivery");
            if(!delivered.isEmpty()&&(!paid(e,id,hash)||!phase.equals("DELIVERING")))throw new IOException("Delivered purchase has no original debit/delivery intent");
            if(delivered.isEmpty()&&!e.player().getInventory().getItem(slot).isEmpty())throw new IOException("Original delivery slot is occupied; free slot "+(slot+1)+" and recover this purchase");
            check(e,l,n,original,id);
            if(!paid(e,id,hash)){
                NationData nation=nation(e);if(!afford(nation,cost))throw new IOException("Original shop price is still outstanding: "+cost.describe());
                nation.addTreasury(-cost.money());cost.resources().forEach((k,v)->nation.addResource(k,-v));e.data().setPlacedMapAsset(paidKey(id),hash);e.data().setDirty();
                fault.at(SavePoint.AFTER_DEBIT);
            }
            saveLedgerVerified(e);check(e,l,n,original,id);fault.at(SavePoint.AFTER_LEDGER_SAVE);
            if(delivered.isEmpty()){
                receiptAgreement(e);savePlayerVerified(e);check(e,l,n,original,id);
                if(!e.player().getInventory().getItem(slot).isEmpty()||!receipt(e,id).isEmpty())throw new IOException("Delivery source changed");
                if(memoryReceipts(e).size()>=MAX_HISTORY)throw new IOException("Shop player history exhausted");
                if(!paid(e,id,hash))throw new IOException("Original debit receipt changed before delivery");
                CompoundTag before=nativePlayer(e);
                ListTag beforeInventory=before.getList("Inventory",Tag.TAG_COMPOUND).copy();
                CompoundTag beforeReceipts=receipts(before).copy();
                advance(e,id,hash,"DELIVERING");boolean durable=false, insertionAttempted=false;
                CompoundTag delivery=null;
                try{
                    setReceipt(e,id,hash);insertionAttempted=true;e.player().getInventory().setItem(slot,stack.copy());
                    delivery=nativePlayer(e);
                    fault.at(SavePoint.AFTER_DELIVERY);check(e,l,n,original,id);
                    if(!sameDeliveryImage(nativePlayer(e),delivery))throw new IOException("Staged delivery image changed");
                    savePlayerVerified(e);durable=true;fault.at(SavePoint.AFTER_PLAYER_SAVE);
                }catch(IOException|RuntimeException failure){
                    if(!durable)rollback(e,id,hash,slot,stack,insertionAttempted,beforeInventory,beforeReceipts,delivery,failure);
                    else preserveDeliveryProof(e,id,hash,failure);
                    throw failure;
                }
            }
            try {
            check(e,l,n,original,id);receiptAgreement(e);
            if(!hash.equals(receipt(e,id))||!paid(e,id,hash))throw new IOException("Purchase receipt changed before completion");
            fault.at(SavePoint.BEFORE_COMMITTED);check(e,l,n,original,id);receiptAgreement(e);
            if(!hash.equals(receipt(e,id))||!paid(e,id,hash))throw new IOException("Purchase receipt changed at completion");
            advance(e,id,hash,"COMMITTED");
            fault.at(SavePoint.AFTER_COMMITTED);return new Result(true,"Purchase completed; item delivered and original price charged once.");
            }catch(IOException|RuntimeException failure){preserveDeliveryProof(e,id,hash,failure);throw failure;}
        }
    }
    private static boolean sameDeliveryImage(CompoundTag a,CompoundTag b)throws IOException {
        return a.getList("Inventory",Tag.TAG_COMPOUND).equals(b.getList("Inventory",Tag.TAG_COMPOUND))&&receipts(a).equals(receipts(b));
    }
    private static void rollback(Environment e,UUID id,String hash,int slot,ItemStack staged,
                                 boolean insertionAttempted,ListTag beforeInventory,CompoundTag beforeReceipts,CompoundTag expectedDelivery,Exception failure) {
        try{
            CompoundTag disk=nativeFile(playerFile(e));
            // A native delivery may have succeeded even when its callback or readback failed.
            // Never erase that durable proof by rolling the live player back.
            if(hash.equals(receipts(disk).getString(id.toString()))){
                if(expectedDelivery==null||!sameDeliveryImage(disk,expectedDelivery))throw new IOException("Saved delivery image is ambiguous");
                preserveDeliveryProof(e,id,hash,failure);return;
            }
            if(!receipts(disk).equals(beforeReceipts)||!disk.getList("Inventory",Tag.TAG_COMPOUND).equals(beforeInventory))
                throw new IOException("Native pre-delivery state changed; retain delivery hold");
            ItemStack current=e.player().getInventory().getItem(slot);
            if(insertionAttempted&&!ItemStack.matches(current,staged))
                throw new IOException("Staged delivery moved or changed; retain delivery hold");
            if(!insertionAttempted&&!current.isEmpty())throw new IOException("Original delivery slot changed");
            String currentReceipt=receipt(e,id);
            if(!currentReceipt.isEmpty()&&!hash.equals(currentReceipt))throw new IOException("Staged receipt changed");
            if(insertionAttempted)e.player().getInventory().setItem(slot,ItemStack.EMPTY);
            if(hash.equals(currentReceipt))setReceipt(e,id,null);
            // Retry only when both frozen preimages still match, not merely live == disk.
            if(!memoryReceipts(e).equals(beforeReceipts)||!nativePlayer(e).getList("Inventory",Tag.TAG_COMPOUND).equals(beforeInventory))
                throw new IOException("Player changed during delivery rollback");
            advance(e,id,hash,"READY");return;
        }catch(Exception invalid){failure.addSuppressed(invalid);}
        try{hold(e,id,hash);}catch(Exception invalid){failure.addSuppressed(invalid);}
        if(e.player().connection!=null)e.player().connection.disconnect(net.minecraft.network.chat.Component.literal("Shop delivery requires recovery review. Your purchase record has been retained."));
    }
    private static void preserveDeliveryProof(Environment e,UUID id,String hash,Exception failure) {
        try{if(hash.equals(receipt(e,id)))return;}catch(Exception invalid){failure.addSuppressed(invalid);}
        try{hold(e,id,hash);}catch(Exception invalid){failure.addSuppressed(invalid);}
        if(e.player().connection!=null)e.player().connection.disconnect(net.minecraft.network.chat.Component.literal("Shop delivery requires recovery review. Your purchase record has been retained."));
    }
    private static void hold(Environment e,UUID id,String hash)throws IOException {
        if(!progress(e,id,hash).equals("COMMITTED"))advance(e,id,hash,"HELD");
    }
    private static void check(Environment e,Lock l,CompoundTag n,byte[] expected,UUID id)throws IOException {
        thread(e);l.verify();if(!identity(e).equals(n.getCompound("identity"))||!Arrays.equals(expected,read(intentPath(e,id))))throw new IOException("Shop identity/history changed");
        e.guard(n.getCompound("facility").copy());
    }
    private static Lock lock(Environment e)throws IOException {
        thread(e);if(busy)throw new IOException("Another shop operation is active");busy=true;FileChannel channel=null;
        try{Path path=root(e).resolve("data/catancraft_shop40.lock");safe(path);Files.createDirectories(path.getParent());channel=FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);FileLock lock;
            try{lock=channel.tryLock();}catch(OverlappingFileLockException ex){throw new IOException("Shop storage is already locked",ex);}if(lock==null)throw new IOException("Shop storage is already locked");
            return new Lock(path,channel,lock);
        }catch(IOException|RuntimeException ex){try{if(channel!=null)channel.close();}finally{busy=false;}throw ex;}
    }
    private static final class Lock implements AutoCloseable {
        final Path path;final FileChannel channel;final FileLock lock;final Object key;
        Lock(Path p,FileChannel c,FileLock l)throws IOException{path=p;channel=c;lock=l;key=Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).fileKey();if(key==null)throw new IOException("Shop storage needs stable lock identity");}
        void verify()throws IOException{safe(path);if(!lock.isValid()||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||!key.equals(Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).fileKey()))throw new IOException("Shop lock changed");}
        public void close()throws IOException{try{if(lock.isValid())lock.release();}finally{try{channel.close();}finally{busy=false;}}}
    }
}
