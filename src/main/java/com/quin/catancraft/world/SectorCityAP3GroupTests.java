package com.quin.catancraft.world;

import com.quin.catancraft.data.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Real full City A P2/P3 geometry through grouped service. Disposable native worlds only. */
@GameTestHolder("catancraft_city_a_group") @PrefixGameTestTemplate(false)
public final class SectorCityAP3GroupTests {
    private static final AtomicInteger SITES=new AtomicInteger();
    private static final int FLAGS=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS;
    private static final TicketType<ChunkPos> TICKET=TicketType.create("catancraft_city_group_fixture",Comparator.comparingLong(ChunkPos::toLong));
    private static final SectorContracts.Direction N=SectorContracts.Direction.NORTH,E=SectorContracts.Direction.EAST,S=SectorContracts.Direction.SOUTH,W=SectorContracts.Direction.WEST;
    @GameTest(template="townhall_state_empty",batch="city_group01",timeoutTicks=24000)
    public static void complete_city_mixed_upgrade_open_north_oneprice_and_historical_duplicate(GameTestHelper h){start(h,f->{
        CompoundTag book=f.book();UUID harmless=f.harmless.getUUID();ItemStack stack=f.harmless.getItem().copy();
        f.buy(h,N);Path oldNorth=f.pointed(N);byte[] originalNorth=Files.readAllBytes(oldNorth);f.buy(h,E);f.buy(h,S);f.buy(h,W);
        f.assertAssembly();h.assertTrue(f.open().equals(Set.of("NW","NE","SW","SE")),"Full Basic city did not open all four exact owned corners");
        long money=f.nation().treasury();ok(h,f.op(N,SectorTransaction.Action.APPLY));h.assertTrue(f.nation().treasury()==money&&Arrays.equals(originalNorth,Files.readAllBytes(oldNorth)),"Historical North duplicate charged or rewrote old journal");
        f.buy(h,N);h.assertTrue(f.levels().equals(SectorCityAP3.levels(2,1,1,1)),"North upgrade with both portals open changed neighbors");f.assertAssembly();
        f.buy(h,E);f.buy(h,S);f.buy(h,W);f.assertAssembly();
        h.assertTrue(f.nation().treasury()==f.initialMoney-22000&&f.nation().resource(ResourceType.WOOD)==f.initialResources.get(ResourceType.WOOD)-60&&f.nation().resource(ResourceType.STONE)==f.initialResources.get(ResourceType.STONE)-120&&f.nation().resource(ResourceType.CONCRETE)==f.initialResources.get(ResourceType.CONCRETE)-200&&f.nation().resource(ResourceType.STEEL)==f.initialResources.get(ResourceType.STEEL)-60,"Four whole Basic+incremental Reinforced purchases did not cost exact $22000/W60/S120/C200/Steel60");
        f.reload();f.disabled=true;for(var side:SectorContracts.Direction.values())ok(h,f.op(side,SectorTransaction.Action.STATUS));
        h.runAfterDelay(40,()->{try{f.assertAssembly();h.assertTrue(book.equals(f.book())&&!f.harmless.isRemoved()&&harmless.equals(f.harmless.getUUID())&&ItemStack.matches(stack,f.harmless.getItem()),"Delayed assembly lost outside book or harmless item");h.succeed();}catch(IOException ex){throw new RuntimeException(ex);}});
    });}
    @GameTest(template="townhall_state_empty",batch="city_group02",timeoutTicks=24000)
    public static void coordinated_last_seam_interruption_reload_resume_and_current_rollback(GameTestHelper h){start(h,f->{
        CompoundTag book=f.book();f.buy(h,N);String northReceipt=f.state(N).receipt();long afterNorth=f.nation().treasury();var contract=f.contract(E);int lastForeign=0;var owners=SectorCityAP3.transition(E,f.levels(),f.open()).writeOwners();for(int i=0;i<contract.plan().cells().size();i++){var cell=contract.plan().cells().get(i);if(owners.get(cell.pos().subtract(f.delta))!=E)lastForeign=i+1;}h.assertTrue(lastForeign>0,"East purchase did not include north-owned coordinated seam");final int stop=lastForeign;
        ok(h,f.quote(E));no(h,f.op(E,SectorTransaction.Action.APPLY,(stage,n)->{if(stage.equals("write")&&n==stop)throw new IOException("Native final coordinated seam interruption");}));
        h.assertTrue(f.state(E).level()==0&&f.nation().treasury()==afterNorth-1500,"Interrupted side exposed completed tier or failed one-charge reservation");f.reload();f.disabled=true;ok(h,f.op(E,SectorTransaction.Action.RESUME));f.assertAssembly();h.assertTrue(f.state(E).level()==1&&f.open().equals(Set.of("NE"))&&f.nation().treasury()==afterNorth-1500,"Historical coordinated resume lost exact connection or charged twice");
        no(h,f.op(N,SectorTransaction.Action.ROLLBACK));f.assertAssembly();h.assertTrue(f.state(N).receipt().equals(northReceipt),"Superseded North rollback changed ownership");
        ok(h,f.op(E,SectorTransaction.Action.ROLLBACK));h.assertTrue(f.state(E).level()==0&&f.state(E).receipt().isEmpty()&&f.state(N).receipt().equals(northReceipt)&&f.open().isEmpty()&&f.nation().treasury()==afterNorth,"Current rollback did not restore both terminal masks and one paid progress/refund");f.assertAssembly();ok(h,f.op(E,SectorTransaction.Action.ROLLBACK));h.assertTrue(f.nation().treasury()==afterNorth&&book.equals(f.book()),"Duplicate group rollback refunded twice or lost management book");
        h.runAfterDelay(40,()->{try{f.assertAssembly();h.assertTrue(book.equals(f.book()),"Delayed rollback book drift");h.succeed();}catch(IOException ex){throw new RuntimeException(ex);}});
    });}
    @GameTest(template="townhall_state_empty",batch="city_group03",timeoutTicks=24000)
    public static void late_source_conflict_and_missing_retained_grandparent_refuse_without_charge(GameTestHelper h){start(h,f->{
        var plan=f.contract(N).plan();var last=plan.cells().get(plan.cells().size()-1);f.level.setBlock(last.pos(),Blocks.DIAMOND_BLOCK.defaultBlockState(),FLAGS);CompoundTag nation=f.nation().save();ok(h,f.quote(N));no(h,f.op(N,SectorTransaction.Action.APPLY));h.assertTrue(f.histories().isEmpty()&&nation.equals(f.nation().save())&&f.state(N).level()==0&&f.level.getBlockState(last.pos()).is(Blocks.DIAMOND_BLOCK),"Late full-city conflict partially charged/placed side");for(var cell:plan.cells())h.assertTrue(f.level.getBlockState(cell.pos()).equals(cell.pos().equals(last.pos())?Blocks.DIAMOND_BLOCK.defaultBlockState():cell.before()),"Conflict changed earlier component");f.level.setBlock(last.pos(),last.before(),FLAGS);
        f.buy(h,N);Path north=f.pointed(N);f.buy(h,E);nation=f.nation().save();String head=f.data.placedMapAsset("city_group_v1:a:operation");Path retained=north.resolveSibling(north.getFileName()+".retained-evidence");Files.move(north,retained);
        try{no(h,f.quote(S));h.assertTrue(nation.equals(f.nation().save())&&head.equals(f.data.placedMapAsset("city_group_v1:a:operation"))&&f.state(S).level()==0&&Files.exists(retained),"Missing ancestor was silently adopted or charged");}finally{Files.move(retained,north);}
        f.assertAssembly();h.succeed();
    });}
    @GameTest(template="townhall_state_empty",batch="city_group04",timeoutTicks=36000)
    public static void bounded_full_city_all_sides_and_connected_north_upgrade(GameTestHelper h){start(h,f->boundedBuy(h,f,0));}
    private static void boundedBuy(GameTestHelper h,Fixture f,int index)throws IOException{
        var sequence=List.of(N,E,S,W,N);
        if(index==sequence.size()){
            f.assertAssembly();h.assertTrue(f.levels().equals(SectorCityAP3.levels(2,1,1,1))&&f.open().size()==4,"Bounded mixed city lost connections or North upgrade");
            h.assertTrue(f.nation().treasury()==f.initialMoney-10000&&f.nation().resource(ResourceType.WOOD)==f.initialResources.get(ResourceType.WOOD)-60&&f.nation().resource(ResourceType.STONE)==f.initialResources.get(ResourceType.STONE)-120&&f.nation().resource(ResourceType.CONCRETE)==f.initialResources.get(ResourceType.CONCRETE)-50&&f.nation().resource(ResourceType.STEEL)==f.initialResources.get(ResourceType.STEEL)-15,"Bounded group price mismatch");
            f.reload();for(var side:SectorContracts.Direction.values())ok(h,f.op(side,SectorTransaction.Action.STATUS));h.succeed();return;
        }
        var side=sequence.get(index);int source=f.state(side).level();long started=System.nanoTime();ok(h,f.quote(side));
        ok(h,SectorService.operationFixtureBounded(f.ctx,side,SectorTransaction.Action.APPLY,f.provider));
        com.mojang.logging.LogUtils.getLogger().info("City A bounded {} quote and preparation: {} ms",side,(System.nanoTime()-started)/1_000_000.0);
        h.assertTrue(f.state(side).level()==source,"Preparation exposed purchased level");
        no(h,SectorService.operationFixtureBounded(f.ctx,side,SectorTransaction.Action.APPLY,f.provider));
        boundedWait(h,f,index,source);
    }
    private static void boundedWait(GameTestHelper h,Fixture f,int index,int source){h.runAfterDelay(1,()->{try{
        var side=List.of(N,E,S,W,N).get(index);
        if(SectorService.activeFixture(f.ctx)){h.assertTrue(f.state(side).level()==source,"Pending runner exposed target level");boundedWait(h,f,index,source);return;}
        h.assertTrue(f.state(side).level()==source+1,"Bounded group failed before completion; inspect runner refusal log");f.assertAssembly();boundedBuy(h,f,index+1);
    }catch(IOException error){throw new RuntimeException(error);}});}
    private interface Body{void run(Fixture f)throws Exception;}
    private static void start(GameTestHelper h,Body body){try{Fixture f=new Fixture(h);f.stage();ready(h,f,body,0);}catch(Exception ex){throw new RuntimeException(ex);}}
    private static void ready(GameTestHelper h,Fixture f,Body body,int tries){h.runAfterDelay(tries==0?20:1,()->{try{StreetscapeService.requireLoadedEntities(f.level,List.of(f.readiness));}catch(IOException loading){if(tries<600){ready(h,f,body,tries+1);return;}throw new RuntimeException("Full-city entities never became genuinely available",loading);}try{h.assertTrue(f.level.getEntitiesOfClass(ItemEntity.class,new AABB(f.harmless.blockPosition()).inflate(2)).stream().anyMatch(e->e.getUUID().equals(f.harmless.getUUID())),"Harmless item not visible in native entity query");body.run(f);}catch(Exception ex){throw new RuntimeException(ex);}});}
    private static void ok(GameTestHelper h,MapAssetService.Result result){h.assertTrue(result.success(),result.message());}private static void no(GameTestHelper h,MapAssetService.Result result){h.assertTrue(!result.success(),"Expected refusal: "+result.message());}
    private static final class Fixture {
        final ServerLevel level;final BlockPos delta,bookPos;final Path root;final UUID actor=UUID.randomUUID(),owner;final String worldId=UUID.randomUUID().toString();final long initialMoney;final Map<ResourceType,Long> initialResources;final Map<BlockPos,BlockState> fresh;final AABB readiness;CatanSavedData data=new CatanSavedData();SectorService.Context ctx;ItemEntity harmless;boolean disabled;
        Fixture(GameTestHelper h)throws IOException{
            level=h.getLevel();if(!(level.getServer() instanceof GameTestServer))throw new IOException("Full-city fixture requires disposable GameTestServer");BlockPos relative=h.absolutePos(new BlockPos(14000+320*SITES.getAndIncrement(),0,14000));BlockPos origin=new BlockPos(relative.getX(),260,relative.getZ());delta=origin.subtract(new BlockPos(-845,79,-853));root=level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().resolve("data/city-a-group-fixture/"+UUID.randomUUID());var nation=data.createNation("City A actual P3 fixture",actor);owner=nation.id();nation.addTreasury(100000);for(ResourceType type:List.of(ResourceType.WOOD,ResourceType.STONE,ResourceType.CONCRETE,ResourceType.STEEL))nation.addResource(type,10000);initialMoney=nation.treasury();initialResources=Map.copyOf(nation.stockpileView());data.createTerritory("a","Native City A",ResourceType.WOOD).setOwnerNationId(owner);context();
            Map<BlockPos,BlockState> placed=new HashMap<>();SectorCityAP3.assembly(SectorCityAP3.levels(0,0,0,0),Set.of()).states().forEach((p,s)->placed.put(p.offset(delta),s));fresh=Collections.unmodifiableMap(placed);
            int minX=fresh.keySet().stream().mapToInt(BlockPos::getX).min().orElseThrow(),maxX=fresh.keySet().stream().mapToInt(BlockPos::getX).max().orElseThrow(),minY=fresh.keySet().stream().mapToInt(BlockPos::getY).min().orElseThrow(),maxY=fresh.keySet().stream().mapToInt(BlockPos::getY).max().orElseThrow(),minZ=fresh.keySet().stream().mapToInt(BlockPos::getZ).min().orElseThrow(),maxZ=fresh.keySet().stream().mapToInt(BlockPos::getZ).max().orElseThrow();
            bookPos=new BlockPos(maxX+6,minY+2,minZ+5);readiness=new AABB(minX-4,minY-4,minZ-4,maxX+10,maxY+5,maxZ+5);
        }
        void context()throws IOException{ctx=SectorService.fixtureContext(level.getServer(),root,data,"a",actor,worldId,()->100L);}void reload()throws IOException{data=SectorService.reloadFixture(ctx);context();}
        final SectorService.Provider provider=new SectorService.Provider(){public SectorContracts.Contract resolve(SectorService.Context c,SectorContracts.Direction side,int source)throws IOException{if(disabled)throw new IOException("Current fixture catalog disabled after journal checkpoint");return SectorCityAP3Group.fixtureContract(c,side,source,delta);}public String priceVersion(){return SectorPriceCatalog.VERSION;}};
        SectorContracts.Contract contract(SectorContracts.Direction side)throws IOException{return SectorCityAP3Group.fixtureContract(ctx,side,state(side).level(),delta);}MapAssetService.Result quote(SectorContracts.Direction side){return SectorService.quoteFixture(ctx,side,provider);}MapAssetService.Result op(SectorContracts.Direction side,SectorTransaction.Action action){return SectorService.operationFixture(ctx,side,action,provider);}MapAssetService.Result op(SectorContracts.Direction side,SectorTransaction.Action action,SectorTransaction.Fault fault){return SectorService.operationFixture(ctx,side,action,provider,fault);}
        void buy(GameTestHelper h,SectorContracts.Direction side){
            long started=System.nanoTime();ok(h,quote(side));ok(h,op(side,SectorTransaction.Action.APPLY));
            com.mojang.logging.LogUtils.getLogger().info("City A fixture {} complete quote/purchase: {} ms",side,(System.nanoTime()-started)/1_000_000);
        }
        NationData nation(){return data.nation(owner);}SectorService.State state(SectorContracts.Direction side)throws IOException{return SectorService.savedState(data,"a",side);}Map<SectorContracts.Direction,Integer> levels()throws IOException{var out=new EnumMap<SectorContracts.Direction,Integer>(SectorContracts.Direction.class);for(var side:SectorContracts.Direction.values())out.put(side,state(side).level());return Map.copyOf(out);}
        Path pointed(SectorContracts.Direction side){return root.resolve("data").resolve(data.placedMapAsset("sector_v1:a:"+side.name().toLowerCase(Locale.ROOT)+":operation"));}
        Set<String> open()throws IOException{String file=data.placedMapAsset("city_group_v1:a:operation");if(file==null||file.equals("none"))return Set.of();Set<String> result=new HashSet<>();for(var tag:ProducerJournal.read(root.resolve("data").resolve(file)).getCompound("identity").getCompound("cityGroup").getList("afterOpenCorners",net.minecraft.nbt.Tag.TAG_STRING))result.add(tag.getAsString());return Set.copyOf(result);}
        List<Path> histories()throws IOException{try(var files=Files.newDirectoryStream(root.resolve("data"),"catancraft_sector_a_*_v1.dat")){List<Path> result=new ArrayList<>();for(Path p:files)result.add(p);return result;}}
        CompoundTag book(){return ((BarrelBlockEntity)level.getBlockEntity(bookPos)).saveWithFullMetadata().copy();}
        void assertAssembly()throws IOException{SectorCityAP3Group.verifyCurrentAssembly(ctx);var expected=SectorCityAP3.assembly(levels(),open());for(BlockPos p:SectorCityAP3.ownership().keySet())if(!level.getBlockState(p.offset(delta)).equals(expected.states().get(p)))throw new AssertionError("Actual City A owned target/cap differs at "+p);}
        void stage()throws IOException{
            for(int x=((int)Math.floor(readiness.minX)-16)>>4;x<=(((int)Math.ceil(readiness.maxX)+16)>>4);x++)for(int z=((int)Math.floor(readiness.minZ)-16)>>4;z<=(((int)Math.ceil(readiness.maxZ)+16)>>4);z++){ChunkPos chunk=new ChunkPos(x,z);level.getChunkSource().addRegionTicket(TICKET,chunk,2,chunk,true);level.setChunkForced(x,z,true);level.getChunk(x,z);}
            int minX=(int)readiness.minX+2,maxX=(int)readiness.maxX-8,minY=(int)readiness.minY+3,maxY=(int)readiness.maxY-2,minZ=(int)readiness.minZ+2,maxZ=(int)readiness.maxZ-2;
            // Synthetic enclosure is outside every immutable supplied context cell. No entities are removed.
            for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){safeGlass(new BlockPos(x,minY,z));safeGlass(new BlockPos(x,maxY,z));}
            for(int y=minY+1;y<maxY;y++){for(int x=minX;x<=maxX;x++){safeGlass(new BlockPos(x,y,minZ));safeGlass(new BlockPos(x,y,maxZ));}for(int z=minZ+1;z<maxZ;z++){safeGlass(new BlockPos(minX,y,z));safeGlass(new BlockPos(maxX,y,z));}}
            // Supplied strips omit terrain beneath their gravel. Add only missing external fixture support.
            for(var row:fresh.entrySet())if(row.getValue().getBlock() instanceof FallingBlock){BlockPos below=row.getKey().below();if(!fresh.containsKey(below)){if(SectorCityAP3.ownership().containsKey(below.subtract(delta)))throw new IOException("Synthetic gravity support enters authored ownership");level.setBlock(below,Blocks.GLASS.defaultBlockState(),FLAGS);}}
            for(var row:fresh.entrySet())level.setBlock(row.getKey(),row.getValue(),FLAGS);
            level.setBlock(bookPos.below(),Blocks.GLASS.defaultBlockState(),FLAGS);level.setBlock(bookPos,Blocks.BARREL.defaultBlockState(),FLAGS);ItemStack book=new ItemStack(Items.WRITTEN_BOOK);book.getOrCreateTag().putString("title","Full City A grouped owner management book");book.getOrCreateTag().putString("author","Owner");book.getOrCreateTag().putUUID("ownership",owner);((BarrelBlockEntity)level.getBlockEntity(bookPos)).setItem(0,book);
            BlockPos itemPos=new BlockPos(-750,85,-750).offset(delta);harmless=new ItemEntity(level,itemPos.getX()+.5,itemPos.getY()+.2,itemPos.getZ()+.5,new ItemStack(Items.EMERALD,13));harmless.setNoGravity(true);harmless.setDeltaMovement(Vec3.ZERO);level.addFreshEntity(harmless);
        }
        void safeGlass(BlockPos pos)throws IOException{if(fresh.containsKey(pos))throw new IOException("Synthetic enclosure would overwrite pinned source context");level.setBlock(pos,Blocks.GLASS.defaultBlockState(),FLAGS);}
    }
}
