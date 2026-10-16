package com.quin.catancraft.world;

import com.quin.catancraft.data.*;
import com.quin.catancraft.economy.EconomyCost;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Native fixtures only; injected costs exercise accounting without choosing game prices. */
@GameTestHolder("catancraft_defense") @PrefixGameTestTemplate(false)
public final class DefenseTests {
    @GameTest(template="townhall_state_empty",batch="defense_contract",timeoutTicks=600)
    public static void all_864_exact_registered_sockets_and_deferred_refusal(GameTestHelper h){
        try{
            int plans=0;for(char c='a';c<='p';c++){
                var list=DefenseRevisions.city(String.valueOf(c));h.assertTrue(list.size()==54,"Missing compatible defense sockets");
                for(var p:list){plans++;h.assertTrue(DefenseCostCatalog.cost(p.module()).money()>0,"Registered defense has no explicit nonfree price");Set<BlockPos> used=new HashSet<>();for(var cell:p.cells()){h.assertTrue(used.add(cell.pos()),"Duplicate sparse write");h.assertTrue(cell.before().isAir(),"Terrain overwrite candidate");h.assertTrue(!cell.after().isAir(),"Air clearing candidate");}for(String dep:p.dependencies())DefenseRevisions.plan(p.city(),dep);}
            }
            h.assertTrue(DefenseCostCatalog.cost("r4a_wall_6").money()==500 && DefenseCostCatalog.cost("r4a_wall_7").money()==500 && DefenseCostCatalog.cost("r4a_wall_end_cap").money()==500 && DefenseCostCatalog.cost("r4a_checkpoint_wing").money()==500,"Short authored wall price band changed");
            h.assertTrue(DefenseCostCatalog.cost("r4a_wall_14").money()==1000 && DefenseCostCatalog.cost("r4a_wall_straight_16").money()==1000,"Long wall price band changed");
            h.assertTrue(DefenseCostCatalog.cost("r6_compact_casemate").money()==4000 && DefenseCostCatalog.cost("r6_compact_casemate").resources().equals(Map.of(ResourceType.CONCRETE,40L,ResourceType.STEEL,15L)),"Casemate price changed");
            for(String id:List.of("r4a_wall_6","r4a_wall_7","r4a_wall_end_cap","r4a_checkpoint_wing","r6_low_wall_8"))assertCost(h,id,500,Map.of(ResourceType.WOOD,5L,ResourceType.STONE,15L));
            for(String id:List.of("r4a_wall_14","r4a_wall_straight_16","r6_low_wall_16"))assertCost(h,id,1000,Map.of(ResourceType.WOOD,10L,ResourceType.STONE,30L));
            assertCost(h,"r4a_wall_corner",750,Map.of(ResourceType.WOOD,10L,ResourceType.STONE,20L));assertCost(h,"r4a_checkpoint",2000,Map.of(ResourceType.WOOD,20L,ResourceType.STONE,40L));assertCost(h,"r4a_guard_tower",3000,Map.of(ResourceType.WOOD,20L,ResourceType.STONE,50L,ResourceType.STEEL,10L));
            expectRefusal(()->DefenseCostCatalog.cost("r4b_vehicle_gate_open"));
            CompoundTag bad=new CompoundTag();bad.putLong("money",-1);expectRefusal(()->DefenseService.historicalCost(bad));bad.putLong("money",1);bad.putLong("unknown",2);expectRefusal(()->DefenseService.historicalCost(bad));
            historySelection(h.getLevel());
            h.assertTrue(plans==864,"Coverage mismatch");for(String denied:List.of("access_gate","gate_wing_w","bunker_w","bunker_join_n_w","west_02","trench"))expectRefusal(()->DefenseRevisions.plan("a",denied));h.succeed();
        }catch(Exception x){throw new RuntimeException(x);}
    }
    @GameTest(template="townhall_state_empty",batch="defense_native",timeoutTicks=1200)
    public static void native_a_i_one_charge_duplicate_rollback_exact_refund(GameTestHelper h){
        try{for(String city:List.of("a","i"))for(String socket:List.of("front_w_04","r6_casemate_w","r6_west_01_half_0")){
            Fixture f=new Fixture(h.getLevel(),city,socket);f.stage();long money=f.nation.treasury();
            var result=DefenseTransaction.run(f,DefenseTransaction.Action.APPLY);h.assertTrue(result.writes()==f.plan.cells().size(),"Not all real sparse writes executed");
            h.assertTrue(f.nation.treasury()==money-73 && f.nation.resource(ResourceType.STONE)==97,"Purchase not charged once");
            h.assertTrue(DefenseTransaction.run(f,DefenseTransaction.Action.APPLY).writes()==0,"Duplicate repasted");h.assertTrue(f.nation.treasury()==money-73,"Duplicate charged");
            DefenseTransaction.run(f,DefenseTransaction.Action.ROLLBACK);h.assertTrue(f.nation.treasury()==money&&f.nation.resource(ResourceType.STONE)==100,"Exact refund failed");
            for(var c:f.plan.cells())h.assertTrue(f.level.getBlockState(c.pos()).equals(c.before()),"Rollback source mismatch");h.assertTrue(DefenseTransaction.run(f,DefenseTransaction.Action.ROLLBACK).writes()==0,"Duplicate refund");
        }h.succeed();}catch(Exception x){throw new RuntimeException(x);}
    }
    @GameTest(template="townhall_state_empty",batch="defense_recovery",timeoutTicks=1200)
    public static void reserve_partial_resume_and_rollback_drift_refusal(GameTestHelper h){
        try{
            Fixture f=new Fixture(h.getLevel(),"a","front_e_04");f.stage();long money=f.nation.treasury();
            expectRefusal(()->DefenseTransaction.run(f,DefenseTransaction.Action.APPLY,(stage,n)->{if(stage.equals("write")&&n==3)throw new IOException("Injected interruption");}));
            h.assertTrue(f.receipt.endsWith(":reserved")&&f.nation.treasury()==money-73,"Payment not reserved durably before writes");
            DefenseTransaction.run(f,DefenseTransaction.Action.RESUME);h.assertTrue(f.nation.treasury()==money-73,"Resume double charged");
            var first=f.plan.cells().get(0);f.level.setBlock(first.pos(),Blocks.DIAMOND_BLOCK.defaultBlockState(),2);
            expectRefusal(()->DefenseTransaction.run(f,DefenseTransaction.Action.ROLLBACK));h.assertTrue(f.nation.treasury()==money-73,"Conflict refunded committed purchase");
            f.level.setBlock(first.pos(),first.after(),2);DefenseTransaction.run(f,DefenseTransaction.Action.ROLLBACK);h.assertTrue(f.nation.treasury()==money,"Recovery refund failed");h.succeed();
        }catch(Exception x){throw new RuntimeException(x);}
    }
    @GameTest(template="townhall_state_empty",batch="defense_refusal",timeoutTicks=1200)
    public static void source_support_dependency_owner_refusal_before_charge(GameTestHelper h){
        try{
            Fixture f=new Fixture(h.getLevel(),"i","front_e_04");f.stage();long money=f.nation.treasury();
            var first=f.plan.cells().get(0);f.level.setBlock(first.pos(),Blocks.GOLD_BLOCK.defaultBlockState(),2);
            expectRefusal(()->DefenseTransaction.run(f,DefenseTransaction.Action.APPLY));h.assertTrue(!Files.exists(f.file)&&f.nation.treasury()==money,"Fresh conflict changed payment/history");
            f.level.setBlock(first.pos(),first.before(),2);f.authorized=false;
            expectRefusal(()->DefenseTransaction.run(f,DefenseTransaction.Action.APPLY));h.assertTrue(!Files.exists(f.file)&&f.nation.treasury()==money,"Owner/dependency refusal charged");
            f.authorized=true;var read=f.plan.reads().get(0);f.level.setBlock(read.pos(),Blocks.GOLD_BLOCK.defaultBlockState(),2);
            expectRefusal(()->DefenseTransaction.run(f,DefenseTransaction.Action.APPLY));h.assertTrue(!Files.exists(f.file)&&f.nation.treasury()==money,"Read-only support conflict charged");h.succeed();
        }catch(Exception x){throw new RuntimeException(x);}
    }
    @GameTest(template="townhall_state_empty",batch="defense_compatible",timeoutTicks=1200)
    public static void checkpoint_wings_owned_overlap_dependency_and_reverse_order(GameTestHelper h){
        try{
            Fixture checkpoint=new Fixture(h.getLevel(),"a","access_checkpoint"),wing=new Fixture(h.getLevel(),"a","checkpoint_wing_w");
            wing.installed=checkpoint.installed;checkpoint.stage();wing.stage();
            expectRefusal(()->DefenseTransaction.run(wing,DefenseTransaction.Action.APPLY));
            DefenseTransaction.run(checkpoint,DefenseTransaction.Action.APPLY);DefenseTransaction.run(wing,DefenseTransaction.Action.APPLY);
            h.assertTrue(DefenseTransaction.run(checkpoint,DefenseTransaction.Action.APPLY).writes()==0,"Compatible wing invalidated owned checkpoint");
            expectRefusal(()->DefenseTransaction.run(checkpoint,DefenseTransaction.Action.ROLLBACK));
            DefenseTransaction.run(wing,DefenseTransaction.Action.ROLLBACK);DefenseTransaction.run(checkpoint,DefenseTransaction.Action.ROLLBACK);h.succeed();
        }catch(Exception x){throw new RuntimeException(x);}
    }
    @GameTest(template="townhall_state_empty",batch="defense_no_money",timeoutTicks=1200)
    public static void insufficient_money_refuses_without_writes_or_history(GameTestHelper h){
        try{Fixture f=new Fixture(h.getLevel(),"a","front_w_04");f.stage();f.nation.addTreasury(-f.nation.treasury());
            expectRefusal(()->DefenseTransaction.run(f,DefenseTransaction.Action.APPLY));
            h.assertTrue(f.nation.treasury()==0&&f.nation.resource(ResourceType.STONE)==100&&!Files.exists(f.file),"Insufficient money changed accounting/history");for(var c:f.plan.cells())h.assertTrue(f.level.getBlockState(c.pos()).equals(c.before()),"Insufficient money wrote blocks");h.succeed();
        }catch(Exception x){throw new RuntimeException(x);}
    }
    @GameTest(template="townhall_state_empty",batch="defense_no_materials",timeoutTicks=1200)
    public static void insufficient_material_refuses_without_writes_or_history(GameTestHelper h){
        try{Fixture f=new Fixture(h.getLevel(),"i","front_w_04");f.stage();f.nation.addResource(ResourceType.STONE,-100);
            expectRefusal(()->DefenseTransaction.run(f,DefenseTransaction.Action.APPLY));
            h.assertTrue(f.nation.treasury()==1000&&f.nation.resource(ResourceType.STONE)==0&&!Files.exists(f.file),"Insufficient materials changed accounting/history");for(var c:f.plan.cells())h.assertTrue(f.level.getBlockState(c.pos()).equals(c.before()),"Insufficient materials wrote blocks");h.succeed();
        }catch(Exception x){throw new RuntimeException(x);}
    }
    private static void historySelection(ServerLevel level) throws Exception {
        Path root=level.getServer().getWorldPath(LevelResource.ROOT).resolve("data/defense_history_"+UUID.randomUUID());Files.createDirectories(root.resolve("data"));CatanSavedData data=new CatanSavedData();
        String key="defense_receipt:a:front_w_04";UUID previous=UUID.randomUUID();data.setPlacedMapAsset(key,previous+":reserved");
        expectRefusal(()->DefenseService.chooseHistory(root,data,"a","front_w_04",DefenseTransaction.Action.APPLY));
        CompoundTag j=new CompoundTag(),identity=new CompoundTag();identity.putString("world",root.toString());identity.putString("city","a");identity.putString("socket","front_w_04");j.put("identity",identity);j.putUUID("id",previous);j.putString("phase","ROLLED_BACK");
        Path old=root.resolve("data/catancraft_defense_a_front_w_04_r4a_r6_1.dat");ProducerJournal.write(old,j,false);data.setPlacedMapAsset(key,previous+":refunded");
        Path fresh=DefenseService.chooseHistory(root,data,"a","front_w_04",DefenseTransaction.Action.APPLY);
        if(fresh.equals(old)||Files.exists(fresh)||!ProducerJournal.read(old).equals(j))throw new AssertionError("Fresh repurchase overwrote retained history");
        if(!DefenseService.chooseHistory(root,data,"a","front_w_04",DefenseTransaction.Action.STATUS).equals(old))throw new AssertionError("Current receipt history selection failed");
        CompoundTag pending=j.copy();pending.putUUID("id",UUID.randomUUID());pending.putString("phase","PREPARED");ProducerJournal.write(fresh,pending,false);
        if(!DefenseService.chooseHistory(root,data,"a","front_w_04",DefenseTransaction.Action.RESUME).equals(fresh))throw new AssertionError("Prepared unreserved history not selected");
        Path duplicate=root.resolve("data/catancraft_defense_a_front_w_04_"+UUID.randomUUID()+"_r4a_r6_1.dat");CompoundTag second=pending.copy();second.putUUID("id",UUID.randomUUID());ProducerJournal.write(duplicate,second,false);
        expectRefusal(()->DefenseService.chooseHistory(root,data,"a","front_w_04",DefenseTransaction.Action.RESUME));
        // Intentionally retain all isolated test histories as evidence; no live history was accessed.
    }
    private static void assertCost(GameTestHelper h,String module,long money,Map<ResourceType,Long> materials) throws IOException {
        EconomyCost c=DefenseCostCatalog.cost(module);h.assertTrue(c.money()==money&&c.resources().equals(materials),"Explicit provisional price mismatch for "+module);
    }
    private interface Checked {void run() throws Exception;}
    private static void expectRefusal(Checked r) throws Exception {try{r.run();}catch(IOException expected){return;}throw new AssertionError("Expected safety refusal");}
    private static final class Fixture implements DefenseTransaction.Environment {
        final ServerLevel level;final DefenseRevisions.Plan plan;final NationData nation=new NationData(UUID.randomUUID(),"defense-test",UUID.randomUUID(),1000);final Path file;String receipt="",owned="";boolean authorized=true;Map<String,DefenseRevisions.Plan> installed=new HashMap<>();
        Fixture(ServerLevel l,String city,String socket) throws IOException {
            if(!(l.getServer() instanceof GameTestServer))throw new IOException("Disposable GameTest server required");level=l;plan=DefenseRevisions.plan(city,socket);nation.addResource(ResourceType.STONE,100);
            file=l.getServer().getWorldPath(LevelResource.ROOT).resolve("data/defense_native_"+UUID.randomUUID()+".dat");
        }
        void stage(){for(var r:plan.reads()){level.getChunkAt(r.pos());level.setBlock(r.pos(),r.state(),2|16|32);}for(var c:plan.cells()){level.getChunkAt(c.pos());level.setBlock(c.pos(),c.before(),2|16|32);}}
        public ServerLevel level(){return level;}public Path file(){return file;}public DefenseRevisions.Plan plan(){return plan;}public EconomyCost cost(){return new EconomyCost(73,Map.of(ResourceType.STONE,3L));}public NationData nation(){return nation;}
        public CompoundTag identity(){CompoundTag n=new CompoundTag();n.putString("test",file.toString());return n;}public String receipt(){return receipt;}public void receipt(String v){receipt=v;}public String owned(){return owned;}public void owned(String v){owned=v==null?"":v;if(v==null)installed.remove(plan.socket());else installed.put(plan.socket(),plan);}
        public net.minecraft.world.level.block.state.BlockState readState(DefenseRevisions.Read read){for(var p:installed.values())if(!p.socket().equals(plan.socket()))for(var c:p.cells())if(c.pos().equals(read.pos()))return c.after();return read.state();}
        public void requirements(boolean rollback) throws IOException {
            if(!authorized)throw new IOException("Fixture missing ownership/dependency");
            if(!rollback)for(String dep:plan.dependencies())if(!installed.containsKey(dep))throw new IOException("Fixture missing dependency");
            for(var p:installed.values())if(!p.socket().equals(plan.socket())){
                if(plan.excludes().contains(p.socket())||p.excludes().contains(plan.socket()))throw new IOException("Fixture exclusivity");
                if(rollback&&p.dependencies().contains(plan.socket()))throw new IOException("Fixture rollback dependent first");
            }
        }public void allow(BlockPos p) throws IOException {if(!authorized)throw new IOException("Fixture owner changed");}
        public void save() throws IOException {CompoundTag n=nation.save();n.putString("receipt",receipt);n.putString("owned",owned);ProducerJournal.write(file.resolveSibling(file.getFileName()+".ledger"),n,Files.exists(file.resolveSibling(file.getFileName()+".ledger")));}
    }
}
