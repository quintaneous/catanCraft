package com.quin.catancraft.world;

import com.quin.catancraft.data.*;
import com.quin.catancraft.map.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

/** Read-only physical contract for reviewed all-city counters, retaining legacy A identities.
 * Provisional interaction policy: eye within four blocks, unobstructed counter sightline.
 * A craft table's sparse context is read from the pinned r4 source; no repair occurs here. */
final class ShopFacility40 {
    static final double REACH_SQUARED = 16.0;
    static final BlockPos STARTER = new BlockPos(-750, 84, -773);
    private static final String REVISION = "city_a_weapon_counter40_v1";
    private static final int[][] CONTEXT = {{19,3,18}, {19,2,18}, {19,2,17}, {19,3,17}, {19,1,17}};
    record Construction(String token, int level) {}
    record Binding(CompoundTag identity, String title, int level, BlockPos counter) {
        Binding { identity = identity.copy(); counter = counter.immutable(); }
        public CompoundTag identity() { return identity.copy(); }
        boolean starter() { return identity.getString("type").equals("starter"); }
        CompoundTag forOffer(int required) throws IOException {
            if (required < 0 || required > level) throw new IOException("Visit your completed Weapons Factory for this item.");
            CompoundTag tag = identity(); tag.putInt("requiredLevel", required); return tag;
        }
    }
    private ShopFacility40() {}

