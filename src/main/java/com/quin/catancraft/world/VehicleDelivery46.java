package com.quin.catancraft.world;

import com.quin.catancraft.data.ResourceType;
import net.minecraft.nbt.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;

/** Unregistered entity-delivery protocol. No native adapter or public purchase route is activated.
 * Ambiguous debit/spawn attempts stay held; this class never refunds or recreates delivery. */
final class VehicleDelivery46 {
    enum Phase { PREPARED, DEBIT_ATTEMPTED, PAID, SPAWN_ATTEMPTED, COMPLETE }
    enum Debit { ABSENT, EXACT, UNKNOWN, CONFLICT }
    enum Delivery { ABSENT_LOADED, EXACT_UNSAVED, EXACT_SAVED, UNKNOWN, CONFLICT }
    record Intent(UUID id, UUID actor, UUID nation, UUID entity, String world,
                  String offer, String catalog, VehicleBay45.Bay bay, ShopPurchase40.Price price) {
        Intent {
            uuid(id); uuid(actor); uuid(nation); uuid(entity); label(world); label(offer); label(catalog);
            Objects.requireNonNull(bay); Objects.requireNonNull(price);
            String type = switch (offer) { case "pickup" -> "superbwarfare:sodayo_pick_up"; case "lav150" -> "superbwarfare:lav_150"; default -> throw new IllegalArgumentException("Unknown historical vehicle offer"); };
            if (!nation.equals(bay.facility().nation()) || !type.equals(bay.vehicle()))
                throw new IllegalArgumentException("Foreign facility or vehicle identity");
        }
    }
    record Record(Intent intent, Phase phase) {
        Record { Objects.requireNonNull(intent); Objects.requireNonNull(phase); }
    }
    record Result(boolean complete, String reason) {}
    interface Journal {
        Record read(UUID id) throws IOException;
        void create(Intent intent) throws IOException;
        void transition(Record expected, Phase next) throws IOException;
    }
    /** Native integration must verify memory/disk agreement. EXACT debit means the original
     * price and operation fingerprint are durably posted together; EXACT_SAVED means the
     * exact entity UUID/type/nation/operation fingerprint and original fixed-bay transform
     * have independently verified durable entity storage. Moved or missing entities before
     * settlement remain UNKNOWN rather than being replaced. An entity scan or unloaded-chunk absence is never this evidence. */
    interface Environment {
        void guard(Intent intent) throws IOException;
        Debit debitState(Intent intent) throws IOException;
        void debitAndSave(Intent intent) throws IOException;
        Delivery deliveryState(Intent intent) throws IOException;
        void spawnAndSave(Intent intent) throws IOException;
    }
    private VehicleDelivery46() {}

