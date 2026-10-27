package com.quin.catancraft.world;

import com.google.gson.*;
import com.quin.catancraft.data.*;
import com.quin.catancraft.economy.*;
import com.quin.catancraft.map.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Strict adapter for the original R5 civic journal format and hash-pinned R7 plans. */
final class CivicPurchaseRecovery {
    private CivicPurchaseRecovery(){}
    interface Authorization { void check()throws IOException; }
    static JsonObject read(byte[] bytes)throws IOException {
        try{
            JsonObject j=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
            String hash=j.get("sha256").getAsString();JsonObject checked=j.deepCopy();R5CivicAutomaticInstaller.seal(checked);
            if(!hash.equals(checked.get("sha256").getAsString())||j.get("schema").getAsInt()!=1)throw new IOException("Civic journal checksum/schema mismatch");
            String phase=j.get("phase").getAsString();if(!Set.of("PENDING","PHYSICAL_COMPLETE_ECONOMY_PENDING","COMMITTED").contains(phase))throw new IOException("Unknown civic phase");
            return j;
        }catch(IOException e){throw e;}catch(Exception e){throw new IOException("Invalid civic purchase journal",e);}
    }
    static PaidConstructionRecovery.Environment environment(MinecraftServer server,CatanSavedData data,ServerLevel level,
            TerritoryDefinition def,Path root,Path original,Authorization authorizer)throws Exception {
        byte[] bytes=PaidConstructionRecovery.readBounded(original,2_800_000);JsonObject j=read(bytes);
        BuildingType type=BuildingType.valueOf(j.get("module").getAsString());
        return environment(server,data,level,def,root,original,authorizer,R5CivicAutomaticInstaller.contract(def,type));
    }
    static PaidConstructionRecovery.Environment fixture(MinecraftServer server,CatanSavedData data,ServerLevel level,
            TerritoryDefinition def,Path root,Path original,Authorization authorizer,R5CivicAutomaticInstaller.Plan plan)throws Exception {
        if(!(server instanceof net.minecraft.gametest.framework.GameTestServer))throw new IOException("Recovery fixture requires GameTestServer");
        return environment(server,data,level,def,root,original,authorizer,plan);
    }
    private static PaidConstructionRecovery.Environment environment(MinecraftServer server,CatanSavedData data,ServerLevel level,
            TerritoryDefinition def,Path root,Path original,Authorization authorizer,R5CivicAutomaticInstaller.Plan plan)throws Exception {
        byte[] bytes=PaidConstructionRecovery.readBounded(original,2_800_000);JsonObject j=read(bytes);
        if(j.get("phase").getAsString().equals("COMMITTED"))throw new IOException("Completed civic history is not a recovery request");
        String city=def.id();TerritoryData t=data.territory(city);
        UUID owner=UUID.fromString(j.get("owner_nation_uuid").getAsString()),id=UUID.fromString(j.get("transaction_id").getAsString());
        NationData nation=data.nation(owner);BuildingType type=BuildingType.valueOf(j.get("module").getAsString());
        if(!R5CivicAutomaticInstaller.isCivic(type)||!city.equals(j.get("territory").getAsString())||!root.toString().equals(j.get("world_root").getAsString())
                ||!def.dimension().equals(j.get("dimension").getAsString())||!def.dimension().equals(level.dimension().location().toString())
                ||!"R5-1 VERIFIED AGAINST R7".equals(j.get("source_revision").getAsString()))throw new IOException("Foreign civic source identity");
        String name="catancraft_r5_auto_"+city+"_"+type.name().toLowerCase(Locale.ROOT)+"_v1.json";
        if(!original.equals(root.resolve("data").resolve(name)))throw new IOException("Civic journal location mismatch");
        EconomyCost cost=EconomyCatalog.buildingCost(type);
        validateCost(j,cost);
        JsonArray ordered=new JsonArray(),originals=new JsonArray();Map<BlockPos,List<BlockState>> allowed=new LinkedHashMap<>();
        for(var w:plan.writes()){
            JsonObject row=new JsonObject();row.add("xyz",R5CivicAutomaticInstaller.xyz(w.pos()));row.addProperty("stage",w.stage());
            row.addProperty("before",R5CivicAutomaticInstaller.canonical(w.before()));row.addProperty("after",R5CivicAutomaticInstaller.canonical(w.after()));ordered.add(row);
            allowed.computeIfAbsent(w.pos(),k->new ArrayList<>()).addAll(List.of(w.before(),w.after()));
        }
        List<PaidConstructionRecovery.Cell> cells=new ArrayList<>();
        for(var c:plan.finalCells().values()){
            JsonObject row=new JsonObject();row.add("xyz",R5CivicAutomaticInstaller.xyz(c.pos()));row.addProperty("before",R5CivicAutomaticInstaller.canonical(c.before()));
            row.addProperty("after",R5CivicAutomaticInstaller.canonical(c.after()));row.addProperty("before_nbt","NONE");originals.add(row);
            var states=new ArrayList<>(allowed.get(c.pos()));BlockState target=c.after(),live=level.getBlockState(c.pos());
            // An already installed barrel may be open. Never close/recreate it or replace its actual inventory.
            if(target.is(Blocks.BARREL)&&live.is(Blocks.BARREL)&&live.setValue(BarrelBlock.OPEN,false).equals(target.setValue(BarrelBlock.OPEN,false))){states.add(live);target=live;}
            cells.add(new PaidConstructionRecovery.Cell(c.pos(),states,target));
        }
        if(!ordered.equals(j.getAsJsonArray("ordered_writes"))||!originals.equals(j.getAsJsonArray("originals")))throw new IOException("Civic journal does not match the exact approved source/write contract");
        boolean required=j.has("receipt_required")&&j.get("receipt_required").getAsBoolean();
        CompoundTag identity=new CompoundTag();identity.putString("kind","civic");identity.putString("world",root.toString());identity.putString("city",city);identity.putUUID("owner",owner);identity.putUUID("transaction",id);identity.putString("module",type.name());
        return new PaidConstructionRecovery.Environment(){
            public ServerLevel level(){return level;}public Path original(){return original;}public byte[] originalBytes(){return bytes.clone();}
            public CompoundTag identity(){return identity.copy();}public List<PaidConstructionRecovery.Cell> cells(){return List.copyOf(cells);}
            public void authorize()throws IOException{
                authorizer.check();
                if(t==null||nation==null||data.territory(city)!=t||data.nation(owner)!=nation||!owner.equals(t.ownerNationId()))throw new IOException("Civic recovery owner changed");
            }
            public boolean paid()throws IOException{
                var found=t.buildings().stream().filter(b->b.type()==type).toList();
                boolean target=found.size()==1&&found.get(0).level()==1&&found.get(0).targetStock()==0&&found.get(0).plotId().isEmpty();
                return PaidConstructionRecovery.paid(data,id,target,found.isEmpty(),required);
            }
            public void checkPayment()throws IOException{
                authorize();if(!paid()){
                    if(t.cityLevel()<type.minCityLevel()||t.buildings().size()>=EconomyCatalog.maxBuildingSlots(t.cityLevel()))throw new IOException("Civic recovery capacity/level changed");
                    if(!cost.canAfford(nation))throw new PreparationDeferred("Waiting for original civic purchase resources; no repeat payment taken");
                }
            }
            public void settle()throws IOException{
                checkPayment();if(!paid()){cost.charge(nation);t.buildings().add(new BuildingInstance(type,1,0,""));}
                PaidConstructionRecovery.receipt(data,id);data.setDirty();
            }
            public CompoundTag ledger(){return data.save(new CompoundTag());}
            public void save()throws IOException{if(!server.saveEverything(true,true,true))throw new IOException("Civic recovery save incomplete; original purchase and receipt retained");}
        };
    }
    private static void validateCost(JsonObject j,EconomyCost cost)throws IOException {
        try{
            if(j.get("money_cost").getAsLong()!=cost.money()||j.get("treasury_before").getAsLong()<cost.money())throw new IOException("Civic original money contract mismatch");
            JsonObject resources=j.getAsJsonObject("resources");if(resources.size()!=ResourceType.values().length)throw new IOException("Incomplete civic resource preimage");
            for(ResourceType r:ResourceType.values()){
                JsonObject v=resources.getAsJsonObject(r.name());long required=cost.resources().getOrDefault(r,0L);
                if(v.get("cost").getAsLong()!=required||v.get("before").getAsLong()<required)throw new IOException("Civic resource contract mismatch "+r);
            }
        }catch(IOException e){throw e;}catch(Exception e){throw new IOException("Malformed civic original cost",e);}
    }
}
