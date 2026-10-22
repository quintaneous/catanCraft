package com.quin.catancraft.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import java.io.IOException;
import java.util.*;

/** Exact versioned read-only vanilla grass decay, never a terrain replacement rule.
 * NONE is the original strict journal contract and serializes to no additional spec tag.
 */
public record SectorNaturalSupportPolicy(String version,Set<BlockPos> grassSupports,Map<BlockPos,BlockState> fixedCoverGrass) {
    public static final String VERSION="covered-grass-to-dirt-v1";
    public static final String FIXED_VERSION="covered-grass-to-dirt-v2";
    public static final String SHAPED_VERSION="covered-grass-to-dirt-v3";
    public SectorNaturalSupportPolicy(String version,Set<BlockPos> grassSupports){this(version,grassSupports,Map.of());}
    public static final SectorNaturalSupportPolicy NONE=new SectorNaturalSupportPolicy("",Set.of());
    public SectorNaturalSupportPolicy {
        Objects.requireNonNull(version);Objects.requireNonNull(grassSupports);Objects.requireNonNull(fixedCoverGrass);
        Map<BlockPos,BlockState> fixed=new HashMap<>();fixedCoverGrass.forEach((p,state)->fixed.put(Objects.requireNonNull(p).immutable(),Objects.requireNonNull(state)));fixedCoverGrass=Collections.unmodifiableMap(fixed);
        Set<BlockPos> copy=new HashSet<>();for(BlockPos p:grassSupports)copy.add(Objects.requireNonNull(p).immutable());grassSupports=Collections.unmodifiableSet(copy);
        if((version.isEmpty()!=grassSupports.isEmpty())||(!version.isEmpty()&&!VERSION.equals(version)&&!FIXED_VERSION.equals(version)&&!SHAPED_VERSION.equals(version)))throw new IllegalArgumentException("Unknown/empty natural support policy");
        if((!FIXED_VERSION.equals(version)&&!SHAPED_VERSION.equals(version)&&!fixedCoverGrass.isEmpty())||!grassSupports.containsAll(fixedCoverGrass.keySet()))throw new IllegalArgumentException("Invalid fixed-cover policy");
    }
    public static SectorNaturalSupportPolicy coveredGrass(Set<BlockPos> positions){return positions.isEmpty()?NONE:new SectorNaturalSupportPolicy(VERSION,positions);}
    /** Explicit unchanged supplied read covers; keys are automatically part of the support whitelist. */
    public static SectorNaturalSupportPolicy coveredGrass(Set<BlockPos> owned,Map<BlockPos,BlockState> fixed){Set<BlockPos> all=new HashSet<>(owned);all.addAll(fixed.keySet());return all.isEmpty()?NONE:new SectorNaturalSupportPolicy(FIXED_VERSION,all,fixed);}
    public static SectorNaturalSupportPolicy coveredGrassShaped(Set<BlockPos> owned,Map<BlockPos,BlockState> fixed){Set<BlockPos> all=new HashSet<>(owned);all.addAll(fixed.keySet());return all.isEmpty()?NONE:new SectorNaturalSupportPolicy(SHAPED_VERSION,all,fixed);}
    private boolean covers(BlockState state,BlockPos pos){return SHAPED_VERSION.equals(version)?blocksGrass(state,pos):opaque(state,pos);}
    public boolean enabled(){return !version.isEmpty();}
    public CompoundTag describe(){
        CompoundTag tag=new CompoundTag();if(enabled()){tag.putString("version",version);tag.putLongArray("grassSupports",grassSupports.stream().mapToLong(BlockPos::asLong).sorted().toArray());if(FIXED_VERSION.equals(version)||SHAPED_VERSION.equals(version)){ListTag rows=new ListTag();fixedCoverGrass.keySet().stream().sorted(Comparator.comparingLong(BlockPos::asLong)).forEach(p->{CompoundTag row=new CompoundTag();row.putLong("pos",p.asLong());row.put("cover",NbtUtils.writeBlockState(fixedCoverGrass.get(p)));rows.add(row);});tag.put("fixedCoverGrass",rows);}}return tag;
    }
    public static SectorNaturalSupportPolicy read(CompoundTag tag)throws IOException {
        if(tag.isEmpty())return NONE;
        boolean v2=FIXED_VERSION.equals(tag.getString("version"))||SHAPED_VERSION.equals(tag.getString("version"));
        if(!tag.getAllKeys().equals(v2?Set.of("version","grassSupports","fixedCoverGrass"):Set.of("version","grassSupports"))||!tag.contains("version",Tag.TAG_STRING)||!tag.contains("grassSupports",Tag.TAG_LONG_ARRAY)||(!v2&&!VERSION.equals(tag.getString("version"))))throw new IOException("Unknown natural support journal contract");
        long[] positions=tag.getLongArray("grassSupports");if(positions.length==0||positions.length>500000)throw new IOException("Invalid natural support whitelist length");
        Set<BlockPos> found=new HashSet<>();for(long p:positions)if(!found.add(BlockPos.of(p)))throw new IOException("Duplicate natural support position");
        Map<BlockPos,BlockState> fixed=new HashMap<>();
        if(v2){if(!tag.contains("fixedCoverGrass",Tag.TAG_LIST))throw new IOException("Missing fixed cover list");ListTag rows=tag.getList("fixedCoverGrass",Tag.TAG_COMPOUND);if(rows.size()!=((ListTag)tag.get("fixedCoverGrass")).size()||rows.size()>found.size())throw new IOException("Invalid fixed cover list");
            for(Tag value:rows){CompoundTag row=(CompoundTag)value;if(!row.getAllKeys().equals(Set.of("pos","cover"))||!row.contains("pos",Tag.TAG_LONG)||!row.contains("cover",Tag.TAG_COMPOUND))throw new IOException("Malformed fixed cover");BlockPos p=BlockPos.of(row.getLong("pos"));BlockState state=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),row.getCompound("cover"));if(!NbtUtils.writeBlockState(state).equals(row.getCompound("cover"))||!found.contains(p)||fixed.put(p,state)!=null)throw new IOException("Unknown/duplicate fixed cover");}}
        SectorNaturalSupportPolicy policy=v2?new SectorNaturalSupportPolicy(tag.getString("version"),found,fixed):coveredGrass(found);if(!policy.describe().equals(tag))throw new IOException("Noncanonical natural support whitelist");return policy;
    }
    static boolean opaque(BlockState state,BlockPos p){return state!=null&&!state.hasBlockEntity()&&state.getFluidState().isEmpty()&&state.canOcclude()&&state.isSolidRender(EmptyBlockGetter.INSTANCE,p);}
    /** Same occlusion calculation used by vanilla grass survival; no future-cover permission. */
    static boolean blocksGrass(BlockState state,BlockPos above){
        return state!=null&&!state.hasBlockEntity()&&state.getFluidState().isEmpty()
            &&net.minecraft.world.level.lighting.LightEngine.getLightBlockInto(EmptyBlockGetter.INSTANCE,
                Blocks.GRASS_BLOCK.defaultBlockState(),above.below(),state,above,net.minecraft.core.Direction.UP,
                state.getLightBlock(EmptyBlockGetter.INSTANCE,above))>=15;
    }
    static Map<BlockPos,BlockState> geometry(SectorTransaction.Plan plan,boolean target){
        Map<BlockPos,BlockState> map=new HashMap<>();for(var c:plan.cells())map.put(c.pos(),target?c.after():c.before());for(var r:plan.reads())map.put(r.pos(),r.state());return map;
    }
    public void validate(SectorTransaction.Plan plan)throws IOException {
        if(!enabled())return;
        Map<BlockPos,BlockState> source=geometry(plan,false),target=geometry(plan,true),reads=new HashMap<>();for(var r:plan.reads())reads.put(r.pos(),r.state());
        for(BlockPos p:grassSupports){
            if(!Blocks.GRASS_BLOCK.defaultBlockState().equals(reads.get(p)))throw new IOException("Natural support must be an exact original grass read: "+p);
            BlockState fixed=fixedCoverGrass.get(p);if(fixed!=null&&(!covers(fixed,p.above())||!fixed.equals(reads.get(p.above()))))throw new IOException("Fixed natural cover must be an exact unchanged opaque protected read: "+p);
            if(!covers(target.get(p.above()),p.above())&&!covers(source.get(p.above()),p.above()))throw new IOException("Natural grass support has no approved opaque cover: "+p);
        }
    }
    /** Frozen previous paid proof is supplied by the adapter after ProducerJournal.read.
     * Required fields: schema,id,nation,phase=COMMITTED,identity,spec. The receipt must be
     * id+:paid; source target tier/revision and stable world/owner identity must match.
     */
    static Set<BlockPos> inherited(SectorTransaction.Plan plan,CompoundTag proof,String receipt,UUID nation,CompoundTag identity)throws IOException {
        Map<BlockPos,BlockState> source=geometry(plan,false);
        CompoundTag group=null;
        if(identity.contains("cityGroup")){
            if(!identity.contains("cityGroup",Tag.TAG_COMPOUND))throw new IOException("Invalid city group natural support identity");
            group=groupHeader(identity.getCompound("cityGroup"));
            if(!group.getString("parentFile").isEmpty()&&proof.isEmpty())throw new IOException("Grouped natural support requires its exact committed city parent");
        }
        if(proof.isEmpty()){
            for(BlockPos p:plan.naturalSupport().grassSupports())if(!plan.naturalSupport().fixedCoverGrass().containsKey(p)&&plan.naturalSupport().covers(source.get(p.above()),p.above()))throw new IOException("Covered lower-tier grass requires its paid natural-support proof");
            return Set.of();
        }
        String paidReceipt=group==null?receipt:group.getString("parentCityReceipt");
        if(!proof.contains("schema",Tag.TAG_INT)||proof.getInt("schema")!=1||!proof.hasUUID("id")||!proof.hasUUID("nation")||!proof.getUUID("nation").equals(nation)||!proof.getString("phase").equals("COMMITTED")||!paidReceipt.equals(proof.getUUID("id")+":paid"))throw new IOException("Natural support source is not the previous paid operation");
        CompoundTag previousIdentity=proof.getCompound("identity");
        for(String key:List.of("world","worldId","dimension","city","sector","owner","approval"))if((group==null||!key.equals("sector"))&&!Objects.equals(identity.get(key),previousIdentity.get(key)))throw new IOException("Natural support proof world/owner differs: "+key);
        SectorTransaction.Plan previous=SectorTransaction.fromDescription(proof.getCompound("spec"));
        if(group==null){if(previous.targetLevel()!=plan.sourceLevel()||!previous.targetRevision().equals(plan.sourceRevision()))throw new IOException("Natural support paid source level/revision differs");}
        else validateGroupParent(plan,source,previous,proof,identity,previousIdentity,group);
        Map<BlockPos,BlockState> oldTarget=geometry(previous,true);Set<BlockPos> allowed=new HashSet<>();
        for(BlockPos p:plan.naturalSupport().grassSupports())if(!plan.naturalSupport().fixedCoverGrass().containsKey(p)&&plan.naturalSupport().covers(source.get(p.above()),p.above())){
            if(!previous.naturalSupport().grassSupports().contains(p)||!Objects.equals(source.get(p.above()),oldTarget.get(p.above())))throw new IOException("Natural support source cover was not owned under the historical policy: "+p);
            allowed.add(p);
        }
        return Set.copyOf(allowed);
    }
    /** Narrow approved City A group lineage, never a generic cross-sector proof exception. */
    private static CompoundTag groupHeader(CompoundTag group)throws IOException {
        Set<String> expected=new HashSet<>(Set.of("schema","parentFile","parentCityReceipt","archiveSHA256","resourceSHA256","translation","beforeStates","beforeOpenCorners","afterOpenCorners"));
        if(group.contains("parentId"))expected.add("parentId");
        if(!group.getAllKeys().equals(expected)||!group.contains("schema",Tag.TAG_INT)||group.getInt("schema")!=1||!group.contains("translation",Tag.TAG_LONG)
            ||!group.contains("archiveSHA256",Tag.TAG_STRING)||!SectorCityAP3.ARCHIVE_SHA.equals(group.getString("archiveSHA256"))
            ||!group.contains("resourceSHA256",Tag.TAG_STRING)||!SectorCityAP3.RESOURCE_SHA.equals(group.getString("resourceSHA256"))
            ||!group.contains("parentFile",Tag.TAG_STRING)||!group.contains("parentCityReceipt",Tag.TAG_STRING)||!group.contains("beforeStates",Tag.TAG_COMPOUND))throw new IOException("Unknown grouped support schema/assets");
        boolean parent=!group.getString("parentFile").isEmpty();
        if(parent){if(!group.hasUUID("parentId")||!group.getString("parentFile").matches("[A-Za-z0-9_-]+\\.dat")||!group.getString("parentCityReceipt").equals(group.getUUID("parentId")+":paid"))throw new IOException("Invalid grouped support parent pointer");}
        else if(group.contains("parentId")||!group.getString("parentCityReceipt").isEmpty())throw new IOException("Fresh group has an unexplained city parent receipt");
        CompoundTag states=group.getCompound("beforeStates");if(!states.getAllKeys().equals(Set.of("NORTH","EAST","SOUTH","WEST")))throw new IOException("Grouped support requires all four prior sector states");
        for(String side:states.getAllKeys()){
            if(!states.contains(side,Tag.TAG_COMPOUND))throw new IOException("Malformed grouped sector state");CompoundTag state=states.getCompound(side);
            if(!state.getAllKeys().equals(Set.of("level","receipt","revision"))||!state.contains("level",Tag.TAG_INT)||state.getInt("level")<0||state.getInt("level")>2||!state.contains("receipt",Tag.TAG_STRING)||!state.contains("revision",Tag.TAG_STRING))throw new IOException("Unknown grouped sector progress");
            if(state.getInt("level")==0){if(!state.getString("receipt").isEmpty()||!state.getString("revision").isEmpty())throw new IOException("Fresh grouped sector carries paid state");}
            else if(state.getString("receipt").isBlank()||state.getString("revision").isBlank()||!parent)throw new IOException("Grouped purchased state lacks parent proof");
        }
        cornerSet(group,"beforeOpenCorners");cornerSet(group,"afterOpenCorners");return group;
    }
    private static Set<String> cornerSet(CompoundTag group,String key)throws IOException {
        if(!group.contains(key,Tag.TAG_LIST))throw new IOException("Missing grouped corner state");ListTag list=group.getList(key,Tag.TAG_STRING);if(list.size()!=((ListTag)group.get(key)).size())throw new IOException("Unknown grouped corner list type");
        Set<String> found=new HashSet<>();for(Tag t:list)if(t.getAsString().isBlank()||!found.add(t.getAsString()))throw new IOException("Unknown/duplicate grouped corner state");return found;
    }
    private static void validateGroupParent(SectorTransaction.Plan plan,Map<BlockPos,BlockState> source,SectorTransaction.Plan previous,CompoundTag proof,CompoundTag identity,CompoundTag previousIdentity,CompoundTag group)throws IOException {
        if(!identity.getString("city").equals("a")||!group.hasUUID("parentId")||!group.getUUID("parentId").equals(proof.getUUID("id"))||!group.getString("parentFile").equals(previousIdentity.getString("operationFile"))||!previousIdentity.contains("cityGroup",Tag.TAG_COMPOUND))throw new IOException("Grouped support does not identify the exact City A parent history");
        CompoundTag oldGroup=groupHeader(previousIdentity.getCompound("cityGroup"));
        for(String pin:List.of("archiveSHA256","resourceSHA256","translation"))if(!Objects.equals(group.get(pin),oldGroup.get(pin)))throw new IOException("Grouped natural support asset/site changed");
        if(!identity.getString("sector").equalsIgnoreCase(plan.sector())||!previousIdentity.getString("sector").equalsIgnoreCase(previous.sector())||!geometry(previous,true).equals(source))throw new IOException("Grouped natural support requires exact entire-city parent target/source geometry");
        if(!cornerSet(group,"beforeOpenCorners").equals(cornerSet(oldGroup,"afterOpenCorners")))throw new IOException("Grouped natural support corner lineage differs");
        CompoundTag expected=oldGroup.getCompound("beforeStates").copy(),updated=new CompoundTag();updated.putInt("level",previous.targetLevel());updated.putString("receipt",proof.getUUID("id")+":paid");updated.putString("revision",previous.targetRevision());
        expected.put(previous.sector().toUpperCase(Locale.ROOT),updated);
        if(!expected.equals(group.getCompound("beforeStates")))throw new IOException("Grouped natural support paid sector-state lineage differs");
    }
    static CompoundTag freeze(CompoundTag journal){CompoundTag result=new CompoundTag();for(String key:List.of("schema","id","nation","phase","identity","spec"))if(journal.contains(key))result.put(key,journal.get(key).copy());return result;}
    static final class Guard {
        final SectorNaturalSupportPolicy policy;final Set<BlockPos> inherited;final Set<BlockPos> exposed=new HashSet<>();
        final ServerLevel level;final Map<BlockPos,BlockState> target;boolean pending;
        Guard(ServerLevel level,SectorTransaction.Plan plan,Set<BlockPos> inherited,boolean pending,CompoundTag journal)throws IOException {
            this.level=level;this.policy=plan.naturalSupport();this.target=policy.enabled()?geometry(plan,true):Map.of();this.inherited=inherited;this.pending=pending;
            if(journal!=null&&journal.contains("naturalSupportExposure")){
                if(!policy.enabled()||!journal.contains("naturalSupportExposure",Tag.TAG_LONG_ARRAY))throw new IOException("Unknown natural support exposure journal field");
                long[] positions=journal.getLongArray("naturalSupportExposure");if(positions.length>policy.grassSupports().size())throw new IOException("Oversized natural support exposure proof");
                for(long position:positions){BlockPos p=BlockPos.of(position);if(!policy.grassSupports().contains(p)||!exposed.add(p))throw new IOException("Unapproved/duplicate natural support exposure");}
                if(!Arrays.equals(positions,exposed.stream().mapToLong(BlockPos::asLong).sorted().toArray())||(!pending&&!exposed.isEmpty()))throw new IOException("Noncanonical or premature natural support exposure proof");
            }
        }
        boolean matches(BlockPos pos,BlockState expected,BlockState actual){
            BlockState fixed=policy.fixedCoverGrass().get(pos);
            if(fixed!=null){
                if(!fixed.equals(level.getBlockState(pos.above()))||level.getBlockEntity(pos.above())!=null)return false;
                return (expected.equals(Blocks.GRASS_BLOCK.defaultBlockState())||expected.equals(Blocks.DIRT.defaultBlockState()))
                    &&(actual.equals(Blocks.GRASS_BLOCK.defaultBlockState())||actual.equals(Blocks.DIRT.defaultBlockState()));
            }
            if((expected.is(Blocks.GRASS_BLOCK)||expected.is(Blocks.DIRT))&&policy.grassSupports().contains(pos)&&pending&&!exposed.contains(pos)){
                BlockState cover=target.get(pos.above());
                // Observe actual approved cover, never grant exposure from a future target alone.
                if(policy.covers(cover,pos.above())&&cover.equals(level.getBlockState(pos.above())))exposed.add(pos);
            }
            if(expected.equals(actual))return true;
            if(!policy.grassSupports().contains(pos)||(!exposed.contains(pos)&&!inherited.contains(pos)))return false;
            return (expected.equals(Blocks.GRASS_BLOCK.defaultBlockState())||expected.equals(Blocks.DIRT.defaultBlockState()))
                &&(actual.equals(Blocks.GRASS_BLOCK.defaultBlockState())||actual.equals(Blocks.DIRT.defaultBlockState()));
        }
        void persist(CompoundTag journal){if(policy.enabled())journal.putLongArray("naturalSupportExposure",exposed.stream().mapToLong(BlockPos::asLong).sorted().toArray());}
    }
}
