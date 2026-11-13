package com.quin.catancraft.world;

import com.mojang.authlib.GameProfile;
import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.command.NationProcurement;
import com.quin.catancraft.data.*;
import com.quin.catancraft.map.MapDefinitionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;

/** One scenario, executed in explicitly separate seed and verify JVMs.
 * The seed phase is fixture preparation, not a claim that restart verification passed.
 * The retained historical paid path uses an exact-world fixture bridge after 050
 * moved public aliases to physical sessions. No manually tagged entities or NBT
 * reconstruction replace that old payment/spawn path. This does not qualify 050 purchasing.
 */
@GameTestHolder("catancraft_sbw_procurement35") @PrefixGameTestTemplate(false)
public final class SbwProcurement35Tests {
    private static final String NS="catancraft_sbw_procurement35";
    private static final String TOKEN="sec01:procurement35:session";
    private static final String NAME="SbwProcurement35";
    private static final int FLAGS=2|16|32;
    private static final AABB AREA=new AABB(-930,130,-930,-810,160,-810);
    private static final BlockPos CONTROL=new BlockPos(-900,140,-840);
    private record Purchase(String key,String type,long money,Map<ResourceType,Long> materials,BlockPos player) {}
    private static final List<Purchase> PURCHASES=List.of(
        new Purchase("pickup","superbwarfare:sodayo_pick_up",4000,
            Map.of(ResourceType.STEEL,20L,ResourceType.MECHANICAL_PARTS,10L,ResourceType.FUEL,8L),new BlockPos(-900,140,-900)),
        new Purchase("lav150","superbwarfare:lav_150",8500,
            Map.of(ResourceType.STEEL,50L,ResourceType.MECHANICAL_PARTS,25L,ResourceType.FUEL,20L,ResourceType.ELECTRONICS,5L),new BlockPos(-840,140,-900)));
    private static Fixture active;

    @GameTest(template="townhall_state_empty",batch="sbw_procurement35",timeoutTicks=1800)
    public static void actual_purchase_persists_through_separate_jvm(GameTestHelper h)throws Exception {
        String phase=System.getProperty("catancraft.sbwRestartPhase","");
        h.assertTrue(phase.equals("seed")||phase.equals("verify"),"Explicit seed|verify JVM phase is required");
        h.assertTrue(active==null,"Another SEC-01 restart fixture is active");
        Fixture f=new Fixture(h,phase);active=f;
        await(f,0,()->{if(phase.equals("seed"))f.seed();else f.verify();});
    }

    @AfterBatch(batch="sbw_procurement35")
    public static void releaseFixture(ServerLevel level){if(active!=null){active.close();active=null;}}

    private interface Checked {void run()throws Exception;}
    private interface Interaction {Object run()throws Exception;}
    private static void await(Fixture f,int ticks,Checked work){
        try{
            f.lease.keepAlive();
            boolean resident=f.lease.ready();
            if(resident&&f.phase.equals("verify"))for(Tag t:f.proof.getList("vehicles",Tag.TAG_COMPOUND))
                resident&=f.level.getEntity(((CompoundTag)t).getUUID("uuid"))!=null;
            if(resident){work.run();return;}
            f.h.assertTrue(ticks<600,"Actual persisted vehicle chunks/entities did not become resident");
            f.h.runAfterDelay(5,()->await(f,ticks+5,work));
        }catch(Exception failure){f.close();throw new RuntimeException(failure);}
    }

