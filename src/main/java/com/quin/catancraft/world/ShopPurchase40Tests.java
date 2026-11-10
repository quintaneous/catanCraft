package com.quin.catancraft.world;

import com.mojang.authlib.GameProfile;
import com.quin.catancraft.data.*;
import com.quin.catancraft.world.ShopPurchase40.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Controlled native player-data/SavedData persistence, with synthetic counter
 * authority and vanilla items. FakePlayers join the save roster synchronously
 * only; these tests do not establish connected-player or TaCZ compatibility. */
@GameTestHolder("catancraft_shop40") @PrefixGameTestTemplate(false)
public final class ShopPurchase40Tests {
    private static final AtomicInteger NAMES=new AtomicInteger();
    private static final long MONEY=10_000, WOOD=100;
    private ShopPurchase40Tests() {}

    @GameTest(template="townhall_state_empty",batch="shop40_01",timeoutTicks=200)
    public static void intent_precedes_charge_and_exact_item_delivery(GameTestHelper h){run(h,f->{
        UUID id=ShopPurchase40.create(f,f.request());byte[] original=f.intent(id);
        h.assertTrue(f.nation.treasury()==MONEY&&f.nation.resource(ResourceType.WOOD)==WOOD&&f.diamonds()==0,
                "Preparing intent charged or delivered before confirmation execution");
        h.assertTrue(ShopPurchase40.pending(f).equals(List.of(id)),"Prepared operation was not retained as pending");
        h.assertTrue(ShopPurchase40.resume(f,id).complete(),"Purchase did not complete");f.assertPaidOnce();f.assertExactItem(0);
        h.assertTrue(f.diskReceipts().equals(f.receipts())&&!f.receipts().isEmpty(),"Delivery receipt did not share native saved player data");
        same(h,original,ShopPurchase40.intentPath(f,id),"Completed purchase changed its original quoted intent");
    });}

    @GameTest(template="townhall_state_empty",batch="shop40_02",timeoutTicks=200)
    public static void completed_duplicate_is_inert_and_cannot_charge_or_deliver_again(GameTestHelper h){run(h,f->{
        UUID id=ShopPurchase40.create(f,f.request());ShopPurchase40.resume(f,id);
        byte[] original=f.intent(id),receipt=Files.readAllBytes(ShopPurchase40.receiptPath(f,id));
        CompoundTag player=f.player.saveWithoutId(new CompoundTag()).copy();int saves=f.playerSaves+f.ledgerSaves;
        h.assertTrue(ShopPurchase40.resume(f,id).complete()&&ShopPurchase40.pending(f).isEmpty(),"Completed operation replayed or stayed pending");
        f.assertPaidOnce();h.assertTrue(f.diamonds()==3&&player.equals(f.player.saveWithoutId(new CompoundTag())),"Duplicate changed player items/state");
        h.assertTrue(saves==f.playerSaves+f.ledgerSaves,"Completed duplicate repeated physical saves");
        same(h,original,ShopPurchase40.intentPath(f,id),"Duplicate changed original intent");same(h,receipt,ShopPurchase40.receiptPath(f,id),"Duplicate changed completion receipt");
    });}

    @GameTest(template="townhall_state_empty",batch="shop40_03",timeoutTicks=200)
    public static void consumed_delivered_item_is_not_granted_again_before_completion(GameTestHelper h){run(h,f->{
        UUID id=ShopPurchase40.create(f,f.request());refuse(()->ShopPurchase40.fixtureResume(f,id,failAt(SavePoint.BEFORE_COMMITTED)));
        f.assertPaidOnce();f.assertExactItem(0);CompoundTag delivered=f.receipts().copy();
        f.player.getInventory().setItem(0,ItemStack.EMPTY);f.persistPlayer();
        h.assertTrue(ShopPurchase40.resume(f,id).complete()&&f.diamonds()==0&&f.receipts().equals(delivered),"Recovery regranted an already consumed delivered item");f.assertPaidOnce();
    });}

    @GameTest(template="townhall_state_empty",batch="shop40_04",timeoutTicks=200)
    public static void moved_delivered_stack_keeps_its_one_delivery_receipt(GameTestHelper h){run(h,f->{
        UUID id=ShopPurchase40.create(f,f.request());refuse(()->ShopPurchase40.fixtureResume(f,id,failAt(SavePoint.BEFORE_COMMITTED)));
        ItemStack moved=f.player.getInventory().getItem(0).copy();f.player.getInventory().setItem(0,ItemStack.EMPTY);f.player.getInventory().setItem(9,moved);f.persistPlayer();
        h.assertTrue(ShopPurchase40.resume(f,id).complete()&&f.player.getInventory().getItem(0).isEmpty()&&f.diamonds()==3,"Recovery duplicated a moved delivery");
        f.assertExactItem(9);f.assertPaidOnce();
    });}

