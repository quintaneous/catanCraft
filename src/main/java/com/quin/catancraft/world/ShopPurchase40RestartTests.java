package com.quin.catancraft.world;

import com.mojang.authlib.GameProfile;
import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.CatanSavedData;
import com.quin.catancraft.data.NationData;
import com.quin.catancraft.data.ResourceType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One synthetic transaction scenario in two distinct JVMs and one retained native
 * world. Both actors use actual PlayerList.saveAll/playerdata and CatanSavedData.
 * A stops after durable payment. B stops after durable delivery, then consumes its
 * item while retaining its native player receipt. Verify loads those original
 * files; it never recreates nations, requests, player inventory or proof state.
 * This is not a connected-player, physical-counter or gunpack acceptance test. */
@GameTestHolder("catancraft_shop40_restart") @PrefixGameTestTemplate(false)
public final class ShopPurchase40RestartTests {
    private static final String NS = "catancraft_shop40_restart";
    private static final String FIXTURE = "synthetic-native-shop40-restart-v1";
    private static final String DIM = "minecraft:overworld";
    private static final long FUNDS = 10_000, STOCK = 40;
    private static final Actor A = new Actor(UUID.fromString("40000000-0000-0000-0000-0000000000a1"), "CCShop40A",
            UUID.fromString("40000000-0000-0000-0000-0000000000a2"), Items.IRON_NUGGET,
            new ShopPurchase40.Price(275, Map.of(ResourceType.STEEL, 3L, ResourceType.MECHANICAL_PARTS, 2L)));
    private static final Actor B = new Actor(UUID.fromString("40000000-0000-0000-0000-0000000000b1"), "CCShop40B",
            UUID.fromString("40000000-0000-0000-0000-0000000000b2"), Items.GOLD_NUGGET,
            new ShopPurchase40.Price(425, Map.of(ResourceType.STEEL, 5L, ResourceType.ELECTRONICS, 1L)));
    private static final List<Actor> ACTORS = List.of(A, B);
    private ShopPurchase40RestartTests() {}

    @GameTest(template="townhall_state_empty", timeoutTicks=200)
    public static void native_payment_and_consumed_delivery_recover_in_distinct_jvms(GameTestHelper h) throws Exception {
        ServerLevel level = h.getLevel();
        Path root = controlled(level);
        String phase = System.getProperty("catancraft.shopRestartPhase", "");
        require(phase.equals("seed") || phase.equals("verify"), "Explicit seed|verify JVM phase required");
        source();
        Path seedProof = root.resolve("data/catancraft_shop40_restart_seed.proof");
        Path verified = root.resolve("data/catancraft_shop40_restart_verified.proof");
        require(!Files.exists(verified, LinkOption.NOFOLLOW_LINKS), "Already-verified world cannot be reused");
        if (phase.equals("seed")) seed(level, root, seedProof);
        else verify(level, root, seedProof, verified);
        require(level.getServer().getPlayerList().getPlayers().isEmpty(), "Synthetic players escaped synchronous native saves");
        h.succeed();
    }

