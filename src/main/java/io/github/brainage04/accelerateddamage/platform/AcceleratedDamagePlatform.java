package io.github.brainage04.accelerateddamage.platform;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRule;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface AcceleratedDamagePlatform {
    GameRule<Boolean> registerBooleanGameRule(String path);

    void registerGameRuleChange(GameRule<Boolean> rule, BiConsumer<Boolean, MinecraftServer> callback);

    void registerPlayerJoin(BiConsumer<ServerPlayer, MinecraftServer> callback);

    void registerServerStarted(Consumer<MinecraftServer> callback);

    void registerServerStopped(Consumer<MinecraftServer> callback);

    void registerEndServerTick(Consumer<MinecraftServer> callback);
}
