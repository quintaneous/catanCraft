package com.quin.catancraft.world;

import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.map.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** One pinned TH1 covered grass cell remains exposed grass at TH2. Its actual
 * dirt before-image is historical source authority, not a general soil policy.
 * Original intents remain immutable; late observations are independently sealed. */
final class BasicHallSoil53 {
    private BasicHallSoil53() {}
    static final String FIELD="sourceSoil53", POLICY_FIELD="soilObservationPolicy";
    static final String POLICY="pinned-th1-exposed-soil-v1", SUFFIX=".soil53-v1.proof";
    private static final int LIMIT=8*1024*1024;
    record Site(int index,int aboveIndex,BlockPos position,BlockState cover,CompoundTag anchor) {}
    private record Parent(Path original,byte[] bytes,CompoundTag intent,BasicConstructionPurchase.Request request,
                          Site site,BlockState before,byte[] companion,byte[] observation) {}

    /** Derive the unique transition from complete immutable assets, then bind its
     * transform. A coordinate alone never grants authority. */
    static Site site(TerritoryDefinition def)throws IOException {
        ProducerService.validateMap(def);
        var a=SchematicAssetRegistry.territoryAnchor("th2",def);ProducerExteriorR2.validateRotation(a);
        var one=BasicConstructionAssets.load("th1");var two=BasicConstructionAssets.load("th2");
        var three=BasicConstructionAssets.load("th3");int found=-1;
        if(!BasicConstructionAssets.BUNDLE_SHA.equals("cbaf8b095958f7de19a58bbca2723f18a1273d908eb5f174d36cd4aa1e9608d1")
                ||one.width()!=75||one.height()!=47||one.length()!=65||two.states().length!=one.states().length
                ||three.states().length!=one.states().length)throw new IOException("Retained Hall soil assets changed");
        for(int y=0;y<one.height()-1;y++)for(int z=0;z<one.length();z++)for(int x=0;x<one.width();x++) {
            int i=(y*one.length()+z)*one.width()+x;
            if(one.at(x,y,z).equals(Blocks.GRASS_BLOCK.defaultBlockState())
                    &&two.at(x,y,z).equals(Blocks.GRASS_BLOCK.defaultBlockState())&&two.at(x,y+1,z).isAir()
                    &&SectorNaturalSupportPolicy.blocksGrass(one.at(x,y+1,z),new BlockPos(x,y+1,z))) {
                if(found!=-1||x!=62||y!=1||z!=24||!one.at(x,y+1,z).equals(Blocks.SPRUCE_LOG.defaultBlockState())
                        ||!three.at(x,y,z).equals(Blocks.STONE_BRICKS.defaultBlockState()))throw new IOException("Retained Hall soil topology changed");
                found=i;
            }
        }
        if(found<0)throw new IOException("Missing retained Hall soil transition");
        int sign=a.rotation()==0?1:-1;
        BlockPos p=new BlockPos(a.x()+sign*(62-one.ax()),a.y()+1-one.ay(),a.z()+sign*(24-one.az()));
        CompoundTag anchor=new CompoundTag();anchor.putString("id",a.id());anchor.putIntArray("position",new int[]{a.x(),a.y(),a.z()});anchor.putInt("rotation",a.rotation());
        return new Site(found,found+one.width()*one.length(),p,Blocks.SPRUCE_LOG.defaultBlockState().rotate(ProducerExteriorR2.rotation(a)),anchor);
    }
    static Path observation(Path original){return original.resolveSibling(original.getFileName()+SUFFIX);}
    private static boolean parentRequest(BasicConstructionPurchase.Request r){return r.kind().equals("hall")&&r.from()==1&&r.to()==2;}
    private static boolean childRequest(BasicConstructionPurchase.Request r){return r.kind().equals("hall")&&r.from()==2&&r.to()==3;}
    static void validateIntentFields(CompoundTag intent,BasicConstructionPurchase.Request r)throws IOException {
        if(intent.contains(POLICY_FIELD)&&(!parentRequest(r)||!intent.contains(POLICY_FIELD,Tag.TAG_STRING)
                ||!POLICY.equals(intent.getString(POLICY_FIELD))))throw new IOException("Invalid Hall soil observation policy");
        if(intent.contains(FIELD)&&(!childRequest(r)||!intent.contains(FIELD,Tag.TAG_COMPOUND)))throw new IOException("Invalid Hall soil source proof");
    }
    private static CompoundTag identity(CompoundTag intent,BasicConstructionPurchase.Request r) {
        CompoundTag n=BasicConstructionPurchase.describe(r);n.putString("world",intent.getString("world"));n.putUUID("transaction",intent.getUUID("id"));return n;
    }
    static BlockState captured(CompoundTag snapshot,int index)throws IOException {
        int[] states=snapshot.getIntArray("states");ListTag tags=snapshot.getList("palette",Tag.TAG_COMPOUND);
        if(states.length!=75*47*65||tags.isEmpty()||tags.size()>4096||index<0||index>=states.length)throw new IOException("Invalid Hall soil before-image");
        List<BlockState> palette=new ArrayList<>();
        for(int i=0;i<tags.size();i++) {
            var tag=tags.getCompound(i);BlockState state=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),tag);
            if(!NbtUtils.writeBlockState(state).equals(tag))throw new IOException("Invalid Hall soil captured state");palette.add(state);
        }
        for(int i:states)if(i<0||i>=palette.size())throw new IOException("Invalid Hall soil state index");
        return palette.get(states[index]);
    }
    private static CompoundTag observationTag(byte[] bytes,CompoundTag intent,BasicConstructionPurchase.Request r,Site site) {
        CompoundTag n=new CompoundTag();n.putInt("schema",1);n.putString("policy",POLICY);n.put("identity",identity(intent,r));
        n.putString("originalSHA256",TownHallRevisions.sha(bytes));n.put("anchor",site.anchor().copy());n.putLong("position",site.position().asLong());
        n.put("soil",NbtUtils.writeBlockState(Blocks.DIRT.defaultBlockState()));n.put("cover",NbtUtils.writeBlockState(site.cover()));return n;
    }
    private static byte[] readObservation(Path original,byte[] bytes,CompoundTag intent,BasicConstructionPurchase.Request r,Site site,BlockState before)throws IOException {
        Path p=observation(original);if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))return null;
        byte[] observed=PaidConstructionRecovery.readBounded(p,LIMIT);
        if(!POLICY.equals(intent.getString(POLICY_FIELD))||!before.equals(Blocks.GRASS_BLOCK.defaultBlockState())
                ||!observationTag(bytes,intent,r,site).equals(ProducerJournal.read(observed)))throw new IOException("Invalid retained Hall soil observation");
        return observed;
    }
    private static Parent parent(Path root,CatanSavedData data,TerritoryDefinition def)throws IOException {
        Path p=root.resolve("data/catancraft_basic_purchase_"+def.id()+"_th2_v1.dat");
        if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))return null;
        byte[] bytes=PaidConstructionRecovery.readBounded(p,LIMIT);CompoundTag intent=BasicConstructionPurchase.read(bytes);
        var r=BasicConstructionPurchase.request(intent.getCompound("request"));var t=data.territory(def.id());
        if(!parentRequest(r)||!r.city().equals(def.id())||!root.toString().equals(intent.getString("world"))
                ||!p.equals(BasicConstructionPurchase.file(root,r))||t==null||!r.owner().equals(t.ownerNationId())||data.nation(r.owner())==null)
            throw new IOException("Foreign retained Hall soil parent");
        // The old actual debit/receipt remains authoritative; never reprice history.
        var cost=DefenseService.historicalCost(intent.getCompound("cost"));
        if(!DefenseTransaction.cost(cost).equals(intent.getCompound("cost")))throw new IOException("Invalid retained Hall soil price");
        CatanSavedData before;
        try{before=CatanSavedData.load(intent.getCompound("ledgerBefore"));}catch(RuntimeException e){throw new IOException("Invalid retained Hall soil ledger",e);}
        var old=before.territory(def.id());var nation=before.nation(r.owner());
        if(old==null||nation==null||old.cityLevel()!=1||!r.owner().equals(old.ownerNationId())||!nation.isLeader(r.actor())
                ||!SchematicAssetRegistry.asset("th1").placementToken().equals(before.placedMapAsset(BasicConstructionPurchase.stateKey(r)))
                ||before.placedMapAsset(PaidConstructionRecovery.receiptKey(intent.getUUID("id")))!=null)
            throw new IOException("Invalid retained Hall soil source ledger");
        byte[] companion=PaidConstructionRecovery.readBounded(PaidConstructionRecovery.companion(p),LIMIT);
        CompoundTag expected=new CompoundTag();expected.putInt("schema",1);expected.put("identity",identity(intent,r));
        expected.putString("originalSHA256",TownHallRevisions.sha(bytes));expected.putString("phase","COMMITTED");
        if(!expected.equals(ProducerJournal.read(companion))||!(intent.getUUID("id")+":paid").equals(data.placedMapAsset(PaidConstructionRecovery.receiptKey(intent.getUUID("id")))))
            throw new IOException("Retained Hall soil parent is not paid and committed");
        Site site=site(def);BlockState soil=captured(intent.getCompound("snapshot"),site.index());
        if(!(soil.equals(Blocks.DIRT.defaultBlockState())||soil.equals(Blocks.GRASS_BLOCK.defaultBlockState()))
                ||!site.cover().equals(captured(intent.getCompound("snapshot"),site.aboveIndex())))throw new IOException("Retained Hall soil lacks its exact source cover");
        byte[] observation=readObservation(p,bytes,intent,r,site,soil);
        return new Parent(p,bytes,intent,r,site,soil,companion,observation);
    }
    private static CompoundTag proof(Parent p) {
        if(p==null||!p.before().equals(Blocks.DIRT.defaultBlockState())&&p.observation()==null)return new CompoundTag();
        CompoundTag n=new CompoundTag();n.putInt("schema",1);n.putString("policy",POLICY);n.put("identity",identity(p.intent(),p.request()));
        n.put("anchor",p.site().anchor().copy());n.putLong("position",p.site().position().asLong());
        n.putString("parentOriginalSHA256",TownHallRevisions.sha(p.bytes()));n.putString("parentCompanionSHA256",TownHallRevisions.sha(p.companion()));
        n.putString("observationKind",p.observation()==null?"sealed-before-image":"sealed-before-write");
        if(p.observation()!=null)n.putString("observationSHA256",TownHallRevisions.sha(p.observation()));return n;
    }
    /** Read-only checkpoint authority applies only while TH2 is current. */
    static Set<BlockPos> completedDirt(Path root,CatanSavedData data,TerritoryDefinition def)throws IOException {
        var t=data.territory(def.id());if(t==null||t.cityLevel()!=2)return Set.of();
        if(!SchematicAssetRegistry.asset("th2").placementToken().equals(data.placedMapAsset("territory:"+def.id()+":town_hall")))
            throw new IOException("Retained Hall soil current revision changed");
        Parent p=parent(root,data,def);return proof(p).isEmpty()?Set.of():Set.of(p.site().position());
    }
    static void validateCompletedObservation(Path root,CatanSavedData data,TerritoryDefinition def,Path sidecar)throws IOException {
        Parent p=parent(root,data,def);
        var t=data.territory(def.id());
        if(p==null||t==null||t.cityLevel()<2||p.observation()==null||!observation(p.original()).equals(sidecar))throw new IOException("Foreign completed Hall soil observation");
    }
    static CompoundTag select(Path root,CatanSavedData data,TerritoryDefinition def,BasicConstructionPurchase.Request r)throws IOException {
        if(!childRequest(r))return new CompoundTag();
        if(data.territory(r.city()).cityLevel()!=2)throw new IOException("Retained Hall soil source tier changed");
        Parent p=parent(root,data,def);return proof(p);
    }
    static Set<BlockPos> positions(CompoundTag proof){return proof.isEmpty()?Set.of():Set.of(BlockPos.of(proof.getLong("position")));}
    static Set<BlockPos> read(Path root,CatanSavedData data,TerritoryDefinition def,CompoundTag child,BasicConstructionPurchase.Request r)throws IOException {
        if(!child.contains(FIELD))return Set.of();
        validateIntentFields(child,r);Parent p=parent(root,data,def);CompoundTag expected=proof(p);
        if(expected.isEmpty()||!expected.equals(child.getCompound(FIELD))||!r.owner().equals(p.request().owner())
                ||!r.city().equals(p.request().city())||!root.toString().equals(child.getString("world"))
                ||!captured(child.getCompound("snapshot"),p.site().index()).equals(Blocks.DIRT.defaultBlockState()))
            throw new IOException("Retained Hall soil child proof changed");
        return Set.of(p.site().position());
    }
    /** Called only after full recovery preflight, before PREPARED or any write.
     * Never infer a late observation after the original log has gone. */
    static void beforeWrites(ServerLevel level,Path original,byte[] bytes,CompoundTag intent,
                             BasicConstructionPurchase.Request r,TerritoryDefinition def)throws IOException {
        if(!intent.contains(POLICY_FIELD))return;
        validateIntentFields(intent,r);Site site=site(def);
        if(!Arrays.equals(bytes,PaidConstructionRecovery.readBounded(original,LIMIT)))throw new IOException("Hall soil original changed before observation");
        BlockState before=captured(intent.getCompound("snapshot"),site.index());
        if(!site.cover().equals(captured(intent.getCompound("snapshot"),site.aboveIndex())))throw new IOException("Hall soil source cover changed");
        byte[] existing=readObservation(original,bytes,intent,r,site,before);
        if(before.equals(Blocks.DIRT.defaultBlockState())||existing!=null)return;
        if(!before.equals(Blocks.GRASS_BLOCK.defaultBlockState()))throw new IOException("Invalid Hall soil source observation");
        if(level.getBlockState(site.position()).equals(Blocks.DIRT.defaultBlockState())) {
            if(!level.getBlockState(site.position().above()).equals(site.cover()))throw new IOException("Hall soil decay lacks an observed source log");
            ProducerJournal.write(observation(original),observationTag(bytes,intent,r,site),false);
        }
    }
}
