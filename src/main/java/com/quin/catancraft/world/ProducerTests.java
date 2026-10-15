package com.quin.catancraft.world;

import com.google.gson.*;
import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.*;
import com.quin.catancraft.economy.*;
import com.quin.catancraft.map.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Real delivered asset bytes, native world writes, actual journal I/O, and saved-data reload.
 * These injected interruptions are not process-kill or power-loss tests.
 */
@GameTestHolder("catancraft") @PrefixGameTestTemplate(false)
public final class ProducerTests {
    private static final int FLAGS=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS;
    private static final ProducerTransaction.Action APPLY=ProducerTransaction.Action.APPLY, RESUME=ProducerTransaction.Action.RESUME,
        ROLLBACK=ProducerTransaction.Action.ROLLBACK, STATUS=ProducerTransaction.Action.STATUS;
    private static final int[] GROUP_COUNTS={4291,4244,4775,3371};
    // Sequential GameTest batches reuse the test world. Clearing an earlier inventory fixture
    // can leave item drops which falsely occupy a later hall. This hook runs BEFORE that batch;
    // it cannot remove the occupancy entities deliberately created inside the guard tests.
    @BeforeBatch(batch="repair_guards")
    public static void isolateRepairGuards(ServerLevel level) {
        if(!(level.getServer() instanceof GameTestServer))throw new IllegalStateException("Test-world isolation only");
        List<ItemEntity> stale=new ArrayList<>();
        for(var entity:level.getAllEntities())if(entity instanceof ItemEntity item)stale.add(item);
        for(var item:stale)item.discard();
        CatanCraft.LOGGER.info("PRODUCER_TEST_ISOLATION priorFixtureItemsRemoved={}",stale.size());
    }
    @GameTest(template="townhall_state_empty",batch="producer_contract",timeoutTicks=600)
    public static void all_delivered_assets_deltas_and_applicability(GameTestHelper h) {
        try {
            int[][] counts={{571,858,789,733},{1612,1862,2028,1306},{2108,1524,1958,1332}};
            for(int fi=0;fi<3;fi++)for(int rotation:new int[]{0,180}) {
                String f=ProducerRevisions.FAMILIES.get(fi);MapAnchor anchor=new MapAnchor("fixture",100,81,100,rotation);
                for(int l=1;l<=5;l++) {
                    var plan=ProducerRevisions.plan(f,l,anchor);h.assertTrue(plan.size()==45167,"Full volume missing");
                    int barrels=0;for(var c:plan)if(c.state().is(Blocks.BARREL))barrels++;
                    h.assertTrue(barrels==(f.equals("farm")?6:f.equals("quarry")?3:5),"Protected barrel count changed");
                    if(l==1)continue;var previous=ProducerRevisions.plan(f,l-1,anchor);int changed=0;
                    for(int i=0;i<plan.size();i++)if(!plan.get(i).state().equals(previous.get(i).state())) {
                        changed++;h.assertTrue(!ProducerRevisions.protectedCell(plan.get(i).state()) && !ProducerRevisions.protectedCell(previous.get(i).state()),"Protected delta");
                        if(f.equals("quarry"))h.assertTrue(plan.get(i).localY()>=8,"Quarry pit mutation");
                    }
                    h.assertTrue(changed==counts[fi][l-2],"Delivered adjacent delta mismatch");
                }
            }
            try(Reader in=new InputStreamReader(Objects.requireNonNull(ProducerTests.class.getResourceAsStream("/data/catancraft/maps/season1_river_bridges_v3.json")))) {
                JsonObject map=JsonParser.parseReader(in).getAsJsonObject();int combinations=0;
                for(JsonElement row:map.getAsJsonArray("territories")) {
                    TerritoryDefinition d=new Gson().fromJson(row,TerritoryDefinition.class);ProducerService.validateMap(d);
                    combinations+=ProducerRevisions.applicable(d).size()*4;
                }
                h.assertTrue(combinations==80,"Unrelated producers would be selected");
            }
            refuse(h,()->ProducerRevisions.revision("farm","farm@r999"));
            h.assertTrue(!ProducerRevisions.matches(Blocks.AIR.defaultBlockState(),Blocks.WHEAT.defaultBlockState()),"Missing crop allowed");
            h.assertTrue(!ProducerRevisions.matches(Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL,1),Blocks.WATER.defaultBlockState()),"Unexpected flowing water allowed");
            CatanCraft.LOGGER.info("PRODUCER_CONTRACT all15Pins=PASS adjacent12Deltas=PASS bothRotations=PASS applicable=80");h.succeed();
        }catch(Exception x){throw new RuntimeException(x);}
    }
    @GameTest(template="townhall_state_empty",batch="producer_lifecycle",timeoutTicks=1200)
    public static void grouped_l1_to_l5_north_one_charge(GameTestHelper h){lifecycle(h,0);}
    @GameTest(template="townhall_state_empty",batch="producer_lifecycle",timeoutTicks=1200)
    public static void grouped_l1_to_l5_south_one_charge(GameTestHelper h){lifecycle(h,180);}
    @GameTest(template="townhall_state_empty",batch="producer_retrofit",timeoutTicks=600)
    public static void already_paid_l5_north_free_retrofit(GameTestHelper h){retrofit(h,0);}
    @GameTest(template="townhall_state_empty",batch="producer_retrofit",timeoutTicks=600)
    public static void already_paid_l5_south_free_retrofit(GameTestHelper h){retrofit(h,180);}
    @GameTest(template="townhall_state_empty",batch="producer_recovery",timeoutTicks=600)
    public static void interrupted_forward_and_rollback_north(GameTestHelper h){recovery(h,0);}
    @GameTest(template="townhall_state_empty",batch="producer_recovery",timeoutTicks=600)
    public static void interrupted_forward_and_rollback_south(GameTestHelper h){recovery(h,180);}
    @GameTest(template="townhall_state_empty",batch="producer_receipts",timeoutTicks=600)
    public static void saved_payment_receipt_prevents_second_charge(GameTestHelper h){receiptRecovery(h,false);}
    @GameTest(template="townhall_state_empty",batch="producer_receipts",timeoutTicks=600)
    public static void saved_refund_receipt_prevents_second_refund(GameTestHelper h){receiptRecovery(h,true);}
    @GameTest(template="townhall_state_empty",batch="producer_guards",timeoutTicks=600)
    public static void last_plot_conflict_refuses_entire_group(GameTestHelper h){guards(h);}
    @GameTest(template="townhall_state_empty",batch="producer_guards",timeoutTicks=600)
    public static void corrupt_history_and_changed_identity_refuse(GameTestHelper h){corruption(h);}
    @GameTest(template="townhall_state_empty",batch="producer_guards",timeoutTicks=600)
    public static void unaffordable_occupied_and_unapproved_refuse(GameTestHelper h){occupancy(h);}
    @GameTest(template="townhall_state_empty",batch="producer_ticks",timeoutTicks=600)
    public static void l5_producers_native_delayed_state_north(GameTestHelper h){delayed(h,0);}
    @GameTest(template="townhall_state_empty",batch="producer_ticks",timeoutTicks=600)
    public static void l5_producers_native_delayed_state_south(GameTestHelper h){delayed(h,180);}
    private static void lifecycle(GameTestHelper h,int rotation) {
        try {
            Env e=new Env(h,rotation,1);e.prepare(1);Map<BlockPos,CompoundTag> inventories=e.inventories();
            Map<Path,byte[]> histories=new HashMap<>();long total=0;
            for(int source=1;source<5;source++) {
                e.select(source,source+1);CompoundTag before=e.nation().save();EconomyCost cost=e.spec().cost();
                var result=ProducerTransaction.run(e,APPLY);h.assertTrue(result.writes()==GROUP_COUNTS[source-1],"Group writes");total+=result.writes();
                assertCharge(h,before,e.nation(),cost);h.assertTrue(e.territory().producerLevel()==source+1,"Paid level");e.assertGeometry(source+1);
                h.assertTrue(inventories.equals(e.inventories()),"Inventory NBT changed");
                byte[] journal=Files.readAllBytes(e.file());h.assertTrue(ProducerTransaction.run(e,APPLY).writes()==0,"Repeat wrote blocks");
                h.assertTrue(Arrays.equals(journal,Files.readAllBytes(e.file())),"Repeat rewrote journal");histories.put(e.file(),journal);e.reload();
            }
            CompoundTag paid=e.nation().save();EconomyCost last=e.spec().cost();
            h.assertTrue(ProducerTransaction.run(e,ROLLBACK).writes()==GROUP_COUNTS[3],"Rollback delta");
            h.assertTrue(e.nation().treasury()==paid.getLong("treasury")+last.money(),"Refund money");
            for(ResourceType type:ResourceType.values())h.assertTrue(e.nation().resource(type)==NationData.load(paid).resource(type)+last.resources().getOrDefault(type,0L),"Refund resources");
            e.assertGeometry(4);h.assertTrue(inventories.equals(e.inventories()),"Rollback lost NBT");
            h.assertTrue(ProducerTransaction.run(e,ROLLBACK).writes()==0,"Double refund");
            for(var entry:histories.entrySet())if(!entry.getKey().equals(e.file()))h.assertTrue(Arrays.equals(entry.getValue(),Files.readAllBytes(entry.getKey())),"Earlier journal changed");
            e.select(1,2);refuse(h,()->ProducerTransaction.run(e,ROLLBACK));
            CatanCraft.LOGGER.info("PRODUCER_LIFECYCLE rotation={} forwardWrites={} fourCharges=PASS rollback=PASS history=PASS inventory=PASS disk=PASS",rotation,total);h.succeed();
        }catch(Exception x){throw new RuntimeException(x);}
    }
    private static void retrofit(GameTestHelper h,int rotation) {
        try {
            Env e=new Env(h,rotation,5);e.prepare(1);e.select(1,5);CompoundTag before=e.nation().save();var inventories=e.inventories();
            ProducerTransaction.run(e,APPLY);e.assertGeometry(5);h.assertTrue(before.equals(e.nation().save()),"Retrofit charged");
            h.assertTrue(e.territory().producerLevel()==5 && inventories.equals(e.inventories()),"Retrofit altered paid level or NBT");
            e.reload();h.assertTrue(ProducerTransaction.run(e,APPLY).writes()==0,"Retrofit not idempotent");
            // A legitimate inventory edit after commit must survive rollback, not restore stale before-NBT.
            var barrel=(BarrelBlockEntity)e.level().getBlockEntity(inventories.keySet().iterator().next());barrel.setItem(1,new ItemStack(Items.EMERALD,11));var edited=e.inventories();
            ProducerTransaction.run(e,ROLLBACK);e.assertGeometry(1);h.assertTrue(edited.equals(e.inventories()),"Rollback erased newer inventory");
            h.assertTrue(before.equals(e.nation().save()) && e.territory().producerLevel()==5,"Retrofit rollback altered economy");
            CatanCraft.LOGGER.info("PRODUCER_RETROFIT rotation={} paid=5 charge=0 persisted=PASS currentInventoryRollback=PASS",rotation);h.succeed();
        }catch(Exception x){throw new RuntimeException(x);}
    }
    private static void recovery(GameTestHelper h,int rotation) {
        try {
            Env e=new Env(h,rotation,1);e.prepare(1);e.select(1,2);CompoundTag money=e.nation().save();var inventory=e.inventories();
            refuse(h,()->ProducerTransaction.run(e,APPLY,(stage,n)->{if(stage.equals("write") && n==7)throw new IOException("Injected interruption");}));
            h.assertTrue(money.equals(e.nation().save()) && e.territory().producerLevel()==1,"Charged partial visuals");e.reload();
            h.assertTrue(ProducerTransaction.run(e,RESUME).writes()==4284,"Resume count");assertCharge(h,money,e.nation(),e.spec().cost());
            refuse(h,()->ProducerTransaction.run(e,ROLLBACK,(stage,n)->{if(stage.equals("rollback_write") && n==9)throw new IOException("Injected rollback interruption");}));
            e.reload();h.assertTrue(ProducerTransaction.run(e,ROLLBACK).writes()==4282,"Reverse recovery count");
            h.assertTrue(money.equals(e.nation().save()) && inventory.equals(e.inventories()),"Recovery altered ledger/NBT");e.assertGeometry(1);
            CatanCraft.LOGGER.info("PRODUCER_RECOVERY rotation={} forward=7+4284 rollback=9+4282 exactlyOnce=PASS",rotation);h.succeed();
        }catch(Exception x){throw new RuntimeException(x);}
    }
    private static void receiptRecovery(GameTestHelper h,boolean rollback) {
        try {
            Env e=new Env(h,0,1);e.prepare(1);e.select(1,2);CompoundTag source=e.nation().save();
            if(rollback)ProducerTransaction.run(e,APPLY);
            refuse(h,()->ProducerTransaction.run(e,rollback?ROLLBACK:APPLY,(stage,n)->{if(stage.equals("ledger_saved"))throw new IOException("Injected saved receipt interruption");}));
            e.reload();CompoundTag durable=e.nation().save();
            h.assertTrue(ProducerTransaction.run(e,rollback?ROLLBACK:RESUME).writes()==0,"Receipt recovery wrote physical state twice");
            h.assertTrue(durable.equals(e.nation().save()),"Receipt recovery applied money twice");
            if(rollback)h.assertTrue(source.equals(durable),"Net balance not restored");else assertCharge(h,source,e.nation(),e.spec().cost());
            CatanCraft.LOGGER.info("PRODUCER_RECEIPT rollback={} zeroExtraWrites=PASS exactlyOnce=PASS",rollback);h.succeed();
        }catch(Exception x){throw new RuntimeException(x);}
    }
    private static void guards(GameTestHelper h) {
        try {
            Env e=new Env(h,0,1);e.prepare(1);e.select(1,2);var before=e.snapshot();CompoundTag ledger=e.ledger();
            var cells=ProducerRevisions.plan("lumberyard",1,e.anchors.get("lumberyard"));BlockPos p=cells.get(40000).position();BlockState old=e.level().getBlockState(p);
            e.level().setBlock(p,Blocks.DIAMOND_BLOCK.defaultBlockState(),FLAGS);refuse(h,()->ProducerTransaction.run(e,APPLY));
            h.assertTrue(!Files.exists(e.file()) && ledger.equals(e.ledger()),"Conflict wrote journal or charged");e.level().setBlock(p,old,FLAGS);h.assertTrue(before.equals(e.snapshot()),"Earlier plot changed");
            // Removing only a block entity is not a missing-barrel fixture: Minecraft recreates it
            // lazily from the still-present BARREL block. Remove the actual block, without fixture loot.
            BlockPos barrel=e.inventories().keySet().iterator().next();
            BlockState barrelState=e.level().getBlockState(barrel);
            BarrelBlockEntity original=(BarrelBlockEntity)e.level().getBlockEntity(barrel);
            CompoundTag originalNbt=original.saveWithFullMetadata().copy();original.clearContent();
            e.level().setBlock(barrel,Blocks.AIR.defaultBlockState(),FLAGS);
            h.assertTrue(e.level().getBlockState(barrel).isAir(),"Missing barrel fixture was not installed");
            refuse(h,()->ProducerTransaction.run(e,APPLY));
            h.assertTrue(!Files.exists(e.file()) && ledger.equals(e.ledger()),"Missing inventory accepted");
            e.level().setBlock(barrel,barrelState,FLAGS);
            e.level().getBlockEntity(barrel).load(originalNbt);
            h.assertTrue(originalNbt.equals(e.level().getBlockEntity(barrel).saveWithFullMetadata()),"Fixture NBT restoration failed");
            CatanCraft.LOGGER.info("PRODUCER_GUARDS lastPlotConflict=PASS fullGroupNoWrites=PASS missingBarrel=PASS");h.succeed();
        }catch(Exception x){throw new RuntimeException(x);}
    }
    private static void corruption(GameTestHelper h) {
        try {
            Env e=new Env(h,180,1);e.prepare(1);e.select(1,2);
            refuse(h,()->ProducerTransaction.run(e,APPLY,(stage,n)->{if(stage.equals("prepared"))throw new IOException("stop");}));
            byte[] journal=Files.readAllBytes(e.file());CompoundTag ledger=e.ledger();e.identitySuffix="wrong-world";
            refuse(h,()->ProducerTransaction.run(e,RESUME));e.identitySuffix="";
            byte[] damaged=journal.clone();damaged[damaged.length-4]^=1;Files.write(e.file(),damaged);refuse(h,()->ProducerTransaction.run(e,RESUME));
            Files.write(e.file(),journal);h.assertTrue(ledger.equals(e.ledger()),"Refusal changed ledger");ProducerTransaction.run(e,ROLLBACK);
            h.assertTrue(ledger.getList("nations",Tag.TAG_COMPOUND).equals(e.ledger().getList("nations",Tag.TAG_COMPOUND)),"Uncharged rollback refunded");
            CatanCraft.LOGGER.info("PRODUCER_JOURNAL corruptSeal=PASS wrongWorld=PASS preparedRollback=PASS");h.succeed();
        }catch(Exception x){throw new RuntimeException(x);}
    }
    private static void occupancy(GameTestHelper h) {
        try {
            Env e=new Env(h,0,1);e.prepare(1);e.select(1,2);long cash=e.nation().treasury();e.nation().addTreasury(-cash);
            refuse(h,()->ProducerTransaction.run(e,APPLY));h.assertTrue(!Files.exists(e.file()),"Unaffordable journal");e.nation().addTreasury(cash);
            BlockPos p=e.anchors.get("farm")==null?BlockPos.ZERO:new BlockPos(e.anchors.get("farm").x(),e.anchors.get("farm").y()+3,e.anchors.get("farm").z()+2);
            ItemEntity item=new ItemEntity(e.level(),p.getX()+.5,p.getY(),p.getZ()+.5,new ItemStack(Items.STICK));e.level().addFreshEntity(item);
            refuse(h,()->ProducerTransaction.run(e,APPLY));h.assertTrue(!Files.exists(e.file()),"Occupied plot mutated");item.discard();
            h.assertTrue(!ProducerService.upgrade(e.level().getServer(),"a").success(),"Unapproved world enabled");
            CatanCraft.LOGGER.info("PRODUCER_REFUSALS funds=PASS occupied=PASS worldAuthorization=PASS");h.succeed();
        }catch(Exception x){throw new RuntimeException(x);}
    }
    private static void delayed(GameTestHelper h,int rotation) {
        try {
            Env e=new Env(h,rotation,5);e.prepare(1);e.select(1,5);ProducerTransaction.run(e,APPLY);var inventory=e.inventories();
            h.runAfterDelay(10,()->{try {
                ProducerTransaction.run(e,STATUS);e.assertGeometry(5);h.assertTrue(inventory.equals(e.inventories()),"Delayed barrel drift");
                CatanCraft.LOGGER.info("PRODUCER_DELAYED rotation={} allThreeL5=PASS cropFluidState=PASS barrelNBT=PASS",rotation);h.succeed();
            }catch(Exception x){throw new RuntimeException(x);}});
        }catch(Exception x){throw new RuntimeException(x);}
    }
    private interface Checked {void run() throws IOException;}
    private static void refuse(GameTestHelper h,Checked c) throws IOException {boolean no=false;try{c.run();}catch(IOException|IllegalStateException expected){no=true;}h.assertTrue(no,"Expected fail-closed refusal");}
    private static void assertCharge(GameTestHelper h,CompoundTag before,NationData after,EconomyCost cost) {
        NationData old=NationData.load(before);h.assertTrue(after.treasury()==old.treasury()-cost.money(),"Not exactly one money charge");
        for(ResourceType type:ResourceType.values())h.assertTrue(after.resource(type)==old.resource(type)-cost.resources().getOrDefault(type,0L),"Not exactly one resource charge");
    }
    static final class Env implements ProducerTransaction.Environment {
        final GameTestHelper h;final Path dir;final UUID owner;final int rotation;final BlockPos origin;
        final Map<String,MapAnchor> anchors=new LinkedHashMap<>();CatanSavedData data=new CatanSavedData();ProducerTransaction.Spec spec;String identitySuffix="";
        Env(GameTestHelper h,int rotation,int paid) throws IOException {
            this.h=h;this.rotation=rotation;origin=h.absolutePos(new BlockPos(2,2,2));
            dir=level().getServer().getWorldPath(LevelResource.ROOT).resolve("data/producer26-tests/"+UUID.randomUUID());Files.createDirectories(dir);
            NationData nation=data.createNation("Producer fixture",UUID.randomUUID());owner=nation.id();nation.addTreasury(1000000);
            for(ResourceType type:ResourceType.values())nation.addResource(type,100000);
            TerritoryData t=data.createTerritory("a","Producer fixture",ResourceType.WOOD);t.setOwnerNationId(owner);t.setProducerLevel(paid);t.setCityLevel(5);
            int fi=0;for(String f:ProducerRevisions.FAMILIES) {
                int minX=origin.getX()+(fi==1?35:0),minZ=origin.getZ()+(fi==2?35:0);var a=SchematicAssetRegistry.asset(f);
                anchors.put(f,new MapAnchor(f,minX+(rotation==0?a.anchorX():30-a.anchorX()),origin.getY()+a.anchorY(),minZ+(rotation==0?a.anchorZ():30-a.anchorZ()),rotation));fi++;
            }
        }
        void select(int source,int target) {List<ProducerTransaction.Plot> plots=new ArrayList<>();for(String f:ProducerRevisions.FAMILIES)plots.add(new ProducerTransaction.Plot(f,source,target,anchors.get(f)));spec=new ProducerTransaction.Spec(territory().producerLevel(),target,plots);}
        void prepare(int visual) throws IOException {
            for(String f:ProducerRevisions.FAMILIES) {
                var plan=ProducerRevisions.plan(f,visual,anchors.get(f));var box=TownHallRepairTransaction.bounds(plan);
                for(int x=((int)box.minX-2)>>4;x<=((int)box.maxX+2)>>4;x++)for(int z=((int)box.minZ-2)>>4;z<=((int)box.maxZ+2)>>4;z++)level().getChunk(x,z);
                for(var c:plan) {
                    BlockState state=c.state();
                    if(state.is(Blocks.WHEAT)||state.is(Blocks.CARROTS))state=state.setValue(CropBlock.AGE,c.localX()%8);
                    if(state.is(Blocks.FARMLAND))state=state.setValue(FarmBlock.MOISTURE,c.localX()%8);
                    if(state.getBlock() instanceof DoorBlock)state=state.setValue(DoorBlock.OPEN,true);
                    level().setBlock(c.position(),state,FLAGS);
                    if(state.is(Blocks.BARREL)) {
                        var barrel=(BarrelBlockEntity)level().getBlockEntity(c.position());ItemStack stack=new ItemStack(Items.DIAMOND,17);
                        stack.setHoverName(net.minecraft.network.chat.Component.literal("Keep producer inventory"));barrel.setItem(0,stack);
                    }
                }
                token(f,ProducerRevisions.token(f,visual));
            }
            save();
        }
        void assertGeometry(int visual) throws IOException {for(String f:ProducerRevisions.FAMILIES)for(var c:ProducerRevisions.plan(f,visual,anchors.get(f)))h.assertTrue(ProducerRevisions.matches(level().getBlockState(c.position()),c.state()),"Producer geometry mismatch at "+c.position());}
        Map<BlockPos,BlockState> snapshot() throws IOException {Map<BlockPos,BlockState> out=new HashMap<>();for(String f:ProducerRevisions.FAMILIES)for(var c:ProducerRevisions.plan(f,1,anchors.get(f)))out.put(c.position(),level().getBlockState(c.position()));return out;}
        Map<BlockPos,CompoundTag> inventories() throws IOException {Map<BlockPos,CompoundTag> out=new HashMap<>();for(String f:ProducerRevisions.FAMILIES)for(var c:ProducerRevisions.plan(f,1,anchors.get(f)))if(c.state().is(Blocks.BARREL))out.put(c.position(),level().getBlockEntity(c.position()).saveWithFullMetadata().copy());return out;}
        public ServerLevel level(){return h.getLevel();}public Path file(){return dir.resolve("from"+spec.plots().get(0).source()+"to"+spec.paidAfter()+".dat");}
        public ProducerTransaction.Spec spec(){return spec;}public NationData nation(){return data.nation(owner);}public TerritoryData territory(){return data.territory("a");}
        public CompoundTag identity(){CompoundTag n=new CompoundTag();n.putString("world",dir+identitySuffix);n.putString("owner",String.valueOf(territory().ownerNationId()));return n;}
        public String token(String family){return data.placedMapAsset("producer:"+family);}public void token(String family,String value){data.setPlacedMapAsset("producer:"+family,value);}
        public String receipt(){return data.placedMapAsset("receipt");}public void receipt(String value){data.setPlacedMapAsset("receipt",value);}
        public CompoundTag ledger(){return data.save(new CompoundTag());}public void dirty(){data.setDirty();}
        public void save() throws IOException {try(OutputStream out=Files.newOutputStream(dir.resolve("ledger.nbt"))){NbtIo.writeCompressed(data.save(new CompoundTag()),out);}}
        void reload() throws IOException {try(InputStream in=Files.newInputStream(dir.resolve("ledger.nbt"))){data=CatanSavedData.load(NbtIo.readCompressed(in));}}
        public void allowWrite(BlockPos p) throws IOException {
            // The quarry full read contains six layers below a hall-style Y floor; writes must not.
            var a=anchors.get("quarry");int minX=origin.getX(),minZ=origin.getZ();
            if(p.getX()>=minX && p.getX()<=minX+30 && p.getZ()>=minZ && p.getZ()<=minZ+30 && p.getY()<origin.getY()+6)throw new IOException("Deep quarry write refused");
        }
    }
}
