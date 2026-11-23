package com.quin.catancraft.world;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.quin.catancraft.data.*;
import com.quin.catancraft.economy.*;
import com.quin.catancraft.map.MapDefinitionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Actual copied authored terrain, normal founding commands and production wall
 * transactions. Explicitly funded developer fixture, never economy-pacing or
 * connected-client evidence. The second JVM recovers two genuinely partial sides. */
@GameTestHolder("catancraft_sector53_authored")
@PrefixGameTestTemplate(false)
public final class SectorAuthored53Tests {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final String READY="Base city paths and six house designs ready";
    private static final List<String> ORDER=List.of("a","b","e","f","d","c","h","g","m","i","j","n","p","l","k","o");
    private static final List<List<String>> ORDERS=List.of(
            List.of("north","east","south","west","north","east","south","west"),
            List.of("east","north","east","south","west","north","west","south"),
            List.of("west","south","west","north","east","south","east","north"),
            List.of("south","west","north","east","south","east","north","west"));
    private SectorAuthored53Tests() {}
    @GameTest(template="townhall_state_empty",batch="sector53_authored",timeoutTicks=220000)
    public static void all_city_authored_walls_and_two_city_process_recovery(GameTestHelper h) throws Exception {
        new Campaign(h).start();
    }
    private record Purchase(String city,String side,boolean interrupt,int orderIndex) {}
    @FunctionalInterface private interface Work { void run() throws Exception; }
    private static void require(boolean condition,String message) throws IOException {if(!condition)throw new IOException(message);}
    private static void ok(MapAssetService.Result result)throws IOException {require(result.success(),result.message());}
    private static String frozen(CompoundTag journal)throws IOException {
        CompoundTag stable=journal.copy();stable.remove("phase");stable.remove("naturalSupportExposure");
        var bytes=new ByteArrayOutputStream();NbtIo.writeCompressed(stable,bytes);return TownHallRevisions.sha(bytes.toByteArray());
    }

    /** Bounds count completed purchases. The final two APPLYING journals must coexist at shutdown. */
    private record SeedRange(int start,int end,String predecessor) {
        static SeedRange requested(String phase)throws IOException {
            String start=System.getProperty("catancraft.wall53RangeStart");
            String end=System.getProperty("catancraft.wall53RangeEnd");
            String predecessor=System.getProperty("catancraft.wall53Predecessor","");
            if(start==null&&end==null&&predecessor.isEmpty())return null;
            require(phase.equals("seed")&&start!=null&&end!=null&&start.matches("0|[1-9][0-9]{0,2}")
                    &&end.matches("0|[1-9][0-9]{0,2}"),"Invalid wall seed range");
            int first=Integer.parseInt(start),last=Integer.parseInt(end);
            require(first<=126&&last<=128&&last!=127,"Range splits the two-city partial recovery");
            require(predecessor.isEmpty()?first==0:predecessor.matches("[0-9a-f]{64}"),"Invalid checkpoint predecessor");
            require(last>first||(last==0&&first==0&&predecessor.isEmpty()),"Empty or reversed wall range");
            return new SeedRange(first,last,predecessor);
        }
        void addTo(JsonObject report){
            report.addProperty("rangeStart",start);
            report.addProperty("rangeEnd",end);
            report.addProperty("predecessorSHA256",predecessor);
        }
    }

    private static final class Campaign {
        final GameTestHelper h;final ServerLevel level;final MinecraftServer server;final CatanSavedData data;
        final Path root,evidence;final String source,phase;final List<Purchase> purchases=new ArrayList<>();
        final Map<String,FakePlayer> actors=new HashMap<>();final JsonArray events=new JsonArray();
        SectorChunkLease lease;String leased="";int cityIndex,purchaseIndex,waited;boolean submitted;
        Purchase current;Path operation;long moneyBefore;Map<ResourceType,Long> stockBefore;int priorLevel;
        JsonObject seed;CatanSavedData expected;int verifyIndex;String action="initialize";
        int hallFrom;long hallCash,hallCycle;Map<ResourceType,Long> hallStock;
        Map<String,String> priorWallHashes=Map.of();CompoundTag priorWallStates;
        final Map<String,String> hallHistories=new TreeMap<>();
        final SeedRange range;

