package com.quin.catancraft.world;

import com.quin.catancraft.data.*;
import com.quin.catancraft.economy.EconomyCost;
import com.quin.catancraft.ui.DefenseMenuRows;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Real server GUI-contract/service tests. These do not claim an interactive client walkthrough. */
@GameTestHolder("catancraft_wall_gui") @PrefixGameTestTemplate(false)
public final class WallGuiTests {
    private static final SectorContracts.Direction N=SectorContracts.Direction.NORTH;
    private static final AtomicInteger SITES=new AtomicInteger();
    private static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> TICKET=
            net.minecraft.server.level.TicketType.create("catancraft_wall_gui_fixture",Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong));

    @GameTest(template="townhall_state_empty",timeoutTicks=100)
    public static void dashboard_lists_four_build_buttons_and_catalog_costs(GameTestHelper h)throws Exception {
        var data=new CatanSavedData();var nation=data.createNation("GUI rows",UUID.randomUUID());
        nation.addResource(ResourceType.WOOD,100);nation.addResource(ResourceType.STONE,100);
        List<SectorService.MenuState> states=new ArrayList<>();
        for(var side:SectorContracts.Direction.values())states.add(new SectorService.MenuState(side,0,SectorPriceCatalog.cost(side,0),"",false));
        var rows=DefenseMenuRows.rows("a",true,nation,states);
        h.assertTrue(rows.get(0).startsWith("N|a|defenses|DEFENSES"),"Defenses missing from collapsible territory section");
        h.assertTrue(rows.stream().filter(s->s.startsWith("A|nation defense review a ")).count()==4,"Not exactly four whole-side buttons");
        for(var side:SectorContracts.Direction.values())h.assertTrue(rows.stream().anyMatch(s->s.startsWith("A|nation defense review a "+side.name().toLowerCase(Locale.ROOT)+"|Build Basic")),"Wrong compass/action");
        h.assertTrue(rows.stream().anyMatch(s->s.contains("$1500")&&s.contains("15 Wood")&&s.contains("30 Stone")),"Full cost omitted");
        h.assertTrue(rows.get(rows.size()-1).equals("Q|a|defenses"),"Section scope not closed");h.succeed();
    }
    @GameTest(template="townhall_state_empty",timeoutTicks=100)
    public static void upgrade_maximum_pending_member_and_missing_material_states(GameTestHelper h)throws Exception {
        var data=new CatanSavedData();var nation=data.createNation("GUI states",UUID.randomUUID());
        var price=SectorPriceCatalog.cost(N,1);
        var state=new SectorService.MenuState(N,1,price,"",false);
        var missing=DefenseMenuRows.rows("a",true,nation,List.of(state));
        h.assertTrue(missing.stream().anyMatch(s->s.startsWith("Y|Missing:")&&s.contains("Concrete")&&s.contains("Steel")),"Material shortage not shown");
        h.assertTrue(noPurchase(missing),"Insufficient resources still clickable");
        nation.addResource(ResourceType.CONCRETE,50);nation.addResource(ResourceType.STEEL,15);
        var funded=DefenseMenuRows.rows("a",true,nation,List.of(state));
        h.assertTrue(funded.stream().anyMatch(s->s.contains("|Upgrade to Reinforced - North")),"Upgrade action missing");
        h.assertTrue(noPurchase(DefenseMenuRows.rows("a",false,nation,List.of(state))),"Member can spend through GUI");
        h.assertTrue(noPurchase(DefenseMenuRows.rows("a",true,nation,List.of(new SectorService.MenuState(N,1,price,"Working",true)))),"Pending action still clickable");
        h.assertTrue(noPurchase(DefenseMenuRows.rows("a",true,nation,List.of(new SectorService.MenuState(N,2,null,"",false)))),"Max tier can be purchased");
        nation.addTreasury(-nation.treasury());
        h.assertTrue(DefenseMenuRows.missing(nation,price).equals("$4000"),"Money shortage not distinct");h.succeed();
    }
    @GameTest(template="townhall_state_empty",timeoutTicks=100)
    public static void confirmation_contains_exact_side_tier_price_token_and_cancel(GameTestHelper h)throws Exception {
        String token=UUID.randomUUID().toString();
        var quote=new SectorService.MenuQuote(token,"a",N,1,2,SectorPriceCatalog.cost(N,1),SectorPriceCatalog.VERSION);
        var rows=DefenseMenuRows.confirmation("City A",quote);
        h.assertTrue(rows.get(0).equals("V|defense_confirmation"),"Client confirmation route missing");
        h.assertTrue(rows.stream().anyMatch(s->s.equals("P|Side: North")),"Side missing");
        h.assertTrue(rows.stream().anyMatch(s->s.contains("$4000")&&s.contains("50 Concrete")&&s.contains("15 Steel")),"Incremental quote wrong");
        h.assertTrue(rows.stream().anyMatch(s->s.equals("A|nation defense confirm a north "+token+"|Confirm purchase")),"Confirm not bound to exact token");
        h.assertTrue(rows.stream().anyMatch(s->s.equals("A|nation defense cancel a north "+token+"|Cancel")),"Cancel missing");h.succeed();
    }
    @GameTest(template="townhall_state_empty",timeoutTicks=100)
    public static void gui_command_routes_are_registered_with_token_required(GameTestHelper h) {
        var root=DefenseCommands.tree().build();
        for(String verb:List.of("review","confirm","cancel"))for(String side:List.of("north","east","south","west")){
            var node=root.getChild(verb).getChild("city").getChild(side);
            h.assertTrue(node!=null,"Missing GUI route");
            h.assertTrue(verb.equals("review")?node.getCommand()!=null:node.getCommand()==null&&node.getChild("quoteId").getCommand()!=null,"Mutation accepts an unbound confirmation");
        }
        h.succeed();
    }
    @GameTest(template="townhall_state_empty",batch="wall_gui01",timeoutTicks=1200)
    public static void cancel_and_superseded_quote_have_zero_effect(GameTestHelper h){start(h,f->{
        long money=f.nation.treasury();var first=f.quote();f.cancel(first.token());
        refuses(h,()->f.confirm(first.token()));
        var old=f.quote();var current=f.quote();f.cancel(old.token());
        refuses(h,()->f.confirm(old.token()));
        h.assertTrue(f.nation.treasury()==money&&f.level.getBlockState(f.block).isAir(),"Canceled/stale token mutated state");
        ok(h,f.confirm(current.token()));h.assertTrue(f.nation.treasury()==money-1500,"Current token did not purchase exactly once");
    });}
    @GameTest(template="townhall_state_empty",batch="wall_gui02",timeoutTicks=1200)
    public static void purchase_upgrade_and_replayed_gui_confirm_are_exactly_once(GameTestHelper h){start(h,f->{
        long money=f.nation.treasury();var q=f.quote();ok(h,f.confirm(q.token()));
        refuses(h,()->f.confirm(q.token()));
        h.assertTrue(SectorService.savedState(f.data,"a",N).level()==1,"Basic not persisted");
        var up=f.quote();ok(h,f.confirm(up.token()));refuses(h,()->f.confirm(up.token()));
        h.assertTrue(SectorService.savedState(f.data,"a",N).level()==2&&f.nation.treasury()==money-5500,"Upgrade/replay wrong price or level");
        var reloaded=SectorService.reloadFixture(f.context);
        h.assertTrue(SectorService.savedState(reloaded,"a",N).level()==2&&reloaded.nation(f.nation.id()).treasury()==f.nation.treasury(),"Persisted ledger differs");
        refuses(h,f::quote);
    });}
    @GameTest(template="townhall_state_empty",batch="wall_gui03",timeoutTicks=1200)
    public static void expired_quote_and_changed_funds_refuse_without_writes(GameTestHelper h){start(h,f->{
        long money=f.nation.treasury();var expired=f.quote();f.clock.addAndGet(1201);refuses(h,()->f.confirm(expired.token()));
        h.assertTrue(f.nation.treasury()==money,"Expired quote charged");
        var q=f.quote();String token=q.token();f.nation.addResource(ResourceType.STONE,-f.nation.resource(ResourceType.STONE));
        refuses(h,()->f.confirm(token));h.assertTrue(f.nation.treasury()==money&&f.level.getBlockState(f.block).isAir(),"Changed balance created partial purchase");
    });}
    @GameTest(template="townhall_state_empty",batch="wall_gui04",timeoutTicks=1200)
    public static void wrong_actor_or_side_token_and_owned_world_conflict_refuse(GameTestHelper h){start(h,f->{
        var q=f.quote();long money=f.nation.treasury();
        var wrong=new SectorService.Context(f.context.server(),f.context.root(),f.data,"a",UUID.randomUUID(),f.nation.id(),f.context.worldId(),f.context.approval(),f.clock::get);
        refuses(h,()->SectorService.confirmMenuContext(wrong,N,q.token(),f,false));
        refuses(h,()->SectorService.confirmMenuContext(f.context,SectorContracts.Direction.EAST,q.token(),f,false));
        f.level.setBlock(f.block,Blocks.GOLD_BLOCK.defaultBlockState(),2|16|32);
        refuses(h,()->f.confirm(q.token()));
        h.assertTrue(f.nation.treasury()==money&&f.level.getBlockState(f.block).is(Blocks.GOLD_BLOCK),"Unauthorized/obstructed confirmation charged or replaced a block");
    });}
    private static boolean noPurchase(List<String> rows){return rows.stream().noneMatch(s->s.startsWith("A|nation defense review "));}
    private interface Checked {void run()throws Exception;}
    private static void refuses(GameTestHelper h,Checked action)throws Exception {
        boolean refused=false;try{action.run();}catch(IOException expected){refused=true;}
        h.assertTrue(refused,"Expected service refusal");
    }
    private static void ok(GameTestHelper h,MapAssetService.Result result){h.assertTrue(result.success(),result.message());}
    private interface Body {void run(Fixture fixture)throws Exception;}
    private static void start(GameTestHelper h,Body body){try{Fixture f=new Fixture(h);f.stage();await(h,f,body,0);}catch(Exception e){throw new RuntimeException(e);}}
    private static void await(GameTestHelper h,Fixture f,Body body,int attempts){h.runAfterDelay(attempts==0?20:1,()->{
        try{StreetscapeService.requireLoadedEntities(f.level,List.of(SectorService.entityBounds(f.resolve(f.context,N,0).plan())));}
        catch(IOException e){if(attempts<400){await(h,f,body,attempts+1);return;}throw new RuntimeException(e);}
        try{body.run(f);h.succeed();}catch(Exception e){throw new RuntimeException(e);}
    });}
    private static final class Fixture implements SectorService.Provider {
        final ServerLevel level;final BlockPos block;final CatanSavedData data=new CatanSavedData();
        final UUID actor=UUID.randomUUID();final NationData nation;final AtomicLong clock=new AtomicLong(100);
        final SectorService.Context context;
        Fixture(GameTestHelper h)throws IOException {
            level=h.getLevel();block=h.absolutePos(new BlockPos(2400+48*SITES.getAndIncrement(),10,2400));
            nation=data.createNation("GUI transaction",actor);nation.addTreasury(10000);
            for(var r:ResourceType.values())nation.addResource(r,1000);
            data.createTerritory("a","GUI city",ResourceType.WOOD).setOwnerNationId(nation.id());
            Path root=level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().resolve("data/gui-fixtures/"+UUID.randomUUID());
            context=SectorService.fixtureContext(level.getServer(),root,data,"a",actor,UUID.randomUUID().toString(),clock::get);
        }
        void stage(){
            for(int x=(block.getX()-16)>>4;x<=(block.getX()+16)>>4;x++)for(int z=(block.getZ()-16)>>4;z<=(block.getZ()+16)>>4;z++){
                var chunk=new net.minecraft.world.level.ChunkPos(x,z);level.getChunkSource().addRegionTicket(TICKET,chunk,2,chunk,true);level.setChunkForced(x,z,true);level.getChunk(x,z);
            }
            for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)for(int y=-1;y<=3;y++)level.setBlock(block.offset(x,y,z),y==-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2|16|32);
        }
        public String priceVersion(){return SectorPriceCatalog.VERSION;}
        public SectorContracts.Contract resolve(SectorService.Context c,SectorContracts.Direction side,int source)throws IOException {
            if(source<0||source>1)throw new IOException("No further fixture tier");
            var old=SectorService.savedState(data,"a",side);
            var plan=new SectorTransaction.Plan(side.name().toLowerCase(Locale.ROOT),source==0?"GUI_BASE":old.revision(),"GUI_"+side+"_L"+(source+1),source,source+1,
                    List.of(new SectorTransaction.Cell(block,source==0?Blocks.AIR.defaultBlockState():Blocks.STONE_BRICKS.defaultBlockState(),source==0?Blocks.STONE_BRICKS.defaultBlockState():Blocks.POLISHED_ANDESITE.defaultBlockState())),
                    List.of(new SectorTransaction.Read(block.below(),Blocks.STONE.defaultBlockState())));
            Map<SectorContracts.Direction,SectorContracts.Neighbor> neighbors=new EnumMap<>(SectorContracts.Direction.class);
            for(var d:side.adjacent()){var state=SectorService.savedState(data,"a",d);neighbors.put(d,new SectorContracts.Neighbor(state.level(),state.receipt()));}
            var corners=Map.of(SectorContracts.Corner.NORTH_WEST,N,SectorContracts.Corner.NORTH_EAST,N,SectorContracts.Corner.SOUTH_WEST,SectorContracts.Direction.SOUTH,SectorContracts.Corner.SOUTH_EAST,SectorContracts.Direction.SOUTH);
            return new SectorContracts.Contract(c.root().toString(),"a",side,plan,corners,neighbors,Set.of(),SectorPriceCatalog.cost(side,source));
        }
        SectorService.MenuQuote quote()throws IOException{return SectorService.menuQuoteContext(context,N,this);}
        MapAssetService.Result confirm(String token)throws IOException{return SectorService.confirmMenuContext(context,N,token,this,false);}
        void cancel(String token)throws IOException{SectorService.cancelMenuContext(context,N,token);}
    }
}
