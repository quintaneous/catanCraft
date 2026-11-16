package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.*;
import com.quin.catancraft.map.MapAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.*;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/** Real bulk placement from empty disposable fixtures, not a manually staged
 * producer target. Actual authored all-city activation is a separate campaign. */
@GameTestHolder("catancraft_placement53") @PrefixGameTestTemplate(false)
public final class SchematicPlacement53Tests {
    private SchematicPlacement53Tests(){}
    @GameTest(template="townhall_state_empty",batch="placement53_shape",timeoutTicks=600)
    public static void native_flag_two_can_change_a_successfully_written_fence(GameTestHelper h)throws Exception{
        var level=h.getLevel();require(level.getServer() instanceof GameTestServer,"Isolated native fixture required");
        BlockPos west=h.absolutePos(new BlockPos(2,2,2)),east=west.east();
        var plan=ProducerRevisions.plan("quarry",1,new MapAnchor("fence-repro",1,8,15,0));
        BlockState westState=plan.stream().filter(c->c.localX()==29&&c.localY()==8&&c.localZ()==9).findFirst().orElseThrow().state();
        BlockState eastState=plan.stream().filter(c->c.localX()==30&&c.localY()==8&&c.localZ()==9).findFirst().orElseThrow().state();
        require(westState.is(Blocks.SPRUCE_FENCE)&&eastState.is(Blocks.SPRUCE_FENCE),"Pinned native reproduction source changed");
        for(BlockPos p:BlockPos.betweenClosed(west.offset(-1,-1,-1),east.offset(1,1,1)))level.setBlock(p,Blocks.AIR.defaultBlockState(),SchematicPlacementService.BULK_FLAGS);
        require(level.setBlock(west,westState,2),"Old-flags west fence did not place");boolean returned=level.setBlock(east,eastState,2);BlockState derived=level.getBlockState(east);
        require(returned&&!derived.equals(eastState)&&derived.is(Blocks.SPRUCE_FENCE)&&derived.getValue(FenceBlock.WEST),"Native flag2 mismatch was not reproduced");
        require(ProducerRevisions.matches(derived,eastState),"Reproduced state is not an allowed derived connection");
        level.setBlock(west,Blocks.AIR.defaultBlockState(),SchematicPlacementService.BULK_FLAGS);level.setBlock(east,Blocks.AIR.defaultBlockState(),SchematicPlacementService.BULK_FLAGS);
        require(level.setBlock(west,westState,SchematicPlacementService.BULK_FLAGS)&&level.setBlock(east,eastState,SchematicPlacementService.BULK_FLAGS)
                &&level.getBlockState(west).equals(westState)&&level.getBlockState(east).equals(eastState),"Bulk flags failed exact authored readback");
        CatanCraft.LOGGER.info("PLACEMENT53_FLAGS nativeFlag2Returned=true nativeFlag2ExactReadback=false bulkFlags={} exactReadback=true",SchematicPlacementService.BULK_FLAGS);h.succeed();
    }
    @GameTest(template="townhall_state_empty",batch="placement53_north",timeoutTicks=1500)
    public static void north_full_l1_bulk_install_is_recognized_by_paid_upgrade(GameTestHelper h)throws Exception{install(h,0);}
    @GameTest(template="townhall_state_empty",batch="placement53_south",timeoutTicks=1500)
    public static void south_full_l1_bulk_install_is_recognized_by_paid_upgrade(GameTestHelper h)throws Exception{install(h,180);}
    private static void install(GameTestHelper h,int rotation)throws Exception{
        var e=new ProducerTests.Env(h,rotation,1);require(e.level().getServer() instanceof GameTestServer,"Isolated producer fixture required");
        AABB bounds=null;for(String family:ProducerRevisions.FAMILIES){var b=TownHallRepairTransaction.bounds(ProducerRevisions.plan(family,1,e.anchors.get(family))).inflate(2);bounds=bounds==null?b:bounds.minmax(b);}
        SectorChunkLease lease=SectorChunkLease.acquire(e.level(),bounds);await(h,e,lease,0);
    }
    private static void await(GameTestHelper h,ProducerTests.Env e,SectorChunkLease lease,int ticks){try{
        lease.keepAlive();if(!lease.ready()){require(ticks<600,"Bulk fixture chunks did not become ready");h.runAfterDelay(5,()->await(h,e,lease,ticks+5));return;}
        long placed=0;int checked=0;CompoundTag before=e.ledger();
        for(String family:ProducerRevisions.FAMILIES){
            var anchor=e.anchors.get(family);var plan=ProducerRevisions.plan(family,1,anchor);var box=TownHallRepairTransaction.bounds(plan).inflate(1);
            for(BlockPos p:BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ))){
                var be=e.level().getBlockEntity(p);if(be instanceof Container c)c.clearContent();e.level().setBlock(p,Blocks.AIR.defaultBlockState(),SchematicPlacementService.BULK_FLAGS);
            }
            // Only stale generated fixture entities, before the actual install.
            for(var entity:new ArrayList<>(e.level().getEntities((net.minecraft.world.entity.Entity)null,box)))
                if(entity instanceof net.minecraft.world.entity.Mob||entity instanceof net.minecraft.world.entity.item.ItemEntity)entity.discard();
            var result=SchematicPlacementService.placeAsset(e.level().getServer(),e.level().dimension().location().toString(),family,anchor);
            require(result.success(),"Actual bulk "+family+" install failed: "+result.message());long expected=plan.stream().filter(c->!c.state().isAir()).count();
            require(result.changedBlocks()==expected,"Bulk install write count differs from exact empty-source delta");placed+=result.changedBlocks();
            for(var cell:plan){require(e.level().getBlockState(cell.position()).equals(cell.state()),"Bulk target differs from pinned full plan at "+cell.position());checked++;}
            var again=SchematicPlacementService.placeAsset(e.level().getServer(),e.level().dimension().location().toString(),family,anchor);
            require(again.success()&&again.changedBlocks()==0,"Immediate repeated bulk install was not a no-op");
            for(var cell:plan)if(cell.state().is(Blocks.BARREL)){
                var be=e.level().getBlockEntity(cell.position());require(be instanceof BarrelBlockEntity,"Bulk installer failed to create real barrel");
                ((BarrelBlockEntity)be).setItem(0,new ItemStack(Items.DIAMOND,17));
            }
        }
        require(checked==135501&&before.equals(e.ledger()),"Bulk placement touched economy or skipped a producer envelope");
        for(String family:ProducerRevisions.FAMILIES)e.token(family,ProducerRevisions.token(family,1));e.select(1,2);e.save();
        final long writes=placed;var inventories=e.inventories();
        h.runAfterDelay(20,()->finish(h,e,lease,inventories,writes));
    }catch(Exception failure){lease.close();throw new RuntimeException(failure);}}
    private static void finish(GameTestHelper h,ProducerTests.Env e,SectorChunkLease lease,Map<BlockPos,CompoundTag> inventories,long writes){try{
        lease.keepAlive();require(ProducerTransaction.run(e,ProducerTransaction.Action.STATUS).writes()==0,"Installed L1 was not recognized by later producer preflight");
        var last=ProducerRevisions.plan("lumberyard",1,e.anchors.get("lumberyard"));BlockPos foreign=last.get(40000).position();BlockState original=e.level().getBlockState(foreign);
        e.level().setBlock(foreign,Blocks.DIAMOND_BLOCK.defaultBlockState(),SchematicPlacementService.BULK_FLAGS);var actual=e.snapshot();CompoundTag ledger=e.ledger();boolean refused=false;
        try{ProducerTransaction.run(e,ProducerTransaction.Action.APPLY);}catch(IOException expected){refused=true;}
        require(refused&&!Files.exists(e.file())&&ledger.equals(e.ledger())&&actual.equals(e.snapshot())&&inventories.equals(e.inventories()),"Foreign material escaped full grouped source checks");
        e.level().setBlock(foreign,original,SchematicPlacementService.BULK_FLAGS);var cost=e.spec().cost();CompoundTag oldNation=e.nation().save();
        var result=ProducerTransaction.run(e,ProducerTransaction.Action.APPLY);require(result.writes()>0&&e.territory().producerLevel()==2,"Actual installed L1 failed paid L2 upgrade");
        var expected=NationData.load(oldNation);cost.charge(expected);require(expected.save().equals(e.nation().save()),"Recognition upgrade charged incorrectly");
        require(inventories.equals(e.inventories())&&ProducerTransaction.run(e,ProducerTransaction.Action.STATUS).writes()==0,"Upgrade changed inventories or failed target recognition");
        for(String family:ProducerRevisions.FAMILIES)require(ProducerRevisions.token(family,2).equals(e.token(family)),"Producer target token missing");
        lease.close();CatanCraft.LOGGER.info("PLACEMENT53_FULL rotation={} families=3 checkedCells=135501 installWrites={} exactImmediate=true duplicateWrites=0 delayedSource=true foreignSourceRefused=true paidL2=true inventoryPreserved=true",e.rotation,writes);h.succeed();
    }catch(Exception failure){lease.close();throw new RuntimeException(failure);}}
    @GameTest(template="townhall_state_empty",batch="placement53_refusal",timeoutTicks=600)
    public static void invalid_rotation_and_native_write_refusal_remain_strict(GameTestHelper h)throws Exception{
        var level=h.getLevel();BlockPos sentinel=h.absolutePos(new BlockPos(2,2,2));level.setBlock(sentinel,Blocks.DIAMOND_BLOCK.defaultBlockState(),SchematicPlacementService.BULK_FLAGS);
        var invalid=SchematicPlacementService.placeAsset(level.getServer(),level.dimension().location().toString(),"quarry",new MapAnchor("invalid",sentinel.getX(),sentinel.getY(),sentinel.getZ(),90));
        require(!invalid.success()&&invalid.changedBlocks()==0&&level.getBlockState(sentinel).is(Blocks.DIAMOND_BLOCK),"Unsupported rotation wrote to fixture");
        var below=SchematicPlacementService.placeAsset(level.getServer(),level.dimension().location().toString(),"quarry",new MapAnchor("below",sentinel.getX(),level.getMinBuildHeight()-100,sentinel.getZ(),0));
        require(!below.success()&&below.changedBlocks()==0&&below.message().contains("setBlock=false")&&below.message().contains("target=")&&below.message().contains("actual=")&&level.getBlockState(sentinel).is(Blocks.DIAMOND_BLOCK),"Real native write refusal was hidden or lacked exact diagnostics");h.succeed();
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
