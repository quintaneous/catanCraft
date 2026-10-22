package com.quin.catancraft.world;

import com.quin.catancraft.data.*;
import com.quin.catancraft.economy.EconomyCost;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Native server-adapter tests with explicit synthetic contracts. No production artwork/prices. */
@GameTestHolder("catancraft_sectors_service") @PrefixGameTestTemplate(false)
public final class SectorServiceTests {
    private static final AtomicInteger SITES=new AtomicInteger();
    private static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> FIXTURE_ENTITY_TICKET=net.minecraft.server.level.TicketType.create("catancraft_sector_service_fixture",Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong));
    private static final SectorContracts.Direction N=SectorContracts.Direction.NORTH,E=SectorContracts.Direction.EAST,S=SectorContracts.Direction.SOUTH,W=SectorContracts.Direction.WEST;
    @GameTest(template="townhall_state_empty",batch="sector_service01",timeoutTicks=2000)
    public static void four_absolute_compass_sides_upgrade_and_duplicate_server_binding(GameTestHelper h){start(h,f->{
        var book=f.book();long money=f.nation().treasury();
        for(var d:SectorContracts.Direction.values()){
            ok(h,f.quote(d));var result=f.op(d,SectorTransaction.Action.APPLY);ok(h,result);h.assertTrue(result.changedBlocks()==2,"Whole fixture side did not place both pieces");long paid=f.nation().treasury();
            ok(h,f.op(d,SectorTransaction.Action.APPLY));h.assertTrue(f.nation().treasury()==paid,"Duplicate confirm charged with consumed quote");
            var state=SectorService.savedState(f.data,f.city,d);h.assertTrue(state.level()==1&&state.revision().contains(d.name()),"Saved compass/revision was rotated or lowercased");
            h.assertTrue(f.level.getBlockState(f.anchor(d)).is(Blocks.STONE_BRICKS),"Absolute compass fixture placement wrong");
        }
        for(var side:SectorContracts.Direction.values()){ok(h,f.op(side,SectorTransaction.Action.STATUS));var repeat=f.op(side,SectorTransaction.Action.APPLY);ok(h,repeat);h.assertTrue(repeat.changedBlocks()==0,"Later valid neighbor purchases invalidated terminal no-op confirmation");}
        h.assertTrue(f.nation().treasury()==money-400&&f.nation().resource(ResourceType.STONE)==988,"Four whole-side prices were not charged once");
        var previous=SectorService.savedState(f.data,f.city,N);ok(h,f.quote(N));ok(h,f.op(N,SectorTransaction.Action.APPLY));h.assertTrue(SectorService.savedState(f.data,f.city,N).level()==2,"Upgrade did not persist one side level");
        ok(h,f.op(N,SectorTransaction.Action.ROLLBACK));var restored=SectorService.savedState(f.data,f.city,N);h.assertTrue(restored.equals(previous)&&f.nation().treasury()==money-400&&book.equals(f.book()),"Upgrade rollback lost prior purchased receipt/revision or actual book");
        ok(h,f.op(N,SectorTransaction.Action.ROLLBACK));h.assertTrue(f.nation().treasury()==money-400,"Repeated server rollback refunded twice");
        Path sourceProof=null;for(Path path:f.histories()){var journal=ProducerJournal.read(path);if(journal.getCompound("identity").getString("sector").equals("north")&&journal.getString("phase").equals("COMMITTED")&&journal.getCompound("spec").getInt("targetLevel")==1)sourceProof=path;}
        h.assertTrue(sourceProof!=null,"Missing source proof fixture");Path retained=sourceProof.resolveSibling(sourceProof.getFileName()+".retained-evidence");Files.move(sourceProof,retained);
        no(h,f.quote(N));no(h,f.op(N,SectorTransaction.Action.ROLLBACK));h.assertTrue(f.nation().treasury()==money-400&&Files.isRegularFile(retained),"Higher rolled-back journal substituted for missing committed source purchase");
    });}
    @GameTest(template="townhall_state_empty",batch="sector_service02",timeoutTicks=2000)
    public static void stale_quotes_bind_price_corner_neighbor_owner_and_tick(GameTestHelper h){start(h,f->{
        long before=f.nation().treasury();ok(h,f.quote(N));f.catalog.price=101;no(h,f.op(N,SectorTransaction.Action.APPLY));
        ok(h,f.quote(N));f.catalog.alternateCorners=true;no(h,f.op(N,SectorTransaction.Action.APPLY));
        ok(h,f.quote(N));f.fakeState(E,1,"fixture-neighbor:paid","NEIGHBOR_REV");no(h,f.op(N,SectorTransaction.Action.APPLY));f.fakeState(E,0,"","");
        ok(h,f.quote(N));f.clock.addAndGet(1201);no(h,f.op(N,SectorTransaction.Action.APPLY));
        ok(h,f.quote(N));var unauthorized=new SectorService.Context(f.ctx.server(),f.ctx.root(),f.ctx.data(),f.city,UUID.randomUUID(),f.ctx.owner(),f.ctx.worldId(),f.ctx.approval(),f.ctx.ticks());no(h,SectorService.operationFixture(unauthorized,N,SectorTransaction.Action.APPLY,f.catalog));
        h.assertTrue(f.nation().treasury()==before&&f.histories().isEmpty()&&SectorService.savedState(f.data,f.city,N).level()==0,"Stale/unauthorized quote mutated world/economy/history");
    });}
    @GameTest(template="townhall_state_empty",batch="sector_service03",timeoutTicks=2000)
    public static void native_resume_and_reverse_use_recorded_plan_cost_saveddata_reload(GameTestHelper h){start(h,f->{
        long before=f.nation().treasury();ok(h,f.quote(N));no(h,f.op(N,SectorTransaction.Action.APPLY,(stage,writes)->{if(stage.equals("write")&&writes==1)throw new IOException("Service forward interruption");}));
        h.assertTrue(SectorService.savedState(f.data,f.city,N).level()==0&&f.nation().treasury()==before-100,"Pending service side shown complete or not reserved");
        no(h,f.op(N,SectorTransaction.Action.APPLY));no(h,f.quote(E));f.reload();f.catalog.disabled=true;f.catalog.price=999999;f.barrel().setItem(1,new ItemStack(Items.EMERALD,11));var book=f.book();
        ok(h,f.op(N,SectorTransaction.Action.RESUME));h.assertTrue(SectorService.savedState(f.data,f.city,N).level()==1&&f.nation().treasury()==before-100&&book.equals(f.book()),"Historical resume looked up current catalog or lost current NBT");
        no(h,f.op(N,SectorTransaction.Action.ROLLBACK,(stage,writes)->{if(stage.equals("rollback_write")&&writes==1)throw new IOException("Service reverse interruption");}));f.reload();ok(h,f.op(N,SectorTransaction.Action.ROLLBACK));
        h.assertTrue(f.nation().treasury()==before&&SectorService.savedState(f.data,f.city,N).level()==0&&SectorService.savedState(f.data,f.city,N).receipt().isEmpty()&&book.equals(f.book()),"Reverse service recovery did not restore complete side/refund/receipt");
        ok(h,f.op(N,SectorTransaction.Action.ROLLBACK));h.assertTrue(f.nation().treasury()==before,"Repeated service recovery refund");
    });}
    @GameTest(template="townhall_state_empty",batch="sector_service04",timeoutTicks=2000)
    public static void mixed_neighbor_interface_and_actual_corner_read_are_guarded(GameTestHelper h){start(h,f->{
        ok(h,f.quote(N));ok(h,f.op(N,SectorTransaction.Action.APPLY));long paid=f.nation().treasury();
        f.catalog.alternateCorners=true;no(h,f.quote(E));h.assertTrue(f.nation().treasury()==paid,"Fresh quote contradicted a purchased neighbor corner table");f.catalog.alternateCorners=false;f.catalog.rejectMixed=true;
        no(h,f.quote(E));h.assertTrue(SectorService.savedState(f.data,f.city,E).level()==0&&f.nation().treasury()==paid,"Unsupported mixed adjacent tiers purchased");
        f.catalog.rejectMixed=false;ok(h,f.quote(E));BlockPos corner=f.origin.offset(-7,0,-7);f.level.setBlock(corner,Blocks.GOLD_BLOCK.defaultBlockState(),2);
        no(h,f.op(E,SectorTransaction.Action.APPLY));h.assertTrue(f.nation().treasury()==paid&&SectorService.savedState(f.data,f.city,E).level()==0&&f.level.getBlockState(f.anchor(E)).isAir(),"Corner read conflict wrote/charged partial side");
    });}
    @GameTest(template="townhall_state_empty",batch="sector_service05",timeoutTicks=2000)
    public static void legacy_city_reservation_and_reentrant_request_do_not_bypass_sector(GameTestHelper h){start(h,f->{
        Path legacy=f.root.resolve("data/catancraft_defense_a_fixture_r4a_r6_1.dat");CompoundTag j=new CompoundTag(),id=new CompoundTag();id.putString("world",f.root.toString());id.putString("city",f.city);j.put("identity",id);j.putUUID("id",UUID.randomUUID());j.putString("phase","PREPARED");ProducerJournal.write(legacy,j,false);byte[] history=Files.readAllBytes(legacy);
        no(h,f.quote(N));h.assertTrue(Arrays.equals(history,Files.readAllBytes(legacy))&&f.histories().isEmpty(),"Legacy reservation changed or bypassed");
        j.putString("phase","ROLLED_BACK");ProducerJournal.write(legacy,j,true);ok(h,f.quote(N));AtomicReference<MapAssetService.Result> nested=new AtomicReference<>();
        ok(h,f.op(N,SectorTransaction.Action.APPLY,(stage,writes)->{if(stage.equals("prepared"))nested.set(f.op(E,SectorTransaction.Action.APPLY));}));
        h.assertTrue(nested.get()!=null&&!nested.get().success()&&SectorService.savedState(f.data,f.city,E).level()==0&&f.nation().treasury()==f.initialMoney-100,"Reentrant request created independent partial purchase");
    });}
    @GameTest(template="townhall_state_empty",batch="sector_service06",timeoutTicks=2000)
    public static void service_preflight_last_piece_and_separate_money_material_refusal(GameTestHelper h){start(h,f->{
        long money=f.nation().treasury();ok(h,f.quote(N));BlockPos last=f.anchor(N).offset(1,0,0);f.level.setBlock(last,Blocks.DIAMOND_BLOCK.defaultBlockState(),2);no(h,f.op(N,SectorTransaction.Action.APPLY));h.assertTrue(f.level.getBlockState(f.anchor(N)).isAir()&&f.nation().treasury()==money&&f.histories().isEmpty(),"Last service component refusal changed first piece/payment");f.level.setBlock(last,Blocks.AIR.defaultBlockState(),2);
        f.nation().addTreasury(-money);ok(h,f.quote(N));no(h,f.op(N,SectorTransaction.Action.APPLY));h.assertTrue(f.nation().treasury()==0&&f.nation().resource(ResourceType.STONE)==1000&&f.histories().isEmpty(),"Insufficient service money mutated materials/history");
        f.nation().addTreasury(money);f.nation().addResource(ResourceType.STONE,-1000);ok(h,f.quote(N));no(h,f.op(N,SectorTransaction.Action.APPLY));h.assertTrue(f.nation().treasury()==money&&f.nation().resource(ResourceType.STONE)==0&&f.histories().isEmpty(),"Insufficient service material debited money/history");
    });}
    @GameTest(template="townhall_state_empty",batch="sector_service07",timeoutTicks=2000)
    public static void paid_pending_is_not_complete_and_current_pointer_refuses_missing_history(GameTestHelper h){start(h,f->{
        ok(h,f.quote(N));no(h,f.op(N,SectorTransaction.Action.APPLY,(stage,writes)->{if(stage.equals("ledger_saved"))throw new IOException("Service paid-ledger crash");}));f.reload();
        var status=f.op(N,SectorTransaction.Action.STATUS);ok(h,status);h.assertTrue(status.message().contains("not a completed new sector"),"Pending paid phase exposed as completed side");
        ok(h,f.op(N,SectorTransaction.Action.RESUME));long paid=f.nation().treasury();Path history=f.histories().get(0);Path retained=history.resolveSibling(history.getFileName()+".retained-evidence");Files.move(history,retained);
        no(h,f.op(N,SectorTransaction.Action.APPLY));no(h,f.quote(N));h.assertTrue(f.nation().treasury()==paid&&Files.isRegularFile(retained),"Missing retained journal caused duplicate debit or deletion");
        Files.move(retained,history);byte[] terminal=Files.readAllBytes(history);f.fakeState(N,0,"","");f.data.setPlacedMapAsset("sector_v1:a:north:operation","none");
        no(h,f.quote(N));no(h,f.op(N,SectorTransaction.Action.APPLY));h.assertTrue(f.nation().treasury()==paid&&Arrays.equals(terminal,Files.readAllBytes(history)),"Orphan terminal history was adopted as fresh after ledger/pointer loss");
    });}
    @GameTest(template="townhall_state_empty",batch="sector_service08",timeoutTicks=2000)
    public static void production_catalog_empty_and_fixture_world_identity_cannot_be_rebound(GameTestHelper h){start(h,f->{
        for(var d:SectorContracts.Direction.values())refuse(()->SectorService.resolveProduction(f.ctx,d,0));ok(h,f.quote(N));ok(h,f.op(N,SectorTransaction.Action.APPLY));
        var wrong=new SectorService.Context(f.ctx.server(),f.ctx.root(),f.ctx.data(),f.city,f.actor,f.ctx.owner(),UUID.randomUUID().toString(),f.ctx.approval(),f.ctx.ticks());
        no(h,SectorService.operationFixture(wrong,N,SectorTransaction.Action.ROLLBACK,f.catalog));h.assertTrue(SectorService.savedState(f.data,f.city,N).level()==1,"Foreign world UUID reclaimed/rolled back an owned side");
        Path outside=Files.createTempDirectory("catancraft-sector-fixture-outside-");Path link=f.level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().resolve("data/fixture-link-"+UUID.randomUUID());
        try{
            try{Files.createSymbolicLink(link,outside);}catch(UnsupportedOperationException unavailable){return;}
            refuse(()->SectorService.fixtureContext(f.level.getServer(),link.resolve("subdir"),f.data,f.city,f.actor,f.worldId,f.clock::get));
            h.assertTrue(!Files.exists(outside.resolve("subdir")),"Fixture authorizer created directories through an intermediate symlink");
        }finally{Files.deleteIfExists(link);Files.deleteIfExists(outside);}
        var productionClaim=new SectorService.Context(f.ctx.server(),f.ctx.root(),f.ctx.data(),f.city,f.actor,f.ctx.owner(),f.ctx.worldId(),SectorService.PRODUCTION,f.ctx.ticks());no(h,SectorService.operationFixture(productionClaim,N,SectorTransaction.Action.ROLLBACK,f.catalog));
    });}
    @GameTest(template="townhall_state_empty",batch="sector_service09",timeoutTicks=2000)
    public static void harmless_entity_preserved_and_unsafe_entity_refuses_through_adapter(GameTestHelper h){
        try{Fixture f=new Fixture(h);f.stage();ItemStack stack=new ItemStack(Items.EMERALD,7);stack.getOrCreateTag().putUUID("fixture",UUID.randomUUID());ItemEntity harmless=item(f.level,f.bookPos.above(),stack);UUID harmlessId=harmless.getUUID();BlockPos collision=f.anchor(N);ItemEntity blocked=item(f.level,collision,new ItemStack(Items.STICK,3));
            whenReady(h,f,()->{
                ok(h,f.quote(N));no(h,f.op(N,SectorTransaction.Action.APPLY));h.assertTrue(!blocked.isRemoved()&&blocked.getItem().getCount()==3&&f.nation().treasury()==f.initialMoney&&f.histories().isEmpty(),"Unsafe adapter preflight moved entity or charged");
                // The successful independent east sector leaves the north obstruction in place.
                ok(h,f.quote(E));ok(h,f.op(E,SectorTransaction.Action.APPLY));h.assertTrue(!harmless.isRemoved()&&harmlessId.equals(harmless.getUUID())&&ItemStack.matches(stack,harmless.getItem())&&!blocked.isRemoved(),"Adapter harmed harmless item or removed obstruction");h.succeed();
            },0);
        }catch(Exception e){throw new RuntimeException(e);}
    }
    @GameTest(template="townhall_state_empty",batch="sector_service10",timeoutTicks=2000)
    public static void startup_recovery_isolates_foreign_city_and_finishes_real_paid_journal(GameTestHelper h){
        try{
            Fixture f=new Fixture(h,"b");f.stage();whenReady(h,f,()->{
                f.data.createTerritory("a","Foreign journal fixture",ResourceType.WOOD).setOwnerNationId(f.owner);
                long money=f.nation().treasury(),stone=f.nation().resource(ResourceType.STONE);
                ok(h,f.quote(N));no(h,f.op(N,SectorTransaction.Action.APPLY,(stage,writes)->{if(stage.equals("write")&&writes==1)throw new IOException("Interrupted startup fixture");}));
                Path paid=f.histories().get(0);CompoundTag foreign=ProducerJournal.read(paid).copy();
                Path bad=f.root.resolve("data/catancraft_sector_a_north_"+UUID.randomUUID()+"_v1.dat");CompoundTag identity=foreign.getCompound("identity");identity.putString("city","a");identity.putUUID("owner",UUID.randomUUID());identity.putString("operationFile",bad.getFileName().toString());ProducerJournal.write(bad,foreign,false);
                byte[] retained=Files.readAllBytes(bad);f.reload();f.catalog.disabled=true;f.catalog.price=999999;CompoundTag book=f.book();
                SectorService.Context a=SectorService.fixtureContext(f.level.getServer(),f.root,f.data,"a",f.actor,f.worldId,f.clock::get);
                var results=SectorService.recoverFixture(List.of(a,f.ctx));
                h.assertTrue(results.size()==2&&!results.get(0).success()&&results.get(0).message().contains("city a")&&results.get(1).success(),"Foreign city prevented independent journal recovery: "+results);
                h.assertTrue(Arrays.equals(retained,Files.readAllBytes(bad)),"Foreign-owner evidence was changed");
                h.assertTrue(ProducerJournal.read(paid).getString("phase").equals("COMMITTED")&&SectorService.savedState(f.data,"b",N).level()==1&&SectorService.savedState(f.data,"a",N).level()==0,"Recovery did not commit only valid city");
                String receipt=SectorService.savedState(f.data,"b",N).receipt();h.assertTrue(receipt.equals(ProducerJournal.read(paid).getUUID("id")+":paid")&&f.nation().treasury()==money-100&&f.nation().resource(ResourceType.STONE)==stone-3&&book.equals(f.book()),"Recovery repeated payment or changed preserved NBT");
                for(int i=0;i<2;i++)h.assertTrue(f.level.getBlockState(f.anchor(N).offset(i,0,0)).is(Blocks.STONE_BRICKS),"Independent city target incomplete");
                var repeated=SectorService.recoverFixture(List.of(a,f.ctx));h.assertTrue(repeated.size()==1&&!repeated.get(0).success()&&f.nation().treasury()==money-100&&SectorService.savedState(f.data,"b",N).receipt().equals(receipt)&&Arrays.equals(retained,Files.readAllBytes(bad)),"Repeat recovery altered settled accounting or foreign evidence");h.succeed();
            },0);
        }catch(Exception e){throw new RuntimeException(e);}
    }
    private interface Checked {void run()throws IOException;}
    private static void refuse(Checked op)throws IOException{try{op.run();}catch(IOException expected){return;}throw new AssertionError("Expected adapter policy refusal");}
    @GameTest(template="townhall_state_empty",batch="sector_service11",timeoutTicks=2000)
    public static void provisional_catalog_sealed_quote_incremental_upgrade_and_historical_recovery(GameTestHelper h){start(h,f->{
        f.catalog.structuralPrices=true;for(var r:List.of(ResourceType.WOOD,ResourceType.CONCRETE,ResourceType.STEEL))f.nation().addResource(r,100);
        long money=f.nation().treasury(),wood=f.nation().resource(ResourceType.WOOD),stone=f.nation().resource(ResourceType.STONE),concrete=f.nation().resource(ResourceType.CONCRETE),steel=f.nation().resource(ResourceType.STEEL);var book=f.book();
        var q=f.quote(N);ok(h,q);h.assertTrue(q.message().contains("$1500")&&q.message().contains("15 Wood")&&q.message().contains("30 Stone")&&q.message().contains("PROVISIONAL PLAYTEST PRICING"),"Basic full quote/version not displayed");
        no(h,f.op(N,SectorTransaction.Action.APPLY,(stage,writes)->{if(stage.equals("write")&&writes==1)throw new IOException("DEF04 paid interruption");}));
        var journal=ProducerJournal.read(f.histories().get(0));h.assertTrue(journal.getCompound("identity").getString("priceCatalogVersion").equals(SectorPriceCatalog.VERSION)&&journal.getCompound("cost").getLong("money")==1500&&journal.getCompound("cost").getLong("wood")==15&&journal.getCompound("cost").getLong("stone")==30,"Quoted original price/version not durable");
        f.reload();f.catalog.disabled=true;f.catalog.catalogVersion="FUTURE_PRICE_VERSION";f.catalog.price=999999;ok(h,f.op(N,SectorTransaction.Action.RESUME));ok(h,f.op(N,SectorTransaction.Action.APPLY));
        h.assertTrue(f.nation().treasury()==money-1500&&f.nation().resource(ResourceType.WOOD)==wood-15&&f.nation().resource(ResourceType.STONE)==stone-30,"Resume/duplicate repriced Basic");
        var basicNation=f.nation().save();var basicState=SectorService.savedState(f.data,f.city,N);f.catalog.disabled=false;f.catalog.catalogVersion=SectorPriceCatalog.VERSION;
        q=f.quote(N);ok(h,q);h.assertTrue(q.message().contains("$4000")&&q.message().contains("50 Concrete")&&q.message().contains("15 Steel"),"Incremental full quote missing");ok(h,f.op(N,SectorTransaction.Action.APPLY));
        h.assertTrue(f.nation().treasury()==money-5500&&f.nation().resource(ResourceType.WOOD)==wood-15&&f.nation().resource(ResourceType.STONE)==stone-30&&f.nation().resource(ResourceType.CONCRETE)==concrete-50&&f.nation().resource(ResourceType.STEEL)==steel-15,"Upgrade full materials or incremental charge incorrect");
        for(Path path:f.histories()){var j=ProducerJournal.read(path);if(j.getCompound("spec").getInt("targetLevel")==2)h.assertTrue(j.getCompound("cost").getLong("money")==4000&&j.getCompound("cost").getLong("concrete")==50&&j.getCompound("cost").getLong("steel")==15&&j.getCompound("cost").getAllKeys().equals(Set.of("money","concrete","steel"))&&j.getCompound("identity").getString("priceCatalogVersion").equals(SectorPriceCatalog.VERSION),"Upgrade journal lost exact cost/version or includes Basic cost");}
        f.catalog.disabled=true;f.catalog.catalogVersion="FUTURE_PRICE_VERSION";f.reload();ok(h,f.op(N,SectorTransaction.Action.ROLLBACK));ok(h,f.op(N,SectorTransaction.Action.ROLLBACK));
        h.assertTrue(basicNation.equals(f.nation().save())&&basicState.equals(SectorService.savedState(f.data,f.city,N))&&book.equals(f.book()),"Historical upgrade refund/duplicate lost paid Basic, original cost or book");
    });}
    @GameTest(template="townhall_state_empty",batch="sector_service12",timeoutTicks=2000)
    public static void provisional_price_fresh_refusals_and_same_price_version_change_are_free(GameTestHelper h){start(h,f->{
        f.catalog.structuralPrices=true;for(var r:List.of(ResourceType.WOOD,ResourceType.STONE))f.nation().addResource(r,100-f.nation().resource(r));
        f.nation().addTreasury(1499-f.nation().treasury());var before=f.nation().save();ok(h,f.quote(N));no(h,f.op(N,SectorTransaction.Action.APPLY));h.assertTrue(before.equals(f.nation().save())&&f.histories().isEmpty(),"DEF04 insufficient money consumed materials");
        f.nation().addTreasury(10000);for(var missing:List.of(ResourceType.WOOD,ResourceType.STONE)){long needed=missing==ResourceType.WOOD?15:30;f.nation().addResource(missing,needed-1-f.nation().resource(missing));before=f.nation().save();ok(h,f.quote(N));no(h,f.op(N,SectorTransaction.Action.APPLY));h.assertTrue(before.equals(f.nation().save())&&f.histories().isEmpty(),"DEF04 insufficient material consumed money/history");f.nation().addResource(missing,100-f.nation().resource(missing));}
        before=f.nation().save();ok(h,f.quote(N));f.catalog.catalogVersion="FUTURE_SAME_PRICE_VERSION";no(h,f.op(N,SectorTransaction.Action.APPLY));h.assertTrue(before.equals(f.nation().save())&&f.histories().isEmpty()&&f.level.getBlockState(f.anchor(N)).isAir(),"Changed price revision reused stale quote");
        f.catalog.catalogVersion=SectorPriceCatalog.VERSION;ok(h,f.quote(N));f.level.setBlock(f.anchor(N).offset(1,0,0),Blocks.DIAMOND_BLOCK.defaultBlockState(),2);no(h,f.op(N,SectorTransaction.Action.APPLY));h.assertTrue(before.equals(f.nation().save())&&f.histories().isEmpty()&&f.level.getBlockState(f.anchor(N)).isAir(),"DEF04 late-piece conflict charged or placed first piece");
    });}
    @GameTest(template="townhall_state_empty",batch="sector_service_runner",timeoutTicks=2000)
    public static void scheduled_failure_stop_reload_and_historical_resume_are_single_charge(GameTestHelper h){
        try{Fixture f=new Fixture(h);f.stage();whenReady(h,f,()->{
            CompoundTag book=f.book();long money=f.nation().treasury();ok(h,f.quote(N));
            ok(h,SectorService.operationFixtureBounded(f.ctx,N,SectorTransaction.Action.APPLY,f.catalog,(stage,n)->{if(stage.equals("write")&&n==1)throw new IOException("Injected scheduled partial write");}));
            no(h,SectorService.operationFixtureBounded(f.ctx,N,SectorTransaction.Action.APPLY,f.catalog));
            h.runAfterDelay(2,()->{try{
                h.assertTrue(!SectorService.activeFixture(f.ctx)&&SectorService.savedState(f.data,f.city,N).level()==0&&f.nation().treasury()==money-100,"Scheduler failed to clear pending task or changed payment/level");
                h.assertTrue(f.level.getBlockState(f.anchor(N)).is(Blocks.STONE_BRICKS)&&f.level.getBlockState(f.anchor(N).offset(1,0,0)).isAir(),"Fault did not retain exact one-write prefix");
                f.reload();f.catalog.disabled=true;f.catalog.price=999999;
                ok(h,SectorService.operationFixtureBounded(f.ctx,N,SectorTransaction.Action.RESUME,f.catalog));
                SectorService.stopped(f.level.getServer());
                h.assertTrue(!SectorService.activeFixture(f.ctx)&&f.nation().treasury()==money-100,"Stopped scheduler refunded or retained stale task");
                f.reload();ok(h,SectorService.operationFixtureBounded(f.ctx,N,SectorTransaction.Action.RESUME,f.catalog));
                h.runAfterDelay(2,()->{try{
                    h.assertTrue(!SectorService.activeFixture(f.ctx)&&SectorService.savedState(f.data,f.city,N).level()==1&&f.nation().treasury()==money-100&&book.equals(f.book()),"Scheduled restart lost journaled price, completed level or book");
                    ok(h,f.op(N,SectorTransaction.Action.APPLY));h.assertTrue(f.nation().treasury()==money-100,"Scheduled duplicate charged twice");
                    ok(h,SectorService.operationFixtureBounded(f.ctx,N,SectorTransaction.Action.ROLLBACK,f.catalog));
                    h.runAfterDelay(2,()->{try{
                        h.assertTrue(!SectorService.activeFixture(f.ctx)&&SectorService.savedState(f.data,f.city,N).level()==0&&f.nation().treasury()==money&&f.nation().resource(ResourceType.STONE)==1000&&book.equals(f.book()),"Scheduled rollback did not refund original price once");
                        ok(h,f.op(N,SectorTransaction.Action.ROLLBACK));h.assertTrue(f.nation().treasury()==money,"Scheduled duplicate refund repeated");h.succeed();
                    }catch(Exception error){throw new RuntimeException(error);}});
                }catch(Exception error){throw new RuntimeException(error);}});
            }catch(Exception error){throw new RuntimeException(error);}});
        },0);}catch(Exception error){throw new RuntimeException(error);}
    }
    private interface Body {void run(Fixture f)throws Exception;}
    private static void start(GameTestHelper h,Body body){try{Fixture f=new Fixture(h);f.stage();whenReady(h,f,()->{body.run(f);h.succeed();},0);}catch(Exception e){throw new RuntimeException(e);}}
    private interface Ready {void run()throws Exception;}
    private static void whenReady(GameTestHelper h,Fixture f,Ready ready,int attempts){
        h.runAfterDelay(attempts==0?20:1,()->{
            try{List<net.minecraft.world.phys.AABB> boxes=new ArrayList<>();for(var side:SectorContracts.Direction.values())boxes.add(SectorService.entityBounds(f.catalog.resolve(f.ctx,side,0).plan()));StreetscapeService.requireLoadedEntities(f.level,boxes);}
            catch(IOException loading){if(attempts<400){whenReady(h,f,ready,attempts+1);return;}throw new RuntimeException("Fixture entity sections failed to become genuinely ready",loading);}
            try{ready.run();}catch(Exception e){throw new RuntimeException(e);}
        });
    }
    private static void ok(GameTestHelper h,MapAssetService.Result r){h.assertTrue(r.success(),r.message());}
    private static void no(GameTestHelper h,MapAssetService.Result r){h.assertTrue(!r.success(),"Expected refusal: "+r.message());}
    private static ItemEntity item(ServerLevel l,BlockPos p,ItemStack stack){ItemEntity e=new ItemEntity(l,p.getX()+.5,p.getY()+.15,p.getZ()+.5,stack);e.setNoGravity(true);e.setDeltaMovement(Vec3.ZERO);l.addFreshEntity(e);return e;}
    private static final class Fixture {
        final ServerLevel level;final BlockPos origin,bookPos;final String city,worldId=UUID.randomUUID().toString();final Path root;final UUID actor=UUID.randomUUID(),owner;final AtomicLong clock=new AtomicLong(100);final Catalog catalog=new Catalog();CatanSavedData data=new CatanSavedData();SectorService.Context ctx;final long initialMoney;
        Fixture(GameTestHelper h)throws IOException {this(h,"a");}
        Fixture(GameTestHelper h,String city)throws IOException {
            this.city=city;level=h.getLevel();if(!(level.getServer() instanceof GameTestServer))throw new IOException("Isolated service test world required");origin=h.absolutePos(new BlockPos(2000+48*SITES.getAndIncrement(),10,2000));bookPos=origin.offset(0,0,2);
            root=level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().resolve("data/sector-service-fixtures/"+UUID.randomUUID());NationData nation=data.createNation("Sector adapter fixture",actor);owner=nation.id();nation.addTreasury(10000);nation.addResource(ResourceType.STONE,1000);initialMoney=nation.treasury();var t=data.createTerritory(city,"Fixture city",ResourceType.WOOD);t.setOwnerNationId(owner);context();
        }
        void context()throws IOException{ctx=SectorService.fixtureContext(level.getServer(),root,data,city,actor,worldId,clock::get);}
        void reload()throws IOException{data=SectorService.reloadFixture(ctx);context();}
        NationData nation(){return data.nation(owner);}BlockPos anchor(SectorContracts.Direction d){return origin.offset(d.dx*5,0,d.dz*5);}
        BarrelBlockEntity barrel(){return (BarrelBlockEntity)level.getBlockEntity(bookPos);}CompoundTag book(){return barrel().saveWithFullMetadata().copy();}
        void stage()throws IOException {
            int minX=(origin.getX()-32)>>4,maxX=(origin.getX()+32)>>4,minZ=(origin.getZ()-32)>>4,maxZ=(origin.getZ()+32)>>4;for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){var chunk=new net.minecraft.world.level.ChunkPos(x,z);level.getChunkSource().addRegionTicket(FIXTURE_ENTITY_TICKET,chunk,2,chunk,true);level.setChunkForced(x,z,true);level.getChunk(x,z);}
            for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++)for(int y=-1;y<=2;y++){BlockPos p=origin.offset(x,y,z);var old=level.getBlockEntity(p);if(old instanceof net.minecraft.world.Container inventory)inventory.clearContent();level.setBlock(p,y==-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2|16|32);}
            for(int x:new int[]{-7,7})for(int z:new int[]{-7,7})level.setBlock(origin.offset(x,0,z),Blocks.BEDROCK.defaultBlockState(),2|16|32);
            level.setBlock(bookPos,Blocks.BARREL.defaultBlockState(),2|16|32);ItemStack book=new ItemStack(Items.WRITTEN_BOOK);book.getOrCreateTag().putString("title","Actual service fixture book");book.getOrCreateTag().putString("author","Owner");barrel().setItem(0,book);
        }
        MapAssetService.Result quote(SectorContracts.Direction d){return SectorService.quoteFixture(ctx,d,catalog);}MapAssetService.Result op(SectorContracts.Direction d,SectorTransaction.Action a){return SectorService.operationFixture(ctx,d,a,catalog);}MapAssetService.Result op(SectorContracts.Direction d,SectorTransaction.Action a,SectorTransaction.Fault fault){return SectorService.operationFixture(ctx,d,a,catalog,fault);}
        List<Path> histories()throws IOException{try(var s=Files.newDirectoryStream(root.resolve("data"),"catancraft_sector_"+city+"_*_v1.dat")){List<Path> p=new ArrayList<>();for(Path file:s)p.add(file);return p;}}
        void fakeState(SectorContracts.Direction d,int level,String receipt,String revision){String p="sector_v1:"+city+":"+d.name().toLowerCase(Locale.ROOT)+":";data.setPlacedMapAsset(p+"level",Integer.toString(level));data.setPlacedMapAsset(p+"receipt",receipt.isEmpty()?"none":receipt);data.setPlacedMapAsset(p+"revision",revision.isEmpty()?"none":"hex:"+HexFormat.of().formatHex(revision.getBytes(StandardCharsets.UTF_8)));}
        final class Catalog implements SectorService.Provider {
            long price=100;boolean disabled,alternateCorners,rejectMixed,structuralPrices;String catalogVersion=SectorPriceCatalog.VERSION;
            public String priceVersion(){return structuralPrices?catalogVersion:"SYNTHETIC_FIXTURE_V1";}
            public SectorContracts.Contract resolve(SectorService.Context c,SectorContracts.Direction side,int source)throws IOException {
                if(disabled||source>1)throw new IOException("Synthetic fixture catalog entry unavailable");var current=SectorService.savedState(c.data(),city,side);Map<SectorContracts.Direction,SectorContracts.Neighbor> neighbors=new EnumMap<>(SectorContracts.Direction.class);
                Set<Integer> levels=new HashSet<>();for(var d:side.adjacent()){var state=SectorService.savedState(c.data(),city,d);neighbors.put(d,new SectorContracts.Neighbor(state.level(),state.receipt()));levels.add(state.level());}if(rejectMixed&&levels.size()>1)throw new IOException("No approved synthetic mixed-tier corner interface");
                BlockPos base=anchor(side);List<SectorTransaction.Cell> cells=new ArrayList<>();for(int i=0;i<2;i++)cells.add(new SectorTransaction.Cell(base.offset(side==N||side==S?i:0,0,side==E||side==W?i:0),source==0?Blocks.AIR.defaultBlockState():Blocks.STONE_BRICKS.defaultBlockState(),source==0?Blocks.STONE_BRICKS.defaultBlockState():Blocks.POLISHED_ANDESITE.defaultBlockState()));
                if(source>0)for(int i=0;i<2;i++)cells.add(new SectorTransaction.Cell(base.offset(side==N||side==S?i:0,1,side==E||side==W?i:0),Blocks.AIR.defaultBlockState(),Blocks.STONE_BRICKS.defaultBlockState()));cells.add(new SectorTransaction.Cell(bookPos,Blocks.BARREL.defaultBlockState(),Blocks.BARREL.defaultBlockState()));
                List<SectorTransaction.Read> reads=new ArrayList<>();for(int x:new int[]{-7,7})for(int z:new int[]{-7,7})reads.add(new SectorTransaction.Read(origin.offset(x,0,z),Blocks.BEDROCK.defaultBlockState()));reads.add(new SectorTransaction.Read(origin,Blocks.AIR.defaultBlockState()));
                Map<SectorContracts.Corner,SectorContracts.Direction> corners=new EnumMap<>(SectorContracts.Corner.class);corners.put(SectorContracts.Corner.NORTH_WEST,alternateCorners?W:N);corners.put(SectorContracts.Corner.NORTH_EAST,N);corners.put(SectorContracts.Corner.SOUTH_WEST,S);corners.put(SectorContracts.Corner.SOUTH_EAST,S);
                var plan=new SectorTransaction.Plan(side.name().toLowerCase(Locale.ROOT),source==0?"SYNTHETIC_BASE":current.revision(),"SYNTHETIC_"+side.name()+"_L"+(source+1),source,source+1,cells,reads);
                return new SectorContracts.Contract(c.root().toString(),city,side,plan,corners,neighbors,Set.of(origin),structuralPrices?SectorPriceCatalog.cost(side,source):new EconomyCost(price*(source+1),Map.of(ResourceType.STONE,3L)));
            }
        }
    }
}