    @GameTest(template="townhall_state_empty",batch="shop40_05",timeoutTicks=200)
    public static void native_player_clone_preserves_delivery_receipt_after_inventory_loss(GameTestHelper h){run(h,f->{
        UUID id=ShopPurchase40.create(f,f.request());refuse(()->ShopPurchase40.fixtureResume(f,id,failAt(SavePoint.BEFORE_COMMITTED)));
        CompoundTag receipt=f.receipts().copy();FakePlayer clone=new FakePlayer(f.level,new GameProfile(f.player.getUUID(),f.player.getGameProfile().getName()));
        clone.restoreFrom(f.player,false);
        h.assertTrue(clone.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound("CatanCraftShop40").equals(receipt),"Native Forge clone lost death-preserved delivery receipts");
        clone.getInventory().clearContent();f.replacePlayer(clone);f.persistPlayer();
        h.assertTrue(ShopPurchase40.resume(f,id).complete()&&f.diamonds()==0&&f.receipts().equals(receipt),"Recovery regranted a pre-death delivery");f.assertPaidOnce();
    });}

    @GameTest(template="townhall_state_empty",batch="shop40_06",timeoutTicks=200)
    public static void silently_failed_ledger_save_blocks_items_and_retries_without_second_debit(GameTestHelper h){run(h,f->{
        UUID id=ShopPurchase40.create(f,f.request());f.suppressLedger=true;refuse(()->ShopPurchase40.resume(f,id));
        h.assertTrue(f.diamonds()==0&&!Files.exists(ShopPurchase40.receiptPath(f,id)),"Unverified debit save delivered an item or completed the operation");
        f.assertPaidOnce();f.suppressLedger=false;
        h.assertTrue(ShopPurchase40.resume(f,id).complete()&&f.diamonds()==3,"Verified ledger retry did not finish one delivery");f.assertPaidOnce();
    });}

    @GameTest(template="townhall_state_empty",batch="shop40_07",timeoutTicks=200)
    public static void ledger_callback_failure_after_actual_save_uses_durable_readback(GameTestHelper h){run(h,f->{
        UUID id=ShopPurchase40.create(f,f.request());f.throwAfterLedger=true;
        h.assertTrue(ShopPurchase40.resume(f,id).complete(),"A callback exception discarded a proven native debit save");
        f.assertPaidOnce();f.assertExactItem(0);f.throwAfterLedger=false;
        h.assertTrue(ShopPurchase40.resume(f,id).complete()&&f.diamonds()==3,"Saved debit callback failure duplicated delivery");f.assertPaidOnce();
    });}

    @GameTest(template="townhall_state_empty",batch="shop40_08",timeoutTicks=200)
    public static void silent_player_save_failure_rolls_back_only_staged_item_and_receipt(GameTestHelper h){run(h,f->{
        f.player.getInventory().setItem(12,new ItemStack(Items.BOOK,2));f.persistPlayer();
        UUID id=ShopPurchase40.create(f,f.request());f.suppressPlayer=true;refuse(()->ShopPurchase40.resume(f,id));
        h.assertTrue(f.diamonds()==0&&f.player.getInventory().getItem(12).getCount()==2&&f.receipts().isEmpty(),"Unverified delivery remained usable or rollback changed unrelated inventory");
        f.assertPaidOnce();f.suppressPlayer=false;h.assertTrue(ShopPurchase40.resume(f,id).complete(),"Clean failed-save rollback could not recover");
        f.assertExactItem(0);f.assertPaidOnce();h.assertTrue(f.player.getInventory().getItem(12).getCount()==2,"Recovery changed protected existing items");
    });}

    @GameTest(template="townhall_state_empty",batch="shop40_09",timeoutTicks=200)
    public static void player_callback_failure_after_native_replacement_does_not_rollback_saved_delivery(GameTestHelper h){run(h,f->{
        UUID id=ShopPurchase40.create(f,f.request());f.throwAfterPlayer=true;
        h.assertTrue(ShopPurchase40.resume(f,id).complete(),"A post-save callback exception ignored a proven player file");
        f.assertPaidOnce();f.assertExactItem(0);h.assertTrue(f.diskReceipts().equals(f.receipts()),"Saved player receipt was rolled back after verified replacement");
        f.throwAfterPlayer=false;h.assertTrue(ShopPurchase40.resume(f,id).complete()&&f.diamonds()==3,"Callback error recovery duplicated items");
    });}

    @GameTest(template="townhall_state_empty",batch="shop40_10",timeoutTicks=200)
    public static void post_debit_fault_retains_one_charge_before_any_item(GameTestHelper h){run(h,f->{
        UUID id=ShopPurchase40.create(f,f.request());refuse(()->ShopPurchase40.fixtureResume(f,id,failAt(SavePoint.AFTER_DEBIT)));
        f.assertPaidOnce();h.assertTrue(f.diamonds()==0,"Fault after debit had already delivered an item");
        h.assertTrue(ShopPurchase40.resume(f,id).complete()&&f.diamonds()==3,"Original debit could not resume exactly once");f.assertPaidOnce();
    });}