    private static void seed(ServerLevel level, Path root, Path proof) throws Exception {
        require(!Files.exists(proof, LinkOption.NOFOLLOW_LINKS), "Seed cannot overwrite original restart proof");
        CatanSavedData data = CatanSavedData.get(level.getServer());
        require(data.nations().isEmpty(), "Seed requires a fresh synthetic nation ledger");
        for (Actor actor : ACTORS) {
            require(!Files.exists(playerPath(root, actor), LinkOption.NOFOLLOW_LINKS), "Seed refuses existing native actor data");
            require(data.placedMapAsset(paidKey(actor)) == null, "Seed refuses existing debit receipt");
            if (Files.isDirectory(root.resolve("data"), LinkOption.NOFOLLOW_LINKS)) {
                try (var files = Files.newDirectoryStream(root.resolve("data"), "catancraft_shop40_" + actor.id() + "_*")) {
                    require(!files.iterator().hasNext(), "Seed refuses existing actor purchase history");
                }
            }
        }
        for (Actor actor : ACTORS) {
            NationData nation = data.createNation(actor.name(), actor.id());
            nation.setTreasury(FUNDS);
            for (ResourceType type : ResourceType.values()) nation.addResource(type, STOCK);
        }
        data.setDirty(); level.getServer().overworld().getDataStorage().save();
        require(nativeNbt(bytes(root.resolve("data/catancraft.dat"))).getCompound("data").equals(data.save(new CompoundTag())),
                "Initial nations were not saved through native SavedData");

        CompoundTag record = metadata(level, root);
        ListTag actors = new ListTag();
        for (Actor actor : ACTORS) {
            NationData nation = data.nationForPlayer(actor.id());
            require(nation != null, "Initial native nation missing");
            Model env = new Model(level, actor, nation, false);
            require(count(env.player.saveWithoutId(new CompoundTag()), actor) == 0
                    && env.player.getInventory().isEmpty() && delivery(env.player.saveWithoutId(new CompoundTag()), actor).isEmpty(),
                    "Seed actor is not fresh");
            CompoundTag before = nation.save();
            ShopPurchase40.Request request = new ShopPurchase40.Request(actor.operation(), "synthetic-" + actor.name(),
                    FIXTURE, env.facility, actor.price(), actor.stack());
            require(ShopPurchase40.fixtureCreate(env, request, point -> {}).equals(actor.operation()), "Original request was not created");
            byte[] original = bytes(ShopPurchase40.intentPath(env, actor.operation()));
            CompoundTag intent = ProducerJournal.read(original);
            assertIntent(env, intent);
            interruptAt(env, actor == A ? ShopPurchase40.SavePoint.AFTER_LEDGER_SAVE : ShopPurchase40.SavePoint.AFTER_PLAYER_SAVE);
            String hash = TownHallRevisions.sha(original);
            assertPaid(env, hash);
            byte[] originalProgress = assertInterruptedProgress(env, hash);
            require(ShopPurchase40.pending(env).equals(List.of(actor.operation())), "Original interrupted request not pending");

            CompoundTag evidence = new CompoundTag();
            evidence.putUUID("actor", actor.id()); evidence.putString("name", actor.name());
            evidence.putUUID("operation", actor.operation()); evidence.putUUID("nation", nation.id());
            evidence.put("nationBefore", before); evidence.put("nationAfterDebit", nation.save());
            evidence.put("price", intent.getCompound("price").copy()); evidence.put("item", actor.stack().save(new CompoundTag()));
            evidence.putString("intentFile", ShopPurchase40.intentPath(env, actor.operation()).getFileName().toString());
            evidence.putByteArray("originalIntentBytes", original); evidence.putString("intentSHA256", hash);
            evidence.putString("progressFile", ShopPurchase40.receiptPath(env, actor.operation()).getFileName().toString());
            evidence.putString("progressPhase", actor == A ? "ABSENT" : "DELIVERING");
            evidence.putByteArray("originalProgressBytes", originalProgress);
            evidence.putString("originalProgressSHA256", TownHallRevisions.sha(originalProgress));
            evidence.putString("debitReceipt", data.placedMapAsset(paidKey(actor)));
            evidence.putString("interruption", actor == A ? "AFTER_LEDGER_SAVE" : "AFTER_PLAYER_SAVE");
            int delivered = actor == A ? 0 : 1;
            byte[] deliveredBytes = bytes(playerPath(root, actor));
            assertInventory(env, nativeNbt(deliveredBytes), delivered, delivered == 0 ? "" : hash);
            evidence.putInt("nativeCountBeforeConsumption", delivered);
            evidence.putString("nativePlayerBeforeConsumptionSHA256", TownHallRevisions.sha(deliveredBytes));
            evidence.putByteArray("nativePlayerBeforeConsumptionBytes", deliveredBytes);
            if (actor == B) {
                int slot = intent.getInt("slot");
                require(ItemStack.matches(env.player.getInventory().getItem(slot), actor.stack()), "Consumption must remove the exact delivered stack");
                env.player.getInventory().setItem(slot, ItemStack.EMPTY);
                env.persistPlayer();
                assertInventory(env, nativeNbt(bytes(playerPath(root, actor))), 0, hash);
            }
            byte[] playerBytes = bytes(playerPath(root, actor));
            evidence.putString("playerFile", "playerdata/" + actor.id() + ".dat");
            evidence.putByteArray("originalPlayerBytes", playerBytes);
            evidence.putString("originalPlayerSHA256", TownHallRevisions.sha(playerBytes));
            evidence.putString("deliveryReceipt", delivery(nativeNbt(playerBytes), actor));
            evidence.putInt("nativeCountAtRestart", count(nativeNbt(playerBytes), actor));
            evidence.putInt("ledgerSaves", env.ledgerSaves); evidence.putInt("playerSaves", env.playerSaves);
            require(Arrays.equals(original, bytes(ShopPurchase40.intentPath(env, actor.operation()))), "Seed changed immutable intent bytes");
            require(Arrays.equals(originalProgress, assertInterruptedProgress(env, hash)), "Consumption/native save changed interrupted delivery progress");
            actors.add(evidence);
        }
        record.put("actors", actors); record.put("nativeLedger", data.save(new CompoundTag()));
        record.putString("nativeLedgerSHA256", TownHallRevisions.sha(bytes(root.resolve("data/catancraft.dat"))));
        record.putString("result", "SEEDED_PENDING_DISTINCT_JVM_VERIFICATION");
        ProducerJournal.write(proof, record, false);
        CatanCraft.LOGGER.info("SHOP40_RESTART_SEED_PASS actors=2 paidOnce=PASS actorAItem=0 actorBDelivered=1 actorBConsumed=1 actorAProgress=ABSENT actorBProgress=DELIVERING nativePlayerFiles=PASS nativeLedger=PASS process={} source={} restartVerification=PENDING", process(), source());
    }

