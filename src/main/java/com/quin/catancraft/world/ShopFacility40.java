package com.quin.catancraft.world;

import com.quin.catancraft.data.*;
import com.quin.catancraft.map.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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

/** Read-only physical contract for the initial, authorized City A rollout.
 * Provisional interaction policy: eye within four blocks, unobstructed counter sightline.
 * A craft table's sparse context is read from the pinned r4 source; no repair occurs here. */
final class ShopFacility40 {
    static final double REACH_SQUARED = 16.0;
    static final BlockPos STARTER = new BlockPos(-750, 84, -773);
    private static final String REVISION = "city_a_weapon_counter40_v1";
    private static final int[][] CONTEXT = {{19,3,18}, {19,2,18}, {19,2,17}, {19,3,17}, {19,1,17}};
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
        if (!player.serverLevel().dimension().location().toString().equals("minecraft:overworld")) return false;
        return STARTER.equals(pos) || pos.equals(new BlockPos(-805,82,-771))
                || pos.equals(new BlockPos(-805,82,-731)) || pos.equals(new BlockPos(-695,82,-737));
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
        if (!recognizes(player, counter)) throw new IOException("Visit the City A weapon counter.");
        MinecraftServer server = player.server;
        Path root = ConstructionWorldAuthorization.authorize(server);
        TerritoryDefinition definition = MapDefinitionManager.territory("a");
        canonical(definition);
        NationData nation = leader(player);
        CatanSavedData data = CatanSavedData.get(server);
        TerritoryData city = data.territory("a");
        if (city == null || !nation.id().equals(city.ownerNationId()) || city.cityLevel() < 1)
            throw new IOException("This counter requires your nation's completed City A Town Hall.");
        if (SectorService.hasActiveOperation(server) || CityBasePreparation.isRunning(server))
            throw new IOException("City construction is active; return when it has finished.");
        PaidRecoveryService.requireClear(server, "a");
        TownHallRepairService.requireNoPending(server, "a");
        for (String file : List.of("catancraft_siege38_a.dat", "catancraft_snapshot39_a.dat", "catancraft_restore39_a.dat"))
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
        boolean starter = counter.equals(STARTER);
        String plot, token;
        int level;
        MapAnchor anchor;
        if (starter) {
            plot = "town_hall"; level = 0; anchor = definition.managementAnchor();
            token = data.placedMapAsset("territory:a:town_hall");
            String expected = city.cityLevel() <= 3 ? SchematicAssetRegistry.asset("th" + city.cityLevel()).placementToken() : "";
            boolean complete = city.cityLevel() <= 3 ? expected.equals(token)
                    : city.cityLevel() <= 5 && (TownHallRevisions.token(city.cityLevel(), 1).equals(token)
                    || TownHallRevisions.token(city.cityLevel(), 2).equals(token));
            if (!complete || !player.serverLevel().getBlockState(counter).is(Blocks.LECTERN)
                    || !(player.serverLevel().getBlockEntity(counter) instanceof LecternBlockEntity))
                throw new IOException("The completed Town Hall management terminal could not be verified.");
        } else {
            List<BuildingInstance> factories = city.buildings().stream().filter(b -> b.type() == BuildingType.WEAPONS_FACTORY).toList();
            if (factories.size() != 1) throw new IOException("A unique completed Weapons Factory is required here.");
            BuildingInstance factory = factories.get(0);
            plot = IndustrialPlotService.occupiedPlot(factory); level = factory.level();
            if (!IndustrialPlotService.INDUSTRIAL_PLOTS.contains(plot) || definition.reservedPlotIds().contains(plot)
                    || city.buildings().stream().filter(b -> plot.equals(IndustrialPlotService.occupiedPlot(b))).count() != 1)
                throw new IOException("The Weapons Factory plot could not be verified.");
            anchor = IndustrialPlotService.anchorFor(BuildingType.WEAPONS_FACTORY, plot, definition);
            canonicalFactory(anchor, plot);
            if (!local(anchor,19,3,18).equals(counter)) throw new IOException("Visit the counter inside your completed Weapons Factory.");
            token = data.placedMapAsset("territory:a:" + plot);
            if (!SchematicAssetRegistry.asset("weapons_factory").placementToken().equals(token))
                throw new IOException("This Weapons Factory has not completed construction.");
            verifyFactoryContext(player, anchor);
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", 1); tag.putString("revision", REVISION);
        tag.putString("world", root.toRealPath().toString());
        tag.putString("worldId", Files.exists(root.resolve(FreshDemoAuthorization.MARKER), LinkOption.NOFOLLOW_LINKS)
                ? FreshDemoAuthorization.worldId(server, root) : "authorized_historical_r7");
        tag.putString("levelName", server.getWorldData().getLevelName());
        tag.putString("dimension", definition.dimension()); tag.putLong("seed", player.serverLevel().getSeed());
        tag.putString("city", "a"); tag.putUUID("owner", nation.id());
        tag.putString("type", starter ? "starter" : "weapons"); tag.putString("plot", plot);
        tag.putString("sourceToken", token); tag.putLong("counter", counter.asLong());
        tag.putIntArray("anchor", new int[]{anchor.x(), anchor.y(), anchor.z(), anchor.rotation()});
        tag.putInt("quotedLevel", level); tag.putInt("requiredLevel", 0);
        return new Binding(tag, starter ? "City A starter counter" : "City A Weapons Factory · Level " + level, level, counter);
    }