    @GameTest(template="townhall_state_empty",batch="shop40_11",timeoutTicks=200)
    public static void saved_debit_and_original_historical_price_survive_later_balance_changes(GameTestHelper h){run(h,f->{
        UUID id=ShopPurchase40.create(f,f.request());byte[] intent=f.intent(id);
        refuse(()->ShopPurchase40.fixtureResume(f,id,failAt(SavePoint.AFTER_LEDGER_SAVE)));f.assertPaidOnce();
        f.nation.addTreasury(777);f.nation.addResource(ResourceType.WOOD,9);f.persistLedger();
        h.assertTrue(ShopPurchase40.resume(f,id).complete()&&f.diamonds()==3,"Saved paid operation failed to deliver");
        h.assertTrue(f.nation.treasury()==MONEY-100+777&&f.nation.resource(ResourceType.WOOD)==WOOD-2+9,"Recovery reapplied original debit or rolled back later balances");
        same(h,intent,ShopPurchase40.intentPath(f,id),"Recovery repriced its original historical intent");
    });}

    @GameTest(template="townhall_state_empty",batch="shop40_12",timeoutTicks=200)
    public static void post_insertion_fault_removes_unverified_staged_delivery_before_return(GameTestHelper h){run(h,f->{
        UUID id=ShopPurchase40.create(f,f.request());refuse(()->ShopPurchase40.fixtureResume(f,id,failAt(SavePoint.AFTER_DELIVERY)));
        h.assertTrue(f.diamonds()==0&&f.receipts().isEmpty(),"Unverified staged item/receipt escaped the interrupted operation");f.assertPaidOnce();
        h.assertTrue(ShopPurchase40.resume(f,id).complete()&&f.diamonds()==3,"Rolled-back stage could not resume one delivery");f.assertPaidOnce();
    });}

    @GameTest(template="townhall_state_empty",batch="shop40_13",timeoutTicks=200)
    public static void post_player_save_and_post_commit_faults_reconcile_without_regrant(GameTestHelper h){run(h,f->{
        UUID id=ShopPurchase40.create(f,f.request());refuse(()->ShopPurchase40.fixtureResume(f,id,failAt(SavePoint.AFTER_PLAYER_SAVE)));
        f.assertExactItem(0);f.assertPaidOnce();h.assertTrue(!f.diskReceipts().isEmpty(),"Post-player-save fault lacks native receipt evidence");
        refuse(()->ShopPurchase40.fixtureResume(f,id,failAt(SavePoint.AFTER_COMMITTED)));
        byte[] receipt=Files.readAllBytes(ShopPurchase40.receiptPath(f,id));
        h.assertTrue(ShopPurchase40.resume(f,id).complete()&&f.diamonds()==3,"Durable completion after fault regranted items");f.assertPaidOnce();
        same(h,receipt,ShopPurchase40.receiptPath(f,id),"Repeated durable completion rewrote receipt");
    });}

    @GameTest(template="townhall_state_empty",batch="shop40_14",timeoutTicks=200)
    public static void intent_write_faults_leave_absence_or_one_immutable_pending_purchase(GameTestHelper h){run(h,f->{
        Request request=f.request();refuse(()->ShopPurchase40.fixtureCreate(f,request,failAt(SavePoint.BEFORE_INTENT)));
        h.assertTrue(!Files.exists(ShopPurchase40.intentPath(f,request.id()))&&f.nation.treasury()==MONEY&&f.diamonds()==0,"Pre-intent fault created purchase effects");
        refuse(()->ShopPurchase40.fixtureCreate(f,request,failAt(SavePoint.AFTER_INTENT)));
        byte[] original=f.intent(request.id());h.assertTrue(f.nation.treasury()==MONEY&&f.diamonds()==0,"Post-intent failure charged or delivered");
        h.assertTrue(ShopPurchase40.resume(f,request.id()).complete(),"Retained complete intent could not resume");f.assertPaidOnce();f.assertExactItem(0);
        same(h,original,ShopPurchase40.intentPath(f,request.id()),"Post-intent recovery replaced original history");
    });}

    @GameTest(template="townhall_state_empty",batch="shop40_15",timeoutTicks=200)
    public static void full_inventory_or_changed_selected_slot_never_overwrites_items(GameTestHelper h){run(h,f->{
        for(int i=0;i<36;i++)f.player.getInventory().setItem(i,new ItemStack(Items.STONE,64));f.persistPlayer();
        Request request=f.request();refuse(()->ShopPurchase40.create(f,request));
        h.assertTrue(!Files.exists(ShopPurchase40.intentPath(f,request.id()))&&f.nation.treasury()==MONEY&&f.diamonds()==0,"Full inventory accepted or charged a new purchase");
        f.player.getInventory().setItem(0,ItemStack.EMPTY);f.persistPlayer();UUID id=ShopPurchase40.create(f,request);
        ItemStack unrelated=new ItemStack(Items.EMERALD,5);f.player.getInventory().setItem(0,unrelated);f.persistPlayer();
        refuse(()->ShopPurchase40.resume(f,id));
        h.assertTrue(ItemStack.matches(unrelated,f.player.getInventory().getItem(0))&&f.diamonds()==0,"Changed selected slot was overwritten or another slot was silently substituted");
        h.assertTrue(f.nation.treasury()==MONEY&&f.nation.resource(ResourceType.WOOD)==WOOD,"Occupied reserved slot charged before it could deliver");
    });}

