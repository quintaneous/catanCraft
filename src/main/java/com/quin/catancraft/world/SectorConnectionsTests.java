package com.quin.catancraft.world;

import com.quin.catancraft.data.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Internal twelve-cell coordinated primitive only; not complete P3 purchase/assembly acceptance. */
@GameTestHolder("catancraft_sector_connections") @PrefixGameTestTemplate(false)
public final class SectorConnectionsTests {
    private static final AtomicInteger SITES=new AtomicInteger();
    private static final int FLAGS=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS;
    private static final TicketType<ChunkPos> TICKET=TicketType.create("catancraft_connection_fixture",Comparator.comparingLong(ChunkPos::toLong));
    @GameTest(template="townhall_state_empty",batch="connections01",timeoutTicks=2000)
    public static void twelve_exclusive_cells_open_once_and_reverse_without_purchase_or_fee(GameTestHelper h){start(h,e->{
        CompoundTag nation=e.nation().save(),book=e.book();List<SectorConnections.Participant> paid=e.currentParticipants();
        h.assertTrue(SectorConnections.run(e,SectorConnections.Action.APPLY).writes()==12,"Connection did not coordinate all twelve cells");byte[] journal=Files.readAllBytes(e.file());h.assertTrue(SectorConnections.run(e,SectorConnections.Action.APPLY).writes()==0&&Arrays.equals(journal,Files.readAllBytes(e.file())),"Connection duplicate mutated retained history");
        h.assertTrue(SectorConnections.run(e,SectorConnections.Action.ROLLBACK).writes()==12,"Connection did not restore both six-cell terminals");h.assertTrue(SectorConnections.run(e,SectorConnections.Action.ROLLBACK).writes()==0,"Connection duplicate reverse wrote twice");
        e.closed();h.assertTrue(paid.equals(e.currentParticipants())&&nation.equals(e.nation().save())&&book.equals(e.book()),"Connection acquired neighbor geometry or changed purchased accounting/NBT");h.runAfterDelay(40,()->{e.closed();h.assertTrue(book.equals(e.book()),"Delayed connection book drift");h.succeed();});
    });}
    @GameTest(template="townhall_state_empty",batch="connections02",timeoutTicks=2000)
    public static void interrupted_forward_and_inverse_reload_preserve_actual_current_book(GameTestHelper h){start(h,e->{
        CompoundTag nation=e.nation().save();refuse(()->SectorConnections.run(e,SectorConnections.Action.APPLY,(stage,n)->{if(stage.equals("write")&&n==7)throw new IOException("Connection forward interruption");}));e.reload();e.barrel().setItem(1,new ItemStack(Items.EMERALD,11));CompoundTag book=e.book();h.assertTrue(SectorConnections.run(e,SectorConnections.Action.RESUME).writes()==5,"Connection did not resume exact mixed twelve-cell state");
        refuse(()->SectorConnections.run(e,SectorConnections.Action.ROLLBACK,(stage,n)->{if(stage.equals("rollback_write")&&n==5)throw new IOException("Connection inverse interruption");}));e.reload();h.assertTrue(SectorConnections.run(e,SectorConnections.Action.ROLLBACK).writes()==7,"Connection inverse did not resume exact mixed state");e.closed();h.assertTrue(nation.equals(e.nation().save())&&book.equals(e.book())&&e.firstReceipt().isEmpty()&&e.secondReceipt().isEmpty(),"Connection recovery lost source/NBT or changed accounting");h.succeed();
    });}
    @GameTest(template="townhall_state_empty",batch="connections03",timeoutTicks=2000)
    public static void final_cell_or_changed_purchased_neighbor_refuses_before_journal(GameTestHelper h){start(h,e->{
        CompoundTag nation=e.nation().save();var cap=e.plan.caps().get(11);e.level.setBlock(cap.pos(),Blocks.DIAMOND_BLOCK.defaultBlockState(),FLAGS);refuse(()->SectorConnections.run(e,SectorConnections.Action.APPLY));h.assertTrue(!Files.exists(e.file())&&nation.equals(e.nation().save()),"Final neighbor cap conflict partly opened/charged connection");e.level.setBlock(cap.pos(),cap.closed(),FLAGS);
        e.data.setPlacedMapAsset("connection_fixture:WEST:revision","foreign_revision");refuse(()->SectorConnections.run(e,SectorConnections.Action.APPLY));h.assertTrue(!Files.exists(e.file()),"Changed neighbor proof created connection history");e.closed();h.succeed();
    });}
    @GameTest(template="townhall_state_empty",batch="connections04",timeoutTicks=2000)
    public static void split_receipts_and_callback_inventory_mutations_refuse_without_erasing_evidence(GameTestHelper h){start(h,e->{
        e.receipt("orphan:open");refuse(()->SectorConnections.run(e,SectorConnections.Action.APPLY));h.assertTrue(!Files.exists(e.file())&&e.firstReceipt().equals("orphan:open")&&e.secondReceipt().equals("orphan:open"),"Unexplained matching receipts were adopted or erased");e.receipt("");
        e.data.setPlacedMapAsset("connection_fixture:linkA","foreign:open");refuse(()->SectorConnections.run(e,SectorConnections.Action.APPLY));h.assertTrue(!Files.exists(e.file()),"Split connection receipt was silently repaired");e.data.setPlacedMapAsset("connection_fixture:linkA","none");
        refuse(()->SectorConnections.run(e,SectorConnections.Action.APPLY,(stage,n)->{if(stage.equals("prepared"))e.barrel().setItem(1,new ItemStack(Items.DIAMOND,3));}));h.assertTrue(Files.exists(e.file())&&ProducerJournal.read(e.file()).getString("phase").equals("PREPARED")&&e.barrel().getItem(1).getCount()==3,"Callback mutation was erased or unjournaled");e.closed();h.succeed();
    });}
    private interface Body{void run(Env e)throws Exception;}private interface Checked{void run()throws IOException;}
    private static void refuse(Checked c)throws IOException{try{c.run();}catch(IOException expected){return;}throw new AssertionError("Expected guarded connection refusal");}
    private static void start(GameTestHelper h,Body body){try{Env e=new Env(h);e.stage();ready(h,e,body,0);}catch(Exception e){throw new RuntimeException(e);}}
    private static void ready(GameTestHelper h,Env e,Body body,int tries){h.runAfterDelay(tries==0?20:1,()->{try{StreetscapeService.requireLoadedEntities(e.level,List.of(e.entityBounds()));}catch(IOException loading){if(tries<400){ready(h,e,body,tries+1);return;}throw new RuntimeException(loading);}try{body.run(e);}catch(Exception ex){throw new RuntimeException(ex);}});}
    private static final class Env implements SectorConnections.Environment {
        final ServerLevel level;final BlockPos origin,bookPos;final Path root,file;final UUID owner,actor=UUID.randomUUID();final SectorConnections.Plan plan;final CompoundTag identity=new CompoundTag();CatanSavedData data=new CatanSavedData();final DefenseReservationVerifier reservations=new DefenseReservationVerifier();
        Env(GameTestHelper h)throws IOException{
            level=h.getLevel();origin=new BlockPos(7000+128*SITES.getAndIncrement(),140,7000);bookPos=origin.offset(5,0,2);root=level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().resolve("data/connection-fixtures/"+UUID.randomUUID());Files.createDirectories(root.resolve("data"));file=root.resolve("data/catancraft_connection_a_nw_"+UUID.randomUUID()+"_v1.dat");owner=data.createNation("Connection fixture",actor).id();
            List<SectorConnections.Participant> participants=new ArrayList<>();for(var side:List.of(SectorContracts.Direction.NORTH,SectorContracts.Direction.WEST)){String receipt=UUID.randomUUID()+":paid";data.setPlacedMapAsset("connection_fixture:"+side+":level","1");data.setPlacedMapAsset("connection_fixture:"+side+":paid",receipt);data.setPlacedMapAsset("connection_fixture:"+side+":revision","fixture_basic");participants.add(new SectorConnections.Participant(side,1,receipt,"fixture_basic"));}
            List<SectorConnections.Cap> caps=new ArrayList<>();List<SectorTransaction.Read> reads=new ArrayList<>();for(int z=0;z<2;z++)for(int x=0;x<3;x++){var side=z==0?SectorContracts.Direction.NORTH:SectorContracts.Direction.WEST;BlockPos p=origin.offset(x,0,z);caps.add(new SectorConnections.Cap(p,Blocks.STONE_BRICKS.defaultBlockState(),side));caps.add(new SectorConnections.Cap(p.above(),Blocks.STONE_BRICK_SLAB.defaultBlockState(),side));reads.add(new SectorTransaction.Read(p.below(),Blocks.STONE.defaultBlockState()));}reads.add(new SectorTransaction.Read(bookPos,Blocks.BARREL.defaultBlockState()));plan=new SectorConnections.Plan(SectorContracts.Corner.NORTH_WEST,SectorContracts.Direction.NORTH,participants,caps,reads);
            identity.putString("world",root.toString());identity.putString("city","a");identity.putString("corner","NW");identity.putUUID("owner",owner);identity.putUUID("actor",actor);
        }
        public ServerLevel level(){return level;}public Path file(){return file;}public SectorConnections.Plan plan(){return plan;}public CompoundTag identity(){return identity.copy();}
        public List<SectorConnections.Participant> currentParticipants(){List<SectorConnections.Participant> parts=new ArrayList<>();for(var side:List.of(SectorContracts.Direction.NORTH,SectorContracts.Direction.WEST))parts.add(new SectorConnections.Participant(side,Integer.parseInt(data.placedMapAsset("connection_fixture:"+side+":level")),data.placedMapAsset("connection_fixture:"+side+":paid"),data.placedMapAsset("connection_fixture:"+side+":revision")));return parts;}
        String value(String key){String v=data.placedMapAsset(key);return v==null||v.equals("none")?"":v;}public String firstReceipt(){return value("connection_fixture:linkA");}public String secondReceipt(){return value("connection_fixture:linkB");}public void receipt(String r){data.setPlacedMapAsset("connection_fixture:linkA",r.isEmpty()?"none":r);data.setPlacedMapAsset("connection_fixture:linkB",r.isEmpty()?"none":r);}
        public void save()throws IOException{CompoundTag n=new CompoundTag();n.put("ledger",data.save(new CompoundTag()));Path p=root.resolve("data/connection_fixture_ledger.dat");ProducerJournal.write(p,n,Files.exists(p));}void reload()throws IOException{data=CatanSavedData.load(ProducerJournal.read(root.resolve("data/connection_fixture_ledger.dat")).getCompound("ledger"));}
        public void requirements()throws IOException{if(!data.nation(owner).isLeader(actor))throw new IOException("Connection owner changed");reservations.requireAvailable(root,"a",file);}
        public void allow(BlockPos p,SectorContracts.Direction side)throws IOException{if(plan.caps().stream().noneMatch(cap->cap.pos().equals(p)&&cap.cellOwner()==side))throw new IOException("Connection cap owner changed");}
        NationData nation(){return data.nation(owner);}BarrelBlockEntity barrel(){return (BarrelBlockEntity)level.getBlockEntity(bookPos);}CompoundTag book(){return barrel().saveWithFullMetadata().copy();}
        void closed(){for(var cap:plan.caps())if(!level.getBlockState(cap.pos()).equals(cap.closed()))throw new AssertionError("Connection closed source differs "+cap.pos());}
        net.minecraft.world.phys.AABB entityBounds(){
            Set<BlockPos> positions=new HashSet<>();for(var cap:plan.caps()){positions.add(cap.pos());for(var d:net.minecraft.core.Direction.values())positions.add(cap.pos().relative(d));}for(var read:plan.reads())positions.add(read.pos());net.minecraft.world.phys.AABB bounds=null;for(BlockPos p:positions){var one=new net.minecraft.world.phys.AABB(p).inflate(2);bounds=bounds==null?one:bounds.minmax(one);}return bounds;
        }
        void stage(){for(int x=((int)Math.floor(entityBounds().minX)-32)>>4;x<=(((int)Math.ceil(entityBounds().maxX)+32)>>4);x++)for(int z=((int)Math.floor(entityBounds().minZ)-32)>>4;z<=(((int)Math.ceil(entityBounds().maxZ)+32)>>4);z++){ChunkPos c=new ChunkPos(x,z);level.getChunkSource().addRegionTicket(TICKET,c,2,c,true);level.setChunkForced(x,z,true);level.getChunk(x,z);}for(int x=-4;x<=9;x++)for(int z=-4;z<=8;z++)for(int y=-3;y<=5;y++)if(x==-4||x==9||z==-4||z==8||y==-3||y==5)level.setBlock(origin.offset(x,y,z),Blocks.GLASS.defaultBlockState(),FLAGS);for(int x=-3;x<=8;x++)for(int z=-3;z<=7;z++)for(int y=-2;y<=4;y++)level.setBlock(origin.offset(x,y,z),y<0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),FLAGS);for(var cap:plan.caps())level.setBlock(cap.pos(),cap.closed(),FLAGS);level.setBlock(bookPos,Blocks.BARREL.defaultBlockState(),FLAGS);ItemStack b=new ItemStack(Items.WRITTEN_BOOK);b.getOrCreateTag().putString("title","Actual connection management book");barrel().setItem(0,b);}
    }
}
