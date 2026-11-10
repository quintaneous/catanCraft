package com.quin.catancraft.world;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.quin.catancraft.data.*;
import com.quin.catancraft.map.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import static net.minecraft.commands.Commands.literal;

/** Native geometry and refusal tests in the disposable shop world. Detached actors are
 * intentionally NOT authenticated clients; these tests do not certify positive in-game
 * interaction, a loaded TaCZ builder, or actual click-to-delivery client acceptance. */
@GameTestHolder("catancraft_shop40") @PrefixGameTestTemplate(false)
public final class ShopFacility40Tests {
    private ShopFacility40Tests() {}

    @GameTest(template="townhall_state_empty",batch="shop40_facility_01",timeoutTicks=200)
    public static void all_six_actual_plot_rotations_match_pinned_factory_positions(GameTestHelper h) throws Exception {
        controlled(h);
        int[][] expected = {{-805,82,-771},{-805,82,-731},{-695,82,-737},
                {-695,82,271},{-695,82,231},{-805,82,237}};
        int row = 0;
        for (String city : List.of("a", "i")) {
            TerritoryDefinition definition = MapDefinitionManager.territory(city);
            h.assertTrue(definition != null, "Canonical test map is absent");
            for (String plot : IndustrialPlotService.INDUSTRIAL_PLOTS) {
                MapAnchor anchor = IndustrialPlotService.anchorFor(BuildingType.WEAPONS_FACTORY, plot, definition);
                int rotation = city.equals("a") ? (plot.equals("plot_4") ? 180 : 0) : (plot.equals("plot_4") ? 0 : 180);
                h.assertTrue(anchor != null && anchor.rotation() == rotation, "The actual plot4 factory flip was lost");
                List<SchematicPlacementService.PlanCell> plan = BasicConstructionAssets.plan("weapons_factory", anchor);
                for (int[] local : new int[][]{{19,3,18},{19,2,18},{19,2,17},{19,3,17},{19,1,17},{24,2,13}}) {
                    int index = (local[1]*31 + local[2])*31 + local[0];
                    h.assertTrue(ShopFacility40.local(anchor,local[0],local[1],local[2]).equals(plan.get(index).position()),
                            "Counter context transform differs from the native factory placement plan");
                }
                int[] wanted = expected[row++];
                h.assertTrue(ShopFacility40.local(anchor,19,3,18).equals(new BlockPos(wanted[0],wanted[1],wanted[2])),
                        "Counter moved from the surveyed exact plot/orientation position");
            }
        }
        refuse(() -> ShopFacility40.local(new MapAnchor("unsupported",0,81,0,90),19,3,18));
        refuse(() -> ShopFacility40.local(null,19,3,18));
        h.succeed();
    }

    @GameTest(template="townhall_state_empty",batch="shop40_facility_02",timeoutTicks=200)
    public static void pinned_sparse_counter_context_has_native_standing_support_and_no_inventory(GameTestHelper h) throws Exception {
        controlled(h);
        var source = BasicConstructionAssets.load("weapons_factory");
        h.assertTrue(source.width()==31 && source.height()==47 && source.length()==31
                && source.ax()==29 && source.ay()==2 && source.az()==15, "Pinned factory anchor/envelope changed");
        h.assertTrue(source.at(19,3,18).is(Blocks.CRAFTING_TABLE) && !source.at(19,3,18).hasBlockEntity(),
                "Shop counter is no longer the authored block-entity-free crafting table");
        h.assertTrue(source.at(19,2,17).isAir() && source.at(19,3,17).isAir(), "Authored standing feet/head are obstructed");
        BlockPos probe = h.absolutePos(new BlockPos(0,3,0));
        h.assertTrue(source.at(19,1,17).isFaceSturdy(h.getLevel(),probe,Direction.UP), "Authored standing floor has no full native top support");
        h.assertTrue(source.at(24,2,13).getBlock() instanceof DoorBlock
                && !source.at(24,2,13).getValue(DoorBlock.OPEN), "Surveyed route no longer passes a normal closed native door");
        h.assertTrue(ShopFacility40.REACH_SQUARED==16.0, "Provisional four-block eye reach changed without acceptance updates");
        h.succeed();
    }

