package com.quin.catancraft.world;

import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraftforge.common.util.FakePlayer;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;

/** Controlled fixtures only: Forge's public roster is intentionally unmodifiable.
 * Invoke the server's actual native player store without pretending a FakePlayer
 * is authenticated or inserting it into the connected/ticking player roster. */
final class ShopNativePlayerFixture40 {
    private ShopNativePlayerFixture40() {}
    static void save(ServerPlayer player)throws IOException {
        var server=player.server;Path root=server.getWorldPath(LevelResource.ROOT).toRealPath();
        if(!(player instanceof FakePlayer)||!(server instanceof GameTestServer)||!server.isSameThread()
                ||!server.getPlayerList().getPlayers().isEmpty()||!root.getFileName().toString().equals("world")
                ||!Set.of("controlled-catancraft_shop40-run","controlled-catancraft_shop40_restart-run").contains(root.getParent().getFileName().toString())
                ||!Files.readString(root.getParent().resolve(".catancraft-controlled-fixture-v1")).equals("CatanCraft synthetic flat fixture v1\n"))
            throw new IOException("Native player fixture requires its empty marked disposable GameTest server");
        try {
            Field[] candidates=Arrays.stream(PlayerList.class.getDeclaredFields()).filter(f->f.getType()==PlayerDataStorage.class).toArray(Field[]::new);
            if(candidates.length!=1||!candidates[0].trySetAccessible())throw new IOException("Cannot identify native fixture player store");
            PlayerDataStorage storage=(PlayerDataStorage)candidates[0].get(server.getPlayerList());
            if(!storage.getPlayerDataFolder().toPath().toRealPath().equals(root.resolve("playerdata").toRealPath()))
                throw new IOException("Native fixture player directory differs from the controlled world");
            storage.save(player); // Native serialization, replacement and Forge event; never manufacture a file.
        }catch(ReflectiveOperationException|RuntimeException failure){throw new IOException("Native fixture player save failed",failure);}
    }
}
