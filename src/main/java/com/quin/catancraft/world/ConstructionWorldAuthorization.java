package com.quin.catancraft.world;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.nio.file.*;

/** Normal same-release progression in a bound fresh demo, or unchanged historical R7 scope.
 * Maintenance-only installers retain their original authorizers. A malformed fresh marker
 * never falls back to historical authorization and no marker is created by gameplay code.
 */
final class ConstructionWorldAuthorization {
    private ConstructionWorldAuthorization(){}
    static Path authorize(MinecraftServer server)throws IOException {
        Path root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        if(Files.exists(root.resolve(FreshDemoAuthorization.MARKER),LinkOption.NOFOLLOW_LINKS))return FreshDemoAuthorization.authorize(server);
        return TownHallRepairService.authorize(server);
    }
}