    private static void verify(ServerLevel level, Path root, Path proof, Path verified) throws Exception {
        byte[] proofBytes = bytes(proof);
        CompoundTag record = ProducerJournal.read(proofBytes);
        require(record.getInt("schema") == 1 && NS.equals(record.getString("namespace"))
                && FIXTURE.equals(record.getString("fixture")) && root.toString().equals(record.getString("worldRoot"))
                && DIM.equals(record.getString("dimension")) && level.getSeed() == record.getLong("seed")
                && source().equals(record.getString("source")), "Foreign restart evidence/source/world");
        require(!record.getString("process").isBlank() && !process().equals(record.getString("process")), "Verify must use a distinct JVM");
        CatanSavedData data = CatanSavedData.get(level.getServer());
        require(data.save(new CompoundTag()).equals(record.getCompound("nativeLedger")), "Original native ledger did not survive restart");
        require(nativeNbt(bytes(root.resolve("data/catancraft.dat"))).getCompound("data").equals(record.getCompound("nativeLedger")),
                "Original native ledger file differs from seeded evidence");
        ListTag seeded = record.getList("actors", Tag.TAG_COMPOUND);
        require(seeded.size() == 2, "Both original synthetic actors are required");
        CompoundTag result = metadata(level, root);
        result.putString("seedProcess", record.getString("process"));
        result.putString("seedProofSHA256", TownHallRevisions.sha(proofBytes));
        ListTag verifiedActors = new ListTag();
        for (int i = 0; i < ACTORS.size(); i++) {
            Actor actor = ACTORS.get(i); CompoundTag evidence = seeded.getCompound(i);
            require(actor.id().equals(evidence.getUUID("actor")) && actor.name().equals(evidence.getString("name"))
                    && actor.operation().equals(evidence.getUUID("operation")), "Seed actor/operation identity changed");
            NationData nation = data.nation(evidence.getUUID("nation"));
            require(nation != null && nation == data.nationForPlayer(actor.id()) && nation.isLeader(actor.id())
                    && nation.save().equals(evidence.getCompound("nationAfterDebit")), "Original native nation/balance missing");
            byte[] originalPlayer = bytes(playerPath(root, actor));
            require(Arrays.equals(originalPlayer, evidence.getByteArray("originalPlayerBytes"))
                    && TownHallRevisions.sha(originalPlayer).equals(evidence.getString("originalPlayerSHA256")),
                    "Original native player bytes changed before verification");
            // Load original native player NBT before any recovery/save. No reseeding.
            Model env = new Model(level, actor, nation, true);
            byte[] original = bytes(ShopPurchase40.intentPath(env, actor.operation()));
            String hash = TownHallRevisions.sha(original);
            require(Arrays.equals(original, evidence.getByteArray("originalIntentBytes"))
                    && hash.equals(evidence.getString("intentSHA256")) && hash.equals(evidence.getString("debitReceipt")),
                    "Original immutable intent/debit receipt changed across JVMs");
            CompoundTag intent = ProducerJournal.read(original);
            assertIntent(env, intent);
            require(intent.getCompound("price").equals(evidence.getCompound("price"))
                    && intent.getCompound("item").equals(evidence.getCompound("item")), "Original item/price was not retained");
            assertPaid(env, hash);
            byte[] originalProgress = assertInterruptedProgress(env, hash);
            require(Arrays.equals(originalProgress, evidence.getByteArray("originalProgressBytes"))
                    && TownHallRevisions.sha(originalProgress).equals(evidence.getString("originalProgressSHA256"))
                    && evidence.getString("progressPhase").equals(actor == A ? "ABSENT" : "DELIVERING")
                    && ShopPurchase40.receiptPath(env, actor.operation()).getFileName().toString().equals(evidence.getString("progressFile"))
                    && ShopPurchase40.pending(env).equals(List.of(actor.operation())),
                    "Verify requires the original pending operation and unchanged durable delivery progress");
            assertInventory(env, nativeNbt(originalPlayer), 0, actor == A ? "" : hash);
            require(evidence.getInt("nativeCountAtRestart") == 0
                    && evidence.getInt("nativeCountBeforeConsumption") == (actor == A ? 0 : 1)
                    && evidence.getString("deliveryReceipt").equals(actor == A ? "" : hash), "Seed delivery/consumption checkpoint changed");
            byte[] beforeConsumption = evidence.getByteArray("nativePlayerBeforeConsumptionBytes");
            require(TownHallRevisions.sha(beforeConsumption).equals(evidence.getString("nativePlayerBeforeConsumptionSHA256"))
                    && count(nativeNbt(beforeConsumption), actor) == (actor == A ? 0 : 1)
                    && delivery(nativeNbt(beforeConsumption), actor).equals(actor == A ? "" : hash),
                    "Original native pre-consumption delivery evidence changed");

            CompoundTag beforeLedger = data.save(new CompoundTag());
            require(ShopPurchase40.resume(env, actor.operation()).complete(), "Original purchase did not recover");
            require(beforeLedger.equals(data.save(new CompoundTag())) && nation.save().equals(evidence.getCompound("nationAfterDebit")),
                    "Recovery debited the original nation again");
            assertPaid(env, hash);
            int recoveredCount = actor == A ? 1 : 0;
            assertInventory(env, nativeNbt(bytes(playerPath(root, actor))), recoveredCount, hash);
            require(actor == A || env.playerSaves == 0, "Consumed delivery unexpectedly tried to save/insert another item");
            require(ShopPurchase40.pending(env).isEmpty(), "Recovered operation remains pending");
            byte[] completion = bytes(ShopPurchase40.receiptPath(env, actor.operation()));
            CompoundTag committed = ProducerJournal.read(completion);
            require(committed.getString("phase").equals("COMMITTED") && committed.getUUID("id").equals(actor.operation())
                    && committed.getString("intentSHA256").equals(hash), "Completion is not bound to the immutable original intent");

            byte[] completedPlayer = bytes(playerPath(root, actor));
            byte[] completedLedger = bytes(root.resolve("data/catancraft.dat"));
            CompoundTag completedMemory = env.player.saveWithoutId(new CompoundTag());
            int ledgerSaves = env.ledgerSaves, playerSaves = env.playerSaves;
            for (int retry = 0; retry < 3; retry++) require(ShopPurchase40.resume(env, actor.operation()).complete(), "Completed retry failed");
            require(env.ledgerSaves == ledgerSaves && env.playerSaves == playerSaves
                    && beforeLedger.equals(data.save(new CompoundTag()))
                    && completedMemory.equals(env.player.saveWithoutId(new CompoundTag()))
                    && Arrays.equals(completedPlayer, bytes(playerPath(root, actor)))
                    && Arrays.equals(completedLedger, bytes(root.resolve("data/catancraft.dat")))
                    && Arrays.equals(completion, bytes(ShopPurchase40.receiptPath(env, actor.operation())))
                    && Arrays.equals(original, bytes(ShopPurchase40.intentPath(env, actor.operation()))),
                    "Completed retries performed a save, debit, insertion or history rewrite");
            CompoundTag outcome = evidence.copy();
            outcome.putInt("nativeCountAfterRecovery", recoveredCount); outcome.putInt("additionalDebit", 0);
            outcome.putInt("newItemsAfterRecovery", recoveredCount); outcome.putInt("completedRetries", 3);
            outcome.putInt("recoveryLedgerSaves", ledgerSaves); outcome.putInt("recoveryPlayerSaves", playerSaves);
            outcome.putInt("completedRetrySaves", 0); outcome.putString("deliveryReceiptAfterRecovery", hash);
            outcome.putString("completionSHA256", TownHallRevisions.sha(completion));
            outcome.putString("nativePlayerAfterRecoverySHA256", TownHallRevisions.sha(completedPlayer));
            outcome.put("nationAfterRecovery", nation.save()); verifiedActors.add(outcome);
        }
        require(Arrays.equals(proofBytes, bytes(proof)), "Verification rewrote immutable seed evidence");
        result.put("actors", verifiedActors); result.putString("result", "PASS");
        result.putString("nativeLedgerAfterRecoverySHA256", TownHallRevisions.sha(bytes(root.resolve("data/catancraft.dat"))));
        ProducerJournal.write(verified, result, false);
        CatanCraft.LOGGER.info("SHOP40_RESTART_VERIFY_PASS distinctJVM=PASS originalNativeLedger=PASS originalNativePlayers=PASS immutableIntentBytes=PASS originalProgressBytes=PASS actorAAdditionalDebit=0 actorARecoveryItems=1 actorBAdditionalDebit=0 actorBRecoveryItems=0 consumedDeliveryReceipt=PASS completedRetrySaves=0 seedProcess={} verifyProcess={} source={}", record.getString("process"), process(), source());
    }

