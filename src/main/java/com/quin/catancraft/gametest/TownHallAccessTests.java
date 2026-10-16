package com.quin.catancraft.gametest;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.map.MapAnchor;
import com.quin.catancraft.world.SchematicPlacementService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Regression detector for the owner's blocked rear exits / missing stair landings.
 * Runs ONLY in the isolated GameTest fixture. Nothing calls a purchase, touches
 * a saved player city, changes a schematic, or enables revision migration.
 *
 * The immutable R1 sources MUST reproduce the defects. Six trim removals and ten
 * floor additions are then an IN-MEMORY TEST CONTROL, not Blender's R2 artwork or
 * a releasable repair. This verifies that the collision/support probes detect
 * both the failures and their removal. Candidate R2 acceptance is still pending.
 * Native voxel collision shapes are used; this is not a simulated player input,
 * whole-interior route, economy, journal, restart, or installed-modpack test.
 */
@GameTestHolder("catancraft")
@PrefixGameTestTemplate(false)
public final class TownHallAccessTests {
    private static final int SAFE = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final double HALF_WIDTH = 0.30, HEIGHT = 1.80;

    @GameTest(template="townhall_state_empty", batch="townhall_access", timeoutTicks=300)
    public static void r1_th4_north_access_defects_and_native_control(GameTestHelper h) { check(h, 4, 0); }

    @GameTest(template="townhall_state_empty", batch="townhall_access", timeoutTicks=300)
    public static void r1_th4_south_access_defects_and_native_control(GameTestHelper h) { check(h, 4, 180); }

    @GameTest(template="townhall_state_empty", batch="townhall_access", timeoutTicks=300)
    public static void r1_th5_north_access_defects_and_native_control(GameTestHelper h) { check(h, 5, 0); }

    @GameTest(template="townhall_state_empty", batch="townhall_access", timeoutTicks=300)
    public static void r1_th5_south_access_defects_and_native_control(GameTestHelper h) { check(h, 5, 180); }

    private static void check(GameTestHelper h, int tier, int rotation) {
        try {
            BlockPos origin = h.absolutePos(new BlockPos(2, 2, 2));
            MapAnchor anchor = new MapAnchor("access-test", origin.getX()+37, origin.getY()+2,
                    origin.getZ()+(rotation == 0 ? 1 : 63), rotation);
            List<SchematicPlacementService.PlanCell> cells = SchematicPlacementService.plannedCells("th"+tier, anchor);
            h.assertTrue(cells.size() == 229125, "Missing full R1 Town Hall envelope");
            for (var c : cells) h.getLevel().setBlock(c.position(), c.state(), SAFE);
            // Open BOTH door halves. An operable door is not automatically a
            // usable doorway: surrounding headroom and floor must also pass.
            for (var c : cells) if (c.state().getBlock() instanceof DoorBlock)
                h.getLevel().setBlock(c.position(), c.state().setValue(DoorBlock.OPEN, true), SAFE);
            BlockPos manager = pos(cells, 37, 5, 17);
            CompoundTag managerBefore = h.getLevel().getBlockEntity(manager).saveWithFullMetadata().copy();
            int left = tier == 4 ? 7 : 5, right = tier == 4 ? 67 : 69, rear = tier == 4 ? 55 : 58;
            int blocked = blockedRearLeaves(h, cells, left, right, rear);
            int unsupported = unsupportedLandingCells(h, cells, left, right);
            h.assertTrue(blocked == 6, "R1 must reproduce exactly 6 blocked rear leaf approaches, got "+blocked);
            h.assertTrue(unsupported == 10, "R1 must reproduce exactly 10 missing stair-floor cells, got "+unsupported);
            // Front entrance is a real, unchanged positive control.
            for (int x = 36; x <= 38; x++)
                h.assertTrue(clearBodySweep(h, pos(cells,x,5,10),pos(cells,x,5,12)), "Front entrance unexpectedly blocked");
            CatanCraft.LOGGER.info("TH_ACCESS_R1 tier={} rotation={} blockedRearLeaves={} unsupportedLandingCells={}",
                    tier, rotation, blocked, unsupported);

            Map<BlockPos, BlockState> control = new LinkedHashMap<>();
            for (int center : new int[]{left+10, right-10}) for (int x=center-1; x<=center+1; x++) {
                BlockPos p = pos(cells,x,6,rear+1);
                h.assertTrue(h.getLevel().getBlockState(p).is(tier==4 ? Blocks.CHISELED_SANDSTONE : Blocks.CHISELED_QUARTZ_BLOCK),
                        "Original trim material changed at "+p);
                control.put(p, Blocks.AIR.defaultBlockState());
            }
            for (int start : new int[]{left+3, right-7}) for (int x=start; x<start+5; x++) {
                BlockPos p=pos(cells,x,14,34);
                h.assertTrue(h.getLevel().getBlockState(p).isAir(), "Original missing landing changed at "+p);
                control.put(p, Blocks.POLISHED_DIORITE.defaultBlockState());
            }
            h.assertTrue(control.size()==16 && !control.containsKey(manager), "Control mask changed");
            control.forEach((p,s)->h.getLevel().setBlock(p,s,SAFE));
            assertControl(h,cells,left,right,rear);

            // Detector sensitivity: reintroduce each defect independently.
            BlockPos head = pos(cells,left+10,6,rear+1);
            h.getLevel().setBlock(head,Blocks.STONE.defaultBlockState(),SAFE);
            h.assertTrue(blockedRearLeaves(h,cells,left,right,rear)>0, "Headroom detector missed reintroduced obstruction");
            h.getLevel().setBlock(head,control.get(head),SAFE);
            BlockPos floor = pos(cells,left+5,14,34);
            h.getLevel().setBlock(floor,Blocks.AIR.defaultBlockState(),SAFE);
            h.assertTrue(unsupportedLandingCells(h,cells,left,right)>0, "Support detector missed reintroduced gap");
            h.getLevel().setBlock(floor,control.get(floor),SAFE);
            assertExactControlEnvelope(h,cells,control,manager,managerBefore);

            h.runAfterDelay(5,()->{
                assertControl(h,cells,left,right,rear);
                assertExactControlEnvelope(h,cells,control,manager,managerBefore);
                CatanCraft.LOGGER.info("TH_ACCESS_CONTROL tier={} rotation={} patchCells=16 blockedRearLeaves=0 unsupportedLandingCells=0 delayed=PASS scope=TEST_CONTROL_ONLY",tier,rotation);
                h.succeed();
            });
        } catch (Exception e) {
            throw new RuntimeException("Access detector failed for TH"+tier+" rotation "+rotation,e);
        }
    }

