package com.quin.catancraft.world;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import java.io.IOException;
import java.util.*;

/** Native registry validation of actual exported geometry. No player/world acceptance inferred. */
@GameTestHolder("catancraft_city_a_p3") @PrefixGameTestTemplate(false)
public final class SectorCityAP3Tests {
    private interface Checked{void run()throws Exception;}
    private static void run(GameTestHelper h,Checked test){try{test.run();h.succeed();}catch(Exception e){throw new RuntimeException(e);}}
    @GameTest(template="townhall_state_empty",timeoutTicks=12000)
    public static void all_81_level_combinations_have_exact_exclusive_ownership(GameTestHelper h){run(h,()->{
        int cases=0;for(int n=0;n<3;n++)for(int e=0;e<3;e++)for(int s=0;s<3;s++)for(int w=0;w<3;w++){
            var levels=SectorCityAP3.levels(n,e,s,w);var open=SectorCityAP3.availableConnections(levels);var a=SectorCityAP3.assembly(levels,open);
            h.assertTrue(a.openCorners().size()==((n>0&&e>0?1:0)+(n>0&&w>0?1:0)+(s>0&&e>0?1:0)+(s>0&&w>0?1:0)),"Missing tier-pair seam");
            for(var p:SectorCityAP3.ownership().keySet())h.assertTrue(a.states().containsKey(p),"Missing ownership guard");cases++;
        }h.assertTrue(cases==81,"All 81 level vectors required");
    });}
    @GameTest(template="townhall_state_empty",timeoutTicks=12000)
    public static void north_upgrade_preserves_each_open_connection_mode(GameTestHelper h){run(h,()->{
        for(var open:List.of(Set.<String>of(),Set.of("NW"),Set.of("NE"),Set.of("NW","NE"))){
            var levels=SectorCityAP3.levels(1,open.contains("NE")?1:0,0,open.contains("NW")?2:0);var t=SectorCityAP3.transition(SectorContracts.Direction.NORTH,levels,open);
            h.assertTrue(t.plan().cells().size()==4539-6*open.size(),"North connected upgrade must omit open six-cell caps");h.assertTrue(t.afterConnections().equals(open),"Upgrade changed corner mode");
            h.assertTrue(t.writeOwners().values().stream().allMatch(d->d==SectorContracts.Direction.NORTH),"North upgrade acquired adjacent ownership");
            var after=SectorCityAP3.assembly(t.afterLevels(),t.afterConnections());for(var p:List.of(new BlockPos(-732,88,-849),new BlockPos(-732,88,-848),new BlockPos(-716,88,-839)))h.assertTrue(!after.states().get(p).isAir(),"Frozen North pier cap erased");
        }
    });}
    @GameTest(template="townhall_state_empty",timeoutTicks=12000)
    public static void all_side_upgrades_reverse_exactly_with_mixed_neighbors(GameTestHelper h){run(h,()->{
        int cases=0;for(var side:SectorContracts.Direction.values())for(int a=0;a<3;a++)for(int b=0;b<3;b++){
            var levels=new EnumMap<SectorContracts.Direction,Integer>(SectorContracts.Direction.class);levels.putAll(SectorCityAP3.levels(0,0,0,0));levels.put(side,1);var adjacent=new ArrayList<>(side.adjacent());adjacent.sort(Comparator.comparing(Enum::name));levels.put(adjacent.get(0),a);levels.put(adjacent.get(1),b);
            var open=SectorCityAP3.availableConnections(levels);var before=SectorCityAP3.assembly(levels,open);var t=SectorCityAP3.transition(side,levels,open);var after=SectorCityAP3.assembly(t.afterLevels(),t.afterConnections());var replay=new HashMap<>(before.states());
            for(var c:t.plan().cells()){h.assertTrue(replay.get(c.pos()).equals(c.before()),"Upgrade wrong exact source");replay.put(c.pos(),c.after());}h.assertTrue(replay.equals(after.states()),"Upgrade differs from target assembly");
            for(var c:t.plan().cells())replay.put(c.pos(),c.before());h.assertTrue(replay.equals(before.states()),"Reverse changed other sector or support");
            h.assertTrue(t.writeOwners().values().stream().allMatch(d->d==side),"Upgrade modifies neighbor ownership");cases++;
        }h.assertTrue(cases==36,"All 36 mixed-neighbor upgrades required");
    });}
    @GameTest(template="townhall_state_empty",timeoutTicks=12000)
    public static void purchase_coordinates_neighbor_caps_and_retains_rear_access(GameTestHelper h){run(h,()->{
        var levels=SectorCityAP3.levels(2,0,1,0);var t=SectorCityAP3.transition(SectorContracts.Direction.EAST,levels,Set.of());
        h.assertTrue(t.afterConnections().equals(Set.of("NE","SE")),"East purchase must coordinate both supported seams");
        for(var d:List.of(SectorContracts.Direction.NORTH,SectorContracts.Direction.SOUTH))h.assertTrue(t.writeOwners().values().stream().filter(x->x==d).count()==6,"Each neighbor retains exclusive six-cell terminal ownership");
        for(var p:SectorCityAP3.ownership().keySet())h.assertTrue(!(p.getX()>=-770&&p.getX()<=-730&&p.getZ()>=-666),"South rear resource corridor written");
        h.assertTrue(SectorPriceCatalog.cost(SectorContracts.Direction.EAST,0).money()==1500&&SectorPriceCatalog.cost(SectorContracts.Direction.EAST,1).money()==4000,"Whole-side provisional charge changed");
    });}
    @GameTest(template="townhall_state_empty",timeoutTicks=12000)
    public static void unsupported_connection_or_tier_refuses(GameTestHelper h){run(h,()->{
        refuse(()->SectorCityAP3.assembly(SectorCityAP3.levels(1,0,0,0),Set.of("NE")));
        refuse(()->SectorCityAP3.assembly(SectorCityAP3.levels(1,1,1,1),Set.of("UNKNOWN")));
        refuse(()->SectorCityAP3.transition(SectorContracts.Direction.NORTH,SectorCityAP3.levels(2,1,1,1),Set.of()));
        refuse(()->SectorCityAP3.levels(3,0,0,0));
    });}
    private static void refuse(Checked c)throws Exception{try{c.run();}catch(IOException expected){return;}throw new AssertionError("Expected strict P3 contract refusal");}
}
