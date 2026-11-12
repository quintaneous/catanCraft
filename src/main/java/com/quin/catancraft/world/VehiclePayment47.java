package com.quin.catancraft.world;

import com.quin.catancraft.data.*;
import com.quin.catancraft.economy.EconomyCost;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Actual nation debit and operation receipt share one authoritative native SavedData file. */
final class VehiclePayment47 {
    static final String CATALOG="native_vehicle47_v1";
    private VehiclePayment47(){}
    static String key(UUID id){return "vehicle47_paid:"+id;}
    static ShopPurchase40.Price price(String offer)throws IOException {
        return switch(offer){
            case "pickup" -> new ShopPurchase40.Price(4000,Map.of(ResourceType.STEEL,20L,ResourceType.MECHANICAL_PARTS,10L,ResourceType.FUEL,8L));
            case "lav150" -> new ShopPurchase40.Price(8500,Map.of(ResourceType.STEEL,50L,ResourceType.MECHANICAL_PARTS,25L,ResourceType.FUEL,20L,ResourceType.ELECTRONICS,5L));
            default -> throw new IOException("Unknown vehicle47 offer");
        };
    }
    static void requireOriginalPrice(VehicleDelivery46.Intent i)throws IOException {
        if(!CATALOG.equals(i.catalog())||!price(i.offer()).equals(i.price()))throw new IOException("Unknown or modified historical vehicle47 catalog/price");
    }
    static NationData leader(CatanSavedData data,UUID actor)throws IOException {
        List<NationData> memberships=data.nations().stream().filter(n->n.members().contains(actor)).toList();
        if(memberships.size()!=1||!memberships.get(0).isLeader(actor))throw new IOException("Vehicle payment requires one unambiguous original nation leader");
        return memberships.get(0);
    }
    static CompoundTag disk(ServerLevel level)throws IOException {
        Path path=level.getServer().getWorldPath(LevelResource.ROOT).toRealPath().resolve("data/catancraft.dat");
        safe(path);var before=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!before.isRegularFile()||before.fileKey()==null||before.size()>16*1024*1024)throw new IOException("Missing, oversized or unidentifiable authoritative nation ledger");
        byte[] bytes;try(var in=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){bytes=in.readNBytes(16*1024*1024+1);}
        var after=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(bytes.length!=before.size()||!after.isRegularFile()||!Objects.equals(before.fileKey(),after.fileKey())||before.size()!=after.size()||!before.lastModifiedTime().equals(after.lastModifiedTime()))throw new IOException("Authoritative nation ledger changed during readback");
        CompoundTag envelope;
        try(var gzip=new GZIPInputStream(new ByteArrayInputStream(bytes))){
            byte[] decoded=gzip.readNBytes(64*1024*1024+1);if(decoded.length>64*1024*1024)throw new IOException("Oversized decompressed authoritative nation ledger");
            try(var in=new DataInputStream(new ByteArrayInputStream(decoded))){envelope=NbtIo.read(in,new NbtAccounter(128L*1024*1024));if(envelope==null||in.read()!=-1)throw new IOException("Malformed trailing native nation ledger NBT");}
        }catch(RuntimeException malformed){throw new IOException("Malformed or oversized authoritative nation ledger NBT",malformed);}
        if(!envelope.contains("data",Tag.TAG_COMPOUND))throw new IOException("Malformed native nation ledger");
        return envelope.getCompound("data");
    }
    static VehicleDelivery46.Debit state(ServerLevel level,VehicleDelivery46.Intent i)throws IOException {
        requireOriginalPrice(i);CatanSavedData data=CatanSavedData.get(level.getServer());
        CompoundTag memory=data.save(new CompoundTag()),disk=disk(level);
        if(!memory.equals(disk))return VehicleDelivery46.Debit.UNKNOWN;
        String receipt=data.placedMapAsset(key(i.id()));
        if(receipt==null)return VehicleDelivery46.Debit.ABSENT;
        return receipt.equals(VehicleDelivery46.fingerprint(i))?VehicleDelivery46.Debit.EXACT:VehicleDelivery46.Debit.CONFLICT;
    }
    static void debit(ServerLevel level,VehicleDelivery46.Intent i,VehicleNativeDelivery47.Hooks hooks)throws IOException {
        if(!level.getServer().isSameThread())throw new IOException("Vehicle payment requires server thread");
        if(state(level,i)!=VehicleDelivery46.Debit.ABSENT)throw new IOException("Vehicle payment already exists or is ambiguous");
        CatanSavedData data=CatanSavedData.get(level.getServer());NationData nation=leader(data,i.actor());
        if(data.nation(i.nation())!=nation)throw new IOException("Vehicle nation/leader changed");
        long count=data.placedMapAssetsView().keySet().stream().filter(k->k.startsWith("vehicle47_paid:")).count();
        if(count>=4096)throw new IOException("Vehicle payment history limit; preserve records for review");
        EconomyCost cost=new EconomyCost(i.price().money(),i.price().resources());
        if(!cost.canAfford(nation))throw new IOException("Cannot afford original vehicle quote");
        cost.charge(nation);data.setPlacedMapAsset(key(i.id()),VehicleDelivery46.fingerprint(i));
        CompoundTag expected=data.save(new CompoundTag());
        hooks.at(VehicleNativeDelivery47.SavePoint.BEFORE_LEDGER_SAVE);
        data.setDirty();level.getServer().overworld().getDataStorage().save();
        if(!disk(level).equals(expected)||!data.save(new CompoundTag()).equals(expected))throw new IOException("Original vehicle debit/receipt native save not verified");
        hooks.at(VehicleNativeDelivery47.SavePoint.AFTER_LEDGER_SAVE);
    }
    static void safe(Path path)throws IOException {
        Path walk=path.toAbsolutePath().getRoot();for(Path part:path.toAbsolutePath()){
            walk=walk.resolve(part);if(Files.isSymbolicLink(walk))throw new IOException("Vehicle native storage symlink refused");
            if(!walk.equals(path.toAbsolutePath())&&Files.exists(walk,LinkOption.NOFOLLOW_LINKS)&&!Files.isDirectory(walk,LinkOption.NOFOLLOW_LINKS))throw new IOException("Vehicle native storage ancestor is not a directory");
        }
    }
}
