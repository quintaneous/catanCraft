package com.quin.catancraft.world;

import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.data.CatanSavedData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Published TaCZ/SBW coexistence proof only; this test creates no actor or purchase. */
@GameTestHolder("catancraft_native42_combined")
@PrefixGameTestTemplate(false)
public final class NativeWeapon42CombinedTests {
    private NativeWeapon42CombinedTests() {}

    @GameTest(template = "townhall_state_empty", batch = "native42_combined", timeoutTicks = 1200)
    public static void all_97_offers_qualify_with_both_published_providers(GameTestHelper helper) throws Exception {
        MinecraftServer server = helper.getLevel().getServer();
        controlled(helper.getLevel());
        require(ModList.get().isLoaded("tacz") && ModList.get().isLoaded("superbwarfare"),
                "Combined qualification requires both published native providers");
        require(ModList.get().getModContainerById("simplebedrockmodel").orElseThrow().getModInfo()
                        .getVersion().toString().equals("2.6.2-forge-mc1.20.1"),
                "Combined runtime did not select qualified SimpleBedrockModel 2.6.2");
        require("15".equals(System.getProperty("catancraft.native42CombinedRuntimeArtifacts"))
                        && digestProperty("catancraft.taczRuntimeManifestSHA256")
                        && digestProperty("catancraft.sbwRuntimeManifestSHA256"),
                "Combined test is missing its verified runtime manifest bindings");
        var offers = WeaponStock42.offers();
        require(offers.size() == 97
                        && offers.stream().filter(offer -> offer.provider().equals("tacz")).count() == 69
                        && offers.stream().filter(offer -> offer.provider().equals("superbwarfare")).count() == 28,
                "Combined stock must contain all 97 offers (69 TaCZ, 28 SBW)");
        CompoundTag accounting = CatanSavedData.get(server).save(new CompoundTag());
        Map<String, Map<UUID, CompoundTag>> entities = entities(server);
        for (WeaponStock42.Offer offer : offers) {
            NativeWeapon42.Supply supply = NativeWeapon42.quote(server, offer);
            ItemStack item = supply.item();
            NativeWeapon42Tests.assertNative(offer, item);
            require(item.getCount() == offer.count() && item.getCount() <= item.getMaxStackSize(),
                    "Combined native stock produced the wrong item amount: " + offer.id());
            CompoundTag original = item.save(new CompoundTag());
            require(original.equals(ItemStack.of(original.copy()).save(new CompoundTag())),
                    "Combined native stock did not roundtrip exactly: " + offer.id());
            NativeWeapon42.recheck(server, offer, supply);
            require(original.equals(supply.item().save(new CompoundTag())),
                    "Combined native recheck changed the quoted identity: " + offer.id());
        }
        require(accounting.equals(CatanSavedData.get(server).save(new CompoundTag())),
                "Combined native quoting changed nations, balances or accounting");
        require(server.getPlayerList().getPlayers().isEmpty() && entities.equals(entities(server)),
                "Combined native quoting created or changed a player/entity");
        CatanCraft.LOGGER.info("NATIVE42_COMBINED_PASS offers=97 tacz=69 sbw=28 nativeIdentity=PASS "
                + "unloaded=PASS roundtrip=PASS recheck=PASS players=0 accountingChanges=0 entityChanges=0 "
                + "simplebedrockmodel=2.6.2-forge-mc1.20.1 runtimeArtifacts=15");
        helper.succeed();
    }

    private static void controlled(ServerLevel level) throws IOException {
        MinecraftServer server = level.getServer();
        Path requested = server.getWorldPath(LevelResource.ROOT).toAbsolutePath();
        for (Path path = requested; path != null; path = path.getParent()) {
            if (Files.isSymbolicLink(path)) throw new IOException("Combined native test refuses a symbolic-link world path");
        }
        Path root = requested.toRealPath();
        Path marker = root.getParent().resolve(".catancraft-controlled-fixture-v1");
        if (!(server instanceof GameTestServer) || !server.isSameThread() || !server.getPlayerList().getPlayers().isEmpty()
                || !root.getFileName().toString().equals("world")
                || !root.getParent().getFileName().toString().equals("controlled-catancraft_native42_combined-run")
                || !root.getParent().getParent().getFileName().toString().equals("build")
                || Files.isSymbolicLink(marker) || !Files.isRegularFile(marker)
                || !Files.readString(marker).equals("CatanCraft synthetic flat fixture v1\n")
                || !level.dimension().location().toString().equals("minecraft:overworld") || level.getSeed() != 0)
            throw new IOException("Combined native test requires its empty marked disposable GameTest world");
    }

    private static Map<String, Map<UUID, CompoundTag>> entities(MinecraftServer server) {
        Map<String, Map<UUID, CompoundTag>> result = new HashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            Map<UUID, CompoundTag> contents = new HashMap<>();
            for (var entity : level.getAllEntities()) contents.put(entity.getUUID(), entity.saveWithoutId(new CompoundTag()));
            result.put(level.dimension().location().toString(), Map.copyOf(contents));
        }
        return Map.copyOf(result);
    }

    private static boolean digestProperty(String key) {
        return System.getProperty(key, "").matches("[0-9a-f]{64}");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