    private static byte[] assertInterruptedProgress(Model env, String intentHash) throws IOException {
        Path progressPath = ShopPurchase40.receiptPath(env, env.actor.operation());
        if (env.actor == A) {
            require(!Files.exists(progressPath, LinkOption.NOFOLLOW_LINKS), "Payment-only interruption must precede delivery progress");
            return new byte[0];
        }
        byte[] progressBytes = bytes(progressPath);
        CompoundTag progress = ProducerJournal.read(progressBytes);
        require(progress.getAllKeys().equals(Set.of("schema", "id", "intentSHA256", "phase"))
                && progress.getInt("schema") == 1 && progress.getUUID("id").equals(env.actor.operation())
                && progress.getString("intentSHA256").equals(intentHash) && progress.getString("phase").equals("DELIVERING"),
                "Delivered interruption must retain its original durable DELIVERING progress");
        return progressBytes;
    }

    private static void assertIntent(Model env, CompoundTag intent) {
        CompoundTag price = new CompoundTag(); price.putLong("money", env.actor.price().money());
        CompoundTag resources = new CompoundTag(); env.actor.price().resources().forEach((type, amount) -> resources.putLong(type.name(), amount));
        price.put("resources", resources);
        CompoundTag identity = intent.getCompound("identity");
        require(intent.getUUID("id").equals(env.actor.operation()) && intent.getString("catalog").equals(FIXTURE)
                && intent.getString("offer").equals("synthetic-" + env.actor.name()) && intent.getCompound("facility").equals(env.facility)
                && intent.getCompound("price").equals(price) && intent.getCompound("item").equals(env.actor.stack().save(new CompoundTag()))
                && identity.getUUID("actor").equals(env.actor.id()) && identity.getUUID("nation").equals(env.nation.id())
                && identity.getString("root").equals(env.root.toString()) && identity.getString("dimension").equals(DIM)
                && identity.getLong("seed") == env.level.getSeed() && intent.getInt("slot") == 0,
                "Immutable original request differs from the independently expected fixture");
    }

