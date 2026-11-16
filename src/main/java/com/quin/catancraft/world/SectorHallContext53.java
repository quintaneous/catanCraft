package com.quin.catancraft.world;

import com.quin.catancraft.data.*;
import com.quin.catancraft.map.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Current paid development may replace eleven unowned wall-context reads. It
 * never changes a wall plan, write, rollback image, price or historical journal. */
final class SectorHallContext53 {
    static final String POLICY="paid-hall-approach-read53-v1";
    private static final int LIMIT=8*1024*1024;
    private static final String FALLBACK_SHA="ee6927f440328e1e07c8581ab594ba7906f5d2dd11cdec4f2f9618430f483281";
    private static final Map<String,Map<BlockPos,BlockState>> MASKS=new HashMap<>();
    private SectorHallContext53(){}
    private record Bound(Path path,byte[] bytes){}
    static final class Context {
        private final Path root;private final CatanSavedData data;private final TerritoryDefinition def;
        private final UUID owner;private final int tier;private final String token;
        private final Map<BlockPos,BlockState> reads;private final List<Bound> files;
        private final Map<String,String> receipts;private final Set<Path> absent;
        private Context(Path root,CatanSavedData data,TerritoryDefinition def,Map<BlockPos,BlockState> reads,List<Bound> files,Map<String,String> receipts,Set<Path> absent){
            this.root=root;this.data=data;this.def=def;var t=data.territory(def.id());owner=t.ownerNationId();tier=t.cityLevel();token=data.placedMapAsset(key(def.id()));
            this.reads=Collections.unmodifiableMap(new HashMap<>(reads));this.files=List.copyOf(files);this.receipts=Collections.unmodifiableMap(new HashMap<>(receipts));this.absent=Set.copyOf(absent);
        }
        Map<BlockPos,BlockState> reads(){return reads;}
        void verifyUnchanged()throws IOException {
            SectorSites53.canonical(def);if(!def.equals(MapDefinitionManager.territory(def.id())))throw new IOException("Hall context map changed");var t=data.territory(def.id());
            if(!root.equals(root.toRealPath())||Files.isSymbolicLink(root.resolve("data"))||t==null||!owner.equals(t.ownerNationId())||data.nation(owner)==null||t.cityLevel()!=tier||!Objects.equals(token,data.placedMapAsset(key(def.id()))))throw new IOException("Paid Hall read context ownership/tier changed");
            for(var e:receipts.entrySet())if(!Objects.equals(e.getValue(),data.placedMapAsset(e.getKey())))throw new IOException("Paid Hall read context receipt changed");
            for(var f:files)if(!Arrays.equals(f.bytes(),PaidConstructionRecovery.readBounded(f.path(),LIMIT)))throw new IOException("Paid Hall read history changed");
            for(Path p:absent)if(Files.exists(p,LinkOption.NOFOLLOW_LINKS))throw new IOException("Hall history appeared during wall operation");
        }
    }
    private static String key(String city){return "territory:"+city+":town_hall";}
    private static Path original(Path root,String city,int tier){return root.resolve("data/catancraft_th_prestige_"+city+"_"+tier+"_v1.dat");}
    private static Path repair(Path root,String city,int tier){return root.resolve("data/catancraft_th_repair_"+city+"_"+tier+"_r1_r2.dat");}
    private static CompoundTag identity(CompoundTag j){CompoundTag n=new CompoundTag();n.putString("kind","prestige");n.putString("world",j.getString("worldRoot"));n.putString("city",j.getString("city"));n.putUUID("owner",UUID.fromString(j.getString("ownerNation")));n.putUUID("transaction",j.getUUID("transaction"));n.putString("source",j.getString("sourceToken"));n.putString("target",j.getString("targetToken"));return n;}
    private static void header(Path root,String city,int tier,CompoundTag j)throws IOException {
        try{UUID.fromString(j.getString("ownerNation"));}catch(RuntimeException e){throw new IOException("Invalid Hall history owner",e);}
        if(!city.equals(j.getString("city"))||!root.toString().equals(j.getString("worldRoot"))||j.getInt("toLevel")!=tier||j.getInt("fromLevel")!=tier-1)throw new IOException("Foreign Hall read history");
    }
    private static CompoundTag companion(Path p,byte[] bytes,CompoundTag j)throws IOException {
        Path c=PaidConstructionRecovery.companion(p);if(!Files.exists(c,LinkOption.NOFOLLOW_LINKS))return null;
        CompoundTag n=ProducerJournal.read(PaidConstructionRecovery.readBounded(c,LIMIT));
        CompoundTag expected=new CompoundTag();expected.putInt("schema",1);expected.put("identity",identity(j));expected.putString("originalSHA256",TownHallRevisions.sha(bytes));expected.putString("phase",n.getString("phase"));
        if(!Set.of("PREPARED","PHYSICAL_COMPLETE","COMMITTED").contains(n.getString("phase"))||!expected.equals(n))throw new IOException("Foreign Hall recovery companion");return n;
    }
    static boolean pending(Path root,String city)throws IOException {
        if(!city.matches("[a-p]"))throw new IOException("Unknown Hall context city");boolean pending=false;
        for(int tier=4;tier<=5;tier++){
            Path rp=repair(root,city,tier);if(Files.exists(rp,LinkOption.NOFOLLOW_LINKS)){
                var r=TownHallRepairTransaction.read(rp);if(r==null)throw new IOException("Missing Hall repair record");var id=r.getCompound("identity");
                try{UUID.fromString(id.getString("owner"));}catch(RuntimeException e){throw new IOException("Invalid repair owner",e);}
                if(r.getInt("schema")!=1||!r.hasUUID("transaction"))throw new IOException("Invalid Hall repair schema");
                if(!root.toString().equals(id.getString("world"))||!city.equals(id.getString("city"))||id.getInt("tier")!=tier)throw new IOException("Foreign Hall repair history");
                var phase=TownHallRepairTransaction.phase(r);if(phase!=TownHallRepairTransaction.Phase.COMMITTED&&phase!=TownHallRepairTransaction.Phase.ROLLED_BACK)pending=true;
            }
            Path p=original(root,city,tier);if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS)){if(Files.exists(PaidConstructionRecovery.companion(p),LinkOption.NOFOLLOW_LINKS))throw new IOException("Orphan Hall recovery companion");continue;}
            byte[] bytes=PaidConstructionRecovery.readBounded(p,LIMIT);CompoundTag j=PrestigePurchaseRecovery.read(bytes);header(root,city,tier,j);CompoundTag c=companion(p,bytes,j);
            if(j.getString("phase").equals("COMMITTED")&&c!=null&&!c.getString("phase").equals("COMMITTED"))throw new IOException("Completed Hall has an incomplete recovery companion");
            if(!j.getString("phase").equals("COMMITTED")&&(c==null||!c.getString("phase").equals("COMMITTED")))pending=true;
        }return pending;
    }
    static Context capture(MinecraftServer server,Path root,CatanSavedData data,String city,UUID owner)throws IOException {
        var def=MapDefinitionManager.territory(city);if(def==null||!def.dimension().equals(server.overworld().dimension().location().toString())||data.territory(city)==null||!owner.equals(data.territory(city).ownerNationId()))throw new IOException("Foreign Hall read owner/dimension");
        return capture(root,data,def);
    }
    static Context capture(Path root,CatanSavedData data,TerritoryDefinition def)throws IOException {
        SectorSites53.canonical(def);if(!root.isAbsolute()||!root.equals(root.toRealPath())||Files.isSymbolicLink(root.resolve("data")))throw new IOException("Noncanonical Hall context root");
        var t=data.territory(def.id());if(t==null||t.ownerNationId()==null||data.nation(t.ownerNationId())==null)throw new IOException("Missing Hall context owner");
        if(pending(root,def.id()))throw new PreparationDeferred("Town Hall upgrade awaits recovery before wall work");
        List<Bound> files=new ArrayList<>();Map<String,String> receipts=new HashMap<>();Set<Path> absent=new HashSet<>();
        for(int tier=4;tier<=5;tier++)for(Path p:List.of(original(root,def.id(),tier),PaidConstructionRecovery.companion(original(root,def.id(),tier)),repair(root,def.id(),tier))){
            if(Files.exists(p,LinkOption.NOFOLLOW_LINKS))files.add(new Bound(p,PaidConstructionRecovery.readBounded(p,LIMIT)));else absent.add(p);
        }
        if(t.cityLevel()<4)return new Context(root,data,def,Map.of(),files,receipts,absent);
        if(t.cityLevel()>5)throw new IOException("Unknown Hall context tier");
        int tier=t.cityLevel();Path p=original(root,def.id(),tier);byte[] bytes=PaidConstructionRecovery.readBounded(p,LIMIT);CompoundTag j=PrestigePurchaseRecovery.read(bytes);header(root,def.id(),tier,j);
        if(!t.ownerNationId().toString().equals(j.getString("ownerNation")))throw new IOException("Hall context owner mismatch");
        int revision;try{revision=TownHallRevisions.revision(tier,j.getString("targetToken"));}catch(RuntimeException e){throw new IOException("Unknown paid Hall revision",e);}
        validateRepairLineage(root,def,t.ownerNationId(),tier,j.getString("targetToken"),data.placedMapAsset(key(def.id())));
        if(j.contains("targetSHA256")&&!TownHallRevisions.hash(tier,revision).equals(j.getString("targetSHA256")))throw new IOException("Hall target fingerprint changed");
        String receiptKey=PaidConstructionRecovery.receiptKey(j.getUUID("transaction")),receipt=j.getUUID("transaction")+":paid";
        String recorded=data.placedMapAsset(receiptKey);
        if(j.contains("receiptRequired")&&!j.contains("receiptRequired",Tag.TAG_BYTE))throw new IOException("Invalid Hall receipt requirement");
        // Pre-receipt purchases saved the debit, tier and token together. Their
        // COMMITTED original and full pinned topology remain mandatory authority.
        // A recovery companion always finishes by creating its durable receipt.
        if(recorded!=null&&!receipt.equals(recorded)||recorded==null&&(j.getBoolean("receiptRequired")||!j.getString("phase").equals("COMMITTED")))throw new IOException("Hall context lacks original paid receipt");
        receipts.put(receiptKey,recorded);
        CompoundTag recovered=companion(p,bytes,j);if(!j.getString("phase").equals("COMMITTED")&&(recovered==null||!recovered.getString("phase").equals("COMMITTED")))throw new PreparationDeferred("Hall completion is pending");
        var anchor=SchematicAssetRegistry.territoryAnchor("th"+tier,def);var after=TownHallRevisions.plan(tier,revision,anchor);
        if(tier==4){
            if(!SchematicAssetRegistry.asset("th3").placementToken().equals(j.getString("sourceToken")))throw new IOException("Unknown Hall source token");
            IOException basicFailure=null;try{validateHistory(j,BasicConstructionAssets.plan("th3",anchor),after);}catch(IOException e){basicFailure=e;}
            if(basicFailure!=null)try{validateHistory(j,fallback(anchor),after);}catch(IOException e){e.addSuppressed(basicFailure);throw e;}
        }else{
            int sourceRevision;try{sourceRevision=TownHallRevisions.revision(4,j.getString("sourceToken"));}catch(RuntimeException e){throw new IOException("Unknown Hall source revision",e);}
            validateHistory(j,TownHallRevisions.plan(4,sourceRevision,anchor),after);
        }
        Context result=new Context(root,data,def,qualifiedPositions(def),files,receipts,absent);result.verifyUnchanged();return result;
    }
    /** Same-tier visual repair changes the saved token, never its paid purchase history. */
    private static void validateRepairLineage(Path root,TerritoryDefinition def,UUID owner,int tier,String purchased,String current)throws IOException {
        Path file=repair(root,def.id(),tier);
        if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS)){if(!purchased.equals(current))throw new IOException("Hall context token lacks repair lineage");return;}
        var n=TownHallRepairTransaction.read(file);var anchor=SchematicAssetRegistry.territoryAnchor("th"+tier,def);
        CompoundTag identity=new CompoundTag();identity.putString("world",root.toString());identity.putString("dimension",def.dimension());identity.putString("city",def.id());identity.putString("owner",owner.toString());identity.putInt("tier",tier);identity.putIntArray("anchor",new int[]{anchor.x(),anchor.y(),anchor.z(),anchor.rotation()});
        var phase=TownHallRepairTransaction.phase(n);
        if(phase!=TownHallRepairTransaction.Phase.COMMITTED&&phase!=TownHallRepairTransaction.Phase.ROLLED_BACK)throw new PreparationDeferred("Town Hall repair awaits completion");
        int revision=phase==TownHallRepairTransaction.Phase.COMMITTED?2:1;
        if(!purchased.equals(TownHallRevisions.token(tier,1))||!TownHallRevisions.token(tier,revision).equals(current))throw new IOException("Hall repair phase/token mismatch");
        CompoundTag history=new CompoundTag();for(int level=4;level<=5;level++){
            Path p=original(root,def.id(),level);
            if(level>tier){if(Files.exists(p,LinkOption.NOFOLLOW_LINKS))throw new IOException("Future Hall purchase disagrees with repair lineage");history.putString("tier"+level,"ABSENT");continue;}
            byte[] bytes=PaidConstructionRecovery.readBounded(p,LIMIT);var j=PrestigePurchaseRecovery.read(bytes);header(root,def.id(),level,j);
            if(!j.getString("phase").equals("COMMITTED")||!owner.toString().equals(j.getString("ownerNation")))throw new IOException("Repair lacks original completed Hall history");
            history.putString("tier"+level,TownHallRevisions.sha(bytes));
        }
        var before=TownHallRevisions.plan(tier,1,anchor);var after=TownHallRevisions.plan(tier,2,anchor);int[] mask=TownHallRevisions.mask(before,after);
        if(n.getInt("schema")!=1||!n.hasUUID("transaction")||!identity.equals(n.getCompound("identity"))||!history.equals(n.getCompound("history"))||!TownHallRevisions.hash(tier,1).equals(n.getString("sourceHash"))||!TownHallRevisions.hash(tier,2).equals(n.getString("targetHash"))||!Arrays.equals(mask,n.getIntArray("mask")))throw new IOException("Foreign Hall repair lineage");
        var rows=n.getList("blocks",Tag.TAG_COMPOUND);if(rows.size()!=mask.length)throw new IOException("Incomplete Hall repair lineage");
        for(int k=0;k<mask.length;k++){int i=mask[k];var row=rows.getCompound(k);if(row.getInt("index")!=i||!NbtUtils.writeBlockState(before.get(i).state()).equals(row.getCompound("before"))||!NbtUtils.writeBlockState(after.get(i).state()).equals(row.getCompound("after")))throw new IOException("Foreign Hall repair cell");}
    }
    static synchronized Map<BlockPos,BlockState> qualifiedPositions(TerritoryDefinition def)throws IOException {
        SectorSites53.canonical(def);if(MASKS.containsKey(def.id()))return MASKS.get(def.id());
        var site=SectorSites53.site(def.id());var walls=SectorSites53.assembly(site,SectorCityAP3.levels(0,0,0,0),Set.of());var owners=SectorSites53.ownership(site);
        var anchor=SchematicAssetRegistry.territoryAnchor("th3",def);var basic=BasicConstructionAssets.plan("th3",anchor);Map<BlockPos,BlockState> mask=new HashMap<>();
        int overlap=0;for(var cell:basic)if(walls.containsKey(cell.position())){overlap++;if(owners.containsKey(cell.position()))throw new IOException("Hall context overlaps owned wall geometry");}
        if(overlap!=88)throw new IOException("Hall/wall guard intersection changed");
        for(int tier=4;tier<=5;tier++)for(int revision=1;revision<=2;revision++){
            var target=TownHallRevisions.plan(tier,revision,anchor);int changed=0;
            for(int i=0;i<basic.size();i++){var old=basic.get(i);var next=target.get(i);BlockState wall=walls.get(old.position());if(wall==null)continue;
                if(!old.state().equals(wall))throw new IOException("Unqualified early Hall wall context");
                if(!next.state().equals(wall)){
                    if(old.localX()<32||old.localX()>42||old.localY()!=1||old.localZ()!=0||!wall.equals(Blocks.GRASS_BLOCK.defaultBlockState())||!next.state().equals(Blocks.POLISHED_ANDESITE.defaultBlockState())||site.protectedRoads().contains(old.position()))throw new IOException("Unqualified prestige wall context");
                    mask.put(old.position(),next.state());changed++;
                }
            }if(changed!=11)throw new IOException("Prestige approach mask changed");
        }
        if(mask.size()!=11)throw new IOException("Ambiguous Hall context transform");var result=Collections.unmodifiableMap(new HashMap<>(mask));MASKS.put(def.id(),result);return result;
    }
    private static BlockState decode(CompoundTag row,String field)throws IOException{if(!row.contains(field,Tag.TAG_COMPOUND))throw new IOException("Missing Hall structured delta");var n=row.getCompound(field);BlockState s=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),n);if(!NbtUtils.writeBlockState(s).equals(n))throw new IOException("Invalid Hall delta block state");return s;}
    private static void validateHistory(CompoundTag j,List<SchematicPlacementService.PlanCell> before,List<SchematicPlacementService.PlanCell> after)throws IOException {
        if(before.size()!=TownHallRevisions.CELLS||after.size()!=before.size())throw new IOException("Incomplete Hall plan");
        if(!j.contains("treasuryCost",Tag.TAG_LONG)||!j.contains("treasuryBefore",Tag.TAG_LONG)||j.getLong("treasuryCost")<0||j.getLong("treasuryBefore")<j.getLong("treasuryCost"))throw new IOException("Invalid historical Hall price");
        var resources=j.getCompound("resources");if(resources.getAllKeys().size()!=ResourceType.values().length)throw new IOException("Incomplete Hall original resources");
        for(var r:ResourceType.values()){var n=resources.getCompound(r.name());if(!n.getAllKeys().equals(Set.of("before","cost"))||!n.contains("before",Tag.TAG_LONG)||!n.contains("cost",Tag.TAG_LONG)||n.getLong("cost")<0||n.getLong("before")<n.getLong("cost"))throw new IOException("Invalid Hall material preimage");}
        var natural=PrestigeNaturalSupport.read(j,before,after);Map<BlockPos,CompoundTag> rows=new HashMap<>();Map<BlockPos,BlockState> actual=new HashMap<>(),doors=new HashMap<>();
        var saved=j.getList("blocks",Tag.TAG_COMPOUND);if(saved.isEmpty()||saved.size()>before.size())throw new IOException("Missing Hall deltas");
        for(Tag tag:saved){var row=(CompoundTag)tag;var pos=BlockPos.of(row.getLong("pos"));if(!row.contains("pos",Tag.TAG_LONG)||rows.put(pos,row)!=null)throw new IOException("Duplicate Hall delta");actual.put(pos,decode(row,"beforeState"));decode(row,"afterState");}
        for(var c:before)if(c.state().getBlock() instanceof DoorBlock)doors.put(c.position(),c.state());var missing=TownHallInteractionPolicy.missingDoorCells(doors,p->actual.getOrDefault(p,doors.get(p)));
        var manager=after.get(TownHallRevisions.managerIndex()).position();if(!manager.equals(new BlockPos(j.getInt("managerX"),j.getInt("managerY"),j.getInt("managerZ")))||!j.contains("managerNbt",Tag.TAG_COMPOUND)||!j.getCompound("managerNbt").getString("id").equals("minecraft:lectern"))throw new IOException("Invalid Hall management record");
        int consumed=0;
        for(int i=0;i<before.size();i++){var old=before.get(i);var next=after.get(i);BlockPos p=old.position();if(!p.equals(next.position()))throw new IOException("Hall transform mismatch");var row=rows.get(p);
            if(p.equals(manager)){if(row!=null||!old.state().is(Blocks.LECTERN)||!next.state().is(Blocks.LECTERN))throw new IOException("Hall manager was changed");continue;}
            if(row==null){if(!TownHallInteractionPolicy.matches(old.state(),next.state(),false)||(natural.has(p)&&!natural.before(p).equals(natural.targetFor(p,natural.before(p),next.state()))))throw new IOException("Omitted Hall delta at "+p);continue;}
            consumed++;BlockState a=actual.get(p),b=decode(row,"afterState");
            if(!TownHallInteractionPolicy.matches(a,old.state(),false)&&!missing.contains(p)&&!(natural.has(p)&&natural.before(p).equals(a)))throw new IOException("Foreign Hall source delta at "+p);
            if(natural.has(p)&&!natural.before(p).equals(a)||!natural.targetFor(p,a,next.state()).equals(b)||a.equals(b)||!a.getFluidState().isEmpty()||!b.getFluidState().isEmpty())throw new IOException("Foreign Hall target delta at "+p);
            boolean lectern=a.is(Blocks.LECTERN)&&!b.hasBlockEntity();if(a.hasBlockEntity()&&!lectern||b.hasBlockEntity())throw new IOException("Foreign Hall inventory delta");
            if(lectern){var n=row.getCompound("beforeBlockEntity");if(!n.getString("id").equals("minecraft:lectern")||n.contains("Book")&&!n.getCompound("Book").isEmpty())throw new IOException("Nonempty Hall inventory preimage");}else if(row.contains("beforeBlockEntity"))throw new IOException("Foreign Hall block entity");
        }if(consumed!=rows.size())throw new IOException("Hall delta outside pinned envelope");
    }
    private static List<SchematicPlacementService.PlanCell> fallback(MapAnchor anchor)throws IOException {
        byte[] raw;try(var in=SectorHallContext53.class.getResourceAsStream("/data/catancraft/schematics/thall3.schem")){if(in==null)throw new IOException("Missing original TH3 source");raw=in.readNBytes(100001);}
        if(!FALLBACK_SHA.equals(TownHallRevisions.sha(raw)))throw new IOException("Original TH3 fingerprint changed");var n=NbtIo.readCompressed(new ByteArrayInputStream(raw));
        int max=n.getInt("PaletteMax");BlockState[] palette=new BlockState[max];var encoded=n.getCompound("Palette");for(String k:encoded.getAllKeys())palette[encoded.getInt(k)]=BasicConstructionAssets.parsePalette(k);
        List<SchematicPlacementService.PlanCell> out=new ArrayList<>();int value=0,shift=0,index=0,sign=anchor.rotation()==0?1:-1;
        for(byte sb:n.getByteArray("BlockData")){int b=sb&255;value|=(b&127)<<shift;if((b&128)!=0){shift+=7;continue;}int x=index%75,z=index/75%65,y=index/(75*65);out.add(new SchematicPlacementService.PlanCell(x,y,z,new BlockPos(anchor.x()+sign*(x-37),anchor.y()+y-2,anchor.z()+sign*(z-1)),palette[value].rotate(ProducerExteriorR2.rotation(anchor))));index++;value=0;shift=0;}
        if(index!=TownHallRevisions.CELLS)throw new IOException("Original TH3 envelope changed");return List.copyOf(out);
    }
}