    private static final class Fixture {
        final GameTestHelper h;final MinecraftServer server;final ServerLevel level;final CatanSavedData data;
        final String phase;final Path root,history,verified;final SectorChunkLease lease;final String process,runtimeManifest;
        final CompoundTag proof;final byte[] historyBytes;FakePlayer player;NationData nation;
        final Map<String,UUID> ids=new LinkedHashMap<>();
        Fixture(GameTestHelper h,String phase)throws Exception {
            this.h=h;this.phase=phase;level=h.getLevel();server=level.getServer();
            h.assertTrue(server instanceof GameTestServer,"SEC-01 fixture requires an isolated GameTestServer");
            root=server.getWorldPath(LevelResource.ROOT).toRealPath();
            h.assertTrue(root.getFileName().toString().equals("world")
                &&root.getParent().getFileName().toString().equals("controlled-"+NS+"-run"),"Foreign SEC-01 fixture world path");
            h.assertTrue(Files.readString(root.getParent().resolve(".catancraft-controlled-fixture-v1"))
                .equals("CatanCraft synthetic flat fixture v1\n"),"Missing exact controlled-world provenance");
            Properties properties=new Properties();try(var in=Files.newInputStream(root.getParent().resolve("server.properties"))){properties.load(in);}
            h.assertTrue("world".equals(properties.getProperty("level-name"))
                &&"minecraft:flat".equals(properties.getProperty("level-type")),"Foreign SEC-01 fixture generator/settings");
            h.assertTrue(level.dimension().location().toString().equals("minecraft:overworld")&&level.getSeed()==0,"Foreign SEC-01 fixture dimension/seed");
            h.assertTrue(ModList.get().isLoaded("superbwarfare"),"Real Superb Warfare runtime is required");
            Class.forName("com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity");
            runtimeManifest=System.getProperty("catancraft.sbwRuntimeManifestSHA256","");
            h.assertTrue(runtimeManifest.matches("[0-9a-f]{64}"),"Pinned SBW runtime manifest SHA-256 is required");
            process=ProcessHandle.current().pid()+":"+ManagementFactory.getRuntimeMXBean().getStartTime();
            history=root.resolve("data/catancraft_sec01_procurement35_seed.dat");
            verified=root.resolve("data/catancraft_sec01_procurement35_verified.dat");
            h.assertTrue(!Files.exists(verified,LinkOption.NOFOLLOW_LINKS),"Already-verified SEC-01 history must not be reused");
            data=CatanSavedData.get(server);
            if(phase.equals("seed")){
                h.assertTrue(!Files.exists(history,LinkOption.NOFOLLOW_LINKS)&&data.placedMapAsset(TOKEN)==null
                    &&data.nations().isEmpty(),"Refusing preexisting SEC-01 seed history or nation");
                proof=new CompoundTag();historyBytes=null;
            }else{
                historyBytes=PaidConstructionRecovery.readBounded(history,8*1024*1024+40);proof=ProducerJournal.read(historyBytes);validateProof();
                nation=data.nation(proof.getUUID("nation"));
                h.assertTrue(nation!=null&&proof.getUUID("leader").equals(nation.leaderId()),"Persisted purchased nation/leader missing");
                h.assertTrue(proof.getUUID("session").toString().equals(data.placedMapAsset(TOKEN)),"Seed proof belongs to another SavedData instance");
                player=player(proof.getUUID("leader"));
                for(Tag t:proof.getList("vehicles",Tag.TAG_COMPOUND)){CompoundTag row=(CompoundTag)t;ids.put(row.getString("key"),row.getUUID("uuid"));}
            }
            var def=MapDefinitionManager.territory("a");
            h.assertTrue(def!=null&&"minecraft:overworld".equals(def.dimension()),"Canonical City A is missing");
            for(Purchase p:PURCHASES)h.assertTrue(def.contains("minecraft:overworld",p.player().getX(),p.player().getZ()),"Fixture purchase is outside canonical City A");
            lease=SectorChunkLease.acquire(level,AREA);
            try{for(var chunk:lease.positions())h.assertTrue(!level.getForcedChunks().contains(chunk.toLong()),"Unexpected persistent forceload in SEC-01 fixture");}
            catch(RuntimeException failure){lease.close();throw failure;}
        }
        void validateProof()throws IOException {
            Set<String> keys=Set.of("schema","namespace","phase","world","dimension","seed","process","session","leader","nation","nationBefore","nationAfter","territoryAfter","vehicles","sbwVersion","runtimeManifestSHA256");
            h.assertTrue(proof.getAllKeys().equals(keys)&&proof.getInt("schema")==1&&NS.equals(proof.getString("namespace"))
                &&"SEEDED".equals(proof.getString("phase"))&&root.toString().equals(proof.getString("world"))
                &&level.dimension().location().toString().equals(proof.getString("dimension"))&&level.getSeed()==proof.getLong("seed")
                &&proof.hasUUID("session")&&proof.hasUUID("leader")&&proof.hasUUID("nation"),"Foreign or malformed SEC-01 seed proof");
            h.assertTrue(!process.equals(proof.getString("process"))&&!proof.getString("process").isBlank(),"Verify phase must run in a new JVM");
            h.assertTrue(sbwVersion().equals(proof.getString("sbwVersion")),"SBW runtime version changed across restart");
            h.assertTrue(runtimeManifest.equals(proof.getString("runtimeManifestSHA256")),"Pinned SBW runtime manifest changed across restart");
            ListTag rows=proof.getList("vehicles",Tag.TAG_COMPOUND);Set<UUID> unique=new HashSet<>();
            h.assertTrue(rows.size()==PURCHASES.size(),"Incomplete real-purchase entity proof");
            for(int i=0;i<PURCHASES.size();i++){
                var p=PURCHASES.get(i);var row=rows.getCompound(i);
                h.assertTrue(row.getAllKeys().equals(Set.of("key","type","uuid","x","y","z"))&&p.key().equals(row.getString("key"))
                    &&p.type().equals(row.getString("type"))&&row.hasUUID("uuid")&&unique.add(row.getUUID("uuid"))
                    &&AREA.contains(row.getDouble("x"),row.getDouble("y"),row.getDouble("z")),"Foreign, duplicated or missing purchased entity proof");
            }
        }
        String sbwVersion(){return ModList.get().getModContainerById("superbwarfare").orElseThrow().getModInfo().getVersion().toString();}
        FakePlayer player(UUID leader){
            FakePlayer p=FakePlayerFactory.get(level,new GameProfile(leader,NAME));p.getInventory().clearContent();p.setShiftKeyDown(true);
            return p;
        }
        void seed()throws Exception {
            var territory=data.territory("a");h.assertTrue(territory!=null&&territory.ownerNationId()==null&&territory.buildings().isEmpty(),"Refusing an owned/developed fixture territory");
            for(Purchase p:PURCHASES)platform(p.player());platform(CONTROL);
            h.assertTrue(purchasedEntities().isEmpty()&&drops().isEmpty(),"Preexisting purchased vehicle or dropped-item fixture");
            UUID leader=UUID.randomUUID();player=player(leader);nation=data.createNation("SEC01 procurement35",leader);
            nation.setTreasury(100000);for(ResourceType type:ResourceType.values())nation.addResource(type,1000);
            territory.setOwnerNationId(nation.id());territory.setCityLevel(2);
            territory.buildings().add(new BuildingInstance(BuildingType.VEHICLE_FACTORY,1,0,SchematicAssetRegistry.requiredPlot(BuildingType.VEHICLE_FACTORY)));
            UUID session=UUID.randomUUID();data.setPlacedMapAsset(TOKEN,session.toString());data.setDirty();
            CompoundTag originalNation=nation.save().copy();
            for(Purchase p:PURCHASES){
                positionPlayer(p.player());player.getInventory().clearContent();
                CompoundTag before=nation.save().copy();Set<UUID> existing=purchasedEntities().keySet();
                h.assertTrue(NationProcurement.legacyFixturePurchase(player,p.key())==1,"Historical paid procurement fixture refused "+p.key());
                assertCharge(before,p);h.assertTrue(player.getInventory().isEmpty()&&drops().isEmpty(),"Procurement awarded a portable item or dropped an item");
                Map<UUID,Entity> after=purchasedEntities();Set<UUID> created=new HashSet<>(after.keySet());created.removeAll(existing);
                h.assertTrue(created.size()==1,"Real procurement did not create exactly one purchased vehicle");
                UUID id=created.iterator().next();Entity e=after.get(id);assertPurchased(e,p,id);ids.put(p.key(),id);
                guard(e,p);h.assertTrue(nation.save().equals(expectedAfter(before,p)),"Retrieval attempt changed actual purchase accounting");
            }
            player.getInventory().clearContent();
            // Allow real vehicle physics to settle; never set NoGravity or reconstruct vehicle NBT.
            h.runAfterDelay(40,()->{try{
                lease.keepAlive();assertCounts();assertTotalCharge(originalNation);
                h.assertTrue(player.getInventory().isEmpty()&&drops().isEmpty(),"Procurement emitted an item during native settling ticks");
                proof.putInt("schema",1);proof.putString("namespace",NS);proof.putString("phase","SEEDED");
                proof.putString("world",root.toString());proof.putString("dimension",level.dimension().location().toString());proof.putLong("seed",level.getSeed());
                proof.putString("process",process);proof.putUUID("session",session);proof.putUUID("leader",leader);proof.putUUID("nation",nation.id());
                proof.put("nationBefore",originalNation);proof.put("nationAfter",nation.save().copy());proof.put("territoryAfter",territory.save().copy());proof.putString("sbwVersion",sbwVersion());
                proof.putString("runtimeManifestSHA256",runtimeManifest);
                ListTag rows=new ListTag();for(Purchase p:PURCHASES){Entity e=level.getEntity(ids.get(p.key()));assertPurchased(e,p,ids.get(p.key()));
                    CompoundTag row=new CompoundTag();row.putString("key",p.key());row.putString("type",p.type());row.putUUID("uuid",e.getUUID());
                    row.putDouble("x",e.getX());row.putDouble("y",e.getY());row.putDouble("z",e.getZ());rows.add(row);}
                proof.put("vehicles",rows);save();diskMatches();
                // Seal only after successful native world/SavedData save. A later seed refuses reuse.
                ProducerJournal.write(history,proof,false);
                CatanCraft.LOGGER.info("SEC01_PROCUREMENT35_SEED_PASS purchases=2 originalPrices=PASS tags=PASS noPortableItems=PASS saved=PASS process={} restartVerification=PENDING",process);
                close();h.succeed();
            }catch(Exception e){close();throw new RuntimeException(e);}});
        }
        void verify()throws Exception {
            assertSavedLedger();assertTotalCharge(proof.getCompound("nationBefore"));assertCounts();diskMatches();
            h.assertTrue(drops().isEmpty(),"Unexpected dropped items persisted across procurement restart");
            for(Purchase p:PURCHASES){
                Entity restored=level.getEntity(ids.get(p.key()));assertPurchased(restored,p,ids.get(p.key()));
                positionPlayer(p.player());guard(restored,p);assertSavedLedger();
            }
            ordinaryControl();assertSavedLedger();assertCounts();
            save();diskMatches();h.assertTrue(Arrays.equals(historyBytes,PaidConstructionRecovery.readBounded(history,8*1024*1024+40)),"Verify phase rewrote immutable seed proof");
            CompoundTag receipt=new CompoundTag();receipt.putInt("schema",1);receipt.putUUID("session",proof.getUUID("session"));
            receipt.putString("seedSHA256",TownHallRevisions.sha(historyBytes));receipt.putString("seedProcess",proof.getString("process"));receipt.putString("verifyProcess",process);
            receipt.putString("runtimeManifestSHA256",runtimeManifest);
            receipt.putString("result","PASS");ProducerJournal.write(verified,receipt,false);
            CatanCraft.LOGGER.info("SEC01_PROCUREMENT35_VERIFY_PASS restoredActualEntities=2 distinctJVM=PASS originalPricesOnce=PASS ownership=PASS bothHands=PASS noPackedItems=PASS ordinaryRetrieval=PASS seedProcess={} verifyProcess={}",proof.getString("process"),process);
            close();h.succeed();
        }
        void platform(BlockPos center)throws IOException {
            h.assertTrue(phase.equals("seed"),"Verification must never stage replacement world geometry");
            for(int x=center.getX()-22;x<=center.getX()+22;x++)for(int z=center.getZ()-22;z<=center.getZ()+22;z++){
                BlockPos floor=new BlockPos(x,center.getY()-1,z);
                h.assertTrue(level.getBlockState(floor).isAir()&&level.getBlockState(floor.above()).isAir(),"Unexpected preexisting fixture platform");
                level.setBlock(floor,Blocks.STONE.defaultBlockState(),FLAGS);
            }
        }
        void positionPlayer(BlockPos pos){player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);player.setYRot(0);player.setXRot(0);player.setShiftKeyDown(true);}
        ItemStack crowbar(){
            Item item=ForgeRegistries.ITEMS.getValue(new ResourceLocation("superbwarfare:crowbar"));
            h.assertTrue(item!=null&&item!=Items.AIR,"Actual SBW crowbar missing");return new ItemStack(item);
        }
        void guard(Entity e,Purchase purchase)throws Exception {
            for(InteractionHand hand:InteractionHand.values()){
                player.setItemInHand(InteractionHand.MAIN_HAND,hand==InteractionHand.MAIN_HAND?crowbar():ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.OFF_HAND,hand==InteractionHand.OFF_HAND?crowbar():ItemStack.EMPTY);
                denied(e,purchase,()->e.getClass().getMethod("onCrowbarInteract",ItemStack.class,Player.class,InteractionHand.class).invoke(e,crowbar(),player,hand),true);
                Class<?> vehicle=Class.forName("com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity");
                denied(e,purchase,()->Class.forName("com.atsuishio.superbwarfare.item.weapon.CrowbarItem")
                    .getMethod("crowbarInteract",vehicle,ItemStack.class,Player.class,InteractionHand.class).invoke(null,e,crowbar(),player,hand),true);
                // Published base interaction reads mainStack even with an OFF_HAND argument.
                player.setItemInHand(InteractionHand.MAIN_HAND,crowbar());player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
                denied(e,purchase,()->e.interact(player,hand),true);
            }
            // Offhand-only pickup/LAV is not a base retrieval route; still preserve entity/items.
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.setItemInHand(InteractionHand.OFF_HAND,crowbar());
            denied(e,purchase,()->e.interact(player,InteractionHand.OFF_HAND),false);
        }
        void denied(Entity e,Purchase purchase,Interaction action,boolean routed)throws Exception {
            ItemSnapshot before=items();CompoundTag money=nation.save().copy();Object result=action.run();
            h.assertTrue(!routed||result==InteractionResult.FAIL,"Actual SBW retrieval route did not refuse");
            assertPurchased(e,purchase,ids.getOrDefault(purchase.key(),e.getUUID()));
            h.assertTrue(before.equals(items())&&money.equals(nation.save()),"Purchased retrieval changed inventory/dropped items or nation accounting");
        }
        void ordinaryControl()throws Exception {
            positionPlayer(CONTROL);player.getInventory().clearContent();player.setItemInHand(InteractionHand.MAIN_HAND,crowbar());
            EntityType<?> type=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(PURCHASES.get(0).type()));
            h.assertTrue(type!=null,"Ordinary actual pickup type missing");Entity ordinary=type.create(level);
            h.assertTrue(ordinary!=null&&!ordinary.getPersistentData().getBoolean(NationProcurement.PURCHASED_TAG),"Ordinary control must be a genuinely unpurchased entity");
            ordinary.setPos(CONTROL.getX()+.5,CONTROL.getY()+.1,CONTROL.getZ()+7.5);
            h.assertTrue(level.addFreshEntity(ordinary),"Cannot stage ordinary retrieval control");ItemSnapshot before=items();int count=itemCount();
            Object result=ordinary.interact(player,InteractionHand.MAIN_HAND);
            h.assertTrue(result==InteractionResult.SUCCESS&&ordinary.isRemoved()&&!before.equals(items())&&itemCount()>count,"Ordinary actual main-hand pickup retrieval no longer removes entity and awards items");
        }
        void assertPurchased(Entity e,Purchase p,UUID id){
            h.assertTrue(e!=null&&!e.isRemoved()&&id.equals(e.getUUID())&&p.type().equals(String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(e.getType())))
                &&e.getPersistentData().getBoolean(NationProcurement.PURCHASED_TAG)
                &&nation.id().toString().equals(e.getPersistentData().getString(NationProcurement.NATION_TAG))
                &&AREA.contains(e.getX(),e.getY(),e.getZ()),"Purchased entity identity/type/ownership/residency changed: "+p.key());
        }
        Map<UUID,Entity> purchasedEntities(){
            Map<UUID,Entity> found=new HashMap<>();for(Entity e:level.getAllEntities())if(!e.isRemoved()&&e.getPersistentData().getBoolean(NationProcurement.PURCHASED_TAG))found.put(e.getUUID(),e);return found;
        }
        void assertCounts(){
            Map<UUID,Entity> found=purchasedEntities();h.assertTrue(found.keySet().equals(new HashSet<>(ids.values())),"Purchased entities were duplicated, missing, or replaced");
            for(Purchase p:PURCHASES){long count=0;for(Entity e:level.getAllEntities())if(!e.isRemoved()&&AREA.intersects(e.getBoundingBox())&&p.type().equals(String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(e.getType()))))count++;
                h.assertTrue(count==1,"Expected exactly one actual "+p.key()+" in the persisted fixture");assertPurchased(found.get(ids.get(p.key())),p,ids.get(p.key()));}
        }
        CompoundTag expectedAfter(CompoundTag before,Purchase p){NationData expected=NationData.load(before);expected.addTreasury(-p.money());p.materials().forEach((type,count)->expected.addResource(type,-count));return expected.save();}
        void assertCharge(CompoundTag before,Purchase p){h.assertTrue(expectedAfter(before,p).equals(nation.save()),"Actual command did not charge the exact original "+p.key()+" price once");}
        void assertTotalCharge(CompoundTag original){CompoundTag expected=original.copy();for(Purchase p:PURCHASES)expected=expectedAfter(expected,p);h.assertTrue(expected.equals(nation.save()),"Post-restart accounting is not exactly one original charge per real purchase");}
        void assertSavedLedger(){h.assertTrue(proof.getCompound("nationAfter").equals(nation.save())&&data.territory("a")!=null
            &&proof.getCompound("territoryAfter").equals(data.territory("a").save()),"Actual saved owner/progression/balance changed across restart/retrieval");}
        void save()throws IOException {data.setDirty();if(!server.saveEverything(true,true,true))throw new IOException("Native world/SavedData save failed");}
        void diskMatches()throws IOException {
            CatanSavedData disk=CatanSavedData.load(NbtIo.readCompressed(root.resolve("data/catancraft.dat").toFile()).getCompound("data"));
            h.assertTrue(disk.nation(nation.id())!=null&&disk.nation(nation.id()).save().equals(nation.save())
                &&disk.territory("a")!=null&&disk.territory("a").save().equals(data.territory("a").save())
                &&Objects.equals(disk.placedMapAsset(TOKEN),data.placedMapAsset(TOKEN)),"Actual SavedData disk accounting/ownership differs");
        }
        Map<UUID,CompoundTag> drops(){Map<UUID,CompoundTag> out=new HashMap<>();for(ItemEntity e:level.getEntitiesOfClass(ItemEntity.class,AREA))if(!e.isRemoved())out.put(e.getUUID(),e.getItem().save(new CompoundTag()));return Map.copyOf(out);}
        private record ItemSnapshot(ListTag inventory,Map<UUID,CompoundTag> drops) {}
        ItemSnapshot items(){return new ItemSnapshot(player.getInventory().save(new ListTag()),drops());}
        int itemCount(){int count=0;for(Tag t:player.getInventory().save(new ListTag()))count+=((CompoundTag)t).getByte("Count")&255;for(CompoundTag n:drops().values())count+=n.getByte("Count")&255;return count;}
        void close(){if(lease!=null)lease.close();if(player!=null)player.getInventory().clearContent();}
    }
}