    private static void assertControl(GameTestHelper h, List<SchematicPlacementService.PlanCell> cells,
            int left, int right, int rear) {
        h.assertTrue(blockedRearLeaves(h,cells,left,right,rear)==0,"Control rear-door passage still blocked");
        h.assertTrue(unsupportedLandingCells(h,cells,left,right)==0,"Control upper landing still missing");
        for (int start : new int[]{left+3,right-7}) for (int x=start;x<start+5;x++) {
            // Sweep from the top tread to the gallery, not merely a reachable
            // gallery target via an unrelated route. Sample floor support every
            // 1/8 block so the original 1-block gap cannot be jumped over.
            BlockPos a=pos(cells,x,15,33),b=pos(cells,x,15,35);
            h.assertTrue(clearBodySweep(h,a,b),"Upper landing has blocked upright headroom");
            for(int step=0;step<=16;step++) {
                double t=step/16.0;
                double cx=a.getX()+0.5+(b.getX()-a.getX())*t;
                double cz=a.getZ()+0.5+(b.getZ()-a.getZ())*t;
                h.assertTrue(supported(h,cx,a.getY(),cz),"Unsupported top-tread-to-gallery route");
            }
        }
    }

    private static int blockedRearLeaves(GameTestHelper h,List<SchematicPlacementService.PlanCell> cells,
            int left,int right,int rear) {
        int count=0;
        for(int center:new int[]{left+10,right-10}) for(int x=center-1;x<=center+1;x++)
            if(!clearBodySweep(h,pos(cells,x,5,rear-1),pos(cells,x,5,rear+1)))count++;
        return count;
    }

    private static int unsupportedLandingCells(GameTestHelper h,List<SchematicPlacementService.PlanCell> cells,
            int left,int right) {
        int count=0;
        for(int start:new int[]{left+3,right-7})for(int x=start;x<start+5;x++) {
            BlockPos p=pos(cells,x,15,34);
            if(!supported(h,p.getX()+0.5,p.getY(),p.getZ()+0.5))count++;
        }
        return count;
    }

    private static boolean clearBodySweep(GameTestHelper h,BlockPos a,BlockPos b) {
        return !collides(h,new AABB(Math.min(a.getX(),b.getX())+0.5-HALF_WIDTH,Math.min(a.getY(),b.getY())+0.001,
                Math.min(a.getZ(),b.getZ())+0.5-HALF_WIDTH,Math.max(a.getX(),b.getX())+0.5+HALF_WIDTH,
                Math.max(a.getY(),b.getY())+HEIGHT,Math.max(a.getZ(),b.getZ())+0.5+HALF_WIDTH));
    }

    private static boolean supported(GameTestHelper h,double x,double feet,double z) {
        return collides(h,new AABB(x-HALF_WIDTH,feet-0.05,z-HALF_WIDTH,x+HALF_WIDTH,feet,z+HALF_WIDTH));
    }

    private static boolean collides(GameTestHelper h,AABB box) {
        // One-cell margin catches shapes which extend beyond their block cell.
        for(int y=(int)Math.floor(box.minY)-1;y<=(int)Math.floor(box.maxY)+1;y++)
            for(int z=(int)Math.floor(box.minZ)-1;z<=(int)Math.floor(box.maxZ)+1;z++)
                for(int x=(int)Math.floor(box.minX)-1;x<=(int)Math.floor(box.maxX)+1;x++) {
                    BlockPos p=new BlockPos(x,y,z);
                    for(AABB shape:h.getLevel().getBlockState(p).getCollisionShape(h.getLevel(),p).toAabbs())
                        if(shape.move(x,y,z).intersects(box))return true;
                }
        return false;
    }

    private static BlockPos pos(List<SchematicPlacementService.PlanCell> cells,int x,int y,int z) {
        var cell=cells.get((y*65+z)*75+x);
        if(cell.localX()!=x||cell.localY()!=y||cell.localZ()!=z)throw new IllegalStateException("Plan order changed");
        return cell.position();
    }

    private static void assertExactControlEnvelope(GameTestHelper h,List<SchematicPlacementService.PlanCell> cells,
            Map<BlockPos,BlockState> control,BlockPos manager,CompoundTag managerBefore) {
        for(var c:cells) {
            BlockState expected=control.getOrDefault(c.position(),c.state());
            if(expected.getBlock() instanceof DoorBlock)expected=expected.setValue(DoorBlock.OPEN,true);
            h.assertTrue(h.getLevel().getBlockState(c.position()).equals(expected),"Control changed an unrelated cell at "+c.position());
        }
        h.assertTrue(h.getLevel().getBlockEntity(manager)!=null &&
                h.getLevel().getBlockEntity(manager).saveWithFullMetadata().equals(managerBefore),"Control changed manager NBT");
    }
}
