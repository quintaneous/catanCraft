package com.quin.catancraft.world;

import com.quin.catancraft.data.*;
import com.quin.catancraft.economy.EconomyCost;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Actual approved north P2 geometry in disposable native worlds. Prices use the approved provisional structural catalog. */
@GameTestHolder("catancraft_north_p2") @PrefixGameTestTemplate(false)
public final class SectorNorthP2Tests {
    private static final int FLAGS=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS;
    private static final AtomicInteger SITES=new AtomicInteger();
    private static final TicketType<ChunkPos> TICKET=TicketType.create("catancraft_p2_fixture",Comparator.comparingLong(ChunkPos::toLong));
    private static final SectorContracts.Direction NORTH=SectorContracts.Direction.NORTH;
    @GameTest(template="townhall_state_empty",batch="p2_01",timeoutTicks=12000)
    public static void full_north_purchase_upgrade_duplicate_and_exact_reverse(GameTestHelper h){start(h,f->{
        Map<BlockPos,BlockState> fresh=f.scene();CompoundTag nation=f.nation().save(),book=f.book();
        ok(h,f.quote());var basic=f.op(SectorTransaction.Action.APPLY);ok(h,basic);h.assertTrue(basic.changedBlocks()==5013,"P2 fresh sparse write count");f.verify(f.plan(0),true);String basicReceipt=f.state().receipt();Map<BlockPos,BlockState> basicScene=f.scene();
        byte[] history=Files.readAllBytes(f.pointed());ok(h,f.op(SectorTransaction.Action.APPLY));h.assertTrue(Arrays.equals(history,Files.readAllBytes(f.pointed()))&&f.nation().treasury()==f.initialMoney-1500,"P2 duplicate changed payment/history");
        ok(h,f.quote());var upgrade=f.op(SectorTransaction.Action.APPLY);ok(h,upgrade);h.assertTrue(upgrade.changedBlocks()==4539&&f.state().level()==2&&f.nation().treasury()==f.initialMoney-5500&&f.nation().resource(ResourceType.STONE)==f.initialStone-30&&f.nation().resource(ResourceType.CONCRETE)==f.initialConcrete-50&&f.nation().resource(ResourceType.STEEL)==f.initialSteel-15,"P2 upgrade exact delta or one group price");f.verify(f.plan(1),true);
        ok(h,f.op(SectorTransaction.Action.ROLLBACK));h.assertTrue(f.state().level()==1&&f.state().receipt().equals(basicReceipt)&&basicScene.equals(f.scene())&&f.nation().treasury()==f.initialMoney-1500&&book.equals(f.book()),"P2 upgrade reverse lost basic ownership/source/book");
        // Latest rolled-back history deliberately remains; previous committed history is retained.
        h.assertTrue(f.histories().size()==2,"P2 history overwritten");h.runAfterDelay(40,()->{try{
            f.verifyPolicyScene(basicScene);h.assertTrue(book.equals(f.book()),"Delayed P2 management book drift");
            // The actual paid source must still support a new upgrade after natural ticks.
            ok(h,f.quote());ok(h,f.op(SectorTransaction.Action.APPLY));h.assertTrue(f.state().level()==2&&f.nation().treasury()==f.initialMoney-5500,"Delayed P2 upgrade lost original accounting");
            ok(h,f.op(SectorTransaction.Action.ROLLBACK));f.verifyPolicyScene(basicScene);h.assertTrue(f.state().level()==1&&book.equals(f.book()),"Delayed P2 rollback lost base ownership/book");h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}});
    });}
    @GameTest(template="townhall_state_empty",batch="p2_02",timeoutTicks=12000)
    public static void full_north_interrupted_native_reload_historical_resume_and_refund(GameTestHelper h){start(h,f->{
        Map<BlockPos,BlockState> source=f.scene();CompoundTag original=f.nation().save(),book=f.book();ok(h,f.quote());
        no(h,f.op(SectorTransaction.Action.APPLY,(stage,writes)->{if(stage.equals("write")&&writes==3)throw new IOException("P2 native interruption");}));
        h.assertTrue(f.state().level()==0&&f.nation().treasury()==f.initialMoney-1500,"P2 pending progress/payment invalid");f.reload();f.disabled=true;ok(h,f.op(SectorTransaction.Action.RESUME));f.verify(f.plan(0),true);h.assertTrue(f.state().level()==1&&f.nation().treasury()==f.initialMoney-1500&&book.equals(f.book()),"P2 historical resume used disabled price catalog or lost book");
        no(h,f.op(SectorTransaction.Action.ROLLBACK,(stage,writes)->{if(stage.equals("rollback_write")&&writes==3)throw new IOException("P2 native reverse interruption");}));f.reload();ok(h,f.op(SectorTransaction.Action.ROLLBACK));
        h.assertTrue(source.equals(f.scene())&&original.equals(f.nation().save())&&f.state().level()==0&&f.state().receipt().isEmpty()&&book.equals(f.book()),"P2 complete source/refund/NBT restoration failed");ok(h,f.op(SectorTransaction.Action.ROLLBACK));h.assertTrue(original.equals(f.nation().save()),"P2 duplicate refund");
        h.runAfterDelay(40,()->{h.assertTrue(source.equals(f.scene())&&book.equals(f.book()),"Delayed P2 rollback source/NBT drift");h.succeed();});
    });}
    @GameTest(template="townhall_state_empty",batch="p2_03",timeoutTicks=12000)
    public static void full_north_final_cell_and_exact_support_conflicts_charge_nothing(GameTestHelper h){start(h,f->{
        var plan=f.plan(0);var last=plan.cells().get(plan.cells().size()-1);f.level.setBlock(last.pos(),Blocks.DIAMOND_BLOCK.defaultBlockState(),FLAGS);var scene=f.scene();CompoundTag nation=f.nation().save(),book=f.book();ok(h,f.quote());no(h,f.op(SectorTransaction.Action.APPLY));h.assertTrue(scene.equals(f.scene())&&nation.equals(f.nation().save())&&f.histories().isEmpty()&&book.equals(f.book()),"P2 late-source conflict partially changed/charged group");
        f.level.setBlock(last.pos(),last.before(),FLAGS);var support=plan.reads().stream().filter(r->r.pos().getY()==f.anchor.getY()-1&&!r.state().isAir()).findFirst().orElseThrow();f.level.setBlock(support.pos(),Blocks.AIR.defaultBlockState(),FLAGS);scene=f.scene();ok(h,f.quote());no(h,f.op(SectorTransaction.Action.APPLY));h.assertTrue(scene.equals(f.scene())&&nation.equals(f.nation().save())&&f.histories().isEmpty(),"P2 missing support changed/charged group");h.succeed();
    });}
    @GameTest(template="townhall_state_empty",batch="p2_04",timeoutTicks=12000)
    public static void pinned_north_sparse_masks_caps_roads_and_scope(GameTestHelper h){start(h,f->{
        var basic=f.plan(0);var upgrade=f.plan(1);h.assertTrue(basic.cells().size()==5013&&upgrade.cells().size()==4539&&basic.reads().size()>35000,"P2 full read/sparse counts lost");
        Set<BlockPos> roads=f.contract(0).roads();for(var c:basic.cells())h.assertTrue(c.before().isAir()&&!c.after().isAir()&&!roads.contains(c.pos()),"Fresh P2 erases or enters protected road");
        for(BlockPos canonical:List.of(new BlockPos(-732,88,-849),new BlockPos(-732,88,-848),new BlockPos(-716,88,-839))){BlockPos p=f.anchor.offset(canonical.subtract(SectorNorthP2.CANONICAL_ANCHOR));var cell=basic.cells().stream().filter(c->c.pos().equals(p)).findFirst().orElseThrow();h.assertTrue(cell.after().is(Blocks.STONE_BRICK_SLAB),"P2 owned upper pier cap removed");}
        refuse(()->SectorNorthP2.fixtureContract(f.ctx,SectorContracts.Direction.EAST,0,f.cost(0),f.anchor));refuse(()->SectorNorthP2.fixtureContract(f.ctx,NORTH,2,f.cost(0),f.anchor));refuse(()->SectorNorthP2.fixtureContract(f.ctx,NORTH,0,null,f.anchor));
        var foreign=new SectorService.Context(f.ctx.server(),f.root,f.data,"b",f.actor,f.owner,f.worldId,SectorService.FIXTURE,f.ctx.ticks());refuse(()->SectorNorthP2.fixtureContract(foreign,NORTH,0,f.cost(0),f.anchor));
        var canonical=SectorNorthP2.plan(f.level,0);var outside=canonical.cells().stream().filter(c->c.pos().getZ()<-846).findFirst().orElseThrow();h.assertTrue(SectorNorthP2.approvedExtensionWrite("a",canonical,outside.pos())&&!SectorNorthP2.approvedExtensionWrite("b",canonical,outside.pos())&&!SectorNorthP2.approvedExtensionWrite("a",basic,outside.pos()),"P2 extension permission escaped exact canonical plan/city");
        f.data.setPlacedMapAsset(DefenseService.ownedKey("a","front_w_00"),"unknown_owned_revision");refuse(()->SectorNorthP2.requireNoLegacyOwnership(f.ctx,basic));f.data.setPlacedMapAsset(DefenseService.ownedKey("a","front_w_00"),"defense_unowned");f.data.setPlacedMapAsset("defense_receipt:a:front_w_00","unproven:paid");refuse(()->SectorNorthP2.requireNoLegacyOwnership(f.ctx,basic));h.succeed();
    });}
    private interface Checked{void run()throws IOException;}private static void refuse(Checked c)throws IOException{try{c.run();}catch(IOException expected){return;}throw new AssertionError("Expected P2 scope refusal");}
    private interface Body{void run(Fixture f)throws Exception;}
    private static void start(GameTestHelper h,Body body){try{Fixture f=new Fixture(h);f.stage();ready(h,f,body,0);}catch(Exception e){throw new RuntimeException(e);}}
    private static void ready(GameTestHelper h,Fixture f,Body body,int tries){h.runAfterDelay(tries==0?20:1,()->{try{StreetscapeService.requireLoadedEntities(f.level,List.of(SectorService.entityBounds(f.contract(0).plan()),SectorService.entityBounds(f.contract(1).plan())));}catch(IOException loading){if(tries<400){ready(h,f,body,tries+1);return;}throw new RuntimeException("P2 native entity sections unavailable",loading);}try{body.run(f);}catch(Exception e){throw new RuntimeException(e);}});}
    private static void ok(GameTestHelper h,MapAssetService.Result r){h.assertTrue(r.success(),r.message());}private static void no(GameTestHelper h,MapAssetService.Result r){h.assertTrue(!r.success(),"Expected refusal: "+r.message());}
    private static final class Fixture {
        final ServerLevel level;final BlockPos anchor,bookPos;final Path root;final UUID actor=UUID.randomUUID(),owner;final String worldId=UUID.randomUUID().toString();CatanSavedData data=new CatanSavedData();SectorService.Context ctx;boolean disabled;final long initialMoney,initialStone,initialConcrete,initialSteel;
        Fixture(GameTestHelper h)throws IOException{level=h.getLevel();anchor=new BlockPos(4000+320*SITES.getAndIncrement(),200,4000);bookPos=anchor.offset(100,0,30);root=level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().resolve("data/p2-native/"+UUID.randomUUID());var nation=data.createNation("P2 native fixture",actor);owner=nation.id();nation.addTreasury(10000);for(var resource:List.of(ResourceType.WOOD,ResourceType.STONE,ResourceType.CONCRETE,ResourceType.STEEL))nation.addResource(resource,1000);initialMoney=nation.treasury();initialStone=nation.resource(ResourceType.STONE);initialConcrete=nation.resource(ResourceType.CONCRETE);initialSteel=nation.resource(ResourceType.STEEL);data.createTerritory("a","P2 native City A",ResourceType.WOOD).setOwnerNationId(owner);context();}
        void context()throws IOException{ctx=SectorService.fixtureContext(level.getServer(),root,data,"a",actor,worldId,()->100L);}void reload()throws IOException{data=SectorService.reloadFixture(ctx);context();}NationData nation(){return data.nation(owner);}SectorService.State state()throws IOException{return SectorService.savedState(data,"a",NORTH);}
        EconomyCost cost(int from){try{return SectorPriceCatalog.cost(NORTH,from);}catch(IOException e){throw new IllegalArgumentException(e);}}
        SectorTransaction.Plan plan(int from)throws IOException{return SectorNorthP2.fixturePlan(level,from,anchor);}
        SectorContracts.Contract contract(int from)throws IOException{var c=SectorNorthP2.fixtureContract(ctx,NORTH,from,cost(from),anchor);List<SectorTransaction.Cell> cells=new ArrayList<>(c.plan().cells());cells.add(new SectorTransaction.Cell(bookPos,Blocks.BARREL.defaultBlockState(),Blocks.BARREL.defaultBlockState()));var p=c.plan();return new SectorContracts.Contract(c.world(),c.city(),c.sector(),new SectorTransaction.Plan(p.sector(),p.sourceRevision(),p.targetRevision(),p.sourceLevel(),p.targetLevel(),cells,p.reads(),p.naturalSupport()),c.corners(),c.neighbors(),c.roads(),c.cost());}
        final SectorService.Provider provider=new SectorService.Provider(){public String priceVersion(){return SectorPriceCatalog.VERSION;}public SectorContracts.Contract resolve(SectorService.Context c,SectorContracts.Direction d,int from)throws IOException{if(disabled)throw new IOException("P2 test catalog unavailable");if(d!=NORTH)throw new IOException("P2 other side unavailable");return contract(from);}};
        MapAssetService.Result quote(){return SectorService.quoteFixture(ctx,NORTH,provider);}MapAssetService.Result op(SectorTransaction.Action a){return SectorService.operationFixture(ctx,NORTH,a,provider);}MapAssetService.Result op(SectorTransaction.Action a,SectorTransaction.Fault fault){return SectorService.operationFixture(ctx,NORTH,a,provider,fault);}
        Path pointed(){return root.resolve("data").resolve(data.placedMapAsset("sector_v1:a:north:operation"));}
        List<Path> histories()throws IOException{try(var stream=Files.newDirectoryStream(root.resolve("data"),"catancraft_sector_a_*_v1.dat")){List<Path> files=new ArrayList<>();for(Path p:stream)files.add(p);return files;}}
        CompoundTag book(){return ((BarrelBlockEntity)level.getBlockEntity(bookPos)).saveWithFullMetadata().copy();}
        Map<BlockPos,BlockState> scene(){try{Map<BlockPos,BlockState> out=new LinkedHashMap<>();var p=plan(0);for(var c:p.cells())out.put(c.pos(),level.getBlockState(c.pos()));for(var r:p.reads())out.put(r.pos(),level.getBlockState(r.pos()));return out;}catch(IOException e){throw new RuntimeException(e);}}
        void verifyPolicyScene(Map<BlockPos,BlockState> expected)throws IOException {
            Set<BlockPos> allowed=plan(0).naturalSupport().grassSupports();
            for(var entry:expected.entrySet()){
                BlockState actual=level.getBlockState(entry.getKey());if(actual.equals(entry.getValue()))continue;
                if(!allowed.contains(entry.getKey())||!entry.getValue().equals(Blocks.GRASS_BLOCK.defaultBlockState())||!actual.equals(Blocks.DIRT.defaultBlockState())||level.getBlockEntity(entry.getKey())!=null)throw new AssertionError("Unexpected delayed P2 drift: "+entry.getKey()+" "+entry.getValue()+" -> "+actual);
            }
        }
        String drift(Map<BlockPos,BlockState> expected){StringBuilder out=new StringBuilder();for(var e:expected.entrySet())if(!e.getValue().equals(level.getBlockState(e.getKey()))&&out.length()<2000)out.append(e.getKey()).append(": ").append(e.getValue()).append(" -> ").append(level.getBlockState(e.getKey())).append("; ");return out.toString();}
        void verify(SectorTransaction.Plan p,boolean after){for(var c:p.cells())if(!level.getBlockState(c.pos()).equals(after?c.after():c.before()))throw new AssertionError("P2 target mismatch "+c.pos());for(var r:p.reads())if(!level.getBlockState(r.pos()).equals(r.state()))throw new AssertionError("P2 protected source mismatch "+r.pos());}
        void stage()throws IOException{
            var p=plan(0);Map<BlockPos,BlockState> source=new LinkedHashMap<>();for(var c:p.cells())source.put(c.pos(),c.before());for(var r:p.reads())source.put(r.pos(),r.state());
            int minX=source.keySet().stream().mapToInt(BlockPos::getX).min().orElseThrow()-3,maxX=Math.max(bookPos.getX()+3,source.keySet().stream().mapToInt(BlockPos::getX).max().orElseThrow()+3),minZ=source.keySet().stream().mapToInt(BlockPos::getZ).min().orElseThrow()-3,maxZ=Math.max(bookPos.getZ()+3,source.keySet().stream().mapToInt(BlockPos::getZ).max().orElseThrow()+3);
            for(int x=minX>>4;x<=(maxX>>4);x++)for(int z=minZ>>4;z<=(maxZ>>4);z++){ChunkPos chunk=new ChunkPos(x,z);level.getChunkSource().addRegionTicket(TICKET,chunk,2,chunk,true);level.setChunkForced(x,z,true);level.getChunk(x,z);}
            for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)for(int y=anchor.getY()-2;y<=anchor.getY()+11;y++){BlockPos pos=new BlockPos(x,y,z);var be=level.getBlockEntity(pos);if(be instanceof net.minecraft.world.Container inv)inv.clearContent();level.setBlock(pos,y==anchor.getY()-2?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),FLAGS);}
            for(var r:source.entrySet())level.setBlock(r.getKey(),r.getValue(),FLAGS);level.setBlock(bookPos.below(),Blocks.STONE.defaultBlockState(),FLAGS);level.setBlock(bookPos,Blocks.BARREL.defaultBlockState(),FLAGS);ItemStack book=new ItemStack(Items.WRITTEN_BOOK);book.getOrCreateTag().putString("title","P2 owner management book");book.getOrCreateTag().putString("author","Owner");((BarrelBlockEntity)level.getBlockEntity(bookPos)).setItem(0,book);
        }
    }
}