    private static void assertPaid(Model env, String hash) throws IOException {
        require(env.nation.treasury() == FUNDS - env.actor.price().money(), "Original money price was not charged exactly once");
        for (ResourceType type : ResourceType.values()) require(env.nation.resource(type) == STOCK - env.actor.price().resources().getOrDefault(type, 0L),
                "Original resource price was not charged exactly once: " + type);
        require(hash.equals(env.data().placedMapAsset(paidKey(env.actor))), "Native debit receipt is not bound to the intent hash");
        CompoundTag nativeLedger = nativeNbt(bytes(env.root.resolve("data/catancraft.dat"))).getCompound("data");
        require(nativeLedger.equals(env.data().save(new CompoundTag())), "Native ledger readback differs from actual SavedData");
    }

    private static void assertInventory(Model env, CompoundTag disk, int expectedCount, String expectedReceipt) {
        CompoundTag memory = env.player.saveWithoutId(new CompoundTag());
        require(disk.hasUUID("UUID") && disk.getUUID("UUID").equals(env.actor.id())
                && count(disk, env.actor) == expectedCount && count(memory, env.actor) == expectedCount
                && disk.getList("Inventory", Tag.TAG_COMPOUND).size() == expectedCount
                && disk.getList("Inventory", Tag.TAG_COMPOUND).equals(memory.getList("Inventory", Tag.TAG_COMPOUND))
                && delivery(disk, env.actor).equals(expectedReceipt) && delivery(memory, env.actor).equals(expectedReceipt),
                "Native inventory/delivery receipt differs from the expected actual player state");
    }

