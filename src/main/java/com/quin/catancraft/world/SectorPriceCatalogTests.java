package com.quin.catancraft.world;

import com.quin.catancraft.data.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.*;
import java.io.IOException;
import java.util.*;

/** Actual centralized prices; no geometry approval, world editing or fixture price substitution. */
@GameTestHolder("catancraft_sector_prices") @PrefixGameTestTemplate(false)
public final class SectorPriceCatalogTests {
    @GameTest(template="townhall_state_empty",batch="prices01",timeoutTicks=100)
    public static void all_four_compass_sides_have_identical_versioned_incremental_prices(GameTestHelper h){try{
        for(var side:SectorContracts.Direction.values()){
            var basic=SectorPriceCatalog.quote(side,0);var reinforced=SectorPriceCatalog.quote(side,1);
            h.assertTrue(basic.version().equals(SectorPriceCatalog.VERSION)&&reinforced.version().equals(basic.version())&&basic.side()==side&&reinforced.side()==side&&basic.sourceLevel()==0&&basic.targetLevel()==1&&reinforced.sourceLevel()==1&&reinforced.targetLevel()==2,"Catalog version/side/transition binding lost");
            h.assertTrue(basic.cost().money()==1500&&basic.cost().resources().equals(Map.of(ResourceType.WOOD,15L,ResourceType.STONE,30L)),"Whole Basic price differs by side");
            h.assertTrue(reinforced.cost().money()==4000&&reinforced.cost().resources().equals(Map.of(ResourceType.CONCRETE,50L,ResourceType.STEEL,15L)),"Reinforced increment recharges Basic or differs by side");
        }h.succeed();
    }catch(Exception e){throw new RuntimeException(e);}}
    @GameTest(template="townhall_state_empty",batch="prices02",timeoutTicks=100)
    public static void unknown_side_and_every_unsupported_level_refuse_without_free_fallback(GameTestHelper h){try{
        refuse(()->SectorPriceCatalog.cost(null,0));for(int level:new int[]{Integer.MIN_VALUE,-1,2,3,Integer.MAX_VALUE})for(var side:SectorContracts.Direction.values())refuse(()->SectorPriceCatalog.cost(side,level));h.succeed();
    }catch(Exception e){throw new RuntimeException(e);}}
    @GameTest(template="townhall_state_empty",batch="prices03",timeoutTicks=100)
    public static void returned_material_maps_cannot_mutate_shared_catalog(GameTestHelper h){try{
        for(int level=0;level<=1;level++){var cost=SectorPriceCatalog.cost(SectorContracts.Direction.NORTH,level);Map<ResourceType,Long> before=Map.copyOf(cost.resources());boolean refused=false;try{cost.resources().put(ResourceType.WOOD,1L);}catch(UnsupportedOperationException expected){refused=true;}h.assertTrue(refused&&before.equals(SectorPriceCatalog.cost(SectorContracts.Direction.EAST,level).resources()),"Returned materials mutate approved shared prices");}h.succeed();
    }catch(Exception e){throw new RuntimeException(e);}}
    @GameTest(template="townhall_state_empty",batch="prices04",timeoutTicks=100)
    public static void exact_basic_plus_reinforced_charge_is_5500_and_original_materials_once(GameTestHelper h){try{
        NationData n=nation(10000);n.addResource(ResourceType.WOOD,100);n.addResource(ResourceType.STONE,100);n.addResource(ResourceType.CONCRETE,100);n.addResource(ResourceType.STEEL,100);
        SectorPriceCatalog.cost(SectorContracts.Direction.WEST,0).charge(n);SectorPriceCatalog.cost(SectorContracts.Direction.WEST,1).charge(n);
        h.assertTrue(n.treasury()==4500&&n.resource(ResourceType.WOOD)==85&&n.resource(ResourceType.STONE)==70&&n.resource(ResourceType.CONCRETE)==50&&n.resource(ResourceType.STEEL)==85,"Incremental total incorrectly charges Basic twice");h.succeed();
    }catch(Exception e){throw new RuntimeException(e);}}
    @GameTest(template="townhall_state_empty",batch="prices05",timeoutTicks=100)
    public static void insufficient_money_cannot_consume_any_material(GameTestHelper h){try{
        for(int from=0;from<=1;from++){var cost=SectorPriceCatalog.cost(SectorContracts.Direction.SOUTH,from);NationData n=nation(cost.money()-1);cost.resources().forEach((type,amount)->n.addResource(type,amount));CompoundTag before=n.save();h.assertTrue(!cost.canAfford(n),"Short money accepted");refuseCharge(()->cost.charge(n));h.assertTrue(before.equals(n.save()),"Short-money charge consumed resources");}h.succeed();
    }catch(Exception e){throw new RuntimeException(e);}}
    @GameTest(template="townhall_state_empty",batch="prices06",timeoutTicks=100)
    public static void each_insufficient_material_cannot_consume_money_or_other_materials(GameTestHelper h){try{
        for(int from=0;from<=1;from++){var cost=SectorPriceCatalog.cost(SectorContracts.Direction.EAST,from);for(var missing:cost.resources().keySet()){NationData n=nation(cost.money());cost.resources().forEach((type,amount)->n.addResource(type,amount-(type==missing?1:0)));CompoundTag before=n.save();h.assertTrue(!cost.canAfford(n),"Short "+missing+" accepted");refuseCharge(()->cost.charge(n));h.assertTrue(before.equals(n.save()),"Short-material charge partially consumed money/other materials");}}h.succeed();
    }catch(Exception e){throw new RuntimeException(e);}}
    @GameTest(template="townhall_state_empty",batch="prices07",timeoutTicks=100)
    public static void historical_cost_decoder_keeps_recorded_amounts_instead_of_current_schedule(GameTestHelper h){try{
        CompoundTag recorded=new CompoundTag();recorded.putLong("money",101);recorded.putLong("stone",7);var historical=SectorTransaction.historicalCost(recorded);h.assertTrue(historical.money()==101&&historical.resources().equals(Map.of(ResourceType.STONE,7L))&&SectorPriceCatalog.northP2(0).money()==1500,"Historical cost replaced with new catalog");h.succeed();
    }catch(Exception e){throw new RuntimeException(e);}}
    private static NationData nation(long money){return new NationData(UUID.randomUUID(),"DEF04 price fixture",UUID.randomUUID(),money);}
    private interface Checked{void run()throws IOException;}private static void refuse(Checked c)throws IOException{try{c.run();}catch(IOException expected){return;}throw new AssertionError("Unapproved price did not refuse");}
    private static void refuseCharge(Runnable charge){try{charge.run();}catch(IllegalStateException expected){return;}throw new AssertionError("Unaffordable charge accepted");}
}