    @GameTest(template="townhall_state_empty",batch="shop40_facility_03",timeoutTicks=200)
    public static void native_open_door_route_and_counter_ray_work_in_both_rotations(GameTestHelper h) throws Exception {
        controlled(h);
        ServerLevel level = h.getLevel(); var source = BasicConstructionAssets.load("weapons_factory");
        List<int[]> route = new ArrayList<>();
        for(int x=29;x>=25;x--)route.add(new int[]{x,15});
        for(int z=14;z>=13;z--)route.add(new int[]{25,z});
        for(int x=24;x>=19;x--)route.add(new int[]{x,13});
        for(int z=14;z<=17;z++)route.add(new int[]{19,z});
        h.assertTrue(route.size()==17,"Surveyed approach is not sixteen horizontal steps");
        Map<BlockPos,BlockState> restore = new LinkedHashMap<>();
        try {
            for(int rotation : new int[]{0,180}) {
                BlockPos origin = h.absolutePos(new BlockPos(32 + rotation/180*32,8,32));
                MapAnchor anchor = new MapAnchor("synthetic_shop_route",origin.getX(),origin.getY(),origin.getZ(),rotation);
                Rotation turn = rotation==0 ? Rotation.NONE : Rotation.CLOCKWISE_180;
                List<int[]> columns = new ArrayList<>(route); columns.add(new int[]{19,18});
                for(int[] column : columns)for(int y=1;y<=3;y++) {
                    BlockPos at=ShopFacility40.local(anchor,column[0],y,column[1]);
                    level.getChunkAt(at); restore.putIfAbsent(at,level.getBlockState(at));
                    level.setBlock(at,source.at(column[0],y,column[1]).rotate(turn),18);
                }
                BlockPos door = ShopFacility40.local(anchor,24,2,13);
                AABB doorway = corridor(ShopFacility40.local(anchor,25,2,13),ShopFacility40.local(anchor,23,2,13));
                h.assertTrue(!clear(level,doorway),"Native closed door was incorrectly treated as passable");
                for(int y=2;y<=3;y++) {
                    BlockPos at=ShopFacility40.local(anchor,24,y,13);
                    level.setBlock(at,level.getBlockState(at).setValue(DoorBlock.OPEN,true),18);
                }
                for(int i=1;i<route.size();i++) {
                    int[] a=route.get(i-1), b=route.get(i);
                    BlockPos from=ShopFacility40.local(anchor,a[0],2,a[1]),to=ShopFacility40.local(anchor,b[0],2,b[1]);
                    h.assertTrue(clear(level,corridor(from,to)),"Native 0.6-by-1.8 player corridor is blocked after normal door opening");
                    h.assertTrue(level.getBlockState(to.below()).isFaceSturdy(level,to.below(),Direction.UP),"Route lost native supporting floor");
                }
                BlockPos stand=ShopFacility40.local(anchor,19,2,17),counter=ShopFacility40.local(anchor,19,3,18);
                Vec3 eye=Vec3.atBottomCenterOf(stand).add(0,1.62,0),target=Vec3.atCenterOf(counter);
                h.assertTrue(eye.distanceToSqr(target)<=ShopFacility40.REACH_SQUARED,"Standing cell lies outside provisional counter reach");
                var ray=level.clip(new ClipContext(eye,target,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,null));
                h.assertTrue(ray.getType()==HitResult.Type.BLOCK && ray.getBlockPos().equals(counter),"Native standing-eye ray does not reach the authored crafting table");
                level.setBlock(stand.above(),Blocks.STONE.defaultBlockState(),18);
                var blocked=level.clip(new ClipContext(eye,target,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,null));
                h.assertTrue(blocked.getType()!=HitResult.Type.BLOCK || !blocked.getBlockPos().equals(counter),"An intervening native obstruction still permits the counter ray");
                level.setBlock(stand.above(),Blocks.AIR.defaultBlockState(),18);
            }
        } finally {
            for(var entry:restore.entrySet())level.setBlock(entry.getKey(),entry.getValue(),18);
        }
        h.succeed();
    }

    @GameTest(template="townhall_state_empty",batch="shop40_facility_04",timeoutTicks=200)
    public static void starter_tier_and_exact_existing_catalog_do_not_activate_reference_guns(GameTestHelper h) throws Exception {
        controlled(h);
        h.assertTrue(WeaponShopCatalog40.offers().stream().map(WeaponShopCatalog40.Offer::id).toList()
                .equals(List.of("pistol","ak47","ammo9","ammo762")),"New reference guns or changed offer ordering became active");
        checkOffer(h,"pistol","tacz:glock_17",WeaponShopCatalog40.Kind.GUN,1,0,350,Map.of(ResourceType.WOOD,5L,ResourceType.STONE,5L));
        checkOffer(h,"ak47","tacz:ak47",WeaponShopCatalog40.Kind.GUN,1,1,1200,Map.of(ResourceType.STEEL,12L,ResourceType.WOOD,10L));
        checkOffer(h,"ammo9","tacz:9mm",WeaponShopCatalog40.Kind.AMMO,32,0,100,Map.of(ResourceType.STONE,5L));
        checkOffer(h,"ammo762","tacz:762x39",WeaponShopCatalog40.Kind.AMMO,32,1,175,Map.of(ResourceType.STEEL,2L,ResourceType.EXPLOSIVES,1L));
        refuse(()->WeaponShopCatalog40.offer("reference_best_gun"));
        CompoundTag source=new CompoundTag();source.putString("type","starter");source.putString("city","a");
        var starter=new ShopFacility40.Binding(source,"fixture",0,ShopFacility40.STARTER);
        source.putString("city","changed");CompoundTag copy=starter.identity();copy.putString("city","changed_again");
        h.assertTrue(starter.identity().getString("city").equals("a"),"Caller can mutate frozen facility identity");
        h.assertTrue(starter.forOffer(WeaponShopCatalog40.offer("pistol").requiredLevel()).getInt("requiredLevel")==0,"Starter Glock unexpectedly needs a factory");
        refuse(()->starter.forOffer(WeaponShopCatalog40.offer("ak47").requiredLevel()));
        refuse(()->starter.forOffer(WeaponShopCatalog40.offer("ammo762").requiredLevel()));
        h.assertTrue(PhysicalWeaponShop40.catalogRows().size()==4
                && PhysicalWeaponShop40.catalogRows().stream().anyMatch(s->s.startsWith("Gun: "))
                && PhysicalWeaponShop40.catalogRows().stream().anyMatch(s->s.startsWith("Ammo: ")),"Read-only catalog lost item kinds");
        h.succeed();
    }

