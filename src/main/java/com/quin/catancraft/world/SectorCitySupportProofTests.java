package com.quin.catancraft.world;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.io.IOException;
import java.util.*;

/** Small in-memory lineage fixtures using native registries. No world writes or approved artwork. */
@GameTestHolder("catancraft_sectors_group_proof") @PrefixGameTestTemplate(false)
public final class SectorCitySupportProofTests {
    private static final BlockPos NORTH=new BlockPos(0,120,-10),EAST=new BlockPos(10,120,0),ROAD=new BlockPos(0,119,0);
    private record Fixture(UUID nation,UUID parent,SectorTransaction.Plan before,SectorTransaction.Plan next,CompoundTag proof,CompoundTag identity){}
    private static CompoundTag state(int level,String receipt,String revision){CompoundTag n=new CompoundTag();n.putInt("level",level);n.putString("receipt",receipt);n.putString("revision",revision);return n;}
    private static CompoundTag states(){CompoundTag n=new CompoundTag();for(var side:SectorContracts.Direction.values())n.put(side.name(),state(0,"",""));return n;}
    private static CompoundTag group(UUID parent,CompoundTag before){
        CompoundTag n=new CompoundTag();n.putInt("schema",1);n.putString("parentFile",parent==null?"":"north.dat");n.putString("parentCityReceipt",parent==null?"":parent+":paid");if(parent!=null)n.putUUID("parentId",parent);
        n.putString("archiveSHA256",SectorCityAP3.ARCHIVE_SHA);n.putString("resourceSHA256",SectorCityAP3.RESOURCE_SHA);n.putLong("translation",0L);n.put("beforeStates",before);
        n.put("beforeOpenCorners",new ListTag());n.put("afterOpenCorners",new ListTag());return n;
    }
    private static CompoundTag identity(UUID nation,String sector,CompoundTag group){
        CompoundTag n=new CompoundTag();n.putString("world","/isolated-group-policy-fixture");n.putString("worldId","native-group-policy-fixture");n.putString("dimension","minecraft:overworld");n.putString("city","a");n.putUUID("owner",nation);n.putString("approval","gametest-fixture-v1");n.putString("sector",sector);n.putString("operationFile",sector+".dat");n.put("cityGroup",group);return n;
    }
    private static Fixture fixture()throws IOException {
        UUID nation=UUID.randomUUID(),parent=UUID.randomUUID();
        var reads=List.of(new SectorTransaction.Read(NORTH.below(),Blocks.GRASS_BLOCK.defaultBlockState()),new SectorTransaction.Read(EAST.below(),Blocks.GRASS_BLOCK.defaultBlockState()),new SectorTransaction.Read(ROAD,Blocks.BEDROCK.defaultBlockState()));
        var before=new SectorTransaction.Plan("north","north-fresh-r1","north-basic-r1",0,1,
            List.of(new SectorTransaction.Cell(NORTH,Blocks.AIR.defaultBlockState(),Blocks.STONE.defaultBlockState()),new SectorTransaction.Cell(EAST,Blocks.AIR.defaultBlockState(),Blocks.AIR.defaultBlockState())),reads,SectorNaturalSupportPolicy.coveredGrass(Set.of(NORTH.below())));
        var next=new SectorTransaction.Plan("east","east-fresh-r1","east-basic-r1",0,1,
            List.of(new SectorTransaction.Cell(NORTH,Blocks.STONE.defaultBlockState(),Blocks.STONE.defaultBlockState()),new SectorTransaction.Cell(EAST,Blocks.AIR.defaultBlockState(),Blocks.STONE_BRICKS.defaultBlockState())),reads,SectorNaturalSupportPolicy.coveredGrass(Set.of(NORTH.below(),EAST.below())));
        CompoundTag priorIdentity=identity(nation,"north",group(null,states())),progress=states();progress.put("NORTH",state(1,parent+":paid",before.targetRevision()));
        CompoundTag proof=new CompoundTag();proof.putInt("schema",1);proof.putUUID("id",parent);proof.putUUID("nation",nation);proof.putString("phase","COMMITTED");proof.put("identity",priorIdentity);proof.put("spec",SectorTransaction.describe(before));
        return new Fixture(nation,parent,before,next,proof,identity(nation,"east",group(parent,progress)));
    }
    private static Set<BlockPos> inherit(Fixture f,CompoundTag proof,CompoundTag identity)throws IOException{return SectorNaturalSupportPolicy.inherited(f.next(),proof,"",f.nation(),identity);}
    @FunctionalInterface interface Attempt{void run()throws IOException;}
    private static void refuse(Attempt attempt)throws IOException{try{attempt.run();}catch(IOException expected){return;}throw new AssertionError("Invalid grouped natural-support proof accepted");}
    @GameTest(template="townhall_state_empty",timeoutTicks=200)
    public static void new_east_inherits_paid_north_only_from_complete_city_source(GameTestHelper h){
        try{var f=fixture();Set<BlockPos> accepted=inherit(f,f.proof(),f.identity());
            h.assertTrue(accepted.equals(Set.of(NORTH.below()))&&!accepted.contains(EAST.below()),"New side inherited an unpaid/future cover or lost paid neighboring support");
            // Whole-context mismatch matters even outside the natural-support whitelist.
            var reads=new ArrayList<>(f.next().reads());reads.set(2,new SectorTransaction.Read(ROAD,Blocks.STONE.defaultBlockState()));
            var drifted=new SectorTransaction.Plan("east",f.next().sourceRevision(),f.next().targetRevision(),0,1,f.next().cells(),reads,f.next().naturalSupport());
            refuse(()->SectorNaturalSupportPolicy.inherited(drifted,f.proof(),"",f.nation(),f.identity()));
            CompoundTag nongroup=f.identity().copy();nongroup.remove("cityGroup");refuse(()->inherit(f,f.proof(),nongroup));h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    @GameTest(template="townhall_state_empty",timeoutTicks=200)
    public static void grouped_parent_receipt_assets_site_owner_and_progress_are_exact(GameTestHelper h){
        try{var f=fixture();
            for(String field:List.of("archiveSHA256","resourceSHA256")){CompoundTag n=f.identity().copy();n.getCompound("cityGroup").putString(field,"0".repeat(64));refuse(()->inherit(f,f.proof(),n));}
            CompoundTag moved=f.identity().copy();moved.getCompound("cityGroup").putLong("translation",new BlockPos(1,0,0).asLong());refuse(()->inherit(f,f.proof(),moved));
            CompoundTag otherOwner=f.identity().copy();otherOwner.putUUID("owner",UUID.randomUUID());refuse(()->inherit(f,f.proof(),otherOwner));
            CompoundTag wrongParent=f.identity().copy();wrongParent.getCompound("cityGroup").putUUID("parentId",UUID.randomUUID());refuse(()->inherit(f,f.proof(),wrongParent));
            CompoundTag missing=f.proof().copy();missing.getCompound("identity").remove("cityGroup");refuse(()->inherit(f,missing,f.identity()));
            CompoundTag badFile=f.proof().copy();badFile.getCompound("identity").putString("operationFile","other.dat");refuse(()->inherit(f,badFile,f.identity()));
            CompoundTag badProgress=f.identity().copy();badProgress.getCompound("cityGroup").getCompound("beforeStates").put("EAST",state(1,UUID.randomUUID()+":paid","foreign-paid-east"));refuse(()->inherit(f,f.proof(),badProgress));
            CompoundTag corners=f.identity().copy();ListTag wrongCorners=new ListTag();wrongCorners.add(StringTag.valueOf("NE"));corners.getCompound("cityGroup").put("beforeOpenCorners",wrongCorners);refuse(()->inherit(f,f.proof(),corners));
            CompoundTag pending=f.proof().copy();pending.putString("phase","APPLYING");refuse(()->inherit(f,pending,f.identity()));
            h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
}