    /** Historical paid intent retains its original price/item. Higher factory level is acceptable;
     * ownership, exact source revision, physical placement and all other native guards still apply. */
    static void guard(ServerPlayer player, CompoundTag original) throws IOException { recheck(player, original, false); }
    static Binding recheck(ServerPlayer player, CompoundTag original, boolean exactLevel) throws IOException {
        if (original == null || !original.contains("counter", net.minecraft.nbt.Tag.TAG_LONG)
                || !original.contains("requiredLevel", net.minecraft.nbt.Tag.TAG_INT)
                || !original.contains("quotedLevel", net.minecraft.nbt.Tag.TAG_INT))
            throw new IOException("The counter selection is incomplete; open it again.");
        Binding current = open(player, BlockPos.of(original.getLong("counter")));
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
    static BlockPos local(MapAnchor anchor, int x, int y, int z) throws IOException {
        if (anchor == null || (anchor.rotation() != 0 && anchor.rotation() != 180)) throw new IOException("Unsupported factory orientation.");
        int sign = anchor.rotation() == 0 ? 1 : -1;
        return new BlockPos(anchor.x() + sign * (x - 29), anchor.y() + y - 2, anchor.z() + sign * (z - 15));
    }
    private static void verifyFactoryContext(ServerPlayer player, MapAnchor anchor) throws IOException {
        var source = BasicConstructionAssets.load("weapons_factory");
        Rotation rotation = anchor.rotation() == 0 ? Rotation.NONE : Rotation.CLOCKWISE_180;
        for (int[] xyz : CONTEXT) {
            BlockPos at = local(anchor, xyz[0], xyz[1], xyz[2]);
            if (!player.serverLevel().hasChunkAt(at) || player.serverLevel().getBlockEntity(at) != null
                    || !player.serverLevel().getBlockState(at).equals(source.at(xyz[0], xyz[1], xyz[2]).rotate(rotation)))
                throw new IOException("The weapon counter or its standing space has changed; no purchase was made.");
        }
    }
    private static void canonical(TerritoryDefinition definition) throws IOException {
        ProducerService.validateMap(definition);
        MapAnchor manager = definition.managementAnchor();
        if (!definition.id().equals("a") || manager == null || manager.rotation() != 0
                || !new BlockPos(manager.x(),manager.y(),manager.z()).equals(STARTER))
            throw new IOException("This counter is outside the approved City A placement.");
        for (String plot : IndustrialPlotService.INDUSTRIAL_PLOTS)
            canonicalFactory(IndustrialPlotService.anchorFor(BuildingType.WEAPONS_FACTORY, plot, definition), plot);
    }
    private static void canonicalFactory(MapAnchor anchor, String plot) throws IOException {
        int x = plot.equals("plot_4") ? -705 : -795;
        int z = plot.equals("plot_1") ? -774 : -734;
        if (anchor == null || anchor.x() != x || anchor.y() != 81 || anchor.z() != z
                || anchor.rotation() != (plot.equals("plot_4") ? 180 : 0))
            throw new IOException("The factory placement differs from the approved City A map.");
    }
}
