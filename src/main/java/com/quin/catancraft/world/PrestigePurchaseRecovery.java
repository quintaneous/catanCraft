package com.quin.catancraft.world;

import com.quin.catancraft.data.*;
import com.quin.catancraft.economy.*;
import com.quin.catancraft.map.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Recovery of existing TH3->4/TH4->5 records; never a same-tier repair or blind repaste. */
final class PrestigePurchaseRecovery {
    private PrestigePurchaseRecovery(){}
    static CompoundTag read(byte[] bytes)throws IOException {
        try(var in=new GZIPInputStream(new ByteArrayInputStream(bytes))){
            byte[] plain=in.readNBytes(64*1024*1024+1);if(plain.length>64*1024*1024)throw new IOException("Oversized prestige history");
            CompoundTag j=NbtIo.read(new DataInputStream(new ByteArrayInputStream(plain)));
            if(j==null||j.getInt("schema")!=1||!j.hasUUID("transaction")||!Set.of("PENDING","PHYSICAL_COMPLETE_ECONOMY_PENDING","COMMITTED").contains(j.getString("phase")))throw new IOException("Invalid prestige journal schema/phase");
            return j;
        }
    }
    static PaidConstructionRecovery.Environment environment(MinecraftServer server,CatanSavedData data,ServerLevel level,
            TerritoryDefinition def,Path root,Path original,CivicPurchaseRecovery.Authorization authorizer)throws Exception {
        byte[] bytes=PaidConstructionRecovery.readBounded(original,8*1024*1024);CompoundTag j=read(bytes);
        if(j.getString("phase").equals("COMMITTED"))throw new IOException("Completed prestige history is not a recovery request");
        int from=j.getInt("fromLevel"),to=j.getInt("toLevel");String city=def.id();
        UUID id=j.getUUID("transaction"),owner=UUID.fromString(j.getString("ownerNation"));
        NationData nation=data.nation(owner);TerritoryData t=data.territory(city);
        if((to!=4&&to!=5)||from!=to-1||!city.equals(j.getString("city"))||!root.toString().equals(j.getString("worldRoot"))
                ||!def.dimension().equals(level.dimension().location().toString())||!original.equals(root.resolve("data").resolve("catancraft_th_prestige_"+city+"_"+to+"_v1.dat")))throw new IOException("Foreign prestige identity");
        String sourceToken=j.getString("sourceToken"),targetToken=j.getString("targetToken"),stateKey="territory:"+city+":town_hall";
        var a=SchematicAssetRegistry.territoryAnchor("th"+from,def);var b=SchematicAssetRegistry.territoryAnchor("th"+to,def);
        if(a==null||b==null||a.x()!=b.x()||a.y()!=b.y()||a.z()!=b.z()||a.rotation()!=b.rotation())throw new IOException("Prestige anchor changed");
        int revision=TownHallRevisions.revision(to,targetToken);
        if(j.contains("targetSHA256")&&!TownHallRevisions.hash(to,revision).equals(j.getString("targetSHA256")))throw new IOException("Prestige target hash mismatch");
        List<SchematicPlacementService.PlanCell> before;
        if(from==3){
            if(!SchematicAssetRegistry.asset("th3").placementToken().equals(sourceToken))throw new IOException("Unknown TH3 source revision");
            var validation=SchematicPlacementService.validateAsset("th3",a);if(!validation.success())throw new IOException(validation.message());
            before=SchematicPlacementService.plannedCells("th3",a);
        }else before=TownHallRevisions.plan(from,TownHallRevisions.revision(from,sourceToken),a);
        var after=TownHallRevisions.plan(to,revision,b);
        if(before.size()!=TownHallRevisions.CELLS||after.size()!=before.size())throw new IOException("Prestige envelope mismatch");
        EconomyCost cost=EconomyCatalog.cityUpgradeCost(from);validateCost(j,cost);
        ListTag saved=j.getList("blocks",Tag.TAG_COMPOUND);if(saved.isEmpty()||saved.size()>TownHallRevisions.CELLS)throw new IOException("Missing prestige actual before-images");
        Map<BlockPos,CompoundTag> changes=new LinkedHashMap<>();Map<BlockPos,BlockState> actualBefore=new HashMap<>(),doors=new HashMap<>();
        for(int i=0;i<saved.size();i++){
            var row=saved.getCompound(i);BlockPos p=BlockPos.of(row.getLong("pos"));
            if(!row.contains("pos",Tag.TAG_LONG)||changes.put(p,row)!=null)throw new IOException("Invalid/duplicate prestige before-image");
            actualBefore.put(p,decode(row,"beforeState"));decode(row,"afterState");
        }
        for(var cell:before)if(cell.state().getBlock() instanceof DoorBlock)doors.put(cell.position(),cell.state());
        Set<BlockPos> missing=TownHallInteractionPolicy.missingDoorCells(doors,p->actualBefore.getOrDefault(p,doors.get(p)));
        BlockPos manager=after.get(TownHallRevisions.managerIndex()).position();
        if(!manager.equals(new BlockPos(j.getInt("managerX"),j.getInt("managerY"),j.getInt("managerZ")))||!j.contains("managerNbt",Tag.TAG_COMPOUND))throw new IOException("Missing management lectern preservation record");
        List<PaidConstructionRecovery.Cell> cells=new ArrayList<>();Set<BlockPos> consumed=new HashSet<>();
        for(int i=0;i<before.size();i++){
            var old=before.get(i);var next=after.get(i);BlockPos p=old.position();boolean management=p.equals(manager);
            if(!p.equals(next.position())||!def.siegeRegion().contains(p)||!def.restorationRegion().contains(p))throw new IOException("Prestige geometry changed at "+p);
            if(!level.hasChunkAt(p))throw new PreparationDeferred("Prestige context is still loading");
            BlockState live=level.getBlockState(p);CompoundTag row=changes.get(p);
            if(management){
                if(row!=null||!old.state().is(Blocks.LECTERN)||!next.state().is(Blocks.LECTERN)||!TownHallInteractionPolicy.matches(live,next.state(),true)||!(level.getBlockEntity(p) instanceof LecternBlockEntity))throw new IOException("Management lectern conflict");
                // Preserve today's actual book/page NBT, never restore the historical book snapshot.
                cells.add(new PaidConstructionRecovery.Cell(p,List.of(live),live));continue;
            }
            if(row==null){
                if(!TownHallInteractionPolicy.matches(old.state(),next.state(),false)||!TownHallInteractionPolicy.matches(live,next.state(),false))throw new IOException("Prestige read-context conflict or omitted delta at "+p);
                if(level.getBlockEntity(p)!=null&&!(live.is(Blocks.LECTERN)&&level.getBlockEntity(p) instanceof LecternBlockEntity))throw new IOException("Unexpected unchanged prestige inventory at "+p);
                cells.add(new PaidConstructionRecovery.Cell(p,List.of(live),live));continue;
            }
            consumed.add(p);BlockState originalState=actualBefore.get(p),target=decode(row,"afterState");
            if(!TownHallInteractionPolicy.matches(originalState,old.state(),false)&&!missing.contains(p))throw new IOException("Untrusted prestige before-state at "+p);
            if(!TownHallInteractionPolicy.targetFor(originalState,next.state()).equals(target)||originalState.equals(target))throw new IOException("Untrusted prestige target at "+p);
            boolean emptyLectern=originalState.is(Blocks.LECTERN)&&!target.hasBlockEntity();
            if((originalState.hasBlockEntity()&&!emptyLectern)||target.hasBlockEntity())throw new IOException("Unapproved prestige inventory delta at "+p);
            if(emptyLectern){
                CompoundTag nbt=row.getCompound("beforeBlockEntity");
                if(!row.contains("beforeBlockEntity",Tag.TAG_COMPOUND)||!"minecraft:lectern".equals(nbt.getString("id"))||nbt.contains("Book")&& !nbt.getCompound("Book").isEmpty())throw new IOException("Nonempty/invalid original decorative lectern at "+p);
            }else if(row.contains("beforeBlockEntity"))throw new IOException("Unexpected prestige block entity preimage");
            // Preserve normal interactions/derived states in a completed target, without
            // broadening source materials or accepting a foreign block.
            BlockState wanted=TownHallInteractionPolicy.matches(live,target,false)?live:target;
            boolean recognized=TownHallInteractionPolicy.matches(live,originalState,false)||TownHallInteractionPolicy.matches(live,target,false);
            if(!recognized)throw new IOException("Prestige recovery conflict at "+p);
            cells.add(new PaidConstructionRecovery.Cell(p,List.of(live),wanted,emptyLectern));
        }
        if(consumed.size()!=changes.size())throw new IOException("Prestige journal writes outside exact envelope");
        boolean required=j.getBoolean("receiptRequired");CompoundTag identity=new CompoundTag();
        identity.putString("kind","prestige");identity.putString("world",root.toString());identity.putString("city",city);identity.putUUID("owner",owner);identity.putUUID("transaction",id);identity.putString("source",sourceToken);identity.putString("target",targetToken);
        return new PaidConstructionRecovery.Environment(){
            public ServerLevel level(){return level;}public Path original(){return original;}public byte[] originalBytes(){return bytes.clone();}
            public CompoundTag identity(){return identity.copy();}public List<PaidConstructionRecovery.Cell> cells(){return List.copyOf(cells);}
            public void authorize()throws IOException{authorizer.check();if(t==null||nation==null||data.territory(city)!=t||data.nation(owner)!=nation||!owner.equals(t.ownerNationId()))throw new IOException("Prestige recovery ownership changed");}
            public boolean paid()throws IOException{return PaidConstructionRecovery.paid(data,id,t.cityLevel()==to&&targetToken.equals(data.placedMapAsset(stateKey)),t.cityLevel()==from&&sourceToken.equals(data.placedMapAsset(stateKey)),required);}
            public void checkPayment()throws IOException{authorize();if(!paid()&&!cost.canAfford(nation))throw new PreparationDeferred("Waiting for original Town Hall purchase resources; no repeat payment taken");}
            public void settle()throws IOException{checkPayment();if(!paid()){cost.charge(nation);t.setCityLevel(to);data.setPlacedMapAsset(stateKey,targetToken);}PaidConstructionRecovery.receipt(data,id);data.setDirty();}
            public CompoundTag ledger(){return data.save(new CompoundTag());}
            public void save()throws IOException{if(!server.saveEverything(true,true,true))throw new IOException("Prestige recovery save incomplete; original journal and receipt retained");}
        };
    }
    private static BlockState decode(CompoundTag row,String key)throws IOException {
        if(!row.contains(key,Tag.TAG_COMPOUND))throw new IOException("Legacy prestige history lacks structured actual before-image; manual review required");
        CompoundTag n=row.getCompound(key);BlockState s=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),n);
        if(!NbtUtils.writeBlockState(s).equals(n))throw new IOException("Invalid prestige blockstate");return s;
    }
    private static void validateCost(CompoundTag j,EconomyCost cost)throws IOException {
        if(cost==null||!j.contains("treasuryCost",Tag.TAG_LONG)||j.getLong("treasuryCost")!=cost.money()||j.getLong("treasuryBefore")<cost.money())throw new IOException("Prestige original money contract mismatch");
        CompoundTag resources=j.getCompound("resources");if(resources.getAllKeys().size()!=ResourceType.values().length)throw new IOException("Incomplete prestige resource preimage");
        for(ResourceType r:ResourceType.values()){var row=resources.getCompound(r.name());long amount=cost.resources().getOrDefault(r,0L);if(!row.contains("cost",Tag.TAG_LONG)||row.getLong("cost")!=amount||row.getLong("before")<amount)throw new IOException("Prestige original resource contract mismatch "+r);}
    }
}