    static void prepare(Journal journal, Intent intent, Environment env) throws IOException {
        Objects.requireNonNull(intent); Objects.requireNonNull(journal); Objects.requireNonNull(env);
        Record prior = journal.read(intent.id());
        if (prior != null) {
            if (!prior.intent().equals(intent)) throw new IOException("Vehicle operation UUID already binds another intent");
            return;
        }
        env.guard(intent);
        if (env.debitState(intent) != Debit.ABSENT || env.deliveryState(intent) != Delivery.ABSENT_LOADED)
            throw new IOException("New vehicle intent has existing or ambiguous delivery/payment");
        journal.create(intent);
        requireRecord(journal, new Record(intent, Phase.PREPARED));
    }
    static Result execute(Journal journal, UUID id, Environment env) throws IOException {
        Objects.requireNonNull(journal); Objects.requireNonNull(env); uuid(id);
        Record r = journal.read(id);
        if (r == null || !id.equals(r.intent().id())) throw new IOException("Missing or foreign vehicle intent");
        Intent i = r.intent();
        // A completed vehicle can move, be destroyed, or leave loaded chunks. Never recreate it.
        if (r.phase() == Phase.COMPLETE) return new Result(true, "Already settled; no delivery replay");
        env.guard(i);
        if (r.phase() == Phase.PREPARED) {
            if (env.debitState(i) != Debit.ABSENT || env.deliveryState(i) != Delivery.ABSENT_LOADED)
                return held("Prepared vehicle has conflicting or unknown payment/delivery");
            r = advance(journal, r, Phase.DEBIT_ATTEMPTED);
            try { env.debitAndSave(i); } catch (IOException failed) {
                if (env.debitState(i) != Debit.EXACT) return held("Debit attempt requires exact durable readback: " + failed.getMessage());
            }
        }
        if (r.phase() == Phase.DEBIT_ATTEMPTED) {
            if (env.debitState(i) != Debit.EXACT) return held("Debit attempt unresolved; no repeated charge");
            r = advance(journal, r, Phase.PAID);
        }
        if (env.debitState(i) != Debit.EXACT) return held("Historical debit is missing, changed or unverifiable");
        if (r.phase() == Phase.PAID) {
            // This is the only path allowed to invoke spawn. A pre-existing entity is not
            // retroactively adopted as this delivery before the attempt marker.
            env.guard(i);
            if (env.deliveryState(i) != Delivery.ABSENT_LOADED) return held("Paid vehicle bay/UUID is ambiguous before spawn");
            r = advance(journal, r, Phase.SPAWN_ATTEMPTED);
            try { env.spawnAndSave(i); } catch (IOException failed) {
                if (env.deliveryState(i) != Delivery.EXACT_SAVED) return held("Spawn attempt unresolved: " + failed.getMessage());
            }
        }
        if (r.phase() != Phase.SPAWN_ATTEMPTED) throw new IOException("Unexpected vehicle phase");
        // Loaded absence, moved/gone entities, unsaved presence and unknown regions all HOLD.
        // The attempt marker deliberately loses availability at the crash-before-spawn window.
        if (env.deliveryState(i) != Delivery.EXACT_SAVED || env.debitState(i) != Debit.EXACT)
            return held("Spawn attempt requires exact saved entity and original debit; no replacement/refund");
        advance(journal, r, Phase.COMPLETE);
        return new Result(true, "Original vehicle delivery settled once");
    }
    private static Result held(String reason) { return new Result(false, reason); }
    private static Record advance(Journal journal, Record old, Phase next) throws IOException {
        if (next.ordinal() != old.phase().ordinal() + 1) throw new IOException("Non-monotonic vehicle phase");
        journal.transition(old, next); Record updated = new Record(old.intent(), next);
        requireRecord(journal, updated); return updated;
    }
    private static void requireRecord(Journal journal, Record expected) throws IOException {
        if (!expected.equals(journal.read(expected.intent().id()))) throw new IOException("Vehicle journal save/readback did not agree");
    }

