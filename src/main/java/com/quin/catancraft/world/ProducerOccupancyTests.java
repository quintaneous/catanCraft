package com.quin.catancraft.world;
import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.*;
import com.quin.catancraft.map.MapAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
@GameTestHolder("catancraft_public27") @PrefixGameTestTemplate(false)
public final class ProducerOccupancyTests {
    @GameTest(template="townhall_state_empty",batch="producer27_occupancy",timeoutTicks=1000)
    public static void retry_message_only_for_proven_fresh_refusal(GameTestHelper h) {
        try {
            if(!(h.getLevel().getServer() instanceof GameTestServer))throw new IOException("Fixture only");
            var e=new ProducerTests.Env(h,0,1);
            // A separate plot area prevents this extra inventory fixture from contaminating
            // retained Town Hall batches when GameTest lays out their templates afterwards.
            BlockPos offset=new BlockPos(12288-e.origin.getX(),81-e.origin.getY(),12288-e.origin.getZ());
            e.anchors.replaceAll((f,a)->new MapAnchor(f,a.x()+offset.getX(),a.y()+offset.getY(),a.z()+offset.getZ(),a.rotation()));
            ProducerTransaction.Environment isolated=new ProducerTransaction.Environment() {
                public ServerLevel level(){return e.level();}public Path file(){return e.file();}
                public ProducerTransaction.Spec spec(){return e.spec();}public CompoundTag identity(){return e.identity();}
                public NationData nation(){return e.nation();}public TerritoryData territory(){return e.territory();}
                public String token(String f){return e.token(f);}public void token(String f,String v){e.token(f,v);}
                public String receipt(){return e.receipt();}public void receipt(String v){e.receipt(v);}
                public CompoundTag ledger(){return e.ledger();}public void dirty(){e.dirty();}public void save()throws IOException{e.save();}
                public void allowWrite(BlockPos p)throws IOException{e.allowWrite(p.subtract(offset));}
            };
            // Keep the dedicated native fixture accessible; getChunk alone is not a
            // ticking/visible-entity ticket. Only this GameTestServer fixture is forced.
            for(var entry:e.anchors.entrySet()) {
                var bounds=TownHallRepairTransaction.bounds(ProducerRevisions.plan(entry.getKey(),1,entry.getValue()));
                for(int x=((int)bounds.minX-2)>>4;x<=((int)bounds.maxX+2)>>4;x++)
                    for(int z=((int)bounds.minZ-2)>>4;z<=((int)bounds.maxZ+2)>>4;z++)e.level().setChunkForced(x,z,true);
            }
            e.prepare(1);e.select(1,2);CompoundTag ledger=e.ledger().copy();
            var farm=TownHallRepairTransaction.bounds(ProducerRevisions.plan("farm",1,e.anchors.get("farm")));
            var p=ProducerTests.hazardPosition(e);
            var center=new net.minecraft.world.phys.Vec3(p.getX()+.5,p.getY()+.25,p.getZ()+.5);
            ItemEntity item=new ItemEntity(e.level(),center.x,center.y,center.z,new ItemStack(Items.STICK));item.setNoGravity(true);item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            h.assertTrue(e.level().addFreshEntity(item),"Fixture entity was not added");
            // Newly synchronously loaded chunks do not immediately expose their entities to
            // getEntities. Wait for native chunk/entity accessibility, then prove occupancy.
            h.runAfterDelay(5,()->{
                try {
                    h.assertTrue(e.level().getEntities((net.minecraft.world.entity.Entity)null,farm).contains(item),"Fixture entity not visible to native occupancy query");
                    boolean fresh=false;try{ProducerTransaction.run(isolated,ProducerTransaction.Action.APPLY);}
                    catch(ProducerTransaction.FreshOccupancyRefusal expected){fresh=true;}finally{item.discard();}
                    h.assertTrue(fresh,"Fresh occupied request was not classified as preflight refusal");
                    h.assertTrue(!Files.exists(e.file())&&ledger.equals(e.ledger()),"Fresh refusal changed journal or ledger");
                    boolean interrupted=false;
                    try{ProducerTransaction.run(isolated,ProducerTransaction.Action.APPLY,(s,n)->{if(s.equals("prepared"))throw new IOException("injected interruption");});}
                    catch(IOException expected){interrupted=expected.getMessage().equals("injected interruption");}
                    h.assertTrue(interrupted,"Prepared interruption was not reached");byte[] journal=Files.readAllBytes(e.file());
                    ItemEntity next=new ItemEntity(e.level(),center.x,center.y,center.z,new ItemStack(Items.STICK));next.setNoGravity(true);next.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                    h.assertTrue(e.level().addFreshEntity(next),"Recovery fixture entity not added");
                    try {
                        h.assertTrue(e.level().getEntities((net.minecraft.world.entity.Entity)null,farm).contains(next),"Recovery fixture not visible");
                        boolean recovery=false;try{ProducerTransaction.run(isolated,ProducerTransaction.Action.RESUME);}
                        catch(ProducerTransaction.FreshOccupancyRefusal wrong){throw new IllegalStateException("Pending journal misclassified as fresh retry",wrong);}
                        catch(IOException expected){recovery=expected.getMessage().startsWith("Construction hazard:");}
                        h.assertTrue(recovery&&Arrays.equals(journal,Files.readAllBytes(e.file()))&&ledger.equals(e.ledger()),"Pending occupancy changed journal/ledger or wrong refusal");
                    }finally{next.discard();}
                    ProducerTransaction.run(isolated,ProducerTransaction.Action.RESUME);
                    byte[] completed=Files.readAllBytes(e.file());CompoundTag paidLedger=e.ledger().copy();
                    ItemEntity standing=new ItemEntity(e.level(),p.getX()+.5,p.getY()+1,p.getZ()+.5,new ItemStack(Items.DIAMOND,7));
                    standing.setNoGravity(true);standing.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                    h.assertTrue(e.level().addFreshEntity(standing),"Reverse support fixture spawn failed");
                    h.runAfterDelay(5,()->{try {
                        h.assertTrue(e.level().getEntities((net.minecraft.world.entity.Entity)null,farm.inflate(2)).contains(standing),"Reverse support fixture invisible");
                        boolean refused=false;try{ProducerTransaction.run(isolated,ProducerTransaction.Action.ROLLBACK);}
                        catch(IOException hazard){refused=hazard.getMessage().startsWith("Construction hazard:");}
                        h.assertTrue(refused&&Arrays.equals(completed,Files.readAllBytes(e.file()))&&paidLedger.equals(e.ledger()),"Reverse hazard changed history/paid level/refund");
                        h.assertTrue(!standing.isRemoved()&&standing.getItem().getCount()==7,"Reverse refusal lost entity/item");
                        CatanCraft.LOGGER.info("PRODUCER_OCCUPANCY_CLASSIFICATION nativeQuery=PASS freshNoWritesNoCharge=PASS pendingRetained=PASS reverseSupport=PASS isolatedFixture=PASS");h.succeed();
                    }catch(Exception ex){throw new RuntimeException(ex);}finally{standing.discard();}});

                }catch(Exception ex){item.discard();throw new RuntimeException(ex);}
            });
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
}