    @GameTest(template="townhall_state_empty",batch="shop40_16",timeoutTicks=200)
    public static void request_and_price_are_deep_immutable_and_invalid_costs_refuse(GameTestHelper h){run(h,f->{
        invalid(()->new Price(-1,Map.of()));invalid(()->new Price(0,Map.of()));invalid(()->new Price(1,Map.of(ResourceType.WOOD,-2L)));
        Price validPrice=new Price(100,Map.of(ResourceType.WOOD,2L));
        for(Request bad:List.of(
                new Request(new UUID(0,0),"fixture_diamonds","synthetic-shop40-v1",f.facility,validPrice,f.item()),
                new Request(UUID.randomUUID(),"fixture_diamonds","synthetic-shop40-v1",new CompoundTag(),validPrice,f.item()),
                new Request(UUID.randomUUID(),"fixture_diamonds","synthetic-shop40-v1",f.facility,validPrice,ItemStack.EMPTY),
                new Request(UUID.randomUUID(),"fixture_diamonds","synthetic-shop40-v1",f.facility,validPrice,new ItemStack(Items.DIAMOND,65)))) {
            refuse(()->ShopPurchase40.create(f,bad));
            h.assertTrue(!Files.exists(ShopPurchase40.intentPath(f,bad.id())),"Malformed request created purchase history");
        }
        h.assertTrue(f.nation.treasury()==MONEY&&f.diamonds()==0,"Malformed request charged or delivered");
        Map<ResourceType,Long> resources=new EnumMap<>(ResourceType.class);resources.put(ResourceType.WOOD,2L);
        CompoundTag facility=f.facility.copy();ItemStack item=f.item();Price price=new Price(100,resources);
        Request request=new Request(UUID.randomUUID(),"fixture_diamonds","synthetic-shop40-v1",facility,price,item);
        resources.put(ResourceType.WOOD,99L);facility.putString("id","changed");item.setCount(50);request.facility().putString("id","getter-mutated");request.item().setCount(51);
        UUID id=ShopPurchase40.create(f,request);h.assertTrue(ShopPurchase40.resume(f,id).complete(),"Deep-copied request was changed through caller references");f.assertPaidOnce();f.assertExactItem(0);
    });}

    @GameTest(template="townhall_state_empty",batch="shop40_17",timeoutTicks=200)
    public static void current_counter_authority_is_rechecked_before_charge_and_delivery(GameTestHelper h){run(h,f->{
        Request request=f.request();f.allowed=false;refuse(()->ShopPurchase40.create(f,request));
        h.assertTrue(!Files.exists(ShopPurchase40.intentPath(f,request.id()))&&f.nation.treasury()==MONEY,"Revoked opening created a purchase intent");
        f.allowed=true;UUID id=ShopPurchase40.create(f,request);byte[] intent=f.intent(id);f.allowed=false;refuse(()->ShopPurchase40.resume(f,id));
        h.assertTrue(f.nation.treasury()==MONEY&&f.diamonds()==0,"Revoked counter session charged or delivered");same(h,intent,ShopPurchase40.intentPath(f,id),"Revoked authorization changed history");
        f.allowed=true;f.facility.putString("id","foreign-current-counter");refuse(()->ShopPurchase40.resume(f,id));
        h.assertTrue(f.nation.treasury()==MONEY&&f.diamonds()==0,"Changed facility identity accepted old authorization");
        f.facility.putString("id","synthetic-shop40-v1");f.beforeLedger=()->f.allowed=false;
        refuse(()->ShopPurchase40.resume(f,id));f.assertPaidOnce();
        h.assertTrue(f.diamonds()==0&&f.receipts().isEmpty(),"Authority revoked during native ledger save still delivered an item");
        same(h,intent,ShopPurchase40.intentPath(f,id),"Mid-save revocation rewrote original purchase");
    });}

    @GameTest(template="townhall_state_empty",batch="shop40_18",timeoutTicks=200)
    public static void changed_nation_or_ambiguous_membership_cannot_adopt_old_purchase(GameTestHelper h){run(h,f->{
        UUID id=ShopPurchase40.create(f,f.request());byte[] original=f.intent(id);
        NationData foreign=f.data.createNation("Shop40_other_"+NAMES.incrementAndGet(),UUID.randomUUID());foreign.addMember(f.player.getUUID());
        refuse(()->ShopPurchase40.resume(f,id));foreign.removeMember(f.player.getUUID());
        h.assertTrue(f.nation.treasury()==MONEY&&f.diamonds()==0,"Changed or ambiguous nation membership spent a budget or received items");
        h.assertTrue(f.data.nations().remove(f.nation),"Fixture did not remove its original synthetic nation");
        NationData replacement=f.data.createNation("Shop40_new_"+NAMES.incrementAndGet(),f.player.getUUID());replacement.setTreasury(MONEY);replacement.addResource(ResourceType.WOOD,WOOD);
        refuse(()->ShopPurchase40.resume(f,id));
        h.assertTrue(replacement.treasury()==MONEY&&replacement.resource(ResourceType.WOOD)==WOOD&&f.nation.treasury()==MONEY&&f.diamonds()==0,"New nation adopted an old nation's pending purchase");
        same(h,original,ShopPurchase40.intentPath(f,id),"Nation changes rewrote original binding");
    });}

