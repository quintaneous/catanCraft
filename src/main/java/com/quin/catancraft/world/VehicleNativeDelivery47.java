package com.quin.catancraft.world;

import com.quin.catancraft.command.NationProcurement;
import com.quin.catancraft.data.*;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.zip.*;

/** Real native persistence adapter, exposed only to exact controlled fixtures.
 * Factory exit qualification and production physical authority are not registered here. */
final class VehicleNativeDelivery47 implements VehicleDelivery46.Environment {
    static final String PROVENANCE="CatanCraftVehicleDelivery47";
    enum SavePoint { BEFORE_LEDGER_SAVE, AFTER_LEDGER_SAVE, BEFORE_ENTITY_ADD, AFTER_ENTITY_ADD, BEFORE_ENTITY_SAVE, AFTER_ENTITY_SAVE }
    @FunctionalInterface interface Hooks {void at(SavePoint point)throws IOException;}
    @FunctionalInterface interface Authority {void validate(VehicleDelivery46.Intent intent,boolean beforeSpawn)throws IOException;}
    private static final Hooks NONE=p->{};
    private final ServerPlayer actor;
    private final ServerLevel level;
    private final VehicleDelivery46.Journal journal;
    private final Authority authority;
    private final Hooks hooks;
    private VehicleNativeDelivery47(ServerPlayer actor,VehicleDelivery46.Journal journal,Authority authority,Hooks hooks)throws IOException {
        this.actor=Objects.requireNonNull(actor);level=actor.serverLevel();this.journal=Objects.requireNonNull(journal);this.authority=Objects.requireNonNull(authority);this.hooks=Objects.requireNonNull(hooks);scope();
    }
    static VehicleNativeDelivery47 fixture(ServerPlayer actor,VehicleDelivery46.Journal journal,Authority authority)throws IOException{return fixture(actor,journal,authority,NONE);}
    static VehicleNativeDelivery47 fixture(ServerPlayer actor,VehicleDelivery46.Journal journal,Authority authority,Hooks hooks)throws IOException{return new VehicleNativeDelivery47(actor,journal,authority,hooks);}
    static String receiptKey(UUID id){return VehiclePayment47.key(id);}
    static ShopPurchase40.Price price(String offer)throws IOException{return VehiclePayment47.price(offer);}
    static String worldIdentity(ServerLevel level)throws IOException {
        String identity=level.getServer().getWorldPath(LevelResource.ROOT).toRealPath()+"\n"+level.getSeed()+"\n"+level.dimension().location();
        return "vehicle47:"+TownHallRevisions.sha(identity.getBytes(StandardCharsets.UTF_8));
    }
    static VehicleDelivery46.Intent newIntent(ServerPlayer actor,String offer,VehicleBay45.Bay bay)throws IOException {
        if(!actor.server.isSameThread())throw new IOException("Vehicle intent requires server thread");
        NationData nation=VehiclePayment47.leader(CatanSavedData.get(actor.server),actor.getUUID());
        if(!nation.id().equals(bay.facility().nation()))throw new IOException("Vehicle intent requires original nation leader");
        return new VehicleDelivery46.Intent(UUID.randomUUID(),actor.getUUID(),nation.id(),UUID.randomUUID(),worldIdentity(actor.serverLevel()),offer,VehiclePayment47.CATALOG,bay,price(offer));
    }
    private void scope()throws IOException {
        if(!(level.getServer() instanceof GameTestServer)||!level.getServer().isSameThread()||!level.getServer().getPlayerList().getPlayers().isEmpty())throw new IOException("Vehicle47 adapter is unregistered outside controlled GameTestServer");
        Path root=level.getServer().getWorldPath(LevelResource.ROOT).toRealPath();String parent=root.getParent().getFileName().toString();
        if(!root.getFileName().toString().equals("world")||!Set.of("controlled-catancraft_vehicle47-run","controlled-catancraft_vehicle47_restart-run").contains(parent)
                ||!Files.readString(root.getParent().resolve(".catancraft-controlled-fixture-v1")).equals("CatanCraft synthetic flat fixture v1\n")
                ||!level.dimension().location().toString().equals("minecraft:overworld")||level.getSeed()!=0||!ModList.get().isLoaded("superbwarfare"))throw new IOException("Foreign native vehicle fixture");
        if(!System.getProperty("catancraft.sbwRuntimeManifestSHA256","").matches("[0-9a-f]{64}"))throw new IOException("Missing pinned native vehicle runtime");
    }
    private static NativeVehicleBay46.Spec spec(VehicleDelivery46.Intent i)throws IOException {
        var s=switch(i.offer()){case "pickup"->NativeVehicleBay46.PICKUP;case "lav150"->NativeVehicleBay46.LAV;default->throw new IOException("Unknown vehicle offer");};
        if(!i.bay().vehicle().equals("superbwarfare:"+s.id())||!i.bay().nativeGeometrySHA256().equals(s.sha()))throw new IOException("Vehicle native definition differs from intent");return s;
    }
    private void definition(VehicleDelivery46.Intent i)throws IOException {
        var s=spec(i);var resource=new ResourceLocation("superbwarfare","sbw/vehicles/"+s.id()+".json");
        try(var in=level.getServer().getResourceManager().getResourceOrThrow(resource).open()){
            byte[] bytes=in.readNBytes(200001);
            if(bytes.length>200000||!TownHallRevisions.sha(bytes).equals(s.sha()))throw new IOException("Effective native vehicle definition changed before payment/spawn");
        }
        if(ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("superbwarfare",s.id()))==null)throw new IOException("Native vehicle type is unavailable");
    }
    private void identity(VehicleDelivery46.Intent i)throws IOException {
        scope();VehiclePayment47.requireOriginalPrice(i);spec(i);
        if(!worldIdentity(level).equals(i.world())||!actor.getUUID().equals(i.actor())||actor.serverLevel()!=level||!actor.isAlive()
                ||!level.dimension().location().toString().equals(i.bay().facility().dimension()))throw new IOException("Foreign actor/world/dimension vehicle intent");
        CatanSavedData data=CatanSavedData.get(level.getServer());NationData nation=VehiclePayment47.leader(data,i.actor());
        if(data.nation(i.nation())!=nation)throw new IOException("Vehicle nation/leader changed");
        VehicleDelivery46.Record r=journal.read(i.id());if(r!=null&&!r.intent().equals(i))throw new IOException("Vehicle journal identity changed");
    }
    private static ChunkPos chunk(VehicleDelivery46.Intent i){var p=i.bay().worldSpawn();return new ChunkPos(((int)Math.floor(p.x()))>>4,((int)Math.floor(p.z()))>>4);}
    private static void resident(ServerLevel level,ChunkPos chunk)throws IOException {
        if(level.getChunkSource().getChunkNow(chunk.x,chunk.z)==null||!level.areEntitiesLoaded(chunk.toLong()))throw new IOException("Vehicle block/entity chunk is not resident");
    }
    private void loaded(VehicleDelivery46.Intent i)throws IOException {
        Set<ChunkPos> chunks=new HashSet<>();chunks.add(chunk(i));
        for(var mask:List.of(i.bay().support(),i.bay().clearance(),i.bay().boarding(),i.bay().departure()))for(var c:mask){var p=i.bay().world(c);chunks.add(new ChunkPos(p.x()>>4,p.z()>>4));}
        if(chunks.size()>64)throw new IOException("Vehicle delivery envelope exceeds bounded residency");for(var c:chunks)resident(level,c);
    }
    public void guard(VehicleDelivery46.Intent i)throws IOException {
        identity(i);definition(i);loaded(i);VehicleDelivery46.Record r=journal.read(i.id());
        authority.validate(i,r==null||r.phase().ordinal()<VehicleDelivery46.Phase.SPAWN_ATTEMPTED.ordinal());
    }
    public VehicleDelivery46.Debit debitState(VehicleDelivery46.Intent i)throws IOException{identity(i);return VehiclePayment47.state(level,i);}
    public void debitAndSave(VehicleDelivery46.Intent i)throws IOException {
        guard(i);VehicleDelivery46.Record r=journal.read(i.id());
        if(r==null||r.phase()!=VehicleDelivery46.Phase.DEBIT_ATTEMPTED)throw new IOException("Missing durable debit-attempt boundary");
        VehiclePayment47.debit(level,i,hooks);
    }
    public VehicleDelivery46.Delivery deliveryState(VehicleDelivery46.Intent i)throws IOException {
        identity(i);try{loaded(i);}catch(IOException unloaded){return VehicleDelivery46.Delivery.UNKNOWN;}
        Entity present=null;
        for(ServerLevel other:level.getServer().getAllLevels()){
            Entity found=other.getEntity(i.entity());if(found!=null){if(other!=level||present!=null)return VehicleDelivery46.Delivery.CONFLICT;present=found;}
        }
        CompoundTag disk=savedEntity(level,i);
        if(present==null){
            if(disk!=null)return VehicleDelivery46.Delivery.UNKNOWN;
            var r=journal.read(i.id());return r==null||r.phase().ordinal()<VehicleDelivery46.Phase.SPAWN_ATTEMPTED.ordinal()?VehicleDelivery46.Delivery.ABSENT_LOADED:VehicleDelivery46.Delivery.UNKNOWN;
        }
        if(!identityMatches(present,i))return VehicleDelivery46.Delivery.CONFLICT;
        if(!atFixedPose(present,i))return VehicleDelivery46.Delivery.UNKNOWN;
        if(disk==null)return VehicleDelivery46.Delivery.EXACT_UNSAVED;
        if(!matches(disk,i))return VehicleDelivery46.Delivery.CONFLICT;
        // Verify every native field, including provider data, rather than recognizing tags alone.
        if(!snapshot(present).equals(disk))return VehicleDelivery46.Delivery.EXACT_UNSAVED;
        return VehicleDelivery46.Delivery.EXACT_SAVED;
    }
    public void spawnAndSave(VehicleDelivery46.Intent i)throws IOException {
        identity(i);definition(i);loaded(i);authority.validate(i,true);
        var r=journal.read(i.id());if(r==null||r.phase()!=VehicleDelivery46.Phase.SPAWN_ATTEMPTED||VehiclePayment47.state(level,i)!=VehicleDelivery46.Debit.EXACT)throw new IOException("Vehicle spawn lacks original durable attempt/debit");
        for(ServerLevel other:level.getServer().getAllLevels())if(other.getEntity(i.entity())!=null)throw new IOException("Preselected native UUID already exists");
        if(savedEntity(level,i)!=null)throw new IOException("Preselected native UUID already stored in delivery chunk");
        Entity entity;
        try{
            entity=NativeVehicleBay46.create(level,spec(i));entity.setUUID(i.entity());var p=i.bay().worldSpawn();
            NativeVehicleBay46.pose(entity,new Vec3(p.x(),p.y(),p.z()),i.bay().worldHeading());
        }catch(Exception failure){throw new IOException("Cannot initialize exact native vehicle",failure);}
        entity.getPersistentData().putBoolean(NationProcurement.PURCHASED_TAG,true);
        entity.getPersistentData().putString(NationProcurement.NATION_TAG,i.nation().toString());
        entity.getPersistentData().put(PROVENANCE,provenance(i));
        hooks.at(SavePoint.BEFORE_ENTITY_ADD);
        if(!level.addFreshEntity(entity))throw new IOException("Native world refused exact vehicle UUID");
        hooks.at(SavePoint.AFTER_ENTITY_ADD);hooks.at(SavePoint.BEFORE_ENTITY_SAVE);
        if(!level.getServer().saveEverything(true,true,true))throw new IOException("Native entity/world save refused");
        if(deliveryState(i)!=VehicleDelivery46.Delivery.EXACT_SAVED)throw new IOException("Native vehicle entity-region readback differs");
        hooks.at(SavePoint.AFTER_ENTITY_SAVE);
    }
    static CompoundTag provenance(VehicleDelivery46.Intent i)throws IOException {
        CompoundTag n=new CompoundTag();n.putInt("schema",1);n.putUUID("operation",i.id());n.putUUID("actor",i.actor());n.putUUID("nation",i.nation());n.putUUID("entity",i.entity());n.putString("world",i.world());n.putString("offer",i.offer());n.putString("type",i.bay().vehicle());n.putString("fingerprint",VehicleDelivery46.fingerprint(i));
        var p=i.bay().worldSpawn();n.putDouble("x",p.x());n.putDouble("y",p.y());n.putDouble("z",p.z());n.putInt("yaw",i.bay().worldHeading());return n;
    }
    static boolean matches(Entity e,VehicleDelivery46.Intent i)throws IOException{return identityMatches(e,i)&&atFixedPose(e,i);}
    private static boolean identityMatches(Entity e,VehicleDelivery46.Intent i)throws IOException {
        return !e.isRemoved()&&e.getUUID().equals(i.entity())&&i.bay().vehicle().equals(String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(e.getType())))
                &&e.level().dimension().location().toString().equals(i.bay().facility().dimension())&&forgeMatches(e.getPersistentData(),i);
    }
    private static boolean forgeMatches(CompoundTag f,VehicleDelivery46.Intent i)throws IOException {
        return f.contains(NationProcurement.PURCHASED_TAG,Tag.TAG_BYTE)&&f.getBoolean(NationProcurement.PURCHASED_TAG)
                &&f.contains(NationProcurement.NATION_TAG,Tag.TAG_STRING)&&f.getString(NationProcurement.NATION_TAG).equals(i.nation().toString())
                &&f.contains(PROVENANCE,Tag.TAG_COMPOUND)&&f.getCompound(PROVENANCE).equals(provenance(i));
    }
    private static boolean atFixedPose(Entity e,VehicleDelivery46.Intent i){var p=i.bay().worldSpawn();return close(e.getX(),p.x())&&close(e.getY(),p.y())&&close(e.getZ(),p.z())&&yaw(e.getYRot(),i.bay().worldHeading())&&close(e.getXRot(),0)&&uprightRoll(e);}
    private static boolean uprightRoll(Entity e){
        // Pinned SBW getRoll(delta) lerps prevRoll/current roll, neither of which is
        // represented by the native entity NBT. Inspect both endpoints independently.
        try{
            var getter=e.getClass().getMethod("getRoll",float.class);
            Object previous=getter.invoke(e,0f),current=getter.invoke(e,1f);
            return previous instanceof Number a&&current instanceof Number b&&close(a.doubleValue(),0)&&close(b.doubleValue(),0);
        }catch(ReflectiveOperationException|RuntimeException unsupported){return false;}
    }
    private static boolean close(double a,double b){return Double.isFinite(a)&&Double.isFinite(b)&&Math.abs(a-b)<0.000001;}
    private static boolean yaw(double a,double b){return Double.isFinite(a)&&Double.isFinite(b)&&Math.abs(Math.IEEEremainder(a-b,360))<0.0001;}
    static CompoundTag snapshot(Entity e){CompoundTag n=e.saveWithoutId(new CompoundTag());n.putString("id",String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(e.getType())));return n;}
    static boolean matches(CompoundTag n,VehicleDelivery46.Intent i)throws IOException {
        if(!n.hasUUID("UUID")||!n.getUUID("UUID").equals(i.entity())||!n.contains("id",Tag.TAG_STRING)||!n.getString("id").equals(i.bay().vehicle())||!n.contains("ForgeData",Tag.TAG_COMPOUND)||!forgeMatches(n.getCompound("ForgeData"),i))return false;
        if(!n.contains("Pos",Tag.TAG_LIST)||!n.contains("Rotation",Tag.TAG_LIST))return false;
        ListTag pos=n.getList("Pos",Tag.TAG_DOUBLE),rot=n.getList("Rotation",Tag.TAG_FLOAT);var p=i.bay().worldSpawn();
        return pos.size()==3&&rot.size()==2&&close(pos.getDouble(0),p.x())&&close(pos.getDouble(1),p.y())&&close(pos.getDouble(2),p.z())&&yaw(rot.getFloat(0),i.bay().worldHeading())&&close(rot.getFloat(1),0);
    }

    /** Independent READ-ONLY Anvil parser. Does not construct RegionFile, allocate chunks,
     * load entities, create directories or repair files. Exact bay chunk only; no assertion
     * that an arbitrary caller UUID is absent from every unloaded chunk in every dimension. */
    static CompoundTag savedEntity(ServerLevel level,VehicleDelivery46.Intent i)throws IOException {
        if(!level.getServer().isSameThread()||!worldIdentity(level).equals(i.world())||!level.dimension().location().toString().equals(i.bay().facility().dimension()))throw new IOException("Foreign native entity readback world/thread");
        ChunkPos c=chunk(i);resident(level,c);Path root=level.getServer().getWorldPath(LevelResource.ROOT).toRealPath();
        if(!level.dimension().location().toString().equals("minecraft:overworld"))throw new IOException("Unqualified entity storage dimension");
        Path file=root.resolve("entities/r."+(c.x>>5)+"."+(c.z>>5)+".mca");
        return readEntityChunk(file,c,i.entity());
    }
    /** Same parser/schema path as native delivery; exposed for immutable malformed-file fixtures. */
    static CompoundTag readEntityChunk(Path file,ChunkPos c,UUID expected)throws IOException {
        VehiclePayment47.safe(file);CompoundTag chunk=readRegion(file,c);if(chunk==null)return null;
        if(!chunk.contains("Position",Tag.TAG_INT_ARRAY)||!Arrays.equals(chunk.getIntArray("Position"),new int[]{c.x,c.z})||!chunk.contains("Entities",Tag.TAG_LIST))throw new IOException("Foreign entity chunk position/schema");
        ListTag list=(ListTag)chunk.get("Entities");
        if(list.size()>4096||(list.getElementType()!=Tag.TAG_COMPOUND&&!(list.isEmpty()&&list.getElementType()==Tag.TAG_END)))throw new IOException("Invalid/oversized native entity list");
        CompoundTag found=null;Set<UUID> seen=new HashSet<>();
        for(Tag t:list){
            if(!(t instanceof CompoundTag e)||!e.hasUUID("UUID")||!e.contains("id",Tag.TAG_STRING)||ResourceLocation.tryParse(e.getString("id"))==null)throw new IOException("Malformed persisted native entity identity");
            UUID id=e.getUUID("UUID");if(!seen.add(id))throw new IOException("Duplicate persisted entity UUID");
            if(id.equals(expected))found=e.copy();
        }
        return found;
    }
    private static CompoundTag readRegion(Path file,ChunkPos chunk)throws IOException {
        if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS))return null;
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("Native entity region is not a file");
        BasicFileAttributes before=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(before.fileKey()==null||before.size()<8192||before.size()>1024L*1024*1024||before.size()%4096!=0)throw new IOException("Invalid native region size/file identity");
        byte[] payload;
        try(FileChannel in=FileChannel.open(file,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            byte[] header=read(in,0,8192);int index=(chunk.x&31)+(chunk.z&31)*32;int location=ByteBuffer.wrap(header,index*4,4).getInt();
            // Validate all occupied extents, including when this chunk appears absent.
            BitSet allocated=new BitSet((int)(before.size()/4096));allocated.set(0,2);
            for(int n=0;n<1024;n++){
                int entry=ByteBuffer.wrap(header,n*4,4).getInt();if(entry==0)continue;
                int start=entry>>>8,count=entry&255,end=start+count;
                if(start<2||count==0||(long)end*4096>before.size())throw new IOException("Invalid native region location");
                int overlap=allocated.nextSetBit(start);if(overlap>=0&&overlap<end)throw new IOException("Overlapping native region locations");allocated.set(start,end);
            }
            if(location==0){stable(file,before,in,header,null,0);return null;}
            int offset=location>>>8,sectors=location&255;
            payload=read(in,(long)offset*4096,sectors*4096);int length=ByteBuffer.wrap(payload,0,4).getInt();
            if(length<2||length>payload.length-4)throw new IOException("Invalid native entity payload length");
            int compression=payload[4]&255;if((compression&128)!=0)throw new IOException("External native entity chunks are not qualified");
            stable(file,before,in,header,payload,(long)offset*4096);
            try(InputStream bytes=new ByteArrayInputStream(payload,5,length-1);InputStream decoded=switch(compression){case 1->new GZIPInputStream(bytes);case 2->new InflaterInputStream(bytes);case 3->bytes;default->throw new IOException("Unknown native entity compression");}){
                byte[] nbt=decoded.readNBytes(16*1024*1024+1);if(nbt.length>16*1024*1024)throw new IOException("Oversized decompressed native entity chunk");
                try(DataInputStream data=new DataInputStream(new ByteArrayInputStream(nbt))){
                    CompoundTag result=NbtIo.read(data,new NbtAccounter(32L*1024*1024));if(result==null||data.read()!=-1)throw new IOException("Malformed trailing native entity NBT");return result;
                }catch(RuntimeException malformed){throw new IOException("Malformed or oversized native entity NBT",malformed);}
            }
        }
    }
    private static byte[] read(FileChannel in,long position,int count)throws IOException {
        ByteBuffer b=ByteBuffer.allocate(count);while(b.hasRemaining()){int n=in.read(b,position+b.position());if(n<0)throw new IOException("Truncated native region");if(n==0)throw new IOException("Unstable native region read");}return b.array();
    }
    private static void stable(Path file,BasicFileAttributes before,FileChannel in,byte[] header,byte[] payload,long offset)throws IOException {
        if(!Arrays.equals(header,read(in,0,8192))||(payload!=null&&!Arrays.equals(payload,read(in,offset,payload.length))))throw new IOException("Native entity region changed during readback");
        BasicFileAttributes after=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!after.isRegularFile()||after.fileKey()==null||before.size()!=after.size()||!before.lastModifiedTime().equals(after.lastModifiedTime())||!Objects.equals(before.fileKey(),after.fileKey())||in.size()!=before.size())throw new IOException("Native entity region changed during readback");
    }
}
