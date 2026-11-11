package com.quin.catancraft.world;

import com.quin.catancraft.data.ResourceType;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import java.io.IOException;
import java.util.*;
import static com.quin.catancraft.world.VehicleBay45.*;

/** Pure policy fixtures only: synthetic positions and geometry hashes do not register a real bay. */
@GameTestHolder("catancraft_vehicle45") @PrefixGameTestTemplate(false)
public final class VehicleBay45Tests {
    private static final UUID ACTOR = UUID.fromString("45000000-0000-0000-0000-000000000001");
    private static final UUID NATION = UUID.fromString("45000000-0000-0000-0000-000000000002");
    private static final String VEHICLE = "superbwarfare:lav_150";
    private static final Position POSITION = new Position(0.25,0.1,0.75);
    private static final Cell SPAWN = new Cell(0,0,0), BOARD = new Cell(1,0,0), EXIT = new Cell(2,0,0), FLOOR = new Cell(0,-1,0);
    private static Facility facility(int rotation) { return new Facility("synthetic", "plot_1", NATION,"minecraft:overworld","fixture-only","a".repeat(64),new Cell(100,80,100),rotation,new Cell(90,81,100),1); }
    private static Bay bay(String id, Facility f) { return new Bay(id,f,VEHICLE,"b".repeat(64),POSITION,90,Set.of(FLOOR),Set.of(SPAWN,BOARD,EXIT),Set.of(BOARD),Set.of(EXIT)); }
    private static Presence presence(Facility f) { return new Presence(ACTOR,NATION,f.dimension(),f,true,true,true,true,false); }
    private static ShopPurchase40.Price price() { return new ShopPurchase40.Price(8500,Map.of(ResourceType.STEEL,50L)); }
    private static class ProbeFixture implements Probe {
        Set<Cell> blocked = new HashSet<>(), unsupported = new HashSet<>(), unloaded = new HashSet<>();
        boolean geometry = true;
        public boolean geometryMatches(Bay b) { return geometry; }
        public boolean loadedAndOwned(Cell c,Facility f) { return !unloaded.contains(c); }
        public boolean clearOfBlocksFluidsAndEntities(Cell c) { return !blocked.contains(c); }
        public boolean fullSupport(Cell c) { return !unsupported.contains(c); }
    }
    @GameTest(template="townhall_state_empty",timeoutTicks=100)
    public static void exact_anchor_rotation_and_heading_no_player_relative_spawn(GameTestHelper h) throws Exception {
        var a=bay("one",facility(0));var b=bay("one",facility(180));
        h.assertTrue(a.world(EXIT).equals(new Cell(102,80,100))&&b.world(EXIT).equals(new Cell(98,80,100)),"Factory transform changed");
        h.assertTrue(a.worldSpawn().equals(new WorldPosition(100.25,80.1,100.75))
                &&b.worldSpawn().equals(new WorldPosition(100.75,80.1,100.25)),"Fractional native spawn was rounded or rotated incorrectly");
        h.assertTrue(a.worldHeading()==90&&b.worldHeading()==270,"Heading did not rotate with factory");
        refuse(()->new Cell(1,0,1).transform(new Cell(0,0,0),90));
        refuse(()->new Cell(1,0,0).transform(new Cell(Integer.MAX_VALUE,0,0),0));h.succeed();
    }
    @GameTest(template="townhall_state_empty",timeoutTicks=100)
    public static void missing_unknown_foreign_duplicate_registry_never_falls_back(GameTestHelper h) throws Exception {
        var f=facility(0);var b=bay("one",f);var p=new ProbeFixture();
        refuse(()->select(f,List.of(),VEHICLE,p));refuse(()->select(f,List.of(b),"superbwarfare:unknown",p));
        refuse(()->select(f,List.of(b,b),VEHICLE,p));refuse(()->select(f,List.of(bay("other",facility(180))),VEHICLE,p));h.succeed();
    }
    @GameTest(template="townhall_state_empty",timeoutTicks=100)
    public static void every_clearance_support_departure_and_geometry_failure_refuses(GameTestHelper h) throws Exception {
        var f=facility(0);var b=bay("one",f);
        for(Cell c:b.clearance()) {var p=new ProbeFixture();p.blocked.add(b.world(c));refuse(()->select(f,List.of(b),VEHICLE,p));}
        for(Cell c:List.of(FLOOR,SPAWN,BOARD,EXIT)) {var p=new ProbeFixture();p.unloaded.add(b.world(c));refuse(()->select(f,List.of(b),VEHICLE,p));}
        var floor=new ProbeFixture();floor.unsupported.add(b.world(FLOOR));refuse(()->select(f,List.of(b),VEHICLE,floor));
        var nativeGeometry=new ProbeFixture();nativeGeometry.geometry=false;refuse(()->select(f,List.of(b),VEHICLE,nativeGeometry));h.succeed();
    }
    @GameTest(template="townhall_state_empty",timeoutTicks=100)
    public static void deterministic_selection_and_confirmation_cannot_switch_bays(GameTestHelper h) throws Exception {
        var f=facility(0);var first=bay("first",f);var next=new Bay("next",f,VEHICLE,"b".repeat(64),new Position(4.5,0.1,0.5),90,Set.of(new Cell(4,-1,0)),Set.of(new Cell(4,0,0),new Cell(5,0,0),new Cell(6,0,0)),Set.of(new Cell(5,0,0)),Set.of(new Cell(6,0,0)));
        var p=new ProbeFixture();var registry=List.of(first,next);var q=quote(presence(f),registry,VEHICLE,price(),10,p);
        h.assertTrue(q.bay().equals(first),"Stable registry ordering changed");p.blocked.add(first.world(SPAWN));
        h.assertTrue(select(f,registry,VEHICLE,p).equals(next),"Explicit clear alternative was not selected");
        refuse(()->confirm(q,presence(f),11,registry,p));h.succeed();
    }
    @GameTest(template="townhall_state_empty",timeoutTicks=100)
    public static void physical_authority_completed_source_and_pending_work_are_rechecked(GameTestHelper h) throws Exception {
        var f=facility(0);var b=bay("one",f);var p=new ProbeFixture();var q=quote(presence(f),List.of(b),VEHICLE,price(),10,p);
        for(int i=0;i<5;i++) {var c=new Presence(ACTOR,NATION,f.dimension(),f,i!=0,i!=1,i!=2,i!=3,i==4);refuse(()->confirm(q,c,11,List.of(b),p));}
        var foreign=new Presence(ACTOR,UUID.randomUUID(),f.dimension(),f,true,true,true,true,false);refuse(()->confirm(q,foreign,11,List.of(b),p));
        var dimension=new Presence(ACTOR,NATION,"minecraft:the_nether",f,true,true,true,true,false);refuse(()->confirm(q,dimension,11,List.of(b),p));h.succeed();
    }
    @GameTest(template="townhall_state_empty",timeoutTicks=100)
    public static void actor_facility_registration_and_expiry_changes_invalidate_quote(GameTestHelper h) throws Exception {
        var f=facility(0);var b=bay("one",f);var p=new ProbeFixture();var q=quote(presence(f),List.of(b),VEHICLE,price(),100,p);
        confirm(q,presence(f),1299,List.of(b),p);
        for(long tick:new long[]{-1,99,1300,Long.MAX_VALUE})refuse(()->confirm(q,presence(f),tick,List.of(b),p));
        var foreign=new Presence(UUID.randomUUID(),NATION,f.dimension(),f,true,true,true,true,false);refuse(()->confirm(q,foreign,101,List.of(b),p));
        refuse(()->confirm(q,presence(f),101,List.of(bay("replacement",f)),p));
        var rotated=facility(180);refuse(()->confirm(q,presence(rotated),101,List.of(bay("one",rotated)),p));
        var nearEnd=quote(presence(f),List.of(b),VEHICLE,price(),Long.MAX_VALUE-1199,p);confirm(nearEnd,presence(f),Long.MAX_VALUE,List.of(b),p);h.succeed();
    }
    @GameTest(template="townhall_state_empty",timeoutTicks=100)
    public static void defensive_mask_and_historical_price_copies_remain_immutable(GameTestHelper h) throws Exception {
        var f=facility(0);var mask=new HashSet<>(Set.of(SPAWN,BOARD,EXIT));var b=new Bay("one",f,VEHICLE,"b".repeat(64),POSITION,90,Set.of(FLOOR),mask,Set.of(BOARD),Set.of(EXIT));mask.clear();
        var costs=new HashMap<ResourceType,Long>();costs.put(ResourceType.STEEL,50L);var original=new ShopPurchase40.Price(8500,costs);costs.put(ResourceType.STEEL,1L);
        var q=quote(presence(f),List.of(b),VEHICLE,original,1,new ProbeFixture());
        h.assertTrue(b.clearance().size()==3&&q.price().money()==8500&&q.price().resources().get(ResourceType.STEEL)==50L,"Caller mutated frozen masks/price");h.succeed();
    }
    @GameTest(template="townhall_state_empty",timeoutTicks=100)
    public static void incomplete_unbounded_or_contradictory_masks_are_not_certificates(GameTestHelper h) throws Exception {
        var f=facility(0);
        for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,257})refuse(()->new Position(invalid,0,0));
        refuse(()->new Bay("one",f,VEHICLE,"missing",POSITION,90,Set.of(FLOOR),Set.of(SPAWN,BOARD,EXIT),Set.of(BOARD),Set.of(EXIT)));
        refuse(()->new Bay("one",f,VEHICLE,"b".repeat(64),POSITION,90,Set.of(FLOOR),Set.of(SPAWN,BOARD),Set.of(BOARD),Set.of(EXIT)));
        refuse(()->new Bay("one",f,VEHICLE,"b".repeat(64),POSITION,90,Set.of(SPAWN),Set.of(SPAWN,BOARD,EXIT),Set.of(BOARD),Set.of(EXIT)));
        refuse(()->new Bay("one",f,VEHICLE,"b".repeat(64),POSITION,90,Set.of(FLOOR),Set.of(SPAWN,BOARD,EXIT,new Cell(257,0,0)),Set.of(BOARD),Set.of(EXIT)));h.succeed();
    }
    @FunctionalInterface private interface Action { void run() throws Exception; }
    private static void refuse(Action action) throws Exception { try {action.run();}catch(IOException|IllegalArgumentException|ArithmeticException expected){return;}throw new AssertionError("Unsafe bay policy accepted"); }
}