    @GameTest(template="townhall_state_empty",batch="shop40_19",timeoutTicks=200)
    public static void corrupt_original_and_resealed_foreign_completion_are_retained_and_refused(GameTestHelper h){run(h,f->{
        UUID id=ShopPurchase40.create(f,f.request());byte[] original=f.intent(id),corrupt=original.clone();corrupt[corrupt.length-1]^=1;
        Files.write(ShopPurchase40.intentPath(f,id).resolveSibling("retained-original-"+id+".dat"),original,StandardOpenOption.CREATE_NEW);
        Files.write(ShopPurchase40.intentPath(f,id),corrupt);refuse(()->ShopPurchase40.resume(f,id));
        h.assertTrue(f.nation.treasury()==MONEY&&f.diamonds()==0,"Corrupt original authorized purchase effects");same(h,corrupt,ShopPurchase40.intentPath(f,id),"Corrupt evidence was replaced");
        try(Fixture other=new Fixture(h)) {
            UUID done=ShopPurchase40.create(other,other.request());ShopPurchase40.resume(other,done);
            Path receipt=ShopPurchase40.receiptPath(other,done);CompoundTag hostile=ProducerJournal.read(receipt);hostile.putString("unrecognizedAuthority","approved");ProducerJournal.write(receipt,hostile,true);byte[] bytes=Files.readAllBytes(receipt);
            refuse(()->ShopPurchase40.resume(other,done));other.assertPaidOnce();h.assertTrue(other.diamonds()==3,"Malformed completion caused replay");same(h,bytes,receipt,"Malformed completion evidence was overwritten");
        }
    });}

    @GameTest(template="townhall_state_empty",batch="shop40_20",timeoutTicks=200)
    public static void pending_intent_uuid_and_reentrant_saves_cannot_create_a_second_purchase(GameTestHelper h){run(h,f->{
        Request request=f.request();UUID id=ShopPurchase40.create(f,request);byte[] original=f.intent(id);
        refuse(()->ShopPurchase40.create(f,request));
        Request changed=new Request(id,request.offer(),request.catalog(),request.facility(),new Price(101,Map.of(ResourceType.WOOD,2L)),request.item());
        refuse(()->ShopPurchase40.create(f,changed));Request competing=f.request();refuse(()->ShopPurchase40.create(f,competing));
        h.assertTrue(!Files.exists(ShopPurchase40.intentPath(f,competing.id()))&&f.nation.treasury()==MONEY,"Another pending purchase created history or charged");
        same(h,original,ShopPurchase40.intentPath(f,id),"Repeated/conflicting creation replaced original intent");
        try(Fixture other=new Fixture(h)) {
            Request nested=other.request();f.beforeLedger=()->{f.reentrantAttempted=true;refuse(()->ShopPurchase40.create(other,nested));};
            h.assertTrue(ShopPurchase40.resume(f,id).complete()&&f.reentrantAttempted,"Native save did not exercise guarded reentrancy");
            h.assertTrue(!Files.exists(ShopPurchase40.intentPath(other,nested.id()))&&other.nation.treasury()==MONEY&&other.diamonds()==0,"Reentrant save callback created another purchase");
        }
        f.assertPaidOnce();f.assertExactItem(0);
    });}

    @GameTest(template="townhall_state_empty",batch="shop40_21",timeoutTicks=200)
    public static void removed_stage_and_receipt_saved_as_empty_never_enable_redelivery(GameTestHelper h){run(h,f->{
        UUID id=ShopPurchase40.create(f,f.request());byte[] original=f.intent(id);
        refuse(()->ShopPurchase40.fixtureResume(f,id,point->{if(point==SavePoint.AFTER_DELIVERY){
            f.player.getInventory().setItem(0,ItemStack.EMPTY);f.receipts().remove(id.toString());f.persistPlayer();
            throw new IOException("Injected staged item consumption and receipt removal before interruption");
        }}));
        f.assertPaidOnce();h.assertTrue(f.diamonds()==0&&f.diskReceipts().getString(id.toString()).isEmpty(),"Fixture did not persist its stripped delivery state");
        unresolvedDelivery(h,f,id);f.persistPlayer();refuse(()->ShopPurchase40.resume(f,id));refuse(()->ShopPurchase40.resume(f,id));
        refuse(()->ShopPurchase40.create(f,f.request()));f.assertPaidOnce();
        h.assertTrue(f.diamonds()==0,"Native empty-state save allowed an already escaped delivery to be granted again");
        same(h,original,ShopPurchase40.intentPath(f,id),"Stripped delivery recovery replaced its original intent");
    });}

    @GameTest(template="townhall_state_empty",batch="shop40_22",timeoutTicks=200)
    public static void moved_stage_and_removed_receipt_cannot_buy_a_second_copy_on_retry(GameTestHelper h){run(h,f->{
        UUID id=ShopPurchase40.create(f,f.request());byte[] original=f.intent(id);
        refuse(()->ShopPurchase40.fixtureResume(f,id,point->{if(point==SavePoint.AFTER_DELIVERY){
            f.player.getInventory().setItem(9,f.player.getInventory().getItem(0).copy());f.player.getInventory().setItem(0,ItemStack.EMPTY);
            f.receipts().remove(id.toString());f.persistPlayer();throw new IOException("Injected moved stage and removed receipt");
        }}));
        unresolvedDelivery(h,f,id);f.assertExactItem(9);f.assertPaidOnce();f.persistPlayer();
        refuse(()->ShopPurchase40.resume(f,id));refuse(()->ShopPurchase40.create(f,f.request()));
        h.assertTrue(f.player.getInventory().getItem(0).isEmpty()&&f.diamonds()==3,"Moved staged delivery was duplicated or replaced during recovery");
        f.assertExactItem(9);f.assertPaidOnce();same(h,original,ShopPurchase40.intentPath(f,id),"Moved delivery altered original history");
    });}