    /** Strict schema: no unknown-field coercion, defaults, historical repricing or bay relocation. */
    static CompoundTag encode(Record record) {
        Intent i = record.intent(); CompoundTag n = new CompoundTag(); n.putInt("schema", 1); n.putString("phase", record.phase().name());
        n.putUUID("id", i.id()); n.putUUID("actor", i.actor()); n.putUUID("nation", i.nation()); n.putUUID("entity", i.entity());
        n.putString("world", i.world()); n.putString("offer", i.offer()); n.putString("catalog", i.catalog());
        n.put("bay", bayTag(i.bay())); n.put("price", priceTag(i.price())); return n;
    }
    static Record decode(CompoundTag n) throws IOException {
        try {
            keys(n,"schema","phase","id","actor","nation","entity","world","offer","catalog","bay","price");
            type(n,"schema",Tag.TAG_INT); if (n.getInt("schema") != 1) throw new IOException("Unknown vehicle journal schema");
            for (String k : List.of("id","actor","nation","entity")) if (!n.hasUUID(k)) throw new IOException("Malformed vehicle UUID");
            for (String k : List.of("phase","world","offer","catalog")) type(n,k,Tag.TAG_STRING);
            type(n,"bay",Tag.TAG_COMPOUND); type(n,"price",Tag.TAG_COMPOUND);
            return new Record(new Intent(n.getUUID("id"),n.getUUID("actor"),n.getUUID("nation"),n.getUUID("entity"),
                    n.getString("world"),n.getString("offer"),n.getString("catalog"),bay(n.getCompound("bay")),price(n.getCompound("price"))),Phase.valueOf(n.getString("phase")));
        } catch (IllegalArgumentException | NullPointerException ex) { throw new IOException("Malformed vehicle journal", ex); }
    }
    static String fingerprint(Intent intent) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); canonical(encode(new Record(intent,Phase.PREPARED)),new DataOutputStream(out));
        return TownHallRevisions.sha(out.toByteArray());
    }
    /** Canonical, type-preserving digest input independent of map insertion/GZIP order. */
    private static void canonical(Tag tag, DataOutputStream out)throws IOException {
        out.writeByte(tag.getId());
        switch(tag.getId()) {
            case Tag.TAG_COMPOUND -> {
                CompoundTag n=(CompoundTag)tag;List<String> keys=new ArrayList<>(n.getAllKeys());Collections.sort(keys);out.writeInt(keys.size());
                for(String k:keys){out.writeUTF(k);canonical(n.get(k),out);}
            }
            case Tag.TAG_LIST -> {ListTag list=(ListTag)tag;out.writeInt(list.size());for(Tag child:list)canonical(child,out);}
            case Tag.TAG_INT_ARRAY -> {int[] values=((IntArrayTag)tag).getAsIntArray();out.writeInt(values.length);for(int v:values)out.writeInt(v);}
            case Tag.TAG_INT -> out.writeInt(((IntTag)tag).getAsInt());
            case Tag.TAG_LONG -> out.writeLong(((LongTag)tag).getAsLong());
            case Tag.TAG_DOUBLE -> out.writeDouble(((DoubleTag)tag).getAsDouble());
            case Tag.TAG_STRING -> out.writeUTF(tag.getAsString());
            default -> throw new IOException("Unexpected vehicle fingerprint type");
        }
    }
    private static CompoundTag priceTag(ShopPurchase40.Price p) {
        CompoundTag n=new CompoundTag(),resources=new CompoundTag(); n.putLong("money",p.money());
        p.resources().forEach((k,v)->resources.putLong(k.name(),v)); n.put("resources",resources); return n;
    }
    private static ShopPurchase40.Price price(CompoundTag n)throws IOException {
        keys(n,"money","resources");type(n,"money",Tag.TAG_LONG);type(n,"resources",Tag.TAG_COMPOUND);
        Map<ResourceType,Long> values=new EnumMap<>(ResourceType.class);CompoundTag r=n.getCompound("resources");
        if(r.size()>ResourceType.values().length)throw new IOException("Oversized vehicle price");
        for(String k:r.getAllKeys()){type(r,k,Tag.TAG_LONG);values.put(ResourceType.valueOf(k),r.getLong(k));}
        return new ShopPurchase40.Price(n.getLong("money"),values);
    }
    private static CompoundTag cellTag(VehicleBay45.Cell c){CompoundTag n=new CompoundTag();n.putInt("x",c.x());n.putInt("y",c.y());n.putInt("z",c.z());return n;}
    private static VehicleBay45.Cell cell(CompoundTag n)throws IOException{keys(n,"x","y","z");for(String k:List.of("x","y","z"))type(n,k,Tag.TAG_INT);return new VehicleBay45.Cell(n.getInt("x"),n.getInt("y"),n.getInt("z"));}
    private static ListTag maskTag(Set<VehicleBay45.Cell> cells){ListTag list=new ListTag();cells.stream().sorted(Comparator.comparingInt(VehicleBay45.Cell::x).thenComparingInt(VehicleBay45.Cell::y).thenComparingInt(VehicleBay45.Cell::z)).forEach(c->list.add(cellTag(c)));return list;}
    private static Set<VehicleBay45.Cell> mask(CompoundTag n,String key)throws IOException{
        type(n,key,Tag.TAG_LIST);ListTag list=(ListTag)n.get(key);if(list.isEmpty()||list.size()>65536||list.getElementType()!=Tag.TAG_COMPOUND)throw new IOException("Invalid vehicle mask");
        Set<VehicleBay45.Cell> result=new HashSet<>();for(Tag t:list)if(!result.add(cell((CompoundTag)t)))throw new IOException("Duplicate vehicle mask cell");return result;
    }
    private static CompoundTag bayTag(VehicleBay45.Bay b){
        CompoundTag n=new CompoundTag(),f=new CompoundTag();var a=b.facility();
        f.putString("city",a.city());f.putString("plot",a.plot());f.putUUID("nation",a.nation());f.putString("dimension",a.dimension());f.putString("revision",a.revision());f.putString("source",a.sourceSHA256());f.put("anchor",cellTag(a.anchor()));f.putInt("rotation",a.rotation());f.put("counter",cellTag(a.counter()));f.putInt("level",a.level());
        n.put("facility",f);n.putString("id",b.id());n.putString("vehicle",b.vehicle());n.putString("geometry",b.nativeGeometrySHA256());
        CompoundTag pos=new CompoundTag();pos.putDouble("x",b.spawn().x());pos.putDouble("y",b.spawn().y());pos.putDouble("z",b.spawn().z());n.put("spawn",pos);n.putInt("heading",b.heading());
        n.put("support",maskTag(b.support()));n.put("clearance",maskTag(b.clearance()));n.put("boarding",maskTag(b.boarding()));n.put("departure",maskTag(b.departure()));return n;
    }
    private static VehicleBay45.Bay bay(CompoundTag n)throws IOException{
        keys(n,"facility","id","vehicle","geometry","spawn","heading","support","clearance","boarding","departure");
        for(String k:List.of("id","vehicle","geometry"))type(n,k,Tag.TAG_STRING);type(n,"facility",Tag.TAG_COMPOUND);type(n,"spawn",Tag.TAG_COMPOUND);type(n,"heading",Tag.TAG_INT);
        CompoundTag f=n.getCompound("facility");keys(f,"city","plot","nation","dimension","revision","source","anchor","rotation","counter","level");
        for(String k:List.of("city","plot","dimension","revision","source"))type(f,k,Tag.TAG_STRING);
        for(String k:List.of("rotation","level"))type(f,k,Tag.TAG_INT);for(String k:List.of("anchor","counter"))type(f,k,Tag.TAG_COMPOUND);
        if(!f.hasUUID("nation"))throw new IOException("Invalid facility nation");
        var facility=new VehicleBay45.Facility(f.getString("city"),f.getString("plot"),f.getUUID("nation"),f.getString("dimension"),f.getString("revision"),f.getString("source"),cell(f.getCompound("anchor")),f.getInt("rotation"),cell(f.getCompound("counter")),f.getInt("level"));
        CompoundTag p=n.getCompound("spawn");keys(p,"x","y","z");for(String k:List.of("x","y","z"))type(p,k,Tag.TAG_DOUBLE);
        return new VehicleBay45.Bay(n.getString("id"),facility,n.getString("vehicle"),n.getString("geometry"),new VehicleBay45.Position(p.getDouble("x"),p.getDouble("y"),p.getDouble("z")),n.getInt("heading"),mask(n,"support"),mask(n,"clearance"),mask(n,"boarding"),mask(n,"departure"));
    }
    private static void keys(CompoundTag n,String...keys)throws IOException{if(!n.getAllKeys().equals(Set.of(keys)))throw new IOException("Unknown or missing vehicle journal fields");}
    private static void type(CompoundTag n,String key,int type)throws IOException{if(!n.contains(key,type))throw new IOException("Wrong vehicle journal field type: "+key);}
    private static void label(String text){if(text==null||text.isBlank()||text.length()>160)throw new IllegalArgumentException("Explicit bounded vehicle identity required");}
    private static void uuid(UUID id){if(id==null||id.equals(new UUID(0,0)))throw new IllegalArgumentException("Nonzero vehicle identity required");}

    /** Append-only sealed phase files. Torn/foreign/gapped records are preserved and refused.
     * Caller supplies an authorized directory; this class never discovers or opens a live world. */
    static final class FileJournal implements Journal {
        private final Path root;
        FileJournal(Path directory)throws IOException{root=directory.toAbsolutePath().normalize();safe(root);Files.createDirectories(root);safe(root);}
        private Path path(UUID id,Phase p){uuid(id);return root.resolve(id+"."+p.ordinal()+".dat");}
        private static void safe(Path p)throws IOException{
            Path walk=p.getRoot();for(Path part:p){walk=walk.resolve(part);if(Files.isSymbolicLink(walk))throw new IOException("Vehicle journal symlink refused");
                if(!walk.equals(p)&&Files.exists(walk,LinkOption.NOFOLLOW_LINKS)&&!Files.isDirectory(walk,LinkOption.NOFOLLOW_LINKS))throw new IOException("Vehicle journal ancestor is not a directory");}
        }
        public Record read(UUID id)throws IOException{
            safe(root);Record latest=null;boolean missing=false;Set<Path> expected=new HashSet<>();
            for(Phase p:Phase.values()){
                Path f=path(id,p);expected.add(f);safe(f);
                if(!Files.exists(f,LinkOption.NOFOLLOW_LINKS)){missing=true;continue;}
                if(missing)throw new IOException("Gapped vehicle journal phases");
                Record r=decode(ProducerJournal.read(f));
                if(!r.intent().id().equals(id)||r.phase()!=p||(latest!=null&&!latest.intent().equals(r.intent())))throw new IOException("Changed vehicle journal identity/phase");latest=r;
            }
            int count=0;try(var files=Files.newDirectoryStream(root,id+".*")){for(Path f:files)if(++count>5||!expected.contains(f))throw new IOException("Unknown vehicle journal companion");}
            return latest;
        }
        public void create(Intent intent)throws IOException{locked(()->{if(read(intent.id())!=null)throw new IOException("Vehicle intent exists");append(new Record(intent,Phase.PREPARED));});}
        public void transition(Record expected,Phase next)throws IOException{locked(()->{
            if(next.ordinal()!=expected.phase().ordinal()+1||!expected.equals(read(expected.intent().id())))throw new IOException("Vehicle journal compare-and-append refused");
            append(new Record(expected.intent(),next));
        });}
        private void append(Record record)throws IOException{
            Path destination=path(record.intent().id(),record.phase());safe(destination);
            Path temporary=root.resolve("vehicle46-"+UUID.randomUUID()+".sealed");
            try{
                ProducerJournal.write(temporary,encode(record),false);byte[] bytes=Files.readAllBytes(temporary);
                // CREATE_NEW never overwrites a prior phase. A partial file causes a hold on readback.
                try(FileChannel out=FileChannel.open(destination,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
                    ByteBuffer buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())out.write(buffer);out.force(true);
                }
                try(FileChannel dir=FileChannel.open(root,StandardOpenOption.READ)){dir.force(true);}catch(UnsupportedOperationException|AccessDeniedException unsupported){}
                if(!decode(ProducerJournal.read(destination)).equals(record))throw new IOException("Vehicle journal readback mismatch");
            }finally{Files.deleteIfExists(temporary);}
        }
        @FunctionalInterface private interface IO {void run()throws IOException;}
        private void locked(IO action)throws IOException{
            Path p=root.resolve("vehicle46.lock");safe(p);
            try(FileChannel c=FileChannel.open(p,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
                FileLock lock;try{lock=c.tryLock();}catch(OverlappingFileLockException busy){throw new IOException("Vehicle journal busy",busy);}
                if(lock==null)throw new IOException("Vehicle journal busy");try(lock){action.run();}
            }
        }
    }
}
