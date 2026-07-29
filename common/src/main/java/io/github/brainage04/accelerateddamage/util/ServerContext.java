package io.github.brainage04.accelerateddamage.util;

import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

public final class ServerContext {
    private static MinecraftServer server;

    private ServerContext() {
    }

    public static void set(MinecraftServer currentServer) {
        server = currentServer;
    }

    public static void clear(MinecraftServer stoppedServer) {
        if (server == stoppedServer) {
            server = null;
        }
    }

    public static @Nullable MinecraftServer get() {
        return server;
    }
}