    @GameTest(template="townhall_state_empty",batch="shop40_23",timeoutTicks=200)
    public static void native_save_that_strips_staged_receipt_cannot_verify_or_reopen_delivery(GameTestHelper h){run(h,f->{
        UUID id=ShopPurchase40.create(f,f.request());boolean[] stripped={false},verified={false};
        f.beforePlayer=()->{if(!stripped[0]&&!f.receipts().getString(id.toString()).isEmpty()){
            stripped[0]=true;f.receipts().remove(id.toString());
        }};
        refuse(()->ShopPurchase40.fixtureResume(f,id,point->{if(point==SavePoint.AFTER_PLAYER_SAVE)verified[0]=true;}));
        h.assertTrue(stripped[0]&&!verified[0],"Native receipt stripping passed the frozen player-save verification boundary");
        h.assertTrue(f.diskReceipts().getString(id.toString()).isEmpty(),"Fixture did not persist the stripped receipt");
        unresolvedDelivery(h,f,id);f.assertPaidOnce();int retained=f.diamonds();f.beforePlayer=null;f.persistPlayer();
        refuse(()->ShopPurchase40.resume(f,id));refuse(()->ShopPurchase40.create(f,f.request()));
        h.assertTrue(f.diamonds()==retained,"Failed frozen-save verification permitted another delivery");f.assertPaidOnce();
    });}

    @GameTest(template="townhall_state_empty",batch="shop40_24",timeoutTicks=200)
    public static void durable_delivery_receipt_survives_unrelated_post_save_inventory_mutation(GameTestHelper h){run(h,f->{
        UUID id=ShopPurchase40.create(f,f.request());boolean[] changed={false};
        f.afterPlayer=()->{if(!changed[0]&&!f.receipts().getString(id.toString()).isEmpty()){
            changed[0]=true;f.player.getInventory().setItem(12,new ItemStack(Items.BOOK,2));
        }};
        refuse(()->ShopPurchase40.resume(f,id));h.assertTrue(changed[0],"Fixture did not mutate live inventory after saving delivery");
        String hash=TownHallRevisions.sha(f.intent(id));
        h.assertTrue(f.receipts().getString(id.toString()).equals(hash)&&f.diskReceipts().getString(id.toString()).equals(hash),"Failed live verification rolled back an already durable delivery receipt");
        f.assertExactItem(0);f.assertPaidOnce();
        h.assertTrue(f.player.getInventory().getItem(12).is(Items.BOOK)&&f.player.getInventory().getItem(12).getCount()==2,"Delivery rollback changed unrelated post-save inventory");
        f.afterPlayer=null;f.persistPlayer();
        h.assertTrue(f.diskReceipts().getString(id.toString()).equals(hash),"Later native save erased proven delivery evidence");
        try{h.assertTrue(ShopPurchase40.resume(f,id).complete(),"Recovery returned an incomplete delivery without refusing");}
        catch(IOException held){h.assertTrue(f.phase(id).equals("HELD"),"Proven delivery was neither recoverable nor explicitly held");}
        f.assertExactItem(0);f.assertPaidOnce();h.assertTrue(f.diamonds()==3,"Post-save mutation recovery duplicated the durable item");
    });}

    @GameTest(template="townhall_state_empty",batch="shop40_25",timeoutTicks=200)
    public static void completed_history_allows_a_later_new_nation_purchase(GameTestHelper h){run(h,f->{
        UUID prior=ShopPurchase40.create(f,f.request());ShopPurchase40.resume(f,prior);
        byte[] original=f.intent(prior),completion=Files.readAllBytes(ShopPurchase40.receiptPath(f,prior));f.assertPaidOnce();
        h.assertTrue(f.data.nations().remove(f.nation),"Fixture did not remove its completed synthetic nation");
        NationData replacement=f.data.createNation("Shop40_later_"+NAMES.incrementAndGet(),f.player.getUUID());replacement.setTreasury(MONEY);replacement.addResource(ResourceType.WOOD,WOOD);
        f.facility.putUUID("owner",replacement.id());f.persistLedger();
        UUID next=ShopPurchase40.create(f,f.request());h.assertTrue(!next.equals(prior)&&ShopPurchase40.resume(f,next).complete(),"Completed older-nation history blocked a distinct current-nation purchase");
        h.assertTrue(replacement.treasury()==MONEY-100&&replacement.resource(ResourceType.WOOD)==WOOD-2&&f.diamonds()==6,"New nation purchase reused old funds or old delivery");
        f.assertPaidOnce();f.assertExactItem(0);f.assertExactItem(1);
        h.assertTrue(ShopPurchase40.pending(f).isEmpty()&&f.receipts().contains(prior.toString())&&f.receipts().contains(next.toString()),"Distinct completed deliveries did not retain separate receipt identities");
        same(h,original,ShopPurchase40.intentPath(f,prior),"New nation purchase rewrote old intent");same(h,completion,ShopPurchase40.receiptPath(f,prior),"New nation purchase rewrote old completion");
    });}

