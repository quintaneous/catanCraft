package com.quin.catancraft.world;

import com.quin.catancraft.data.MonumentType;
import com.quin.catancraft.data.ResourceType;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.util.*;

/** Strict weighted reward model. Production currently selects the explicit
 * one-entry compatibility table; numeric randomized profiles are not enabled. */
public final class RewardTable41 {
    private static final long MAX_AMOUNT=1_000_000_000_000L;
    public enum Kind { GUN, AMMO, VEHICLE }
    /** Pending reference only. This does not construct an item or spawn an entity. */
    public record Entitlement(Kind kind,String objectId,int count,String revision) {
        public Entitlement {
            Objects.requireNonNull(kind);label(revision);
            if(objectId==null||objectId.length()>160||!objectId.contains(":")||ResourceLocation.tryParse(objectId)==null
                    ||!ResourceLocation.tryParse(objectId).toString().equals(objectId)
                    ||count<1||count>(kind==Kind.AMMO?4096:1))throw new IllegalArgumentException("Invalid reward entitlement");
        }
        CompoundTag save(){CompoundTag n=new CompoundTag();n.putString("kind",kind.name());n.putString("id",objectId);n.putInt("count",count);n.putString("revision",revision);return n;}
    }
    public record Bundle(long money,Map<ResourceType,Long> resources,List<Entitlement> entitlements) {
        public Bundle {
            if(money<0||money>MAX_AMOUNT||resources==null||entitlements==null||resources.size()>ResourceType.values().length||entitlements.size()>16)
                throw new IllegalArgumentException("Invalid reward bundle bounds");
            for(var e:resources.entrySet())if(e.getKey()==null||e.getValue()==null||e.getValue()<=0||e.getValue()>MAX_AMOUNT)throw new IllegalArgumentException("Invalid reward resource");
            if(entitlements.stream().anyMatch(Objects::isNull)||(money==0&&resources.isEmpty()&&entitlements.isEmpty()))throw new IllegalArgumentException("Empty/invalid reward bundle");
            resources=Map.copyOf(resources);entitlements=List.copyOf(entitlements);
        }
        public CompoundTag save(){
            CompoundTag n=new CompoundTag(),r=new CompoundTag();n.putLong("money",money);
            for(ResourceType type:ResourceType.values())if(resources.containsKey(type))r.putLong(type.name(),resources.get(type));n.put("resources",r);
            ListTag items=new ListTag();entitlements.forEach(e->items.add(e.save()));n.put("entitlements",items);return n;
        }
        public static Bundle load(CompoundTag n)throws IOException {
            try {
                keys(n,Set.of("money","resources","entitlements"));type(n,"money",Tag.TAG_LONG);type(n,"resources",Tag.TAG_COMPOUND);type(n,"entitlements",Tag.TAG_LIST);
                CompoundTag r=n.getCompound("resources");Map<ResourceType,Long> resources=new EnumMap<>(ResourceType.class);
                for(String key:r.getAllKeys()){type(r,key,Tag.TAG_LONG);resources.put(ResourceType.valueOf(key),r.getLong(key));}
                ListTag list=(ListTag)n.get("entitlements");if(!list.isEmpty()&&list.getElementType()!=Tag.TAG_COMPOUND)throw new IOException("Malformed entitlement list");
                if(list.size()>16)throw new IOException("Too many reward entitlements");List<Entitlement> entries=new ArrayList<>();
                for(int i=0;i<list.size();i++){CompoundTag e=list.getCompound(i);keys(e,Set.of("kind","id","count","revision"));type(e,"kind",Tag.TAG_STRING);type(e,"id",Tag.TAG_STRING);type(e,"count",Tag.TAG_INT);type(e,"revision",Tag.TAG_STRING);
                    entries.add(new Entitlement(Kind.valueOf(e.getString("kind")),e.getString("id"),e.getInt("count"),e.getString("revision")));}
                return new Bundle(n.getLong("money"),resources,entries);
            }catch(IllegalArgumentException|NullPointerException failure){throw new IOException("Malformed reward bundle",failure);}
        }
        public String describe(){
            List<String> parts=new ArrayList<>();if(money>0)parts.add("+$"+money+" treasury");
            for(ResourceType type:ResourceType.values())if(resources.containsKey(type))parts.add("+"+resources.get(type)+" "+type.id());
            if(!entitlements.isEmpty())parts.add(entitlements.size()+" pending equipment entitlement(s); delivery unavailable");return String.join(", ",parts);
        }
    }
    public record Entry(String id,int weight,Bundle reward) {
        public Entry {label(id);Objects.requireNonNull(reward);if(weight<1||weight>1_000_000)throw new IllegalArgumentException("Invalid reward weight");}
    }
    public record Roll(String entryId,Bundle reward) {}
    private final String revision;private final List<Entry> entries;private final long total;
    public RewardTable41(String revision,List<Entry> entries){
        label(revision);if(entries==null||entries.isEmpty()||entries.size()>64)throw new IllegalArgumentException("Invalid reward table size");
        Set<String> ids=new HashSet<>();long sum=0;for(Entry e:entries){if(e==null||!ids.add(e.id()))throw new IllegalArgumentException("Duplicate reward entry");sum=Math.addExact(sum,e.weight());}
        this.revision=revision;this.entries=List.copyOf(entries);this.total=sum;
    }
    public String revision(){return revision;}public List<Entry> entries(){return entries;}public long totalWeight(){return total;}
    public Roll roll(long seed){long ticket=new SplittableRandom(seed).nextLong(total);for(Entry entry:entries){if(ticket<entry.weight())return new Roll(entry.id(),entry.reward());ticket-=entry.weight();}throw new IllegalStateException("Reward weight accounting mismatch");}
    public CompoundTag save(){CompoundTag n=new CompoundTag();n.putInt("schema",1);n.putString("revision",revision);ListTag list=new ListTag();for(Entry e:entries){CompoundTag v=new CompoundTag();v.putString("id",e.id());v.putInt("weight",e.weight());v.put("reward",e.reward().save());list.add(v);}n.put("entries",list);return n;}
    public static RewardTable41 load(CompoundTag n)throws IOException {
        try{keys(n,Set.of("schema","revision","entries"));type(n,"schema",Tag.TAG_INT);type(n,"revision",Tag.TAG_STRING);type(n,"entries",Tag.TAG_LIST);
            if(n.getInt("schema")!=1)throw new IOException("Unknown reward table schema");ListTag list=(ListTag)n.get("entries");if(list.isEmpty()||list.getElementType()!=Tag.TAG_COMPOUND||list.size()>64)throw new IOException("Malformed reward table entries");
            List<Entry> entries=new ArrayList<>();for(int i=0;i<list.size();i++){CompoundTag e=list.getCompound(i);keys(e,Set.of("id","weight","reward"));type(e,"id",Tag.TAG_STRING);type(e,"weight",Tag.TAG_INT);type(e,"reward",Tag.TAG_COMPOUND);entries.add(new Entry(e.getString("id"),e.getInt("weight"),Bundle.load(e.getCompound("reward"))));}
            return new RewardTable41(n.getString("revision"),entries);
        }catch(IllegalArgumentException|NullPointerException failure){throw new IOException("Malformed reward table",failure);}
    }
    public static RewardTable41 compatibility(MonumentType type){return new RewardTable41("monument41_legacy_"+type.id()+"_v1",List.of(new Entry("legacy_national_supplies",1,new Bundle(type.moneyReward(),type.resourceRewards(),List.of()))));}
    static void keys(CompoundTag n,Set<String> keys)throws IOException{if(!n.getAllKeys().equals(keys))throw new IOException("Unknown/missing reward record fields");}
    static void type(CompoundTag n,String key,int type)throws IOException{if(!n.contains(key,type))throw new IOException("Wrong reward field type: "+key);}
    static void label(String s){if(s==null||s.isBlank()||s.length()>160)throw new IllegalArgumentException("Explicit bounded reward identity required");}
}