        Campaign(GameTestHelper helper)throws Exception {
            h=helper;level=h.getLevel();server=level.getServer();data=CatanSavedData.get(server);
            require(server instanceof GameTestServer&&server.isSameThread(),"Isolated main-thread GameTestServer required");
            root=server.getWorldPath(LevelResource.ROOT).toRealPath();
            source=System.getProperty("catancraft.progression53Source","");phase=System.getProperty("catancraft.progression53Phase","");
            require(source.matches("[0-9a-f]{40}")&&Set.of("seed","verify").contains(phase),"Exact source and seed|verify required");
            range=SeedRange.requested(phase);
            Path inputPath=Path.of("config/catancraft/progression53-input.json");
            require(Files.isRegularFile(inputPath,LinkOption.NOFOLLOW_LINKS)&&Files.size(inputPath)<65536,"Bounded authored input required");
            JsonObject input=JsonParser.parseString(Files.readString(inputPath)).getAsJsonObject();
            require(input.get("schema").getAsInt()==1&&input.get("purpose").getAsString().equals("verified-clean-r7-progression53")
                    &&input.get("sourceCommit").getAsString().equals(source),"Different authored input/source");
            require(root.getFileName().toString().equals(input.get("worldFolder").getAsString())
                    &&server.getWorldData().getLevelName().equals(input.get("levelName").getAsString())
                    &&level.getSeed()==input.get("seed").getAsLong(),"Authored world identity changed");
            require(ShopSites51.MAP_SHA256.equals(input.get("mapSha256").getAsString())
                    &&ShopSites51.MAP_SHA256.equals(TownHallRevisions.sha(Files.readAllBytes(MapDefinitionManager.path()))),"Reviewed map differs");
            require(FreshDemoAuthorization.authorize(server).equals(root),"Bound fresh identity required");
            require(!Files.exists(root.resolve(R5CivicAutomaticInstaller.MARKER_NAME)),"Fresh run must not depend on historical opt-in");
            evidence=root.resolve("data/sector53-authored");require(!Files.isSymbolicLink(evidence),"Evidence symlink refused");
            for(String start:List.of("a","d","m","p")) {
                UUID id=UUID.nameUUIDFromBytes(("CatanCraft-sector53-command-"+start).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                FakePlayer actor=new FakePlayer(level,new GameProfile(id,"Walls53"+start.toUpperCase(Locale.ROOT)));
                actor.setPos(0,200,0);actors.put(start,actor);
            }
        }
        void start()throws Exception {
            if(phase.equals("verify")){beginVerify();return;}
            if(range!=null&&!range.predecessor().isEmpty()){resumeRange();return;}
            require(!Files.exists(evidence)&&data.nations().isEmpty()&&data.territories().size()==16
                    &&data.territories().stream().allMatch(t->t.ownerNationId()==null&&t.cityLevel()==1&&t.buildings().isEmpty()),
                    "Seed requires pristine unowned authored world; never reset existing progress");
            Files.createDirectory(evidence);
            for(var entry:actors.entrySet()) {
                command(entry.getValue(),"nation create Sector53-"+entry.getKey());
                NationData nation=data.nationForPlayer(entry.getValue().getUUID());require(nation!=null,"Nation command failed");
                // Deliberate, recorded developer funding. No claim of normal affordability.
                nation.addTreasury(1_000_000);for(ResourceType type:ResourceType.values())nation.addResource(type,100_000);
                JsonObject grant=new JsonObject();grant.addProperty("kind","EXPLICIT_DEVELOPER_FIXTURE_GRANT");
                grant.addProperty("nation",nation.id().toString());grant.addProperty("money",1_000_000);grant.addProperty("eachResource",100_000);events.add(grant);
            }
            data.setDirty();schedule(this::activateNext);
        }
        String startFor(String city){int n=ORDER.indexOf(city);return List.of("a","d","m","p").get(n/4);}
        FakePlayer actor(String city){return actors.get(startFor(city));}
        NationData nation(String city){return data.nationForPlayer(actor(city).getUUID());}
        void command(FakePlayer actor,String command)throws Exception {
            require(server.getCommands().getDispatcher().execute(command,actor.createCommandSourceStack().withPermission(2).withSuppressedOutput())==1,
                    "Normal command failed: "+command);
        }
        void acquire(String city)throws IOException {
            if(city.equals(leased))return;
            closeLease();var center=MapDefinitionManager.territory(city).cityCenter();
            // Includes sparse North extension and support/shell cells, without writes.
            lease=SectorChunkLease.acquire(level,new AABB(center.x()-125,60,center.z()-125,center.x()+126,150,center.z()+126));
            leased=city;waited=0;
        }
        boolean readyLease()throws IOException {
            lease.keepAlive();require(++waited<8000,"Authored chunks/entities did not become ready: "+leased);
            return lease.ready();
        }
        void activateNext()throws Exception {
            if(cityIndex==ORDER.size()){closeLease();makePurchases();schedule(this::purchaseNext);return;}
            String city=ORDER.get(cityIndex);action="normal founding "+city;acquire(city);
            if(!readyLease()){schedule(this::activateNext);return;}
            if(!submitted) {
                var def=MapDefinitionManager.territory(city);require(data.territory(city).ownerNationId()==null,"City inherited an owner");
                String cmd=def.startsWithSettlement()?"nation start "+def.startSlot():"nation claim "+city;
                command(actor(city),cmd);require(nation(city).id().equals(data.territory(city).ownerNationId()),"Founding owner mismatch");
                submitted=true;waited=0;
            }
            String status=CityBasePreparation.status(server,city);require(!status.contains("blocked:"),status);
            if(!READY.equals(status)){require(++waited<10000,"Automatic base did not complete: "+city+" "+status);schedule(this::activateNext);return;}
            JsonObject row=new JsonObject();row.addProperty("kind","normal_founding_and_automatic_base");row.addProperty("city",city);
            row.addProperty("route",MapDefinitionManager.territory(city).startsWithSettlement()?"nation_start":"nation_claim");
            row.addProperty("publicJournalSHA256",TownHallRevisions.sha(Files.readAllBytes(root.resolve("data/catancraft_r5_public_"+city+"_v1.dat"))));events.add(row);
            cityIndex++;submitted=false;closeLease();schedule(this::activateNext);
        }
        void makePurchases() {
            List<Purchase> delayed=new ArrayList<>();
            for(String city:ORDER) {
                var order=ORDERS.get((city.charAt(0)-'a')%4);
                for(int i=0;i<order.size();i++) {
                    boolean pause=(city.equals("d")||city.equals("m"))&&i==7;
                    Purchase p=new Purchase(city,order.get(i),pause,i);if(pause)delayed.add(p);else purchases.add(p);
                }
            }
            purchases.addAll(delayed);
        }
        void purchaseNext()throws Exception {
            if(purchaseIndex==purchases.size()){finishSeed();return;}
            if(range!=null&&purchaseIndex==range.end()){finishRange();return;}
            current=purchases.get(purchaseIndex);action="wall "+current.city()+" "+current.side();acquire(current.city());
            if(!readyLease()){schedule(this::purchaseNext);return;}
            int requiredHall=current.orderIndex()<2?1:current.orderIndex()<4?4:5;
            if(data.territory(current.city()).cityLevel()<requiredHall){beginHallUpgrade();return;}
            require(data.territory(current.city()).cityLevel()==requiredHall,"Unexpected Hall level before wall purchase");
            var side=SectorContracts.Direction.parse(current.side());
            priorLevel=SectorService.savedState(data,current.city(),side).level();require(priorLevel<2,"Unexpected wall level before purchase");
            CompoundTag before=data.save(new CompoundTag());
            require(!SectorService.quote(server,UUID.randomUUID(),current.city(),current.side()).success(),"Wrong owner obtained wall quote");
            require(before.equals(data.save(new CompoundTag())),"Refused wrong-owner quote changed ledger");
            ok(SectorService.quote(server,actor(current.city()).getUUID(),current.city(),current.side()));
            require(before.equals(data.save(new CompoundTag())),"Quote changed ledger");
            var payer=nation(current.city());moneyBefore=payer.treasury();stockBefore=Map.copyOf(payer.stockpileView());
            ok(SectorService.operation(server,actor(current.city()).getUUID(),current.city(),current.side(),SectorTransaction.Action.APPLY));
            var cost=SectorPriceCatalog.cost(side,priorLevel);
            require(payer.treasury()==moneyBefore-cost.money(),"Exact same-tick money reservation differs");
            for(ResourceType type:ResourceType.values())require(payer.resource(type)==stockBefore.getOrDefault(type,0L)-cost.resources().getOrDefault(type,0L),"Exact same-tick material debit differs: "+type);
            String pointer=data.placedMapAsset("sector_v1:"+current.city()+":"+current.side()+":operation");
            require(pointer!=null&&pointer.matches("catancraft_sector_"+current.city()+"_"+current.side()+"_[0-9a-f-]{36}_v1\\.dat"),"Missing exact operation pointer");
            operation=root.resolve("data").resolve(pointer);
            CompoundTag journal=ProducerJournal.read(operation);require(journal.getString("phase").equals("APPLYING"),"Purchase did not persist APPLYING");
            require(journal.getCompound("identity").getString("approval").equals(SectorService.PRODUCTION),"Fixture substituted production wall provider");
            CompoundTag after=data.save(new CompoundTag());
            require(!SectorService.operation(server,actor(current.city()).getUUID(),current.city(),current.side(),SectorTransaction.Action.APPLY).success(),"Concurrent duplicate accepted");
            require(after.equals(data.save(new CompoundTag())),"Concurrent duplicate repeated payment");
            waited=0;schedule(this::waitPurchase);
        }
        Map<String,String> completedWallHashes(String city)throws IOException {
            Map<String,String> hashes=new TreeMap<>();
            try(var paths=Files.newDirectoryStream(root.resolve("data"),"catancraft_sector_"+city+"_*_v1.dat")) {
                for(Path path:paths) {
                    require(ProducerJournal.read(path).getString("phase").equals("COMMITTED"),"Hall milestone found unfinished wall history");
                    hashes.put(path.getFileName().toString(),TownHallRevisions.sha(Files.readAllBytes(path)));
                }
            }
            return hashes;
        }
        CompoundTag wallStates(String city)throws IOException {
            CompoundTag result=new CompoundTag();
            for(var side:SectorContracts.Direction.values()) {
                var state=SectorService.savedState(data,city,side);CompoundTag row=new CompoundTag();
                row.putInt("level",state.level());row.putString("receipt",state.receipt());row.putString("revision",state.revision());result.put(side.name(),row);
            }
            return result;
        }
        void beginHallUpgrade()throws Exception {
            String city=current.city();action="paid Hall milestone "+city;
            require(!SectorService.hasActiveOperation(server),"Hall milestone raced an active wall");
            String status=PaidRecoveryService.status(server,city);require(!status.contains("blocked:"),status);
            if(PaidRecoveryService.isRunning(server)||CityBasePreparation.isRunning(server)){schedule(this::purchaseNext);return;}
            hallFrom=data.territory(city).cityLevel();var cost=EconomyCatalog.cityUpgradeCost(hallFrom);
            require(cost!=null&&cost.canAfford(nation(city)),"Recorded developer fixture cannot afford Hall milestone");
            priorWallHashes=completedWallHashes(city);require(priorWallHashes.size()>=2,"Hall milestone lacks prior paid walls");
            priorWallStates=wallStates(city);hallCash=nation(city).treasury();hallStock=Map.copyOf(nation(city).stockpileView());hallCycle=data.lastScheduledEconomyCycle();
            command(actor(city),"nation upgrade city "+city);waited=0;schedule(this::waitHallUpgrade);
        }
        void waitHallUpgrade()throws Exception {
            String city=current.city();lease.keepAlive();require(++waited<12000,"Hall milestone failed to finish in "+city+": "+PaidRecoveryService.status(server,city));
            require(!PaidRecoveryService.status(server,city).contains("blocked:"),PaidRecoveryService.status(server,city));
            if(data.territory(city).cityLevel()==hallFrom||PaidRecoveryService.isRunning(server)){schedule(this::waitHallUpgrade);return;}
            require(data.territory(city).cityLevel()==hallFrom+1,"Hall milestone advanced unexpected tiers");
            var cost=EconomyCatalog.cityUpgradeCost(hallFrom);var payer=nation(city);
            require(hallCycle==data.lastScheduledEconomyCycle(),"Economy advanced during Hall milestone; debit proof is ambiguous");
            require(payer.treasury()==hallCash-cost.money(),"Hall milestone cash debit differs");
            for(ResourceType type:ResourceType.values())require(payer.resource(type)==hallStock.getOrDefault(type,0L)-cost.resources().getOrDefault(type,0L),"Hall milestone material debit differs: "+type);
            require(priorWallStates.equals(wallStates(city))&&priorWallHashes.equals(completedWallHashes(city)),"Hall upgrade changed earlier wall receipts/history");
            CompoundTag before=data.save(new CompoundTag());
            for(var side:SectorContracts.Direction.values())if(SectorService.savedState(data,city,side).level()>0)
                ok(SectorService.operation(server,actor(city).getUUID(),city,side.name().toLowerCase(Locale.ROOT),SectorTransaction.Action.STATUS));
            require(before.equals(data.save(new CompoundTag())),"Wall assembly status after Hall upgrade changed ledger");
            require(priorWallHashes.equals(completedWallHashes(city)),"Wall status rewrote completed history");
            try(var paths=Files.newDirectoryStream(root.resolve("data"))) {
                for(Path path:paths) {
                    String name=path.getFileName().toString();
                    if(name.startsWith("catancraft_basic_purchase_"+city+"_th")||name.startsWith("catancraft_th_prestige_"+city+"_")) {
                        String hash=TownHallRevisions.sha(Files.readAllBytes(path));String old=hallHistories.putIfAbsent(name,hash);
                        require(old==null||old.equals(hash),"Earlier Hall history changed");
                    }
                }
            }
            for(var entry:hallHistories.entrySet())require(TownHallRevisions.sha(Files.readAllBytes(root.resolve("data").resolve(entry.getKey()))).equals(entry.getValue()),"Earlier Hall history removed or changed");
            JsonObject row=new JsonObject();row.addProperty("kind","paid_hall_milestone");row.addProperty("city",city);
            row.addProperty("from",hallFrom);row.addProperty("to",hallFrom+1);row.addProperty("exactDebit",true);
            row.addProperty("earlierWallAssemblyVerified",true);row.addProperty("unchangedWallJournals",priorWallHashes.size());events.add(row);
            writeProgress();schedule(this::purchaseNext);
        }
        void waitPurchase()throws Exception {
            lease.keepAlive();require(++waited<12000,"Wall runner did not complete: "+action);
            var side=SectorContracts.Direction.parse(current.side());
            if(current.interrupt()) {
                CompoundTag journal=ProducerJournal.read(operation);int changed=0,total=journal.getList("actualBefore",Tag.TAG_COMPOUND).size();
                for(Tag item:journal.getList("actualBefore",Tag.TAG_COMPOUND)) {var cell=(CompoundTag)item;
                    if(NbtUtils.writeBlockState(level.getBlockState(BlockPos.of(cell.getLong("pos")))).equals(cell.getCompound("after")))changed++;
                }
                if(changed>0) {
                    require(changed<total&&journal.getString("phase").equals("APPLYING"),"Interruption must retain a genuinely partial side");
                    SectorService.stopped(server);require(!SectorService.hasActiveOperation(server),"Stopped fixture retained active runner");
                    require(SectorService.savedState(data,current.city(),side).level()==priorLevel,"Partial side advertised completed level");
                    require(server.saveEverything(true,true,true),"Partial world save failed");record(journal,changed,true);nextPurchase();return;
                }
            }else if(!SectorService.hasActiveOperation(server)) {
                CompoundTag journal=ProducerJournal.read(operation);
                require(journal.getString("phase").equals("COMMITTED")&&SectorService.savedState(data,current.city(),side).level()==priorLevel+1,
                        "Runner stopped without committing: "+action+" "+journal.getString("phase"));
                ok(SectorService.operation(server,actor(current.city()).getUUID(),current.city(),current.side(),SectorTransaction.Action.STATUS));
                record(journal,journal.getList("actualBefore",Tag.TAG_COMPOUND).size(),false);nextPurchase();return;
            }
            require(SectorService.hasActiveOperation(server),"Partial wall runner failed before interruption");schedule(this::waitPurchase);
        }
        void record(CompoundTag journal,int written,boolean partial)throws Exception {
            var price=SectorTransaction.historicalCost(journal.getCompound("cost"));var expectedPrice=SectorPriceCatalog.cost(SectorContracts.Direction.parse(current.side()),priorLevel);
            require(price.money()==expectedPrice.money()&&price.resources().equals(expectedPrice.resources()),"Journal changed original agreed price");
            var identity=journal.getCompound("identity");
            require(journal.getUUID("nation").equals(nation(current.city()).id())
                    &&identity.getUUID("owner").equals(nation(current.city()).id())
                    &&identity.getString("city").equals(current.city())&&identity.getString("sector").equals(current.side())
                    &&identity.getCompound("cityGroup").getInt("schema")== (current.city().equals("a")?1:2),
                    "Cross-city journal/owner/schema isolation failed");
            JsonObject row=new JsonObject();row.addProperty("kind","wall_purchase");row.addProperty("city",current.city());row.addProperty("side",current.side());row.addProperty("townHallLevel",data.territory(current.city()).cityLevel());
            row.addProperty("from",priorLevel);row.addProperty("to",priorLevel+1);row.addProperty("operation",operation.getFileName().toString());
            row.addProperty("id",journal.getUUID("id").toString());row.addProperty("money",price.money());row.addProperty("partialAtSeed",partial);
            row.addProperty("observedWritten",written);row.addProperty("exactChanges",journal.getList("actualBefore",Tag.TAG_COMPOUND).size());
            row.addProperty("journalSHA256",TownHallRevisions.sha(Files.readAllBytes(operation)));row.addProperty("immutableHistorySHA256",frozen(journal));
            row.addProperty("groupSchema",journal.getCompound("identity").getCompound("cityGroup").getInt("schema"));events.add(row);
            writeProgress();
        }
        void nextPurchase(){purchaseIndex++;operation=null;closeLease();schedule(this::purchaseNext);}
        void writeProgress()throws IOException {Files.writeString(evidence.resolve("progress.json"),JSON.toJson(events)+"\n");}
        Path checkpoint(int index){return evidence.resolve("checkpoint-"+index+".json");}
        Path checkpointLedger(int index){return evidence.resolve("checkpoint-"+index+"-ledger.nbt");}

        void finishRange()throws Exception {
            require(purchaseIndex<=126&&!SectorService.hasActiveOperation(server)&&!PaidRecoveryService.isRunning(server),
                    "Refusing to checkpoint an unfinished transaction");
            require(server.saveEverything(true,true,true),"Wall range save failed");
            Path ledger=checkpointLedger(purchaseIndex);
            require(!Files.exists(ledger,LinkOption.NOFOLLOW_LINKS),"Refusing to replace a wall range ledger");
            CompoundTag saved=data.save(new CompoundTag());
            var disk=CatanSavedData.load(NbtIo.readCompressed(root.resolve("data/catancraft.dat").toFile()).getCompound("data"));
            require(disk.save(new CompoundTag()).equals(saved),"Wall range SavedData differs on disk");
            NbtIo.writeCompressed(saved,ledger.toFile());
            JsonObject report=base();
            range.addTo(report);
            report.addProperty("result","WALL_SEED_SEGMENT_PASS");
            report.addProperty("ledgerSHA256",TownHallRevisions.sha(Files.readAllBytes(ledger)));
            report.add("events",events);
            report.add("hallHistorySHA256",JSON.toJsonTree(hallHistories));
            writeProgress();
            Files.writeString(checkpoint(purchaseIndex),JSON.toJson(report)+"\n",StandardOpenOption.CREATE_NEW);
            closeLease();
            h.succeed();
        }

        void resumeRange()throws Exception {
            action="resume wall checkpoint "+range.start();
            require(!Files.exists(evidence.resolve("seed.json"))&&!Files.exists(evidence.resolve("verify.json"))
                    &&!Files.exists(evidence.resolve("failure-seed.json")),"World already finished or failed");
            Path path=checkpoint(range.start());
            require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)&&Files.size(path)<2_000_000,
                    "Missing or oversized checkpoint");
            byte[] bytes=Files.readAllBytes(path);
            require(TownHallRevisions.sha(bytes).equals(range.predecessor()),"Wall checkpoint hash mismatch");
            JsonObject prior=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            require(prior.get("schema").getAsInt()==1&&source.equals(prior.get("source").getAsString())
                    &&FreshDemoAuthorization.worldId(server,root).equals(prior.get("worldId").getAsString())
                    &&"seed".equals(prior.get("phase").getAsString())&&"WALL_SEED_SEGMENT_PASS".equals(prior.get("result").getAsString())
                    &&prior.get("rangeEnd").getAsInt()==range.start(),"Different wall checkpoint identity");
            events.addAll(prior.getAsJsonArray("events"));
            require(events.equals(JsonParser.parseString(Files.readString(evidence.resolve("progress.json")))),
                    "Wall progress has moved past the sealed checkpoint");
            Path ledger=checkpointLedger(range.start());
            require(Files.isRegularFile(ledger,LinkOption.NOFOLLOW_LINKS)&&TownHallRevisions.sha(Files.readAllBytes(ledger))
                    .equals(prior.get("ledgerSHA256").getAsString()),"Wall checkpoint ledger changed");
            expected=CatanSavedData.load(NbtIo.readCompressed(ledger.toFile()));
            expectedEconomy();
            for(String city:ORDER)require(expected.territory(city).save().equals(data.territory(city).save()),
                    "Territory changed across wall checkpoint: "+city);
            makePurchases();
            int founded=0;
            for(JsonElement item:events){
                JsonObject row=item.getAsJsonObject();
                String kind=row.get("kind").getAsString();
                if(kind.equals("normal_founding_and_automatic_base")){
                    require(founded<ORDER.size()&&ORDER.get(founded).equals(row.get("city").getAsString()),
                            "Checkpoint founding history differs");
                    founded++;
                }else if(kind.equals("wall_purchase")){
                    require(purchaseIndex<range.start(),"Too many prior purchases at checkpoint");
                    Purchase earlier=purchases.get(purchaseIndex++);
                    require(earlier.city().equals(row.get("city").getAsString())&&earlier.side().equals(row.get("side").getAsString())
                            &&!row.get("partialAtSeed").getAsBoolean(),"Prior wall purchase order differs");
                    String operationName=row.get("operation").getAsString();
                    require(operationName.matches("catancraft_sector_"+earlier.city()+"_"+earlier.side()+"_[0-9a-f-]{36}_v1\\.dat"),
                            "Invalid wall history path");
                    require(row.get("journalSHA256").getAsString().equals(TownHallRevisions.sha(
                            Files.readAllBytes(root.resolve("data").resolve(operationName)))),"Prior wall history changed: "+operationName);
                }
            }
            require(founded==ORDER.size()&&purchaseIndex==range.start(),"Incomplete wall checkpoint prefix");
            verifyHallHistories(prior);
            for(var row:prior.getAsJsonObject("hallHistorySHA256").entrySet())
                hallHistories.put(row.getKey(),row.getValue().getAsString());
            waited=0;
            schedule(this::waitForResumedBase);
        }