    @GameTest(template="townhall_state_empty",batch="shop40_26",timeoutTicks=200)
    public static void paid_receipt_mutation_during_native_ledger_save_refuses_before_delivery(GameTestHelper h){run(h,f->{
        UUID id=ShopPurchase40.create(f,f.request());byte[] original=f.intent(id);String foreign="0".repeat(64);
        f.beforeLedger=()->f.data.setPlacedMapAsset("shop40_paid:"+id,foreign);
        refuse(()->ShopPurchase40.resume(f,id));f.assertPaidOnce();
        h.assertTrue(f.diamonds()==0&&f.receipts().isEmpty(),"Mutated saved debit receipt authorized item delivery");
        h.assertTrue(foreign.equals(f.data.placedMapAsset("shop40_paid:"+id)),"Conflicting debit evidence was silently repaired");
        f.beforeLedger=null;refuse(()->ShopPurchase40.resume(f,id));
        h.assertTrue(f.diamonds()==0&&f.receipts().isEmpty(),"Conflicting debit receipt enabled later delivery");f.assertPaidOnce();
        same(h,original,ShopPurchase40.intentPath(f,id),"Debit receipt conflict replaced original intent");
    });}

    @GameTest(template="townhall_state_empty",batch="shop40_27",timeoutTicks=200)
    public static void changed_staged_item_saved_with_valid_receipt_stays_held_without_redelivery(GameTestHelper h){run(h,f->{
        UUID id=ShopPurchase40.create(f,f.request());byte[] original=f.intent(id);String hash=TownHallRevisions.sha(original);
        ItemStack replacement=new ItemStack(Items.EMERALD,7);boolean[] changed={false},verified={false};
        f.beforePlayer=()->{if(!changed[0]&&f.receipts().getString(id.toString()).equals(hash)){
            changed[0]=true;f.player.getInventory().setItem(0,replacement.copy());
        }};
        refuse(()->ShopPurchase40.fixtureResume(f,id,point->{if(point==SavePoint.AFTER_PLAYER_SAVE)verified[0]=true;}));
        h.assertTrue(changed[0]&&!verified[0],"Changed staged item passed frozen player-save verification");
        CompoundTag nativePlayer=NbtIo.readCompressed(f.root.resolve("playerdata").resolve(f.player.getUUID()+".dat").toFile());
        ListTag inventory=nativePlayer.getList("Inventory",Tag.TAG_COMPOUND);boolean savedReplacement=false;
        for(int i=0;i<inventory.size();i++)if(inventory.getCompound(i).getByte("Slot")==0)
            savedReplacement=ItemStack.matches(replacement,ItemStack.of(inventory.getCompound(i)));
        h.assertTrue(savedReplacement&&f.diskReceipts().getString(id.toString()).equals(hash),"Fixture did not persist a different item together with the original receipt");
        h.assertTrue(f.phase(id).equals("HELD")&&f.receipts().getString(id.toString()).equals(hash),"Ambiguous saved item was committed or its original receipt was erased");
        byte[] held=Files.readAllBytes(ShopPurchase40.receiptPath(f,id));f.beforePlayer=null;f.persistPlayer();
        refuse(()->ShopPurchase40.resume(f,id));refuse(()->ShopPurchase40.resume(f,id));refuse(()->ShopPurchase40.create(f,f.request()));
        f.assertPaidOnce();h.assertTrue(f.diamonds()==0&&ItemStack.matches(replacement,f.player.getInventory().getItem(0)),"Held altered delivery was overwritten or redelivered");
        same(h,held,ShopPurchase40.receiptPath(f,id),"Retry changed the retained delivery hold");same(h,original,ShopPurchase40.intentPath(f,id),"Altered delivery replaced its original item intent");
    });}