    private static int count(CompoundTag nativePlayer, Actor actor) {
        int count = 0; ListTag inventory = nativePlayer.getList("Inventory", Tag.TAG_COMPOUND);
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = ItemStack.of(inventory.getCompound(i));
            if (ItemStack.isSameItemSameTags(stack, actor.stack())) count += stack.getCount();
        }
        return count;
    }

    private static String delivery(CompoundTag player, Actor actor) {
        return player.getCompound("ForgeData").getCompound(Player.PERSISTED_NBT_TAG)
                .getCompound(ShopPurchase40.RECEIPTS).getString(actor.operation().toString());
    }

    private static void interruptAt(Model env, ShopPurchase40.SavePoint point) throws IOException {
        IOException stop = new IOException("Intentional restart fixture interruption at " + point);
        try { ShopPurchase40.fixtureResume(env, env.actor.operation(), at -> { if (at == point) throw stop; }); }
        catch (IOException ex) { if (ex == stop) return; throw ex; }
        throw new AssertionError("The intended durable save boundary was not reached: " + point);
    }

    private static CompoundTag metadata(ServerLevel level, Path root) {
        CompoundTag n = new CompoundTag(); n.putInt("schema", 1); n.putString("namespace", NS); n.putString("fixture", FIXTURE);
        n.putString("source", source()); n.putString("worldRoot", root.toString()); n.putString("dimension", DIM);
        n.putLong("seed", level.getSeed()); n.putString("process", process()); return n;
    }
    private static String paidKey(Actor actor) { return "shop40_paid:" + actor.operation(); }
    private static Path playerPath(Path root, Actor actor) { return root.resolve("playerdata/" + actor.id() + ".dat"); }
    private static String process() { return ProcessHandle.current().pid() + ":" + ManagementFactory.getRuntimeMXBean().getStartTime(); }
    private static String source() {
        String source = System.getProperty("catancraft.shopRestartSource", "");
        require(source.matches("[0-9a-f]{40}"), "Explicit 40-hex catancraft.shopRestartSource is required in both JVMs"); return source;
    }
    private static byte[] bytes(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file) || Files.size(file) > 8 * 1024 * 1024 + 40)
            throw new IOException("Missing or invalid retained native restart evidence: " + file.getFileName());
        return Files.readAllBytes(file);
    }
    private static CompoundTag nativeNbt(byte[] bytes) throws IOException {
        try (var in = new ByteArrayInputStream(bytes)) { return NbtIo.readCompressed(in); }
    }
    private static Path controlled(ServerLevel level) throws IOException {
        Path root = level.getServer().getWorldPath(LevelResource.ROOT).toRealPath();
        if (!(level.getServer() instanceof GameTestServer) || !level.getServer().isSameThread()
                || !root.getFileName().toString().equals("world") || !root.getParent().getFileName().toString().equals("controlled-" + NS + "-run")
                || !root.getParent().getParent().getFileName().toString().equals("build")
                || !Files.readString(root.getParent().resolve(".catancraft-controlled-fixture-v1")).equals("CatanCraft synthetic flat fixture v1\n")
                || !DIM.equals(level.dimension().location().toString()) || level.getSeed() != 0)
            throw new IOException("Shop restart fixture requires its isolated marked native GameTest world");
        return root;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private record Actor(UUID id, String name, UUID operation, Item item, ShopPurchase40.Price price) {
        ItemStack stack() {
            ItemStack stack = new ItemStack(item, 1);
            stack.getOrCreateTag().putString("CatanCraftSyntheticShop40Actor", id.toString()); return stack;
        }
    }

    private static final class Model implements ShopPurchase40.Environment {
        final ServerLevel level; final Actor actor; final NationData nation; final Path root;
        final FakePlayer player; final CompoundTag facility; final ShopPurchase40.Environment nativeRuntime;
        int ledgerSaves, playerSaves;
        Model(ServerLevel level, Actor actor, NationData nation, boolean loadOriginal) throws IOException {
            this.level = level; this.actor = actor; this.nation = nation; this.root = controlled(level);
            CompoundTag original = loadOriginal ? nativeNbt(bytes(playerPath(root, actor))) : null;
            if (original != null) require(original.hasUUID("UUID") && original.getUUID("UUID").equals(actor.id()), "Original player file has foreign native identity");
            this.player = FakePlayerFactory.get(level, new GameProfile(actor.id(), actor.name()));
            if (original != null) player.load(original);
            else player.setPos(0.5, 80, 0.5);
            require(player.getUUID().equals(actor.id()) && player.getGameProfile().getName().equals(actor.name()), "Synthetic native actor identity changed");
            facility = new CompoundTag(); facility.putString("id", FIXTURE + ":" + actor.name()); facility.putUUID("owner", nation.id());
            nativeRuntime = ShopPurchase40.runtime(player, this::guard);
            guard(facility);
            if (original != null) assertInventory(this, original, count(original, actor), delivery(original, actor));
        }
        public ServerPlayer player() { return player; }
        public CatanSavedData data() { return CatanSavedData.get(level.getServer()); }
        public void guard(CompoundTag originalFacility) throws IOException {
            if (!root.equals(controlled(level)) || player.serverLevel() != level || !player.getUUID().equals(actor.id())
                    || data().nation(nation.id()) != nation || data().nationForPlayer(actor.id()) != nation || !nation.isLeader(actor.id())
                    || !facility.equals(originalFacility)) throw new IOException("Synthetic fixture's original native identity/facility changed");
        }
        public void persistLedger() throws IOException { guard(facility); ledgerSaves++; nativeRuntime.persistLedger(); }
        public void persistPlayer() throws IOException {
            guard(facility);
            List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers();
            if (!players.isEmpty()) throw new IOException("Controlled restart fixture requires no connected/ticking players");
            playerSaves++; players.add(player);
            try { nativeRuntime.persistPlayer(); }
            finally { players.remove(player); }
            require(players.isEmpty(), "Temporary native player-save registration was not removed");
        }
    }
}
