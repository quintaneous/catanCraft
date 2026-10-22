package com.quin.catancraft.world;

import com.quin.catancraft.data.ResourceType;
import com.quin.catancraft.economy.EconomyCost;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.io.IOException;
import java.util.*;

/** Labeled policy fixtures only: these tiny plans are not approved sector artwork or prices. */
@GameTestHolder("catancraft_sectors") @PrefixGameTestTemplate(false)
public final class SectorContractTests {
    private static final BlockPos CELL=new BlockPos(0,80,0),ROAD=new BlockPos(3,80,0);
    private static final EconomyCost PRICE=new EconomyCost(17,Map.of(ResourceType.STONE,3L));
    private static SectorTransaction.Cell cell(){return new SectorTransaction.Cell(CELL,Blocks.AIR.defaultBlockState(),Blocks.STONE.defaultBlockState());}
    private static SectorTransaction.Plan plan(String target,List<SectorTransaction.Cell> cells){return new SectorTransaction.Plan("north","fixture_source",target,0,1,cells,List.of(new SectorTransaction.Read(ROAD,Blocks.STONE.defaultBlockState())));}
    private static Map<SectorContracts.Corner,SectorContracts.Direction> corners(){return Map.of(
        SectorContracts.Corner.NORTH_WEST,SectorContracts.Direction.NORTH,SectorContracts.Corner.NORTH_EAST,SectorContracts.Direction.EAST,
        SectorContracts.Corner.SOUTH_EAST,SectorContracts.Direction.SOUTH,SectorContracts.Corner.SOUTH_WEST,SectorContracts.Direction.WEST);}
    private static Map<SectorContracts.Direction,SectorContracts.Neighbor> neighbors(String receipt){return Map.of(
        SectorContracts.Direction.EAST,new SectorContracts.Neighbor(1,receipt),SectorContracts.Direction.WEST,new SectorContracts.Neighbor(0,""));}
    private static SectorContracts.Contract contract(String target,String receipt,EconomyCost price)throws IOException {
        return new SectorContracts.Contract("isolated-policy-fixture","a",SectorContracts.Direction.NORTH,plan(target,List.of(cell())),corners(),neighbors(receipt),Set.of(ROAD),price);
    }
    @FunctionalInterface private interface Attempt{void run()throws IOException;}
    private static void refuses(GameTestHelper h,Attempt action)throws IOException {
        boolean refused=false;try{action.run();}catch(IOException expected){refused=true;}h.assertTrue(refused,"Invalid sector policy accepted");
    }
    @GameTest(template="townhall_state_empty",timeoutTicks=100)
    public static void compass_is_absolute_for_both_city_rotations(GameTestHelper h)throws IOException {
        for(int cityRotation:new int[]{0,180}){
            h.assertTrue(SectorContracts.Direction.parse("north").dz==-1&&SectorContracts.Direction.NORTH.dx==0,"North was rotated with city "+cityRotation);
            h.assertTrue(SectorContracts.Direction.EAST.dx==1&&SectorContracts.Direction.EAST.dz==0,"East was rotated");
            h.assertTrue(SectorContracts.Direction.SOUTH.dz==1&&SectorContracts.Direction.WEST.dx==-1,"South/west compass changed");
        }
        refuses(h,()->SectorContracts.Direction.parse("front"));h.succeed();
    }
    @GameTest(template="townhall_state_empty",timeoutTicks=100)
    public static void shared_cells_roads_and_missing_corner_ownership_refuse(GameTestHelper h)throws IOException {
        refuses(h,()->new SectorContracts.Contract("fixture","a",SectorContracts.Direction.NORTH,plan("target",List.of(cell(),cell())),corners(),neighbors("e1"),Set.of(ROAD),PRICE));
        refuses(h,()->new SectorContracts.Contract("fixture","a",SectorContracts.Direction.NORTH,plan("target",List.of(cell())),corners(),neighbors("e1"),Set.of(CELL,ROAD),PRICE));
        refuses(h,()->new SectorContracts.Contract("fixture","a",SectorContracts.Direction.NORTH,plan("target",List.of(cell())),Map.of(),neighbors("e1"),Set.of(ROAD),PRICE));
        var invalid=new EnumMap<SectorContracts.Corner,SectorContracts.Direction>(SectorContracts.Corner.class);invalid.putAll(corners());invalid.put(SectorContracts.Corner.NORTH_WEST,SectorContracts.Direction.SOUTH);
        refuses(h,()->new SectorContracts.Contract("fixture","a",SectorContracts.Direction.NORTH,plan("target",List.of(cell())),invalid,neighbors("e1"),Set.of(ROAD),PRICE));
        var north=contract("fixture_target","east_original",PRICE);
        var eastPlan=new SectorTransaction.Plan("east","fixture_source","fixture_target",0,1,List.of(cell()),List.of(new SectorTransaction.Read(ROAD,Blocks.STONE.defaultBlockState())));
        var east=new SectorContracts.Contract("isolated-policy-fixture","a",SectorContracts.Direction.EAST,eastPlan,corners(),
            Map.of(SectorContracts.Direction.NORTH,new SectorContracts.Neighbor(0,""),SectorContracts.Direction.SOUTH,new SectorContracts.Neighbor(0,"")),Set.of(ROAD),PRICE);
        refuses(h,()->SectorContracts.requireDisjoint(List.of(north,east)));
        h.succeed();
    }
    @GameTest(template="townhall_state_empty",timeoutTicks=100)
    public static void north_can_preserve_incident_ownership_without_inventing_south(GameTestHelper h)throws IOException {
        var north=Map.of(SectorContracts.Corner.NORTH_WEST,SectorContracts.Direction.NORTH,SectorContracts.Corner.NORTH_EAST,SectorContracts.Direction.NORTH);
        var c=new SectorContracts.Contract("fixture","a",SectorContracts.Direction.NORTH,plan("target",List.of(cell())),north,neighbors("e1"),Set.of(ROAD),PRICE);
        h.assertTrue(c.corners().size()==2&&!c.corners().containsKey(SectorContracts.Corner.SOUTH_EAST),"Unapproved south corner invented");
        refuses(h,()->new SectorContracts.Contract("fixture","a",SectorContracts.Direction.NORTH,plan("target",List.of(cell())),Map.of(SectorContracts.Corner.NORTH_WEST,SectorContracts.Direction.NORTH),neighbors("e1"),Set.of(ROAD),PRICE));
        h.succeed();
    }
    @GameTest(template="townhall_state_empty",timeoutTicks=100)
    public static void quote_rejects_same_level_new_receipt_owner_price_and_revision(GameTestHelper h)throws IOException {
        UUID owner=UUID.randomUUID();var quote=new SectorContracts.Quote(owner,contract("fixture_target","east_original",PRICE));
        quote.requireCurrent(owner,contract("fixture_target","east_original",PRICE));
        refuses(h,()->quote.requireCurrent(owner,contract("fixture_target","east_new_receipt_same_level",PRICE)));
        refuses(h,()->quote.requireCurrent(UUID.randomUUID(),contract("fixture_target","east_original",PRICE)));
        refuses(h,()->quote.requireCurrent(owner,contract("different_target_revision","east_original",PRICE)));
        refuses(h,()->quote.requireCurrent(owner,contract("fixture_target","east_original",new EconomyCost(18,PRICE.resources()))));
        refuses(h,()->quote.requireCurrent(owner,contract("fixture_target","east_original",new EconomyCost(17,Map.of(ResourceType.STONE,4L)))));
        h.succeed();
    }
    @GameTest(template="townhall_state_empty",timeoutTicks=100)
    public static void aggregate_price_exact_unknown_and_overflow_refuse(GameTestHelper h)throws IOException {
        var sum=SectorContracts.aggregate(List.of(PRICE,new EconomyCost(5,Map.of(ResourceType.STONE,2L,ResourceType.WOOD,1L))));
        h.assertTrue(sum.money()==22&&sum.resources().get(ResourceType.STONE)==5&&sum.resources().get(ResourceType.WOOD)==1,"Aggregate cost changed");
        refuses(h,()->SectorContracts.aggregate(Arrays.asList(PRICE,null)));
        refuses(h,()->SectorContracts.aggregate(List.of(new EconomyCost(Long.MAX_VALUE,Map.of()),PRICE)));
        h.succeed();
    }
    private static List<SectorContracts.Contract> partition(boolean overlapRead,boolean overlapPreservedUnion,boolean duplicateWrite)throws IOException {
        List<SectorContracts.Contract> out=new ArrayList<>();
        for(var side:SectorContracts.Direction.values()){
            BlockPos pos=new BlockPos(10*side.ordinal(),80,10);List<SectorTransaction.Cell> cells=new ArrayList<>();
            if(duplicateWrite&&side==SectorContracts.Direction.EAST)pos=new BlockPos(0,80,10);
            cells.add(new SectorTransaction.Cell(pos,Blocks.AIR.defaultBlockState(),Blocks.STONE.defaultBlockState()));
            List<SectorTransaction.Read> reads=new ArrayList<>();reads.add(new SectorTransaction.Read(ROAD,Blocks.STONE.defaultBlockState()));
            if(side==SectorContracts.Direction.EAST){
                BlockPos northCell=new BlockPos(0,80,10);
                if(overlapRead)reads.add(new SectorTransaction.Read(northCell,Blocks.AIR.defaultBlockState()));
                if(overlapPreservedUnion)cells.add(new SectorTransaction.Cell(northCell,Blocks.AIR.defaultBlockState(),Blocks.AIR.defaultBlockState()));
            }
            Map<SectorContracts.Direction,SectorContracts.Neighbor> adjacent=new EnumMap<>(SectorContracts.Direction.class);
            for(var neighbor:side.adjacent())adjacent.put(neighbor,new SectorContracts.Neighbor(0,""));
            var plan=new SectorTransaction.Plan(side.name().toLowerCase(Locale.ROOT),"fixture_source","fixture_target",0,1,List.copyOf(cells),List.copyOf(reads));
            out.add(new SectorContracts.Contract("partition_fixture","a",side,plan,corners(),adjacent,Set.of(ROAD),PRICE));
        }
        return out;
    }
    @GameTest(template="townhall_state_empty",timeoutTicks=100)
    public static void four_sector_partition_rejects_other_side_reads_and_preserved_union(GameTestHelper h)throws IOException {
        var valid=partition(false,false,false);SectorContracts.requireDisjoint(valid);
        refuses(h,()->SectorContracts.requireDisjoint(valid.subList(0,3)));
        refuses(h,()->SectorContracts.requireDisjoint(partition(true,false,false)));
        refuses(h,()->SectorContracts.requireDisjoint(partition(false,true,false)));
        refuses(h,()->SectorContracts.requireDisjoint(partition(false,false,true)));
        h.succeed();
    }
}