    private interface Attempt { void run() throws Exception; }
    private interface Body { void run(Fixture f) throws Exception; }
    private static void run(GameTestHelper h,Body body) {
        try(Fixture f=new Fixture(h)){body.run(f);h.succeed();}catch(Exception error){throw new RuntimeException(error);}
    }
    private static void refuse(Attempt attempt)throws Exception {try{attempt.run();}catch(IOException expected){return;}throw new AssertionError("Expected guarded shop refusal");}
    private static void invalid(Attempt attempt)throws Exception {try{attempt.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("Expected invalid price rejection");}
    private static Fault failAt(SavePoint wanted){return point->{if(point==wanted)throw new IOException("Injected shop fault at "+wanted);};}
    private static void same(GameTestHelper h,byte[] expected,Path file,String message)throws IOException {h.assertTrue(Arrays.equals(expected,Files.readAllBytes(file)),message);}
    private static void unresolvedDelivery(GameTestHelper h,Fixture f,UUID id)throws IOException {
        h.assertTrue(Set.of("DELIVERING","HELD").contains(f.phase(id)),"Ambiguous staged delivery was reset to a retryable or completed state");
    }

    private static final class Fixture implements Environment,AutoCloseable {
        final GameTestHelper h;final ServerLevel level;final CatanSavedData data;final NationData nation;final Path root;final CompoundTag facility;
        FakePlayer player;boolean allowed=true,suppressLedger,suppressPlayer,throwAfterLedger,throwAfterPlayer,reentrantAttempted;int ledgerSaves,playerSaves;Attempt beforeLedger,beforePlayer,afterPlayer;
        Fixture(GameTestHelper h)throws Exception {
            this.h=h;level=h.getLevel();root=level.getServer().getWorldPath(LevelResource.ROOT).toRealPath();
            if(!(level.getServer() instanceof GameTestServer)||!level.getServer().isSameThread()
                    ||!root.getFileName().toString().equals("world")
                    ||!root.getParent().getFileName().toString().equals("controlled-catancraft_shop40-run")
                    ||!Files.readString(root.getParent().resolve(".catancraft-controlled-fixture-v1")).equals("CatanCraft synthetic flat fixture v1\n"))
                throw new IOException("Shop purchase tests require their marked disposable native world");
            player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"Shop40_"+NAMES.incrementAndGet()));player.setGameMode(GameType.SURVIVAL);player.setHealth(20);
            player.getInventory().clearContent();player.setPos(0.5,140,0.5);
            data=CatanSavedData.get(level.getServer());nation=data.createNation("Shop40_"+NAMES.incrementAndGet(),player.getUUID());nation.setTreasury(MONEY);nation.addResource(ResourceType.WOOD,WOOD);
            facility=new CompoundTag();facility.putString("city","fixture");facility.putString("id","synthetic-shop40-v1");facility.putUUID("owner",nation.id());
            level.getServer().getPlayerList().getPlayers().add(player);
            try{persistLedger();persistPlayer();}catch(Exception error){close();throw error;}
        }
        Request request(){return new Request(UUID.randomUUID(),"fixture_diamonds","synthetic-shop40-v1",facility,new Price(100,Map.of(ResourceType.WOOD,2L)),item());}
        ItemStack item(){ItemStack result=new ItemStack(Items.DIAMOND,3);result.setHoverName(Component.literal("Synthetic shop delivery"));result.getOrCreateTag().putString("FixturePayload","preserve-exact-native-nbt");return result;}
        public ServerPlayer player(){return player;}public CatanSavedData data(){return data;}
        public void guard(CompoundTag originalFacility)throws IOException {
            if(!allowed||!facility.equals(originalFacility))throw new IOException("Synthetic current counter authority changed");
            if(player.serverLevel()!=level||data!=CatanSavedData.get(level.getServer()))throw new IOException("Synthetic shop level/data changed");
        }
        public void persistLedger()throws IOException {
            if(beforeLedger!=null)try{beforeLedger.run();}catch(IOException error){throw error;}catch(Exception error){throw new IOException(error);}
            ledgerSaves++;if(suppressLedger)return;data.setDirty();level.getDataStorage().save();if(throwAfterLedger)throw new IOException("Injected callback after actual ledger save");
        }
        public void persistPlayer()throws IOException {
            playerSaves++;if(suppressPlayer)return;hook(beforePlayer);level.getServer().getPlayerList().saveAll();hook(afterPlayer);
            if(throwAfterPlayer)throw new IOException("Injected callback after native player replacement");
        }
        private void hook(Attempt callback)throws IOException {if(callback!=null)try{callback.run();}catch(IOException error){throw error;}catch(Exception error){throw new IOException(error);}}
        byte[] intent(UUID id)throws IOException{return Files.readAllBytes(ShopPurchase40.intentPath(this,id));}
        String phase(UUID id)throws IOException {Path path=ShopPurchase40.receiptPath(this,id);return Files.exists(path)?ProducerJournal.read(path).getString("phase"):"";}
        CompoundTag receipts(){return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound("CatanCraftShop40");}
        CompoundTag diskReceipts()throws IOException {return NbtIo.readCompressed(root.resolve("playerdata").resolve(player.getUUID()+".dat").toFile()).getCompound("ForgeData").getCompound(Player.PERSISTED_NBT_TAG).getCompound("CatanCraftShop40");}
        int diamonds(){int result=0;for(int i=0;i<36;i++)if(player.getInventory().getItem(i).is(Items.DIAMOND))result+=player.getInventory().getItem(i).getCount();return result;}
        void assertPaidOnce(){h.assertTrue(nation.treasury()==MONEY-100&&nation.resource(ResourceType.WOOD)==WOOD-2,"Original price was not charged exactly once");}
        void assertExactItem(int slot){h.assertTrue(ItemStack.matches(item(),player.getInventory().getItem(slot)),"Delivered stack count or full native NBT differs from the immutable offer");}
        void replacePlayer(FakePlayer replacement){level.getServer().getPlayerList().getPlayers().remove(player);player=replacement;level.getServer().getPlayerList().getPlayers().add(player);}
        public void close(){level.getServer().getPlayerList().getPlayers().remove(player);}
    }
}