    /** Recognition alone confers no permission. It also catches a missing/replaced native counter. */
    static boolean recognizes(ServerPlayer player, BlockPos pos) {
        return ShopSites51.recognizes(player.serverLevel().dimension().location().toString(), pos, false);
    }
    static boolean starterCounter(ServerPlayer player, BlockPos pos) {
        return ShopSites51.recognizes(player.serverLevel().dimension().location().toString(), pos, true);
    }
    static boolean connected(ServerPlayer player) {
        return player != null && !(player instanceof FakePlayer) && player.getServer() != null
                && player.getServer().getPlayerList().getPlayer(player.getUUID()) == player
                && player.connection != null && player.connection.isAcceptingMessages()
                && !player.hasDisconnected() && !player.isRemoved();
    }
    static void actor(ServerPlayer player) throws IOException {
        if (!connected(player) || !player.getServer().isSameThread() || !player.isAlive()
                || player.getServer().getLevel(player.serverLevel().dimension()) != player.serverLevel())
            throw new IOException("Use the counter while connected and alive.");
        GameType mode = player.gameMode.getGameModeForPlayer();
        if (mode != GameType.SURVIVAL && mode != GameType.ADVENTURE)
            throw new IOException("Use the weapon counter in Survival or Adventure mode.");
    }
    static NationData leader(ServerPlayer player) throws IOException {
        CatanSavedData data = CatanSavedData.get(player.server);
        List<NationData> nations = data.nations().stream().filter(n -> n.members().contains(player.getUUID())).toList();
        if (nations.size() != 1 || !nations.get(0).isLeader(player.getUUID()))
            throw new IOException("Only your nation's leader can spend nation supplies at this counter.");
        return nations.get(0);
    }
    static Binding open(ServerPlayer player, BlockPos counter) throws IOException {
        actor(player);
        ShopSites51.Site site = ShopSites51.resolve(player.serverLevel().dimension().location().toString(), counter);
        MinecraftServer server = player.server;
        Path root = ConstructionWorldAuthorization.authorize(server);
        TerritoryDefinition definition = site.definition();
        String cityId = site.city();
        NationData nation = leader(player);
        CatanSavedData data = CatanSavedData.get(server);
        TerritoryData city = data.territory(cityId);
        if (city == null || !nation.id().equals(city.ownerNationId()) || city.cityLevel() < 1)
            throw new IOException("This counter requires your nation's completed local Town Hall.");
        if (SectorService.hasActiveOperation(server) || CityBasePreparation.isRunning(server))
            throw new IOException("City construction is active; return when it has finished.");
        PaidRecoveryService.requireClear(server, cityId);
        TownHallRepairService.requireNoPending(server, cityId);
        for (String file : List.of("catancraft_siege38_" + cityId + ".dat", "catancraft_snapshot39_" + cityId + ".dat", "catancraft_restore39_" + cityId + ".dat"))
            if (Files.exists(root.resolve("data").resolve(file), LinkOption.NOFOLLOW_LINKS))
                throw new IOException("This city's siege or restoration history requires review before shopping.");
        if (!definition.toBoundary().contains(definition.dimension(), player.blockPosition().getX(), player.blockPosition().getZ()))
            throw new IOException("Return to the counter inside your city.");
        if (!player.serverLevel().hasChunkAt(counter)) throw new IOException("The counter is not loaded.");
        double distance = player.getEyePosition().distanceToSqr(Vec3.atCenterOf(counter));
        if (!Double.isFinite(distance) || distance > REACH_SQUARED)
            throw new IOException("Move within four blocks of the counter and open it again.");
        var hit = player.serverLevel().clip(new ClipContext(player.getEyePosition(), Vec3.atCenterOf(counter),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(counter))
            throw new IOException("The counter must be in clear reach.");
        Construction construction = construction(player.serverLevel(), data, nation, site);
        CompoundTag tag = identity(site, construction, nation.id(), root.toRealPath(),
                Files.exists(root.resolve(FreshDemoAuthorization.MARKER), LinkOption.NOFOLLOW_LINKS)
                        ? FreshDemoAuthorization.worldId(server, root) : "authorized_historical_r7",
                server.getWorldData().getLevelName(), player.serverLevel().getSeed());
        String title = "City " + cityId.toUpperCase(java.util.Locale.ROOT)
                + (site.starter() ? " starter counter" : " Weapons Factory · Level " + construction.level());
        return new Binding(tag, title, construction.level(), counter);
    }

    /** Construction inspection is read-only, not player/session authorization. */
    static Construction construction(ServerLevel level, CatanSavedData data, NationData nation, ShopSites51.Site site) throws IOException {
        ShopSites51.canonical(site.definition());
        TerritoryDefinition definition = site.definition();
        if (!definition.dimension().equals(level.dimension().location().toString()))
            throw new IOException("The counter is in a different dimension.");
        TerritoryData city = data.territory(site.city());
        if (city == null || nation == null || !nation.id().equals(city.ownerNationId()) || city.cityLevel() < 1 || city.cityLevel() > 5)
            throw new IOException("This counter requires your nation's completed local Town Hall.");
        BlockPos counter = site.counter();
        String token;
        int factoryLevel;
        if (site.starter()) {
            factoryLevel = 0;
            token = data.placedMapAsset("territory:" + site.city() + ":town_hall");
            String expected = city.cityLevel() <= 3 ? SchematicAssetRegistry.asset("th" + city.cityLevel()).placementToken() : "";
            boolean complete = city.cityLevel() <= 3 ? expected.equals(token)
                    : city.cityLevel() <= 5 && (TownHallRevisions.token(city.cityLevel(), 1).equals(token)
                    || TownHallRevisions.token(city.cityLevel(), 2).equals(token));
            if (!complete || !level.hasChunkAt(counter) || !level.getBlockState(counter).is(Blocks.LECTERN)
                    || !(level.getBlockEntity(counter) instanceof LecternBlockEntity))
                throw new IOException("The completed Town Hall management terminal could not be verified.");
        } else {
            List<BuildingInstance> factories = city.buildings().stream().filter(b -> b.type() == BuildingType.WEAPONS_FACTORY).toList();
            if (factories.size() != 1) throw new IOException("A unique completed Weapons Factory is required here.");
            BuildingInstance factory = factories.get(0);
            String plot = IndustrialPlotService.occupiedPlot(factory); factoryLevel = factory.level();
            if (!IndustrialPlotService.INDUSTRIAL_PLOTS.contains(plot) || definition.reservedPlotIds().contains(plot)
                    || !site.plot().equals(plot) || factoryLevel < 1 || factoryLevel > 5
                    || city.buildings().stream().filter(b -> plot.equals(IndustrialPlotService.occupiedPlot(b))).count() != 1)
                throw new IOException("The Weapons Factory plot could not be verified.");
            MapAnchor anchor = IndustrialPlotService.anchorFor(BuildingType.WEAPONS_FACTORY, plot, definition);
            if (!local(anchor,19,3,18).equals(counter)) throw new IOException("Visit the counter inside your completed Weapons Factory.");
            token = data.placedMapAsset("territory:" + site.city() + ":" + plot);
            if (!SchematicAssetRegistry.asset("weapons_factory").placementToken().equals(token))
                throw new IOException("This Weapons Factory has not completed construction.");
            verifyFactoryContext(level, anchor);
        }
        return new Construction(token, factoryLevel);
    }

    /** A keeps the exact schema/field set and revision used by 040-050 paid histories. */
    static CompoundTag identity(ShopSites51.Site site, Construction construction, java.util.UUID owner,
                                Path root, String worldId, String levelName, long seed) {
        MapAnchor anchor = site.anchor();
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", 1); tag.putString("revision", site.city().equals("a") ? REVISION : ShopSites51.REVISION);
        if (!site.city().equals("a")) tag.putString("siteMapSHA256", ShopSites51.MAP_SHA256);
        tag.putString("world", root.toString()); tag.putString("worldId", worldId);
        tag.putString("levelName", levelName);
        tag.putString("dimension", site.definition().dimension()); tag.putLong("seed", seed);
        tag.putString("city", site.city()); tag.putUUID("owner", owner);
        tag.putString("type", site.starter() ? "starter" : "weapons"); tag.putString("plot", site.plot());
        tag.putString("sourceToken", construction.token()); tag.putLong("counter", site.counter().asLong());
        tag.putIntArray("anchor", new int[]{anchor.x(), anchor.y(), anchor.z(), anchor.rotation()});
        tag.putInt("quotedLevel", construction.level()); tag.putInt("requiredLevel", 0);
        return tag;
    }

    /** Historical paid intent retains its original price/item. Higher factory level is acceptable;
     * ownership, exact source revision, physical placement and all other native guards still apply. */
    static void guard(ServerPlayer player, CompoundTag original) throws IOException { recheck(player, original, false); }
    static Binding recheck(ServerPlayer player, CompoundTag original, boolean exactLevel) throws IOException {
        selection(original);
        return match(open(player, BlockPos.of(original.getLong("counter"))), original, exactLevel);
    }
    private static void selection(CompoundTag original) throws IOException {
        if (original == null || !original.contains("counter", net.minecraft.nbt.Tag.TAG_LONG)
                || !original.contains("requiredLevel", net.minecraft.nbt.Tag.TAG_INT)
                || !original.contains("quotedLevel", net.minecraft.nbt.Tag.TAG_INT))
            throw new IOException("The counter selection is incomplete; open it again.");
    }
    static Binding match(Binding current, CompoundTag original, boolean exactLevel) throws IOException {
        selection(original);
        int required = original.getInt("requiredLevel"), quoted = original.getInt("quotedLevel");
        if (required < 0 || required > 5 || quoted < required || quoted > 5 || current.level() < required
                || (exactLevel && current.level() != quoted))
            throw new IOException("The local factory level changed; open the counter again.");
        CompoundTag expected = current.identity();
        expected.putInt("requiredLevel", required);
        if (!exactLevel) expected.putInt("quotedLevel", quoted);
        if (!expected.equals(original)) throw new IOException("The counter or its ownership changed; open it again.");
        return current;
    }
    static void requireSameSite(CompoundTag active, CompoundTag original) throws IOException {
        for (String key : List.of("world", "worldId", "dimension", "city", "type", "plot", "counter", "anchor")) {
            if (!active.contains(key) || !original.contains(key) || !java.util.Objects.equals(active.get(key), original.get(key)))
                throw new IOException("Continue this unfinished purchase or claim at " + ShopSites51.origin(original) + ".");
        }
    }
    static BlockPos local(MapAnchor anchor, int x, int y, int z) throws IOException {
        if (anchor == null || (anchor.rotation() != 0 && anchor.rotation() != 180)) throw new IOException("Unsupported factory orientation.");
        int sign = anchor.rotation() == 0 ? 1 : -1;
        return new BlockPos(anchor.x() + sign * (x - 29), anchor.y() + y - 2, anchor.z() + sign * (z - 15));
    }
    private static void verifyFactoryContext(ServerLevel level, MapAnchor anchor) throws IOException {
        var source = BasicConstructionAssets.load("weapons_factory");
        Rotation rotation = anchor.rotation() == 0 ? Rotation.NONE : Rotation.CLOCKWISE_180;
        for (int[] xyz : CONTEXT) {
            BlockPos at = local(anchor, xyz[0], xyz[1], xyz[2]);
            if (!level.hasChunkAt(at) || level.getBlockEntity(at) != null
                    || !level.getBlockState(at).equals(source.at(xyz[0], xyz[1], xyz[2]).rotate(rotation)))
                throw new IOException("The weapon counter or its standing space has changed; no purchase was made.");
        }
    }
}