        void waitForResumedBase()throws Exception {
            action="waiting for base verification after wall checkpoint";
            expectedEconomy();
            require(++waited<12000,"Base verification did not finish after wall checkpoint");
            boolean ready=!PaidRecoveryService.isRunning(server)&&!CityBasePreparation.isRunning(server)
                    &&!SectorService.hasActiveOperation(server);
            for(String city:ORDER){
                String status=CityBasePreparation.status(server,city);
                require(!status.contains("blocked:"),city+": "+status);
                String paid=PaidRecoveryService.status(server,city);
                require(!paid.contains("blocked:"),city+": "+paid);
                ready&=READY.equals(status);
            }
            if(!ready){schedule(this::waitForResumedBase);return;}
            waited=0;
            schedule(this::purchaseNext);
        }

        void finishSeed()throws Exception {
            require(purchaseIndex==128,"Incomplete 128-operation wall scope");
            int pending=0;for(String city:ORDER)for(var side:SectorContracts.Direction.values()) {
                var state=SectorService.savedState(data,city,side);boolean interrupted=(city.equals("d")||city.equals("m"))&&side==SectorContracts.Direction.WEST;
                require(state.level()==(interrupted?1:2),"Wrong seed wall level: "+city+" "+side);if(interrupted)pending++;
            }
            verifyMilestoneCoverage(events);
            for(String city:ORDER)require(data.territory(city).cityLevel()==5,"Missing final Hall tier in "+city);
            require(pending==2&&server.saveEverything(true,true,true),"Seed histories not saved");
            Path ledger=evidence.resolve("seed-ledger.nbt");NbtIo.writeCompressed(data.save(new CompoundTag()),ledger.toFile());
            JsonObject report=base();report.addProperty("result","SEED_PASS_TWO_PARTIAL_OPERATIONS_REQUIRE_PROCESS_RESTART");
            if(range!=null)range.addTo(report);
            report.addProperty("cities",16);report.addProperty("sectorPlacements",64);report.addProperty("purchases",128);
            report.addProperty("committedAtSeed",126);report.addProperty("partialAtSeed",2);report.addProperty("seedLedgerSHA256",TownHallRevisions.sha(Files.readAllBytes(ledger)));report.add("events",events);report.add("hallHistorySHA256",JSON.toJsonTree(hallHistories));report.addProperty("paidHallUpgrades",64);
            Files.writeString(evidence.resolve("seed.json"),JSON.toJson(report)+"\n",StandardOpenOption.CREATE_NEW);
            closeLease();h.succeed();
        }
        void beginVerify()throws Exception {
            Path file=evidence.resolve("seed.json");require(Files.isRegularFile(file)&&Files.size(file)<2_000_000,"Missing bounded previous-process evidence");
            seed=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            require(seed.get("source").getAsString().equals(source)&&seed.get("worldId").getAsString().equals(FreshDemoAuthorization.worldId(server,root))
                    &&seed.get("result").getAsString().equals("SEED_PASS_TWO_PARTIAL_OPERATIONS_REQUIRE_PROCESS_RESTART"),"Foreign seed proof");
            Path ledger=evidence.resolve("seed-ledger.nbt");require(TownHallRevisions.sha(Files.readAllBytes(ledger)).equals(seed.get("seedLedgerSHA256").getAsString()),"Seed ledger proof changed");
            verifyMilestoneCoverage(seed.getAsJsonArray("events"));verifyHallHistories(seed);
            expected=CatanSavedData.load(NbtIo.readCompressed(ledger.toFile()));waited=0;schedule(this::waitRecovery);
        }
        void verifyHallHistories(JsonObject history)throws IOException {
            for(var entry:history.getAsJsonObject("hallHistorySHA256").entrySet()) {
                require(entry.getKey().matches("catancraft_(basic_purchase|th_prestige)_[a-p]_[a-z0-9_.-]+"),"Unsafe Hall evidence path");
                require(TownHallRevisions.sha(Files.readAllBytes(root.resolve("data").resolve(entry.getKey()))).equals(entry.getValue().getAsString()),"Hall history changed across restart");
            }
        }
        void expectedEconomy()throws IOException {
            require(expected.hasEconomySchedule()&&data.hasEconomySchedule(),"Missing actual economy checkpoint");
            long before=expected.lastScheduledEconomyCycle(),actual=data.lastScheduledEconomyCycle();
            // Continuous polling can prove one observed boundary. A larger jump
            // cannot establish how many postings occurred; production intentionally
            // posts once on a jump and never creates catch-up income.
            require(actual>=before&&actual-before<=1,"Ambiguous economy checkpoint jump during restart verification");
            if(actual>before)EconomyEngine.scheduledCycle(expected,Math.multiplyExact(actual,EconomyBalance.CYCLE_TICKS));
            for(var nation:expected.nations())require(data.nation(nation.id())!=null&&data.nation(nation.id()).save().equals(nation.save()),
                    "Restart duplicated payment or changed resources beyond scheduled economy: "+nation.id());
        }
        void waitRecovery()throws Exception {
            action="automatic two-city restart recovery";expectedEconomy();
            require(++waited<24000,"Automatic startup did not finish both pending cities");
            boolean done=true;for(String city:List.of("d","m"))done&=SectorService.savedState(data,city,SectorContracts.Direction.WEST).level()==2;
            if(!done||SectorService.hasActiveOperation(server)){schedule(this::waitRecovery);return;}
            verifyIndex=0;closeLease();schedule(this::verifyCity);
        }
        void verifyCity()throws Exception {
            expectedEconomy();
            if(verifyIndex==ORDER.size()){finishVerify();return;}
            String city=ORDER.get(verifyIndex);action="restart assembly "+city;acquire(city);
            if(!readyLease()){schedule(this::verifyCity);return;}
            // Startup base verification can still be finishing; it must preserve paid walls.
            String base=CityBasePreparation.status(server,city);require(!base.contains("blocked:"),base);
            if(!READY.equals(base)){require(++waited<12000,"Restart base verification stuck "+city);schedule(this::verifyCity);return;}
            require(data.territory(city).cityLevel()==5,"Restart lost paid Hall tier in "+city);
            for(var side:SectorContracts.Direction.values())require(SectorService.savedState(data,city,side).level()==2,"Restart lost completed wall: "+city+" "+side);
            CompoundTag before=data.save(new CompoundTag());
            ok(SectorService.operation(server,actor(city).getUUID(),city,"north",SectorTransaction.Action.STATUS));
            require(before.equals(data.save(new CompoundTag())),"Read-only assembly status changed ledger");
            verifyIndex++;closeLease();schedule(this::verifyCity);
        }
        void finishVerify()throws Exception {
            int count=0,partial=0;Set<String> ids=new HashSet<>();
            for(JsonElement value:seed.getAsJsonArray("events")) {
                JsonObject row=value.getAsJsonObject();if(!row.get("kind").getAsString().equals("wall_purchase"))continue;
                String name=row.get("operation").getAsString();require(name.matches("catancraft_sector_[a-p]_(north|east|south|west)_[0-9a-f-]{36}_v1\\.dat"),"Unsafe saved operation path");
                Path file=root.resolve("data").resolve(name);CompoundTag journal=ProducerJournal.read(file);
                require(journal.getString("phase").equals("COMMITTED")&&journal.getUUID("id").toString().equals(row.get("id").getAsString())
                        &&frozen(journal).equals(row.get("immutableHistorySHA256").getAsString())
                        &&journal.getCompound("identity").getString("city").equals(row.get("city").getAsString())
                        &&journal.getCompound("identity").getString("sector").equals(row.get("side").getAsString()),"Restart changed original history or failed completion: "+name);
                require(ids.add(journal.getUUID("id").toString()),"Duplicate operation identity");
                if(row.get("partialAtSeed").getAsBoolean())partial++;
                else require(TownHallRevisions.sha(Files.readAllBytes(file)).equals(row.get("journalSHA256").getAsString()),"Committed history rewritten: "+name);
                count++;
            }
            require(count==128&&partial==2,"Missing full journal/restart coverage");expectedEconomy();
            require(server.saveEverything(true,true,true),"Restart final save failed");
            CatanSavedData disk=CatanSavedData.load(NbtIo.readCompressed(root.resolve("data/catancraft.dat").toFile()).getCompound("data"));
            require(disk.save(new CompoundTag()).equals(data.save(new CompoundTag())),"Final SavedData disk/memory differs");
            verifyHallHistories(seed);
            JsonObject report=base();report.addProperty("result","PASS");report.addProperty("cities",16);report.addProperty("sectorPlacements",64);
            report.addProperty("committedPurchases",128);report.addProperty("partialOperationsRecovered",2);report.addProperty("unchangedCommittedJournals",126);
            report.addProperty("paidHallUpgrades",64);report.addProperty("allCityHall4And5WallPurchasesVerified",true);
            report.addProperty("earlierWallsPreservedAcrossHallUpgrades",true);
            report.addProperty("normalEconomyPacingEvidence",false);report.addProperty("connectedClientAcceptance",false);
            report.addProperty("seedEvidenceSHA256",TownHallRevisions.sha(Files.readAllBytes(evidence.resolve("seed.json"))));
            Files.writeString(evidence.resolve("verify.json"),JSON.toJson(report)+"\n",StandardOpenOption.CREATE_NEW);closeLease();h.succeed();
        }
        void verifyMilestoneCoverage(JsonArray rows)throws IOException {
            Map<String,Set<Integer>> halls=new HashMap<>();Map<String,Map<Integer,Integer>> wallTiers=new HashMap<>();int upgrades=0;
            for(JsonElement item:rows) {
                JsonObject row=item.getAsJsonObject();String kind=row.get("kind").getAsString();
                if(kind.equals("paid_hall_milestone")) {
                    String city=row.get("city").getAsString();int to=row.get("to").getAsInt();
                    require(ORDER.contains(city)&&to>=2&&to<=5&&row.get("from").getAsInt()==to-1
                            &&row.get("exactDebit").getAsBoolean()&&row.get("earlierWallAssemblyVerified").getAsBoolean()
                            &&halls.computeIfAbsent(city,k->new HashSet<>()).add(to),"Missing/duplicate Hall milestone");upgrades++;
                }else if(kind.equals("wall_purchase"))wallTiers.computeIfAbsent(row.get("city").getAsString(),k->new HashMap<>()).merge(row.get("townHallLevel").getAsInt(),1,Integer::sum);
            }
            require(upgrades==64,"Missing 64 registered paid Hall upgrades");
            for(String city:ORDER)require(Set.of(2,3,4,5).equals(halls.get(city))&&Map.of(1,2,4,2,5,4).equals(wallTiers.get(city)),"Missing wall-before/after-Hall coverage in "+city);
        }
        JsonObject base()throws IOException {JsonObject report=new JsonObject();report.addProperty("schema",1);report.addProperty("source",source);
            report.addProperty("worldId",FreshDemoAuthorization.worldId(server,root));report.addProperty("phase",phase);report.addProperty("fundedDeveloperFixture",true);
            report.addProperty("manualTargetBlockStamping",false);report.addProperty("realAuthoredCoordinates",true);return report;}
        void closeLease(){if(lease!=null)lease.close();lease=null;leased="";}
        void schedule(Work work){h.runAfterDelay(1,()->{try{work.run();}catch(Exception ex){closeLease();
            try{JsonObject failure=base();failure.addProperty("result","FAIL");failure.addProperty("action",action);failure.addProperty("message",ex.toString());
                Files.createDirectories(evidence);Files.writeString(evidence.resolve("failure-"+phase+".json"),JSON.toJson(failure)+"\n");}catch(Exception suppressed){ex.addSuppressed(suppressed);}
            throw new RuntimeException("Authored wall qualification failed at "+action,ex);}});}
    }
}