    @GameTest(template="townhall_state_empty",batch="shop40_facility_05",timeoutTicks=200)
    public static void session_ttl_is_bounded_exclusive_and_rejects_clock_rollback(GameTestHelper h) throws Exception {
        controlled(h);
        h.assertTrue(PhysicalWeaponShop40.SESSION_TICKS==1200,"Documented provisional session duration changed");
        h.assertTrue(PhysicalWeaponShop40.withinLifetime(100,100)&&PhysicalWeaponShop40.withinLifetime(100,1299),"Live session boundary rejected");
        for(long tick:new long[]{-1,0,99,1300,1301,Long.MAX_VALUE})
            h.assertTrue(!PhysicalWeaponShop40.withinLifetime(100,tick),"Expired or reversed clock refreshed a session");
        h.assertTrue(!PhysicalWeaponShop40.withinLifetime(-1,0),"Negative creation time accepted");
        h.assertTrue(PhysicalWeaponShop40.withinLifetime(Long.MAX_VALUE-1199,Long.MAX_VALUE)
                &&!PhysicalWeaponShop40.withinLifetime(Long.MAX_VALUE-1200,Long.MAX_VALUE),"Near-overflow session lifetime is unbounded");
        h.succeed();
    }

    @GameTest(template="townhall_state_empty",batch="shop40_facility_06",timeoutTicks=200)
    public static void detached_fake_dead_and_spectator_actors_cannot_open_physical_shopping(GameTestHelper h) throws Exception {
        controlled(h); ServerLevel level=h.getLevel();
        FakePlayer fake=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"Shop40Refusal"));
        var server=level.getServer();server.getPlayerList().getPlayers().add(fake);
        try {
            for(GameType mode:List.of(GameType.SURVIVAL,GameType.ADVENTURE,GameType.CREATIVE,GameType.SPECTATOR)) {
                fake.setGameMode(mode);fake.setHealth(20);fake.setPos(-805.5,81,-732.5);
                h.assertTrue(!ShopFacility40.connected(fake),"Detached FakePlayer was accepted as an authenticated connection");
                refuse(()->ShopFacility40.actor(fake));refuse(()->ShopFacility40.open(fake,new BlockPos(-805,82,-731)));
            }
            fake.setGameMode(GameType.SURVIVAL);fake.setHealth(0);refuse(()->ShopFacility40.actor(fake));
            // A native ServerPlayer with no connection is rejected too; no synthetic connection is installed.
            ServerPlayer detached=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),"Shop40Detached"));
            refuse(()->ShopFacility40.actor(detached));detached.setHealth(0);refuse(()->ShopFacility40.actor(detached));
            h.assertTrue(!ShopFacility40.connected(null),"Null actor became a connected player");
        } finally {server.getPlayerList().getPlayers().remove(fake);PhysicalWeaponShop40.stopped(server);}
        h.succeed();
    }

    @GameTest(template="townhall_state_empty",batch="shop40_facility_07",timeoutTicks=200)
    public static void typed_review_confirm_and_recovery_routes_cannot_mint_a_counter_session(GameTestHelper h) throws Exception {
        Path root=controlled(h);ServerLevel level=h.getLevel();
        FakePlayer player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"Shop40Remote"));
        player.setGameMode(GameType.SURVIVAL);player.setPos(0,140,0);
        CatanSavedData data=CatanSavedData.get(level.getServer());NationData nation=data.createNation("Shop40Remote_"+player.getUUID(),player.getUUID());
        nation.setTreasury(10000);nation.addResource(ResourceType.WOOD,100);nation.addResource(ResourceType.STONE,100);
        CompoundTag before=nation.save().copy();
        h.assertTrue(player.getInventory().isEmpty(),"Remote refusal fixture must start with empty inventory");
        PhysicalWeaponShop40.stopped(level.getServer());
        CommandDispatcher<CommandSourceStack> dispatcher=new CommandDispatcher<>();
        dispatcher.register(literal("nation").then(PhysicalWeaponShop40.addRoutes(literal("procure"))));
        UUID nonce=UUID.randomUUID();
        for(String route:List.of("review pistol","review ak47","confirm "+nonce,"cancel "+nonce,"menu "+nonce,"recover "+nonce,"close "+nonce))
            h.assertTrue(dispatcher.execute("nation procure shop "+route,player.createCommandSourceStack())==0,"Remote command minted shopping authority: "+route);
        h.assertTrue(PhysicalWeaponShop40.review(player.createCommandSourceStack(),"pistol")==0,"Legacy command bypassed the physical counter");
        h.assertTrue(!PhysicalWeaponShop40.openCounter(player,new BlockPos(0,140,0)),"Unregistered block opened a shop");
        h.assertTrue(PhysicalWeaponShop40.openCounter(player,new BlockPos(-805,82,-731)),"Recognized counter did not consume its refused fake-player interaction");
        h.assertTrue(PhysicalWeaponShop40.review(player.createCommandSourceStack(),"pistol")==0,"Refused counter click left a usable session");
        h.assertTrue(nation.save().equals(before)&&player.getInventory().isEmpty(),"Refused commands changed nation funds or inventory");
        try(var files=Files.newDirectoryStream(root.resolve("data"),"catancraft_shop40_"+player.getUUID()+"_*.dat")) {
            h.assertTrue(!files.iterator().hasNext(),"Remote/refused counter routes created a purchase intent");
        }
        PhysicalWeaponShop40.stopped(level.getServer());h.succeed();
    }

    @GameTest(template="townhall_state_empty",batch="shop40_facility_08",timeoutTicks=200)
    public static void missing_native_weapon_mod_cannot_create_fallback_items(GameTestHelper h) throws Exception {
        controlled(h);
        h.assertTrue(!ModList.get().isLoaded("tacz"),"This refusal fixture requires the controlled no-TaCZ classpath; loaded-mod acceptance is separate");
        for(var offer:WeaponShopCatalog40.offers())refuse(()->WeaponShopCatalog40.build(offer));
        h.succeed();
    }

    private interface Attempt {void run() throws Exception;}
    private static void refuse(Attempt attempt)throws Exception {
        try{attempt.run();}catch(IOException expected){return;}throw new AssertionError("Expected physical shop refusal");
    }
    private static Path controlled(GameTestHelper h)throws IOException {
        var server=h.getLevel().getServer();Path root=server.getWorldPath(LevelResource.ROOT).toRealPath();
        if(!(server instanceof GameTestServer)||!server.isSameThread()||!root.getFileName().toString().equals("world")
                ||!root.getParent().getFileName().toString().equals("controlled-catancraft_shop40-run")
                ||!Files.readString(root.getParent().resolve(".catancraft-controlled-fixture-v1")).equals("CatanCraft synthetic flat fixture v1\n"))
            throw new IOException("Facility tests require their marked disposable native world");
        return root;
    }
    private static void checkOffer(GameTestHelper h,String id,String nativeId,WeaponShopCatalog40.Kind kind,int count,int tier,long money,Map<ResourceType,Long> resources)throws IOException {
        var offer=WeaponShopCatalog40.offer(id);
        h.assertTrue(offer.nativeId().equals(nativeId)&&offer.kind()==kind&&offer.count()==count&&offer.requiredLevel()==tier
                &&offer.price().money()==money&&offer.price().resources().equals(resources),"Existing offer or price changed: "+id);
    }
    private static AABB corridor(BlockPos a,BlockPos b) {
        return new AABB(Math.min(a.getX(),b.getX())+0.2,a.getY(),Math.min(a.getZ(),b.getZ())+0.2,
                Math.max(a.getX(),b.getX())+0.8,a.getY()+1.8,Math.max(a.getZ(),b.getZ())+0.8);
    }
    private static boolean clear(ServerLevel level,AABB box) {
        for(int x=(int)Math.floor(box.minX);x<=(int)Math.floor(box.maxX);x++)
            for(int y=(int)Math.floor(box.minY);y<=(int)Math.floor(box.maxY);y++)
                for(int z=(int)Math.floor(box.minZ);z<=(int)Math.floor(box.maxZ);z++) {
                    BlockPos p=new BlockPos(x,y,z);
                    for(AABB shape:level.getBlockState(p).getCollisionShape(level,p).toAabbs())
                        if(shape.move(p.getX(),p.getY(),p.getZ()).intersects(box))return false;
                }
        return true;
    }
}
