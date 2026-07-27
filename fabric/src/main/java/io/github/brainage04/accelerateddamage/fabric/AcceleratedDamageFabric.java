package io.github.brainage04.accelerateddamage.fabric;

import io.github.brainage04.accelerateddamage.AcceleratedDamage;
import io.github.brainage04.accelerateddamage.platform.AcceleratedDamagePlatform;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class AcceleratedDamageFabric implements ModInitializer, AcceleratedDamagePlatform {
    @Override
    public void onInitialize() {
        AcceleratedDamage.initialize(this);
    }

    @Override
    public GameRule<Boolean> registerBooleanGameRule(String path) {
        return GameRuleBuilder.forBoolean(false).category(GameRuleCategory.PLAYER)
                .buildAndRegister(Identifier.fromNamespaceAndPath(AcceleratedDamage.MOD_ID, path));
    }

    @Override
    public void registerGameRuleChange(GameRule<Boolean> rule, BiConsumer<Boolean, MinecraftServer> callback) {
        GameRuleEvents.changeCallback(rule).register(callback::accept);
    }

    @Override
    public void registerPlayerJoin(BiConsumer<ServerPlayer, MinecraftServer> callback) {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> callback.accept(handler.player, server));
    }

    @Override public void registerServerStarted(Consumer<MinecraftServer> callback) { ServerLifecycleEvents.SERVER_STARTED.register(callback::accept); }
    @Override public void registerServerStopped(Consumer<MinecraftServer> callback) { ServerLifecycleEvents.SERVER_STOPPED.register(callback::accept); }
    @Override public void registerEndServerTick(Consumer<MinecraftServer> callback) { ServerTickEvents.END_SERVER_TICK.register(callback::accept); }
}
